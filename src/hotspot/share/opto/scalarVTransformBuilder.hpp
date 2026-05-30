/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_SCALAR_VTRANSFORM_BUILDER_HPP
#define SHARE_OPTO_SCALAR_VTRANSFORM_BUILDER_HPP

// Facility class that builds a VTransform from the scalar C2 graph.
// From this scalar VTransform, we later derive vectorized VTransforms.
class ScalarVTransformBuilder : public StackObj {
private:
  const VLoopAnalyzer& _vloop_analyzer;
  const VLoop& _vloop;
  VTransform& _vtransform;

  HashTable</* Node::_idx*/ int, VTransformNode* /* or null*/> _idx_to_vtnode;

public:
  ScalarVTransformBuilder(VTransform& vtransform) :
      _vloop_analyzer(vtransform.vloop_analyzer()),
      _vloop(_vloop_analyzer.vloop()),
      _vtransform(vtransform)
  {
    assert(!_vtransform.has_graph(), "constructor is passed an empty vtransform");
    build();
    assert(_vtransform.has_graph(), "vtransform must contain some vtnodes now");
  }

private:
  void build();
  void build_scalar_vtnodes();
  void build_inputs_for_vtnodes(VectorSet& vtn_memory_dependencies);
  void build_uses_after_loop();

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

  VTransformNode* get_vtnode_or_wrap_as_outer(Node* n);
  void init_req(Node* n, VTransformNode* vtn, const int index);
  void init_all_req(Node* n, VTransformNode* vtn);
  void add_memory_dependencies_of_node_to_vtnode(Node* n, VTransformNode* vtn, VectorSet& vtn_memory_dependencies);
};

#endif // SHARE_OPTO_SCALAR_VTRANSFORM_BUILDER_HPP
