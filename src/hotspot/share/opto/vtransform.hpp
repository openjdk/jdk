/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_VTRANSFORM_HPP
#define SHARE_OPTO_VTRANSFORM_HPP

#include "opto/node.hpp"
#include "opto/vectorization.hpp"

// VTransform:
// - Models the transformation of the scalar loop to vectorized loop:
//   It is a "C2 subgraph" -> "C2 subgraph" mapping.
// - The VTransform contains a graph (VTransformGraph), which consists of
//   many vtnodes (VTransformNode).
// - Each vtnode models a part of the transformation, and is supposed
//   to represent the output C2 nodes after the vectorization as closely
//   as possible.
//
// This is the life-cycle of a VTransform:
// - Construction:
//   - From SuperWord, with the SuperWordVTransformBuilder.
//
// - Optimize:
//   - Move non-strict order reductions out of the loop
//
// - Schedule:
//   - Compute linearization of the VTransformGraph, into an order that respects
//     all edges in the graph (bailout if cycle detected).
//
// - Cost-Model:
//   - We use a cost-model as a heuristic to determine if vectorization is profitable.
//     Compute the cost of the loop with and without vectorization.
//
// - Apply:
//   - Changes to the C2 IR are only made once the "apply" method is called.
//   - Each vtnode generates its corresponding scalar and vector C2 nodes,
//     possibly replacing old scalar C2 nodes.
//
// Future Plans with VTransform:
// - Pack/Unpack/Shuffle: introduce additional nodes not present in the scalar loop.
//                        This is difficult to do with the SuperWord packset approach.
// - If-conversion: convert predicated nodes into CFG.

typedef int VTransformNodeIDX;
class VTransform;
class VTransformNode;
class VTransformScalarNode;
class VTransformInputScalarNode;
class VTransformOutputScalarNode;
class VTransformLoopPhiNode;
class VTransformVectorNode;
class VTransformElementWiseVectorNode;
class VTransformCmpVectorNode;
class VTransformBoolVectorNode;
class VTransformReductionVectorNode;
class VTransformMemVectorNode;

// Result from VTransformNode::apply
class VTransformApplyResult {
private:
  Node* const _node;
  const uint _vector_length; // number of elements
  const uint _vector_width;  // total width in bytes

  VTransformApplyResult(Node* n, uint vector_length, uint vector_width) :
    _node(n),
    _vector_length(vector_length),
    _vector_width(vector_width) {}

public:
  static VTransformApplyResult make_scalar(Node* n) {
    return VTransformApplyResult(n, 0, 0);
  }

  static VTransformApplyResult make_vector(Node* n, uint vector_length, uint vector_width) {
    assert(vector_length > 0 && vector_width > 0, "must have nonzero size");
    return VTransformApplyResult(n, vector_length, vector_width);
  }

  static VTransformApplyResult make_empty() {
    return VTransformApplyResult(nullptr, 0, 0);
  }

  Node* node() const { return _node; }
  uint vector_length() const { return _vector_length; }
  uint vector_width() const { return _vector_width; }
  NOT_PRODUCT( void trace(VTransformNode* vtnode) const; )
};

#ifndef PRODUCT
// Convenience class for tracing flags.
class VTransformTrace {
public:
  const bool _verbose;
  const bool _rejections;
  const bool _align_vector;
  const bool _info;

  VTransformTrace(const VTrace& vtrace,
                  const bool is_trace_rejections,
                  const bool is_trace_align_vector,
                  const bool is_trace_info) :
    _verbose     (vtrace.is_trace(TraceAutoVectorizationTag::ALL)),
    _rejections  (_verbose | is_trace_vtransform(vtrace) | is_trace_rejections),
    _align_vector(_verbose | is_trace_vtransform(vtrace) | is_trace_align_vector),
    _info        (_verbose | is_trace_vtransform(vtrace) | is_trace_info) {}

  static bool is_trace_vtransform(const VTrace& vtrace) {
    return vtrace.is_trace(TraceAutoVectorizationTag::VTRANSFORM);
  }
};
#endif

// VTransformGraph: component of VTransform
// See description at top of this file.
class VTransformGraph : public StackObj {
private:
  const VLoopAnalyzer& _vloop_analyzer;
  const VLoop& _vloop;

  NOT_PRODUCT(const VTransformTrace _trace;)

  VTransformNodeIDX _next_idx;
  GrowableArray<VTransformNode*> _vtnodes;

  // Schedule (linearization) of the graph. We use this to reorder the memory graph
  // before inserting vector operations.
  GrowableArray<VTransformNode*> _schedule;

public:
  VTransformGraph(const VLoopAnalyzer& vloop_analyzer,
                  Arena& arena
                  NOT_PRODUCT( COMMA const VTransformTrace trace)) :
    _vloop_analyzer(vloop_analyzer),
    _vloop(vloop_analyzer.vloop()),
    NOT_PRODUCT(_trace(trace) COMMA)
    _next_idx(0),
    _vtnodes(&arena, _vloop.estimated_body_length(), 0, nullptr),
    _schedule(&arena, _vloop.estimated_body_length(), 0, nullptr) {}

  VTransformNodeIDX new_idx() { return _next_idx++; }
  void add_vtnode(VTransformNode* vtnode);
  DEBUG_ONLY( bool is_empty() const { return _vtnodes.is_empty(); } )
  DEBUG_ONLY( bool is_scheduled() const { return _schedule.is_nonempty(); } )
  const GrowableArray<VTransformNode*>& vtnodes() const { return _vtnodes; }

  void optimize(VTransform& vtransform);
  bool schedule();
  float cost() const;
  void apply_vectorization_for_each_vtnode(uint& max_vector_length, uint& max_vector_width) const;

private:
  // VLoop accessors
  PhaseIdealLoop* phase()     const { return _vloop.phase(); }
  PhaseIterGVN& igvn()        const { return _vloop.phase()->igvn(); }
  bool in_bb(const Node* n)   const { return _vloop.in_bb(n); }

  void collect_nodes_without_req_or_dependency(GrowableArray<VTransformNode*>& stack) const;
  int count_alive_vtnodes() const;

  void mark_vtnodes_in_loop(VectorSet& in_loop) const;

#ifndef PRODUCT
  void print_vtnodes() const;
  void print_schedule() const;
  void trace_schedule_cycle(const GrowableArray<VTransformNode*>& stack,
                            const VectorSet& pre_visited,
                            const VectorSet& post_visited) const;
#endif
};

// VTransform: models the transformation of the scalar loop to vectorized loop.
// It is a "C2 subgraph" to "C2 subgraph" mapping.
// See description at top of this file.
class VTransform : public StackObj {
private:
  const VLoopAnalyzer& _vloop_analyzer;
  const VLoop& _vloop;

  NOT_PRODUCT(const VTransformTrace _trace;)

  // Everything in the vtransform is allocated from this arena, including all vtnodes.
  Arena _arena;

  VTransformGraph _graph;

  // VPointer, and the alignment width (aw) for which we align the main-loop,
  // by adjusting the pre-loop limit.
  VPointer const* _vpointer_for_main_loop_alignment;
  int _aw_for_main_loop_alignment;

public:
  VTransform(const VLoopAnalyzer& vloop_analyzer,
             VPointer const* vpointer_for_main_loop_alignment,
             int aw_for_main_loop_alignment
             NOT_PRODUCT( COMMA const VTransformTrace trace)
             ) :
    _vloop_analyzer(vloop_analyzer),
    _vloop(vloop_analyzer.vloop()),
    NOT_PRODUCT(_trace(trace) COMMA)
    _arena(mtCompiler),
    _graph(_vloop_analyzer, _arena NOT_PRODUCT(COMMA _trace)),
    _vpointer_for_main_loop_alignment(vpointer_for_main_loop_alignment),
    _aw_for_main_loop_alignment(aw_for_main_loop_alignment) {}

  const VLoopAnalyzer& vloop_analyzer() const { return _vloop_analyzer; }
  const VLoop& vloop() const { return _vloop; }
  Arena* arena() { return &_arena; }
  DEBUG_ONLY( bool has_graph() const { return !_graph.is_empty(); } )
  VTransformGraph& graph() { return _graph; }

  void optimize() { return _graph.optimize(*this); }
  bool schedule() { return _graph.schedule(); }
  float cost() const { return _graph.cost(); }
  void apply();

private:
  // VLoop accessors
  PhaseIdealLoop* phase()     const { return _vloop.phase(); }
  PhaseIterGVN& igvn()        const { return _vloop.phase()->igvn(); }
  IdealLoopTree* lpt()        const { return _vloop.lpt(); }
  CountedLoopNode* cl()       const { return _vloop.cl(); }
  int iv_stride()             const { return cl()->stride_con(); }

  // VLoopVPointers accessors
  const VPointer& vpointer(const MemNode* mem) const {
    return _vloop_analyzer.vpointers().vpointer(mem);
  }

  // Ensure that the main loop vectors are aligned by adjusting the pre loop limit.
  void determine_mem_ref_and_aw_for_main_loop_alignment();
  void adjust_pre_loop_limit_to_align_main_loop_vectors();

  void apply_vectorization() const;
};

// During "VTransform::apply", we keep track of the already transformed nodes, as
// well as the current memory states.
class VTransformApplyState : public StackObj {
private:
  const VLoopAnalyzer& _vloop_analyzer;

  // We keep track of the resulting Nodes from every "VTransformNode::apply" call.
  // Since "apply" is called on defs before uses, this allows us to find the
  // generated def (input) nodes when we are generating the use nodes in "apply".
  GrowableArray<Node*> _vtnode_idx_to_transformed_node;

  // We keep track of the current memory state in each slice. If the slice has only
  // loads (and no phi), then this is always the input memory state from before the
  // loop. If there is a memory phi, this is initially the memory phi, and each time
  // a store is processed, it is updated to that store.
  GrowableArray<Node*> _memory_states;

  // Class to denote which use after the loop depends on the last memory
  // state in the loop.
  // use->in(in_idx) = <last memory state in loop of slice alias_idx>
  class MemoryStateUseAfterLoop : public StackObj {
  public:
    Node* _use;
    int _in_idx;
    int _alias_idx;

    MemoryStateUseAfterLoop(Node* use, int in_idx, int alias_idx) :
      _use(use), _in_idx(in_idx), _alias_idx(alias_idx) {}
    MemoryStateUseAfterLoop() : MemoryStateUseAfterLoop(nullptr, 0, 0) {}
  };

  GrowableArray<MemoryStateUseAfterLoop> _memory_state_uses_after_loop;

public:
  VTransformApplyState(const VLoopAnalyzer& vloop_analyzer, int num_vtnodes) :
    _vloop_analyzer(vloop_analyzer),
    _vtnode_idx_to_transformed_node(num_vtnodes, num_vtnodes, nullptr),
    _memory_states(num_slices(), num_slices(), nullptr)
  {
    init_memory_states();
  }

  const VLoop& vloop() const { return _vloop_analyzer.vloop(); }
  PhaseIdealLoop* phase() const { return vloop().phase(); }

  void set_transformed_node(VTransformNode* vtn, Node* n);
  Node* transformed_node(const VTransformNode* vtn) const;

  Node* memory_state(int alias_idx) const { return _memory_states.at(alias_idx); }
  void set_memory_state(int alias_idx, Node* n) { _memory_states.at_put(alias_idx, n); }

  Node* memory_state(const TypePtr* adr_type) const {
    int alias_idx = phase()->C->get_alias_index(adr_type);
    return memory_state(alias_idx);
  }

  void set_memory_state(const TypePtr* adr_type, Node* n) {
    int alias_idx = phase()->C->get_alias_index(adr_type);
    return set_memory_state(alias_idx, n);
  }

  void fix_memory_state_uses_after_loop();

private:
  int num_slices() const { return _vloop_analyzer.memory_slices().heads().length(); }
  void init_memory_states();
};

// Bundle information for VTransformNode.
class VTransformNodePrototype : public StackObj {
private:
  Node* _approximate_origin; // for proper propagation of node notes
  int _scalar_opcode;
  uint _vector_length;
  BasicType _element_basic_type;
  const TypePtr* _adr_type; // carries slice information for memory ops (load, store, phi)

public:
  VTransformNodePrototype(Node* approximate_origin,
                          int scalar_opcode,
                          uint vector_length,
                          BasicType element_basic_type,
                          const TypePtr* adr_type) :
    _approximate_origin(approximate_origin),
    _scalar_opcode(scalar_opcode),
    _vector_length(vector_length),
    _element_basic_type(element_basic_type),
    _adr_type(adr_type) {}

  static VTransformNodePrototype make_from_scalar(Node* n, const VLoopAnalyzer& vloop_analyzer) {
    int opc = n->Opcode();
    int vlen = 1;
    BasicType bt = vloop_analyzer.vloop().in_bb(n) ? vloop_analyzer.types().velt_basic_type(n)
                                                   : n->bottom_type()->basic_type();
    const TypePtr* adr_type = n->adr_type();
    return VTransformNodePrototype(n, opc, vlen, bt, adr_type);
  }

  static VTransformNodePrototype make_from_pack(const Node_List* pack, const VLoopAnalyzer& vloop_analyzer) {
    Node* first = pack->at(0);
    int opc = first->Opcode();
    int vlen = pack->size();
    BasicType bt = vloop_analyzer.types().velt_basic_type(first);
    const TypePtr* adr_type = first->adr_type();
    return VTransformNodePrototype(first, opc, vlen, bt, adr_type);
  }

  Node* approximate_origin() const { return _approximate_origin; }
  int scalar_opcode() const { return _scalar_opcode; }
  uint vector_length() const { return _vector_length; }
  BasicType element_basic_type() const { return _element_basic_type; }
  const TypePtr* adr_type() const { return _adr_type; }
};

// The vtnodes (VTransformNode) resemble the C2 IR Nodes, and model a part of the
// VTransform. Many such vtnodes make up the VTransformGraph. The vtnodes represent
// the resulting scalar and vector nodes as closely as possible.
// See description at top of this file.
class VTransformNode : public ArenaObj {
public:
  const VTransformNodeIDX _idx;

private:
  bool _is_alive;

  VTransformNodePrototype _prototype;

  // _in is split into required inputs (_req), and additional dependencies.
  const uint _req;
  GrowableArray<VTransformNode*> _in;
  GrowableArray<VTransformNode*> _out;

public:
  VTransformNode(VTransform& vtransform, VTransformNodePrototype prototype, const uint req) :
    _idx(vtransform.graph().new_idx()),
    _is_alive(true),
    _prototype(prototype),
    _req(req),
    _in(vtransform.arena(),  req, req, nullptr),
    _out(vtransform.arena(), 4, 0, nullptr)
  {
    vtransform.graph().add_vtnode(this);
  }

  VTransformNodePrototype prototype() const { return _prototype; }
  Node* approximate_origin() const { return _prototype.approximate_origin(); }
  int scalar_opcode() const { return _prototype.scalar_opcode(); }
  uint vector_length() const { return _prototype.vector_length(); }
  BasicType element_basic_type() const { return _prototype.element_basic_type(); }
  const TypePtr* adr_type() const { return _prototype.adr_type(); }

  void init_req(uint i, VTransformNode* n) {
    assert(i < _req, "must be a req");
    assert(_in.at(i) == nullptr && n != nullptr, "only set once");
    _in.at_put(i, n);
    n->add_out(this);
  }

  void set_req(uint i, VTransformNode* n) {
    assert(i < _req, "must be a req");
    VTransformNode* old = _in.at(i);
    if (old != nullptr) { old->del_out(this); }
    _in.at_put(i, n);
    if (n != nullptr) { n->add_out(this); }
  }

  void swap_req(uint i, uint j) {
    assert(i < _req, "must be a req");
    assert(j < _req, "must be a req");
    VTransformNode* tmp = _in.at(i);
    _in.at_put(i, _in.at(j));
    _in.at_put(j, tmp);
  }

  void add_dependency(VTransformNode* n) {
    assert(n != nullptr, "no need to add nullptr");
    _in.push(n);
    n->add_out(this);
  }

  void add_out(VTransformNode* n) {
    _out.push(n);
  }

  void replace_by(VTransformNode* n) {
    while(outs() > 0) {
      VTransformNode* use = out(outs() - 1);
      for (uint i = 0; i < use->req(); i++) {
        if (use->in(i) == this) {
          use->set_req(i, n);
        }
      }
    }
  }

  void del_out(VTransformNode* n) {
    VTransformNode* last = _out.top();
    int i = _out.find(n);
    _out.delete_at(i); // replace with last
  }

  uint req() const { return _req; }
  VTransformNode* in(int i) const { return _in.at(i); }
  int outs() const { return _out.length(); }
  VTransformNode* out(int i) const { return _out.at(i); }

  VTransformNode* unique_out() const {
    assert(outs() == 1, "must be unique");
    return _out.at(0);
  }

  bool has_req_or_dependency() const {
    for (int i = 0; i < _in.length(); i++) {
      if (_in.at(i) != nullptr) { return true; }
    }
    return false;
  }

  bool is_alive() const { return _is_alive; }

  void mark_dead() {
    _is_alive = false;
    // Remove all inputs
    for (uint i = 0; i < req(); i++) {
      set_req(i, nullptr);
    }
  }

  virtual VTransformScalarNode* isa_Scalar() { return nullptr; }
  virtual VTransformInputScalarNode* isa_InputScalar() { return nullptr; }
  virtual VTransformOutputScalarNode* isa_OutputScalar() { return nullptr; }
  virtual VTransformLoopPhiNode* isa_LoopPhi() { return nullptr; }
  virtual const VTransformLoopPhiNode* isa_LoopPhi() const { return nullptr; }
  virtual VTransformVectorNode* isa_Vector() { return nullptr; }
  virtual VTransformElementWiseVectorNode* isa_ElementWiseVector() { return nullptr; }
  virtual VTransformCmpVectorNode* isa_CmpVector() { return nullptr; }
  virtual VTransformBoolVectorNode* isa_BoolVector() { return nullptr; }
  virtual VTransformReductionVectorNode* isa_ReductionVector() { return nullptr; }
  virtual VTransformMemVectorNode* isa_MemVector() { return nullptr; }
  virtual bool is_load_or_store_in_loop() const { return false; }

  virtual bool optimize(const VLoopAnalyzer& vloop_analyzer, VTransform& vtransform) { return false; }
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const = 0;

  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const = 0;
  virtual void apply_cleanup(VTransformApplyState& apply_state) const {};

  void register_new_node_from_vectorization(VTransformApplyState& apply_state, Node* vn) const;

  NOT_PRODUCT(virtual const char* name() const = 0;)
  NOT_PRODUCT(void print() const;)
  NOT_PRODUCT(virtual void print_spec() const {};)
  NOT_PRODUCT(static void print_node_idx(const VTransformNode* vtn);)
};

// Identity transform for scalar nodes.
class VTransformScalarNode : public VTransformNode {
private:
  Node* _node;
public:
  VTransformScalarNode(VTransform& vtransform, VTransformNodePrototype prototype, Node* n) :
    VTransformNode(vtransform, prototype, n->req()), _node(n) {}
  Node* node() const { return _node; }
  virtual VTransformScalarNode* isa_Scalar() override { return this; }
  virtual bool is_load_or_store_in_loop() const override { return _node->is_Load() || _node->is_Store(); }
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "Scalar"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Wrapper node for nodes outside the loop that are inputs to nodes in the loop.
// Since we want the loop-internal nodes to be able to reference all inputs as vtnodes,
// we must wrap the inputs that are outside the loop into special vtnodes, too.
class VTransformInputScalarNode : public VTransformScalarNode {
public:
  VTransformInputScalarNode(VTransform& vtransform, VTransformNodePrototype prototype, Node* n) :
    VTransformScalarNode(vtransform, prototype, n) {}
  virtual VTransformInputScalarNode* isa_InputScalar() override { return this; }
  virtual bool is_load_or_store_in_loop() const override { return false; }
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override { ShouldNotReachHere(); }
  NOT_PRODUCT(virtual const char* name() const override { return "InputScalar"; };)
};

// Wrapper node for nodes outside the loop that are outputs from nodes in the loop.
// Since we want the loop-internal nodes to be able to reference all inputs as vtnodes,
// we must wrap the outputs that are outside the loop into special vtnodes, too.
class VTransformOutputScalarNode : public VTransformScalarNode {
public:
  VTransformOutputScalarNode(VTransform& vtransform, VTransformNodePrototype prototype, Node* n) :
    VTransformScalarNode(vtransform, prototype, n) {}
  virtual VTransformOutputScalarNode* isa_OutputScalar() override { return this; }
  virtual bool is_load_or_store_in_loop() const override { return false; }
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override { ShouldNotReachHere(); };
  NOT_PRODUCT(virtual const char* name() const override { return "OutputScalar"; };)
};

// We want to be able to conveniently find all Phis that belong to the LoopNode.
class VTransformLoopPhiNode : public VTransformScalarNode {
public:
  VTransformLoopPhiNode(VTransform& vtransform, VTransformNodePrototype prototype, PhiNode* n) :
    VTransformScalarNode(vtransform, prototype, n) {}
  virtual VTransformLoopPhiNode* isa_LoopPhi() override { return this; }
  virtual const VTransformLoopPhiNode* isa_LoopPhi() const override { return this; }
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  virtual void apply_cleanup(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "LoopPhi"; };)
};

// Base class for all vector vtnodes, i.e. vtnodes with a vector output.
class VTransformVectorNode : public VTransformNode {
public:
  VTransformVectorNode(VTransform& vtransform, VTransformNodePrototype prototype, const uint req) :
    VTransformNode(vtransform, prototype, req) {}

  virtual VTransformVectorNode* isa_Vector() override { return this; }
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Transform produces a ReplicateNode, replicating the input to all vector lanes.
class VTransformReplicateNode : public VTransformNode {
public:
  VTransformReplicateNode(VTransform& vtransform, VTransformNodePrototype prototype) :
    VTransformNode(vtransform, prototype, 2) {}
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "Replicate"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Transform introduces a scalar ConvI2LNode that was not previously in the C2 graph.
class VTransformConvI2LNode : public VTransformNode {
public:
  VTransformConvI2LNode(VTransform& vtransform, VTransformNodePrototype prototype) : VTransformNode(vtransform, prototype, 2) {}
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "ConvI2L"; };)
};

// Transform introduces a shift-count node that truncates the shift count for a vector shift.
class VTransformShiftCountNode : public VTransformNode {
private:
  juint _mask;

public:
  VTransformShiftCountNode(VTransform& vtransform, VTransformNodePrototype prototype, juint mask) :
    VTransformNode(vtransform, prototype, 2), _mask(mask) {}
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "ShiftCount"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Transform introduces a PopulateIndex node: [phi, phi+1, phi+2, phi+3, ...].
class VTransformPopulateIndexNode : public VTransformNode {
public:
  VTransformPopulateIndexNode(VTransform& vtransform, VTransformNodePrototype prototype) :
    VTransformNode(vtransform, prototype, 2) {}
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "PopulateIndex"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Simple element-wise vectors.
class VTransformElementWiseVectorNode : public VTransformVectorNode {
private:
  const int _vector_opcode;

public:
  VTransformElementWiseVectorNode(VTransform& vtransform, VTransformNodePrototype prototype, uint req, const int vector_opcode) :
    VTransformVectorNode(vtransform, prototype, req), _vector_opcode(vector_opcode) {}
  virtual VTransformElementWiseVectorNode* isa_ElementWiseVector() override { return this; }
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "ElementWiseVector"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// The scalar operation was a long -> int operation.
// However, the vector operation is long -> long.
// Hence, we lower the node to: long --long_op--> long --cast--> int
class VTransformLongToIntVectorNode : public VTransformVectorNode {
public:
  VTransformLongToIntVectorNode(VTransform& vtransform, VTransformNodePrototype prototype, uint req) :
    VTransformVectorNode(vtransform, prototype, req) {}
  virtual bool optimize(const VLoopAnalyzer& vloop_analyzer, VTransform& vtransform) override;
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override { ShouldNotReachHere(); }
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override { ShouldNotReachHere(); }
  NOT_PRODUCT(virtual const char* name() const override { return "LongToIntVector"; };)
};

struct VTransformBoolTest {
  const BoolTest::mask _mask;
  const bool _is_negated;

  VTransformBoolTest(const BoolTest::mask mask, bool is_negated) :
    _mask(mask), _is_negated(is_negated) {}
};

// Cmp + Bool -> VectorMaskCmp
// The Bool node takes care of "cost" and "apply".
class VTransformCmpVectorNode : public VTransformVectorNode {
public:
  VTransformCmpVectorNode(VTransform& vtransform, VTransformNodePrototype prototype, uint req) :
    VTransformVectorNode(vtransform, prototype, req) {}
  virtual VTransformCmpVectorNode* isa_CmpVector() override { return this; }
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override { return 0; }
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override { return VTransformApplyResult::make_empty(); }
  NOT_PRODUCT(virtual const char* name() const override { return "CmpVector"; };)
};

class VTransformBoolVectorNode : public VTransformVectorNode {
private:
  const VTransformBoolTest _test;
public:
  VTransformBoolVectorNode(VTransform& vtransform, VTransformNodePrototype prototype, VTransformBoolTest test) :
    VTransformVectorNode(vtransform, prototype, 2), _test(test) {}
  VTransformBoolTest test() const { return _test; }
  virtual VTransformBoolVectorNode* isa_BoolVector() override { return this; }
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "BoolVector"; };)
};

class VTransformReductionVectorNode : public VTransformVectorNode {
public:
  // req = 3 -> [ctrl, scalar init, vector]
  VTransformReductionVectorNode(VTransform& vtransform, VTransformNodePrototype prototype) :
    VTransformVectorNode(vtransform, prototype, 3) {}
  virtual VTransformReductionVectorNode* isa_ReductionVector() override { return this; }
  virtual bool optimize(const VLoopAnalyzer& vloop_analyzer, VTransform& vtransform) override;
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "ReductionVector"; };)

private:
  int vector_reduction_opcode() const;
  bool requires_strict_order() const;
  bool optimize_move_non_strict_order_reductions_out_of_loop(const VLoopAnalyzer& vloop_analyzer, VTransform& vtransform);
};

class VTransformMemVectorNode : public VTransformVectorNode {
private:
  VPointer const* _vpointer;

public:
  VTransformMemVectorNode(VTransform& vtransform, VTransformNodePrototype prototype, const uint req, const VPointer* vpointer) :
    VTransformVectorNode(vtransform, prototype, req), _vpointer(vpointer) {}
  virtual VTransformMemVectorNode* isa_MemVector() override { return this; }
  virtual bool is_load_or_store_in_loop() const override { return true; }
  VPointer const* vpointer() const { return _vpointer; }
};

class VTransformLoadVectorNode : public VTransformMemVectorNode {
private:
  const LoadNode::ControlDependency _control_dependency;

  GrowableArray<Node*> _nodes;
public:
  // req = 3 -> [ctrl, mem, adr]
  VTransformLoadVectorNode(VTransform& vtransform, VTransformNodePrototype prototype, const VPointer* vpointer, const LoadNode::ControlDependency control_dependency) :
    VTransformMemVectorNode(vtransform, prototype, 3, vpointer),
    _control_dependency(control_dependency),
    _nodes(vtransform.arena(),
           vector_length(),
           vector_length(),
	   nullptr) {}
  LoadNode::ControlDependency control_dependency() const;

  void set_nodes(const Node_List* pack) {
    assert(pack->size() == vector_length(), "must have same length");
    for (uint k = 0; k < pack->size(); k++) {
      _nodes.at_put(k, pack->at(k));
    }
  }

  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "LoadVector"; };)
};

class VTransformStoreVectorNode : public VTransformMemVectorNode {
public:
  // req = 4 -> [ctrl, mem, adr, val]
  VTransformStoreVectorNode(VTransform& vtransform, VTransformNodePrototype prototype, const VPointer* vpointer) :
    VTransformMemVectorNode(vtransform, prototype, 4, vpointer) {}
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "StoreVector"; };)
};

#endif // SHARE_OPTO_VTRANSFORM_HPP
