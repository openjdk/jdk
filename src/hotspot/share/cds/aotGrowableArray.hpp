/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_AOT_AOTGROWABLEARRAY_HPP
#define SHARE_AOT_AOTGROWABLEARRAY_HPP

#include <utilities/growableArray.hpp>

class AOTGrowableArrayHelper {
public:
  static void deallocate(void* mem);
};

template <typename E>
class AOTGrowableArray : public GrowableArrayWithAllocator<E, AOTGrowableArray<E>> {
  friend class VMStructs;
  friend class GrowableArrayWithAllocator<E, AOTGrowableArray>;

  static E* allocate(int max, MemTag mem_tag) {
    return (E*)GrowableArrayCHeapAllocator::allocate(max, sizeof(E), mem_tag);
  }

  E* allocate() {
    return allocate(this->_capacity, mtClass);
  }

  void deallocate(E* mem) {
    AOTGrowableArrayHelper::deallocate(mem);
  }

public:
  AOTGrowableArray(int initial_capacity, MemTag mem_tag) :
      GrowableArrayWithAllocator<E, AOTGrowableArray>(
          allocate(initial_capacity, mem_tag),
          initial_capacity) {}

  AOTGrowableArray() : AOTGrowableArray(0, mtClassShared) {}

  // methods required by MetaspaceClosure
  void metaspace_pointers_do(MetaspaceClosure* it);
  int size_in_heapwords() const { return (int)heap_word_size(sizeof(*this)); }
  MetaspaceObj::Type type() const { return MetaspaceObj::GrowableArrayType; }
  static bool is_read_only_by_default() { return false; }
};

#endif // SHARE_AOT_AOTGROWABLEARRAY_HPP
