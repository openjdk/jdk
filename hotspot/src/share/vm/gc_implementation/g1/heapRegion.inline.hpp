/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGION_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGION_INLINE_HPP

#include "gc_implementation/g1/g1BlockOffsetTable.inline.hpp"

inline HeapWord* G1OffsetTableContigSpace::allocate(size_t size) {
  HeapWord* res = ContiguousSpace::allocate(size);
  if (res != NULL) {
    _offsets.alloc_block(res, size);
  }
  return res;
}

// Because of the requirement of keeping "_offsets" up to date with the
// allocations, we sequentialize these with a lock.  Therefore, best if
// this is used for larger LAB allocations only.
inline HeapWord* G1OffsetTableContigSpace::par_allocate(size_t size) {
  MutexLocker x(&_par_alloc_lock);
  // Given that we take the lock no need to use par_allocate() here.
  HeapWord* res = ContiguousSpace::allocate(size);
  if (res != NULL) {
    _offsets.alloc_block(res, size);
  }
  return res;
}

inline HeapWord* G1OffsetTableContigSpace::block_start(const void* p) {
  return _offsets.block_start(p);
}

inline HeapWord*
G1OffsetTableContigSpace::block_start_const(const void* p) const {
  return _offsets.block_start_const(p);
}

inline void HeapRegion::note_start_of_marking() {
  _next_marked_bytes = 0;
  _next_top_at_mark_start = top();
}

inline void HeapRegion::note_end_of_marking() {
  _prev_top_at_mark_start = _next_top_at_mark_start;
  _prev_marked_bytes = _next_marked_bytes;
  _next_marked_bytes = 0;

  assert(_prev_marked_bytes <=
         (size_t) pointer_delta(prev_top_at_mark_start(), bottom()) *
         HeapWordSize, "invariant");
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

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGION_INLINE_HPP
