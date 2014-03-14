/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1COLLECTEDHEAP_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1COLLECTEDHEAP_INLINE_HPP

#include "gc_implementation/g1/concurrentMark.hpp"
#include "gc_implementation/g1/g1CollectedHeap.hpp"
#include "gc_implementation/g1/g1AllocRegion.inline.hpp"
#include "gc_implementation/g1/g1CollectorPolicy.hpp"
#include "gc_implementation/g1/g1SATBCardTableModRefBS.hpp"
#include "gc_implementation/g1/heapRegionSet.inline.hpp"
#include "gc_implementation/g1/heapRegionSeq.inline.hpp"
#include "utilities/taskqueue.hpp"

// Inline functions for G1CollectedHeap

template <class T>
inline HeapRegion*
G1CollectedHeap::heap_region_containing(const T addr) const {
  HeapRegion* hr = _hrs.addr_to_region((HeapWord*) addr);
  // hr can be null if addr in perm_gen
  if (hr != NULL && hr->continuesHumongous()) {
    hr = hr->humongous_start_region();
  }
  return hr;
}

template <class T>
inline HeapRegion*
G1CollectedHeap::heap_region_containing_raw(const T addr) const {
  assert(_g1_reserved.contains((const void*) addr), "invariant");
  HeapRegion* res = _hrs.addr_to_region_unsafe((HeapWord*) addr);
  return res;
}

inline bool G1CollectedHeap::obj_in_cs(oop obj) {
  HeapRegion* r = _hrs.addr_to_region((HeapWord*) obj);
  return r != NULL && r->in_collection_set();
}

inline HeapWord*
G1CollectedHeap::attempt_allocation(size_t word_size,
                                    unsigned int* gc_count_before_ret,
                                    int* gclocker_retry_count_ret) {
  assert_heap_not_locked_and_not_at_safepoint();
  assert(!isHumongous(word_size), "attempt_allocation() should not "
         "be called for humongous allocation requests");

  HeapWord* result = _mutator_alloc_region.attempt_allocation(word_size,
                                                      false /* bot_updates */);
  if (result == NULL) {
    result = attempt_allocation_slow(word_size,
                                     gc_count_before_ret,
                                     gclocker_retry_count_ret);
  }
  assert_heap_not_locked();
  if (result != NULL) {
    dirty_young_block(result, word_size);
  }
  return result;
}

inline HeapWord* G1CollectedHeap::survivor_attempt_allocation(size_t
                                                              word_size) {
  assert(!isHumongous(word_size),
         "we should not be seeing humongous-size allocations in this path");

  HeapWord* result = _survivor_gc_alloc_region.attempt_allocation(word_size,
                                                      false /* bot_updates */);
  if (result == NULL) {
    MutexLockerEx x(FreeList_lock, Mutex::_no_safepoint_check_flag);
    result = _survivor_gc_alloc_region.attempt_allocation_locked(word_size,
                                                      false /* bot_updates */);
  }
  if (result != NULL) {
    dirty_young_block(result, word_size);
  }
  return result;
}

inline HeapWord* G1CollectedHeap::old_attempt_allocation(size_t word_size) {
  assert(!isHumongous(word_size),
         "we should not be seeing humongous-size allocations in this path");

  HeapWord* result = _old_gc_alloc_region.attempt_allocation(word_size,
                                                       true /* bot_updates */);
  if (result == NULL) {
    MutexLockerEx x(FreeList_lock, Mutex::_no_safepoint_check_flag);
    result = _old_gc_alloc_region.attempt_allocation_locked(word_size,
                                                       true /* bot_updates */);
  }
  return result;
}

// It dirties the cards that cover the block so that so that the post
// write barrier never queues anything when updating objects on this
// block. It is assumed (and in fact we assert) that the block
// belongs to a young region.
inline void
G1CollectedHeap::dirty_young_block(HeapWord* start, size_t word_size) {
  assert_heap_not_locked();

  // Assign the containing region to containing_hr so that we don't
  // have to keep calling heap_region_containing_raw() in the
  // asserts below.
  DEBUG_ONLY(HeapRegion* containing_hr = heap_region_containing_raw(start);)
  assert(containing_hr != NULL && start != NULL && word_size > 0,
         "pre-condition");
  assert(containing_hr->is_in(start), "it should contain start");
  assert(containing_hr->is_young(), "it should be young");
  assert(!containing_hr->isHumongous(), "it should not be humongous");

  HeapWord* end = start + word_size;
  assert(containing_hr->is_in(end - 1), "it should also contain end - 1");

  MemRegion mr(start, end);
  g1_barrier_set()->g1_mark_as_young(mr);
}

inline RefToScanQueue* G1CollectedHeap::task_queue(int i) const {
  return _task_queues->queue(i);
}

inline bool G1CollectedHeap::isMarkedPrev(oop obj) const {
  return _cm->prevMarkBitMap()->isMarked((HeapWord *)obj);
}

inline bool G1CollectedHeap::isMarkedNext(oop obj) const {
  return _cm->nextMarkBitMap()->isMarked((HeapWord *)obj);
}

#ifndef PRODUCT
// Support for G1EvacuationFailureALot

inline bool
G1CollectedHeap::evacuation_failure_alot_for_gc_type(bool gcs_are_young,
                                                     bool during_initial_mark,
                                                     bool during_marking) {
  bool res = false;
  if (during_marking) {
    res |= G1EvacuationFailureALotDuringConcMark;
  }
  if (during_initial_mark) {
    res |= G1EvacuationFailureALotDuringInitialMark;
  }
  if (gcs_are_young) {
    res |= G1EvacuationFailureALotDuringYoungGC;
  } else {
    // GCs are mixed
    res |= G1EvacuationFailureALotDuringMixedGC;
  }
  return res;
}

inline void
G1CollectedHeap::set_evacuation_failure_alot_for_current_gc() {
  if (G1EvacuationFailureALot) {
    // Note we can't assert that _evacuation_failure_alot_for_current_gc
    // is clear here. It may have been set during a previous GC but that GC
    // did not copy enough objects (i.e. G1EvacuationFailureALotCount) to
    // trigger an evacuation failure and clear the flags and and counts.

    // Check if we have gone over the interval.
    const size_t gc_num = total_collections();
    const size_t elapsed_gcs = gc_num - _evacuation_failure_alot_gc_number;

    _evacuation_failure_alot_for_current_gc = (elapsed_gcs >= G1EvacuationFailureALotInterval);

    // Now check if G1EvacuationFailureALot is enabled for the current GC type.
    const bool gcs_are_young = g1_policy()->gcs_are_young();
    const bool during_im = g1_policy()->during_initial_mark_pause();
    const bool during_marking = mark_in_progress();

    _evacuation_failure_alot_for_current_gc &=
      evacuation_failure_alot_for_gc_type(gcs_are_young,
                                          during_im,
                                          during_marking);
  }
}

inline bool
G1CollectedHeap::evacuation_should_fail() {
  if (!G1EvacuationFailureALot || !_evacuation_failure_alot_for_current_gc) {
    return false;
  }
  // G1EvacuationFailureALot is in effect for current GC
  // Access to _evacuation_failure_alot_count is not atomic;
  // the value does not have to be exact.
  if (++_evacuation_failure_alot_count < G1EvacuationFailureALotCount) {
    return false;
  }
  _evacuation_failure_alot_count = 0;
  return true;
}

inline void G1CollectedHeap::reset_evacuation_should_fail() {
  if (G1EvacuationFailureALot) {
    _evacuation_failure_alot_gc_number = total_collections();
    _evacuation_failure_alot_count = 0;
    _evacuation_failure_alot_for_current_gc = false;
  }
}
#endif  // #ifndef PRODUCT

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1COLLECTEDHEAP_INLINE_HPP
