/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1BLOCKOFFSETTABLE_HPP
#define SHARE_GC_G1_G1BLOCKOFFSETTABLE_HPP

#include "gc/g1/g1RegionToSpaceMapper.hpp"
#include "gc/shared/blockOffsetTable.hpp"
#include "gc/shared/cardTable.hpp"
#include "memory/memRegion.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"

// This implementation of "G1BlockOffsetTable" divides the covered region
// into "N"-word subregions (where "N" = 2^"LogN".  An array with an entry
// for each such subregion indicates how far back one must go to find the
// start of the chunk that includes the first word of the subregion.
class G1BlockOffsetTable : public CHeapObj<mtGC> {
  // The reserved region covered by the table.
  MemRegion _reserved;

  // Biased array-start of BOT array for fast BOT entry translation
  Atomic<uint8_t>* _offset_base;

  // Bounds checking accessors:
  // For performance these have to devolve to array accesses in product builds.
  inline uint8_t offset_array(Atomic<uint8_t>* addr) const;

  inline void set_offset_array(Atomic<uint8_t>* addr, uint8_t offset);

  inline void set_offset_array(Atomic<uint8_t>* addr, HeapWord* high, HeapWord* low);

  inline void set_offset_array(Atomic<uint8_t>* left, Atomic<uint8_t>* right, uint8_t offset);

  // Mapping from address to object start array entry
  inline Atomic<uint8_t>* entry_for_addr(const void* const p) const;

  // Mapping from object start array entry to address of first word
  inline HeapWord* addr_for_entry(const Atomic<uint8_t>* const p) const;

  void check_address(Atomic<uint8_t>* addr, const char* msg) const NOT_DEBUG_RETURN;

  // Sets the entries corresponding to the cards starting at "start" and ending
  // at "end" to point back to the card before "start"; [start, end]
  void set_remainder_to_point_to_start_incl(Atomic<uint8_t>* start, Atomic<uint8_t>* end);

  // Update BOT entries corresponding to the mem range [blk_start, blk_end).
  void update_for_block_work(HeapWord* blk_start, HeapWord* blk_end);

  void check_all_cards(Atomic<uint8_t>* left_card, Atomic<uint8_t>* right_card) const NOT_DEBUG_RETURN;

  void verify_offset(Atomic<uint8_t>* card_index, uint8_t upper) const NOT_DEBUG_RETURN;
  void verify_for_block(HeapWord* blk_start, HeapWord* blk_end) const NOT_DEBUG_RETURN;

  static HeapWord* align_up_by_card_size(HeapWord* const addr) {
    return align_up(addr, CardTable::card_size());
  }

public:
  // Return the number of slots needed for an offset array
  // that covers mem_region_words words.
  static size_t compute_size(size_t mem_region_words);

  // Returns how many bytes of the heap a single byte of the BOT corresponds to.
  static size_t heap_map_factor() {
    return CardTable::card_size();
  }

  // Initialize the Block Offset Table to cover the memory region passed
  // in the heap parameter.
  G1BlockOffsetTable(MemRegion heap, G1RegionToSpaceMapper* storage);

  inline static bool is_crossing_card_boundary(HeapWord* const obj_start,
                                               HeapWord* const obj_end);

  // Returns the address of the start of the block reaching into the card containing
  // "addr".
  inline HeapWord* block_start_reaching_into_card(const void* addr) const;

  inline void update_for_block(HeapWord* blk_start, HeapWord* blk_end);
};

#endif // SHARE_GC_G1_G1BLOCKOFFSETTABLE_HPP
