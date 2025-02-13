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

class outputStream;

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
  typedef RBTree<K, V, COMPARATOR, ALLOCATOR> TreeType;
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

    DEBUG_ONLY(mutable bool _visited);

  public:
    const K& key() const { return _key; }
    V& val() { return _value; }
    const V& val() const { return _value; }
    void set_val(const V& v) { _value = v; }

  private:
    bool is_black() const { return (_parent & 0x1) != 0; }
    bool is_red() const { return (_parent & 0x1) == 0; }

    void set_black() { _parent |= 0x1; }
    void set_red() { _parent &= ~0x1; }

    RBNode* parent() const { return (RBNode*)(_parent & ~0x1); }
    void set_parent(RBNode* new_parent) { _parent = (_parent & 0x1) | (uintptr_t)new_parent; }

    RBNode(const K& key, const V& val DEBUG_ONLY(COMMA bool visited))
        : _parent(0), _left(nullptr), _right(nullptr),
          _key(key), _value(val) DEBUG_ONLY(COMMA _visited(visited)) {}

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

    const RBNode* prev() const;

    const RBNode* next() const;

  #ifdef ASSERT
    void verify(size_t& num_nodes, size_t& black_nodes_until_leaf,
                size_t& shortest_leaf_path, size_t& longest_leaf_path,
                size_t& tree_depth, bool expect_visited) const;
  #endif // ASSERT
  }; // End: RBNode

  typedef TreeType::RBNode NodeType;

private:
  RBNode* _root;
  DEBUG_ONLY(mutable bool _expected_visited);

  RBNode* allocate_node(const K& key, const V& val) {
    void* node_place = _allocator.allocate(sizeof(RBNode));
    assert(node_place != nullptr, "rb-tree allocator must exit on failure");
    _num_nodes++;
    return new (node_place) RBNode(key, val DEBUG_ONLY(COMMA _expected_visited));
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
  RBNode* insert_node(const K& key, const V& val);

  void fix_insert_violations(RBNode* node);

  void remove_black_leaf(RBNode* node);

  // Assumption: node has at most one child. Two children is handled in `remove()`
  void remove_from_tree(RBNode* node);

  void print_node_on(outputStream* st, int depth, const NodeType* n) const;

public:
  NONCOPYABLE(RBTree);

  RBTree() : _allocator(), _num_nodes(0), _root(nullptr) DEBUG_ONLY(COMMA _expected_visited(false)) {
    static_assert(std::is_trivially_destructible<K>::value, "key type must be trivially destructable");
  }
  ~RBTree() { this->remove_all(); }

  size_t size() const { return _num_nodes; }

  // Inserts a node with the given k/v into the tree,
  // if the key already exist, the value is updated instead.
  void upsert(const K& key, const V& val) {
    RBNode* node = insert_node(key, val);
    fix_insert_violations(node);
  }

  // Removes the node with the given key from the tree if it exists.
  // Returns true if the node was successfully removed, false otherwise.
  bool remove(const K& key) {
    RBNode* node = find_node(key);
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

  // Finds the node with the closest key <= the given key
  const RBNode* closest_leq(const K& key) const {
    RBNode* candidate = nullptr;
    RBNode* pos = _root;
    while (pos != nullptr) {
      const int cmp_r = COMPARATOR::cmp(pos->key(), key);
      if (cmp_r == 0) { // Exact match
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
  const RBNode* closest_gt(const K& key) const {
    RBNode* candidate = nullptr;
    RBNode* pos = _root;
    while (pos != nullptr) {
      const int cmp_r = COMPARATOR::cmp(pos->key(), key);
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

  // Finds the node with the closest key >= the given key
  const RBNode* closest_geq(const K& key) const {
    RBNode* candidate = nullptr;
    RBNode* pos = _root;
    while (pos != nullptr) {
      const int cmp_r = COMPARATOR::cmp(pos->key(), key);
      if (cmp_r == 0) { // Exact match
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

  RBNode* closest_leq(const K& key) {
    return const_cast<RBNode*>(
        static_cast<const TreeType*>(this)->closest_leq(key));
  }

  RBNode* closest_gt(const K& key) {
    return const_cast<RBNode*>(
        static_cast<const TreeType*>(this)->closest_gt(key));
  }

  RBNode* closest_geq(const K& key) {
    return const_cast<RBNode*>(
        static_cast<const TreeType*>(this)->closest_geq(key));
  }

  // Returns leftmost node, nullptr if tree is empty.
  // If COMPARATOR::cmp(a, b) behaves canonically (positive value for a > b), this will the smallest key value.
  const RBNode* leftmost() const {
    RBNode* n = _root, *n2 = nullptr;
    while (n != nullptr) {
      n2 = n;
      n = n->_left;
    }
    return n2;
  }

  // Returns rightmost node, nullptr if tree is empty.
  // If COMPARATOR::cmp(a, b) behaves canonically (positive value for a > b), this will the largest key value.
  const RBNode* rightmost() const {
    RBNode* n = _root, *n2 = nullptr;
    while (n != nullptr) {
      n2 = n;
      n = n->_right;
    }
    return n2;
  }

  RBNode* leftmost()  { return const_cast<NodeType*>(static_cast<const TreeType*>(this)->leftmost()); }
  RBNode* rightmost() { return const_cast<NodeType*>(static_cast<const TreeType*>(this)->rightmost()); }

  struct Range {
    RBNode* start;
    RBNode* end;
    Range(RBNode* start, RBNode* end)
    : start(start), end(end) {}
  };

  // Return the range [start, end)
  // where start->key() <= addr < end->key().
  // Failure to find the range leads to start and/or end being null.
  Range find_enclosing_range(K key) const {
    RBNode* start = closest_leq(key);
    RBNode* end = closest_gt(key);
    return Range(start, end);
  }

  // Finds the node associated with the key
  const RBNode* find_node(const K& key) const;

  RBNode* find_node(const K& key) {
    return const_cast<RBNode*>(
        static_cast<const TreeType*>(this)->find_node(key));
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
  void visit_range_in_order(const K& from, const K& to, F f) const;

  // Verifies that the tree is correct and holds rb-properties
  void verify_self() const NOT_DEBUG({});

  void print_on(outputStream* st) const;

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
