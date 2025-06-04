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

#ifndef SHARE_GC_G1_G1BLOCKOFFSETTABLE_INLINE_HPP
#define SHARE_GC_G1_G1BLOCKOFFSETTABLE_INLINE_HPP

#include "gc/g1/g1BlockOffsetTable.hpp"

#include "gc/g1/g1HeapRegion.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/memset_with_concurrent_readers.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"

inline HeapWord* G1BlockOffsetTable::block_start_reaching_into_card(const void* addr) const {
  assert(_reserved.contains(addr), "invalid address");

  uint8_t* entry = entry_for_addr(addr);
  uint8_t offset = offset_array(entry);
  while (offset >= CardTable::card_size_in_words()) {
    // The excess of the offset from N_words indicates a power of Base
    // to go back by.
    size_t n_cards_back = BOTConstants::entry_to_cards_back(offset);
    entry -= n_cards_back;
    offset = offset_array(entry);
  }
  assert(offset < CardTable::card_size_in_words(), "offset too large");
  HeapWord* q = addr_for_entry(entry);
  return q - offset;
}

uint8_t G1BlockOffsetTable::offset_array(uint8_t* addr) const {
  check_address(addr, "Block offset table address out of range");
  return Atomic::load(addr);
}

inline uint8_t* G1BlockOffsetTable::entry_for_addr(const void* const p) const {
  assert(_reserved.contains(p),
         "out of bounds access to block offset table");
  uint8_t* result = const_cast<uint8_t*>(&_offset_base[uintptr_t(p) >> CardTable::card_shift()]);
  return result;
}

inline HeapWord* G1BlockOffsetTable::addr_for_entry(const uint8_t* const p) const {
  // _offset_base can be "negative", so can't use pointer_delta().
  size_t delta = p - _offset_base;
  HeapWord* result = (HeapWord*) (delta << CardTable::card_shift());
  assert(_reserved.contains(result),
         "out of bounds accessor from block offset table");
  return result;
}

#endif // SHARE_GC_G1_G1BLOCKOFFSETTABLE_INLINE_HPP
