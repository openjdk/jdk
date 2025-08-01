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

// An intrusive red-black tree is constructed with two template parameters:
// K is the key type used.
// COMPARATOR must have a static function `cmp(K a, const IntrusiveRBNode* b)` which returns:
//     - an int < 0 when a < b
//     - an int == 0 when a == b
//     - an int > 0 when a > b
// Additional static functions used for extra validation can optionally be provided:
//   `cmp(K a, K b)` which returns:
//       - an int < 0 when a < b
//       - an int == 0 when a == b
//       - an int > 0 when a > b
//   `cmp(const IntrusiveRBNode* a, const IntrusiveRBNode* b)` which returns:
//       - true if a < b
//       - false otherwise
// K needs to be of a type that is trivially destructible.
// K needs to be stored by the user and is not stored inside the tree.
// Nodes are address stable and will not change during its lifetime.

// A red-black tree is constructed with four template parameters:
// K is the key type stored in the tree nodes.
// V is the value type stored in the tree nodes.
// COMPARATOR must have a static function `cmp(K a, K b)` which returns:
//     - an int < 0 when a < b
//     - an int == 0 when a == b
//     - an int > 0 when a > b
// A second static function `cmp(const RBNode<K, V>* a, const RBNode<K, V>* b)`
// used for extra validation can optionally be provided. This should return:
//     - true if a < b
//     - false otherwise
// ALLOCATOR must check for oom and exit, as RBTree does not handle the allocation failing.
// K needs to be of a type that is trivially destructible.
// The tree will call a value's destructor when its node is removed.
// Nodes are address stable and will not change during its lifetime.

template <typename K, typename NodeType, typename COMPARATOR>
class AbstractRBTree;

class outputStream;

class IntrusiveRBNode {
  template <typename K, typename NodeType, typename COMPARATOR>
  friend class AbstractRBTree;
  template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
  friend class RBTree;
  friend class RBTreeTest;

  uintptr_t _parent; // LSB encodes color information. 0 = RED, 1 = BLACK
  IntrusiveRBNode* _left;
  IntrusiveRBNode* _right;

  DEBUG_ONLY(mutable bool _visited);

public:
  IntrusiveRBNode() : _parent(0), _left(nullptr), _right(nullptr) DEBUG_ONLY(COMMA _visited(false)) {}

  // Gets the previous in-order node in the tree.
  // nullptr is returned if there is no previous node.
  const IntrusiveRBNode* prev() const;
  IntrusiveRBNode* prev();

  // Gets the next in-order node in the tree.
  // nullptr is returned if there is no next node.
  const IntrusiveRBNode* next() const;
  IntrusiveRBNode* next();

  void print_on(outputStream* st, int depth = 0) const;

private:
  bool is_black() const { return (_parent & 0x1) != 0; }
  bool is_red() const { return (_parent & 0x1) == 0; }

  void set_black() { _parent |= 0x1; }
  void set_red() { _parent &= ~0x1; }

  IntrusiveRBNode* parent() const { return (IntrusiveRBNode*)(_parent & ~0x1); }
  void set_parent(IntrusiveRBNode* new_parent) { _parent = (_parent & 0x1) | (uintptr_t)new_parent; }

  bool is_right_child() const {
    return parent() != nullptr && parent()->_right == this;
  }

  bool is_left_child() const {
    return parent() != nullptr && parent()->_left == this;
  }

  void replace_child(IntrusiveRBNode* old_child, IntrusiveRBNode* new_child);

  // This node down, right child up
  // Returns right child (now parent)
  IntrusiveRBNode* rotate_left();

  // This node down, left child up
  // Returns left child (now parent)
  IntrusiveRBNode* rotate_right();

  template <typename NodeType, typename NodeVerifier>
  void verify(size_t& num_nodes, size_t& black_nodes_until_leaf,
              size_t& shortest_leaf_path, size_t& longest_leaf_path,
              size_t& tree_depth, bool expect_visited, NodeVerifier verifier) const;

};

template <typename K, typename V>
class RBNode : public IntrusiveRBNode {
  template <typename K2, typename V2, typename COMPARATOR, typename ALLOCATOR>
  friend class RBTree;
  friend class RBTreeTest;

private:
  K _key;
  V _value;

public:
  const K& key() const { return _key; }

  V& val() { return _value; }
  const V& val() const { return _value; }
  void set_val(const V& v) { _value = v; }

  RBNode() {}
  RBNode(const K& key) : IntrusiveRBNode(), _key(key) {}
  RBNode(const K& key, const V& val) : IntrusiveRBNode(), _key(key), _value(val) {}

  const RBNode<K, V>* prev() const { return (RBNode<K, V>*)IntrusiveRBNode::prev(); }
  const RBNode<K, V>* next() const { return (RBNode<K, V>*)IntrusiveRBNode::next(); }
  RBNode<K, V>* prev() { return (RBNode<K, V>*)IntrusiveRBNode::prev(); }
  RBNode<K, V>* next() { return (RBNode<K, V>*)IntrusiveRBNode::next(); }

  void print_on(outputStream* st, int depth = 0) const;

};

template <typename K, typename NodeType, typename COMPARATOR>
class AbstractRBTree {
  friend class RBTreeTest;
  typedef AbstractRBTree<K, NodeType, COMPARATOR> TreeType;

public:
  // Represents the location of a (would be) node in the tree.
  // If a cursor is valid (valid() == true) it points somewhere in the tree.
  // If the cursor points to an existing node (found() == true), node() can be used to access that node.
  // If no node is pointed to, node() returns null, regardless if the cursor is valid or not.
  class Cursor {
    friend AbstractRBTree<K, NodeType, COMPARATOR>;
    NodeType** _insert_location;
    NodeType* _parent;
    Cursor() : _insert_location(nullptr), _parent(nullptr) {}
    Cursor(NodeType** insert_location, NodeType* parent)
        : _insert_location(insert_location), _parent(parent) {}
    Cursor(NodeType* const* insert_location, NodeType* parent)
        : _insert_location((NodeType**)insert_location), _parent(parent) {}

  public:
    bool valid() const { return _insert_location != nullptr; }
    bool found() const { return *_insert_location != nullptr; }
    NodeType* node() { return _insert_location == nullptr ? nullptr : *_insert_location; }
    NodeType* node() const { return _insert_location == nullptr ? nullptr : *_insert_location; }
  };

protected:
  size_t _num_nodes;
  IntrusiveRBNode* _root;
  DEBUG_ONLY(mutable bool _expected_visited);

private:
  template <typename CMP, typename RET, typename ARG1, typename ARG2, typename = void>
  struct has_cmp_type : std::false_type {};
  template <typename CMP, typename RET, typename ARG1, typename ARG2>
  struct has_cmp_type<CMP, RET, ARG1, ARG2, decltype(static_cast<RET(*)(ARG1, ARG2)>(CMP::cmp), void())> : std::true_type {};

  template <typename CMP>
  static constexpr bool HasKeyComparator = has_cmp_type<CMP, int, K, K>::value;

  template <typename CMP>
  static constexpr bool HasNodeComparator = has_cmp_type<CMP, int, K, const NodeType*>::value;

  template <typename CMP>
  static constexpr bool HasNodeVerifier = has_cmp_type<CMP, bool, const NodeType*, const NodeType*>::value;

  template <typename CMP = COMPARATOR, ENABLE_IF(HasKeyComparator<CMP> && !HasNodeComparator<CMP>)>
  int cmp(const K& a, const NodeType* b) const {
    return COMPARATOR::cmp(a, b->key());
  }

  template <typename CMP = COMPARATOR, ENABLE_IF(HasNodeComparator<CMP>)>
  int cmp(const K& a, const NodeType* b) const {
    return COMPARATOR::cmp(a, b);
  }

  template <typename CMP = COMPARATOR, ENABLE_IF(!HasNodeVerifier<CMP>)>
  bool cmp(const NodeType* a, const NodeType* b) const {
    return true;
  }

  template <typename CMP = COMPARATOR, ENABLE_IF(HasNodeVerifier<CMP>)>
  bool cmp(const NodeType* a, const NodeType* b) const {
    return COMPARATOR::cmp(a, b);
  }

  // Cannot assert if no key comparator exist.
  template <typename CMP = COMPARATOR, ENABLE_IF(!HasKeyComparator<CMP>)>
  void assert_key_leq(K a, K b) const {}

  template <typename CMP = COMPARATOR, ENABLE_IF(HasKeyComparator<CMP>)>
  void assert_key_leq(K a, K b) const {
    assert(COMPARATOR::cmp(a, b) <= 0, "key a must be less or equal to key b");
  }

  // True if node is black (nil nodes count as black)
  static inline bool is_black(const IntrusiveRBNode* node) {
    return node == nullptr || node->is_black();
  }

  static inline bool is_red(const IntrusiveRBNode* node) {
    return node != nullptr && node->is_red();
  }

  void fix_insert_violations(IntrusiveRBNode* node);

  void remove_black_leaf(IntrusiveRBNode* node);

  // Assumption: node has at most one child. Two children is handled in `remove_at_cursor()`
  void remove_from_tree(IntrusiveRBNode* node);

  template <typename NodeVerifier>
  void verify_self(NodeVerifier verifier) const;

  void print_node_on(outputStream* st, int depth, const NodeType* n) const;

public:
  NONCOPYABLE(AbstractRBTree);

  AbstractRBTree() : _num_nodes(0), _root(nullptr) DEBUG_ONLY(COMMA _expected_visited(false)) {
    static_assert(std::is_trivially_destructible<K>::value, "key type must be trivially destructable");
    static_assert(HasKeyComparator<COMPARATOR> || HasNodeComparator<COMPARATOR>,
                  "comparator must be of correct type");
  }

  size_t size() const { return _num_nodes; }

  // Gets the cursor associated with the given node or key.
  Cursor cursor(const K& key, const NodeType* hint_node = nullptr);
  Cursor cursor(const NodeType* node);
  const Cursor cursor(const K& key, const NodeType* hint_node = nullptr) const;
  const Cursor cursor(const NodeType* node) const;

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
  void insert_at_cursor(NodeType* node, const Cursor& node_cursor);

  // Removes the node referenced by the cursor
  // The cursor must point to a valid existing node
  void remove_at_cursor(const Cursor& node_cursor);

  // Replace the node referenced by the cursor with a new node.
  // The old node is destroyed.
  // The user must ensure that no tree properties are broken:
  // There must not exist any node with the new key
  // For all nodes with key < old_key, must also have key < new_key
  // For all nodes with key > old_key, must also have key > new_key
  void replace_at_cursor(NodeType* new_node, const Cursor& node_cursor);

  // Finds the node associated with the given key.
  NodeType* find_node(const K& key, const NodeType* hint_node = nullptr) const {
    Cursor node_cursor = cursor(key, hint_node);
    return node_cursor.node();
  }

  NodeType* find_node(const K& key, const NodeType* hint_node = nullptr) {
    Cursor node_cursor = cursor(key, hint_node);
    return node_cursor.node();
  }

  // Inserts the given node into the tree.
  void insert(const K& key, NodeType* node, const NodeType* hint_node = nullptr) {
    Cursor node_cursor = cursor(key, hint_node);
    insert_at_cursor(node, node_cursor);
  }

  void remove(NodeType* node) {
    Cursor node_cursor = cursor(node);
    remove_at_cursor(node_cursor);
  }

  // Finds the node with the closest key <= the given key.
  // If no node is found, null is returned instead.
  NodeType* closest_leq(const K& key) const {
    Cursor node_cursor = cursor(key);
    return node_cursor.found() ? node_cursor.node() : prev(node_cursor).node();
  }

  NodeType* closest_leq(const K& key) {
    Cursor node_cursor = cursor(key);
    return node_cursor.found() ? node_cursor.node() : prev(node_cursor).node();
  }

  // Finds the node with the closest key > the given key.
  // If no node is found, null is returned instead.
  NodeType* closest_gt(const K& key) const {
    Cursor node_cursor = cursor(key);
    return next(node_cursor).node();
  }

  NodeType* closest_gt(const K& key) {
    Cursor node_cursor = cursor(key);
    return next(node_cursor).node();
  }

  // Finds the node with the closest key >= the given key.
  // If no node is found, null is returned instead.
  NodeType* closest_ge(const K& key) const {
    Cursor node_cursor = cursor(key);
    return node_cursor.found() ? node_cursor.node() : next(node_cursor).node();
  }

  NodeType* closest_ge(const K& key) {
    Cursor node_cursor = cursor(key);
    return node_cursor.found() ? node_cursor.node() : next(node_cursor).node();
  }

  // Returns leftmost node, nullptr if tree is empty.
  // If COMPARATOR::cmp(a, b) behaves canonically (positive value for a > b), this will the smallest key value.
  const NodeType* leftmost() const {
    IntrusiveRBNode* n = _root, *n2 = nullptr;
    while (n != nullptr) {
      n2 = n;
      n = n->_left;
    }
    return (NodeType*)n2;
  }

  // Returns rightmost node, nullptr if tree is empty.
  // If COMPARATOR::cmp(a, b) behaves canonically (positive value for a > b), this will the largest key value.
  const NodeType* rightmost() const {
    IntrusiveRBNode* n = _root, *n2 = nullptr;
    while (n != nullptr) {
      n2 = n;
      n = n->_right;
    }
    return (NodeType*)n2;
  }

  NodeType* leftmost()  { return const_cast<NodeType*>(static_cast<const TreeType*>(this)->leftmost()); }
  NodeType* rightmost() { return const_cast<NodeType*>(static_cast<const TreeType*>(this)->rightmost()); }

  struct Range {
    NodeType* start;
    NodeType* end;
    Range(NodeType* start, NodeType* end)
    : start(start), end(end) {}
  };

  // Return the range [start, end)
  // where start->key() <= addr < end->key().
  // Failure to find the range leads to start and/or end being null.
  Range find_enclosing_range(K key) const {
    NodeType* start = closest_leq(key);
    NodeType* end = closest_gt(key);
    return Range(start, end);
  }

  // Visit all RBNodes in ascending order, calling f on each node.
  template <typename F>
  void visit_in_order(F f) const;

  // Visit all RBNodes in ascending order whose keys are in range [from, to], calling f on each node.
  template <typename F>
  void visit_range_in_order(const K& from, const K& to, F f) const;

  // Verifies that the tree is correct and holds rb-properties
  // If not using a key comparator (when using IntrusiveRBTree for example),
  // A second `cmp` must exist in COMPARATOR (see top of file).
  template <typename CMP = COMPARATOR, ENABLE_IF(HasNodeVerifier<CMP>)>
  void verify_self() const {
    verify_self([](const NodeType* a, const NodeType* b){ return COMPARATOR::cmp(a, b);});
  }

  template <typename CMP = COMPARATOR, ENABLE_IF(HasKeyComparator<CMP> && !HasNodeVerifier<CMP>)>
  void verify_self() const {
    verify_self([](const NodeType* a, const NodeType* b){ return COMPARATOR::cmp(a->key(), b->key()) < 0; });
  }

  template <typename CMP = COMPARATOR, ENABLE_IF(HasNodeComparator<CMP> && !HasKeyComparator<CMP> && !HasNodeVerifier<CMP>)>
  void verify_self() const {
    verify_self([](const NodeType*, const NodeType*){ return true;});
  }

  void print_on(outputStream* st) const;

};

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
class RBTree : public AbstractRBTree<K, RBNode<K, V>, COMPARATOR> {
  friend class RBTreeTest;
  typedef AbstractRBTree<K, RBNode<K, V>, COMPARATOR> BaseType;

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

  void replace_at_cursor(RBNode<K, V>* new_node, const Cursor& node_cursor) {
    RBNode<K, V>* old_node = node_cursor.node();
    BaseType::replace_at_cursor(new_node, node_cursor);
    free_node(old_node);
  }

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
      node->set_val(val);
      return;
    }

    node = allocate_node(key, val);
    insert_at_cursor(node, node_cursor);
  }

  // Finds the value of the node associated with the given key.
  V* find(const K& key) {
    Cursor node_cursor = cursor(key);
    return node_cursor.found() ? &node_cursor.node()->_value : nullptr;
  }

  V* find(const K& key) const {
    const Cursor node_cursor = cursor(key);
    return node_cursor.found() ? &node_cursor.node()->_value : nullptr;
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
    IntrusiveRBNode* to_delete[64];
    int stack_idx = 0;
    to_delete[stack_idx++] = BaseType::_root;

    while (stack_idx > 0) {
      IntrusiveRBNode* head = to_delete[--stack_idx];
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

template <typename K, typename COMPARATOR>
using IntrusiveRBTree = AbstractRBTree<K, IntrusiveRBNode, COMPARATOR>;

#endif // SHARE_UTILITIES_RBTREE_HPP
