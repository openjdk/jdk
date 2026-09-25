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

#ifndef SHARE_UTILITIES_UNORDEREDMAP_HPP
#define SHARE_UTILITIES_UNORDEREDMAP_HPP

#include "memory/allocation.hpp"
#include "memory/arena.hpp"
#include "nmt/memTag.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/swissTable.hpp"

// A FlatHashTable is an open-addressed hash table that puts all of its entries in a flat array.
// This means that pointers to entries are not stable and may be invalidated when the table grows
// or shrinks. This aims to be an easy-to-use hash table implementation that is performant in most
// of the cases. In contrast to ResizeableHashTable, this table may grow automatically upon
// insertion instead of users having to manually grow it.
template <class Key, class T, auto HASH, auto KEY_EQUAL, class Allocator>
class FlatHashTableBase {
private:
  class Entry {
  public:
    uint64_t _hash;
    Key _key;
    T _value;

    Entry(uint64_t h, const Key& key, const T& value) : _hash(h), _key(key), _value(value) {}

    uint64_t hash() const {
      return _hash;
    }
  };

  static bool key_hash_match(const Key& key, uint64_t h, const Entry& entry) {
    if constexpr (std::is_integral_v<Key>) {
      return KEY_EQUAL(key, entry._key);
    } else {
      return h == entry._hash && KEY_EQUAL(key, entry._key);
    }
  }

  SwissTableImpl<Entry, Allocator> _impl;

  using ImplType = decltype(_impl);

  // User-provided hash functions often have terrible avalanche. For example, most of the time, the
  // provided hash function for Key = int would be the identity function. In addition, the
  // algorithm requires good avalanche. So, we hash the result again. This function is the same as
  // j.u.SplittableRandom::mix64.
  uint64_t internal_hash(const Key& key) const {
    uint64_t h = HASH(key);
    h = (h ^ (h >> 30)) * 0xbf58476d1ce4e5b9;
    h = (h ^ (h >> 27)) * 0x94d049bb133111eb;
    return h ^ (h >> 31);
  }

public:
  FlatHashTableBase(Allocator alloc) : _impl(alloc) {}

  size_t size() const {
    return _impl.size();
  }

  const T* get(const Key& key) const {
    uint64_t h = internal_hash(key);
    auto find_res = _impl.template find<Key, key_hash_match>(h, key);
    if (find_res.result() == ImplType::FindResult::NOT_EXIST) {
      return nullptr;
    }

    assert(find_res.entry() != nullptr, "inconsistent");
    return &find_res.entry()->_value;
  }

  bool put(const Key& key, const T& value) {
    uint64_t h = internal_hash(key);
    auto emplace_entry = [&](bool exist, Entry* n) {
      ::new(n) Entry(h, key, value);
    };
    auto emplace_res = _impl.template emplace<Key, key_hash_match>(h, key, emplace_entry);
    if (emplace_res.result() == ImplType::EmplaceResult::FAIL_TO_ALLOCATE) {
      vm_exit_out_of_memory(0, OOM_MALLOC_ERROR, "Fail to put a new entry into the UnstableUnorderedMap");
    }
    return emplace_res.result() == ImplType::EmplaceResult::NOT_EXIST;
  }

  bool put_if_absent(const Key& key, const T& value) {
    uint64_t h = internal_hash(key);
    auto emplace_entry = [&](bool exist, Entry* n) {
      if (!exist) {
        ::new(n) Entry(h, key, value);
      }
    };
    auto emplace_res = _impl.template emplace<Key, key_hash_match>(h, key, emplace_entry);
    if (emplace_res.result() == ImplType::EmplaceResult::FAIL_TO_ALLOCATE) {
      vm_exit_out_of_memory(0, OOM_MALLOC_ERROR, "Fail to put a new entry into the UnstableUnorderedMap");
    }
    return emplace_res.result() == ImplType::EmplaceResult::NOT_EXIST;
  }

  bool remove(const Key& key) {
    uint64_t h = internal_hash(key);
    auto extract_entry = [](Entry* n) {};
    auto erase_res = _impl.template erase<Key, key_hash_match>(h, key, extract_entry);
    return erase_res.result() == ImplType::EraseResult::ERASED;
  }
};

template <class Key, class T, auto HASH = primitive_hash<Key>, auto KEY_EQUAL = primitive_equals<Key>>
class FlatHashTableArena : public AnyObj {
private:
  class Allocator {
  private:
    Arena* _arena;

  public:
    Allocator(Arena* arena) : _arena(arena) {}

    void* allocate(size_t size) {
      return _arena->Amalloc(size);
    }

    void deallocate(void* ptr) {}
  };

  FlatHashTableBase<Key, T, HASH, KEY_EQUAL, Allocator> _impl;

  NONCOPYABLE(FlatHashTableArena);

public:
  FlatHashTableArena(Arena* arena) : _impl(Allocator(arena)) {}

  size_t size() const {
    return _impl.size();
  }

  const T* get(const Key& key) const {
    return _impl.get(key);
  }

  T* get(const Key& key) {
    return const_cast<T*>(const_cast<const std::remove_pointer_t<decltype(this)>*>(this)->get(key));
  }

  bool put(const Key& key, const T& value) {
    return _impl.put(key, value);
  }

  bool put_if_absent(const Key& key, const T& value) {
    return _impl.put_if_absent(key, value);
  }

  bool remove(const Key& key) {
    return _impl.remove(key);
  }
};

template <class Key, class T, MemTag mem_tag, auto HASH = primitive_hash<Key>, auto KEY_EQUAL = primitive_equals<Key>>
class FlatHashTableCHeap {
private:
  class Allocator {
  public:
    void* allocate(size_t size) {
      return NEW_C_HEAP_ARRAY(char*, size, mem_tag);
    }

    void deallocate(void* ptr) {
      FREE_C_HEAP_ARRAY(ptr);
    }
  };

  FlatHashTableBase<Key, T, HASH, KEY_EQUAL, Allocator> _impl;

  NONCOPYABLE(FlatHashTableCHeap);

public:
  FlatHashTableCHeap() : _impl(Allocator()) {}

  size_t size() const {
    return _impl.size();
  }

  const T* get(const Key& key) const {
    return _impl.get(key);
  }

  T* get(const Key& key) {
    return const_cast<T*>(const_cast<const std::remove_pointer_t<decltype(this)>*>(this)->get(key));
  }

  bool put(const Key& key, const T& value) {
    return _impl.put(key, value);
  }

  bool put_if_absent(const Key& key, const T& value) {
    return _impl.put_if_absent(key, value);
  }

  bool remove(const Key& key) {
    return _impl.remove(key);
  }
};

#endif // SHARE_UTILITIES_UNORDEREDMAP_HPP
