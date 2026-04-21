/*
 * Copyright (c) 2007, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/vtransform.hpp"
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
//
// For more documentation, see: SuperWord::SLP_extract

// The PairSet is a set of pairs. These are later combined to packs,
// and stored in the PackSet.
class PairSet : public StackObj {
private:
  const VTransform& _scalar_vtransform;
  const VLoop& _vloop;

  // Doubly-linked pairs. If not linked: -1
  GrowableArray<int> _left_to_right; // vtn idx -> vtn idx
  GrowableArray<int> _right_to_left; // vtn idx -> vtn idx
  // Example:
  //
  //   Pairs: (n1, n2) and (n2, n3)
  //   n1->_idx = 1
  //   n2->_idx = 3
  //   n3->_idx = 5
  //
  //   vtn idx:          0   1   2   3   4   5   6
  //
  //   left_to_right:  |   | 3 |   | 5 |   |   |   |
  //                         n1----->
  //                                 n2----->
  //
  //   right_to_left:  |   |   |   | 1 |   | 3 |   |
  //                          <------n2
  //                                  <------n3
  //
  //   Nodes with vtn idx 0, 2, 4, and 6 are in no pair, they are thus neither left nor right elements,
  //   and hence have no entries in the mapping.
  //
  //   Nodes with vtn idx 1 and 3 (n1 and n2) are both a left element in some pair. Therefore, they both
  //   have an entry in the left_to_right mapping. This mapping indicates which right element they are
  //   paired with, namely the nodes with vtn idx 3 and 5 (n2 and n3), respectively.
  //
  //   Nodes with vtn idx 3 and 5 (n2 and n4) are both a right element in some pair. Therefore, they both
  //   have an entry in the right_to_left mapping. This mapping indicates which left element they are
  //   paired with, namely the nodes with vtn idx 1 and 3 (n1 and n2), respectively.
  //
  //   Node n1 with vtn idx 1 is not a right element in any pair, thus its right_to_left is empty.
  //
  //   Node n2 with vtn idx 3 is both a left element of pair (n2, n3), and a right element of pair (n1, n2).
  //   Thus it has entries in both left_to_right (mapping n2->n3) and right_to_left (mapping n2->n1).
  //
  //   Node n3 with vtn idx 5 is not a left element in any pair, thus its left_to_right is empty.

  // List of all left elements vtn idx, in the order of pair addition.
  GrowableArray<int> _lefts_in_insertion_order;

public:
  // Initialize empty, i.e. all not linked (-1).
  PairSet(Arena* arena, const VTransform& scalar_vtransform) :
    _scalar_vtransform(scalar_vtransform),
    _vloop(scalar_vtransform.vloop()),
    _left_to_right(arena, _scalar_vtransform.graph().vtnodes().length(), _scalar_vtransform.graph().vtnodes().length(), -1),
    _right_to_left(arena, _scalar_vtransform.graph().vtnodes().length(), _scalar_vtransform.graph().vtnodes().length(), -1),
    _lefts_in_insertion_order(arena, 8, 0, 0) {}

  const VTransform& scalar_vtransform() const { return _scalar_vtransform; }

  bool is_empty() const { return _lefts_in_insertion_order.is_empty(); }

  bool is_left(int i)  const { return _left_to_right.at(i) != -1; }
  bool is_right(int i) const { return _right_to_left.at(i) != -1; }
  bool is_left(const VTransformNode* n)  const { return is_left(n->_idx); }
  bool is_right(const VTransformNode* n)  const { return is_right(n->_idx); }

  bool is_pair(const VTransformNode* n1, const VTransformNode* n2) const { return is_left(n1) && get_right_for(n1) == n2; }

  bool is_left_in_a_left_most_pair(int i)   const { return is_left(i) && !is_right(i); }
  bool is_right_in_a_right_most_pair(int i) const { return !is_left(i) && is_right(i); }
  bool is_left_in_a_left_most_pair(const VTransformNode* n)   const { return is_left_in_a_left_most_pair(n->_idx); }
  bool is_right_in_a_right_most_pair(const VTransformNode* n) const { return is_right_in_a_right_most_pair(n->_idx); }

  int get_right_for(int i) const { return _left_to_right.at(i); }
  const VTransformNode* get_right_for(const VTransformNode* n) const { return idx2vtn(get_right_for(n->_idx)); }
  const VTransformNode* get_right_or_null_for(const VTransformNode* n) const { return is_left(n) ? get_right_for(n) : nullptr; }

  // To access elements in insertion order:
  int length() const { return _lefts_in_insertion_order.length(); }
  const VTransformNode* left_at_in_insertion_order(int i)  const { return idx2vtn(_lefts_in_insertion_order.at(i)); }
  const VTransformNode* right_at_in_insertion_order(int i) const { return idx2vtn(get_right_for(_lefts_in_insertion_order.at(i))); }

  void add_pair(const VTransformNode* n1, const VTransformNode* n2) {
    assert(n1 != nullptr && n2 != nullptr && n1 != n2, "no nullptr, and different nodes");
    _left_to_right.at_put(n1->_idx, n2->_idx);
    _right_to_left.at_put(n2->_idx, n1->_idx);
    _lefts_in_insertion_order.append(n1->_idx);
    assert(is_left(n1) && is_right(n2), "must be set now");
  }

  NOT_PRODUCT(void print() const;)

  // TODO: consider making some more methods above private...
private:
  const VTransformNode* idx2vtn(int idx) const { return _scalar_vtransform.idx2vtn(idx); }
};

// Iterate over the PairSet, pair-chain by pair-chain.
// A pair-chain starts with a "left-most" pair (n1, n2), where n1 is never a right-element
// in any pair. We walk a chain: (n2, n3), (n3, n4) ... until we hit a "right-most" pair
// where the right-element is never a left-element of any pair.
// These pair-chains will later be combined into packs by combine_pairs_to_longer_packs.
class PairSetIterator : public StackObj {
private:
  const PairSet& _pairset;

  int _chain_start_idx; // idx of left-element in the left-most pair.
  int _current_idx;     // idx of left-element of the current pair.
  const int _end_idx;

public:
  PairSetIterator(const PairSet& pairset) :
    _pairset(pairset),
    _chain_start_idx(-1),
    _current_idx(-1),
    _end_idx(pairset.scalar_vtransform().graph().vtnodes().length())
  {
    next_chain();
  }

  bool done() const {
    return _chain_start_idx >= _end_idx;
  }

  const VTransformNode* left() const {
    return idx2vtn(_current_idx);
  }

  const VTransformNode* right() const {
    int idx_2 = _pairset.get_right_for(_current_idx);
    return idx2vtn(idx_2);
  }

  // Try to keep walking on the current pair-chain, else find a new pair-chain.
  void next() {
    assert(_pairset.is_left(_current_idx), "current was valid");
    _current_idx = _pairset.get_right_for(_current_idx);
    if (!_pairset.is_left(_current_idx)) {
      next_chain();
    }
  }

private:
  const VTransformNode* idx2vtn(int idx) const { return _pairset.scalar_vtransform().idx2vtn(idx); }

  void next_chain() {
    do {
      _chain_start_idx++;
    } while (!done() && !_pairset.is_left_in_a_left_most_pair(_chain_start_idx));
    _current_idx = _chain_start_idx;
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
  Pack* _first_pack;
  Pack* _second_pack;

  SplitStatus(Kind kind, Pack* first_pack, Pack* second_pack) :
    _kind(kind), _first_pack(first_pack), _second_pack(second_pack)
  {
    assert(_kind != Unchanged || (first_pack != nullptr && second_pack == nullptr), "unchanged status conditions");
    assert(_kind != Rejected  || (first_pack == nullptr && second_pack == nullptr), "rejected status conditions");
    assert(_kind != Modified  || (first_pack != nullptr && second_pack == nullptr), "modified status conditions");
    assert(_kind != Split     || (first_pack != nullptr && second_pack != nullptr), "split status conditions");
  }

public:
  static SplitStatus make_unchanged(Pack* old_pack) {
    return SplitStatus(Unchanged, old_pack, nullptr);
  }

  static SplitStatus make_rejected() {
    return SplitStatus(Rejected, nullptr, nullptr);
  }

  static SplitStatus make_modified(Pack* first_pack) {
    return SplitStatus(Modified, first_pack, nullptr);
  }

  static SplitStatus make_split(Pack* first_pack, Pack* second_pack) {
    return SplitStatus(Split, first_pack, second_pack);
  }

  bool is_unchanged() const { return _kind == Unchanged; }
  Pack* first_pack() const { return _first_pack; }
  Pack* second_pack() const { return _second_pack; }
};

class PackSet : public StackObj {
private:
  const VTransform& _scalar_vtransform; // TODO: is it even needed?
  const VLoop& _vloop;
  Arena* _arena;

  // Set of all packs:
  GrowableArray<Pack*> _packs;

  // Mapping from nodes to their pack: vtn->idx -> pack
  GrowableArray<Pack*> _node_to_pack;

public:
  // Initialize empty, i.e. no packs, and unmapped (nullptr).
  PackSet(Arena* arena, const VTransform& scalar_vtransform) :
    _scalar_vtransform(scalar_vtransform),
    _vloop(scalar_vtransform.vloop()),
    _arena(arena),
    _packs(arena, 8, 0, nullptr),
    _node_to_pack(arena, _scalar_vtransform.graph().vtnodes().length(), _scalar_vtransform.graph().vtnodes().length(), nullptr)
    {}

  // Accessors to iterate over packs.
  int length() const { return _packs.length(); }
  bool is_empty() const { return _packs.is_empty(); }
  Pack* at(int i) const { return _packs.at(i); }

private:
  void map_node_in_pack(const VTransformNode* n, Pack* new_pack) {
    assert(get_pack(n) == nullptr, "was previously unmapped");
    _node_to_pack.at_put(n->_idx, new_pack);
  }

  void remap_node_in_pack(const VTransformNode* n, Pack* new_pack) {
    assert(get_pack(n) != nullptr && new_pack != nullptr && get_pack(n) != new_pack, "was previously mapped");
    _node_to_pack.at_put(n->_idx, new_pack);
  }

  void unmap_node_in_pack(const VTransformNode* n) {
    assert(get_pack(n) != nullptr, "was previously mapped");
    _node_to_pack.at_put(n->_idx, nullptr);
  }

  void unmap_all_nodes_in_pack(Pack* old_pack) {
    for (int i = 0; i < old_pack->length(); i++) {
      unmap_node_in_pack(old_pack->at(i));
    }
  }
public:
  Pack* get_pack(const VTransformNode* n) const { return _node_to_pack.at(n->_idx); }

  void add_pack(Pack* pack) {
    _packs.append(pack);
    for (int i = 0; i < pack->length(); i++) {
      const VTransformNode* n = pack->at(i);
      map_node_in_pack(n, pack);
    }
  }

  Pack* strided_pack_input_at_index_or_null(const Pack* pack, const int index, const int stride, const int offset) const;
  bool is_muladds2i_pack_with_pack_inputs(const Pack* pack) const;
  Node* same_inputs_at_index_or_null(const Pack* pack, const int index) const;
  VTransformBoolTest get_bool_test(const Pack* bool_pack) const;

  Pack* pack_input_at_index_or_null(const Pack* pack, const int index) const {
    return strided_pack_input_at_index_or_null(pack, index, 1, 0);
  }

private:
  SplitStatus split_pack(const char* split_name, Pack* pack, SplitTask task);
public:
  template <typename SplitStrategy>
  void split_packs(const char* split_name, SplitStrategy strategy);

  template <typename FilterPredicate>
  void filter_packs(const char* filter_name,
                    const char* rejection_message,
                    FilterPredicate filter);

  void clear() { _packs.clear(); }

public:
  DEBUG_ONLY(void verify() const;)
  NOT_PRODUCT(void print() const;)
  NOT_PRODUCT(static void print_pack(Pack* pack);)
};

// -----------------------------SuperWord---------------------------------
// Transforms scalar operations into packed (superword) operations.
class SuperWord : public ResourceObj {
 private:
  const VTransformAnalyzer& _scalar_vtransform_analyzer;
  const VTransform&    _scalar_vtransform;
  const VLoopAnalyzer& _vloop_analyzer; // TODO: consider not using it...
  const VLoop&         _vloop;

  // Arena for small data structures. Large data structures are allocated in
  // VSharedData, and reused over many AutoVectorizations.
  Arena _arena;

  CloneMap&            _clone_map;       // map of nodes created in cloning

  PairSet _pairset;
  PackSet _packset;

  // VPointer, and the alignment width (aw) for which we align the main-loop,
  // by adjusting the pre-loop limit.
  VPointer const* _vpointer_for_main_loop_alignment;
  int _aw_for_main_loop_alignment;

 public:
  SuperWord(const VTransformAnalyzer& scalar_vtransform_analyzer);

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
  // TODO: rm?
  bool is_marked_reduction(const Node* n) const {
    return _vloop_analyzer.reductions().is_marked_reduction(n);
  }

  bool is_marked_reduction(const VTransformNode* n) const {
    // TODO: impl!
    return false;
  }

  // TODO: rm?
  bool reduction(const Node* n1, const Node* n2) const {
    return _vloop_analyzer.reductions().is_marked_reduction_pair(n1, n2);
  }

  bool reduction(const VTransformNode* n1, const VTransformNode* n2) const {
    // TODO: impl!
    return false;
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

  template<typename Callback>
  void for_each_mem(Callback callback) const {
    return _vloop_analyzer.body().for_each_mem(callback);
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

  int data_size(const Node* n) const {
    return _vloop_analyzer.types().data_size(n);
  }

  // VLoopDependencyGraph accessors
  const VLoopDependencyGraph& dependency_graph() const {
    return _vloop_analyzer.dependency_graph();
  }

  // TODO: rm?
  bool independent(Node* n1, Node* n2) const {
    return _vloop_analyzer.dependency_graph().independent(n1, n2);
  }

  bool independent(const VTransformNode* n1, const VTransformNode* n2) const {
    return _scalar_vtransform_analyzer.dependency().independent(n1, n2);
  }

  bool mutually_independent(const Pack* pack) const {
    return _scalar_vtransform_analyzer.dependency().mutually_independent(pack);
  }

  // VLoopVPointer accessors
  const VPointer& vpointer(const MemNode* mem) const {
    return _vloop_analyzer.vpointers().vpointer(mem);
  }

  bool     do_vector_loop()        { return _do_vector_loop; }

  const PackSet& packset() const { return _packset; }
  Pack* get_pack(const VTransformNode* n) const { return _packset.get_pack(n); }

 private:
  bool           _do_vector_loop;  // whether to do vectorization/simd style

  // Accessors
  Arena* arena()                   { return &_arena; }

  // CloneMap utilities
  bool same_origin_idx(Node* a, Node* b) const;
  bool same_generation(Node* a, Node* b) const;

private:
  bool SLP_extract();

  // Find the "seed" memops pairs. These are pairs that we strongly suspect would lead to vectorization.
  class MemOp : public StackObj {
  private:
    VTransformMemopScalarNode* _vtnode;
    int _original_index;

  public:
    // Empty, for GrowableArray
    MemOp() :
      _vtnode(nullptr),
      _original_index(-1) {}
    MemOp(VTransformMemopScalarNode* vtnode, int original_index) :
      _vtnode(vtnode),
      _original_index(original_index) {}

    VTransformMemopScalarNode* vtnode() const { return _vtnode; }
    int original_index() const { return _original_index; }

    static int cmp_by_group(MemOp* a, MemOp* b);
    static int cmp_by_group_and_con_and_original_index(MemOp* a, MemOp* b);

    // We use two comparisons, because a subtraction could underflow.
    template <typename T>
    static int cmp_code(T a, T b) {
      if (a < b) { return -1; }
      if (a > b) { return  1; }
      return 0;
    }
  };
  void create_adjacent_memop_pairs();
  void collect_valid_memops(GrowableArray<MemOp>& memops) const;
  void create_adjacent_memop_pairs_in_all_groups(const GrowableArray<MemOp>& memops);
  static int find_group_end(const GrowableArray<MemOp>& memops, int group_start);
  void create_adjacent_memop_pairs_in_one_group(const GrowableArray<MemOp>& memops, const int group_start, int group_end);

  // Various methods to check if we can pack two nodes.
  bool can_pack_into_pair(Node* s1, Node* s2); // TODO: rm
  bool can_pack_into_pair(const VTransformNode* s1, const VTransformNode* s2) const;
  // Is s1 immediately before s2 in memory?
  bool are_adjacent_refs(Node* s1, Node* s2) const; // TODO: rm
  bool are_adjacent_refs(const VTransformNode* s1, const VTransformNode* s2) const;
  // Are s1 and s2 similar?
  bool isomorphic(Node* s1, Node* s2); // TODO: rm
  //bool isomorphic(const VTransformNode* s1, const VTransformNode* s2) const;
  // Do we have pattern n1 = (iv + c) and n2 = (iv + c + 1)?
  bool is_populate_index(const Node* n1, const Node* n2) const; // TODO: rm
  bool is_populate_index(const VTransformNode* n1, const VTransformNode* n2) const;
  // For a node pair (s1, s2) which is isomorphic and independent,
  // do s1 and s2 have similar input edges?
  bool have_similar_inputs(Node* s1, Node* s2); // TODO: rm
  bool have_similar_inputs(const VTransformNode* s1, const VTransformNode* s2) const;

  void extend_pairset_with_more_pairs_by_following_use_and_def();
  bool extend_pairset_with_more_pairs_by_following_def(const VTransformNode* s1, const VTransformNode* s2);
  bool extend_pairset_with_more_pairs_by_following_use(const VTransformNode* s1, const VTransformNode* s2);
  void order_inputs_of_all_use_pairs_to_match_def_pair(const VTransformNode* def1, const VTransformNode* def2);
  enum PairOrderStatus { Ordered, Unordered, Unknown };
  PairOrderStatus order_inputs_of_uses_to_match_def_pair(const VTransformNode* def1, const VTransformNode* def2, const VTransformNode* use1, const VTransformNode* use2);
  int estimate_cost_savings_when_packing_as_pair(const VTransformNode* s1, const VTransformNode* s2) const;

  void combine_pairs_to_longer_packs();

  void split_packs_at_use_def_boundaries();
  void split_packs_only_implemented_with_smaller_size();
  void split_packs_to_break_mutual_dependence();

  void filter_packs_for_power_of_2_size();
  void filter_packs_for_mutual_independence();
  void filter_packs_for_alignment();
  const AlignmentSolution* pack_alignment_solution(const Pack* pack);
  void filter_packs_for_implemented();
  void filter_packs_for_profitable();

  DEBUG_ONLY(void verify_packs() const;)

  // Can code be generated for the pack, restricted to size nodes?
  bool implemented(const Pack* pack, const int size) const;
  // Find the maximal implemented size smaller or equal to the packs size
  uint max_implemented_size(const Pack* pack);

  // For pack p, are all operands and all uses (with in the block) vector?
  bool profitable(const Pack* p) const;

  // Verify that all uses of packs are also packs, i.e. we do not need extract operations.
  DEBUG_ONLY(void verify_no_extract();)

  // Check if n_super's pack uses are a superset of n_sub's pack uses.
  bool has_use_pack_superset(const VTransformNode* n1, const VTransformNode* n2) const;
  // Find a boundary in the pack, where left and right have different pack uses and defs.
  uint find_use_def_boundary(const Pack* pack) const;

  // Is use->in(u_idx) a vector use?
  bool is_vector_use(const VTransformNode* use, int u_idx) const;

  bool is_velt_basic_type_compatible_use_def(Node* use, Node* def, const uint pack_size) const;

  bool do_vtransform() const;
};

#endif // SHARE_OPTO_SUPERWORD_HPP
