/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_freeChunk.cpp.incl"

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
