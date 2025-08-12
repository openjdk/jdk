/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1BiasedArray.hpp"
#include "memory/padded.inline.hpp"

G1BiasedMappedArrayBase::G1BiasedMappedArrayBase() :
  _alloc_base(nullptr),
  _base(nullptr),
  _length(0),
  _biased_base(0),
  _bias(0),
  _shift_by(0) { }

G1BiasedMappedArrayBase::~G1BiasedMappedArrayBase() {
  FreeHeap(_alloc_base);
}

// Allocate a new array, generic version.
address G1BiasedMappedArrayBase::create_new_base_array(size_t length, size_t elem_size) {
  assert(length > 0, "just checking");
  assert(elem_size > 0, "just checking");
  return PaddedPrimitiveArray<u_char, mtGC>::create(length * elem_size, &_alloc_base);
}

#ifndef PRODUCT
void G1BiasedMappedArrayBase::verify_index(idx_t index) const {
  guarantee(_base != nullptr, "Array not initialized");
  guarantee(index < length(), "Index out of bounds index: %zu length: %zu", index, length());
}

void G1BiasedMappedArrayBase::verify_biased_index(idx_t biased_index) const {
  guarantee(_biased_base != 0, "Array not initialized");
  guarantee(biased_index >= bias() && biased_index < (bias() + length()),
            "Biased index out of bounds, index: %zu bias: %zu length: %zu",
            biased_index, bias(), length());
}
#endif
