/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

// #include "memory/allocation.hpp"
// #include "memory/iterator.hpp"
// #include "utilities/debug.hpp"
// #include "opto/callnode.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
// #include "utilities/ostream.hpp"
// #include "utilities/powerOfTwo.hpp"

// COMPARATOR must have a static function `cmp(a,b)` which returns:
//     - an int < 0 when a < b
//     - an int == 0 when a == b
//     - an int > 0 when a > b
// ALLOCATOR must check for oom and exit, as RBTree currently does not handle the
// allocation failing.

template <typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
class RBTree {
  friend class RBTreeTest;
  friend class NMTVMATreeTest;
  enum Color { BLACK, RED };
  static constexpr int LEFT = 0;
  static constexpr int RIGHT = 1;
  ALLOCATOR _allocator;
  size_t _num_nodes;

public:
  // Root node - parent is nullptr
  // Leaf node - both left and right are nullptr
  class RBNode {
    friend RBTree;
    RBNode* _parent;
    RBNode* _left;
    RBNode* _right;
    RBNode* _children[2];
    Color _color;

    K _key;
    V _value;

  public:
    RBNode(const K &k, const V &v)
        : _parent(nullptr), _left(nullptr), _right(nullptr), _color(Color::RED),
          _key(k), _value(v) {}

    const K &key() const { return _key; }
    V &val() { return _value; }

    RBNode* left() { return _left; }
    RBNode* right() { return _right; }
    RBNode* parent() { return _parent; }
    Color color() { return _color; }

    bool is_right_child() {
      return _parent != nullptr && _parent->right() == this;
    }
    bool is_left_child() {
      return _parent != nullptr && _parent->left() == this;
    }

    void replace_child(RBNode* old_child, RBNode* new_child) {
      if (_left == old_child) {
        _left = new_child;
      } else if (_right == old_child) {
        _right = new_child;
      }
    }

    RBNode* sibling() {
      if (_parent == nullptr) {
        return nullptr;
      }
      if (this == _parent->left()) {
        return _parent->right();
      }
      return _parent->left();
    }

    RBNode* close_nephew() {
      if (_parent == nullptr) {
        return nullptr;
      }
      if (this == _parent->left()) {
        return _parent->right()->left();
      }
      return _parent->left()->right();
    }

    RBNode* distant_nephew() {
      if (_parent == nullptr) {
        return nullptr;
      }
      if (this == _parent->left()) {
        return _parent->right()->right();
      }
      return _parent->left()->left();
    }

    RBNode* uncle() {
      if (_parent == nullptr || _parent->parent() == nullptr) {
        return nullptr;
      }
      RBNode* grandparent = _parent->parent();
      if (_parent == grandparent->left()) {
        return grandparent->right();
      }
      return grandparent->left();
    }

    RBNode* rotate_left() {
      // Move node down, and right child up
      RBNode* old_right = _right;

      _right = old_right->left();
      if (old_right->left() != nullptr) {
        old_right->left()->_parent = this;
      }

      old_right->_parent = _parent;
      if (is_left_child()) {
        _parent->_left = old_right;
      }
      else if (is_right_child()) {
        _parent->_right = old_right;
      }

      old_right->_left = this;
      _parent = old_right;

      return old_right;
    }

    RBNode* rotate_right() {
      // Move node down, and left child up
      RBNode* old_left = _left;

      _left = old_left->right();
      if (old_left->right() != nullptr) {
        old_left->right()->_parent = this;
      }

      old_left->_parent = _parent;
      if (is_left_child()) {
        _parent->_left = old_left;
      }
      else if (is_right_child()) {
        _parent->_right = old_left;
      }

      old_left->_right = this;
      _parent = old_left;

      return old_left;
    }

    template <typename F> void visit_in_order_inner(F f) {
      if (_left != nullptr) {
        _left->visit_in_order_inner(f);
      }
      f(this);
      if (_right != nullptr) {
        _right->visit_in_order_inner(f);
      }
    }

    // Visit all RBNodes in ascending order whose keys are in range [from, to).
    template <typename F> void visit_range_in_order_inner(const K& from, const K& to, F f) {
      int cmp_from = COMPARATOR::cmp(from, key());
      int cmp_to = COMPARATOR::cmp(to, key());
      if (_left != nullptr && cmp_from < 0) { // from < key
        _left->visit_range_in_order_inner(from, to, f);
      }
      if (cmp_from <= 0 && cmp_to > 0) { // from <= key && to > key
        f(this);
      }
      if (_right != nullptr && cmp_to > 0) { // to > key
        _right->visit_range_in_order_inner(from, to, f);
      }
    }

#ifdef ASSERT
    bool is_correct(int num_blacks) const {
      if (_color == BLACK) {
        num_blacks--;
      }
      bool left_is_correct = num_blacks == 0;
      bool right_is_correct = num_blacks == 0;
      if (_left != nullptr) {
        if (COMPARATOR::cmp(_left->key(), _key) >= 0 || // left >= root, or
            (_color == RED && _left->color() == RED) || // 2 red nodes, or
            (_left->parent() != this)) {                // Pointer mismatch,
          return false;                                 // incorrect.
        }
        left_is_correct = _left->is_correct(num_blacks);
      }
      if (_right != nullptr) {
        if (COMPARATOR::cmp(_right->key(), _key) <= 0 || // left >= root, or
            (_color == RED && _left->color() == RED)  || // 2 red nodes, or
            (_right->parent() != this)) {                // Pointer mismatch,
          return false;                                  // incorrect.
        }
        right_is_correct = _right->is_correct(num_blacks);
      }
      return left_is_correct && right_is_correct;
    }

    size_t count_nodes() const {
      size_t right_nodes = _right == nullptr ? 0 : _right->count_nodes();
      size_t left_nodes = _left == nullptr ? 0 : _left->count_nodes();
      return 1 + right_nodes + left_nodes;
    }
#endif // ASSERT

  };

private:
  RBNode* _root;

  RBNode* allocate_node(const K &k, const V &v) {
    void* node_place = _allocator.allocate(sizeof(RBNode));
    if (node_place != nullptr) {
      _num_nodes++;
    }
    return new (node_place) RBNode(k, v);
  }

  void free_node(RBNode *node) {
    _allocator.free(node);
    _num_nodes--;
  }

  inline bool is_black(RBNode *node) {
    return node == nullptr || node->color() == BLACK;
  }

  inline bool is_red(RBNode *node) {
    return node != nullptr && node->color() == RED;
  }

  RBNode* find(RBNode* curr, const K &k) {
    while (curr != nullptr) {
      int key_cmp_k = COMPARATOR::cmp(k, curr->key());

      if (key_cmp_k == 0) {       // k == key
        return curr;
      } else if (key_cmp_k < 0) { // k < key
        curr = curr->left();
      } else {                    // k > key
        curr = curr->right();
      }
    }

    return nullptr;
  }

  RBNode* insert_node(const K &k, const V &v, bool replace) {
    RBNode* curr = _root;
    if (curr == nullptr) { // Tree is empty
      _root = allocate_node(k, v);
      return _root;
    }

    RBNode* parent = nullptr;
    while (curr != nullptr) {
      int key_cmp_k = COMPARATOR::cmp(k, curr->key());

      if (key_cmp_k == 0) { // k == key
        if (replace) {
          curr->_value = v;
        }
        return curr;
      }

      parent = curr;
      if (key_cmp_k < 0) { // k < key
        curr = curr->left();
      } else {             // k > key
        curr = curr->right();
      }
    }

    // Create and insert new node
    RBNode* node = allocate_node(k, v);
    node->_parent = parent;

    int key_cmp_k = COMPARATOR::cmp(k, parent->key());
    if (key_cmp_k < 0) { // k < key
      parent->_left = node;
    } else {             // k > key
      parent->_right = node;
    }

    return node;
  }

  void fix_violations(RBNode* node) {
    if(node->color() == BLACK) { // node's value was updated
      return;                    // Tree is already correct
    }

    RBNode* parent = node->parent();
    while (parent != nullptr && parent->color() != Color::BLACK) {
      // Node and parent are both red, creating a red-violation

      RBNode* grandparent = parent->parent();
      if (grandparent == nullptr) { // Parent is the tree root
        parent->_color = BLACK;     // Color parent black to eliminate the red-violation
        return;
      }

      RBNode* uncle = node->uncle();
      if (is_black(uncle)) { // Parent is red, uncle is black
        // Rotate the parent to the position of the grandparent

        // Different rotation directions dependant on side of the tree
        if (parent->is_left_child()) {
          if (node->is_right_child()) { // Node is an "inner" node
            // Rotate and swap node and parent to make it an "outer" node
            parent->rotate_left();
            parent = node;
          }
          grandparent->rotate_right();  // Rotate the parent to the position of the grandparent
        } else if (parent->is_right_child()) {
          if (node->is_left_child()) {  // Node is an "inner" node
            // Rotate and swap node and parent to make it an "outer" node
            parent->rotate_right();
            parent = node;
          }
          grandparent->rotate_left();   // Rotate the parent to the position of the grandparent
        }

        // Swap parent and grandparent colors to eliminate the red-violation
        parent->_color = BLACK;
        grandparent->_color = RED;
        if (_root == grandparent) {
          _root = parent;
        }

        return;
      }

      // Parent and uncle are both red
      // Paint both black, paint grandparent red to not create a black-violation
      parent->_color = BLACK;
      uncle->_color = BLACK;
      grandparent->_color = RED;

      // Move up two levels to check for new potential red-violation
      node = grandparent;
      parent = grandparent->parent();
    }
  }

  void remove_inner(RBNode *node) {
    RBNode *parent = node->parent();
    while (parent != nullptr) {

      RBNode *sibling = node->sibling();
      if (is_red(sibling)) { // Sibling red, parent and nephews must be black
        // Rotate so sibling becomes parent, swap parent and sibling colors
        parent->_color = RED;
        sibling->_color = BLACK;
        if (node->is_left_child()) {
          parent->rotate_left();
          sibling = parent->right();
        } else {
          parent->rotate_right();
          sibling = parent->left();
        }

        if (_root == parent) {
          _root = parent->parent();
        }
      }

      RBNode *close_nephew = node->close_nephew();
      RBNode *distant_nephew = node->distant_nephew();
      if (is_red(distant_nephew) || is_red(close_nephew)) {
        if (is_black(distant_nephew)) { // close red, distant black,
          // Rotate sibling down and inner nephew up
          if (node->is_left_child()) {
            sibling->rotate_right();
          } else {
            sibling->rotate_left();
          }
          sibling->_color = RED;
          close_nephew->_color = BLACK;
          distant_nephew = sibling;
          sibling = close_nephew;
        }

        // Distant nephew red
        if (node->is_left_child()) {
          parent->rotate_left();
        } else {
          parent->rotate_right();
        }
        if (_root == parent) {
          _root = sibling;
        }
        sibling->_color = parent->color();
        parent->_color = BLACK;
        distant_nephew->_color = BLACK;
        return;
      }

      if (is_red(parent)) { // parent red, sibling and nephews black
        // Swap parent and sibling colors to restore black balance
        sibling->_color = RED;
        parent->_color = BLACK;
        return;
      }

      // Parent, sibling, both nephews black
      sibling->_color = RED;
      node = parent;
      parent = node->parent();
    }
  }
  
  void remove_all_inner(RBNode *node) {
    if (node == nullptr)
      return;
    remove_all_inner(node->left());
    remove_all_inner(node->right());
    free_node(node);
  }

public:
  NONCOPYABLE(RBTree);

  RBTree() : _allocator(), _num_nodes(0), _root(nullptr) {}

  size_t num_nodes() const { return _num_nodes; }

  void insert(const K &k, const V &v) {
    RBNode* node = insert_node(k, v, false);
    fix_violations(node);
  }

  void upsert(const K &k, const V &v) {
    RBNode* node = insert_node(k, v, true);
    fix_violations(node);
    // verify_tree();
  }


  bool remove(const K &k) {
    RBNode* node = find(_root, k);
    if (node == nullptr) {
      return false;
    }
    RBNode* left= node->left();
    RBNode* right = node->right();

    if (left != nullptr && right != nullptr) { // node has two children
      // Copy the k/v from the in-order successor and delete that node instead
      RBNode* curr = right;
      while (curr->left() != nullptr) {
        curr = curr->left();
      }
      node->_key = curr->key();
      node->_value = curr->val();

      node = curr;
      right = curr->right();
      left = nullptr; // Must be, since curr is the left-most child of the right subtree
    }

    RBNode* parent = node->parent();
    if (left != nullptr) { // node has a left only-child
      // node must be black, and child red, otherwise a black-violation would exist
      // Remove node and color the child black.
      node->_left->_color = BLACK;
      node->_left->_parent = node->parent();
      if (parent == nullptr) {
        _root = node->left();
      } else {
        node->parent()->replace_child(node, left);
      }
    } else if (right != nullptr) { // node has a right only-child
      node->_right->_color = BLACK;
      node->_right->_parent = node->parent();
      if (parent == nullptr) {
        _root = node->right();
      } else {
        node->parent()->replace_child(node, right);
      }
    }

    else { // node has no children
      if (node == _root) { // Tree empty
        _root = nullptr;
      } else { // node is black, creating a black imbalance
        if (is_black(node)) {
          remove_inner(node);
        }
        node->parent()->replace_child(node, nullptr);
      }
    }

    free_node(node);
    return true;
  }

  void remove_all() {
    remove_all_inner(_root);
    _num_nodes = 0;
    _root = nullptr;
  }

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

  V& find(K& key) {
    RBNode* node = find(_root, key);
    return node->val();
  }

  // Visit all RBNodes in ascending order.
  template <typename F>
  void visit_in_order(F f) const {
    _root->visit_in_order_inner(f);
  }

  // Visit all RBNodes in ascending order whose keys are in range [from, to).
  template <typename F>
  void visit_range_in_order(const K& from, const K& to, F f) {
    _root->visit_range_in_order_inner(from, to, f);
  }

#ifdef ASSERT
  bool verify_tree() {
    if (_root == nullptr) {
      return _num_nodes == 0;
    }

    int black_nodes = 0;
    RBNode *node = _root;
    while (node != nullptr) {
      if (node->color() == BLACK) {
        black_nodes++;
      }
      node = node->left();
    }

    const size_t num_nodes = _root->count_nodes();
    const int maximum_depth = log2i(_num_nodes + 1) * 2;

    bool count_fail = num_nodes != _num_nodes;
    bool too_deep = 2 * black_nodes > maximum_depth;
    bool correct = _root->is_correct(black_nodes);

    return correct && !count_fail && !too_deep;
  }
#endif // ASSERT

};

class RBTreeCHeapAllocator {
public:
  void *allocate(size_t sz) {
    void *allocation = os::malloc(sz, mtNMT);
    if (allocation == nullptr) {
      vm_exit_out_of_memory(sz, OOM_MALLOC_ERROR,
                            "red-black tree failed allocation");
    }
    return allocation;
  }

  void free(void *ptr) { os::free(ptr); }
};

template <typename K, typename V, typename COMPARATOR>
using RBTreeCHeap = RBTree<K, V, COMPARATOR, RBTreeCHeapAllocator>;

#endif // SHARE_UTILITIES_RBTREE_HPP
