/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
#include "opto/loopnode.hpp"
#include "opto/addnode.hpp"
#include "opto/callnode.hpp"
#include "opto/castnode.hpp"
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/loopnode.hpp"
#include "opto/matcher.hpp"
#include "opto/mulnode.hpp"
#include "opto/opaquenode.hpp"
#include "opto/predicates.hpp"
#include "opto/rootnode.hpp"
#include "opto/subnode.hpp"
#include <fenv.h>
#include <math.h>

/*
 * The general idea of Loop Predication is to hoist a check inside a loop body by inserting a Hoisted Check Predicate with
 * an uncommon trap on the entry path to the loop. The old check inside the loop can be eliminated. If the condition of
 * the Hoisted Check Predicate fails at runtime, we'll execute the uncommon trap to avoid entering the loop which misses
 * the check. Loop Predication can currently remove array range checks and loop invariant checks (such as null checks).
 *
 * On top of these predicates added by Loop Predication, there are other kinds of predicates. A detailed description
 * about all predicates can be found in predicates.hpp.
*/

//-------------------------------register_control-------------------------
void PhaseIdealLoop::register_control(Node* n, IdealLoopTree *loop, Node* pred, bool update_body) {
  assert(n->is_CFG(), "msust be control node");
  _igvn.register_new_node_with_optimizer(n);
  if (update_body) {
    loop->_body.push(n);
  }
  set_loop(n, loop);
  // When called from beautify_loops() idom is not constructed yet.
  if (_idom != nullptr) {
    set_idom(n, pred, dom_depth(pred));
  }
}

//------------------------------create_new_if_for_predicate------------------------
// create a new if above the uct_if_pattern for the predicate to be promoted.
//
//          before                                after
//        ----------                           ----------
//           ctrl                                 ctrl
//            |                                     |
//            |                                     |
//            v                                     v
//           iff                                 new_iff
//          /    \                                /      \
//         /      \                              /        \
//        v        v                            v          v
//  uncommon_proj cont_proj                   if_uct     if_cont
// \      |        |                           |          |
//  \     |        |                           |          |
//   v    v        v                           |          v
//     rgn       loop                          |         iff
//      |                                      |        /     \
//      |                                      |       /       \
//      v                                      |      v         v
// uncommon_trap                               | uncommon_proj cont_proj
//                                           \  \    |           |
//                                            \  \   |           |
//                                             v  v  v           v
//                                               rgn           loop
//                                                |
//                                                |
//                                                v
//                                           uncommon_trap
//
//
// We will create a region to guard the uct call if there is no one there.
// The continuation projection (if_cont) of the new_iff is returned which
// is an IfTrue projection. This code is also used to clone predicates to cloned loops.
IfProjNode* PhaseIdealLoop::create_new_if_for_predicate(ParsePredicateSuccessProj* parse_predicate_proj, Node* new_entry,
                                                        Deoptimization::DeoptReason reason,
                                                        const int opcode, const bool rewire_uncommon_proj_phi_inputs) {
  assert(parse_predicate_proj->is_uncommon_trap_if_pattern(reason), "must be a uct if pattern!");
  ParsePredicateNode* parse_predicate = parse_predicate_proj->in(0)->as_ParsePredicate();

  ProjNode* uncommon_proj = parse_predicate->proj_out(false);
  Node* uct_region = uncommon_proj->unique_ctrl_out();
  assert(uct_region->is_Region() || uct_region->is_Call(), "must be a region or call uct");

  uint proj_index = 1; // region's edge corresponding to uncommon_proj
  if (!uct_region->is_Region()) { // create a region to guard the call
    assert(uct_region->is_Call(), "must be call uct");
    CallNode* call = uct_region->as_Call();
    IdealLoopTree* loop = get_loop(call);
    uct_region = new RegionNode(1);
    Node* uncommon_proj_orig = uncommon_proj;
    uncommon_proj = uncommon_proj->clone()->as_Proj();
    register_control(uncommon_proj, loop, parse_predicate);
    uct_region->add_req(uncommon_proj);
    register_control(uct_region, loop, uncommon_proj);
    _igvn.replace_input_of(call, 0, uct_region);
    // When called from beautify_loops() idom is not constructed yet.
    if (_idom != nullptr) {
      set_idom(call, uct_region, dom_depth(uct_region));
    }
    // Move nodes pinned on the projection or whose control is set to
    // the projection to the region.
    lazy_replace(uncommon_proj_orig, uct_region);
  } else {
    // Find region's edge corresponding to uncommon_proj
    for (; proj_index < uct_region->req(); proj_index++)
      if (uct_region->in(proj_index) == uncommon_proj) break;
    assert(proj_index < uct_region->req(), "sanity");
  }

  Node* entry = parse_predicate->in(0);
  if (new_entry != nullptr) {
    // Cloning the predicate to new location.
    entry = new_entry;
  }
  // Create new_iff
  IdealLoopTree* lp = get_loop(entry);
  IfNode* new_iff = nullptr;
  switch (opcode) {
    case Op_If:
      new_iff = new IfNode(entry, parse_predicate->in(1), parse_predicate->_prob, parse_predicate->_fcnt);
      break;
    case Op_RangeCheck:
      new_iff = new RangeCheckNode(entry, parse_predicate->in(1), parse_predicate->_prob, parse_predicate->_fcnt);
      break;
    case Op_ParsePredicate:
      new_iff = new ParsePredicateNode(entry, reason, &_igvn);
      break;
    default:
      fatal("no other If variant here");
  }
  register_control(new_iff, lp, entry);
  IfProjNode* if_cont = new IfTrueNode(new_iff);
  IfProjNode* if_uct  = new IfFalseNode(new_iff);

  register_control(if_cont, lp, new_iff);
  register_control(if_uct, get_loop(uct_region), new_iff);

  _igvn.add_input_to(uct_region, if_uct);

  // If rgn has phis add new edges which has the same
  // value as on original uncommon_proj pass.
  assert(uct_region->in(uct_region->req() - 1) == if_uct, "new edge should be last");
  bool has_phi = false;
  for (DUIterator_Fast imax, i = uct_region->fast_outs(imax); i < imax; i++) {
    Node* use = uct_region->fast_out(i);
    if (use->is_Phi() && use->outcnt() > 0) {
      assert(use->in(0) == uct_region, "");
      _igvn.rehash_node_delayed(use);
      Node* phi_input = use->in(proj_index);

      if (uncommon_proj->outcnt() > 1 && !phi_input->is_CFG() && !phi_input->is_Phi() && get_ctrl(phi_input) == uncommon_proj) {
        // There are some control dependent nodes on the uncommon projection. We cannot simply reuse these data nodes.
        // We either need to rewire them from the old uncommon projection to the newly created uncommon proj (if the old
        // If is dying) or clone them and update their control (if the old If is not dying).
        if (rewire_uncommon_proj_phi_inputs) {
          // Replace phi input for the old uncommon projection with TOP as the If is dying anyways. Reuse the old data
          // nodes by simply updating control inputs and ctrl.
          _igvn.replace_input_of(use, proj_index, C->top());
          set_ctrl_of_nodes_with_same_ctrl(phi_input, uncommon_proj, if_uct);
        } else {
          phi_input = clone_nodes_with_same_ctrl(phi_input, uncommon_proj, if_uct);
        }
      }
      use->add_req(phi_input);
      has_phi = true;
    }
  }
  assert(!has_phi || uct_region->req() > 3, "no phis when region is created");

  if (new_entry == nullptr) {
    // Attach if_cont to iff
    _igvn.replace_input_of(parse_predicate, 0, if_cont);
    if (_idom != nullptr) {
      set_idom(parse_predicate, if_cont, dom_depth(parse_predicate));
    }
  }

  // When called from beautify_loops() idom is not constructed yet.
  if (_idom != nullptr) {
    Node* ridom = idom(uct_region);
    Node* nrdom = dom_lca_internal(ridom, new_iff);
    set_idom(uct_region, nrdom, dom_depth(uct_region));
  }

  return if_cont->as_IfProj();
}

// Update ctrl and control inputs of all data nodes starting from 'node' to 'new_ctrl' which have 'old_ctrl' as
// current ctrl.
void PhaseIdealLoop::set_ctrl_of_nodes_with_same_ctrl(Node* node, ProjNode* old_ctrl, Node* new_ctrl) {
  Unique_Node_List nodes_with_same_ctrl = find_nodes_with_same_ctrl(node, old_ctrl);
  for (uint j = 0; j < nodes_with_same_ctrl.size(); j++) {
    Node* next = nodes_with_same_ctrl[j];
    if (next->in(0) == old_ctrl) {
      _igvn.replace_input_of(next, 0, new_ctrl);
    }
    set_ctrl(next, new_ctrl);
  }
}

// Recursively find all input nodes with the same ctrl.
Unique_Node_List PhaseIdealLoop::find_nodes_with_same_ctrl(Node* node, const ProjNode* ctrl) {
  Unique_Node_List nodes_with_same_ctrl;
  nodes_with_same_ctrl.push(node);
  for (uint j = 0; j < nodes_with_same_ctrl.size(); j++) {
    Node* next = nodes_with_same_ctrl[j];
    for (uint k = 1; k < next->req(); k++) {
      Node* in = next->in(k);
      if (!in->is_Phi() && get_ctrl(in) == ctrl) {
        nodes_with_same_ctrl.push(in);
      }
    }
  }
  return nodes_with_same_ctrl;
}

// Clone all nodes with the same ctrl as 'old_ctrl' starting from 'node' by following its inputs. Rewire the cloned nodes
// to 'new_ctrl'. Returns the clone of 'node'.
Node* PhaseIdealLoop::clone_nodes_with_same_ctrl(Node* node, ProjNode* old_ctrl, Node* new_ctrl) {
  DEBUG_ONLY(uint last_idx = C->unique();)
  Unique_Node_List nodes_with_same_ctrl = find_nodes_with_same_ctrl(node, old_ctrl);
  Dict old_new_mapping = clone_nodes(nodes_with_same_ctrl); // Cloned but not rewired, yet
  rewire_cloned_nodes_to_ctrl(old_ctrl, new_ctrl, nodes_with_same_ctrl, old_new_mapping);
  Node* clone_phi_input = static_cast<Node*>(old_new_mapping[node]);
  assert(clone_phi_input != nullptr && clone_phi_input->_idx >= last_idx, "must exist and be a proper clone");
  return clone_phi_input;
}

// Clone all the nodes on 'list_to_clone' and return an old->new mapping.
Dict PhaseIdealLoop::clone_nodes(const Node_List& list_to_clone) {
  Dict old_new_mapping(cmpkey, hashkey);
  for (uint i = 0; i < list_to_clone.size(); i++) {
    Node* next = list_to_clone[i];
    Node* clone = next->clone();
    _igvn.register_new_node_with_optimizer(clone);
    old_new_mapping.Insert(next, clone);
  }
  return old_new_mapping;
}

// Rewire inputs of the unprocessed cloned nodes (inputs are not updated, yet, and still point to the old nodes) by
// using the old_new_mapping.
void PhaseIdealLoop::rewire_cloned_nodes_to_ctrl(const ProjNode* old_ctrl, Node* new_ctrl,
                                                 const Node_List& nodes_with_same_ctrl, const Dict& old_new_mapping) {
  for (uint i = 0; i < nodes_with_same_ctrl.size(); i++) {
    Node* next = nodes_with_same_ctrl[i];
    Node* clone = static_cast<Node*>(old_new_mapping[next]);
    if (next->in(0) == old_ctrl) {
      // All data nodes with a control input to the uncommon projection in the chain need to be rewired to the new uncommon
      // projection (could not only be the last data node in the chain but also, for example, a DivNode within the chain).
      _igvn.replace_input_of(clone, 0, new_ctrl);
      set_ctrl(clone, new_ctrl);
    }
    rewire_inputs_of_clones_to_clones(new_ctrl, clone, old_new_mapping, next);
  }
}

// Rewire the inputs of the cloned nodes to the old nodes to the new clones.
void PhaseIdealLoop::rewire_inputs_of_clones_to_clones(Node* new_ctrl, Node* clone, const Dict& old_new_mapping,
                                                       const Node* next) {
  for (uint i = 1; i < next->req(); i++) {
    Node* in = next->in(i);
    if (!in->is_Phi()) {
      assert(!in->is_CFG(), "must be data node");
      Node* in_clone = static_cast<Node*>(old_new_mapping[in]);
      if (in_clone != nullptr) {
        _igvn.replace_input_of(clone, i, in_clone);
        set_ctrl(clone, new_ctrl);
      }
    }
  }
}

IfProjNode* PhaseIdealLoop::clone_parse_predicate_to_unswitched_loop(ParsePredicateSuccessProj* parse_predicate_proj,
                                                                     Node* new_entry, Deoptimization::DeoptReason reason,
                                                                     const bool slow_loop) {

  IfProjNode* new_predicate_proj = create_new_if_for_predicate(parse_predicate_proj, new_entry, reason, Op_ParsePredicate,
                                                               slow_loop);
  assert(new_predicate_proj->is_IfTrue(), "the success projection of a Parse Predicate is a true projection");
  ParsePredicateNode* parse_predicate = new_predicate_proj->in(0)->as_ParsePredicate();
  return new_predicate_proj;
}

// Clones Assertion Predicates to both unswitched loops starting at 'old_predicate_proj' by following its control inputs.
// It also rewires the control edges of data nodes with dependencies in the loop from the old predicates to the new
// cloned predicates.
void PhaseIdealLoop::clone_assertion_predicates_to_unswitched_loop(IdealLoopTree* loop, const Node_List& old_new,
                                                                   Deoptimization::DeoptReason reason,
                                                                   IfProjNode* old_predicate_proj,
                                                                   ParsePredicateSuccessProj* fast_loop_parse_predicate_proj,
                                                                   ParsePredicateSuccessProj* slow_loop_parse_predicate_proj) {
  assert(fast_loop_parse_predicate_proj->in(0)->is_ParsePredicate() &&
         slow_loop_parse_predicate_proj->in(0)->is_ParsePredicate(), "sanity check");
  // Only need to clone range check predicates as those can be changed and duplicated by inserting pre/main/post loops
  // and doing loop unrolling. Push the original predicates on a list to later process them in reverse order to keep the
  // original predicate order.
  Unique_Node_List list;
  get_assertion_predicates(old_predicate_proj, list);

  Node_List to_process;
  IfNode* iff = old_predicate_proj->in(0)->as_If();
  IfProjNode* uncommon_proj = iff->proj_out(1 - old_predicate_proj->as_Proj()->_con)->as_IfProj();
  // Process in reverse order such that 'create_new_if_for_predicate' can be used in
  // 'clone_assertion_predicate_for_unswitched_loops' and the original order is maintained.
  for (int i = list.size() - 1; i >= 0; i--) {
    Node* predicate = list.at(i);
    assert(predicate->in(0)->is_If(), "must be If node");
    iff = predicate->in(0)->as_If();
    assert(predicate->is_Proj() && predicate->as_Proj()->is_IfProj(), "predicate must be a projection of an if node");
    IfProjNode* predicate_proj = predicate->as_IfProj();

    IfProjNode* fast_proj = clone_assertion_predicate_for_unswitched_loops(iff, predicate_proj, reason, fast_loop_parse_predicate_proj);
    assert(assertion_predicate_has_loop_opaque_node(fast_proj->in(0)->as_If()), "must find Assertion Predicate for fast loop");
    IfProjNode* slow_proj = clone_assertion_predicate_for_unswitched_loops(iff, predicate_proj, reason, slow_loop_parse_predicate_proj);
    assert(assertion_predicate_has_loop_opaque_node(slow_proj->in(0)->as_If()), "must find Assertion Predicate for slow loop");

    // Update control dependent data nodes.
    for (DUIterator j = predicate->outs(); predicate->has_out(j); j++) {
      Node* fast_node = predicate->out(j);
      if (loop->is_member(get_loop(ctrl_or_self(fast_node)))) {
        assert(fast_node->in(0) == predicate, "only control edge");
        Node* slow_node = old_new[fast_node->_idx];
        assert(slow_node->in(0) == predicate, "only control edge");
        _igvn.replace_input_of(fast_node, 0, fast_proj);
        to_process.push(slow_node);
        --j;
      }
    }
    // Have to delay updates to the slow loop so uses of predicate are not modified while we iterate on them.
    while (to_process.size() > 0) {
      Node* slow_node = to_process.pop();
      _igvn.replace_input_of(slow_node, 0, slow_proj);
    }
  }
}

// Put all Assertion Predicate projections on a list, starting at 'predicate' and going up in the tree. If 'get_opaque'
// is set, then the Opaque4 nodes of the Assertion Predicates are put on the list instead of the projections.
void PhaseIdealLoop::get_assertion_predicates(Node* predicate, Unique_Node_List& list, bool get_opaque) {
  ParsePredicateNode* parse_predicate = predicate->in(0)->as_ParsePredicate();
  ProjNode* uncommon_proj = parse_predicate->proj_out(1 - predicate->as_Proj()->_con);
  Node* rgn = uncommon_proj->unique_ctrl_out();
  assert(rgn->is_Region() || rgn->is_Call(), "must be a region or call uct");
  predicate = parse_predicate->in(0);
  while (predicate != nullptr && predicate->is_Proj() && predicate->in(0)->is_If()) {
    IfNode* iff = predicate->in(0)->as_If();
    uncommon_proj = iff->proj_out(1 - predicate->as_Proj()->_con);
    if (uncommon_proj->unique_ctrl_out() != rgn) {
      break;
    }
    if (iff->in(1)->Opcode() == Op_Opaque4 && assertion_predicate_has_loop_opaque_node(iff)) {
      if (get_opaque) {
        // Collect the predicate Opaque4 node.
        list.push(iff->in(1));
      } else {
        // Collect the predicate projection.
        list.push(predicate);
      }
    }
    predicate = predicate->in(0)->in(0);
  }
}

// Clone an Assertion Predicate for an unswitched loop. OpaqueLoopInit and OpaqueLoopStride nodes are cloned and uncommon
// traps are kept for the predicate (a Halt node is used later when creating pre/main/post loops and copying this cloned
// predicate again).
IfProjNode* PhaseIdealLoop::clone_assertion_predicate_for_unswitched_loops(Node* iff, IfProjNode* predicate,
                                                                           Deoptimization::DeoptReason reason,
                                                                           ParsePredicateSuccessProj* parse_predicate_proj) {
  Node* bol = create_bool_from_template_assertion_predicate(iff, nullptr, nullptr, parse_predicate_proj);
  IfProjNode* if_proj = create_new_if_for_predicate(parse_predicate_proj, nullptr, reason, iff->Opcode(), false);
  _igvn.replace_input_of(if_proj->in(0), 1, bol);
  _igvn.replace_input_of(parse_predicate_proj->in(0), 0, if_proj);
  set_idom(parse_predicate_proj->in(0), if_proj, dom_depth(if_proj));
  return if_proj;
}

// Clone the old Parse Predicates and Assertion Predicates before the unswitch If to the unswitched loops after the
// unswitch If.
void PhaseIdealLoop::clone_parse_and_assertion_predicates_to_unswitched_loop(IdealLoopTree* loop, Node_List& old_new,
                                                                             IfProjNode*& iffast_pred, IfProjNode*& ifslow_pred) {
  LoopNode* head = loop->_head->as_Loop();
  Node* entry = head->skip_strip_mined()->in(LoopNode::EntryControl);

  const Predicates predicates(entry);
  clone_loop_predication_predicates_to_unswitched_loop(loop, old_new, predicates.loop_predicate_block(),
                                                       Deoptimization::Reason_predicate, iffast_pred, ifslow_pred);
  clone_loop_predication_predicates_to_unswitched_loop(loop, old_new, predicates.profiled_loop_predicate_block(),
                                                       Deoptimization::Reason_profile_predicate, iffast_pred, ifslow_pred);

  const PredicateBlock* loop_limit_check_predicate_block = predicates.loop_limit_check_predicate_block();
  if (loop_limit_check_predicate_block->has_parse_predicate() && !head->is_CountedLoop()) {
    // Don't clone the Loop Limit Check Parse Predicate if we already have a counted loop (a Loop Limit Check Predicate
    // is only created when converting a LoopNode to a CountedLoopNode).
    clone_parse_predicate_to_unswitched_loops(loop_limit_check_predicate_block, Deoptimization::Reason_loop_limit_check,
                                              iffast_pred, ifslow_pred);
  }
}

// Clone the Parse Predicate and Template Assertion Predicates of a Loop Predication related Predicate Block.
void PhaseIdealLoop::clone_loop_predication_predicates_to_unswitched_loop(IdealLoopTree* loop, const Node_List& old_new,
                                                                          const PredicateBlock* predicate_block,
                                                                          Deoptimization::DeoptReason reason,
                                                                          IfProjNode*& iffast_pred,
                                                                          IfProjNode*& ifslow_pred) {
  if (predicate_block->has_parse_predicate()) {
    // We currently only clone Assertion Predicates if there are Parse Predicates. This is not entirely correct and will
    // be changed with the complete fix for Assertion Predicates.
    clone_parse_predicate_to_unswitched_loops(predicate_block, reason, iffast_pred, ifslow_pred);
    assert(iffast_pred->in(0)->is_ParsePredicate() && ifslow_pred->in(0)->is_ParsePredicate(),
           "must be success projections of the cloned Parse Predicates");
    clone_assertion_predicates_to_unswitched_loop(loop, old_new, reason, predicate_block->parse_predicate_success_proj(),
                                                  iffast_pred->as_IfTrue(), ifslow_pred->as_IfTrue());
  }
}

void PhaseIdealLoop::clone_parse_predicate_to_unswitched_loops(const PredicateBlock* predicate_block,
                                                               Deoptimization::DeoptReason reason,
                                                               IfProjNode*& iffast_pred, IfProjNode*& ifslow_pred) {
  assert(predicate_block->has_parse_predicate(), "must have parse predicate");
  ParsePredicateSuccessProj* parse_predicate_proj = predicate_block->parse_predicate_success_proj();
  iffast_pred = clone_parse_predicate_to_unswitched_loop(parse_predicate_proj, iffast_pred, reason, false);
  check_cloned_parse_predicate_for_unswitching(iffast_pred, true);

  ifslow_pred = clone_parse_predicate_to_unswitched_loop(parse_predicate_proj, ifslow_pred, reason, true);
  check_cloned_parse_predicate_for_unswitching(ifslow_pred, false);
}

#ifndef PRODUCT
void PhaseIdealLoop::check_cloned_parse_predicate_for_unswitching(const Node* new_entry, const bool is_fast_loop) {
  assert(new_entry != nullptr, "IfTrue or IfFalse after clone predicate");
  if (TraceLoopPredicate) {
    tty->print("Parse Predicate cloned to %s loop: ", is_fast_loop ? "fast" : "slow");
    new_entry->in(0)->dump();
  }
}
#endif

//------------------------------Invariance-----------------------------------
// Helper class for loop_predication_impl to compute invariance on the fly and
// clone invariants.
class Invariance : public StackObj {
  VectorSet _visited, _invariant;
  Node_Stack _stack;
  VectorSet _clone_visited;
  Node_List _old_new; // map of old to new (clone)
  IdealLoopTree* _lpt;
  PhaseIdealLoop* _phase;
  Node* _data_dependency_on; // The projection into the loop on which data nodes are dependent or null otherwise

  // Helper function to set up the invariance for invariance computation
  // If n is a known invariant, set up directly. Otherwise, look up the
  // the possibility to push n onto the stack for further processing.
  void visit(Node* use, Node* n) {
    if (_lpt->is_invariant(n)) { // known invariant
      _invariant.set(n->_idx);
    } else if (!n->is_CFG()) {
      Node *n_ctrl = _phase->ctrl_or_self(n);
      Node *u_ctrl = _phase->ctrl_or_self(use); // self if use is a CFG
      if (_phase->is_dominator(n_ctrl, u_ctrl)) {
        _stack.push(n, n->in(0) == nullptr ? 1 : 0);
      }
    }
  }

  // Compute invariance for "the_node" and (possibly) all its inputs recursively
  // on the fly
  void compute_invariance(Node* n) {
    assert(_visited.test(n->_idx), "must be");
    visit(n, n);
    while (_stack.is_nonempty()) {
      Node*  n = _stack.node();
      uint idx = _stack.index();
      if (idx == n->req()) { // all inputs are processed
        _stack.pop();
        // n is invariant if it's inputs are all invariant
        bool all_inputs_invariant = true;
        for (uint i = 0; i < n->req(); i++) {
          Node* in = n->in(i);
          if (in == nullptr) continue;
          assert(_visited.test(in->_idx), "must have visited input");
          if (!_invariant.test(in->_idx)) { // bad guy
            all_inputs_invariant = false;
            break;
          }
        }
        if (all_inputs_invariant) {
          // If n's control is a predicate that was moved out of the
          // loop, it was marked invariant but n is only invariant if
          // it depends only on that test. Otherwise, unless that test
          // is out of the loop, it's not invariant.
          if (n->is_CFG() || n->depends_only_on_test() || n->in(0) == nullptr || !_phase->is_member(_lpt, n->in(0))) {
            _invariant.set(n->_idx); // I am a invariant too
          }
        }
      } else { // process next input
        _stack.set_index(idx + 1);
        Node* m = n->in(idx);
        if (m != nullptr && !_visited.test_set(m->_idx)) {
          visit(n, m);
        }
      }
    }
  }

  // Helper function to set up _old_new map for clone_nodes.
  // If n is a known invariant, set up directly ("clone" of n == n).
  // Otherwise, push n onto the stack for real cloning.
  void clone_visit(Node* n) {
    assert(_invariant.test(n->_idx), "must be invariant");
    if (_lpt->is_invariant(n)) { // known invariant
      _old_new.map(n->_idx, n);
    } else { // to be cloned
      assert(!n->is_CFG(), "should not see CFG here");
      _stack.push(n, n->in(0) == nullptr ? 1 : 0);
    }
  }

  // Clone "n" and (possibly) all its inputs recursively
  void clone_nodes(Node* n, Node* ctrl) {
    clone_visit(n);
    while (_stack.is_nonempty()) {
      Node*  n = _stack.node();
      uint idx = _stack.index();
      if (idx == n->req()) { // all inputs processed, clone n!
        _stack.pop();
        // clone invariant node
        Node* n_cl = n->clone();
        _old_new.map(n->_idx, n_cl);
        _phase->register_new_node(n_cl, ctrl);
        for (uint i = 0; i < n->req(); i++) {
          Node* in = n_cl->in(i);
          if (in == nullptr) continue;
          n_cl->set_req(i, _old_new[in->_idx]);
        }
      } else { // process next input
        _stack.set_index(idx + 1);
        Node* m = n->in(idx);
        if (m != nullptr && !_clone_visited.test_set(m->_idx)) {
          clone_visit(m); // visit the input
        }
      }
    }
  }

 public:
  Invariance(Arena* area, IdealLoopTree* lpt) :
    _visited(area), _invariant(area),
    _stack(area, 10 /* guess */),
    _clone_visited(area), _old_new(area),
    _lpt(lpt), _phase(lpt->_phase),
    _data_dependency_on(nullptr)
  {
    LoopNode* head = _lpt->_head->as_Loop();
    Node* entry = head->skip_strip_mined()->in(LoopNode::EntryControl);
    if (entry->outcnt() != 1) {
      // If a node is pinned between the predicates and the loop
      // entry, we won't be able to move any node in the loop that
      // depends on it above it in a predicate. Mark all those nodes
      // as non-loop-invariant.
      // Loop predication could create new nodes for which the below
      // invariant information is missing. Mark the 'entry' node to
      // later check again if a node needs to be treated as non-loop-
      // invariant as well.
      _data_dependency_on = entry;
      Unique_Node_List wq;
      wq.push(entry);
      for (uint next = 0; next < wq.size(); ++next) {
        Node *n = wq.at(next);
        for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
          Node* u = n->fast_out(i);
          if (!u->is_CFG()) {
            Node* c = _phase->get_ctrl(u);
            if (_lpt->is_member(_phase->get_loop(c)) || _phase->is_dominator(c, head)) {
              _visited.set(u->_idx);
              wq.push(u);
            }
          }
        }
      }
    }
  }

  // Did we explicitly mark some nodes non-loop-invariant? If so, return the entry node on which some data nodes
  // are dependent that prevent loop predication. Otherwise, return null.
  Node* data_dependency_on() {
    return _data_dependency_on;
  }

  // Map old to n for invariance computation and clone
  void map_ctrl(Node* old, Node* n) {
    assert(old->is_CFG() && n->is_CFG(), "must be");
    _old_new.map(old->_idx, n); // "clone" of old is n
    _invariant.set(old->_idx);  // old is invariant
    _clone_visited.set(old->_idx);
  }

  // Driver function to compute invariance
  bool is_invariant(Node* n) {
    if (!_visited.test_set(n->_idx))
      compute_invariance(n);
    return (_invariant.test(n->_idx) != 0);
  }

  // Driver function to clone invariant
  Node* clone(Node* n, Node* ctrl) {
    assert(ctrl->is_CFG(), "must be");
    assert(_invariant.test(n->_idx), "must be an invariant");
    if (!_clone_visited.test(n->_idx))
      clone_nodes(n, ctrl);
    return _old_new[n->_idx];
  }
};

//------------------------------is_range_check_if -----------------------------------
// Returns true if the predicate of iff is in "scale*iv + offset u< load_range(ptr)" format
// Note: this function is particularly designed for loop predication. We require load_range
//       and offset to be loop invariant computed on the fly by "invar"
bool IdealLoopTree::is_range_check_if(IfProjNode* if_success_proj, PhaseIdealLoop *phase, BasicType bt, Node *iv, Node *&range,
                                      Node *&offset, jlong &scale) const {
  IfNode* iff = if_success_proj->in(0)->as_If();
  if (!is_loop_exit(iff)) {
    return false;
  }
  if (!iff->in(1)->is_Bool()) {
    return false;
  }
  const BoolNode *bol = iff->in(1)->as_Bool();
  if (bol->_test._test != BoolTest::lt || if_success_proj->is_IfFalse()) {
    // We don't have the required range check pattern:
    // if (scale*iv + offset <u limit) {
    //
    // } else {
    //   trap();
    // }
    //
    // Having the trap on the true projection:
    // if (scale*iv + offset <u limit) {
    //   trap();
    // }
    //
    // is not correct. We would need to flip the test to get the expected "trap on false path" pattern:
    // if (scale*iv + offset >=u limit) {
    //
    // } else {
    //   trap();
    // }
    //
    // If we create a Range Check Predicate for this wrong pattern, it could succeed at runtime (i.e. true for the
    // value of "scale*iv + offset" in the first loop iteration and true for the value of "scale*iv + offset" in the
    // last loop iteration) while the check to be hoisted could fail in other loop iterations.
    //
    // Example:
    // Loop: "for (int i = -1; i < 1000; i++)"
    // init = "scale*iv + offset" in the first loop iteration = 1*-1 + 0 = -1
    // last = "scale*iv + offset" in the last loop iteration = 1*999 + 0 = 999
    // limit = 100
    //
    // Range Check Predicate is always true:
    // init >=u limit && last >=u limit  <=>
    // -1 >=u 100 && 999 >= u 100
    //
    // But for 0 <= x < 100: x >=u 100 is false.
    // We would wrongly skip the branch with the trap() and possibly miss to execute some other statements inside that
    // trap() branch.
    return false;
  }
  if (!bol->in(1)->is_Cmp()) {
    return false;
  }
  const CmpNode *cmp = bol->in(1)->as_Cmp();
  if (cmp->Opcode() != Op_Cmp_unsigned(bt)) {
    return false;
  }
  range = cmp->in(2);
  if (range->Opcode() != Op_LoadRange) {
    const TypeInteger* tinteger = phase->_igvn.type(range)->isa_integer(bt);
    if (tinteger == nullptr || tinteger->empty() || tinteger->lo_as_long() < 0) {
      // Allow predication on positive values that aren't LoadRanges.
      // This allows optimization of loops where the length of the
      // array is a known value and doesn't need to be loaded back
      // from the array.
      return false;
    }
  } else {
    assert(bt == T_INT, "no LoadRange for longs");
  }
  scale  = 0;
  offset = nullptr;
  if (!phase->is_scaled_iv_plus_offset(cmp->in(1), iv, bt, &scale, &offset)) {
    return false;
  }
  return true;
}

bool IdealLoopTree::is_range_check_if(IfProjNode* if_success_proj, PhaseIdealLoop *phase, Invariance& invar DEBUG_ONLY(COMMA ProjNode *predicate_proj)) const {
  Node* range = nullptr;
  Node* offset = nullptr;
  jlong scale = 0;
  Node* iv = _head->as_BaseCountedLoop()->phi();
  Compile* C = Compile::current();
  const uint old_unique_idx = C->unique();
  if (!is_range_check_if(if_success_proj, phase, T_INT, iv, range, offset, scale)) {
    return false;
  }
  if (!invar.is_invariant(range)) {
    return false;
  }
  if (offset != nullptr) {
    if (!invar.is_invariant(offset)) { // offset must be invariant
      return false;
    }
    Node* data_dependency_on = invar.data_dependency_on();
    if (data_dependency_on != nullptr && old_unique_idx < C->unique()) {
      // 'offset' node was newly created in is_range_check_if(). Check that it does not depend on the entry projection
      // into the loop. If it does, we cannot perform loop predication (see Invariant::Invariant()).
      assert(!offset->is_CFG(), "offset must be a data node");
      if (_phase->get_ctrl(offset) == data_dependency_on) {
        return false;
      }
    }
  }
#ifdef ASSERT
  if (offset && phase->has_ctrl(offset)) {
    Node* offset_ctrl = phase->get_ctrl(offset);
    if (phase->get_loop(predicate_proj) == phase->get_loop(offset_ctrl) &&
        phase->is_dominator(predicate_proj, offset_ctrl)) {
      // If the control of offset is loop predication promoted by previous pass,
      // then it will lead to cyclic dependency.
      // Previously promoted loop predication is in the same loop of predication
      // point.
      // This situation can occur when pinning nodes too conservatively - can we do better?
      assert(false, "cyclic dependency prevents range check elimination, idx: offset %d, offset_ctrl %d, predicate_proj %d",
             offset->_idx, offset_ctrl->_idx, predicate_proj->_idx);
    }
  }
#endif
  return true;
}

//------------------------------rc_predicate-----------------------------------
// Create a range check predicate
//
// for (i = init; i < limit; i += stride) {
//    a[scale*i+offset]
// }
//
// Compute max(scale*i + offset) for init <= i < limit and build the predicate
// as "max(scale*i + offset) u< a.length".
//
// There are two cases for max(scale*i + offset):
// (1) stride*scale > 0
//   max(scale*i + offset) = scale*(limit-stride) + offset
// (2) stride*scale < 0
//   max(scale*i + offset) = scale*init + offset
BoolNode* PhaseIdealLoop::rc_predicate(IdealLoopTree* loop, Node* ctrl, int scale, Node* offset, Node* init,
                                       Node* limit, jint stride, Node* range, bool upper, bool& overflow) {
  jint con_limit  = (limit != nullptr && limit->is_Con())  ? limit->get_int()  : 0;
  jint con_init   = init->is_Con()   ? init->get_int()   : 0;
  jint con_offset = offset->is_Con() ? offset->get_int() : 0;

  stringStream* predString = nullptr;
  if (TraceLoopPredicate) {
    predString = new (mtCompiler) stringStream();
    predString->print("rc_predicate ");
  }

  overflow = false;
  Node* max_idx_expr = nullptr;
  const TypeInt* idx_type = TypeInt::INT;
  if ((stride > 0) == (scale > 0) == upper) {
    guarantee(limit != nullptr, "sanity");
    if (TraceLoopPredicate) {
      if (limit->is_Con()) {
        predString->print("(%d ", con_limit);
      } else {
        predString->print("(limit ");
      }
      predString->print("- %d) ", stride);
    }
    // Check if (limit - stride) may overflow
    const TypeInt* limit_type = _igvn.type(limit)->isa_int();
    jint limit_lo = limit_type->_lo;
    jint limit_hi = limit_type->_hi;
    if ((stride > 0 && (java_subtract(limit_lo, stride) < limit_lo)) ||
        (stride < 0 && (java_subtract(limit_hi, stride) > limit_hi))) {
      // No overflow possible
      ConINode* con_stride = _igvn.intcon(stride);
      set_ctrl(con_stride, C->root());
      max_idx_expr = new SubINode(limit, con_stride);
      idx_type = TypeInt::make(limit_lo - stride, limit_hi - stride, limit_type->_widen);
    } else {
      // May overflow
      overflow = true;
      limit = new ConvI2LNode(limit);
      register_new_node(limit, ctrl);
      ConLNode* con_stride = _igvn.longcon(stride);
      set_ctrl(con_stride, C->root());
      max_idx_expr = new SubLNode(limit, con_stride);
    }
    register_new_node(max_idx_expr, ctrl);
  } else {
    if (TraceLoopPredicate) {
      if (init->is_Con()) {
        predString->print("%d ", con_init);
      } else {
        predString->print("init ");
      }
    }
    idx_type = _igvn.type(init)->isa_int();
    max_idx_expr = init;
  }

  if (scale != 1) {
    ConNode* con_scale = _igvn.intcon(scale);
    set_ctrl(con_scale, C->root());
    if (TraceLoopPredicate) {
      predString->print("* %d ", scale);
    }
    // Check if (scale * max_idx_expr) may overflow
    const TypeInt* scale_type = TypeInt::make(scale);
    MulINode* mul = new MulINode(max_idx_expr, con_scale);
    idx_type = (TypeInt*)mul->mul_ring(idx_type, scale_type);
    if (overflow || TypeInt::INT->higher_equal(idx_type)) {
      // May overflow
      mul->destruct(&_igvn);
      if (!overflow) {
        max_idx_expr = new ConvI2LNode(max_idx_expr);
        register_new_node(max_idx_expr, ctrl);
      }
      overflow = true;
      con_scale = _igvn.longcon(scale);
      set_ctrl(con_scale, C->root());
      max_idx_expr = new MulLNode(max_idx_expr, con_scale);
    } else {
      // No overflow possible
      max_idx_expr = mul;
    }
    register_new_node(max_idx_expr, ctrl);
  }

  if (offset && (!offset->is_Con() || con_offset != 0)){
    if (TraceLoopPredicate) {
      if (offset->is_Con()) {
        predString->print("+ %d ", con_offset);
      } else {
        predString->print("+ offset");
      }
    }
    // Check if (max_idx_expr + offset) may overflow
    const TypeInt* offset_type = _igvn.type(offset)->isa_int();
    jint lo = java_add(idx_type->_lo, offset_type->_lo);
    jint hi = java_add(idx_type->_hi, offset_type->_hi);
    if (overflow || (lo > hi) ||
        ((idx_type->_lo & offset_type->_lo) < 0 && lo >= 0) ||
        ((~(idx_type->_hi | offset_type->_hi)) < 0 && hi < 0)) {
      // May overflow
      if (!overflow) {
        max_idx_expr = new ConvI2LNode(max_idx_expr);
        register_new_node(max_idx_expr, ctrl);
      }
      overflow = true;
      offset = new ConvI2LNode(offset);
      register_new_node(offset, ctrl);
      max_idx_expr = new AddLNode(max_idx_expr, offset);
    } else {
      // No overflow possible
      max_idx_expr = new AddINode(max_idx_expr, offset);
    }
    register_new_node(max_idx_expr, ctrl);
  }

  CmpNode* cmp = nullptr;
  if (overflow) {
    // Integer expressions may overflow, do long comparison
    range = new ConvI2LNode(range);
    register_new_node(range, ctrl);
    cmp = new CmpULNode(max_idx_expr, range);
  } else {
    cmp = new CmpUNode(max_idx_expr, range);
  }
  register_new_node(cmp, ctrl);
  BoolNode* bol = new BoolNode(cmp, BoolTest::lt);
  register_new_node(bol, ctrl);

  if (TraceLoopPredicate) {
    predString->print_cr("<u range");
    tty->print("%s", predString->base());
    delete predString;
  }
  return bol;
}

// Should loop predication look not only in the path from tail to head
// but also in branches of the loop body?
bool PhaseIdealLoop::loop_predication_should_follow_branches(IdealLoopTree* loop, float& loop_trip_cnt) {
  if (!UseProfiledLoopPredicate) {
    return false;
  }

  LoopNode* head = loop->_head->as_Loop();
  bool follow_branches = true;
  IdealLoopTree* l = loop->_child;
  // For leaf loops and loops with a single inner loop
  while (l != nullptr && follow_branches) {
    IdealLoopTree* child = l;
    if (child->_child != nullptr &&
        child->_head->is_OuterStripMinedLoop()) {
      assert(child->_child->_next == nullptr, "only one inner loop for strip mined loop");
      assert(child->_child->_head->is_CountedLoop() && child->_child->_head->as_CountedLoop()->is_strip_mined(), "inner loop should be strip mined");
      child = child->_child;
    }
    if (child->_child != nullptr || child->_irreducible) {
      follow_branches = false;
    }
    l = l->_next;
  }
  if (follow_branches) {
    loop->compute_profile_trip_cnt(this);
    if (head->is_profile_trip_failed()) {
      follow_branches = false;
    } else {
      loop_trip_cnt = head->profile_trip_cnt();
      if (head->is_CountedLoop()) {
        CountedLoopNode* cl = head->as_CountedLoop();
        if (cl->phi() != nullptr) {
          const TypeInt* t = _igvn.type(cl->phi())->is_int();
          float worst_case_trip_cnt = ((float)t->_hi - t->_lo) / ABS(cl->stride_con());
          if (worst_case_trip_cnt < loop_trip_cnt) {
            loop_trip_cnt = worst_case_trip_cnt;
          }
        }
      }
    }
  }
  return follow_branches;
}

float PathFrequency::to(Node* n) {
  // post order walk on the CFG graph from n to _dom
  IdealLoopTree* loop = _phase->get_loop(_dom);
  Node* c = n;
  for (;;) {
    assert(_phase->get_loop(c) == loop, "have to be in the same loop");
    if (c == _dom || _freqs.at_grow(c->_idx, -1) >= 0) {
      float f = c == _dom ? 1 : _freqs.at(c->_idx);
      Node* prev = c;
      while (_stack.size() > 0 && prev == c) {
        Node* n = _stack.node();
        if (!n->is_Region()) {
          if (_phase->get_loop(n) != _phase->get_loop(n->in(0))) {
            // Found an inner loop: compute frequency of reaching this
            // exit from the loop head by looking at the number of
            // times each loop exit was taken
            IdealLoopTree* inner_loop = _phase->get_loop(n->in(0));
            LoopNode* inner_head = inner_loop->_head->as_Loop();
            assert(_phase->get_loop(n) == loop, "only 1 inner loop");
            if (inner_head->is_OuterStripMinedLoop()) {
              inner_head->verify_strip_mined(1);
              if (n->in(0) == inner_head->in(LoopNode::LoopBackControl)->in(0)) {
                n = n->in(0)->in(0)->in(0);
              }
              inner_loop = inner_loop->_child;
              inner_head = inner_loop->_head->as_Loop();
              inner_head->verify_strip_mined(1);
            }
            float loop_exit_cnt = 0.0f;
            for (uint i = 0; i < inner_loop->_body.size(); i++) {
              Node *n = inner_loop->_body[i];
              float c = inner_loop->compute_profile_trip_cnt_helper(n);
              loop_exit_cnt += c;
            }
            float cnt = -1;
            if (n->in(0)->is_If()) {
              IfNode* iff = n->in(0)->as_If();
              float p = n->in(0)->as_If()->_prob;
              if (n->Opcode() == Op_IfFalse) {
                p = 1 - p;
              }
              if (p > PROB_MIN) {
                cnt = p * iff->_fcnt;
              } else {
                cnt = 0;
              }
            } else {
              assert(n->in(0)->is_Jump(), "unsupported node kind");
              JumpNode* jmp = n->in(0)->as_Jump();
              float p = n->in(0)->as_Jump()->_probs[n->as_JumpProj()->_con];
              cnt = p * jmp->_fcnt;
            }
            float this_exit_f = cnt > 0 ? cnt / loop_exit_cnt : 0;
            this_exit_f = check_and_truncate_frequency(this_exit_f);
            f = f * this_exit_f;
            f = check_and_truncate_frequency(f);
          } else {
            float p = -1;
            if (n->in(0)->is_If()) {
              p = n->in(0)->as_If()->_prob;
              if (n->Opcode() == Op_IfFalse) {
                p = 1 - p;
              }
            } else {
              assert(n->in(0)->is_Jump(), "unsupported node kind");
              p = n->in(0)->as_Jump()->_probs[n->as_JumpProj()->_con];
            }
            f = f * p;
            f = check_and_truncate_frequency(f);
          }
          _freqs.at_put_grow(n->_idx, (float)f, -1);
          _stack.pop();
        } else {
          float prev_f = _freqs_stack.pop();
          float new_f = f;
          f = new_f + prev_f;
          f = check_and_truncate_frequency(f);
          uint i = _stack.index();
          if (i < n->req()) {
            c = n->in(i);
            _stack.set_index(i+1);
            _freqs_stack.push(f);
          } else {
            _freqs.at_put_grow(n->_idx, f, -1);
            _stack.pop();
          }
        }
      }
      if (_stack.size() == 0) {
        return check_and_truncate_frequency(f);
      }
    } else if (c->is_Loop()) {
      ShouldNotReachHere();
      c = c->in(LoopNode::EntryControl);
    } else if (c->is_Region()) {
      _freqs_stack.push(0);
      _stack.push(c, 2);
      c = c->in(1);
    } else {
      if (c->is_IfProj()) {
        IfNode* iff = c->in(0)->as_If();
        if (iff->_prob == PROB_UNKNOWN) {
          // assume never taken
          _freqs.at_put_grow(c->_idx, 0, -1);
        } else if (_phase->get_loop(c) != _phase->get_loop(iff)) {
          if (iff->_fcnt == COUNT_UNKNOWN) {
            // assume never taken
            _freqs.at_put_grow(c->_idx, 0, -1);
          } else {
            // skip over loop
            _stack.push(c, 1);
            c = _phase->get_loop(c->in(0))->_head->as_Loop()->skip_strip_mined()->in(LoopNode::EntryControl);
          }
        } else {
          _stack.push(c, 1);
          c = iff;
        }
      } else if (c->is_JumpProj()) {
        JumpNode* jmp = c->in(0)->as_Jump();
        if (_phase->get_loop(c) != _phase->get_loop(jmp)) {
          if (jmp->_fcnt == COUNT_UNKNOWN) {
            // assume never taken
            _freqs.at_put_grow(c->_idx, 0, -1);
          } else {
            // skip over loop
            _stack.push(c, 1);
            c = _phase->get_loop(c->in(0))->_head->as_Loop()->skip_strip_mined()->in(LoopNode::EntryControl);
          }
        } else {
          _stack.push(c, 1);
          c = jmp;
        }
      } else if (c->Opcode() == Op_CatchProj &&
                 c->in(0)->Opcode() == Op_Catch &&
                 c->in(0)->in(0)->is_Proj() &&
                 c->in(0)->in(0)->in(0)->is_Call()) {
        // assume exceptions are never thrown
        uint con = c->as_Proj()->_con;
        if (con == CatchProjNode::fall_through_index) {
          Node* call = c->in(0)->in(0)->in(0)->in(0);
          if (_phase->get_loop(call) != _phase->get_loop(c)) {
            _freqs.at_put_grow(c->_idx, 0, -1);
          } else {
            c = call;
          }
        } else {
          assert(con >= CatchProjNode::catch_all_index, "what else?");
          _freqs.at_put_grow(c->_idx, 0, -1);
        }
      } else if (c->unique_ctrl_out_or_null() == nullptr && !c->is_If() && !c->is_Jump()) {
        ShouldNotReachHere();
      } else {
        c = c->in(0);
      }
    }
  }
  ShouldNotReachHere();
  return -1;
}

void PhaseIdealLoop::loop_predication_follow_branches(Node *n, IdealLoopTree *loop, float loop_trip_cnt,
                                                      PathFrequency& pf, Node_Stack& stack, VectorSet& seen,
                                                      Node_List& if_proj_list) {
  assert(n->is_Region(), "start from a region");
  Node* tail = loop->tail();
  stack.push(n, 1);
  do {
    Node* c = stack.node();
    assert(c->is_Region() || c->is_IfProj(), "only region here");
    uint i = stack.index();

    if (i < c->req()) {
      stack.set_index(i+1);
      Node* in = c->in(i);
      while (!is_dominator(in, tail) && !seen.test_set(in->_idx)) {
        IdealLoopTree* in_loop = get_loop(in);
        if (in_loop != loop) {
          in = in_loop->_head->in(LoopNode::EntryControl);
        } else if (in->is_Region()) {
          stack.push(in, 1);
          break;
        } else if (in->is_IfProj() &&
                   in->as_Proj()->is_uncommon_trap_if_pattern() &&
                   (in->in(0)->Opcode() == Op_If ||
                    in->in(0)->Opcode() == Op_RangeCheck)) {
          if (pf.to(in) * loop_trip_cnt >= 1) {
            stack.push(in, 1);
          }
          in = in->in(0);
        } else {
          in = in->in(0);
        }
      }
    } else {
      if (c->is_IfProj()) {
        if_proj_list.push(c);
      }
      stack.pop();
    }

  } while (stack.size() > 0);
}

bool PhaseIdealLoop::loop_predication_impl_helper(IdealLoopTree* loop, IfProjNode* if_success_proj,
                                                  ParsePredicateSuccessProj* parse_predicate_proj, CountedLoopNode* cl,
                                                  ConNode* zero, Invariance& invar, Deoptimization::DeoptReason reason) {
  // Following are changed to nonnull when a predicate can be hoisted
  IfProjNode* new_predicate_proj = nullptr;
  IfNode*   iff  = if_success_proj->in(0)->as_If();
  Node*     test = iff->in(1);
  if (!test->is_Bool()) { //Conv2B, ...
    return false;
  }
  BoolNode* bol = test->as_Bool();
  if (invar.is_invariant(bol)) {
    // Invariant test
    new_predicate_proj = create_new_if_for_predicate(parse_predicate_proj, nullptr,
                                                     reason,
                                                     iff->Opcode());
    Node* ctrl = new_predicate_proj->in(0)->as_If()->in(0);
    BoolNode* new_predicate_bol = invar.clone(bol, ctrl)->as_Bool();

    // Negate test if necessary (Parse Predicates always have IfTrue as success projection and IfFalse as uncommon trap)
    bool negated = false;
    if (if_success_proj->is_IfFalse()) {
      new_predicate_bol = new BoolNode(new_predicate_bol->in(1), new_predicate_bol->_test.negate());
      register_new_node(new_predicate_bol, ctrl);
      negated = true;
    }
    IfNode* new_predicate_iff = new_predicate_proj->in(0)->as_If();
    _igvn.hash_delete(new_predicate_iff);
    new_predicate_iff->set_req(1, new_predicate_bol);
#ifndef PRODUCT
    if (TraceLoopPredicate) {
      tty->print("Predicate invariant if%s: %d ", negated ? " negated" : "", new_predicate_iff->_idx);
      loop->dump_head();
    } else if (TraceLoopOpts) {
      tty->print("Predicate IC ");
      loop->dump_head();
    }
#endif
  } else if (cl != nullptr && loop->is_range_check_if(if_success_proj, this, invar DEBUG_ONLY(COMMA parse_predicate_proj))) {
    // Range check for counted loops
    assert(if_success_proj->is_IfTrue(), "trap must be on false projection for a range check");
    const Node*    cmp    = bol->in(1)->as_Cmp();
    Node*          idx    = cmp->in(1);
    assert(!invar.is_invariant(idx), "index is variant");
    Node* rng = cmp->in(2);
    assert(rng->Opcode() == Op_LoadRange || iff->is_RangeCheck() || _igvn.type(rng)->is_int()->_lo >= 0, "must be");
    assert(invar.is_invariant(rng), "range must be invariant");
    int scale    = 1;
    Node* offset = zero;
    bool ok = is_scaled_iv_plus_offset(idx, cl->phi(), &scale, &offset);
    assert(ok, "must be index expression");

    Node* init    = cl->init_trip();
    // Limit is not exact.
    // Calculate exact limit here.
    // Note, counted loop's test is '<' or '>'.
    loop->compute_trip_count(this);
    Node* limit   = exact_limit(loop);
    int  stride   = cl->stride()->get_int();

    // Build if's for the upper and lower bound tests.  The
    // lower_bound test will dominate the upper bound test and all
    // cloned or created nodes will use the lower bound test as
    // their declared control.

    // Perform cloning to keep Invariance state correct since the
    // late schedule will place invariant things in the loop.
    Node* ctrl = parse_predicate_proj->in(0)->as_If()->in(0);
    rng = invar.clone(rng, ctrl);
    if (offset && offset != zero) {
      assert(invar.is_invariant(offset), "offset must be loop invariant");
      offset = invar.clone(offset, ctrl);
    }
    // If predicate expressions may overflow in the integer range, longs are used.
    bool overflow = false;
    // Test the lower bound
    BoolNode* lower_bound_bol = rc_predicate(loop, ctrl, scale, offset, init, limit, stride, rng, false, overflow);

    const int if_opcode = iff->Opcode();
    IfProjNode* lower_bound_proj = create_new_if_for_predicate(parse_predicate_proj, nullptr, reason, overflow ? Op_If : if_opcode);
    IfNode* lower_bound_iff = lower_bound_proj->in(0)->as_If();
    _igvn.hash_delete(lower_bound_iff);
    lower_bound_iff->set_req(1, lower_bound_bol);
    if (TraceLoopPredicate) tty->print_cr("lower bound check if: %d", lower_bound_iff->_idx);

    // Test the upper bound
    BoolNode* upper_bound_bol = rc_predicate(loop, lower_bound_proj, scale, offset, init, limit, stride, rng, true,
                                             overflow);

    IfProjNode* upper_bound_proj = create_new_if_for_predicate(parse_predicate_proj, nullptr, reason, overflow ? Op_If : if_opcode);
    assert(upper_bound_proj->in(0)->as_If()->in(0) == lower_bound_proj, "should dominate");
    IfNode* upper_bound_iff = upper_bound_proj->in(0)->as_If();
    _igvn.hash_delete(upper_bound_iff);
    upper_bound_iff->set_req(1, upper_bound_bol);
    if (TraceLoopPredicate) tty->print_cr("upper bound check if: %d", lower_bound_iff->_idx);

    // Fall through into rest of the cleanup code which will move any dependent nodes to the skeleton predicates of the
    // upper bound test. We always need to create skeleton predicates in order to properly remove dead loops when later
    // splitting the predicated loop into (unreachable) sub-loops (i.e. done by unrolling, peeling, pre/main/post etc.).
    new_predicate_proj = add_template_assertion_predicate(iff, loop, if_success_proj, parse_predicate_proj, upper_bound_proj, scale,
                                                          offset, init, limit, stride, rng, overflow, reason);

#ifndef PRODUCT
    if (TraceLoopOpts && !TraceLoopPredicate) {
      tty->print("Predicate RC ");
      loop->dump_head();
    }
#endif
  } else {
    // Loop variant check (for example, range check in non-counted loop)
    // with uncommon trap.
    return false;
  }
  assert(new_predicate_proj != nullptr, "sanity");
  // Success - attach condition (new_predicate_bol) to predicate if
  invar.map_ctrl(if_success_proj, new_predicate_proj); // so that invariance test can be appropriate

  // Eliminate the old If in the loop body
  dominated_by(new_predicate_proj, iff, if_success_proj->_con != new_predicate_proj->_con);

  C->set_major_progress();
  return true;
}

// Each newly created Hoisted Check Predicate is accompanied by two Template Assertion Predicates. Later, we initialize
// them by making a copy of them when splitting a loop into sub loops. The Assertion Predicates ensure that dead sub
// loops are removed properly.
IfProjNode* PhaseIdealLoop::add_template_assertion_predicate(IfNode* iff, IdealLoopTree* loop, IfProjNode* if_proj,
                                                             ParsePredicateSuccessProj* parse_predicate_proj,
                                                             IfProjNode* upper_bound_proj, const int scale, Node* offset,
                                                             Node* init, Node* limit, const jint stride,
                                                             Node* rng, bool& overflow, Deoptimization::DeoptReason reason) {
  // First predicate for the initial value on first loop iteration
  Node* opaque_init = new OpaqueLoopInitNode(C, init);
  register_new_node(opaque_init, upper_bound_proj);
  bool negate = (if_proj->_con != parse_predicate_proj->_con);
  BoolNode* bol = rc_predicate(loop, upper_bound_proj, scale, offset, opaque_init, limit, stride, rng,
                               (stride > 0) != (scale > 0), overflow);
  Node* opaque_bol = new Opaque4Node(C, bol, _igvn.intcon(1)); // This will go away once loop opts are over
  C->add_template_assertion_predicate_opaq(opaque_bol);
  register_new_node(opaque_bol, upper_bound_proj);
  IfProjNode* new_proj = create_new_if_for_predicate(parse_predicate_proj, nullptr, reason, overflow ? Op_If : iff->Opcode());
  _igvn.replace_input_of(new_proj->in(0), 1, opaque_bol);
  assert(opaque_init->outcnt() > 0, "should be used");

  // Second predicate for init + (current stride - initial stride)
  // This is identical to the previous predicate initially but as
  // unrolling proceeds current stride is updated.
  Node* init_stride = loop->_head->as_CountedLoop()->stride();
  Node* opaque_stride = new OpaqueLoopStrideNode(C, init_stride);
  register_new_node(opaque_stride, new_proj);
  Node* max_value = new SubINode(opaque_stride, init_stride);
  register_new_node(max_value, new_proj);
  max_value = new AddINode(opaque_init, max_value);
  register_new_node(max_value, new_proj);
  // init + (current stride - initial stride) is within the loop so narrow its type by leveraging the type of the iv Phi
  max_value = new CastIINode(max_value, loop->_head->as_CountedLoop()->phi()->bottom_type());
  register_new_node(max_value, parse_predicate_proj);

  bol = rc_predicate(loop, new_proj, scale, offset, max_value, limit, stride, rng, (stride > 0) != (scale > 0),
                     overflow);
  opaque_bol = new Opaque4Node(C, bol, _igvn.intcon(1));
  C->add_template_assertion_predicate_opaq(opaque_bol);
  register_new_node(opaque_bol, new_proj);
  new_proj = create_new_if_for_predicate(parse_predicate_proj, nullptr, reason, overflow ? Op_If : iff->Opcode());
  _igvn.replace_input_of(new_proj->in(0), 1, opaque_bol);
  assert(max_value->outcnt() > 0, "should be used");
  assert(assertion_predicate_has_loop_opaque_node(new_proj->in(0)->as_If()), "unexpected");

  return new_proj;
}

// Insert Hoisted Check Predicates for null checks and range checks and additional Template Assertion Predicates for
// range checks.
bool PhaseIdealLoop::loop_predication_impl(IdealLoopTree* loop) {
  LoopNode* head = loop->_head->as_Loop();

  if (head->unique_ctrl_out()->is_NeverBranch()) {
    // do nothing for infinite loops
    return false;
  }

  if (head->is_OuterStripMinedLoop()) {
    return false;
  }

  CountedLoopNode *cl = nullptr;
  if (head->is_valid_counted_loop(T_INT)) {
    cl = head->as_CountedLoop();
    // do nothing for iteration-splitted loops
    if (!cl->is_normal_loop()) return false;
    // Avoid RCE if Counted loop's test is '!='.
    BoolTest::mask bt = cl->loopexit()->test_trip();
    if (bt != BoolTest::lt && bt != BoolTest::gt)
      cl = nullptr;
  }

  Node* entry = head->skip_strip_mined()->in(LoopNode::EntryControl);
  const Predicates predicates(entry);
  const PredicateBlock* loop_predicate_block = predicates.loop_predicate_block();
  const PredicateBlock* profiled_loop_predicate_block = predicates.profiled_loop_predicate_block();
  float loop_trip_cnt = -1;
  bool follow_branches = profiled_loop_predicate_block->has_parse_predicate() &&
                         loop_predication_should_follow_branches(loop, loop_trip_cnt);
  assert(!follow_branches || loop_trip_cnt >= 0, "negative trip count?");

  if (!loop_predicate_block->has_parse_predicate() && !follow_branches) {
#ifndef PRODUCT
    if (TraceLoopPredicate) {
      tty->print("Missing Parse Predicates:");
      loop->dump_head();
      head->dump(1);
    }
#endif
    return false;
  }
  ConNode* zero = _igvn.intcon(0);
  set_ctrl(zero, C->root());

  ResourceArea* area = Thread::current()->resource_area();
  Invariance invar(area, loop);

  // Create list of if-projs such that a newer proj dominates all older
  // projs in the list, and they all dominate loop->tail()
  Node_List if_proj_list;
  Node_List regions;
  Node* current_proj = loop->tail(); // start from tail


  Node_List controls;
  while (current_proj != head) {
    if (loop == get_loop(current_proj) && // still in the loop ?
        current_proj->is_Proj()        && // is a projection  ?
        (current_proj->in(0)->Opcode() == Op_If ||
         current_proj->in(0)->Opcode() == Op_RangeCheck)) { // is a if projection ?
      if_proj_list.push(current_proj);
    }
    if (follow_branches &&
        current_proj->Opcode() == Op_Region &&
        loop == get_loop(current_proj)) {
      regions.push(current_proj);
    }
    current_proj = idom(current_proj);
  }

  bool hoisted = false; // true if at least one proj is promoted

  if (can_create_loop_predicates(profiled_loop_predicate_block)) {
    while (if_proj_list.size() > 0) {
      Node* n = if_proj_list.pop();

      IfProjNode* if_proj = n->as_IfProj();
      IfNode* iff = if_proj->in(0)->as_If();

      CallStaticJavaNode* call = if_proj->is_uncommon_trap_if_pattern();
      if (call == nullptr) {
        if (loop->is_loop_exit(iff)) {
          // stop processing the remaining projs in the list because the execution of them
          // depends on the condition of "iff" (iff->in(1)).
          break;
        } else {
          // Both arms are inside the loop. There are two cases:
          // (1) there is one backward branch. In this case, any remaining proj
          //     in the if_proj list post-dominates "iff". So, the condition of "iff"
          //     does not determine the execution the remaining projs directly, and we
          //     can safely continue.
          // (2) both arms are forwarded, i.e. a diamond shape. In this case, "proj"
          //     does not dominate loop->tail(), so it can not be in the if_proj list.
          continue;
        }
      }
      Deoptimization::DeoptReason reason = Deoptimization::trap_request_reason(call->uncommon_trap_request());
      if (reason == Deoptimization::Reason_predicate) {
        break;
      }

      if (loop_predicate_block->has_parse_predicate()) {
        ParsePredicateSuccessProj* loop_parse_predicate_proj = loop_predicate_block->parse_predicate_success_proj();
        hoisted = loop_predication_impl_helper(loop, if_proj, loop_parse_predicate_proj, cl, zero, invar,
                                               Deoptimization::Reason_predicate) | hoisted;
      }
    } // end while
  }

  if (follow_branches) {
    assert(profiled_loop_predicate_block->has_parse_predicate(), "sanity check");
    PathFrequency pf(loop->_head, this);

    // Some projections were skipped due to an early loop exit. Try them with profile data.
    while (if_proj_list.size() > 0) {
      Node* if_proj = if_proj_list.pop();
      float f = pf.to(if_proj);
      if (if_proj->as_Proj()->is_uncommon_trap_if_pattern() &&
          f * loop_trip_cnt >= 1) {
        ParsePredicateSuccessProj* profiled_loop_parse_predicate_proj =
            profiled_loop_predicate_block->parse_predicate_success_proj();
        hoisted = loop_predication_impl_helper(loop, if_proj->as_IfProj(), profiled_loop_parse_predicate_proj,
                                               cl, zero, invar, Deoptimization::Reason_profile_predicate) | hoisted;
      }
    }

    // And look into all branches
    Node_Stack stack(0);
    VectorSet seen;
    Node_List if_proj_list_freq(area);
    while (regions.size() > 0) {
      Node* c = regions.pop();
      loop_predication_follow_branches(c, loop, loop_trip_cnt, pf, stack, seen, if_proj_list_freq);
    }

    for (uint i = 0; i < if_proj_list_freq.size(); i++) {
      IfProjNode* if_proj = if_proj_list_freq.at(i)->as_IfProj();
      ParsePredicateSuccessProj* profiled_loop_parse_predicate_proj =
          profiled_loop_predicate_block->parse_predicate_success_proj();
      hoisted = loop_predication_impl_helper(loop, if_proj, profiled_loop_parse_predicate_proj, cl, zero,
                                             invar, Deoptimization::Reason_profile_predicate) | hoisted;
    }
  }

#ifndef PRODUCT
  // report that the loop predication has been actually performed
  // for this loop
  if (TraceLoopPredicate && hoisted) {
    tty->print("Loop Predication Performed:");
    loop->dump_head();
  }
#endif

  head->verify_strip_mined(1);

  return hoisted;
}

// We cannot add Loop Predicates if:
// (1) Already added Profiled Loop Predicates (Loop Predicates and Profiled Loop Predicates can be dependent
//     through a data node, and thus we should only add new Profiled Loop Predicates which are below Loop Predicates
//     in the graph).
// (2) There are currently no Profiled Loop Predicates, but we have a data node with a control dependency on the Loop
//     Parse Predicate (could happen, for example, if we've removed an earlier created Profiled Loop Predicate with
//     dominated_by()). We should not create a Loop Predicate for a check that is dependent on this data node because
//     the Loop Predicate would end up above the data node with its dependency on the Loop Parse Predicate below. This
//     would become unschedulable. However, we can still hoist the check as Profiled Loop Predicate which would end up
//     below the Loop Parse Predicate.
bool PhaseIdealLoop::can_create_loop_predicates(const PredicateBlock* profiled_loop_predicate_block) const {
  bool has_profiled_loop_predicate_block = profiled_loop_predicate_block != nullptr;
  bool can_create_loop_predicates = true;
  if (has_profiled_loop_predicate_block
      && (profiled_loop_predicate_block->has_runtime_predicates() // (1)
          || profiled_loop_predicate_block->entry()->outcnt() != 1)) { // (2)
    can_create_loop_predicates = false;
  }
  return can_create_loop_predicates;
}

//------------------------------loop_predication--------------------------------
// driver routine for loop predication optimization
bool IdealLoopTree::loop_predication( PhaseIdealLoop *phase) {
  bool hoisted = false;
  // Recursively promote predicates
  if (_child) {
    hoisted = _child->loop_predication( phase);
  }

  // self
  if (can_apply_loop_predication()) {
    hoisted |= phase->loop_predication_impl(this);
  }

  if (_next) { //sibling
    hoisted |= _next->loop_predication( phase);
  }

  return hoisted;
}

bool IdealLoopTree::can_apply_loop_predication() {
  return _head->is_Loop() && !_irreducible && !tail()->is_top();
}
