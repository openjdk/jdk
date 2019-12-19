/*
 * Copyright (c) 2006, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.inline.hpp"
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/loopnode.hpp"
#include "opto/opaquenode.hpp"
#include "opto/rootnode.hpp"

//================= Loop Unswitching =====================
//
// orig:                       transformed:
//                               if (invariant-test) then
//  predicate                      predicate
//  loop                           loop
//    stmt1                          stmt1
//    if (invariant-test) then       stmt2
//      stmt2                        stmt4
//    else                         endloop
//      stmt3                    else
//    endif                        predicate [clone]
//    stmt4                        loop [clone]
//  endloop                          stmt1 [clone]
//                                   stmt3
//                                   stmt4 [clone]
//                                 endloop
//                               endif
//
// Note: the "else" clause may be empty

//------------------------------policy_unswitching-----------------------------
// Return TRUE or FALSE if the loop should be unswitched
// (ie. clone loop with an invariant test that does not exit the loop)
bool IdealLoopTree::policy_unswitching( PhaseIdealLoop *phase ) const {
  if (!LoopUnswitching) {
    return false;
  }
  if (!_head->is_Loop()) {
    return false;
  }

  // If nodes are depleted, some transform has miscalculated its needs.
  assert(!phase->exceeding_node_budget(), "sanity");

  // check for vectorized loops, any unswitching was already applied
  if (_head->is_CountedLoop() && _head->as_CountedLoop()->is_unroll_only()) {
    return false;
  }

  LoopNode* head = _head->as_Loop();
  if (head->unswitch_count() + 1 > head->unswitch_max()) {
    return false;
  }
  if (phase->find_unswitching_candidate(this) == NULL) {
    return false;
  }

  // Too speculative if running low on nodes.
  return phase->may_require_nodes(est_loop_clone_sz(2));
}

//------------------------------find_unswitching_candidate-----------------------------
// Find candidate "if" for unswitching
IfNode* PhaseIdealLoop::find_unswitching_candidate(const IdealLoopTree *loop) const {

  // Find first invariant test that doesn't exit the loop
  LoopNode *head = loop->_head->as_Loop();
  IfNode* unswitch_iff = NULL;
  Node* n = head->in(LoopNode::LoopBackControl);
  while (n != head) {
    Node* n_dom = idom(n);
    if (n->is_Region()) {
      if (n_dom->is_If()) {
        IfNode* iff = n_dom->as_If();
        if (iff->in(1)->is_Bool()) {
          BoolNode* bol = iff->in(1)->as_Bool();
          if (bol->in(1)->is_Cmp()) {
            // If condition is invariant and not a loop exit,
            // then found reason to unswitch.
            if (loop->is_invariant(bol) && !loop->is_loop_exit(iff)) {
              unswitch_iff = iff;
            }
          }
        }
      }
    }
    n = n_dom;
  }
  return unswitch_iff;
}

//------------------------------do_unswitching-----------------------------
// Clone loop with an invariant test (that does not exit) and
// insert a clone of the test that selects which version to
// execute.
void PhaseIdealLoop::do_unswitching(IdealLoopTree *loop, Node_List &old_new) {

  // Find first invariant test that doesn't exit the loop
  LoopNode *head = loop->_head->as_Loop();

  IfNode* unswitch_iff = find_unswitching_candidate((const IdealLoopTree *)loop);
  assert(unswitch_iff != NULL, "should be at least one");

#ifndef PRODUCT
  if (TraceLoopOpts) {
    tty->print("Unswitch   %d ", head->unswitch_count()+1);
    loop->dump_head();
  }
#endif

  // Need to revert back to normal loop
  if (head->is_CountedLoop() && !head->as_CountedLoop()->is_normal_loop()) {
    head->as_CountedLoop()->set_normal_loop();
  }

  ProjNode* proj_true = create_slow_version_of_loop(loop, old_new, unswitch_iff->Opcode(), CloneIncludesStripMined);

#ifdef ASSERT
  Node* uniqc = proj_true->unique_ctrl_out();
  Node* entry = head->skip_strip_mined()->in(LoopNode::EntryControl);
  Node* predicate = find_predicate(entry);
  if (predicate != NULL) {
    entry = skip_loop_predicates(entry);
  }
  if (predicate != NULL && UseLoopPredicate) {
    // We may have two predicates, find first.
    Node* n = find_predicate(entry);
    if (n != NULL) {
      predicate = n;
      entry = skip_loop_predicates(entry);
    }
  }
  if (predicate != NULL && UseProfiledLoopPredicate) {
    entry = find_predicate(entry);
    if (entry != NULL) predicate = entry;
  }
  if (predicate != NULL) predicate = predicate->in(0);
  assert(proj_true->is_IfTrue() &&
         (predicate == NULL && uniqc == head && !head->is_strip_mined() ||
          predicate == NULL && uniqc == head->in(LoopNode::EntryControl) && head->is_strip_mined() ||
          predicate != NULL && uniqc == predicate), "by construction");
#endif
  // Increment unswitch count
  LoopNode* head_clone = old_new[head->_idx]->as_Loop();
  int nct = head->unswitch_count() + 1;
  head->set_unswitch_count(nct);
  head_clone->set_unswitch_count(nct);

  // Add test to new "if" outside of loop
  IfNode* invar_iff   = proj_true->in(0)->as_If();
  Node* invar_iff_c   = invar_iff->in(0);
  BoolNode* bol       = unswitch_iff->in(1)->as_Bool();
  invar_iff->set_req(1, bol);
  invar_iff->_prob    = unswitch_iff->_prob;

  ProjNode* proj_false = invar_iff->proj_out(0)->as_Proj();

  // Hoist invariant casts out of each loop to the appropriate
  // control projection.

  Node_List worklist;

  for (DUIterator_Fast imax, i = unswitch_iff->fast_outs(imax); i < imax; i++) {
    ProjNode* proj= unswitch_iff->fast_out(i)->as_Proj();
    // Copy to a worklist for easier manipulation
    for (DUIterator_Fast jmax, j = proj->fast_outs(jmax); j < jmax; j++) {
      Node* use = proj->fast_out(j);
      if (use->Opcode() == Op_CheckCastPP && loop->is_invariant(use->in(1))) {
        worklist.push(use);
      }
    }
    ProjNode* invar_proj = invar_iff->proj_out(proj->_con)->as_Proj();
    while (worklist.size() > 0) {
      Node* use = worklist.pop();
      Node* nuse = use->clone();
      nuse->set_req(0, invar_proj);
      _igvn.replace_input_of(use, 1, nuse);
      register_new_node(nuse, invar_proj);
      // Same for the clone
      Node* use_clone = old_new[use->_idx];
      _igvn.replace_input_of(use_clone, 1, nuse);
    }
  }

  // Hardwire the control paths in the loops into if(true) and if(false)
  _igvn.rehash_node_delayed(unswitch_iff);
  short_circuit_if(unswitch_iff, proj_true);

  IfNode* unswitch_iff_clone = old_new[unswitch_iff->_idx]->as_If();
  _igvn.rehash_node_delayed(unswitch_iff_clone);
  short_circuit_if(unswitch_iff_clone, proj_false);

  // Reoptimize loops
  loop->record_for_igvn();
  for(int i = loop->_body.size() - 1; i >= 0 ; i--) {
    Node *n = loop->_body[i];
    Node *n_clone = old_new[n->_idx];
    _igvn._worklist.push(n_clone);
  }

#ifndef PRODUCT
  if (TraceLoopUnswitching) {
    tty->print_cr("Loop unswitching orig: %d @ %d  new: %d @ %d",
                  head->_idx,                unswitch_iff->_idx,
                  old_new[head->_idx]->_idx, unswitch_iff_clone->_idx);
  }
#endif

  C->set_major_progress();
}

//-------------------------create_slow_version_of_loop------------------------
// Create a slow version of the loop by cloning the loop
// and inserting an if to select fast-slow versions.
// Return control projection of the entry to the fast version.
ProjNode* PhaseIdealLoop::create_slow_version_of_loop(IdealLoopTree *loop,
                                                      Node_List &old_new,
                                                      int opcode,
                                                      CloneLoopMode mode) {
  LoopNode* head  = loop->_head->as_Loop();
  bool counted_loop = head->is_CountedLoop();
  Node*     entry = head->skip_strip_mined()->in(LoopNode::EntryControl);
  _igvn.rehash_node_delayed(entry);
  IdealLoopTree* outer_loop = loop->_parent;

  head->verify_strip_mined(1);

  Node *cont      = _igvn.intcon(1);
  set_ctrl(cont, C->root());
  Node* opq       = new Opaque1Node(C, cont);
  register_node(opq, outer_loop, entry, dom_depth(entry));
  Node *bol       = new Conv2BNode(opq);
  register_node(bol, outer_loop, entry, dom_depth(entry));
  IfNode* iff = (opcode == Op_RangeCheck) ? new RangeCheckNode(entry, bol, PROB_MAX, COUNT_UNKNOWN) :
    new IfNode(entry, bol, PROB_MAX, COUNT_UNKNOWN);
  register_node(iff, outer_loop, entry, dom_depth(entry));
  ProjNode* iffast = new IfTrueNode(iff);
  register_node(iffast, outer_loop, iff, dom_depth(iff));
  ProjNode* ifslow = new IfFalseNode(iff);
  register_node(ifslow, outer_loop, iff, dom_depth(iff));

  // Clone the loop body.  The clone becomes the slow loop.  The
  // original pre-header will (illegally) have 3 control users
  // (old & new loops & new if).
  clone_loop(loop, old_new, dom_depth(head->skip_strip_mined()), mode, iff);
  assert(old_new[head->_idx]->is_Loop(), "" );

  // Fast (true) control
  Node* iffast_pred = clone_loop_predicates(entry, iffast, !counted_loop);

  // Slow (false) control
  Node* ifslow_pred = clone_loop_predicates(entry, ifslow, !counted_loop);

  Node* l = head->skip_strip_mined();
  _igvn.replace_input_of(l, LoopNode::EntryControl, iffast_pred);
  set_idom(l, iffast_pred, dom_depth(l));
  LoopNode* slow_l = old_new[head->_idx]->as_Loop()->skip_strip_mined();
  _igvn.replace_input_of(slow_l, LoopNode::EntryControl, ifslow_pred);
  set_idom(slow_l, ifslow_pred, dom_depth(l));

  if (iffast != iffast_pred && entry->outcnt() > 1) {
    // This situation occurs when only non-CFG nodes (i.e. no control dependencies between them) with a control
    // input from the loop header were partially peeled before (now control dependent on loop entry control).
    // If additional CFG nodes were peeled, then the insertion point of the loop predicates from the parsing stage
    // would not be found anymore and the predicates not cloned at all (i.e. iffast == iffast_pred) as it happens
    // for normal peeling. Those partially peeled statements have a control input from the old loop entry control
    // and need to be executed after the predicates. These control dependencies need to be removed from the old
    // entry control and added to the new entry control nodes 'iffast_pred' and 'ifslow_pred'. Since each node can
    // only have one control input, we need to create clones for all statements (2) that can be reached over a path
    // from the old entry control 'entry' (1) to a loop phi (8, 9). The old nodes (2) will be moved to the fast loop and the
    // new cloned nodes (10) to the slow loop.
    //
    // The result of the following algorithm is visualized below. The cloned loop predicates for the fast loop
    // are between the loop selection node (3) and the entry control for the fast loop (4) and for the slow loop
    // between the loop selection node (3) and the entry control for the slow loop (5), respectively.
    //
    //      1 entry                                    1 entry
    //      /     \                                       |
    //  2 stmt    3 iff                                 3 iff
    //   |        /  \                                 /     \
    //   |      ..    ..                             ..       ..
    //   |      /      \                             /         \
    //   | 4 iffast_p  5 ifslow_p          4 iffast_p          5 ifslow_p
    //   |     |          |                /    \               /       \
    //   |   6 head  7 slow_head   ==>  6 head  2 stmt   7 slow_head  10 cloned_stmt
    //   |     |          |                \    /               \       /
    //   +--\  |    +--\  |                8 phi                  9 phi
    //   |   8 phi  |  9 phi
    //   |          |
    //   +----------+
    //
    assert(ifslow != ifslow_pred, "sanity - must also be different");

    ResourceMark rm;
    Unique_Node_List worklist;
    Unique_Node_List phis;
    Node_List old_clone;
    LoopNode* slow_head = old_new[head->_idx]->as_Loop();

    // 1) Do a BFS starting from the outputs of the original entry control node 'entry' to all (loop) phis
    // and add the non-phi nodes to the worklist.
    // First get all outputs of 'entry' which are not the new "loop selection check" 'iff'.
    for (DUIterator_Fast imax, i = entry->fast_outs(imax); i < imax; i++) {
      Node* stmt = entry->fast_out(i);
      if (stmt != iff) {
        assert(!stmt->is_CFG(), "cannot be a CFG node");
        worklist.push(stmt);
      }
    }

    // Then do a BFS from all collected nodes so far and stop if a phi node is hit.
    // Keep track of them on a separate 'phis' list to adjust their inputs later.
    for (uint i = 0; i < worklist.size(); i++) {
      Node* stmt = worklist.at(i);
      for (DUIterator_Fast jmax, j = stmt->fast_outs(jmax); j < jmax; j++) {
        Node* out = stmt->fast_out(j);
        assert(!out->is_CFG(), "cannot be a CFG node");
        if (out->is_Phi()) {
          assert(out->in(PhiNode::Region) == head || out->in(PhiNode::Region) == slow_head,
                 "phi must be either part of the slow or the fast loop");
          phis.push(out);
        } else {
          worklist.push(out);
        }
      }
    }

    // 2) All nodes of interest are in 'worklist' and are now cloned. This could not be done simultaneously
    // in step 1 in an easy way because we could have cloned a node which has an input that is added to the
    // worklist later. As a result, the BFS would hit a clone which does not need to be cloned again.
    // While cloning a node, the control inputs to 'entry' are updated such that the old node points to
    // 'iffast_pred' and the clone to 'ifslow_pred', respectively.
    for (uint i = 0; i < worklist.size(); i++) {
      Node* stmt = worklist.at(i);
      assert(!stmt->is_CFG(), "cannot be a CFG node");
      Node* cloned_stmt = stmt->clone();
      old_clone.map(stmt->_idx, cloned_stmt);
      _igvn.register_new_node_with_optimizer(cloned_stmt);

      if (stmt->in(0) == entry) {
        _igvn.replace_input_of(stmt, 0, iffast_pred);
        set_ctrl(stmt, iffast_pred);
        _igvn.replace_input_of(cloned_stmt, 0, ifslow_pred);
        set_ctrl(cloned_stmt, ifslow_pred);
      }
    }

    // 3) Update the entry control of all collected phi nodes of the slow loop to use the cloned nodes
    // instead of the old ones from the worklist
    for (uint i = 0; i < phis.size(); i++) {
      assert(phis.at(i)->is_Phi(), "must be a phi");
      PhiNode* phi = phis.at(i)->as_Phi();
      if (phi->in(PhiNode::Region) == slow_head) {
        // Slow loop: Update phi entry control to use the cloned version instead of the old one from the worklist
        Node* entry_control = phi->in(LoopNode::EntryControl);
        _igvn.replace_input_of(phi, LoopNode::EntryControl, old_clone[phi->in(LoopNode::EntryControl)->_idx]);
      }

    }

    // 4) Replace all input edges of cloned nodes from old nodes on the worklist by an input edge from their
    // corresponding cloned version.
    for (uint i = 0; i < worklist.size(); i++) {
      Node* stmt = worklist.at(i);
      for (uint j = 0; j < stmt->req(); j++) {
        Node* in = stmt->in(j);
        if (in == NULL) {
          continue;
        }

        if (worklist.contains(in)) {
          // Replace the edge old1->clone_of_old_2 with an edge clone_of_old1->clone_of_old2
          old_clone[stmt->_idx]->set_req(j, old_clone[in->_idx]);
        }
      }
    }
  }
  recompute_dom_depth();

  return iffast;
}

LoopNode* PhaseIdealLoop::create_reserve_version_of_loop(IdealLoopTree *loop, CountedLoopReserveKit* lk) {
  Node_List old_new;
  LoopNode* head  = loop->_head->as_Loop();
  bool counted_loop = head->is_CountedLoop();
  Node*     entry = head->skip_strip_mined()->in(LoopNode::EntryControl);
  _igvn.rehash_node_delayed(entry);
  IdealLoopTree* outer_loop = head->is_strip_mined() ? loop->_parent->_parent : loop->_parent;

  ConINode* const_1 = _igvn.intcon(1);
  set_ctrl(const_1, C->root());
  IfNode* iff = new IfNode(entry, const_1, PROB_MAX, COUNT_UNKNOWN);
  register_node(iff, outer_loop, entry, dom_depth(entry));
  ProjNode* iffast = new IfTrueNode(iff);
  register_node(iffast, outer_loop, iff, dom_depth(iff));
  ProjNode* ifslow = new IfFalseNode(iff);
  register_node(ifslow, outer_loop, iff, dom_depth(iff));

  // Clone the loop body.  The clone becomes the slow loop.  The
  // original pre-header will (illegally) have 3 control users
  // (old & new loops & new if).
  clone_loop(loop, old_new, dom_depth(head), CloneIncludesStripMined, iff);
  assert(old_new[head->_idx]->is_Loop(), "" );

  LoopNode* slow_head = old_new[head->_idx]->as_Loop();

#ifndef PRODUCT
  if (TraceLoopOpts) {
    tty->print_cr("PhaseIdealLoop::create_reserve_version_of_loop:");
    tty->print("\t iff = %d, ", iff->_idx); iff->dump();
    tty->print("\t iffast = %d, ", iffast->_idx); iffast->dump();
    tty->print("\t ifslow = %d, ", ifslow->_idx); ifslow->dump();
    tty->print("\t before replace_input_of: head = %d, ", head->_idx); head->dump();
    tty->print("\t before replace_input_of: slow_head = %d, ", slow_head->_idx); slow_head->dump();
  }
#endif

  // Fast (true) control
  _igvn.replace_input_of(head->skip_strip_mined(), LoopNode::EntryControl, iffast);
  // Slow (false) control
  _igvn.replace_input_of(slow_head->skip_strip_mined(), LoopNode::EntryControl, ifslow);

  recompute_dom_depth();

  lk->set_iff(iff);

#ifndef PRODUCT
  if (TraceLoopOpts ) {
    tty->print("\t after  replace_input_of: head = %d, ", head->_idx); head->dump();
    tty->print("\t after  replace_input_of: slow_head = %d, ", slow_head->_idx); slow_head->dump();
  }
#endif

  return slow_head->as_Loop();
}

CountedLoopReserveKit::CountedLoopReserveKit(PhaseIdealLoop* phase, IdealLoopTree *loop, bool active = true) :
  _phase(phase),
  _lpt(loop),
  _lp(NULL),
  _iff(NULL),
  _lp_reserved(NULL),
  _has_reserved(false),
  _use_new(false),
  _active(active)
  {
    create_reserve();
  };

CountedLoopReserveKit::~CountedLoopReserveKit() {
  if (!_active) {
    return;
  }

  if (_has_reserved && !_use_new) {
    // intcon(0)->iff-node reverts CF to the reserved copy
    ConINode* const_0 = _phase->_igvn.intcon(0);
    _phase->set_ctrl(const_0, _phase->C->root());
    _iff->set_req(1, const_0);

    #ifndef PRODUCT
      if (TraceLoopOpts) {
        tty->print_cr("CountedLoopReserveKit::~CountedLoopReserveKit()");
        tty->print("\t discard loop %d and revert to the reserved loop clone %d: ", _lp->_idx, _lp_reserved->_idx);
        _lp_reserved->dump();
      }
    #endif
  }
}

bool CountedLoopReserveKit::create_reserve() {
  if (!_active) {
    return false;
  }

  if(!_lpt->_head->is_CountedLoop()) {
    if (TraceLoopOpts) {
      tty->print_cr("CountedLoopReserveKit::create_reserve: %d not counted loop", _lpt->_head->_idx);
    }
    return false;
  }
  CountedLoopNode *cl = _lpt->_head->as_CountedLoop();
  if (!cl->is_valid_counted_loop()) {
    if (TraceLoopOpts) {
      tty->print_cr("CountedLoopReserveKit::create_reserve: %d not valid counted loop", cl->_idx);
    }
    return false; // skip malformed counted loop
  }
  if (!cl->is_main_loop()) {
    bool loop_not_canonical = true;
    if (cl->is_post_loop() && (cl->slp_max_unroll() > 0)) {
      loop_not_canonical = false;
    }
    // only reject some loop forms
    if (loop_not_canonical) {
      if (TraceLoopOpts) {
        tty->print_cr("CountedLoopReserveKit::create_reserve: %d not canonical loop", cl->_idx);
      }
      return false; // skip normal, pre, and post (conditionally) loops
    }
  }

  _lp = _lpt->_head->as_Loop();
  _lp_reserved = _phase->create_reserve_version_of_loop(_lpt, this);

  if (!_lp_reserved->is_CountedLoop()) {
    return false;
  }

  Node* ifslow_pred = _lp_reserved->skip_strip_mined()->in(LoopNode::EntryControl);

  if (!ifslow_pred->is_IfFalse()) {
    return false;
  }

  Node* iff = ifslow_pred->in(0);
  if (!iff->is_If() || iff != _iff) {
    return false;
  }

  if (iff->in(1)->Opcode() != Op_ConI) {
    return false;
  }

  return _has_reserved = true;
}
