/*
 * Copyright (c) 2026, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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

#include "opto/genericOpts.hpp"
#include "opto/vectornode.hpp"

// This file implements the generic optimization framework.
//
// Each optimization is a GenericOpts method written against NodeOps<NT>.
// NodeOps<NT> declares the fixed primitive operation interfaces.
// NodeOpsTraits<NT> defines them for one type of node. The interface stays
// the same across node types; the traits supply the matching and construction
// for that node type.
//
// A node type is represented by a NodeTypeCon, such as VECTOR for vector and
// PVectMask nodes. Nodes of the same type have essentially the same primitive
// operations, so the optimization decouples from each specific node.
//
// NodeType maps the root node class to a NodeTypeCon. Define one specialization
// for each root class so a call site can deduce NodeTypeCon from that class.

enum class NodeTypeCon {
  VECTOR  // Vector and PVectMask (e.g. XorV / XorVMask)
};

template<typename NodeClass>
struct NodeType;

template<NodeTypeCon NT>
struct NodeOpsTraits;

// Fixed interface. Every NodeTypeCon implements these functions through
// NodeOpsTraits<NT>.
template<NodeTypeCon NT>
struct NodeOps {
  static bool is_and(const Node* n) {
    return NodeOpsTraits<NT>::is_and(n);
  }
  static bool is_xor(const Node* n) {
    return NodeOpsTraits<NT>::is_xor(n);
  }
  static Node* make_and(Node* in1, Node* in2) {
    return NodeOpsTraits<NT>::make_and(in1, in2);
  }
  static Node* make_xor(Node* in1, Node* in2) {
    return NodeOpsTraits<NT>::make_xor(in1, in2);
  }
  static Node* make_not(PhaseGVN* phase, Node* in) {
    return NodeOpsTraits<NT>::make_not(phase, in);
  }
};

template<>
struct NodeType<XorVNode> {
  static constexpr NodeTypeCon value = NodeTypeCon::VECTOR;
};

template<>
struct NodeOpsTraits<NodeTypeCon::VECTOR> {
  // Unpredicated AndV, or AndVMask when n is a PVectMask.
  static bool is_and(const Node* n) {
    return !n->is_predicated_vector() &&
           n->Opcode() == (is_pvectmask(n) ? Op_AndVMask : Op_AndV);
  }

  // Unpredicated XorV, or XorVMask when n is a PVectMask.
  static bool is_xor(const Node* n) {
    return !n->is_predicated_vector() &&
           n->Opcode() == (is_pvectmask(n) ? Op_XorVMask : Op_XorV);
  }

  static Node* make_and(Node* in1, Node* in2) {
    return VectorNode::make(Op_AndV, in1, in2, vect_type(in1), is_pvectmask(in1));
  }

  static Node* make_xor(Node* in1, Node* in2) {
    return VectorNode::make(Op_XorV, in1, in2, vect_type(in1), is_pvectmask(in1));
  }

  // XorV / XorVMask with an all-ones vector.
  static Node* make_not(PhaseGVN* phase, Node* in) {
    const TypeVect* vt = vect_type(in);
    uint vlen = vt->length();
    BasicType bt = vt->element_basic_type();
    bool is_mask = is_pvectmask(in);
    Node* all_ones = phase->transform(VectorNode::make_con(phase, vlen, bt, -1, is_mask));
    return make_xor(in, all_ones);
  }

 private:
  static bool is_pvectmask(const Node* n) {
    return n->bottom_type()->isa_pvectmask() != nullptr;
  }

  static const TypeVect* vect_type(const Node* n) {
    return n->bottom_type()->is_vect();
  }
};

// Canonicalize `(A & B) ^ B` to `~A & B`.
template<typename NodeClass>
Node* GenericOpts::Ideal_XorAnd_to_AndNot(NodeClass* n, PhaseGVN* phase) {
  constexpr NodeTypeCon nt = NodeType<NodeClass>::value;
  using T = NodeOps<nt>;

  if (!T::is_xor(n)) {
    return nullptr;
  }

  Node* and_node = n->in(1);
  Node* shared = n->in(2);
  if (!T::is_and(and_node)) {
    and_node = n->in(2);
    shared = n->in(1);
  }

  if (!T::is_and(and_node)) {
    return nullptr;
  }

  // `And` must share one operand with `Xor`.
  Node* other = nullptr;
  if (and_node->in(1) == shared) {
    other = and_node->in(2);
  } else if (and_node->in(2) == shared) {
    other = and_node->in(1);
  } else {
    return nullptr;
  }

  // `And` should only have a single use, otherwise the optimization may be
  // unprofitable.
  if (and_node->outcnt() != 1) {
    return nullptr;
  }

  // Neither `shared` nor `other` should be the Top node.
  if (shared->is_top() || other->is_top()) {
    return nullptr;
  }

  Node* not_other = phase->transform(T::make_not(phase, other));
  return T::make_and(shared, not_other);
}

template Node* GenericOpts::Ideal_XorAnd_to_AndNot<XorVNode>(XorVNode* n, PhaseGVN* phase);
