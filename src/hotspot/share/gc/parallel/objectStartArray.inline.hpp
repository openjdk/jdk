/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_PARALLEL_OBJECTSTARTARRAY_INLINE_HPP
#define SHARE_GC_PARALLEL_OBJECTSTARTARRAY_INLINE_HPP

#include "gc/parallel/objectStartArray.hpp"

HeapWord* ObjectStartArray::object_start(HeapWord* const addr) const {
  HeapWord* cur_block = block_start_reaching_into_card(addr);

  while (true) {
    HeapWord* next_block = cur_block + cast_to_oop(cur_block)->size();
    if (next_block > addr) {
      assert(cur_block <= addr, "postcondition");
      return cur_block;
    }
    // Because the BOT is precise, we should never step into the next card
    // (i.e. crossing the card boundary).
    assert(!is_crossing_card_boundary(next_block, addr), "must be");
    cur_block = next_block;
  }
}

HeapWord* ObjectStartArray::block_start_reaching_into_card(HeapWord* const addr) const {
  const uint8_t* entry = entry_for_addr(addr);

  uint8_t offset;
  while (true) {
    offset = *entry;

    if (offset < BOTConstants::card_size_in_words()) {
      break;
    }

    // The excess of the offset from N_words indicates a power of Base
    // to go back by.
    size_t n_cards_back = BOTConstants::entry_to_cards_back(offset);
    entry -= n_cards_back;
  }

  HeapWord* q = addr_for_entry(entry);
  return q - offset;
}

#endif // SHARE_GC_PARALLEL_OBJECTSTARTARRAY_INLINE_HPP
