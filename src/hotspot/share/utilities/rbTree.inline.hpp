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

#include "metaprogramming/enableIf.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/rbTree.hpp"

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode::replace_child(
    RBNode* old_child, RBNode* new_child) {
  if (_left == old_child) {
    _left = new_child;
  } else if (_right == old_child) {
    _right = new_child;
  } else {
    ShouldNotReachHere();
  }
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline typename RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode*
RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode::rotate_left() {
  // This node down, right child up
  RBNode* old_right = _right;

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

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline typename RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode*
RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode::rotate_right() {
  // This node down, left child up
  RBNode* old_left = _left;

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

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline const typename RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode*
RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode::prev() const {
  const RBNode* node = this;
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

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline const typename RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode*
RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode::next() const {
  const RBNode* node = this;
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

#ifdef ASSERT
template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode::verify(
    size_t& num_nodes, size_t& black_nodes_until_leaf, size_t& shortest_leaf_path, size_t& longest_leaf_path,
    size_t& tree_depth, bool expect_visited) const {
  assert(expect_visited != _visited, "node already visited");
  _visited = !_visited;

  size_t num_black_nodes_left = 0;
  size_t shortest_leaf_path_left = 0;
  size_t longest_leaf_path_left = 0;
  size_t tree_depth_left = 0;

  if (_left != nullptr) {
    if (_right == nullptr) {
      assert(is_black() && _left->is_red(), "if one child it must be red and node black");
    }
    assert(COMPARATOR::cmp(_left->key(), _key) < 0, "left node must be less than parent");
    assert(is_black() || _left->is_black(), "2 red nodes in a row");
    assert(_left->parent() == this, "pointer mismatch");
    _left->verify(num_nodes, num_black_nodes_left, shortest_leaf_path_left,
                  longest_leaf_path_left, tree_depth_left, expect_visited);
  }

  size_t num_black_nodes_right = 0;
  size_t shortest_leaf_path_right = 0;
  size_t longest_leaf_path_right = 0;
  size_t tree_depth_right = 0;

  if (_right != nullptr) {
    if (_left == nullptr) {
      assert(is_black() && _right->is_red(), "if one child it must be red and node black");
    }
    assert(COMPARATOR::cmp(_right->key(), _key) > 0, "right node must be greater than parent");
    assert(is_black() || _left->is_black(), "2 red nodes in a row");
    assert(_right->parent() == this, "pointer mismatch");
    _right->verify(num_nodes, num_black_nodes_right, shortest_leaf_path_right,
                   longest_leaf_path_right, tree_depth_right, expect_visited);
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

#endif // ASSERT

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline const typename RBTree<K, V, COMPARATOR, ALLOCATOR>::RBNode*
RBTree<K, V, COMPARATOR, ALLOCATOR>::find_node(const K& key) const {
  RBNode* curr = _root;
  while (curr != nullptr) {
    const int key_cmp_k = COMPARATOR::cmp(key, curr->key());

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
RBTree<K, V, COMPARATOR, ALLOCATOR>::insert_node(const K& key, const V& val) {
  RBNode* curr = _root;
  if (curr == nullptr) { // Tree is empty
    _root = allocate_node(key, val);
    return _root;
  }

  RBNode* parent = nullptr;
  while (curr != nullptr) {
    const int key_cmp_k = COMPARATOR::cmp(key, curr->key());

    if (key_cmp_k == 0) {
      curr->_value = val;
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
  RBNode* node = allocate_node(key, val);
  node->set_parent(parent);

  const int key_cmp_k = COMPARATOR::cmp(key, parent->key());
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

  RBNode* parent = node->parent();
  while (parent != nullptr && parent->is_red()) {
    // Node and parent are both red, creating a red-violation

    RBNode* grandparent = parent->parent();
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
    parent = grandparent->parent();
  }
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::remove_black_leaf(RBNode* node) {
  // Black node removed, balancing needed
  RBNode* parent = node->parent();
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
        _root = parent->parent();
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
    parent = node->parent();
  }
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::remove_from_tree(RBNode* node) {
  RBNode* parent = node->parent();
  RBNode* left = node->_left;
  RBNode* right = node->_right;
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

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::remove(RBNode* node) {
  assert(node != nullptr, "must be");

  if (node->_left != nullptr && node->_right != nullptr) { // node has two children
    // Swap place with the in-order successor and delete there instead
    RBNode* curr = node->_right;
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
  free_node(node);
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
template <typename F>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::visit_in_order(F f) const {
  const RBNode* to_visit[64];
  int stack_idx = 0;
  const RBNode* head = _root;
  while (stack_idx > 0 || head != nullptr) {
    while (head != nullptr) {
      to_visit[stack_idx++] = head;
      assert(stack_idx <= (int)(sizeof(to_visit)/sizeof(to_visit[0])), "stack too deep");
      head = head->_left;
    }
    head = to_visit[--stack_idx];
    f(head);
    head = head->_right;
  }
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
template <typename F>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::visit_range_in_order(const K& from, const K& to, F f) const {
  assert(COMPARATOR::cmp(from, to) <= 0, "from must be less or equal to to");
  const RBNode* curr = closest_geq(from);
  if (curr == nullptr) return;
  const RBNode* const end = closest_geq(to);

  while (curr != nullptr && curr != end) {
    f(curr);
    curr = curr->next();
  }
}

#ifdef ASSERT
template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
inline void RBTree<K, V, COMPARATOR, ALLOCATOR>::verify_self() const {
  if (_root == nullptr) {
    assert(_num_nodes == 0, "rbtree has nodes but no root");
    return;
  }

  assert(_root->parent() == nullptr, "root of rbtree has a parent");

  size_t num_nodes = 0;
  size_t black_depth = 0;
  size_t tree_depth = 0;
  size_t shortest_leaf_path = 0;
  size_t longest_leaf_path = 0;
  _expected_visited = !_expected_visited;

  _root->verify(num_nodes, black_depth, shortest_leaf_path, longest_leaf_path, tree_depth, _expected_visited);

  const unsigned int maximum_depth = log2i(size() + 1) * 2;

  assert(shortest_leaf_path <= longest_leaf_path && longest_leaf_path <= shortest_leaf_path * 2,
         "tree imbalanced, shortest path: %zu longest: %zu",
         shortest_leaf_path, longest_leaf_path);
  assert(tree_depth <= maximum_depth, "rbtree is too deep");
  assert(size() == num_nodes,
         "unexpected number of nodes in rbtree. expected: %zu"
         ", actual: %zu", size(), num_nodes);
}
#endif // ASSERT

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

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
void RBTree<K, V, COMPARATOR, ALLOCATOR>::print_node_on(outputStream* st, int depth, const NodeType* n) const {
  st->print("(%d)", depth);
  st->sp(1 + depth * 2);
  st->print("@" PTR_FORMAT ": [", p2i(n));
  print_T<K>(st, n->key());
  st->print("] = ");
  print_T<V>(st, n->val());
  st->cr();
  depth++;
  if (n->_right != nullptr) {
    print_node_on(st, depth, n->_right);
  }
  if (n->_left != nullptr) {
    print_node_on(st, depth, n->_left);
  }
}

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
void RBTree<K, V, COMPARATOR, ALLOCATOR>::print_on(outputStream* st) const {
  if (_root != nullptr) {
    print_node_on(st, 0, _root);
  }
}

#endif // SHARE_UTILITIES_RBTREE_INLINE_HPP
