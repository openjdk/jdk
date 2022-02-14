/*
 * Copyright (c) 2021, Huawei Technologies Co. Ltd. All rights reserved.
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

#include "precompiled.hpp"

#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1EvacFailureRegions.inline.hpp"
#include "gc/g1/g1HeapRegionChunk.hpp"
#include "gc/g1/heapRegion.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"

G1EvacFailureRegions::G1EvacFailureRegions() :
  _regions_failed_evacuation(mtGC),
  _evac_failure_regions(nullptr),
  _chunk_claimers(nullptr),
  _evac_failure_regions_cur_length(0),
  _max_regions(0),
  _heap(G1CollectedHeap::heap()),
  _phase_times(_heap->phase_times()) {
}

G1EvacFailureRegions::~G1EvacFailureRegions() {
  assert(_evac_failure_regions == nullptr, "not cleaned up");
  assert(_chunk_claimers == nullptr, "not cleaned up");
}

void G1EvacFailureRegions::pre_collection(uint max_regions) {
  Atomic::store(&_evac_failure_regions_cur_length, 0u);
  _max_regions = max_regions;
  _regions_failed_evacuation.resize(_max_regions);
  _evac_failure_regions = NEW_C_HEAP_ARRAY(uint, _max_regions, mtGC);
  _chunk_claimers = NEW_C_HEAP_ARRAY(G1HeapRegionChunksClaimer*, _max_regions, mtGC);
}

void G1EvacFailureRegions::post_collection() {
  _regions_failed_evacuation.resize(0);

  for (uint i = 0; i < _evac_failure_regions_cur_length; i++) {
    FREE_C_HEAP_OBJ(_chunk_claimers[_evac_failure_regions[i]]);
  }
  FREE_C_HEAP_ARRAY(uint, _chunk_claimers);
  _chunk_claimers = nullptr;

  FREE_C_HEAP_ARRAY(uint, _evac_failure_regions);
  _evac_failure_regions = nullptr;
  _max_regions = 0; // To have any record() attempt fail in the future.
}

bool G1EvacFailureRegions::contains(uint region_idx) const {
  assert(region_idx < _max_regions, "must be");
  return _regions_failed_evacuation.par_at(region_idx, memory_order_relaxed);
}

void G1EvacFailureRegions::par_iterate(HeapRegionClosure* closure,
                                       HeapRegionClaimer* hrclaimer,
                                       uint worker_id) const {
  G1CollectedHeap::heap()->par_iterate_regions_array(closure,
                                                     hrclaimer,
                                                     _evac_failure_regions,
                                                     Atomic::load(&_evac_failure_regions_cur_length),
                                                     worker_id);
}

void G1EvacFailureRegions::par_iterate_chunks_in_regions(G1HeapRegionChunkClosure* chunk_closure,
                                                         uint worker_id) const {
  G1ScanChunksInHeapRegionClosure closure(_chunk_claimers, chunk_closure, worker_id);

  G1CollectedHeap::heap()->par_iterate_regions_array(&closure,
                                                     nullptr, // pass null, so every worker thread go through every region.
                                                     _evac_failure_regions,
                                                     Atomic::load(&_evac_failure_regions_cur_length),
                                                     worker_id);
}

class PrepareEvacFailureRegionTask : public WorkerTask {
  G1EvacFailureRegions* _evac_failure_regions;
  uint _num_workers;
  HeapRegionClaimer _claimer;

  class PrepareEvacFailureRegionClosure : public HeapRegionClosure {
    G1EvacFailureRegions* _evac_failure_regions;
    uint _worker_id;
  public:
    PrepareEvacFailureRegionClosure(G1EvacFailureRegions* evac_failure_regions, uint worker_id) :
      _evac_failure_regions(evac_failure_regions),
      _worker_id(worker_id) { }

    bool do_heap_region(HeapRegion* r) override {
      assert(_evac_failure_regions->contains(r->hrm_index()), "precondition");
      _evac_failure_regions->prepare_region(r->hrm_index(), _worker_id);
      return false;
    }
  };

public:
  PrepareEvacFailureRegionTask(G1EvacFailureRegions* evac_failure_regions, uint num_workers) :
  WorkerTask("Prepare Evacuation Failure Region Task"),
  _evac_failure_regions(evac_failure_regions),
  _num_workers(num_workers),
  _claimer(_num_workers) {
  }

  void work(uint worker_id) override {
    PrepareEvacFailureRegionClosure closure(_evac_failure_regions, worker_id);
    _evac_failure_regions->par_iterate(&closure, &_claimer, worker_id);
  }
};

void G1EvacFailureRegions::prepare_regions() {
  uint num_workers = MAX2(1u, MIN2(_evac_failure_regions_cur_length, G1CollectedHeap::heap()->workers()->active_workers()));
  PrepareEvacFailureRegionTask task(this, num_workers);
  G1CollectedHeap::heap()->workers()->run_task(&task, num_workers);
}

void G1EvacFailureRegions::prepare_region(uint region_idx, uint worker_id) {
  HeapRegion* hr = _heap->region_at(region_idx);
  assert(!hr->is_pinned(), "Unexpected pinned region at index %u", hr->hrm_index());
  assert(hr->in_collection_set(), "bad CS");
  assert(contains(hr->hrm_index()), "precondition");

  Ticks start = Ticks::now();
  G1GCPhaseTimes* phase_times = G1CollectedHeap::heap()->phase_times();

  hr->clear_index_in_opt_cset();

  bool during_concurrent_start = _heap->collector_state()->in_concurrent_start_gc();
  bool during_concurrent_mark = _heap->collector_state()->mark_or_rebuild_in_progress();

  hr->note_self_forwarding_removal_start(during_concurrent_start,
                                         during_concurrent_mark);

  _phase_times->record_or_add_thread_work_item(G1GCPhaseTimes::RestoreRetainedRegions,
                                               worker_id,
                                               1,
                                               G1GCPhaseTimes::RestoreRetainedRegionsNum);

  hr->rem_set()->clean_code_roots(hr);
  hr->rem_set()->clear_locked(true);

  phase_times->record_or_add_time_secs(G1GCPhaseTimes::PrepareRetainedRegions, worker_id, (Ticks::now() - start).seconds());
}
