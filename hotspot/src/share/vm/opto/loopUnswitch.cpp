/*
 * Copyright (c) 2006, 2008, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_loopUnswitch.cpp.incl"

//================= Loop Unswitching =====================
//
// orig:                       transformed:
//                               if (invariant-test) then
//  loop                           loop
//    stmt1                          stmt1
//    if (invariant-test) then       stmt2
//      stmt2                        stmt4
//    else                         endloop
//      stmt3                    else
//    endif                        loop [clone]
//    stmt4                          stmt1 [clone]
//  endloop                          stmt3
//                                   stmt4 [clone]
//                                 endloop
//                               endif
//
// Note: the "else" clause may be empty

//------------------------------policy_unswitching-----------------------------
// Return TRUE or FALSE if the loop should be unswitched
// (ie. clone loop with an invariant test that does not exit the loop)
bool IdealLoopTree::policy_unswitching( PhaseIdealLoop *phase ) const {
  if( !LoopUnswitching ) {
    return false;
  }
  if (!_head->is_Loop()) {
    return false;
  }
  uint nodes_left = MaxNodeLimit - phase->C->unique();
  if (2 * _body.size() > nodes_left) {
    return false; // Too speculative if running low on nodes.
  }
  LoopNode* head = _head->as_Loop();
  if (head->unswitch_count() + 1 > head->unswitch_max()) {
    return false;
  }
  return phase->find_unswitching_candidate(this) != NULL;
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
void PhaseIdealLoop::do_unswitching (IdealLoopTree *loop, Node_List &old_new) {

  // Find first invariant test that doesn't exit the loop
  LoopNode *head = loop->_head->as_Loop();

  IfNode* unswitch_iff = find_unswitching_candidate((const IdealLoopTree *)loop);
  assert(unswitch_iff != NULL, "should be at least one");

  // Need to revert back to normal loop
  if (head->is_CountedLoop() && !head->as_CountedLoop()->is_normal_loop()) {
    head->as_CountedLoop()->set_normal_loop();
  }

  ProjNode* proj_true = create_slow_version_of_loop(loop, old_new);

  assert(proj_true->is_IfTrue() && proj_true->unique_ctrl_out() == head, "by construction");

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
      _igvn.hash_delete(use);
      use->set_req(1, nuse);
      _igvn._worklist.push(use);
      register_new_node(nuse, invar_proj);
      // Same for the clone
      Node* use_clone = old_new[use->_idx];
      _igvn.hash_delete(use_clone);
      use_clone->set_req(1, nuse);
      _igvn._worklist.push(use_clone);
    }
  }

  // Hardwire the control paths in the loops into if(true) and if(false)
  _igvn.hash_delete(unswitch_iff);
  short_circuit_if(unswitch_iff, proj_true);
  _igvn._worklist.push(unswitch_iff);

  IfNode* unswitch_iff_clone = old_new[unswitch_iff->_idx]->as_If();
  _igvn.hash_delete(unswitch_iff_clone);
  short_circuit_if(unswitch_iff_clone, proj_false);
  _igvn._worklist.push(unswitch_iff_clone);

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
                                                      Node_List &old_new) {
  LoopNode* head  = loop->_head->as_Loop();
  Node*     entry = head->in(LoopNode::EntryControl);
  _igvn.hash_delete(entry);
  _igvn._worklist.push(entry);
  IdealLoopTree* outer_loop = loop->_parent;

  Node *cont      = _igvn.intcon(1);
  set_ctrl(cont, C->root());
  Node* opq       = new (C, 2) Opaque1Node(C, cont);
  register_node(opq, outer_loop, entry, dom_depth(entry));
  Node *bol       = new (C, 2) Conv2BNode(opq);
  register_node(bol, outer_loop, entry, dom_depth(entry));
  IfNode* iff = new (C, 2) IfNode(entry, bol, PROB_MAX, COUNT_UNKNOWN);
  register_node(iff, outer_loop, entry, dom_depth(entry));
  ProjNode* iffast = new (C, 1) IfTrueNode(iff);
  register_node(iffast, outer_loop, iff, dom_depth(iff));
  ProjNode* ifslow = new (C, 1) IfFalseNode(iff);
  register_node(ifslow, outer_loop, iff, dom_depth(iff));

  // Clone the loop body.  The clone becomes the fast loop.  The
  // original pre-header will (illegally) have 2 control users (old & new loops).
  clone_loop(loop, old_new, dom_depth(head), iff);
  assert(old_new[head->_idx]->is_Loop(), "" );

  // Fast (true) control
  _igvn.hash_delete(head);
  head->set_req(LoopNode::EntryControl, iffast);
  set_idom(head, iffast, dom_depth(head));
  _igvn._worklist.push(head);

  // Slow (false) control
  LoopNode* slow_head = old_new[head->_idx]->as_Loop();
  _igvn.hash_delete(slow_head);
  slow_head->set_req(LoopNode::EntryControl, ifslow);
  set_idom(slow_head, ifslow, dom_depth(slow_head));
  _igvn._worklist.push(slow_head);

  recompute_dom_depth();

  return iffast;
}
