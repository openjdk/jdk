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
// - Future Plans: optimize, if-conversion, etc.
//
// - Schedule:
//   - Compute linearization of the VTransformGraph, into an order that respects
//     all edges in the graph (bailout if cycle detected).
//
// - Apply:
//   - Changes to the C2 IR are only made once the "apply" method is called.
//   - Each vtnode generates its corresponding scalar and vector C2 nodes,
//     possibly replacing old scalar C2 nodes.
//
// Future Plans with VTransform:
// - Cost model: estimate if vectorization is profitable.
// - Optimizations: moving unordered reductions out of the loop, whih decreases cost.
// - Pack/Unpack/Shuffle: introduce additional nodes not present in the scalar loop.
//                        This is difficult to do with the SuperWord packset approach.
// - If-conversion: convert predicated nodes into CFG.

typedef int VTransformNodeIDX;
class VTransformNode;
class VTransformScalarNode;
class VTransformInputScalarNode;
class VTransformVectorNode;
class VTransformElementWiseVectorNode;
class VTransformBoolVectorNode;
class VTransformReductionVectorNode;
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
  const bool _speculative_runtime_checks;
  const bool _info;

  VTransformTrace(const VTrace& vtrace,
                  const bool is_trace_rejections,
                  const bool is_trace_align_vector,
                  const bool is_trace_speculative_runtime_checks,
                  const bool is_trace_info) :
    _verbose                   (vtrace.is_trace(TraceAutoVectorizationTag::ALL)),
    _rejections                (_verbose | is_trace_vtransform(vtrace) | is_trace_rejections),
    _align_vector              (_verbose | is_trace_vtransform(vtrace) | is_trace_align_vector),
    _speculative_runtime_checks(_verbose | is_trace_vtransform(vtrace) | is_trace_speculative_runtime_checks),
    _info                      (_verbose | is_trace_vtransform(vtrace) | is_trace_info) {}

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

  bool schedule();
  bool has_store_to_load_forwarding_failure(const VLoopAnalyzer& vloop_analyzer) const;
  void apply_memops_reordering_with_schedule() const;
  void apply_vectorization_for_each_vtnode(uint& max_vector_length, uint& max_vector_width) const;

private:
  // VLoop accessors
  PhaseIdealLoop* phase()     const { return _vloop.phase(); }
  PhaseIterGVN& igvn()        const { return _vloop.phase()->igvn(); }
  bool in_bb(const Node* n)   const { return _vloop.in_bb(n); }

  void collect_nodes_without_req_or_dependency(GrowableArray<VTransformNode*>& stack) const;

  template<typename Callback>
  void for_each_memop_in_schedule(Callback callback) const;

#ifndef PRODUCT
  void print_vtnodes() const;
  void print_schedule() const;
  void print_memops_schedule() const;
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

  // Memory reference, and the alignment width (aw) for which we align the main-loop,
  // by adjusting the pre-loop limit.
  MemNode const* _mem_ref_for_main_loop_alignment;
  int _aw_for_main_loop_alignment;

public:
  VTransform(const VLoopAnalyzer& vloop_analyzer,
             MemNode const* mem_ref_for_main_loop_alignment,
             int aw_for_main_loop_alignment
             NOT_PRODUCT( COMMA const VTransformTrace trace)
             ) :
    _vloop_analyzer(vloop_analyzer),
    _vloop(vloop_analyzer.vloop()),
    NOT_PRODUCT(_trace(trace) COMMA)
    _arena(mtCompiler, Arena::Tag::tag_superword),
    _graph(_vloop_analyzer, _arena NOT_PRODUCT(COMMA _trace)),
    _mem_ref_for_main_loop_alignment(mem_ref_for_main_loop_alignment),
    _aw_for_main_loop_alignment(aw_for_main_loop_alignment) {}

  const VLoopAnalyzer& vloop_analyzer() const { return _vloop_analyzer; }
  Arena* arena() { return &_arena; }
  DEBUG_ONLY( bool has_graph() const { return !_graph.is_empty(); } )
  VTransformGraph& graph() { return _graph; }

  bool schedule() { return _graph.schedule(); }
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
  void determine_mem_ref_and_aw_for_main_loop_alignment();
  void adjust_pre_loop_limit_to_align_main_loop_vectors();

  void apply_speculative_runtime_checks();
  void add_speculative_alignment_check(Node* node, juint alignment);
  void add_speculative_check(BoolNode* bol);

  void apply_vectorization() const;
};

// The vtnodes (VTransformNode) resemble the C2 IR Nodes, and model a part of the
// VTransform. Many such vtnodes make up the VTransformGraph. The vtnodes represent
// the resulting scalar and vector nodes as closely as possible.
// See description at top of this file.
class VTransformNode : public ArenaObj {
public:
  const VTransformNodeIDX _idx;

private:
  // _in is split into required inputs (_req, i.e. all data dependencies),
  // and memory dependencies.
  const uint _req;
  GrowableArray<VTransformNode*> _in;
  GrowableArray<VTransformNode*> _out;

public:
  VTransformNode(VTransform& vtransform, const uint req) :
    _idx(vtransform.graph().new_idx()),
    _req(req),
    _in(vtransform.arena(),  req, req, nullptr),
    _out(vtransform.arena(), 4, 0, nullptr)
  {
    vtransform.graph().add_vtnode(this);
  }

  void set_req(uint i, VTransformNode* n) {
    assert(i < _req, "must be a req");
    assert(_in.at(i) == nullptr && n != nullptr, "only set once");
    _in.at_put(i, n);
    n->add_out(this);
  }

  void swap_req(uint i, uint j) {
    assert(i < _req, "must be a req");
    assert(j < _req, "must be a req");
    VTransformNode* tmp = _in.at(i);
    _in.at_put(i, _in.at(j));
    _in.at_put(j, tmp);
  }

  void add_memory_dependency(VTransformNode* n) {
    assert(n != nullptr, "no need to add nullptr");
    _in.push(n);
    n->add_out(this);
  }

  void add_out(VTransformNode* n) {
    _out.push(n);
  }

  uint req() const { return _req; }
  VTransformNode* in(int i) const { return _in.at(i); }
  int outs() const { return _out.length(); }
  VTransformNode* out(int i) const { return _out.at(i); }

  bool has_req_or_dependency() const {
    for (int i = 0; i < _in.length(); i++) {
      if (_in.at(i) != nullptr) { return true; }
    }
    return false;
  }

  virtual VTransformScalarNode* isa_Scalar() { return nullptr; }
  virtual VTransformInputScalarNode* isa_InputScalar() { return nullptr; }
  virtual VTransformVectorNode* isa_Vector() { return nullptr; }
  virtual VTransformElementWiseVectorNode* isa_ElementWiseVector() { return nullptr; }
  virtual VTransformBoolVectorNode* isa_BoolVector() { return nullptr; }
  virtual VTransformReductionVectorNode* isa_ReductionVector() { return nullptr; }
  virtual VTransformMemVectorNode* isa_MemVector() { return nullptr; }
  virtual VTransformLoadVectorNode* isa_LoadVector() { return nullptr; }
  virtual VTransformStoreVectorNode* isa_StoreVector() { return nullptr; }

  virtual bool is_load_in_loop() const { return false; }
  virtual bool is_load_or_store_in_loop() const { return false; }
  virtual const VPointer& vpointer(const VLoopAnalyzer& vloop_analyzer) const { ShouldNotReachHere(); }

  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const = 0;

  Node* find_transformed_input(int i, const GrowableArray<Node*>& vnode_idx_to_transformed_node) const;

  void register_new_node_from_vectorization(const VLoopAnalyzer& vloop_analyzer, Node* vn, Node* old_node) const;

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
  VTransformScalarNode(VTransform& vtransform, Node* n) :
    VTransformNode(vtransform, n->req()), _node(n) {}
  Node* node() const { return _node; }
  virtual VTransformScalarNode* isa_Scalar() override { return this; }
  virtual bool is_load_in_loop() const override { return _node->is_Load(); }
  virtual bool is_load_or_store_in_loop() const override { return _node->is_Load() || _node->is_Store(); }
  virtual const VPointer& vpointer(const VLoopAnalyzer& vloop_analyzer) const override { return vloop_analyzer.vpointers().vpointer(node()->as_Mem()); }
  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "Scalar"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Wrapper node for nodes outside the loop that are inputs to nodes in the loop.
// Since we want the loop-internal nodes to be able to reference all inputs as vtnodes,
// we must wrap the inputs that are outside the loop into special vtnodes, too.
class VTransformInputScalarNode : public VTransformScalarNode {
public:
  VTransformInputScalarNode(VTransform& vtransform, Node* n) :
    VTransformScalarNode(vtransform, n) {}
  virtual VTransformInputScalarNode* isa_InputScalar() override { return this; }
  virtual bool is_load_in_loop() const override { return false; }
  virtual bool is_load_or_store_in_loop() const override { return false; }
  NOT_PRODUCT(virtual const char* name() const override { return "InputScalar"; };)
};

// Transform produces a ReplicateNode, replicating the input to all vector lanes.
class VTransformReplicateNode : public VTransformNode {
private:
  int _vlen;
  BasicType _element_type;
public:
  VTransformReplicateNode(VTransform& vtransform, int vlen, BasicType element_type) :
    VTransformNode(vtransform, 2), _vlen(vlen), _element_type(element_type) {}
  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "Replicate"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Transform introduces a scalar ConvI2LNode that was not previously in the C2 graph.
class VTransformConvI2LNode : public VTransformNode {
public:
  VTransformConvI2LNode(VTransform& vtransform) : VTransformNode(vtransform, 2) {}
  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const override;
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
  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const override;
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
  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "PopulateIndex"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Base class for all vector vtnodes.
class VTransformVectorNode : public VTransformNode {
private:
  GrowableArray<Node*> _nodes;
public:
  VTransformVectorNode(VTransform& vtransform, const uint req, const uint number_of_nodes) :
    VTransformNode(vtransform, req), _nodes(vtransform.arena(), number_of_nodes, number_of_nodes, nullptr) {}

  void set_nodes(const Node_List* pack) {
    for (uint k = 0; k < pack->size(); k++) {
      _nodes.at_put(k, pack->at(k));
    }
  }

  const GrowableArray<Node*>& nodes() const { return _nodes; }
  virtual VTransformVectorNode* isa_Vector() override { return this; }
  void register_new_node_from_vectorization_and_replace_scalar_nodes(const VLoopAnalyzer& vloop_analyzer, Node* vn) const;
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Catch all for all element-wise vector operations.
class VTransformElementWiseVectorNode : public VTransformVectorNode {
public:
  VTransformElementWiseVectorNode(VTransform& vtransform, uint req, uint number_of_nodes) :
    VTransformVectorNode(vtransform, req, number_of_nodes) {}
  virtual VTransformElementWiseVectorNode* isa_ElementWiseVector() override { return this; }
  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "ElementWiseVector"; };)
};

struct VTransformBoolTest {
  const BoolTest::mask _mask;
  const bool _is_negated;

  VTransformBoolTest(const BoolTest::mask mask, bool is_negated) :
    _mask(mask), _is_negated(is_negated) {}
};

class VTransformBoolVectorNode : public VTransformElementWiseVectorNode {
private:
  const VTransformBoolTest _test;
public:
  VTransformBoolVectorNode(VTransform& vtransform, uint number_of_nodes, VTransformBoolTest test) :
    VTransformElementWiseVectorNode(vtransform, 2, number_of_nodes), _test(test) {}
  VTransformBoolTest test() const { return _test; }
  virtual VTransformBoolVectorNode* isa_BoolVector() override { return this; }
  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "BoolVector"; };)
};

class VTransformReductionVectorNode : public VTransformVectorNode {
public:
  // req = 3 -> [ctrl, scalar init, vector]
  VTransformReductionVectorNode(VTransform& vtransform, uint number_of_nodes) :
    VTransformVectorNode(vtransform, 3, number_of_nodes) {}
  virtual VTransformReductionVectorNode* isa_ReductionVector() override { return this; }
  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "ReductionVector"; };)
};

class VTransformMemVectorNode : public VTransformVectorNode {
private:
  const VPointer _vpointer; // with size of the vector

public:
  VTransformMemVectorNode(VTransform& vtransform, const uint req, uint number_of_nodes, const VPointer& vpointer) :
    VTransformVectorNode(vtransform, req, number_of_nodes),
    _vpointer(vpointer) {}

  virtual VTransformMemVectorNode* isa_MemVector() override { return this; }
  virtual bool is_load_or_store_in_loop() const override { return true; }
  virtual const VPointer& vpointer(const VLoopAnalyzer& vloop_analyzer) const override { return _vpointer; }
};

class VTransformLoadVectorNode : public VTransformMemVectorNode {
public:
  // req = 3 -> [ctrl, mem, adr]
  VTransformLoadVectorNode(VTransform& vtransform, uint number_of_nodes, const VPointer& vpointer) :
    VTransformMemVectorNode(vtransform, 3, number_of_nodes, vpointer) {}
  LoadNode::ControlDependency control_dependency() const;
  virtual VTransformLoadVectorNode* isa_LoadVector() override { return this; }
  virtual bool is_load_in_loop() const override { return true; }
  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "LoadVector"; };)
};

class VTransformStoreVectorNode : public VTransformMemVectorNode {
public:
  // req = 4 -> [ctrl, mem, adr, val]
  VTransformStoreVectorNode(VTransform& vtransform, uint number_of_nodes, const VPointer& vpointer) :
    VTransformMemVectorNode(vtransform, 4, number_of_nodes, vpointer) {}
  virtual VTransformStoreVectorNode* isa_StoreVector() override { return this; }
  virtual bool is_load_in_loop() const override { return false; }
  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "StoreVector"; };)
};

// Invoke callback on all memops, in the order of the schedule.
template<typename Callback>
void VTransformGraph::for_each_memop_in_schedule(Callback callback) const {
  assert(_schedule.length() == _vtnodes.length(), "schedule was computed");

  for (int i = 0; i < _schedule.length(); i++) {
    VTransformNode* vtn = _schedule.at(i);

    // We can ignore input nodes, they are outside the loop.
    if (vtn->isa_InputScalar() != nullptr) { continue; }

    VTransformScalarNode* scalar = vtn->isa_Scalar();
    if (scalar != nullptr && scalar->node()->is_Mem()) {
      callback(scalar->node()->as_Mem());
    }

    VTransformVectorNode* vector = vtn->isa_Vector();
    if (vector != nullptr && vector->nodes().at(0)->is_Mem()) {
      for (int j = 0; j < vector->nodes().length(); j++) {
        callback(vector->nodes().at(j)->as_Mem());
      }
    }
  }
}

#endif // SHARE_OPTO_VTRANSFORM_HPP
