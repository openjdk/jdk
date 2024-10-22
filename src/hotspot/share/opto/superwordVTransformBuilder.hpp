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

#include "opto/vtransform.hpp"
#include "opto/superword.hpp"

#ifndef SHARE_OPTO_SUPERWORD_VTRANSFORM_BUILDER_HPP
#define SHARE_OPTO_SUPERWORD_VTRANSFORM_BUILDER_HPP

// Facility class that builds a VTransform from a SuperWord PackSet.
class SuperWordVTransformBuilder : public StackObj {
private:
  const VLoopAnalyzer& _vloop_analyzer;
  const VLoop& _vloop;
  const PackSet& _packset;
  VTransform& _vtransform;

  ResourceHashtable</* Node::_idx*/ int, VTransformNode* /* or null*/> _idx_to_vtnode;

public:
  SuperWordVTransformBuilder(const PackSet& packset,
                             VTransform& vtransform) :
      _vloop_analyzer(vtransform.vloop_analyzer()),
      _vloop(_vloop_analyzer.vloop()),
      _packset(packset),
      _vtransform(vtransform)
  {
    assert(!_vtransform.has_graph(), "constructor is passed an empty vtransform");
    build();
    assert(_vtransform.has_graph(), "vtransform must contain some vtnodes now");
  }

private:
  void build();
  void build_vector_vtnodes_for_packed_nodes();
  void build_scalar_vtnodes_for_non_packed_nodes();
  void build_inputs_for_vector_vtnodes(VectorSet& vtn_dependencies);
  void build_inputs_for_scalar_vtnodes(VectorSet& vtn_dependencies);

  // Helper methods for building VTransform.
  VTransformNode* get_vtnode_or_null(Node* n) const {
    VTransformNode** ptr = _idx_to_vtnode.get(n->_idx);
    return (ptr == nullptr) ? nullptr : *ptr;
  }

  VTransformNode* get_vtnode(Node* n) const {
    VTransformNode* vtn = get_vtnode_or_null(n);
    assert(vtn != nullptr, "expect non-null vtnode");
    return vtn;
  }

  void map_node_to_vtnode(Node* n, VTransformNode* vtn) {
    assert(vtn != nullptr, "only set non-null vtnodes");
    _idx_to_vtnode.put_when_absent(n->_idx, vtn);
  }

  VTransformVectorNode* make_vector_vtnode_for_pack(const Node_List* pack) const;
  VTransformNode* get_or_make_vtnode_vector_input_at_index(const Node_List* pack, const int index);
  VTransformNode* get_vtnode_or_wrap_as_input_scalar(Node* n);
  void set_req_with_scalar(Node* n, VTransformNode* vtn, VectorSet& vtn_dependencies, const int index);
  void set_req_with_vector(const Node_List* pack, VTransformNode* vtn, VectorSet& vtn_dependencies, const int index);
  void set_all_req_with_scalars(Node* n, VTransformNode* vtn, VectorSet& vtn_dependencies);
  void set_all_req_with_vectors(const Node_List* pack, VTransformNode* vtn, VectorSet& vtn_dependencies);
  void add_dependencies_of_node_to_vtnode(Node* n, VTransformNode* vtn, VectorSet& vtn_dependencies);
};

#endif // SHARE_OPTO_SUPERWORD_VTRANSFORM_BUILDER_HPP
