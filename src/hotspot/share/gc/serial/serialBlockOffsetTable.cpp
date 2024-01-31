/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/serial/serialBlockOffsetTable.inline.hpp"
#include "gc/shared/blockOffsetTable.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "memory/universe.hpp"
#include "nmt/memTracker.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"

SerialBlockOffsetSharedArray::SerialBlockOffsetSharedArray(MemRegion reserved,
                                                           size_t init_word_size):
  _reserved(reserved) {
  size_t size = compute_size(reserved.word_size());
  ReservedSpace rs(size);
  if (!rs.is_reserved()) {
    vm_exit_during_initialization("Could not reserve enough space for heap offset array");
  }

  MemTracker::record_virtual_memory_type((address)rs.base(), mtGC);

  if (!_vs.initialize(rs, 0)) {
    vm_exit_during_initialization("Could not reserve enough space for heap offset array");
  }
  _offset_array = (uint8_t*)_vs.low_boundary();
  resize(init_word_size);
  log_trace(gc, bot)("SerialBlockOffsetSharedArray::SerialBlockOffsetSharedArray: ");
  log_trace(gc, bot)("   rs.base(): " PTR_FORMAT " rs.size(): " SIZE_FORMAT_X_0 " rs end(): " PTR_FORMAT,
                     p2i(rs.base()), rs.size(), p2i(rs.base() + rs.size()));
  log_trace(gc, bot)("   _vs.low_boundary(): " PTR_FORMAT "  _vs.high_boundary(): " PTR_FORMAT,
                     p2i(_vs.low_boundary()), p2i(_vs.high_boundary()));
}

void SerialBlockOffsetSharedArray::resize(size_t new_word_size) {
  assert(new_word_size <= _reserved.word_size(), "Resize larger than reserved");
  size_t new_size = compute_size(new_word_size);
  size_t old_size = _vs.committed_size();
  size_t delta;
  char* high = _vs.high();
  if (new_size > old_size) {
    delta = ReservedSpace::page_align_size_up(new_size - old_size);
    assert(delta > 0, "just checking");
    if (!_vs.expand_by(delta)) {
      vm_exit_out_of_memory(delta, OOM_MMAP_ERROR, "offset table expansion");
    }
    assert(_vs.high() == high + delta, "invalid expansion");
  } else {
    delta = ReservedSpace::page_align_size_down(old_size - new_size);
    if (delta == 0) return;
    _vs.shrink_by(delta);
    assert(_vs.high() == high - delta, "invalid expansion");
  }
}

// Write the backskip value for each logarithmic region (array slots containing the same entry value).
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
void SerialBlockOffsetTable::update_for_block_work(HeapWord* blk_start,
                                                   HeapWord* blk_end) {
  HeapWord* const cur_card_boundary = align_up_by_card_size(blk_start);
  size_t const offset_card = _array->index_for(cur_card_boundary);

  // The first card holds the actual offset.
  _array->set_offset_array(offset_card, cur_card_boundary, blk_start);

  // Check if this block spans over other cards.
  size_t end_card = _array->index_for(blk_end - 1);
  assert(offset_card <= end_card, "inv");

  if (offset_card != end_card) {
    // Handling remaining cards.
    size_t start_card_for_region = offset_card + 1;
    for (uint i = 0; i < BOTConstants::N_powers; i++) {
      // -1 so that the reach ends in this region and not at the start
      // of the next.
      size_t reach = offset_card + BOTConstants::power_to_cards_back(i + 1) - 1;
      uint8_t value = checked_cast<uint8_t>(BOTConstants::card_size_in_words() + i);

      _array->set_offset_array(start_card_for_region, MIN2(reach, end_card), value);
      start_card_for_region = reach + 1;

      if (reach >= end_card) {
        break;
      }
    }
    assert(start_card_for_region > end_card, "Sanity check");
  }

  debug_only(verify_for_block(blk_start, blk_end);)
}

HeapWord* SerialBlockOffsetTable::block_start_reaching_into_card(const void* addr) const {
  size_t index = _array->index_for(addr);

  uint8_t offset;
  while (true) {
    offset = _array->offset_array(index);

    if (offset < BOTConstants::card_size_in_words()) {
      break;
    }

    // The excess of the offset from N_words indicates a power of Base
    // to go back by.
    size_t n_cards_back = BOTConstants::entry_to_cards_back(offset);
    index -= n_cards_back;
  }

  HeapWord* q = _array->address_for_index(index);
  return q - offset;
}

void SerialBlockOffsetTable::verify_for_block(HeapWord* blk_start, HeapWord* blk_end) const {
  assert(is_crossing_card_boundary(blk_start, blk_end), "precondition");

  const size_t start_card = _array->index_for(align_up_by_card_size(blk_start));
  const size_t end_card = _array->index_for(blk_end - 1);
  // Check cards in [start_card, end_card]
  assert(_array->offset_array(start_card) < BOTConstants::card_size_in_words(), "offset card");

  for (size_t i = start_card + 1; i <= end_card; ++i) {
    const uint8_t prev  = _array->offset_array(i-1);
    const uint8_t value = _array->offset_array(i);
    if (prev != value) {
      assert(value >= prev, "monotonic");
      size_t n_cards_back = BOTConstants::entry_to_cards_back(value);
      assert(start_card == (i - n_cards_back), "inv");
    }
  }
}
