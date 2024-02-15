/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1YoungGCAllocationFailureInjector.inline.hpp"
#include "gc/g1/g1_globals.hpp"

#if ALLOCATION_FAILURE_INJECTOR

class SelectAllocationFailureRegionClosure : public HeapRegionClosure {
  CHeapBitMap& _allocation_failure_regions;
  size_t _allocation_failure_regions_num;

public:
  SelectAllocationFailureRegionClosure(CHeapBitMap& allocation_failure_regions, size_t cset_length) :
    _allocation_failure_regions(allocation_failure_regions),
    _allocation_failure_regions_num(cset_length * G1GCAllocationFailureALotCSetPercent / 100) { }

  bool do_heap_region(HeapRegion* r) override {
    assert(r->in_collection_set(), "must be");
    if (_allocation_failure_regions_num > 0) {
      _allocation_failure_regions.set_bit(r->hrm_index());
      --_allocation_failure_regions_num;
    }
    return _allocation_failure_regions_num == 0;
  }
};

G1YoungGCAllocationFailureInjector::G1YoungGCAllocationFailureInjector()
  : _inject_allocation_failure_for_current_gc(),
    _last_collection_with_allocation_failure(),
    _allocation_failure_regions(mtGC) {}

void G1YoungGCAllocationFailureInjector::select_allocation_failure_regions() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  _allocation_failure_regions.reinitialize(g1h->max_reserved_regions());
  SelectAllocationFailureRegionClosure closure(_allocation_failure_regions, g1h->collection_set()->cur_length());
  g1h->collection_set_iterate_all(&closure);
}

bool G1YoungGCAllocationFailureInjector::arm_if_needed_for_gc_type(bool for_young_only_phase,
                                                                   bool during_concurrent_start,
                                                                   bool mark_or_rebuild_in_progress) {
  bool res = false;
  if (mark_or_rebuild_in_progress) {
    res |= G1GCAllocationFailureALotDuringConcMark;
  }
  if (during_concurrent_start) {
    res |= G1GCAllocationFailureALotDuringConcurrentStart;
  }
  if (for_young_only_phase) {
    res |= G1GCAllocationFailureALotDuringYoungGC;
  } else {
    // GCs are mixed
    res |= G1GCAllocationFailureALotDuringMixedGC;
  }
  return res;
}

void G1YoungGCAllocationFailureInjector::arm_if_needed() {
  if (G1GCAllocationFailureALot) {
    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    // Check if we have gone over the interval.
    const size_t gc_num = g1h->total_collections();
    const size_t elapsed_gcs = gc_num - _last_collection_with_allocation_failure;

    _inject_allocation_failure_for_current_gc = (elapsed_gcs >= G1GCAllocationFailureALotInterval);

    // Now check if evacuation failure injection should be enabled for the current GC.
    G1CollectorState* collector_state = g1h->collector_state();
    const bool in_young_only_phase = collector_state->in_young_only_phase();
    const bool in_concurrent_start_gc = collector_state->in_concurrent_start_gc();
    const bool mark_or_rebuild_in_progress = collector_state->mark_or_rebuild_in_progress();

    _inject_allocation_failure_for_current_gc &=
      arm_if_needed_for_gc_type(in_young_only_phase,
                                in_concurrent_start_gc,
                                mark_or_rebuild_in_progress);

    if (_inject_allocation_failure_for_current_gc) {
      select_allocation_failure_regions();
    }
  }
}

void G1YoungGCAllocationFailureInjector::reset() {
  _last_collection_with_allocation_failure = G1CollectedHeap::heap()->total_collections();
  _inject_allocation_failure_for_current_gc = false;
}

#endif // #if ALLOCATION_FAILURE_INJECTOR
