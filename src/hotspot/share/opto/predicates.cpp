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

// Walk over all Initialized Assertion Predicates and return the entry into the first Initialized Assertion Predicate
// (i.e. not belonging to an Initialized Assertion Predicate anymore)
Node* AssertionPredicatesWithHalt::find_entry(Node* start_proj) {
  Node* entry = start_proj;
  while (is_assertion_predicate_success_proj(entry)) {
    entry = entry->in(0)->in(0);
  }
  return entry;
}

bool AssertionPredicatesWithHalt::is_assertion_predicate_success_proj(const Node* predicate_proj) {
  if (predicate_proj == nullptr || !predicate_proj->is_IfProj() || !predicate_proj->in(0)->is_If()) {
    return false;
  }
  return has_opaque4(predicate_proj) && has_halt(predicate_proj);
}

// Check if the If node of `predicate_proj` has an Opaque4 node as input.
bool AssertionPredicatesWithHalt::has_opaque4(const Node* predicate_proj) {
  IfNode* iff = predicate_proj->in(0)->as_If();
  return iff->in(1)->Opcode() == Op_Opaque4;
}

// Check if the other projection (UCT projection) of `success_proj` has a Halt node as output.
bool AssertionPredicatesWithHalt::has_halt(const Node* success_proj) {
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

Deoptimization::DeoptReason RuntimePredicate::uncommon_trap_reason(IfProjNode* if_proj) {
    CallStaticJavaNode* uct_call = if_proj->is_uncommon_trap_if_pattern();
    if (uct_call == nullptr) {
      return Deoptimization::Reason_none;
    }
    return Deoptimization::trap_request_reason(uct_call->uncommon_trap_request());
}

bool RuntimePredicate::is_success_proj(Node* node, Deoptimization::DeoptReason deopt_reason) {
  if (may_be_runtime_predicate_if(node)) {
    return deopt_reason == uncommon_trap_reason(node->as_IfProj());
  } else {
    return false;
  }
}

// A Runtime Predicate must have an If or a RangeCheck node, while the If should not be a zero trip guard check.
bool RuntimePredicate::may_be_runtime_predicate_if(Node* node) {
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

  Node* transform_opaque_init(OpaqueLoopInitNode* opaque_init) override {
    return _phase->clone_and_register(opaque_init, _new_ctrl)->as_OpaqueLoopInit();
  }

  Node* transform_opaque_stride(OpaqueLoopStrideNode* opaque_stride) override {
    return _phase->clone_and_register(opaque_stride, _new_ctrl)->as_OpaqueLoopStride();
  }
};

// Creates an identical clone of this Template Assertion Predicate Expression (i.e.cloning all nodes from the Opaque4Node
// to and including the OpaqueLoop* nodes). The cloned nodes are rewired to reflect the same graph structure as found for
// this Template Assertion Predicate Expression. The cloned nodes get 'new_ctrl' as ctrl. There is no other update done
// for the cloned nodes. Return the newly cloned Opaque4Node.
Opaque4Node* TemplateAssertionPredicateExpression::clone(Node* new_ctrl, PhaseIdealLoop* phase) {
  CloneStrategy clone_init_and_stride_strategy(phase, new_ctrl);
  return clone(clone_init_and_stride_strategy, new_ctrl, phase);
}

// Class to collect data nodes from a source to target nodes by following the inputs of the source node recursively.
// The class takes a node filter to decide which input nodes to follow and a target node predicate to start backtracking
// from. All nodes found on all paths from source->target(s) returned in a Unique_Node_List (without duplicates).
class DataNodesOnPathToTargets : public StackObj {
  typedef bool (*NodeCheck)(const Node*);

  NodeCheck _node_filter; // Node filter function to decide if we should process a node or not while searching for targets.
  NodeCheck _is_target_node; // Function to decide if a node is a target node (i.e. where we should start backtracking).
  Node_Stack _stack; // Stack that stores entries of the form: [Node* node, int next_unvisited_input_index_of_node].
  VectorSet _visited; // Ensure that we are not visiting a node twice in the DFS walk which could happen with diamonds.
  Unique_Node_List _collected_nodes; // The resulting node collection of all nodes on paths from source->target(s).

 public:
  DataNodesOnPathToTargets(NodeCheck node_filter, NodeCheck is_target_node)
      : _node_filter(node_filter),
        _is_target_node(is_target_node),
        _stack(2) {}
  NONCOPYABLE(DataNodesOnPathToTargets);

  // Collect all input nodes from 'start_node'->target(s) by applying the node filter to discover new input nodes and
  // the target node predicate to stop discovering more inputs and start backtracking. The implementation is done
  // with an iterative DFS walk including a visited set to avoid redundant double visits when encountering a diamand
  // shape which could consume a lot of unnecessary time.
  const Unique_Node_List& collect(Node* start_node) {
    assert(_collected_nodes.size() == 0, "should not call this method twice in a row");
    assert(!_is_target_node(start_node), "no trivial paths where start node is also a target");

    push_unvisited_node(start_node);
    while (_stack.is_nonempty()) {
      Node* current = _stack.node();
      _visited.set(current->_idx);
      if (_is_target_node(current)) {
        // Target node? Do not visit its inputs and begin backtracking.
        _collected_nodes.push(current);
        pop_target_node_and_collect_predecessor();
      } else if (!push_next_unvisited_input()) {
        // All inputs visited. Continue backtracking.
        pop_node_and_maybe_collect_predecessor();
      }
    }
    return _collected_nodes;
  }

 private:
  // The predecessor (just below the target node (currently on top) on the stack) is also on the path from
  // start->target. Collect it and pop the target node from the top of the stack.
  void pop_target_node_and_collect_predecessor() {
    _stack.pop();
    assert(_stack.is_nonempty(), "target nodes should not be start nodes");
    _collected_nodes.push(_stack.node());
  }

  // Push the next unvisited input node of the current node on top of the stack by using its stored associated input index:
  //
  //                        Stack:
  //      I1  I2  I3        [current, 2] // Index 2 means that I1 (first data node at index 1) was visited before.
  //       \  |  /                       // The next unvisited input is I2. Visit I2 by pushing a new entry [I2, 1]
  //   Y   current                       // and update the index [current, 2] -> [current, 3] to visit I3 once 'current'
  //    \  /                             // is on top of stack again later in DFS walk.
  //     X                  [X, 3]       // Index 3 points past node input array which means that there are no more inputs
  //                                     // to visit. Once X is on top of stack again, we are done with 'X' and pop it.
  //
  // If an input was already collected before (i.e. part of start->target), then the current node is part of some kind
  // of diamond with it:
  //
  //        C3       X
  //       /  \     /
  //     C2   current
  //      \  /
  //       C1
  //
  // Cx means collected. Since C3 is already collected (and thus already visited), we add 'current' to the collected list
  // since it must also be on a path from start->target. We continue the DFS with X which could potentially also be on a
  // start->target path but that is not known yet.
  //
  // This method returns true if there is an unvisited input and return false otherwise if all inputs have been visited.
  bool push_next_unvisited_input() {
    Node* current = _stack.node();
    const uint next_unvisited_input_index = _stack.index();
    for (uint i = next_unvisited_input_index; i < current->req(); i++) {
      Node* input = current->in(i);
      if (_node_filter(input)) {
        if (!_visited.test(input->_idx)) { // Avoid double visits which could take a long time to process.
          // Visit current->in(i) next in DFS walk. Once 'current' is again on top of stack, we need to visit in(i+1).
          push_input_and_update_current_index(input, i + 1);
          return true;
        } else if (_collected_nodes.member(input)) {
          // Diamond case, see description above.
          // Input node part of start->target? Then current node (i.e. a predecessor of input) is also on path. Collect it.
          _collected_nodes.push(current);
        }
      }
    }
    return false;
  }

  // Update the index of the current node on top of the stack with the next unvisited input index and push 'input' to
  // the stack which is visited next in the DFS order.
  void push_input_and_update_current_index(Node* input, uint next_unvisited_input_index) {
    _stack.set_index(next_unvisited_input_index);
    push_unvisited_node(input);
  }

  // Push the next unvisited node in the DFS order with index 1 since this node needs to visit all its inputs.
  void push_unvisited_node(Node* next_to_visit) {
    _stack.push(next_to_visit, 1);
  }

  // If the current node on top of the stack is on a path from start->target(s), then also collect the predecessor node
  // before popping the current node.
  void pop_node_and_maybe_collect_predecessor() {
    Node* current_node = _stack.node();
    _stack.pop();
    if (_stack.is_nonempty() && _collected_nodes.member(current_node)) {
      // Current node was part of start->target? Then predecessor (i.e. newly on top of stack) is also on path. Collect it.
      Node* predecessor = _stack.node();
      _collected_nodes.push(predecessor);
    }
  }
};

// Clones this Template Assertion Predicate Expression and applies the given strategy to transform the OpaqueLoop* nodes.
Opaque4Node* TemplateAssertionPredicateExpression::clone(TransformStrategyForOpaqueLoopNodes& transform_strategy,
                                                         Node* new_ctrl, PhaseIdealLoop* phase) {
  ResourceMark rm;
  auto is_opaque_loop_node = [](const Node* node) {
    return node->is_Opaque1();
  };
  DataNodesOnPathToTargets data_nodes_on_path_to_targets(TemplateAssertionPredicateExpression::maybe_contains,
                                                         is_opaque_loop_node);
  const Unique_Node_List& collected_nodes = data_nodes_on_path_to_targets.collect(_opaque4_node);
  DataNodeGraph data_node_graph(collected_nodes, phase);
  const OrigToNewHashtable& orig_to_new = data_node_graph.clone_with_opaque_loop_transform_strategy(transform_strategy, new_ctrl);
  assert(orig_to_new.contains(_opaque4_node), "must exist");
  Node* opaque4_clone = *orig_to_new.get(_opaque4_node);
  return opaque4_clone->as_Opaque4();
}
