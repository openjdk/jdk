/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/mulnode.hpp"
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
        _auto_vectorization_parse_predicate_proj->dump_bfs(5,0,"");
      } else if (_multiversioning_fast_proj != nullptr) {
        tty->print_cr("  multiversioning_fast_proj: speculate and multiversion");
        _multiversioning_fast_proj->dump_bfs(5,0,"");
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

  _memory_slices.find_memory_slices();

  // If there is no memory slice detected, it means there is no store.
  // If there is no reduction and no store, then we give up, because
  // vectorization is not possible anyway (given current limitations).
  if (!_reductions.is_marked_reduction_loop() &&
      _memory_slices.heads().is_empty()) {
    return VStatus::make_failure(VLoopAnalyzer::FAILURE_NO_REDUCTION_OR_STORE);
  }

  VStatus body_status = _body.construct();
  if (!body_status.is_success()) {
    return body_status;
  }

  _types.compute_vector_element_type();

  _vpointers.compute_vpointers();

  _dependency_graph.construct();

  return VStatus::make_success();
}

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
    ::new (&_vpointers[pointers_idx]) VPointer(mem, _vloop);
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
void VLoopDependencyGraph::construct() {
  const GrowableArray<PhiNode*>& mem_slice_heads = _memory_slices.heads();
  const GrowableArray<MemNode*>& mem_slice_tails = _memory_slices.tails();

  ResourceMark rm;
  GrowableArray<MemNode*> slice_nodes;
  GrowableArray<int> memory_pred_edges;

  // For each memory slice, create the memory subgraph
  for (int i = 0; i < mem_slice_heads.length(); i++) {
    PhiNode* head = mem_slice_heads.at(i);
    MemNode* tail = mem_slice_tails.at(i);

    _memory_slices.get_slice_in_reverse_order(head, tail, slice_nodes);

    // In forward order (reverse of reverse), visit all memory nodes in the slice.
    for (int j = slice_nodes.length() - 1; j >= 0 ; j--) {
      MemNode* n1 = slice_nodes.at(j);
      memory_pred_edges.clear();

      const VPointer& p1 = _vpointers.vpointer(n1);
      // For all memory nodes before it, check if we need to add a memory edge.
      for (int k = slice_nodes.length() - 1; k > j; k--) {
        MemNode* n2 = slice_nodes.at(k);

        // Ignore Load-Load dependencies:
        if (n1->is_Load() && n2->is_Load()) { continue; }

        const VPointer& p2 = _vpointers.vpointer(n2);
        if (!p1.never_overlaps_with(p2)) {
          // Possibly overlapping memory
          memory_pred_edges.append(_body.bb_idx(n2));
        }
      }
      if (memory_pred_edges.is_nonempty()) {
        // Data edges are taken implicitly from the C2 graph, thus we only add
        // a dependency node if we have memory edges.
        add_node(n1, memory_pred_edges);
      }
    }
    slice_nodes.clear();
  }

  compute_depth();

  NOT_PRODUCT( if (_vloop.is_trace_dependency_graph()) { print(); } )
}

void VLoopDependencyGraph::add_node(MemNode* n, GrowableArray<int>& memory_pred_edges) {
  assert(_dependency_nodes.at_grow(_body.bb_idx(n), nullptr) == nullptr, "not yet created");
  assert(!memory_pred_edges.is_empty(), "no need to create a node without edges");
  DependencyNode* dn = new (_arena) DependencyNode(n, memory_pred_edges, _arena);
  _dependency_nodes.at_put_grow(_body.bb_idx(n), dn, nullptr);
}

int VLoopDependencyGraph::find_max_pred_depth(const Node* n) const {
  int max_pred_depth = 0;
  if (!n->is_Phi()) { // ignore backedge
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
      for (uint j = 0; j < dn->memory_pred_edges_length(); j++) {
        Node* pred = _body.body().at(dn->memory_pred_edge(j));
        tty->print("  %d %s", pred->_idx, pred->Name());
      }
      tty->print_cr("]");
    }
  }
  tty->cr();

  tty->print_cr(" Complete dependency graph:");
  for (int i = 0; i < _body.body().length(); i++) {
    Node* n = _body.body().at(i);
    tty->print("  d%02d Dependencies[%d %s:", depth(n), n->_idx, n->Name());
    for (PredsIterator it(*this, n); !it.done(); it.next()) {
      Node* pred = it.current();
      tty->print("  %d %s", pred->_idx, pred->Name());
    }
    tty->print_cr("]");
  }
}
#endif

VLoopDependencyGraph::DependencyNode::DependencyNode(MemNode* n,
                                                     GrowableArray<int>& memory_pred_edges,
                                                     Arena* arena) :
    _node(n),
    _memory_pred_edges_length(memory_pred_edges.length()),
    _memory_pred_edges(nullptr)
{
  assert(memory_pred_edges.is_nonempty(), "not empty");
  uint bytes = memory_pred_edges.length() * sizeof(int);
  _memory_pred_edges = (int*)arena->Amalloc(bytes);
  memcpy(_memory_pred_edges, memory_pred_edges.adr_at(0), bytes);
}

VLoopDependencyGraph::PredsIterator::PredsIterator(const VLoopDependencyGraph& dependency_graph,
                                                   const Node* node) :
    _dependency_graph(dependency_graph),
    _node(node),
    _dependency_node(dependency_graph.dependency_node(node)),
    _current(nullptr),
    _is_current_memory_edge(false),
    _next_pred(0),
    _end_pred(node->req()),
    _next_memory_pred(0),
    _end_memory_pred((_dependency_node != nullptr) ? _dependency_node->memory_pred_edges_length() : 0)
{
  if (_node->is_Store() || _node->is_Load()) {
    // Load: address
    // Store: address, value
    _next_pred = MemNode::Address;
  } else {
    assert(!_node->is_Mem(), "only loads and stores are expected mem nodes");
    _next_pred = 1; // skip control
  }
  next();
}

void VLoopDependencyGraph::PredsIterator::next() {
  if (_next_pred < _end_pred) {
    _current = _node->in(_next_pred++);
    _is_current_memory_edge = false;
  } else if (_next_memory_pred < _end_memory_pred) {
    int pred_bb_idx = _dependency_node->memory_pred_edge(_next_memory_pred++);
    _current = _dependency_graph._body.body().at(pred_bb_idx);
    _is_current_memory_edge = true;
  } else {
    _current = nullptr; // done
    _is_current_memory_edge = false;
  }
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
