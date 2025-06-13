/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "opto/c2_MacroAssembler.hpp"
#include "opto/callnode.hpp"
#include "opto/compile.hpp"
#include "opto/loopnode.hpp"
#include "opto/phaseX.hpp"
#include "opto/reachability.hpp"
#include "opto/regalloc.hpp"
#include "opto/runtime.hpp"

/*
 * Reference::reachabilityFence support.
 *
 * Reachability fences (RFs) are intended to be used in performance critical code,
 * so the primary goal for C2 support is to reduce their runtime overhead as much as possible.
 *
 * Reference::reachabilityFence() calls are intrinsified into ReachabilityFence CFG nodes
 * and transition through multiple phases:
 *   (0) initial set of RFs is materialized during parsing;
 *   (1) optimization pass during loop opts which eliminates redundant nodes and
 *     moves loop-invariant ones outside loops;
 *   (2) reachability information is transferred to safepoint nodes (appended as edges after debug info);
 *   (3) reachability information from safepoints materialized as RF nodes attached to the safepoint node.
 *
 * Some implementation considerations.
 *
 * It looks attractive to get rid of RF nodes early and transfer to safepoint-attached representation,
 * but it is not correct until loop opts are done.
 *
 * RF nodes may interfere with RA, so stand-alone RF nodes are eliminated and reachability information is
 * transferred to corresponding safepoints. When safepoints are pruned during macro expansion, corresponding
 * reachability info also goes away.
 *
 * Unfortunately, it's not straightforward to stay with safepoint-attached representation till the very end,
 * because information about derived oops is attached to safepoints in a similar way. So, for now RFs are
 * rematerialized at safepoints before RA.
 */

// RF is redundant for some referent oop when the referent has another user which keeps it alive across the RF.
// In terms of dominance relation it can be formulated as "a referent has a user which is dominated by the redundant RF".
// Until loop opts are over, only RF nodes are considered as usages (controlled by rf_only flag).
static bool is_redundant_rf_helper(Node* ctrl, Node* referent, PhaseIdealLoop* phase, PhaseGVN& gvn, bool rf_only) {
  const Type* t = gvn.type(referent);
  if (!PreserveReachabilityFencesOnConstants && t->singleton()) {
    return true; // no-op fence
  }
  if (t == TypePtr::NULL_PTR) {
    return true; // no-op fence
  }
  if (referent->is_Proj() && referent->in(0)->is_CallJava()) {
    ciMethod* m = referent->in(0)->as_CallJava()->method();
    if (m != nullptr && m->is_boxing_method()) {
      return true;
    }
  }
  for (Node* cur = referent;
       cur != nullptr;
       cur = (cur->is_ConstraintCast() ? cur->in(1) : nullptr)) {
    for (DUIterator_Fast imax, i = cur->fast_outs(imax); i < imax; i++) {
      Node* use = cur->fast_out(i);
      if (rf_only && !use->is_ReachabilityFence()) {
        continue; // skip non-RF uses
      }
      if (use != ctrl) {
        if (phase != nullptr) {
          Node* use_ctrl = (rf_only ? use : phase->ctrl_or_self(use));
          if (phase->is_dominator(ctrl, use_ctrl)) {
            return true;
          }
        } else {
          assert(rf_only, "");
          if (gvn.is_dominator(ctrl, use)) {
            return true;
          }
        }
      }
    }
  }
  return false;
}

Node* ReachabilityFenceNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  return (remove_dead_region(phase, can_reshape) ? this : nullptr);
}

Node* ReachabilityFenceNode::Identity(PhaseGVN* phase) {
  if (is_redundant_rf_helper(this, in(1), nullptr, *phase, true /*rf_only*/)) {
    return in(0);
  }
  return this;
}

#ifndef PRODUCT
static void rf_desc(outputStream* st, const ReachabilityFenceNode* rf, PhaseRegAlloc* ra) {
  char buf[50];
  ra->dump_register(rf->in(1), buf, sizeof(buf));
  st->print("reachability fence [%s]", buf);
}

void ReachabilityFenceNode::format(PhaseRegAlloc* ra, outputStream* st) const {
  rf_desc(st, this, ra);
}

void ReachabilityFenceNode::emit(C2_MacroAssembler* masm, PhaseRegAlloc* ra) const {
  ResourceMark rm;
  stringStream ss;
  rf_desc(&ss, this, ra);
  const char* desc = masm->code_string(ss.freeze());
  masm->block_comment(desc);
}
#endif

// Detect safepoint nodes which are important for reachability tracking purposes.
static bool is_significant_sfpt(Node* n) {
  if (n->is_SafePoint()) {
    SafePointNode* sfpt = n->as_SafePoint();
    if (!sfpt->guaranteed_safepoint()) {
      return false; // not a real safepoint
    } else if (sfpt->is_CallStaticJava() && sfpt->as_CallStaticJava()->is_uncommon_trap()) {
      return false; // uncommon traps are exit points
    }
    return true;
  }
  return false;
}

void PhaseIdealLoop::insert_rf(Node* ctrl, Node* referent) {
  IdealLoopTree* lpt = get_loop(ctrl);
  Node* ctrl_end = ctrl->unique_ctrl_out();

  Node* new_rf = new ReachabilityFenceNode(C, ctrl, referent);

  register_control(new_rf, lpt, ctrl);
  set_idom(new_rf, ctrl, dom_depth(ctrl) + 1);
  if (lpt->_rfs == nullptr) {
    lpt->_rfs = new Node_List();
  }
  lpt->_rfs->push(new_rf);

  igvn().rehash_node_delayed(ctrl_end);
  ctrl_end->replace_edge(ctrl, new_rf);

  if (idom(ctrl_end) == ctrl) {
    set_idom(ctrl_end, new_rf, dom_depth(new_rf) + 1);
  } else {
    assert(ctrl_end->is_Region(), "");
  }
}

void PhaseIdealLoop::replace_rf(Node* old_node, Node* new_node) {
  assert(old_node->is_ReachabilityFence() ||
         (old_node->is_Proj() && old_node->in(0)->is_ReachabilityFence()),
         "%s", NodeClassNames[old_node->Opcode()]);

  IdealLoopTree* lpt = get_loop(old_node);
  if (!lpt->is_root()) {
    lpt->_body.yank(old_node);
  }
  assert(lpt->_rfs != nullptr, "missing");
  assert(lpt->_rfs->contains(old_node), "missing");
  lpt->_rfs->yank(old_node);
  lazy_replace(old_node, new_node);
}

void PhaseIdealLoop::remove_rf(Node* rf) {
  assert(rf->is_ReachabilityFence(), "");
  Node* referent = rf->in(1);
  if (igvn().type(referent) != TypePtr::NULL_PTR) {
    igvn().replace_input_of(rf, 1, makecon(TypePtr::NULL_PTR));
    if (referent->outcnt() == 0) {
      remove_dead_node(referent);
    }
  }
  Node* rf_ctrl_in = rf->in(0);
  replace_rf(rf, rf_ctrl_in);
}

bool PhaseIdealLoop::is_redundant_rf(Node* rf, bool rf_only) {
  assert(rf->is_ReachabilityFence(), "");
  Node* referent = rf->in(1);
  return is_redundant_rf_helper(rf, referent, this, igvn(), rf_only);
}

// Updates the unique list of redundant RFs.
// Returns true if new instances of redundant fences are found.
bool PhaseIdealLoop::find_redundant_rfs(Unique_Node_List& redundant_rfs) {
  bool found = false;
  for (int i = 0; i < C->reachability_fences_count(); i++) {
    Node* rf = C->reachability_fence(i);
    Node* referent = rf->in(1);
    assert(rf->outcnt() > 0, "dead node");
    if (!redundant_rfs.member(rf) && is_redundant_rf(rf, true /*rf_only*/)) {
      redundant_rfs.push(rf);
      found = true;
    }
  }
  return found;
}

#ifdef ASSERT
static void dump_rfs_on(outputStream* st, PhaseIdealLoop* phase, Unique_Node_List& redundant_rfs, bool rf_only) {
  for (int i = 0; i < phase->C->reachability_fences_count(); i++) {
    Node* rf = phase->C->reachability_fence(i);
    Node* referent = rf->in(1);
    bool detected = redundant_rfs.member(rf);
    bool redundant = is_redundant_rf_helper(rf, referent, phase, phase->igvn(), rf_only);

    st->print(" %3d: %s%s ", i, (redundant ? "R" : " "), (detected ? "D" : " "));
    rf->dump("", false, st);
    st->cr();

    st->print("         ");
    referent->dump("", false, st);
    st->cr();
    if (redundant != detected) {
      for (Node* cur = referent;
           cur != nullptr;
           cur = (cur->is_ConstraintCast() ? cur->in(1) : nullptr)) {
        bool first = true;
        for (DUIterator_Fast imax, i = cur->fast_outs(imax); i < imax; i++) {
          Node* use = cur->fast_out(i);
          if (rf_only && !use->is_ReachabilityFence()) {
            continue; // skip non-RF uses
          }
          if (use != rf) {
            Node* use_ctrl = (rf_only ? use : phase->ctrl_or_self(use));
            if (phase->is_dominator(rf, use_ctrl)) {
              if (first) {
                st->print("=====REF "); cur->dump("", false, st); st->cr();
                first = false;
              }
              st->print("     D "); use_ctrl->dump("", false, st); st->cr();
              if (use != use_ctrl) {
                st->print("         "); use->dump("", false, st); st->cr();
              }
            }
          }
        }
      }
    }
  }
}

bool PhaseIdealLoop::has_redundant_rfs(Unique_Node_List& ignored_rfs, bool rf_only) {
  for (int i = 0; i < C->reachability_fences_count(); i++) {
    Node* rf = C->reachability_fence(i);
    Node* referent = rf->in(1);
    assert(rf->outcnt() > 0, "dead node");
    if (ignored_rfs.member(rf)) {
      continue; // skip
    } else if (is_redundant_rf(rf, rf_only)) {
      dump_rfs_on(tty, this, ignored_rfs, rf_only);
      return true;
    }
  }
  return false;
}
#endif // ASSERT


//======================================================================
//---------------------------- Phase 1 ---------------------------------
// Optimization pass over reachability fences during loop opts.
// Eliminate redundant RFs and move RFs with loop-invariant referent out of the loop.
bool PhaseIdealLoop::optimize_reachability_fences() {
  Compile::TracePhase tp(_t_reachability);

  if (!OptimizeReachabilityFences) {
    return false;
  }

  Unique_Node_List redundant_rfs;
  find_redundant_rfs(redundant_rfs);

  Node_List worklist;
  for (int i = 0; i < C->reachability_fences_count(); i++) {
    Node* rf = C->reachability_fence(i);
    if (!redundant_rfs.member(rf)) {
      // Move RFs out of counted loops when possible.
      IdealLoopTree* lpt = get_loop(rf);
      Node* referent = rf->in(1);
      Node* loop_exit = lpt->unique_loop_exit_or_null();
      if (lpt->is_invariant(referent) && loop_exit != nullptr) {
        // Switch to the outermost loop.
        for (IdealLoopTree* outer_loop = lpt->_parent;
             outer_loop->is_invariant(referent) && outer_loop->unique_loop_exit_or_null() != nullptr;
             outer_loop = outer_loop->_parent) {
          assert(is_member(outer_loop, rf), "");
          loop_exit = outer_loop->unique_loop_exit_or_null();
        }
        assert(loop_exit != nullptr, "");
        worklist.push(referent);
        worklist.push(loop_exit);
        redundant_rfs.push(rf);
      }
    }
  }

  // Populate RFs outside counted loops.
  while (worklist.size() > 0) {
    Node* ctrl_out = worklist.pop();
    Node* referent = worklist.pop();
    insert_rf(ctrl_out, referent);
  }

  // Redundancy is determined by dominance relation.
  // Sometimes it becomes evident that an RF is redundant once it is moved out of the loop.
  // Also, newly introduced RF can make some existing RFs redundant.
  find_redundant_rfs(redundant_rfs);

  // Eliminate redundant RFs.
  bool progress = (redundant_rfs.size() > 0);
  while (redundant_rfs.size() > 0) {
    remove_rf(redundant_rfs.pop());
  }

  assert(redundant_rfs.size() == 0, "");
  assert(!has_redundant_rfs(redundant_rfs, true /*rf_only*/), "");

  return progress;
}

//======================================================================
//---------------------------- Phase 2 ---------------------------------

// Linearly traverse CFG upwards starting at n until first merge point.
// All encountered safepoints are recorded in safepoints list.
static void linear_traversal(Node* n, Node_Stack& worklist, VectorSet& visited, Node_List& safepoints) {
  for (Node* ctrl = n; ctrl != nullptr; ctrl = ctrl->in(0)) {
    assert(ctrl->is_CFG(), "");
    if (visited.test_set(ctrl->_idx)) {
      return;
    } else {
      if (ctrl->is_Region()) {
        worklist.push(ctrl, 1);
        return; // stop at merge points
      } else if (is_significant_sfpt(ctrl)) {
        safepoints.push(ctrl);
      }
    }
  }
}

// Enumerate all safepoints which are reachable from the RF to its referent through CFG.
// Start at rf node and traverse CFG upwards until referent's control node is reached.
static void enumerate_interfering_sfpts(Node* rf, PhaseIdealLoop* phase, Node_List& safepoints) {
  Node* referent = rf->in(1);
  Node* referent_ctrl = phase->get_ctrl(referent);
  assert(phase->is_dominator(referent_ctrl, rf), "sanity");

  VectorSet visited;
  visited.set(referent_ctrl->_idx); // end point

  Node_Stack stack(0);
  linear_traversal(rf, stack, visited, safepoints); // start point
  while (stack.is_nonempty()) {
    Node* cur = stack.node();
    uint  idx = stack.index();

    assert(cur != nullptr, "");
    assert(cur->is_Region(), "%s", NodeClassNames[cur->Opcode()]);
    assert(phase->is_dominator(referent_ctrl, cur), "");
    assert(idx > 0 && idx <= cur->req(), "%d %d", idx, cur->req());

    if (idx < cur->req()) {
      stack.set_index(idx + 1);
      linear_traversal(cur->in(idx), stack, visited, safepoints);
    } else {
      stack.pop();
    }
  }
}

// Start offset for reachability info on a safepoint node.
static uint rf_start_offset(SafePointNode* sfpt) {
  uint nof_extra_edges = 0;
  if (sfpt->is_Call()) {
    address entry = sfpt->as_Call()->entry_point();
    if (entry == OptoRuntime::new_array_Java() ||
        entry == OptoRuntime::new_array_nozero_Java()) {
      nof_extra_edges = 1; // valid_length_test_input
    }
  }
  return sfpt->jvms()->oopoff() + nof_extra_edges;
}

// Phase 2: migrate reachability info to safepoints.
// All RFs are replaced with edges from corresponding referents to interfering safepoints.
// Interfering safepoints are safepoint nodes which are reachable from the RF to its referent through CFG.
bool PhaseIdealLoop::eliminate_reachability_fences() {
  Compile::TracePhase tp(_t_reachability);

  if (!OptimizeReachabilityFences) {
    return false;
  }

  Unique_Node_List redundant_rfs;
  Node_List worklist;
  for (int i = 0; i < C->reachability_fences_count(); i++) {
    ReachabilityFenceNode* rf = C->reachability_fence(i)->as_ReachabilityFence();
    assert(!is_redundant_rf(rf, true /*rf_only*/), "missed");
    if (!is_redundant_rf(rf, false /*rf_only*/)) {
      Node_List safepoints;
      enumerate_interfering_sfpts(rf, this, safepoints);

      Node* referent = rf->in(1);
      while (safepoints.size() > 0) {
        SafePointNode* sfpt = safepoints.pop()->as_SafePoint();
        assert(is_dominator(get_ctrl(referent), sfpt), "");
        assert(sfpt->req() == rf_start_offset(sfpt), "");
        if (sfpt->find_edge(referent) == -1) {
          worklist.push(sfpt);
          worklist.push(referent);
        }
      }
    }
    redundant_rfs.push(rf);
  }

  while (worklist.size() > 0) {
    Node* referent = worklist.pop();
    Node* sfpt     = worklist.pop();
    sfpt->add_req(referent);
    igvn()._worklist.push(sfpt);
  }

  bool progress =  (redundant_rfs.size() > 0);
  while (redundant_rfs.size() > 0) {
    remove_rf(redundant_rfs.pop());
  }

  assert(C->reachability_fences_count() == 0, "");
  return progress;
}

//======================================================================
//---------------------------- Phase 3 ---------------------------------

// Find a point in CFG right after safepoint node to insert reachability fence.
static Node* sfpt_ctrl_out(SafePointNode* sfpt) {
  if (sfpt->is_Call()) {
    CallProjections callprojs;
    sfpt->as_Call()->extract_projections(&callprojs, false /*separate_io_proj*/, false /*do_asserts*/);
    if (callprojs.fallthrough_catchproj != nullptr) {
      return callprojs.fallthrough_catchproj;
    } else if (callprojs.catchall_catchproj != nullptr) {
      return callprojs.catchall_catchproj; // rethrow stub // TODO: safe to ignore?
    } else if (callprojs.fallthrough_proj != nullptr) {
      return callprojs.fallthrough_proj; // no exceptions thrown
    } else {
      ShouldNotReachHere();
    }
  } else {
    return sfpt;
  }
}

// Phase 3: expand reachability fences from safepoint info.
// Turn extra safepoint edges into reachability fences immediately following the safepoint.
void Compile::expand_reachability_fences(Unique_Node_List& safepoints) {
  for (uint i = 0; i < safepoints.size(); i++) {
    SafePointNode* sfpt = safepoints.at(i)->as_SafePoint();

    uint rf_offset = rf_start_offset(sfpt);
    if (sfpt->jvms() != nullptr && sfpt->req() > rf_offset) {
      assert(is_significant_sfpt(sfpt), "");
      Node* ctrl_out = sfpt_ctrl_out(sfpt);
      Node* ctrl_end = ctrl_out->unique_ctrl_out();

      while (sfpt->req() > rf_offset) {
        int idx = sfpt->req() - 1;
        Node* referent = sfpt->in(idx);
        sfpt->del_req(idx);

        Node* new_rf = new ReachabilityFenceNode(C, ctrl_out, referent);
        ctrl_end->replace_edge(ctrl_out, new_rf);
        ctrl_end = new_rf;
      }
    }
  }
}
