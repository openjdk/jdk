/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/predicates.hpp"
#include "opto/rootnode.hpp"
#include "opto/runtime.hpp"
#include "opto/subnode.hpp"
#include "opto/superword.hpp"
#include "opto/vectornode.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/stubRoutines.hpp"

//------------------------------is_loop_exit-----------------------------------
// Given an IfNode, return the loop-exiting projection or null if both
// arms remain in the loop.
Node *IdealLoopTree::is_loop_exit(Node *iff) const {
  if (iff->outcnt() != 2) return nullptr;  // Ignore partially dead tests
  PhaseIdealLoop *phase = _phase;
  // Test is an IfNode, has 2 projections.  If BOTH are in the loop
  // we need loop unswitching instead of peeling.
  if (!is_member(phase->get_loop(iff->raw_out(0))))
    return iff->raw_out(0);
  if (!is_member(phase->get_loop(iff->raw_out(1))))
    return iff->raw_out(1);
  return nullptr;
}


//=============================================================================


//------------------------------record_for_igvn----------------------------
// Put loop body on igvn work list
void IdealLoopTree::record_for_igvn() {
  for (uint i = 0; i < _body.size(); i++) {
    Node *n = _body.at(i);
    _phase->_igvn._worklist.push(n);
  }
  // put body of outer strip mined loop on igvn work list as well
  if (_head->is_CountedLoop() && _head->as_Loop()->is_strip_mined()) {
    CountedLoopNode* l = _head->as_CountedLoop();
    Node* outer_loop = l->outer_loop();
    assert(outer_loop != nullptr, "missing piece of strip mined loop");
    _phase->_igvn._worklist.push(outer_loop);
    Node* outer_loop_tail = l->outer_loop_tail();
    assert(outer_loop_tail != nullptr, "missing piece of strip mined loop");
    _phase->_igvn._worklist.push(outer_loop_tail);
    Node* outer_loop_end = l->outer_loop_end();
    assert(outer_loop_end != nullptr, "missing piece of strip mined loop");
    _phase->_igvn._worklist.push(outer_loop_end);
    Node* outer_safepoint = l->outer_safepoint();
    assert(outer_safepoint != nullptr, "missing piece of strip mined loop");
    _phase->_igvn._worklist.push(outer_safepoint);
    Node* cle_out = _head->as_CountedLoop()->loopexit()->proj_out(false);
    assert(cle_out != nullptr, "missing piece of strip mined loop");
    _phase->_igvn._worklist.push(cle_out);
  }
}

//------------------------------compute_exact_trip_count-----------------------
// Compute loop trip count if possible. Do not recalculate trip count for
// split loops (pre-main-post) which have their limits and inits behind Opaque node.
void IdealLoopTree::compute_trip_count(PhaseIdealLoop* phase) {
  if (!_head->as_Loop()->is_valid_counted_loop(T_INT)) {
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
  if (init_n != nullptr && limit_n != nullptr) {
    // Use longs to avoid integer overflow.
    int stride_con = cl->stride_con();
    const TypeInt* init_type = phase->_igvn.type(init_n)->is_int();
    const TypeInt* limit_type = phase->_igvn.type(limit_n)->is_int();
    jlong init_con = (stride_con > 0) ? init_type->_lo : init_type->_hi;
    jlong limit_con = (stride_con > 0) ? limit_type->_hi : limit_type->_lo;
    int stride_m = stride_con - (stride_con > 0 ? 1 : -1);
    jlong trip_count = (limit_con - init_con + stride_m)/stride_con;
    // The loop body is always executed at least once even if init >= limit (for stride_con > 0) or
    // init <= limit (for stride_con < 0).
    trip_count = MAX2(trip_count, (jlong)1);
    if (trip_count < (jlong)max_juint) {
      if (init_n->is_Con() && limit_n->is_Con()) {
        // Set exact trip count.
        cl->set_exact_trip_count((uint)trip_count);
      } else if (cl->unrolled_count() == 1) {
        // Set maximum trip count before unrolling.
        cl->set_trip_count((uint)trip_count);
      }
    }
  }
}

//------------------------------compute_profile_trip_cnt----------------------------
// Compute loop trip count from profile data as
//    (backedge_count + loop_exit_count) / loop_exit_count

float IdealLoopTree::compute_profile_trip_cnt_helper(Node* n) {
  if (n->is_If()) {
    IfNode *iff = n->as_If();
    if (iff->_fcnt != COUNT_UNKNOWN && iff->_prob != PROB_UNKNOWN) {
      Node *exit = is_loop_exit(iff);
      if (exit) {
        float exit_prob = iff->_prob;
        if (exit->Opcode() == Op_IfFalse) {
          exit_prob = 1.0 - exit_prob;
        }
        if (exit_prob > PROB_MIN) {
          float exit_cnt = iff->_fcnt * exit_prob;
          return exit_cnt;
        }
      }
    }
  }
  if (n->is_Jump()) {
    JumpNode *jmp = n->as_Jump();
    if (jmp->_fcnt != COUNT_UNKNOWN) {
      float* probs = jmp->_probs;
      float exit_prob = 0;
      PhaseIdealLoop *phase = _phase;
      for (DUIterator_Fast imax, i = jmp->fast_outs(imax); i < imax; i++) {
        JumpProjNode* u = jmp->fast_out(i)->as_JumpProj();
        if (!is_member(_phase->get_loop(u))) {
          exit_prob += probs[u->_con];
        }
      }
      return exit_prob * jmp->_fcnt;
    }
  }
  return 0;
}

void IdealLoopTree::compute_profile_trip_cnt(PhaseIdealLoop *phase) {
  if (!_head->is_Loop()) {
    return;
  }
  LoopNode* head = _head->as_Loop();
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
        back->in(0)->as_If()->_prob != PROB_UNKNOWN &&
        (back->Opcode() == Op_IfTrue ? 1-back->in(0)->as_If()->_prob : back->in(0)->as_If()->_prob) > PROB_MIN) {
      break;
    }
    back = phase->idom(back);
  }
  if (back != head) {
    assert((back->Opcode() == Op_IfTrue || back->Opcode() == Op_IfFalse) &&
           back->in(0), "if-projection exists");
    IfNode* back_if = back->in(0)->as_If();
    float loop_back_cnt = back_if->_fcnt * (back->Opcode() == Op_IfTrue ? back_if->_prob : (1 - back_if->_prob));

    // Now compute a loop exit count
    float loop_exit_cnt = 0.0f;
    if (_child == nullptr) {
      for (uint i = 0; i < _body.size(); i++) {
        Node *n = _body[i];
        loop_exit_cnt += compute_profile_trip_cnt_helper(n);
      }
    } else {
      ResourceMark rm;
      Unique_Node_List wq;
      wq.push(back);
      for (uint i = 0; i < wq.size(); i++) {
        Node *n = wq.at(i);
        assert(n->is_CFG(), "only control nodes");
        if (n != head) {
          if (n->is_Region()) {
            for (uint j = 1; j < n->req(); j++) {
              wq.push(n->in(j));
            }
          } else {
            loop_exit_cnt += compute_profile_trip_cnt_helper(n);
            wq.push(n->in(0));
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
  } else {
    head->mark_profile_trip_failed();
  }
#ifndef PRODUCT
  if (TraceProfileTripCount) {
    tty->print_cr("compute_profile_trip_cnt  lp: %d cnt: %f\n", head->_idx, trip_cnt);
  }
#endif
  head->set_profile_trip_cnt(trip_cnt);
}

//---------------------find_invariant-----------------------------
// Return nonzero index of invariant operand for an associative
// binary operation of (nonconstant) invariant and variant values.
// Helper for reassociate_invariants.
int IdealLoopTree::find_invariant(Node* n, PhaseIdealLoop *phase) {
  bool in1_invar = this->is_invariant(n->in(1));
  bool in2_invar = this->is_invariant(n->in(2));
  if (in1_invar && !in2_invar) return 1;
  if (!in1_invar && in2_invar) return 2;
  return 0;
}

//---------------------is_associative-----------------------------
// Return TRUE if "n" is an associative binary node. If "base" is
// not null, "n" must be re-associative with it.
bool IdealLoopTree::is_associative(Node* n, Node* base) {
  int op = n->Opcode();
  if (base != nullptr) {
    assert(is_associative(base), "Base node should be associative");
    int base_op = base->Opcode();
    if (base_op == Op_AddI || base_op == Op_SubI) {
      return op == Op_AddI || op == Op_SubI;
    }
    if (base_op == Op_AddL || base_op == Op_SubL) {
      return op == Op_AddL || op == Op_SubL;
    }
    return op == base_op;
  } else {
    // Integer "add/sub/mul/and/or/xor" operations are associative.
    return op == Op_AddI || op == Op_AddL
        || op == Op_SubI || op == Op_SubL
        || op == Op_MulI || op == Op_MulL
        || op == Op_AndI || op == Op_AndL
        || op == Op_OrI  || op == Op_OrL
        || op == Op_XorI || op == Op_XorL;
  }
}

//---------------------reassociate_add_sub------------------------
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
Node* IdealLoopTree::reassociate_add_sub(Node* n1, int inv1_idx, int inv2_idx, PhaseIdealLoop *phase) {
  assert(n1->is_Add() || n1->is_Sub(), "Target node should be add or subtract");
  Node* n2   = n1->in(3 - inv1_idx);
  Node* inv1 = n1->in(inv1_idx);
  Node* inv2 = n2->in(inv2_idx);
  Node* x    = n2->in(3 - inv2_idx);

  bool neg_x    = n2->is_Sub() && inv2_idx == 1;
  bool neg_inv2 = n2->is_Sub() && inv2_idx == 2;
  bool neg_inv1 = n1->is_Sub() && inv1_idx == 2;
  if (n1->is_Sub() && inv1_idx == 1) {
    neg_x    = !neg_x;
    neg_inv2 = !neg_inv2;
  }

  bool is_int = n1->bottom_type()->isa_int() != nullptr;
  Node* inv1_c = phase->get_ctrl(inv1);
  Node* n_inv1;
  if (neg_inv1) {
    Node* zero;
    if (is_int) {
      zero = phase->_igvn.intcon(0);
      n_inv1 = new SubINode(zero, inv1);
    } else {
      zero = phase->_igvn.longcon(0L);
      n_inv1 = new SubLNode(zero, inv1);
    }
    phase->set_ctrl(zero, phase->C->root());
    phase->register_new_node(n_inv1, inv1_c);
  } else {
    n_inv1 = inv1;
  }

  Node* inv;
  if (is_int) {
    if (neg_inv2) {
      inv = new SubINode(n_inv1, inv2);
    } else {
      inv = new AddINode(n_inv1, inv2);
    }
    phase->register_new_node(inv, phase->get_early_ctrl(inv));
    if (neg_x) {
      return new SubINode(inv, x);
    } else {
      return new AddINode(x, inv);
    }
  } else {
    if (neg_inv2) {
      inv = new SubLNode(n_inv1, inv2);
    } else {
      inv = new AddLNode(n_inv1, inv2);
    }
    phase->register_new_node(inv, phase->get_early_ctrl(inv));
    if (neg_x) {
      return new SubLNode(inv, x);
    } else {
      return new AddLNode(x, inv);
    }
  }
}

//---------------------reassociate-----------------------------
// Reassociate invariant binary expressions with add/sub/mul/
// and/or/xor operators.
// For add/sub expressions: see "reassociate_add_sub"
//
// For mul/and/or/xor expressions:
//
// inv1 op (x op inv2) => (inv1 op inv2) op x
//
Node* IdealLoopTree::reassociate(Node* n1, PhaseIdealLoop *phase) {
  if (!is_associative(n1) || n1->outcnt() == 0) return nullptr;
  if (is_invariant(n1)) return nullptr;
  // Don't mess with add of constant (igvn moves them to expression tree root.)
  if (n1->is_Add() && n1->in(2)->is_Con()) return nullptr;

  int inv1_idx = find_invariant(n1, phase);
  if (!inv1_idx) return nullptr;
  Node* n2 = n1->in(3 - inv1_idx);
  if (!is_associative(n2, n1)) return nullptr;
  int inv2_idx = find_invariant(n2, phase);
  if (!inv2_idx) return nullptr;

  if (!phase->may_require_nodes(10, 10)) return nullptr;

  Node* result = nullptr;
  switch (n1->Opcode()) {
    case Op_AddI:
    case Op_AddL:
    case Op_SubI:
    case Op_SubL:
      result = reassociate_add_sub(n1, inv1_idx, inv2_idx, phase);
      break;
    case Op_MulI:
    case Op_MulL:
    case Op_AndI:
    case Op_AndL:
    case Op_OrI:
    case Op_OrL:
    case Op_XorI:
    case Op_XorL: {
      Node* inv1 = n1->in(inv1_idx);
      Node* inv2 = n2->in(inv2_idx);
      Node* x    = n2->in(3 - inv2_idx);
      Node* inv  = n2->clone_with_data_edge(inv1, inv2);
      phase->register_new_node(inv, phase->get_early_ctrl(inv));
      result = n1->clone_with_data_edge(x, inv);
      break;
    }
    default:
      ShouldNotReachHere();
  }

  assert(result != nullptr, "");
  phase->register_new_node(result, phase->get_ctrl(n1));
  phase->_igvn.replace_node(n1, result);
  assert(phase->get_loop(phase->get_ctrl(n1)) == this, "");
  _body.yank(n1);
  return result;
}

//---------------------reassociate_invariants-----------------------------
// Reassociate invariant expressions:
void IdealLoopTree::reassociate_invariants(PhaseIdealLoop *phase) {
  for (int i = _body.size() - 1; i >= 0; i--) {
    Node *n = _body.at(i);
    for (int j = 0; j < 5; j++) {
      Node* nn = reassociate(n, phase);
      if (nn == nullptr) break;
      n = nn; // again
    }
  }
}

//------------------------------policy_peeling---------------------------------
// Return TRUE if the loop should be peeled, otherwise return FALSE. Peeling
// is applicable if we can make a loop-invariant test (usually a null-check)
// execute before we enter the loop. When TRUE, the estimated node budget is
// also requested.
bool IdealLoopTree::policy_peeling(PhaseIdealLoop *phase) {
  uint estimate = estimate_peeling(phase);

  return estimate == 0 ? false : phase->may_require_nodes(estimate);
}

// Perform actual policy and size estimate for the loop peeling transform, and
// return the estimated loop size if peeling is applicable, otherwise return
// zero. No node budget is allocated.
uint IdealLoopTree::estimate_peeling(PhaseIdealLoop *phase) {

  // If nodes are depleted, some transform has miscalculated its needs.
  assert(!phase->exceeding_node_budget(), "sanity");

  // Peeling does loop cloning which can result in O(N^2) node construction.
  if (_body.size() > 255) {
    return 0;   // Suppress too large body size.
  }
  // Optimistic estimate that approximates loop body complexity via data and
  // control flow fan-out (instead of using the more pessimistic: BodySize^2).
  uint estimate = est_loop_clone_sz(2);

  if (phase->exceeding_node_budget(estimate)) {
    return 0;   // Too large to safely clone.
  }

  // Check for vectorized loops, any peeling done was already applied.
  if (_head->is_CountedLoop()) {
    CountedLoopNode* cl = _head->as_CountedLoop();
    if (cl->is_unroll_only() || cl->trip_count() == 1) {
      return 0;
    }
  }

  Node* test = tail();

  while (test != _head) {   // Scan till run off top of loop
    if (test->is_If()) {    // Test?
      Node *ctrl = phase->get_ctrl(test->in(1));
      if (ctrl->is_top()) {
        return 0;           // Found dead test on live IF?  No peeling!
      }
      // Standard IF only has one input value to check for loop invariance.
      assert(test->Opcode() == Op_If ||
             test->Opcode() == Op_CountedLoopEnd ||
             test->Opcode() == Op_LongCountedLoopEnd ||
             test->Opcode() == Op_RangeCheck ||
             test->Opcode() == Op_ParsePredicate,
             "Check this code when new subtype is added");
      // Condition is not a member of this loop?
      if (!is_member(phase->get_loop(ctrl)) && is_loop_exit(test)) {
        return estimate;    // Found reason to peel!
      }
    }
    // Walk up dominators to loop _head looking for test which is executed on
    // every path through the loop.
    test = phase->idom(test);
  }
  return 0;
}

//------------------------------peeled_dom_test_elim---------------------------
// If we got the effect of peeling, either by actually peeling or by making
// a pre-loop which must execute at least once, we can remove all
// loop-invariant dominated tests in the main body.
void PhaseIdealLoop::peeled_dom_test_elim(IdealLoopTree* loop, Node_List& old_new) {
  bool progress = true;
  while (progress) {
    progress = false; // Reset for next iteration
    Node* prev = loop->_head->in(LoopNode::LoopBackControl); // loop->tail();
    Node* test = prev->in(0);
    while (test != loop->_head) { // Scan till run off top of loop
      int p_op = prev->Opcode();
      assert(test != nullptr, "test cannot be null");
      Node* test_cond = nullptr;
      if ((p_op == Op_IfFalse || p_op == Op_IfTrue) && test->is_If()) {
        test_cond = test->in(1);
      }
      if (test_cond != nullptr && // Test?
          !test_cond->is_Con() && // And not already obvious?
          // And condition is not a member of this loop?
          !loop->is_member(get_loop(get_ctrl(test_cond)))) {
        // Walk loop body looking for instances of this test
        for (uint i = 0; i < loop->_body.size(); i++) {
          Node* n = loop->_body.at(i);
          // Check against cached test condition because dominated_by()
          // replaces the test condition with a constant.
          if (n->is_If() && n->in(1) == test_cond) {
            // IfNode was dominated by version in peeled loop body
            progress = true;
            dominated_by(old_new[prev->_idx]->as_IfProj(), n->as_If());
          }
        }
      }
      prev = test;
      test = idom(test);
    } // End of scan tests in loop
  } // End of while (progress)
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
//                predicates
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
//                predicates
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
//                     |
//                     v
//                predicates
//                    /
//                   /
//        clone     /            orig
//                 /
//                /              +----------+
//               /               |          |
//              /                |          |
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
//                 stmt1
//                    |
//                    v
//                predicates
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
//             | Initialized Assertion Predicates
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
void PhaseIdealLoop::do_peeling(IdealLoopTree *loop, Node_List &old_new) {

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
  LoopNode* head = loop->_head->as_Loop();
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
  const uint idx_before_clone = Compile::current()->unique();
  LoopNode* outer_loop_head = head->skip_strip_mined();
  clone_loop(loop, old_new, dom_depth(outer_loop_head), ControlAroundStripMined);

  // Step 2: Make the old-loop fall-in edges point to the peeled iteration.
  //         Do this by making the old-loop fall-in edges act as if they came
  //         around the loopback from the prior iteration (follow the old-loop
  //         backedges) and then map to the new peeled iteration.  This leaves
  //         the pre-loop with only 1 user (the new peeled iteration), but the
  //         peeled-loop backedge has 2 users.
  Node* new_entry = old_new[head->in(LoopNode::LoopBackControl)->_idx];
  _igvn.hash_delete(outer_loop_head);
  outer_loop_head->set_req(LoopNode::EntryControl, new_entry);
  for (DUIterator_Fast jmax, j = head->fast_outs(jmax); j < jmax; j++) {
    Node* old = head->fast_out(j);
    if (old->in(0) == loop->_head && old->req() == 3 && old->is_Phi()) {
      Node* new_exit_value = old_new[old->in(LoopNode::LoopBackControl)->_idx];
      if (!new_exit_value)     // Backedge value is ALSO loop invariant?
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

  int dd_outer_loop_head = dom_depth(outer_loop_head);
  set_idom(outer_loop_head, outer_loop_head->in(LoopNode::EntryControl), dd_outer_loop_head);
  for (uint j3 = 0; j3 < loop->_body.size(); j3++) {
    Node *old = loop->_body.at(j3);
    Node *nnn = old_new[old->_idx];
    if (!has_ctrl(nnn)) {
      set_idom(nnn, idom(nnn), dd_outer_loop_head-1);
    }
  }

  // Step 5: Assertion Predicates initialization
  if (counted_loop && UseLoopPredicate) {
    CountedLoopNode *cl_head = head->as_CountedLoop();
    Node* init = cl_head->init_trip();
    Node* stride = cl_head->stride();
    IdealLoopTree* outer_loop = get_loop(outer_loop_head);
    const Predicates predicates(new_head->in(LoopNode::EntryControl));
    initialize_assertion_predicates_for_peeled_loop(predicates.loop_predicate_block(),
                                                    outer_loop_head, dd_outer_loop_head,
                                                    init, stride, outer_loop,
                                                    idx_before_clone, old_new);
    initialize_assertion_predicates_for_peeled_loop(predicates.profiled_loop_predicate_block(),
                                                    outer_loop_head, dd_outer_loop_head,
                                                    init, stride, outer_loop,
                                                    idx_before_clone, old_new);
 }

  // Now force out all loop-invariant dominating tests.  The optimizer
  // finds some, but we _know_ they are all useless.
  peeled_dom_test_elim(loop,old_new);

  loop->record_for_igvn();
}

//------------------------------policy_maximally_unroll------------------------
// Calculate the exact  loop trip-count and return TRUE if loop can be fully,
// i.e. maximally, unrolled, otherwise return FALSE. When TRUE, the estimated
// node budget is also requested.
bool IdealLoopTree::policy_maximally_unroll(PhaseIdealLoop* phase) const {
  CountedLoopNode* cl = _head->as_CountedLoop();
  assert(cl->is_normal_loop(), "");
  if (!cl->is_valid_counted_loop(T_INT)) {
    return false;   // Malformed counted loop.
  }
  if (!cl->has_exact_trip_count()) {
    return false;   // Trip count is not exact.
  }

  uint trip_count = cl->trip_count();
  // Note, max_juint is used to indicate unknown trip count.
  assert(trip_count > 1, "one iteration loop should be optimized out already");
  assert(trip_count < max_juint, "exact trip_count should be less than max_juint.");

  // If nodes are depleted, some transform has miscalculated its needs.
  assert(!phase->exceeding_node_budget(), "sanity");

  // Allow the unrolled body to get larger than the standard loop size limit.
  uint unroll_limit = (uint)LoopUnrollLimit * 4;
  assert((intx)unroll_limit == LoopUnrollLimit * 4, "LoopUnrollLimit must fit in 32bits");
  if (trip_count > unroll_limit || _body.size() > unroll_limit) {
    return false;
  }

  uint new_body_size = est_loop_unroll_sz(trip_count);

  if (new_body_size == UINT_MAX) { // Check for bad estimate (overflow).
    return false;
  }

  // Fully unroll a loop with few iterations, regardless of other conditions,
  // since the following (general) loop optimizations will split such loop in
  // any case (into pre-main-post).
  if (trip_count <= 3) {
    return phase->may_require_nodes(new_body_size);
  }

  // Reject if unrolling will result in too much node construction.
  if (new_body_size > unroll_limit || phase->exceeding_node_budget(new_body_size)) {
    return false;
  }

  // Do not unroll a loop with String intrinsics code.
  // String intrinsics are large and have loops.
  for (uint k = 0; k < _body.size(); k++) {
    Node* n = _body.at(k);
    switch (n->Opcode()) {
      case Op_StrComp:
      case Op_StrEquals:
      case Op_VectorizedHashCode:
      case Op_StrIndexOf:
      case Op_StrIndexOfChar:
      case Op_EncodeISOArray:
      case Op_AryEq:
      case Op_CountPositives: {
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

  return phase->may_require_nodes(new_body_size);
}


//------------------------------policy_unroll----------------------------------
// Return TRUE or FALSE if the loop should be unrolled or not. Apply unroll if
// the loop is  a counted loop and  the loop body is small  enough. When TRUE,
// the estimated node budget is also requested.
bool IdealLoopTree::policy_unroll(PhaseIdealLoop *phase) {

  CountedLoopNode *cl = _head->as_CountedLoop();
  assert(cl->is_normal_loop() || cl->is_main_loop(), "");

  if (!cl->is_valid_counted_loop(T_INT)) {
    return false; // Malformed counted loop
  }

  // If nodes are depleted, some transform has miscalculated its needs.
  assert(!phase->exceeding_node_budget(), "sanity");

  // Protect against over-unrolling.
  // After split at least one iteration will be executed in pre-loop.
  if (cl->trip_count() <= (cl->is_normal_loop() ? 2u : 1u)) {
    return false;
  }
  _local_loop_unroll_limit  = LoopUnrollLimit;
  _local_loop_unroll_factor = 4;
  int future_unroll_cnt = cl->unrolled_count() * 2;
  if (!cl->is_vectorized_loop()) {
    if (future_unroll_cnt > LoopMaxUnroll) return false;
  } else {
    // obey user constraints on vector mapped loops with additional unrolling applied
    int unroll_constraint = (cl->slp_max_unroll()) ? cl->slp_max_unroll() : 1;
    if ((future_unroll_cnt / unroll_constraint) > LoopMaxUnroll) return false;
  }

  const int stride_con = cl->stride_con();

  // Check for initial stride being a small enough constant
  const int initial_stride_sz = MAX2(1<<2, Matcher::max_vector_size(T_BYTE) / 2);
  // Maximum stride size should protect against overflow, when doubling stride unroll_count times
  const int max_stride_size = MIN2<int>(max_jint / 2 - 2, initial_stride_sz * future_unroll_cnt);
  // No abs() use; abs(min_jint) = min_jint
  if (stride_con < -max_stride_size || stride_con > max_stride_size) return false;

  // Don't unroll if the next round of unrolling would push us
  // over the expected trip count of the loop.  One is subtracted
  // from the expected trip count because the pre-loop normally
  // executes 1 iteration.
  if (UnrollLimitForProfileCheck > 0 &&
      cl->profile_trip_cnt() != COUNT_UNKNOWN &&
      future_unroll_cnt        > UnrollLimitForProfileCheck &&
      (float)future_unroll_cnt > cl->profile_trip_cnt() - 1.0) {
    return false;
  }

  bool should_unroll = true;

  // When unroll count is greater than LoopUnrollMin, don't unroll if:
  //   the residual iterations are more than 10% of the trip count
  //   and rounds of "unroll,optimize" are not making significant progress
  //   Progress defined as current size less than 20% larger than previous size.
  if (UseSuperWord && cl->node_count_before_unroll() > 0 &&
      future_unroll_cnt > LoopUnrollMin &&
      is_residual_iters_large(future_unroll_cnt, cl) &&
      1.2 * cl->node_count_before_unroll() < (double)_body.size()) {
    if ((cl->slp_max_unroll() == 0) && !is_residual_iters_large(cl->unrolled_count(), cl)) {
      // cl->slp_max_unroll() = 0 means that the previous slp analysis never passed.
      // slp analysis may fail due to the loop IR is too complicated especially during the early stage
      // of loop unrolling analysis. But after several rounds of loop unrolling and other optimizations,
      // it's possible that the loop IR becomes simple enough to pass the slp analysis.
      // So we don't return immediately in hoping that the next slp analysis can succeed.
      should_unroll = false;
      future_unroll_cnt = cl->unrolled_count();
    } else {
      return false;
    }
  }

  Node *init_n = cl->init_trip();
  Node *limit_n = cl->limit();
  if (limit_n == nullptr) return false; // We will dereference it below.

  // Non-constant bounds.
  // Protect against over-unrolling when init or/and limit are not constant
  // (so that trip_count's init value is maxint) but iv range is known.
  if (init_n == nullptr || !init_n->is_Con() || !limit_n->is_Con()) {
    Node* phi = cl->phi();
    if (phi != nullptr) {
      assert(phi->is_Phi() && phi->in(0) == _head, "Counted loop should have iv phi.");
      const TypeInt* iv_type = phase->_igvn.type(phi)->is_int();
      int next_stride = stride_con * 2; // stride after this unroll
      if (next_stride > 0) {
        if (iv_type->_lo > max_jint - next_stride || // overflow
            iv_type->_lo + next_stride >  iv_type->_hi) {
          return false;  // over-unrolling
        }
      } else if (next_stride < 0) {
        if (iv_type->_hi < min_jint - next_stride || // overflow
            iv_type->_hi + next_stride <  iv_type->_lo) {
          return false;  // over-unrolling
        }
      }
    }
  }

  // After unroll limit will be adjusted: new_limit = limit-stride.
  // Bailout if adjustment overflow.
  const TypeInt* limit_type = phase->_igvn.type(limit_n)->is_int();
  if ((stride_con > 0 && ((min_jint + stride_con) > limit_type->_hi)) ||
      (stride_con < 0 && ((max_jint + stride_con) < limit_type->_lo)))
    return false;  // overflow

  // Rudimentary cost model to estimate loop unrolling
  // factor.
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
      case Op_RoundF:
      case Op_RoundD: {
          body_size += Matcher::scalar_op_pre_select_sz_estimate(n->Opcode(), n->bottom_type()->basic_type());
      } break;
      case Op_CountTrailingZerosV:
      case Op_CountLeadingZerosV:
      case Op_ReverseV:
      case Op_RoundVF:
      case Op_RoundVD:
      case Op_VectorCastD2X:
      case Op_VectorCastF2X:
      case Op_PopCountVI:
      case Op_PopCountVL: {
        const TypeVect* vt = n->bottom_type()->is_vect();
        body_size += Matcher::vector_op_pre_select_sz_estimate(n->Opcode(), vt->element_basic_type(), vt->length());
      } break;
      case Op_StrComp:
      case Op_StrEquals:
      case Op_StrIndexOf:
      case Op_StrIndexOfChar:
      case Op_EncodeISOArray:
      case Op_AryEq:
      case Op_VectorizedHashCode:
      case Op_CountPositives: {
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
    // Only attempt slp analysis when user controls do not prohibit it
    if (!range_checks_present() && (LoopMaxUnroll > _local_loop_unroll_factor)) {
      // Once policy_slp_analysis succeeds, mark the loop with the
      // maximal unroll factor so that we minimize analysis passes
      if (future_unroll_cnt >= _local_loop_unroll_factor) {
        policy_unroll_slp_analysis(cl, phase, future_unroll_cnt);
      }
    }
  }

  int slp_max_unroll_factor = cl->slp_max_unroll();
  if ((LoopMaxUnroll < slp_max_unroll_factor) && FLAG_IS_DEFAULT(LoopMaxUnroll) && UseSubwordForMaxVector) {
    LoopMaxUnroll = slp_max_unroll_factor;
  }

  uint estimate = est_loop_clone_sz(2);

  if (cl->has_passed_slp()) {
    if (slp_max_unroll_factor >= future_unroll_cnt) {
      return should_unroll && phase->may_require_nodes(estimate);
    }
    return false; // Loop too big.
  }

  // Check for being too big
  if (body_size > (uint)_local_loop_unroll_limit) {
    if ((cl->is_subword_loop() || xors_in_loop >= 4) && body_size < 4u * LoopUnrollLimit) {
      return should_unroll && phase->may_require_nodes(estimate);
    }
    return false; // Loop too big.
  }

  if (cl->is_unroll_only()) {
    if (TraceSuperWordLoopUnrollAnalysis) {
      tty->print_cr("policy_unroll passed vector loop(vlen=%d, factor=%d)\n",
                    slp_max_unroll_factor, future_unroll_cnt);
    }
  }

  // Unroll once!  (Each trip will soon do double iterations)
  return should_unroll && phase->may_require_nodes(estimate);
}

void IdealLoopTree::policy_unroll_slp_analysis(CountedLoopNode *cl, PhaseIdealLoop *phase, int future_unroll_cnt) {

  // If nodes are depleted, some transform has miscalculated its needs.
  assert(!phase->exceeding_node_budget(), "sanity");

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
      if (slp_max_unroll_factor >= future_unroll_cnt) {
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


//------------------------------policy_range_check-----------------------------
// Return TRUE or FALSE if the loop should be range-check-eliminated or not.
// When TRUE, the estimated node budget is also requested.
//
// We will actually perform iteration-splitting, a more powerful form of RCE.
bool IdealLoopTree::policy_range_check(PhaseIdealLoop* phase, bool provisional, BasicType bt) const {
  if (!provisional && !RangeCheckElimination) return false;

  // If nodes are depleted, some transform has miscalculated its needs.
  assert(provisional || !phase->exceeding_node_budget(), "sanity");

  if (_head->is_CountedLoop()) {
    CountedLoopNode *cl = _head->as_CountedLoop();
    // If we unrolled  with no intention of doing RCE and we  later changed our
    // minds, we got no pre-loop.  Either we need to make a new pre-loop, or we
    // have to disallow RCE.
    if (cl->is_main_no_pre_loop()) return false; // Disallowed for now.

    // check for vectorized loops, some opts are no longer needed
    // RCE needs pre/main/post loops. Don't apply it on a single iteration loop.
    if (cl->is_unroll_only() || (cl->is_normal_loop() && cl->trip_count() == 1)) return false;
  } else {
    assert(provisional, "no long counted loop expected");
  }

  BaseCountedLoopNode* cl = _head->as_BaseCountedLoop();
  Node *trip_counter = cl->phi();
  assert(!cl->is_LongCountedLoop() || bt == T_LONG, "only long range checks in long counted loops");
  assert(cl->is_valid_counted_loop(cl->bt()), "only for well formed loops");

  // Check loop body for tests of trip-counter plus loop-invariant vs
  // loop-invariant.
  for (uint i = 0; i < _body.size(); i++) {
    Node *iff = _body[i];
    if (iff->Opcode() == Op_If ||
        iff->Opcode() == Op_RangeCheck) { // Test?

      // Comparing trip+off vs limit
      Node *bol = iff->in(1);
      if (bol->req() != 2) {
        continue; // dead constant test
      }
      if (!bol->is_Bool()) {
        assert(bol->Opcode() == Op_Conv2B, "predicate check only");
        continue;
      }
      if (bol->as_Bool()->_test._test == BoolTest::ne) {
        continue; // not RC
      }
      Node *cmp = bol->in(1);

      if (provisional) {
        // Try to pattern match with either cmp inputs, do not check
        // whether one of the inputs is loop independent as it may not
        // have had a chance to be hoisted yet.
        if (!phase->is_scaled_iv_plus_offset(cmp->in(1), trip_counter, bt, nullptr, nullptr) &&
            !phase->is_scaled_iv_plus_offset(cmp->in(2), trip_counter, bt, nullptr, nullptr)) {
          continue;
        }
      } else {
        Node *rc_exp = cmp->in(1);
        Node *limit = cmp->in(2);
        Node *limit_c = phase->get_ctrl(limit);
        if (limit_c == phase->C->top()) {
          return false;           // Found dead test on live IF?  No RCE!
        }
        if (is_member(phase->get_loop(limit_c))) {
          // Compare might have operands swapped; commute them
          rc_exp = cmp->in(2);
          limit  = cmp->in(1);
          limit_c = phase->get_ctrl(limit);
          if (is_member(phase->get_loop(limit_c))) {
            continue;             // Both inputs are loop varying; cannot RCE
          }
        }

        if (!phase->is_scaled_iv_plus_offset(rc_exp, trip_counter, bt, nullptr, nullptr)) {
          continue;
        }
      }
      // Found a test like 'trip+off vs limit'. Test is an IfNode, has two (2)
      // projections. If BOTH are in the loop we need loop unswitching instead
      // of iteration splitting.
      if (is_loop_exit(iff)) {
        // Found valid reason to split iterations (if there is room).
        // NOTE: Usually a gross overestimate.
        // Long range checks cause the loop to be transformed in a loop nest which only causes a fixed number of nodes
        // to be added
        return provisional || bt == T_LONG || phase->may_require_nodes(est_loop_clone_sz(2));
      }
    } // End of is IF
  }

  return false;
}

//------------------------------policy_peel_only-------------------------------
// Return TRUE or FALSE if the loop should NEVER be RCE'd or aligned.  Useful
// for unrolling loops with NO array accesses.
bool IdealLoopTree::policy_peel_only(PhaseIdealLoop *phase) const {

  // If nodes are depleted, some transform has miscalculated its needs.
  assert(!phase->exceeding_node_budget(), "sanity");

  // check for vectorized loops, any peeling done was already applied
  if (_head->is_CountedLoop() && _head->as_CountedLoop()->is_unroll_only()) {
    return false;
  }

  for (uint i = 0; i < _body.size(); i++) {
    if (_body[i]->is_Mem()) {
      return false;
    }
  }
  // No memory accesses at all!
  return true;
}

//------------------------------clone_up_backedge_goo--------------------------
// If Node n lives in the back_ctrl block and cannot float, we clone a private
// version of n in preheader_ctrl block and return that, otherwise return n.
Node *PhaseIdealLoop::clone_up_backedge_goo(Node *back_ctrl, Node *preheader_ctrl, Node *n, VectorSet &visited, Node_Stack &clones) {
  if (get_ctrl(n) != back_ctrl) return n;

  // Only visit once
  if (visited.test_set(n->_idx)) {
    Node *x = clones.find(n->_idx);
    return (x != nullptr) ? x : n;
  }

  Node *x = nullptr;               // If required, a clone of 'n'
  // Check for 'n' being pinned in the backedge.
  if (n->in(0) && n->in(0) == back_ctrl) {
    assert(clones.find(n->_idx) == nullptr, "dead loop");
    x = n->clone();             // Clone a copy of 'n' to preheader
    clones.push(x, n->_idx);
    x->set_req(0, preheader_ctrl); // Fix x's control input to preheader
  }

  // Recursive fixup any other input edges into x.
  // If there are no changes we can just return 'n', otherwise
  // we need to clone a private copy and change it.
  for (uint i = 1; i < n->req(); i++) {
    Node *g = clone_up_backedge_goo(back_ctrl, preheader_ctrl, n->in(i), visited, clones);
    if (g != n->in(i)) {
      if (!x) {
        assert(clones.find(n->_idx) == nullptr, "dead loop");
        x = n->clone();
        clones.push(x, n->_idx);
      }
      x->set_req(i, g);
    }
  }
  if (x) {                     // x can legally float to pre-header location
    register_new_node(x, preheader_ctrl);
    return x;
  } else {                      // raise n to cover LCA of uses
    set_ctrl(n, find_non_split_ctrl(back_ctrl->in(0)));
  }
  return n;
}

Node* PhaseIdealLoop::cast_incr_before_loop(Node* incr, Node* ctrl, Node* loop) {
  Node* castii = new CastIINode(incr, TypeInt::INT, ConstraintCastNode::UnconditionalDependency);
  castii->set_req(0, ctrl);
  register_new_node(castii, ctrl);
  for (DUIterator_Fast imax, i = incr->fast_outs(imax); i < imax; i++) {
    Node* n = incr->fast_out(i);
    if (n->is_Phi() && n->in(0) == loop) {
      int nrep = n->replace_edge(incr, castii, &_igvn);
      return castii;
    }
  }
  return nullptr;
}

#ifdef ASSERT
void PhaseIdealLoop::ensure_zero_trip_guard_proj(Node* node, bool is_main_loop) {
  assert(node->is_IfProj(), "must be the zero trip guard If node");
  Node* zer_bol = node->in(0)->in(1);
  assert(zer_bol != nullptr && zer_bol->is_Bool(), "must be Bool");
  Node* zer_cmp = zer_bol->in(1);
  assert(zer_cmp != nullptr && zer_cmp->Opcode() == Op_CmpI, "must be CmpI");
  // For the main loop, the opaque node is the second input to zer_cmp, for the post loop it's the first input node
  Node* zer_opaq = zer_cmp->in(is_main_loop ? 2 : 1);
  assert(zer_opaq != nullptr && zer_opaq->Opcode() == Op_OpaqueZeroTripGuard, "must be OpaqueZeroTripGuard");
}
#endif

// Make two copies of each Template Assertion Predicate before the pre-loop and add them to the main-loop. One remains
// a template while the other one is initialized with the initial value of the loop induction variable. The Initialized
// Assertion Predicates ensures that the main-loop is removed if some type ranges of Cast or Convert nodes become
// impossible and are replaced by top (i.e. a sign that the main-loop is dead).
void PhaseIdealLoop::copy_assertion_predicates_to_main_loop_helper(const PredicateBlock* predicate_block, Node* init,
                                                                   Node* stride, IdealLoopTree* outer_loop,
                                                                   LoopNode* outer_main_head, const uint dd_main_head,
                                                                   const uint idx_before_pre_post,
                                                                   const uint idx_after_post_before_pre,
                                                                   Node* zero_trip_guard_proj_main,
                                                                   Node* zero_trip_guard_proj_post,
                                                                   const Node_List &old_new) {
  if (predicate_block->has_parse_predicate()) {
#ifdef ASSERT
    ensure_zero_trip_guard_proj(zero_trip_guard_proj_main, true);
    ensure_zero_trip_guard_proj(zero_trip_guard_proj_post, false);
#endif
    Node* predicate_proj = predicate_block->parse_predicate_success_proj();
    IfNode* iff = predicate_proj->in(0)->as_If();
    ProjNode* uncommon_proj = iff->proj_out(1 - predicate_proj->as_Proj()->_con);
    Node* rgn = uncommon_proj->unique_ctrl_out();
    assert(rgn->is_Region() || rgn->is_Call(), "must be a region or call uct");
    predicate_proj = iff->in(0);
    Node* current_proj = outer_main_head->in(LoopNode::EntryControl);
    Node* prev_proj = current_proj;
    Node* opaque_init = new OpaqueLoopInitNode(C, init);
    register_new_node(opaque_init, outer_main_head->in(LoopNode::EntryControl));
    Node* opaque_stride = new OpaqueLoopStrideNode(C, stride);
    register_new_node(opaque_stride, outer_main_head->in(LoopNode::EntryControl));

    while (predicate_proj != nullptr && predicate_proj->is_Proj() && predicate_proj->in(0)->is_If()) {
      iff = predicate_proj->in(0)->as_If();
      uncommon_proj = iff->proj_out(1 - predicate_proj->as_Proj()->_con);
      if (uncommon_proj->unique_ctrl_out() != rgn)
        break;
      if (iff->in(1)->Opcode() == Op_Opaque4) {
        assert(assertion_predicate_has_loop_opaque_node(iff), "unexpected");
        // Clone the Assertion Predicate twice and initialize one with the initial
        // value of the loop induction variable. Leave the other predicate
        // to be initialized when increasing the stride during loop unrolling.
        prev_proj = clone_assertion_predicate_and_initialize(iff, opaque_init, nullptr, predicate_proj, uncommon_proj,
                                                             current_proj, outer_loop, prev_proj);
        assert(assertion_predicate_has_loop_opaque_node(prev_proj->in(0)->as_If()), "");

        prev_proj = clone_assertion_predicate_and_initialize(iff, init, stride, predicate_proj, uncommon_proj,
                                                             current_proj, outer_loop, prev_proj);
        assert(!assertion_predicate_has_loop_opaque_node(prev_proj->in(0)->as_If()), "");

        // Rewire any control inputs from the cloned Assertion Predicates down to the main and post loop for data nodes
        // that are part of the main loop (and were cloned to the pre and post loop).
        for (DUIterator i = predicate_proj->outs(); predicate_proj->has_out(i); i++) {
          Node* loop_node = predicate_proj->out(i);
          Node* pre_loop_node = old_new[loop_node->_idx];
          // Change the control if 'loop_node' is part of the main loop. If there is an old->new mapping and the index of
          // 'pre_loop_node' is greater than idx_before_pre_post, then we know that 'loop_node' was cloned and is part of
          // the main loop (and 'pre_loop_node' is part of the pre loop).
          if (!loop_node->is_CFG() && (pre_loop_node != nullptr && pre_loop_node->_idx > idx_after_post_before_pre)) {
            // 'loop_node' is a data node and part of the main loop. Rewire the control to the projection of the zero-trip guard if node
            // of the main loop that is immediately preceding the cloned predicates.
            _igvn.replace_input_of(loop_node, 0, zero_trip_guard_proj_main);
            --i;
          } else if (loop_node->_idx > idx_before_pre_post && loop_node->_idx < idx_after_post_before_pre) {
            // 'loop_node' is a data node and part of the post loop. Rewire the control to the projection of the zero-trip guard if node
            // of the post loop that is immediately preceding the post loop header node (there are no cloned predicates for the post loop).
            assert(pre_loop_node == nullptr, "a node belonging to the post loop should not have an old_new mapping at this stage");
            _igvn.replace_input_of(loop_node, 0, zero_trip_guard_proj_post);
            --i;
          }
        }

        // Remove the Assertion Predicate from the pre-loop
        _igvn.replace_input_of(iff, 1, _igvn.intcon(1));
      }
      predicate_proj = predicate_proj->in(0)->in(0);
    }
    _igvn.replace_input_of(outer_main_head, LoopNode::EntryControl, prev_proj);
    set_idom(outer_main_head, prev_proj, dd_main_head);
  }
}

// Is 'n' a node that can be found on the input chain of a Template Assertion Predicate bool (i.e. between a Template
// Assertion Predicate If node and the OpaqueLoop* nodes)?
static bool is_part_of_template_assertion_predicate_bool(Node* n) {
  int op = n->Opcode();
  return (n->is_Bool() ||
          n->is_Cmp() ||
          op == Op_AndL ||
          op == Op_OrL ||
          op == Op_RShiftL ||
          op == Op_LShiftL ||
          op == Op_LShiftI ||
          op == Op_AddL ||
          op == Op_AddI ||
          op == Op_MulL ||
          op == Op_MulI ||
          op == Op_SubL ||
          op == Op_SubI ||
          op == Op_ConvI2L ||
          op == Op_CastII);
}

bool PhaseIdealLoop::subgraph_has_opaque(Node* n) {
  if (n->Opcode() == Op_OpaqueLoopInit || n->Opcode() == Op_OpaqueLoopStride) {
    return true;
  }
  if (!is_part_of_template_assertion_predicate_bool(n)) {
    return false;
  }
  uint init;
  uint stride;
  count_opaque_loop_nodes(n, init, stride);
  return init != 0 || stride != 0;
}

bool PhaseIdealLoop::assertion_predicate_has_loop_opaque_node(IfNode* iff) {
  uint init;
  uint stride;
  count_opaque_loop_nodes(iff->in(1)->in(1), init, stride);
#ifdef ASSERT
  ResourceMark rm;
  Unique_Node_List wq;
  wq.clear();
  wq.push(iff->in(1)->in(1));
  uint verif_init = 0;
  uint verif_stride = 0;
  for (uint i = 0; i < wq.size(); i++) {
    Node* n = wq.at(i);
    int op = n->Opcode();
    if (!n->is_CFG()) {
      if (n->Opcode() == Op_OpaqueLoopInit) {
        verif_init++;
      } else if (n->Opcode() == Op_OpaqueLoopStride) {
        verif_stride++;
      } else {
        for (uint j = 1; j < n->req(); j++) {
          Node* m = n->in(j);
          if (m != nullptr) {
            wq.push(m);
          }
        }
      }
    }
  }
  assert(init == verif_init && stride == verif_stride, "missed opaque node");
#endif
  assert(stride == 0 || init != 0, "init should be there every time stride is");
  return init != 0;
}

void PhaseIdealLoop::count_opaque_loop_nodes(Node* n, uint& init, uint& stride) {
  init = 0;
  stride = 0;
  ResourceMark rm;
  Unique_Node_List wq;
  wq.push(n);
  for (uint i = 0; i < wq.size(); i++) {
    Node* n = wq.at(i);
    if (is_part_of_template_assertion_predicate_bool(n)) {
      for (uint j = 1; j < n->req(); j++) {
        Node* m = n->in(j);
        if (m != nullptr) {
          wq.push(m);
        }
      }
      continue;
    }
    if (n->Opcode() == Op_OpaqueLoopInit) {
      init++;
    } else if (n->Opcode() == Op_OpaqueLoopStride) {
      stride++;
    }
  }
}

// Create a new Bool node from the provided Template Assertion Predicate.
// Unswitched loop: new_init and new_stride are both null. Clone OpaqueLoopInit and OpaqueLoopStride.
// Otherwise: Replace found OpaqueLoop* nodes with new_init and new_stride, respectively.
Node* PhaseIdealLoop::create_bool_from_template_assertion_predicate(Node* template_assertion_predicate, Node* new_init,
                                                                    Node* new_stride, Node* control) {
  Node_Stack to_clone(2);
  Node* opaque4 = template_assertion_predicate->in(1);
  assert(opaque4->Opcode() == Op_Opaque4, "must be Opaque4");
  to_clone.push(opaque4, 1);
  uint current = C->unique();
  Node* result = nullptr;
  bool is_unswitched_loop = new_init == nullptr && new_stride == nullptr;
  assert(new_init != nullptr || is_unswitched_loop, "new_init must be set when new_stride is non-null");
  // Look for the opaque node to replace with the new value
  // and clone everything in between. We keep the Opaque4 node
  // so the duplicated predicates are eliminated once loop
  // opts are over: they are here only to keep the IR graph
  // consistent.
  do {
    Node* n = to_clone.node();
    uint i = to_clone.index();
    Node* m = n->in(i);
    if (is_part_of_template_assertion_predicate_bool(m)) {
      to_clone.push(m, 1);
      continue;
    }
    if (m->is_Opaque1()) {
      if (n->_idx < current) {
        n = n->clone();
        register_new_node(n, control);
      }
      int op = m->Opcode();
      if (op == Op_OpaqueLoopInit) {
        if (is_unswitched_loop && m->_idx < current && new_init == nullptr) {
          new_init = m->clone();
          register_new_node(new_init, control);
        }
        n->set_req(i, new_init);
      } else {
        assert(op == Op_OpaqueLoopStride, "unexpected opaque node");
        if (is_unswitched_loop && m->_idx < current && new_stride == nullptr) {
          new_stride = m->clone();
          register_new_node(new_stride, control);
        }
        if (new_stride != nullptr) {
          n->set_req(i, new_stride);
        }
      }
      to_clone.set_node(n);
    }
    while (true) {
      Node* cur = to_clone.node();
      uint j = to_clone.index();
      if (j+1 < cur->req()) {
        to_clone.set_index(j+1);
        break;
      }
      to_clone.pop();
      if (to_clone.size() == 0) {
        result = cur;
        break;
      }
      Node* next = to_clone.node();
      j = to_clone.index();
      if (next->in(j) != cur) {
        assert(cur->_idx >= current || next->in(j)->Opcode() == Op_Opaque1, "new node or Opaque1 being replaced");
        if (next->_idx < current) {
          next = next->clone();
          register_new_node(next, control);
          to_clone.set_node(next);
        }
        next->set_req(j, cur);
      }
    }
  } while (result == nullptr);
  assert(result->_idx >= current, "new node expected");
  assert(!is_unswitched_loop || new_init != nullptr, "new_init must always be found and cloned");
  return result;
}

// Clone an Assertion Predicate for the main loop. new_init and new_stride are set as new inputs. Since the predicates
// cannot fail at runtime, Halt nodes are inserted instead of uncommon traps.
Node* PhaseIdealLoop::clone_assertion_predicate_and_initialize(Node* iff, Node* new_init, Node* new_stride, Node* predicate,
                                                               Node* uncommon_proj, Node* control, IdealLoopTree* outer_loop,
                                                               Node* input_proj) {
  Node* result = create_bool_from_template_assertion_predicate(iff, new_init, new_stride, control);
  Node* proj = predicate->clone();
  Node* other_proj = uncommon_proj->clone();
  Node* new_iff = iff->clone();
  new_iff->set_req(1, result);
  proj->set_req(0, new_iff);
  other_proj->set_req(0, new_iff);
  Node* frame = new ParmNode(C->start(), TypeFunc::FramePtr);
  register_new_node(frame, C->start());
  // It's impossible for the predicate to fail at runtime. Use a Halt node.
  Node* halt = new HaltNode(other_proj, frame, "duplicated predicate failed which is impossible");
  _igvn.add_input_to(C->root(), halt);
  new_iff->set_req(0, input_proj);

  register_control(new_iff, outer_loop == _ltree_root ? _ltree_root : outer_loop->_parent, input_proj);
  register_control(proj, outer_loop == _ltree_root ? _ltree_root : outer_loop->_parent, new_iff);
  register_control(other_proj, _ltree_root, new_iff);
  register_control(halt, _ltree_root, other_proj);
  return proj;
}

void PhaseIdealLoop::copy_assertion_predicates_to_main_loop(CountedLoopNode* pre_head, Node* init, Node* stride,
                                                            IdealLoopTree* outer_loop, LoopNode* outer_main_head,
                                                            const uint dd_main_head, const uint idx_before_pre_post,
                                                            const uint idx_after_post_before_pre,
                                                            Node* zero_trip_guard_proj_main,
                                                            Node* zero_trip_guard_proj_post,
                                                            const Node_List &old_new) {
  if (UseLoopPredicate) {
    Node* entry = pre_head->in(LoopNode::EntryControl);
    const Predicates predicates(entry);
    copy_assertion_predicates_to_main_loop_helper(predicates.loop_predicate_block(), init, stride, outer_loop,
                                                  outer_main_head, dd_main_head, idx_before_pre_post,
                                                  idx_after_post_before_pre, zero_trip_guard_proj_main,
                                                  zero_trip_guard_proj_post, old_new);
    copy_assertion_predicates_to_main_loop_helper(predicates.profiled_loop_predicate_block(), init, stride,
                                                  outer_loop, outer_main_head, dd_main_head, idx_before_pre_post,
                                                  idx_after_post_before_pre, zero_trip_guard_proj_main,
                                                  zero_trip_guard_proj_post, old_new);
  }
}

//------------------------------insert_pre_post_loops--------------------------
// Insert pre and post loops.  If peel_only is set, the pre-loop can not have
// more iterations added.  It acts as a 'peel' only, no lower-bound RCE, no
// alignment.  Useful to unroll loops that do no array accesses.
void PhaseIdealLoop::insert_pre_post_loops(IdealLoopTree *loop, Node_List &old_new, bool peel_only) {

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
  assert(main_head->is_normal_loop(), "");
  CountedLoopEndNode *main_end = main_head->loopexit();
  assert(main_end->outcnt() == 2, "1 true, 1 false path only");

  Node *pre_header= main_head->in(LoopNode::EntryControl);
  Node *init      = main_head->init_trip();
  Node *incr      = main_end ->incr();
  Node *limit     = main_end ->limit();
  Node *stride    = main_end ->stride();
  Node *cmp       = main_end ->cmp_node();
  BoolTest::mask b_test = main_end->test_trip();

  // Need only 1 user of 'bol' because I will be hacking the loop bounds.
  Node *bol = main_end->in(CountedLoopEndNode::TestValue);
  if (bol->outcnt() != 1) {
    bol = bol->clone();
    register_new_node(bol,main_end->in(CountedLoopEndNode::TestControl));
    _igvn.replace_input_of(main_end, CountedLoopEndNode::TestValue, bol);
  }
  // Need only 1 user of 'cmp' because I will be hacking the loop bounds.
  if (cmp->outcnt() != 1) {
    cmp = cmp->clone();
    register_new_node(cmp,main_end->in(CountedLoopEndNode::TestControl));
    _igvn.replace_input_of(bol, 1, cmp);
  }

  // Add the post loop
  const uint idx_before_pre_post = Compile::current()->unique();
  CountedLoopNode *post_head = nullptr;
  Node* post_incr = incr;
  Node* main_exit = insert_post_loop(loop, old_new, main_head, main_end, post_incr, limit, post_head);
  const uint idx_after_post_before_pre = Compile::current()->unique();

  //------------------------------
  // Step B: Create Pre-Loop.

  // Step B1: Clone the loop body.  The clone becomes the pre-loop.  The main
  // loop pre-header illegally has 2 control users (old & new loops).
  LoopNode* outer_main_head = main_head;
  IdealLoopTree* outer_loop = loop;
  if (main_head->is_strip_mined()) {
    main_head->verify_strip_mined(1);
    outer_main_head = main_head->outer_loop();
    outer_loop = loop->_parent;
    assert(outer_loop->_head == outer_main_head, "broken loop tree");
  }
  uint dd_main_head = dom_depth(outer_main_head);
  clone_loop(loop, old_new, dd_main_head, ControlAroundStripMined);
  CountedLoopNode*    pre_head = old_new[main_head->_idx]->as_CountedLoop();
  CountedLoopEndNode* pre_end  = old_new[main_end ->_idx]->as_CountedLoopEnd();
  pre_head->set_pre_loop(main_head);
  Node *pre_incr = old_new[incr->_idx];

  // Reduce the pre-loop trip count.
  pre_end->_prob = PROB_FAIR;

  // Find the pre-loop normal exit.
  Node* pre_exit = pre_end->proj_out(false);
  assert(pre_exit->Opcode() == Op_IfFalse, "");
  IfFalseNode *new_pre_exit = new IfFalseNode(pre_end);
  _igvn.register_new_node_with_optimizer(new_pre_exit);
  set_idom(new_pre_exit, pre_end, dd_main_head);
  set_loop(new_pre_exit, outer_loop->_parent);

  // Step B2: Build a zero-trip guard for the main-loop.  After leaving the
  // pre-loop, the main-loop may not execute at all.  Later in life this
  // zero-trip guard will become the minimum-trip guard when we unroll
  // the main-loop.
  Node *min_opaq = new OpaqueZeroTripGuardNode(C, limit, b_test);
  Node *min_cmp  = new CmpINode(pre_incr, min_opaq);
  Node *min_bol  = new BoolNode(min_cmp, b_test);
  register_new_node(min_opaq, new_pre_exit);
  register_new_node(min_cmp , new_pre_exit);
  register_new_node(min_bol , new_pre_exit);

  // Build the IfNode (assume the main-loop is executed always).
  IfNode *min_iff = new IfNode(new_pre_exit, min_bol, PROB_ALWAYS, COUNT_UNKNOWN);
  _igvn.register_new_node_with_optimizer(min_iff);
  set_idom(min_iff, new_pre_exit, dd_main_head);
  set_loop(min_iff, outer_loop->_parent);

  // Plug in the false-path, taken if we need to skip main-loop
  _igvn.hash_delete(pre_exit);
  pre_exit->set_req(0, min_iff);
  set_idom(pre_exit, min_iff, dd_main_head);
  set_idom(pre_exit->unique_ctrl_out(), min_iff, dd_main_head);
  // Make the true-path, must enter the main loop
  Node *min_taken = new IfTrueNode(min_iff);
  _igvn.register_new_node_with_optimizer(min_taken);
  set_idom(min_taken, min_iff, dd_main_head);
  set_loop(min_taken, outer_loop->_parent);
  // Plug in the true path
  _igvn.hash_delete(outer_main_head);
  outer_main_head->set_req(LoopNode::EntryControl, min_taken);
  set_idom(outer_main_head, min_taken, dd_main_head);

  VectorSet visited;
  Node_Stack clones(main_head->back_control()->outcnt());
  // Step B3: Make the fall-in values to the main-loop come from the
  // fall-out values of the pre-loop.
  for (DUIterator i2 = main_head->outs(); main_head->has_out(i2); i2++) {
    Node* main_phi = main_head->out(i2);
    if (main_phi->is_Phi() && main_phi->in(0) == main_head && main_phi->outcnt() > 0) {
      Node* pre_phi = old_new[main_phi->_idx];
      Node* fallpre = clone_up_backedge_goo(pre_head->back_control(),
                                            main_head->skip_strip_mined()->in(LoopNode::EntryControl),
                                            pre_phi->in(LoopNode::LoopBackControl),
                                            visited, clones);
      _igvn.hash_delete(main_phi);
      main_phi->set_req(LoopNode::EntryControl, fallpre);
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

  // CastII for the main loop:
  Node* castii = cast_incr_before_loop(pre_incr, min_taken, main_head);
  assert(castii != nullptr, "no castII inserted");
  assert(post_head->in(1)->is_IfProj(), "must be zero-trip guard If node projection of the post loop");
  copy_assertion_predicates_to_main_loop(pre_head, castii, stride, outer_loop, outer_main_head, dd_main_head,
                                         idx_before_pre_post, idx_after_post_before_pre, min_taken, post_head->in(1),
                                         old_new);
  copy_assertion_predicates_to_post_loop(outer_main_head, post_head, post_incr, stride);

  // Step B4: Shorten the pre-loop to run only 1 iteration (for now).
  // RCE and alignment may change this later.
  Node *cmp_end = pre_end->cmp_node();
  assert(cmp_end->in(2) == limit, "");
  Node *pre_limit = new AddINode(init, stride);

  // Save the original loop limit in this Opaque1 node for
  // use by range check elimination.
  Node *pre_opaq  = new Opaque1Node(C, pre_limit, limit);

  register_new_node(pre_limit, pre_head->in(0));
  register_new_node(pre_opaq , pre_head->in(0));

  // Since no other users of pre-loop compare, I can hack limit directly
  assert(cmp_end->outcnt() == 1, "no other users");
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
    register_new_node(new_bol0, pre_head->in(0));
    _igvn.replace_input_of(pre_end, CountedLoopEndNode::TestValue, new_bol0);
    // Modify main loop guard condition
    assert(min_iff->in(CountedLoopEndNode::TestValue) == min_bol, "guard okay");
    BoolNode* new_bol1 = new BoolNode(min_bol->in(1), new_test);
    register_new_node(new_bol1, new_pre_exit);
    _igvn.hash_delete(min_iff);
    min_iff->set_req(CountedLoopEndNode::TestValue, new_bol1);
    // Modify main loop end condition
    BoolNode* main_bol = main_end->in(CountedLoopEndNode::TestValue)->as_Bool();
    BoolNode* new_bol2 = new BoolNode(main_bol->in(1), new_test);
    register_new_node(new_bol2, main_end->in(CountedLoopEndNode::TestControl));
    _igvn.replace_input_of(main_end, CountedLoopEndNode::TestValue, new_bol2);
  }

  // Flag main loop
  main_head->set_main_loop();
  if (peel_only) {
    main_head->set_main_no_pre_loop();
  }

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
// unroll_policy has  already informed  us that more  unrolling is  about to
// happen  to the  main  loop.  The  resultant  post loop  will  serve as  a
// vectorized drain loop.
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

  if (!may_require_nodes(loop->est_loop_clone_sz(2))) {
    return;
  }

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
  // diagnostic to show loop end is not properly formed
  assert(main_end->outcnt() == 2, "1 true, 1 false path only");

  // mark this loop as processed
  main_head->mark_has_atomic_post_loop();

  Node *incr = main_end->incr();
  Node *limit = main_end->limit();

  // In this case we throw away the result as we are not using it to connect anything else.
  CountedLoopNode *post_head = nullptr;
  insert_post_loop(loop, old_new, main_head, main_end, incr, limit, post_head);
  copy_assertion_predicates_to_post_loop(main_head->skip_strip_mined(), post_head, incr, main_head->stride());

  // It's difficult to be precise about the trip-counts
  // for post loops.  They are usually very short,
  // so guess that unit vector trips is a reasonable value.
  post_head->set_profile_trip_cnt(cur_unroll);

  // Now force out all loop-invariant dominating tests.  The optimizer
  // finds some, but we _know_ they are all useless.
  peeled_dom_test_elim(loop, old_new);
  loop->record_for_igvn();
}

//------------------------------insert_post_loop-------------------------------
// Insert post loops.  Add a post loop to the given loop passed.
Node *PhaseIdealLoop::insert_post_loop(IdealLoopTree* loop, Node_List& old_new,
                                       CountedLoopNode* main_head, CountedLoopEndNode* main_end,
                                       Node*& incr, Node* limit, CountedLoopNode*& post_head) {
  IfNode* outer_main_end = main_end;
  IdealLoopTree* outer_loop = loop;
  if (main_head->is_strip_mined()) {
    main_head->verify_strip_mined(1);
    outer_main_end = main_head->outer_loop_end();
    outer_loop = loop->_parent;
    assert(outer_loop->_head == main_head->in(LoopNode::EntryControl), "broken loop tree");
  }

  //------------------------------
  // Step A: Create a new post-Loop.
  Node* main_exit = outer_main_end->proj_out(false);
  assert(main_exit->Opcode() == Op_IfFalse, "");
  int dd_main_exit = dom_depth(main_exit);

  // Step A1: Clone the loop body of main. The clone becomes the post-loop.
  // The main loop pre-header illegally has 2 control users (old & new loops).
  clone_loop(loop, old_new, dd_main_exit, ControlAroundStripMined);
  assert(old_new[main_end->_idx]->Opcode() == Op_CountedLoopEnd, "");
  post_head = old_new[main_head->_idx]->as_CountedLoop();
  post_head->set_normal_loop();
  post_head->set_post_loop(main_head);

  // Reduce the post-loop trip count.
  CountedLoopEndNode* post_end = old_new[main_end->_idx]->as_CountedLoopEnd();
  post_end->_prob = PROB_FAIR;

  // Build the main-loop normal exit.
  IfFalseNode *new_main_exit = new IfFalseNode(outer_main_end);
  _igvn.register_new_node_with_optimizer(new_main_exit);
  set_idom(new_main_exit, outer_main_end, dd_main_exit);
  set_loop(new_main_exit, outer_loop->_parent);

  // Step A2: Build a zero-trip guard for the post-loop.  After leaving the
  // main-loop, the post-loop may not execute at all.  We 'opaque' the incr
  // (the previous loop trip-counter exit value) because we will be changing
  // the exit value (via additional unrolling) so we cannot constant-fold away the zero
  // trip guard until all unrolling is done.
  Node *zer_opaq = new OpaqueZeroTripGuardNode(C, incr, main_end->test_trip());
  Node *zer_cmp = new CmpINode(zer_opaq, limit);
  Node *zer_bol = new BoolNode(zer_cmp, main_end->test_trip());
  register_new_node(zer_opaq, new_main_exit);
  register_new_node(zer_cmp, new_main_exit);
  register_new_node(zer_bol, new_main_exit);

  // Build the IfNode
  IfNode *zer_iff = new IfNode(new_main_exit, zer_bol, PROB_FAIR, COUNT_UNKNOWN);
  _igvn.register_new_node_with_optimizer(zer_iff);
  set_idom(zer_iff, new_main_exit, dd_main_exit);
  set_loop(zer_iff, outer_loop->_parent);

  // Plug in the false-path, taken if we need to skip this post-loop
  _igvn.replace_input_of(main_exit, 0, zer_iff);
  set_idom(main_exit, zer_iff, dd_main_exit);
  set_idom(main_exit->unique_out(), zer_iff, dd_main_exit);
  // Make the true-path, must enter this post loop
  Node *zer_taken = new IfTrueNode(zer_iff);
  _igvn.register_new_node_with_optimizer(zer_taken);
  set_idom(zer_taken, zer_iff, dd_main_exit);
  set_loop(zer_taken, outer_loop->_parent);
  // Plug in the true path
  _igvn.hash_delete(post_head);
  post_head->set_req(LoopNode::EntryControl, zer_taken);
  set_idom(post_head, zer_taken, dd_main_exit);

  VectorSet visited;
  Node_Stack clones(main_head->back_control()->outcnt());
  // Step A3: Make the fall-in values to the post-loop come from the
  // fall-out values of the main-loop.
  for (DUIterator i = main_head->outs(); main_head->has_out(i); i++) {
    Node* main_phi = main_head->out(i);
    if (main_phi->is_Phi() && main_phi->in(0) == main_head && main_phi->outcnt() > 0) {
      Node* cur_phi = old_new[main_phi->_idx];
      Node* fallnew = clone_up_backedge_goo(main_head->back_control(),
                                            post_head->init_control(),
                                            main_phi->in(LoopNode::LoopBackControl),
                                            visited, clones);
      _igvn.hash_delete(cur_phi);
      cur_phi->set_req(LoopNode::EntryControl, fallnew);
    }
  }

  // CastII for the new post loop:
  incr = cast_incr_before_loop(zer_opaq->in(1), zer_taken, post_head);
  assert(incr != nullptr, "no castII inserted");

  return new_main_exit;
}

//------------------------------is_invariant-----------------------------
// Return true if n is invariant
bool IdealLoopTree::is_invariant(Node* n) const {
  Node *n_c = _phase->has_ctrl(n) ? _phase->get_ctrl(n) : n;
  if (n_c->is_top()) return false;
  return !is_member(_phase->get_loop(n_c));
}

// Search the Assertion Predicates added by loop predication and/or range check elimination and update them according
// to the new stride.
void PhaseIdealLoop::update_main_loop_assertion_predicates(Node* ctrl, CountedLoopNode* loop_head, Node* init,
                                                           const int stride_con) {
  Node* entry = ctrl;
  Node* prev_proj = ctrl;
  LoopNode* outer_loop_head = loop_head->skip_strip_mined();
  IdealLoopTree* outer_loop = get_loop(outer_loop_head);

  // Compute the value of the loop induction variable at the end of the
  // first iteration of the unrolled loop: init + new_stride_con - init_inc
  int new_stride_con = stride_con * 2;
  Node* max_value = _igvn.intcon(new_stride_con);
  set_ctrl(max_value, C->root());

  while (entry != nullptr && entry->is_Proj() && entry->in(0)->is_If()) {
    IfNode* iff = entry->in(0)->as_If();
    ProjNode* proj = iff->proj_out(1 - entry->as_Proj()->_con);
    if (proj->unique_ctrl_out()->Opcode() != Op_Halt) {
      break;
    }
    if (iff->in(1)->Opcode() == Op_Opaque4) {
      if (!assertion_predicate_has_loop_opaque_node(iff)) {
        // No OpaqueLoop* node? Then it's one of the two Initialized Assertion Predicates:
        // - For the initial access a[init]
        // - For the last access a[init+old_stride-orig_stride]
        // We could keep the one for the initial access but we do not know which one we currently have here. Just kill both.
        // We will create new Initialized Assertion Predicates from the Template Assertion Predicates below:
        // - For the initial access a[init] (same as before)
        // - For the last access a[init+new_stride-orig_stride] (with the new unroll stride)
        _igvn.replace_input_of(iff, 1, iff->in(1)->in(2));
      } else {
        // Template Assertion Predicate: Clone it to create initialized version with new stride.
        prev_proj = clone_assertion_predicate_and_initialize(iff, init, max_value, entry, proj, ctrl, outer_loop,
                                                             prev_proj);
        assert(!assertion_predicate_has_loop_opaque_node(prev_proj->in(0)->as_If()), "unexpected");
      }
    }
    entry = entry->in(0)->in(0);
  }
  if (prev_proj != ctrl) {
    _igvn.replace_input_of(outer_loop_head, LoopNode::EntryControl, prev_proj);
    set_idom(outer_loop_head, prev_proj, dom_depth(outer_loop_head));
  }
}

// Go over the Assertion Predicates of the main loop and make a copy for the post loop with its initial iv value and
// stride as inputs.
void PhaseIdealLoop::copy_assertion_predicates_to_post_loop(LoopNode* main_loop_head, CountedLoopNode* post_loop_head,
                                                            Node* init, Node* stride) {
  Node* post_loop_entry = post_loop_head->in(LoopNode::EntryControl);
  Node* main_loop_entry = main_loop_head->in(LoopNode::EntryControl);
  IdealLoopTree* post_loop = get_loop(post_loop_head);

  Node* ctrl = main_loop_entry;
  Node* prev_proj = post_loop_entry;
  while (ctrl != nullptr && ctrl->is_Proj() && ctrl->in(0)->is_If()) {
    IfNode* iff = ctrl->in(0)->as_If();
    ProjNode* proj = iff->proj_out(1 - ctrl->as_Proj()->_con);
    if (proj->unique_ctrl_out()->Opcode() != Op_Halt) {
      break;
    }
    if (iff->in(1)->Opcode() == Op_Opaque4 && assertion_predicate_has_loop_opaque_node(iff)) {
      prev_proj = clone_assertion_predicate_and_initialize(iff, init, stride, ctrl, proj, post_loop_entry,
                                                           post_loop, prev_proj);
      assert(!assertion_predicate_has_loop_opaque_node(prev_proj->in(0)->as_If()), "unexpected");
    }
    ctrl = ctrl->in(0)->in(0);
  }
  if (prev_proj != post_loop_entry) {
    _igvn.replace_input_of(post_loop_head, LoopNode::EntryControl, prev_proj);
    set_idom(post_loop_head, prev_proj, dom_depth(post_loop_head));
  }
}

void PhaseIdealLoop::initialize_assertion_predicates_for_peeled_loop(const PredicateBlock* predicate_block,
                                                                     LoopNode* outer_loop_head,
                                                                     const int dd_outer_loop_head, Node* init,
                                                                     Node* stride, IdealLoopTree* outer_loop,
                                                                     const uint idx_before_clone,
                                                                     const Node_List &old_new) {
  if (!predicate_block->has_parse_predicate()) {
    return;
  }
  Node* control = outer_loop_head->in(LoopNode::EntryControl);
  Node* input_proj = control;

  const Node* parse_predicate_uncommon_trap = predicate_block->parse_predicate()->uncommon_trap();
  Node* next_regular_predicate_proj = predicate_block->skip_parse_predicate();
  while (next_regular_predicate_proj->is_IfProj()) {
    IfNode* iff = next_regular_predicate_proj->in(0)->as_If();
    ProjNode* uncommon_proj = iff->proj_out(1 - next_regular_predicate_proj->as_Proj()->_con);
    if (uncommon_proj->unique_ctrl_out() != parse_predicate_uncommon_trap) {
      // Does not belong to this Predicate Block anymore.
      break;
    }
    if (iff->in(1)->Opcode() == Op_Opaque4) {
      assert(assertion_predicate_has_loop_opaque_node(iff), "unexpected");
      input_proj = clone_assertion_predicate_and_initialize(iff, init, stride, next_regular_predicate_proj, uncommon_proj, control,
                                                            outer_loop, input_proj);

      // Rewire any control inputs from the old Assertion Predicates above the peeled iteration down to the initialized
      // Assertion Predicates above the peeled loop.
      for (DUIterator i = next_regular_predicate_proj->outs(); next_regular_predicate_proj->has_out(i); i++) {
        Node* dependent = next_regular_predicate_proj->out(i);
        Node* new_node = old_new[dependent->_idx];

        if (!dependent->is_CFG() &&
            dependent->_idx < idx_before_clone &&  // old node
            new_node != nullptr &&                 // cloned
            new_node->_idx >= idx_before_clone) {  // for peeling
          // The old nodes from the peeled loop still point to the predicate above the peeled loop.
          // We need to rewire the dependencies to the newly Initialized Assertion Predicates.
          _igvn.replace_input_of(dependent, 0, input_proj);
          --i; // correct for just deleted predicate->out(i)
        }
      }
    }
    next_regular_predicate_proj = iff->in(0);
  }

  _igvn.replace_input_of(outer_loop_head, LoopNode::EntryControl, input_proj);
  set_idom(outer_loop_head, input_proj, dd_outer_loop_head);
}

//------------------------------do_unroll--------------------------------------
// Unroll the loop body one step - make each trip do 2 iterations.
void PhaseIdealLoop::do_unroll(IdealLoopTree *loop, Node_List &old_new, bool adjust_min_trip) {
  assert(LoopUnrollLimit, "");
  CountedLoopNode *loop_head = loop->_head->as_CountedLoop();
  CountedLoopEndNode *loop_end = loop_head->loopexit();
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

  if (C->do_vector_loop() && (PrintOpto && (VerifyLoopOptimizations || TraceLoopOpts))) {
    Node_Stack stack(C->live_nodes() >> 2);
    Node_List rpo_list;
    VectorSet visited;
    visited.set(loop_head->_idx);
    rpo(loop_head, stack, visited, rpo_list);
    dump(loop, rpo_list.size(), rpo_list);
  }
#endif

  // Remember loop node count before unrolling to detect
  // if rounds of unroll,optimize are making progress
  loop_head->set_node_count_before_unroll(loop->_body.size());

  Node *ctrl  = loop_head->skip_strip_mined()->in(LoopNode::EntryControl);
  Node *limit = loop_head->limit();
  Node *init  = loop_head->init_trip();
  Node *stride = loop_head->stride();

  Node *opaq = nullptr;
  if (adjust_min_trip) {       // If not maximally unrolling, need adjustment
    // Search for zero-trip guard.

    // Check the shape of the graph at the loop entry. If an inappropriate
    // graph shape is encountered, the compiler bails out loop unrolling;
    // compilation of the method will still succeed.
    opaq = loop_head->is_canonical_loop_entry();
    if (opaq == nullptr) {
      return;
    }
    // Zero-trip test uses an 'opaque' node which is not shared.
    assert(opaq->outcnt() == 1 && opaq->in(1) == limit, "");
  }

  C->set_major_progress();

  Node* new_limit = nullptr;
  int stride_con = stride->get_int();
  int stride_p = (stride_con > 0) ? stride_con : -stride_con;
  uint old_trip_count = loop_head->trip_count();
  // Verify that unroll policy result is still valid.
  assert(old_trip_count > 1 && (!adjust_min_trip || stride_p <=
    MIN2<int>(max_jint / 2 - 2, MAX2(1<<3, Matcher::max_vector_size(T_BYTE)) * loop_head->unrolled_count())), "sanity");

  update_main_loop_assertion_predicates(ctrl, loop_head, init, stride_con);

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
    assert(opaq != nullptr && opaq->in(1) == limit, "sanity");

    // Verify that policy_unroll result is still valid.
    const TypeInt* limit_type = _igvn.type(limit)->is_int();
    assert((stride_con > 0 && ((min_jint + stride_con) <= limit_type->_hi)) ||
           (stride_con < 0 && ((max_jint + stride_con) >= limit_type->_lo)),
           "sanity");

    if (limit->is_Con()) {
      // The check in policy_unroll and the assert above guarantee
      // no underflow if limit is constant.
      new_limit = _igvn.intcon(limit->get_int() - stride_con);
      set_ctrl(new_limit, C->root());
    } else {
      // Limit is not constant. Int subtraction could lead to underflow.
      // (1) Convert to long.
      Node* limit_l = new ConvI2LNode(limit);
      register_new_node(limit_l, get_ctrl(limit));
      Node* stride_l = _igvn.longcon(stride_con);
      set_ctrl(stride_l, C->root());

      // (2) Subtract: compute in long, to prevent underflow.
      Node* new_limit_l = new SubLNode(limit_l, stride_l);
      register_new_node(new_limit_l, ctrl);

      // (3) Clamp to int range, in case we had subtraction underflow.
      Node* underflow_clamp_l = _igvn.longcon((stride_con > 0) ? min_jint : max_jint);
      set_ctrl(underflow_clamp_l, C->root());
      Node* new_limit_no_underflow_l = nullptr;
      if (stride_con > 0) {
        // limit = MaxL(limit - stride, min_jint)
        new_limit_no_underflow_l = new MaxLNode(C, new_limit_l, underflow_clamp_l);
      } else {
        // limit = MinL(limit - stride, max_jint)
        new_limit_no_underflow_l = new MinLNode(C, new_limit_l, underflow_clamp_l);
      }
      register_new_node(new_limit_no_underflow_l, ctrl);

      // (4) Convert back to int.
      new_limit = new ConvL2INode(new_limit_no_underflow_l);
      register_new_node(new_limit, ctrl);
    }

    assert(new_limit != nullptr, "");
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

  // ---------
  // Step 4: Clone the loop body.  Move it inside the loop.  This loop body
  // represents the odd iterations; since the loop trips an even number of
  // times its backedge is never taken.  Kill the backedge.
  uint dd = dom_depth(loop_head);
  clone_loop(loop, old_new, dd, IgnoreStripMined);

  // Make backedges of the clone equal to backedges of the original.
  // Make the fall-in from the original come from the fall-out of the clone.
  for (DUIterator_Fast jmax, j = loop_head->fast_outs(jmax); j < jmax; j++) {
    Node* phi = loop_head->fast_out(j);
    if (phi->is_Phi() && phi->in(0) == loop_head && phi->outcnt() > 0) {
      Node *newphi = old_new[phi->_idx];
      _igvn.hash_delete(phi);
      _igvn.hash_delete(newphi);

      phi   ->set_req(LoopNode::   EntryControl, newphi->in(LoopNode::LoopBackControl));
      newphi->set_req(LoopNode::LoopBackControl, phi   ->in(LoopNode::LoopBackControl));
      phi   ->set_req(LoopNode::LoopBackControl, C->top());
    }
  }
  Node *clone_head = old_new[loop_head->_idx];
  _igvn.hash_delete(clone_head);
  loop_head ->set_req(LoopNode::   EntryControl, clone_head->in(LoopNode::LoopBackControl));
  clone_head->set_req(LoopNode::LoopBackControl, loop_head ->in(LoopNode::LoopBackControl));
  loop_head ->set_req(LoopNode::LoopBackControl, C->top());
  loop->_head = clone_head;     // New loop header

  set_idom(loop_head,  loop_head ->in(LoopNode::EntryControl), dd);
  set_idom(clone_head, clone_head->in(LoopNode::EntryControl), dd);

  // Kill the clone's backedge
  Node *newcle = old_new[loop_end->_idx];
  _igvn.hash_delete(newcle);
  Node *one = _igvn.intcon(1);
  set_ctrl(one, C->root());
  newcle->set_req(1, one);
  // Force clone into same loop body
  uint max = loop->_body.size();
  for (uint k = 0; k < max; k++) {
    Node *old = loop->_body.at(k);
    Node *nnn = old_new[old->_idx];
    loop->_body.push(nnn);
    if (!has_ctrl(old)) {
      set_loop(nnn, loop);
    }
  }

  loop->record_for_igvn();
  loop_head->clear_strip_mined();

#ifndef PRODUCT
  if (C->do_vector_loop() && (PrintOpto && (VerifyLoopOptimizations || TraceLoopOpts))) {
    tty->print("\nnew loop after unroll\n");       loop->dump_head();
    for (uint i = 0; i < loop->_body.size(); i++) {
      loop->_body.at(i)->dump();
    }
    if (C->clone_map().is_debug()) {
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

void PhaseIdealLoop::do_maximally_unroll(IdealLoopTree *loop, Node_List &old_new) {
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

//------------------------------adjust_limit-----------------------------------
// Helper function that computes new loop limit as (rc_limit-offset)/scale
Node* PhaseIdealLoop::adjust_limit(bool is_positive_stride, Node* scale, Node* offset, Node* rc_limit, Node* old_limit, Node* pre_ctrl, bool round) {
  Node* old_limit_long = new ConvI2LNode(old_limit);
  register_new_node(old_limit_long, pre_ctrl);

  Node* sub = new SubLNode(rc_limit, offset);
  register_new_node(sub, pre_ctrl);
  Node* limit = new DivLNode(nullptr, sub, scale);
  register_new_node(limit, pre_ctrl);

  // When the absolute value of scale is greater than one, the division
  // may round limit down/up, so add/sub one to/from the limit.
  if (round) {
    limit = new AddLNode(limit, _igvn.longcon(is_positive_stride ? -1 : 1));
    register_new_node(limit, pre_ctrl);
  }

  // Clamp the limit to handle integer under-/overflows by using long values.
  // We only convert the limit back to int when we handled under-/overflows.
  // Note that all values are longs in the following computations.
  // When reducing the limit, clamp to [min_jint, old_limit]:
  //   INT(MINL(old_limit, MAXL(limit, min_jint)))
  //   - integer underflow of limit: MAXL chooses min_jint.
  //   - integer overflow of limit: MINL chooses old_limit (<= MAX_INT < limit)
  // When increasing the limit, clamp to [old_limit, max_jint]:
  //   INT(MAXL(old_limit, MINL(limit, max_jint)))
  //   - integer overflow of limit: MINL chooses max_jint.
  //   - integer underflow of limit: MAXL chooses old_limit (>= MIN_INT > limit)
  // INT() is finally converting the limit back to an integer value.

  Node* inner_result_long = nullptr;
  Node* outer_result_long = nullptr;
  if (is_positive_stride) {
    inner_result_long = new MaxLNode(C, limit, _igvn.longcon(min_jint));
    outer_result_long = new MinLNode(C, inner_result_long, old_limit_long);
  } else {
    inner_result_long = new MinLNode(C, limit, _igvn.longcon(max_jint));
    outer_result_long = new MaxLNode(C, inner_result_long, old_limit_long);
  }
  register_new_node(inner_result_long, pre_ctrl);
  register_new_node(outer_result_long, pre_ctrl);

  limit = new ConvL2INode(outer_result_long);
  register_new_node(limit, pre_ctrl);
  return limit;
}

//------------------------------add_constraint---------------------------------
// Constrain the main loop iterations so the conditions:
//    low_limit <= scale_con*I + offset < upper_limit
// always hold true. That is, either increase the number of iterations in the
// pre-loop or reduce the number of iterations in the main-loop until the condition
// holds true in the main-loop. Stride, scale, offset and limit are all loop
// invariant. Further, stride and scale are constants (offset and limit often are).
void PhaseIdealLoop::add_constraint(jlong stride_con, jlong scale_con, Node* offset, Node* low_limit, Node* upper_limit, Node* pre_ctrl, Node** pre_limit, Node** main_limit) {
  assert(_igvn.type(offset)->isa_long() != nullptr && _igvn.type(low_limit)->isa_long() != nullptr &&
         _igvn.type(upper_limit)->isa_long() != nullptr, "arguments should be long values");

  // For a positive stride, we need to reduce the main-loop limit and
  // increase the pre-loop limit. This is reversed for a negative stride.
  bool is_positive_stride = (stride_con > 0);

  // If the absolute scale value is greater one, division in 'adjust_limit' may require
  // rounding. Make sure the ABS method correctly handles min_jint.
  // Only do this for the pre-loop, one less iteration of the main loop doesn't hurt.
  bool round = ABS(scale_con) > 1;

  Node* scale = _igvn.longcon(scale_con);
  set_ctrl(scale, C->root());

  if ((stride_con^scale_con) >= 0) { // Use XOR to avoid overflow
    // Positive stride*scale: the affine function is increasing,
    // the pre-loop checks for underflow and the post-loop for overflow.

    // The overflow limit: scale*I+offset < upper_limit
    // For the main-loop limit compute:
    //   ( if (scale > 0) /* and stride > 0 */
    //       I < (upper_limit-offset)/scale
    //     else /* scale < 0 and stride < 0 */
    //       I > (upper_limit-offset)/scale
    //   )
    *main_limit = adjust_limit(is_positive_stride, scale, offset, upper_limit, *main_limit, pre_ctrl, false);

    // The underflow limit: low_limit <= scale*I+offset
    // For the pre-loop limit compute:
    //   NOT(scale*I+offset >= low_limit)
    //   scale*I+offset < low_limit
    //   ( if (scale > 0) /* and stride > 0 */
    //       I < (low_limit-offset)/scale
    //     else /* scale < 0 and stride < 0 */
    //       I > (low_limit-offset)/scale
    //   )
    *pre_limit = adjust_limit(!is_positive_stride, scale, offset, low_limit, *pre_limit, pre_ctrl, round);
  } else {
    // Negative stride*scale: the affine function is decreasing,
    // the pre-loop checks for overflow and the post-loop for underflow.

    // The overflow limit: scale*I+offset < upper_limit
    // For the pre-loop limit compute:
    //   NOT(scale*I+offset < upper_limit)
    //   scale*I+offset >= upper_limit
    //   scale*I+offset+1 > upper_limit
    //   ( if (scale < 0) /* and stride > 0 */
    //       I < (upper_limit-(offset+1))/scale
    //     else /* scale > 0 and stride < 0 */
    //       I > (upper_limit-(offset+1))/scale
    //   )
    Node* one = _igvn.longcon(1);
    set_ctrl(one, C->root());
    Node* plus_one = new AddLNode(offset, one);
    register_new_node(plus_one, pre_ctrl);
    *pre_limit = adjust_limit(!is_positive_stride, scale, plus_one, upper_limit, *pre_limit, pre_ctrl, round);

    // The underflow limit: low_limit <= scale*I+offset
    // For the main-loop limit compute:
    //   scale*I+offset+1 > low_limit
    //   ( if (scale < 0) /* and stride > 0 */
    //       I < (low_limit-(offset+1))/scale
    //     else /* scale > 0 and stride < 0 */
    //       I > (low_limit-(offset+1))/scale
    //   )
    *main_limit = adjust_limit(is_positive_stride, scale, plus_one, low_limit, *main_limit, pre_ctrl, false);
  }
}

//----------------------------------is_iv------------------------------------
// Return true if exp is the value (of type bt) of the given induction var.
// This grammar of cases is recognized, where X is I|L according to bt:
//    VIV[iv] = iv | (CastXX VIV[iv]) | (ConvI2X VIV[iv])
bool PhaseIdealLoop::is_iv(Node* exp, Node* iv, BasicType bt) {
  exp = exp->uncast();
  if (exp == iv && iv->bottom_type()->isa_integer(bt)) {
    return true;
  }

  if (bt == T_LONG && iv->bottom_type()->isa_int() && exp->Opcode() == Op_ConvI2L && exp->in(1)->uncast() == iv) {
    return true;
  }
  return false;
}

//------------------------------is_scaled_iv---------------------------------
// Return true if exp is a constant times the given induction var (of type bt).
// The multiplication is either done in full precision (exactly of type bt),
// or else bt is T_LONG but iv is scaled using 32-bit arithmetic followed by a ConvI2L.
// This grammar of cases is recognized, where X is I|L according to bt:
//    SIV[iv] = VIV[iv] | (CastXX SIV[iv])
//            | (MulX VIV[iv] ConX) | (MulX ConX VIV[iv])
//            | (LShiftX VIV[iv] ConI)
//            | (ConvI2L SIV[iv])  -- a "short-scale" can occur here; note recursion
//            | (SubX 0 SIV[iv])  -- same as MulX(iv, -scale); note recursion
//            | (AddX SIV[iv] SIV[iv])  -- sum of two scaled iv; note recursion
//            | (SubX SIV[iv] SIV[iv])  -- difference of two scaled iv; note recursion
//    VIV[iv] = [either iv or its value converted; see is_iv() above]
// On success, the constant scale value is stored back to *p_scale.
// The value (*p_short_scale) reports if such a ConvI2L conversion was present.
bool PhaseIdealLoop::is_scaled_iv(Node* exp, Node* iv, BasicType bt, jlong* p_scale, bool* p_short_scale, int depth) {
  BasicType exp_bt = bt;
  exp = exp->uncast();  //strip casts
  assert(exp_bt == T_INT || exp_bt == T_LONG, "unexpected int type");
  if (is_iv(exp, iv, exp_bt)) {
    if (p_scale != nullptr) {
      *p_scale = 1;
    }
    if (p_short_scale != nullptr) {
      *p_short_scale = false;
    }
    return true;
  }
  if (exp_bt == T_LONG && iv->bottom_type()->isa_int() && exp->Opcode() == Op_ConvI2L) {
    exp = exp->in(1);
    exp_bt = T_INT;
  }
  int opc = exp->Opcode();
  int which = 0;  // this is which subexpression we find the iv in
  // Can't use is_Mul() here as it's true for AndI and AndL
  if (opc == Op_Mul(exp_bt)) {
    if ((is_iv(exp->in(which = 1), iv, exp_bt) && exp->in(2)->is_Con()) ||
        (is_iv(exp->in(which = 2), iv, exp_bt) && exp->in(1)->is_Con())) {
      Node* factor = exp->in(which == 1 ? 2 : 1);  // the other argument
      jlong scale = factor->find_integer_as_long(exp_bt, 0);
      if (scale == 0) {
        return false;  // might be top
      }
      if (p_scale != nullptr) {
        *p_scale = scale;
      }
      if (p_short_scale != nullptr) {
        // (ConvI2L (MulI iv K)) can be 64-bit linear if iv is kept small enough...
        *p_short_scale = (exp_bt != bt && scale != 1);
      }
      return true;
    }
  } else if (opc == Op_LShift(exp_bt)) {
    if (is_iv(exp->in(1), iv, exp_bt) && exp->in(2)->is_Con()) {
      jint shift_amount = exp->in(2)->find_int_con(min_jint);
      if (shift_amount == min_jint) {
        return false;  // might be top
      }
      jlong scale;
      if (exp_bt == T_INT) {
        scale = java_shift_left((jint)1, (juint)shift_amount);
      } else if (exp_bt == T_LONG) {
        scale = java_shift_left((jlong)1, (julong)shift_amount);
      }
      if (p_scale != nullptr) {
        *p_scale = scale;
      }
      if (p_short_scale != nullptr) {
        // (ConvI2L (MulI iv K)) can be 64-bit linear if iv is kept small enough...
        *p_short_scale = (exp_bt != bt && scale != 1);
      }
      return true;
    }
  } else if (opc == Op_Add(exp_bt)) {
    jlong scale_l = 0;
    jlong scale_r = 0;
    bool short_scale_l = false;
    bool short_scale_r = false;
    if (depth == 0 &&
        is_scaled_iv(exp->in(1), iv, exp_bt, &scale_l, &short_scale_l, depth + 1) &&
        is_scaled_iv(exp->in(2), iv, exp_bt, &scale_r, &short_scale_r, depth + 1)) {
      // AddX(iv*K1, iv*K2) => iv*(K1+K2)
      jlong scale_sum = java_add(scale_l, scale_r);
      if (scale_sum > max_signed_integer(exp_bt) || scale_sum <= min_signed_integer(exp_bt)) {
        // This logic is shared by int and long. For int, the result may overflow
        // as we use jlong to compute so do the check here. Long result may also
        // overflow but that's fine because result wraps.
        return false;
      }
      if (p_scale != nullptr) {
        *p_scale = scale_sum;
      }
      if (p_short_scale != nullptr) {
        *p_short_scale = short_scale_l && short_scale_r;
      }
      return true;
    }
  } else if (opc == Op_Sub(exp_bt)) {
    if (exp->in(1)->find_integer_as_long(exp_bt, -1) == 0) {
      jlong scale = 0;
      if (depth == 0 && is_scaled_iv(exp->in(2), iv, exp_bt, &scale, p_short_scale, depth + 1)) {
        // SubX(0, iv*K) => iv*(-K)
        if (scale == min_signed_integer(exp_bt)) {
          // This should work even if -K overflows, but let's not.
          return false;
        }
        scale = java_multiply(scale, (jlong)-1);
        if (p_scale != nullptr) {
          *p_scale = scale;
        }
        if (p_short_scale != nullptr) {
          // (ConvI2L (MulI iv K)) can be 64-bit linear if iv is kept small enough...
          *p_short_scale = *p_short_scale || (exp_bt != bt && scale != 1);
        }
        return true;
      }
    } else {
      jlong scale_l = 0;
      jlong scale_r = 0;
      bool short_scale_l = false;
      bool short_scale_r = false;
      if (depth == 0 &&
          is_scaled_iv(exp->in(1), iv, exp_bt, &scale_l, &short_scale_l, depth + 1) &&
          is_scaled_iv(exp->in(2), iv, exp_bt, &scale_r, &short_scale_r, depth + 1)) {
        // SubX(iv*K1, iv*K2) => iv*(K1-K2)
        jlong scale_diff = java_subtract(scale_l, scale_r);
        if (scale_diff > max_signed_integer(exp_bt) || scale_diff <= min_signed_integer(exp_bt)) {
          // This logic is shared by int and long. For int, the result may
          // overflow as we use jlong to compute so do the check here. Long
          // result may also overflow but that's fine because result wraps.
          return false;
        }
        if (p_scale != nullptr) {
          *p_scale = scale_diff;
        }
        if (p_short_scale != nullptr) {
          *p_short_scale = short_scale_l && short_scale_r;
        }
        return true;
      }
    }
  }
  // We could also recognize (iv*K1)*K2, even with overflow, but let's not.
  return false;
}

//-------------------------is_scaled_iv_plus_offset--------------------------
// Return true if exp is a simple linear transform of the given induction var.
// The scale must be constant and the addition tree (if any) must be simple.
// This grammar of cases is recognized, where X is I|L according to bt:
//
//    OIV[iv] = SIV[iv] | (CastXX OIV[iv])
//            | (AddX SIV[iv] E) | (AddX E SIV[iv])
//            | (SubX SIV[iv] E) | (SubX E SIV[iv])
//    SSIV[iv] = (ConvI2X SIV[iv])  -- a "short scale" might occur here
//    SIV[iv] = [a possibly scaled value of iv; see is_scaled_iv() above]
//
// On success, the constant scale value is stored back to *p_scale unless null.
// Likewise, the addend (perhaps a synthetic AddX node) is stored to *p_offset.
// Also, (*p_short_scale) reports if a ConvI2L conversion was seen after a MulI,
// meaning bt is T_LONG but iv was scaled using 32-bit arithmetic.
// To avoid looping, the match is depth-limited, and so may fail to match the grammar to complex expressions.
bool PhaseIdealLoop::is_scaled_iv_plus_offset(Node* exp, Node* iv, BasicType bt, jlong* p_scale, Node** p_offset, bool* p_short_scale, int depth) {
  assert(bt == T_INT || bt == T_LONG, "unexpected int type");
  jlong scale = 0;  // to catch result from is_scaled_iv()
  BasicType exp_bt = bt;
  exp = exp->uncast();
  if (is_scaled_iv(exp, iv, exp_bt, &scale, p_short_scale)) {
    if (p_scale != nullptr) {
      *p_scale = scale;
    }
    if (p_offset != nullptr) {
      Node *zero = _igvn.zerocon(bt);
      set_ctrl(zero, C->root());
      *p_offset = zero;
    }
    return true;
  }
  if (exp_bt != bt) {
    // We would now be matching inputs like (ConvI2L exp:(AddI (MulI iv S) E)).
    // It's hard to make 32-bit arithmetic linear if it overflows.  Although we do
    // cope with overflowing multiplication by S, it would be even more work to
    // handle overflowing addition of E.  So we bail out here on ConvI2L input.
    return false;
  }
  int opc = exp->Opcode();
  int which = 0;  // this is which subexpression we find the iv in
  Node* offset = nullptr;
  if (opc == Op_Add(exp_bt)) {
    // Check for a scaled IV in (AddX (MulX iv S) E) or (AddX E (MulX iv S)).
    if (is_scaled_iv(exp->in(which = 1), iv, bt, &scale, p_short_scale) ||
        is_scaled_iv(exp->in(which = 2), iv, bt, &scale, p_short_scale)) {
      offset = exp->in(which == 1 ? 2 : 1);  // the other argument
      if (p_scale != nullptr) {
        *p_scale = scale;
      }
      if (p_offset != nullptr) {
        *p_offset = offset;
      }
      return true;
    }
    // Check for more addends, like (AddX (AddX (MulX iv S) E1) E2), etc.
    if (is_scaled_iv_plus_extra_offset(exp->in(1), exp->in(2), iv, bt, p_scale, p_offset, p_short_scale, depth) ||
        is_scaled_iv_plus_extra_offset(exp->in(2), exp->in(1), iv, bt, p_scale, p_offset, p_short_scale, depth)) {
      return true;
    }
  } else if (opc == Op_Sub(exp_bt)) {
    if (is_scaled_iv(exp->in(which = 1), iv, bt, &scale, p_short_scale) ||
        is_scaled_iv(exp->in(which = 2), iv, bt, &scale, p_short_scale)) {
      // Match (SubX SIV[iv] E) as if (AddX SIV[iv] (SubX 0 E)), and
      // match (SubX E SIV[iv]) as if (AddX E (SubX 0 SIV[iv])).
      offset = exp->in(which == 1 ? 2 : 1);  // the other argument
      if (which == 2) {
        // We can't handle a scale of min_jint (or min_jlong) here as -1 * min_jint = min_jint
        if (scale == min_signed_integer(bt)) {
          return false;   // cannot negate the scale of the iv
        }
        scale = java_multiply(scale, (jlong)-1);
      }
      if (p_scale != nullptr) {
        *p_scale = scale;
      }
      if (p_offset != nullptr) {
        if (which == 1) {  // must negate the extracted offset
          Node *zero = _igvn.integercon(0, exp_bt);
          set_ctrl(zero, C->root());
          Node *ctrl_off = get_ctrl(offset);
          offset = SubNode::make(zero, offset, exp_bt);
          register_new_node(offset, ctrl_off);
        }
        *p_offset = offset;
      }
      return true;
    }
  }
  return false;
}

// Helper for is_scaled_iv_plus_offset(), not called separately.
// The caller encountered (AddX exp1 offset3) or (AddX offset3 exp1).
// Here, exp1 is inspected to see if it is a simple linear transform of iv.
// If so, the offset3 is combined with any other offset2 from inside exp1.
bool PhaseIdealLoop::is_scaled_iv_plus_extra_offset(Node* exp1, Node* offset3, Node* iv,
                                                    BasicType bt,
                                                    jlong* p_scale, Node** p_offset,
                                                    bool* p_short_scale, int depth) {
  // By the time we reach here, it is unlikely that exp1 is a simple iv*K.
  // If is a linear iv transform, it is probably an add or subtract.
  // Let's collect the internal offset2 from it.
  Node* offset2 = nullptr;
  if (offset3->is_Con() &&
      depth < 2 &&
      is_scaled_iv_plus_offset(exp1, iv, bt, p_scale,
                               &offset2, p_short_scale, depth+1)) {
    if (p_offset != nullptr) {
      Node* ctrl_off2 = get_ctrl(offset2);
      Node* offset = AddNode::make(offset2, offset3, bt);
      register_new_node(offset, ctrl_off2);
      *p_offset = offset;
    }
    return true;
  }
  return false;
}

// Same as PhaseIdealLoop::duplicate_predicates() but for range checks
// eliminated by iteration splitting.
Node* PhaseIdealLoop::add_range_check_elimination_assertion_predicate(IdealLoopTree* loop,
                                                                      Node* ctrl, const int scale_con,
                                                                      Node* offset, Node* limit, jint stride_con,
                                                                      Node* value) {
  bool overflow = false;
  BoolNode* bol = rc_predicate(loop, ctrl, scale_con, offset, value, nullptr, stride_con,
                               limit, (stride_con > 0) != (scale_con > 0), overflow);
  Node* opaque_bol = new Opaque4Node(C, bol, _igvn.intcon(1));
  register_new_node(opaque_bol, ctrl);
  IfNode* new_iff = nullptr;
  if (overflow) {
    new_iff = new IfNode(ctrl, opaque_bol, PROB_MAX, COUNT_UNKNOWN);
  } else {
    new_iff = new RangeCheckNode(ctrl, opaque_bol, PROB_MAX, COUNT_UNKNOWN);
  }
  register_control(new_iff, loop->_parent, ctrl);
  Node* iffalse = new IfFalseNode(new_iff);
  register_control(iffalse, _ltree_root, new_iff);
  ProjNode* iftrue = new IfTrueNode(new_iff);
  register_control(iftrue, loop->_parent, new_iff);
  Node *frame = new ParmNode(C->start(), TypeFunc::FramePtr);
  register_new_node(frame, C->start());
  Node* halt = new HaltNode(iffalse, frame, "range check predicate failed which is impossible");
  register_control(halt, _ltree_root, iffalse);
  _igvn.add_input_to(C->root(), halt);
  return iftrue;
}

//------------------------------do_range_check---------------------------------
// Eliminate range-checks and other trip-counter vs loop-invariant tests.
void PhaseIdealLoop::do_range_check(IdealLoopTree *loop, Node_List &old_new) {
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
  if (!cl->stride_is_con()) {
    return;
  }
  // Find the trip counter; we are iteration splitting based on it
  Node *trip_counter = cl->phi();
  // Find the main loop limit; we will trim it's iterations
  // to not ever trip end tests
  Node *main_limit = cl->limit();

  // Check graph shape. Cannot optimize a loop if zero-trip
  // Opaque1 node is optimized away and then another round
  // of loop opts attempted.
  if (cl->is_canonical_loop_entry() == nullptr) {
    return;
  }

  // Need to find the main-loop zero-trip guard
  Node *ctrl = cl->skip_assertion_predicates_with_halt();
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
  if (pre_opaq1->Opcode() != Op_Opaque1) {
    return;
  }
  Opaque1Node *pre_opaq = (Opaque1Node*)pre_opaq1;
  Node *pre_limit = pre_opaq->in(1);

  // Where do we put new limit calculations
  Node *pre_ctrl = pre_end->loopnode()->in(LoopNode::EntryControl);

  // Ensure the original loop limit is available from the
  // pre-loop Opaque1 node.
  Node *orig_limit = pre_opaq->original_loop_limit();
  if (orig_limit == nullptr || _igvn.type(orig_limit) == Type::TOP) {
    return;
  }
  // Must know if its a count-up or count-down loop

  int stride_con = cl->stride_con();
  Node* zero = _igvn.longcon(0);
  Node* one  = _igvn.longcon(1);
  // Use symmetrical int range [-max_jint,max_jint]
  Node* mini = _igvn.longcon(-max_jint);
  set_ctrl(zero, C->root());
  set_ctrl(one,  C->root());
  set_ctrl(mini, C->root());

  Node* loop_entry = cl->skip_strip_mined()->in(LoopNode::EntryControl);
  assert(loop_entry->is_Proj() && loop_entry->in(0)->is_If(), "if projection only");

  // Check loop body for tests of trip-counter plus loop-invariant vs loop-variant.
  for (uint i = 0; i < loop->_body.size(); i++) {
    Node *iff = loop->_body[i];
    if (iff->Opcode() == Op_If ||
        iff->Opcode() == Op_RangeCheck) { // Test?
      // Test is an IfNode, has 2 projections.  If BOTH are in the loop
      // we need loop unswitching instead of iteration splitting.
      Node *exit = loop->is_loop_exit(iff);
      if (!exit) continue;
      int flip = (exit->Opcode() == Op_IfTrue) ? 1 : 0;

      // Get boolean condition to test
      Node *i1 = iff->in(1);
      if (!i1->is_Bool()) continue;
      BoolNode *bol = i1->as_Bool();
      BoolTest b_test = bol->_test;
      // Flip sense of test if exit condition is flipped
      if (flip) {
        b_test = b_test.negate();
      }
      // Get compare
      Node *cmp = bol->in(1);

      // Look for trip_counter + offset vs limit
      Node *rc_exp = cmp->in(1);
      Node *limit  = cmp->in(2);
      int scale_con= 1;        // Assume trip counter not scaled

      Node *limit_c = get_ctrl(limit);
      if (loop->is_member(get_loop(limit_c))) {
        // Compare might have operands swapped; commute them
        b_test = b_test.commute();
        rc_exp = cmp->in(2);
        limit  = cmp->in(1);
        limit_c = get_ctrl(limit);
        if (loop->is_member(get_loop(limit_c))) {
          continue;             // Both inputs are loop varying; cannot RCE
        }
      }
      // Here we know 'limit' is loop invariant

      // 'limit' maybe pinned below the zero trip test (probably from a
      // previous round of rce), in which case, it can't be used in the
      // zero trip test expression which must occur before the zero test's if.
      if (is_dominator(ctrl, limit_c)) {
        continue;  // Don't rce this check but continue looking for other candidates.
      }

      // Check for scaled induction variable plus an offset
      Node *offset = nullptr;

      if (!is_scaled_iv_plus_offset(rc_exp, trip_counter, &scale_con, &offset)) {
        continue;
      }

      Node *offset_c = get_ctrl(offset);
      if (loop->is_member(get_loop(offset_c))) {
        continue;               // Offset is not really loop invariant
      }
      // Here we know 'offset' is loop invariant.

      // As above for the 'limit', the 'offset' maybe pinned below the
      // zero trip test.
      if (is_dominator(ctrl, offset_c)) {
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

      // Perform the limit computations in jlong to avoid overflow
      jlong lscale_con = scale_con;
      Node* int_offset = offset;
      offset = new ConvI2LNode(offset);
      register_new_node(offset, pre_ctrl);
      Node* int_limit = limit;
      limit = new ConvI2LNode(limit);
      register_new_node(limit, pre_ctrl);

      // Adjust pre and main loop limits to guard the correct iteration set
      if (cmp->Opcode() == Op_CmpU) { // Unsigned compare is really 2 tests
        if (b_test._test == BoolTest::lt) { // Range checks always use lt
          // The underflow and overflow limits: 0 <= scale*I+offset < limit
          add_constraint(stride_con, lscale_con, offset, zero, limit, pre_ctrl, &pre_limit, &main_limit);
          Node* init = cl->init_trip();
          Node* opaque_init = new OpaqueLoopInitNode(C, init);
          register_new_node(opaque_init, loop_entry);

          // Initialized Assertion Predicate for the value of the initial main-loop.
          loop_entry = add_range_check_elimination_assertion_predicate(loop, loop_entry, scale_con, int_offset,
                                                                       int_limit, stride_con, init);
          assert(!assertion_predicate_has_loop_opaque_node(loop_entry->in(0)->as_If()), "unexpected");

          // Add two Template Assertion Predicates to create new Initialized Assertion Predicates from when either
          // unrolling or splitting this main-loop further.
          loop_entry = add_range_check_elimination_assertion_predicate(loop, loop_entry, scale_con, int_offset,
                                                                       int_limit, stride_con, opaque_init);
          assert(assertion_predicate_has_loop_opaque_node(loop_entry->in(0)->as_If()), "unexpected");

          Node* opaque_stride = new OpaqueLoopStrideNode(C, cl->stride());
          register_new_node(opaque_stride, loop_entry);
          Node* max_value = new SubINode(opaque_stride, cl->stride());
          register_new_node(max_value, loop_entry);
          max_value = new AddINode(opaque_init, max_value);
          register_new_node(max_value, loop_entry);
          // init + (current stride - initial stride) is within the loop so narrow its type by leveraging the type of the iv Phi
          max_value = new CastIINode(max_value, loop->_head->as_CountedLoop()->phi()->bottom_type());
          register_new_node(max_value, loop_entry);
          loop_entry = add_range_check_elimination_assertion_predicate(loop, loop_entry, scale_con, int_offset,
                                                                       int_limit, stride_con, max_value);
          assert(assertion_predicate_has_loop_opaque_node(loop_entry->in(0)->as_If()), "unexpected");

        } else {
          if (PrintOpto) {
            tty->print_cr("missed RCE opportunity");
          }
          continue;             // In release mode, ignore it
        }
      } else {                  // Otherwise work on normal compares
        switch(b_test._test) {
        case BoolTest::gt:
          // Fall into GE case
        case BoolTest::ge:
          // Convert (I*scale+offset) >= Limit to (I*(-scale)+(-offset)) <= -Limit
          lscale_con = -lscale_con;
          offset = new SubLNode(zero, offset);
          register_new_node(offset, pre_ctrl);
          limit  = new SubLNode(zero, limit);
          register_new_node(limit, pre_ctrl);
          // Fall into LE case
        case BoolTest::le:
          if (b_test._test != BoolTest::gt) {
            // Convert X <= Y to X < Y+1
            limit = new AddLNode(limit, one);
            register_new_node(limit, pre_ctrl);
          }
          // Fall into LT case
        case BoolTest::lt:
          // The underflow and overflow limits: MIN_INT <= scale*I+offset < limit
          // Note: (MIN_INT+1 == -MAX_INT) is used instead of MIN_INT here
          // to avoid problem with scale == -1: MIN_INT/(-1) == MIN_INT.
          add_constraint(stride_con, lscale_con, offset, mini, limit, pre_ctrl, &pre_limit, &main_limit);
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
      Node *kill_con = _igvn.intcon(1-flip);
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
          _igvn.replace_input_of(cd, 0, ctrl); // ctrl, not null
          --i;
          --imax;
        }
      }
    } // End of is IF
  }
  if (loop_entry != cl->skip_strip_mined()->in(LoopNode::EntryControl)) {
    _igvn.replace_input_of(cl->skip_strip_mined(), LoopNode::EntryControl, loop_entry);
    set_idom(cl->skip_strip_mined(), loop_entry, dom_depth(cl->skip_strip_mined()));
  }

  // Update loop limits
  if (pre_limit != orig_limit) {
    // Computed pre-loop limit can be outside of loop iterations range.
    pre_limit = (stride_con > 0) ? (Node*)new MinINode(pre_limit, orig_limit)
                                 : (Node*)new MaxINode(pre_limit, orig_limit);
    register_new_node(pre_limit, pre_ctrl);
  }
  _igvn.replace_input_of(pre_opaq, 1, pre_limit);

  // Note:: we are making the main loop limit no longer precise;
  // need to round up based on stride.
  cl->set_nonexact_trip_count();
  Node *main_cle = cl->loopexit();
  Node *main_bol = main_cle->in(1);
  // Hacking loop bounds; need private copies of exit test
  if (main_bol->outcnt() > 1) {     // BoolNode shared?
    main_bol = main_bol->clone();   // Clone a private BoolNode
    register_new_node(main_bol, main_cle->in(0));
    _igvn.replace_input_of(main_cle, 1, main_bol);
  }
  Node *main_cmp = main_bol->in(1);
  if (main_cmp->outcnt() > 1) {     // CmpNode shared?
    main_cmp = main_cmp->clone();   // Clone a private CmpNode
    register_new_node(main_cmp, main_cle->in(0));
    _igvn.replace_input_of(main_bol, 1, main_cmp);
  }
  assert(main_limit == cl->limit() || get_ctrl(main_limit) == pre_ctrl, "wrong control for added limit");
  const TypeInt* orig_limit_t = _igvn.type(orig_limit)->is_int();
  bool upward = cl->stride_con() > 0;
  // The new loop limit is <= (for an upward loop) >= (for a downward loop) than the orig limit.
  // The expression that computes the new limit may be too complicated and the computed type of the new limit
  // may be too pessimistic. A CastII here guarantees it's not lost.
  main_limit = new CastIINode(main_limit, TypeInt::make(upward ? min_jint : orig_limit_t->_lo,
                                                        upward ? orig_limit_t->_hi : max_jint, Type::WidenMax));
  main_limit->init_req(0, pre_ctrl);
  register_new_node(main_limit, pre_ctrl);
  // Hack the now-private loop bounds
  _igvn.replace_input_of(main_cmp, 2, main_limit);
  // The OpaqueNode is unshared by design
  assert(opqzm->outcnt() == 1, "cannot hack shared node");
  _igvn.replace_input_of(opqzm, 1, main_limit);

  return;
}

bool IdealLoopTree::compute_has_range_checks() const {
  assert(_head->is_CountedLoop(), "");
  for (uint i = 0; i < _body.size(); i++) {
    Node *iff = _body[i];
    int iff_opc = iff->Opcode();
    if (iff_opc == Op_If || iff_opc == Op_RangeCheck) {
      return true;
    }
  }
  return false;
}

//------------------------------DCE_loop_body----------------------------------
// Remove simplistic dead code from loop body
void IdealLoopTree::DCE_loop_body() {
  for (uint i = 0; i < _body.size(); i++) {
    if (_body.at(i)->outcnt() == 0) {
      _body.map(i, _body.pop());
      i--; // Ensure we revisit the updated index.
    }
  }
}


//------------------------------adjust_loop_exit_prob--------------------------
// Look for loop-exit tests with the 50/50 (or worse) guesses from the parsing stage.
// Replace with a 1-in-10 exit guess.
void IdealLoopTree::adjust_loop_exit_prob(PhaseIdealLoop *phase) {
  Node *test = tail();
  while (test != _head) {
    uint top = test->Opcode();
    if (top == Op_IfTrue || top == Op_IfFalse) {
      int test_con = ((ProjNode*)test)->_con;
      assert(top == (uint)(test_con? Op_IfTrue: Op_IfFalse), "sanity");
      IfNode *iff = test->in(0)->as_If();
      if (iff->outcnt() == 2) {         // Ignore dead tests
        Node *bol = iff->in(1);
        if (bol && bol->req() > 1 && bol->in(1) &&
            ((bol->in(1)->Opcode() == Op_CompareAndExchangeB) ||
             (bol->in(1)->Opcode() == Op_CompareAndExchangeS) ||
             (bol->in(1)->Opcode() == Op_CompareAndExchangeI) ||
             (bol->in(1)->Opcode() == Op_CompareAndExchangeL) ||
             (bol->in(1)->Opcode() == Op_CompareAndExchangeP) ||
             (bol->in(1)->Opcode() == Op_CompareAndExchangeN) ||
             (bol->in(1)->Opcode() == Op_WeakCompareAndSwapB) ||
             (bol->in(1)->Opcode() == Op_WeakCompareAndSwapS) ||
             (bol->in(1)->Opcode() == Op_WeakCompareAndSwapI) ||
             (bol->in(1)->Opcode() == Op_WeakCompareAndSwapL) ||
             (bol->in(1)->Opcode() == Op_WeakCompareAndSwapP) ||
             (bol->in(1)->Opcode() == Op_WeakCompareAndSwapN) ||
             (bol->in(1)->Opcode() == Op_CompareAndSwapB) ||
             (bol->in(1)->Opcode() == Op_CompareAndSwapS) ||
             (bol->in(1)->Opcode() == Op_CompareAndSwapI) ||
             (bol->in(1)->Opcode() == Op_CompareAndSwapL) ||
             (bol->in(1)->Opcode() == Op_CompareAndSwapP) ||
             (bol->in(1)->Opcode() == Op_CompareAndSwapN) ||
             (bol->in(1)->Opcode() == Op_ShenandoahCompareAndExchangeP) ||
             (bol->in(1)->Opcode() == Op_ShenandoahCompareAndExchangeN) ||
             (bol->in(1)->Opcode() == Op_ShenandoahWeakCompareAndSwapP) ||
             (bol->in(1)->Opcode() == Op_ShenandoahWeakCompareAndSwapN) ||
             (bol->in(1)->Opcode() == Op_ShenandoahCompareAndSwapP) ||
             (bol->in(1)->Opcode() == Op_ShenandoahCompareAndSwapN)))
          return;               // Allocation loops RARELY take backedge
        // Find the OTHER exit path from the IF
        Node* ex = iff->proj_out(1-test_con);
        float p = iff->_prob;
        if (!phase->is_member(this, ex) && iff->_fcnt == COUNT_UNKNOWN) {
          if (top == Op_IfTrue) {
            if (p < (PROB_FAIR + PROB_UNLIKELY_MAG(3))) {
              iff->_prob = PROB_STATIC_FREQUENT;
            }
          } else {
            if (p > (PROB_FAIR - PROB_UNLIKELY_MAG(3))) {
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
static CountedLoopNode* locate_pre_from_main(CountedLoopNode* main_loop) {
  assert(!main_loop->is_main_no_pre_loop(), "Does not have a pre loop");
  Node* ctrl = main_loop->skip_assertion_predicates_with_halt();
  assert(ctrl->Opcode() == Op_IfTrue || ctrl->Opcode() == Op_IfFalse, "");
  Node* iffm = ctrl->in(0);
  assert(iffm->Opcode() == Op_If, "");
  Node* p_f = iffm->in(0);
  assert(p_f->Opcode() == Op_IfFalse, "");
  CountedLoopNode* pre_loop = p_f->in(0)->as_CountedLoopEnd()->loopnode();
  assert(pre_loop->is_pre_loop(), "No pre loop found");
  return pre_loop;
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
  if (_next == nullptr) {
    return;
  }

  Node* next_head = _next->_head;
  if (!next_head->is_CountedLoop()) {
    return;
  }

  CountedLoopNode* main_head = next_head->as_CountedLoop();
  if (!main_head->is_main_loop() || main_head->is_main_no_pre_loop()) {
    return;
  }

  assert(locate_pre_from_main(main_head) == cl, "bad main loop");
  Node* main_iff = main_head->skip_assertion_predicates_with_halt()->in(0);

  // Remove the Opaque1Node of the pre loop and make it execute all iterations
  phase->_igvn.replace_input_of(pre_cmp, 2, pre_cmp->in(2)->in(2));
  // Remove the OpaqueZeroTripGuardNode of the main loop so it can be optimized out
  Node* main_cmp = main_iff->in(1)->in(1);
  assert(main_cmp->in(2)->Opcode() == Op_OpaqueZeroTripGuard, "main loop has no opaque node?");
  phase->_igvn.replace_input_of(main_cmp, 2, main_cmp->in(2)->in(1));
}

//------------------------------do_remove_empty_loop---------------------------
// We always attempt remove empty loops.   The approach is to replace the trip
// counter with the value it will have on the last iteration.  This will break
// the loop.
bool IdealLoopTree::do_remove_empty_loop(PhaseIdealLoop *phase) {
  if (!_head->is_CountedLoop()) {
    return false;   // Dead loop
  }
  if (!empty_loop_candidate(phase)) {
    return false;
  }
  CountedLoopNode *cl = _head->as_CountedLoop();
#ifdef ASSERT
  // Call collect_loop_core_nodes to exercise the assert that checks that it finds the right number of nodes
  if (empty_loop_with_extra_nodes_candidate(phase)) {
    Unique_Node_List wq;
    collect_loop_core_nodes(phase, wq);
  }
#endif
  // Minimum size must be empty loop
  if (_body.size() > EMPTY_LOOP_SIZE) {
    // This loop has more nodes than an empty loop but, maybe they are only kept alive by the outer strip mined loop's
    // safepoint. If they go away once the safepoint is removed, that loop is empty.
    if (!empty_loop_with_data_nodes(phase)) {
      return false;
    }
  }
  if (cl->is_pre_loop()) {
    // If the loop we are removing is a pre-loop then the main and post loop
    // can be removed as well.
    remove_main_post_loops(cl, phase);
  }

#ifdef ASSERT
  // Ensure at most one used phi exists, which is the iv.
  Node* iv = nullptr;
  for (DUIterator_Fast imax, i = cl->fast_outs(imax); i < imax; i++) {
    Node* n = cl->fast_out(i);
    if ((n->Opcode() == Op_Phi) && (n->outcnt() > 0)) {
      assert(iv == nullptr, "Too many phis");
      iv = n;
    }
  }
  assert(iv == cl->phi(), "Wrong phi");
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
    Predicates predicates(cl->skip_strip_mined()->in(LoopNode::EntryControl));
    Node* in_ctrl = predicates.entry();
    if (in_ctrl->Opcode() == Op_IfTrue || in_ctrl->Opcode() == Op_IfFalse) {
      bool maybe_swapped = (in_ctrl->Opcode() == Op_IfFalse);
      // The test should look like just the backedge of a CountedLoop
      Node* iff = in_ctrl->in(0);
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
  // iteration (exact_limit - stride), to make sure the loop exit value
  // is correct, for any users after the loop.
  // Note: the final value after increment should not overflow since
  // counted loop has limit check predicate.
  Node* phi = cl->phi();
  Node* exact_limit = phase->exact_limit(this);

  // We need to pin the exact limit to prevent it from floating above the zero trip guard.
  Node* cast_ii = ConstraintCastNode::make(cl->in(LoopNode::EntryControl), exact_limit, phase->_igvn.type(exact_limit), ConstraintCastNode::UnconditionalDependency, T_INT);
  phase->register_new_node(cast_ii, cl->in(LoopNode::EntryControl));

  Node* final_iv = new SubINode(cast_ii, cl->stride());
  phase->register_new_node(final_iv, cl->in(LoopNode::EntryControl));
  phase->_igvn.replace_node(phi, final_iv);

  // Set loop-exit condition to false. Then the CountedLoopEnd will collapse,
  // because the back edge is never taken.
  Node* zero = phase->_igvn.intcon(0);
  phase->_igvn.replace_input_of(cl->loopexit(), CountedLoopEndNode::TestValue, zero);

  phase->C->set_major_progress();
  return true;
}

bool IdealLoopTree::empty_loop_candidate(PhaseIdealLoop* phase) const {
  CountedLoopNode *cl = _head->as_CountedLoop();
  if (!cl->is_valid_counted_loop(T_INT)) {
    return false;   // Malformed loop
  }
  if (!phase->is_member(this, phase->get_ctrl(cl->loopexit()->in(CountedLoopEndNode::TestValue)))) {
    return false;   // Infinite loop
  }
  return true;
}

bool IdealLoopTree::empty_loop_with_data_nodes(PhaseIdealLoop* phase) const {
  CountedLoopNode* cl = _head->as_CountedLoop();
  if (!cl->is_strip_mined() || !empty_loop_with_extra_nodes_candidate(phase)) {
    return false;
  }
  Unique_Node_List empty_loop_nodes;
  Unique_Node_List wq;

  // Start from all data nodes in the loop body that are not one of the EMPTY_LOOP_SIZE nodes expected in an empty body
  enqueue_data_nodes(phase, empty_loop_nodes, wq);
  // and now follow uses
  for (uint i = 0; i < wq.size(); ++i) {
    Node* n = wq.at(i);
    for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
      Node* u = n->fast_out(j);
      if (u->Opcode() == Op_SafePoint) {
        // found a safepoint. Maybe this loop's safepoint or another loop safepoint.
        if (!process_safepoint(phase, empty_loop_nodes, wq, u)) {
          return false;
        }
      } else {
        const Type* u_t = phase->_igvn.type(u);
        if (u_t == Type::CONTROL || u_t == Type::MEMORY || u_t == Type::ABIO) {
          // found a side effect
          return false;
        }
        wq.push(u);
      }
    }
  }
  // Nodes (ignoring the EMPTY_LOOP_SIZE nodes of the "core" of the loop) are kept alive by otherwise empty loops'
  // safepoints: kill them.
  for (uint i = 0; i < wq.size(); ++i) {
    Node* n = wq.at(i);
    phase->_igvn.replace_node(n, phase->C->top());
  }

#ifdef ASSERT
  for (uint i = 0; i < _body.size(); ++i) {
    Node* n = _body.at(i);
    assert(wq.member(n) || empty_loop_nodes.member(n), "missed a node in the body?");
  }
#endif

  return true;
}

bool IdealLoopTree::process_safepoint(PhaseIdealLoop* phase, Unique_Node_List& empty_loop_nodes, Unique_Node_List& wq,
                                      Node* sfpt) const {
  CountedLoopNode* cl = _head->as_CountedLoop();
  if (cl->outer_safepoint() == sfpt) {
    // the current loop's safepoint
    return true;
  }

  // Some other loop's safepoint. Maybe that loop is empty too.
  IdealLoopTree* sfpt_loop = phase->get_loop(sfpt);
  if (!sfpt_loop->_head->is_OuterStripMinedLoop()) {
    return false;
  }
  IdealLoopTree* sfpt_inner_loop = sfpt_loop->_child;
  CountedLoopNode* sfpt_cl = sfpt_inner_loop->_head->as_CountedLoop();
  assert(sfpt_cl->is_strip_mined(), "inconsistent");

  if (empty_loop_nodes.member(sfpt_cl)) {
    // already taken care of
    return true;
  }

  if (!sfpt_inner_loop->empty_loop_candidate(phase) || !sfpt_inner_loop->empty_loop_with_extra_nodes_candidate(phase)) {
    return false;
  }

  // Enqueue the nodes of that loop for processing too
  sfpt_inner_loop->enqueue_data_nodes(phase, empty_loop_nodes, wq);
  return true;
}

bool IdealLoopTree::empty_loop_with_extra_nodes_candidate(PhaseIdealLoop* phase) const {
  CountedLoopNode *cl = _head->as_CountedLoop();
  // No other control flow node in the loop body
  if (cl->loopexit()->in(0) != cl) {
    return false;
  }

  if (phase->is_member(this, phase->get_ctrl(cl->limit()))) {
    return false;
  }
  return true;
}

void IdealLoopTree::enqueue_data_nodes(PhaseIdealLoop* phase, Unique_Node_List& empty_loop_nodes,
                                       Unique_Node_List& wq) const {
  collect_loop_core_nodes(phase, empty_loop_nodes);
  for (uint i = 0; i < _body.size(); ++i) {
    Node* n = _body.at(i);
    if (!empty_loop_nodes.member(n)) {
      wq.push(n);
    }
  }
}

// This collects the node that would be left if this body was empty
void IdealLoopTree::collect_loop_core_nodes(PhaseIdealLoop* phase, Unique_Node_List& wq) const {
  uint before = wq.size();
  wq.push(_head->in(LoopNode::LoopBackControl));
  for (uint i = 0; i < wq.size(); ++i) {
    Node* n = wq.at(i);
    for (uint j = 0; j < n->req(); ++j) {
      Node* in = n->in(j);
      if (in != nullptr) {
        if (phase->get_loop(phase->ctrl_or_self(in)) == this) {
          wq.push(in);
        }
      }
    }
  }
  assert(wq.size() - before == EMPTY_LOOP_SIZE, "expect the EMPTY_LOOP_SIZE nodes of this body if empty");
}

//------------------------------do_one_iteration_loop--------------------------
// Convert one iteration loop into normal code.
bool IdealLoopTree::do_one_iteration_loop(PhaseIdealLoop *phase) {
  if (!_head->as_Loop()->is_valid_counted_loop(T_INT)) {
    return false; // Only for counted loop
  }
  CountedLoopNode *cl = _head->as_CountedLoop();
  if (!cl->has_exact_trip_count() || cl->trip_count() != 1) {
    return false;
  }

#ifndef PRODUCT
  if (TraceLoopOpts) {
    tty->print("OneIteration ");
    this->dump_head();
  }
#endif

  Node *init_n = cl->init_trip();
  // Loop boundaries should be constant since trip count is exact.
  assert((cl->stride_con() > 0 && init_n->get_int() + cl->stride_con() >= cl->limit()->get_int()) ||
         (cl->stride_con() < 0 && init_n->get_int() + cl->stride_con() <= cl->limit()->get_int()), "should be one iteration");
  // Replace the phi at loop head with the value of the init_trip.
  // Then the CountedLoopEnd will collapse (backedge will not be taken)
  // and all loop-invariant uses of the exit values will be correct.
  phase->_igvn.replace_node(cl->phi(), cl->init_trip());
  phase->C->set_major_progress();
  return true;
}

//=============================================================================
//------------------------------iteration_split_impl---------------------------
bool IdealLoopTree::iteration_split_impl(PhaseIdealLoop *phase, Node_List &old_new) {
  if (!_head->is_Loop()) {
    // Head could be a region with a NeverBranch that was added in beautify loops but the region was not
    // yet transformed into a LoopNode. Bail out and wait until beautify loops turns it into a Loop node.
    return false;
  }
  // Compute loop trip count if possible.
  compute_trip_count(phase);

  // Convert one iteration loop into normal code.
  if (do_one_iteration_loop(phase)) {
    return true;
  }
  // Check and remove empty loops (spam micro-benchmarks)
  if (do_remove_empty_loop(phase)) {
    return true;  // Here we removed an empty loop
  }

  AutoNodeBudget node_budget(phase);

  // Non-counted loops may be peeled; exactly 1 iteration is peeled.
  // This removes loop-invariant tests (usually null checks).
  if (!_head->is_CountedLoop()) { // Non-counted loop
    if (PartialPeelLoop && phase->partial_peel(this, old_new)) {
      // Partial peel succeeded so terminate this round of loop opts
      return false;
    }
    if (policy_peeling(phase)) {    // Should we peel?
      if (PrintOpto) { tty->print_cr("should_peel"); }
      phase->do_peeling(this, old_new);
    } else if (policy_unswitching(phase)) {
      phase->do_unswitching(this, old_new);
      return false; // need to recalculate idom data
    } else if (phase->duplicate_loop_backedge(this, old_new)) {
      return false;
    } else if (_head->is_LongCountedLoop()) {
      phase->create_loop_nest(this, old_new);
    }
    return true;
  }
  CountedLoopNode *cl = _head->as_CountedLoop();

  if (!cl->is_valid_counted_loop(T_INT)) return true; // Ignore various kinds of broken loops

  // Do nothing special to pre- and post- loops
  if (cl->is_pre_loop() || cl->is_post_loop()) return true;

  // Compute loop trip count from profile data
  compute_profile_trip_cnt(phase);

  // Before attempting fancy unrolling, RCE or alignment, see if we want
  // to completely unroll this loop or do loop unswitching.
  if (cl->is_normal_loop()) {
    if (policy_unswitching(phase)) {
      phase->do_unswitching(this, old_new);
      return false; // need to recalculate idom data
    }
    if (policy_maximally_unroll(phase)) {
      // Here we did some unrolling and peeling.  Eventually we will
      // completely unroll this loop and it will no longer be a loop.
      phase->do_maximally_unroll(this, old_new);
      return true;
    }
    if (StressDuplicateBackedge && phase->duplicate_loop_backedge(this, old_new)) {
      return false;
    }
  }

  uint est_peeling = estimate_peeling(phase);
  bool should_peel = 0 < est_peeling;

  // Counted loops may be peeled, or may need some iterations run up
  // front for RCE. Thus we clone a full loop up front whose trip count is
  // at least 1 (if peeling), but may be several more.

  // The main loop will start cache-line aligned with at least 1
  // iteration of the unrolled body (zero-trip test required) and
  // will have some range checks removed.

  // A post-loop will finish any odd iterations (leftover after
  // unrolling), plus any needed for RCE purposes.

  bool should_unroll = policy_unroll(phase);
  bool should_rce    = policy_range_check(phase, false, T_INT);
  bool should_rce_long = policy_range_check(phase, false, T_LONG);

  // If not RCE'ing (iteration splitting), then we do not need a pre-loop.
  // We may still need to peel an initial iteration but we will not
  // be needing an unknown number of pre-iterations.
  //
  // Basically, if peel_only reports TRUE first time through, we will not
  // be able to later do RCE on this loop.
  bool peel_only = policy_peel_only(phase) && !should_rce;

  // If we have any of these conditions (RCE, unrolling) met, then
  // we switch to the pre-/main-/post-loop model.  This model also covers
  // peeling.
  if (should_rce || should_unroll) {
    if (cl->is_normal_loop()) { // Convert to 'pre/main/post' loops
      if (should_rce_long && phase->create_loop_nest(this, old_new)) {
        return true;
      }
      uint estimate = est_loop_clone_sz(3);
      if (!phase->may_require_nodes(estimate)) {
        return false;
      }
      phase->insert_pre_post_loops(this, old_new, peel_only);
    }
    // Adjust the pre- and main-loop limits to let the pre and  post loops run
    // with full checks, but the main-loop with no checks.  Remove said checks
    // from the main body.
    if (should_rce) {
      phase->do_range_check(this, old_new);
    }

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
  } else {                      // Else we have an unchanged counted loop
    if (should_peel) {          // Might want to peel but do nothing else
      if (phase->may_require_nodes(est_peeling)) {
        phase->do_peeling(this, old_new);
      }
    }
    if (should_rce_long) {
      phase->create_loop_nest(this, old_new);
    }
  }
  return true;
}


//=============================================================================
//------------------------------iteration_split--------------------------------
bool IdealLoopTree::iteration_split(PhaseIdealLoop* phase, Node_List &old_new) {
  // Recursively iteration split nested loops
  if (_child && !_child->iteration_split(phase, old_new)) {
    return false;
  }

  // Clean out prior deadwood
  DCE_loop_body();

  // Look for loop-exit tests with my 50/50 guesses from the Parsing stage.
  // Replace with a 1-in-10 exit guess.
  if (!is_root() && is_loop()) {
    adjust_loop_exit_prob(phase);
  }

  // Unrolling, RCE and peeling efforts, iff innermost loop.
  if (_allow_optimizations && is_innermost()) {
    if (!_has_call) {
      if (!iteration_split_impl(phase, old_new)) {
        return false;
      }
    } else {
      AutoNodeBudget node_budget(phase);
      if (policy_unswitching(phase)) {
        phase->do_unswitching(this, old_new);
        return false; // need to recalculate idom data
      }
    }
  }

  if (_next && !_next->iteration_split(phase, old_new)) {
    return false;
  }
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


// Examine an inner loop looking for a single store of an invariant
// value in a unit stride loop,
bool PhaseIdealLoop::match_fill_loop(IdealLoopTree* lpt, Node*& store, Node*& store_value,
                                     Node*& shift, Node*& con) {
  const char* msg = nullptr;
  Node* msg_node = nullptr;

  store_value = nullptr;
  con = nullptr;
  shift = nullptr;

  // Process the loop looking for stores.  If there are multiple
  // stores or extra control flow give at this point.
  CountedLoopNode* head = lpt->_head->as_CountedLoop();
  for (uint i = 0; msg == nullptr && i < lpt->_body.size(); i++) {
    Node* n = lpt->_body.at(i);
    if (n->outcnt() == 0) continue; // Ignore dead
    if (n->is_Store()) {
      if (store != nullptr) {
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
    } else if (n->is_If() && n != head->loopexit_or_null()) {
      msg = "extra control flow";
      msg_node = n;
    }
  }

  if (store == nullptr) {
    // No store in loop
    return false;
  }

  if (msg == nullptr && head->stride_con() != 1) {
    // could handle negative strides too
    if (head->stride_con() < 0) {
      msg = "negative stride";
    } else {
      msg = "non-unit stride";
    }
  }

  if (msg == nullptr && !store->in(MemNode::Address)->is_AddP()) {
    msg = "can't handle store address";
    msg_node = store->in(MemNode::Address);
  }

  if (msg == nullptr &&
      (!store->in(MemNode::Memory)->is_Phi() ||
       store->in(MemNode::Memory)->in(LoopNode::LoopBackControl) != store)) {
    msg = "store memory isn't proper phi";
    msg_node = store->in(MemNode::Memory);
  }

  // Make sure there is an appropriate fill routine
  BasicType t = store->as_Mem()->memory_type();
  const char* fill_name;
  if (msg == nullptr &&
      StubRoutines::select_fill_function(t, false, fill_name) == nullptr) {
    msg = "unsupported store";
    msg_node = store;
  }

  if (msg != nullptr) {
#ifndef PRODUCT
    if (TraceOptimizeFill) {
      tty->print_cr("not fill intrinsic candidate: %s", msg);
      if (msg_node != nullptr) msg_node->dump();
    }
#endif
    return false;
  }

  // Make sure the address expression can be handled.  It should be
  // head->phi * elsize + con.  head->phi might have a ConvI2L(CastII()).
  Node* elements[4];
  Node* cast = nullptr;
  Node* conv = nullptr;
  bool found_index = false;
  int count = store->in(MemNode::Address)->as_AddP()->unpack_offsets(elements, ARRAY_SIZE(elements));
  for (int e = 0; e < count; e++) {
    Node* n = elements[e];
    if (n->is_Con() && con == nullptr) {
      con = n;
    } else if (n->Opcode() == Op_LShiftX && shift == nullptr) {
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
    } else if (n->Opcode() == Op_ConvI2L && conv == nullptr) {
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
  if (msg == nullptr && shift == nullptr && t != T_BYTE && t != T_BOOLEAN) {
    msg = "can't find shift";
    msg_node = store;
  }

  if (msg != nullptr) {
#ifndef PRODUCT
    if (TraceOptimizeFill) {
      tty->print_cr("not fill intrinsic: %s", msg);
      if (msg_node != nullptr) msg_node->dump();
    }
#endif
    return false;
  }

  // No make sure all the other nodes in the loop can be handled
  VectorSet ok;

  // store related values are ok
  ok.set(store->_idx);
  ok.set(store->in(MemNode::Memory)->_idx);

  CountedLoopEndNode* loop_exit = head->loopexit();

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

  for (uint i = 0; msg == nullptr && i < lpt->_body.size(); i++) {
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
  for (uint i = 0; msg == nullptr && i < lpt->_body.size(); i++) {
    Node* n = lpt->_body.at(i);
    // These values can be replaced with other nodes if they are used
    // outside the loop.
    if (n == store || n == loop_exit || n == head->incr() || n == store->in(MemNode::Memory)) continue;
    for (SimpleDUIterator iter(n); iter.has_next(); iter.next()) {
      Node* use = iter.get();
      if (!lpt->_body.contains(use)) {
        msg = "node is used outside loop";
        msg_node = n;
        break;
      }
    }
  }

#ifdef ASSERT
  if (TraceOptimizeFill) {
    if (msg != nullptr) {
      tty->print_cr("no fill intrinsic: %s", msg);
      if (msg_node != nullptr) msg_node->dump();
    } else {
      tty->print_cr("fill intrinsic for:");
    }
    store->dump();
    if (Verbose) {
      lpt->_body.dump();
    }
  }
#endif

  return msg == nullptr;
}



bool PhaseIdealLoop::intrinsify_fill(IdealLoopTree* lpt) {
  // Only for counted inner loops
  if (!lpt->is_counted() || !lpt->is_innermost()) {
    return false;
  }

  // Must have constant stride
  CountedLoopNode* head = lpt->_head->as_CountedLoop();
  if (!head->is_valid_counted_loop(T_INT) || !head->is_normal_loop()) {
    return false;
  }

  head->verify_strip_mined(1);

  // Check that the body only contains a store of a loop invariant
  // value that is indexed by the loop phi.
  Node* store = nullptr;
  Node* store_value = nullptr;
  Node* shift = nullptr;
  Node* offset = nullptr;
  if (!match_fill_loop(lpt, store, store_value, shift, offset)) {
    return false;
  }

  Node* exit = head->loopexit()->proj_out_or_null(0);
  if (exit == nullptr) {
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
  if (shift != nullptr) {
    // byte arrays don't require a shift but others do.
    index = new LShiftXNode(index, shift->in(2));
    _igvn.register_new_node_with_optimizer(index);
  }
  Node* from = new AddPNode(base, base, index);
  _igvn.register_new_node_with_optimizer(from);
  // For normal array fills, C2 uses two AddP nodes for array element
  // addressing. But for array fills with Unsafe call, there's only one
  // AddP node adding an absolute offset, so we do a null check here.
  assert(offset != nullptr || C->has_unsafe_access(),
         "Only array fills with unsafe have no extra offset");
  if (offset != nullptr) {
    from = new AddPNode(base, from, offset);
    _igvn.register_new_node_with_optimizer(from);
  }
  // Compute the number of elements to copy
  Node* len = new SubINode(head->limit(), head->init_trip());
  _igvn.register_new_node_with_optimizer(len);

  // If the store is on the backedge, it is not executed in the last
  // iteration, and we must subtract 1 from the len.
  Node* backedge = head->loopexit()->proj_out(1);
  if (store->in(0) == backedge) {
    len = new SubINode(len, _igvn.intcon(1));
    _igvn.register_new_node_with_optimizer(len);
#ifndef PRODUCT
    if (TraceOptimizeFill) {
      tty->print_cr("ArrayFill store on backedge, subtract 1 from len.");
    }
#endif
  }

  BasicType t = store->as_Mem()->memory_type();
  bool aligned = false;
  if (offset != nullptr && head->init_trip()->is_Con()) {
    int element_size = type2aelembytes(t);
    aligned = (offset->find_intptr_t_type()->get_con() + head->init_trip()->get_int() * element_size) % HeapWordSize == 0;
  }

  // Build a call to the fill routine
  const char* fill_name;
  address fill = StubRoutines::select_fill_function(t, aligned, fill_name);
  assert(fill != nullptr, "what?");

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
  call->init_req(TypeFunc::ReturnAdr, C->start()->proj_out_or_null(TypeFunc::ReturnAdr));
  call->init_req(TypeFunc::FramePtr,  C->start()->proj_out_or_null(TypeFunc::FramePtr));
  _igvn.register_new_node_with_optimizer(call);
  result_ctrl = new ProjNode(call,TypeFunc::Control);
  _igvn.register_new_node_with_optimizer(result_ctrl);
  result_mem = new ProjNode(call,TypeFunc::Memory);
  _igvn.register_new_node_with_optimizer(result_mem);

/* Disable following optimization until proper fix (add missing checks).

  // If this fill is tightly coupled to an allocation and overwrites
  // the whole body, allow it to take over the zeroing.
  AllocateNode* alloc = AllocateNode::Ideal_allocation(base, this);
  if (alloc != nullptr && alloc->is_AllocateArray()) {
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

  if (head->is_strip_mined()) {
    // Inner strip mined loop goes away so get rid of outer strip
    // mined loop
    Node* outer_sfpt = head->outer_safepoint();
    Node* in = outer_sfpt->in(0);
    Node* outer_out = head->outer_loop_exit();
    lazy_replace(outer_out, in);
    _igvn.replace_input_of(outer_sfpt, 0, C->top());
  }

  // Redirect the old control and memory edges that are outside the loop.
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

#ifndef PRODUCT
  if (TraceOptimizeFill) {
    tty->print("ArrayFill call   ");
    call->dump();
  }
#endif

  return true;
}
