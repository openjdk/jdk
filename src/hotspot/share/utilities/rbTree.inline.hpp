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

#ifndef SHARE_UTILITIES_RBTREE_INLINE_HPP
#define SHARE_UTILITIES_RBTREE_INLINE_HPP

#include "utilities/rbTree.hpp"

#include "metaprogramming/enableIf.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/powerOfTwo.hpp"

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
  if (_left != nullptr) { // right subtree exists
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

template <typename NodeType, typename NodeVerifier>
inline void IntrusiveRBNode::verify(
    size_t& num_nodes, size_t& black_nodes_until_leaf, size_t& shortest_leaf_path, size_t& longest_leaf_path,
    size_t& tree_depth, bool expect_visited, NodeVerifier verifier) const {
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
                  longest_leaf_path_left, tree_depth_left, expect_visited, verifier);
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
                   longest_leaf_path_right, tree_depth_right, expect_visited, verifier);
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

template <typename K, typename NodeType, typename COMPARATOR>
inline const typename AbstractRBTree<K, NodeType, COMPARATOR>::Cursor
AbstractRBTree<K, NodeType, COMPARATOR>::cursor(const K& key, const NodeType* hint_node) const {
  IntrusiveRBNode* parent = nullptr;
  IntrusiveRBNode* const* insert_location = &_root;

  if (hint_node != nullptr) {
    const int hint_cmp = cmp(key, hint_node);
    while (hint_node->parent() != nullptr) {
      const int parent_cmp = cmp(key, (NodeType*)hint_node->parent());
      // Move up until the parent would put us on the other side of the key.
      // Meaning we are in the correct subtree.
      if ((parent_cmp <= 0 && hint_cmp < 0) ||
          (parent_cmp >= 0 && hint_cmp > 0)) {
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
    const int key_cmp_k = cmp(key, curr);

    if (key_cmp_k == 0) {
      break;
    }

    parent = *insert_location;
    if (key_cmp_k < 0) {
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
    assert(cmp(static_cast<const NodeType*>(new_node), static_cast<const NodeType*>(new_node->parent())), "new node not < parent");
  } else if (new_node->is_right_child()) {
    assert(cmp(static_cast<const NodeType*>(new_node->parent()), static_cast<const NodeType*>(new_node)), "new node not > parent");
  }

  new_node->_left = old_node->_left;
  new_node->_right = old_node->_right;
  if (new_node->_left != nullptr) {
    assert(cmp(static_cast<const NodeType*>(new_node->_left), static_cast<const NodeType*>(new_node)), "left child not < new node");
    new_node->_left->set_parent(new_node);
  }
  if (new_node->_right != nullptr) {
    assert(cmp(static_cast<const NodeType*>(new_node), static_cast<const NodeType*>(new_node->_right)), "right child not > new node");
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
template <typename F>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::visit_in_order(F f) const {
  const NodeType* node = leftmost();
  while (node != nullptr) {
    f(node);
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
    f(start);
    start = start->next();
  }
}

template <typename K, typename NodeType, typename COMPARATOR>
template <typename NodeVerifier>
inline void AbstractRBTree<K, NodeType, COMPARATOR>::verify_self(NodeVerifier verifier) const {
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
                tree_depth, expected_visited, verifier);

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
          ENABLE_IF(std::is_integral<T>::value),
          ENABLE_IF(std::is_signed<T>::value)>
void print_T(outputStream* st, T x) {
  st->print(INT64_FORMAT, (int64_t)x);
}

template <typename T,
          ENABLE_IF(std::is_integral<T>::value),
          ENABLE_IF(std::is_unsigned<T>::value)>
void print_T(outputStream* st, T x) {
  st->print(UINT64_FORMAT, (uint64_t)x);
}

template <typename T,
          ENABLE_IF(std::is_pointer<T>::value)>
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
void AbstractRBTree<K, NodeType, COMPARATOR>::print_node_on(outputStream* st, int depth, const NodeType* n) const {
  n->print_on(st, depth);
  depth++;
  if (n->_right != nullptr) {
    print_node_on(st, depth, (NodeType*)n->_right);
  }
  if (n->_left != nullptr) {
    print_node_on(st, depth, (NodeType*)n->_left);
  }
}

template <typename K, typename NodeType, typename COMPARATOR>
void AbstractRBTree<K, NodeType, COMPARATOR>::print_on(outputStream* st) const {
  if (_root != nullptr) {
    print_node_on(st, 0, (NodeType*)_root);
  }
}

#endif // SHARE_UTILITIES_RBTREE_INLINE_HPP
