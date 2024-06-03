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

#ifndef SHARE_GC_SERIAL_SERIALBLOCKOFFSETTABLE_INLINE_HPP
#define SHARE_GC_SERIAL_SERIALBLOCKOFFSETTABLE_INLINE_HPP

#include "gc/serial/serialBlockOffsetTable.hpp"

inline uint8_t* SerialBlockOffsetTable::entry_for_addr(const void* const p) const {
  assert(_reserved.contains(p),
         "out of bounds access to block offset array");
  uint8_t* result = &_offset_base[uintptr_t(p) >> CardTable::card_shift()];
  return result;
}

inline HeapWord* SerialBlockOffsetTable::addr_for_entry(const uint8_t* const p) const {
  // _offset_base can be "negative", so can't use pointer_delta().
  size_t delta = p - _offset_base;
  HeapWord* result = (HeapWord*) (delta << CardTable::card_shift());
  assert(_reserved.contains(result),
         "out of bounds accessor from block offset array");
  return result;
}

#endif // SHARE_GC_SERIAL_SERIALBLOCKOFFSETTABLE_INLINE_HPP
