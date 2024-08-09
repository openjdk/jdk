/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciMethodData.hpp"
#include "compiler/compileLog.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/c2/barrierSetC2.hpp"
#include "libadt/vectset.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "opto/addnode.hpp"
#include "opto/arraycopynode.hpp"
#include "opto/callnode.hpp"
#include "opto/castnode.hpp"
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/divnode.hpp"
#include "opto/idealGraphPrinter.hpp"
#include "opto/loopnode.hpp"
#include "opto/movenode.hpp"
#include "opto/mulnode.hpp"
#include "opto/opaquenode.hpp"
#include "opto/predicates.hpp"
#include "opto/rootnode.hpp"
#include "opto/runtime.hpp"
#include "opto/vectorization.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/powerOfTwo.hpp"

//=============================================================================
//--------------------------is_cloop_ind_var-----------------------------------
// Determine if a node is a counted loop induction variable.
// NOTE: The method is declared in "node.hpp".
bool Node::is_cloop_ind_var() const {
  return (is_Phi() &&
          as_Phi()->region()->is_CountedLoop() &&
          as_Phi()->region()->as_CountedLoop()->phi() == this);
}

//=============================================================================
//------------------------------dump_spec--------------------------------------
// Dump special per-node info
#ifndef PRODUCT
void LoopNode::dump_spec(outputStream *st) const {
  RegionNode::dump_spec(st);
  if (is_inner_loop()) st->print( "inner " );
  if (is_partial_peel_loop()) st->print( "partial_peel " );
  if (partial_peel_has_failed()) st->print( "partial_peel_failed " );
}
#endif

//------------------------------is_valid_counted_loop-------------------------
bool LoopNode::is_valid_counted_loop(BasicType bt) const {
  if (is_BaseCountedLoop() && as_BaseCountedLoop()->bt() == bt) {
    BaseCountedLoopNode*    l  = as_BaseCountedLoop();
    BaseCountedLoopEndNode* le = l->loopexit_or_null();
    if (le != nullptr &&
        le->proj_out_or_null(1 /* true */) == l->in(LoopNode::LoopBackControl)) {
      Node* phi  = l->phi();
      Node* exit = le->proj_out_or_null(0 /* false */);
      if (exit != nullptr && exit->Opcode() == Op_IfFalse &&
          phi != nullptr && phi->is_Phi() &&
          phi->in(LoopNode::LoopBackControl) == l->incr() &&
          le->loopnode() == l && le->stride_is_con()) {
        return true;
      }
    }
  }
  return false;
}

//------------------------------get_early_ctrl---------------------------------
// Compute earliest legal control
Node *PhaseIdealLoop::get_early_ctrl( Node *n ) {
  assert( !n->is_Phi() && !n->is_CFG(), "this code only handles data nodes" );
  uint i;
  Node *early;
  if (n->in(0) && !n->is_expensive()) {
    early = n->in(0);
    if (!early->is_CFG()) // Might be a non-CFG multi-def
      early = get_ctrl(early);        // So treat input as a straight data input
    i = 1;
  } else {
    early = get_ctrl(n->in(1));
    i = 2;
  }
  uint e_d = dom_depth(early);
  assert( early, "" );
  for (; i < n->req(); i++) {
    Node *cin = get_ctrl(n->in(i));
    assert( cin, "" );
    // Keep deepest dominator depth
    uint c_d = dom_depth(cin);
    if (c_d > e_d) {           // Deeper guy?
      early = cin;              // Keep deepest found so far
      e_d = c_d;
    } else if (c_d == e_d &&    // Same depth?
               early != cin) { // If not equal, must use slower algorithm
      // If same depth but not equal, one _must_ dominate the other
      // and we want the deeper (i.e., dominated) guy.
      Node *n1 = early;
      Node *n2 = cin;
      while (1) {
        n1 = idom(n1);          // Walk up until break cycle
        n2 = idom(n2);
        if (n1 == cin ||        // Walked early up to cin
            dom_depth(n2) < c_d)
          break;                // early is deeper; keep him
        if (n2 == early ||      // Walked cin up to early
            dom_depth(n1) < c_d) {
          early = cin;          // cin is deeper; keep him
          break;
        }
      }
      e_d = dom_depth(early);   // Reset depth register cache
    }
  }

  // Return earliest legal location
  assert(early == find_non_split_ctrl(early), "unexpected early control");

  if (n->is_expensive() && !_verify_only && !_verify_me) {
    assert(n->in(0), "should have control input");
    early = get_early_ctrl_for_expensive(n, early);
  }

  return early;
}

//------------------------------get_early_ctrl_for_expensive---------------------------------
// Move node up the dominator tree as high as legal while still beneficial
Node *PhaseIdealLoop::get_early_ctrl_for_expensive(Node *n, Node* earliest) {
  assert(n->in(0) && n->is_expensive(), "expensive node with control input here");
  assert(OptimizeExpensiveOps, "optimization off?");

  Node* ctl = n->in(0);
  assert(ctl->is_CFG(), "expensive input 0 must be cfg");
  uint min_dom_depth = dom_depth(earliest);
#ifdef ASSERT
  if (!is_dominator(ctl, earliest) && !is_dominator(earliest, ctl)) {
    dump_bad_graph("Bad graph detected in get_early_ctrl_for_expensive", n, earliest, ctl);
    assert(false, "Bad graph detected in get_early_ctrl_for_expensive");
  }
#endif
  if (dom_depth(ctl) < min_dom_depth) {
    return earliest;
  }

  while (true) {
    Node* next = ctl;
    // Moving the node out of a loop on the projection of an If
    // confuses Loop Predication. So, once we hit a loop in an If branch
    // that doesn't branch to an UNC, we stop. The code that process
    // expensive nodes will notice the loop and skip over it to try to
    // move the node further up.
    if (ctl->is_CountedLoop() && ctl->in(1) != nullptr && ctl->in(1)->in(0) != nullptr && ctl->in(1)->in(0)->is_If()) {
      if (!ctl->in(1)->as_Proj()->is_uncommon_trap_if_pattern()) {
        break;
      }
      next = idom(ctl->in(1)->in(0));
    } else if (ctl->is_Proj()) {
      // We only move it up along a projection if the projection is
      // the single control projection for its parent: same code path,
      // if it's a If with UNC or fallthrough of a call.
      Node* parent_ctl = ctl->in(0);
      if (parent_ctl == nullptr) {
        break;
      } else if (parent_ctl->is_CountedLoopEnd() && parent_ctl->as_CountedLoopEnd()->loopnode() != nullptr) {
        next = parent_ctl->as_CountedLoopEnd()->loopnode()->init_control();
      } else if (parent_ctl->is_If()) {
        if (!ctl->as_Proj()->is_uncommon_trap_if_pattern()) {
          break;
        }
        assert(idom(ctl) == parent_ctl, "strange");
        next = idom(parent_ctl);
      } else if (ctl->is_CatchProj()) {
        if (ctl->as_Proj()->_con != CatchProjNode::fall_through_index) {
          break;
        }
        assert(parent_ctl->in(0)->in(0)->is_Call(), "strange graph");
        next = parent_ctl->in(0)->in(0)->in(0);
      } else {
        // Check if parent control has a single projection (this
        // control is the only possible successor of the parent
        // control). If so, we can try to move the node above the
        // parent control.
        int nb_ctl_proj = 0;
        for (DUIterator_Fast imax, i = parent_ctl->fast_outs(imax); i < imax; i++) {
          Node *p = parent_ctl->fast_out(i);
          if (p->is_Proj() && p->is_CFG()) {
            nb_ctl_proj++;
            if (nb_ctl_proj > 1) {
              break;
            }
          }
        }

        if (nb_ctl_proj > 1) {
          break;
        }
        assert(parent_ctl->is_Start() || parent_ctl->is_MemBar() || parent_ctl->is_Call() ||
               BarrierSet::barrier_set()->barrier_set_c2()->is_gc_barrier_node(parent_ctl), "unexpected node");
        assert(idom(ctl) == parent_ctl, "strange");
        next = idom(parent_ctl);
      }
    } else {
      next = idom(ctl);
    }
    if (next->is_Root() || next->is_Start() || dom_depth(next) < min_dom_depth) {
      break;
    }
    ctl = next;
  }

  if (ctl != n->in(0)) {
    _igvn.replace_input_of(n, 0, ctl);
    _igvn.hash_insert(n);
  }

  return ctl;
}


//------------------------------set_early_ctrl---------------------------------
// Set earliest legal control
void PhaseIdealLoop::set_early_ctrl(Node* n, bool update_body) {
  Node *early = get_early_ctrl(n);

  // Record earliest legal location
  set_ctrl(n, early);
  IdealLoopTree *loop = get_loop(early);
  if (update_body && loop->_child == nullptr) {
    loop->_body.push(n);
  }
}

//------------------------------set_subtree_ctrl-------------------------------
// set missing _ctrl entries on new nodes
void PhaseIdealLoop::set_subtree_ctrl(Node* n, bool update_body) {
  // Already set?  Get out.
  if (_loop_or_ctrl[n->_idx]) return;
  // Recursively set _loop_or_ctrl array to indicate where the Node goes
  uint i;
  for (i = 0; i < n->req(); ++i) {
    Node *m = n->in(i);
    if (m && m != C->root()) {
      set_subtree_ctrl(m, update_body);
    }
  }

  // Fixup self
  set_early_ctrl(n, update_body);
}

IdealLoopTree* PhaseIdealLoop::insert_outer_loop(IdealLoopTree* loop, LoopNode* outer_l, Node* outer_ift) {
  IdealLoopTree* outer_ilt = new IdealLoopTree(this, outer_l, outer_ift);
  IdealLoopTree* parent = loop->_parent;
  IdealLoopTree* sibling = parent->_child;
  if (sibling == loop) {
    parent->_child = outer_ilt;
  } else {
    while (sibling->_next != loop) {
      sibling = sibling->_next;
    }
    sibling->_next = outer_ilt;
  }
  outer_ilt->_next = loop->_next;
  outer_ilt->_parent = parent;
  outer_ilt->_child = loop;
  outer_ilt->_nest = loop->_nest;
  loop->_parent = outer_ilt;
  loop->_next = nullptr;
  loop->_nest++;
  assert(loop->_nest <= SHRT_MAX, "sanity");
  return outer_ilt;
}

// Create a skeleton strip mined outer loop: a Loop head before the
// inner strip mined loop, a safepoint and an exit condition guarded
// by an opaque node after the inner strip mined loop with a backedge
// to the loop head. The inner strip mined loop is left as it is. Only
// once loop optimizations are over, do we adjust the inner loop exit
// condition to limit its number of iterations, set the outer loop
// exit condition and add Phis to the outer loop head. Some loop
// optimizations that operate on the inner strip mined loop need to be
// aware of the outer strip mined loop: loop unswitching needs to
// clone the outer loop as well as the inner, unrolling needs to only
// clone the inner loop etc. No optimizations need to change the outer
// strip mined loop as it is only a skeleton.
IdealLoopTree* PhaseIdealLoop::create_outer_strip_mined_loop(BoolNode *test, Node *cmp, Node *init_control,
                                                             IdealLoopTree* loop, float cl_prob, float le_fcnt,
                                                             Node*& entry_control, Node*& iffalse) {
  Node* outer_test = _igvn.intcon(0);
  set_ctrl(outer_test, C->root());
  Node *orig = iffalse;
  iffalse = iffalse->clone();
  _igvn.register_new_node_with_optimizer(iffalse);
  set_idom(iffalse, idom(orig), dom_depth(orig));

  IfNode *outer_le = new OuterStripMinedLoopEndNode(iffalse, outer_test, cl_prob, le_fcnt);
  Node *outer_ift = new IfTrueNode (outer_le);
  Node* outer_iff = orig;
  _igvn.replace_input_of(outer_iff, 0, outer_le);

  LoopNode *outer_l = new OuterStripMinedLoopNode(C, init_control, outer_ift);
  entry_control = outer_l;

  IdealLoopTree* outer_ilt = insert_outer_loop(loop, outer_l, outer_ift);

  set_loop(iffalse, outer_ilt);
  // When this code runs, loop bodies have not yet been populated.
  const bool body_populated = false;
  register_control(outer_le, outer_ilt, iffalse, body_populated);
  register_control(outer_ift, outer_ilt, outer_le, body_populated);
  set_idom(outer_iff, outer_le, dom_depth(outer_le));
  _igvn.register_new_node_with_optimizer(outer_l);
  set_loop(outer_l, outer_ilt);
  set_idom(outer_l, init_control, dom_depth(init_control)+1);

  return outer_ilt;
}

void PhaseIdealLoop::insert_loop_limit_check_predicate(ParsePredicateSuccessProj* loop_limit_check_parse_proj,
                                                       Node* cmp_limit, Node* bol) {
  assert(loop_limit_check_parse_proj->in(0)->is_ParsePredicate(), "must be parse predicate");
  Node* new_predicate_proj = create_new_if_for_predicate(loop_limit_check_parse_proj, nullptr,
                                                         Deoptimization::Reason_loop_limit_check,
                                                         Op_If);
  Node* iff = new_predicate_proj->in(0);
  cmp_limit = _igvn.register_new_node_with_optimizer(cmp_limit);
  bol = _igvn.register_new_node_with_optimizer(bol);
  set_subtree_ctrl(bol, false);
  _igvn.replace_input_of(iff, 1, bol);

#ifndef PRODUCT
  // report that the loop predication has been actually performed
  // for this loop
  if (TraceLoopLimitCheck) {
    tty->print_cr("Counted Loop Limit Check generated:");
    debug_only( bol->dump(2); )
  }
#endif
}

Node* PhaseIdealLoop::loop_exit_control(Node* x, IdealLoopTree* loop) {
  // Counted loop head must be a good RegionNode with only 3 not null
  // control input edges: Self, Entry, LoopBack.
  if (x->in(LoopNode::Self) == nullptr || x->req() != 3 || loop->_irreducible) {
    return nullptr;
  }
  Node *init_control = x->in(LoopNode::EntryControl);
  Node *back_control = x->in(LoopNode::LoopBackControl);
  if (init_control == nullptr || back_control == nullptr) {   // Partially dead
    return nullptr;
  }
  // Must also check for TOP when looking for a dead loop
  if (init_control->is_top() || back_control->is_top()) {
    return nullptr;
  }

  // Allow funny placement of Safepoint
  if (back_control->Opcode() == Op_SafePoint) {
    back_control = back_control->in(TypeFunc::Control);
  }

  // Controlling test for loop
  Node *iftrue = back_control;
  uint iftrue_op = iftrue->Opcode();
  if (iftrue_op != Op_IfTrue &&
      iftrue_op != Op_IfFalse) {
    // I have a weird back-control.  Probably the loop-exit test is in
    // the middle of the loop and I am looking at some trailing control-flow
    // merge point.  To fix this I would have to partially peel the loop.
    return nullptr; // Obscure back-control
  }

  // Get boolean guarding loop-back test
  Node *iff = iftrue->in(0);
  if (get_loop(iff) != loop || !iff->in(1)->is_Bool()) {
    return nullptr;
  }
  return iftrue;
}

Node* PhaseIdealLoop::loop_exit_test(Node* back_control, IdealLoopTree* loop, Node*& incr, Node*& limit, BoolTest::mask& bt, float& cl_prob) {
  Node* iftrue = back_control;
  uint iftrue_op = iftrue->Opcode();
  Node* iff = iftrue->in(0);
  BoolNode* test = iff->in(1)->as_Bool();
  bt = test->_test._test;
  cl_prob = iff->as_If()->_prob;
  if (iftrue_op == Op_IfFalse) {
    bt = BoolTest(bt).negate();
    cl_prob = 1.0 - cl_prob;
  }
  // Get backedge compare
  Node* cmp = test->in(1);
  if (!cmp->is_Cmp()) {
    return nullptr;
  }

  // Find the trip-counter increment & limit.  Limit must be loop invariant.
  incr  = cmp->in(1);
  limit = cmp->in(2);

  // ---------
  // need 'loop()' test to tell if limit is loop invariant
  // ---------

  if (!is_member(loop, get_ctrl(incr))) { // Swapped trip counter and limit?
    Node* tmp = incr;            // Then reverse order into the CmpI
    incr = limit;
    limit = tmp;
    bt = BoolTest(bt).commute(); // And commute the exit test
  }
  if (is_member(loop, get_ctrl(limit))) { // Limit must be loop-invariant
    return nullptr;
  }
  if (!is_member(loop, get_ctrl(incr))) { // Trip counter must be loop-variant
    return nullptr;
  }
  return cmp;
}

Node* PhaseIdealLoop::loop_iv_incr(Node* incr, Node* x, IdealLoopTree* loop, Node*& phi_incr) {
  if (incr->is_Phi()) {
    if (incr->as_Phi()->region() != x || incr->req() != 3) {
      return nullptr; // Not simple trip counter expression
    }
    phi_incr = incr;
    incr = phi_incr->in(LoopNode::LoopBackControl); // Assume incr is on backedge of Phi
    if (!is_member(loop, get_ctrl(incr))) { // Trip counter must be loop-variant
      return nullptr;
    }
  }
  return incr;
}

Node* PhaseIdealLoop::loop_iv_stride(Node* incr, IdealLoopTree* loop, Node*& xphi) {
  assert(incr->Opcode() == Op_AddI || incr->Opcode() == Op_AddL, "caller resp.");
  // Get merge point
  xphi = incr->in(1);
  Node *stride = incr->in(2);
  if (!stride->is_Con()) {     // Oops, swap these
    if (!xphi->is_Con()) {     // Is the other guy a constant?
      return nullptr;          // Nope, unknown stride, bail out
    }
    Node *tmp = xphi;          // 'incr' is commutative, so ok to swap
    xphi = stride;
    stride = tmp;
  }
  return stride;
}

PhiNode* PhaseIdealLoop::loop_iv_phi(Node* xphi, Node* phi_incr, Node* x, IdealLoopTree* loop) {
  if (!xphi->is_Phi()) {
    return nullptr; // Too much math on the trip counter
  }
  if (phi_incr != nullptr && phi_incr != xphi) {
    return nullptr;
  }
  PhiNode *phi = xphi->as_Phi();

  // Phi must be of loop header; backedge must wrap to increment
  if (phi->region() != x) {
    return nullptr;
  }
  return phi;
}

static int check_stride_overflow(jlong final_correction, const TypeInteger* limit_t, BasicType bt) {
  if (final_correction > 0) {
    if (limit_t->lo_as_long() > (max_signed_integer(bt) - final_correction)) {
      return -1;
    }
    if (limit_t->hi_as_long() > (max_signed_integer(bt) - final_correction)) {
      return 1;
    }
  } else {
    if (limit_t->hi_as_long() < (min_signed_integer(bt) - final_correction)) {
      return -1;
    }
    if (limit_t->lo_as_long() < (min_signed_integer(bt) - final_correction)) {
      return 1;
    }
  }
  return 0;
}

static bool condition_stride_ok(BoolTest::mask bt, jlong stride_con) {
  // If the condition is inverted and we will be rolling
  // through MININT to MAXINT, then bail out.
  if (bt == BoolTest::eq || // Bail out, but this loop trips at most twice!
      // Odd stride
      (bt == BoolTest::ne && stride_con != 1 && stride_con != -1) ||
      // Count down loop rolls through MAXINT
      ((bt == BoolTest::le || bt == BoolTest::lt) && stride_con < 0) ||
      // Count up loop rolls through MININT
      ((bt == BoolTest::ge || bt == BoolTest::gt) && stride_con > 0)) {
    return false; // Bail out
  }
  return true;
}

Node* PhaseIdealLoop::loop_nest_replace_iv(Node* iv_to_replace, Node* inner_iv, Node* outer_phi, Node* inner_head,
                                           BasicType bt) {
  Node* iv_as_long;
  if (bt == T_LONG) {
    iv_as_long = new ConvI2LNode(inner_iv, TypeLong::INT);
    register_new_node(iv_as_long, inner_head);
  } else {
    iv_as_long = inner_iv;
  }
  Node* iv_replacement = AddNode::make(outer_phi, iv_as_long, bt);
  register_new_node(iv_replacement, inner_head);
  for (DUIterator_Last imin, i = iv_to_replace->last_outs(imin); i >= imin;) {
    Node* u = iv_to_replace->last_out(i);
#ifdef ASSERT
    if (!is_dominator(inner_head, ctrl_or_self(u))) {
      assert(u->is_Phi(), "should be a Phi");
      for (uint j = 1; j < u->req(); j++) {
        if (u->in(j) == iv_to_replace) {
          assert(is_dominator(inner_head, u->in(0)->in(j)), "iv use above loop?");
        }
      }
    }
#endif
    _igvn.rehash_node_delayed(u);
    int nb = u->replace_edge(iv_to_replace, iv_replacement, &_igvn);
    i -= nb;
  }
  return iv_replacement;
}

// Add a Parse Predicate with an uncommon trap on the failing/false path. Normal control will continue on the true path.
void PhaseIdealLoop::add_parse_predicate(Deoptimization::DeoptReason reason, Node* inner_head, IdealLoopTree* loop,
                                         SafePointNode* sfpt) {
  if (!C->too_many_traps(reason)) {
    ParsePredicateNode* parse_predicate = new ParsePredicateNode(inner_head->in(LoopNode::EntryControl), reason, &_igvn);
    register_control(parse_predicate, loop, inner_head->in(LoopNode::EntryControl));
    Node* if_false = new IfFalseNode(parse_predicate);
    register_control(if_false, _ltree_root, parse_predicate);
    Node* if_true = new IfTrueNode(parse_predicate);
    register_control(if_true, loop, parse_predicate);

    int trap_request = Deoptimization::make_trap_request(reason, Deoptimization::Action_maybe_recompile);
    address call_addr = OptoRuntime::uncommon_trap_blob()->entry_point();
    const TypePtr* no_memory_effects = nullptr;
    JVMState* jvms = sfpt->jvms();
    CallNode* unc = new CallStaticJavaNode(OptoRuntime::uncommon_trap_Type(), call_addr, "uncommon_trap",
                                           no_memory_effects);

    Node* mem = nullptr;
    Node* i_o = nullptr;
    if (sfpt->is_Call()) {
      mem = sfpt->proj_out(TypeFunc::Memory);
      i_o = sfpt->proj_out(TypeFunc::I_O);
    } else {
      mem = sfpt->memory();
      i_o = sfpt->i_o();
    }

    Node *frame = new ParmNode(C->start(), TypeFunc::FramePtr);
    register_new_node(frame, C->start());
    Node *ret = new ParmNode(C->start(), TypeFunc::ReturnAdr);
    register_new_node(ret, C->start());

    unc->init_req(TypeFunc::Control, if_false);
    unc->init_req(TypeFunc::I_O, i_o);
    unc->init_req(TypeFunc::Memory, mem); // may gc ptrs
    unc->init_req(TypeFunc::FramePtr, frame);
    unc->init_req(TypeFunc::ReturnAdr, ret);
    unc->init_req(TypeFunc::Parms+0, _igvn.intcon(trap_request));
    unc->set_cnt(PROB_UNLIKELY_MAG(4));
    unc->copy_call_debug_info(&_igvn, sfpt);

    for (uint i = TypeFunc::Parms; i < unc->req(); i++) {
      set_subtree_ctrl(unc->in(i), false);
    }
    register_control(unc, _ltree_root, if_false);

    Node* ctrl = new ProjNode(unc, TypeFunc::Control);
    register_control(ctrl, _ltree_root, unc);
    Node* halt = new HaltNode(ctrl, frame, "uncommon trap returned which should never happen" PRODUCT_ONLY(COMMA /*reachable*/false));
    register_control(halt, _ltree_root, ctrl);
    _igvn.add_input_to(C->root(), halt);

    _igvn.replace_input_of(inner_head, LoopNode::EntryControl, if_true);
    set_idom(inner_head, if_true, dom_depth(inner_head));
  }
}

// Find a safepoint node that dominates the back edge. We need a
// SafePointNode so we can use its jvm state to create empty
// predicates.
static bool no_side_effect_since_safepoint(Compile* C, Node* x, Node* mem, MergeMemNode* mm, PhaseIdealLoop* phase) {
  SafePointNode* safepoint = nullptr;
  for (DUIterator_Fast imax, i = x->fast_outs(imax); i < imax; i++) {
    Node* u = x->fast_out(i);
    if (u->is_memory_phi()) {
      Node* m = u->in(LoopNode::LoopBackControl);
      if (u->adr_type() == TypePtr::BOTTOM) {
        if (m->is_MergeMem() && mem->is_MergeMem()) {
          if (m != mem DEBUG_ONLY(|| true)) {
            // MergeMemStream can modify m, for example to adjust the length to mem.
            // This is unfortunate, and probably unnecessary. But as it is, we need
            // to add m to the igvn worklist, else we may have a modified node that
            // is not on the igvn worklist.
            phase->igvn()._worklist.push(m);
            for (MergeMemStream mms(m->as_MergeMem(), mem->as_MergeMem()); mms.next_non_empty2(); ) {
              if (!mms.is_empty()) {
                if (mms.memory() != mms.memory2()) {
                  return false;
                }
#ifdef ASSERT
                if (mms.alias_idx() != Compile::AliasIdxBot) {
                  mm->set_memory_at(mms.alias_idx(), mem->as_MergeMem()->base_memory());
                }
#endif
              }
            }
          }
        } else if (mem->is_MergeMem()) {
          if (m != mem->as_MergeMem()->base_memory()) {
            return false;
          }
        } else {
          return false;
        }
      } else {
        if (mem->is_MergeMem()) {
          if (m != mem->as_MergeMem()->memory_at(C->get_alias_index(u->adr_type()))) {
            return false;
          }
#ifdef ASSERT
          mm->set_memory_at(C->get_alias_index(u->adr_type()), mem->as_MergeMem()->base_memory());
#endif
        } else {
          if (m != mem) {
            return false;
          }
        }
      }
    }
  }
  return true;
}

SafePointNode* PhaseIdealLoop::find_safepoint(Node* back_control, Node* x, IdealLoopTree* loop) {
  IfNode* exit_test = back_control->in(0)->as_If();
  SafePointNode* safepoint = nullptr;
  if (exit_test->in(0)->is_SafePoint() && exit_test->in(0)->outcnt() == 1) {
    safepoint = exit_test->in(0)->as_SafePoint();
  } else {
    Node* c = back_control;
    while (c != x && c->Opcode() != Op_SafePoint) {
      c = idom(c);
    }

    if (c->Opcode() == Op_SafePoint) {
      safepoint = c->as_SafePoint();
    }

    if (safepoint == nullptr) {
      return nullptr;
    }

    Node* mem = safepoint->in(TypeFunc::Memory);

    // We can only use that safepoint if there's no side effect between the backedge and the safepoint.

    // mm is used for book keeping
    MergeMemNode* mm = nullptr;
#ifdef ASSERT
    if (mem->is_MergeMem()) {
      mm = mem->clone()->as_MergeMem();
      _igvn._worklist.push(mm);
      for (MergeMemStream mms(mem->as_MergeMem()); mms.next_non_empty(); ) {
        if (mms.alias_idx() != Compile::AliasIdxBot && loop != get_loop(ctrl_or_self(mms.memory()))) {
          mm->set_memory_at(mms.alias_idx(), mem->as_MergeMem()->base_memory());
        }
      }
    }
#endif
    if (!no_side_effect_since_safepoint(C, x, mem, mm, this)) {
      safepoint = nullptr;
    } else {
      assert(mm == nullptr|| _igvn.transform(mm) == mem->as_MergeMem()->base_memory(), "all memory state should have been processed");
    }
#ifdef ASSERT
    if (mm != nullptr) {
      _igvn.remove_dead_node(mm);
    }
#endif
  }
  return safepoint;
}

// If the loop has the shape of a counted loop but with a long
// induction variable, transform the loop in a loop nest: an inner
// loop that iterates for at most max int iterations with an integer
// induction variable and an outer loop that iterates over the full
// range of long values from the initial loop in (at most) max int
// steps. That is:
//
// x: for (long phi = init; phi < limit; phi += stride) {
//   // phi := Phi(L, init, incr)
//   // incr := AddL(phi, longcon(stride))
//   long incr = phi + stride;
//   ... use phi and incr ...
// }
//
// OR:
//
// x: for (long phi = init; (phi += stride) < limit; ) {
//   // phi := Phi(L, AddL(init, stride), incr)
//   // incr := AddL(phi, longcon(stride))
//   long incr = phi + stride;
//   ... use phi and (phi + stride) ...
// }
//
// ==transform=>
//
// const ulong inner_iters_limit = INT_MAX - stride - 1;  //near 0x7FFFFFF0
// assert(stride <= inner_iters_limit);  // else abort transform
// assert((extralong)limit + stride <= LONG_MAX);  // else deopt
// outer_head: for (long outer_phi = init;;) {
//   // outer_phi := Phi(outer_head, init, AddL(outer_phi, I2L(inner_phi)))
//   ulong inner_iters_max = (ulong) MAX(0, ((extralong)limit + stride - outer_phi));
//   long inner_iters_actual = MIN(inner_iters_limit, inner_iters_max);
//   assert(inner_iters_actual == (int)inner_iters_actual);
//   int inner_phi, inner_incr;
//   x: for (inner_phi = 0;; inner_phi = inner_incr) {
//     // inner_phi := Phi(x, intcon(0), inner_incr)
//     // inner_incr := AddI(inner_phi, intcon(stride))
//     inner_incr = inner_phi + stride;
//     if (inner_incr < inner_iters_actual) {
//       ... use phi=>(outer_phi+inner_phi) ...
//       continue;
//     }
//     else break;
//   }
//   if ((outer_phi+inner_phi) < limit)  //OR (outer_phi+inner_incr) < limit
//     continue;
//   else break;
// }
//
// The same logic is used to transform an int counted loop that contains long range checks into a loop nest of 2 int
// loops with long range checks transformed to int range checks in the inner loop.
bool PhaseIdealLoop::create_loop_nest(IdealLoopTree* loop, Node_List &old_new) {
  Node* x = loop->_head;
  // Only for inner loops
  if (loop->_child != nullptr || !x->is_BaseCountedLoop() || x->as_Loop()->is_loop_nest_outer_loop()) {
    return false;
  }

  if (x->is_CountedLoop() && !x->as_CountedLoop()->is_main_loop() && !x->as_CountedLoop()->is_normal_loop()) {
    return false;
  }

  BaseCountedLoopNode* head = x->as_BaseCountedLoop();
  BasicType bt = x->as_BaseCountedLoop()->bt();

  check_counted_loop_shape(loop, x, bt);

#ifndef PRODUCT
  if (bt == T_LONG) {
    Atomic::inc(&_long_loop_candidates);
  }
#endif

  jlong stride_con_long = head->stride_con();
  assert(stride_con_long != 0, "missed some peephole opt");
  // We can't iterate for more than max int at a time.
  if (stride_con_long != (jint)stride_con_long || stride_con_long == min_jint) {
    assert(bt == T_LONG, "only for long loops");
    return false;
  }
  jint stride_con = checked_cast<jint>(stride_con_long);
  // The number of iterations for the integer count loop: guarantee no
  // overflow: max_jint - stride_con max. -1 so there's no need for a
  // loop limit check if the exit test is <= or >=.
  int iters_limit = max_jint - ABS(stride_con) - 1;
#ifdef ASSERT
  if (bt == T_LONG && StressLongCountedLoop > 0) {
    iters_limit = iters_limit / StressLongCountedLoop;
  }
#endif
  // At least 2 iterations so counted loop construction doesn't fail
  if (iters_limit/ABS(stride_con) < 2) {
    return false;
  }

  PhiNode* phi = head->phi()->as_Phi();
  Node* incr = head->incr();

  Node* back_control = head->in(LoopNode::LoopBackControl);

  // data nodes on back branch not supported
  if (back_control->outcnt() > 1) {
    return false;
  }

  Node* limit = head->limit();
  // We'll need to use the loop limit before the inner loop is entered
  if (!is_dominator(get_ctrl(limit), x)) {
    return false;
  }

  IfNode* exit_test = head->loopexit();

  assert(back_control->Opcode() == Op_IfTrue, "wrong projection for back edge");

  Node_List range_checks;
  iters_limit = extract_long_range_checks(loop, stride_con, iters_limit, phi, range_checks);

  if (bt == T_INT) {
    // The only purpose of creating a loop nest is to handle long range checks. If there are none, do not proceed further.
    if (range_checks.size() == 0) {
      return false;
    }
  }

  // Take what we know about the number of iterations of the long counted loop into account when computing the limit of
  // the inner loop.
  const Node* init = head->init_trip();
  const TypeInteger* lo = _igvn.type(init)->is_integer(bt);
  const TypeInteger* hi = _igvn.type(limit)->is_integer(bt);
  if (stride_con < 0) {
    swap(lo, hi);
  }
  if (hi->hi_as_long() <= lo->lo_as_long()) {
    // not a loop after all
    return false;
  }

  if (range_checks.size() > 0) {
    // This transformation requires peeling one iteration. Also, if it has range checks and they are eliminated by Loop
    // Predication, then 2 Hoisted Check Predicates are added for one range check. Finally, transforming a long range
    // check requires extra logic to be executed before the loop is entered and for the outer loop. As a result, the
    // transformations can't pay off for a small number of iterations: roughly, if the loop runs for 3 iterations, it's
    // going to execute as many range checks once transformed with range checks eliminated (1 peeled iteration with
    // range checks + 2 predicates per range checks) as it would have not transformed. It also has to pay for the extra
    // logic on loop entry and for the outer loop.
    loop->compute_trip_count(this);
    if (head->is_CountedLoop() && head->as_CountedLoop()->has_exact_trip_count()) {
      if (head->as_CountedLoop()->trip_count() <= 3) {
        return false;
      }
    } else {
      loop->compute_profile_trip_cnt(this);
      if (!head->is_profile_trip_failed() && head->profile_trip_cnt() <= 3) {
        return false;
      }
    }
  }

  julong orig_iters = (julong)hi->hi_as_long() - lo->lo_as_long();
  iters_limit = checked_cast<int>(MIN2((julong)iters_limit, orig_iters));

  // We need a safepoint to insert Parse Predicates for the inner loop.
  SafePointNode* safepoint;
  if (bt == T_INT && head->as_CountedLoop()->is_strip_mined()) {
    // Loop is strip mined: use the safepoint of the outer strip mined loop
    OuterStripMinedLoopNode* outer_loop = head->as_CountedLoop()->outer_loop();
    assert(outer_loop != nullptr, "no outer loop");
    safepoint = outer_loop->outer_safepoint();
    outer_loop->transform_to_counted_loop(&_igvn, this);
    exit_test = head->loopexit();
  } else {
    safepoint = find_safepoint(back_control, x, loop);
  }

  Node* exit_branch = exit_test->proj_out(false);
  Node* entry_control = head->in(LoopNode::EntryControl);

  // Clone the control flow of the loop to build an outer loop
  Node* outer_back_branch = back_control->clone();
  Node* outer_exit_test = new IfNode(exit_test->in(0), exit_test->in(1), exit_test->_prob, exit_test->_fcnt);
  Node* inner_exit_branch = exit_branch->clone();

  LoopNode* outer_head = new LoopNode(entry_control, outer_back_branch);
  IdealLoopTree* outer_ilt = insert_outer_loop(loop, outer_head, outer_back_branch);

  const bool body_populated = true;
  register_control(outer_head, outer_ilt, entry_control, body_populated);

  _igvn.register_new_node_with_optimizer(inner_exit_branch);
  set_loop(inner_exit_branch, outer_ilt);
  set_idom(inner_exit_branch, exit_test, dom_depth(exit_branch));

  outer_exit_test->set_req(0, inner_exit_branch);
  register_control(outer_exit_test, outer_ilt, inner_exit_branch, body_populated);

  _igvn.replace_input_of(exit_branch, 0, outer_exit_test);
  set_idom(exit_branch, outer_exit_test, dom_depth(exit_branch));

  outer_back_branch->set_req(0, outer_exit_test);
  register_control(outer_back_branch, outer_ilt, outer_exit_test, body_populated);

  _igvn.replace_input_of(x, LoopNode::EntryControl, outer_head);
  set_idom(x, outer_head, dom_depth(x));

  // add an iv phi to the outer loop and use it to compute the inner
  // loop iteration limit
  Node* outer_phi = phi->clone();
  outer_phi->set_req(0, outer_head);
  register_new_node(outer_phi, outer_head);

  Node* inner_iters_max = nullptr;
  if (stride_con > 0) {
    inner_iters_max = MaxNode::max_diff_with_zero(limit, outer_phi, TypeInteger::bottom(bt), _igvn);
  } else {
    inner_iters_max = MaxNode::max_diff_with_zero(outer_phi, limit, TypeInteger::bottom(bt), _igvn);
  }

  Node* inner_iters_limit = _igvn.integercon(iters_limit, bt);
  // inner_iters_max may not fit in a signed integer (iterating from
  // Long.MIN_VALUE to Long.MAX_VALUE for instance). Use an unsigned
  // min.
  const TypeInteger* inner_iters_actual_range = TypeInteger::make(0, iters_limit, Type::WidenMin, bt);
  Node* inner_iters_actual = MaxNode::unsigned_min(inner_iters_max, inner_iters_limit, inner_iters_actual_range, _igvn);

  Node* inner_iters_actual_int;
  if (bt == T_LONG) {
    inner_iters_actual_int = new ConvL2INode(inner_iters_actual);
    _igvn.register_new_node_with_optimizer(inner_iters_actual_int);
    // When the inner loop is transformed to a counted loop, a loop limit check is not expected to be needed because
    // the loop limit is less or equal to max_jint - stride - 1 (if stride is positive but a similar argument exists for
    // a negative stride). We add a CastII here to guarantee that, when the counted loop is created in a subsequent loop
    // opts pass, an accurate range of values for the limits is found.
    const TypeInt* inner_iters_actual_int_range = TypeInt::make(0, iters_limit, Type::WidenMin);
    inner_iters_actual_int = new CastIINode(outer_head, inner_iters_actual_int, inner_iters_actual_int_range, ConstraintCastNode::UnconditionalDependency);
    _igvn.register_new_node_with_optimizer(inner_iters_actual_int);
  } else {
    inner_iters_actual_int = inner_iters_actual;
  }

  Node* int_zero = _igvn.intcon(0);
  set_ctrl(int_zero, C->root());
  if (stride_con < 0) {
    inner_iters_actual_int = new SubINode(int_zero, inner_iters_actual_int);
    _igvn.register_new_node_with_optimizer(inner_iters_actual_int);
  }

  // Clone the iv data nodes as an integer iv
  Node* int_stride = _igvn.intcon(stride_con);
  set_ctrl(int_stride, C->root());
  Node* inner_phi = new PhiNode(x->in(0), TypeInt::INT);
  Node* inner_incr = new AddINode(inner_phi, int_stride);
  Node* inner_cmp = nullptr;
  inner_cmp = new CmpINode(inner_incr, inner_iters_actual_int);
  Node* inner_bol = new BoolNode(inner_cmp, exit_test->in(1)->as_Bool()->_test._test);
  inner_phi->set_req(LoopNode::EntryControl, int_zero);
  inner_phi->set_req(LoopNode::LoopBackControl, inner_incr);
  register_new_node(inner_phi, x);
  register_new_node(inner_incr, x);
  register_new_node(inner_cmp, x);
  register_new_node(inner_bol, x);

  _igvn.replace_input_of(exit_test, 1, inner_bol);

  // Clone inner loop phis to outer loop
  for (uint i = 0; i < head->outcnt(); i++) {
    Node* u = head->raw_out(i);
    if (u->is_Phi() && u != inner_phi && u != phi) {
      assert(u->in(0) == head, "inconsistent");
      Node* clone = u->clone();
      clone->set_req(0, outer_head);
      register_new_node(clone, outer_head);
      _igvn.replace_input_of(u, LoopNode::EntryControl, clone);
    }
  }

  // Replace inner loop long iv phi as inner loop int iv phi + outer
  // loop iv phi
  Node* iv_add = loop_nest_replace_iv(phi, inner_phi, outer_phi, head, bt);

  set_subtree_ctrl(inner_iters_actual_int, body_populated);

  LoopNode* inner_head = create_inner_head(loop, head, exit_test);

  // Summary of steps from initial loop to loop nest:
  //
  // == old IR nodes =>
  //
  // entry_control: {...}
  // x:
  // for (long phi = init;;) {
  //   // phi := Phi(x, init, incr)
  //   // incr := AddL(phi, longcon(stride))
  //   exit_test:
  //   if (phi < limit)
  //     back_control: fallthrough;
  //   else
  //     exit_branch: break;
  //   long incr = phi + stride;
  //   ... use phi and incr ...
  //   phi = incr;
  // }
  //
  // == new IR nodes (just before final peel) =>
  //
  // entry_control: {...}
  // long adjusted_limit = limit + stride;  //because phi_incr != nullptr
  // assert(!limit_check_required || (extralong)limit + stride == adjusted_limit);  // else deopt
  // ulong inner_iters_limit = max_jint - ABS(stride) - 1;  //near 0x7FFFFFF0
  // outer_head:
  // for (long outer_phi = init;;) {
  //   // outer_phi := phi->clone(), in(0):=outer_head, => Phi(outer_head, init, incr)
  //   // REPLACE phi  => AddL(outer_phi, I2L(inner_phi))
  //   // REPLACE incr => AddL(outer_phi, I2L(inner_incr))
  //   // SO THAT outer_phi := Phi(outer_head, init, AddL(outer_phi, I2L(inner_incr)))
  //   ulong inner_iters_max = (ulong) MAX(0, ((extralong)adjusted_limit - outer_phi) * SGN(stride));
  //   int inner_iters_actual_int = (int) MIN(inner_iters_limit, inner_iters_max) * SGN(stride);
  //   inner_head: x: //in(1) := outer_head
  //   int inner_phi;
  //   for (inner_phi = 0;;) {
  //     // inner_phi := Phi(x, intcon(0), inner_phi + stride)
  //     int inner_incr = inner_phi + stride;
  //     bool inner_bol = (inner_incr < inner_iters_actual_int);
  //     exit_test: //exit_test->in(1) := inner_bol;
  //     if (inner_bol) // WAS (phi < limit)
  //       back_control: fallthrough;
  //     else
  //       inner_exit_branch: break;  //exit_branch->clone()
  //     ... use phi=>(outer_phi+inner_phi) ...
  //     inner_phi = inner_phi + stride;  // inner_incr
  //   }
  //   outer_exit_test:  //exit_test->clone(), in(0):=inner_exit_branch
  //   if ((outer_phi+inner_phi) < limit)  // WAS (phi < limit)
  //     outer_back_branch: fallthrough;  //back_control->clone(), in(0):=outer_exit_test
  //   else
  //     exit_branch: break;  //in(0) := outer_exit_test
  // }

  if (bt == T_INT) {
    outer_phi = new ConvI2LNode(outer_phi);
    register_new_node(outer_phi, outer_head);
  }

  transform_long_range_checks(stride_con, range_checks, outer_phi, inner_iters_actual_int,
                              inner_phi, iv_add, inner_head);
  // Peel one iteration of the loop and use the safepoint at the end
  // of the peeled iteration to insert Parse Predicates. If no well
  // positioned safepoint peel to guarantee a safepoint in the outer
  // loop.
  if (safepoint != nullptr || !loop->_has_call) {
    old_new.clear();
    do_peeling(loop, old_new);
  } else {
    C->set_major_progress();
  }

  if (safepoint != nullptr) {
    SafePointNode* cloned_sfpt = old_new[safepoint->_idx]->as_SafePoint();

    if (UseLoopPredicate) {
      add_parse_predicate(Deoptimization::Reason_predicate, inner_head, outer_ilt, cloned_sfpt);
    }
    if (UseProfiledLoopPredicate) {
      add_parse_predicate(Deoptimization::Reason_profile_predicate, inner_head, outer_ilt, cloned_sfpt);
    }
    add_parse_predicate(Deoptimization::Reason_loop_limit_check, inner_head, outer_ilt, cloned_sfpt);
  }

#ifndef PRODUCT
  if (bt == T_LONG) {
    Atomic::inc(&_long_loop_nests);
  }
#endif

  inner_head->mark_loop_nest_inner_loop();
  outer_head->mark_loop_nest_outer_loop();

  return true;
}

int PhaseIdealLoop::extract_long_range_checks(const IdealLoopTree* loop, jint stride_con, int iters_limit, PhiNode* phi,
                                              Node_List& range_checks) {
  const jlong min_iters = 2;
  jlong reduced_iters_limit = iters_limit;
  jlong original_iters_limit = iters_limit;
  for (uint i = 0; i < loop->_body.size(); i++) {
    Node* c = loop->_body.at(i);
    if (c->is_IfProj() && c->in(0)->is_RangeCheck()) {
      IfProjNode* if_proj = c->as_IfProj();
      CallStaticJavaNode* call = if_proj->is_uncommon_trap_if_pattern();
      if (call != nullptr) {
        Node* range = nullptr;
        Node* offset = nullptr;
        jlong scale = 0;
        if (loop->is_range_check_if(if_proj, this, T_LONG, phi, range, offset, scale) &&
            loop->is_invariant(range) && loop->is_invariant(offset) &&
            scale != min_jlong &&
            original_iters_limit / ABS(scale) >= min_iters * ABS(stride_con)) {
          assert(scale == (jint)scale, "scale should be an int");
          reduced_iters_limit = MIN2(reduced_iters_limit, original_iters_limit/ABS(scale));
          range_checks.push(c);
        }
      }
    }
  }

  return checked_cast<int>(reduced_iters_limit);
}

// One execution of the inner loop covers a sub-range of the entire iteration range of the loop: [A,Z), aka [A=init,
// Z=limit). If the loop has at least one trip (which is the case here), the iteration variable i always takes A as its
// first value, followed by A+S (S is the stride), next A+2S, etc. The limit is exclusive, so that the final value B of
// i is never Z.  It will be B=Z-1 if S=1, or B=Z+1 if S=-1.

// If |S|>1 the formula for the last value B would require a floor operation, specifically B=floor((Z-sgn(S)-A)/S)*S+A,
// which is B=Z-sgn(S)U for some U in [1,|S|].  So when S>0, i ranges as i:[A,Z) or i:[A,B=Z-U], or else (in reverse)
// as i:(Z,A] or i:[B=Z+U,A].  It will become important to reason about this inclusive range [A,B] or [B,A].

// Within the loop there may be many range checks.  Each such range check (R.C.) is of the form 0 <= i*K+L < R, where K
// is a scale factor applied to the loop iteration variable i, and L is some offset; K, L, and R are loop-invariant.
// Because R is never negative (see below), this check can always be simplified to an unsigned check i*K+L <u R.

// When a long loop over a 64-bit variable i (outer_iv) is decomposed into a series of shorter sub-loops over a 32-bit
// variable j (inner_iv), j ranges over a shorter interval j:[0,B_2] or [0,Z_2) (assuming S > 0), where the limit is
// chosen to prevent various cases of 32-bit overflow (including multiplications j*K below).  In the sub-loop the
// logical value i is offset from j by a 64-bit constant C, so i ranges in i:C+[0,Z_2).

// For S<0, j ranges (in reverse!) through j:[-|B_2|,0] or (-|Z_2|,0].  For either sign of S, we can say i=j+C and j
// ranges through 32-bit ranges [A_2,B_2] or [B_2,A_2] (A_2=0 of course).

// The disjoint union of all the C+[A_2,B_2] ranges from the sub-loops must be identical to the whole range [A,B].
// Assuming S>0, the first C must be A itself, and the next C value is the previous C+B_2, plus S.  If |S|=1, the next
// C value is also the previous C+Z_2.  In each sub-loop, j counts from j=A_2=0 and i counts from C+0 and exits at
// j=B_2 (i=C+B_2), just before it gets to i=C+Z_2.  Both i and j count up (from C and 0) if S>0; otherwise they count
// down (from C and 0 again).

// Returning to range checks, we see that each i*K+L <u R expands to (C+j)*K+L <u R, or j*K+Q <u R, where Q=(C*K+L).
// (Recall that K and L and R are loop-invariant scale, offset and range values for a particular R.C.)  This is still a
// 64-bit comparison, so the range check elimination logic will not apply to it.  (The R.C.E. transforms operate only on
// 32-bit indexes and comparisons, because they use 64-bit temporary values to avoid overflow; see
// PhaseIdealLoop::add_constraint.)

// We must transform this comparison so that it gets the same answer, but by means of a 32-bit R.C. (using j not i) of
// the form j*K+L_2 <u32 R_2.  Note that L_2 and R_2 must be loop-invariant, but only with respect to the sub-loop.  Thus, the
// problem reduces to computing values for L_2 and R_2 (for each R.C. in the loop) in the loop header for the sub-loop.
// Then the standard R.C.E. transforms can take those as inputs and further compute the necessary minimum and maximum
// values for the 32-bit counter j within which the range checks can be eliminated.

// So, given j*K+Q <u R, we need to find some j*K+L_2 <u32 R_2, where L_2 and R_2 fit in 32 bits, and the 32-bit operations do
// not overflow. We also need to cover the cases where i*K+L (= j*K+Q) overflows to a 64-bit negative, since that is
// allowed as an input to the R.C., as long as the R.C. as a whole fails.

// If 32-bit multiplication j*K might overflow, we adjust the sub-loop limit Z_2 closer to zero to reduce j's range.

// For each R.C. j*K+Q <u32 R, the range of mathematical values of j*K+Q in the sub-loop is [Q_min, Q_max], where
// Q_min=Q and Q_max=B_2*K+Q (if S>0 and K>0), Q_min=A_2*K+Q and Q_max=Q (if S<0 and K>0),
// Q_min=B_2*K+Q and Q_max=Q if (S>0 and K<0), Q_min=Q and Q_max=A_2*K+Q (if S<0 and K<0)

// Note that the first R.C. value is always Q=(S*K>0 ? Q_min : Q_max).  Also Q_{min,max} = Q + {min,max}(A_2*K,B_2*K).
// If S*K>0 then, as the loop iterations progress, each R.C. value i*K+L = j*K+Q goes up from Q=Q_min towards Q_max.
// If S*K<0 then j*K+Q starts at Q=Q_max and goes down towards Q_min.

// Case A: Some Negatives (but no overflow).
// Number line:
// |s64_min   .    .    .    0    .    .    .   s64_max|
// |    .  Q_min..Q_max .    0    .    .    .     .    |  s64 negative
// |    .     .    .    .    R=0  R<   R<   R<    R<   |  (against R values)
// |    .     .    .  Q_min..0..Q_max  .    .     .    |  small mixed
// |    .     .    .    .    R    R    R<   R<    R<   |  (against R values)
//
// R values which are out of range (>Q_max+1) are reduced to max(0,Q_max+1).  They are marked on the number line as R<.
//
// So, if Q_min <s64 0, then use this test:
// j*K + s32_trunc(Q_min) <u32 clamp(R, 0, Q_max+1) if S*K>0 (R.C.E. steps upward)
// j*K + s32_trunc(Q_max) <u32 clamp(R, 0, Q_max+1) if S*K<0 (R.C.E. steps downward)
// Both formulas reduce to adding j*K to the 32-bit truncated value of the first R.C. expression value, Q:
// j*K + s32_trunc(Q) <u32 clamp(R, 0, Q_max+1) for all S,K

// If the 32-bit truncation loses information, no harm is done, since certainly the clamp also will return R_2=zero.

// Case B: No Negatives.
// Number line:
// |s64_min   .    .    .    0    .    .    .   s64_max|
// |    .     .    .    .    0 Q_min..Q_max .     .    |  small positive
// |    .     .    .    .    R>   R    R    R<    R<   |  (against R values)
// |    .     .    .    .    0    . Q_min..Q_max  .    |  s64 positive
// |    .     .    .    .    R>   R>   R    R     R<   |  (against R values)
//
// R values which are out of range (<Q_min or >Q_max+1) are reduced as marked: R> up to Q_min, R< down to Q_max+1.
// Then the whole comparison is shifted left by Q_min, so it can take place at zero, which is a nice 32-bit value.
//
// So, if both Q_min, Q_max+1 >=s64 0, then use this test:
// j*K + 0         <u32 clamp(R, Q_min, Q_max+1) - Q_min if S*K>0
// More generally:
// j*K + Q - Q_min <u32 clamp(R, Q_min, Q_max+1) - Q_min for all S,K

// Case C: Overflow in the 64-bit domain
// Number line:
// |..Q_max-2^64   .    .    0    .    .    .   Q_min..|  s64 overflow
// |    .     .    .    .    R>   R>   R>   R>    R    |  (against R values)
//
// In this case, Q_min >s64 Q_max+1, even though the mathematical values of Q_min and Q_max+1 are correctly ordered.
// The formulas from the previous case can be used, except that the bad upper bound Q_max is replaced by max_jlong.
// (In fact, we could use any replacement bound from R to max_jlong inclusive, as the input to the clamp function.)
//
// So if Q_min >=s64 0 but Q_max+1 <s64 0, use this test:
// j*K + 0         <u32 clamp(R, Q_min, max_jlong) - Q_min if S*K>0
// More generally:
// j*K + Q - Q_min <u32 clamp(R, Q_min, max_jlong) - Q_min for all S,K
//
// Dropping the bad bound means only Q_min is used to reduce the range of R:
// j*K + Q - Q_min <u32 max(Q_min, R) - Q_min for all S,K
//
// Here the clamp function is a 64-bit min/max that reduces the dynamic range of its R operand to the required [L,H]:
//     clamp(X, L, H) := max(L, min(X, H))
// When degenerately L > H, it returns L not H.
//
// All of the formulas above can be merged into a single one:
//     L_clamp = Q_min < 0 ? 0 : Q_min        --whether and how far to left-shift
//     H_clamp = Q_max+1 < Q_min ? max_jlong : Q_max+1
//             = Q_max+1 < 0 && Q_min >= 0 ? max_jlong : Q_max+1
//     Q_first = Q = (S*K>0 ? Q_min : Q_max) = (C*K+L)
//     R_clamp = clamp(R, L_clamp, H_clamp)   --reduced dynamic range
//     replacement R.C.:
//       j*K + Q_first - L_clamp <u32 R_clamp - L_clamp
//     or equivalently:
//       j*K + L_2 <u32 R_2
//     where
//       L_2 = Q_first - L_clamp
//       R_2 = R_clamp - L_clamp
//
// Note on why R is never negative:
//
// Various details of this transformation would break badly if R could be negative, so this transformation only
// operates after obtaining hard evidence that R<0 is impossible.  For example, if R comes from a LoadRange node, we
// know R cannot be negative.  For explicit checks (of both int and long) a proof is constructed in
// inline_preconditions_checkIndex, which triggers an uncommon trap if R<0, then wraps R in a ConstraintCastNode with a
// non-negative type.  Later on, when IdealLoopTree::is_range_check_if looks for an optimizable R.C., it checks that
// the type of that R node is non-negative.  Any "wild" R node that could be negative is not treated as an optimizable
// R.C., but R values from a.length and inside checkIndex are good to go.
//
void PhaseIdealLoop::transform_long_range_checks(int stride_con, const Node_List &range_checks, Node* outer_phi,
                                                 Node* inner_iters_actual_int, Node* inner_phi,
                                                 Node* iv_add, LoopNode* inner_head) {
  Node* long_zero = _igvn.longcon(0);
  set_ctrl(long_zero, C->root());
  Node* int_zero = _igvn.intcon(0);
  set_ctrl(int_zero, this->C->root());
  Node* long_one = _igvn.longcon(1);
  set_ctrl(long_one, this->C->root());
  Node* int_stride = _igvn.intcon(checked_cast<int>(stride_con));
  set_ctrl(int_stride, this->C->root());

  for (uint i = 0; i < range_checks.size(); i++) {
    ProjNode* proj = range_checks.at(i)->as_Proj();
    ProjNode* unc_proj = proj->other_if_proj();
    RangeCheckNode* rc = proj->in(0)->as_RangeCheck();
    jlong scale = 0;
    Node* offset = nullptr;
    Node* rc_bol = rc->in(1);
    Node* rc_cmp = rc_bol->in(1);
    if (rc_cmp->Opcode() == Op_CmpU) {
      // could be shared and have already been taken care of
      continue;
    }
    bool short_scale = false;
    bool ok = is_scaled_iv_plus_offset(rc_cmp->in(1), iv_add, T_LONG, &scale, &offset, &short_scale);
    assert(ok, "inconsistent: was tested before");
    Node* range = rc_cmp->in(2);
    Node* c = rc->in(0);
    Node* entry_control = inner_head->in(LoopNode::EntryControl);

    Node* R = range;
    Node* K = _igvn.longcon(scale);
    set_ctrl(K, this->C->root());

    Node* L = offset;

    if (short_scale) {
      // This converts:
      // (int)i*K + L <u64 R
      // with K an int into:
      // i*(long)K + L <u64 unsigned_min((long)max_jint + L + 1, R)
      // to protect against an overflow of (int)i*K
      //
      // Because if (int)i*K overflows, there are K,L where:
      // (int)i*K + L <u64 R is false because (int)i*K+L overflows to a negative which becomes a huge u64 value.
      // But if i*(long)K + L is >u64 (long)max_jint and still is <u64 R, then
      // i*(long)K + L <u64 R is true.
      //
      // As a consequence simply converting i*K + L <u64 R to i*(long)K + L <u64 R could cause incorrect execution.
      //
      // It's always true that:
      // (int)i*K <u64 (long)max_jint + 1
      // which implies (int)i*K + L <u64 (long)max_jint + 1 + L
      // As a consequence:
      // i*(long)K + L <u64 unsigned_min((long)max_jint + L + 1, R)
      // is always false in case of overflow of i*K
      //
      // Note, there are also K,L where i*K overflows and
      // i*K + L <u64 R is true, but
      // i*(long)K + L <u64 unsigned_min((long)max_jint + L + 1, R) is false
      // So this transformation could cause spurious deoptimizations and failed range check elimination
      // (but not incorrect execution) for unlikely corner cases with overflow.
      // If this causes problems in practice, we could maybe direct execution to a post-loop, instead of deoptimizing.
      Node* max_jint_plus_one_long = _igvn.longcon((jlong)max_jint + 1);
      set_ctrl(max_jint_plus_one_long, C->root());
      Node* max_range = new AddLNode(max_jint_plus_one_long, L);
      register_new_node(max_range, entry_control);
      R = MaxNode::unsigned_min(R, max_range, TypeLong::POS, _igvn);
      set_subtree_ctrl(R, true);
    }

    Node* C = outer_phi;

    // Start with 64-bit values:
    //   i*K + L <u64 R
    //   (C+j)*K + L <u64 R
    //   j*K + Q <u64 R    where Q = Q_first = C*K+L
    Node* Q_first = new MulLNode(C, K);
    register_new_node(Q_first, entry_control);
    Q_first = new AddLNode(Q_first, L);
    register_new_node(Q_first, entry_control);

    // Compute endpoints of the range of values j*K + Q.
    //  Q_min = (j=0)*K + Q;  Q_max = (j=B_2)*K + Q
    Node* Q_min = Q_first;

    // Compute the exact ending value B_2 (which is really A_2 if S < 0)
    Node* B_2 = new LoopLimitNode(this->C, int_zero, inner_iters_actual_int, int_stride);
    register_new_node(B_2, entry_control);
    B_2 = new SubINode(B_2, int_stride);
    register_new_node(B_2, entry_control);
    B_2 = new ConvI2LNode(B_2);
    register_new_node(B_2, entry_control);

    Node* Q_max = new MulLNode(B_2, K);
    register_new_node(Q_max, entry_control);
    Q_max = new AddLNode(Q_max, Q_first);
    register_new_node(Q_max, entry_control);

    if (scale * stride_con < 0) {
      swap(Q_min, Q_max);
    }
    // Now, mathematically, Q_max > Q_min, and they are close enough so that (Q_max-Q_min) fits in 32 bits.

    // L_clamp = Q_min < 0 ? 0 : Q_min
    Node* Q_min_cmp = new CmpLNode(Q_min, long_zero);
    register_new_node(Q_min_cmp, entry_control);
    Node* Q_min_bool = new BoolNode(Q_min_cmp, BoolTest::lt);
    register_new_node(Q_min_bool, entry_control);
    Node* L_clamp = new CMoveLNode(Q_min_bool, Q_min, long_zero, TypeLong::LONG);
    register_new_node(L_clamp, entry_control);
    // (This could also be coded bitwise as L_clamp = Q_min & ~(Q_min>>63).)

    Node* Q_max_plus_one = new AddLNode(Q_max, long_one);
    register_new_node(Q_max_plus_one, entry_control);

    // H_clamp = Q_max+1 < Q_min ? max_jlong : Q_max+1
    // (Because Q_min and Q_max are close, the overflow check could also be encoded as Q_max+1 < 0 & Q_min >= 0.)
    Node* max_jlong_long = _igvn.longcon(max_jlong);
    set_ctrl(max_jlong_long, this->C->root());
    Node* Q_max_cmp = new CmpLNode(Q_max_plus_one, Q_min);
    register_new_node(Q_max_cmp, entry_control);
    Node* Q_max_bool = new BoolNode(Q_max_cmp, BoolTest::lt);
    register_new_node(Q_max_bool, entry_control);
    Node* H_clamp = new CMoveLNode(Q_max_bool, Q_max_plus_one, max_jlong_long, TypeLong::LONG);
    register_new_node(H_clamp, entry_control);
    // (This could also be coded bitwise as H_clamp = ((Q_max+1)<<1 | M)>>>1 where M = (Q_max+1)>>63 & ~Q_min>>63.)

    // R_2 = clamp(R, L_clamp, H_clamp) - L_clamp
    // that is:  R_2 = clamp(R, L_clamp=0, H_clamp=Q_max)      if Q_min < 0
    // or else:  R_2 = clamp(R, L_clamp,   H_clamp) - Q_min    if Q_min >= 0
    // and also: R_2 = clamp(R, L_clamp,   Q_max+1) - L_clamp  if Q_min < Q_max+1 (no overflow)
    // or else:  R_2 = clamp(R, L_clamp, *no limit*)- L_clamp  if Q_max+1 < Q_min (overflow)
    Node* R_2 = clamp(R, L_clamp, H_clamp);
    R_2 = new SubLNode(R_2, L_clamp);
    register_new_node(R_2, entry_control);
    R_2 = new ConvL2INode(R_2, TypeInt::POS);
    register_new_node(R_2, entry_control);

    // L_2 = Q_first - L_clamp
    // We are subtracting L_clamp from both sides of the <u32 comparison.
    // If S*K>0, then Q_first == 0 and the R.C. expression at -L_clamp and steps upward to Q_max-L_clamp.
    // If S*K<0, then Q_first != 0 and the R.C. expression starts high and steps downward to Q_min-L_clamp.
    Node* L_2 = new SubLNode(Q_first, L_clamp);
    register_new_node(L_2, entry_control);
    L_2 = new ConvL2INode(L_2, TypeInt::INT);
    register_new_node(L_2, entry_control);

    // Transform the range check using the computed values L_2/R_2
    // from:   i*K + L   <u64 R
    // to:     j*K + L_2 <u32 R_2
    // that is:
    //   (j*K + Q_first) - L_clamp <u32 clamp(R, L_clamp, H_clamp) - L_clamp
    K = _igvn.intcon(checked_cast<int>(scale));
    set_ctrl(K, this->C->root());
    Node* scaled_iv = new MulINode(inner_phi, K);
    register_new_node(scaled_iv, c);
    Node* scaled_iv_plus_offset = new AddINode(scaled_iv, L_2);
    register_new_node(scaled_iv_plus_offset, c);

    Node* new_rc_cmp = new CmpUNode(scaled_iv_plus_offset, R_2);
    register_new_node(new_rc_cmp, c);

    _igvn.replace_input_of(rc_bol, 1, new_rc_cmp);
  }
}

Node* PhaseIdealLoop::clamp(Node* R, Node* L, Node* H) {
  Node* min = MaxNode::signed_min(R, H, TypeLong::LONG, _igvn);
  set_subtree_ctrl(min, true);
  Node* max = MaxNode::signed_max(L, min, TypeLong::LONG, _igvn);
  set_subtree_ctrl(max, true);
  return max;
}

LoopNode* PhaseIdealLoop::create_inner_head(IdealLoopTree* loop, BaseCountedLoopNode* head,
                                            IfNode* exit_test) {
  LoopNode* new_inner_head = new LoopNode(head->in(1), head->in(2));
  IfNode* new_inner_exit = new IfNode(exit_test->in(0), exit_test->in(1), exit_test->_prob, exit_test->_fcnt);
  _igvn.register_new_node_with_optimizer(new_inner_head);
  _igvn.register_new_node_with_optimizer(new_inner_exit);
  loop->_body.push(new_inner_head);
  loop->_body.push(new_inner_exit);
  loop->_body.yank(head);
  loop->_body.yank(exit_test);
  set_loop(new_inner_head, loop);
  set_loop(new_inner_exit, loop);
  set_idom(new_inner_head, idom(head), dom_depth(head));
  set_idom(new_inner_exit, idom(exit_test), dom_depth(exit_test));
  lazy_replace(head, new_inner_head);
  lazy_replace(exit_test, new_inner_exit);
  loop->_head = new_inner_head;
  return new_inner_head;
}

#ifdef ASSERT
void PhaseIdealLoop::check_counted_loop_shape(IdealLoopTree* loop, Node* x, BasicType bt) {
  Node* back_control = loop_exit_control(x, loop);
  assert(back_control != nullptr, "no back control");

  BoolTest::mask mask = BoolTest::illegal;
  float cl_prob = 0;
  Node* incr = nullptr;
  Node* limit = nullptr;

  Node* cmp = loop_exit_test(back_control, loop, incr, limit, mask, cl_prob);
  assert(cmp != nullptr && cmp->Opcode() == Op_Cmp(bt), "no exit test");

  Node* phi_incr = nullptr;
  incr = loop_iv_incr(incr, x, loop, phi_incr);
  assert(incr != nullptr && incr->Opcode() == Op_Add(bt), "no incr");

  Node* xphi = nullptr;
  Node* stride = loop_iv_stride(incr, loop, xphi);

  assert(stride != nullptr, "no stride");

  PhiNode* phi = loop_iv_phi(xphi, phi_incr, x, loop);

  assert(phi != nullptr && phi->in(LoopNode::LoopBackControl) == incr, "No phi");

  jlong stride_con = stride->get_integer_as_long(bt);

  assert(condition_stride_ok(mask, stride_con), "illegal condition");

  assert(mask != BoolTest::ne, "unexpected condition");
  assert(phi_incr == nullptr, "bad loop shape");
  assert(cmp->in(1) == incr, "bad exit test shape");

  // Safepoint on backedge not supported
  assert(x->in(LoopNode::LoopBackControl)->Opcode() != Op_SafePoint, "no safepoint on backedge");
}
#endif

#ifdef ASSERT
// convert an int counted loop to a long counted to stress handling of
// long counted loops
bool PhaseIdealLoop::convert_to_long_loop(Node* cmp, Node* phi, IdealLoopTree* loop) {
  Unique_Node_List iv_nodes;
  Node_List old_new;
  iv_nodes.push(cmp);
  bool failed = false;

  for (uint i = 0; i < iv_nodes.size() && !failed; i++) {
    Node* n = iv_nodes.at(i);
    switch(n->Opcode()) {
      case Op_Phi: {
        Node* clone = new PhiNode(n->in(0), TypeLong::LONG);
        old_new.map(n->_idx, clone);
        break;
      }
      case Op_CmpI: {
        Node* clone = new CmpLNode(nullptr, nullptr);
        old_new.map(n->_idx, clone);
        break;
      }
      case Op_AddI: {
        Node* clone = new AddLNode(nullptr, nullptr);
        old_new.map(n->_idx, clone);
        break;
      }
      case Op_CastII: {
        failed = true;
        break;
      }
      default:
        DEBUG_ONLY(n->dump());
        fatal("unexpected");
    }

    for (uint i = 1; i < n->req(); i++) {
      Node* in = n->in(i);
      if (in == nullptr) {
        continue;
      }
      if (loop->is_member(get_loop(get_ctrl(in)))) {
        iv_nodes.push(in);
      }
    }
  }

  if (failed) {
    for (uint i = 0; i < iv_nodes.size(); i++) {
      Node* n = iv_nodes.at(i);
      Node* clone = old_new[n->_idx];
      if (clone != nullptr) {
        _igvn.remove_dead_node(clone);
      }
    }
    return false;
  }

  for (uint i = 0; i < iv_nodes.size(); i++) {
    Node* n = iv_nodes.at(i);
    Node* clone = old_new[n->_idx];
    for (uint i = 1; i < n->req(); i++) {
      Node* in = n->in(i);
      if (in == nullptr) {
        continue;
      }
      Node* in_clone = old_new[in->_idx];
      if (in_clone == nullptr) {
        assert(_igvn.type(in)->isa_int(), "");
        in_clone = new ConvI2LNode(in);
        _igvn.register_new_node_with_optimizer(in_clone);
        set_subtree_ctrl(in_clone, false);
      }
      if (in_clone->in(0) == nullptr) {
        in_clone->set_req(0, C->top());
        clone->set_req(i, in_clone);
        in_clone->set_req(0, nullptr);
      } else {
        clone->set_req(i, in_clone);
      }
    }
    _igvn.register_new_node_with_optimizer(clone);
  }
  set_ctrl(old_new[phi->_idx], phi->in(0));

  for (uint i = 0; i < iv_nodes.size(); i++) {
    Node* n = iv_nodes.at(i);
    Node* clone = old_new[n->_idx];
    set_subtree_ctrl(clone, false);
    Node* m = n->Opcode() == Op_CmpI ? clone : nullptr;
    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* u = n->fast_out(i);
      if (iv_nodes.member(u)) {
        continue;
      }
      if (m == nullptr) {
        m = new ConvL2INode(clone);
        _igvn.register_new_node_with_optimizer(m);
        set_subtree_ctrl(m, false);
      }
      _igvn.rehash_node_delayed(u);
      int nb = u->replace_edge(n, m, &_igvn);
      --i, imax -= nb;
    }
  }
  return true;
}
#endif

//------------------------------is_counted_loop--------------------------------
bool PhaseIdealLoop::is_counted_loop(Node* x, IdealLoopTree*&loop, BasicType iv_bt) {
  PhaseGVN *gvn = &_igvn;

  Node* back_control = loop_exit_control(x, loop);
  if (back_control == nullptr) {
    return false;
  }

  BoolTest::mask bt = BoolTest::illegal;
  float cl_prob = 0;
  Node* incr = nullptr;
  Node* limit = nullptr;
  Node* cmp = loop_exit_test(back_control, loop, incr, limit, bt, cl_prob);
  if (cmp == nullptr || cmp->Opcode() != Op_Cmp(iv_bt)) {
    return false; // Avoid pointer & float & 64-bit compares
  }

  // Trip-counter increment must be commutative & associative.
  if (incr->Opcode() == Op_Cast(iv_bt)) {
    incr = incr->in(1);
  }

  Node* phi_incr = nullptr;
  incr = loop_iv_incr(incr, x, loop, phi_incr);
  if (incr == nullptr) {
    return false;
  }

  Node* trunc1 = nullptr;
  Node* trunc2 = nullptr;
  const TypeInteger* iv_trunc_t = nullptr;
  Node* orig_incr = incr;
  if (!(incr = CountedLoopNode::match_incr_with_optional_truncation(incr, &trunc1, &trunc2, &iv_trunc_t, iv_bt))) {
    return false; // Funny increment opcode
  }
  assert(incr->Opcode() == Op_Add(iv_bt), "wrong increment code");

  Node* xphi = nullptr;
  Node* stride = loop_iv_stride(incr, loop, xphi);

  if (stride == nullptr) {
    return false;
  }

  if (xphi->Opcode() == Op_Cast(iv_bt)) {
    xphi = xphi->in(1);
  }

  // Stride must be constant
  jlong stride_con = stride->get_integer_as_long(iv_bt);
  assert(stride_con != 0, "missed some peephole opt");

  PhiNode* phi = loop_iv_phi(xphi, phi_incr, x, loop);

  if (phi == nullptr ||
      (trunc1 == nullptr && phi->in(LoopNode::LoopBackControl) != incr) ||
      (trunc1 != nullptr && phi->in(LoopNode::LoopBackControl) != trunc1)) {
    return false;
  }

  Node* iftrue = back_control;
  uint iftrue_op = iftrue->Opcode();
  Node* iff = iftrue->in(0);
  BoolNode* test = iff->in(1)->as_Bool();

  const TypeInteger* limit_t = gvn->type(limit)->is_integer(iv_bt);
  if (trunc1 != nullptr) {
    // When there is a truncation, we must be sure that after the truncation
    // the trip counter will end up higher than the limit, otherwise we are looking
    // at an endless loop. Can happen with range checks.

    // Example:
    // int i = 0;
    // while (true)
    //    sum + = array[i];
    //    i++;
    //    i = i && 0x7fff;
    //  }
    //
    // If the array is shorter than 0x8000 this exits through a AIOOB
    //  - Counted loop transformation is ok
    // If the array is longer then this is an endless loop
    //  - No transformation can be done.

    const TypeInteger* incr_t = gvn->type(orig_incr)->is_integer(iv_bt);
    if (limit_t->hi_as_long() > incr_t->hi_as_long()) {
      // if the limit can have a higher value than the increment (before the phi)
      return false;
    }
  }

  Node *init_trip = phi->in(LoopNode::EntryControl);

  // If iv trunc type is smaller than int, check for possible wrap.
  if (!TypeInteger::bottom(iv_bt)->higher_equal(iv_trunc_t)) {
    assert(trunc1 != nullptr, "must have found some truncation");

    // Get a better type for the phi (filtered thru if's)
    const TypeInteger* phi_ft = filtered_type(phi);

    // Can iv take on a value that will wrap?
    //
    // Ensure iv's limit is not within "stride" of the wrap value.
    //
    // Example for "short" type
    //    Truncation ensures value is in the range -32768..32767 (iv_trunc_t)
    //    If the stride is +10, then the last value of the induction
    //    variable before the increment (phi_ft->_hi) must be
    //    <= 32767 - 10 and (phi_ft->_lo) must be >= -32768 to
    //    ensure no truncation occurs after the increment.

    if (stride_con > 0) {
      if (iv_trunc_t->hi_as_long() - phi_ft->hi_as_long() < stride_con ||
          iv_trunc_t->lo_as_long() > phi_ft->lo_as_long()) {
        return false;  // truncation may occur
      }
    } else if (stride_con < 0) {
      if (iv_trunc_t->lo_as_long() - phi_ft->lo_as_long() > stride_con ||
          iv_trunc_t->hi_as_long() < phi_ft->hi_as_long()) {
        return false;  // truncation may occur
      }
    }
    // No possibility of wrap so truncation can be discarded
    // Promote iv type to Int
  } else {
    assert(trunc1 == nullptr && trunc2 == nullptr, "no truncation for int");
  }

  if (!condition_stride_ok(bt, stride_con)) {
    return false;
  }

  const TypeInteger* init_t = gvn->type(init_trip)->is_integer(iv_bt);

  if (stride_con > 0) {
    if (init_t->lo_as_long() > max_signed_integer(iv_bt) - stride_con) {
      return false; // cyclic loop
    }
  } else {
    if (init_t->hi_as_long() < min_signed_integer(iv_bt) - stride_con) {
      return false; // cyclic loop
    }
  }

  if (phi_incr != nullptr && bt != BoolTest::ne) {
    // check if there is a possibility of IV overflowing after the first increment
    if (stride_con > 0) {
      if (init_t->hi_as_long() > max_signed_integer(iv_bt) - stride_con) {
        return false;
      }
    } else {
      if (init_t->lo_as_long() < min_signed_integer(iv_bt) - stride_con) {
        return false;
      }
    }
  }

  // =================================================
  // ---- SUCCESS!   Found A Trip-Counted Loop!  -----
  //

  if (x->Opcode() == Op_Region) {
    // x has not yet been transformed to Loop or LongCountedLoop.
    // This should only happen if we are inside an infinite loop.
    // It happens like this:
    //   build_loop_tree -> do not attach infinite loop and nested loops
    //   beautify_loops  -> does not transform the infinite and nested loops to LoopNode, because not attached yet
    //   build_loop_tree -> find and attach infinite and nested loops
    //   counted_loop    -> nested Regions are not yet transformed to LoopNodes, we land here
    assert(x->as_Region()->is_in_infinite_subgraph(),
           "x can only be a Region and not Loop if inside infinite loop");
    // Come back later when Region is transformed to LoopNode
    return false;
  }

  assert(x->Opcode() == Op_Loop || x->Opcode() == Op_LongCountedLoop, "regular loops only");
  C->print_method(PHASE_BEFORE_CLOOPS, 3, x);

  // ===================================================
  // We can only convert this loop to a counted loop if we can guarantee that the iv phi will never overflow at runtime.
  // This is an implicit assumption taken by some loop optimizations. We therefore must ensure this property at all cost.
  // At this point, we've already excluded some trivial cases where an overflow could have been proven statically.
  // But even though we cannot prove that an overflow will *not* happen, we still want to speculatively convert this loop
  // to a counted loop. This can be achieved by adding additional iv phi overflow checks before the loop. If they fail,
  // we trap and resume execution before the loop without having executed any iteration of the loop, yet.
  //
  // These additional iv phi overflow checks can be inserted as Loop Limit Check Predicates above the Loop Limit Check
  // Parse Predicate which captures a JVM state just before the entry of the loop. If there is no such Parse Predicate,
  // we cannot generate a Loop Limit Check Predicate and thus cannot speculatively convert the loop to a counted loop.
  //
  // In the following, we only focus on int loops with stride > 0 to keep things simple. The argumentation and proof
  // for stride < 0 is analogously. For long loops, we would replace max_int with max_long.
  //
  //
  // The loop to be converted does not always need to have the often used shape:
  //
  //                                                 i = init
  //     i = init                                loop:
  //     do {                                        ...
  //         // ...               equivalent         i+=stride
  //         i+=stride               <==>            if (i < limit)
  //     } while (i < limit);                          goto loop
  //                                             exit:
  //                                                 ...
  //
  // where the loop exit check uses the post-incremented iv phi and a '<'-operator.
  //
  // We could also have '<='-operator (or '>='-operator for negative strides) or use the pre-incremented iv phi value
  // in the loop exit check:
  //
  //         i = init
  //     loop:
  //         ...
  //         if (i <= limit)
  //             i+=stride
  //             goto loop
  //     exit:
  //         ...
  //
  // Let's define the following terms:
  // - iv_pre_i: The pre-incremented iv phi before the i-th iteration.
  // - iv_post_i: The post-incremented iv phi after the i-th iteration.
  //
  // The iv_pre_i and iv_post_i have the following relation:
  //      iv_pre_i + stride = iv_post_i
  //
  // When converting a loop to a counted loop, we want to have a canonicalized loop exit check of the form:
  //     iv_post_i < adjusted_limit
  //
  // If that is not the case, we need to canonicalize the loop exit check by using different values for adjusted_limit:
  // (LE1) iv_post_i < limit: Already canonicalized. We can directly use limit as adjusted_limit.
  //           -> adjusted_limit = limit.
  // (LE2) iv_post_i <= limit:
  //           iv_post_i < limit + 1
  //           -> adjusted limit = limit + 1
  // (LE3) iv_pre_i < limit:
  //           iv_pre_i + stride < limit + stride
  //           iv_post_i < limit + stride
  //           -> adjusted_limit = limit + stride
  // (LE4) iv_pre_i <= limit:
  //           iv_pre_i < limit + 1
  //           iv_pre_i + stride < limit + stride + 1
  //           iv_post_i < limit + stride + 1
  //           -> adjusted_limit = limit + stride + 1
  //
  // Note that:
  //     (AL) limit <= adjusted_limit.
  //
  // The following loop invariant has to hold for counted loops with n iterations (i.e. loop exit check true after n-th
  // loop iteration) and a canonicalized loop exit check to guarantee that no iv_post_i over- or underflows:
  // (INV) For i = 1..n, min_int <= iv_post_i <= max_int
  //
  // To prove (INV), we require the following two conditions/assumptions:
  // (i): adjusted_limit - 1 + stride <= max_int
  // (ii): init < limit
  //
  // If we can prove (INV), we know that there can be no over- or underflow of any iv phi value. We prove (INV) by
  // induction by assuming (i) and (ii).
  //
  // Proof by Induction
  // ------------------
  // > Base case (i = 1): We show that (INV) holds after the first iteration:
  //     min_int <= iv_post_1 = init + stride <= max_int
  // Proof:
  //     First, we note that (ii) implies
  //         (iii) init <= limit - 1
  //     max_int >= adjusted_limit - 1 + stride   [using (i)]
  //             >= limit - 1 + stride            [using (AL)]
  //             >= init + stride                 [using (iii)]
  //             >= min_int                       [using stride > 0, no underflow]
  // Thus, no overflow happens after the first iteration and (INV) holds for i = 1.
  //
  // Note that to prove the base case we need (i) and (ii).
  //
  // > Induction Hypothesis (i = j, j > 1): Assume that (INV) holds after the j-th iteration:
  //     min_int <= iv_post_j <= max_int
  // > Step case (i = j + 1): We show that (INV) also holds after the j+1-th iteration:
  //     min_int <= iv_post_{j+1} = iv_post_j + stride <= max_int
  // Proof:
  // If iv_post_j >= adjusted_limit:
  //     We exit the loop after the j-th iteration, and we don't execute the j+1-th iteration anymore. Thus, there is
  //     also no iv_{j+1}. Since (INV) holds for iv_j, there is nothing left to prove.
  // If iv_post_j < adjusted_limit:
  //     First, we note that:
  //         (iv) iv_post_j <= adjusted_limit - 1
  //     max_int >= adjusted_limit - 1 + stride    [using (i)]
  //             >= iv_post_j + stride             [using (iv)]
  //             >= min_int                        [using stride > 0, no underflow]
  //
  // Note that to prove the step case we only need (i).
  //
  // Thus, by assuming (i) and (ii), we proved (INV).
  //
  //
  // It is therefore enough to add the following two Loop Limit Check Predicates to check assumptions (i) and (ii):
  //
  // (1) Loop Limit Check Predicate for (i):
  //     Using (i): adjusted_limit - 1 + stride <= max_int
  //
  //     This condition is now restated to use limit instead of adjusted_limit:
  //
  //     To prevent an overflow of adjusted_limit -1 + stride itself, we rewrite this check to
  //         max_int - stride + 1 >= adjusted_limit
  //     We can merge the two constants into
  //         canonicalized_correction = stride - 1
  //     which gives us
  //        max_int - canonicalized_correction >= adjusted_limit
  //
  //     To directly use limit instead of adjusted_limit in the predicate condition, we split adjusted_limit into:
  //         adjusted_limit = limit + limit_correction
  //     Since stride > 0 and limit_correction <= stride + 1, we can restate this with no over- or underflow into:
  //         max_int - canonicalized_correction - limit_correction >= limit
  //     Since canonicalized_correction and limit_correction are both constants, we can replace them with a new constant:
  //         final_correction = canonicalized_correction + limit_correction
  //     which gives us:
  //
  //     Final predicate condition:
  //         max_int - final_correction >= limit
  //
  // (2) Loop Limit Check Predicate for (ii):
  //     Using (ii): init < limit
  //
  //     This Loop Limit Check Predicate is not required if we can prove at compile time that either:
  //        (2.1) type(init) < type(limit)
  //             In this case, we know:
  //                 all possible values of init < all possible values of limit
  //             and we can skip the predicate.
  //
  //        (2.2) init < limit is already checked before (i.e. found as a dominating check)
  //            In this case, we do not need to re-check the condition and can skip the predicate.
  //            This is often found for while- and for-loops which have the following shape:
  //
  //                if (init < limit) { // Dominating test. Do not need the Loop Limit Check Predicate below.
  //                    i = init;
  //                    if (init >= limit) { trap(); } // Here we would insert the Loop Limit Check Predicate
  //                    do {
  //                        i += stride;
  //                    } while (i < limit);
  //                }
  //
  //        (2.3) init + stride <= max_int
  //            In this case, there is no overflow of the iv phi after the first loop iteration.
  //            In the proof of the base case above we showed that init + stride <= max_int by using assumption (ii):
  //                init < limit
  //            In the proof of the step case above, we did not need (ii) anymore. Therefore, if we already know at
  //            compile time that init + stride <= max_int then we have trivially proven the base case and that
  //            there is no overflow of the iv phi after the first iteration. In this case, we don't need to check (ii)
  //            again and can skip the predicate.


  // Accounting for (LE3) and (LE4) where we use pre-incremented phis in the loop exit check.
  const jlong limit_correction_for_pre_iv_exit_check = (phi_incr != nullptr) ? stride_con : 0;

  // Accounting for (LE2) and (LE4) where we use <= or >= in the loop exit check.
  const bool includes_limit = (bt == BoolTest::le || bt == BoolTest::ge);
  const jlong limit_correction_for_le_ge_exit_check = (includes_limit ? (stride_con > 0 ? 1 : -1) : 0);

  const jlong limit_correction = limit_correction_for_pre_iv_exit_check + limit_correction_for_le_ge_exit_check;
  const jlong canonicalized_correction = stride_con + (stride_con > 0 ? -1 : 1);
  const jlong final_correction = canonicalized_correction + limit_correction;

  int sov = check_stride_overflow(final_correction, limit_t, iv_bt);
  Node* init_control = x->in(LoopNode::EntryControl);

  // If sov==0, limit's type always satisfies the condition, for
  // example, when it is an array length.
  if (sov != 0) {
    if (sov < 0) {
      return false;  // Bailout: integer overflow is certain.
    }
    // (1) Loop Limit Check Predicate is required because we could not statically prove that
    //     limit + final_correction = adjusted_limit - 1 + stride <= max_int
    assert(!x->as_Loop()->is_loop_nest_inner_loop(), "loop was transformed");
    const Predicates predicates(init_control);
    const PredicateBlock* loop_limit_check_predicate_block = predicates.loop_limit_check_predicate_block();
    if (!loop_limit_check_predicate_block->has_parse_predicate()) {
      // The Loop Limit Check Parse Predicate is not generated if this method trapped here before.
#ifdef ASSERT
      if (TraceLoopLimitCheck) {
        tty->print("Missing Loop Limit Check Parse Predicate:");
        loop->dump_head();
        x->dump(1);
      }
#endif
      return false;
    }

    ParsePredicateNode* loop_limit_check_parse_predicate = loop_limit_check_predicate_block->parse_predicate();
    if (!is_dominator(get_ctrl(limit), loop_limit_check_parse_predicate->in(0))) {
      return false;
    }

    Node* cmp_limit;
    Node* bol;

    if (stride_con > 0) {
      cmp_limit = CmpNode::make(limit, _igvn.integercon(max_signed_integer(iv_bt) - final_correction, iv_bt), iv_bt);
      bol = new BoolNode(cmp_limit, BoolTest::le);
    } else {
      cmp_limit = CmpNode::make(limit, _igvn.integercon(min_signed_integer(iv_bt) - final_correction, iv_bt), iv_bt);
      bol = new BoolNode(cmp_limit, BoolTest::ge);
    }

    insert_loop_limit_check_predicate(init_control->as_IfTrue(), cmp_limit, bol);
  }

  // (2.3)
  const bool init_plus_stride_could_overflow =
          (stride_con > 0 && init_t->hi_as_long() > max_signed_integer(iv_bt) - stride_con) ||
          (stride_con < 0 && init_t->lo_as_long() < min_signed_integer(iv_bt) - stride_con);
  // (2.1)
  const bool init_gte_limit = (stride_con > 0 && init_t->hi_as_long() >= limit_t->lo_as_long()) ||
                              (stride_con < 0 && init_t->lo_as_long() <= limit_t->hi_as_long());

  if (init_gte_limit && // (2.1)
     ((bt == BoolTest::ne || init_plus_stride_could_overflow) && // (2.3)
      !has_dominating_loop_limit_check(init_trip, limit, stride_con, iv_bt, init_control))) { // (2.2)
    // (2) Iteration Loop Limit Check Predicate is required because neither (2.1), (2.2), nor (2.3) holds.
    // We use the following condition:
    // - stride > 0: init < limit
    // - stride < 0: init > limit
    //
    // This predicate is always required if we have a non-equal-operator in the loop exit check (where stride = 1 is
    // a requirement). We transform the loop exit check by using a less-than-operator. By doing so, we must always
    // check that init < limit. Otherwise, we could have a different number of iterations at runtime.

    const Predicates predicates(init_control);
    const PredicateBlock* loop_limit_check_predicate_block = predicates.loop_limit_check_predicate_block();
    if (!loop_limit_check_predicate_block->has_parse_predicate()) {
      // The Loop Limit Check Parse Predicate is not generated if this method trapped here before.
#ifdef ASSERT
      if (TraceLoopLimitCheck) {
        tty->print("Missing Loop Limit Check Parse Predicate:");
        loop->dump_head();
        x->dump(1);
      }
#endif
      return false;
    }

    ParsePredicateNode* loop_limit_check_parse_predicate = loop_limit_check_predicate_block->parse_predicate();
    Node* parse_predicate_entry = loop_limit_check_parse_predicate->in(0);
    if (!is_dominator(get_ctrl(limit), parse_predicate_entry) ||
        !is_dominator(get_ctrl(init_trip), parse_predicate_entry)) {
      return false;
    }

    Node* cmp_limit;
    Node* bol;

    if (stride_con > 0) {
      cmp_limit = CmpNode::make(init_trip, limit, iv_bt);
      bol = new BoolNode(cmp_limit, BoolTest::lt);
    } else {
      cmp_limit = CmpNode::make(init_trip, limit, iv_bt);
      bol = new BoolNode(cmp_limit, BoolTest::gt);
    }

    insert_loop_limit_check_predicate(init_control->as_IfTrue(), cmp_limit, bol);
  }

  if (bt == BoolTest::ne) {
    // Now we need to canonicalize the loop condition if it is 'ne'.
    assert(stride_con == 1 || stride_con == -1, "simple increment only - checked before");
    if (stride_con > 0) {
      // 'ne' can be replaced with 'lt' only when init < limit. This is ensured by the inserted predicate above.
      bt = BoolTest::lt;
    } else {
      assert(stride_con < 0, "must be");
      // 'ne' can be replaced with 'gt' only when init > limit. This is ensured by the inserted predicate above.
      bt = BoolTest::gt;
    }
  }

  Node* sfpt = nullptr;
  if (loop->_child == nullptr) {
    sfpt = find_safepoint(back_control, x, loop);
  } else {
    sfpt = iff->in(0);
    if (sfpt->Opcode() != Op_SafePoint) {
      sfpt = nullptr;
    }
  }

  if (x->in(LoopNode::LoopBackControl)->Opcode() == Op_SafePoint) {
    Node* backedge_sfpt = x->in(LoopNode::LoopBackControl);
    if (((iv_bt == T_INT && LoopStripMiningIter != 0) ||
         iv_bt == T_LONG) &&
        sfpt == nullptr) {
      // Leaving the safepoint on the backedge and creating a
      // CountedLoop will confuse optimizations. We can't move the
      // safepoint around because its jvm state wouldn't match a new
      // location. Give up on that loop.
      return false;
    }
    if (is_deleteable_safept(backedge_sfpt)) {
      lazy_replace(backedge_sfpt, iftrue);
      if (loop->_safepts != nullptr) {
        loop->_safepts->yank(backedge_sfpt);
      }
      loop->_tail = iftrue;
    }
  }


#ifdef ASSERT
  if (iv_bt == T_INT &&
      !x->as_Loop()->is_loop_nest_inner_loop() &&
      StressLongCountedLoop > 0 &&
      trunc1 == nullptr &&
      convert_to_long_loop(cmp, phi, loop)) {
    return false;
  }
#endif

  Node* adjusted_limit = limit;
  if (phi_incr != nullptr) {
    // If compare points directly to the phi we need to adjust
    // the compare so that it points to the incr. Limit have
    // to be adjusted to keep trip count the same and we
    // should avoid int overflow.
    //
    //   i = init; do {} while(i++ < limit);
    // is converted to
    //   i = init; do {} while(++i < limit+1);
    //
    adjusted_limit = gvn->transform(AddNode::make(limit, stride, iv_bt));
  }

  if (includes_limit) {
    // The limit check guaranties that 'limit <= (max_jint - stride)' so
    // we can convert 'i <= limit' to 'i < limit+1' since stride != 0.
    //
    Node* one = (stride_con > 0) ? gvn->integercon( 1, iv_bt) : gvn->integercon(-1, iv_bt);
    adjusted_limit = gvn->transform(AddNode::make(adjusted_limit, one, iv_bt));
    if (bt == BoolTest::le)
      bt = BoolTest::lt;
    else if (bt == BoolTest::ge)
      bt = BoolTest::gt;
    else
      ShouldNotReachHere();
  }
  set_subtree_ctrl(adjusted_limit, false);

  // Build a canonical trip test.
  // Clone code, as old values may be in use.
  incr = incr->clone();
  incr->set_req(1,phi);
  incr->set_req(2,stride);
  incr = _igvn.register_new_node_with_optimizer(incr);
  set_early_ctrl(incr, false);
  _igvn.rehash_node_delayed(phi);
  phi->set_req_X( LoopNode::LoopBackControl, incr, &_igvn );

  // If phi type is more restrictive than Int, raise to
  // Int to prevent (almost) infinite recursion in igvn
  // which can only handle integer types for constants or minint..maxint.
  if (!TypeInteger::bottom(iv_bt)->higher_equal(phi->bottom_type())) {
    Node* nphi = PhiNode::make(phi->in(0), phi->in(LoopNode::EntryControl), TypeInteger::bottom(iv_bt));
    nphi->set_req(LoopNode::LoopBackControl, phi->in(LoopNode::LoopBackControl));
    nphi = _igvn.register_new_node_with_optimizer(nphi);
    set_ctrl(nphi, get_ctrl(phi));
    _igvn.replace_node(phi, nphi);
    phi = nphi->as_Phi();
  }
  cmp = cmp->clone();
  cmp->set_req(1,incr);
  cmp->set_req(2, adjusted_limit);
  cmp = _igvn.register_new_node_with_optimizer(cmp);
  set_ctrl(cmp, iff->in(0));

  test = test->clone()->as_Bool();
  (*(BoolTest*)&test->_test)._test = bt;
  test->set_req(1,cmp);
  _igvn.register_new_node_with_optimizer(test);
  set_ctrl(test, iff->in(0));

  // Replace the old IfNode with a new LoopEndNode
  Node *lex = _igvn.register_new_node_with_optimizer(BaseCountedLoopEndNode::make(iff->in(0), test, cl_prob, iff->as_If()->_fcnt, iv_bt));
  IfNode *le = lex->as_If();
  uint dd = dom_depth(iff);
  set_idom(le, le->in(0), dd); // Update dominance for loop exit
  set_loop(le, loop);

  // Get the loop-exit control
  Node *iffalse = iff->as_If()->proj_out(!(iftrue_op == Op_IfTrue));

  // Need to swap loop-exit and loop-back control?
  if (iftrue_op == Op_IfFalse) {
    Node *ift2=_igvn.register_new_node_with_optimizer(new IfTrueNode (le));
    Node *iff2=_igvn.register_new_node_with_optimizer(new IfFalseNode(le));

    loop->_tail = back_control = ift2;
    set_loop(ift2, loop);
    set_loop(iff2, get_loop(iffalse));

    // Lazy update of 'get_ctrl' mechanism.
    lazy_replace(iffalse, iff2);
    lazy_replace(iftrue,  ift2);

    // Swap names
    iffalse = iff2;
    iftrue  = ift2;
  } else {
    _igvn.rehash_node_delayed(iffalse);
    _igvn.rehash_node_delayed(iftrue);
    iffalse->set_req_X( 0, le, &_igvn );
    iftrue ->set_req_X( 0, le, &_igvn );
  }

  set_idom(iftrue,  le, dd+1);
  set_idom(iffalse, le, dd+1);
  assert(iff->outcnt() == 0, "should be dead now");
  lazy_replace( iff, le ); // fix 'get_ctrl'

  Node* entry_control = init_control;
  bool strip_mine_loop = iv_bt == T_INT &&
                         loop->_child == nullptr &&
                         sfpt != nullptr &&
                         !loop->_has_call &&
                         is_deleteable_safept(sfpt);
  IdealLoopTree* outer_ilt = nullptr;
  if (strip_mine_loop) {
    outer_ilt = create_outer_strip_mined_loop(test, cmp, init_control, loop,
                                              cl_prob, le->_fcnt, entry_control,
                                              iffalse);
  }

  // Now setup a new CountedLoopNode to replace the existing LoopNode
  BaseCountedLoopNode *l = BaseCountedLoopNode::make(entry_control, back_control, iv_bt);
  l->set_unswitch_count(x->as_Loop()->unswitch_count()); // Preserve
  // The following assert is approximately true, and defines the intention
  // of can_be_counted_loop.  It fails, however, because phase->type
  // is not yet initialized for this loop and its parts.
  //assert(l->can_be_counted_loop(this), "sanity");
  _igvn.register_new_node_with_optimizer(l);
  set_loop(l, loop);
  loop->_head = l;
  // Fix all data nodes placed at the old loop head.
  // Uses the lazy-update mechanism of 'get_ctrl'.
  lazy_replace( x, l );
  set_idom(l, entry_control, dom_depth(entry_control) + 1);

  if (iv_bt == T_INT && (LoopStripMiningIter == 0 || strip_mine_loop)) {
    // Check for immediately preceding SafePoint and remove
    if (sfpt != nullptr && (strip_mine_loop || is_deleteable_safept(sfpt))) {
      if (strip_mine_loop) {
        Node* outer_le = outer_ilt->_tail->in(0);
        Node* sfpt_clone = sfpt->clone();
        sfpt_clone->set_req(0, iffalse);
        outer_le->set_req(0, sfpt_clone);

        Node* polladdr = sfpt_clone->in(TypeFunc::Parms);
        if (polladdr != nullptr && polladdr->is_Load()) {
          // Polling load should be pinned outside inner loop.
          Node* new_polladdr = polladdr->clone();
          new_polladdr->set_req(0, iffalse);
          _igvn.register_new_node_with_optimizer(new_polladdr, polladdr);
          set_ctrl(new_polladdr, iffalse);
          sfpt_clone->set_req(TypeFunc::Parms, new_polladdr);
        }
        // When this code runs, loop bodies have not yet been populated.
        const bool body_populated = false;
        register_control(sfpt_clone, outer_ilt, iffalse, body_populated);
        set_idom(outer_le, sfpt_clone, dom_depth(sfpt_clone));
      }
      lazy_replace(sfpt, sfpt->in(TypeFunc::Control));
      if (loop->_safepts != nullptr) {
        loop->_safepts->yank(sfpt);
      }
    }
  }

#ifdef ASSERT
  assert(l->is_valid_counted_loop(iv_bt), "counted loop shape is messed up");
  assert(l == loop->_head && l->phi() == phi && l->loopexit_or_null() == lex, "" );
#endif
#ifndef PRODUCT
  if (TraceLoopOpts) {
    tty->print("Counted      ");
    loop->dump_head();
  }
#endif

  C->print_method(PHASE_AFTER_CLOOPS, 3, l);

  // Capture bounds of the loop in the induction variable Phi before
  // subsequent transformation (iteration splitting) obscures the
  // bounds
  l->phi()->as_Phi()->set_type(l->phi()->Value(&_igvn));

  if (strip_mine_loop) {
    l->mark_strip_mined();
    l->verify_strip_mined(1);
    outer_ilt->_head->as_Loop()->verify_strip_mined(1);
    loop = outer_ilt;
  }

#ifndef PRODUCT
  if (x->as_Loop()->is_loop_nest_inner_loop() && iv_bt == T_LONG) {
    Atomic::inc(&_long_loop_counted_loops);
  }
#endif
  if (iv_bt == T_LONG && x->as_Loop()->is_loop_nest_outer_loop()) {
    l->mark_loop_nest_outer_loop();
  }

  return true;
}

// Check if there is a dominating loop limit check of the form 'init < limit' starting at the loop entry.
// If there is one, then we do not need to create an additional Loop Limit Check Predicate.
bool PhaseIdealLoop::has_dominating_loop_limit_check(Node* init_trip, Node* limit, const jlong stride_con,
                                                     const BasicType iv_bt, Node* loop_entry) {
  // Eagerly call transform() on the Cmp and Bool node to common them up if possible. This is required in order to
  // successfully find a dominated test with the If node below.
  Node* cmp_limit;
  Node* bol;
  if (stride_con > 0) {
    cmp_limit = _igvn.transform(CmpNode::make(init_trip, limit, iv_bt));
    bol = _igvn.transform(new BoolNode(cmp_limit, BoolTest::lt));
  } else {
    cmp_limit = _igvn.transform(CmpNode::make(init_trip, limit, iv_bt));
    bol = _igvn.transform(new BoolNode(cmp_limit, BoolTest::gt));
  }

  // Check if there is already a dominating init < limit check. If so, we do not need a Loop Limit Check Predicate.
  IfNode* iff = new IfNode(loop_entry, bol, PROB_MIN, COUNT_UNKNOWN);
  // Also add fake IfProj nodes in order to call transform() on the newly created IfNode.
  IfFalseNode* if_false = new IfFalseNode(iff);
  IfTrueNode* if_true = new IfTrueNode(iff);
  Node* dominated_iff = _igvn.transform(iff);
  // ConI node? Found dominating test (IfNode::dominated_by() returns a ConI node).
  const bool found_dominating_test = dominated_iff != nullptr && dominated_iff->is_ConI();

  // Kill the If with its projections again in the next IGVN round by cutting it off from the graph.
  _igvn.replace_input_of(iff, 0, C->top());
  _igvn.replace_input_of(iff, 1, C->top());
  return found_dominating_test;
}

//----------------------exact_limit-------------------------------------------
Node* PhaseIdealLoop::exact_limit( IdealLoopTree *loop ) {
  assert(loop->_head->is_CountedLoop(), "");
  CountedLoopNode *cl = loop->_head->as_CountedLoop();
  assert(cl->is_valid_counted_loop(T_INT), "");

  if (cl->stride_con() == 1 ||
      cl->stride_con() == -1 ||
      cl->limit()->Opcode() == Op_LoopLimit) {
    // Old code has exact limit (it could be incorrect in case of int overflow).
    // Loop limit is exact with stride == 1. And loop may already have exact limit.
    return cl->limit();
  }
  Node *limit = nullptr;
#ifdef ASSERT
  BoolTest::mask bt = cl->loopexit()->test_trip();
  assert(bt == BoolTest::lt || bt == BoolTest::gt, "canonical test is expected");
#endif
  if (cl->has_exact_trip_count()) {
    // Simple case: loop has constant boundaries.
    // Use jlongs to avoid integer overflow.
    int stride_con = cl->stride_con();
    jlong  init_con = cl->init_trip()->get_int();
    jlong limit_con = cl->limit()->get_int();
    julong trip_cnt = cl->trip_count();
    jlong final_con = init_con + trip_cnt*stride_con;
    int final_int = (int)final_con;
    // The final value should be in integer range since the loop
    // is counted and the limit was checked for overflow.
    assert(final_con == (jlong)final_int, "final value should be integer");
    limit = _igvn.intcon(final_int);
  } else {
    // Create new LoopLimit node to get exact limit (final iv value).
    limit = new LoopLimitNode(C, cl->init_trip(), cl->limit(), cl->stride());
    register_new_node(limit, cl->in(LoopNode::EntryControl));
  }
  assert(limit != nullptr, "sanity");
  return limit;
}

//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.
// Attempt to convert into a counted-loop.
Node *LoopNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if (!can_be_counted_loop(phase) && !is_OuterStripMinedLoop()) {
    phase->C->set_major_progress();
  }
  return RegionNode::Ideal(phase, can_reshape);
}

#ifdef ASSERT
void LoopNode::verify_strip_mined(int expect_skeleton) const {
  const OuterStripMinedLoopNode* outer = nullptr;
  const CountedLoopNode* inner = nullptr;
  if (is_strip_mined()) {
    if (!is_valid_counted_loop(T_INT)) {
      return; // Skip malformed counted loop
    }
    assert(is_CountedLoop(), "no Loop should be marked strip mined");
    inner = as_CountedLoop();
    outer = inner->in(LoopNode::EntryControl)->as_OuterStripMinedLoop();
  } else if (is_OuterStripMinedLoop()) {
    outer = this->as_OuterStripMinedLoop();
    inner = outer->unique_ctrl_out()->as_CountedLoop();
    assert(inner->is_valid_counted_loop(T_INT) && inner->is_strip_mined(), "OuterStripMinedLoop should have been removed");
    assert(!is_strip_mined(), "outer loop shouldn't be marked strip mined");
  }
  if (inner != nullptr || outer != nullptr) {
    assert(inner != nullptr && outer != nullptr, "missing loop in strip mined nest");
    Node* outer_tail = outer->in(LoopNode::LoopBackControl);
    Node* outer_le = outer_tail->in(0);
    assert(outer_le->Opcode() == Op_OuterStripMinedLoopEnd, "tail of outer loop should be an If");
    Node* sfpt = outer_le->in(0);
    assert(sfpt->Opcode() == Op_SafePoint, "where's the safepoint?");
    Node* inner_out = sfpt->in(0);
    CountedLoopEndNode* cle = inner_out->in(0)->as_CountedLoopEnd();
    assert(cle == inner->loopexit_or_null(), "mismatch");
    bool has_skeleton = outer_le->in(1)->bottom_type()->singleton() && outer_le->in(1)->bottom_type()->is_int()->get_con() == 0;
    if (has_skeleton) {
      assert(expect_skeleton == 1 || expect_skeleton == -1, "unexpected skeleton node");
      assert(outer->outcnt() == 2, "only control nodes");
    } else {
      assert(expect_skeleton == 0 || expect_skeleton == -1, "no skeleton node?");
      uint phis = 0;
      uint be_loads = 0;
      Node* be = inner->in(LoopNode::LoopBackControl);
      for (DUIterator_Fast imax, i = inner->fast_outs(imax); i < imax; i++) {
        Node* u = inner->fast_out(i);
        if (u->is_Phi()) {
          phis++;
          for (DUIterator_Fast jmax, j = be->fast_outs(jmax); j < jmax; j++) {
            Node* n = be->fast_out(j);
            if (n->is_Load()) {
              assert(n->in(0) == be || n->find_prec_edge(be) > 0, "should be on the backedge");
              do {
                n = n->raw_out(0);
              } while (!n->is_Phi());
              if (n == u) {
                be_loads++;
                break;
              }
            }
          }
        }
      }
      assert(be_loads <= phis, "wrong number phis that depends on a pinned load");
      for (DUIterator_Fast imax, i = outer->fast_outs(imax); i < imax; i++) {
        Node* u = outer->fast_out(i);
        assert(u == outer || u == inner || u->is_Phi(), "nothing between inner and outer loop");
      }
      uint stores = 0;
      for (DUIterator_Fast imax, i = inner_out->fast_outs(imax); i < imax; i++) {
        Node* u = inner_out->fast_out(i);
        if (u->is_Store()) {
          stores++;
        }
      }
      // Late optimization of loads on backedge can cause Phi of outer loop to be eliminated but Phi of inner loop is
      // not guaranteed to be optimized out.
      assert(outer->outcnt() >= phis + 2 - be_loads && outer->outcnt() <= phis + 2 + stores + 1, "only phis");
    }
    assert(sfpt->outcnt() == 1, "no data node");
    assert(outer_tail->outcnt() == 1 || !has_skeleton, "no data node");
  }
}
#endif

//=============================================================================
//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.
// Attempt to convert into a counted-loop.
Node *CountedLoopNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  return RegionNode::Ideal(phase, can_reshape);
}

//------------------------------dump_spec--------------------------------------
// Dump special per-node info
#ifndef PRODUCT
void CountedLoopNode::dump_spec(outputStream *st) const {
  LoopNode::dump_spec(st);
  if (stride_is_con()) {
    st->print("stride: %d ",stride_con());
  }
  if (is_pre_loop ()) st->print("pre of N%d" , _main_idx);
  if (is_main_loop()) st->print("main of N%d", _idx);
  if (is_post_loop()) st->print("post of N%d", _main_idx);
  if (is_strip_mined()) st->print(" strip mined");
}
#endif

//=============================================================================
jlong BaseCountedLoopEndNode::stride_con() const {
  return stride()->bottom_type()->is_integer(bt())->get_con_as_long(bt());
}


BaseCountedLoopEndNode* BaseCountedLoopEndNode::make(Node* control, Node* test, float prob, float cnt, BasicType bt) {
  if (bt == T_INT) {
    return new CountedLoopEndNode(control, test, prob, cnt);
  }
  assert(bt == T_LONG, "unsupported");
  return new LongCountedLoopEndNode(control, test, prob, cnt);
}

//=============================================================================
//------------------------------Value-----------------------------------------
const Type* LoopLimitNode::Value(PhaseGVN* phase) const {
  const Type* init_t   = phase->type(in(Init));
  const Type* limit_t  = phase->type(in(Limit));
  const Type* stride_t = phase->type(in(Stride));
  // Either input is TOP ==> the result is TOP
  if (init_t   == Type::TOP) return Type::TOP;
  if (limit_t  == Type::TOP) return Type::TOP;
  if (stride_t == Type::TOP) return Type::TOP;

  int stride_con = stride_t->is_int()->get_con();
  if (stride_con == 1)
    return bottom_type();  // Identity

  if (init_t->is_int()->is_con() && limit_t->is_int()->is_con()) {
    // Use jlongs to avoid integer overflow.
    jlong init_con   =  init_t->is_int()->get_con();
    jlong limit_con  = limit_t->is_int()->get_con();
    int  stride_m   = stride_con - (stride_con > 0 ? 1 : -1);
    jlong trip_count = (limit_con - init_con + stride_m)/stride_con;
    jlong final_con  = init_con + stride_con*trip_count;
    int final_int = (int)final_con;
    // The final value should be in integer range since the loop
    // is counted and the limit was checked for overflow.
    // Assert checks for overflow only if all input nodes are ConINodes, as during CCP
    // there might be a temporary overflow from PhiNodes see JDK-8309266
    assert((in(Init)->is_ConI() && in(Limit)->is_ConI() && in(Stride)->is_ConI()) ? final_con == (jlong)final_int : true, "final value should be integer");
    if (final_con == (jlong)final_int) {
      return TypeInt::make(final_int);
    } else {
      return bottom_type();
    }
  }

  return bottom_type(); // TypeInt::INT
}

//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.
Node *LoopLimitNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if (phase->type(in(Init))   == Type::TOP ||
      phase->type(in(Limit))  == Type::TOP ||
      phase->type(in(Stride)) == Type::TOP)
    return nullptr;  // Dead

  int stride_con = phase->type(in(Stride))->is_int()->get_con();
  if (stride_con == 1)
    return nullptr;  // Identity

  if (in(Init)->is_Con() && in(Limit)->is_Con())
    return nullptr;  // Value

  // Delay following optimizations until all loop optimizations
  // done to keep Ideal graph simple.
  if (!can_reshape || !phase->C->post_loop_opts_phase()) {
    return nullptr;
  }

  const TypeInt* init_t  = phase->type(in(Init) )->is_int();
  const TypeInt* limit_t = phase->type(in(Limit))->is_int();
  int stride_p;
  jlong lim, ini;
  julong max;
  if (stride_con > 0) {
    stride_p = stride_con;
    lim = limit_t->_hi;
    ini = init_t->_lo;
    max = (julong)max_jint;
  } else {
    stride_p = -stride_con;
    lim = init_t->_hi;
    ini = limit_t->_lo;
    max = (julong)min_jint;
  }
  julong range = lim - ini + stride_p;
  if (range <= max) {
    // Convert to integer expression if it is not overflow.
    Node* stride_m = phase->intcon(stride_con - (stride_con > 0 ? 1 : -1));
    Node *range = phase->transform(new SubINode(in(Limit), in(Init)));
    Node *bias  = phase->transform(new AddINode(range, stride_m));
    Node *trip  = phase->transform(new DivINode(nullptr, bias, in(Stride)));
    Node *span  = phase->transform(new MulINode(trip, in(Stride)));
    return new AddINode(span, in(Init)); // exact limit
  }

  if (is_power_of_2(stride_p) ||                // divisor is 2^n
      !Matcher::has_match_rule(Op_LoopLimit)) { // or no specialized Mach node?
    // Convert to long expression to avoid integer overflow
    // and let igvn optimizer convert this division.
    //
    Node*   init   = phase->transform( new ConvI2LNode(in(Init)));
    Node*  limit   = phase->transform( new ConvI2LNode(in(Limit)));
    Node* stride   = phase->longcon(stride_con);
    Node* stride_m = phase->longcon(stride_con - (stride_con > 0 ? 1 : -1));

    Node *range = phase->transform(new SubLNode(limit, init));
    Node *bias  = phase->transform(new AddLNode(range, stride_m));
    Node *span;
    if (stride_con > 0 && is_power_of_2(stride_p)) {
      // bias >= 0 if stride >0, so if stride is 2^n we can use &(-stride)
      // and avoid generating rounding for division. Zero trip guard should
      // guarantee that init < limit but sometimes the guard is missing and
      // we can get situation when init > limit. Note, for the empty loop
      // optimization zero trip guard is generated explicitly which leaves
      // only RCE predicate where exact limit is used and the predicate
      // will simply fail forcing recompilation.
      Node* neg_stride   = phase->longcon(-stride_con);
      span = phase->transform(new AndLNode(bias, neg_stride));
    } else {
      Node *trip  = phase->transform(new DivLNode(nullptr, bias, stride));
      span = phase->transform(new MulLNode(trip, stride));
    }
    // Convert back to int
    Node *span_int = phase->transform(new ConvL2INode(span));
    return new AddINode(span_int, in(Init)); // exact limit
  }

  return nullptr;    // No progress
}

//------------------------------Identity---------------------------------------
// If stride == 1 return limit node.
Node* LoopLimitNode::Identity(PhaseGVN* phase) {
  int stride_con = phase->type(in(Stride))->is_int()->get_con();
  if (stride_con == 1 || stride_con == -1)
    return in(Limit);
  return this;
}

//=============================================================================
//----------------------match_incr_with_optional_truncation--------------------
// Match increment with optional truncation:
// CHAR: (i+1)&0x7fff, BYTE: ((i+1)<<8)>>8, or SHORT: ((i+1)<<16)>>16
// Return null for failure. Success returns the increment node.
Node* CountedLoopNode::match_incr_with_optional_truncation(Node* expr, Node** trunc1, Node** trunc2,
                                                           const TypeInteger** trunc_type,
                                                           BasicType bt) {
  // Quick cutouts:
  if (expr == nullptr || expr->req() != 3)  return nullptr;

  Node *t1 = nullptr;
  Node *t2 = nullptr;
  Node* n1 = expr;
  int   n1op = n1->Opcode();
  const TypeInteger* trunc_t = TypeInteger::bottom(bt);

  if (bt == T_INT) {
    // Try to strip (n1 & M) or (n1 << N >> N) from n1.
    if (n1op == Op_AndI &&
        n1->in(2)->is_Con() &&
        n1->in(2)->bottom_type()->is_int()->get_con() == 0x7fff) {
      // %%% This check should match any mask of 2**K-1.
      t1 = n1;
      n1 = t1->in(1);
      n1op = n1->Opcode();
      trunc_t = TypeInt::CHAR;
    } else if (n1op == Op_RShiftI &&
               n1->in(1) != nullptr &&
               n1->in(1)->Opcode() == Op_LShiftI &&
               n1->in(2) == n1->in(1)->in(2) &&
               n1->in(2)->is_Con()) {
      jint shift = n1->in(2)->bottom_type()->is_int()->get_con();
      // %%% This check should match any shift in [1..31].
      if (shift == 16 || shift == 8) {
        t1 = n1;
        t2 = t1->in(1);
        n1 = t2->in(1);
        n1op = n1->Opcode();
        if (shift == 16) {
          trunc_t = TypeInt::SHORT;
        } else if (shift == 8) {
          trunc_t = TypeInt::BYTE;
        }
      }
    }
  }

  // If (maybe after stripping) it is an AddI, we won:
  if (n1op == Op_Add(bt)) {
    *trunc1 = t1;
    *trunc2 = t2;
    *trunc_type = trunc_t;
    return n1;
  }

  // failed
  return nullptr;
}

LoopNode* CountedLoopNode::skip_strip_mined(int expect_skeleton) {
  if (is_strip_mined() && in(EntryControl) != nullptr && in(EntryControl)->is_OuterStripMinedLoop()) {
    verify_strip_mined(expect_skeleton);
    return in(EntryControl)->as_Loop();
  }
  return this;
}

OuterStripMinedLoopNode* CountedLoopNode::outer_loop() const {
  assert(is_strip_mined(), "not a strip mined loop");
  Node* c = in(EntryControl);
  if (c == nullptr || c->is_top() || !c->is_OuterStripMinedLoop()) {
    return nullptr;
  }
  return c->as_OuterStripMinedLoop();
}

IfTrueNode* OuterStripMinedLoopNode::outer_loop_tail() const {
  Node* c = in(LoopBackControl);
  if (c == nullptr || c->is_top()) {
    return nullptr;
  }
  return c->as_IfTrue();
}

IfTrueNode* CountedLoopNode::outer_loop_tail() const {
  LoopNode* l = outer_loop();
  if (l == nullptr) {
    return nullptr;
  }
  return l->outer_loop_tail();
}

OuterStripMinedLoopEndNode* OuterStripMinedLoopNode::outer_loop_end() const {
  IfTrueNode* proj = outer_loop_tail();
  if (proj == nullptr) {
    return nullptr;
  }
  Node* c = proj->in(0);
  if (c == nullptr || c->is_top() || c->outcnt() != 2) {
    return nullptr;
  }
  return c->as_OuterStripMinedLoopEnd();
}

OuterStripMinedLoopEndNode* CountedLoopNode::outer_loop_end() const {
  LoopNode* l = outer_loop();
  if (l == nullptr) {
    return nullptr;
  }
  return l->outer_loop_end();
}

IfFalseNode* OuterStripMinedLoopNode::outer_loop_exit() const {
  IfNode* le = outer_loop_end();
  if (le == nullptr) {
    return nullptr;
  }
  Node* c = le->proj_out_or_null(false);
  if (c == nullptr) {
    return nullptr;
  }
  return c->as_IfFalse();
}

IfFalseNode* CountedLoopNode::outer_loop_exit() const {
  LoopNode* l = outer_loop();
  if (l == nullptr) {
    return nullptr;
  }
  return l->outer_loop_exit();
}

SafePointNode* OuterStripMinedLoopNode::outer_safepoint() const {
  IfNode* le = outer_loop_end();
  if (le == nullptr) {
    return nullptr;
  }
  Node* c = le->in(0);
  if (c == nullptr || c->is_top()) {
    return nullptr;
  }
  assert(c->Opcode() == Op_SafePoint, "broken outer loop");
  return c->as_SafePoint();
}

SafePointNode* CountedLoopNode::outer_safepoint() const {
  LoopNode* l = outer_loop();
  if (l == nullptr) {
    return nullptr;
  }
  return l->outer_safepoint();
}

Node* CountedLoopNode::skip_assertion_predicates_with_halt() {
  Node* ctrl = in(LoopNode::EntryControl);
  if (is_main_loop()) {
    ctrl = skip_strip_mined()->in(LoopNode::EntryControl);
  }
  if (is_main_loop() || is_post_loop()) {
    AssertionPredicatesWithHalt assertion_predicates(ctrl);
    return assertion_predicates.entry();
  }
  return ctrl;
}


int CountedLoopNode::stride_con() const {
  CountedLoopEndNode* cle = loopexit_or_null();
  return cle != nullptr ? cle->stride_con() : 0;
}

BaseCountedLoopNode* BaseCountedLoopNode::make(Node* entry, Node* backedge, BasicType bt) {
  if (bt == T_INT) {
    return new CountedLoopNode(entry, backedge);
  }
  assert(bt == T_LONG, "unsupported");
  return new LongCountedLoopNode(entry, backedge);
}

void OuterStripMinedLoopNode::fix_sunk_stores(CountedLoopEndNode* inner_cle, LoopNode* inner_cl, PhaseIterGVN* igvn,
                                              PhaseIdealLoop* iloop) {
  Node* cle_out = inner_cle->proj_out(false);
  Node* cle_tail = inner_cle->proj_out(true);
  if (cle_out->outcnt() > 1) {
    // Look for chains of stores that were sunk
    // out of the inner loop and are in the outer loop
    for (DUIterator_Fast imax, i = cle_out->fast_outs(imax); i < imax; i++) {
      Node* u = cle_out->fast_out(i);
      if (u->is_Store()) {
        int alias_idx = igvn->C->get_alias_index(u->adr_type());
        Node* first = u;
        for (;;) {
          Node* next = first->in(MemNode::Memory);
          if (!next->is_Store() || next->in(0) != cle_out) {
            break;
          }
          assert(igvn->C->get_alias_index(next->adr_type()) == alias_idx, "");
          first = next;
        }
        Node* last = u;
        for (;;) {
          Node* next = nullptr;
          for (DUIterator_Fast jmax, j = last->fast_outs(jmax); j < jmax; j++) {
            Node* uu = last->fast_out(j);
            if (uu->is_Store() && uu->in(0) == cle_out) {
              assert(next == nullptr, "only one in the outer loop");
              next = uu;
              assert(igvn->C->get_alias_index(next->adr_type()) == alias_idx, "");
            }
          }
          if (next == nullptr) {
            break;
          }
          last = next;
        }
        Node* phi = nullptr;
        for (DUIterator_Fast jmax, j = inner_cl->fast_outs(jmax); j < jmax; j++) {
          Node* uu = inner_cl->fast_out(j);
          if (uu->is_Phi()) {
            Node* be = uu->in(LoopNode::LoopBackControl);
            if (be->is_Store() && be->in(0) == inner_cl->in(LoopNode::LoopBackControl)) {
              assert(igvn->C->get_alias_index(uu->adr_type()) != alias_idx && igvn->C->get_alias_index(uu->adr_type()) != Compile::AliasIdxBot, "unexpected store");
            }
            if (be == last || be == first->in(MemNode::Memory)) {
              assert(igvn->C->get_alias_index(uu->adr_type()) == alias_idx || igvn->C->get_alias_index(uu->adr_type()) == Compile::AliasIdxBot, "unexpected alias");
              assert(phi == nullptr, "only one phi");
              phi = uu;
            }
          }
        }
#ifdef ASSERT
        for (DUIterator_Fast jmax, j = inner_cl->fast_outs(jmax); j < jmax; j++) {
          Node* uu = inner_cl->fast_out(j);
          if (uu->is_memory_phi()) {
            if (uu->adr_type() == igvn->C->get_adr_type(igvn->C->get_alias_index(u->adr_type()))) {
              assert(phi == uu, "what's that phi?");
            } else if (uu->adr_type() == TypePtr::BOTTOM) {
              Node* n = uu->in(LoopNode::LoopBackControl);
              uint limit = igvn->C->live_nodes();
              uint i = 0;
              while (n != uu) {
                i++;
                assert(i < limit, "infinite loop");
                if (n->is_Proj()) {
                  n = n->in(0);
                } else if (n->is_SafePoint() || n->is_MemBar()) {
                  n = n->in(TypeFunc::Memory);
                } else if (n->is_Phi()) {
                  n = n->in(1);
                } else if (n->is_MergeMem()) {
                  n = n->as_MergeMem()->memory_at(igvn->C->get_alias_index(u->adr_type()));
                } else if (n->is_Store() || n->is_LoadStore() || n->is_ClearArray()) {
                  n = n->in(MemNode::Memory);
                } else {
                  n->dump();
                  ShouldNotReachHere();
                }
              }
            }
          }
        }
#endif
        if (phi == nullptr) {
          // If an entire chains was sunk, the
          // inner loop has no phi for that memory
          // slice, create one for the outer loop
          phi = PhiNode::make(inner_cl, first->in(MemNode::Memory), Type::MEMORY,
                              igvn->C->get_adr_type(igvn->C->get_alias_index(u->adr_type())));
          phi->set_req(LoopNode::LoopBackControl, last);
          phi = register_new_node(phi, inner_cl, igvn, iloop);
          igvn->replace_input_of(first, MemNode::Memory, phi);
        } else {
          // Or fix the outer loop fix to include
          // that chain of stores.
          Node* be = phi->in(LoopNode::LoopBackControl);
          assert(!(be->is_Store() && be->in(0) == inner_cl->in(LoopNode::LoopBackControl)), "store on the backedge + sunk stores: unsupported");
          if (be == first->in(MemNode::Memory)) {
            if (be == phi->in(LoopNode::LoopBackControl)) {
              igvn->replace_input_of(phi, LoopNode::LoopBackControl, last);
            } else {
              igvn->replace_input_of(be, MemNode::Memory, last);
            }
          } else {
#ifdef ASSERT
            if (be == phi->in(LoopNode::LoopBackControl)) {
              assert(phi->in(LoopNode::LoopBackControl) == last, "");
            } else {
              assert(be->in(MemNode::Memory) == last, "");
            }
#endif
          }
        }
      }
    }
  }
}

void OuterStripMinedLoopNode::adjust_strip_mined_loop(PhaseIterGVN* igvn) {
  // Look for the outer & inner strip mined loop, reduce number of
  // iterations of the inner loop, set exit condition of outer loop,
  // construct required phi nodes for outer loop.
  CountedLoopNode* inner_cl = unique_ctrl_out()->as_CountedLoop();
  assert(inner_cl->is_strip_mined(), "inner loop should be strip mined");
  if (LoopStripMiningIter == 0) {
    remove_outer_loop_and_safepoint(igvn);
    return;
  }
  if (LoopStripMiningIter == 1) {
    transform_to_counted_loop(igvn, nullptr);
    return;
  }
  Node* inner_iv_phi = inner_cl->phi();
  if (inner_iv_phi == nullptr) {
    IfNode* outer_le = outer_loop_end();
    Node* iff = igvn->transform(new IfNode(outer_le->in(0), outer_le->in(1), outer_le->_prob, outer_le->_fcnt));
    igvn->replace_node(outer_le, iff);
    inner_cl->clear_strip_mined();
    return;
  }
  CountedLoopEndNode* inner_cle = inner_cl->loopexit();

  int stride = inner_cl->stride_con();
  // For a min int stride, LoopStripMiningIter * stride overflows the int range for all values of LoopStripMiningIter
  // except 0 or 1. Those values are handled early on in this method and causes the method to return. So for a min int
  // stride, the method is guaranteed to return at the next check below.
  jlong scaled_iters_long = ((jlong)LoopStripMiningIter) * ABS((jlong)stride);
  int scaled_iters = (int)scaled_iters_long;
  if ((jlong)scaled_iters != scaled_iters_long) {
    // Remove outer loop and safepoint (too few iterations)
    remove_outer_loop_and_safepoint(igvn);
    return;
  }
  jlong short_scaled_iters = LoopStripMiningIterShortLoop * ABS(stride);
  const TypeInt* inner_iv_t = igvn->type(inner_iv_phi)->is_int();
  jlong iter_estimate = (jlong)inner_iv_t->_hi - (jlong)inner_iv_t->_lo;
  assert(iter_estimate > 0, "broken");
  if (iter_estimate <= short_scaled_iters) {
    // Remove outer loop and safepoint: loop executes less than LoopStripMiningIterShortLoop
    remove_outer_loop_and_safepoint(igvn);
    return;
  }
  if (iter_estimate <= scaled_iters_long) {
    // We would only go through one iteration of
    // the outer loop: drop the outer loop but
    // keep the safepoint so we don't run for
    // too long without a safepoint
    IfNode* outer_le = outer_loop_end();
    Node* iff = igvn->transform(new IfNode(outer_le->in(0), outer_le->in(1), outer_le->_prob, outer_le->_fcnt));
    igvn->replace_node(outer_le, iff);
    inner_cl->clear_strip_mined();
    return;
  }

  Node* cle_tail = inner_cle->proj_out(true);
  ResourceMark rm;
  Node_List old_new;
  if (cle_tail->outcnt() > 1) {
    // Look for nodes on backedge of inner loop and clone them
    Unique_Node_List backedge_nodes;
    for (DUIterator_Fast imax, i = cle_tail->fast_outs(imax); i < imax; i++) {
      Node* u = cle_tail->fast_out(i);
      if (u != inner_cl) {
        assert(!u->is_CFG(), "control flow on the backedge?");
        backedge_nodes.push(u);
      }
    }
    uint last = igvn->C->unique();
    for (uint next = 0; next < backedge_nodes.size(); next++) {
      Node* n = backedge_nodes.at(next);
      old_new.map(n->_idx, n->clone());
      for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
        Node* u = n->fast_out(i);
        assert(!u->is_CFG(), "broken");
        if (u->_idx >= last) {
          continue;
        }
        if (!u->is_Phi()) {
          backedge_nodes.push(u);
        } else {
          assert(u->in(0) == inner_cl, "strange phi on the backedge");
        }
      }
    }
    // Put the clones on the outer loop backedge
    Node* le_tail = outer_loop_tail();
    for (uint next = 0; next < backedge_nodes.size(); next++) {
      Node *n = old_new[backedge_nodes.at(next)->_idx];
      for (uint i = 1; i < n->req(); i++) {
        if (n->in(i) != nullptr && old_new[n->in(i)->_idx] != nullptr) {
          n->set_req(i, old_new[n->in(i)->_idx]);
        }
      }
      if (n->in(0) != nullptr && n->in(0) == cle_tail) {
        n->set_req(0, le_tail);
      }
      igvn->register_new_node_with_optimizer(n);
    }
  }

  Node* iv_phi = nullptr;
  // Make a clone of each phi in the inner loop
  // for the outer loop
  for (uint i = 0; i < inner_cl->outcnt(); i++) {
    Node* u = inner_cl->raw_out(i);
    if (u->is_Phi()) {
      assert(u->in(0) == inner_cl, "inconsistent");
      Node* phi = u->clone();
      phi->set_req(0, this);
      Node* be = old_new[phi->in(LoopNode::LoopBackControl)->_idx];
      if (be != nullptr) {
        phi->set_req(LoopNode::LoopBackControl, be);
      }
      phi = igvn->transform(phi);
      igvn->replace_input_of(u, LoopNode::EntryControl, phi);
      if (u == inner_iv_phi) {
        iv_phi = phi;
      }
    }
  }

  if (iv_phi != nullptr) {
    // Now adjust the inner loop's exit condition
    Node* limit = inner_cl->limit();
    // If limit < init for stride > 0 (or limit > init for stride < 0),
    // the loop body is run only once. Given limit - init (init - limit resp.)
    // would be negative, the unsigned comparison below would cause
    // the loop body to be run for LoopStripMiningIter.
    Node* max = nullptr;
    if (stride > 0) {
      max = MaxNode::max_diff_with_zero(limit, iv_phi, TypeInt::INT, *igvn);
    } else {
      max = MaxNode::max_diff_with_zero(iv_phi, limit, TypeInt::INT, *igvn);
    }
    // sub is positive and can be larger than the max signed int
    // value. Use an unsigned min.
    Node* const_iters = igvn->intcon(scaled_iters);
    Node* min = MaxNode::unsigned_min(max, const_iters, TypeInt::make(0, scaled_iters, Type::WidenMin), *igvn);
    // min is the number of iterations for the next inner loop execution:
    // unsigned_min(max(limit - iv_phi, 0), scaled_iters) if stride > 0
    // unsigned_min(max(iv_phi - limit, 0), scaled_iters) if stride < 0

    Node* new_limit = nullptr;
    if (stride > 0) {
      new_limit = igvn->transform(new AddINode(min, iv_phi));
    } else {
      new_limit = igvn->transform(new SubINode(iv_phi, min));
    }
    Node* inner_cmp = inner_cle->cmp_node();
    Node* inner_bol = inner_cle->in(CountedLoopEndNode::TestValue);
    Node* outer_bol = inner_bol;
    // cmp node for inner loop may be shared
    inner_cmp = inner_cmp->clone();
    inner_cmp->set_req(2, new_limit);
    inner_bol = inner_bol->clone();
    inner_bol->set_req(1, igvn->transform(inner_cmp));
    igvn->replace_input_of(inner_cle, CountedLoopEndNode::TestValue, igvn->transform(inner_bol));
    // Set the outer loop's exit condition too
    igvn->replace_input_of(outer_loop_end(), 1, outer_bol);
  } else {
    assert(false, "should be able to adjust outer loop");
    IfNode* outer_le = outer_loop_end();
    Node* iff = igvn->transform(new IfNode(outer_le->in(0), outer_le->in(1), outer_le->_prob, outer_le->_fcnt));
    igvn->replace_node(outer_le, iff);
    inner_cl->clear_strip_mined();
  }
}

void OuterStripMinedLoopNode::transform_to_counted_loop(PhaseIterGVN* igvn, PhaseIdealLoop* iloop) {
  CountedLoopNode* inner_cl = unique_ctrl_out()->as_CountedLoop();
  CountedLoopEndNode* cle = inner_cl->loopexit();
  Node* inner_test = cle->in(1);
  IfNode* outer_le = outer_loop_end();
  CountedLoopEndNode* inner_cle = inner_cl->loopexit();
  Node* safepoint = outer_safepoint();

  fix_sunk_stores(inner_cle, inner_cl, igvn, iloop);

  // make counted loop exit test always fail
  ConINode* zero = igvn->intcon(0);
  if (iloop != nullptr) {
    iloop->set_ctrl(zero, igvn->C->root());
  }
  igvn->replace_input_of(cle, 1, zero);
  // replace outer loop end with CountedLoopEndNode with formers' CLE's exit test
  Node* new_end = new CountedLoopEndNode(outer_le->in(0), inner_test, cle->_prob, cle->_fcnt);
  register_control(new_end, inner_cl, outer_le->in(0), igvn, iloop);
  if (iloop == nullptr) {
    igvn->replace_node(outer_le, new_end);
  } else {
    iloop->lazy_replace(outer_le, new_end);
  }
  // the backedge of the inner loop must be rewired to the new loop end
  Node* backedge = cle->proj_out(true);
  igvn->replace_input_of(backedge, 0, new_end);
  if (iloop != nullptr) {
    iloop->set_idom(backedge, new_end, iloop->dom_depth(new_end) + 1);
  }
  // make the outer loop go away
  igvn->replace_input_of(in(LoopBackControl), 0, igvn->C->top());
  igvn->replace_input_of(this, LoopBackControl, igvn->C->top());
  inner_cl->clear_strip_mined();
  if (iloop != nullptr) {
    Unique_Node_List wq;
    wq.push(safepoint);

    IdealLoopTree* outer_loop_ilt = iloop->get_loop(this);
    IdealLoopTree* loop = iloop->get_loop(inner_cl);

    for (uint i = 0; i < wq.size(); i++) {
      Node* n = wq.at(i);
      for (uint j = 0; j < n->req(); ++j) {
        Node* in = n->in(j);
        if (in == nullptr || in->is_CFG()) {
          continue;
        }
        if (iloop->get_loop(iloop->get_ctrl(in)) != outer_loop_ilt) {
          continue;
        }
        assert(!loop->_body.contains(in), "");
        loop->_body.push(in);
        wq.push(in);
      }
    }
    iloop->set_loop(safepoint, loop);
    loop->_body.push(safepoint);
    iloop->set_loop(safepoint->in(0), loop);
    loop->_body.push(safepoint->in(0));
    outer_loop_ilt->_tail = igvn->C->top();
  }
}

void OuterStripMinedLoopNode::remove_outer_loop_and_safepoint(PhaseIterGVN* igvn) const {
  CountedLoopNode* inner_cl = unique_ctrl_out()->as_CountedLoop();
  Node* outer_sfpt = outer_safepoint();
  Node* outer_out = outer_loop_exit();
  igvn->replace_node(outer_out, outer_sfpt->in(0));
  igvn->replace_input_of(outer_sfpt, 0, igvn->C->top());
  inner_cl->clear_strip_mined();
}

Node* OuterStripMinedLoopNode::register_new_node(Node* node, LoopNode* ctrl, PhaseIterGVN* igvn, PhaseIdealLoop* iloop) {
  if (iloop == nullptr) {
    return igvn->transform(node);
  }
  iloop->register_new_node(node, ctrl);
  return node;
}

Node* OuterStripMinedLoopNode::register_control(Node* node, Node* loop, Node* idom, PhaseIterGVN* igvn,
                                                PhaseIdealLoop* iloop) {
  if (iloop == nullptr) {
    return igvn->transform(node);
  }
  iloop->register_control(node, iloop->get_loop(loop), idom);
  return node;
}

const Type* OuterStripMinedLoopEndNode::Value(PhaseGVN* phase) const {
  if (!in(0)) return Type::TOP;
  if (phase->type(in(0)) == Type::TOP)
    return Type::TOP;

  // Until expansion, the loop end condition is not set so this should not constant fold.
  if (is_expanded(phase)) {
    return IfNode::Value(phase);
  }

  return TypeTuple::IFBOTH;
}

bool OuterStripMinedLoopEndNode::is_expanded(PhaseGVN *phase) const {
  // The outer strip mined loop head only has Phi uses after expansion
  if (phase->is_IterGVN()) {
    Node* backedge = proj_out_or_null(true);
    if (backedge != nullptr) {
      Node* head = backedge->unique_ctrl_out_or_null();
      if (head != nullptr && head->is_OuterStripMinedLoop()) {
        if (head->find_out_with(Op_Phi) != nullptr) {
          return true;
        }
      }
    }
  }
  return false;
}

Node *OuterStripMinedLoopEndNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if (remove_dead_region(phase, can_reshape))  return this;

  return nullptr;
}

//------------------------------filtered_type--------------------------------
// Return a type based on condition control flow
// A successful return will be a type that is restricted due
// to a series of dominating if-tests, such as:
//    if (i < 10) {
//       if (i > 0) {
//          here: "i" type is [1..10)
//       }
//    }
// or a control flow merge
//    if (i < 10) {
//       do {
//          phi( , ) -- at top of loop type is [min_int..10)
//         i = ?
//       } while ( i < 10)
//
const TypeInt* PhaseIdealLoop::filtered_type( Node *n, Node* n_ctrl) {
  assert(n && n->bottom_type()->is_int(), "must be int");
  const TypeInt* filtered_t = nullptr;
  if (!n->is_Phi()) {
    assert(n_ctrl != nullptr || n_ctrl == C->top(), "valid control");
    filtered_t = filtered_type_from_dominators(n, n_ctrl);

  } else {
    Node* phi    = n->as_Phi();
    Node* region = phi->in(0);
    assert(n_ctrl == nullptr || n_ctrl == region, "ctrl parameter must be region");
    if (region && region != C->top()) {
      for (uint i = 1; i < phi->req(); i++) {
        Node* val   = phi->in(i);
        Node* use_c = region->in(i);
        const TypeInt* val_t = filtered_type_from_dominators(val, use_c);
        if (val_t != nullptr) {
          if (filtered_t == nullptr) {
            filtered_t = val_t;
          } else {
            filtered_t = filtered_t->meet(val_t)->is_int();
          }
        }
      }
    }
  }
  const TypeInt* n_t = _igvn.type(n)->is_int();
  if (filtered_t != nullptr) {
    n_t = n_t->join(filtered_t)->is_int();
  }
  return n_t;
}


//------------------------------filtered_type_from_dominators--------------------------------
// Return a possibly more restrictive type for val based on condition control flow of dominators
const TypeInt* PhaseIdealLoop::filtered_type_from_dominators( Node* val, Node *use_ctrl) {
  if (val->is_Con()) {
     return val->bottom_type()->is_int();
  }
  uint if_limit = 10; // Max number of dominating if's visited
  const TypeInt* rtn_t = nullptr;

  if (use_ctrl && use_ctrl != C->top()) {
    Node* val_ctrl = get_ctrl(val);
    uint val_dom_depth = dom_depth(val_ctrl);
    Node* pred = use_ctrl;
    uint if_cnt = 0;
    while (if_cnt < if_limit) {
      if ((pred->Opcode() == Op_IfTrue || pred->Opcode() == Op_IfFalse)) {
        if_cnt++;
        const TypeInt* if_t = IfNode::filtered_int_type(&_igvn, val, pred);
        if (if_t != nullptr) {
          if (rtn_t == nullptr) {
            rtn_t = if_t;
          } else {
            rtn_t = rtn_t->join(if_t)->is_int();
          }
        }
      }
      pred = idom(pred);
      if (pred == nullptr || pred == C->top()) {
        break;
      }
      // Stop if going beyond definition block of val
      if (dom_depth(pred) < val_dom_depth) {
        break;
      }
    }
  }
  return rtn_t;
}


//------------------------------dump_spec--------------------------------------
// Dump special per-node info
#ifndef PRODUCT
void CountedLoopEndNode::dump_spec(outputStream *st) const {
  if( in(TestValue) != nullptr && in(TestValue)->is_Bool() ) {
    BoolTest bt( test_trip()); // Added this for g++.

    st->print("[");
    bt.dump_on(st);
    st->print("]");
  }
  st->print(" ");
  IfNode::dump_spec(st);
}
#endif

//=============================================================================
//------------------------------is_member--------------------------------------
// Is 'l' a member of 'this'?
bool IdealLoopTree::is_member(const IdealLoopTree *l) const {
  while( l->_nest > _nest ) l = l->_parent;
  return l == this;
}

//------------------------------set_nest---------------------------------------
// Set loop tree nesting depth.  Accumulate _has_call bits.
int IdealLoopTree::set_nest( uint depth ) {
  assert(depth <= SHRT_MAX, "sanity");
  _nest = depth;
  int bits = _has_call;
  if( _child ) bits |= _child->set_nest(depth+1);
  if( bits ) _has_call = 1;
  if( _next  ) bits |= _next ->set_nest(depth  );
  return bits;
}

//------------------------------split_fall_in----------------------------------
// Split out multiple fall-in edges from the loop header.  Move them to a
// private RegionNode before the loop.  This becomes the loop landing pad.
void IdealLoopTree::split_fall_in( PhaseIdealLoop *phase, int fall_in_cnt ) {
  PhaseIterGVN &igvn = phase->_igvn;
  uint i;

  // Make a new RegionNode to be the landing pad.
  RegionNode* landing_pad = new RegionNode(fall_in_cnt + 1);
  phase->set_loop(landing_pad,_parent);
  // If _head was irreducible loop entry, landing_pad may now be too
  landing_pad->set_loop_status(_head->as_Region()->loop_status());
  // Gather all the fall-in control paths into the landing pad
  uint icnt = fall_in_cnt;
  uint oreq = _head->req();
  for( i = oreq-1; i>0; i-- )
    if( !phase->is_member( this, _head->in(i) ) )
      landing_pad->set_req(icnt--,_head->in(i));

  // Peel off PhiNode edges as well
  for (DUIterator_Fast jmax, j = _head->fast_outs(jmax); j < jmax; j++) {
    Node *oj = _head->fast_out(j);
    if( oj->is_Phi() ) {
      PhiNode* old_phi = oj->as_Phi();
      assert( old_phi->region() == _head, "" );
      igvn.hash_delete(old_phi);   // Yank from hash before hacking edges
      Node *p = PhiNode::make_blank(landing_pad, old_phi);
      uint icnt = fall_in_cnt;
      for( i = oreq-1; i>0; i-- ) {
        if( !phase->is_member( this, _head->in(i) ) ) {
          p->init_req(icnt--, old_phi->in(i));
          // Go ahead and clean out old edges from old phi
          old_phi->del_req(i);
        }
      }
      // Search for CSE's here, because ZKM.jar does a lot of
      // loop hackery and we need to be a little incremental
      // with the CSE to avoid O(N^2) node blow-up.
      Node *p2 = igvn.hash_find_insert(p); // Look for a CSE
      if( p2 ) {                // Found CSE
        p->destruct(&igvn);     // Recover useless new node
        p = p2;                 // Use old node
      } else {
        igvn.register_new_node_with_optimizer(p, old_phi);
      }
      // Make old Phi refer to new Phi.
      old_phi->add_req(p);
      // Check for the special case of making the old phi useless and
      // disappear it.  In JavaGrande I have a case where this useless
      // Phi is the loop limit and prevents recognizing a CountedLoop
      // which in turn prevents removing an empty loop.
      Node *id_old_phi = old_phi->Identity(&igvn);
      if( id_old_phi != old_phi ) { // Found a simple identity?
        // Note that I cannot call 'replace_node' here, because
        // that will yank the edge from old_phi to the Region and
        // I'm mid-iteration over the Region's uses.
        for (DUIterator_Last imin, i = old_phi->last_outs(imin); i >= imin; ) {
          Node* use = old_phi->last_out(i);
          igvn.rehash_node_delayed(use);
          uint uses_found = 0;
          for (uint j = 0; j < use->len(); j++) {
            if (use->in(j) == old_phi) {
              if (j < use->req()) use->set_req (j, id_old_phi);
              else                use->set_prec(j, id_old_phi);
              uses_found++;
            }
          }
          i -= uses_found;    // we deleted 1 or more copies of this edge
        }
      }
      igvn._worklist.push(old_phi);
    }
  }
  // Finally clean out the fall-in edges from the RegionNode
  for( i = oreq-1; i>0; i-- ) {
    if( !phase->is_member( this, _head->in(i) ) ) {
      _head->del_req(i);
    }
  }
  igvn.rehash_node_delayed(_head);
  // Transform landing pad
  igvn.register_new_node_with_optimizer(landing_pad, _head);
  // Insert landing pad into the header
  _head->add_req(landing_pad);
}

//------------------------------split_outer_loop-------------------------------
// Split out the outermost loop from this shared header.
void IdealLoopTree::split_outer_loop( PhaseIdealLoop *phase ) {
  PhaseIterGVN &igvn = phase->_igvn;

  // Find index of outermost loop; it should also be my tail.
  uint outer_idx = 1;
  while( _head->in(outer_idx) != _tail ) outer_idx++;

  // Make a LoopNode for the outermost loop.
  Node *ctl = _head->in(LoopNode::EntryControl);
  Node *outer = new LoopNode( ctl, _head->in(outer_idx) );
  outer = igvn.register_new_node_with_optimizer(outer, _head);
  phase->set_created_loop_node();

  // Outermost loop falls into '_head' loop
  _head->set_req(LoopNode::EntryControl, outer);
  _head->del_req(outer_idx);
  // Split all the Phis up between '_head' loop and 'outer' loop.
  for (DUIterator_Fast jmax, j = _head->fast_outs(jmax); j < jmax; j++) {
    Node *out = _head->fast_out(j);
    if( out->is_Phi() ) {
      PhiNode *old_phi = out->as_Phi();
      assert( old_phi->region() == _head, "" );
      Node *phi = PhiNode::make_blank(outer, old_phi);
      phi->init_req(LoopNode::EntryControl,    old_phi->in(LoopNode::EntryControl));
      phi->init_req(LoopNode::LoopBackControl, old_phi->in(outer_idx));
      phi = igvn.register_new_node_with_optimizer(phi, old_phi);
      // Make old Phi point to new Phi on the fall-in path
      igvn.replace_input_of(old_phi, LoopNode::EntryControl, phi);
      old_phi->del_req(outer_idx);
    }
  }

  // Use the new loop head instead of the old shared one
  _head = outer;
  phase->set_loop(_head, this);
}

//------------------------------fix_parent-------------------------------------
static void fix_parent( IdealLoopTree *loop, IdealLoopTree *parent ) {
  loop->_parent = parent;
  if( loop->_child ) fix_parent( loop->_child, loop   );
  if( loop->_next  ) fix_parent( loop->_next , parent );
}

//------------------------------estimate_path_freq-----------------------------
static float estimate_path_freq( Node *n ) {
  // Try to extract some path frequency info
  IfNode *iff;
  for( int i = 0; i < 50; i++ ) { // Skip through a bunch of uncommon tests
    uint nop = n->Opcode();
    if( nop == Op_SafePoint ) {   // Skip any safepoint
      n = n->in(0);
      continue;
    }
    if( nop == Op_CatchProj ) {   // Get count from a prior call
      // Assume call does not always throw exceptions: means the call-site
      // count is also the frequency of the fall-through path.
      assert( n->is_CatchProj(), "" );
      if( ((CatchProjNode*)n)->_con != CatchProjNode::fall_through_index )
        return 0.0f;            // Assume call exception path is rare
      Node *call = n->in(0)->in(0)->in(0);
      assert( call->is_Call(), "expect a call here" );
      const JVMState *jvms = ((CallNode*)call)->jvms();
      ciMethodData* methodData = jvms->method()->method_data();
      if (!methodData->is_mature())  return 0.0f; // No call-site data
      ciProfileData* data = methodData->bci_to_data(jvms->bci());
      if ((data == nullptr) || !data->is_CounterData()) {
        // no call profile available, try call's control input
        n = n->in(0);
        continue;
      }
      return data->as_CounterData()->count()/FreqCountInvocations;
    }
    // See if there's a gating IF test
    Node *n_c = n->in(0);
    if( !n_c->is_If() ) break;       // No estimate available
    iff = n_c->as_If();
    if( iff->_fcnt != COUNT_UNKNOWN )   // Have a valid count?
      // Compute how much count comes on this path
      return ((nop == Op_IfTrue) ? iff->_prob : 1.0f - iff->_prob) * iff->_fcnt;
    // Have no count info.  Skip dull uncommon-trap like branches.
    if( (nop == Op_IfTrue  && iff->_prob < PROB_LIKELY_MAG(5)) ||
        (nop == Op_IfFalse && iff->_prob > PROB_UNLIKELY_MAG(5)) )
      break;
    // Skip through never-taken branch; look for a real loop exit.
    n = iff->in(0);
  }
  return 0.0f;                  // No estimate available
}

//------------------------------merge_many_backedges---------------------------
// Merge all the backedges from the shared header into a private Region.
// Feed that region as the one backedge to this loop.
void IdealLoopTree::merge_many_backedges( PhaseIdealLoop *phase ) {
  uint i;

  // Scan for the top 2 hottest backedges
  float hotcnt = 0.0f;
  float warmcnt = 0.0f;
  uint hot_idx = 0;
  // Loop starts at 2 because slot 1 is the fall-in path
  for( i = 2; i < _head->req(); i++ ) {
    float cnt = estimate_path_freq(_head->in(i));
    if( cnt > hotcnt ) {       // Grab hottest path
      warmcnt = hotcnt;
      hotcnt = cnt;
      hot_idx = i;
    } else if( cnt > warmcnt ) { // And 2nd hottest path
      warmcnt = cnt;
    }
  }

  // See if the hottest backedge is worthy of being an inner loop
  // by being much hotter than the next hottest backedge.
  if( hotcnt <= 0.0001 ||
      hotcnt < 2.0*warmcnt ) hot_idx = 0;// No hot backedge

  // Peel out the backedges into a private merge point; peel
  // them all except optionally hot_idx.
  PhaseIterGVN &igvn = phase->_igvn;

  Node *hot_tail = nullptr;
  // Make a Region for the merge point
  Node *r = new RegionNode(1);
  for( i = 2; i < _head->req(); i++ ) {
    if( i != hot_idx )
      r->add_req( _head->in(i) );
    else hot_tail = _head->in(i);
  }
  igvn.register_new_node_with_optimizer(r, _head);
  // Plug region into end of loop _head, followed by hot_tail
  while( _head->req() > 3 ) _head->del_req( _head->req()-1 );
  igvn.replace_input_of(_head, 2, r);
  if( hot_idx ) _head->add_req(hot_tail);

  // Split all the Phis up between '_head' loop and the Region 'r'
  for (DUIterator_Fast jmax, j = _head->fast_outs(jmax); j < jmax; j++) {
    Node *out = _head->fast_out(j);
    if( out->is_Phi() ) {
      PhiNode* n = out->as_Phi();
      igvn.hash_delete(n);      // Delete from hash before hacking edges
      Node *hot_phi = nullptr;
      Node *phi = new PhiNode(r, n->type(), n->adr_type());
      // Check all inputs for the ones to peel out
      uint j = 1;
      for( uint i = 2; i < n->req(); i++ ) {
        if( i != hot_idx )
          phi->set_req( j++, n->in(i) );
        else hot_phi = n->in(i);
      }
      // Register the phi but do not transform until whole place transforms
      igvn.register_new_node_with_optimizer(phi, n);
      // Add the merge phi to the old Phi
      while( n->req() > 3 ) n->del_req( n->req()-1 );
      igvn.replace_input_of(n, 2, phi);
      if( hot_idx ) n->add_req(hot_phi);
    }
  }


  // Insert a new IdealLoopTree inserted below me.  Turn it into a clone
  // of self loop tree.  Turn self into a loop headed by _head and with
  // tail being the new merge point.
  IdealLoopTree *ilt = new IdealLoopTree( phase, _head, _tail );
  phase->set_loop(_tail,ilt);   // Adjust tail
  _tail = r;                    // Self's tail is new merge point
  phase->set_loop(r,this);
  ilt->_child = _child;         // New guy has my children
  _child = ilt;                 // Self has new guy as only child
  ilt->_parent = this;          // new guy has self for parent
  ilt->_nest = _nest;           // Same nesting depth (for now)

  // Starting with 'ilt', look for child loop trees using the same shared
  // header.  Flatten these out; they will no longer be loops in the end.
  IdealLoopTree **pilt = &_child;
  while( ilt ) {
    if( ilt->_head == _head ) {
      uint i;
      for( i = 2; i < _head->req(); i++ )
        if( _head->in(i) == ilt->_tail )
          break;                // Still a loop
      if( i == _head->req() ) { // No longer a loop
        // Flatten ilt.  Hang ilt's "_next" list from the end of
        // ilt's '_child' list.  Move the ilt's _child up to replace ilt.
        IdealLoopTree **cp = &ilt->_child;
        while( *cp ) cp = &(*cp)->_next;   // Find end of child list
        *cp = ilt->_next;       // Hang next list at end of child list
        *pilt = ilt->_child;    // Move child up to replace ilt
        ilt->_head = nullptr;   // Flag as a loop UNIONED into parent
        ilt = ilt->_child;      // Repeat using new ilt
        continue;               // do not advance over ilt->_child
      }
      assert( ilt->_tail == hot_tail, "expected to only find the hot inner loop here" );
      phase->set_loop(_head,ilt);
    }
    pilt = &ilt->_child;        // Advance to next
    ilt = *pilt;
  }

  if( _child ) fix_parent( _child, this );
}

//------------------------------beautify_loops---------------------------------
// Split shared headers and insert loop landing pads.
// Insert a LoopNode to replace the RegionNode.
// Return TRUE if loop tree is structurally changed.
bool IdealLoopTree::beautify_loops( PhaseIdealLoop *phase ) {
  bool result = false;
  // Cache parts in locals for easy
  PhaseIterGVN &igvn = phase->_igvn;

  igvn.hash_delete(_head);      // Yank from hash before hacking edges

  // Check for multiple fall-in paths.  Peel off a landing pad if need be.
  int fall_in_cnt = 0;
  for( uint i = 1; i < _head->req(); i++ )
    if( !phase->is_member( this, _head->in(i) ) )
      fall_in_cnt++;
  assert( fall_in_cnt, "at least 1 fall-in path" );
  if( fall_in_cnt > 1 )         // Need a loop landing pad to merge fall-ins
    split_fall_in( phase, fall_in_cnt );

  // Swap inputs to the _head and all Phis to move the fall-in edge to
  // the left.
  fall_in_cnt = 1;
  while( phase->is_member( this, _head->in(fall_in_cnt) ) )
    fall_in_cnt++;
  if( fall_in_cnt > 1 ) {
    // Since I am just swapping inputs I do not need to update def-use info
    Node *tmp = _head->in(1);
    igvn.rehash_node_delayed(_head);
    _head->set_req( 1, _head->in(fall_in_cnt) );
    _head->set_req( fall_in_cnt, tmp );
    // Swap also all Phis
    for (DUIterator_Fast imax, i = _head->fast_outs(imax); i < imax; i++) {
      Node* phi = _head->fast_out(i);
      if( phi->is_Phi() ) {
        igvn.rehash_node_delayed(phi); // Yank from hash before hacking edges
        tmp = phi->in(1);
        phi->set_req( 1, phi->in(fall_in_cnt) );
        phi->set_req( fall_in_cnt, tmp );
      }
    }
  }
  assert( !phase->is_member( this, _head->in(1) ), "left edge is fall-in" );
  assert(  phase->is_member( this, _head->in(2) ), "right edge is loop" );

  // If I am a shared header (multiple backedges), peel off the many
  // backedges into a private merge point and use the merge point as
  // the one true backedge.
  if (_head->req() > 3) {
    // Merge the many backedges into a single backedge but leave
    // the hottest backedge as separate edge for the following peel.
    if (!_irreducible) {
      merge_many_backedges( phase );
    }

    // When recursively beautify my children, split_fall_in can change
    // loop tree structure when I am an irreducible loop. Then the head
    // of my children has a req() not bigger than 3. Here we need to set
    // result to true to catch that case in order to tell the caller to
    // rebuild loop tree. See issue JDK-8244407 for details.
    result = true;
  }

  // If I have one hot backedge, peel off myself loop.
  // I better be the outermost loop.
  if (_head->req() > 3 && !_irreducible) {
    split_outer_loop( phase );
    result = true;

  } else if (!_head->is_Loop() && !_irreducible) {
    // Make a new LoopNode to replace the old loop head
    Node *l = new LoopNode( _head->in(1), _head->in(2) );
    l = igvn.register_new_node_with_optimizer(l, _head);
    phase->set_created_loop_node();
    // Go ahead and replace _head
    phase->_igvn.replace_node( _head, l );
    _head = l;
    phase->set_loop(_head, this);
  }

  // Now recursively beautify nested loops
  if( _child ) result |= _child->beautify_loops( phase );
  if( _next  ) result |= _next ->beautify_loops( phase );
  return result;
}

//------------------------------allpaths_check_safepts----------------------------
// Allpaths backwards scan. Starting at the head, traversing all backedges, and the body. Terminating each path at first
// safepoint encountered.  Helper for check_safepts.
void IdealLoopTree::allpaths_check_safepts(VectorSet &visited, Node_List &stack) {
  assert(stack.size() == 0, "empty stack");
  stack.push(_head);
  visited.clear();
  visited.set(_head->_idx);
  while (stack.size() > 0) {
    Node* n = stack.pop();
    if (n->is_Call() && n->as_Call()->guaranteed_safepoint()) {
      // Terminate this path
    } else if (n->Opcode() == Op_SafePoint) {
      if (_phase->get_loop(n) != this) {
        if (_required_safept == nullptr) _required_safept = new Node_List();
        // save the first we run into on that path: closest to the tail if the head has a single backedge
        _required_safept->push(n);
      }
      // Terminate this path
    } else {
      uint start = n->is_Region() ? 1 : 0;
      uint end   = n->is_Region() && (!n->is_Loop() || n == _head) ? n->req() : start + 1;
      for (uint i = start; i < end; i++) {
        Node* in = n->in(i);
        assert(in->is_CFG(), "must be");
        if (!visited.test_set(in->_idx) && is_member(_phase->get_loop(in))) {
          stack.push(in);
        }
      }
    }
  }
}

//------------------------------check_safepts----------------------------
// Given dominators, try to find loops with calls that must always be
// executed (call dominates loop tail).  These loops do not need non-call
// safepoints (ncsfpt).
//
// A complication is that a safepoint in a inner loop may be needed
// by an outer loop. In the following, the inner loop sees it has a
// call (block 3) on every path from the head (block 2) to the
// backedge (arc 3->2).  So it deletes the ncsfpt (non-call safepoint)
// in block 2, _but_ this leaves the outer loop without a safepoint.
//
//          entry  0
//                 |
//                 v
// outer 1,2    +->1
//              |  |
//              |  v
//              |  2<---+  ncsfpt in 2
//              |_/|\   |
//                 | v  |
// inner 2,3      /  3  |  call in 3
//               /   |  |
//              v    +--+
//        exit  4
//
//
// This method creates a list (_required_safept) of ncsfpt nodes that must
// be protected is created for each loop. When a ncsfpt maybe deleted, it
// is first looked for in the lists for the outer loops of the current loop.
//
// The insights into the problem:
//  A) counted loops are okay
//  B) innermost loops are okay (only an inner loop can delete
//     a ncsfpt needed by an outer loop)
//  C) a loop is immune from an inner loop deleting a safepoint
//     if the loop has a call on the idom-path
//  D) a loop is also immune if it has a ncsfpt (non-call safepoint) on the
//     idom-path that is not in a nested loop
//  E) otherwise, an ncsfpt on the idom-path that is nested in an inner
//     loop needs to be prevented from deletion by an inner loop
//
// There are two analyses:
//  1) The first, and cheaper one, scans the loop body from
//     tail to head following the idom (immediate dominator)
//     chain, looking for the cases (C,D,E) above.
//     Since inner loops are scanned before outer loops, there is summary
//     information about inner loops.  Inner loops can be skipped over
//     when the tail of an inner loop is encountered.
//
//  2) The second, invoked if the first fails to find a call or ncsfpt on
//     the idom path (which is rare), scans all predecessor control paths
//     from the tail to the head, terminating a path when a call or sfpt
//     is encountered, to find the ncsfpt's that are closest to the tail.
//
void IdealLoopTree::check_safepts(VectorSet &visited, Node_List &stack) {
  // Bottom up traversal
  IdealLoopTree* ch = _child;
  if (_child) _child->check_safepts(visited, stack);
  if (_next)  _next ->check_safepts(visited, stack);

  if (!_head->is_CountedLoop() && !_has_sfpt && _parent != nullptr) {
    bool  has_call         = false;    // call on dom-path
    bool  has_local_ncsfpt = false;    // ncsfpt on dom-path at this loop depth
    Node* nonlocal_ncsfpt  = nullptr;  // ncsfpt on dom-path at a deeper depth
    if (!_irreducible) {
      // Scan the dom-path nodes from tail to head
      for (Node* n = tail(); n != _head; n = _phase->idom(n)) {
        if (n->is_Call() && n->as_Call()->guaranteed_safepoint()) {
          has_call = true;
          _has_sfpt = 1;          // Then no need for a safept!
          break;
        } else if (n->Opcode() == Op_SafePoint) {
          if (_phase->get_loop(n) == this) {
            has_local_ncsfpt = true;
            break;
          }
          if (nonlocal_ncsfpt == nullptr) {
            nonlocal_ncsfpt = n; // save the one closest to the tail
          }
        } else {
          IdealLoopTree* nlpt = _phase->get_loop(n);
          if (this != nlpt) {
            // If at an inner loop tail, see if the inner loop has already
            // recorded seeing a call on the dom-path (and stop.)  If not,
            // jump to the head of the inner loop.
            assert(is_member(nlpt), "nested loop");
            Node* tail = nlpt->_tail;
            if (tail->in(0)->is_If()) tail = tail->in(0);
            if (n == tail) {
              // If inner loop has call on dom-path, so does outer loop
              if (nlpt->_has_sfpt) {
                has_call = true;
                _has_sfpt = 1;
                break;
              }
              // Skip to head of inner loop
              assert(_phase->is_dominator(_head, nlpt->_head), "inner head dominated by outer head");
              n = nlpt->_head;
              if (_head == n) {
                // this and nlpt (inner loop) have the same loop head. This should not happen because
                // during beautify_loops we call merge_many_backedges. However, infinite loops may not
                // have been attached to the loop-tree during build_loop_tree before beautify_loops,
                // but then attached in the build_loop_tree afterwards, and so still have unmerged
                // backedges. Check if we are indeed in an infinite subgraph, and terminate the scan,
                // since we have reached the loop head of this.
                assert(_head->as_Region()->is_in_infinite_subgraph(),
                       "only expect unmerged backedges in infinite loops");
                break;
              }
            }
          }
        }
      }
    }
    // Record safept's that this loop needs preserved when an
    // inner loop attempts to delete it's safepoints.
    if (_child != nullptr && !has_call && !has_local_ncsfpt) {
      if (nonlocal_ncsfpt != nullptr) {
        if (_required_safept == nullptr) _required_safept = new Node_List();
        _required_safept->push(nonlocal_ncsfpt);
      } else {
        // Failed to find a suitable safept on the dom-path.  Now use
        // an all paths walk from tail to head, looking for safepoints to preserve.
        allpaths_check_safepts(visited, stack);
      }
    }
  }
}

//---------------------------is_deleteable_safept----------------------------
// Is safept not required by an outer loop?
bool PhaseIdealLoop::is_deleteable_safept(Node* sfpt) {
  assert(sfpt->Opcode() == Op_SafePoint, "");
  IdealLoopTree* lp = get_loop(sfpt)->_parent;
  while (lp != nullptr) {
    Node_List* sfpts = lp->_required_safept;
    if (sfpts != nullptr) {
      for (uint i = 0; i < sfpts->size(); i++) {
        if (sfpt == sfpts->at(i))
          return false;
      }
    }
    lp = lp->_parent;
  }
  return true;
}

//---------------------------replace_parallel_iv-------------------------------
// Replace parallel induction variable (parallel to trip counter)
void PhaseIdealLoop::replace_parallel_iv(IdealLoopTree *loop) {
  assert(loop->_head->is_CountedLoop(), "");
  CountedLoopNode *cl = loop->_head->as_CountedLoop();
  if (!cl->is_valid_counted_loop(T_INT)) {
    return;         // skip malformed counted loop
  }
  Node *incr = cl->incr();
  if (incr == nullptr) {
    return;         // Dead loop?
  }
  Node *init = cl->init_trip();
  Node *phi  = cl->phi();
  int stride_con = cl->stride_con();

  // Visit all children, looking for Phis
  for (DUIterator i = cl->outs(); cl->has_out(i); i++) {
    Node *out = cl->out(i);
    // Look for other phis (secondary IVs). Skip dead ones
    if (!out->is_Phi() || out == phi || !has_node(out)) {
      continue;
    }

    PhiNode* phi2 = out->as_Phi();
    Node* incr2 = phi2->in(LoopNode::LoopBackControl);
    // Look for induction variables of the form:  X += constant
    if (phi2->region() != loop->_head ||
        incr2->req() != 3 ||
        incr2->in(1)->uncast() != phi2 ||
        incr2 == incr ||
        incr2->Opcode() != Op_AddI ||
        !incr2->in(2)->is_Con()) {
      continue;
    }

    if (incr2->in(1)->is_ConstraintCast() &&
        !(incr2->in(1)->in(0)->is_IfProj() && incr2->in(1)->in(0)->in(0)->is_RangeCheck())) {
      // Skip AddI->CastII->Phi case if CastII is not controlled by local RangeCheck
      continue;
    }
    // Check for parallel induction variable (parallel to trip counter)
    // via an affine function.  In particular, count-down loops with
    // count-up array indices are common. We only RCE references off
    // the trip-counter, so we need to convert all these to trip-counter
    // expressions.
    Node* init2 = phi2->in(LoopNode::EntryControl);
    int stride_con2 = incr2->in(2)->get_int();

    // The ratio of the two strides cannot be represented as an int
    // if stride_con2 is min_int and stride_con is -1.
    if (stride_con2 == min_jint && stride_con == -1) {
      continue;
    }

    // The general case here gets a little tricky.  We want to find the
    // GCD of all possible parallel IV's and make a new IV using this
    // GCD for the loop.  Then all possible IVs are simple multiples of
    // the GCD.  In practice, this will cover very few extra loops.
    // Instead we require 'stride_con2' to be a multiple of 'stride_con',
    // where +/-1 is the common case, but other integer multiples are
    // also easy to handle.
    int ratio_con = stride_con2/stride_con;

    if ((ratio_con * stride_con) == stride_con2) { // Check for exact
#ifndef PRODUCT
      if (TraceLoopOpts) {
        tty->print("Parallel IV: %d ", phi2->_idx);
        loop->dump_head();
      }
#endif
      // Convert to using the trip counter.  The parallel induction
      // variable differs from the trip counter by a loop-invariant
      // amount, the difference between their respective initial values.
      // It is scaled by the 'ratio_con'.
      Node* ratio = _igvn.intcon(ratio_con);
      set_ctrl(ratio, C->root());
      Node* ratio_init = new MulINode(init, ratio);
      _igvn.register_new_node_with_optimizer(ratio_init, init);
      set_early_ctrl(ratio_init, false);
      Node* diff = new SubINode(init2, ratio_init);
      _igvn.register_new_node_with_optimizer(diff, init2);
      set_early_ctrl(diff, false);
      Node* ratio_idx = new MulINode(phi, ratio);
      _igvn.register_new_node_with_optimizer(ratio_idx, phi);
      set_ctrl(ratio_idx, cl);
      Node* add = new AddINode(ratio_idx, diff);
      _igvn.register_new_node_with_optimizer(add);
      set_ctrl(add, cl);
      _igvn.replace_node( phi2, add );
      // Sometimes an induction variable is unused
      if (add->outcnt() == 0) {
        _igvn.remove_dead_node(add);
      }
      --i; // deleted this phi; rescan starting with next position
      continue;
    }
  }
}

void IdealLoopTree::remove_safepoints(PhaseIdealLoop* phase, bool keep_one) {
  Node* keep = nullptr;
  if (keep_one) {
    // Look for a safepoint on the idom-path.
    for (Node* i = tail(); i != _head; i = phase->idom(i)) {
      if (i->Opcode() == Op_SafePoint && phase->get_loop(i) == this) {
        keep = i;
        break; // Found one
      }
    }
  }

  // Don't remove any safepoints if it is requested to keep a single safepoint and
  // no safepoint was found on idom-path. It is not safe to remove any safepoint
  // in this case since there's no safepoint dominating all paths in the loop body.
  bool prune = !keep_one || keep != nullptr;

  // Delete other safepoints in this loop.
  Node_List* sfpts = _safepts;
  if (prune && sfpts != nullptr) {
    assert(keep == nullptr || keep->Opcode() == Op_SafePoint, "not safepoint");
    for (uint i = 0; i < sfpts->size(); i++) {
      Node* n = sfpts->at(i);
      assert(phase->get_loop(n) == this, "");
      if (n != keep && phase->is_deleteable_safept(n)) {
        phase->lazy_replace(n, n->in(TypeFunc::Control));
      }
    }
  }
}

//------------------------------counted_loop-----------------------------------
// Convert to counted loops where possible
void IdealLoopTree::counted_loop( PhaseIdealLoop *phase ) {

  // For grins, set the inner-loop flag here
  if (!_child) {
    if (_head->is_Loop()) _head->as_Loop()->set_inner_loop();
  }

  IdealLoopTree* loop = this;
  if (_head->is_CountedLoop() ||
      phase->is_counted_loop(_head, loop, T_INT)) {

    if (LoopStripMiningIter == 0 || _head->as_CountedLoop()->is_strip_mined()) {
      // Indicate we do not need a safepoint here
      _has_sfpt = 1;
    }

    // Remove safepoints
    bool keep_one_sfpt = !(_has_call || _has_sfpt);
    remove_safepoints(phase, keep_one_sfpt);

    // Look for induction variables
    phase->replace_parallel_iv(this);
  } else if (_head->is_LongCountedLoop() ||
             phase->is_counted_loop(_head, loop, T_LONG)) {
    remove_safepoints(phase, true);
  } else {
    assert(!_head->is_Loop() || !_head->as_Loop()->is_loop_nest_inner_loop(), "transformation to counted loop should not fail");
    if (_parent != nullptr && !_irreducible) {
      // Not a counted loop. Keep one safepoint.
      bool keep_one_sfpt = true;
      remove_safepoints(phase, keep_one_sfpt);
    }
  }

  // Recursively
  assert(loop->_child != this || (loop->_head->as_Loop()->is_OuterStripMinedLoop() && _head->as_CountedLoop()->is_strip_mined()), "what kind of loop was added?");
  assert(loop->_child != this || (loop->_child->_child == nullptr && loop->_child->_next == nullptr), "would miss some loops");
  if (loop->_child && loop->_child != this) loop->_child->counted_loop(phase);
  if (loop->_next)  loop->_next ->counted_loop(phase);
}


// The Estimated Loop Clone Size:
//   CloneFactor * (~112% * BodySize + BC) + CC + FanOutTerm,
// where  BC and  CC are  totally ad-hoc/magic  "body" and "clone" constants,
// respectively, used to ensure that the node usage estimates made are on the
// safe side, for the most part. The FanOutTerm is an attempt to estimate the
// possible additional/excessive nodes generated due to data and control flow
// merging, for edges reaching outside the loop.
uint IdealLoopTree::est_loop_clone_sz(uint factor) const {

  precond(0 < factor && factor < 16);

  uint const bc = 13;
  uint const cc = 17;
  uint const sz = _body.size() + (_body.size() + 7) / 2;
  uint estimate = factor * (sz + bc) + cc;

  assert((estimate - cc) / factor == sz + bc, "overflow");

  return estimate + est_loop_flow_merge_sz();
}

// The Estimated Loop (full-) Unroll Size:
//   UnrollFactor * (~106% * BodySize) + CC + FanOutTerm,
// where CC is a (totally) ad-hoc/magic "clone" constant, used to ensure that
// node usage estimates made are on the safe side, for the most part. This is
// a "light" version of the loop clone size calculation (above), based on the
// assumption that most of the loop-construct overhead will be unraveled when
// (fully) unrolled. Defined for unroll factors larger or equal to one (>=1),
// including an overflow check and returning UINT_MAX in case of an overflow.
uint IdealLoopTree::est_loop_unroll_sz(uint factor) const {

  precond(factor > 0);

  // Take into account that after unroll conjoined heads and tails will fold.
  uint const b0 = _body.size() - EMPTY_LOOP_SIZE;
  uint const cc = 7;
  uint const sz = b0 + (b0 + 15) / 16;
  uint estimate = factor * sz + cc;

  if ((estimate - cc) / factor != sz) {
    return UINT_MAX;
  }

  return estimate + est_loop_flow_merge_sz();
}

// Estimate the growth effect (in nodes) of merging control and data flow when
// cloning a loop body, based on the amount of  control and data flow reaching
// outside of the (current) loop body.
uint IdealLoopTree::est_loop_flow_merge_sz() const {

  uint ctrl_edge_out_cnt = 0;
  uint data_edge_out_cnt = 0;

  for (uint i = 0; i < _body.size(); i++) {
    Node* node = _body.at(i);
    uint outcnt = node->outcnt();

    for (uint k = 0; k < outcnt; k++) {
      Node* out = node->raw_out(k);
      if (out == nullptr) continue;
      if (out->is_CFG()) {
        if (!is_member(_phase->get_loop(out))) {
          ctrl_edge_out_cnt++;
        }
      } else if (_phase->has_ctrl(out)) {
        Node* ctrl = _phase->get_ctrl(out);
        assert(ctrl != nullptr, "must be");
        assert(ctrl->is_CFG(), "must be");
        if (!is_member(_phase->get_loop(ctrl))) {
          data_edge_out_cnt++;
        }
      }
    }
  }
  // Use data and control count (x2.0) in estimate iff both are > 0. This is
  // a rather pessimistic estimate for the most part, in particular for some
  // complex loops, but still not enough to capture all loops.
  if (ctrl_edge_out_cnt > 0 && data_edge_out_cnt > 0) {
    return 2 * (ctrl_edge_out_cnt + data_edge_out_cnt);
  }
  return 0;
}

#ifndef PRODUCT
//------------------------------dump_head--------------------------------------
// Dump 1 liner for loop header info
void IdealLoopTree::dump_head() {
  tty->sp(2 * _nest);
  tty->print("Loop: N%d/N%d ", _head->_idx, _tail->_idx);
  if (_irreducible) tty->print(" IRREDUCIBLE");
  Node* entry = _head->is_Loop() ? _head->as_Loop()->skip_strip_mined(-1)->in(LoopNode::EntryControl)
                                 : _head->in(LoopNode::EntryControl);
  const Predicates predicates(entry);
  if (predicates.loop_limit_check_predicate_block()->is_non_empty()) {
    tty->print(" limit_check");
  }
  if (UseProfiledLoopPredicate && predicates.profiled_loop_predicate_block()->is_non_empty()) {
    tty->print(" profile_predicated");
  }
  if (UseLoopPredicate && predicates.loop_predicate_block()->is_non_empty()) {
    tty->print(" predicated");
  }
  if (_head->is_CountedLoop()) {
    CountedLoopNode *cl = _head->as_CountedLoop();
    tty->print(" counted");

    Node* init_n = cl->init_trip();
    if (init_n  != nullptr &&  init_n->is_Con())
      tty->print(" [%d,", cl->init_trip()->get_int());
    else
      tty->print(" [int,");
    Node* limit_n = cl->limit();
    if (limit_n  != nullptr &&  limit_n->is_Con())
      tty->print("%d),", cl->limit()->get_int());
    else
      tty->print("int),");
    int stride_con  = cl->stride_con();
    if (stride_con > 0) tty->print("+");
    tty->print("%d", stride_con);

    tty->print(" (%0.f iters) ", cl->profile_trip_cnt());

    if (cl->is_pre_loop ()) tty->print(" pre" );
    if (cl->is_main_loop()) tty->print(" main");
    if (cl->is_post_loop()) tty->print(" post");
    if (cl->is_vectorized_loop()) tty->print(" vector");
    if (range_checks_present()) tty->print(" rc ");
  }
  if (_has_call) tty->print(" has_call");
  if (_has_sfpt) tty->print(" has_sfpt");
  if (_rce_candidate) tty->print(" rce");
  if (_safepts != nullptr && _safepts->size() > 0) {
    tty->print(" sfpts={"); _safepts->dump_simple(); tty->print(" }");
  }
  if (_required_safept != nullptr && _required_safept->size() > 0) {
    tty->print(" req={"); _required_safept->dump_simple(); tty->print(" }");
  }
  if (Verbose) {
    tty->print(" body={"); _body.dump_simple(); tty->print(" }");
  }
  if (_head->is_Loop() && _head->as_Loop()->is_strip_mined()) {
    tty->print(" strip_mined");
  }
  tty->cr();
}

//------------------------------dump-------------------------------------------
// Dump loops by loop tree
void IdealLoopTree::dump() {
  dump_head();
  if (_child) _child->dump();
  if (_next)  _next ->dump();
}

#endif

static void log_loop_tree_helper(IdealLoopTree* root, IdealLoopTree* loop, CompileLog* log) {
  if (loop == root) {
    if (loop->_child != nullptr) {
      log->begin_head("loop_tree");
      log->end_head();
      log_loop_tree_helper(root, loop->_child, log);
      log->tail("loop_tree");
      assert(loop->_next == nullptr, "what?");
    }
  } else if (loop != nullptr) {
    Node* head = loop->_head;
    log->begin_head("loop");
    log->print(" idx='%d' ", head->_idx);
    if (loop->_irreducible) log->print("irreducible='1' ");
    if (head->is_Loop()) {
      if (head->as_Loop()->is_inner_loop())        log->print("inner_loop='1' ");
      if (head->as_Loop()->is_partial_peel_loop()) log->print("partial_peel_loop='1' ");
    } else if (head->is_CountedLoop()) {
      CountedLoopNode* cl = head->as_CountedLoop();
      if (cl->is_pre_loop())  log->print("pre_loop='%d' ",  cl->main_idx());
      if (cl->is_main_loop()) log->print("main_loop='%d' ", cl->_idx);
      if (cl->is_post_loop()) log->print("post_loop='%d' ", cl->main_idx());
    }
    log->end_head();
    log_loop_tree_helper(root, loop->_child, log);
    log->tail("loop");
    log_loop_tree_helper(root, loop->_next, log);
  }
}

void PhaseIdealLoop::log_loop_tree() {
  if (C->log() != nullptr) {
    log_loop_tree_helper(_ltree_root, _ltree_root, C->log());
  }
}

// Eliminate all Parse and Template Assertion Predicates that are not associated with a loop anymore. The eliminated
// predicates will be removed during the next round of IGVN.
void PhaseIdealLoop::eliminate_useless_predicates() {
  if (C->parse_predicate_count() == 0 && C->template_assertion_predicate_count() == 0) {
    return; // No predicates left.
  }

  eliminate_useless_parse_predicates();
  eliminate_useless_template_assertion_predicates();
}

// Eliminate all Parse Predicates that do not belong to a loop anymore by marking them useless. These will be removed
// during the next round of IGVN.
void PhaseIdealLoop::eliminate_useless_parse_predicates() {
  mark_all_parse_predicates_useless();
  if (C->has_loops()) {
    mark_loop_associated_parse_predicates_useful();
  }
  add_useless_parse_predicates_to_igvn_worklist();
}

void PhaseIdealLoop::mark_all_parse_predicates_useless() const {
  for (int i = 0; i < C->parse_predicate_count(); i++) {
    C->parse_predicate(i)->mark_useless();
  }
}

void PhaseIdealLoop::mark_loop_associated_parse_predicates_useful() {
  for (LoopTreeIterator iterator(_ltree_root); !iterator.done(); iterator.next()) {
    IdealLoopTree* loop = iterator.current();
    if (loop->can_apply_loop_predication()) {
      mark_useful_parse_predicates_for_loop(loop);
    }
  }
}

void PhaseIdealLoop::mark_useful_parse_predicates_for_loop(IdealLoopTree* loop) {
  Node* entry = loop->_head->as_Loop()->skip_strip_mined()->in(LoopNode::EntryControl);
  const Predicates predicates(entry);
  ParsePredicateIterator iterator(predicates);
  while (iterator.has_next()) {
    iterator.next()->mark_useful();
  }
}

void PhaseIdealLoop::add_useless_parse_predicates_to_igvn_worklist() {
  for (int i = 0; i < C->parse_predicate_count(); i++) {
    ParsePredicateNode* parse_predicate_node = C->parse_predicate(i);
    if (parse_predicate_node->is_useless()) {
      _igvn._worklist.push(parse_predicate_node);
    }
  }
}


// Eliminate all Template Assertion Predicates that do not belong to their originally associated loop anymore by
// replacing the Opaque4 node of the If node with true. These nodes will be removed during the next round of IGVN.
void PhaseIdealLoop::eliminate_useless_template_assertion_predicates() {
  Unique_Node_List useful_predicates;
  if (C->has_loops()) {
    collect_useful_template_assertion_predicates(useful_predicates);
  }
  eliminate_useless_template_assertion_predicates(useful_predicates);
}

void PhaseIdealLoop::collect_useful_template_assertion_predicates(Unique_Node_List& useful_predicates) {
  for (LoopTreeIterator iterator(_ltree_root); !iterator.done(); iterator.next()) {
    IdealLoopTree* loop = iterator.current();
    if (loop->can_apply_loop_predication()) {
      collect_useful_template_assertion_predicates_for_loop(loop, useful_predicates);
    }
  }
}

void PhaseIdealLoop::collect_useful_template_assertion_predicates_for_loop(IdealLoopTree* loop,
                                                                           Unique_Node_List &useful_predicates) {
  Node* entry = loop->_head->as_Loop()->skip_strip_mined()->in(LoopNode::EntryControl);
  const Predicates predicates(entry);
  if (UseProfiledLoopPredicate) {
    const PredicateBlock* profiled_loop_predicate_block = predicates.profiled_loop_predicate_block();
    if (profiled_loop_predicate_block->has_parse_predicate()) {
      IfProjNode* parse_predicate_proj = profiled_loop_predicate_block->parse_predicate_success_proj();
      get_assertion_predicates(parse_predicate_proj, useful_predicates, true);
    }
  }

  if (UseLoopPredicate) {
    const PredicateBlock* loop_predicate_block = predicates.loop_predicate_block();
    if (loop_predicate_block->has_parse_predicate()) {
      IfProjNode* parse_predicate_proj = loop_predicate_block->parse_predicate_success_proj();
      get_assertion_predicates(parse_predicate_proj, useful_predicates, true);
    }
  }
}

void PhaseIdealLoop::eliminate_useless_template_assertion_predicates(Unique_Node_List& useful_predicates) {
  for (int i = C->template_assertion_predicate_count(); i > 0; i--) {
    Opaque4Node* opaque4_node = C->template_assertion_predicate_opaq_node(i - 1)->as_Opaque4();
    if (!useful_predicates.member(opaque4_node)) { // not in the useful list
      _igvn.replace_node(opaque4_node, opaque4_node->in(2));
    }
  }
}

// If a post or main loop is removed due to an assert predicate, the opaque that guards the loop is not needed anymore
void PhaseIdealLoop::eliminate_useless_zero_trip_guard() {
  if (_zero_trip_guard_opaque_nodes.size() == 0) {
    return;
  }
  Unique_Node_List useful_zero_trip_guard_opaques_nodes;
  for (LoopTreeIterator iter(_ltree_root); !iter.done(); iter.next()) {
    IdealLoopTree* lpt = iter.current();
    if (lpt->_child == nullptr && lpt->is_counted()) {
      CountedLoopNode* head = lpt->_head->as_CountedLoop();
      Node* opaque = head->is_canonical_loop_entry();
      if (opaque != nullptr) {
        useful_zero_trip_guard_opaques_nodes.push(opaque);
      }
    }
  }
  for (uint i = 0; i < _zero_trip_guard_opaque_nodes.size(); ++i) {
    OpaqueZeroTripGuardNode* opaque = ((OpaqueZeroTripGuardNode*)_zero_trip_guard_opaque_nodes.at(i));
    DEBUG_ONLY(CountedLoopNode* guarded_loop = opaque->guarded_loop());
    if (!useful_zero_trip_guard_opaques_nodes.member(opaque)) {
      IfNode* iff = opaque->if_node();
      IdealLoopTree* loop = get_loop(iff);
      while (loop != _ltree_root && loop != nullptr) {
        loop = loop->_parent;
      }
      if (loop == nullptr) {
        // unreachable from _ltree_root: zero trip guard is in a newly discovered infinite loop.
        // We can't tell if the opaque node is useful or not
        assert(guarded_loop == nullptr || guarded_loop->is_in_infinite_subgraph(), "");
      } else {
        assert(guarded_loop == nullptr, "");
        this->_igvn.replace_node(opaque, opaque->in(1));
      }
    } else {
      assert(guarded_loop != nullptr, "");
    }
  }
}

//------------------------process_expensive_nodes-----------------------------
// Expensive nodes have their control input set to prevent the GVN
// from commoning them and as a result forcing the resulting node to
// be in a more frequent path. Use CFG information here, to change the
// control inputs so that some expensive nodes can be commoned while
// not executed more frequently.
bool PhaseIdealLoop::process_expensive_nodes() {
  assert(OptimizeExpensiveOps, "optimization off?");

  // Sort nodes to bring similar nodes together
  C->sort_expensive_nodes();

  bool progress = false;

  for (int i = 0; i < C->expensive_count(); ) {
    Node* n = C->expensive_node(i);
    int start = i;
    // Find nodes similar to n
    i++;
    for (; i < C->expensive_count() && Compile::cmp_expensive_nodes(n, C->expensive_node(i)) == 0; i++);
    int end = i;
    // And compare them two by two
    for (int j = start; j < end; j++) {
      Node* n1 = C->expensive_node(j);
      if (is_node_unreachable(n1)) {
        continue;
      }
      for (int k = j+1; k < end; k++) {
        Node* n2 = C->expensive_node(k);
        if (is_node_unreachable(n2)) {
          continue;
        }

        assert(n1 != n2, "should be pair of nodes");

        Node* c1 = n1->in(0);
        Node* c2 = n2->in(0);

        Node* parent_c1 = c1;
        Node* parent_c2 = c2;

        // The call to get_early_ctrl_for_expensive() moves the
        // expensive nodes up but stops at loops that are in a if
        // branch. See whether we can exit the loop and move above the
        // If.
        if (c1->is_Loop()) {
          parent_c1 = c1->in(1);
        }
        if (c2->is_Loop()) {
          parent_c2 = c2->in(1);
        }

        if (parent_c1 == parent_c2) {
          _igvn._worklist.push(n1);
          _igvn._worklist.push(n2);
          continue;
        }

        // Look for identical expensive node up the dominator chain.
        if (is_dominator(c1, c2)) {
          c2 = c1;
        } else if (is_dominator(c2, c1)) {
          c1 = c2;
        } else if (parent_c1->is_Proj() && parent_c1->in(0)->is_If() &&
                   parent_c2->is_Proj() && parent_c1->in(0) == parent_c2->in(0)) {
          // Both branches have the same expensive node so move it up
          // before the if.
          c1 = c2 = idom(parent_c1->in(0));
        }
        // Do the actual moves
        if (n1->in(0) != c1) {
          _igvn.replace_input_of(n1, 0, c1);
          progress = true;
        }
        if (n2->in(0) != c2) {
          _igvn.replace_input_of(n2, 0, c2);
          progress = true;
        }
      }
    }
  }

  return progress;
}

#ifdef ASSERT
// Goes over all children of the root of the loop tree. Check if any of them have a path
// down to Root, that does not go via a NeverBranch exit.
bool PhaseIdealLoop::only_has_infinite_loops() {
  ResourceMark rm;
  Unique_Node_List worklist;
  // start traversal at all loop heads of first-level loops
  for (IdealLoopTree* l = _ltree_root->_child; l != nullptr; l = l->_next) {
    Node* head = l->_head;
    assert(head->is_Region(), "");
    worklist.push(head);
  }
  return RegionNode::are_all_nodes_in_infinite_subgraph(worklist);
}
#endif


//=============================================================================
//----------------------------build_and_optimize-------------------------------
// Create a PhaseLoop.  Build the ideal Loop tree.  Map each Ideal Node to
// its corresponding LoopNode.  If 'optimize' is true, do some loop cleanups.
void PhaseIdealLoop::build_and_optimize() {
  assert(!C->post_loop_opts_phase(), "no loop opts allowed");

  bool do_split_ifs = (_mode == LoopOptsDefault);
  bool skip_loop_opts = (_mode == LoopOptsNone);
  bool do_max_unroll = (_mode == LoopOptsMaxUnroll);


  int old_progress = C->major_progress();
  uint orig_worklist_size = _igvn._worklist.size();

  // Reset major-progress flag for the driver's heuristics
  C->clear_major_progress();

#ifndef PRODUCT
  // Capture for later assert
  uint unique = C->unique();
  _loop_invokes++;
  _loop_work += unique;
#endif

  // True if the method has at least 1 irreducible loop
  _has_irreducible_loops = false;

  _created_loop_node = false;

  VectorSet visited;
  // Pre-grow the mapping from Nodes to IdealLoopTrees.
  _loop_or_ctrl.map(C->unique(), nullptr);
  memset(_loop_or_ctrl.adr(), 0, wordSize * C->unique());

  // Pre-build the top-level outermost loop tree entry
  _ltree_root = new IdealLoopTree( this, C->root(), C->root() );
  // Do not need a safepoint at the top level
  _ltree_root->_has_sfpt = 1;

  // Initialize Dominators.
  // Checked in clone_loop_predicate() during beautify_loops().
  _idom_size = 0;
  _idom      = nullptr;
  _dom_depth = nullptr;
  _dom_stk   = nullptr;

  // Empty pre-order array
  allocate_preorders();

  // Build a loop tree on the fly.  Build a mapping from CFG nodes to
  // IdealLoopTree entries.  Data nodes are NOT walked.
  build_loop_tree();
  // Check for bailout, and return
  if (C->failing()) {
    return;
  }

  // Verify that the has_loops() flag set at parse time is consistent
  // with the just built loop tree. With infinite loops, it could be
  // that one pass of loop opts only finds infinite loops, clears the
  // has_loops() flag but adds NeverBranch nodes so the next loop opts
  // verification pass finds a non empty loop tree. When the back edge
  // is an exception edge, parsing doesn't set has_loops().
  assert(_ltree_root->_child == nullptr || C->has_loops() || only_has_infinite_loops() || C->has_exception_backedge(), "parsing found no loops but there are some");
  // No loops after all
  if( !_ltree_root->_child && !_verify_only ) C->set_has_loops(false);

  // There should always be an outer loop containing the Root and Return nodes.
  // If not, we have a degenerate empty program.  Bail out in this case.
  if (!has_node(C->root())) {
    if (!_verify_only) {
      C->clear_major_progress();
      assert(false, "empty program detected during loop optimization");
      C->record_method_not_compilable("empty program detected during loop optimization");
    }
    return;
  }

  BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
  // Nothing to do, so get out
  bool stop_early = !C->has_loops() && !skip_loop_opts && !do_split_ifs && !do_max_unroll && !_verify_me &&
          !_verify_only && !bs->is_gc_specific_loop_opts_pass(_mode);
  bool do_expensive_nodes = C->should_optimize_expensive_nodes(_igvn);
  bool strip_mined_loops_expanded = bs->strip_mined_loops_expanded(_mode);
  if (stop_early && !do_expensive_nodes) {
    return;
  }

  // Set loop nesting depth
  _ltree_root->set_nest( 0 );

  // Split shared headers and insert loop landing pads.
  // Do not bother doing this on the Root loop of course.
  if( !_verify_me && !_verify_only && _ltree_root->_child ) {
    C->print_method(PHASE_BEFORE_BEAUTIFY_LOOPS, 3);
    if( _ltree_root->_child->beautify_loops( this ) ) {
      // Re-build loop tree!
      _ltree_root->_child = nullptr;
      _loop_or_ctrl.clear();
      reallocate_preorders();
      build_loop_tree();
      // Check for bailout, and return
      if (C->failing()) {
        return;
      }
      // Reset loop nesting depth
      _ltree_root->set_nest( 0 );

      C->print_method(PHASE_AFTER_BEAUTIFY_LOOPS, 3);
    }
  }

  // Build Dominators for elision of null checks & loop finding.
  // Since nodes do not have a slot for immediate dominator, make
  // a persistent side array for that info indexed on node->_idx.
  _idom_size = C->unique();
  _idom      = NEW_RESOURCE_ARRAY( Node*, _idom_size );
  _dom_depth = NEW_RESOURCE_ARRAY( uint,  _idom_size );
  _dom_stk   = nullptr; // Allocated on demand in recompute_dom_depth
  memset( _dom_depth, 0, _idom_size * sizeof(uint) );

  Dominators();

  if (!_verify_only) {
    // As a side effect, Dominators removed any unreachable CFG paths
    // into RegionNodes.  It doesn't do this test against Root, so
    // we do it here.
    for( uint i = 1; i < C->root()->req(); i++ ) {
      if (!_loop_or_ctrl[C->root()->in(i)->_idx]) { // Dead path into Root?
        _igvn.delete_input_of(C->root(), i);
        i--;                      // Rerun same iteration on compressed edges
      }
    }

    // Given dominators, try to find inner loops with calls that must
    // always be executed (call dominates loop tail).  These loops do
    // not need a separate safepoint.
    Node_List cisstack;
    _ltree_root->check_safepts(visited, cisstack);
  }

  // Walk the DATA nodes and place into loops.  Find earliest control
  // node.  For CFG nodes, the _loop_or_ctrl array starts out and remains
  // holding the associated IdealLoopTree pointer.  For DATA nodes, the
  // _loop_or_ctrl array holds the earliest legal controlling CFG node.

  // Allocate stack with enough space to avoid frequent realloc
  int stack_size = (C->live_nodes() >> 1) + 16; // (live_nodes>>1)+16 from Java2D stats
  Node_Stack nstack(stack_size);

  visited.clear();
  Node_List worklist;
  // Don't need C->root() on worklist since
  // it will be processed among C->top() inputs
  worklist.push(C->top());
  visited.set(C->top()->_idx); // Set C->top() as visited now
  build_loop_early( visited, worklist, nstack );

  // Given early legal placement, try finding counted loops.  This placement
  // is good enough to discover most loop invariants.
  if (!_verify_me && !_verify_only && !strip_mined_loops_expanded) {
    _ltree_root->counted_loop( this );
  }

  // Find latest loop placement.  Find ideal loop placement.
  visited.clear();
  init_dom_lca_tags();
  // Need C->root() on worklist when processing outs
  worklist.push(C->root());
  NOT_PRODUCT( C->verify_graph_edges(); )
  worklist.push(C->top());
  build_loop_late( visited, worklist, nstack );
  if (C->failing()) { return; }

  if (_verify_only) {
    C->restore_major_progress(old_progress);
    assert(C->unique() == unique, "verification _mode made Nodes? ? ?");
    assert(_igvn._worklist.size() == orig_worklist_size, "shouldn't push anything");
    return;
  }

  // clear out the dead code after build_loop_late
  while (_deadlist.size()) {
    _igvn.remove_globally_dead_node(_deadlist.pop());
  }

  eliminate_useless_zero_trip_guard();

  if (stop_early) {
    assert(do_expensive_nodes, "why are we here?");
    if (process_expensive_nodes()) {
      // If we made some progress when processing expensive nodes then
      // the IGVN may modify the graph in a way that will allow us to
      // make some more progress: we need to try processing expensive
      // nodes again.
      C->set_major_progress();
    }
    return;
  }

  // Some parser-inserted loop predicates could never be used by loop
  // predication or they were moved away from loop during some optimizations.
  // For example, peeling. Eliminate them before next loop optimizations.
  eliminate_useless_predicates();

#ifndef PRODUCT
  C->verify_graph_edges();
  if (_verify_me) {             // Nested verify pass?
    // Check to see if the verify _mode is broken
    assert(C->unique() == unique, "non-optimize _mode made Nodes? ? ?");
    return;
  }
  DEBUG_ONLY( if (VerifyLoopOptimizations) { verify(); } );
  if (TraceLoopOpts && C->has_loops()) {
    _ltree_root->dump();
  }
#endif

  if (skip_loop_opts) {
    C->restore_major_progress(old_progress);
    return;
  }

  if (do_max_unroll) {
    for (LoopTreeIterator iter(_ltree_root); !iter.done(); iter.next()) {
      IdealLoopTree* lpt = iter.current();
      if (lpt->is_innermost() && lpt->_allow_optimizations && !lpt->_has_call && lpt->is_counted()) {
        lpt->compute_trip_count(this);
        if (!lpt->do_one_iteration_loop(this) &&
            !lpt->do_remove_empty_loop(this)) {
          AutoNodeBudget node_budget(this);
          if (lpt->_head->as_CountedLoop()->is_normal_loop() &&
              lpt->policy_maximally_unroll(this)) {
            memset( worklist.adr(), 0, worklist.max()*sizeof(Node*) );
            do_maximally_unroll(lpt, worklist);
          }
        }
      }
    }

    C->restore_major_progress(old_progress);
    return;
  }

  if (bs->optimize_loops(this, _mode, visited, nstack, worklist)) {
    return;
  }

  if (ReassociateInvariants && !C->major_progress()) {
    // Reassociate invariants and prep for split_thru_phi
    for (LoopTreeIterator iter(_ltree_root); !iter.done(); iter.next()) {
      IdealLoopTree* lpt = iter.current();
      if (!lpt->is_loop()) {
        continue;
      }
      Node* head = lpt->_head;
      if (!head->is_BaseCountedLoop() || !lpt->is_innermost()) continue;

      // check for vectorized loops, any reassociation of invariants was already done
      if (head->is_CountedLoop()) {
        if (head->as_CountedLoop()->is_unroll_only()) {
          continue;
        } else {
          AutoNodeBudget node_budget(this);
          lpt->reassociate_invariants(this);
        }
      }
      // Because RCE opportunities can be masked by split_thru_phi,
      // look for RCE candidates and inhibit split_thru_phi
      // on just their loop-phi's for this pass of loop opts
      if (SplitIfBlocks && do_split_ifs &&
          head->as_BaseCountedLoop()->is_valid_counted_loop(head->as_BaseCountedLoop()->bt()) &&
          (lpt->policy_range_check(this, true, T_LONG) ||
           (head->is_CountedLoop() && lpt->policy_range_check(this, true, T_INT)))) {
        lpt->_rce_candidate = 1; // = true
      }
    }
  }

  // Check for aggressive application of split-if and other transforms
  // that require basic-block info (like cloning through Phi's)
  if (!C->major_progress() && SplitIfBlocks && do_split_ifs) {
    visited.clear();
    split_if_with_blocks( visited, nstack);
    DEBUG_ONLY( if (VerifyLoopOptimizations) { verify(); } );
  }

  if (!C->major_progress() && do_expensive_nodes && process_expensive_nodes()) {
    C->set_major_progress();
  }

  // Perform loop predication before iteration splitting
  if (UseLoopPredicate && C->has_loops() && !C->major_progress() && (C->parse_predicate_count() > 0)) {
    _ltree_root->_child->loop_predication(this);
  }

  if (OptimizeFill && UseLoopPredicate && C->has_loops() && !C->major_progress()) {
    if (do_intrinsify_fill()) {
      C->set_major_progress();
    }
  }

  // Perform iteration-splitting on inner loops.  Split iterations to avoid
  // range checks or one-shot null checks.

  // If split-if's didn't hack the graph too bad (no CFG changes)
  // then do loop opts.
  if (C->has_loops() && !C->major_progress()) {
    memset( worklist.adr(), 0, worklist.max()*sizeof(Node*) );
    _ltree_root->_child->iteration_split( this, worklist );
    // No verify after peeling!  GCM has hoisted code out of the loop.
    // After peeling, the hoisted code could sink inside the peeled area.
    // The peeling code does not try to recompute the best location for
    // all the code before the peeled area, so the verify pass will always
    // complain about it.
  }

  // Check for bailout, and return
  if (C->failing()) {
    return;
  }

  // Do verify graph edges in any case
  NOT_PRODUCT( C->verify_graph_edges(); );

  if (!do_split_ifs) {
    // We saw major progress in Split-If to get here.  We forced a
    // pass with unrolling and not split-if, however more split-if's
    // might make progress.  If the unrolling didn't make progress
    // then the major-progress flag got cleared and we won't try
    // another round of Split-If.  In particular the ever-common
    // instance-of/check-cast pattern requires at least 2 rounds of
    // Split-If to clear out.
    C->set_major_progress();
  }

  // Repeat loop optimizations if new loops were seen
  if (created_loop_node()) {
    C->set_major_progress();
  }

  // Keep loop predicates and perform optimizations with them
  // until no more loop optimizations could be done.
  // After that switch predicates off and do more loop optimizations.
  if (!C->major_progress() && (C->parse_predicate_count() > 0)) {
    C->mark_parse_predicate_nodes_useless(_igvn);
    assert(C->parse_predicate_count() == 0, "should be zero now");
     if (TraceLoopOpts) {
       tty->print_cr("PredicatesOff");
     }
     C->set_major_progress();
  }

  // Auto-vectorize main-loop
  if (C->do_superword() && C->has_loops() && !C->major_progress()) {
    Compile::TracePhase tp("autoVectorize", &timers[_t_autoVectorize]);

    // Shared data structures for all AutoVectorizations, to reduce allocations
    // of large arrays.
    VSharedData vshared;
    for (LoopTreeIterator iter(_ltree_root); !iter.done(); iter.next()) {
      IdealLoopTree* lpt = iter.current();
      AutoVectorizeStatus status = auto_vectorize(lpt, vshared);

      if (status == AutoVectorizeStatus::TriedAndFailed) {
        // We tried vectorization, but failed. From now on only unroll the loop.
        CountedLoopNode* cl = lpt->_head->as_CountedLoop();
        if (cl->has_passed_slp()) {
          C->set_major_progress();
          cl->set_notpassed_slp();
          cl->mark_do_unroll_only();
        }
      }
    }
  }

  // Move UnorderedReduction out of counted loop. Can be introduced by AutoVectorization.
  if (C->has_loops() && !C->major_progress()) {
    for (LoopTreeIterator iter(_ltree_root); !iter.done(); iter.next()) {
      IdealLoopTree* lpt = iter.current();
      if (lpt->is_counted() && lpt->is_innermost()) {
        move_unordered_reduction_out_of_loop(lpt);
      }
    }
  }
}

#ifndef PRODUCT
//------------------------------print_statistics-------------------------------
int PhaseIdealLoop::_loop_invokes=0;// Count of PhaseIdealLoop invokes
int PhaseIdealLoop::_loop_work=0; // Sum of PhaseIdealLoop x unique
volatile int PhaseIdealLoop::_long_loop_candidates=0; // Number of long loops seen
volatile int PhaseIdealLoop::_long_loop_nests=0; // Number of long loops successfully transformed to a nest
volatile int PhaseIdealLoop::_long_loop_counted_loops=0; // Number of long loops successfully transformed to a counted loop
void PhaseIdealLoop::print_statistics() {
  tty->print_cr("PhaseIdealLoop=%d, sum _unique=%d, long loops=%d/%d/%d", _loop_invokes, _loop_work, _long_loop_counted_loops, _long_loop_nests, _long_loop_candidates);
}
#endif

#ifdef ASSERT
// Build a verify-only PhaseIdealLoop, and see that it agrees with "this".
void PhaseIdealLoop::verify() const {
  ResourceMark rm;
  int old_progress = C->major_progress();
  bool success = true;

  PhaseIdealLoop phase_verify(_igvn, this);
  if (C->failing()) return;

  // Verify ctrl and idom of every node.
  success &= verify_idom_and_nodes(C->root(), &phase_verify);

  // Verify loop-tree.
  success &= _ltree_root->verify_tree(phase_verify._ltree_root);

  assert(success, "VerifyLoopOptimizations failed");

  // Major progress was cleared by creating a verify version of PhaseIdealLoop.
  C->restore_major_progress(old_progress);
}

// Perform a BFS starting at n, through all inputs.
// Call verify_idom and verify_node on all nodes of BFS traversal.
bool PhaseIdealLoop::verify_idom_and_nodes(Node* root, const PhaseIdealLoop* phase_verify) const {
  Unique_Node_List worklist;
  worklist.push(root);
  bool success = true;
  for (uint i = 0; i < worklist.size(); i++) {
    Node* n = worklist.at(i);
    // process node
    success &= verify_idom(n, phase_verify);
    success &= verify_loop_ctrl(n, phase_verify);
    // visit inputs
    for (uint j = 0; j < n->req(); j++) {
      if (n->in(j) != nullptr) {
        worklist.push(n->in(j));
      }
    }
  }
  return success;
}

// Verify dominator structure (IDOM).
bool PhaseIdealLoop::verify_idom(Node* n, const PhaseIdealLoop* phase_verify) const {
  // Verify IDOM for all CFG nodes (except root).
  if (!n->is_CFG() || n->is_Root()) {
    return true; // pass
  }

  if (n->_idx >= _idom_size) {
    tty->print("CFG Node with no idom: ");
    n->dump();
    return false; // fail
  }

  Node* id = idom_no_update(n);
  Node* id_verify = phase_verify->idom_no_update(n);
  if (id != id_verify) {
    tty->print("Mismatching idom for node: ");
    n->dump();
    tty->print("  We have idom: ");
    id->dump();
    tty->print("  Verify has idom: ");
    id_verify->dump();
    tty->cr();
    return false; // fail
  }
  return true; // pass
}

// Verify "_loop_or_ctrl": control and loop membership.
//  (0) _loop_or_ctrl[i] == nullptr -> node not reachable.
//  (1) has_ctrl -> check lowest bit. 1 -> data node. 0 -> ctrl node.
//  (2) has_ctrl true: get_ctrl_no_update returns ctrl of data node.
//  (3) has_ctrl false: get_loop_idx returns IdealLoopTree for ctrl node.
bool PhaseIdealLoop::verify_loop_ctrl(Node* n, const PhaseIdealLoop* phase_verify) const {
  const uint i = n->_idx;
  // The loop-tree was built from def to use (top-down).
  // The verification happens from use to def (bottom-up).
  // We may thus find nodes during verification that are not in the loop-tree.
  if (_loop_or_ctrl[i] == nullptr || phase_verify->_loop_or_ctrl[i] == nullptr) {
    if (_loop_or_ctrl[i] != nullptr || phase_verify->_loop_or_ctrl[i] != nullptr) {
      tty->print_cr("Was reachable in only one. this %d, verify %d.",
                 _loop_or_ctrl[i] != nullptr, phase_verify->_loop_or_ctrl[i] != nullptr);
      n->dump();
      return false; // fail
    }
    // Not reachable for both.
    return true; // pass
  }

  if (n->is_CFG() == has_ctrl(n)) {
    tty->print_cr("Exactly one should be true: %d for is_CFG, %d for has_ctrl.", n->is_CFG(), has_ctrl(n));
    n->dump();
    return false; // fail
  }

  if (has_ctrl(n) != phase_verify->has_ctrl(n)) {
    tty->print_cr("Mismatch has_ctrl: %d for this, %d for verify.", has_ctrl(n), phase_verify->has_ctrl(n));
    n->dump();
    return false; // fail
  } else if (has_ctrl(n)) {
    assert(phase_verify->has_ctrl(n), "sanity");
    // n is a data node.
    // Verify that its ctrl is the same.

    // Broken part of VerifyLoopOptimizations (A)
    // Reason:
    //   BUG, wrong control set for example in
    //   PhaseIdealLoop::split_if_with_blocks
    //   at "set_ctrl(x, new_ctrl);"
    /*
    if( _loop_or_ctrl[i] != loop_verify->_loop_or_ctrl[i] &&
        get_ctrl_no_update(n) != loop_verify->get_ctrl_no_update(n) ) {
      tty->print("Mismatched control setting for: ");
      n->dump();
      if( fail++ > 10 ) return;
      Node *c = get_ctrl_no_update(n);
      tty->print("We have it as: ");
      if( c->in(0) ) c->dump();
        else tty->print_cr("N%d",c->_idx);
      tty->print("Verify thinks: ");
      if( loop_verify->has_ctrl(n) )
        loop_verify->get_ctrl_no_update(n)->dump();
      else
        loop_verify->get_loop_idx(n)->dump();
      tty->cr();
    }
    */
    return true; // pass
  } else {
    assert(!phase_verify->has_ctrl(n), "sanity");
    // n is a ctrl node.
    // Verify that not has_ctrl, and that get_loop_idx is the same.

    // Broken part of VerifyLoopOptimizations (B)
    // Reason:
    //   NeverBranch node for example is added to loop outside its scope.
    //   Once we run build_loop_tree again, it is added to the correct loop.
    /*
    if (!C->major_progress()) {
      // Loop selection can be messed up if we did a major progress
      // operation, like split-if.  Do not verify in that case.
      IdealLoopTree *us = get_loop_idx(n);
      IdealLoopTree *them = loop_verify->get_loop_idx(n);
      if( us->_head != them->_head ||  us->_tail != them->_tail ) {
        tty->print("Unequals loops for: ");
        n->dump();
        if( fail++ > 10 ) return;
        tty->print("We have it as: ");
        us->dump();
        tty->print("Verify thinks: ");
        them->dump();
        tty->cr();
      }
    }
    */
    return true; // pass
  }
}

static int compare_tree(IdealLoopTree* const& a, IdealLoopTree* const& b) {
  assert(a != nullptr && b != nullptr, "must be");
  return a->_head->_idx - b->_head->_idx;
}

GrowableArray<IdealLoopTree*> IdealLoopTree::collect_sorted_children() const {
  GrowableArray<IdealLoopTree*> children;
  IdealLoopTree* child = _child;
  while (child != nullptr) {
    assert(child->_parent == this, "all must be children of this");
    children.insert_sorted<compare_tree>(child);
    child = child->_next;
  }
  return children;
}

// Verify that tree structures match. Because the CFG can change, siblings
// within the loop tree can be reordered. We attempt to deal with that by
// reordering the verify's loop tree if possible.
bool IdealLoopTree::verify_tree(IdealLoopTree* loop_verify) const {
  assert(_head == loop_verify->_head, "mismatched loop head");
  assert(this->_parent != nullptr || this->_next == nullptr, "is_root_loop implies has_no_sibling");

  // Collect the children
  GrowableArray<IdealLoopTree*> children = collect_sorted_children();
  GrowableArray<IdealLoopTree*> children_verify = loop_verify->collect_sorted_children();

  bool success = true;

  // Compare the two children lists
  for (int i = 0, j = 0; i < children.length() || j < children_verify.length(); ) {
    IdealLoopTree* child        = nullptr;
    IdealLoopTree* child_verify = nullptr;
    // Read from both lists, if possible.
    if (i < children.length()) {
      child = children.at(i);
    }
    if (j < children_verify.length()) {
      child_verify = children_verify.at(j);
    }
    assert(child != nullptr || child_verify != nullptr, "must find at least one");
    if (child != nullptr && child_verify != nullptr && child->_head != child_verify->_head) {
      // We found two non-equal children. Select the smaller one.
      if (child->_head->_idx < child_verify->_head->_idx) {
        child_verify = nullptr;
      } else {
        child = nullptr;
      }
    }
    // Process the two children, or potentially log the failure if we only found one.
    if (child_verify == nullptr) {
      if (child->_irreducible && Compile::current()->major_progress()) {
        // Irreducible loops can pick a different header (one of its entries).
      } else {
        tty->print_cr("We have a loop that verify does not have");
        child->dump();
        success = false;
      }
      i++; // step for this
    } else if (child == nullptr) {
      if (child_verify->_irreducible && Compile::current()->major_progress()) {
        // Irreducible loops can pick a different header (one of its entries).
      } else if (child_verify->_head->as_Region()->is_in_infinite_subgraph()) {
        // Infinite loops do not get attached to the loop-tree on their first visit.
        // "this" runs before "loop_verify". It is thus possible that we find the
        // infinite loop only for "child_verify". Only finding it with "child" would
        // mean that we lost it, which is not ok.
      } else {
        tty->print_cr("Verify has a loop that we do not have");
        child_verify->dump();
        success = false;
      }
      j++; // step for verify
    } else {
      assert(child->_head == child_verify->_head, "We have both and they are equal");
      success &= child->verify_tree(child_verify); // Recursion
      i++; // step for this
      j++; // step for verify
    }
  }

  // Broken part of VerifyLoopOptimizations (D)
  // Reason:
  //   split_if has to update the _tail, if it is modified. But that is done by
  //   checking to what loop the iff belongs to. That info can be wrong, and then
  //   we do not update the _tail correctly.
  /*
  Node *tail = _tail;           // Inline a non-updating version of
  while( !tail->in(0) )         // the 'tail()' call.
    tail = tail->in(1);
  assert( tail == loop->_tail, "mismatched loop tail" );
  */

  if (_head->is_CountedLoop()) {
    CountedLoopNode *cl = _head->as_CountedLoop();

    Node* ctrl     = cl->init_control();
    Node* back     = cl->back_control();
    assert(ctrl != nullptr && ctrl->is_CFG(), "sane loop in-ctrl");
    assert(back != nullptr && back->is_CFG(), "sane loop backedge");
    cl->loopexit(); // assert implied
  }

  // Broken part of VerifyLoopOptimizations (E)
  // Reason:
  //   PhaseIdealLoop::split_thru_region creates new nodes for loop that are not added
  //   to the loop body. Or maybe they are not added to the correct loop.
  //   at "Node* x = n->clone();"
  /*
  // Innermost loops need to verify loop bodies,
  // but only if no 'major_progress'
  int fail = 0;
  if (!Compile::current()->major_progress() && _child == nullptr) {
    for( uint i = 0; i < _body.size(); i++ ) {
      Node *n = _body.at(i);
      if (n->outcnt() == 0)  continue; // Ignore dead
      uint j;
      for( j = 0; j < loop->_body.size(); j++ )
        if( loop->_body.at(j) == n )
          break;
      if( j == loop->_body.size() ) { // Not found in loop body
        // Last ditch effort to avoid assertion: Its possible that we
        // have some users (so outcnt not zero) but are still dead.
        // Try to find from root.
        if (Compile::current()->root()->find(n->_idx)) {
          fail++;
          tty->print("We have that verify does not: ");
          n->dump();
        }
      }
    }
    for( uint i2 = 0; i2 < loop->_body.size(); i2++ ) {
      Node *n = loop->_body.at(i2);
      if (n->outcnt() == 0)  continue; // Ignore dead
      uint j;
      for( j = 0; j < _body.size(); j++ )
        if( _body.at(j) == n )
          break;
      if( j == _body.size() ) { // Not found in loop body
        // Last ditch effort to avoid assertion: Its possible that we
        // have some users (so outcnt not zero) but are still dead.
        // Try to find from root.
        if (Compile::current()->root()->find(n->_idx)) {
          fail++;
          tty->print("Verify has that we do not: ");
          n->dump();
        }
      }
    }
    assert( !fail, "loop body mismatch" );
  }
  */
  return success;
}
#endif

//------------------------------set_idom---------------------------------------
void PhaseIdealLoop::set_idom(Node* d, Node* n, uint dom_depth) {
  _nesting.check(); // Check if a potential reallocation in the resource arena is safe
  uint idx = d->_idx;
  if (idx >= _idom_size) {
    uint newsize = next_power_of_2(idx);
    _idom      = REALLOC_RESOURCE_ARRAY( Node*,     _idom,_idom_size,newsize);
    _dom_depth = REALLOC_RESOURCE_ARRAY( uint, _dom_depth,_idom_size,newsize);
    memset( _dom_depth + _idom_size, 0, (newsize - _idom_size) * sizeof(uint) );
    _idom_size = newsize;
  }
  _idom[idx] = n;
  _dom_depth[idx] = dom_depth;
}

//------------------------------recompute_dom_depth---------------------------------------
// The dominator tree is constructed with only parent pointers.
// This recomputes the depth in the tree by first tagging all
// nodes as "no depth yet" marker.  The next pass then runs up
// the dom tree from each node marked "no depth yet", and computes
// the depth on the way back down.
void PhaseIdealLoop::recompute_dom_depth() {
  uint no_depth_marker = C->unique();
  uint i;
  // Initialize depth to "no depth yet" and realize all lazy updates
  for (i = 0; i < _idom_size; i++) {
    // Only indices with a _dom_depth has a Node* or null (otherwise uninitialized).
    if (_dom_depth[i] > 0 && _idom[i] != nullptr) {
      _dom_depth[i] = no_depth_marker;

      // heal _idom if it has a fwd mapping in _loop_or_ctrl
      if (_idom[i]->in(0) == nullptr) {
        idom(i);
      }
    }
  }
  if (_dom_stk == nullptr) {
    uint init_size = C->live_nodes() / 100; // Guess that 1/100 is a reasonable initial size.
    if (init_size < 10) init_size = 10;
    _dom_stk = new GrowableArray<uint>(init_size);
  }
  // Compute new depth for each node.
  for (i = 0; i < _idom_size; i++) {
    uint j = i;
    // Run up the dom tree to find a node with a depth
    while (_dom_depth[j] == no_depth_marker) {
      _dom_stk->push(j);
      j = _idom[j]->_idx;
    }
    // Compute the depth on the way back down this tree branch
    uint dd = _dom_depth[j] + 1;
    while (_dom_stk->length() > 0) {
      uint j = _dom_stk->pop();
      _dom_depth[j] = dd;
      dd++;
    }
  }
}

//------------------------------sort-------------------------------------------
// Insert 'loop' into the existing loop tree.  'innermost' is a leaf of the
// loop tree, not the root.
IdealLoopTree *PhaseIdealLoop::sort( IdealLoopTree *loop, IdealLoopTree *innermost ) {
  if( !innermost ) return loop; // New innermost loop

  int loop_preorder = get_preorder(loop->_head); // Cache pre-order number
  assert( loop_preorder, "not yet post-walked loop" );
  IdealLoopTree **pp = &innermost;      // Pointer to previous next-pointer
  IdealLoopTree *l = *pp;               // Do I go before or after 'l'?

  // Insert at start of list
  while( l ) {                  // Insertion sort based on pre-order
    if( l == loop ) return innermost; // Already on list!
    int l_preorder = get_preorder(l->_head); // Cache pre-order number
    assert( l_preorder, "not yet post-walked l" );
    // Check header pre-order number to figure proper nesting
    if( loop_preorder > l_preorder )
      break;                    // End of insertion
    // If headers tie (e.g., shared headers) check tail pre-order numbers.
    // Since I split shared headers, you'd think this could not happen.
    // BUT: I must first do the preorder numbering before I can discover I
    // have shared headers, so the split headers all get the same preorder
    // number as the RegionNode they split from.
    if( loop_preorder == l_preorder &&
        get_preorder(loop->_tail) < get_preorder(l->_tail) )
      break;                    // Also check for shared headers (same pre#)
    pp = &l->_parent;           // Chain up list
    l = *pp;
  }
  // Link into list
  // Point predecessor to me
  *pp = loop;
  // Point me to successor
  IdealLoopTree *p = loop->_parent;
  loop->_parent = l;            // Point me to successor
  if( p ) sort( p, innermost ); // Insert my parents into list as well
  return innermost;
}

//------------------------------build_loop_tree--------------------------------
// I use a modified Vick/Tarjan algorithm.  I need pre- and a post- visit
// bits.  The _loop_or_ctrl[] array is mapped by Node index and holds a null for
// not-yet-pre-walked, pre-order # for pre-but-not-post-walked and holds the
// tightest enclosing IdealLoopTree for post-walked.
//
// During my forward walk I do a short 1-layer lookahead to see if I can find
// a loop backedge with that doesn't have any work on the backedge.  This
// helps me construct nested loops with shared headers better.
//
// Once I've done the forward recursion, I do the post-work.  For each child
// I check to see if there is a backedge.  Backedges define a loop!  I
// insert an IdealLoopTree at the target of the backedge.
//
// During the post-work I also check to see if I have several children
// belonging to different loops.  If so, then this Node is a decision point
// where control flow can choose to change loop nests.  It is at this
// decision point where I can figure out how loops are nested.  At this
// time I can properly order the different loop nests from my children.
// Note that there may not be any backedges at the decision point!
//
// Since the decision point can be far removed from the backedges, I can't
// order my loops at the time I discover them.  Thus at the decision point
// I need to inspect loop header pre-order numbers to properly nest my
// loops.  This means I need to sort my childrens' loops by pre-order.
// The sort is of size number-of-control-children, which generally limits
// it to size 2 (i.e., I just choose between my 2 target loops).
void PhaseIdealLoop::build_loop_tree() {
  // Allocate stack of size C->live_nodes()/2 to avoid frequent realloc
  GrowableArray <Node *> bltstack(C->live_nodes() >> 1);
  Node *n = C->root();
  bltstack.push(n);
  int pre_order = 1;
  int stack_size;

  while ( ( stack_size = bltstack.length() ) != 0 ) {
    n = bltstack.top(); // Leave node on stack
    if ( !is_visited(n) ) {
      // ---- Pre-pass Work ----
      // Pre-walked but not post-walked nodes need a pre_order number.

      set_preorder_visited( n, pre_order ); // set as visited

      // ---- Scan over children ----
      // Scan first over control projections that lead to loop headers.
      // This helps us find inner-to-outer loops with shared headers better.

      // Scan children's children for loop headers.
      for ( int i = n->outcnt() - 1; i >= 0; --i ) {
        Node* m = n->raw_out(i);       // Child
        if( m->is_CFG() && !is_visited(m) ) { // Only for CFG children
          // Scan over children's children to find loop
          for (DUIterator_Fast jmax, j = m->fast_outs(jmax); j < jmax; j++) {
            Node* l = m->fast_out(j);
            if( is_visited(l) &&       // Been visited?
                !is_postvisited(l) &&  // But not post-visited
                get_preorder(l) < pre_order ) { // And smaller pre-order
              // Found!  Scan the DFS down this path before doing other paths
              bltstack.push(m);
              break;
            }
          }
        }
      }
      pre_order++;
    }
    else if ( !is_postvisited(n) ) {
      // Note: build_loop_tree_impl() adds out edges on rare occasions,
      // such as com.sun.rsasign.am::a.
      // For non-recursive version, first, process current children.
      // On next iteration, check if additional children were added.
      for ( int k = n->outcnt() - 1; k >= 0; --k ) {
        Node* u = n->raw_out(k);
        if ( u->is_CFG() && !is_visited(u) ) {
          bltstack.push(u);
        }
      }
      if ( bltstack.length() == stack_size ) {
        // There were no additional children, post visit node now
        (void)bltstack.pop(); // Remove node from stack
        pre_order = build_loop_tree_impl( n, pre_order );
        // Check for bailout
        if (C->failing()) {
          return;
        }
        // Check to grow _preorders[] array for the case when
        // build_loop_tree_impl() adds new nodes.
        check_grow_preorders();
      }
    }
    else {
      (void)bltstack.pop(); // Remove post-visited node from stack
    }
  }
  DEBUG_ONLY(verify_regions_in_irreducible_loops();)
}

//------------------------------build_loop_tree_impl---------------------------
int PhaseIdealLoop::build_loop_tree_impl( Node *n, int pre_order ) {
  // ---- Post-pass Work ----
  // Pre-walked but not post-walked nodes need a pre_order number.

  // Tightest enclosing loop for this Node
  IdealLoopTree *innermost = nullptr;

  // For all children, see if any edge is a backedge.  If so, make a loop
  // for it.  Then find the tightest enclosing loop for the self Node.
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node* m = n->fast_out(i);   // Child
    if( n == m ) continue;      // Ignore control self-cycles
    if( !m->is_CFG() ) continue;// Ignore non-CFG edges

    IdealLoopTree *l;           // Child's loop
    if( !is_postvisited(m) ) {  // Child visited but not post-visited?
      // Found a backedge
      assert( get_preorder(m) < pre_order, "should be backedge" );
      // Check for the RootNode, which is already a LoopNode and is allowed
      // to have multiple "backedges".
      if( m == C->root()) {     // Found the root?
        l = _ltree_root;        // Root is the outermost LoopNode
      } else {                  // Else found a nested loop
        // Insert a LoopNode to mark this loop.
        l = new IdealLoopTree(this, m, n);
      } // End of Else found a nested loop
      if( !has_loop(m) )        // If 'm' does not already have a loop set
        set_loop(m, l);         // Set loop header to loop now

    } else {                    // Else not a nested loop
      if (!_loop_or_ctrl[m->_idx]) continue; // Dead code has no loop
      IdealLoopTree* m_loop = get_loop(m);
      l = m_loop;          // Get previously determined loop
      // If successor is header of a loop (nest), move up-loop till it
      // is a member of some outer enclosing loop.  Since there are no
      // shared headers (I've split them already) I only need to go up
      // at most 1 level.
      while( l && l->_head == m ) // Successor heads loop?
        l = l->_parent;         // Move up 1 for me
      // If this loop is not properly parented, then this loop
      // has no exit path out, i.e. its an infinite loop.
      if( !l ) {
        // Make loop "reachable" from root so the CFG is reachable.  Basically
        // insert a bogus loop exit that is never taken.  'm', the loop head,
        // points to 'n', one (of possibly many) fall-in paths.  There may be
        // many backedges as well.

        // Here I set the loop to be the root loop.  I could have, after
        // inserting a bogus loop exit, restarted the recursion and found my
        // new loop exit.  This would make the infinite loop a first-class
        // loop and it would then get properly optimized.  What's the use of
        // optimizing an infinite loop?
        l = _ltree_root;        // Oops, found infinite loop

        if (!_verify_only) {
          // Insert the NeverBranch between 'm' and it's control user.
          NeverBranchNode *iff = new NeverBranchNode( m );
          _igvn.register_new_node_with_optimizer(iff);
          set_loop(iff, m_loop);
          Node *if_t = new CProjNode( iff, 0 );
          _igvn.register_new_node_with_optimizer(if_t);
          set_loop(if_t, m_loop);

          Node* cfg = nullptr;       // Find the One True Control User of m
          for (DUIterator_Fast jmax, j = m->fast_outs(jmax); j < jmax; j++) {
            Node* x = m->fast_out(j);
            if (x->is_CFG() && x != m && x != iff)
              { cfg = x; break; }
          }
          assert(cfg != nullptr, "must find the control user of m");
          uint k = 0;             // Probably cfg->in(0)
          while( cfg->in(k) != m ) k++; // But check in case cfg is a Region
          _igvn.replace_input_of(cfg, k, if_t); // Now point to NeverBranch

          // Now create the never-taken loop exit
          Node *if_f = new CProjNode( iff, 1 );
          _igvn.register_new_node_with_optimizer(if_f);
          set_loop(if_f, l);
          // Find frame ptr for Halt.  Relies on the optimizer
          // V-N'ing.  Easier and quicker than searching through
          // the program structure.
          Node *frame = new ParmNode( C->start(), TypeFunc::FramePtr );
          _igvn.register_new_node_with_optimizer(frame);
          // Halt & Catch Fire
          Node* halt = new HaltNode(if_f, frame, "never-taken loop exit reached");
          _igvn.register_new_node_with_optimizer(halt);
          set_loop(halt, l);
          _igvn.add_input_to(C->root(), halt);
        }
        set_loop(C->root(), _ltree_root);
      }
    }
    if (is_postvisited(l->_head)) {
      // We are currently visiting l, but its head has already been post-visited.
      // l is irreducible: we just found a second entry m.
      _has_irreducible_loops = true;
      RegionNode* secondary_entry = m->as_Region();
      DEBUG_ONLY(secondary_entry->verify_can_be_irreducible_entry();)

      // Walk up the loop-tree, mark all loops that are already post-visited as irreducible
      // Since m is a secondary entry to them all.
      while( is_postvisited(l->_head) ) {
        l->_irreducible = 1; // = true
        RegionNode* head = l->_head->as_Region();
        DEBUG_ONLY(head->verify_can_be_irreducible_entry();)
        l = l->_parent;
        // Check for bad CFG here to prevent crash, and bailout of compile
        if (l == nullptr) {
#ifndef PRODUCT
          if (TraceLoopOpts) {
            tty->print_cr("bailout: unhandled CFG: infinite irreducible loop");
            m->dump();
          }
#endif
          // This is a rare case that we do not want to handle in C2.
          C->record_method_not_compilable("unhandled CFG detected during loop optimization");
          return pre_order;
        }
      }
    }
    if (!_verify_only) {
      C->set_has_irreducible_loop(_has_irreducible_loops);
    }

    // This Node might be a decision point for loops.  It is only if
    // it's children belong to several different loops.  The sort call
    // does a trivial amount of work if there is only 1 child or all
    // children belong to the same loop.  If however, the children
    // belong to different loops, the sort call will properly set the
    // _parent pointers to show how the loops nest.
    //
    // In any case, it returns the tightest enclosing loop.
    innermost = sort( l, innermost );
  }

  // Def-use info will have some dead stuff; dead stuff will have no
  // loop decided on.

  // Am I a loop header?  If so fix up my parent's child and next ptrs.
  if( innermost && innermost->_head == n ) {
    assert( get_loop(n) == innermost, "" );
    IdealLoopTree *p = innermost->_parent;
    IdealLoopTree *l = innermost;
    while( p && l->_head == n ) {
      l->_next = p->_child;     // Put self on parents 'next child'
      p->_child = l;            // Make self as first child of parent
      l = p;                    // Now walk up the parent chain
      p = l->_parent;
    }
  } else {
    // Note that it is possible for a LoopNode to reach here, if the
    // backedge has been made unreachable (hence the LoopNode no longer
    // denotes a Loop, and will eventually be removed).

    // Record tightest enclosing loop for self.  Mark as post-visited.
    set_loop(n, innermost);
    // Also record has_call flag early on
    if( innermost ) {
      if( n->is_Call() && !n->is_CallLeaf() && !n->is_macro() ) {
        // Do not count uncommon calls
        if( !n->is_CallStaticJava() || !n->as_CallStaticJava()->_name ) {
          Node *iff = n->in(0)->in(0);
          // No any calls for vectorized loops.
          if (C->do_superword() ||
              !iff->is_If() ||
              (n->in(0)->Opcode() == Op_IfFalse && (1.0 - iff->as_If()->_prob) >= 0.01) ||
              iff->as_If()->_prob >= 0.01) {
            innermost->_has_call = 1;
          }
        }
      } else if( n->is_Allocate() && n->as_Allocate()->_is_scalar_replaceable ) {
        // Disable loop optimizations if the loop has a scalar replaceable
        // allocation. This disabling may cause a potential performance lost
        // if the allocation is not eliminated for some reason.
        innermost->_allow_optimizations = false;
        innermost->_has_call = 1; // = true
      } else if (n->Opcode() == Op_SafePoint) {
        // Record all safepoints in this loop.
        if (innermost->_safepts == nullptr) innermost->_safepts = new Node_List();
        innermost->_safepts->push(n);
      }
    }
  }

  // Flag as post-visited now
  set_postvisited(n);
  return pre_order;
}

#ifdef ASSERT
//--------------------------verify_regions_in_irreducible_loops----------------
// Iterate down from Root through CFG, verify for every region:
// if it is in an irreducible loop it must be marked as such
void PhaseIdealLoop::verify_regions_in_irreducible_loops() {
  ResourceMark rm;
  if (!_has_irreducible_loops) {
    // last build_loop_tree has not found any irreducible loops
    // hence no region has to be marked is_in_irreduible_loop
    return;
  }

  RootNode* root = C->root();
  Unique_Node_List worklist; // visit all nodes once
  worklist.push(root);
  bool failure = false;
  for (uint i = 0; i < worklist.size(); i++) {
    Node* n = worklist.at(i);
    if (n->is_Region()) {
      RegionNode* region = n->as_Region();
      if (is_in_irreducible_loop(region) &&
          region->loop_status() == RegionNode::LoopStatus::Reducible) {
        failure = true;
        tty->print("irreducible! ");
        region->dump();
      }
    }
    for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
      Node* use = n->fast_out(j);
      if (use->is_CFG()) {
        worklist.push(use); // push if was not pushed before
      }
    }
  }
  assert(!failure, "region in irreducible loop was marked as reducible");
}

//---------------------------is_in_irreducible_loop-------------------------
// Analogous to ciTypeFlow::Block::is_in_irreducible_loop
bool PhaseIdealLoop::is_in_irreducible_loop(RegionNode* region) {
  if (!_has_irreducible_loops) {
    return false; // no irreducible loop in graph
  }
  IdealLoopTree* l = get_loop(region); // l: innermost loop that contains region
  do {
    if (l->_irreducible) {
      return true; // found it
    }
    if (l == _ltree_root) {
      return false; // reached root, terimnate
    }
    l = l->_parent;
  } while (l != nullptr);
  assert(region->is_in_infinite_subgraph(), "must be in infinite subgraph");
  // We have "l->_parent == nullptr", which happens only for infinite loops,
  // where no parent is attached to the loop. We did not find any irreducible
  // loop from this block out to lp. Thus lp only has one entry, and no exit
  // (it is infinite and reducible). We can always rewrite an infinite loop
  // that is nested inside other loops:
  // while(condition) { infinite_loop; }
  // with an equivalent program where the infinite loop is an outermost loop
  // that is not nested in any loop:
  // while(condition) { break; } infinite_loop;
  // Thus, we can understand lp as an outermost loop, and can terminate and
  // conclude: this block is in no irreducible loop.
  return false;
}
#endif

//------------------------------build_loop_early-------------------------------
// Put Data nodes into some loop nest, by setting the _loop_or_ctrl[]->loop mapping.
// First pass computes the earliest controlling node possible.  This is the
// controlling input with the deepest dominating depth.
void PhaseIdealLoop::build_loop_early( VectorSet &visited, Node_List &worklist, Node_Stack &nstack ) {
  while (worklist.size() != 0) {
    // Use local variables nstack_top_n & nstack_top_i to cache values
    // on nstack's top.
    Node *nstack_top_n = worklist.pop();
    uint  nstack_top_i = 0;
//while_nstack_nonempty:
    while (true) {
      // Get parent node and next input's index from stack's top.
      Node  *n = nstack_top_n;
      uint   i = nstack_top_i;
      uint cnt = n->req(); // Count of inputs
      if (i == 0) {        // Pre-process the node.
        if( has_node(n) &&            // Have either loop or control already?
            !has_ctrl(n) ) {          // Have loop picked out already?
          // During "merge_many_backedges" we fold up several nested loops
          // into a single loop.  This makes the members of the original
          // loop bodies pointing to dead loops; they need to move up
          // to the new UNION'd larger loop.  I set the _head field of these
          // dead loops to null and the _parent field points to the owning
          // loop.  Shades of UNION-FIND algorithm.
          IdealLoopTree *ilt;
          while( !(ilt = get_loop(n))->_head ) {
            // Normally I would use a set_loop here.  But in this one special
            // case, it is legal (and expected) to change what loop a Node
            // belongs to.
            _loop_or_ctrl.map(n->_idx, (Node*)(ilt->_parent));
          }
          // Remove safepoints ONLY if I've already seen I don't need one.
          // (the old code here would yank a 2nd safepoint after seeing a
          // first one, even though the 1st did not dominate in the loop body
          // and thus could be avoided indefinitely)
          if( !_verify_only && !_verify_me && ilt->_has_sfpt && n->Opcode() == Op_SafePoint &&
              is_deleteable_safept(n)) {
            Node *in = n->in(TypeFunc::Control);
            lazy_replace(n,in);       // Pull safepoint now
            if (ilt->_safepts != nullptr) {
              ilt->_safepts->yank(n);
            }
            // Carry on with the recursion "as if" we are walking
            // only the control input
            if( !visited.test_set( in->_idx ) ) {
              worklist.push(in);      // Visit this guy later, using worklist
            }
            // Get next node from nstack:
            // - skip n's inputs processing by setting i > cnt;
            // - we also will not call set_early_ctrl(n) since
            //   has_node(n) == true (see the condition above).
            i = cnt + 1;
          }
        }
      } // if (i == 0)

      // Visit all inputs
      bool done = true;       // Assume all n's inputs will be processed
      while (i < cnt) {
        Node *in = n->in(i);
        ++i;
        if (in == nullptr) continue;
        if (in->pinned() && !in->is_CFG())
          set_ctrl(in, in->in(0));
        int is_visited = visited.test_set( in->_idx );
        if (!has_node(in)) {  // No controlling input yet?
          assert( !in->is_CFG(), "CFG Node with no controlling input?" );
          assert( !is_visited, "visit only once" );
          nstack.push(n, i);  // Save parent node and next input's index.
          nstack_top_n = in;  // Process current input now.
          nstack_top_i = 0;
          done = false;       // Not all n's inputs processed.
          break; // continue while_nstack_nonempty;
        } else if (!is_visited) {
          // This guy has a location picked out for him, but has not yet
          // been visited.  Happens to all CFG nodes, for instance.
          // Visit him using the worklist instead of recursion, to break
          // cycles.  Since he has a location already we do not need to
          // find his location before proceeding with the current Node.
          worklist.push(in);  // Visit this guy later, using worklist
        }
      }
      if (done) {
        // All of n's inputs have been processed, complete post-processing.

        // Compute earliest point this Node can go.
        // CFG, Phi, pinned nodes already know their controlling input.
        if (!has_node(n)) {
          // Record earliest legal location
          set_early_ctrl(n, false);
        }
        if (nstack.is_empty()) {
          // Finished all nodes on stack.
          // Process next node on the worklist.
          break;
        }
        // Get saved parent node and next input's index.
        nstack_top_n = nstack.node();
        nstack_top_i = nstack.index();
        nstack.pop();
      }
    } // while (true)
  }
}

//------------------------------dom_lca_internal--------------------------------
// Pair-wise LCA
Node *PhaseIdealLoop::dom_lca_internal( Node *n1, Node *n2 ) const {
  if( !n1 ) return n2;          // Handle null original LCA
  assert( n1->is_CFG(), "" );
  assert( n2->is_CFG(), "" );
  // find LCA of all uses
  uint d1 = dom_depth(n1);
  uint d2 = dom_depth(n2);
  while (n1 != n2) {
    if (d1 > d2) {
      n1 =      idom(n1);
      d1 = dom_depth(n1);
    } else if (d1 < d2) {
      n2 =      idom(n2);
      d2 = dom_depth(n2);
    } else {
      // Here d1 == d2.  Due to edits of the dominator-tree, sections
      // of the tree might have the same depth.  These sections have
      // to be searched more carefully.

      // Scan up all the n1's with equal depth, looking for n2.
      Node *t1 = idom(n1);
      while (dom_depth(t1) == d1) {
        if (t1 == n2)  return n2;
        t1 = idom(t1);
      }
      // Scan up all the n2's with equal depth, looking for n1.
      Node *t2 = idom(n2);
      while (dom_depth(t2) == d2) {
        if (t2 == n1)  return n1;
        t2 = idom(t2);
      }
      // Move up to a new dominator-depth value as well as up the dom-tree.
      n1 = t1;
      n2 = t2;
      d1 = dom_depth(n1);
      d2 = dom_depth(n2);
    }
  }
  return n1;
}

//------------------------------compute_idom-----------------------------------
// Locally compute IDOM using dom_lca call.  Correct only if the incoming
// IDOMs are correct.
Node *PhaseIdealLoop::compute_idom( Node *region ) const {
  assert( region->is_Region(), "" );
  Node *LCA = nullptr;
  for( uint i = 1; i < region->req(); i++ ) {
    if( region->in(i) != C->top() )
      LCA = dom_lca( LCA, region->in(i) );
  }
  return LCA;
}

bool PhaseIdealLoop::verify_dominance(Node* n, Node* use, Node* LCA, Node* early) {
  bool had_error = false;
#ifdef ASSERT
  if (early != C->root()) {
    // Make sure that there's a dominance path from LCA to early
    Node* d = LCA;
    while (d != early) {
      if (d == C->root()) {
        dump_bad_graph("Bad graph detected in compute_lca_of_uses", n, early, LCA);
        tty->print_cr("*** Use %d isn't dominated by def %d ***", use->_idx, n->_idx);
        had_error = true;
        break;
      }
      d = idom(d);
    }
  }
#endif
  return had_error;
}


Node* PhaseIdealLoop::compute_lca_of_uses(Node* n, Node* early, bool verify) {
  // Compute LCA over list of uses
  bool had_error = false;
  Node *LCA = nullptr;
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax && LCA != early; i++) {
    Node* c = n->fast_out(i);
    if (_loop_or_ctrl[c->_idx] == nullptr)
      continue;                 // Skip the occasional dead node
    if( c->is_Phi() ) {         // For Phis, we must land above on the path
      for( uint j=1; j<c->req(); j++ ) {// For all inputs
        if( c->in(j) == n ) {   // Found matching input?
          Node *use = c->in(0)->in(j);
          if (_verify_only && use->is_top()) continue;
          LCA = dom_lca_for_get_late_ctrl( LCA, use, n );
          if (verify) had_error = verify_dominance(n, use, LCA, early) || had_error;
        }
      }
    } else {
      // For CFG data-users, use is in the block just prior
      Node *use = has_ctrl(c) ? get_ctrl(c) : c->in(0);
      LCA = dom_lca_for_get_late_ctrl( LCA, use, n );
      if (verify) had_error = verify_dominance(n, use, LCA, early) || had_error;
    }
  }
  assert(!had_error, "bad dominance");
  return LCA;
}

// Check the shape of the graph at the loop entry. In some cases,
// the shape of the graph does not match the shape outlined below.
// That is caused by the Opaque1 node "protecting" the shape of
// the graph being removed by, for example, the IGVN performed
// in PhaseIdealLoop::build_and_optimize().
//
// After the Opaque1 node has been removed, optimizations (e.g., split-if,
// loop unswitching, and IGVN, or a combination of them) can freely change
// the graph's shape. As a result, the graph shape outlined below cannot
// be guaranteed anymore.
Node* CountedLoopNode::is_canonical_loop_entry() {
  if (!is_main_loop() && !is_post_loop()) {
    return nullptr;
  }
  Node* ctrl = skip_assertion_predicates_with_halt();

  if (ctrl == nullptr || (!ctrl->is_IfTrue() && !ctrl->is_IfFalse())) {
    return nullptr;
  }
  Node* iffm = ctrl->in(0);
  if (iffm == nullptr || iffm->Opcode() != Op_If) {
    return nullptr;
  }
  Node* bolzm = iffm->in(1);
  if (bolzm == nullptr || !bolzm->is_Bool()) {
    return nullptr;
  }
  Node* cmpzm = bolzm->in(1);
  if (cmpzm == nullptr || !cmpzm->is_Cmp()) {
    return nullptr;
  }

  uint input = is_main_loop() ? 2 : 1;
  if (input >= cmpzm->req() || cmpzm->in(input) == nullptr) {
    return nullptr;
  }
  bool res = cmpzm->in(input)->Opcode() == Op_OpaqueZeroTripGuard;
#ifdef ASSERT
  bool found_opaque = false;
  for (uint i = 1; i < cmpzm->req(); i++) {
    Node* opnd = cmpzm->in(i);
    if (opnd && opnd->is_Opaque1()) {
      found_opaque = true;
      break;
    }
  }
  assert(found_opaque == res, "wrong pattern");
#endif
  return res ? cmpzm->in(input) : nullptr;
}

// Find pre loop end from main loop. Returns nullptr if none.
CountedLoopEndNode* CountedLoopNode::find_pre_loop_end() {
  assert(is_main_loop(), "Can only find pre-loop from main-loop");
  // The loop cannot be optimized if the graph shape at the loop entry is
  // inappropriate.
  if (is_canonical_loop_entry() == nullptr) {
    return nullptr;
  }

  Node* p_f = skip_assertion_predicates_with_halt()->in(0)->in(0);
  if (!p_f->is_IfFalse() || !p_f->in(0)->is_CountedLoopEnd()) {
    return nullptr;
  }
  CountedLoopEndNode* pre_end = p_f->in(0)->as_CountedLoopEnd();
  CountedLoopNode* loop_node = pre_end->loopnode();
  if (loop_node == nullptr || !loop_node->is_pre_loop()) {
    return nullptr;
  }
  return pre_end;
}

//------------------------------get_late_ctrl----------------------------------
// Compute latest legal control.
Node *PhaseIdealLoop::get_late_ctrl( Node *n, Node *early ) {
  assert(early != nullptr, "early control should not be null");

  Node* LCA = compute_lca_of_uses(n, early);
#ifdef ASSERT
  if (LCA == C->root() && LCA != early) {
    // def doesn't dominate uses so print some useful debugging output
    compute_lca_of_uses(n, early, true);
  }
#endif

  if (n->is_Load() && LCA != early) {
    LCA = get_late_ctrl_with_anti_dep(n->as_Load(), early, LCA);
  }

  assert(LCA == find_non_split_ctrl(LCA), "unexpected late control");
  return LCA;
}

// if this is a load, check for anti-dependent stores
// We use a conservative algorithm to identify potential interfering
// instructions and for rescheduling the load.  The users of the memory
// input of this load are examined.  Any use which is not a load and is
// dominated by early is considered a potentially interfering store.
// This can produce false positives.
Node* PhaseIdealLoop::get_late_ctrl_with_anti_dep(LoadNode* n, Node* early, Node* LCA) {
  int load_alias_idx = C->get_alias_index(n->adr_type());
  if (C->alias_type(load_alias_idx)->is_rewritable()) {
    Unique_Node_List worklist;

    Node* mem = n->in(MemNode::Memory);
    for (DUIterator_Fast imax, i = mem->fast_outs(imax); i < imax; i++) {
      Node* s = mem->fast_out(i);
      worklist.push(s);
    }
    for (uint i = 0; i < worklist.size() && LCA != early; i++) {
      Node* s = worklist.at(i);
      if (s->is_Load() || s->Opcode() == Op_SafePoint ||
          (s->is_CallStaticJava() && s->as_CallStaticJava()->uncommon_trap_request() != 0) ||
          s->is_Phi()) {
        continue;
      } else if (s->is_MergeMem()) {
        for (DUIterator_Fast imax, i = s->fast_outs(imax); i < imax; i++) {
          Node* s1 = s->fast_out(i);
          worklist.push(s1);
        }
      } else {
        Node* sctrl = has_ctrl(s) ? get_ctrl(s) : s->in(0);
        assert(sctrl != nullptr || !s->is_reachable_from_root(), "must have control");
        if (sctrl != nullptr && !sctrl->is_top() && is_dominator(early, sctrl)) {
          const TypePtr* adr_type = s->adr_type();
          if (s->is_ArrayCopy()) {
            // Copy to known instance needs destination type to test for aliasing
            const TypePtr* dest_type = s->as_ArrayCopy()->_dest_type;
            if (dest_type != TypeOopPtr::BOTTOM) {
              adr_type = dest_type;
            }
          }
          if (C->can_alias(adr_type, load_alias_idx)) {
            LCA = dom_lca_for_get_late_ctrl(LCA, sctrl, n);
          } else if (s->is_CFG() && s->is_Multi()) {
            // Look for the memory use of s (that is the use of its memory projection)
            for (DUIterator_Fast imax, i = s->fast_outs(imax); i < imax; i++) {
              Node* s1 = s->fast_out(i);
              assert(s1->is_Proj(), "projection expected");
              if (_igvn.type(s1) == Type::MEMORY) {
                for (DUIterator_Fast jmax, j = s1->fast_outs(jmax); j < jmax; j++) {
                  Node* s2 = s1->fast_out(j);
                  worklist.push(s2);
                }
              }
            }
          }
        }
      }
    }
    // For Phis only consider Region's inputs that were reached by following the memory edges
    if (LCA != early) {
      for (uint i = 0; i < worklist.size(); i++) {
        Node* s = worklist.at(i);
        if (s->is_Phi() && C->can_alias(s->adr_type(), load_alias_idx)) {
          Node* r = s->in(0);
          for (uint j = 1; j < s->req(); j++) {
            Node* in = s->in(j);
            Node* r_in = r->in(j);
            // We can't reach any node from a Phi because we don't enqueue Phi's uses above
            if (((worklist.member(in) && !in->is_Phi()) || in == mem) && is_dominator(early, r_in)) {
              LCA = dom_lca_for_get_late_ctrl(LCA, r_in, n);
            }
          }
        }
      }
    }
  }
  return LCA;
}

// Is CFG node 'dominator' dominating node 'n'?
bool PhaseIdealLoop::is_dominator(Node* dominator, Node* n) {
  if (dominator == n) {
    return true;
  }
  assert(dominator->is_CFG() && n->is_CFG(), "must have CFG nodes");
  uint dd = dom_depth(dominator);
  while (dom_depth(n) >= dd) {
    if (n == dominator) {
      return true;
    }
    n = idom(n);
  }
  return false;
}

// Is CFG node 'dominator' strictly dominating node 'n'?
bool PhaseIdealLoop::is_strict_dominator(Node* dominator, Node* n) {
  return dominator != n && is_dominator(dominator, n);
}

//------------------------------dom_lca_for_get_late_ctrl_internal-------------
// Pair-wise LCA with tags.
// Tag each index with the node 'tag' currently being processed
// before advancing up the dominator chain using idom().
// Later calls that find a match to 'tag' know that this path has already
// been considered in the current LCA (which is input 'n1' by convention).
// Since get_late_ctrl() is only called once for each node, the tag array
// does not need to be cleared between calls to get_late_ctrl().
// Algorithm trades a larger constant factor for better asymptotic behavior
//
Node *PhaseIdealLoop::dom_lca_for_get_late_ctrl_internal(Node *n1, Node *n2, Node *tag_node) {
  uint d1 = dom_depth(n1);
  uint d2 = dom_depth(n2);
  jlong tag = tag_node->_idx | (((jlong)_dom_lca_tags_round) << 32);

  do {
    if (d1 > d2) {
      // current lca is deeper than n2
      _dom_lca_tags.at_put_grow(n1->_idx, tag);
      n1 =      idom(n1);
      d1 = dom_depth(n1);
    } else if (d1 < d2) {
      // n2 is deeper than current lca
      jlong memo = _dom_lca_tags.at_grow(n2->_idx, 0);
      if (memo == tag) {
        return n1;    // Return the current LCA
      }
      _dom_lca_tags.at_put_grow(n2->_idx, tag);
      n2 =      idom(n2);
      d2 = dom_depth(n2);
    } else {
      // Here d1 == d2.  Due to edits of the dominator-tree, sections
      // of the tree might have the same depth.  These sections have
      // to be searched more carefully.

      // Scan up all the n1's with equal depth, looking for n2.
      _dom_lca_tags.at_put_grow(n1->_idx, tag);
      Node *t1 = idom(n1);
      while (dom_depth(t1) == d1) {
        if (t1 == n2)  return n2;
        _dom_lca_tags.at_put_grow(t1->_idx, tag);
        t1 = idom(t1);
      }
      // Scan up all the n2's with equal depth, looking for n1.
      _dom_lca_tags.at_put_grow(n2->_idx, tag);
      Node *t2 = idom(n2);
      while (dom_depth(t2) == d2) {
        if (t2 == n1)  return n1;
        _dom_lca_tags.at_put_grow(t2->_idx, tag);
        t2 = idom(t2);
      }
      // Move up to a new dominator-depth value as well as up the dom-tree.
      n1 = t1;
      n2 = t2;
      d1 = dom_depth(n1);
      d2 = dom_depth(n2);
    }
  } while (n1 != n2);
  return n1;
}

//------------------------------init_dom_lca_tags------------------------------
// Tag could be a node's integer index, 32bits instead of 64bits in some cases
// Intended use does not involve any growth for the array, so it could
// be of fixed size.
void PhaseIdealLoop::init_dom_lca_tags() {
  uint limit = C->unique() + 1;
  _dom_lca_tags.at_grow(limit, 0);
  _dom_lca_tags_round = 0;
#ifdef ASSERT
  for (uint i = 0; i < limit; ++i) {
    assert(_dom_lca_tags.at(i) == 0, "Must be distinct from each node pointer");
  }
#endif // ASSERT
}

//------------------------------build_loop_late--------------------------------
// Put Data nodes into some loop nest, by setting the _loop_or_ctrl[]->loop mapping.
// Second pass finds latest legal placement, and ideal loop placement.
void PhaseIdealLoop::build_loop_late( VectorSet &visited, Node_List &worklist, Node_Stack &nstack ) {
  while (worklist.size() != 0) {
    Node *n = worklist.pop();
    // Only visit once
    if (visited.test_set(n->_idx)) continue;
    uint cnt = n->outcnt();
    uint   i = 0;
    while (true) {
      assert(_loop_or_ctrl[n->_idx], "no dead nodes");
      // Visit all children
      if (i < cnt) {
        Node* use = n->raw_out(i);
        ++i;
        // Check for dead uses.  Aggressively prune such junk.  It might be
        // dead in the global sense, but still have local uses so I cannot
        // easily call 'remove_dead_node'.
        if (_loop_or_ctrl[use->_idx] != nullptr || use->is_top()) { // Not dead?
          // Due to cycles, we might not hit the same fixed point in the verify
          // pass as we do in the regular pass.  Instead, visit such phis as
          // simple uses of the loop head.
          if( use->in(0) && (use->is_CFG() || use->is_Phi()) ) {
            if( !visited.test(use->_idx) )
              worklist.push(use);
          } else if( !visited.test_set(use->_idx) ) {
            nstack.push(n, i); // Save parent and next use's index.
            n   = use;         // Process all children of current use.
            cnt = use->outcnt();
            i   = 0;
          }
        } else {
          // Do not visit around the backedge of loops via data edges.
          // push dead code onto a worklist
          _deadlist.push(use);
        }
      } else {
        // All of n's children have been processed, complete post-processing.
        build_loop_late_post(n);
        if (C->failing()) { return; }
        if (nstack.is_empty()) {
          // Finished all nodes on stack.
          // Process next node on the worklist.
          break;
        }
        // Get saved parent node and next use's index. Visit the rest of uses.
        n   = nstack.node();
        cnt = n->outcnt();
        i   = nstack.index();
        nstack.pop();
      }
    }
  }
}

// Verify that no data node is scheduled in the outer loop of a strip
// mined loop.
void PhaseIdealLoop::verify_strip_mined_scheduling(Node *n, Node* least) {
#ifdef ASSERT
  if (get_loop(least)->_nest == 0) {
    return;
  }
  IdealLoopTree* loop = get_loop(least);
  Node* head = loop->_head;
  if (head->is_OuterStripMinedLoop() &&
      // Verification can't be applied to fully built strip mined loops
      head->as_Loop()->outer_loop_end()->in(1)->find_int_con(-1) == 0) {
    Node* sfpt = head->as_Loop()->outer_safepoint();
    ResourceMark rm;
    Unique_Node_List wq;
    wq.push(sfpt);
    for (uint i = 0; i < wq.size(); i++) {
      Node *m = wq.at(i);
      for (uint i = 1; i < m->req(); i++) {
        Node* nn = m->in(i);
        if (nn == n) {
          return;
        }
        if (nn != nullptr && has_ctrl(nn) && get_loop(get_ctrl(nn)) == loop) {
          wq.push(nn);
        }
      }
    }
    ShouldNotReachHere();
  }
#endif
}


//------------------------------build_loop_late_post---------------------------
// Put Data nodes into some loop nest, by setting the _loop_or_ctrl[]->loop mapping.
// Second pass finds latest legal placement, and ideal loop placement.
void PhaseIdealLoop::build_loop_late_post(Node *n) {
  build_loop_late_post_work(n, true);
}

void PhaseIdealLoop::build_loop_late_post_work(Node *n, bool pinned) {

  if (n->req() == 2 && (n->Opcode() == Op_ConvI2L || n->Opcode() == Op_CastII) && !C->major_progress() && !_verify_only) {
    _igvn._worklist.push(n);  // Maybe we'll normalize it, if no more loops.
  }

#ifdef ASSERT
  if (_verify_only && !n->is_CFG()) {
    // Check def-use domination.
    compute_lca_of_uses(n, get_ctrl(n), true /* verify */);
  }
#endif

  // CFG and pinned nodes already handled
  if( n->in(0) ) {
    if( n->in(0)->is_top() ) return; // Dead?

    // We'd like +VerifyLoopOptimizations to not believe that Mod's/Loads
    // _must_ be pinned (they have to observe their control edge of course).
    // Unlike Stores (which modify an unallocable resource, the memory
    // state), Mods/Loads can float around.  So free them up.
    switch( n->Opcode() ) {
    case Op_DivI:
    case Op_DivF:
    case Op_DivD:
    case Op_ModI:
    case Op_ModF:
    case Op_ModD:
    case Op_LoadB:              // Same with Loads; they can sink
    case Op_LoadUB:             // during loop optimizations.
    case Op_LoadUS:
    case Op_LoadD:
    case Op_LoadF:
    case Op_LoadI:
    case Op_LoadKlass:
    case Op_LoadNKlass:
    case Op_LoadL:
    case Op_LoadS:
    case Op_LoadP:
    case Op_LoadN:
    case Op_LoadRange:
    case Op_LoadD_unaligned:
    case Op_LoadL_unaligned:
    case Op_StrComp:            // Does a bunch of load-like effects
    case Op_StrEquals:
    case Op_StrIndexOf:
    case Op_StrIndexOfChar:
    case Op_AryEq:
    case Op_VectorizedHashCode:
    case Op_CountPositives:
      pinned = false;
    }
    if (n->is_CMove() || n->is_ConstraintCast()) {
      pinned = false;
    }
    if( pinned ) {
      IdealLoopTree *chosen_loop = get_loop(n->is_CFG() ? n : get_ctrl(n));
      if( !chosen_loop->_child )       // Inner loop?
        chosen_loop->_body.push(n); // Collect inner loops
      return;
    }
  } else {                      // No slot zero
    if( n->is_CFG() ) {         // CFG with no slot 0 is dead
      _loop_or_ctrl.map(n->_idx,nullptr); // No block setting, it's globally dead
      return;
    }
    assert(!n->is_CFG() || n->outcnt() == 0, "");
  }

  // Do I have a "safe range" I can select over?
  Node *early = get_ctrl(n);// Early location already computed

  // Compute latest point this Node can go
  Node *LCA = get_late_ctrl( n, early );
  // LCA is null due to uses being dead
  if( LCA == nullptr ) {
#ifdef ASSERT
    for (DUIterator i1 = n->outs(); n->has_out(i1); i1++) {
      assert(_loop_or_ctrl[n->out(i1)->_idx] == nullptr, "all uses must also be dead");
    }
#endif
    _loop_or_ctrl.map(n->_idx, nullptr); // This node is useless
    _deadlist.push(n);
    return;
  }
  assert(LCA != nullptr && !LCA->is_top(), "no dead nodes");

  Node *legal = LCA;            // Walk 'legal' up the IDOM chain
  Node *least = legal;          // Best legal position so far
  while( early != legal ) {     // While not at earliest legal
    if (legal->is_Start() && !early->is_Root()) {
#ifdef ASSERT
      // Bad graph. Print idom path and fail.
      dump_bad_graph("Bad graph detected in build_loop_late", n, early, LCA);
      assert(false, "Bad graph detected in build_loop_late");
#endif
      C->record_method_not_compilable("Bad graph detected in build_loop_late");
      return;
    }
    // Find least loop nesting depth
    legal = idom(legal);        // Bump up the IDOM tree
    // Check for lower nesting depth
    if( get_loop(legal)->_nest < get_loop(least)->_nest )
      least = legal;
  }
  assert(early == legal || legal != C->root(), "bad dominance of inputs");

  if (least != early) {
    // Move the node above predicates as far up as possible so a
    // following pass of Loop Predication doesn't hoist a predicate
    // that depends on it above that node.
    PredicateEntryIterator predicate_iterator(least);
    while (predicate_iterator.has_next()) {
      Node* next_predicate_entry = predicate_iterator.next_entry();
      if (is_strict_dominator(next_predicate_entry, early)) {
        break;
      }
      least = next_predicate_entry;
    }
  }
  // Try not to place code on a loop entry projection
  // which can inhibit range check elimination.
  if (least != early && !BarrierSet::barrier_set()->barrier_set_c2()->is_gc_specific_loop_opts_pass(_mode)) {
    Node* ctrl_out = least->unique_ctrl_out_or_null();
    if (ctrl_out != nullptr && ctrl_out->is_Loop() &&
        least == ctrl_out->in(LoopNode::EntryControl) &&
        (ctrl_out->is_CountedLoop() || ctrl_out->is_OuterStripMinedLoop())) {
      Node* least_dom = idom(least);
      if (get_loop(least_dom)->is_member(get_loop(least))) {
        least = least_dom;
      }
    }
  }
  // Don't extend live ranges of raw oops
  if (least != early && n->is_ConstraintCast() && n->in(1)->bottom_type()->isa_rawptr() &&
      !n->bottom_type()->isa_rawptr()) {
    least = early;
  }

#ifdef ASSERT
  // Broken part of VerifyLoopOptimizations (F)
  // Reason:
  //   _verify_me->get_ctrl_no_update(n) seems to return wrong result
  /*
  // If verifying, verify that 'verify_me' has a legal location
  // and choose it as our location.
  if( _verify_me ) {
    Node *v_ctrl = _verify_me->get_ctrl_no_update(n);
    Node *legal = LCA;
    while( early != legal ) {   // While not at earliest legal
      if( legal == v_ctrl ) break;  // Check for prior good location
      legal = idom(legal)      ;// Bump up the IDOM tree
    }
    // Check for prior good location
    if( legal == v_ctrl ) least = legal; // Keep prior if found
  }
  */
#endif

  // Assign discovered "here or above" point
  least = find_non_split_ctrl(least);
  verify_strip_mined_scheduling(n, least);
  set_ctrl(n, least);

  // Collect inner loop bodies
  IdealLoopTree *chosen_loop = get_loop(least);
  if( !chosen_loop->_child )   // Inner loop?
    chosen_loop->_body.push(n);// Collect inner loops

  if (!_verify_only && n->Opcode() == Op_OpaqueZeroTripGuard) {
    _zero_trip_guard_opaque_nodes.push(n);
  }

}

#ifdef ASSERT
void PhaseIdealLoop::dump_bad_graph(const char* msg, Node* n, Node* early, Node* LCA) {
  tty->print_cr("%s", msg);
  tty->print("n: "); n->dump();
  tty->print("early(n): "); early->dump();
  if (n->in(0) != nullptr  && !n->in(0)->is_top() &&
      n->in(0) != early && !n->in(0)->is_Root()) {
    tty->print("n->in(0): "); n->in(0)->dump();
  }
  for (uint i = 1; i < n->req(); i++) {
    Node* in1 = n->in(i);
    if (in1 != nullptr && in1 != n && !in1->is_top()) {
      tty->print("n->in(%d): ", i); in1->dump();
      Node* in1_early = get_ctrl(in1);
      tty->print("early(n->in(%d)): ", i); in1_early->dump();
      if (in1->in(0) != nullptr     && !in1->in(0)->is_top() &&
          in1->in(0) != in1_early && !in1->in(0)->is_Root()) {
        tty->print("n->in(%d)->in(0): ", i); in1->in(0)->dump();
      }
      for (uint j = 1; j < in1->req(); j++) {
        Node* in2 = in1->in(j);
        if (in2 != nullptr && in2 != n && in2 != in1 && !in2->is_top()) {
          tty->print("n->in(%d)->in(%d): ", i, j); in2->dump();
          Node* in2_early = get_ctrl(in2);
          tty->print("early(n->in(%d)->in(%d)): ", i, j); in2_early->dump();
          if (in2->in(0) != nullptr     && !in2->in(0)->is_top() &&
              in2->in(0) != in2_early && !in2->in(0)->is_Root()) {
            tty->print("n->in(%d)->in(%d)->in(0): ", i, j); in2->in(0)->dump();
          }
        }
      }
    }
  }
  tty->cr();
  tty->print("LCA(n): "); LCA->dump();
  for (uint i = 0; i < n->outcnt(); i++) {
    Node* u1 = n->raw_out(i);
    if (u1 == n)
      continue;
    tty->print("n->out(%d): ", i); u1->dump();
    if (u1->is_CFG()) {
      for (uint j = 0; j < u1->outcnt(); j++) {
        Node* u2 = u1->raw_out(j);
        if (u2 != u1 && u2 != n && u2->is_CFG()) {
          tty->print("n->out(%d)->out(%d): ", i, j); u2->dump();
        }
      }
    } else {
      Node* u1_later = get_ctrl(u1);
      tty->print("later(n->out(%d)): ", i); u1_later->dump();
      if (u1->in(0) != nullptr     && !u1->in(0)->is_top() &&
          u1->in(0) != u1_later && !u1->in(0)->is_Root()) {
        tty->print("n->out(%d)->in(0): ", i); u1->in(0)->dump();
      }
      for (uint j = 0; j < u1->outcnt(); j++) {
        Node* u2 = u1->raw_out(j);
        if (u2 == n || u2 == u1)
          continue;
        tty->print("n->out(%d)->out(%d): ", i, j); u2->dump();
        if (!u2->is_CFG()) {
          Node* u2_later = get_ctrl(u2);
          tty->print("later(n->out(%d)->out(%d)): ", i, j); u2_later->dump();
          if (u2->in(0) != nullptr     && !u2->in(0)->is_top() &&
              u2->in(0) != u2_later && !u2->in(0)->is_Root()) {
            tty->print("n->out(%d)->in(0): ", i); u2->in(0)->dump();
          }
        }
      }
    }
  }
  dump_idoms(early, LCA);
  tty->cr();
}

// Class to compute the real LCA given an early node and a wrong LCA in a bad graph.
class RealLCA {
  const PhaseIdealLoop* _phase;
  Node* _early;
  Node* _wrong_lca;
  uint _early_index;
  int _wrong_lca_index;

  // Given idom chains of early and wrong LCA: Walk through idoms starting at StartNode and find the first node which
  // is different: Return the previously visited node which must be the real LCA.
  // The node lists also contain _early and _wrong_lca, respectively.
  Node* find_real_lca(Unique_Node_List& early_with_idoms, Unique_Node_List& wrong_lca_with_idoms) {
    int early_index = early_with_idoms.size() - 1;
    int wrong_lca_index = wrong_lca_with_idoms.size() - 1;
    bool found_difference = false;
    do {
      if (early_with_idoms[early_index] != wrong_lca_with_idoms[wrong_lca_index]) {
        // First time early and wrong LCA idoms differ. Real LCA must be at the previous index.
        found_difference = true;
        break;
      }
      early_index--;
      wrong_lca_index--;
    } while (wrong_lca_index >= 0);

    assert(early_index >= 0, "must always find an LCA - cannot be early");
    _early_index = early_index;
    _wrong_lca_index = wrong_lca_index;
    Node* real_lca = early_with_idoms[_early_index + 1]; // Plus one to skip _early.
    assert(found_difference || real_lca == _wrong_lca, "wrong LCA dominates early and is therefore the real LCA");
    return real_lca;
  }

  void dump(Node* real_lca) {
    tty->cr();
    tty->print_cr("idoms of early \"%d %s\":", _early->_idx, _early->Name());
    _phase->dump_idom(_early, _early_index + 1);

    tty->cr();
    tty->print_cr("idoms of (wrong) LCA \"%d %s\":", _wrong_lca->_idx, _wrong_lca->Name());
    _phase->dump_idom(_wrong_lca, _wrong_lca_index + 1);

    tty->cr();
    tty->print("Real LCA of early \"%d %s\" (idom[%d]) and wrong LCA \"%d %s\"",
               _early->_idx, _early->Name(), _early_index, _wrong_lca->_idx, _wrong_lca->Name());
    if (_wrong_lca_index >= 0) {
      tty->print(" (idom[%d])", _wrong_lca_index);
    }
    tty->print_cr(":");
    real_lca->dump();
  }

 public:
  RealLCA(const PhaseIdealLoop* phase, Node* early, Node* wrong_lca)
      : _phase(phase), _early(early), _wrong_lca(wrong_lca), _early_index(0), _wrong_lca_index(0) {
    assert(!wrong_lca->is_Start(), "StartNode is always a common dominator");
  }

  void compute_and_dump() {
    ResourceMark rm;
    Unique_Node_List early_with_idoms;
    Unique_Node_List wrong_lca_with_idoms;
    early_with_idoms.push(_early);
    wrong_lca_with_idoms.push(_wrong_lca);
    _phase->get_idoms(_early, 10000, early_with_idoms);
    _phase->get_idoms(_wrong_lca, 10000, wrong_lca_with_idoms);
    Node* real_lca = find_real_lca(early_with_idoms, wrong_lca_with_idoms);
    dump(real_lca);
  }
};

// Dump the idom chain of early, of the wrong LCA and dump the real LCA of early and wrong LCA.
void PhaseIdealLoop::dump_idoms(Node* early, Node* wrong_lca) {
  assert(!is_dominator(early, wrong_lca), "sanity check that early does not dominate wrong lca");
  assert(!has_ctrl(early) && !has_ctrl(wrong_lca), "sanity check, no data nodes");

  RealLCA real_lca(this, early, wrong_lca);
  real_lca.compute_and_dump();
}
#endif // ASSERT

#ifndef PRODUCT
//------------------------------dump-------------------------------------------
void PhaseIdealLoop::dump() const {
  ResourceMark rm;
  Node_Stack stack(C->live_nodes() >> 2);
  Node_List rpo_list;
  VectorSet visited;
  visited.set(C->top()->_idx);
  rpo(C->root(), stack, visited, rpo_list);
  // Dump root loop indexed by last element in PO order
  dump(_ltree_root, rpo_list.size(), rpo_list);
}

void PhaseIdealLoop::dump(IdealLoopTree* loop, uint idx, Node_List &rpo_list) const {
  loop->dump_head();

  // Now scan for CFG nodes in the same loop
  for (uint j = idx; j > 0; j--) {
    Node* n = rpo_list[j-1];
    if (!_loop_or_ctrl[n->_idx])      // Skip dead nodes
      continue;

    if (get_loop(n) != loop) { // Wrong loop nest
      if (get_loop(n)->_head == n &&    // Found nested loop?
          get_loop(n)->_parent == loop)
        dump(get_loop(n), rpo_list.size(), rpo_list);     // Print it nested-ly
      continue;
    }

    // Dump controlling node
    tty->sp(2 * loop->_nest);
    tty->print("C");
    if (n == C->root()) {
      n->dump();
    } else {
      Node* cached_idom   = idom_no_update(n);
      Node* computed_idom = n->in(0);
      if (n->is_Region()) {
        computed_idom = compute_idom(n);
        // computed_idom() will return n->in(0) when idom(n) is an IfNode (or
        // any MultiBranch ctrl node), so apply a similar transform to
        // the cached idom returned from idom_no_update.
        cached_idom = find_non_split_ctrl(cached_idom);
      }
      tty->print(" ID:%d", computed_idom->_idx);
      n->dump();
      if (cached_idom != computed_idom) {
        tty->print_cr("*** BROKEN IDOM!  Computed as: %d, cached as: %d",
                      computed_idom->_idx, cached_idom->_idx);
      }
    }
    // Dump nodes it controls
    for (uint k = 0; k < _loop_or_ctrl.max(); k++) {
      // (k < C->unique() && get_ctrl(find(k)) == n)
      if (k < C->unique() && _loop_or_ctrl[k] == (Node*)((intptr_t)n + 1)) {
        Node* m = C->root()->find(k);
        if (m && m->outcnt() > 0) {
          if (!(has_ctrl(m) && get_ctrl_no_update(m) == n)) {
            tty->print_cr("*** BROKEN CTRL ACCESSOR!  _loop_or_ctrl[k] is %p, ctrl is %p",
                          _loop_or_ctrl[k], has_ctrl(m) ? get_ctrl_no_update(m) : nullptr);
          }
          tty->sp(2 * loop->_nest + 1);
          m->dump();
        }
      }
    }
  }
}

void PhaseIdealLoop::dump_idom(Node* n, const uint count) const {
  if (has_ctrl(n)) {
    tty->print_cr("No idom for data nodes");
  } else {
    ResourceMark rm;
    Unique_Node_List idoms;
    get_idoms(n, count, idoms);
    dump_idoms_in_reverse(n, idoms);
  }
}

void PhaseIdealLoop::get_idoms(Node* n, const uint count, Unique_Node_List& idoms) const {
  Node* next = n;
  for (uint i = 0; !next->is_Start() && i < count; i++) {
    next = idom(next);
    assert(!idoms.member(next), "duplicated idom is not possible");
    idoms.push(next);
  }
}

void PhaseIdealLoop::dump_idoms_in_reverse(const Node* n, const Node_List& idom_list) const {
  Node* next;
  uint padding = 3;
  uint node_index_padding_width = static_cast<int>(log10(static_cast<double>(C->unique()))) + 1;
  for (int i = idom_list.size() - 1; i >= 0; i--) {
    if (i == 9 || i == 99) {
      padding++;
    }
    next = idom_list[i];
    tty->print_cr("idom[%d]:%*c%*d  %s", i, padding, ' ', node_index_padding_width, next->_idx, next->Name());
  }
  tty->print_cr("n:      %*c%*d  %s", padding, ' ', node_index_padding_width, n->_idx, n->Name());
}
#endif // NOT PRODUCT

// Collect a R-P-O for the whole CFG.
// Result list is in post-order (scan backwards for RPO)
void PhaseIdealLoop::rpo(Node* start, Node_Stack &stk, VectorSet &visited, Node_List &rpo_list) const {
  stk.push(start, 0);
  visited.set(start->_idx);

  while (stk.is_nonempty()) {
    Node* m   = stk.node();
    uint  idx = stk.index();
    if (idx < m->outcnt()) {
      stk.set_index(idx + 1);
      Node* n = m->raw_out(idx);
      if (n->is_CFG() && !visited.test_set(n->_idx)) {
        stk.push(n, 0);
      }
    } else {
      rpo_list.push(m);
      stk.pop();
    }
  }
}


//=============================================================================
//------------------------------LoopTreeIterator-------------------------------

// Advance to next loop tree using a preorder, left-to-right traversal.
void LoopTreeIterator::next() {
  assert(!done(), "must not be done.");
  if (_curnt->_child != nullptr) {
    _curnt = _curnt->_child;
  } else if (_curnt->_next != nullptr) {
    _curnt = _curnt->_next;
  } else {
    while (_curnt != _root && _curnt->_next == nullptr) {
      _curnt = _curnt->_parent;
    }
    if (_curnt == _root) {
      _curnt = nullptr;
      assert(done(), "must be done.");
    } else {
      assert(_curnt->_next != nullptr, "must be more to do");
      _curnt = _curnt->_next;
    }
  }
}
