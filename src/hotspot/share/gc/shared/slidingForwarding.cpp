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
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/slidingForwarding.hpp"
#include "utilities/ostream.hpp"
#include "utilities/powerOfTwo.hpp"

#ifdef _LP64

// We cannot use 0, because that may already be a valid base address in zero-based heaps.
// 0x1 is safe because heap base addresses must be aligned by much larger alginemnt
HeapWord* const SlidingForwarding::UNUSED_BASE = reinterpret_cast<HeapWord*>(0x1);

SlidingForwarding::SlidingForwarding(MemRegion heap, size_t region_size_words)
  : _heap_start(heap.start()),
    _num_regions(((heap.end() - heap.start()) / region_size_words) + 1),
    _region_size_words(region_size_words),
    _region_size_words_shift(log2i_exact(region_size_words)),
  _bases_table(nullptr),
  _fallback_table(nullptr) {
  assert(_region_size_words_shift <= NUM_COMPRESSED_BITS, "regions must not be larger than maximum addressing bits allow");
  size_t heap_size_words = heap.end() - heap.start();
  if (UseSerialGC && heap_size_words <= (1 << NUM_COMPRESSED_BITS)) {
    // In this case we can treat the whole heap as a single region and
    // make the encoding very simple.
    _num_regions = 1;
    _region_size_words = round_up_power_of_2(heap_size_words);
    _region_size_words_shift = log2i_exact(_region_size_words);
  }
}

SlidingForwarding::~SlidingForwarding() {
  if (_bases_table != nullptr) {
    FREE_C_HEAP_ARRAY(HeapWord*, _bases_table);
    _bases_table = nullptr;
  }
  if (_fallback_table != nullptr) {
    delete _fallback_table;
    _fallback_table = nullptr;
  }
}

void SlidingForwarding::begin() {
  assert(_bases_table == nullptr, "Should be uninitialized");
  size_t max = _num_regions * NUM_TARGET_REGIONS;
  _bases_table = NEW_C_HEAP_ARRAY(HeapWord*, max, mtGC);
  for (size_t i = 0; i < max; i++) {
    _bases_table[i] = UNUSED_BASE;
  }
}

void SlidingForwarding::end() {
  assert(_bases_table != nullptr, "Should be initialized");
  FREE_C_HEAP_ARRAY(HeapWord*, _bases_table);
  _bases_table = nullptr;

  if (_fallback_table != nullptr) {
    delete _fallback_table;
    _fallback_table = nullptr;
  }
}

void SlidingForwarding::fallback_forward_to(HeapWord* from, HeapWord* to) {
  if (_fallback_table == nullptr) {
    _fallback_table = new FallbackTable();
  }
  _fallback_table->forward_to(from, to);
}

HeapWord* SlidingForwarding::fallback_forwardee(HeapWord* from) const {
  assert(_fallback_table != nullptr, "fallback table must be present");
  return _fallback_table->forwardee(from);
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
  // This is the mixer stage of the murmur3 hashing:
  // https://github.com/aappleby/smhasher/blob/master/src/MurmurHash3.cpp
  val ^= val >> 33;
  val *= 0xff51afd7ed558ccdULL;
  val ^= val >> 33;
  val *= 0xc4ceb9fe1a85ec53ULL;
  val ^= val >> 33;
  // Shift to table-size.
  val = val >> (64 - log2i_exact(TABLE_SIZE));
  assert(val < TABLE_SIZE, "must fit in table: val: " UINT64_FORMAT ", table-size: " UINTX_FORMAT ", table-size-bits: %d",
         val, TABLE_SIZE, log2i_exact(TABLE_SIZE));
  return static_cast<size_t>(val);
}

void FallbackTable::forward_to(HeapWord* from, HeapWord* to) {
  size_t idx = home_index(from);
  if (_table[idx]._from != nullptr) {
    FallbackTableEntry* entry = NEW_C_HEAP_OBJ(FallbackTableEntry, mtGC);
    entry->_next = _table[idx]._next;
    entry->_from = _table[idx]._from;
    entry->_to = _table[idx]._to;
    _table[idx]._next = entry;
  } else {
    assert(_table[idx]._next == nullptr, "next-link should be null here");
  }
  _table[idx]._from = from;
  _table[idx]._to   = to;
}

HeapWord* FallbackTable::forwardee(HeapWord* from) const {
  size_t idx = home_index(from);
  const FallbackTableEntry* entry = &_table[idx];
  while (entry != nullptr) {
    if (entry->_from == from) {
      return entry->_to;
    }
    entry = entry->_next;
  }
  return nullptr;
}

#endif // _LP64
