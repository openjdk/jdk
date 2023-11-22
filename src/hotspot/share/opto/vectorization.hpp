/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
  bool is_trace_loop_analyze() const {
    return _trace_tags.at(TraceAutovectorizationTag::TAG_LOOP_ANALYZE);
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
    return _trace_tags.at(TraceAutovectorizationTag::TAG_VECTOR_ELEMENT_TYPE);
  }
  bool is_trace_pointer_analysis() const {
    return _trace_tags.at(TraceAutovectorizationTag::TAG_POINTER_ANALYSIS);
  }
  bool is_trace_alignment() const {
    return _trace_tags.at(TraceAutovectorizationTag::TAG_ALIGNMENT);
  }
#endif

  // Check if the loop passes some basic preconditions for vectorization.
  // Overwrite previous data. Return indicates if analysis succeeded.
  bool check_preconditions(IdealLoopTree* lpt, bool allow_cfg);

protected:
  const char* check_preconditions_helper();
};

// Find the reductions in the loop and mark them.
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

  DEBUG_ONLY(void print() const;)
};

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
  DEBUG_ONLY(void print() const;)

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

// Represents the dependence graph, constructed from the data dependencies
// and memory dependencies.
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

// Compute necessary vector element type for expressions
// This propagates backwards a narrower integer type when the
// upper bits of the value are not needed.
// Example:  char a,b,c;  a = b + c;
// Normally the type of the add is integer, but for packed character
// operations the type of the add needs to be char.
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

  const Type* velt_type(Node* n) const {
    assert(_vloop.in_body(n), "only call on nodes in loop");
    const Type* t = _velt_type.at(_body.body_idx(n));
    assert(t != nullptr, "must have type");
    return t;
  }

  BasicType velt_basic_type(Node* n) const {
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

  int vector_width(Node* n) const {
    BasicType bt = velt_basic_type(n);
    return MIN2(ABS(_vloop.iv_stride()), Matcher::max_vector_size(bt));
  }

  int vector_width_in_bytes(Node* n) const {
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
// as possible.
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
    _types(*this, _body),
    _dependence_graph(*this, _memory_slices, _body) {};
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
  MemNode*        _mem;      // My memory reference node
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

  VPointer(MemNode* mem, const VLoop& vloop) :
    VPointer(mem, vloop, nullptr, false) {}
  VPointer(MemNode* mem, const VLoop& vloop, Node_Stack* nstack) :
    VPointer(mem, vloop, nstack, true) {}
 private:
  VPointer(MemNode* mem, const VLoop& vloop,
           Node_Stack* nstack, bool analyze_only);
  // Following is used to create a temporary object during
  // the pattern match of an address expression.
  VPointer(VPointer* p);
  NONCOPYABLE(VPointer);

 public:
  bool valid()  { return _adr != nullptr; }
  bool has_iv() { return _scale != 0; }

  Node* base()             { return _base; }
  Node* adr()              { return _adr; }
  MemNode* mem()           { return _mem; }
  int   scale_in_bytes()   { return _scale; }
  Node* invar()            { return _invar; }
  int   offset_in_bytes()  { return _offset; }
  int   memory_size()      { return _mem->memory_size(); }
  Node_Stack* node_stack() { return _nstack; }

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
    void ctor_1(Node* mem);
    void ctor_2(Node* adr);
    void ctor_3(Node* adr, int i);
    void ctor_4(Node* adr, int i);
    void ctor_5(Node* adr, Node* base,  int i);
    void ctor_6(Node* mem);

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

#endif // SHARE_OPTO_VECTORIZATION_HPP
