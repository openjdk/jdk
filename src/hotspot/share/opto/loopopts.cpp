/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/c2/barrierSetC2.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "opto/addnode.hpp"
#include "opto/callnode.hpp"
#include "opto/castnode.hpp"
#include "opto/connode.hpp"
#include "opto/castnode.hpp"
#include "opto/divnode.hpp"
#include "opto/loopnode.hpp"
#include "opto/matcher.hpp"
#include "opto/mulnode.hpp"
#include "opto/movenode.hpp"
#include "opto/opaquenode.hpp"
#include "opto/rootnode.hpp"
#include "opto/subnode.hpp"
#include "opto/subtypenode.hpp"
#include "opto/vectornode.hpp"
#include "utilities/macros.hpp"

//=============================================================================
//------------------------------split_thru_phi---------------------------------
// Split Node 'n' through merge point if there is enough win.
Node* PhaseIdealLoop::split_thru_phi(Node* n, Node* region, int policy) {
  if (n->Opcode() == Op_ConvI2L && n->bottom_type() != TypeLong::LONG) {
    // ConvI2L may have type information on it which is unsafe to push up
    // so disable this for now
    return nullptr;
  }

  // Splitting range check CastIIs through a loop induction Phi can
  // cause new Phis to be created that are left unrelated to the loop
  // induction Phi and prevent optimizations (vectorization)
  if (n->Opcode() == Op_CastII && region->is_CountedLoop() &&
      n->in(1) == region->as_CountedLoop()->phi()) {
    return nullptr;
  }

  if (cannot_split_division(n, region)) {
    return nullptr;
  }

  int wins = 0;
  assert(!n->is_CFG(), "");
  assert(region->is_Region(), "");

  const Type* type = n->bottom_type();
  const TypeOopPtr* t_oop = _igvn.type(n)->isa_oopptr();
  Node* phi;
  if (t_oop != nullptr && t_oop->is_known_instance_field()) {
    int iid    = t_oop->instance_id();
    int index  = C->get_alias_index(t_oop);
    int offset = t_oop->offset();
    phi = new PhiNode(region, type, nullptr, iid, index, offset);
  } else {
    phi = PhiNode::make_blank(region, n);
  }
  uint old_unique = C->unique();
  for (uint i = 1; i < region->req(); i++) {
    Node* x;
    Node* the_clone = nullptr;
    if (region->in(i) == C->top()) {
      x = C->top();             // Dead path?  Use a dead data op
    } else {
      x = n->clone();           // Else clone up the data op
      the_clone = x;            // Remember for possible deletion.
      // Alter data node to use pre-phi inputs
      if (n->in(0) == region)
        x->set_req( 0, region->in(i) );
      for (uint j = 1; j < n->req(); j++) {
        Node* in = n->in(j);
        if (in->is_Phi() && in->in(0) == region)
          x->set_req(j, in->in(i)); // Use pre-Phi input for the clone
      }
    }
    // Check for a 'win' on some paths
    const Type* t = x->Value(&_igvn);

    bool singleton = t->singleton();

    // A TOP singleton indicates that there are no possible values incoming
    // along a particular edge. In most cases, this is OK, and the Phi will
    // be eliminated later in an Ideal call. However, we can't allow this to
    // happen if the singleton occurs on loop entry, as the elimination of
    // the PhiNode may cause the resulting node to migrate back to a previous
    // loop iteration.
    if (singleton && t == Type::TOP) {
      // Is_Loop() == false does not confirm the absence of a loop (e.g., an
      // irreducible loop may not be indicated by an affirmative is_Loop());
      // therefore, the only top we can split thru a phi is on a backedge of
      // a loop.
      singleton &= region->is_Loop() && (i != LoopNode::EntryControl);
    }

    if (singleton) {
      wins++;
      x = ((PhaseGVN&)_igvn).makecon(t);
    } else {
      // We now call Identity to try to simplify the cloned node.
      // Note that some Identity methods call phase->type(this).
      // Make sure that the type array is big enough for
      // our new node, even though we may throw the node away.
      // (Note: This tweaking with igvn only works because x is a new node.)
      _igvn.set_type(x, t);
      // If x is a TypeNode, capture any more-precise type permanently into Node
      // otherwise it will be not updated during igvn->transform since
      // igvn->type(x) is set to x->Value() already.
      x->raise_bottom_type(t);
      Node* y = x->Identity(&_igvn);
      if (y != x) {
        wins++;
        x = y;
      } else {
        y = _igvn.hash_find(x);
        if (y == nullptr) {
          y = similar_subtype_check(x, region->in(i));
        }
        if (y) {
          wins++;
          x = y;
        } else {
          // Else x is a new node we are keeping
          // We do not need register_new_node_with_optimizer
          // because set_type has already been called.
          _igvn._worklist.push(x);
        }
      }
    }

    phi->set_req( i, x );

    if (the_clone == nullptr) {
      continue;
    }

    if (the_clone != x) {
      _igvn.remove_dead_node(the_clone);
    } else if (region->is_Loop() && i == LoopNode::LoopBackControl &&
               n->is_Load() && can_move_to_inner_loop(n, region->as_Loop(), x)) {
      // it is not a win if 'x' moved from an outer to an inner loop
      // this edge case can only happen for Load nodes
      wins = 0;
      break;
    }
  }
  // Too few wins?
  if (wins <= policy) {
    _igvn.remove_dead_node(phi);
    return nullptr;
  }

  // Record Phi
  register_new_node( phi, region );

  for (uint i2 = 1; i2 < phi->req(); i2++) {
    Node *x = phi->in(i2);
    // If we commoned up the cloned 'x' with another existing Node,
    // the existing Node picks up a new use.  We need to make the
    // existing Node occur higher up so it dominates its uses.
    Node *old_ctrl;
    IdealLoopTree *old_loop;

    if (x->is_Con()) {
      // Constant's control is always root.
      set_ctrl(x, C->root());
      continue;
    }
    // The occasional new node
    if (x->_idx >= old_unique) {     // Found a new, unplaced node?
      old_ctrl = nullptr;
      old_loop = nullptr;               // Not in any prior loop
    } else {
      old_ctrl = get_ctrl(x);
      old_loop = get_loop(old_ctrl); // Get prior loop
    }
    // New late point must dominate new use
    Node *new_ctrl = dom_lca(old_ctrl, region->in(i2));
    if (new_ctrl == old_ctrl) // Nothing is changed
      continue;

    IdealLoopTree *new_loop = get_loop(new_ctrl);

    // Don't move x into a loop if its uses are
    // outside of loop. Otherwise x will be cloned
    // for each use outside of this loop.
    IdealLoopTree *use_loop = get_loop(region);
    if (!new_loop->is_member(use_loop) &&
        (old_loop == nullptr || !new_loop->is_member(old_loop))) {
      // Take early control, later control will be recalculated
      // during next iteration of loop optimizations.
      new_ctrl = get_early_ctrl(x);
      new_loop = get_loop(new_ctrl);
    }
    // Set new location
    set_ctrl(x, new_ctrl);
    // If changing loop bodies, see if we need to collect into new body
    if (old_loop != new_loop) {
      if (old_loop && !old_loop->_child)
        old_loop->_body.yank(x);
      if (!new_loop->_child)
        new_loop->_body.push(x);  // Collect body info
    }
  }

  return phi;
}

// Test whether node 'x' can move into an inner loop relative to node 'n'.
// Note: The test is not exact. Returns true if 'x' COULD end up in an inner loop,
// BUT it can also return true and 'x' is in the outer loop
bool PhaseIdealLoop::can_move_to_inner_loop(Node* n, LoopNode* n_loop, Node* x) {
  IdealLoopTree* n_loop_tree = get_loop(n_loop);
  IdealLoopTree* x_loop_tree = get_loop(get_early_ctrl(x));
  // x_loop_tree should be outer or same loop as n_loop_tree
  return !x_loop_tree->is_member(n_loop_tree);
}

// Subtype checks that carry profile data don't common so look for a replacement by following edges
Node* PhaseIdealLoop::similar_subtype_check(const Node* x, Node* r_in) {
  if (x->is_SubTypeCheck()) {
    Node* in1 = x->in(1);
    for (DUIterator_Fast imax, i = in1->fast_outs(imax); i < imax; i++) {
      Node* u = in1->fast_out(i);
      if (u != x && u->is_SubTypeCheck() && u->in(1) == x->in(1) && u->in(2) == x->in(2)) {
        for (DUIterator_Fast jmax, j = u->fast_outs(jmax); j < jmax; j++) {
          Node* bol = u->fast_out(j);
          for (DUIterator_Fast kmax, k = bol->fast_outs(kmax); k < kmax; k++) {
            Node* iff = bol->fast_out(k);
            // Only dominating subtype checks are interesting: otherwise we risk replacing a subtype check by another with
            // unrelated profile
            if (iff->is_If() && is_dominator(iff, r_in)) {
              return u;
            }
          }
        }
      }
    }
  }
  return nullptr;
}

// Return true if 'n' is a Div or Mod node (without zero check If node which was removed earlier) with a loop phi divisor
// of a trip-counted (integer or long) loop with a backedge input that could be zero (include zero in its type range). In
// this case, we cannot split the division to the backedge as it could freely float above the loop exit check resulting in
// a division by zero. This situation is possible because the type of an increment node of an iv phi (trip-counter) could
// include zero while the iv phi does not (see PhiNode::Value() for trip-counted loops where we improve types of iv phis).
// We also need to check other loop phis as they could have been created in the same split-if pass when applying
// PhaseIdealLoop::split_thru_phi() to split nodes through an iv phi.
bool PhaseIdealLoop::cannot_split_division(const Node* n, const Node* region) const {
  const Type* zero;
  switch (n->Opcode()) {
    case Op_DivI:
    case Op_ModI:
      zero = TypeInt::ZERO;
      break;
    case Op_DivL:
    case Op_ModL:
      zero = TypeLong::ZERO;
      break;
    default:
      return false;
  }

  assert(n->in(0) == nullptr, "divisions with zero check should already have bailed out earlier in split-if");
  Node* divisor = n->in(2);
  return is_divisor_counted_loop_phi(divisor, region) &&
         loop_phi_backedge_type_contains_zero(divisor, zero);
}

bool PhaseIdealLoop::is_divisor_counted_loop_phi(const Node* divisor, const Node* loop) {
  return loop->is_BaseCountedLoop() && divisor->is_Phi() && divisor->in(0) == loop;
}

bool PhaseIdealLoop::loop_phi_backedge_type_contains_zero(const Node* phi_divisor, const Type* zero) const {
    return _igvn.type(phi_divisor->in(LoopNode::LoopBackControl))->filter_speculative(zero) != Type::TOP;
}

//------------------------------dominated_by------------------------------------
// Replace the dominated test with an obvious true or false.  Place it on the
// IGVN worklist for later cleanup.  Move control-dependent data Nodes on the
// live path up to the dominating control.
void PhaseIdealLoop::dominated_by(IfProjNode* prevdom, IfNode* iff, bool flip, bool exclude_loop_predicate) {
  if (VerifyLoopOptimizations && PrintOpto) { tty->print_cr("dominating test"); }

  // prevdom is the dominating projection of the dominating test.
  assert(iff->Opcode() == Op_If ||
         iff->Opcode() == Op_CountedLoopEnd ||
         iff->Opcode() == Op_LongCountedLoopEnd ||
         iff->Opcode() == Op_RangeCheck ||
         iff->Opcode() == Op_ParsePredicate,
        "Check this code when new subtype is added");

  int pop = prevdom->Opcode();
  assert( pop == Op_IfFalse || pop == Op_IfTrue, "" );
  if (flip) {
    if (pop == Op_IfTrue)
      pop = Op_IfFalse;
    else
      pop = Op_IfTrue;
  }
  // 'con' is set to true or false to kill the dominated test.
  Node *con = _igvn.makecon(pop == Op_IfTrue ? TypeInt::ONE : TypeInt::ZERO);
  set_ctrl(con, C->root()); // Constant gets a new use
  // Hack the dominated test
  _igvn.replace_input_of(iff, 1, con);

  // If I dont have a reachable TRUE and FALSE path following the IfNode then
  // I can assume this path reaches an infinite loop.  In this case it's not
  // important to optimize the data Nodes - either the whole compilation will
  // be tossed or this path (and all data Nodes) will go dead.
  if (iff->outcnt() != 2) return;

  // Make control-dependent data Nodes on the live path (path that will remain
  // once the dominated IF is removed) become control-dependent on the
  // dominating projection.
  Node* dp = iff->proj_out_or_null(pop == Op_IfTrue);

  // Loop predicates may have depending checks which should not
  // be skipped. For example, range check predicate has two checks
  // for lower and upper bounds.
  if (dp == nullptr)
    return;

  ProjNode* dp_proj  = dp->as_Proj();
  ProjNode* unc_proj = iff->proj_out(1 - dp_proj->_con)->as_Proj();
  if (exclude_loop_predicate &&
      (unc_proj->is_uncommon_trap_proj(Deoptimization::Reason_predicate) != nullptr ||
       unc_proj->is_uncommon_trap_proj(Deoptimization::Reason_profile_predicate) != nullptr ||
       unc_proj->is_uncommon_trap_proj(Deoptimization::Reason_range_check) != nullptr)) {
    // If this is a range check (IfNode::is_range_check), do not
    // reorder because Compile::allow_range_check_smearing might have
    // changed the check.
    return; // Let IGVN transformation change control dependence.
  }

  IdealLoopTree* old_loop = get_loop(dp);

  for (DUIterator_Fast imax, i = dp->fast_outs(imax); i < imax; i++) {
    Node* cd = dp->fast_out(i); // Control-dependent node
    // Do not rewire Div and Mod nodes which could have a zero divisor to avoid skipping their zero check.
    if (cd->depends_only_on_test() && _igvn.no_dependent_zero_check(cd)) {
      assert(cd->in(0) == dp, "");
      _igvn.replace_input_of(cd, 0, prevdom);
      set_early_ctrl(cd, false);
      IdealLoopTree* new_loop = get_loop(get_ctrl(cd));
      if (old_loop != new_loop) {
        if (!old_loop->_child) {
          old_loop->_body.yank(cd);
        }
        if (!new_loop->_child) {
          new_loop->_body.push(cd);
        }
      }
      --i;
      --imax;
    }
  }
}

//------------------------------has_local_phi_input----------------------------
// Return TRUE if 'n' has Phi inputs from its local block and no other
// block-local inputs (all non-local-phi inputs come from earlier blocks)
Node *PhaseIdealLoop::has_local_phi_input( Node *n ) {
  Node *n_ctrl = get_ctrl(n);
  // See if some inputs come from a Phi in this block, or from before
  // this block.
  uint i;
  for( i = 1; i < n->req(); i++ ) {
    Node *phi = n->in(i);
    if( phi->is_Phi() && phi->in(0) == n_ctrl )
      break;
  }
  if( i >= n->req() )
    return nullptr;                // No Phi inputs; nowhere to clone thru

  // Check for inputs created between 'n' and the Phi input.  These
  // must split as well; they have already been given the chance
  // (courtesy of a post-order visit) and since they did not we must
  // recover the 'cost' of splitting them by being very profitable
  // when splitting 'n'.  Since this is unlikely we simply give up.
  for( i = 1; i < n->req(); i++ ) {
    Node *m = n->in(i);
    if( get_ctrl(m) == n_ctrl && !m->is_Phi() ) {
      // We allow the special case of AddP's with no local inputs.
      // This allows us to split-up address expressions.
      if (m->is_AddP() &&
          get_ctrl(m->in(AddPNode::Base)) != n_ctrl &&
          get_ctrl(m->in(AddPNode::Address)) != n_ctrl &&
          get_ctrl(m->in(AddPNode::Offset)) != n_ctrl) {
        // Move the AddP up to the dominating point. That's fine because control of m's inputs
        // must dominate get_ctrl(m) == n_ctrl and we just checked that the input controls are != n_ctrl.
        Node* c = find_non_split_ctrl(idom(n_ctrl));
        if (c->is_OuterStripMinedLoop()) {
          c->as_Loop()->verify_strip_mined(1);
          c = c->in(LoopNode::EntryControl);
        }
        set_ctrl_and_loop(m, c);
        continue;
      }
      return nullptr;
    }
    assert(n->is_Phi() || m->is_Phi() || is_dominator(get_ctrl(m), n_ctrl), "m has strange control");
  }

  return n_ctrl;
}

// Replace expressions like ((V+I) << 2) with (V<<2 + I<<2).
Node* PhaseIdealLoop::remix_address_expressions_add_left_shift(Node* n, IdealLoopTree* n_loop, Node* n_ctrl, BasicType bt) {
  assert(bt == T_INT || bt == T_LONG, "only for integers");
  int n_op = n->Opcode();

  if (n_op == Op_LShift(bt)) {
    // Scale is loop invariant
    Node* scale = n->in(2);
    Node* scale_ctrl = get_ctrl(scale);
    IdealLoopTree* scale_loop = get_loop(scale_ctrl);
    if (n_loop == scale_loop || !scale_loop->is_member(n_loop)) {
      return nullptr;
    }
    const TypeInt* scale_t = scale->bottom_type()->isa_int();
    if (scale_t != nullptr && scale_t->is_con() && scale_t->get_con() >= 16) {
      return nullptr;              // Dont bother with byte/short masking
    }
    // Add must vary with loop (else shift would be loop-invariant)
    Node* add = n->in(1);
    Node* add_ctrl = get_ctrl(add);
    IdealLoopTree* add_loop = get_loop(add_ctrl);
    if (n_loop != add_loop) {
      return nullptr;  // happens w/ evil ZKM loops
    }

    // Convert I-V into I+ (0-V); same for V-I
    if (add->Opcode() == Op_Sub(bt) &&
        _igvn.type(add->in(1)) != TypeInteger::zero(bt)) {
      assert(add->Opcode() == Op_SubI || add->Opcode() == Op_SubL, "");
      Node* zero = _igvn.integercon(0, bt);
      set_ctrl(zero, C->root());
      Node* neg = SubNode::make(zero, add->in(2), bt);
      register_new_node(neg, get_ctrl(add->in(2)));
      add = AddNode::make(add->in(1), neg, bt);
      register_new_node(add, add_ctrl);
    }
    if (add->Opcode() != Op_Add(bt)) return nullptr;
    assert(add->Opcode() == Op_AddI || add->Opcode() == Op_AddL, "");
    // See if one add input is loop invariant
    Node* add_var = add->in(1);
    Node* add_var_ctrl = get_ctrl(add_var);
    IdealLoopTree* add_var_loop = get_loop(add_var_ctrl);
    Node* add_invar = add->in(2);
    Node* add_invar_ctrl = get_ctrl(add_invar);
    IdealLoopTree* add_invar_loop = get_loop(add_invar_ctrl);
    if (add_invar_loop == n_loop) {
      // Swap to find the invariant part
      add_invar = add_var;
      add_invar_ctrl = add_var_ctrl;
      add_invar_loop = add_var_loop;
      add_var = add->in(2);
    } else if (add_var_loop != n_loop) { // Else neither input is loop invariant
      return nullptr;
    }
    if (n_loop == add_invar_loop || !add_invar_loop->is_member(n_loop)) {
      return nullptr;              // No invariant part of the add?
    }

    // Yes!  Reshape address expression!
    Node* inv_scale = LShiftNode::make(add_invar, scale, bt);
    Node* inv_scale_ctrl =
            dom_depth(add_invar_ctrl) > dom_depth(scale_ctrl) ?
            add_invar_ctrl : scale_ctrl;
    register_new_node(inv_scale, inv_scale_ctrl);
    Node* var_scale = LShiftNode::make(add_var, scale, bt);
    register_new_node(var_scale, n_ctrl);
    Node* var_add = AddNode::make(var_scale, inv_scale, bt);
    register_new_node(var_add, n_ctrl);
    _igvn.replace_node(n, var_add);
    return var_add;
  }
  return nullptr;
}

//------------------------------remix_address_expressions----------------------
// Rework addressing expressions to get the most loop-invariant stuff
// moved out.  We'd like to do all associative operators, but it's especially
// important (common) to do address expressions.
Node* PhaseIdealLoop::remix_address_expressions(Node* n) {
  if (!has_ctrl(n))  return nullptr;
  Node* n_ctrl = get_ctrl(n);
  IdealLoopTree* n_loop = get_loop(n_ctrl);

  // See if 'n' mixes loop-varying and loop-invariant inputs and
  // itself is loop-varying.

  // Only interested in binary ops (and AddP)
  if (n->req() < 3 || n->req() > 4) return nullptr;

  Node* n1_ctrl = get_ctrl(n->in(                    1));
  Node* n2_ctrl = get_ctrl(n->in(                    2));
  Node* n3_ctrl = get_ctrl(n->in(n->req() == 3 ? 2 : 3));
  IdealLoopTree* n1_loop = get_loop(n1_ctrl);
  IdealLoopTree* n2_loop = get_loop(n2_ctrl);
  IdealLoopTree* n3_loop = get_loop(n3_ctrl);

  // Does one of my inputs spin in a tighter loop than self?
  if ((n_loop->is_member(n1_loop) && n_loop != n1_loop) ||
      (n_loop->is_member(n2_loop) && n_loop != n2_loop) ||
      (n_loop->is_member(n3_loop) && n_loop != n3_loop)) {
    return nullptr;                // Leave well enough alone
  }

  // Is at least one of my inputs loop-invariant?
  if (n1_loop == n_loop &&
      n2_loop == n_loop &&
      n3_loop == n_loop) {
    return nullptr;                // No loop-invariant inputs
  }

  Node* res = remix_address_expressions_add_left_shift(n, n_loop, n_ctrl, T_INT);
  if (res != nullptr) {
    return res;
  }
  res = remix_address_expressions_add_left_shift(n, n_loop, n_ctrl, T_LONG);
  if (res != nullptr) {
    return res;
  }

  int n_op = n->Opcode();
  // Replace (I+V) with (V+I)
  if (n_op == Op_AddI ||
      n_op == Op_AddL ||
      n_op == Op_AddF ||
      n_op == Op_AddD ||
      n_op == Op_MulI ||
      n_op == Op_MulL ||
      n_op == Op_MulF ||
      n_op == Op_MulD) {
    if (n2_loop == n_loop) {
      assert(n1_loop != n_loop, "");
      n->swap_edges(1, 2);
    }
  }

  // Replace ((I1 +p V) +p I2) with ((I1 +p I2) +p V),
  // but not if I2 is a constant.
  if (n_op == Op_AddP) {
    if (n2_loop == n_loop && n3_loop != n_loop) {
      if (n->in(2)->Opcode() == Op_AddP && !n->in(3)->is_Con()) {
        Node* n22_ctrl = get_ctrl(n->in(2)->in(2));
        Node* n23_ctrl = get_ctrl(n->in(2)->in(3));
        IdealLoopTree* n22loop = get_loop(n22_ctrl);
        IdealLoopTree* n23_loop = get_loop(n23_ctrl);
        if (n22loop != n_loop && n22loop->is_member(n_loop) &&
            n23_loop == n_loop) {
          Node* add1 = new AddPNode(n->in(1), n->in(2)->in(2), n->in(3));
          // Stuff new AddP in the loop preheader
          register_new_node(add1, n_loop->_head->as_Loop()->skip_strip_mined(1)->in(LoopNode::EntryControl));
          Node* add2 = new AddPNode(n->in(1), add1, n->in(2)->in(3));
          register_new_node(add2, n_ctrl);
          _igvn.replace_node(n, add2);
          return add2;
        }
      }
    }

    // Replace (I1 +p (I2 + V)) with ((I1 +p I2) +p V)
    if (n2_loop != n_loop && n3_loop == n_loop) {
      if (n->in(3)->Opcode() == Op_AddX) {
        Node* V = n->in(3)->in(1);
        Node* I = n->in(3)->in(2);
        if (is_member(n_loop,get_ctrl(V))) {
        } else {
          Node *tmp = V; V = I; I = tmp;
        }
        if (!is_member(n_loop,get_ctrl(I))) {
          Node* add1 = new AddPNode(n->in(1), n->in(2), I);
          // Stuff new AddP in the loop preheader
          register_new_node(add1, n_loop->_head->as_Loop()->skip_strip_mined(1)->in(LoopNode::EntryControl));
          Node* add2 = new AddPNode(n->in(1), add1, V);
          register_new_node(add2, n_ctrl);
          _igvn.replace_node(n, add2);
          return add2;
        }
      }
    }
  }

  return nullptr;
}

// Optimize ((in1[2*i] * in2[2*i]) + (in1[2*i+1] * in2[2*i+1]))
Node *PhaseIdealLoop::convert_add_to_muladd(Node* n) {
  assert(n->Opcode() == Op_AddI, "sanity");
  Node * nn = nullptr;
  Node * in1 = n->in(1);
  Node * in2 = n->in(2);
  if (in1->Opcode() == Op_MulI && in2->Opcode() == Op_MulI) {
    IdealLoopTree* loop_n = get_loop(get_ctrl(n));
    if (loop_n->is_counted() &&
        loop_n->_head->as_Loop()->is_valid_counted_loop(T_INT) &&
        Matcher::match_rule_supported(Op_MulAddVS2VI) &&
        Matcher::match_rule_supported(Op_MulAddS2I)) {
      Node* mul_in1 = in1->in(1);
      Node* mul_in2 = in1->in(2);
      Node* mul_in3 = in2->in(1);
      Node* mul_in4 = in2->in(2);
      if (mul_in1->Opcode() == Op_LoadS &&
          mul_in2->Opcode() == Op_LoadS &&
          mul_in3->Opcode() == Op_LoadS &&
          mul_in4->Opcode() == Op_LoadS) {
        IdealLoopTree* loop1 = get_loop(get_ctrl(mul_in1));
        IdealLoopTree* loop2 = get_loop(get_ctrl(mul_in2));
        IdealLoopTree* loop3 = get_loop(get_ctrl(mul_in3));
        IdealLoopTree* loop4 = get_loop(get_ctrl(mul_in4));
        IdealLoopTree* loop5 = get_loop(get_ctrl(in1));
        IdealLoopTree* loop6 = get_loop(get_ctrl(in2));
        // All nodes should be in the same counted loop.
        if (loop_n == loop1 && loop_n == loop2 && loop_n == loop3 &&
            loop_n == loop4 && loop_n == loop5 && loop_n == loop6) {
          Node* adr1 = mul_in1->in(MemNode::Address);
          Node* adr2 = mul_in2->in(MemNode::Address);
          Node* adr3 = mul_in3->in(MemNode::Address);
          Node* adr4 = mul_in4->in(MemNode::Address);
          if (adr1->is_AddP() && adr2->is_AddP() && adr3->is_AddP() && adr4->is_AddP()) {
            if ((adr1->in(AddPNode::Base) == adr3->in(AddPNode::Base)) &&
                (adr2->in(AddPNode::Base) == adr4->in(AddPNode::Base))) {
              nn = new MulAddS2INode(mul_in1, mul_in2, mul_in3, mul_in4);
              register_new_node(nn, get_ctrl(n));
              _igvn.replace_node(n, nn);
              return nn;
            } else if ((adr1->in(AddPNode::Base) == adr4->in(AddPNode::Base)) &&
                       (adr2->in(AddPNode::Base) == adr3->in(AddPNode::Base))) {
              nn = new MulAddS2INode(mul_in1, mul_in2, mul_in4, mul_in3);
              register_new_node(nn, get_ctrl(n));
              _igvn.replace_node(n, nn);
              return nn;
            }
          }
        }
      }
    }
  }
  return nn;
}

//------------------------------conditional_move-------------------------------
// Attempt to replace a Phi with a conditional move.  We have some pretty
// strict profitability requirements.  All Phis at the merge point must
// be converted, so we can remove the control flow.  We need to limit the
// number of c-moves to a small handful.  All code that was in the side-arms
// of the CFG diamond is now speculatively executed.  This code has to be
// "cheap enough".  We are pretty much limited to CFG diamonds that merge
// 1 or 2 items with a total of 1 or 2 ops executed speculatively.
Node *PhaseIdealLoop::conditional_move( Node *region ) {

  assert(region->is_Region(), "sanity check");
  if (region->req() != 3) return nullptr;

  // Check for CFG diamond
  Node *lp = region->in(1);
  Node *rp = region->in(2);
  if (!lp || !rp) return nullptr;
  Node *lp_c = lp->in(0);
  if (lp_c == nullptr || lp_c != rp->in(0) || !lp_c->is_If()) return nullptr;
  IfNode *iff = lp_c->as_If();

  // Check for ops pinned in an arm of the diamond.
  // Can't remove the control flow in this case
  if (lp->outcnt() > 1) return nullptr;
  if (rp->outcnt() > 1) return nullptr;

  IdealLoopTree* r_loop = get_loop(region);
  assert(r_loop == get_loop(iff), "sanity");
  // Always convert to CMOVE if all results are used only outside this loop.
  bool used_inside_loop = (r_loop == _ltree_root);

  // Check profitability
  int cost = 0;
  int phis = 0;
  for (DUIterator_Fast imax, i = region->fast_outs(imax); i < imax; i++) {
    Node *out = region->fast_out(i);
    if (!out->is_Phi()) continue; // Ignore other control edges, etc
    phis++;
    PhiNode* phi = out->as_Phi();
    BasicType bt = phi->type()->basic_type();
    switch (bt) {
    case T_DOUBLE:
    case T_FLOAT:
      if (C->use_cmove()) {
        continue; //TODO: maybe we want to add some cost
      }
      cost += Matcher::float_cmove_cost(); // Could be very expensive
      break;
    case T_LONG: {
      cost += Matcher::long_cmove_cost(); // May encodes as 2 CMOV's
    }
    case T_INT:                 // These all CMOV fine
    case T_ADDRESS: {           // (RawPtr)
      cost++;
      break;
    }
    case T_NARROWOOP: // Fall through
    case T_OBJECT: {            // Base oops are OK, but not derived oops
      const TypeOopPtr *tp = phi->type()->make_ptr()->isa_oopptr();
      // Derived pointers are Bad (tm): what's the Base (for GC purposes) of a
      // CMOVE'd derived pointer?  It's a CMOVE'd derived base.  Thus
      // CMOVE'ing a derived pointer requires we also CMOVE the base.  If we
      // have a Phi for the base here that we convert to a CMOVE all is well
      // and good.  But if the base is dead, we'll not make a CMOVE.  Later
      // the allocator will have to produce a base by creating a CMOVE of the
      // relevant bases.  This puts the allocator in the business of
      // manufacturing expensive instructions, generally a bad plan.
      // Just Say No to Conditionally-Moved Derived Pointers.
      if (tp && tp->offset() != 0)
        return nullptr;
      cost++;
      break;
    }
    default:
      return nullptr;              // In particular, can't do memory or I/O
    }
    // Add in cost any speculative ops
    for (uint j = 1; j < region->req(); j++) {
      Node *proj = region->in(j);
      Node *inp = phi->in(j);
      if (get_ctrl(inp) == proj) { // Found local op
        cost++;
        // Check for a chain of dependent ops; these will all become
        // speculative in a CMOV.
        for (uint k = 1; k < inp->req(); k++)
          if (get_ctrl(inp->in(k)) == proj)
            cost += ConditionalMoveLimit; // Too much speculative goo
      }
    }
    // See if the Phi is used by a Cmp or Narrow oop Decode/Encode.
    // This will likely Split-If, a higher-payoff operation.
    for (DUIterator_Fast kmax, k = phi->fast_outs(kmax); k < kmax; k++) {
      Node* use = phi->fast_out(k);
      if (use->is_Cmp() || use->is_DecodeNarrowPtr() || use->is_EncodeNarrowPtr())
        cost += ConditionalMoveLimit;
      // Is there a use inside the loop?
      // Note: check only basic types since CMoveP is pinned.
      if (!used_inside_loop && is_java_primitive(bt)) {
        IdealLoopTree* u_loop = get_loop(has_ctrl(use) ? get_ctrl(use) : use);
        if (r_loop == u_loop || r_loop->is_member(u_loop)) {
          used_inside_loop = true;
        }
      }
    }
  }//for
  Node* bol = iff->in(1);
  if (bol->Opcode() == Op_Opaque4) {
    return nullptr; // Ignore loop predicate checks (the Opaque4 ensures they will go away)
  }
  assert(bol->Opcode() == Op_Bool, "Unexpected node");
  int cmp_op = bol->in(1)->Opcode();
  if (cmp_op == Op_SubTypeCheck) { // SubTypeCheck expansion expects an IfNode
    return nullptr;
  }
  // It is expensive to generate flags from a float compare.
  // Avoid duplicated float compare.
  if (phis > 1 && (cmp_op == Op_CmpF || cmp_op == Op_CmpD)) return nullptr;

  // Ignore cost if CMOVE can be moved outside the loop.
  if (used_inside_loop && cost >= ConditionalMoveLimit) {
    return nullptr;
  }
  // Check for highly predictable branch.  No point in CMOV'ing if
  // we are going to predict accurately all the time.
  constexpr float infrequent_prob = PROB_UNLIKELY_MAG(2);
  if (C->use_cmove() && (cmp_op == Op_CmpF || cmp_op == Op_CmpD)) {
    //keep going
  } else if (iff->_prob < infrequent_prob || iff->_prob > (1.0f - infrequent_prob)) {
    return nullptr;
  }

  // --------------
  // Now replace all Phis with CMOV's
  Node *cmov_ctrl = iff->in(0);
  uint flip = (lp->Opcode() == Op_IfTrue);
  Node_List wq;
  while (1) {
    PhiNode* phi = nullptr;
    for (DUIterator_Fast imax, i = region->fast_outs(imax); i < imax; i++) {
      Node *out = region->fast_out(i);
      if (out->is_Phi()) {
        phi = out->as_Phi();
        break;
      }
    }
    if (phi == nullptr || _igvn.type(phi) == Type::TOP) {
      break;
    }
    if (PrintOpto && VerifyLoopOptimizations) { tty->print_cr("CMOV"); }
    // Move speculative ops
    wq.push(phi);
    while (wq.size() > 0) {
      Node *n = wq.pop();
      for (uint j = 1; j < n->req(); j++) {
        Node* m = n->in(j);
        if (m != nullptr && !is_dominator(get_ctrl(m), cmov_ctrl)) {
#ifndef PRODUCT
          if (PrintOpto && VerifyLoopOptimizations) {
            tty->print("  speculate: ");
            m->dump();
          }
#endif
          set_ctrl(m, cmov_ctrl);
          wq.push(m);
        }
      }
    }
    Node *cmov = CMoveNode::make(cmov_ctrl, iff->in(1), phi->in(1+flip), phi->in(2-flip), _igvn.type(phi));
    register_new_node( cmov, cmov_ctrl );
    _igvn.replace_node( phi, cmov );
#ifndef PRODUCT
    if (TraceLoopOpts) {
      tty->print("CMOV  ");
      r_loop->dump_head();
      if (Verbose) {
        bol->in(1)->dump(1);
        cmov->dump(1);
      }
    }
    DEBUG_ONLY( if (VerifyLoopOptimizations) { verify(); } );
#endif
  }

  // The useless CFG diamond will fold up later; see the optimization in
  // RegionNode::Ideal.
  _igvn._worklist.push(region);

  return iff->in(1);
}

static void enqueue_cfg_uses(Node* m, Unique_Node_List& wq) {
  for (DUIterator_Fast imax, i = m->fast_outs(imax); i < imax; i++) {
    Node* u = m->fast_out(i);
    if (u->is_CFG()) {
      if (u->is_NeverBranch()) {
        u = u->as_NeverBranch()->proj_out(0);
        enqueue_cfg_uses(u, wq);
      } else {
        wq.push(u);
      }
    }
  }
}

// Try moving a store out of a loop, right before the loop
Node* PhaseIdealLoop::try_move_store_before_loop(Node* n, Node *n_ctrl) {
  // Store has to be first in the loop body
  IdealLoopTree *n_loop = get_loop(n_ctrl);
  if (n->is_Store() && n_loop != _ltree_root &&
      n_loop->is_loop() && n_loop->_head->is_Loop() &&
      n->in(0) != nullptr) {
    Node* address = n->in(MemNode::Address);
    Node* value = n->in(MemNode::ValueIn);
    Node* mem = n->in(MemNode::Memory);
    IdealLoopTree* address_loop = get_loop(get_ctrl(address));
    IdealLoopTree* value_loop = get_loop(get_ctrl(value));

    // - address and value must be loop invariant
    // - memory must be a memory Phi for the loop
    // - Store must be the only store on this memory slice in the
    // loop: if there's another store following this one then value
    // written at iteration i by the second store could be overwritten
    // at iteration i+n by the first store: it's not safe to move the
    // first store out of the loop
    // - nothing must observe the memory Phi: it guarantees no read
    // before the store, we are also guaranteed the store post
    // dominates the loop head (ignoring a possible early
    // exit). Otherwise there would be extra Phi involved between the
    // loop's Phi and the store.
    // - there must be no early exit from the loop before the Store
    // (such an exit most of the time would be an extra use of the
    // memory Phi but sometimes is a bottom memory Phi that takes the
    // store as input).

    if (!n_loop->is_member(address_loop) &&
        !n_loop->is_member(value_loop) &&
        mem->is_Phi() && mem->in(0) == n_loop->_head &&
        mem->outcnt() == 1 &&
        mem->in(LoopNode::LoopBackControl) == n) {

      assert(n_loop->_tail != nullptr, "need a tail");
      assert(is_dominator(n_ctrl, n_loop->_tail), "store control must not be in a branch in the loop");

      // Verify that there's no early exit of the loop before the store.
      bool ctrl_ok = false;
      {
        // Follow control from loop head until n, we exit the loop or
        // we reach the tail
        ResourceMark rm;
        Unique_Node_List wq;
        wq.push(n_loop->_head);

        for (uint next = 0; next < wq.size(); ++next) {
          Node *m = wq.at(next);
          if (m == n->in(0)) {
            ctrl_ok = true;
            continue;
          }
          assert(!has_ctrl(m), "should be CFG");
          if (!n_loop->is_member(get_loop(m)) || m == n_loop->_tail) {
            ctrl_ok = false;
            break;
          }
          enqueue_cfg_uses(m, wq);
          if (wq.size() > 10) {
            ctrl_ok = false;
            break;
          }
        }
      }
      if (ctrl_ok) {
        // move the Store
        _igvn.replace_input_of(mem, LoopNode::LoopBackControl, mem);
        _igvn.replace_input_of(n, 0, n_loop->_head->as_Loop()->skip_strip_mined()->in(LoopNode::EntryControl));
        _igvn.replace_input_of(n, MemNode::Memory, mem->in(LoopNode::EntryControl));
        // Disconnect the phi now. An empty phi can confuse other
        // optimizations in this pass of loop opts.
        _igvn.replace_node(mem, mem->in(LoopNode::EntryControl));
        n_loop->_body.yank(mem);

        set_ctrl_and_loop(n, n->in(0));

        return n;
      }
    }
  }
  return nullptr;
}

// Try moving a store out of a loop, right after the loop
void PhaseIdealLoop::try_move_store_after_loop(Node* n) {
  if (n->is_Store() && n->in(0) != nullptr) {
    Node *n_ctrl = get_ctrl(n);
    IdealLoopTree *n_loop = get_loop(n_ctrl);
    // Store must be in a loop
    if (n_loop != _ltree_root && !n_loop->_irreducible) {
      Node* address = n->in(MemNode::Address);
      Node* value = n->in(MemNode::ValueIn);
      IdealLoopTree* address_loop = get_loop(get_ctrl(address));
      // address must be loop invariant
      if (!n_loop->is_member(address_loop)) {
        // Store must be last on this memory slice in the loop and
        // nothing in the loop must observe it
        Node* phi = nullptr;
        for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
          Node* u = n->fast_out(i);
          if (has_ctrl(u)) { // control use?
            IdealLoopTree *u_loop = get_loop(get_ctrl(u));
            if (!n_loop->is_member(u_loop)) {
              continue;
            }
            if (u->is_Phi() && u->in(0) == n_loop->_head) {
              assert(_igvn.type(u) == Type::MEMORY, "bad phi");
              // multiple phis on the same slice are possible
              if (phi != nullptr) {
                return;
              }
              phi = u;
              continue;
            }
          }
          return;
        }
        if (phi != nullptr) {
          // Nothing in the loop before the store (next iteration)
          // must observe the stored value
          bool mem_ok = true;
          {
            ResourceMark rm;
            Unique_Node_List wq;
            wq.push(phi);
            for (uint next = 0; next < wq.size() && mem_ok; ++next) {
              Node *m = wq.at(next);
              for (DUIterator_Fast imax, i = m->fast_outs(imax); i < imax && mem_ok; i++) {
                Node* u = m->fast_out(i);
                if (u->is_Store() || u->is_Phi()) {
                  if (u != n) {
                    wq.push(u);
                    mem_ok = (wq.size() <= 10);
                  }
                } else {
                  mem_ok = false;
                  break;
                }
              }
            }
          }
          if (mem_ok) {
            // Move the store out of the loop if the LCA of all
            // users (except for the phi) is outside the loop.
            Node* hook = new Node(1);
            hook->init_req(0, n_ctrl); // Add an input to prevent hook from being dead
            _igvn.rehash_node_delayed(phi);
            int count = phi->replace_edge(n, hook, &_igvn);
            assert(count > 0, "inconsistent phi");

            // Compute latest point this store can go
            Node* lca = get_late_ctrl(n, get_ctrl(n));
            if (lca->is_OuterStripMinedLoop()) {
              lca = lca->in(LoopNode::EntryControl);
            }
            if (n_loop->is_member(get_loop(lca))) {
              // LCA is in the loop - bail out
              _igvn.replace_node(hook, n);
              return;
            }
#ifdef ASSERT
            if (n_loop->_head->is_Loop() && n_loop->_head->as_Loop()->is_strip_mined()) {
              assert(n_loop->_head->Opcode() == Op_CountedLoop, "outer loop is a strip mined");
              n_loop->_head->as_Loop()->verify_strip_mined(1);
              Node* outer = n_loop->_head->as_CountedLoop()->outer_loop();
              IdealLoopTree* outer_loop = get_loop(outer);
              assert(n_loop->_parent == outer_loop, "broken loop tree");
              assert(get_loop(lca) == outer_loop, "safepoint in outer loop consume all memory state");
            }
#endif
            lca = place_outside_loop(lca, n_loop);
            assert(!n_loop->is_member(get_loop(lca)), "control must not be back in the loop");
            assert(get_loop(lca)->_nest < n_loop->_nest || lca->in(0)->is_NeverBranch(), "must not be moved into inner loop");

            // Move store out of the loop
            _igvn.replace_node(hook, n->in(MemNode::Memory));
            _igvn.replace_input_of(n, 0, lca);
            set_ctrl_and_loop(n, lca);

            // Disconnect the phi now. An empty phi can confuse other
            // optimizations in this pass of loop opts..
            if (phi->in(LoopNode::LoopBackControl) == phi) {
              _igvn.replace_node(phi, phi->in(LoopNode::EntryControl));
              n_loop->_body.yank(phi);
            }
          }
        }
      }
    }
  }
}

//------------------------------split_if_with_blocks_pre-----------------------
// Do the real work in a non-recursive function.  Data nodes want to be
// cloned in the pre-order so they can feed each other nicely.
Node *PhaseIdealLoop::split_if_with_blocks_pre( Node *n ) {
  // Cloning these guys is unlikely to win
  int n_op = n->Opcode();
  if (n_op == Op_MergeMem) {
    return n;
  }
  if (n->is_Proj()) {
    return n;
  }
  // Do not clone-up CmpFXXX variations, as these are always
  // followed by a CmpI
  if (n->is_Cmp()) {
    return n;
  }
  // Attempt to use a conditional move instead of a phi/branch
  if (ConditionalMoveLimit > 0 && n_op == Op_Region) {
    Node *cmov = conditional_move( n );
    if (cmov) {
      return cmov;
    }
  }
  if (n->is_CFG() || n->is_LoadStore()) {
    return n;
  }
  if (n->is_Opaque1()) { // Opaque nodes cannot be mod'd
    if (!C->major_progress()) {   // If chance of no more loop opts...
      _igvn._worklist.push(n);  // maybe we'll remove them
    }
    return n;
  }

  if (n->is_Con()) {
    return n;   // No cloning for Con nodes
  }

  Node *n_ctrl = get_ctrl(n);
  if (!n_ctrl) {
    return n;       // Dead node
  }

  Node* res = try_move_store_before_loop(n, n_ctrl);
  if (res != nullptr) {
    return n;
  }

  // Attempt to remix address expressions for loop invariants
  Node *m = remix_address_expressions( n );
  if( m ) return m;

  if (n_op == Op_AddI) {
    Node *nn = convert_add_to_muladd( n );
    if ( nn ) return nn;
  }

  if (n->is_ConstraintCast()) {
    Node* dom_cast = n->as_ConstraintCast()->dominating_cast(&_igvn, this);
    // ConstraintCastNode::dominating_cast() uses node control input to determine domination.
    // Node control inputs don't necessarily agree with loop control info (due to
    // transformations happened in between), thus additional dominance check is needed
    // to keep loop info valid.
    if (dom_cast != nullptr && is_dominator(get_ctrl(dom_cast), get_ctrl(n))) {
      _igvn.replace_node(n, dom_cast);
      return dom_cast;
    }
  }

  // Determine if the Node has inputs from some local Phi.
  // Returns the block to clone thru.
  Node *n_blk = has_local_phi_input( n );
  if( !n_blk ) return n;

  // Do not clone the trip counter through on a CountedLoop
  // (messes up the canonical shape).
  if (((n_blk->is_CountedLoop() || (n_blk->is_Loop() && n_blk->as_Loop()->is_loop_nest_inner_loop())) && n->Opcode() == Op_AddI) ||
      (n_blk->is_LongCountedLoop() && n->Opcode() == Op_AddL)) {
    return n;
  }
  // Pushing a shift through the iv Phi can get in the way of addressing optimizations or range check elimination
  if (n_blk->is_BaseCountedLoop() && n->Opcode() == Op_LShift(n_blk->as_BaseCountedLoop()->bt()) &&
      n->in(1) == n_blk->as_BaseCountedLoop()->phi()) {
    return n;
  }

  // Check for having no control input; not pinned.  Allow
  // dominating control.
  if (n->in(0)) {
    Node *dom = idom(n_blk);
    if (dom_lca(n->in(0), dom) != n->in(0)) {
      return n;
    }
  }
  // Policy: when is it profitable.  You must get more wins than
  // policy before it is considered profitable.  Policy is usually 0,
  // so 1 win is considered profitable.  Big merges will require big
  // cloning, so get a larger policy.
  int policy = n_blk->req() >> 2;

  // If the loop is a candidate for range check elimination,
  // delay splitting through it's phi until a later loop optimization
  if (n_blk->is_BaseCountedLoop()) {
    IdealLoopTree *lp = get_loop(n_blk);
    if (lp && lp->_rce_candidate) {
      return n;
    }
  }

  if (must_throttle_split_if()) return n;

  // Split 'n' through the merge point if it is profitable
  Node *phi = split_thru_phi( n, n_blk, policy );
  if (!phi) return n;

  // Found a Phi to split thru!
  // Replace 'n' with the new phi
  _igvn.replace_node( n, phi );
  // Moved a load around the loop, 'en-registering' something.
  if (n_blk->is_Loop() && n->is_Load() &&
      !phi->in(LoopNode::LoopBackControl)->is_Load())
    C->set_major_progress();

  return phi;
}

static bool merge_point_too_heavy(Compile* C, Node* region) {
  // Bail out if the region and its phis have too many users.
  int weight = 0;
  for (DUIterator_Fast imax, i = region->fast_outs(imax); i < imax; i++) {
    weight += region->fast_out(i)->outcnt();
  }
  int nodes_left = C->max_node_limit() - C->live_nodes();
  if (weight * 8 > nodes_left) {
    if (PrintOpto) {
      tty->print_cr("*** Split-if bails out:  %d nodes, region weight %d", C->unique(), weight);
    }
    return true;
  } else {
    return false;
  }
}

static bool merge_point_safe(Node* region) {
  // 4799512: Stop split_if_with_blocks from splitting a block with a ConvI2LNode
  // having a PhiNode input. This sidesteps the dangerous case where the split
  // ConvI2LNode may become TOP if the input Value() does not
  // overlap the ConvI2L range, leaving a node which may not dominate its
  // uses.
  // A better fix for this problem can be found in the BugTraq entry, but
  // expediency for Mantis demands this hack.
#ifdef _LP64
  for (DUIterator_Fast imax, i = region->fast_outs(imax); i < imax; i++) {
    Node* n = region->fast_out(i);
    if (n->is_Phi()) {
      for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
        Node* m = n->fast_out(j);
        if (m->Opcode() == Op_ConvI2L)
          return false;
        if (m->is_CastII()) {
          return false;
        }
      }
    }
  }
#endif
  return true;
}


//------------------------------place_outside_loop---------------------------------
// Place some computation outside of this loop on the path to the use passed as argument
Node* PhaseIdealLoop::place_outside_loop(Node* useblock, IdealLoopTree* loop) const {
  Node* head = loop->_head;
  assert(!loop->is_member(get_loop(useblock)), "must be outside loop");
  if (head->is_Loop() && head->as_Loop()->is_strip_mined()) {
    loop = loop->_parent;
    assert(loop->_head->is_OuterStripMinedLoop(), "malformed strip mined loop");
  }

  // Pick control right outside the loop
  for (;;) {
    Node* dom = idom(useblock);
    if (loop->is_member(get_loop(dom)) ||
        // NeverBranch nodes are not assigned to the loop when constructed
        (dom->is_NeverBranch() && loop->is_member(get_loop(dom->in(0))))) {
      break;
    }
    useblock = dom;
  }
  assert(find_non_split_ctrl(useblock) == useblock, "should be non split control");
  return useblock;
}


bool PhaseIdealLoop::identical_backtoback_ifs(Node *n) {
  if (!n->is_If() || n->is_BaseCountedLoopEnd()) {
    return false;
  }
  if (!n->in(0)->is_Region()) {
    return false;
  }

  Node* region = n->in(0);
  Node* dom = idom(region);
  if (!dom->is_If() ||  !n->as_If()->same_condition(dom, &_igvn)) {
    return false;
  }
  IfNode* dom_if = dom->as_If();
  Node* proj_true = dom_if->proj_out(1);
  Node* proj_false = dom_if->proj_out(0);

  for (uint i = 1; i < region->req(); i++) {
    if (is_dominator(proj_true, region->in(i))) {
      continue;
    }
    if (is_dominator(proj_false, region->in(i))) {
      continue;
    }
    return false;
  }

  return true;
}


bool PhaseIdealLoop::can_split_if(Node* n_ctrl) {
  if (must_throttle_split_if()) {
    return false;
  }

  // Do not do 'split-if' if irreducible loops are present.
  if (_has_irreducible_loops) {
    return false;
  }

  if (merge_point_too_heavy(C, n_ctrl)) {
    return false;
  }

  // Do not do 'split-if' if some paths are dead.  First do dead code
  // elimination and then see if its still profitable.
  for (uint i = 1; i < n_ctrl->req(); i++) {
    if (n_ctrl->in(i) == C->top()) {
      return false;
    }
  }

  // If trying to do a 'Split-If' at the loop head, it is only
  // profitable if the cmp folds up on BOTH paths.  Otherwise we
  // risk peeling a loop forever.

  // CNC - Disabled for now.  Requires careful handling of loop
  // body selection for the cloned code.  Also, make sure we check
  // for any input path not being in the same loop as n_ctrl.  For
  // irreducible loops we cannot check for 'n_ctrl->is_Loop()'
  // because the alternative loop entry points won't be converted
  // into LoopNodes.
  IdealLoopTree *n_loop = get_loop(n_ctrl);
  for (uint j = 1; j < n_ctrl->req(); j++) {
    if (get_loop(n_ctrl->in(j)) != n_loop) {
      return false;
    }
  }

  // Check for safety of the merge point.
  if (!merge_point_safe(n_ctrl)) {
    return false;
  }

  return true;
}

// Detect if the node is the inner strip-mined loop
// Return: null if it's not the case, or the exit of outer strip-mined loop
static Node* is_inner_of_stripmined_loop(const Node* out) {
  Node* out_le = nullptr;

  if (out->is_CountedLoopEnd()) {
      const CountedLoopNode* loop = out->as_CountedLoopEnd()->loopnode();

      if (loop != nullptr && loop->is_strip_mined()) {
        out_le = loop->in(LoopNode::EntryControl)->as_OuterStripMinedLoop()->outer_loop_exit();
      }
  }

  return out_le;
}

//------------------------------split_if_with_blocks_post----------------------
// Do the real work in a non-recursive function.  CFG hackery wants to be
// in the post-order, so it can dirty the I-DOM info and not use the dirtied
// info.
void PhaseIdealLoop::split_if_with_blocks_post(Node *n) {

  // Cloning Cmp through Phi's involves the split-if transform.
  // FastLock is not used by an If
  if (n->is_Cmp() && !n->is_FastLock()) {
    Node *n_ctrl = get_ctrl(n);
    // Determine if the Node has inputs from some local Phi.
    // Returns the block to clone thru.
    Node *n_blk = has_local_phi_input(n);
    if (n_blk != n_ctrl) {
      return;
    }

    if (!can_split_if(n_ctrl)) {
      return;
    }

    if (n->outcnt() != 1) {
      return; // Multiple bool's from 1 compare?
    }
    Node *bol = n->unique_out();
    assert(bol->is_Bool(), "expect a bool here");
    if (bol->outcnt() != 1) {
      return;// Multiple branches from 1 compare?
    }
    Node *iff = bol->unique_out();

    // Check some safety conditions
    if (iff->is_If()) {        // Classic split-if?
      if (iff->in(0) != n_ctrl) {
        return; // Compare must be in same blk as if
      }
    } else if (iff->is_CMove()) { // Trying to split-up a CMOVE
      // Can't split CMove with different control.
      if (get_ctrl(iff) != n_ctrl) {
        return;
      }
      if (get_ctrl(iff->in(2)) == n_ctrl ||
          get_ctrl(iff->in(3)) == n_ctrl) {
        return;                 // Inputs not yet split-up
      }
      if (get_loop(n_ctrl) != get_loop(get_ctrl(iff))) {
        return;                 // Loop-invar test gates loop-varying CMOVE
      }
    } else {
      return;  // some other kind of node, such as an Allocate
    }

    // When is split-if profitable?  Every 'win' on means some control flow
    // goes dead, so it's almost always a win.
    int policy = 0;
    // Split compare 'n' through the merge point if it is profitable
    Node *phi = split_thru_phi( n, n_ctrl, policy);
    if (!phi) {
      return;
    }

    // Found a Phi to split thru!
    // Replace 'n' with the new phi
    _igvn.replace_node(n, phi);

    // Now split the bool up thru the phi
    Node *bolphi = split_thru_phi(bol, n_ctrl, -1);
    guarantee(bolphi != nullptr, "null boolean phi node");

    _igvn.replace_node(bol, bolphi);
    assert(iff->in(1) == bolphi, "");

    if (bolphi->Value(&_igvn)->singleton()) {
      return;
    }

    // Conditional-move?  Must split up now
    if (!iff->is_If()) {
      Node *cmovphi = split_thru_phi(iff, n_ctrl, -1);
      _igvn.replace_node(iff, cmovphi);
      return;
    }

    // Now split the IF
    C->print_method(PHASE_BEFORE_SPLIT_IF, 4, iff);
    if ((PrintOpto && VerifyLoopOptimizations) || TraceLoopOpts) {
      tty->print_cr("Split-If");
    }
    do_split_if(iff);
    C->print_method(PHASE_AFTER_SPLIT_IF, 4, iff);
    return;
  }

  // Two identical ifs back to back can be merged
  if (try_merge_identical_ifs(n)) {
    return;
  }

  // Check for an IF ready to split; one that has its
  // condition codes input coming from a Phi at the block start.
  int n_op = n->Opcode();

  // Check for an IF being dominated by another IF same test
  if (n_op == Op_If ||
      n_op == Op_RangeCheck) {
    Node *bol = n->in(1);
    uint max = bol->outcnt();
    // Check for same test used more than once?
    if (bol->is_Bool() && (max > 1 || bol->in(1)->is_SubTypeCheck())) {
      // Search up IDOMs to see if this IF is dominated.
      Node* cmp = bol->in(1);
      Node *cutoff = cmp->is_SubTypeCheck() ? dom_lca(get_ctrl(cmp->in(1)), get_ctrl(cmp->in(2))) : get_ctrl(bol);

      // Now search up IDOMs till cutoff, looking for a dominating test
      Node *prevdom = n;
      Node *dom = idom(prevdom);
      while (dom != cutoff) {
        if (dom->req() > 1 && n->as_If()->same_condition(dom, &_igvn) && prevdom->in(0) == dom &&
            safe_for_if_replacement(dom)) {
          // It's invalid to move control dependent data nodes in the inner
          // strip-mined loop, because:
          //  1) break validation of LoopNode::verify_strip_mined()
          //  2) move code with side-effect in strip-mined loop
          // Move to the exit of outer strip-mined loop in that case.
          Node* out_le = is_inner_of_stripmined_loop(dom);
          if (out_le != nullptr) {
            prevdom = out_le;
          }
          // Replace the dominated test with an obvious true or false.
          // Place it on the IGVN worklist for later cleanup.
          C->set_major_progress();
          dominated_by(prevdom->as_IfProj(), n->as_If(), false, true);
          DEBUG_ONLY( if (VerifyLoopOptimizations) { verify(); } );
          return;
        }
        prevdom = dom;
        dom = idom(prevdom);
      }
    }
  }

  try_sink_out_of_loop(n);

  try_move_store_after_loop(n);
}

// Transform:
//
// if (some_condition) {
//   // body 1
// } else {
//   // body 2
// }
// if (some_condition) {
//   // body 3
// } else {
//   // body 4
// }
//
// into:
//
//
// if (some_condition) {
//   // body 1
//   // body 3
// } else {
//   // body 2
//   // body 4
// }
bool PhaseIdealLoop::try_merge_identical_ifs(Node* n) {
  if (identical_backtoback_ifs(n) && can_split_if(n->in(0))) {
    Node *n_ctrl = n->in(0);
    IfNode* dom_if = idom(n_ctrl)->as_If();
    if (n->in(1) != dom_if->in(1)) {
      assert(n->in(1)->in(1)->is_SubTypeCheck() &&
             (n->in(1)->in(1)->as_SubTypeCheck()->method() != nullptr ||
              dom_if->in(1)->in(1)->as_SubTypeCheck()->method() != nullptr), "only for subtype checks with profile data attached");
      _igvn.replace_input_of(n, 1, dom_if->in(1));
    }
    ProjNode* dom_proj_true = dom_if->proj_out(1);
    ProjNode* dom_proj_false = dom_if->proj_out(0);

    // Now split the IF
    RegionNode* new_false_region;
    RegionNode* new_true_region;
    do_split_if(n, &new_false_region, &new_true_region);
    assert(new_false_region->req() == new_true_region->req(), "");
#ifdef ASSERT
    for (uint i = 1; i < new_false_region->req(); ++i) {
      assert(new_false_region->in(i)->in(0) == new_true_region->in(i)->in(0), "unexpected shape following split if");
      assert(i == new_false_region->req() - 1 || new_false_region->in(i)->in(0)->in(1) == new_false_region->in(i + 1)->in(0)->in(1), "unexpected shape following split if");
    }
#endif
    assert(new_false_region->in(1)->in(0)->in(1) == dom_if->in(1), "dominating if and dominated if after split must share test");

    // We now have:
    // if (some_condition) {
    //   // body 1
    //   if (some_condition) {
    //     body3: // new_true_region
    //     // body3
    //   } else {
    //     goto body4;
    //   }
    // } else {
    //   // body 2
    //  if (some_condition) {
    //     goto body3;
    //   } else {
    //     body4:   // new_false_region
    //     // body4;
    //   }
    // }
    //

    // clone pinned nodes thru the resulting regions
    push_pinned_nodes_thru_region(dom_if, new_true_region);
    push_pinned_nodes_thru_region(dom_if, new_false_region);

    // Optimize out the cloned ifs. Because pinned nodes were cloned, this also allows a CastPP that would be dependent
    // on a projection of n to have the dom_if as a control dependency. We don't want the CastPP to end up with an
    // unrelated control dependency.
    for (uint i = 1; i < new_false_region->req(); i++) {
      if (is_dominator(dom_proj_true, new_false_region->in(i))) {
        dominated_by(dom_proj_true->as_IfProj(), new_false_region->in(i)->in(0)->as_If(), false, false);
      } else {
        assert(is_dominator(dom_proj_false, new_false_region->in(i)), "bad if");
        dominated_by(dom_proj_false->as_IfProj(), new_false_region->in(i)->in(0)->as_If(), false, false);
      }
    }
    return true;
  }
  return false;
}

void PhaseIdealLoop::push_pinned_nodes_thru_region(IfNode* dom_if, Node* region) {
  for (DUIterator i = region->outs(); region->has_out(i); i++) {
    Node* u = region->out(i);
    if (!has_ctrl(u) || u->is_Phi() || !u->depends_only_on_test() || !_igvn.no_dependent_zero_check(u)) {
      continue;
    }
    assert(u->in(0) == region, "not a control dependent node?");
    uint j = 1;
    for (; j < u->req(); ++j) {
      Node* in = u->in(j);
      if (!is_dominator(ctrl_or_self(in), dom_if)) {
        break;
      }
    }
    if (j == u->req()) {
      Node *phi = PhiNode::make_blank(region, u);
      for (uint k = 1; k < region->req(); ++k) {
        Node* clone = u->clone();
        clone->set_req(0, region->in(k));
        register_new_node(clone, region->in(k));
        phi->init_req(k, clone);
      }
      register_new_node(phi, region);
      _igvn.replace_node(u, phi);
      --i;
    }
  }
}

bool PhaseIdealLoop::safe_for_if_replacement(const Node* dom) const {
  if (!dom->is_CountedLoopEnd()) {
    return true;
  }
  CountedLoopEndNode* le = dom->as_CountedLoopEnd();
  CountedLoopNode* cl = le->loopnode();
  if (cl == nullptr) {
    return true;
  }
  if (!cl->is_main_loop()) {
    return true;
  }
  if (cl->is_canonical_loop_entry() == nullptr) {
    return true;
  }
  // Further unrolling is possible so loop exit condition might change
  return false;
}

// See if a shared loop-varying computation has no loop-varying uses.
// Happens if something is only used for JVM state in uncommon trap exits,
// like various versions of induction variable+offset.  Clone the
// computation per usage to allow it to sink out of the loop.
void PhaseIdealLoop::try_sink_out_of_loop(Node* n) {
  if (has_ctrl(n) &&
      !n->is_Phi() &&
      !n->is_Bool() &&
      !n->is_Proj() &&
      !n->is_MergeMem() &&
      !n->is_CMove() &&
      n->Opcode() != Op_Opaque4 &&
      !n->is_Type()) {
    Node *n_ctrl = get_ctrl(n);
    IdealLoopTree *n_loop = get_loop(n_ctrl);

    if (n->in(0) != nullptr) {
      IdealLoopTree* loop_ctrl = get_loop(n->in(0));
      if (n_loop != loop_ctrl && n_loop->is_member(loop_ctrl)) {
        // n has a control input inside a loop but get_ctrl() is member of an outer loop. This could happen, for example,
        // for Div nodes inside a loop (control input inside loop) without a use except for an UCT (outside the loop).
        // Rewire control of n to right outside of the loop, regardless if its input(s) are later sunk or not.
        _igvn.replace_input_of(n, 0, place_outside_loop(n_ctrl, loop_ctrl));
      }
    }
    if (n_loop != _ltree_root && n->outcnt() > 1) {
      // Compute early control: needed for anti-dependence analysis. It's also possible that as a result of
      // previous transformations in this loop opts round, the node can be hoisted now: early control will tell us.
      Node* early_ctrl = compute_early_ctrl(n, n_ctrl);
      if (n_loop->is_member(get_loop(early_ctrl)) && // check that this one can't be hoisted now
          ctrl_of_all_uses_out_of_loop(n, early_ctrl, n_loop)) { // All uses in outer loops!
        assert(!n->is_Store() && !n->is_LoadStore(), "no node with a side effect");
        Node* outer_loop_clone = nullptr;
        for (DUIterator_Last jmin, j = n->last_outs(jmin); j >= jmin;) {
          Node* u = n->last_out(j); // Clone private computation per use
          _igvn.rehash_node_delayed(u);
          Node* x = n->clone(); // Clone computation
          Node* x_ctrl = nullptr;
          if (u->is_Phi()) {
            // Replace all uses of normal nodes.  Replace Phi uses
            // individually, so the separate Nodes can sink down
            // different paths.
            uint k = 1;
            while (u->in(k) != n) k++;
            u->set_req(k, x);
            // x goes next to Phi input path
            x_ctrl = u->in(0)->in(k);
            // Find control for 'x' next to use but not inside inner loops.
            x_ctrl = place_outside_loop(x_ctrl, n_loop);
            --j;
          } else {              // Normal use
            if (has_ctrl(u)) {
              x_ctrl = get_ctrl(u);
            } else {
              x_ctrl = u->in(0);
            }
            // Find control for 'x' next to use but not inside inner loops.
            x_ctrl = place_outside_loop(x_ctrl, n_loop);
            // Replace all uses
            if (u->is_ConstraintCast() && _igvn.type(n)->higher_equal(u->bottom_type()) && u->in(0) == x_ctrl) {
              // If we're sinking a chain of data nodes, we might have inserted a cast to pin the use which is not necessary
              // anymore now that we're going to pin n as well
              _igvn.replace_node(u, x);
              --j;
            } else {
              int nb = u->replace_edge(n, x, &_igvn);
              j -= nb;
            }
          }

          if (n->is_Load()) {
            // For loads, add a control edge to a CFG node outside of the loop
            // to force them to not combine and return back inside the loop
            // during GVN optimization (4641526).
            assert(x_ctrl == get_late_ctrl_with_anti_dep(x->as_Load(), early_ctrl, x_ctrl), "anti-dependences were already checked");

            IdealLoopTree* x_loop = get_loop(x_ctrl);
            Node* x_head = x_loop->_head;
            if (x_head->is_Loop() && x_head->is_OuterStripMinedLoop()) {
              // Do not add duplicate LoadNodes to the outer strip mined loop
              if (outer_loop_clone != nullptr) {
                _igvn.replace_node(x, outer_loop_clone);
                continue;
              }
              outer_loop_clone = x;
            }
            x->set_req(0, x_ctrl);
          } else if (n->in(0) != nullptr){
            x->set_req(0, x_ctrl);
          }
          assert(dom_depth(n_ctrl) <= dom_depth(x_ctrl), "n is later than its clone");
          assert(!n_loop->is_member(get_loop(x_ctrl)), "should have moved out of loop");
          register_new_node(x, x_ctrl);

          // Chain of AddP nodes: (AddP base (AddP base (AddP base )))
          // All AddP nodes must keep the same base after sinking so:
          // 1- We don't add a CastPP here until the last one of the chain is sunk: if part of the chain is not sunk,
          // their bases remain the same.
          // (see 2- below)
          assert(!x->is_AddP() || !x->in(AddPNode::Address)->is_AddP() ||
                 x->in(AddPNode::Address)->in(AddPNode::Base) == x->in(AddPNode::Base) ||
                 !x->in(AddPNode::Address)->in(AddPNode::Base)->eqv_uncast(x->in(AddPNode::Base)), "unexpected AddP shape");
          if (x->in(0) == nullptr && !x->is_DecodeNarrowPtr() &&
              !(x->is_AddP() && x->in(AddPNode::Address)->is_AddP() && x->in(AddPNode::Address)->in(AddPNode::Base) == x->in(AddPNode::Base))) {
            assert(!x->is_Load(), "load should be pinned");
            // Use a cast node to pin clone out of loop
            Node* cast = nullptr;
            for (uint k = 0; k < x->req(); k++) {
              Node* in = x->in(k);
              if (in != nullptr && n_loop->is_member(get_loop(get_ctrl(in)))) {
                const Type* in_t = _igvn.type(in);
                cast = ConstraintCastNode::make_cast_for_type(x_ctrl, in, in_t,
                                                              ConstraintCastNode::UnconditionalDependency, nullptr);
              }
              if (cast != nullptr) {
                Node* prev = _igvn.hash_find_insert(cast);
                if (prev != nullptr && get_ctrl(prev) == x_ctrl) {
                  cast->destruct(&_igvn);
                  cast = prev;
                } else {
                  register_new_node(cast, x_ctrl);
                }
                x->replace_edge(in, cast);
                // Chain of AddP nodes:
                // 2- A CastPP of the base is only added now that all AddP nodes are sunk
                if (x->is_AddP() && k == AddPNode::Base) {
                  update_addp_chain_base(x, n->in(AddPNode::Base), cast);
                }
                break;
              }
            }
            assert(cast != nullptr, "must have added a cast to pin the node");
          }
        }
        _igvn.remove_dead_node(n);
      }
      _dom_lca_tags_round = 0;
    }
  }
}

void PhaseIdealLoop::update_addp_chain_base(Node* x, Node* old_base, Node* new_base) {
  ResourceMark rm;
  Node_List wq;
  wq.push(x);
  while (wq.size() != 0) {
    Node* n = wq.pop();
    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* u = n->fast_out(i);
      if (u->is_AddP() && u->in(AddPNode::Base) == old_base) {
        _igvn.replace_input_of(u, AddPNode::Base, new_base);
        wq.push(u);
      }
    }
  }
}

// Compute the early control of a node by following its inputs until we reach
// nodes that are pinned. Then compute the LCA of the control of all pinned nodes.
Node* PhaseIdealLoop::compute_early_ctrl(Node* n, Node* n_ctrl) {
  Node* early_ctrl = nullptr;
  ResourceMark rm;
  Unique_Node_List wq;
  wq.push(n);
  for (uint i = 0; i < wq.size(); i++) {
    Node* m = wq.at(i);
    Node* c = nullptr;
    if (m->is_CFG()) {
      c = m;
    } else if (m->pinned()) {
      c = m->in(0);
    } else {
      for (uint j = 0; j < m->req(); j++) {
        Node* in = m->in(j);
        if (in != nullptr) {
          wq.push(in);
        }
      }
    }
    if (c != nullptr) {
      assert(is_dominator(c, n_ctrl), "control input must dominate current control");
      if (early_ctrl == nullptr || is_dominator(early_ctrl, c)) {
        early_ctrl = c;
      }
    }
  }
  assert(is_dominator(early_ctrl, n_ctrl), "early control must dominate current control");
  return early_ctrl;
}

bool PhaseIdealLoop::ctrl_of_all_uses_out_of_loop(const Node* n, Node* n_ctrl, IdealLoopTree* n_loop) {
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node* u = n->fast_out(i);
    if (u->is_Opaque1()) {
      return false;  // Found loop limit, bugfix for 4677003
    }
    // We can't reuse tags in PhaseIdealLoop::dom_lca_for_get_late_ctrl_internal() so make sure calls to
    // get_late_ctrl_with_anti_dep() use their own tag
    _dom_lca_tags_round++;
    assert(_dom_lca_tags_round != 0, "shouldn't wrap around");

    if (u->is_Phi()) {
      for (uint j = 1; j < u->req(); ++j) {
        if (u->in(j) == n && !ctrl_of_use_out_of_loop(n, n_ctrl, n_loop, u->in(0)->in(j))) {
          return false;
        }
      }
    } else {
      Node* ctrl = has_ctrl(u) ? get_ctrl(u) : u->in(0);
      if (!ctrl_of_use_out_of_loop(n, n_ctrl, n_loop, ctrl)) {
        return false;
      }
    }
  }
  return true;
}

bool PhaseIdealLoop::ctrl_of_use_out_of_loop(const Node* n, Node* n_ctrl, IdealLoopTree* n_loop, Node* ctrl) {
  if (n->is_Load()) {
    ctrl = get_late_ctrl_with_anti_dep(n->as_Load(), n_ctrl, ctrl);
  }
  IdealLoopTree *u_loop = get_loop(ctrl);
  if (u_loop == n_loop) {
    return false; // Found loop-varying use
  }
  if (n_loop->is_member(u_loop)) {
    return false; // Found use in inner loop
  }
  // Sinking a node from a pre loop to its main loop pins the node between the pre and main loops. If that node is input
  // to a check that's eliminated by range check elimination, it becomes input to an expression that feeds into the exit
  // test of the pre loop above the point in the graph where it's pinned.
  if (n_loop->_head->is_CountedLoop() && n_loop->_head->as_CountedLoop()->is_pre_loop() &&
      u_loop->_head->is_CountedLoop() && u_loop->_head->as_CountedLoop()->is_main_loop() &&
      n_loop->_next == get_loop(u_loop->_head->as_CountedLoop()->skip_strip_mined())) {
    return false;
  }
  return true;
}

//------------------------------split_if_with_blocks---------------------------
// Check for aggressive application of 'split-if' optimization,
// using basic block level info.
void PhaseIdealLoop::split_if_with_blocks(VectorSet &visited, Node_Stack &nstack) {
  Node* root = C->root();
  visited.set(root->_idx); // first, mark root as visited
  // Do pre-visit work for root
  Node* n   = split_if_with_blocks_pre(root);
  uint  cnt = n->outcnt();
  uint  i   = 0;

  while (true) {
    // Visit all children
    if (i < cnt) {
      Node* use = n->raw_out(i);
      ++i;
      if (use->outcnt() != 0 && !visited.test_set(use->_idx)) {
        // Now do pre-visit work for this use
        use = split_if_with_blocks_pre(use);
        nstack.push(n, i); // Save parent and next use's index.
        n   = use;         // Process all children of current use.
        cnt = use->outcnt();
        i   = 0;
      }
    }
    else {
      // All of n's children have been processed, complete post-processing.
      if (cnt != 0 && !n->is_Con()) {
        assert(has_node(n), "no dead nodes");
        split_if_with_blocks_post(n);
      }
      if (must_throttle_split_if()) {
        nstack.clear();
      }
      if (nstack.is_empty()) {
        // Finished all nodes on stack.
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


//=============================================================================
//
//                   C L O N E   A   L O O P   B O D Y
//

//------------------------------clone_iff--------------------------------------
// Passed in a Phi merging (recursively) some nearly equivalent Bool/Cmps.
// "Nearly" because all Nodes have been cloned from the original in the loop,
// but the fall-in edges to the Cmp are different.  Clone bool/Cmp pairs
// through the Phi recursively, and return a Bool.
Node* PhaseIdealLoop::clone_iff(PhiNode* phi) {

  // Convert this Phi into a Phi merging Bools
  uint i;
  for (i = 1; i < phi->req(); i++) {
    Node *b = phi->in(i);
    if (b->is_Phi()) {
      _igvn.replace_input_of(phi, i, clone_iff(b->as_Phi()));
    } else {
      assert(b->is_Bool() || b->Opcode() == Op_Opaque4, "");
    }
  }

  Node* n = phi->in(1);
  Node* sample_opaque = nullptr;
  Node *sample_bool = nullptr;
  if (n->Opcode() == Op_Opaque4) {
    sample_opaque = n;
    sample_bool = n->in(1);
    assert(sample_bool->is_Bool(), "wrong type");
  } else {
    sample_bool = n;
  }
  Node *sample_cmp = sample_bool->in(1);

  // Make Phis to merge the Cmp's inputs.
  PhiNode *phi1 = new PhiNode(phi->in(0), Type::TOP);
  PhiNode *phi2 = new PhiNode(phi->in(0), Type::TOP);
  for (i = 1; i < phi->req(); i++) {
    Node *n1 = sample_opaque == nullptr ? phi->in(i)->in(1)->in(1) : phi->in(i)->in(1)->in(1)->in(1);
    Node *n2 = sample_opaque == nullptr ? phi->in(i)->in(1)->in(2) : phi->in(i)->in(1)->in(1)->in(2);
    phi1->set_req(i, n1);
    phi2->set_req(i, n2);
    phi1->set_type(phi1->type()->meet_speculative(n1->bottom_type()));
    phi2->set_type(phi2->type()->meet_speculative(n2->bottom_type()));
  }
  // See if these Phis have been made before.
  // Register with optimizer
  Node *hit1 = _igvn.hash_find_insert(phi1);
  if (hit1) {                   // Hit, toss just made Phi
    _igvn.remove_dead_node(phi1); // Remove new phi
    assert(hit1->is_Phi(), "" );
    phi1 = (PhiNode*)hit1;      // Use existing phi
  } else {                      // Miss
    _igvn.register_new_node_with_optimizer(phi1);
  }
  Node *hit2 = _igvn.hash_find_insert(phi2);
  if (hit2) {                   // Hit, toss just made Phi
    _igvn.remove_dead_node(phi2); // Remove new phi
    assert(hit2->is_Phi(), "" );
    phi2 = (PhiNode*)hit2;      // Use existing phi
  } else {                      // Miss
    _igvn.register_new_node_with_optimizer(phi2);
  }
  // Register Phis with loop/block info
  set_ctrl(phi1, phi->in(0));
  set_ctrl(phi2, phi->in(0));
  // Make a new Cmp
  Node *cmp = sample_cmp->clone();
  cmp->set_req(1, phi1);
  cmp->set_req(2, phi2);
  _igvn.register_new_node_with_optimizer(cmp);
  set_ctrl(cmp, phi->in(0));

  // Make a new Bool
  Node *b = sample_bool->clone();
  b->set_req(1,cmp);
  _igvn.register_new_node_with_optimizer(b);
  set_ctrl(b, phi->in(0));

  if (sample_opaque != nullptr) {
    Node* opaque = sample_opaque->clone();
    opaque->set_req(1, b);
    _igvn.register_new_node_with_optimizer(opaque);
    set_ctrl(opaque, phi->in(0));
    return opaque;
  }

  assert(b->is_Bool(), "");
  return b;
}

//------------------------------clone_bool-------------------------------------
// Passed in a Phi merging (recursively) some nearly equivalent Bool/Cmps.
// "Nearly" because all Nodes have been cloned from the original in the loop,
// but the fall-in edges to the Cmp are different.  Clone bool/Cmp pairs
// through the Phi recursively, and return a Bool.
CmpNode*PhaseIdealLoop::clone_bool(PhiNode* phi) {
  uint i;
  // Convert this Phi into a Phi merging Bools
  for( i = 1; i < phi->req(); i++ ) {
    Node *b = phi->in(i);
    if( b->is_Phi() ) {
      _igvn.replace_input_of(phi, i, clone_bool(b->as_Phi()));
    } else {
      assert( b->is_Cmp() || b->is_top(), "inputs are all Cmp or TOP" );
    }
  }

  Node *sample_cmp = phi->in(1);

  // Make Phis to merge the Cmp's inputs.
  PhiNode *phi1 = new PhiNode( phi->in(0), Type::TOP );
  PhiNode *phi2 = new PhiNode( phi->in(0), Type::TOP );
  for( uint j = 1; j < phi->req(); j++ ) {
    Node *cmp_top = phi->in(j); // Inputs are all Cmp or TOP
    Node *n1, *n2;
    if( cmp_top->is_Cmp() ) {
      n1 = cmp_top->in(1);
      n2 = cmp_top->in(2);
    } else {
      n1 = n2 = cmp_top;
    }
    phi1->set_req( j, n1 );
    phi2->set_req( j, n2 );
    phi1->set_type(phi1->type()->meet_speculative(n1->bottom_type()));
    phi2->set_type(phi2->type()->meet_speculative(n2->bottom_type()));
  }

  // See if these Phis have been made before.
  // Register with optimizer
  Node *hit1 = _igvn.hash_find_insert(phi1);
  if( hit1 ) {                  // Hit, toss just made Phi
    _igvn.remove_dead_node(phi1); // Remove new phi
    assert( hit1->is_Phi(), "" );
    phi1 = (PhiNode*)hit1;      // Use existing phi
  } else {                      // Miss
    _igvn.register_new_node_with_optimizer(phi1);
  }
  Node *hit2 = _igvn.hash_find_insert(phi2);
  if( hit2 ) {                  // Hit, toss just made Phi
    _igvn.remove_dead_node(phi2); // Remove new phi
    assert( hit2->is_Phi(), "" );
    phi2 = (PhiNode*)hit2;      // Use existing phi
  } else {                      // Miss
    _igvn.register_new_node_with_optimizer(phi2);
  }
  // Register Phis with loop/block info
  set_ctrl(phi1, phi->in(0));
  set_ctrl(phi2, phi->in(0));
  // Make a new Cmp
  Node *cmp = sample_cmp->clone();
  cmp->set_req( 1, phi1 );
  cmp->set_req( 2, phi2 );
  _igvn.register_new_node_with_optimizer(cmp);
  set_ctrl(cmp, phi->in(0));

  assert( cmp->is_Cmp(), "" );
  return (CmpNode*)cmp;
}

void PhaseIdealLoop::clone_loop_handle_data_uses(Node* old, Node_List &old_new,
                                                 IdealLoopTree* loop, IdealLoopTree* outer_loop,
                                                 Node_List*& split_if_set, Node_List*& split_bool_set,
                                                 Node_List*& split_cex_set, Node_List& worklist,
                                                 uint new_counter, CloneLoopMode mode) {
  Node* nnn = old_new[old->_idx];
  // Copy uses to a worklist, so I can munge the def-use info
  // with impunity.
  for (DUIterator_Fast jmax, j = old->fast_outs(jmax); j < jmax; j++)
    worklist.push(old->fast_out(j));

  while( worklist.size() ) {
    Node *use = worklist.pop();
    if (!has_node(use))  continue; // Ignore dead nodes
    if (use->in(0) == C->top())  continue;
    IdealLoopTree *use_loop = get_loop( has_ctrl(use) ? get_ctrl(use) : use );
    // Check for data-use outside of loop - at least one of OLD or USE
    // must not be a CFG node.
#ifdef ASSERT
    if (loop->_head->as_Loop()->is_strip_mined() && outer_loop->is_member(use_loop) && !loop->is_member(use_loop) && old_new[use->_idx] == nullptr) {
      Node* sfpt = loop->_head->as_CountedLoop()->outer_safepoint();
      assert(mode != IgnoreStripMined, "incorrect cloning mode");
      assert((mode == ControlAroundStripMined && use == sfpt) || !use->is_reachable_from_root(), "missed a node");
    }
#endif
    if (!loop->is_member(use_loop) && !outer_loop->is_member(use_loop) && (!old->is_CFG() || !use->is_CFG())) {

      // If the Data use is an IF, that means we have an IF outside of the
      // loop that is switching on a condition that is set inside of the
      // loop.  Happens if people set a loop-exit flag; then test the flag
      // in the loop to break the loop, then test is again outside of the
      // loop to determine which way the loop exited.
      // Loop predicate If node connects to Bool node through Opaque1 node.
      //
      // If the use is an AllocateArray through its ValidLengthTest input,
      // make sure the Bool/Cmp input is cloned down to avoid a Phi between
      // the AllocateArray node and its ValidLengthTest input that could cause
      // split if to break.
      if (use->is_If() || use->is_CMove() || use->Opcode() == Op_Opaque4 ||
          (use->Opcode() == Op_AllocateArray && use->in(AllocateNode::ValidLengthTest) == old)) {
        // Since this code is highly unlikely, we lazily build the worklist
        // of such Nodes to go split.
        if (!split_if_set) {
          split_if_set = new Node_List();
        }
        split_if_set->push(use);
      }
      if (use->is_Bool()) {
        if (!split_bool_set) {
          split_bool_set = new Node_List();
        }
        split_bool_set->push(use);
      }
      if (use->Opcode() == Op_CreateEx) {
        if (!split_cex_set) {
          split_cex_set = new Node_List();
        }
        split_cex_set->push(use);
      }


      // Get "block" use is in
      uint idx = 0;
      while( use->in(idx) != old ) idx++;
      Node *prev = use->is_CFG() ? use : get_ctrl(use);
      assert(!loop->is_member(get_loop(prev)) && !outer_loop->is_member(get_loop(prev)), "" );
      Node* cfg = (prev->_idx >= new_counter && prev->is_Region())
        ? prev->in(2)
        : idom(prev);
      if( use->is_Phi() )     // Phi use is in prior block
        cfg = prev->in(idx);  // NOT in block of Phi itself
      if (cfg->is_top()) {    // Use is dead?
        _igvn.replace_input_of(use, idx, C->top());
        continue;
      }

      // If use is referenced through control edge... (idx == 0)
      if (mode == IgnoreStripMined && idx == 0) {
        LoopNode *head = loop->_head->as_Loop();
        if (head->is_strip_mined() && is_dominator(head->outer_loop_exit(), prev)) {
          // That node is outside the inner loop, leave it outside the
          // outer loop as well to not confuse verification code.
          assert(!loop->_parent->is_member(use_loop), "should be out of the outer loop");
          _igvn.replace_input_of(use, 0, head->outer_loop_exit());
          continue;
        }
      }

      while(!outer_loop->is_member(get_loop(cfg))) {
        prev = cfg;
        cfg = (cfg->_idx >= new_counter && cfg->is_Region()) ? cfg->in(2) : idom(cfg);
      }
      // If the use occurs after merging several exits from the loop, then
      // old value must have dominated all those exits.  Since the same old
      // value was used on all those exits we did not need a Phi at this
      // merge point.  NOW we do need a Phi here.  Each loop exit value
      // is now merged with the peeled body exit; each exit gets its own
      // private Phi and those Phis need to be merged here.
      Node *phi;
      if( prev->is_Region() ) {
        if( idx == 0 ) {      // Updating control edge?
          phi = prev;         // Just use existing control
        } else {              // Else need a new Phi
          phi = PhiNode::make( prev, old );
          // Now recursively fix up the new uses of old!
          for( uint i = 1; i < prev->req(); i++ ) {
            worklist.push(phi); // Onto worklist once for each 'old' input
          }
        }
      } else {
        // Get new RegionNode merging old and new loop exits
        prev = old_new[prev->_idx];
        assert( prev, "just made this in step 7" );
        if( idx == 0) {      // Updating control edge?
          phi = prev;         // Just use existing control
        } else {              // Else need a new Phi
          // Make a new Phi merging data values properly
          phi = PhiNode::make( prev, old );
          phi->set_req( 1, nnn );
        }
      }
      // If inserting a new Phi, check for prior hits
      if( idx != 0 ) {
        Node *hit = _igvn.hash_find_insert(phi);
        if( hit == nullptr ) {
          _igvn.register_new_node_with_optimizer(phi); // Register new phi
        } else {                                      // or
          // Remove the new phi from the graph and use the hit
          _igvn.remove_dead_node(phi);
          phi = hit;                                  // Use existing phi
        }
        set_ctrl(phi, prev);
      }
      // Make 'use' use the Phi instead of the old loop body exit value
      assert(use->in(idx) == old, "old is still input of use");
      // We notify all uses of old, including use, and the indirect uses,
      // that may now be optimized because we have replaced old with phi.
      _igvn.add_users_to_worklist(old);
      _igvn.replace_input_of(use, idx, phi);
      if( use->_idx >= new_counter ) { // If updating new phis
        // Not needed for correctness, but prevents a weak assert
        // in AddPNode from tripping (when we end up with different
        // base & derived Phis that will become the same after
        // IGVN does CSE).
        Node *hit = _igvn.hash_find_insert(use);
        if( hit )             // Go ahead and re-hash for hits.
          _igvn.replace_node( use, hit );
      }
    }
  }
}

static void collect_nodes_in_outer_loop_not_reachable_from_sfpt(Node* n, const IdealLoopTree *loop, const IdealLoopTree* outer_loop,
                                                                const Node_List &old_new, Unique_Node_List& wq, PhaseIdealLoop* phase,
                                                                bool check_old_new) {
  for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
    Node* u = n->fast_out(j);
    assert(check_old_new || old_new[u->_idx] == nullptr, "shouldn't have been cloned");
    if (!u->is_CFG() && (!check_old_new || old_new[u->_idx] == nullptr)) {
      Node* c = phase->get_ctrl(u);
      IdealLoopTree* u_loop = phase->get_loop(c);
      assert(!loop->is_member(u_loop) || !loop->_body.contains(u), "can be in outer loop or out of both loops only");
      if (!loop->is_member(u_loop)) {
        if (outer_loop->is_member(u_loop)) {
          wq.push(u);
        } else {
          // nodes pinned with control in the outer loop but not referenced from the safepoint must be moved out of
          // the outer loop too
          Node* u_c = u->in(0);
          if (u_c != nullptr) {
            IdealLoopTree* u_c_loop = phase->get_loop(u_c);
            if (outer_loop->is_member(u_c_loop) && !loop->is_member(u_c_loop)) {
              wq.push(u);
            }
          }
        }
      }
    }
  }
}

void PhaseIdealLoop::clone_outer_loop(LoopNode* head, CloneLoopMode mode, IdealLoopTree *loop,
                                      IdealLoopTree* outer_loop, int dd, Node_List &old_new,
                                      Node_List& extra_data_nodes) {
  if (head->is_strip_mined() && mode != IgnoreStripMined) {
    CountedLoopNode* cl = head->as_CountedLoop();
    Node* l = cl->outer_loop();
    Node* tail = cl->outer_loop_tail();
    IfNode* le = cl->outer_loop_end();
    Node* sfpt = cl->outer_safepoint();
    CountedLoopEndNode* cle = cl->loopexit();
    CountedLoopNode* new_cl = old_new[cl->_idx]->as_CountedLoop();
    CountedLoopEndNode* new_cle = new_cl->as_CountedLoop()->loopexit_or_null();
    Node* cle_out = cle->proj_out(false);

    Node* new_sfpt = nullptr;
    Node* new_cle_out = cle_out->clone();
    old_new.map(cle_out->_idx, new_cle_out);
    if (mode == CloneIncludesStripMined) {
      // clone outer loop body
      Node* new_l = l->clone();
      Node* new_tail = tail->clone();
      IfNode* new_le = le->clone()->as_If();
      new_sfpt = sfpt->clone();

      set_loop(new_l, outer_loop->_parent);
      set_idom(new_l, new_l->in(LoopNode::EntryControl), dd);
      set_loop(new_cle_out, outer_loop->_parent);
      set_idom(new_cle_out, new_cle, dd);
      set_loop(new_sfpt, outer_loop->_parent);
      set_idom(new_sfpt, new_cle_out, dd);
      set_loop(new_le, outer_loop->_parent);
      set_idom(new_le, new_sfpt, dd);
      set_loop(new_tail, outer_loop->_parent);
      set_idom(new_tail, new_le, dd);
      set_idom(new_cl, new_l, dd);

      old_new.map(l->_idx, new_l);
      old_new.map(tail->_idx, new_tail);
      old_new.map(le->_idx, new_le);
      old_new.map(sfpt->_idx, new_sfpt);

      new_l->set_req(LoopNode::LoopBackControl, new_tail);
      new_l->set_req(0, new_l);
      new_tail->set_req(0, new_le);
      new_le->set_req(0, new_sfpt);
      new_sfpt->set_req(0, new_cle_out);
      new_cle_out->set_req(0, new_cle);
      new_cl->set_req(LoopNode::EntryControl, new_l);

      _igvn.register_new_node_with_optimizer(new_l);
      _igvn.register_new_node_with_optimizer(new_tail);
      _igvn.register_new_node_with_optimizer(new_le);
    } else {
      Node *newhead = old_new[loop->_head->_idx];
      newhead->as_Loop()->clear_strip_mined();
      _igvn.replace_input_of(newhead, LoopNode::EntryControl, newhead->in(LoopNode::EntryControl)->in(LoopNode::EntryControl));
      set_idom(newhead, newhead->in(LoopNode::EntryControl), dd);
    }
    // Look at data node that were assigned a control in the outer
    // loop: they are kept in the outer loop by the safepoint so start
    // from the safepoint node's inputs.
    IdealLoopTree* outer_loop = get_loop(l);
    Node_Stack stack(2);
    stack.push(sfpt, 1);
    uint new_counter = C->unique();
    while (stack.size() > 0) {
      Node* n = stack.node();
      uint i = stack.index();
      while (i < n->req() &&
             (n->in(i) == nullptr ||
              !has_ctrl(n->in(i)) ||
              get_loop(get_ctrl(n->in(i))) != outer_loop ||
              (old_new[n->in(i)->_idx] != nullptr && old_new[n->in(i)->_idx]->_idx >= new_counter))) {
        i++;
      }
      if (i < n->req()) {
        stack.set_index(i+1);
        stack.push(n->in(i), 0);
      } else {
        assert(old_new[n->_idx] == nullptr || n == sfpt || old_new[n->_idx]->_idx < new_counter, "no clone yet");
        Node* m = n == sfpt ? new_sfpt : n->clone();
        if (m != nullptr) {
          for (uint i = 0; i < n->req(); i++) {
            if (m->in(i) != nullptr && old_new[m->in(i)->_idx] != nullptr) {
              m->set_req(i, old_new[m->in(i)->_idx]);
            }
          }
        } else {
          assert(n == sfpt && mode != CloneIncludesStripMined, "where's the safepoint clone?");
        }
        if (n != sfpt) {
          extra_data_nodes.push(n);
          _igvn.register_new_node_with_optimizer(m);
          assert(get_ctrl(n) == cle_out, "what other control?");
          set_ctrl(m, new_cle_out);
          old_new.map(n->_idx, m);
        }
        stack.pop();
      }
    }
    if (mode == CloneIncludesStripMined) {
      _igvn.register_new_node_with_optimizer(new_sfpt);
      _igvn.register_new_node_with_optimizer(new_cle_out);
    }
    // Some other transformation may have pessimistically assigned some
    // data nodes to the outer loop. Set their control so they are out
    // of the outer loop.
    ResourceMark rm;
    Unique_Node_List wq;
    for (uint i = 0; i < extra_data_nodes.size(); i++) {
      Node* old = extra_data_nodes.at(i);
      collect_nodes_in_outer_loop_not_reachable_from_sfpt(old, loop, outer_loop, old_new, wq, this, true);
    }

    for (uint i = 0; i < loop->_body.size(); i++) {
      Node* old = loop->_body.at(i);
      collect_nodes_in_outer_loop_not_reachable_from_sfpt(old, loop, outer_loop, old_new, wq, this, true);
    }

    Node* inner_out = sfpt->in(0);
    if (inner_out->outcnt() > 1) {
      collect_nodes_in_outer_loop_not_reachable_from_sfpt(inner_out, loop, outer_loop, old_new, wq, this, true);
    }

    Node* new_ctrl = cl->outer_loop_exit();
    assert(get_loop(new_ctrl) != outer_loop, "must be out of the loop nest");
    for (uint i = 0; i < wq.size(); i++) {
      Node* n = wq.at(i);
      set_ctrl(n, new_ctrl);
      if (n->in(0) != nullptr) {
        _igvn.replace_input_of(n, 0, new_ctrl);
      }
      collect_nodes_in_outer_loop_not_reachable_from_sfpt(n, loop, outer_loop, old_new, wq, this, false);
    }
  } else {
    Node *newhead = old_new[loop->_head->_idx];
    set_idom(newhead, newhead->in(LoopNode::EntryControl), dd);
  }
}

//------------------------------clone_loop-------------------------------------
//
//                   C L O N E   A   L O O P   B O D Y
//
// This is the basic building block of the loop optimizations.  It clones an
// entire loop body.  It makes an old_new loop body mapping; with this mapping
// you can find the new-loop equivalent to an old-loop node.  All new-loop
// nodes are exactly equal to their old-loop counterparts, all edges are the
// same.  All exits from the old-loop now have a RegionNode that merges the
// equivalent new-loop path.  This is true even for the normal "loop-exit"
// condition.  All uses of loop-invariant old-loop values now come from (one
// or more) Phis that merge their new-loop equivalents.
//
// This operation leaves the graph in an illegal state: there are two valid
// control edges coming from the loop pre-header to both loop bodies.  I'll
// definitely have to hack the graph after running this transform.
//
// From this building block I will further edit edges to perform loop peeling
// or loop unrolling or iteration splitting (Range-Check-Elimination), etc.
//
// Parameter side_by_size_idom:
//   When side_by_size_idom is null, the dominator tree is constructed for
//      the clone loop to dominate the original.  Used in construction of
//      pre-main-post loop sequence.
//   When nonnull, the clone and original are side-by-side, both are
//      dominated by the side_by_side_idom node.  Used in construction of
//      unswitched loops.
void PhaseIdealLoop::clone_loop( IdealLoopTree *loop, Node_List &old_new, int dd,
                                CloneLoopMode mode, Node* side_by_side_idom) {

  LoopNode* head = loop->_head->as_Loop();
  head->verify_strip_mined(1);

  if (C->do_vector_loop() && PrintOpto) {
    const char* mname = C->method()->name()->as_quoted_ascii();
    if (mname != nullptr) {
      tty->print("PhaseIdealLoop::clone_loop: for vectorize method %s\n", mname);
    }
  }

  CloneMap& cm = C->clone_map();
  if (C->do_vector_loop()) {
    cm.set_clone_idx(cm.max_gen()+1);
#ifndef PRODUCT
    if (PrintOpto) {
      tty->print_cr("PhaseIdealLoop::clone_loop: _clone_idx %d", cm.clone_idx());
      loop->dump_head();
    }
#endif
  }

  // Step 1: Clone the loop body.  Make the old->new mapping.
  clone_loop_body(loop->_body, old_new, &cm);

  IdealLoopTree* outer_loop = (head->is_strip_mined() && mode != IgnoreStripMined) ? get_loop(head->as_CountedLoop()->outer_loop()) : loop;

  // Step 2: Fix the edges in the new body.  If the old input is outside the
  // loop use it.  If the old input is INside the loop, use the corresponding
  // new node instead.
  fix_body_edges(loop->_body, loop, old_new, dd, outer_loop->_parent, false);

  Node_List extra_data_nodes; // data nodes in the outer strip mined loop
  clone_outer_loop(head, mode, loop, outer_loop, dd, old_new, extra_data_nodes);

  // Step 3: Now fix control uses.  Loop varying control uses have already
  // been fixed up (as part of all input edges in Step 2).  Loop invariant
  // control uses must be either an IfFalse or an IfTrue.  Make a merge
  // point to merge the old and new IfFalse/IfTrue nodes; make the use
  // refer to this.
  Node_List worklist;
  uint new_counter = C->unique();
  fix_ctrl_uses(loop->_body, loop, old_new, mode, side_by_side_idom, &cm, worklist);

  // Step 4: If loop-invariant use is not control, it must be dominated by a
  // loop exit IfFalse/IfTrue.  Find "proper" loop exit.  Make a Region
  // there if needed.  Make a Phi there merging old and new used values.
  Node_List *split_if_set = nullptr;
  Node_List *split_bool_set = nullptr;
  Node_List *split_cex_set = nullptr;
  fix_data_uses(loop->_body, loop, mode, outer_loop, new_counter, old_new, worklist, split_if_set, split_bool_set, split_cex_set);

  for (uint i = 0; i < extra_data_nodes.size(); i++) {
    Node* old = extra_data_nodes.at(i);
    clone_loop_handle_data_uses(old, old_new, loop, outer_loop, split_if_set,
                                split_bool_set, split_cex_set, worklist, new_counter,
                                mode);
  }

  // Check for IFs that need splitting/cloning.  Happens if an IF outside of
  // the loop uses a condition set in the loop.  The original IF probably
  // takes control from one or more OLD Regions (which in turn get from NEW
  // Regions).  In any case, there will be a set of Phis for each merge point
  // from the IF up to where the original BOOL def exists the loop.
  finish_clone_loop(split_if_set, split_bool_set, split_cex_set);

}

void PhaseIdealLoop::finish_clone_loop(Node_List* split_if_set, Node_List* split_bool_set, Node_List* split_cex_set) {
  if (split_if_set) {
    while (split_if_set->size()) {
      Node *iff = split_if_set->pop();
      uint input = iff->Opcode() == Op_AllocateArray ? AllocateNode::ValidLengthTest : 1;
      if (iff->in(input)->is_Phi()) {
        Node *b = clone_iff(iff->in(input)->as_Phi());
        _igvn.replace_input_of(iff, input, b);
      }
    }
  }
  if (split_bool_set) {
    while (split_bool_set->size()) {
      Node *b = split_bool_set->pop();
      Node *phi = b->in(1);
      assert(phi->is_Phi(), "");
      CmpNode *cmp = clone_bool((PhiNode*) phi);
      _igvn.replace_input_of(b, 1, cmp);
    }
  }
  if (split_cex_set) {
    while (split_cex_set->size()) {
      Node *b = split_cex_set->pop();
      assert(b->in(0)->is_Region(), "");
      assert(b->in(1)->is_Phi(), "");
      assert(b->in(0)->in(0) == b->in(1)->in(0), "");
      split_up(b, b->in(0), nullptr);
    }
  }
}

void PhaseIdealLoop::fix_data_uses(Node_List& body, IdealLoopTree* loop, CloneLoopMode mode, IdealLoopTree* outer_loop,
                                   uint new_counter, Node_List &old_new, Node_List &worklist, Node_List*& split_if_set,
                                   Node_List*& split_bool_set, Node_List*& split_cex_set) {
  for(uint i = 0; i < body.size(); i++ ) {
    Node* old = body.at(i);
    clone_loop_handle_data_uses(old, old_new, loop, outer_loop, split_if_set,
                                split_bool_set, split_cex_set, worklist, new_counter,
                                mode);
  }
}

void PhaseIdealLoop::fix_ctrl_uses(const Node_List& body, const IdealLoopTree* loop, Node_List &old_new, CloneLoopMode mode,
                                   Node* side_by_side_idom, CloneMap* cm, Node_List &worklist) {
  LoopNode* head = loop->_head->as_Loop();
  for(uint i = 0; i < body.size(); i++ ) {
    Node* old = body.at(i);
    if( !old->is_CFG() ) continue;

    // Copy uses to a worklist, so I can munge the def-use info
    // with impunity.
    for (DUIterator_Fast jmax, j = old->fast_outs(jmax); j < jmax; j++) {
      worklist.push(old->fast_out(j));
    }

    while (worklist.size()) {  // Visit all uses
      Node *use = worklist.pop();
      if (!has_node(use))  continue; // Ignore dead nodes
      IdealLoopTree *use_loop = get_loop(has_ctrl(use) ? get_ctrl(use) : use );
      if (!loop->is_member(use_loop) && use->is_CFG()) {
        // Both OLD and USE are CFG nodes here.
        assert(use->is_Proj(), "" );
        Node* nnn = old_new[old->_idx];

        Node* newuse = nullptr;
        if (head->is_strip_mined() && mode != IgnoreStripMined) {
          CountedLoopNode* cl = head->as_CountedLoop();
          CountedLoopEndNode* cle = cl->loopexit();
          Node* cle_out = cle->proj_out_or_null(false);
          if (use == cle_out) {
            IfNode* le = cl->outer_loop_end();
            use = le->proj_out(false);
            use_loop = get_loop(use);
            if (mode == CloneIncludesStripMined) {
              nnn = old_new[le->_idx];
            } else {
              newuse = old_new[cle_out->_idx];
            }
          }
        }
        if (newuse == nullptr) {
          newuse = use->clone();
        }

        // Clone the loop exit control projection
        if (C->do_vector_loop() && cm != nullptr) {
          cm->verify_insert_and_clone(use, newuse, cm->clone_idx());
        }
        newuse->set_req(0,nnn);
        _igvn.register_new_node_with_optimizer(newuse);
        set_loop(newuse, use_loop);
        set_idom(newuse, nnn, dom_depth(nnn) + 1 );

        // We need a Region to merge the exit from the peeled body and the
        // exit from the old loop body.
        RegionNode *r = new RegionNode(3);
        uint dd_r = MIN2(dom_depth(newuse), dom_depth(use));
        assert(dd_r >= dom_depth(dom_lca(newuse, use)), "" );

        // The original user of 'use' uses 'r' instead.
        for (DUIterator_Last lmin, l = use->last_outs(lmin); l >= lmin;) {
          Node* useuse = use->last_out(l);
          _igvn.rehash_node_delayed(useuse);
          uint uses_found = 0;
          if (useuse->in(0) == use) {
            useuse->set_req(0, r);
            uses_found++;
            if (useuse->is_CFG()) {
              // This is not a dom_depth > dd_r because when new
              // control flow is constructed by a loop opt, a node and
              // its dominator can end up at the same dom_depth
              assert(dom_depth(useuse) >= dd_r, "");
              set_idom(useuse, r, dom_depth(useuse));
            }
          }
          for (uint k = 1; k < useuse->req(); k++) {
            if( useuse->in(k) == use ) {
              useuse->set_req(k, r);
              uses_found++;
              if (useuse->is_Loop() && k == LoopNode::EntryControl) {
                // This is not a dom_depth > dd_r because when new
                // control flow is constructed by a loop opt, a node
                // and its dominator can end up at the same dom_depth
                assert(dom_depth(useuse) >= dd_r , "");
                set_idom(useuse, r, dom_depth(useuse));
              }
            }
          }
          l -= uses_found;    // we deleted 1 or more copies of this edge
        }

        assert(use->is_Proj(), "loop exit should be projection");
        // lazy_replace() below moves all nodes that are:
        // - control dependent on the loop exit or
        // - have control set to the loop exit
        // below the post-loop merge point. lazy_replace() takes a dead control as first input. To make it
        // possible to use it, the loop exit projection is cloned and becomes the new exit projection. The initial one
        // becomes dead and is "replaced" by the region.
        Node* use_clone = use->clone();
        register_control(use_clone, use_loop, idom(use), dom_depth(use));
        // Now finish up 'r'
        r->set_req(1, newuse);
        r->set_req(2, use_clone);
        _igvn.register_new_node_with_optimizer(r);
        set_loop(r, use_loop);
        set_idom(r, (side_by_side_idom == nullptr) ? newuse->in(0) : side_by_side_idom, dd_r);
        lazy_replace(use, r);
        // Map the (cloned) old use to the new merge point
        old_new.map(use_clone->_idx, r);
      } // End of if a loop-exit test
    }
  }
}

void PhaseIdealLoop::fix_body_edges(const Node_List &body, IdealLoopTree* loop, const Node_List &old_new, int dd,
                                    IdealLoopTree* parent, bool partial) {
  for(uint i = 0; i < body.size(); i++ ) {
    Node *old = body.at(i);
    Node *nnn = old_new[old->_idx];
    // Fix CFG/Loop controlling the new node
    if (has_ctrl(old)) {
      set_ctrl(nnn, old_new[get_ctrl(old)->_idx]);
    } else {
      set_loop(nnn, parent);
      if (old->outcnt() > 0) {
        Node* dom = idom(old);
        if (old_new[dom->_idx] != nullptr) {
          dom = old_new[dom->_idx];
          set_idom(nnn, dom, dd );
        }
      }
    }
    // Correct edges to the new node
    for (uint j = 0; j < nnn->req(); j++) {
        Node *n = nnn->in(j);
        if (n != nullptr) {
          IdealLoopTree *old_in_loop = get_loop(has_ctrl(n) ? get_ctrl(n) : n);
          if (loop->is_member(old_in_loop)) {
            if (old_new[n->_idx] != nullptr) {
              nnn->set_req(j, old_new[n->_idx]);
            } else {
              assert(!body.contains(n), "");
              assert(partial, "node not cloned");
            }
          }
        }
    }
    _igvn.hash_find_insert(nnn);
  }
}

void PhaseIdealLoop::clone_loop_body(const Node_List& body, Node_List &old_new, CloneMap* cm) {
  for (uint i = 0; i < body.size(); i++) {
    Node* old = body.at(i);
    Node* nnn = old->clone();
    old_new.map(old->_idx, nnn);
    if (C->do_vector_loop() && cm != nullptr) {
      cm->verify_insert_and_clone(old, nnn, cm->clone_idx());
    }
    _igvn.register_new_node_with_optimizer(nnn);
  }
}


//---------------------- stride_of_possible_iv -------------------------------------
// Looks for an iff/bool/comp with one operand of the compare
// being a cycle involving an add and a phi,
// with an optional truncation (left-shift followed by a right-shift)
// of the add. Returns zero if not an iv.
int PhaseIdealLoop::stride_of_possible_iv(Node* iff) {
  Node* trunc1 = nullptr;
  Node* trunc2 = nullptr;
  const TypeInteger* ttype = nullptr;
  if (!iff->is_If() || iff->in(1) == nullptr || !iff->in(1)->is_Bool()) {
    return 0;
  }
  BoolNode* bl = iff->in(1)->as_Bool();
  Node* cmp = bl->in(1);
  if (!cmp || (cmp->Opcode() != Op_CmpI && cmp->Opcode() != Op_CmpU)) {
    return 0;
  }
  // Must have an invariant operand
  if (is_member(get_loop(iff), get_ctrl(cmp->in(2)))) {
    return 0;
  }
  Node* add2 = nullptr;
  Node* cmp1 = cmp->in(1);
  if (cmp1->is_Phi()) {
    // (If (Bool (CmpX phi:(Phi ...(Optional-trunc(AddI phi add2))) )))
    Node* phi = cmp1;
    for (uint i = 1; i < phi->req(); i++) {
      Node* in = phi->in(i);
      Node* add = CountedLoopNode::match_incr_with_optional_truncation(in,
                                &trunc1, &trunc2, &ttype, T_INT);
      if (add && add->in(1) == phi) {
        add2 = add->in(2);
        break;
      }
    }
  } else {
    // (If (Bool (CmpX addtrunc:(Optional-trunc((AddI (Phi ...addtrunc...) add2)) )))
    Node* addtrunc = cmp1;
    Node* add = CountedLoopNode::match_incr_with_optional_truncation(addtrunc,
                                &trunc1, &trunc2, &ttype, T_INT);
    if (add && add->in(1)->is_Phi()) {
      Node* phi = add->in(1);
      for (uint i = 1; i < phi->req(); i++) {
        if (phi->in(i) == addtrunc) {
          add2 = add->in(2);
          break;
        }
      }
    }
  }
  if (add2 != nullptr) {
    const TypeInt* add2t = _igvn.type(add2)->is_int();
    if (add2t->is_con()) {
      return add2t->get_con();
    }
  }
  return 0;
}


//---------------------- stay_in_loop -------------------------------------
// Return the (unique) control output node that's in the loop (if it exists.)
Node* PhaseIdealLoop::stay_in_loop( Node* n, IdealLoopTree *loop) {
  Node* unique = nullptr;
  if (!n) return nullptr;
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node* use = n->fast_out(i);
    if (!has_ctrl(use) && loop->is_member(get_loop(use))) {
      if (unique != nullptr) {
        return nullptr;
      }
      unique = use;
    }
  }
  return unique;
}

//------------------------------ register_node -------------------------------------
// Utility to register node "n" with PhaseIdealLoop
void PhaseIdealLoop::register_node(Node* n, IdealLoopTree* loop, Node* pred, uint ddepth) {
  _igvn.register_new_node_with_optimizer(n);
  loop->_body.push(n);
  if (n->is_CFG()) {
    set_loop(n, loop);
    set_idom(n, pred, ddepth);
  } else {
    set_ctrl(n, pred);
  }
}

//------------------------------ proj_clone -------------------------------------
// Utility to create an if-projection
ProjNode* PhaseIdealLoop::proj_clone(ProjNode* p, IfNode* iff) {
  ProjNode* c = p->clone()->as_Proj();
  c->set_req(0, iff);
  return c;
}

//------------------------------ short_circuit_if -------------------------------------
// Force the iff control output to be the live_proj
Node* PhaseIdealLoop::short_circuit_if(IfNode* iff, ProjNode* live_proj) {
  guarantee(live_proj != nullptr, "null projection");
  int proj_con = live_proj->_con;
  assert(proj_con == 0 || proj_con == 1, "false or true projection");
  Node *con = _igvn.intcon(proj_con);
  set_ctrl(con, C->root());
  if (iff) {
    iff->set_req(1, con);
  }
  return con;
}

//------------------------------ insert_if_before_proj -------------------------------------
// Insert a new if before an if projection (* - new node)
//
// before
//           if(test)
//           /     \
//          v       v
//    other-proj   proj (arg)
//
// after
//           if(test)
//           /     \
//          /       v
//         |      * proj-clone
//         v          |
//    other-proj      v
//                * new_if(relop(cmp[IU](left,right)))
//                  /  \
//                 v    v
//         * new-proj  proj
//         (returned)
//
ProjNode* PhaseIdealLoop::insert_if_before_proj(Node* left, bool Signed, BoolTest::mask relop, Node* right, ProjNode* proj) {
  IfNode* iff = proj->in(0)->as_If();
  IdealLoopTree *loop = get_loop(proj);
  ProjNode *other_proj = iff->proj_out(!proj->is_IfTrue())->as_Proj();
  uint ddepth = dom_depth(proj);

  _igvn.rehash_node_delayed(iff);
  _igvn.rehash_node_delayed(proj);

  proj->set_req(0, nullptr);  // temporary disconnect
  ProjNode* proj2 = proj_clone(proj, iff);
  register_node(proj2, loop, iff, ddepth);

  Node* cmp = Signed ? (Node*) new CmpINode(left, right) : (Node*) new CmpUNode(left, right);
  register_node(cmp, loop, proj2, ddepth);

  BoolNode* bol = new BoolNode(cmp, relop);
  register_node(bol, loop, proj2, ddepth);

  int opcode = iff->Opcode();
  assert(opcode == Op_If || opcode == Op_RangeCheck, "unexpected opcode");
  IfNode* new_if = (opcode == Op_If) ? new IfNode(proj2, bol, iff->_prob, iff->_fcnt):
    new RangeCheckNode(proj2, bol, iff->_prob, iff->_fcnt);
  register_node(new_if, loop, proj2, ddepth);

  proj->set_req(0, new_if); // reattach
  set_idom(proj, new_if, ddepth);

  ProjNode* new_exit = proj_clone(other_proj, new_if)->as_Proj();
  guarantee(new_exit != nullptr, "null exit node");
  register_node(new_exit, get_loop(other_proj), new_if, ddepth);

  return new_exit;
}

//------------------------------ insert_region_before_proj -------------------------------------
// Insert a region before an if projection (* - new node)
//
// before
//           if(test)
//          /      |
//         v       |
//       proj      v
//               other-proj
//
// after
//           if(test)
//          /      |
//         v       |
// * proj-clone    v
//         |     other-proj
//         v
// * new-region
//         |
//         v
// *      dum_if
//       /     \
//      v       \
// * dum-proj    v
//              proj
//
RegionNode* PhaseIdealLoop::insert_region_before_proj(ProjNode* proj) {
  IfNode* iff = proj->in(0)->as_If();
  IdealLoopTree *loop = get_loop(proj);
  ProjNode *other_proj = iff->proj_out(!proj->is_IfTrue())->as_Proj();
  uint ddepth = dom_depth(proj);

  _igvn.rehash_node_delayed(iff);
  _igvn.rehash_node_delayed(proj);

  proj->set_req(0, nullptr);  // temporary disconnect
  ProjNode* proj2 = proj_clone(proj, iff);
  register_node(proj2, loop, iff, ddepth);

  RegionNode* reg = new RegionNode(2);
  reg->set_req(1, proj2);
  register_node(reg, loop, iff, ddepth);

  IfNode* dum_if = new IfNode(reg, short_circuit_if(nullptr, proj), iff->_prob, iff->_fcnt);
  register_node(dum_if, loop, reg, ddepth);

  proj->set_req(0, dum_if); // reattach
  set_idom(proj, dum_if, ddepth);

  ProjNode* dum_proj = proj_clone(other_proj, dum_if);
  register_node(dum_proj, loop, dum_if, ddepth);

  return reg;
}

//------------------------------ insert_cmpi_loop_exit -------------------------------------
// Clone a signed compare loop exit from an unsigned compare and
// insert it before the unsigned cmp on the stay-in-loop path.
// All new nodes inserted in the dominator tree between the original
// if and it's projections.  The original if test is replaced with
// a constant to force the stay-in-loop path.
//
// This is done to make sure that the original if and it's projections
// still dominate the same set of control nodes, that the ctrl() relation
// from data nodes to them is preserved, and that their loop nesting is
// preserved.
//
// before
//          if(i <u limit)    unsigned compare loop exit
//         /       |
//        v        v
//   exit-proj   stay-in-loop-proj
//
// after
//          if(stay-in-loop-const)  original if
//         /       |
//        /        v
//       /  if(i <  limit)    new signed test
//      /  /       |
//     /  /        v
//    /  /  if(i <u limit)    new cloned unsigned test
//   /  /   /      |
//   v  v  v       |
//    region       |
//        |        |
//      dum-if     |
//     /  |        |
// ether  |        |
//        v        v
//   exit-proj   stay-in-loop-proj
//
IfNode* PhaseIdealLoop::insert_cmpi_loop_exit(IfNode* if_cmpu, IdealLoopTree *loop) {
  const bool Signed   = true;
  const bool Unsigned = false;

  BoolNode* bol = if_cmpu->in(1)->as_Bool();
  if (bol->_test._test != BoolTest::lt) return nullptr;
  CmpNode* cmpu = bol->in(1)->as_Cmp();
  if (cmpu->Opcode() != Op_CmpU) return nullptr;
  int stride = stride_of_possible_iv(if_cmpu);
  if (stride == 0) return nullptr;

  Node* lp_proj = stay_in_loop(if_cmpu, loop);
  guarantee(lp_proj != nullptr, "null loop node");

  ProjNode* lp_continue = lp_proj->as_Proj();
  ProjNode* lp_exit     = if_cmpu->proj_out(!lp_continue->is_IfTrue())->as_Proj();
  if (!lp_exit->is_IfFalse()) {
    // The loop exit condition is (i <u limit) ==> (i >= 0 && i < limit).
    // We therefore can't add a single exit condition.
    return nullptr;
  }
  // The loop exit condition is !(i <u limit) ==> (i < 0 || i >= limit).
  // Split out the exit condition (i < 0) for stride < 0 or (i >= limit) for stride > 0.
  Node* limit = nullptr;
  if (stride > 0) {
    limit = cmpu->in(2);
  } else {
    limit = _igvn.makecon(TypeInt::ZERO);
    set_ctrl(limit, C->root());
  }
  // Create a new region on the exit path
  RegionNode* reg = insert_region_before_proj(lp_exit);
  guarantee(reg != nullptr, "null region node");

  // Clone the if-cmpu-true-false using a signed compare
  BoolTest::mask rel_i = stride > 0 ? bol->_test._test : BoolTest::ge;
  ProjNode* cmpi_exit = insert_if_before_proj(cmpu->in(1), Signed, rel_i, limit, lp_continue);
  reg->add_req(cmpi_exit);

  // Clone the if-cmpu-true-false
  BoolTest::mask rel_u = bol->_test._test;
  ProjNode* cmpu_exit = insert_if_before_proj(cmpu->in(1), Unsigned, rel_u, cmpu->in(2), lp_continue);
  reg->add_req(cmpu_exit);

  // Force original if to stay in loop.
  short_circuit_if(if_cmpu, lp_continue);

  return cmpi_exit->in(0)->as_If();
}

//------------------------------ remove_cmpi_loop_exit -------------------------------------
// Remove a previously inserted signed compare loop exit.
void PhaseIdealLoop::remove_cmpi_loop_exit(IfNode* if_cmp, IdealLoopTree *loop) {
  Node* lp_proj = stay_in_loop(if_cmp, loop);
  assert(if_cmp->in(1)->in(1)->Opcode() == Op_CmpI &&
         stay_in_loop(lp_proj, loop)->is_If() &&
         stay_in_loop(lp_proj, loop)->in(1)->in(1)->Opcode() == Op_CmpU, "inserted cmpi before cmpu");
  Node *con = _igvn.makecon(lp_proj->is_IfTrue() ? TypeInt::ONE : TypeInt::ZERO);
  set_ctrl(con, C->root());
  if_cmp->set_req(1, con);
}

//------------------------------ scheduled_nodelist -------------------------------------
// Create a post order schedule of nodes that are in the
// "member" set.  The list is returned in "sched".
// The first node in "sched" is the loop head, followed by
// nodes which have no inputs in the "member" set, and then
// followed by the nodes that have an immediate input dependence
// on a node in "sched".
void PhaseIdealLoop::scheduled_nodelist( IdealLoopTree *loop, VectorSet& member, Node_List &sched ) {

  assert(member.test(loop->_head->_idx), "loop head must be in member set");
  VectorSet visited;
  Node_Stack nstack(loop->_body.size());

  Node* n  = loop->_head;  // top of stack is cached in "n"
  uint idx = 0;
  visited.set(n->_idx);

  // Initially push all with no inputs from within member set
  for(uint i = 0; i < loop->_body.size(); i++ ) {
    Node *elt = loop->_body.at(i);
    if (member.test(elt->_idx)) {
      bool found = false;
      for (uint j = 0; j < elt->req(); j++) {
        Node* def = elt->in(j);
        if (def && member.test(def->_idx) && def != elt) {
          found = true;
          break;
        }
      }
      if (!found && elt != loop->_head) {
        nstack.push(n, idx);
        n = elt;
        assert(!visited.test(n->_idx), "not seen yet");
        visited.set(n->_idx);
      }
    }
  }

  // traverse out's that are in the member set
  while (true) {
    if (idx < n->outcnt()) {
      Node* use = n->raw_out(idx);
      idx++;
      if (!visited.test_set(use->_idx)) {
        if (member.test(use->_idx)) {
          nstack.push(n, idx);
          n = use;
          idx = 0;
        }
      }
    } else {
      // All outputs processed
      sched.push(n);
      if (nstack.is_empty()) break;
      n   = nstack.node();
      idx = nstack.index();
      nstack.pop();
    }
  }
}


//------------------------------ has_use_in_set -------------------------------------
// Has a use in the vector set
bool PhaseIdealLoop::has_use_in_set( Node* n, VectorSet& vset ) {
  for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
    Node* use = n->fast_out(j);
    if (vset.test(use->_idx)) {
      return true;
    }
  }
  return false;
}


//------------------------------ has_use_internal_to_set -------------------------------------
// Has use internal to the vector set (ie. not in a phi at the loop head)
bool PhaseIdealLoop::has_use_internal_to_set( Node* n, VectorSet& vset, IdealLoopTree *loop ) {
  Node* head  = loop->_head;
  for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
    Node* use = n->fast_out(j);
    if (vset.test(use->_idx) && !(use->is_Phi() && use->in(0) == head)) {
      return true;
    }
  }
  return false;
}


//------------------------------ clone_for_use_outside_loop -------------------------------------
// clone "n" for uses that are outside of loop
int PhaseIdealLoop::clone_for_use_outside_loop( IdealLoopTree *loop, Node* n, Node_List& worklist ) {
  int cloned = 0;
  assert(worklist.size() == 0, "should be empty");
  for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
    Node* use = n->fast_out(j);
    if( !loop->is_member(get_loop(has_ctrl(use) ? get_ctrl(use) : use)) ) {
      worklist.push(use);
    }
  }

  if (C->check_node_count(worklist.size() + NodeLimitFudgeFactor,
                          "Too many clones required in clone_for_use_outside_loop in partial peeling")) {
    return -1;
  }

  while( worklist.size() ) {
    Node *use = worklist.pop();
    if (!has_node(use) || use->in(0) == C->top()) continue;
    uint j;
    for (j = 0; j < use->req(); j++) {
      if (use->in(j) == n) break;
    }
    assert(j < use->req(), "must be there");

    // clone "n" and insert it between the inputs of "n" and the use outside the loop
    Node* n_clone = n->clone();
    _igvn.replace_input_of(use, j, n_clone);
    cloned++;
    Node* use_c;
    if (!use->is_Phi()) {
      use_c = has_ctrl(use) ? get_ctrl(use) : use->in(0);
    } else {
      // Use in a phi is considered a use in the associated predecessor block
      use_c = use->in(0)->in(j);
    }
    set_ctrl(n_clone, use_c);
    assert(!loop->is_member(get_loop(use_c)), "should be outside loop");
    get_loop(use_c)->_body.push(n_clone);
    _igvn.register_new_node_with_optimizer(n_clone);
#ifndef PRODUCT
    if (TracePartialPeeling) {
      tty->print_cr("loop exit cloning old: %d new: %d newbb: %d", n->_idx, n_clone->_idx, get_ctrl(n_clone)->_idx);
    }
#endif
  }
  return cloned;
}


//------------------------------ clone_for_special_use_inside_loop -------------------------------------
// clone "n" for special uses that are in the not_peeled region.
// If these def-uses occur in separate blocks, the code generator
// marks the method as not compilable.  For example, if a "BoolNode"
// is in a different basic block than the "IfNode" that uses it, then
// the compilation is aborted in the code generator.
void PhaseIdealLoop::clone_for_special_use_inside_loop( IdealLoopTree *loop, Node* n,
                                                        VectorSet& not_peel, Node_List& sink_list, Node_List& worklist ) {
  if (n->is_Phi() || n->is_Load()) {
    return;
  }
  assert(worklist.size() == 0, "should be empty");
  for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
    Node* use = n->fast_out(j);
    if ( not_peel.test(use->_idx) &&
         (use->is_If() || use->is_CMove() || use->is_Bool()) &&
         use->in(1) == n)  {
      worklist.push(use);
    }
  }
  if (worklist.size() > 0) {
    // clone "n" and insert it between inputs of "n" and the use
    Node* n_clone = n->clone();
    loop->_body.push(n_clone);
    _igvn.register_new_node_with_optimizer(n_clone);
    set_ctrl(n_clone, get_ctrl(n));
    sink_list.push(n_clone);
    not_peel.set(n_clone->_idx);
#ifndef PRODUCT
    if (TracePartialPeeling) {
      tty->print_cr("special not_peeled cloning old: %d new: %d", n->_idx, n_clone->_idx);
    }
#endif
    while( worklist.size() ) {
      Node *use = worklist.pop();
      _igvn.rehash_node_delayed(use);
      for (uint j = 1; j < use->req(); j++) {
        if (use->in(j) == n) {
          use->set_req(j, n_clone);
        }
      }
    }
  }
}


//------------------------------ insert_phi_for_loop -------------------------------------
// Insert phi(lp_entry_val, back_edge_val) at use->in(idx) for loop lp if phi does not already exist
void PhaseIdealLoop::insert_phi_for_loop( Node* use, uint idx, Node* lp_entry_val, Node* back_edge_val, LoopNode* lp ) {
  Node *phi = PhiNode::make(lp, back_edge_val);
  phi->set_req(LoopNode::EntryControl, lp_entry_val);
  // Use existing phi if it already exists
  Node *hit = _igvn.hash_find_insert(phi);
  if( hit == nullptr ) {
    _igvn.register_new_node_with_optimizer(phi);
    set_ctrl(phi, lp);
  } else {
    // Remove the new phi from the graph and use the hit
    _igvn.remove_dead_node(phi);
    phi = hit;
  }
  _igvn.replace_input_of(use, idx, phi);
}

#ifdef ASSERT
//------------------------------ is_valid_loop_partition -------------------------------------
// Validate the loop partition sets: peel and not_peel
bool PhaseIdealLoop::is_valid_loop_partition( IdealLoopTree *loop, VectorSet& peel, Node_List& peel_list,
                                              VectorSet& not_peel ) {
  uint i;
  // Check that peel_list entries are in the peel set
  for (i = 0; i < peel_list.size(); i++) {
    if (!peel.test(peel_list.at(i)->_idx)) {
      return false;
    }
  }
  // Check at loop members are in one of peel set or not_peel set
  for (i = 0; i < loop->_body.size(); i++ ) {
    Node *def  = loop->_body.at(i);
    uint di = def->_idx;
    // Check that peel set elements are in peel_list
    if (peel.test(di)) {
      if (not_peel.test(di)) {
        return false;
      }
      // Must be in peel_list also
      bool found = false;
      for (uint j = 0; j < peel_list.size(); j++) {
        if (peel_list.at(j)->_idx == di) {
          found = true;
          break;
        }
      }
      if (!found) {
        return false;
      }
    } else if (not_peel.test(di)) {
      if (peel.test(di)) {
        return false;
      }
    } else {
      return false;
    }
  }
  return true;
}

//------------------------------ is_valid_clone_loop_exit_use -------------------------------------
// Ensure a use outside of loop is of the right form
bool PhaseIdealLoop::is_valid_clone_loop_exit_use( IdealLoopTree *loop, Node* use, uint exit_idx) {
  Node *use_c = has_ctrl(use) ? get_ctrl(use) : use;
  return (use->is_Phi() &&
          use_c->is_Region() && use_c->req() == 3 &&
          (use_c->in(exit_idx)->Opcode() == Op_IfTrue ||
           use_c->in(exit_idx)->Opcode() == Op_IfFalse ||
           use_c->in(exit_idx)->Opcode() == Op_JumpProj) &&
          loop->is_member( get_loop( use_c->in(exit_idx)->in(0) ) ) );
}

//------------------------------ is_valid_clone_loop_form -------------------------------------
// Ensure that all uses outside of loop are of the right form
bool PhaseIdealLoop::is_valid_clone_loop_form( IdealLoopTree *loop, Node_List& peel_list,
                                               uint orig_exit_idx, uint clone_exit_idx) {
  uint len = peel_list.size();
  for (uint i = 0; i < len; i++) {
    Node *def = peel_list.at(i);

    for (DUIterator_Fast jmax, j = def->fast_outs(jmax); j < jmax; j++) {
      Node *use = def->fast_out(j);
      Node *use_c = has_ctrl(use) ? get_ctrl(use) : use;
      if (!loop->is_member(get_loop(use_c))) {
        // use is not in the loop, check for correct structure
        if (use->in(0) == def) {
          // Okay
        } else if (!is_valid_clone_loop_exit_use(loop, use, orig_exit_idx)) {
          return false;
        }
      }
    }
  }
  return true;
}
#endif

//------------------------------ partial_peel -------------------------------------
// Partially peel (aka loop rotation) the top portion of a loop (called
// the peel section below) by cloning it and placing one copy just before
// the new loop head and the other copy at the bottom of the new loop.
//
//    before                       after                where it came from
//
//    stmt1                        stmt1
//  loop:                          stmt2                     clone
//    stmt2                        if condA goto exitA       clone
//    if condA goto exitA        new_loop:                   new
//    stmt3                        stmt3                     clone
//    if !condB goto loop          if condB goto exitB       clone
//  exitB:                         stmt2                     orig
//    stmt4                        if !condA goto new_loop   orig
//  exitA:                         goto exitA
//                               exitB:
//                                 stmt4
//                               exitA:
//
// Step 1: find the cut point: an exit test on probable
//         induction variable.
// Step 2: schedule (with cloning) operations in the peel
//         section that can be executed after the cut into
//         the section that is not peeled.  This may need
//         to clone operations into exit blocks.  For
//         instance, a reference to A[i] in the not-peel
//         section and a reference to B[i] in an exit block
//         may cause a left-shift of i by 2 to be placed
//         in the peel block.  This step will clone the left
//         shift into the exit block and sink the left shift
//         from the peel to the not-peel section.
// Step 3: clone the loop, retarget the control, and insert
//         phis for values that are live across the new loop
//         head.  This is very dependent on the graph structure
//         from clone_loop.  It creates region nodes for
//         exit control and associated phi nodes for values
//         flow out of the loop through that exit.  The region
//         node is dominated by the clone's control projection.
//         So the clone's peel section is placed before the
//         new loop head, and the clone's not-peel section is
//         forms the top part of the new loop.  The original
//         peel section forms the tail of the new loop.
// Step 4: update the dominator tree and recompute the
//         dominator depth.
//
//                   orig
//
//                   stmt1
//                     |
//                     v
//                 predicates
//                     |
//                     v
//                   loop<----+
//                     |      |
//                   stmt2    |
//                     |      |
//                     v      |
//                    ifA     |
//                   / |      |
//                  v  v      |
//               false true   ^  <-- last_peel
//               /     |      |
//              /   ===|==cut |
//             /     stmt3    |  <-- first_not_peel
//            /        |      |
//            |        v      |
//            v       ifB     |
//          exitA:   / \      |
//                  /   \     |
//                 v     v    |
//               false true   |
//               /       \    |
//              /         ----+
//             |
//             v
//           exitB:
//           stmt4
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
//   +---->loop                loop<----+
//   |      |                    |      |
//   |    stmt2                stmt2    |
//   |      |                    |      |
//   |      v                    v      |
//   |      ifA                 ifA     |
//   |      | \                / |      |
//   |      v  v              v  v      |
//   ^    true  false      false true   ^  <-- last_peel
//   |      |   ^   \       /    |      |
//   | cut==|==  \   \     /  ===|==cut |
//   |    stmt3   \   \   /    stmt3    |  <-- first_not_peel
//   |      |    dom   | |       |      |
//   |      v      \  1v v2      v      |
//   |      ifB     regionA     ifB     |
//   |      / \        |       / \      |
//   |     /   \       v      /   \     |
//   |    v     v    exitA:  v     v    |
//   |    true  false      false true   |
//   |    /     ^   \      /       \    |
//   +----       \   \    /         ----+
//               dom  \  /
//                 \  1v v2
//                  regionB
//                     |
//                     v
//                   exitB:
//                   stmt4
//
//
//           after partial peel
//
//                  stmt1
//                     |
//                     v
//                predicates
//                 /
//        clone   /             orig
//               /          TOP
//              /             \
//             v               v
//    TOP->loop                loop----+
//          |                    |      |
//        stmt2                stmt2    |
//          |                    |      |
//          v                    v      |
//          ifA                 ifA     |
//          | \                / |      |
//          v  v              v  v      |
//        true  false      false true   |     <-- last_peel
//          |   ^   \       /    +------|---+
//  +->newloop   \   \     /  === ==cut |   |
//  |     stmt3   \   \   /     TOP     |   |
//  |       |    dom   | |      stmt3   |   | <-- first_not_peel
//  |       v      \  1v v2      v      |   |
//  |       ifB     regionA     ifB     ^   v
//  |       / \        |       / \      |   |
//  |      /   \       v      /   \     |   |
//  |     v     v    exitA:  v     v    |   |
//  |     true  false      false true   |   |
//  |     /     ^   \      /       \    |   |
//  |    |       \   \    /         v   |   |
//  |    |       dom  \  /         TOP  |   |
//  |    |         \  1v v2             |   |
//  ^    v          regionB             |   |
//  |    |             |                |   |
//  |    |             v                ^   v
//  |    |           exitB:             |   |
//  |    |           stmt4              |   |
//  |    +------------>-----------------+   |
//  |                                       |
//  +-----------------<---------------------+
//
//
//              final graph
//
//                  stmt1
//                    |
//                    v
//                predicates
//                    |
//                    v
//                  stmt2 clone
//                    |
//                    v
//         ........> ifA clone
//         :        / |
//        dom      /  |
//         :      v   v
//         :  false   true
//         :  |       |
//         :  |       v
//         :  |    newloop<-----+
//         :  |        |        |
//         :  |     stmt3 clone |
//         :  |        |        |
//         :  |        v        |
//         :  |       ifB       |
//         :  |      / \        |
//         :  |     v   v       |
//         :  |  false true     |
//         :  |   |     |       |
//         :  |   v    stmt2    |
//         :  | exitB:  |       |
//         :  | stmt4   v       |
//         :  |       ifA orig  |
//         :  |      /  \       |
//         :  |     /    \      |
//         :  |    v     v      |
//         :  |  false  true    |
//         :  |  /        \     |
//         :  v  v         -----+
//          RegionA
//             |
//             v
//           exitA
//
bool PhaseIdealLoop::partial_peel( IdealLoopTree *loop, Node_List &old_new ) {

  assert(!loop->_head->is_CountedLoop(), "Non-counted loop only");
  if (!loop->_head->is_Loop()) {
    return false;
  }
  LoopNode *head = loop->_head->as_Loop();

  if (head->is_partial_peel_loop() || head->partial_peel_has_failed()) {
    return false;
  }

  // Check for complex exit control
  for (uint ii = 0; ii < loop->_body.size(); ii++) {
    Node *n = loop->_body.at(ii);
    int opc = n->Opcode();
    if (n->is_Call()        ||
        opc == Op_Catch     ||
        opc == Op_CatchProj ||
        opc == Op_Jump      ||
        opc == Op_JumpProj) {
#ifndef PRODUCT
      if (TracePartialPeeling) {
        tty->print_cr("\nExit control too complex: lp: %d", head->_idx);
      }
#endif
      return false;
    }
  }

  int dd = dom_depth(head);

  // Step 1: find cut point

  // Walk up dominators to loop head looking for first loop exit
  // which is executed on every path thru loop.
  IfNode *peel_if = nullptr;
  IfNode *peel_if_cmpu = nullptr;

  Node *iff = loop->tail();
  while (iff != head) {
    if (iff->is_If()) {
      Node *ctrl = get_ctrl(iff->in(1));
      if (ctrl->is_top()) return false; // Dead test on live IF.
      // If loop-varying exit-test, check for induction variable
      if (loop->is_member(get_loop(ctrl)) &&
          loop->is_loop_exit(iff) &&
          is_possible_iv_test(iff)) {
        Node* cmp = iff->in(1)->in(1);
        if (cmp->Opcode() == Op_CmpI) {
          peel_if = iff->as_If();
        } else {
          assert(cmp->Opcode() == Op_CmpU, "must be CmpI or CmpU");
          peel_if_cmpu = iff->as_If();
        }
      }
    }
    iff = idom(iff);
  }

  // Prefer signed compare over unsigned compare.
  IfNode* new_peel_if = nullptr;
  if (peel_if == nullptr) {
    if (!PartialPeelAtUnsignedTests || peel_if_cmpu == nullptr) {
      return false;   // No peel point found
    }
    new_peel_if = insert_cmpi_loop_exit(peel_if_cmpu, loop);
    if (new_peel_if == nullptr) {
      return false;   // No peel point found
    }
    peel_if = new_peel_if;
  }
  Node* last_peel        = stay_in_loop(peel_if, loop);
  Node* first_not_peeled = stay_in_loop(last_peel, loop);
  if (first_not_peeled == nullptr || first_not_peeled == head) {
    return false;
  }

#ifndef PRODUCT
  if (TraceLoopOpts) {
    tty->print("PartialPeel  ");
    loop->dump_head();
  }

  if (TracePartialPeeling) {
    tty->print_cr("before partial peel one iteration");
    Node_List wl;
    Node* t = head->in(2);
    while (true) {
      wl.push(t);
      if (t == head) break;
      t = idom(t);
    }
    while (wl.size() > 0) {
      Node* tt = wl.pop();
      tt->dump();
      if (tt == last_peel) tty->print_cr("-- cut --");
    }
  }
#endif

  C->print_method(PHASE_BEFORE_PARTIAL_PEELING, 4, head);

  VectorSet peel;
  VectorSet not_peel;
  Node_List peel_list;
  Node_List worklist;
  Node_List sink_list;

  uint estimate = loop->est_loop_clone_sz(1);
  if (exceeding_node_budget(estimate)) {
    return false;
  }

  // Set of cfg nodes to peel are those that are executable from
  // the head through last_peel.
  assert(worklist.size() == 0, "should be empty");
  worklist.push(head);
  peel.set(head->_idx);
  while (worklist.size() > 0) {
    Node *n = worklist.pop();
    if (n != last_peel) {
      for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
        Node* use = n->fast_out(j);
        if (use->is_CFG() &&
            loop->is_member(get_loop(use)) &&
            !peel.test_set(use->_idx)) {
          worklist.push(use);
        }
      }
    }
  }

  // Set of non-cfg nodes to peel are those that are control
  // dependent on the cfg nodes.
  for (uint i = 0; i < loop->_body.size(); i++) {
    Node *n = loop->_body.at(i);
    Node *n_c = has_ctrl(n) ? get_ctrl(n) : n;
    if (peel.test(n_c->_idx)) {
      peel.set(n->_idx);
    } else {
      not_peel.set(n->_idx);
    }
  }

  // Step 2: move operations from the peeled section down into the
  //         not-peeled section

  // Get a post order schedule of nodes in the peel region
  // Result in right-most operand.
  scheduled_nodelist(loop, peel, peel_list);

  assert(is_valid_loop_partition(loop, peel, peel_list, not_peel), "bad partition");

  // For future check for too many new phis
  uint old_phi_cnt = 0;
  for (DUIterator_Fast jmax, j = head->fast_outs(jmax); j < jmax; j++) {
    Node* use = head->fast_out(j);
    if (use->is_Phi()) old_phi_cnt++;
  }

#ifndef PRODUCT
  if (TracePartialPeeling) {
    tty->print_cr("\npeeled list");
  }
#endif

  // Evacuate nodes in peel region into the not_peeled region if possible
  bool too_many_clones = false;
  uint new_phi_cnt = 0;
  uint cloned_for_outside_use = 0;
  for (uint i = 0; i < peel_list.size();) {
    Node* n = peel_list.at(i);
#ifndef PRODUCT
  if (TracePartialPeeling) n->dump();
#endif
    bool incr = true;
    if (!n->is_CFG()) {
      if (has_use_in_set(n, not_peel)) {
        // If not used internal to the peeled region,
        // move "n" from peeled to not_peeled region.
        if (!has_use_internal_to_set(n, peel, loop)) {
          // if not pinned and not a load (which maybe anti-dependent on a store)
          // and not a CMove (Matcher expects only bool->cmove).
          if (n->in(0) == nullptr && !n->is_Load() && !n->is_CMove()) {
            int new_clones = clone_for_use_outside_loop(loop, n, worklist);
            if (C->failing()) return false;
            if (new_clones == -1) {
              too_many_clones = true;
              break;
            }
            cloned_for_outside_use += new_clones;
            sink_list.push(n);
            peel.remove(n->_idx);
            not_peel.set(n->_idx);
            peel_list.remove(i);
            incr = false;
#ifndef PRODUCT
            if (TracePartialPeeling) {
              tty->print_cr("sink to not_peeled region: %d newbb: %d",
                            n->_idx, get_ctrl(n)->_idx);
            }
#endif
          }
        } else {
          // Otherwise check for special def-use cases that span
          // the peel/not_peel boundary such as bool->if
          clone_for_special_use_inside_loop(loop, n, not_peel, sink_list, worklist);
          new_phi_cnt++;
        }
      }
    }
    if (incr) i++;
  }

  estimate += cloned_for_outside_use + new_phi_cnt;
  bool exceed_node_budget = !may_require_nodes(estimate);
  bool exceed_phi_limit = new_phi_cnt > old_phi_cnt + PartialPeelNewPhiDelta;

  if (too_many_clones || exceed_node_budget || exceed_phi_limit) {
#ifndef PRODUCT
    if (TracePartialPeeling && exceed_phi_limit) {
      tty->print_cr("\nToo many new phis: %d  old %d new cmpi: %c",
                    new_phi_cnt, old_phi_cnt, new_peel_if != nullptr?'T':'F');
    }
#endif
    if (new_peel_if != nullptr) {
      remove_cmpi_loop_exit(new_peel_if, loop);
    }
    // Inhibit more partial peeling on this loop
    assert(!head->is_partial_peel_loop(), "not partial peeled");
    head->mark_partial_peel_failed();
    if (cloned_for_outside_use > 0) {
      // Terminate this round of loop opts because
      // the graph outside this loop was changed.
      C->set_major_progress();
      return true;
    }
    return false;
  }

  // Step 3: clone loop, retarget control, and insert new phis

  // Create new loop head for new phis and to hang
  // the nodes being moved (sinked) from the peel region.
  LoopNode* new_head = new LoopNode(last_peel, last_peel);
  new_head->set_unswitch_count(head->unswitch_count()); // Preserve
  _igvn.register_new_node_with_optimizer(new_head);
  assert(first_not_peeled->in(0) == last_peel, "last_peel <- first_not_peeled");
  _igvn.replace_input_of(first_not_peeled, 0, new_head);
  set_loop(new_head, loop);
  loop->_body.push(new_head);
  not_peel.set(new_head->_idx);
  set_idom(new_head, last_peel, dom_depth(first_not_peeled));
  set_idom(first_not_peeled, new_head, dom_depth(first_not_peeled));

  while (sink_list.size() > 0) {
    Node* n = sink_list.pop();
    set_ctrl(n, new_head);
  }

  assert(is_valid_loop_partition(loop, peel, peel_list, not_peel), "bad partition");

  clone_loop(loop, old_new, dd, IgnoreStripMined);

  const uint clone_exit_idx = 1;
  const uint orig_exit_idx  = 2;
  assert(is_valid_clone_loop_form(loop, peel_list, orig_exit_idx, clone_exit_idx), "bad clone loop");

  Node* head_clone             = old_new[head->_idx];
  LoopNode* new_head_clone     = old_new[new_head->_idx]->as_Loop();
  Node* orig_tail_clone        = head_clone->in(2);

  // Add phi if "def" node is in peel set and "use" is not

  for (uint i = 0; i < peel_list.size(); i++) {
    Node *def  = peel_list.at(i);
    if (!def->is_CFG()) {
      for (DUIterator_Fast jmax, j = def->fast_outs(jmax); j < jmax; j++) {
        Node *use = def->fast_out(j);
        if (has_node(use) && use->in(0) != C->top() &&
            (!peel.test(use->_idx) ||
             (use->is_Phi() && use->in(0) == head)) ) {
          worklist.push(use);
        }
      }
      while( worklist.size() ) {
        Node *use = worklist.pop();
        for (uint j = 1; j < use->req(); j++) {
          Node* n = use->in(j);
          if (n == def) {

            // "def" is in peel set, "use" is not in peel set
            // or "use" is in the entry boundary (a phi) of the peel set

            Node* use_c = has_ctrl(use) ? get_ctrl(use) : use;

            if ( loop->is_member(get_loop( use_c )) ) {
              // use is in loop
              if (old_new[use->_idx] != nullptr) { // null for dead code
                Node* use_clone = old_new[use->_idx];
                _igvn.replace_input_of(use, j, C->top());
                insert_phi_for_loop( use_clone, j, old_new[def->_idx], def, new_head_clone );
              }
            } else {
              assert(is_valid_clone_loop_exit_use(loop, use, orig_exit_idx), "clone loop format");
              // use is not in the loop, check if the live range includes the cut
              Node* lp_if = use_c->in(orig_exit_idx)->in(0);
              if (not_peel.test(lp_if->_idx)) {
                assert(j == orig_exit_idx, "use from original loop");
                insert_phi_for_loop( use, clone_exit_idx, old_new[def->_idx], def, new_head_clone );
              }
            }
          }
        }
      }
    }
  }

  // Step 3b: retarget control

  // Redirect control to the new loop head if a cloned node in
  // the not_peeled region has control that points into the peeled region.
  // This necessary because the cloned peeled region will be outside
  // the loop.
  //                            from    to
  //          cloned-peeled    <---+
  //    new_head_clone:            |    <--+
  //          cloned-not_peeled  in(0)    in(0)
  //          orig-peeled

  for (uint i = 0; i < loop->_body.size(); i++) {
    Node *n = loop->_body.at(i);
    if (!n->is_CFG()           && n->in(0) != nullptr        &&
        not_peel.test(n->_idx) && peel.test(n->in(0)->_idx)) {
      Node* n_clone = old_new[n->_idx];
      _igvn.replace_input_of(n_clone, 0, new_head_clone);
    }
  }

  // Backedge of the surviving new_head (the clone) is original last_peel
  _igvn.replace_input_of(new_head_clone, LoopNode::LoopBackControl, last_peel);

  // Cut first node in original not_peel set
  _igvn.rehash_node_delayed(new_head);                     // Multiple edge updates:
  new_head->set_req(LoopNode::EntryControl,    C->top());  //   use rehash_node_delayed / set_req instead of
  new_head->set_req(LoopNode::LoopBackControl, C->top());  //   multiple replace_input_of calls

  // Copy head_clone back-branch info to original head
  // and remove original head's loop entry and
  // clone head's back-branch
  _igvn.rehash_node_delayed(head); // Multiple edge updates
  head->set_req(LoopNode::EntryControl,    head_clone->in(LoopNode::LoopBackControl));
  head->set_req(LoopNode::LoopBackControl, C->top());
  _igvn.replace_input_of(head_clone, LoopNode::LoopBackControl, C->top());

  // Similarly modify the phis
  for (DUIterator_Fast kmax, k = head->fast_outs(kmax); k < kmax; k++) {
    Node* use = head->fast_out(k);
    if (use->is_Phi() && use->outcnt() > 0) {
      Node* use_clone = old_new[use->_idx];
      _igvn.rehash_node_delayed(use); // Multiple edge updates
      use->set_req(LoopNode::EntryControl,    use_clone->in(LoopNode::LoopBackControl));
      use->set_req(LoopNode::LoopBackControl, C->top());
      _igvn.replace_input_of(use_clone, LoopNode::LoopBackControl, C->top());
    }
  }

  // Step 4: update dominator tree and dominator depth

  set_idom(head, orig_tail_clone, dd);
  recompute_dom_depth();

  // Inhibit more partial peeling on this loop
  new_head_clone->set_partial_peel_loop();
  C->set_major_progress();
  loop->record_for_igvn();

#ifndef PRODUCT
  if (TracePartialPeeling) {
    tty->print_cr("\nafter partial peel one iteration");
    Node_List wl;
    Node* t = last_peel;
    while (true) {
      wl.push(t);
      if (t == head_clone) break;
      t = idom(t);
    }
    while (wl.size() > 0) {
      Node* tt = wl.pop();
      if (tt == head) tty->print_cr("orig head");
      else if (tt == new_head_clone) tty->print_cr("new head");
      else if (tt == head_clone) tty->print_cr("clone head");
      tt->dump();
    }
  }
#endif

  C->print_method(PHASE_AFTER_PARTIAL_PEELING, 4, new_head_clone);

  return true;
}

// Transform:
//
// loop<-----------------+
//  |                    |
// stmt1 stmt2 .. stmtn  |
//  |     |        |     |
//  \     |       /      |
//    v   v     v        |
//       region          |
//         |             |
//     shared_stmt       |
//         |             |
//         v             |
//         if            |
//         / \           |
//        |   -----------+
//        v
//
// into:
//
//    loop<-------------------+
//     |                      |
//     v                      |
// +->loop                    |
// |   |                      |
// |  stmt1 stmt2 .. stmtn    |
// |   |     |        |       |
// |   |      \       /       |
// |   |       v     v        |
// |   |        region1       |
// |   |           |          |
// |  shared_stmt shared_stmt |
// |   |           |          |
// |   v           v          |
// |   if          if         |
// |   /\          / \        |
// +--   |         |   -------+
//       \         /
//        v       v
//         region2
//
// (region2 is shown to merge mirrored projections of the loop exit
// ifs to make the diagram clearer but they really merge the same
// projection)
//
// Conditions for this transformation to trigger:
// - the path through stmt1 is frequent enough
// - the inner loop will be turned into a counted loop after transformation
bool PhaseIdealLoop::duplicate_loop_backedge(IdealLoopTree *loop, Node_List &old_new) {
  if (!DuplicateBackedge) {
    return false;
  }
  assert(!loop->_head->is_CountedLoop() || StressDuplicateBackedge, "Non-counted loop only");
  if (!loop->_head->is_Loop()) {
    return false;
  }

  uint estimate = loop->est_loop_clone_sz(1);
  if (exceeding_node_budget(estimate)) {
    return false;
  }

  LoopNode *head = loop->_head->as_Loop();

  Node* region = nullptr;
  IfNode* exit_test = nullptr;
  uint inner;
  float f;
  if (StressDuplicateBackedge) {
    if (head->is_strip_mined()) {
      return false;
    }
    Node* c = head->in(LoopNode::LoopBackControl);

    while (c != head) {
      if (c->is_Region()) {
        region = c;
      }
      c = idom(c);
    }

    if (region == nullptr) {
      return false;
    }

    inner = 1;
  } else {
    // Is the shape of the loop that of a counted loop...
    Node* back_control = loop_exit_control(head, loop);
    if (back_control == nullptr) {
      return false;
    }

    BoolTest::mask bt = BoolTest::illegal;
    float cl_prob = 0;
    Node* incr = nullptr;
    Node* limit = nullptr;
    Node* cmp = loop_exit_test(back_control, loop, incr, limit, bt, cl_prob);
    if (cmp == nullptr || cmp->Opcode() != Op_CmpI) {
      return false;
    }

    // With an extra phi for the candidate iv?
    // Or the region node is the loop head
    if (!incr->is_Phi() || incr->in(0) == head) {
      return false;
    }

    PathFrequency pf(head, this);
    region = incr->in(0);

    // Go over all paths for the extra phi's region and see if that
    // path is frequent enough and would match the expected iv shape
    // if the extra phi is removed
    inner = 0;
    for (uint i = 1; i < incr->req(); ++i) {
      Node* in = incr->in(i);
      Node* trunc1 = nullptr;
      Node* trunc2 = nullptr;
      const TypeInteger* iv_trunc_t = nullptr;
      Node* orig_in = in;
      if (!(in = CountedLoopNode::match_incr_with_optional_truncation(in, &trunc1, &trunc2, &iv_trunc_t, T_INT))) {
        continue;
      }
      assert(in->Opcode() == Op_AddI, "wrong increment code");
      Node* xphi = nullptr;
      Node* stride = loop_iv_stride(in, loop, xphi);

      if (stride == nullptr) {
        continue;
      }

      PhiNode* phi = loop_iv_phi(xphi, nullptr, head, loop);
      if (phi == nullptr ||
          (trunc1 == nullptr && phi->in(LoopNode::LoopBackControl) != incr) ||
          (trunc1 != nullptr && phi->in(LoopNode::LoopBackControl) != trunc1)) {
        return false;
      }

      f = pf.to(region->in(i));
      if (f > 0.5) {
        inner = i;
        break;
      }
    }

    if (inner == 0) {
      return false;
    }

    exit_test = back_control->in(0)->as_If();
  }

  if (idom(region)->is_Catch()) {
    return false;
  }

  // Collect all control nodes that need to be cloned (shared_stmt in the diagram)
  Unique_Node_List wq;
  wq.push(head->in(LoopNode::LoopBackControl));
  for (uint i = 0; i < wq.size(); i++) {
    Node* c = wq.at(i);
    assert(get_loop(c) == loop, "not in the right loop?");
    if (c->is_Region()) {
      if (c != region) {
        for (uint j = 1; j < c->req(); ++j) {
          wq.push(c->in(j));
        }
      }
    } else {
      wq.push(c->in(0));
    }
    assert(!is_dominator(c, region) || c == region, "shouldn't go above region");
  }

  Node* region_dom = idom(region);

  // Can't do the transformation if this would cause a membar pair to
  // be split
  for (uint i = 0; i < wq.size(); i++) {
    Node* c = wq.at(i);
    if (c->is_MemBar() && (c->as_MemBar()->trailing_store() || c->as_MemBar()->trailing_load_store())) {
      assert(c->as_MemBar()->leading_membar()->trailing_membar() == c, "bad membar pair");
      if (!wq.member(c->as_MemBar()->leading_membar())) {
        return false;
      }
    }
  }

  // Collect data nodes that need to be clones as well
  int dd = dom_depth(head);

  for (uint i = 0; i < loop->_body.size(); ++i) {
    Node* n = loop->_body.at(i);
    if (has_ctrl(n)) {
      Node* c = get_ctrl(n);
      if (wq.member(c)) {
        wq.push(n);
      }
    } else {
      set_idom(n, idom(n), dd);
    }
  }

  // clone shared_stmt
  clone_loop_body(wq, old_new, nullptr);

  Node* region_clone = old_new[region->_idx];
  region_clone->set_req(inner, C->top());
  set_idom(region, region->in(inner), dd);

  // Prepare the outer loop
  Node* outer_head = new LoopNode(head->in(LoopNode::EntryControl), old_new[head->in(LoopNode::LoopBackControl)->_idx]);
  register_control(outer_head, loop->_parent, outer_head->in(LoopNode::EntryControl));
  _igvn.replace_input_of(head, LoopNode::EntryControl, outer_head);
  set_idom(head, outer_head, dd);

  fix_body_edges(wq, loop, old_new, dd, loop->_parent, true);

  // Make one of the shared_stmt copies only reachable from stmt1, the
  // other only from stmt2..stmtn.
  Node* dom = nullptr;
  for (uint i = 1; i < region->req(); ++i) {
    if (i != inner) {
      _igvn.replace_input_of(region, i, C->top());
    }
    Node* in = region_clone->in(i);
    if (in->is_top()) {
      continue;
    }
    if (dom == nullptr) {
      dom = in;
    } else {
      dom = dom_lca(dom, in);
    }
  }

  set_idom(region_clone, dom, dd);

  // Set up the outer loop
  for (uint i = 0; i < head->outcnt(); i++) {
    Node* u = head->raw_out(i);
    if (u->is_Phi()) {
      Node* outer_phi = u->clone();
      outer_phi->set_req(0, outer_head);
      Node* backedge = old_new[u->in(LoopNode::LoopBackControl)->_idx];
      if (backedge == nullptr) {
        backedge = u->in(LoopNode::LoopBackControl);
      }
      outer_phi->set_req(LoopNode::LoopBackControl, backedge);
      register_new_node(outer_phi, outer_head);
      _igvn.replace_input_of(u, LoopNode::EntryControl, outer_phi);
    }
  }

  // create control and data nodes for out of loop uses (including region2)
  Node_List worklist;
  uint new_counter = C->unique();
  fix_ctrl_uses(wq, loop, old_new, ControlAroundStripMined, outer_head, nullptr, worklist);

  Node_List *split_if_set = nullptr;
  Node_List *split_bool_set = nullptr;
  Node_List *split_cex_set = nullptr;
  fix_data_uses(wq, loop, ControlAroundStripMined, head->is_strip_mined() ? loop->_parent : loop, new_counter, old_new, worklist, split_if_set, split_bool_set, split_cex_set);

  finish_clone_loop(split_if_set, split_bool_set, split_cex_set);

  if (exit_test != nullptr) {
    float cnt = exit_test->_fcnt;
    if (cnt != COUNT_UNKNOWN) {
      exit_test->_fcnt = cnt * f;
      old_new[exit_test->_idx]->as_If()->_fcnt = cnt * (1 - f);
    }
  }

  C->set_major_progress();

  return true;
}

// Having ReductionNodes in the loop is expensive. They need to recursively
// fold together the vector values, for every vectorized loop iteration. If
// we encounter the following pattern, we can vector accumulate the values
// inside the loop, and only have a single UnorderedReduction after the loop.
//
// CountedLoop     init
//          |        |
//          +------+ | +-----------------------+
//                 | | |                       |
//                PhiNode (s)                  |
//                  |                          |
//                  |          Vector          |
//                  |            |             |
//               UnorderedReduction (first_ur) |
//                  |                          |
//                 ...         Vector          |
//                  |            |             |
//               UnorderedReduction (last_ur)  |
//                       |                     |
//                       +---------------------+
//
// We patch the graph to look like this:
//
// CountedLoop   identity_vector
//         |         |
//         +-------+ | +---------------+
//                 | | |               |
//                PhiNode (v)          |
//                   |                 |
//                   |         Vector  |
//                   |           |     |
//                 VectorAccumulator   |
//                   |                 |
//                  ...        Vector  |
//                   |           |     |
//      init       VectorAccumulator   |
//        |          |     |           |
//     UnorderedReduction  +-----------+
//
// We turned the scalar (s) Phi into a vectorized one (v). In the loop, we
// use vector_accumulators, which do the same reductions, but only element
// wise. This is a single operation per vector_accumulator, rather than many
// for a UnorderedReduction. We can then reduce the last vector_accumulator
// after the loop, and also reduce the init value into it.
// We can not do this with all reductions. Some reductions do not allow the
// reordering of operations (for example float addition).
void PhaseIdealLoop::move_unordered_reduction_out_of_loop(IdealLoopTree* loop) {
  assert(!C->major_progress() && loop->is_counted() && loop->is_innermost(), "sanity");

  // Find all Phi nodes with UnorderedReduction on backedge.
  CountedLoopNode* cl = loop->_head->as_CountedLoop();
  for (DUIterator_Fast jmax, j = cl->fast_outs(jmax); j < jmax; j++) {
    Node* phi = cl->fast_out(j);
    // We have a phi with a single use, and a UnorderedReduction on the backedge.
    if (!phi->is_Phi() || phi->outcnt() != 1 || !phi->in(2)->is_UnorderedReduction()) {
      continue;
    }

    UnorderedReductionNode* last_ur = phi->in(2)->as_UnorderedReduction();

    // Determine types
    const TypeVect* vec_t = last_ur->vect_type();
    uint vector_length    = vec_t->length();
    BasicType bt          = vec_t->element_basic_type();
    const Type* bt_t      = Type::get_const_basic_type(bt);

    // Convert opcode from vector-reduction -> scalar -> normal-vector-op
    const int sopc        = VectorNode::scalar_opcode(last_ur->Opcode(), bt);
    const int vopc        = VectorNode::opcode(sopc, bt);
    if (!Matcher::match_rule_supported_vector(vopc, vector_length, bt)) {
        DEBUG_ONLY( last_ur->dump(); )
        assert(false, "do not have normal vector op for this reduction");
        continue; // not implemented -> fails
    }

    // Traverse up the chain of UnorderedReductions, checking that it loops back to
    // the phi. Check that all UnorderedReductions only have a single use, except for
    // the last (last_ur), which only has phi as a use in the loop, and all other uses
    // are outside the loop.
    UnorderedReductionNode* current = last_ur;
    UnorderedReductionNode* first_ur = nullptr;
    while (true) {
      assert(current->is_UnorderedReduction(), "sanity");

      // Expect no ctrl and a vector_input from within the loop.
      Node* ctrl = current->in(0);
      Node* vector_input = current->in(2);
      if (ctrl != nullptr || get_ctrl(vector_input) != cl) {
        DEBUG_ONLY( current->dump(1); )
        assert(false, "reduction has ctrl or bad vector_input");
        break; // Chain traversal fails.
      }

      assert(current->vect_type() != nullptr, "must have vector type");
      if (current->vect_type() != last_ur->vect_type()) {
        // Reductions do not have the same vector type (length and element type).
        break; // Chain traversal fails.
      }

      // Expect single use of UnorderedReduction, except for last_ur.
      if (current == last_ur) {
        // Expect all uses to be outside the loop, except phi.
        for (DUIterator_Fast kmax, k = current->fast_outs(kmax); k < kmax; k++) {
          Node* use = current->fast_out(k);
          if (use != phi && ctrl_or_self(use) == cl) {
            DEBUG_ONLY( current->dump(-1); )
            assert(false, "reduction has use inside loop");
            // Should not be allowed by SuperWord::mark_reductions
            return; // bail out of optimization
          }
        }
      } else {
        if (current->outcnt() != 1) {
          break; // Chain traversal fails.
        }
      }

      // Expect another UnorderedReduction or phi as the scalar input.
      Node* scalar_input = current->in(1);
      if (scalar_input->is_UnorderedReduction() &&
          scalar_input->Opcode() == current->Opcode()) {
        // Move up the UnorderedReduction chain.
        current = scalar_input->as_UnorderedReduction();
      } else if (scalar_input == phi) {
        // Chain terminates at phi.
        first_ur = current;
        current = nullptr;
        break; // Success.
      } else {
        // scalar_input is neither phi nor a matching reduction
        // Can for example be scalar reduction when we have
        // partial vectorization.
        break; // Chain traversal fails.
      }
    }
    if (current != nullptr) {
      // Chain traversal was not successful.
      continue;
    }
    assert(first_ur != nullptr, "must have successfully terminated chain traversal");

    Node* identity_scalar = ReductionNode::make_identity_con_scalar(_igvn, sopc, bt);
    set_ctrl(identity_scalar, C->root());
    VectorNode* identity_vector = VectorNode::scalar2vector(identity_scalar, vector_length, bt_t);
    register_new_node(identity_vector, C->root());
    assert(vec_t == identity_vector->vect_type(), "matching vector type");
    VectorNode::trace_new_vector(identity_vector, "UnorderedReduction");

    // Turn the scalar phi into a vector phi.
    _igvn.rehash_node_delayed(phi);
    Node* init = phi->in(1); // Remember init before replacing it.
    phi->set_req_X(1, identity_vector, &_igvn);
    phi->as_Type()->set_type(vec_t);
    _igvn.set_type(phi, vec_t);

    // Traverse down the chain of UnorderedReductions, and replace them with vector_accumulators.
    current = first_ur;
    while (true) {
      // Create vector_accumulator to replace current.
      Node* last_vector_accumulator = current->in(1);
      Node* vector_input            = current->in(2);
      VectorNode* vector_accumulator = VectorNode::make(vopc, last_vector_accumulator, vector_input, vec_t);
      register_new_node(vector_accumulator, cl);
      _igvn.replace_node(current, vector_accumulator);
      VectorNode::trace_new_vector(vector_accumulator, "UnorderedReduction");
      if (current == last_ur) {
        break;
      }
      current = vector_accumulator->unique_out()->as_UnorderedReduction();
    }

    // Create post-loop reduction.
    Node* last_accumulator = phi->in(2);
    Node* post_loop_reduction = ReductionNode::make(sopc, nullptr, init, last_accumulator, bt);

    // Take over uses of last_accumulator that are not in the loop.
    for (DUIterator i = last_accumulator->outs(); last_accumulator->has_out(i); i++) {
      Node* use = last_accumulator->out(i);
      if (use != phi && use != post_loop_reduction) {
        assert(ctrl_or_self(use) != cl, "use must be outside loop");
        use->replace_edge(last_accumulator, post_loop_reduction,  &_igvn);
        --i;
      }
    }
    register_new_node(post_loop_reduction, get_late_ctrl(post_loop_reduction, cl));
    VectorNode::trace_new_vector(post_loop_reduction, "UnorderedReduction");

    assert(last_accumulator->outcnt() == 2, "last_accumulator has 2 uses: phi and post_loop_reduction");
    assert(post_loop_reduction->outcnt() > 0, "should have taken over all non loop uses of last_accumulator");
    assert(phi->outcnt() == 1, "accumulator is the only use of phi");
  }
}
