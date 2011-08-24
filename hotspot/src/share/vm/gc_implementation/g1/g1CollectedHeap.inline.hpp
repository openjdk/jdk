/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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
                                    unsigned int* gc_count_before_ret) {
  assert_heap_not_locked_and_not_at_safepoint();
  assert(!isHumongous(word_size), "attempt_allocation() should not "
         "be called for humongous allocation requests");

  HeapWord* result = _mutator_alloc_region.attempt_allocation(word_size,
                                                      false /* bot_updates */);
  if (result == NULL) {
    result = attempt_allocation_slow(word_size, gc_count_before_ret);
  }
  assert_heap_not_locked();
  if (result != NULL) {
    dirty_young_block(result, word_size);
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
  ((CardTableModRefBS*)_g1h->barrier_set())->dirty(mr);
}

inline RefToScanQueue* G1CollectedHeap::task_queue(int i) const {
  return _task_queues->queue(i);
}

inline  bool G1CollectedHeap::isMarkedPrev(oop obj) const {
  return _cm->prevMarkBitMap()->isMarked((HeapWord *)obj);
}

inline bool G1CollectedHeap::isMarkedNext(oop obj) const {
  return _cm->nextMarkBitMap()->isMarked((HeapWord *)obj);
}

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1COLLECTEDHEAP_INLINE_HPP
