/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "libadt/vectset.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "opto/addnode.hpp"
#include "opto/c2compiler.hpp"
#include "opto/castnode.hpp"
#include "opto/convertnode.hpp"
#include "opto/matcher.hpp"
#include "opto/memnode.hpp"
#include "opto/opcodes.hpp"
#include "opto/opaquenode.hpp"
#include "opto/superword.hpp"
#include "opto/vectornode.hpp"
#include "opto/movenode.hpp"
#include "utilities/powerOfTwo.hpp"

//
//                  S U P E R W O R D   T R A N S F O R M
//=============================================================================

//------------------------------SuperWord---------------------------
SuperWord::SuperWord(PhaseIdealLoop* phase) :
  _phase(phase),
  _arena(phase->C->comp_arena()),
  _igvn(phase->_igvn),
  _packset(arena(), 8,  0, nullptr),                        // packs for the current block
  _bb_idx(arena(), (int)(1.10 * phase->C->unique()), 0, 0), // node idx to index in bb
  _block(arena(), 8,  0, nullptr),                          // nodes in current block
  _data_entry(arena(), 8,  0, nullptr),                     // nodes with all inputs from outside
  _mem_slice_head(arena(), 8,  0, nullptr),                 // memory slice heads
  _mem_slice_tail(arena(), 8,  0, nullptr),                 // memory slice tails
  _node_info(arena(), 8,  0, SWNodeInfo::initial),          // info needed per node
  _clone_map(phase->C->clone_map()),                        // map of nodes created in cloning
  _align_to_ref(nullptr),                                   // memory reference to align vectors to
  _disjoint_ptrs(arena(), 8,  0, OrderedPair::initial),     // runtime disambiguated pointer pairs
  _dg(_arena),                                              // dependence graph
  _visited(arena()),                                        // visited node set
  _post_visited(arena()),                                   // post visited node set
  _n_idx_list(arena(), 8),                                  // scratch list of (node,index) pairs
  _nlist(arena(), 8, 0, nullptr),                           // scratch list of nodes
  _stk(arena(), 8, 0, nullptr),                             // scratch stack of nodes
  _lpt(nullptr),                                            // loop tree node
  _lp(nullptr),                                             // CountedLoopNode
  _loop_reductions(arena()),                                // reduction nodes in the current loop
  _bb(nullptr),                                             // basic block
  _iv(nullptr),                                             // induction var
  _race_possible(false),                                    // cases where SDMU is true
  _early_return(true),                                      // analysis evaluations routine
  _do_vector_loop(phase->C->do_vector_loop()),              // whether to do vectorization/simd style
  _num_work_vecs(0),                                        // amount of vector work we have
  _num_reductions(0)                                        // amount of reduction work we have
{
#ifndef PRODUCT
  _vector_loop_debug = 0;
  if (_phase->C->method() != nullptr) {
    _vector_loop_debug = phase->C->directive()->VectorizeDebugOption;
  }

#endif
}

//------------------------------transform_loop---------------------------
bool SuperWord::transform_loop(IdealLoopTree* lpt, bool do_optimization) {
  assert(_phase->C->do_superword(), "SuperWord option should be enabled");
  // SuperWord only works with power of two vector sizes.
  int vector_width = Matcher::vector_width_in_bytes(T_BYTE);
  if (vector_width < 2 || !is_power_of_2(vector_width)) {
    return false;
  }

  assert(lpt->_head->is_CountedLoop(), "must be");
  CountedLoopNode *cl = lpt->_head->as_CountedLoop();

  if (!cl->is_valid_counted_loop(T_INT)) {
    return false; // skip malformed counted loop
  }

  // Initialize simple data used by reduction marking early.
  set_lpt(lpt);
  set_lp(cl);
  // For now, define one block which is the entire loop body.
  set_bb(cl);

  if (SuperWordReductions) {
    mark_reductions();
  }

  // skip any loop that has not been assigned max unroll by analysis
  if (do_optimization) {
    if (SuperWordLoopUnrollAnalysis && cl->slp_max_unroll() == 0) {
      return false;
    }
  }

  // Check for no control flow in body (other than exit)
  Node *cl_exit = cl->loopexit();
  if (cl->is_main_loop() && (cl_exit->in(0) != lpt->_head)) {
    #ifndef PRODUCT
      if (TraceSuperWord) {
        tty->print_cr("SuperWord::transform_loop: loop too complicated, cl_exit->in(0) != lpt->_head");
        tty->print("cl_exit %d", cl_exit->_idx); cl_exit->dump();
        tty->print("cl_exit->in(0) %d", cl_exit->in(0)->_idx); cl_exit->in(0)->dump();
        tty->print("lpt->_head %d", lpt->_head->_idx); lpt->_head->dump();
        lpt->dump_head();
      }
    #endif
    return false;
  }

  // Make sure the are no extra control users of the loop backedge
  if (cl->back_control()->outcnt() != 1) {
    return false;
  }

  // Skip any loops already optimized by slp
  if (cl->is_vectorized_loop()) {
    return false;
  }

  if (cl->is_unroll_only()) {
    return false;
  }

  if (cl->is_main_loop()) {
    // Check for pre-loop ending with CountedLoopEnd(Bool(Cmp(x,Opaque1(limit))))
    CountedLoopEndNode* pre_end = cl->find_pre_loop_end();
    if (pre_end == nullptr) {
      return false;
    }
    Node* pre_opaq1 = pre_end->limit();
    if (pre_opaq1->Opcode() != Op_Opaque1) {
      return false;
    }
    cl->set_pre_loop_end(pre_end);
  }

  init(); // initialize data structures

  bool success = true;
  if (do_optimization) {
    assert(_packset.length() == 0, "packset must be empty");
    success = SLP_extract();
  }
  return success;
}

//------------------------------early unrolling analysis------------------------------
void SuperWord::unrolling_analysis(int &local_loop_unroll_factor) {
  bool is_slp = true;
  size_t ignored_size = lpt()->_body.size();
  int *ignored_loop_nodes = NEW_RESOURCE_ARRAY(int, ignored_size);
  Node_Stack nstack((int)ignored_size);
  CountedLoopNode *cl = lpt()->_head->as_CountedLoop();
  Node *cl_exit = cl->loopexit_or_null();

  // First clear the entries
  for (uint i = 0; i < lpt()->_body.size(); i++) {
    ignored_loop_nodes[i] = -1;
  }

  int max_vector = Matcher::superword_max_vector_size(T_BYTE);

  // Process the loop, some/all of the stack entries will not be in order, ergo
  // need to preprocess the ignored initial state before we process the loop
  for (uint i = 0; i < lpt()->_body.size(); i++) {
    Node* n = lpt()->_body.at(i);
    if (n == cl->incr() ||
      is_marked_reduction(n) ||
      n->is_AddP() ||
      n->is_Cmp() ||
      n->is_Bool() ||
      n->is_IfTrue() ||
      n->is_CountedLoop() ||
      (n == cl_exit)) {
      ignored_loop_nodes[i] = n->_idx;
      continue;
    }

    if (n->is_If()) {
      IfNode *iff = n->as_If();
      if (iff->_fcnt != COUNT_UNKNOWN && iff->_prob != PROB_UNKNOWN) {
        if (lpt()->is_loop_exit(iff)) {
          ignored_loop_nodes[i] = n->_idx;
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
      ignored_loop_nodes[i] = n->_idx;
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
      bt = n->as_Mem()->memory_type();
    } else {
      bt = n->bottom_type()->basic_type();
    }
    if (is_java_primitive(bt) == false) {
      ignored_loop_nodes[i] = n->_idx;
      continue;
    }

    if (n->is_Mem()) {
      MemNode* current = n->as_Mem();
      Node* adr = n->in(MemNode::Address);
      Node* n_ctrl = _phase->get_ctrl(adr);

      // save a queue of post process nodes
      if (n_ctrl != nullptr && lpt()->is_member(_phase->get_loop(n_ctrl))) {
        // Process the memory expression
        int stack_idx = 0;
        bool have_side_effects = true;
        if (adr->is_AddP() == false) {
          nstack.push(adr, stack_idx++);
        } else {
          // Mark the components of the memory operation in nstack
          VPointer p1(current, phase(), lpt(), &nstack, true);
          have_side_effects = p1.node_stack()->is_nonempty();
        }

        // Process the pointer stack
        while (have_side_effects) {
          Node* pointer_node = nstack.node();
          for (uint j = 0; j < lpt()->_body.size(); j++) {
            Node* cur_node = lpt()->_body.at(j);
            if (cur_node == pointer_node) {
              ignored_loop_nodes[j] = cur_node->_idx;
              break;
            }
          }
          nstack.pop();
          have_side_effects = nstack.is_nonempty();
        }
      }
    }
  }

  if (is_slp) {
    // Now we try to find the maximum supported consistent vector which the machine
    // description can use
    bool flag_small_bt = false;
    for (uint i = 0; i < lpt()->_body.size(); i++) {
      if (ignored_loop_nodes[i] != -1) continue;

      BasicType bt;
      Node* n = lpt()->_body.at(i);
      if (n->is_Mem()) {
        bt = n->as_Mem()->memory_type();
      } else {
        bt = n->bottom_type()->basic_type();
      }

      if (is_java_primitive(bt) == false) continue;

      int cur_max_vector = Matcher::superword_max_vector_size(bt);

      // If a max vector exists which is not larger than _local_loop_unroll_factor
      // stop looking, we already have the max vector to map to.
      if (cur_max_vector < local_loop_unroll_factor) {
        is_slp = false;
        if (TraceSuperWordLoopUnrollAnalysis) {
          tty->print_cr("slp analysis fails: unroll limit greater than max vector\n");
        }
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
              if (!in->is_Mem() && in_bb(in) && in->bottom_type()->basic_type() == T_INT) {
                bool same_type = true;
                for (DUIterator_Fast kmax, k = in->fast_outs(kmax); k < kmax; k++) {
                  Node *use = in->fast_out(k);
                  if (!in_bb(use) && use->bottom_type()->basic_type() != bt) {
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
      cl->set_slp_max_unroll(local_loop_unroll_factor);
    }
  }
}

bool SuperWord::is_reduction(const Node* n) {
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

bool SuperWord::is_reduction_operator(const Node* n) {
  int opc = n->Opcode();
  return (opc != ReductionNode::opcode(opc, n->bottom_type()->basic_type()));
}

bool SuperWord::in_reduction_cycle(const Node* n, uint input) {
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

Node* SuperWord::original_input(const Node* n, uint i) {
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

void SuperWord::mark_reductions() {

  _loop_reductions.clear();

  // Iterate through all phi nodes associated to the loop and search for
  // reduction cycles in the basic block.
  for (DUIterator_Fast imax, i = lp()->fast_outs(imax); i < imax; i++) {
    const Node* phi = lp()->fast_out(i);
    if (!phi->is_Phi()) {
      continue;
    }
    if (phi->outcnt() == 0) {
      continue;
    }
    if (phi == iv()) {
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
          first, input, lpt()->_body.size(),
          [&](const Node* n) { return n->Opcode() == first->Opcode() && in_bb(n); },
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
        if (!in_bb(u)) {
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

#ifndef PRODUCT
  if (_do_vector_loop && TraceSuperWord) {
    tty->print("SuperWord::SLP_extract\n");
    tty->print("input loop\n");
    _lpt->dump_head();
    _lpt->dump();
    for (uint i = 0; i < _lpt->_body.size(); i++) {
      _lpt->_body.at(i)->dump();
    }
  }
#endif

  CountedLoopNode* cl = lpt()->_head->as_CountedLoop();
  assert(cl->is_main_loop(), "SLP should only work on main loops");

  // Ready the block
  if (!construct_bb()) {
    return false; // Exit if no interesting nodes or complex graph.
  }

  // build _dg, _disjoint_ptrs
  dependence_graph();

  // compute function depth(Node*)
  compute_max_depth();

  // Compute vector element types
  compute_vector_element_type();

  // Attempt vectorization
  find_adjacent_refs();

  if (_packset.length() == 0) {
#ifndef PRODUCT
    if (TraceSuperWord) {
      tty->print_cr("\nNo pair packs generated, abort SuperWord.");
      tty->cr();
    }
#endif
    return false;
  }

  extend_packlist();

  combine_packs();

  filter_packs_for_alignment();

  construct_my_pack_map();

  filter_packs();

  DEBUG_ONLY(verify_packs();)

  schedule();

  return output();
}

//------------------------------find_adjacent_refs---------------------------
// Find the adjacent memory references and create pack pairs for them.
// We can find adjacent memory references by comparing their relative
// alignment. Whether the final vectors can be aligned is determined later
// once all vectors are extended and combined.
void SuperWord::find_adjacent_refs() {
  // Get list of memory operations
  Node_List memops;
  for (int i = 0; i < _block.length(); i++) {
    Node* n = _block.at(i);
    if (n->is_Mem() && !n->is_LoadStore() && in_bb(n) &&
        is_java_primitive(n->as_Mem()->memory_type())) {
      int align = memory_alignment(n->as_Mem(), 0);
      if (align != bottom_align) {
        memops.push(n);
      }
    }
  }
  if (TraceSuperWord) {
    tty->print_cr("\nfind_adjacent_refs found %d memops", memops.size());
  }

  int max_idx;

  // Take the first mem_ref as the reference to align to. The pre-loop trip count is
  // modified to align this reference to a vector-aligned address. If strict alignment
  // is required, we may change the reference later (see filter_packs_for_alignment()).
  MemNode* align_to_mem_ref = nullptr;

  while (memops.size() != 0) {
    // Find a memory reference to align to.
    MemNode* mem_ref = find_align_to_ref(memops, max_idx);
    if (mem_ref == nullptr) break;
    int iv_adjustment = get_iv_adjustment(mem_ref);

    if (align_to_mem_ref == nullptr) {
      align_to_mem_ref = mem_ref;
      set_align_to_ref(align_to_mem_ref);
    }

    VPointer align_to_ref_p(mem_ref, phase(), lpt(), nullptr, false);
    // Set alignment relative to "align_to_ref" for all related memory operations.
    for (int i = memops.size() - 1; i >= 0; i--) {
      MemNode* s = memops.at(i)->as_Mem();
      if (isomorphic(s, mem_ref) &&
           (!_do_vector_loop || same_origin_idx(s, mem_ref))) {
        VPointer p2(s, phase(), lpt(), nullptr, false);
        if (p2.comparable(align_to_ref_p)) {
          int align = memory_alignment(s, iv_adjustment);
          set_alignment(s, align);
        }
      }
    }

    // Create initial pack pairs of memory operations for which alignment was set.
    for (uint i = 0; i < memops.size(); i++) {
      Node* s1 = memops.at(i);
      int align = alignment(s1);
      if (align == top_align) continue;
      for (uint j = 0; j < memops.size(); j++) {
        Node* s2 = memops.at(j);
        if (alignment(s2) == top_align) continue;
        if (s1 != s2 && are_adjacent_refs(s1, s2)) {
          if (stmts_can_pack(s1, s2, align)) {
            Node_List* pair = new Node_List();
            pair->push(s1);
            pair->push(s2);
            if (!_do_vector_loop || same_origin_idx(s1, s2)) {
              _packset.append(pair);
            }
          }
        }
      }
    }

    // Remove used mem nodes.
    for (int i = memops.size() - 1; i >= 0; i--) {
      MemNode* m = memops.at(i)->as_Mem();
      if (alignment(m) != top_align) {
        memops.remove(i);
      }
    }
  } // while (memops.size() != 0)

  assert(_packset.is_empty() || align_to_mem_ref != nullptr,
         "packset empty or we find the alignment reference");

#ifndef PRODUCT
  if (TraceSuperWord) {
    tty->print_cr("\nAfter find_adjacent_refs");
    print_packset();
  }
#endif
}

//------------------------------find_align_to_ref---------------------------
// Find a memory reference to align the loop induction variable to.
// Looks first at stores then at loads, looking for a memory reference
// with the largest number of references similar to it.
MemNode* SuperWord::find_align_to_ref(Node_List &memops, int &idx) {
  GrowableArray<int> cmp_ct(arena(), memops.size(), memops.size(), 0);

  // Count number of comparable memory ops
  for (uint i = 0; i < memops.size(); i++) {
    MemNode* s1 = memops.at(i)->as_Mem();
    VPointer p1(s1, phase(), lpt(), nullptr, false);
    for (uint j = i+1; j < memops.size(); j++) {
      MemNode* s2 = memops.at(j)->as_Mem();
      if (isomorphic(s1, s2)) {
        VPointer p2(s2, phase(), lpt(), nullptr, false);
        if (p1.comparable(p2)) {
          (*cmp_ct.adr_at(i))++;
          (*cmp_ct.adr_at(j))++;
        }
      }
    }
  }

  // Find Store (or Load) with the greatest number of "comparable" references,
  // biggest vector size, smallest data size and smallest iv offset.
  int max_ct        = 0;
  int max_vw        = 0;
  int max_idx       = -1;
  int min_size      = max_jint;
  int min_iv_offset = max_jint;
  for (uint j = 0; j < memops.size(); j++) {
    MemNode* s = memops.at(j)->as_Mem();
    if (s->is_Store()) {
      int vw = vector_width_in_bytes(s);
      assert(vw > 1, "sanity");
      VPointer p(s, phase(), lpt(), nullptr, false);
      if ( cmp_ct.at(j) >  max_ct ||
          (cmp_ct.at(j) == max_ct &&
            ( vw >  max_vw ||
             (vw == max_vw &&
              ( data_size(s) <  min_size ||
               (data_size(s) == min_size &&
                p.offset_in_bytes() < min_iv_offset)))))) {
        max_ct = cmp_ct.at(j);
        max_vw = vw;
        max_idx = j;
        min_size = data_size(s);
        min_iv_offset = p.offset_in_bytes();
      }
    }
  }
  // If no stores, look at loads
  if (max_ct == 0) {
    for (uint j = 0; j < memops.size(); j++) {
      MemNode* s = memops.at(j)->as_Mem();
      if (s->is_Load()) {
        int vw = vector_width_in_bytes(s);
        assert(vw > 1, "sanity");
        VPointer p(s, phase(), lpt(), nullptr, false);
        if ( cmp_ct.at(j) >  max_ct ||
            (cmp_ct.at(j) == max_ct &&
              ( vw >  max_vw ||
               (vw == max_vw &&
                ( data_size(s) <  min_size ||
                 (data_size(s) == min_size &&
                  p.offset_in_bytes() < min_iv_offset)))))) {
          max_ct = cmp_ct.at(j);
          max_vw = vw;
          max_idx = j;
          min_size = data_size(s);
          min_iv_offset = p.offset_in_bytes();
        }
      }
    }
  }

#ifdef ASSERT
  if (TraceSuperWord && Verbose) {
    tty->print_cr("\nVector memops after find_align_to_ref");
    for (uint i = 0; i < memops.size(); i++) {
      MemNode* s = memops.at(i)->as_Mem();
      s->dump();
    }
  }
#endif

  idx = max_idx;
  if (max_ct > 0) {
#ifdef ASSERT
    if (TraceSuperWord) {
      tty->print("\nVector align to node: ");
      memops.at(max_idx)->as_Mem()->dump();
    }
#endif
    return memops.at(max_idx)->as_Mem();
  }
  return nullptr;
}

//---------------------------get_vw_bytes_special------------------------
int SuperWord::get_vw_bytes_special(MemNode* s) {
  // Get the vector width in bytes.
  int vw = vector_width_in_bytes(s);

  // Check for special case where there is an MulAddS2I usage where short vectors are going to need combined.
  BasicType btype = velt_basic_type(s);
  if (type2aelembytes(btype) == 2) {
    bool should_combine_adjacent = true;
    for (DUIterator_Fast imax, i = s->fast_outs(imax); i < imax; i++) {
      Node* user = s->fast_out(i);
      if (!VectorNode::is_muladds2i(user)) {
        should_combine_adjacent = false;
      }
    }
    if (should_combine_adjacent) {
      vw = MIN2(Matcher::superword_max_vector_size(btype)*type2aelembytes(btype), vw * 2);
    }
  }

  // Check for special case where there is a type conversion between different data size.
  int vectsize = max_vector_size_in_def_use_chain(s);
  if (vectsize < Matcher::superword_max_vector_size(btype)) {
    vw = MIN2(vectsize * type2aelembytes(btype), vw);
  }

  return vw;
}

//---------------------------get_iv_adjustment---------------------------
// Calculate loop's iv adjustment for this memory ops.
int SuperWord::get_iv_adjustment(MemNode* mem_ref) {
  VPointer align_to_ref_p(mem_ref, phase(), lpt(), nullptr, false);
  int offset = align_to_ref_p.offset_in_bytes();
  int scale  = align_to_ref_p.scale_in_bytes();
  int elt_size = align_to_ref_p.memory_size();
  int vw       = get_vw_bytes_special(mem_ref);
  assert(vw > 1, "sanity");
  int iv_adjustment;
  if (scale != 0) {
    int stride_sign = (scale * iv_stride()) > 0 ? 1 : -1;
    // At least one iteration is executed in pre-loop by default. As result
    // several iterations are needed to align memory operations in main-loop even
    // if offset is 0.
    int iv_adjustment_in_bytes = (stride_sign * vw - (offset % vw));
    iv_adjustment = iv_adjustment_in_bytes/elt_size;
  } else {
    // This memory op is not dependent on iv (scale == 0)
    iv_adjustment = 0;
  }

#ifndef PRODUCT
  if (TraceSuperWord) {
    tty->print("SuperWord::get_iv_adjustment: n = %d, noffset = %d iv_adjust = %d elt_size = %d scale = %d iv_stride = %d vect_size %d: ",
      mem_ref->_idx, offset, iv_adjustment, elt_size, scale, iv_stride(), vw);
    mem_ref->dump();
  }
#endif
  return iv_adjustment;
}

//---------------------------dependence_graph---------------------------
// Construct dependency graph.
// Add dependence edges to load/store nodes for memory dependence
//    A.out()->DependNode.in(1) and DependNode.out()->B.prec(x)
void SuperWord::dependence_graph() {
  CountedLoopNode *cl = lpt()->_head->as_CountedLoop();
  assert(cl->is_main_loop(), "SLP should only work on main loops");

  // First, assign a dependence node to each memory node
  for (int i = 0; i < _block.length(); i++ ) {
    Node *n = _block.at(i);
    if (n->is_Mem() || n->is_memory_phi()) {
      _dg.make_node(n);
    }
  }

  // For each memory slice, create the dependences
  for (int i = 0; i < _mem_slice_head.length(); i++) {
    Node* n      = _mem_slice_head.at(i);
    Node* n_tail = _mem_slice_tail.at(i);

    // Get slice in predecessor order (last is first)
    mem_slice_preds(n_tail, n, _nlist);

#ifndef PRODUCT
    if(TraceSuperWord && Verbose) {
      tty->print_cr("SuperWord::dependence_graph: built a new mem slice");
      for (int j = _nlist.length() - 1; j >= 0 ; j--) {
        _nlist.at(j)->dump();
      }
    }
#endif
    // Make the slice dependent on the root
    DepMem* slice = _dg.dep(n);
    _dg.make_edge(_dg.root(), slice);

    // Create a sink for the slice
    DepMem* slice_sink = _dg.make_node(nullptr);
    _dg.make_edge(slice_sink, _dg.tail());

    // Now visit each pair of memory ops, creating the edges
    for (int j = _nlist.length() - 1; j >= 0 ; j--) {
      Node* s1 = _nlist.at(j);

      // If no dependency yet, use slice
      if (_dg.dep(s1)->in_cnt() == 0) {
        _dg.make_edge(slice, s1);
      }
      VPointer p1(s1->as_Mem(), phase(), lpt(), nullptr, false);
      bool sink_dependent = true;
      for (int k = j - 1; k >= 0; k--) {
        Node* s2 = _nlist.at(k);
        if (s1->is_Load() && s2->is_Load())
          continue;
        VPointer p2(s2->as_Mem(), phase(), lpt(), nullptr, false);

        int cmp = p1.cmp(p2);
        if (SuperWordRTDepCheck &&
            p1.base() != p2.base() && p1.valid() && p2.valid()) {
          // Trace disjoint pointers
          OrderedPair pp(p1.base(), p2.base());
          _disjoint_ptrs.append_if_missing(pp);
        }
        if (!VPointer::not_equal(cmp)) {
          // Possibly same address
          _dg.make_edge(s1, s2);
          sink_dependent = false;
        }
      }
      if (sink_dependent) {
        _dg.make_edge(s1, slice_sink);
      }
    }

    if (TraceSuperWord) {
      tty->print_cr("\nDependence graph for slice: %d", n->_idx);
      for (int q = 0; q < _nlist.length(); q++) {
        _dg.print(_nlist.at(q));
      }
      tty->cr();
    }

    _nlist.clear();
  }

  if (TraceSuperWord) {
    tty->print_cr("\ndisjoint_ptrs: %s", _disjoint_ptrs.length() > 0 ? "" : "NONE");
    for (int r = 0; r < _disjoint_ptrs.length(); r++) {
      _disjoint_ptrs.at(r).print();
      tty->cr();
    }
    tty->cr();
  }

}

//---------------------------mem_slice_preds---------------------------
// Return a memory slice (node list) in predecessor order starting at "start"
void SuperWord::mem_slice_preds(Node* start, Node* stop, GrowableArray<Node*> &preds) {
  assert(preds.length() == 0, "start empty");
  Node* n = start;
  Node* prev = nullptr;
  while (true) {
    NOT_PRODUCT( if(is_trace_mem_slice()) tty->print_cr("SuperWord::mem_slice_preds: n %d", n->_idx);)
    assert(in_bb(n), "must be in block");
    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* out = n->fast_out(i);
      if (out->is_Load()) {
        if (in_bb(out)) {
          preds.push(out);
          if (TraceSuperWord && Verbose) {
            tty->print_cr("SuperWord::mem_slice_preds: added pred(%d)", out->_idx);
          }
        }
      } else {
        // FIXME
        if (out->is_MergeMem() && !in_bb(out)) {
          // Either unrolling is causing a memory edge not to disappear,
          // or need to run igvn.optimize() again before SLP
        } else if (out->is_memory_phi() && !in_bb(out)) {
          // Ditto.  Not sure what else to check further.
        } else if (out->Opcode() == Op_StoreCM && out->in(MemNode::OopStore) == n) {
          // StoreCM has an input edge used as a precedence edge.
          // Maybe an issue when oop stores are vectorized.
        } else {
          assert(out == prev || prev == nullptr, "no branches off of store slice");
        }
      }//else
    }//for
    if (n == stop) break;
    preds.push(n);
    if (TraceSuperWord && Verbose) {
      tty->print_cr("SuperWord::mem_slice_preds: added pred(%d)", n->_idx);
    }
    prev = n;
    assert(n->is_Mem(), "unexpected node %s", n->Name());
    n = n->in(MemNode::Memory);
  }
}

//------------------------------stmts_can_pack---------------------------
// Can s1 and s2 be in a pack with s1 immediately preceding s2 and
// s1 aligned at "align"
bool SuperWord::stmts_can_pack(Node* s1, Node* s2, int align) {

  // Do not use superword for non-primitives
  BasicType bt1 = velt_basic_type(s1);
  BasicType bt2 = velt_basic_type(s2);
  if(!is_java_primitive(bt1) || !is_java_primitive(bt2))
    return false;
  BasicType longer_bt = longer_type_for_conversion(s1);
  if (Matcher::superword_max_vector_size(bt1) < 2 ||
      (longer_bt != T_ILLEGAL && Matcher::superword_max_vector_size(longer_bt) < 2)) {
    return false; // No vectors for this type
  }

  if (isomorphic(s1, s2)) {
    if ((independent(s1, s2) && have_similar_inputs(s1, s2)) || reduction(s1, s2)) {
      if (!exists_at(s1, 0) && !exists_at(s2, 1)) {
        if (!s1->is_Mem() || are_adjacent_refs(s1, s2)) {
          int s1_align = alignment(s1);
          int s2_align = alignment(s2);
          if (s1_align == top_align || s1_align == align) {
            if (s2_align == top_align || s2_align == align + data_size(s1)) {
              return true;
            }
          }
        }
      }
    }
  }
  return false;
}

//------------------------------exists_at---------------------------
// Does s exist in a pack at position pos?
bool SuperWord::exists_at(Node* s, uint pos) {
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* p = _packset.at(i);
    if (p->at(pos) == s) {
      return true;
    }
  }
  return false;
}

//------------------------------are_adjacent_refs---------------------------
// Is s1 immediately before s2 in memory?
bool SuperWord::are_adjacent_refs(Node* s1, Node* s2) {
  if (!s1->is_Mem() || !s2->is_Mem()) return false;
  if (!in_bb(s1)    || !in_bb(s2))    return false;

  // Do not use superword for non-primitives
  if (!is_java_primitive(s1->as_Mem()->memory_type()) ||
      !is_java_primitive(s2->as_Mem()->memory_type())) {
    return false;
  }

  // Adjacent memory references must be on the same slice.
  if (!same_memory_slice(s1->as_Mem(), s2->as_Mem())) {
    return false;
  }

  // Adjacent memory references must have the same base, be comparable
  // and have the correct distance between them.
  VPointer p1(s1->as_Mem(), phase(), lpt(), nullptr, false);
  VPointer p2(s2->as_Mem(), phase(), lpt(), nullptr, false);
  if (p1.base() != p2.base() || !p1.comparable(p2)) return false;
  int diff = p2.offset_in_bytes() - p1.offset_in_bytes();
  return diff == data_size(s1);
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

//------------------------------independent---------------------------
// Is there no data path from s1 to s2 or s2 to s1?
bool SuperWord::independent(Node* s1, Node* s2) {
  //  assert(s1->Opcode() == s2->Opcode(), "check isomorphic first");
  int d1 = depth(s1);
  int d2 = depth(s2);
  if (d1 == d2) return s1 != s2;
  Node* deep    = d1 > d2 ? s1 : s2;
  Node* shallow = d1 > d2 ? s2 : s1;

  visited_clear();

  return independent_path(shallow, deep);
}

//------------------------------find_dependence---------------------
// Is any s1 in p dependent on any s2 in p? Yes: return such a s2. No: return nullptr.
// We could query independent(s1, s2) for all pairs, but that results
// in O(p.size * p.size) graph traversals. We can do it all in one BFS!
// Start the BFS traversal at all nodes from the pack. Traverse DepPreds
// recursively, for nodes that have at least depth min_d, which is the
// smallest depth of all nodes from the pack. Once we have traversed all
// those nodes, and have not found another node from the pack, we know
// that all nodes in the pack are independent.
Node* SuperWord::find_dependence(Node_List* p) {
  if (is_marked_reduction(p->at(0))) {
    return nullptr; // ignore reductions
  }
  ResourceMark rm;
  Unique_Node_List worklist; // traversal queue
  int min_d = depth(p->at(0));
  visited_clear();
  for (uint k = 0; k < p->size(); k++) {
    Node* n = p->at(k);
    min_d = MIN2(min_d, depth(n));
    worklist.push(n); // start traversal at all nodes in p
    visited_set(n); // mark node
  }
  for (uint i = 0; i < worklist.size(); i++) {
    Node* n = worklist.at(i);
    for (DepPreds preds(n, _dg); !preds.done(); preds.next()) {
      Node* pred = preds.current();
      if (in_bb(pred) && depth(pred) >= min_d) {
        if (visited_test(pred)) { // marked as in p?
          return pred;
        }
        worklist.push(pred);
      }
    }
  }
  return nullptr;
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

//------------------------------reduction---------------------------
// Is there a data path between s1 and s2 and the nodes reductions?
bool SuperWord::reduction(Node* s1, Node* s2) {
  bool retValue = false;
  int d1 = depth(s1);
  int d2 = depth(s2);
  if (d2 > d1) {
    if (is_marked_reduction(s1) && is_marked_reduction(s2)) {
      // This is an ordered set, so s1 should define s2
      for (DUIterator_Fast imax, i = s1->fast_outs(imax); i < imax; i++) {
        Node* t1 = s1->fast_out(i);
        if (t1 == s2) {
          // both nodes are reductions and connected
          retValue = true;
        }
      }
    }
  }

  return retValue;
}

//------------------------------independent_path------------------------------
// Helper for independent
bool SuperWord::independent_path(Node* shallow, Node* deep, uint dp) {
  if (dp >= 1000) return false; // stop deep recursion
  visited_set(deep);
  int shal_depth = depth(shallow);
  assert(shal_depth <= depth(deep), "must be");
  for (DepPreds preds(deep, _dg); !preds.done(); preds.next()) {
    Node* pred = preds.current();
    if (in_bb(pred) && !visited_test(pred)) {
      if (shallow == pred) {
        return false;
      }
      if (shal_depth < depth(pred) && !independent_path(shallow, pred, dp+1)) {
        return false;
      }
    }
  }
  return true;
}

//------------------------------set_alignment---------------------------
void SuperWord::set_alignment(Node* s1, Node* s2, int align) {
  set_alignment(s1, align);
  if (align == top_align || align == bottom_align) {
    set_alignment(s2, align);
  } else {
    set_alignment(s2, align + data_size(s1));
  }
}

//------------------------------data_size---------------------------
int SuperWord::data_size(Node* s) {
  int bsize = type2aelembytes(velt_basic_type(s));
  assert(bsize != 0, "valid size");
  return bsize;
}

//------------------------------extend_packlist---------------------------
// Extend packset by following use->def and def->use links from pack members.
void SuperWord::extend_packlist() {
  bool changed;
  do {
    packset_sort(_packset.length());
    changed = false;
    for (int i = 0; i < _packset.length(); i++) {
      Node_List* p = _packset.at(i);
      changed |= follow_use_defs(p);
      changed |= follow_def_uses(p);
    }
  } while (changed);

  if (_race_possible) {
    for (int i = 0; i < _packset.length(); i++) {
      Node_List* p = _packset.at(i);
      order_def_uses(p);
    }
  }

  if (TraceSuperWord) {
    tty->print_cr("\nAfter extend_packlist");
    print_packset();
  }
}

//------------------------------adjust_alignment_for_type_conversion---------------------------------
// Adjust the target alignment if conversion between different data size exists in def-use nodes.
int SuperWord::adjust_alignment_for_type_conversion(Node* s, Node* t, int align) {
  // Do not use superword for non-primitives
  BasicType bt1 = velt_basic_type(s);
  BasicType bt2 = velt_basic_type(t);
  if (!is_java_primitive(bt1) || !is_java_primitive(bt2)) {
    return align;
  }
  if (longer_type_for_conversion(s) != T_ILLEGAL ||
      longer_type_for_conversion(t) != T_ILLEGAL) {
    align = align / data_size(s) * data_size(t);
  }
  return align;
}

//------------------------------follow_use_defs---------------------------
// Extend the packset by visiting operand definitions of nodes in pack p
bool SuperWord::follow_use_defs(Node_List* p) {
  assert(p->size() == 2, "just checking");
  Node* s1 = p->at(0);
  Node* s2 = p->at(1);
  assert(s1->req() == s2->req(), "just checking");
  assert(alignment(s1) + data_size(s1) == alignment(s2), "just checking");

  if (s1->is_Load()) return false;

  NOT_PRODUCT(if(is_trace_alignment()) tty->print_cr("SuperWord::follow_use_defs: s1 %d, align %d", s1->_idx, alignment(s1));)
  bool changed = false;
  int start = s1->is_Store() ? MemNode::ValueIn   : 1;
  int end   = s1->is_Store() ? MemNode::ValueIn+1 : s1->req();
  for (int j = start; j < end; j++) {
    int align = alignment(s1);
    Node* t1 = s1->in(j);
    Node* t2 = s2->in(j);
    if (!in_bb(t1) || !in_bb(t2) || t1->is_Mem() || t2->is_Mem())  {
      // Only follow non-memory nodes in block - we do not want to resurrect misaligned packs.
      continue;
    }
    align = adjust_alignment_for_type_conversion(s1, t1, align);
    if (stmts_can_pack(t1, t2, align)) {
      if (est_savings(t1, t2) >= 0) {
        Node_List* pair = new Node_List();
        pair->push(t1);
        pair->push(t2);
        _packset.append(pair);
        NOT_PRODUCT(if(is_trace_alignment()) tty->print_cr("SuperWord::follow_use_defs: set_alignment(%d, %d, %d)", t1->_idx, t2->_idx, align);)
        set_alignment(t1, t2, align);
        changed = true;
      }
    }
  }
  return changed;
}

//------------------------------follow_def_uses---------------------------
// Extend the packset by visiting uses of nodes in pack p
bool SuperWord::follow_def_uses(Node_List* p) {
  bool changed = false;
  Node* s1 = p->at(0);
  Node* s2 = p->at(1);
  assert(p->size() == 2, "just checking");
  assert(s1->req() == s2->req(), "just checking");
  assert(alignment(s1) + data_size(s1) == alignment(s2), "just checking");

  if (s1->is_Store()) return false;

  int align = alignment(s1);
  NOT_PRODUCT(if(is_trace_alignment()) tty->print_cr("SuperWord::follow_def_uses: s1 %d, align %d", s1->_idx, align);)
  int savings = -1;
  int num_s1_uses = 0;
  Node* u1 = nullptr;
  Node* u2 = nullptr;
  for (DUIterator_Fast imax, i = s1->fast_outs(imax); i < imax; i++) {
    Node* t1 = s1->fast_out(i);
    num_s1_uses++;
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
      if (t2->Opcode() == Op_AddI && t2 == _lp->as_CountedLoop()->incr()) continue; // don't mess with the iv
      if (!opnd_positions_match(s1, t1, s2, t2))
        continue;
      int adjusted_align = alignment(s1);
      adjusted_align = adjust_alignment_for_type_conversion(s1, t1, adjusted_align);
      if (stmts_can_pack(t1, t2, adjusted_align)) {
        int my_savings = est_savings(t1, t2);
        if (my_savings > savings) {
          savings = my_savings;
          u1 = t1;
          u2 = t2;
          align = adjusted_align;
        }
      }
    }
  }
  if (num_s1_uses > 1) {
    _race_possible = true;
  }
  if (savings >= 0) {
    Node_List* pair = new Node_List();
    pair->push(u1);
    pair->push(u2);
    _packset.append(pair);
    NOT_PRODUCT(if(is_trace_alignment()) tty->print_cr("SuperWord::follow_def_uses: set_alignment(%d, %d, %d)", u1->_idx, u2->_idx, align);)
    set_alignment(u1, u2, align);
    changed = true;
  }
  return changed;
}

//------------------------------order_def_uses---------------------------
// For extended packsets, ordinally arrange uses packset by major component
void SuperWord::order_def_uses(Node_List* p) {
  Node* s1 = p->at(0);

  if (s1->is_Store()) return;

  // reductions are always managed beforehand
  if (is_marked_reduction(s1)) return;

  for (DUIterator_Fast imax, i = s1->fast_outs(imax); i < imax; i++) {
    Node* t1 = s1->fast_out(i);

    // Only allow operand swap on commuting operations
    if (!t1->is_Add() && !t1->is_Mul() && !VectorNode::is_muladds2i(t1)) {
      break;
    }

    // Now find t1's packset
    Node_List* p2 = nullptr;
    for (int j = 0; j < _packset.length(); j++) {
      p2 = _packset.at(j);
      Node* first = p2->at(0);
      if (t1 == first) {
        break;
      }
      p2 = nullptr;
    }
    // Arrange all sub components by the major component
    if (p2 != nullptr) {
      for (uint j = 1; j < p->size(); j++) {
        Node* d1 = p->at(j);
        Node* u1 = p2->at(j);
        opnd_positions_match(s1, t1, d1, u1);
      }
    }
  }
}

//---------------------------opnd_positions_match-------------------------
// Is the use of d1 in u1 at the same operand position as d2 in u2?
bool SuperWord::opnd_positions_match(Node* d1, Node* u1, Node* d2, Node* u2) {
  // check reductions to see if they are marshalled to represent the reduction
  // operator in a specified opnd
  if (is_marked_reduction(u1) && is_marked_reduction(u2)) {
    // ensure reductions have phis and reduction definitions feeding the 1st operand
    Node* first = u1->in(2);
    if (first->is_Phi() || is_marked_reduction(first)) {
      u1->swap_edges(1, 2);
    }
    // ensure reductions have phis and reduction definitions feeding the 1st operand
    first = u2->in(2);
    if (first->is_Phi() || is_marked_reduction(first)) {
      u2->swap_edges(1, 2);
    }
    return true;
  }

  uint ct = u1->req();
  if (ct != u2->req()) return false;
  uint i1 = 0;
  uint i2 = 0;
  do {
    for (i1++; i1 < ct; i1++) if (u1->in(i1) == d1) break;
    for (i2++; i2 < ct; i2++) if (u2->in(i2) == d2) break;
    if (i1 != i2) {
      if ((i1 == (3-i2)) && (u2->is_Add() || u2->is_Mul())) {
        // Further analysis relies on operands position matching.
        u2->swap_edges(i1, i2);
      } else if (VectorNode::is_muladds2i(u2) && u1 != u2) {
        if (i1 == 5 - i2) { // ((i1 == 3 && i2 == 2) || (i1 == 2 && i2 == 3) || (i1 == 1 && i2 == 4) || (i1 == 4 && i2 == 1))
          u2->swap_edges(1, 2);
          u2->swap_edges(3, 4);
        }
        if (i1 == 3 - i2 || i1 == 7 - i2) { // ((i1 == 1 && i2 == 2) || (i1 == 2 && i2 == 1) || (i1 == 3 && i2 == 4) || (i1 == 4 && i2 == 3))
          u2->swap_edges(2, 3);
          u2->swap_edges(1, 4);
        }
        return false; // Just swap the edges, the muladds2i nodes get packed in follow_use_defs
      } else {
        return false;
      }
    } else if (i1 == i2 && VectorNode::is_muladds2i(u2) && u1 != u2) {
      u2->swap_edges(1, 3);
      u2->swap_edges(2, 4);
      return false; // Just swap the edges, the muladds2i nodes get packed in follow_use_defs
    }
  } while (i1 < ct);
  return true;
}

//------------------------------est_savings---------------------------
// Estimate the savings from executing s1 and s2 as a pack
int SuperWord::est_savings(Node* s1, Node* s2) {
  int save_in = 2 - 1; // 2 operations per instruction in packed form

  // inputs
  for (uint i = 1; i < s1->req(); i++) {
    Node* x1 = s1->in(i);
    Node* x2 = s2->in(i);
    if (x1 != x2) {
      if (are_adjacent_refs(x1, x2)) {
        save_in += adjacent_profit(x1, x2);
      } else if (!in_packset(x1, x2)) {
        save_in -= pack_cost(2);
      } else {
        save_in += unpack_cost(2);
      }
    }
  }

  // uses of result
  uint ct = 0;
  int save_use = 0;
  for (DUIterator_Fast imax, i = s1->fast_outs(imax); i < imax; i++) {
    Node* s1_use = s1->fast_out(i);
    for (int j = 0; j < _packset.length(); j++) {
      Node_List* p = _packset.at(j);
      if (p->at(0) == s1_use) {
        for (DUIterator_Fast kmax, k = s2->fast_outs(kmax); k < kmax; k++) {
          Node* s2_use = s2->fast_out(k);
          if (p->at(p->size()-1) == s2_use) {
            ct++;
            if (are_adjacent_refs(s1_use, s2_use)) {
              save_use += adjacent_profit(s1_use, s2_use);
            }
          }
        }
      }
    }
  }

  if (ct < s1->outcnt()) save_use += unpack_cost(1);
  if (ct < s2->outcnt()) save_use += unpack_cost(1);

  return MAX2(save_in, save_use);
}

//------------------------------costs---------------------------
int SuperWord::adjacent_profit(Node* s1, Node* s2) { return 2; }
int SuperWord::pack_cost(int ct)   { return ct; }
int SuperWord::unpack_cost(int ct) { return ct; }

//------------------------------combine_packs---------------------------
// Combine packs A and B with A.last == B.first into A.first..,A.last,B.second,..B.last
void SuperWord::combine_packs() {
#ifdef ASSERT
  for (int i = 0; i < _packset.length(); i++) {
    assert(_packset.at(i) != nullptr, "no nullptr in packset");
  }
#endif

  bool changed = true;
  // Combine packs regardless max vector size.
  while (changed) {
    changed = false;
    for (int i = 0; i < _packset.length(); i++) {
      Node_List* p1 = _packset.at(i);
      if (p1 == nullptr) continue;
      // Because of sorting we can start at i + 1
      for (int j = i + 1; j < _packset.length(); j++) {
        Node_List* p2 = _packset.at(j);
        if (p2 == nullptr) continue;
        if (p1->at(p1->size()-1) == p2->at(0)) {
          for (uint k = 1; k < p2->size(); k++) {
            p1->push(p2->at(k));
          }
          _packset.at_put(j, nullptr);
          changed = true;
        }
      }
    }
  }

  // Split packs which have size greater then max vector size.
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* p1 = _packset.at(i);
    if (p1 != nullptr) {
      uint max_vlen = max_vector_size_in_def_use_chain(p1->at(0)); // Max elements in vector
      assert(is_power_of_2(max_vlen), "sanity");
      uint psize = p1->size();
      if (!is_power_of_2(psize)) {
        // We currently only support power-of-2 sizes for vectors.
#ifndef PRODUCT
        if (TraceSuperWord) {
          tty->cr();
          tty->print_cr("WARNING: Removed pack[%d] with size that is not a power of 2:", i);
          print_pack(p1);
        }
#endif
        _packset.at_put(i, nullptr);
        continue;
      }
      if (psize > max_vlen) {
        Node_List* pack = new Node_List();
        for (uint j = 0; j < psize; j++) {
          pack->push(p1->at(j));
          if (pack->size() >= max_vlen) {
            assert(is_power_of_2(pack->size()), "sanity");
            _packset.append(pack);
            pack = new Node_List();
          }
        }
        _packset.at_put(i, nullptr);
      }
    }
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
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* p = _packset.at(i);
    if (p != nullptr) {
      Node* dependence = find_dependence(p);
      if (dependence != nullptr) {
#ifndef PRODUCT
        if (TraceSuperWord) {
          tty->cr();
          tty->print_cr("WARNING: Found dependency at distance greater than 1.");
          dependence->dump();
          tty->print_cr("In pack[%d]", i);
          print_pack(p);
        }
#endif
        _packset.at_put(i, nullptr);
      }
    }
  }

  // Remove all nullptr from packset
  compress_packset();

#ifndef PRODUCT
  if (TraceSuperWord) {
    tty->print_cr("\nAfter combine_packs");
    print_packset();
  }
#endif
}

// Find the set of alignment solutions for load/store pack.
const AlignmentSolution* SuperWord::pack_alignment_solution(Node_List* pack) {
  assert(pack != nullptr && (pack->at(0)->is_Load() || pack->at(0)->is_Store()), "only load/store packs");

  const MemNode* mem_ref = pack->at(0)->as_Mem();
  VPointer mem_ref_p(mem_ref, phase(), lpt(), nullptr, false);
  const CountedLoopEndNode* pre_end = lp()->pre_loop_end();
  assert(pre_end->stride_is_con(), "pre loop stride is constant");

  AlignmentSolver solver(pack->at(0)->as_Mem(),
                         pack->size(),
                         mem_ref_p.base(),
                         mem_ref_p.offset_in_bytes(),
                         mem_ref_p.invar(),
                         mem_ref_p.invar_factor(),
                         mem_ref_p.scale_in_bytes(),
                         pre_end->init_trip(),
                         pre_end->stride_con(),
                         iv_stride()
                         DEBUG_ONLY(COMMA is_trace_align_vector()));
  return solver.solve();
}

// Ensure all packs are aligned, if AlignVector is on.
// Find an alignment solution: find the set of pre_iter that memory align all packs.
// Start with the maximal set (pre_iter >= 0) and filter it with the constraints
// that the packs impose. Remove packs that do not have a compatible solution.
void SuperWord::filter_packs_for_alignment() {
  // We do not need to filter if no alignment is required.
  if (!vectors_should_be_aligned()) {
    return;
  }

#ifndef PRODUCT
  if (TraceSuperWord || is_trace_align_vector()) {
    tty->print_cr("\nfilter_packs_for_alignment:");
  }
#endif

  ResourceMark rm;

  // Start with trivial (unconstrained) solution space
  AlignmentSolution const* current = new TrivialAlignmentSolution();
  int mem_ops_count = 0;
  int mem_ops_rejected = 0;
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* p = _packset.at(i);
    if (p != nullptr) {
      if (p->at(0)->is_Load() || p->at(0)->is_Store()) {
        mem_ops_count++;
        // Find solution for pack p, and filter with current solution.
        const AlignmentSolution* s = pack_alignment_solution(p);
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
          // Solution failed or is not compatible, remove pack i.
          _packset.at_put(i, nullptr);
          mem_ops_rejected++;
        } else {
          // Solution is compatible.
          current = intersect;
        }
      }
    }
  }

#ifndef PRODUCT
  if (TraceSuperWord || is_trace_align_vector()) {
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
    set_align_to_ref(current->as_constrained()->mem_ref());
  }

  // Remove all nullptr from packset
  compress_packset();
}

// Compress packset, such that it has no nullptr entries
void SuperWord::compress_packset() {
  int j = 0;
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* p = _packset.at(i);
    if (p != nullptr) {
      _packset.at_put(j, p);
      j++;
    }
  }
  _packset.trunc_to(j);
}

//-----------------------------construct_my_pack_map--------------------------
// Construct the map from nodes to packs.  Only valid after the
// point where a node is only in one pack (after combine_packs).
void SuperWord::construct_my_pack_map() {
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* p = _packset.at(i);
    for (uint j = 0; j < p->size(); j++) {
      Node* s = p->at(j);
#ifdef ASSERT
      if (my_pack(s) != nullptr) {
        s->dump(1);
        tty->print_cr("packs[%d]:", i);
        print_pack(p);
        assert(false, "only in one pack");
      }
#endif
      set_my_pack(s, p);
    }
  }
}

//------------------------------filter_packs---------------------------
// Remove packs that are not implemented or not profitable.
void SuperWord::filter_packs() {
  // Remove packs that are not implemented
  for (int i = _packset.length() - 1; i >= 0; i--) {
    Node_List* pk = _packset.at(i);
    bool impl = implemented(pk);
    if (!impl) {
#ifndef PRODUCT
      if ((TraceSuperWord && Verbose) || _vector_loop_debug) {
        tty->print_cr("Unimplemented");
        pk->at(0)->dump();
      }
#endif
      remove_pack_at(i);
    }
    Node *n = pk->at(0);
    if (is_marked_reduction(n)) {
      _num_reductions++;
    } else {
      _num_work_vecs++;
    }
  }

  // Remove packs that are not profitable
  bool changed;
  do {
    changed = false;
    for (int i = _packset.length() - 1; i >= 0; i--) {
      Node_List* pk = _packset.at(i);
      bool prof = profitable(pk);
      if (!prof) {
#ifndef PRODUCT
        if ((TraceSuperWord && Verbose) || _vector_loop_debug) {
          tty->print_cr("Unprofitable");
          pk->at(0)->dump();
        }
#endif
        remove_pack_at(i);
        changed = true;
      }
    }
  } while (changed);

#ifndef PRODUCT
  if (TraceSuperWord) {
    tty->print_cr("\nAfter filter_packs");
    print_packset();
    tty->cr();
  }
#endif
}

//------------------------------implemented---------------------------
// Can code be generated for pack p?
bool SuperWord::implemented(Node_List* p) {
  bool retValue = false;
  Node* p0 = p->at(0);
  if (p0 != nullptr) {
    int opc = p0->Opcode();
    uint size = p->size();
    if (is_marked_reduction(p0)) {
      const Type *arith_type = p0->bottom_type();
      // Length 2 reductions of INT/LONG do not offer performance benefits
      if (((arith_type->basic_type() == T_INT) || (arith_type->basic_type() == T_LONG)) && (size == 2)) {
        retValue = false;
      } else {
        retValue = ReductionNode::implemented(opc, size, arith_type->basic_type());
      }
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
    } else if (requires_long_to_int_conversion(opc)) {
      // Java API for Long.bitCount/numberOfLeadingZeros/numberOfTrailingZeros
      // returns int type, but Vector API for them returns long type. To unify
      // the implementation in backend, superword splits the vector implementation
      // for Java API into an execution node with long type plus another node
      // converting long to int.
      retValue = VectorNode::implemented(opc, size, T_LONG) &&
                 VectorCastNode::implemented(Op_ConvL2I, size, T_LONG, T_INT);
    } else {
      // Vector unsigned right shift for signed subword types behaves differently
      // from Java Spec. But when the shift amount is a constant not greater than
      // the number of sign extended bits, the unsigned right shift can be
      // vectorized to a signed right shift.
      if (VectorNode::can_transform_shift_op(p0, velt_basic_type(p0))) {
        opc = Op_RShiftI;
      }
      retValue = VectorNode::implemented(opc, size, velt_basic_type(p0));
    }
  }
  return retValue;
}

bool SuperWord::requires_long_to_int_conversion(int opc) {
  switch(opc) {
    case Op_PopCountL:
    case Op_CountLeadingZerosL:
    case Op_CountTrailingZerosL:
      return true;
    default:
      return false;
  }
}

//------------------------------same_inputs--------------------------
// For pack p, are all idx operands the same?
bool SuperWord::same_inputs(Node_List* p, int idx) {
  Node* p0 = p->at(0);
  uint vlen = p->size();
  Node* p0_def = p0->in(idx);
  for (uint i = 1; i < vlen; i++) {
    Node* pi = p->at(i);
    Node* pi_def = pi->in(idx);
    if (p0_def != pi_def) {
      return false;
    }
  }
  return true;
}

//------------------------------profitable---------------------------
// For pack p, are all operands and all uses (with in the block) vector?
bool SuperWord::profitable(Node_List* p) {
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
    Node_List* second_pk = my_pack(second_in);
    if ((second_pk == nullptr) || (_num_work_vecs == _num_reductions)) {
      // Unmark reduction if no parent pack or if not enough work
      // to cover reduction expansion overhead
      _loop_reductions.remove(p0->_idx);
      return false;
    } else if (second_pk->size() != p->size()) {
      return false;
    }
  }
  if (VectorNode::is_shift(p0)) {
    // For now, return false if shift count is vector or not scalar promotion
    // case (different shift counts) because it is not supported yet.
    Node* cnt = p0->in(2);
    Node_List* cnt_pk = my_pack(cnt);
    if (cnt_pk != nullptr)
      return false;
    if (!same_inputs(p, 2))
      return false;
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
                ((use->is_Phi() && use->in(0) == _lpt->_head) ||
                 (!_lpt->is_member(_phase->get_loop(_phase->ctrl_or_self(use))) && i == p->size()-1))) {
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
    if (bol == nullptr || my_pack(bol) == nullptr) {
      return false;
    }
    // Verify that Bool has a matching Cmp pack
    CmpNode* cmp = bol->in(1)->as_Cmp();
    if (cmp == nullptr || my_pack(cmp) == nullptr) {
      return false;
    }
  }
  return true;
}

#ifdef ASSERT
void SuperWord::verify_packs() {
  // Verify independence at pack level.
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* p = _packset.at(i);
    Node* dependence = find_dependence(p);
    if (dependence != nullptr) {
      tty->print_cr("Other nodes in pack have dependence on:");
      dependence->dump();
      tty->print_cr("The following nodes are not independent:");
      for (uint k = 0; k < p->size(); k++) {
        Node* n = p->at(k);
        if (!independent(n, dependence)) {
          n->dump();
        }
      }
      tty->print_cr("They are all from pack[%d]", i);
      print_pack(p);
    }
    assert(dependence == nullptr, "all nodes in pack must be mutually independent");
  }

  // Verify all nodes in packset have my_pack set correctly.
  Unique_Node_List processed;
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* p = _packset.at(i);
    for (uint k = 0; k < p->size(); k++) {
      Node* n = p->at(k);
      assert(in_bb(n), "only nodes in bb can be in packset");
      assert(!processed.member(n), "node should only occur once in packset");
      assert(my_pack(n) == p, "n has consisten packset info");
      processed.push(n);
    }
  }

  // Check that no other node has my_pack set.
  for (int i = 0; i < _block.length(); i++) {
    Node* n = _block.at(i);
    if (!processed.member(n)) {
      assert(my_pack(n) == nullptr, "should not have pack if not in packset");
    }
  }
}
#endif

// The PacksetGraph combines the DepPreds graph with the packset. In the PackSet
// graph, we have two kinds of nodes:
//  (1) pack-node:   Represents all nodes of some pack p in a single node, which
//                   shall later become a vector node.
//  (2) scalar-node: Represents a node that is not in any pack.
// For any edge (n1, n2) in DepPreds, we add an edge to the PacksetGraph for the
// PacksetGraph nodes corresponding to n1 and n2.
// We work from the DepPreds graph, because it gives us all the data-dependencies,
// as well as more refined memory-dependencies than the C2 graph. DepPreds does
// not have cycles. But packing nodes can introduce cyclic dependencies. Example:
//
//                                                       +--------+
//  A -> X                                               |        v
//                     Pack [A,B] and [X,Y]             [A,B]    [X,Y]
//  Y -> B                                                 ^        |
//                                                         +--------+
//
class PacksetGraph {
private:
  // pid: packset graph node id.
  GrowableArray<int> _pid;                 // bb_idx(n) -> pid
  GrowableArray<Node*> _pid_to_node;       // one node per pid, find rest via my_pack
  GrowableArray<GrowableArray<int>> _out;  // out-edges
  GrowableArray<int> _incnt;               // number of (implicit) in-edges
  int _max_pid = 0;

  bool _schedule_success;

  SuperWord* _slp;
public:
  PacksetGraph(SuperWord* slp)
  : _pid(8, 0, /* default */ 0), _slp(slp) {
  }
  // Get pid, if there is a packset node that n belongs to. Else return 0.
  int get_pid_or_zero(const Node* n) const {
    if (!_slp->in_bb(n)) {
      return 0;
    }
    int idx = _slp->bb_idx(n);
    if (idx >= _pid.length()) {
      return 0;
    } else {
      return _pid.at(idx);
    }
  }
  int get_pid(const Node* n) {
    int poz = get_pid_or_zero(n);
    assert(poz != 0, "pid should not be zero");
    return poz;
  }
  void set_pid(Node* n, int pid) {
    assert(n != nullptr && pid > 0, "sane inputs");
    assert(_slp->in_bb(n), "must be");
    int idx = _slp->bb_idx(n);
    _pid.at_put_grow(idx, pid);
    _pid_to_node.at_put_grow(pid - 1, n, nullptr);
  }
  Node* get_node(int pid) {
    assert(pid > 0 && pid <= _pid_to_node.length(), "pid must be mapped");
    Node* n = _pid_to_node.at(pid - 1);
    assert(n != nullptr, "sanity");
    return n;
  }
  int new_pid() {
    _incnt.push(0);
    _out.push(GrowableArray<int>());
    return ++_max_pid;
  }
  int incnt(int pid) { return _incnt.at(pid - 1); }
  void incnt_set(int pid, int cnt) { return _incnt.at_put(pid - 1, cnt); }
  GrowableArray<int>& out(int pid) { return _out.at(pid - 1); }
  bool schedule_success() const { return _schedule_success; }

  // Create nodes (from packs and scalar-nodes), and add edges, based on DepPreds.
  void build() {
    const GrowableArray<Node_List*> &packset = _slp->packset();
    const GrowableArray<Node*> &block = _slp->block();
    const DepGraph &dg = _slp->dg();
    // Map nodes in packsets
    for (int i = 0; i < packset.length(); i++) {
      Node_List* p = packset.at(i);
      int pid = new_pid();
      for (uint k = 0; k < p->size(); k++) {
        Node* n = p->at(k);
        set_pid(n, pid);
        assert(_slp->my_pack(n) == p, "matching packset");
      }
    }

    int max_pid_packset = _max_pid;

    // Map nodes not in packset
    for (int i = 0; i < block.length(); i++) {
      Node* n = block.at(i);
      if (n->is_Phi() || n->is_CFG()) {
        continue; // ignore control flow
      }
      int pid = get_pid_or_zero(n);
      if (pid == 0) {
        pid = new_pid();
        set_pid(n, pid);
        assert(_slp->my_pack(n) == nullptr, "no packset");
      }
    }

    // Map edges for packset nodes
    VectorSet set;
    for (int i = 0; i < packset.length(); i++) {
      Node_List* p = packset.at(i);
      set.clear();
      int pid = get_pid(p->at(0));
      for (uint k = 0; k < p->size(); k++) {
        Node* n = p->at(k);
        assert(pid == get_pid(n), "all nodes in pack have same pid");
        for (DepPreds preds(n, dg); !preds.done(); preds.next()) {
          Node* pred = preds.current();
          int pred_pid = get_pid_or_zero(pred);
          if (pred_pid == pid && _slp->is_marked_reduction(n)) {
            continue; // reduction -> self-cycle is not a cyclic dependency
          }
          // Only add edges once, and only for mapped nodes (in block)
          if (pred_pid > 0 && !set.test_set(pred_pid)) {
            incnt_set(pid, incnt(pid) + 1); // increment
            out(pred_pid).push(pid);
          }
        }
      }
    }

    // Map edges for nodes not in packset
    for (int i = 0; i < block.length(); i++) {
      Node* n = block.at(i);
      int pid = get_pid_or_zero(n); // zero for Phi or CFG
      if (pid <= max_pid_packset) {
        continue; // Only scalar-nodes
      }
      for (DepPreds preds(n, dg); !preds.done(); preds.next()) {
        Node* pred = preds.current();
        int pred_pid = get_pid_or_zero(pred);
        // Only add edges for mapped nodes (in block)
        if (pred_pid > 0) {
          incnt_set(pid, incnt(pid) + 1); // increment
          out(pred_pid).push(pid);
        }
      }
    }
  }

  // Schedule nodes of PacksetGraph to worklist, using topsort: schedule a node
  // that has zero incnt. If a PacksetGraph node corresponds to memops, then add
  // those to the memops_schedule. At the end, we return the memops_schedule, and
  // note if topsort was successful.
  Node_List schedule() {
    Node_List memops_schedule;
    GrowableArray<int> worklist;
    // Directly schedule all nodes without precedence
    for (int pid = 1; pid <= _max_pid; pid++) {
      if (incnt(pid) == 0) {
        worklist.push(pid);
      }
    }
    // Continue scheduling via topological sort
    for (int i = 0; i < worklist.length(); i++) {
      int pid = worklist.at(i);

      // Add memops to memops_schedule
      Node* n = get_node(pid);
      Node_List* p = _slp->my_pack(n);
      if (n->is_Mem()) {
        if (p == nullptr) {
          memops_schedule.push(n);
        } else {
          for (uint k = 0; k < p->size(); k++) {
            memops_schedule.push(p->at(k));
            assert(p->at(k)->is_Mem(), "only schedule memops");
          }
        }
      }

      // Decrement incnt for all successors
      for (int j = 0; j < out(pid).length(); j++){
        int pid_use = out(pid).at(j);
        int incnt_use = incnt(pid_use) - 1;
        incnt_set(pid_use, incnt_use);
        // Did use lose its last input?
        if (incnt_use == 0) {
          worklist.push(pid_use);
        }
      }
    }

    // Was every pid scheduled? If not, we found some cycles in the PacksetGraph.
    _schedule_success = (worklist.length() == _max_pid);
    return memops_schedule;
  }

  // Print the PacksetGraph.
  // print_nodes = true: print all C2 nodes beloning to PacksetGrahp node.
  // print_zero_incnt = false: do not print nodes that have no in-edges (any more).
  void print(bool print_nodes, bool print_zero_incnt) {
    const GrowableArray<Node*> &block = _slp->block();
    tty->print_cr("PacksetGraph");
    for (int pid = 1; pid <= _max_pid; pid++) {
      if (incnt(pid) == 0 && !print_zero_incnt) {
        continue;
      }
      tty->print("Node %d. incnt %d [", pid, incnt(pid));
      for (int j = 0; j < out(pid).length(); j++) {
        tty->print("%d ", out(pid).at(j));
      }
      tty->print_cr("]");
#ifndef PRODUCT
      if (print_nodes) {
        for (int i = 0; i < block.length(); i++) {
          Node* n = block.at(i);
          if (get_pid_or_zero(n) == pid) {
            tty->print("    ");
            n->dump();
          }
        }
      }
#endif
    }
  }
};

// The C2 graph (specifically the memory graph), needs to be re-ordered.
// (1) Build the PacksetGraph. It combines the DepPreds graph with the
//     packset. The PacksetGraph gives us the dependencies that must be
//     respected after scheduling.
// (2) Schedule the PacksetGraph to the memops_schedule, which represents
//     a linear order of all memops in the body. The order respects the
//     dependencies of the PacksetGraph.
// (3) If the PacksetGraph has cycles, we cannot schedule. Abort.
// (4) Use the memops_schedule to re-order the memops in all slices.
void SuperWord::schedule() {
  if (_packset.length() == 0) {
    return; // empty packset
  }
  ResourceMark rm;

  // (1) Build the PacksetGraph.
  PacksetGraph graph(this);
  graph.build();

  // (2) Schedule the PacksetGraph.
  Node_List memops_schedule = graph.schedule();

  // (3) Check if the PacksetGraph schedule succeeded (had no cycles).
  // We now know that we only have independent packs, see verify_packs.
  // This is a necessary but not a sufficient condition for an acyclic
  // graph (DAG) after scheduling. Thus, we must check if the packs have
  // introduced a cycle. The SuperWord paper mentions the need for this
  // in "3.7 Scheduling".
  if (!graph.schedule_success()) {
    if (TraceSuperWord) {
      tty->print_cr("SuperWord::schedule found cycle in PacksetGraph:");
      graph.print(true, false);
      tty->print_cr("removing all packs from packset.");
    }
    _packset.clear();
    return;
  }

#ifndef PRODUCT
  if (TraceSuperWord) {
    tty->print_cr("SuperWord::schedule: memops_schedule:");
    memops_schedule.dump();
  }
#endif

  CountedLoopNode* cl = lpt()->_head->as_CountedLoop();
  _phase->C->print_method(PHASE_SUPERWORD1_BEFORE_SCHEDULE, 4, cl);

  // (4) Use the memops_schedule to re-order the memops in all slices.
  schedule_reorder_memops(memops_schedule);
}


// Reorder the memory graph for all slices in parallel. We walk over the schedule once,
// and track the current memory state of each slice.
void SuperWord::schedule_reorder_memops(Node_List &memops_schedule) {
  int max_slices = _phase->C->num_alias_types();
  // When iterating over the memops_schedule, we keep track of the current memory state,
  // which is the Phi or a store in the loop.
  GrowableArray<Node*> current_state_in_slice(max_slices, max_slices, nullptr);
  // The memory state after the loop is the last store inside the loop. If we reorder the
  // loop we may have a different last store, and we need to adjust the uses accordingly.
  GrowableArray<Node*> old_last_store_in_slice(max_slices, max_slices, nullptr);

  // (1) Set up the initial memory state from Phi. And find the old last store.
  for (int i = 0; i < _mem_slice_head.length(); i++) {
    Node* phi  = _mem_slice_head.at(i);
    assert(phi->is_Phi(), "must be phi");
    int alias_idx = _phase->C->get_alias_index(phi->adr_type());
    current_state_in_slice.at_put(alias_idx, phi);

    // If we have a memory phi, we have a last store in the loop, find it over backedge.
    StoreNode* last_store = phi->in(2)->as_Store();
    old_last_store_in_slice.at_put(alias_idx, last_store);
  }

  // (2) Walk over memops_schedule, append memops to the current state
  //     of that slice. If it is a Store, we take it as the new state.
  for (uint i = 0; i < memops_schedule.size(); i++) {
    MemNode* n = memops_schedule.at(i)->as_Mem();
    assert(n->is_Load() || n->is_Store(), "only loads or stores");
    int alias_idx = _phase->C->get_alias_index(n->adr_type());
    Node* current_state = current_state_in_slice.at(alias_idx);
    if (current_state == nullptr) {
      // If there are only loads in a slice, we never update the memory
      // state in the loop, hence there is no phi for the memory state.
      // We just keep the old memory state that was outside the loop.
      assert(n->is_Load() && !in_bb(n->in(MemNode::Memory)),
             "only loads can have memory state from outside loop");
    } else {
      _igvn.replace_input_of(n, MemNode::Memory, current_state);
      if (n->is_Store()) {
        current_state_in_slice.at_put(alias_idx, n);
      }
    }
  }

  // (3) For each slice, we add the current state to the backedge
  //     in the Phi. Further, we replace uses of the old last store
  //     with uses of the new last store (current_state).
  Node_List uses_after_loop;
  for (int i = 0; i < _mem_slice_head.length(); i++) {
    Node* phi  = _mem_slice_head.at(i);
    int alias_idx = _phase->C->get_alias_index(phi->adr_type());
    Node* current_state = current_state_in_slice.at(alias_idx);
    assert(current_state != nullptr, "slice is mapped");
    assert(current_state != phi, "did some work in between");
    assert(current_state->is_Store(), "sanity");
    _igvn.replace_input_of(phi, 2, current_state);

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
    for (uint k = 0; k < uses_after_loop.size(); k++) {
      Node* use = uses_after_loop.at(k);
      for (uint j = 0; j < use->req(); j++) {
        Node* def = use->in(j);
        if (def == last_store) {
          _igvn.replace_input_of(use, j, current_state);
        }
      }
    }
  }
}

//------------------------------output---------------------------
// Convert packs into vector node operations
// At this point, all correctness and profitability checks have passed.
// We start the irreversible process of editing the C2 graph. Should
// there be an unexpected situation (assert fails), then we can only
// bail out of the compilation, as the graph has already been partially
// modified. We bail out, and retry without SuperWord.
bool SuperWord::output() {
  CountedLoopNode *cl = lpt()->_head->as_CountedLoop();
  assert(cl->is_main_loop(), "SLP should only work on main loops");
  Compile* C = _phase->C;
  if (_packset.length() == 0) {
    return false;
  }

#ifndef PRODUCT
  if (TraceLoopOpts) {
    tty->print("SuperWord::output    ");
    lpt()->dump_head();
  }
#endif
  _phase->C->print_method(PHASE_SUPERWORD2_BEFORE_OUTPUT, 4, cl);

  adjust_pre_loop_limit_to_align_main_loop_vectors();

  // Insert extract (unpack) operations for scalar uses
  for (int i = 0; i < _packset.length(); i++) {
    insert_extracts(_packset.at(i));
  }

  uint max_vlen_in_bytes = 0;
  uint max_vlen = 0;

  for (int i = 0; i < _block.length(); i++) {
    Node* n = _block.at(i);
    Node_List* p = my_pack(n);
    if (p != nullptr && n == p->at(p->size()-1)) {
      // After schedule_reorder_memops, we know that the memops have the same order in the pack
      // as in the memory slice. Hence, "first" is the first memop in the slice from the pack,
      // and "n" is the last node in the slice from the pack.
      Node* first = p->at(0);
      uint vlen = p->size();
      uint vlen_in_bytes = 0;
      Node* vn = nullptr;
      NOT_PRODUCT(if(is_trace_cmov()) {tty->print_cr("VPointer::output: %d executed first, %d executed last in pack", first->_idx, n->_idx); print_pack(p);})
      int   opc = n->Opcode();
      if (n->is_Load()) {
        Node* ctl = n->in(MemNode::Control);
        Node* mem = first->in(MemNode::Memory);
        // Set the memory dependency of the LoadVector as early as possible.
        // Walk up the memory chain, and ignore any StoreVector that provably
        // does not have any memory dependency.
        while (mem->is_StoreVector()) {
          VPointer p_store(mem->as_Mem(), phase(), lpt(), nullptr, false);
          if (p_store.overlap_possible_with_any_in(p)) {
            break;
          } else {
            mem = mem->in(MemNode::Memory);
          }
        }
        Node* adr = first->in(MemNode::Address);
        const TypePtr* atyp = n->adr_type();
        vn = LoadVectorNode::make(opc, ctl, mem, adr, atyp, vlen, velt_basic_type(n), control_dependency(p));
        vlen_in_bytes = vn->as_LoadVector()->memory_size();
      } else if (n->is_Store()) {
        // Promote value to be stored to vector
        Node* val = vector_opd(p, MemNode::ValueIn);
        if (val == nullptr) {
          assert(false, "input to vector store was not created");
          C->record_failure(C2Compiler::retry_no_superword());
          return false; // bailout
        }

        Node* ctl = n->in(MemNode::Control);
        Node* mem = first->in(MemNode::Memory);
        Node* adr = first->in(MemNode::Address);
        const TypePtr* atyp = n->adr_type();
        vn = StoreVectorNode::make(opc, ctl, mem, adr, atyp, val, vlen);
        vlen_in_bytes = vn->as_StoreVector()->memory_size();
      } else if (VectorNode::is_scalar_rotate(n)) {
        Node* in1 = first->in(1);
        Node* in2 = first->in(2);
        // If rotation count is non-constant or greater than 8bit value create a vector.
        if (!in2->is_Con() || !Matcher::supports_vector_constant_rotates(in2->get_int())) {
          in2 =  vector_opd(p, 2);
        }
        vn = VectorNode::make(opc, in1, in2, vlen, velt_basic_type(n));
        vlen_in_bytes = vn->as_Vector()->length_in_bytes();
      } else if (VectorNode::is_roundopD(n)) {
        Node* in1 = vector_opd(p, 1);
        Node* in2 = first->in(2);
        assert(in2->is_Con(), "Constant rounding mode expected.");
        vn = VectorNode::make(opc, in1, in2, vlen, velt_basic_type(n));
        vlen_in_bytes = vn->as_Vector()->length_in_bytes();
      } else if (VectorNode::is_muladds2i(n)) {
        assert(n->req() == 5u, "MulAddS2I should have 4 operands.");
        Node* in1 = vector_opd(p, 1);
        Node* in2 = vector_opd(p, 2);
        vn = VectorNode::make(opc, in1, in2, vlen, velt_basic_type(n));
        vlen_in_bytes = vn->as_Vector()->length_in_bytes();
      } else if (opc == Op_SignumF || opc == Op_SignumD) {
        assert(n->req() == 4, "four inputs expected");
        Node* in = vector_opd(p, 1);
        Node* zero = vector_opd(p, 2);
        Node* one = vector_opd(p, 3);
        vn = VectorNode::make(opc, in, zero, one, vlen, velt_basic_type(n));
        vlen_in_bytes = vn->as_Vector()->length_in_bytes();
      } else if (n->is_Cmp()) {
        // Bool + Cmp + CMove -> VectorMaskCmp + VectorBlend
        continue;
      } else if (n->is_Bool()) {
        // Bool + Cmp + CMove -> VectorMaskCmp + VectorBlend
        continue;
      } else if (n->is_CMove()) {
        // Bool + Cmp + CMove -> VectorMaskCmp + VectorBlend

        BoolNode* bol = n->in(1)->as_Bool();
        assert(bol != nullptr, "must have Bool above CMove");
        BoolTest::mask bol_test = bol->_test._test;
        assert(bol_test == BoolTest::eq ||
               bol_test == BoolTest::ne ||
               bol_test == BoolTest::ge ||
               bol_test == BoolTest::gt ||
               bol_test == BoolTest::lt ||
               bol_test == BoolTest::le,
               "CMove bool should be one of: eq,ne,ge,ge,lt,le");
        Node_List* p_bol = my_pack(bol);
        assert(p_bol != nullptr, "CMove must have matching Bool pack");

#ifdef ASSERT
        for (uint j = 0; j < p_bol->size(); j++) {
          Node* m = p_bol->at(j);
          assert(m->as_Bool()->_test._test == bol_test,
                 "all bool nodes must have same test");
        }
#endif

        CmpNode* cmp = bol->in(1)->as_Cmp();
        assert(cmp != nullptr, "must have cmp above CMove");
        Node_List* p_cmp = my_pack(cmp);
        assert(p_cmp != nullptr, "Bool must have matching Cmp pack");

        Node* cmp_in1 = vector_opd(p_cmp, 1);
        Node* cmp_in2 = vector_opd(p_cmp, 2);

        Node* blend_in1 = vector_opd(p, 2);
        Node* blend_in2 = vector_opd(p, 3);

        if (cmp->Opcode() == Op_CmpF || cmp->Opcode() == Op_CmpD) {
          // If we have a Float or Double comparison, we must be careful with
          // handling NaN's correctly. CmpF and CmpD have a return code, as
          // they are based on the java bytecodes fcmpl/dcmpl:
          // -1: cmp_in1 <  cmp_in2, or at least one of the two is a NaN
          //  0: cmp_in1 == cmp_in2  (no NaN)
          //  1: cmp_in1 >  cmp_in2  (no NaN)
          //
          // The "bol_test" selects which of the [-1, 0, 1] cases lead to "true".
          //
          // Note: ordered   (O) comparison returns "false" if either input is NaN.
          //       unordered (U) comparison returns "true"  if either input is NaN.
          //
          // The VectorMaskCmpNode does a comparison directly on in1 and in2, in the java
          // standard way (all comparisons are ordered, except NEQ is unordered).
          //
          // In the following, "bol_test" already matches the cmp code for VectorMaskCmpNode:
          //   BoolTest::eq:  Case 0     -> EQ_O
          //   BoolTest::ne:  Case -1, 1 -> NEQ_U
          //   BoolTest::ge:  Case 0, 1  -> GE_O
          //   BoolTest::gt:  Case 1     -> GT_O
          //
          // But the lt and le comparisons must be converted from unordered to ordered:
          //   BoolTest::lt:  Case -1    -> LT_U -> VectorMaskCmp would interpret lt as LT_O
          //   BoolTest::le:  Case -1, 0 -> LE_U -> VectorMaskCmp would interpret le as LE_O
          //
          if (bol_test == BoolTest::lt || bol_test == BoolTest::le) {
            // Negating the bol_test and swapping the blend-inputs leaves all non-NaN cases equal,
            // but converts the unordered (U) to an ordered (O) comparison.
            //      VectorBlend(VectorMaskCmp(LT_U, in1_cmp, in2_cmp), in1_blend, in2_blend)
            // <==> VectorBlend(VectorMaskCmp(GE_O, in1_cmp, in2_cmp), in2_blend, in1_blend)
            //      VectorBlend(VectorMaskCmp(LE_U, in1_cmp, in2_cmp), in1_blend, in2_blend)
            // <==> VectorBlend(VectorMaskCmp(GT_O, in1_cmp, in2_cmp), in2_blend, in1_blend)
            bol_test = bol->_test.negate();
            swap(blend_in1, blend_in2);
          }
        }

        // VectorMaskCmp
        ConINode* bol_test_node  = _igvn.intcon((int)bol_test);
        BasicType bt = velt_basic_type(cmp);
        const TypeVect* vt = TypeVect::make(bt, vlen);
        VectorNode* mask = new VectorMaskCmpNode(bol_test, cmp_in1, cmp_in2, bol_test_node, vt);
        _igvn.register_new_node_with_optimizer(mask);
        _phase->set_ctrl(mask, _phase->get_ctrl(p->at(0)));
        _igvn._worklist.push(mask);

        // VectorBlend
        vn = new VectorBlendNode(blend_in1, blend_in2, mask);
      } else if (n->req() == 3) {
        // Promote operands to vector
        Node* in1 = nullptr;
        bool node_isa_reduction = is_marked_reduction(n);
        if (node_isa_reduction) {
          // the input to the first reduction operation is retained
          in1 = first->in(1);
        } else {
          in1 = vector_opd(p, 1);
          if (in1 == nullptr) {
            assert(false, "input in1 to vector operand was not created");
            C->record_failure(C2Compiler::retry_no_superword());
            return false; // bailout
          }
        }
        Node* in2 = vector_opd(p, 2);
        if (in2 == nullptr) {
          assert(false, "input in2 to vector operand was not created");
          C->record_failure(C2Compiler::retry_no_superword());
          return false; // bailout
        }
        if (in1->Opcode() == Op_Replicate && (node_isa_reduction == false) && (n->is_Add() || n->is_Mul())) {
          // Move invariant vector input into second position to avoid register spilling.
          Node* tmp = in1;
          in1 = in2;
          in2 = tmp;
        }
        if (node_isa_reduction) {
          const Type *arith_type = n->bottom_type();
          vn = ReductionNode::make(opc, nullptr, in1, in2, arith_type->basic_type());
          if (in2->is_Load()) {
            vlen_in_bytes = in2->as_LoadVector()->memory_size();
          } else {
            vlen_in_bytes = in2->as_Vector()->length_in_bytes();
          }
        } else {
          // Vector unsigned right shift for signed subword types behaves differently
          // from Java Spec. But when the shift amount is a constant not greater than
          // the number of sign extended bits, the unsigned right shift can be
          // vectorized to a signed right shift.
          if (VectorNode::can_transform_shift_op(n, velt_basic_type(n))) {
            opc = Op_RShiftI;
          }
          vn = VectorNode::make(opc, in1, in2, vlen, velt_basic_type(n));
          vlen_in_bytes = vn->as_Vector()->length_in_bytes();
        }
      } else if (opc == Op_SqrtF || opc == Op_SqrtD ||
                 opc == Op_AbsF || opc == Op_AbsD ||
                 opc == Op_AbsI || opc == Op_AbsL ||
                 opc == Op_NegF || opc == Op_NegD ||
                 opc == Op_RoundF || opc == Op_RoundD ||
                 opc == Op_ReverseBytesI || opc == Op_ReverseBytesL ||
                 opc == Op_ReverseBytesUS || opc == Op_ReverseBytesS ||
                 opc == Op_ReverseI || opc == Op_ReverseL ||
                 opc == Op_PopCountI || opc == Op_CountLeadingZerosI ||
                 opc == Op_CountTrailingZerosI) {
        assert(n->req() == 2, "only one input expected");
        Node* in = vector_opd(p, 1);
        vn = VectorNode::make(opc, in, nullptr, vlen, velt_basic_type(n));
        vlen_in_bytes = vn->as_Vector()->length_in_bytes();
      } else if (requires_long_to_int_conversion(opc)) {
        // Java API for Long.bitCount/numberOfLeadingZeros/numberOfTrailingZeros
        // returns int type, but Vector API for them returns long type. To unify
        // the implementation in backend, superword splits the vector implementation
        // for Java API into an execution node with long type plus another node
        // converting long to int.
        assert(n->req() == 2, "only one input expected");
        Node* in = vector_opd(p, 1);
        Node* longval = VectorNode::make(opc, in, nullptr, vlen, T_LONG);
        _igvn.register_new_node_with_optimizer(longval);
        _phase->set_ctrl(longval, _phase->get_ctrl(first));
        vn = VectorCastNode::make(Op_VectorCastL2X, longval, T_INT, vlen);
        vlen_in_bytes = vn->as_Vector()->length_in_bytes();
      } else if (VectorNode::is_convert_opcode(opc)) {
        assert(n->req() == 2, "only one input expected");
        BasicType bt = velt_basic_type(n);
        Node* in = vector_opd(p, 1);
        int vopc = VectorCastNode::opcode(opc, in->bottom_type()->is_vect()->element_basic_type());
        vn = VectorCastNode::make(vopc, in, bt, vlen);
        vlen_in_bytes = vn->as_Vector()->length_in_bytes();
      } else if (opc == Op_FmaD || opc == Op_FmaF) {
        // Promote operands to vector
        Node* in1 = vector_opd(p, 1);
        Node* in2 = vector_opd(p, 2);
        Node* in3 = vector_opd(p, 3);
        vn = VectorNode::make(opc, in1, in2, in3, vlen, velt_basic_type(n));
        vlen_in_bytes = vn->as_Vector()->length_in_bytes();
      } else {
        assert(false, "Unhandled scalar opcode (%s)", NodeClassNames[opc]);
        C->record_failure(C2Compiler::retry_no_superword());
        return false; // bailout
      }

      if (vn == nullptr) {
        assert(false, "got null node instead of vector node");
        C->record_failure(C2Compiler::retry_no_superword());
        return false; // bailout
      }

#ifdef ASSERT
      // Mark Load/Store Vector for alignment verification
      if (VerifyAlignVector) {
        if (vn->Opcode() == Op_LoadVector) {
          vn->as_LoadVector()->set_must_verify_alignment();
        } else if (vn->Opcode() == Op_StoreVector) {
          vn->as_StoreVector()->set_must_verify_alignment();
        }
      }
#endif

      _block.at_put(i, vn);
      _igvn.register_new_node_with_optimizer(vn);
      _phase->set_ctrl(vn, _phase->get_ctrl(first));
      for (uint j = 0; j < p->size(); j++) {
        Node* pm = p->at(j);
        _igvn.replace_node(pm, vn);
      }
      _igvn._worklist.push(vn);

      if (vlen > max_vlen) {
        max_vlen = vlen;
      }
      if (vlen_in_bytes > max_vlen_in_bytes) {
        max_vlen_in_bytes = vlen_in_bytes;
      }
      VectorNode::trace_new_vector(vn, "SuperWord");
    }
  }//for (int i = 0; i < _block.length(); i++)

  if (max_vlen_in_bytes > C->max_vector_size()) {
    C->set_max_vector_size(max_vlen_in_bytes);
  }
  if (max_vlen_in_bytes > 0) {
    cl->mark_loop_vectorized();
  }

  if (SuperWordLoopUnrollAnalysis) {
    if (cl->has_passed_slp()) {
      uint slp_max_unroll_factor = cl->slp_max_unroll();
      if (slp_max_unroll_factor == max_vlen) {
        if (TraceSuperWordLoopUnrollAnalysis) {
          tty->print_cr("vector loop(unroll=%d, len=%d)\n", max_vlen, max_vlen_in_bytes*BitsPerByte);
        }
        // For atomic unrolled loops which are vector mapped, instigate more unrolling
        cl->set_notpassed_slp();
        // if vector resources are limited, do not allow additional unrolling
        if (Matcher::float_pressure_limit() > 8) {
          C->set_major_progress();
          cl->mark_do_unroll_only();
        }
      }
    }
  }

  _phase->C->print_method(PHASE_SUPERWORD3_AFTER_OUTPUT, 4, cl);

  return true;
}

//------------------------------vector_opd---------------------------
// Create a vector operand for the nodes in pack p for operand: in(opd_idx)
Node* SuperWord::vector_opd(Node_List* p, int opd_idx) {
  Node* p0 = p->at(0);
  uint vlen = p->size();
  Node* opd = p0->in(opd_idx);
  CountedLoopNode *cl = lpt()->_head->as_CountedLoop();
  bool have_same_inputs = same_inputs(p, opd_idx);

  // Insert index population operation to create a vector of increasing
  // indices starting from the iv value. In some special unrolled loops
  // (see JDK-8286125), we need scalar replications of the iv value if
  // all inputs are the same iv, so we do a same inputs check here.
  if (opd == iv() && !have_same_inputs) {
    BasicType p0_bt = velt_basic_type(p0);
    BasicType iv_bt = is_subword_type(p0_bt) ? p0_bt : T_INT;
    assert(VectorNode::is_populate_index_supported(iv_bt), "Should support");
    const TypeVect* vt = TypeVect::make(iv_bt, vlen);
    Node* vn = new PopulateIndexNode(iv(), _igvn.intcon(1), vt);
    VectorNode::trace_new_vector(vn, "SuperWord");
    _igvn.register_new_node_with_optimizer(vn);
    _phase->set_ctrl(vn, _phase->get_ctrl(opd));
    return vn;
  }

  if (have_same_inputs) {
    if (opd->is_Vector() || opd->is_LoadVector()) {
      if (opd_idx == 2 && VectorNode::is_shift(p0)) {
        assert(false, "shift's count can't be vector");
        return nullptr;
      }
      return opd; // input is matching vector
    }
    if ((opd_idx == 2) && VectorNode::is_shift(p0)) {
      Node* cnt = opd;
      // Vector instructions do not mask shift count, do it here.
      juint mask = (p0->bottom_type() == TypeInt::INT) ? (BitsPerInt - 1) : (BitsPerLong - 1);
      const TypeInt* t = opd->find_int_type();
      if (t != nullptr && t->is_con()) {
        juint shift = t->get_con();
        if (shift > mask) { // Unsigned cmp
          cnt = ConNode::make(TypeInt::make(shift & mask));
          _igvn.register_new_node_with_optimizer(cnt);
        }
      } else {
        if (t == nullptr || t->_lo < 0 || t->_hi > (int)mask) {
          cnt = ConNode::make(TypeInt::make(mask));
          _igvn.register_new_node_with_optimizer(cnt);
          cnt = new AndINode(opd, cnt);
          _igvn.register_new_node_with_optimizer(cnt);
          _phase->set_ctrl(cnt, _phase->get_ctrl(opd));
        }
        if (!opd->bottom_type()->isa_int()) {
          assert(false, "int type only");
          return nullptr;
        }
      }
      // Move shift count into vector register.
      cnt = VectorNode::shift_count(p0->Opcode(), cnt, vlen, velt_basic_type(p0));
      _igvn.register_new_node_with_optimizer(cnt);
      _phase->set_ctrl(cnt, _phase->get_ctrl(opd));
      return cnt;
    }
    if (opd->is_StoreVector()) {
      assert(false, "StoreVector is not expected here");
      return nullptr;
    }
    // Convert scalar input to vector with the same number of elements as
    // p0's vector. Use p0's type because size of operand's container in
    // vector should match p0's size regardless operand's size.
    const Type* p0_t = nullptr;
    VectorNode* vn = nullptr;
    if (opd_idx == 2 && VectorNode::is_scalar_rotate(p0)) {
       Node* conv = opd;
       p0_t =  TypeInt::INT;
       if (p0->bottom_type()->isa_long()) {
         p0_t = TypeLong::LONG;
         conv = new ConvI2LNode(opd);
         _igvn.register_new_node_with_optimizer(conv);
         _phase->set_ctrl(conv, _phase->get_ctrl(opd));
       }
       vn = VectorNode::scalar2vector(conv, vlen, p0_t);
    } else {
       p0_t =  velt_type(p0);
       vn = VectorNode::scalar2vector(opd, vlen, p0_t);
    }

    _igvn.register_new_node_with_optimizer(vn);
    _phase->set_ctrl(vn, _phase->get_ctrl(opd));
    VectorNode::trace_new_vector(vn, "SuperWord");
    return vn;
  }

  // Insert pack operation
  BasicType bt = velt_basic_type(p0);
  PackNode* pk = PackNode::make(opd, vlen, bt);
  DEBUG_ONLY( const BasicType opd_bt = opd->bottom_type()->basic_type(); )

  for (uint i = 1; i < vlen; i++) {
    Node* pi = p->at(i);
    Node* in = pi->in(opd_idx);
    if (my_pack(in) != nullptr) {
      assert(false, "Should already have been unpacked");
      return nullptr;
    }
    assert(opd_bt == in->bottom_type()->basic_type(), "all same type");
    pk->add_opd(in);
    if (VectorNode::is_muladds2i(pi)) {
      Node* in2 = pi->in(opd_idx + 2);
      if (my_pack(in2) != nullptr) {
        assert(false, "Should already have been unpacked");
        return nullptr;
      }
      assert(opd_bt == in2->bottom_type()->basic_type(), "all same type");
      pk->add_opd(in2);
    }
  }
  _igvn.register_new_node_with_optimizer(pk);
  _phase->set_ctrl(pk, _phase->get_ctrl(opd));
  VectorNode::trace_new_vector(pk, "SuperWord");
  return pk;
}

//------------------------------insert_extracts---------------------------
// If a use of pack p is not a vector use, then replace the
// use with an extract operation.
void SuperWord::insert_extracts(Node_List* p) {
  if (p->at(0)->is_Store()) return;
  assert(_n_idx_list.is_empty(), "empty (node,index) list");

  // Inspect each use of each pack member.  For each use that is
  // not a vector use, replace the use with an extract operation.

  for (uint i = 0; i < p->size(); i++) {
    Node* def = p->at(i);
    for (DUIterator_Fast jmax, j = def->fast_outs(jmax); j < jmax; j++) {
      Node* use = def->fast_out(j);
      for (uint k = 0; k < use->req(); k++) {
        Node* n = use->in(k);
        if (def == n) {
          Node_List* u_pk = my_pack(use);
          if ((u_pk == nullptr || use->is_CMove()) && !is_vector_use(use, k)) {
              _n_idx_list.push(use, k);
          }
        }
      }
    }
  }

  while (_n_idx_list.is_nonempty()) {
    Node* use = _n_idx_list.node();
    int   idx = _n_idx_list.index();
    _n_idx_list.pop();
    Node* def = use->in(idx);

    if (is_marked_reduction(def)) continue;

    // Insert extract operation
    _igvn.hash_delete(def);
    int def_pos = alignment(def) / data_size(def);

    ConINode* def_pos_con = _igvn.intcon(def_pos)->as_ConI();
    Node* ex = ExtractNode::make(def, def_pos_con, velt_basic_type(def));
    _igvn.register_new_node_with_optimizer(ex);
    _phase->set_ctrl(ex, _phase->get_ctrl(def));
    _igvn.replace_input_of(use, idx, ex);
    _igvn._worklist.push(def);

    bb_insert_after(ex, bb_idx(def));
    set_velt_type(ex, velt_type(def));
  }
}

//------------------------------is_vector_use---------------------------
// Is use->in(u_idx) a vector use?
bool SuperWord::is_vector_use(Node* use, int u_idx) {
  Node_List* u_pk = my_pack(use);
  if (u_pk == nullptr) return false;
  if (is_marked_reduction(use)) return true;
  Node* def = use->in(u_idx);
  Node_List* d_pk = my_pack(def);
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

  if (VectorNode::is_muladds2i(use)) {
    // MulAddS2I takes shorts and produces ints - hence the special checks
    // on alignment and size.
    if (u_pk->size() * 2 != d_pk->size()) {
      return false;
    }
    for (uint i = 0; i < MIN2(d_pk->size(), u_pk->size()); i++) {
      Node* ui = u_pk->at(i);
      Node* di = d_pk->at(i);
      if (alignment(ui) != alignment(di) * 2) {
        return false;
      }
    }
    return true;
  }

  if (u_pk->size() != d_pk->size())
    return false;

  if (longer_type_for_conversion(use) != T_ILLEGAL) {
    // These opcodes take a type of a kind of size and produce a type of
    // another size - hence the special checks on alignment and size.
    for (uint i = 0; i < u_pk->size(); i++) {
      Node* ui = u_pk->at(i);
      Node* di = d_pk->at(i);
      if (ui->in(u_idx) != di) {
        return false;
      }
      if (alignment(ui) / type2aelembytes(velt_basic_type(ui)) !=
          alignment(di) / type2aelembytes(velt_basic_type(di))) {
        return false;
      }
    }
    return true;
  }

  for (uint i = 0; i < u_pk->size(); i++) {
    Node* ui = u_pk->at(i);
    Node* di = d_pk->at(i);
    if (ui->in(u_idx) != di || alignment(ui) != alignment(di))
      return false;
  }
  return true;
}

//------------------------------construct_bb---------------------------
// Construct reverse postorder list of block members
bool SuperWord::construct_bb() {
  Node* entry = bb();

  assert(_stk.length() == 0,            "stk is empty");
  assert(_block.length() == 0,          "block is empty");
  assert(_data_entry.length() == 0,     "data_entry is empty");
  assert(_mem_slice_head.length() == 0, "mem_slice_head is empty");
  assert(_mem_slice_tail.length() == 0, "mem_slice_tail is empty");

  // Find non-control nodes with no inputs from within block,
  // create a temporary map from node _idx to bb_idx for use
  // by the visited and post_visited sets,
  // and count number of nodes in block.
  int bb_ct = 0;
  for (uint i = 0; i < lpt()->_body.size(); i++) {
    Node *n = lpt()->_body.at(i);
    set_bb_idx(n, i); // Create a temporary map
    if (in_bb(n)) {
      if (n->is_LoadStore() || n->is_MergeMem() ||
          (n->is_Proj() && !n->as_Proj()->is_CFG())) {
        // Bailout if the loop has LoadStore, MergeMem or data Proj
        // nodes. Superword optimization does not work with them.
        return false;
      }
      bb_ct++;
      if (!n->is_CFG()) {
        bool found = false;
        for (uint j = 0; j < n->req(); j++) {
          Node* def = n->in(j);
          if (def && in_bb(def)) {
            found = true;
            break;
          }
        }
        if (!found) {
          assert(n != entry, "can't be entry");
          _data_entry.push(n);
        }
      }
    }
  }

  // Find memory slices (head and tail)
  for (DUIterator_Fast imax, i = lp()->fast_outs(imax); i < imax; i++) {
    Node *n = lp()->fast_out(i);
    if (in_bb(n) && n->is_memory_phi()) {
      Node* n_tail  = n->in(LoopNode::LoopBackControl);
      if (n_tail != n->in(LoopNode::EntryControl)) {
        if (!n_tail->is_Mem()) {
          assert(n_tail->is_Mem(), "unexpected node for memory slice: %s", n_tail->Name());
          return false; // Bailout
        }
        _mem_slice_head.push(n);
        _mem_slice_tail.push(n_tail);
      }
    }
  }

  // Create an RPO list of nodes in block

  visited_clear();
  post_visited_clear();

  // Push all non-control nodes with no inputs from within block, then control entry
  for (int j = 0; j < _data_entry.length(); j++) {
    Node* n = _data_entry.at(j);
    visited_set(n);
    _stk.push(n);
  }
  visited_set(entry);
  _stk.push(entry);

  // Do a depth first walk over out edges
  int rpo_idx = bb_ct - 1;
  int size;
  int reduction_uses = 0;
  while ((size = _stk.length()) > 0) {
    Node* n = _stk.top(); // Leave node on stack
    if (!visited_test_set(n)) {
      // forward arc in graph
    } else if (!post_visited_test(n)) {
      // cross or back arc
      for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
        Node *use = n->fast_out(i);
        if (in_bb(use) && !visited_test(use) &&
            // Don't go around backedge
            (!use->is_Phi() || n == entry)) {
          if (is_marked_reduction(use)) {
            // First see if we can map the reduction on the given system we are on, then
            // make a data entry operation for each reduction we see.
            BasicType bt = use->bottom_type()->basic_type();
            if (ReductionNode::implemented(use->Opcode(), Matcher::superword_max_vector_size(bt), bt)) {
              reduction_uses++;
            }
          }
          _stk.push(use);
        }
      }
      if (_stk.length() == size) {
        // There were no additional uses, post visit node now
        _stk.pop(); // Remove node from stack
        assert(rpo_idx >= 0, "");
        _block.at_put_grow(rpo_idx, n);
        rpo_idx--;
        post_visited_set(n);
        assert(rpo_idx >= 0 || _stk.is_empty(), "");
      }
    } else {
      _stk.pop(); // Remove post-visited node from stack
    }
  }//while

  int ii_current = -1;
  unsigned int load_idx = (unsigned int)-1;
  // Create real map of block indices for nodes
  for (int j = 0; j < _block.length(); j++) {
    Node* n = _block.at(j);
    set_bb_idx(n, j);
  }//for

  // Ensure extra info is allocated.
  initialize_bb();

#ifndef PRODUCT
  if (TraceSuperWord) {
    print_bb();
    tty->print_cr("\ndata entry nodes: %s", _data_entry.length() > 0 ? "" : "NONE");
    for (int m = 0; m < _data_entry.length(); m++) {
      tty->print("%3d ", m);
      _data_entry.at(m)->dump();
    }
    tty->print_cr("\nmemory slices: %s", _mem_slice_head.length() > 0 ? "" : "NONE");
    for (int m = 0; m < _mem_slice_head.length(); m++) {
      tty->print("%3d ", m); _mem_slice_head.at(m)->dump();
      tty->print("    ");    _mem_slice_tail.at(m)->dump();
    }
  }
#endif
  assert(rpo_idx == -1 && bb_ct == _block.length(), "all block members found");
  return (_mem_slice_head.length() > 0) || (reduction_uses > 0) || (_data_entry.length() > 0);
}

//------------------------------initialize_bb---------------------------
// Initialize per node info
void SuperWord::initialize_bb() {
  Node* last = _block.at(_block.length() - 1);
  grow_node_info(bb_idx(last));
}

//------------------------------bb_insert_after---------------------------
// Insert n into block after pos
void SuperWord::bb_insert_after(Node* n, int pos) {
  int n_pos = pos + 1;
  // Make room
  for (int i = _block.length() - 1; i >= n_pos; i--) {
    _block.at_put_grow(i+1, _block.at(i));
  }
  for (int j = _node_info.length() - 1; j >= n_pos; j--) {
    _node_info.at_put_grow(j+1, _node_info.at(j));
  }
  // Set value
  _block.at_put_grow(n_pos, n);
  _node_info.at_put_grow(n_pos, SWNodeInfo::initial);
  // Adjust map from node->_idx to _block index
  for (int i = n_pos; i < _block.length(); i++) {
    set_bb_idx(_block.at(i), i);
  }
}

//------------------------------compute_max_depth---------------------------
// Compute max depth for expressions from beginning of block
// Use to prune search paths during test for independence.
void SuperWord::compute_max_depth() {
  int ct = 0;
  bool again;
  do {
    again = false;
    for (int i = 0; i < _block.length(); i++) {
      Node* n = _block.at(i);
      if (!n->is_Phi()) {
        int d_orig = depth(n);
        int d_in   = 0;
        for (DepPreds preds(n, _dg); !preds.done(); preds.next()) {
          Node* pred = preds.current();
          if (in_bb(pred)) {
            d_in = MAX2(d_in, depth(pred));
          }
        }
        if (d_in + 1 != d_orig) {
          set_depth(n, d_in + 1);
          again = true;
        }
      }
    }
    ct++;
  } while (again);

  if (TraceSuperWord && Verbose) {
    tty->print_cr("compute_max_depth iterated: %d times", ct);
  }
}

BasicType SuperWord::longer_type_for_conversion(Node* n) {
  if (!(VectorNode::is_convert_opcode(n->Opcode()) ||
        requires_long_to_int_conversion(n->Opcode())) ||
      !in_bb(n->in(1))) {
    return T_ILLEGAL;
  }
  assert(in_bb(n), "must be in the bb");
  BasicType src_t = velt_basic_type(n->in(1));
  BasicType dst_t = velt_basic_type(n);
  // Do not use superword for non-primitives.
  // Superword does not support casting involving unsigned types.
  if (!is_java_primitive(src_t) || is_unsigned_subword_type(src_t) ||
      !is_java_primitive(dst_t) || is_unsigned_subword_type(dst_t)) {
    return T_ILLEGAL;
  }
  int src_size = type2aelembytes(src_t);
  int dst_size = type2aelembytes(dst_t);
  return src_size == dst_size ? T_ILLEGAL
                              : (src_size > dst_size ? src_t : dst_t);
}

int SuperWord::max_vector_size_in_def_use_chain(Node* n) {
  BasicType bt = velt_basic_type(n);
  BasicType vt = bt;

  // find the longest type among def nodes.
  uint start, end;
  VectorNode::vector_operands(n, &start, &end);
  for (uint i = start; i < end; ++i) {
    Node* input = n->in(i);
    if (!in_bb(input)) continue;
    BasicType newt = longer_type_for_conversion(input);
    vt = (newt == T_ILLEGAL) ? vt : newt;
  }

  // find the longest type among use nodes.
  for (uint i = 0; i < n->outcnt(); ++i) {
    Node* output = n->raw_out(i);
    if (!in_bb(output)) continue;
    BasicType newt = longer_type_for_conversion(output);
    vt = (newt == T_ILLEGAL) ? vt : newt;
  }

  int max = Matcher::superword_max_vector_size(vt);
  // If now there is no vectors for the longest type, the nodes with the longest
  // type in the def-use chain are not packed in SuperWord::stmts_can_pack.
  return max < 2 ? Matcher::superword_max_vector_size(bt) : max;
}

//-------------------------compute_vector_element_type-----------------------
// Compute necessary vector element type for expressions
// This propagates backwards a narrower integer type when the
// upper bits of the value are not needed.
// Example:  char a,b,c;  a = b + c;
// Normally the type of the add is integer, but for packed character
// operations the type of the add needs to be char.
void SuperWord::compute_vector_element_type() {
  if (TraceSuperWord && Verbose) {
    tty->print_cr("\ncompute_velt_type:");
  }

  // Initial type
  for (int i = 0; i < _block.length(); i++) {
    Node* n = _block.at(i);
    set_velt_type(n, container_type(n));
  }

  // Propagate integer narrowed type backwards through operations
  // that don't depend on higher order bits
  for (int i = _block.length() - 1; i >= 0; i--) {
    Node* n = _block.at(i);
    // Only integer types need be examined
    const Type* vtn = velt_type(n);
    if (vtn->basic_type() == T_INT) {
      uint start, end;
      VectorNode::vector_operands(n, &start, &end);

      for (uint j = start; j < end; j++) {
        Node* in  = n->in(j);
        // Don't propagate through a memory
        if (!in->is_Mem() && in_bb(in) && velt_type(in)->basic_type() == T_INT &&
            data_size(n) < data_size(in)) {
          bool same_type = true;
          for (DUIterator_Fast kmax, k = in->fast_outs(kmax); k < kmax; k++) {
            Node *use = in->fast_out(k);
            if (!in_bb(use) || !same_velt_type(use, n)) {
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
            if (VectorNode::is_shift_opcode(op) || op == Op_AbsI || op == Op_ReverseBytesI) {
              Node* load = in->in(1);
              if (load->is_Load() && in_bb(load) && (velt_type(load)->basic_type() == T_INT)) {
                // Only Load nodes distinguish signed (LoadS/LoadB) and unsigned
                // (LoadUS/LoadUB) values. Store nodes only have one version.
                vt = velt_type(load);
              } else if (op != Op_LShiftI) {
                // Widen type to int to avoid the creation of vector nodes. Note
                // that left shifts work regardless of the signedness.
                vt = TypeInt::INT;
              }
            }
            set_velt_type(in, vt);
          }
        }
      }
    }
  }
  for (int i = 0; i < _block.length(); i++) {
    Node* n = _block.at(i);
    Node* nn = n;
    if (nn->is_Bool() && nn->in(0) == nullptr) {
      nn = nn->in(1);
      assert(nn->is_Cmp(), "always have Cmp above Bool");
    }
    if (nn->is_Cmp() && nn->in(0) == nullptr) {
      assert(in_bb(nn->in(1)) || in_bb(nn->in(2)), "one of the inputs must be in the loop too");
      if (in_bb(nn->in(1))) {
        set_velt_type(n, velt_type(nn->in(1)));
      } else {
        set_velt_type(n, velt_type(nn->in(2)));
      }
    }
  }
#ifndef PRODUCT
  if (TraceSuperWord && Verbose) {
    for (int i = 0; i < _block.length(); i++) {
      Node* n = _block.at(i);
      velt_type(n)->dump();
      tty->print("\t");
      n->dump();
    }
  }
#endif
}

//------------------------------memory_alignment---------------------------
// Alignment within a vector memory reference
int SuperWord::memory_alignment(MemNode* s, int iv_adjust) {
#ifndef PRODUCT
  if ((TraceSuperWord && Verbose) || is_trace_alignment()) {
    tty->print("SuperWord::memory_alignment within a vector memory reference for %d:  ", s->_idx); s->dump();
  }
#endif
  VPointer p(s, phase(), lpt(), nullptr, false);
  if (!p.valid()) {
    NOT_PRODUCT(if(is_trace_alignment()) tty->print_cr("VPointer::memory_alignment: VPointer p invalid, return bottom_align");)
    return bottom_align;
  }
  int vw = get_vw_bytes_special(s);
  if (vw < 2) {
    NOT_PRODUCT(if(is_trace_alignment()) tty->print_cr("VPointer::memory_alignment: vector_width_in_bytes < 2, return bottom_align");)
    return bottom_align; // No vectors for this type
  }
  int offset  = p.offset_in_bytes();
  offset     += iv_adjust*p.memory_size();
  int off_rem = offset % vw;
  int off_mod = off_rem >= 0 ? off_rem : off_rem + vw;
#ifndef PRODUCT
  if ((TraceSuperWord && Verbose) || is_trace_alignment()) {
    tty->print_cr("VPointer::memory_alignment: off_rem = %d, off_mod = %d (offset = %d)", off_rem, off_mod, offset);
  }
#endif
  return off_mod;
}

//---------------------------container_type---------------------------
// Smallest type containing range of values
const Type* SuperWord::container_type(Node* n) {
  if (n->is_Mem()) {
    BasicType bt = n->as_Mem()->memory_type();
    if (n->is_Store() && (bt == T_CHAR)) {
      // Use T_SHORT type instead of T_CHAR for stored values because any
      // preceding arithmetic operation extends values to signed Int.
      bt = T_SHORT;
    }
    if (n->Opcode() == Op_LoadUB) {
      // Adjust type for unsigned byte loads, it is important for right shifts.
      // T_BOOLEAN is used because there is no basic type representing type
      // TypeInt::UBYTE. Use of T_BOOLEAN for vectors is fine because only
      // size (one byte) and sign is important.
      bt = T_BOOLEAN;
    }
    return Type::get_const_basic_type(bt);
  }
  const Type* t = _igvn.type(n);
  if (t->basic_type() == T_INT) {
    // A narrow type of arithmetic operations will be determined by
    // propagating the type of memory operations.
    return TypeInt::INT;
  }
  return t;
}

bool SuperWord::same_velt_type(Node* n1, Node* n2) {
  const Type* vt1 = velt_type(n1);
  const Type* vt2 = velt_type(n2);
  if (vt1->basic_type() == T_INT && vt2->basic_type() == T_INT) {
    // Compare vectors element sizes for integer types.
    return data_size(n1) == data_size(n2);
  }
  return vt1 == vt2;
}

bool SuperWord::same_memory_slice(MemNode* best_align_to_mem_ref, MemNode* mem_ref) const {
  return _phase->C->get_alias_index(mem_ref->adr_type()) == _phase->C->get_alias_index(best_align_to_mem_ref->adr_type());
}

//------------------------------in_packset---------------------------
// Are s1 and s2 in a pack pair and ordered as s1,s2?
bool SuperWord::in_packset(Node* s1, Node* s2) {
  for (int i = 0; i < _packset.length(); i++) {
    Node_List* p = _packset.at(i);
    assert(p->size() == 2, "must be");
    if (p->at(0) == s1 && p->at(p->size()-1) == s2) {
      return true;
    }
  }
  return false;
}

//------------------------------remove_pack_at---------------------------
// Remove the pack at position pos in the packset
void SuperWord::remove_pack_at(int pos) {
  Node_List* p = _packset.at(pos);
  for (uint i = 0; i < p->size(); i++) {
    Node* s = p->at(i);
    set_my_pack(s, nullptr);
  }
  _packset.remove_at(pos);
}

void SuperWord::packset_sort(int n) {
  // simple bubble sort so that we capitalize with O(n) when its already sorted
  while (n != 0) {
    bool swapped = false;
    for (int i = 1; i < n; i++) {
      Node_List* q_low = _packset.at(i-1);
      Node_List* q_i = _packset.at(i);

      // only swap when we find something to swap
      if (alignment(q_low->at(0)) > alignment(q_i->at(0))) {
        Node_List* t = q_i;
        *(_packset.adr_at(i)) = q_low;
        *(_packset.adr_at(i-1)) = q_i;
        swapped = true;
      }
    }
    if (swapped == false) break;
    n--;
  }
}

LoadNode::ControlDependency SuperWord::control_dependency(Node_List* p) {
  LoadNode::ControlDependency dep = LoadNode::DependsOnlyOnTest;
  for (uint i = 0; i < p->size(); i++) {
    Node* n = p->at(i);
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

#define TRACE_ALIGN_VECTOR_NODE(node) { \
  DEBUG_ONLY(                           \
    if (is_trace_align_vector()) {      \
      tty->print("  " #node ": ");      \
      node->dump();                     \
    }                                   \
  )                                     \
}                                       \

// Ensure that the main loop vectors are aligned by adjusting the pre loop limit. We memory-align
// the address of "align_to_ref" to the maximal possible vector width. We adjust the pre-loop
// iteration count by adjusting the pre-loop limit.
void SuperWord::adjust_pre_loop_limit_to_align_main_loop_vectors() {
  const MemNode* align_to_ref = _align_to_ref;
  assert(align_to_ref != nullptr, "align_to_ref must be set");
  assert(lp()->is_main_loop(), "can only do alignment for main loop");

  // The opaque node for the limit, where we adjust the input
  Opaque1Node* pre_opaq = lp()->pre_loop_end()->limit()->as_Opaque1();

  // Current pre-loop limit.
  Node* old_limit = pre_opaq->in(1);

  // Where we put new limit calculations.
  Node* pre_ctrl = lp()->pre_loop_head()->in(LoopNode::EntryControl);

  // Ensure the original loop limit is available from the pre-loop Opaque1 node.
  Node* orig_limit = pre_opaq->original_loop_limit();
  assert(orig_limit != nullptr && _igvn.type(orig_limit) != Type::TOP, "");

  VPointer align_to_ref_p(align_to_ref, phase(), lpt(), nullptr, false);
  assert(align_to_ref_p.valid(), "sanity");

  // For the main-loop, we want the address of align_to_ref to be memory aligned
  // with some alignment width (aw, a power of 2). When we enter the main-loop,
  // we know that iv is equal to the pre-loop limit. If we adjust the pre-loop
  // limit by executing adjust_pre_iter many extra iterations, we can change the
  // alignment of the address.
  //
  //   adr = base + offset + invar + scale * iv                               (1)
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
  //   iv = new_limit = old_limit + adjust_pre_iter                           (3a, stride > 0)
  //   iv = new_limit = old_limit - adjust_pre_iter                           (3b, stride < 0)
  //
  // We define boi as:
  //
  //   boi = base + offset + invar                                            (4)
  //
  // And now we can simplify the address using (1), (3), and (4):
  //
  //   adr = boi + scale * new_limit
  //   adr = boi + scale * (old_limit + adjust_pre_iter)                      (5a, stride > 0)
  //   adr = boi + scale * (old_limit - adjust_pre_iter)                      (5b, stride < 0)
  //
  // And hence we can restate (2) with (5), and solve the equation for adjust_pre_iter:
  //
  //   (boi + scale * (old_limit + adjust_pre_iter) % aw = 0                  (6a, stride > 0)
  //   (boi + scale * (old_limit - adjust_pre_iter) % aw = 0                  (6b, stride < 0)
  //
  // In most cases, scale is the element size, for example:
  //
  //   for (i = 0; i < a.length; i++) { a[i] = ...; }
  //
  // It is thus reasonable to assume that both abs(scale) and abs(stride) are
  // strictly positive powers of 2. Further, they can be assumed to be non-zero,
  // otherwise the address does not depend on iv, and the alignment cannot be
  // affected by adjusting the pre-loop limit.
  //
  // Further, if abs(scale) >= aw, then adjust_pre_iter has no effect on alignment, and
  // we are not able to affect the alignment at all. Hence, we require abs(scale) < aw.
  //
  // Moreover, for alignment to be achievable, boi must be a multiple of scale. If strict
  // alignment is required (i.e. -XX:+AlignVector), this is guaranteed by the filtering
  // done with the AlignmentSolver / AlignmentSolution. If strict alignment is not
  // required, then alignment is still preferable for performance, but not necessary.
  // In many cases boi will be a multiple of scale, but if it is not, then the adjustment
  // does not guarantee alignment, but the code is still correct.
  //
  // Hence, in what follows we assume that boi is a multiple of scale, and in fact all
  // terms in (6) are multiples of scale. Therefore we divide all terms by scale:
  //
  //   AW = aw / abs(scale)            (power of 2)                           (7)
  //   BOI = boi / abs(scale)                                                 (8)
  //
  // and restate (6), using (7) and (8), i.e. we divide (6) by abs(scale):
  //
  //   (BOI + sign(scale) * (old_limit + adjust_pre_iter) % AW = 0           (9a, stride > 0)
  //   (BOI + sign(scale) * (old_limit - adjust_pre_iter) % AW = 0           (9b, stride < 0)
  //
  //   where: sign(scale) = scale / abs(scale) = (scale > 0 ? 1 : -1)
  //
  // Note, (9) allows for periodic solutions of adjust_pre_iter, with periodicity AW.
  // But we would like to spend as few iterations in the pre-loop as possible,
  // hence we want the smallest adjust_pre_iter, and so:
  //
  //   0 <= adjust_pre_iter < AW                                              (10)
  //
  // We solve (9) for adjust_pre_iter, in the following 4 cases:
  //
  // Case A: scale > 0 && stride > 0 (i.e. sign(scale) =  1)
  //   (BOI + old_limit + adjust_pre_iter) % AW = 0
  //   adjust_pre_iter = (-BOI - old_limit) % AW                              (11a)
  //
  // Case B: scale < 0 && stride > 0 (i.e. sign(scale) = -1)
  //   (BOI - old_limit - adjust_pre_iter) % AW = 0
  //   adjust_pre_iter = (BOI - old_limit) % AW                               (11b)
  //
  // Case C: scale > 0 && stride < 0 (i.e. sign(scale) =  1)
  //   (BOI + old_limit - adjust_pre_iter) % AW = 0
  //   adjust_pre_iter = (BOI + old_limit) % AW                               (11c)
  //
  // Case D: scale < 0 && stride < 0 (i.e. sign(scale) = -1)
  //   (BOI - old_limit + adjust_pre_iter) % AW = 0
  //   adjust_pre_iter = (-BOI + old_limit) % AW                              (11d)
  //
  // We now generalize the equations (11*) by using:
  //
  //   OP:   (stride         > 0) ? SUB   : ADD
  //   XBOI: (stride * scale > 0) ? -BOI  : BOI
  //
  // which gives us the final pre-loop limit adjustment:
  //
  //   adjust_pre_iter = (XBOI OP old_limit) % AW                             (12)
  //
  // We can construct XBOI by additionally defining:
  //
  //   xboi = (stride * scale > 0) ? -boi              : boi                  (13)
  //
  // which gives us:
  //
  //   XBOI = (stride * scale > 0) ? -BOI              : BOI
  //        = (stride * scale > 0) ? -boi / abs(scale) : boi / abs(scale)
  //        = xboi / abs(scale)                                               (14)
  //
  // When we have computed adjust_pre_iter, we update the pre-loop limit
  // with (3a, b). However, we have to make sure that the adjust_pre_iter
  // additional pre-loop iterations do not lead the pre-loop to execute
  // iterations that would step over the original limit (orig_limit) of
  // the loop. Hence, we must constrain the updated limit as follows:
  //
  // constrained_limit = MIN(old_limit + adjust_pre_iter, orig_limit)
  //                   = MIN(new_limit,                   orig_limit)         (15a, stride > 0)
  // constrained_limit = MAX(old_limit - adjust_pre_iter, orig_limit)
  //                   = MAX(new_limit,                   orig_limit)         (15a, stride < 0)

  // We chose an aw that is the maximal possible vector width for the type of
  // align_to_ref.
  const int aw       = vector_width_in_bytes(align_to_ref);
  const int stride   = iv_stride();
  const int scale    = align_to_ref_p.scale_in_bytes();
  const int offset   = align_to_ref_p.offset_in_bytes();
  Node* base         = align_to_ref_p.adr();
  Node* invar        = align_to_ref_p.invar();

#ifdef ASSERT
  if (is_trace_align_vector()) {
    tty->print_cr("\nadjust_pre_loop_limit_to_align_main_loop_vectors:");
    tty->print("  align_to_ref:");
    align_to_ref->dump();
    tty->print_cr("  aw:       %d", aw);
    tty->print_cr("  stride:   %d", stride);
    tty->print_cr("  scale:    %d", scale);
    tty->print_cr("  offset:   %d", offset);
    tty->print("  base:");
    base->dump();
    if (invar == nullptr) {
      tty->print_cr("  invar:     null");
    } else {
      tty->print("  invar:");
      invar->dump();
    }
    tty->print("  old_limit: ");
    old_limit->dump();
    tty->print("  orig_limit: ");
    orig_limit->dump();
  }
#endif

  if (stride == 0 || !is_power_of_2(abs(stride)) ||
      scale  == 0 || !is_power_of_2(abs(scale))  ||
      abs(scale) >= aw) {
#ifdef ASSERT
    if (is_trace_align_vector()) {
      tty->print_cr(" Alignment cannot be affected by changing pre-loop limit because");
      tty->print_cr(" stride or scale are not power of 2, or abs(scale) >= aw.");
    }
#endif
    // Cannot affect alignment, abort.
    return;
  }

  assert(stride != 0 && is_power_of_2(abs(stride)) &&
         scale  != 0 && is_power_of_2(abs(scale))  &&
         abs(scale) < aw, "otherwise we cannot affect alignment with pre-loop");

  const int AW = aw / abs(scale);

#ifdef ASSERT
  if (is_trace_align_vector()) {
    tty->print_cr("  AW = aw(%d) / abs(scale(%d)) = %d", aw, scale, AW);
  }
#endif

  // 1: Compute (13a, b):
  //    xboi = -boi = (-base - offset - invar)         (stride * scale > 0)
  //    xboi = +boi = (+base + offset + invar)         (stride * scale < 0)
  const bool is_sub = scale * stride > 0;

  // 1.1: offset
  Node* xboi = _igvn.intcon(is_sub ? -offset : offset);
  TRACE_ALIGN_VECTOR_NODE(xboi);

  // 1.2: invar (if it exists)
  if (invar != nullptr) {
    if (_igvn.type(invar)->isa_long()) {
      // Computations are done % (vector width/element size) so it's
      // safe to simply convert invar to an int and loose the upper 32
      // bit half.
      invar = new ConvL2INode(invar);
      _igvn.register_new_node_with_optimizer(invar);
      TRACE_ALIGN_VECTOR_NODE(invar);
   }
    if (is_sub) {
      xboi = new SubINode(xboi, invar);
    } else {
      xboi = new AddINode(xboi, invar);
    }
    _igvn.register_new_node_with_optimizer(xboi);
    _phase->set_ctrl(xboi, pre_ctrl);
    TRACE_ALIGN_VECTOR_NODE(xboi);
  }

  // 1.3: base (unless base is guaranteed aw aligned)
  if (aw > ObjectAlignmentInBytes || align_to_ref_p.base()->is_top()) {
    // The base is only aligned with ObjectAlignmentInBytes with arrays.
    // When the base() is top, we have no alignment guarantee at all.
    // Hence, we must now take the base into account for the calculation.
    Node* xbase = new CastP2XNode(nullptr, base);
    _igvn.register_new_node_with_optimizer(xbase);
    TRACE_ALIGN_VECTOR_NODE(xbase);
#ifdef _LP64
    xbase  = new ConvL2INode(xbase);
    _igvn.register_new_node_with_optimizer(xbase);
    TRACE_ALIGN_VECTOR_NODE(xbase);
#endif
    if (is_sub) {
      xboi = new SubINode(xboi, xbase);
    } else {
      xboi = new AddINode(xboi, xbase);
    }
    _igvn.register_new_node_with_optimizer(xboi);
    _phase->set_ctrl(xboi, pre_ctrl);
    TRACE_ALIGN_VECTOR_NODE(xboi);
  }

  // 2: Compute (14):
  //    XBOI = xboi / abs(scale)
  //    The division is executed as shift
  Node* log2_abs_scale = _igvn.intcon(exact_log2(abs(scale)));
  Node* XBOI = new URShiftINode(xboi, log2_abs_scale);
  _igvn.register_new_node_with_optimizer(XBOI);
  _phase->set_ctrl(XBOI, pre_ctrl);
  TRACE_ALIGN_VECTOR_NODE(log2_abs_scale);
  TRACE_ALIGN_VECTOR_NODE(XBOI);

  // 3: Compute (12):
  //    adjust_pre_iter = (XBOI OP old_limit) % AW
  //
  // 3.1: XBOI_OP_old_limit = XBOI OP old_limit
  Node* XBOI_OP_old_limit = nullptr;
  if (stride > 0) {
    XBOI_OP_old_limit = new SubINode(XBOI, old_limit);
  } else {
    XBOI_OP_old_limit = new AddINode(XBOI, old_limit);
  }
  _igvn.register_new_node_with_optimizer(XBOI_OP_old_limit);
  _phase->set_ctrl(XBOI_OP_old_limit, pre_ctrl);
  TRACE_ALIGN_VECTOR_NODE(XBOI_OP_old_limit);

  // 3.2: Compute:
  //    adjust_pre_iter = (XBOI OP old_limit) % AW
  //                    = XBOI_OP_old_limit % AW
  //                    = XBOI_OP_old_limit AND (AW - 1)
  //    Since AW is a power of 2, the modulo operation can be replaced with
  //    a bitmask operation.
  Node* mask_AW = _igvn.intcon(AW-1);
  Node* adjust_pre_iter = new AndINode(XBOI_OP_old_limit, mask_AW);
  _igvn.register_new_node_with_optimizer(adjust_pre_iter);
  _phase->set_ctrl(adjust_pre_iter, pre_ctrl);
  TRACE_ALIGN_VECTOR_NODE(mask_AW);
  TRACE_ALIGN_VECTOR_NODE(adjust_pre_iter);

  // 4: Compute (3a, b):
  //    new_limit = old_limit + adjust_pre_iter     (stride > 0)
  //    new_limit = old_limit - adjust_pre_iter     (stride < 0)
  Node* new_limit = nullptr;
  if (stride < 0) {
    new_limit = new SubINode(old_limit, adjust_pre_iter);
  } else {
    new_limit = new AddINode(old_limit, adjust_pre_iter);
  }
  _igvn.register_new_node_with_optimizer(new_limit);
  _phase->set_ctrl(new_limit, pre_ctrl);
  TRACE_ALIGN_VECTOR_NODE(new_limit);

  // 5: Compute (15a, b):
  //    Prevent pre-loop from going past the original limit of the loop.
  Node* constrained_limit =
    (stride > 0) ? (Node*) new MinINode(new_limit, orig_limit)
                 : (Node*) new MaxINode(new_limit, orig_limit);
  _igvn.register_new_node_with_optimizer(constrained_limit);
  _phase->set_ctrl(constrained_limit, pre_ctrl);
  TRACE_ALIGN_VECTOR_NODE(constrained_limit);

  // 6: Hack the pre-loop limit
  _igvn.replace_input_of(pre_opaq, 1, constrained_limit);
}

//------------------------------init---------------------------
void SuperWord::init() {
  _dg.init();
  _packset.clear();
  _disjoint_ptrs.clear();
  _block.clear();
  _data_entry.clear();
  _mem_slice_head.clear();
  _mem_slice_tail.clear();
  _node_info.clear();
  _align_to_ref = nullptr;
  _race_possible = 0;
  _early_return = false;
  _num_work_vecs = 0;
  _num_reductions = 0;
}

//------------------------------print_packset---------------------------
void SuperWord::print_packset() {
#ifndef PRODUCT
  tty->print_cr("packset");
  for (int i = 0; i < _packset.length(); i++) {
    tty->print_cr("Pack: %d", i);
    Node_List* p = _packset.at(i);
    if (p == nullptr) {
      tty->print_cr("  nullptr");
    } else {
      print_pack(p);
    }
  }
#endif
}

//------------------------------print_pack---------------------------
void SuperWord::print_pack(Node_List* p) {
  for (uint i = 0; i < p->size(); i++) {
    print_stmt(p->at(i));
  }
}

//------------------------------print_bb---------------------------
void SuperWord::print_bb() {
#ifndef PRODUCT
  tty->print_cr("\nBlock");
  for (int i = 0; i < _block.length(); i++) {
    Node* n = _block.at(i);
    tty->print("%d ", i);
    if (n) {
      n->dump();
    }
  }
#endif
}

//------------------------------print_stmt---------------------------
void SuperWord::print_stmt(Node* s) {
#ifndef PRODUCT
  tty->print(" align: %d \t", alignment(s));
  s->dump();
#endif
}

// ========================= OrderedPair =====================

const OrderedPair OrderedPair::initial;

// ========================= SWNodeInfo =====================

const SWNodeInfo SWNodeInfo::initial;


// ============================ DepGraph ===========================

//------------------------------make_node---------------------------
// Make a new dependence graph node for an ideal node.
DepMem* DepGraph::make_node(Node* node) {
  DepMem* m = new (_arena) DepMem(node);
  if (node != nullptr) {
    assert(_map.at_grow(node->_idx) == nullptr, "one init only");
    _map.at_put_grow(node->_idx, m);
  }
  return m;
}

//------------------------------make_edge---------------------------
// Make a new dependence graph edge from dpred -> dsucc
DepEdge* DepGraph::make_edge(DepMem* dpred, DepMem* dsucc) {
  DepEdge* e = new (_arena) DepEdge(dpred, dsucc, dsucc->in_head(), dpred->out_head());
  dpred->set_out_head(e);
  dsucc->set_in_head(e);
  return e;
}

// ========================== DepMem ========================

//------------------------------in_cnt---------------------------
int DepMem::in_cnt() {
  int ct = 0;
  for (DepEdge* e = _in_head; e != nullptr; e = e->next_in()) ct++;
  return ct;
}

//------------------------------out_cnt---------------------------
int DepMem::out_cnt() {
  int ct = 0;
  for (DepEdge* e = _out_head; e != nullptr; e = e->next_out()) ct++;
  return ct;
}

//------------------------------print-----------------------------
void DepMem::print() {
#ifndef PRODUCT
  tty->print("  DepNode %d (", _node->_idx);
  for (DepEdge* p = _in_head; p != nullptr; p = p->next_in()) {
    Node* pred = p->pred()->node();
    tty->print(" %d", pred != nullptr ? pred->_idx : 0);
  }
  tty->print(") [");
  for (DepEdge* s = _out_head; s != nullptr; s = s->next_out()) {
    Node* succ = s->succ()->node();
    tty->print(" %d", succ != nullptr ? succ->_idx : 0);
  }
  tty->print_cr(" ]");
#endif
}

// =========================== DepEdge =========================

//------------------------------DepPreds---------------------------
void DepEdge::print() {
#ifndef PRODUCT
  tty->print_cr("DepEdge: %d [ %d ]", _pred->node()->_idx, _succ->node()->_idx);
#endif
}

// =========================== DepPreds =========================
// Iterator over predecessor edges in the dependence graph.

//------------------------------DepPreds---------------------------
DepPreds::DepPreds(Node* n, const DepGraph& dg) {
  _n = n;
  _done = false;
  if (_n->is_Store() || _n->is_Load()) {
    _next_idx = MemNode::Address;
    _end_idx  = n->req();
    _dep_next = dg.dep(_n)->in_head();
  } else if (_n->is_Mem()) {
    _next_idx = 0;
    _end_idx  = 0;
    _dep_next = dg.dep(_n)->in_head();
  } else {
    _next_idx = 1;
    _end_idx  = _n->req();
    _dep_next = nullptr;
  }
  next();
}

//------------------------------next---------------------------
void DepPreds::next() {
  if (_dep_next != nullptr) {
    _current  = _dep_next->pred()->node();
    _dep_next = _dep_next->next_in();
  } else if (_next_idx < _end_idx) {
    _current  = _n->in(_next_idx++);
  } else {
    _done = true;
  }
}

// =========================== DepSuccs =========================
// Iterator over successor edges in the dependence graph.

//------------------------------DepSuccs---------------------------
DepSuccs::DepSuccs(Node* n, DepGraph& dg) {
  _n = n;
  _done = false;
  if (_n->is_Load()) {
    _next_idx = 0;
    _end_idx  = _n->outcnt();
    _dep_next = dg.dep(_n)->out_head();
  } else if (_n->is_Mem() || _n->is_memory_phi()) {
    _next_idx = 0;
    _end_idx  = 0;
    _dep_next = dg.dep(_n)->out_head();
  } else {
    _next_idx = 0;
    _end_idx  = _n->outcnt();
    _dep_next = nullptr;
  }
  next();
}

//-------------------------------next---------------------------
void DepSuccs::next() {
  if (_dep_next != nullptr) {
    _current  = _dep_next->succ()->node();
    _dep_next = _dep_next->next_out();
  } else if (_next_idx < _end_idx) {
    _current  = _n->raw_out(_next_idx++);
  } else {
    _done = true;
  }
}

//
// --------------------------------- vectorization/simd -----------------------------------
//
bool SuperWord::same_origin_idx(Node* a, Node* b) const {
  return a != nullptr && b != nullptr && _clone_map.same_idx(a->_idx, b->_idx);
}
bool SuperWord::same_generation(Node* a, Node* b) const {
  return a != nullptr && b != nullptr && _clone_map.same_gen(a->_idx, b->_idx);
}
