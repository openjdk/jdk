/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1CARDTABLE_INLINE_HPP
#define SHARE_GC_G1_G1CARDTABLE_INLINE_HPP

#include "gc/g1/g1CardTable.hpp"

#include "gc/g1/g1HeapRegion.hpp"
#include "utilities/population_count.hpp"

inline uint G1CardTable::region_idx_for(CardValue* p) {
  size_t const card_idx = pointer_delta(p, _byte_map, sizeof(CardValue));
  return (uint)(card_idx >> G1HeapRegion::LogCardsPerRegion);
}

inline bool G1CardTable::mark_clean_as_from_remset(CardValue* card) {
  CardValue value = *card;
  if (value == clean_card_val()) {
    *card = g1_from_remset_card;
    return true;
  }
  return false;
}

// Returns bits from a where mask is 0, and bits from b where mask is 1.
//
// Example:
// a      = 0xAAAAAAAA
// b      = 0xBBBBBBBB
// mask   = 0xFF00FF00
// result = 0xBBAABBAA
inline size_t blend(size_t a, size_t b, size_t mask) {
  return (a & ~mask) | (b & mask);
}

inline size_t G1CardTable::mark_clean_range_as_from_remset(size_t start_card_index, size_t num_cards) {
  assert(is_aligned(start_card_index, sizeof(size_t)), "Start card index must be aligned.");
  assert(is_aligned(num_cards, sizeof(size_t)), "Number of cards to change must be evenly divisible.");

  size_t result = 0;

  size_t const num_chunks = num_cards / sizeof(size_t);

  size_t* cur_word = (size_t*)&_byte_map[start_card_index];
  size_t* const end_word_map = cur_word + num_chunks;
  while (cur_word < end_word_map) {
    size_t value = *cur_word;
    if (value == WordAllClean) {
      *cur_word = WordAllFromRemset;
      result += sizeof(size_t);
    } else if ((value & WordAlreadyScanned) == 0) {
      // Do nothing if there is no "Clean" card in it.
    } else {
      // There is a mix of cards in there. Tread "slowly".
      size_t clean_card_mask = (value & WordAlreadyScanned) * 0xff; // All "Clean" cards have 0xff, all other places 0x00 now.
      result += population_count(clean_card_mask) / BitsPerByte;
      *cur_word = blend(value, WordAllFromRemset, clean_card_mask);
    }
    cur_word++;
  }
  return result;
}

inline size_t G1CardTable::change_dirty_cards_to(CardValue* start_card, CardValue* end_card, CardValue which) {
  size_t result = 0;
  for (CardValue* i_card = start_card; i_card < end_card; ++i_card) {
    CardValue value = *i_card;
    assert((value & g1_card_already_scanned) == 0,
           "Must have been dirty %d start " PTR_FORMAT " " PTR_FORMAT, value, p2i(start_card), p2i(end_card));
    if (value == g1_dirty_card) {
      result++;
    }
    *i_card = which;
  }
  return result;
}

#endif /* SHARE_GC_G1_G1CARDTABLE_INLINE_HPP */
