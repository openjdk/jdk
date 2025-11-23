/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/vectornode.hpp"

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
//   - From SuperWord PackSet, with the SuperWordVTransformBuilder.
//
// - Optimize:
//   - Move non-strict order reductions out of the loop. This means we have
//     only element-wise operations inside the loop, rather than the much
//     more expensive lane-crossing reductions. We need to do this before
//     assessing profitability with the cost-model.
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
//   - Align the main loop, by adjusting pre loop limit.
//   - Add speculative runtime checks (alignment and aliasing).
//   - Each vtnode generates its corresponding scalar and vector C2 nodes,
//     possibly replacing old scalar C2 nodes. We apply each vtnode in order
//     of the schedule, so that all input vtnodes are already applied, i.e.
//     all input vtnodes have already generated the transformed C2 nodes.
//   - We also build the new memory graph on the fly. The schedule may have
//     reordered the memory operations, and so we cannot use the old memory
//     graph, but must build it from the scheduled order. We keep track of
//     the current memory state in VTransformApplyState.
//
// Future Plans with VTransform:
// - Cost model: estimate if vectorization is profitable.
// - Pack/Unpack/Shuffle: introduce additional nodes not present in the scalar loop.
//                        This is difficult to do with the SuperWord packset approach.
// - If-conversion: convert predicated nodes into CFG.

typedef int VTransformNodeIDX;
class VTransform;
class VTransformNode;
class VTransformMemopScalarNode;
class VTransformDataScalarNode;
class VTransformPhiScalarNode;
class VTransformCFGNode;
class VTransformCountedLoopNode;
class VTransformOuterNode;
class VTransformVectorNode;
class VTransformElementWiseVectorNode;
class VTransformCmpVectorNode;
class VTransformBoolVectorNode;
class VTransformReductionVectorNode;
class VTransformPhiVectorNode;
class VTransformMemVectorNode;
class VTransformLoadVectorNode;
class VTransformStoreVectorNode;

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

  static VTransformApplyResult make_vector(VectorNode* vn) {
    return VTransformApplyResult(vn, vn->length(), vn->length_in_bytes());
  }

  static VTransformApplyResult make_vector(Node* n, const TypeVect* vt) {
    return VTransformApplyResult(n, vt->length(), vt->length_in_bytes());
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
  const bool _speculative_aliasing_analysis;
  const bool _speculative_runtime_checks;
  const bool _info;

  VTransformTrace(const VTrace& vtrace,
                  const bool is_trace_rejections,
                  const bool is_trace_align_vector,
                  const bool is_trace_speculative_aliasing_analysis,
                  const bool is_trace_speculative_runtime_checks,
                  const bool is_trace_info) :
    _verbose                   (vtrace.is_trace(TraceAutoVectorizationTag::ALL)),
    _rejections                    (_verbose | is_trace_vtransform(vtrace) | is_trace_rejections),
    _align_vector                  (_verbose | is_trace_vtransform(vtrace) | is_trace_align_vector),
    _speculative_aliasing_analysis (_verbose | is_trace_vtransform(vtrace) | is_trace_speculative_aliasing_analysis),
    _speculative_runtime_checks    (_verbose | is_trace_vtransform(vtrace) | is_trace_speculative_runtime_checks),
    _info                          (_verbose | is_trace_vtransform(vtrace) | is_trace_info) {}

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
  const GrowableArray<VTransformNode*>& get_schedule() const { return _schedule; }

  void optimize(VTransform& vtransform);
  bool schedule();
  bool has_store_to_load_forwarding_failure(const VLoopAnalyzer& vloop_analyzer) const;
  float cost_for_vector_loop() const;
  void apply_vectorization_for_each_vtnode(uint& max_vector_length, uint& max_vector_width) const;

private:
  // VLoop accessors
  PhaseIdealLoop* phase()     const { return _vloop.phase(); }
  PhaseIterGVN& igvn()        const { return _vloop.phase()->igvn(); }
  bool in_bb(const Node* n)   const { return _vloop.in_bb(n); }

  void collect_nodes_without_strong_in_edges(GrowableArray<VTransformNode*>& stack) const;
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
    _arena(mtCompiler, Arena::Tag::tag_superword),
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
  bool is_profitable() const;
  float cost_for_vector_loop() const { return _graph.cost_for_vector_loop(); }
  bool has_store_to_load_forwarding_failure() const { return _graph.has_store_to_load_forwarding_failure(_vloop_analyzer); }
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
  void determine_vpointer_and_aw_for_main_loop_alignment();
  void adjust_pre_loop_limit_to_align_main_loop_vectors();

  void apply_speculative_alignment_runtime_checks();
  void apply_speculative_aliasing_runtime_checks();
  void add_speculative_alignment_check(Node* node, juint alignment);

  template<typename Callback>
  void add_speculative_check(Callback callback);

  void apply_vectorization() const;
};

// Keeps track of the state during "VTransform::apply"
// -> keep track of the already transformed nodes and the memory state.
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

  // We need to keep track of the memory uses after the loop, for the slices that
  // have a memory phi.
  //   use->in(in_idx) = <last memory state in loop of slice alias_idx>
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
    init_memory_states_and_uses_after_loop();
  }

  const VLoop& vloop() const { return _vloop_analyzer.vloop(); }
  PhaseIdealLoop* phase() const { return vloop().phase(); }
  const VLoopAnalyzer& vloop_analyzer() const { return _vloop_analyzer; }

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
  void init_memory_states_and_uses_after_loop();
};

// The vtnodes (VTransformNode) resemble the C2 IR Nodes, and model a part of the
// VTransform. Many such vtnodes make up the VTransformGraph. The vtnodes represent
// the resulting scalar and vector nodes as closely as possible.
// See description at top of this file.
//
// There are 3 tyes of edges:
// - data edges (req):           corresponding to C2 IR Node data edges, except control
//                               and memory.
// - strong memory edges:        memory edges that must be respected when scheduling.
// - weak memory edges:          memory edges that can be violated, but if violated then
//                               corresponding aliasing analysis runtime checks must be
//                               inserted.
//
// Strong edges: union of data edges and strong memory edges.
//               These must be respected by scheduling in all cases.
//
// The C2 IR Node memory edges essentially define a linear order of all memory operations
// (only Loads with the same memory input can be executed in an arbitrary order). This is
// efficient, because it means every Load and Store has exactly one input memory edge,
// which keeps the memory edge count linear. This is approach is too restrictive for
// vectorization, for example, we could never vectorize stores, since they are all in a
// dependency chain. Instead, we model the memory edges between all memory nodes, which
// could be quadratic in the worst case. For vectorization, we must essentially reorder the
// instructions in the graph. For this we must model all memory dependencies.
class VTransformNode : public ArenaObj {
public:
  const VTransformNodeIDX _idx;

private:
  bool _is_alive;

  // We split _in into 3 sections:
  // - data edges (req):     _in[0                           .. _req-1]
  // - strong memory edges:  _in[_req                        .. _in_end_strong_memory_edges-1]
  // - weak memory edges:    _in[_in_end_strong_memory_edges .. ]
  const uint _req;
  uint _in_end_strong_memory_edges;
  GrowableArray<VTransformNode*> _in;

  // We split _out into 2 sections:
  // - strong edges:         _out[0                     .. _out_end_strong_edges-1]
  // - weak memory edges:    _out[_out_end_strong_edges .. _len-1]
  uint _out_end_strong_edges;
  GrowableArray<VTransformNode*> _out;

public:
  VTransformNode(VTransform& vtransform, const uint req) :
    _idx(vtransform.graph().new_idx()),
    _is_alive(true),
    _req(req),
    _in_end_strong_memory_edges(req),
    _in(vtransform.arena(),  req, req, nullptr),
    _out_end_strong_edges(0),
    _out(vtransform.arena(), 4, 0, nullptr)
  {
    vtransform.graph().add_vtnode(this);
  }

  void init_req(uint i, VTransformNode* n) {
    assert(i < _req, "must be a req");
    assert(_in.at(i) == nullptr && n != nullptr, "only set once");
    _in.at_put(i, n);
    n->add_out_strong_edge(this);
  }

  void set_req(uint i, VTransformNode* n) {
    assert(i < _req, "must be a req");
    VTransformNode* old = _in.at(i);
    if (old != nullptr) { old->del_out_strong_edge(this); }
    _in.at_put(i, n);
    if (n != nullptr) { n->add_out_strong_edge(this); }
  }

  void swap_req(uint i, uint j) {
    assert(i < _req, "must be a req");
    assert(j < _req, "must be a req");
    VTransformNode* tmp = _in.at(i);
    _in.at_put(i, _in.at(j));
    _in.at_put(j, tmp);
  }

  void add_strong_memory_edge(VTransformNode* n) {
    assert(n != nullptr, "no need to add nullptr");
    if (_in_end_strong_memory_edges < (uint)_in.length()) {
      // Put n in place of first weak memory edge, and move
      // the weak memory edge to the end.
      VTransformNode* first_weak = _in.at(_in_end_strong_memory_edges);
      _in.at_put(_in_end_strong_memory_edges, n);
      _in.push(first_weak);
    } else {
      _in.push(n);
    }
    _in_end_strong_memory_edges++;
    n->add_out_strong_edge(this);
  }

  void add_weak_memory_edge(VTransformNode* n) {
    assert(n != nullptr, "no need to add nullptr");
    _in.push(n);
    n->add_out_weak_memory_edge(this);
  }

private:
  void add_out_strong_edge(VTransformNode* n) {
    if (_out_end_strong_edges < (uint)_out.length()) {
      // Put n in place of first weak memory edge, and move
      // the weak memory edge to the end.
      VTransformNode* first_weak = _out.at(_out_end_strong_edges);
      _out.at_put(_out_end_strong_edges, n);
      _out.push(first_weak);
    } else {
      _out.push(n);
    }
    _out_end_strong_edges++;
  }

  void add_out_weak_memory_edge(VTransformNode* n) {
    _out.push(n);
  }

  void del_out_strong_edge(VTransformNode* n) {
    int i = _out.find(n);
    assert(0 <= i && i < (int)_out_end_strong_edges, "must be in strong edges");

    // Replace n with the last strong edge.
    VTransformNode* last_strong = _out.at(_out_end_strong_edges - 1);
    _out.at_put(i, last_strong);

    if (_out_end_strong_edges < (uint)_out.length()) {
      // Now replace where last_strong was with the last weak edge.
      VTransformNode* last_weak = _out.top();
      _out.at_put(_out_end_strong_edges - 1, last_weak);
    }
    _out.pop();
    _out_end_strong_edges--;
  }

public:
  uint req() const { return _req; }
  uint out_strong_edges() const { return _out_end_strong_edges; }
  uint out_weak_edges() const { return _out.length() - _out_end_strong_edges; }

  VTransformNode* in_req(uint i) const {
    assert(i < _req, "must be a req");
    return _in.at(i);
  }

  VTransformNode* out_strong_edge(uint i) const {
    assert(i < out_strong_edges(), "must be a strong memory edge or data edge");
    return _out.at(i);
  }

  VTransformNode* out_weak_edge(uint i) const {
    assert(i < out_weak_edges(), "must be a strong memory edge");
    return _out.at(_out_end_strong_edges + i);
  }

  bool has_strong_in_edge() const {
    for (uint i = 0; i < _in_end_strong_memory_edges; i++) {
      if (_in.at(i) != nullptr) { return true; }
    }
    return false;
  }

  VTransformNode* unique_out_strong_edge() const {
    assert(out_strong_edges() == 1, "must be unique");
    return _out.at(0);
  }

  bool is_alive() const { return _is_alive; }

  void mark_dead() {
    _is_alive = false;
    // Remove all inputs
    for (uint i = 0; i < req(); i++) {
      set_req(i, nullptr);
    }
  }

  virtual VTransformMemopScalarNode* isa_MemopScalar() { return nullptr; }
  virtual VTransformPhiScalarNode* isa_PhiScalar() { return nullptr; }
  virtual VTransformCountedLoopNode* isa_CountedLoop() { return nullptr; }
  virtual VTransformOuterNode* isa_Outer() { return nullptr; }
  virtual VTransformVectorNode* isa_Vector() { return nullptr; }
  virtual VTransformElementWiseVectorNode* isa_ElementWiseVector() { return nullptr; }
  virtual VTransformCmpVectorNode* isa_CmpVector() { return nullptr; }
  virtual VTransformBoolVectorNode* isa_BoolVector() { return nullptr; }
  virtual VTransformReductionVectorNode* isa_ReductionVector() { return nullptr; }
  virtual VTransformPhiVectorNode* isa_PhiVector() { return nullptr; }
  virtual VTransformMemVectorNode* isa_MemVector() { return nullptr; }
  virtual VTransformLoadVectorNode* isa_LoadVector() { return nullptr; }
  virtual VTransformStoreVectorNode* isa_StoreVector() { return nullptr; }

  virtual bool is_load_in_loop() const { return false; }
  virtual bool is_load_or_store_in_loop() const { return false; }
  virtual const VPointer& vpointer() const { ShouldNotReachHere(); }
  virtual bool is_loop_head_phi() const { return false; }

  virtual bool optimize(const VLoopAnalyzer& vloop_analyzer, VTransform& vtransform) { return false; }

  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const = 0;

  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const = 0;
  virtual void apply_backedge(VTransformApplyState& apply_state) const {};
  void apply_vtn_inputs_to_node(Node* n, VTransformApplyState& apply_state) const;
  void register_new_node_from_vectorization(VTransformApplyState& apply_state, Node* vn) const;

  NOT_PRODUCT(virtual const char* name() const = 0;)
  NOT_PRODUCT(void print() const;)
  NOT_PRODUCT(virtual void print_spec() const {};)
  NOT_PRODUCT(static void print_node_idx(const VTransformNode* vtn);)
};

// Identity transform for scalar loads and stores.
class VTransformMemopScalarNode : public VTransformNode {
private:
  MemNode* _node;
  const VPointer _vpointer;
public:
  VTransformMemopScalarNode(VTransform& vtransform, MemNode* n, const VPointer& vpointer) :
    VTransformNode(vtransform, n->req()), _node(n), _vpointer(vpointer)
  {
    assert(node()->is_Load() || node()->is_Store(), "must be memop");
  }

  MemNode* node() const { return _node; }
  virtual VTransformMemopScalarNode* isa_MemopScalar() override { return this; }

  virtual bool is_load_in_loop() const override { return _node->is_Load(); }
  virtual bool is_load_or_store_in_loop() const override { return true; }

  virtual const VPointer& vpointer() const override { return _vpointer; }
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "MemopScalar"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Identity transform for scalar data nodes.
class VTransformDataScalarNode : public VTransformNode {
private:
  Node* _node;
public:
  VTransformDataScalarNode(VTransform& vtransform, Node* n) :
    VTransformNode(vtransform, n->req()), _node(n)
  {
    assert(!_node->is_Mem() && !_node->is_Phi() && !_node->is_CFG(), "must be data node: %s", _node->Name());
  }

  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "DataScalar"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Identity transform for loop head phi nodes.
class VTransformPhiScalarNode : public VTransformNode {
private:
  PhiNode* _node;
public:
  VTransformPhiScalarNode(VTransform& vtransform, PhiNode* n) :
    VTransformNode(vtransform, n->req()), _node(n)
  {
    assert(_node->in(0)->is_Loop(), "phi ctrl must be Loop: %s", _node->in(0)->Name());
  }

  PhiNode* node() const { return _node; }

  virtual VTransformPhiScalarNode* isa_PhiScalar() override { return this; }
  virtual bool is_loop_head_phi() const override { return in_req(0)->isa_CountedLoop() != nullptr; }
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override { return 0; }
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  virtual void apply_backedge(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "PhiScalar"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Identity transform for CFG nodes.
class VTransformCFGNode : public VTransformNode {
private:
  Node* _node;
public:
  VTransformCFGNode(VTransform& vtransform, Node* n) :
    VTransformNode(vtransform, n->req()), _node(n)
  {
    assert(_node->is_CFG(), "must be CFG node: %s", _node->Name());
  }

  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override { return 0; }
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "CFG"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Identity transform for CountedLoop, the only CFG node with a backedge.
class VTransformCountedLoopNode : public VTransformCFGNode {
public:
  VTransformCountedLoopNode(VTransform& vtransform, CountedLoopNode* n) :
    VTransformCFGNode(vtransform, n) {}

  virtual VTransformCountedLoopNode* isa_CountedLoop() override { return this; }
  NOT_PRODUCT(virtual const char* name() const override { return "CountedLoop"; };)
};

// Wrapper node for nodes outside the loop that are inputs to nodes in the loop.
// Since we want the loop-internal nodes to be able to reference all inputs as vtnodes,
// we must wrap the inputs that are outside the loop into special vtnodes, too.
class VTransformOuterNode : public VTransformNode {
private:
  Node* _node;
public:
  VTransformOuterNode(VTransform& vtransform, Node* n) :
    VTransformNode(vtransform, n->req()), _node(n) {}

  virtual VTransformOuterNode* isa_Outer() override { return this; }
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override { ShouldNotReachHere(); }
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "Outer"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Transform produces a ReplicateNode, replicating the input to all vector lanes.
class VTransformReplicateNode : public VTransformNode {
private:
  int _vlen;
  BasicType _element_type;
public:
  VTransformReplicateNode(VTransform& vtransform, int vlen, BasicType element_type) :
    VTransformNode(vtransform, 2), _vlen(vlen), _element_type(element_type) {}
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "Replicate"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Transform introduces a scalar ConvI2LNode that was not previously in the C2 graph.
class VTransformConvI2LNode : public VTransformNode {
public:
  VTransformConvI2LNode(VTransform& vtransform) : VTransformNode(vtransform, 2) {}
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "ConvI2L"; };)
};

// Transform introduces a shift-count node that truncates the shift count for a vector shift.
class VTransformShiftCountNode : public VTransformNode {
private:
  int _vlen;
  const BasicType _element_bt;
  juint _mask;
  int _shift_opcode;
public:
  VTransformShiftCountNode(VTransform& vtransform, int vlen, BasicType element_bt, juint mask, int shift_opcode) :
    VTransformNode(vtransform, 2), _vlen(vlen), _element_bt(element_bt), _mask(mask), _shift_opcode(shift_opcode) {}
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "ShiftCount"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Transform introduces a PopulateIndex node: [phi, phi+1, phi+2, phi+3, ...].
class VTransformPopulateIndexNode : public VTransformNode {
private:
  int _vlen;
  const BasicType _element_bt;
public:
  VTransformPopulateIndexNode(VTransform& vtransform, int vlen, const BasicType element_bt) :
    VTransformNode(vtransform, 2), _vlen(vlen), _element_bt(element_bt) {}
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "PopulateIndex"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Bundle the information needed for vector nodes.
class VTransformVectorNodeProperties : public StackObj {
private:
  Node* _approximate_origin; // for proper propagation of node notes
  const int _scalar_opcode;
  const uint _vector_length;
  const BasicType _element_basic_type;

  VTransformVectorNodeProperties(Node* approximate_origin,
                                 int scalar_opcode,
                                 uint vector_length,
                                 BasicType element_basic_type) :
    _approximate_origin(approximate_origin),
    _scalar_opcode(scalar_opcode),
    _vector_length(vector_length),
    _element_basic_type(element_basic_type) {}

public:
  static VTransformVectorNodeProperties make_from_pack(const Node_List* pack, const VLoopAnalyzer& vloop_analyzer) {
    Node* first = pack->at(0);
    int opc = first->Opcode();
    int vlen = pack->size();
    BasicType bt = vloop_analyzer.types().velt_basic_type(first);
    return VTransformVectorNodeProperties(first, opc, vlen, bt);
  }

  static VTransformVectorNodeProperties make_for_phi_vector(PhiNode* phi, int vlen, BasicType bt) {
    return VTransformVectorNodeProperties(phi, phi->Opcode(), vlen, bt);
  }

  Node* approximate_origin()     const { return _approximate_origin; }
  int scalar_opcode()            const { return _scalar_opcode; }
  uint vector_length()           const { return _vector_length; }
  BasicType element_basic_type() const { return _element_basic_type; }
};

// Abstract base class for all vector vtnodes.
class VTransformVectorNode : public VTransformNode {
private:
  const VTransformVectorNodeProperties _properties;
public:
  VTransformVectorNode(VTransform& vtransform, const uint req, const VTransformVectorNodeProperties properties) :
    VTransformNode(vtransform, req), _properties(properties) {}

  virtual VTransformVectorNode* isa_Vector() override { return this; }
  void register_new_node_from_vectorization_and_replace_scalar_nodes(VTransformApplyState& apply_state, Node* vn) const;
  NOT_PRODUCT(virtual void print_spec() const override;)

protected:
  const VTransformVectorNodeProperties& properties() const { return _properties; }
  Node* approximate_origin()     const { return _properties.approximate_origin(); }
  int scalar_opcode()            const { return _properties.scalar_opcode(); }
  uint vector_length()           const { return _properties.vector_length(); }
  BasicType element_basic_type() const { return _properties.element_basic_type(); }
};

// Catch all for all element-wise vector operations.
class VTransformElementWiseVectorNode : public VTransformVectorNode {
private:
  const int _vector_opcode;
public:
  VTransformElementWiseVectorNode(VTransform& vtransform, uint req, const VTransformVectorNodeProperties properties, const int vector_opcode) :
    VTransformVectorNode(vtransform, req, properties), _vector_opcode(vector_opcode) {}
  virtual VTransformElementWiseVectorNode* isa_ElementWiseVector() override { return this; }
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "ElementWiseVector"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// The scalar operation was a long -> int operation.
// However, the vector operation is long -> long.
// Hence, we vectorize it as: long --long_op--> long --cast--> int
class VTransformElementWiseLongOpWithCastToIntVectorNode : public VTransformVectorNode {
public:
  VTransformElementWiseLongOpWithCastToIntVectorNode(VTransform& vtransform, const VTransformVectorNodeProperties properties) :
    VTransformVectorNode(vtransform, 2, properties) {}
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "ElementWiseLongOpWithCastToIntVector"; };)
};

class VTransformReinterpretVectorNode : public VTransformVectorNode {
private:
  const BasicType _src_bt;
public:
  VTransformReinterpretVectorNode(VTransform& vtransform, const VTransformVectorNodeProperties properties, const BasicType src_bt) :
    VTransformVectorNode(vtransform, 2, properties), _src_bt(src_bt) {}
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "ReinterpretVector"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

struct VTransformBoolTest {
  const BoolTest::mask _mask;
  const bool _is_negated;

  VTransformBoolTest(const BoolTest::mask mask, bool is_negated) :
    _mask(mask), _is_negated(is_negated) {}
};

// Cmp + Bool -> VectorMaskCmp
// The Bool node takes care of "apply".
class VTransformCmpVectorNode : public VTransformVectorNode {
public:
  VTransformCmpVectorNode(VTransform& vtransform, const VTransformVectorNodeProperties properties) :
    VTransformVectorNode(vtransform, 3, properties) {}
  virtual VTransformCmpVectorNode* isa_CmpVector() override { return this; }
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override { return 0; }
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override { return VTransformApplyResult::make_empty(); }
  NOT_PRODUCT(virtual const char* name() const override { return "CmpVector"; };)
};

class VTransformBoolVectorNode : public VTransformVectorNode {
private:
  const VTransformBoolTest _test;
public:
  VTransformBoolVectorNode(VTransform& vtransform, const VTransformVectorNodeProperties properties, VTransformBoolTest test) :
    VTransformVectorNode(vtransform, 2, properties), _test(test) {}
  VTransformBoolTest test() const { return _test; }
  virtual VTransformBoolVectorNode* isa_BoolVector() override { return this; }
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "BoolVector"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

class VTransformReductionVectorNode : public VTransformVectorNode {
public:
  // req = 3 -> [ctrl, scalar init, vector]
  VTransformReductionVectorNode(VTransform& vtransform, const VTransformVectorNodeProperties properties) :
    VTransformVectorNode(vtransform, 3, properties) {}
  virtual VTransformReductionVectorNode* isa_ReductionVector() override { return this; }
  virtual bool optimize(const VLoopAnalyzer& vloop_analyzer, VTransform& vtransform) override;
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "ReductionVector"; };)

private:
  int vector_reduction_opcode() const;
  bool requires_strict_order() const;
  bool optimize_move_non_strict_order_reductions_out_of_loop_preconditions(VTransform& vtransform);
  bool optimize_move_non_strict_order_reductions_out_of_loop(const VLoopAnalyzer& vloop_analyzer, VTransform& vtransform);
};

class VTransformPhiVectorNode : public VTransformVectorNode {
public:
  VTransformPhiVectorNode(VTransform& vtransform, uint req, const VTransformVectorNodeProperties properties) :
    VTransformVectorNode(vtransform, req, properties) {}
  virtual VTransformPhiVectorNode* isa_PhiVector() override { return this; }
  virtual bool is_loop_head_phi() const override { return in_req(0)->isa_CountedLoop() != nullptr; }
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override { return 0; }
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  virtual void apply_backedge(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "PhiVector"; };)
};

class VTransformMemVectorNode : public VTransformVectorNode {
private:
  const VPointer _vpointer; // with size of the vector
protected:
  const TypePtr* _adr_type;

public:
  VTransformMemVectorNode(VTransform& vtransform, const uint req, const VTransformVectorNodeProperties properties, const VPointer& vpointer, const TypePtr* adr_type) :
    VTransformVectorNode(vtransform, req, properties),
    _vpointer(vpointer),
    _adr_type(adr_type) {}

  virtual VTransformMemVectorNode* isa_MemVector() override { return this; }
  virtual bool is_load_or_store_in_loop() const override { return true; }
  virtual const VPointer& vpointer() const override { return _vpointer; }
};

class VTransformLoadVectorNode : public VTransformMemVectorNode {
private:
  const LoadNode::ControlDependency _control_dependency;

public:
  // req = 3 -> [ctrl, mem, adr]
  VTransformLoadVectorNode(VTransform& vtransform,
                           const VTransformVectorNodeProperties properties,
                           const VPointer& vpointer,
                           const TypePtr* adr_type,
                           const LoadNode::ControlDependency control_dependency) :
    VTransformMemVectorNode(vtransform, 3, properties, vpointer, adr_type), _control_dependency(control_dependency) {}
  LoadNode::ControlDependency control_dependency() const;
  virtual VTransformLoadVectorNode* isa_LoadVector() override { return this; }
  virtual bool is_load_in_loop() const override { return true; }
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "LoadVector"; };)
};

class VTransformStoreVectorNode : public VTransformMemVectorNode {
public:
  // req = 4 -> [ctrl, mem, adr, val]
  VTransformStoreVectorNode(VTransform& vtransform, const VTransformVectorNodeProperties properties, const VPointer& vpointer, const TypePtr* adr_type) :
    VTransformMemVectorNode(vtransform, 4, properties, vpointer, adr_type) {}
  virtual VTransformStoreVectorNode* isa_StoreVector() override { return this; }
  virtual bool is_load_in_loop() const override { return false; }
  virtual float cost(const VLoopAnalyzer& vloop_analyzer) const override;
  virtual VTransformApplyResult apply(VTransformApplyState& apply_state) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "StoreVector"; };)
};
#endif // SHARE_OPTO_VTRANSFORM_HPP
