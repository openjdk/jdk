/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/loopnode.hpp"
#include "opto/addnode.hpp"
#include "opto/callnode.hpp"
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/loopnode.hpp"
#include "opto/mulnode.hpp"
#include "opto/opaquenode.hpp"
#include "opto/rootnode.hpp"
#include "opto/subnode.hpp"

/*
 * The general idea of Loop Predication is to insert a predicate on the entry
 * path to a loop, and raise a uncommon trap if the check of the condition fails.
 * The condition checks are promoted from inside the loop body, and thus
 * the checks inside the loop could be eliminated. Currently, loop predication
 * optimization has been applied to remove array range check and loop invariant
 * checks (such as null checks).
*/

//-------------------------------register_control-------------------------
void PhaseIdealLoop::register_control(Node* n, IdealLoopTree *loop, Node* pred) {
  assert(n->is_CFG(), "must be control node");
  _igvn.register_new_node_with_optimizer(n);
  loop->_body.push(n);
  set_loop(n, loop);
  // When called from beautify_loops() idom is not constructed yet.
  if (_idom != NULL) {
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
// The true projecttion (if_cont) of the new_iff is returned.
// This code is also used to clone predicates to cloned loops.
ProjNode* PhaseIdealLoop::create_new_if_for_predicate(ProjNode* cont_proj, Node* new_entry,
                                                      Deoptimization::DeoptReason reason,
                                                      int opcode) {
  assert(cont_proj->is_uncommon_trap_if_pattern(reason), "must be a uct if pattern!");
  IfNode* iff = cont_proj->in(0)->as_If();

  ProjNode *uncommon_proj = iff->proj_out(1 - cont_proj->_con);
  Node     *rgn   = uncommon_proj->unique_ctrl_out();
  assert(rgn->is_Region() || rgn->is_Call(), "must be a region or call uct");

  uint proj_index = 1; // region's edge corresponding to uncommon_proj
  if (!rgn->is_Region()) { // create a region to guard the call
    assert(rgn->is_Call(), "must be call uct");
    CallNode* call = rgn->as_Call();
    IdealLoopTree* loop = get_loop(call);
    rgn = new RegionNode(1);
    rgn->add_req(uncommon_proj);
    register_control(rgn, loop, uncommon_proj);
    _igvn.replace_input_of(call, 0, rgn);
    // When called from beautify_loops() idom is not constructed yet.
    if (_idom != NULL) {
      set_idom(call, rgn, dom_depth(rgn));
    }
    for (DUIterator_Fast imax, i = uncommon_proj->fast_outs(imax); i < imax; i++) {
      Node* n = uncommon_proj->fast_out(i);
      if (n->is_Load() || n->is_Store()) {
        _igvn.replace_input_of(n, 0, rgn);
        --i; --imax;
      }
    }
  } else {
    // Find region's edge corresponding to uncommon_proj
    for (; proj_index < rgn->req(); proj_index++)
      if (rgn->in(proj_index) == uncommon_proj) break;
    assert(proj_index < rgn->req(), "sanity");
  }

  Node* entry = iff->in(0);
  if (new_entry != NULL) {
    // Clonning the predicate to new location.
    entry = new_entry;
  }
  // Create new_iff
  IdealLoopTree* lp = get_loop(entry);
  IfNode* new_iff = NULL;
  if (opcode == Op_If) {
    new_iff = new IfNode(entry, iff->in(1), iff->_prob, iff->_fcnt);
  } else {
    assert(opcode == Op_RangeCheck, "no other if variant here");
    new_iff = new RangeCheckNode(entry, iff->in(1), iff->_prob, iff->_fcnt);
  }
  register_control(new_iff, lp, entry);
  Node *if_cont = new IfTrueNode(new_iff);
  Node *if_uct  = new IfFalseNode(new_iff);
  if (cont_proj->is_IfFalse()) {
    // Swap
    Node* tmp = if_uct; if_uct = if_cont; if_cont = tmp;
  }
  register_control(if_cont, lp, new_iff);
  register_control(if_uct, get_loop(rgn), new_iff);

  // if_uct to rgn
  _igvn.hash_delete(rgn);
  rgn->add_req(if_uct);
  // When called from beautify_loops() idom is not constructed yet.
  if (_idom != NULL) {
    Node* ridom = idom(rgn);
    Node* nrdom = dom_lca(ridom, new_iff);
    set_idom(rgn, nrdom, dom_depth(rgn));
  }

  // If rgn has phis add new edges which has the same
  // value as on original uncommon_proj pass.
  assert(rgn->in(rgn->req() -1) == if_uct, "new edge should be last");
  bool has_phi = false;
  for (DUIterator_Fast imax, i = rgn->fast_outs(imax); i < imax; i++) {
    Node* use = rgn->fast_out(i);
    if (use->is_Phi() && use->outcnt() > 0) {
      assert(use->in(0) == rgn, "");
      _igvn.rehash_node_delayed(use);
      use->add_req(use->in(proj_index));
      has_phi = true;
    }
  }
  assert(!has_phi || rgn->req() > 3, "no phis when region is created");

  if (new_entry == NULL) {
    // Attach if_cont to iff
    _igvn.replace_input_of(iff, 0, if_cont);
    if (_idom != NULL) {
      set_idom(iff, if_cont, dom_depth(iff));
    }
  }
  return if_cont->as_Proj();
}

//------------------------------create_new_if_for_predicate------------------------
// Create a new if below new_entry for the predicate to be cloned (IGVN optimization)
ProjNode* PhaseIterGVN::create_new_if_for_predicate(ProjNode* cont_proj, Node* new_entry,
                                                    Deoptimization::DeoptReason reason,
                                                    int opcode) {
  assert(new_entry != 0, "only used for clone predicate");
  assert(cont_proj->is_uncommon_trap_if_pattern(reason), "must be a uct if pattern!");
  IfNode* iff = cont_proj->in(0)->as_If();

  ProjNode *uncommon_proj = iff->proj_out(1 - cont_proj->_con);
  Node     *rgn   = uncommon_proj->unique_ctrl_out();
  assert(rgn->is_Region() || rgn->is_Call(), "must be a region or call uct");

  uint proj_index = 1; // region's edge corresponding to uncommon_proj
  if (!rgn->is_Region()) { // create a region to guard the call
    assert(rgn->is_Call(), "must be call uct");
    CallNode* call = rgn->as_Call();
    rgn = new RegionNode(1);
    register_new_node_with_optimizer(rgn);
    rgn->add_req(uncommon_proj);
    replace_input_of(call, 0, rgn);
  } else {
    // Find region's edge corresponding to uncommon_proj
    for (; proj_index < rgn->req(); proj_index++)
      if (rgn->in(proj_index) == uncommon_proj) break;
    assert(proj_index < rgn->req(), "sanity");
  }

  // Create new_iff in new location.
  IfNode* new_iff = NULL;
  if (opcode == Op_If) {
    new_iff = new IfNode(new_entry, iff->in(1), iff->_prob, iff->_fcnt);
  } else {
    assert(opcode == Op_RangeCheck, "no other if variant here");
    new_iff = new RangeCheckNode(new_entry, iff->in(1), iff->_prob, iff->_fcnt);
  }

  register_new_node_with_optimizer(new_iff);
  Node *if_cont = new IfTrueNode(new_iff);
  Node *if_uct  = new IfFalseNode(new_iff);
  if (cont_proj->is_IfFalse()) {
    // Swap
    Node* tmp = if_uct; if_uct = if_cont; if_cont = tmp;
  }
  register_new_node_with_optimizer(if_cont);
  register_new_node_with_optimizer(if_uct);

  // if_uct to rgn
  hash_delete(rgn);
  rgn->add_req(if_uct);

  // If rgn has phis add corresponding new edges which has the same
  // value as on original uncommon_proj pass.
  assert(rgn->in(rgn->req() -1) == if_uct, "new edge should be last");
  bool has_phi = false;
  for (DUIterator_Fast imax, i = rgn->fast_outs(imax); i < imax; i++) {
    Node* use = rgn->fast_out(i);
    if (use->is_Phi() && use->outcnt() > 0) {
      rehash_node_delayed(use);
      use->add_req(use->in(proj_index));
      has_phi = true;
    }
  }
  assert(!has_phi || rgn->req() > 3, "no phis when region is created");

  return if_cont->as_Proj();
}

//--------------------------clone_predicate-----------------------
ProjNode* PhaseIdealLoop::clone_predicate(ProjNode* predicate_proj, Node* new_entry,
                                          Deoptimization::DeoptReason reason,
                                          PhaseIdealLoop* loop_phase,
                                          PhaseIterGVN* igvn) {
  ProjNode* new_predicate_proj;
  if (loop_phase != NULL) {
    new_predicate_proj = loop_phase->create_new_if_for_predicate(predicate_proj, new_entry, reason, Op_If);
  } else {
    new_predicate_proj =       igvn->create_new_if_for_predicate(predicate_proj, new_entry, reason, Op_If);
  }
  IfNode* iff = new_predicate_proj->in(0)->as_If();
  Node* ctrl  = iff->in(0);

  // Match original condition since predicate's projections could be swapped.
  assert(predicate_proj->in(0)->in(1)->in(1)->Opcode()==Op_Opaque1, "must be");
  Node* opq = new Opaque1Node(igvn->C, predicate_proj->in(0)->in(1)->in(1)->in(1));
  igvn->C->add_predicate_opaq(opq);

  Node* bol = new Conv2BNode(opq);
  if (loop_phase != NULL) {
    loop_phase->register_new_node(opq, ctrl);
    loop_phase->register_new_node(bol, ctrl);
  } else {
    igvn->register_new_node_with_optimizer(opq);
    igvn->register_new_node_with_optimizer(bol);
  }
  igvn->hash_delete(iff);
  iff->set_req(1, bol);
  return new_predicate_proj;
}


//--------------------------clone_loop_predicates-----------------------
// Interface from IGVN
Node* PhaseIterGVN::clone_loop_predicates(Node* old_entry, Node* new_entry, bool clone_limit_check) {
  return PhaseIdealLoop::clone_loop_predicates(old_entry, new_entry, clone_limit_check, NULL, this);
}

// Interface from PhaseIdealLoop
Node* PhaseIdealLoop::clone_loop_predicates(Node* old_entry, Node* new_entry, bool clone_limit_check) {
  return clone_loop_predicates(old_entry, new_entry, clone_limit_check, this, &this->_igvn);
}

// Clone loop predicates to cloned loops (peeled, unswitched, split_if).
Node* PhaseIdealLoop::clone_loop_predicates(Node* old_entry, Node* new_entry,
                                                bool clone_limit_check,
                                                PhaseIdealLoop* loop_phase,
                                                PhaseIterGVN* igvn) {
#ifdef ASSERT
  if (new_entry == NULL || !(new_entry->is_Proj() || new_entry->is_Region() || new_entry->is_SafePoint())) {
    if (new_entry != NULL)
      new_entry->dump();
    assert(false, "not IfTrue, IfFalse, Region or SafePoint");
  }
#endif
  // Search original predicates
  Node* entry = old_entry;
  ProjNode* limit_check_proj = NULL;
  if (LoopLimitCheck) {
    limit_check_proj = find_predicate_insertion_point(entry, Deoptimization::Reason_loop_limit_check);
    if (limit_check_proj != NULL) {
      entry = entry->in(0)->in(0);
    }
  }
  if (UseLoopPredicate) {
    ProjNode* predicate_proj = find_predicate_insertion_point(entry, Deoptimization::Reason_predicate);
    if (predicate_proj != NULL) { // right pattern that can be used by loop predication
      // clone predicate
      new_entry = clone_predicate(predicate_proj, new_entry,
                                  Deoptimization::Reason_predicate,
                                  loop_phase, igvn);
      assert(new_entry != NULL && new_entry->is_Proj(), "IfTrue or IfFalse after clone predicate");
      if (TraceLoopPredicate) {
        tty->print("Loop Predicate cloned: ");
        debug_only( new_entry->in(0)->dump(); )
      }
    }
  }
  if (limit_check_proj != NULL && clone_limit_check) {
    // Clone loop limit check last to insert it before loop.
    // Don't clone a limit check which was already finalized
    // for this counted loop (only one limit check is needed).
    new_entry = clone_predicate(limit_check_proj, new_entry,
                                Deoptimization::Reason_loop_limit_check,
                                loop_phase, igvn);
    assert(new_entry != NULL && new_entry->is_Proj(), "IfTrue or IfFalse after clone limit check");
    if (TraceLoopLimitCheck) {
      tty->print("Loop Limit Check cloned: ");
      debug_only( new_entry->in(0)->dump(); )
    }
  }
  return new_entry;
}

//--------------------------skip_loop_predicates------------------------------
// Skip related predicates.
Node* PhaseIdealLoop::skip_loop_predicates(Node* entry) {
  Node* predicate = NULL;
  if (LoopLimitCheck) {
    predicate = find_predicate_insertion_point(entry, Deoptimization::Reason_loop_limit_check);
    if (predicate != NULL) {
      entry = entry->in(0)->in(0);
    }
  }
  if (UseLoopPredicate) {
    predicate = find_predicate_insertion_point(entry, Deoptimization::Reason_predicate);
    if (predicate != NULL) { // right pattern that can be used by loop predication
      IfNode* iff = entry->in(0)->as_If();
      ProjNode* uncommon_proj = iff->proj_out(1 - entry->as_Proj()->_con);
      Node* rgn = uncommon_proj->unique_ctrl_out();
      assert(rgn->is_Region() || rgn->is_Call(), "must be a region or call uct");
      entry = entry->in(0)->in(0);
      while (entry != NULL && entry->is_Proj() && entry->in(0)->is_If()) {
        uncommon_proj = entry->in(0)->as_If()->proj_out(1 - entry->as_Proj()->_con);
        if (uncommon_proj->unique_ctrl_out() != rgn)
          break;
        entry = entry->in(0)->in(0);
      }
    }
  }
  return entry;
}

//--------------------------find_predicate_insertion_point-------------------
// Find a good location to insert a predicate
ProjNode* PhaseIdealLoop::find_predicate_insertion_point(Node* start_c, Deoptimization::DeoptReason reason) {
  if (start_c == NULL || !start_c->is_Proj())
    return NULL;
  if (start_c->as_Proj()->is_uncommon_trap_if_pattern(reason)) {
    return start_c->as_Proj();
  }
  return NULL;
}

//--------------------------find_predicate------------------------------------
// Find a predicate
Node* PhaseIdealLoop::find_predicate(Node* entry) {
  Node* predicate = NULL;
  if (LoopLimitCheck) {
    predicate = find_predicate_insertion_point(entry, Deoptimization::Reason_loop_limit_check);
    if (predicate != NULL) { // right pattern that can be used by loop predication
      return entry;
    }
  }
  if (UseLoopPredicate) {
    predicate = find_predicate_insertion_point(entry, Deoptimization::Reason_predicate);
    if (predicate != NULL) { // right pattern that can be used by loop predication
      return entry;
    }
  }
  return NULL;
}

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
        _stack.push(n, n->in(0) == NULL ? 1 : 0);
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
          if (in == NULL) continue;
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
          if (n->is_CFG() || n->depends_only_on_test() || n->in(0) == NULL || !_phase->is_member(_lpt, n->in(0))) {
            _invariant.set(n->_idx); // I am a invariant too
          }
        }
      } else { // process next input
        _stack.set_index(idx + 1);
        Node* m = n->in(idx);
        if (m != NULL && !_visited.test_set(m->_idx)) {
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
      _stack.push(n, n->in(0) == NULL ? 1 : 0);
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
          if (in == NULL) continue;
          n_cl->set_req(i, _old_new[in->_idx]);
        }
      } else { // process next input
        _stack.set_index(idx + 1);
        Node* m = n->in(idx);
        if (m != NULL && !_clone_visited.test_set(m->_idx)) {
          clone_visit(m); // visit the input
        }
      }
    }
  }

 public:
  Invariance(Arena* area, IdealLoopTree* lpt) :
    _lpt(lpt), _phase(lpt->_phase),
    _visited(area), _invariant(area), _stack(area, 10 /* guess */),
    _clone_visited(area), _old_new(area)
  {}

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
bool IdealLoopTree::is_range_check_if(IfNode *iff, PhaseIdealLoop *phase, Invariance& invar) const {
  if (!is_loop_exit(iff)) {
    return false;
  }
  if (!iff->in(1)->is_Bool()) {
    return false;
  }
  const BoolNode *bol = iff->in(1)->as_Bool();
  if (bol->_test._test != BoolTest::lt) {
    return false;
  }
  if (!bol->in(1)->is_Cmp()) {
    return false;
  }
  const CmpNode *cmp = bol->in(1)->as_Cmp();
  if (cmp->Opcode() != Op_CmpU) {
    return false;
  }
  Node* range = cmp->in(2);
  if (range->Opcode() != Op_LoadRange) {
    const TypeInt* tint = phase->_igvn.type(range)->isa_int();
    if (tint == NULL || tint->empty() || tint->_lo < 0) {
      // Allow predication on positive values that aren't LoadRanges.
      // This allows optimization of loops where the length of the
      // array is a known value and doesn't need to be loaded back
      // from the array.
      return false;
    }
  }
  if (!invar.is_invariant(range)) {
    return false;
  }
  Node *iv     = _head->as_CountedLoop()->phi();
  int   scale  = 0;
  Node *offset = NULL;
  if (!phase->is_scaled_iv_plus_offset(cmp->in(1), iv, &scale, &offset)) {
    return false;
  }
  if (offset && !invar.is_invariant(offset)) { // offset must be invariant
    return false;
  }
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
BoolNode* PhaseIdealLoop::rc_predicate(IdealLoopTree *loop, Node* ctrl,
                                       int scale, Node* offset,
                                       Node* init, Node* limit, Node* stride,
                                       Node* range, bool upper) {
  stringStream* predString = NULL;
  if (TraceLoopPredicate) {
    predString = new stringStream();
    predString->print("rc_predicate ");
  }

  Node* max_idx_expr  = init;
  int stride_con = stride->get_int();
  if ((stride_con > 0) == (scale > 0) == upper) {
    if (LoopLimitCheck) {
      // With LoopLimitCheck limit is not exact.
      // Calculate exact limit here.
      // Note, counted loop's test is '<' or '>'.
      limit = exact_limit(loop);
      max_idx_expr = new SubINode(limit, stride);
      register_new_node(max_idx_expr, ctrl);
      if (TraceLoopPredicate) predString->print("(limit - stride) ");
    } else {
      max_idx_expr = new SubINode(limit, stride);
      register_new_node(max_idx_expr, ctrl);
      if (TraceLoopPredicate) predString->print("(limit - stride) ");
    }
  } else {
    if (TraceLoopPredicate) predString->print("init ");
  }

  if (scale != 1) {
    ConNode* con_scale = _igvn.intcon(scale);
    max_idx_expr = new MulINode(max_idx_expr, con_scale);
    register_new_node(max_idx_expr, ctrl);
    if (TraceLoopPredicate) predString->print("* %d ", scale);
  }

  if (offset && (!offset->is_Con() || offset->get_int() != 0)){
    max_idx_expr = new AddINode(max_idx_expr, offset);
    register_new_node(max_idx_expr, ctrl);
    if (TraceLoopPredicate)
      if (offset->is_Con()) predString->print("+ %d ", offset->get_int());
      else predString->print("+ offset ");
  }

  CmpUNode* cmp = new CmpUNode(max_idx_expr, range);
  register_new_node(cmp, ctrl);
  BoolNode* bol = new BoolNode(cmp, BoolTest::lt);
  register_new_node(bol, ctrl);

  if (TraceLoopPredicate) {
    predString->print_cr("<u range");
    tty->print("%s", predString->as_string());
  }
  return bol;
}

//------------------------------ loop_predication_impl--------------------------
// Insert loop predicates for null checks and range checks
bool PhaseIdealLoop::loop_predication_impl(IdealLoopTree *loop) {
  if (!UseLoopPredicate) return false;

  if (!loop->_head->is_Loop()) {
    // Could be a simple region when irreducible loops are present.
    return false;
  }
  LoopNode* head = loop->_head->as_Loop();

  if (head->unique_ctrl_out()->Opcode() == Op_NeverBranch) {
    // do nothing for infinite loops
    return false;
  }

  CountedLoopNode *cl = NULL;
  if (head->is_valid_counted_loop()) {
    cl = head->as_CountedLoop();
    // do nothing for iteration-splitted loops
    if (!cl->is_normal_loop()) return false;
    // Avoid RCE if Counted loop's test is '!='.
    BoolTest::mask bt = cl->loopexit()->test_trip();
    if (bt != BoolTest::lt && bt != BoolTest::gt)
      cl = NULL;
  }

  Node* entry = head->in(LoopNode::EntryControl);
  ProjNode *predicate_proj = NULL;
  // Loop limit check predicate should be near the loop.
  if (LoopLimitCheck) {
    predicate_proj = find_predicate_insertion_point(entry, Deoptimization::Reason_loop_limit_check);
    if (predicate_proj != NULL)
      entry = predicate_proj->in(0)->in(0);
  }

  predicate_proj = find_predicate_insertion_point(entry, Deoptimization::Reason_predicate);
  if (!predicate_proj) {
#ifndef PRODUCT
    if (TraceLoopPredicate) {
      tty->print("missing predicate:");
      loop->dump_head();
      head->dump(1);
    }
#endif
    return false;
  }
  ConNode* zero = _igvn.intcon(0);
  set_ctrl(zero, C->root());

  ResourceArea *area = Thread::current()->resource_area();
  Invariance invar(area, loop);

  // Create list of if-projs such that a newer proj dominates all older
  // projs in the list, and they all dominate loop->tail()
  Node_List if_proj_list(area);
  Node *current_proj = loop->tail(); //start from tail
  while (current_proj != head) {
    if (loop == get_loop(current_proj) && // still in the loop ?
        current_proj->is_Proj()        && // is a projection  ?
        (current_proj->in(0)->Opcode() == Op_If ||
         current_proj->in(0)->Opcode() == Op_RangeCheck)) { // is a if projection ?
      if_proj_list.push(current_proj);
    }
    current_proj = idom(current_proj);
  }

  bool hoisted = false; // true if at least one proj is promoted
  while (if_proj_list.size() > 0) {
    // Following are changed to nonnull when a predicate can be hoisted
    ProjNode* new_predicate_proj = NULL;

    ProjNode* proj = if_proj_list.pop()->as_Proj();
    IfNode*   iff  = proj->in(0)->as_If();

    if (!proj->is_uncommon_trap_if_pattern(Deoptimization::Reason_none)) {
      if (loop->is_loop_exit(iff)) {
        // stop processing the remaining projs in the list because the execution of them
        // depends on the condition of "iff" (iff->in(1)).
        break;
      } else {
        // Both arms are inside the loop. There are two cases:
        // (1) there is one backward branch. In this case, any remaining proj
        //     in the if_proj list post-dominates "iff". So, the condition of "iff"
        //     does not determine the execution the remining projs directly, and we
        //     can safely continue.
        // (2) both arms are forwarded, i.e. a diamond shape. In this case, "proj"
        //     does not dominate loop->tail(), so it can not be in the if_proj list.
        continue;
      }
    }

    Node*     test = iff->in(1);
    if (!test->is_Bool()){ //Conv2B, ...
      continue;
    }
    BoolNode* bol = test->as_Bool();
    if (invar.is_invariant(bol)) {
      // Invariant test
      new_predicate_proj = create_new_if_for_predicate(predicate_proj, NULL,
                                                       Deoptimization::Reason_predicate,
                                                       iff->Opcode());
      Node* ctrl = new_predicate_proj->in(0)->as_If()->in(0);
      BoolNode* new_predicate_bol = invar.clone(bol, ctrl)->as_Bool();

      // Negate test if necessary
      bool negated = false;
      if (proj->_con != predicate_proj->_con) {
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
    } else if (cl != NULL && loop->is_range_check_if(iff, this, invar)) {
      // Range check for counted loops
      const Node*    cmp    = bol->in(1)->as_Cmp();
      Node*          idx    = cmp->in(1);
      assert(!invar.is_invariant(idx), "index is variant");
      Node* rng = cmp->in(2);
      assert(rng->Opcode() == Op_LoadRange || _igvn.type(rng)->is_int() >= 0, "must be");
      assert(invar.is_invariant(rng), "range must be invariant");
      int scale    = 1;
      Node* offset = zero;
      bool ok = is_scaled_iv_plus_offset(idx, cl->phi(), &scale, &offset);
      assert(ok, "must be index expression");

      Node* init    = cl->init_trip();
      Node* limit   = cl->limit();
      Node* stride  = cl->stride();

      // Build if's for the upper and lower bound tests.  The
      // lower_bound test will dominate the upper bound test and all
      // cloned or created nodes will use the lower bound test as
      // their declared control.
      ProjNode* lower_bound_proj = create_new_if_for_predicate(predicate_proj, NULL, Deoptimization::Reason_predicate, iff->Opcode());
      ProjNode* upper_bound_proj = create_new_if_for_predicate(predicate_proj, NULL, Deoptimization::Reason_predicate, iff->Opcode());
      assert(upper_bound_proj->in(0)->as_If()->in(0) == lower_bound_proj, "should dominate");
      Node *ctrl = lower_bound_proj->in(0)->as_If()->in(0);

      // Perform cloning to keep Invariance state correct since the
      // late schedule will place invariant things in the loop.
      rng = invar.clone(rng, ctrl);
      if (offset && offset != zero) {
        assert(invar.is_invariant(offset), "offset must be loop invariant");
        offset = invar.clone(offset, ctrl);
      }

      // Test the lower bound
      BoolNode*  lower_bound_bol = rc_predicate(loop, ctrl, scale, offset, init, limit, stride, rng, false);
      // Negate test if necessary
      bool negated = false;
      if (proj->_con != predicate_proj->_con) {
        lower_bound_bol = new BoolNode(lower_bound_bol->in(1), lower_bound_bol->_test.negate());
        register_new_node(lower_bound_bol, ctrl);
        negated = true;
      }
      IfNode* lower_bound_iff = lower_bound_proj->in(0)->as_If();
      _igvn.hash_delete(lower_bound_iff);
      lower_bound_iff->set_req(1, lower_bound_bol);
      if (TraceLoopPredicate) tty->print_cr("lower bound check if: %s %d ", negated ? " negated" : "", lower_bound_iff->_idx);

      // Test the upper bound
      BoolNode* upper_bound_bol = rc_predicate(loop, lower_bound_proj, scale, offset, init, limit, stride, rng, true);
      negated = false;
      if (proj->_con != predicate_proj->_con) {
        upper_bound_bol = new BoolNode(upper_bound_bol->in(1), upper_bound_bol->_test.negate());
        register_new_node(upper_bound_bol, ctrl);
        negated = true;
      }
      IfNode* upper_bound_iff = upper_bound_proj->in(0)->as_If();
      _igvn.hash_delete(upper_bound_iff);
      upper_bound_iff->set_req(1, upper_bound_bol);
      if (TraceLoopPredicate) tty->print_cr("upper bound check if: %s %d ", negated ? " negated" : "", lower_bound_iff->_idx);

      // Fall through into rest of the clean up code which will move
      // any dependent nodes onto the upper bound test.
      new_predicate_proj = upper_bound_proj;

#ifndef PRODUCT
      if (TraceLoopOpts && !TraceLoopPredicate) {
        tty->print("Predicate RC ");
        loop->dump_head();
      }
#endif
    } else {
      // Loop variant check (for example, range check in non-counted loop)
      // with uncommon trap.
      continue;
    }
    assert(new_predicate_proj != NULL, "sanity");
    // Success - attach condition (new_predicate_bol) to predicate if
    invar.map_ctrl(proj, new_predicate_proj); // so that invariance test can be appropriate

    // Eliminate the old If in the loop body
    dominated_by( new_predicate_proj, iff, proj->_con != new_predicate_proj->_con );

    hoisted = true;
    C->set_major_progress();
  } // end while

#ifndef PRODUCT
  // report that the loop predication has been actually performed
  // for this loop
  if (TraceLoopPredicate && hoisted) {
    tty->print("Loop Predication Performed:");
    loop->dump_head();
  }
#endif

  return hoisted;
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
  if (!_irreducible && !tail()->is_top()) {
    hoisted |= phase->loop_predication_impl(this);
  }

  if (_next) { //sibling
    hoisted |= _next->loop_predication( phase);
  }

  return hoisted;
}
