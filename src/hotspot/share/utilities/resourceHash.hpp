/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_RESOURCEHASH_HPP
#define SHARE_UTILITIES_RESOURCEHASH_HPP

#include "memory/allocation.hpp"

template<
    typename TABLE_IMPL,
    typename K, typename V,
    unsigned (*HASH)  (K const&),
    bool     (*EQUALS)(K const&, K const&),
    ResourceObj::allocation_type ALLOC_TYPE,
    MEMFLAGS MEM_TYPE
    >
class ResourceHashtableBase : public ResourceObj {
 private:

  class Node : public ResourceObj {
   public:
    unsigned _hash;
    K _key;
    V _value;
    Node* _next;

    Node(unsigned hash, K const& key, V const& value) :
        _hash(hash), _key(key), _value(value), _next(NULL) {}

    // Create a node with a default-constructed value.
    Node(unsigned hash, K const& key) :
        _hash(hash), _key(key), _value(), _next(NULL) {}

  };

  int _number_of_entries;
  Node** _table;

  // Returns a pointer to where the node where the value would reside if
  // it's in the table.
  Node** lookup_node(unsigned hash, K const& key) {
    unsigned index = hash % size();
    Node** ptr = &_table[index];
    while (*ptr != NULL) {
      Node* node = *ptr;
      if (node->_hash == hash && EQUALS(key, node->_key)) {
        break;
      }
      ptr = &(node->_next);
    }
    return ptr;
  }

  Node const** lookup_node(unsigned hash, K const& key) const {
    return const_cast<Node const**>(
        const_cast<ResourceHashtableBase*>(this)->lookup_node(hash, key));
  }

  Node** alloc_table(unsigned size) {
    Node** table;
    if (ALLOC_TYPE == C_HEAP) {
      table = NEW_C_HEAP_ARRAY(Node*, size, MEM_TYPE);
    } else {
      table = NEW_RESOURCE_ARRAY(Node*, size);
    }
    memset(table, 0, size * sizeof(Node*));
    return table;
  }

 public:
  ResourceHashtableBase(unsigned size) : _number_of_entries(0) {
    // Don't call size() yet as the TABLE_IMPL constructor
    // hasn't been called yet.
    _table = alloc_table(size);
  }

  ~ResourceHashtableBase() {
    if (ALLOC_TYPE == C_HEAP) {
      Node* const* bucket = _table;
      const unsigned sz = size();
      while (bucket < &_table[sz]) {
        Node* node = *bucket;
        while (node != NULL) {
          Node* cur = node;
          node = node->_next;
          delete cur;
        }
        ++bucket;
      }
      FREE_C_HEAP_ARRAY(Node*, _table);
    }
  }

  unsigned size() const { return static_cast<const TABLE_IMPL*>(this)->size_impl(); }
  int number_of_entries() const { return _number_of_entries; }

  bool contains(K const& key) const {
    return get(key) != NULL;
  }

  V* get(K const& key) const {
    unsigned hv = HASH(key);
    Node const** ptr = lookup_node(hv, key);
    if (*ptr != NULL) {
      return const_cast<V*>(&(*ptr)->_value);
    } else {
      return NULL;
    }
  }

 /**
  * Inserts or replaces a value in the table.
  * @return: true:  if a new item is added
  *          false: if the item already existed and the value is updated
  */
  bool put(K const& key, V const& value) {
    unsigned hv = HASH(key);
    Node** ptr = lookup_node(hv, key);
    if (*ptr != NULL) {
      (*ptr)->_value = value;
      return false;
    } else {
      *ptr = new (ALLOC_TYPE, MEM_TYPE) Node(hv, key, value);
      _number_of_entries ++;
      return true;
    }
  }

  // Look up the key.
  // If an entry for the key exists, leave map unchanged and return a pointer to its value.
  // If no entry for the key exists, create a new entry from key and a default-created value
  //  and return a pointer to the value.
  // *p_created is true if entry was created, false if entry pre-existed.
  V* put_if_absent(K const& key, bool* p_created) {
    unsigned hv = HASH(key);
    Node** ptr = lookup_node(hv, key);
    if (*ptr == NULL) {
      *ptr = new (ALLOC_TYPE, MEM_TYPE) Node(hv, key);
      *p_created = true;
      _number_of_entries ++;
    } else {
      *p_created = false;
    }
    return &(*ptr)->_value;
  }

  // Look up the key.
  // If an entry for the key exists, leave map unchanged and return a pointer to its value.
  // If no entry for the key exists, create a new entry from key and value and return a
  //  pointer to the value.
  // *p_created is true if entry was created, false if entry pre-existed.
  V* put_if_absent(K const& key, V const& value, bool* p_created) {
    unsigned hv = HASH(key);
    Node** ptr = lookup_node(hv, key);
    if (*ptr == NULL) {
      *ptr = new (ALLOC_TYPE, MEM_TYPE) Node(hv, key, value);
      *p_created = true;
      _number_of_entries ++;
    } else {
      *p_created = false;
    }
    return &(*ptr)->_value;
  }


  bool remove(K const& key) {
    unsigned hv = HASH(key);
    Node** ptr = lookup_node(hv, key);

    Node* node = *ptr;
    if (node != NULL) {
      *ptr = node->_next;
      if (ALLOC_TYPE == C_HEAP) {
        delete node;
      }
      _number_of_entries --;
      return true;
    }
    return false;
  }

  // ITER contains bool do_entry(K const&, V const&), which will be
  // called for each entry in the table.  If do_entry() returns false,
  // the iteration is cancelled.
  template<class ITER>
  void iterate(ITER* iter) const {
    Node* const* bucket = _table;
    const unsigned sz = size();
    while (bucket < &_table[sz]) {
      Node* node = *bucket;
      while (node != NULL) {
        bool cont = iter->do_entry(node->_key, node->_value);
        if (!cont) { return; }
        node = node->_next;
      }
      ++bucket;
    }
  }

 protected:
  void resize(unsigned new_size) {
    Node** old_table = _table;
    _table = alloc_table(new_size);

    Node* const* bucket = old_table;
    const unsigned old_size = size();
    while (bucket < &old_table[old_size]) {
      Node* node = *bucket;
      while (node != NULL) {
        Node* next = node->_next;
        unsigned hash = HASH(node->_key);
        unsigned index = hash % new_size;

        node->_next = _table[index];
        _table[index] = node;

        node = next;
      }
      ++bucket;
    }

    FREE_C_HEAP_ARRAY(Node*, old_table);
  }
};

template<
    typename K, typename V,
    unsigned (*HASH)  (K const&)           = primitive_hash<K>,
    bool     (*EQUALS)(K const&, K const&) = primitive_equals<K>,
    unsigned SIZE = 256,
    ResourceObj::allocation_type ALLOC_TYPE = ResourceObj::RESOURCE_AREA,
    MEMFLAGS MEM_TYPE = mtInternal
    >
class ResourceHashtable : public ResourceHashtableBase<
    ResourceHashtable<K, V, HASH, EQUALS, SIZE, ALLOC_TYPE, MEM_TYPE>,
    K, V, HASH, EQUALS, ALLOC_TYPE, MEM_TYPE> {
public:
  ResourceHashtable() : ResourceHashtableBase<ResourceHashtable, K, V, HASH, EQUALS, ALLOC_TYPE, MEM_TYPE>(SIZE) {}
  constexpr unsigned size_impl() const { return SIZE; }
};

#endif // SHARE_UTILITIES_RESOURCEHASH_HPP
