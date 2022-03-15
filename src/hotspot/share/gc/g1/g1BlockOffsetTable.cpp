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

#include "precompiled.hpp"
#include "gc/g1/g1BlockOffsetTable.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "logging/log.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "services/memTracker.hpp"



//////////////////////////////////////////////////////////////////////
// G1BlockOffsetTable
//////////////////////////////////////////////////////////////////////

G1BlockOffsetTable::G1BlockOffsetTable(MemRegion heap, G1RegionToSpaceMapper* storage) :
  _reserved(heap), _offset_array(NULL) {

  MemRegion bot_reserved = storage->reserved();

  _offset_array = (u_char*)bot_reserved.start();

  log_trace(gc, bot)("G1BlockOffsetTable::G1BlockOffsetTable: ");
  log_trace(gc, bot)("    rs.base(): " PTR_FORMAT "  rs.size(): " SIZE_FORMAT "  rs end(): " PTR_FORMAT,
                     p2i(bot_reserved.start()), bot_reserved.byte_size(), p2i(bot_reserved.end()));
}

bool G1BlockOffsetTable::is_card_boundary(HeapWord* p) const {
  assert(p >= _reserved.start(), "just checking");
  size_t delta = pointer_delta(p, _reserved.start());
  return (delta & right_n_bits((int)BOTConstants::log_card_size_in_words())) == (size_t)NoBits;
}

#ifdef ASSERT
void G1BlockOffsetTable::check_index(size_t index, const char* msg) const {
  assert((index) < (_reserved.word_size() >> BOTConstants::log_card_size_in_words()),
         "%s - index: " SIZE_FORMAT ", _vs.committed_size: " SIZE_FORMAT,
         msg, (index), (_reserved.word_size() >> BOTConstants::log_card_size_in_words()));
  assert(G1CollectedHeap::heap()->is_in(address_for_index_raw(index)),
         "Index " SIZE_FORMAT " corresponding to " PTR_FORMAT
         " (%u) is not in committed area.",
         (index),
         p2i(address_for_index_raw(index)),
         G1CollectedHeap::heap()->addr_to_region(address_for_index_raw(index)));
}
#endif // ASSERT

//////////////////////////////////////////////////////////////////////
// G1BlockOffsetTablePart
//////////////////////////////////////////////////////////////////////

G1BlockOffsetTablePart::G1BlockOffsetTablePart(G1BlockOffsetTable* array, HeapRegion* hr) :
  _bot(array),
  _hr(hr)
{
}

void G1BlockOffsetTablePart::update() {
  HeapWord* next_addr = _hr->bottom();
  HeapWord* const limit = _hr->top();

  HeapWord* prev_addr;
  while (next_addr < limit) {
    prev_addr = next_addr;
    next_addr  = prev_addr + block_size(prev_addr);
    update_for_block(prev_addr, next_addr);
  }
  assert(next_addr == limit, "Should stop the scan at the limit.");
}

// The arguments follow the normal convention of denoting
// a right-open interval: [start, end)
void G1BlockOffsetTablePart::set_remainder_to_point_to_start(HeapWord* start, HeapWord* end) {
  assert(start < end, "precondition");
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
  //      Find the index for the address
  //      Find the block offset table entry
  //      Convert the entry to a back slide
  //        (e.g., with today's, offset = 0x81 =>
  //          back slip = 2**(3*(0x81 - N_words)) = 2**3) = 8
  //      Move back N (e.g., 8) entries and repeat with the
  //        value of the new entry
  //
  size_t start_card = _bot->index_for(start);
  size_t end_card = _bot->index_for(end-1);
  assert(start ==_bot->address_for_index(start_card), "Precondition");
  assert(end ==_bot->address_for_index(end_card)+BOTConstants::card_size_in_words(), "Precondition");
  set_remainder_to_point_to_start_incl(start_card, end_card); // closed interval
}

// Unlike the normal convention in this code, the argument here denotes
// a closed, inclusive interval: [start_card, end_card], cf set_remainder_to_point_to_start()
// above.
void G1BlockOffsetTablePart::set_remainder_to_point_to_start_incl(size_t start_card, size_t end_card) {
  assert(start_card <= end_card, "precondition");
  assert(start_card > _bot->index_for(_hr->bottom()), "Cannot be first card");
  assert(_bot->offset_array(start_card-1) < BOTConstants::card_size_in_words(),
         "Offset card has an unexpected value");
  size_t start_card_for_region = start_card;
  u_char offset = max_jubyte;
  for (uint i = 0; i < BOTConstants::N_powers; i++) {
    // -1 so that the the card with the actual offset is counted.  Another -1
    // so that the reach ends in this region and not at the start
    // of the next.
    size_t reach = start_card - 1 + (BOTConstants::power_to_cards_back(i+1) - 1);
    offset = BOTConstants::card_size_in_words() + i;
    if (reach >= end_card) {
      _bot->set_offset_array(start_card_for_region, end_card, offset);
      start_card_for_region = reach + 1;
      break;
    }
    _bot->set_offset_array(start_card_for_region, reach, offset);
    start_card_for_region = reach + 1;
  }
  assert(start_card_for_region > end_card, "Sanity check");
  DEBUG_ONLY(check_all_cards(start_card, end_card);)
}

// The card-interval [start_card, end_card] is a closed interval; this
// is an expensive check -- use with care and only under protection of
// suitable flag.
void G1BlockOffsetTablePart::check_all_cards(size_t start_card, size_t end_card) const {

  if (end_card < start_card) {
    return;
  }
  guarantee(_bot->offset_array(start_card) == BOTConstants::card_size_in_words(), "Wrong value in second card");
  for (size_t c = start_card + 1; c <= end_card; c++ /* yeah! */) {
    u_char entry = _bot->offset_array(c);
    if (c - start_card > BOTConstants::power_to_cards_back(1)) {
      guarantee(entry > BOTConstants::card_size_in_words(),
                "Should be in logarithmic region - "
                "entry: %u, "
                "_array->offset_array(c): %u, "
                "N_words: %u",
                (uint)entry, (uint)_bot->offset_array(c), BOTConstants::card_size_in_words());
    }
    size_t backskip = BOTConstants::entry_to_cards_back(entry);
    size_t landing_card = c - backskip;
    guarantee(landing_card >= (start_card - 1), "Inv");
    if (landing_card >= start_card) {
      guarantee(_bot->offset_array(landing_card) <= entry,
                "Monotonicity - landing_card offset: %u, "
                "entry: %u",
                (uint)_bot->offset_array(landing_card), (uint)entry);
    } else {
      guarantee(landing_card == start_card - 1, "Tautology");
      // Note that N_words is the maximum offset value
      guarantee(_bot->offset_array(landing_card) < BOTConstants::card_size_in_words(),
                "landing card offset: %u, "
                "N_words: %u",
                (uint)_bot->offset_array(landing_card), (uint)BOTConstants::card_size_in_words());
    }
  }
}

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
void G1BlockOffsetTablePart::update_for_block_work(HeapWord* blk_start,
                                                   HeapWord* blk_end) {
  HeapWord* const cur_card_boundary = align_up_by_card_size(blk_start);
  size_t const index =  _bot->index_for_raw(cur_card_boundary);

  assert(blk_start != NULL && blk_end > blk_start,
         "phantom block");
  assert(blk_end > cur_card_boundary, "should be past cur_card_boundary");
  assert(blk_start <= cur_card_boundary, "blk_start should be at or before cur_card_boundary");
  assert(pointer_delta(cur_card_boundary, blk_start) < BOTConstants::card_size_in_words(),
         "offset should be < BOTConstants::card_size_in_words()");
  assert(G1CollectedHeap::heap()->is_in_reserved(blk_start),
         "reference must be into the heap");
  assert(G1CollectedHeap::heap()->is_in_reserved(blk_end - 1),
         "limit must be within the heap");
  assert(cur_card_boundary == _bot->_reserved.start() + index*BOTConstants::card_size_in_words(),
         "index must agree with cur_card_boundary");

  // Mark the card that holds the offset into the block.
  _bot->set_offset_array(index, cur_card_boundary, blk_start);

  // We need to now mark the subsequent cards that this block spans.

  // Index of card on which the block ends.
  size_t end_index = _bot->index_for(blk_end - 1);

  // Are there more cards left to be updated?
  if (index + 1 <= end_index) {
    HeapWord* rem_st  = _bot->address_for_index(index + 1);
    // Calculate rem_end this way because end_index
    // may be the last valid index in the covered region.
    HeapWord* rem_end = _bot->address_for_index(end_index) + BOTConstants::card_size_in_words();
    set_remainder_to_point_to_start(rem_st, rem_end);
  }

#ifdef ASSERT
  // Calculate new_card_boundary this way because end_index
  // may be the last valid index in the covered region.
  HeapWord* new_card_boundary = _bot->address_for_index(end_index) + BOTConstants::card_size_in_words();
  assert(new_card_boundary >= blk_end, "postcondition");

  // The offset can be 0 if the block starts on a boundary.  That
  // is checked by an assertion above.
  size_t start_index = _bot->index_for(blk_start);
  HeapWord* boundary = _bot->address_for_index(start_index);
  assert((_bot->offset_array(index) == 0 && blk_start == boundary) ||
         (_bot->offset_array(index) > 0 && _bot->offset_array(index) < BOTConstants::card_size_in_words()),
         "offset array should have been set - "
         "index offset: %u, "
         "blk_start: " PTR_FORMAT ", "
         "boundary: " PTR_FORMAT,
         (uint)_bot->offset_array(index),
         p2i(blk_start), p2i(boundary));
  for (size_t j = index + 1; j <= end_index; j++) {
    assert(_bot->offset_array(j) > 0 &&
           _bot->offset_array(j) <=
             (u_char) (BOTConstants::card_size_in_words()+BOTConstants::N_powers-1),
           "offset array should have been set - "
           "%u not > 0 OR %u not <= %u",
           (uint) _bot->offset_array(j),
           (uint) _bot->offset_array(j),
           (uint) (BOTConstants::card_size_in_words() + BOTConstants::N_powers - 1));
  }
#endif
}

void G1BlockOffsetTablePart::verify() const {
  assert(_hr->bottom() < _hr->top(), "Only non-empty regions should be verified.");
  size_t start_card = _bot->index_for(_hr->bottom());
  size_t end_card = _bot->index_for(_hr->top() - 1);

  for (size_t current_card = start_card; current_card < end_card; current_card++) {
    u_char entry = _bot->offset_array(current_card);
    if (entry < BOTConstants::card_size_in_words()) {
      // The entry should point to an object before the current card. Verify that
      // it is possible to walk from that object in to the current card by just
      // iterating over the objects following it.
      HeapWord* card_address = _bot->address_for_index(current_card);
      HeapWord* obj_end = card_address - entry;
      while (obj_end < card_address) {
        HeapWord* obj = obj_end;
        size_t obj_size = block_size(obj);
        obj_end = obj + obj_size;
        guarantee(obj_end > obj && obj_end <= _hr->top(),
                  "Invalid object end. obj: " PTR_FORMAT " obj_size: " SIZE_FORMAT " obj_end: " PTR_FORMAT " top: " PTR_FORMAT,
                  p2i(obj), obj_size, p2i(obj_end), p2i(_hr->top()));
      }
    } else {
      // Because we refine the BOT based on which cards are dirty there is not much we can verify here.
      // We need to make sure that we are going backwards and that we don't pass the start of the
      // corresponding heap region. But that is about all we can verify.
      size_t backskip = BOTConstants::entry_to_cards_back(entry);
      guarantee(backskip >= 1, "Must be going back at least one card.");

      size_t max_backskip = current_card - start_card;
      guarantee(backskip <= max_backskip,
                "Going backwards beyond the start_card. start_card: " SIZE_FORMAT " current_card: " SIZE_FORMAT " backskip: " SIZE_FORMAT,
                start_card, current_card, backskip);

      HeapWord* backskip_address = _bot->address_for_index(current_card - backskip);
      guarantee(backskip_address >= _hr->bottom(),
                "Going backwards beyond bottom of the region: bottom: " PTR_FORMAT ", backskip_address: " PTR_FORMAT,
                p2i(_hr->bottom()), p2i(backskip_address));
    }
  }
}

#ifndef PRODUCT
void G1BlockOffsetTablePart::print_on(outputStream* out) {
  size_t from_index = _bot->index_for(_hr->bottom());
  size_t to_index = _bot->index_for(_hr->end());
  out->print_cr(">> BOT for area [" PTR_FORMAT "," PTR_FORMAT ") "
                "cards [" SIZE_FORMAT "," SIZE_FORMAT ")",
                p2i(_hr->bottom()), p2i(_hr->end()), from_index, to_index);
  for (size_t i = from_index; i < to_index; ++i) {
    out->print_cr("  entry " SIZE_FORMAT_W(8) " | " PTR_FORMAT " : %3u",
                  i, p2i(_bot->address_for_index(i)),
                  (uint) _bot->offset_array(i));
  }
}
#endif // !PRODUCT

void G1BlockOffsetTablePart::set_for_starts_humongous(HeapWord* obj_top, size_t fill_size) {
  update_for_block(_hr->bottom(), obj_top);
  if (fill_size > 0) {
    update_for_block(obj_top, fill_size);
  }
}
