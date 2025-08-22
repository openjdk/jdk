/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_PARALLEL_OBJECTSTARTARRAY_HPP
#define SHARE_GC_PARALLEL_OBJECTSTARTARRAY_HPP

#include "gc/parallel/psVirtualspace.hpp"
#include "gc/shared/blockOffsetTable.hpp"
#include "gc/shared/cardTable.hpp"
#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "oops/oop.hpp"

//
// This class can be used to locate the beginning of an object in the
// covered region.
//

class ObjectStartArray : public CHeapObj<mtGC> {
  // The committed (old-gen heap) virtual space this object-start-array covers.
  DEBUG_ONLY(MemRegion  _covered_region;)

  // BOT array
  PSVirtualSpace* _virtual_space;

  // Biased array-start of BOT array for fast heap-addr / BOT entry translation
  uint8_t*        _offset_base;

  // Mapping from address to object start array entry
  uint8_t* entry_for_addr(const void* const p) const {
    assert(_covered_region.contains(p),
           "out of bounds access to object start array");
    uint8_t* result = &_offset_base[uintptr_t(p) >> CardTable::card_shift()];
    return result;
  }

  // Mapping from object start array entry to address of first word
  HeapWord* addr_for_entry(const uint8_t* const p) const {
    // _offset_base can be "negative", so can't use pointer_delta().
    size_t delta = p - _offset_base;
    HeapWord* result = (HeapWord*) (delta << CardTable::card_shift());
    assert(_covered_region.contains(result),
           "out of bounds accessor from card marking array");
    return result;
  }

  static HeapWord* align_up_by_card_size(HeapWord* const addr) {
    return align_up(addr, CardTable::card_size());
  }

  void update_for_block_work(HeapWord* blk_start, HeapWord* blk_end);

  void verify_for_block(HeapWord* blk_start, HeapWord* blk_end) const;

 public:
  ObjectStartArray(MemRegion covered_region);

  // Heap old-gen resizing
  void set_covered_region(MemRegion mr);

  static bool is_crossing_card_boundary(HeapWord* const blk_start,
                                        HeapWord* const blk_end) {
    HeapWord* cur_card_boundary = align_up_by_card_size(blk_start);
    // Strictly greater-than, since we check if this block *crosses* card boundary.
    return blk_end > cur_card_boundary;
  }

  // Returns the address of the start of the block reaching into the card containing
  // "addr".
  inline HeapWord* block_start_reaching_into_card(HeapWord* const addr) const;

  // [blk_start, blk_end) representing a block of memory in the heap.
  void update_for_block(HeapWord* blk_start, HeapWord* blk_end) {
    if (is_crossing_card_boundary(blk_start, blk_end)) {
      update_for_block_work(blk_start, blk_end);
    }
  }

  inline HeapWord* object_start(HeapWord* const addr) const;
};

#endif // SHARE_GC_PARALLEL_OBJECTSTARTARRAY_HPP
