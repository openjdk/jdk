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
#include "utilities/fastHash.hpp"
#include "utilities/ostream.hpp"
#include "utilities/powerOfTwo.hpp"

#include "logging/log.hpp"

// We cannot use 0, because that may already be a valid base address in zero-based heaps.
// 0x1 is safe because heap base addresses must be aligned by much larger alignment
HeapWord* const SlidingForwarding::UNUSED_BASE = reinterpret_cast<HeapWord*>(0x1);

HeapWord* SlidingForwarding::_heap_start = nullptr;
size_t SlidingForwarding::_region_size_words = 0;
size_t SlidingForwarding::_heap_start_region_bias = 0;
size_t SlidingForwarding::_num_regions = 0;
uint SlidingForwarding::_region_size_bytes_shift = 0;
uintptr_t SlidingForwarding::_region_mask = 0;
HeapWord** SlidingForwarding::_biased_bases[SlidingForwarding::NUM_TARGET_REGIONS] = { nullptr, nullptr };
HeapWord** SlidingForwarding::_bases_table = nullptr;
FallbackTable* SlidingForwarding::_fallback_table = nullptr;

void SlidingForwarding::initialize(MemRegion heap, size_t region_size_words) {
#ifdef _LP64
  if (UseAltGCForwarding) {
    _heap_start = heap.start();

    if (UseSerialGC && heap.word_size() <= (1 << NUM_OFFSET_BITS)) {
      // In this case we can treat the whole heap as a single region and
      // make the encoding very simple.
      _num_regions = 1;
      _region_size_words = heap.word_size();
      _region_size_bytes_shift = log2i_exact(round_up_power_of_2(_region_size_words)) + LogHeapWordSize;
  } else {
      _num_regions = align_up(pointer_delta(heap.end(), heap.start()), region_size_words) / region_size_words;
      _region_size_words = region_size_words;
      _region_size_bytes_shift = log2i_exact(_region_size_words) + LogHeapWordSize;
    }
    _heap_start_region_bias = (uintptr_t)_heap_start >> _region_size_bytes_shift;
    _region_mask = ~((uintptr_t(1) << _region_size_bytes_shift) - 1);

    guarantee((_heap_start_region_bias << _region_size_bytes_shift) == (uintptr_t)_heap_start, "must be aligned");

    assert(_region_size_words >= 1, "regions must be at least a word large");
    assert(_bases_table == nullptr, "should not be initialized yet");
    assert(_fallback_table == nullptr, "should not be initialized yet");
  }
#endif
}

void SlidingForwarding::begin() {
#ifdef _LP64
  if (UseAltGCForwarding) {
    assert(_bases_table == nullptr, "should not be initialized yet");
    assert(_fallback_table == nullptr, "should not be initialized yet");

    size_t max = _num_regions * NUM_TARGET_REGIONS;
    _bases_table = NEW_C_HEAP_ARRAY(HeapWord*, max, mtGC);
    _biased_bases[0] = _bases_table - _heap_start_region_bias;
    _biased_bases[1] = _bases_table + _num_regions - _heap_start_region_bias;
    for (size_t i = 0; i < max; i++) {
      _bases_table[i] = UNUSED_BASE;
    }
  }
#endif
}

void SlidingForwarding::end() {
#ifdef _LP64
  if (UseAltGCForwarding) {
    assert(_bases_table != nullptr, "should be initialized");
    FREE_C_HEAP_ARRAY(HeapWord*, _bases_table);
    _bases_table = nullptr;
    delete _fallback_table;
    _fallback_table = nullptr;
  }
#endif
}

void SlidingForwarding::fallback_forward_to(HeapWord* from, HeapWord* to) {
  if (_fallback_table == nullptr) {
    _fallback_table = new FallbackTable();
  }
  _fallback_table->forward_to(from, to);
}

HeapWord* SlidingForwarding::fallback_forwardee(HeapWord* from) {
  assert(_fallback_table != nullptr, "fallback table must be present");
  return _fallback_table->forwardee(from);
}

FallbackTable::FallbackTable() {
  for (uint i = 0; i < TABLE_SIZE; i++) {
    _table[i]._next = nullptr;
    _table[i]._from = nullptr;
    _table[i]._to   = nullptr;
  }
}

FallbackTable::~FallbackTable() {
  for (uint i = 0; i < TABLE_SIZE; i++) {
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
  uint64_t hash = FastHash::get_hash64(val, UCONST64(0xAAAAAAAAAAAAAAAA));
  return hash >> (64 - log2i_exact(TABLE_SIZE));
}

void FallbackTable::forward_to(HeapWord* from, HeapWord* to) {
  size_t idx = home_index(from);
  FallbackTableEntry* head = &_table[idx];
  FallbackTableEntry* entry = head;
  // Search existing entry in chain starting at idx.
  while (entry != nullptr) {
    if (entry->_from == from || entry->_from == nullptr) {
      break;
    }
    entry = entry->_next;
  }
  if (entry == nullptr) {
    // No entry found, create new one and insert after head.
    FallbackTableEntry* new_entry = NEW_C_HEAP_OBJ(FallbackTableEntry, mtGC);
    *new_entry = *head;
    head->_next = new_entry;
    entry = head; // Set from and to fields below.
  }
  // Set from and to in new or found entry.
  entry->_from = from;
  entry->_to   = to;
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
