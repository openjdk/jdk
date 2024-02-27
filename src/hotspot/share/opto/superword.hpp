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

// ========================= Dependence Graph =====================

class DepMem;

//------------------------------DepEdge---------------------------
// An edge in the dependence graph.  The edges incident to a dependence
// node are threaded through _next_in for incoming edges and _next_out
// for outgoing edges.
class DepEdge : public ArenaObj {
 protected:
  DepMem* _pred;
  DepMem* _succ;
  DepEdge* _next_in;   // list of in edges, null terminated
  DepEdge* _next_out;  // list of out edges, null terminated

 public:
  DepEdge(DepMem* pred, DepMem* succ, DepEdge* next_in, DepEdge* next_out) :
    _pred(pred), _succ(succ), _next_in(next_in), _next_out(next_out) {}

  DepEdge* next_in()  { return _next_in; }
  DepEdge* next_out() { return _next_out; }
  DepMem*  pred()     { return _pred; }
  DepMem*  succ()     { return _succ; }

  void print();
};

//------------------------------DepMem---------------------------
// A node in the dependence graph.  _in_head starts the threaded list of
// incoming edges, and _out_head starts the list of outgoing edges.
class DepMem : public ArenaObj {
 protected:
  Node*    _node;     // Corresponding ideal node
  DepEdge* _in_head;  // Head of list of in edges, null terminated
  DepEdge* _out_head; // Head of list of out edges, null terminated

 public:
  DepMem(Node* node) : _node(node), _in_head(nullptr), _out_head(nullptr) {}

  Node*    node()                { return _node;     }
  DepEdge* in_head()             { return _in_head;  }
  DepEdge* out_head()            { return _out_head; }
  void set_in_head(DepEdge* hd)  { _in_head = hd;    }
  void set_out_head(DepEdge* hd) { _out_head = hd;   }

  int in_cnt();  // Incoming edge count
  int out_cnt(); // Outgoing edge count

  void print();
};

//------------------------------DepGraph---------------------------
class DepGraph {
 protected:
  Arena* _arena;
  GrowableArray<DepMem*> _map;
  DepMem* _root;
  DepMem* _tail;

 public:
  DepGraph(Arena* a) : _arena(a), _map(a, 8,  0, nullptr) {
    _root = new (_arena) DepMem(nullptr);
    _tail = new (_arena) DepMem(nullptr);
  }

  DepMem* root() { return _root; }
  DepMem* tail() { return _tail; }

  // Return dependence node corresponding to an ideal node
  DepMem* dep(Node* node) const { return _map.at(node->_idx); }

  // Make a new dependence graph node for an ideal node.
  DepMem* make_node(Node* node);

  // Make a new dependence graph edge dprec->dsucc
  DepEdge* make_edge(DepMem* dpred, DepMem* dsucc);

  DepEdge* make_edge(Node* pred,   Node* succ)   { return make_edge(dep(pred), dep(succ)); }
  DepEdge* make_edge(DepMem* pred, Node* succ)   { return make_edge(pred,      dep(succ)); }
  DepEdge* make_edge(Node* pred,   DepMem* succ) { return make_edge(dep(pred), succ);      }

  void print(Node* n)   { dep(n)->print(); }
  void print(DepMem* d) { d->print(); }
};

//------------------------------DepPreds---------------------------
// Iterator over predecessors in the dependence graph and
// non-memory-graph inputs of ideal nodes.
class DepPreds : public StackObj {
private:
  Node*    _n;
  int      _next_idx, _end_idx;
  DepEdge* _dep_next;
  Node*    _current;
  bool     _done;

public:
  DepPreds(Node* n, const DepGraph& dg);
  Node* current() { return _current; }
  bool  done()    { return _done; }
  void  next();
};

//------------------------------DepSuccs---------------------------
// Iterator over successors in the dependence graph and
// non-memory-graph outputs of ideal nodes.
class DepSuccs : public StackObj {
private:
  Node*    _n;
  int      _next_idx, _end_idx;
  DepEdge* _dep_next;
  Node*    _current;
  bool     _done;

public:
  DepSuccs(Node* n, DepGraph& dg);
  Node* current() { return _current; }
  bool  done()    { return _done; }
  void  next();
};


// ========================= SuperWord =====================

// -----------------------------SWNodeInfo---------------------------------
// Per node info needed by SuperWord
class SWNodeInfo {
 public:
  int         _alignment; // memory alignment for a node
  int         _depth;     // Max expression (DAG) depth from block start
  Node_List*  _my_pack;   // pack containing this node

  SWNodeInfo() : _alignment(-1), _depth(0), _my_pack(nullptr) {}
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

  DepGraph _dg; // Dependence graph

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

#ifndef PRODUCT
  // TraceAutoVectorization and TraceSuperWord
  bool is_trace_superword_alignment() const {
    // Too verbose for TraceSuperWord
    return _vloop.vtrace().is_trace(TraceAutoVectorizationTag::SW_ALIGNMENT);
  }

  bool is_trace_superword_dependence_graph() const {
    return TraceSuperWord ||
           _vloop.vtrace().is_trace(TraceAutoVectorizationTag::SW_DEPENDENCE_GRAPH);
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
           _vloop.vtrace().is_trace(TraceAutoVectorizationTag::SW_DEPENDENCE_GRAPH) ||
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
  const DepGraph&                  dg()      const { return _dg; }
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

  // Max expression (DAG) depth from beginning of the block for each node
  int depth(Node* n) const                   { return _node_info.adr_at(bb_idx(n))->_depth; }
  void set_depth(Node* n, int d)             { int i = bb_idx(n); grow_node_info(i); _node_info.adr_at(i)->_depth = d; }

  // my_pack
 public:
  Node_List* my_pack(Node* n)                 { return !in_bb(n) ? nullptr : _node_info.adr_at(bb_idx(n))->_my_pack; }
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
  // Construct dependency graph.
  void dependence_graph();

  // Can s1 and s2 be in a pack with s1 immediately preceding s2 and  s1 aligned at "align"
  bool stmts_can_pack(Node* s1, Node* s2, int align);
  // Does s exist in a pack at position pos?
  bool exists_at(Node* s, uint pos);
  // Is s1 immediately before s2 in memory?
  bool are_adjacent_refs(Node* s1, Node* s2);
  // Are s1 and s2 similar?
  bool isomorphic(Node* s1, Node* s2);
  // Is there no data path from s1 to s2 or s2 to s1?
  bool independent(Node* s1, Node* s2);
  // Are all nodes in nodes list mutually independent?
  bool mutually_independent(const Node_List* nodes) const;
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

  void split_packs_longer_than_max_vector_size();

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
  // Can code be generated for pack p?
  bool implemented(const Node_List* p);
  // For pack p, are all operands and all uses (with in the block) vector?
  bool profitable(const Node_List* p);
  // Verify that all uses of packs are also packs, i.e. we do not need extract operations.
  DEBUG_ONLY(void verify_no_extract();)
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
