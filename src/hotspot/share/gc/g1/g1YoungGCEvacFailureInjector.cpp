/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1YoungGCEvacFailureInjector.inline.hpp"
#include "gc/g1/g1_globals.hpp"

#if EVAC_FAILURE_INJECTOR

class SelectEvacFailureRegionClosure : public HeapRegionClosure {
  CHeapBitMap& _evac_failure_regions;
  size_t _evac_failure_regions_num;

public:
  SelectEvacFailureRegionClosure(CHeapBitMap& evac_failure_regions, size_t cset_length) :
    _evac_failure_regions(evac_failure_regions),
    _evac_failure_regions_num(cset_length * G1EvacuationFailureALotCSetPercent / 100) { }

  bool do_heap_region(HeapRegion* r) override {
    assert(r->in_collection_set(), "must be");
    if (_evac_failure_regions_num > 0) {
      _evac_failure_regions.set_bit(r->hrm_index());
      --_evac_failure_regions_num;
      return false;
    }
    return true;
  }
};

void G1YoungGCEvacFailureInjector::select_evac_failure_regions() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  _evac_failure_regions.reinitialize(g1h->max_reserved_regions());
  SelectEvacFailureRegionClosure closure(_evac_failure_regions, g1h->collection_set()->cur_length());
  g1h->collection_set_iterate_all(&closure);
}

bool G1YoungGCEvacFailureInjector::arm_if_needed_for_gc_type(bool for_young_gc,
                                                             bool during_concurrent_start,
                                                             bool mark_or_rebuild_in_progress) {
  bool res = false;
  if (mark_or_rebuild_in_progress) {
    res |= G1EvacuationFailureALotDuringConcMark;
  }
  if (during_concurrent_start) {
    res |= G1EvacuationFailureALotDuringConcurrentStart;
  }
  if (for_young_gc) {
    res |= G1EvacuationFailureALotDuringYoungGC;
  } else {
    // GCs are mixed
    res |= G1EvacuationFailureALotDuringMixedGC;
  }
  return res;
}

void G1YoungGCEvacFailureInjector::arm_if_needed() {
  if (G1EvacuationFailureALot) {
    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    // Check if we have gone over the interval.
    const size_t gc_num = g1h->total_collections();
    const size_t elapsed_gcs = gc_num - _last_collection_with_evacuation_failure;

    _inject_evacuation_failure_for_current_gc = (elapsed_gcs >= G1EvacuationFailureALotInterval);

    // Now check if evacuation failure injection should be enabled for the current GC.
    G1CollectorState* collector_state = g1h->collector_state();
    const bool in_young_only_phase = collector_state->in_young_only_phase();
    const bool in_concurrent_start_gc = collector_state->in_concurrent_start_gc();
    const bool mark_or_rebuild_in_progress = collector_state->mark_or_rebuild_in_progress();

    _inject_evacuation_failure_for_current_gc &=
      arm_if_needed_for_gc_type(in_young_only_phase,
                                in_concurrent_start_gc,
                                mark_or_rebuild_in_progress);

    if (_inject_evacuation_failure_for_current_gc) {
      select_evac_failure_regions();
    }
  }
}

void G1YoungGCEvacFailureInjector::reset() {
  _last_collection_with_evacuation_failure = G1CollectedHeap::heap()->total_collections();
  _inject_evacuation_failure_for_current_gc = false;
}

#endif // #if EVAC_FAILURE_INJECTOR
