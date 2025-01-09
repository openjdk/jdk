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
#include "utilities/growableArray.hpp"
#include <type_traits>

// COMPARATOR must have a static function `cmp(a,b)` which returns:
//     - an int < 0 when a < b
//     - an int == 0 when a == b
//     - an int > 0 when a > b
// ALLOCATOR must check for oom and exit, as RBTree currently does not handle the
// allocation failing.
// Key needs to be of a type that is trivially destructable
// The tree will call a value's destructor when its node is removed

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
    RBNode* _parent;
    RBNode* _left;
    RBNode* _right;

    const K _key;
    V _value;

    enum Color : uint8_t { BLACK, RED };
    Color _color;

  public:
    const K& key() const { return _key; }
    V& val() { return _value; }

  private:
    bool is_black() const { return _color == BLACK; }
    bool is_red() const { return _color == RED; }

    void set_black() { _color = BLACK; }
    void set_red() { _color = RED; }

    RBNode(const K& k, const V& v)
        : _parent(nullptr), _left(nullptr), _right(nullptr),
          _key(k), _value(v), _color(RED) {}

    bool is_right_child() const {
      return _parent != nullptr && _parent->_right == this;
    }

    bool is_left_child() const {
      return _parent != nullptr && _parent->_left == this;
    }

    void replace_child(RBNode* old_child, RBNode* new_child);

    // Move node down to the left, and right child up
    RBNode* rotate_left();

    // Move node down to the right, and left child up
    RBNode* rotate_right();

  #ifdef ASSERT
    bool is_correct(unsigned int num_blacks, unsigned int maximum_depth, unsigned int current_depth) const;
    size_t count_nodes() const;
  #endif // ASSERT
  };

private:
  RBNode* _root;

  RBNode* allocate_node(const K& k, const V& v) {
    void* node_place = _allocator.allocate(sizeof(RBNode));
    assert(node_place != nullptr, "rb-tree allocator must exit on failure");
    _num_nodes++;
    return new (node_place) RBNode(k, v);
  }

  void free_node(RBNode* node) {
    node->_value.~V();
    _allocator.free(node);
    _num_nodes--;
  }

  static inline bool is_black(RBNode* node) {
    return node == nullptr || node->is_black();
  }

  static inline bool is_red(RBNode* node) {
    return node != nullptr && node->is_red();
  }

  RBNode* find_node(RBNode* curr, const K& k);

   // If the node with key k already exist, the value is updated instead.
  RBNode* insert_node(const K& k, const V& v);

  void fix_insert_violations(RBNode* node);

  void remove_black_leaf(RBNode* node);

  // Assumption: node has at most one child. Two children is handled in `remove()`
  void remove_from_tree(RBNode* node);

public:
  NONCOPYABLE(RBTree);

  RBTree() : _allocator(), _num_nodes(0), _root(nullptr) {
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
    RBNode* node = find_node(_root, k);
    return remove(node);
  }

  // Removes the given node from the tree.
  // Returns true if the node was successfully removed, false otherwise.
  bool remove(RBNode* node);

  // Removes all existing nodes from the tree.
  void remove_all() {
    GrowableArrayCHeap<RBNode*, mtInternal> to_delete(2 * log2i(_num_nodes + 1));
    to_delete.push(_root);

    while (!to_delete.is_empty()) {
      RBNode* head = to_delete.pop();
      if (head == nullptr) continue;
      to_delete.push(head->_left);
      to_delete.push(head->_right);
      free_node(head);
    }
    _num_nodes = 0;
    _root = nullptr;
  }

  // Finds the node with the closest key <= the given key
  RBNode* closest_leq(const K& key) {
    RBNode* candidate = nullptr;
    RBNode* pos = _root;
    while (pos != nullptr) {
      int cmp_r = COMPARATOR::cmp(pos->key(), key);
      if (cmp_r == 0) { // Exact match
        candidate = pos;
        break; // Can't become better than that.
      }
      if (cmp_r < 0) {
        // Found a match, try to find a better one.
        candidate = pos;
        pos = pos->_right;
      } else if (cmp_r > 0) {
        pos = pos->_left;
      }
    }
    return candidate;
  }

  // Finds the node with the closest key > the given key
  RBNode* closest_gt(const K& key) {
    RBNode* candidate = nullptr;
    RBNode* pos = _root;
    while (pos != nullptr) {
      int cmp_r = COMPARATOR::cmp(pos->key(), key);
      if (cmp_r > 0) { // node > key
        // Found a match, try to find a better one.
        candidate = pos;
        pos = pos->_left;
      } else { // node <= key
        pos = pos->_right;
      }
    }
    return candidate;
  }

  struct Range {
    RBNode* start;
    RBNode* end;
    Range(RBNode* start, RBNode* end) : start(start), end(end) {}
  };

  // Return the range [start, end)
  // where start->key() <= addr < end->key().
  // Failure to find the range leads to start and/or end being nullptr.
  Range find_enclosing_range(K addr) {
    RBNode* start = closest_leq(addr);
    RBNode* end = closest_gt(addr);
    return Range(start, end);
  }

  // Finds the value associated with the key
  V* find(const K& key) {
    RBNode* node = find_node(_root, key);
    if (node == nullptr) {
      return nullptr;
    }
    return &node->val();
  }

  // Visit all RBNodes in ascending order, calling f on each node.
  template <typename F>
  void visit_in_order(F f);

  // Visit all RBNodes in ascending order whose keys are in range [from, to), calling f on each node.
  template <typename F>
  void visit_range_in_order(const K& from, const K& to, F f);

#ifdef ASSERT
  // Verifies that the tree is correct and holds rb-properties
  void verify_self();
#endif // ASSERT

private:
  template<bool Forward>
  class IteratorImpl : public StackObj {
  private:
    const RBTree* const _tree;
    GrowableArrayCHeap<RBNode*, mtInternal> _to_visit;

    void push_left(RBNode* node);
    void push_right(RBNode* node);

  public:
    NONCOPYABLE(IteratorImpl);

    IteratorImpl(const RBTree* tree) : _tree(tree) {
      _to_visit.reserve(2 * log2i(_tree->_num_nodes + 1));
      Forward ? push_left(tree->_root) : push_right(tree->_root);
    }

    bool has_next() { return !_to_visit.is_empty(); }

    RBNode* next() {
      RBNode* node = _to_visit.pop();
      if (node != nullptr) {
        Forward ? push_left(node->_right) : push_right(node->_left);
      }
      return node;
    }
  };

public:
  using Iterator = IteratorImpl<true>; // Forward iterator
  using ReverseIterator = IteratorImpl<false>; // Backward iterator

};

template <MemTag mt>
class RBTreeCHeapAllocator {
public:
  void* allocate(size_t sz) {
    void* allocation = os::malloc(sz, mt);
    if (allocation == nullptr) {
      vm_exit_out_of_memory(sz, OOM_MALLOC_ERROR,
                            "red-black tree failed allocation");
    }
    return allocation;
  }

  void free(void* ptr) { os::free(ptr); }
};

template <typename K, typename V, typename COMPARATOR, MemTag mt>
using RBTreeCHeap = RBTree<K, V, COMPARATOR, RBTreeCHeapAllocator<mt>>;

#endif // SHARE_UTILITIES_RBTREE_HPP
