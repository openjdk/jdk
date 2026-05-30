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

#include "opto/scalarVTransformBuilder.hpp"

void ScalarVTransformBuilder::build() {
  assert(!_vtransform.has_graph(), "start with empty vtransform");

  // Create vtnodes for all nodes in the loop.
  build_scalar_vtnodes();

  // Connect all vtnodes with their inputs. Possibly create vtnodes for input
  // nodes that are outside the loop.
  VectorSet vtn_memory_dependencies; // Shared, but cleared for every vtnode.
  build_inputs_for_vtnodes(vtn_memory_dependencies);

  // Build vtnodes for all uses of nodes from the loop, and connect them
  // as outputs to the nodes in the loop.
  build_uses_after_loop();

#ifndef PRODUCT
  if (_vloop.is_trace_vtransform()) {
    tty->print_cr("After ScalarVTransformBuilder::build:");
    _vtransform.graph().print_vtnodes();
  }
#endif
}

void ScalarVTransformBuilder::build_scalar_vtnodes() {
  for (uint i = 0; i < _vloop.lpt()->_body.size(); i++) {
    Node* n = _vloop.lpt()->_body.at(i);

    VTransformNode* vtn = nullptr;
    if (n->is_Load() || n->is_Store()) {
      MemNode* mem = n->as_Mem();
      const VPointer& mem_p = _vloop_analyzer.vpointers().vpointer(mem);
      vtn = new (_vtransform.arena()) VTransformMemopScalarNode(_vtransform, mem, mem_p);
    } else if (n->is_Phi()) {
      vtn = new (_vtransform.arena()) VTransformPhiScalarNode(_vtransform, n->as_Phi());
    } else if (n->is_CountedLoop()) {
      vtn = new (_vtransform.arena()) VTransformCountedLoopNode(_vtransform, n->as_CountedLoop());
    } else if (n->is_CFG()) {
      vtn = new (_vtransform.arena()) VTransformCFGNode(_vtransform, n);
    } else {
      vtn = new (_vtransform.arena()) VTransformDataScalarNode(_vtransform, n);
    }
    map_node_to_vtnode(n, vtn);
  }
}

void ScalarVTransformBuilder::build_inputs_for_vtnodes(VectorSet& vtn_memory_dependencies) {
  for (uint i = 0; i < _vloop.lpt()->_body.size(); i++) {
    Node* n = _vloop.lpt()->_body.at(i);
    VTransformNode* vtn = get_vtnode(n);
    if (vtn->isa_Vector() != nullptr) { continue; }
    vtn_memory_dependencies.clear(); // Add every dependency only once per vtn.

    if (n->is_Load()) {
      init_req(n, vtn, MemNode::Address);
      add_memory_dependencies_of_node_to_vtnode(n, vtn, vtn_memory_dependencies);
    } else if (n->is_Store()) {
      init_req(n, vtn, MemNode::Address);
      init_req(n, vtn, MemNode::ValueIn);
      add_memory_dependencies_of_node_to_vtnode(n, vtn, vtn_memory_dependencies);
    } else if (n->is_CountedLoop()) {
      // Avoid self-loop, it only creates unnecessary issues in scheduling.
      init_req(n, vtn, LoopNode::EntryControl);
      init_req(n, vtn, LoopNode::LoopBackControl);
    } else {
      init_all_req(n, vtn);
    }
  }
}

// Build vtnodes for all uses of nodes from the loop, and connect them
// as outputs to the nodes in the loop.
void ScalarVTransformBuilder::build_uses_after_loop() {
  for (uint i = 0; i < _vloop.lpt()->_body.size(); i++) {
    Node* n = _vloop.lpt()->_body.at(i);
    VTransformNode* vtn = get_vtnode(n);

    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* use = n->fast_out(i);

      if (!_vloop.in_bb(use)) {
        VTransformNode* vtn_use = get_vtnode_or_wrap_as_outer(use);

        // Set all edges
        for (uint j = 0; j < use->req(); j++) {
          Node* def = use->in(j);
          if (n == def && vtn_use->in_req(j) != vtn) {
            assert(vtn_use->in_req(j) == nullptr, "should not yet be set");
            vtn_use->init_req(j, vtn);
          }
        }
      }
    }
  }
}

void ScalarVTransformBuilder::init_req(Node* n, VTransformNode* vtn, const int index) {
  VTransformNode* req = get_vtnode_or_wrap_as_outer(n->in(index));
  vtn->init_req(index, req);
}

VTransformNode* ScalarVTransformBuilder::get_vtnode_or_wrap_as_outer(Node* n) {
  VTransformNode* vtn = get_vtnode_or_null(n);
  if (vtn != nullptr) { return vtn; }

  assert(!_vloop.in_bb(n), "only nodes outside the loop can be input nodes to the loop");
  vtn = new (_vtransform.arena()) VTransformOuterNode(_vtransform, n);
  map_node_to_vtnode(n, vtn);
  assert(vtn == get_vtnode_or_null(n), "consistency");
  return vtn;
}

void ScalarVTransformBuilder::init_all_req(Node* n, VTransformNode* vtn) {
  assert(vtn->req() == n->req(), "scalars must have same number of reqs");
  for (uint j = 0; j < n->req(); j++) {
    Node* def = n->in(j);
    if (def == nullptr) { continue; }
    init_req(n, vtn, j);
  }
}

void ScalarVTransformBuilder::add_memory_dependencies_of_node_to_vtnode(Node* n, VTransformNode* vtn, VectorSet& vtn_memory_dependencies) {
  // If we cannot speculate, then all dependencies must be strong edges, i.e. scheduling must respect them.
  bool are_speculative_checks_possible = _vloop.are_speculative_checks_possible();

  for (VLoopDependencyGraph::PredsIterator preds(_vloop_analyzer.dependency_graph(), n); !preds.done(); preds.next()) {
    Node* pred = preds.current();
    if (!_vloop.in_bb(pred)) { continue; }
    if (!preds.is_current_memory_edge()) { continue; }
    assert(n->is_Mem() && pred->is_Mem(), "only memory edges");

    // Only track every memory edge once.
    VTransformNode* dependency = get_vtnode(pred);
    if (vtn_memory_dependencies.test_set(dependency->_idx)) { continue; }

    if (are_speculative_checks_possible && preds.is_current_weak_memory_edge()) {
      vtn->add_weak_memory_edge(dependency);
    } else {
      vtn->add_strong_memory_edge(dependency);
    }
  }
}
