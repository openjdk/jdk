/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/arrayOop.hpp"

// Return the maximum length (num elements) of an array of BasicType.  The length can passed
// to typeArrayOop::object_size(scale, length, header_size) without causing an
// overflow. We also need to make sure that this will not overflow a size_t on
// 32 bit platforms when we convert it to a byte size.
int32_t arrayOopDesc::max_array_length(BasicType type) {
  assert(type >= 0 && type < T_CONFLICT, "wrong type");
  assert(type2aelembytes(type) != 0, "wrong type");

  const int elem_size = type2aelembytes(type);
  const size_t max_size_bytes = align_down(SIZE_MAX - base_offset_in_bytes(type), MinObjAlignmentInBytes);
  assert(is_aligned(max_size_bytes, elem_size), "max_size_bytes should be aligned to element size");
  size_t max_elements_per_size_t = max_size_bytes / elem_size;
  if ((size_t)max_jint < max_elements_per_size_t) {
    // It should be ok to return max_jint here, but parts of the code
    // (CollectedHeap, Klass::oop_oop_iterate(), and more) uses an int for
    // passing around the size (in words) of an object. So, we need to avoid
    // overflowing an int when we add the header. See CRs 4718400 and 7110613.
    const size_t header_size_words = heap_word_size(base_offset_in_bytes(type));
    max_elements_per_size_t = align_down(max_jint - static_cast<int>(header_size_words), MinObjAlignment);
  }
  assert(max_elements_per_size_t <= (size_t)max_jint, "must not overflow unsigned int");
  assert(((jlong)max_elements_per_size_t * elem_size + base_offset_in_bytes(type)) / HeapWordSize <= (jlong)max_jint,
         "total array size in bytes must not overflow signed int: max_elements_per_size_t");
  return (int32_t)max_elements_per_size_t;
}
