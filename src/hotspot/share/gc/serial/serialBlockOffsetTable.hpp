/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SERIAL_SERIALBLOCKOFFSETTABLE_HPP
#define SHARE_GC_SERIAL_SERIALBLOCKOFFSETTABLE_HPP

#include "gc/shared/blockOffsetTable.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/memset_with_concurrent_readers.hpp"
#include "memory/allStatic.hpp"
#include "memory/memRegion.hpp"
#include "memory/virtualspace.hpp"
#include "runtime/globals.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// SerialBlockOffsetTable divides the covered region into "N"-word subregions (where
// "N" = 2^"LogN".  An array with an entry for each such subregion indicates
// how far back one must go to find the start of the chunk that includes the
// first word of the subregion.
class SerialBlockOffsetTable: public CHeapObj<mtGC> {
  friend class VMStructs;

  // The reserved heap (i.e. old-gen) covered by the shared array.
  MemRegion _reserved;

  // Array for keeping offsets for retrieving object start fast given an
  // address.
  VirtualSpace _vs;

  // Biased array-start of BOT array for fast BOT entry translation
  uint8_t* _offset_base;

  // Return the number of slots needed for an offset array
  // that covers mem_region_words words.
  static size_t compute_size(size_t mem_region_words) {
    assert(mem_region_words % CardTable::card_size_in_words() == 0, "precondition");

    size_t number_of_slots = mem_region_words / CardTable::card_size_in_words();
    return ReservedSpace::allocation_align_size_up(number_of_slots);
  }

  // Mapping from address to object start array entry.
  uint8_t* entry_for_addr(const void* const p) const;

  // Mapping from object start array entry to address of first word.
  HeapWord* addr_for_entry(const uint8_t* const p) const;

  void update_for_block_work(HeapWord* blk_start, HeapWord* blk_end);

  static HeapWord* align_up_by_card_size(HeapWord* const addr) {
    return align_up(addr, CardTable::card_size());
  }

  void verify_for_block(HeapWord* blk_start, HeapWord* blk_end) const;

public:
  // Initialize the table to cover from "base" to (at least)
  // "base + init_word_size".  In the future, the table may be expanded
  // (see "resize" below) up to the size of "_reserved" (which must be at
  // least "init_word_size".)  The contents of the initial table are
  // undefined; it is the responsibility of the constituent
  // SerialBlockOffsetTable(s) to initialize cards.
  SerialBlockOffsetTable(MemRegion reserved, size_t init_word_size);

  static bool is_crossing_card_boundary(HeapWord* const obj_start,
                                        HeapWord* const obj_end) {
    HeapWord* cur_card_boundary = align_up_by_card_size(obj_start);
    // Strictly greater-than, since we check if this block *crosses* card boundary.
    return obj_end > cur_card_boundary;
  }

  // Returns the address of the start of the block reaching into the card containing
  // "addr".
  HeapWord* block_start_reaching_into_card(const void* addr) const;

  // [blk_start, blk_end) representing a block of memory in the heap.
  void update_for_block(HeapWord* blk_start, HeapWord* blk_end) {
    if (is_crossing_card_boundary(blk_start, blk_end)) {
      update_for_block_work(blk_start, blk_end);
    }
  }

  // Notes a change in the committed size of the region covered by the
  // table.  The "new_word_size" may not be larger than the size of the
  // reserved region this table covers.
  void resize(size_t new_word_size);
};

#endif // SHARE_GC_SERIAL_SERIALBLOCKOFFSETTABLE_HPP
