/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/metachunk.hpp"
#include "utilities/copy.hpp"
#include "utilities/debug.hpp"

//
// Future modification
//
// The Metachunk can conceivable be replaced by the Chunk in
// allocation.hpp.  Note that the latter Chunk is the space for
// allocation (allocations from the chunk are out of the space in
// the Chunk after the header for the Chunk) where as Metachunks
// point to space in a VirtualSpace.  To replace Metachunks with
// Chunks, change Chunks so that they can be allocated out of a VirtualSpace.

const size_t metadata_chunk_initialize = 0xf7f7f7f7;

size_t Metachunk::_overhead =
  Chunk::aligned_overhead_size(sizeof(Metachunk)) / BytesPerWord;

// Metachunk methods

Metachunk* Metachunk::initialize(MetaWord* ptr, size_t word_size) {
  // Set bottom, top, and end.  Allow space for the Metachunk itself
  Metachunk* chunk = (Metachunk*) ptr;

  MetaWord* chunk_bottom = ptr + _overhead;
  chunk->set_bottom(ptr);
  chunk->set_top(chunk_bottom);
  MetaWord* chunk_end = ptr + word_size;
  assert(chunk_end > chunk_bottom, "Chunk must be too small");
  chunk->set_end(chunk_end);
  chunk->set_next(NULL);
  chunk->set_word_size(word_size);
#ifdef ASSERT
  size_t data_word_size = pointer_delta(chunk_end, chunk_bottom, sizeof(MetaWord));
  Copy::fill_to_words((HeapWord*) chunk_bottom, data_word_size, metadata_chunk_initialize);
#endif
  return chunk;
}


MetaWord* Metachunk::allocate(size_t word_size) {
  MetaWord* result = NULL;
  // If available, bump the pointer to allocate.
  if (free_word_size() >= word_size) {
    result = _top;
    _top = _top + word_size;
  }
  return result;
}

// _bottom points to the start of the chunk including the overhead.
size_t Metachunk::used_word_size() {
  return pointer_delta(_top, _bottom, sizeof(MetaWord));
}

size_t Metachunk::free_word_size() {
  return pointer_delta(_end, _top, sizeof(MetaWord));
}

size_t Metachunk::capacity_word_size() {
  return pointer_delta(_end, _bottom, sizeof(MetaWord));
}

void Metachunk::print_on(outputStream* st) const {
  st->print_cr("Metachunk:"
               " bottom " PTR_FORMAT " top " PTR_FORMAT
               " end " PTR_FORMAT " size " SIZE_FORMAT,
               bottom(), top(), end(), word_size());
}

#ifndef PRODUCT
void Metachunk::mangle() {
  // Mangle the payload of the chunk and not the links that
  // maintain list of chunks.
  HeapWord* start = (HeapWord*)(bottom() + overhead());
  size_t word_size = capacity_word_size() - overhead();
  Copy::fill_to_words(start, word_size, metadata_chunk_initialize);
}
#endif // PRODUCT

void Metachunk::verify() {
#ifdef ASSERT
  // Cannot walk through the blocks unless the blocks have
  // headers with sizes.
  assert(_bottom <= _top &&
         _top <= _end,
         "Chunk has been smashed");
#endif
  return;
}
