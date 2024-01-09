/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_VECTORIZATION_HPP
#define SHARE_OPTO_VECTORIZATION_HPP

#include "utilities/pair.hpp"
#include "opto/node.hpp"
#include "opto/loopnode.hpp"
#include "opto/traceautovectorizationtags.hpp"

// Code in this file and the vectorization.cpp contains shared logics and
// utilities for C2's loop auto-vectorization.

// Base class, used to check basic structure in preparation for auto-vectorization.
// The subclass VLoopAnalyzer is used to analyze the loop and feed that information
// to the auto-vectorization.
class VLoop : public StackObj {
protected:
  PhaseIdealLoop* _phase = nullptr;
  Arena* _arena = nullptr;
  IdealLoopTree* _lpt = nullptr;
  CountedLoopNode* _cl = nullptr;
  Node* _cl_exit = nullptr;
  PhiNode* _iv = nullptr;
  bool _allow_cfg = false;
  CountedLoopEndNode* _pre_loop_end; // only for main loops

  const CHeapBitMap &_trace_tags;

  static constexpr char const* SUCCESS                    = "success";
  static constexpr char const* FAILURE_ALREADY_VECTORIZED = "loop already vectorized";
  static constexpr char const* FAILURE_UNROLL_ONLY        = "loop only wants to be unrolled";
  static constexpr char const* FAILURE_VECTOR_WIDTH       = "vector_width must be power of 2";
  static constexpr char const* FAILURE_VALID_COUNTED_LOOP = "must be valid counted loop (int)";
  static constexpr char const* FAILURE_CONTROL_FLOW       = "control flow in loop not allowed";
  static constexpr char const* FAILURE_BACKEDGE           = "nodes on backedge not allowed";
  static constexpr char const* FAILURE_PRE_LOOP_LIMIT     = "main-loop must be able to adjust pre-loop-limit (not found)";

public:
  VLoop(PhaseIdealLoop* phase) :
    _phase(phase),
    _arena(phase->C->comp_arena()),
    _trace_tags(phase->C->directive()->traceautovectorization_tags()) {}
  NONCOPYABLE(VLoop);

protected:
  virtual void reset(IdealLoopTree* lpt, bool allow_cfg) {
    assert(_phase == lpt->_phase, "must be the same phase");
    _lpt       = lpt;
    _cl        = nullptr;
    _cl_exit   = nullptr;
    _iv        = nullptr;
    _allow_cfg = allow_cfg;
  }

public:
  Arena* arena()          const { return _arena; }
  IdealLoopTree* lpt()    const { assert(_lpt     != nullptr, ""); return _lpt; };
  PhaseIdealLoop* phase() const { assert(_phase   != nullptr, ""); return _phase; }
  CountedLoopNode* cl()   const { assert(_cl      != nullptr, ""); return _cl; };
  Node* cl_exit()         const { assert(_cl_exit != nullptr, ""); return _cl_exit; };
  PhiNode* iv()           const { assert(_iv      != nullptr, ""); return _iv; };
  int iv_stride()         const { return cl()->stride_con(); };
  bool is_allow_cfg()     const { return _allow_cfg; }
  CountedLoopEndNode* pre_loop_end() const {
    assert(cl()->is_main_loop(), "only main loop can reference pre-loop");
    assert(_pre_loop_end != nullptr, "must have found it");
    return _pre_loop_end;
  };
  CountedLoopNode* pre_loop_head() const {
    CountedLoopNode* head = pre_loop_end()->loopnode();
    assert(head != nullptr, "must find head");
    return head;
  };

  bool in_body(const Node* n) const {
    // We only accept any nodes which have the loop head as their ctrl.
    const Node* ctrl = _phase->has_ctrl(n) ? _phase->get_ctrl(n) : n;
    return n != nullptr && n->outcnt() > 0 && ctrl == _cl;
  }

  // Do we have to enforce strict alignment criteria on this platform?
  static bool vectors_must_be_aligned() {
   return !Matcher::misaligned_vectors_ok() || AlignVector;
  }

#ifndef PRODUCT
  bool is_trace_precondition() const {
    return _trace_tags.at(TraceAutovectorizationTag::TAG_PRECONDITION);
  }
  bool is_trace_loop_analyzer() const {
    return _trace_tags.at(TraceAutovectorizationTag::TAG_LOOP_ANALYZER);
  }
  bool is_trace_memory_slices() const {
    return _trace_tags.at(TraceAutovectorizationTag::TAG_MEMORY_SLICES);
  }
  bool is_trace_body() const {
    return _trace_tags.at(TraceAutovectorizationTag::TAG_BODY);
  }
  bool is_trace_dependence_graph() const {
    return _trace_tags.at(TraceAutovectorizationTag::TAG_DEPENDENCE_GRAPH);
  }
  bool is_trace_vector_element_type() const {
    return _trace_tags.at(TraceAutovectorizationTag::TAG_TYPES);
  }
  bool is_trace_pointer_analysis() const {
    return _trace_tags.at(TraceAutovectorizationTag::TAG_POINTER_ANALYSIS);
  }
  bool is_trace_superword_adjacent_memops() const {
    return TraceSuperWord ||
           _trace_tags.at(TraceAutovectorizationTag::TAG_SW_ADJACENT_MEMOPS);
  }
  bool is_trace_superword_alignment() const {
    return _trace_tags.at(TraceAutovectorizationTag::TAG_SW_ALIGNMENT);
  }
  bool is_trace_superword_rejections() const {
    return TraceSuperWord ||
           _trace_tags.at(TraceAutovectorizationTag::TAG_SW_REJECTIONS);
  }
  bool is_trace_superword_packset() const {
    return TraceSuperWord ||
           _trace_tags.at(TraceAutovectorizationTag::TAG_SW_PACKSET);
  }
  bool is_trace_superword_all() const {
    return TraceSuperWord ||
           _trace_tags.at(TraceAutovectorizationTag::TAG_SW_ALL);
  }
  bool is_trace_superword_info() const {
    return TraceSuperWord ||
           _trace_tags.at(TraceAutovectorizationTag::TAG_SW_INFO);
  }
  bool is_trace_superword_any() const {
    return TraceSuperWord ||
           _trace_tags.at(TraceAutovectorizationTag::TAG_SW_INFO) ||
           _trace_tags.at(TraceAutovectorizationTag::TAG_SW_ALL) ||
           _trace_tags.at(TraceAutovectorizationTag::TAG_SW_ADJACENT_MEMOPS) ||
           _trace_tags.at(TraceAutovectorizationTag::TAG_SW_ALIGNMENT) ||
           _trace_tags.at(TraceAutovectorizationTag::TAG_SW_REJECTIONS) ||
           _trace_tags.at(TraceAutovectorizationTag::TAG_SW_PACKSET);
  }
  bool is_trace_align_vector() const {
    return _trace_tags.at(TraceAutovectorizationTag::TAG_ALIGN_VECTOR);
  }
#endif

  // Check if the loop passes some basic preconditions for vectorization.
  // Overwrite previous data. Return indicates if analysis succeeded.
  bool check_preconditions(IdealLoopTree* lpt, bool allow_cfg);

protected:
  const char* check_preconditions_helper();
};

// Submodule of VLoopAnalyzer.
// Identify and mark all reductions in the loop.
class VLoopReductions : public StackObj {
private:
  typedef const Pair<const Node*, int> PathEnd;

  const VLoop& _vloop;
  VectorSet _loop_reductions;

public:
  VLoopReductions(const VLoop& vloop) :
    _vloop(vloop),
    _loop_reductions(_vloop.arena()){};
  NONCOPYABLE(VLoopReductions);
  void reset() {
    _loop_reductions.clear();
  }

private:
  // Search for a path P = (n_1, n_2, ..., n_k) such that:
  // - original_input(n_i, input) = n_i+1 for all 1 <= i < k,
  // - path(n) for all n in P,
  // - k <= max, and
  // - there exists a node e such that original_input(n_k, input) = e and end(e).
  // Return <e, k>, if P is found, or <nullptr, -1> otherwise.
  // Note that original_input(n, i) has the same behavior as n->in(i) except
  // that it commutes the inputs of binary nodes whose edges have been swapped.
  template <typename NodePredicate1, typename NodePredicate2>
  static PathEnd find_in_path(const Node* n1, uint input, int max,
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
  // This function can be used for individual queries outside auto-vectorization,
  // e.g. to inform matching in target-specific code. Otherwise, the
  // almost-equivalent but faster mark_reductions() is preferable.
  static bool is_reduction(const Node* n);
  // Whether n is marked as a reduction node.
  bool is_marked_reduction(const Node* n) const { return _loop_reductions.test(n->_idx); }
  bool is_marked_reduction_loop() const { return !_loop_reductions.is_empty(); }
  // Are s1 and s2 reductions with a data path between them?
  bool is_marked_reduction_pair(Node* s1, Node* s2) const;
private:
  // Whether n is a standard reduction operator.
  static bool is_reduction_operator(const Node* n);
  // Whether n is part of a reduction cycle via the 'input' edge index. To bound
  // the search, constrain the size of reduction cycles to LoopMaxUnroll.
  static bool in_reduction_cycle(const Node* n, uint input);
  // Reference to the i'th input node of n, commuting the inputs of binary nodes
  // whose edges have been swapped. Assumes n is a commutative operation.
  static Node* original_input(const Node* n, uint i);
public:
  // Find and mark reductions in a loop. Running mark_reductions() is similar to
  // querying is_reduction(n) for every node in the loop, but stricter in
  // that it assumes counted loops and requires that reduction nodes are not
  // used within the loop except by their reduction cycle predecessors.
  void mark_reductions();
};

// Submodule of VLoopAnalyzer.
// Find the memory slices in the loop.
class VLoopMemorySlices : public StackObj {
private:
  const VLoop& _vloop;

  GrowableArray<PhiNode*> _heads;
  GrowableArray<MemNode*> _tails;

public:
  VLoopMemorySlices(const VLoop& vloop) :
    _vloop(vloop),
    _heads(_vloop.arena(), 8,  0, nullptr),
    _tails(_vloop.arena(), 8,  0, nullptr) {};

  NONCOPYABLE(VLoopMemorySlices);

  void reset() {
    _heads.clear();
    _tails.clear();
  }

  void analyze();

  const GrowableArray<PhiNode*> &heads() const { return _heads; }
  const GrowableArray<MemNode*> &tails() const { return _tails; }

  // Get all memory nodes of a slice, in reverse order
  void get_slice(Node* head, Node* tail, GrowableArray<Node*> &slice) const;

  bool same_memory_slice(MemNode* n1, MemNode* n2) const;

#ifndef PRODUCT
  void print() const;
#endif
};

// Submodule of VLoopAnalyzer.
// Find all nodes in the body, and create a mapping node->_idx to a body_idx.
// This mapping is used so that subsequent datastructures sizes only grow with
// the body size, and not the number of all nodes in the compilation.
class VLoopBody : public StackObj {
private:
  const VLoop& _vloop;

  GrowableArray<Node*> _body;
  GrowableArray<int> _body_idx;

  static constexpr char const* FAILURE_NODE_NOT_ALLOWED  = "encontered unhandled node";

public:
  VLoopBody(const VLoop& vloop) :
    _vloop(vloop),
    _body(_vloop.arena(), 8, 0, nullptr),
    _body_idx(_vloop.arena(), (int)(1.10 * _vloop.phase()->C->unique()), 0, 0) {}

  NONCOPYABLE(VLoopBody);

  void reset() {
    _body.clear();
    _body_idx.clear();
  }

  const char* construct();

#ifndef PRODUCT
  void print() const;
#endif

  int body_idx(const Node* n) const {
    assert(_vloop.in_body(n), "must be in loop_body");
    return _body_idx.at(n->_idx);
  }

  const GrowableArray<Node*>& body() const { return _body; }

private:
  void set_body_idx(Node* n, int i) {
    assert(_vloop.in_body(n), "must be in loop_body");
    _body_idx.at_put_grow(n->_idx, i);
  }
};

// Submodule of VLoopAnalyzer.
// We construct a dependence graph for the loop body, based on:
// 1) data dependencies:
//    The edges of the C2 IR nodes that represent data inputs.
// 2) memory dependencies:
//    We must respect Store->Store, Store->Load, and Load->Store order.
//    We do not have to respect the order if:
//    2.1) two memory operations are in different memory slices or
//    2.2) we can prove that the memory regions will never overlap.
//
// The graph can be queried in the following ways:
// 1) PredsIterator:
//    Given some node in the body, iterate over all its predecessors
//    in the dependence graph.
// 2) independent(s1, s2):
//    Check if there is a path s1->s2 or s2->s1. If not, then s1 and s2
//    can be executed in parallel (e.g. in a vector operation).
// 3) mutually_independent:
//    Check if all nodes in a list are mutually independent. If so, then
//    they can be executed in parallel (e.g. in a vector operation).
class VLoopDependenceGraph : public StackObj {
private:
  class DependenceEdge;
  class DependenceNode;

  const VLoop& _vloop;
  const VLoopMemorySlices& _memory_slices;
  const VLoopBody& _body;

  // node->_idx -> DependenceNode* (or nullptr)
  GrowableArray<DependenceNode*> _map;
  DependenceNode* _root;
  DependenceNode* _sink;
  GrowableArray<int> _depth; // body_idx -> depth in graph (DAG)

public:
  VLoopDependenceGraph(const VLoop& vloop,
                       const VLoopMemorySlices& memory_slices,
                       const VLoopBody& body) :
    _vloop(vloop),
    _memory_slices(memory_slices),
    _body(body),
    _map(vloop.arena(), 8,  0, nullptr),
    _root(nullptr),
    _sink(nullptr),
    _depth(vloop.arena(), 8,  0, 0) {}

  NONCOPYABLE(VLoopDependenceGraph);

  void reset() {
    _map.clear();
    _root = new (_vloop.arena()) DependenceNode(nullptr);
    _sink = new (_vloop.arena()) DependenceNode(nullptr);
    _depth.clear();
  }

  void build();

#ifndef PRODUCT
  void print() const;
#endif

private:
  DependenceNode* root() const { return _root; }
  DependenceNode* sink() const { return _sink; }

  // Return dependence node corresponding to an ideal node
  DependenceNode* get_node(Node* node) const {
    assert(node != nullptr, "must not be nullptr");
    DependenceNode* d = _map.at(node->_idx);
    assert(d != nullptr, "must find dependence node");
    return d;
  }

  // Make a new dependence graph node for an ideal node.
  DependenceNode* make_node(Node* node);

  // Make a new dependence graph edge dprec->dsucc
  DependenceEdge* make_edge(DependenceNode* dpred, DependenceNode* dsucc);

  // An edge in the dependence graph.  The edges incident to a dependence
  // node are threaded through _next_in for incoming edges and _next_out
  // for outgoing edges.
  class DependenceEdge : public ArenaObj {
  protected:
    DependenceNode* _pred;
    DependenceNode* _succ;
    DependenceEdge* _next_in;  // list of in edges, null terminated
    DependenceEdge* _next_out; // list of out edges, null terminated

  public:
    DependenceEdge(DependenceNode* pred,
                   DependenceNode* succ,
                   DependenceEdge* next_in,
                   DependenceEdge* next_out) :
      _pred(pred), _succ(succ), _next_in(next_in), _next_out(next_out) {}

    DependenceEdge* next_in()  { return _next_in; }
    DependenceEdge* next_out() { return _next_out; }
    DependenceNode* pred()     { return _pred; }
    DependenceNode* succ()     { return _succ; }
  };

  // A node in the dependence graph.  _in_head starts the threaded list of
  // incoming edges, and _out_head starts the list of outgoing edges.
  class DependenceNode : public ArenaObj {
  protected:
    Node*           _node;     // Corresponding ideal node
    DependenceEdge* _in_head;  // Head of list of in edges, null terminated
    DependenceEdge* _out_head; // Head of list of out edges, null terminated

  public:
    DependenceNode(Node* node) :
      _node(node),
      _in_head(nullptr),
      _out_head(nullptr)
    {
      assert(node == nullptr ||
             node->is_Mem() ||
             node->is_memory_phi(),
             "only memory graph nodes expected");
    }

    Node*           node()                { return _node;     }
    DependenceEdge* in_head()             { return _in_head;  }
    DependenceEdge* out_head()            { return _out_head; }
    void set_in_head(DependenceEdge* hd)  { _in_head = hd;    }
    void set_out_head(DependenceEdge* hd) { _out_head = hd;   }

    int in_cnt();  // Incoming edge count
    int out_cnt(); // Outgoing edge count

    void print() const;
  };

public:
  // Given some node in the body, iterate over all its predecessors
  // in the dependence graph.
  class PredsIterator {
  private:
    Node*           _n;
    int             _next_idx;
    int             _end_idx;
    DependenceEdge* _dep_next;
    Node*           _current;
    bool            _done;

  public:
    PredsIterator(Node* n, const VLoopDependenceGraph &dg);
    NONCOPYABLE(PredsIterator);

    Node* current() { return _current; }
    bool  done()    { return _done; }
    void  next();
  };

  // Are s1 and s2 independent? i.e. no path from s1 to s2 / s2 to s1?
  bool independent(Node* s1, Node* s2) const;
  // Are all nodes in nodes mutually independent?
  bool mutually_independent(Node_List* nodes) const;

private:
  // Depth in graph (DAG). Used to prune search paths.
  int depth(Node* n) const {
    assert(_vloop.in_body(n), "only call on nodes in loop");
    return _depth.at(_body.body_idx(n));
  }

  void set_depth(Node* n, int d) {
    assert(_vloop.in_body(n), "only call on nodes in loop");
    _depth.at_put(_body.body_idx(n), d);
  }

  void compute_max_depth();
};

// Submodule of VLoopAnalyzer.
// Compute the vector element type for every node in the loop body.
// We need to do this to be able to vectorize the narrower integer
// types (byte, char, short). In the C2 IR, their operations are
// done with full int type with 4 byte precision (e.g. AddI, MulI).
// Example:  char a,b,c;  a = (char)(b + c);
// However, if we can prove the the upper bits are only truncated,
// and the lower bits for the narrower type computed correctly, we
// can compute the operations in the narrower type directly (e.g we
// perform the AddI or MulI with 1 or 2 bytes). This allows us to
// fit more operations in a vector, and can remove the otherwise
// required conversion (int <-> narrower type).
// We compute the types backwards (use-to-def): If all use nodes
// only require the lower bits, then the def node can do the operation
// only with the lower bits, and we propagate the narrower type to it.
class VLoopTypes : public StackObj {
private:
  const VLoop& _vloop;
  const VLoopBody& _body;

  // body_idx -> vector element type
  GrowableArray<const Type*> _velt_type;

public:
  VLoopTypes(const VLoop& vloop,
             const VLoopBody& body) :
    _vloop(vloop),
    _body(body),
    _velt_type(vloop.arena(), 8,  0, nullptr) {}

  NONCOPYABLE(VLoopTypes);

  void reset() {
    _velt_type.clear();
  }

  void compute_vector_element_type();

#ifndef PRODUCT
  void print() const;
#endif

  const Type* velt_type(const Node* n) const {
    assert(_vloop.in_body(n), "only call on nodes in loop");
    const Type* t = _velt_type.at(_body.body_idx(n));
    assert(t != nullptr, "must have type");
    return t;
  }

  BasicType velt_basic_type(const Node* n) const {
    return velt_type(n)->array_element_basic_type();
  }

  int data_size(Node* s) const {
    int bsize = type2aelembytes(velt_basic_type(s));
    assert(bsize != 0, "valid size");
    return bsize;
  }

  bool same_velt_type(Node* n1, Node* n2) const {
    const Type* vt1 = velt_type(n1);
    const Type* vt2 = velt_type(n2);
    if (vt1->basic_type() == T_INT && vt2->basic_type() == T_INT) {
      // Compare vectors element sizes for integer types.
      return data_size(n1) == data_size(n2);
    }
    return vt1 == vt2;
  }

  int vector_width(const Node* n) const {
    BasicType bt = velt_basic_type(n);
    return MIN2(ABS(_vloop.iv_stride()), Matcher::max_vector_size(bt));
  }

  int vector_width_in_bytes(const Node* n) const {
    BasicType bt = velt_basic_type(n);
    return vector_width(n) * type2aelembytes(bt);
  }

private:
  void set_velt_type(Node* n, const Type* t) {
    assert(t != nullptr, "cannot set nullptr");
    assert(_vloop.in_body(n), "only call on nodes in loop");
    _velt_type.at_put(_body.body_idx(n), t);
  }

  const Type* container_type(Node* n) const;
};

// Analyze the loop in preparation for auto-vectorization. This class is
// deliberately structured into many submodules, which are as independent
// as possible, though some submodules do require other submodules.
class VLoopAnalyzer : public VLoop {
protected:
  static constexpr char const* FAILURE_NO_MAX_UNROLL = "slp max unroll analysis required";
  static constexpr char const* FAILURE_NO_REDUCTION_OR_STORE = "no reduction and no store in loop";

  // Submodules that analyze different aspects of the loop
  VLoopReductions      _reductions;
  VLoopMemorySlices    _memory_slices;
  VLoopBody            _body;
  VLoopTypes           _types;
  VLoopDependenceGraph _dependence_graph;

public:
  VLoopAnalyzer(PhaseIdealLoop* phase) :
    VLoop(phase),
    _reductions(*this),
    _memory_slices(*this),
    _body(*this),
    _types(*this, _body), // types requires: body
    _dependence_graph(*this, _memory_slices, _body) // dependence_graph requires: memory_slices and body
  {
  };
  NONCOPYABLE(VLoopAnalyzer);

  // Analyze the loop in preparation for vectorization.
  // Overwrite previous data. Return indicates if analysis succeeded.
  bool analyze(IdealLoopTree* lpt,
               bool allow_cfg);

  // Read-only accessors for submodules
  const VLoopReductions& reductions() const            { return _reductions; }
  const VLoopMemorySlices& memory_slices() const       { return _memory_slices; }
  const VLoopBody& body() const                        { return _body; }
  const VLoopTypes& types() const                      { return _types; }
  const VLoopDependenceGraph& dependence_graph() const { return _dependence_graph; }

private:
  virtual void reset(IdealLoopTree* lpt, bool allow_cfg) override {
    VLoop::reset(lpt, allow_cfg);
    _reductions.reset();
    _memory_slices.reset();
    _body.reset();
    _types.reset();
    _dependence_graph.reset();
  }
  const char* analyze_helper();
};

// A vectorization pointer (VPointer) has information about an address for
// dependence checking and vector alignment. It's usually bound to a memory
// operation in a counted loop for vectorizable analysis.
class VPointer : public StackObj {
 protected:
  const MemNode*  _mem;      // My memory reference node
  const VLoop&    _vloop;

  Node* _base;               // null if unsafe nonheap reference
  Node* _adr;                // address pointer
  int   _scale;              // multiplier for iv (in bytes), 0 if no loop iv
  int   _offset;             // constant offset (in bytes)

  Node* _invar;              // invariant offset (in bytes), null if none
#ifdef ASSERT
  Node* _debug_invar;
  bool  _debug_negate_invar; // if true then use: (0 - _invar)
  Node* _debug_invar_scale;  // multiplier for invariant
#endif

  Node_Stack* _nstack;       // stack used to record a vpointer trace of variants
  bool        _analyze_only; // Used in loop unrolling only for vpointer trace
  uint        _stack_idx;    // Used in loop unrolling only for vpointer trace

  const VLoop&    vloop() const { return _vloop; }
  PhaseIdealLoop* phase() const { return _vloop.phase(); }
  IdealLoopTree*  lpt() const   { return _vloop.lpt(); }
  PhiNode*        iv() const    { return _vloop.iv(); }

  bool is_loop_member(Node* n) const;
  bool invariant(Node* n) const;

  // Match: k*iv + offset
  bool scaled_iv_plus_offset(Node* n);
  // Match: k*iv where k is a constant that's not zero
  bool scaled_iv(Node* n);
  // Match: offset is (k [+/- invariant])
  bool offset_plus_k(Node* n, bool negate = false);

 public:
  enum CMP {
    Less          = 1,
    Greater       = 2,
    Equal         = 4,
    NotEqual      = (Less | Greater),
    NotComparable = (Less | Greater | Equal)
  };

  VPointer(const MemNode* mem, const VLoop& vloop) :
    VPointer(mem, vloop, nullptr, false) {}
  VPointer(const MemNode* mem, const VLoop& vloop, Node_Stack* nstack) :
    VPointer(mem, vloop, nstack, true) {}
 private:
  VPointer(const MemNode* mem, const VLoop& vloop,
           Node_Stack* nstack, bool analyze_only);
  // Following is used to create a temporary object during
  // the pattern match of an address expression.
  VPointer(VPointer* p);
  NONCOPYABLE(VPointer);

 public:
  bool valid()             const { return _adr != nullptr; }
  bool has_iv()            const { return _scale != 0; }
  Node* base()             const { return _base; }
  Node* adr()              const { return _adr; }
  const MemNode* mem()     const { return _mem; }
  int   scale_in_bytes()   const { return _scale; }
  Node* invar()            const { return _invar; }
  int   offset_in_bytes()  const { return _offset; }
  int   memory_size()      const { return _mem->memory_size(); }
  Node_Stack* node_stack() const { return _nstack; }

  // Biggest detectable factor of the invariant.
  int   invar_factor() const;

  // Comparable?
  bool invar_equals(VPointer& q) {
    assert(_debug_invar == NodeSentinel || q._debug_invar == NodeSentinel ||
           (_invar == q._invar) == (_debug_invar == q._debug_invar &&
                                    _debug_invar_scale == q._debug_invar_scale &&
                                    _debug_negate_invar == q._debug_negate_invar), "");
    return _invar == q._invar;
  }

  int cmp(VPointer& q) {
    if (valid() && q.valid() &&
        (_adr == q._adr || (_base == _adr && q._base == q._adr)) &&
        _scale == q._scale   && invar_equals(q)) {
      bool overlap = q._offset <   _offset +   memory_size() &&
                       _offset < q._offset + q.memory_size();
      return overlap ? Equal : (_offset < q._offset ? Less : Greater);
    } else {
      return NotComparable;
    }
  }

  bool overlap_possible_with_any_in(Node_List* p) {
    for (uint k = 0; k < p->size(); k++) {
      MemNode* mem = p->at(k)->as_Mem();
      VPointer p_mem(mem, vloop());
      // Only if we know that we have Less or Greater can we
      // be sure that there can never be an overlap between
      // the two memory regions.
      if (!not_equal(p_mem)) {
        return true;
      }
    }
    return false;
  }

  bool not_equal(VPointer& q)     { return not_equal(cmp(q)); }
  bool equal(VPointer& q)         { return equal(cmp(q)); }
  bool comparable(VPointer& q)    { return comparable(cmp(q)); }
  static bool not_equal(int cmp)  { return cmp <= NotEqual; }
  static bool equal(int cmp)      { return cmp == Equal; }
  static bool comparable(int cmp) { return cmp < NotComparable; }

  void print();

#ifndef PRODUCT
  class Tracer {
    friend class VPointer;
    const VLoop &_vloop;
    static int _depth;
    int _depth_save;
    void print_depth() const;
    int  depth() const    { return _depth; }
    void set_depth(int d) { _depth = d; }
    void inc_depth()      { _depth++; }
    void dec_depth()      { if (_depth > 0) _depth--; }
    void store_depth()    { _depth_save = _depth; }
    void restore_depth()  { _depth = _depth_save; }

    class Depth {
      friend class VPointer;
      Depth()      { ++_depth; }
      Depth(int x) { _depth = 0; }
      ~Depth()     { if (_depth > 0) --_depth; }
    };
    Tracer(const VLoop &vloop) : _vloop(vloop) {}

    bool is_trace_pointer_analysis() const { return _vloop.is_trace_pointer_analysis(); }

    // tracing functions
    void ctor_1(const Node* mem);
    void ctor_2(Node* adr);
    void ctor_3(Node* adr, int i);
    void ctor_4(Node* adr, int i);
    void ctor_5(Node* adr, Node* base,  int i);
    void ctor_6(const Node* mem);

    void scaled_iv_plus_offset_1(Node* n);
    void scaled_iv_plus_offset_2(Node* n);
    void scaled_iv_plus_offset_3(Node* n);
    void scaled_iv_plus_offset_4(Node* n);
    void scaled_iv_plus_offset_5(Node* n);
    void scaled_iv_plus_offset_6(Node* n);
    void scaled_iv_plus_offset_7(Node* n);
    void scaled_iv_plus_offset_8(Node* n);

    void scaled_iv_1(Node* n);
    void scaled_iv_2(Node* n, int scale);
    void scaled_iv_3(Node* n, int scale);
    void scaled_iv_4(Node* n, int scale);
    void scaled_iv_5(Node* n, int scale);
    void scaled_iv_6(Node* n, int scale);
    void scaled_iv_7(Node* n);
    void scaled_iv_8(Node* n, VPointer* tmp);
    void scaled_iv_9(Node* n, int _scale, int _offset, Node* _invar);
    void scaled_iv_10(Node* n);

    void offset_plus_k_1(Node* n);
    void offset_plus_k_2(Node* n, int _offset);
    void offset_plus_k_3(Node* n, int _offset);
    void offset_plus_k_4(Node* n);
    void offset_plus_k_5(Node* n, Node* _invar);
    void offset_plus_k_6(Node* n, Node* _invar, bool _negate_invar, int _offset);
    void offset_plus_k_7(Node* n, Node* _invar, bool _negate_invar, int _offset);
    void offset_plus_k_8(Node* n, Node* _invar, bool _negate_invar, int _offset);
    void offset_plus_k_9(Node* n, Node* _invar, bool _negate_invar, int _offset);
    void offset_plus_k_10(Node* n, Node* _invar, bool _negate_invar, int _offset);
    void offset_plus_k_11(Node* n);
  } _tracer; // Tracer
#endif

  Node* maybe_negate_invar(bool negate, Node* invar);

  void maybe_add_to_invar(Node* new_invar, bool negate);

  Node* register_if_new(Node* n) const;
};


// Vector element size statistics for loop vectorization with vector masks
class VectorElementSizeStats {
 private:
  static const int NO_SIZE = -1;
  static const int MIXED_SIZE = -2;
  int* _stats;

 public:
  VectorElementSizeStats(Arena* a) : _stats(NEW_ARENA_ARRAY(a, int, 4)) {
    clear();
  }

  void clear() { memset(_stats, 0, sizeof(int) * 4); }

  void record_size(int size) {
    assert(1 <= size && size <= 8 && is_power_of_2(size), "Illegal size");
    _stats[exact_log2(size)]++;
  }

  int count_size(int size) {
    assert(1 <= size && size <= 8 && is_power_of_2(size), "Illegal size");
    return _stats[exact_log2(size)];
  }

  int smallest_size() {
    for (int i = 0; i <= 3; i++) {
      if (_stats[i] > 0) return (1 << i);
    }
    return NO_SIZE;
  }

  int largest_size() {
    for (int i = 3; i >= 0; i--) {
      if (_stats[i] > 0) return (1 << i);
    }
    return NO_SIZE;
  }

  int unique_size() {
    int small = smallest_size();
    int large = largest_size();
    return (small == large) ? small : MIXED_SIZE;
  }
};

// When alignment is required, we must adjust the pre-loop iteration count pre_iter,
// such that the address is aligned for any main_iter >= 0:
//
//   adr = base + offset + invar + scale * init
//                               + scale * pre_stride * pre_iter
//                               + scale * main_stride * main_iter
//
// The AlignmentSolver generates solutions of the following forms:
//   1. Empty:       No pre_iter guarantees alignment.
//   2. Trivial:     Any pre_iter guarantees alignment.
//   3. Constrained: There is a periodic solution, but it is not trivial.
//
// The Constrained solution is of the following form:
//
//   pre_iter = m * q + r                                    (for any integer m)
//                   [- invar / (scale * pre_stride)  ]      (if there is an invariant)
//                   [- init / pre_stride             ]      (if init is variable)
//
// The solution is periodic with periodicity q, which is guaranteed to be a power of 2.
// This periodic solution is "rotated" by three alignment terms: one for constants (r),
// one for the invariant (if present), and one for init (if it is variable).
//
// The "filter" method combines the solutions of two mem_refs, such that the new set of
// values for pre_iter guarantees alignment for both mem_refs.
//
class EmptyAlignmentSolution;
class TrivialAlignmentSolution;
class ConstrainedAlignmentSolution;

class AlignmentSolution : public ResourceObj {
public:
  virtual bool is_empty() const = 0;
  virtual bool is_trivial() const = 0;
  virtual bool is_constrained() const = 0;

  virtual const ConstrainedAlignmentSolution* as_constrained() const {
    assert(is_constrained(), "must be constrained");
    return nullptr;
  }

  // Implemented by each subclass
  virtual const AlignmentSolution* filter(const AlignmentSolution* other) const = 0;
  virtual void print() const = 0;

  // Compute modulo and ensure that we get a positive remainder
  static int mod(int i, int q) {
    assert(q >= 1, "modulo value must be large enough");

    // Modulo operator: Get positive 0 <= r < q  for positive i, but
    //                  get negative 0 >= r > -q for negative i.
    int r = i % q;

    // Make negative r into positive ones:
    r = (r >= 0) ? r : r + q;

    assert(0 <= r && r < q, "remainder must fit in modulo space");
    return r;
  }
};

class EmptyAlignmentSolution : public AlignmentSolution {
private:
  const char* _reason;
public:
  EmptyAlignmentSolution(const char* reason) :  _reason(reason) {}
  virtual bool is_empty() const override final       { return true; }
  virtual bool is_trivial() const override final     { return false; }
  virtual bool is_constrained() const override final { return false; }
  const char* reason() const { return _reason; }

  virtual const AlignmentSolution* filter(const AlignmentSolution* other) const override final {
    // If "this" cannot be guaranteed to be aligned, then we also cannot guarantee to align
    // "this" and "other" together.
    return new EmptyAlignmentSolution("empty solution input to filter");
  }

  virtual void print() const override final {
    tty->print_cr("empty solution: %s", reason());
  };
};

class TrivialAlignmentSolution : public AlignmentSolution {
public:
  TrivialAlignmentSolution() {}
  virtual bool is_empty() const override final       { return false; }
  virtual bool is_trivial() const override final     { return true; }
  virtual bool is_constrained() const override final { return false; }

  virtual const AlignmentSolution* filter(const AlignmentSolution* other) const override final {
    if (other->is_empty()) {
      // If "other" cannot be guaranteed to be aligned, then we also cannot guarantee to align
      // "this" and "other".
      return new EmptyAlignmentSolution("empty solution input to filter");
    }
    // Since "this" is trivial (no constraints), the solution of "other" guarantees alignment
    // of both.
    return other;
  }

  virtual void print() const override final {
    tty->print_cr("pre_iter >= 0 (trivial)");
  };
};

class ConstrainedAlignmentSolution : public AlignmentSolution {
private:
  const MemNode* _mem_ref;
  const int _q;
  const int _r;
  const Node* _invar;
  const int _scale;
public:
  ConstrainedAlignmentSolution(const MemNode* mem_ref,
                               const int q,
                               const int r,
                               const Node* invar,
                               int scale) :
      _mem_ref(mem_ref),
      _q(q),
      _r(r),
      _invar(invar),
      _scale(scale) {
    assert(q > 1 && is_power_of_2(q), "q must be power of 2");
    assert(0 <= r && r < q, "r must be in modulo space of q");
    assert(_mem_ref != nullptr, "must have mem_ref");
  }

  virtual bool is_empty() const override final       { return false; }
  virtual bool is_trivial() const override final     { return false; }
  virtual bool is_constrained() const override final { return true; }

  const MemNode* mem_ref() const        { return _mem_ref; }

  virtual const ConstrainedAlignmentSolution* as_constrained() const override final { return this; }

  virtual const AlignmentSolution* filter(const AlignmentSolution* other) const override final {
    if (other->is_empty()) {
      // If "other" cannot be guaranteed to be aligned, then we also cannot guarantee to align
      // "this" and "other" together.
      return new EmptyAlignmentSolution("empty solution input to filter");
    }
    // Since "other" is trivial (no constraints), the solution of "this" guarantees alignment
    // of both.
    if (other->is_trivial()) {
      return this;
    }

    // Both solutions are constrained:
    ConstrainedAlignmentSolution const* s1 = this;
    ConstrainedAlignmentSolution const* s2 = other->as_constrained();

    // Thus, pre_iter is the intersection of two sets, i.e. constrained by these two equations,
    // for any integers m1 and m2:
    //
    //   pre_iter = m1 * q1 + r1
    //                     [- invar1 / (scale1 * pre_stride)  ]
    //                     [- init / pre_stride               ]
    //
    //   pre_iter = m2 * q2 + r2
    //                     [- invar2 / (scale2 * pre_stride)  ]
    //                     [- init / pre_stride               ]
    //
    // Note: pre_stride and init are identical for all mem_refs in the loop.
    //
    // The init alignment term either does not exist for both mem_refs, or exists identically
    // for both. The init alignment term is thus trivially identical.
    //
    // The invar alignment term is identical if either:
    //   - both mem_refs have no invariant.
    //   - both mem_refs have the same invariant and the same scale.
    //
    if (s1->_invar != s2->_invar) {
      return new EmptyAlignmentSolution("invar not identical");
    }
    if (s1->_invar != nullptr && s1->_scale != s2->_scale) {
      return new EmptyAlignmentSolution("has invar with different scale");
    }

    // Now, we have reduced the problem to:
    //
    //   pre_iter = m1 * q1 + r1 [- x]       (S1)
    //   pre_iter = m2 * q2 + r2 [- x]       (S2)
    //

    // Make s2 the bigger modulo space, i.e. has larger periodicity q.
    // This guarantees that S2 is either identical to, a subset of,
    // or disjunct from S1 (but cannot be a strict superset of S1).
    if (s1->_q > s2->_q) {
      swap(s1, s2);
    }
    assert(s1->_q <= s2->_q, "s1 is a smaller modulo space than s2");

    // Is S2 subset of (or equal to) S1?
    //
    // for any m2, there are integers a, b, m1: m2 * q2     + r2          =
    //                                          m2 * a * q1 + b * q1 + r1 =
    //                                          (m2 * a + b) * q1 + r1
    //
    // Since q1 and q2 are both powers of 2, and q1 <= q2, we know there
    // is an integer a: a * q1 = q2. Thus, it remains to check if there
    // is an integer b: b * q1 + r1 = r2. This is equivalent to checking:
    //
    //   r1 = r1 % q1 = r2 % q1
    //
    if (mod(s2->_r, s1->_q) != s1->_r) {
      // Neither is subset of the other -> no intersection
      return new EmptyAlignmentSolution("empty intersection (r and q)");
    }

    // Now we know: "s1 = m1 * q1 + r1" is a superset of "s2 = m2 * q2 + r2"
    // Hence, any solution of S2 guarantees alignment for both mem_refs.
    return s2; // return the subset
  }

  virtual void print() const override final {
    tty->print("m * q(%d) + r(%d)", _q, _r);
    if (_invar != nullptr) {
      tty->print(" - invar[%d] / (scale(%d) * pre_stride)", _invar->_idx, _scale);
    }
    tty->print_cr(" [- init / pre_stride], mem_ref[%d]", mem_ref()->_idx);
  };
};

// When strict alignment is required (e.g. -XX:+AlignVector), then we must ensure
// that all vector memory accesses can be aligned. We achieve this alignment by
// adjusting the pre-loop limit, which adjusts the number of iterations executed
// in the pre-loop.
//
// This is how the pre-loop and unrolled main-loop look like for a memref (adr):
//
// iv = init
// i = 0 // single-iteration counter
//
// pre-loop:
//   iv = init + i * pre_stride
//   adr = base + offset + invar + scale * iv
//   adr = base + offset + invar + scale * (init + i * pre_stride)
//   iv += pre_stride
//   i++
//
// pre_iter = i // number of iterations in the pre-loop
// iv = init + pre_iter * pre_stride
//
// main_iter = 0 // main-loop iteration counter
// main_stride = unroll_factor * pre_stride
//
// main-loop:
//   i = pre_iter + main_iter * unroll_factor
//   iv = init + i * pre_stride = init + pre_iter * pre_stride + main_iter * unroll_factor * pre_stride
//                              = init + pre_iter * pre_stride + main_iter * main_stride
//   adr = base + offset + invar + scale * iv // must be aligned
//   iv += main_stride
//   i  += unroll_factor
//   main_iter++
//
// For each vector memory access, we can find the set of pre_iter (number of pre-loop
// iterations) which would align its address. The AlignmentSolver finds such an
// AlignmentSolution. We can then check which solutions are compatible, and thus
// decide if we have to (partially) reject vectorization if not all vectors have
// a compatible solutions.
class AlignmentSolver {
private:
  const MemNode* _mem_ref;       // first element
  const uint     _vector_length; // number of elements in vector
  const int      _element_size;
  const int      _vector_width;  // in bytes

  // All vector loads and stores need to be memory aligned. The alignment width (aw) in
  // principle is the vector_width. But when vector_width > ObjectAlignmentInBytes this is
  // too strict, since any memory object is only guaranteed to be ObjectAlignmentInBytes
  // aligned. For example, the relative offset between two arrays is only guaranteed to
  // be divisible by ObjectAlignmentInBytes.
  const int      _aw;

  // We analyze the address of mem_ref. The idea is to disassemble it into a linear
  // expression, where we can use the constant factors as the basis for ensuring the
  // alignment of vector memory accesses.
  //
  // The Simple form of the address is disassembled by VPointer into:
  //
  //   adr = base + offset + invar + scale * iv
  //
  // Where the iv can be written as:
  //
  //   iv = init + pre_stride * pre_iter + main_stride * main_iter
  //
  // pre_iter:    number of pre-loop iterations (adjustable via pre-loop limit)
  // main_iter:   number of main-loop iterations (main_iter >= 0)
  //
  const Node*    _base;           // base of address (e.g. Java array object, aw-aligned)
  const int      _offset;
  const Node*    _invar;
  const int      _invar_factor;   // known constant factor of invar
  const int      _scale;
  const Node*    _init_node;      // value of iv before pre-loop
  const int      _pre_stride;     // address increment per pre-loop iteration
  const int      _main_stride;    // address increment per main-loop iteration

  DEBUG_ONLY( const bool _is_trace; );

  static const MemNode* mem_ref_not_null(const MemNode* mem_ref) {
    assert(mem_ref != nullptr, "not nullptr");
    return mem_ref;
  }

public:
  AlignmentSolver(const MemNode* mem_ref,
                  const uint vector_length,
                  const Node* base,
                  const int offset,
                  const Node* invar,
                  const int invar_factor,
                  const int scale,
                  const Node* init_node,
                  const int pre_stride,
                  const int main_stride
                  DEBUG_ONLY( COMMA const bool is_trace)
                  ) :
      _mem_ref(           mem_ref_not_null(mem_ref)),
      _vector_length(     vector_length),
      _element_size(      _mem_ref->memory_size()),
      _vector_width(      _vector_length * _element_size),
      _aw(                MIN2(_vector_width, ObjectAlignmentInBytes)),
      _base(              base),
      _offset(            offset),
      _invar(             invar),
      _invar_factor(      invar_factor),
      _scale(             scale),
      _init_node(         init_node),
      _pre_stride(        pre_stride),
      _main_stride(       main_stride)
      DEBUG_ONLY( COMMA _is_trace(is_trace) )
  {
    assert(_mem_ref != nullptr &&
           (_mem_ref->is_Load() || _mem_ref->is_Store()),
           "only load or store vectors allowed");
  }

  AlignmentSolution* solve() const;

private:
  class EQ4 {
   private:
    const int _C_const;
    const int _C_invar;
    const int _C_init;
    const int _C_pre;
    const int _aw;

   public:
    EQ4(const int C_const, const int C_invar, const int C_init, const int C_pre, const int aw) :
    _C_const(C_const), _C_invar(C_invar), _C_init(C_init), _C_pre(C_pre), _aw(aw) {}

    enum State { TRIVIAL, CONSTRAINED, EMPTY };

    State eq4a_state() const {
      return (abs(_C_pre) >= _aw) ? ( (C_const_mod_aw() == 0       ) ? TRIVIAL     : EMPTY)
                                  : ( (C_const_mod_abs_C_pre() == 0) ? CONSTRAINED : EMPTY);
    }

    State eq4b_state() const {
      return (abs(_C_pre) >= _aw) ? ( (C_invar_mod_aw() == 0       ) ? TRIVIAL     : EMPTY)
                                  : ( (C_invar_mod_abs_C_pre() == 0) ? CONSTRAINED : EMPTY);
    }

    State eq4c_state() const {
      return (abs(_C_pre) >= _aw) ? ( (C_init_mod_aw() == 0       )  ? TRIVIAL     : EMPTY)
                                  : ( (C_init_mod_abs_C_pre() == 0)  ? CONSTRAINED : EMPTY);
    }

   private:
    int C_const_mod_aw() const        { return AlignmentSolution::mod(_C_const, _aw); }
    int C_invar_mod_aw() const        { return AlignmentSolution::mod(_C_invar, _aw); }
    int C_init_mod_aw() const         { return AlignmentSolution::mod(_C_init,  _aw); }
    int C_const_mod_abs_C_pre() const { return AlignmentSolution::mod(_C_const, abs(_C_pre)); }
    int C_invar_mod_abs_C_pre() const { return AlignmentSolution::mod(_C_invar, abs(_C_pre)); }
    int C_init_mod_abs_C_pre() const  { return AlignmentSolution::mod(_C_init,  abs(_C_pre)); }

#ifdef ASSERT
   public:
    void trace() const;

   private:
    static const char* state_to_str(State s) {
      if (s == TRIVIAL)     { return "trivial"; }
      if (s == CONSTRAINED) { return "constrained"; }
      return "empty";
    }
#endif
  };

#ifdef ASSERT
  bool is_trace() const { return _is_trace; }
  void trace_start_solve() const;
  void trace_reshaped_form(const int C_const,
                           const int C_const_init,
                           const int C_invar,
                           const int C_init,
                           const int C_pre,
                           const int C_main) const;
  void trace_main_iteration_alignment(const int C_const,
                                      const int C_invar,
                                      const int C_init,
                                      const int C_pre,
                                      const int C_main,
                                      const int C_main_mod_aw) const;
  void trace_constrained_solution(const int C_const,
                                  const int C_invar,
                                  const int C_init,
                                  const int C_pre,
                                  const int q,
                                  const int r) const;
#endif
};

#endif // SHARE_OPTO_VECTORIZATION_HPP
