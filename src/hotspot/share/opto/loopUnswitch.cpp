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
#include "opto/castnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/loopnode.hpp"
#include "opto/opaquenode.hpp"
#include "opto/predicates.hpp"
#include "opto/rootnode.hpp"

// Loop Unswitching is a loop optimization to move an invariant, non-loop-exiting test in the loop body before the loop.
// Such a test is either always true or always false in all loop iterations and could therefore only be executed once.
// To achieve that, we duplicate the loop and change the original and cloned loop as follows:
// - Original loop -> true-path-loop:
//        The true-path of the invariant, non-loop-exiting test in the original loop
//        is kept while the false-path is killed. We call this unswitched loop version
//        the true-path-loop.
// - Cloned loop -> false-path-loop:
//        The false-path of the invariant, non-loop-exiting test in the cloned loop
//        is kept while the true-path is killed. We call this unswitched loop version
//        the false-path loop.
//
// The invariant, non-loop-exiting test can now be moved before both loops (to only execute it once) and turned into a
// loop selector If node to select at runtime which unswitched loop version should be executed.
// - Loop selector true?  Execute the true-path-loop.
// - Loop selector false? Execute the false-path-loop.
//
// Note that even though an invariant test that exits the loop could also be optimized with Loop Unswitching, it is more
// efficient to simply peel the loop which achieves the same result in a simpler manner (also see policy_peeling()).
//
// The following graphs summarizes the Loop Unswitching optimization.
// We start with the original loop:
//
//                       [Predicates]
//                            |
//                       Original Loop
//                         stmt1
//                         if (invariant-test)
//                           if-path
//                         else
//                           else-path
//                         stmt2
//                       Endloop
//
//
// which is unswitched into a true-path-loop and a false-path-loop together with a loop selector:
//
//
//            [Initialized Assertion Predicates]
//                            |
//                 loop selector If (invariant-test)
//                    /                   \
//                true?                  false?
//                /                         \
//    [Cloned Parse Predicates]         [Cloned Parse Predicates]
//    [Cloned Template                  [Cloned Template
//     Assertion Predicates]             Assertion Predicates]
//          |                                  |
//    True-Path-Loop                    False-Path-Loop
//      cloned stmt1                      cloned stmt1
//      cloned if-path                    cloned else-path
//      cloned stmt2                      cloned stmt2
//    Endloop                           Endloop


// Return true if the loop should be unswitched or false otherwise.
bool IdealLoopTree::policy_unswitching(PhaseIdealLoop* phase) const {
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
  if (phase->find_unswitch_candidate(this) == nullptr) {
    return false;
  }

  // Too speculative if running low on nodes.
  return phase->may_require_nodes(est_loop_clone_sz(2));
}

// Find an invariant test in the loop body that does not exit the loop. If multiple tests are found, we pick the first
// one in the loop body. Return the "unswitch candidate" If to apply Loop Unswitching on.
IfNode* PhaseIdealLoop::find_unswitch_candidate(const IdealLoopTree* loop) const {
  LoopNode* head = loop->_head->as_Loop();
  IfNode* unswitch_candidate = nullptr;
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
              assert(iff->Opcode() == Op_If || iff->is_RangeCheck() || iff->is_BaseCountedLoopEnd(), "valid ifs");
              unswitch_candidate = iff;
            }
          }
        }
      }
    }
    n = n_dom;
  }
  return unswitch_candidate;
}

// This class creates an If node (i.e. loop selector) that selects if the true-path-loop or the false-path-loop should be
// executed at runtime. This is done by finding an invariant and non-loop-exiting unswitch candidate If node (guaranteed
// to exist at this point) to perform Loop Unswitching on.
class UnswitchedLoopSelector : public StackObj {
  PhaseIdealLoop* const _phase;
  IdealLoopTree* const _outer_loop;
  Node* const _original_loop_entry;
  IfNode* const _unswitch_candidate;
  IfNode* const _selector;
  IfTrueNode* const _true_path_loop_proj;
  IfFalseNode* const _false_path_loop_proj;

  enum PathToLoop { TRUE_PATH, FALSE_PATH };

 public:
  UnswitchedLoopSelector(IdealLoopTree* loop)
      : _phase(loop->_phase),
        _outer_loop(loop->skip_strip_mined()->_parent),
        _original_loop_entry(loop->_head->as_Loop()->skip_strip_mined()->in(LoopNode::EntryControl)),
        _unswitch_candidate(find_unswitch_candidate(loop)),
        _selector(create_selector_if()),
        _true_path_loop_proj(create_proj_to_loop(TRUE_PATH)->as_IfTrue()),
        _false_path_loop_proj(create_proj_to_loop(FALSE_PATH)->as_IfFalse()) {
  }
  NONCOPYABLE(UnswitchedLoopSelector);

 private:
  IfNode* find_unswitch_candidate(IdealLoopTree* loop) {
    IfNode* unswitch_candidate = _phase->find_unswitch_candidate(loop);
    assert(unswitch_candidate != nullptr, "guaranteed to exist by policy_unswitching");
    assert(_phase->is_member(loop, unswitch_candidate), "must be inside original loop");
    return unswitch_candidate;
  }

  IfNode* create_selector_if() const {
    const uint dom_depth = _phase->dom_depth(_original_loop_entry);
    _phase->igvn().rehash_node_delayed(_original_loop_entry);
    BoolNode* unswitch_candidate_bool = _unswitch_candidate->in(1)->as_Bool();
    IfNode* selector_if = IfNode::make_with_same_profile(_unswitch_candidate, _original_loop_entry,
                                                         unswitch_candidate_bool);
    _phase->register_node(selector_if, _outer_loop, _original_loop_entry, dom_depth);
    return selector_if;
  }

  IfProjNode* create_proj_to_loop(const PathToLoop path_to_loop) {
    const uint dom_depth = _phase->dom_depth(_original_loop_entry);
    IfProjNode* proj_to_loop;
    if (path_to_loop == TRUE_PATH) {
      proj_to_loop = new IfTrueNode(_selector);
    } else {
      proj_to_loop = new IfFalseNode(_selector);
    }
    _phase->register_node(proj_to_loop, _outer_loop, _selector, dom_depth);
    return proj_to_loop;
  }

 public:
  IfNode* unswitch_candidate() const {
    return _unswitch_candidate;
  }

  IfNode* selector() const {
    return _selector;
  }

  IfTrueNode* true_path_loop_proj() const {
    return _true_path_loop_proj;
  }

  IfFalseNode* false_path_loop_proj() const {
    return _false_path_loop_proj;
  }
};

// Class to unswitch the original loop and create Predicates at the new unswitched loop versions. The newly cloned loop
// becomes the false-path-loop while original loop becomes the true-path-loop.
class OriginalLoop : public StackObj {
  LoopNode* const _loop_head; // OuterStripMinedLoopNode if loop strip mined, else just the loop head.
  IdealLoopTree* const _loop;
  Node_List& _old_new;
  PhaseIdealLoop* const _phase;

 public:
  OriginalLoop(IdealLoopTree* loop, Node_List& old_new)
      : _loop_head(loop->_head->as_Loop()->skip_strip_mined()),
        _loop(loop),
        _old_new(old_new),
        _phase(loop->_phase) {}
  NONCOPYABLE(OriginalLoop);

 private:
  void fix_loop_entries(IfProjNode* true_path_loop_entry, IfProjNode* false_path_loop_entry) {
    _phase->replace_loop_entry(_loop_head, true_path_loop_entry);
    LoopNode* false_path_loop_strip_mined_head = old_to_new(_loop_head)->as_Loop();
    _phase->replace_loop_entry(false_path_loop_strip_mined_head, false_path_loop_entry);
  }

  Node* old_to_new(const Node* old) const {
    return _old_new[old->_idx];
  }

#ifdef ASSERT
  void verify_unswitched_loop_versions(LoopNode* true_path_loop_head,
                                       const UnswitchedLoopSelector& unswitched_loop_selector) const {
    verify_unswitched_loop_version(true_path_loop_head, unswitched_loop_selector.true_path_loop_proj());
    verify_unswitched_loop_version(old_to_new(true_path_loop_head)->as_Loop(),
                                   unswitched_loop_selector.false_path_loop_proj());
  }

  static void verify_unswitched_loop_version(LoopNode* loop_head, IfProjNode* loop_selector_if_proj) {
    Node* entry = loop_head->skip_strip_mined()->in(LoopNode::EntryControl);
    const Predicates predicates(entry);
    // When skipping all predicates, we should end up at 'loop_selector_if_proj'.
    assert(loop_selector_if_proj == predicates.entry(), "should end up at loop selector If");
  }
#endif // ASSERT

  // Remove the unswitch candidate If nodes in both unswitched loop versions which are now dominated by the loop selector
  // If node. Keep the true-path-path in the true-path-loop and the false-path-path in the false-path-loop by setting
  // the bool input accordingly. The unswitch candidate If nodes are folded in the next IGVN round.
  void remove_unswitch_candidate_from_loops(const UnswitchedLoopSelector& unswitched_loop_selector) {
    IfNode* unswitching_candidate = unswitched_loop_selector.unswitch_candidate();
    _phase->igvn().rehash_node_delayed(unswitching_candidate);
    _phase->dominated_by(unswitched_loop_selector.true_path_loop_proj(), unswitching_candidate);

    IfNode* unswitching_candidate_clone = _old_new[unswitching_candidate->_idx]->as_If();
    _phase->igvn().rehash_node_delayed(unswitching_candidate_clone);
    _phase->dominated_by(unswitched_loop_selector.false_path_loop_proj(), unswitching_candidate_clone);
  }

 public:
  // Unswitch the original loop on the invariant loop selector by creating a true-path-loop and a false-path-loop.
  // Remove the unswitch candidate If from both unswitched loop versions which are now covered by the loop selector If.
  void unswitch(const UnswitchedLoopSelector& unswitched_loop_selector) {
    _phase->clone_loop(_loop, _old_new, _phase->dom_depth(_loop_head),
                       PhaseIdealLoop::CloneIncludesStripMined, unswitched_loop_selector.selector());

    // At this point, the selector If projections are the corresponding loop entries.
    // clone_parse_and_assertion_predicates_to_unswitched_loop() could clone additional predicates after the selector
    // If projections. The loop entries are updated accordingly.
    IfProjNode* true_path_loop_entry = unswitched_loop_selector.true_path_loop_proj();
    IfProjNode* false_path_loop_entry = unswitched_loop_selector.false_path_loop_proj();
    _phase->clone_parse_and_assertion_predicates_to_unswitched_loop(_loop, _old_new,
                                                                    true_path_loop_entry, false_path_loop_entry);

    fix_loop_entries(true_path_loop_entry, false_path_loop_entry);

    DEBUG_ONLY(verify_unswitched_loop_versions(_loop->_head->as_Loop(), unswitched_loop_selector);)

    _phase->recompute_dom_depth();
    remove_unswitch_candidate_from_loops(unswitched_loop_selector);
  }
};

// See comments below file header for more information about Loop Unswitching.
void PhaseIdealLoop::do_unswitching(IdealLoopTree* loop, Node_List& old_new) {
  assert(LoopUnswitching, "LoopUnswitching must be enabled");

  LoopNode* original_head = loop->_head->as_Loop();
  if (has_control_dependencies_from_predicates(original_head)) {
    NOT_PRODUCT(trace_loop_unswitching_impossible(original_head);)
    return;
  }

  NOT_PRODUCT(trace_loop_unswitching_count(loop, original_head);)
  C->print_method(PHASE_BEFORE_LOOP_UNSWITCHING, 4, original_head);

  revert_to_normal_loop(original_head);

  const UnswitchedLoopSelector unswitched_loop_selector(loop);
  OriginalLoop original_loop(loop, old_new);
  original_loop.unswitch(unswitched_loop_selector);

  hoist_invariant_check_casts(loop, old_new, unswitched_loop_selector);
  add_unswitched_loop_version_bodies_to_igvn(loop, old_new);

  LoopNode* new_head = old_new[original_head->_idx]->as_Loop();
  increment_unswitch_counts(original_head, new_head);

  NOT_PRODUCT(trace_loop_unswitching_result(unswitched_loop_selector, original_head, new_head);)
  C->print_method(PHASE_AFTER_LOOP_UNSWITCHING, 4, new_head);
  C->set_major_progress();
}

bool PhaseIdealLoop::has_control_dependencies_from_predicates(LoopNode* head) {
  Node* entry = head->skip_strip_mined()->in(LoopNode::EntryControl);
  const Predicates predicates(entry);
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

#ifndef PRODUCT
void PhaseIdealLoop::trace_loop_unswitching_impossible(const LoopNode* original_head) {
  if (TraceLoopUnswitching) {
    tty->print_cr("Loop Unswitching \"%d %s\" not possible due to control dependencies",
                  original_head->_idx, original_head->Name());
  }
}

void PhaseIdealLoop::trace_loop_unswitching_count(IdealLoopTree* loop, LoopNode* original_head) {
  if (TraceLoopOpts) {
    tty->print("Unswitch   %d ", original_head->unswitch_count() + 1);
    loop->dump_head();
  }
}

void PhaseIdealLoop::trace_loop_unswitching_result(const UnswitchedLoopSelector& unswitched_loop_selector,
                                                   const LoopNode* original_head, const LoopNode* new_head) {
  if (TraceLoopUnswitching) {
    IfNode* unswitch_candidate = unswitched_loop_selector.unswitch_candidate();
    IfNode* loop_selector = unswitched_loop_selector.selector();
    tty->print_cr("Loop Unswitching:");
    tty->print_cr("- Unswitch-Candidate-If: %d %s", unswitch_candidate->_idx, unswitch_candidate->Name());
    tty->print_cr("- Loop-Selector-If: %d %s", loop_selector->_idx, loop_selector->Name());
    tty->print_cr("- True-Path-Loop (=Orig): %d %s", original_head->_idx, original_head->Name());
    tty->print_cr("- False-Path-Loop (=Clone): %d %s", new_head->_idx, new_head->Name());
  }
}
#endif

// When unswitching a counted loop, we need to convert it back to a normal loop since it's not a proper pre, main or,
// post loop anymore after loop unswitching.
void PhaseIdealLoop::revert_to_normal_loop(const LoopNode* loop_head) {
  CountedLoopNode* cl = loop_head->isa_CountedLoop();
  if (cl != nullptr && !cl->is_normal_loop()) {
    cl->set_normal_loop();
  }
}

// Hoist invariant CheckCastPPNodes out of each unswitched loop version to the appropriate loop selector If projection.
void PhaseIdealLoop::hoist_invariant_check_casts(const IdealLoopTree* loop, const Node_List& old_new,
                                                 const UnswitchedLoopSelector& unswitched_loop_selector) {
  IfNode* unswitch_candidate = unswitched_loop_selector.unswitch_candidate();
  IfNode* loop_selector = unswitched_loop_selector.selector();
  ResourceMark rm;
  GrowableArray<CheckCastPPNode*> loop_invariant_check_casts;
  for (DUIterator_Fast imax, i = unswitch_candidate->fast_outs(imax); i < imax; i++) {
    IfProjNode* proj = unswitch_candidate->fast_out(i)->as_IfProj();
    // Copy to a worklist for easier manipulation
    for (DUIterator_Fast jmax, j = proj->fast_outs(jmax); j < jmax; j++) {
      CheckCastPPNode* check_cast = proj->fast_out(j)->isa_CheckCastPP();
      if (check_cast != nullptr && loop->is_invariant(check_cast->in(1))) {
        loop_invariant_check_casts.push(check_cast);
      }
    }
    IfProjNode* loop_selector_if_proj = loop_selector->proj_out(proj->_con)->as_IfProj();
    while (loop_invariant_check_casts.length() > 0) {
      CheckCastPPNode* cast = loop_invariant_check_casts.pop();
      Node* cast_clone = cast->clone();
      cast_clone->set_req(0, loop_selector_if_proj);
      _igvn.replace_input_of(cast, 1, cast_clone);
      register_new_node(cast_clone, loop_selector_if_proj);
      // Same for the clone
      Node* use_clone = old_new[cast->_idx];
      _igvn.replace_input_of(use_clone, 1, cast_clone);
    }
  }
}

// Enable more optimizations possibilities in the next IGVN round.
void PhaseIdealLoop::add_unswitched_loop_version_bodies_to_igvn(IdealLoopTree* loop, const Node_List& old_new) {
  loop->record_for_igvn();
  for(int i = loop->_body.size() - 1; i >= 0 ; i--) {
    Node* n = loop->_body[i];
    Node* n_clone = old_new[n->_idx];
    _igvn._worklist.push(n_clone);
  }
}

void PhaseIdealLoop::increment_unswitch_counts(LoopNode* original_head, LoopNode* new_head) {
  const int unswitch_count = original_head->unswitch_count() + 1;
  original_head->set_unswitch_count(unswitch_count);
  new_head->set_unswitch_count(unswitch_count);
}

