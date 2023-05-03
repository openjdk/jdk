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
// 0x1 is safe because heap base addresses must be aligned by much larger alignment
HeapWord* const SlidingForwarding::UNUSED_BASE = reinterpret_cast<HeapWord*>(0x1);

SlidingForwarding* SlidingForwarding::_sliding_forwarding = nullptr;

SlidingForwarding::SlidingForwarding(MemRegion heap, size_t region_size_words)
  : _heap_start(heap.start()),
    _num_regions(align_up(pointer_delta(heap.end(), heap.start()), region_size_words) / region_size_words),
    _region_size_words(region_size_words),
    _region_size_words_shift(log2i_exact(region_size_words)),
  _bases_table(nullptr),
  _fallback_table(nullptr) {
  assert(_region_size_words >= 1, "regions must be at least a word large");
  assert(_region_size_words <= pointer_delta(heap.end(), heap.start()), "");
  assert(_region_size_words_shift <= NUM_OFFSET_BITS, "regions must not be larger than maximum addressing bits allow");
  size_t heap_size_words = heap.end() - heap.start();
  if (UseSerialGC && heap_size_words <= (1 << NUM_OFFSET_BITS)) {
    // In this case we can treat the whole heap as a single region and
    // make the encoding very simple.
    _num_regions = 1;
    _region_size_words = round_up_power_of_2(heap_size_words);
    _region_size_words_shift = log2i_exact(_region_size_words);
  }
}

SlidingForwarding::~SlidingForwarding() {
  FREE_C_HEAP_ARRAY(region_bases, _bases_table);
  _bases_table = nullptr;
  delete _fallback_table;
  _fallback_table = nullptr;
}

void SlidingForwarding::initialize(MemRegion heap, size_t region_size_words) {
#ifdef _LP64
  if (UseAltGCForwarding) {
    assert(_sliding_forwarding == nullptr, "only call this once");
    _sliding_forwarding = new SlidingForwarding(heap, region_size_words);
  }
#endif
}

void SlidingForwarding::begin_impl() {
  assert(_bases_table == nullptr, "Should be uninitialized");
  size_t max = _num_regions * NUM_TARGET_REGIONS;
  _bases_table = NEW_C_HEAP_ARRAY(HeapWord*, max, mtGC);
  for (size_t i = 0; i < max; i++) {
    _bases_table[i] = UNUSED_BASE;
  }
}

void SlidingForwarding::begin() {
#ifdef _LP64
  if (UseAltGCForwarding) {
    assert(_sliding_forwarding != nullptr, "expect sliding forwarding initialized");
    _sliding_forwarding->begin_impl();
  }
#endif
}

void SlidingForwarding::end_impl() {
  assert(_bases_table != nullptr, "Should be initialized");
  FREE_C_HEAP_ARRAY(HeapWord*, _bases_table);
  _bases_table = nullptr;

  delete _fallback_table;
  _fallback_table = nullptr;
}

void SlidingForwarding::end() {
#ifdef _LP64
  if (UseAltGCForwarding) {
    assert(_sliding_forwarding != nullptr, "expect sliding forwarding initialized");
    _sliding_forwarding->end_impl();
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

uint FallbackTable::home_index(HeapWord* from) {
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
  uint idx = static_cast<uint>(val);
  assert(idx < TABLE_SIZE, "must fit in table: idx: %u, table-size: %u, table-size-bits: %d",
         idx, TABLE_SIZE, log2i_exact(TABLE_SIZE));
  return idx;
}

void FallbackTable::forward_to(HeapWord* from, HeapWord* to) {
  uint idx = home_index(from);
  FallbackTableEntry* head = &_table[idx];
  FallbackTableEntry* entry = head;
  // Search existing entry in chain starting at idx.
  while (entry != nullptr) {
    if (entry->_from == from) {
      break;
    }
    entry = entry->_next;
  }
  if (entry == nullptr) {
    // No entry found, create new one and insert after head.
    FallbackTableEntry* new_entry = NEW_C_HEAP_OBJ(FallbackTableEntry, mtGC);
    new_entry->_next = head->_next;
    new_entry->_from = head->_from;
    new_entry->_to   = head->_to;
    head->_next = new_entry;
    entry = head; // Set from and to fields below.
  }
  // Set from and to in new or found entry.
  entry->_from = from;
  entry->_to   = to;
}

HeapWord* FallbackTable::forwardee(HeapWord* from) const {
  uint idx = home_index(from);
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
