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

#ifndef SHARE_UTILITIES_RBTREE_INLINE_HPP
#define SHARE_UTILITIES_RBTREE_INLINE_HPP

#include "utilities/rbTree.hpp"

#include "memory/allocation.hpp"
#include "memory/arena.hpp"
#include "memory/resourceArea.hpp"
#include "metaprogramming/enableIf.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/powerOfTwo.hpp"

inline IntrusiveRBNode::IntrusiveRBNode()
  : _parent(0), _left(nullptr), _right(nullptr) DEBUG_ONLY(COMMA _visited(false)) {}

inline bool IntrusiveRBNode::is_black() const {
  return (_parent & 0x1) != 0;
}

inline bool IntrusiveRBNode::is_red() const {
  return (_parent & 0x1) == 0;
}

inline void IntrusiveRBNode::set_black() {
  _parent |= 0x1;
}
inline void IntrusiveRBNode::set_red() {
  _parent &= ~0x1;
}

inline IntrusiveRBNode* IntrusiveRBNode::parent() const {
  return reinterpret_cast<IntrusiveRBNode*>(_parent & ~0x1);
}

inline void IntrusiveRBNode::set_parent(IntrusiveRBNode* new_parent) {
  _parent = (_parent & 0x1) | reinterpret_cast<uintptr_t>(new_parent);
}

inline bool IntrusiveRBNode::is_right_child() const {
  return parent() != nullptr && parent()->_right == this;
}

inline bool IntrusiveRBNode::is_left_child() const {
  return parent() != nullptr && parent()->_left == this;
}

inline void IntrusiveRBNode::replace_child(IntrusiveRBNode* old_child, IntrusiveRBNode* new_child) {
  if (_left == old_child) {
    _left = new_child;
  } else if (_right == old_child) {
    _right = new_child;
  } else {
    ShouldNotReachHere();
  }
}

inline IntrusiveRBNode* IntrusiveRBNode::rotate_left() {
  // This node down, right child up
  IntrusiveRBNode* old_right = _right;

  _right = old_right->_left;
  if (_right != nullptr) {
    _right->set_parent(this);
  }

  old_right->set_parent(parent());
  if (parent() != nullptr) {
    parent()->replace_child(this, old_right);
  }

  old_right->_left = this;
  set_parent(old_right);

  return old_right;
}

inline IntrusiveRBNode* IntrusiveRBNode::rotate_right() {
  // This node down, left child up
  IntrusiveRBNode* old_left = _left;

  _left = old_left->_right;
  if (_left != nullptr) {
    _left->set_parent(this);
  }

  old_left->set_parent(parent());
  if (parent() != nullptr) {
    parent()->replace_child(this, old_left);
  }

  old_left->_right = this;
  set_parent(old_left);

  return old_left;
}

inline const IntrusiveRBNode* IntrusiveRBNode::prev() const {
  const IntrusiveRBNode* node = this;
  if (_left != nullptr) { // left subtree exists
    node = _left;
    while (node->_right != nullptr) {
      node = node->_right;
    }
    return node;
  }

  while (node != nullptr && node->is_left_child()) {
    node = node->parent();
  }
  return node->parent();
}

inline const IntrusiveRBNode* IntrusiveRBNode::next() const {
  const IntrusiveRBNode* node = this;
  if (_right != nullptr) { // right subtree exists
    node = _right;
    while (node->_left != nullptr) {
      node = node->_left;
    }
    return node;
  }

  while (node != nullptr && node->is_right_child()) {
    node = node->parent();
  }
  return node->parent();
}

inline IntrusiveRBNode* IntrusiveRBNode::prev() {
  return const_cast<IntrusiveRBNode*>(static_cast<const IntrusiveRBNode*>(this)->prev());
}

inline IntrusiveRBNode* IntrusiveRBNode::next() {
  return const_cast<IntrusiveRBNode*>(static_cast<const IntrusiveRBNode*>(this)->next());
}

template <typename NodeType, typename NODE_VERIFIER, typename USER_VERIFIER>
inline void IntrusiveRBNode::verify(
    size_t& num_nodes, size_t& black_nodes_until_leaf, size_t& shortest_leaf_path, size_t& longest_leaf_path,
    size_t& tree_depth, bool expect_visited, NODE_VERIFIER verifier, const USER_VERIFIER& extra_verifier) const {
  bool extra_verifier_result = extra_verifier(static_cast<const NodeType*>(this));
  assert(extra_verifier_result, "user provided verifier failed");
  assert(expect_visited != _visited, "node already visited");
  DEBUG_ONLY(_visited = !_visited);

  size_t num_black_nodes_left = 0;
  size_t shortest_leaf_path_left = 0;
  size_t longest_leaf_path_left = 0;
  size_t tree_depth_left = 0;

  if (_left != nullptr) {
    assert(verifier((NodeType*)_left, (NodeType*)this), "left child must compare strictly less than parent");
    if (_right == nullptr) {
      assert(is_black() && _left->is_red(), "if one child it must be red and node black");
    }
    assert(is_black() || _left->is_black(), "2 red nodes in a row");
    assert(_left->parent() == this, "pointer mismatch");
    _left->verify<NodeType>(num_nodes, num_black_nodes_left, shortest_leaf_path_left,
                  longest_leaf_path_left, tree_depth_left, expect_visited, verifier, extra_verifier);
  }

  size_t num_black_nodes_right = 0;
  size_t shortest_leaf_path_right = 0;
  size_t longest_leaf_path_right = 0;
  size_t tree_depth_right = 0;

  if (_right != nullptr) {
    assert(verifier((NodeType*)this, (NodeType*)_right), "right child must compare strictly greater than parent");
    if (_left == nullptr) {
      assert(is_black() && _right->is_red(), "if one child it must be red and node black");
    }
    assert(is_black() || _left->is_black(), "2 red nodes in a row");
    assert(_right->parent() == this, "pointer mismatch");
    _right->verify<NodeType>(num_nodes, num_black_nodes_right, shortest_leaf_path_right,
                   longest_leaf_path_right, tree_depth_right, expect_visited, verifier, extra_verifier);
  }

  shortest_leaf_path = MAX2(longest_leaf_path_left, longest_leaf_path_right);
  longest_leaf_path = MAX2(longest_leaf_path_left, longest_leaf_path_right);

  assert(shortest_leaf_path <= longest_leaf_path && longest_leaf_path <= shortest_leaf_path * 2,
         "tree imbalanced, shortest path: %zu longest: %zu", shortest_leaf_path, longest_leaf_path);
  assert(num_black_nodes_left == num_black_nodes_right,
         "number of black nodes in left/right subtree should match");

  num_nodes++;
  tree_depth = 1 + MAX2(tree_depth_left, tree_depth_right);

  shortest_leaf_path++;
  longest_leaf_path++;

  black_nodes_until_leaf = num_black_nodes_left;
  if (is_black()) {
    black_nodes_until_leaf++;
  }

}

template <typename K, typename V>
inline const K& RBNode<K, V>::key() const {
  return _key;
}

template <typename K, typename V>
inline V& RBNode<K, V>::val() {
  return _value;
}

template <typename K, typename V>
inline const V& RBNode<K, V>::val() const {
  return _value;
}

template <typename K, typename V>
inline void RBNode<K, V>::set_val(const V& v) {
  _value = v;
}

template <typename K, typename V>
inline RBNode<K, V>::RBNode() {}

template <typename K, typename V>
inline RBNode<K, V>::RBNode(const K& key) : IntrusiveRBNode(), _key(key) {}

template <typename K, typename V>
inline RBNode<K, V>::RBNode(const K& key, const V& val) : IntrusiveRBNode(), _key(key), _value(val) {}

template <typename K, typename V>
inline const RBNode<K, V>* RBNode<K, V>::prev() const {
  return static_cast<const RBNode<K, V>*>(IntrusiveRBNode::prev());
}

template <typename K, typename V>
inline const RBNode<K, V>* RBNode<K, V>::next() const {
  return static_cast<const RBNode<K, V>*>(IntrusiveRBNode::next());
}

template <typename K, typename V>
inline RBNode<K, V>* RBNode<K, V>::prev() {
  return static_cast<RBNode<K, V>*>(IntrusiveRBNode::prev());
}

template <typename K, typename V>
inline RBNode<K, V>* RBNode<K, V>::next() {
  return static_cast<RBNode<K, V>*>(IntrusiveRBNode::next());
}

template <typename K, typename NodeType, typename COMPARATOR>
inline AbstractRBTree<K, NodeType, COMPARATOR>::Cursor::Cursor()
  : _insert_location(nullptr), _parent(nullptr) {}

template <typename K, typename NodeType, typename COMPARATOR>
inline AbstractRBTree<K, NodeType, COMPARATOR>::Cursor::Cursor(NodeType** insert_location, NodeType* parent)
  : _insert_location(insert_location), _parent(parent) {}

template <typename K, typename NodeType, typename COMPARATOR>
inline AbstractRBTree<K, NodeType, COMPARATOR>::Cursor::Cursor(NodeType* const* insert_location, NodeType* parent)
  : _insert_location(const_cast<NodeType**>(insert_location)), _parent(parent) {}

template <typename K, typename NodeType, typename COMPARATOR>
inline bool AbstractRBTree<K, NodeType, COMPARATOR>::Cursor::valid() const {
  return _insert_location != nullptr;
}

template <typename K, typename NodeType, typename COMPARATOR>
inline bool AbstractRBTree<K, NodeType, COMPARATOR>::Cursor::found() const {
  return *_insert_location != nullptr;
}

template <typename K, typename NodeType, typename COMPARATOR>
inline NodeType* AbstractRBTree<K, NodeType, COMPARATOR>::Cursor::node() {
  return _insert_location == nullptr ? nullptr : *_insert_location;
}

template <typename K, typename NodeType, typename COMPARATOR>
inline NodeType* AbstractRBTree<K, NodeType, COMPARATOR>::Cursor::node() const {
  return _insert_location == nullptr ? nullptr : *_insert_location;
}

template <typename K, typename NodeType, typename COMPARATOR>
inline RBTreeOrdering AbstractRBTree<K, NodeType, COMPARATOR>::cmp(const K& a, const NodeType* b) const {
  if constexpr (HasNodeComparator) {
    return COMPARATOR::cmp(a, b);
  } else if constexpr (HasKeyComparator) {
    return COMPARATOR::cmp(a, b->key());
  }
}

template <typename K, typename NodeType, typename COMPARATOR>
inline bool AbstractRBTree<K, NodeType, COMPARATOR>::less_than(const NodeType* a, const NodeType* b) const {
  if constexpr (HasNodeVerifier) {
    return COMPARATOR::less_than(a, b);
  } else {
    return true;
  }
}

template <typename K, typename NodeType, typename COMPARATOR>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::assert_key_leq(K a, K b) const {
  if constexpr (HasKeyComparator) { // Cannot assert if no key comparator exist.
    assert(COMPARATOR::cmp(a, b) != RBTreeOrdering::GT, "key a must be less or equal to key b");
  }
}

template <typename K, typename NodeType, typename COMPARATOR>
inline bool AbstractRBTree<K, NodeType, COMPARATOR>::is_black(const IntrusiveRBNode* node) {
  return node == nullptr || node->is_black();
}

template <typename K, typename NodeType, typename COMPARATOR>
inline bool AbstractRBTree<K, NodeType, COMPARATOR>::is_red(const IntrusiveRBNode* node) {
  return node != nullptr && node->is_red();
}

template <typename K, typename NodeType, typename COMPARATOR>
inline bool AbstractRBTree<K, NodeType, COMPARATOR>::empty_verifier::operator()(const NodeType* n) const {
  return true;
}

template <typename K, typename NodeType, typename COMPARATOR>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::default_printer::operator()(outputStream* st, const NodeType* n, int depth) const {
  n->print_on(st, depth);
}

template <typename K, typename NodeType, typename COMPARATOR>
inline AbstractRBTree<K, NodeType, COMPARATOR>::AbstractRBTree()
    : _num_nodes(0), _root(nullptr) DEBUG_ONLY(COMMA _expected_visited(false)) {
  static_assert(std::is_trivially_destructible<K>::value, "key type must be trivially destructable");
  static_assert(HasKeyComparator || HasNodeComparator, "comparator must be of correct type");
}

template <typename K, typename NodeType, typename COMPARATOR>
inline size_t AbstractRBTree<K, NodeType, COMPARATOR>::size() const {
  return _num_nodes;
}

template <typename K, typename NodeType, typename COMPARATOR>
inline const typename AbstractRBTree<K, NodeType, COMPARATOR>::Cursor
AbstractRBTree<K, NodeType, COMPARATOR>::cursor(const K& key, const NodeType* hint_node) const {
  IntrusiveRBNode* parent = nullptr;
  IntrusiveRBNode* const* insert_location = &_root;

  if (hint_node != nullptr) {
    const RBTreeOrdering hint_cmp = cmp(key, hint_node);
    while (hint_node->parent() != nullptr) {
      const RBTreeOrdering parent_cmp = cmp(key, (NodeType*)hint_node->parent());
      // Move up until the parent would put us on the other side of the key.
      // Meaning we are in the correct subtree.
      if ((parent_cmp != RBTreeOrdering::GT && hint_cmp == RBTreeOrdering::LT) ||
          (parent_cmp != RBTreeOrdering::LT    && hint_cmp == RBTreeOrdering::GT)) {
        hint_node = (NodeType*)hint_node->parent();
      } else {
        break;
      }
    }

    if (hint_node->is_left_child()) {
      insert_location = &hint_node->parent()->_left;
    } else if (hint_node->is_right_child()) {
      insert_location = &hint_node->parent()->_right;
    }
  }

  while (*insert_location != nullptr) {
    NodeType* curr = (NodeType*)*insert_location;
    const RBTreeOrdering key_cmp_k = cmp(key, curr);

    if (key_cmp_k == RBTreeOrdering::EQ) {
      break;
    }

    parent = *insert_location;
    if (key_cmp_k == RBTreeOrdering::LT) {
      insert_location = &curr->_left;
    } else {
      insert_location = &curr->_right;
    }
  }

  return Cursor((NodeType**)insert_location, (NodeType*)parent);
}

template <typename K, typename NodeType, typename COMPARATOR>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::insert_at_cursor(NodeType* node, const Cursor& node_cursor) {
  assert(node_cursor.valid() && !node_cursor.found(), "must be");
  _num_nodes++;

  *node_cursor._insert_location = node;

  node->set_parent(node_cursor._parent);
  node->set_red();
  node->_left = nullptr;
  node->_right = nullptr;

  DEBUG_ONLY(node->_visited = _expected_visited);

  if (node_cursor._parent == nullptr) {
    return;
  }

  fix_insert_violations(node);
}

template <typename K, typename NodeType, typename COMPARATOR>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::fix_insert_violations(IntrusiveRBNode* node) {
  if (node->is_black()) { // node's value was updated
    return;               // Tree is already correct
  }

  IntrusiveRBNode* parent = node->parent();
  while (parent != nullptr && parent->is_red()) {
    // Node and parent are both red, creating a red-violation

    IntrusiveRBNode* grandparent = parent->parent();
    if (grandparent == nullptr) { // Parent is the tree root
      assert(parent == _root, "parent must be root");
      parent->set_black(); // Color parent black to eliminate the red-violation
      return;
    }

    IntrusiveRBNode* uncle = parent->is_left_child() ? grandparent->_right : grandparent->_left;
    if (is_black(uncle)) { // Parent is red, uncle is black
      // Rotate the parent to the position of the grandparent
      if (parent->is_left_child()) {
        if (node->is_right_child()) { // Node is an "inner" node
          // Rotate and swap node and parent to make it an "outer" node
          parent->rotate_left();
          parent = node;
        }
        grandparent->rotate_right(); // Rotate the parent to the position of the grandparent
      } else if (parent->is_right_child()) {
        if (node->is_left_child()) { // Node is an "inner" node
          // Rotate and swap node and parent to make it an "outer" node
          parent->rotate_right();
          parent = node;
        }
        grandparent->rotate_left(); // Rotate the parent to the position of the grandparent
      }

      // Swap parent and grandparent colors to eliminate the red-violation
      parent->set_black();
      grandparent->set_red();

      if (_root == grandparent) {
        _root = parent;
      }

      return;
    }

    // Parent and uncle are both red
    // Paint both black, paint grandparent red to not create a black-violation
    parent->set_black();
    uncle->set_black();
    grandparent->set_red();

    // Move up two levels to check for new potential red-violation
    node = grandparent;
    parent = grandparent->parent();
  }
}

template <typename K, typename NodeType, typename COMPARATOR>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::remove_black_leaf(IntrusiveRBNode* node) {
  // Black node removed, balancing needed
  IntrusiveRBNode* parent = node->parent();
  while (parent != nullptr) {
    // Sibling must exist. If it did not, node would need to be red to not break
    // tree properties, and could be trivially removed before reaching here
    IntrusiveRBNode* sibling = node->is_left_child() ? parent->_right : parent->_left;
    if (is_red(sibling)) { // Sibling red, parent and nephews must be black
      assert(is_black(parent), "parent must be black");
      assert(is_black(sibling->_left), "nephew must be black");
      assert(is_black(sibling->_right), "nephew must be black");
      // Swap parent and sibling colors
      parent->set_red();
      sibling->set_black();

      // Rotate parent down and sibling up
      if (node->is_left_child()) {
        parent->rotate_left();
        sibling = parent->_right;
      } else {
        parent->rotate_right();
        sibling = parent->_left;
      }

      if (_root == parent) {
        _root = parent->parent();
      }
      // Further balancing needed
    }

    IntrusiveRBNode* close_nephew = node->is_left_child() ? sibling->_left : sibling->_right;
    IntrusiveRBNode* distant_nephew = node->is_left_child() ? sibling->_right : sibling->_left;
    if (is_red(distant_nephew) || is_red(close_nephew)) {
      if (is_black(distant_nephew)) { // close red, distant black
        // Rotate sibling down and inner nephew up
        if (node->is_left_child()) {
          sibling->rotate_right();
        } else {
          sibling->rotate_left();
        }

        distant_nephew = sibling;
        sibling = close_nephew;

        distant_nephew->set_red();
        sibling->set_black();
      }

      // Distant nephew red
      // Rotate parent down and sibling up
      if (node->is_left_child()) {
        parent->rotate_left();
      } else {
        parent->rotate_right();
      }
      if (_root == parent) {
        _root = sibling;
      }

      // Swap parent and sibling colors
      if (parent->is_black()) {
        sibling->set_black();
      } else {
        sibling->set_red();
      }
      parent->set_black();

      // Color distant nephew black to restore black balance
      distant_nephew->set_black();
      return;
    }

    if (is_red(parent)) { // parent red, sibling and nephews black
      // Swap parent and sibling colors to restore black balance
      sibling->set_red();
      parent->set_black();
      return;
    }

    // Parent, sibling, and both nephews black
    // Color sibling red and move up one level
    sibling->set_red();
    node = parent;
    parent = node->parent();
  }
}

template <typename K, typename NodeType, typename COMPARATOR>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::remove_from_tree(IntrusiveRBNode* node) {
  IntrusiveRBNode* parent = node->parent();
  IntrusiveRBNode* left = node->_left;
  IntrusiveRBNode* right = node->_right;
  if (left != nullptr) { // node has a left only-child
    // node must be black, and child red, otherwise a black-violation would
    // exist Remove node and color the child black.
    assert(right == nullptr, "right must be nullptr");
    assert(is_black(node), "node must be black");
    assert(is_red(left), "child must be red");
    left->set_black();
    left->set_parent(parent);
    if (parent == nullptr) {
      assert(node == _root, "node must be root");
      _root = left;
    } else {
      parent->replace_child(node, left);
    }
  } else if (right != nullptr) { // node has a right only-child
    // node must be black, and child red, otherwise a black-violation would
    // exist Remove node and color the child black.
    assert(left == nullptr, "left must be nullptr");
    assert(is_black(node), "node must be black");
    assert(is_red(right), "child must be red");
    right->set_black();
    right->set_parent(parent);
    if (parent == nullptr) {
      assert(node == _root, "node must be root");
      _root = right;
    } else {
      parent->replace_child(node, right);
    }
  } else {               // node has no children
    if (node == _root) { // Tree empty
      _root = nullptr;
    } else {
      if (is_black(node)) {
        // Removed node is black, creating a black imbalance
        remove_black_leaf(node);
      }
      parent->replace_child(node, nullptr);
    }
  }
}

template <typename K, typename NodeType, typename COMPARATOR>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::remove_at_cursor(const Cursor& node_cursor) {
  assert(node_cursor.valid() && node_cursor.found(), "must be");
  _num_nodes--;

  IntrusiveRBNode* node = node_cursor.node();

  if (node->_left != nullptr && node->_right != nullptr) { // node has two children
    // Swap place with the in-order successor and delete there instead
    IntrusiveRBNode* curr = node->_right;
    while (curr->_left != nullptr) {
      curr = curr->_left;
    }

    if (_root == node) _root = curr;

    swap(curr->_left, node->_left);
    swap(curr->_parent, node->_parent); // Swaps parent and color

    // If node is curr's parent, parent and right pointers become invalid
    if (node->_right == curr) {
      node->_right = curr->_right;
      node->set_parent(curr);
      curr->_right = node;
    } else {
      swap(curr->_right, node->_right);
      node->parent()->replace_child(curr, node);
      curr->_right->set_parent(curr);
    }

    if (curr->parent() != nullptr) curr->parent()->replace_child(node, curr);
    curr->_left->set_parent(curr);


    if (node->_left != nullptr) node->_left->set_parent(node);
    if (node->_right != nullptr) node->_right->set_parent(node);
  }

  remove_from_tree(node);
}

template <typename K, typename NodeType, typename COMPARATOR>
inline const typename AbstractRBTree<K, NodeType, COMPARATOR>::Cursor
AbstractRBTree<K, NodeType, COMPARATOR>::cursor(const NodeType* node) const {
  if (node == nullptr) {
    return Cursor();
  }

  if (node->parent() == nullptr) {
    return Cursor((NodeType**)&_root, nullptr);
  }

  IntrusiveRBNode* parent = node->parent();
  IntrusiveRBNode** insert_location =
      node->is_left_child() ? &parent->_left : &parent->_right;
  return Cursor((NodeType**)insert_location, (NodeType*)parent);
}

template <typename K, typename NodeType, typename COMPARATOR>
inline const typename AbstractRBTree<K, NodeType, COMPARATOR>::Cursor
AbstractRBTree<K, NodeType, COMPARATOR>::next(const Cursor& node_cursor) const {
  if (node_cursor.found()) {
    return cursor(node_cursor.node()->next());
  }

  if (node_cursor._parent == nullptr) { // Tree is empty
    return Cursor();
  }

  // Pointing to non-existant node
  if ((NodeType**)&node_cursor._parent->_left == node_cursor._insert_location) { // Left child, parent is next
    return cursor(node_cursor._parent);
  }

  return cursor(node_cursor._parent->next()); // Right child, parent's next is also node's next
}

template <typename K, typename NodeType, typename COMPARATOR>
inline const typename AbstractRBTree<K, NodeType, COMPARATOR>::Cursor
AbstractRBTree<K, NodeType, COMPARATOR>::prev(const Cursor& node_cursor) const {
  if (node_cursor.found()) {
    return cursor(node_cursor.node()->prev());
  }

  if (node_cursor._parent == nullptr) { // Tree is empty
    return Cursor();
  }

  // Pointing to non-existant node
  if ((NodeType**)&node_cursor._parent->_right == node_cursor._insert_location) { // Right child, parent is prev
    return cursor(node_cursor._parent);
  }

  return cursor(node_cursor._parent->prev()); // Left child, parent's prev is also node's prev
}

template <typename K, typename NodeType, typename COMPARATOR>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::replace_at_cursor(NodeType* new_node, const Cursor& node_cursor) {
  assert(node_cursor.valid() && node_cursor.found(), "must be");
  NodeType* old_node = node_cursor.node();
  if (old_node == new_node) {
    return;
  }

  *node_cursor._insert_location = new_node;
  new_node->_parent = old_node->_parent;

  if (new_node->is_left_child()) {
    assert(less_than(static_cast<const NodeType*>(new_node), static_cast<const NodeType*>(new_node->parent())), "new node not < parent");
  } else if (new_node->is_right_child()) {
    assert(less_than(static_cast<const NodeType*>(new_node->parent()), static_cast<const NodeType*>(new_node)), "new node not > parent");
  }

  new_node->_left = old_node->_left;
  new_node->_right = old_node->_right;
  if (new_node->_left != nullptr) {
    assert(less_than(static_cast<const NodeType*>(new_node->_left), static_cast<const NodeType*>(new_node)), "left child not < new node");
    new_node->_left->set_parent(new_node);
  }
  if (new_node->_right != nullptr) {
    assert(less_than(static_cast<const NodeType*>(new_node), static_cast<const NodeType*>(new_node->_right)), "right child not > new node");
    new_node->_right->set_parent(new_node);
  }

  DEBUG_ONLY(new_node->_visited = old_node->_visited);
}

template <typename K, typename NodeType, typename COMPARATOR>
inline typename AbstractRBTree<K, NodeType, COMPARATOR>::Cursor
AbstractRBTree<K, NodeType, COMPARATOR>::cursor(const K& key, const NodeType* hint_node) {
  return static_cast<const AbstractRBTree<K, NodeType, COMPARATOR>*>(this)->cursor(key, hint_node);
}

template <typename K, typename NodeType, typename COMPARATOR>
inline typename AbstractRBTree<K, NodeType, COMPARATOR>::Cursor
AbstractRBTree<K, NodeType, COMPARATOR>::cursor(const NodeType* node) {
  return static_cast<const AbstractRBTree<K, NodeType, COMPARATOR>*>(this)->cursor(node);
}

template <typename K, typename NodeType, typename COMPARATOR>
inline typename AbstractRBTree<K, NodeType, COMPARATOR>::Cursor
AbstractRBTree<K, NodeType, COMPARATOR>::next(const Cursor& node_cursor) {
  return static_cast<const AbstractRBTree<K, NodeType, COMPARATOR>*>(this)->next(node_cursor);
}

template <typename K, typename NodeType, typename COMPARATOR>
inline typename AbstractRBTree<K, NodeType, COMPARATOR>::Cursor
AbstractRBTree<K, NodeType, COMPARATOR>::prev(const Cursor& node_cursor) {
  return static_cast<const AbstractRBTree<K, NodeType, COMPARATOR>*>(this)->prev(node_cursor);
}

template <typename K, typename NodeType, typename COMPARATOR>
inline NodeType* AbstractRBTree<K, NodeType, COMPARATOR>::find_node(const K& key, const NodeType* hint_node) const {
  Cursor node_cursor = cursor(key, hint_node);
  return node_cursor.node();
}

template <typename K, typename NodeType, typename COMPARATOR>
inline NodeType* AbstractRBTree<K, NodeType, COMPARATOR>::find_node(const K& key, const NodeType* hint_node) {
  Cursor node_cursor = cursor(key, hint_node);
  return node_cursor.node();
}

template <typename K, typename NodeType, typename COMPARATOR>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::insert(const K& key, NodeType* node, const NodeType* hint_node) {
  Cursor node_cursor = cursor(key, hint_node);
  insert_at_cursor(node, node_cursor);
}

template <typename K, typename NodeType, typename COMPARATOR>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::remove(NodeType* node) {
  Cursor node_cursor = cursor(node);
  remove_at_cursor(node_cursor);
}

template <typename K, typename NodeType, typename COMPARATOR>
inline NodeType* AbstractRBTree<K, NodeType, COMPARATOR>::closest_leq(const K& key) const {
  Cursor node_cursor = cursor(key);
  return node_cursor.found() ? node_cursor.node() : prev(node_cursor).node();
}

template <typename K, typename NodeType, typename COMPARATOR>
inline NodeType* AbstractRBTree<K, NodeType, COMPARATOR>::closest_leq(const K& key) {
  Cursor node_cursor = cursor(key);
  return node_cursor.found() ? node_cursor.node() : prev(node_cursor).node();
}

template <typename K, typename NodeType, typename COMPARATOR>
inline NodeType* AbstractRBTree<K, NodeType, COMPARATOR>::closest_gt(const K& key) const {
  Cursor node_cursor = cursor(key);
  return next(node_cursor).node();
}

template <typename K, typename NodeType, typename COMPARATOR>
inline NodeType* AbstractRBTree<K, NodeType, COMPARATOR>::closest_gt(const K& key) {
  Cursor node_cursor = cursor(key);
  return next(node_cursor).node();
}

template <typename K, typename NodeType, typename COMPARATOR>
inline NodeType* AbstractRBTree<K, NodeType, COMPARATOR>::closest_ge(const K& key) const {
  Cursor node_cursor = cursor(key);
  return node_cursor.found() ? node_cursor.node() : next(node_cursor).node();
}

template <typename K, typename NodeType, typename COMPARATOR>
inline NodeType* AbstractRBTree<K, NodeType, COMPARATOR>::closest_ge(const K& key) {
  Cursor node_cursor = cursor(key);
  return node_cursor.found() ? node_cursor.node() : next(node_cursor).node();
}

template <typename K, typename NodeType, typename COMPARATOR>
inline const NodeType* AbstractRBTree<K, NodeType, COMPARATOR>::leftmost() const {
  IntrusiveRBNode* n = _root, *n2 = nullptr;
  while (n != nullptr) {
    n2 = n;
    n = n->_left;
  }
  return static_cast<const NodeType*>(n2);
}

template <typename K, typename NodeType, typename COMPARATOR>
inline const NodeType* AbstractRBTree<K, NodeType, COMPARATOR>::rightmost() const {
  IntrusiveRBNode* n = _root, *n2 = nullptr;
  while (n != nullptr) {
    n2 = n;
    n = n->_right;
  }
  return static_cast<const NodeType*>(n2);
}

template <typename K, typename NodeType, typename COMPARATOR>
inline NodeType* AbstractRBTree<K, NodeType, COMPARATOR>::leftmost() {
  return const_cast<NodeType*>(static_cast<const AbstractRBTree<K, NodeType, COMPARATOR>*>(this)->leftmost());
}

template <typename K, typename NodeType, typename COMPARATOR>
inline NodeType* AbstractRBTree<K, NodeType, COMPARATOR>::rightmost() {
  return const_cast<NodeType*>(static_cast<const AbstractRBTree<K, NodeType, COMPARATOR>*>(this)->rightmost());
}

template <typename K, typename NodeType, typename COMPARATOR>
inline typename AbstractRBTree<K, NodeType, COMPARATOR>::Range
AbstractRBTree<K, NodeType, COMPARATOR>::find_enclosing_range(K key) const {
  NodeType* start = closest_leq(key);
  NodeType* end = closest_gt(key);
  return Range(start, end);
}

template <typename K, typename NodeType, typename COMPARATOR>
template <typename F>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::visit_in_order(F f) const {
  const NodeType* node = leftmost();
  while (node != nullptr) {
    if (!f(node)) {
      return;
    }
    node = node->next();
  }
}

template <typename K, typename NodeType, typename COMPARATOR>
template <typename F>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::visit_in_order(F f) {
  NodeType* node = leftmost();
  while (node != nullptr) {
    if (!f(node)) {
      return;
    }
    node = node->next();
  }
}

template <typename K, typename NodeType, typename COMPARATOR>
template <typename F>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::visit_range_in_order(const K& from, const K& to, F f) const {
  assert_key_leq(from, to);
  if (_root == nullptr) {
    return;
  }

  Cursor cursor_start = cursor(from);
  Cursor cursor_end = cursor(to);
  const NodeType* start = cursor_start.found() ? cursor_start.node() : next(cursor_start).node();
  const NodeType* end = next(cursor_end).node();

  while (start != end) {
    if (!f(start)) {
      return;
    }
    start = start->next();
  }
}

template <typename K, typename NodeType, typename COMPARATOR>
template <typename F>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::visit_range_in_order(const K& from, const K& to, F f) {
  assert_key_leq(from, to);
  if (_root == nullptr) {
    return;
  }

  Cursor cursor_start = cursor(from);
  Cursor cursor_end = cursor(to);
  NodeType* start = cursor_start.found() ? cursor_start.node() : next(cursor_start).node();
  NodeType* end = next(cursor_end).node();

  while (start != end) {
    if (!f(start)) {
      return;
    }
    start = start->next();
  }
}

template <typename K, typename NodeType, typename COMPARATOR>
template <typename USER_VERIFIER>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::verify_self(const USER_VERIFIER& extra_verifier) const {
  if constexpr (HasNodeVerifier) {
    verify_self([](const NodeType* a, const NodeType* b){ return COMPARATOR::less_than(a, b);}, extra_verifier);
  } else if constexpr (HasKeyComparator) {
    verify_self([](const NodeType* a, const NodeType* b){ return COMPARATOR::cmp(a->key(), b->key()) == RBTreeOrdering::LT; }, extra_verifier);
  } else {
    verify_self([](const NodeType*, const NodeType*){ return true;}, extra_verifier);
  }
}

template <typename K, typename NodeType, typename COMPARATOR>
template <typename NODE_VERIFIER, typename USER_VERIFIER>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::verify_self(NODE_VERIFIER verifier, const USER_VERIFIER& extra_verifier) const {
  if (_root == nullptr) {
    assert(_num_nodes == 0, "rbtree has %zu nodes but no root", _num_nodes);
    return;
  }

  assert(_root->parent() == nullptr, "root of rbtree has a parent");

  size_t num_nodes = 0;
  size_t black_depth = 0;
  size_t tree_depth = 0;
  size_t shortest_leaf_path = 0;
  size_t longest_leaf_path = 0;
  DEBUG_ONLY(_expected_visited = !_expected_visited);
  bool expected_visited = DEBUG_ONLY(_expected_visited) NOT_DEBUG(false);

  _root->verify<NodeType>(num_nodes, black_depth, shortest_leaf_path, longest_leaf_path,
                tree_depth, expected_visited, verifier, extra_verifier);

  const unsigned int maximum_depth = log2i(size() + 1) * 2;

  assert(shortest_leaf_path <= longest_leaf_path && longest_leaf_path <= shortest_leaf_path * 2,
         "tree imbalanced, shortest path: %zu longest: %zu",
         shortest_leaf_path, longest_leaf_path);
  assert(tree_depth <= maximum_depth, "rbtree is too deep");
  assert(size() == num_nodes,
         "unexpected number of nodes in rbtree. expected: %zu"
         ", actual: %zu", size(), num_nodes);
}

template <typename T,
          ENABLE_IF(std::is_integral_v<T>),
          ENABLE_IF(std::is_signed_v<T>)>
void print_T(outputStream* st, T x) {
  st->print(INT64_FORMAT, (int64_t)x);
}

template <typename T,
          ENABLE_IF(std::is_integral_v<T>),
          ENABLE_IF(std::is_unsigned_v<T>)>
void print_T(outputStream* st, T x) {
  st->print(UINT64_FORMAT, (uint64_t)x);
}

template <typename T,
          ENABLE_IF(std::is_pointer_v<T>)>
void print_T(outputStream* st, T x) {
  st->print(PTR_FORMAT, p2i(x));
}

inline void IntrusiveRBNode::print_on(outputStream* st, int depth) const {
  st->print("(%d)", depth);
  st->sp(1 + depth * 2);
  st->print("@" PTR_FORMAT, p2i(this));
  st->cr();
}

template <typename K, typename V>
inline void RBNode<K, V>::print_on(outputStream* st, int depth) const {
  st->print("(%d)", depth);
  st->sp(1 + depth * 2);
  st->print("@" PTR_FORMAT ": [", p2i(this));
  print_T<K>(st, key());
  st->print("] = ");
  print_T<V>(st, val());
  st->cr();
}

template <typename K, typename NodeType, typename COMPARATOR>
template <typename PRINTER>
void AbstractRBTree<K, NodeType, COMPARATOR>::print_node_on(outputStream* st, int depth, const NodeType* n, const PRINTER& node_printer) const {
  node_printer(st, n, depth);
  depth++;
  if (n->_left != nullptr) {
    print_node_on(st, depth, (NodeType*)n->_left, node_printer);
  }
  if (n->_right != nullptr) {
    print_node_on(st, depth, (NodeType*)n->_right, node_printer);
  }
}

template <typename K, typename NodeType, typename COMPARATOR>
template <typename PRINTER>
void AbstractRBTree<K, NodeType, COMPARATOR>::print_on(outputStream* st, const PRINTER& node_printer) const {
  if (_root != nullptr) {
    print_node_on(st, 0, (NodeType*)_root, node_printer);
  }
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
template<typename... AllocArgs>
inline RBTree<K, V, COMPARATOR, ALLOCATOR>::RBTree(AllocArgs... alloc_args) : BaseType(), _allocator(alloc_args...) {}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline RBTree<K, V, COMPARATOR, ALLOCATOR>::~RBTree() {
  remove_all();
}

template<typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
bool RBTree<K, V, COMPARATOR, ALLOCATOR>::copy_into(RBTree& other) const {
  assert(other.size() == 0, "You can only copy into an empty RBTree");
  assert(std::is_copy_constructible<K>::value, "Key type must be copy-constructible when copying a RBTree");
  assert(std::is_copy_constructible<V>::value, "Value type must be copy-constructible when copying a RBTree");
  enum class Dir { Left, Right };
  struct node_pair { const IntrusiveRBNode* current; IntrusiveRBNode* other_parent; Dir dir; };
  struct stack {
    node_pair s[64];
    int idx = 0;
    stack() : idx(0) {}
    node_pair pop() { idx--; return s[idx]; };
    void push(node_pair n) { s[idx] = n; idx++; };
    bool is_empty() { return idx == 0; };
  };

  stack visit_stack;
  if (this->_root == nullptr)  {
    return true;
  }
  RBNode<K, V>* root = static_cast<RBNode<K, V>*>(this->_root);
  other._root = other.allocate_node(root->key(), root->val());
  if (other._root == nullptr) return false;

  visit_stack.push({this->_root->_left, other._root, Dir::Left});
  visit_stack.push({this->_root->_right, other._root, Dir::Right});
  while (!visit_stack.is_empty()) {
    node_pair n = visit_stack.pop();
    const RBNode<K, V>* current = static_cast<const RBNode<K, V>*>(n.current);
    if (current == nullptr) continue;
    RBNode<K, V>* new_node = other.allocate_node(current->key(), current->val());
    if (new_node == nullptr) {
      return false;
    }
    if (n.dir == Dir::Left) {
      n.other_parent->_left = new_node;
    } else {
      n.other_parent->_right = new_node;
    }
    new_node->set_parent(n.other_parent);
    new_node->_parent |= n.current->_parent & 0x1;
    visit_stack.push({n.current->_left, new_node, Dir::Left});
    visit_stack.push({n.current->_right, new_node, Dir::Right});
  }
  other._num_nodes = this->_num_nodes;
  DEBUG_ONLY(other._expected_visited = this->_expected_visited);
  return true;
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::replace_at_cursor(RBNode<K, V>* new_node, const Cursor& node_cursor) {
  RBNode<K, V>* old_node = node_cursor.node();
  BaseType::replace_at_cursor(new_node, node_cursor);
  free_node(old_node);
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline RBNode<K, V>* RBTree<K, V, COMPARATOR, ALLOCATOR>::allocate_node(const K& key) {
  void* node_place = _allocator.allocate(sizeof(RBNode<K, V>));
  if (node_place == nullptr) {
    return nullptr;
  }
  return new (node_place) RBNode<K, V>(key);
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline RBNode<K, V>* RBTree<K, V, COMPARATOR, ALLOCATOR>::allocate_node(const K& key, const V& val) {
  void* node_place = _allocator.allocate(sizeof(RBNode<K, V>));
  if (node_place == nullptr) {
    return nullptr;
  }
  return new (node_place) RBNode<K, V>(key, val);
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::free_node(RBNode<K, V>* node) {
  node->_value.~V();
  _allocator.free(node);
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline bool RBTree<K, V, COMPARATOR, ALLOCATOR>::upsert(const K& key, const V& val, const RBNode<K, V>* hint_node) {
  Cursor node_cursor = cursor(key, hint_node);
  RBNode<K, V>* node = node_cursor.node();
  if (node != nullptr) {
    node->set_val(val);
    return true;
  }

  node = allocate_node(key, val);
  if (node == nullptr) {
    return false;
  }
  insert_at_cursor(node, node_cursor);
  return true;
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline V* RBTree<K, V, COMPARATOR, ALLOCATOR>::find(const K& key) {
  Cursor node_cursor = cursor(key);
  return node_cursor.found() ? &node_cursor.node()->_value : nullptr;
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline V* RBTree<K, V, COMPARATOR, ALLOCATOR>::find(const K& key) const {
  const Cursor node_cursor = cursor(key);
  return node_cursor.found() ? &node_cursor.node()->_value : nullptr;
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::remove(RBNode<K, V>* node) {
  Cursor node_cursor = cursor(node);
  remove_at_cursor(node_cursor);
  free_node(node);
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline bool RBTree<K, V, COMPARATOR, ALLOCATOR>::remove(const K& key) {
  Cursor node_cursor = cursor(key);
  if (!node_cursor.found()) {
    return false;
  }
  RBNode<K, V>* node = node_cursor.node();
  remove_at_cursor(node_cursor);
  free_node((RBNode<K, V>*)node);
  return true;
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::remove_all() {
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

template <MemTag mem_tag, AllocFailType strategy>
inline void* RBTreeCHeapAllocator<mem_tag, strategy>::allocate(size_t sz) {
  return AllocateHeap(sz, mem_tag, strategy);
}

template <MemTag mem_tag, AllocFailType strategy>
inline void RBTreeCHeapAllocator<mem_tag, strategy>::free(void* ptr) {
  FreeHeap(ptr);
}

template <AllocFailType strategy>
inline RBTreeArenaAllocator<strategy>::RBTreeArenaAllocator(Arena* arena) : _arena(arena) {}

template <AllocFailType strategy>
inline void* RBTreeArenaAllocator<strategy>::allocate(size_t sz) {
  return _arena->Amalloc(sz, strategy);
}

template <AllocFailType strategy>
inline void RBTreeArenaAllocator<strategy>::free(void* ptr) { /* NOP */ }

template <AllocFailType strategy>
inline RBTreeResourceAreaAllocator<strategy>::RBTreeResourceAreaAllocator(ResourceArea* rarea) : _rarea(rarea) {}

template <AllocFailType strategy>
inline void* RBTreeResourceAreaAllocator<strategy>::allocate(size_t sz) {
  return _rarea->Amalloc(sz, strategy);
}

template <AllocFailType strategy>
inline void RBTreeResourceAreaAllocator<strategy>::free(void* ptr) { /* NOP */ }

#endif // SHARE_UTILITIES_RBTREE_INLINE_HPP
