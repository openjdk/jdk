/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

class VirtualSpaceNode;

const size_t metadata_chunk_initialize = 0xf7f7f7f7;

size_t Metachunk::object_alignment() {
  // Must align pointers and sizes to 8,
  // so that 64 bit types get correctly aligned.
  const size_t alignment = 8;

  // Make sure that the Klass alignment also agree.
  STATIC_ASSERT(alignment == (size_t)KlassAlignmentInBytes);

  return alignment;
}

size_t Metachunk::overhead() {
  return align_size_up(sizeof(Metachunk), object_alignment()) / BytesPerWord;
}

// Metachunk methods

Metachunk::Metachunk(size_t word_size,
                     VirtualSpaceNode* container)
    : Metabase<Metachunk>(word_size),
    _top(NULL),
    _container(container)
{
  _top = initial_top();
#ifdef ASSERT
  set_is_tagged_free(false);
  size_t data_word_size = pointer_delta(end(),
                                        _top,
                                        sizeof(MetaWord));
  Copy::fill_to_words((HeapWord*)_top,
                      data_word_size,
                      metadata_chunk_initialize);
#endif
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
size_t Metachunk::used_word_size() const {
  return pointer_delta(_top, bottom(), sizeof(MetaWord));
}

size_t Metachunk::free_word_size() const {
  return pointer_delta(end(), _top, sizeof(MetaWord));
}

void Metachunk::print_on(outputStream* st) const {
  st->print_cr("Metachunk:"
               " bottom " PTR_FORMAT " top " PTR_FORMAT
               " end " PTR_FORMAT " size " SIZE_FORMAT,
               bottom(), _top, end(), word_size());
  if (Verbose) {
    st->print_cr("    used " SIZE_FORMAT " free " SIZE_FORMAT,
                 used_word_size(), free_word_size());
  }
}

#ifndef PRODUCT
void Metachunk::mangle() {
  // Mangle the payload of the chunk and not the links that
  // maintain list of chunks.
  HeapWord* start = (HeapWord*)(bottom() + overhead());
  size_t size = word_size() - overhead();
  Copy::fill_to_words(start, size, metadata_chunk_initialize);
}
#endif // PRODUCT

void Metachunk::verify() {
#ifdef ASSERT
  // Cannot walk through the blocks unless the blocks have
  // headers with sizes.
  assert(bottom() <= _top &&
         _top <= (MetaWord*)end(),
         "Chunk has been smashed");
#endif
  return;
}

/////////////// Unit tests ///////////////

#ifndef PRODUCT

class TestMetachunk {
 public:
  static void test() {
    size_t size = 2 * 1024 * 1024;
    void* memory = malloc(size);
    assert(memory != NULL, "Failed to malloc 2MB");

    Metachunk* metachunk = ::new (memory) Metachunk(size / BytesPerWord, NULL);

    assert(metachunk->bottom() == (MetaWord*)metachunk, "assert");
    assert(metachunk->end() == (uintptr_t*)metachunk + metachunk->size(), "assert");

    // Check sizes
    assert(metachunk->size() == metachunk->word_size(), "assert");
    assert(metachunk->word_size() == pointer_delta(metachunk->end(), metachunk->bottom(),
        sizeof(MetaWord*)), "assert");

    // Check usage
    assert(metachunk->used_word_size() == metachunk->overhead(), "assert");
    assert(metachunk->free_word_size() == metachunk->word_size() - metachunk->used_word_size(), "assert");
    assert(metachunk->top() == metachunk->initial_top(), "assert");
    assert(metachunk->is_empty(), "assert");

    // Allocate
    size_t alloc_size = 64; // Words
    assert(is_size_aligned(alloc_size, Metachunk::object_alignment()), "assert");

    MetaWord* mem = metachunk->allocate(alloc_size);

    // Check post alloc
    assert(mem == metachunk->initial_top(), "assert");
    assert(mem + alloc_size == metachunk->top(), "assert");
    assert(metachunk->used_word_size() == metachunk->overhead() + alloc_size, "assert");
    assert(metachunk->free_word_size() == metachunk->word_size() - metachunk->used_word_size(), "assert");
    assert(!metachunk->is_empty(), "assert");

    // Clear chunk
    metachunk->reset_empty();

    // Check post clear
    assert(metachunk->used_word_size() == metachunk->overhead(), "assert");
    assert(metachunk->free_word_size() == metachunk->word_size() - metachunk->used_word_size(), "assert");
    assert(metachunk->top() == metachunk->initial_top(), "assert");
    assert(metachunk->is_empty(), "assert");

    free(memory);
  }
};

void TestMetachunk_test() {
  TestMetachunk::test();
}

#endif
