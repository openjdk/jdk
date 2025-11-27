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

#include "opto/castnode.hpp"
#include "opto/convertnode.hpp"
#include "opto/rootnode.hpp"
#include "opto/vectorization.hpp"
#include "opto/vectornode.hpp"
#include "opto/vtransform.hpp"

void VTransformGraph::add_vtnode(VTransformNode* vtnode) {
  assert(vtnode->_idx == _vtnodes.length(), "position must match idx");
  _vtnodes.push(vtnode);
}

#define TRACE_OPTIMIZE(code)                          \
  NOT_PRODUCT(                                        \
    if (vtransform.vloop().is_trace_optimization()) { \
      code                                            \
    }                                                 \
  )

// This is similar to IGVN optimization. But we are a bit lazy, and don't care about
// notification / worklist, since the list of nodes is rather small, and we don't
// expect optimizations that trickle over the whole graph.
void VTransformGraph::optimize(VTransform& vtransform) {
  TRACE_OPTIMIZE( tty->print_cr("\nVTransformGraph::optimize"); )

  bool progress = true;
  DEBUG_ONLY(int pass_count = 0;)
  while (progress) {
    progress = false;
    assert(++pass_count < 10, "ensure we do not have endless loops");
    for (int i = 0; i < _vtnodes.length(); i++) {
      VTransformNode* vtn = _vtnodes.at(i);
      if (!vtn->is_alive()) { continue; }
      progress |= vtn->optimize(_vloop_analyzer, vtransform);

      // Nodes that have no use any more are dead.
      if (vtn->out_strong_edges() == 0 &&
          // There are some exceptions:
          // 1. Memory phi uses are not modeled, so they appear to have no use here, but must be kept alive.
          // 2. Similarly, some stores may not have their memory uses modeled, but need to be kept alive.
          // 3. Outer node with strong inputs: is a use after the loop that we must keep alive.
          !(vtn->isa_PhiScalar() != nullptr ||
            vtn->is_load_or_store_in_loop() ||
            (vtn->isa_Outer() != nullptr && vtn->has_strong_in_edge()))) {
        vtn->mark_dead();
        progress = true;
      }
    }
  }
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

  collect_nodes_without_strong_in_edges(stack);
  const int num_alive_nodes = count_alive_vtnodes();

  // We create a reverse-post-visit order. This gives us a linearization, if there are
  // no cycles. Then, we simply reverse the order, and we have a schedule.
  int rpo_idx = num_alive_nodes - 1;
  while (!stack.is_empty()) {
    VTransformNode* vtn = stack.top();
    if (!pre_visited.test_set(vtn->_idx)) {
      // Forward arc in graph (pre-visit).
    } else if (!post_visited.test(vtn->_idx)) {
      // Forward arc in graph. Check if all uses were already visited:
      //   Yes -> post-visit.
      //   No  -> we are mid-visit.
      bool all_uses_already_visited = true;

      // We only need to respect the strong edges (data edges and strong memory edges).
      // Violated weak memory edges are allowed, but require a speculative aliasing
      // runtime check, see VTransform::apply_speculative_aliasing_runtime_checks.
      for (uint i = 0; i < vtn->out_strong_edges(); i++) {
        VTransformNode* use = vtn->out_strong_edge(i);

        // Skip dead nodes
        if (!use->is_alive()) { continue; }

        // Skip backedges.
        if ((use->is_loop_head_phi() || use->isa_CountedLoop() != nullptr) && use->in_req(2) == vtn) {
          continue;
        }

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
  if (_trace._info) {
    print_schedule();
  }
#endif

  assert(rpo_idx == -1, "used up all rpo_idx, rpo_idx=%d", rpo_idx);
  return true;
}

// Push all "root" nodes, i.e. those that have no strong input edges (data edges and strong memory edges):
void VTransformGraph::collect_nodes_without_strong_in_edges(GrowableArray<VTransformNode*>& stack) const {
  for (int i = 0; i < _vtnodes.length(); i++) {
    VTransformNode* vtn = _vtnodes.at(i);
    if (!vtn->is_alive()) { continue; }
    if (!vtn->has_strong_in_edge()) {
      stack.push(vtn);
    }
    // If an Outer node has both inputs and outputs, we will most likely have cycles in the final graph.
    // This is not a correctness problem, but it just will prevent vectorization. If this ever happens
    // try to find a way to avoid the cycle somehow.
    assert(vtn->isa_Outer() == nullptr || (vtn->has_strong_in_edge() != (vtn->out_strong_edges() > 0)),
           "Outer nodes should either be inputs or outputs, but not both, otherwise we may get cycles");
  }
}

int VTransformGraph::count_alive_vtnodes() const {
  int count = 0;
  for (int i = 0; i < _vtnodes.length(); i++) {
    VTransformNode* vtn = _vtnodes.at(i);
    if (vtn->is_alive()) { count++; }
  }
  return count;
}

// Find all nodes that in the loop, in a 2-phase process:
// - First, find all nodes that are not before the loop:
//   - loop-phis
//   - loads and stores that are in the loop
//   - and all their transitive uses.
// - Second, we find all nodes that are not after the loop:
//   - backedges
//   - loads and stores that are in the loop
//   - and all their transitive uses.
//
// in_loop: vtn->_idx -> bool
void VTransformGraph::mark_vtnodes_in_loop(VectorSet& in_loop) const {
  assert(is_scheduled(), "must already be scheduled");

  // Phase 1: find all nodes that are not before the loop.
  VectorSet is_not_before_loop;
  for (int i = 0; i < _schedule.length(); i++) {
    VTransformNode* vtn = _schedule.at(i);
    // Is vtn a loop-phi?
    if (vtn->is_loop_head_phi() ||
        vtn->is_load_or_store_in_loop()) {
      is_not_before_loop.set(vtn->_idx);
      continue;
    }
    // Or one of its transitive uses?
    for (uint j = 0; j < vtn->req(); j++) {
      VTransformNode* def = vtn->in_req(j);
      if (def != nullptr && is_not_before_loop.test(def->_idx)) {
        is_not_before_loop.set(vtn->_idx);
        break;
      }
    }
  }

  // Phase 2: find all nodes that are not after the loop.
  for (int i = _schedule.length()-1; i >= 0; i--) {
    VTransformNode* vtn = _schedule.at(i);
    if (!is_not_before_loop.test(vtn->_idx)) { continue; }
    // Is load or store?
    if (vtn->is_load_or_store_in_loop()) {
        in_loop.set(vtn->_idx);
        continue;
    }
    for (uint i = 0; i < vtn->out_strong_edges(); i++) {
      VTransformNode* use = vtn->out_strong_edge(i);
      // Or is vtn a backedge or one of its transitive defs?
      if (in_loop.test(use->_idx) || use->is_loop_head_phi()) {
        in_loop.set(vtn->_idx);
        break;
      }
    }
  }
}

float VTransformGraph::cost_for_vector_loop() const {
  assert(is_scheduled(), "must already be scheduled");
#ifndef PRODUCT
  if (_vloop.is_trace_cost()) {
    tty->print_cr("\nVTransformGraph::cost_for_vector_loop:");
  }
#endif

  // We only want to count the cost of nodes that are in the loop.
  // This is especially important for cases where we were able to move
  // some nodes outside the loop during VTransform::optimize, e.g.:
  // VTransformReductionVectorNode::optimize_move_non_strict_order_reductions_out_of_loop
  ResourceMark rm;
  VectorSet in_loop; // vtn->_idx -> bool
  mark_vtnodes_in_loop(in_loop);

  float sum = 0;
  for (int i = 0; i < _schedule.length(); i++) {
    VTransformNode* vtn = _schedule.at(i);
    if (!in_loop.test(vtn->_idx)) { continue; }
    float c = vtn->cost(_vloop_analyzer);
    sum += c;
#ifndef PRODUCT
    if (c != 0 && _vloop.is_trace_cost_verbose()) {
      tty->print("  -> cost = %.2f for ", c);
      vtn->print();
    }
#endif
  }

#ifndef PRODUCT
  if (_vloop.is_trace_cost()) {
    tty->print_cr("  total_cost = %.2f", sum);
  }
#endif
  return sum;
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

void VTransform::apply_speculative_alignment_runtime_checks() {
  if (VLoop::vectors_should_be_aligned()) {
#ifdef ASSERT
    if (_trace._align_vector || _trace._speculative_runtime_checks) {
      tty->print_cr("\nVTransform::apply_speculative_alignment_runtime_checks: native memory alignment");
    }
#endif

    const GrowableArray<VTransformNode*>& vtnodes = _graph.vtnodes();
    for (int i = 0; i < vtnodes.length(); i++) {
      VTransformMemVectorNode* vtn = vtnodes.at(i)->isa_MemVector();
      if (vtn == nullptr) { continue; }
      const VPointer& vp = vtn->vpointer();
      if (vp.mem_pointer().base().is_object()) { continue; }
      assert(vp.mem_pointer().base().is_native(), "VPointer base must be object or native");

      // We have a native memory reference. Build a runtime check for it.
      // See: AlignmentSolver::solve
      // In a future RFE we may be able to speculate on invar alignment as
      // well, and allow vectorization of more cases.
      add_speculative_alignment_check(vp.mem_pointer().base().native(), ObjectAlignmentInBytes);
    }
  }
}

#define TRACE_SPECULATIVE_ALIGNMENT_CHECK(node) {                     \
  DEBUG_ONLY(                                                         \
    if (_trace._align_vector || _trace._speculative_runtime_checks) { \
      tty->print("  " #node ": ");                                    \
      node->dump();                                                   \
    }                                                                 \
  )                                                                   \
}                                                                     \

// Check: (node % alignment) == 0.
void VTransform::add_speculative_alignment_check(Node* node, juint alignment) {
  TRACE_SPECULATIVE_ALIGNMENT_CHECK(node);
  Node* ctrl = phase()->get_ctrl(node);

  // Cast adr/long -> int
  if (node->bottom_type()->basic_type() == T_ADDRESS) {
    // adr -> int/long
    node = new CastP2XNode(nullptr, node);
    phase()->register_new_node(node, ctrl);
    TRACE_SPECULATIVE_ALIGNMENT_CHECK(node);
  }
  if (node->bottom_type()->basic_type() == T_LONG) {
    // long -> int
    node  = new ConvL2INode(node);
    phase()->register_new_node(node, ctrl);
    TRACE_SPECULATIVE_ALIGNMENT_CHECK(node);
  }

  Node* mask_alignment = phase()->intcon(alignment-1);
  Node* base_alignment = new AndINode(node, mask_alignment);
  phase()->register_new_node(base_alignment, ctrl);
  TRACE_SPECULATIVE_ALIGNMENT_CHECK(mask_alignment);
  TRACE_SPECULATIVE_ALIGNMENT_CHECK(base_alignment);

  Node* zero = phase()->intcon(0);
  Node* cmp_alignment = CmpNode::make(base_alignment, zero, T_INT, false);
  BoolNode* bol_alignment = new BoolNode(cmp_alignment, BoolTest::eq);
  phase()->register_new_node(cmp_alignment, ctrl);
  phase()->register_new_node(bol_alignment, ctrl);
  TRACE_SPECULATIVE_ALIGNMENT_CHECK(cmp_alignment);
  TRACE_SPECULATIVE_ALIGNMENT_CHECK(bol_alignment);

  add_speculative_check([&] (Node* ctrl) { return bol_alignment; });
}

class VPointerWeakAliasingPair : public StackObj {
private:
  // Using references instead of pointers would be preferrable, but GrowableArray
  // requires a default constructor, and we do not have a default constructor for
  // VPointer.
  const VPointer* _vp1 = nullptr;
  const VPointer* _vp2 = nullptr;

  VPointerWeakAliasingPair(const VPointer& vp1, const VPointer& vp2) : _vp1(&vp1), _vp2(&vp2) {
    assert(vp1.is_valid(), "sanity");
    assert(vp2.is_valid(), "sanity");
    assert(!vp1.never_overlaps_with(vp2), "otherwise no aliasing");
    assert(!vp1.always_overlaps_with(vp2), "otherwise must be strong");
    assert(VPointer::cmp_summands_and_con(vp1, vp2) <= 0, "must be sorted");
  }

public:
  // Default constructor to make GrowableArray happy.
  VPointerWeakAliasingPair() : _vp1(nullptr), _vp2(nullptr) {}

  static VPointerWeakAliasingPair make(const VPointer& vp1, const VPointer& vp2) {
    if (VPointer::cmp_summands_and_con(vp1, vp2) <= 0) {
      return VPointerWeakAliasingPair(vp1, vp2);
    } else {
      return VPointerWeakAliasingPair(vp2, vp1);
    }
  }

  const VPointer& vp1() const { return *_vp1; }
  const VPointer& vp2() const { return *_vp2; }

  // Sort by summands, so that pairs with same summands (summand1, summands2) are adjacent.
  static int cmp_for_sort(VPointerWeakAliasingPair* pair1, VPointerWeakAliasingPair* pair2) {
    int cmp_summands1 = VPointer::cmp_summands(pair1->vp1(), pair2->vp1());
    if (cmp_summands1 != 0) { return cmp_summands1; }
    return VPointer::cmp_summands(pair1->vp2(), pair2->vp2());
  }
};

void VTransform::apply_speculative_aliasing_runtime_checks() {

  if (_vloop.use_speculative_aliasing_checks()) {

#ifdef ASSERT
    if (_trace._speculative_aliasing_analysis || _trace._speculative_runtime_checks) {
      tty->print_cr("\nVTransform::apply_speculative_aliasing_runtime_checks: speculative aliasing analysis runtime checks");
    }
#endif

    // It would be nice to add a ResourceMark here. But it would collide with resource allocation
    // in PhaseIdealLoop::set_idom for _idom and _dom_depth. See also JDK-8337015.
    VectorSet visited;
    GrowableArray<VPointerWeakAliasingPair> weak_aliasing_pairs;

    const GrowableArray<VTransformNode*>& schedule = _graph.get_schedule();
    for (int i = 0; i < schedule.length(); i++) {
      VTransformNode* vtn = schedule.at(i);
      for (uint i = 0; i < vtn->out_weak_edges(); i++) {
        VTransformNode* use = vtn->out_weak_edge(i);
        if (visited.test(use->_idx)) {
          // The use node was already visited, i.e. is higher up in the schedule.
          // The "out" edge thus points backward, i.e. it is violated.
          const VPointer& vp1 = vtn->vpointer();
          const VPointer& vp2 = use->vpointer();
#ifdef ASSERT
          if (_trace._speculative_aliasing_analysis || _trace._speculative_runtime_checks) {
            tty->print_cr("\nViolated Weak Edge:");
            vtn->print();
            vp1.print_on(tty);
            use->print();
            vp2.print_on(tty);
          }
#endif

          // We could generate checks for the pair (vp1, vp2) directly. But in
          // some graphs, this generates quadratically many checks. Example:
          //
          //   set1: a[i+0] a[i+1] a[i+2] a[i+3]
          //   set2: b[i+0] b[i+1] b[i+2] b[i+3]
          //
          // We may have a weak memory edge between every memory access from
          // set1 to every memory access from set2. In this example, this would
          // be 4 * 4 = 16 checks. But instead, we can create a union VPointer
          // for set1 and set2 each, and only create a single check.
          //
          //   set1: a[i+0, size = 4]
          //   set1: b[i+0, size = 4]
          //
          // For this, we add all pairs to an array, and process it below.
          weak_aliasing_pairs.push(VPointerWeakAliasingPair::make(vp1, vp2));
        }
      }
      visited.set(vtn->_idx);
    }

    // Sort so that all pairs with the same summands (summands1, summands2)
    // are consecutive, i.e. in the same group. This allows us to do a linear
    // walk over all pairs of a group and create the union VPointers.
    weak_aliasing_pairs.sort(VPointerWeakAliasingPair::cmp_for_sort);

    int group_start = 0;
    while (group_start < weak_aliasing_pairs.length()) {
      // New group: pick the first pair as the reference.
      const VPointer* vp1 = &weak_aliasing_pairs.at(group_start).vp1();
      const VPointer* vp2 = &weak_aliasing_pairs.at(group_start).vp2();
      jint size1 = vp1->size();
      jint size2 = vp2->size();
      int group_end = group_start + 1;
      while (group_end < weak_aliasing_pairs.length()) {
        const VPointer* vp1_next = &weak_aliasing_pairs.at(group_end).vp1();
        const VPointer* vp2_next = &weak_aliasing_pairs.at(group_end).vp2();
        jint size1_next = vp1_next->size();
        jint size2_next = vp2_next->size();

        // Different summands -> different group.
        if (VPointer::cmp_summands(*vp1, *vp1_next) != 0) { break; }
        if (VPointer::cmp_summands(*vp2, *vp2_next) != 0) { break; }

        // Pick the one with the lower con as the reference.
        if (vp1->con() > vp1_next->con()) {
          swap(vp1, vp1_next);
          swap(size1, size1_next);
        }
        if (vp2->con() > vp2_next->con()) {
          swap(vp2, vp2_next);
          swap(size2, size2_next);
        }

        // Compute the distance from vp1 to vp1_next + size, to get a size that would include vp1_next.
        NoOverflowInt new_size1 = NoOverflowInt(vp1_next->con()) + NoOverflowInt(size1_next) - NoOverflowInt(vp1->con());
        NoOverflowInt new_size2 = NoOverflowInt(vp2_next->con()) + NoOverflowInt(size2_next) - NoOverflowInt(vp2->con());
        if (new_size1.is_NaN() || new_size2.is_NaN()) { break; /* overflow -> new group */ }

        // The "next" VPointer indeed belong to the group.
        //
        // vp1:       |-------------->
        // vp1_next:            |---------------->
        // result:    |-------------------------->
        //
        // vp1:       |-------------------------->
        // vp1_next:            |------->
        // result:    |-------------------------->
        //
        size1 = MAX2(size1, new_size1.value());
        size2 = MAX2(size2, new_size2.value());
        group_end++;
      }
      // Create "union" VPointer that cover all VPointer from the group.
      const VPointer vp1_union = vp1->make_with_size(size1);
      const VPointer vp2_union = vp2->make_with_size(size2);

#ifdef ASSERT
      if (_trace._speculative_aliasing_analysis || _trace._speculative_runtime_checks) {
        tty->print_cr("\nUnion of %d weak aliasing edges:", group_end - group_start);
        vp1_union.print_on(tty);
        vp2_union.print_on(tty);
      }

      // Verification - union must contain all VPointer of the group.
      for (int i = group_start; i < group_end; i++) {
        const VPointer& vp1_i = weak_aliasing_pairs.at(i).vp1();
        const VPointer& vp2_i = weak_aliasing_pairs.at(i).vp2();
        assert(vp1_union.con() <= vp1_i.con(), "must start before");
        assert(vp2_union.con() <= vp2_i.con(), "must start before");
        assert(vp1_union.size() >= vp1_i.size(), "must end after");
        assert(vp2_union.size() >= vp2_i.size(), "must end after");
      }
#endif

      add_speculative_check([&] (Node* ctrl) {
        return vp1_union.make_speculative_aliasing_check_with(vp2_union, ctrl);
      });

      group_start = group_end;
    }
  }
}

// Runtime Checks:
//   Some required properties cannot be proven statically, and require a
//   runtime check:
//   - Alignment:
//       See VTransform::add_speculative_alignment_check
//   - Aliasing:
//       See VTransform::apply_speculative_aliasing_runtime_checks
//   There is a two staged approach for compilation:
//   - AutoVectorization Predicate:
//       See VM flag UseAutoVectorizationPredicate and documentation in predicates.hpp
//       We speculate that the checks pass, and only compile a vectorized  loop.
//       We expect the checks to pass in almost all cases, and so we only need
//       to compile and cache the vectorized loop.
//       If the predicate ever fails, we deoptimize, and eventually compile
//       without predicate. This means we will recompile with multiversioning.
//    - Multiversioning:
//       See VM Flag LoopMultiversioning and documentaiton in loopUnswitch.cpp
//       If the predicate is not available or previously failed, then we compile
//       a vectorized and a scalar loop. If the runtime check passes we take the
//       vectorized loop, else the scalar loop.
//       Multiversioning takes more compile time and code cache, but it also
//       produces fast code for when the runtime check passes (vectorized) and
//       when it fails (scalar performance).
//
// Callback:
//   In some cases, we require the ctrl just before the check iff_speculate to
//   generate the values required in the check. We pass this ctrl into the
//   callback, which is expected to produce the check, i.e. a BoolNode.
template<typename Callback>
void VTransform::add_speculative_check(Callback callback) {
  assert(_vloop.are_speculative_checks_possible(), "otherwise we cannot make speculative assumptions");
  ParsePredicateSuccessProj* parse_predicate_proj = _vloop.auto_vectorization_parse_predicate_proj();
  IfTrueNode* new_check_proj = nullptr;
  if (parse_predicate_proj != nullptr) {
    new_check_proj = phase()->create_new_if_for_predicate(parse_predicate_proj, nullptr,
                                                          Deoptimization::Reason_auto_vectorization_check,
                                                          Op_If);
  } else {
    new_check_proj = phase()->create_new_if_for_multiversion(_vloop.multiversioning_fast_proj());
  }
  Node* iff_speculate = new_check_proj->in(0);

  // Create the check, given the ctrl just before the iff.
  BoolNode* bol = callback(iff_speculate->in(0));

  igvn().replace_input_of(iff_speculate, 1, bol);
  TRACE_SPECULATIVE_ALIGNMENT_CHECK(iff_speculate);
}

// Helper-class for VTransformGraph::has_store_to_load_forwarding_failure.
// It wraps a VPointer. The VPointer has an iv_offset applied, which
// simulates a virtual unrolling. They represent the memory region:
//   [adr, adr + size)
//   adr = base + invar + iv_scale * (iv + iv_offset) + con
class VMemoryRegion : public ResourceObj {
private:
  // Note: VPointer has no default constructor, so we cannot use VMemoryRegion
  //       in-place in a GrowableArray. Hence, we make VMemoryRegion a resource
  //       allocated object, so the GrowableArray of VMemoryRegion* has a default
  //       nullptr element.
  const VPointer _vpointer;
  bool _is_load;      // load or store?
  uint _schedule_order;

public:
  VMemoryRegion(const VPointer& vpointer, bool is_load, uint schedule_order) :
    _vpointer(vpointer),
    _is_load(is_load),
    _schedule_order(schedule_order) {}

    const VPointer& vpointer() const { return _vpointer; }
    bool is_load()        const { return _is_load; }
    uint schedule_order() const { return _schedule_order; }

    static int cmp_for_sort_by_group(VMemoryRegion* r1, VMemoryRegion* r2) {
      // Sort by mem_pointer (base, invar, iv_scale), except for the con.
      return MemPointer::cmp_summands(r1->vpointer().mem_pointer(),
                                      r2->vpointer().mem_pointer());
    }

    static int cmp_for_sort(VMemoryRegion** r1, VMemoryRegion** r2) {
      int cmp_group = cmp_for_sort_by_group(*r1, *r2);
      if (cmp_group != 0) { return cmp_group; }

      // We use two comparisons, because a subtraction could underflow.
      jint con1 = (*r1)->vpointer().con();
      jint con2 = (*r2)->vpointer().con();
      if (con1 < con2) { return -1; }
      if (con1 > con2) { return  1; }
      return 0;
    }

    enum Aliasing { DIFFERENT_GROUP, BEFORE, EXACT_OVERLAP, PARTIAL_OVERLAP, AFTER };

    Aliasing aliasing(VMemoryRegion& other) {
      VMemoryRegion* p1 = this;
      VMemoryRegion* p2 = &other;
      if (cmp_for_sort_by_group(p1, p2) != 0) { return DIFFERENT_GROUP; }

      jlong con1 = p1->vpointer().con();
      jlong con2 = p2->vpointer().con();
      jlong size1 = p1->vpointer().size();
      jlong size2 = p2->vpointer().size();

      if (con1 >= con2 + size2) { return AFTER; }
      if (con2 >= con1 + size1) { return BEFORE; }
      if (con1 == con2 && size1 == size2) { return EXACT_OVERLAP; }
      return PARTIAL_OVERLAP;
    }

#ifndef PRODUCT
  void print() const {
    tty->print("VMemoryRegion[%s schedule_order(%4d), ",
               _is_load ? "load, " : "store,", _schedule_order);
    vpointer().print_on(tty, false);
    tty->print_cr("]");
  }
#endif
};

// Store-to-load-forwarding is a CPU memory optimization, where a load can directly fetch
// its value from the store-buffer, rather than from the L1 cache. This is many CPU cycles
// faster. However, this optimization comes with some restrictions, depending on the CPU.
// Generally, store-to-load-forwarding works if the load and store memory regions match
// exactly (same start and width). Generally problematic are partial overlaps - though
// some CPU's can handle even some subsets of these cases. We conservatively assume that
// all such partial overlaps lead to a store-to-load-forwarding failures, which means the
// load has to stall until the store goes from the store-buffer into the L1 cache, incurring
// a penalty of many CPU cycles.
//
// Example (with "iteration distance" 2):
//   for (int i = 10; i < SIZE; i++) {
//       aI[i] = aI[i - 2] + 1;
//   }
//
//   load_4_bytes( ptr +  -8)
//   store_4_bytes(ptr +   0)    *
//   load_4_bytes( ptr +  -4)    |
//   store_4_bytes(ptr +   4)    | *
//   load_4_bytes( ptr +   0)  <-+ |
//   store_4_bytes(ptr +   8)      |
//   load_4_bytes( ptr +   4)  <---+
//   store_4_bytes(ptr +  12)
//   ...
//
//   In the scalar loop, we can forward the stores from 2 iterations back.
//
// Assume we have 2-element vectors (2*4 = 8 bytes), with the "iteration distance" 2
// example. This gives us this machine code:
//   load_8_bytes( ptr +  -8)
//   store_8_bytes(ptr +   0) |
//   load_8_bytes( ptr +   0) v
//   store_8_bytes(ptr +   8)   |
//   load_8_bytes( ptr +   8)   v
//   store_8_bytes(ptr +  16)
//   ...
//
//   We packed 2 iterations, and the stores can perfectly forward to the loads of
//   the next 2 iterations.
//
// Example (with "iteration distance" 3):
//   for (int i = 10; i < SIZE; i++) {
//       aI[i] = aI[i - 3] + 1;
//   }
//
//   load_4_bytes( ptr + -12)
//   store_4_bytes(ptr +   0)    *
//   load_4_bytes( ptr +  -8)    |
//   store_4_bytes(ptr +   4)    |
//   load_4_bytes( ptr +  -4)    |
//   store_4_bytes(ptr +   8)    |
//   load_4_bytes( ptr +   0)  <-+
//   store_4_bytes(ptr +  12)
//   ...
//
//   In the scalar loop, we can forward the stores from 3 iterations back.
//
// Unfortunately, vectorization can introduce such store-to-load-forwarding failures.
// Assume we have 2-element vectors (2*4 = 8 bytes), with the "iteration distance" 3
// example. This gives us this machine code:
//   load_8_bytes( ptr + -12)
//   store_8_bytes(ptr +   0)  |   |
//   load_8_bytes( ptr +  -4)  x   |
//   store_8_bytes(ptr +   8)     ||
//   load_8_bytes( ptr +   4)     xx  <-- partial overlap with 2 stores
//   store_8_bytes(ptr +  16)
//   ...
//
// We see that eventually all loads are dependent on earlier stores, but the values cannot
// be forwarded because there is some partial overlap.
//
// Preferably, we would have some latency-based cost-model that accounts for such forwarding
// failures, and decide if vectorization with forwarding failures is still profitable. For
// now we go with a simpler heuristic: we simply forbid vectorization if we can PROVE that
// there will be a forwarding failure. This approach has at least 2 possible weaknesses:
//
//  (1) There may be forwarding failures in cases where we cannot prove it.
//      Example:
//        for (int i = 10; i < SIZE; i++) {
//            bI[i] = aI[i - 3] + 1;
//        }
//
//      We do not know if aI and bI refer to the same array or not. However, it is reasonable
//      to assume that if we have two different array references, that they most likely refer
//      to different arrays (i.e. no aliasing), where we would have no forwarding failures.
//  (2) There could be some loops where vectorization introduces forwarding failures, and thus
//      the latency of the loop body is high, but this does not matter because it is dominated
//      by other latency/throughput based costs in the loop body.
//
// Performance measurements with the JMH benchmark StoreToLoadForwarding.java have indicated
// that there is some iteration threshold: if the failure happens between a store and load that
// have an iteration distance below this threshold, the latency is the limiting factor, and we
// should not vectorize to avoid the latency penalty of store-to-load-forwarding failures. If
// the iteration distance is larger than this threshold, the throughput is the limiting factor,
// and we should vectorize in these cases to improve throughput.
//
bool VTransformGraph::has_store_to_load_forwarding_failure(const VLoopAnalyzer& vloop_analyzer) const {
  if (SuperWordStoreToLoadForwardingFailureDetection == 0) { return false; }

  // Collect all pointers for scalar and vector loads/stores.
  ResourceMark rm;
  // Use pointers because no default constructor for elements available.
  GrowableArray<VMemoryRegion*> memory_regions;

  // To detect store-to-load-forwarding failures at the iteration threshold or below, we
  // simulate a super-unrolling to reach SuperWordStoreToLoadForwardingFailureDetection
  // iterations at least. This is a heuristic, and we are not trying to be very precise
  // with the iteration distance. If we have already unrolled more than the iteration
  // threshold, i.e. if "SuperWordStoreToLoadForwardingFailureDetection < unrolled_count",
  // then we simply check if there are any store-to-load-forwarding failures in the unrolled
  // loop body, which may be at larger distance than the desired threshold. We cannot do any
  // more fine-grained analysis, because the unrolling has lost the information about the
  // iteration distance.
  int simulated_unrolling_count = SuperWordStoreToLoadForwardingFailureDetection;
  int unrolled_count = vloop_analyzer.vloop().cl()->unrolled_count();
  uint simulated_super_unrolling_count = MAX2(1, simulated_unrolling_count / unrolled_count);
  int iv_stride = vloop_analyzer.vloop().iv_stride();
  int schedule_order = 0;
  for (uint k = 0; k < simulated_super_unrolling_count; k++) {
    int iv_offset = k * iv_stride; // virtual super-unrolling
    for (int i = 0; i < _schedule.length(); i++) {
      VTransformNode* vtn = _schedule.at(i);
      if (vtn->is_load_or_store_in_loop()) {
        const VPointer& p = vtn->vpointer();
        if (p.is_valid()) {
          VTransformVectorNode* vector = vtn->isa_Vector();
          bool is_load = vtn->is_load_in_loop();
          const VPointer iv_offset_p(p.make_with_iv_offset(iv_offset));
          if (iv_offset_p.is_valid()) {
            // The iv_offset may lead to overflows. This is a heuristic, so we do not
            // care too much about those edge cases.
            memory_regions.push(new VMemoryRegion(iv_offset_p, is_load, schedule_order++));
          }
        }
      }
    }
  }

  // Sort the pointers by group (same base, invar and stride), and then by offset.
  memory_regions.sort(VMemoryRegion::cmp_for_sort);

#ifndef PRODUCT
  if (_trace._verbose) {
    tty->print_cr("VTransformGraph::has_store_to_load_forwarding_failure:");
    tty->print_cr("  simulated_unrolling_count = %d", simulated_unrolling_count);
    tty->print_cr("  simulated_super_unrolling_count = %d", simulated_super_unrolling_count);
    for (int i = 0; i < memory_regions.length(); i++) {
      VMemoryRegion& region = *memory_regions.at(i);
      region.print();
    }
  }
#endif

  // For all pairs of pointers in the same group, check if they have a partial overlap.
  for (int i = 0; i < memory_regions.length(); i++) {
    VMemoryRegion& region1 = *memory_regions.at(i);

    for (int j = i + 1; j < memory_regions.length(); j++) {
      VMemoryRegion& region2 = *memory_regions.at(j);

      const VMemoryRegion::Aliasing aliasing = region1.aliasing(region2);
      if (aliasing == VMemoryRegion::Aliasing::DIFFERENT_GROUP ||
          aliasing == VMemoryRegion::Aliasing::BEFORE) {
        break; // We have reached the next group or pointers that are always after.
      } else if (aliasing == VMemoryRegion::Aliasing::EXACT_OVERLAP) {
        continue;
      } else {
        assert(aliasing == VMemoryRegion::Aliasing::PARTIAL_OVERLAP, "no other case can happen");
        if ((region1.is_load() && !region2.is_load() && region1.schedule_order() > region2.schedule_order()) ||
            (!region1.is_load() && region2.is_load() && region1.schedule_order() < region2.schedule_order())) {
          // We predict that this leads to a store-to-load-forwarding failure penalty.
#ifndef PRODUCT
          if (_trace._rejections) {
            tty->print_cr("VTransformGraph::has_store_to_load_forwarding_failure:");
            tty->print_cr("  Partial overlap of store->load. We predict that this leads to");
            tty->print_cr("  a store-to-load-forwarding failure penalty which makes");
            tty->print_cr("  vectorization unprofitable. These are the two pointers:");
            region1.print();
            region2.print();
          }
#endif
          return true;
        }
      }
    }
  }

  return false;
}

void VTransformApplyState::set_transformed_node(VTransformNode* vtn, Node* n) {
  assert(_vtnode_idx_to_transformed_node.at(vtn->_idx) == nullptr, "only set once");
  _vtnode_idx_to_transformed_node.at_put(vtn->_idx, n);
}

Node* VTransformApplyState::transformed_node(const VTransformNode* vtn) const {
  Node* n = _vtnode_idx_to_transformed_node.at(vtn->_idx);
  assert(n != nullptr, "must find IR node for vtnode");
  return n;
}

void VTransformApplyState::init_memory_states_and_uses_after_loop() {
  const GrowableArray<Node*>& inputs = _vloop_analyzer.memory_slices().inputs();
  const GrowableArray<PhiNode*>& heads = _vloop_analyzer.memory_slices().heads();
  for (int i = 0; i < inputs.length(); i++) {
    PhiNode* head = heads.at(i);
    if (head != nullptr) {
      // Slice with Phi (i.e. with stores) -> start with the phi (phi_mem)
      _memory_states.at_put(i, head);

      // Remember uses outside the loop of the last memory state (store).
      StoreNode* last_store = head->in(2)->as_Store();
      assert(vloop().in_bb(last_store), "backedge store should be in the loop");
      for (DUIterator_Fast jmax, j = last_store->fast_outs(jmax); j < jmax; j++) {
        Node* use = last_store->fast_out(j);
        if (!vloop().in_bb(use)) {
          for (uint k = 0; k < use->req(); k++) {
            if (use->in(k) == last_store) {
              _memory_state_uses_after_loop.push(MemoryStateUseAfterLoop(use, k, i));
            }
          }
        }
      }
    } else {
      // Slice without Phi (i.e. only loads) -> use the input state (entry_mem)
      _memory_states.at_put(i, inputs.at(i));
    }
  }
}

// We may have reordered the scalar stores, or replaced them with vectors. Now
// the last memory state in the loop may have changed. Thus, we need to change
// the uses of the old last memory state the new last memory state.
void VTransformApplyState::fix_memory_state_uses_after_loop() {
  for (int i = 0; i < _memory_state_uses_after_loop.length(); i++) {
    MemoryStateUseAfterLoop& use = _memory_state_uses_after_loop.at(i);
    Node* last_state = memory_state(use._alias_idx);
    phase()->igvn().replace_input_of(use._use, use._in_idx, last_state);
  }
}

void VTransformNode::apply_vtn_inputs_to_node(Node* n, VTransformApplyState& apply_state) const {
  PhaseIdealLoop* phase = apply_state.phase();
  for (uint i = 0; i < req(); i++) {
    VTransformNode* vtn_def = in_req(i);
    if (vtn_def != nullptr) {
      Node* def = apply_state.transformed_node(vtn_def);
      phase->igvn().replace_input_of(n, i, def);
    }
  }
}

float VTransformMemopScalarNode::cost(const VLoopAnalyzer& vloop_analyzer) const {
  // This is an identity transform, but loads and stores must be counted.
  assert(!vloop_analyzer.has_zero_cost(_node), "memop nodes must be counted");
  return vloop_analyzer.cost_for_scalar_node(_node->Opcode());
}

VTransformApplyResult VTransformMemopScalarNode::apply(VTransformApplyState& apply_state) const {
  apply_vtn_inputs_to_node(_node, apply_state);
  // The memory state has to be applied separately: the vtn does not hold it. This allows reordering.
  Node* mem = apply_state.memory_state(_node->adr_type());
  apply_state.phase()->igvn().replace_input_of(_node, 1, mem);
  if (_node->is_Store()) {
    apply_state.set_memory_state(_node->adr_type(), _node);
  }

  return VTransformApplyResult::make_scalar(_node);
}

float VTransformDataScalarNode::cost(const VLoopAnalyzer& vloop_analyzer) const {
  // Since this is an identity transform, we may have nodes that also
  // VLoopAnalyzer::cost does not count for the scalar loop.
  if (vloop_analyzer.has_zero_cost(_node)) {
    return 0;
  } else {
    return vloop_analyzer.cost_for_scalar_node(_node->Opcode());
  }
}

VTransformApplyResult VTransformDataScalarNode::apply(VTransformApplyState& apply_state) const {
  apply_vtn_inputs_to_node(_node, apply_state);
  return VTransformApplyResult::make_scalar(_node);
}

VTransformApplyResult VTransformPhiScalarNode::apply(VTransformApplyState& apply_state) const {
  PhaseIdealLoop* phase = apply_state.phase();
  Node* in0 = apply_state.transformed_node(in_req(0));
  Node* in1 = apply_state.transformed_node(in_req(1));
  phase->igvn().replace_input_of(_node, 0, in0);
  phase->igvn().replace_input_of(_node, 1, in1);
  // Note: the backedge is hooked up later.

  return VTransformApplyResult::make_scalar(_node);
}

// Cleanup backedges. In the schedule, the backedges come after their phis. Hence,
// we only have the transformed backedges after the phis are already transformed.
// We hook the backedges into the phis now, during cleanup.
void VTransformPhiScalarNode::apply_backedge(VTransformApplyState& apply_state) const {
  assert(_node == apply_state.transformed_node(this), "sanity");
  PhaseIdealLoop* phase = apply_state.phase();
  if (_node->is_memory_phi()) {
    // Memory phi/backedge
    // The last memory state of that slice is the backedge.
    Node* last_state = apply_state.memory_state(_node->adr_type());
    phase->igvn().replace_input_of(_node, 2, last_state);
  } else {
    // Data phi/backedge
    Node* in2 = apply_state.transformed_node(in_req(2));
    phase->igvn().replace_input_of(_node, 2, in2);
  }
}

VTransformApplyResult VTransformCFGNode::apply(VTransformApplyState& apply_state) const {
  // We do not modify the inputs of the CountedLoop (and certainly not its backedge)
  if (!_node->is_CountedLoop()) {
    apply_vtn_inputs_to_node(_node, apply_state);
  }
  return VTransformApplyResult::make_scalar(_node);
}

VTransformApplyResult VTransformOuterNode::apply(VTransformApplyState& apply_state) const {
  apply_vtn_inputs_to_node(_node, apply_state);
  return VTransformApplyResult::make_scalar(_node);
}

float VTransformReplicateNode::cost(const VLoopAnalyzer& vloop_analyzer) const {
  return vloop_analyzer.cost_for_vector_node(Op_Replicate, _vlen, _element_type);
}

VTransformApplyResult VTransformReplicateNode::apply(VTransformApplyState& apply_state) const {
  Node* val = apply_state.transformed_node(in_req(1));
  VectorNode* vn = VectorNode::scalar2vector(val, _vlen, _element_type);
  register_new_node_from_vectorization(apply_state, vn);
  return VTransformApplyResult::make_vector(vn);
}

float VTransformConvI2LNode::cost(const VLoopAnalyzer& vloop_analyzer) const {
  return vloop_analyzer.cost_for_scalar_node(Op_ConvI2L);
}

VTransformApplyResult VTransformConvI2LNode::apply(VTransformApplyState& apply_state) const {
  Node* val = apply_state.transformed_node(in_req(1));
  Node* n = new ConvI2LNode(val);
  register_new_node_from_vectorization(apply_state, n);
  return VTransformApplyResult::make_scalar(n);
}

float VTransformShiftCountNode::cost(const VLoopAnalyzer& vloop_analyzer) const {
  int shift_count_opc = VectorNode::shift_count_opcode(_shift_opcode);
  return vloop_analyzer.cost_for_scalar_node(Op_AndI) +
         vloop_analyzer.cost_for_vector_node(shift_count_opc, _vlen, _element_bt);
}

VTransformApplyResult VTransformShiftCountNode::apply(VTransformApplyState& apply_state) const {
  PhaseIdealLoop* phase = apply_state.phase();
  Node* shift_count_in = apply_state.transformed_node(in_req(1));
  assert(shift_count_in->bottom_type()->isa_int(), "int type only for shift count");
  // The shift_count_in would be automatically truncated to the lowest _mask
  // bits in a scalar shift operation. But vector shift does not truncate, so
  // we must apply the mask now.
  Node* shift_count_masked = new AndINode(shift_count_in, phase->intcon(_mask));
  register_new_node_from_vectorization(apply_state, shift_count_masked);
  // Now that masked value is "boadcast" (some platforms only set the lowest element).
  VectorNode* vn = VectorNode::shift_count(_shift_opcode, shift_count_masked, _vlen, _element_bt);
  register_new_node_from_vectorization(apply_state, vn);
  return VTransformApplyResult::make_vector(vn);
}

float VTransformPopulateIndexNode::cost(const VLoopAnalyzer& vloop_analyzer) const {
  return vloop_analyzer.cost_for_vector_node(Op_PopulateIndex, _vlen, _element_bt);
}

VTransformApplyResult VTransformPopulateIndexNode::apply(VTransformApplyState& apply_state) const {
  PhaseIdealLoop* phase = apply_state.phase();
  Node* val = apply_state.transformed_node(in_req(1));
  assert(val->is_Phi(), "expected to be iv");
  assert(VectorNode::is_populate_index_supported(_element_bt), "should support");
  const TypeVect* vt = TypeVect::make(_element_bt, _vlen);
  VectorNode* vn = new PopulateIndexNode(val, phase->intcon(1), vt);
  register_new_node_from_vectorization(apply_state, vn);
  return VTransformApplyResult::make_vector(vn);
}

float VTransformElementWiseVectorNode::cost(const VLoopAnalyzer& vloop_analyzer) const {
  return vloop_analyzer.cost_for_vector_node(_vector_opcode, vector_length(), element_basic_type());
}

VTransformApplyResult VTransformElementWiseVectorNode::apply(VTransformApplyState& apply_state) const {
  assert(2 <= req() && req() <= 4, "Must have 1-3 inputs");
  const TypeVect* vt = TypeVect::make(element_basic_type(), vector_length());
  Node* in1 =                apply_state.transformed_node(in_req(1));
  Node* in2 = (req() >= 3) ? apply_state.transformed_node(in_req(2)) : nullptr;

  VectorNode* vn = nullptr;
  if (req() <= 3) {
    vn = VectorNode::make(_vector_opcode, in1, in2, vt); // unary and binary
  } else {
    Node* in3 = apply_state.transformed_node(in_req(3));
    vn = VectorNode::make(_vector_opcode, in1, in2, in3, vt); // ternary
  }

  register_new_node_from_vectorization(apply_state, vn);
  return VTransformApplyResult::make_vector(vn);
}

float VTransformElementWiseLongOpWithCastToIntVectorNode::cost(const VLoopAnalyzer& vloop_analyzer) const {
  int vopc = VectorNode::opcode(scalar_opcode(), element_basic_type());
  return vloop_analyzer.cost_for_vector_node(vopc, vector_length(), element_basic_type()) +
         vloop_analyzer.cost_for_vector_node(Op_VectorCastL2X, vector_length(), T_INT);
}

VTransformApplyResult VTransformElementWiseLongOpWithCastToIntVectorNode::apply(VTransformApplyState& apply_state) const {
  uint vlen = vector_length();
  int sopc  = scalar_opcode();
  Node* in1 = apply_state.transformed_node(in_req(1));

  // The scalar operation was a long -> int operation.
  // However, the vector operation is long -> long.
  VectorNode* long_vn = VectorNode::make(sopc, in1, nullptr, vlen, T_LONG);
  register_new_node_from_vectorization(apply_state, long_vn);
  // Cast long -> int, to mimic the scalar long -> int operation.
  VectorNode* vn = VectorCastNode::make(Op_VectorCastL2X, long_vn, T_INT, vlen);
  register_new_node_from_vectorization(apply_state, vn);
  return VTransformApplyResult::make_vector(vn);
}

float VTransformReinterpretVectorNode::cost(const VLoopAnalyzer& vloop_analyzer) const {
  return vloop_analyzer.cost_for_vector_node(Op_VectorReinterpret, vector_length(), element_basic_type());
}

VTransformApplyResult VTransformReinterpretVectorNode::apply(VTransformApplyState& apply_state) const {
  const TypeVect* dst_vt = TypeVect::make(element_basic_type(), vector_length());
  const TypeVect* src_vt = TypeVect::make(_src_bt,              vector_length());
  assert(VectorNode::is_reinterpret_opcode(scalar_opcode()), "scalar opcode must be reinterpret");

  Node* in1 = apply_state.transformed_node(in_req(1));
  VectorNode* vn = new VectorReinterpretNode(in1, src_vt, dst_vt);

  register_new_node_from_vectorization(apply_state, vn);
  return VTransformApplyResult::make_vector(vn);
}

float VTransformBoolVectorNode::cost(const VLoopAnalyzer& vloop_analyzer) const {
  assert(scalar_opcode() == Op_Bool, "");
  return vloop_analyzer.cost_for_vector_node(Op_VectorMaskCmp, vector_length(), element_basic_type());
}

VTransformApplyResult VTransformBoolVectorNode::apply(VTransformApplyState& apply_state) const {
  const TypeVect* vt = TypeVect::make(element_basic_type(), vector_length());
  assert(scalar_opcode() == Op_Bool, "");

  // Cmp + Bool -> VectorMaskCmp
  VTransformCmpVectorNode* vtn_cmp = in_req(1)->isa_CmpVector();
  assert(vtn_cmp != nullptr, "bool vtn expects cmp vtn as input");

  Node* cmp_in1 = apply_state.transformed_node(vtn_cmp->in_req(1));
  Node* cmp_in2 = apply_state.transformed_node(vtn_cmp->in_req(2));
  BoolTest::mask mask = test()._mask;

  PhaseIdealLoop* phase = apply_state.phase();
  ConINode* mask_node  = phase->intcon((int)mask);
  VectorNode* vn = new VectorMaskCmpNode(mask, cmp_in1, cmp_in2, mask_node, vt);
  register_new_node_from_vectorization(apply_state, vn);
  return VTransformApplyResult::make_vector(vn);
}

bool VTransformReductionVectorNode::optimize(const VLoopAnalyzer& vloop_analyzer, VTransform& vtransform) {
  return optimize_move_non_strict_order_reductions_out_of_loop(vloop_analyzer, vtransform);
}

int VTransformReductionVectorNode::vector_reduction_opcode() const {
  return ReductionNode::opcode(scalar_opcode(), element_basic_type());
}

bool VTransformReductionVectorNode::requires_strict_order() const {
  int vopc = vector_reduction_opcode();
  return ReductionNode::auto_vectorization_requires_strict_order(vopc);
}

// Having ReductionNodes in the loop is expensive. They need to recursively
// fold together the vector values, for every vectorized loop iteration. If
// we encounter the following pattern, we can vector accumulate the values
// inside the loop, and only have a single UnorderedReduction after the loop.
//
// Note: UnorderedReduction represents a ReductionNode which does not require
// calculating in strict order.
//
// CountedLoop     init
//          |        |
//          +------+ | +------------------------+
//                 | | |                        |
//                PhiNode (s)                   |
//                  |                           |
//                  |          Vector           |
//                  |            |              |
//               UnorderedReduction (first_red) |
//                  |                           |
//                 ...         Vector           |
//                  |            |              |
//               UnorderedReduction (last_red)  |
//                       |                      |
//                       +----------------------+
//
// We patch the graph to look like this:
//
// CountedLoop   identity_vector
//         |         |
//         +-------+ | +---------------+
//                 | | |               |
//                PhiNode (v)          |
//                   |                 |
//                   |         Vector  |
//                   |           |     |
//                 VectorAccumulator   |
//                   |                 |
//                  ...        Vector  |
//                   |           |     |
//      init       VectorAccumulator   |
//        |          |     |           |
//     UnorderedReduction  +-----------+
//
// We turned the scalar (s) Phi into a vectorized one (v). In the loop, we
// use vector_accumulators, which do the same reductions, but only element
// wise. This is a single operation per vector_accumulator, rather than many
// for a UnorderedReduction. We can then reduce the last vector_accumulator
// after the loop, and also reduce the init value into it.
//
// We can not do this with all reductions. Some reductions do not allow the
// reordering of operations (for example float addition/multiplication require
// strict order).
//
// Note: we must perform this optimization already during auto vectorization,
//       before we evaluate the cost-model. Without this optimization, we may
//       still have expensive reduction nodes in the loop which can make
//       vectorization unprofitable. Only with the optimization does vectorization
//       become profitable, since the expensive reduction node is moved
//       outside the loop, and instead cheaper element-wise vector accumulations
//       are performed inside the loop.
bool VTransformReductionVectorNode::optimize_move_non_strict_order_reductions_out_of_loop_preconditions(VTransform& vtransform) {
  // We have a phi with a single use.
  VTransformPhiScalarNode* phi = in_req(1)->isa_PhiScalar();
  if (phi == nullptr) {
    return false;
  }
  if (phi->out_strong_edges() != 1) {
    TRACE_OPTIMIZE(
      tty->print("  Cannot move out of loop, phi has multiple uses:");
      print();
      tty->print("  phi: ");
      phi->print();
    )
    return false;
  }

  if (requires_strict_order()) {
    TRACE_OPTIMIZE(
      tty->print("  Cannot move out of loop, strict order required: ");
      print();
    )
    return false;
  }

  const int sopc     = scalar_opcode();
  const uint vlen    = vector_length();
  const BasicType bt = element_basic_type();
  const int ropc     = vector_reduction_opcode();
  const int vopc     = VectorNode::opcode(sopc, bt);
  if (!Matcher::match_rule_supported_auto_vectorization(vopc, vlen, bt)) {
    // The element-wise vector operation needed for the vector accumulator
    // is not implemented / supported.
    return false;
  }

  // Traverse up the chain of non strict order reductions, checking that it loops
  // back to the phi. Check that all non strict order reductions only have a single
  // use, except for the last (last_red), which only has phi as a use in the loop,
  // and all other uses are outside the loop.
  VTransformReductionVectorNode* last_red    = phi->in_req(2)->isa_ReductionVector();
  VTransformReductionVectorNode* current_red = last_red;
  while (true) {
    if (current_red == nullptr ||
        current_red->vector_reduction_opcode() != ropc ||
        current_red->element_basic_type() != bt ||
        current_red->vector_length() != vlen) {
      TRACE_OPTIMIZE(
        tty->print("  Cannot move out of loop, other reduction node does not match:");
        print();
        tty->print("  other: ");
        if (current_red != nullptr) {
          current_red->print();
        } else {
          tty->print("nullptr");
        }
      )
      return false; // not compatible
    }

    VTransformVectorNode* vector_input = current_red->in_req(2)->isa_Vector();
    if (vector_input == nullptr) {
      assert(false, "reduction has a bad vector input");
      return false;
    }

    // Expect single use of the non strict order reduction. Except for the last_red.
    if (current_red == last_red) {
      // All uses must be outside loop body, except for the phi.
      for (uint i = 0; i < current_red->out_strong_edges(); i++) {
        VTransformNode* use = current_red->out_strong_edge(i);
        if (use->isa_PhiScalar() == nullptr &&
            use->isa_Outer() == nullptr) {
          // Should not be allowed by SuperWord::mark_reductions
          assert(false, "reduction has use inside loop");
          return false;
        }
      }
    } else {
      if (current_red->out_strong_edges() != 1) {
        TRACE_OPTIMIZE(
          tty->print("  Cannot move out of loop, other reduction node has use outside loop:");
          print();
          tty->print("  other: ");
          current_red->print();
        )
        return false; // Only single use allowed
      }
    }

    // If the scalar input is a phi, we passed all checks.
    VTransformNode* scalar_input = current_red->in_req(1);
    if (scalar_input == phi) {
      break;
    }

    // We expect another non strict reduction, verify it in the next iteration.
    current_red = scalar_input->isa_ReductionVector();
  }
  return true; // success
}

bool VTransformReductionVectorNode::optimize_move_non_strict_order_reductions_out_of_loop(const VLoopAnalyzer& vloop_analyzer, VTransform& vtransform) {
  if (!optimize_move_non_strict_order_reductions_out_of_loop_preconditions(vtransform)) {
    return false;
  }

  // All checks were successful. Edit the vtransform graph now.
  TRACE_OPTIMIZE(
    tty->print_cr("VTransformReductionVectorNode::optimize_move_non_strict_order_reductions_out_of_loop");
  )

  const int sopc     = scalar_opcode();
  const uint vlen    = vector_length();
  const BasicType bt = element_basic_type();
  const int vopc     = VectorNode::opcode(sopc, bt);
  PhaseIdealLoop* phase = vloop_analyzer.vloop().phase();

  // Create a vector of identity values.
  Node* identity = ReductionNode::make_identity_con_scalar(phase->igvn(), sopc, bt);
  phase->set_root_as_ctrl(identity);
  VTransformNode* vtn_identity = new (vtransform.arena()) VTransformOuterNode(vtransform, identity);

  VTransformNode* vtn_identity_vector = new (vtransform.arena()) VTransformReplicateNode(vtransform, vlen, bt);
  vtn_identity_vector->init_req(1, vtn_identity);

  // Look at old scalar phi.
  VTransformPhiScalarNode* phi_scalar = in_req(1)->isa_PhiScalar();
  PhiNode* old_phi = phi_scalar->node();
  VTransformNode* init = phi_scalar->in_req(1);

  TRACE_OPTIMIZE(
    tty->print("  phi_scalar ");
    phi_scalar->print();
  )

  // Create new vector phi
  const VTransformVectorNodeProperties properties = VTransformVectorNodeProperties::make_for_phi_vector(old_phi, vlen, bt);
  VTransformPhiVectorNode* phi_vector = new (vtransform.arena()) VTransformPhiVectorNode(vtransform, 3, properties);
  phi_vector->init_req(0, phi_scalar->in_req(0));
  phi_vector->init_req(1, vtn_identity_vector);
  // Note: backedge comes later

  // Traverse down the chain of reductions, and replace them with vector_accumulators.
  VTransformReductionVectorNode* first_red   = this;
  VTransformReductionVectorNode* last_red    = phi_scalar->in_req(2)->isa_ReductionVector();
  VTransformReductionVectorNode* current_red = first_red;
  VTransformNode* current_vector_accumulator = phi_vector;
  while (true) {
    VTransformNode* vector_input = current_red->in_req(2);
    VTransformVectorNode* vector_accumulator = new (vtransform.arena()) VTransformElementWiseVectorNode(vtransform, 3, current_red->properties(), vopc);
    vector_accumulator->init_req(1, current_vector_accumulator);
    vector_accumulator->init_req(2, vector_input);
    TRACE_OPTIMIZE(
      tty->print("  replace    ");
      current_red->print();
      tty->print("  with       ");
      vector_accumulator->print();
    )
    current_vector_accumulator = vector_accumulator;
    if (current_red == last_red) { break; }
    current_red = current_red->unique_out_strong_edge()->isa_ReductionVector();
  }

  // Feed vector accumulator into the backedge.
  phi_vector->set_req(2, current_vector_accumulator);

  // Create post-loop reduction. last_red keeps all uses outside the loop.
  last_red->set_req(1, init);
  last_red->set_req(2, current_vector_accumulator);

  TRACE_OPTIMIZE(
    tty->print("  phi_scalar ");
    phi_scalar->print();
    tty->print("  after loop ");
    last_red->print();
  )
  return true; // success
}

float VTransformReductionVectorNode::cost(const VLoopAnalyzer& vloop_analyzer) const {
  uint vlen    = vector_length();
  BasicType bt = element_basic_type();
  int vopc = vector_reduction_opcode();
  bool requires_strict_order = ReductionNode::auto_vectorization_requires_strict_order(vopc);
  return vloop_analyzer.cost_for_vector_reduction_node(vopc, vlen, bt, requires_strict_order);
}

VTransformApplyResult VTransformReductionVectorNode::apply(VTransformApplyState& apply_state) const {
  Node* init = apply_state.transformed_node(in_req(1));
  Node* vec  = apply_state.transformed_node(in_req(2));

  ReductionNode* vn = ReductionNode::make(scalar_opcode(), nullptr, init, vec, element_basic_type());
  register_new_node_from_vectorization(apply_state, vn);
  return VTransformApplyResult::make_vector(vn, vn->vect_type());
}

VTransformApplyResult VTransformPhiVectorNode::apply(VTransformApplyState& apply_state) const {
  PhaseIdealLoop* phase = apply_state.phase();
  Node* in0 = apply_state.transformed_node(in_req(0));
  Node* in1 = apply_state.transformed_node(in_req(1));

  // We create a new phi node, because the type is different to the scalar phi.
  PhiNode* old_phi = approximate_origin()->as_Phi();
  PhiNode* new_phi = old_phi->clone()->as_Phi();

  phase->igvn().replace_input_of(new_phi, 0, in0);
  phase->igvn().replace_input_of(new_phi, 1, in1);
  // Note: the backedge is hooked up later.

  // Give the new phi node the correct vector type.
  const TypeVect* vt = TypeVect::make(element_basic_type(), vector_length());
  new_phi->as_Type()->set_type(vt);
  phase->igvn().set_type(new_phi, vt);

  return VTransformApplyResult::make_vector(new_phi, vt);
}

// Cleanup backedges. In the schedule, the backedges come after their phis. Hence,
// we only have the transformed backedges after the phis are already transformed.
// We hook the backedges into the phis now, during cleanup.
void VTransformPhiVectorNode::apply_backedge(VTransformApplyState& apply_state) const {
  PhaseIdealLoop* phase = apply_state.phase();
  PhiNode* new_phi = apply_state.transformed_node(this)->as_Phi();
  Node* in2 = apply_state.transformed_node(in_req(2));
  phase->igvn().replace_input_of(new_phi, 2, in2);
}

float VTransformLoadVectorNode::cost(const VLoopAnalyzer& vloop_analyzer) const {
  uint vlen    = vector_length();
  BasicType bt = element_basic_type();
  return vloop_analyzer.cost_for_vector_node(Op_LoadVector, vlen, bt);
}

VTransformApplyResult VTransformLoadVectorNode::apply(VTransformApplyState& apply_state) const {
  int sopc     = scalar_opcode();
  uint vlen    = vector_length();
  BasicType bt = element_basic_type();

  // The memory state has to be applied separately: the vtn does not hold it. This allows reordering.
  Node* ctrl = apply_state.transformed_node(in_req(MemNode::Control));
  Node* mem  = apply_state.memory_state(_adr_type);
  Node* adr  = apply_state.transformed_node(in_req(MemNode::Address));

  // Set the memory dependency of the LoadVector as early as possible.
  // Walk up the memory chain, and ignore any StoreVector that provably
  // does not have any memory dependency.
  const VPointer& load_p = vpointer();
  while (mem->is_StoreVector()) {
    VPointer store_p(mem->as_Mem(), apply_state.vloop());
    if (store_p.never_overlaps_with(load_p)) {
      mem = mem->in(MemNode::Memory);
    } else {
      break;
    }
  }

  LoadVectorNode* vn = LoadVectorNode::make(sopc, ctrl, mem, adr, _adr_type, vlen, bt, _control_dependency);
  DEBUG_ONLY( if (VerifyAlignVector) { vn->set_must_verify_alignment(); } )
  register_new_node_from_vectorization(apply_state, vn);
  return VTransformApplyResult::make_vector(vn, vn->vect_type());
}

float VTransformStoreVectorNode::cost(const VLoopAnalyzer& vloop_analyzer) const {
  uint vlen    = vector_length();
  BasicType bt = element_basic_type();
  return vloop_analyzer.cost_for_vector_node(Op_StoreVector, vlen, bt);
}

VTransformApplyResult VTransformStoreVectorNode::apply(VTransformApplyState& apply_state) const {
  int sopc  = scalar_opcode();
  uint vlen = vector_length();

  // The memory state has to be applied separately: the vtn does not hold it. This allows reordering.
  Node* ctrl = apply_state.transformed_node(in_req(MemNode::Control));
  Node* mem  = apply_state.memory_state(_adr_type);
  Node* adr  = apply_state.transformed_node(in_req(MemNode::Address));

  Node* value = apply_state.transformed_node(in_req(MemNode::ValueIn));
  StoreVectorNode* vn = StoreVectorNode::make(sopc, ctrl, mem, adr, _adr_type, value, vlen);
  DEBUG_ONLY( if (VerifyAlignVector) { vn->set_must_verify_alignment(); } )
  register_new_node_from_vectorization(apply_state, vn);
  apply_state.set_memory_state(_adr_type, vn);
  return VTransformApplyResult::make_vector(vn, vn->vect_type());
}

void VTransformNode::register_new_node_from_vectorization(VTransformApplyState& apply_state, Node* vn) const {
  PhaseIdealLoop* phase = apply_state.phase();
  // Using the cl is sometimes not the most accurate, but still correct. We do not have to be
  // perfectly accurate, because we will set major_progress anyway.
  phase->register_new_node(vn, apply_state.vloop().cl());
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

void VTransformNode::print() const {
  tty->print("%3d %s (", _idx, name());
  for (uint i = 0; i < _req; i++) {
    print_node_idx(_in.at(i));
  }
  if ((uint)_in.length() > _req) {
    tty->print(" | strong:");
    for (uint i = _req; i < _in_end_strong_memory_edges; i++) {
      print_node_idx(_in.at(i));
    }
  }
  if ((uint)_in.length() > _in_end_strong_memory_edges) {
    tty->print(" | weak:");
    for (uint i = _in_end_strong_memory_edges; i < (uint)_in.length(); i++) {
      print_node_idx(_in.at(i));
    }
  }
  tty->print(") %s[", _is_alive ? "" : "dead ");
  for (uint i = 0; i < _out_end_strong_edges; i++) {
    print_node_idx(_out.at(i));
  }
  if ((uint)_out.length() > _out_end_strong_edges) {
    tty->print(" | weak:");
    for (uint i = _out_end_strong_edges; i < (uint)_out.length(); i++) {
      print_node_idx(_out.at(i));
    }
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

void VTransformMemopScalarNode::print_spec() const {
  tty->print("node[%d %s] ", _node->_idx, _node->Name());
  _vpointer.print_on(tty, false);
}

void VTransformDataScalarNode::print_spec() const {
  tty->print("node[%d %s]", _node->_idx, _node->Name());
}

void VTransformPhiScalarNode::print_spec() const {
  tty->print("node[%d %s]", _node->_idx, _node->Name());
}

void VTransformCFGNode::print_spec() const {
  tty->print("node[%d %s]", _node->_idx, _node->Name());
}

void VTransformOuterNode::print_spec() const {
  tty->print("node[%d %s]", _node->_idx, _node->Name());
}

void VTransformReplicateNode::print_spec() const {
  tty->print("vlen=%d element_type=%s", _vlen, type2name(_element_type));
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
  tty->print("Properties[orig=[%d %s] sopc=%s vlen=%d element_bt=%s]",
             approximate_origin()->_idx,
             approximate_origin()->Name(),
             NodeClassNames[scalar_opcode()],
             vector_length(),
             type2name(element_basic_type()));
  if (is_load_or_store_in_loop()) {
    tty->print(" ");
    vpointer().print_on(tty, false);
  }
}

void VTransformElementWiseVectorNode::print_spec() const {
  VTransformVectorNode::print_spec();
  tty->print(" vopc=%s", NodeClassNames[_vector_opcode]);
}

void VTransformReinterpretVectorNode::print_spec() const {
  VTransformVectorNode::print_spec();
  tty->print(" src_bt=%s", type2name(_src_bt));
}

void VTransformBoolVectorNode::print_spec() const {
  VTransformVectorNode::print_spec();
  BoolTest::mask m = BoolTest::mask(_test._mask & ~BoolTest::unsigned_compare);
  const BoolTest bt(m);
  tty->print(" test=%s", m == _test._mask ? "" : "unsigned ");
  bt.dump_on(tty);
}
#endif
