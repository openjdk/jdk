/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cppstdlib/type_traits.hpp"
#include "memory/allocation.hpp"
#include "nmt/memTag.hpp"
#include "utilities/globalDefinitions.hpp"

// An intrusive red-black tree is constructed with two template parameters:
// K is the key type used.
// COMPARATOR must have a static function `cmp(K a, const IntrusiveRBNode* b)` which returns:
//     - RBTreeOrdering::LT when a < b
//     - RBTreeOrdering::EQ when a == b
//     - RBTreeOrdering::GT when a > b
// A second static function `less_than(const IntrusiveRBNode* a, const IntrusiveRBNode* b)`
// used for extra validation can optionally be provided. This should return:
//     - true if a < b
//     - false otherwise
// K needs to be of a type that is trivially destructible.
// K needs to be stored by the user and is not stored inside the tree.
// Nodes are address stable and will not change during its lifetime.

// A red-black tree is constructed with four template parameters:
// K is the key type stored in the tree nodes.
// V is the value type stored in the tree nodes.
// COMPARATOR must have a static function `cmp(K a, K b)` which returns:
//     - RBTreeOrdering::LT when a < b
//     - RBTreeOrdering::EQ when a == b
//     - RBTreeOrdering::GT when a > b
// A second static function `less_than(const RBNode<K, V>* a, const RBNode<K, V>* b)`
// used for extra validation can optionally be provided. This should return:
//     - true if a < b
//     - false otherwise
// K needs to be of a type that is trivially destructible.
// The tree will call a value's destructor when its node is removed.
// Nodes are address stable and will not change during its lifetime.

enum class RBTreeOrdering : int { LT, EQ, GT };

template <typename K, typename NodeType, typename COMPARATOR>
class AbstractRBTree;
class Arena;
class outputStream;
class ResourceArea;

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
  IntrusiveRBNode();

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
  bool is_black() const;
  bool is_red() const;

  void set_black();
  void set_red();

  IntrusiveRBNode* parent() const;
  void set_parent(IntrusiveRBNode* new_parent);

  bool is_right_child() const;

  bool is_left_child() const;

  void replace_child(IntrusiveRBNode* old_child, IntrusiveRBNode* new_child);

  // This node down, right child up
  // Returns right child (now parent)
  IntrusiveRBNode* rotate_left();

  // This node down, left child up
  // Returns left child (now parent)
  IntrusiveRBNode* rotate_right();

  template <typename NodeType, typename NODE_VERIFIER, typename USER_VERIFIER>
  void verify(size_t& num_nodes, size_t& black_nodes_until_leaf,
              size_t& shortest_leaf_path, size_t& longest_leaf_path,
              size_t& tree_depth, bool expect_visited, NODE_VERIFIER verifier,
              const USER_VERIFIER& extra_verifier) const;

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
  const K& key() const;

  V& val();
  const V& val() const;
  void set_val(const V& v);

  RBNode();
  RBNode(const K& key);
  RBNode(const K& key, const V& val);

  const RBNode<K, V>* prev() const;
  const RBNode<K, V>* next() const;
  RBNode<K, V>* prev();
  RBNode<K, V>* next();

  void print_on(outputStream* st, int depth = 0) const;

};

template <typename K, typename NodeType, typename COMPARATOR>
class AbstractRBTree {
  friend class RBTreeTest;
  friend class NMTVMATreeTest;
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
    Cursor();
    Cursor(NodeType** insert_location, NodeType* parent);
    Cursor(NodeType* const* insert_location, NodeType* parent);

  public:
    bool valid() const;
    bool found() const;
    NodeType* node();
    NodeType* node() const;
  };

protected:
  size_t _num_nodes;
  IntrusiveRBNode* _root;
  DEBUG_ONLY(mutable bool _expected_visited);

private:
  static constexpr bool HasKeyComparator =
      std::is_invocable_r_v<RBTreeOrdering, decltype(&COMPARATOR::cmp), K, K>;

  static constexpr bool HasNodeComparator =
      std::is_invocable_r_v<RBTreeOrdering, decltype(&COMPARATOR::cmp), K, const NodeType*>;

  // Due to a bug in older GCC versions with static templated constexpr data members (see GCC PR 71954),
  // we have to express this trait through a struct instead of a constexpr variable directly.
  template<typename, typename = void>
  struct HasNodeVerifierImpl : std::false_type {};

  template <typename CMP>
  struct HasNodeVerifierImpl<CMP, std::void_t<decltype(&CMP::less_than)>>
      : std::bool_constant<std::is_invocable_r_v<bool, decltype(&CMP::less_than), const NodeType*, const NodeType*>> {};

  static constexpr bool HasNodeVerifier = HasNodeVerifierImpl<COMPARATOR>::value;

  RBTreeOrdering cmp(const K& a, const NodeType* b) const;

  bool less_than(const NodeType* a, const NodeType* b) const;

  void assert_key_leq(K a, K b) const;

  // True if node is black (nil nodes count as black)
  static inline bool is_black(const IntrusiveRBNode* node);

  static inline bool is_red(const IntrusiveRBNode* node);

  void fix_insert_violations(IntrusiveRBNode* node);

  void remove_black_leaf(IntrusiveRBNode* node);

  // Assumption: node has at most one child. Two children is handled in `remove_at_cursor()`
  void remove_from_tree(IntrusiveRBNode* node);

  struct empty_verifier {
    bool operator()(const NodeType* n) const;
  };

  template <typename NODE_VERIFIER, typename USER_VERIFIER>
  void verify_self(NODE_VERIFIER verifier, const USER_VERIFIER& extra_verifier) const;

  struct default_printer {
    void operator()(outputStream* st, const NodeType* n, int depth) const;
  };

  template <typename PRINTER>
  void print_node_on(outputStream* st, int depth, const NodeType* n, const PRINTER& node_printer) const;

public:
  NONCOPYABLE(AbstractRBTree);

  AbstractRBTree();

  size_t size() const;

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
  NodeType* find_node(const K& key, const NodeType* hint_node = nullptr);
  NodeType* find_node(const K& key, const NodeType* hint_node = nullptr) const;

  // Inserts the given node into the tree.
  void insert(const K& key, NodeType* node, const NodeType* hint_node = nullptr);

  // Removes the given node from the tree.
  void remove(NodeType* node);

  // Finds the node with the closest key <= the given key.
  // If no node is found, null is returned instead.
  NodeType* closest_leq(const K& key);
  NodeType* closest_leq(const K& key) const;

  // Finds the node with the closest key > the given key.
  // If no node is found, null is returned instead.
  NodeType* closest_gt(const K& key);
  NodeType* closest_gt(const K& key) const;

  // Finds the node with the closest key >= the given key.
  // If no node is found, null is returned instead.
  NodeType* closest_ge(const K& key);
  NodeType* closest_ge(const K& key) const;

  // Returns leftmost node, nullptr if tree is empty.
  // If COMPARATOR::cmp(a, b) behaves canonically (positive value for a > b), this will the smallest key value.
  NodeType* leftmost();
  const NodeType* leftmost() const;

  // Returns rightmost node, nullptr if tree is empty.
  // If COMPARATOR::cmp(a, b) behaves canonically (positive value for a > b), this will the largest key value.
  NodeType* rightmost();
  const NodeType* rightmost() const;

  struct Range {
    NodeType* start;
    NodeType* end;
    Range(NodeType* start, NodeType* end)
    : start(start), end(end) {}
  };

  // Return the range [start, end)
  // where start->key() <= addr < end->key().
  // Failure to find the range leads to start and/or end being null.
  Range find_enclosing_range(K key) const;

  // Visit all RBNodes in ascending order, calling f on each node.
  // If f returns `true` the iteration continues, otherwise it is stopped at the current node.
  template <typename F>
  void visit_in_order(F f) const;

  template <typename F>
  void visit_in_order(F f);

  // Visit all RBNodes in ascending order whose keys are in range [from, to], calling f on each node.
  // If f returns `true` the iteration continues, otherwise it is stopped at the current node.
  template <typename F>
  void visit_range_in_order(const K& from, const K& to, F f) const;

  template <typename F>
  void visit_range_in_order(const K &from, const K &to, F f);

  // Verifies that the tree is correct and holds rb-properties
  // If not using a key comparator (when using IntrusiveRBTree for example),
  // a function `less_than` must exist in COMPARATOR (see top of file).
  // Accepts an optional callable `bool extra_verifier(const Node* n)`.
  // This should return true if the node is valid.
  // If provided, each node is also verified through this callable.
  template <typename USER_VERIFIER = empty_verifier>
  void verify_self(const USER_VERIFIER& extra_verifier = USER_VERIFIER()) const;

  // Accepts an optional printing callable `void node_printer(outputStream* st, const Node* n, int depth)`.
  // If provided, each node is printed through this callable rather than the default `print_on`.
  template <typename PRINTER = default_printer>
  void print_on(outputStream* st, const PRINTER& node_printer = PRINTER()) const;

};

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
class RBTree : public AbstractRBTree<K, RBNode<K, V>, COMPARATOR> {
  friend class RBTreeTest;
  typedef AbstractRBTree<K, RBNode<K, V>, COMPARATOR> BaseType;

  ALLOCATOR _allocator;

public:
  template<typename... AllocArgs>
  RBTree(AllocArgs... alloc_args);
  ~RBTree();
  NONCOPYABLE(RBTree);

  bool copy_into(RBTree& other) const;

  typedef typename BaseType::Cursor Cursor;
  using BaseType::cursor;
  using BaseType::insert_at_cursor;
  using BaseType::remove_at_cursor;
  using BaseType::next;
  using BaseType::prev;

  void replace_at_cursor(RBNode<K, V>* new_node, const Cursor& node_cursor);

  RBNode<K, V>* allocate_node(const K& key);
  RBNode<K, V>* allocate_node(const K& key, const V& val);

  void free_node(RBNode<K, V>* node);

  // Inserts a node with the given key/value into the tree,
  // if the key already exist, the value is updated instead.
  // Returns false if and only if allocation of a new node failed.
  bool upsert(const K& key, const V& val, const RBNode<K, V>* hint_node = nullptr);

  // Finds the value of the node associated with the given key.
  V* find(const K& key);
  V* find(const K& key) const;

  void remove(RBNode<K, V>* node);

  // Removes the node with the given key from the tree if it exists.
  // Returns true if the node was successfully removed, false otherwise.
  bool remove(const K& key);

  // Removes all existing nodes from the tree.
  void remove_all();
};

template <MemTag mem_tag, AllocFailType strategy>
class RBTreeCHeapAllocator {
public:
  void* allocate(size_t sz);
  void free(void* ptr);
};

template <AllocFailType strategy>
class RBTreeArenaAllocator {
  Arena* _arena;
public:
  RBTreeArenaAllocator(Arena* arena);
  void* allocate(size_t sz);
  void free(void* ptr);
};

template <AllocFailType strategy>
class RBTreeResourceAreaAllocator {
  ResourceArea* _rarea;
public:
  RBTreeResourceAreaAllocator(ResourceArea* rarea);
  void* allocate(size_t sz);
  void free(void* ptr);
};



template <typename K, typename V, typename COMPARATOR, MemTag mem_tag, AllocFailType strategy = AllocFailStrategy::EXIT_OOM>
using RBTreeCHeap = RBTree<K, V, COMPARATOR, RBTreeCHeapAllocator<mem_tag, strategy>>;

template <typename K, typename V, typename COMPARATOR, AllocFailType strategy = AllocFailStrategy::EXIT_OOM>
using RBTreeArena = RBTree<K, V, COMPARATOR, RBTreeArenaAllocator<strategy>>;

template <typename K, typename V, typename COMPARATOR, AllocFailType strategy = AllocFailStrategy::EXIT_OOM>
using RBTreeResourceArea = RBTree<K, V, COMPARATOR, RBTreeResourceAreaAllocator<strategy>>;

template <typename K, typename COMPARATOR>
using IntrusiveRBTree = AbstractRBTree<K, IntrusiveRBNode, COMPARATOR>;

#endif // SHARE_UTILITIES_RBTREE_HPP
