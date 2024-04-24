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

#include "precompiled.hpp"
#include "gc/g1/g1BlockOffsetTable.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "logging/log.hpp"
#include "nmt/memTracker.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"

G1BlockOffsetTable::G1BlockOffsetTable(MemRegion heap, G1RegionToSpaceMapper* storage) :
  _reserved(heap), _offset_base(nullptr) {

  MemRegion bot_reserved = storage->reserved();

  _offset_base = ((uint8_t*)bot_reserved.start() - (uintptr_t(_reserved.start()) >> CardTable::card_shift()));

  log_trace(gc, bot)("G1BlockOffsetTable::G1BlockOffsetTable: ");
  log_trace(gc, bot)("    rs.base(): " PTR_FORMAT "  rs.size(): " SIZE_FORMAT "  rs end(): " PTR_FORMAT,
                     p2i(bot_reserved.start()), bot_reserved.byte_size(), p2i(bot_reserved.end()));
}

void G1BlockOffsetTable::set_offset_array(uint8_t* addr, uint8_t offset) {
  check_address(addr, "Block offset table address out of range");
  Atomic::store(addr, offset);
}

void G1BlockOffsetTable::set_offset_array(uint8_t* addr, HeapWord* high, HeapWord* low) {
  assert(high >= low, "addresses out of order");
  size_t offset = pointer_delta(high, low);
  check_offset(offset, "offset too large");
  set_offset_array(addr, (uint8_t)offset);
}

void G1BlockOffsetTable::set_offset_array(uint8_t* left, uint8_t* right, uint8_t offset) {
  check_address(right, "Right block offset table address out of range");
  assert(left <= right, "indexes out of order");
  size_t num_cards = right - left + 1;
  memset_with_concurrent_readers(left, offset, num_cards);
}

#ifdef ASSERT
void G1BlockOffsetTable::check_address(uint8_t* addr, const char* msg) const {
  uint8_t* start_addr = const_cast<uint8_t*>(_offset_base + (uintptr_t(_reserved.start()) >> CardTable::card_shift()));
  uint8_t* end_addr = const_cast<uint8_t*>(_offset_base + (uintptr_t(_reserved.end()) >> CardTable::card_shift()));
  assert(addr >= start_addr && addr <= end_addr,
         "%s - offset address: " PTR_FORMAT ", start address: " PTR_FORMAT ", end address: " PTR_FORMAT,
         msg, (p2i(addr)), (p2i(start_addr)), (p2i(end_addr)));
  assert(G1CollectedHeap::heap()->is_in(addr_for_entry(addr)),
         "Offset address " PTR_FORMAT " corresponding to " PTR_FORMAT
         " (%u) is not in committed area.",
         (p2i(addr)), p2i(addr_for_entry(addr)),
         G1CollectedHeap::heap()->addr_to_region(addr_for_entry(addr)));
}
#endif // ASSERT

// Write the backskip value for each region.
//
//    offset
//    card             2nd                       3rd
//     | +- 1st        |                         |
//     v v             v                         v
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+     +-+-+-+-+-+-+-+-+-+-+-
//    |x|0|0|0|0|0|0|0|1|1|1|1|1|1| ... |1|1|1|1|2|2|2|2|2|2| ...
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+     +-+-+-+-+-+-+-+-+-+-+-
//    11              19                        75
//      12
//
//    offset card is the card that points to the start of an object
//      x - offset value of offset card
//    1st - start of first logarithmic region
//      0 corresponds to logarithmic value N_words + 0 and 2**(3 * 0) = 1
//    2nd - start of second logarithmic region
//      1 corresponds to logarithmic value N_words + 1 and 2**(3 * 1) = 8
//    3rd - start of third logarithmic region
//      2 corresponds to logarithmic value N_words + 2 and 2**(3 * 2) = 64
//
//    integer below the block offset entry is an example of
//    the index of the entry
//
//    Given an address,
//      Find the block offset table entry
//      Convert the entry to a back slide
//        (e.g., with today's, offset = 0x81 =>
//          back slip = 2**(3*(0x81 - N_words)) = 2**3) = 8
//      Move back N (e.g., 8) entries and repeat with the
//        value of the new entry
//
void G1BlockOffsetTable::set_remainder_to_point_to_start_incl(uint8_t* start_card, uint8_t* end_card) {
  assert(start_card <= end_card, "precondition");
  assert(offset_array(start_card-1) < CardTable::card_size_in_words(),
         "Offset card has an unexpected value");
  uint8_t* start_card_for_region = start_card;
  uint8_t offset = UINT8_MAX;
  for (uint i = 0; i < BOTConstants::N_powers; i++) {
    // -1 so that the card with the actual offset is counted.  Another -1
    // so that the reach ends in this region and not at the start
    // of the next.
    uint8_t* reach = start_card - 1 + (BOTConstants::power_to_cards_back(i+1) - 1);
    offset = CardTable::card_size_in_words() + i;
    if (reach >= end_card) {
      set_offset_array(start_card_for_region, end_card, offset);
      start_card_for_region = reach + 1;
      break;
    }
    set_offset_array(start_card_for_region, reach, offset);
    start_card_for_region = reach + 1;
  }
  assert(start_card_for_region > end_card, "Sanity check");
  check_all_cards(start_card, end_card);
}

#ifdef ASSERT
// The card-interval [start_card, end_card] is a closed interval; this
// is an expensive check -- use with care and only under protection of
// suitable flag.
void G1BlockOffsetTable::check_all_cards(uint8_t* start_card, uint8_t* end_card) const {
  if (end_card < start_card) {
    return;
  }
  guarantee(offset_array(start_card) == CardTable::card_size_in_words(), "Wrong value in second card");
  for (uint8_t* c = start_card + 1; c <= end_card; c++ /* yeah! */) {
    uint8_t entry = offset_array(c);
    if ((unsigned)(c - start_card) > BOTConstants::power_to_cards_back(1)) {
      guarantee(entry > CardTable::card_size_in_words(),
                "Should be in logarithmic region - "
                "entry: %u, "
                "_array->offset_array(c): %u, "
                "N_words: %u",
                (uint)entry, (uint)offset_array(c), CardTable::card_size_in_words());
    }
    size_t backskip = BOTConstants::entry_to_cards_back(entry);
    uint8_t* landing_card = c - backskip;
    guarantee(landing_card >= (start_card - 1), "Inv");
    if (landing_card >= start_card) {
      guarantee(offset_array(landing_card) <= entry,
                "Monotonicity - landing_card offset: %u, "
                "entry: %u",
                (uint)offset_array(landing_card), (uint)entry);
    } else {
      guarantee(landing_card == start_card - 1, "Tautology");
      // Note that N_words is the maximum offset value
      guarantee(offset_array(landing_card) < CardTable::card_size_in_words(),
                "landing card offset: %u, "
                "N_words: %u",
                (uint)offset_array(landing_card), (uint)CardTable::card_size_in_words());
    }
  }
}
#endif

//
//              cur_card_boundary
//              |   _index_
//              v   v
//      +-------+-------+-------+-------+-------+
//      | i-1   |   i   | i+1   | i+2   | i+3   |
//      +-------+-------+-------+-------+-------+
//       ( ^    ]
//         blk_start
//
void G1BlockOffsetTable::update_for_block_work(HeapWord* blk_start, HeapWord* blk_end) {
  HeapWord* const cur_card_boundary = align_up_by_card_size(blk_start);
  uint8_t* const offset_card = entry_for_addr(cur_card_boundary);

  assert(blk_start != nullptr && blk_end > blk_start,
         "phantom block");
  assert(blk_end > cur_card_boundary, "should be past cur_card_boundary");
  assert(blk_start <= cur_card_boundary, "blk_start should be at or before cur_card_boundary");
  assert(pointer_delta(cur_card_boundary, blk_start) < CardTable::card_size_in_words(),
         "offset should be < CardTable::card_size_in_words()");
  assert(G1CollectedHeap::heap()->is_in_reserved(blk_start),
         "reference must be into the heap");
  assert(G1CollectedHeap::heap()->is_in_reserved(blk_end - 1),
         "limit must be within the heap");
  assert(cur_card_boundary == addr_for_entry(offset_card),
         "Block offset table entry must agree with cur_card_boundary");

  // Mark the card that holds the offset into the block.
  set_offset_array(offset_card, cur_card_boundary, blk_start);

  // We need to now mark the subsequent cards that this block spans.

  // Index of card on which the block ends.
  uint8_t* end_card = entry_for_addr(blk_end - 1);

  // Are there more cards left to be updated?
  if (offset_card + 1 <= end_card) {
    set_remainder_to_point_to_start_incl(offset_card + 1, end_card);
  }

#ifdef ASSERT
  // Calculate new_card_boundary this way because end_index
  // may be the last valid index in the covered region.
  HeapWord* new_card_boundary = addr_for_entry(end_card) + CardTable::card_size_in_words();
  assert(new_card_boundary >= blk_end, "postcondition");

  // The offset can be 0 if the block starts on a boundary.  That
  // is checked by an assertion above.
  uint8_t* previous_card = entry_for_addr(blk_start);
  HeapWord* boundary = addr_for_entry(previous_card);
  assert((offset_array(offset_card) == 0 && blk_start == boundary) ||
         (offset_array(offset_card) > 0 && offset_array(offset_card) < CardTable::card_size_in_words()),
         "offset array should have been set - "
         "index offset: %u, "
         "blk_start: " PTR_FORMAT ", "
         "boundary: " PTR_FORMAT,
         (uint)offset_array(offset_card),
         p2i(blk_start), p2i(boundary));

  G1BlockOffsetTable::verify_for_block(blk_start, blk_end);
#endif // ASSERT
}

#ifdef ASSERT
void G1BlockOffsetTable::verify_offset(uint8_t* card_index, uint8_t upper_boundary) const {
  assert(offset_array(card_index) <= upper_boundary,
         "Offset %u should not be larger than upper boundary %u.",
         (uint) offset_array(card_index),
         (uint) upper_boundary);
}

void G1BlockOffsetTable::verify_for_block(HeapWord* blk_start, HeapWord* blk_end) const {
  assert(is_crossing_card_boundary(blk_start, blk_end), "precondition");

  uint8_t* start_card = entry_for_addr(align_up_by_card_size(blk_start));
  uint8_t* end_card = entry_for_addr(blk_end - 1);
  // Check cards in [start_card, end_card]
  verify_offset(start_card, CardTable::card_size_in_words());

  for (uint8_t* current_card = start_card + 1; current_card <= end_card; ++current_card) {
    assert(offset_array(current_card) > 0,
           "Offset %u is not larger than 0.",
           (uint) offset_array(current_card));
    verify_offset(current_card, (uint8_t) (CardTable::card_size_in_words() + BOTConstants::N_powers - 1));

    uint8_t* prev  = current_card - 1;
    uint8_t* value = current_card;
    if (offset_array(prev) != offset_array(value)) {
      assert(offset_array(value) >= offset_array(prev), "monotonic");
      size_t n_cards_back = BOTConstants::entry_to_cards_back(offset_array(value));
      assert(start_card == (current_card - n_cards_back), "inv");
    }
  }
}
#endif // ASSERT
