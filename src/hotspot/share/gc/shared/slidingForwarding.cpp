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
SlidingForwarding::FallbackTable* SlidingForwarding::_fallback_table = nullptr;

void SlidingForwarding::initialize(MemRegion heap, size_t region_size_words) {
#ifdef _LP64
  if (UseAltGCForwarding) {
    _heap_start = heap.start();

    // If the heap is small enough to fit directly into the available offset bits,
    // and we are running Serial GC, we can treat the whole heap as a single region
    // if it happens to be aligned to allow biasing.
    size_t rounded_heap_size = round_up_power_of_2(heap.byte_size());

    if (UseSerialGC && (heap.word_size() <= (1 << NUM_OFFSET_BITS)) &&
        is_aligned((uintptr_t)_heap_start, rounded_heap_size)) {
      _num_regions = 1;
      _region_size_words = heap.word_size();
      _region_size_bytes_shift = log2i_exact(rounded_heap_size);
    } else {
      _num_regions = align_up(pointer_delta(heap.end(), heap.start()), region_size_words) / region_size_words;
      _region_size_words = region_size_words;
      _region_size_bytes_shift = log2i_exact(_region_size_words) + LogHeapWordSize;
    }
    _heap_start_region_bias = (uintptr_t)_heap_start >> _region_size_bytes_shift;
    _region_mask = ~((uintptr_t(1) << _region_size_bytes_shift) - 1);

    guarantee((_heap_start_region_bias << _region_size_bytes_shift) == (uintptr_t)_heap_start, "must be aligned: _heap_start_region_bias: " SIZE_FORMAT ", _region_size_byte_shift: %u, _heap_start: " PTR_FORMAT, _heap_start_region_bias, _region_size_bytes_shift, p2i(_heap_start));

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
    HeapWord** biased_start = _bases_table - _heap_start_region_bias;
    _biased_bases[0] = biased_start;
    _biased_bases[1] = biased_start + _num_regions;
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
    _fallback_table = new (mtGC) FallbackTable();
  }
  _fallback_table->put_when_absent(from, to);
}

HeapWord* SlidingForwarding::fallback_forwardee(HeapWord* from) {
  assert(_fallback_table != nullptr, "fallback table must be present");
  HeapWord** found = _fallback_table->get(from);
  if (found != nullptr) {
    return *found;
  } else {
    return nullptr;
  }
}
