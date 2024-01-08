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
class OrderedPair;

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

  void init() { _map.clear(); } // initialize

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
  const Type* _velt_type; // vector element type
  Node_List*  _my_pack;   // pack containing this node

  SWNodeInfo() : _alignment(-1), _depth(0), _velt_type(nullptr), _my_pack(nullptr) {}
  static const SWNodeInfo initial;
};

class SuperWord;

// JVMCI: OrderedPair is moved up to deal with compilation issues on Windows
//------------------------------OrderedPair---------------------------
// Ordered pair of Node*.
class OrderedPair {
 protected:
  Node* _p1;
  Node* _p2;
 public:
  OrderedPair() : _p1(nullptr), _p2(nullptr) {}
  OrderedPair(Node* p1, Node* p2) {
    if (p1->_idx < p2->_idx) {
      _p1 = p1; _p2 = p2;
    } else {
      _p1 = p2; _p2 = p1;
    }
  }

  bool operator==(const OrderedPair &rhs) {
    return _p1 == rhs._p1 && _p2 == rhs._p2;
  }
  void print() { tty->print("  (%d, %d)", _p1->_idx, _p2->_idx); }

  static const OrderedPair initial;
};

// -----------------------------SuperWord---------------------------------
// Transforms scalar operations into packed (superword) operations.
class SuperWord : public ResourceObj {
 friend class VPointer;
 friend class CMoveKit;
 private:
  PhaseIdealLoop* _phase;
  Arena*          _arena;
  PhaseIterGVN   &_igvn;

  enum consts { top_align = -1, bottom_align = -666 };

  GrowableArray<Node_List*> _packset;    // Packs for the current block

  GrowableArray<int> _bb_idx;            // Map from Node _idx to index within block

  GrowableArray<Node*> _block;           // Nodes in current block
  GrowableArray<Node*> _data_entry;      // Nodes with all inputs from outside
  GrowableArray<Node*> _mem_slice_head;  // Memory slice head nodes
  GrowableArray<Node*> _mem_slice_tail;  // Memory slice tail nodes
  GrowableArray<SWNodeInfo> _node_info;  // Info needed per node
  CloneMap&            _clone_map;       // map of nodes created in cloning
  MemNode const* _align_to_ref;          // Memory reference that pre-loop will align to

  GrowableArray<OrderedPair> _disjoint_ptrs; // runtime disambiguated pointer pairs

  DepGraph _dg; // Dependence graph

  // Scratch pads
  VectorSet    _visited;       // Visited set
  VectorSet    _post_visited;  // Post-visited set
  Node_Stack   _n_idx_list;    // List of (node,index) pairs
  GrowableArray<Node*> _nlist; // List of nodes
  GrowableArray<Node*> _stk;   // Stack of nodes

 public:
  SuperWord(PhaseIdealLoop* phase);

  bool transform_loop(IdealLoopTree* lpt, bool do_optimization);

  void unrolling_analysis(int &local_loop_unroll_factor);

  // Accessors for VPointer
  PhaseIdealLoop* phase() const    { return _phase; }
  IdealLoopTree* lpt() const       { return _lpt; }
  PhiNode* iv() const              { return _iv; }

  bool early_return() const        { return _early_return; }

#ifndef PRODUCT
  bool     is_debug()              { return _vector_loop_debug > 0; }
  bool     is_trace_alignment()    { return (_vector_loop_debug & 2) > 0; }
  bool     is_trace_mem_slice()    { return (_vector_loop_debug & 4) > 0; }
  bool     is_trace_loop()         { return (_vector_loop_debug & 8) > 0; }
  bool     is_trace_adjacent()     { return (_vector_loop_debug & 16) > 0; }
  bool     is_trace_cmov()         { return (_vector_loop_debug & 32) > 0; }
  bool     is_trace_align_vector() { return (_vector_loop_debug & 128) > 0; }
#endif
  bool     do_vector_loop()        { return _do_vector_loop; }

  const GrowableArray<Node_List*>& packset() const { return _packset; }
  const GrowableArray<Node*>&      block()   const { return _block; }
  const DepGraph&                  dg()      const { return _dg; }
 private:
  IdealLoopTree* _lpt;             // Current loop tree node
  CountedLoopNode* _lp;            // Current CountedLoopNode
  VectorSet      _loop_reductions; // Reduction nodes in the current loop
  Node*          _bb;              // Current basic block
  PhiNode*       _iv;              // Induction var
  bool           _race_possible;   // In cases where SDMU is true
  bool           _early_return;    // True if we do not initialize
  bool           _do_vector_loop;  // whether to do vectorization/simd style
  int            _num_work_vecs;   // Number of non memory vector operations
  int            _num_reductions;  // Number of reduction expressions applied
#ifndef PRODUCT
  uintx          _vector_loop_debug; // provide more printing in debug mode
#endif

  // Accessors
  Arena* arena()                   { return _arena; }

  Node* bb()                       { return _bb; }
  void set_bb(Node* bb)            { _bb = bb; }
  void set_lpt(IdealLoopTree* lpt) { _lpt = lpt; }
  CountedLoopNode* lp() const      { return _lp; }
  void set_lp(CountedLoopNode* lp) {
    _lp = lp;
    _iv = lp->as_CountedLoop()->phi()->as_Phi();
  }
  int iv_stride() const            { return lp()->stride_con(); }

  int vector_width(const Node* n) const {
    BasicType bt = velt_basic_type(n);
    return MIN2(ABS(iv_stride()), Matcher::max_vector_size(bt));
  }
  int vector_width_in_bytes(const Node* n) const {
    BasicType bt = velt_basic_type(n);
    return vector_width(n)*type2aelembytes(bt);
  }
  int get_vw_bytes_special(MemNode* s);
  const MemNode* align_to_ref() const { return _align_to_ref; }
  void set_align_to_ref(const MemNode* m) { _align_to_ref = m; }

  const Node* ctrl(const Node* n) const { return _phase->has_ctrl(n) ? _phase->get_ctrl(n) : n; }

  // block accessors
 public:
  bool in_bb(const Node* n) const  { return n != nullptr && n->outcnt() > 0 && ctrl(n) == _bb; }
  int  bb_idx(const Node* n) const { assert(in_bb(n), "must be"); return _bb_idx.at(n->_idx); }
 private:
  void set_bb_idx(Node* n, int i)  { _bb_idx.at_put_grow(n->_idx, i); }

  // visited set accessors
  void visited_clear()           { _visited.clear(); }
  void visited_set(Node* n)      { return _visited.set(bb_idx(n)); }
  int visited_test(Node* n)      { return _visited.test(bb_idx(n)); }
  int visited_test_set(Node* n)  { return _visited.test_set(bb_idx(n)); }
  void post_visited_clear()      { _post_visited.clear(); }
  void post_visited_set(Node* n) { return _post_visited.set(bb_idx(n)); }
  int post_visited_test(Node* n) { return _post_visited.test(bb_idx(n)); }

  // Ensure node_info contains element "i"
  void grow_node_info(int i) { if (i >= _node_info.length()) _node_info.at_put_grow(i, SWNodeInfo::initial); }

  // should we align vector memory references on this platform?
  bool vectors_should_be_aligned() { return !Matcher::misaligned_vectors_ok() || AlignVector; }

  // memory alignment for a node
  int alignment(Node* n)                     { return _node_info.adr_at(bb_idx(n))->_alignment; }
  void set_alignment(Node* n, int a)         { int i = bb_idx(n); grow_node_info(i); _node_info.adr_at(i)->_alignment = a; }

  // Max expression (DAG) depth from beginning of the block for each node
  int depth(Node* n)                         { return _node_info.adr_at(bb_idx(n))->_depth; }
  void set_depth(Node* n, int d)             { int i = bb_idx(n); grow_node_info(i); _node_info.adr_at(i)->_depth = d; }

  // vector element type
  const Type* velt_type(const Node* n) const { return _node_info.adr_at(bb_idx(n))->_velt_type; }
  BasicType velt_basic_type(const Node* n) const { return velt_type(n)->array_element_basic_type(); }
  void set_velt_type(Node* n, const Type* t) { int i = bb_idx(n); grow_node_info(i); _node_info.adr_at(i)->_velt_type = t; }
  bool same_velt_type(Node* n1, Node* n2);
  bool same_memory_slice(MemNode* best_align_to_mem_ref, MemNode* mem_ref) const;

  // my_pack
 public:
  Node_List* my_pack(Node* n)                 { return !in_bb(n) ? nullptr : _node_info.adr_at(bb_idx(n))->_my_pack; }
 private:
  void set_my_pack(Node* n, Node_List* p)     { int i = bb_idx(n); grow_node_info(i); _node_info.adr_at(i)->_my_pack = p; }
  // is pack good for converting into one vector node replacing bunches of Cmp, Bool, CMov nodes.
  static bool requires_long_to_int_conversion(int opc);
  // For pack p, are all idx operands the same?
  bool same_inputs(Node_List* p, int idx);
  // CloneMap utilities
  bool same_origin_idx(Node* a, Node* b) const;
  bool same_generation(Node* a, Node* b) const;

  // methods

  typedef const Pair<const Node*, int> PathEnd;

  // Search for a path P = (n_1, n_2, ..., n_k) such that:
  // - original_input(n_i, input) = n_i+1 for all 1 <= i < k,
  // - path(n) for all n in P,
  // - k <= max, and
  // - there exists a node e such that original_input(n_k, input) = e and end(e).
  // Return <e, k>, if P is found, or <nullptr, -1> otherwise.
  // Note that original_input(n, i) has the same behavior as n->in(i) except
  // that it commutes the inputs of binary nodes whose edges have been swapped.
  template <typename NodePredicate1, typename NodePredicate2>
  static PathEnd find_in_path(const Node *n1, uint input, int max,
                              NodePredicate1 path, NodePredicate2 end) {
    const PathEnd no_path(nullptr, -1);
    const Node* current = n1;
    int k = 0;
    for (int i = 0; i <= max; i++) {
      if (current == nullptr) {
        return no_path;
      }
      if (end(current)) {
        return PathEnd(current, k);
      }
      if (!path(current)) {
        return no_path;
      }
      current = original_input(current, input);
      k++;
    }
    return no_path;
  }

public:
  // Whether n is a reduction operator and part of a reduction cycle.
  // This function can be used for individual queries outside the SLP analysis,
  // e.g. to inform matching in target-specific code. Otherwise, the
  // almost-equivalent but faster SuperWord::mark_reductions() is preferable.
  static bool is_reduction(const Node* n);
  // Whether n is marked as a reduction node.
  bool is_marked_reduction(Node* n) { return _loop_reductions.test(n->_idx); }
  // Whether the current loop has any reduction node.
  bool is_marked_reduction_loop() { return !_loop_reductions.is_empty(); }
private:
  // Whether n is a standard reduction operator.
  static bool is_reduction_operator(const Node* n);
  // Whether n is part of a reduction cycle via the 'input' edge index. To bound
  // the search, constrain the size of reduction cycles to LoopMaxUnroll.
  static bool in_reduction_cycle(const Node* n, uint input);
  // Reference to the i'th input node of n, commuting the inputs of binary nodes
  // whose edges have been swapped. Assumes n is a commutative operation.
  static Node* original_input(const Node* n, uint i);
  // Find and mark reductions in a loop. Running mark_reductions() is similar to
  // querying is_reduction(n) for every n in the SuperWord loop, but stricter in
  // that it assumes counted loops and requires that reduction nodes are not
  // used within the loop except by their reduction cycle predecessors.
  void mark_reductions();
  // Extract the superword level parallelism
  bool SLP_extract();
  // Find the adjacent memory references and create pack pairs for them.
  void find_adjacent_refs();
  // Find a memory reference to align the loop induction variable to.
  MemNode* find_align_to_ref(Node_List &memops, int &idx);
  // Calculate loop's iv adjustment for this memory ops.
  int get_iv_adjustment(MemNode* mem);
  // Construct dependency graph.
  void dependence_graph();
  // Return a memory slice (node list) in predecessor order starting at "start"
  void mem_slice_preds(Node* start, Node* stop, GrowableArray<Node*> &preds);
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
  // Is any s1 in p dependent on any s2 in p? Yes: return such a s2. No: return nullptr.
  Node* find_dependence(Node_List* p);
  // For a node pair (s1, s2) which is isomorphic and independent,
  // do s1 and s2 have similar input edges?
  bool have_similar_inputs(Node* s1, Node* s2);
  // Is there a data path between s1 and s2 and both are reductions?
  bool reduction(Node* s1, Node* s2);
  // Helper for independent
  bool independent_path(Node* shallow, Node* deep, uint dp=0);
  void set_alignment(Node* s1, Node* s2, int align);
  int data_size(Node* s);
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
  void combine_packs();
  // Ensure all packs are aligned, if AlignVector is on.
  void filter_packs_for_alignment();
  // Find the set of alignment solutions for load/store pack.
  const AlignmentSolution* pack_alignment_solution(Node_List* pack);
  // Compress packset, such that it has no nullptr entries.
  void compress_packset();
  // Construct the map from nodes to packs.
  void construct_my_pack_map();
  // Remove packs that are not implemented or not profitable.
  void filter_packs();
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
  bool implemented(Node_List* p);
  // For pack p, are all operands and all uses (with in the block) vector?
  bool profitable(Node_List* p);
  // If a use of pack p is not a vector use, then replace the use with an extract operation.
  void insert_extracts(Node_List* p);
  // Is use->in(u_idx) a vector use?
  bool is_vector_use(Node* use, int u_idx);
  // Construct reverse postorder list of block members
  bool construct_bb();
  // Initialize per node info
  void initialize_bb();
  // Insert n into block after pos
  void bb_insert_after(Node* n, int pos);
  // Compute max depth for expressions from beginning of block
  void compute_max_depth();
  // Return the longer type for vectorizable type-conversion node or illegal type for other nodes.
  BasicType longer_type_for_conversion(Node* n);
  // Find the longest type in def-use chain for packed nodes, and then compute the max vector size.
  int max_vector_size_in_def_use_chain(Node* n);
  // Compute necessary vector element type for expressions
  void compute_vector_element_type();
  // Are s1 and s2 in a pack pair and ordered as s1,s2?
  bool in_packset(Node* s1, Node* s2);
  // Remove the pack at position pos in the packset
  void remove_pack_at(int pos);
  static LoadNode::ControlDependency control_dependency(Node_List* p);
  // Alignment within a vector memory reference
  int memory_alignment(MemNode* s, int iv_adjust);
  // Smallest type containing range of values
  const Type* container_type(Node* n);
  // Ensure that the main loop vectors are aligned by adjusting the pre loop limit.
  void adjust_pre_loop_limit_to_align_main_loop_vectors();
  // Is the use of d1 in u1 at the same operand position as d2 in u2?
  bool opnd_positions_match(Node* d1, Node* u1, Node* d2, Node* u2);
  void init();

  // print methods
  void print_packset();
  void print_pack(Node_List* p);
  void print_bb();
  void print_stmt(Node* s);

  void packset_sort(int n);
};

#endif // SHARE_OPTO_SUPERWORD_HPP
