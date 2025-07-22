/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "opto/superwordVTransformBuilder.hpp"
#include "opto/vectornode.hpp"

void SuperWordVTransformBuilder::build() {
  assert(!_packset.is_empty(), "must have non-empty packset");
  assert(!_vtransform.has_graph(), "start with empty vtransform");

  // Create vtnodes for all nodes in the loop.
  build_vector_vtnodes_for_packed_nodes();
  build_scalar_vtnodes_for_non_packed_nodes();

  // Connect all vtnodes with their inputs. Possibly create vtnodes for input
  // nodes that are outside the loop.
  VectorSet vtn_memory_dependencies; // Shared, but cleared for every vtnode.
  build_inputs_for_vector_vtnodes(vtn_memory_dependencies);
  build_inputs_for_scalar_vtnodes(vtn_memory_dependencies);
}

void SuperWordVTransformBuilder::build_vector_vtnodes_for_packed_nodes() {
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* pack = _packset.at(i);
    VTransformVectorNode* vtn = make_vector_vtnode_for_pack(pack);
    for (uint k = 0; k < pack->size(); k++) {
      map_node_to_vtnode(pack->at(k), vtn);
    }
  }
}

void SuperWordVTransformBuilder::build_scalar_vtnodes_for_non_packed_nodes() {
  for (int i = 0; i < _vloop_analyzer.body().body().length(); i++) {
    Node* n = _vloop_analyzer.body().body().at(i);
    if (_packset.get_pack(n) != nullptr) { continue; }
    VTransformScalarNode* vtn = new (_vtransform.arena()) VTransformScalarNode(_vtransform, n);
    map_node_to_vtnode(n, vtn);
  }
}

void SuperWordVTransformBuilder::build_inputs_for_vector_vtnodes(VectorSet& vtn_memory_dependencies) {
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* pack = _packset.at(i);
    Node* p0 = pack->at(0);

    VTransformVectorNode* vtn = get_vtnode(p0)->isa_Vector();
    assert(vtn != nullptr, "all packs must have vector vtnodes");
    vtn_memory_dependencies.clear(); // Add every memory dependency only once per vtn.

    if (p0->is_Load()) {
      set_req_with_scalar(p0,   vtn, MemNode::Address);
      for (uint k = 0; k < pack->size(); k++) {
        add_memory_dependencies_of_node_to_vtnode(pack->at(k), vtn, vtn_memory_dependencies);
      }
    } else if (p0->is_Store()) {
      set_req_with_scalar(p0,   vtn, MemNode::Address);
      set_req_with_vector(pack, vtn, MemNode::ValueIn);
      for (uint k = 0; k < pack->size(); k++) {
        add_memory_dependencies_of_node_to_vtnode(pack->at(k), vtn, vtn_memory_dependencies);
      }
    } else if (vtn->isa_ReductionVector() != nullptr) {
      set_req_with_scalar(p0,   vtn, 1); // scalar init
      set_req_with_vector(pack, vtn, 2); // vector
    } else {
      assert(vtn->isa_ElementWiseVector() != nullptr, "all other vtnodes are handled above");
      if (VectorNode::is_scalar_rotate(p0) &&
          p0->in(2)->is_Con() &&
          Matcher::supports_vector_constant_rotates(p0->in(2)->get_int())) {
        set_req_with_vector(pack, vtn, 1);
        set_req_with_scalar(p0,   vtn, 2); // constant rotation
      } else if (VectorNode::is_roundopD(p0)) {
        set_req_with_vector(pack, vtn, 1);
        set_req_with_scalar(p0,   vtn, 2); // constant rounding mode
      } else if (p0->is_CMove()) {
        // Cmp + Bool + CMove -> VectorMaskCmp + VectorBlend.
        set_all_req_with_vectors(pack, vtn);
        VTransformBoolVectorNode* vtn_mask_cmp = vtn->in(1)->isa_BoolVector();
        if (vtn_mask_cmp->test()._is_negated) {
          vtn->swap_req(2, 3); // swap if test was negated.
        }
      } else {
        set_all_req_with_vectors(pack, vtn);
      }
    }
  }
}

void SuperWordVTransformBuilder::build_inputs_for_scalar_vtnodes(VectorSet& vtn_memory_dependencies) {
  for (int i = 0; i < _vloop_analyzer.body().body().length(); i++) {
    Node* n = _vloop_analyzer.body().body().at(i);
    VTransformScalarNode* vtn = get_vtnode(n)->isa_Scalar();
    if (vtn == nullptr) { continue; }
    vtn_memory_dependencies.clear(); // Add every dependency only once per vtn.

    if (n->is_Load()) {
      set_req_with_scalar(n, vtn, MemNode::Address);
      add_memory_dependencies_of_node_to_vtnode(n, vtn, vtn_memory_dependencies);
    } else if (n->is_Store()) {
      set_req_with_scalar(n, vtn, MemNode::Address);
      set_req_with_scalar(n, vtn, MemNode::ValueIn);
      add_memory_dependencies_of_node_to_vtnode(n, vtn, vtn_memory_dependencies);
    } else if (n->is_CountedLoop()) {
      continue; // Is "root", has no dependency.
    } else if (n->is_Phi()) {
      // CountedLoop Phi's: ignore backedge (and entry value).
      assert(n->in(0) == _vloop.cl(), "only Phi's from the CountedLoop allowed");
      set_req_with_scalar(n, vtn, 0);
      continue;
    } else {
      set_all_req_with_scalars(n, vtn);
    }
  }
}

// Create a vtnode for each pack. No in/out edges set yet.
VTransformVectorNode* SuperWordVTransformBuilder::make_vector_vtnode_for_pack(const Node_List* pack) const {
  uint pack_size = pack->size();
  Node* p0 = pack->at(0);
  int opc = p0->Opcode();
  VTransformVectorNode* vtn = nullptr;

  if (p0->is_Load()) {
    const VPointer& scalar_p = _vloop_analyzer.vpointers().vpointer(p0->as_Load());
    const VPointer vector_p(scalar_p.make_with_size(scalar_p.size() * pack_size));
    vtn = new (_vtransform.arena()) VTransformLoadVectorNode(_vtransform, pack_size, vector_p);
  } else if (p0->is_Store()) {
    const VPointer& scalar_p = _vloop_analyzer.vpointers().vpointer(p0->as_Store());
    const VPointer vector_p(scalar_p.make_with_size(scalar_p.size() * pack_size));
    vtn = new (_vtransform.arena()) VTransformStoreVectorNode(_vtransform, pack_size, vector_p);
  } else if (p0->is_Bool()) {
    VTransformBoolTest kind = _packset.get_bool_test(pack);
    vtn = new (_vtransform.arena()) VTransformBoolVectorNode(_vtransform, pack_size, kind);
  } else if (_vloop_analyzer.reductions().is_marked_reduction(p0)) {
    vtn = new (_vtransform.arena()) VTransformReductionVectorNode(_vtransform, pack_size);
  } else if (VectorNode::is_muladds2i(p0)) {
    // A special kind of binary element-wise vector op: the inputs are "ints" a and b,
    // but reinterpreted as two "shorts" [a0, a1] and [b0, b1]:
    //   v = MulAddS2I(a, b) = a0 * b0 + a1 + b1
    assert(p0->req() == 5, "MulAddS2I should have 4 operands");
    vtn = new (_vtransform.arena()) VTransformElementWiseVectorNode(_vtransform, 3, pack_size);
  } else {
    assert(p0->req() == 3 ||
           p0->is_CMove() ||
           VectorNode::is_scalar_op_that_returns_int_but_vector_op_returns_long(opc) ||
           VectorNode::is_convert_opcode(opc) ||
           VectorNode::is_reinterpret_opcode(opc) ||
           VectorNode::is_scalar_unary_op_with_equal_input_and_output_types(opc) ||
           opc == Op_FmaD  ||
           opc == Op_FmaF  ||
           opc == Op_FmaHF ||
           opc == Op_SignumF ||
           opc == Op_SignumD,
           "pack type must be in this list");
    vtn = new (_vtransform.arena()) VTransformElementWiseVectorNode(_vtransform, p0->req(), pack_size);
  }
  vtn->set_nodes(pack);
  return vtn;
}

void SuperWordVTransformBuilder::set_req_with_scalar(Node* n, VTransformNode* vtn, const int index) {
  VTransformNode* req = get_vtnode_or_wrap_as_input_scalar(n->in(index));
  vtn->set_req(index, req);
}

// Either get the existing vtnode vector input (when input is a pack), or else make a
// new vector vtnode for the input (e.g. for Replicate or PopulateIndex).
VTransformNode* SuperWordVTransformBuilder::get_or_make_vtnode_vector_input_at_index(const Node_List* pack, const int index) {
  Node* p0 = pack->at(0);

  Node_List* pack_in = _packset.pack_input_at_index_or_null(pack, index);
  if (pack_in != nullptr) {
    // Input is a matching pack -> vtnode already exists.
    assert(index != 2 || !VectorNode::is_shift(p0), "shift's count cannot be vector");
    return get_vtnode(pack_in->at(0));
  }

  if (VectorNode::is_muladds2i(p0)) {
    assert(_packset.is_muladds2i_pack_with_pack_inputs(pack), "inputs must all be packs");
    // All inputs are strided (stride = 2), either with offset 0 or 1.
    Node_List* pack_in0 = _packset.strided_pack_input_at_index_or_null(pack, index, 2, 0);
    if (pack_in0 != nullptr) {
      return get_vtnode(pack_in0->at(0));
    }
    Node_List* pack_in1 = _packset.strided_pack_input_at_index_or_null(pack, index, 2, 1);
    if (pack_in1 != nullptr) {
      return get_vtnode(pack_in1->at(0));
    }
  }

  Node* same_input = _packset.same_inputs_at_index_or_null(pack, index);
  if (same_input == nullptr && p0->in(index) == _vloop.iv()) {
    // PopulateIndex: [iv+0, iv+1, iv+2, ...]
    VTransformNode* iv_vtn = get_vtnode_or_wrap_as_input_scalar(_vloop.iv());
    BasicType p0_bt = _vloop_analyzer.types().velt_basic_type(p0);
    // If we have subword type, take that type directly. If p0 is some ConvI2L/F/D,
    // then the p0_bt can also be L/F/D but we need to produce ints for the input of
    // the ConvI2L/F/D.
    BasicType element_bt = is_subword_type(p0_bt) ? p0_bt : T_INT;
    VTransformNode* populate_index = new (_vtransform.arena()) VTransformPopulateIndexNode(_vtransform, pack->size(), element_bt);
    populate_index->set_req(1, iv_vtn);
    return populate_index;
  }

  if (same_input != nullptr) {
    VTransformNode* same_input_vtn = get_vtnode_or_wrap_as_input_scalar(same_input);
    if (index == 2 && VectorNode::is_shift(p0)) {
      // Scalar shift count for vector shift operation: vec2 = shiftV(vec1, scalar_count)
      // Scalar shift operations masks the shift count, but the vector shift does not, so
      // create a special ShiftCount node.
      BasicType element_bt = _vloop_analyzer.types().velt_basic_type(p0);
      juint mask = (p0->bottom_type() == TypeInt::INT) ? (BitsPerInt - 1) : (BitsPerLong - 1);
      VTransformNode* shift_count = new (_vtransform.arena()) VTransformShiftCountNode(_vtransform, pack->size(), element_bt, mask, p0->Opcode());
      shift_count->set_req(1, same_input_vtn);
      return shift_count;
    } else {
      // Replicate the scalar same_input to every vector element.
      // In some rare case, p0 is Convert node such as a ConvL2I: all
      // ConvL2I nodes in the pack only differ in their types.
      // velt_basic_type(p0) is the output type of the pack. In the
      // case of a ConvL2I, it can be int or some narrower type such
      // as short etc. But given we replicate the input of the Convert
      // node, we have to use the input type instead.
      BasicType element_type = p0->is_Convert() ? p0->in(1)->bottom_type()->basic_type() : _vloop_analyzer.types().velt_basic_type(p0);
      if (index == 2 && VectorNode::is_scalar_rotate(p0) && element_type == T_LONG) {
        // Scalar rotate has int rotation value, but the scalar rotate expects longs.
        assert(same_input->bottom_type()->isa_int(), "scalar rotate expects int rotation");
        VTransformNode* conv = new (_vtransform.arena()) VTransformConvI2LNode(_vtransform);
        conv->set_req(1, same_input_vtn);
        same_input_vtn = conv;
      }
      VTransformNode* replicate = new (_vtransform.arena()) VTransformReplicateNode(_vtransform, pack->size(), element_type);
      replicate->set_req(1, same_input_vtn);
      return replicate;
    }
  }

  // The input is neither a pack not a same_input node. SuperWord::profitable does not allow
  // any other case. In the future, we could insert a PackNode.
#ifdef ASSERT
  tty->print_cr("\nSuperWordVTransformBuilder::get_or_make_vtnode_vector_input_at_index: index=%d", index);
  pack->dump();
  assert(false, "Pack input was neither a pack nor a same_input node");
#endif
  ShouldNotReachHere();
}

VTransformNode* SuperWordVTransformBuilder::get_vtnode_or_wrap_as_input_scalar(Node* n) {
  VTransformNode* vtn = get_vtnode_or_null(n);
  if (vtn != nullptr) { return vtn; }

  assert(!_vloop.in_bb(n), "only nodes outside the loop can be input nodes to the loop");
  vtn = new (_vtransform.arena()) VTransformInputScalarNode(_vtransform, n);
  map_node_to_vtnode(n, vtn);
  return vtn;
}

void SuperWordVTransformBuilder::set_req_with_vector(const Node_List* pack, VTransformNode* vtn, int j) {
  VTransformNode* req = get_or_make_vtnode_vector_input_at_index(pack, j);
  vtn->set_req(j, req);
}

void SuperWordVTransformBuilder::set_all_req_with_scalars(Node* n, VTransformNode* vtn) {
  assert(vtn->req() == n->req(), "scalars must have same number of reqs");
  for (uint j = 0; j < n->req(); j++) {
    Node* def = n->in(j);
    if (def == nullptr) { continue; }
    set_req_with_scalar(n, vtn, j);
  }
}

void SuperWordVTransformBuilder::set_all_req_with_vectors(const Node_List* pack, VTransformNode* vtn) {
  Node* p0 = pack->at(0);
  assert(vtn->req() <= p0->req(), "must have at at most as many reqs");
  // Vectors have no ctrl, so ignore it.
  for (uint j = 1; j < vtn->req(); j++) {
    Node* def = p0->in(j);
    if (def == nullptr) { continue; }
    set_req_with_vector(pack, vtn, j);
  }
}

void SuperWordVTransformBuilder::add_memory_dependencies_of_node_to_vtnode(Node*n, VTransformNode* vtn, VectorSet& vtn_memory_dependencies) {
  for (VLoopDependencyGraph::PredsIterator preds(_vloop_analyzer.dependency_graph(), n); !preds.done(); preds.next()) {
    Node* pred = preds.current();
    if (!_vloop.in_bb(pred)) { continue; }
    if (!preds.is_current_memory_edge()) { continue; }

    // Only track every memory edge once.
    VTransformNode* dependency = get_vtnode(pred);
    if (vtn_memory_dependencies.test_set(dependency->_idx)) { continue; }

    assert(n->is_Mem() && pred->is_Mem(), "only memory edges");
    vtn->add_memory_dependency(dependency); // Add every dependency only once per vtn.
  }
}
