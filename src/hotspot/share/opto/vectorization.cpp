/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Arm Limited. All rights reserved.
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

#include "opto/addnode.hpp"
#include "opto/castnode.hpp"
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/divnode.hpp"
#include "opto/movenode.hpp"
#include "opto/mulnode.hpp"
#include "opto/noOverflowInt.hpp"
#include "opto/phaseX.hpp"
#include "opto/rootnode.hpp"
#include "opto/vectorization.hpp"

bool VLoop::check_preconditions() {
#ifndef PRODUCT
  if (is_trace_preconditions()) {
    tty->print_cr("\nVLoop::check_preconditions");
    lpt()->dump_head();
    lpt()->head()->dump();
  }
#endif

  VStatus status = check_preconditions_helper();
  if (!status.is_success()) {
#ifndef PRODUCT
    if (is_trace_preconditions()) {
      tty->print_cr("VLoop::check_preconditions: failed: %s", status.failure_reason());
    }
#endif
    return false; // failure
  }
  return true; // success
}

VStatus VLoop::check_preconditions_helper() {
  // Only accept vector width that is power of 2
  int vector_width = Matcher::vector_width_in_bytes(T_BYTE);
  if (vector_width < 2 || !is_power_of_2(vector_width)) {
    return VStatus::make_failure(VLoop::FAILURE_VECTOR_WIDTH);
  }

  // Only accept valid counted loops (int)
  if (!_lpt->_head->as_Loop()->is_valid_counted_loop(T_INT)) {
    return VStatus::make_failure(VLoop::FAILURE_VALID_COUNTED_LOOP);
  }
  _cl = _lpt->_head->as_CountedLoop();
  _iv = _cl->phi()->as_Phi();

  if (_cl->is_vectorized_loop()) {
    return VStatus::make_failure(VLoop::FAILURE_ALREADY_VECTORIZED);
  }

  if (_cl->is_unroll_only()) {
    return VStatus::make_failure(VLoop::FAILURE_UNROLL_ONLY);
  }

  // Check for control flow in the body
  _cl_exit = _cl->loopexit();
  bool has_cfg = _cl_exit->in(0) != _cl;
  if (has_cfg && !is_allow_cfg()) {
#ifndef PRODUCT
    if (is_trace_preconditions()) {
      tty->print_cr("VLoop::check_preconditions: fails because of control flow.");
      tty->print("  cl_exit %d", _cl_exit->_idx); _cl_exit->dump();
      tty->print("  cl_exit->in(0) %d", _cl_exit->in(0)->_idx); _cl_exit->in(0)->dump();
      tty->print("  lpt->_head %d", _cl->_idx); _cl->dump();
      _lpt->dump_head();
    }
#endif
    return VStatus::make_failure(VLoop::FAILURE_CONTROL_FLOW);
  }

  // Make sure the are no extra control users of the loop backedge
  if (_cl->back_control()->outcnt() != 1) {
    return VStatus::make_failure(VLoop::FAILURE_BACKEDGE);
  }

  if (_cl->is_main_loop()) {
    // To align vector memory accesses in the main-loop, we will have to adjust
    // the pre-loop limit.
    CountedLoopEndNode* pre_end = _cl->find_pre_loop_end();
    if (pre_end == nullptr) {
      return VStatus::make_failure(VLoop::FAILURE_PRE_LOOP_LIMIT);
    }
    Node* pre_opaq1 = pre_end->limit();
    if (pre_opaq1->Opcode() != Op_Opaque1) {
      return VStatus::make_failure(VLoop::FAILURE_PRE_LOOP_LIMIT);
    }
    _pre_loop_end = pre_end;

    // See if we find the infrastructure for speculative runtime-checks.
    //  (1) Auto Vectorization Parse Predicate
    Node* pre_ctrl = pre_loop_head()->in(LoopNode::EntryControl);
    const Predicates predicates(pre_ctrl);
    const PredicateBlock* predicate_block = predicates.auto_vectorization_check_block();
    if (predicate_block->has_parse_predicate()) {
      _auto_vectorization_parse_predicate_proj = predicate_block->parse_predicate_success_proj();
    }

    //  (2) Multiversioning fast-loop projection
    IfTrueNode* before_predicates = predicates.entry()->isa_IfTrue();
    if (before_predicates != nullptr &&
        before_predicates->in(0)->is_If() &&
        before_predicates->in(0)->in(1)->is_OpaqueMultiversioning()) {
      _multiversioning_fast_proj = before_predicates;
    }
#ifndef PRODUCT
    if (is_trace_preconditions() || is_trace_speculative_runtime_checks()) {
      tty->print_cr(" Infrastructure for speculative runtime-checks:");
      if (_auto_vectorization_parse_predicate_proj != nullptr) {
        tty->print_cr("  auto_vectorization_parse_predicate_proj: speculate and trap");
        _auto_vectorization_parse_predicate_proj->dump_bfs(5, nullptr, "");
      } else if (_multiversioning_fast_proj != nullptr) {
        tty->print_cr("  multiversioning_fast_proj: speculate and multiversion");
        _multiversioning_fast_proj->dump_bfs(5, nullptr, "");
      } else {
        tty->print_cr("  Not found.");
      }
    }
#endif
    assert(_auto_vectorization_parse_predicate_proj == nullptr ||
           _multiversioning_fast_proj == nullptr, "we should only have at most one of these");
    assert(_cl->is_multiversion_fast_loop() == (_multiversioning_fast_proj != nullptr),
           "must find the multiversion selector IFF loop is a multiversion fast loop");
  }

  return VStatus::make_success();
}

// Return true iff all submodules are loaded successfully
bool VLoopAnalyzer::setup_submodules() {
#ifndef PRODUCT
  if (_vloop.is_trace_loop_analyzer()) {
    tty->print_cr("\nVLoopAnalyzer::setup_submodules");
    _vloop.lpt()->dump_head();
    _vloop.cl()->dump();
  }
#endif

  VStatus status = setup_submodules_helper();
  if (!status.is_success()) {
#ifndef PRODUCT
    if (_vloop.is_trace_loop_analyzer()) {
      tty->print_cr("\nVLoopAnalyze::setup_submodules: failed: %s", status.failure_reason());
    }
#endif
    return false; // failed
  }
  return true; // success
}

VStatus VLoopAnalyzer::setup_submodules_helper() {
  // Skip any loop that has not been assigned max unroll by analysis.
  if (SuperWordLoopUnrollAnalysis && _vloop.cl()->slp_max_unroll() == 0) {
    return VStatus::make_failure(VLoopAnalyzer::FAILURE_NO_MAX_UNROLL);
  }

  if (SuperWordReductions) {
    _reductions.mark_reductions();
  }

  VStatus body_status = _body.construct();
  if (!body_status.is_success()) {
    return body_status;
  }

  VStatus slices_status = _memory_slices.find_memory_slices();
  if (!slices_status.is_success()) {
    return slices_status;
  }

  // If there is no memory slice detected, it means there is no store.
  // If there is no reduction and no store, then we give up, because
  // vectorization is not possible anyway (given current limitations).
  if (!_reductions.is_marked_reduction_loop() &&
      _memory_slices.heads().is_empty()) {
    return VStatus::make_failure(VLoopAnalyzer::FAILURE_NO_REDUCTION_OR_STORE);
  }

  _types.compute_vector_element_type();

  _vpointers.compute_vpointers();

  _dependency_graph.construct();

  return VStatus::make_success();
}

// There are 2 kinds of slices:
// - No memory phi: only loads.
//   - Usually, all loads have the same input memory state from before the loop.
//   - Only rarely this is not the case, and we just bail out for now.
// - With memory phi. Chain of memory operations inside the loop.
VStatus VLoopMemorySlices::find_memory_slices() {
  Compile* C = _vloop.phase()->C;
  // We iterate over the body, which is topologically sorted. Hence, if there is a phi
  // in a slice, we will find it first, and the loads and stores afterwards.
  for (int i = 0; i < _body.body().length(); i++) {
    Node* n = _body.body().at(i);
    if (n->is_memory_phi()) {
      // Memory slice with stores (and maybe loads)
      PhiNode* phi = n->as_Phi();
      int alias_idx = C->get_alias_index(phi->adr_type());
      assert(_inputs.at(alias_idx) == nullptr, "did not yet touch this slice");
      _inputs.at_put(alias_idx, phi->in(1));
      _heads.at_put(alias_idx, phi);
    } else if (n->is_Load()) {
      LoadNode* load = n->as_Load();
      int alias_idx = C->get_alias_index(load->adr_type());
      PhiNode* head = _heads.at(alias_idx);
      if (head == nullptr) {
        // We did not find a phi on this slice yet -> must be a slice with only loads.
        // For now, we can only handle slices with a single memory input before the loop,
        // so if we find multiple, we bail out of auto vectorization. If this becomes
        // too restrictive in the fututure, we could consider tracking multiple inputs.
        // Different memory inputs can for example happen if one load has its memory state
        // optimized, and the other load fails to have it optimized, for example because
        // it does not end up on the IGVN worklist any more.
        if (_inputs.at(alias_idx) != nullptr && _inputs.at(alias_idx) != load->in(1)) {
          return VStatus::make_failure(FAILURE_DIFFERENT_MEMORY_INPUT);
        }
        _inputs.at_put(alias_idx, load->in(1));
      } // else: the load belongs to a slice with a phi that already set heads and inputs.
#ifdef ASSERT
    } else if (n->is_Store()) {
      // Found a store. Make sure it is in a slice with a Phi.
      StoreNode* store = n->as_Store();
      int alias_idx = C->get_alias_index(store->adr_type());
      PhiNode* head = _heads.at(alias_idx);
      assert(head != nullptr, "should have found a mem phi for this slice");
#endif
    }
  }
  NOT_PRODUCT( if (_vloop.is_trace_memory_slices()) { print(); } )
  return VStatus::make_success();
}

#ifndef PRODUCT
void VLoopMemorySlices::print() const {
  tty->print_cr("\nVLoopMemorySlices::print: %s",
                heads().length() > 0 ? "" : "NONE");
  for (int i = 0; i < _inputs.length(); i++) {
    Node* input = _inputs.at(i);
    PhiNode* head = _heads.at(i);
    if (input != nullptr) {
      tty->print("%3d input", i);  input->dump();
      if (head == nullptr) {
        tty->print_cr("    load only");
      } else {
        tty->print("    head ");  head->dump();
      }
    }
  }
}
#endif

void VLoopVPointers::compute_vpointers() {
  count_vpointers();
  allocate_vpointers_array();
  compute_and_cache_vpointers();
  NOT_PRODUCT( if (_vloop.is_trace_vpointers()) { print(); } )
}

void VLoopVPointers::count_vpointers() {
  _vpointers_length = 0;
  _body.for_each_mem([&] (const MemNode* mem, int bb_idx) {
    _vpointers_length++;
  });
}

void VLoopVPointers::allocate_vpointers_array() {
  uint bytes = _vpointers_length * sizeof(VPointer);
  _vpointers = (VPointer*)_arena->Amalloc(bytes);
}

void VLoopVPointers::compute_and_cache_vpointers() {
  int pointers_idx = 0;
  _body.for_each_mem([&] (MemNode* const mem, int bb_idx) {
    // Placement new: construct directly into the array.
    ::new (&_vpointers[pointers_idx]) VPointer(mem, _vloop, _pointer_expression_nodes);
    _bb_idx_to_vpointer.at_put(bb_idx, pointers_idx);
    pointers_idx++;
  });
}

const VPointer& VLoopVPointers::vpointer(const MemNode* mem) const {
  assert(mem != nullptr && _vloop.in_bb(mem), "only mem in loop");
  int bb_idx = _body.bb_idx(mem);
  int pointers_idx = _bb_idx_to_vpointer.at(bb_idx);
  assert(0 <= pointers_idx && pointers_idx < _vpointers_length, "valid range");
  return _vpointers[pointers_idx];
}

#ifndef PRODUCT
void VLoopVPointers::print() const {
  tty->print_cr("\nVLoopVPointers::print:");

  _body.for_each_mem([&] (const MemNode* mem, int bb_idx) {
    const VPointer& p = vpointer(mem);
    tty->print("  ");
    p.print_on(tty);
  });
}
#endif

// Construct the dependency graph:
//  - Data-dependencies: implicit (taken from C2 node inputs).
//  - Memory-dependencies:
//    - No edges between different slices.
//    - No Load-Load edges.
//    - Inside a slice, add all Store-Load, Load-Store, Store-Store edges,
//      except if we can prove that the memory does not overlap.
//    - Strong edge: must be respected.
//    - Weak edge:   if we add a speculative aliasing check, we can violate
//                   the edge, i.e. spaw the order.
void VLoopDependencyGraph::construct() {
  const GrowableArray<PhiNode*>& mem_slice_heads = _memory_slices.heads();

  ResourceMark rm;
  GrowableArray<MemNode*> slice_nodes;
  GrowableArray<int> strong_memory_edges;
  GrowableArray<int> weak_memory_edges;

  // For each memory slice, create the memory subgraph
  for (int i = 0; i < mem_slice_heads.length(); i++) {
    PhiNode* head = mem_slice_heads.at(i);
    // If there is no head (memory-phi) for this slice, then we have either no memops
    // in the loop, or only loads. We do not need to add any memory edges in that case.
    if (head == nullptr) { continue; }
    MemNode* tail = head->in(2)->as_Mem();

    _memory_slices.get_slice_in_reverse_order(head, tail, slice_nodes);

    // In forward order (reverse of reverse), visit all memory nodes in the slice.
    for (int j = slice_nodes.length() - 1; j >= 0 ; j--) {
      MemNode* n1 = slice_nodes.at(j);
      strong_memory_edges.clear();
      weak_memory_edges.clear();

      const VPointer& p1 = _vpointers.vpointer(n1);
      // For all memory nodes before it, check if we need to add a memory edge.
      for (int k = slice_nodes.length() - 1; k > j; k--) {
        MemNode* n2 = slice_nodes.at(k);

        // Ignore Load-Load dependencies:
        if (n1->is_Load() && n2->is_Load()) { continue; }

        const VPointer& p2 = _vpointers.vpointer(n2);

        // If we can prove that they will never overlap -> drop edge.
        if (!p1.never_overlaps_with(p2)) {
          if (p1.can_make_speculative_aliasing_check_with(p2)) {
            weak_memory_edges.append(_body.bb_idx(n2));
          } else {
            strong_memory_edges.append(_body.bb_idx(n2));
          }
        }
      }
      if (strong_memory_edges.is_nonempty() || weak_memory_edges.is_nonempty()) {
        // Data edges are taken implicitly from the C2 graph, thus we only add
        // a dependency node if we have memory edges.
        add_node(n1, strong_memory_edges, weak_memory_edges);
      }
    }
    slice_nodes.clear();
  }

  compute_depth();

  NOT_PRODUCT( if (_vloop.is_trace_dependency_graph()) { print(); } )
}

void VLoopDependencyGraph::add_node(MemNode* n, GrowableArray<int>& strong_memory_edges, GrowableArray<int>& weak_memory_edges) {
  assert(_dependency_nodes.at_grow(_body.bb_idx(n), nullptr) == nullptr, "not yet created");
  DependencyNode* dn = new (_arena) DependencyNode(n, strong_memory_edges, weak_memory_edges, _arena);
  _dependency_nodes.at_put_grow(_body.bb_idx(n), dn, nullptr);
}

int VLoopDependencyGraph::find_max_pred_depth(const Node* n) const {
  int max_pred_depth = 0;
  if (!n->is_Phi()) { // ignore backedge
    // We must compute the dependence graph depth with all edges (including the weak edges), so that
    // the independence queries work correctly, no matter if we check independence with or without
    // weak edges.
    for (PredsIterator it(*this, n); !it.done(); it.next()) {
      Node* pred = it.current();
      if (_vloop.in_bb(pred)) {
        max_pred_depth = MAX2(max_pred_depth, depth(pred));
      }
    }
  }
  return max_pred_depth;
}

// We iterate over the body, which is already ordered by the dependencies, i.e. pred comes
// before use. With a single pass, we can compute the depth of every node, since we can
// assume that the depth of all preds is already computed when we compute the depth of use.
void VLoopDependencyGraph::compute_depth() {
  for (int i = 0; i < _body.body().length(); i++) {
    Node* n = _body.body().at(i);
    set_depth(n, find_max_pred_depth(n) + 1);
  }

#ifdef ASSERT
  for (int i = 0; i < _body.body().length(); i++) {
    Node* n = _body.body().at(i);
    int max_pred_depth = find_max_pred_depth(n);
    if (depth(n) != max_pred_depth + 1) {
      print();
      tty->print_cr("Incorrect depth: %d vs %d", depth(n), max_pred_depth + 1);
      n->dump();
    }
    assert(depth(n) == max_pred_depth + 1, "must have correct depth");
  }
#endif
}

#ifndef PRODUCT
void VLoopDependencyGraph::print() const {
  tty->print_cr("\nVLoopDependencyGraph::print:");

  tty->print_cr(" Memory pred edges:");
  for (int i = 0; i < _body.body().length(); i++) {
    Node* n = _body.body().at(i);
    const DependencyNode* dn = dependency_node(n);
    if (dn != nullptr) {
      tty->print("  DependencyNode[%d %s:", n->_idx, n->Name());
      for (uint j = 0; j < dn->num_strong_memory_edges(); j++) {
        Node* pred = _body.body().at(dn->strong_memory_edge(j));
        tty->print("  %d %s", pred->_idx, pred->Name());
      }
      tty->print(" | weak:");
      for (uint j = 0; j < dn->num_weak_memory_edges(); j++) {
        Node* pred = _body.body().at(dn->weak_memory_edge(j));
        tty->print("  %d %s", pred->_idx, pred->Name());
      }
      tty->print_cr("]");
    }
  }
  tty->cr();

  // If we cannot speculate (aliasing analysis runtime checks), we need to respect all edges.
  bool with_weak_memory_edges = !_vloop.use_speculative_aliasing_checks();
  if (with_weak_memory_edges) {
    tty->print_cr(" Complete dependency graph (with weak edges, because we cannot speculate):");
  } else {
    tty->print_cr(" Dependency graph without weak edges (because we can speculate):");
  }
  for (int i = 0; i < _body.body().length(); i++) {
    Node* n = _body.body().at(i);
    tty->print("  d%02d Dependencies[%d %s:", depth(n), n->_idx, n->Name());
    for (PredsIterator it(*this, n); !it.done(); it.next()) {
      if (!with_weak_memory_edges && it.is_current_weak_memory_edge()) { continue; }
      Node* pred = it.current();
      tty->print("  %d %s", pred->_idx, pred->Name());
    }
    tty->print_cr("]");
  }
}
#endif

VLoopDependencyGraph::DependencyNode::DependencyNode(MemNode* n,
                                                     GrowableArray<int>& strong_memory_edges,
                                                     GrowableArray<int>& weak_memory_edges,
                                                     Arena* arena) :
    _node(n),
    _num_strong_memory_edges(strong_memory_edges.length()),
    _num_weak_memory_edges(weak_memory_edges.length()),
    _memory_edges(nullptr)
{
  assert(strong_memory_edges.is_nonempty() || weak_memory_edges.is_nonempty(), "only generate DependencyNode if there are pred edges");
  uint bytes_strong = strong_memory_edges.length() * sizeof(int);
  uint bytes_weak = weak_memory_edges.length() * sizeof(int);
  uint bytes_total = bytes_strong + bytes_weak;
  _memory_edges = (int*)arena->Amalloc(bytes_total);
  if (strong_memory_edges.length() > 0) {
    memcpy(_memory_edges, strong_memory_edges.adr_at(0), bytes_strong);
  }
  if (weak_memory_edges.length() > 0) {
    memcpy(_memory_edges + strong_memory_edges.length(), weak_memory_edges.adr_at(0), bytes_weak);
  }
}

VLoopDependencyGraph::PredsIterator::PredsIterator(const VLoopDependencyGraph& dependency_graph,
                                                   const Node* node) :
    _dependency_graph(dependency_graph),
    _node(node),
    _dependency_node(dependency_graph.dependency_node(node)),
    _current(nullptr),
    _is_current_memory_edge(false),
    _is_current_weak_memory_edge(false),
    _next_data_edge(0),
    _end_data_edge(node->req()),
    _next_strong_memory_edge(0),
    _end_strong_memory_edge((_dependency_node != nullptr) ? _dependency_node->num_strong_memory_edges() : 0),
    _next_weak_memory_edge(0),
    _end_weak_memory_edge((_dependency_node != nullptr) ? _dependency_node->num_weak_memory_edges() : 0)
{
  if (_node->is_Store() || _node->is_Load()) {
    // Ignore ctrl and memory, only address and value are data dependencies.
    // Memory edges are already covered by the strong and weak memory edges.
    // Load:  [ctrl, memory] address
    // Store: [ctrl, memory] address, value
    _next_data_edge = MemNode::Address;
  } else {
    assert(!_node->is_Mem(), "only loads and stores are expected mem nodes");
    _next_data_edge = 1; // skip control
  }
  next();
}

void VLoopDependencyGraph::PredsIterator::next() {
  if (_next_data_edge < _end_data_edge) {
    _current = _node->in(_next_data_edge++);
    _is_current_memory_edge = false;
    _is_current_weak_memory_edge = false;
  } else if (_next_strong_memory_edge < _end_strong_memory_edge) {
    int pred_bb_idx = _dependency_node->strong_memory_edge(_next_strong_memory_edge++);
    _current = _dependency_graph._body.body().at(pred_bb_idx);
    _is_current_memory_edge = true;
    _is_current_weak_memory_edge = false;
  } else if (_next_weak_memory_edge < _end_weak_memory_edge) {
    int pred_bb_idx = _dependency_node->weak_memory_edge(_next_weak_memory_edge++);
    _current = _dependency_graph._body.body().at(pred_bb_idx);
    _is_current_memory_edge = true;
    _is_current_weak_memory_edge = true;
  } else {
    _current = nullptr; // done
    _is_current_memory_edge = false;
    _is_current_weak_memory_edge = false;
  }
}

// Cost-model heuristic for nodes that do not contribute to computational
// cost inside the loop.
bool VLoopAnalyzer::has_zero_cost(Node* n) const {
  // Outside body?
  if (!_vloop.in_bb(n)) { return true; }

  // Internal nodes of pointer expressions are most likely folded into
  // the load / store and have no additional cost.
  if (vpointers().is_in_pointer_expression(n)) { return true; }

  // Not all AddP nodes can be detected in VPointer parsing, so
  // we filter them out here.
  // We don't want to explicitly model the cost of control flow,
  // since we have the same CFG structure before and after
  // vectorization: A loop head, a loop exit, with a backedge.
  if (n->is_AddP() || // Pointer expression
      n->is_CFG() ||  // CFG
      n->is_Phi() ||  // CFG
      n->is_Cmp() ||  // CFG
      n->is_Bool()) { // CFG
    return true;
  }

  // All other nodes have a non-zero cost.
  return false;
}

// Compute the cost over all operations in the (scalar) loop.
float VLoopAnalyzer::cost_for_scalar_loop() const {
#ifndef PRODUCT
  if (_vloop.is_trace_cost()) {
    tty->print_cr("\nVLoopAnalyzer::cost_for_scalar_loop:");
  }
#endif

  float sum = 0;
  for (int j = 0; j < body().body().length(); j++) {
    Node* n = body().body().at(j);
    if (!has_zero_cost(n)) {
      float c = cost_for_scalar_node(n->Opcode());
      sum += c;
#ifndef PRODUCT
      if (_vloop.is_trace_cost_verbose()) {
        tty->print_cr("  -> cost = %.2f for %d %s", c, n->_idx, n->Name());
      }
#endif
    }
  }

#ifndef PRODUCT
  if (_vloop.is_trace_cost()) {
    tty->print_cr("  total_cost = %.2f", sum);
  }
#endif
  return sum;
}

// For now, we use unit cost. We might refine that in the future.
// If needed, we could also use platform specific costs, if the
// default here is not accurate enough.
float VLoopAnalyzer::cost_for_scalar_node(int opcode) const {
  float c = 1;
#ifndef PRODUCT
  if (_vloop.is_trace_cost()) {
    tty->print_cr("  cost = %.2f opc=%s", c, NodeClassNames[opcode]);
  }
#endif
  return c;
}

// For now, we use unit cost. We might refine that in the future.
// If needed, we could also use platform specific costs, if the
// default here is not accurate enough.
float VLoopAnalyzer::cost_for_vector_node(int opcode, int vlen, BasicType bt) const {
  float c = 1;
#ifndef PRODUCT
  if (_vloop.is_trace_cost()) {
    tty->print_cr("  cost = %.2f opc=%s vlen=%d bt=%s",
                  c, NodeClassNames[opcode], vlen, type2name(bt));
  }
#endif
  return c;
}

// For now, we use unit cost, i.e. we count the number of backend instructions
// that the vtnode will use. We might refine that in the future.
// If needed, we could also use platform specific costs, if the
// default here is not accurate enough.
float VLoopAnalyzer::cost_for_vector_reduction_node(int opcode, int vlen, BasicType bt, bool requires_strict_order) const {
  // Each reduction is composed of multiple instructions, each estimated with a unit cost.
  //                                Linear: shuffle and reduce    Recursive: shuffle and reduce
  float c = requires_strict_order ? 2 * vlen                    : 2 * exact_log2(vlen);
#ifndef PRODUCT
  if (_vloop.is_trace_cost()) {
    tty->print_cr("  cost = %.2f opc=%s vlen=%d bt=%s requires_strict_order=%s",
                  c, NodeClassNames[opcode], vlen, type2name(bt),
                  requires_strict_order ? "true" : "false");
  }
#endif
  return c;
}

// Computing aliasing runtime check using init and last of main-loop
// -----------------------------------------------------------------
//
// We have two VPointer vp1 and vp2, and would like to create a runtime check that
// guarantees that the corresponding pointers p1 and p2 do not overlap (alias) for
// any iv value in the strided range r = [init, init + iv_stride, .. limit).
// Remember that vp1 and vp2 both represent a region in memory, starting at a
// "pointer", and extending for "size" bytes:
//
//   vp1(iv) = [p1(iv), size1)
//   vp2(iv) = [p2(iv), size2)
//
//       |---size1--->           |-------size2------->
//       |                       |
//     p1(iv)                  p2(iv)
//
// In each iv value (intuitively: for each iteration), we check that there is no
// overlap:
//
//   for all iv in r: p1(iv) + size1 <= p2(iv) OR p2(iv) + size2 <= p1(iv)
//
// This would allow situations where for some iv p1 is lower than p2, and for
// other iv p1 is higher than p2. This is not very useful in practice. We can
// strengthen the condition, which will make the check simpler later:
//
//   for all iv in r: p1(iv) + size1 <= p2(iv)                    (P1-BEFORE-P2)
//   OR
//   for all iv in r: p2(iv) + size2 <= p1(iv)                    (P1-AFTER-P2)
//
// Note: apart from this strengthening, the checks we derive below are byte accurate,
//       i.e. they are equivalent to the conditions above. This means we have NO case
//       where:
//       1) The check passes (predicts no overlap) but the pointers do actually overlap.
//          This would be bad because we would wrongly vectorize, possibly leading to
//          wrong results.
//       2) The check does not pass (predicts overlap) but the pointers do not overlap.
//          This would be suboptimal, as we would not be able to vectorize, and either
//          trap (with predicate), or go into the slow-loop (with multiversioning).
//
//
// We apply the "MemPointer Linearity Corrolary" to VPointer vp and the corresponding
// pointer p:
//   (C0) is given by the construction of VPointer vp, which simply wraps a MemPointer mp.
//   (c1) with v = iv and scale_v = iv_scale
//   (C2) with r = [init, init + iv_stride, .. last - stride_v, last], which is the set
//        of possible iv values in the loop, with "init" the first iv value, and "last"
//        the last iv value which is closest to limit.
//        Note: iv_stride > 0  ->  limit - iv_stride <= last < limit
//              iv_stride < 0  ->  limit < last <= limit - iv_stride
//        We have to be a little careful, and cannot just use "limit" instead of "last" as
//        the last value in r, because the iv never reaches limit in the main-loop, and
//        so we are not sure if the memory access at p(limit) is still in bounds.
//        For now, we just assume that we can compute init and limit, and we will derive
//        the computation of these values later on.
//   (C3) the memory accesses for every iv value in the loop must be in bounds, otherwise
//        the program has undefined behaviour already.
//   (C4) abs(iv_scale * iv_stride) < 2^31 is given by the checks in
//        VPointer::init_are_scale_and_stride_not_too_large.
//
// Hence, it follows that we can see p and vp as linear functions of iv in r, i.e. for
// all iv values in the loop:
//   p(iv)  = p(init)  - init * iv_scale + iv * iv_scale
//   vp(iv) = vp(init) - init * iv_scale + iv * iv_scale
//
// Hence, p1 and p2 have the linear form:
//   p1(iv)  = p1(init) - init * iv_scale1 + iv * iv_scale1             (LINEAR-FORM-INIT)
//   p2(iv)  = p2(init) - init * iv_scale2 + iv * iv_scale2
//
// With the (Alternative Corrolary P) we get the alternative linar form:
//   p1(iv)  = p1(last) - last * iv_scale1 + iv * iv_scale1             (LINEAR-FORM-LAST)
//   p2(iv)  = p2(last) - last * iv_scale2 + iv * iv_scale2
//
//
// We can now use this linearity to construct aliasing runtime checks, depending on the
// different "geometry" of the two VPointer over their iv, i.e. the "slopes" of the linear
// functions. In the following graphs, the x-axis denotes the values of iv, from init to
// last. And the y-axis denotes the pointer position p(iv). Intuitively, this problem
// can be seen as having two bands that should not overlap.
//
//       Case 1                     Case 2                     Case 3
//       parallel lines             same sign slope            different sign slope
//                                  but not parallel
//
//       +---------+                +---------+                +---------+
//       |         |                |        #|                |#        |
//       |         |                |       # |                |  #      |
//       |        #|                |      #  |                |    #    |
//       |      #  |                |     #   |                |      #  |
//       |    #    |                |    #    |                |        #|
//       |  # ^    |                |   #     |                |        ^|
//       |#   |   #|                |  #      |                |        ||
//       |    v #  |                | #       |                |        v|
//       |    #    |                |#       #|                |        #|
//       |  #      |                |^     #  |                |      #  |
//       |#        |                ||   #    |                |    #    |
//       |         |                |v #      |                |  #      |
//       |         |                |#        |                |#        |
//       +---------+                +---------+                +---------+
//
//
// Case 1: parallel lines, i.e. iv_scale = iv_scale1 = iv_scale2
//
//   p1(iv)  = p1(init)  - init * iv_scale + iv * iv_scale
//   p2(iv)  = p2(init)  - init * iv_scale + iv * iv_scale
//
//   Given this, it follows:
//     p1(iv) + size1 <= p2(iv)      <==>      p1(init) + size1 <= p2(init)
//     p2(iv) + size2 <= p1(iv)      <==>      p2(init) + size2 <= p1(init)
//
//   Hence, we do not have to check the condition for every iv, but only for init.
//
//   p1(init) + size1 <= p2(init)  OR  p2(init) + size2 <= p1(init)
//   ----- is equivalent to -----      ---- is equivalent to ------
//          (P1-BEFORE-P2)         OR         (P1-AFTER-P2)
//
//
// Case 2 and 3: different slopes, i.e. iv_scale1 != iv_scale2
//
//   Without loss of generality, we assume iv_scale1 < iv_scale2.
//   (Otherwise, we just swap p1 and p2).
//
//   If iv_stride >= 0, i.e. init <= iv <= last:
//     (iv - init) * iv_scale1 <= (iv - init) * iv_scale2
//     (iv - last) * iv_scale1 >= (iv - last) * iv_scale2                 (POS-STRIDE)
//   If iv_stride <= 0, i.e. last <= iv <= init:
//     (iv - init) * iv_scale1 >= (iv - init) * iv_scale2
//     (iv - last) * iv_scale1 <= (iv - last) * iv_scale2                 (NEG-STRIDE)
//
//   Below, we show that these conditions are equivalent:
//
//       p1(init) + size1 <= p2(init)       (if iv_stride >= 0)  |    p2(last) + size2 <= p1(last)      (if iv_stride >= 0)   |
//       p1(last) + size1 <= p2(last)       (if iv_stride <= 0)  |    p2(init) + size2 <= p1(init)      (if iv_stride <= 0)   |
//       ---- are equivalent to -----                            |    ---- are equivalent to -----                            |
//              (P1-BEFORE-P2)                                   |           (P1-AFTER-P2)                                    |
//                                                               |                                                            |
//   Proof:                                                      |                                                            |
//                                                               |                                                            |
//     Assume: (P1-BEFORE-P2)                                    |  Assume: (P1-AFTER-P2)                                     |
//       for all iv in r: p1(iv) + size1 <= p2(iv)               |    for all iv in r: p2(iv) + size2 <= p1(iv)               |
//       => And since init and last in r =>                      |    => And since init and last in r =>                      |
//       p1(init) + size1 <= p2(init)                            |    p2(init) + size2 <= p1(init)                            |
//       p1(last) + size1 <= p2(last)                            |    p2(last) + size2 <= p1(last)                            |
//                                                               |                                                            |
//                                                               |                                                            |
//     Assume: p1(init) + size1 <= p2(init)                      |  Assume: p2(last) + size2 <= p1(last)                      |
//        and: iv_stride >= 0                                    |     and: iv_stride >= 0                                    |
//                                                               |                                                            |
//          size1 + p1(iv)                                       |       size2 + p2(iv)                                       |
//                  --------- apply (LINEAR-FORM-INIT) --------- |               --------- apply (LINEAR-FORM-LAST) --------- |
//        = size1 + p1(init) - init * iv_scale1 + iv * iv_scale1 |     = size2 + p2(last) - last * iv_scale2 + iv * iv_scale2 |
//                           ------ apply (POS-STRIDE) --------- |                        ------ apply (POS-STRIDE) --------- |
//       <= size1 + p1(init) - init * iv_scale2 + iv * iv_scale2 |    <= size2 + p2(last) - last * iv_scale1 + iv * iv_scale1 |
//          -- assumption --                                     |       -- assumption --                                     |
//       <=         p2(init) - init * iv_scale2 + iv * iv_scale2 |    <=         p1(last) - last * iv_scale1 + iv * iv_scale1 |
//                  --------- apply (LINEAR-FORM-INIT) --------- |               --------- apply (LINEAR-FORM-LAST) --------- |
//        =         p2(iv)                                       |     =         p1(iv)                                       |
//                                                               |                                                            |
//                                                               |                                                            |
//     Assume: p1(last) + size1 <= p2(last)                      |  Assume: p2(init) + size2 <= p1(init)                      |
//        and: iv_stride <= 0                                    |     and: iv_stride <= 0                                    |
//                                                               |                                                            |
//          size1 + p1(iv)                                       |       size2 + p2(iv)                                       |
//                  --------- apply (LINEAR-FORM-LAST) --------- |               --------- apply (LINEAR-FORM-INIT) --------- |
//        = size1 + p1(last) - last * iv_scale1 + iv * iv_scale1 |     = size2 + p2(init) - init * iv_scale2 + iv * iv_scale2 |
//                           ------ apply (NEG-STRIDE) --------- |                        ------ apply (NEG-STRIDE) --------- |
//       <= size1 + p1(last) - last * iv_scale2 + iv * iv_scale2 |    <= size2 + p2(init) - init * iv_scale1 + iv * iv_scale1 |
//          -- assumption --                                     |       -- assumption --                                     |
//       <=         p2(last) - last * iv_scale2 + iv * iv_scale2 |    <=         p1(init) - init * iv_scale1 + iv * iv_scale1 |
//                  --------- apply (LINEAR-FORM-LAST) --------- |               --------- apply (LINEAR-FORM-INIT) --------- |
//        =         p2(iv)                                       |     =         p1(iv)                                       |
//                                                               |                                                            |
//
//   The obtained conditions already look very simple. However, we would like to avoid
//   computing 4 addresses (p1(init), p1(last), p2(init), p2(last)), and would instead
//   prefer to only compute 2 addresses, and derive the other two from the distance (span)
//   between the pointers at init and last. Using (LINEAR-FORM-INIT), we get:
//
//     p1(last) = p1(init) - init * iv_scale1 + last * iv_scale1                 (SPAN-1)
//                         --------------- defines -------------
//                p1(init) + span1
//
//     p2(last) = p2(init) - init * iv_scale2 + last * iv_scale2                 (SPAN-2)
//                         --------------- defines -------------
//                p1(init) + span2
//
//     span1 = - init * iv_scale1 + last * iv_scale1 = (last - init) * iv_scale1
//     span2 = - init * iv_scale2 + last * iv_scale2 = (last - init) * iv_scale2
//
//   Thus, we can use the conditions below:
//     p1(init)         + size1 <= p2(init)          OR  p2(init) + span2 + size2 <= p1(init) + span1    (if iv_stride >= 0)
//     p1(init) + span1 + size1 <= p2(init) + span2  OR  p2(init)         + size2 <= p1(init)            (if iv_stride <= 0)
//
//   Below, we visualize the conditions, so that the reader can gain an intuitiion.
//   For simplicity, we only show the case with iv_stride > 0. Also, remember that
//   iv_scale1 < iv_scale2.
//
//                             +---------+                     +---------+
//                             |        #|                     |        #| <-- p1(init) + span1
//                             |       # |  ^ span2    span1 ^ |      # ^|
//                             |      #  |  |                | |    #   ||
//                             |     #   |  |                | |  #     v| <-- p2(init) + span2 + size2
//                             |    #    |  |                v |#       #|
//                             |   #     |  |          span2 ^ |       # |
//                             |  #      |  |                | |      #  |
//                             | #       |  |                | |     #   |
//        p2(init)         --> |#       #|  v                | |    #    |
//                             |^     #  |  ^ span1          | |   #     |
//                             ||   #    |  |                | |  #      |
//        p1(init) + size1 --> |v #      |  |                | | #       |
//                             |#        |  v                v |#        |
//                             +---------+                     +---------+
//
// -------------------------------------------------------------------------------------------------------------------------
//
// Computing the last iv value in a loop
// -------------------------------------
//
// Let us define a helper function, that computes the last iv value in a loop,
// given variable init and limit values, and a constant stride. If the loop
// is never entered, we just return the init value.
//
//   LAST(init, stride, limit), where stride > 0:   |  LAST(init, stride, limit), where stride < 0:
//     last = init                                  |  last = init
//     for (iv = init; iv < limit; iv += stride)    |  for (iv = init; iv > limit; iv += stride)
//       last = iv                                  |    last = iv
//
// It follows that for some k:
//    last = init + k * stride
//
// If the loop is not entered, we can set k=0.
//
// If the loop is entered:
//   last is very close to limit:
//     stride > 0  ->  limit - stride <= last < limit
//     stride < 0  ->  limit < last <= limit - stride
//
//     If stride > 0:
//         limit        - stride                   <= last              <   limit
//         limit        - stride                   <= init + k * stride <   limit
//         limit - init - stride                   <=        k * stride <   limit - init
//         limit - init - stride - 1               <         k * stride <=  limit - init - 1
//        (limit - init - stride - 1) / stride     <         k          <= (limit - init - 1) / stride
//        (limit - init          - 1) / stride - 1 <         k          <= (limit - init - 1) / stride
//     -> k = (limit - init - 1) / stride
//     -> dividend "limit - init - 1" is >=0. So a regular round to zero division can be used.
//        Note: to incorporate the case where the loop is not entered (init >= limit), we see
//              that the divident is zero or negative, and so the result will be zero or
//              negative. Thus, we can just clamp k to zero, or last to init, so that we get
//              a solution that also works when the loop is not entered:
//
//              k = (limit - init - 1) / abs(stride)
//              last = MAX(init, init + k * stride)
//
//     If stride < 0:
//         limit                               <  last              <=   limit        - stride
//         limit                               <  init + k * stride <=   limit        - stride
//         limit - init                        <         k * stride <=   limit - init - stride
//         limit - init + 1                    <=        k * stride <    limit - init - stride + 1
//        (limit - init + 1) /     stride      >=        k          >   (limit - init - stride + 1) /     stride
//       -(limit - init + 1) / abs(stride)     >=        k          >  -(limit - init - stride + 1) / abs(stride)
//       -(limit - init + 1) / abs(stride)     >=        k          >  -(limit - init          + 1) / abs(stride) - 1
//        (init - limit - 1) / abs(stride)     >=        k          >   (init - limit          - 1) / abs(stride) - 1
//        (init - limit - 1) / abs(stride)     >=        k          >   (init - limit          - 1) / abs(stride) - 1
//     -> k = (init - limit - 1) / abs(stride)
//     -> dividend "init - limit" is >=0. So a regular round to zero division can be used.
//        Note: to incorporate the case where the loop is not entered (init <= limit), we see
//              that the divident is zero or negative, and so the result will be zero or
//              negative. Thus, we can just clamp k to zero, or last to init, so that we get
//              a solution that also works when the loop is not entered:
//
//              k = (init - limit - 1) / abs(stride)
//              last = MIN(init, init + k * stride)
//
// Now we can put it all together:
//   LAST(init, stride, limit)
//     If stride > 0:
//       k = (limit - init - 1) / abs(stride)
//       last = MAX(init, init + k * stride)
//     If stride < 0:
//       k = (init - limit - 1) / abs(stride)
//       last = MIN(init, init + k * stride)
//
// We will have to consider the implications of clamping to init when the loop is not entered
// at the use of LAST further down.
//
// -------------------------------------------------------------------------------------------------------------------------
//
// Computing init and last for the main-loop
// -----------------------------------------
//
// As we have seen above, we always need the "init" of the main-loop. And if "iv_scale1 != iv_scale2", then we
// also need the "last" of the main-loop. These values need to be pre-loop invariant, because the check is
// to be performed before the pre-loop (at the predicate or multiversioning selector_if). It will be helpful
// to recall the iv structure in the pre and main-loop:
//
//                  | iv = pre_init
//                  |
//   Pre-Loop       | +----------------+
//                  phi                |
//                   |                 |  -> pre_last: last iv value in pre-loop
//                   + pre_iv_stride   |
//                   |-----------------+
//                   | exit check: < pre_limit
//                   |
//                   | iv = main_init = init
//                   |
//   Main-Loop       | +------------------------------+
//                   phi                              |
//                    |                               | -> last: last iv value in main-loop
//                    + main_iv_stride = iv_stride    |
//                    |-------------------------------+
//                    | exit check: < main_limit = limit
//
// Unfortunately, the init (aka. main_init) is not pre-loop invariant, rather it is only available
// after the pre-loop. We will have to compute:
//
//   pre_last = LAST(pre_init, pre_iv_stride, pre_limit)
//   init = pre_last + pre_iv_stride
//
// If we need "last", we unfortunately must compute it as well:
//
//   last = LAST(init, iv_stride, limit)
//
//
// These computations assume that we indeed do enter the main-loop - otherwise
// it does not make sense to talk about the "last main iteration". Of course
// entering the main-loop implies that we entered the pre-loop already. But
// what happens if we check the aliasing runtime check, but later would never
// enter the main-loop?
//
// First: no matter if we pass or fail the aliasing runtime check, we will
// not get wrong results. If we fail the check, we end up in the less optimized
// slow-loop. If we pass the check, and we don't enter the main-loop, we
// never rely on the aliasing check, after all only the vectorized main-loop
// (and the vectorized post-loop) rely on the aliasing check.
//
// But: The worry is that we may fail the aliasing runtime check "spuriously",
// i.e. even though we would never enter the main-loop, and that this could have
// unfortunate side-effects (for example deopting unnecessarily). Let's
// look at the two possible cases:
//  1) We would never even enter the pre-loop.
//     There are only predicates between the aliasing runtime check and the pre-loop,
//     so a predicate would have to fail. These are rather rare cases. If we
//     are using multiversioning for the aliasing runtime check, we would
//     immediately fail the predicate in either the slow or fast loop, so
//     the decision of the aliasing runtime check does not matter. But if
//     we are using a predicate for the aliaing runtime check, then we may
//     end up deopting twice: once for the aliasing runtime check, and then
//     again for the other predicate. This would not be great, but again,
//     failing predicates are rare in the first place.
//
//  2) We would enter the pre-loop, but not the main-loop.
//     The pre_last must be accurate, because we are entering the pre-loop.
//     But then we fail the zero-trip guard of the main-loop. Thus, for the
//     main-loop, the init lies "after" the limit. Thus, the computed last
//     for the main-loop equals the init. This means that span1 and span2
//     are zero. Hence, p1(init) and p2(init) would have to alias for the
//     aliasing runtime check to fail. Hence, it would not be surprising
//     at all if we deopted because of the aliasing runtime check.
//
bool VPointer::can_make_speculative_aliasing_check_with(const VPointer& other) const {
  const VPointer& vp1 = *this;
  const VPointer& vp2 = other;

  if (!_vloop.use_speculative_aliasing_checks()) { return false; }

  // Both pointers need a nice linear form, otherwise we cannot formulate the check.
  if (!vp1.is_valid() || !vp2.is_valid()) { return false; }

  // The pointers always overlap -> a speculative check would always fail.
  if (vp1.always_overlaps_with(vp2)) { return false; }

  // The pointers never overlap -> a speculative check would always succeed.
  assert(!vp1.never_overlaps_with(vp2), "ensured by caller");

  // The speculative aliasing check happens either at the AutoVectorization predicate
  // or at the multiversion_if. That is before the pre-loop. From the construction of
  // VPointer, we already know that all its variables (except iv) are pre-loop invariant.
  //
  // In VPointer::make_speculative_aliasing_check_with we compute main_init in all
  // cases. For this, we require pre_init and pre_limit. These values must be available
  // for the speculative check, i.e. their control must dominate the speculative check.
  // Further, "if vp1.iv_scale() != vp2.iv_scale()" we additionally need to have
  // main_limit available for the speculative check.
  // Note: no matter if the speculative check is inserted as a predicate or at the
  //       multiversion if, the speculative check happens before (dominates) the
  //       pre-loop.
  Node* pre_init = _vloop.pre_loop_end()->init_trip();
  Opaque1Node* pre_limit_opaq = _vloop.pre_loop_end()->limit()->as_Opaque1();
  Node* pre_limit = pre_limit_opaq->in(1);
  Node* main_limit = _vloop.cl()->limit();
  if (!_vloop.is_available_for_speculative_check(pre_init)) {
#ifdef ASSERT
    if (_vloop.is_trace_speculative_aliasing_analysis()) {
      tty->print_cr("VPointer::can_make_speculative_aliasing_check_with: pre_limit is not available at speculative check!");
    }
#endif
    return false;
  }
  if (!_vloop.is_available_for_speculative_check(pre_limit)) {
#ifdef ASSERT
    if (_vloop.is_trace_speculative_aliasing_analysis()) {
      tty->print_cr("VPointer::can_make_speculative_aliasing_check_with: pre_limit is not available at speculative check!");
    }
#endif
    return false;
  }

  if (vp1.iv_scale() != vp2.iv_scale() && !_vloop.is_available_for_speculative_check(main_limit)) {
#ifdef ASSERT
    if (_vloop.is_trace_speculative_aliasing_analysis()) {
      tty->print_cr("VPointer::can_make_speculative_aliasing_check_with: main_limit is not available at speculative check!");
    }
#endif
    return false;
  }

  // The speculative check also needs to create the pointer expressions for both
  // VPointers. We must check that we can do that, i.e. that all variables of the
  // VPointers are available at the speculative check (and not just pre-loop invariant).
  if (!this->can_make_pointer_expression_at_speculative_check()) {
#ifdef ASSERT
    if (_vloop.is_trace_speculative_aliasing_analysis()) {
      tty->print_cr("VPointer::can_make_speculative_aliasing_check_with: not all variables of VPointer are avaialbe at speculative check!");
      this->print_on(tty);
    }
#endif
    return false;
  }

  if (!other.can_make_pointer_expression_at_speculative_check()) {
#ifdef ASSERT
    if (_vloop.is_trace_speculative_aliasing_analysis()) {
      tty->print_cr("VPointer::can_make_speculative_aliasing_check_with: not all variables of VPointer are avaialbe at speculative check!");
      other.print_on(tty);
    }
#endif
    return false;
  }

  return true;
}

// For description and derivation see "Computing the last iv value in a loop".
// Note: the iv computations here should not overflow. But out of an abundance
//       of caution, we compute everything in long anyway.
Node* make_last(Node* initL, jint stride, Node* limitL, PhaseIdealLoop* phase) {
  PhaseIterGVN& igvn = phase->igvn();

  Node* abs_strideL = igvn.longcon(abs(stride));
  Node* strideL = igvn.longcon(stride);

  // If in some rare case the limit is "before" init, then
  // this subtraction could overflow. Doing the calculations
  // in long prevents this. Below, we clamp the "last" value
  // back to init, which gets us back into the safe int range.
  Node* diffL = (stride > 0) ? new SubLNode(limitL, initL)
                             : new SubLNode(initL, limitL);
  Node* diffL_m1 = new AddLNode(diffL, igvn.longcon(-1));
  Node* k = new DivLNode(nullptr, diffL_m1, abs_strideL);

  // Compute last = init + k * iv_stride
  Node* k_mul_stride = new MulLNode(k, strideL);
  Node* last = new AddLNode(initL, k_mul_stride);

  // Make sure that the last does not lie "before" init.
  Node* last_clamped = MaxNode::build_min_max_long(&igvn, initL, last, stride > 0);

  phase->register_new_node_with_ctrl_of(diffL,        initL);
  phase->register_new_node_with_ctrl_of(diffL_m1,     initL);
  phase->register_new_node_with_ctrl_of(k,            initL);
  phase->register_new_node_with_ctrl_of(k_mul_stride, initL);
  phase->register_new_node_with_ctrl_of(last,         initL);
  phase->register_new_node_with_ctrl_of(last_clamped, initL);

  return last_clamped;
}

BoolNode* make_a_plus_b_leq_c(Node* a, Node* b, Node* c, PhaseIdealLoop* phase) {
  Node* a_plus_b = new AddLNode(a, b);
  Node* cmp = CmpNode::make(a_plus_b, c, T_LONG, true);
  BoolNode* bol = new BoolNode(cmp, BoolTest::le);
  phase->register_new_node_with_ctrl_of(a_plus_b, a);
  phase->register_new_node_with_ctrl_of(cmp, a);
  phase->register_new_node_with_ctrl_of(bol, a);
  return bol;
}

BoolNode* VPointer::make_speculative_aliasing_check_with(const VPointer& other, Node* ctrl) const {
  // Ensure iv_scale1 <= iv_scale2.
  const VPointer& vp1 = (this->iv_scale() <= other.iv_scale()) ? *this : other;
  const VPointer& vp2 = (this->iv_scale() <= other.iv_scale()) ? other :*this ;
  assert(vp1.iv_scale() <= vp2.iv_scale(), "ensured by swapping if necessary");

  assert(vp1.can_make_speculative_aliasing_check_with(vp2), "sanity");

  PhaseIdealLoop* phase = _vloop.phase();
  PhaseIterGVN& igvn = phase->igvn();

  // init (aka main_init): compute it from the the pre-loop structure.
  // As described above, we cannot just take the _vloop.cl().init_trip(), because that
  // value is pre-loop dependent, and we need a pre-loop independent value, so we can
  // have it available at the predicate / multiversioning selector_if.
  // For this, we need to be sure that the pre_limit is pre-loop independent as well,
  // see can_make_speculative_aliasing_check_with.
  Node* pre_init = _vloop.pre_loop_end()->init_trip();
  jint pre_iv_stride = _vloop.pre_loop_end()->stride_con();
  Opaque1Node* pre_limit_opaq = _vloop.pre_loop_end()->limit()->as_Opaque1();
  Node* pre_limit = pre_limit_opaq->in(1);
  assert(_vloop.is_pre_loop_invariant(pre_init),  "needed for aliasing check before pre-loop");
  assert(_vloop.is_pre_loop_invariant(pre_limit), "needed for aliasing check before pre-loop");
  assert(_vloop.is_available_for_speculative_check(pre_init),  "ctrl must be early enough to avoid cycles");
  assert(_vloop.is_available_for_speculative_check(pre_limit), "ctrl must be early enough to avoid cycles");

  Node* pre_initL = new ConvI2LNode(pre_init);
  Node* pre_limitL = new ConvI2LNode(pre_limit);
  phase->register_new_node_with_ctrl_of(pre_initL, pre_init);
  phase->register_new_node_with_ctrl_of(pre_limitL, pre_init);

  Node* pre_lastL = make_last(pre_initL, pre_iv_stride, pre_limitL, phase);

  Node* main_initL = new AddLNode(pre_lastL, igvn.longcon(pre_iv_stride));
  phase->register_new_node_with_ctrl_of(main_initL, pre_init);

  Node* main_init = new ConvL2INode(main_initL);
  phase->register_new_node_with_ctrl_of(main_init, pre_init);

  assert(vp1.can_make_pointer_expression_at_speculative_check(), "variables must be available early enough to avoid cycles");
  assert(vp2.can_make_pointer_expression_at_speculative_check(), "variables must be available early enough to avoid cycles");
  Node* p1_init = vp1.make_pointer_expression(main_init, ctrl);
  Node* p2_init = vp2.make_pointer_expression(main_init, ctrl);
  Node* size1 = igvn.longcon(vp1.size());
  Node* size2 = igvn.longcon(vp2.size());

#ifdef ASSERT
  if (_vloop.is_trace_speculative_aliasing_analysis() || _vloop.is_trace_speculative_runtime_checks()) {
    tty->print_cr("\nVPointer::make_speculative_aliasing_check_with:");
    tty->print("pre_init:  "); pre_init->dump();
    tty->print("pre_limit: "); pre_limit->dump();
    tty->print("pre_lastL: "); pre_lastL->dump();
    tty->print("main_init: "); main_init->dump();
    tty->print_cr("p1_init:");
    p1_init->dump_bfs(5, nullptr, "");
    tty->print_cr("p2_init:");
    p2_init->dump_bfs(5, nullptr, "");
  }
#endif

  BoolNode* condition1 = nullptr;
  BoolNode* condition2 = nullptr;
  if (vp1.iv_scale() == vp2.iv_scale()) {
#ifdef ASSERT
    if (_vloop.is_trace_speculative_aliasing_analysis() || _vloop.is_trace_speculative_runtime_checks()) {
      tty->print_cr("  Same iv_scale(%d) -> parallel lines -> simple conditions:", vp1.iv_scale());
      tty->print_cr("  p1(init) + size1 <= p2(init)  OR  p2(init) + size2 <= p1(init)");
      tty->print_cr("  -------- condition1 --------      ------- condition2 ---------");
    }
#endif
    condition1 = make_a_plus_b_leq_c(p1_init, size1, p2_init, phase);
    condition2 = make_a_plus_b_leq_c(p2_init, size2, p1_init, phase);
  } else {
    assert(vp1.iv_scale() < vp2.iv_scale(), "assumed in proof, established above by swapping");

#ifdef ASSERT
    if (_vloop.is_trace_speculative_aliasing_analysis() || _vloop.is_trace_speculative_runtime_checks()) {
      tty->print_cr("  Different iv_scale -> lines with different slopes -> more complex conditions:");
      tty->print_cr("  p1(init)         + size1 <= p2(init)          OR  p2(init) + span2 + size2 <= p1(init) + span1  (if iv_stride >= 0)");
      tty->print_cr("  p1(init) + span1 + size1 <= p2(init) + span2  OR  p2(init)         + size2 <= p1(init)          (if iv_stride <= 0)");
      tty->print_cr("  ---------------- condition1 ----------------      --------------- condition2 -----------------");
    }
#endif

    // last (aka main_last): compute from main-loop structure.
    jint main_iv_stride = _vloop.iv_stride();
    Node* main_limit = _vloop.cl()->limit();
    assert(_vloop.is_pre_loop_invariant(main_limit), "needed for aliasing check before pre-loop");
    assert(_vloop.is_available_for_speculative_check(main_limit), "ctrl must be early enough to avoid cycles");

    Node* main_limitL = new ConvI2LNode(main_limit);
    phase->register_new_node_with_ctrl_of(main_limitL, pre_init);

    Node* main_lastL = make_last(main_initL, main_iv_stride, main_limitL, phase);

    // Compute span1 = (last - init) * iv_scale1
    //         span2 = (last - init) * iv_scale2
    Node* last_minus_init = new SubLNode(main_lastL, main_initL);
    Node* iv_scale1 = igvn.longcon(vp1.iv_scale());
    Node* iv_scale2 = igvn.longcon(vp2.iv_scale());
    Node* span1 = new MulLNode(last_minus_init, iv_scale1);
    Node* span2 = new MulLNode(last_minus_init, iv_scale2);

    phase->register_new_node_with_ctrl_of(last_minus_init, pre_init);
    phase->register_new_node_with_ctrl_of(span1,           pre_init);
    phase->register_new_node_with_ctrl_of(span2,           pre_init);

#ifdef ASSERT
    if (_vloop.is_trace_speculative_aliasing_analysis() || _vloop.is_trace_speculative_runtime_checks()) {
      tty->print("main_limitL: "); main_limitL->dump();
      tty->print("main_lastL: "); main_lastL->dump();
      tty->print("p1_init: "); p1_init->dump();
      tty->print("p2_init: "); p2_init->dump();
      tty->print("size1: "); size1->dump();
      tty->print("size2: "); size2->dump();
      tty->print_cr("span1: "); span1->dump_bfs(5, nullptr, "");
      tty->print_cr("span2: "); span2->dump_bfs(5, nullptr, "");
    }
#endif

    Node* p1_init_plus_span1 = new AddLNode(p1_init, span1);
    Node* p2_init_plus_span2 = new AddLNode(p2_init, span2);
    phase->register_new_node_with_ctrl_of(p1_init_plus_span1, pre_init);
    phase->register_new_node_with_ctrl_of(p2_init_plus_span2, pre_init);
    if (_vloop.iv_stride() >= 0) {
      condition1 = make_a_plus_b_leq_c(p1_init,            size1, p2_init,            phase);
      condition2 = make_a_plus_b_leq_c(p2_init_plus_span2, size2, p1_init_plus_span1, phase);
    } else {
      condition1 = make_a_plus_b_leq_c(p1_init_plus_span1, size1, p2_init_plus_span2, phase);
      condition2 = make_a_plus_b_leq_c(p2_init,            size2, p1_init,            phase);
    }
  }

#ifdef ASSERT
  if (_vloop.is_trace_speculative_aliasing_analysis() || _vloop.is_trace_speculative_runtime_checks()) {
    tty->print_cr("condition1:");
    condition1->dump_bfs(5, nullptr, "");
    tty->print_cr("condition2:");
    condition2->dump_bfs(5, nullptr, "");
  }
#endif

  // Construct "condition1 OR condition2". Convert the bol value back to an int value
  // that we can "OR" to create a single bol value. On x64, the two CMove are converted
  // to two setbe instructions which capture the condition bits to a register, meaning
  // we only have a single branch in the end.
  Node* zero = igvn.intcon(0);
  Node* one  = igvn.intcon(1);
  Node* cmov1 = new CMoveINode(condition1, zero, one, TypeInt::INT);
  Node* cmov2 = new CMoveINode(condition2, zero, one, TypeInt::INT);
  phase->register_new_node_with_ctrl_of(cmov1, main_initL);
  phase->register_new_node_with_ctrl_of(cmov2, main_initL);

  Node* c1_or_c2 = new OrINode(cmov1, cmov2);
  Node* cmp = CmpNode::make(c1_or_c2, zero, T_INT);
  BoolNode* bol = new BoolNode(cmp, BoolTest::ne);
  phase->register_new_node_with_ctrl_of(c1_or_c2, main_initL);
  phase->register_new_node_with_ctrl_of(cmp, main_initL);
  phase->register_new_node_with_ctrl_of(bol, main_initL);

  return bol;
}

// Creates the long pointer expression, evaluated with iv = iv_value.
// Since we are casting pointers to long with CastP2X, we must be careful
// that the values do not cross SafePoints, where the oop could be moved
// by GC, and the already cast value would not be updated, as it is not in
// the oop-map. For this, we must set a ctrl that is late enough, so that we
// cannot cross a SafePoint.
Node* VPointer::make_pointer_expression(Node* iv_value, Node* ctrl) const {
  assert(is_valid(), "must be valid");

  PhaseIdealLoop* phase = _vloop.phase();
  PhaseIterGVN& igvn = phase->igvn();
  Node* iv = _vloop.iv();

  auto maybe_add = [&] (Node* n1, Node* n2, BasicType bt) {
    if (n1 == nullptr) { return n2; }
    Node* add = AddNode::make(n1, n2, bt);
    phase->register_new_node(add, ctrl);
    return add;
  };

  Node* expression = nullptr;
  mem_pointer().for_each_raw_summand_of_int_group(0, [&] (const MemPointerRawSummand& s) {
    Node* node = nullptr;
    if (s.is_con()) {
      // Long constant.
      NoOverflowInt con = s.scaleI() * s.scaleL();
      node = igvn.longcon(con.value());
    } else {
      // Long variable.
      assert(s.scaleI().is_one(), "must be long variable");
      Node* scaleL = igvn.longcon(s.scaleL().value());
      Node* variable = (s.variable() == iv) ? iv_value : s.variable();
      if (variable->bottom_type()->isa_ptr() != nullptr) {
        // Use a ctrl that is late enough, so that we do not
        // evaluate the cast before a SafePoint.
        variable = new CastP2XNode(ctrl, variable);
        phase->register_new_node(variable, ctrl);
      }
      node = new MulLNode(scaleL, variable);
      phase->register_new_node(node, ctrl);
    }
    expression = maybe_add(expression, node, T_LONG);
  });

  int max_int_group = mem_pointer().max_int_group();
  for (int int_group = 1; int_group <= max_int_group; int_group++) {
    Node* int_expression = nullptr;
    NoOverflowInt int_group_scaleL;
    mem_pointer().for_each_raw_summand_of_int_group(int_group, [&] (const MemPointerRawSummand& s) {
      Node* node = nullptr;
      if (s.is_con()) {
        node = igvn.intcon(s.scaleI().value());
      } else {
        Node* scaleI = igvn.intcon(s.scaleI().value());
        Node* variable = (s.variable() == iv) ? iv_value : s.variable();
        node = new MulINode(scaleI, variable);
        phase->register_new_node(node, ctrl);
      }
      int_group_scaleL = s.scaleL(); // remember for multiplication after ConvI2L
      int_expression = maybe_add(int_expression, node, T_INT);
    });
    assert(int_expression != nullptr, "no empty int group");
    int_expression = new ConvI2LNode(int_expression);
    phase->register_new_node(int_expression, ctrl);
    Node* scaleL = igvn.longcon(int_group_scaleL.value());
    int_expression = new MulLNode(scaleL, int_expression);
    phase->register_new_node(int_expression, ctrl);
    expression = maybe_add(expression, int_expression, T_LONG);
  }

  return expression;
}

#ifndef PRODUCT
void VPointer::print_on(outputStream* st, bool end_with_cr) const {
  st->print("VPointer[");

  if (!is_valid()) {
    st->print_cr("invalid]");
    return;
  }

  st->print("size: %2d, %s, ", size(),
            _mem_pointer.base().is_object() ? "object" : "native");

  Node* base = _mem_pointer.base().object_or_native();
  tty->print("base(%d %s) + con(%3d) + iv_scale(%3d) * iv + invar(",
             base->_idx, base->Name(),
             _mem_pointer.con().value(),
             _iv_scale);

  int count = 0;
  for_each_invar_summand([&] (const MemPointerSummand& s) {
    if (count > 0) {
      st->print(" + ");
    }
    s.print_on(tty);
    count++;
  });
  if (count == 0) {
    st->print("0");
  }
  st->print(")]");
  if (end_with_cr) { st->cr(); }
}
#endif

AlignmentSolution* AlignmentSolver::solve() const {
  DEBUG_ONLY( trace_start_solve(); )

  // Out of simplicity: non power-of-2 stride not supported.
  if (!is_power_of_2(abs(_pre_stride))) {
    return new EmptyAlignmentSolution("non power-of-2 stride not supported");
  }
  assert(is_power_of_2(abs(_main_stride)), "main_stride is power of 2");
  assert(_aw > 0 && is_power_of_2(_aw), "aw must be power of 2");

  // Out of simplicity: non power-of-2 iv_scale not supported.
  if (abs(iv_scale()) == 0 || !is_power_of_2(abs(iv_scale()))) {
    return new EmptyAlignmentSolution("non power-of-2 iv_scale not supported");
  }

  // We analyze the address of mem_ref. The idea is to disassemble it into a linear
  // expression, where we can use the constant factors as the basis for ensuring the
  // alignment of vector memory accesses.
  //
  // The Simple form of the address is disassembled by VPointer into:
  //
  //   adr = base + invar + iv_scale * iv + con
  //
  // Where the iv can be written as:
  //
  //   iv = init + pre_stride * pre_iter + main_stride * main_iter
  //
  // init:        value before pre-loop
  // pre_stride:  increment per pre-loop iteration
  // pre_iter:    number of pre-loop iterations (adjustable via pre-loop limit)
  // main_stride: increment per main-loop iteration (= pre_stride * unroll_factor)
  // main_iter:   number of main-loop iterations (main_iter >= 0)
  //
  // In the following, we restate the Simple form of the address expression, by first
  // expanding the iv variable. In a second step, we reshape the expression again, and
  // state it as a linear expression, consisting of 6 terms.
  //
  //          Simple form             Expansion of iv variable                  Reshaped with constants   Comments for terms
  //          -----------             ------------------------                  -----------------------   ------------------
  //   adr =  base                 =  base                                   =  base                      (assume: base % aw = 0)
  //        + invar                 + invar_factor * var_invar                + C_invar * var_invar       (term for invariant)
  //                            /   + iv_scale * init                         + C_init  * var_init        (term for variable init)
  //        + iv_scale * iv  -> |   + iv_scale * pre_stride * pre_iter        + C_pre   * pre_iter        (adjustable pre-loop term)
  //                            \   + iv_scale * main_stride * main_iter      + C_main  * main_iter       (main-loop term)
  //        + con                   + con                                     + C_const                   (sum of constant terms)
  //
  // We describe the 6 terms:
  //   1) The "base" of the address:
  //        - For heap objects, this is the base of the object, and as such
  //          ObjectAlignmentInBytes (a power of 2) aligned.
  //        - For off-heap / native memory, the "base" has no alignment
  //          gurantees. To ensure alignment we can do either of these:
  //          - Add a runtime check to verify ObjectAlignmentInBytes alignment,
  //            i.e. we can speculatively compile with an alignment assumption.
  //            If we pass the check, we can go into the loop with the alignment
  //            assumption, if we fail we have to trap/deopt or take the other
  //            loop version without alignment assumptions.
  //          - If runtime checks are not possible, then we return an empty
  //            solution, i.e. we do not vectorize the corresponding pack.
  //
  //      Let us assume we have an object "base", or passed the alignment
  //      runtime check for native "bases", hence we know:
  //
  //        base % ObjectAlignmentInBytes = 0
  //
  //      We defined aw = MIN(vector_width, ObjectAlignmentInBytes), which is
  //      a power of 2. And hence we know that "base" is thus also aw-aligned:
  //
  //        base % ObjectAlignmentInBytes = 0     ==>    base % aw = 0              (BASE_ALIGNED)
  //
  //   2) The "C_const" term is the sum of all constant terms. This is "con",
  //      plus "iv_scale * init" if it is constant.
  //   3) The "C_invar * var_invar" is the factorization of "invar" into a constant
  //      and variable term. If there is no invariant, then "C_invar" is zero.
  //
  //        invar = C_invar * var_invar                                             (FAC_INVAR)
  //
  //   4) The "C_init * var_init" is the factorization of "iv_scale * init" into a
  //      constant and a variable term. If "init" is constant, then "C_init" is
  //      zero, and "C_const" accounts for "init" instead.
  //
  //        iv_scale * init = C_init * var_init + iv_scale * C_const_init           (FAC_INIT)
  //        C_init       = (init is constant) ? 0    : iv_scale
  //        C_const_init = (init is constant) ? init : 0
  //
  //   5) The "C_pre * pre_iter" term represents how much the iv is incremented
  //      during the "pre_iter" pre-loop iterations. This term can be adjusted
  //      by changing the pre-loop limit, which defines how many pre-loop iterations
  //      are executed. This allows us to adjust the alignment of the main-loop
  //      memory reference.
  //   6) The "C_main * main_iter" term represents how much the iv is increased
  //      during "main_iter" main-loop iterations.

  // For native memory, we must add a runtime-check that "base % ObjectAlignmentInBytes = ",
  // to ensure (BASE_ALIGNED). If we cannot add this runtime-check, we have no guarantee on
  // its alignment.
  if (!_vpointer.mem_pointer().base().is_object() && !_are_speculative_checks_possible) {
    return new EmptyAlignmentSolution("Cannot add speculative check for native memory alignment.");
  }

  // Attribute init (i.e. _init_node) either to C_const or to C_init term.
  const int C_const_init = _init_node->is_ConI() ? _init_node->as_ConI()->get_int() : 0;
  const int C_const =      _vpointer.con() + C_const_init * iv_scale();

  // Set C_invar depending on if invar is present
  const int C_invar = _vpointer.compute_invar_factor();

  const int C_init = _init_node->is_ConI() ? 0 : iv_scale();
  const int C_pre =  iv_scale() * _pre_stride;
  const int C_main = iv_scale() * _main_stride;

  DEBUG_ONLY( trace_reshaped_form(C_const, C_const_init, C_invar, C_init, C_pre, C_main); )

  // We must find a pre_iter, such that adr is aw aligned: adr % aw = 0. Note, that we are defining the
  // modulo operator "%" such that the remainder is always positive, see AlignmentSolution::mod(i, q).
  //
  // Since "base % aw = 0" (BASE_ALIGNED), we only need to ensure alignment of the other 5 terms:
  //
  //   (C_const + C_invar * var_invar + C_init * var_init + C_pre * pre_iter + C_main * main_iter) % aw = 0      (1)
  //
  // Alignment must be maintained over all main-loop iterations, i.e. for any main_iter >= 0, we require:
  //
  //   C_main % aw = 0                                                                                           (2)
  //
  const int C_main_mod_aw = AlignmentSolution::mod(C_main, _aw);

  DEBUG_ONLY( trace_main_iteration_alignment(C_const, C_invar, C_init, C_pre, C_main, C_main_mod_aw); )

  if (C_main_mod_aw != 0) {
    return new EmptyAlignmentSolution("EQ(2) not satisfied (cannot align across main-loop iterations)");
  }

  // In what follows, we need to show that the C_const, init and invar terms can be aligned by
  // adjusting the pre-loop iteration count (pre_iter), which is controlled by the pre-loop
  // limit.
  //
  //     (C_const + C_invar * var_invar + C_init * var_init + C_pre * pre_iter) % aw = 0                         (3)
  //
  // We strengthen the constraints by splitting the equation into 3 equations, where we
  // want to find integer solutions for pre_iter_C_const, pre_iter_C_invar, and
  // pre_iter_C_init, which means that the C_const, init and invar terms can be aligned
  // independently:
  //
  //   (C_const             + C_pre * pre_iter_C_const) % aw = 0                 (4a)
  //   (C_invar * var_invar + C_pre * pre_iter_C_invar) % aw = 0                 (4b)
  //   (C_init  * var_init  + C_pre * pre_iter_C_init ) % aw = 0                 (4c)
  //
  // We now prove that (4a, b, c) are sufficient as well as necessary to guarantee (3)
  // for any runtime value of var_invar and var_init (i.e. for any invar and init).
  // This tells us that the "strengthening" does not restrict the algorithm more than
  // necessary.
  //
  // Sufficient (i.e (4a, b, c) imply (3)):
  //
  //   pre_iter = pre_iter_C_const + pre_iter_C_invar + pre_iter_C_init
  //
  // Adding up (4a, b, c):
  //
  //   0 = (  C_const             + C_pre * pre_iter_C_const
  //        + C_invar * var_invar + C_pre * pre_iter_C_invar
  //        + C_init  * var_init  + C_pre * pre_iter_C_init  ) % aw
  //
  //     = (  C_const + C_invar * var_invar + C_init * var_init
  //        + C_pre * (pre_iter_C_const + pre_iter_C_invar + pre_iter_C_init)) % aw
  //
  //     = (  C_const + C_invar * var_invar + C_init * var_init
  //        + C_pre * pre_iter) % aw
  //
  // Necessary (i.e. (3) implies (4a, b, c)):
  //  (4a): Set var_invar = var_init = 0 at runtime. Applying this to (3), we get:
  //
  //        0 =
  //          = (C_const + C_invar * var_invar + C_init * var_init + C_pre * pre_iter) % aw
  //          = (C_const + C_invar * 0         + C_init * 0        + C_pre * pre_iter) % aw
  //          = (C_const                                           + C_pre * pre_iter) % aw
  //
  //        This is of the same form as (4a), and we have a solution:
  //        pre_iter_C_const = pre_iter
  //
  //  (4b): Set var_init = 0, and assume (4a), which we just proved is implied by (3).
  //        Subtract (4a) from (3):
  //
  //        0 =
  //          =  (C_const + C_invar * var_invar + C_init * var_init + C_pre * pre_iter) % aw
  //           - (C_const + C_pre * pre_iter_C_const) % aw
  //          =  (C_invar * var_invar + C_init * var_init + C_pre * pre_iter - C_pre * pre_iter_C_const) % aw
  //          =  (C_invar * var_invar + C_init * 0        + C_pre * (pre_iter - pre_iter_C_const)) % aw
  //          =  (C_invar * var_invar +                   + C_pre * (pre_iter - pre_iter_C_const)) % aw
  //
  //        This is of the same form as (4b), and we have a solution:
  //        pre_iter_C_invar = pre_iter - pre_iter_C_const
  //
  //  (4c): Set var_invar = 0, and assume (4a), which we just proved is implied by (3).
  //        Subtract (4a) from (3):
  //
  //        0 =
  //          =  (C_const + C_invar * var_invar + C_init * var_init + C_pre * pre_iter) % aw
  //           - (C_const + C_pre * pre_iter_C_const) % aw
  //          =  (C_invar * var_invar + C_init * var_init + C_pre * pre_iter - C_pre * pre_iter_C_const) % aw
  //          =  (C_invar * 0         + C_init * var_init + C_pre * (pre_iter - pre_iter_C_const)) % aw
  //          =  (                    + C_init * var_init + C_pre * (pre_iter - pre_iter_C_const)) % aw
  //
  //        This is of the same form as (4c), and we have a solution:
  //        pre_iter_C_invar = pre_iter - pre_iter_C_const
  //
  // The solutions of Equations (4a, b, c) for pre_iter_C_const, pre_iter_C_invar, and pre_iter_C_init
  // respectively, can have one of these states:
  //
  //   trivial:     The solution can be any integer.
  //   constrained: There is a (periodic) solution, but it is not trivial.
  //   empty:       Statically we cannot guarantee a solution for all var_invar and var_init.
  //
  // We look at (4a):
  //
  //   abs(C_pre) >= aw
  //   -> Since abs(C_pre) is a power of two, we have C_pre % aw = 0. Therefore:
  //
  //        For any pre_iter_C_const: (C_pre * pre_iter_C_const) % aw = 0
  //
  //        (C_const + C_pre * pre_iter_C_const) % aw = 0
  //         C_const                             % aw = 0
  //
  //      Hence, we can only satisfy (4a) if C_Const is aw aligned:
  //
  //      C_const % aw == 0:
  //      -> (4a) has a trivial solution since we can choose any value for pre_iter_C_const.
  //
  //      C_const % aw != 0:
  //      -> (4a) has an empty solution since no pre_iter_C_const can achieve aw alignment.
  //
  //   abs(C_pre) < aw:
  //   -> Since both abs(C_pre) and aw are powers of two, we know:
  //
  //        There exists integer x > 1: aw = abs(C_pre) * x
  //
  //      C_const % abs(C_pre) == 0:
  //      -> There exists integer z: C_const = C_pre * z
  //
  //          (C_const   + C_pre * pre_iter_C_const) % aw               = 0
  //          ==>
  //          (C_pre * z + C_pre * pre_iter_C_const) % aw               = 0
  //          ==>
  //          (C_pre * z + C_pre * pre_iter_C_const) % (abs(C_pre) * x) = 0
  //          ==>
  //          (        z +         pre_iter_C_const) %               x  = 0
  //          ==>
  //          for any m: pre_iter_C_const = m * x - z
  //
  //        Hence, pre_iter_C_const has a non-trivial (because x > 1) periodic (periodicity x)
  //        solution, i.e. it has a constrained solution.
  //
  //      C_const % abs(C_pre) != 0:
  //        There exists integer x > 1: aw = abs(C_pre) * x
  //
  //           C_const                             %  abs(C_pre)      != 0
  //          ==>
  //          (C_const + C_pre * pre_iter_C_const) %  abs(C_pre)      != 0
  //          ==>
  //          (C_const + C_pre * pre_iter_C_const) % (abs(C_pre) * x) != 0
  //          ==>
  //          (C_const + C_pre * pre_iter_C_const) % aw               != 0
  //
  //        This is in contradiction with (4a), and therefore there cannot be any solution,
  //        i.e. we have an empty solution.
  //
  // In summary, for (4a):
  //
  //   abs(C_pre) >= aw  AND  C_const % aw == 0          -> trivial
  //   abs(C_pre) >= aw  AND  C_const % aw != 0          -> empty
  //   abs(C_pre) <  aw  AND  C_const % abs(C_pre) == 0  -> constrained
  //   abs(C_pre) <  aw  AND  C_const % abs(C_pre) != 0  -> empty
  //
  // With analogue argumentation for (4b):
  //
  //   abs(C_pre) >= aw  AND  C_invar % aw == 0           -> trivial
  //   abs(C_pre) >= aw  AND  C_invar % aw != 0           -> empty
  //   abs(C_pre) <  aw  AND  C_invar % abs(C_pre) == 0   -> constrained
  //   abs(C_pre) <  aw  AND  C_invar % abs(C_pre) != 0   -> empty
  //
  // With analogue argumentation for (4c):
  //
  //   abs(C_pre) >= aw  AND  C_init  % aw == 0           -> trivial
  //   abs(C_pre) >= aw  AND  C_init  % aw != 0           -> empty
  //   abs(C_pre) <  aw  AND  C_init  % abs(C_pre) == 0   -> constrained
  //   abs(C_pre) <  aw  AND  C_init  % abs(C_pre) != 0   -> empty
  //
  // Out of these states follows the state for the solution of pre_iter:
  //
  //   Trivial:     If (4a, b, c) are all trivial.
  //   Empty:       If any of (4a, b, c) is empty, because then we cannot guarantee a solution
  //                for pre_iter, for all possible invar and init values.
  //   Constrained: Else. Incidentally, (4a, b, c) are all constrained themselves, as we argue below.

  const EQ4 eq4(C_const, C_invar, C_init, C_pre, _aw);
  const EQ4::State eq4a_state = eq4.eq4a_state();
  const EQ4::State eq4b_state = eq4.eq4b_state();
  const EQ4::State eq4c_state = eq4.eq4c_state();

#ifdef ASSERT
  if (is_trace()) {
    eq4.trace();
  }
#endif

  // If (4a, b, c) are all trivial, then also the solution for pre_iter is trivial:
  if (eq4a_state == EQ4::State::TRIVIAL &&
      eq4b_state == EQ4::State::TRIVIAL &&
      eq4c_state == EQ4::State::TRIVIAL) {
    return new TrivialAlignmentSolution();
  }

  // If any of (4a, b, c) is empty, then we also cannot guarantee a solution for pre_iter, for
  // any init and invar, hence the solution for pre_iter is empty:
  if (eq4a_state == EQ4::State::EMPTY ||
      eq4b_state == EQ4::State::EMPTY ||
      eq4c_state == EQ4::State::EMPTY) {
    return new EmptyAlignmentSolution("EQ(4a, b, c) not all non-empty: cannot align const, invar and init terms individually");
  }

  // If abs(C_pre) >= aw, then the solutions to (4a, b, c) are all either trivial or empty, and
  // hence we would have found the solution to pre_iter above as either trivial or empty. Thus
  // we now know that:
  //
  //   abs(C_pre) < aw
  //
  assert(abs(C_pre) < _aw, "implied by constrained case");

  // And since abs(C_pre) < aw, the solutions of (4a, b, c) can now only be constrained or empty.
  // But since we already handled the empty case, the solutions are now all constrained.
  assert(eq4a_state == EQ4::State::CONSTRAINED &&
         eq4a_state == EQ4::State::CONSTRAINED &&
         eq4a_state == EQ4::State::CONSTRAINED, "all must be constrained now");

  // And since they are all constrained, we must have:
  //
  //   C_const % abs(C_pre) = 0                                                  (5a)
  //   C_invar % abs(C_pre) = 0                                                  (5b)
  //   C_init  % abs(C_pre) = 0                                                  (5c)
  //
  assert(AlignmentSolution::mod(C_const, abs(C_pre)) == 0, "EQ(5a): C_const must be alignable");
  assert(AlignmentSolution::mod(C_invar, abs(C_pre)) == 0, "EQ(5b): C_invar must be alignable");
  assert(AlignmentSolution::mod(C_init,  abs(C_pre)) == 0, "EQ(5c): C_init  must be alignable");

  // With (5a, b, c), we know that there are integers X, Y, Z:
  //
  //   C_const = X * abs(C_pre)   ==>   X = C_const / abs(C_pre)                 (6a)
  //   C_invar = Y * abs(C_pre)   ==>   Y = C_invar / abs(C_pre)                 (6b)
  //   C_init  = Z * abs(C_pre)   ==>   Z = C_init  / abs(C_pre)                 (6c)
  //
  // Further, we define:
  //
  //   sign(C_pre) = C_pre / abs(C_pre) = (C_pre > 0) ? 1 : -1,                  (7)
  //
  // We know that abs(C_pre) as well as aw are powers of 2, and since (5) we can define integer q:
  //
  //   q = aw / abs(C_pre)                                                       (8)
  //
  const int q = _aw / abs(C_pre);

  assert(q >= 2, "implied by constrained solution");

  // We now know that all terms in (4a, b, c) are divisible by abs(C_pre):
  //
  //   (C_const                    / abs(C_pre) + C_pre * pre_iter_C_const /  abs(C_pre)) % (aw / abs(C_pre)) =
  //   (X * abs(C_pre)             / abs(C_pre) + C_pre * pre_iter_C_const /  abs(C_pre)) % (aw / abs(C_pre)) =
  //   (X                                       +         pre_iter_C_const * sign(C_pre)) % q                 = 0  (9a)
  //
  //   -> pre_iter_C_const * sign(C_pre) = mx1 * q -               X
  //   -> pre_iter_C_const               = mx2 * q - sign(C_pre) * X                                               (10a)
  //      (for any integers mx1, mx2)
  //
  //   (C_invar        * var_invar / abs(C_pre) + C_pre * pre_iter_C_invar /  abs(C_pre)) % (aw / abs(C_pre)) =
  //   (Y * abs(C_pre) * var_invar / abs(C_pre) + C_pre * pre_iter_C_invar /  abs(C_pre)) % (aw / abs(C_pre)) =
  //   (Y              * var_invar              +         pre_iter_C_invar * sign(C_pre)) % q                 = 0  (9b)
  //
  //   -> pre_iter_C_invar * sign(C_pre) = my1 * q -               Y * var_invar
  //   -> pre_iter_C_invar               = my2 * q - sign(C_pre) * Y * var_invar                                   (10b)
  //      (for any integers my1, my2)
  //
  //   (C_init          * var_init  / abs(C_pre) + C_pre * pre_iter_C_init /  abs(C_pre)) % (aw / abs(C_pre)) =
  //   (Z * abs(C_pre)  * var_init  / abs(C_pre) + C_pre * pre_iter_C_init /  abs(C_pre)) % (aw / abs(C_pre)) =
  //   (Z * var_init                             +         pre_iter_C_init * sign(C_pre)) % q                 = 0  (9c)
  //
  //   -> pre_iter_C_init  * sign(C_pre) = mz1 * q -               Z * var_init
  //   -> pre_iter_C_init                = mz2 * q - sign(C_pre) * Z * var_init                                    (10c)
  //      (for any integers mz1, mz2)
  //
  //
  // Having solved the equations using the division, we can re-substitute X, Y, and Z, and apply (FAC_INVAR) as
  // well as (FAC_INIT). We use the fact that sign(x) == 1 / sign(x) and sign(x) * abs(x) == x:
  //
  //   pre_iter_C_const = mx2 * q - sign(C_pre) * X
  //                    = mx2 * q - sign(C_pre) * C_const             / abs(C_pre)
  //                    = mx2 * q - C_const / C_pre
  //                    = mx2 * q - C_const / (iv_scale * pre_stride)                               (11a)
  //
  // If there is an invariant:
  //
  //   pre_iter_C_invar = my2 * q - sign(C_pre) * Y       * var_invar
  //                    = my2 * q - sign(C_pre) * C_invar * var_invar / abs(C_pre)
  //                    = my2 * q - sign(C_pre) * invar               / abs(C_pre)
  //                    = my2 * q - invar / C_pre
  //                    = my2 * q - invar / (iv_scale * pre_stride)                                 (11b, with invar)
  //
  // If there is no invariant (i.e. C_invar = 0 ==> Y = 0):
  //
  //   pre_iter_C_invar = my2 * q                                                                   (11b, no invar)
  //
  // If init is variable (i.e. C_init = iv_scale, init = var_init):
  //
  //   pre_iter_C_init  = mz2 * q - sign(C_pre) * Z          * var_init
  //                    = mz2 * q - sign(C_pre) * C_init     * var_init  / abs(C_pre)
  //                    = mz2 * q - sign(C_pre) * iv_scale   * init      / abs(C_pre)
  //                    = mz2 * q - iv_scale * init / C_pre
  //                    = mz2 * q - iv_scale * init / (iv_scale * pre_stride)
  //                    = mz2 * q - init / pre_stride                                               (11c, variable init)
  //
  // If init is constant (i.e. C_init = 0 ==> Z = 0):
  //
  //   pre_iter_C_init  = mz2 * q                                                                   (11c, constant init)
  //
  // Note, that the solutions found by (11a, b, c) are all periodic with periodicity q. We combine them,
  // with m = mx2 + my2 + mz2:
  //
  //   pre_iter =   pre_iter_C_const + pre_iter_C_invar + pre_iter_C_init
  //            =   mx2 * q  - C_const / (iv_scale * pre_stride)
  //              + my2 * q [- invar / (iv_scale * pre_stride) ]
  //              + mz2 * q [- init / pre_stride               ]
  //
  //            =   m * q                                 (periodic part)
  //              - C_const / (iv_scale * pre_stride)        (align constant term)
  //             [- invar / (iv_scale * pre_stride)   ]      (align invariant term, if present)
  //             [- init / pre_stride                 ]      (align variable init term, if present)    (12)
  //
  // We can further simplify this solution by introducing integer 0 <= r < q:
  //
  //   r = (-C_const / (iv_scale * pre_stride)) % q                                                    (13)
  //
  const int r = AlignmentSolution::mod(-C_const / (iv_scale() * _pre_stride), q);
  //
  //   pre_iter = m * q + r
  //                   [- invar / (iv_scale * pre_stride)  ]
  //                   [- init / pre_stride                ]                                           (14)
  //
  // We thus get a solution that can be stated in terms of:
  //
  //   q (periodicity), r (constant alignment), invar, iv_scale, pre_stride, init
  //
  // However, pre_stride and init are shared by all mem_ref in the loop, hence we do not need to provide
  // them in the solution description.

  DEBUG_ONLY( trace_constrained_solution(C_const, C_invar, C_init, C_pre, q, r); )

  return new ConstrainedAlignmentSolution(_mem_ref, q, r, _vpointer /* holds invar and iv_scale */);

  // APPENDIX:
  // We can now verify the success of the solution given by (12):
  //
  //   adr % aw =
  //
  //   -> Simple form
  //   (base + invar + iv_scale * iv + con) % aw =
  //
  //   -> Expand iv
  //   (base + con + invar + iv_scale * (init + pre_stride * pre_iter + main_stride * main_iter)) % aw =
  //
  //   -> Reshape
  //   (base + con + invar
  //         + iv_scale * init
  //         + iv_scale * pre_stride * pre_iter
  //         + iv_scale * main_stride * main_iter)) % aw =
  //
  //   -> apply (BASE_ALIGNED): base % aw = 0
  //   -> main-loop iterations aligned (2): C_main % aw = (iv_scale * main_stride) % aw = 0
  //   (con + invar + iv_scale * init + iv_scale * pre_stride * pre_iter) % aw =
  //
  //   -> apply (12)
  //   (con + invar + iv_scale * init
  //        + iv_scale * pre_stride * (m * q - C_const / (iv_scale * pre_stride)
  //                                        [- invar / (iv_scale * pre_stride) ]
  //                                        [- init / pre_stride               ]
  //                                  )
  //   ) % aw =
  //
  //   -> expand C_const = con [+ init * iv_scale]  (if init const)
  //   (con + invar + iv_scale * init
  //        + iv_scale * pre_stride * (m * q - con / (iv_scale * pre_stride)
  //                                        [- init / pre_stride               ]          (if init constant)
  //                                        [- invar / (iv_scale * pre_stride) ]          (if invar present)
  //                                        [- init / pre_stride               ]          (if init variable)
  //                                  )
  //   ) % aw =
  //
  //   -> assuming invar = 0 if it is not present
  //   -> merge the two init terms (variable or constant)
  //   -> apply (8): q = aw / (abs(C_pre)) = aw / abs(iv_scale * pre_stride)
  //   -> and hence: (iv_scale * pre_stride * q) % aw = 0
  //   -> all terms are canceled out
  //   (con + invar + iv_scale * init
  //        + iv_scale * pre_stride * m * q                              -> aw aligned
  //        - iv_scale * pre_stride * con   / (iv_scale * pre_stride)    -> = con
  //        - iv_scale * pre_stride * init  / pre_stride                 -> = iv_scale * init
  //        - iv_scale * pre_stride * invar / (iv_scale * pre_stride)    -> = invar
  //   ) % aw = 0
  //
  // The solution given by (12) does indeed guarantee alignment.
}

#ifdef ASSERT
void AlignmentSolver::trace_start_solve() const {
  if (is_trace()) {
    tty->print(" vector mem_ref:");
    _mem_ref->dump();
    tty->print("  VPointer: ");
    _vpointer.print_on(tty);
    tty->print_cr("  vector_width = %d", _vector_width);
    tty->print_cr("  aw = alignment_width = min(vector_width(%d), ObjectAlignmentInBytes(%d)) = %d",
                  _vector_width, ObjectAlignmentInBytes, _aw);

    if (!_init_node->is_ConI()) {
      tty->print("  init:");
      _init_node->dump();
    }

    tty->print_cr("  invar = SUM(invar_summands), invar_summands:");
    int invar_count = 0;
    _vpointer.for_each_invar_summand([&] (const MemPointerSummand& s) {
      tty->print("   ");
      s.print_on(tty);
      tty->print(" -> ");
      s.variable()->dump();
      invar_count++;
    });
    if (invar_count == 0) {
      tty->print_cr("   No invar_summands.");
    }

    const jint invar_factor = _vpointer.compute_invar_factor();
    tty->print_cr("  invar_factor = %d", invar_factor);

    // iv = init + pre_iter * pre_stride + main_iter * main_stride
    tty->print("  iv = init");
    if (_init_node->is_ConI()) {
      tty->print("(%4d)", _init_node->as_ConI()->get_int());
    } else {
      tty->print("[%4d]", _init_node->_idx);
    }
    tty->print_cr(" + pre_iter * pre_stride(%d) + main_iter * main_stride(%d)",
                  _pre_stride, _main_stride);
    // adr = base + con + invar + iv_scale * iv
    tty->print("  adr = base[%d]", base().object_or_native()->_idx);
    tty->print_cr(" + invar + iv_scale(%d) * iv + con(%d)", iv_scale(), _vpointer.con());
  }
}

void AlignmentSolver::trace_reshaped_form(const int C_const,
                                          const int C_const_init,
                                          const int C_invar,
                                          const int C_init,
                                          const int C_pre,
                                          const int C_main) const
{
  if (is_trace()) {
    tty->print("      = base[%d] + ", base().object_or_native()->_idx);
    tty->print_cr("C_const(%d) + C_invar(%d) * var_invar + C_init(%d) * var_init + C_pre(%d) * pre_iter + C_main(%d) * main_iter",
                  C_const, C_invar, C_init,  C_pre, C_main);
    if (_init_node->is_ConI()) {
      tty->print_cr("  init is constant:");
      tty->print_cr("    C_const_init = %d", C_const_init);
      tty->print_cr("    C_init = %d", C_init);
    } else {
      tty->print_cr("  init is variable:");
      tty->print_cr("    C_const_init = %d", C_const_init);
      tty->print_cr("    C_init = abs(iv_scale)= %d", C_init);
    }
    if (C_invar != 0) {
      tty->print_cr("  invariant present:");
      tty->print_cr("    C_invar = invar_factor = %d", C_invar);
    } else {
      tty->print_cr("  no invariant:");
      tty->print_cr("    C_invar = %d", C_invar);
    }
    tty->print_cr("  C_const = con(%d) + iv_scale(%d) * C_const_init(%d) = %d",
                  _vpointer.con(), iv_scale(), C_const_init, C_const);
    tty->print_cr("  C_pre   = iv_scale(%d) * pre_stride(%d) = %d",
                  iv_scale(), _pre_stride, C_pre);
    tty->print_cr("  C_main  = iv_scale(%d) * main_stride(%d) = %d",
                  iv_scale(), _main_stride, C_main);
  }
}

void AlignmentSolver::trace_main_iteration_alignment(const int C_const,
                                                     const int C_invar,
                                                     const int C_init,
                                                     const int C_pre,
                                                     const int C_main,
                                                     const int C_main_mod_aw) const
{
  if (is_trace()) {
    tty->print("  EQ(1 ): (C_const(%d) + C_invar(%d) * var_invar + C_init(%d) * var_init",
                  C_const, C_invar, C_init);
    tty->print(" + C_pre(%d) * pre_iter + C_main(%d) * main_iter) %% aw(%d) = 0",
                  C_pre, C_main, _aw);
    tty->print_cr(" (given base aligned -> align rest)");
    tty->print("  EQ(2 ): C_main(%d) %% aw(%d) = %d = 0",
               C_main, _aw, C_main_mod_aw);
    tty->print_cr(" (alignment across iterations)");
  }
}

void AlignmentSolver::EQ4::trace() const {
  tty->print_cr("  EQ(4a): (C_const(%3d)             + C_pre(%d) * pre_iter_C_const) %% aw(%d) = 0  (align const term individually)",
                _C_const, _C_pre, _aw);
  tty->print_cr("          -> %s", state_to_str(eq4a_state()));

  tty->print_cr("  EQ(4b): (C_invar(%3d) * var_invar + C_pre(%d) * pre_iter_C_invar) %% aw(%d) = 0  (align invar term individually)",
                _C_invar, _C_pre, _aw);
  tty->print_cr("          -> %s", state_to_str(eq4b_state()));

  tty->print_cr("  EQ(4c): (C_init( %3d) * var_init  + C_pre(%d) * pre_iter_C_init ) %% aw(%d) = 0  (align init term individually)",
                _C_init, _C_pre, _aw);
  tty->print_cr("          -> %s", state_to_str(eq4c_state()));
}

void AlignmentSolver::trace_constrained_solution(const int C_const,
                                                 const int C_invar,
                                                 const int C_init,
                                                 const int C_pre,
                                                 const int q,
                                                 const int r) const
{
  if (is_trace()) {
    tty->print_cr("  EQ(4a, b, c) all constrained, hence:");
    tty->print_cr("  EQ(5a): C_const(%3d) %% abs(C_pre(%d)) = 0", C_const, C_pre);
    tty->print_cr("  EQ(5b): C_invar(%3d) %% abs(C_pre(%d)) = 0", C_invar, C_pre);
    tty->print_cr("  EQ(5c): C_init( %3d) %% abs(C_pre(%d)) = 0", C_init,  C_pre);

    tty->print_cr("  All terms in EQ(4a, b, c) are divisible by abs(C_pre(%d)).", C_pre);
    const int X    = C_const / abs(C_pre);
    const int Y    = C_invar / abs(C_pre);
    const int Z    = C_init  / abs(C_pre);
    const int sign = (C_pre > 0) ? 1 : -1;
    tty->print_cr("  X = C_const(%3d) / abs(C_pre(%d)) = %d       (6a)", C_const, C_pre, X);
    tty->print_cr("  Y = C_invar(%3d) / abs(C_pre(%d)) = %d       (6b)", C_invar, C_pre, Y);
    tty->print_cr("  Z = C_init( %3d) / abs(C_pre(%d)) = %d       (6c)", C_init , C_pre, Z);
    tty->print_cr("  q = aw(     %3d) / abs(C_pre(%d)) = %d       (8)",  _aw,     C_pre, q);
    tty->print_cr("  sign(C_pre) = (C_pre(%d) > 0) ? 1 : -1 = %d  (7)",  C_pre,   sign);

    tty->print_cr("  EQ(9a): (X(%3d)             + pre_iter_C_const * sign(C_pre)) %% q(%d) = 0", X, q);
    tty->print_cr("  EQ(9b): (Y(%3d) * var_invar + pre_iter_C_invar * sign(C_pre)) %% q(%d) = 0", Y, q);
    tty->print_cr("  EQ(9c): (Z(%3d) * var_init  + pre_iter_C_init  * sign(C_pre)) %% q(%d) = 0", Z, q);

    tty->print_cr("  EQ(10a): pre_iter_C_const = mx2 * q(%d) - sign(C_pre) * X(%d)",             q, X);
    tty->print_cr("  EQ(10b): pre_iter_C_invar = my2 * q(%d) - sign(C_pre) * Y(%d) * var_invar", q, Y);
    tty->print_cr("  EQ(10c): pre_iter_C_init  = mz2 * q(%d) - sign(C_pre) * Z(%d) * var_init ", q, Z);

    tty->print_cr("  r = (-C_const(%d) / (iv_scale(%d) * pre_stride(%d)) %% q(%d) = %d",
                  C_const, iv_scale(), _pre_stride, q, r);

    tty->print_cr("  EQ(14):  pre_iter = m * q(%3d) - r(%d)", q, r);
    if (C_invar != 0) {
      tty->print_cr("                                 - invar / (iv_scale(%d) * pre_stride(%d))",
                    iv_scale(), _pre_stride);
    }
    if (!_init_node->is_ConI()) {
      tty->print_cr("                                 - init / pre_stride(%d)",
                    _pre_stride);
    }
  }
}
#endif
