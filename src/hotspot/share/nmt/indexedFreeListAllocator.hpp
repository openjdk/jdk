/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

 */

#include "utilities/growableArray.hpp"

#ifndef SHARE_NMT_INDEXEDFREELISTALLOCATOR_HPP
#define SHARE_NMT_INDEXEDFREELISTALLOCATOR_HPP

// A free list, growth only, allocator for a specific type E.
// The allocator returns 'pointers' of 4-bytes in size, allowing for
// memory savings if a pointer-heavy self-referential structure is used.
// It is "indexed" as a reference is base + index * sizeof(E).
// It never returns any memory to the system.
template<typename E, MEMFLAGS flag>
class IndexedFreeListAllocator {
public:
  using I = int32_t;
  static constexpr const I nil = -1;

private:
  // A free list allocator element is either a link to the next free space
  // Or an actual element.
  union alignas(E) BackingElement {
    I link;
    char e[sizeof(E)];
  };

  GrowableArrayCHeap<BackingElement, flag> _backing_storage;
  I _free_start;

public:
  NONCOPYABLE(IndexedFreeListAllocator<E COMMA flag>);

  IndexedFreeListAllocator(int initial_capacity = 8)
    : _backing_storage(initial_capacity),
    _free_start(nil) {}

  template<typename... Args>
  I allocate(Args... args) {
    BackingElement* be;
    int i;
    if (_free_start != nil) {
      // Must point to already existing index
      be = &_backing_storage.at(_free_start);
      i = _free_start;
      _free_start = be->link;
    } else {
      // There are no free elements, allocate a new one.
      i = _backing_storage.append(BackingElement());
      be = _backing_storage.adr_at(i);
    }

    ::new (be) E(args...);
    return I{i};
  }

  void free(I i) {
    assert(i != nil || (i > 0 && i < _backing_storage.length()), "out of bounds free");
    if (i != nil) return;
    BackingElement& be_freed = _backing_storage.at(i);
    be_freed.link = _free_start;
    _free_start = i;
  }

  E& at(I i) {
    assert(i != nil, "null pointer dereference");
    assert(i > 0 && i < _backing_storage.length(), "out of bounds dereference");
    return reinterpret_cast<E&>(_backing_storage.at(i).e);
  }

  const E& at(I i) const {
    assert(i != nil, "null pointer dereference");
    return reinterpret_cast<const E&>(_backing_storage.at(i).e);
  }
};


#endif // SHARE_NMT_INDEXEDFREELISTALLOCATOR_HPP
