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

#ifndef SHARE_NMT_TREAP_HPP
#define SHARE_NMT_TREAP_HPP

#include "memory/allocation.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include <stdint.h>

// A Treap is a self-balanced binary tree where each node is equipped with a
// priority. It adds the invariant that the priority of a parent P is strictly larger
// larger than the priority of its children. When priorities are randomly
// assigned the tree is balanced.
// All operations are defined through merge and split, which are each other's inverse.
// merge(left_treap, right_treap) => treap where left_treap <= right_treap
// split(treap, key) => (left_treap, right_treap)  where left_treap <= right_treap
// Recursion is used in these, but the depth of the call stack is the depth of
// the tree which is O(log n) so we are safe from stack overflow.

// TreapNode has LEQ nodes on the left, GT nodes on the right.
template<typename K, typename V, int(*CMP)(K,K)>
class TreapNode {
  template<typename InnerK, typename InnerV, int(*CMPP)(InnerK,InnerK), typename Allocator>
  friend class Treap;

  using Node = TreapNode<K,V,CMP>;

  uint64_t _priority;
  const K _key;
  V _value;

  Node* _left;
  Node* _right;

public:
  TreapNode(const K& k, const V& v, uint64_t p)
  : _priority(p), _key(k), _value(v), _left(nullptr), _right(nullptr) {
  }

  const K& key() const {
    return _key;
  }

  V& val() {
    return _value;
  }

  Node* left() const {
    return _left;
  }

  Node* right() const {
    return _right;
  }
};

template<typename K, typename V, int(*CMP)(K,K), typename ALLOCATOR>
class Treap {
  friend class VMATree;
  friend class VMATreeTest;

  using Node = TreapNode<K, V, CMP>;

  Node* _root;
  uint64_t _prng_seed;

private:
  uint64_t prng_next() {
    // Taken directly off of JFRPrng
    static const uint64_t PrngMult = 0x5DEECE66DLL;
    static const uint64_t PrngAdd = 0xB;
    static const uint64_t PrngModPower = 48;
    static const uint64_t PrngModMask = (static_cast<uint64_t>(1) << PrngModPower) - 1;
    _prng_seed = (PrngMult * _prng_seed + PrngAdd) & PrngModMask;
    return _prng_seed;
  }

  struct node_pair {
    Node* left;
    Node* right;
  };

  enum SplitMode {
    LT, // <
    LEQ // <=
  };

  // Split tree at head into two trees, SplitMode decides where EQ values go.
  // We have SplitMode because it makes remove() trivial to implement.
  static node_pair split(Node* head, const K& key, SplitMode mode = LEQ DEBUG_ONLY(COMMA int recur_count = 0)) {
    assert(recur_count < 200, "Call-stack depth should never exceed 200");

    if (head == nullptr) {
      return {nullptr, nullptr};
    }
    if ((CMP(head->_key, key) <= 0 && mode == LEQ) || (CMP(head->_key, key) < 0 && mode == LT)) {
      node_pair p = split(head->_right, key, mode DEBUG_ONLY(COMMA recur_count + 1));
      head->_right = p.left;
      return node_pair{head, p.right};
    } else {
      node_pair p = split(head->_left, key, mode DEBUG_ONLY(COMMA recur_count + 1));
      head->_left = p.right;
      return node_pair{p.left, head};
    }
  }

  // Invariant: left is a treap whose keys are LEQ to the keys in right.
  static Node* merge(Node* left, Node* right DEBUG_ONLY(COMMA int recur_count = 0)) {
    assert(recur_count < 200, "Call-stack depth should never exceed 200");

    if (left == nullptr) return right;
    if (right == nullptr) return left;

    if (left->_priority > right->_priority) {
      // We need
      //      LEFT
      //         |
      //         RIGHT
      // for the invariant re: priorities to hold.
      left->_right = merge(left->_right, right DEBUG_ONLY(COMMA recur_count + 1));
      return left;
    } else {
      // We need
      //         RIGHT
      //         |
      //      LEFT
      // for the invariant re: priorities to hold.
      right->_left = merge(left, right->_left DEBUG_ONLY(COMMA recur_count + 1));
      return right;
    }
  }

  static Node* find(Node* node, const K& k DEBUG_ONLY(COMMA int recur_count = 0)) {
    if (node == nullptr) {
      return nullptr;
    }
    if (CMP(node->_key, k) == 0) { // EQ
      return node;
    }

    if (CMP(node->_key, k) <= 0) { // LEQ
      return find(node->_left, k DEBUG_ONLY(COMMA recur_count + 1));
    } else {
      return find(node->_right, k DEBUG_ONLY(COMMA recur_count + 1));
    }
  }
public:
  Treap(uint64_t seed = 1234)
    : _root(nullptr),
      _prng_seed(seed) {
  }
  ~Treap() {
    this->remove_all();
  }

  void upsert(const K& k, const V& v) {
    // (LEQ_k, GT_k)
    node_pair split_up = split(this->_root, k);
    Node* found = find(split_up.left, k);
    if (found != nullptr) {
      // Already exists, update value.
      found->_value = v;
      this->_root = merge(split_up.left, split_up.right);
    }
    // Doesn't exist, make node
    void* node_place = ALLOCATOR::allocate(sizeof(Node));
    uint64_t prio = prng_next();
    Node* node = new (node_place) Node(k, v, prio);
    // merge(merge(LEQ_k, EQ_k), GT_k)
    this->_root = merge(merge(split_up.left, node), split_up.right);
  }

  void remove(const K& k) {
    // (LEQ_k, GT_k)
    node_pair fst_split = split(this->_root, k, LEQ);
    // (LT_k, GEQ_k) == (LT_k, EQ_k) since it's from LEQ_k and keys are unique.
    node_pair snd_split = split(fst_split.left, k, LT);

    if (snd_split.right != nullptr) {
      // The key k existed, we delete it.
      ALLOCATOR::free(snd_split.right);
    }
    // Merge together everything
    this->_root = merge(snd_split.left, fst_split.right);
  }

  // Delete all nodes.
  void remove_all() {
    GrowableArrayCHeap<Node*, mtNMT> to_delete;
    to_delete.push(this->_root);

    while (!to_delete.is_empty()) {
      Node* head = to_delete.pop();
      if (head == nullptr) continue;
      to_delete.push(head->_left);
      to_delete.push(head->_right);
      ALLOCATOR::free(head);
    }
  }

  Node* closest_geq(const K& key) {
    // Need to go "left-ward" for EQ node, so do a leq search first.
    Node* leqB = closest_leq(key);
    if (leqB != nullptr && leqB->key() == key) {
      return leqB;
    }
    Node* gtB = nullptr;
    Node* head = _root;
    while (head != nullptr) {
      int cmp_r = CMP(head->key(), key);
      if (cmp_r == 0) { // Exact match
        gtB = head;
        break; // Can't become better than that.
      }
      if (cmp_r > 0) {
        // Found a match, try to find a better one.
        gtB = head;
        head = head->_left;
      } else if (cmp_r < 0) {
        head = head->_right;
      }
    }
    return gtB;
  }

  Node* closest_leq(const K& key) {
    Node* leqA_n = nullptr;
    Node* head = _root;
    while (head != nullptr) {
      int cmp_r = CMP(head->key(), key);
      if (cmp_r == 0) { // Exact match
        leqA_n = head;
        break; // Can't become better than that.
      }
      if (cmp_r < 0) {
        // Found a match, try to find a better one.
        leqA_n = head;
        head = head->_right;
      } else if (cmp_r > 0) {
        head = head->_left;
      }
    }
    return leqA_n;
  }
};

class TreapCHeapAllocator {
public:
  static void* allocate(size_t sz) {
    return os::malloc(sz, mtNMT);
  }

  static void free(void* ptr) {
    os::free(ptr);
  }
};

template<typename K, typename V, int (*CMP)(K, K)>
using TreapCHeap = Treap<K, V, CMP, TreapCHeapAllocator>;

#endif //SHARE_NMT_TREAP_HPP
