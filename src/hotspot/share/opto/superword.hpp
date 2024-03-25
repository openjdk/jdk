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

//
//                  S U P E R W O R D   T R A N S F O R M
//
// SuperWords are short, fixed length vectors.
//
// Algorithm from:
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
// Definition 3.1 A Pack is an n-tuple, <s1, ...,sn>, where
// s1,...,sn are independent isomorphic statements in a basic
// block.
//
// Definition 3.2 A PackSet is a set of Packs.
//
// Definition 3.3 A Pair is a Pack of size two, where the
// first statement is considered the left element, and the
// second statement is considered the right element.

class VPointer;

// ========================= SuperWord =====================

// -----------------------------SWNodeInfo---------------------------------
// Per node info needed by SuperWord
class SWNodeInfo {
 public:
  int         _alignment; // memory alignment for a node
  Node_List*  _my_pack;   // pack containing this node

  SWNodeInfo() : _alignment(-1), _my_pack(nullptr) {}
  static const SWNodeInfo initial;
};

// -----------------------------SuperWord---------------------------------
// Transforms scalar operations into packed (superword) operations.
class SuperWord : public ResourceObj {
 private:
  const VLoopAnalyzer& _vloop_analyzer;
  const VLoop&         _vloop;

  // Arena for small data structures. Large data structures are allocated in
  // VSharedData, and reused over many AutoVectorizations.
  Arena _arena;

  enum consts { top_align = -1, bottom_align = -666 };

  GrowableArray<Node_List*> _packset;    // Packs for the current block

  GrowableArray<SWNodeInfo> _node_info;  // Info needed per node
  CloneMap&            _clone_map;       // map of nodes created in cloning
  MemNode const* _align_to_ref;          // Memory reference that pre-loop will align to

 public:
  SuperWord(const VLoopAnalyzer &vloop_analyzer);

  // Attempt to run the SuperWord algorithm on the loop. Return true if we succeed.
  bool transform_loop();

  // Decide if loop can eventually be vectorized, and what unrolling factor is required.
  static void unrolling_analysis(const VLoop &vloop, int &local_loop_unroll_factor);

  // VLoop Accessors
  PhaseIdealLoop* phase()     const { return _vloop.phase(); }
  PhaseIterGVN& igvn()        const { return _vloop.phase()->igvn(); }
  IdealLoopTree* lpt()        const { return _vloop.lpt(); }
  CountedLoopNode* cl()       const { return _vloop.cl(); }
  PhiNode* iv()               const { return _vloop.iv(); }
  int iv_stride()             const { return cl()->stride_con(); }
  bool in_bb(const Node* n)   const { return _vloop.in_bb(n); }

  // VLoopReductions Accessors
  bool is_marked_reduction(const Node* n) const {
    return _vloop_analyzer.reductions().is_marked_reduction(n);
  }

  bool reduction(Node* n1, Node* n2) const {
    return _vloop_analyzer.reductions().is_marked_reduction_pair(n1, n2);
  }

  // VLoopMemorySlices Accessors
  bool same_memory_slice(MemNode* n1, MemNode* n2) const {
    return _vloop_analyzer.memory_slices().same_memory_slice(n1, n2);
  }

  // VLoopBody Accessors
  const GrowableArray<Node*>& body() const {
    return _vloop_analyzer.body().body();
  }

  int bb_idx(const Node* n) const     {
    return _vloop_analyzer.body().bb_idx(n);
  }

  // VLoopTypes Accessors
  const Type* velt_type(Node* n) const {
    return _vloop_analyzer.types().velt_type(n);
  }

  BasicType velt_basic_type(Node* n) const {
    return _vloop_analyzer.types().velt_basic_type(n);
  }

  bool same_velt_type(Node* n1, Node* n2) const {
    return _vloop_analyzer.types().same_velt_type(n1, n2);
  }

  int data_size(Node* n) const {
    return _vloop_analyzer.types().data_size(n);
  }

  int vector_width(Node* n) const {
    return _vloop_analyzer.types().vector_width(n);
  }

  int vector_width_in_bytes(const Node* n) const {
    return _vloop_analyzer.types().vector_width_in_bytes(n);
  }

  // VLoopDependencyGraph Accessors
  const VLoopDependencyGraph& dependency_graph() const {
    return _vloop_analyzer.dependency_graph();
  }

  bool independent(Node* n1, Node* n2) const {
    return _vloop_analyzer.dependency_graph().independent(n1, n2);
  }

  bool mutually_independent(const Node_List* nodes) const {
    return _vloop_analyzer.dependency_graph().mutually_independent(nodes);
  }

#ifndef PRODUCT
  // TraceAutoVectorization and TraceSuperWord
  bool is_trace_superword_alignment() const {
    // Too verbose for TraceSuperWord
    return _vloop.vtrace().is_trace(TraceAutoVectorizationTag::SW_ALIGNMENT);
  }

  bool is_trace_superword_adjacent_memops() const {
    return TraceSuperWord ||
           _vloop.vtrace().is_trace(TraceAutoVectorizationTag::SW_ADJACENT_MEMOPS);
  }

  bool is_trace_superword_rejections() const {
    return TraceSuperWord ||
           _vloop.vtrace().is_trace(TraceAutoVectorizationTag::SW_REJECTIONS);
  }

  bool is_trace_superword_packset() const {
    return TraceSuperWord ||
           _vloop.vtrace().is_trace(TraceAutoVectorizationTag::SW_PACKSET);
  }

  bool is_trace_superword_info() const {
    return TraceSuperWord ||
           _vloop.vtrace().is_trace(TraceAutoVectorizationTag::SW_INFO);
  }

  bool is_trace_superword_verbose() const {
    // Too verbose for TraceSuperWord
    return _vloop.vtrace().is_trace(TraceAutoVectorizationTag::SW_VERBOSE);
  }

  bool is_trace_superword_any() const {
    return TraceSuperWord ||
           is_trace_align_vector() ||
           _vloop.vtrace().is_trace(TraceAutoVectorizationTag::SW_ALIGNMENT) ||
           _vloop.vtrace().is_trace(TraceAutoVectorizationTag::SW_ADJACENT_MEMOPS) ||
           _vloop.vtrace().is_trace(TraceAutoVectorizationTag::SW_REJECTIONS) ||
           _vloop.vtrace().is_trace(TraceAutoVectorizationTag::SW_PACKSET) ||
           _vloop.vtrace().is_trace(TraceAutoVectorizationTag::SW_INFO) ||
           _vloop.vtrace().is_trace(TraceAutoVectorizationTag::SW_VERBOSE);
  }

  bool is_trace_align_vector() const {
    return _vloop.vtrace().is_trace(TraceAutoVectorizationTag::ALIGN_VECTOR) ||
           is_trace_superword_verbose();
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
  Arena* arena()                   { return &_arena; }

  int get_vw_bytes_special(MemNode* s);
  const MemNode* align_to_ref() const { return _align_to_ref; }
  void set_align_to_ref(const MemNode* m) { _align_to_ref = m; }

  // Ensure node_info contains element "i"
  void grow_node_info(int i) { if (i >= _node_info.length()) _node_info.at_put_grow(i, SWNodeInfo::initial); }

  // should we align vector memory references on this platform?
  bool vectors_should_be_aligned() { return !Matcher::misaligned_vectors_ok() || AlignVector; }

  // memory alignment for a node
  int alignment(Node* n)                     { return _node_info.adr_at(bb_idx(n))->_alignment; }
  void set_alignment(Node* n, int a)         { int i = bb_idx(n); grow_node_info(i); _node_info.adr_at(i)->_alignment = a; }

  // my_pack
 public:
  Node_List* my_pack(const Node* n)     const { return !in_bb(n) ? nullptr : _node_info.adr_at(bb_idx(n))->_my_pack; }
 private:
  void set_my_pack(Node* n, Node_List* p)     { int i = bb_idx(n); grow_node_info(i); _node_info.adr_at(i)->_my_pack = p; }
  // is pack good for converting into one vector node replacing bunches of Cmp, Bool, CMov nodes.
  static bool requires_long_to_int_conversion(int opc);
  // For pack p, are all idx operands the same?
  bool same_inputs(const Node_List* p, int idx);
  // CloneMap utilities
  bool same_origin_idx(Node* a, Node* b) const;
  bool same_generation(Node* a, Node* b) const;

private:
  bool SLP_extract();
  // Find the adjacent memory references and create pack pairs for them.
  void find_adjacent_refs();
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
  // Do we have pattern n1 = (iv + c) and n2 = (iv + c + 1)?
  bool is_populate_index(const Node* n1, const Node* n2) const;
  // For a node pair (s1, s2) which is isomorphic and independent,
  // do s1 and s2 have similar input edges?
  bool have_similar_inputs(Node* s1, Node* s2);
  void set_alignment(Node* s1, Node* s2, int align);
  // Extend packset by following use->def and def->use links from pack members.
  void extend_packset_with_more_pairs_by_following_use_and_def();
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
  void combine_pairs_to_longer_packs();

  class SplitTask {
  private:
    enum Kind {
      // The lambda method for split_packs can return one of these tasks:
      Unchanged, // The pack is left in the packset, unchanged.
      Rejected,  // The pack is removed from the packset.
      Split,     // Split away split_size nodes from the end of the pack.
    };
    const Kind _kind;
    const uint _split_size;
    const char* _message;

    SplitTask(const Kind kind, const uint split_size, const char* message) :
        _kind(kind), _split_size(split_size), _message(message)
    {
      assert(message != nullptr, "must have message");
      assert(_kind != Unchanged || split_size == 0, "unchanged task conditions");
      assert(_kind != Rejected  || split_size == 0, "reject task conditions");
      assert(_kind != Split     || split_size != 0, "split task conditions");
    }

  public:
    static SplitTask make_split(const uint split_size, const char* message) {
      return SplitTask(Split, split_size, message);
    }

    static SplitTask make_unchanged() {
      return SplitTask(Unchanged, 0, "unchanged");
    }

    static SplitTask make_rejected(const char* message) {
      return SplitTask(Rejected, 0, message);
    }

    bool is_unchanged() const { return _kind == Unchanged; }
    bool is_rejected() const { return _kind == Rejected; }
    bool is_split() const { return _kind == Split; }
    const char* message() const { return _message; }

    uint split_size() const {
      assert(is_split(), "only split tasks have split_size");
      return _split_size;
    }
  };

  class SplitStatus {
  private:
    enum Kind {
      // After split_pack, we have:                              first_pack   second_pack
      Unchanged, // The pack is left in the pack, unchanged.     old_pack     nullptr
      Rejected,  // The pack is removed from the packset.        nullptr      nullptr
      Modified,  // The pack had some nodes removed.             old_pack     nullptr
      Split,     // The pack was split into two packs.           pack1        pack2
    };
    Kind _kind;
    Node_List* _first_pack;
    Node_List* _second_pack;

    SplitStatus(Kind kind, Node_List* first_pack, Node_List* second_pack) :
      _kind(kind), _first_pack(first_pack), _second_pack(second_pack)
    {
      assert(_kind != Unchanged || (first_pack != nullptr && second_pack == nullptr), "unchanged status conditions");
      assert(_kind != Rejected  || (first_pack == nullptr && second_pack == nullptr), "rejected status conditions");
      assert(_kind != Modified  || (first_pack != nullptr && second_pack == nullptr), "modified status conditions");
      assert(_kind != Split     || (first_pack != nullptr && second_pack != nullptr), "split status conditions");
    }

  public:
    static SplitStatus make_unchanged(Node_List* old_pack) {
      return SplitStatus(Unchanged, old_pack, nullptr);
    }

    static SplitStatus make_rejected() {
      return SplitStatus(Rejected, nullptr, nullptr);
    }

    static SplitStatus make_modified(Node_List* first_pack) {
      return SplitStatus(Modified, first_pack, nullptr);
    }

    static SplitStatus make_split(Node_List* first_pack, Node_List* second_pack) {
      return SplitStatus(Split, first_pack, second_pack);
    }

    bool is_unchanged() const { return _kind == Unchanged; }
    Node_List* first_pack() const { return _first_pack; }
    Node_List* second_pack() const { return _second_pack; }
  };

  SplitStatus split_pack(const char* split_name, Node_List* pack, SplitTask task);
  template <typename SplitStrategy>
  void split_packs(const char* split_name, SplitStrategy strategy);

  void split_packs_at_use_def_boundaries();
  void split_packs_only_implemented_with_smaller_size();
  void split_packs_to_break_mutual_dependence();

  // Filter out packs with various filter predicates
  template <typename FilterPredicate>
  void filter_packs(const char* filter_name,
                    const char* error_message,
                    FilterPredicate filter);
  void filter_packs_for_power_of_2_size();
  void filter_packs_for_mutual_independence();
  // Ensure all packs are aligned, if AlignVector is on.
  void filter_packs_for_alignment();
  // Find the set of alignment solutions for load/store pack.
  const AlignmentSolution* pack_alignment_solution(const Node_List* pack);
  // Compress packset, such that it has no nullptr entries.
  void compress_packset();
  // Construct the map from nodes to packs.
  void construct_my_pack_map();
  // Remove packs that are not implemented.
  void filter_packs_for_implemented();
  // Remove packs that are not profitable.
  void filter_packs_for_profitable();
  // Verify that for every pack, all nodes are mutually independent.
  // Also verify that packset and my_pack are consistent.
  DEBUG_ONLY(void verify_packs();)
  // Adjust the memory graph for the packed operations
  void schedule();
  // Helper function for schedule, that reorders all memops, slice by slice, according to the schedule
  void schedule_reorder_memops(Node_List &memops_schedule);

  // Convert packs into vector node operations
  bool output();
  // Create a vector operand for the nodes in pack p for operand: in(opd_idx)
  Node* vector_opd(Node_List* p, int opd_idx);

  // Can code be generated for the pack, restricted to size nodes?
  bool implemented(const Node_List* pack, uint size);
  // Find the maximal implemented size smaller or equal to the packs size
  uint max_implemented_size(const Node_List* pack);

  // For pack p, are all operands and all uses (with in the block) vector?
  bool profitable(const Node_List* p);
  // Verify that all uses of packs are also packs, i.e. we do not need extract operations.
  DEBUG_ONLY(void verify_no_extract();)

  // Check if n_super's pack uses are a superset of n_sub's pack uses.
  bool has_use_pack_superset(const Node* n1, const Node* n2) const;
  // Find a boundary in the pack, where left and right have different pack uses and defs.
  uint find_use_def_boundary(const Node_List* pack) const;
  // Is use->in(u_idx) a vector use?
  bool is_vector_use(Node* use, int u_idx);

  // Initialize per node info
  void initialize_node_info();
  // Compute max depth for expressions from beginning of block
  void compute_max_depth();
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

  // print methods
  void print_packset();
  void print_pack(Node_List* p);
  void print_stmt(Node* s);

  void packset_sort(int n);
};

#endif // SHARE_OPTO_SUPERWORD_HPP
