/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "libadt/vectset.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/arena.hpp"

VectorSet::VectorSet(Arena *arena) {
  _set_arena = arena;
  size = 2;                     // Small initial size
  data = (uint32_t *)_set_arena->Amalloc(size*sizeof(uint32_t));
  data[0] = 0;                  // No elements
  data[1] = 0;
}

// Expand the existing set to a bigger size
void VectorSet::grow(uint newsize) {
  newsize = (newsize+31) >> 5;
  uint x = size;
  while (x < newsize) {
    x <<= 1;
  }
  data = (uint32_t *)_set_arena->Arealloc(data, size*sizeof(uint32_t), x*sizeof(uint32_t));
  memset((char*)(data + size), 0, (x - size) * sizeof(uint32_t));
  size = x;
}

// Insert a member into an existing Set.
void VectorSet::insert(uint elem) {
  uint word = elem >> 5;
  uint32_t mask = 1L << (elem & 31);
  if (word >= size) {
    grow(elem + 1);
  }
  data[word] |= mask;
}

// Clear a set
void VectorSet::clear() {
  if( size > 100 ) {            // Reclaim storage only if huge
    FREE_RESOURCE_ARRAY(uint32_t,data,size);
    size = 2;                   // Small initial size
    data = NEW_RESOURCE_ARRAY(uint32_t,size);
  }
  memset(data, 0, size*sizeof(uint32_t));
}

// Return true if the set is empty
bool VectorSet::is_empty() const {
  for (uint32_t i = 0; i < size; i++) {
    if (data[i] != 0) {
      return false;
    }
  }
  return true;
}

int VectorSet::hash() const {
  uint32_t _xor = 0;
  uint lim = ((size < 4) ? size : 4);
  for (uint i = 0; i < lim; i++) {
    _xor ^= data[i];
  }
  return (int)_xor;
}
