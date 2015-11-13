/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_HEAPREGION_INLINE_HPP
#define SHARE_VM_GC_G1_HEAPREGION_INLINE_HPP

#include "gc/g1/g1BlockOffsetTable.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/shared/space.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.inline.hpp"

inline HeapWord* G1OffsetTableContigSpace::allocate_impl(size_t min_word_size,
                                                         size_t desired_word_size,
                                                         size_t* actual_size) {
  HeapWord* obj = top();
  size_t available = pointer_delta(end(), obj);
  size_t want_to_allocate = MIN2(available, desired_word_size);
  if (want_to_allocate >= min_word_size) {
    HeapWord* new_top = obj + want_to_allocate;
    set_top(new_top);
    assert(is_aligned(obj) && is_aligned(new_top), "checking alignment");
    *actual_size = want_to_allocate;
    return obj;
  } else {
    return NULL;
  }
}

inline HeapWord* G1OffsetTableContigSpace::par_allocate_impl(size_t min_word_size,
                                                             size_t desired_word_size,
                                                             size_t* actual_size) {
  do {
    HeapWord* obj = top();
    size_t available = pointer_delta(end(), obj);
    size_t want_to_allocate = MIN2(available, desired_word_size);
    if (want_to_allocate >= min_word_size) {
      HeapWord* new_top = obj + want_to_allocate;
      HeapWord* result = (HeapWord*)Atomic::cmpxchg_ptr(new_top, top_addr(), obj);
      // result can be one of two:
      //  the old top value: the exchange succeeded
      //  otherwise: the new value of the top is returned.
      if (result == obj) {
        assert(is_aligned(obj) && is_aligned(new_top), "checking alignment");
        *actual_size = want_to_allocate;
        return obj;
      }
    } else {
      return NULL;
    }
  } while (true);
}

inline HeapWord* G1OffsetTableContigSpace::allocate(size_t min_word_size,
                                                    size_t desired_word_size,
                                                    size_t* actual_size) {
  HeapWord* res = allocate_impl(min_word_size, desired_word_size, actual_size);
  if (res != NULL) {
    _offsets.alloc_block(res, *actual_size);
  }
  return res;
}

inline HeapWord* G1OffsetTableContigSpace::allocate(size_t word_size) {
  size_t temp;
  return allocate(word_size, word_size, &temp);
}

inline HeapWord* G1OffsetTableContigSpace::par_allocate(size_t word_size) {
  size_t temp;
  return par_allocate(word_size, word_size, &temp);
}

// Because of the requirement of keeping "_offsets" up to date with the
// allocations, we sequentialize these with a lock.  Therefore, best if
// this is used for larger LAB allocations only.
inline HeapWord* G1OffsetTableContigSpace::par_allocate(size_t min_word_size,
                                                        size_t desired_word_size,
                                                        size_t* actual_size) {
  MutexLocker x(&_par_alloc_lock);
  return allocate(min_word_size, desired_word_size, actual_size);
}

inline HeapWord* G1OffsetTableContigSpace::block_start(const void* p) {
  return _offsets.block_start(p);
}

inline HeapWord*
G1OffsetTableContigSpace::block_start_const(const void* p) const {
  return _offsets.block_start_const(p);
}

inline bool
HeapRegion::block_is_obj(const HeapWord* p) const {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  if (!this->is_in(p)) {
    assert(is_continues_humongous(), "This case can only happen for humongous regions");
    return (p == humongous_start_region()->bottom());
  }
  if (ClassUnloadingWithConcurrentMark) {
    return !g1h->is_obj_dead(oop(p), this);
  }
  return p < top();
}

inline size_t
HeapRegion::block_size(const HeapWord *addr) const {
  if (addr == top()) {
    return pointer_delta(end(), addr);
  }

  if (block_is_obj(addr)) {
    return oop(addr)->size();
  }

  assert(ClassUnloadingWithConcurrentMark,
         "All blocks should be objects if G1 Class Unloading isn't used. "
         "HR: [" PTR_FORMAT ", " PTR_FORMAT ", " PTR_FORMAT ") "
         "addr: " PTR_FORMAT,
         p2i(bottom()), p2i(top()), p2i(end()), p2i(addr));

  // Old regions' dead objects may have dead classes
  // We need to find the next live object in some other
  // manner than getting the oop size
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  HeapWord* next = g1h->concurrent_mark()->prevMarkBitMap()->
      getNextMarkedWordAddress(addr, prev_top_at_mark_start());

  assert(next > addr, "must get the next live object");
  return pointer_delta(next, addr);
}

inline HeapWord* HeapRegion::par_allocate_no_bot_updates(size_t min_word_size,
                                                         size_t desired_word_size,
                                                         size_t* actual_word_size) {
  assert(is_young(), "we can only skip BOT updates on young regions");
  return par_allocate_impl(min_word_size, desired_word_size, actual_word_size);
}

inline HeapWord* HeapRegion::allocate_no_bot_updates(size_t word_size) {
  size_t temp;
  return allocate_no_bot_updates(word_size, word_size, &temp);
}

inline HeapWord* HeapRegion::allocate_no_bot_updates(size_t min_word_size,
                                                     size_t desired_word_size,
                                                     size_t* actual_word_size) {
  assert(is_young(), "we can only skip BOT updates on young regions");
  return allocate_impl(min_word_size, desired_word_size, actual_word_size);
}

inline void HeapRegion::note_start_of_marking() {
  _next_marked_bytes = 0;
  _next_top_at_mark_start = top();
}

inline void HeapRegion::note_end_of_marking() {
  _prev_top_at_mark_start = _next_top_at_mark_start;
  _prev_marked_bytes = _next_marked_bytes;
  _next_marked_bytes = 0;
}

inline void HeapRegion::note_start_of_copying(bool during_initial_mark) {
  if (is_survivor()) {
    // This is how we always allocate survivors.
    assert(_next_top_at_mark_start == bottom(), "invariant");
  } else {
    if (during_initial_mark) {
      // During initial-mark we'll explicitly mark any objects on old
      // regions that are pointed to by roots. Given that explicit
      // marks only make sense under NTAMS it'd be nice if we could
      // check that condition if we wanted to. Given that we don't
      // know where the top of this region will end up, we simply set
      // NTAMS to the end of the region so all marks will be below
      // NTAMS. We'll set it to the actual top when we retire this region.
      _next_top_at_mark_start = end();
    } else {
      // We could have re-used this old region as to-space over a
      // couple of GCs since the start of the concurrent marking
      // cycle. This means that [bottom,NTAMS) will contain objects
      // copied up to and including initial-mark and [NTAMS, top)
      // will contain objects copied during the concurrent marking cycle.
      assert(top() >= _next_top_at_mark_start, "invariant");
    }
  }
}

inline void HeapRegion::note_end_of_copying(bool during_initial_mark) {
  if (is_survivor()) {
    // This is how we always allocate survivors.
    assert(_next_top_at_mark_start == bottom(), "invariant");
  } else {
    if (during_initial_mark) {
      // See the comment for note_start_of_copying() for the details
      // on this.
      assert(_next_top_at_mark_start == end(), "pre-condition");
      _next_top_at_mark_start = top();
    } else {
      // See the comment for note_start_of_copying() for the details
      // on this.
      assert(top() >= _next_top_at_mark_start, "invariant");
    }
  }
}

inline bool HeapRegion::in_collection_set() const {
  return G1CollectedHeap::heap()->is_in_cset(this);
}

inline HeapRegion* HeapRegion::next_in_collection_set() const {
  assert(in_collection_set(), "should only invoke on member of CS.");
  assert(_next_in_special_set == NULL ||
         _next_in_special_set->in_collection_set(),
         "Malformed CS.");
  return _next_in_special_set;
}

void HeapRegion::set_next_in_collection_set(HeapRegion* r) {
  assert(in_collection_set(), "should only invoke on member of CS.");
  assert(r == NULL || r->in_collection_set(), "Malformed CS.");
  _next_in_special_set = r;
}

#endif // SHARE_VM_GC_G1_HEAPREGION_INLINE_HPP
