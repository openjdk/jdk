/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "ci/ciTypeFlow.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "opto/addnode.hpp"
#include "opto/castnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/connode.hpp"
#include "opto/loopnode.hpp"
#include "opto/phaseX.hpp"
#include "opto/predicates_enums.hpp"
#include "opto/rootnode.hpp"
#include "opto/runtime.hpp"
#include "opto/subnode.hpp"
#include "opto/subtypenode.hpp"

// Portions of code courtesy of Clifford Click

// Optimization - Graph Style


#ifndef PRODUCT
extern uint explicit_null_checks_elided;
#endif

IfNode::IfNode(Node* control, Node* bol, float p, float fcnt)
    : MultiBranchNode(2),
      _prob(p),
      _fcnt(fcnt),
      _assertion_predicate_type(AssertionPredicateType::None) {
  init_node(control, bol);
}

IfNode::IfNode(Node* control, Node* bol, float p, float fcnt, AssertionPredicateType assertion_predicate_type)
    : MultiBranchNode(2),
      _prob(p),
      _fcnt(fcnt),
      _assertion_predicate_type(assertion_predicate_type) {
  init_node(control, bol);
}

//=============================================================================
//------------------------------Value------------------------------------------
// Return a tuple for whichever arm of the IF is reachable
const Type* IfNode::Value(PhaseGVN* phase) const {
  if( !in(0) ) return Type::TOP;
  if( phase->type(in(0)) == Type::TOP )
    return Type::TOP;
  const Type *t = phase->type(in(1));
  if( t == Type::TOP )          // data is undefined
    return TypeTuple::IFNEITHER; // unreachable altogether
  if( t == TypeInt::ZERO )      // zero, or false
    return TypeTuple::IFFALSE;  // only false branch is reachable
  if( t == TypeInt::ONE )       // 1, or true
    return TypeTuple::IFTRUE;   // only true branch is reachable
  assert( t == TypeInt::BOOL, "expected boolean type" );

  return TypeTuple::IFBOTH;     // No progress
}

const RegMask &IfNode::out_RegMask() const {
  return RegMask::Empty;
}

//------------------------------split_if---------------------------------------
// Look for places where we merge constants, then test on the merged value.
// If the IF test will be constant folded on the path with the constant, we
// win by splitting the IF to before the merge point.
static Node* split_if(IfNode *iff, PhaseIterGVN *igvn) {
  // I could be a lot more general here, but I'm trying to squeeze this
  // in before the Christmas '98 break so I'm gonna be kinda restrictive
  // on the patterns I accept.  CNC

  // Look for a compare of a constant and a merged value
  Node *i1 = iff->in(1);
  if( !i1->is_Bool() ) return nullptr;
  BoolNode *b = i1->as_Bool();
  Node *cmp = b->in(1);
  if( !cmp->is_Cmp() ) return nullptr;
  i1 = cmp->in(1);
  if( i1 == nullptr || !i1->is_Phi() ) return nullptr;
  PhiNode *phi = i1->as_Phi();
  Node *con2 = cmp->in(2);
  if( !con2->is_Con() ) return nullptr;
  // See that the merge point contains some constants
  Node *con1=nullptr;
  uint i4;
  RegionNode* phi_region = phi->region();
  for (i4 = 1; i4 < phi->req(); i4++ ) {
    con1 = phi->in(i4);
    // Do not optimize partially collapsed merges
    if (con1 == nullptr || phi_region->in(i4) == nullptr || igvn->type(phi_region->in(i4)) == Type::TOP) {
      igvn->_worklist.push(iff);
      return nullptr;
    }
    if( con1->is_Con() ) break; // Found a constant
    // Also allow null-vs-not-null checks
    const TypePtr *tp = igvn->type(con1)->isa_ptr();
    if( tp && tp->_ptr == TypePtr::NotNull )
      break;
  }
  if( i4 >= phi->req() ) return nullptr; // Found no constants

  igvn->C->set_has_split_ifs(true); // Has chance for split-if

  // Make sure that the compare can be constant folded away
  Node *cmp2 = cmp->clone();
  cmp2->set_req(1,con1);
  cmp2->set_req(2,con2);
  const Type *t = cmp2->Value(igvn);
  // This compare is dead, so whack it!
  igvn->remove_dead_node(cmp2);
  if( !t->singleton() ) return nullptr;

  // No intervening control, like a simple Call
  Node* r = iff->in(0);
  if (!r->is_Region() || r->is_Loop() || phi_region != r || r->as_Region()->is_copy()) {
    return nullptr;
  }

  // No other users of the cmp/bool
  if (b->outcnt() != 1 || cmp->outcnt() != 1) {
    //tty->print_cr("many users of cmp/bool");
    return nullptr;
  }

  // Make sure we can determine where all the uses of merged values go
  for (DUIterator_Fast jmax, j = r->fast_outs(jmax); j < jmax; j++) {
    Node* u = r->fast_out(j);
    if( u == r ) continue;
    if( u == iff ) continue;
    if( u->outcnt() == 0 ) continue; // use is dead & ignorable
    if( !u->is_Phi() ) {
      /*
      if( u->is_Start() ) {
        tty->print_cr("Region has inlined start use");
      } else {
        tty->print_cr("Region has odd use");
        u->dump(2);
      }*/
      return nullptr;
    }
    if( u != phi ) {
      // CNC - do not allow any other merged value
      //tty->print_cr("Merging another value");
      //u->dump(2);
      return nullptr;
    }
    // Make sure we can account for all Phi uses
    for (DUIterator_Fast kmax, k = u->fast_outs(kmax); k < kmax; k++) {
      Node* v = u->fast_out(k); // User of the phi
      // CNC - Allow only really simple patterns.
      // In particular I disallow AddP of the Phi, a fairly common pattern
      if (v == cmp) continue;  // The compare is OK
      if (v->is_ConstraintCast()) {
        // If the cast is derived from data flow edges, it may not have a control edge.
        // If so, it should be safe to split. But follow-up code can not deal with
        // this (l. 359). So skip.
        if (v->in(0) == nullptr) {
          return nullptr;
        }
        if (v->in(0)->in(0) == iff) {
          continue;               // CastPP/II of the IfNode is OK
        }
      }
      // Disabled following code because I cannot tell if exactly one
      // path dominates without a real dominator check. CNC 9/9/1999
      //uint vop = v->Opcode();
      //if( vop == Op_Phi ) {        // Phi from another merge point might be OK
      //  Node *r = v->in(0);        // Get controlling point
      //  if( !r ) return nullptr;   // Degraded to a copy
      //  // Find exactly one path in (either True or False doms, but not IFF)
      //  int cnt = 0;
      //  for( uint i = 1; i < r->req(); i++ )
      //    if( r->in(i) && r->in(i)->in(0) == iff )
      //      cnt++;
      //  if( cnt == 1 ) continue; // Exactly one of True or False guards Phi
      //}
      if( !v->is_Call() ) {
        /*
        if( v->Opcode() == Op_AddP ) {
          tty->print_cr("Phi has AddP use");
        } else if( v->Opcode() == Op_CastPP ) {
          tty->print_cr("Phi has CastPP use");
        } else if( v->Opcode() == Op_CastII ) {
          tty->print_cr("Phi has CastII use");
        } else {
          tty->print_cr("Phi has use I can't be bothered with");
        }
        */
      }
      return nullptr;

      /* CNC - Cut out all the fancy acceptance tests
      // Can we clone this use when doing the transformation?
      // If all uses are from Phis at this merge or constants, then YES.
      if( !v->in(0) && v != cmp ) {
        tty->print_cr("Phi has free-floating use");
        v->dump(2);
        return nullptr;
      }
      for( uint l = 1; l < v->req(); l++ ) {
        if( (!v->in(l)->is_Phi() || v->in(l)->in(0) != r) &&
            !v->in(l)->is_Con() ) {
          tty->print_cr("Phi has use");
          v->dump(2);
          return nullptr;
        } // End of if Phi-use input is neither Phi nor Constant
      } // End of for all inputs to Phi-use
      */
    } // End of for all uses of Phi
  } // End of for all uses of Region

  // Only do this if the IF node is in a sane state
  if (iff->outcnt() != 2)
    return nullptr;

  // Got a hit!  Do the Mondo Hack!
  //
  //ABC  a1c   def   ghi            B     1     e     h   A C   a c   d f   g i
  // R - Phi - Phi - Phi            Rc - Phi - Phi - Phi   Rx - Phi - Phi - Phi
  //     cmp - 2                         cmp - 2               cmp - 2
  //       bool                            bool_c                bool_x
  //       if                               if_c                  if_x
  //      T  F                              T  F                  T  F
  // ..s..    ..t ..                   ..s..    ..t..        ..s..    ..t..
  //
  // Split the paths coming into the merge point into 2 separate groups of
  // merges.  On the left will be all the paths feeding constants into the
  // Cmp's Phi.  On the right will be the remaining paths.  The Cmp's Phi
  // will fold up into a constant; this will let the Cmp fold up as well as
  // all the control flow.  Below the original IF we have 2 control
  // dependent regions, 's' and 't'.  Now we will merge the two paths
  // just prior to 's' and 't' from the two IFs.  At least 1 path (and quite
  // likely 2 or more) will promptly constant fold away.
  PhaseGVN *phase = igvn;

  // Make a region merging constants and a region merging the rest
  uint req_c = 0;
  for (uint ii = 1; ii < r->req(); ii++) {
    if (phi->in(ii) == con1) {
      req_c++;
    }
    if (Node::may_be_loop_entry(r->in(ii))) {
      // Bail out if splitting through a region with a Parse Predicate input (could
      // also be a loop header before loop opts creates a LoopNode for it).
      return nullptr;
    }
  }

  // If all the defs of the phi are the same constant, we already have the desired end state.
  // Skip the split that would create empty phi and region nodes.
  if ((r->req() - req_c) == 1) {
    return nullptr;
  }

  // At this point we know that we can apply the split if optimization. If the region is still on the worklist,
  // we should wait until it is processed. The region might be removed which makes this optimization redundant.
  // This also avoids the creation of dead data loops when rewiring data nodes below when a region is dying.
  if (igvn->_worklist.member(r)) {
    igvn->_worklist.push(iff); // retry split if later again
    return nullptr;
  }

  Node *region_c = new RegionNode(req_c + 1);
  Node *phi_c    = con1;
  uint  len      = r->req();
  Node *region_x = new RegionNode(len - req_c);
  Node *phi_x    = PhiNode::make_blank(region_x, phi);
  for (uint i = 1, i_c = 1, i_x = 1; i < len; i++) {
    if (phi->in(i) == con1) {
      region_c->init_req( i_c++, r  ->in(i) );
    } else {
      region_x->init_req( i_x,   r  ->in(i) );
      phi_x   ->init_req( i_x++, phi->in(i) );
    }
  }

  // Register the new RegionNodes but do not transform them.  Cannot
  // transform until the entire Region/Phi conglomerate has been hacked
  // as a single huge transform.
  igvn->register_new_node_with_optimizer( region_c );
  igvn->register_new_node_with_optimizer( region_x );
  // Prevent the untimely death of phi_x.  Currently he has no uses.  He is
  // about to get one.  If this only use goes away, then phi_x will look dead.
  // However, he will be picking up some more uses down below.
  Node *hook = new Node(4);
  hook->init_req(0, phi_x);
  hook->init_req(1, phi_c);
  phi_x = phase->transform( phi_x );

  // Make the compare
  Node *cmp_c = phase->makecon(t);
  Node *cmp_x = cmp->clone();
  cmp_x->set_req(1,phi_x);
  cmp_x->set_req(2,con2);
  cmp_x = phase->transform(cmp_x);
  // Make the bool
  Node *b_c = phase->transform(new BoolNode(cmp_c,b->_test._test));
  Node *b_x = phase->transform(new BoolNode(cmp_x,b->_test._test));
  // Make the IfNode
  IfNode* iff_c = iff->clone()->as_If();
  iff_c->set_req(0, region_c);
  iff_c->set_req(1, b_c);
  igvn->set_type_bottom(iff_c);
  igvn->_worklist.push(iff_c);
  hook->init_req(2, iff_c);

  IfNode* iff_x = iff->clone()->as_If();
  iff_x->set_req(0, region_x);
  iff_x->set_req(1, b_x);
  igvn->set_type_bottom(iff_x);
  igvn->_worklist.push(iff_x);
  hook->init_req(3, iff_x);

  // Make the true/false arms
  Node *iff_c_t = phase->transform(new IfTrueNode (iff_c));
  Node *iff_c_f = phase->transform(new IfFalseNode(iff_c));
  Node *iff_x_t = phase->transform(new IfTrueNode (iff_x));
  Node *iff_x_f = phase->transform(new IfFalseNode(iff_x));

  // Merge the TRUE paths
  Node *region_s = new RegionNode(3);
  igvn->_worklist.push(region_s);
  region_s->init_req(1, iff_c_t);
  region_s->init_req(2, iff_x_t);
  igvn->register_new_node_with_optimizer( region_s );

  // Merge the FALSE paths
  Node *region_f = new RegionNode(3);
  igvn->_worklist.push(region_f);
  region_f->init_req(1, iff_c_f);
  region_f->init_req(2, iff_x_f);
  igvn->register_new_node_with_optimizer( region_f );

  igvn->hash_delete(cmp);// Remove soon-to-be-dead node from hash table.
  cmp->set_req(1,nullptr);  // Whack the inputs to cmp because it will be dead
  cmp->set_req(2,nullptr);
  // Check for all uses of the Phi and give them a new home.
  // The 'cmp' got cloned, but CastPP/IIs need to be moved.
  Node *phi_s = nullptr;     // do not construct unless needed
  Node *phi_f = nullptr;     // do not construct unless needed
  for (DUIterator_Last i2min, i2 = phi->last_outs(i2min); i2 >= i2min; --i2) {
    Node* v = phi->last_out(i2);// User of the phi
    igvn->rehash_node_delayed(v); // Have to fixup other Phi users
    uint vop = v->Opcode();
    Node *proj = nullptr;
    if( vop == Op_Phi ) {       // Remote merge point
      Node *r = v->in(0);
      for (uint i3 = 1; i3 < r->req(); i3++)
        if (r->in(i3) && r->in(i3)->in(0) == iff) {
          proj = r->in(i3);
          break;
        }
    } else if( v->is_ConstraintCast() ) {
      proj = v->in(0);          // Controlling projection
    } else {
      assert( 0, "do not know how to handle this guy" );
    }
    guarantee(proj != nullptr, "sanity");

    Node *proj_path_data, *proj_path_ctrl;
    if( proj->Opcode() == Op_IfTrue ) {
      if( phi_s == nullptr ) {
        // Only construct phi_s if needed, otherwise provides
        // interfering use.
        phi_s = PhiNode::make_blank(region_s,phi);
        phi_s->init_req( 1, phi_c );
        phi_s->init_req( 2, phi_x );
        hook->add_req(phi_s);
        phi_s = phase->transform(phi_s);
      }
      proj_path_data = phi_s;
      proj_path_ctrl = region_s;
    } else {
      if( phi_f == nullptr ) {
        // Only construct phi_f if needed, otherwise provides
        // interfering use.
        phi_f = PhiNode::make_blank(region_f,phi);
        phi_f->init_req( 1, phi_c );
        phi_f->init_req( 2, phi_x );
        hook->add_req(phi_f);
        phi_f = phase->transform(phi_f);
      }
      proj_path_data = phi_f;
      proj_path_ctrl = region_f;
    }

    // Fixup 'v' for for the split
    if( vop == Op_Phi ) {       // Remote merge point
      uint i;
      for( i = 1; i < v->req(); i++ )
        if( v->in(i) == phi )
          break;
      v->set_req(i, proj_path_data );
    } else if( v->is_ConstraintCast() ) {
      v->set_req(0, proj_path_ctrl );
      v->set_req(1, proj_path_data );
    } else
      ShouldNotReachHere();
  }

  // Now replace the original iff's True/False with region_s/region_t.
  // This makes the original iff go dead.
  for (DUIterator_Last i3min, i3 = iff->last_outs(i3min); i3 >= i3min; --i3) {
    Node* p = iff->last_out(i3);
    assert( p->Opcode() == Op_IfTrue || p->Opcode() == Op_IfFalse, "" );
    Node *u = (p->Opcode() == Op_IfTrue) ? region_s : region_f;
    // Replace p with u
    igvn->add_users_to_worklist(p);
    for (DUIterator_Last lmin, l = p->last_outs(lmin); l >= lmin;) {
      Node* x = p->last_out(l);
      igvn->hash_delete(x);
      uint uses_found = 0;
      for( uint j = 0; j < x->req(); j++ ) {
        if( x->in(j) == p ) {
          x->set_req(j, u);
          uses_found++;
        }
      }
      l -= uses_found;    // we deleted 1 or more copies of this edge
    }
    igvn->remove_dead_node(p);
  }

  // Force the original merge dead
  igvn->hash_delete(r);
  // First, remove region's dead users.
  for (DUIterator_Last lmin, l = r->last_outs(lmin); l >= lmin;) {
    Node* u = r->last_out(l);
    if( u == r ) {
      r->set_req(0, nullptr);
    } else {
      assert(u->outcnt() == 0, "only dead users");
      igvn->remove_dead_node(u);
    }
    l -= 1;
  }
  igvn->remove_dead_node(r);

  // Now remove the bogus extra edges used to keep things alive
  igvn->remove_dead_node( hook );

  // Must return either the original node (now dead) or a new node
  // (Do not return a top here, since that would break the uniqueness of top.)
  return new ConINode(TypeInt::ZERO);
}

IfNode* IfNode::make_with_same_profile(IfNode* if_node_profile, Node* ctrl, Node* bol) {
  // Assert here that we only try to create a clone from an If node with the same profiling if that actually makes sense.
  // Some If node subtypes should not be cloned in this way. In theory, we should not clone BaseCountedLoopEndNodes.
  // But they can end up being used as normal If nodes when peeling a loop - they serve as zero-trip guard.
  // Allow them as well.
  assert(if_node_profile->Opcode() == Op_If || if_node_profile->is_RangeCheck()
         || if_node_profile->is_BaseCountedLoopEnd(), "should not clone other nodes");
  if (if_node_profile->is_RangeCheck()) {
    // RangeCheck nodes could be further optimized.
    return new RangeCheckNode(ctrl, bol, if_node_profile->_prob, if_node_profile->_fcnt);
  } else {
    // Not a RangeCheckNode? Fall back to IfNode.
    return new IfNode(ctrl, bol, if_node_profile->_prob, if_node_profile->_fcnt);
  }
}

// if this IfNode follows a range check pattern return the projection
// for the failed path
ProjNode* IfNode::range_check_trap_proj(int& flip_test, Node*& l, Node*& r) {
  if (outcnt() != 2) {
    return nullptr;
  }
  Node* b = in(1);
  if (b == nullptr || !b->is_Bool())  return nullptr;
  BoolNode* bn = b->as_Bool();
  Node* cmp = bn->in(1);
  if (cmp == nullptr)  return nullptr;
  if (cmp->Opcode() != Op_CmpU)  return nullptr;

  l = cmp->in(1);
  r = cmp->in(2);
  flip_test = 1;
  if (bn->_test._test == BoolTest::le) {
    l = cmp->in(2);
    r = cmp->in(1);
    flip_test = 2;
  } else if (bn->_test._test != BoolTest::lt) {
    return nullptr;
  }
  if (l->is_top())  return nullptr;   // Top input means dead test
  if (r->Opcode() != Op_LoadRange && !is_RangeCheck())  return nullptr;

  // We have recognized one of these forms:
  //  Flip 1:  If (Bool[<] CmpU(l, LoadRange)) ...
  //  Flip 2:  If (Bool[<=] CmpU(LoadRange, l)) ...

  ProjNode* iftrap = proj_out_or_null(flip_test == 2 ? true : false);
  return iftrap;
}


//------------------------------is_range_check---------------------------------
// Return 0 if not a range check.  Return 1 if a range check and set index and
// offset.  Return 2 if we had to negate the test.  Index is null if the check
// is versus a constant.
int RangeCheckNode::is_range_check(Node* &range, Node* &index, jint &offset) {
  int flip_test = 0;
  Node* l = nullptr;
  Node* r = nullptr;
  ProjNode* iftrap = range_check_trap_proj(flip_test, l, r);

  if (iftrap == nullptr) {
    return 0;
  }

  // Make sure it's a real range check by requiring an uncommon trap
  // along the OOB path.  Otherwise, it's possible that the user wrote
  // something which optimized to look like a range check but behaves
  // in some other way.
  if (iftrap->is_uncommon_trap_proj(Deoptimization::Reason_range_check) == nullptr) {
    return 0;
  }

  // Look for index+offset form
  Node* ind = l;
  jint  off = 0;
  if (l->is_top()) {
    return 0;
  } else if (l->Opcode() == Op_AddI) {
    if ((off = l->in(1)->find_int_con(0)) != 0) {
      ind = l->in(2)->uncast();
    } else if ((off = l->in(2)->find_int_con(0)) != 0) {
      ind = l->in(1)->uncast();
    }
  } else if ((off = l->find_int_con(-1)) >= 0) {
    // constant offset with no variable index
    ind = nullptr;
  } else {
    // variable index with no constant offset (or dead negative index)
    off = 0;
  }

  // Return all the values:
  index  = ind;
  offset = off;
  range  = r;
  return flip_test;
}

//------------------------------adjust_check-----------------------------------
// Adjust (widen) a prior range check
static void adjust_check(IfProjNode* proj, Node* range, Node* index,
                         int flip, jint off_lo, PhaseIterGVN* igvn) {
  PhaseGVN *gvn = igvn;
  // Break apart the old check
  Node *iff = proj->in(0);
  Node *bol = iff->in(1);
  if( bol->is_top() ) return;   // In case a partially dead range check appears
  // bail (or bomb[ASSERT/DEBUG]) if NOT projection-->IfNode-->BoolNode
  DEBUG_ONLY( if (!bol->is_Bool()) { proj->dump(3); fatal("Expect projection-->IfNode-->BoolNode"); } )
  if (!bol->is_Bool()) return;

  Node *cmp = bol->in(1);
  // Compute a new check
  Node *new_add = gvn->intcon(off_lo);
  if (index) {
    new_add = off_lo ? gvn->transform(new AddINode(index, new_add)) : index;
  }
  Node *new_cmp = (flip == 1)
    ? new CmpUNode(new_add, range)
    : new CmpUNode(range, new_add);
  new_cmp = gvn->transform(new_cmp);
  // See if no need to adjust the existing check
  if (new_cmp == cmp) return;
  // Else, adjust existing check
  Node* new_bol = gvn->transform(new BoolNode(new_cmp, bol->as_Bool()->_test._test));
  igvn->rehash_node_delayed(iff);
  iff->set_req_X(1, new_bol, igvn);
  // As part of range check smearing, this range check is widened. Loads and range check Cast nodes that are control
  // dependent on this range check now depend on multiple dominating range checks. These control dependent nodes end up
  // at the lowest/nearest dominating check in the graph. To ensure that these Loads/Casts do not float above any of the
  // dominating checks (even when the lowest dominating check is later replaced by yet another dominating check), we
  // need to pin them at the lowest dominating check.
  proj->pin_array_access_nodes(igvn);
}

//------------------------------up_one_dom-------------------------------------
// Walk up the dominator tree one step.  Return null at root or true
// complex merges.  Skips through small diamonds.
Node* IfNode::up_one_dom(Node *curr, bool linear_only) {
  Node *dom = curr->in(0);
  if( !dom )                    // Found a Region degraded to a copy?
    return curr->nonnull_req(); // Skip thru it

  if( curr != dom )             // Normal walk up one step?
    return dom;

  // Use linear_only if we are still parsing, since we cannot
  // trust the regions to be fully filled in.
  if (linear_only)
    return nullptr;

  if( dom->is_Root() )
    return nullptr;

  // Else hit a Region.  Check for a loop header
  if( dom->is_Loop() )
    return dom->in(1);          // Skip up thru loops

  // Check for small diamonds
  Node *din1, *din2, *din3, *din4;
  if( dom->req() == 3 &&        // 2-path merge point
      (din1 = dom ->in(1)) &&   // Left  path exists
      (din2 = dom ->in(2)) &&   // Right path exists
      (din3 = din1->in(0)) &&   // Left  path up one
      (din4 = din2->in(0)) ) {  // Right path up one
    if( din3->is_Call() &&      // Handle a slow-path call on either arm
        (din3 = din3->in(0)) )
      din3 = din3->in(0);
    if( din4->is_Call() &&      // Handle a slow-path call on either arm
        (din4 = din4->in(0)) )
      din4 = din4->in(0);
    if (din3 != nullptr && din3 == din4 && din3->is_If()) // Regions not degraded to a copy
      return din3;              // Skip around diamonds
  }

  // Give up the search at true merges
  return nullptr;                  // Dead loop?  Or hit root?
}


//------------------------------filtered_int_type--------------------------------
// Return a possibly more restrictive type for val based on condition control flow for an if
const TypeInt* IfNode::filtered_int_type(PhaseGVN* gvn, Node* val, Node* if_proj) {
  assert(if_proj &&
         (if_proj->Opcode() == Op_IfTrue || if_proj->Opcode() == Op_IfFalse), "expecting an if projection");
  if (if_proj->in(0) && if_proj->in(0)->is_If()) {
    IfNode* iff = if_proj->in(0)->as_If();
    if (iff->in(1) && iff->in(1)->is_Bool()) {
      BoolNode* bol = iff->in(1)->as_Bool();
      if (bol->in(1) && bol->in(1)->is_Cmp()) {
        const CmpNode* cmp  = bol->in(1)->as_Cmp();
        if (cmp->in(1) == val) {
          const TypeInt* cmp2_t = gvn->type(cmp->in(2))->isa_int();
          if (cmp2_t != nullptr) {
            jint lo = cmp2_t->_lo;
            jint hi = cmp2_t->_hi;
            BoolTest::mask msk = if_proj->Opcode() == Op_IfTrue ? bol->_test._test : bol->_test.negate();
            switch (msk) {
            case BoolTest::ne: {
              // If val is compared to its lower or upper bound, we can narrow the type
              const TypeInt* val_t = gvn->type(val)->isa_int();
              if (val_t != nullptr && !val_t->singleton() && cmp2_t->is_con()) {
                if (val_t->_lo == lo) {
                  return TypeInt::make(val_t->_lo + 1, val_t->_hi, val_t->_widen);
                } else if (val_t->_hi == hi) {
                  return TypeInt::make(val_t->_lo, val_t->_hi - 1, val_t->_widen);
                }
              }
              // Can't refine type
              return nullptr;
            }
            case BoolTest::eq:
              return cmp2_t;
            case BoolTest::lt:
              lo = TypeInt::INT->_lo;
              if (hi != min_jint) {
                hi = hi - 1;
              }
              break;
            case BoolTest::le:
              lo = TypeInt::INT->_lo;
              break;
            case BoolTest::gt:
              if (lo != max_jint) {
                lo = lo + 1;
              }
              hi = TypeInt::INT->_hi;
              break;
            case BoolTest::ge:
              // lo unchanged
              hi = TypeInt::INT->_hi;
              break;
            default:
              break;
            }
            const TypeInt* rtn_t = TypeInt::make(lo, hi, cmp2_t->_widen);
            return rtn_t;
          }
        }
      }
    }
  }
  return nullptr;
}

//------------------------------fold_compares----------------------------
// See if a pair of CmpIs can be converted into a CmpU.  In some cases
// the direction of this if is determined by the preceding if so it
// can be eliminate entirely.
//
// Given an if testing (CmpI n v) check for an immediately control
// dependent if that is testing (CmpI n v2) and has one projection
// leading to this if and the other projection leading to a region
// that merges one of this ifs control projections.
//
//                   If
//                  / |
//                 /  |
//                /   |
//              If    |
//              /\    |
//             /  \   |
//            /    \  |
//           /    Region
//
// Or given an if testing (CmpI n v) check for a dominating if that is
// testing (CmpI n v2), both having one projection leading to an
// uncommon trap. Allow Another independent guard in between to cover
// an explicit range check:
// if (index < 0 || index >= array.length) {
// which may need a null check to guard the LoadRange
//
//                   If
//                  / \
//                 /   \
//                /     \
//              If      unc
//              /\
//             /  \
//            /    \
//           /      unc
//

// Is the comparison for this If suitable for folding?
bool IfNode::cmpi_folds(PhaseIterGVN* igvn, bool fold_ne) {
  return in(1) != nullptr &&
    in(1)->is_Bool() &&
    in(1)->in(1) != nullptr &&
    in(1)->in(1)->Opcode() == Op_CmpI &&
    in(1)->in(1)->in(2) != nullptr &&
    in(1)->in(1)->in(2) != igvn->C->top() &&
    (in(1)->as_Bool()->_test.is_less() ||
     in(1)->as_Bool()->_test.is_greater() ||
     (fold_ne && in(1)->as_Bool()->_test._test == BoolTest::ne));
}

// Is a dominating control suitable for folding with this if?
bool IfNode::is_ctrl_folds(Node* ctrl, PhaseIterGVN* igvn) {
  return ctrl != nullptr &&
    ctrl->is_Proj() &&
    ctrl->outcnt() == 1 && // No side-effects
    ctrl->in(0) != nullptr &&
    ctrl->in(0)->Opcode() == Op_If &&
    ctrl->in(0)->outcnt() == 2 &&
    ctrl->in(0)->as_If()->cmpi_folds(igvn, true) &&
    // Must compare same value
    ctrl->in(0)->in(1)->in(1)->in(1) != nullptr &&
    ctrl->in(0)->in(1)->in(1)->in(1) != igvn->C->top() &&
    ctrl->in(0)->in(1)->in(1)->in(1) == in(1)->in(1)->in(1);
}

// Do this If and the dominating If share a region?
bool IfNode::has_shared_region(ProjNode* proj, ProjNode*& success, ProjNode*& fail) {
  ProjNode* otherproj = proj->other_if_proj();
  Node* otherproj_ctrl_use = otherproj->unique_ctrl_out_or_null();
  RegionNode* region = (otherproj_ctrl_use != nullptr && otherproj_ctrl_use->is_Region()) ? otherproj_ctrl_use->as_Region() : nullptr;
  success = nullptr;
  fail = nullptr;

  if (otherproj->outcnt() == 1 && region != nullptr && !region->has_phi()) {
    for (int i = 0; i < 2; i++) {
      ProjNode* proj = proj_out(i);
      if (success == nullptr && proj->outcnt() == 1 && proj->unique_out() == region) {
        success = proj;
      } else if (fail == nullptr) {
        fail = proj;
      } else {
        success = fail = nullptr;
      }
    }
  }
  return success != nullptr && fail != nullptr;
}

bool IfNode::is_dominator_unc(CallStaticJavaNode* dom_unc, CallStaticJavaNode* unc) {
  // Different methods and methods containing jsrs are not supported.
  ciMethod* method = unc->jvms()->method();
  ciMethod* dom_method = dom_unc->jvms()->method();
  if (method != dom_method || method->has_jsrs()) {
    return false;
  }
  // Check that both traps are in the same activation of the method (instead
  // of two activations being inlined through different call sites) by verifying
  // that the call stacks are equal for both JVMStates.
  JVMState* dom_caller = dom_unc->jvms()->caller();
  JVMState* caller = unc->jvms()->caller();
  if ((dom_caller == nullptr) != (caller == nullptr)) {
    // The current method must either be inlined into both dom_caller and
    // caller or must not be inlined at all (top method). Bail out otherwise.
    return false;
  } else if (dom_caller != nullptr && !dom_caller->same_calls_as(caller)) {
    return false;
  }
  // Check that the bci of the dominating uncommon trap dominates the bci
  // of the dominated uncommon trap. Otherwise we may not re-execute
  // the dominated check after deoptimization from the merged uncommon trap.
  ciTypeFlow* flow = dom_method->get_flow_analysis();
  int bci = unc->jvms()->bci();
  int dom_bci = dom_unc->jvms()->bci();
  if (!flow->is_dominated_by(bci, dom_bci)) {
    return false;
  }

  return true;
}

// Return projection that leads to an uncommon trap if any
ProjNode* IfNode::uncommon_trap_proj(CallStaticJavaNode*& call, Deoptimization::DeoptReason reason) const {
  for (int i = 0; i < 2; i++) {
    call = proj_out(i)->is_uncommon_trap_proj(reason);
    if (call != nullptr) {
      return proj_out(i);
    }
  }
  return nullptr;
}

// Do this If and the dominating If both branch out to an uncommon trap
bool IfNode::has_only_uncommon_traps(ProjNode* proj, ProjNode*& success, ProjNode*& fail, PhaseIterGVN* igvn) {
  ProjNode* otherproj = proj->other_if_proj();
  CallStaticJavaNode* dom_unc = otherproj->is_uncommon_trap_proj();

  if (otherproj->outcnt() == 1 && dom_unc != nullptr) {
    // We need to re-execute the folded Ifs after deoptimization from the merged traps
    if (!dom_unc->jvms()->should_reexecute()) {
      return false;
    }

    CallStaticJavaNode* unc = nullptr;
    ProjNode* unc_proj = uncommon_trap_proj(unc);
    if (unc_proj != nullptr && unc_proj->outcnt() == 1) {
      if (dom_unc == unc) {
        // Allow the uncommon trap to be shared through a region
        RegionNode* r = unc->in(0)->as_Region();
        if (r->outcnt() != 2 || r->req() != 3 || r->find_edge(otherproj) == -1 || r->find_edge(unc_proj) == -1) {
          return false;
        }
        assert(r->has_phi() == nullptr, "simple region shouldn't have a phi");
      } else if (dom_unc->in(0) != otherproj || unc->in(0) != unc_proj) {
        return false;
      }

      if (!is_dominator_unc(dom_unc, unc)) {
        return false;
      }

      // See merge_uncommon_traps: the reason of the uncommon trap
      // will be changed and the state of the dominating If will be
      // used. Checked that we didn't apply this transformation in a
      // previous compilation and it didn't cause too many traps
      ciMethod* dom_method = dom_unc->jvms()->method();
      int dom_bci = dom_unc->jvms()->bci();
      if (!igvn->C->too_many_traps(dom_method, dom_bci, Deoptimization::Reason_unstable_fused_if) &&
          !igvn->C->too_many_traps(dom_method, dom_bci, Deoptimization::Reason_range_check) &&
          // Return true if c2 manages to reconcile with UnstableIf optimization. See the comments for it.
          igvn->C->remove_unstable_if_trap(dom_unc, true/*yield*/)) {
        success = unc_proj;
        fail = unc_proj->other_if_proj();
        return true;
      }
    }
  }
  return false;
}

// Check that the 2 CmpI can be folded into as single CmpU and proceed with the folding
bool IfNode::fold_compares_helper(ProjNode* proj, ProjNode* success, ProjNode* fail, PhaseIterGVN* igvn) {
  Node* this_cmp = in(1)->in(1);
  BoolNode* this_bool = in(1)->as_Bool();
  IfNode* dom_iff = proj->in(0)->as_If();
  BoolNode* dom_bool = dom_iff->in(1)->as_Bool();
  Node* lo = dom_iff->in(1)->in(1)->in(2);
  Node* hi = this_cmp->in(2);
  Node* n = this_cmp->in(1);
  ProjNode* otherproj = proj->other_if_proj();

  const TypeInt* lo_type = IfNode::filtered_int_type(igvn, n, otherproj);
  const TypeInt* hi_type = IfNode::filtered_int_type(igvn, n, success);

  BoolTest::mask lo_test = dom_bool->_test._test;
  BoolTest::mask hi_test = this_bool->_test._test;
  BoolTest::mask cond = hi_test;

  // convert:
  //
  //          dom_bool = x {<,<=,>,>=} a
  //                           / \
  //     proj = {True,False}  /   \ otherproj = {False,True}
  //                         /
  //        this_bool = x {<,<=} b
  //                       / \
  //  fail = {True,False} /   \ success = {False,True}
  //                     /
  //
  // (Second test guaranteed canonicalized, first one may not have
  // been canonicalized yet)
  //
  // into:
  //
  // cond = (x - lo) {<u,<=u,>u,>=u} adjusted_lim
  //                       / \
  //                 fail /   \ success
  //                     /
  //

  // Figure out which of the two tests sets the upper bound and which
  // sets the lower bound if any.
  Node* adjusted_lim = nullptr;
  if (lo_type != nullptr && hi_type != nullptr && hi_type->_lo > lo_type->_hi &&
      hi_type->_hi == max_jint && lo_type->_lo == min_jint && lo_test != BoolTest::ne) {
    assert((dom_bool->_test.is_less() && !proj->_con) ||
           (dom_bool->_test.is_greater() && proj->_con), "incorrect test");

    // this_bool = <
    //   dom_bool = >= (proj = True) or dom_bool = < (proj = False)
    //     x in [a, b[ on the fail (= True) projection, b > a-1 (because of hi_type->_lo > lo_type->_hi test above):
    //     lo = a, hi = b, adjusted_lim = b-a, cond = <u
    //   dom_bool = > (proj = True) or dom_bool = <= (proj = False)
    //     x in ]a, b[ on the fail (= True) projection, b > a:
    //     lo = a+1, hi = b, adjusted_lim = b-a-1, cond = <u
    // this_bool = <=
    //   dom_bool = >= (proj = True) or dom_bool = < (proj = False)
    //     x in [a, b] on the fail (= True) projection, b+1 > a-1:
    //     lo = a, hi = b, adjusted_lim = b-a+1, cond = <u
    //     lo = a, hi = b, adjusted_lim = b-a, cond = <=u doesn't work because b = a - 1 is possible, then b-a = -1
    //   dom_bool = > (proj = True) or dom_bool = <= (proj = False)
    //     x in ]a, b] on the fail (= True) projection b+1 > a:
    //     lo = a+1, hi = b, adjusted_lim = b-a, cond = <u
    //     lo = a+1, hi = b, adjusted_lim = b-a-1, cond = <=u doesn't work because a = b is possible, then b-a-1 = -1

    if (hi_test == BoolTest::lt) {
      if (lo_test == BoolTest::gt || lo_test == BoolTest::le) {
        lo = igvn->transform(new AddINode(lo, igvn->intcon(1)));
      }
    } else if (hi_test == BoolTest::le) {
      if (lo_test == BoolTest::ge || lo_test == BoolTest::lt) {
        adjusted_lim = igvn->transform(new SubINode(hi, lo));
        adjusted_lim = igvn->transform(new AddINode(adjusted_lim, igvn->intcon(1)));
        cond = BoolTest::lt;
      } else if (lo_test == BoolTest::gt || lo_test == BoolTest::le) {
        adjusted_lim = igvn->transform(new SubINode(hi, lo));
        lo = igvn->transform(new AddINode(lo, igvn->intcon(1)));
        cond = BoolTest::lt;
      } else {
        assert(false, "unhandled lo_test: %d", lo_test);
        return false;
      }
    } else {
      assert(igvn->_worklist.member(in(1)) && in(1)->Value(igvn) != igvn->type(in(1)), "unhandled hi_test: %d", hi_test);
      return false;
    }
    // this test was canonicalized
    assert(this_bool->_test.is_less() && fail->_con, "incorrect test");
  } else if (lo_type != nullptr && hi_type != nullptr && lo_type->_lo > hi_type->_hi &&
             lo_type->_hi == max_jint && hi_type->_lo == min_jint && lo_test != BoolTest::ne) {

    // this_bool = <
    //   dom_bool = < (proj = True) or dom_bool = >= (proj = False)
    //     x in [b, a[ on the fail (= False) projection, a > b-1 (because of lo_type->_lo > hi_type->_hi above):
    //     lo = b, hi = a, adjusted_lim = a-b, cond = >=u
    //   dom_bool = <= (proj = True) or dom_bool = > (proj = False)
    //     x in [b, a] on the fail (= False) projection, a+1 > b-1:
    //     lo = b, hi = a, adjusted_lim = a-b+1, cond = >=u
    //     lo = b, hi = a, adjusted_lim = a-b, cond = >u doesn't work because a = b - 1 is possible, then b-a = -1
    // this_bool = <=
    //   dom_bool = < (proj = True) or dom_bool = >= (proj = False)
    //     x in ]b, a[ on the fail (= False) projection, a > b:
    //     lo = b+1, hi = a, adjusted_lim = a-b-1, cond = >=u
    //   dom_bool = <= (proj = True) or dom_bool = > (proj = False)
    //     x in ]b, a] on the fail (= False) projection, a+1 > b:
    //     lo = b+1, hi = a, adjusted_lim = a-b, cond = >=u
    //     lo = b+1, hi = a, adjusted_lim = a-b-1, cond = >u doesn't work because a = b is possible, then b-a-1 = -1

    swap(lo, hi);
    swap(lo_type, hi_type);
    swap(lo_test, hi_test);

    assert((dom_bool->_test.is_less() && proj->_con) ||
           (dom_bool->_test.is_greater() && !proj->_con), "incorrect test");

    cond = (hi_test == BoolTest::le || hi_test == BoolTest::gt) ? BoolTest::gt : BoolTest::ge;

    if (lo_test == BoolTest::lt) {
      if (hi_test == BoolTest::lt || hi_test == BoolTest::ge) {
        cond = BoolTest::ge;
      } else if (hi_test == BoolTest::le || hi_test == BoolTest::gt) {
        adjusted_lim = igvn->transform(new SubINode(hi, lo));
        adjusted_lim = igvn->transform(new AddINode(adjusted_lim, igvn->intcon(1)));
        cond = BoolTest::ge;
      } else {
        assert(false, "unhandled hi_test: %d", hi_test);
        return false;
      }
    } else if (lo_test == BoolTest::le) {
      if (hi_test == BoolTest::lt || hi_test == BoolTest::ge) {
        lo = igvn->transform(new AddINode(lo, igvn->intcon(1)));
        cond = BoolTest::ge;
      } else if (hi_test == BoolTest::le || hi_test == BoolTest::gt) {
        adjusted_lim = igvn->transform(new SubINode(hi, lo));
        lo = igvn->transform(new AddINode(lo, igvn->intcon(1)));
        cond = BoolTest::ge;
      } else {
        assert(false, "unhandled hi_test: %d", hi_test);
        return false;
      }
    } else {
      assert(igvn->_worklist.member(in(1)) && in(1)->Value(igvn) != igvn->type(in(1)), "unhandled lo_test: %d", lo_test);
      return false;
    }
    // this test was canonicalized
    assert(this_bool->_test.is_less() && !fail->_con, "incorrect test");
  } else {
    const TypeInt* failtype = filtered_int_type(igvn, n, proj);
    if (failtype != nullptr) {
      const TypeInt* type2 = filtered_int_type(igvn, n, fail);
      if (type2 != nullptr) {
        if (failtype->filter(type2) == Type::TOP) {
          // previous if determines the result of this if so
          // replace Bool with constant
          igvn->replace_input_of(this, 1, igvn->intcon(success->_con));
          return true;
        }
      }
    }
    return false;
  }

  assert(lo != nullptr && hi != nullptr, "sanity");
  Node* hook = new Node(lo); // Add a use to lo to prevent him from dying
  // Merge the two compares into a single unsigned compare by building (CmpU (n - lo) (hi - lo))
  Node* adjusted_val = igvn->transform(new SubINode(n,  lo));
  if (adjusted_lim == nullptr) {
    adjusted_lim = igvn->transform(new SubINode(hi, lo));
  }
  hook->destruct(igvn);

  if (adjusted_val->is_top() || adjusted_lim->is_top()) {
    return false;
  }

  if (igvn->type(adjusted_lim)->is_int()->_lo < 0 &&
      !igvn->C->post_loop_opts_phase()) {
    // If range check elimination applies to this comparison, it includes code to protect from overflows that may
    // cause the main loop to be skipped entirely. Delay this transformation.
    // Example:
    // for (int i = 0; i < limit; i++) {
    //   if (i < max_jint && i > min_jint) {...
    // }
    // Comparisons folded as:
    // i - min_jint - 1 <u -2
    // when RC applies, main loop limit becomes:
    // min(limit, max(-2 + min_jint + 1, min_jint))
    // = min(limit, min_jint)
    // = min_jint
    if (adjusted_val->outcnt() == 0) {
      igvn->remove_dead_node(adjusted_val);
    }
    if (adjusted_lim->outcnt() == 0) {
      igvn->remove_dead_node(adjusted_lim);
    }
    igvn->C->record_for_post_loop_opts_igvn(this);
    return false;
  }

  Node* newcmp = igvn->transform(new CmpUNode(adjusted_val, adjusted_lim));
  Node* newbool = igvn->transform(new BoolNode(newcmp, cond));

  igvn->replace_input_of(dom_iff, 1, igvn->intcon(proj->_con));
  igvn->replace_input_of(this, 1, newbool);

  return true;
}

// Merge the branches that trap for this If and the dominating If into
// a single region that branches to the uncommon trap for the
// dominating If
Node* IfNode::merge_uncommon_traps(ProjNode* proj, ProjNode* success, ProjNode* fail, PhaseIterGVN* igvn) {
  Node* res = this;
  assert(success->in(0) == this, "bad projection");

  ProjNode* otherproj = proj->other_if_proj();

  CallStaticJavaNode* unc = success->is_uncommon_trap_proj();
  CallStaticJavaNode* dom_unc = otherproj->is_uncommon_trap_proj();

  if (unc != dom_unc) {
    Node* r = new RegionNode(3);

    r->set_req(1, otherproj);
    r->set_req(2, success);
    r = igvn->transform(r);
    assert(r->is_Region(), "can't go away");

    // Make both If trap at the state of the first If: once the CmpI
    // nodes are merged, if we trap we don't know which of the CmpI
    // nodes would have caused the trap so we have to restart
    // execution at the first one
    igvn->replace_input_of(dom_unc, 0, r);
    igvn->replace_input_of(unc, 0, igvn->C->top());
  }
  int trap_request = dom_unc->uncommon_trap_request();
  Deoptimization::DeoptReason reason = Deoptimization::trap_request_reason(trap_request);
  Deoptimization::DeoptAction action = Deoptimization::trap_request_action(trap_request);

  int flip_test = 0;
  Node* l = nullptr;
  Node* r = nullptr;

  if (success->in(0)->as_If()->range_check_trap_proj(flip_test, l, r) != nullptr) {
    // If this looks like a range check, change the trap to
    // Reason_range_check so the compiler recognizes it as a range
    // check and applies the corresponding optimizations
    trap_request = Deoptimization::make_trap_request(Deoptimization::Reason_range_check, action);

    improve_address_types(l, r, fail, igvn);

    res = igvn->transform(new RangeCheckNode(in(0), in(1), _prob, _fcnt));
  } else if (unc != dom_unc) {
    // If we trap we won't know what CmpI would have caused the trap
    // so use a special trap reason to mark this pair of CmpI nodes as
    // bad candidate for folding. On recompilation we won't fold them
    // and we may trap again but this time we'll know what branch
    // traps
    trap_request = Deoptimization::make_trap_request(Deoptimization::Reason_unstable_fused_if, action);
  }
  igvn->replace_input_of(dom_unc, TypeFunc::Parms, igvn->intcon(trap_request));
  return res;
}

// If we are turning 2 CmpI nodes into a CmpU that follows the pattern
// of a rangecheck on index i, on 64 bit the compares may be followed
// by memory accesses using i as index. In that case, the CmpU tells
// us something about the values taken by i that can help the compiler
// (see Compile::conv_I2X_index())
void IfNode::improve_address_types(Node* l, Node* r, ProjNode* fail, PhaseIterGVN* igvn) {
#ifdef _LP64
  ResourceMark rm;
  Node_Stack stack(2);

  assert(r->Opcode() == Op_LoadRange, "unexpected range check");
  const TypeInt* array_size = igvn->type(r)->is_int();

  stack.push(l, 0);

  while(stack.size() > 0) {
    Node* n = stack.node();
    uint start = stack.index();

    uint i = start;
    for (; i < n->outcnt(); i++) {
      Node* use = n->raw_out(i);
      if (stack.size() == 1) {
        if (use->Opcode() == Op_ConvI2L) {
          const TypeLong* bounds = use->as_Type()->type()->is_long();
          if (bounds->_lo <= array_size->_lo && bounds->_hi >= array_size->_hi &&
              (bounds->_lo != array_size->_lo || bounds->_hi != array_size->_hi)) {
            stack.set_index(i+1);
            stack.push(use, 0);
            break;
          }
        }
      } else if (use->is_Mem()) {
        Node* ctrl = use->in(0);
        for (int i = 0; i < 10 && ctrl != nullptr && ctrl != fail; i++) {
          ctrl = up_one_dom(ctrl);
        }
        if (ctrl == fail) {
          Node* init_n = stack.node_at(1);
          assert(init_n->Opcode() == Op_ConvI2L, "unexpected first node");
          // Create a new narrow ConvI2L node that is dependent on the range check
          Node* new_n = igvn->C->conv_I2X_index(igvn, l, array_size, fail);

          // The type of the ConvI2L may be widen and so the new
          // ConvI2L may not be better than an existing ConvI2L
          if (new_n != init_n) {
            for (uint j = 2; j < stack.size(); j++) {
              Node* n = stack.node_at(j);
              Node* clone = n->clone();
              int rep = clone->replace_edge(init_n, new_n, igvn);
              assert(rep > 0, "can't find expected node?");
              clone = igvn->transform(clone);
              init_n = n;
              new_n = clone;
            }
            igvn->hash_delete(use);
            int rep = use->replace_edge(init_n, new_n, igvn);
            assert(rep > 0, "can't find expected node?");
            igvn->transform(use);
            if (init_n->outcnt() == 0) {
              igvn->_worklist.push(init_n);
            }
          }
        }
      } else if (use->in(0) == nullptr && (igvn->type(use)->isa_long() ||
                                        igvn->type(use)->isa_ptr())) {
        stack.set_index(i+1);
        stack.push(use, 0);
        break;
      }
    }
    if (i == n->outcnt()) {
      stack.pop();
    }
  }
#endif
}

bool IfNode::is_cmp_with_loadrange(ProjNode* proj) {
  if (in(1) != nullptr &&
      in(1)->in(1) != nullptr &&
      in(1)->in(1)->in(2) != nullptr) {
    Node* other = in(1)->in(1)->in(2);
    if (other->Opcode() == Op_LoadRange &&
        ((other->in(0) != nullptr && other->in(0) == proj) ||
         (other->in(0) == nullptr &&
          other->in(2) != nullptr &&
          other->in(2)->is_AddP() &&
          other->in(2)->in(1) != nullptr &&
          other->in(2)->in(1)->Opcode() == Op_CastPP &&
          other->in(2)->in(1)->in(0) == proj))) {
      return true;
    }
  }
  return false;
}

bool IfNode::is_null_check(ProjNode* proj, PhaseIterGVN* igvn) {
  Node* other = in(1)->in(1)->in(2);
  if (other->in(MemNode::Address) != nullptr &&
      proj->in(0)->in(1) != nullptr &&
      proj->in(0)->in(1)->is_Bool() &&
      proj->in(0)->in(1)->in(1) != nullptr &&
      proj->in(0)->in(1)->in(1)->Opcode() == Op_CmpP &&
      proj->in(0)->in(1)->in(1)->in(2) != nullptr &&
      proj->in(0)->in(1)->in(1)->in(1) == other->in(MemNode::Address)->in(AddPNode::Address)->uncast() &&
      igvn->type(proj->in(0)->in(1)->in(1)->in(2)) == TypePtr::NULL_PTR) {
    return true;
  }
  return false;
}

// Check that the If that is in between the 2 integer comparisons has
// no side effect
bool IfNode::is_side_effect_free_test(ProjNode* proj, PhaseIterGVN* igvn) {
  if (proj == nullptr) {
    return false;
  }
  CallStaticJavaNode* unc = proj->is_uncommon_trap_if_pattern();
  if (unc != nullptr && proj->outcnt() <= 2) {
    if (proj->outcnt() == 1 ||
        // Allow simple null check from LoadRange
        (is_cmp_with_loadrange(proj) && is_null_check(proj, igvn))) {
      CallStaticJavaNode* unc = proj->is_uncommon_trap_if_pattern();
      CallStaticJavaNode* dom_unc = proj->in(0)->in(0)->as_Proj()->is_uncommon_trap_if_pattern();
      assert(dom_unc != nullptr, "is_uncommon_trap_if_pattern returned null");

      // reroute_side_effect_free_unc changes the state of this
      // uncommon trap to restart execution at the previous
      // CmpI. Check that this change in a previous compilation didn't
      // cause too many traps.
      int trap_request = unc->uncommon_trap_request();
      Deoptimization::DeoptReason reason = Deoptimization::trap_request_reason(trap_request);

      if (igvn->C->too_many_traps(dom_unc->jvms()->method(), dom_unc->jvms()->bci(), reason)) {
        return false;
      }

      if (!is_dominator_unc(dom_unc, unc)) {
        return false;
      }

      return true;
    }
  }
  return false;
}

// Make the If between the 2 integer comparisons trap at the state of
// the first If: the last CmpI is the one replaced by a CmpU and the
// first CmpI is eliminated, so the test between the 2 CmpI nodes
// won't be guarded by the first CmpI anymore. It can trap in cases
// where the first CmpI would have prevented it from executing: on a
// trap, we need to restart execution at the state of the first CmpI
void IfNode::reroute_side_effect_free_unc(ProjNode* proj, ProjNode* dom_proj, PhaseIterGVN* igvn) {
  CallStaticJavaNode* dom_unc = dom_proj->is_uncommon_trap_if_pattern();
  ProjNode* otherproj = proj->other_if_proj();
  CallStaticJavaNode* unc = proj->is_uncommon_trap_if_pattern();
  Node* call_proj = dom_unc->unique_ctrl_out();
  Node* halt = call_proj->unique_ctrl_out();

  Node* new_unc = dom_unc->clone();
  call_proj = call_proj->clone();
  halt = halt->clone();
  Node* c = otherproj->clone();

  c = igvn->transform(c);
  new_unc->set_req(TypeFunc::Parms, unc->in(TypeFunc::Parms));
  new_unc->set_req(0, c);
  new_unc = igvn->transform(new_unc);
  call_proj->set_req(0, new_unc);
  call_proj = igvn->transform(call_proj);
  halt->set_req(0, call_proj);
  halt = igvn->transform(halt);

  igvn->replace_node(otherproj, igvn->C->top());
  igvn->C->root()->add_req(halt);
}

Node* IfNode::fold_compares(PhaseIterGVN* igvn) {
  if (Opcode() != Op_If) return nullptr;

  if (cmpi_folds(igvn)) {
    Node* ctrl = in(0);
    if (is_ctrl_folds(ctrl, igvn)) {
      // A integer comparison immediately dominated by another integer
      // comparison
      ProjNode* success = nullptr;
      ProjNode* fail = nullptr;
      ProjNode* dom_cmp = ctrl->as_Proj();
      if (has_shared_region(dom_cmp, success, fail) &&
          // Next call modifies graph so must be last
          fold_compares_helper(dom_cmp, success, fail, igvn)) {
        return this;
      }
      if (has_only_uncommon_traps(dom_cmp, success, fail, igvn) &&
          // Next call modifies graph so must be last
          fold_compares_helper(dom_cmp, success, fail, igvn)) {
        return merge_uncommon_traps(dom_cmp, success, fail, igvn);
      }
      return nullptr;
    } else if (ctrl->in(0) != nullptr &&
               ctrl->in(0)->in(0) != nullptr) {
      ProjNode* success = nullptr;
      ProjNode* fail = nullptr;
      Node* dom = ctrl->in(0)->in(0);
      ProjNode* dom_cmp = dom->isa_Proj();
      ProjNode* other_cmp = ctrl->isa_Proj();

      // Check if it's an integer comparison dominated by another
      // integer comparison with another test in between
      if (is_ctrl_folds(dom, igvn) &&
          has_only_uncommon_traps(dom_cmp, success, fail, igvn) &&
          is_side_effect_free_test(other_cmp, igvn) &&
          // Next call modifies graph so must be last
          fold_compares_helper(dom_cmp, success, fail, igvn)) {
        reroute_side_effect_free_unc(other_cmp, dom_cmp, igvn);
        return merge_uncommon_traps(dom_cmp, success, fail, igvn);
      }
    }
  }
  return nullptr;
}

//------------------------------remove_useless_bool----------------------------
// Check for people making a useless boolean: things like
// if( (x < y ? true : false) ) { ... }
// Replace with if( x < y ) { ... }
static Node *remove_useless_bool(IfNode *iff, PhaseGVN *phase) {
  Node *i1 = iff->in(1);
  if( !i1->is_Bool() ) return nullptr;
  BoolNode *bol = i1->as_Bool();

  Node *cmp = bol->in(1);
  if( cmp->Opcode() != Op_CmpI ) return nullptr;

  // Must be comparing against a bool
  const Type *cmp2_t = phase->type( cmp->in(2) );
  if( cmp2_t != TypeInt::ZERO &&
      cmp2_t != TypeInt::ONE )
    return nullptr;

  // Find a prior merge point merging the boolean
  i1 = cmp->in(1);
  if( !i1->is_Phi() ) return nullptr;
  PhiNode *phi = i1->as_Phi();
  if( phase->type( phi ) != TypeInt::BOOL )
    return nullptr;

  // Check for diamond pattern
  int true_path = phi->is_diamond_phi();
  if( true_path == 0 ) return nullptr;

  // Make sure that iff and the control of the phi are different. This
  // should really only happen for dead control flow since it requires
  // an illegal cycle.
  if (phi->in(0)->in(1)->in(0) == iff) return nullptr;

  // phi->region->if_proj->ifnode->bool->cmp
  BoolNode *bol2 = phi->in(0)->in(1)->in(0)->in(1)->as_Bool();

  // Now get the 'sense' of the test correct so we can plug in
  // either iff2->in(1) or its complement.
  int flip = 0;
  if( bol->_test._test == BoolTest::ne ) flip = 1-flip;
  else if( bol->_test._test != BoolTest::eq ) return nullptr;
  if( cmp2_t == TypeInt::ZERO ) flip = 1-flip;

  const Type *phi1_t = phase->type( phi->in(1) );
  const Type *phi2_t = phase->type( phi->in(2) );
  // Check for Phi(0,1) and flip
  if( phi1_t == TypeInt::ZERO ) {
    if( phi2_t != TypeInt::ONE ) return nullptr;
    flip = 1-flip;
  } else {
    // Check for Phi(1,0)
    if( phi1_t != TypeInt::ONE  ) return nullptr;
    if( phi2_t != TypeInt::ZERO ) return nullptr;
  }
  if( true_path == 2 ) {
    flip = 1-flip;
  }

  Node* new_bol = (flip ? phase->transform( bol2->negate(phase) ) : bol2);
  assert(new_bol != iff->in(1), "must make progress");
  iff->set_req_X(1, new_bol, phase);
  // Intervening diamond probably goes dead
  phase->C->set_major_progress();
  return iff;
}

static IfNode* idealize_test(PhaseGVN* phase, IfNode* iff);

struct RangeCheck {
  IfProjNode* ctl;
  jint off;
};

Node* IfNode::Ideal_common(PhaseGVN *phase, bool can_reshape) {
  if (remove_dead_region(phase, can_reshape))  return this;
  // No Def-Use info?
  if (!can_reshape)  return nullptr;

  // Don't bother trying to transform a dead if
  if (in(0)->is_top())  return nullptr;
  // Don't bother trying to transform an if with a dead test
  if (in(1)->is_top())  return nullptr;
  // Another variation of a dead test
  if (in(1)->is_Con())  return nullptr;
  // Another variation of a dead if
  if (outcnt() < 2)  return nullptr;

  // Canonicalize the test.
  Node* idt_if = idealize_test(phase, this);
  if (idt_if != nullptr)  return idt_if;

  // Try to split the IF
  PhaseIterGVN *igvn = phase->is_IterGVN();
  Node *s = split_if(this, igvn);
  if (s != nullptr)  return s;

  return NodeSentinel;
}

//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.  Strip out
// control copies
Node* IfNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  Node* res = Ideal_common(phase, can_reshape);
  if (res != NodeSentinel) {
    return res;
  }

  // Check for people making a useless boolean: things like
  // if( (x < y ? true : false) ) { ... }
  // Replace with if( x < y ) { ... }
  Node* bol2 = remove_useless_bool(this, phase);
  if (bol2) return bol2;

  if (in(0) == nullptr) return nullptr;     // Dead loop?

  PhaseIterGVN* igvn = phase->is_IterGVN();
  Node* result = fold_compares(igvn);
  if (result != nullptr) {
    return result;
  }

  // Scan for an equivalent test
  int dist = 4;               // Cutoff limit for search
  if (is_If() && in(1)->is_Bool()) {
    Node* cmp = in(1)->in(1);
    if (cmp->Opcode() == Op_CmpP &&
        cmp->in(2) != nullptr && // make sure cmp is not already dead
        cmp->in(2)->bottom_type() == TypePtr::NULL_PTR) {
      dist = 64;              // Limit for null-pointer scans
    }
  }

  Node* prev_dom = search_identical(dist, igvn);

  if (prev_dom != nullptr) {
    // Dominating CountedLoopEnd (left over from some now dead loop) will become the new loop exit. Outer strip mined
    // loop will go away. Mark this loop as no longer strip mined.
    if (is_CountedLoopEnd()) {
      CountedLoopNode* counted_loop_node = as_CountedLoopEnd()->loopnode();
      if (counted_loop_node != nullptr) {
        counted_loop_node->clear_strip_mined();
      }
    }
    // Replace dominated IfNode
    return dominated_by(prev_dom, igvn, false);
  }

  return simple_subsuming(igvn);
}

//------------------------------dominated_by-----------------------------------
Node* IfNode::dominated_by(Node* prev_dom, PhaseIterGVN* igvn, bool pin_array_access_nodes) {
#ifndef PRODUCT
  if (TraceIterativeGVN) {
    tty->print("   Removing IfNode: "); this->dump();
  }
#endif

  igvn->hash_delete(this);      // Remove self to prevent spurious V-N
  Node *idom = in(0);
  // Need opcode to decide which way 'this' test goes
  int prev_op = prev_dom->Opcode();
  Node *top = igvn->C->top(); // Shortcut to top

  // Now walk the current IfNode's projections.
  // Loop ends when 'this' has no more uses.
  for (DUIterator_Last imin, i = last_outs(imin); i >= imin; --i) {
    Node *ifp = last_out(i);     // Get IfTrue/IfFalse
    igvn->add_users_to_worklist(ifp);
    // Check which projection it is and set target.
    // Data-target is either the dominating projection of the same type
    // or TOP if the dominating projection is of opposite type.
    // Data-target will be used as the new control edge for the non-CFG
    // nodes like Casts and Loads.
    Node *data_target = (ifp->Opcode() == prev_op) ? prev_dom : top;
    // Control-target is just the If's immediate dominator or TOP.
    Node *ctrl_target = (ifp->Opcode() == prev_op) ?     idom : top;

    // For each child of an IfTrue/IfFalse projection, reroute.
    // Loop ends when projection has no more uses.
    for (DUIterator_Last jmin, j = ifp->last_outs(jmin); j >= jmin; --j) {
      Node* s = ifp->last_out(j);   // Get child of IfTrue/IfFalse
      if (s->depends_only_on_test() && igvn->no_dependent_zero_check(s)) {
        // For control producers.
        // Do not rewire Div and Mod nodes which could have a zero divisor to avoid skipping their zero check.
        igvn->replace_input_of(s, 0, data_target); // Move child to data-target
        if (pin_array_access_nodes && data_target != top) {
          // As a result of range check smearing, Loads and range check Cast nodes that are control dependent on this
          // range check (that is about to be removed) now depend on multiple dominating range checks. After the removal
          // of this range check, these control dependent nodes end up at the lowest/nearest dominating check in the
          // graph. To ensure that these Loads/Casts do not float above any of the dominating checks (even when the
          // lowest dominating check is later replaced by yet another dominating check), we need to pin them at the
          // lowest dominating check.
          Node* clone = s->pin_array_access_node();
          if (clone != nullptr) {
            clone = igvn->transform(clone);
            igvn->replace_node(s, clone);
          }
        }
      } else {
        // Find the control input matching this def-use edge.
        // For Regions it may not be in slot 0.
        uint l;
        for (l = 0; s->in(l) != ifp; l++) { }
        igvn->replace_input_of(s, l, ctrl_target);
      }
    } // End for each child of a projection

    igvn->remove_dead_node(ifp);
  } // End for each IfTrue/IfFalse child of If

  // Kill the IfNode
  igvn->remove_dead_node(this);

  // Must return either the original node (now dead) or a new node
  // (Do not return a top here, since that would break the uniqueness of top.)
  return new ConINode(TypeInt::ZERO);
}

Node* IfNode::search_identical(int dist, PhaseIterGVN* igvn) {
  // Setup to scan up the CFG looking for a dominating test
  Node* dom = in(0);
  Node* prev_dom = this;
  int op = Opcode();
  // Search up the dominator tree for an If with an identical test
  while (dom->Opcode() != op ||  // Not same opcode?
         !same_condition(dom, igvn) ||  // Not same input 1?
         prev_dom->in(0) != dom) {  // One path of test does not dominate?
    if (dist < 0) return nullptr;

    dist--;
    prev_dom = dom;
    dom = up_one_dom(dom);
    if (!dom) return nullptr;
  }

  // Check that we did not follow a loop back to ourselves
  if (this == dom) {
    return nullptr;
  }

#ifndef PRODUCT
  if (dist > 2) { // Add to count of null checks elided
    explicit_null_checks_elided++;
  }
#endif

  return prev_dom;
}

bool IfNode::same_condition(const Node* dom, PhaseIterGVN* igvn) const {
  Node* dom_bool = dom->in(1);
  Node* this_bool = in(1);
  if (dom_bool == this_bool) {
    return true;
  }

  if (dom_bool == nullptr || !dom_bool->is_Bool() ||
      this_bool == nullptr || !this_bool->is_Bool()) {
    return false;
  }
  Node* dom_cmp = dom_bool->in(1);
  Node* this_cmp = this_bool->in(1);

  // If the comparison is a subtype check, then SubTypeCheck nodes may have profile data attached to them and may be
  // different nodes even-though they perform the same subtype check
  if (dom_cmp == nullptr || !dom_cmp->is_SubTypeCheck() ||
      this_cmp == nullptr || !this_cmp->is_SubTypeCheck()) {
    return false;
  }

  if (dom_cmp->in(1) != this_cmp->in(1) ||
      dom_cmp->in(2) != this_cmp->in(2) ||
      dom_bool->as_Bool()->_test._test != this_bool->as_Bool()->_test._test) {
    return false;
  }

  return true;
}


static int subsuming_bool_test_encode(Node*);

// Check if dominating test is subsuming 'this' one.
//
//              cmp
//              / \
//     (r1)  bool  \
//            /    bool (r2)
//    (dom) if       \
//            \       )
//    (pre)  if[TF]  /
//               \  /
//                if (this)
//   \r1
//  r2\  eqT  eqF  neT  neF  ltT  ltF  leT  leF  gtT  gtF  geT  geF
//  eq    t    f    f    t    f    -    -    f    f    -    -    f
//  ne    f    t    t    f    t    -    -    t    t    -    -    t
//  lt    f    -    -    f    t    f    -    f    f    -    f    t
//  le    t    -    -    t    t    -    t    f    f    t    -    t
//  gt    f    -    -    f    f    -    f    t    t    f    -    f
//  ge    t    -    -    t    f    t    -    t    t    -    t    f
//
Node* IfNode::simple_subsuming(PhaseIterGVN* igvn) {
  // Table encoding: N/A (na), True-branch (tb), False-branch (fb).
  static enum { na, tb, fb } s_short_circuit_map[6][12] = {
  /*rel: eq+T eq+F ne+T ne+F lt+T lt+F le+T le+F gt+T gt+F ge+T ge+F*/
  /*eq*/{ tb,  fb,  fb,  tb,  fb,  na,  na,  fb,  fb,  na,  na,  fb },
  /*ne*/{ fb,  tb,  tb,  fb,  tb,  na,  na,  tb,  tb,  na,  na,  tb },
  /*lt*/{ fb,  na,  na,  fb,  tb,  fb,  na,  fb,  fb,  na,  fb,  tb },
  /*le*/{ tb,  na,  na,  tb,  tb,  na,  tb,  fb,  fb,  tb,  na,  tb },
  /*gt*/{ fb,  na,  na,  fb,  fb,  na,  fb,  tb,  tb,  fb,  na,  fb },
  /*ge*/{ tb,  na,  na,  tb,  fb,  tb,  na,  tb,  tb,  na,  tb,  fb }};

  Node* pre = in(0);
  if (!pre->is_IfTrue() && !pre->is_IfFalse()) {
    return nullptr;
  }
  Node* dom = pre->in(0);
  if (!dom->is_If()) {
    return nullptr;
  }
  Node* bol = in(1);
  if (!bol->is_Bool()) {
    return nullptr;
  }
  Node* cmp = in(1)->in(1);
  if (!cmp->is_Cmp()) {
    return nullptr;
  }

  if (!dom->in(1)->is_Bool()) {
    return nullptr;
  }
  if (dom->in(1)->in(1) != cmp) {  // Not same cond?
    return nullptr;
  }

  int drel = subsuming_bool_test_encode(dom->in(1));
  int trel = subsuming_bool_test_encode(bol);
  int bout = pre->is_IfFalse() ? 1 : 0;

  if (drel < 0 || trel < 0) {
    return nullptr;
  }
  int br = s_short_circuit_map[trel][2*drel+bout];
  if (br == na) {
    return nullptr;
  }
#ifndef PRODUCT
  if (TraceIterativeGVN) {
    tty->print("   Subsumed IfNode: "); dump();
  }
#endif
  // Replace condition with constant True(1)/False(0).
  bool is_always_true = br == tb;
  set_req(1, igvn->intcon(is_always_true ? 1 : 0));

  // Update any data dependencies to the directly dominating test. This subsumed test is not immediately removed by igvn
  // and therefore subsequent optimizations might miss these data dependencies otherwise. There might be a dead loop
  // ('always_taken_proj' == 'pre') that is cleaned up later. Skip this case to make the iterator work properly.
  Node* always_taken_proj = proj_out(is_always_true);
  if (always_taken_proj != pre) {
    for (DUIterator_Fast imax, i = always_taken_proj->fast_outs(imax); i < imax; i++) {
      Node* u = always_taken_proj->fast_out(i);
      if (!u->is_CFG()) {
        igvn->replace_input_of(u, 0, pre);
        --i;
        --imax;
      }
    }
  }

  if (bol->outcnt() == 0) {
    igvn->remove_dead_node(bol);    // Kill the BoolNode.
  }
  return this;
}

// Map BoolTest to local table encoding. The BoolTest (e)numerals
//   { eq = 0, ne = 4, le = 5, ge = 7, lt = 3, gt = 1 }
// are mapped to table indices, while the remaining (e)numerals in BoolTest
//   { overflow = 2, no_overflow = 6, never = 8, illegal = 9 }
// are ignored (these are not modeled in the table).
//
static int subsuming_bool_test_encode(Node* node) {
  precond(node->is_Bool());
  BoolTest::mask x = node->as_Bool()->_test._test;
  switch (x) {
    case BoolTest::eq: return 0;
    case BoolTest::ne: return 1;
    case BoolTest::lt: return 2;
    case BoolTest::le: return 3;
    case BoolTest::gt: return 4;
    case BoolTest::ge: return 5;
    case BoolTest::overflow:
    case BoolTest::no_overflow:
    case BoolTest::never:
    case BoolTest::illegal:
    default:
      return -1;
  }
}

//------------------------------Identity---------------------------------------
// If the test is constant & we match, then we are the input Control
Node* IfProjNode::Identity(PhaseGVN* phase) {
  // Can only optimize if cannot go the other way
  const TypeTuple *t = phase->type(in(0))->is_tuple();
  if (t == TypeTuple::IFNEITHER || (always_taken(t) &&
       // During parsing (GVN) we don't remove dead code aggressively.
       // Cut off dead branch and let PhaseRemoveUseless take care of it.
      (!phase->is_IterGVN() ||
       // During IGVN, first wait for the dead branch to be killed.
       // Otherwise, the IfNode's control will have two control uses (the IfNode
       // that doesn't go away because it still has uses and this branch of the
       // If) which breaks other optimizations. Node::has_special_unique_user()
       // will cause this node to be reprocessed once the dead branch is killed.
       in(0)->outcnt() == 1))) {
    // IfNode control
    if (in(0)->is_BaseCountedLoopEnd()) {
      // CountedLoopEndNode may be eliminated by if subsuming, replace CountedLoopNode with LoopNode to
      // avoid mismatching between CountedLoopNode and CountedLoopEndNode in the following optimization.
      Node* head = unique_ctrl_out_or_null();
      if (head != nullptr && head->is_BaseCountedLoop() && head->in(LoopNode::LoopBackControl) == this) {
        Node* new_head = new LoopNode(head->in(LoopNode::EntryControl), this);
        phase->is_IterGVN()->register_new_node_with_optimizer(new_head);
        phase->is_IterGVN()->replace_node(head, new_head);
      }
    }
    return in(0)->in(0);
  }
  // no progress
  return this;
}

bool IfNode::is_zero_trip_guard() const {
  if (in(1)->is_Bool() && in(1)->in(1)->is_Cmp()) {
    return in(1)->in(1)->in(1)->Opcode() == Op_OpaqueZeroTripGuard;
  }
  return false;
}

void IfProjNode::pin_array_access_nodes(PhaseIterGVN* igvn) {
  for (DUIterator i = outs(); has_out(i); i++) {
    Node* u = out(i);
    if (!u->depends_only_on_test()) {
      continue;
    }
    Node* clone = u->pin_array_access_node();
    if (clone != nullptr) {
      clone = igvn->transform(clone);
      assert(clone != u, "shouldn't common");
      igvn->replace_node(u, clone);
      --i;
    }
  }
}

#ifndef PRODUCT
void IfNode::dump_spec(outputStream* st) const {
  switch (_assertion_predicate_type) {
    case AssertionPredicateType::InitValue:
      st->print("#Init Value Assertion Predicate  ");
      break;
    case AssertionPredicateType::LastValue:
      st->print("#Last Value Assertion Predicate  ");
      break;
    case AssertionPredicateType::FinalIv:
      st->print("#Final IV Assertion Predicate  ");
      break;
    case AssertionPredicateType::None:
      // No Assertion Predicate
      break;
    default:
      fatal("Unknown Assertion Predicate type");
  }
  st->print("P=%f, C=%f", _prob, _fcnt);
}
#endif // NOT PRODUCT

//------------------------------idealize_test----------------------------------
// Try to canonicalize tests better.  Peek at the Cmp/Bool/If sequence and
// come up with a canonical sequence.  Bools getting 'eq', 'gt' and 'ge' forms
// converted to 'ne', 'le' and 'lt' forms.  IfTrue/IfFalse get swapped as
// needed.
static IfNode* idealize_test(PhaseGVN* phase, IfNode* iff) {
  assert(iff->in(0) != nullptr, "If must be live");

  if (iff->outcnt() != 2)  return nullptr; // Malformed projections.
  Node* old_if_f = iff->proj_out(false);
  Node* old_if_t = iff->proj_out(true);

  // CountedLoopEnds want the back-control test to be TRUE, regardless of
  // whether they are testing a 'gt' or 'lt' condition.  The 'gt' condition
  // happens in count-down loops
  if (iff->is_BaseCountedLoopEnd())  return nullptr;
  if (!iff->in(1)->is_Bool())  return nullptr; // Happens for partially optimized IF tests
  BoolNode *b = iff->in(1)->as_Bool();
  BoolTest bt = b->_test;
  // Test already in good order?
  if( bt.is_canonical() )
    return nullptr;

  // Flip test to be canonical.  Requires flipping the IfFalse/IfTrue and
  // cloning the IfNode.
  Node* new_b = phase->transform( new BoolNode(b->in(1), bt.negate()) );
  if( !new_b->is_Bool() ) return nullptr;
  b = new_b->as_Bool();

  PhaseIterGVN *igvn = phase->is_IterGVN();
  assert( igvn, "Test is not canonical in parser?" );

  // The IF node never really changes, but it needs to be cloned
  iff = iff->clone()->as_If();
  iff->set_req(1, b);
  iff->_prob = 1.0-iff->_prob;

  Node *prior = igvn->hash_find_insert(iff);
  if( prior ) {
    igvn->remove_dead_node(iff);
    iff = (IfNode*)prior;
  } else {
    // Cannot call transform on it just yet
    igvn->set_type_bottom(iff);
  }
  igvn->_worklist.push(iff);

  // Now handle projections.  Cloning not required.
  Node* new_if_f = (Node*)(new IfFalseNode( iff ));
  Node* new_if_t = (Node*)(new IfTrueNode ( iff ));

  igvn->register_new_node_with_optimizer(new_if_f);
  igvn->register_new_node_with_optimizer(new_if_t);
  // Flip test, so flip trailing control
  igvn->replace_node(old_if_f, new_if_t);
  igvn->replace_node(old_if_t, new_if_f);

  // Progress
  return iff;
}

Node* RangeCheckNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  Node* res = Ideal_common(phase, can_reshape);
  if (res != NodeSentinel) {
    return res;
  }

  PhaseIterGVN *igvn = phase->is_IterGVN();
  // Setup to scan up the CFG looking for a dominating test
  Node* prev_dom = this;

  // Check for range-check vs other kinds of tests
  Node* index1;
  Node* range1;
  jint offset1;
  int flip1 = is_range_check(range1, index1, offset1);
  if (flip1) {
    Node* dom = in(0);
    // Try to remove extra range checks.  All 'up_one_dom' gives up at merges
    // so all checks we inspect post-dominate the top-most check we find.
    // If we are going to fail the current check and we reach the top check
    // then we are guaranteed to fail, so just start interpreting there.
    // We 'expand' the top 3 range checks to include all post-dominating
    // checks.
    //
    // Example:
    // a[i+x] // (1) 1 < x < 6
    // a[i+3] // (2)
    // a[i+4] // (3)
    // a[i+6] // max = max of all constants
    // a[i+2]
    // a[i+1] // min = min of all constants
    //
    // If x < 3:
    //   (1) a[i+x]: Leave unchanged
    //   (2) a[i+3]: Replace with a[i+max] = a[i+6]: i+x < i+3 <= i+6  -> (2) is covered
    //   (3) a[i+4]: Replace with a[i+min] = a[i+1]: i+1 < i+4 <= i+6  -> (3) and all following checks are covered
    //   Remove all other a[i+c] checks
    //
    // If x >= 3:
    //   (1) a[i+x]: Leave unchanged
    //   (2) a[i+3]: Replace with a[i+min] = a[i+1]: i+1 < i+3 <= i+x  -> (2) is covered
    //   (3) a[i+4]: Replace with a[i+max] = a[i+6]: i+1 < i+4 <= i+6  -> (3) and all following checks are covered
    //   Remove all other a[i+c] checks
    //
    // We only need the top 2 range checks if x is the min or max of all constants.
    //
    // This, however, only works if the interval [i+min,i+max] is not larger than max_int (i.e. abs(max - min) < max_int):
    // The theoretical max size of an array is max_int with:
    // - Valid index space: [0,max_int-1]
    // - Invalid index space: [max_int,-1] // max_int, min_int, min_int - 1 ..., -1
    //
    // The size of the consecutive valid index space is smaller than the size of the consecutive invalid index space.
    // If we choose min and max in such a way that:
    // - abs(max - min) < max_int
    // - i+max and i+min are inside the valid index space
    // then all indices [i+min,i+max] must be in the valid index space. Otherwise, the invalid index space must be
    // smaller than the valid index space which is never the case for any array size.
    //
    // Choosing a smaller array size only makes the valid index space smaller and the invalid index space larger and
    // the argument above still holds.
    //
    // Note that the same optimization with the same maximal accepted interval size can also be found in C1.
    const jlong maximum_number_of_min_max_interval_indices = (jlong)max_jint;

    // The top 3 range checks seen
    const int NRC = 3;
    RangeCheck prev_checks[NRC];
    int nb_checks = 0;

    // Low and high offsets seen so far
    jint off_lo = offset1;
    jint off_hi = offset1;

    bool found_immediate_dominator = false;

    // Scan for the top checks and collect range of offsets
    for (int dist = 0; dist < 999; dist++) { // Range-Check scan limit
      if (dom->Opcode() == Op_RangeCheck &&  // Not same opcode?
          prev_dom->in(0) == dom) { // One path of test does dominate?
        if (dom == this) return nullptr; // dead loop
        // See if this is a range check
        Node* index2;
        Node* range2;
        jint offset2;
        int flip2 = dom->as_RangeCheck()->is_range_check(range2, index2, offset2);
        // See if this is a _matching_ range check, checking against
        // the same array bounds.
        if (flip2 == flip1 && range2 == range1 && index2 == index1 &&
            dom->outcnt() == 2) {
          if (nb_checks == 0 && dom->in(1) == in(1)) {
            // Found an immediately dominating test at the same offset.
            // This kind of back-to-back test can be eliminated locally,
            // and there is no need to search further for dominating tests.
            assert(offset2 == offset1, "Same test but different offsets");
            found_immediate_dominator = true;
            break;
          }

          // "x - y" -> must add one to the difference for number of elements in [x,y]
          const jlong diff = (jlong)MIN2(offset2, off_lo) - (jlong)MAX2(offset2, off_hi);
          if (ABS(diff) < maximum_number_of_min_max_interval_indices) {
            // Gather expanded bounds
            off_lo = MIN2(off_lo, offset2);
            off_hi = MAX2(off_hi, offset2);
            // Record top NRC range checks
            prev_checks[nb_checks % NRC].ctl = prev_dom->as_IfProj();
            prev_checks[nb_checks % NRC].off = offset2;
            nb_checks++;
          }
        }
      }
      prev_dom = dom;
      dom = up_one_dom(dom);
      if (!dom) break;
    }

    if (!found_immediate_dominator) {
      // Attempt to widen the dominating range check to cover some later
      // ones.  Since range checks "fail" by uncommon-trapping to the
      // interpreter, widening a check can make us speculatively enter
      // the interpreter.  If we see range-check deopt's, do not widen!
      if (!phase->C->allow_range_check_smearing())  return nullptr;

      if (can_reshape && !phase->C->post_loop_opts_phase()) {
        // We are about to perform range check smearing (i.e. remove this RangeCheck if it is dominated by
        // a series of RangeChecks which have a range that covers this RangeCheck). This can cause array access nodes to
        // be pinned. We want to avoid that and first allow range check elimination a chance to remove the RangeChecks
        // from loops. Hence, we delay range check smearing until after loop opts.
        phase->C->record_for_post_loop_opts_igvn(this);
        return nullptr;
      }

      // Didn't find prior covering check, so cannot remove anything.
      if (nb_checks == 0) {
        return nullptr;
      }
      // Constant indices only need to check the upper bound.
      // Non-constant indices must check both low and high.
      int chk0 = (nb_checks - 1) % NRC;
      if (index1) {
        if (nb_checks == 1) {
          return nullptr;
        } else {
          // If the top range check's constant is the min or max of
          // all constants we widen the next one to cover the whole
          // range of constants.
          RangeCheck rc0 = prev_checks[chk0];
          int chk1 = (nb_checks - 2) % NRC;
          RangeCheck rc1 = prev_checks[chk1];
          if (rc0.off == off_lo) {
            adjust_check(rc1.ctl, range1, index1, flip1, off_hi, igvn);
            prev_dom = rc1.ctl;
          } else if (rc0.off == off_hi) {
            adjust_check(rc1.ctl, range1, index1, flip1, off_lo, igvn);
            prev_dom = rc1.ctl;
          } else {
            // If the top test's constant is not the min or max of all
            // constants, we need 3 range checks. We must leave the
            // top test unchanged because widening it would allow the
            // accesses it protects to successfully read/write out of
            // bounds.
            if (nb_checks == 2) {
              return nullptr;
            }
            int chk2 = (nb_checks - 3) % NRC;
            RangeCheck rc2 = prev_checks[chk2];
            // The top range check a+i covers interval: -a <= i < length-a
            // The second range check b+i covers interval: -b <= i < length-b
            if (rc1.off <= rc0.off) {
              // if b <= a, we change the second range check to:
              // -min_of_all_constants <= i < length-min_of_all_constants
              // Together top and second range checks now cover:
              // -min_of_all_constants <= i < length-a
              // which is more restrictive than -b <= i < length-b:
              // -b <= -min_of_all_constants <= i < length-a <= length-b
              // The third check is then changed to:
              // -max_of_all_constants <= i < length-max_of_all_constants
              // so 2nd and 3rd checks restrict allowed values of i to:
              // -min_of_all_constants <= i < length-max_of_all_constants
              adjust_check(rc1.ctl, range1, index1, flip1, off_lo, igvn);
              adjust_check(rc2.ctl, range1, index1, flip1, off_hi, igvn);
            } else {
              // if b > a, we change the second range check to:
              // -max_of_all_constants <= i < length-max_of_all_constants
              // Together top and second range checks now cover:
              // -a <= i < length-max_of_all_constants
              // which is more restrictive than -b <= i < length-b:
              // -b < -a <= i < length-max_of_all_constants <= length-b
              // The third check is then changed to:
              // -max_of_all_constants <= i < length-max_of_all_constants
              // so 2nd and 3rd checks restrict allowed values of i to:
              // -min_of_all_constants <= i < length-max_of_all_constants
              adjust_check(rc1.ctl, range1, index1, flip1, off_hi, igvn);
              adjust_check(rc2.ctl, range1, index1, flip1, off_lo, igvn);
            }
            prev_dom = rc2.ctl;
          }
        }
      } else {
        RangeCheck rc0 = prev_checks[chk0];
        // 'Widen' the offset of the 1st and only covering check
        adjust_check(rc0.ctl, range1, index1, flip1, off_hi, igvn);
        // Test is now covered by prior checks, dominate it out
        prev_dom = rc0.ctl;
      }
      // The last RangeCheck is found to be redundant with a sequence of n (n >= 2) preceding RangeChecks.
      // If an array load is control dependent on the eliminated range check, the array load nodes (CastII and Load)
      // become control dependent on the last range check of the sequence, but they are really dependent on the entire
      // sequence of RangeChecks. If RangeCheck#n is later replaced by a dominating identical check, the array load
      // nodes must not float above the n-1 other RangeCheck in the sequence. We pin the array load nodes here to
      // guarantee it doesn't happen.
      //
      // RangeCheck#1                 RangeCheck#1
      //    |      \                     |      \
      //    |      uncommon trap         |      uncommon trap
      //    ..                           ..
      // RangeCheck#n              -> RangeCheck#n
      //    |      \                     |      \
      //    |      uncommon trap        CastII  uncommon trap
      // RangeCheck                     Load
      //    |      \
      //   CastII  uncommon trap
      //   Load

      return dominated_by(prev_dom, igvn, true);
    }
  } else {
    prev_dom = search_identical(4, igvn);

    if (prev_dom == nullptr) {
      return nullptr;
    }
  }

  // Replace dominated IfNode
  return dominated_by(prev_dom, igvn, false);
}

ParsePredicateNode::ParsePredicateNode(Node* control, Deoptimization::DeoptReason deopt_reason, PhaseGVN* gvn)
    : IfNode(control, gvn->intcon(1), PROB_MAX, COUNT_UNKNOWN),
      _deopt_reason(deopt_reason),
      _predicate_state(PredicateState::Useful) {
  init_class_id(Class_ParsePredicate);
  gvn->C->add_parse_predicate(this);
  gvn->C->record_for_post_loop_opts_igvn(this);
#ifdef ASSERT
  switch (deopt_reason) {
    case Deoptimization::Reason_predicate:
    case Deoptimization::Reason_profile_predicate:
    case Deoptimization::Reason_auto_vectorization_check:
    case Deoptimization::Reason_loop_limit_check:
    case Deoptimization::Reason_short_running_long_loop:
      break;
    default:
      assert(false, "unsupported deoptimization reason for Parse Predicate");
  }
#endif // ASSERT
}

void ParsePredicateNode::mark_useless(PhaseIterGVN& igvn) {
  _predicate_state = PredicateState::Useless;
  igvn._worklist.push(this);
}

Node* ParsePredicateNode::uncommon_trap() const {
  ParsePredicateUncommonProj* uncommon_proj = proj_out(0)->as_IfFalse();
  Node* uct_region_or_call = uncommon_proj->unique_ctrl_out();
  assert(uct_region_or_call->is_Region() || uct_region_or_call->is_Call(), "must be a region or call uct");
  return uct_region_or_call;
}

// Fold this node away once it becomes useless or at latest in post loop opts IGVN.
const Type* ParsePredicateNode::Value(PhaseGVN* phase) const {
  assert(_predicate_state != PredicateState::MaybeUseful, "should only be MaybeUseful when eliminating useless "
                                                          "predicates during loop opts");
  if (phase->type(in(0)) == Type::TOP) {
    return Type::TOP;
  }
  if (_predicate_state == PredicateState::Useless || phase->C->post_loop_opts_phase()) {
    return TypeTuple::IFTRUE;
  }
  return bottom_type();
}

#ifndef PRODUCT
void ParsePredicateNode::dump_spec(outputStream* st) const {
  st->print(" #");
  switch (_deopt_reason) {
    case Deoptimization::DeoptReason::Reason_predicate:
      st->print("Loop ");
      break;
    case Deoptimization::DeoptReason::Reason_profile_predicate:
      st->print("Profiled_Loop ");
      break;
    case Deoptimization::DeoptReason::Reason_auto_vectorization_check:
      st->print("Auto_Vectorization_Check ");
      break;
    case Deoptimization::DeoptReason::Reason_loop_limit_check:
      st->print("Loop_Limit_Check ");
      break;
    case Deoptimization::DeoptReason::Reason_short_running_long_loop:
      st->print("Short_Running_Long_Loop ");
      break;
    default:
      fatal("unknown kind");
  }
  if (_predicate_state == PredicateState::Useless) {
    st->print("#useless ");
  }
}
#endif // NOT PRODUCT
