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

#include "memory/allocation.hpp"
#include "memory/arena.hpp"
#include "utilities/growableArray.hpp"
#include "runtime/os.hpp"

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
  class I {
    friend IndexedFreeListAllocator<E, flag>;
    const int32_t _idx;

    I(int32_t idx) : _idx(idx) {}

  public:
    I(const I& other) : _idx(other._idx) {}

    I& operator=(const I& other) {;
      *this = other;
    }

    bool operator !=(I other) {
      return _idx != other._idx;
    }
    bool operator==(I other) {
      return _idx == other._idx;
    }

    bool is_nil() {
      return *this == IndexedFreeListAllocator<E, flag>::nil;
    }
  };

  static const I nil;

private:
  // A free list allocator element is either a link to the next free space
  // Or an actual element.
  union alignas(E) BackingElement {
    I link;
    char e[sizeof(E)];

    BackingElement() : link(nil) {
    }
  };

  GrowableArrayCHeap<BackingElement, flag> _backing_storage;
  I _free_start;

public:
  NONCOPYABLE(IndexedFreeListAllocator<E COMMA flag>);

  IndexedFreeListAllocator(int initial_capacity = 8)
    : _backing_storage(initial_capacity),
      _free_start(I(nil._idx)) {}

  template<typename... Args>
  I allocate(Args... args) {
    BackingElement* be;
    int i;
    if (_free_start != nil) {
      // Must point to already existing index
      be = &_backing_storage.at(_free_start._idx);
      i = _free_start._idx;
      _free_start = be->link;
    } else {
      // There are no free elements, allocate a new one.
      i = _backing_storage.append(BackingElement());
      be = _backing_storage.adr_at(i);
    }

    ::new (be) E(args...);
    return I(i);
  }

  void free(I i) {
    assert(!i.is_nil() || (i._idx > 0 && i._idx < _backing_storage.length()), "out of bounds free");

    BackingElement& be_freed = _backing_storage.at(i._idx);
    be_freed.link = _free_start;
    _free_start = i;
  }

  E& at(I i) {
    assert(!i.is_nil(), "null pointer dereference");
    assert(i._idx > 0 && i._idx < _backing_storage.length(), "out of bounds dereference");
    return reinterpret_cast<E&>(_backing_storage.at(i._idx).e);
  }

  const E& at(I i) const {
    assert(!i.is_nil(), "null pointer dereference");
    return reinterpret_cast<const E&>(_backing_storage.at(i._idx).e);
  }
};

template<typename E, MEMFLAGS flag>
const typename IndexedFreeListAllocator<E, flag>::I
    IndexedFreeListAllocator<E, flag>::nil(-1);

// A CHeap allocator
template<typename E, MEMFLAGS flag>
class CHeapAllocator {
public:
  struct I {
    E* e;
    bool operator !=(I other) {
      return e != other.e;
    }
    bool operator==(I other) {
      return e == other.e;
    }
  };
  static constexpr const I nil = {nullptr};

  template<typename... Args>
  I allocate(Args... args) {
    void* place = os::malloc(sizeof(E), flag);
    ::new (place) E(args...);
    return I{static_cast<E*>(place)};
  }

  void free(I i) {
    return os::free(i.e);
  }

  E& at(I i) {
    return *i.e;
  };

  const E& at(I i) const {
    return *i.e;
  };
};

// An Arena allocator
template<typename E, MEMFLAGS flag>
class ArenaAllocator {
  Arena _arena;
public:
  ArenaAllocator() : _arena(flag) {}

  struct I {
    E* e;
    bool operator !=(I other) {
      return e != other.e;
    }
    bool operator==(I other) {
      return e == other.e;
    }
  };
  static constexpr const I nil = {nullptr};

  template<typename... Args>
  I allocate(Args... args) {
    void* place = _arena.Amalloc(sizeof(E));
    ::new (place) E(args...);
    return I{static_cast<E*>(place)};
  }

  void free(I i) {
    _arena.Afree(i.e, sizeof(E));
  }

  E& at(I i) {
    return *i.e;
  };

  const E& at(I i) const {
    return *i.e;
  };
};

#endif // SHARE_NMT_INDEXEDFREELISTALLOCATOR_HPP
