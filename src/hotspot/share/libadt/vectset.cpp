/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "libadt/vectset.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/arena.hpp"
#include "utilities/count_leading_zeros.hpp"

VectorSet::VectorSet(Arena *arena) : _size(2),
    _data(NEW_ARENA_ARRAY(arena, uint32_t, 2)),
    _set_arena(arena) {
  _data[0] = 0;
  _data[1] = 0;
}

// Expand the existing set to a bigger size
void VectorSet::grow(uint new_size) {
  new_size = (new_size + bit_mask) >> word_bits;
  assert(new_size != 0 && new_size < (1U << 31), "");
  uint x = (1U << 31) >> (count_leading_zeros(new_size) - 1);
  _data = REALLOC_ARENA_ARRAY(_set_arena, uint32_t, _data, _size, x);
  Copy::zero_to_bytes(_data + _size, (x - _size) * sizeof(uint32_t));
  _size = x;
}

// Insert a member into an existing Set.
void VectorSet::insert(uint elem) {
  uint32_t word = elem >> word_bits;
  uint32_t mask = 1U << (elem & bit_mask);
  if (word >= _size) {
    grow(elem + 1);
  }
  _data[word] |= mask;
}

// Resets the storage
void VectorSet::reset_memory() {
  assert(_size >= 2, "_size can never be less than 2");
  _data = REALLOC_ARENA_ARRAY(_set_arena, uint32_t, _data, _size, 2);
  _size = 2;
  _data[0] = 0;
  _data[1] = 0;
}

// Return true if the set is empty
bool VectorSet::is_empty() const {
  for (uint32_t i = 0; i < _size; i++) {
    if (_data[i] != 0) {
      return false;
    }
  }
  return true;
}
