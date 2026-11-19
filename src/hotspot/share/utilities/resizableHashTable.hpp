/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_RESIZABLEHASHTABLE_HPP
#define SHARE_UTILITIES_RESIZABLEHASHTABLE_HPP

#include "utilities/hashTable.hpp"

template<
    typename K, typename V,
    AnyObj::allocation_type ALLOC_TYPE,
    MemTag MEM_TAG>
class ResizeableHashTableStorage : public AnyObj {
  using Node = HashTableNode<K, V>;

protected:
  unsigned _table_size;
  Node** _table;

  ResizeableHashTableStorage(unsigned table_size) {
    _table_size = table_size;
    _table = alloc_table(table_size);
  }

  ~ResizeableHashTableStorage() {
    if (ALLOC_TYPE == C_HEAP) {
      FREE_C_HEAP_ARRAY(_table);
    }
  }

  Node** alloc_table(unsigned table_size) {
    Node** table;
    if (ALLOC_TYPE == C_HEAP) {
      table = NEW_C_HEAP_ARRAY(Node*, table_size, MEM_TAG);
    } else {
      table = NEW_RESOURCE_ARRAY(Node*, table_size);
    }
    memset(table, 0, table_size * sizeof(Node*));
    return table;
  }

  unsigned table_size() const {
    return _table_size;
  }

  Node** table() const {
    return _table;
  }
};

template<
    typename K, typename V,
    AnyObj::allocation_type ALLOC_TYPE = AnyObj::RESOURCE_AREA,
    MemTag MEM_TAG = mtInternal,
    unsigned (*HASH)  (K const&)           = primitive_hash<K>,
    bool     (*EQUALS)(K const&, K const&) = primitive_equals<K>,
    int LOAD_FACTOR = 8,
    bool USE_LARGE_TABLE_SIZE = false
    >
class ResizeableHashTable : public HashTableBase<
    ResizeableHashTableStorage<K, V, ALLOC_TYPE, MEM_TAG>,
    K, V, ALLOC_TYPE, MEM_TAG, HASH, EQUALS> {
  unsigned _max_size;

  using BASE = HashTableBase<ResizeableHashTableStorage<K, V, ALLOC_TYPE, MEM_TAG>,
                                     K, V, ALLOC_TYPE, MEM_TAG, HASH, EQUALS>;
  using Node = HashTableNode<K, V>;
  NONCOPYABLE(ResizeableHashTable);

  // Calculate next "good" hashtable size based on requested count
  int calculate_resize() const {
    const int resize_factor = 2;     // by how much we will resize using current number of entries

    // possible hashmap sizes - odd primes that roughly double in size.
    // To avoid excessive resizing the odd primes from 4801-76831 and
    // 76831-307261 have been removed.
    const int large_table_sizes[] =  { 107, 1009, 2017, 4049, 5051, 10103, 20201,
                                       40423, 76831, 307261, 614563, 1228891, 2457733,
                                       4915219, 9830479, 19660831, 39321619, 78643219 };
    const int large_array_size = sizeof(large_table_sizes)/sizeof(int);

    int requested = resize_factor * BASE::number_of_entries();
    int start_at = USE_LARGE_TABLE_SIZE ? 8 : 0;
    int newsize;
    for (int i = start_at; i < large_array_size; i++) {
      newsize = large_table_sizes[i];
      if (newsize >= requested) {
        return newsize;
      }
    }
    return requested; // greater than a size in the table
  }

  bool maybe_grow() {
    unsigned old_size = BASE::_table_size;
    if (old_size >= _max_size) {
      return false;
    }
    if (BASE::number_of_entries() / int(old_size) > LOAD_FACTOR) {
      unsigned new_size = MIN2<unsigned>(calculate_resize(), _max_size);
      resize(new_size);
      return true;
    } else {
      return false;
    }
  }

public:
  ResizeableHashTable(unsigned size, unsigned max_size)
  : BASE(size), _max_size(max_size) {
    assert(size <= 0x3fffffff && max_size <= 0x3fffffff, "avoid overflow in resize");
  }

  void resize(unsigned new_size) {
    Node** old_table = BASE::_table;
    Node** new_table = BASE::alloc_table(new_size);

    Node* const* bucket = old_table;
    while (bucket < &old_table[BASE::_table_size]) {
      Node* node = *bucket;
      while (node != nullptr) {
        Node* next = node->_next;
        unsigned hash = node->_hash;
        unsigned index = hash % new_size;

        node->_next = new_table[index];
        new_table[index] = node;

        node = next;
      }
      ++bucket;
    }

    if (ALLOC_TYPE == AnyObj::C_HEAP) {
      FREE_C_HEAP_ARRAY(old_table);
    }
    BASE::_table = new_table;
    BASE::_table_size = new_size;
  }

  bool put(K const& key, V const& value) {
    bool created = BASE::put(key, value);
    if (created) {
      maybe_grow();
    }
    return created;
  }

  bool put_when_absent(K const& key, V const& value) {
    bool created = BASE::put_when_absent(key, value);
    if (created) {
      maybe_grow();
    }
    return created;
  }

  V* put_if_absent(K const& key, bool* p_created) {
    V* node = BASE::put_if_absent(key, p_created);
    if (*p_created) {
      maybe_grow();
    }
    return node;
  }

  V* put_if_absent(K const& key, V const& value, bool* p_created) {
    V* node = BASE::put_if_absent(key, value, p_created);
    if (*p_created) {
      maybe_grow();
    }
    return node;
  }
};

#endif // SHARE_UTILITIES_RESIZABLEHASHTABLE_HPP
