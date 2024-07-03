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

// VTransform
//
// Maps the transformation from the scalar to the vectorized loop.
//
// The graph (VTransformGraph) of vtnodes (VTransformNode) represents the output
// C2 graph after vectorization as closely as possible.
//
// This allows us to schedule the graph, and check for possible cycles that
// vectorization might introduce.
//
// Changes to the C2 IR are only made once the "apply" method is called, and
// each vtnode generates its corresponding scalar or vector C2 nodes.
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

// Result from a VTransformNode::apply
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
  NOT_PRODUCT( void trace(VTransformNode* vtn) const; )
};

// VTransformGraph is a graph of VTransformNode, which represent the VTransform. It
// is designed to resemble the C2 nodes after "apply" as closely as possible.
// Currently, there are these stages to the VTransform:
//
//  - Construction:
//      external (e.g. with SuperWordVTransformBuilder)
//
//  - Schedule:
//      compute linearization of graph, into a order that respects all edges in the
//      graph (bailout if circle detected).
//
//  - Apply:
//      Make all necessary changes to the C2 IR, each VTransformNode generates the
//      corresponding scalar or vector C2 nodes.
//
class VTransformGraph : public StackObj {
private:
  const VLoopAnalyzer& _vloop_analyzer;
  const VLoop& _vloop;

  // Everything in the graph is allocated from this arena, including all vtnodes.
  Arena _arena;

  VTransformNodeIDX _next_idx;
  GrowableArray<VTransformNode*> _vtnodes;

  // Schedule (linearization) of the graph. We use this to reorder the memory graph
  // before inserting vector operations.
  GrowableArray<VTransformNode*> _schedule;

  // Memory reference, and the alignment width (aw) for which we align the main-loop,
  // by adjusting the pre-loop limit.
  MemNode const* _mem_ref_for_main_loop_alignment;
  int _aw_for_main_loop_alignment;

#ifndef PRODUCT
  bool _is_trace_rejections;
  bool _is_trace_align_vector;
  bool _is_trace_info;
  bool _is_trace_verbose;
#endif

public:
  VTransformGraph(const VLoopAnalyzer& vloop_analyzer,
                  MemNode const* mem_ref_for_main_loop_alignment,
                  int aw_for_main_loop_alignment
                  NOT_PRODUCT( COMMA const bool is_trace_rejections)
                  NOT_PRODUCT( COMMA const bool is_trace_align_vector)
                  NOT_PRODUCT( COMMA const bool is_trace_info)
                  ) :
    _vloop_analyzer(vloop_analyzer),
    _vloop(vloop_analyzer.vloop()),
    _arena(mtCompiler),
    _next_idx(0),
    _vtnodes(&_arena, _vloop.estimated_body_length(), 0, nullptr),
    _schedule(&_arena, _vloop.estimated_body_length(), 0, nullptr),
    _mem_ref_for_main_loop_alignment(mem_ref_for_main_loop_alignment),
    _aw_for_main_loop_alignment(aw_for_main_loop_alignment)
    NOT_PRODUCT( COMMA _is_trace_rejections(is_trace_rejections) )
    NOT_PRODUCT( COMMA _is_trace_align_vector(is_trace_align_vector) )
    NOT_PRODUCT( COMMA _is_trace_info(is_trace_info) )
  {
#ifndef PRODUCT
    bool is_trace     = _vloop.vtrace().is_trace(TraceAutoVectorizationTag::VTRANSFORM);
    _is_trace_verbose = _vloop.vtrace().is_trace(TraceAutoVectorizationTag::ALL);
    _is_trace_rejections   |= is_trace || _is_trace_verbose;
    _is_trace_align_vector |= is_trace || _is_trace_verbose;
    _is_trace_info         |= is_trace || _is_trace_verbose;
#endif
  }

  const VLoopAnalyzer& vloop_analyzer() const { return _vloop_analyzer; }
  Arena* arena() { return &_arena; }
  VTransformNodeIDX new_idx() { return _next_idx++; }
  void add_vtnode(VTransformNode* vtnode);
  bool is_empty() const { return _vtnodes.is_empty(); }

  bool schedule();
  void apply();

private:
  // VLoop accessors
  PhaseIdealLoop* phase()     const { return _vloop.phase(); }
  PhaseIterGVN& igvn()        const { return _vloop.phase()->igvn(); }
  IdealLoopTree* lpt()        const { return _vloop.lpt(); }
  CountedLoopNode* cl()       const { return _vloop.cl(); }
  int iv_stride()             const { return cl()->stride_con(); }
  bool in_bb(const Node* n)   const { return _vloop.in_bb(n); }

  // VLoopVPointers accessors
  const VPointer& vpointer(const MemNode* mem) const {
    return _vloop_analyzer.vpointers().vpointer(mem);
  }

  void schedule_collect_nodes_without_req_or_dependency(GrowableArray<VTransformNode*>& stack) const;

  template<typename Callback>
  void for_each_memop_in_schedule(Callback callback) const;

  void apply_memops_reordering_with_schedule() const;

  // Ensure that the main loop vectors are aligned by adjusting the pre loop limit.
  void determine_mem_ref_and_aw_for_main_loop_alignment();
  void adjust_pre_loop_limit_to_align_main_loop_vectors();

  void apply_vectorization() const;

#ifndef PRODUCT
  void print_vtnodes() const;
  void print_schedule() const;
  void print_memops_schedule() const;
  void trace_schedule_cycle(const GrowableArray<VTransformNode*>& stack,
                            const VectorSet& pre_visited,
                            const VectorSet& post_visited) const;
#endif
};

// VTransformNodes resemble the C2 IR Nodes. They represent the resulting scalar and
// vector nodes as closely as possible.
class VTransformNode : public ArenaObj {
public:
  const VTransformNodeIDX _idx;

private:
  // _in is split into required inputs (_req), and additional dependencies.
  const uint _req;
  GrowableArray<VTransformNode*> _in;
  GrowableArray<VTransformNode*> _out;

public:
  VTransformNode(VTransformGraph& graph, const uint req) :
    _idx(graph.new_idx()),
    _req(req),
    _in(graph.arena(),  req, req, nullptr),
    _out(graph.arena(), 4, 0, nullptr)
  {
    graph.add_vtnode(this);
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

  void add_dependency(VTransformNode* n) {
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
  VTransformScalarNode(VTransformGraph& graph, Node* n) :
    VTransformNode(graph, n->req()), _node(n) {}
  Node* node() const { return _node; }
  virtual VTransformScalarNode* isa_Scalar() override { return this; }
  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "Scalar"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Wrapper node for nodes outside the loop that are inputs to nodes in the loop.
// Since we want the loop-internal nodes to be able to reference all inputs as vtnodes,
// we must wrap the inputs that are outside the loop also into special vtnodes.
class VTransformInputScalarNode : public VTransformScalarNode {
public:
  VTransformInputScalarNode(VTransformGraph& graph, Node* n) :
    VTransformScalarNode(graph, n) {}
  virtual VTransformInputScalarNode* isa_InputScalar() override { return this; }
  NOT_PRODUCT(virtual const char* name() const override { return "InputScalar"; };)
};

// Transform produces a ReplicateNode, replicating the input to all vector lanes.
class VTransformReplicateNode : public VTransformNode {
private:
  int _vlen;
  const Type* _element_type;
public:
  VTransformReplicateNode(VTransformGraph& graph, int vlen, const Type* element_type) :
    VTransformNode(graph, 2), _vlen(vlen), _element_type(element_type) {}
  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "Replicate"; };)
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Transform introduces a scalar ConvI2LNode that was not previously in the C2 graph.
class VTransformConvI2LNode : public VTransformNode {
public:
  VTransformConvI2LNode(VTransformGraph& graph) : VTransformNode(graph, 2) {}
  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "ConvI2L"; };)
};

// Transform introduces a shift-count node, that truncates the shift count for a vector shift.
class VTransformShiftCountNode : public VTransformNode {
private:
  int _vlen;
  const BasicType _element_bt;
  juint _mask;
  int _shift_opcode;
public:
  VTransformShiftCountNode(VTransformGraph& graph, int vlen, BasicType element_bt, juint mask, int shift_opcode) :
    VTransformNode(graph, 2), _vlen(vlen), _element_bt(element_bt), _mask(mask), _shift_opcode(shift_opcode) {}
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
  VTransformPopulateIndexNode(VTransformGraph& graph, int vlen, const BasicType element_bt) :
    VTransformNode(graph, 2), _vlen(vlen), _element_bt(element_bt) {}
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
  VTransformVectorNode(VTransformGraph& graph, const uint req, const uint number_of_nodes) :
    VTransformNode(graph, req), _nodes(graph.arena(), number_of_nodes, number_of_nodes, nullptr) {}

  void set_nodes(const Node_List* pack) {
    for (uint k = 0; k < pack->size(); k++) {
      _nodes.at_put(k, pack->at(k));
    }
  }

  const GrowableArray<Node*> nodes() const { return _nodes; }
  virtual VTransformVectorNode* isa_Vector() override { return this; }
  void register_new_node_from_vectorization_and_replace_scalar_nodes(const VLoopAnalyzer& vloop_analyzer, Node* vn) const;
  NOT_PRODUCT(virtual void print_spec() const override;)
};

// Catch all for all element-wise vector operations.
class VTransformElementWiseVectorNode : public VTransformVectorNode {
public:
  VTransformElementWiseVectorNode(VTransformGraph& graph, uint req, uint number_of_nodes) :
    VTransformVectorNode(graph, req, number_of_nodes) {}
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
  VTransformBoolVectorNode(VTransformGraph& graph, uint number_of_nodes, VTransformBoolTest test) :
    VTransformElementWiseVectorNode(graph, 2, number_of_nodes), _test(test) {}
  VTransformBoolTest test() const { return _test; }
  virtual VTransformBoolVectorNode* isa_BoolVector() override { return this; }
  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "BoolVector"; };)
};

class VTransformReductionVectorNode : public VTransformVectorNode {
public:
  // req = 3 -> [ctrl, scalar init, vector]
  VTransformReductionVectorNode(VTransformGraph& graph, uint number_of_nodes) :
    VTransformVectorNode(graph, 3, number_of_nodes) {}
  virtual VTransformReductionVectorNode* isa_ReductionVector() override { return this; }
  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "ReductionVector"; };)
};

class VTransformLoadVectorNode : public VTransformVectorNode {
public:
  // req = 3 -> [ctrl, mem, adr]
  VTransformLoadVectorNode(VTransformGraph& graph, uint number_of_nodes) :
    VTransformVectorNode(graph, 3, number_of_nodes) {}
  LoadNode::ControlDependency control_dependency() const;
  virtual VTransformApplyResult apply(const VLoopAnalyzer& vloop_analyzer,
                                      const GrowableArray<Node*>& vnode_idx_to_transformed_node) const override;
  NOT_PRODUCT(virtual const char* name() const override { return "LoadVector"; };)
};

class VTransformStoreVectorNode : public VTransformVectorNode {
public:
  // req = 4 -> [ctrl, mem, adr, val]
  VTransformStoreVectorNode(VTransformGraph& graph, uint number_of_nodes) :
    VTransformVectorNode(graph, 4, number_of_nodes) {}
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
