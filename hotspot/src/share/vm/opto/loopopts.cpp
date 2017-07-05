/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/addnode.hpp"
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

//=============================================================================
//------------------------------split_thru_phi---------------------------------
// Split Node 'n' through merge point if there is enough win.
Node *PhaseIdealLoop::split_thru_phi( Node *n, Node *region, int policy ) {
  if (n->Opcode() == Op_ConvI2L && n->bottom_type() != TypeLong::LONG) {
    // ConvI2L may have type information on it which is unsafe to push up
    // so disable this for now
    return NULL;
  }

  int wins = 0;
  assert(!n->is_CFG(), "");
  assert(region->is_Region(), "");

  const Type* type = n->bottom_type();
  const TypeOopPtr *t_oop = _igvn.type(n)->isa_oopptr();
  Node *phi;
  if (t_oop != NULL && t_oop->is_known_instance_field()) {
    int iid    = t_oop->instance_id();
    int index  = C->get_alias_index(t_oop);
    int offset = t_oop->offset();
    phi = new PhiNode(region, type, NULL, iid, index, offset);
  } else {
    phi = PhiNode::make_blank(region, n);
  }
  uint old_unique = C->unique();
  for (uint i = 1; i < region->req(); i++) {
    Node *x;
    Node* the_clone = NULL;
    if (region->in(i) == C->top()) {
      x = C->top();             // Dead path?  Use a dead data op
    } else {
      x = n->clone();           // Else clone up the data op
      the_clone = x;            // Remember for possible deletion.
      // Alter data node to use pre-phi inputs
      if (n->in(0) == region)
        x->set_req( 0, region->in(i) );
      for (uint j = 1; j < n->req(); j++) {
        Node *in = n->in(j);
        if (in->is_Phi() && in->in(0) == region)
          x->set_req( j, in->in(i) ); // Use pre-Phi input for the clone
      }
    }
    // Check for a 'win' on some paths
    const Type *t = x->Value(&_igvn);

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
      Node *y = x->Identity(&_igvn);
      if (y != x) {
        wins++;
        x = y;
      } else {
        y = _igvn.hash_find(x);
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
    if (x != the_clone && the_clone != NULL)
      _igvn.remove_dead_node(the_clone);
    phi->set_req( i, x );
  }
  // Too few wins?
  if (wins <= policy) {
    _igvn.remove_dead_node(phi);
    return NULL;
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
      old_ctrl = NULL;
      old_loop = NULL;               // Not in any prior loop
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
        (old_loop == NULL || !new_loop->is_member(old_loop))) {
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

//------------------------------dominated_by------------------------------------
// Replace the dominated test with an obvious true or false.  Place it on the
// IGVN worklist for later cleanup.  Move control-dependent data Nodes on the
// live path up to the dominating control.
void PhaseIdealLoop::dominated_by( Node *prevdom, Node *iff, bool flip, bool exclude_loop_predicate ) {
  if (VerifyLoopOptimizations && PrintOpto) { tty->print_cr("dominating test"); }

  // prevdom is the dominating projection of the dominating test.
  assert( iff->is_If(), "" );
  assert(iff->Opcode() == Op_If || iff->Opcode() == Op_CountedLoopEnd || iff->Opcode() == Op_RangeCheck, "Check this code when new subtype is added");
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
  Node* dp = iff->as_If()->proj_out(pop == Op_IfTrue);

  // Loop predicates may have depending checks which should not
  // be skipped. For example, range check predicate has two checks
  // for lower and upper bounds.
  if (dp == NULL)
    return;

  ProjNode* dp_proj  = dp->as_Proj();
  ProjNode* unc_proj = iff->as_If()->proj_out(1 - dp_proj->_con)->as_Proj();
  if (exclude_loop_predicate &&
      (unc_proj->is_uncommon_trap_proj(Deoptimization::Reason_predicate) != NULL ||
       unc_proj->is_uncommon_trap_proj(Deoptimization::Reason_range_check) != NULL)) {
    // If this is a range check (IfNode::is_range_check), do not
    // reorder because Compile::allow_range_check_smearing might have
    // changed the check.
    return; // Let IGVN transformation change control dependence.
  }

  IdealLoopTree *old_loop = get_loop(dp);

  for (DUIterator_Fast imax, i = dp->fast_outs(imax); i < imax; i++) {
    Node* cd = dp->fast_out(i); // Control-dependent node
    if (cd->depends_only_on_test()) {
      assert(cd->in(0) == dp, "");
      _igvn.replace_input_of(cd, 0, prevdom);
      set_early_ctrl(cd);
      IdealLoopTree *new_loop = get_loop(get_ctrl(cd));
      if (old_loop != new_loop) {
        if (!old_loop->_child) old_loop->_body.yank(cd);
        if (!new_loop->_child) new_loop->_body.push(cd);
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
    return NULL;                // No Phi inputs; nowhere to clone thru

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
          get_ctrl(m->in(2)) != n_ctrl &&
          get_ctrl(m->in(3)) != n_ctrl) {
        // Move the AddP up to dominating point
        set_ctrl_and_loop(m, find_non_split_ctrl(idom(n_ctrl)));
        continue;
      }
      return NULL;
    }
  }

  return n_ctrl;
}

//------------------------------remix_address_expressions----------------------
// Rework addressing expressions to get the most loop-invariant stuff
// moved out.  We'd like to do all associative operators, but it's especially
// important (common) to do address expressions.
Node *PhaseIdealLoop::remix_address_expressions( Node *n ) {
  if (!has_ctrl(n))  return NULL;
  Node *n_ctrl = get_ctrl(n);
  IdealLoopTree *n_loop = get_loop(n_ctrl);

  // See if 'n' mixes loop-varying and loop-invariant inputs and
  // itself is loop-varying.

  // Only interested in binary ops (and AddP)
  if( n->req() < 3 || n->req() > 4 ) return NULL;

  Node *n1_ctrl = get_ctrl(n->in(                    1));
  Node *n2_ctrl = get_ctrl(n->in(                    2));
  Node *n3_ctrl = get_ctrl(n->in(n->req() == 3 ? 2 : 3));
  IdealLoopTree *n1_loop = get_loop( n1_ctrl );
  IdealLoopTree *n2_loop = get_loop( n2_ctrl );
  IdealLoopTree *n3_loop = get_loop( n3_ctrl );

  // Does one of my inputs spin in a tighter loop than self?
  if( (n_loop->is_member( n1_loop ) && n_loop != n1_loop) ||
      (n_loop->is_member( n2_loop ) && n_loop != n2_loop) ||
      (n_loop->is_member( n3_loop ) && n_loop != n3_loop) )
    return NULL;                // Leave well enough alone

  // Is at least one of my inputs loop-invariant?
  if( n1_loop == n_loop &&
      n2_loop == n_loop &&
      n3_loop == n_loop )
    return NULL;                // No loop-invariant inputs


  int n_op = n->Opcode();

  // Replace expressions like ((V+I) << 2) with (V<<2 + I<<2).
  if( n_op == Op_LShiftI ) {
    // Scale is loop invariant
    Node *scale = n->in(2);
    Node *scale_ctrl = get_ctrl(scale);
    IdealLoopTree *scale_loop = get_loop(scale_ctrl );
    if( n_loop == scale_loop || !scale_loop->is_member( n_loop ) )
      return NULL;
    const TypeInt *scale_t = scale->bottom_type()->isa_int();
    if( scale_t && scale_t->is_con() && scale_t->get_con() >= 16 )
      return NULL;              // Dont bother with byte/short masking
    // Add must vary with loop (else shift would be loop-invariant)
    Node *add = n->in(1);
    Node *add_ctrl = get_ctrl(add);
    IdealLoopTree *add_loop = get_loop(add_ctrl);
    //assert( n_loop == add_loop, "" );
    if( n_loop != add_loop ) return NULL;  // happens w/ evil ZKM loops

    // Convert I-V into I+ (0-V); same for V-I
    if( add->Opcode() == Op_SubI &&
        _igvn.type( add->in(1) ) != TypeInt::ZERO ) {
      Node *zero = _igvn.intcon(0);
      set_ctrl(zero, C->root());
      Node *neg = new SubINode( _igvn.intcon(0), add->in(2) );
      register_new_node( neg, get_ctrl(add->in(2) ) );
      add = new AddINode( add->in(1), neg );
      register_new_node( add, add_ctrl );
    }
    if( add->Opcode() != Op_AddI ) return NULL;
    // See if one add input is loop invariant
    Node *add_var = add->in(1);
    Node *add_var_ctrl = get_ctrl(add_var);
    IdealLoopTree *add_var_loop = get_loop(add_var_ctrl );
    Node *add_invar = add->in(2);
    Node *add_invar_ctrl = get_ctrl(add_invar);
    IdealLoopTree *add_invar_loop = get_loop(add_invar_ctrl );
    if( add_var_loop == n_loop ) {
    } else if( add_invar_loop == n_loop ) {
      // Swap to find the invariant part
      add_invar = add_var;
      add_invar_ctrl = add_var_ctrl;
      add_invar_loop = add_var_loop;
      add_var = add->in(2);
      Node *add_var_ctrl = get_ctrl(add_var);
      IdealLoopTree *add_var_loop = get_loop(add_var_ctrl );
    } else                      // Else neither input is loop invariant
      return NULL;
    if( n_loop == add_invar_loop || !add_invar_loop->is_member( n_loop ) )
      return NULL;              // No invariant part of the add?

    // Yes!  Reshape address expression!
    Node *inv_scale = new LShiftINode( add_invar, scale );
    Node *inv_scale_ctrl =
      dom_depth(add_invar_ctrl) > dom_depth(scale_ctrl) ?
      add_invar_ctrl : scale_ctrl;
    register_new_node( inv_scale, inv_scale_ctrl );
    Node *var_scale = new LShiftINode( add_var, scale );
    register_new_node( var_scale, n_ctrl );
    Node *var_add = new AddINode( var_scale, inv_scale );
    register_new_node( var_add, n_ctrl );
    _igvn.replace_node( n, var_add );
    return var_add;
  }

  // Replace (I+V) with (V+I)
  if( n_op == Op_AddI ||
      n_op == Op_AddL ||
      n_op == Op_AddF ||
      n_op == Op_AddD ||
      n_op == Op_MulI ||
      n_op == Op_MulL ||
      n_op == Op_MulF ||
      n_op == Op_MulD ) {
    if( n2_loop == n_loop ) {
      assert( n1_loop != n_loop, "" );
      n->swap_edges(1, 2);
    }
  }

  // Replace ((I1 +p V) +p I2) with ((I1 +p I2) +p V),
  // but not if I2 is a constant.
  if( n_op == Op_AddP ) {
    if( n2_loop == n_loop && n3_loop != n_loop ) {
      if( n->in(2)->Opcode() == Op_AddP && !n->in(3)->is_Con() ) {
        Node *n22_ctrl = get_ctrl(n->in(2)->in(2));
        Node *n23_ctrl = get_ctrl(n->in(2)->in(3));
        IdealLoopTree *n22loop = get_loop( n22_ctrl );
        IdealLoopTree *n23_loop = get_loop( n23_ctrl );
        if( n22loop != n_loop && n22loop->is_member(n_loop) &&
            n23_loop == n_loop ) {
          Node *add1 = new AddPNode( n->in(1), n->in(2)->in(2), n->in(3) );
          // Stuff new AddP in the loop preheader
          register_new_node( add1, n_loop->_head->in(LoopNode::EntryControl) );
          Node *add2 = new AddPNode( n->in(1), add1, n->in(2)->in(3) );
          register_new_node( add2, n_ctrl );
          _igvn.replace_node( n, add2 );
          return add2;
        }
      }
    }

    // Replace (I1 +p (I2 + V)) with ((I1 +p I2) +p V)
    if (n2_loop != n_loop && n3_loop == n_loop) {
      if (n->in(3)->Opcode() == Op_AddX) {
        Node *V = n->in(3)->in(1);
        Node *I = n->in(3)->in(2);
        if (is_member(n_loop,get_ctrl(V))) {
        } else {
          Node *tmp = V; V = I; I = tmp;
        }
        if (!is_member(n_loop,get_ctrl(I))) {
          Node *add1 = new AddPNode(n->in(1), n->in(2), I);
          // Stuff new AddP in the loop preheader
          register_new_node(add1, n_loop->_head->in(LoopNode::EntryControl));
          Node *add2 = new AddPNode(n->in(1), add1, V);
          register_new_node(add2, n_ctrl);
          _igvn.replace_node(n, add2);
          return add2;
        }
      }
    }
  }

  return NULL;
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
  if (region->req() != 3) return NULL;

  // Check for CFG diamond
  Node *lp = region->in(1);
  Node *rp = region->in(2);
  if (!lp || !rp) return NULL;
  Node *lp_c = lp->in(0);
  if (lp_c == NULL || lp_c != rp->in(0) || !lp_c->is_If()) return NULL;
  IfNode *iff = lp_c->as_If();

  // Check for ops pinned in an arm of the diamond.
  // Can't remove the control flow in this case
  if (lp->outcnt() > 1) return NULL;
  if (rp->outcnt() > 1) return NULL;

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
      if (C->use_cmove()) {
        continue; //TODO: maybe we want to add some cost
      }
    case T_FLOAT: {
      cost += Matcher::float_cmove_cost(); // Could be very expensive
      break;
    }
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
        return NULL;
      cost++;
      break;
    }
    default:
      return NULL;              // In particular, can't do memory or I/O
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
  assert(bol->Opcode() == Op_Bool, "");
  int cmp_op = bol->in(1)->Opcode();
  // It is expensive to generate flags from a float compare.
  // Avoid duplicated float compare.
  if (phis > 1 && (cmp_op == Op_CmpF || cmp_op == Op_CmpD)) return NULL;

  float infrequent_prob = PROB_UNLIKELY_MAG(3);
  // Ignore cost and blocks frequency if CMOVE can be moved outside the loop.
  if (used_inside_loop) {
    if (cost >= ConditionalMoveLimit) return NULL; // Too much goo

    // BlockLayoutByFrequency optimization moves infrequent branch
    // from hot path. No point in CMOV'ing in such case (110 is used
    // instead of 100 to take into account not exactness of float value).
    if (BlockLayoutByFrequency) {
      infrequent_prob = MAX2(infrequent_prob, (float)BlockLayoutMinDiamondPercentage/110.0f);
    }
  }
  // Check for highly predictable branch.  No point in CMOV'ing if
  // we are going to predict accurately all the time.
  if (C->use_cmove() && cmp_op == Op_CmpD) ;//keep going
  else if (iff->_prob < infrequent_prob ||
      iff->_prob > (1.0f - infrequent_prob))
    return NULL;

  // --------------
  // Now replace all Phis with CMOV's
  Node *cmov_ctrl = iff->in(0);
  uint flip = (lp->Opcode() == Op_IfTrue);
  while (1) {
    PhiNode* phi = NULL;
    for (DUIterator_Fast imax, i = region->fast_outs(imax); i < imax; i++) {
      Node *out = region->fast_out(i);
      if (out->is_Phi()) {
        phi = out->as_Phi();
        break;
      }
    }
    if (phi == NULL)  break;
    if (PrintOpto && VerifyLoopOptimizations) { tty->print_cr("CMOV"); }
    // Move speculative ops
    for (uint j = 1; j < region->req(); j++) {
      Node *proj = region->in(j);
      Node *inp = phi->in(j);
      if (get_ctrl(inp) == proj) { // Found local op
#ifndef PRODUCT
        if (PrintOpto && VerifyLoopOptimizations) {
          tty->print("  speculate: ");
          inp->dump();
        }
#endif
        set_ctrl(inp, cmov_ctrl);
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
    if (VerifyLoopOptimizations) verify();
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
      if (u->Opcode() == Op_NeverBranch) {
        u = ((NeverBranchNode*)u)->proj_out(0);
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
  if (n->is_Store() && n_loop != _ltree_root && n_loop->is_loop() && n->in(0) != NULL) {
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

      assert(n_loop->_tail != NULL, "need a tail");
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
        _igvn.replace_input_of(n, 0, n_loop->_head->in(LoopNode::EntryControl));
        _igvn.replace_input_of(n, MemNode::Memory, mem->in(LoopNode::EntryControl));
        // Disconnect the phi now. An empty phi can confuse other
        // optimizations in this pass of loop opts.
        _igvn.replace_node(mem, mem->in(LoopNode::EntryControl));
        n_loop->_body.yank(mem);

        IdealLoopTree* new_loop = get_loop(n->in(0));
        set_ctrl_and_loop(n, n->in(0));

        return n;
      }
    }
  }
  return NULL;
}

// Try moving a store out of a loop, right after the loop
void PhaseIdealLoop::try_move_store_after_loop(Node* n) {
  if (n->is_Store() && n->in(0) != NULL) {
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
        Node* phi = NULL;
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
              if (phi != NULL) {
                return;
              }
              phi = u;
              continue;
            }
          }
          return;
        }
        if (phi != NULL) {
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
            // Move the Store out of the loop creating clones along
            // all paths out of the loop that observe the stored value
            _igvn.rehash_node_delayed(phi);
            int count = phi->replace_edge(n, n->in(MemNode::Memory));
            assert(count > 0, "inconsistent phi");
            for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
              Node* u = n->fast_out(i);
              Node* c = get_ctrl(u);

              if (u->is_Phi()) {
                c = u->in(0)->in(u->find_edge(n));
              }
              IdealLoopTree *u_loop = get_loop(c);
              assert (!n_loop->is_member(u_loop), "only the phi should have been a use in the loop");
              while(true) {
                Node* next_c = find_non_split_ctrl(idom(c));
                if (n_loop->is_member(get_loop(next_c))) {
                  break;
                }
                c = next_c;
              }

              Node* st = n->clone();
              st->set_req(0, c);
              _igvn.register_new_node_with_optimizer(st);

              set_ctrl(st, c);
              IdealLoopTree* new_loop = get_loop(c);
              assert(new_loop != n_loop, "should be moved out of loop");
              if (new_loop->_child == NULL) new_loop->_body.push(st);

              _igvn.replace_input_of(u, u->find_edge(n), st);
              --imax;
              --i;
            }


            assert(n->outcnt() == 0, "all uses should be gone");
            _igvn.replace_input_of(n, MemNode::Memory, C->top());
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
  if( n_op == Op_MergeMem ) return n;
  if( n->is_Proj() ) return n;
  // Do not clone-up CmpFXXX variations, as these are always
  // followed by a CmpI
  if( n->is_Cmp() ) return n;
  // Attempt to use a conditional move instead of a phi/branch
  if( ConditionalMoveLimit > 0 && n_op == Op_Region ) {
    Node *cmov = conditional_move( n );
    if( cmov ) return cmov;
  }
  if( n->is_CFG() || n->is_LoadStore() )
    return n;
  if( n_op == Op_Opaque1 ||     // Opaque nodes cannot be mod'd
      n_op == Op_Opaque2 ) {
    if( !C->major_progress() )   // If chance of no more loop opts...
      _igvn._worklist.push(n);  // maybe we'll remove them
    return n;
  }

  if( n->is_Con() ) return n;   // No cloning for Con nodes

  Node *n_ctrl = get_ctrl(n);
  if( !n_ctrl ) return n;       // Dead node

  Node* res = try_move_store_before_loop(n, n_ctrl);
  if (res != NULL) {
    return n;
  }

  // Attempt to remix address expressions for loop invariants
  Node *m = remix_address_expressions( n );
  if( m ) return m;

  if (n->is_ConstraintCast()) {
    Node* dom_cast = n->as_ConstraintCast()->dominating_cast(this);
    if (dom_cast != NULL) {
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
  if( n_blk->is_CountedLoop() && n->Opcode() == Op_AddI ) return n;

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
  if (n_blk->is_CountedLoop()) {
    IdealLoopTree *lp = get_loop(n_blk);
    if (lp && lp->_rce_candidate) {
      return n;
    }
  }

  // Use same limit as split_if_with_blocks_post
  if( C->live_nodes() > 35000 ) return n; // Method too big

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
  // 6855164: If the merge point has a FastLockNode with a PhiNode input, we stop
  // split_if_with_blocks from splitting a block because we could not move around
  // the FastLockNode.
  for (DUIterator_Fast imax, i = region->fast_outs(imax); i < imax; i++) {
    Node* n = region->fast_out(i);
    if (n->is_Phi()) {
      for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
        Node* m = n->fast_out(j);
        if (m->is_FastLock())
          return false;
#ifdef _LP64
        if (m->Opcode() == Op_ConvI2L)
          return false;
        if (m->is_CastII() && m->isa_CastII()->has_range_check()) {
          return false;
        }
#endif
      }
    }
  }
  return true;
}


//------------------------------place_near_use---------------------------------
// Place some computation next to use but not inside inner loops.
// For inner loop uses move it to the preheader area.
Node *PhaseIdealLoop::place_near_use( Node *useblock ) const {
  IdealLoopTree *u_loop = get_loop( useblock );
  return (u_loop->_irreducible || u_loop->_child)
    ? useblock
    : u_loop->_head->in(LoopNode::EntryControl);
}


//------------------------------split_if_with_blocks_post----------------------
// Do the real work in a non-recursive function.  CFG hackery wants to be
// in the post-order, so it can dirty the I-DOM info and not use the dirtied
// info.
void PhaseIdealLoop::split_if_with_blocks_post( Node *n ) {

  // Cloning Cmp through Phi's involves the split-if transform.
  // FastLock is not used by an If
  if( n->is_Cmp() && !n->is_FastLock() ) {
    if( C->live_nodes() > 35000 ) return; // Method too big

    // Do not do 'split-if' if irreducible loops are present.
    if( _has_irreducible_loops )
      return;

    Node *n_ctrl = get_ctrl(n);
    // Determine if the Node has inputs from some local Phi.
    // Returns the block to clone thru.
    Node *n_blk = has_local_phi_input( n );
    if( n_blk != n_ctrl ) return;

    if( merge_point_too_heavy(C, n_ctrl) )
      return;

    if( n->outcnt() != 1 ) return; // Multiple bool's from 1 compare?
    Node *bol = n->unique_out();
    assert( bol->is_Bool(), "expect a bool here" );
    if( bol->outcnt() != 1 ) return;// Multiple branches from 1 compare?
    Node *iff = bol->unique_out();

    // Check some safety conditions
    if( iff->is_If() ) {        // Classic split-if?
      if( iff->in(0) != n_ctrl ) return; // Compare must be in same blk as if
    } else if (iff->is_CMove()) { // Trying to split-up a CMOVE
      // Can't split CMove with different control edge.
      if (iff->in(0) != NULL && iff->in(0) != n_ctrl ) return;
      if( get_ctrl(iff->in(2)) == n_ctrl ||
          get_ctrl(iff->in(3)) == n_ctrl )
        return;                 // Inputs not yet split-up
      if ( get_loop(n_ctrl) != get_loop(get_ctrl(iff)) ) {
        return;                 // Loop-invar test gates loop-varying CMOVE
      }
    } else {
      return;  // some other kind of node, such as an Allocate
    }

    // Do not do 'split-if' if some paths are dead.  First do dead code
    // elimination and then see if its still profitable.
    for( uint i = 1; i < n_ctrl->req(); i++ )
      if( n_ctrl->in(i) == C->top() )
        return;

    // When is split-if profitable?  Every 'win' on means some control flow
    // goes dead, so it's almost always a win.
    int policy = 0;
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
    for( uint j = 1; j < n_ctrl->req(); j++ )
      if( get_loop(n_ctrl->in(j)) != n_loop )
        return;

    // Check for safety of the merge point.
    if( !merge_point_safe(n_ctrl) ) {
      return;
    }

    // Split compare 'n' through the merge point if it is profitable
    Node *phi = split_thru_phi( n, n_ctrl, policy );
    if( !phi ) return;

    // Found a Phi to split thru!
    // Replace 'n' with the new phi
    _igvn.replace_node( n, phi );

    // Now split the bool up thru the phi
    Node *bolphi = split_thru_phi( bol, n_ctrl, -1 );
    guarantee(bolphi != NULL, "null boolean phi node");

    _igvn.replace_node( bol, bolphi );
    assert( iff->in(1) == bolphi, "" );

    if( bolphi->Value(&_igvn)->singleton() )
      return;

    // Conditional-move?  Must split up now
    if( !iff->is_If() ) {
      Node *cmovphi = split_thru_phi( iff, n_ctrl, -1 );
      _igvn.replace_node( iff, cmovphi );
      return;
    }

    // Now split the IF
    do_split_if( iff );
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
    if (max > 1 && bol->is_Bool()) {
      // Search up IDOMs to see if this IF is dominated.
      Node *cutoff = get_ctrl(bol);

      // Now search up IDOMs till cutoff, looking for a dominating test
      Node *prevdom = n;
      Node *dom = idom(prevdom);
      while (dom != cutoff) {
        if (dom->req() > 1 && dom->in(1) == bol && prevdom->in(0) == dom) {
          // Replace the dominated test with an obvious true or false.
          // Place it on the IGVN worklist for later cleanup.
          C->set_major_progress();
          dominated_by(prevdom, n, false, true);
#ifndef PRODUCT
          if( VerifyLoopOptimizations ) verify();
#endif
          return;
        }
        prevdom = dom;
        dom = idom(prevdom);
      }
    }
  }

  // See if a shared loop-varying computation has no loop-varying uses.
  // Happens if something is only used for JVM state in uncommon trap exits,
  // like various versions of induction variable+offset.  Clone the
  // computation per usage to allow it to sink out of the loop.
  if (has_ctrl(n) && !n->in(0)) {// n not dead and has no control edge (can float about)
    Node *n_ctrl = get_ctrl(n);
    IdealLoopTree *n_loop = get_loop(n_ctrl);
    if( n_loop != _ltree_root ) {
      DUIterator_Fast imax, i = n->fast_outs(imax);
      for (; i < imax; i++) {
        Node* u = n->fast_out(i);
        if( !has_ctrl(u) )     break; // Found control user
        IdealLoopTree *u_loop = get_loop(get_ctrl(u));
        if( u_loop == n_loop ) break; // Found loop-varying use
        if( n_loop->is_member( u_loop ) ) break; // Found use in inner loop
        if( u->Opcode() == Op_Opaque1 ) break; // Found loop limit, bugfix for 4677003
      }
      bool did_break = (i < imax);  // Did we break out of the previous loop?
      if (!did_break && n->outcnt() > 1) { // All uses in outer loops!
        Node *late_load_ctrl = NULL;
        if (n->is_Load()) {
          // If n is a load, get and save the result from get_late_ctrl(),
          // to be later used in calculating the control for n's clones.
          clear_dom_lca_tags();
          late_load_ctrl = get_late_ctrl(n, n_ctrl);
        }
        // If n is a load, and the late control is the same as the current
        // control, then the cloning of n is a pointless exercise, because
        // GVN will ensure that we end up where we started.
        if (!n->is_Load() || late_load_ctrl != n_ctrl) {
          for (DUIterator_Last jmin, j = n->last_outs(jmin); j >= jmin; ) {
            Node *u = n->last_out(j); // Clone private computation per use
            _igvn.rehash_node_delayed(u);
            Node *x = n->clone(); // Clone computation
            Node *x_ctrl = NULL;
            if( u->is_Phi() ) {
              // Replace all uses of normal nodes.  Replace Phi uses
              // individually, so the separate Nodes can sink down
              // different paths.
              uint k = 1;
              while( u->in(k) != n ) k++;
              u->set_req( k, x );
              // x goes next to Phi input path
              x_ctrl = u->in(0)->in(k);
              --j;
            } else {              // Normal use
              // Replace all uses
              for( uint k = 0; k < u->req(); k++ ) {
                if( u->in(k) == n ) {
                  u->set_req( k, x );
                  --j;
                }
              }
              x_ctrl = get_ctrl(u);
            }

            // Find control for 'x' next to use but not inside inner loops.
            // For inner loop uses get the preheader area.
            x_ctrl = place_near_use(x_ctrl);

            if (n->is_Load()) {
              // For loads, add a control edge to a CFG node outside of the loop
              // to force them to not combine and return back inside the loop
              // during GVN optimization (4641526).
              //
              // Because we are setting the actual control input, factor in
              // the result from get_late_ctrl() so we respect any
              // anti-dependences. (6233005).
              x_ctrl = dom_lca(late_load_ctrl, x_ctrl);

              // Don't allow the control input to be a CFG splitting node.
              // Such nodes should only have ProjNodes as outs, e.g. IfNode
              // should only have IfTrueNode and IfFalseNode (4985384).
              x_ctrl = find_non_split_ctrl(x_ctrl);
              assert(dom_depth(n_ctrl) <= dom_depth(x_ctrl), "n is later than its clone");

              x->set_req(0, x_ctrl);
            }
            register_new_node(x, x_ctrl);

            // Some institutional knowledge is needed here: 'x' is
            // yanked because if the optimizer runs GVN on it all the
            // cloned x's will common up and undo this optimization and
            // be forced back in the loop.  This is annoying because it
            // makes +VerifyOpto report false-positives on progress.  I
            // tried setting control edges on the x's to force them to
            // not combine, but the matching gets worried when it tries
            // to fold a StoreP and an AddP together (as part of an
            // address expression) and the AddP and StoreP have
            // different controls.
            if (!x->is_Load() && !x->is_DecodeNarrowPtr()) _igvn._worklist.yank(x);
          }
          _igvn.remove_dead_node(n);
        }
      }
    }
  }

  try_move_store_after_loop(n);

  // Check for Opaque2's who's loop has disappeared - who's input is in the
  // same loop nest as their output.  Remove 'em, they are no longer useful.
  if( n_op == Op_Opaque2 &&
      n->in(1) != NULL &&
      get_loop(get_ctrl(n)) == get_loop(get_ctrl(n->in(1))) ) {
    _igvn.replace_node( n, n->in(1) );
  }
}

//------------------------------split_if_with_blocks---------------------------
// Check for aggressive application of 'split-if' optimization,
// using basic block level info.
void PhaseIdealLoop::split_if_with_blocks( VectorSet &visited, Node_Stack &nstack ) {
  Node *n = C->root();
  visited.set(n->_idx); // first, mark node as visited
  // Do pre-visit work for root
  n = split_if_with_blocks_pre( n );
  uint cnt = n->outcnt();
  uint i   = 0;
  while (true) {
    // Visit all children
    if (i < cnt) {
      Node* use = n->raw_out(i);
      ++i;
      if (use->outcnt() != 0 && !visited.test_set(use->_idx)) {
        // Now do pre-visit work for this use
        use = split_if_with_blocks_pre( use );
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
        split_if_with_blocks_post( n );
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
BoolNode *PhaseIdealLoop::clone_iff( PhiNode *phi, IdealLoopTree *loop ) {

  // Convert this Phi into a Phi merging Bools
  uint i;
  for( i = 1; i < phi->req(); i++ ) {
    Node *b = phi->in(i);
    if( b->is_Phi() ) {
      _igvn.replace_input_of(phi, i, clone_iff( b->as_Phi(), loop ));
    } else {
      assert( b->is_Bool(), "" );
    }
  }

  Node *sample_bool = phi->in(1);
  Node *sample_cmp  = sample_bool->in(1);

  // Make Phis to merge the Cmp's inputs.
  PhiNode *phi1 = new PhiNode( phi->in(0), Type::TOP );
  PhiNode *phi2 = new PhiNode( phi->in(0), Type::TOP );
  for( i = 1; i < phi->req(); i++ ) {
    Node *n1 = phi->in(i)->in(1)->in(1);
    Node *n2 = phi->in(i)->in(1)->in(2);
    phi1->set_req( i, n1 );
    phi2->set_req( i, n2 );
    phi1->set_type( phi1->type()->meet_speculative(n1->bottom_type()));
    phi2->set_type( phi2->type()->meet_speculative(n2->bottom_type()));
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

  // Make a new Bool
  Node *b = sample_bool->clone();
  b->set_req(1,cmp);
  _igvn.register_new_node_with_optimizer(b);
  set_ctrl(b, phi->in(0));

  assert( b->is_Bool(), "" );
  return (BoolNode*)b;
}

//------------------------------clone_bool-------------------------------------
// Passed in a Phi merging (recursively) some nearly equivalent Bool/Cmps.
// "Nearly" because all Nodes have been cloned from the original in the loop,
// but the fall-in edges to the Cmp are different.  Clone bool/Cmp pairs
// through the Phi recursively, and return a Bool.
CmpNode *PhaseIdealLoop::clone_bool( PhiNode *phi, IdealLoopTree *loop ) {
  uint i;
  // Convert this Phi into a Phi merging Bools
  for( i = 1; i < phi->req(); i++ ) {
    Node *b = phi->in(i);
    if( b->is_Phi() ) {
      _igvn.replace_input_of(phi, i, clone_bool( b->as_Phi(), loop ));
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

//------------------------------sink_use---------------------------------------
// If 'use' was in the loop-exit block, it now needs to be sunk
// below the post-loop merge point.
void PhaseIdealLoop::sink_use( Node *use, Node *post_loop ) {
  if (!use->is_CFG() && get_ctrl(use) == post_loop->in(2)) {
    set_ctrl(use, post_loop);
    for (DUIterator j = use->outs(); use->has_out(j); j++)
      sink_use(use->out(j), post_loop);
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
//   When side_by_size_idom is NULL, the dominator tree is constructed for
//      the clone loop to dominate the original.  Used in construction of
//      pre-main-post loop sequence.
//   When nonnull, the clone and original are side-by-side, both are
//      dominated by the side_by_side_idom node.  Used in construction of
//      unswitched loops.
void PhaseIdealLoop::clone_loop( IdealLoopTree *loop, Node_List &old_new, int dd,
                                 Node* side_by_side_idom) {

  if (C->do_vector_loop() && PrintOpto) {
    const char* mname = C->method()->name()->as_quoted_ascii();
    if (mname != NULL) {
      tty->print("PhaseIdealLoop::clone_loop: for vectorize method %s\n", mname);
    }
  }

  CloneMap& cm = C->clone_map();
  Dict* dict = cm.dict();
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
  uint i;
  for( i = 0; i < loop->_body.size(); i++ ) {
    Node *old = loop->_body.at(i);
    Node *nnn = old->clone();
    old_new.map( old->_idx, nnn );
    if (C->do_vector_loop()) {
      cm.verify_insert_and_clone(old, nnn, cm.clone_idx());
    }
    _igvn.register_new_node_with_optimizer(nnn);
  }


  // Step 2: Fix the edges in the new body.  If the old input is outside the
  // loop use it.  If the old input is INside the loop, use the corresponding
  // new node instead.
  for( i = 0; i < loop->_body.size(); i++ ) {
    Node *old = loop->_body.at(i);
    Node *nnn = old_new[old->_idx];
    // Fix CFG/Loop controlling the new node
    if (has_ctrl(old)) {
      set_ctrl(nnn, old_new[get_ctrl(old)->_idx]);
    } else {
      set_loop(nnn, loop->_parent);
      if (old->outcnt() > 0) {
        set_idom( nnn, old_new[idom(old)->_idx], dd );
      }
    }
    // Correct edges to the new node
    for( uint j = 0; j < nnn->req(); j++ ) {
        Node *n = nnn->in(j);
        if( n ) {
          IdealLoopTree *old_in_loop = get_loop( has_ctrl(n) ? get_ctrl(n) : n );
          if( loop->is_member( old_in_loop ) )
            nnn->set_req(j, old_new[n->_idx]);
        }
    }
    _igvn.hash_find_insert(nnn);
  }
  Node *newhead = old_new[loop->_head->_idx];
  set_idom(newhead, newhead->in(LoopNode::EntryControl), dd);


  // Step 3: Now fix control uses.  Loop varying control uses have already
  // been fixed up (as part of all input edges in Step 2).  Loop invariant
  // control uses must be either an IfFalse or an IfTrue.  Make a merge
  // point to merge the old and new IfFalse/IfTrue nodes; make the use
  // refer to this.
  ResourceArea *area = Thread::current()->resource_area();
  Node_List worklist(area);
  uint new_counter = C->unique();
  for( i = 0; i < loop->_body.size(); i++ ) {
    Node* old = loop->_body.at(i);
    if( !old->is_CFG() ) continue;
    Node* nnn = old_new[old->_idx];

    // Copy uses to a worklist, so I can munge the def-use info
    // with impunity.
    for (DUIterator_Fast jmax, j = old->fast_outs(jmax); j < jmax; j++)
      worklist.push(old->fast_out(j));

    while( worklist.size() ) {  // Visit all uses
      Node *use = worklist.pop();
      if (!has_node(use))  continue; // Ignore dead nodes
      IdealLoopTree *use_loop = get_loop( has_ctrl(use) ? get_ctrl(use) : use );
      if( !loop->is_member( use_loop ) && use->is_CFG() ) {
        // Both OLD and USE are CFG nodes here.
        assert( use->is_Proj(), "" );

        // Clone the loop exit control projection
        Node *newuse = use->clone();
        if (C->do_vector_loop()) {
          cm.verify_insert_and_clone(use, newuse, cm.clone_idx());
        }
        newuse->set_req(0,nnn);
        _igvn.register_new_node_with_optimizer(newuse);
        set_loop(newuse, use_loop);
        set_idom(newuse, nnn, dom_depth(nnn) + 1 );

        // We need a Region to merge the exit from the peeled body and the
        // exit from the old loop body.
        RegionNode *r = new RegionNode(3);
        // Map the old use to the new merge point
        old_new.map( use->_idx, r );
        uint dd_r = MIN2(dom_depth(newuse),dom_depth(use));
        assert( dd_r >= dom_depth(dom_lca(newuse,use)), "" );

        // The original user of 'use' uses 'r' instead.
        for (DUIterator_Last lmin, l = use->last_outs(lmin); l >= lmin;) {
          Node* useuse = use->last_out(l);
          _igvn.rehash_node_delayed(useuse);
          uint uses_found = 0;
          if( useuse->in(0) == use ) {
            useuse->set_req(0, r);
            uses_found++;
            if( useuse->is_CFG() ) {
              assert( dom_depth(useuse) > dd_r, "" );
              set_idom(useuse, r, dom_depth(useuse));
            }
          }
          for( uint k = 1; k < useuse->req(); k++ ) {
            if( useuse->in(k) == use ) {
              useuse->set_req(k, r);
              uses_found++;
            }
          }
          l -= uses_found;    // we deleted 1 or more copies of this edge
        }

        // Now finish up 'r'
        r->set_req( 1, newuse );
        r->set_req( 2,    use );
        _igvn.register_new_node_with_optimizer(r);
        set_loop(r, use_loop);
        set_idom(r, !side_by_side_idom ? newuse->in(0) : side_by_side_idom, dd_r);
      } // End of if a loop-exit test
    }
  }

  // Step 4: If loop-invariant use is not control, it must be dominated by a
  // loop exit IfFalse/IfTrue.  Find "proper" loop exit.  Make a Region
  // there if needed.  Make a Phi there merging old and new used values.
  Node_List *split_if_set = NULL;
  Node_List *split_bool_set = NULL;
  Node_List *split_cex_set = NULL;
  for( i = 0; i < loop->_body.size(); i++ ) {
    Node* old = loop->_body.at(i);
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
      if( !loop->is_member( use_loop ) && (!old->is_CFG() || !use->is_CFG())) {

        // If the Data use is an IF, that means we have an IF outside of the
        // loop that is switching on a condition that is set inside of the
        // loop.  Happens if people set a loop-exit flag; then test the flag
        // in the loop to break the loop, then test is again outside of the
        // loop to determine which way the loop exited.
        // Loop predicate If node connects to Bool node through Opaque1 node.
        if (use->is_If() || use->is_CMove() || C->is_predicate_opaq(use)) {
          // Since this code is highly unlikely, we lazily build the worklist
          // of such Nodes to go split.
          if( !split_if_set )
            split_if_set = new Node_List(area);
          split_if_set->push(use);
        }
        if( use->is_Bool() ) {
          if( !split_bool_set )
            split_bool_set = new Node_List(area);
          split_bool_set->push(use);
        }
        if( use->Opcode() == Op_CreateEx ) {
          if( !split_cex_set )
            split_cex_set = new Node_List(area);
          split_cex_set->push(use);
        }


        // Get "block" use is in
        uint idx = 0;
        while( use->in(idx) != old ) idx++;
        Node *prev = use->is_CFG() ? use : get_ctrl(use);
        assert( !loop->is_member( get_loop( prev ) ), "" );
        Node *cfg = prev->_idx >= new_counter
          ? prev->in(2)
          : idom(prev);
        if( use->is_Phi() )     // Phi use is in prior block
          cfg = prev->in(idx);  // NOT in block of Phi itself
        if (cfg->is_top()) {    // Use is dead?
          _igvn.replace_input_of(use, idx, C->top());
          continue;
        }

        while( !loop->is_member( get_loop( cfg ) ) ) {
          prev = cfg;
          cfg = cfg->_idx >= new_counter ? cfg->in(2) : idom(cfg);
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
          if( idx == 0 ) {      // Updating control edge?
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
          if( hit == NULL ) {
           _igvn.register_new_node_with_optimizer(phi); // Register new phi
          } else {                                      // or
            // Remove the new phi from the graph and use the hit
            _igvn.remove_dead_node(phi);
            phi = hit;                                  // Use existing phi
          }
          set_ctrl(phi, prev);
        }
        // Make 'use' use the Phi instead of the old loop body exit value
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

        // If 'use' was in the loop-exit block, it now needs to be sunk
        // below the post-loop merge point.
        sink_use( use, prev );
      }
    }
  }

  // Check for IFs that need splitting/cloning.  Happens if an IF outside of
  // the loop uses a condition set in the loop.  The original IF probably
  // takes control from one or more OLD Regions (which in turn get from NEW
  // Regions).  In any case, there will be a set of Phis for each merge point
  // from the IF up to where the original BOOL def exists the loop.
  if( split_if_set ) {
    while( split_if_set->size() ) {
      Node *iff = split_if_set->pop();
      if( iff->in(1)->is_Phi() ) {
        BoolNode *b = clone_iff( iff->in(1)->as_Phi(), loop );
        _igvn.replace_input_of(iff, 1, b);
      }
    }
  }
  if( split_bool_set ) {
    while( split_bool_set->size() ) {
      Node *b = split_bool_set->pop();
      Node *phi = b->in(1);
      assert( phi->is_Phi(), "" );
      CmpNode *cmp = clone_bool( (PhiNode*)phi, loop );
      _igvn.replace_input_of(b, 1, cmp);
    }
  }
  if( split_cex_set ) {
    while( split_cex_set->size() ) {
      Node *b = split_cex_set->pop();
      assert( b->in(0)->is_Region(), "" );
      assert( b->in(1)->is_Phi(), "" );
      assert( b->in(0)->in(0) == b->in(1)->in(0), "" );
      split_up( b, b->in(0), NULL );
    }
  }

}


//---------------------- stride_of_possible_iv -------------------------------------
// Looks for an iff/bool/comp with one operand of the compare
// being a cycle involving an add and a phi,
// with an optional truncation (left-shift followed by a right-shift)
// of the add. Returns zero if not an iv.
int PhaseIdealLoop::stride_of_possible_iv(Node* iff) {
  Node* trunc1 = NULL;
  Node* trunc2 = NULL;
  const TypeInt* ttype = NULL;
  if (!iff->is_If() || iff->in(1) == NULL || !iff->in(1)->is_Bool()) {
    return 0;
  }
  BoolNode* bl = iff->in(1)->as_Bool();
  Node* cmp = bl->in(1);
  if (!cmp || cmp->Opcode() != Op_CmpI && cmp->Opcode() != Op_CmpU) {
    return 0;
  }
  // Must have an invariant operand
  if (is_member(get_loop(iff), get_ctrl(cmp->in(2)))) {
    return 0;
  }
  Node* add2 = NULL;
  Node* cmp1 = cmp->in(1);
  if (cmp1->is_Phi()) {
    // (If (Bool (CmpX phi:(Phi ...(Optional-trunc(AddI phi add2))) )))
    Node* phi = cmp1;
    for (uint i = 1; i < phi->req(); i++) {
      Node* in = phi->in(i);
      Node* add = CountedLoopNode::match_incr_with_optional_truncation(in,
                                &trunc1, &trunc2, &ttype);
      if (add && add->in(1) == phi) {
        add2 = add->in(2);
        break;
      }
    }
  } else {
    // (If (Bool (CmpX addtrunc:(Optional-trunc((AddI (Phi ...addtrunc...) add2)) )))
    Node* addtrunc = cmp1;
    Node* add = CountedLoopNode::match_incr_with_optional_truncation(addtrunc,
                                &trunc1, &trunc2, &ttype);
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
  if (add2 != NULL) {
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
  Node* unique = NULL;
  if (!n) return NULL;
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node* use = n->fast_out(i);
    if (!has_ctrl(use) && loop->is_member(get_loop(use))) {
      if (unique != NULL) {
        return NULL;
      }
      unique = use;
    }
  }
  return unique;
}

//------------------------------ register_node -------------------------------------
// Utility to register node "n" with PhaseIdealLoop
void PhaseIdealLoop::register_node(Node* n, IdealLoopTree *loop, Node* pred, int ddepth) {
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
  guarantee(live_proj != NULL, "null projection");
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
  int ddepth = dom_depth(proj);

  _igvn.rehash_node_delayed(iff);
  _igvn.rehash_node_delayed(proj);

  proj->set_req(0, NULL);  // temporary disconnect
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
  guarantee(new_exit != NULL, "null exit node");
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
  int ddepth = dom_depth(proj);

  _igvn.rehash_node_delayed(iff);
  _igvn.rehash_node_delayed(proj);

  proj->set_req(0, NULL);  // temporary disconnect
  ProjNode* proj2 = proj_clone(proj, iff);
  register_node(proj2, loop, iff, ddepth);

  RegionNode* reg = new RegionNode(2);
  reg->set_req(1, proj2);
  register_node(reg, loop, iff, ddepth);

  IfNode* dum_if = new IfNode(reg, short_circuit_if(NULL, proj), iff->_prob, iff->_fcnt);
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
  if (bol->_test._test != BoolTest::lt) return NULL;
  CmpNode* cmpu = bol->in(1)->as_Cmp();
  if (cmpu->Opcode() != Op_CmpU) return NULL;
  int stride = stride_of_possible_iv(if_cmpu);
  if (stride == 0) return NULL;

  Node* lp_proj = stay_in_loop(if_cmpu, loop);
  guarantee(lp_proj != NULL, "null loop node");

  ProjNode* lp_continue = lp_proj->as_Proj();
  ProjNode* lp_exit     = if_cmpu->proj_out(!lp_continue->is_IfTrue())->as_Proj();

  Node* limit = NULL;
  if (stride > 0) {
    limit = cmpu->in(2);
  } else {
    limit = _igvn.makecon(TypeInt::ZERO);
    set_ctrl(limit, C->root());
  }
  // Create a new region on the exit path
  RegionNode* reg = insert_region_before_proj(lp_exit);
  guarantee(reg != NULL, "null region node");

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
  Arena *a = Thread::current()->resource_area();
  VectorSet visited(a);
  Node_Stack nstack(a, loop->_body.size());

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
#if !defined(PRODUCT)
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
    not_peel <<= n_clone->_idx;  // add n_clone to not_peel set.
#if !defined(PRODUCT)
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
  if( hit == NULL ) {
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
//               loop predicate
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
//               loop predicate
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
//               loop predicate
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
//               loop predicate
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
    return false;  }

  LoopNode *head  = loop->_head->as_Loop();

  if (head->is_partial_peel_loop() || head->partial_peel_has_failed()) {
    return false;
  }

  // Check for complex exit control
  for(uint ii = 0; ii < loop->_body.size(); ii++ ) {
    Node *n = loop->_body.at(ii);
    int opc = n->Opcode();
    if (n->is_Call()        ||
        opc == Op_Catch     ||
        opc == Op_CatchProj ||
        opc == Op_Jump      ||
        opc == Op_JumpProj) {
#if !defined(PRODUCT)
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
  IfNode *peel_if = NULL;
  IfNode *peel_if_cmpu = NULL;

  Node *iff = loop->tail();
  while( iff != head ) {
    if( iff->is_If() ) {
      Node *ctrl = get_ctrl(iff->in(1));
      if (ctrl->is_top()) return false; // Dead test on live IF.
      // If loop-varying exit-test, check for induction variable
      if( loop->is_member(get_loop(ctrl)) &&
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
  IfNode* new_peel_if = NULL;
  if (peel_if == NULL) {
    if (!PartialPeelAtUnsignedTests || peel_if_cmpu == NULL) {
      return false;   // No peel point found
    }
    new_peel_if = insert_cmpi_loop_exit(peel_if_cmpu, loop);
    if (new_peel_if == NULL) {
      return false;   // No peel point found
    }
    peel_if = new_peel_if;
  }
  Node* last_peel        = stay_in_loop(peel_if, loop);
  Node* first_not_peeled = stay_in_loop(last_peel, loop);
  if (first_not_peeled == NULL || first_not_peeled == head) {
    return false;
  }

#if !defined(PRODUCT)
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
  ResourceArea *area = Thread::current()->resource_area();
  VectorSet peel(area);
  VectorSet not_peel(area);
  Node_List peel_list(area);
  Node_List worklist(area);
  Node_List sink_list(area);

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
  uint i;
  for(i = 0; i < loop->_body.size(); i++ ) {
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
  scheduled_nodelist(loop, peel, peel_list );

  assert(is_valid_loop_partition(loop, peel, peel_list, not_peel), "bad partition");

  // For future check for too many new phis
  uint old_phi_cnt = 0;
  for (DUIterator_Fast jmax, j = head->fast_outs(jmax); j < jmax; j++) {
    Node* use = head->fast_out(j);
    if (use->is_Phi()) old_phi_cnt++;
  }

#if !defined(PRODUCT)
  if (TracePartialPeeling) {
    tty->print_cr("\npeeled list");
  }
#endif

  // Evacuate nodes in peel region into the not_peeled region if possible
  uint new_phi_cnt = 0;
  uint cloned_for_outside_use = 0;
  for (i = 0; i < peel_list.size();) {
    Node* n = peel_list.at(i);
#if !defined(PRODUCT)
  if (TracePartialPeeling) n->dump();
#endif
    bool incr = true;
    if ( !n->is_CFG() ) {

      if ( has_use_in_set(n, not_peel) ) {

        // If not used internal to the peeled region,
        // move "n" from peeled to not_peeled region.

        if ( !has_use_internal_to_set(n, peel, loop) ) {

          // if not pinned and not a load (which maybe anti-dependent on a store)
          // and not a CMove (Matcher expects only bool->cmove).
          if ( n->in(0) == NULL && !n->is_Load() && !n->is_CMove() ) {
            cloned_for_outside_use += clone_for_use_outside_loop( loop, n, worklist );
            sink_list.push(n);
            peel     >>= n->_idx; // delete n from peel set.
            not_peel <<= n->_idx; // add n to not_peel set.
            peel_list.remove(i);
            incr = false;
#if !defined(PRODUCT)
            if (TracePartialPeeling) {
              tty->print_cr("sink to not_peeled region: %d newbb: %d",
                            n->_idx, get_ctrl(n)->_idx);
            }
#endif
          }
        } else {
          // Otherwise check for special def-use cases that span
          // the peel/not_peel boundary such as bool->if
          clone_for_special_use_inside_loop( loop, n, not_peel, sink_list, worklist );
          new_phi_cnt++;
        }
      }
    }
    if (incr) i++;
  }

  if (new_phi_cnt > old_phi_cnt + PartialPeelNewPhiDelta) {
#if !defined(PRODUCT)
    if (TracePartialPeeling) {
      tty->print_cr("\nToo many new phis: %d  old %d new cmpi: %c",
                    new_phi_cnt, old_phi_cnt, new_peel_if != NULL?'T':'F');
    }
#endif
    if (new_peel_if != NULL) {
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

  clone_loop( loop, old_new, dd );

  const uint clone_exit_idx = 1;
  const uint orig_exit_idx  = 2;
  assert(is_valid_clone_loop_form( loop, peel_list, orig_exit_idx, clone_exit_idx ), "bad clone loop");

  Node* head_clone             = old_new[head->_idx];
  LoopNode* new_head_clone     = old_new[new_head->_idx]->as_Loop();
  Node* orig_tail_clone        = head_clone->in(2);

  // Add phi if "def" node is in peel set and "use" is not

  for(i = 0; i < peel_list.size(); i++ ) {
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
              if (old_new[use->_idx] != NULL) { // null for dead code
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

  for(i = 0; i < loop->_body.size(); i++ ) {
    Node *n = loop->_body.at(i);
    if (!n->is_CFG()           && n->in(0) != NULL        &&
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

#if !defined(PRODUCT)
  if (TracePartialPeeling) {
    tty->print_cr("\nafter partial peel one iteration");
    Node_List wl(area);
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
  return true;
}

//------------------------------reorg_offsets----------------------------------
// Reorganize offset computations to lower register pressure.  Mostly
// prevent loop-fallout uses of the pre-incremented trip counter (which are
// then alive with the post-incremented trip counter forcing an extra
// register move)
void PhaseIdealLoop::reorg_offsets(IdealLoopTree *loop) {
  // Perform it only for canonical counted loops.
  // Loop's shape could be messed up by iteration_split_impl.
  if (!loop->_head->is_CountedLoop())
    return;
  if (!loop->_head->as_Loop()->is_valid_counted_loop())
    return;

  CountedLoopNode *cl = loop->_head->as_CountedLoop();
  CountedLoopEndNode *cle = cl->loopexit();
  Node *exit = cle->proj_out(false);
  Node *phi = cl->phi();

  // Check for the special case of folks using the pre-incremented
  // trip-counter on the fall-out path (forces the pre-incremented
  // and post-incremented trip counter to be live at the same time).
  // Fix this by adjusting to use the post-increment trip counter.

  bool progress = true;
  while (progress) {
    progress = false;
    for (DUIterator_Fast imax, i = phi->fast_outs(imax); i < imax; i++) {
      Node* use = phi->fast_out(i);   // User of trip-counter
      if (!has_ctrl(use))  continue;
      Node *u_ctrl = get_ctrl(use);
      if (use->is_Phi()) {
        u_ctrl = NULL;
        for (uint j = 1; j < use->req(); j++)
          if (use->in(j) == phi)
            u_ctrl = dom_lca(u_ctrl, use->in(0)->in(j));
      }
      IdealLoopTree *u_loop = get_loop(u_ctrl);
      // Look for loop-invariant use
      if (u_loop == loop) continue;
      if (loop->is_member(u_loop)) continue;
      // Check that use is live out the bottom.  Assuming the trip-counter
      // update is right at the bottom, uses of of the loop middle are ok.
      if (dom_lca(exit, u_ctrl) != exit) continue;
      // Hit!  Refactor use to use the post-incremented tripcounter.
      // Compute a post-increment tripcounter.
      Node *opaq = new Opaque2Node( C, cle->incr() );
      register_new_node(opaq, exit);
      Node *neg_stride = _igvn.intcon(-cle->stride_con());
      set_ctrl(neg_stride, C->root());
      Node *post = new AddINode( opaq, neg_stride);
      register_new_node(post, exit);
      _igvn.rehash_node_delayed(use);
      for (uint j = 1; j < use->req(); j++) {
        if (use->in(j) == phi)
          use->set_req(j, post);
      }
      // Since DU info changed, rerun loop
      progress = true;
      break;
    }
  }

}
