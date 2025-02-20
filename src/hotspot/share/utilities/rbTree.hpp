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

#include "metaprogramming/enableIf.hpp"
#include "nmt/memTag.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include <type_traits>

// A tree is constructed with several template parameters:
// K is the key type stored in the tree nodes.
// V is the value type stored in the tree nodes.
// COMPARATOR must have a static function `cmp(K a, K b)` which returns:
//     - an int < 0 when a < b
//     - an int == 0 when a == b
//     - an int > 0 when a > b
// ALLOCATOR must check for oom and exit, as RBTree does not handle the allocation failing.
// For the intrusive RBTree, only K and COMPARATOR are needed.
// K needs to be of a type that is trivially destructible.
// The tree will call a value's destructor when its node is removed.
// Nodes are address stable and will not change during its lifetime.

class outputStream;

template <typename K, typename V, typename COMPARATOR>
class AbstractRBTree;

// If the value in a node is not desired (like in an intrusive tree),
// we can use empty base optimization to avoid wasting space
// by inheriting from Empty instead of Value.
struct Empty {};

template <typename V>
class Value {
public:
  V _value;
  Value(const V& val) : _value(val) {}
};

template <typename K, typename V>
class RBNode : std::conditional_t<std::is_same<V, void>::value, Empty, Value<V>>{
  template <typename K2, typename V2, typename COMPARATOR>
  friend class AbstractRBTree;
  template <typename K2, typename V2, typename COMPARATOR, typename ALLOCATOR>
  friend class RBTree;
  friend class RBTreeTest;

private:
  uintptr_t _parent; // LSB encodes color information. 0 = RED, 1 = BLACK
  RBNode* _left;
  RBNode* _right;

  K _key;

  DEBUG_ONLY(mutable bool _visited);

public:
  const K& key() const { return _key; }
  void set_key(K& key) { _key = key; }

  template <typename VV = V, ENABLE_IF(!std::is_same<VV, void>::value)>
  VV& val() { return Value<VV>::_value; }

  template <typename VV = V, ENABLE_IF(!std::is_same<VV, void>::value)>
  const VV& val() const { return Value<VV>::_value; }

  template <typename VV = V, ENABLE_IF(!std::is_same<VV, void>::value)>
  void set_val(const VV& v) { Value<VV>::_value = v; }

  RBNode() {}
  RBNode(const K& key)
      : _parent(0), _left(nullptr), _right(nullptr),
        _key(key) DEBUG_ONLY(COMMA _visited(false)) {}

  template <typename VV = V, ENABLE_IF(!std::is_same<VV, void>::value)>
  RBNode(const K& key, const VV& val)
      :  Value<VV>(val), _parent(0), _left(nullptr), _right(nullptr),
        _key(key) DEBUG_ONLY(COMMA _visited(false)) {}

  // Gets the previous in-order node in the tree.
  // nullptr is returned if there is no previous node.
  const RBNode* prev() const;

  // Gets the next in-order node in the tree.
  // nullptr is returned if there is no next node.
  const RBNode* next() const;

private:
  bool is_black() const { return (_parent & 0x1) != 0; }
  bool is_red() const { return (_parent & 0x1) == 0; }

  void set_black() { _parent |= 0x1; }
  void set_red() { _parent &= ~0x1; }

  RBNode* parent() const { return (RBNode*)(_parent & ~0x1); }
  void set_parent(RBNode* new_parent) { _parent = (_parent & 0x1) | (uintptr_t)new_parent; }

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

#ifdef ASSERT
  void verify(size_t& num_nodes, size_t& black_nodes_until_leaf,
              size_t& shortest_leaf_path, size_t& longest_leaf_path,
              size_t& tree_depth, bool expect_visited, int (*cmp)(K, K)) const;
#endif // ASSERT
}; // End: RBNode

template <typename K, typename V, typename COMPARATOR>
class AbstractRBTree {
  friend class RBTreeTest;
  typedef AbstractRBTree<K, V, COMPARATOR> TreeType;

public:
  typedef RBNode<K, V> NodeType;

  // Represents the location of a (would be) node in the tree.
  // If a cursor is valid (valid() == true) it points somewhere in the tree.
  // If the cursor points to an existing node (found() == true), node() can be used to access that node.
  // If no node is pointed to, node() returns null, regardless if the cursor is valid or not.
  class Cursor {
    friend AbstractRBTree<K, V, COMPARATOR>;
    RBNode<K, V>** _insert_location;
    RBNode<K, V>* _parent;
    K _key;
    Cursor() : _insert_location(nullptr), _parent(nullptr) {}
    Cursor(RBNode<K, V>** insert_location, RBNode<K, V>* parent, const K& key)
        : _insert_location(insert_location), _parent(parent), _key(key) {}
    Cursor(RBNode<K, V>* const* insert_location, RBNode<K, V>* parent, const K& key)
        : _insert_location((RBNode<K, V>**)insert_location), _parent(parent), _key(key) {}

  public:
    bool valid() const { return _insert_location != nullptr; }
    bool found() const { return *_insert_location != nullptr; }
    RBNode<K, V>* node() { return _insert_location == nullptr ? nullptr : *_insert_location; }
    RBNode<K, V>* node() const { return _insert_location == nullptr ? nullptr : *_insert_location; }
  };

protected:
  size_t _num_nodes;
  RBNode<K, V>* _root;
  DEBUG_ONLY(mutable bool _expected_visited);

  // True if node is black (nil nodes count as black)
  static inline bool is_black(const RBNode<K, V>* node) {
    return node == nullptr || node->is_black();
  }

  static inline bool is_red(const RBNode<K, V>* node) {
    return node != nullptr && node->is_red();
  }

  void fix_insert_violations(RBNode<K, V>* node);

  void remove_black_leaf(RBNode<K, V>* node);

  // Assumption: node has at most one child. Two children is handled in `remove_at_cursor()`
  void remove_from_tree(RBNode<K, V>* node);

  void print_node_on(outputStream* st, int depth, const NodeType* n) const;

public:
  NONCOPYABLE(AbstractRBTree);

  AbstractRBTree() : _num_nodes(0), _root(nullptr) DEBUG_ONLY(COMMA _expected_visited(false)) {
    static_assert(std::is_trivially_destructible<K>::value, "key type must be trivially destructable");
  }

  size_t size() const { return _num_nodes; }

  // Gets the cursor associated with the given node or key.
  Cursor cursor(const K& key, const RBNode<K, V>* hint_node = nullptr);
  Cursor cursor(const RBNode<K, V>* node);
  const Cursor cursor(const K& key, const RBNode<K, V>* hint_node = nullptr) const;
  const Cursor cursor(const RBNode<K, V>* node) const;

  // Moves to the next existing node.
  // If no next node exist, the cursor becomes invalid.
  Cursor next(const Cursor& node_cursor);
  const Cursor next(const Cursor& node_cursor) const;

  // Moves to the previous existing node.
  // If no previous node exist, the cursor becomes invalid.
  Cursor prev(const Cursor& node_cursor);
  const Cursor prev(const Cursor& node_cursor) const;

  // Initializes and inserts a node at the cursor location.
  // The cursor must not point to an existing node.
  // Node is given the same key used in `cursor()`.
  void insert_at_cursor(RBNode<K, V>* node, const Cursor& node_cursor);

  // Removes the node referenced by the cursor
  // The cursor must point to a valid existing node
  void remove_at_cursor(const Cursor& node_cursor);

  // Replace the node referenced by the cursor with a new node.
  // The old node is destroyed.
  // The user must ensure that no tree properties are broken:
  // There must not exist any node with the new key
  // For all nodes with key < old_key, must also have key < new_key
  // For all nodes with key > old_key, must also have key > new_key
  void replace_at_cursor(RBNode<K, V>* new_node, const Cursor& node_cursor);

  // Finds the value of the node associated with the given key.
  V* find(const K& key) {
    Cursor node_cursor = cursor(key);
    return node_cursor.found() ? &node_cursor.node()->_value : nullptr;
  }

  V* find(const K& key) const {
    const Cursor node_cursor = cursor(key);
    return node_cursor.found() ? &node_cursor.node()->_value : nullptr;
  }

  // Finds the node associated with the given key.
  RBNode<K, V>* find_node(const K& key, const RBNode<K, V>* hint_node = nullptr) const {
    Cursor node_cursor = cursor(key, hint_node);
    return node_cursor.node();
  }

  RBNode<K, V>* find_node(const K& key, const RBNode<K, V>* hint_node = nullptr) {
    Cursor node_cursor = cursor(key, hint_node);
    return node_cursor.node();
  }

  // Inserts the given node into the tree.
  void insert(RBNode<K, V>* node, const RBNode<K, V>* hint_node = nullptr) {
    Cursor node_cursor = cursor(node->key(), hint_node);
    insert_at_cursor(node, node_cursor);
  }

  void remove(RBNode<K, V>* node) {
    Cursor node_cursor = cursor(node);
    remove_at_cursor(node_cursor);
  }

  // Finds the node with the closest key <= the given key.
  // If no node is found, null is returned instead.
  RBNode<K, V>* closest_leq(const K& key) const {
    Cursor node_cursor = cursor(key);
    return node_cursor.found() ? node_cursor.node() : prev(node_cursor).node();
  }

  RBNode<K, V>* closest_leq(const K& key) {
    Cursor node_cursor = cursor(key);
    return node_cursor.found() ? node_cursor.node() : prev(node_cursor).node();
  }

  // Finds the node with the closest key > the given key.
  // If no node is found, null is returned instead.
  RBNode<K, V>* closest_gt(const K& key) const {
    Cursor node_cursor = cursor(key);
    return next(node_cursor).node();
  }

  RBNode<K, V>* closest_gt(const K& key) {
    Cursor node_cursor = cursor(key);
    return next(node_cursor).node();
  }

  // Finds the node with the closest key >= the given key.
  // If no node is found, null is returned instead.
  RBNode<K, V>* closest_ge(const K& key) const {
    Cursor node_cursor = cursor(key);
    return node_cursor.found() ? node_cursor.node() : next(node_cursor).node();
  }

  RBNode<K, V>* closest_ge(const K& key) {
    Cursor node_cursor = cursor(key);
    return node_cursor.found() ? node_cursor.node() : next(node_cursor).node();
  }

  // Returns leftmost node, nullptr if tree is empty.
  // If COMPARATOR::cmp(a, b) behaves canonically (positive value for a > b), this will the smallest key value.
  const RBNode<K, V>* leftmost() const {
    RBNode<K, V>* n = _root, *n2 = nullptr;
    while (n != nullptr) {
      n2 = n;
      n = n->_left;
    }
    return n2;
  }

  // Returns rightmost node, nullptr if tree is empty.
  // If COMPARATOR::cmp(a, b) behaves canonically (positive value for a > b), this will the largest key value.
  const RBNode<K, V>* rightmost() const {
    RBNode<K, V>* n = _root, *n2 = nullptr;
    while (n != nullptr) {
      n2 = n;
      n = n->_right;
    }
    return n2;
  }

  RBNode<K, V>* leftmost()  { return const_cast<NodeType*>(static_cast<const TreeType*>(this)->leftmost()); }
  RBNode<K, V>* rightmost() { return const_cast<NodeType*>(static_cast<const TreeType*>(this)->rightmost()); }

  struct Range {
    RBNode<K, V>* start;
    RBNode<K, V>* end;
    Range(RBNode<K, V>* start, RBNode<K, V>* end)
    : start(start), end(end) {}
  };

  // Return the range [start, end)
  // where start->key() <= addr < end->key().
  // Failure to find the range leads to start and/or end being null.
  Range find_enclosing_range(K key) const {
    RBNode<K, V>* start = closest_leq(key);
    RBNode<K, V>* end = closest_gt(key);
    return Range(start, end);
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

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
class RBTree : public AbstractRBTree<K, V, COMPARATOR> {
  friend class RBTreeTest;
  typedef AbstractRBTree<K, V, COMPARATOR> BaseType;

  ALLOCATOR _allocator;

public:
  RBTree() : BaseType(), _allocator() {}
  ~RBTree() { remove_all(); }

  typedef typename BaseType::Cursor Cursor;
  using BaseType::cursor;
  using BaseType::insert_at_cursor;
  using BaseType::remove_at_cursor;
  using BaseType::next;
  using BaseType::prev;

  RBNode<K, V>* allocate_node(const K& key, const V& val) {
    void* node_place = _allocator.allocate(sizeof(RBNode<K, V>));
    assert(node_place != nullptr, "rb-tree allocator must exit on failure");
    return new (node_place) RBNode<K, V>(key, val);
  }

  void free_node(RBNode<K, V>* node) {
    node->_value.~V();
    _allocator.free(node);
  }

  // Inserts a node with the given key/value into the tree,
  // if the key already exist, the value is updated instead.
  void upsert(const K& key, const V& val, const RBNode<K, V>* hint_node = nullptr) {
    Cursor node_cursor = cursor(key, hint_node);
    RBNode<K, V>* node = node_cursor.node();
    if (node != nullptr) {
      // node->_value = val;
      node->set_val(val);
      return;
    }

    node = allocate_node(key, val);
    insert_at_cursor(node, node_cursor);
  }

  void remove(RBNode<K, V>* node) {
    Cursor node_cursor = cursor(node);
    remove_at_cursor(node_cursor);
    free_node(node);
  }

  // Removes the node with the given key from the tree if it exists.
  // Returns true if the node was successfully removed, false otherwise.
  bool remove(const K& key) {
    Cursor node_cursor = cursor(key);
    if (!node_cursor.found()) {
      return false;
    }
    RBNode<K, V>* node = node_cursor.node();
    remove_at_cursor(node_cursor);
    free_node((RBNode<K, V>*)node);
    return true;
  }

  // Removes all existing nodes from the tree.
  void remove_all() {
    RBNode<K, V>* to_delete[64];
    int stack_idx = 0;
    to_delete[stack_idx++] = BaseType::_root;

    while (stack_idx > 0) {
      RBNode<K, V>* head = to_delete[--stack_idx];
      if (head == nullptr) continue;
      to_delete[stack_idx++] = head->_left;
      to_delete[stack_idx++] = head->_right;
      free_node((RBNode<K, V>*)head);
    }
    BaseType::_num_nodes = 0;
    BaseType::_root = nullptr;
  }
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

template <typename K>
using IntrusiveNode = RBNode<K, void>;

template <typename K, typename COMPARATOR>
using IntrusiveRBTree = AbstractRBTree<K, void, COMPARATOR>;

#endif // SHARE_UTILITIES_RBTREE_HPP
