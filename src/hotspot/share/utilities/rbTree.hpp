/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_RBTREE_HPP
#define SHARE_UTILITIES_RBTREE_HPP

#include "nmt/memTag.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include <type_traits>

// COMPARATOR must have a static function `cmp(a,b)` which returns:
//     - an int < 0 when a < b
//     - an int == 0 when a == b
//     - an int > 0 when a > b
// ALLOCATOR must check for oom and exit, as RBTree currently does not handle the
// allocation failing.
// Key needs to be of a type that is trivially destructible.
// The tree will call a value's destructor when its node is removed.
// Nodes are address stable and will not change during its lifetime.

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
class RBTree {
  friend class RBTreeTest;

private:
  ALLOCATOR _allocator;
  size_t _num_nodes;

public:
  class RBNode {
    friend RBTree;
    friend class RBTreeTest;

  private:
    uintptr_t _parent; // LSB encodes color information. 0 = RED, 1 = BLACK
    RBNode* _left;
    RBNode* _right;

    const K _key;
    V _value;

    DEBUG_ONLY(bool _visited);

  public:
    const K& key() const { return _key; }
    V& val() { return _value; }
    const V& val() const { return _value; }

  private:
    bool is_black() const { return (_parent & 0x1) != 0; }
    bool is_red() const { return (_parent & 0x1) == 0; }

    void set_black() { _parent = _parent | 0x1; }
    void set_red() { _parent = _parent & ~0x1; }

    RBNode* parent() const { return (RBNode*)(_parent & ~0x1); }
    void set_parent(RBNode* new_parent) {_parent = (_parent & 0x1) | ((uintptr_t)new_parent & ~0x1); }

    RBNode(const K& k, const V& v DEBUG_ONLY(COMMA bool visited))
        : _parent(0), _left(nullptr), _right(nullptr),
          _key(k), _value(v) DEBUG_ONLY(COMMA _visited(visited)) {}

    bool is_right_child() const {
      return parent() != nullptr && parent()->_right == this;
    }

    bool is_left_child() const {
      return parent() != nullptr && parent()->_left == this;
    }

    void replace_child(RBNode* old_child, RBNode* new_child);

    // This node down, right child up
    // Returns right child (now parent)
    RBNode* rotate_left();

    // This node down, left child up
    // Returns left child (now parent)
    RBNode* rotate_right();

    RBNode* prev();

    RBNode* next();

  #ifdef ASSERT
    void verify(size_t& num_nodes, size_t& black_nodes_until_leaf,
                size_t& shortest_leaf_path, size_t& longest_leaf_path,
                size_t& tree_depth, bool expect_visited);
#endif // ASSERT
  };

private:
  RBNode* _root;
  DEBUG_ONLY(bool _expected_visited);

  RBNode* allocate_node(const K& k, const V& v) {
    void* node_place = _allocator.allocate(sizeof(RBNode));
    assert(node_place != nullptr, "rb-tree allocator must exit on failure");
    _num_nodes++;
    return new (node_place) RBNode(k, v DEBUG_ONLY(COMMA _expected_visited));
  }

  void free_node(RBNode* node) {
    node->_value.~V();
    _allocator.free(node);
    _num_nodes--;
  }

  // True if node is black (nil nodes count as black)
  static inline bool is_black(const RBNode* node) {
    return node == nullptr || node->is_black();
  }

  static inline bool is_red(const RBNode* node) {
    return node != nullptr && node->is_red();
  }


  // If the node with key k already exist, the value is updated instead.
  RBNode* insert_node(const K& k, const V& v);

  void fix_insert_violations(RBNode* node);

  void remove_black_leaf(RBNode* node);

  // Assumption: node has at most one child. Two children is handled in `remove()`
  void remove_from_tree(RBNode* node);

public:
  NONCOPYABLE(RBTree);

  RBTree() : _allocator(), _num_nodes(0), _root(nullptr) DEBUG_ONLY(COMMA _expected_visited(false)) {
    static_assert(std::is_trivially_destructible<K>::value, "key type must be trivially destructable");
  }
  ~RBTree() { this->remove_all(); }

  size_t size() { return _num_nodes; }

  // Inserts a node with the given k/v into the tree,
  // if the key already exist, the value is updated instead.
  void upsert(const K& k, const V& v) {
    RBNode* node = insert_node(k, v);
    fix_insert_violations(node);
  }

  // Removes the node with the given key from the tree if it exists.
  // Returns true if the node was successfully removed, false otherwise.
  bool remove(const K& k) {
    RBNode* node = find_node(k);
    if (node == nullptr){
      return false;
    }
    remove(node);
    return true;
  }

  // Removes the given node from the tree. node must be a valid node
  void remove(RBNode* node);

  // Removes all existing nodes from the tree.
  void remove_all() {
    RBNode* to_delete[64];
    int stack_idx = 0;
    to_delete[stack_idx++] = _root;

    while (stack_idx > 0) {
      RBNode* head = to_delete[--stack_idx];
      if (head == nullptr) continue;
      to_delete[stack_idx++] = head->_left;
      to_delete[stack_idx++] = head->_right;
      free_node(head);
    }
    _num_nodes = 0;
    _root = nullptr;
  }

  // Alters behaviour of closest_(leq/gt) functions to include/exclude the exact value
  enum BoundMode : uint8_t { EXCLUSIVE, INCLUSIVE };

  // Finds the node with the closest key <= the given key
  // Change mode to EXCLUSIVE to not include node matching key
  RBNode* closest_leq(const K& key, BoundMode mode = INCLUSIVE) {
    RBNode* candidate = nullptr;
    RBNode* pos = _root;
    while (pos != nullptr) {
      const int cmp_r = COMPARATOR::cmp(pos->key(), key);
      if (mode == INCLUSIVE && cmp_r == 0) { // Exact match
        candidate = pos;
        break; // Can't become better than that.
      }
      if (cmp_r < 0) {
        // Found a match, try to find a better one.
        candidate = pos;
        pos = pos->_right;
      } else {
        pos = pos->_left;
      }
    }
    return candidate;
  }

  // Finds the node with the closest key > the given key
  // Change mode to INCLUSIVE to include node matching key
  RBNode* closest_gt(const K& key, BoundMode mode = EXCLUSIVE) {
    RBNode* candidate = nullptr;
    RBNode* pos = _root;
    while (pos != nullptr) {
      const int cmp_r = COMPARATOR::cmp(pos->key(), key);
      if (mode == INCLUSIVE && cmp_r == 0) { // Exact match
        candidate = pos;
        break; // Can't become better than that.
      }
      if (cmp_r > 0) {
        // Found a match, try to find a better one.
        candidate = pos;
        pos = pos->_left;
      } else {
        pos = pos->_right;
      }
    }
    return candidate;
  }

  const RBNode* closest_leq(const K& k, BoundMode mode = INCLUSIVE) const {
    return const_cast<RBTree<K, V, COMPARATOR, ALLOCATOR>*>(this)->closest_leq(k, mode);
  }

  const RBNode* closest_gt(const K& k, BoundMode mode = EXCLUSIVE) const {
    return const_cast<RBTree<K, V, COMPARATOR, ALLOCATOR>*>(this)->closest_gt(k, mode);
  }

  // Finds the node associated with the key
  RBNode* find_node(const K& k);

  const RBNode* find_node(const K& k) const {
    return const_cast<RBTree<K, V, COMPARATOR, ALLOCATOR>*>(this)->find_node(k);
  }

  // Finds the value associated with the key
  V* find(const K& key) {
    RBNode* node = find_node(key);
    return node == nullptr ? nullptr : &node->val();
  }

  const V* find(const K& key) const {
    const RBNode* node = find_node(key);
    return node == nullptr ? nullptr : &node->val();
  }

  // Visit all RBNodes in ascending order, calling f on each node.
  template <typename F>
  void visit_in_order(F f) const;

  // Visit all RBNodes in ascending order whose keys are in range [from, to), calling f on each node.
  template <typename F>
  void visit_range_in_order(const K& from, const K& to, F f);

#ifdef ASSERT
  // Verifies that the tree is correct and holds rb-properties
  void verify_self();
#endif // ASSERT

};

template <MemTag mem_tag>
class RBTreeCHeapAllocator {
public:
  void* allocate(size_t sz) {
    void* allocation = os::malloc(sz, mem_tag);
    if (allocation == nullptr) {
      vm_exit_out_of_memory(sz, OOM_MALLOC_ERROR,
                            "red-black tree failed allocation");
    }
    return allocation;
  }

  void free(void* ptr) { os::free(ptr); }
};

template <typename K, typename V, typename COMPARATOR, MemTag mem_tag>
using RBTreeCHeap = RBTree<K, V, COMPARATOR, RBTreeCHeapAllocator<mem_tag>>;

#endif // SHARE_UTILITIES_RBTREE_HPP
