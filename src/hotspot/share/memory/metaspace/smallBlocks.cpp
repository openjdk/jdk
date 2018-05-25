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

#include "memory/metaspace/smallBlocks.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

namespace metaspace {

void SmallBlocks::print_on(outputStream* st) const {
  st->print_cr("SmallBlocks:");
  for (uint i = _small_block_min_size; i < _small_block_max_size; i++) {
    uint k = i - _small_block_min_size;
    st->print_cr("small_lists size " SIZE_FORMAT " count " SIZE_FORMAT, _small_lists[k].size(), _small_lists[k].count());
  }
}


// Returns the total size, in words, of all blocks, across all block sizes.
size_t SmallBlocks::total_size() const {
  size_t result = 0;
  for (uint i = _small_block_min_size; i < _small_block_max_size; i++) {
    uint k = i - _small_block_min_size;
    result = result + _small_lists[k].count() * _small_lists[k].size();
  }
  return result;
}

// Returns the total number of all blocks across all block sizes.
uintx SmallBlocks::total_num_blocks() const {
  uintx result = 0;
  for (uint i = _small_block_min_size; i < _small_block_max_size; i++) {
    uint k = i - _small_block_min_size;
    result = result + _small_lists[k].count();
  }
  return result;
}

} // namespace metaspace

