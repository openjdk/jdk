/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
 * Copyright (c) 2023 Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_BINLIST_HPP
#define SHARE_MEMORY_METASPACE_BINLIST_HPP

#include "memory/metaspace/counters.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {

// BinList is a data structure to manage small to very small memory blocks
// (only a few words). It is used to manage deallocated blocks - see
// class FreeBlocks.

// Memory blocks are kept in a vector of linked lists of equi-sized blocks:
//
// wordsize
//
//       +---+   +---+   +---+      +---+
//  1    |   |-->|   |-->|   |-...->|   |
//       +---+   +---+   +---+      +---+
//
//       +----+   +----+   +----+      +----+
//  2    |    |-->|    |-->|    |-...->|    |
//       +----+   +----+   +----+      +----+
//
//       +-----+   +-----+   +-----+      +-----+
//  3    |     |-->|     |-->|     |-...->|     |
//       +-----+   +-----+   +-----+      +-----+
//  .
//  .
//  .
//
//       +----------+   +----------+   +----------+      +----------+
//  n    |          |-->|          |-->|          |-...->|          |
//       +----------+   +----------+   +----------+      +----------+

// Insertion is of course fast, O(1).
//
// On retrieval, we attempt to find the closest fit to a given size, walking the
// list head vector (a bitmask is used to speed that part up).
//
// This structure is a bit expensive in memory costs (we pay one pointer per managed
// block size) so we only use it for a small number of sizes.

template <int num_lists>
class BinListImpl {

  struct Block {
    Block* const _next;
    Block(Block* next) : _next(next) {}
  };

#define BLOCK_FORMAT              "Block @" PTR_FORMAT ": size: " SIZE_FORMAT ", next: " PTR_FORMAT
#define BLOCK_FORMAT_ARGS(b, sz)  p2i(b), (sz), p2i((b)->_next)

  // Block size must be exactly one word size.
  STATIC_ASSERT(sizeof(Block) == BytesPerWord);
  STATIC_ASSERT(num_lists > 0);

public:

  // Minimal word size a block must have to be manageable by this structure.
  const static size_t MinWordSize = 1;

  // Maximal (incl) word size a block can have to be manageable by this structure.
  const static size_t MaxWordSize = num_lists;

private:

  Block* _blocks[num_lists];

  MemRangeCounter _counter;

  // Given a word size, returns the index of the list holding blocks of that size
  static int index_for_word_size(size_t word_size) {
    int index = (int)(word_size - MinWordSize);
    assert(index >= 0 && index < num_lists, "Invalid index %d", index);
    return index;
  }

  // Given an index of a list, return the word size that list serves
  static size_t word_size_for_index(int index) {
    assert(index >= 0 && index < num_lists, "Invalid index %d", index);
    return index + MinWordSize;
  }

  // Search the range [index, _num_lists) for the smallest non-empty list. Returns -1 on fail.
  int index_for_next_non_empty_list(int index) {
    assert(index >= 0 && index < num_lists, "Invalid index %d", index);
    int i2 = index;
    while (i2 < num_lists && _blocks[i2] == nullptr) {
      i2 ++;
    }
    return i2 == num_lists ? -1 : i2;
  }

#ifdef ASSERT
  static const uintptr_t canary = 0xFFEEFFEE;
  static void write_canary(MetaWord* p, size_t word_size) {
    if (word_size > 1) { // 1-word-sized blocks have no space for a canary
      ((uintptr_t*)p)[word_size - 1] = canary;
    }
  }
  static bool check_canary(const Block* b, size_t word_size) {
    return word_size == 1 || // 1-word-sized blocks have no space for a canary
           ((const uintptr_t*)b)[word_size - 1] == canary;
  }
#endif

public:

  BinListImpl() {
    for (int i = 0; i < num_lists; i++) {
      _blocks[i] = nullptr;
    }
  }

  void add_block(MetaWord* p, size_t word_size) {
    assert(word_size >= MinWordSize &&
           word_size <= MaxWordSize, "bad block size");
    DEBUG_ONLY(write_canary(p, word_size);)
    const int index = index_for_word_size(word_size);
    Block* old_head = _blocks[index];
    Block* new_head = new (p) Block(old_head);
    _blocks[index] = new_head;
    _counter.add(word_size);
  }

  // Given a word_size, searches and returns a block of at least that size.
  // Block may be larger. Real block size is returned in *p_real_word_size.
  MetaWord* remove_block(size_t word_size, size_t* p_real_word_size) {
    assert(word_size >= MinWordSize &&
           word_size <= MaxWordSize, "bad block size " SIZE_FORMAT ".", word_size);
    int index = index_for_word_size(word_size);
    index = index_for_next_non_empty_list(index);
    if (index != -1) {
      Block* b = _blocks[index];
      const size_t real_word_size = word_size_for_index(index);
      assert(b != nullptr, "Sanity");
      assert(check_canary(b, real_word_size),
             "bad block in list[%d] (" BLOCK_FORMAT ")", index, BLOCK_FORMAT_ARGS(b, real_word_size));
      _blocks[index] = b->_next;
      _counter.sub(real_word_size);
      *p_real_word_size = real_word_size;
      return (MetaWord*)b;
    } else {
      *p_real_word_size = 0;
      return nullptr;
    }
  }

  // Returns number of blocks in this structure
  unsigned count() const { return _counter.count(); }

  // Returns total size, in words, of all elements.
  size_t total_size() const { return _counter.total_size(); }

  bool is_empty() const { return count() == 0; }

#ifdef ASSERT
  void verify() const {
    MemRangeCounter local_counter;
    for (int i = 0; i < num_lists; i++) {
      const size_t s = word_size_for_index(i);
      int pos = 0;
      for (Block* b = _blocks[i]; b != nullptr; b = b->_next, pos++) {
        assert(check_canary(b, s), "");
        local_counter.add(s);
      }
    }
    local_counter.check(_counter);
  }
#endif // ASSERT

};

typedef BinListImpl<32> BinList32;

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_BINLIST_HPP
