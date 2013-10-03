/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
#include "memory/metablock.hpp"
#include "utilities/copy.hpp"
#include "utilities/debug.hpp"

// Blocks of space for metadata are allocated out of Metachunks.
//
// Metachunk are allocated out of MetadataVirtualspaces and once
// allocated there is no explicit link between a Metachunk and
// the MetadataVirtualspaces from which it was allocated.
//
// Each SpaceManager maintains a
// list of the chunks it is using and the current chunk.  The current
// chunk is the chunk from which allocations are done.  Space freed in
// a chunk is placed on the free list of blocks (BlockFreelist) and
// reused from there.
//
// Future modification
//
// The Metachunk can conceivable be replaced by the Chunk in
// allocation.hpp.  Note that the latter Chunk is the space for
// allocation (allocations from the chunk are out of the space in
// the Chunk after the header for the Chunk) where as Metachunks
// point to space in a VirtualSpace.  To replace Metachunks with
// Chunks, change Chunks so that they can be allocated out of a VirtualSpace.
size_t Metablock::_min_block_byte_size = sizeof(Metablock);

// New blocks returned by the Metaspace are zero initialized.
// We should fix the constructors to not assume this instead.
Metablock* Metablock::initialize(MetaWord* p, size_t word_size) {
  if (p == NULL) {
    return NULL;
  }

  Metablock* result = (Metablock*) p;

  // Clear the memory
  Copy::fill_to_aligned_words((HeapWord*)result, word_size);
#ifdef ASSERT
  result->set_word_size(word_size);
#endif
  return result;
}
