/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/convertnode.hpp"
#include "opto/memnode.hpp"
#include "opto/movenode.hpp"
#include "opto/superword.hpp"
#include "opto/superwordVTransformBuilder.hpp"
#include "opto/vectornode.hpp"

SuperWord::SuperWord(const VLoopAnalyzer &vloop_analyzer) :
  _vloop_analyzer(vloop_analyzer),
  _vloop(vloop_analyzer.vloop()),
  _arena(mtCompiler, Arena::Tag::tag_superword),
  _clone_map(phase()->C->clone_map()),                      // map of nodes created in cloning
  _pairset(&_arena, _vloop_analyzer),
  _packset(&_arena, _vloop_analyzer
           NOT_PRODUCT(COMMA is_trace_superword_packset())
           NOT_PRODUCT(COMMA is_trace_superword_rejections())
           ),
  _mem_ref_for_main_loop_alignment(nullptr),
  _aw_for_main_loop_alignment(0),
  _do_vector_loop(phase()->C->do_vector_loop()),            // whether to do vectorization/simd style
  _num_work_vecs(0),                                        // amount of vector work we have
  _num_reductions(0)                                        // amount of reduction work we have
{
}

// Collect ignored loop nodes during VPointer parsing.
class SuperWordUnrollingAnalysisIgnoredNodes : public MemPointerParserCallback {
private:
  const VLoop&     _vloop;
  const Node_List& _body;
  bool*            _ignored;

public:
  SuperWordUnrollingAnalysisIgnoredNodes(const VLoop& vloop) :
    _vloop(vloop),
    _body(_vloop.lpt()->_body),
    _ignored(NEW_RESOURCE_ARRAY(bool, _body.size()))
  {
    for (uint i = 0; i < _body.size(); i++) {
      _ignored[i] = false;
    }
  }

  virtual void callback(Node* n) override { set_ignored(n); }

  void set_ignored(uint i) {
    assert(i < _body.size(), "must be in bounds");
    _ignored[i] = true;
  }

  void set_ignored(Node* n) {
    // Only consider nodes in the loop.
    Node* ctrl = _vloop.phase()->get_ctrl(n);
    if (_vloop.lpt()->is_member(_vloop.phase()->get_loop(ctrl))) {
      // Find the index in the loop.
      for (uint j = 0; j < _body.size(); j++) {
        if (n == _body.at(j)) {
          set_ignored(j);
          return;
        }
      }
      assert(false, "must find");
    }
  }

  bool is_ignored(uint i) const {
    assert(i < _vloop.lpt()->_body.size(), "must be in bounds");
    return _ignored[i];
  }
};

// SuperWord unrolling analysis does:
// - Determine if the loop is a candidate for auto vectorization (SuperWord).
// - Find a good unrolling factor, to ensure full vector width utilization once we vectorize.
void SuperWord::unrolling_analysis(const VLoop &vloop, int &local_loop_unroll_factor) {
  IdealLoopTree* lpt    = vloop.lpt();
  CountedLoopNode* cl   = vloop.cl();
  Node* cl_exit         = vloop.cl_exit();
  PhaseIdealLoop* phase = vloop.phase();

  SuperWordUnrollingAnalysisIgnoredNodes ignored_nodes(vloop);
  bool is_slp = true;

  int max_vector = Matcher::max_vector_size_auto_vectorization(T_BYTE);

  // Process the loop, some/all of the stack entries will not be in order, ergo
  // need to preprocess the ignored initial state before we process the loop
  for (uint i = 0; i < lpt->_body.size(); i++) {
    Node* n = lpt->_body.at(i);
    if (n == cl->incr() ||
      n->is_AddP() ||
      n->is_Cmp() ||
      n->is_Bool() ||
      n->is_IfTrue() ||
      n->is_CountedLoop() ||
      (n == cl_exit)) {
      ignored_nodes.set_ignored(i);
      continue;
    }

    if (n->is_If()) {
      IfNode *iff = n->as_If();
      if (iff->_fcnt != COUNT_UNKNOWN && iff->_prob != PROB_UNKNOWN) {
        if (lpt->is_loop_exit(iff)) {
          ignored_nodes.set_ignored(i);
          continue;
        }
      }
    }

    if (n->is_memory_phi()) {
      Node* n_tail = n->in(LoopNode::LoopBackControl);
      if (n_tail != n->in(LoopNode::EntryControl)) {
        if (!n_tail->is_Mem()) {
          is_slp = false;
          break;
        }
      }
    }

    // This must happen after check of phi/if
    if (n->is_Phi() || n->is_If()) {
      ignored_nodes.set_ignored(i);
      continue;
    }

    if (n->is_LoadStore() || n->is_MergeMem() ||
      (n->is_Proj() && !n->as_Proj()->is_CFG())) {
      is_slp = false;
      break;
    }

    // Ignore nodes with non-primitive type.
    BasicType bt;
    if (n->is_Mem()) {
      bt = n->as_Mem()->value_basic_type();
    } else {
      bt = n->bottom_type()->basic_type();
    }
    if (is_java_primitive(bt) == false) {
      ignored_nodes.set_ignored(i);
      continue;
    }

    if (n->is_Mem()) {
      MemNode* current = n->as_Mem();
      Node* adr = n->in(MemNode::Address);
      Node* n_ctrl = phase->get_ctrl(adr);

      // save a queue of post process nodes
      if (n_ctrl != nullptr && lpt->is_member(phase->get_loop(n_ctrl))) {
        // Parse the address expression with VPointer, and mark the internal
        // nodes of the address expression in ignore_nodes.
        VPointer p(current, vloop, ignored_nodes);
      }
    }
  }

  if (is_slp) {
    // Now we try to find the maximum supported consistent vector which the machine
    // description can use
    bool flag_small_bt = false;
    for (uint i = 0; i < lpt->_body.size(); i++) {
      if (ignored_nodes.is_ignored(i)) continue;

      BasicType bt;
      Node* n = lpt->_body.at(i);
      if (n->is_Mem()) {
        bt = n->as_Mem()->value_basic_type();
      } else {
        bt = n->bottom_type()->basic_type();
      }

      if (is_java_primitive(bt) == false) continue;

      int cur_max_vector = Matcher::max_vector_size_auto_vectorization(bt);

      // If a max vector exists which is not larger than _local_loop_unroll_factor
      // stop looking, we already have the max vector to map to.
      if (cur_max_vector < local_loop_unroll_factor) {
        is_slp = false;
#ifndef PRODUCT
        if (TraceSuperWordLoopUnrollAnalysis) {
          tty->print_cr("slp analysis fails: unroll limit greater than max vector\n");
        }
#endif
        break;
      }

      // Map the maximal common vector except conversion nodes, because we can't get
      // the precise basic type for conversion nodes in the stage of early analysis.
      if (!VectorNode::is_convert_opcode(n->Opcode()) &&
          VectorNode::implemented(n->Opcode(), cur_max_vector, bt)) {
        if (cur_max_vector < max_vector && !flag_small_bt) {
          max_vector = cur_max_vector;
        } else if (cur_max_vector > max_vector && UseSubwordForMaxVector) {
          // Analyse subword in the loop to set maximum vector size to take advantage of full vector width for subword types.
          // Here we analyze if narrowing is likely to happen and if it is we set vector size more aggressively.
          // We check for possibility of narrowing by looking through chain operations using subword types.
          if (is_subword_type(bt)) {
            uint start, end;
            VectorNode::vector_operands(n, &start, &end);

            for (uint j = start; j < end; j++) {
              Node* in = n->in(j);
              // Don't propagate through a memory
              if (!in->is_Mem() && vloop.in_bb(in) && in->bottom_type()->basic_type() == T_INT) {
                bool same_type = true;
                for (DUIterator_Fast kmax, k = in->fast_outs(kmax); k < kmax; k++) {
                  Node *use = in->fast_out(k);
                  if (!vloop.in_bb(use) && use->bottom_type()->basic_type() != bt) {
                    same_type = false;
                    break;
                  }
                }
                if (same_type) {
                  max_vector = cur_max_vector;
                  flag_small_bt = true;
                  cl->mark_subword_loop();
                }
              }
            }
          }
        }
      }
    }
    if (is_slp) {
      local_loop_unroll_factor = max_vector;
      cl->mark_passed_slp();
    }
    cl->mark_was_slp();
    if (cl->is_main_loop()) {
#ifndef PRODUCT
      if (TraceSuperWordLoopUnrollAnalysis) {
        tty->print_cr("slp analysis: set max unroll to %d", local_loop_unroll_factor);
      }
#endif
      cl->set_slp_max_unroll(local_loop_unroll_factor);
    }
  }
}

bool VLoopReductions::is_reduction(const Node* n) {
  if (!is_reduction_operator(n)) {
    return false;
  }
  // Test whether there is a reduction cycle via every edge index
  // (typically indices 1 and 2).
  for (uint input = 1; input < n->req(); input++) {
    if (in_reduction_cycle(n, input)) {
      return true;
    }
  }
  return false;
}

bool VLoopReductions::is_reduction_operator(const Node* n) {
  int opc = n->Opcode();
  return (opc != ReductionNode::opcode(opc, n->bottom_type()->basic_type()));
}

bool VLoopReductions::in_reduction_cycle(const Node* n, uint input) {
  // First find input reduction path to phi node.
  auto has_my_opcode = [&](const Node* m){ return m->Opcode() == n->Opcode(); };
  PathEnd path_to_phi = find_in_path(n, input, LoopMaxUnroll, has_my_opcode,
                                     [&](const Node* m) { return m->is_Phi(); });
  const Node* phi = path_to_phi.first;
  if (phi == nullptr) {
    return false;
  }
  // If there is an input reduction path from the phi's loop-back to n, then n
  // is part of a reduction cycle.
  const Node* first = phi->in(LoopNode::LoopBackControl);
  PathEnd path_from_phi = find_in_path(first, input, LoopMaxUnroll, has_my_opcode,
                                       [&](const Node* m) { return m == n; });
  return path_from_phi.first != nullptr;
}

Node* VLoopReductions::original_input(const Node* n, uint i) {
  if (n->has_swapped_edges()) {
    assert(n->is_Add() || n->is_Mul(), "n should be commutative");
    if (i == 1) {
      return n->in(2);
    } else if (i == 2) {
      return n->in(1);
    }
  }
  return n->in(i);
}

void VLoopReductions::mark_reductions() {
  assert(_loop_reductions.is_empty(), "must not yet be computed");
  CountedLoopNode* cl = _vloop.cl();

  // Iterate through all phi nodes associated to the loop and search for
  // reduction cycles in the basic block.
  for (DUIterator_Fast imax, i = cl->fast_outs(imax); i < imax; i++) {
    const Node* phi = cl->fast_out(i);
    if (!phi->is_Phi()) {
      continue;
    }
    if (phi->outcnt() == 0) {
      continue;
    }
    if (phi == _vloop.iv()) {
      continue;
    }
    // The phi's loop-back is considered the first node in the reduction cycle.
    const Node* first = phi->in(LoopNode::LoopBackControl);
    if (first == nullptr) {
      continue;
    }
    // Test that the node fits the standard pattern for a reduction operator.
    if (!is_reduction_operator(first)) {
      continue;
    }
    // Test that 'first' is the beginning of a reduction cycle ending in 'phi'.
    // To contain the number of searched paths, assume that all nodes in a
    // reduction cycle are connected via the same edge index, modulo swapped
    // inputs. This assumption is realistic because reduction cycles usually
    // consist of nodes cloned by loop unrolling.
    int reduction_input = -1;
    int path_nodes = -1;
    for (uint input = 1; input < first->req(); input++) {
      // Test whether there is a reduction path in the basic block from 'first'
      // to the phi node following edge index 'input'.
      PathEnd path =
        find_in_path(
          first, input, _vloop.lpt()->_body.size(),
          [&](const Node* n) { return n->Opcode() == first->Opcode() &&
                                      _vloop.in_bb(n); },
          [&](const Node* n) { return n == phi; });
      if (path.first != nullptr) {
        reduction_input = input;
        path_nodes = path.second;
        break;
      }
    }
    if (reduction_input == -1) {
      continue;
    }
    // Test that reduction nodes do not have any users in the loop besides their
    // reduction cycle successors.
    const Node* current = first;
    const Node* succ = phi; // current's successor in the reduction cycle.
    bool used_in_loop = false;
    for (int i = 0; i < path_nodes; i++) {
      for (DUIterator_Fast jmax, j = current->fast_outs(jmax); j < jmax; j++) {
        Node* u = current->fast_out(j);
        if (!_vloop.in_bb(u)) {
          continue;
        }
        if (u == succ) {
          continue;
        }
        used_in_loop = true;
        break;
      }
      if (used_in_loop) {
        break;
      }
      succ = current;
      current = original_input(current, reduction_input);
    }
    if (used_in_loop) {
      continue;
    }
    // Reduction cycle found. Mark all nodes in the found path as reductions.
    current = first;
    for (int i = 0; i < path_nodes; i++) {
      _loop_reductions.set(current->_idx);
      current = original_input(current, reduction_input);
    }
  }
}

bool SuperWord::transform_loop() {
  assert(phase()->C->do_superword(), "SuperWord option should be enabled");
  assert(cl()->is_main_loop(), "SLP should only work on main loops");
#ifndef PRODUCT
  if (is_trace_superword_any()) {
    tty->print_cr("\nSuperWord::transform_loop:");
    lpt()->dump_head();
    cl()->dump();
  }
#endif

  if (!SLP_extract()) {
#ifndef PRODUCT
    if (is_trace_superword_any()) {
      tty->print_cr("\nSuperWord::transform_loop failed: SuperWord::SLP_extract did not vectorize");
    }
#endif
    return false;
  }

#ifndef PRODUCT
  if (is_trace_superword_any()) {
    tty->print_cr("\nSuperWord::transform_loop: success");
  }
#endif
  return true;
}

//------------------------------SLP_extract---------------------------
// Extract the superword level parallelism
//
// 1) A reverse post-order of nodes in the block is constructed.  By scanning
//    this list from first to last, all definitions are visited before their uses.
//
// 2) A point-to-point dependence graph is constructed between memory references.
//    This simplifies the upcoming "independence" checker.
//
// 3) The maximum depth in the node graph from the beginning of the block
//    to each node is computed.  This is used to prune the graph search
//    in the independence checker.
//
// 4) For integer types, the necessary bit width is propagated backwards
//    from stores to allow packed operations on byte, char, and short
//    integers.  This reverses the promotion to type "int" that javac
//    did for operations like: char c1,c2,c3;  c1 = c2 + c3.
//
// 5) One of the memory references is picked to be an aligned vector reference.
//    The pre-loop trip count is adjusted to align this reference in the
//    unrolled body.
//
// 6) The initial set of pack pairs is seeded with memory references.
//
// 7) The set of pack pairs is extended by following use->def and def->use links.
//
// 8) The pairs are combined into vector sized packs.
//
// 9) Reorder the memory slices to co-locate members of the memory packs.
//
// 10) Generate ideal vector nodes for the final set of packs and where necessary,
//    inserting scalar promotion, vector creation from multiple scalars, and
//    extraction of scalar values from vectors.
//
bool SuperWord::SLP_extract() {
  assert(cl()->is_main_loop(), "SLP should only work on main loops");

  // Find "seed" pairs.
  create_adjacent_memop_pairs();

  if (_pairset.is_empty()) {
#ifndef PRODUCT
    if (is_trace_superword_any()) {
      tty->print_cr("\nNo pair packs generated, abort SuperWord.");
      tty->cr();
    }
#endif
    return false;
  }

  extend_pairset_with_more_pairs_by_following_use_and_def();

  combine_pairs_to_longer_packs();

  split_packs_at_use_def_boundaries();  // a first time: create natural boundaries
  split_packs_only_implemented_with_smaller_size();
  split_packs_to_break_mutual_dependence();
  split_packs_at_use_def_boundaries();  // again: propagate split of other packs

  filter_packs_for_power_of_2_size();
  filter_packs_for_mutual_independence();
  filter_packs_for_alignment();
  filter_packs_for_implemented();
  filter_packs_for_profitable();

  DEBUG_ONLY(verify_packs();)
  DEBUG_ONLY(verify_no_extract());

  return schedule_and_apply();
}

int SuperWord::MemOp::cmp_by_group(MemOp* a, MemOp* b) {
  // Opcode
  int c_Opcode = cmp_code(a->mem()->Opcode(), b->mem()->Opcode());
  if (c_Opcode != 0) { return c_Opcode; }

  // VPointer summands
  return MemPointer::cmp_summands(a->vpointer().mem_pointer(),
                                  b->vpointer().mem_pointer());
}

int SuperWord::MemOp::cmp_by_group_and_con_and_original_index(MemOp* a, MemOp* b) {
  // Group
  int cmp_group = cmp_by_group(a, b);
  if (cmp_group != 0) { return cmp_group; }

  // VPointer con
  jint a_con = a->vpointer().mem_pointer().con().value();
  jint b_con = b->vpointer().mem_pointer().con().value();
  int c_con = cmp_code(a_con, b_con);
  if (c_con != 0) { return c_con; }

  return cmp_code(a->original_index(), b->original_index());
}

// Find the "seed" memops pairs. These are pairs that we strongly suspect would lead to vectorization.
void SuperWord::create_adjacent_memop_pairs() {
  ResourceMark rm;
  GrowableArray<MemOp> memops;

  collect_valid_memops(memops);

  // Sort the MemOps by group, and inside a group by VPointer con:
  //  - Group: all memops with the same opcode, and the same VPointer summands. Adjacent memops
  //           have the same opcode and the same VPointer summands, only the VPointer con is
  //           different. Thus, two memops can only be adjacent if they are in the same group.
  //           This decreases the work.
  //  - VPointer con: Sorting by VPointer con inside the group allows us to perform a sliding
  //                  window algorithm, to determine adjacent memops efficiently.
  // Since GrowableArray::sort relies on qsort, the sort is not stable on its own. This can lead
  // to worse packing in some cases. To make the sort stable, our last cmp criterion is the
  // original index, i.e. the position in the memops array before sorting.
  memops.sort(MemOp::cmp_by_group_and_con_and_original_index);

#ifndef PRODUCT
  if (is_trace_superword_adjacent_memops()) {
    tty->print_cr("\nSuperWord::create_adjacent_memop_pairs:");
  }
#endif

  create_adjacent_memop_pairs_in_all_groups(memops);

#ifndef PRODUCT
  if (is_trace_superword_packset()) {
    tty->print_cr("\nAfter Superword::create_adjacent_memop_pairs");
    _pairset.print();
  }
#endif
}

// Collect all memops that could potentially be vectorized.
void SuperWord::collect_valid_memops(GrowableArray<MemOp>& memops) const {
  int original_index = 0;
  for_each_mem([&] (MemNode* mem, int bb_idx) {
    const VPointer& p = vpointer(mem);
    if (p.is_valid() &&
        !mem->is_LoadStore() &&
        is_java_primitive(mem->value_basic_type())) {
      memops.append(MemOp(mem, &p, original_index++));
    }
  });
}

// For each group, find the adjacent memops.
void SuperWord::create_adjacent_memop_pairs_in_all_groups(const GrowableArray<MemOp>& memops) {
  int group_start = 0;
  while (group_start < memops.length()) {
    int group_end = find_group_end(memops, group_start);
    create_adjacent_memop_pairs_in_one_group(memops, group_start, group_end);
    group_start = group_end;
  }
}

// Step forward until we find a MemOp of another group, or we reach the end of the array.
int SuperWord::find_group_end(const GrowableArray<MemOp>& memops, int group_start) {
  int group_end = group_start + 1;
  while (group_end < memops.length() &&
         MemOp::cmp_by_group(
           memops.adr_at(group_start),
           memops.adr_at(group_end)
         ) == 0) {
    group_end++;
  }
  return group_end;
}

// Find adjacent memops for a single group, e.g. for all LoadI of the same base, invar, etc.
// Create pairs and add them to the pairset.
void SuperWord::create_adjacent_memop_pairs_in_one_group(const GrowableArray<MemOp>& memops, const int group_start, const int group_end) {
#ifndef PRODUCT
  if (is_trace_superword_adjacent_memops()) {
    tty->print_cr(" group:");
    for (int i = group_start; i < group_end; i++) {
      const MemOp& memop = memops.at(i);
      tty->print("  ");
      memop.mem()->dump();
      tty->print("  ");
      memop.vpointer().print_on(tty);
    }
  }
#endif

  MemNode* first = memops.at(group_start).mem();
  const int element_size = data_size(first);

  // For each ref in group: find others that can be paired:
  for (int i = group_start; i < group_end; i++) {
    const VPointer& p1  = memops.at(i).vpointer();
    MemNode* mem1 = memops.at(i).mem();

    bool found = false;
    // For each ref in group with larger or equal offset:
    for (int j = i + 1; j < group_end; j++) {
      const VPointer& p2  = memops.at(j).vpointer();
      MemNode* mem2 = memops.at(j).mem();
      assert(mem1 != mem2, "look only at pair of different memops");

      // Check for correct distance.
      assert(data_size(mem1) == element_size, "all nodes in group must have the same element size");
      assert(data_size(mem2) == element_size, "all nodes in group must have the same element size");
      assert(p1.con() <= p2.con(), "must be sorted by offset");
      if (p1.con() + element_size > p2.con()) { continue; }
      if (p1.con() + element_size < p2.con()) { break; }

      // Only allow nodes from same origin idx to be packed (see CompileCommand Option Vectorize)
      if (_do_vector_loop && !same_origin_idx(mem1, mem2)) { continue; }

      if (!can_pack_into_pair(mem1, mem2)) { continue; }

#ifndef PRODUCT
      if (is_trace_superword_adjacent_memops()) {
        if (found) {
          tty->print_cr(" WARNING: multiple pairs with the same node. Ignored pairing:");
        } else {
          tty->print_cr(" pair:");
        }
        tty->print("  ");
        p1.print_on(tty);
        tty->print("  ");
        p2.print_on(tty);
      }
#endif

      if (!found) {
        _pairset.add_pair(mem1, mem2);
      }
    }
  }
}

void VLoopMemorySlices::find_memory_slices() {
  assert(_heads.is_empty(), "not yet computed");
  assert(_tails.is_empty(), "not yet computed");
  CountedLoopNode* cl = _vloop.cl();

  // Iterate over all memory phis
  for (DUIterator_Fast imax, i = cl->fast_outs(imax); i < imax; i++) {
    PhiNode* phi = cl->fast_out(i)->isa_Phi();
    if (phi != nullptr && _vloop.in_bb(phi) && phi->is_memory_phi()) {
      Node* phi_tail = phi->in(LoopNode::LoopBackControl);
      if (phi_tail != phi->in(LoopNode::EntryControl)) {
        _heads.push(phi);
        _tails.push(phi_tail->as_Mem());
      }
    }
  }

  NOT_PRODUCT( if (_vloop.is_trace_memory_slices()) { print(); } )
}

#ifndef PRODUCT
void VLoopMemorySlices::print() const {
  tty->print_cr("\nVLoopMemorySlices::print: %s",
                heads().length() > 0 ? "" : "NONE");
  for (int m = 0; m < heads().length(); m++) {
    tty->print("%6d ", m);  heads().at(m)->dump();
    tty->print("       ");  tails().at(m)->dump();
  }
}
#endif

// Get all memory nodes of a slice, in reverse order
void VLoopMemorySlices::get_slice_in_reverse_order(PhiNode* head, MemNode* tail, GrowableArray<MemNode*> &slice) const {
  assert(slice.is_empty(), "start empty");
  Node* n = tail;
  Node* prev = nullptr;
  while (true) {
    assert(_vloop.in_bb(n), "must be in block");
    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* out = n->fast_out(i);
      if (out->is_Load()) {
        if (_vloop.in_bb(out)) {
          slice.push(out->as_Load());
        }
      } else {
        // FIXME
        if (out->is_MergeMem() && !_vloop.in_bb(out)) {
          // Either unrolling is causing a memory edge not to disappear,
          // or need to run igvn.optimize() again before SLP
        } else if (out->is_memory_phi() && !_vloop.in_bb(out)) {
          // Ditto.  Not sure what else to check further.
        } else {
          assert(out == prev || prev == nullptr, "no branches off of store slice");
        }
      }//else
    }//for
    if (n == head) { break; }
    slice.push(n->as_Mem());
    prev = n;
    assert(n->is_Mem(), "unexpected node %s", n->Name());
    n = n->in(MemNode::Memory);
  }

#ifndef PRODUCT
  if (_vloop.is_trace_memory_slices()) {
    tty->print_cr("\nVLoopMemorySlices::get_slice_in_reverse_order:");
    head->dump();
    for (int j = slice.length() - 1; j >= 0 ; j--) {
      slice.at(j)->dump();
    }
  }
#endif
}

// Check if two nodes can be packed into a pair.
bool SuperWord::can_pack_into_pair(Node* s1, Node* s2) {

  // Do not use superword for non-primitives
  BasicType bt1 = velt_basic_type(s1);
  BasicType bt2 = velt_basic_type(s2);
  if(!is_java_primitive(bt1) || !is_java_primitive(bt2))
    return false;
  if (Matcher::max_vector_size_auto_vectorization(bt1) < 2) {
    return false; // No vectors for this type
  }

  // Forbid anything that looks like a PopulateIndex to be packed. It does not need to be packed,
  // and will still be vectorized by SuperWordVTransformBuilder::get_or_make_vtnode_vector_input_at_index.
  if (isomorphic(s1, s2) && !is_populate_index(s1, s2)) {
    if ((independent(s1, s2) && have_similar_inputs(s1, s2)) || reduction(s1, s2)) {
      if (!_pairset.is_left(s1) && !_pairset.is_right(s2)) {
        if (!s1->is_Mem() || are_adjacent_refs(s1, s2)) {
          return true;
        }
      }
    }
  }
  return false;
}

//------------------------------are_adjacent_refs---------------------------
// Is s1 immediately before s2 in memory?
bool SuperWord::are_adjacent_refs(Node* s1, Node* s2) const {
  if (!s1->is_Mem() || !s2->is_Mem()) return false;
  if (!in_bb(s1)    || !in_bb(s2))    return false;

  // Do not use superword for non-primitives
  if (!is_java_primitive(s1->as_Mem()->value_basic_type()) ||
      !is_java_primitive(s2->as_Mem()->value_basic_type())) {
    return false;
  }

  // Adjacent memory references must be on the same slice.
  if (!same_memory_slice(s1->as_Mem(), s2->as_Mem())) {
    return false;
  }

  const VPointer& p1 = vpointer(s1->as_Mem());
  const VPointer& p2 = vpointer(s2->as_Mem());
  return p1.is_adjacent_to_and_before(p2);
}

//------------------------------isomorphic---------------------------
// Are s1 and s2 similar?
bool SuperWord::isomorphic(Node* s1, Node* s2) {
  if (s1->Opcode() != s2->Opcode() ||
      s1->req() != s2->req() ||
      !same_velt_type(s1, s2) ||
      (s1->is_Bool() && s1->as_Bool()->_test._test != s2->as_Bool()->_test._test)) {
    return false;
  }

  Node* s1_ctrl = s1->in(0);
  Node* s2_ctrl = s2->in(0);
  // If the control nodes are equivalent, no further checks are required to test for isomorphism.
  if (s1_ctrl == s2_ctrl) {
    return true;
  } else {
    // If the control nodes are not invariant for the loop, fail isomorphism test.
    const bool s1_ctrl_inv = (s1_ctrl == nullptr) || lpt()->is_invariant(s1_ctrl);
    const bool s2_ctrl_inv = (s2_ctrl == nullptr) || lpt()->is_invariant(s2_ctrl);
    return s1_ctrl_inv && s2_ctrl_inv;
  }
}

// Look for pattern n1 = (iv + c) and n2 = (iv + c + 1), which may lead to
// PopulateIndex vector node. We skip the pack creation of these nodes. They
// will be vectorized by SuperWordVTransformBuilder::get_or_make_vtnode_vector_input_at_index.
bool SuperWord::is_populate_index(const Node* n1, const Node* n2) const {
  return n1->is_Add() &&
         n2->is_Add() &&
         n1->in(1) == iv() &&
         n2->in(1) == iv() &&
         n1->in(2)->is_Con() &&
         n2->in(2)->is_Con() &&
         n2->in(2)->get_int() - n1->in(2)->get_int() == 1;
}

// Is there no data path from s1 to s2 or s2 to s1?
bool VLoopDependencyGraph::independent(Node* s1, Node* s2) const {
  int d1 = depth(s1);
  int d2 = depth(s2);

  if (d1 == d2) {
    // Same depth:
    //  1) same node       -> dependent
    //  2) different nodes -> same level implies there is no path
    return s1 != s2;
  }

  // Traversal starting at the deeper node to find the shallower one.
  Node* deep    = d1 > d2 ? s1 : s2;
  Node* shallow = d1 > d2 ? s2 : s1;
  int min_d = MIN2(d1, d2); // prune traversal at min_d

  ResourceMark rm;
  Unique_Node_List worklist;
  worklist.push(deep);
  for (uint i = 0; i < worklist.size(); i++) {
    Node* n = worklist.at(i);
    for (PredsIterator preds(*this, n); !preds.done(); preds.next()) {
      Node* pred = preds.current();
      if (_vloop.in_bb(pred) && depth(pred) >= min_d) {
        if (pred == shallow) {
          return false; // found it -> dependent
        }
        worklist.push(pred);
      }
    }
  }
  return true; // not found -> independent
}

// Are all nodes in nodes list mutually independent?
// We could query independent(s1, s2) for all pairs, but that results
// in O(size * size) graph traversals. We can do it all in one BFS!
// Start the BFS traversal at all nodes from the nodes list. Traverse
// Preds recursively, for nodes that have at least depth min_d, which
// is the smallest depth of all nodes from the nodes list. Once we have
// traversed all those nodes, and have not found another node from the
// nodes list, we know that all nodes in the nodes list are independent.
bool VLoopDependencyGraph::mutually_independent(const Node_List* nodes) const {
  ResourceMark rm;
  Unique_Node_List worklist;
  VectorSet nodes_set;
  int min_d = depth(nodes->at(0));
  for (uint k = 0; k < nodes->size(); k++) {
    Node* n = nodes->at(k);
    min_d = MIN2(min_d, depth(n));
    worklist.push(n); // start traversal at all nodes in nodes list
    nodes_set.set(_body.bb_idx(n));
  }
  for (uint i = 0; i < worklist.size(); i++) {
    Node* n = worklist.at(i);
    for (PredsIterator preds(*this, n); !preds.done(); preds.next()) {
      Node* pred = preds.current();
      if (_vloop.in_bb(pred) && depth(pred) >= min_d) {
        if (nodes_set.test(_body.bb_idx(pred))) {
          return false; // found one -> dependent
        }
        worklist.push(pred);
      }
    }
  }
  return true; // not found -> independent
}

//--------------------------have_similar_inputs-----------------------
// For a node pair (s1, s2) which is isomorphic and independent,
// do s1 and s2 have similar input edges?
bool SuperWord::have_similar_inputs(Node* s1, Node* s2) {
  // assert(isomorphic(s1, s2) == true, "check isomorphic");
  // assert(independent(s1, s2) == true, "check independent");
  if (s1->req() > 1 && !s1->is_Store() && !s1->is_Load()) {
    for (uint i = 1; i < s1->req(); i++) {
      Node* s1_in = s1->in(i);
      Node* s2_in = s2->in(i);
      if (s1_in->is_Phi() && s2_in->is_Add() && s2_in->in(1) == s1_in) {
        // Special handling for expressions with loop iv, like "b[i] = a[i] * i".
        // In this case, one node has an input from the tripcount iv and another
        // node has an input from iv plus an offset.
        if (!s1_in->as_Phi()->is_tripcount(T_INT)) return false;
      } else {
        if (s1_in->Opcode() != s2_in->Opcode()) return false;
      }
    }
  }
  return true;
}

bool VLoopReductions::is_marked_reduction_pair(const Node* s1, const Node* s2) const {
  if (is_marked_reduction(s1) &&
      is_marked_reduction(s2)) {
    // This is an ordered set, so s1 should define s2
    for (DUIterator_Fast imax, i = s1->fast_outs(imax); i < imax; i++) {
      Node* t1 = s1->fast_out(i);
      if (t1 == s2) {
        // both nodes are reductions and connected
        return true;
      }
    }
  }
  return false;
}

// Extend pairset by following use->def and def->use links from pair members.
void SuperWord::extend_pairset_with_more_pairs_by_following_use_and_def() {
  bool changed;
  do {
    changed = false;
    // Iterate the pairs in insertion order.
    for (int i = 0; i < _pairset.length(); i++) {
      Node* left  = _pairset.left_at_in_insertion_order(i);
      Node* right = _pairset.right_at_in_insertion_order(i);
      changed |= extend_pairset_with_more_pairs_by_following_def(left, right);
      changed |= extend_pairset_with_more_pairs_by_following_use(left, right);
    }
  } while (changed);

  // During extend_pairset_with_more_pairs_by_following_use, we may have re-ordered the
  // inputs of some nodes, when calling order_inputs_of_uses_to_match_def_pair. If a def
  // node has multiple uses, we may have re-ordered some of the inputs one use after
  // packing another use with the old order. Now that we have all pairs, we must ensure
  // that the order between the pairs is matching again. Since the PairSetIterator visits
  // all pair-chains from left-to-right, we essencially impose the order of the first
  // element on all other elements in the pair-chain.
  for (PairSetIterator pair(_pairset); !pair.done(); pair.next()) {
    Node* left  = pair.left();
    Node* right = pair.right();
    order_inputs_of_all_use_pairs_to_match_def_pair(left, right);
  }

#ifndef PRODUCT
  if (is_trace_superword_packset()) {
    tty->print_cr("\nAfter Superword::extend_pairset_with_more_pairs_by_following_use_and_def");
    _pairset.print();
  }
#endif
}

bool SuperWord::extend_pairset_with_more_pairs_by_following_def(Node* s1, Node* s2) {
  assert(_pairset.is_pair(s1, s2), "(s1, s2) must be a pair");
  assert(s1->req() == s2->req(), "just checking");

  if (s1->is_Load()) return false;

  bool changed = false;
  int start = s1->is_Store() ? MemNode::ValueIn   : 1;
  int end   = s1->is_Store() ? MemNode::ValueIn+1 : s1->req();
  for (int j = start; j < end; j++) {
    Node* t1 = s1->in(j);
    Node* t2 = s2->in(j);
    if (!in_bb(t1) || !in_bb(t2) || t1->is_Mem() || t2->is_Mem())  {
      // Only follow non-memory nodes in block - we do not want to resurrect misaligned packs.
      continue;
    }
    if (can_pack_into_pair(t1, t2)) {
      if (estimate_cost_savings_when_packing_as_pair(t1, t2) >= 0) {
        _pairset.add_pair(t1, t2);
        changed = true;
      }
    }
  }
  return changed;
}

// Note: we only extend with a single pair (the one with most savings) for every call. Since we keep
//       calling this method as long as there are some changes, we will eventually pack all pairs that
//       can be packed.
bool SuperWord::extend_pairset_with_more_pairs_by_following_use(Node* s1, Node* s2) {
  assert(_pairset.is_pair(s1, s2), "(s1, s2) must be a pair");
  assert(s1->req() == s2->req(), "just checking");

  if (s1->is_Store()) return false;

  int savings = -1;
  Node* u1 = nullptr;
  Node* u2 = nullptr;
  for (DUIterator_Fast imax, i = s1->fast_outs(imax); i < imax; i++) {
    Node* t1 = s1->fast_out(i);
    if (!in_bb(t1) || t1->is_Mem()) {
      // Only follow non-memory nodes in block - we do not want to resurrect misaligned packs.
      continue;
    }
    for (DUIterator_Fast jmax, j = s2->fast_outs(jmax); j < jmax; j++) {
      Node* t2 = s2->fast_out(j);
      if (!in_bb(t2) || t2->is_Mem()) {
        // Only follow non-memory nodes in block - we do not want to resurrect misaligned packs.
        continue;
      }
      if (t2->Opcode() == Op_AddI && t2 == cl()->incr()) continue; // don't mess with the iv
      if (order_inputs_of_uses_to_match_def_pair(s1, s2, t1, t2) != PairOrderStatus::Ordered) { continue; }
      if (can_pack_into_pair(t1, t2)) {
        int my_savings = estimate_cost_savings_when_packing_as_pair(t1, t2);
        if (my_savings > savings) {
          savings = my_savings;
          u1 = t1;
          u2 = t2;
        }
      }
    }
  }
  if (savings >= 0) {
    _pairset.add_pair(u1, u2);
    return true; // changed
  }
  return false; // no change
}

// For a pair (def1, def2), find all use packs (use1, use2), and ensure that their inputs have an order
// that matches the (def1, def2) pair.
void SuperWord::order_inputs_of_all_use_pairs_to_match_def_pair(Node* def1, Node* def2) {
  assert(_pairset.is_pair(def1, def2), "(def1, def2) must be a pair");

  if (def1->is_Store()) return;

  // reductions are always managed beforehand
  if (is_marked_reduction(def1)) return;

  for (DUIterator_Fast imax, i = def1->fast_outs(imax); i < imax; i++) {
    Node* use1 = def1->fast_out(i);

    // Only allow operand swap on commuting operations
    if (!use1->is_Add() && !use1->is_Mul() && !VectorNode::is_muladds2i(use1)) {
      break;
    }

    // Find pair (use1, use2)
    Node* use2 = _pairset.get_right_or_null_for(use1);
    if (use2 == nullptr) { break; }

    order_inputs_of_uses_to_match_def_pair(def1, def2, use1, use2);
  }
}

// For a def-pair (def1, def2), and their use-nodes (use1, use2):
// Ensure that the input order of (use1, use2) matches the order of (def1, def2).
//
// We have different cases:
//
// 1. Reduction (use1, use2): must always reduce left-to-right. Make sure that we have pattern:
//
//    phi/reduction x1  phi/reduction x2                    phi/reduction x1
//                | |               | |    and hopefully:               | |
//                use1              use2                                use1 x2
//                                                                         | |
//                                                                         use2
//
// 2: Commutative operations, just as Add/Mul and their subclasses: we can try to swap edges:
//
//     def1 x1   x2 def2           def1 x1   def2 x2
//        | |     | |       ==>       | |       | |
//        use1    use2                use1      use2
//
// 3: MulAddS2I (use1, use2): we can try to swap edges:
//
//    (x1 * x2) + (x3 * x4)    ==>  3.a: (x2 * x1) + (x4 * x3)
//                                  3.b: (x4 * x3) + (x2 * x1)
//                                  3.c: (x3 * x4) + (x1 * x2)
//
//    Note: MulAddS2I with its 4 inputs is too complicated, if there is any mismatch, we always
//          return PairOrderStatus::Unknown.
//          Therefore, extend_pairset_with_more_pairs_by_following_use cannot extend to MulAddS2I,
//          but there is a chance that extend_pairset_with_more_pairs_by_following_def can do it.
//
// 4: Otherwise, check if the inputs of (use1, use2) already match (def1, def2), i.e. for all input indices i:
//
//    use1->in(i) == def1 || use2->in(i) == def2   ->    use1->in(i) == def1 && use2->in(i) == def2
//
SuperWord::PairOrderStatus SuperWord::order_inputs_of_uses_to_match_def_pair(Node* def1, Node* def2, Node* use1, Node* use2) {
  assert(_pairset.is_pair(def1, def2), "(def1, def2) must be a pair");

  // 1. Reduction
  if (is_marked_reduction(use1) && is_marked_reduction(use2)) {
    Node* use1_in2 = use1->in(2);
    if (use1_in2->is_Phi() || is_marked_reduction(use1_in2)) {
      use1->swap_edges(1, 2);
    }
    Node* use2_in2 = use2->in(2);
    if (use2_in2->is_Phi() || is_marked_reduction(use2_in2)) {
      use2->swap_edges(1, 2);
    }
    return PairOrderStatus::Ordered;
  }

  uint ct = use1->req();
  if (ct != use2->req()) { return PairOrderStatus::Unordered; };
  uint i1 = 0;
  uint i2 = 0;
  do {
    for (i1++; i1 < ct; i1++) { if (use1->in(i1) == def1) { break; } }
    for (i2++; i2 < ct; i2++) { if (use2->in(i2) == def2) { break; } }
    if (i1 != i2) {
      if ((i1 == (3-i2)) && (use2->is_Add() || use2->is_Mul())) {
        // 2. Commutative: swap edges, and hope the other position matches too.
        use2->swap_edges(i1, i2);
      } else if (VectorNode::is_muladds2i(use2) && use1 != use2) {
        // 3.a/b: MulAddS2I.
        if (i1 == 5 - i2) { // ((i1 == 3 && i2 == 2) || (i1 == 2 && i2 == 3) || (i1 == 1 && i2 == 4) || (i1 == 4 && i2 == 1))
          use2->swap_edges(1, 2);
          use2->swap_edges(3, 4);
        }
        if (i1 == 3 - i2 || i1 == 7 - i2) { // ((i1 == 1 && i2 == 2) || (i1 == 2 && i2 == 1) || (i1 == 3 && i2 == 4) || (i1 == 4 && i2 == 3))
          use2->swap_edges(2, 3);
          use2->swap_edges(1, 4);
        }
        return PairOrderStatus::Unknown;
      } else {
        // 4. The inputs are not ordered, and we cannot do anything about it.
        return PairOrderStatus::Unordered;
      }
    } else if (i1 == i2 && VectorNode::is_muladds2i(use2) && use1 != use2) {
      // 3.c: MulAddS2I.
      use2->swap_edges(1, 3);
      use2->swap_edges(2, 4);
      return PairOrderStatus::Unknown;
    }
  } while (i1 < ct);

  // 4. All inputs match.
  return PairOrderStatus::Ordered;
}

// Estimate the savings from executing s1 and s2 as a pair.
int SuperWord::estimate_cost_savings_when_packing_as_pair(const Node* s1, const Node* s2) const {
  int save_in = 2 - 1; // 2 operations per instruction in packed form

  const int adjacent_profit = 2;
  auto pack_cost       = [&] (const int size) { return size; };
  auto unpack_cost     = [&] (const int size) { return size; };

  // inputs
  for (uint i = 1; i < s1->req(); i++) {
    Node* x1 = s1->in(i);
    Node* x2 = s2->in(i);
    if (x1 != x2) {
      if (are_adjacent_refs(x1, x2)) {
        save_in += adjacent_profit;
      } else if (!_pairset.is_pair(x1, x2)) {
        save_in -= pack_cost(2);
      } else {
        save_in += unpack_cost(2);
      }
    }
  }

  // uses of result
  uint number_of_packed_use_pairs = 0;
  int save_use = 0;
  for (DUIterator_Fast imax, i = s1->fast_outs(imax); i < imax; i++) {
    Node* use1 = s1->fast_out(i);

    // Find pair (use1, use2)
    Node* use2 = _pairset.get_right_or_null_for(use1);
    if (use2 == nullptr) { continue; }

    for (DUIterator_Fast kmax, k = s2->fast_outs(kmax); k < kmax; k++) {
      if (use2 == s2->fast_out(k)) {
        // We have pattern:
        //
        //   s1    s2
        //    |    |
        // [use1, use2]
        //
        number_of_packed_use_pairs++;
        if (are_adjacent_refs(use1, use2)) {
          save_use += adjacent_profit;
        }
      }
    }
  }

  if (number_of_packed_use_pairs < s1->outcnt()) save_use += unpack_cost(1);
  if (number_of_packed_use_pairs < s2->outcnt()) save_use += unpack_cost(1);

  return MAX2(save_in, save_use);
}

// Combine pairs (n1, n2), (n2, n3), ... into pack (n1, n2, n3 ...)
void SuperWord::combine_pairs_to_longer_packs() {
#ifdef ASSERT
  assert(!_pairset.is_empty(), "pairset not empty");
  assert(_packset.is_empty(), "packset not empty");
#endif

  // Iterate pair-chain by pair-chain, each from left-most to right-most.
  Node_List* pack = nullptr;
  for (PairSetIterator pair(_pairset); !pair.done(); pair.next()) {
    Node* left  = pair.left();
    Node* right = pair.right();
    if (_pairset.is_left_in_a_left_most_pair(left)) {
      assert(pack == nullptr, "no unfinished pack");
      pack = new (arena()) Node_List(arena());
      pack->push(left);
    }
    assert(pack != nullptr, "must have unfinished pack");
    pack->push(right);
    if (_pairset.is_right_in_a_right_most_pair(right)) {
      _packset.add_pack(pack);
      pack = nullptr;
    }
  }
  assert(pack == nullptr, "no unfinished pack");

  assert(!_packset.is_empty(), "must have combined some packs");

#ifndef PRODUCT
  if (is_trace_superword_packset()) {
    tty->print_cr("\nAfter Superword::combine_pairs_to_longer_packs");
    _packset.print();
  }
#endif
}

SplitStatus PackSet::split_pack(const char* split_name,
                                Node_List* pack,
                                SplitTask task)
{
  uint pack_size = pack->size();

  if (task.is_unchanged()) {
    return SplitStatus::make_unchanged(pack);
  }

  if (task.is_rejected()) {
#ifndef PRODUCT
      if (is_trace_superword_rejections()) {
        tty->cr();
        tty->print_cr("WARNING: Removed pack: %s:", task.message());
        print_pack(pack);
      }
#endif
    unmap_all_nodes_in_pack(pack);
    return SplitStatus::make_rejected();
  }

  uint split_size = task.split_size();
  assert(0 < split_size && split_size < pack_size, "split_size must be in range");

  // Split the size
  uint new_size = split_size;
  uint old_size = pack_size - new_size;

#ifndef PRODUCT
  if (is_trace_superword_packset()) {
    tty->cr();
    tty->print_cr("INFO: splitting pack (sizes: %d %d): %s:",
                  old_size, new_size, task.message());
    print_pack(pack);
  }
#endif

  // Are both sizes too small to be a pack?
  if (old_size < 2 && new_size < 2) {
    assert(old_size == 1 && new_size == 1, "implied");
#ifndef PRODUCT
      if (is_trace_superword_rejections()) {
        tty->cr();
        tty->print_cr("WARNING: Removed size 2 pack, cannot be split: %s:", task.message());
        print_pack(pack);
      }
#endif
    unmap_all_nodes_in_pack(pack);
    return SplitStatus::make_rejected();
  }

  // Just pop off a single node?
  if (new_size < 2) {
    assert(new_size == 1 && old_size >= 2, "implied");
    Node* n = pack->pop();
    unmap_node_in_pack(n);
#ifndef PRODUCT
      if (is_trace_superword_rejections()) {
        tty->cr();
        tty->print_cr("WARNING: Removed node from pack, because of split: %s:", task.message());
        n->dump();
      }
#endif
    return SplitStatus::make_modified(pack);
  }

  // Just remove a single node at front?
  if (old_size < 2) {
    assert(old_size == 1 && new_size >= 2, "implied");
    Node* n = pack->at(0);
    pack->remove(0);
    unmap_node_in_pack(n);
#ifndef PRODUCT
      if (is_trace_superword_rejections()) {
        tty->cr();
        tty->print_cr("WARNING: Removed node from pack, because of split: %s:", task.message());
        n->dump();
      }
#endif
    return SplitStatus::make_modified(pack);
  }

  // We will have two packs
  assert(old_size >= 2 && new_size >= 2, "implied");
  Node_List* new_pack = new Node_List(new_size);

  for (uint i = 0; i < new_size; i++) {
    Node* n = pack->at(old_size + i);
    new_pack->push(n);
    remap_node_in_pack(n, new_pack);
  }

  for (uint i = 0; i < new_size; i++) {
    pack->pop();
  }

  // We assume that new_pack is more "stable" (i.e. will have to be split less than new_pack).
  // Put "pack" second, so that we insert it later in the list, and iterate over it again sooner.
  return SplitStatus::make_split(new_pack, pack);
}

template <typename SplitStrategy>
void PackSet::split_packs(const char* split_name,
                          SplitStrategy strategy) {
  bool changed;
  do {
    changed = false;
    int new_packset_length = 0;
    for (int i = 0; i < _packs.length(); i++) {
      Node_List* pack = _packs.at(i);
      assert(pack != nullptr && pack->size() >= 2, "no nullptr, at least size 2");
      SplitTask task = strategy(pack);
      SplitStatus status = split_pack(split_name, pack, task);
      changed |= !status.is_unchanged();
      Node_List* first_pack = status.first_pack();
      Node_List* second_pack = status.second_pack();
      _packs.at_put(i, nullptr); // take out pack
      if (first_pack != nullptr) {
        // The first pack can be put at the current position
        assert(i >= new_packset_length, "only move packs down");
        _packs.at_put(new_packset_length++, first_pack);
      }
      if (second_pack != nullptr) {
        // The second node has to be appended at the end
        _packs.append(second_pack);
      }
    }
    _packs.trunc_to(new_packset_length);
  } while (changed);

#ifndef PRODUCT
  if (is_trace_superword_packset()) {
    tty->print_cr("\nAfter %s", split_name);
    print();
  }
#endif
}

// Split packs at boundaries where left and right have different use or def packs.
void SuperWord::split_packs_at_use_def_boundaries() {
  auto split_strategy = [&](const Node_List* pack) {
    uint pack_size = pack->size();
    uint boundary = find_use_def_boundary(pack);
    assert(boundary < pack_size, "valid boundary %d", boundary);
    if (boundary != 0) {
      return SplitTask::make_split(pack_size - boundary, "found a use/def boundary");
    }
    return SplitTask::make_unchanged();
  };
  _packset.split_packs("SuperWord::split_packs_at_use_def_boundaries", split_strategy);
}

// Split packs that are only implemented with a smaller pack size. Also splits packs
// such that they eventually have power of 2 size.
void SuperWord::split_packs_only_implemented_with_smaller_size() {
  auto split_strategy = [&](const Node_List* pack) {
    uint pack_size = pack->size();
    uint implemented_size = max_implemented_size(pack);
    if (implemented_size == 0)  {
      return SplitTask::make_rejected("not implemented at any smaller size");
    }
    assert(is_power_of_2(implemented_size), "power of 2 size or zero: %d", implemented_size);
    if (implemented_size != pack_size) {
      return SplitTask::make_split(implemented_size, "only implemented at smaller size");
    }
    return SplitTask::make_unchanged();
  };
  _packset.split_packs("SuperWord::split_packs_only_implemented_with_smaller_size", split_strategy);
}

// Split packs that have a mutual dependency, until all packs are mutually_independent.
void SuperWord::split_packs_to_break_mutual_dependence() {
  auto split_strategy = [&](const Node_List* pack) {
    uint pack_size = pack->size();
    assert(is_power_of_2(pack_size), "ensured by earlier splits %d", pack_size);
    if (!is_marked_reduction(pack->at(0)) &&
        !mutually_independent(pack)) {
      // As a best guess, we split the pack in half. This way, we iteratively make the
      // packs smaller, until there is no dependency.
      return SplitTask::make_split(pack_size >> 1, "was not mutually independent");
    }
    return SplitTask::make_unchanged();
  };
  _packset.split_packs("SuperWord::split_packs_to_break_mutual_dependence", split_strategy);
}

template <typename FilterPredicate>
void PackSet::filter_packs(const char* filter_name,
                             const char* rejection_message,
                             FilterPredicate filter) {
  auto split_strategy = [&](const Node_List* pack) {
    if (filter(pack)) {
      return SplitTask::make_unchanged();
    } else {
      return SplitTask::make_rejected(rejection_message);
    }
  };
  split_packs(filter_name, split_strategy);
}

void SuperWord::filter_packs_for_power_of_2_size() {
  auto filter = [&](const Node_List* pack) {
    return is_power_of_2(pack->size());
  };
  _packset.filter_packs("SuperWord::filter_packs_for_power_of_2_size",
                        "size is not a power of 2", filter);
}

// We know that the nodes in a pair pack were independent - this gives us independence
// at distance 1. But now that we may have more than 2 nodes in a pack, we need to check
// if they are all mutually independent. If there is a dependence we remove the pack.
// This is better than giving up completely - we can have partial vectorization if some
// are rejected and others still accepted.
//
// Examples with dependence at distance 1 (pack pairs are not created):
// for (int i ...) { v[i + 1] = v[i] + 5; }
// for (int i ...) { v[i] = v[i - 1] + 5; }
//
// Example with independence at distance 1, but dependence at distance 2 (pack pairs are
// created and we need to filter them out now):
// for (int i ...) { v[i + 2] = v[i] + 5; }
// for (int i ...) { v[i] = v[i - 2] + 5; }
//
// Note: dependencies are created when a later load may reference the same memory location
// as an earlier store. This happens in "read backward" or "store forward" cases. On the
// other hand, "read forward" or "store backward" cases do not have such dependencies:
// for (int i ...) { v[i] = v[i + 1] + 5; }
// for (int i ...) { v[i - 1] = v[i] + 5; }
void SuperWord::filter_packs_for_mutual_independence() {
  auto filter = [&](const Node_List* pack) {
    // reductions are trivially connected
    return is_marked_reduction(pack->at(0)) ||
           mutually_independent(pack);
  };
  _packset.filter_packs("SuperWord::filter_packs_for_mutual_independence",
                        "found dependency between nodes at distance greater than 1", filter);
}

// Find the set of alignment solutions for load/store pack.
const AlignmentSolution* SuperWord::pack_alignment_solution(const Node_List* pack) {
  assert(pack != nullptr && (pack->at(0)->is_Load() || pack->at(0)->is_Store()), "only load/store packs");

  const MemNode* mem_ref = pack->at(0)->as_Mem();
  const VPointer& mem_ref_p = vpointer(mem_ref);
  const CountedLoopEndNode* pre_end = _vloop.pre_loop_end();
  assert(pre_end->stride_is_con(), "pre loop stride is constant");

  AlignmentSolver solver(mem_ref_p,
                         pack->at(0)->as_Mem(),
                         pack->size(),
                         pre_end->init_trip(),
                         pre_end->stride_con(),
                         iv_stride(),
                         _vloop.are_speculative_checks_possible()
                         DEBUG_ONLY(COMMA is_trace_align_vector()));
  return solver.solve();
}

// Ensure all packs are aligned, if AlignVector is on.
// Find an alignment solution: find the set of pre_iter that memory align all packs.
// Start with the maximal set (pre_iter >= 0) and filter it with the constraints
// that the packs impose. Remove packs that do not have a compatible solution.
void SuperWord::filter_packs_for_alignment() {
  // We do not need to filter if no alignment is required.
  if (!VLoop::vectors_should_be_aligned()) {
    return;
  }

#ifndef PRODUCT
  if (is_trace_superword_info() || is_trace_align_vector()) {
    tty->print_cr("\nSuperWord::filter_packs_for_alignment:");
  }
#endif

  ResourceMark rm;

  // Start with trivial (unconstrained) solution space
  AlignmentSolution const* current = new TrivialAlignmentSolution();
  int mem_ops_count = 0;
  int mem_ops_rejected = 0;

  auto filter = [&](const Node_List* pack) {
    // Only memops need to be aligned.
    if (!pack->at(0)->is_Load() &&
        !pack->at(0)->is_Store()) {
      return true; // accept all non memops
    }

    mem_ops_count++;
    const AlignmentSolution* s = pack_alignment_solution(pack);
    const AlignmentSolution* intersect = current->filter(s);

#ifndef PRODUCT
    if (is_trace_align_vector()) {
      tty->print("  solution for pack:         ");
      s->print();
      tty->print("  intersection with current: ");
      intersect->print();
    }
#endif
    if (intersect->is_empty()) {
      mem_ops_rejected++;
      return false; // reject because of empty solution
    }

    current = intersect;
    return true; // accept because of non-empty solution
  };

  _packset.filter_packs("SuperWord::filter_packs_for_alignment",
                        "rejected by AlignVector (strict alignment requirement)", filter);

#ifndef PRODUCT
  if (is_trace_superword_info() || is_trace_align_vector()) {
    tty->print("\n final solution: ");
    current->print();
    tty->print_cr(" rejected mem_ops packs: %d of %d", mem_ops_rejected, mem_ops_count);
    tty->cr();
  }
#endif

  assert(!current->is_empty(), "solution must be non-empty");
  if (current->is_constrained()) {
    // Solution is constrained (not trivial)
    // -> must change pre-limit to achieve alignment
    MemNode const* mem = current->as_constrained()->mem_ref();
    Node_List* pack = get_pack(mem);
    assert(pack != nullptr, "memop of final solution must still be packed");
    _mem_ref_for_main_loop_alignment = mem;
    _aw_for_main_loop_alignment = pack->size() * mem->memory_size();
  }
}

// Remove packs that are not implemented
void SuperWord::filter_packs_for_implemented() {
  auto filter = [&](const Node_List* pack) {
    return implemented(pack, pack->size());
  };
  _packset.filter_packs("SuperWord::filter_packs_for_implemented",
                        "Unimplemented", filter);
}

// Remove packs that are not profitable.
void SuperWord::filter_packs_for_profitable() {
  // Count the number of reductions vs other vector ops, for the
  // reduction profitability heuristic.
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* pack = _packset.at(i);
    Node* n = pack->at(0);
    if (is_marked_reduction(n)) {
      _num_reductions++;
    } else {
      _num_work_vecs++;
    }
  }

  // Remove packs that are not profitable
  auto filter = [&](const Node_List* pack) {
    return profitable(pack);
  };
  _packset.filter_packs("Superword::filter_packs_for_profitable",
                        "not profitable", filter);
}

// Can code be generated for the pack, restricted to size nodes?
bool SuperWord::implemented(const Node_List* pack, const uint size) const {
  assert(size >= 2 && size <= pack->size() && is_power_of_2(size), "valid size");
  bool retValue = false;
  Node* p0 = pack->at(0);
  if (p0 != nullptr) {
    int opc = p0->Opcode();
    if (is_marked_reduction(p0)) {
      const Type *arith_type = p0->bottom_type();
      // This heuristic predicts that 2-element reductions for INT/LONG are not
      // profitable. This heuristic was added in JDK-8078563. The argument
      // was that reductions are not just a single instruction, but multiple, and
      // hence it is not directly clear that they are profitable. If we only have
      // two elements per vector, then the performance gains from non-reduction
      // vectors are at most going from 2 scalar instructions to 1 vector instruction.
      // But a 2-element reduction vector goes from 2 scalar instructions to
      // 3 instructions (1 shuffle and two reduction ops).
      // However, this optimization assumes that these reductions stay in the loop
      // which may not be true any more in most cases after the introduction of:
      // PhaseIdealLoop::move_unordered_reduction_out_of_loop
      // Hence, this heuristic has room for improvement.
      bool is_two_element_int_or_long_reduction = (size == 2) &&
                                                  (arith_type->basic_type() == T_INT ||
                                                   arith_type->basic_type() == T_LONG);
      if (is_two_element_int_or_long_reduction && AutoVectorizationOverrideProfitability != 2) {
#ifndef PRODUCT
        if (is_trace_superword_rejections()) {
          tty->print_cr("\nPerformance heuristic: 2-element INT/LONG reduction not profitable.");
          tty->print_cr("  Can override with AutoVectorizationOverrideProfitability=2");
        }
#endif
        return false;
      }
      retValue = ReductionNode::implemented(opc, size, arith_type->basic_type());
    } else if (VectorNode::is_convert_opcode(opc)) {
      retValue = VectorCastNode::implemented(opc, size, velt_basic_type(p0->in(1)), velt_basic_type(p0));
    } else if (VectorNode::is_minmax_opcode(opc) && is_subword_type(velt_basic_type(p0))) {
      // Java API for Math.min/max operations supports only int, long, float
      // and double types. Thus, avoid generating vector min/max nodes for
      // integer subword types with superword vectorization.
      // See JDK-8294816 for miscompilation issues with shorts.
      return false;
    } else if (p0->is_Cmp()) {
      // Cmp -> Bool -> Cmove
      retValue = UseVectorCmov;
    } else if (VectorNode::is_scalar_op_that_returns_int_but_vector_op_returns_long(opc)) {
      // Requires extra vector long -> int conversion.
      retValue = VectorNode::implemented(opc, size, T_LONG) &&
                 VectorCastNode::implemented(Op_ConvL2I, size, T_LONG, T_INT);
    } else {
      if (VectorNode::can_use_RShiftI_instead_of_URShiftI(p0, velt_basic_type(p0))) {
        opc = Op_RShiftI;
      }
      retValue = VectorNode::implemented(opc, size, velt_basic_type(p0));
    }
  }
  return retValue;
}

// Find the maximal implemented size smaller or equal to the packs size
uint SuperWord::max_implemented_size(const Node_List* pack) {
  uint size = round_down_power_of_2(pack->size());
  if (implemented(pack, size)) {
    return size;
  } else {
    // Iteratively divide size by 2, and check.
    for (uint s = size >> 1; s >= 2; s >>= 1) {
      if (implemented(pack, s)) {
        return s;
      }
    }
    return 0; // not implementable at all
  }
}

// If the j-th input for all nodes in the pack is the same input: return it, else nullptr.
Node* PackSet::same_inputs_at_index_or_null(const Node_List* pack, const int index) const {
  Node* p0_in = pack->at(0)->in(index);
  for (uint i = 1; i < pack->size(); i++) {
    if (pack->at(i)->in(index) != p0_in) {
      return nullptr; // not same
    }
  }
  return p0_in;
}

VTransformBoolTest PackSet::get_bool_test(const Node_List* bool_pack) const {
  BoolNode* bol = bool_pack->at(0)->as_Bool();
  BoolTest::mask mask = bol->_test._test;
  bool is_negated = false;
  assert(mask == BoolTest::eq ||
         mask == BoolTest::ne ||
         mask == BoolTest::ge ||
         mask == BoolTest::gt ||
         mask == BoolTest::lt ||
         mask == BoolTest::le,
         "Bool should be one of: eq, ne, ge, gt, lt, le");

#ifdef ASSERT
  for (uint j = 0; j < bool_pack->size(); j++) {
    Node* m = bool_pack->at(j);
    assert(m->as_Bool()->_test._test == mask,
           "all bool nodes must have same test");
  }
#endif

  CmpNode* cmp0 = bol->in(1)->as_Cmp();
  assert(get_pack(cmp0) != nullptr, "Bool must have matching Cmp pack");

  if (cmp0->Opcode() == Op_CmpF || cmp0->Opcode() == Op_CmpD) {
    // If we have a Float or Double comparison, we must be careful with
    // handling NaN's correctly. CmpF and CmpD have a return code, as
    // they are based on the java bytecodes fcmpl/dcmpl:
    // -1: cmp_in1 <  cmp_in2, or at least one of the two is a NaN
    //  0: cmp_in1 == cmp_in2  (no NaN)
    //  1: cmp_in1 >  cmp_in2  (no NaN)
    //
    // The "mask" selects which of the [-1, 0, 1] cases lead to "true".
    //
    // Note: ordered   (O) comparison returns "false" if either input is NaN.
    //       unordered (U) comparison returns "true"  if either input is NaN.
    //
    // The VectorMaskCmpNode does a comparison directly on in1 and in2, in the java
    // standard way (all comparisons are ordered, except NEQ is unordered).
    //
    // In the following, "mask" already matches the cmp code for VectorMaskCmpNode:
    //   BoolTest::eq:  Case 0     -> EQ_O
    //   BoolTest::ne:  Case -1, 1 -> NEQ_U
    //   BoolTest::ge:  Case 0, 1  -> GE_O
    //   BoolTest::gt:  Case 1     -> GT_O
    //
    // But the lt and le comparisons must be converted from unordered to ordered:
    //   BoolTest::lt:  Case -1    -> LT_U -> VectorMaskCmp would interpret lt as LT_O
    //   BoolTest::le:  Case -1, 0 -> LE_U -> VectorMaskCmp would interpret le as LE_O
    //
    if (mask == BoolTest::lt || mask == BoolTest::le) {
      // Negating the mask gives us the negated result, since all non-NaN cases are
      // negated, and the unordered (U) comparisons are turned into ordered (O) comparisons.
      //          VectorMaskCmp(LT_U, in1_cmp, in2_cmp)
      // <==> NOT VectorMaskCmp(GE_O, in1_cmp, in2_cmp)
      //          VectorMaskCmp(LE_U, in1_cmp, in2_cmp)
      // <==> NOT VectorMaskCmp(GT_O, in1_cmp, in2_cmp)
      //
      // When a VectorBlend uses the negated mask, it can simply swap its blend-inputs:
      //      VectorBlend(    VectorMaskCmp(LT_U, in1_cmp, in2_cmp), in1_blend, in2_blend)
      // <==> VectorBlend(NOT VectorMaskCmp(GE_O, in1_cmp, in2_cmp), in1_blend, in2_blend)
      // <==> VectorBlend(    VectorMaskCmp(GE_O, in1_cmp, in2_cmp), in2_blend, in1_blend)
      //      VectorBlend(    VectorMaskCmp(LE_U, in1_cmp, in2_cmp), in1_blend, in2_blend)
      // <==> VectorBlend(NOT VectorMaskCmp(GT_O, in1_cmp, in2_cmp), in1_blend, in2_blend)
      // <==> VectorBlend(    VectorMaskCmp(GT_O, in1_cmp, in2_cmp), in2_blend, in1_blend)
      mask = bol->_test.negate();
      is_negated = true;
    }
  }

  return VTransformBoolTest(mask, is_negated);
}

//------------------------------profitable---------------------------
// For pack p, are all operands and all uses (with in the block) vector?
bool SuperWord::profitable(const Node_List* p) const {
  Node* p0 = p->at(0);
  uint start, end;
  VectorNode::vector_operands(p0, &start, &end);

  // Return false if some inputs are not vectors or vectors with different
  // size or alignment.
  // Also, for now, return false if not scalar promotion case when inputs are
  // the same. Later, implement PackNode and allow differing, non-vector inputs
  // (maybe just the ones from outside the block.)
  for (uint i = start; i < end; i++) {
    if (!is_vector_use(p0, i)) {
      return false;
    }
  }
  // Check if reductions are connected
  if (is_marked_reduction(p0)) {
    Node* second_in = p0->in(2);
    Node_List* second_pk = get_pack(second_in);
    if (second_pk == nullptr) {
      // The second input has to be the vector we wanted to reduce,
      // but it was not packed.
      return false;
    } else if (_num_work_vecs == _num_reductions && AutoVectorizationOverrideProfitability != 2) {
      // This heuristic predicts that the reduction is not profitable.
      // Reduction vectors can be expensive, because they require multiple
      // operations to fold all the lanes together. Hence, vectorizing the
      // reduction is not profitable on its own. Hence, we need a lot of
      // other "work vectors" that deliver performance improvements to
      // balance out the performance loss due to reductions.
      // This heuristic is a bit simplistic, and assumes that the reduction
      // vector stays in the loop. But in some cases, we can move the
      // reduction out of the loop, replacing it with a single vector op.
      // See: PhaseIdealLoop::move_unordered_reduction_out_of_loop
      // Hence, this heuristic has room for improvement.
#ifndef PRODUCT
        if (is_trace_superword_rejections()) {
          tty->print_cr("\nPerformance heuristic: not enough vectors in the loop to make");
          tty->print_cr("  reduction profitable.");
          tty->print_cr("  Can override with AutoVectorizationOverrideProfitability=2");
        }
#endif
      return false;
    } else if (second_pk->size() != p->size()) {
      return false;
    }
  }
  if (VectorNode::is_shift(p0)) {
    // For now, return false if shift count is vector or not scalar promotion
    // case (different shift counts) because it is not supported yet.
    Node* cnt = p0->in(2);
    Node_List* cnt_pk = get_pack(cnt);
    if (cnt_pk != nullptr || _packset.same_inputs_at_index_or_null(p, 2) == nullptr) {
      return false;
    }
  }
  if (!p0->is_Store()) {
    // For now, return false if not all uses are vector.
    // Later, implement ExtractNode and allow non-vector uses (maybe
    // just the ones outside the block.)
    for (uint i = 0; i < p->size(); i++) {
      Node* def = p->at(i);
      for (DUIterator_Fast jmax, j = def->fast_outs(jmax); j < jmax; j++) {
        Node* use = def->fast_out(j);
        for (uint k = 0; k < use->req(); k++) {
          Node* n = use->in(k);
          if (def == n) {
            // Reductions should only have a Phi use at the loop head or a non-phi use
            // outside of the loop if it is the last element of the pack (e.g. SafePoint).
            if (is_marked_reduction(def) &&
                ((use->is_Phi() && use->in(0) == lpt()->_head) ||
                 (!lpt()->is_member(phase()->get_loop(phase()->ctrl_or_self(use))) && i == p->size()-1))) {
              continue;
            }
            if (!is_vector_use(use, k)) {
              return false;
            }
          }
        }
      }
    }
  }
  if (p0->is_Cmp()) {
    // Verify that Cmp pack only has Bool pack uses
    for (DUIterator_Fast jmax, j = p0->fast_outs(jmax); j < jmax; j++) {
      Node* bol = p0->fast_out(j);
      if (!bol->is_Bool() || bol->in(0) != nullptr || !is_vector_use(bol, 1)) {
        return false;
      }
    }
  }
  if (p0->is_Bool()) {
    // Verify that Bool pack only has CMove pack uses
    for (DUIterator_Fast jmax, j = p0->fast_outs(jmax); j < jmax; j++) {
      Node* cmove = p0->fast_out(j);
      if (!cmove->is_CMove() || cmove->in(0) != nullptr || !is_vector_use(cmove, 1)) {
        return false;
      }
    }
  }
  if (p0->is_CMove()) {
    // Verify that CMove has a matching Bool pack
    BoolNode* bol = p0->in(1)->as_Bool();
    if (bol == nullptr || get_pack(bol) == nullptr) {
      return false;
    }
    // Verify that Bool has a matching Cmp pack
    CmpNode* cmp = bol->in(1)->as_Cmp();
    if (cmp == nullptr || get_pack(cmp) == nullptr) {
      return false;
    }
  }
  return true;
}

#ifdef ASSERT
void SuperWord::verify_packs() const {
  _packset.verify();

  // All packs must be:
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* pack = _packset.at(i);

    // 1. Mutually independent (or a reduction).
    if (!is_marked_reduction(pack->at(0)) &&
        !mutually_independent(pack)) {
      tty->print_cr("FAILURE: nodes not mutually independent in pack[%d]", i);
      _packset.print_pack(pack);
      assert(false, "pack nodes not mutually independent");
    }

    // 2. Implemented.
    if (!implemented(pack, pack->size())) {
      tty->print_cr("FAILURE: nodes not implementable in pack[%d]", i);
      _packset.print_pack(pack);
      assert(false, "pack not implementable");
    }

    // 3. Profitable.
    if (!profitable(pack)) {
      tty->print_cr("FAILURE: nodes not profitable in pack[%d]", i);
      _packset.print_pack(pack);
      assert(false, "pack not profitable");
    }
  }
}

void PackSet::verify() const {
  // Verify all nodes in packset have pack set correctly.
  ResourceMark rm;
  Unique_Node_List processed;
  for (int i = 0; i < _packs.length(); i++) {
    Node_List* p = _packs.at(i);
    for (uint k = 0; k < p->size(); k++) {
      Node* n = p->at(k);
      assert(_vloop.in_bb(n), "only nodes in bb can be in packset");
      assert(!processed.member(n), "node should only occur once in packset");
      assert(get_pack(n) == p, "n has consisten packset info");
      processed.push(n);
    }
  }

  // Check that no other node has pack set.
  for (int i = 0; i < _body.body().length(); i++) {
    Node* n = _body.body().at(i);
    if (!processed.member(n)) {
      assert(get_pack(n) == nullptr, "should not have pack if not in packset");
    }
  }
}
#endif

bool SuperWord::schedule_and_apply() const {
  if (_packset.is_empty()) { return false; }

  // Make an empty transform.
#ifndef PRODUCT
  VTransformTrace trace(_vloop.vtrace(),
                        is_trace_superword_rejections(),
                        is_trace_align_vector(),
                        _vloop.is_trace_speculative_runtime_checks(),
                        is_trace_superword_info());
#endif
  VTransform vtransform(_vloop_analyzer,
                        _mem_ref_for_main_loop_alignment,
                        _aw_for_main_loop_alignment
                        NOT_PRODUCT(COMMA trace)
                        );

  // Build the transform from the packset.
  {
    ResourceMark rm;
    SuperWordVTransformBuilder builder(_packset, vtransform);
  }

  if (!vtransform.schedule()) { return false; }
  if (vtransform.has_store_to_load_forwarding_failure()) { return false; }

  if (AutoVectorizationOverrideProfitability == 0) {
#ifndef PRODUCT
    if (is_trace_superword_any()) {
      tty->print_cr("\nForced bailout of vectorization (AutoVectorizationOverrideProfitability=0).");
    }
#endif
    return false;
  }

  vtransform.apply();
  return true;
}

// Apply the vectorization, i.e. we irreversibly edit the C2 graph. At this point, all
// correctness and profitability checks have passed, and the graph was successfully scheduled.
void VTransform::apply() {
#ifndef PRODUCT
  if (_trace._info || TraceLoopOpts) {
    tty->print_cr("\nVTransform::apply:");
    lpt()->dump_head();
    lpt()->head()->dump();
  }
  assert(cl()->is_main_loop(), "auto vectorization only for main loops");
  assert(_graph.is_scheduled(), "must already be scheduled");
#endif

  Compile* C = phase()->C;
  C->print_method(PHASE_AUTO_VECTORIZATION1_BEFORE_APPLY, 4, cl());

  _graph.apply_memops_reordering_with_schedule();
  C->print_method(PHASE_AUTO_VECTORIZATION2_AFTER_REORDER, 4, cl());

  adjust_pre_loop_limit_to_align_main_loop_vectors();
  C->print_method(PHASE_AUTO_VECTORIZATION3_AFTER_ADJUST_LIMIT, 4, cl());

  apply_speculative_runtime_checks();
  C->print_method(PHASE_AUTO_VECTORIZATION4_AFTER_SPECULATIVE_RUNTIME_CHECKS, 4, cl());

  apply_vectorization();
  C->print_method(PHASE_AUTO_VECTORIZATION5_AFTER_APPLY, 4, cl());
}

// We prepare the memory graph for the replacement of scalar memops with vector memops.
// We reorder all slices in parallel, ensuring that the memops inside each slice are
// ordered according to the _schedule. This means that all packed memops are consecutive
// in the memory graph after the reordering.
void VTransformGraph::apply_memops_reordering_with_schedule() const {
#ifndef PRODUCT
  assert(is_scheduled(), "must be already scheduled");
  if (_trace._info) {
    print_memops_schedule();
  }
#endif

  ResourceMark rm;
  int max_slices = phase()->C->num_alias_types();
  // When iterating over the schedule, we keep track of the current memory state,
  // which is the Phi or a store in the loop.
  GrowableArray<Node*> current_state_in_slice(max_slices, max_slices, nullptr);
  // The memory state after the loop is the last store inside the loop. If we reorder the
  // loop we may have a different last store, and we need to adjust the uses accordingly.
  GrowableArray<Node*> old_last_store_in_slice(max_slices, max_slices, nullptr);

  const GrowableArray<PhiNode*>& mem_slice_head = _vloop_analyzer.memory_slices().heads();

  // (1) Set up the initial memory state from Phi. And find the old last store.
  for (int i = 0; i < mem_slice_head.length(); i++) {
    Node* phi  = mem_slice_head.at(i);
    assert(phi->is_Phi(), "must be phi");
    int alias_idx = phase()->C->get_alias_index(phi->adr_type());
    current_state_in_slice.at_put(alias_idx, phi);

    // If we have a memory phi, we have a last store in the loop, find it over backedge.
    StoreNode* last_store = phi->in(2)->as_Store();
    old_last_store_in_slice.at_put(alias_idx, last_store);
  }

  // (2) Walk over schedule, append memops to the current state
  //     of that slice. If it is a Store, we take it as the new state.
  for_each_memop_in_schedule([&] (MemNode* n) {
    assert(n->is_Load() || n->is_Store(), "only loads or stores");
    int alias_idx = phase()->C->get_alias_index(n->adr_type());
    Node* current_state = current_state_in_slice.at(alias_idx);
    if (current_state == nullptr) {
      // If there are only loads in a slice, we never update the memory
      // state in the loop, hence there is no phi for the memory state.
      // We just keep the old memory state that was outside the loop.
      assert(n->is_Load() && !in_bb(n->in(MemNode::Memory)),
             "only loads can have memory state from outside loop");
    } else {
      igvn().replace_input_of(n, MemNode::Memory, current_state);
      if (n->is_Store()) {
        current_state_in_slice.at_put(alias_idx, n);
      }
    }
  });

  // (3) For each slice, we add the current state to the backedge
  //     in the Phi. Further, we replace uses of the old last store
  //     with uses of the new last store (current_state).
  GrowableArray<Node*> uses_after_loop;
  for (int i = 0; i < mem_slice_head.length(); i++) {
    Node* phi  = mem_slice_head.at(i);
    int alias_idx = phase()->C->get_alias_index(phi->adr_type());
    Node* current_state = current_state_in_slice.at(alias_idx);
    assert(current_state != nullptr, "slice is mapped");
    assert(current_state != phi, "did some work in between");
    assert(current_state->is_Store(), "sanity");
    igvn().replace_input_of(phi, 2, current_state);

    // Replace uses of old last store with current_state (new last store)
    // Do it in two loops: first find all the uses, and change the graph
    // in as second loop so that we do not break the iterator.
    Node* last_store = old_last_store_in_slice.at(alias_idx);
    assert(last_store != nullptr, "we have a old last store");
    uses_after_loop.clear();
    for (DUIterator_Fast kmax, k = last_store->fast_outs(kmax); k < kmax; k++) {
      Node* use = last_store->fast_out(k);
      if (!in_bb(use)) {
        uses_after_loop.push(use);
      }
    }
    for (int k = 0; k < uses_after_loop.length(); k++) {
      Node* use = uses_after_loop.at(k);
      for (uint j = 0; j < use->req(); j++) {
        Node* def = use->in(j);
        if (def == last_store) {
          igvn().replace_input_of(use, j, current_state);
        }
      }
    }
  }
}

void VTransformGraph::apply_vectorization_for_each_vtnode(uint& max_vector_length, uint& max_vector_width) const {
  ResourceMark rm;
  // We keep track of the resulting Nodes from every "VTransformNode::apply" call.
  // Since "apply" is called on defs before uses, this allows us to find the
  // generated def (input) nodes when we are generating the use nodes in "apply".
  int length = _vtnodes.length();
  GrowableArray<Node*> vtnode_idx_to_transformed_node(length, length, nullptr);

  for (int i = 0; i < _schedule.length(); i++) {
    VTransformNode* vtn = _schedule.at(i);
    VTransformApplyResult result = vtn->apply(_vloop_analyzer,
                                              vtnode_idx_to_transformed_node);
    NOT_PRODUCT( if (_trace._verbose) { result.trace(vtn); } )

    vtnode_idx_to_transformed_node.at_put(vtn->_idx, result.node());
    max_vector_length = MAX2(max_vector_length, result.vector_length());
    max_vector_width  = MAX2(max_vector_width,  result.vector_width());
  }
}

// We call "apply" on every VTransformNode, which replaces the packed scalar nodes with vector nodes.
void VTransform::apply_vectorization() const {
  Compile* C = phase()->C;
#ifndef PRODUCT
  if (_trace._verbose) {
    tty->print_cr("\nVTransform::apply_vectorization:");
  }
#endif

  uint max_vector_length = 0; // number of elements
  uint max_vector_width  = 0; // total width in bytes
  _graph.apply_vectorization_for_each_vtnode(max_vector_length, max_vector_width);

  assert(max_vector_length > 0 && max_vector_width > 0, "must have vectorized");
  cl()->mark_loop_vectorized();

  if (max_vector_width > C->max_vector_size()) {
    C->set_max_vector_size(max_vector_width);
  }

  if (SuperWordLoopUnrollAnalysis) {
    if (cl()->has_passed_slp()) {
      uint slp_max_unroll_factor = cl()->slp_max_unroll();
      if (slp_max_unroll_factor == max_vector_length) {
#ifndef PRODUCT
        if (TraceSuperWordLoopUnrollAnalysis) {
          tty->print_cr("vector loop(unroll=%d, len=%d)\n", max_vector_length, max_vector_width * BitsPerByte);
        }
#endif
        // For atomic unrolled loops which are vector mapped, instigate more unrolling
        cl()->set_notpassed_slp();
        // if vector resources are limited, do not allow additional unrolling
        if (Matcher::float_pressure_limit() > 8) {
          C->set_major_progress();
          cl()->mark_do_unroll_only();
        }
      }
    }
  }
}

#ifdef ASSERT
// We check that every packset (name it p_def) only has vector uses (p_use),
// which are proper vector uses of def.
void SuperWord::verify_no_extract() {
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* p_def = _packset.at(i);

    // A vector store has no uses
    if (p_def->at(0)->is_Store()) { continue; }

    // for every def in p_def, and every use:
    for (uint i = 0; i < p_def->size(); i++) {
      Node* def = p_def->at(i);
      for (DUIterator_Fast jmax, j = def->fast_outs(jmax); j < jmax; j++) {
        Node* use = def->fast_out(j);
        // find every use->def edge:
        for (uint k = 0; k < use->req(); k++) {
          Node* maybe_def = use->in(k);
          if (def == maybe_def) {
            Node_List* p_use = get_pack(use);
            if (is_marked_reduction(def)) { continue; }
            assert(p_use != nullptr && is_vector_use(use, k), "all uses must be vector uses");
          }
        }
      }
    }
  }
}
#endif

// Check if n_super's pack uses are a superset of n_sub's pack uses.
bool SuperWord::has_use_pack_superset(const Node* n_super, const Node* n_sub) const {
  Node_List* pack = get_pack(n_super);
  assert(pack != nullptr && pack == get_pack(n_sub), "must have the same pack");

  // For all uses of n_sub that are in a pack (use_sub) ...
  for (DUIterator_Fast jmax, j = n_sub->fast_outs(jmax); j < jmax; j++) {
    Node* use_sub = n_sub->fast_out(j);
    Node_List* pack_use_sub = get_pack(use_sub);
    if (pack_use_sub == nullptr) { continue; }

    // ... and all input edges: use_sub->in(i) == n_sub.
    uint start, end;
    VectorNode::vector_operands(use_sub, &start, &end);
    for (uint i = start; i < end; i++) {
      if (use_sub->in(i) != n_sub) { continue; }

      // Check if n_super has any use use_super in the same pack ...
      bool found = false;
      for (DUIterator_Fast kmax, k = n_super->fast_outs(kmax); k < kmax; k++) {
        Node* use_super = n_super->fast_out(k);
        Node_List* pack_use_super = get_pack(use_super);
        if (pack_use_sub != pack_use_super) { continue; }

        // ... and where there is an edge use_super->in(i) == n_super.
        // For MulAddS2I it is expected to have defs over different input edges.
        if (use_super->in(i) != n_super && !VectorNode::is_muladds2i(use_super)) { continue; }

        found = true;
        break;
      }
      if (!found) {
        // n_sub has a use-edge (use_sub->in(i) == n_sub) with use_sub in a packset,
        // but n_super does not have any edge (use_super->in(i) == n_super) with
        // use_super in the same packset. Hence, n_super does not have a use pack
        // superset of n_sub.
        return false;
      }
    }
  }
  // n_super has all edges that n_sub has.
  return true;
}

// Find a boundary in the pack, where left and right have different pack uses and defs.
// This is a natural boundary to split a pack, to ensure that use and def packs match.
// If no boundary is found, return zero.
uint SuperWord::find_use_def_boundary(const Node_List* pack) const {
  Node* p0 = pack->at(0);
  Node* p1 = pack->at(1);

  const bool is_reduction_pack = reduction(p0, p1);

  // Inputs range
  uint start, end;
  VectorNode::vector_operands(p0, &start, &end);

  for (int i = pack->size() - 2; i >= 0; i--) {
    // For all neighbours
    Node* n0 = pack->at(i + 0);
    Node* n1 = pack->at(i + 1);


    // 1. Check for matching defs
    for (uint j = start; j < end; j++) {
      Node* n0_in = n0->in(j);
      Node* n1_in = n1->in(j);
      // No boundary if:
      // 1) the same packs OR
      // 2) reduction edge n0->n1 or n1->n0
      if (get_pack(n0_in) != get_pack(n1_in) &&
          !((n0 == n1_in || n1 == n0_in) && is_reduction_pack)) {
        return i + 1;
      }
    }

    // 2. Check for matching uses: equal if both are superset of the other.
    //    Reductions have no pack uses, so they match trivially on the use packs.
    if (!is_reduction_pack &&
        !(has_use_pack_superset(n0, n1) &&
          has_use_pack_superset(n1, n0))) {
      return i + 1;
    }
  }

  return 0;
}

//------------------------------is_vector_use---------------------------
// Is use->in(u_idx) a vector use?
bool SuperWord::is_vector_use(Node* use, int u_idx) const {
  Node_List* u_pk = get_pack(use);
  if (u_pk == nullptr) return false;

  // Reduction: first input is internal connection.
  if (is_marked_reduction(use) && u_idx == 1) {
    for (uint i = 1; i < u_pk->size(); i++) {
      if (u_pk->at(i - 1) != u_pk->at(i)->in(1)) {
        return false; // not internally connected
      }
    }
    return true;
  }

  Node* def = use->in(u_idx);
  Node_List* d_pk = get_pack(def);
  if (d_pk == nullptr) {
    Node* n = u_pk->at(0)->in(u_idx);
    if (n == iv()) {
      // check for index population
      BasicType bt = velt_basic_type(use);
      if (!VectorNode::is_populate_index_supported(bt)) return false;
      for (uint i = 1; i < u_pk->size(); i++) {
        // We can create a vector filled with iv indices if all other nodes
        // in use pack have inputs of iv plus node index.
        Node* use_in = u_pk->at(i)->in(u_idx);
        if (!use_in->is_Add() || use_in->in(1) != n) return false;
        const TypeInt* offset_t = use_in->in(2)->bottom_type()->is_int();
        if (offset_t == nullptr || !offset_t->is_con() ||
            offset_t->get_con() != (jint) i) return false;
      }
    } else {
      // check for scalar promotion
      for (uint i = 1; i < u_pk->size(); i++) {
        if (u_pk->at(i)->in(u_idx) != n) return false;
      }
    }
    return true;
  }

  if (!is_velt_basic_type_compatible_use_def(use, def)) {
    return false;
  }

  if (VectorNode::is_muladds2i(use)) {
    return _packset.is_muladds2i_pack_with_pack_inputs(u_pk);
  }

  return _packset.pack_input_at_index_or_null(u_pk, u_idx) != nullptr;
}

// MulAddS2I takes 4 shorts and produces an int. We can reinterpret
// the 4 shorts as two ints: a = (a0, a1) and b = (b0, b1).
//
// Inputs:                 1    2    3    4
// Offsets:                0    0    1    1
//   v = MulAddS2I(a, b) = a0 * b0 + a1 * b1
//
// But permutations are possible, because add and mul are commutative. For
// simplicity, the first input is always either a0 or a1. These are all
// the possible permutations:
//
//   v = MulAddS2I(a, b) = a0 * b0 + a1 * b1     (case 1)
//   v = MulAddS2I(a, b) = a0 * b0 + b1 * a1     (case 2)
//   v = MulAddS2I(a, b) = a1 * b1 + a0 * b0     (case 3)
//   v = MulAddS2I(a, b) = a1 * b1 + b0 * a0     (case 4)
//
// To vectorize, we expect (a0, a1) to be consecutive in one input pack,
// and (b0, b1) in the other input pack. Thus, both a and b are strided,
// with stride = 2. Further, a0 and b0 have offset 0, whereas a1 and b1
// have offset 1.
bool PackSet::is_muladds2i_pack_with_pack_inputs(const Node_List* pack) const {
  assert(VectorNode::is_muladds2i(pack->at(0)), "must be MulAddS2I");

  bool pack1_has_offset_0 = (strided_pack_input_at_index_or_null(pack, 1, 2, 0) != nullptr);
  Node_List* pack1 = strided_pack_input_at_index_or_null(pack, 1, 2, pack1_has_offset_0 ? 0 : 1);
  Node_List* pack2 = strided_pack_input_at_index_or_null(pack, 2, 2, pack1_has_offset_0 ? 0 : 1);
  Node_List* pack3 = strided_pack_input_at_index_or_null(pack, 3, 2, pack1_has_offset_0 ? 1 : 0);
  Node_List* pack4 = strided_pack_input_at_index_or_null(pack, 4, 2, pack1_has_offset_0 ? 1 : 0);

  return pack1 != nullptr &&
         pack2 != nullptr &&
         pack3 != nullptr &&
         pack4 != nullptr &&
         ((pack1 == pack3 && pack2 == pack4) || // case 1 or 3
          (pack1 == pack4 && pack2 == pack3));  // case 2 or 4
}

Node_List* PackSet::strided_pack_input_at_index_or_null(const Node_List* pack, const int index, const int stride, const int offset) const {
  Node* def0 = pack->at(0)->in(index);

  Node_List* pack_in = get_pack(def0);
  if (pack_in == nullptr || pack->size() * stride != pack_in->size()) {
    return nullptr; // size mismatch
  }

  for (uint i = 0; i < pack->size(); i++) {
    if (pack->at(i)->in(index) != pack_in->at(i * stride + offset)) {
      return nullptr; // use-def mismatch
    }
  }
  return pack_in;
}

// Check if the output type of def is compatible with the input type of use, i.e. if the
// types have the same size.
bool SuperWord::is_velt_basic_type_compatible_use_def(Node* use, Node* def) const {
  assert(in_bb(def) && in_bb(use), "both use and def are in loop");

  // Conversions are trivially compatible.
  if (VectorNode::is_convert_opcode(use->Opcode())) {
    return true;
  }

  BasicType use_bt = velt_basic_type(use);
  BasicType def_bt = velt_basic_type(def);

  assert(is_java_primitive(use_bt), "sanity %s", type2name(use_bt));
  assert(is_java_primitive(def_bt), "sanity %s", type2name(def_bt));

  // Nodes like Long.bitCount: expect long input, and int output.
  if (VectorNode::is_scalar_op_that_returns_int_but_vector_op_returns_long(use->Opcode())) {
    return type2aelembytes(def_bt) == 8 &&
           type2aelembytes(use_bt) == 4;
  }

  // MulAddS2I: expect short input, and int output.
  if (VectorNode::is_muladds2i(use)) {
    return type2aelembytes(def_bt) == 2 &&
           type2aelembytes(use_bt) == 4;
  }

  // Default case: input size of use equals output size of def.
  return type2aelembytes(use_bt) == type2aelembytes(def_bt);
}

// Return nullptr if success, else failure message
VStatus VLoopBody::construct() {
  assert(_body.is_empty(), "body is empty");

  // First pass over loop body:
  //  (1) Check that there are no unwanted nodes (LoadStore, MergeMem, data Proj).
  //  (2) Count number of nodes, and create a temporary map (_idx -> bb_idx).
  //  (3) Verify that all non-ctrl nodes have an input inside the loop.
  int body_count = 0;
  for (uint i = 0; i < _vloop.lpt()->_body.size(); i++) {
    Node* n = _vloop.lpt()->_body.at(i);
    set_bb_idx(n, i); // Create a temporary map
    if (_vloop.in_bb(n)) {
      body_count++;

      if (n->is_LoadStore() || n->is_MergeMem() ||
          (n->is_Proj() && !n->as_Proj()->is_CFG())) {
        // Bailout if the loop has LoadStore, MergeMem or data Proj
        // nodes. Superword optimization does not work with them.
#ifndef PRODUCT
        if (_vloop.is_trace_body()) {
          tty->print_cr("VLoopBody::construct: fails because of unhandled node:");
          n->dump();
        }
#endif
        return VStatus::make_failure(VLoopBody::FAILURE_NODE_NOT_ALLOWED);
      }

      if (!n->is_CFG()) {
        bool found = false;
        for (uint j = 0; j < n->req(); j++) {
          Node* def = n->in(j);
          if (def != nullptr && _vloop.in_bb(def)) {
            found = true;
            break;
          }
        }
        if (!found) {
          // If all inputs to a data-node are outside the loop, the node itself should be outside the loop.
#ifndef PRODUCT
          if (_vloop.is_trace_body()) {
            tty->print_cr("VLoopBody::construct: fails because data node in loop has no input in loop:");
            n->dump();
          }
#endif
          return VStatus::make_failure(VLoopBody::FAILURE_UNEXPECTED_CTRL);
        }
      }
    }
  }

  // Create a reverse-post-order list of nodes in body
  ResourceMark rm;
  GrowableArray<Node*> stack;
  VectorSet visited;
  VectorSet post_visited;

  visited.set(bb_idx(_vloop.cl()));
  stack.push(_vloop.cl());

  // Do a depth first walk over out edges
  int rpo_idx = body_count - 1;
  while (!stack.is_empty()) {
    Node* n = stack.top(); // Leave node on stack
    if (!visited.test_set(bb_idx(n))) {
      // forward arc in graph
    } else if (!post_visited.test(bb_idx(n))) {
      // cross or back arc
      const int old_length = stack.length();

      // If a Load depends on the same memory state as a Store, we must make sure that
      // the Load is ordered before the Store.
      //
      //      mem
      //       |
      //    +--+--+
      //    |     |
      //    |    Load (n)
      //    |
      //   Store (mem_use)
      //
      if (n->is_Load()) {
        Node* mem = n->in(MemNode::Memory);
        for (DUIterator_Fast imax, i = mem->fast_outs(imax); i < imax; i++) {
          Node* mem_use = mem->fast_out(i);
          if (mem_use->is_Store() && _vloop.in_bb(mem_use) && !visited.test(bb_idx(mem_use))) {
            stack.push(mem_use); // Ordering edge: Load (n) -> Store (mem_use)
          }
        }
      }

      for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
        Node* use = n->fast_out(i);
        if (_vloop.in_bb(use) && !visited.test(bb_idx(use)) &&
            // Don't go around backedge
            (!use->is_Phi() || n == _vloop.cl())) {
          stack.push(use); // Ordering edge: n -> use
        }
      }

      if (stack.length() == old_length) {
        // There were no additional uses, post visit node now
        stack.pop(); // Remove node from stack
        assert(rpo_idx >= 0, "must still have idx to pass out");
        _body.at_put_grow(rpo_idx, n);
        rpo_idx--;
        post_visited.set(bb_idx(n));
        assert(rpo_idx >= 0 || stack.is_empty(), "still have idx left or are finished");
      }
    } else {
      stack.pop(); // Remove post-visited node from stack
    }
  }

  // Create real map of body indices for nodes
  for (int j = 0; j < _body.length(); j++) {
    Node* n = _body.at(j);
    set_bb_idx(n, j);
  }

#ifndef PRODUCT
  if (_vloop.is_trace_body()) {
    print();
  }
#endif

  assert(rpo_idx == -1 && body_count == _body.length(), "all body members found");
  return VStatus::make_success();
}

// Returns true if the given operation can be vectorized with "truncation" where the upper bits in the integer do not
// contribute to the result. This is true for most arithmetic operations, but false for operations such as
// leading/trailing zero count.
static bool can_subword_truncate(Node* in, const Type* type) {
  if (in->is_Load() || in->is_Store() || in->is_Convert() || in->is_Phi()) {
    return true;
  }

  int opc = in->Opcode();

  // If the node's base type is a subword type, check an additional set of nodes.
  if (type == TypeInt::SHORT || type == TypeInt::CHAR) {
    switch (opc) {
    case Op_ReverseBytesS:
    case Op_ReverseBytesUS:
      return true;
    }
  }

  // Can be truncated:
  switch (opc) {
  case Op_AddI:
  case Op_SubI:
  case Op_MulI:
  case Op_AndI:
  case Op_OrI:
  case Op_XorI:
    return true;
  }

#ifdef ASSERT
  // While shifts have subword vectorized forms, they require knowing the precise type of input loads so they are
  // considered non-truncating.
  if (VectorNode::is_shift_opcode(opc)) {
    return false;
  }

  // Vector nodes should not truncate.
  if (type->isa_vect() != nullptr || type->isa_vectmask() != nullptr || in->is_Reduction()) {
    return false;
  }

  // Cannot be truncated:
  switch (opc) {
  case Op_AbsI:
  case Op_DivI:
  case Op_ModI:
  case Op_MinI:
  case Op_MaxI:
  case Op_CMoveI:
  case Op_Conv2B:
  case Op_RotateRight:
  case Op_RotateLeft:
  case Op_PopCountI:
  case Op_ReverseBytesI:
  case Op_ReverseI:
  case Op_CountLeadingZerosI:
  case Op_CountTrailingZerosI:
  case Op_IsFiniteF:
  case Op_IsFiniteD:
  case Op_IsInfiniteF:
  case Op_IsInfiniteD:
  case Op_CmpLTMask:
  case Op_RoundF:
  case Op_RoundD:
  case Op_ExtractS:
  case Op_ExtractC:
  case Op_ExtractB:
    return false;
  default:
    // If this assert is hit, that means that we need to determine if the node can be safely truncated,
    // and then add it to the list of truncating nodes or the list of non-truncating ones just above.
    // In product, we just return false, which is always correct.
    assert(false, "Unexpected node in SuperWord truncation: %s", NodeClassNames[in->Opcode()]);
  }
#endif

  // Default to disallowing vector truncation
  return false;
}

void VLoopTypes::compute_vector_element_type() {
#ifndef PRODUCT
  if (_vloop.is_trace_vector_element_type()) {
    tty->print_cr("\nVLoopTypes::compute_vector_element_type:");
  }
#endif

  const GrowableArray<Node*>& body = _body.body();

  assert(_velt_type.is_empty(), "must not yet be computed");
  // reserve space
  _velt_type.at_put_grow(body.length()-1, nullptr);

  // Initial type
  for (int i = 0; i < body.length(); i++) {
    Node* n = body.at(i);
    set_velt_type(n, container_type(n));
  }

  // Propagate integer narrowed type backwards through operations
  // that don't depend on higher order bits
  for (int i = body.length() - 1; i >= 0; i--) {
    Node* n = body.at(i);
    // Only integer types need be examined
    const Type* vtn = velt_type(n);
    if (vtn->basic_type() == T_INT) {
      uint start, end;
      VectorNode::vector_operands(n, &start, &end);

      for (uint j = start; j < end; j++) {
        Node* in  = n->in(j);
        // Don't propagate through a memory
        if (!in->is_Mem() &&
            _vloop.in_bb(in) &&
            velt_type(in)->basic_type() == T_INT &&
            data_size(n) < data_size(in)) {
          bool same_type = true;
          for (DUIterator_Fast kmax, k = in->fast_outs(kmax); k < kmax; k++) {
            Node *use = in->fast_out(k);
            if (!_vloop.in_bb(use) || !same_velt_type(use, n)) {
              same_type = false;
              break;
            }
          }
          if (same_type) {
            // In any Java arithmetic operation, operands of small integer types
            // (boolean, byte, char & short) should be promoted to int first.
            // During narrowed integer type backward propagation, for some operations
            // like RShiftI, Abs, and ReverseBytesI,
            // the compiler has to know the higher order bits of the 1st operand,
            // which will be lost in the narrowed type. These operations shouldn't
            // be vectorized if the higher order bits info is imprecise.
            const Type* vt = vtn;
            int op = in->Opcode();
            if (!can_subword_truncate(in, vt)) {
              Node* load = in->in(1);
              // For certain operations such as shifts and abs(), use the size of the load if it exists
              if ((VectorNode::is_shift_opcode(op) || op == Op_AbsI) && load->is_Load() &&
                  _vloop.in_bb(load) &&
                  (velt_type(load)->basic_type() == T_INT)) {
                // Only Load nodes distinguish signed (LoadS/LoadB) and unsigned
                // (LoadUS/LoadUB) values. Store nodes only have one version.
                vt = velt_type(load);
              } else if (op != Op_LShiftI) {
                // Widen type to the node type to avoid the creation of vector nodes. Note
                // that left shifts work regardless of the signedness.
                vt = container_type(in);
              }
            }
            set_velt_type(in, vt);
          }
        }
      }
    }
  }
  for (int i = 0; i < body.length(); i++) {
    Node* n = body.at(i);
    Node* nn = n;
    if (nn->is_Bool() && nn->in(0) == nullptr) {
      nn = nn->in(1);
      assert(nn->is_Cmp(), "always have Cmp above Bool");
    }
    if (nn->is_Cmp() && nn->in(0) == nullptr) {
      assert(_vloop.in_bb(nn->in(1)) || _vloop.in_bb(nn->in(2)),
             "one of the inputs must be in the loop, too");
      if (_vloop.in_bb(nn->in(1))) {
        set_velt_type(n, velt_type(nn->in(1)));
      } else {
        set_velt_type(n, velt_type(nn->in(2)));
      }
    }
  }
#ifndef PRODUCT
  if (_vloop.is_trace_vector_element_type()) {
    for (int i = 0; i < body.length(); i++) {
      Node* n = body.at(i);
      velt_type(n)->dump();
      tty->print("\t");
      n->dump();
    }
  }
#endif
}

// Smallest type containing range of values
const Type* VLoopTypes::container_type(Node* n) const {
  int opc = n->Opcode();
  if (n->is_Mem()) {
    BasicType bt = n->as_Mem()->value_basic_type();
    if (n->is_Store() && (bt == T_CHAR)) {
      // Use T_SHORT type instead of T_CHAR for stored values because any
      // preceding arithmetic operation extends values to signed Int.
      bt = T_SHORT;
    }
    if (opc == Op_LoadUB) {
      // Adjust type for unsigned byte loads, it is important for right shifts.
      // T_BOOLEAN is used because there is no basic type representing type
      // TypeInt::UBYTE. Use of T_BOOLEAN for vectors is fine because only
      // size (one byte) and sign is important.
      bt = T_BOOLEAN;
    }
    return Type::get_const_basic_type(bt);
  }
  const Type* t = _vloop.phase()->igvn().type(n);
  if (t->basic_type() == T_INT) {
    // Float to half float conversion may be succeeded by a conversion from
    // half float to float, in such a case back propagation of narrow type (SHORT)
    // may not be possible.
    if (opc == Op_ConvF2HF || opc == Op_ReinterpretHF2S) {
      return TypeInt::SHORT;
    }
    // A narrow type of arithmetic operations will be determined by
    // propagating the type of memory operations.
    return TypeInt::INT;
  }
  return t;
}

bool VLoopMemorySlices::same_memory_slice(MemNode* m1, MemNode* m2) const {
  return _vloop.phase()->C->get_alias_index(m1->adr_type()) ==
         _vloop.phase()->C->get_alias_index(m2->adr_type());
}

LoadNode::ControlDependency VTransformLoadVectorNode::control_dependency() const {
  LoadNode::ControlDependency dep = LoadNode::DependsOnlyOnTest;
  for (int i = 0; i < nodes().length(); i++) {
    Node* n = nodes().at(i);
    assert(n->is_Load(), "only meaningful for loads");
    if (!n->depends_only_on_test()) {
      if (n->as_Load()->has_unknown_control_dependency() &&
          dep != LoadNode::Pinned) {
        // Upgrade to unknown control...
        dep = LoadNode::UnknownControl;
      } else {
        // Otherwise, we must pin it.
        dep = LoadNode::Pinned;
      }
    }
  }
  return dep;
}

// Find the memop pack with the maximum vector width, unless they were already
// determined (e.g. by SuperWord::filter_packs_for_alignment()).
void VTransform::determine_mem_ref_and_aw_for_main_loop_alignment() {
  if (_mem_ref_for_main_loop_alignment != nullptr) {
    assert(VLoop::vectors_should_be_aligned(), "mem_ref only set if filtered for alignment");
    return;
  }

  MemNode const* mem_ref = nullptr;
  int max_aw = 0;

  const GrowableArray<VTransformNode*>& vtnodes = _graph.vtnodes();
  for (int i = 0; i < vtnodes.length(); i++) {
    VTransformMemVectorNode* vtn = vtnodes.at(i)->isa_MemVector();
    if (vtn == nullptr) { continue; }
    MemNode* p0 = vtn->nodes().at(0)->as_Mem();

    int vw = p0->memory_size() * vtn->nodes().length();
    // Generally, we prefer to align with the largest memory op (load or store).
    // If there are multiple, then SuperWordAutomaticAlignment determines if we
    // prefer loads or stores.
    // When a load or store is misaligned, this can lead to the load or store
    // being split, when it goes over a cache line. Most CPUs can schedule
    // more loads than stores per cycle (often 2 loads and 1 store). Hence,
    // it is worse if a store is split, and less bad if a load is split.
    // By default, we have SuperWordAutomaticAlignment=1, i.e. we align with a
    // store if possible, to avoid splitting that store.
    bool prefer_store = mem_ref != nullptr && SuperWordAutomaticAlignment == 1 && mem_ref->is_Load() && p0->is_Store();
    bool prefer_load  = mem_ref != nullptr && SuperWordAutomaticAlignment == 2 && mem_ref->is_Store() && p0->is_Load();
    if (vw > max_aw || (vw == max_aw && (prefer_load || prefer_store))) {
      max_aw = vw;
      mem_ref = p0;
    }
  }
  assert(mem_ref != nullptr && max_aw > 0, "found mem_ref and aw");
  _mem_ref_for_main_loop_alignment = mem_ref;
  _aw_for_main_loop_alignment = max_aw;
}

#define TRACE_ALIGN_VECTOR_NODE(node) { \
  DEBUG_ONLY(                           \
    if (_trace._align_vector) {         \
      tty->print("  " #node ": ");      \
      node->dump();                     \
    }                                   \
  )                                     \
}                                       \

// Ensure that the main loop vectors are aligned by adjusting the pre loop limit. We memory-align
// the address of "_mem_ref_for_main_loop_alignment" to "_aw_for_main_loop_alignment", which is a
// sufficiently large alignment width. We adjust the pre-loop iteration count by adjusting the
// pre-loop limit.
void VTransform::adjust_pre_loop_limit_to_align_main_loop_vectors() {
  determine_mem_ref_and_aw_for_main_loop_alignment();
  const MemNode* align_to_ref = _mem_ref_for_main_loop_alignment;
  const int aw                = _aw_for_main_loop_alignment;

  if (!VLoop::vectors_should_be_aligned() && SuperWordAutomaticAlignment == 0) {
#ifdef ASSERT
    if (_trace._align_vector) {
      tty->print_cr("\nVTransform::adjust_pre_loop_limit_to_align_main_loop_vectors: disabled.");
    }
#endif
    return;
  }

  assert(align_to_ref != nullptr && aw > 0, "must have alignment reference and aw");
  assert(cl()->is_main_loop(), "can only do alignment for main loop");

  // The opaque node for the limit, where we adjust the input
  Opaque1Node* pre_opaq = _vloop.pre_loop_end()->limit()->as_Opaque1();

  // Current pre-loop limit.
  Node* old_limit = pre_opaq->in(1);

  // Where we put new limit calculations.
  Node* pre_ctrl = _vloop.pre_loop_head()->in(LoopNode::EntryControl);

  // Ensure the original loop limit is available from the pre-loop Opaque1 node.
  Node* orig_limit = pre_opaq->original_loop_limit();
  assert(orig_limit != nullptr && igvn().type(orig_limit) != Type::TOP, "");

  const VPointer& p = vpointer(align_to_ref);
  assert(p.is_valid(), "sanity");

  // For the main-loop, we want the address of align_to_ref to be memory aligned
  // with some alignment width (aw, a power of 2). When we enter the main-loop,
  // we know that iv is equal to the pre-loop limit. If we adjust the pre-loop
  // limit by executing adjust_pre_iter many extra iterations, we can change the
  // alignment of the address.
  //
  //   adr = base + invar + iv_scale * iv + con                               (1)
  //   adr % aw = 0                                                           (2)
  //
  // Note, that we are defining the modulo operator "%" such that the remainder is
  // always positive, see AlignmentSolution::mod(i, q). Since we are only computing
  // modulo with powers of 2, we can instead simply use the last log2(q) bits of
  // a number i, to get "i % q". This is performed with a bitmask.
  //
  // The limit of the pre-loop needs to be adjusted:
  //
  //   old_limit:       current pre-loop limit
  //   new_limit:       new pre-loop limit
  //   adjust_pre_iter: additional pre-loop iterations for alignment adjustment
  //
  // We want to find adjust_pre_iter, such that the address is aligned when entering
  // the main-loop:
  //
  //   iv = new_limit = old_limit + adjust_pre_iter                           (3a, iv_stride > 0)
  //   iv = new_limit = old_limit - adjust_pre_iter                           (3b, iv_stride < 0)
  //
  // We define bic as:
  //
  //   bic = base + invar + con                                               (4)
  //
  // And now we can simplify the address using (1), (3), and (4):
  //
  //   adr = bic + iv_scale * new_limit
  //   adr = bic + iv_scale * (old_limit + adjust_pre_iter)                   (5a, iv_stride > 0)
  //   adr = bic + iv_scale * (old_limit - adjust_pre_iter)                   (5b, iv_stride < 0)
  //
  // And hence we can restate (2) with (5), and solve the equation for adjust_pre_iter:
  //
  //   (bic + iv_scale * (old_limit + adjust_pre_iter) % aw = 0               (6a, iv_stride > 0)
  //   (bic + iv_scale * (old_limit - adjust_pre_iter) % aw = 0               (6b, iv_stride < 0)
  //
  // In most cases, iv_scale is the element size, for example:
  //
  //   for (i = 0; i < a.length; i++) { a[i] = ...; }
  //
  // It is thus reasonable to assume that both abs(iv_scale) and abs(iv_stride) are
  // strictly positive powers of 2. Further, they can be assumed to be non-zero,
  // otherwise the address does not depend on iv, and the alignment cannot be
  // affected by adjusting the pre-loop limit.
  //
  // Further, if abs(iv_scale) >= aw, then adjust_pre_iter has no effect on alignment, and
  // we are not able to affect the alignment at all. Hence, we require abs(iv_scale) < aw.
  //
  // Moreover, for alignment to be achievable, bic must be a multiple of iv_scale. If strict
  // alignment is required (i.e. -XX:+AlignVector), this is guaranteed by the filtering
  // done with the AlignmentSolver / AlignmentSolution. If strict alignment is not
  // required, then alignment is still preferable for performance, but not necessary.
  // In many cases bic will be a multiple of iv_scale, but if it is not, then the adjustment
  // does not guarantee alignment, but the code is still correct.
  //
  // Hence, in what follows we assume that bic is a multiple of iv_scale, and in fact all
  // terms in (6) are multiples of iv_scale. Therefore we divide all terms by iv_scale:
  //
  //   AW = aw / abs(iv_scale)            (power of 2)                        (7)
  //   BIC = bic / abs(iv_scale)                                              (8)
  //
  // and restate (6), using (7) and (8), i.e. we divide (6) by abs(iv_scale):
  //
  //   (BIC + sign(iv_scale) * (old_limit + adjust_pre_iter) % AW = 0         (9a, iv_stride > 0)
  //   (BIC + sign(iv_scale) * (old_limit - adjust_pre_iter) % AW = 0         (9b, iv_stride < 0)
  //
  //   where: sign(iv_scale) = iv_scale / abs(iv_scale) = (iv_scale > 0 ? 1 : -1)
  //
  // Note, (9) allows for periodic solutions of adjust_pre_iter, with periodicity AW.
  // But we would like to spend as few iterations in the pre-loop as possible,
  // hence we want the smallest adjust_pre_iter, and so:
  //
  //   0 <= adjust_pre_iter < AW                                              (10)
  //
  // We solve (9) for adjust_pre_iter, in the following 4 cases:
  //
  // Case A: iv_scale > 0 && iv_stride > 0 (i.e. sign(iv_scale) =  1)
  //   (BIC + old_limit + adjust_pre_iter) % AW = 0
  //   adjust_pre_iter = (-BIC - old_limit) % AW                              (11a)
  //
  // Case B: iv_scale < 0 && iv_stride > 0 (i.e. sign(iv_scale) = -1)
  //   (BIC - old_limit - adjust_pre_iter) % AW = 0
  //   adjust_pre_iter = (BIC - old_limit) % AW                               (11b)
  //
  // Case C: iv_scale > 0 && iv_stride < 0 (i.e. sign(iv_scale) =  1)
  //   (BIC + old_limit - adjust_pre_iter) % AW = 0
  //   adjust_pre_iter = (BIC + old_limit) % AW                               (11c)
  //
  // Case D: iv_scale < 0 && iv_stride < 0 (i.e. sign(iv_scale) = -1)
  //   (BIC - old_limit + adjust_pre_iter) % AW = 0
  //   adjust_pre_iter = (-BIC + old_limit) % AW                              (11d)
  //
  // We now generalize the equations (11*) by using:
  //
  //   OP:   (iv_stride            > 0) ?  SUB  : ADD
  //   XBIC: (iv_stride * iv_scale > 0) ? -BIC  : BIC
  //
  // which gives us the final pre-loop limit adjustment:
  //
  //   adjust_pre_iter = (XBIC OP old_limit) % AW                             (12)
  //
  // We can construct XBIC by additionally defining:
  //
  //   xbic = (iv_stride * iv_scale > 0) ? -bic                 : bic         (13)
  //
  // which gives us:
  //
  //   XBIC = (iv_stride * iv_scale > 0) ? -BIC                 : BIC
  //        = (iv_stride * iv_scale > 0) ? -bic / abs(iv_scale) : bic / abs(iv_scale)
  //        = xbic / abs(iv_scale)                                            (14)
  //
  // When we have computed adjust_pre_iter, we update the pre-loop limit
  // with (3a, b). However, we have to make sure that the adjust_pre_iter
  // additional pre-loop iterations do not lead the pre-loop to execute
  // iterations that would step over the original limit (orig_limit) of
  // the loop. Hence, we must constrain the updated limit as follows:
  //
  // constrained_limit = MIN(old_limit + adjust_pre_iter, orig_limit)
  //                   = MIN(new_limit,                   orig_limit)         (15a, iv_stride > 0)
  // constrained_limit = MAX(old_limit - adjust_pre_iter, orig_limit)
  //                   = MAX(new_limit,                   orig_limit)         (15a, iv_stride < 0)
  //
  const int iv_stride = this->iv_stride();
  const int iv_scale  = p.iv_scale();
  const int con       = p.con();
  Node* base          = p.mem_pointer().base().object_or_native();

#ifdef ASSERT
  if (_trace._align_vector) {
    tty->print_cr("\nVTransform::adjust_pre_loop_limit_to_align_main_loop_vectors:");
    tty->print("  align_to_ref:");
    align_to_ref->dump();
    tty->print("  ");
    p.print_on(tty);
    tty->print_cr("  aw:        %d", aw);
    tty->print_cr("  iv_stride: %d", iv_stride);
    tty->print_cr("  iv_scale:  %d", iv_scale);
    tty->print_cr("  con:       %d", con);
    tty->print("  base:");
    base->dump();
    if (!p.has_invar_summands()) {
      tty->print_cr("  invar:     none");
    } else {
      tty->print_cr("  invar_summands:");
      p.for_each_invar_summand([&] (const MemPointerSummand& s) {
        tty->print("   -> ");
        s.print_on(tty);
      });
      tty->cr();
    }
    tty->print("  old_limit: ");
    old_limit->dump();
    tty->print("  orig_limit: ");
    orig_limit->dump();
  }
#endif

  if (iv_stride == 0 || !is_power_of_2(abs(iv_stride)) ||
      iv_scale  == 0 || !is_power_of_2(abs(iv_scale))  ||
      abs(iv_scale) >= aw) {
#ifdef ASSERT
    if (_trace._align_vector) {
      tty->print_cr(" Alignment cannot be affected by changing pre-loop limit because");
      tty->print_cr(" iv_stride or iv_scale are not power of 2, or abs(iv_scale) >= aw.");
    }
#endif
    // Cannot affect alignment, abort.
    return;
  }

  assert(iv_stride != 0 && is_power_of_2(abs(iv_stride)) &&
         iv_scale  != 0 && is_power_of_2(abs(iv_scale))  &&
         abs(iv_scale) < aw, "otherwise we cannot affect alignment with pre-loop");

  const int AW = aw / abs(iv_scale);

#ifdef ASSERT
  if (_trace._align_vector) {
    tty->print_cr("  AW = aw(%d) / abs(iv_scale(%d)) = %d", aw, iv_scale, AW);
  }
#endif

  // 1: Compute (13a, b):
  //    xbic = -bic = (-base - invar - con)         (iv_stride * iv_scale > 0)
  //    xbic = +bic = (+base + invar + con)         (iv_stride * iv_scale < 0)
  const bool is_sub = iv_scale * iv_stride > 0;

  // 1.1: con
  Node* xbic = igvn().intcon(is_sub ? -con : con);
  TRACE_ALIGN_VECTOR_NODE(xbic);

  // 1.2: invar = SUM(invar_summands)
  //      We iteratively add / subtract all invar_summands, if there are any.
  p.for_each_invar_summand([&] (const MemPointerSummand& s) {
    Node* invar_variable = s.variable();
    jint  invar_scale    = s.scale().value();
    TRACE_ALIGN_VECTOR_NODE(invar_variable);
    if (igvn().type(invar_variable)->isa_long()) {
      // Computations are done % (vector width/element size) so it's
      // safe to simply convert invar to an int and loose the upper 32
      // bit half.
      invar_variable = new ConvL2INode(invar_variable);
      phase()->register_new_node(invar_variable, pre_ctrl);
      TRACE_ALIGN_VECTOR_NODE(invar_variable);
    }
    Node* invar_scale_con = igvn().intcon(invar_scale);
    TRACE_ALIGN_VECTOR_NODE(invar_scale_con);
    Node* invar_summand = new MulINode(invar_variable, invar_scale_con);
    phase()->register_new_node(invar_summand, pre_ctrl);
    TRACE_ALIGN_VECTOR_NODE(invar_summand);
    if (is_sub) {
      xbic = new SubINode(xbic, invar_summand);
    } else {
      xbic = new AddINode(xbic, invar_summand);
    }
    phase()->register_new_node(xbic, pre_ctrl);
    TRACE_ALIGN_VECTOR_NODE(xbic);
  });

  // 1.3: base (unless base is guaranteed aw aligned)
  bool is_base_native = p.mem_pointer().base().is_native();
  if (aw > ObjectAlignmentInBytes || is_base_native) {
    // For objects, the base is ObjectAlignmentInBytes aligned.
    // For native memory, we simply have a long that was cast to
    // a pointer via CastX2P, or if we parsed through the CastX2P
    // we only have a long. There is no alignment guarantee, and
    // we must always take the base into account for the calculation.
    //
    // Computations are done % (vector width/element size) so it's
    // safe to simply convert invar to an int and loose the upper 32
    // bit half. The base could be ptr, long or int. We cast all
    // to int.
    Node* xbase = base;
    if (igvn().type(xbase)->isa_ptr()) {
      // ptr -> int/long
      xbase = new CastP2XNode(nullptr, xbase);
      phase()->register_new_node(xbase, pre_ctrl);
      TRACE_ALIGN_VECTOR_NODE(xbase);
    }
    if (igvn().type(xbase)->isa_long()) {
      // long -> int
      xbase  = new ConvL2INode(xbase);
      phase()->register_new_node(xbase, pre_ctrl);
      TRACE_ALIGN_VECTOR_NODE(xbase);
    }
    if (is_sub) {
      xbic = new SubINode(xbic, xbase);
    } else {
      xbic = new AddINode(xbic, xbase);
    }
    phase()->register_new_node(xbic, pre_ctrl);
    TRACE_ALIGN_VECTOR_NODE(xbic);
  }

  // 2: Compute (14):
  //    XBIC = xbic / abs(iv_scale)
  //    The division is executed as shift
  Node* log2_abs_iv_scale = igvn().intcon(exact_log2(abs(iv_scale)));
  Node* XBIC = new URShiftINode(xbic, log2_abs_iv_scale);
  phase()->register_new_node(XBIC, pre_ctrl);
  TRACE_ALIGN_VECTOR_NODE(log2_abs_iv_scale);
  TRACE_ALIGN_VECTOR_NODE(XBIC);

  // 3: Compute (12):
  //    adjust_pre_iter = (XBIC OP old_limit) % AW
  //
  // 3.1: XBIC_OP_old_limit = XBIC OP old_limit
  Node* XBIC_OP_old_limit = nullptr;
  if (iv_stride > 0) {
    XBIC_OP_old_limit = new SubINode(XBIC, old_limit);
  } else {
    XBIC_OP_old_limit = new AddINode(XBIC, old_limit);
  }
  phase()->register_new_node(XBIC_OP_old_limit, pre_ctrl);
  TRACE_ALIGN_VECTOR_NODE(XBIC_OP_old_limit);

  // 3.2: Compute:
  //    adjust_pre_iter = (XBIC OP old_limit) % AW
  //                    = XBIC_OP_old_limit % AW
  //                    = XBIC_OP_old_limit AND (AW - 1)
  //    Since AW is a power of 2, the modulo operation can be replaced with
  //    a bitmask operation.
  Node* mask_AW = igvn().intcon(AW-1);
  Node* adjust_pre_iter = new AndINode(XBIC_OP_old_limit, mask_AW);
  phase()->register_new_node(adjust_pre_iter, pre_ctrl);
  TRACE_ALIGN_VECTOR_NODE(mask_AW);
  TRACE_ALIGN_VECTOR_NODE(adjust_pre_iter);

  // 4: The computation of the new pre-loop limit could overflow (for 3a) or
  //    underflow (for 3b) the int range. This is problematic in combination
  //    with Range Check Elimination (RCE), which determines a "safe" range
  //    where a RangeCheck will always succeed. RCE adjusts the pre-loop limit
  //    such that we only enter the main-loop once we have reached the "safe"
  //    range, and adjusts the main-loop limit so that we exit the main-loop
  //    before we leave the "safe" range. After RCE, the range of the main-loop
  //    can only be safely narrowed, and should never be widened. Hence, the
  //    pre-loop limit can only be increased (for iv_stride > 0), but an add
  //    overflow might decrease it, or decreased (for iv_stride < 0), but a sub
  //    underflow might increase it. To prevent that, we perform the Sub / Add
  //    and Max / Min with long operations.
  old_limit       = new ConvI2LNode(old_limit);
  orig_limit      = new ConvI2LNode(orig_limit);
  adjust_pre_iter = new ConvI2LNode(adjust_pre_iter);
  phase()->register_new_node(old_limit, pre_ctrl);
  phase()->register_new_node(orig_limit, pre_ctrl);
  phase()->register_new_node(adjust_pre_iter, pre_ctrl);
  TRACE_ALIGN_VECTOR_NODE(old_limit);
  TRACE_ALIGN_VECTOR_NODE(orig_limit);
  TRACE_ALIGN_VECTOR_NODE(adjust_pre_iter);

  // 5: Compute (3a, b):
  //    new_limit = old_limit + adjust_pre_iter     (iv_stride > 0)
  //    new_limit = old_limit - adjust_pre_iter     (iv_stride < 0)
  //
  Node* new_limit = nullptr;
  if (iv_stride < 0) {
    new_limit = new SubLNode(old_limit, adjust_pre_iter);
  } else {
    new_limit = new AddLNode(old_limit, adjust_pre_iter);
  }
  phase()->register_new_node(new_limit, pre_ctrl);
  TRACE_ALIGN_VECTOR_NODE(new_limit);

  // 6: Compute (15a, b):
  //    Prevent pre-loop from going past the original limit of the loop.
  Node* constrained_limit =
    (iv_stride > 0) ? (Node*) new MinLNode(phase()->C, new_limit, orig_limit)
                    : (Node*) new MaxLNode(phase()->C, new_limit, orig_limit);
  phase()->register_new_node(constrained_limit, pre_ctrl);
  TRACE_ALIGN_VECTOR_NODE(constrained_limit);

  // 7: We know that the result is in the int range, there is never truncation
  constrained_limit = new ConvL2INode(constrained_limit);
  phase()->register_new_node(constrained_limit, pre_ctrl);
  TRACE_ALIGN_VECTOR_NODE(constrained_limit);

  // 8: Hack the pre-loop limit
  igvn().replace_input_of(pre_opaq, 1, constrained_limit);
}

#ifndef PRODUCT
void PairSet::print() const {
  tty->print_cr("\nPairSet::print: %d pairs", length());
  int chain = 0;
  int chain_index = 0;
  for (PairSetIterator pair(*this); !pair.done(); pair.next()) {
    Node* left  = pair.left();
    Node* right = pair.right();
    if (is_left_in_a_left_most_pair(left)) {
      chain_index = 0;
      tty->print_cr(" Pair-chain %d:", chain++);
      tty->print("  %3d: ", chain_index++);
      left->dump();
    }
    tty->print("  %3d: ", chain_index++);
    right->dump();
  }
}

void PackSet::print() const {
  tty->print_cr("\nPackSet::print: %d packs", _packs.length());
  for (int i = 0; i < _packs.length(); i++) {
    tty->print_cr(" Pack: %d", i);
    Node_List* pack = _packs.at(i);
    if (pack == nullptr) {
      tty->print_cr("  nullptr");
    } else {
      print_pack(pack);
    }
  }
}

void PackSet::print_pack(Node_List* pack) {
  for (uint i = 0; i < pack->size(); i++) {
    tty->print("  %3d: ", i);
    pack->at(i)->dump();
  }
}
#endif

#ifndef PRODUCT
void VLoopBody::print() const {
  tty->print_cr("\nVLoopBody::print");
  for (int i = 0; i < body().length(); i++) {
    Node* n = body().at(i);
    tty->print("%4d ", i);
    if (n != nullptr) {
      n->dump();
    }
  }
}
#endif

//
// --------------------------------- vectorization/simd -----------------------------------
//
bool SuperWord::same_origin_idx(Node* a, Node* b) const {
  return a != nullptr && b != nullptr && _clone_map.same_idx(a->_idx, b->_idx);
}
bool SuperWord::same_generation(Node* a, Node* b) const {
  return a != nullptr && b != nullptr && _clone_map.same_gen(a->_idx, b->_idx);
}

