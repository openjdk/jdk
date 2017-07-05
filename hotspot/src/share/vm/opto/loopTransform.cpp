/*
 * Copyright 2000-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_loopTransform.cpp.incl"

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
    n_inv1 = new (phase->C, 3) SubINode(zero, inv1);
    phase->register_new_node(n_inv1, inv1_c);
  } else {
    n_inv1 = inv1;
  }
  Node* inv;
  if (neg_inv2) {
    inv = new (phase->C, 3) SubINode(n_inv1, inv2);
  } else {
    inv = new (phase->C, 3) AddINode(n_inv1, inv2);
  }
  phase->register_new_node(inv, phase->get_early_ctrl(inv));

  Node* addx;
  if (neg_x) {
    addx = new (phase->C, 3) SubINode(inv, x);
  } else {
    addx = new (phase->C, 3) AddINode(x, inv);
  }
  phase->register_new_node(addx, phase->get_ctrl(x));
  phase->_igvn.hash_delete(n1);
  phase->_igvn.subsume_node(n1, addx);
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
  int  uniq      = phase->C->unique();
  // Peeling does loop cloning which can result in O(N^2) node construction
  if( body_size > 255 /* Prevent overflow for large body_size */
      || (body_size * body_size + uniq > MaxNodeLimit) ) {
    return false;           // too large to safely clone
  }
  while( test != _head ) {      // Scan till run off top of loop
    if( test->is_If() ) {       // Test?
      Node *ctrl = phase->get_ctrl(test->in(1));
      if (ctrl->is_top())
        return false;           // Found dead test on live IF?  No peeling!
      // Standard IF only has one input value to check for loop invariance
      assert( test->Opcode() == Op_If || test->Opcode() == Op_CountedLoopEnd, "Check this code when new subtype is added");
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
void PhaseIdealLoop::do_peeling( IdealLoopTree *loop, Node_List &old_new ) {

  C->set_major_progress();
  // Peeling a 'main' loop in a pre/main/post situation obfuscates the
  // 'pre' loop from the main and the 'pre' can no longer have it's
  // iterations adjusted.  Therefore, we need to declare this loop as
  // no longer a 'main' loop; it will need new pre and post loops before
  // we can do further RCE.
  Node *h = loop->_head;
  if( h->is_CountedLoop() ) {
    CountedLoopNode *cl = h->as_CountedLoop();
    assert(cl->trip_count() > 0, "peeling a fully unrolled loop");
    cl->set_trip_count(cl->trip_count() - 1);
    if( cl->is_main_loop() ) {
      cl->set_normal_loop();
#ifndef PRODUCT
      if( PrintOpto && VerifyLoopOptimizations ) {
        tty->print("Peeling a 'main' loop; resetting to 'normal' ");
        loop->dump_head();
      }
#endif
    }
  }

  // Step 1: Clone the loop body.  The clone becomes the peeled iteration.
  //         The pre-loop illegally has 2 control users (old & new loops).
  clone_loop( loop, old_new, dom_depth(loop->_head) );


  // Step 2: Make the old-loop fall-in edges point to the peeled iteration.
  //         Do this by making the old-loop fall-in edges act as if they came
  //         around the loopback from the prior iteration (follow the old-loop
  //         backedges) and then map to the new peeled iteration.  This leaves
  //         the pre-loop with only 1 user (the new peeled iteration), but the
  //         peeled-loop backedge has 2 users.
  for (DUIterator_Fast jmax, j = loop->_head->fast_outs(jmax); j < jmax; j++) {
    Node* old = loop->_head->fast_out(j);
    if( old->in(0) == loop->_head && old->req() == 3 &&
        (old->is_Loop() || old->is_Phi()) ) {
      Node *new_exit_value = old_new[old->in(LoopNode::LoopBackControl)->_idx];
      if( !new_exit_value )     // Backedge value is ALSO loop invariant?
        // Then loop body backedge value remains the same.
        new_exit_value = old->in(LoopNode::LoopBackControl);
      _igvn.hash_delete(old);
      old->set_req(LoopNode::EntryControl, new_exit_value);
    }
  }


  // Step 3: Cut the backedge on the clone (so its not a loop) and remove the
  //         extra backedge user.
  Node *nnn = old_new[loop->_head->_idx];
  _igvn.hash_delete(nnn);
  nnn->set_req(LoopNode::LoopBackControl, C->top());
  for (DUIterator_Fast j2max, j2 = nnn->fast_outs(j2max); j2 < j2max; j2++) {
    Node* use = nnn->fast_out(j2);
    if( use->in(0) == nnn && use->req() == 3 && use->is_Phi() ) {
      _igvn.hash_delete(use);
      use->set_req(LoopNode::LoopBackControl, C->top());
    }
  }


  // Step 4: Correct dom-depth info.  Set to loop-head depth.
  int dd = dom_depth(loop->_head);
  set_idom(loop->_head, loop->_head->in(1), dd);
  for (uint j3 = 0; j3 < loop->_body.size(); j3++) {
    Node *old = loop->_body.at(j3);
    Node *nnn = old_new[old->_idx];
    if (!has_ctrl(nnn))
      set_idom(nnn, idom(nnn), dd-1);
    // While we're at it, remove any SafePoints from the peeled code
    if( old->Opcode() == Op_SafePoint ) {
      Node *nnn = old_new[old->_idx];
      lazy_replace(nnn,nnn->in(TypeFunc::Control));
    }
  }

  // Now force out all loop-invariant dominating tests.  The optimizer
  // finds some, but we _know_ they are all useless.
  peeled_dom_test_elim(loop,old_new);

  loop->record_for_igvn();
}

//------------------------------policy_maximally_unroll------------------------
// Return exact loop trip count, or 0 if not maximally unrolling
bool IdealLoopTree::policy_maximally_unroll( PhaseIdealLoop *phase ) const {
  CountedLoopNode *cl = _head->as_CountedLoop();
  assert( cl->is_normal_loop(), "" );

  Node *init_n = cl->init_trip();
  Node *limit_n = cl->limit();

  // Non-constant bounds
  if( init_n   == NULL || !init_n->is_Con()  ||
      limit_n  == NULL || !limit_n->is_Con() ||
      // protect against stride not being a constant
      !cl->stride_is_con() ) {
    return false;
  }
  int init   = init_n->get_int();
  int limit  = limit_n->get_int();
  int span   = limit - init;
  int stride = cl->stride_con();

  if (init >= limit || stride > span) {
    // return a false (no maximally unroll) and the regular unroll/peel
    // route will make a small mess which CCP will fold away.
    return false;
  }
  uint trip_count = span/stride;   // trip_count can be greater than 2 Gig.
  assert( (int)trip_count*stride == span, "must divide evenly" );

  // Real policy: if we maximally unroll, does it get too big?
  // Allow the unrolled mess to get larger than standard loop
  // size.  After all, it will no longer be a loop.
  uint body_size    = _body.size();
  uint unroll_limit = (uint)LoopUnrollLimit * 4;
  assert( (intx)unroll_limit == LoopUnrollLimit * 4, "LoopUnrollLimit must fit in 32bits");
  cl->set_trip_count(trip_count);
  if( trip_count <= unroll_limit && body_size <= unroll_limit ) {
    uint new_body_size = body_size * trip_count;
    if (new_body_size <= unroll_limit &&
        body_size == new_body_size / trip_count &&
        // Unrolling can result in a large amount of node construction
        new_body_size < MaxNodeLimit - phase->C->unique()) {
      return true;    // maximally unroll
    }
  }

  return false;               // Do not maximally unroll
}


//------------------------------policy_unroll----------------------------------
// Return TRUE or FALSE if the loop should be unrolled or not.  Unroll if
// the loop is a CountedLoop and the body is small enough.
bool IdealLoopTree::policy_unroll( PhaseIdealLoop *phase ) const {

  CountedLoopNode *cl = _head->as_CountedLoop();
  assert( cl->is_normal_loop() || cl->is_main_loop(), "" );

  // protect against stride not being a constant
  if( !cl->stride_is_con() ) return false;

  // protect against over-unrolling
  if( cl->trip_count() <= 1 ) return false;

  int future_unroll_ct = cl->unrolled_count() * 2;

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
      (future_unroll_ct - 1) * 10.0 > cl->profile_trip_cnt() &&
      1.2 * cl->node_count_before_unroll() < (double)_body.size()) {
    return false;
  }

  Node *init_n = cl->init_trip();
  Node *limit_n = cl->limit();
  // Non-constant bounds.
  // Protect against over-unrolling when init or/and limit are not constant
  // (so that trip_count's init value is maxint) but iv range is known.
  if( init_n   == NULL || !init_n->is_Con()  ||
      limit_n  == NULL || !limit_n->is_Con() ) {
    Node* phi = cl->phi();
    if( phi != NULL ) {
      assert(phi->is_Phi() && phi->in(0) == _head, "Counted loop should have iv phi.");
      const TypeInt* iv_type = phase->_igvn.type(phi)->is_int();
      int next_stride = cl->stride_con() * 2; // stride after this unroll
      if( next_stride > 0 ) {
        if( iv_type->_lo + next_stride <= iv_type->_lo || // overflow
            iv_type->_lo + next_stride >  iv_type->_hi ) {
          return false;  // over-unrolling
        }
      } else if( next_stride < 0 ) {
        if( iv_type->_hi + next_stride >= iv_type->_hi || // overflow
            iv_type->_hi + next_stride <  iv_type->_lo ) {
          return false;  // over-unrolling
        }
      }
    }
  }

  // Adjust body_size to determine if we unroll or not
  uint body_size = _body.size();
  // Key test to unroll CaffeineMark's Logic test
  int xors_in_loop = 0;
  // Also count ModL, DivL and MulL which expand mightly
  for( uint k = 0; k < _body.size(); k++ ) {
    switch( _body.at(k)->Opcode() ) {
    case Op_XorI: xors_in_loop++; break; // CaffeineMark's Logic test
    case Op_ModL: body_size += 30; break;
    case Op_DivL: body_size += 30; break;
    case Op_MulL: body_size += 10; break;
    }
  }

  // Check for being too big
  if( body_size > (uint)LoopUnrollLimit ) {
    if( xors_in_loop >= 4 && body_size < (uint)LoopUnrollLimit*4) return true;
    // Normal case: loop too big
    return false;
  }

  // Check for stride being a small enough constant
  if( abs(cl->stride_con()) > (1<<3) ) return false;

  // Unroll once!  (Each trip will soon do double iterations)
  return true;
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
  if( !RangeCheckElimination ) return false;

  CountedLoopNode *cl = _head->as_CountedLoop();
  // If we unrolled with no intention of doing RCE and we later
  // changed our minds, we got no pre-loop.  Either we need to
  // make a new pre-loop, or we gotta disallow RCE.
  if( cl->is_main_no_pre_loop() ) return false; // Disallowed for now.
  Node *trip_counter = cl->phi();

  // Check loop body for tests of trip-counter plus loop-invariant vs
  // loop-invariant.
  for( uint i = 0; i < _body.size(); i++ ) {
    Node *iff = _body[i];
    if( iff->Opcode() == Op_If ) { // Test?

      // Comparing trip+off vs limit
      Node *bol = iff->in(1);
      if( bol->req() != 2 ) continue; // dead constant test
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

  for( uint i = 0; i < _body.size(); i++ )
    if( _body[i]->is_Mem() )
      return false;

  // No memory accesses at all!
  return true;
}

//------------------------------clone_up_backedge_goo--------------------------
// If Node n lives in the back_ctrl block and cannot float, we clone a private
// version of n in preheader_ctrl block and return that, otherwise return n.
Node *PhaseIdealLoop::clone_up_backedge_goo( Node *back_ctrl, Node *preheader_ctrl, Node *n ) {
  if( get_ctrl(n) != back_ctrl ) return n;

  Node *x = NULL;               // If required, a clone of 'n'
  // Check for 'n' being pinned in the backedge.
  if( n->in(0) && n->in(0) == back_ctrl ) {
    x = n->clone();             // Clone a copy of 'n' to preheader
    x->set_req( 0, preheader_ctrl ); // Fix x's control input to preheader
  }

  // Recursive fixup any other input edges into x.
  // If there are no changes we can just return 'n', otherwise
  // we need to clone a private copy and change it.
  for( uint i = 1; i < n->req(); i++ ) {
    Node *g = clone_up_backedge_goo( back_ctrl, preheader_ctrl, n->in(i) );
    if( g != n->in(i) ) {
      if( !x )
        x = n->clone();
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

//------------------------------insert_pre_post_loops--------------------------
// Insert pre and post loops.  If peel_only is set, the pre-loop can not have
// more iterations added.  It acts as a 'peel' only, no lower-bound RCE, no
// alignment.  Useful to unroll loops that do no array accesses.
void PhaseIdealLoop::insert_pre_post_loops( IdealLoopTree *loop, Node_List &old_new, bool peel_only ) {

  C->set_major_progress();

  // Find common pieces of the loop being guarded with pre & post loops
  CountedLoopNode *main_head = loop->_head->as_CountedLoop();
  assert( main_head->is_normal_loop(), "" );
  CountedLoopEndNode *main_end = main_head->loopexit();
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
    _igvn.hash_delete(main_end);
    main_end->set_req(CountedLoopEndNode::TestValue, bol);
  }
  // Need only 1 user of 'cmp' because I will be hacking the loop bounds.
  if( cmp->outcnt() != 1 ) {
    cmp = cmp->clone();
    register_new_node(cmp,main_end->in(CountedLoopEndNode::TestControl));
    _igvn.hash_delete(bol);
    bol->set_req(1, cmp);
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
  IfFalseNode *new_main_exit = new (C, 1) IfFalseNode(main_end);
  _igvn.register_new_node_with_optimizer( new_main_exit );
  set_idom(new_main_exit, main_end, dd_main_exit );
  set_loop(new_main_exit, loop->_parent);

  // Step A2: Build a zero-trip guard for the post-loop.  After leaving the
  // main-loop, the post-loop may not execute at all.  We 'opaque' the incr
  // (the main-loop trip-counter exit value) because we will be changing
  // the exit value (via unrolling) so we cannot constant-fold away the zero
  // trip guard until all unrolling is done.
  Node *zer_opaq = new (C, 2) Opaque1Node(C, incr);
  Node *zer_cmp  = new (C, 3) CmpINode( zer_opaq, limit );
  Node *zer_bol  = new (C, 2) BoolNode( zer_cmp, b_test );
  register_new_node( zer_opaq, new_main_exit );
  register_new_node( zer_cmp , new_main_exit );
  register_new_node( zer_bol , new_main_exit );

  // Build the IfNode
  IfNode *zer_iff = new (C, 2) IfNode( new_main_exit, zer_bol, PROB_FAIR, COUNT_UNKNOWN );
  _igvn.register_new_node_with_optimizer( zer_iff );
  set_idom(zer_iff, new_main_exit, dd_main_exit);
  set_loop(zer_iff, loop->_parent);

  // Plug in the false-path, taken if we need to skip post-loop
  _igvn.hash_delete( main_exit );
  main_exit->set_req(0, zer_iff);
  _igvn._worklist.push(main_exit);
  set_idom(main_exit, zer_iff, dd_main_exit);
  set_idom(main_exit->unique_out(), zer_iff, dd_main_exit);
  // Make the true-path, must enter the post loop
  Node *zer_taken = new (C, 1) IfTrueNode( zer_iff );
  _igvn.register_new_node_with_optimizer( zer_taken );
  set_idom(zer_taken, zer_iff, dd_main_exit);
  set_loop(zer_taken, loop->_parent);
  // Plug in the true path
  _igvn.hash_delete( post_head );
  post_head->set_req(LoopNode::EntryControl, zer_taken);
  set_idom(post_head, zer_taken, dd_main_exit);

  // Step A3: Make the fall-in values to the post-loop come from the
  // fall-out values of the main-loop.
  for (DUIterator_Fast imax, i = main_head->fast_outs(imax); i < imax; i++) {
    Node* main_phi = main_head->fast_out(i);
    if( main_phi->is_Phi() && main_phi->in(0) == main_head && main_phi->outcnt() >0 ) {
      Node *post_phi = old_new[main_phi->_idx];
      Node *fallmain  = clone_up_backedge_goo(main_head->back_control(),
                                              post_head->init_control(),
                                              main_phi->in(LoopNode::LoopBackControl));
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
  IfFalseNode *new_pre_exit = new (C, 1) IfFalseNode(pre_end);
  _igvn.register_new_node_with_optimizer( new_pre_exit );
  set_idom(new_pre_exit, pre_end, dd_main_head);
  set_loop(new_pre_exit, loop->_parent);

  // Step B2: Build a zero-trip guard for the main-loop.  After leaving the
  // pre-loop, the main-loop may not execute at all.  Later in life this
  // zero-trip guard will become the minimum-trip guard when we unroll
  // the main-loop.
  Node *min_opaq = new (C, 2) Opaque1Node(C, limit);
  Node *min_cmp  = new (C, 3) CmpINode( pre_incr, min_opaq );
  Node *min_bol  = new (C, 2) BoolNode( min_cmp, b_test );
  register_new_node( min_opaq, new_pre_exit );
  register_new_node( min_cmp , new_pre_exit );
  register_new_node( min_bol , new_pre_exit );

  // Build the IfNode (assume the main-loop is executed always).
  IfNode *min_iff = new (C, 2) IfNode( new_pre_exit, min_bol, PROB_ALWAYS, COUNT_UNKNOWN );
  _igvn.register_new_node_with_optimizer( min_iff );
  set_idom(min_iff, new_pre_exit, dd_main_head);
  set_loop(min_iff, loop->_parent);

  // Plug in the false-path, taken if we need to skip main-loop
  _igvn.hash_delete( pre_exit );
  pre_exit->set_req(0, min_iff);
  set_idom(pre_exit, min_iff, dd_main_head);
  set_idom(pre_exit->unique_out(), min_iff, dd_main_head);
  // Make the true-path, must enter the main loop
  Node *min_taken = new (C, 1) IfTrueNode( min_iff );
  _igvn.register_new_node_with_optimizer( min_taken );
  set_idom(min_taken, min_iff, dd_main_head);
  set_loop(min_taken, loop->_parent);
  // Plug in the true path
  _igvn.hash_delete( main_head );
  main_head->set_req(LoopNode::EntryControl, min_taken);
  set_idom(main_head, min_taken, dd_main_head);

  // Step B3: Make the fall-in values to the main-loop come from the
  // fall-out values of the pre-loop.
  for (DUIterator_Fast i2max, i2 = main_head->fast_outs(i2max); i2 < i2max; i2++) {
    Node* main_phi = main_head->fast_out(i2);
    if( main_phi->is_Phi() && main_phi->in(0) == main_head && main_phi->outcnt() > 0 ) {
      Node *pre_phi = old_new[main_phi->_idx];
      Node *fallpre  = clone_up_backedge_goo(pre_head->back_control(),
                                             main_head->init_control(),
                                             pre_phi->in(LoopNode::LoopBackControl));
      _igvn.hash_delete(main_phi);
      main_phi->set_req( LoopNode::EntryControl, fallpre );
    }
  }

  // Step B4: Shorten the pre-loop to run only 1 iteration (for now).
  // RCE and alignment may change this later.
  Node *cmp_end = pre_end->cmp_node();
  assert( cmp_end->in(2) == limit, "" );
  Node *pre_limit = new (C, 3) AddINode( init, stride );

  // Save the original loop limit in this Opaque1 node for
  // use by range check elimination.
  Node *pre_opaq  = new (C, 3) Opaque1Node(C, pre_limit, limit);

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

  if (pre_end->in(CountedLoopEndNode::TestValue)->as_Bool()->_test._test == BoolTest::ne) {

    BoolTest::mask new_test = (main_end->stride_con() > 0) ? BoolTest::lt : BoolTest::gt;
    // Modify pre loop end condition
    Node* pre_bol = pre_end->in(CountedLoopEndNode::TestValue)->as_Bool();
    BoolNode* new_bol0 = new (C, 2) BoolNode(pre_bol->in(1), new_test);
    register_new_node( new_bol0, pre_head->in(0) );
    _igvn.hash_delete(pre_end);
    pre_end->set_req(CountedLoopEndNode::TestValue, new_bol0);
    // Modify main loop guard condition
    assert(min_iff->in(CountedLoopEndNode::TestValue) == min_bol, "guard okay");
    BoolNode* new_bol1 = new (C, 2) BoolNode(min_bol->in(1), new_test);
    register_new_node( new_bol1, new_pre_exit );
    _igvn.hash_delete(min_iff);
    min_iff->set_req(CountedLoopEndNode::TestValue, new_bol1);
    // Modify main loop end condition
    BoolNode* main_bol = main_end->in(CountedLoopEndNode::TestValue)->as_Bool();
    BoolNode* new_bol2 = new (C, 2) BoolNode(main_bol->in(1), new_test);
    register_new_node( new_bol2, main_end->in(CountedLoopEndNode::TestControl) );
    _igvn.hash_delete(main_end);
    main_end->set_req(CountedLoopEndNode::TestValue, new_bol2);
  }

  // Flag main loop
  main_head->set_main_loop();
  if( peel_only ) main_head->set_main_no_pre_loop();

  // It's difficult to be precise about the trip-counts
  // for the pre/post loops.  They are usually very short,
  // so guess that 4 trips is a reasonable value.
  post_head->set_profile_trip_cnt(4.0);
  pre_head->set_profile_trip_cnt(4.0);

  // Now force out all loop-invariant dominating tests.  The optimizer
  // finds some, but we _know_ they are all useless.
  peeled_dom_test_elim(loop,old_new);
}

//------------------------------is_invariant-----------------------------
// Return true if n is invariant
bool IdealLoopTree::is_invariant(Node* n) const {
  Node *n_c = _phase->get_ctrl(n);
  if (n_c->is_top()) return false;
  return !is_member(_phase->get_loop(n_c));
}


//------------------------------do_unroll--------------------------------------
// Unroll the loop body one step - make each trip do 2 iterations.
void PhaseIdealLoop::do_unroll( IdealLoopTree *loop, Node_List &old_new, bool adjust_min_trip ) {
  assert( LoopUnrollLimit, "" );
#ifndef PRODUCT
  if( PrintOpto && VerifyLoopOptimizations ) {
    tty->print("Unrolling ");
    loop->dump_head();
  }
#endif
  CountedLoopNode *loop_head = loop->_head->as_CountedLoop();
  CountedLoopEndNode *loop_end = loop_head->loopexit();
  assert( loop_end, "" );

  // Remember loop node count before unrolling to detect
  // if rounds of unroll,optimize are making progress
  loop_head->set_node_count_before_unroll(loop->_body.size());

  Node *ctrl  = loop_head->in(LoopNode::EntryControl);
  Node *limit = loop_head->limit();
  Node *init  = loop_head->init_trip();
  Node *strid = loop_head->stride();

  Node *opaq = NULL;
  if( adjust_min_trip ) {       // If not maximally unrolling, need adjustment
    assert( loop_head->is_main_loop(), "" );
    assert( ctrl->Opcode() == Op_IfTrue || ctrl->Opcode() == Op_IfFalse, "" );
    Node *iff = ctrl->in(0);
    assert( iff->Opcode() == Op_If, "" );
    Node *bol = iff->in(1);
    assert( bol->Opcode() == Op_Bool, "" );
    Node *cmp = bol->in(1);
    assert( cmp->Opcode() == Op_CmpI, "" );
    opaq = cmp->in(2);
    // Occasionally it's possible for a pre-loop Opaque1 node to be
    // optimized away and then another round of loop opts attempted.
    // We can not optimize this particular loop in that case.
    if( opaq->Opcode() != Op_Opaque1 )
      return;                   // Cannot find pre-loop!  Bail out!
  }

  C->set_major_progress();

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
  Node *span = new (C, 3) SubINode( limit, init );
  register_new_node( span, ctrl );
  Node *trip = new (C, 3) DivINode( 0, span, strid );
  register_new_node( trip, ctrl );
  Node *mtwo = _igvn.intcon(-2);
  set_ctrl(mtwo, C->root());
  Node *rond = new (C, 3) AndINode( trip, mtwo );
  register_new_node( rond, ctrl );
  Node *spn2 = new (C, 3) MulINode( rond, strid );
  register_new_node( spn2, ctrl );
  Node *lim2 = new (C, 3) AddINode( spn2, init );
  register_new_node( lim2, ctrl );

  // Hammer in the new limit
  Node *ctrl2 = loop_end->in(0);
  Node *cmp2 = new (C, 3) CmpINode( loop_head->incr(), lim2 );
  register_new_node( cmp2, ctrl2 );
  Node *bol2 = new (C, 2) BoolNode( cmp2, loop_end->test_trip() );
  register_new_node( bol2, ctrl2 );
  _igvn.hash_delete(loop_end);
  loop_end->set_req(CountedLoopEndNode::TestValue, bol2);

  // Step 3: Find the min-trip test guaranteed before a 'main' loop.
  // Make it a 1-trip test (means at least 2 trips).
  if( adjust_min_trip ) {
    // Guard test uses an 'opaque' node which is not shared.  Hence I
    // can edit it's inputs directly.  Hammer in the new limit for the
    // minimum-trip guard.
    assert( opaq->outcnt() == 1, "" );
    _igvn.hash_delete(opaq);
    opaq->set_req(1, lim2);
  }

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
}

//------------------------------do_maximally_unroll----------------------------

void PhaseIdealLoop::do_maximally_unroll( IdealLoopTree *loop, Node_List &old_new ) {
  CountedLoopNode *cl = loop->_head->as_CountedLoop();
  assert( cl->trip_count() > 0, "");

  // If loop is tripping an odd number of times, peel odd iteration
  if( (cl->trip_count() & 1) == 1 ) {
    do_peeling( loop, old_new );
  }

  // Now its tripping an even number of times remaining.  Double loop body.
  // Do not adjust pre-guards; they are not needed and do not exist.
  if( cl->trip_count() > 0 ) {
    do_unroll( loop, old_new, false );
  }
}

//------------------------------dominates_backedge---------------------------------
// Returns true if ctrl is executed on every complete iteration
bool IdealLoopTree::dominates_backedge(Node* ctrl) {
  assert(ctrl->is_CFG(), "must be control");
  Node* backedge = _head->as_Loop()->in(LoopNode::LoopBackControl);
  return _phase->dom_lca_internal(ctrl, backedge) == ctrl;
}

//------------------------------add_constraint---------------------------------
// Constrain the main loop iterations so the condition:
//    scale_con * I + offset  <  limit
// always holds true.  That is, either increase the number of iterations in
// the pre-loop or the post-loop until the condition holds true in the main
// loop.  Stride, scale, offset and limit are all loop invariant.  Further,
// stride and scale are constants (offset and limit often are).
void PhaseIdealLoop::add_constraint( int stride_con, int scale_con, Node *offset, Node *limit, Node *pre_ctrl, Node **pre_limit, Node **main_limit ) {

  // Compute "I :: (limit-offset)/scale_con"
  Node *con = new (C, 3) SubINode( limit, offset );
  register_new_node( con, pre_ctrl );
  Node *scale = _igvn.intcon(scale_con);
  set_ctrl(scale, C->root());
  Node *X = new (C, 3) DivINode( 0, con, scale );
  register_new_node( X, pre_ctrl );

  // For positive stride, the pre-loop limit always uses a MAX function
  // and the main loop a MIN function.  For negative stride these are
  // reversed.

  // Also for positive stride*scale the affine function is increasing, so the
  // pre-loop must check for underflow and the post-loop for overflow.
  // Negative stride*scale reverses this; pre-loop checks for overflow and
  // post-loop for underflow.
  if( stride_con*scale_con > 0 ) {
    // Compute I < (limit-offset)/scale_con
    // Adjust main-loop last iteration to be MIN/MAX(main_loop,X)
    *main_limit = (stride_con > 0)
      ? (Node*)(new (C, 3) MinINode( *main_limit, X ))
      : (Node*)(new (C, 3) MaxINode( *main_limit, X ));
    register_new_node( *main_limit, pre_ctrl );

  } else {
    // Compute (limit-offset)/scale_con + SGN(-scale_con) <= I
    // Add the negation of the main-loop constraint to the pre-loop.
    // See footnote [++] below for a derivation of the limit expression.
    Node *incr = _igvn.intcon(scale_con > 0 ? -1 : 1);
    set_ctrl(incr, C->root());
    Node *adj = new (C, 3) AddINode( X, incr );
    register_new_node( adj, pre_ctrl );
    *pre_limit = (scale_con > 0)
      ? (Node*)new (C, 3) MinINode( *pre_limit, adj )
      : (Node*)new (C, 3) MaxINode( *pre_limit, adj );
    register_new_node( *pre_limit, pre_ctrl );

//   [++] Here's the algebra that justifies the pre-loop limit expression:
//
//   NOT( scale_con * I + offset  <  limit )
//      ==
//   scale_con * I + offset  >=  limit
//      ==
//   SGN(scale_con) * I  >=  (limit-offset)/|scale_con|
//      ==
//   (limit-offset)/|scale_con|   <=  I * SGN(scale_con)
//      ==
//   (limit-offset)/|scale_con|-1  <  I * SGN(scale_con)
//      ==
//   ( if (scale_con > 0) /*common case*/
//       (limit-offset)/scale_con - 1  <  I
//     else
//       (limit-offset)/scale_con + 1  >  I
//    )
//   ( if (scale_con > 0) /*common case*/
//       (limit-offset)/scale_con + SGN(-scale_con)  <  I
//     else
//       (limit-offset)/scale_con + SGN(-scale_con)  >  I
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
    if (exp->in(2)->is_Con()) {
      Node* offset2 = NULL;
      if (depth < 2 &&
          is_scaled_iv_plus_offset(exp->in(1), iv, p_scale,
                                   p_offset != NULL ? &offset2 : NULL, depth+1)) {
        if (p_offset != NULL) {
          Node *ctrl_off2 = get_ctrl(offset2);
          Node* offset = new (C, 3) AddINode(offset2, exp->in(2));
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
        Node* offset = new (C, 3) SubINode(zero, exp->in(2));
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
  if( PrintOpto && VerifyLoopOptimizations ) {
    tty->print("Range Check Elimination ");
    loop->dump_head();
  }
#endif
  assert( RangeCheckElimination, "" );
  CountedLoopNode *cl = loop->_head->as_CountedLoop();
  assert( cl->is_main_loop(), "" );

  // Find the trip counter; we are iteration splitting based on it
  Node *trip_counter = cl->phi();
  // Find the main loop limit; we will trim it's iterations
  // to not ever trip end tests
  Node *main_limit = cl->limit();
  // Find the pre-loop limit; we will expand it's iterations to
  // not ever trip low tests.
  Node *ctrl  = cl->in(LoopNode::EntryControl);
  assert( ctrl->Opcode() == Op_IfTrue || ctrl->Opcode() == Op_IfFalse, "" );
  Node *iffm = ctrl->in(0);
  assert( iffm->Opcode() == Op_If, "" );
  Node *p_f = iffm->in(0);
  assert( p_f->Opcode() == Op_IfFalse, "" );
  CountedLoopEndNode *pre_end = p_f->in(0)->as_CountedLoopEnd();
  assert( pre_end->loopnode()->is_pre_loop(), "" );
  Node *pre_opaq1 = pre_end->limit();
  // Occasionally it's possible for a pre-loop Opaque1 node to be
  // optimized away and then another round of loop opts attempted.
  // We can not optimize this particular loop in that case.
  if( pre_opaq1->Opcode() != Op_Opaque1 )
    return;
  Opaque1Node *pre_opaq = (Opaque1Node*)pre_opaq1;
  Node *pre_limit = pre_opaq->in(1);

  // Where do we put new limit calculations
  Node *pre_ctrl = pre_end->loopnode()->in(LoopNode::EntryControl);

  // Ensure the original loop limit is available from the
  // pre-loop Opaque1 node.
  Node *orig_limit = pre_opaq->original_loop_limit();
  if( orig_limit == NULL || _igvn.type(orig_limit) == Type::TOP )
    return;

  // Need to find the main-loop zero-trip guard
  Node *bolzm = iffm->in(1);
  assert( bolzm->Opcode() == Op_Bool, "" );
  Node *cmpzm = bolzm->in(1);
  assert( cmpzm->is_Cmp(), "" );
  Node *opqzm = cmpzm->in(2);
  if( opqzm->Opcode() != Op_Opaque1 )
    return;
  assert( opqzm->in(1) == main_limit, "do not understand situation" );

  // Must know if its a count-up or count-down loop

  // protect against stride not being a constant
  if ( !cl->stride_is_con() ) {
    return;
  }
  int stride_con = cl->stride_con();
  Node *zero = _igvn.intcon(0);
  Node *one  = _igvn.intcon(1);
  set_ctrl(zero, C->root());
  set_ctrl(one,  C->root());

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
    if( iff->Opcode() == Op_If ) { // Test?

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

      // At this point we have the expression as:
      //   scale_con * trip_counter + offset :: limit
      // where scale_con, offset and limit are loop invariant.  Trip_counter
      // monotonically increases by stride_con, a constant.  Both (or either)
      // stride_con and scale_con can be negative which will flip about the
      // sense of the test.

      // Adjust pre and main loop limits to guard the correct iteration set
      if( cmp->Opcode() == Op_CmpU ) {// Unsigned compare is really 2 tests
        if( b_test._test == BoolTest::lt ) { // Range checks always use lt
          // The overflow limit: scale*I+offset < limit
          add_constraint( stride_con, scale_con, offset, limit, pre_ctrl, &pre_limit, &main_limit );
          // The underflow limit: 0 <= scale*I+offset.
          // Some math yields: -scale*I-(offset+1) < 0
          Node *plus_one = new (C, 3) AddINode( offset, one );
          register_new_node( plus_one, pre_ctrl );
          Node *neg_offset = new (C, 3) SubINode( zero, plus_one );
          register_new_node( neg_offset, pre_ctrl );
          add_constraint( stride_con, -scale_con, neg_offset, zero, pre_ctrl, &pre_limit, &main_limit );
          if (!conditional_rc) {
            conditional_rc = !loop->dominates_backedge(iff);
          }
        } else {
#ifndef PRODUCT
          if( PrintOpto )
            tty->print_cr("missed RCE opportunity");
#endif
          continue;             // In release mode, ignore it
        }
      } else {                  // Otherwise work on normal compares
        switch( b_test._test ) {
        case BoolTest::ge:      // Convert X >= Y to -X <= -Y
          scale_con = -scale_con;
          offset = new (C, 3) SubINode( zero, offset );
          register_new_node( offset, pre_ctrl );
          limit  = new (C, 3) SubINode( zero, limit  );
          register_new_node( limit, pre_ctrl );
          // Fall into LE case
        case BoolTest::le:      // Convert X <= Y to X < Y+1
          limit = new (C, 3) AddINode( limit, one );
          register_new_node( limit, pre_ctrl );
          // Fall into LT case
        case BoolTest::lt:
          add_constraint( stride_con, scale_con, offset, limit, pre_ctrl, &pre_limit, &main_limit );
          if (!conditional_rc) {
            conditional_rc = !loop->dominates_backedge(iff);
          }
          break;
        default:
#ifndef PRODUCT
          if( PrintOpto )
            tty->print_cr("missed RCE opportunity");
#endif
          continue;             // Unhandled case
        }
      }

      // Kill the eliminated test
      C->set_major_progress();
      Node *kill_con = _igvn.intcon( 1-flip );
      set_ctrl(kill_con, C->root());
      _igvn.hash_delete(iff);
      iff->set_req(1, kill_con);
      _igvn._worklist.push(iff);
      // Find surviving projection
      assert(iff->is_If(), "");
      ProjNode* dp = ((IfNode*)iff)->proj_out(1-flip);
      // Find loads off the surviving projection; remove their control edge
      for (DUIterator_Fast imax, i = dp->fast_outs(imax); i < imax; i++) {
        Node* cd = dp->fast_out(i); // Control-dependent node
        if( cd->is_Load() ) {   // Loads can now float around in the loop
          _igvn.hash_delete(cd);
          // Allow the load to float around in the loop, or before it
          // but NOT before the pre-loop.
          cd->set_req(0, ctrl);   // ctrl, not NULL
          _igvn._worklist.push(cd);
          --i;
          --imax;
        }
      }

    } // End of is IF

  }

  // Update loop limits
  if (conditional_rc) {
    pre_limit = (stride_con > 0) ? (Node*)new (C,3) MinINode(pre_limit, orig_limit)
                                 : (Node*)new (C,3) MaxINode(pre_limit, orig_limit);
    register_new_node(pre_limit, pre_ctrl);
  }
  _igvn.hash_delete(pre_opaq);
  pre_opaq->set_req(1, pre_limit);

  // Note:: we are making the main loop limit no longer precise;
  // need to round up based on stride.
  if( stride_con != 1 && stride_con != -1 ) { // Cutout for common case
    // "Standard" round-up logic:  ([main_limit-init+(y-1)]/y)*y+init
    // Hopefully, compiler will optimize for powers of 2.
    Node *ctrl = get_ctrl(main_limit);
    Node *stride = cl->stride();
    Node *init = cl->init_trip();
    Node *span = new (C, 3) SubINode(main_limit,init);
    register_new_node(span,ctrl);
    Node *rndup = _igvn.intcon(stride_con + ((stride_con>0)?-1:1));
    Node *add = new (C, 3) AddINode(span,rndup);
    register_new_node(add,ctrl);
    Node *div = new (C, 3) DivINode(0,add,stride);
    register_new_node(div,ctrl);
    Node *mul = new (C, 3) MulINode(div,stride);
    register_new_node(mul,ctrl);
    Node *newlim = new (C, 3) AddINode(mul,init);
    register_new_node(newlim,ctrl);
    main_limit = newlim;
  }

  Node *main_cle = cl->loopexit();
  Node *main_bol = main_cle->in(1);
  // Hacking loop bounds; need private copies of exit test
  if( main_bol->outcnt() > 1 ) {// BoolNode shared?
    _igvn.hash_delete(main_cle);
    main_bol = main_bol->clone();// Clone a private BoolNode
    register_new_node( main_bol, main_cle->in(0) );
    main_cle->set_req(1,main_bol);
  }
  Node *main_cmp = main_bol->in(1);
  if( main_cmp->outcnt() > 1 ) { // CmpNode shared?
    _igvn.hash_delete(main_bol);
    main_cmp = main_cmp->clone();// Clone a private CmpNode
    register_new_node( main_cmp, main_cle->in(0) );
    main_bol->set_req(1,main_cmp);
  }
  // Hack the now-private loop bounds
  _igvn.hash_delete(main_cmp);
  main_cmp->set_req(2, main_limit);
  _igvn._worklist.push(main_cmp);
  // The OpaqueNode is unshared by design
  _igvn.hash_delete(opqzm);
  assert( opqzm->outcnt() == 1, "cannot hack shared node" );
  opqzm->set_req(1,main_limit);
  _igvn._worklist.push(opqzm);
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


//------------------------------policy_do_remove_empty_loop--------------------
// Micro-benchmark spamming.  Policy is to always remove empty loops.
// The 'DO' part is to replace the trip counter with the value it will
// have on the last iteration.  This will break the loop.
bool IdealLoopTree::policy_do_remove_empty_loop( PhaseIdealLoop *phase ) {
  // Minimum size must be empty loop
  if( _body.size() > 7/*number of nodes in an empty loop*/ ) return false;

  if( !_head->is_CountedLoop() ) return false;     // Dead loop
  CountedLoopNode *cl = _head->as_CountedLoop();
  if( !cl->loopexit() ) return false; // Malformed loop
  if( !phase->is_member(this,phase->get_ctrl(cl->loopexit()->in(CountedLoopEndNode::TestValue)) ) )
    return false;             // Infinite loop
#ifndef PRODUCT
  if( PrintOpto )
    tty->print_cr("Removing empty loop");
#endif
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
  // Replace the phi at loop head with the final value of the last
  // iteration.  Then the CountedLoopEnd will collapse (backedge never
  // taken) and all loop-invariant uses of the exit values will be correct.
  Node *phi = cl->phi();
  Node *final = new (phase->C, 3) SubINode( cl->limit(), cl->stride() );
  phase->register_new_node(final,cl->in(LoopNode::EntryControl));
  phase->_igvn.hash_delete(phi);
  phase->_igvn.subsume_node(phi,final);
  phase->C->set_major_progress();
  return true;
}


//=============================================================================
//------------------------------iteration_split_impl---------------------------
bool IdealLoopTree::iteration_split_impl( PhaseIdealLoop *phase, Node_List &old_new ) {
  // Check and remove empty loops (spam micro-benchmarks)
  if( policy_do_remove_empty_loop(phase) )
    return true;                     // Here we removed an empty loop

  bool should_peel = policy_peeling(phase); // Should we peel?

  bool should_unswitch = policy_unswitching(phase);

  // Non-counted loops may be peeled; exactly 1 iteration is peeled.
  // This removes loop-invariant tests (usually null checks).
  if( !_head->is_CountedLoop() ) { // Non-counted loop
    if (PartialPeelLoop && phase->partial_peel(this, old_new)) {
      // Partial peel succeeded so terminate this round of loop opts
      return false;
    }
    if( should_peel ) {            // Should we peel?
#ifndef PRODUCT
      if (PrintOpto) tty->print_cr("should_peel");
#endif
      phase->do_peeling(this,old_new);
    } else if( should_unswitch ) {
      phase->do_unswitching(this, old_new);
    }
    return true;
  }
  CountedLoopNode *cl = _head->as_CountedLoop();

  if( !cl->loopexit() ) return true; // Ignore various kinds of broken loops

  // Do nothing special to pre- and post- loops
  if( cl->is_pre_loop() || cl->is_post_loop() ) return true;

  // Compute loop trip count from profile data
  compute_profile_trip_cnt(phase);

  // Before attempting fancy unrolling, RCE or alignment, see if we want
  // to completely unroll this loop or do loop unswitching.
  if( cl->is_normal_loop() ) {
    if (should_unswitch) {
      phase->do_unswitching(this, old_new);
      return true;
    }
    bool should_maximally_unroll =  policy_maximally_unroll(phase);
    if( should_maximally_unroll ) {
      // Here we did some unrolling and peeling.  Eventually we will
      // completely unroll this loop and it will no longer be a loop.
      phase->do_maximally_unroll(this,old_new);
      return true;
    }
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
  if( should_rce || should_align || should_unroll ) {
    if( cl->is_normal_loop() )  // Convert to 'pre/main/post' loops
      phase->insert_pre_post_loops(this,old_new, !may_rce_align);

    // Adjust the pre- and main-loop limits to let the pre and post loops run
    // with full checks, but the main-loop with no checks.  Remove said
    // checks from the main body.
    if( should_rce )
      phase->do_range_check(this,old_new);

    // Double loop body for unrolling.  Adjust the minimum-trip test (will do
    // twice as many iterations as before) and the main body limit (only do
    // an even number of trips).  If we are peeling, we might enable some RCE
    // and we'd rather unroll the post-RCE'd loop SO... do not unroll if
    // peeling.
    if( should_unroll && !should_peel )
      phase->do_unroll(this,old_new, true);

    // Adjust the pre-loop limits to align the main body
    // iterations.
    if( should_align )
      Unimplemented();

  } else {                      // Else we have an unchanged counted loop
    if( should_peel )           // Might want to peel but do nothing else
      phase->do_peeling(this,old_new);
  }
  return true;
}


//=============================================================================
//------------------------------iteration_split--------------------------------
bool IdealLoopTree::iteration_split( PhaseIdealLoop *phase, Node_List &old_new ) {
  // Recursively iteration split nested loops
  if( _child && !_child->iteration_split( phase, old_new ))
    return false;

  // Clean out prior deadwood
  DCE_loop_body();


  // Look for loop-exit tests with my 50/50 guesses from the Parsing stage.
  // Replace with a 1-in-10 exit guess.
  if( _parent /*not the root loop*/ &&
      !_irreducible &&
      // Also ignore the occasional dead backedge
      !tail()->is_top() ) {
    adjust_loop_exit_prob(phase);
  }


  // Gate unrolling, RCE and peeling efforts.
  if( !_child &&                // If not an inner loop, do not split
      !_irreducible &&
      _allow_optimizations &&
      !tail()->is_top() ) {     // Also ignore the occasional dead backedge
    if (!_has_call) {
      if (!iteration_split_impl( phase, old_new )) {
        return false;
      }
    } else if (policy_unswitching(phase)) {
      phase->do_unswitching(this, old_new);
    }
  }

  // Minor offset re-organization to remove loop-fallout uses of
  // trip counter.
  if( _head->is_CountedLoop() ) phase->reorg_offsets( this );
  if( _next && !_next->iteration_split( phase, old_new ))
    return false;
  return true;
}
