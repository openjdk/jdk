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

#include "opto/node.hpp"
#include "opto/loopnode.hpp"
#include "opto/traceAutoVectorizationTag.hpp"
#include "utilities/pair.hpp"

// Code in this file and the vectorization.cpp contains shared logics and
// utilities for C2's loop auto-vectorization.

class VPointer;

class VStatus : public StackObj {
private:
  const char* _failure_reason;

  VStatus(const char* failure_reason) : _failure_reason(failure_reason) {}

public:
  static VStatus make_success() { return VStatus(nullptr); }

  static VStatus make_failure(const char* failure_reason) {
    assert(failure_reason != nullptr, "must have reason");
    return VStatus(failure_reason);
  }

  bool is_success() const { return _failure_reason == nullptr; }

  const char* failure_reason() const {
    assert(!is_success(), "only failures have reason");
    return _failure_reason;
  }
};

#ifndef PRODUCT
// Access to TraceAutoVectorization tags
class VTrace : public StackObj {
private:
  const CHeapBitMap &_trace_tags;

public:
  VTrace() : _trace_tags(Compile::current()->directive()->trace_auto_vectorization_tags()) {}
  NONCOPYABLE(VTrace);

  bool is_trace(TraceAutoVectorizationTag tag) const {
    return _trace_tags.at(tag);
  }
};
#endif

// Basic loop structure accessors and vectorization preconditions checking
class VLoop : public StackObj {
private:
  PhaseIdealLoop* const _phase;
  IdealLoopTree* const _lpt;
  const bool _allow_cfg;
  CountedLoopNode* _cl;
  Node* _cl_exit;
  PhiNode* _iv;
  CountedLoopEndNode* _pre_loop_end; // cache access to pre-loop for main loops only

  NOT_PRODUCT(VTrace _vtrace;)

  static constexpr char const* FAILURE_ALREADY_VECTORIZED = "loop already vectorized";
  static constexpr char const* FAILURE_UNROLL_ONLY        = "loop only wants to be unrolled";
  static constexpr char const* FAILURE_VECTOR_WIDTH       = "vector_width must be power of 2";
  static constexpr char const* FAILURE_VALID_COUNTED_LOOP = "must be valid counted loop (int)";
  static constexpr char const* FAILURE_CONTROL_FLOW       = "control flow in loop not allowed";
  static constexpr char const* FAILURE_BACKEDGE           = "nodes on backedge not allowed";
  static constexpr char const* FAILURE_PRE_LOOP_LIMIT     = "main-loop must be able to adjust pre-loop-limit (not found)";

public:
  VLoop(IdealLoopTree* lpt, bool allow_cfg) :
    _phase     (lpt->_phase),
    _lpt       (lpt),
    _allow_cfg (allow_cfg),
    _cl        (nullptr),
    _cl_exit   (nullptr),
    _iv        (nullptr),
    _pre_loop_end (nullptr) {}
  NONCOPYABLE(VLoop);

  IdealLoopTree* lpt()        const { return _lpt; };
  PhaseIdealLoop* phase()     const { return _phase; }
  CountedLoopNode* cl()       const { return _cl; };
  Node* cl_exit()             const { return _cl_exit; };
  PhiNode* iv()               const { return _iv; };
  int iv_stride()             const { return cl()->stride_con(); };
  bool is_allow_cfg()         const { return _allow_cfg; }

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

  // Estimate maximum size for data structures, to avoid repeated reallocation
  int estimated_body_length() const { return lpt()->_body.size(); };
  int estimated_node_count()  const { return (int)(1.10 * phase()->C->unique()); };

#ifndef PRODUCT
  const VTrace& vtrace()      const { return _vtrace; }

  bool is_trace_preconditions() const {
    return _vtrace.is_trace(TraceAutoVectorizationTag::PRECONDITIONS);
  }

  bool is_trace_loop_analyzer() const {
    return _vtrace.is_trace(TraceAutoVectorizationTag::LOOP_ANALYZER);
  }

  bool is_trace_memory_slices() const {
    return _vtrace.is_trace(TraceAutoVectorizationTag::MEMORY_SLICES);
  }

  bool is_trace_body() const {
    return _vtrace.is_trace(TraceAutoVectorizationTag::BODY);
  }

  bool is_trace_vector_element_type() const {
    return _vtrace.is_trace(TraceAutoVectorizationTag::TYPES);
  }

  bool is_trace_dependency_graph() const {
    return _vtrace.is_trace(TraceAutoVectorizationTag::DEPENDENCY_GRAPH);
  }

  bool is_trace_vpointers() const {
    return _vtrace.is_trace(TraceAutoVectorizationTag::POINTERS);
  }

  bool is_trace_pointer_analysis() const {
    return _vtrace.is_trace(TraceAutoVectorizationTag::POINTER_ANALYSIS);
  }
#endif

  // Is the node in the basic block of the loop?
  // We only accept any nodes which have the loop head as their ctrl.
  bool in_bb(const Node* n) const {
    const Node* ctrl = _phase->has_ctrl(n) ? _phase->get_ctrl(n) : n;
    return n != nullptr && n->outcnt() > 0 && ctrl == _cl;
  }

  // Check if the loop passes some basic preconditions for vectorization.
  // Return indicates if analysis succeeded.
  bool check_preconditions();

private:
  VStatus check_preconditions_helper();
};

// Optimization to keep allocation of large arrays in AutoVectorization low.
// We allocate the arrays once, and reuse them for multiple loops that we
// AutoVectorize, clearing them before every new use.
class VSharedData : public StackObj {
private:
  // Arena, used to allocate all arrays from.
  Arena _arena;

  // An array that maps node->_idx to a much smaller idx, which is at most the
  // size of a loop body. This allow us to have smaller arrays for other data
  // structures, since we are using smaller indices.
  GrowableArray<int> _node_idx_to_loop_body_idx;

public:
  VSharedData() :
    _arena(mtCompiler),
    _node_idx_to_loop_body_idx(&_arena, estimated_node_count(), 0, 0)
  {
  }

  GrowableArray<int>& node_idx_to_loop_body_idx() {
    return _node_idx_to_loop_body_idx;
  }

  // Must be cleared before each AutoVectorization use
  void clear() {
    _node_idx_to_loop_body_idx.clear();
  }

private:
  static int estimated_node_count() {
    return (int)(1.10 * Compile::current()->unique());
  }
};

// Submodule of VLoopAnalyzer.
// Identify and mark all reductions in the loop.
class VLoopReductions : public StackObj {
private:
  typedef const Pair<const Node*, int> PathEnd;

  const VLoop& _vloop;
  VectorSet _loop_reductions;

public:
  VLoopReductions(Arena* arena, const VLoop& vloop) :
    _vloop(vloop),
    _loop_reductions(arena){};

  NONCOPYABLE(VLoopReductions);

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
  // Find and mark reductions in a loop. Running mark_reductions() is similar to
  // querying is_reduction(n) for every node in the loop, but stricter in
  // that it assumes counted loops and requires that reduction nodes are not
  // used within the loop except by their reduction cycle predecessors.
  void mark_reductions();

  // Whether n is a reduction operator and part of a reduction cycle.
  // This function can be used for individual queries outside auto-vectorization,
  // e.g. to inform matching in target-specific code. Otherwise, the
  // almost-equivalent but faster mark_reductions() is preferable.
  static bool is_reduction(const Node* n);

  // Whether n is marked as a reduction node.
  bool is_marked_reduction(const Node* n) const { return _loop_reductions.test(n->_idx); }

  bool is_marked_reduction_loop() const { return !_loop_reductions.is_empty(); }

  // Are s1 and s2 reductions with a data path between them?
  bool is_marked_reduction_pair(const Node* s1, const Node* s2) const;

private:
  // Whether n is a standard reduction operator.
  static bool is_reduction_operator(const Node* n);

  // Whether n is part of a reduction cycle via the 'input' edge index. To bound
  // the search, constrain the size of reduction cycles to LoopMaxUnroll.
  static bool in_reduction_cycle(const Node* n, uint input);

  // Reference to the i'th input node of n, commuting the inputs of binary nodes
  // whose edges have been swapped. Assumes n is a commutative operation.
  static Node* original_input(const Node* n, uint i);
};

// Submodule of VLoopAnalyzer.
// Find the memory slices in the loop.
class VLoopMemorySlices : public StackObj {
private:
  const VLoop& _vloop;

  GrowableArray<PhiNode*> _heads;
  GrowableArray<MemNode*> _tails;

public:
  VLoopMemorySlices(Arena* arena, const VLoop& vloop) :
    _vloop(vloop),
    _heads(arena, 8, 0, nullptr),
    _tails(arena, 8, 0, nullptr) {};
  NONCOPYABLE(VLoopMemorySlices);

  void find_memory_slices();

  const GrowableArray<PhiNode*>& heads() const { return _heads; }
  const GrowableArray<MemNode*>& tails() const { return _tails; }

  // Get all memory nodes of a slice, in reverse order
  void get_slice_in_reverse_order(PhiNode* head, MemNode* tail, GrowableArray<MemNode*>& slice) const;

  bool same_memory_slice(MemNode* m1, MemNode* m2) const;

#ifndef PRODUCT
  void print() const;
#endif
};

// Submodule of VLoopAnalyzer.
// Finds all nodes in the body, and creates a mapping node->_idx to a body_idx.
// This mapping is used so that subsequent datastructures sizes only grow with
// the body size, and not the number of all nodes in the compilation.
class VLoopBody : public StackObj {
private:
  static constexpr char const* FAILURE_NODE_NOT_ALLOWED = "encontered unhandled node";
  static constexpr char const* FAILURE_UNEXPECTED_CTRL  = "data node in loop has no input in loop";

  const VLoop& _vloop;

  // Mapping body_idx -> Node*
  GrowableArray<Node*> _body;

  // Mapping node->_idx -> body_idx
  // Can be very large, and thus lives in VSharedData
  GrowableArray<int>& _body_idx;

public:
  VLoopBody(Arena* arena, const VLoop& vloop, VSharedData& vshared) :
    _vloop(vloop),
    _body(arena, vloop.estimated_body_length(), 0, nullptr),
    _body_idx(vshared.node_idx_to_loop_body_idx()) {}

  NONCOPYABLE(VLoopBody);

  VStatus construct();
  const GrowableArray<Node*>& body() const { return _body; }
  NOT_PRODUCT( void print() const; )

  int bb_idx(const Node* n) const {
    assert(_vloop.in_bb(n), "must be in basic block");
    return _body_idx.at(n->_idx);
  }

  template<typename Callback>
  void for_each_mem(Callback callback) const {
    for (int i = 0; i < _body.length(); i++) {
      MemNode* mem = _body.at(i)->isa_Mem();
      if (mem != nullptr && _vloop.in_bb(mem)) {
        callback(mem, i);
      }
    }
  }

private:
  void set_bb_idx(Node* n, int i) {
    _body_idx.at_put_grow(n->_idx, i);
  }
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
// with only the lower bits, and we propagate the narrower type to it.
class VLoopTypes : public StackObj {
private:
  const VLoop&     _vloop;
  const VLoopBody& _body;

  // bb_idx -> vector element type
  GrowableArray<const Type*> _velt_type;

public:
  VLoopTypes(Arena* arena,
             const VLoop& vloop,
             const VLoopBody& body) :
    _vloop(vloop),
    _body(body),
    _velt_type(arena, vloop.estimated_body_length(), 0, nullptr) {}
  NONCOPYABLE(VLoopTypes);

  void compute_vector_element_type();
  NOT_PRODUCT( void print() const; )

  const Type* velt_type(const Node* n) const {
    assert(_vloop.in_bb(n), "only call on nodes in loop");
    const Type* t = _velt_type.at(_body.bb_idx(n));
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
    assert(_vloop.in_bb(n), "only call on nodes in loop");
    _velt_type.at_put(_body.bb_idx(n), t);
  }

  // Smallest type containing range of values
  const Type* container_type(Node* n) const;
};

// Submodule of VLoopAnalyzer.
// We compute and cache the VPointer for every load and store.
class VLoopVPointers : public StackObj {
private:
  Arena*                   _arena;
  const VLoop&             _vloop;
  const VLoopBody&         _body;

  // Array of cached pointers
  VPointer* _vpointers;
  int _vpointers_length;

  // Map bb_idx -> index in _vpointers. -1 if not mapped.
  GrowableArray<int> _bb_idx_to_vpointer;

public:
  VLoopVPointers(Arena* arena,
                 const VLoop& vloop,
                 const VLoopBody& body) :
    _arena(arena),
    _vloop(vloop),
    _body(body),
    _vpointers(nullptr),
    _bb_idx_to_vpointer(arena,
                        vloop.estimated_body_length(),
                        vloop.estimated_body_length(),
                        -1) {}
  NONCOPYABLE(VLoopVPointers);

  void compute_vpointers();
  const VPointer& vpointer(const MemNode* mem) const;
  NOT_PRODUCT( void print() const; )

private:
  void count_vpointers();
  void allocate_vpointers_array();
  void compute_and_cache_vpointers();
};

// Submodule of VLoopAnalyzer.
// The dependency graph is used to determine if nodes are independent, and can thus potentially
// be executed in parallel. That is a prerequisite for packing nodes into vector operations.
// The dependency graph is a combination:
//  - Data-dependencies: they can directly be taken from the C2 node inputs.
//  - Memory-dependencies: the edges in the C2 memory-slice are too restrictive: for example all
//                         stores are serialized, even if their memory does not overlap. Thus,
//                         we refine the memory-dependencies (see construct method).
class VLoopDependencyGraph : public StackObj {
private:
  class DependencyNode;

  Arena*                   _arena;
  const VLoop&             _vloop;
  const VLoopBody&         _body;
  const VLoopMemorySlices& _memory_slices;
  const VLoopVPointers&    _vpointers;

  // bb_idx -> DependenceNode*
  GrowableArray<DependencyNode*> _dependency_nodes;

  // Node depth in DAG: bb_idx -> depth
  GrowableArray<int> _depths;

public:
  VLoopDependencyGraph(Arena* arena,
                       const VLoop& vloop,
                       const VLoopBody& body,
                       const VLoopMemorySlices& memory_slices,
                       const VLoopVPointers& pointers) :
    _arena(arena),
    _vloop(vloop),
    _body(body),
    _memory_slices(memory_slices),
    _vpointers(pointers),
    _dependency_nodes(arena,
                      vloop.estimated_body_length(),
                      vloop.estimated_body_length(),
                      nullptr),
    _depths(arena,
            vloop.estimated_body_length(),
            vloop.estimated_body_length(),
            0) {}
  NONCOPYABLE(VLoopDependencyGraph);

  void construct();
  bool independent(Node* s1, Node* s2) const;
  bool mutually_independent(const Node_List* nodes) const;

private:
  void add_node(MemNode* n, GrowableArray<int>& memory_pred_edges);
  int depth(const Node* n) const { return _depths.at(_body.bb_idx(n)); }
  void set_depth(const Node* n, int d) { _depths.at_put(_body.bb_idx(n), d); }
  int find_max_pred_depth(const Node* n) const;
  void compute_depth();
  NOT_PRODUCT( void print() const; )

  const DependencyNode* dependency_node(const Node* n) const {
    return _dependency_nodes.at(_body.bb_idx(n));
  }

  class DependencyNode : public ArenaObj {
  private:
    MemNode* _node; // Corresponding ideal node
    const uint _memory_pred_edges_length;
    int* _memory_pred_edges; // memory pred-edges, mapping to bb_idx
  public:
    DependencyNode(MemNode* n, GrowableArray<int>& memory_pred_edges, Arena* arena);
    NONCOPYABLE(DependencyNode);
    uint memory_pred_edges_length() const { return _memory_pred_edges_length; }

    int memory_pred_edge(uint i) const {
      assert(i < _memory_pred_edges_length, "bounds check");
      return _memory_pred_edges[i];
    }
  };

public:
  // Iterator for dependency graph predecessors of a node.
  class PredsIterator : public StackObj {
  private:
    const VLoopDependencyGraph& _dependency_graph;

    const Node* _node;
    const DependencyNode* _dependency_node;

    Node* _current;

    // Iterate in node->in(i)
    int _next_pred;
    int _end_pred;

    // Iterate in dependency_node->memory_pred_edge(i)
    int _next_memory_pred;
    int _end_memory_pred;
  public:
    PredsIterator(const VLoopDependencyGraph& dependency_graph, const Node* node);
    NONCOPYABLE(PredsIterator);
    void next();
    bool done() const { return _current == nullptr; }
    Node* current() const {
      assert(!done(), "not done yet");
      return _current;
    }
  };
};

// Analyze the loop in preparation for auto-vectorization. This class is
// deliberately structured into many submodules, which are as independent
// as possible, though some submodules do require other submodules.
class VLoopAnalyzer : StackObj {
private:
  static constexpr char const* FAILURE_NO_MAX_UNROLL         = "slp max unroll analysis required";
  static constexpr char const* FAILURE_NO_REDUCTION_OR_STORE = "no reduction and no store in loop";

  const VLoop&         _vloop;

  // Arena for all submodules
  Arena                _arena;

  // If all submodules are setup successfully, we set this flag at the
  // end of the constructor
  bool                 _success;

  // Submodules
  VLoopReductions      _reductions;
  VLoopMemorySlices    _memory_slices;
  VLoopBody            _body;
  VLoopTypes           _types;
  VLoopVPointers       _vpointers;
  VLoopDependencyGraph _dependency_graph;

public:
  VLoopAnalyzer(const VLoop& vloop, VSharedData& vshared) :
    _vloop(vloop),
    _arena(mtCompiler),
    _success(false),
    _reductions      (&_arena, vloop),
    _memory_slices   (&_arena, vloop),
    _body            (&_arena, vloop, vshared),
    _types           (&_arena, vloop, _body),
    _vpointers       (&_arena, vloop, _body),
    _dependency_graph(&_arena, vloop, _body, _memory_slices, _vpointers)
  {
    _success = setup_submodules();
  }
  NONCOPYABLE(VLoopAnalyzer);

  bool success() const { return _success; }

  // Read-only accessors for submodules
  const VLoop& vloop()                           const { return _vloop; }
  const VLoopReductions& reductions()            const { return _reductions; }
  const VLoopMemorySlices& memory_slices()       const { return _memory_slices; }
  const VLoopBody& body()                        const { return _body; }
  const VLoopTypes& types()                      const { return _types; }
  const VLoopVPointers& vpointers()              const { return _vpointers; }
  const VLoopDependencyGraph& dependency_graph() const { return _dependency_graph; }

private:
  bool setup_submodules();
  VStatus setup_submodules_helper();
};

// A vectorization pointer (VPointer) has information about an address for
// dependence checking and vector alignment. It's usually bound to a memory
// operation in a counted loop for vectorizable analysis.
class VPointer : public ArenaObj {
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
  bool invar_equals(const VPointer& q) const {
    assert(_debug_invar == NodeSentinel || q._debug_invar == NodeSentinel ||
           (_invar == q._invar) == (_debug_invar == q._debug_invar &&
                                    _debug_invar_scale == q._debug_invar_scale &&
                                    _debug_negate_invar == q._debug_negate_invar), "");
    return _invar == q._invar;
  }

  int cmp(const VPointer& q) const {
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

  bool overlap_possible_with_any_in(const Node_List* p) const {
    for (uint k = 0; k < p->size(); k++) {
      MemNode* mem = p->at(k)->as_Mem();
      VPointer p_mem(mem, _vloop);
      // Only if we know that we have Less or Greater can we
      // be sure that there can never be an overlap between
      // the two memory regions.
      if (!not_equal(p_mem)) {
        return true;
      }
    }
    return false;
  }

  bool not_equal(const VPointer& q)  const { return not_equal(cmp(q)); }
  bool equal(const VPointer& q)      const { return equal(cmp(q)); }
  bool comparable(const VPointer& q) const { return comparable(cmp(q)); }
  static bool not_equal(int cmp)  { return cmp <= NotEqual; }
  static bool equal(int cmp)      { return cmp == Equal; }
  static bool comparable(int cmp) { return cmp < NotComparable; }

  NOT_PRODUCT( void print() const; )

#ifndef PRODUCT
  class Tracer {
    friend class VPointer;
    bool _is_trace_alignment;
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
    Tracer(bool is_trace_alignment) : _is_trace_alignment(is_trace_alignment) {}

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
