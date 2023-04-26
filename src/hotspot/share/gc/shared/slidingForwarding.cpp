/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
#include "gc/shared/slidingForwarding.hpp"
#include "utilities/ostream.hpp"
#include "utilities/powerOfTwo.hpp"

#ifdef _LP64
HeapWord* const SlidingForwarding::UNUSED_BASE = reinterpret_cast<HeapWord*>(0x1);
#endif

SlidingForwarding::SlidingForwarding(MemRegion heap)
#ifdef _LP64
  : _heap_start(heap.start()),
    _num_regions(((heap.end() - heap.start()) >> NUM_COMPRESSED_BITS) + 1),
    _region_size_words_shift(NUM_COMPRESSED_BITS),
    _target_base_table(NEW_C_HEAP_ARRAY(HeapWord*, _num_regions * 2, mtGC)),
    _fallback_table(nullptr) {
  assert(_region_size_words_shift <= NUM_COMPRESSED_BITS, "regions must not be larger than maximum addressing bits allow");
#else
  {
#endif
}

SlidingForwarding::SlidingForwarding(MemRegion heap, size_t region_size_words_shift)
#ifdef _LP64
  : _heap_start(heap.start()),
  _num_regions(((heap.end() - heap.start()) >> region_size_words_shift) + 1),
  _region_size_words_shift(region_size_words_shift),
  _target_base_table(NEW_C_HEAP_ARRAY(HeapWord*, _num_regions * NUM_REGIONS, mtGC)),
  _fallback_table(nullptr) {
  assert(region_size_words_shift <= NUM_COMPRESSED_BITS, "regions must not be larger than maximum addressing bits allow");
#else
  {
#endif
}

SlidingForwarding::~SlidingForwarding() {
#ifdef _LP64
  FREE_C_HEAP_ARRAY(HeapWord*, _target_base_table);
  if (_fallback_table != nullptr) {
    delete _fallback_table;
    _fallback_table = nullptr;
    //FREE_C_HEAP_OBJ(_fallback_table);
  }
#endif
}

void SlidingForwarding::clear() {
#ifdef _LP64
  size_t max = _num_regions * NUM_REGIONS;
  for (size_t i = 0; i < max; i++) {
    _target_base_table[i] = UNUSED_BASE;
  }
  if (_fallback_table != nullptr) {
    delete _fallback_table;
    _fallback_table = nullptr;
    //FREE_C_HEAP_OBJ(_fallback_table);
  }
#endif
}

void SlidingForwarding::fallback_forward_to(HeapWord* from, HeapWord* to) {
  if (_fallback_table == nullptr) {
    _fallback_table = new FallbackTable();
  }
  _fallback_table->forward_to(from, to);
}

HeapWord* SlidingForwarding::fallback_forwardee(HeapWord* from) const {
  if (_fallback_table == nullptr) {
    //tty->print_cr("_fallback_table is null");
    return nullptr;
  } else {
    return _fallback_table->forwardee(from);
  }
}

FallbackTable::FallbackTable() {
  for (size_t i = 0; i < TABLE_SIZE; i++) {
    _table[i]._next = nullptr;
    _table[i]._from = nullptr;
    _table[i]._to   = nullptr;
  }
}

FallbackTable::~FallbackTable() {
  for (size_t i = 0; i < TABLE_SIZE; i++) {
    FallbackTableEntry* entry = _table[i]._next;
    while (entry != nullptr) {
      FallbackTableEntry* next = entry->_next;
      FREE_C_HEAP_OBJ(entry);
      entry = next;
    }
  }
}

size_t FallbackTable::home_index(HeapWord* from) {
  uint64_t val = reinterpret_cast<uint64_t>(from);
  val *= 0xbf58476d1ce4e5b9ull;
  val ^= val >> 56;
  val *= 0x94d049bb133111ebull;
  val = (val * 11400714819323198485llu) >> (64 - log2i_exact(TABLE_SIZE));
  assert(val < TABLE_SIZE, "must fit in table: val: " UINT64_FORMAT ", table-size: " UINTX_FORMAT ", table-size-bits: %d", val, TABLE_SIZE, log2i_exact(TABLE_SIZE));
  return reinterpret_cast<uintx>(val);
}

void FallbackTable::forward_to(HeapWord* from, HeapWord* to) {
  size_t idx = home_index(from);
  //tty->print_cr("index for: " PTR_FORMAT ": " SIZE_FORMAT, p2i(from), idx);
  if (_table[idx]._from != nullptr) {
    FallbackTableEntry* entry = NEW_C_HEAP_OBJ(FallbackTableEntry, mtGC);
    entry->_next = _table[idx]._next;
    entry->_from = _table[idx]._from;
    entry->_to = _table[idx]._to;
    _table[idx]._next = entry;
  }
  _table[idx]._from = from;
  _table[idx]._to   = to;
}

HeapWord* FallbackTable::forwardee(HeapWord* from) const {
  size_t idx = home_index(from);
  //tty->print_cr("forwardee index for: " PTR_FORMAT ": " SIZE_FORMAT, p2i(from), idx);
  const FallbackTableEntry* entry = &_table[idx];
  while (entry != nullptr) {
    //tty->print_cr("checking entry: " PTR_FORMAT, p2i(entry));
    if (entry->_from == from) {
      return entry->_to;
    }
    entry = entry->_next;
  }
  return nullptr;
}
