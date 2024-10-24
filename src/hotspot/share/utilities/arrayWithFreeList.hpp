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

#include "nmt/memTag.hpp"
#include <cstdint>
#include <limits>
#include <type_traits>
#include "runtime/os.hpp"

// A flat array of elements E, backed by C-heap, growing on-demand. It allows for
// returning arbitrary elements and keeps them in a freelist. Elements can be uniquely
// identified via array index.
template<typename E, MemTag MT, typename II = int32_t>
class ArrayWithFreeList {
  constexpr void static_assert_E_satisfies_type_requirements(bool fixed) const {
    static_assert(std::numeric_limits<II>::is_exact, "must be");
    static_assert(std::numeric_limits<II>::max() <= std::numeric_limits<uint64_t>::max(), "cannot have index larger than uint64_t");
    if (fixed) {
      static_assert(std::is_trivially_destructible<E>::value, "must be");
    } else {
      static_assert(std::is_trivially_copyable<E>::value && std::is_trivially_destructible<E>::value, "must be");
    }
  }

public:
  // Export the I so it's easily available for consumption by users
  using I = II;
  static constexpr const I nil = std::numeric_limits<I>::max();
  static constexpr const I max = nil - 1;

  // A free list allocator element is either a link to the next free space
  // or an actual element.
  union BackingElement {
    I link;
    E e;
  };

private:
  // A minimal resizable array with customizable len/cap properties.
  class resizable_array {
    bool _fixed_size;
    I _len;
    I _cap;
    BackingElement* _data;

    bool grow() {
      if (_cap == std::numeric_limits<I>::max() - 1) {
        // Already at max capacity.
        return false;
      }

      // Widen the capacity temporarily.
      uint64_t widened_cap = static_cast<uint64_t>(_cap);
      if (std::numeric_limits<uint64_t>::max() - widened_cap < widened_cap) {
        // Overflow of uint64_t in case of resize, we fail.
        return  false;
      }
      // Safe to double the widened_cap
      widened_cap *= 2;
      // If I has max size (2**X) - 1, is cap at 2**(X-1)?
      if (std::numeric_limits<I>::max() - _cap == (_cap - 1)) {
        // Reduce widened_cap
        widened_cap -= 1;
      }

      I next_cap = static_cast<I>(widened_cap);
      void* next_array = os::realloc(_data, next_cap * sizeof(BackingElement), MT);
      if (next_array == nullptr) {
        return false;
      }
      _data = static_cast<BackingElement*>(next_array);
      _cap = next_cap;
      return true;
    }

  public:
    resizable_array(I initial_cap)
    : _fixed_size(false),
      _len(0),
      _cap(initial_cap),
      _data(static_cast<BackingElement*>(os::malloc(initial_cap * sizeof(BackingElement), MT))) {
    }

    resizable_array(BackingElement* data, II capacity)
    : _fixed_size(true),
      _len(0),
      _cap(capacity),
      _data(data) {}

    ~resizable_array() {
      if (!_fixed_size) {
        os::free(_data);
      }
    }

    I length() {
      return _len;
    }

    BackingElement& at(I i) {
      assert(i < _len, "oob");
      return _data[i];
    }

    BackingElement* adr_at(I i) {
      return &at(i);
    }

    I append() {
      if (_len == _cap) {
        if (_fixed_size) return nil;
        if (!grow()) {
          return nil;
        }
      }
      I idx = _len++;
      return idx;
    }

    void remove_last() {
      I idx = _len - 1;
      --_len;
    }
  };

  resizable_array _backing_storage;
  I _free_start;

  bool is_in_bounds(I i) {
    return i >= 0 && i < _backing_storage.length();
  }

public:
  NONCOPYABLE(ArrayWithFreeList);

  ArrayWithFreeList(int initial_capacity = 8)
    : _backing_storage(initial_capacity),
    _free_start(nil) {
    static_assert_E_satisfies_type_requirements(false);
  }

  ArrayWithFreeList(BackingElement* data, II capacity)
  : _backing_storage(data, capacity), _free_start(nil) {
    static_assert_E_satisfies_type_requirements(true);
  }

  template<typename... Args>
  I allocate(Args... args) {
    BackingElement* be;
    I i;
    if (_free_start != nil) {
      // Must point to already existing index
      be = _backing_storage.adr_at(_free_start);
      i = _free_start;
      _free_start = be->link;
    } else {
      // There are no free elements, allocate a new one.
      i = _backing_storage.append();
      if (i == nil) return i;
      be = _backing_storage.adr_at(i);
    }

    ::new (be) E{args...};
    return i;
  }

  void deallocate(I i) {
    assert(i == nil || is_in_bounds(i), "out of bounds free");
    if (i == nil) return;
    if (i == _backing_storage.length()) {
      _backing_storage.remove_last();
    } else {
      BackingElement& be_freed = _backing_storage.at(i);
      be_freed.link = _free_start;
      _free_start = i;
    }
  }

  E& at(I i) {
    assert(i != nil, "null pointer dereference");
    assert(is_in_bounds(i), "out of bounds dereference");
    return _backing_storage.at(i).e;
  }
};

#endif // SHARE_NMT_ARRAYWITHFREELIST_HPP
