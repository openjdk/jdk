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
 */

#include "precompiled.hpp"
#include "opto/vtransform.hpp"
#include "opto/vectornode.hpp"
#include "opto/convertnode.hpp"

void VTransformGraph::add_vtnode(VTransformNode* vtnode) {
  assert(vtnode->_idx == _vtnodes.length(), "position must match idx");
  _vtnodes.push(vtnode);
}

// Compute a linearization of the graph. We do this with a reverse-post-order of a DFS.
// This only works if the graph is a directed acyclic graph (DAG). The C2 graph, and
// the VLoopDependencyGraph are both DAGs, but after introduction of vectors/packs, the
// graph has additional constraints which can introduce cycles. Example:
//
//                                                       +--------+
//  A -> X                                               |        v
//                     Pack [A,B] and [X,Y]             [A,B]    [X,Y]
//  Y -> B                                                 ^        |
//                                                         +--------+
//
// We return "true" IFF we find no cycle, i.e. if the linearization succeeds.
bool VTransformGraph::schedule() {
  assert(!is_scheduled(), "not yet scheduled");

#ifndef PRODUCT
  if (_trace._verbose) {
    print_vtnodes();
  }
#endif

  ResourceMark rm;
  GrowableArray<VTransformNode*> stack;
  VectorSet pre_visited;
  VectorSet post_visited;

  collect_nodes_without_req_or_dependency(stack);

  // We create a reverse-post-visit order. This gives us a linearization, if there are
  // no cycles. Then, we simply reverse the order, and we have a schedule.
  int rpo_idx = _vtnodes.length() - 1;
  while (!stack.is_empty()) {
    VTransformNode* vtn = stack.top();
    if (!pre_visited.test_set(vtn->_idx)) {
      // Forward arc in graph (pre-visit).
    } else if (!post_visited.test(vtn->_idx)) {
      // Forward arc in graph. Check if all uses were already visited:
      //   Yes -> post-visit.
      //   No  -> we are mid-visit.
      bool all_uses_already_visited = true;

      for (int i = 0; i < vtn->outs(); i++) {
        VTransformNode* use = vtn->out(i);
        if (post_visited.test(use->_idx)) { continue; }
        if (pre_visited.test(use->_idx)) {
          // Cycle detected!
          // The nodes that are pre_visited but not yet post_visited form a path from
          // the "root" to the current vtn. Now, we are looking at an edge (vtn, use),
          // and discover that use is also pre_visited but not post_visited. Thus, use
          // lies on that path from "root" to vtn, and the edge (vtn, use) closes a
          // cycle.
          NOT_PRODUCT(if (_trace._rejections) { trace_schedule_cycle(stack, pre_visited, post_visited); } )
          return false;
        }
        stack.push(use);
        all_uses_already_visited = false;
      }

      if (all_uses_already_visited) {
        stack.pop();
        post_visited.set(vtn->_idx);           // post-visit
        _schedule.at_put_grow(rpo_idx--, vtn); // assign rpo_idx
      }
    } else {
      stack.pop(); // Already post-visited. Ignore secondary edge.
    }
  }

#ifndef PRODUCT
  if (_trace._verbose) {
    print_schedule();
  }
#endif

  assert(rpo_idx == -1, "used up all rpo_idx, rpo_idx=%d", rpo_idx);
  return true;
}

// Push all "root" nodes, i.e. those that have no inputs (req or dependency):
void VTransformGraph::collect_nodes_without_req_or_dependency(GrowableArray<VTransformNode*>& stack) const {
  for (int i = 0; i < _vtnodes.length(); i++) {
    VTransformNode* vtn = _vtnodes.at(i);
    if (!vtn->has_req_or_dependency()) {
      stack.push(vtn);
    }
  }
}

#ifndef PRODUCT
void VTransformGraph::trace_schedule_cycle(const GrowableArray<VTransformNode*>& stack,
                                           const VectorSet& pre_visited,
                                           const VectorSet& post_visited) const {
  tty->print_cr("\nVTransform::schedule found a cycle on path (P), vectorization attempt fails.");
  for (int j = 0; j < stack.length(); j++) {
    VTransformNode* n = stack.at(j);
    bool on_path = pre_visited.test(n->_idx) && !post_visited.test(n->_idx);
    tty->print("  %s ", on_path ? "P" : "_");
    n->print();
  }
}

void VTransformApplyResult::trace(VTransformNode* vtnode) const {
  tty->print("  apply: ");
  vtnode->print();
  tty->print("    ->   ");
  if (_node == nullptr) {
    tty->print_cr("nullptr");
  } else {
    _node->dump();
  }
}
#endif

Node* VTransformNode::find_transformed_input(int i, const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  Node* n = vnode_idx_to_transformed_node.at(in(i)->_idx);
  assert(n != nullptr, "must find input IR node");
  return n;
}

VTransformApplyResult VTransformScalarNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                  const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  // This was just wrapped. Now we simply unwap without touching the inputs.
  return VTransformApplyResult::make_scalar(_node);
}

VTransformApplyResult VTransformReplicateNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                     const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  Node* val = find_transformed_input(1, vnode_idx_to_transformed_node);
  VectorNode* vn = VectorNode::scalar2vector(val, _vlen, _element_type);
  register_new_node_from_vectorization(vloop_analyzer, vn, val);
  return VTransformApplyResult::make_vector(vn, _vlen, vn->length_in_bytes());
}

VTransformApplyResult VTransformConvI2LNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                   const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  Node* val = find_transformed_input(1, vnode_idx_to_transformed_node);
  Node* n = new ConvI2LNode(val);
  register_new_node_from_vectorization(vloop_analyzer, n, val);
  return VTransformApplyResult::make_scalar(n);
}

VTransformApplyResult VTransformShiftCountNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  PhaseIdealLoop* phase = vloop_analyzer.vloop().phase();
  Node* shift_count_in = find_transformed_input(1, vnode_idx_to_transformed_node);
  assert(shift_count_in->bottom_type()->isa_int(), "int type only for shift count");
  // The shift_count_in would be automatically truncated to the lowest _mask
  // bits in a scalar shift operation. But vector shift does not truncate, so
  // we must apply the mask now.
  Node* shift_count_masked = new AndINode(shift_count_in, phase->igvn().intcon(_mask));
  register_new_node_from_vectorization(vloop_analyzer, shift_count_masked, shift_count_in);
  // Now that masked value is "boadcast" (some platforms only set the lowest element).
  VectorNode* vn = VectorNode::shift_count(_shift_opcode, shift_count_masked, _vlen, _element_bt);
  register_new_node_from_vectorization(vloop_analyzer, vn, shift_count_in);
  return VTransformApplyResult::make_vector(vn, _vlen, vn->length_in_bytes());
}


VTransformApplyResult VTransformPopulateIndexNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                         const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  PhaseIdealLoop* phase = vloop_analyzer.vloop().phase();
  Node* val = find_transformed_input(1, vnode_idx_to_transformed_node);
  assert(val->is_Phi(), "expected to be iv");
  assert(VectorNode::is_populate_index_supported(_element_bt), "should support");
  const TypeVect* vt = TypeVect::make(_element_bt, _vlen);
  VectorNode* vn = new PopulateIndexNode(val, phase->igvn().intcon(1), vt);
  register_new_node_from_vectorization(vloop_analyzer, vn, val);
  return VTransformApplyResult::make_vector(vn, _vlen, vn->length_in_bytes());
}

VTransformApplyResult VTransformElementWiseVectorNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                             const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  Node* first = nodes().at(0);
  uint  vlen = nodes().length();
  int   opc  = first->Opcode();
  BasicType bt = vloop_analyzer.types().velt_basic_type(first);

  if (first->is_Cmp()) {
    // Cmp + Bool -> VectorMaskCmp
    // Handled by Bool / VTransformBoolVectorNode, so we do not generate any nodes here.
    return VTransformApplyResult::make_empty();
  }

  assert(2 <= req() && req() <= 4, "Must have 1-3 inputs");
  VectorNode* vn = nullptr;
  Node* in1 =                find_transformed_input(1, vnode_idx_to_transformed_node);
  Node* in2 = (req() >= 3) ? find_transformed_input(2, vnode_idx_to_transformed_node) : nullptr;
  Node* in3 = (req() >= 4) ? find_transformed_input(3, vnode_idx_to_transformed_node) : nullptr;

  if (first->is_CMove()) {
    assert(req() == 4, "three inputs expected: mask, blend1, blend2");
    vn = new VectorBlendNode(/* blend1 */ in2, /* blend2 */ in3, /* mask */ in1);
  } else if (VectorNode::is_convert_opcode(opc)) {
    assert(first->req() == 2 && req() == 2, "only one input expected");
    int vopc = VectorCastNode::opcode(opc, in1->bottom_type()->is_vect()->element_basic_type());
    vn = VectorCastNode::make(vopc, in1, bt, vlen);
  } else if (VectorNode::can_use_RShiftI_instead_of_URShiftI(first, bt)) {
    opc = Op_RShiftI;
    vn = VectorNode::make(opc, in1, in2, vlen, bt);
  } else if (VectorNode::is_scalar_op_that_returns_int_but_vector_op_returns_long(opc)) {
    // The scalar operation was a long -> int operation.
    // However, the vector operation is long -> long.
    VectorNode* long_vn = VectorNode::make(opc, in1, nullptr, vlen, T_LONG);
    register_new_node_from_vectorization(vloop_analyzer, long_vn, first);
    // Cast long -> int, to mimic the scalar long -> int operation.
    vn = VectorCastNode::make(Op_VectorCastL2X, long_vn, T_INT, vlen);
  } else if (req() == 3 ||
             VectorNode::is_scalar_unary_op_with_equal_input_and_output_types(opc)) {
    assert(!VectorNode::is_roundopD(first) || in2->is_Con(), "rounding mode must be constant");
    vn = VectorNode::make(opc, in1, in2, vlen, bt); // unary and binary
  } else {
    assert(req() == 4, "three inputs expected");
    assert(opc == Op_FmaD ||
           opc == Op_FmaF ||
           opc == Op_SignumF ||
           opc == Op_SignumD,
           "element wise operation must be from this list");
    vn = VectorNode::make(opc, in1, in2, in3, vlen, bt); // ternary
  }

  register_new_node_from_vectorization_and_replace_scalar_nodes(vloop_analyzer, vn);
  return VTransformApplyResult::make_vector(vn, vlen, vn->length_in_bytes());
}

VTransformApplyResult VTransformBoolVectorNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  BoolNode* first = nodes().at(0)->as_Bool();
  uint  vlen = nodes().length();
  BasicType bt = vloop_analyzer.types().velt_basic_type(first);

  // Cmp + Bool -> VectorMaskCmp
  VTransformElementWiseVectorNode* vtn_cmp = in(1)->isa_ElementWiseVector();
  assert(vtn_cmp != nullptr && vtn_cmp->nodes().at(0)->is_Cmp(),
         "bool vtn expects cmp vtn as input");

  Node* cmp_in1 = vtn_cmp->find_transformed_input(1, vnode_idx_to_transformed_node);
  Node* cmp_in2 = vtn_cmp->find_transformed_input(2, vnode_idx_to_transformed_node);
  BoolTest::mask mask = test()._mask;

  PhaseIdealLoop* phase = vloop_analyzer.vloop().phase();
  ConINode* mask_node  = phase->igvn().intcon((int)mask);
  const TypeVect* vt = TypeVect::make(bt, vlen);
  VectorNode* vn = new VectorMaskCmpNode(mask, cmp_in1, cmp_in2, mask_node, vt);
  register_new_node_from_vectorization_and_replace_scalar_nodes(vloop_analyzer, vn);
  return VTransformApplyResult::make_vector(vn, vlen, vn->vect_type()->length_in_bytes());
}

VTransformApplyResult VTransformReductionVectorNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                           const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  Node* first = nodes().at(0);
  uint  vlen = nodes().length();
  int   opc  = first->Opcode();
  BasicType bt = first->bottom_type()->basic_type();

  Node* init = find_transformed_input(1, vnode_idx_to_transformed_node);
  Node* vec  = find_transformed_input(2, vnode_idx_to_transformed_node);

  ReductionNode* vn = ReductionNode::make(opc, nullptr, init, vec, bt);
  register_new_node_from_vectorization_and_replace_scalar_nodes(vloop_analyzer, vn);
  return VTransformApplyResult::make_vector(vn, vlen, vn->vect_type()->length_in_bytes());
}

VTransformApplyResult VTransformLoadVectorNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  LoadNode* first = nodes().at(0)->as_Load();
  uint  vlen = nodes().length();
  Node* ctrl = first->in(MemNode::Control);
  Node* mem  = first->in(MemNode::Memory);
  Node* adr  = first->in(MemNode::Address);
  int   opc  = first->Opcode();
  const TypePtr* adr_type = first->adr_type();
  BasicType bt = vloop_analyzer.types().velt_basic_type(first);

  // Set the memory dependency of the LoadVector as early as possible.
  // Walk up the memory chain, and ignore any StoreVector that provably
  // does not have any memory dependency.
  while (mem->is_StoreVector()) {
    VPointer p_store(mem->as_Mem(), vloop_analyzer.vloop());
    if (p_store.overlap_possible_with_any_in(nodes())) {
      break;
    } else {
      mem = mem->in(MemNode::Memory);
    }
  }

  LoadVectorNode* vn = LoadVectorNode::make(opc, ctrl, mem, adr, adr_type, vlen, bt,
                                            control_dependency());
  DEBUG_ONLY( if (VerifyAlignVector) { vn->set_must_verify_alignment(); } )
  register_new_node_from_vectorization_and_replace_scalar_nodes(vloop_analyzer, vn);
  return VTransformApplyResult::make_vector(vn, vlen, vn->memory_size());
}

VTransformApplyResult VTransformStoreVectorNode::apply(const VLoopAnalyzer& vloop_analyzer,
                                                       const GrowableArray<Node*>& vnode_idx_to_transformed_node) const {
  StoreNode* first = nodes().at(0)->as_Store();
  uint  vlen = nodes().length();
  Node* ctrl = first->in(MemNode::Control);
  Node* mem  = first->in(MemNode::Memory);
  Node* adr  = first->in(MemNode::Address);
  int   opc  = first->Opcode();
  const TypePtr* adr_type = first->adr_type();

  Node* value = find_transformed_input(MemNode::ValueIn, vnode_idx_to_transformed_node);
  StoreVectorNode* vn = StoreVectorNode::make(opc, ctrl, mem, adr, adr_type, value, vlen);
  DEBUG_ONLY( if (VerifyAlignVector) { vn->set_must_verify_alignment(); } )
  register_new_node_from_vectorization_and_replace_scalar_nodes(vloop_analyzer, vn);
  return VTransformApplyResult::make_vector(vn, vlen, vn->memory_size());
}

void VTransformVectorNode::register_new_node_from_vectorization_and_replace_scalar_nodes(const VLoopAnalyzer& vloop_analyzer, Node* vn) const {
  PhaseIdealLoop* phase = vloop_analyzer.vloop().phase();
  Node* first = nodes().at(0);

  register_new_node_from_vectorization(vloop_analyzer, vn, first);

  for (int i = 0; i < _nodes.length(); i++) {
    Node* n = _nodes.at(i);
    phase->igvn().replace_node(n, vn);
  }
}

void VTransformNode::register_new_node_from_vectorization(const VLoopAnalyzer& vloop_analyzer, Node* vn, Node* old_node) const {
  PhaseIdealLoop* phase = vloop_analyzer.vloop().phase();
  phase->register_new_node_with_ctrl_of(vn, old_node);
  phase->igvn()._worklist.push(vn);
  VectorNode::trace_new_vector(vn, "AutoVectorization");
}

#ifndef PRODUCT
void VTransformGraph::print_vtnodes() const {
  tty->print_cr("\nVTransformGraph::print_vtnodes:");
  for (int i = 0; i < _vtnodes.length(); i++) {
    _vtnodes.at(i)->print();
  }
}

void VTransformGraph::print_schedule() const {
  tty->print_cr("\nVTransformGraph::print_schedule:");
  for (int i = 0; i < _schedule.length(); i++) {
    tty->print(" %3d: ", i);
    VTransformNode* vtn = _schedule.at(i);
    if (vtn == nullptr) {
      tty->print_cr("nullptr");
    } else {
      vtn->print();
    }
  }
}

void VTransformGraph::print_memops_schedule() const {
  tty->print_cr("\nVTransformGraph::print_memops_schedule:");
  int i = 0;
  for_each_memop_in_schedule([&] (MemNode* mem) {
    tty->print(" %3d: ", i++);
    mem->dump();
  });
}

void VTransformNode::print() const {
  tty->print("%3d %s (", _idx, name());
  for (uint i = 0; i < _req; i++) {
    print_node_idx(_in.at(i));
  }
  if ((uint)_in.length() > _req) {
    tty->print(" |");
    for (int i = _req; i < _in.length(); i++) {
      print_node_idx(_in.at(i));
    }
  }
  tty->print(") [");
  for (int i = 0; i < _out.length(); i++) {
    print_node_idx(_out.at(i));
  }
  tty->print("] ");
  print_spec();
  tty->cr();
}

void VTransformNode::print_node_idx(const VTransformNode* vtn) {
  if (vtn == nullptr) {
    tty->print(" _");
  } else {
    tty->print(" %d", vtn->_idx);
  }
}

void VTransformScalarNode::print_spec() const {
  tty->print("node[%d %s]", _node->_idx, _node->Name());
}

void VTransformReplicateNode::print_spec() const {
  tty->print("vlen=%d element_type=", _vlen);
  _element_type->dump();
}

void VTransformShiftCountNode::print_spec() const {
  tty->print("vlen=%d element_bt=%s mask=%d shift_opcode=%s",
             _vlen, type2name(_element_bt), _mask,
             NodeClassNames[_shift_opcode]);
}

void VTransformPopulateIndexNode::print_spec() const {
  tty->print("vlen=%d element_bt=%s", _vlen, type2name(_element_bt));
}

void VTransformVectorNode::print_spec() const {
  tty->print("%d-pack[", _nodes.length());
  for (int i = 0; i < _nodes.length(); i++) {
    Node* n = _nodes.at(i);
    if (i > 0) {
      tty->print(", ");
    }
    tty->print("%d %s", n->_idx, n->Name());
  }
  tty->print("]");
}
#endif
