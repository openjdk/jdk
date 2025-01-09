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

#include "utilities/debug.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/rbTree.hpp"

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode::replace_child(
    RBNode* old_child, RBNode* new_child) {
  if (_left == old_child) {
    _left = new_child;
  } else if (_right == old_child) {
    _right = new_child;
  }
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline typename RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode*
RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode::rotate_left() {
  // Move node down to the left, and right child up
  RBNode* old_right = _right;

  _right = old_right->_left;
  if (old_right->_left != nullptr) {
    old_right->_left->_parent = this;
  }

  old_right->_parent = _parent;
  if (is_left_child()) {
    _parent->_left = old_right;
  } else if (is_right_child()) {
    _parent->_right = old_right;
  }

  old_right->_left = this;
  _parent = old_right;

  return old_right;
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline typename RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode*
RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode::rotate_right() {
  // Move node down to the right, and left child up
  RBNode* old_left = _left;

  _left = old_left->_right;
  if (old_left->_right != nullptr) {
    old_left->_right->_parent = this;
  }

  old_left->_parent = _parent;
  if (is_left_child()) {
    _parent->_left = old_left;
  } else if (is_right_child()) {
    _parent->_right = old_left;
  }

  old_left->_right = this;
  _parent = old_left;

  return old_left;
}

#ifdef ASSERT
template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline bool RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode::is_correct(
  unsigned int num_blacks, unsigned int maximum_depth, unsigned int current_depth) const {
  if (current_depth > maximum_depth) {
    return false;
  }

  if (is_black()) {
    num_blacks--;
  }

  bool left_is_correct = num_blacks == 0;
  bool right_is_correct = num_blacks == 0;
  if (_left != nullptr) {
    if (COMPARATOR::cmp(_left->key(), _key) >= 0 || // left >= root, or
        (is_red() && _left->is_red()) ||            // 2 red nodes, or
        (_left->_parent != this)) {                 // Pointer mismatch,
      return false;                                 // all incorrect.
    }
    left_is_correct = _left->is_correct(num_blacks, maximum_depth, current_depth++);
  }
  if (_right != nullptr) {
    if (COMPARATOR::cmp(_right->key(), _key) <= 0 || // right <= root, or
        (is_red() && _left->is_red()) ||             // 2 red nodes, or
        (_right->_parent != this)) {                 // Pointer mismatch,
      return false;                                  // all incorrect.
    }
    right_is_correct = _right->is_correct(num_blacks, maximum_depth, current_depth++);
  }
  return left_is_correct && right_is_correct;
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline size_t RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode::count_nodes() const {
  size_t left_nodes = _left == nullptr ? 0 : _left->count_nodes();
  size_t right_nodes = _right == nullptr ? 0 : _right->count_nodes();
  return 1 + left_nodes + right_nodes;
}

#endif // ASSERT

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline typename RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode*
RBTree<K, V, COMPARATOR, ALLOCATOR>::find_node(RBNode* curr, const K& k) {
  while (curr != nullptr) {
    int key_cmp_k = COMPARATOR::cmp(k, curr->key());

    if (key_cmp_k == 0) {
      return curr;
    } else if (key_cmp_k < 0) {
      curr = curr->_left;
    } else {
      curr = curr->_right;
    }
  }

  return nullptr;
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline typename RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode*
RBTree<K, V, COMPARATOR, ALLOCATOR>::insert_node(const K& k, const V& v) {
  RBNode* curr = _root;
  if (curr == nullptr) { // Tree is empty
    _root = allocate_node(k, v);
    return _root;
  }

  RBNode* parent = nullptr;
  while (curr != nullptr) {
    int key_cmp_k = COMPARATOR::cmp(k, curr->key());

    if (key_cmp_k == 0) {
      curr->_value = v;
      return curr;
    }

    parent = curr;
    if (key_cmp_k < 0) {
      curr = curr->_left;
    } else {
      curr = curr->_right;
    }
  }

  // Create and insert new node
  RBNode* node = allocate_node(k, v);
  node->_parent = parent;

  int key_cmp_k = COMPARATOR::cmp(k, parent->key());
  if (key_cmp_k < 0) {
    parent->_left = node;
  } else {
    parent->_right = node;
  }

  return node;
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::fix_insert_violations(RBNode* node) {
  if (node->is_black()) { // node's value was updated
    return;               // Tree is already correct
  }

  RBNode* parent = node->_parent;
  while (parent != nullptr && parent->is_red()) {
    // Node and parent are both red, creating a red-violation

    RBNode* grandparent = parent->_parent;
    if (grandparent == nullptr) { // Parent is the tree root
      assert(parent == _root, "parent must be root");
      parent->set_black(); // Color parent black to eliminate the red-violation
      return;
    }

    RBNode* uncle = parent->is_left_child() ? grandparent->_right : grandparent->_left;
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
    parent = grandparent->_parent;
  }
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::remove_black_leaf(RBNode* node) {
  // Black node removed, balancing needed
  RBNode* parent = node->_parent;
  while (parent != nullptr) {
    // Sibling must exist. If it did not, node would need to be red to not break
    // tree properties, and could be trivially removed before reaching here
    RBNode* sibling = node->is_left_child() ? parent->_right : parent->_left;
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
        _root = parent->_parent;
      }
      // Further balancing needed
    }

    RBNode* close_nephew = node->is_left_child() ? sibling->_left : sibling->_right;
    RBNode* distant_nephew = node->is_left_child() ? sibling->_right : sibling->_left;
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
    parent = node->_parent;
  }
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::remove_from_tree(RBNode* node) {
  RBNode* parent = node->_parent;
  RBNode* left = node->_left;
  RBNode* right = node->_right;
  if (left != nullptr) { // node has a left only-child
    // node must be black, and child red, otherwise a black-violation would
    // exist Remove node and color the child black.
    assert(right == nullptr, "right must be nullptr");
    assert(is_black(node), "node must be black");
    assert(is_red(left), "child must be red");
    left->set_black();
    left->_parent = parent;
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
    right->_parent = parent;
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

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline bool RBTree<K, V, COMPARATOR, ALLOCATOR>::remove(RBNode* node) {
  if (node == nullptr) {
    return false;
  }

  if (node->_left != nullptr && node->_right != nullptr) { // node has two children
    // Swap place with the in-order successor and delete there instead
    RBNode* curr = node->_right;
    while (curr->_left != nullptr) {
      curr = curr->_left;
    }

    if (_root == node) _root = curr;

    std::swap(curr->_left, node->_left);
    std::swap(curr->_color, node->_color);

    // If node is curr's parent, swapping right/parent severs the node connection
    if (node->_right == curr) {
      node->_right = curr->_right;
      curr->_parent = node->_parent;
      node->_parent = curr;
      curr->_right = node;
    } else {
      std::swap(curr->_right, node->_right);
      std::swap(curr->_parent, node->_parent);
      node->_parent->replace_child(curr, node);
      curr->_right->_parent = curr;
    }

    if (curr->_parent != nullptr) curr->_parent->replace_child(node, curr);
    curr->_left->_parent = curr;

    if (node->_left != nullptr) node->_left->_parent = node;
    if (node->_right != nullptr) node->_right->_parent = node;
  }

  remove_from_tree(node);
  free_node(node);
  return true;
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
template <typename F>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::visit_in_order(F f) {
  GrowableArrayCHeap<RBNode*, mtInternal> to_visit(2 * log2i(_num_nodes + 1));
  RBNode* head = _root;
  while (!to_visit.is_empty() || head != nullptr) {
    while (head != nullptr) {
      to_visit.push(head);
      head = head->_left;
    }
    head = to_visit.pop();
    f(head);
    head = head->_right;
  }
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
template <typename F>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::visit_range_in_order(const K& from, const K& to, F f) {
  assert(COMPARATOR::cmp(from, to) <= 0, "from must be less or equal to to");
  GrowableArrayCHeap<RBNode*, mtInternal> to_visit;
  RBNode* head = _root;
  while (!to_visit.is_empty() || head != nullptr) {
    while (head != nullptr) {
      int cmp_from = COMPARATOR::cmp(head->_key, from);
      to_visit.push(head);
      if (cmp_from >= 0) {
        head = head->_left;
      } else {
        // We've reached a node which is strictly less than from
        // We don't need to visit any further to the left.
        break;
      }
    }
    head = to_visit.pop();
    const int cmp_from = COMPARATOR::cmp(head->_key, from);
    const int cmp_to = COMPARATOR::cmp(head->_key, to);
    if (cmp_from >= 0 && cmp_to < 0) {
      f(head);
    }
    if (cmp_to < 0) {
      head = head->_right;
    } else {
      head = nullptr;
    }
  }
}

#ifdef ASSERT
template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::verify_self() {
  if (_root == nullptr) {
    assert(_num_nodes == 0, "rbtree has nodes but no root");
    return;
  }

  assert(_root->_parent == nullptr, "root of rbtree has a parent");

  unsigned int black_nodes = 0;
  RBNode* node = _root;
  while (node != nullptr) {
    if (node->is_black()) {
      black_nodes++;
    }
    node = node->_left;
  }

  const size_t actual_num_nodes = _root->count_nodes();
  const size_t expected_num_nodes = size();
  const unsigned int maximum_depth = log2i(size() + 1) * 2;

  assert(expected_num_nodes == actual_num_nodes,
         "unexpected number of nodes in rbtree. expected: " SIZE_FORMAT
         ", actual: " SIZE_FORMAT, expected_num_nodes, actual_num_nodes);
  assert(2 * black_nodes <= maximum_depth,
         "rbtree is too deep for its number of nodes. can be at "
         "most: " INT32_FORMAT ", but is: " UINT32_FORMAT, maximum_depth, 2 * black_nodes);
  assert(_root->is_correct(black_nodes, maximum_depth, 1), "rbtree does not hold rb-properties");
}
#endif // ASSERT

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
template <bool Forward>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::IteratorImpl<Forward>::push_left(RBNode* node) {
  while (node != nullptr) {
    _to_visit.push(node);
    node = node->_left;
  }
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
template <bool Forward>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::IteratorImpl<Forward>::push_right(RBNode* node) {
  while (node != nullptr) {
    _to_visit.push(node);
    node = node->_right;
  }
}

#endif // SHARE_UTILITIES_RBTREE_INLINE_HPP