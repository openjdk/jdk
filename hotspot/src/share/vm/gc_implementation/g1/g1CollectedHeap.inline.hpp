/*
 * Copyright (c) 2001, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/orderAccess.inline.hpp"
#include "utilities/taskqueue.hpp"

// Inline functions for G1CollectedHeap

// Return the region with the given index. It assumes the index is valid.
inline HeapRegion* G1CollectedHeap::region_at(uint index) const { return _hrs.at(index); }

inline uint G1CollectedHeap::addr_to_region(HeapWord* addr) const {
  assert(is_in_reserved(addr),
         err_msg("Cannot calculate region index for address "PTR_FORMAT" that is outside of the heap ["PTR_FORMAT", "PTR_FORMAT")",
                 p2i(addr), p2i(_reserved.start()), p2i(_reserved.end())));
  return (uint)(pointer_delta(addr, _reserved.start(), sizeof(uint8_t)) >> HeapRegion::LogOfHRGrainBytes);
}

inline HeapWord* G1CollectedHeap::bottom_addr_for_region(uint index) const {
  return _hrs.reserved().start() + index * HeapRegion::GrainWords;
}

template <class T>
inline HeapRegion* G1CollectedHeap::heap_region_containing_raw(const T addr) const {
  assert(addr != NULL, "invariant");
  assert(is_in_g1_reserved((const void*) addr),
      err_msg("Address "PTR_FORMAT" is outside of the heap ranging from ["PTR_FORMAT" to "PTR_FORMAT")",
          p2i((void*)addr), p2i(g1_reserved().start()), p2i(g1_reserved().end())));
  return _hrs.addr_to_region((HeapWord*) addr);
}

template <class T>
inline HeapRegion* G1CollectedHeap::heap_region_containing(const T addr) const {
  HeapRegion* hr = heap_region_containing_raw(addr);
  if (hr->continuesHumongous()) {
    return hr->humongous_start_region();
  }
  return hr;
}

inline void G1CollectedHeap::reset_gc_time_stamp() {
  _gc_time_stamp = 0;
  OrderAccess::fence();
  // Clear the cached CSet starting regions and time stamps.
  // Their validity is dependent on the GC timestamp.
  clear_cset_start_regions();
}

inline void G1CollectedHeap::increment_gc_time_stamp() {
  ++_gc_time_stamp;
  OrderAccess::fence();
}

inline void G1CollectedHeap::old_set_remove(HeapRegion* hr) {
  _old_set.remove(hr);
}

inline bool G1CollectedHeap::obj_in_cs(oop obj) {
  HeapRegion* r = _hrs.addr_to_region((HeapWord*) obj);
  return r != NULL && r->in_collection_set();
}

inline HeapWord* G1CollectedHeap::attempt_allocation(size_t word_size,
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
  assert(word_size > 0, "pre-condition");
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

// This is a fast test on whether a reference points into the
// collection set or not. Assume that the reference
// points into the heap.
inline bool G1CollectedHeap::is_in_cset(oop obj) {
  bool ret = _in_cset_fast_test.is_in_cset((HeapWord*)obj);
  // let's make sure the result is consistent with what the slower
  // test returns
  assert( ret || !obj_in_cs(obj), "sanity");
  assert(!ret ||  obj_in_cs(obj), "sanity");
  return ret;
}

bool G1CollectedHeap::is_in_cset_or_humongous(const oop obj) {
  return _in_cset_fast_test.is_in_cset_or_humongous((HeapWord*)obj);
}

G1CollectedHeap::in_cset_state_t G1CollectedHeap::in_cset_state(const oop obj) {
  return _in_cset_fast_test.at((HeapWord*)obj);
}

void G1CollectedHeap::register_humongous_region_with_in_cset_fast_test(uint index) {
  _in_cset_fast_test.set_humongous(index);
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

inline bool G1CollectedHeap::evacuation_should_fail() {
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

inline bool G1CollectedHeap::is_in_young(const oop obj) {
  if (obj == NULL) {
    return false;
  }
  return heap_region_containing(obj)->is_young();
}

// We don't need barriers for initializing stores to objects
// in the young gen: for the SATB pre-barrier, there is no
// pre-value that needs to be remembered; for the remembered-set
// update logging post-barrier, we don't maintain remembered set
// information for young gen objects.
inline bool G1CollectedHeap::can_elide_initializing_store_barrier(oop new_obj) {
  return is_in_young(new_obj);
}

inline bool G1CollectedHeap::is_obj_dead(const oop obj) const {
  if (obj == NULL) {
    return false;
  }
  return is_obj_dead(obj, heap_region_containing(obj));
}

inline bool G1CollectedHeap::is_obj_ill(const oop obj) const {
  if (obj == NULL) {
    return false;
  }
  return is_obj_ill(obj, heap_region_containing(obj));
}

inline void G1CollectedHeap::set_humongous_is_live(oop obj) {
  uint region = addr_to_region((HeapWord*)obj);
  // We not only set the "live" flag in the humongous_is_live table, but also
  // reset the entry in the _in_cset_fast_test table so that subsequent references
  // to the same humongous object do not go into the slow path again.
  // This is racy, as multiple threads may at the same time enter here, but this
  // is benign.
  // During collection we only ever set the "live" flag, and only ever clear the
  // entry in the in_cset_fast_table.
  // We only ever evaluate the contents of these tables (in the VM thread) after
  // having synchronized the worker threads with the VM thread, or in the same
  // thread (i.e. within the VM thread).
  if (!_humongous_is_live.is_live(region)) {
    _humongous_is_live.set_live(region);
    _in_cset_fast_test.clear_humongous(region);
  }
}

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1COLLECTEDHEAP_INLINE_HPP
