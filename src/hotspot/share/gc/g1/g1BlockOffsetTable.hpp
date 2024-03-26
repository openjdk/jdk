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

#ifndef SHARE_GC_G1_G1BLOCKOFFSETTABLE_HPP
#define SHARE_GC_G1_G1BLOCKOFFSETTABLE_HPP

#include "gc/g1/g1RegionToSpaceMapper.hpp"
#include "gc/shared/blockOffsetTable.hpp"
#include "gc/shared/cardTable.hpp"
#include "memory/memRegion.hpp"
#include "memory/virtualspace.hpp"
#include "utilities/globalDefinitions.hpp"

// Forward declarations
class G1BlockOffsetTable;
class HeapRegion;

// This implementation of "G1BlockOffsetTable" divides the covered region
// into "N"-word subregions (where "N" = 2^"LogN".  An array with an entry
// for each such subregion indicates how far back one must go to find the
// start of the chunk that includes the first word of the subregion.
//
// Each G1BlockOffsetTablePart is owned by a HeapRegion.

class G1BlockOffsetTable: public CHeapObj<mtGC> {
  friend class G1BlockOffsetTablePart;
  friend class VMStructs;

private:
  // The reserved region covered by the table.
  MemRegion _reserved;

  // Biased array-start of BOT array for fast BOT entry translation
  volatile u_char* _offset_base;

  void check_offset(size_t offset, const char* msg) const {
    assert(offset < CardTable::card_size_in_words(),
           "%s - offset: " SIZE_FORMAT ", N_words: %u",
           msg, offset, CardTable::card_size_in_words());
  }

  // Bounds checking accessors:
  // For performance these have to devolve to array accesses in product builds.
  inline u_char offset_array(u_char* addr) const;

  inline void set_offset_array_raw(u_char* addr, u_char offset);
  inline void set_offset_array(u_char* addr, u_char offset);

  inline void set_offset_array(u_char* addr, HeapWord* high, HeapWord* low);

  inline void set_offset_array(u_char* left, u_char* right, u_char offset);

  void check_address(u_char* addr, const char* msg) const NOT_DEBUG_RETURN;

public:

  // Return the number of slots needed for an offset array
  // that covers mem_region_words words.
  static size_t compute_size(size_t mem_region_words) {
    size_t number_of_slots = (mem_region_words / CardTable::card_size_in_words());
    return ReservedSpace::allocation_align_size_up(number_of_slots);
  }

  // Returns how many bytes of the heap a single byte of the BOT corresponds to.
  static size_t heap_map_factor() {
    return CardTable::card_size();
  }

  // Initialize the Block Offset Table to cover the memory region passed
  // in the heap parameter.
  G1BlockOffsetTable(MemRegion heap, G1RegionToSpaceMapper* storage);

  // Mapping from address to object start array entry
  u_char* entry_for_addr(const void* const p) const;

  // Mapping from object start array entry to address of first word
  HeapWord* addr_for_entry(const u_char* const p) const;
};

class G1BlockOffsetTablePart {
  friend class G1BlockOffsetTable;
  friend class VMStructs;
private:
  // This is the global BlockOffsetTable.
  G1BlockOffsetTable* _bot;

  // The region that owns this part of the BOT.
  HeapRegion* _hr;

  // Sets the entries corresponding to the cards starting at "start" and ending
  // at "end" to point back to the card before "start"; [start, end]
  void set_remainder_to_point_to_start_incl(u_char* start, u_char* end);

  // Update BOT entries corresponding to the mem range [blk_start, blk_end).
  void update_for_block_work(HeapWord* blk_start, HeapWord* blk_end);

  void check_all_cards(u_char* left_card, u_char* right_card) const NOT_DEBUG_RETURN;

  static HeapWord* align_up_by_card_size(HeapWord* const addr) {
    return align_up(addr, CardTable::card_size());
  }

  void update_for_block(HeapWord* blk_start, size_t size) {
    update_for_block(blk_start, blk_start + size);
  }
public:
  static bool is_crossing_card_boundary(HeapWord* const obj_start,
                                        HeapWord* const obj_end) {
    HeapWord* cur_card_boundary = align_up_by_card_size(obj_start);
    // strictly greater-than
    return obj_end > cur_card_boundary;
  }

  //  The elements of the array are initialized to zero.
  G1BlockOffsetTablePart(G1BlockOffsetTable* array, HeapRegion* hr);

  void verify() const;

  // Returns the address of the start of the block reaching into the card containing
  // "addr".
  inline HeapWord* block_start_reaching_into_card(const void* addr) const;

  void update_for_block(HeapWord* blk_start, HeapWord* blk_end) {
    if (is_crossing_card_boundary(blk_start, blk_end)) {
      update_for_block_work(blk_start, blk_end);
    }
  }

  void set_for_starts_humongous(HeapWord* obj_top, size_t fill_size);

  void print_on(outputStream* out) PRODUCT_RETURN;
};

#endif // SHARE_GC_G1_G1BLOCKOFFSETTABLE_HPP
