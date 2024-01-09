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

#ifndef SHARE_OPTO_SUPERWORD_HPP
#define SHARE_OPTO_SUPERWORD_HPP

#include "opto/vectorization.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/pair.hpp"


// ----------------- SuperWord Auto-Vectorizer --------------
//
// Algorithm based on:
//
// Exploiting SuperWord Level Parallelism with
//   Multimedia Instruction Sets
// by
//   Samuel Larsen and Saman Amarasinghe
//   MIT Laboratory for Computer Science
// date
//   May 2000
// published in
//   ACM SIGPLAN Notices
//   Proceedings of ACM PLDI '00,  Volume 35 Issue 5
//
// ---------------------- Definitions -----------------------
//
// Definitions:
//
// ILP (Instruction Level Parallelism):
//   Parallel or simultaneous execution of multiple operations.
//   The average number of operations run per CPU cycle.
//
// SIMD instructions (Single Input Multiple Data instructions):
//   Instructions that perform a single operation (e.g. add / mul)
//   on multiple data inputs (e.g. vector add / mul).
//   Using SIMD instructions can be a way to increase ILP.
//
// SLP (SuperWord Level Parallelism):
//   ILP by the use of (short) SIMD instructions, where a piece
//   of a program with scalar operations was analyzed for operations
//   that can be packed into SIMD instructions, and hence executed
//   in parallel.
//
// Isomorphic:
//   Adjective for operations which are of the same form. They
//   perform the same operations on the same structure of inputs.
//   SIMD instructions execute the same operations on every
//   element, with the inputs all having the same structure.
//   Hence, packed operations must be isomorphic.
//
// Independent:
//   Adjective for operations which can be executed in parallel
//   without changing the semantics of the program. SIMD instructions
//   execute all their packed operations in parallel. Hence packed
//   operations must be independent.
//
// Pack (definition 3.1):
//   A pack is an n-tuple, <s1, ...,sn>, where, s1,...,sn are
//   independent isomorphic operations (in a basic block).
//
// Packset (definition 3.2):
//   A packset is a set of packs.
//
// Pair (definition 3.3):
//   A pair is a pack of size two, where the first operation is
//   considered the left element, and the second operation is
//   considered the right element.
//
// -------------------- Algorithm Summary -------------------
//
// As designed by the paper cited above, the SuperWord algorithm can
// be applied to any basic block, not just loop nests. However, the
// implementation here is only applied to innermost loops.
//
// These are the steps of the SuperWord auto-vectorizer:
//
// 1)  PhaseIdealLoop::insert_pre_post_loops
//     Split the CountedLoop into pre-main-post loops. The pre-loop
//     ensures alignment for the main-loop, the main-loop is strip-
//     mined (occasionally safepoints) and is the candidate for
//     vectorization. The post-loop executes the remaining iterations.
//
// 2)  VLoop::check_preconditions
//     For vectorization loops must have a certain form, for example
//     no control flow other than the loop exit check.
//
// 3)  SuperWord::unrolling_analysis
//     We check if there are any forbidden nodes in the loop. If not,
//     then we determine the optimal unrolling factor, such that we
//     do not unroll unnecessarily, but still can fill the maximal
//     vector length. This depends on the types used in the loop.
//
// 4)  PhaseIdealLoop::do_unroll
//     We unroll until the desired unroll factor is reached. This
//     is supposed to increase the parallelism in the loop body,
//     as there are now many iterations merged together which can
//     hopefully be packed into SIMD vector operations.
//
// 5)  VLoopAnalyzer::analyze
//     In preparation for auto-vectorization, the loop body is analyzed.
//     We find reductions and the memory slices. We determine the type
//     of every node, and construct a dependence graph. The resulting
//     data structures are then available to the auto-vectorizer.
//
// 6)  SuperWord::transform_loop
//     We try to (partially) vectorize the loop. We do this as follows:
//
//     a) find_adjacent_refs:
//        We find pairs independent isomorphic adjacent memory operations.
//
//     b) extend_packlist:
//        We iteratively extend these "seed" pairs to their inputs and
//        outputs (non-memory operations), hopefully extending to all
//        operations that can be parallelized.
//
//     c) combine_packs:
//        We combine the pairs into vector sized packs, hopefully filling
//        the maximal vector size.
//
//     d) filter_packs:
//        We filter the packs, checking if the operations are implemented
//        in the backend, and checking if the inputs and outputs to the
//        packs are also vectorizable.
//
//     e) schedule:
//        We construct the PacksetGraph, based on the dependence graph
//        and the packset. We schedule it to a linear order. If there
//        are cycles in the graph, this is not possible and we bailout.
//
//        If the schedule succeeds, we know that vectorization will be
//        successful. We can now start making changes to the graph.
//
//     f) schedule_reorder_memops:
//        We adjust the memory graph of each memory slice according to
//        the linear order of the schedule.
//
//     g) align_initial_loop_index:
//        We adjust the pre-loop limit so that the main-loop is aligned.
//
//     h) output:
//        For each pack, we replace the scalar operations with a vector
//        operation.
//
// 9)  PhaseIdealLoop::insert_vector_post_loop
//     Before the main-loop is super-unrolled,  we first make a clone
//     of it and call it the vector-post-loop, or vectorized drain-loop.
//     The super-unrolled main-loop might have quite a large stride,
//     and after its last iteration, there may still be many iterations
//     left. To avoid doing all of them in the post-loop, we execute
//     as many as possible with this vectorized drain-loop.
//
// 10) PhaseIdealLoop::do_unroll
//     We further unroll the vectorized main-loop (i.e. super-unroll).
//     The goal is to saturate the CPU pipeline with vector instructions,
//     and to reduce the overhead of the loop exit check.

// Per node info needed by SuperWord
class SWNodeInfo {
 public:
  int         _alignment; // memory alignment for a node
  Node_List*  _my_pack;   // pack containing this node

  SWNodeInfo() : _alignment(-1), _my_pack(nullptr) {}
  static const SWNodeInfo initial;
};

// Transforms scalar operations into packed (superword) operations.
class SuperWord : public ResourceObj {
 private:
  const VLoopAnalyzer &_vla;
  Arena* _arena;

  enum consts { top_align = -1, bottom_align = -666 };

  GrowableArray<Node_List*> _packset;    // Packs for the current block

  GrowableArray<SWNodeInfo> _node_info;  // Info needed per node
  CloneMap&            _clone_map;       // map of nodes created in cloning
  MemNode const* _align_to_ref;          // Memory reference that pre-loop will align to

  // Scratch pads
  Node_Stack   _n_idx_list;    // List of (node,index) pairs

  static constexpr char const* SUCCESS                 = "success";
  static constexpr char const* FAILURE_NO_ADJACENT_MEM = "no adjacent loads or stores found";
  static constexpr char const* FAILURE_COMBINE_PACKS   = "empty packset after combine_packs";
  static constexpr char const* FAILURE_FILTER_PACKS    = "empty packset after filter_packs";
  static constexpr char const* FAILURE_SCHEDULE_CYCLE  = "schedule found cycle in packset";
  static constexpr char const* FAILURE_OUTPUT_BAILOUT  = "unexpected bailout in output";

 public:
  SuperWord(const VLoopAnalyzer &vla);

  // Decide if loop can eventually be vectorized, and what unrolling factor is required.
  static void unrolling_analysis(const VLoop &vloop, int &local_loop_unroll_factor);

  // Attempt to run the SuperWord algorithm on the loop. Return true if we succeed.
  bool transform_loop();

  // VLoopAnalyzer
  const VLoopAnalyzer& vla()  const { return _vla; }
  IdealLoopTree* lpt()        const { return vla().lpt(); }
  PhaseIdealLoop* phase()     const { return vla().phase(); }
  PhaseIterGVN& igvn()        const { return vla().phase()->igvn(); }
  CountedLoopNode* cl()       const { return vla().cl(); }
  PhiNode* iv()               const { return vla().iv(); }
  int iv_stride()             const { return vla().iv_stride(); }
  bool in_body(const Node* n) const { return vla().in_body(n); }

  // VLoopAnalyzer reductions
  bool is_marked_reduction(const Node* n) const {
    return vla().reductions().is_marked_reduction(n);
  }
  bool reduction(Node* s1, Node* s2) const {
    return vla().reductions().is_marked_reduction_pair(s1, s2);
  }

  // VLoopAnalyzer memory slices
  bool same_memory_slice(MemNode* n1, MemNode* n2) const {
    return vla().memory_slices().same_memory_slice(n1, n2);
  }

  // VLoopAnalyzer body
  const GrowableArray<Node*>& body() const {
    return vla().body().body();
  }
  int body_idx(const Node* n) const     {
    return vla().body().body_idx(n);
  }

  // VLoopAnalyzer dependence graph
  bool independent(Node* s1, Node* s2) const {
    return vla().dependence_graph().independent(s1, s2);
  }

  // VLoopAnalyzer vector element type
  const Type* velt_type(Node* n) const {
    return vla().types().velt_type(n);
  }
  BasicType velt_basic_type(Node* n) const {
    return vla().types().velt_basic_type(n);
  }
  bool same_velt_type(Node* n1, Node* n2) const {
    return vla().types().same_velt_type(n1, n2);
  }
  int data_size(Node* n) const {
    return vla().types().data_size(n);
  }
  int vector_width(Node* n) const {
    return vla().types().vector_width(n);
  }
  int vector_width_in_bytes(const Node* n) const {
    return vla().types().vector_width_in_bytes(n);
  }

#ifndef PRODUCT
  // TraceAutoVectorization
  bool is_trace_superword_adjacent_memops() const {
    return vla().is_trace_superword_adjacent_memops();
  }
  bool is_trace_superword_alignment() const {
    return vla().is_trace_superword_alignment();
  }
  bool is_trace_superword_rejections() const {
    return vla().is_trace_superword_rejections();
  }
  bool is_trace_superword_packset() const {
    return vla().is_trace_superword_packset();
  }
  bool is_trace_superword_all() const {
    return vla().is_trace_superword_all();
  }
  bool is_trace_superword_info() const {
    return vla().is_trace_superword_info();
  }
  bool is_trace_superword_any() const {
    return vla().is_trace_superword_any();
  }
  bool is_trace_align_vector() const {
    return vla().is_trace_align_vector();
  }
#endif

  bool     do_vector_loop()        { return _do_vector_loop; }

  const GrowableArray<Node_List*>& packset() const { return _packset; }
 private:
  bool           _race_possible;   // In cases where SDMU is true
  bool           _do_vector_loop;  // whether to do vectorization/simd style
  int            _num_work_vecs;   // Number of non memory vector operations
  int            _num_reductions;  // Number of reduction expressions applied

  // Accessors
  Arena* arena()                   { return _arena; }

  int get_vw_bytes_special(MemNode* s);
  const MemNode* align_to_ref() const { return _align_to_ref; }
  void set_align_to_ref(const MemNode* m) { _align_to_ref = m; }

  // Ensure node_info contains element "i"
  void grow_node_info(int i) { if (i >= _node_info.length()) _node_info.at_put_grow(i, SWNodeInfo::initial); }

  // memory alignment for a node
  int alignment(Node* n) const               { return _node_info.adr_at(body_idx(n))->_alignment; }
  void set_alignment(Node* n, int a)         { int i = body_idx(n); grow_node_info(i); _node_info.adr_at(i)->_alignment = a; }

  // my_pack
 public:
  Node_List* my_pack(Node* n) const {
    return !vla().in_body(n) ? nullptr : _node_info.adr_at(body_idx(n))->_my_pack;
  }
 private:
  void set_my_pack(Node* n, Node_List* p)     { int i = body_idx(n); grow_node_info(i); _node_info.adr_at(i)->_my_pack = p; }
  // is pack good for converting into one vector node replacing bunches of Cmp, Bool, CMov nodes.
  static bool requires_long_to_int_conversion(int opc);
  // For pack p, are all idx operands the same?
  bool same_inputs(Node_List* p, int idx);
  // CloneMap utilities
  bool same_origin_idx(Node* a, Node* b) const;
  bool same_generation(Node* a, Node* b) const;

  // Attempt to run the SuperWord algorithm on the loop. Return true if we succeed.
  const char* transform_loop_helper();
  // Find the adjacent memory references and create pack pairs for them.
  const char* find_adjacent_refs();
  // Find a memory reference to align the loop induction variable to.
  MemNode* find_align_to_ref(Node_List &memops, int &idx);
  // Calculate loop's iv adjustment for this memory ops.
  int get_iv_adjustment(MemNode* mem);
  // Can s1 and s2 be in a pack with s1 immediately preceding s2 and  s1 aligned at "align"
  bool stmts_can_pack(Node* s1, Node* s2, int align);
  // Does s exist in a pack at position pos?
  bool exists_at(Node* s, uint pos);
  // Is s1 immediately before s2 in memory?
  bool are_adjacent_refs(Node* s1, Node* s2);
  // Are s1 and s2 similar?
  bool isomorphic(Node* s1, Node* s2);
  // For a node pair (s1, s2) which is isomorphic and independent,
  // do s1 and s2 have similar input edges?
  bool have_similar_inputs(Node* s1, Node* s2);
  void set_alignment(Node* s1, Node* s2, int align);
  // Extend packset by following use->def and def->use links from pack members.
  void extend_packlist();
  int adjust_alignment_for_type_conversion(Node* s, Node* t, int align);
  // Extend the packset by visiting operand definitions of nodes in pack p
  bool follow_use_defs(Node_List* p);
  // Extend the packset by visiting uses of nodes in pack p
  bool follow_def_uses(Node_List* p);
  // For extended packsets, ordinally arrange uses packset by major component
  void order_def_uses(Node_List* p);
  // Estimate the savings from executing s1 and s2 as a pack
  int est_savings(Node* s1, Node* s2);
  int adjacent_profit(Node* s1, Node* s2);
  int pack_cost(int ct);
  int unpack_cost(int ct);
  // Combine packs A and B with A.last == B.first into A.first..,A.last,B.second,..B.last
  const char* combine_packs();
  // Ensure all packs are aligned, if AlignVector is on.
  void filter_packs_for_alignment();
  // Find the set of alignment solutions for load/store pack.
  const AlignmentSolution* pack_alignment_solution(Node_List* pack);
  // Compress packset, such that it has no nullptr entries.
  void compress_packset();
  // Construct the map from nodes to packs.
  void construct_my_pack_map();
  // Remove packs that are not implemented or not profitable.
  const char* filter_packs();
  // Verify that for every pack, all nodes are mutually independent.
  // Also verify that packset and my_pack are consistent.
  DEBUG_ONLY(void verify_packs() const;)
  // Adjust the memory graph for the packed operations
  const char* schedule();
  // Helper function for schedule, that reorders all memops, slice by slice, according to the schedule
  void schedule_reorder_memops(Node_List &memops_schedule);

  // Convert packs into vector node operations
  const char* output();
  // Create a vector operand for the nodes in pack p for operand: in(opd_idx)
  Node* vector_opd(Node_List* p, int opd_idx);
  // Can code be generated for pack p?
  bool implemented(Node_List* p);
  // For pack p, are all operands and all uses (with in the block) vector?
  bool profitable(Node_List* p);
  // If a use of pack p is not a vector use, then replace the use with an extract operation.
  void insert_extracts(Node_List* p);
  // Is use->in(u_idx) a vector use?
  bool is_vector_use(Node* use, int u_idx);
  // Return the longer type for vectorizable type-conversion node or illegal type for other nodes.
  BasicType longer_type_for_conversion(Node* n);
  // Find the longest type in def-use chain for packed nodes, and then compute the max vector size.
  int max_vector_size_in_def_use_chain(Node* n);
  // Are s1 and s2 in a pack pair and ordered as s1,s2?
  bool in_packset(Node* s1, Node* s2);
  // Remove the pack at position pos in the packset
  void remove_pack_at(int pos);
  static LoadNode::ControlDependency control_dependency(Node_List* p);
  // Alignment within a vector memory reference
  int memory_alignment(MemNode* s, int iv_adjust);
  // Ensure that the main loop vectors are aligned by adjusting the pre loop limit.
  void adjust_pre_loop_limit_to_align_main_loop_vectors();
  // Is the use of d1 in u1 at the same operand position as d2 in u2?
  bool opnd_positions_match(Node* d1, Node* u1, Node* d2, Node* u2);
  void init();

  // print methods
  void print_packset() const;
  void print_pack(Node_List* p) const;
  void print_stmt(Node* s) const;

  void packset_sort(int n);
};

#endif // SHARE_OPTO_SUPERWORD_HPP
