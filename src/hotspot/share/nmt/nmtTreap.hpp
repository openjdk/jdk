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

#include "utilities/globalDefinitions.hpp"
#include "memory/allocation.hpp"
#include "runtime/os.hpp"
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
  template<typename InnerK, typename InnerV, int(*CMPP)(InnerK,InnerK)>
  friend class TreapCHeap;

  uint64_t _priority;
  const K _key;
  V _value;
  using Node = TreapNode<K,V,CMP>;
  Node* _left;
  Node* _right;

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
  static node_pair split(Node* head, const K& key, SplitMode mode = LEQ) {
    if (head == nullptr) {
      return {nullptr, nullptr};
    }
    if ( (CMP(head->_key, key) <= 0 && mode == LEQ) ||
         (CMP(head->_key, key) < 0  && mode == LT) ) {
      node_pair p = split(head->_right, key, mode);
      head->_right = p.left;
      return node_pair{head, p.right};
    } else {
      node_pair p = split(head->_left, key, mode);
      head->_left = p.right;
      return node_pair{p.left, head};
    }
  }

  // Invariant: left is a treap whose keys are LEQ to the keys in right.
  static Node* merge(Node* left, Node* right) {
    if (left == nullptr) return right;
    if (right == nullptr) return left;

    if (left->_priority > right->_priority) {
      // We need
      //      LEFT
      //         |
      //         RIGHT
      // For the invariant re: priorities to hold.
      left->_right = merge(left->_right, right);
      return left;
    } else {
      // We need
      //         RIGHT
      //         |
      //      LEFT
      // For the invariant re: priorities to hold.
      right->_left = merge(left, right->_left);
      return right;
    }
  }

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

  static Node* find(Node* node, const K& k) {
    if (node == nullptr) {
      return nullptr;
    }
    if (CMP(node->_key, k) == 0) { // EQ
      return node;
    }

    if (CMP(node->_key, k) <= 0) { // LEQ
      return find(node->_left, k);
    } else {
      return find(node->_right, k);
    }
  }

  template<typename MakeNode>
  static Node* upsert(Node* head, const K& k, const V& v, MakeNode make_node) {
    // (LEQ_k, GT_k)
    node_pair split = Node::split(head, k);
    Node* found = find(split.left, k);
    if (found != nullptr) {
      // Already exists, update value.
      found->_value = v;
      return merge(split.left, split.right);
    }
    // Doesn't exist, make node
    Node* node = make_node(k, v);
    // merge(merge(LEQ_k, EQ_k), GT_k)
    return merge(merge(split.left, node), split.right);
  }

  template<typename Free>
  static Node* remove(Node *head, const K& k, Free free) {
    // (LEQ_k, GT_k)
    node_pair fst_split = split(head, k, LEQ);
    // (LT_k, GEQ_k) == (LT_k, EQ_k) since it's from LEQ_k and keys are unique.
    node_pair snd_split = split(fst_split.left, k, LT);

    if (snd_split.right != nullptr) {
      // The key k existed, we delete it.
      free(snd_split.right);
    }
    // Merge together everything
    return merge(snd_split.left, fst_split.right);
  }

  // Delete all nodes.
  template<typename Free>
  static Node* remove_all(Node* tree, Free free) {
    GrowableArrayCHeap<Node*, mtNMT> to_delete;
    to_delete.push(tree);

    while (!to_delete.is_empty()) {
      Node* head = to_delete.pop();
      if (head == nullptr) continue;
      to_delete.push(head->_left);
      to_delete.push(head->_right);
      free(head);
    }
    return nullptr;
  }
};

template<typename K, typename V, int(*CMP)(K,K)>
class TreapCHeap {
  friend class VMATree;
  friend class VMATreeTest;
  using Node = TreapNode<K, V, CMP>;
  Node* _root;
  uint64_t _prng_seed;

public:
  TreapCHeap(uint64_t seed = 1234) : _root(nullptr), _prng_seed(seed) {
  }
  ~TreapCHeap() {
    this->remove_all();
  }

  uint64_t prng_next() {
    // Taken directly off of JFRPrng
    static const uint64_t PrngMult = 0x5DEECE66DLL;
    static const uint64_t PrngAdd = 0xB;
    static const uint64_t PrngModPower = 48;
    static const uint64_t PrngModMask = (static_cast<uint64_t>(1) << PrngModPower) - 1;
    _prng_seed = (PrngMult * _prng_seed + PrngAdd) & PrngModMask;
    return _prng_seed;
  }

  void upsert(const K& k, const V& v) {
    _root = Node::upsert(_root, k, v, [&](const K& k, const V& v) {
      uint64_t rand = this->prng_next();
      void* place = os::malloc(sizeof(Node), mtNMT);
      new (place) Node(k, v, rand);
      return (Node*)place;
    });
  }

  void remove(const K& k) {
    _root = Node::remove(_root, k, [](void* ptr) {
      os::free(ptr);
    });
  }

  void remove_all() {
    _root = Node::remove_all(_root, [](void* ptr){
      os::free(ptr);
    });
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

#endif //SHARE_NMT_TREAP_HPP
