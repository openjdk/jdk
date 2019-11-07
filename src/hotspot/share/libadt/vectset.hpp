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

// Vector Sets

// These sets can grow or shrink, based on the initial size and the largest
// element currently in them.

//------------------------------VectorSet--------------------------------------
class VectorSet : public ResourceObj {
private:
  uint size;                    // Size of data IN LONGWORDS (32bits)
  uint32_t* data;               // The data, bit packed
  Arena *_set_arena;

  void grow(uint newsize);      // Grow vector to required bitsize

public:
  VectorSet(Arena *arena);
  ~VectorSet() {}
  void insert(uint elem);

  void clear();
  bool is_empty() const;
  int hash() const;
  void reset() {
    memset(data, 0, size*sizeof(uint32_t));
  }

  // Expose internals for speed-critical fast iterators
  uint word_size() const { return size; }

  // Fast inlined "test and set".  Replaces the idiom:
  //     if (visited.test(idx)) return;
  //     visited.set(idx);
  // With:
  //     if (visited.test_set(idx)) return;
  //
  int test_set(uint elem) {
    uint word = elem >> 5;           // Get the longword offset
    if (word >= size) {
      // Then grow; set; return 0;
      this->insert(elem);
      return 0;
    }
    uint32_t mask = 1L << (elem & 31); // Get bit mask
    uint32_t datum = data[word] & mask;// Get bit
    data[word] |= mask;              // Set bit
    return datum;                    // Return bit
  }

  // Fast inlined test
  int test(uint elem) const {
    uint word = elem >> 5;
    if (word >= size) {
      return 0;
    }
    uint32_t mask = 1L << (elem & 31);
    return data[word] & mask;
  }

  void remove(uint elem) {
    uint word = elem >> 5;
    if (word >= size) {
      return;
    }
    uint32_t mask = 1L << (elem & 31);
    data[word] &= ~mask; // Clear bit
  }

  // Fast inlined set
  void set(uint elem) {
    uint word = elem >> 5;
    if (word >= size) {
      this->insert(elem);
    } else {
      uint32_t mask = 1L << (elem & 31);
      data[word] |= mask;
    }
  }
};

#endif // SHARE_LIBADT_VECTSET_HPP
