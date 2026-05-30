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

#include "opto/superword.hpp"
#include "opto/vtransform.hpp"

#ifndef SHARE_OPTO_SUPERWORD_VTRANSFORM_BUILDER_HPP
#define SHARE_OPTO_SUPERWORD_VTRANSFORM_BUILDER_HPP

// Facility that produces a vectorized vtransfrom from the SuperWord PackSet:
// The SuperWord Packset determined the packs we want to apply on the scalar vtransform.
// Now we want to produce a vectorized vtransform that replaces the packed scalar vtnodes
// into vector vtnodes.
class SuperWordVTransformBuilder : public StackObj {
private:
  const VLoopAnalyzer& _vloop_analyzer; // TODO: do we need this?
  const VLoop& _vloop;
  const PackSet& _packset;
  VTransform& _vector_vtransform; // TODO: verify old vs new vtn with asserts?

  // TODO: I think this can now be a GrowableArray.
  HashTable</* old->_idx*/ int, VTransformNode* /* or null*/> _idx_to_vtnode;

public:
  SuperWordVTransformBuilder(const PackSet& packset,
                             VTransform& vector_vtransform) :
      _vloop_analyzer(vector_vtransform.vloop_analyzer()),
      _vloop(_vloop_analyzer.vloop()),
      _packset(packset),
      _vector_vtransform(vector_vtransform)
  {
    assert(!_vector_vtransform.has_graph(), "constructor is passed an empty vtransform");
    build();
    assert(_vector_vtransform.has_graph(), "vtransform must contain some vtnodes now");
  }

private:
  void build();
  void build_vector_vtnodes_for_packed_nodes();
  void build_scalar_vtnodes_for_non_packed_nodes();
  void build_inputs_for_vector_vtnodes(VectorSet& vtn_memory_dependencies);
  void build_inputs_for_scalar_vtnodes(VectorSet& vtn_memory_dependencies);
  void build_uses_after_loop();

  // Helper methods for building VTransform.
  VTransformNode* get_vtnode_or_null(const VTransformNode* old) const {
    VTransformNode** ptr = _idx_to_vtnode.get(old->_idx);
    return (ptr == nullptr) ? nullptr : *ptr;
  }

  VTransformNode* get_vtnode(const VTransformNode* old) const {
    VTransformNode* vtn = get_vtnode_or_null(old);
    assert(vtn != nullptr, "expect non-null vtnode");
    return vtn;
  }

  void map_node_to_vtnode(const VTransformNode* old, VTransformNode* vtn) {
    assert(vtn != nullptr, "only set non-null vtnodes");
    _idx_to_vtnode.put_when_absent(old->_idx, vtn);
  }

  VTransformVectorNode* make_vector_vtnode_for_pack(const Pack* pack) const;
  VTransformNode* get_or_make_vtnode_vector_input_at_index(const Pack* pack, const int index);
  VTransformNode* get_vtnode_or_wrap_as_outer(const VTransformNode* old);
  void init_req_with_scalar(const VTransformNode* old, VTransformNode* vtn, const int index);
  void init_req_with_vector(const Pack* pack, VTransformNode* vtn, const int index);
  void init_all_req_with_scalars(const VTransformNode* old, VTransformNode* vtn);
  void init_all_req_with_vectors(const Pack* pack, VTransformNode* vtn);
  void add_memory_dependencies_of_node_to_vtnode(const VTransformNode* old, VTransformNode* vtn, VectorSet& vtn_memory_dependencies);
  LoadNode::ControlDependency load_control_dependency(const Pack* pack) const;
};

#endif // SHARE_OPTO_SUPERWORD_VTRANSFORM_BUILDER_HPP
