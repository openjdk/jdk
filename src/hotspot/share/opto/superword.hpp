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

// The PairSet is a set of pairs. These are later combined to packs,
// and stored in the PackSet.
class PairSet : public StackObj {
private:
  const VLoop& _vloop;
  const VLoopBody& _body;

  // Doubly-linked pairs. If not linked: -1
  GrowableArray<int> _left_to_right; // bb_idx -> bb_idx
  GrowableArray<int> _right_to_left; // bb_idx -> bb_idx
  // Example:
  //
  //   Pairs: (n1, n2) and (n2, n3)
  //   bb_idx(n1) = 1
  //   bb_idx(n2) = 3
  //   bb_idx(n3) = 5
  //
  //   index / bb_idx:   0   1   2   3   4   5   6
  //
  //   left_to_right:  |   | 3 |   | 5 |   |   |   |
  //                         n1----->
  //                                 n2----->
  //
  //   right_to_left:  |   |   |   | 1 |   | 3 |   |
  //                          <------n2
  //                                  <------n3
  //
  //   Nodes with bb_idx 0, 2, 4, and 6 are in no pair, they are thus neither left nor right elements,
  //   and hence have no entries in the mapping.
  //
  //   Nodes with bb_idx 1 and 3 (n1 and n2) are both a left element in some pair. Therefore, they both
  //   have an entry in the left_to_right mapping. This mapping indicates which right element they are
  //   paired with, namely the nodes with bb_idx 3 and 5 (n2 and n3), respectively.
  //
  //   Nodes with bb_idx 3 and 5 (n2 and n4) are both a right element in some pair. Therefore, they both
  //   have an entry in the right_to_left mapping. This mapping indicates which left element they are
  //   paired with, namely the nodes with bb_idx 1 and 3 (n1 and n2), respectively.
  //
  //   Node n1 with bb_idx 1 is not a right element in any pair, thus its right_to_left is empty.
  //
  //   Node n2 with bb_idx 3 is both a left element of pair (n2, n3), and a right element of pair (n1, n2).
  //   Thus it has entries in both left_to_right (mapping n2->n3) and right_to_left (mapping n2->n1).
  //
  //   Node n3 with bb_idx 5 is not a left element in any pair, thus its left_to_right is empty.

  // List of all left elements bb_idx, in the order of pair addition.
  GrowableArray<int> _lefts_in_insertion_order;

public:
  // Initialize empty, i.e. all not linked (-1).
  PairSet(Arena* arena, const VLoopAnalyzer& vloop_analyzer) :
    _vloop(vloop_analyzer.vloop()),
    _body(vloop_analyzer.body()),
    _left_to_right(arena, _body.body().length(), _body.body().length(), -1),
    _right_to_left(arena, _body.body().length(), _body.body().length(), -1),
    _lefts_in_insertion_order(arena, 8, 0, 0) {}

  const VLoopBody& body() const { return _body; }

  bool is_empty() const { return _lefts_in_insertion_order.is_empty(); }

  bool is_left(int i)  const { return _left_to_right.at(i) != -1; }
  bool is_right(int i) const { return _right_to_left.at(i) != -1; }
  bool is_left(const Node* n)  const { return _vloop.in_bb(n) && is_left( _body.bb_idx(n)); }
  bool is_right(const Node* n) const { return _vloop.in_bb(n) && is_right(_body.bb_idx(n)); }

  bool is_pair(const Node* n1, const Node* n2) const { return is_left(n1) && get_right_for(n1) == n2; }

  bool is_left_in_a_left_most_pair(int i)   const { return is_left(i) && !is_right(i); }
  bool is_right_in_a_right_most_pair(int i) const { return !is_left(i) && is_right(i); }
  bool is_left_in_a_left_most_pair(const Node* n)   const { return is_left_in_a_left_most_pair( _body.bb_idx(n)); }
  bool is_right_in_a_right_most_pair(const Node* n) const { return is_right_in_a_right_most_pair(_body.bb_idx(n)); }

  int get_right_for(int i) const { return _left_to_right.at(i); }
  Node* get_right_for(const Node* n) const { return _body.body().at(get_right_for(_body.bb_idx(n))); }
  Node* get_right_or_null_for(const Node* n) const { return is_left(n) ? get_right_for(n) : nullptr; }

  // To access elements in insertion order:
  int length() const { return _lefts_in_insertion_order.length(); }
  Node* left_at_in_insertion_order(int i)  const { return _body.body().at(_lefts_in_insertion_order.at(i)); }
  Node* right_at_in_insertion_order(int i) const { return _body.body().at(get_right_for(_lefts_in_insertion_order.at(i))); }

  void add_pair(Node* n1, Node* n2) {
    assert(n1 != nullptr && n2 != nullptr && n1 != n2, "no nullptr, and different nodes");
    assert(!is_left(n1) && !is_right(n2), "cannot be left twice, or right twice");
    int bb_idx_1 = _body.bb_idx(n1);
    int bb_idx_2 = _body.bb_idx(n2);
    _left_to_right.at_put(bb_idx_1, bb_idx_2);
    _right_to_left.at_put(bb_idx_2, bb_idx_1);
    _lefts_in_insertion_order.append(bb_idx_1);
    assert(is_left(n1) && is_right(n2), "must be set now");
  }

  NOT_PRODUCT(void print() const;)
};

// Iterate over the PairSet, pair-chain by pair-chain.
// A pair-chain starts with a "left-most" pair (n1, n2), where n1 is never a right-element
// in any pair. We walk a chain: (n2, n3), (n3, n4) ... until we hit a "right-most" pair
// where the right-element is never a left-element of any pair.
// These pair-chains will later be combined into packs by combine_pairs_to_longer_packs.
class PairSetIterator : public StackObj {
private:
  const PairSet& _pairset;
  const VLoopBody& _body;

  int _chain_start_bb_idx; // bb_idx of left-element in the left-most pair.
  int _current_bb_idx;     // bb_idx of left-element of the current pair.
  const int _end_bb_idx;

public:
  PairSetIterator(const PairSet& pairset) :
    _pairset(pairset),
    _body(pairset.body()),
    _chain_start_bb_idx(-1),
    _current_bb_idx(-1),
    _end_bb_idx(_body.body().length())
  {
    next_chain();
  }

  bool done() const {
    return _chain_start_bb_idx >= _end_bb_idx;
  }

  Node* left() const {
    return _body.body().at(_current_bb_idx);
  }

  Node* right() const {
    int bb_idx_2 = _pairset.get_right_for(_current_bb_idx);
    return _body.body().at(bb_idx_2);
  }

  // Try to keep walking on the current pair-chain, else find a new pair-chain.
  void next() {
    assert(_pairset.is_left(_current_bb_idx), "current was valid");
    _current_bb_idx = _pairset.get_right_for(_current_bb_idx);
    if (!_pairset.is_left(_current_bb_idx)) {
      next_chain();
    }
  }

private:
  void next_chain() {
    do {
      _chain_start_bb_idx++;
    } while (!done() && !_pairset.is_left_in_a_left_most_pair(_chain_start_bb_idx));
    _current_bb_idx = _chain_start_bb_idx;
  }
};

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

class PackSet : public StackObj {
private:
  const VLoop& _vloop;
  const VLoopBody& _body;

  // Set of all packs:
  GrowableArray<Node_List*> _packs;

  // Mapping from nodes to their pack: bb_idx -> pack
  GrowableArray<Node_List*> _node_to_pack;

  NOT_PRODUCT(const bool _trace_packset;)
  NOT_PRODUCT(const bool _trace_rejections;)

public:
  // Initialize empty, i.e. no packs, and unmapped (nullptr).
  PackSet(Arena* arena, const VLoopAnalyzer& vloop_analyzer
          NOT_PRODUCT(COMMA bool trace_packset COMMA bool trace_rejections)
          ) :
    _vloop(vloop_analyzer.vloop()),
    _body(vloop_analyzer.body()),
    _packs(arena, 8, 0, nullptr),
    _node_to_pack(arena, _body.body().length(), _body.body().length(), nullptr)
    NOT_PRODUCT(COMMA _trace_packset(trace_packset))
    NOT_PRODUCT(COMMA _trace_rejections(trace_rejections))
    {}

  // Accessors to iterate over packs.
  int length() const { return _packs.length(); }
  bool is_empty() const { return _packs.is_empty(); }
  Node_List* at(int i) const { return _packs.at(i); }

private:
  void map_node_in_pack(const Node* n, Node_List* new_pack) {
    assert(get_pack(n) == nullptr, "was previously unmapped");
    _node_to_pack.at_put(_body.bb_idx(n), new_pack);
  }

  void remap_node_in_pack(const Node* n, Node_List* new_pack) {
    assert(get_pack(n) != nullptr && new_pack != nullptr && get_pack(n) != new_pack, "was previously mapped");
    _node_to_pack.at_put(_body.bb_idx(n), new_pack);
  }

  void unmap_node_in_pack(const Node* n) {
    assert(get_pack(n) != nullptr, "was previously mapped");
    _node_to_pack.at_put(_body.bb_idx(n), nullptr);
  }

  void unmap_all_nodes_in_pack(Node_List* old_pack) {
    for (uint i = 0; i < old_pack->size(); i++) {
      unmap_node_in_pack(old_pack->at(i));
    }
  }
public:
  Node_List* get_pack(const Node* n) const { return !_vloop.in_bb(n) ? nullptr : _node_to_pack.at(_body.bb_idx(n)); }

  void add_pack(Node_List* pack) {
    _packs.append(pack);
    for (uint i = 0; i < pack->size(); i++) {
      Node* n = pack->at(i);
      map_node_in_pack(n, pack);
    }
  }

private:
  SplitStatus split_pack(const char* split_name, Node_List* pack, SplitTask task);
public:
  template <typename SplitStrategy>
  void split_packs(const char* split_name, SplitStrategy strategy);

  template <typename FilterPredicate>
  void filter_packs(const char* filter_name,
                    const char* rejection_message,
                    FilterPredicate filter);

  void clear() { _packs.clear(); }

private:
  NOT_PRODUCT(bool is_trace_superword_packset() const { return _trace_packset; })
  NOT_PRODUCT(bool is_trace_superword_rejections() const { return _trace_rejections; })
public:
  DEBUG_ONLY(void verify() const;)
  NOT_PRODUCT(void print() const;)
  NOT_PRODUCT(static void print_pack(Node_List* pack);)
};

// ========================= SuperWord =====================

// -----------------------------SWNodeInfo---------------------------------
// Per node info needed by SuperWord
class SWNodeInfo {
 public:
  int         _alignment; // memory alignment for a node

  SWNodeInfo() : _alignment(-1) {}
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

  GrowableArray<SWNodeInfo> _node_info;  // Info needed per node
  CloneMap&            _clone_map;       // map of nodes created in cloning

  PairSet _pairset;
  PackSet _packset;

  // Memory reference, and the alignment width (aw) for which we align the main-loop,
  // by adjusting the pre-loop limit.
  MemNode const* _mem_ref_for_main_loop_alignment;
  int _aw_for_main_loop_alignment;

 public:
  SuperWord(const VLoopAnalyzer &vloop_analyzer);

  // Attempt to run the SuperWord algorithm on the loop. Return true if we succeed.
  bool transform_loop();

  // Decide if loop can eventually be vectorized, and what unrolling factor is required.
  static void unrolling_analysis(const VLoop &vloop, int &local_loop_unroll_factor);

  // VLoop accessors
  PhaseIdealLoop* phase()     const { return _vloop.phase(); }
  PhaseIterGVN& igvn()        const { return _vloop.phase()->igvn(); }
  IdealLoopTree* lpt()        const { return _vloop.lpt(); }
  CountedLoopNode* cl()       const { return _vloop.cl(); }
  PhiNode* iv()               const { return _vloop.iv(); }
  int iv_stride()             const { return cl()->stride_con(); }
  bool in_bb(const Node* n)   const { return _vloop.in_bb(n); }

  // VLoopReductions accessors
  bool is_marked_reduction(const Node* n) const {
    return _vloop_analyzer.reductions().is_marked_reduction(n);
  }

  bool reduction(const Node* n1, const Node* n2) const {
    return _vloop_analyzer.reductions().is_marked_reduction_pair(n1, n2);
  }

  // VLoopMemorySlices accessors
  bool same_memory_slice(MemNode* n1, MemNode* n2) const {
    return _vloop_analyzer.memory_slices().same_memory_slice(n1, n2);
  }

  // VLoopBody accessors
  const GrowableArray<Node*>& body() const {
    return _vloop_analyzer.body().body();
  }

  int bb_idx(const Node* n) const     {
    return _vloop_analyzer.body().bb_idx(n);
  }

  // VLoopTypes accessors
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

  // VLoopDependencyGraph accessors
  const VLoopDependencyGraph& dependency_graph() const {
    return _vloop_analyzer.dependency_graph();
  }

  bool independent(Node* n1, Node* n2) const {
    return _vloop_analyzer.dependency_graph().independent(n1, n2);
  }

  bool mutually_independent(const Node_List* nodes) const {
    return _vloop_analyzer.dependency_graph().mutually_independent(nodes);
  }

  // VLoopVPointer accessors
  const VPointer& vpointer(const MemNode* mem) const {
    return _vloop_analyzer.vpointers().vpointer(mem);
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

  const PackSet& packset() const { return _packset; }
  Node_List* get_pack(const Node* n) const { return _packset.get_pack(n); }

 private:
  bool           _do_vector_loop;  // whether to do vectorization/simd style
  int            _num_work_vecs;   // Number of non memory vector operations
  int            _num_reductions;  // Number of reduction expressions applied

  // Accessors
  Arena* arena()                   { return &_arena; }

  int get_vw_bytes_special(MemNode* s);

  // Ensure node_info contains element "i"
  void grow_node_info(int i) { if (i >= _node_info.length()) _node_info.at_put_grow(i, SWNodeInfo::initial); }

  // should we align vector memory references on this platform?
  bool vectors_should_be_aligned() { return !Matcher::misaligned_vectors_ok() || AlignVector; }

  // memory alignment for a node
  int alignment(Node* n) const               { return _node_info.adr_at(bb_idx(n))->_alignment; }
  void set_alignment(Node* n, int a)         { int i = bb_idx(n); grow_node_info(i); _node_info.adr_at(i)->_alignment = a; }

  // is pack good for converting into one vector node replacing bunches of Cmp, Bool, CMov nodes.
  static bool requires_long_to_int_conversion(int opc);
  // For pack p, are all idx operands the same?
  bool same_inputs(const Node_List* p, int idx) const;
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
  // Is s1 immediately before s2 in memory?
  bool are_adjacent_refs(Node* s1, Node* s2) const;
  // Are s1 and s2 similar?
  bool isomorphic(Node* s1, Node* s2);
  // Do we have pattern n1 = (iv + c) and n2 = (iv + c + 1)?
  bool is_populate_index(const Node* n1, const Node* n2) const;
  // For a node pair (s1, s2) which is isomorphic and independent,
  // do s1 and s2 have similar input edges?
  bool have_similar_inputs(Node* s1, Node* s2);
  void set_alignment(Node* s1, Node* s2, int align);
  int adjust_alignment_for_type_conversion(Node* s, Node* t, int align);

  void extend_pairset_with_more_pairs_by_following_use_and_def();
  bool extend_pairset_with_more_pairs_by_following_def(Node* s1, Node* s2);
  bool extend_pairset_with_more_pairs_by_following_use(Node* s1, Node* s2);
  void order_inputs_of_all_use_pairs_to_match_def_pair(Node* def1, Node* def2);
  enum PairOrderStatus { Ordered, Unordered, Unknown };
  PairOrderStatus order_inputs_of_uses_to_match_def_pair(Node* def1, Node* def2, Node* use1, Node* use2);
  int estimate_cost_savings_when_packing_as_pair(const Node* s1, const Node* s2) const;

  void combine_pairs_to_longer_packs();

  void split_packs_at_use_def_boundaries();
  void split_packs_only_implemented_with_smaller_size();
  void split_packs_to_break_mutual_dependence();

  void filter_packs_for_power_of_2_size();
  void filter_packs_for_mutual_independence();
  void filter_packs_for_alignment();
  const AlignmentSolution* pack_alignment_solution(const Node_List* pack);
  void filter_packs_for_implemented();
  void filter_packs_for_profitable();

  DEBUG_ONLY(void verify_packs() const;)

  // Adjust the memory graph for the packed operations
  void schedule();
  // Helper function for schedule, that reorders all memops, slice by slice, according to the schedule
  void schedule_reorder_memops(Node_List &memops_schedule);

  // Convert packs into vector node operations
  bool output();
  // Create a vector operand for the nodes in pack p for operand: in(opd_idx)
  Node* vector_opd(Node_List* p, int opd_idx);

  // Can code be generated for the pack, restricted to size nodes?
  bool implemented(const Node_List* pack, const uint size) const;
  // Find the maximal implemented size smaller or equal to the packs size
  uint max_implemented_size(const Node_List* pack);

  // For pack p, are all operands and all uses (with in the block) vector?
  bool profitable(const Node_List* p) const;

  // Verify that all uses of packs are also packs, i.e. we do not need extract operations.
  DEBUG_ONLY(void verify_no_extract();)

  // Check if n_super's pack uses are a superset of n_sub's pack uses.
  bool has_use_pack_superset(const Node* n1, const Node* n2) const;
  // Find a boundary in the pack, where left and right have different pack uses and defs.
  uint find_use_def_boundary(const Node_List* pack) const;

  // Is use->in(u_idx) a vector use?
  bool is_vector_use(Node* use, int u_idx) const;

  // Initialize per node info
  void initialize_node_info();
  // Return the longer type for vectorizable type-conversion node or illegal type for other nodes.
  BasicType longer_type_for_conversion(Node* n) const;
  // Find the longest type in def-use chain for packed nodes, and then compute the max vector size.
  int max_vector_size_in_def_use_chain(Node* n);

  static LoadNode::ControlDependency control_dependency(Node_List* p);
  // Alignment within a vector memory reference
  int memory_alignment(MemNode* s, int iv_adjust);
  // Ensure that the main loop vectors are aligned by adjusting the pre loop limit.
  void determine_mem_ref_and_aw_for_main_loop_alignment();
  void adjust_pre_loop_limit_to_align_main_loop_vectors();
};

#endif // SHARE_OPTO_SUPERWORD_HPP
