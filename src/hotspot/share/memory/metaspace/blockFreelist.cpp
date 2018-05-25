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
#include "precompiled.hpp"

#include "logging/log.hpp"
#include "memory/binaryTreeDictionary.inline.hpp"
#include "memory/metaspace/blockFreelist.hpp"
#include "utilities/ostream.hpp"
#include "utilities/globalDefinitions.hpp"


namespace metaspace {


BlockFreelist::BlockFreelist() : _dictionary(new BlockTreeDictionary()), _small_blocks(NULL) {}

BlockFreelist::~BlockFreelist() {
  delete _dictionary;
  if (_small_blocks != NULL) {
    delete _small_blocks;
  }
}

void BlockFreelist::return_block(MetaWord* p, size_t word_size) {
  assert(word_size >= SmallBlocks::small_block_min_size(), "never return dark matter");

  Metablock* free_chunk = ::new (p) Metablock(word_size);
  if (word_size < SmallBlocks::small_block_max_size()) {
    small_blocks()->return_block(free_chunk, word_size);
  } else {
  dictionary()->return_chunk(free_chunk);
}
  log_trace(gc, metaspace, freelist, blocks)("returning block at " INTPTR_FORMAT " size = "
            SIZE_FORMAT, p2i(free_chunk), word_size);
}

MetaWord* BlockFreelist::get_block(size_t word_size) {
  assert(word_size >= SmallBlocks::small_block_min_size(), "never get dark matter");

  // Try small_blocks first.
  if (word_size < SmallBlocks::small_block_max_size()) {
    // Don't create small_blocks() until needed.  small_blocks() allocates the small block list for
    // this space manager.
    MetaWord* new_block = (MetaWord*) small_blocks()->get_block(word_size);
    if (new_block != NULL) {
      log_trace(gc, metaspace, freelist, blocks)("getting block at " INTPTR_FORMAT " size = " SIZE_FORMAT,
              p2i(new_block), word_size);
      return new_block;
    }
  }

  if (word_size < BlockFreelist::min_dictionary_size()) {
    // If allocation in small blocks fails, this is Dark Matter.  Too small for dictionary.
    return NULL;
  }

  Metablock* free_block = dictionary()->get_chunk(word_size);
  if (free_block == NULL) {
    return NULL;
  }

  const size_t block_size = free_block->size();
  if (block_size > WasteMultiplier * word_size) {
    return_block((MetaWord*)free_block, block_size);
    return NULL;
  }

  MetaWord* new_block = (MetaWord*)free_block;
  assert(block_size >= word_size, "Incorrect size of block from freelist");
  const size_t unused = block_size - word_size;
  if (unused >= SmallBlocks::small_block_min_size()) {
    return_block(new_block + word_size, unused);
  }

  log_trace(gc, metaspace, freelist, blocks)("getting block at " INTPTR_FORMAT " size = " SIZE_FORMAT,
            p2i(new_block), word_size);
  return new_block;
}

void BlockFreelist::print_on(outputStream* st) const {
  dictionary()->print_free_lists(st);
  if (_small_blocks != NULL) {
    _small_blocks->print_on(st);
  }
}

} // namespace metaspace

