/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSetCandidates.hpp"
#include "gc/g1/g1CollectionSetChooser.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "gc/shared/space.hpp"
#include "runtime/atomic.hpp"
#include "utilities/quickSort.hpp"

// Determine collection set candidates (from marking): For all regions determine
// whether they should be a collection set candidate, calculate their efficiency,
// sort and put them into the candidates.
// Threads calculate the GC efficiency of the regions they get to process, and
// put them into some work area without sorting. At the end that array is sorted and
// moved to the destination.
class G1BuildCandidateRegionsTask : public WorkerTask {

  using CandidateInfo = G1CollectionSetCandidateInfo;

  // Work area for building the set of collection set candidates. Contains references
  // to heap regions with their GC efficiencies calculated. To reduce contention
  // on claiming array elements, worker threads claim parts of this array in chunks;
  // Array elements may be null as threads might not get enough regions to fill
  // up their chunks completely.
  // Final sorting will remove them.
  class G1BuildCandidateArray : public StackObj {

    uint const _max_size;
    uint const _chunk_size;

    CandidateInfo* _data;

    uint volatile _cur_claim_idx;

    // Calculates the maximum array size that will be used.
    static uint required_array_size(uint num_regions, uint chunk_size, uint num_workers) {
      uint const max_waste = num_workers * chunk_size;
      // The array should be aligned with respect to chunk_size.
      uint const aligned_num_regions = ((num_regions + chunk_size - 1) / chunk_size) * chunk_size;

      return aligned_num_regions + max_waste;
    }

  public:
    G1BuildCandidateArray(uint max_num_regions, uint chunk_size, uint num_workers) :
      _max_size(required_array_size(max_num_regions, chunk_size, num_workers)),
      _chunk_size(chunk_size),
      _data(NEW_C_HEAP_ARRAY(CandidateInfo, _max_size, mtGC)),
      _cur_claim_idx(0) {
      for (uint i = 0; i < _max_size; i++) {
        _data[i] = CandidateInfo();
      }
    }

    ~G1BuildCandidateArray() {
      FREE_C_HEAP_ARRAY(CandidateInfo, _data);
    }

    // Claim a new chunk, returning its bounds [from, to[.
    void claim_chunk(uint& from, uint& to) {
      uint result = Atomic::add(&_cur_claim_idx, _chunk_size);
      assert(_max_size > result - 1,
             "Array too small, is %u should be %u with chunk size %u.",
             _max_size, result, _chunk_size);
      from = result - _chunk_size;
      to = result;
    }

    // Set element in array.
    void set(uint idx, G1HeapRegion* hr) {
      assert(idx < _max_size, "Index %u out of bounds %u", idx, _max_size);
      assert(_data[idx]._r == nullptr, "Value must not have been set.");
      _data[idx] = CandidateInfo(hr);
    }

    void sort_by_gc_efficiency() {
      if (_cur_claim_idx == 0) {
        return;
      }
      for (uint i = _cur_claim_idx; i < _max_size; i++) {
        assert(_data[i]._r == nullptr, "must be");
      }
      qsort(_data, _cur_claim_idx, sizeof(_data[0]), (_sort_Fn)G1CollectionSetCandidateInfo::compare_region_gc_efficiency);
      for (uint i = _cur_claim_idx; i < _max_size; i++) {
        assert(_data[i]._r == nullptr, "must be");
      }
    }

    CandidateInfo* array() const { return _data; }
  };

  // Per-region closure. In addition to determining whether a region should be
  // added to the candidates, and calculating those regions' gc efficiencies, also
  // gather additional statistics.
  class G1BuildCandidateRegionsClosure : public G1HeapRegionClosure {
    G1BuildCandidateArray* _array;

    uint _cur_chunk_idx;
    uint _cur_chunk_end;

    uint _regions_added;

    void add_region(G1HeapRegion* hr) {
      if (_cur_chunk_idx == _cur_chunk_end) {
        _array->claim_chunk(_cur_chunk_idx, _cur_chunk_end);
      }
      assert(_cur_chunk_idx < _cur_chunk_end, "Must be");

      _array->set(_cur_chunk_idx, hr);
      _cur_chunk_idx++;

      _regions_added++;
    }

  public:
    G1BuildCandidateRegionsClosure(G1BuildCandidateArray* array) :
      _array(array),
      _cur_chunk_idx(0),
      _cur_chunk_end(0),
      _regions_added(0) { }

    bool do_heap_region(G1HeapRegion* r) {
      // Candidates from marking are always old; also keep regions that are already
      // collection set candidates (some retained regions) in that list.
      if (!r->is_old() || r->is_collection_set_candidate()) {
        // Keep remembered sets and everything for these regions.
        return false;
      }

      // Can not add a region without a remembered set to the candidates.
      if (!r->rem_set()->is_tracked()) {
        return false;
      }

      // Skip any region that is currently used as an old GC alloc region. We should
      // not consider those for collection before we fill them up as the effective
      // gain from them is small. I.e. we only actually reclaim from the filled part,
      // as the remainder is still eligible for allocation. These objects are also
      // likely to have already survived a few collections, so they might be longer
      // lived anyway.
      // Otherwise the Old region must satisfy the liveness condition.
      bool should_add = !G1CollectedHeap::heap()->is_old_gc_alloc_region(r) &&
                        G1CollectionSetChooser::region_occupancy_low_enough_for_evac(r->live_bytes());
      if (should_add) {
        add_region(r);
      } else {
        r->rem_set()->clear(true /* only_cardset */);
      }
      return false;
    }

    uint regions_added() const { return _regions_added; }
  };

  G1CollectedHeap* _g1h;
  G1HeapRegionClaimer _hrclaimer;

  uint volatile _num_regions_added;

  G1BuildCandidateArray _result;

  void update_totals(uint num_regions) {
    if (num_regions > 0) {
      Atomic::add(&_num_regions_added, num_regions);
    }
  }

  // Early prune (remove) regions meeting the G1HeapWastePercent criteria. That
  // is, either until only the minimum amount of old collection set regions are
  // available (for forward progress in evacuation) or the waste accumulated by the
  // removed regions is above the maximum allowed waste.
  // Updates number of candidates and reclaimable bytes given.
  void prune(CandidateInfo* data) {
    G1Policy* p = G1CollectedHeap::heap()->policy();

    uint num_candidates = Atomic::load(&_num_regions_added);

    uint min_old_cset_length = p->calc_min_old_cset_length(num_candidates);
    uint num_pruned = 0;
    size_t wasted_bytes = 0;

    if (min_old_cset_length >= num_candidates) {
      // We take all of the candidate regions to provide some forward progress.
      return;
    }

    size_t allowed_waste = p->allowed_waste_in_collection_set();
    uint max_to_prune = num_candidates - min_old_cset_length;

    while (true) {
      G1HeapRegion* r = data[num_candidates - num_pruned - 1]._r;
      size_t const reclaimable = r->reclaimable_bytes();
      if (num_pruned >= max_to_prune ||
          wasted_bytes + reclaimable > allowed_waste) {
        break;
      }
      r->rem_set()->clear(true /* cardset_only */);

      wasted_bytes += reclaimable;
      num_pruned++;
    }

    log_debug(gc, ergo, cset)("Pruned %u regions out of %u, leaving %zu bytes waste (allowed %zu)",
                              num_pruned,
                              num_candidates,
                              wasted_bytes,
                              allowed_waste);

    Atomic::sub(&_num_regions_added, num_pruned, memory_order_relaxed);
  }

public:
  G1BuildCandidateRegionsTask(uint max_num_regions, uint chunk_size, uint num_workers) :
    WorkerTask("G1 Build Candidate Regions"),
    _g1h(G1CollectedHeap::heap()),
    _hrclaimer(num_workers),
    _num_regions_added(0),
    _result(max_num_regions, chunk_size, num_workers) { }

  void work(uint worker_id) {
    G1BuildCandidateRegionsClosure cl(&_result);
    _g1h->heap_region_par_iterate_from_worker_offset(&cl, &_hrclaimer, worker_id);
    update_totals(cl.regions_added());
  }

  void sort_and_prune_into(G1CollectionSetCandidates* candidates) {
    _result.sort_by_gc_efficiency();
    prune(_result.array());
    candidates->set_candidates_from_marking(_result.array(),
                                            _num_regions_added);
  }
};

uint G1CollectionSetChooser::calculate_work_chunk_size(uint num_workers, uint num_regions) {
  assert(num_workers > 0, "Active gc workers should be greater than 0");
  return MAX2(num_regions / num_workers, 1U);
}

void G1CollectionSetChooser::build(WorkerThreads* workers, uint max_num_regions, G1CollectionSetCandidates* candidates) {
  uint num_workers = workers->active_workers();
  uint chunk_size = calculate_work_chunk_size(num_workers, max_num_regions);

  G1BuildCandidateRegionsTask cl(max_num_regions, chunk_size, num_workers);
  workers->run_task(&cl, num_workers);

  cl.sort_and_prune_into(candidates);
  candidates->verify();
}
