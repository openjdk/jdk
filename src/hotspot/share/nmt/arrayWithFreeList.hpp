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

#ifndef SHARE_NMT_ARRAYWITHFREELIST_HPP
#define SHARE_NMT_ARRAYWITHFREELIST_HPP

#include "utilities/growableArray.hpp"
#include <type_traits>

// A flat array of elements E, backed by C-heap, growing on-demand. It allows for
// returning arbitrary elements and keeps them in a freelist. Elements can be uniquely
// identified via array index.
template<typename E, MEMFLAGS flag>
class ArrayWithFreeList {

  // An E must be trivially copyable and destructible, but it may be constructed
  // however it likes.
  constexpr void static_assert_E_satisfies_type_requirements() const {
    static_assert(std::is_trivially_copyable<E>::value && std::is_trivially_destructible<E>::value, "must be");
  }

public:
  using I = int32_t;
  static constexpr const I nil = -1;

private:
  // A free list allocator element is either a link to the next free space
  // or an actual element.
  union BackingElement {
    I link;
    E e;
  };

  GrowableArrayCHeap<BackingElement, flag> _backing_storage;
  I _free_start;

  bool is_in_bounds(I i) {
    return i >= 0 && i < _backing_storage.length();
  }

public:
  NONCOPYABLE(ArrayWithFreeList);

  ArrayWithFreeList(int initial_capacity = 8)
    : _backing_storage(initial_capacity),
    _free_start(nil) {}

  template<typename... Args>
  I allocate(Args... args) {
    static_assert_E_satisfies_type_requirements();
    BackingElement* be;
    I i;
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

    ::new (be) E{args...};
    return i;
  }

  void deallocate(I i) {
    static_assert_E_satisfies_type_requirements();
    assert(i == nil || is_in_bounds(i), "out of bounds free");
    if (i == nil) return;
    BackingElement& be_freed = _backing_storage.at(i);
    be_freed.link = _free_start;
    _free_start = i;
  }

  E& at(I i) {
    static_assert_E_satisfies_type_requirements();
    assert(i != nil, "null pointer dereference");
    assert(is_in_bounds(i), "out of bounds dereference");
    return _backing_storage.at(i).e;
  }
};

#endif // SHARE_NMT_ARRAYWITHFREELIST_HPP
