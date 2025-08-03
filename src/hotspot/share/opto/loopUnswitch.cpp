/*
 * Copyright (c) 2006, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.inline.hpp"
#include "opto/castnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/loopnode.hpp"
#include "opto/opaquenode.hpp"
#include "opto/predicates.hpp"
#include "opto/rootnode.hpp"

// Multiversioning:
// A loop is cloned, and a selector If decides which loop is taken at run-time: the true-path-loop (original) or the
// false-path-loop (cloned).
//
// Use-cases:
// - Speculative compilation:
//   The selector If checks some assumptions which allow stronger optimization in the true-path-loop. If the assumptions
//   do not hold, we can still execute in the false-path-loop, although with fewer optimizations.
//   See: PhaseIdealLoop::maybe_multiversion_for_auto_vectorization_runtime_checks
//        PhaseIdealLoop::create_new_if_for_multiversion
//
// - Unswitching:
//   The selector If has the same (loop invariant) condition as some unswitching candidate If inside the loop. This
//   allows us to constant-fold the unswitching candidate If to true in the true-path-loop and to false in the
//   false-path-loop, thus eliminating the unswitching candidate If from the loop.
//
//
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

// LoopSelector is used for loop multiversioning and unswitching. This class creates an If node (i.e. loop selector)
// that selects if the true-path-loop or the false-path-loop should be executed at runtime.
class LoopSelector : public StackObj {
  // Cached fields for construction.
  PhaseIdealLoop* const _phase;
  IdealLoopTree* const _outer_loop;
  Node* const _original_loop_entry;
  const uint _dom_depth; // of original_loop_entry

  // Constructed selector if with its projections.
  IfNode* const _selector;
  IfTrueNode* const _true_path_loop_proj;
  IfFalseNode* const _false_path_loop_proj;

  enum PathToLoop { TRUE_PATH, FALSE_PATH };

 public:
  // For multiversioning: create a new selector (multiversion_if) from a bol condition.
  LoopSelector(IdealLoopTree* loop, Node* bol, float prob, float fcnt)
      : _phase(loop->_phase),
        _outer_loop(loop->skip_strip_mined()->_parent),
        _original_loop_entry(loop->_head->as_Loop()->skip_strip_mined()->in(LoopNode::EntryControl)),
        _dom_depth(_phase->dom_depth(_original_loop_entry)),
        _selector(create_multiversioning_if(bol, prob, fcnt)), // multiversioning
        _true_path_loop_proj(create_proj_to_loop(TRUE_PATH)->as_IfTrue()),
        _false_path_loop_proj(create_proj_to_loop(FALSE_PATH)->as_IfFalse()) {
  }

  // For unswitching: create an unswitching if before the loop, from a pre-existing
  //                  unswitching_candidate inside the loop.
  LoopSelector(IdealLoopTree* loop, IfNode* unswitch_candidate)
      : _phase(loop->_phase),
        _outer_loop(loop->skip_strip_mined()->_parent),
        _original_loop_entry(loop->_head->as_Loop()->skip_strip_mined()->in(LoopNode::EntryControl)),
        _dom_depth(_phase->dom_depth(_original_loop_entry)),
        _selector(create_unswitching_if(unswitch_candidate)), // unswitching
        _true_path_loop_proj(create_proj_to_loop(TRUE_PATH)->as_IfTrue()),
        _false_path_loop_proj(create_proj_to_loop(FALSE_PATH)->as_IfFalse()) {
  }
  NONCOPYABLE(LoopSelector);

  IfNode* create_multiversioning_if(Node* bol, float prob, float fcnt) {
    _phase->igvn().rehash_node_delayed(_original_loop_entry);
    IfNode* selector_if = new IfNode(_original_loop_entry, bol, prob, fcnt);
    _phase->register_node(selector_if, _outer_loop, _original_loop_entry, _dom_depth);
    return selector_if;
  }

  IfNode* create_unswitching_if(IfNode* unswitch_candidate) {
    _phase->igvn().rehash_node_delayed(_original_loop_entry);
    BoolNode* unswitch_candidate_bool = unswitch_candidate->in(1)->as_Bool();
    IfNode* selector_if = IfNode::make_with_same_profile(unswitch_candidate, _original_loop_entry,
                                                         unswitch_candidate_bool);
    _phase->register_node(selector_if, _outer_loop, _original_loop_entry, _dom_depth);
    return selector_if;
  }

 private:
  IfProjNode* create_proj_to_loop(const PathToLoop path_to_loop) {
    IfProjNode* proj_to_loop;
    if (path_to_loop == TRUE_PATH) {
      proj_to_loop = new IfTrueNode(_selector);
    } else {
      proj_to_loop = new IfFalseNode(_selector);
    }
    _phase->register_node(proj_to_loop, _outer_loop, _selector, _dom_depth);
    return proj_to_loop;
  }

 public:
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

// This class creates an If node (i.e. loop selector) that selects if the true-path-loop or the false-path-loop should be
// executed at runtime. This is done by finding an invariant and non-loop-exiting unswitch candidate If node (guaranteed
// to exist at this point) to perform Loop Unswitching on.
class UnswitchedLoopSelector : public StackObj {
  IfNode* const _unswitch_candidate;
  const LoopSelector _loop_selector;

 public:
  UnswitchedLoopSelector(IdealLoopTree* loop)
      : _unswitch_candidate(find_unswitch_candidate(loop)),
        _loop_selector(loop, _unswitch_candidate) {}
  NONCOPYABLE(UnswitchedLoopSelector);

 private:
  static IfNode* find_unswitch_candidate(IdealLoopTree* loop) {
    IfNode* unswitch_candidate = loop->_phase->find_unswitch_candidate(loop);
    assert(unswitch_candidate != nullptr, "guaranteed to exist by policy_unswitching");
    assert(loop->_phase->is_member(loop, unswitch_candidate), "must be inside original loop");
    return unswitch_candidate;
  }

 public:
  IfNode* unswitch_candidate() const {
    return _unswitch_candidate;
  }

  const LoopSelector& loop_selector() const {
    return _loop_selector;
  }
};

// Class to unswitch the original loop and create Predicates at the new unswitched loop versions. The newly cloned loop
// becomes the false-path-loop while original loop becomes the true-path-loop.
class OriginalLoop : public StackObj {
  LoopNode* const _loop_head;
  LoopNode* const _outer_loop_head; // OuterStripMinedLoopNode if loop strip mined, else just the loop head.
  IdealLoopTree* const _loop;
  Node_List& _old_new;
  PhaseIdealLoop* const _phase;

 public:
  OriginalLoop(IdealLoopTree* loop, Node_List& old_new)
      : _loop_head(loop->_head->as_Loop()),
        _outer_loop_head(loop->_head->as_Loop()->skip_strip_mined()),
        _loop(loop),
        _old_new(old_new),
        _phase(loop->_phase) {}
  NONCOPYABLE(OriginalLoop);

  // Unswitch the original loop on the invariant loop selector by creating a true-path-loop and a false-path-loop.
  // Remove the unswitch candidate If from both unswitched loop versions which are now covered by the loop selector If.
  void unswitch(const UnswitchedLoopSelector& unswitched_loop_selector) {
    multiversion(unswitched_loop_selector.loop_selector());
    remove_unswitch_candidate_from_loops(unswitched_loop_selector);
  }

  // Multiversion the original loop. The loop selector if selects between the original loop (true-path-loop), and
  // a copy of it (false-path-loop).
  void multiversion(const LoopSelector& loop_selector) {
    const uint first_false_path_loop_node_index = _phase->C->unique();
    clone_loop(loop_selector);

    move_parse_and_template_assertion_predicates_to_unswitched_loops(loop_selector,
                                                                     first_false_path_loop_node_index);
    DEBUG_ONLY(verify_loop_versions(_loop->_head->as_Loop(), loop_selector);)

    _phase->recompute_dom_depth();
  }

 private:
  void clone_loop(const LoopSelector& loop_selector) {
    _phase->clone_loop(_loop, _old_new, _phase->dom_depth(_outer_loop_head),
                       PhaseIdealLoop::CloneIncludesStripMined, loop_selector.selector());
    fix_loop_entries(loop_selector);
  }

  void fix_loop_entries(const LoopSelector& loop_selector) const {
    _phase->replace_loop_entry(_outer_loop_head, loop_selector.true_path_loop_proj());
    LoopNode* false_path_loop_strip_mined_head = old_to_new(_outer_loop_head)->as_Loop();
    _phase->replace_loop_entry(false_path_loop_strip_mined_head,
                               loop_selector.false_path_loop_proj());
  }

  // Moves the Parse And Template Assertion Predicates to the true and false path loop. They are inserted between the
  // loop heads and the loop selector If projections. The old Parse and Template Assertion Predicates before
  // the unswitched loop selector are killed.
  void move_parse_and_template_assertion_predicates_to_unswitched_loops(
    const LoopSelector& loop_selector, const uint first_false_path_loop_node_index) const {
    const NodeInOriginalLoopBody node_in_true_path_loop_body(first_false_path_loop_node_index, _old_new);
    const NodeInClonedLoopBody node_in_false_path_loop_body(first_false_path_loop_node_index);
    CloneUnswitchedLoopPredicatesVisitor
    clone_unswitched_loop_predicates_visitor(_loop_head, old_to_new(_loop_head)->as_Loop(), node_in_true_path_loop_body,
                                             node_in_false_path_loop_body, _phase);
    Node* source_loop_entry = loop_selector.selector()->in(0);
    PredicateIterator predicate_iterator(source_loop_entry);
    predicate_iterator.for_each(clone_unswitched_loop_predicates_visitor);
  }

#ifdef ASSERT
  void verify_loop_versions(LoopNode* true_path_loop_head,
                            const LoopSelector& loop_selector) const {
    verify_loop_version(true_path_loop_head,
                        loop_selector.true_path_loop_proj());
    verify_loop_version(old_to_new(true_path_loop_head)->as_Loop(),
                        loop_selector.false_path_loop_proj());
  }

  static void verify_loop_version(LoopNode* loop_head, IfProjNode* loop_selector_if_proj) {
    Node* entry = loop_head->skip_strip_mined()->in(LoopNode::EntryControl);
    const Predicates predicates(entry);
    // When skipping all predicates, we should end up at 'loop_selector_if_proj'.
    assert(loop_selector_if_proj == predicates.entry(), "should end up at loop selector If");
  }
#endif // ASSERT

  Node* old_to_new(const Node* old) const {
    return _old_new[old->_idx];
  }

  // Remove the unswitch candidate If nodes in both unswitched loop versions which are now dominated by the loop selector
  // If node. Keep the true-path-path in the true-path-loop and the false-path-path in the false-path-loop by setting
  // the bool input accordingly. The unswitch candidate If nodes are folded in the next IGVN round.
  void remove_unswitch_candidate_from_loops(const UnswitchedLoopSelector& unswitched_loop_selector) {
    const LoopSelector& loop_selector = unswitched_loop_selector.loop_selector();;
    IfNode* unswitch_candidate        = unswitched_loop_selector.unswitch_candidate();
    _phase->igvn().rehash_node_delayed(unswitch_candidate);
    _phase->dominated_by(loop_selector.true_path_loop_proj(), unswitch_candidate);

    IfNode* unswitch_candidate_clone = _old_new[unswitch_candidate->_idx]->as_If();
    _phase->igvn().rehash_node_delayed(unswitch_candidate_clone);
    _phase->dominated_by(loop_selector.false_path_loop_proj(), unswitch_candidate_clone);
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

void PhaseIdealLoop::do_multiversioning(IdealLoopTree* lpt, Node_List& old_new) {
#ifndef PRODUCT
  if (TraceLoopOpts || TraceLoopMultiversioning) {
    tty->print("Multiversion ");
    lpt->dump_head();
  }
#endif
  assert(LoopMultiversioning, "LoopMultiversioning must be enabled");

  CountedLoopNode* original_head = lpt->_head->as_CountedLoop();
  C->print_method(PHASE_BEFORE_LOOP_MULTIVERSIONING, 4, original_head);

  Node* one = _igvn.intcon(1);
  set_ctrl(one, C->root());
  Node* opaque = new OpaqueMultiversioningNode(C, one);
  set_ctrl(opaque, C->root());
  _igvn.register_new_node_with_optimizer(opaque);
  _igvn.set_type(opaque, TypeInt::BOOL);

  const LoopSelector loop_selector(lpt, opaque, PROB_LIKELY_MAG(3), COUNT_UNKNOWN);
  OriginalLoop original_loop(lpt, old_new);
  original_loop.multiversion(loop_selector);

  add_unswitched_loop_version_bodies_to_igvn(lpt, old_new);

  CountedLoopNode* new_head = old_new[original_head->_idx]->as_CountedLoop();
  original_head->set_multiversion_fast_loop();
  new_head->set_multiversion_delayed_slow_loop();

  NOT_PRODUCT(trace_loop_multiversioning_result(loop_selector, original_head, new_head);)
  C->print_method(PHASE_AFTER_LOOP_MULTIVERSIONING, 4, new_head);
  C->set_major_progress();
}

// Create a new if in the multiversioning pattern, adding an additional condition for the
// multiversioning fast-loop.
//
// Before:
//                       entry  opaque
//                         |      |
//                      multiversion_if
//                         |      |
//        +----------------+      +---------------+
//        |                                       |
//   multiversion_fast_proj          multiversion_slow_proj
//                                                |
//                                                +--------+
//                                                         |
//                                                      slow_path
//
//
// After:
//                     entry  opaque <-- to be replaced by caller
//                         |  |
//                        new_if
//                         |  |
//                         |  +-----------------------------+
//                         |                                |
//                 new_if_true  opaque                new_if_false
//                         |      |                         |
//                      multiversion_if                     |
//                         |      |                         |
//        +----------------+      +---------------+         |
//        |                                       |         |
//   multiversion_fast_proj      new_multiversion_slow_proj |
//                                                |         |
//                                                +------+  |
//                                                       |  |
//                                                      region
//                                                         |
//                                                      slow_path
//
IfTrueNode* PhaseIdealLoop::create_new_if_for_multiversion(IfTrueNode* multiversioning_fast_proj) {
  // Give all nodes in the old sub-graph a name.
  IfNode* multiversion_if = multiversioning_fast_proj->in(0)->as_If();
  Node* entry = multiversion_if->in(0);
  OpaqueMultiversioningNode* opaque = multiversion_if->in(1)->as_OpaqueMultiversioning();
  IfFalseNode* multiversion_slow_proj = multiversion_if->proj_out(0)->as_IfFalse();
  Node* slow_path = multiversion_slow_proj->unique_ctrl_out();

  // The slow_loop may still be delayed, and waiting for runtime-checks to be added to the
  // multiversion_if. Now that we have at least one condition for the multiversioning,
  // we should resume optimizations for the slow loop.
  opaque->notify_slow_loop_that_it_can_resume_optimizations();

  // Create new_if with its projections.
  IfNode* new_if = IfNode::make_with_same_profile(multiversion_if, entry, opaque);
  IdealLoopTree* lp = get_loop(entry);
  register_control(new_if, lp, entry);

  IfTrueNode*  new_if_true  = new IfTrueNode(new_if);
  IfFalseNode* new_if_false = new IfFalseNode(new_if);
  register_control(new_if_true,  lp, new_if);
  register_control(new_if_false, lp, new_if);

  // Hook new_if_true into multiversion_if.
  _igvn.replace_input_of(multiversion_if, 0, new_if_true);

  // Clone multiversion_slow_path - this allows us to easily carry the dependencies to
  // the new region below.
  IfFalseNode* new_multiversion_slow_proj = multiversion_slow_proj->clone()->as_IfFalse();
  register_control(new_multiversion_slow_proj, lp, multiversion_if);

  // Create new Region.
  RegionNode* region = new RegionNode(1);
  region->add_req(new_multiversion_slow_proj);
  region->add_req(new_if_false);
  register_control(region, lp, new_multiversion_slow_proj);

  // Hook region into slow_path, in stead of the multiversion_slow_proj.
  // This also moves all other dependencies of the multiversion_slow_proj to the region.
  _igvn.replace_node(multiversion_slow_proj, region);

  return new_if_true;
}

OpaqueMultiversioningNode* find_multiversion_opaque_from_multiversion_if_false(Node* maybe_multiversion_if_false) {
  IfFalseNode* multiversion_if_false = maybe_multiversion_if_false->isa_IfFalse();
  if (multiversion_if_false == nullptr) { return nullptr; }
  IfNode* multiversion_if = multiversion_if_false->in(0)->isa_If();
  if (multiversion_if == nullptr) { return nullptr; }
  return multiversion_if->in(1)->isa_OpaqueMultiversioning();
}

bool PhaseIdealLoop::try_resume_optimizations_for_delayed_slow_loop(IdealLoopTree* lpt) {
  CountedLoopNode* cl = lpt->_head->as_CountedLoop();
  assert(cl->is_multiversion_delayed_slow_loop(), "must currently be delayed");

  // Find multiversion_if.
  Node* entry = cl->skip_strip_mined()->in(LoopNode::EntryControl);
  const Predicates predicates(entry);

  Node* slow_path = predicates.entry();

  // Find opaque.
  OpaqueMultiversioningNode* opaque = nullptr;
  if (slow_path->is_Region()) {
    for (uint i = 1; i < slow_path->req(); i++) {
      Node* n = slow_path->in(i);
      opaque = find_multiversion_opaque_from_multiversion_if_false(n);
      if (opaque != nullptr) { break; }
    }
  } else {
    opaque = find_multiversion_opaque_from_multiversion_if_false(slow_path);
  }
  assert(opaque != nullptr, "must have found multiversion opaque node");
  if (opaque == nullptr) { return false; }

  // We may still be delayed, if there were not yet any runtime-checks added
  // for the multiversioning. We may never add any, and then this loop would
  // fold away. So we wait until some runtime-checks are added, then we know
  // that this loop will be reachable and it is worth optimizing further.
  if (opaque->is_delayed_slow_loop()) { return false; }

  // Clear away the "delayed" status, i.e. resume optimizations.
  cl->set_no_multiversion();
  cl->set_multiversion_slow_loop();
#ifndef PRODUCT
  if (TraceLoopOpts) {
    tty->print("Resume Optimizations ");
    lpt->dump_head();
  }
#endif
  return true;
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
    IfNode* loop_selector = unswitched_loop_selector.loop_selector().selector();
    tty->print_cr("Loop Unswitching:");
    tty->print_cr("- Unswitch-Candidate-If: %d %s", unswitch_candidate->_idx, unswitch_candidate->Name());
    tty->print_cr("- Loop-Selector-If: %d %s", loop_selector->_idx, loop_selector->Name());
    tty->print_cr("- True-Path-Loop (=Orig): %d %s", original_head->_idx, original_head->Name());
    tty->print_cr("- False-Path-Loop (=Clone): %d %s", new_head->_idx, new_head->Name());
  }
}

void PhaseIdealLoop::trace_loop_multiversioning_result(const LoopSelector& loop_selector,
                                                       const LoopNode* original_head, const LoopNode* new_head) {
  if (TraceLoopMultiversioning) {
    IfNode* selector_if = loop_selector.selector();
    tty->print_cr("Loop Multiversioning:");
    tty->print_cr("- Loop-Selector-If: %d %s", selector_if->_idx, selector_if->Name());
    tty->print_cr("- True-Path-Loop (=Orig / Fast): %d %s", original_head->_idx, original_head->Name());
    tty->print_cr("- False-Path-Loop (=Clone / Slow): %d %s", new_head->_idx, new_head->Name());
  }
}
#endif

// When unswitching a counted loop, we need to convert it back to a normal loop since it's not a proper pre, main or,
// post loop anymore after loop unswitching. We also lose the multiversion structure, with access to the multiversion_if.
void PhaseIdealLoop::revert_to_normal_loop(const LoopNode* loop_head) {
  CountedLoopNode* cl = loop_head->isa_CountedLoop();
  if (cl == nullptr) { return; }
  if (!cl->is_normal_loop()) { cl->set_normal_loop(); }
  if (cl->is_multiversion()) { cl->set_no_multiversion(); }
}

// Hoist invariant CheckCastPPNodes out of each unswitched loop version to the appropriate loop selector If projection.
void PhaseIdealLoop::hoist_invariant_check_casts(const IdealLoopTree* loop, const Node_List& old_new,
                                                 const UnswitchedLoopSelector& unswitched_loop_selector) {
  IfNode* unswitch_candidate = unswitched_loop_selector.unswitch_candidate();
  IfNode* loop_selector = unswitched_loop_selector.loop_selector().selector();
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

