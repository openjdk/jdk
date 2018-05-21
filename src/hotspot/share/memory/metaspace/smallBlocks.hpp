/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_SMALLBLOCKS_HPP
#define SHARE_MEMORY_METASPACE_SMALLBLOCKS_HPP

#include "memory/allocation.hpp"
#include "memory/binaryTreeDictionary.hpp"
#include "memory/metaspace/metablock.hpp"
#include "utilities/globalDefinitions.hpp"

class outputStream;

namespace metaspace {

class SmallBlocks : public CHeapObj<mtClass> {

  const static uint _small_block_max_size = sizeof(TreeChunk<Metablock,  FreeList<Metablock> >)/HeapWordSize;
  // Note: this corresponds to the imposed miminum allocation size, see SpaceManager::get_allocation_word_size()
  const static uint _small_block_min_size = sizeof(Metablock)/HeapWordSize;

private:
  FreeList<Metablock> _small_lists[_small_block_max_size - _small_block_min_size];

  FreeList<Metablock>& list_at(size_t word_size) {
    assert(word_size >= _small_block_min_size, "There are no metaspace objects less than %u words", _small_block_min_size);
    return _small_lists[word_size - _small_block_min_size];
  }

public:
  SmallBlocks() {
    for (uint i = _small_block_min_size; i < _small_block_max_size; i++) {
      uint k = i - _small_block_min_size;
      _small_lists[k].set_size(i);
    }
  }

  // Returns the total size, in words, of all blocks, across all block sizes.
  size_t total_size() const;

  // Returns the total number of all blocks across all block sizes.
  uintx total_num_blocks() const;

  static uint small_block_max_size() { return _small_block_max_size; }
  static uint small_block_min_size() { return _small_block_min_size; }

  MetaWord* get_block(size_t word_size) {
    if (list_at(word_size).count() > 0) {
      MetaWord* new_block = (MetaWord*) list_at(word_size).get_chunk_at_head();
      return new_block;
    } else {
      return NULL;
    }
  }
  void return_block(Metablock* free_chunk, size_t word_size) {
    list_at(word_size).return_chunk_at_head(free_chunk, false);
    assert(list_at(word_size).count() > 0, "Should have a chunk");
  }

  void print_on(outputStream* st) const;

};

} // namespace metaspace


#endif /* SHARE_MEMORY_METASPACE_SMALLBLOCKS_HPP */

