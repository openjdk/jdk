/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/concurrentMarkSweep/freeBlockDictionary.hpp"
#include "utilities/copy.hpp"

#ifndef PRODUCT

#define baadbabeHeapWord badHeapWordVal
#define deadbeefHeapWord 0xdeadbeef

size_t const FreeChunk::header_size() {
  return sizeof(FreeChunk)/HeapWordSize;
}

void FreeChunk::mangleAllocated(size_t size) {
  // mangle all but the header of a just-allocated block
  // of storage
  assert(size >= MinChunkSize, "smallest size of object");
  // we can't assert that _size == size because this may be an
  // allocation out of a linear allocation block
  assert(sizeof(FreeChunk) % HeapWordSize == 0,
         "shouldn't write beyond chunk");
  HeapWord* addr = (HeapWord*)this;
  size_t hdr = header_size();
  Copy::fill_to_words(addr + hdr, size - hdr, baadbabeHeapWord);
}

void FreeChunk::mangleFreed(size_t sz) {
  assert(baadbabeHeapWord != deadbeefHeapWord, "Need distinct patterns");
  // mangle all but the header of a just-freed block of storage
  // just prior to passing it to the storage dictionary
  assert(sz >= MinChunkSize, "smallest size of object");
  assert(sz == size(), "just checking");
  HeapWord* addr = (HeapWord*)this;
  size_t hdr = header_size();
  Copy::fill_to_words(addr + hdr, sz - hdr, deadbeefHeapWord);
}

void FreeChunk::verifyList() const {
  FreeChunk* nextFC = next();
  if (nextFC != NULL) {
    assert(this == nextFC->prev(), "broken chain");
    assert(size() == nextFC->size(), "wrong size");
    nextFC->verifyList();
  }
}
#endif

void FreeChunk::print_on(outputStream* st) {
  st->print_cr("Next: " PTR_FORMAT " Prev: " PTR_FORMAT " %s",
    next(), prev(), cantCoalesce() ? "[can't coalesce]" : "");
}
