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
#include "gc_implementation/g1/g1CollectorPolicy.hpp"
#include "gc_implementation/g1/heapRegionSeq.hpp"
#include "utilities/taskqueue.hpp"

// Inline functions for G1CollectedHeap

inline HeapRegion*
G1CollectedHeap::heap_region_containing(const void* addr) const {
  HeapRegion* hr = _hrs->addr_to_region(addr);
  // hr can be null if addr in perm_gen
  if (hr != NULL && hr->continuesHumongous()) {
    hr = hr->humongous_start_region();
  }
  return hr;
}

inline HeapRegion*
G1CollectedHeap::heap_region_containing_raw(const void* addr) const {
  assert(_g1_reserved.contains(addr), "invariant");
  size_t index = pointer_delta(addr, _g1_reserved.start(), 1)
                                        >> HeapRegion::LogOfHRGrainBytes;

  HeapRegion* res = _hrs->at(index);
  assert(res == _hrs->addr_to_region(addr), "sanity");
  return res;
}

inline bool G1CollectedHeap::obj_in_cs(oop obj) {
  HeapRegion* r = _hrs->addr_to_region(obj);
  return r != NULL && r->in_collection_set();
}

// See the comment in the .hpp file about the locking protocol and
// assumptions of this method (and other related ones).
inline HeapWord*
G1CollectedHeap::allocate_from_cur_alloc_region(HeapRegion* cur_alloc_region,
                                                size_t word_size,
                                                bool with_heap_lock) {
  assert_not_at_safepoint();
  assert(with_heap_lock == Heap_lock->owned_by_self(),
         "with_heap_lock and Heap_lock->owned_by_self() should be a tautology");
  assert(cur_alloc_region != NULL, "pre-condition of the method");
  assert(cur_alloc_region->is_young(),
         "we only support young current alloc regions");
  assert(!isHumongous(word_size), "allocate_from_cur_alloc_region() "
         "should not be used for humongous allocations");
  assert(!cur_alloc_region->isHumongous(), "Catch a regression of this bug.");

  assert(!cur_alloc_region->is_empty(),
         err_msg("region ["PTR_FORMAT","PTR_FORMAT"] should not be empty",
                 cur_alloc_region->bottom(), cur_alloc_region->end()));
  HeapWord* result = cur_alloc_region->par_allocate_no_bot_updates(word_size);
  if (result != NULL) {
    assert(is_in(result), "result should be in the heap");

    if (with_heap_lock) {
      Heap_lock->unlock();
    }
    assert_heap_not_locked();
    // Do the dirtying after we release the Heap_lock.
    dirty_young_block(result, word_size);
    return result;
  }

  if (with_heap_lock) {
    assert_heap_locked();
  } else {
    assert_heap_not_locked();
  }
  return NULL;
}

// See the comment in the .hpp file about the locking protocol and
// assumptions of this method (and other related ones).
inline HeapWord*
G1CollectedHeap::attempt_allocation(size_t word_size) {
  assert_heap_not_locked_and_not_at_safepoint();
  assert(!isHumongous(word_size), "attempt_allocation() should not be called "
         "for humongous allocation requests");

  HeapRegion* cur_alloc_region = _cur_alloc_region;
  if (cur_alloc_region != NULL) {
    HeapWord* result = allocate_from_cur_alloc_region(cur_alloc_region,
                                                   word_size,
                                                   false /* with_heap_lock */);
    assert_heap_not_locked();
    if (result != NULL) {
      return result;
    }
  }

  // Our attempt to allocate lock-free failed as the current
  // allocation region is either NULL or full. So, we'll now take the
  // Heap_lock and retry.
  Heap_lock->lock();

  HeapWord* result = attempt_allocation_locked(word_size);
  if (result != NULL) {
    assert_heap_not_locked();
    return result;
  }

  assert_heap_locked();
  return NULL;
}

inline void
G1CollectedHeap::retire_cur_alloc_region_common(HeapRegion* cur_alloc_region) {
  assert_heap_locked_or_at_safepoint();
  assert(cur_alloc_region != NULL && cur_alloc_region == _cur_alloc_region,
         "pre-condition of the call");
  assert(cur_alloc_region->is_young(),
         "we only support young current alloc regions");

  // The region is guaranteed to be young
  g1_policy()->add_region_to_incremental_cset_lhs(cur_alloc_region);
  _summary_bytes_used += cur_alloc_region->used();
  _cur_alloc_region = NULL;
}

inline HeapWord*
G1CollectedHeap::attempt_allocation_locked(size_t word_size) {
  assert_heap_locked_and_not_at_safepoint();
  assert(!isHumongous(word_size), "attempt_allocation_locked() "
         "should not be called for humongous allocation requests");

  // First, reread the current alloc region and retry the allocation
  // in case somebody replaced it while we were waiting to get the
  // Heap_lock.
  HeapRegion* cur_alloc_region = _cur_alloc_region;
  if (cur_alloc_region != NULL) {
    HeapWord* result = allocate_from_cur_alloc_region(
                                                  cur_alloc_region, word_size,
                                                  true /* with_heap_lock */);
    if (result != NULL) {
      assert_heap_not_locked();
      return result;
    }

    // We failed to allocate out of the current alloc region, so let's
    // retire it before getting a new one.
    retire_cur_alloc_region(cur_alloc_region);
  }

  assert_heap_locked();
  // Try to get a new region and allocate out of it
  HeapWord* result = replace_cur_alloc_region_and_allocate(word_size,
                                                     false, /* at_safepoint */
                                                     true,  /* do_dirtying */
                                                     false  /* can_expand */);
  if (result != NULL) {
    assert_heap_not_locked();
    return result;
  }

  assert_heap_locked();
  return NULL;
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
