/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#include "precompiled.hpp"
#include "opto/addnode.hpp"
#include "opto/callnode.hpp"
#include "opto/castnode.hpp"
#include "opto/loopnode.hpp"
#include "opto/node.hpp"
#include "opto/predicates.hpp"
#include "opto/rootnode.hpp"

// Walk over all Initialized Assertion Predicates and return the entry into the first Initialized Assertion Predicate
// (i.e. not belonging to an Initialized Assertion Predicate anymore)
Node* AssertionPredicatesWithHalt::find_entry(Node* start_proj) {
  assert(start_proj != nullptr, "should not be null");
  Node* entry = start_proj;
  while (AssertionPredicateWithHalt::is_predicate(entry)) {
    entry = entry->in(0)->in(0);
  }
  return entry;
}

// An Assertion Predicate has always a true projection on the success path.
bool may_be_assertion_predicate_if(const Node* node) {
  assert(node != nullptr, "should not be null");
  return node->is_IfTrue() && RegularPredicate::may_be_predicate_if(node->as_IfProj());
}

bool AssertionPredicateWithHalt::is_predicate(const Node* maybe_success_proj) {
  if (!may_be_assertion_predicate_if(maybe_success_proj)) {
    return false;
  }
  return has_assertion_predicate_opaque(maybe_success_proj) && has_halt(maybe_success_proj);
}

// Check if the If node of `predicate_proj` has an OpaqueTemplateAssertionPredicate (Template Assertion Predicate) or
// an OpaqueInitializedAssertionPredicate (Initialized Assertion Predicate) node as input.
bool AssertionPredicateWithHalt::has_assertion_predicate_opaque(const Node* predicate_proj) {
  IfNode* iff = predicate_proj->in(0)->as_If();
  Node* bol = iff->in(1);
  return bol->is_OpaqueTemplateAssertionPredicate() || bol->is_OpaqueInitializedAssertionPredicate();
}

// Check if the other projection (UCT projection) of `success_proj` has a Halt node as output.
bool AssertionPredicateWithHalt::has_halt(const Node* success_proj) {
  ProjNode* other_proj = success_proj->as_IfProj()->other_if_proj();
  return other_proj->outcnt() == 1 && other_proj->unique_out()->Opcode() == Op_Halt;
}

// Returns the Parse Predicate node if the provided node is a Parse Predicate success proj. Otherwise, return null.
ParsePredicateNode* ParsePredicate::init_parse_predicate(Node* parse_predicate_proj,
                                                         Deoptimization::DeoptReason deopt_reason) {
  assert(parse_predicate_proj != nullptr, "must not be null");
  if (parse_predicate_proj->is_IfTrue() && parse_predicate_proj->in(0)->is_ParsePredicate()) {
    ParsePredicateNode* parse_predicate_node = parse_predicate_proj->in(0)->as_ParsePredicate();
    if (parse_predicate_node->deopt_reason() == deopt_reason) {
      return parse_predicate_node;
    }
  }
  return nullptr;
}

Deoptimization::DeoptReason RegularPredicateWithUCT::uncommon_trap_reason(IfProjNode* if_proj) {
    CallStaticJavaNode* uct_call = if_proj->is_uncommon_trap_if_pattern();
    if (uct_call == nullptr) {
      return Deoptimization::Reason_none;
    }
    return Deoptimization::trap_request_reason(uct_call->uncommon_trap_request());
}

bool RegularPredicateWithUCT::is_predicate(Node* maybe_success_proj) {
  if (RegularPredicate::may_be_predicate_if(maybe_success_proj)) {
    return has_valid_uncommon_trap(maybe_success_proj);
  } else {
    return false;
  }
}

bool RegularPredicateWithUCT::has_valid_uncommon_trap(const Node* success_proj) {
  assert(RegularPredicate::may_be_predicate_if(success_proj), "must have been checked before");
  const Deoptimization::DeoptReason deopt_reason = uncommon_trap_reason(success_proj->as_IfProj());
  return (deopt_reason == Deoptimization::Reason_loop_limit_check ||
          deopt_reason == Deoptimization::Reason_predicate ||
          deopt_reason == Deoptimization::Reason_profile_predicate);
}

bool RegularPredicateWithUCT::is_predicate(const Node* node, Deoptimization::DeoptReason deopt_reason) {
  if (RegularPredicate::may_be_predicate_if(node)) {
    return deopt_reason == uncommon_trap_reason(node->as_IfProj());
  } else {
    return false;
  }
}

// A Regular Predicate must have an If or a RangeCheck node, while the If should not be a zero trip guard check.
// Note that this method can be called during IGVN, so we also need to check that the If is not top.
bool RegularPredicate::may_be_predicate_if(const Node* node) {
  if (node->is_IfProj() && node->in(0)->is_If()) {
    const IfNode* if_node = node->in(0)->as_If();
    const int opcode_if = if_node->Opcode();
    if ((opcode_if == Op_If && !if_node->is_zero_trip_guard())
        || opcode_if == Op_RangeCheck) {
      return true;
    }
  }
  return false;
}

// Runtime Predicates always have an UCT since they could normally fail at runtime. In this case we execute the trap
// on the failing path.
bool RuntimePredicate::is_predicate(Node* node) {
  return RegularPredicateWithUCT::is_predicate(node);
}

bool RuntimePredicate::is_predicate(Node* node, Deoptimization::DeoptReason deopt_reason) {
  return RegularPredicateWithUCT::is_predicate(node, deopt_reason);
}

// Rewire any non-CFG nodes dependent on this Template Assertion Predicate (i.e. with a control input to this
// Template Assertion Predicate) to the 'target_predicate' based on the 'data_in_loop_body' check.
void TemplateAssertionPredicate::rewire_loop_data_dependencies(IfTrueNode* target_predicate,
                                                               const NodeInLoopBody& data_in_loop_body,
                                                               PhaseIdealLoop* phase) const {
  for (DUIterator i = _success_proj->outs(); _success_proj->has_out(i); i++) {
    Node* output = _success_proj->out(i);
    if (!output->is_CFG() && data_in_loop_body.check(output)) {
      phase->igvn().replace_input_of(output, 0, target_predicate);
      --i; // account for the just deleted output
    }
  }
}

// Template Assertion Predicates always have the dedicated OpaqueTemplateAssertionPredicate to identify them.
bool TemplateAssertionPredicate::is_predicate(Node* node) {
  if (!may_be_assertion_predicate_if(node)) {
    return false;
  }
  IfNode* if_node = node->in(0)->as_If();
  return if_node->in(1)->is_OpaqueTemplateAssertionPredicate();
}

// Clone this Template Assertion Predicate and replace the OpaqueLoopInitNode with the provided 'new_opaque_init' node.
IfTrueNode* TemplateAssertionPredicate::clone_and_replace_init(Node* new_control, OpaqueLoopInitNode* new_opaque_init,
                                                               PhaseIdealLoop* phase) const {
  assert(PhaseIdealLoop::assertion_predicate_has_loop_opaque_node(_if_node),
         "must find OpaqueLoop* nodes for Template Assertion Predicate");
  TemplateAssertionExpression template_assertion_expression(opaque_node());
  OpaqueTemplateAssertionPredicateNode* new_opaque_node =
      template_assertion_expression.clone_and_replace_init(new_opaque_init, new_control, phase);
  AssertionPredicateIfCreator assertion_predicate_if_creator(phase);
  IfTrueNode* success_proj = assertion_predicate_if_creator.create_for_template(new_control, _if_node->Opcode(),
                                                                                new_opaque_node,
                                                                                _if_node->assertion_predicate_type());
  assert(PhaseIdealLoop::assertion_predicate_has_loop_opaque_node(success_proj->in(0)->as_If()),
         "Template Assertion Predicates must have OpaqueLoop* nodes in the bool expression");
  return success_proj;
}

// Replace the input to OpaqueLoopStrideNode with 'new_stride' and leave the other nodes unchanged.
void TemplateAssertionPredicate::replace_opaque_stride_input(Node* new_stride, PhaseIterGVN& igvn) const {
  TemplateAssertionExpression expression(opaque_node());
  expression.replace_opaque_stride_input(new_stride, igvn);
}

// Create a new Initialized Assertion Predicate from this template at 'new_control' and return the success projection
// of the newly created Initialized Assertion Predicate.
IfTrueNode* TemplateAssertionPredicate::initialize(PhaseIdealLoop* phase, Node* new_control) const {
  assert(phase->assertion_predicate_has_loop_opaque_node(head()),
         "must find OpaqueLoop* nodes for Template Assertion Predicate");
  InitializedAssertionPredicateCreator initialized_assertion_predicate(phase);
  IfTrueNode* success_proj = initialized_assertion_predicate.create_from_template(head(), new_control);
  assert(!phase->assertion_predicate_has_loop_opaque_node(success_proj->in(0)->as_If()),
         "Initialized Assertion Predicates do not have OpaqueLoop* nodes in the bool expression anymore");
  return success_proj;
}

// Initialized Assertion Predicates always have the dedicated OpaqueInitiailizedAssertionPredicate node to identify
// them.
bool InitializedAssertionPredicate::is_predicate(Node* node) {
  if (!may_be_assertion_predicate_if(node)) {
    return false;
  }
  IfNode* if_node = node->in(0)->as_If();
  return if_node->in(1)->is_OpaqueInitializedAssertionPredicate();
}

void InitializedAssertionPredicate::kill(PhaseIdealLoop* phase) const {
  Node* true_con = phase->igvn().intcon(1);
  phase->set_ctrl(true_con, phase->C->root());
  phase->igvn().replace_input_of(_if_node, 1, true_con);
}

#ifdef ASSERT
// Check that the block has at most one Parse Predicate and that we only find Regular Predicate nodes (i.e. IfProj,
// If, or RangeCheck nodes).
void RegularPredicateBlock::verify_block(Node* tail) {
  Node* next = tail;
  while (next != _entry) {
    assert(!next->is_ParsePredicate(), "can only have one Parse Predicate in a block");
    const int opcode = next->Opcode();
    assert(next->is_IfProj() || opcode == Op_If || opcode == Op_RangeCheck,
           "Regular Predicates consist of an IfProj and an If or RangeCheck node");
    assert(opcode != Op_If || !next->as_If()->is_zero_trip_guard(), "should not be zero trip guard");
    next = next->in(0);
  }
}
#endif // ASSERT

// This strategy clones the OpaqueLoopInit and OpaqueLoopStride nodes.
class CloneStrategy : public TransformStrategyForOpaqueLoopNodes {
  PhaseIdealLoop* const _phase;
  Node* const _new_ctrl;

 public:
  CloneStrategy(PhaseIdealLoop* phase, Node* new_ctrl)
      : _phase(phase),
        _new_ctrl(new_ctrl) {}
  NONCOPYABLE(CloneStrategy);

  Node* transform_opaque_init(OpaqueLoopInitNode* opaque_init) const override {
    return _phase->clone_and_register(opaque_init, _new_ctrl)->as_OpaqueLoopInit();
  }

  Node* transform_opaque_stride(OpaqueLoopStrideNode* opaque_stride) const override {
    return _phase->clone_and_register(opaque_stride, _new_ctrl)->as_OpaqueLoopStride();
  }
};

// This strategy replaces the OpaqueLoopInitNode with the provided init node and clones the OpaqueLoopStrideNode.
class ReplaceInitAndCloneStrideStrategy : public TransformStrategyForOpaqueLoopNodes {
  Node* const _new_init;
  Node* const _new_ctrl;
  PhaseIdealLoop* const _phase;

 public:
  ReplaceInitAndCloneStrideStrategy(Node* new_init, Node* new_ctrl, PhaseIdealLoop* phase)
      : _new_init(new_init),
        _new_ctrl(new_ctrl),
        _phase(phase) {}
  NONCOPYABLE(ReplaceInitAndCloneStrideStrategy);

  Node* transform_opaque_init(OpaqueLoopInitNode* opaque_init) const override {
    return _new_init;
  }

  Node* transform_opaque_stride(OpaqueLoopStrideNode* opaque_stride) const override {
    return _phase->clone_and_register(opaque_stride, _new_ctrl)->as_OpaqueLoopStride();
  }
};

// This strategy replaces the OpaqueLoopInit and OpaqueLoopStride nodes with the provided init and stride nodes,
// respectively.
class ReplaceInitAndStrideStrategy : public TransformStrategyForOpaqueLoopNodes {
  Node* const _new_init;
  Node* const _new_stride;

 public:
  ReplaceInitAndStrideStrategy(Node* new_init, Node* new_stride)
      : _new_init(new_init),
        _new_stride(new_stride) {}
  NONCOPYABLE(ReplaceInitAndStrideStrategy);

  Node* transform_opaque_init(OpaqueLoopInitNode* opaque_init) const override {
    return _new_init;
  }

  Node* transform_opaque_stride(OpaqueLoopStrideNode* opaque_stride) const override {
    return _new_stride;
  }
};

// Creates an identical clone of this Template Assertion Expression (i.e.cloning all nodes from the
// OpaqueTemplateAssertionPredicate to and including the OpaqueLoop* nodes). The cloned nodes are rewired to reflect the
// same graph structure as found for this Template Assertion Expression. The cloned nodes get 'new_ctrl' as ctrl. There
// is no other update done for the cloned nodes. Return the newly cloned OpaqueTemplateAssertionPredicate.
OpaqueTemplateAssertionPredicateNode* TemplateAssertionExpression::clone(Node* new_ctrl, PhaseIdealLoop* phase) {
  CloneStrategy clone_init_and_stride_strategy(phase, new_ctrl);
  return clone(clone_init_and_stride_strategy, new_ctrl, phase);
}

// Same as clone() but instead of cloning the OpaqueLoopInitNode, we replace it with the provided 'new_init' node.
OpaqueTemplateAssertionPredicateNode*
TemplateAssertionExpression::clone_and_replace_init(Node* new_init, Node* new_ctrl, PhaseIdealLoop* phase) {
  ReplaceInitAndCloneStrideStrategy replace_init_and_clone_stride_strategy(new_init, new_ctrl, phase);
  return clone(replace_init_and_clone_stride_strategy, new_ctrl, phase);
}

// Same as clone() but instead of cloning the OpaqueLoopInit and OpaqueLoopStride node, we replace them with the provided
// 'new_init' and 'new_stride' nodes, respectively.
OpaqueTemplateAssertionPredicateNode*
TemplateAssertionExpression::clone_and_replace_init_and_stride(Node* new_control, Node* new_init, Node* new_stride,
                                                               PhaseIdealLoop* phase) {
  ReplaceInitAndStrideStrategy replace_init_and_stride_strategy(new_init, new_stride);
  return clone(replace_init_and_stride_strategy, new_control, phase);
}

// Class to collect data nodes from a source to target nodes by following the inputs of the source node recursively.
// The class takes a node filter to decide which input nodes to follow and a target node predicate to start backtracking
// from. All nodes found on all paths from source->target(s) are returned in a Unique_Node_List (without duplicates).
class DataNodesOnPathsToTargets : public StackObj {
  typedef bool (*NodeCheck)(const Node*);

  // Node filter function to decide if we should process a node or not while searching for targets.
  NodeCheck _node_filter;
  // Function to decide if a node is a target node (i.e. where we should start backtracking). This check should also
  // trivially pass the _node_filter.
  NodeCheck _is_target_node;
  // The resulting node collection of all nodes on paths from source->target(s).
  Unique_Node_List _collected_nodes;
  // List to track all nodes visited on the search for target nodes starting at a start node. These nodes are then used
  // in backtracking to find the nodes actually being on a start->target(s) path. This list also serves as visited set
  // to avoid double visits of a node which could happen with diamonds shapes.
  Unique_Node_List _nodes_to_visit;

 public:
  DataNodesOnPathsToTargets(NodeCheck node_filter, NodeCheck is_target_node)
      : _node_filter(node_filter),
        _is_target_node(is_target_node) {}
  NONCOPYABLE(DataNodesOnPathsToTargets);

  // Collect all input nodes from 'start_node'->target(s) by applying the node filter to discover new input nodes and
  // the target node predicate to stop discovering more inputs and start backtracking. The implementation is done
  // with two BFS traversal: One to collect the target nodes (if any) and one to backtrack from the target nodes to
  // find all other nodes on the start->target(s) paths.
  const Unique_Node_List& collect(Node* start_node) {
    assert(_collected_nodes.size() == 0 && _nodes_to_visit.size() == 0, "should not call this method twice in a row");
    assert(!_is_target_node(start_node), "no trivial paths where start node is also a target node");

    collect_target_nodes(start_node);
    backtrack_from_target_nodes();
    assert(_collected_nodes.size() == 0 || _collected_nodes.member(start_node),
           "either target node predicate was never true or must find start node again when doing backtracking work");
    return _collected_nodes;
  }

 private:
  // Do a BFS from the start_node to collect all target nodes. We can then do another BFS from the target nodes to
  // find all nodes on the paths from start->target(s).
  // Note: We could do a single DFS pass to search targets and backtrack in one walk. But this is much more complex.
  //       Given that the typical Template Assertion Expression only consists of a few nodes, we aim for simplicity here.
  void collect_target_nodes(Node* start_node) {
    _nodes_to_visit.push(start_node);
    for (uint i = 0; i < _nodes_to_visit.size(); i++) {
      Node* next = _nodes_to_visit[i];
      for (uint j = 1; j < next->req(); j++) {
        Node* input = next->in(j);
        if (_is_target_node(input)) {
          assert(_node_filter(input), "must also pass node filter");
          _collected_nodes.push(input);
        } else if (_node_filter(input)) {
          _nodes_to_visit.push(input);
        }
      }
    }
  }

  // Backtrack from all previously collected target nodes by using the visited set of the start->target(s) search. If no
  // node was collected in the first place (i.e. target node predicate was never true), then nothing needs to be done.
  void backtrack_from_target_nodes() {
    for (uint i = 0; i < _collected_nodes.size(); i++) {
      Node* node_on_path = _collected_nodes[i];
      for (DUIterator_Fast jmax, j = node_on_path->fast_outs(jmax); j < jmax; j++) {
        Node* use = node_on_path->fast_out(j);
        if (_nodes_to_visit.member(use)) {
          // use must be on a path from start->target(s) because it was also visited in the first BFS starting from
          // the start node.
          _collected_nodes.push(use);
        }
      }
    }
  }
};

// Clones this Template Assertion Expression and applies the given strategy to transform the OpaqueLoop* nodes.
OpaqueTemplateAssertionPredicateNode*
TemplateAssertionExpression::clone(const TransformStrategyForOpaqueLoopNodes& transform_strategy, Node* new_ctrl,
                                   PhaseIdealLoop* phase) {
  ResourceMark rm;
  auto is_opaque_loop_node = [](const Node* node) {
    return node->is_Opaque1();
  };
  DataNodesOnPathsToTargets data_nodes_on_path_to_targets(TemplateAssertionExpressionNode::is_maybe_in_expression,
                                                          is_opaque_loop_node);
  const Unique_Node_List& collected_nodes = data_nodes_on_path_to_targets.collect(_opaque_node);
  DataNodeGraph data_node_graph(collected_nodes, phase);
  const OrigToNewHashtable& orig_to_new = data_node_graph.clone_with_opaque_loop_transform_strategy(transform_strategy, new_ctrl);
  assert(orig_to_new.contains(_opaque_node), "must exist");
  Node* opaque_node_clone = *orig_to_new.get(_opaque_node);
  return opaque_node_clone->as_OpaqueTemplateAssertionPredicate();
}

// This class is used to replace the input to OpaqueLoopStrideNode with a new node while leaving the other nodes
// unchanged.
class ReplaceOpaqueStrideInput : public StackObj {
  PhaseIterGVN& _igvn;
  Unique_Node_List _nodes_to_visit;

 public:
  ReplaceOpaqueStrideInput(OpaqueTemplateAssertionPredicateNode* start_node, PhaseIterGVN& igvn) : _igvn(igvn) {
    _nodes_to_visit.push(start_node);
  }
  NONCOPYABLE(ReplaceOpaqueStrideInput);

  void replace(Node* new_opaque_stride_input) {
    for (uint i = 0; i < _nodes_to_visit.size(); i++) {
      Node* next = _nodes_to_visit[i];
      for (uint j = 1; j < next->req(); j++) {
        Node* input = next->in(j);
        if (input->is_OpaqueLoopStride()) {
          assert(TemplateAssertionExpressionNode::is_maybe_in_expression(input), "must also pass node filter");
          _igvn.replace_input_of(input, 1, new_opaque_stride_input);
        } else if (TemplateAssertionExpressionNode::is_maybe_in_expression(input)) {
          _nodes_to_visit.push(input);
        }
      }
    }
  }
};

// Replace the input to OpaqueLoopStrideNode with 'new_stride' and leave the other nodes unchanged.
void TemplateAssertionExpression::replace_opaque_stride_input(Node* new_stride, PhaseIterGVN& igvn) {
  ReplaceOpaqueStrideInput replace_opaque_stride_input(_opaque_node, igvn);
  replace_opaque_stride_input.replace(new_stride);
}

// The transformations of this class fold the OpaqueLoop* nodes by returning their inputs.
class RemoveOpaqueLoopNodesStrategy : public TransformStrategyForOpaqueLoopNodes {
 public:
  Node* transform_opaque_init(OpaqueLoopInitNode* opaque_init) const override {
    return opaque_init->in(1);
  }

  Node* transform_opaque_stride(OpaqueLoopStrideNode* opaque_stride) const override {
    return opaque_stride->in(1);
  }
};

OpaqueInitializedAssertionPredicateNode*
TemplateAssertionExpression::clone_and_fold_opaque_loop_nodes(Node* new_control, PhaseIdealLoop* phase) {
  RemoveOpaqueLoopNodesStrategy remove_opaque_loop_nodes_strategy;
  OpaqueTemplateAssertionPredicateNode* cloned_template_opaque = clone(remove_opaque_loop_nodes_strategy, new_control,
                                                                       phase);
  OpaqueInitializedAssertionPredicateNode* opaque_initialized_opaque =
      new OpaqueInitializedAssertionPredicateNode(cloned_template_opaque->in(1)->as_Bool(), phase->C);
  phase->register_new_node(opaque_initialized_opaque, new_control);
  return opaque_initialized_opaque;
}

// Check if this node belongs a Template Assertion Expression (including OpaqueLoop* nodes).
bool TemplateAssertionExpressionNode::is_in_expression(Node* node) {
  if (is_maybe_in_expression(node)) {
    ResourceMark rm;
    Unique_Node_List list;
    list.push(node);
    for (uint i = 0; i < list.size(); i++) {
      Node* next = list.at(i);
      if (next->is_OpaqueLoopInit() || next->is_OpaqueLoopStride()) {
        return true;
      } else if (is_maybe_in_expression(next)) {
        list.push_non_cfg_inputs_of(next);
      }
    }
  }
  return false;
}

bool TemplateAssertionExpressionNode::is_template_assertion_predicate(Node* node) {
  return node->is_If() && node->in(1)->is_OpaqueTemplateAssertionPredicate();
}

// This class creates the Assertion Predicate expression to be used for a Template or Initialized Assertion Predicate.
class AssertionPredicateExpressionCreator : public StackObj {
  PhaseIdealLoop* const _phase;
  const jint _stride;
  const int _scale;
  Node* const _offset;
  Node* const _range;
  const bool _upper;

 public:
  AssertionPredicateExpressionCreator(const int stride, const int scale, Node* offset, Node* range,
                                      PhaseIdealLoop* phase)
      : _phase(phase),
        _stride(stride),
        _scale(scale),
        _offset(offset),
        _range(range),
        _upper((_stride > 0) != (_scale > 0)) {} // Make sure rc_predicate() chooses the "scale*init + offset" case.

  // Create the expression for a Template Assertion Predicate with an OpaqueTemplateAssertionPredicate node.
  OpaqueTemplateAssertionPredicateNode* create_for_template(Node* new_control, Node* operand, bool& does_overflow) const {
    BoolNode* bool_for_expression =  _phase->rc_predicate(new_control, _scale, _offset, operand, nullptr,
                                                          _stride, _range, _upper, does_overflow);
    return create_opaque_node(new_control, bool_for_expression);
  }

 private:
  OpaqueTemplateAssertionPredicateNode* create_opaque_node(Node* new_control, BoolNode* bool_for_expression) const {
    OpaqueTemplateAssertionPredicateNode* new_expression = new OpaqueTemplateAssertionPredicateNode(bool_for_expression);
    _phase->C->add_template_assertion_predicate_opaq(new_expression);
    _phase->register_new_node(new_expression, new_control);
    return new_expression;
  }

 public:
  // Create the expression for an Initialized Assertion Predicate with an OpaqueInitializedAssertionPredicate node.
  OpaqueInitializedAssertionPredicateNode* create_for_initialized(Node* new_control, Node* operand,
                                                                  bool& does_overflow) const {
    BoolNode* bool_for_expression = _phase->rc_predicate(new_control, _scale, _offset, operand, nullptr,
                                                         _stride, _range, _upper, does_overflow);
    return create_opaque_initialized_assertion_predicate_node(new_control, bool_for_expression);
  }

 private:
  OpaqueInitializedAssertionPredicateNode* create_opaque_initialized_assertion_predicate_node(
      Node* new_control, BoolNode* bool_for_expression) const {
    OpaqueInitializedAssertionPredicateNode* new_expression =
        new OpaqueInitializedAssertionPredicateNode(bool_for_expression, _phase->C);
    _phase->register_new_node(new_expression, new_control);
    return new_expression;
  }
};

// Creates an If with a success and a fail path with the given assertion_expression. The only difference to
// create_for_initialized() is that we use a template specific Halt message on the fail path.
IfTrueNode* AssertionPredicateIfCreator::create_for_template(Node* new_control, const int if_opcode,
                                                             Node* assertion_expression,
                                                             const AssertionPredicateType assertion_predicate_type) {
  const char* halt_message = "Template Assertion Predicates are always removed before code generation";
  return create(new_control, if_opcode, assertion_expression, halt_message, assertion_predicate_type);
}

// Creates an If with a success and a fail path with the given assertion_expression. The only difference to
// create_for_template() is that we use a initialized specific Halt message on the fail path.
IfTrueNode* AssertionPredicateIfCreator::create_for_initialized(Node* new_control, const int if_opcode,
                                                                Node* assertion_expression,
                                                                const AssertionPredicateType assertion_predicate_type) {
  const char* halt_message = "Initialized Assertion Predicate cannot fail";
  return create(new_control, if_opcode, assertion_expression, halt_message, assertion_predicate_type);
}

// Creates the If node for an Assertion Predicate with a success path and a fail path having a Halt node:
//
//      new_control   assertion_expression
//                \   /
//                 If
//               /    \
//        success     fail path
//           proj      with Halt
//
IfTrueNode* AssertionPredicateIfCreator::create(Node* new_control, const int if_opcode, Node* assertion_expression,
                                                const char* halt_message,
                                                const AssertionPredicateType assertion_predicate_type) {
  assert(assertion_expression->is_OpaqueTemplateAssertionPredicate() ||
         assertion_expression->is_OpaqueInitializedAssertionPredicate(), "not a valid assertion expression");
  IdealLoopTree* loop = _phase->get_loop(new_control);
  IfNode* if_node = create_if_node(new_control, if_opcode, assertion_expression, loop, assertion_predicate_type);
  create_fail_path(if_node, loop, halt_message);
  return create_success_path(if_node, loop);
}

IfNode* AssertionPredicateIfCreator::create_if_node(Node* new_control, const int if_opcode, Node* assertion_expression,
                                                    IdealLoopTree* loop,
                                                    const AssertionPredicateType assertion_predicate_type) {
  IfNode* if_node;
  if (if_opcode == Op_If) {
    if_node = new IfNode(new_control, assertion_expression, PROB_MAX, COUNT_UNKNOWN, assertion_predicate_type);
  } else {
    assert(if_opcode == Op_RangeCheck, "must be range check");
    if_node = new RangeCheckNode(new_control, assertion_expression, PROB_MAX, COUNT_UNKNOWN, assertion_predicate_type);
  }
  _phase->register_control(if_node, loop, new_control);
  return if_node;
}

IfTrueNode* AssertionPredicateIfCreator::create_success_path(IfNode* if_node, IdealLoopTree* loop) {
  IfTrueNode* success_proj = new IfTrueNode(if_node);
  _phase->register_control(success_proj, loop, if_node);
  return success_proj;
}

void AssertionPredicateIfCreator::create_fail_path(IfNode* if_node, IdealLoopTree* loop, const char* halt_message) {
  IfFalseNode* fail_proj = new IfFalseNode(if_node);
  _phase->register_control(fail_proj, loop, if_node);
  create_halt_node(fail_proj, loop, halt_message);
}

void AssertionPredicateIfCreator::create_halt_node(IfFalseNode* fail_proj, IdealLoopTree* loop,
                                                   const char* halt_message) {
  StartNode* start_node = _phase->C->start();
  Node* frame = new ParmNode(start_node, TypeFunc::FramePtr);
  _phase->register_new_node(frame, start_node);
  Node* halt = new HaltNode(fail_proj, frame, halt_message);
  _phase->igvn().add_input_to(_phase->C->root(), halt);
  _phase->register_control(halt, loop, fail_proj);
}

// Creates an init and last value Template Assertion Predicate connected together from a Parse Predicate with an UCT on
// the failing path. Returns the success projection of the last value Template Assertion Predicate.
IfTrueNode* TemplateAssertionPredicateCreator::create_with_uncommon_trap(
    Node* new_control, ParsePredicateSuccessProj* parse_predicate_success_proj,
    const Deoptimization::DeoptReason deopt_reason, const int if_opcode) {
  OpaqueLoopInitNode* opaque_init = create_opaque_init(new_control);
  bool does_overflow;
  OpaqueTemplateAssertionPredicateNode* template_assertion_predicate_expression =
      create_for_init_value(new_control, opaque_init, does_overflow);
  IfTrueNode* template_predicate_success_proj =
      create_if_node_with_uncommon_trap(template_assertion_predicate_expression, parse_predicate_success_proj,
                                        deopt_reason, if_opcode, does_overflow,
                                        AssertionPredicateType::InitValue);
  template_assertion_predicate_expression = create_for_last_value(template_predicate_success_proj, opaque_init,
                                                                  does_overflow);
  return create_if_node_with_uncommon_trap(template_assertion_predicate_expression, parse_predicate_success_proj,
                                           deopt_reason, if_opcode, does_overflow,
                                           AssertionPredicateType::LastValue);
}

OpaqueLoopInitNode* TemplateAssertionPredicateCreator::create_opaque_init(Node* new_control) {
  OpaqueLoopInitNode* opaque_init = new OpaqueLoopInitNode(_phase->C, _loop_head->init_trip());
  _phase->register_new_node(opaque_init, new_control);
  return opaque_init;
}

OpaqueTemplateAssertionPredicateNode*
TemplateAssertionPredicateCreator::create_for_init_value(Node* new_control, OpaqueLoopInitNode* opaque_init,
                                                         bool& does_overflow) const {
  AssertionPredicateExpressionCreator expression_creator(_loop_head->stride_con(), _scale, _offset, _range, _phase);
  return expression_creator.create_for_template(new_control, opaque_init, does_overflow);
}

IfTrueNode* TemplateAssertionPredicateCreator::create_if_node_with_uncommon_trap(
    OpaqueTemplateAssertionPredicateNode* template_assertion_predicate_expression,
    ParsePredicateSuccessProj* parse_predicate_success_proj, const Deoptimization::DeoptReason deopt_reason,
    const int if_opcode, const bool does_overflow, const AssertionPredicateType assertion_predicate_type) {
  IfTrueNode* success_proj = _phase->create_new_if_for_predicate(parse_predicate_success_proj, nullptr, deopt_reason,
                                                                 does_overflow ? Op_If : if_opcode, false,
                                                                 assertion_predicate_type);
  _phase->igvn().replace_input_of(success_proj->in(0), 1, template_assertion_predicate_expression);
  return success_proj;
}

OpaqueTemplateAssertionPredicateNode*
TemplateAssertionPredicateCreator::create_for_last_value(Node* new_control, OpaqueLoopInitNode* opaque_init,
                                                         bool& does_overflow) const {
  Node* last_value = create_last_value(new_control, opaque_init);
  AssertionPredicateExpressionCreator expression_creator(_loop_head->stride_con(), _scale, _offset, _range, _phase);
  return expression_creator.create_for_template(new_control, last_value, does_overflow);
}

Node* TemplateAssertionPredicateCreator::create_last_value(Node* new_control, OpaqueLoopInitNode* opaque_init) const {
  Node* init_stride = _loop_head->stride();
  Node* opaque_stride = new OpaqueLoopStrideNode(_phase->C, init_stride);
  _phase->register_new_node(opaque_stride, new_control);
  Node* last_value = new SubINode(opaque_stride, init_stride);
  _phase->register_new_node(last_value, new_control);
  last_value = new AddINode(opaque_init, last_value);
  _phase->register_new_node(last_value, new_control);
  // init + (current stride - initial stride) is within the loop so narrow its type by leveraging the type of the iv phi
  last_value = new CastIINode(new_control, last_value, _loop_head->phi()->bottom_type());
  _phase->register_new_node(last_value, new_control);
  return last_value;
}

IfTrueNode* TemplateAssertionPredicateCreator::create_if_node_with_halt(
    Node* new_control, OpaqueTemplateAssertionPredicateNode* template_assertion_predicate_expression,
    const bool does_overflow, const AssertionPredicateType assertion_predicate_type) {
  AssertionPredicateIfCreator assertion_predicate_if_creator(_phase);
  return assertion_predicate_if_creator.create_for_template(new_control, does_overflow ? Op_If : Op_RangeCheck,
                                                            template_assertion_predicate_expression,
                                                            assertion_predicate_type);
}

// Creates an init and last value Template Assertion Predicate connected together with a Halt node on the failing path.
// Returns the success projection of the last value Template Assertion Predicate latter.
IfTrueNode* TemplateAssertionPredicateCreator::create_with_halt(Node* new_control) {
  OpaqueLoopInitNode* opaque_init = create_opaque_init(new_control);
  bool does_overflow;
  OpaqueTemplateAssertionPredicateNode* template_assertion_predicate_expression =
      create_for_init_value(new_control, opaque_init, does_overflow);
  IfTrueNode* template_predicate_success_proj =
      create_if_node_with_halt(new_control, template_assertion_predicate_expression, does_overflow,
                               AssertionPredicateType::InitValue);
  template_assertion_predicate_expression = create_for_last_value(template_predicate_success_proj, opaque_init,
                                                                  does_overflow);
  return create_if_node_with_halt(template_predicate_success_proj, template_assertion_predicate_expression,
                                  does_overflow, AssertionPredicateType::LastValue);
}

InitializedAssertionPredicateCreator::InitializedAssertionPredicateCreator(PhaseIdealLoop* phase)
    : _phase(phase) {}

// Create an Initialized Assertion Predicate from the provided template_assertion_predicate at 'new_control'.
// We clone the Template Assertion Expression and replace:
// - OpaqueTemplateAssertionPredicateNode with OpaqueInitializedAssertionPredicate
// - OpaqueLoop*Nodes with new_init and _ew_stride, respectively.
//
//             /         init                 stride
//             |           |                    |
//             |  OpaqueLoopInitNode  OpaqueLoopStrideNode                        /        new_init    new_stride
//  Template   |                 \     /                                          |              \     /
//  Assertion  |                   ...                                 Assertion  |                ...
//  Expression |                    |                                  Expression |                 |
//             |                   Bool                                           |              new Bool
//             |                    |                                             |                 |
//             \      OpaqueTemplateAssertionPredicate    ===>    new_control     \  OpaqueInitializedAssertionPredicate
//                                  |                                        \      /
//                                 If                                         new If
//                               /    \                                       /    \
//                         success     fail path                     new success   new Halt
//                           proj    (Halt or UCT)                       proj
//
IfTrueNode* InitializedAssertionPredicateCreator::create_from_template(IfNode* template_assertion_predicate,
                                                                       Node* new_control, Node* new_init,
                                                                       Node* new_stride) {
  OpaqueInitializedAssertionPredicateNode* assertion_expression =
      create_assertion_expression_from_template(template_assertion_predicate, new_control, new_init, new_stride);
  return create_control_nodes(new_control, template_assertion_predicate->Opcode(), assertion_expression,
                              template_assertion_predicate->assertion_predicate_type());
}

// Create a new Initialized Assertion Predicate from 'template_assertion_predicate' by cloning it but omitting the
// OpaqueLoop*Notes (i.e. taking their inputs instead).
IfTrueNode* InitializedAssertionPredicateCreator::create_from_template(IfNode* template_assertion_predicate,
                                                                       Node* new_control) {
  OpaqueTemplateAssertionPredicateNode* template_opaque =
      template_assertion_predicate->in(1)->as_OpaqueTemplateAssertionPredicate();
  TemplateAssertionExpression template_assertion_expression(template_opaque);
  OpaqueInitializedAssertionPredicateNode* assertion_expression =
      template_assertion_expression.clone_and_fold_opaque_loop_nodes(new_control, _phase);
  return create_control_nodes(new_control, template_assertion_predicate->Opcode(), assertion_expression,
                              template_assertion_predicate->assertion_predicate_type());
}

// Create a new Initialized Assertion Predicate directly without a template.
IfTrueNode* InitializedAssertionPredicateCreator::create(Node* operand, Node* new_control, const jint stride,
                                                         const int scale, Node* offset, Node* range,
                                                         const AssertionPredicateType assertion_predicate_type) {
  AssertionPredicateExpressionCreator expression_creator(stride, scale, offset, range, _phase);
  bool does_overflow;
  OpaqueInitializedAssertionPredicateNode* assertion_expression =
      expression_creator.create_for_initialized(new_control, operand, does_overflow);
  return create_control_nodes(new_control, does_overflow ? Op_If : Op_RangeCheck, assertion_expression,
                              assertion_predicate_type);
}

// Creates the CFG nodes for the Initialized Assertion Predicate.
IfTrueNode* InitializedAssertionPredicateCreator::create_control_nodes(
    Node* new_control, const int if_opcode, OpaqueInitializedAssertionPredicateNode* assertion_expression,
    const AssertionPredicateType assertion_predicate_type) {
  AssertionPredicateIfCreator assertion_predicate_if_creator(_phase);
  return assertion_predicate_if_creator.create_for_initialized(new_control, if_opcode, assertion_expression,
                                                               assertion_predicate_type);
}

// Create a new Assertion Expression based from the given template to be used as bool input for the Initialized
// Assertion Predicate IfNode.
OpaqueInitializedAssertionPredicateNode*
InitializedAssertionPredicateCreator::create_assertion_expression_from_template(IfNode* template_assertion_predicate,
                                                                                Node* new_control, Node* new_init,
                                                                                Node* new_stride) {
  OpaqueTemplateAssertionPredicateNode* template_opaque =
      template_assertion_predicate->in(1)->as_OpaqueTemplateAssertionPredicate();
  TemplateAssertionExpression template_assertion_expression(template_opaque);
  OpaqueTemplateAssertionPredicateNode* tmp_opaque =
      template_assertion_expression.clone_and_replace_init_and_stride(new_control, new_init, new_stride, _phase);
  OpaqueInitializedAssertionPredicateNode* assertion_expression =
      new OpaqueInitializedAssertionPredicateNode(tmp_opaque->in(1)->as_Bool(), _phase->C);
  _phase->register_new_node(assertion_expression, new_control);
  return assertion_expression;
}

#ifndef PRODUCT
void PredicateBlock::dump() const {
  dump("");
}

void PredicateBlock::dump(const char* prefix) const {
  if (is_non_empty()) {
    PredicatePrinter printer(prefix);
    PredicateBlockIterator iterator(_tail, _deopt_reason);
    iterator.for_each(printer);
  } else {
    tty->print_cr("%s- <empty>", prefix);
  }
}

// Dumps all predicates from the loop to the earliest predicate in a pretty format.
void Predicates::dump() const {
  if (has_any()) {
    Node* loop_head = _tail->unique_ctrl_out();
    tty->print_cr("%d %s:", loop_head->_idx, loop_head->Name());
    tty->print_cr("- Loop Limit Check Predicate Block:");
    _loop_limit_check_predicate_block.dump("  ");
    tty->print_cr("- Profiled Loop Predicate Block:");
    _profiled_loop_predicate_block.dump("  ");
    tty->print_cr("- Loop Predicate Block:");
    _loop_predicate_block.dump("  ");
    tty->cr();
  } else {
    tty->print_cr("<no predicates>");
  }
}

void Predicates::dump_at(Node* node) {
  Predicates predicates(node);
  predicates.dump();
}

// Debug method to dump all predicates that are found above 'loop_node'.
void Predicates::dump_for_loop(LoopNode* loop_node) {
  dump_at(loop_node->skip_strip_mined()->in(LoopNode::EntryControl));
}
#endif // NOT PRODUCT

// Keep track of whether we are in the correct Predicate Block where Template Assertion Predicates can be found.
// The PredicateIterator will always start at the loop entry and first visits the Loop Limit Check Predicate Block.
void CreateAssertionPredicatesVisitor::visit(const ParsePredicate& parse_predicate) {
  Deoptimization::DeoptReason deopt_reason = parse_predicate.head()->deopt_reason();
  if (deopt_reason == Deoptimization::Reason_predicate ||
      deopt_reason == Deoptimization::Reason_profile_predicate) {
    _has_hoisted_check_parse_predicates = true;
  }
}

void CreateAssertionPredicatesVisitor::visit(const TemplateAssertionPredicate& template_assertion_predicate) {
  if (!_has_hoisted_check_parse_predicates) {
    // Only process if we are in the correct Predicate Block.
    return;
  }
  if (_clone_template) {
    _new_control = clone_template_and_replace_init_input(template_assertion_predicate);
  }
  _new_control = initialize_from_template(template_assertion_predicate);
}

// Create an Initialized Assertion Predicate from the provided Template Assertion Predicate.
IfTrueNode* CreateAssertionPredicatesVisitor::initialize_from_template(
    const TemplateAssertionPredicate& template_assertion_predicate) const {
  IfNode* template_head = template_assertion_predicate.head();
  IfTrueNode* initialized_predicate = _phase->create_initialized_assertion_predicate(template_head, _init, _stride,
                                                                                     _new_control);
  template_assertion_predicate.rewire_loop_data_dependencies(initialized_predicate, _node_in_loop_body, _phase);
  return initialized_predicate;
}

// Clone the provided 'template_assertion_predicate' and set '_init' as new input for the OpaqueLoopInitNode.
IfTrueNode* CreateAssertionPredicatesVisitor::clone_template_and_replace_init_input(
    const TemplateAssertionPredicate& template_assertion_predicate) {
  OpaqueLoopInitNode* opaque_init = new OpaqueLoopInitNode(_phase->C, _init);
  _phase->register_new_node(opaque_init, _new_control);
  return template_assertion_predicate.clone_and_replace_init(_new_control, opaque_init, _phase);
}

// Clone the Template Assertion Predicate and set a new input for the OpaqueLoopStrideNode.
void UpdateStrideForAssertionPredicates::visit(const TemplateAssertionPredicate& template_assertion_predicate) {
  if (!template_assertion_predicate.is_last_value()) {
    // Only Last Value Assertion Predicates have an OpaqueLoopStrideNode.
    return;
  }
  replace_opaque_stride_input(template_assertion_predicate);
  Node* template_tail_control_out = template_assertion_predicate.tail()->unique_ctrl_out();
  IfTrueNode* initialized_success_proj = initialize_from_updated_template(template_assertion_predicate);
  connect_initialized_assertion_predicate(template_tail_control_out, initialized_success_proj);
}

// Replace the input to OpaqueLoopStrideNode with 'new_stride' and leave the other nodes unchanged.
void UpdateStrideForAssertionPredicates::replace_opaque_stride_input(
    const TemplateAssertionPredicate& template_assertion_predicate) const {
  template_assertion_predicate.replace_opaque_stride_input(_new_stride, _phase->igvn());
}

IfTrueNode* UpdateStrideForAssertionPredicates::initialize_from_updated_template(
    const TemplateAssertionPredicate& template_assertion_predicate) const {
  IfTrueNode* initialized_success_proj = template_assertion_predicate.initialize(_phase, template_assertion_predicate.tail());
  return initialized_success_proj;
}

// The newly created Initialized Assertion Predicate can safely be inserted because this visitor is already visiting
// the Template Assertion Predicate above this. So, we will not accidentally visit this again and kill it with the
// visit() method for Initialized Assertion Predicates.
void UpdateStrideForAssertionPredicates::connect_initialized_assertion_predicate(
    Node* new_control_out, IfTrueNode* initialized_success_proj) const {
  if (new_control_out->is_Loop()) {
    _phase->igvn().replace_input_of(new_control_out, LoopNode::EntryControl, initialized_success_proj);
  } else {
    _phase->igvn().replace_input_of(new_control_out, 0, initialized_success_proj);
  }
  _phase->set_idom(new_control_out, initialized_success_proj, _phase->dom_depth(new_control_out));
}
