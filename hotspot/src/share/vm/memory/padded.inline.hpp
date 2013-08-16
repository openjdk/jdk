/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.inline.hpp"
#include "memory/padded.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// Creates an aligned padded array.
// The memory can't be deleted since the raw memory chunk is not returned.
template <class T, MEMFLAGS flags, size_t alignment>
PaddedEnd<T>* PaddedArray<T, flags, alignment>::create_unfreeable(uint length) {
  // Check that the PaddedEnd class works as intended.
  STATIC_ASSERT(is_size_aligned_(sizeof(PaddedEnd<T>), alignment));

  // Allocate a chunk of memory large enough to allow for some alignment.
  void* chunk = AllocateHeap(length * sizeof(PaddedEnd<T, alignment>) + alignment, flags);

  // Make the initial alignment.
  PaddedEnd<T>* aligned_padded_array = (PaddedEnd<T>*)align_pointer_up(chunk, alignment);

  // Call the default constructor for each element.
  for (uint i = 0; i < length; i++) {
    ::new (&aligned_padded_array[i]) T();
  }

  return aligned_padded_array;
}
