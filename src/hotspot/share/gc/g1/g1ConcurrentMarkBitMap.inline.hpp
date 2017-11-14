/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1CONCURRENTMARKBITMAP_INLINE_HPP
#define SHARE_VM_GC_G1_G1CONCURRENTMARKBITMAP_INLINE_HPP

#include "gc/g1/g1ConcurrentMarkBitMap.hpp"
#include "memory/memRegion.hpp"
#include "utilities/align.hpp"
#include "utilities/bitMap.inline.hpp"

inline bool G1CMBitMap::iterate(G1CMBitMapClosure* cl, MemRegion mr) {
  assert(!mr.is_empty(), "Does not support empty memregion to iterate over");
  assert(_covered.contains(mr),
         "Given MemRegion from " PTR_FORMAT " to " PTR_FORMAT " not contained in heap area",
         p2i(mr.start()), p2i(mr.end()));

  BitMap::idx_t const end_offset = addr_to_offset(mr.end());
  BitMap::idx_t offset = _bm.get_next_one_offset(addr_to_offset(mr.start()), end_offset);

  while (offset < end_offset) {
    HeapWord* const addr = offset_to_addr(offset);
    if (!cl->do_addr(addr)) {
      return false;
    }
    size_t const obj_size = (size_t)((oop)addr)->size();
    offset = _bm.get_next_one_offset(offset + (obj_size >> _shifter), end_offset);
  }
  return true;
}

inline HeapWord* G1CMBitMap::get_next_marked_addr(const HeapWord* addr,
                                                  const HeapWord* limit) const {
  assert(limit != NULL, "limit must not be NULL");
  // Round addr up to a possible object boundary to be safe.
  size_t const addr_offset = addr_to_offset(align_up(addr, HeapWordSize << _shifter));
  size_t const limit_offset = addr_to_offset(limit);
  size_t const nextOffset = _bm.get_next_one_offset(addr_offset, limit_offset);
  return offset_to_addr(nextOffset);
}

#ifdef ASSERT
inline void G1CMBitMap::check_mark(HeapWord* addr) {
  assert(G1CollectedHeap::heap()->is_in_exact(addr),
         "Trying to access bitmap " PTR_FORMAT " for address " PTR_FORMAT " not in the heap.",
         p2i(this), p2i(addr));
}
#endif

inline void G1CMBitMap::mark(HeapWord* addr) {
  check_mark(addr);
  _bm.set_bit(addr_to_offset(addr));
}

inline void G1CMBitMap::clear(HeapWord* addr) {
  check_mark(addr);
  _bm.clear_bit(addr_to_offset(addr));
}

inline bool G1CMBitMap::par_mark(HeapWord* addr) {
  check_mark(addr);
  return _bm.par_set_bit(addr_to_offset(addr));
}

inline bool G1CMBitMap::par_mark(oop obj) {
  return par_mark((HeapWord*) obj);
}

inline bool G1CMBitMap::is_marked(oop obj) const{
  return is_marked((HeapWord*) obj);
}

inline void G1CMBitMap::clear(oop obj) {
  clear((HeapWord*) obj);
}

#endif // SHARE_VM_GC_G1_G1CONCURRENTMARKBITMAP_INLINE_HPP
