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

#ifndef SHARE_LIBADT_VECTSET_HPP
#define SHARE_LIBADT_VECTSET_HPP

#include "memory/allocation.hpp"
#include "utilities/copy.hpp"

// Vector Sets

// These sets can grow or shrink, based on the initial size and the largest
// element currently in them.

//------------------------------VectorSet--------------------------------------
class VectorSet : public ResourceObj {
private:

  static const uint word_bits = 5;
  static const uint bit_mask  = 31;

  uint       _size;             // Size of data in 32-bit words
  uint32_t*  _data;             // The data, bit packed
  Arena*     _set_arena;

  void grow(uint newsize);      // Grow vector to required bitsize
  void reset_memory();
public:
  VectorSet(Arena *arena);
  ~VectorSet() {}

  void insert(uint elem);
  bool is_empty() const;
  void reset() {
    Copy::zero_to_bytes(_data, _size * sizeof(uint32_t));
  }
  void clear() {
    // Reclaim storage if huge
    if (_size > 100) {
      reset_memory();
    } else {
      reset();
    }
  }

  // Fast inlined "test and set".  Replaces the idiom:
  //     if (visited.test(idx)) return;
  //     visited.set(idx);
  // With:
  //     if (visited.test_set(idx)) return;
  //
  bool test_set(uint elem) {
    uint32_t word = elem >> word_bits;
    if (word >= _size) {
      // Then grow; set; return 0;
      this->insert(elem);
      return false;
    }
    uint32_t mask = 1U << (elem & bit_mask);
    uint32_t data = _data[word];
    _data[word] = data | mask;
    return (data & mask) != 0;
  }

  // Fast inlined test
  bool test(uint elem) const {
    uint32_t word = elem >> word_bits;
    if (word >= _size) {
      return false;
    }
    uint32_t mask = 1U << (elem & bit_mask);
    return (_data[word] & mask) != 0;
  }

  void remove(uint elem) {
    uint32_t word = elem >> word_bits;
    if (word >= _size) {
      return;
    }
    uint32_t mask = 1U << (elem & bit_mask);
    _data[word] &= ~mask; // Clear bit
  }

  // Fast inlined set
  void set(uint elem) {
    uint32_t word = elem >> word_bits;
    if (word >= _size) {
      this->insert(elem);
    } else {
      uint32_t mask = 1U << (elem & bit_mask);
      _data[word] |= mask;
    }
  }
};

#endif // SHARE_LIBADT_VECTSET_HPP
