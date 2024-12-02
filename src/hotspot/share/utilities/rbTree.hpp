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

#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/powerOfTwo.hpp"

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

private:

  ALLOCATOR _allocator;
  size_t _num_nodes;
  bool _num_outdated;

public:
  class RBNode {
    friend RBTree;

  private:
    RBNode* _parent;
    RBNode* _left;
    RBNode* _right;

    K _key;
    V _value;

    enum Color : uint8_t { BLACK, RED };
    Color _color;

  public:
    const K& key() const { return _key; }
    V& val() { return _value; }

    bool is_black() const  {
      return _color == BLACK;
    }

    bool is_red() const {
      return _color == RED;
    }

  private:
    void color_black() {
      _color = BLACK;
    }

    void color_red() {
      _color = RED;
    }

    RBNode(const K &k, const V &v)
        : _parent(nullptr), _left(nullptr), _right(nullptr),
          _key(k), _value(v), _color(RED) {}

    bool is_right_child() {
      return _parent != nullptr && _parent->_right == this;
    }

    bool is_left_child() {
      return _parent != nullptr && _parent->_left == this;
    }

    void update_children(RBNode* left, RBNode* right) {
      _left = left;
      _right = right;
      if (left != nullptr) {
        left->_parent = this;
      }
      if (right != nullptr) {
        right->_parent = this;
      }
    }

    void replace_child(RBNode* old_child, RBNode* new_child) {
      if (_left == old_child) {
        _left = new_child;
      } else if (_right == old_child) {
        _right = new_child;
      }
    }

    RBNode* rotate_left() {
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

    RBNode* rotate_right() {
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

    template <typename F>
    void visit_in_order_inner(F f) {
      if (_left != nullptr) {
        _left->visit_in_order_inner(f);
      }
      f(this);
      if (_right != nullptr) {
        _right->visit_in_order_inner(f);
      }
    }

    // Visit all RBNodes in ascending order whose keys are in range [from, to).
    template <typename F>
    void visit_range_in_order_inner(const K& from, const K& to, F f) {
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
    bool is_correct(unsigned int num_blacks) const {
      if (is_black()) {
        num_blacks--;
      }

      bool left_is_correct = num_blacks == 0;
      bool right_is_correct = num_blacks == 0;
      if (_left != nullptr) {
        if (COMPARATOR::cmp(_left->key(), _key) >= 0 || // left >= root, or
            (is_red() && _left->is_red())            || // 2 red nodes, or
            (_left->_parent != this)) {                 // Pointer mismatch,
          return false;                                 // all incorrect.
        }
        left_is_correct = _left->is_correct(num_blacks);
      }
      if (_right != nullptr) {
        if (COMPARATOR::cmp(_right->key(), _key) <= 0 || // right <= root, or
            (is_red() && _left->is_red())             || // 2 red nodes, or
            (_right->_parent != this)) {                 // Pointer mismatch,
          return false;                                  // all incorrect.
        }
        right_is_correct = _right->is_correct(num_blacks);
      }
      return left_is_correct && right_is_correct;
    }

    size_t count_nodes() const {
      size_t left_nodes = _left == nullptr ? 0 : _left->count_nodes();
      size_t right_nodes = _right == nullptr ? 0 : _right->count_nodes();
      return 1 + left_nodes + right_nodes;
    }
#endif // ASSERT
  };

private:
  RBNode* _root;

  RBNode* allocate_node(const K& k, const V& v) {
    void* node_place = _allocator.allocate(sizeof(RBNode));
    if (node_place == nullptr) {
      return nullptr;
    }
    _num_nodes++;
    return new (node_place) RBNode(k, v);
  }

  void free_node(RBNode* node) {
    _allocator.free(node);
    _num_nodes--;
  }

  static inline bool is_black(RBNode* node) {
    return node == nullptr || node->is_black();
  }

  static inline bool is_red(RBNode* node) {
    return node != nullptr && node->is_red();
  }

  RBNode* find(RBNode* curr, const K& k) {
    while (curr != nullptr) {
      int key_cmp_k = COMPARATOR::cmp(k, curr->key());

      if (key_cmp_k == 0) {       // k == key
        return curr;
      } else if (key_cmp_k < 0) { // k < key
        curr = curr->_left;
      } else {                    // k > key
        curr = curr->_right;
      }
    }

    return nullptr;
  }

  RBNode* insert_node(const K& k, const V& v) {
    RBNode* curr = _root;
    if (curr == nullptr) { // Tree is empty
      _root = allocate_node(k, v);
      return _root;
    }

    RBNode* parent = nullptr;
    while (curr != nullptr) {
      int key_cmp_k = COMPARATOR::cmp(k, curr->key());

      if (key_cmp_k == 0) { // k == key
        curr->_value = v;
        return curr;
      }

      parent = curr;
      if (key_cmp_k < 0) { // k < key
        curr = curr->_left;
      } else {             // k > key
        curr = curr->_right;
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

  void fix_insert_violations(RBNode* node) {
    if(node->is_black()) { // node's value was updated
      return;              // Tree is already correct
    }

    RBNode* parent = node->_parent;
    while (parent != nullptr && parent->is_red()) {
      // Node and parent are both red, creating a red-violation

      RBNode* grandparent = parent->_parent;
      if (grandparent == nullptr) { // Parent is the tree root
        assert(parent == _root, "parent must be root");
        parent->color_black();      // Color parent black to eliminate the red-violation
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
        parent->color_black();
        grandparent->color_red();

        if (_root == grandparent) {
          _root = parent;
        }

        return;
      }

      // Parent and uncle are both red
      // Paint both black, paint grandparent red to not create a black-violation
      parent->color_black();
      uncle->color_black();
      grandparent->color_red();

      // Move up two levels to check for new potential red-violation
      node = grandparent;
      parent = grandparent->_parent;
    }
  }

  void remove_black_leaf(RBNode* node) {
    // Black node removed, balancing needed
    RBNode* parent = node->_parent;
    while (parent != nullptr) {
      // Sibling must exist. If it did not, node would need to be red to not break tree properties,
      // and could be trivially removed before reaching here
      RBNode* sibling = node->is_left_child() ? parent->_right : parent->_left;
      if (is_red(sibling)) { // Sibling red, parent and nephews must be black
        assert(is_black(parent), "parent must be black");
        assert(is_black(sibling->_left), "nephew must be black");
        assert(is_black(sibling->_right), "nephew must be black");
        // Swap parent and sibling colors
        parent->color_red();
        sibling->color_black();

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
          
          distant_nephew->color_red();
          sibling->color_black();
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
          sibling->color_black();
        } else {
          sibling->color_red();
        }
        parent->color_black();
        if (sibling->is_black()) {
        }

        // Color distant nephew black to restore black balance
        distant_nephew->color_black();
        return;
      }

      if (is_red(parent)) { // parent red, sibling and nephews black
        // Swap parent and sibling colors to restore black balance
        sibling->color_red();
        parent->color_black();
        return;
      }

      // Parent, sibling, and both nephews black
      // Color sibling red and move up one level
      sibling->color_red();
      node = parent;
      parent = node->_parent;
    }
  }

  // Assumption: node has at most one child. Two children is handled in `remove()`
  void remove_from_tree(RBNode* node) {
    RBNode* parent = node->_parent;
    RBNode* left = node->_left;
    RBNode* right = node->_right;
    if (left != nullptr) { // node has a left only-child
      // node must be black, and child red, otherwise a black-violation would exist
      // Remove node and color the child black.
      assert(right == nullptr, "right must be nullptr");
      assert(is_black(node), "node must be black");
      assert(is_red(left), "child must be red");
      left->color_black();
      left->_parent = parent;
      if (parent == nullptr) {
        assert(node == _root, "node must be root");
        _root = left;
      } else {
        parent->replace_child(node, left);
      }
    } else if (right != nullptr) { // node has a right only-child
      // node must be black, and child red, otherwise a black-violation would exist
      // Remove node and color the child black.
      assert(left == nullptr, "left must be nullptr");
      assert(is_black(node), "node must be black");
      assert(is_red(right), "child must be red");
      right->color_black();
      right->_parent = parent;
      if (parent == nullptr) {
        assert(node == _root, "node must be root");
        _root = right;
      } else {
        parent->replace_child(node, right);
      }
    } else { // node has no children
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

  void remove_all_inner(RBNode* node) {
    if (node == nullptr) {
      return;
    }
    remove_all_inner(node->_left);
    remove_all_inner(node->_right);
    free_node(node);
  }

public:
  NONCOPYABLE(RBTree);

  RBTree() : _allocator(), _num_nodes(0), _num_outdated(false), _root(nullptr) {}
  ~RBTree() { this->remove_all(); }

  size_t size() {
    if (_num_outdated) {
      _num_nodes = _root->count_nodes();
      _num_outdated = false;
    }
    return _num_nodes;
  }

  void upsert(const K& k, const V& v) {
    RBNode* node = insert_node(k, v);
    fix_insert_violations(node);
  }

  bool remove(const K& k) {
    RBNode* node = find(_root, k);
    return remove(node);
  }

  bool remove(RBNode* node) {
    if (node == nullptr) {
      return false;
    }

    if (node->_left != nullptr && node->_right != nullptr) { // node has two children
      // Copy the k/v from the in-order successor and delete that node instead
      RBNode* curr = node->_right;
      while (curr->_left != nullptr) {
        curr = curr->_left;
      }
      node->_key = curr->key();
      node->_value = curr->val();

      node = curr;
    }

    remove_from_tree(node);
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
    Range(RBNode* start, RBNode* end)
    : start(start), end(end) {}
  };

  // Return the range [start, end)
  // where start->key() <= addr < end->key().
  // Failure to find the range leads to start and/or end being null.
  Range find_enclosing_range(K addr) {
    RBNode* start = closest_leq(addr);
    RBNode* end = closest_gt(addr);
    return Range(start, end);
  }

  V* find(K& key) {
    RBNode* node = find(_root, key);
    if (node == nullptr) {
      return nullptr;
    }
    return &node->val();
  }

  // Visit all RBNodes in ascending order.
  template <typename F>
  void visit_in_order(F f) const {
    _root->visit_in_order_inner(f);
  }

  // Visit all RBNodes in ascending order whose keys are in range [from, to).
  template <typename F>
  void visit_range_in_order(const K& from, const K& to, F f) {
    if (_root == nullptr)
      return;
    _root->visit_range_in_order_inner(from, to, f);
  }

#ifdef ASSERT
  void verify_self() {
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

    assert(expected_num_nodes == actual_num_nodes, "unexpected number of nodes in rbtree. expected: " SIZE_FORMAT ", actual: " SIZE_FORMAT, expected_num_nodes, actual_num_nodes);
    assert(2 * black_nodes <= maximum_depth, "rbtree is too deep for its number of nodes. can be at most: " INT32_FORMAT ", but is: " UINT32_FORMAT, maximum_depth, 2 * black_nodes);
    assert(_root->is_correct(black_nodes), "rbtree does not hold rb-properties");
  }
#endif // ASSERT

private:
  template<bool Forward>
  class RBTreeIteratorImpl : public StackObj {
  private:
    const RBTree* const _tree;
    GrowableArrayCHeap<RBNode*, mtInternal> _to_visit;
    RBNode* _next;

    void push_left(RBNode* node) {
      while (node != nullptr) {
        _to_visit.push(node);
        node = node->_left;
      }
    }

    void push_right(RBNode* node) {
      while (node != nullptr) {
        _to_visit.push(node);
        node = node->_right;
      }
    }

  public:
    RBTreeIteratorImpl(const RBTree* tree) : _tree(tree) {
      Forward ? push_left(tree->_root) : push_right(tree->_root);
    }

    bool has_next() {
      return !_to_visit.is_empty();
    }

    RBNode* next() {
      RBNode* node = _to_visit.pop();
      if (node != nullptr) {
        Forward ? push_left(node->_right) : push_right(node->_left);
      }
      return node;
    }
  };

public:
  using RBTreeIterator = RBTreeIteratorImpl<true>; // Forward iterator
  using RBTreeReverseIterator = RBTreeIteratorImpl<false>; // Backward iterator

};

class RBTreeCHeapAllocator {
public:
  void* allocate(size_t sz) {
    void* allocation = os::malloc(sz, mtNMT);
    if (allocation == nullptr) {
      vm_exit_out_of_memory(sz, OOM_MALLOC_ERROR,
                            "red-black tree failed allocation");
    }
    return allocation;
  }

  void free(void* ptr) { os::free(ptr); }
};

template <typename K, typename V, typename COMPARATOR>
using RBTreeCHeap = RBTree<K, V, COMPARATOR, RBTreeCHeapAllocator>;


#endif // SHARE_UTILITIES_RBTREE_HPP
