/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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

#ifdef _LP64
HeapWord* const SlidingForwarding::UNUSED_BASE = reinterpret_cast<HeapWord*>(0x1);
#endif

SlidingForwarding::SlidingForwarding(MemRegion heap)
#ifdef _LP64
        : _heap_start(heap.start()),
          _num_regions(((heap.end() - heap.start()) >> NUM_COMPRESSED_BITS) + 1),
          _region_size_words_shift(NUM_COMPRESSED_BITS),
          _target_base_table(NEW_C_HEAP_ARRAY(HeapWord*, _num_regions * 2, mtGC)) {
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
          _target_base_table(NEW_C_HEAP_ARRAY(HeapWord*, _num_regions * (ONE << NUM_REGION_BITS), mtGC)) {
  assert(region_size_words_shift <= NUM_COMPRESSED_BITS, "regions must not be larger than maximum addressing bits allow");
#else
  {
#endif
}

SlidingForwarding::~SlidingForwarding() {
#ifdef _LP64
  FREE_C_HEAP_ARRAY(HeapWord*, _target_base_table);
#endif
}

void SlidingForwarding::clear() {
#ifdef _LP64
  size_t max = _num_regions * (ONE << NUM_REGION_BITS);
  for (size_t i = 0; i < max; i++) {
    _target_base_table[i] = UNUSED_BASE;
  }
#endif
}
