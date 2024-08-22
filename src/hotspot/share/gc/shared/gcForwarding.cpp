/*
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
#include "gc/shared/gcForwarding.hpp"
#include "memory/memRegion.hpp"
#include "runtime/globals_extension.hpp"

HeapWord* GCForwarding::_heap_base = nullptr;
int GCForwarding::_num_low_bits = 0;

void GCForwarding::initialize_flags(size_t max_heap_size) {
#ifdef _LP64
  size_t max_narrow_heap_size = right_n_bits(NumLowBitsNarrow - Shift);
  if (UseCompactObjectHeaders && max_heap_size > max_narrow_heap_size * HeapWordSize) {
    FLAG_SET_DEFAULT(UseCompactObjectHeaders, false);
  }
#endif
}

void GCForwarding::initialize(MemRegion heap) {
#ifdef _LP64
  _heap_base = heap.start();
  if (heap.word_size() <= right_n_bits(NumLowBitsNarrow - Shift)) {
    _num_low_bits = NumLowBitsNarrow;
  } else {
    assert(!UseCompactObjectHeaders, "Compact object headers should be turned off for large heaps");
    _num_low_bits = NumLowBitsWide;
  }
#endif
}
