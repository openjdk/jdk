/*
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/predicates.hpp"
#include "opto/rootnode.hpp"

//================= Loop Unswitching =====================
//
// orig:                       transformed:
//                               if (invariant-test) then
//  predicates                     predicates
//  loop                           loop
//    stmt1                          stmt1
//    if (invariant-test) then       stmt2
//      stmt2                        stmt4
//    else                         endloop
//      stmt3                    else
//    endif                        predicates [clone]
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
  if (phase->find_unswitching_candidate(this) == nullptr) {
    return false;
  }

  // Too speculative if running low on nodes.
  return phase->may_require_nodes(est_loop_clone_sz(2));
}

// Find invariant test in loop body that does not exit the loop. If multiple are found, we pick the first one in the
// loop body. Return the candidate "if" for unswitching.
IfNode* PhaseIdealLoop::find_unswitching_candidate(const IdealLoopTree* loop) const {

  // Find first invariant test that doesn't exit the loop
  LoopNode* head = loop->_head->as_Loop();
  IfNode* unswitching_candidate = nullptr;
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
              unswitching_candidate = iff;
            }
          }
        }
      }
    }
    n = n_dom;
  }
  return unswitching_candidate;
}

// Perform Loop Unswitching on the loop containing an invariant test that does not exit the loop. The loop is cloned
// such that we have two identical loops next to each other - a fast and a slow loop. We modify the loops as follows:
// - Fast loop: We remove the invariant test together with the false path and only leave the true path in the loop.
// - Slow loop: We remove the invariant test together with the true path and only leave the false path in the loop.
//
// We insert a new If node before both loops that performs the removed invariant test. If the test is true at runtime,
// we select the fast loop. Otherwise, we select the slow loop.
void PhaseIdealLoop::do_unswitching(IdealLoopTree* loop, Node_List& old_new) {
  LoopNode* head = loop->_head->as_Loop();
  if (has_control_dependencies_from_predicates(head)) {
    return;
  }

  IfNode* unswitching_candidate = find_unswitching_candidate(loop);
  assert(unswitching_candidate != nullptr, "should be at least one");

#ifndef PRODUCT
  if (TraceLoopOpts) {
    tty->print("Unswitch   %d ", head->unswitch_count()+1);
    loop->dump_head();
  }
#endif

  C->print_method(PHASE_BEFORE_LOOP_UNSWITCHING, 4, head);

  // Need to revert back to normal loop
  if (head->is_CountedLoop() && !head->as_CountedLoop()->is_normal_loop()) {
    head->as_CountedLoop()->set_normal_loop();
  }

  IfNode* loop_selector = create_slow_version_of_loop(loop, old_new, unswitching_candidate);
  IfTrueNode* loop_selector_fast_loop_proj = loop_selector->proj_out(1)->as_IfTrue();

  // Increment unswitch count
  LoopNode* head_clone = old_new[head->_idx]->as_Loop();
  int nct = head->unswitch_count() + 1;
  head->set_unswitch_count(nct);
  head_clone->set_unswitch_count(nct);

  // Hoist invariant casts out of each loop to the appropriate
  // control projection.

  Node_List worklist;
  for (DUIterator_Fast imax, i = unswitching_candidate->fast_outs(imax); i < imax; i++) {
    IfProjNode* proj = unswitching_candidate->fast_out(i)->as_IfProj();
    // Copy to a worklist for easier manipulation
    for (DUIterator_Fast jmax, j = proj->fast_outs(jmax); j < jmax; j++) {
      Node* use = proj->fast_out(j);
      if (use->Opcode() == Op_CheckCastPP && loop->is_invariant(use->in(1))) {
        worklist.push(use);
      }
    }
    IfProjNode* loop_selector_if_proj = loop_selector->proj_out(proj->_con)->as_IfProj();
    while (worklist.size() > 0) {
      Node* use = worklist.pop();
      Node* nuse = use->clone();
      nuse->set_req(0, loop_selector_if_proj);
      _igvn.replace_input_of(use, 1, nuse);
      register_new_node(nuse, loop_selector_if_proj);
      // Same for the clone
      Node* use_clone = old_new[use->_idx];
      _igvn.replace_input_of(use_clone, 1, nuse);
    }
  }

  // Hardwire the control paths in the loops into if(true) and if(false)
  _igvn.rehash_node_delayed(unswitching_candidate);
  dominated_by(loop_selector_fast_loop_proj->as_IfProj(), unswitching_candidate);

  IfNode* unswitching_candidate_clone = old_new[unswitching_candidate->_idx]->as_If();
  _igvn.rehash_node_delayed(unswitching_candidate_clone);
  IfFalseNode* loop_selector_slow_loop_proj = loop_selector->proj_out(0)->as_IfFalse();
  dominated_by(loop_selector_slow_loop_proj, unswitching_candidate_clone);

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
                  head->_idx, unswitching_candidate->_idx,
                  old_new[head->_idx]->_idx, unswitching_candidate_clone->_idx);
  }
#endif

  C->print_method(PHASE_AFTER_LOOP_UNSWITCHING, 4, head_clone);

  C->set_major_progress();
}

bool PhaseIdealLoop::has_control_dependencies_from_predicates(LoopNode* head) {
  Node* entry = head->skip_strip_mined()->in(LoopNode::EntryControl);
  Predicates predicates(entry);
  if (predicates.has_any()) {
    assert(entry->is_IfProj(), "sanity - must be ifProj since there is at least one predicate");
    if (entry->outcnt() > 1) {
      // Bailout if there are predicates from which there are additional control dependencies (i.e. from loop
      // entry 'entry') to previously partially peeled statements since this case is not handled and can lead
      // to a wrong execution. Remove this bailout, once this is fixed.
      return true;
    }
  }
  return false;
}

// Class to create an If node that selects if the fast or the slow loop should be executed at runtime.
class UnswitchedLoopSelector {
  PhaseIdealLoop* _phase;
  IdealLoopTree* _outer_loop;
  Node* _original_loop_entry;
  uint _dom_depth;
  IfNode* _selector;
  IfTrueNode* _fast_loop_proj;
  IfFalseNode* _slow_loop_proj;

  IfNode* create_selector_if(IdealLoopTree* loop, IfNode* unswitch_if_candidate) {
    const uint dom_depth = _phase->dom_depth(_original_loop_entry);
    _phase->igvn().rehash_node_delayed(_original_loop_entry);
    Node* unswitching_candidate_bool = unswitch_if_candidate->in(1)->as_Bool();
    IfNode* selector_if =
        (unswitch_if_candidate->Opcode() == Op_RangeCheck) ?
        new RangeCheckNode(_original_loop_entry, unswitching_candidate_bool, unswitch_if_candidate->_prob,
                           unswitch_if_candidate->_fcnt) :
        new IfNode(_original_loop_entry, unswitching_candidate_bool, unswitch_if_candidate->_prob,
                   unswitch_if_candidate->_fcnt);
    _phase->register_node(selector_if, _outer_loop, _original_loop_entry, dom_depth);
    return selector_if;
  }

  IfTrueNode* create_fast_loop_proj() {
    IfTrueNode* fast_loop_proj = new IfTrueNode(_selector);
    _phase->register_node(fast_loop_proj, _outer_loop, _selector, _dom_depth);
    return fast_loop_proj;
  }

  IfFalseNode* create_slow_loop_proj() {
    IfFalseNode* slow_loop_proj = new IfFalseNode(_selector);
    _phase->register_node(slow_loop_proj, _outer_loop, _selector, _dom_depth);
    return slow_loop_proj;
  }

 public:
  UnswitchedLoopSelector(IfNode* unswitch_if_candidate, IdealLoopTree* loop)
      : _phase(loop->_phase),
        _outer_loop(loop->_head->as_Loop()->is_strip_mined() ? loop->_parent->_parent : loop->_parent),
        _original_loop_entry(loop->_head->as_Loop()->skip_strip_mined()->in(LoopNode::EntryControl)),
        _dom_depth(_phase->dom_depth(_original_loop_entry)),
        _selector(create_selector_if(loop, unswitch_if_candidate)),
        _fast_loop_proj(create_fast_loop_proj()),
        _slow_loop_proj(create_slow_loop_proj()) {}

  uint dom_depth() const {
    return _dom_depth;
  }

  Node* entry() const {
    return _selector->in(0);
  }

  IfNode* selector() const {
    return _selector;
  }

  IfTrueNode* fast_loop_proj() const {
    return _fast_loop_proj;
  }

  IfFalseNode* slow_loop_proj() const {
    return _slow_loop_proj;
  }
};

// Class to unswitch a loop and create predicates at the new loops. The newly cloned loop becomes the slow loop while
// the original loop becomes the fast loop.
class OriginalLoop {
  LoopNode* _loop_head; // The original loop becomes the fast loop.
  LoopNode* _strip_mined_loop_head;
  IdealLoopTree* _loop;
  Node_List* _old_new;
  PhaseIdealLoop* _phase;

  void fix_loop_entries(IfProjNode* iffast_pred, IfProjNode* ifslow_pred) {
    _phase->replace_loop_entry(_strip_mined_loop_head, iffast_pred);
    LoopNode* slow_loop_strip_mined_head = _old_new->at(_strip_mined_loop_head->_idx)->as_Loop();
    _phase->replace_loop_entry(slow_loop_strip_mined_head, ifslow_pred);
  }

#ifdef ASSERT
  static void verify_unswitched_loops(LoopNode* fast_loop_head, UnswitchedLoopSelector& unswitched_loop_selector,
                                      Node_List* old_new) {
    verify_unswitched_loop(fast_loop_head, unswitched_loop_selector.fast_loop_proj());
    verify_unswitched_loop(old_new->at(fast_loop_head->_idx)->as_Loop(), unswitched_loop_selector.slow_loop_proj());
  }

  static void verify_unswitched_loop(LoopNode* loop_head, IfProjNode* loop_selector_if_proj) {
    Node* entry = loop_head->skip_strip_mined()->in(LoopNode::EntryControl);
    Predicates predicates(entry);
    // When skipping all predicates, we should end up at 'loop_selector_if_proj'.
    assert(loop_selector_if_proj == predicates.entry(), "should end up at selector if");
  }
#endif // ASSERT

 public:
  OriginalLoop(IdealLoopTree* loop, Node_List* old_new)
      : _loop_head(loop->_head->as_Loop()),
        _strip_mined_loop_head(_loop_head->skip_strip_mined()),
        _loop(loop),
        _old_new(old_new),
        _phase(loop->_phase) {}


  // Unswitch on the invariant unswitching candidate If. Return the new if which switches between the slow and fast loop.
  IfNode* unswitch(IfNode* unswitching_candidate) {
    UnswitchedLoopSelector unswitched_loop_selector(unswitching_candidate, _loop);
    IfNode* loop_selector = unswitched_loop_selector.selector();
    const uint first_slow_loop_node_index = _phase->C->unique();
    _phase->clone_loop(_loop, *_old_new, _phase->dom_depth(_loop_head),
                       PhaseIdealLoop::CloneIncludesStripMined, loop_selector);
    // Fast (true) and Slow (false) control
    IfProjNode* iffast_pred = unswitched_loop_selector.fast_loop_proj();
    IfProjNode* ifslow_pred = unswitched_loop_selector.slow_loop_proj();
    _phase->clone_parse_and_assertion_predicates_to_unswitched_loop(_loop, *_old_new, iffast_pred, ifslow_pred);

    fix_loop_entries(iffast_pred, ifslow_pred);

    DEBUG_ONLY(verify_unswitched_loops(_loop_head, unswitched_loop_selector, _old_new);)
    return loop_selector;
  }
};

// Create a slow version of the loop by cloning the loop and inserting an If to select the fast or slow version.
// Return the inserted loop selector If.
IfNode* PhaseIdealLoop::create_slow_version_of_loop(IdealLoopTree* loop, Node_List& old_new,
                                                    IfNode* unswitching_candidate) {
  OriginalLoop original_loop(loop, &old_new);
  IfNode* loop_selector = original_loop.unswitch(unswitching_candidate);
  recompute_dom_depth();
  return loop_selector;
}
