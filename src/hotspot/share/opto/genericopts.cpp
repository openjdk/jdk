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

#include "opto/genericopts.hpp"
#include "opto/vectornode.hpp"

// This file implements the generic optimization framework.
//
// Each optimization is a GenericOpts method written against a NT type.
// NodeOps<NT> supplies the primitive operations, the matching and
// construction, for one NT type.
//
// A NT type is represented by a NodeTypeCon, such as VECTOR for vector
// nodes and PVECTMASK for PVectMask nodes. Nodes of the same NT type have
// essentially the same primitive operations, so the optimization decouples
// from each specific node type.
//
// NodeType maps the root node class to a NT type. Define one specialization
// for each root class so a call site can deduce the NT type from that class.

enum class NodeTypeCon {
  VECTOR,    // Vector, e.g. XorV
  PVECTMASK  // PVectMask, e.g. XorVMask
};

template<typename NodeClass>
struct NodeType;

template<NodeTypeCon NT>
struct NodeOps;

template<>
struct NodeType<XorVNode> {
  static constexpr NodeTypeCon value = NodeTypeCon::VECTOR;
};

template<>
struct NodeType<XorVMaskNode> {
  static constexpr NodeTypeCon value = NodeTypeCon::PVECTMASK;
};

template<>
struct NodeOps<NodeTypeCon::VECTOR> {
  // Unpredicated AndV.
  static bool is_and(const Node* n) {
    return n->Opcode() == Op_AndV && !n->is_predicated_vector();
  }

  // Unpredicated XorV.
  static bool is_xor(const Node* n) {
    return n->Opcode() == Op_XorV && !n->is_predicated_vector();
  }

  static Node* make_and(Node* in1, Node* in2) {
    return new AndVNode(in1, in2, in1->bottom_type()->is_vect());
  }

  static Node* make_xor(Node* in1, Node* in2) {
    return new XorVNode(in1, in2, in1->bottom_type()->is_vect());
  }

  // XorV with a Replicate -1.
  static Node* make_not(PhaseGVN* phase, Node* in) {
    const TypeVect* vt = in->bottom_type()->is_vect();
    uint vlen = vt->length();
    BasicType bt = vt->element_basic_type();
    Node* all_ones = phase->transform(VectorNode::make_con(phase, vlen, bt,
                                                           -1, false));
    return make_xor(in, all_ones);
  }
};

template<>
struct NodeOps<NodeTypeCon::PVECTMASK> {
  // Unpredicated AndVMask.
  static bool is_and(const Node* n) {
    return n->Opcode() == Op_AndVMask && !n->is_predicated_vector();
  }

  // Unpredicated XorVMask.
  static bool is_xor(const Node* n) {
    return n->Opcode() == Op_XorVMask && !n->is_predicated_vector();
  }

  static Node* make_and(Node* in1, Node* in2) {
    return new AndVMaskNode(in1, in2, in1->bottom_type()->is_pvectmask());
  }

  static Node* make_xor(Node* in1, Node* in2) {
    return new XorVMaskNode(in1, in2, in1->bottom_type()->is_pvectmask());
  }

  // XorVMask with a MaskAll -1.
  static Node* make_not(PhaseGVN* phase, Node* in) {
    const TypeVect* vt = in->bottom_type()->is_pvectmask();
    uint vlen = vt->length();
    BasicType bt = vt->element_basic_type();
    Node* all_ones = phase->transform(VectorNode::make_con(phase, vlen, bt,
                                                           -1, true));
    return make_xor(in, all_ones);
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
template Node* GenericOpts::Ideal_XorAnd_to_AndNot<XorVMaskNode>(XorVMaskNode* n, PhaseGVN* phase);
