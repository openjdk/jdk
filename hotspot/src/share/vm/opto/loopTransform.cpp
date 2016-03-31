/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "compiler/compileLog.hpp"
#include "memory/allocation.inline.hpp"
#include "opto/addnode.hpp"
#include "opto/callnode.hpp"
#include "opto/castnode.hpp"
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/divnode.hpp"
#include "opto/loopnode.hpp"
#include "opto/mulnode.hpp"
#include "opto/movenode.hpp"
#include "opto/opaquenode.hpp"
#include "opto/rootnode.hpp"
#include "opto/runtime.hpp"
#include "opto/subnode.hpp"
#include "opto/superword.hpp"
#include "opto/vectornode.hpp"

//------------------------------is_loop_exit-----------------------------------
// Given an IfNode, return the loop-exiting projection or NULL if both
// arms remain in the loop.
Node *IdealLoopTree::is_loop_exit(Node *iff) const {
  if( iff->outcnt() != 2 ) return NULL; // Ignore partially dead tests
  PhaseIdealLoop *phase = _phase;
  // Test is an IfNode, has 2 projections.  If BOTH are in the loop
  // we need loop unswitching instead of peeling.
  if( !is_member(phase->get_loop( iff->raw_out(0) )) )
    return iff->raw_out(0);
  if( !is_member(phase->get_loop( iff->raw_out(1) )) )
    return iff->raw_out(1);
  return NULL;
}


//=============================================================================


//------------------------------record_for_igvn----------------------------
// Put loop body on igvn work list
void IdealLoopTree::record_for_igvn() {
  for( uint i = 0; i < _body.size(); i++ ) {
    Node *n = _body.at(i);
    _phase->_igvn._worklist.push(n);
  }
}

//------------------------------compute_exact_trip_count-----------------------
// Compute loop exact trip count if possible. Do not recalculate trip count for
// split loops (pre-main-post) which have their limits and inits behind Opaque node.
void IdealLoopTree::compute_exact_trip_count( PhaseIdealLoop *phase ) {
  if (!_head->as_Loop()->is_valid_counted_loop()) {
    return;
  }
  CountedLoopNode* cl = _head->as_CountedLoop();
  // Trip count may become nonexact for iteration split loops since
  // RCE modifies limits. Note, _trip_count value is not reset since
  // it is used to limit unrolling of main loop.
  cl->set_nonexact_trip_count();

  // Loop's test should be part of loop.
  if (!phase->is_member(this, phase->get_ctrl(cl->loopexit()->in(CountedLoopEndNode::TestValue))))
    return; // Infinite loop

#ifdef ASSERT
  BoolTest::mask bt = cl->loopexit()->test_trip();
  assert(bt == BoolTest::lt || bt == BoolTest::gt ||
         bt == BoolTest::ne, "canonical test is expected");
#endif

  Node* init_n = cl->init_trip();
  Node* limit_n = cl->limit();
  if (init_n  != NULL &&  init_n->is_Con() &&
      limit_n != NULL && limit_n->is_Con()) {
    // Use longs to avoid integer overflow.
    int stride_con  = cl->stride_con();
    jlong init_con   = cl->init_trip()->get_int();
    jlong limit_con  = cl->limit()->get_int();
    int stride_m    = stride_con - (stride_con > 0 ? 1 : -1);
    jlong trip_count = (limit_con - init_con + stride_m)/stride_con;
    if (trip_count > 0 && (julong)trip_count < (julong)max_juint) {
      // Set exact trip count.
      cl->set_exact_trip_count((uint)trip_count);
    }
  }
}

//------------------------------compute_profile_trip_cnt----------------------------
// Compute loop trip count from profile data as
//    (backedge_count + loop_exit_count) / loop_exit_count
void IdealLoopTree::compute_profile_trip_cnt( PhaseIdealLoop *phase ) {
  if (!_head->is_CountedLoop()) {
    return;
  }
  CountedLoopNode* head = _head->as_CountedLoop();
  if (head->profile_trip_cnt() != COUNT_UNKNOWN) {
    return; // Already computed
  }
  float trip_cnt = (float)max_jint; // default is big

  Node* back = head->in(LoopNode::LoopBackControl);
  while (back != head) {
    if ((back->Opcode() == Op_IfTrue || back->Opcode() == Op_IfFalse) &&
        back->in(0) &&
        back->in(0)->is_If() &&
        back->in(0)->as_If()->_fcnt != COUNT_UNKNOWN &&
        back->in(0)->as_If()->_prob != PROB_UNKNOWN) {
      break;
    }
    back = phase->idom(back);
  }
  if (back != head) {
    assert((back->Opcode() == Op_IfTrue || back->Opcode() == Op_IfFalse) &&
           back->in(0), "if-projection exists");
    IfNode* back_if = back->in(0)->as_If();
    float loop_back_cnt = back_if->_fcnt * back_if->_prob;

    // Now compute a loop exit count
    float loop_exit_cnt = 0.0f;
    for( uint i = 0; i < _body.size(); i++ ) {
      Node *n = _body[i];
      if( n->is_If() ) {
        IfNode *iff = n->as_If();
        if( iff->_fcnt != COUNT_UNKNOWN && iff->_prob != PROB_UNKNOWN ) {
          Node *exit = is_loop_exit(iff);
          if( exit ) {
            float exit_prob = iff->_prob;
            if (exit->Opcode() == Op_IfFalse) exit_prob = 1.0 - exit_prob;
            if (exit_prob > PROB_MIN) {
              float exit_cnt = iff->_fcnt * exit_prob;
              loop_exit_cnt += exit_cnt;
            }
          }
        }
      }
    }
    if (loop_exit_cnt > 0.0f) {
      trip_cnt = (loop_back_cnt + loop_exit_cnt) / loop_exit_cnt;
    } else {
      // No exit count so use
      trip_cnt = loop_back_cnt;
    }
  }
#ifndef PRODUCT
  if (TraceProfileTripCount) {
    tty->print_cr("compute_profile_trip_cnt  lp: %d cnt: %f\n", head->_idx, trip_cnt);
  }
#endif
  head->set_profile_trip_cnt(trip_cnt);
}

//---------------------is_invariant_addition-----------------------------
// Return nonzero index of invariant operand for an Add or Sub
// of (nonconstant) invariant and variant values. Helper for reassociate_invariants.
int IdealLoopTree::is_invariant_addition(Node* n, PhaseIdealLoop *phase) {
  int op = n->Opcode();
  if (op == Op_AddI || op == Op_SubI) {
    bool in1_invar = this->is_invariant(n->in(1));
    bool in2_invar = this->is_invariant(n->in(2));
    if (in1_invar && !in2_invar) return 1;
    if (!in1_invar && in2_invar) return 2;
  }
  return 0;
}

//---------------------reassociate_add_sub-----------------------------
// Reassociate invariant add and subtract expressions:
//
// inv1 + (x + inv2)  =>  ( inv1 + inv2) + x
// (x + inv2) + inv1  =>  ( inv1 + inv2) + x
// inv1 + (x - inv2)  =>  ( inv1 - inv2) + x
// inv1 - (inv2 - x)  =>  ( inv1 - inv2) + x
// (x + inv2) - inv1  =>  (-inv1 + inv2) + x
// (x - inv2) + inv1  =>  ( inv1 - inv2) + x
// (x - inv2) - inv1  =>  (-inv1 - inv2) + x
// inv1 + (inv2 - x)  =>  ( inv1 + inv2) - x
// inv1 - (x - inv2)  =>  ( inv1 + inv2) - x
// (inv2 - x) + inv1  =>  ( inv1 + inv2) - x
// (inv2 - x) - inv1  =>  (-inv1 + inv2) - x
// inv1 - (x + inv2)  =>  ( inv1 - inv2) - x
//
Node* IdealLoopTree::reassociate_add_sub(Node* n1, PhaseIdealLoop *phase) {
  if (!n1->is_Add() && !n1->is_Sub() || n1->outcnt() == 0) return NULL;
  if (is_invariant(n1)) return NULL;
  int inv1_idx = is_invariant_addition(n1, phase);
  if (!inv1_idx) return NULL;
  // Don't mess with add of constant (igvn moves them to expression tree root.)
  if (n1->is_Add() && n1->in(2)->is_Con()) return NULL;
  Node* inv1 = n1->in(inv1_idx);
  Node* n2 = n1->in(3 - inv1_idx);
  int inv2_idx = is_invariant_addition(n2, phase);
  if (!inv2_idx) return NULL;
  Node* x    = n2->in(3 - inv2_idx);
  Node* inv2 = n2->in(inv2_idx);

  bool neg_x    = n2->is_Sub() && inv2_idx == 1;
  bool neg_inv2 = n2->is_Sub() && inv2_idx == 2;
  bool neg_inv1 = n1->is_Sub() && inv1_idx == 2;
  if (n1->is_Sub() && inv1_idx == 1) {
    neg_x    = !neg_x;
    neg_inv2 = !neg_inv2;
  }
  Node* inv1_c = phase->get_ctrl(inv1);
  Node* inv2_c = phase->get_ctrl(inv2);
  Node* n_inv1;
  if (neg_inv1) {
    Node *zero = phase->_igvn.intcon(0);
    phase->set_ctrl(zero, phase->C->root());
    n_inv1 = new SubINode(zero, inv1);
    phase->register_new_node(n_inv1, inv1_c);
  } else {
    n_inv1 = inv1;
  }
  Node* inv;
  if (neg_inv2) {
    inv = new SubINode(n_inv1, inv2);
  } else {
    inv = new AddINode(n_inv1, inv2);
  }
  phase->register_new_node(inv, phase->get_early_ctrl(inv));

  Node* addx;
  if (neg_x) {
    addx = new SubINode(inv, x);
  } else {
    addx = new AddINode(x, inv);
  }
  phase->register_new_node(addx, phase->get_ctrl(x));
  phase->_igvn.replace_node(n1, addx);
  assert(phase->get_loop(phase->get_ctrl(n1)) == this, "");
  _body.yank(n1);
  return addx;
}

//---------------------reassociate_invariants-----------------------------
// Reassociate invariant expressions:
void IdealLoopTree::reassociate_invariants(PhaseIdealLoop *phase) {
  for (int i = _body.size() - 1; i >= 0; i--) {
    Node *n = _body.at(i);
    for (int j = 0; j < 5; j++) {
      Node* nn = reassociate_add_sub(n, phase);
      if (nn == NULL) break;
      n = nn; // again
    };
  }
}

//------------------------------policy_peeling---------------------------------
// Return TRUE or FALSE if the loop should be peeled or not.  Peel if we can
// make some loop-invariant test (usually a null-check) happen before the loop.
bool IdealLoopTree::policy_peeling( PhaseIdealLoop *phase ) const {
  Node *test = ((IdealLoopTree*)this)->tail();
  int  body_size = ((IdealLoopTree*)this)->_body.size();
  // Peeling does loop cloning which can result in O(N^2) node construction
  if( body_size > 255 /* Prevent overflow for large body_size */
      || (body_size * body_size + phase->C->live_nodes()) > phase->C->max_node_limit() ) {
    return false;           // too large to safely clone
  }

  // check for vectorized loops, any peeling done was already applied
  if (_head->is_CountedLoop() && _head->as_CountedLoop()->do_unroll_only()) return false;

  while( test != _head ) {      // Scan till run off top of loop
    if( test->is_If() ) {       // Test?
      Node *ctrl = phase->get_ctrl(test->in(1));
      if (ctrl->is_top())
        return false;           // Found dead test on live IF?  No peeling!
      // Standard IF only has one input value to check for loop invariance
      assert(test->Opcode() == Op_If || test->Opcode() == Op_CountedLoopEnd || test->Opcode() == Op_RangeCheck, "Check this code when new subtype is added");
      // Condition is not a member of this loop?
      if( !is_member(phase->get_loop(ctrl)) &&
          is_loop_exit(test) )
        return true;            // Found reason to peel!
    }
    // Walk up dominators to loop _head looking for test which is
    // executed on every path thru loop.
    test = phase->idom(test);
  }
  return false;
}

//------------------------------peeled_dom_test_elim---------------------------
// If we got the effect of peeling, either by actually peeling or by making
// a pre-loop which must execute at least once, we can remove all
// loop-invariant dominated tests in the main body.
void PhaseIdealLoop::peeled_dom_test_elim( IdealLoopTree *loop, Node_List &old_new ) {
  bool progress = true;
  while( progress ) {
    progress = false;           // Reset for next iteration
    Node *prev = loop->_head->in(LoopNode::LoopBackControl);//loop->tail();
    Node *test = prev->in(0);
    while( test != loop->_head ) { // Scan till run off top of loop

      int p_op = prev->Opcode();
      if( (p_op == Op_IfFalse || p_op == Op_IfTrue) &&
          test->is_If() &&      // Test?
          !test->in(1)->is_Con() && // And not already obvious?
          // Condition is not a member of this loop?
          !loop->is_member(get_loop(get_ctrl(test->in(1))))){
        // Walk loop body looking for instances of this test
        for( uint i = 0; i < loop->_body.size(); i++ ) {
          Node *n = loop->_body.at(i);
          if( n->is_If() && n->in(1) == test->in(1) /*&& n != loop->tail()->in(0)*/ ) {
            // IfNode was dominated by version in peeled loop body
            progress = true;
            dominated_by( old_new[prev->_idx], n );
          }
        }
      }
      prev = test;
      test = idom(test);
    } // End of scan tests in loop

  } // End of while( progress )
}

//------------------------------do_peeling-------------------------------------
// Peel the first iteration of the given loop.
// Step 1: Clone the loop body.  The clone becomes the peeled iteration.
//         The pre-loop illegally has 2 control users (old & new loops).
// Step 2: Make the old-loop fall-in edges point to the peeled iteration.
//         Do this by making the old-loop fall-in edges act as if they came
//         around the loopback from the prior iteration (follow the old-loop
//         backedges) and then map to the new peeled iteration.  This leaves
//         the pre-loop with only 1 user (the new peeled iteration), but the
//         peeled-loop backedge has 2 users.
// Step 3: Cut the backedge on the clone (so its not a loop) and remove the
//         extra backedge user.
//
//                   orig
//
//                  stmt1
//                    |
//                    v
//              loop predicate
//                    |
//                    v
//                   loop<----+
//                     |      |
//                   stmt2    |
//                     |      |
//                     v      |
//                    if      ^
//                   / \      |
//                  /   \     |
//                 v     v    |
//               false true   |
//               /       \    |
//              /         ----+
//             |
//             v
//           exit
//
//
//            after clone loop
//
//                   stmt1
//                     |
//                     v
//               loop predicate
//                 /       \
//        clone   /         \   orig
//               /           \
//              /             \
//             v               v
//   +---->loop clone          loop<----+
//   |      |                    |      |
//   |    stmt2 clone          stmt2    |
//   |      |                    |      |
//   |      v                    v      |
//   ^      if clone            If      ^
//   |      / \                / \      |
//   |     /   \              /   \     |
//   |    v     v            v     v    |
//   |    true  false      false true   |
//   |    /         \      /       \    |
//   +----           \    /         ----+
//                    \  /
//                    1v v2
//                  region
//                     |
//                     v
//                   exit
//
//
//         after peel and predicate move
//
//                   stmt1
//                    /
//                   /
//        clone     /            orig
//                 /
//                /              +----------+
//               /               |          |
//              /          loop predicate   |
//             /                 |          |
//            v                  v          |
//   TOP-->loop clone          loop<----+   |
//          |                    |      |   |
//        stmt2 clone          stmt2    |   |
//          |                    |      |   ^
//          v                    v      |   |
//          if clone            If      ^   |
//          / \                / \      |   |
//         /   \              /   \     |   |
//        v     v            v     v    |   |
//      true   false      false  true   |   |
//        |         \      /       \    |   |
//        |          \    /         ----+   ^
//        |           \  /                  |
//        |           1v v2                 |
//        v         region                  |
//        |            |                    |
//        |            v                    |
//        |          exit                   |
//        |                                 |
//        +--------------->-----------------+
//
//
//              final graph
//
//                  stmt1
//                    |
//                    v
//                  stmt2 clone
//                    |
//                    v
//                   if clone
//                  / |
//                 /  |
//                v   v
//            false  true
//             |      |
//             |      v
//             | loop predicate
//             |      |
//             |      v
//             |     loop<----+
//             |      |       |
//             |    stmt2     |
//             |      |       |
//             |      v       |
//             v      if      ^
//             |     /  \     |
//             |    /    \    |
//             |   v     v    |
//             | false  true  |
//             |  |        \  |
//             v  v         --+
//            region
//              |
//              v
//             exit
//
void PhaseIdealLoop::do_peeling( IdealLoopTree *loop, Node_List &old_new ) {

  C->set_major_progress();
  // Peeling a 'main' loop in a pre/main/post situation obfuscates the
  // 'pre' loop from the main and the 'pre' can no longer have its
  // iterations adjusted.  Therefore, we need to declare this loop as
  // no longer a 'main' loop; it will need new pre and post loops before
  // we can do further RCE.
#ifndef PRODUCT
  if (TraceLoopOpts) {
    tty->print("Peel         ");
    loop->dump_head();
  }
#endif
  Node* head = loop->_head;
  bool counted_loop = head->is_CountedLoop();
  if (counted_loop) {
    CountedLoopNode *cl = head->as_CountedLoop();
    assert(cl->trip_count() > 0, "peeling a fully unrolled loop");
    cl->set_trip_count(cl->trip_count() - 1);
    if (cl->is_main_loop()) {
      cl->set_normal_loop();
#ifndef PRODUCT
      if (PrintOpto && VerifyLoopOptimizations) {
        tty->print("Peeling a 'main' loop; resetting to 'normal' ");
        loop->dump_head();
      }
#endif
    }
  }
  Node* entry = head->in(LoopNode::EntryControl);

  // Step 1: Clone the loop body.  The clone becomes the peeled iteration.
  //         The pre-loop illegally has 2 control users (old & new loops).
  clone_loop( loop, old_new, dom_depth(head) );

  // Step 2: Make the old-loop fall-in edges point to the peeled iteration.
  //         Do this by making the old-loop fall-in edges act as if they came
  //         around the loopback from the prior iteration (follow the old-loop
  //         backedges) and then map to the new peeled iteration.  This leaves
  //         the pre-loop with only 1 user (the new peeled iteration), but the
  //         peeled-loop backedge has 2 users.
  Node* new_entry = old_new[head->in(LoopNode::LoopBackControl)->_idx];
  _igvn.hash_delete(head);
  head->set_req(LoopNode::EntryControl, new_entry);
  for (DUIterator_Fast jmax, j = head->fast_outs(jmax); j < jmax; j++) {
    Node* old = head->fast_out(j);
    if (old->in(0) == loop->_head && old->req() == 3 && old->is_Phi()) {
      Node* new_exit_value = old_new[old->in(LoopNode::LoopBackControl)->_idx];
      if (!new_exit_value )     // Backedge value is ALSO loop invariant?
        // Then loop body backedge value remains the same.
        new_exit_value = old->in(LoopNode::LoopBackControl);
      _igvn.hash_delete(old);
      old->set_req(LoopNode::EntryControl, new_exit_value);
    }
  }


  // Step 3: Cut the backedge on the clone (so its not a loop) and remove the
  //         extra backedge user.
  Node* new_head = old_new[head->_idx];
  _igvn.hash_delete(new_head);
  new_head->set_req(LoopNode::LoopBackControl, C->top());
  for (DUIterator_Fast j2max, j2 = new_head->fast_outs(j2max); j2 < j2max; j2++) {
    Node* use = new_head->fast_out(j2);
    if (use->in(0) == new_head && use->req() == 3 && use->is_Phi()) {
      _igvn.hash_delete(use);
      use->set_req(LoopNode::LoopBackControl, C->top());
    }
  }


  // Step 4: Correct dom-depth info.  Set to loop-head depth.
  int dd = dom_depth(head);
  set_idom(head, head->in(1), dd);
  for (uint j3 = 0; j3 < loop->_body.size(); j3++) {
    Node *old = loop->_body.at(j3);
    Node *nnn = old_new[old->_idx];
    if (!has_ctrl(nnn))
      set_idom(nnn, idom(nnn), dd-1);
  }

  // Now force out all loop-invariant dominating tests.  The optimizer
  // finds some, but we _know_ they are all useless.
  peeled_dom_test_elim(loop,old_new);

  loop->record_for_igvn();
}

#define EMPTY_LOOP_SIZE 7 // number of nodes in an empty loop

//------------------------------policy_maximally_unroll------------------------
// Calculate exact loop trip count and return true if loop can be maximally
// unrolled.
bool IdealLoopTree::policy_maximally_unroll( PhaseIdealLoop *phase ) const {
  CountedLoopNode *cl = _head->as_CountedLoop();
  assert(cl->is_normal_loop(), "");
  if (!cl->is_valid_counted_loop())
    return false; // Malformed counted loop

  if (!cl->has_exact_trip_count()) {
    // Trip count is not exact.
    return false;
  }

  uint trip_count = cl->trip_count();
  // Note, max_juint is used to indicate unknown trip count.
  assert(trip_count > 1, "one iteration loop should be optimized out already");
  assert(trip_count < max_juint, "exact trip_count should be less than max_uint.");

  // Real policy: if we maximally unroll, does it get too big?
  // Allow the unrolled mess to get larger than standard loop
  // size.  After all, it will no longer be a loop.
  uint body_size    = _body.size();
  uint unroll_limit = (uint)LoopUnrollLimit * 4;
  assert( (intx)unroll_limit == LoopUnrollLimit * 4, "LoopUnrollLimit must fit in 32bits");
  if (trip_count > unroll_limit || body_size > unroll_limit) {
    return false;
  }

  // Fully unroll a loop with few iterations regardless next
  // conditions since following loop optimizations will split
  // such loop anyway (pre-main-post).
  if (trip_count <= 3)
    return true;

  // Take into account that after unroll conjoined heads and tails will fold,
  // otherwise policy_unroll() may allow more unrolling than max unrolling.
  uint new_body_size = EMPTY_LOOP_SIZE + (body_size - EMPTY_LOOP_SIZE) * trip_count;
  uint tst_body_size = (new_body_size - EMPTY_LOOP_SIZE) / trip_count + EMPTY_LOOP_SIZE;
  if (body_size != tst_body_size) // Check for int overflow
    return false;
  if (new_body_size > unroll_limit ||
      // Unrolling can result in a large amount of node construction
      new_body_size >= phase->C->max_node_limit() - phase->C->live_nodes()) {
    return false;
  }

  // Do not unroll a loop with String intrinsics code.
  // String intrinsics are large and have loops.
  for (uint k = 0; k < _body.size(); k++) {
    Node* n = _body.at(k);
    switch (n->Opcode()) {
      case Op_StrComp:
      case Op_StrEquals:
      case Op_StrIndexOf:
      case Op_StrIndexOfChar:
      case Op_EncodeISOArray:
      case Op_AryEq:
      case Op_HasNegatives: {
        return false;
      }
#if INCLUDE_RTM_OPT
      case Op_FastLock:
      case Op_FastUnlock: {
        // Don't unroll RTM locking code because it is large.
        if (UseRTMLocking) {
          return false;
        }
      }
#endif
    } // switch
  }

  return true; // Do maximally unroll
}


//------------------------------policy_unroll----------------------------------
// Return TRUE or FALSE if the loop should be unrolled or not.  Unroll if
// the loop is a CountedLoop and the body is small enough.
bool IdealLoopTree::policy_unroll(PhaseIdealLoop *phase) {

  CountedLoopNode *cl = _head->as_CountedLoop();
  assert(cl->is_normal_loop() || cl->is_main_loop(), "");

  if (!cl->is_valid_counted_loop())
    return false; // Malformed counted loop

  // Protect against over-unrolling.
  // After split at least one iteration will be executed in pre-loop.
  if (cl->trip_count() <= (uint)(cl->is_normal_loop() ? 2 : 1)) return false;

  _local_loop_unroll_limit = LoopUnrollLimit;
  _local_loop_unroll_factor = 4;
  int future_unroll_ct = cl->unrolled_count() * 2;
  if (!cl->do_unroll_only()) {
    if (future_unroll_ct > LoopMaxUnroll) return false;
  } else {
    // obey user constraints on vector mapped loops with additional unrolling applied
    int unroll_constraint = (cl->slp_max_unroll()) ? cl->slp_max_unroll() : 1;
    if ((future_unroll_ct / unroll_constraint) > LoopMaxUnroll) return false;
  }

  // Check for initial stride being a small enough constant
  if (abs(cl->stride_con()) > (1<<2)*future_unroll_ct) return false;

  // Don't unroll if the next round of unrolling would push us
  // over the expected trip count of the loop.  One is subtracted
  // from the expected trip count because the pre-loop normally
  // executes 1 iteration.
  if (UnrollLimitForProfileCheck > 0 &&
      cl->profile_trip_cnt() != COUNT_UNKNOWN &&
      future_unroll_ct        > UnrollLimitForProfileCheck &&
      (float)future_unroll_ct > cl->profile_trip_cnt() - 1.0) {
    return false;
  }

  // When unroll count is greater than LoopUnrollMin, don't unroll if:
  //   the residual iterations are more than 10% of the trip count
  //   and rounds of "unroll,optimize" are not making significant progress
  //   Progress defined as current size less than 20% larger than previous size.
  if (UseSuperWord && cl->node_count_before_unroll() > 0 &&
      future_unroll_ct > LoopUnrollMin &&
      (future_unroll_ct - 1) * (100 / LoopPercentProfileLimit) > cl->profile_trip_cnt() &&
      1.2 * cl->node_count_before_unroll() < (double)_body.size()) {
    return false;
  }

  Node *init_n = cl->init_trip();
  Node *limit_n = cl->limit();
  int stride_con = cl->stride_con();
  // Non-constant bounds.
  // Protect against over-unrolling when init or/and limit are not constant
  // (so that trip_count's init value is maxint) but iv range is known.
  if (init_n   == NULL || !init_n->is_Con()  ||
      limit_n  == NULL || !limit_n->is_Con()) {
    Node* phi = cl->phi();
    if (phi != NULL) {
      assert(phi->is_Phi() && phi->in(0) == _head, "Counted loop should have iv phi.");
      const TypeInt* iv_type = phase->_igvn.type(phi)->is_int();
      int next_stride = stride_con * 2; // stride after this unroll
      if (next_stride > 0) {
        if (iv_type->_lo + next_stride <= iv_type->_lo || // overflow
            iv_type->_lo + next_stride >  iv_type->_hi) {
          return false;  // over-unrolling
        }
      } else if (next_stride < 0) {
        if (iv_type->_hi + next_stride >= iv_type->_hi || // overflow
            iv_type->_hi + next_stride <  iv_type->_lo) {
          return false;  // over-unrolling
        }
      }
    }
  }

  // After unroll limit will be adjusted: new_limit = limit-stride.
  // Bailout if adjustment overflow.
  const TypeInt* limit_type = phase->_igvn.type(limit_n)->is_int();
  if (stride_con > 0 && ((limit_type->_hi - stride_con) >= limit_type->_hi) ||
      stride_con < 0 && ((limit_type->_lo - stride_con) <= limit_type->_lo))
    return false;  // overflow

  // Adjust body_size to determine if we unroll or not
  uint body_size = _body.size();
  // Key test to unroll loop in CRC32 java code
  int xors_in_loop = 0;
  // Also count ModL, DivL and MulL which expand mightly
  for (uint k = 0; k < _body.size(); k++) {
    Node* n = _body.at(k);
    switch (n->Opcode()) {
      case Op_XorI: xors_in_loop++; break; // CRC32 java code
      case Op_ModL: body_size += 30; break;
      case Op_DivL: body_size += 30; break;
      case Op_MulL: body_size += 10; break;
      case Op_StrComp:
      case Op_StrEquals:
      case Op_StrIndexOf:
      case Op_StrIndexOfChar:
      case Op_EncodeISOArray:
      case Op_AryEq:
      case Op_HasNegatives: {
        // Do not unroll a loop with String intrinsics code.
        // String intrinsics are large and have loops.
        return false;
      }
#if INCLUDE_RTM_OPT
      case Op_FastLock:
      case Op_FastUnlock: {
        // Don't unroll RTM locking code because it is large.
        if (UseRTMLocking) {
          return false;
        }
      }
#endif
    } // switch
  }

  if (UseSuperWord) {
    if (!cl->is_reduction_loop()) {
      phase->mark_reductions(this);
    }

    // Only attempt slp analysis when user controls do not prohibit it
    if (LoopMaxUnroll > _local_loop_unroll_factor) {
      // Once policy_slp_analysis succeeds, mark the loop with the
      // maximal unroll factor so that we minimize analysis passes
      if (future_unroll_ct >= _local_loop_unroll_factor) {
        policy_unroll_slp_analysis(cl, phase, future_unroll_ct);
      }
    }
  }

  int slp_max_unroll_factor = cl->slp_max_unroll();
  if (cl->has_passed_slp()) {
    if (slp_max_unroll_factor >= future_unroll_ct) return true;
    // Normal case: loop too big
    return false;
  }

  // Check for being too big
  if (body_size > (uint)_local_loop_unroll_limit) {
    if (xors_in_loop >= 4 && body_size < (uint)LoopUnrollLimit*4) return true;
    // Normal case: loop too big
    return false;
  }

  if (cl->do_unroll_only()) {
    if (TraceSuperWordLoopUnrollAnalysis) {
      tty->print_cr("policy_unroll passed vector loop(vlen=%d,factor = %d)\n", slp_max_unroll_factor, future_unroll_ct);
    }
  }

  // Unroll once!  (Each trip will soon do double iterations)
  return true;
}

void IdealLoopTree::policy_unroll_slp_analysis(CountedLoopNode *cl, PhaseIdealLoop *phase, int future_unroll_ct) {
  // Enable this functionality target by target as needed
  if (SuperWordLoopUnrollAnalysis) {
    if (!cl->was_slp_analyzed()) {
      SuperWord sw(phase);
      sw.transform_loop(this, false);

      // If the loop is slp canonical analyze it
      if (sw.early_return() == false) {
        sw.unrolling_analysis(_local_loop_unroll_factor);
      }
    }

    if (cl->has_passed_slp()) {
      int slp_max_unroll_factor = cl->slp_max_unroll();
      if (slp_max_unroll_factor >= future_unroll_ct) {
        int new_limit = cl->node_count_before_unroll() * slp_max_unroll_factor;
        if (new_limit > LoopUnrollLimit) {
          if (TraceSuperWordLoopUnrollAnalysis) {
            tty->print_cr("slp analysis unroll=%d, default limit=%d\n", new_limit, _local_loop_unroll_limit);
          }
          _local_loop_unroll_limit = new_limit;
        }
      }
    }
  }
}

//------------------------------policy_align-----------------------------------
// Return TRUE or FALSE if the loop should be cache-line aligned.  Gather the
// expression that does the alignment.  Note that only one array base can be
// aligned in a loop (unless the VM guarantees mutual alignment).  Note that
// if we vectorize short memory ops into longer memory ops, we may want to
// increase alignment.
bool IdealLoopTree::policy_align( PhaseIdealLoop *phase ) const {
  return false;
}

//------------------------------policy_range_check-----------------------------
// Return TRUE or FALSE if the loop should be range-check-eliminated.
// Actually we do iteration-splitting, a more powerful form of RCE.
bool IdealLoopTree::policy_range_check( PhaseIdealLoop *phase ) const {
  if (!RangeCheckElimination) return false;

  CountedLoopNode *cl = _head->as_CountedLoop();
  // If we unrolled with no intention of doing RCE and we later
  // changed our minds, we got no pre-loop.  Either we need to
  // make a new pre-loop, or we gotta disallow RCE.
  if (cl->is_main_no_pre_loop()) return false; // Disallowed for now.
  Node *trip_counter = cl->phi();

  // check for vectorized loops, some opts are no longer needed
  if (cl->do_unroll_only()) return false;

  // Check loop body for tests of trip-counter plus loop-invariant vs
  // loop-invariant.
  for (uint i = 0; i < _body.size(); i++) {
    Node *iff = _body[i];
    if (iff->Opcode() == Op_If ||
        iff->Opcode() == Op_RangeCheck) { // Test?

      // Comparing trip+off vs limit
      Node *bol = iff->in(1);
      if (bol->req() != 2) continue; // dead constant test
      if (!bol->is_Bool()) {
        assert(UseLoopPredicate && bol->Opcode() == Op_Conv2B, "predicate check only");
        continue;
      }
      if (bol->as_Bool()->_test._test == BoolTest::ne)
        continue; // not RC

      Node *cmp = bol->in(1);
      Node *rc_exp = cmp->in(1);
      Node *limit = cmp->in(2);

      Node *limit_c = phase->get_ctrl(limit);
      if( limit_c == phase->C->top() )
        return false;           // Found dead test on live IF?  No RCE!
      if( is_member(phase->get_loop(limit_c) ) ) {
        // Compare might have operands swapped; commute them
        rc_exp = cmp->in(2);
        limit  = cmp->in(1);
        limit_c = phase->get_ctrl(limit);
        if( is_member(phase->get_loop(limit_c) ) )
          continue;             // Both inputs are loop varying; cannot RCE
      }

      if (!phase->is_scaled_iv_plus_offset(rc_exp, trip_counter, NULL, NULL)) {
        continue;
      }
      // Yeah!  Found a test like 'trip+off vs limit'
      // Test is an IfNode, has 2 projections.  If BOTH are in the loop
      // we need loop unswitching instead of iteration splitting.
      if( is_loop_exit(iff) )
        return true;            // Found reason to split iterations
    } // End of is IF
  }

  return false;
}

//------------------------------policy_peel_only-------------------------------
// Return TRUE or FALSE if the loop should NEVER be RCE'd or aligned.  Useful
// for unrolling loops with NO array accesses.
bool IdealLoopTree::policy_peel_only( PhaseIdealLoop *phase ) const {
  // check for vectorized loops, any peeling done was already applied
  if (_head->is_CountedLoop() && _head->as_CountedLoop()->do_unroll_only()) return false;

  for( uint i = 0; i < _body.size(); i++ )
    if( _body[i]->is_Mem() )
      return false;

  // No memory accesses at all!
  return true;
}

//------------------------------clone_up_backedge_goo--------------------------
// If Node n lives in the back_ctrl block and cannot float, we clone a private
// version of n in preheader_ctrl block and return that, otherwise return n.
Node *PhaseIdealLoop::clone_up_backedge_goo( Node *back_ctrl, Node *preheader_ctrl, Node *n, VectorSet &visited, Node_Stack &clones ) {
  if( get_ctrl(n) != back_ctrl ) return n;

  // Only visit once
  if (visited.test_set(n->_idx)) {
    Node *x = clones.find(n->_idx);
    if (x != NULL)
      return x;
    return n;
  }

  Node *x = NULL;               // If required, a clone of 'n'
  // Check for 'n' being pinned in the backedge.
  if( n->in(0) && n->in(0) == back_ctrl ) {
    assert(clones.find(n->_idx) == NULL, "dead loop");
    x = n->clone();             // Clone a copy of 'n' to preheader
    clones.push(x, n->_idx);
    x->set_req( 0, preheader_ctrl ); // Fix x's control input to preheader
  }

  // Recursive fixup any other input edges into x.
  // If there are no changes we can just return 'n', otherwise
  // we need to clone a private copy and change it.
  for( uint i = 1; i < n->req(); i++ ) {
    Node *g = clone_up_backedge_goo( back_ctrl, preheader_ctrl, n->in(i), visited, clones );
    if( g != n->in(i) ) {
      if( !x ) {
        assert(clones.find(n->_idx) == NULL, "dead loop");
        x = n->clone();
        clones.push(x, n->_idx);
      }
      x->set_req(i, g);
    }
  }
  if( x ) {                     // x can legally float to pre-header location
    register_new_node( x, preheader_ctrl );
    return x;
  } else {                      // raise n to cover LCA of uses
    set_ctrl( n, find_non_split_ctrl(back_ctrl->in(0)) );
  }
  return n;
}

bool PhaseIdealLoop::cast_incr_before_loop(Node* incr, Node* ctrl, Node* loop) {
  Node* castii = new CastIINode(incr, TypeInt::INT, true);
  castii->set_req(0, ctrl);
  register_new_node(castii, ctrl);
  for (DUIterator_Fast imax, i = incr->fast_outs(imax); i < imax; i++) {
    Node* n = incr->fast_out(i);
    if (n->is_Phi() && n->in(0) == loop) {
      int nrep = n->replace_edge(incr, castii);
      return true;
    }
  }
  return false;
}

//------------------------------insert_pre_post_loops--------------------------
// Insert pre and post loops.  If peel_only is set, the pre-loop can not have
// more iterations added.  It acts as a 'peel' only, no lower-bound RCE, no
// alignment.  Useful to unroll loops that do no array accesses.
void PhaseIdealLoop::insert_pre_post_loops( IdealLoopTree *loop, Node_List &old_new, bool peel_only ) {

#ifndef PRODUCT
  if (TraceLoopOpts) {
    if (peel_only)
      tty->print("PeelMainPost ");
    else
      tty->print("PreMainPost  ");
    loop->dump_head();
  }
#endif
  C->set_major_progress();

  // Find common pieces of the loop being guarded with pre & post loops
  CountedLoopNode *main_head = loop->_head->as_CountedLoop();
  assert( main_head->is_normal_loop(), "" );
  CountedLoopEndNode *main_end = main_head->loopexit();
  guarantee(main_end != NULL, "no loop exit node");
  assert( main_end->outcnt() == 2, "1 true, 1 false path only" );
  uint dd_main_head = dom_depth(main_head);
  uint max = main_head->outcnt();

  Node *pre_header= main_head->in(LoopNode::EntryControl);
  Node *init      = main_head->init_trip();
  Node *incr      = main_end ->incr();
  Node *limit     = main_end ->limit();
  Node *stride    = main_end ->stride();
  Node *cmp       = main_end ->cmp_node();
  BoolTest::mask b_test = main_end->test_trip();

  // Need only 1 user of 'bol' because I will be hacking the loop bounds.
  Node *bol = main_end->in(CountedLoopEndNode::TestValue);
  if( bol->outcnt() != 1 ) {
    bol = bol->clone();
    register_new_node(bol,main_end->in(CountedLoopEndNode::TestControl));
    _igvn.replace_input_of(main_end, CountedLoopEndNode::TestValue, bol);
  }
  // Need only 1 user of 'cmp' because I will be hacking the loop bounds.
  if( cmp->outcnt() != 1 ) {
    cmp = cmp->clone();
    register_new_node(cmp,main_end->in(CountedLoopEndNode::TestControl));
    _igvn.replace_input_of(bol, 1, cmp);
  }

  //------------------------------
  // Step A: Create Post-Loop.
  Node* main_exit = main_end->proj_out(false);
  assert( main_exit->Opcode() == Op_IfFalse, "" );
  int dd_main_exit = dom_depth(main_exit);

  // Step A1: Clone the loop body.  The clone becomes the post-loop.  The main
  // loop pre-header illegally has 2 control users (old & new loops).
  clone_loop( loop, old_new, dd_main_exit );
  assert( old_new[main_end ->_idx]->Opcode() == Op_CountedLoopEnd, "" );
  CountedLoopNode *post_head = old_new[main_head->_idx]->as_CountedLoop();
  post_head->set_post_loop(main_head);

  // Reduce the post-loop trip count.
  CountedLoopEndNode* post_end = old_new[main_end ->_idx]->as_CountedLoopEnd();
  post_end->_prob = PROB_FAIR;

  // Build the main-loop normal exit.
  IfFalseNode *new_main_exit = new IfFalseNode(main_end);
  _igvn.register_new_node_with_optimizer( new_main_exit );
  set_idom(new_main_exit, main_end, dd_main_exit );
  set_loop(new_main_exit, loop->_parent);

  // Step A2: Build a zero-trip guard for the post-loop.  After leaving the
  // main-loop, the post-loop may not execute at all.  We 'opaque' the incr
  // (the main-loop trip-counter exit value) because we will be changing
  // the exit value (via unrolling) so we cannot constant-fold away the zero
  // trip guard until all unrolling is done.
  Node *zer_opaq = new Opaque1Node(C, incr);
  Node *zer_cmp  = new CmpINode( zer_opaq, limit );
  Node *zer_bol  = new BoolNode( zer_cmp, b_test );
  register_new_node( zer_opaq, new_main_exit );
  register_new_node( zer_cmp , new_main_exit );
  register_new_node( zer_bol , new_main_exit );

  // Build the IfNode
  IfNode *zer_iff = new IfNode( new_main_exit, zer_bol, PROB_FAIR, COUNT_UNKNOWN );
  _igvn.register_new_node_with_optimizer( zer_iff );
  set_idom(zer_iff, new_main_exit, dd_main_exit);
  set_loop(zer_iff, loop->_parent);

  // Plug in the false-path, taken if we need to skip post-loop
  _igvn.replace_input_of(main_exit, 0, zer_iff);
  set_idom(main_exit, zer_iff, dd_main_exit);
  set_idom(main_exit->unique_out(), zer_iff, dd_main_exit);
  // Make the true-path, must enter the post loop
  Node *zer_taken = new IfTrueNode( zer_iff );
  _igvn.register_new_node_with_optimizer( zer_taken );
  set_idom(zer_taken, zer_iff, dd_main_exit);
  set_loop(zer_taken, loop->_parent);
  // Plug in the true path
  _igvn.hash_delete( post_head );
  post_head->set_req(LoopNode::EntryControl, zer_taken);
  set_idom(post_head, zer_taken, dd_main_exit);

  Arena *a = Thread::current()->resource_area();
  VectorSet visited(a);
  Node_Stack clones(a, main_head->back_control()->outcnt());
  // Step A3: Make the fall-in values to the post-loop come from the
  // fall-out values of the main-loop.
  for (DUIterator_Fast imax, i = main_head->fast_outs(imax); i < imax; i++) {
    Node* main_phi = main_head->fast_out(i);
    if( main_phi->is_Phi() && main_phi->in(0) == main_head && main_phi->outcnt() >0 ) {
      Node *post_phi = old_new[main_phi->_idx];
      Node *fallmain  = clone_up_backedge_goo(main_head->back_control(),
                                              post_head->init_control(),
                                              main_phi->in(LoopNode::LoopBackControl),
                                              visited, clones);
      _igvn.hash_delete(post_phi);
      post_phi->set_req( LoopNode::EntryControl, fallmain );
    }
  }

  // Update local caches for next stanza
  main_exit = new_main_exit;


  //------------------------------
  // Step B: Create Pre-Loop.

  // Step B1: Clone the loop body.  The clone becomes the pre-loop.  The main
  // loop pre-header illegally has 2 control users (old & new loops).
  clone_loop( loop, old_new, dd_main_head );
  CountedLoopNode*    pre_head = old_new[main_head->_idx]->as_CountedLoop();
  CountedLoopEndNode* pre_end  = old_new[main_end ->_idx]->as_CountedLoopEnd();
  pre_head->set_pre_loop(main_head);
  Node *pre_incr = old_new[incr->_idx];

  // Reduce the pre-loop trip count.
  pre_end->_prob = PROB_FAIR;

  // Find the pre-loop normal exit.
  Node* pre_exit = pre_end->proj_out(false);
  assert( pre_exit->Opcode() == Op_IfFalse, "" );
  IfFalseNode *new_pre_exit = new IfFalseNode(pre_end);
  _igvn.register_new_node_with_optimizer( new_pre_exit );
  set_idom(new_pre_exit, pre_end, dd_main_head);
  set_loop(new_pre_exit, loop->_parent);

  // Step B2: Build a zero-trip guard for the main-loop.  After leaving the
  // pre-loop, the main-loop may not execute at all.  Later in life this
  // zero-trip guard will become the minimum-trip guard when we unroll
  // the main-loop.
  Node *min_opaq = new Opaque1Node(C, limit);
  Node *min_cmp  = new CmpINode( pre_incr, min_opaq );
  Node *min_bol  = new BoolNode( min_cmp, b_test );
  register_new_node( min_opaq, new_pre_exit );
  register_new_node( min_cmp , new_pre_exit );
  register_new_node( min_bol , new_pre_exit );

  // Build the IfNode (assume the main-loop is executed always).
  IfNode *min_iff = new IfNode( new_pre_exit, min_bol, PROB_ALWAYS, COUNT_UNKNOWN );
  _igvn.register_new_node_with_optimizer( min_iff );
  set_idom(min_iff, new_pre_exit, dd_main_head);
  set_loop(min_iff, loop->_parent);

  // Plug in the false-path, taken if we need to skip main-loop
  _igvn.hash_delete( pre_exit );
  pre_exit->set_req(0, min_iff);
  set_idom(pre_exit, min_iff, dd_main_head);
  set_idom(pre_exit->unique_out(), min_iff, dd_main_head);
  // Make the true-path, must enter the main loop
  Node *min_taken = new IfTrueNode( min_iff );
  _igvn.register_new_node_with_optimizer( min_taken );
  set_idom(min_taken, min_iff, dd_main_head);
  set_loop(min_taken, loop->_parent);
  // Plug in the true path
  _igvn.hash_delete( main_head );
  main_head->set_req(LoopNode::EntryControl, min_taken);
  set_idom(main_head, min_taken, dd_main_head);

  visited.Clear();
  clones.clear();
  // Step B3: Make the fall-in values to the main-loop come from the
  // fall-out values of the pre-loop.
  for (DUIterator_Fast i2max, i2 = main_head->fast_outs(i2max); i2 < i2max; i2++) {
    Node* main_phi = main_head->fast_out(i2);
    if( main_phi->is_Phi() && main_phi->in(0) == main_head && main_phi->outcnt() > 0 ) {
      Node *pre_phi = old_new[main_phi->_idx];
      Node *fallpre  = clone_up_backedge_goo(pre_head->back_control(),
                                             main_head->init_control(),
                                             pre_phi->in(LoopNode::LoopBackControl),
                                             visited, clones);
      _igvn.hash_delete(main_phi);
      main_phi->set_req( LoopNode::EntryControl, fallpre );
    }
  }

  // Nodes inside the loop may be control dependent on a predicate
  // that was moved before the preloop. If the back branch of the main
  // or post loops becomes dead, those nodes won't be dependent on the
  // test that guards that loop nest anymore which could lead to an
  // incorrect array access because it executes independently of the
  // test that was guarding the loop nest. We add a special CastII on
  // the if branch that enters the loop, between the input induction
  // variable value and the induction variable Phi to preserve correct
  // dependencies.

  // CastII for the post loop:
  bool inserted = cast_incr_before_loop(zer_opaq->in(1), zer_taken, post_head);
  assert(inserted, "no castII inserted");

  // CastII for the main loop:
  inserted = cast_incr_before_loop(pre_incr, min_taken, main_head);
  assert(inserted, "no castII inserted");

  // Step B4: Shorten the pre-loop to run only 1 iteration (for now).
  // RCE and alignment may change this later.
  Node *cmp_end = pre_end->cmp_node();
  assert( cmp_end->in(2) == limit, "" );
  Node *pre_limit = new AddINode( init, stride );

  // Save the original loop limit in this Opaque1 node for
  // use by range check elimination.
  Node *pre_opaq  = new Opaque1Node(C, pre_limit, limit);

  register_new_node( pre_limit, pre_head->in(0) );
  register_new_node( pre_opaq , pre_head->in(0) );

  // Since no other users of pre-loop compare, I can hack limit directly
  assert( cmp_end->outcnt() == 1, "no other users" );
  _igvn.hash_delete(cmp_end);
  cmp_end->set_req(2, peel_only ? pre_limit : pre_opaq);

  // Special case for not-equal loop bounds:
  // Change pre loop test, main loop test, and the
  // main loop guard test to use lt or gt depending on stride
  // direction:
  // positive stride use <
  // negative stride use >
  //
  // not-equal test is kept for post loop to handle case
  // when init > limit when stride > 0 (and reverse).

  if (pre_end->in(CountedLoopEndNode::TestValue)->as_Bool()->_test._test == BoolTest::ne) {

    BoolTest::mask new_test = (main_end->stride_con() > 0) ? BoolTest::lt : BoolTest::gt;
    // Modify pre loop end condition
    Node* pre_bol = pre_end->in(CountedLoopEndNode::TestValue)->as_Bool();
    BoolNode* new_bol0 = new BoolNode(pre_bol->in(1), new_test);
    register_new_node( new_bol0, pre_head->in(0) );
    _igvn.replace_input_of(pre_end, CountedLoopEndNode::TestValue, new_bol0);
    // Modify main loop guard condition
    assert(min_iff->in(CountedLoopEndNode::TestValue) == min_bol, "guard okay");
    BoolNode* new_bol1 = new BoolNode(min_bol->in(1), new_test);
    register_new_node( new_bol1, new_pre_exit );
    _igvn.hash_delete(min_iff);
    min_iff->set_req(CountedLoopEndNode::TestValue, new_bol1);
    // Modify main loop end condition
    BoolNode* main_bol = main_end->in(CountedLoopEndNode::TestValue)->as_Bool();
    BoolNode* new_bol2 = new BoolNode(main_bol->in(1), new_test);
    register_new_node( new_bol2, main_end->in(CountedLoopEndNode::TestControl) );
    _igvn.replace_input_of(main_end, CountedLoopEndNode::TestValue, new_bol2);
  }

  // Flag main loop
  main_head->set_main_loop();
  if( peel_only ) main_head->set_main_no_pre_loop();

  // Subtract a trip count for the pre-loop.
  main_head->set_trip_count(main_head->trip_count() - 1);

  // It's difficult to be precise about the trip-counts
  // for the pre/post loops.  They are usually very short,
  // so guess that 4 trips is a reasonable value.
  post_head->set_profile_trip_cnt(4.0);
  pre_head->set_profile_trip_cnt(4.0);

  // Now force out all loop-invariant dominating tests.  The optimizer
  // finds some, but we _know_ they are all useless.
  peeled_dom_test_elim(loop,old_new);
  loop->record_for_igvn();
}

//------------------------------insert_vector_post_loop------------------------
// Insert a copy of the atomic unrolled vectorized main loop as a post loop,
// unroll_policy has already informed us that more unrolling is about to happen to
// the main loop.  The resultant post loop will serve as a vectorized drain loop.
void PhaseIdealLoop::insert_vector_post_loop(IdealLoopTree *loop, Node_List &old_new) {
  if (!loop->_head->is_CountedLoop()) return;

  CountedLoopNode *cl = loop->_head->as_CountedLoop();

  // only process vectorized main loops
  if (!cl->is_vectorized_loop() || !cl->is_main_loop()) return;

  int slp_max_unroll_factor = cl->slp_max_unroll();
  int cur_unroll = cl->unrolled_count();

  if (slp_max_unroll_factor == 0) return;

  // only process atomic unroll vector loops (not super unrolled after vectorization)
  if (cur_unroll != slp_max_unroll_factor) return;

  // we only ever process this one time
  if (cl->has_atomic_post_loop()) return;

#ifndef PRODUCT
  if (TraceLoopOpts) {
    tty->print("PostVector  ");
    loop->dump_head();
  }
#endif
  C->set_major_progress();

  // Find common pieces of the loop being guarded with pre & post loops
  CountedLoopNode *main_head = loop->_head->as_CountedLoop();
  CountedLoopEndNode *main_end = main_head->loopexit();
  guarantee(main_end != NULL, "no loop exit node");
  // diagnostic to show loop end is not properly formed
  assert(main_end->outcnt() == 2, "1 true, 1 false path only");
  uint dd_main_head = dom_depth(main_head);
  uint max = main_head->outcnt();

  // mark this loop as processed
  main_head->mark_has_atomic_post_loop();

  Node *pre_header = main_head->in(LoopNode::EntryControl);
  Node *init = main_head->init_trip();
  Node *incr = main_end->incr();
  Node *limit = main_end->limit();
  Node *stride = main_end->stride();
  Node *cmp = main_end->cmp_node();
  BoolTest::mask b_test = main_end->test_trip();

  //------------------------------
  // Step A: Create a new post-Loop.
  Node* main_exit = main_end->proj_out(false);
  assert(main_exit->Opcode() == Op_IfFalse, "");
  int dd_main_exit = dom_depth(main_exit);

  // Step A1: Clone the loop body of main.  The clone becomes the vector post-loop.
  // The main loop pre-header illegally has 2 control users (old & new loops).
  clone_loop(loop, old_new, dd_main_exit);
  assert(old_new[main_end->_idx]->Opcode() == Op_CountedLoopEnd, "");
  CountedLoopNode *post_head = old_new[main_head->_idx]->as_CountedLoop();
  post_head->set_normal_loop();
  post_head->set_post_loop(main_head);

  // Reduce the post-loop trip count.
  CountedLoopEndNode* post_end = old_new[main_end->_idx]->as_CountedLoopEnd();
  post_end->_prob = PROB_FAIR;

  // Build the main-loop normal exit.
  IfFalseNode *new_main_exit = new IfFalseNode(main_end);
  _igvn.register_new_node_with_optimizer(new_main_exit);
  set_idom(new_main_exit, main_end, dd_main_exit);
  set_loop(new_main_exit, loop->_parent);

  // Step A2: Build a zero-trip guard for the vector post-loop.  After leaving the
  // main-loop, the vector post-loop may not execute at all.  We 'opaque' the incr
  // (the vectorized main-loop trip-counter exit value) because we will be changing
  // the exit value (via additional unrolling) so we cannot constant-fold away the zero
  // trip guard until all unrolling is done.
  Node *zer_opaq = new Opaque1Node(C, incr);
  Node *zer_cmp = new CmpINode(zer_opaq, limit);
  Node *zer_bol = new BoolNode(zer_cmp, b_test);
  register_new_node(zer_opaq, new_main_exit);
  register_new_node(zer_cmp, new_main_exit);
  register_new_node(zer_bol, new_main_exit);

  // Build the IfNode
  IfNode *zer_iff = new IfNode(new_main_exit, zer_bol, PROB_FAIR, COUNT_UNKNOWN);
  _igvn.register_new_node_with_optimizer(zer_iff);
  set_idom(zer_iff, new_main_exit, dd_main_exit);
  set_loop(zer_iff, loop->_parent);

  // Plug in the false-path, taken if we need to skip vector post-loop
  _igvn.replace_input_of(main_exit, 0, zer_iff);
  set_idom(main_exit, zer_iff, dd_main_exit);
  set_idom(main_exit->unique_out(), zer_iff, dd_main_exit);
  // Make the true-path, must enter the vector post loop
  Node *zer_taken = new IfTrueNode(zer_iff);
  _igvn.register_new_node_with_optimizer(zer_taken);
  set_idom(zer_taken, zer_iff, dd_main_exit);
  set_loop(zer_taken, loop->_parent);
  // Plug in the true path
  _igvn.hash_delete(post_head);
  post_head->set_req(LoopNode::EntryControl, zer_taken);
  set_idom(post_head, zer_taken, dd_main_exit);

  Arena *a = Thread::current()->resource_area();
  VectorSet visited(a);
  Node_Stack clones(a, main_head->back_control()->outcnt());
  // Step A3: Make the fall-in values to the vector post-loop come from the
  // fall-out values of the main-loop.
  for (DUIterator_Fast imax, i = main_head->fast_outs(imax); i < imax; i++) {
    Node* main_phi = main_head->fast_out(i);
    if (main_phi->is_Phi() && main_phi->in(0) == main_head && main_phi->outcnt() >0) {
      Node *cur_phi = old_new[main_phi->_idx];
      Node *fallnew = clone_up_backedge_goo(main_head->back_control(),
                                            post_head->init_control(),
                                            main_phi->in(LoopNode::LoopBackControl),
                                            visited, clones);
      _igvn.hash_delete(cur_phi);
      cur_phi->set_req(LoopNode::EntryControl, fallnew);
    }
  }

  // CastII for the new post loop:
  bool inserted = cast_incr_before_loop(zer_opaq->in(1), zer_taken, post_head);
  assert(inserted, "no castII inserted");

  // It's difficult to be precise about the trip-counts
  // for post loops.  They are usually very short,
  // so guess that unit vector trips is a reasonable value.
  post_head->set_profile_trip_cnt((float)slp_max_unroll_factor);

  // Now force out all loop-invariant dominating tests.  The optimizer
  // finds some, but we _know_ they are all useless.
  peeled_dom_test_elim(loop, old_new);
  loop->record_for_igvn();
}

//------------------------------is_invariant-----------------------------
// Return true if n is invariant
bool IdealLoopTree::is_invariant(Node* n) const {
  Node *n_c = _phase->has_ctrl(n) ? _phase->get_ctrl(n) : n;
  if (n_c->is_top()) return false;
  return !is_member(_phase->get_loop(n_c));
}


//------------------------------do_unroll--------------------------------------
// Unroll the loop body one step - make each trip do 2 iterations.
void PhaseIdealLoop::do_unroll( IdealLoopTree *loop, Node_List &old_new, bool adjust_min_trip ) {
  assert(LoopUnrollLimit, "");
  CountedLoopNode *loop_head = loop->_head->as_CountedLoop();
  CountedLoopEndNode *loop_end = loop_head->loopexit();
  assert(loop_end, "");
#ifndef PRODUCT
  if (PrintOpto && VerifyLoopOptimizations) {
    tty->print("Unrolling ");
    loop->dump_head();
  } else if (TraceLoopOpts) {
    if (loop_head->trip_count() < (uint)LoopUnrollLimit) {
      tty->print("Unroll %d(%2d) ", loop_head->unrolled_count()*2, loop_head->trip_count());
    } else {
      tty->print("Unroll %d     ", loop_head->unrolled_count()*2);
    }
    loop->dump_head();
  }

  if (C->do_vector_loop() && (PrintOpto && VerifyLoopOptimizations || TraceLoopOpts)) {
    Arena* arena = Thread::current()->resource_area();
    Node_Stack stack(arena, C->live_nodes() >> 2);
    Node_List rpo_list;
    VectorSet visited(arena);
    visited.set(loop_head->_idx);
    rpo( loop_head, stack, visited, rpo_list );
    dump(loop, rpo_list.size(), rpo_list );
  }
#endif

  // Remember loop node count before unrolling to detect
  // if rounds of unroll,optimize are making progress
  loop_head->set_node_count_before_unroll(loop->_body.size());

  Node *ctrl  = loop_head->in(LoopNode::EntryControl);
  Node *limit = loop_head->limit();
  Node *init  = loop_head->init_trip();
  Node *stride = loop_head->stride();

  Node *opaq = NULL;
  if (adjust_min_trip) {       // If not maximally unrolling, need adjustment
    // Search for zero-trip guard.

    // Check the shape of the graph at the loop entry. If an inappropriate
    // graph shape is encountered, the compiler bails out loop unrolling;
    // compilation of the method will still succeed.
    if (!is_canonical_main_loop_entry(loop_head)) {
      return;
    }
    opaq = ctrl->in(0)->in(1)->in(1)->in(2);
    // Zero-trip test uses an 'opaque' node which is not shared.
    assert(opaq->outcnt() == 1 && opaq->in(1) == limit, "");
  }

  C->set_major_progress();

  Node* new_limit = NULL;
  if (UnrollLimitCheck) {
    int stride_con = stride->get_int();
    int stride_p = (stride_con > 0) ? stride_con : -stride_con;
    uint old_trip_count = loop_head->trip_count();
    // Verify that unroll policy result is still valid.
    assert(old_trip_count > 1 &&
           (!adjust_min_trip || stride_p <= (1<<3)*loop_head->unrolled_count()), "sanity");

    // Adjust loop limit to keep valid iterations number after unroll.
    // Use (limit - stride) instead of (((limit - init)/stride) & (-2))*stride
    // which may overflow.
    if (!adjust_min_trip) {
      assert(old_trip_count > 1 && (old_trip_count & 1) == 0,
             "odd trip count for maximally unroll");
      // Don't need to adjust limit for maximally unroll since trip count is even.
    } else if (loop_head->has_exact_trip_count() && init->is_Con()) {
      // Loop's limit is constant. Loop's init could be constant when pre-loop
      // become peeled iteration.
      jlong init_con = init->get_int();
      // We can keep old loop limit if iterations count stays the same:
      //   old_trip_count == new_trip_count * 2
      // Note: since old_trip_count >= 2 then new_trip_count >= 1
      // so we also don't need to adjust zero trip test.
      jlong limit_con  = limit->get_int();
      // (stride_con*2) not overflow since stride_con <= 8.
      int new_stride_con = stride_con * 2;
      int stride_m    = new_stride_con - (stride_con > 0 ? 1 : -1);
      jlong trip_count = (limit_con - init_con + stride_m)/new_stride_con;
      // New trip count should satisfy next conditions.
      assert(trip_count > 0 && (julong)trip_count < (julong)max_juint/2, "sanity");
      uint new_trip_count = (uint)trip_count;
      adjust_min_trip = (old_trip_count != new_trip_count*2);
    }

    if (adjust_min_trip) {
      // Step 2: Adjust the trip limit if it is called for.
      // The adjustment amount is -stride. Need to make sure if the
      // adjustment underflows or overflows, then the main loop is skipped.
      Node* cmp = loop_end->cmp_node();
      assert(cmp->in(2) == limit, "sanity");
      assert(opaq != NULL && opaq->in(1) == limit, "sanity");

      // Verify that policy_unroll result is still valid.
      const TypeInt* limit_type = _igvn.type(limit)->is_int();
      assert(stride_con > 0 && ((limit_type->_hi - stride_con) < limit_type->_hi) ||
             stride_con < 0 && ((limit_type->_lo - stride_con) > limit_type->_lo), "sanity");

      if (limit->is_Con()) {
        // The check in policy_unroll and the assert above guarantee
        // no underflow if limit is constant.
        new_limit = _igvn.intcon(limit->get_int() - stride_con);
        set_ctrl(new_limit, C->root());
      } else {
        // Limit is not constant.
        if (loop_head->unrolled_count() == 1) { // only for first unroll
          // Separate limit by Opaque node in case it is an incremented
          // variable from previous loop to avoid using pre-incremented
          // value which could increase register pressure.
          // Otherwise reorg_offsets() optimization will create a separate
          // Opaque node for each use of trip-counter and as result
          // zero trip guard limit will be different from loop limit.
          assert(has_ctrl(opaq), "should have it");
          Node* opaq_ctrl = get_ctrl(opaq);
          limit = new Opaque2Node( C, limit );
          register_new_node( limit, opaq_ctrl );
        }
        if (stride_con > 0 && (java_subtract(limit_type->_lo, stride_con) < limit_type->_lo) ||
            stride_con < 0 && (java_subtract(limit_type->_hi, stride_con) > limit_type->_hi)) {
          // No underflow.
          new_limit = new SubINode(limit, stride);
        } else {
          // (limit - stride) may underflow.
          // Clamp the adjustment value with MININT or MAXINT:
          //
          //   new_limit = limit-stride
          //   if (stride > 0)
          //     new_limit = (limit < new_limit) ? MININT : new_limit;
          //   else
          //     new_limit = (limit > new_limit) ? MAXINT : new_limit;
          //
          BoolTest::mask bt = loop_end->test_trip();
          assert(bt == BoolTest::lt || bt == BoolTest::gt, "canonical test is expected");
          Node* adj_max = _igvn.intcon((stride_con > 0) ? min_jint : max_jint);
          set_ctrl(adj_max, C->root());
          Node* old_limit = NULL;
          Node* adj_limit = NULL;
          Node* bol = limit->is_CMove() ? limit->in(CMoveNode::Condition) : NULL;
          if (loop_head->unrolled_count() > 1 &&
              limit->is_CMove() && limit->Opcode() == Op_CMoveI &&
              limit->in(CMoveNode::IfTrue) == adj_max &&
              bol->as_Bool()->_test._test == bt &&
              bol->in(1)->Opcode() == Op_CmpI &&
              bol->in(1)->in(2) == limit->in(CMoveNode::IfFalse)) {
            // Loop was unrolled before.
            // Optimize the limit to avoid nested CMove:
            // use original limit as old limit.
            old_limit = bol->in(1)->in(1);
            // Adjust previous adjusted limit.
            adj_limit = limit->in(CMoveNode::IfFalse);
            adj_limit = new SubINode(adj_limit, stride);
          } else {
            old_limit = limit;
            adj_limit = new SubINode(limit, stride);
          }
          assert(old_limit != NULL && adj_limit != NULL, "");
          register_new_node( adj_limit, ctrl ); // adjust amount
          Node* adj_cmp = new CmpINode(old_limit, adj_limit);
          register_new_node( adj_cmp, ctrl );
          Node* adj_bool = new BoolNode(adj_cmp, bt);
          register_new_node( adj_bool, ctrl );
          new_limit = new CMoveINode(adj_bool, adj_limit, adj_max, TypeInt::INT);
        }
        register_new_node(new_limit, ctrl);
      }
      assert(new_limit != NULL, "");
      // Replace in loop test.
      assert(loop_end->in(1)->in(1) == cmp, "sanity");
      if (cmp->outcnt() == 1 && loop_end->in(1)->outcnt() == 1) {
        // Don't need to create new test since only one user.
        _igvn.hash_delete(cmp);
        cmp->set_req(2, new_limit);
      } else {
        // Create new test since it is shared.
        Node* ctrl2 = loop_end->in(0);
        Node* cmp2  = cmp->clone();
        cmp2->set_req(2, new_limit);
        register_new_node(cmp2, ctrl2);
        Node* bol2 = loop_end->in(1)->clone();
        bol2->set_req(1, cmp2);
        register_new_node(bol2, ctrl2);
        _igvn.replace_input_of(loop_end, 1, bol2);
      }
      // Step 3: Find the min-trip test guaranteed before a 'main' loop.
      // Make it a 1-trip test (means at least 2 trips).

      // Guard test uses an 'opaque' node which is not shared.  Hence I
      // can edit it's inputs directly.  Hammer in the new limit for the
      // minimum-trip guard.
      assert(opaq->outcnt() == 1, "");
      _igvn.replace_input_of(opaq, 1, new_limit);
    }

    // Adjust max trip count. The trip count is intentionally rounded
    // down here (e.g. 15-> 7-> 3-> 1) because if we unwittingly over-unroll,
    // the main, unrolled, part of the loop will never execute as it is protected
    // by the min-trip test.  See bug 4834191 for a case where we over-unrolled
    // and later determined that part of the unrolled loop was dead.
    loop_head->set_trip_count(old_trip_count / 2);

    // Double the count of original iterations in the unrolled loop body.
    loop_head->double_unrolled_count();

  } else { // LoopLimitCheck

    // Adjust max trip count. The trip count is intentionally rounded
    // down here (e.g. 15-> 7-> 3-> 1) because if we unwittingly over-unroll,
    // the main, unrolled, part of the loop will never execute as it is protected
    // by the min-trip test.  See bug 4834191 for a case where we over-unrolled
    // and later determined that part of the unrolled loop was dead.
    loop_head->set_trip_count(loop_head->trip_count() / 2);

    // Double the count of original iterations in the unrolled loop body.
    loop_head->double_unrolled_count();

    // -----------
    // Step 2: Cut back the trip counter for an unroll amount of 2.
    // Loop will normally trip (limit - init)/stride_con.  Since it's a
    // CountedLoop this is exact (stride divides limit-init exactly).
    // We are going to double the loop body, so we want to knock off any
    // odd iteration: (trip_cnt & ~1).  Then back compute a new limit.
    Node *span = new SubINode( limit, init );
    register_new_node( span, ctrl );
    Node *trip = new DivINode( 0, span, stride );
    register_new_node( trip, ctrl );
    Node *mtwo = _igvn.intcon(-2);
    set_ctrl(mtwo, C->root());
    Node *rond = new AndINode( trip, mtwo );
    register_new_node( rond, ctrl );
    Node *spn2 = new MulINode( rond, stride );
    register_new_node( spn2, ctrl );
    new_limit = new AddINode( spn2, init );
    register_new_node( new_limit, ctrl );

    // Hammer in the new limit
    Node *ctrl2 = loop_end->in(0);
    Node *cmp2 = new CmpINode( loop_head->incr(), new_limit );
    register_new_node( cmp2, ctrl2 );
    Node *bol2 = new BoolNode( cmp2, loop_end->test_trip() );
    register_new_node( bol2, ctrl2 );
    _igvn.replace_input_of(loop_end, CountedLoopEndNode::TestValue, bol2);

    // Step 3: Find the min-trip test guaranteed before a 'main' loop.
    // Make it a 1-trip test (means at least 2 trips).
    if( adjust_min_trip ) {
      assert( new_limit != NULL, "" );
      // Guard test uses an 'opaque' node which is not shared.  Hence I
      // can edit it's inputs directly.  Hammer in the new limit for the
      // minimum-trip guard.
      assert( opaq->outcnt() == 1, "" );
      _igvn.hash_delete(opaq);
      opaq->set_req(1, new_limit);
    }
  } // LoopLimitCheck

  // ---------
  // Step 4: Clone the loop body.  Move it inside the loop.  This loop body
  // represents the odd iterations; since the loop trips an even number of
  // times its backedge is never taken.  Kill the backedge.
  uint dd = dom_depth(loop_head);
  clone_loop( loop, old_new, dd );

  // Make backedges of the clone equal to backedges of the original.
  // Make the fall-in from the original come from the fall-out of the clone.
  for (DUIterator_Fast jmax, j = loop_head->fast_outs(jmax); j < jmax; j++) {
    Node* phi = loop_head->fast_out(j);
    if( phi->is_Phi() && phi->in(0) == loop_head && phi->outcnt() > 0 ) {
      Node *newphi = old_new[phi->_idx];
      _igvn.hash_delete( phi );
      _igvn.hash_delete( newphi );

      phi   ->set_req(LoopNode::   EntryControl, newphi->in(LoopNode::LoopBackControl));
      newphi->set_req(LoopNode::LoopBackControl, phi   ->in(LoopNode::LoopBackControl));
      phi   ->set_req(LoopNode::LoopBackControl, C->top());
    }
  }
  Node *clone_head = old_new[loop_head->_idx];
  _igvn.hash_delete( clone_head );
  loop_head ->set_req(LoopNode::   EntryControl, clone_head->in(LoopNode::LoopBackControl));
  clone_head->set_req(LoopNode::LoopBackControl, loop_head ->in(LoopNode::LoopBackControl));
  loop_head ->set_req(LoopNode::LoopBackControl, C->top());
  loop->_head = clone_head;     // New loop header

  set_idom(loop_head,  loop_head ->in(LoopNode::EntryControl), dd);
  set_idom(clone_head, clone_head->in(LoopNode::EntryControl), dd);

  // Kill the clone's backedge
  Node *newcle = old_new[loop_end->_idx];
  _igvn.hash_delete( newcle );
  Node *one = _igvn.intcon(1);
  set_ctrl(one, C->root());
  newcle->set_req(1, one);
  // Force clone into same loop body
  uint max = loop->_body.size();
  for( uint k = 0; k < max; k++ ) {
    Node *old = loop->_body.at(k);
    Node *nnn = old_new[old->_idx];
    loop->_body.push(nnn);
    if (!has_ctrl(old))
      set_loop(nnn, loop);
  }

  loop->record_for_igvn();

#ifndef PRODUCT
  if (C->do_vector_loop() && (PrintOpto && VerifyLoopOptimizations || TraceLoopOpts)) {
    tty->print("\nnew loop after unroll\n");       loop->dump_head();
    for (uint i = 0; i < loop->_body.size(); i++) {
      loop->_body.at(i)->dump();
    }
    if(C->clone_map().is_debug()) {
      tty->print("\nCloneMap\n");
      Dict* dict = C->clone_map().dict();
      DictI i(dict);
      tty->print_cr("Dict@%p[%d] = ", dict, dict->Size());
      for (int ii = 0; i.test(); ++i, ++ii) {
        NodeCloneInfo cl((uint64_t)dict->operator[]((void*)i._key));
        tty->print("%d->%d:%d,", (int)(intptr_t)i._key, cl.idx(), cl.gen());
        if (ii % 10 == 9) {
          tty->print_cr(" ");
        }
      }
      tty->print_cr(" ");
    }
  }
#endif

}

//------------------------------do_maximally_unroll----------------------------

void PhaseIdealLoop::do_maximally_unroll( IdealLoopTree *loop, Node_List &old_new ) {
  CountedLoopNode *cl = loop->_head->as_CountedLoop();
  assert(cl->has_exact_trip_count(), "trip count is not exact");
  assert(cl->trip_count() > 0, "");
#ifndef PRODUCT
  if (TraceLoopOpts) {
    tty->print("MaxUnroll  %d ", cl->trip_count());
    loop->dump_head();
  }
#endif

  // If loop is tripping an odd number of times, peel odd iteration
  if ((cl->trip_count() & 1) == 1) {
    do_peeling(loop, old_new);
  }

  // Now its tripping an even number of times remaining.  Double loop body.
  // Do not adjust pre-guards; they are not needed and do not exist.
  if (cl->trip_count() > 0) {
    assert((cl->trip_count() & 1) == 0, "missed peeling");
    do_unroll(loop, old_new, false);
  }
}

void PhaseIdealLoop::mark_reductions(IdealLoopTree *loop) {
  if (SuperWordReductions == false) return;

  CountedLoopNode* loop_head = loop->_head->as_CountedLoop();
  if (loop_head->unrolled_count() > 1) {
    return;
  }

  Node* trip_phi = loop_head->phi();
  for (DUIterator_Fast imax, i = loop_head->fast_outs(imax); i < imax; i++) {
    Node* phi = loop_head->fast_out(i);
    if (phi->is_Phi() && phi->outcnt() > 0 && phi != trip_phi) {
      // For definitions which are loop inclusive and not tripcounts.
      Node* def_node = phi->in(LoopNode::LoopBackControl);

      if (def_node != NULL) {
        Node* n_ctrl = get_ctrl(def_node);
        if (n_ctrl != NULL && loop->is_member(get_loop(n_ctrl))) {
          // Now test it to see if it fits the standard pattern for a reduction operator.
          int opc = def_node->Opcode();
          if (opc != ReductionNode::opcode(opc, def_node->bottom_type()->basic_type())) {
            if (!def_node->is_reduction()) { // Not marked yet
              // To be a reduction, the arithmetic node must have the phi as input and provide a def to it
              bool ok = false;
              for (unsigned j = 1; j < def_node->req(); j++) {
                Node* in = def_node->in(j);
                if (in == phi) {
                  ok = true;
                  break;
                }
              }

              // do nothing if we did not match the initial criteria
              if (ok == false) {
                continue;
              }

              // The result of the reduction must not be used in the loop
              for (DUIterator_Fast imax, i = def_node->fast_outs(imax); i < imax && ok; i++) {
                Node* u = def_node->fast_out(i);
                if (has_ctrl(u) && !loop->is_member(get_loop(get_ctrl(u)))) {
                  continue;
                }
                if (u == phi) {
                  continue;
                }
                ok = false;
              }

              // iff the uses conform
              if (ok) {
                def_node->add_flag(Node::Flag_is_reduction);
                loop_head->mark_has_reductions();
              }
            }
          }
        }
      }
    }
  }
}

//------------------------------dominates_backedge---------------------------------
// Returns true if ctrl is executed on every complete iteration
bool IdealLoopTree::dominates_backedge(Node* ctrl) {
  assert(ctrl->is_CFG(), "must be control");
  Node* backedge = _head->as_Loop()->in(LoopNode::LoopBackControl);
  return _phase->dom_lca_internal(ctrl, backedge) == ctrl;
}

//------------------------------adjust_limit-----------------------------------
// Helper function for add_constraint().
Node* PhaseIdealLoop::adjust_limit(int stride_con, Node * scale, Node *offset, Node *rc_limit, Node *loop_limit, Node *pre_ctrl) {
  // Compute "I :: (limit-offset)/scale"
  Node *con = new SubINode(rc_limit, offset);
  register_new_node(con, pre_ctrl);
  Node *X = new DivINode(0, con, scale);
  register_new_node(X, pre_ctrl);

  // Adjust loop limit
  loop_limit = (stride_con > 0)
               ? (Node*)(new MinINode(loop_limit, X))
               : (Node*)(new MaxINode(loop_limit, X));
  register_new_node(loop_limit, pre_ctrl);
  return loop_limit;
}

//------------------------------add_constraint---------------------------------
// Constrain the main loop iterations so the conditions:
//    low_limit <= scale_con * I + offset  <  upper_limit
// always holds true.  That is, either increase the number of iterations in
// the pre-loop or the post-loop until the condition holds true in the main
// loop.  Stride, scale, offset and limit are all loop invariant.  Further,
// stride and scale are constants (offset and limit often are).
void PhaseIdealLoop::add_constraint( int stride_con, int scale_con, Node *offset, Node *low_limit, Node *upper_limit, Node *pre_ctrl, Node **pre_limit, Node **main_limit ) {
  // For positive stride, the pre-loop limit always uses a MAX function
  // and the main loop a MIN function.  For negative stride these are
  // reversed.

  // Also for positive stride*scale the affine function is increasing, so the
  // pre-loop must check for underflow and the post-loop for overflow.
  // Negative stride*scale reverses this; pre-loop checks for overflow and
  // post-loop for underflow.

  Node *scale = _igvn.intcon(scale_con);
  set_ctrl(scale, C->root());

  if ((stride_con^scale_con) >= 0) { // Use XOR to avoid overflow
    // The overflow limit: scale*I+offset < upper_limit
    // For main-loop compute
    //   ( if (scale > 0) /* and stride > 0 */
    //       I < (upper_limit-offset)/scale
    //     else /* scale < 0 and stride < 0 */
    //       I > (upper_limit-offset)/scale
    //   )
    //
    // (upper_limit-offset) may overflow or underflow.
    // But it is fine since main loop will either have
    // less iterations or will be skipped in such case.
    *main_limit = adjust_limit(stride_con, scale, offset, upper_limit, *main_limit, pre_ctrl);

    // The underflow limit: low_limit <= scale*I+offset.
    // For pre-loop compute
    //   NOT(scale*I+offset >= low_limit)
    //   scale*I+offset < low_limit
    //   ( if (scale > 0) /* and stride > 0 */
    //       I < (low_limit-offset)/scale
    //     else /* scale < 0 and stride < 0 */
    //       I > (low_limit-offset)/scale
    //   )

    if (low_limit->get_int() == -max_jint) {
      if (!RangeLimitCheck) return;
      // We need this guard when scale*pre_limit+offset >= limit
      // due to underflow. So we need execute pre-loop until
      // scale*I+offset >= min_int. But (min_int-offset) will
      // underflow when offset > 0 and X will be > original_limit
      // when stride > 0. To avoid it we replace positive offset with 0.
      //
      // Also (min_int+1 == -max_int) is used instead of min_int here
      // to avoid problem with scale == -1 (min_int/(-1) == min_int).
      Node* shift = _igvn.intcon(31);
      set_ctrl(shift, C->root());
      Node* sign = new RShiftINode(offset, shift);
      register_new_node(sign, pre_ctrl);
      offset = new AndINode(offset, sign);
      register_new_node(offset, pre_ctrl);
    } else {
      assert(low_limit->get_int() == 0, "wrong low limit for range check");
      // The only problem we have here when offset == min_int
      // since (0-min_int) == min_int. It may be fine for stride > 0
      // but for stride < 0 X will be < original_limit. To avoid it
      // max(pre_limit, original_limit) is used in do_range_check().
    }
    // Pass (-stride) to indicate pre_loop_cond = NOT(main_loop_cond);
    *pre_limit = adjust_limit((-stride_con), scale, offset, low_limit, *pre_limit, pre_ctrl);

  } else { // stride_con*scale_con < 0
    // For negative stride*scale pre-loop checks for overflow and
    // post-loop for underflow.
    //
    // The overflow limit: scale*I+offset < upper_limit
    // For pre-loop compute
    //   NOT(scale*I+offset < upper_limit)
    //   scale*I+offset >= upper_limit
    //   scale*I+offset+1 > upper_limit
    //   ( if (scale < 0) /* and stride > 0 */
    //       I < (upper_limit-(offset+1))/scale
    //     else /* scale > 0 and stride < 0 */
    //       I > (upper_limit-(offset+1))/scale
    //   )
    //
    // (upper_limit-offset-1) may underflow or overflow.
    // To avoid it min(pre_limit, original_limit) is used
    // in do_range_check() for stride > 0 and max() for < 0.
    Node *one  = _igvn.intcon(1);
    set_ctrl(one, C->root());

    Node *plus_one = new AddINode(offset, one);
    register_new_node( plus_one, pre_ctrl );
    // Pass (-stride) to indicate pre_loop_cond = NOT(main_loop_cond);
    *pre_limit = adjust_limit((-stride_con), scale, plus_one, upper_limit, *pre_limit, pre_ctrl);

    if (low_limit->get_int() == -max_jint) {
      if (!RangeLimitCheck) return;
      // We need this guard when scale*main_limit+offset >= limit
      // due to underflow. So we need execute main-loop while
      // scale*I+offset+1 > min_int. But (min_int-offset-1) will
      // underflow when (offset+1) > 0 and X will be < main_limit
      // when scale < 0 (and stride > 0). To avoid it we replace
      // positive (offset+1) with 0.
      //
      // Also (min_int+1 == -max_int) is used instead of min_int here
      // to avoid problem with scale == -1 (min_int/(-1) == min_int).
      Node* shift = _igvn.intcon(31);
      set_ctrl(shift, C->root());
      Node* sign = new RShiftINode(plus_one, shift);
      register_new_node(sign, pre_ctrl);
      plus_one = new AndINode(plus_one, sign);
      register_new_node(plus_one, pre_ctrl);
    } else {
      assert(low_limit->get_int() == 0, "wrong low limit for range check");
      // The only problem we have here when offset == max_int
      // since (max_int+1) == min_int and (0-min_int) == min_int.
      // But it is fine since main loop will either have
      // less iterations or will be skipped in such case.
    }
    // The underflow limit: low_limit <= scale*I+offset.
    // For main-loop compute
    //   scale*I+offset+1 > low_limit
    //   ( if (scale < 0) /* and stride > 0 */
    //       I < (low_limit-(offset+1))/scale
    //     else /* scale > 0 and stride < 0 */
    //       I > (low_limit-(offset+1))/scale
    //   )

    *main_limit = adjust_limit(stride_con, scale, plus_one, low_limit, *main_limit, pre_ctrl);
  }
}


//------------------------------is_scaled_iv---------------------------------
// Return true if exp is a constant times an induction var
bool PhaseIdealLoop::is_scaled_iv(Node* exp, Node* iv, int* p_scale) {
  if (exp == iv) {
    if (p_scale != NULL) {
      *p_scale = 1;
    }
    return true;
  }
  int opc = exp->Opcode();
  if (opc == Op_MulI) {
    if (exp->in(1) == iv && exp->in(2)->is_Con()) {
      if (p_scale != NULL) {
        *p_scale = exp->in(2)->get_int();
      }
      return true;
    }
    if (exp->in(2) == iv && exp->in(1)->is_Con()) {
      if (p_scale != NULL) {
        *p_scale = exp->in(1)->get_int();
      }
      return true;
    }
  } else if (opc == Op_LShiftI) {
    if (exp->in(1) == iv && exp->in(2)->is_Con()) {
      if (p_scale != NULL) {
        *p_scale = 1 << exp->in(2)->get_int();
      }
      return true;
    }
  }
  return false;
}

//-----------------------------is_scaled_iv_plus_offset------------------------------
// Return true if exp is a simple induction variable expression: k1*iv + (invar + k2)
bool PhaseIdealLoop::is_scaled_iv_plus_offset(Node* exp, Node* iv, int* p_scale, Node** p_offset, int depth) {
  if (is_scaled_iv(exp, iv, p_scale)) {
    if (p_offset != NULL) {
      Node *zero = _igvn.intcon(0);
      set_ctrl(zero, C->root());
      *p_offset = zero;
    }
    return true;
  }
  int opc = exp->Opcode();
  if (opc == Op_AddI) {
    if (is_scaled_iv(exp->in(1), iv, p_scale)) {
      if (p_offset != NULL) {
        *p_offset = exp->in(2);
      }
      return true;
    }
    if (is_scaled_iv(exp->in(2), iv, p_scale)) {
      if (p_offset != NULL) {
        *p_offset = exp->in(1);
      }
      return true;
    }
    if (exp->in(2)->is_Con()) {
      Node* offset2 = NULL;
      if (depth < 2 &&
          is_scaled_iv_plus_offset(exp->in(1), iv, p_scale,
                                   p_offset != NULL ? &offset2 : NULL, depth+1)) {
        if (p_offset != NULL) {
          Node *ctrl_off2 = get_ctrl(offset2);
          Node* offset = new AddINode(offset2, exp->in(2));
          register_new_node(offset, ctrl_off2);
          *p_offset = offset;
        }
        return true;
      }
    }
  } else if (opc == Op_SubI) {
    if (is_scaled_iv(exp->in(1), iv, p_scale)) {
      if (p_offset != NULL) {
        Node *zero = _igvn.intcon(0);
        set_ctrl(zero, C->root());
        Node *ctrl_off = get_ctrl(exp->in(2));
        Node* offset = new SubINode(zero, exp->in(2));
        register_new_node(offset, ctrl_off);
        *p_offset = offset;
      }
      return true;
    }
    if (is_scaled_iv(exp->in(2), iv, p_scale)) {
      if (p_offset != NULL) {
        *p_scale *= -1;
        *p_offset = exp->in(1);
      }
      return true;
    }
  }
  return false;
}

//------------------------------do_range_check---------------------------------
// Eliminate range-checks and other trip-counter vs loop-invariant tests.
void PhaseIdealLoop::do_range_check( IdealLoopTree *loop, Node_List &old_new ) {
#ifndef PRODUCT
  if (PrintOpto && VerifyLoopOptimizations) {
    tty->print("Range Check Elimination ");
    loop->dump_head();
  } else if (TraceLoopOpts) {
    tty->print("RangeCheck   ");
    loop->dump_head();
  }
#endif
  assert(RangeCheckElimination, "");
  CountedLoopNode *cl = loop->_head->as_CountedLoop();

  // protect against stride not being a constant
  if (!cl->stride_is_con())
    return;

  // Find the trip counter; we are iteration splitting based on it
  Node *trip_counter = cl->phi();
  // Find the main loop limit; we will trim it's iterations
  // to not ever trip end tests
  Node *main_limit = cl->limit();

  // Check graph shape. Cannot optimize a loop if zero-trip
  // Opaque1 node is optimized away and then another round
  // of loop opts attempted.
  if (!is_canonical_main_loop_entry(cl)) {
    return;
  }

  // Need to find the main-loop zero-trip guard
  Node *ctrl  = cl->in(LoopNode::EntryControl);
  Node *iffm = ctrl->in(0);
  Node *opqzm = iffm->in(1)->in(1)->in(2);
  assert(opqzm->in(1) == main_limit, "do not understand situation");

  // Find the pre-loop limit; we will expand its iterations to
  // not ever trip low tests.
  Node *p_f = iffm->in(0);
  // pre loop may have been optimized out
  if (p_f->Opcode() != Op_IfFalse) {
    return;
  }
  CountedLoopEndNode *pre_end = p_f->in(0)->as_CountedLoopEnd();
  assert(pre_end->loopnode()->is_pre_loop(), "");
  Node *pre_opaq1 = pre_end->limit();
  // Occasionally it's possible for a pre-loop Opaque1 node to be
  // optimized away and then another round of loop opts attempted.
  // We can not optimize this particular loop in that case.
  if (pre_opaq1->Opcode() != Op_Opaque1)
    return;
  Opaque1Node *pre_opaq = (Opaque1Node*)pre_opaq1;
  Node *pre_limit = pre_opaq->in(1);

  // Where do we put new limit calculations
  Node *pre_ctrl = pre_end->loopnode()->in(LoopNode::EntryControl);

  // Ensure the original loop limit is available from the
  // pre-loop Opaque1 node.
  Node *orig_limit = pre_opaq->original_loop_limit();
  if (orig_limit == NULL || _igvn.type(orig_limit) == Type::TOP)
    return;

  // Must know if its a count-up or count-down loop

  int stride_con = cl->stride_con();
  Node *zero = _igvn.intcon(0);
  Node *one  = _igvn.intcon(1);
  // Use symmetrical int range [-max_jint,max_jint]
  Node *mini = _igvn.intcon(-max_jint);
  set_ctrl(zero, C->root());
  set_ctrl(one,  C->root());
  set_ctrl(mini, C->root());

  // Range checks that do not dominate the loop backedge (ie.
  // conditionally executed) can lengthen the pre loop limit beyond
  // the original loop limit. To prevent this, the pre limit is
  // (for stride > 0) MINed with the original loop limit (MAXed
  // stride < 0) when some range_check (rc) is conditionally
  // executed.
  bool conditional_rc = false;

  // Check loop body for tests of trip-counter plus loop-invariant vs
  // loop-invariant.
  for( uint i = 0; i < loop->_body.size(); i++ ) {
    Node *iff = loop->_body[i];
    if (iff->Opcode() == Op_If ||
        iff->Opcode() == Op_RangeCheck) { // Test?
      // Test is an IfNode, has 2 projections.  If BOTH are in the loop
      // we need loop unswitching instead of iteration splitting.
      Node *exit = loop->is_loop_exit(iff);
      if( !exit ) continue;
      int flip = (exit->Opcode() == Op_IfTrue) ? 1 : 0;

      // Get boolean condition to test
      Node *i1 = iff->in(1);
      if( !i1->is_Bool() ) continue;
      BoolNode *bol = i1->as_Bool();
      BoolTest b_test = bol->_test;
      // Flip sense of test if exit condition is flipped
      if( flip )
        b_test = b_test.negate();

      // Get compare
      Node *cmp = bol->in(1);

      // Look for trip_counter + offset vs limit
      Node *rc_exp = cmp->in(1);
      Node *limit  = cmp->in(2);
      jint scale_con= 1;        // Assume trip counter not scaled

      Node *limit_c = get_ctrl(limit);
      if( loop->is_member(get_loop(limit_c) ) ) {
        // Compare might have operands swapped; commute them
        b_test = b_test.commute();
        rc_exp = cmp->in(2);
        limit  = cmp->in(1);
        limit_c = get_ctrl(limit);
        if( loop->is_member(get_loop(limit_c) ) )
          continue;             // Both inputs are loop varying; cannot RCE
      }
      // Here we know 'limit' is loop invariant

      // 'limit' maybe pinned below the zero trip test (probably from a
      // previous round of rce), in which case, it can't be used in the
      // zero trip test expression which must occur before the zero test's if.
      if( limit_c == ctrl ) {
        continue;  // Don't rce this check but continue looking for other candidates.
      }

      // Check for scaled induction variable plus an offset
      Node *offset = NULL;

      if (!is_scaled_iv_plus_offset(rc_exp, trip_counter, &scale_con, &offset)) {
        continue;
      }

      Node *offset_c = get_ctrl(offset);
      if( loop->is_member( get_loop(offset_c) ) )
        continue;               // Offset is not really loop invariant
      // Here we know 'offset' is loop invariant.

      // As above for the 'limit', the 'offset' maybe pinned below the
      // zero trip test.
      if( offset_c == ctrl ) {
        continue; // Don't rce this check but continue looking for other candidates.
      }
#ifdef ASSERT
      if (TraceRangeLimitCheck) {
        tty->print_cr("RC bool node%s", flip ? " flipped:" : ":");
        bol->dump(2);
      }
#endif
      // At this point we have the expression as:
      //   scale_con * trip_counter + offset :: limit
      // where scale_con, offset and limit are loop invariant.  Trip_counter
      // monotonically increases by stride_con, a constant.  Both (or either)
      // stride_con and scale_con can be negative which will flip about the
      // sense of the test.

      // Adjust pre and main loop limits to guard the correct iteration set
      if( cmp->Opcode() == Op_CmpU ) {// Unsigned compare is really 2 tests
        if( b_test._test == BoolTest::lt ) { // Range checks always use lt
          // The underflow and overflow limits: 0 <= scale*I+offset < limit
          add_constraint( stride_con, scale_con, offset, zero, limit, pre_ctrl, &pre_limit, &main_limit );
          if (!conditional_rc) {
            // (0-offset)/scale could be outside of loop iterations range.
            conditional_rc = !loop->dominates_backedge(iff) || RangeLimitCheck;
          }
        } else {
          if (PrintOpto) {
            tty->print_cr("missed RCE opportunity");
          }
          continue;             // In release mode, ignore it
        }
      } else {                  // Otherwise work on normal compares
        switch( b_test._test ) {
        case BoolTest::gt:
          // Fall into GE case
        case BoolTest::ge:
          // Convert (I*scale+offset) >= Limit to (I*(-scale)+(-offset)) <= -Limit
          scale_con = -scale_con;
          offset = new SubINode( zero, offset );
          register_new_node( offset, pre_ctrl );
          limit  = new SubINode( zero, limit  );
          register_new_node( limit, pre_ctrl );
          // Fall into LE case
        case BoolTest::le:
          if (b_test._test != BoolTest::gt) {
            // Convert X <= Y to X < Y+1
            limit = new AddINode( limit, one );
            register_new_node( limit, pre_ctrl );
          }
          // Fall into LT case
        case BoolTest::lt:
          // The underflow and overflow limits: MIN_INT <= scale*I+offset < limit
          // Note: (MIN_INT+1 == -MAX_INT) is used instead of MIN_INT here
          // to avoid problem with scale == -1: MIN_INT/(-1) == MIN_INT.
          add_constraint( stride_con, scale_con, offset, mini, limit, pre_ctrl, &pre_limit, &main_limit );
          if (!conditional_rc) {
            // ((MIN_INT+1)-offset)/scale could be outside of loop iterations range.
            // Note: negative offset is replaced with 0 but (MIN_INT+1)/scale could
            // still be outside of loop range.
            conditional_rc = !loop->dominates_backedge(iff) || RangeLimitCheck;
          }
          break;
        default:
          if (PrintOpto) {
            tty->print_cr("missed RCE opportunity");
          }
          continue;             // Unhandled case
        }
      }

      // Kill the eliminated test
      C->set_major_progress();
      Node *kill_con = _igvn.intcon( 1-flip );
      set_ctrl(kill_con, C->root());
      _igvn.replace_input_of(iff, 1, kill_con);
      // Find surviving projection
      assert(iff->is_If(), "");
      ProjNode* dp = ((IfNode*)iff)->proj_out(1-flip);
      // Find loads off the surviving projection; remove their control edge
      for (DUIterator_Fast imax, i = dp->fast_outs(imax); i < imax; i++) {
        Node* cd = dp->fast_out(i); // Control-dependent node
        if (cd->is_Load() && cd->depends_only_on_test()) {   // Loads can now float around in the loop
          // Allow the load to float around in the loop, or before it
          // but NOT before the pre-loop.
          _igvn.replace_input_of(cd, 0, ctrl); // ctrl, not NULL
          --i;
          --imax;
        }
      }

    } // End of is IF

  }

  // Update loop limits
  if (conditional_rc) {
    pre_limit = (stride_con > 0) ? (Node*)new MinINode(pre_limit, orig_limit)
                                 : (Node*)new MaxINode(pre_limit, orig_limit);
    register_new_node(pre_limit, pre_ctrl);
  }
  _igvn.replace_input_of(pre_opaq, 1, pre_limit);

  // Note:: we are making the main loop limit no longer precise;
  // need to round up based on stride.
  cl->set_nonexact_trip_count();
  if (!LoopLimitCheck && stride_con != 1 && stride_con != -1) { // Cutout for common case
    // "Standard" round-up logic:  ([main_limit-init+(y-1)]/y)*y+init
    // Hopefully, compiler will optimize for powers of 2.
    Node *ctrl = get_ctrl(main_limit);
    Node *stride = cl->stride();
    Node *init = cl->init_trip()->uncast();
    Node *span = new SubINode(main_limit,init);
    register_new_node(span,ctrl);
    Node *rndup = _igvn.intcon(stride_con + ((stride_con>0)?-1:1));
    Node *add = new AddINode(span,rndup);
    register_new_node(add,ctrl);
    Node *div = new DivINode(0,add,stride);
    register_new_node(div,ctrl);
    Node *mul = new MulINode(div,stride);
    register_new_node(mul,ctrl);
    Node *newlim = new AddINode(mul,init);
    register_new_node(newlim,ctrl);
    main_limit = newlim;
  }

  Node *main_cle = cl->loopexit();
  Node *main_bol = main_cle->in(1);
  // Hacking loop bounds; need private copies of exit test
  if( main_bol->outcnt() > 1 ) {// BoolNode shared?
    main_bol = main_bol->clone();// Clone a private BoolNode
    register_new_node( main_bol, main_cle->in(0) );
    _igvn.replace_input_of(main_cle, 1, main_bol);
  }
  Node *main_cmp = main_bol->in(1);
  if( main_cmp->outcnt() > 1 ) { // CmpNode shared?
    main_cmp = main_cmp->clone();// Clone a private CmpNode
    register_new_node( main_cmp, main_cle->in(0) );
    _igvn.replace_input_of(main_bol, 1, main_cmp);
  }
  // Hack the now-private loop bounds
  _igvn.replace_input_of(main_cmp, 2, main_limit);
  // The OpaqueNode is unshared by design
  assert( opqzm->outcnt() == 1, "cannot hack shared node" );
  _igvn.replace_input_of(opqzm, 1, main_limit);
}

//------------------------------DCE_loop_body----------------------------------
// Remove simplistic dead code from loop body
void IdealLoopTree::DCE_loop_body() {
  for( uint i = 0; i < _body.size(); i++ )
    if( _body.at(i)->outcnt() == 0 )
      _body.map( i--, _body.pop() );
}


//------------------------------adjust_loop_exit_prob--------------------------
// Look for loop-exit tests with the 50/50 (or worse) guesses from the parsing stage.
// Replace with a 1-in-10 exit guess.
void IdealLoopTree::adjust_loop_exit_prob( PhaseIdealLoop *phase ) {
  Node *test = tail();
  while( test != _head ) {
    uint top = test->Opcode();
    if( top == Op_IfTrue || top == Op_IfFalse ) {
      int test_con = ((ProjNode*)test)->_con;
      assert(top == (uint)(test_con? Op_IfTrue: Op_IfFalse), "sanity");
      IfNode *iff = test->in(0)->as_If();
      if( iff->outcnt() == 2 ) {        // Ignore dead tests
        Node *bol = iff->in(1);
        if( bol && bol->req() > 1 && bol->in(1) &&
            ((bol->in(1)->Opcode() == Op_StorePConditional ) ||
             (bol->in(1)->Opcode() == Op_StoreIConditional ) ||
             (bol->in(1)->Opcode() == Op_StoreLConditional ) ||
             (bol->in(1)->Opcode() == Op_CompareAndExchangeI ) ||
             (bol->in(1)->Opcode() == Op_CompareAndExchangeL ) ||
             (bol->in(1)->Opcode() == Op_CompareAndExchangeP ) ||
             (bol->in(1)->Opcode() == Op_CompareAndExchangeN ) ||
             (bol->in(1)->Opcode() == Op_WeakCompareAndSwapI ) ||
             (bol->in(1)->Opcode() == Op_WeakCompareAndSwapL ) ||
             (bol->in(1)->Opcode() == Op_WeakCompareAndSwapP ) ||
             (bol->in(1)->Opcode() == Op_WeakCompareAndSwapN ) ||
             (bol->in(1)->Opcode() == Op_CompareAndSwapI ) ||
             (bol->in(1)->Opcode() == Op_CompareAndSwapL ) ||
             (bol->in(1)->Opcode() == Op_CompareAndSwapP ) ||
             (bol->in(1)->Opcode() == Op_CompareAndSwapN )))
          return;               // Allocation loops RARELY take backedge
        // Find the OTHER exit path from the IF
        Node* ex = iff->proj_out(1-test_con);
        float p = iff->_prob;
        if( !phase->is_member( this, ex ) && iff->_fcnt == COUNT_UNKNOWN ) {
          if( top == Op_IfTrue ) {
            if( p < (PROB_FAIR + PROB_UNLIKELY_MAG(3))) {
              iff->_prob = PROB_STATIC_FREQUENT;
            }
          } else {
            if( p > (PROB_FAIR - PROB_UNLIKELY_MAG(3))) {
              iff->_prob = PROB_STATIC_INFREQUENT;
            }
          }
        }
      }
    }
    test = phase->idom(test);
  }
}

#ifdef ASSERT
static CountedLoopNode* locate_pre_from_main(CountedLoopNode *cl) {
  Node *ctrl  = cl->in(LoopNode::EntryControl);
  assert(ctrl->Opcode() == Op_IfTrue || ctrl->Opcode() == Op_IfFalse, "");
  Node *iffm = ctrl->in(0);
  assert(iffm->Opcode() == Op_If, "");
  Node *p_f = iffm->in(0);
  assert(p_f->Opcode() == Op_IfFalse, "");
  CountedLoopEndNode *pre_end = p_f->in(0)->as_CountedLoopEnd();
  assert(pre_end->loopnode()->is_pre_loop(), "");
  return pre_end->loopnode();
}
#endif

// Remove the main and post loops and make the pre loop execute all
// iterations. Useful when the pre loop is found empty.
void IdealLoopTree::remove_main_post_loops(CountedLoopNode *cl, PhaseIdealLoop *phase) {
  CountedLoopEndNode* pre_end = cl->loopexit();
  Node* pre_cmp = pre_end->cmp_node();
  if (pre_cmp->in(2)->Opcode() != Op_Opaque1) {
    // Only safe to remove the main loop if the compiler optimized it
    // out based on an unknown number of iterations
    return;
  }

  // Can we find the main loop?
  if (_next == NULL) {
    return;
  }

  Node* next_head = _next->_head;
  if (!next_head->is_CountedLoop()) {
    return;
  }

  CountedLoopNode* main_head = next_head->as_CountedLoop();
  if (!main_head->is_main_loop()) {
    return;
  }

  assert(locate_pre_from_main(main_head) == cl, "bad main loop");
  Node* main_iff = main_head->in(LoopNode::EntryControl)->in(0);

  // Remove the Opaque1Node of the pre loop and make it execute all iterations
  phase->_igvn.replace_input_of(pre_cmp, 2, pre_cmp->in(2)->in(2));
  // Remove the Opaque1Node of the main loop so it can be optimized out
  Node* main_cmp = main_iff->in(1)->in(1);
  assert(main_cmp->in(2)->Opcode() == Op_Opaque1, "main loop has no opaque node?");
  phase->_igvn.replace_input_of(main_cmp, 2, main_cmp->in(2)->in(1));
}

//------------------------------policy_do_remove_empty_loop--------------------
// Micro-benchmark spamming.  Policy is to always remove empty loops.
// The 'DO' part is to replace the trip counter with the value it will
// have on the last iteration.  This will break the loop.
bool IdealLoopTree::policy_do_remove_empty_loop( PhaseIdealLoop *phase ) {
  // Minimum size must be empty loop
  if (_body.size() > EMPTY_LOOP_SIZE)
    return false;

  if (!_head->is_CountedLoop())
    return false;     // Dead loop
  CountedLoopNode *cl = _head->as_CountedLoop();
  if (!cl->is_valid_counted_loop())
    return false; // Malformed loop
  if (!phase->is_member(this, phase->get_ctrl(cl->loopexit()->in(CountedLoopEndNode::TestValue))))
    return false;             // Infinite loop

  if (cl->is_pre_loop()) {
    // If the loop we are removing is a pre-loop then the main and
    // post loop can be removed as well
    remove_main_post_loops(cl, phase);
  }

#ifdef ASSERT
  // Ensure only one phi which is the iv.
  Node* iv = NULL;
  for (DUIterator_Fast imax, i = cl->fast_outs(imax); i < imax; i++) {
    Node* n = cl->fast_out(i);
    if (n->Opcode() == Op_Phi) {
      assert(iv == NULL, "Too many phis" );
      iv = n;
    }
  }
  assert(iv == cl->phi(), "Wrong phi" );
#endif

  // main and post loops have explicitly created zero trip guard
  bool needs_guard = !cl->is_main_loop() && !cl->is_post_loop();
  if (needs_guard) {
    // Skip guard if values not overlap.
    const TypeInt* init_t = phase->_igvn.type(cl->init_trip())->is_int();
    const TypeInt* limit_t = phase->_igvn.type(cl->limit())->is_int();
    int  stride_con = cl->stride_con();
    if (stride_con > 0) {
      needs_guard = (init_t->_hi >= limit_t->_lo);
    } else {
      needs_guard = (init_t->_lo <= limit_t->_hi);
    }
  }
  if (needs_guard) {
    // Check for an obvious zero trip guard.
    Node* inctrl = PhaseIdealLoop::skip_loop_predicates(cl->in(LoopNode::EntryControl));
    if (inctrl->Opcode() == Op_IfTrue || inctrl->Opcode() == Op_IfFalse) {
      bool maybe_swapped = (inctrl->Opcode() == Op_IfFalse);
      // The test should look like just the backedge of a CountedLoop
      Node* iff = inctrl->in(0);
      if (iff->is_If()) {
        Node* bol = iff->in(1);
        if (bol->is_Bool()) {
          BoolTest test = bol->as_Bool()->_test;
          if (maybe_swapped) {
            test._test = test.commute();
            test._test = test.negate();
          }
          if (test._test == cl->loopexit()->test_trip()) {
            Node* cmp = bol->in(1);
            int init_idx = maybe_swapped ? 2 : 1;
            int limit_idx = maybe_swapped ? 1 : 2;
            if (cmp->is_Cmp() && cmp->in(init_idx) == cl->init_trip() && cmp->in(limit_idx) == cl->limit()) {
              needs_guard = false;
            }
          }
        }
      }
    }
  }

#ifndef PRODUCT
  if (PrintOpto) {
    tty->print("Removing empty loop with%s zero trip guard", needs_guard ? "out" : "");
    this->dump_head();
  } else if (TraceLoopOpts) {
    tty->print("Empty with%s zero trip guard   ", needs_guard ? "out" : "");
    this->dump_head();
  }
#endif

  if (needs_guard) {
    // Peel the loop to ensure there's a zero trip guard
    Node_List old_new;
    phase->do_peeling(this, old_new);
  }

  // Replace the phi at loop head with the final value of the last
  // iteration.  Then the CountedLoopEnd will collapse (backedge never
  // taken) and all loop-invariant uses of the exit values will be correct.
  Node *phi = cl->phi();
  Node *exact_limit = phase->exact_limit(this);
  if (exact_limit != cl->limit()) {
    // We also need to replace the original limit to collapse loop exit.
    Node* cmp = cl->loopexit()->cmp_node();
    assert(cl->limit() == cmp->in(2), "sanity");
    phase->_igvn._worklist.push(cmp->in(2)); // put limit on worklist
    phase->_igvn.replace_input_of(cmp, 2, exact_limit); // put cmp on worklist
  }
  // Note: the final value after increment should not overflow since
  // counted loop has limit check predicate.
  Node *final = new SubINode( exact_limit, cl->stride() );
  phase->register_new_node(final,cl->in(LoopNode::EntryControl));
  phase->_igvn.replace_node(phi,final);
  phase->C->set_major_progress();
  return true;
}

//------------------------------policy_do_one_iteration_loop-------------------
// Convert one iteration loop into normal code.
bool IdealLoopTree::policy_do_one_iteration_loop( PhaseIdealLoop *phase ) {
  if (!_head->as_Loop()->is_valid_counted_loop())
    return false; // Only for counted loop

  CountedLoopNode *cl = _head->as_CountedLoop();
  if (!cl->has_exact_trip_count() || cl->trip_count() != 1) {
    return false;
  }

#ifndef PRODUCT
  if(TraceLoopOpts) {
    tty->print("OneIteration ");
    this->dump_head();
  }
#endif

  Node *init_n = cl->init_trip();
#ifdef ASSERT
  // Loop boundaries should be constant since trip count is exact.
  assert(init_n->get_int() + cl->stride_con() >= cl->limit()->get_int(), "should be one iteration");
#endif
  // Replace the phi at loop head with the value of the init_trip.
  // Then the CountedLoopEnd will collapse (backedge will not be taken)
  // and all loop-invariant uses of the exit values will be correct.
  phase->_igvn.replace_node(cl->phi(), cl->init_trip());
  phase->C->set_major_progress();
  return true;
}

//=============================================================================
//------------------------------iteration_split_impl---------------------------
bool IdealLoopTree::iteration_split_impl( PhaseIdealLoop *phase, Node_List &old_new ) {
  // Compute exact loop trip count if possible.
  compute_exact_trip_count(phase);

  // Convert one iteration loop into normal code.
  if (policy_do_one_iteration_loop(phase))
    return true;

  // Check and remove empty loops (spam micro-benchmarks)
  if (policy_do_remove_empty_loop(phase))
    return true;  // Here we removed an empty loop

  bool should_peel = policy_peeling(phase); // Should we peel?

  bool should_unswitch = policy_unswitching(phase);

  // Non-counted loops may be peeled; exactly 1 iteration is peeled.
  // This removes loop-invariant tests (usually null checks).
  if (!_head->is_CountedLoop()) { // Non-counted loop
    if (PartialPeelLoop && phase->partial_peel(this, old_new)) {
      // Partial peel succeeded so terminate this round of loop opts
      return false;
    }
    if (should_peel) {            // Should we peel?
      if (PrintOpto) { tty->print_cr("should_peel"); }
      phase->do_peeling(this,old_new);
    } else if (should_unswitch) {
      phase->do_unswitching(this, old_new);
    }
    return true;
  }
  CountedLoopNode *cl = _head->as_CountedLoop();

  if (!cl->is_valid_counted_loop()) return true; // Ignore various kinds of broken loops

  // Do nothing special to pre- and post- loops
  if (cl->is_pre_loop() || cl->is_post_loop()) return true;

  // Compute loop trip count from profile data
  compute_profile_trip_cnt(phase);

  // Before attempting fancy unrolling, RCE or alignment, see if we want
  // to completely unroll this loop or do loop unswitching.
  if (cl->is_normal_loop()) {
    if (should_unswitch) {
      phase->do_unswitching(this, old_new);
      return true;
    }
    bool should_maximally_unroll =  policy_maximally_unroll(phase);
    if (should_maximally_unroll) {
      // Here we did some unrolling and peeling.  Eventually we will
      // completely unroll this loop and it will no longer be a loop.
      phase->do_maximally_unroll(this,old_new);
      return true;
    }
  }

  // Skip next optimizations if running low on nodes. Note that
  // policy_unswitching and policy_maximally_unroll have this check.
  int nodes_left = phase->C->max_node_limit() - phase->C->live_nodes();
  if ((int)(2 * _body.size()) > nodes_left) {
    return true;
  }

  // Counted loops may be peeled, may need some iterations run up
  // front for RCE, and may want to align loop refs to a cache
  // line.  Thus we clone a full loop up front whose trip count is
  // at least 1 (if peeling), but may be several more.

  // The main loop will start cache-line aligned with at least 1
  // iteration of the unrolled body (zero-trip test required) and
  // will have some range checks removed.

  // A post-loop will finish any odd iterations (leftover after
  // unrolling), plus any needed for RCE purposes.

  bool should_unroll = policy_unroll(phase);

  bool should_rce = policy_range_check(phase);

  bool should_align = policy_align(phase);

  // If not RCE'ing (iteration splitting) or Aligning, then we do not
  // need a pre-loop.  We may still need to peel an initial iteration but
  // we will not be needing an unknown number of pre-iterations.
  //
  // Basically, if may_rce_align reports FALSE first time through,
  // we will not be able to later do RCE or Aligning on this loop.
  bool may_rce_align = !policy_peel_only(phase) || should_rce || should_align;

  // If we have any of these conditions (RCE, alignment, unrolling) met, then
  // we switch to the pre-/main-/post-loop model.  This model also covers
  // peeling.
  if (should_rce || should_align || should_unroll) {
    if (cl->is_normal_loop())  // Convert to 'pre/main/post' loops
      phase->insert_pre_post_loops(this,old_new, !may_rce_align);

    // Adjust the pre- and main-loop limits to let the pre and post loops run
    // with full checks, but the main-loop with no checks.  Remove said
    // checks from the main body.
    if (should_rce)
      phase->do_range_check(this,old_new);

    // Double loop body for unrolling.  Adjust the minimum-trip test (will do
    // twice as many iterations as before) and the main body limit (only do
    // an even number of trips).  If we are peeling, we might enable some RCE
    // and we'd rather unroll the post-RCE'd loop SO... do not unroll if
    // peeling.
    if (should_unroll && !should_peel) {
      if (SuperWordLoopUnrollAnalysis) {
        phase->insert_vector_post_loop(this, old_new);
      }
      phase->do_unroll(this, old_new, true);
    }

    // Adjust the pre-loop limits to align the main body
    // iterations.
    if (should_align)
      Unimplemented();

  } else {                      // Else we have an unchanged counted loop
    if (should_peel)           // Might want to peel but do nothing else
      phase->do_peeling(this,old_new);
  }
  return true;
}


//=============================================================================
//------------------------------iteration_split--------------------------------
bool IdealLoopTree::iteration_split( PhaseIdealLoop *phase, Node_List &old_new ) {
  // Recursively iteration split nested loops
  if (_child && !_child->iteration_split(phase, old_new))
    return false;

  // Clean out prior deadwood
  DCE_loop_body();


  // Look for loop-exit tests with my 50/50 guesses from the Parsing stage.
  // Replace with a 1-in-10 exit guess.
  if (_parent /*not the root loop*/ &&
      !_irreducible &&
      // Also ignore the occasional dead backedge
      !tail()->is_top()) {
    adjust_loop_exit_prob(phase);
  }

  // Gate unrolling, RCE and peeling efforts.
  if (!_child &&                // If not an inner loop, do not split
      !_irreducible &&
      _allow_optimizations &&
      !tail()->is_top()) {     // Also ignore the occasional dead backedge
    if (!_has_call) {
        if (!iteration_split_impl(phase, old_new)) {
          return false;
        }
    } else if (policy_unswitching(phase)) {
      phase->do_unswitching(this, old_new);
    }
  }

  // Minor offset re-organization to remove loop-fallout uses of
  // trip counter when there was no major reshaping.
  phase->reorg_offsets(this);

  if (_next && !_next->iteration_split(phase, old_new))
    return false;
  return true;
}


//=============================================================================
// Process all the loops in the loop tree and replace any fill
// patterns with an intrinsic version.
bool PhaseIdealLoop::do_intrinsify_fill() {
  bool changed = false;
  for (LoopTreeIterator iter(_ltree_root); !iter.done(); iter.next()) {
    IdealLoopTree* lpt = iter.current();
    changed |= intrinsify_fill(lpt);
  }
  return changed;
}


// Examine an inner loop looking for a a single store of an invariant
// value in a unit stride loop,
bool PhaseIdealLoop::match_fill_loop(IdealLoopTree* lpt, Node*& store, Node*& store_value,
                                     Node*& shift, Node*& con) {
  const char* msg = NULL;
  Node* msg_node = NULL;

  store_value = NULL;
  con = NULL;
  shift = NULL;

  // Process the loop looking for stores.  If there are multiple
  // stores or extra control flow give at this point.
  CountedLoopNode* head = lpt->_head->as_CountedLoop();
  for (uint i = 0; msg == NULL && i < lpt->_body.size(); i++) {
    Node* n = lpt->_body.at(i);
    if (n->outcnt() == 0) continue; // Ignore dead
    if (n->is_Store()) {
      if (store != NULL) {
        msg = "multiple stores";
        break;
      }
      int opc = n->Opcode();
      if (opc == Op_StoreP || opc == Op_StoreN || opc == Op_StoreNKlass || opc == Op_StoreCM) {
        msg = "oop fills not handled";
        break;
      }
      Node* value = n->in(MemNode::ValueIn);
      if (!lpt->is_invariant(value)) {
        msg  = "variant store value";
      } else if (!_igvn.type(n->in(MemNode::Address))->isa_aryptr()) {
        msg = "not array address";
      }
      store = n;
      store_value = value;
    } else if (n->is_If() && n != head->loopexit()) {
      msg = "extra control flow";
      msg_node = n;
    }
  }

  if (store == NULL) {
    // No store in loop
    return false;
  }

  if (msg == NULL && head->stride_con() != 1) {
    // could handle negative strides too
    if (head->stride_con() < 0) {
      msg = "negative stride";
    } else {
      msg = "non-unit stride";
    }
  }

  if (msg == NULL && !store->in(MemNode::Address)->is_AddP()) {
    msg = "can't handle store address";
    msg_node = store->in(MemNode::Address);
  }

  if (msg == NULL &&
      (!store->in(MemNode::Memory)->is_Phi() ||
       store->in(MemNode::Memory)->in(LoopNode::LoopBackControl) != store)) {
    msg = "store memory isn't proper phi";
    msg_node = store->in(MemNode::Memory);
  }

  // Make sure there is an appropriate fill routine
  BasicType t = store->as_Mem()->memory_type();
  const char* fill_name;
  if (msg == NULL &&
      StubRoutines::select_fill_function(t, false, fill_name) == NULL) {
    msg = "unsupported store";
    msg_node = store;
  }

  if (msg != NULL) {
#ifndef PRODUCT
    if (TraceOptimizeFill) {
      tty->print_cr("not fill intrinsic candidate: %s", msg);
      if (msg_node != NULL) msg_node->dump();
    }
#endif
    return false;
  }

  // Make sure the address expression can be handled.  It should be
  // head->phi * elsize + con.  head->phi might have a ConvI2L(CastII()).
  Node* elements[4];
  Node* cast = NULL;
  Node* conv = NULL;
  bool found_index = false;
  int count = store->in(MemNode::Address)->as_AddP()->unpack_offsets(elements, ARRAY_SIZE(elements));
  for (int e = 0; e < count; e++) {
    Node* n = elements[e];
    if (n->is_Con() && con == NULL) {
      con = n;
    } else if (n->Opcode() == Op_LShiftX && shift == NULL) {
      Node* value = n->in(1);
#ifdef _LP64
      if (value->Opcode() == Op_ConvI2L) {
        conv = value;
        value = value->in(1);
      }
      if (value->Opcode() == Op_CastII &&
          value->as_CastII()->has_range_check()) {
        // Skip range check dependent CastII nodes
        cast = value;
        value = value->in(1);
      }
#endif
      if (value != head->phi()) {
        msg = "unhandled shift in address";
      } else {
        if (type2aelembytes(store->as_Mem()->memory_type(), true) != (1 << n->in(2)->get_int())) {
          msg = "scale doesn't match";
        } else {
          found_index = true;
          shift = n;
        }
      }
    } else if (n->Opcode() == Op_ConvI2L && conv == NULL) {
      conv = n;
      n = n->in(1);
      if (n->Opcode() == Op_CastII &&
          n->as_CastII()->has_range_check()) {
        // Skip range check dependent CastII nodes
        cast = n;
        n = n->in(1);
      }
      if (n == head->phi()) {
        found_index = true;
      } else {
        msg = "unhandled input to ConvI2L";
      }
    } else if (n == head->phi()) {
      // no shift, check below for allowed cases
      found_index = true;
    } else {
      msg = "unhandled node in address";
      msg_node = n;
    }
  }

  if (count == -1) {
    msg = "malformed address expression";
    msg_node = store;
  }

  if (!found_index) {
    msg = "missing use of index";
  }

  // byte sized items won't have a shift
  if (msg == NULL && shift == NULL && t != T_BYTE && t != T_BOOLEAN) {
    msg = "can't find shift";
    msg_node = store;
  }

  if (msg != NULL) {
#ifndef PRODUCT
    if (TraceOptimizeFill) {
      tty->print_cr("not fill intrinsic: %s", msg);
      if (msg_node != NULL) msg_node->dump();
    }
#endif
    return false;
  }

  // No make sure all the other nodes in the loop can be handled
  VectorSet ok(Thread::current()->resource_area());

  // store related values are ok
  ok.set(store->_idx);
  ok.set(store->in(MemNode::Memory)->_idx);

  CountedLoopEndNode* loop_exit = head->loopexit();
  guarantee(loop_exit != NULL, "no loop exit node");

  // Loop structure is ok
  ok.set(head->_idx);
  ok.set(loop_exit->_idx);
  ok.set(head->phi()->_idx);
  ok.set(head->incr()->_idx);
  ok.set(loop_exit->cmp_node()->_idx);
  ok.set(loop_exit->in(1)->_idx);

  // Address elements are ok
  if (con)   ok.set(con->_idx);
  if (shift) ok.set(shift->_idx);
  if (cast)  ok.set(cast->_idx);
  if (conv)  ok.set(conv->_idx);

  for (uint i = 0; msg == NULL && i < lpt->_body.size(); i++) {
    Node* n = lpt->_body.at(i);
    if (n->outcnt() == 0) continue; // Ignore dead
    if (ok.test(n->_idx)) continue;
    // Backedge projection is ok
    if (n->is_IfTrue() && n->in(0) == loop_exit) continue;
    if (!n->is_AddP()) {
      msg = "unhandled node";
      msg_node = n;
      break;
    }
  }

  // Make sure no unexpected values are used outside the loop
  for (uint i = 0; msg == NULL && i < lpt->_body.size(); i++) {
    Node* n = lpt->_body.at(i);
    // These values can be replaced with other nodes if they are used
    // outside the loop.
    if (n == store || n == loop_exit || n == head->incr() || n == store->in(MemNode::Memory)) continue;
    for (SimpleDUIterator iter(n); iter.has_next(); iter.next()) {
      Node* use = iter.get();
      if (!lpt->_body.contains(use)) {
        msg = "node is used outside loop";
        // lpt->_body.dump();
        msg_node = n;
        break;
      }
    }
  }

#ifdef ASSERT
  if (TraceOptimizeFill) {
    if (msg != NULL) {
      tty->print_cr("no fill intrinsic: %s", msg);
      if (msg_node != NULL) msg_node->dump();
    } else {
      tty->print_cr("fill intrinsic for:");
    }
    store->dump();
    if (Verbose) {
      lpt->_body.dump();
    }
  }
#endif

  return msg == NULL;
}



bool PhaseIdealLoop::intrinsify_fill(IdealLoopTree* lpt) {
  // Only for counted inner loops
  if (!lpt->is_counted() || !lpt->is_inner()) {
    return false;
  }

  // Must have constant stride
  CountedLoopNode* head = lpt->_head->as_CountedLoop();
  if (!head->is_valid_counted_loop() || !head->is_normal_loop()) {
    return false;
  }

  // Check that the body only contains a store of a loop invariant
  // value that is indexed by the loop phi.
  Node* store = NULL;
  Node* store_value = NULL;
  Node* shift = NULL;
  Node* offset = NULL;
  if (!match_fill_loop(lpt, store, store_value, shift, offset)) {
    return false;
  }

#ifndef PRODUCT
  if (TraceLoopOpts) {
    tty->print("ArrayFill    ");
    lpt->dump_head();
  }
#endif

  // Now replace the whole loop body by a call to a fill routine that
  // covers the same region as the loop.
  Node* base = store->in(MemNode::Address)->as_AddP()->in(AddPNode::Base);

  // Build an expression for the beginning of the copy region
  Node* index = head->init_trip();
#ifdef _LP64
  index = new ConvI2LNode(index);
  _igvn.register_new_node_with_optimizer(index);
#endif
  if (shift != NULL) {
    // byte arrays don't require a shift but others do.
    index = new LShiftXNode(index, shift->in(2));
    _igvn.register_new_node_with_optimizer(index);
  }
  index = new AddPNode(base, base, index);
  _igvn.register_new_node_with_optimizer(index);
  Node* from = new AddPNode(base, index, offset);
  _igvn.register_new_node_with_optimizer(from);
  // Compute the number of elements to copy
  Node* len = new SubINode(head->limit(), head->init_trip());
  _igvn.register_new_node_with_optimizer(len);

  BasicType t = store->as_Mem()->memory_type();
  bool aligned = false;
  if (offset != NULL && head->init_trip()->is_Con()) {
    int element_size = type2aelembytes(t);
    aligned = (offset->find_intptr_t_type()->get_con() + head->init_trip()->get_int() * element_size) % HeapWordSize == 0;
  }

  // Build a call to the fill routine
  const char* fill_name;
  address fill = StubRoutines::select_fill_function(t, aligned, fill_name);
  assert(fill != NULL, "what?");

  // Convert float/double to int/long for fill routines
  if (t == T_FLOAT) {
    store_value = new MoveF2INode(store_value);
    _igvn.register_new_node_with_optimizer(store_value);
  } else if (t == T_DOUBLE) {
    store_value = new MoveD2LNode(store_value);
    _igvn.register_new_node_with_optimizer(store_value);
  }

  Node* mem_phi = store->in(MemNode::Memory);
  Node* result_ctrl;
  Node* result_mem;
  const TypeFunc* call_type = OptoRuntime::array_fill_Type();
  CallLeafNode *call = new CallLeafNoFPNode(call_type, fill,
                                            fill_name, TypeAryPtr::get_array_body_type(t));
  uint cnt = 0;
  call->init_req(TypeFunc::Parms + cnt++, from);
  call->init_req(TypeFunc::Parms + cnt++, store_value);
#ifdef _LP64
  len = new ConvI2LNode(len);
  _igvn.register_new_node_with_optimizer(len);
#endif
  call->init_req(TypeFunc::Parms + cnt++, len);
#ifdef _LP64
  call->init_req(TypeFunc::Parms + cnt++, C->top());
#endif
  call->init_req(TypeFunc::Control,   head->init_control());
  call->init_req(TypeFunc::I_O,       C->top());       // Does no I/O.
  call->init_req(TypeFunc::Memory,    mem_phi->in(LoopNode::EntryControl));
  call->init_req(TypeFunc::ReturnAdr, C->start()->proj_out(TypeFunc::ReturnAdr));
  call->init_req(TypeFunc::FramePtr,  C->start()->proj_out(TypeFunc::FramePtr));
  _igvn.register_new_node_with_optimizer(call);
  result_ctrl = new ProjNode(call,TypeFunc::Control);
  _igvn.register_new_node_with_optimizer(result_ctrl);
  result_mem = new ProjNode(call,TypeFunc::Memory);
  _igvn.register_new_node_with_optimizer(result_mem);

/* Disable following optimization until proper fix (add missing checks).

  // If this fill is tightly coupled to an allocation and overwrites
  // the whole body, allow it to take over the zeroing.
  AllocateNode* alloc = AllocateNode::Ideal_allocation(base, this);
  if (alloc != NULL && alloc->is_AllocateArray()) {
    Node* length = alloc->as_AllocateArray()->Ideal_length();
    if (head->limit() == length &&
        head->init_trip() == _igvn.intcon(0)) {
      if (TraceOptimizeFill) {
        tty->print_cr("Eliminated zeroing in allocation");
      }
      alloc->maybe_set_complete(&_igvn);
    } else {
#ifdef ASSERT
      if (TraceOptimizeFill) {
        tty->print_cr("filling array but bounds don't match");
        alloc->dump();
        head->init_trip()->dump();
        head->limit()->dump();
        length->dump();
      }
#endif
    }
  }
*/

  // Redirect the old control and memory edges that are outside the loop.
  Node* exit = head->loopexit()->proj_out(0);
  // Sometimes the memory phi of the head is used as the outgoing
  // state of the loop.  It's safe in this case to replace it with the
  // result_mem.
  _igvn.replace_node(store->in(MemNode::Memory), result_mem);
  lazy_replace(exit, result_ctrl);
  _igvn.replace_node(store, result_mem);
  // Any uses the increment outside of the loop become the loop limit.
  _igvn.replace_node(head->incr(), head->limit());

  // Disconnect the head from the loop.
  for (uint i = 0; i < lpt->_body.size(); i++) {
    Node* n = lpt->_body.at(i);
    _igvn.replace_node(n, C->top());
  }

  return true;
}
