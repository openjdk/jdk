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
#include "opto/predicates.hpp"
#include "opto/subnode.hpp"

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

TemplateAssertionPredicateBool::TemplateAssertionPredicateBool(Node* source_bool) : _source_bool(source_bool->as_Bool()) {
#ifdef ASSERT
  // During IGVN, we could have multiple outputs of the _source_bool, for example, when the backedge of the loop of
  // this Template Assertion Predicate is about to die and the CastII on the last value bool already folded to a
  // constant (i.e. no OpaqueLoop* nodes anymore). Then IGVN could already have commoned up the bool with the bool of
  // one of the Hoisted Check Predicates. Just check that the Template Assertion Predicate is one of the outputs.
  bool has_template_output = false;
  for (DUIterator_Fast imax, i = source_bool->fast_outs(imax); i < imax; i++) {
    Node* out = source_bool->fast_out(i);
    if (out->Opcode() == Op_Opaque4) {
      has_template_output = true;
      break;
    }
  }
  assert(has_template_output, "must find Template Assertion Predicate as output");
#endif // ASSERT
}

// Stack used when performing DFS on a Template Assertion Predicate Bool. The DFS traversal visits non-CFG inputs of a
// node in increasing node index order (i.e. first visiting the input node at index 1). Each time a new node is visited,
// it is inserted on top of the stack. Each node n in the stack maintains a node input index i, denoted as [n, i]:
//
// i = 0, [n, 0]:
//     n is currently being visited for the first time in the DFS traversal and was newly added to the stack
//
// i > 0, [n, i]:
//     Let node s be the next node being visited after node n in the DFS traversal. The following holds:
//         n->in(i) = s
class DFSNodeStack : public StackObj {
  Node_Stack _stack;
  static const uint _no_inputs_visited_yet = 0;

 public:
  explicit DFSNodeStack(BoolNode* template_bool)
      : _stack(2) {
        _stack.push(template_bool, _no_inputs_visited_yet);
  }

  // Push the next unvisited input of the current node on top of the stack and return true. The visiting order of node
  // inputs is in increasing node input index order. If there are no unvisited inputs left, do nothing and return false.
  bool push_next_unvisited_input() {
    Node* node_on_top = top();
    increment_top_node_input_index();
    const uint next_unvisited_input = _stack.index();
    for (uint index = next_unvisited_input; index < node_on_top->req(); index++) {
      Node* input = node_on_top->in(index);
      if (TemplateAssertionPredicateBool::could_be_part(input)) {
        // We only care about nodes that could possibly be part of a Template Assertion Predicate Bool.
        _stack.set_index(index);
        _stack.push(input, _no_inputs_visited_yet);
        return true;
      }
    }
    return false;
  }

  Node* top() const {
    return _stack.node();
  }

  uint node_index_to_previously_visited_parent() const {
    return _stack.index();
  }

  bool is_not_empty() const {
    return _stack.size() > 0;
  }

  Node* pop() {
    Node* popped_node = top();
    _stack.pop();
    return popped_node;
  }

  void increment_top_node_input_index() {
    _stack.set_index(_stack.index() + 1);
  }

  void replace_top_with(Node* node) {
    _stack.set_node(node);
  }
};

// Interface to transform OpaqueLoop* nodes of a Template Assertion Predicate Bool. The transformations must return a
// new or different existing node.
class TransformOpaqueLoopNodes : public StackObj {
 public:
  virtual Node* transform_opaque_init(OpaqueLoopInitNode* opaque_init) = 0;
  virtual Node* transform_opaque_stride(OpaqueLoopStrideNode* opaque_stride) = 0;
};

// Class to clone a Template Assertion Predicate Bool. The BoolNode and all the nodes up to but excluding the OpaqueLoop*
// nodes are cloned. The OpaqueLoop* nodes are transformed by the provided strategy (e.g. cloned or replaced).
class CloneTemplateAssertionPredicateBool : public StackObj {
  DFSNodeStack _stack;
  PhaseIdealLoop* _phase;
  uint _index_before_cloning;
  Node* _ctrl_for_clones;
  DEBUG_ONLY(bool _found_init;)

  // Replace the OpaqueLoop*Node opaque_loop_node currently on top of the stack by transforming it with the provided
  // strategy. The transformation must return a new or an existing node other than the OpaqueLoop* node itself.
  void transform_opaque_loop_node(const Node* opaque_loop_node, TransformOpaqueLoopNodes* transform_opaque_nodes) {
    assert(opaque_loop_node == _stack.top(), "must be top node");
    Node* transformed_node;
    if (opaque_loop_node->is_OpaqueLoopInit()) {
      DEBUG_ONLY(_found_init = true;)
      transformed_node = transform_opaque_nodes->transform_opaque_init(opaque_loop_node->as_OpaqueLoopInit());
    } else {
      transformed_node = transform_opaque_nodes->transform_opaque_stride(opaque_loop_node->as_OpaqueLoopStride());
    }
    assert(transformed_node != opaque_loop_node, "OpaqueLoop*Node must have been transformed");
    _stack.replace_top_with(transformed_node);
  }

  // Similar to pop_node() but handles only newly transformed OpaqueLoop* nodes.
  void pop_transformed_opaque_loop_node() {
    Node* transformed_opaque_loop_node = _stack.pop();
    assert(_stack.is_not_empty(), "must not be empty when popping a transformed OpaqueLoop*Node");
    if (must_clone_node_on_top(transformed_opaque_loop_node)) {
      clone_and_replace_top_node();
    }
    // Rewire the current node on top (child of old OpaqueLoop*Node) to the newly transformed node.
    rewire_node_on_top_to(transformed_opaque_loop_node);
  }

  // Must only clone current top node if the following two conditions hold:
  // (1) [top, i], top->in(i) != previously_visited_parent
  // (2) top is not a clone.
  // If (1) is false, then we have not transformed or cloned previously_visited_parent and thus know that
  // previously_visited_parent is not part of the Template Assertion Predicate Bool (i.e. not on the chain to an
  // OpaqueLoop* node). We don't need to clone top at this point as it might also not be part of the Template Assertion
  // Predicate Bool.
  // If (1) is true then previously_visited_parent is part of the Template Assertion Predicate Bool. But if top was
  // already cloned, we do not need to clone it again to avoid duplicates.
  bool must_clone_node_on_top(Node* previously_visited_parent) {
    Node* child_of_previously_visited_parent = _stack.top();
    const uint node_index_to_previously_visited_parent = _stack.node_index_to_previously_visited_parent();
    return child_of_previously_visited_parent->_idx < _index_before_cloning && // (2)
           child_of_previously_visited_parent->in(node_index_to_previously_visited_parent) != previously_visited_parent; // (1)
  }

  // `new_parent` replaced the old parent of the current node on top of the stack for which we stored
  //     [top, i]
  // where
  //     top->in(i) = old_parent.
  // We clone the current node on top and replace it in the stack such that
  //     [cloned_top, i]
  void clone_and_replace_top_node() {
    Node* top = _stack.top();
    Node* cloned_top = _phase->clone_and_register(top, _ctrl_for_clones);
    _stack.replace_top_with(cloned_top);
  }

  // Pop a node from the stack and do the following with the node now being on top:
  // - Is top node part of Template Assertion Predicate Bool?
  //   - No? Done.
  //   - Yes:
  //     - Cloned before?
  //       - No? Clone and replace node on top with cloned version.
  //     - Rewire the node on top to the previous node on top (i.e. the cloned node to its new cloned/transformed
  //       parent).
  void pop_node() {
    Node* previously_visited_parent = _stack.pop();
    if (_stack.is_not_empty()) {
      if (must_clone_node_on_top(previously_visited_parent)) {
        clone_and_replace_top_node();
        rewire_node_on_top_to(previously_visited_parent);
      } else if (is_cloned_node(previously_visited_parent)) {
        rewire_node_on_top_to(previously_visited_parent);
      } // Else: Node is not part of the Template Assertion Predicate Bool (i.e. not on the chain to an OpaqueLoop* node)
    }
  }

  bool is_cloned_node(Node* node) const {
    return node->_idx >= _index_before_cloning;
  }

  // `new_parent` replaced the old parent of the current node on top of the stack for which we stored:
  //     [top, i]
  // where
  //     top->in(i) = old_parent.
  // We rewire the top node to the new parent such that:
  //     top->in(i) = new_parent
  void rewire_node_on_top_to(Node* new_parent) {
    Node* top = _stack.top();
    assert(is_cloned_node(top) && !top->is_Opaque1(), "must be cloned node on chain to OpaqueLoop*Node (excluded)");
    const uint index_to_old_parent = _stack.node_index_to_previously_visited_parent();
    top->set_req(index_to_old_parent, new_parent);
  }

 public:
  CloneTemplateAssertionPredicateBool(BoolNode* template_bool, Node* ctrl_for_clones, PhaseIdealLoop* phase)
      : _stack(template_bool),
        _phase(phase),
        _index_before_cloning(phase->C->unique()),
        _ctrl_for_clones(ctrl_for_clones)
  DEBUG_ONLY(COMMA _found_init(false)) {}

  // Look for the OpaqueLoop* nodes to transform them with the strategy defined with 'transform_opaque_loop_nodes'.
  // Clone all nodes in between.
  BoolNode* clone(TransformOpaqueLoopNodes* transform_opaque_loop_nodes) {
    Node* current;
    while (_stack.is_not_empty()) {
      current = _stack.top();
      if (current->is_Opaque1()) {
        transform_opaque_loop_node(current, transform_opaque_loop_nodes);
        pop_transformed_opaque_loop_node();
      } else if (!_stack.push_next_unvisited_input()) {
        pop_node();
      }
    }
    assert(current->is_Bool() && current->_idx >= _index_before_cloning, "new BoolNode expected");
    assert(_found_init, "OpaqueLoopInitNode must always be found");
    return current->as_Bool();
  }
};

// This class caches a single OpaqueLoopInitNode and OpaqueLoopStrideNode. If the node is not cached, yet, we clone it
// and store the clone in the cache to be returned for subsequent calls.
class CachedOpaqueLoopNodes {
  OpaqueLoopInitNode* _cached_opaque_new_init;
  OpaqueLoopStrideNode* _cached_new_opaque_stride;
  PhaseIdealLoop* _phase;
  Node* _new_ctrl;

 public:
  CachedOpaqueLoopNodes(PhaseIdealLoop* phase, Node* new_ctrl)
      : _cached_opaque_new_init(nullptr),
        _cached_new_opaque_stride(nullptr),
        _phase(phase),
        _new_ctrl(new_ctrl) {}

  OpaqueLoopInitNode* clone_if_not_cached(OpaqueLoopInitNode* opaque_init) {
    if (_cached_opaque_new_init == nullptr) {
      _cached_opaque_new_init = _phase->clone_and_register(opaque_init, _new_ctrl)->as_OpaqueLoopInit();
    }
    return _cached_opaque_new_init;
  }

  OpaqueLoopStrideNode* clone_if_not_cached(OpaqueLoopStrideNode* opaque_stride) {
    if (_cached_new_opaque_stride == nullptr) {
      _cached_new_opaque_stride = _phase->clone_and_register(opaque_stride, _new_ctrl)->as_OpaqueLoopStride();
    }
    return _cached_new_opaque_stride;
  }
};

// The transformations of this class clone the existing OpaqueLoop* nodes without any other update.
class CloneOpaqueLoopNodes : public TransformOpaqueLoopNodes {
  CachedOpaqueLoopNodes _cached_opaque_loop_nodes;

 public:
  CloneOpaqueLoopNodes(PhaseIdealLoop* phase, Node* new_ctrl)
      : _cached_opaque_loop_nodes(phase, new_ctrl) {}

  Node* transform_opaque_init(OpaqueLoopInitNode* opaque_init) override {
    return _cached_opaque_loop_nodes.clone_if_not_cached(opaque_init);
  }

  Node* transform_opaque_stride(OpaqueLoopStrideNode* opaque_stride) override {
    return _cached_opaque_loop_nodes.clone_if_not_cached(opaque_stride);
  }
};

// Clones this Template Assertion Predicate Bool. This includes all nodes from the BoolNode to the OpaqueLoop* nodes.
// The cloned nodes are not updated.
BoolNode* TemplateAssertionPredicateBool::clone(Node* new_ctrl, PhaseIdealLoop* phase) {
  CloneOpaqueLoopNodes clone_opaque_loop_nodes(phase, new_ctrl);
  CloneTemplateAssertionPredicateBool clone_template_assertion_predicate_bool(_source_bool, new_ctrl, phase);
  return clone_template_assertion_predicate_bool.clone(&clone_opaque_loop_nodes);
}

// The transformations of this class clone the existing OpaqueLoopStrideNode and replace the OpaqueLoopInitNode with
// a new node.
class CloneWithNewInit : public TransformOpaqueLoopNodes {
  Node* _new_init;
  CachedOpaqueLoopNodes _cached_opaque_loop_nodes;

 public:
  CloneWithNewInit(PhaseIdealLoop* phase, Node* new_ctrl, Node* new_init)
      : _new_init(new_init),
        _cached_opaque_loop_nodes(phase, new_ctrl) {}

  Node* transform_opaque_init(OpaqueLoopInitNode* opaque_init) override {
    return _new_init;
  }

  Node* transform_opaque_stride(OpaqueLoopStrideNode* opaque_stride) override {
    return _cached_opaque_loop_nodes.clone_if_not_cached(opaque_stride);
  }
};

// Clones this Template Assertion Predicate Bool including the OpaqueLoopStrideNode (includes all nodes from the BoolNode
// to the OpaqueLoopStrideNode). The OpaqueLoopInitNode is replaced with a new node.
BoolNode* TemplateAssertionPredicateBool::clone_and_replace_init(Node* new_ctrl, Node* new_init,
                                                                 PhaseIdealLoop* phase) {
  CloneWithNewInit clone_with_new_init(phase, new_ctrl, new_init);
  CloneTemplateAssertionPredicateBool clone_template_assertion_predicate_bool(_source_bool, new_ctrl, phase);
  return clone_template_assertion_predicate_bool.clone(&clone_with_new_init);
}


// The transformations of this class replace the existing OpaqueLoop* nodes with new nodes.
class ReplaceOpaqueLoopNodes : public TransformOpaqueLoopNodes {
  Node* _new_init;
  Node* _new_stride;

 public:
  ReplaceOpaqueLoopNodes(Node* new_init, Node* new_stride) : _new_init(new_init), _new_stride(new_stride) {}

  Node* transform_opaque_init(OpaqueLoopInitNode* opaque_init) override {
    return _new_init;
  }

  Node* transform_opaque_stride(OpaqueLoopStrideNode* opaque_stride) override {
    return _new_stride;
  }
};

// Clones this Template Assertion Predicate Bool and replaces the OpaqueLoop* nodes with new nodes.
BoolNode* TemplateAssertionPredicateBool::clone_and_replace_opaque_loop_nodes(Node* new_ctrl, Node* new_init,
                                                                              Node* new_stride, PhaseIdealLoop* phase) {
  ReplaceOpaqueLoopNodes replaceOpaqueLoopNodes(new_init, new_stride);
  CloneTemplateAssertionPredicateBool clone_template_assertion_predicate_bool(_source_bool, new_ctrl, phase);
  return clone_template_assertion_predicate_bool.clone(&replaceOpaqueLoopNodes);
}
