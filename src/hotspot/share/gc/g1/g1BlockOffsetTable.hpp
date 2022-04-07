/*
 * Copyright (c) 2001, 2021, Oracle and/or its affiliates. All rights reserved.
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

  // Array for keeping offsets for retrieving object start fast given an
  // address.
  volatile u_char* _offset_array;  // byte array keeping backwards offsets

  void check_offset(size_t offset, const char* msg) const {
    assert(offset < BOTConstants::card_size_in_words(),
           "%s - offset: " SIZE_FORMAT ", N_words: %u",
           msg, offset, BOTConstants::card_size_in_words());
  }

  // Bounds checking accessors:
  // For performance these have to devolve to array accesses in product builds.
  inline u_char offset_array(size_t index) const;

  inline void set_offset_array_raw(size_t index, u_char offset);
  inline void set_offset_array(size_t index, u_char offset);

  inline void set_offset_array(size_t index, HeapWord* high, HeapWord* low);

  inline void set_offset_array(size_t left, size_t right, u_char offset);

  bool is_card_boundary(HeapWord* p) const;

  void check_index(size_t index, const char* msg) const NOT_DEBUG_RETURN;

public:

  // Return the number of slots needed for an offset array
  // that covers mem_region_words words.
  static size_t compute_size(size_t mem_region_words) {
    size_t number_of_slots = (mem_region_words / BOTConstants::card_size_in_words());
    return ReservedSpace::allocation_align_size_up(number_of_slots);
  }

  // Returns how many bytes of the heap a single byte of the BOT corresponds to.
  static size_t heap_map_factor() {
    return BOTConstants::card_size();
  }

  // Initialize the Block Offset Table to cover the memory region passed
  // in the heap parameter.
  G1BlockOffsetTable(MemRegion heap, G1RegionToSpaceMapper* storage);

  // Return the appropriate index into "_offset_array" for "p".
  inline size_t index_for(const void* p) const;
  inline size_t index_for_raw(const void* p) const;

  // Return the address indicating the start of the region corresponding to
  // "index" in "_offset_array".
  inline HeapWord* address_for_index(size_t index) const;
  // Variant of address_for_index that does not check the index for validity.
  inline HeapWord* address_for_index_raw(size_t index) const {
    return _reserved.start() + (index << BOTConstants::log_card_size_in_words());
  }
};

class G1BlockOffsetTablePart {
  friend class G1BlockOffsetTable;
  friend class HeapRegion;
  friend class VMStructs;
private:
  // This is the global BlockOffsetTable.
  G1BlockOffsetTable* _bot;

  // The region that owns this subregion.
  HeapRegion* _hr;

  // Sets the entries
  // corresponding to the cards starting at "start" and ending at "end"
  // to point back to the card before "start": the interval [start, end)
  // is right-open.
  void set_remainder_to_point_to_start(HeapWord* start, HeapWord* end);
  // Same as above, except that the args here are a card _index_ interval
  // that is closed: [start_index, end_index]
  void set_remainder_to_point_to_start_incl(size_t start, size_t end);

  inline size_t block_size(const HeapWord* p) const;

  // Returns the address of a block whose start is at most "addr".
  inline HeapWord* block_at_or_preceding(const void* addr) const;

  // Return the address of the beginning of the block that contains "addr".
  // "q" is a block boundary that is <= "addr"; "n" is the address of the
  // next block (or the end of the space.)
  inline HeapWord* forward_to_block_containing_addr(HeapWord* q, HeapWord* n,
                                                    const void* addr) const;

  // Update BOT entries corresponding to the mem range [blk_start, blk_end).
  void update_for_block_work(HeapWord* blk_start, HeapWord* blk_end);

  void check_all_cards(size_t left_card, size_t right_card) const;

public:
  static HeapWord* align_up_by_card_size(HeapWord* const addr) {
    return align_up(addr, BOTConstants::card_size());
  }

  static bool is_crossing_card_boundary(HeapWord* const obj_start,
                                        HeapWord* const obj_end) {
    HeapWord* cur_card_boundary = align_up_by_card_size(obj_start);
    // strictly greater-than
    return obj_end > cur_card_boundary;
  }

  //  The elements of the array are initialized to zero.
  G1BlockOffsetTablePart(G1BlockOffsetTable* array, HeapRegion* hr);

  void update();

  void verify() const;

  // Returns the address of the start of the block containing "addr", or
  // else "null" if it is covered by no block.  (May have side effects,
  // namely updating of shared array entries that "point" too far
  // backwards.  This can occur, for example, when lab allocation is used
  // in a space covered by the table.)
  inline HeapWord* block_start(const void* addr);

  void update_for_block(HeapWord* blk_start, HeapWord* blk_end) {
    if (is_crossing_card_boundary(blk_start, blk_end)) {
      update_for_block_work(blk_start, blk_end);
    }
  }

  void update_for_block(HeapWord* blk_start, size_t size) {
    update_for_block(blk_start, blk_start + size);
  }

  void set_for_starts_humongous(HeapWord* obj_top, size_t fill_size);

  void print_on(outputStream* out) PRODUCT_RETURN;
};

#endif // SHARE_GC_G1_G1BLOCKOFFSETTABLE_HPP
