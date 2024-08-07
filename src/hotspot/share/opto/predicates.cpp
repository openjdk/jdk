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
#include "opto/callnode.hpp"
#include "opto/loopnode.hpp"
#include "opto/node.hpp"
#include "opto/predicates.hpp"
#include "opto/rootnode.hpp"

// Walk over all Initialized Assertion Predicates and return the entry into the first Initialized Assertion Predicate
// (i.e. not belonging to an Initialized Assertion Predicate anymore)
Node* AssertionPredicatesWithHalt::find_entry(Node* start_proj) {
  Node* entry = start_proj;
  while (AssertionPredicateWithHalt::is_predicate(entry)) {
    entry = entry->in(0)->in(0);
  }
  return entry;
}

bool AssertionPredicateWithHalt::is_predicate(const Node* maybe_success_proj) {
  if (maybe_success_proj == nullptr || !maybe_success_proj->is_IfProj() || !maybe_success_proj->in(0)->is_If()) {
    return false;
  }
  return has_assertion_predicate_opaque(maybe_success_proj) && has_halt(maybe_success_proj);
}

// Check if the If node of `predicate_proj` has an Opaque4 (Template Assertion Predicate) or an
// OpaqueInitializedAssertionPredicate (Initialized Assertion Predicate) node as input.
bool AssertionPredicateWithHalt::has_assertion_predicate_opaque(const Node* predicate_proj) {
  IfNode* iff = predicate_proj->in(0)->as_If();
  Node* bol = iff->in(1);
  return bol->is_Opaque4() || bol->is_OpaqueInitializedAssertionPredicate();
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

bool ParsePredicate::is_predicate(Node* maybe_success_proj) {
  if (!maybe_success_proj->is_IfProj()) {
    return false;
  }
  IfNode* if_node = maybe_success_proj->in(0)->as_If();
  return if_node->is_ParsePredicate();
}

Deoptimization::DeoptReason RegularPredicateWithUCT::uncommon_trap_reason(IfProjNode* if_proj) {
    CallStaticJavaNode* uct_call = if_proj->is_uncommon_trap_if_pattern();
    if (uct_call == nullptr) {
      return Deoptimization::Reason_none;
    }
    return Deoptimization::trap_request_reason(uct_call->uncommon_trap_request());
}

bool RegularPredicateWithUCT::is_predicate(Node* maybe_success_proj) {
  if (may_be_predicate_if(maybe_success_proj)) {
    IfProjNode* success_proj = maybe_success_proj->as_IfProj();
    const Deoptimization::DeoptReason deopt_reason = uncommon_trap_reason(success_proj);
    return (deopt_reason == Deoptimization::Reason_loop_limit_check ||
            deopt_reason == Deoptimization::Reason_predicate ||
            deopt_reason == Deoptimization::Reason_profile_predicate);
  } else {
    return false;
  }
}

bool RegularPredicateWithUCT::is_predicate(Node* node, Deoptimization::DeoptReason deopt_reason) {
  if (may_be_predicate_if(node)) {
    return deopt_reason == uncommon_trap_reason(node->as_IfProj());
  } else {
    return false;
  }
}

// A Runtime Predicate must have an If or a RangeCheck node, while the If should not be a zero trip guard check.
bool RegularPredicateWithUCT::may_be_predicate_if(Node* node) {
  if (node->is_IfProj()) {
    const IfNode* if_node = node->in(0)->as_If();
    const int opcode_if = if_node->Opcode();
    if ((opcode_if == Op_If && !if_node->is_zero_trip_guard())
        || opcode_if == Op_RangeCheck) {
      return true;
    }
  }
  return false;
}

bool RuntimePredicate::is_success_proj(Node* node, Deoptimization::DeoptReason deopt_reason) {
  return RegularPredicateWithUCT::is_predicate(node, deopt_reason);
}

ParsePredicateIterator::ParsePredicateIterator(const Predicates& predicates) : _current_index(0) {
  const PredicateBlock* loop_limit_check_predicate_block = predicates.loop_limit_check_predicate_block();
  if (loop_limit_check_predicate_block->has_parse_predicate()) {
    _parse_predicates.push(loop_limit_check_predicate_block->parse_predicate());
  }
  if (UseProfiledLoopPredicate) {
    const PredicateBlock* profiled_loop_predicate_block = predicates.profiled_loop_predicate_block();
    if (profiled_loop_predicate_block->has_parse_predicate()) {
      _parse_predicates.push(profiled_loop_predicate_block->parse_predicate());
    }
  }
  if (UseLoopPredicate) {
    const PredicateBlock* loop_predicate_block = predicates.loop_predicate_block();
    if (loop_predicate_block->has_parse_predicate()) {
      _parse_predicates.push(loop_predicate_block->parse_predicate());
    }
  }
}

ParsePredicateNode* ParsePredicateIterator::next() {
  assert(has_next(), "always check has_next() first");
  return _parse_predicates.at(_current_index++);
}

#ifdef ASSERT
// Check that the block has at most one Parse Predicate and that we only find Regular Predicate nodes (i.e. IfProj,
// If, or RangeCheck nodes).
void PredicateBlock::verify_block() {
  Node* next = _parse_predicate.entry(); // Skip unique Parse Predicate of this block if present
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

// Walk over all Regular Predicates of this block (if any) and return the first node not belonging to the block
// anymore (i.e. entry to the first Regular Predicate in this block if any or `regular_predicate_proj` otherwise).
Node* PredicateBlock::skip_regular_predicates(Node* regular_predicate_proj, Deoptimization::DeoptReason deopt_reason) {
  Node* entry = regular_predicate_proj;
  while (RuntimePredicate::is_success_proj(entry, deopt_reason)) {
    assert(entry->in(0)->as_If(), "must be If node");
    entry = entry->in(0)->in(0);
  }
  return entry;
}

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

// Creates an identical clone of this Template Assertion Expression (i.e.cloning all nodes from the Opaque4Node to and
// including the OpaqueLoop* nodes). The cloned nodes are rewired to reflect the same graph structure as found for this
// Template Assertion Expression. The cloned nodes get 'new_ctrl' as ctrl. There is no other update done for the cloned
// nodes. Return the newly cloned Opaque4Node.
Opaque4Node* TemplateAssertionExpression::clone(Node* new_ctrl, PhaseIdealLoop* phase) {
  CloneStrategy clone_init_and_stride_strategy(phase, new_ctrl);
  return clone(clone_init_and_stride_strategy, new_ctrl, phase);
}

// Same as clone() but instead of cloning the OpaqueLoopInitNode, we replace it with the provided 'new_init' node.
Opaque4Node* TemplateAssertionExpression::clone_and_replace_init(Node* new_init, Node* new_ctrl,
                                                                 PhaseIdealLoop* phase) {
  ReplaceInitAndCloneStrideStrategy replace_init_and_clone_stride_strategy(new_init, new_ctrl, phase);
  return clone(replace_init_and_clone_stride_strategy, new_ctrl, phase);
}

// Same as clone() but instead of cloning the OpaqueLoopInit and OpaqueLoopStride node, we replace them with the provided
// 'new_init' and 'new_stride' nodes, respectively.
Opaque4Node* TemplateAssertionExpression::clone_and_replace_init_and_stride(Node* new_init, Node* new_stride,
                                                                            Node* new_ctrl,
                                                                            PhaseIdealLoop* phase) {
  ReplaceInitAndStrideStrategy replace_init_and_stride_strategy(new_init, new_stride);
  return clone(replace_init_and_stride_strategy, new_ctrl, phase);
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
Opaque4Node* TemplateAssertionExpression::clone(const TransformStrategyForOpaqueLoopNodes& transform_strategy,
                                                Node* new_ctrl, PhaseIdealLoop* phase) {
  ResourceMark rm;
  auto is_opaque_loop_node = [](const Node* node) {
    return node->is_Opaque1();
  };
  DataNodesOnPathsToTargets data_nodes_on_path_to_targets(TemplateAssertionExpressionNode::is_maybe_in_expression,
                                                          is_opaque_loop_node);
  const Unique_Node_List& collected_nodes = data_nodes_on_path_to_targets.collect(_opaque4_node);
  DataNodeGraph data_node_graph(collected_nodes, phase);
  const OrigToNewHashtable& orig_to_new = data_node_graph.clone_with_opaque_loop_transform_strategy(transform_strategy, new_ctrl);
  assert(orig_to_new.contains(_opaque4_node), "must exist");
  Node* opaque4_clone = *orig_to_new.get(_opaque4_node);
  return opaque4_clone->as_Opaque4();
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
  return node->is_If() && node->in(1)->is_Opaque4();
}

InitializedAssertionPredicate::InitializedAssertionPredicate(IfNode* template_assertion_predicate, Node* new_init,
                                                             Node* new_stride, PhaseIdealLoop* phase)
    : _template_assertion_predicate(template_assertion_predicate),
      _new_init(new_init),
      _new_stride(new_stride),
      _phase(phase) {}

// Create an Initialized Assertion Predicate at the provided control from the _template_assertion_predicate.
// We clone the Template Assertion Expression and replace:
// - Opaque4 with OpaqueInitializedAssertionPredicate
// - OpaqueLoop*Nodes with _new_init and _new_stride, respectively.
//
//             /         init                 stride
//             |           |                    |
//             |  OpaqueLoopInitNode  OpaqueLoopStrideNode                      /       _new_init    _new_stride
//  Template   |                 \     /                                        |              \     /
//  Assertion  |                   ...                               Assertion  |                ...
//  Expression |                    |                                Expression |                 |
//             |                   Bool                                         |              new Bool
//             |                    |                                           |                 |
//             \                 Opaque4           ======>          control     \  OpaqueInitializedAssertionPredicate
//                                  |                                      \      /
//                                 If                                       new If
//                               /    \                                     /    \
//                         success     fail path                   new success   new Halt
//                           proj    (Halt or UCT)                     proj
//
IfTrueNode* InitializedAssertionPredicate::create(Node* control) {
  IdealLoopTree* loop = _phase->get_loop(control);
  OpaqueInitializedAssertionPredicateNode* assertion_expression = create_assertion_expression(control);
  IfNode* if_node = create_if_node(control, assertion_expression, loop);
  create_fail_path(if_node, loop);
  return create_success_path(if_node, loop);
}

// Create a new Assertion Expression to be used as bool input for the Initialized Assertion Predicate IfNode.
OpaqueInitializedAssertionPredicateNode* InitializedAssertionPredicate::create_assertion_expression(Node* control) {
  Opaque4Node* template_opaque = _template_assertion_predicate->in(1)->as_Opaque4();
  TemplateAssertionExpression template_assertion_expression(template_opaque);
  Opaque4Node* tmp_opaque = template_assertion_expression.clone_and_replace_init_and_stride(_new_init, _new_stride,
                                                                                            control, _phase);
  OpaqueInitializedAssertionPredicateNode* assertion_expression =
      new OpaqueInitializedAssertionPredicateNode(tmp_opaque->in(1)->as_Bool(), _phase->C);
  _phase->register_new_node(assertion_expression, control);
  return assertion_expression;
}

IfNode* InitializedAssertionPredicate::create_if_node(Node* control,
                                                      OpaqueInitializedAssertionPredicateNode* assertion_expression,
                                                      IdealLoopTree* loop) {
  const int if_opcode = _template_assertion_predicate->Opcode();
  NOT_PRODUCT(const AssertionPredicateType assertion_predicate_type = _template_assertion_predicate->assertion_predicate_type();)
  IfNode* if_node = if_opcode == Op_If ?
      new IfNode(control, assertion_expression, PROB_MAX, COUNT_UNKNOWN NOT_PRODUCT(COMMA assertion_predicate_type)) :
      new RangeCheckNode(control, assertion_expression, PROB_MAX, COUNT_UNKNOWN NOT_PRODUCT(COMMA assertion_predicate_type));
  _phase->register_control(if_node, loop, control);
  return if_node;
}

IfTrueNode* InitializedAssertionPredicate::create_success_path(IfNode* if_node, IdealLoopTree* loop) {
  IfTrueNode* success_proj = new IfTrueNode(if_node);
  _phase->register_control(success_proj, loop, if_node);
  return success_proj;
}

void InitializedAssertionPredicate::create_fail_path(IfNode* if_node, IdealLoopTree* loop) {
  IfFalseNode* fail_proj = new IfFalseNode(if_node);
  _phase->register_control(fail_proj, loop, if_node);
  create_halt_node(fail_proj, loop);
}

void InitializedAssertionPredicate::create_halt_node(IfFalseNode* fail_proj, IdealLoopTree* loop) {
  StartNode* start_node = _phase->C->start();
  Node* frame = new ParmNode(start_node, TypeFunc::FramePtr);
  _phase->register_new_node(frame, start_node);
  Node* halt = new HaltNode(fail_proj, frame, "Initialized Assertion Predicate cannot fail");
  _phase->igvn().add_input_to(_phase->C->root(), halt);
  _phase->register_control(halt, loop, fail_proj);
}

// Is current node pointed to by iterator a predicate?
bool PredicateEntryIterator::has_next() const {
    return ParsePredicate::is_predicate(_current) ||
           RegularPredicateWithUCT::is_predicate(_current) ||
           AssertionPredicateWithHalt::is_predicate(_current);
}

// Skip the current predicate pointed to by iterator by returning the input into the predicate. This could possibly be
// a non-predicate node.
Node* PredicateEntryIterator::next_entry() {
  assert(has_next(), "current must be predicate");
  _current = _current->in(0)->in(0);
  return _current;
}
