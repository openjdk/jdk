/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/collectionSetChooser.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/shared/space.inline.hpp"
#include "runtime/atomic.hpp"

// Even though we don't use the GC efficiency in our heuristics as
// much as we used to, we still order according to GC efficiency. This
// will cause regions with a lot of live objects and large RSets to
// end up at the end of the array. Given that we might skip collecting
// the last few old regions, if after a few mixed GCs the remaining
// have reclaimable bytes under a certain threshold, the hope is that
// the ones we'll skip are ones with both large RSets and a lot of
// live objects, not the ones with just a lot of live objects if we
// ordered according to the amount of reclaimable bytes per region.
static int order_regions(HeapRegion* hr1, HeapRegion* hr2) {
  if (hr1 == NULL) {
    if (hr2 == NULL) {
      return 0;
    } else {
      return 1;
    }
  } else if (hr2 == NULL) {
    return -1;
  }

  double gc_eff1 = hr1->gc_efficiency();
  double gc_eff2 = hr2->gc_efficiency();
  if (gc_eff1 > gc_eff2) {
    return -1;
  } if (gc_eff1 < gc_eff2) {
    return 1;
  } else {
    return 0;
  }
}

static int order_regions(HeapRegion** hr1p, HeapRegion** hr2p) {
  return order_regions(*hr1p, *hr2p);
}

CollectionSetChooser::CollectionSetChooser() :
  // The line below is the worst bit of C++ hackery I've ever written
  // (Detlefs, 11/23).  You should think of it as equivalent to
  // "_regions(100, true)": initialize the growable array and inform it
  // that it should allocate its elem array(s) on the C heap.
  //
  // The first argument, however, is actually a comma expression
  // (set_allocation_type(this, C_HEAP), 100). The purpose of the
  // set_allocation_type() call is to replace the default allocation
  // type for embedded objects STACK_OR_EMBEDDED with C_HEAP. It will
  // allow to pass the assert in GenericGrowableArray() which checks
  // that a growable array object must be on C heap if elements are.
  //
  // Note: containing object is allocated on C heap since it is CHeapObj.
  //
  _regions((ResourceObj::set_allocation_type((address) &_regions,
                                             ResourceObj::C_HEAP),
                  100), true /* C_Heap */),
    _front(0), _end(0), _first_par_unreserved_idx(0),
    _region_live_threshold_bytes(0), _remaining_reclaimable_bytes(0) {
  _region_live_threshold_bytes = mixed_gc_live_threshold_bytes();
}

#ifndef PRODUCT
void CollectionSetChooser::verify() {
  guarantee(_end <= regions_length(), "_end: %u regions length: %u", _end, regions_length());
  guarantee(_front <= _end, "_front: %u _end: %u", _front, _end);
  uint index = 0;
  size_t sum_of_reclaimable_bytes = 0;
  while (index < _front) {
    guarantee(regions_at(index) == NULL,
              "all entries before _front should be NULL");
    index += 1;
  }
  HeapRegion *prev = NULL;
  while (index < _end) {
    HeapRegion *curr = regions_at(index++);
    guarantee(curr != NULL, "Regions in _regions array cannot be NULL");
    guarantee(!curr->is_young(), "should not be young!");
    guarantee(!curr->is_pinned(),
              "Pinned region should not be in collection set (index %u)", curr->hrm_index());
    if (prev != NULL) {
      guarantee(order_regions(prev, curr) != 1,
                "GC eff prev: %1.4f GC eff curr: %1.4f",
                prev->gc_efficiency(), curr->gc_efficiency());
    }
    sum_of_reclaimable_bytes += curr->reclaimable_bytes();
    prev = curr;
  }
  guarantee(sum_of_reclaimable_bytes == _remaining_reclaimable_bytes,
            "reclaimable bytes inconsistent, "
            "remaining: " SIZE_FORMAT " sum: " SIZE_FORMAT,
            _remaining_reclaimable_bytes, sum_of_reclaimable_bytes);
}
#endif // !PRODUCT

void CollectionSetChooser::sort_regions() {
  // First trim any unused portion of the top in the parallel case.
  if (_first_par_unreserved_idx > 0) {
    assert(_first_par_unreserved_idx <= regions_length(),
           "Or we didn't reserved enough length");
    regions_trunc_to(_first_par_unreserved_idx);
  }
  _regions.sort(order_regions);
  assert(_end <= regions_length(), "Requirement");
#ifdef ASSERT
  for (uint i = 0; i < _end; i++) {
    assert(regions_at(i) != NULL, "Should be true by sorting!");
  }
#endif // ASSERT
  if (log_is_enabled(Trace, gc, liveness)) {
    G1PrintRegionLivenessInfoClosure cl("Post-Sorting");
    for (uint i = 0; i < _end; ++i) {
      HeapRegion* r = regions_at(i);
      cl.do_heap_region(r);
    }
  }
  verify();
}

void CollectionSetChooser::add_region(HeapRegion* hr) {
  assert(!hr->is_pinned(),
         "Pinned region shouldn't be added to the collection set (index %u)", hr->hrm_index());
  assert(hr->is_old(), "should be old but is %s", hr->get_type_str());
  assert(hr->rem_set()->is_complete(),
         "Trying to add region %u to the collection set with incomplete remembered set", hr->hrm_index());
  _regions.append(hr);
  _end++;
  _remaining_reclaimable_bytes += hr->reclaimable_bytes();
  hr->calc_gc_efficiency();
}

void CollectionSetChooser::push(HeapRegion* hr) {
  assert(hr != NULL, "Can't put back a NULL region");
  assert(_front >= 1, "Too many regions have been put back");
  _front--;
  regions_at_put(_front, hr);
  _remaining_reclaimable_bytes += hr->reclaimable_bytes();
}

void CollectionSetChooser::prepare_for_par_region_addition(uint n_threads,
                                                           uint n_regions,
                                                           uint chunk_size) {
  _first_par_unreserved_idx = 0;
  uint max_waste = n_threads * chunk_size;
  // it should be aligned with respect to chunk_size
  uint aligned_n_regions = (n_regions + chunk_size - 1) / chunk_size * chunk_size;
  assert(aligned_n_regions % chunk_size == 0, "should be aligned");
  regions_at_put_grow(aligned_n_regions + max_waste - 1, NULL);
}

uint CollectionSetChooser::claim_array_chunk(uint chunk_size) {
  uint res = (uint) Atomic::add((jint) chunk_size,
                                (volatile jint*) &_first_par_unreserved_idx);
  assert(regions_length() > res + chunk_size - 1,
         "Should already have been expanded");
  return res - chunk_size;
}

void CollectionSetChooser::set_region(uint index, HeapRegion* hr) {
  assert(regions_at(index) == NULL, "precondition");
  assert(hr->is_old(), "should be old but is %s", hr->get_type_str());
  regions_at_put(index, hr);
  hr->calc_gc_efficiency();
}

void CollectionSetChooser::update_totals(uint region_num,
                                         size_t reclaimable_bytes) {
  // Only take the lock if we actually need to update the totals.
  if (region_num > 0) {
    assert(reclaimable_bytes > 0, "invariant");
    // We could have just used atomics instead of taking the
    // lock. However, we currently don't have an atomic add for size_t.
    MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
    _end += region_num;
    _remaining_reclaimable_bytes += reclaimable_bytes;
  } else {
    assert(reclaimable_bytes == 0, "invariant");
  }
}

void CollectionSetChooser::iterate(HeapRegionClosure* cl) {
  for (uint i = _front; i < _end; i++) {
    HeapRegion* r = regions_at(i);
    if (cl->do_heap_region(r)) {
      cl->set_incomplete();
      break;
    }
  }
}

void CollectionSetChooser::clear() {
  _regions.clear();
  _front = 0;
  _end = 0;
  _remaining_reclaimable_bytes = 0;
}

class ParKnownGarbageHRClosure: public HeapRegionClosure {
  G1CollectedHeap* _g1h;
  CSetChooserParUpdater _cset_updater;

public:
  ParKnownGarbageHRClosure(CollectionSetChooser* hrSorted,
                           uint chunk_size) :
    _g1h(G1CollectedHeap::heap()),
    _cset_updater(hrSorted, true /* parallel */, chunk_size) { }

  bool do_heap_region(HeapRegion* r) {
    // We will skip any region that's currently used as an old GC
    // alloc region (we should not consider those for collection
    // before we fill them up).
    if (_cset_updater.should_add(r) && !_g1h->is_old_gc_alloc_region(r)) {
      _cset_updater.add_region(r);
    } else if (r->is_old()) {
      // Keep remembered sets for humongous regions, otherwise clean out remembered
      // sets for old regions.
      r->rem_set()->clear(true /* only_cardset */);
    } else {
      assert(!r->is_old() || !r->rem_set()->is_tracked(),
             "Missed to clear unused remembered set of region %u (%s) that is %s",
             r->hrm_index(), r->get_type_str(), r->rem_set()->get_state_str());
    }
    return false;
  }
};

class ParKnownGarbageTask: public AbstractGangTask {
  CollectionSetChooser* _hrSorted;
  uint _chunk_size;
  G1CollectedHeap* _g1h;
  HeapRegionClaimer _hrclaimer;

public:
  ParKnownGarbageTask(CollectionSetChooser* hrSorted, uint chunk_size, uint n_workers) :
      AbstractGangTask("ParKnownGarbageTask"),
      _hrSorted(hrSorted), _chunk_size(chunk_size),
      _g1h(G1CollectedHeap::heap()), _hrclaimer(n_workers) {}

  void work(uint worker_id) {
    ParKnownGarbageHRClosure par_known_garbage_cl(_hrSorted, _chunk_size);
    _g1h->heap_region_par_iterate_from_worker_offset(&par_known_garbage_cl, &_hrclaimer, worker_id);
  }
};

uint CollectionSetChooser::calculate_parallel_work_chunk_size(uint n_workers, uint n_regions) const {
  assert(n_workers > 0, "Active gc workers should be greater than 0");
  const uint overpartition_factor = 4;
  const uint min_chunk_size = MAX2(n_regions / n_workers, 1U);
  return MAX2(n_regions / (n_workers * overpartition_factor), min_chunk_size);
}

bool CollectionSetChooser::region_occupancy_low_enough_for_evac(size_t live_bytes) {
  return live_bytes < mixed_gc_live_threshold_bytes();
}

bool CollectionSetChooser::should_add(HeapRegion* hr) const {
  return !hr->is_young() &&
         !hr->is_pinned() &&
         region_occupancy_low_enough_for_evac(hr->live_bytes()) &&
         hr->rem_set()->is_complete();
}

void CollectionSetChooser::rebuild(WorkGang* workers, uint n_regions) {
  clear();

  uint n_workers = workers->active_workers();

  uint chunk_size = calculate_parallel_work_chunk_size(n_workers, n_regions);
  prepare_for_par_region_addition(n_workers, n_regions, chunk_size);

  ParKnownGarbageTask par_known_garbage_task(this, chunk_size, n_workers);
  workers->run_task(&par_known_garbage_task);

  sort_regions();
}
