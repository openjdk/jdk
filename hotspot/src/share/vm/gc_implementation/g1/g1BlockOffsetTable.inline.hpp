/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1BLOCKOFFSETTABLE_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1BLOCKOFFSETTABLE_INLINE_HPP

#include "gc_implementation/g1/g1BlockOffsetTable.hpp"
#include "memory/space.hpp"

inline HeapWord* G1BlockOffsetTable::block_start(const void* addr) {
  if (addr >= _bottom && addr < _end) {
    return block_start_unsafe(addr);
  } else {
    return NULL;
  }
}

inline HeapWord*
G1BlockOffsetTable::block_start_const(const void* addr) const {
  if (addr >= _bottom && addr < _end) {
    return block_start_unsafe_const(addr);
  } else {
    return NULL;
  }
}

inline size_t G1BlockOffsetSharedArray::index_for(const void* p) const {
  char* pc = (char*)p;
  assert(pc >= (char*)_reserved.start() &&
         pc <  (char*)_reserved.end(),
         "p not in range.");
  size_t delta = pointer_delta(pc, _reserved.start(), sizeof(char));
  size_t result = delta >> LogN;
  assert(result < _vs.committed_size(), "bad index from address");
  return result;
}

inline HeapWord*
G1BlockOffsetSharedArray::address_for_index(size_t index) const {
  assert(index < _vs.committed_size(), "bad index");
  HeapWord* result = _reserved.start() + (index << LogN_words);
  assert(result >= _reserved.start() && result < _reserved.end(),
         "bad address from index");
  return result;
}

inline HeapWord*
G1BlockOffsetArray::block_at_or_preceding(const void* addr,
                                          bool has_max_index,
                                          size_t max_index) const {
  assert(_array->offset_array(0) == 0, "objects can't cross covered areas");
  size_t index = _array->index_for(addr);
  // We must make sure that the offset table entry we use is valid.  If
  // "addr" is past the end, start at the last known one and go forward.
  if (has_max_index) {
    index = MIN2(index, max_index);
  }
  HeapWord* q = _array->address_for_index(index);

  uint offset = _array->offset_array(index);  // Extend u_char to uint.
  while (offset >= N_words) {
    // The excess of the offset from N_words indicates a power of Base
    // to go back by.
    size_t n_cards_back = BlockOffsetArray::entry_to_cards_back(offset);
    q -= (N_words * n_cards_back);
    assert(q >= _sp->bottom(), "Went below bottom!");
    index -= n_cards_back;
    offset = _array->offset_array(index);
  }
  assert(offset < N_words, "offset too large");
  q -= offset;
  return q;
}

inline HeapWord*
G1BlockOffsetArray::
forward_to_block_containing_addr_const(HeapWord* q, HeapWord* n,
                                       const void* addr) const {
  if (csp() != NULL) {
    if (addr >= csp()->top()) return csp()->top();
    while (n <= addr) {
      q = n;
      oop obj = oop(q);
      if (obj->klass_or_null() == NULL) return q;
      n += obj->size();
    }
  } else {
    while (n <= addr) {
      q = n;
      oop obj = oop(q);
      if (obj->klass_or_null() == NULL) return q;
      n += _sp->block_size(q);
    }
  }
  assert(q <= n, "wrong order for q and addr");
  assert(addr < n, "wrong order for addr and n");
  return q;
}

inline HeapWord*
G1BlockOffsetArray::forward_to_block_containing_addr(HeapWord* q,
                                                     const void* addr) {
  if (oop(q)->klass_or_null() == NULL) return q;
  HeapWord* n = q + _sp->block_size(q);
  // In the normal case, where the query "addr" is a card boundary, and the
  // offset table chunks are the same size as cards, the block starting at
  // "q" will contain addr, so the test below will fail, and we'll fall
  // through quickly.
  if (n <= addr) {
    q = forward_to_block_containing_addr_slow(q, n, addr);
  }
  assert(q <= addr, "wrong order for current and arg");
  return q;
}

//////////////////////////////////////////////////////////////////////////
// BlockOffsetArrayNonContigSpace inlines
//////////////////////////////////////////////////////////////////////////
inline void G1BlockOffsetArray::freed(HeapWord* blk_start, HeapWord* blk_end) {
  // Verify that the BOT shows [blk_start, blk_end) to be one block.
  verify_single_block(blk_start, blk_end);
  // adjust _unallocated_block upward or downward
  // as appropriate
  if (BlockOffsetArrayUseUnallocatedBlock) {
    assert(_unallocated_block <= _end,
           "Inconsistent value for _unallocated_block");
    if (blk_end >= _unallocated_block && blk_start <= _unallocated_block) {
      // CMS-specific note: a block abutting _unallocated_block to
      // its left is being freed, a new block is being added or
      // we are resetting following a compaction
      _unallocated_block = blk_start;
    }
  }
}

inline void G1BlockOffsetArray::freed(HeapWord* blk, size_t size) {
  freed(blk, blk + size);
}

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1BLOCKOFFSETTABLE_INLINE_HPP
