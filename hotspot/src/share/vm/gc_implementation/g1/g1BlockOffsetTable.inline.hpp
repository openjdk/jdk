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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1BLOCKOFFSETTABLE_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1BLOCKOFFSETTABLE_INLINE_HPP

#include "gc_implementation/g1/g1BlockOffsetTable.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/heapRegion.inline.hpp"
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

#define check_index(index, msg)                                                \
  assert((index) < (_reserved.word_size() >> LogN_words),                      \
         err_msg("%s - index: "SIZE_FORMAT", _vs.committed_size: "SIZE_FORMAT, \
                 msg, (index), (_reserved.word_size() >> LogN_words)));        \
  assert(G1CollectedHeap::heap()->is_in_exact(address_for_index_raw(index)),   \
         err_msg("Index "SIZE_FORMAT" corresponding to "PTR_FORMAT             \
                 " (%u) is not in committed area.",                            \
                 (index),                                                      \
                 p2i(address_for_index_raw(index)),                            \
                 G1CollectedHeap::heap()->addr_to_region(address_for_index_raw(index))));

u_char G1BlockOffsetSharedArray::offset_array(size_t index) const {
  check_index(index, "index out of range");
  return _offset_array[index];
}

void G1BlockOffsetSharedArray::set_offset_array(size_t index, u_char offset) {
  check_index(index, "index out of range");
  set_offset_array_raw(index, offset);
}

void G1BlockOffsetSharedArray::set_offset_array(size_t index, HeapWord* high, HeapWord* low) {
  check_index(index, "index out of range");
  assert(high >= low, "addresses out of order");
  size_t offset = pointer_delta(high, low);
  check_offset(offset, "offset too large");
  set_offset_array(index, (u_char)offset);
}

void G1BlockOffsetSharedArray::set_offset_array(size_t left, size_t right, u_char offset) {
  check_index(right, "right index out of range");
  assert(left <= right, "indexes out of order");
  size_t num_cards = right - left + 1;
  if (UseMemSetInBOT) {
    memset(&_offset_array[left], offset, num_cards);
  } else {
    size_t i = left;
    const size_t end = i + num_cards;
    for (; i < end; i++) {
      _offset_array[i] = offset;
    }
  }
}

void G1BlockOffsetSharedArray::check_offset_array(size_t index, HeapWord* high, HeapWord* low) const {
  check_index(index, "index out of range");
  assert(high >= low, "addresses out of order");
  check_offset(pointer_delta(high, low), "offset too large");
  assert(_offset_array[index] == pointer_delta(high, low), "Wrong offset");
}

// Variant of index_for that does not check the index for validity.
inline size_t G1BlockOffsetSharedArray::index_for_raw(const void* p) const {
  return pointer_delta((char*)p, _reserved.start(), sizeof(char)) >> LogN;
}

inline size_t G1BlockOffsetSharedArray::index_for(const void* p) const {
  char* pc = (char*)p;
  assert(pc >= (char*)_reserved.start() &&
         pc <  (char*)_reserved.end(),
         err_msg("p (" PTR_FORMAT ") not in reserved [" PTR_FORMAT ", " PTR_FORMAT ")",
                 p2i(p), p2i(_reserved.start()), p2i(_reserved.end())));
  size_t result = index_for_raw(p);
  check_index(result, "bad index from address");
  return result;
}

inline HeapWord*
G1BlockOffsetSharedArray::address_for_index(size_t index) const {
  check_index(index, "index out of range");
  HeapWord* result = address_for_index_raw(index);
  assert(result >= _reserved.start() && result < _reserved.end(),
         err_msg("bad address from index result " PTR_FORMAT
                 " _reserved.start() " PTR_FORMAT " _reserved.end() "
                 PTR_FORMAT,
                 p2i(result), p2i(_reserved.start()), p2i(_reserved.end())));
  return result;
}

#undef check_index

inline size_t
G1BlockOffsetArray::block_size(const HeapWord* p) const {
  return gsp()->block_size(p);
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
    assert(q >= gsp()->bottom(), "Went below bottom!");
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
  if (addr >= gsp()->top()) return gsp()->top();
  while (n <= addr) {
    q = n;
    oop obj = oop(q);
    if (obj->klass_or_null() == NULL) return q;
    n += block_size(q);
  }
  assert(q <= n, "wrong order for q and addr");
  assert(addr < n, "wrong order for addr and n");
  return q;
}

inline HeapWord*
G1BlockOffsetArray::forward_to_block_containing_addr(HeapWord* q,
                                                     const void* addr) {
  if (oop(q)->klass_or_null() == NULL) return q;
  HeapWord* n = q + block_size(q);
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
