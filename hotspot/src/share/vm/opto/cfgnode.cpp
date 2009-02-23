/*
 * Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

// Portions of code courtesy of Clifford Click

// Optimization - Graph Style

#include "incls/_precompiled.incl"
#include "incls/_cfgnode.cpp.incl"

//=============================================================================
//------------------------------Value------------------------------------------
// Compute the type of the RegionNode.
const Type *RegionNode::Value( PhaseTransform *phase ) const {
  for( uint i=1; i<req(); ++i ) {       // For all paths in
    Node *n = in(i);            // Get Control source
    if( !n ) continue;          // Missing inputs are TOP
    if( phase->type(n) == Type::CONTROL )
      return Type::CONTROL;
  }
  return Type::TOP;             // All paths dead?  Then so are we
}

//------------------------------Identity---------------------------------------
// Check for Region being Identity.
Node *RegionNode::Identity( PhaseTransform *phase ) {
  // Cannot have Region be an identity, even if it has only 1 input.
  // Phi users cannot have their Region input folded away for them,
  // since they need to select the proper data input
  return this;
}

//------------------------------merge_region-----------------------------------
// If a Region flows into a Region, merge into one big happy merge.  This is
// hard to do if there is stuff that has to happen
static Node *merge_region(RegionNode *region, PhaseGVN *phase) {
  if( region->Opcode() != Op_Region ) // Do not do to LoopNodes
    return NULL;
  Node *progress = NULL;        // Progress flag
  PhaseIterGVN *igvn = phase->is_IterGVN();

  uint rreq = region->req();
  for( uint i = 1; i < rreq; i++ ) {
    Node *r = region->in(i);
    if( r && r->Opcode() == Op_Region && // Found a region?
        r->in(0) == r &&        // Not already collapsed?
        r != region &&          // Avoid stupid situations
        r->outcnt() == 2 ) {    // Self user and 'region' user only?
      assert(!r->as_Region()->has_phi(), "no phi users");
      if( !progress ) {         // No progress
        if (region->has_phi()) {
          return NULL;        // Only flatten if no Phi users
          // igvn->hash_delete( phi );
        }
        igvn->hash_delete( region );
        progress = region;      // Making progress
      }
      igvn->hash_delete( r );

      // Append inputs to 'r' onto 'region'
      for( uint j = 1; j < r->req(); j++ ) {
        // Move an input from 'r' to 'region'
        region->add_req(r->in(j));
        r->set_req(j, phase->C->top());
        // Update phis of 'region'
        //for( uint k = 0; k < max; k++ ) {
        //  Node *phi = region->out(k);
        //  if( phi->is_Phi() ) {
        //    phi->add_req(phi->in(i));
        //  }
        //}

        rreq++;                 // One more input to Region
      } // Found a region to merge into Region
      // Clobber pointer to the now dead 'r'
      region->set_req(i, phase->C->top());
    }
  }

  return progress;
}



//--------------------------------has_phi--------------------------------------
// Helper function: Return any PhiNode that uses this region or NULL
PhiNode* RegionNode::has_phi() const {
  for (DUIterator_Fast imax, i = fast_outs(imax); i < imax; i++) {
    Node* phi = fast_out(i);
    if (phi->is_Phi()) {   // Check for Phi users
      assert(phi->in(0) == (Node*)this, "phi uses region only via in(0)");
      return phi->as_Phi();  // this one is good enough
    }
  }

  return NULL;
}


//-----------------------------has_unique_phi----------------------------------
// Helper function: Return the only PhiNode that uses this region or NULL
PhiNode* RegionNode::has_unique_phi() const {
  // Check that only one use is a Phi
  PhiNode* only_phi = NULL;
  for (DUIterator_Fast imax, i = fast_outs(imax); i < imax; i++) {
    Node* phi = fast_out(i);
    if (phi->is_Phi()) {   // Check for Phi users
      assert(phi->in(0) == (Node*)this, "phi uses region only via in(0)");
      if (only_phi == NULL) {
        only_phi = phi->as_Phi();
      } else {
        return NULL;  // multiple phis
      }
    }
  }

  return only_phi;
}


//------------------------------check_phi_clipping-----------------------------
// Helper function for RegionNode's identification of FP clipping
// Check inputs to the Phi
static bool check_phi_clipping( PhiNode *phi, ConNode * &min, uint &min_idx, ConNode * &max, uint &max_idx, Node * &val, uint &val_idx ) {
  min     = NULL;
  max     = NULL;
  val     = NULL;
  min_idx = 0;
  max_idx = 0;
  val_idx = 0;
  uint  phi_max = phi->req();
  if( phi_max == 4 ) {
    for( uint j = 1; j < phi_max; ++j ) {
      Node *n = phi->in(j);
      int opcode = n->Opcode();
      switch( opcode ) {
      case Op_ConI:
        {
          if( min == NULL ) {
            min     = n->Opcode() == Op_ConI ? (ConNode*)n : NULL;
            min_idx = j;
          } else {
            max     = n->Opcode() == Op_ConI ? (ConNode*)n : NULL;
            max_idx = j;
            if( min->get_int() > max->get_int() ) {
              // Swap min and max
              ConNode *temp;
              uint     temp_idx;
              temp     = min;     min     = max;     max     = temp;
              temp_idx = min_idx; min_idx = max_idx; max_idx = temp_idx;
            }
          }
        }
        break;
      default:
        {
          val = n;
          val_idx = j;
        }
        break;
      }
    }
  }
  return ( min && max && val && (min->get_int() <= 0) && (max->get_int() >=0) );
}


//------------------------------check_if_clipping------------------------------
// Helper function for RegionNode's identification of FP clipping
// Check that inputs to Region come from two IfNodes,
//
//            If
//      False    True
//       If        |
//  False  True    |
//    |      |     |
//  RegionNode_inputs
//
static bool check_if_clipping( const RegionNode *region, IfNode * &bot_if, IfNode * &top_if ) {
  top_if = NULL;
  bot_if = NULL;

  // Check control structure above RegionNode for (if  ( if  ) )
  Node *in1 = region->in(1);
  Node *in2 = region->in(2);
  Node *in3 = region->in(3);
  // Check that all inputs are projections
  if( in1->is_Proj() && in2->is_Proj() && in3->is_Proj() ) {
    Node *in10 = in1->in(0);
    Node *in20 = in2->in(0);
    Node *in30 = in3->in(0);
    // Check that #1 and #2 are ifTrue and ifFalse from same If
    if( in10 != NULL && in10->is_If() &&
        in20 != NULL && in20->is_If() &&
        in30 != NULL && in30->is_If() && in10 == in20 &&
        (in1->Opcode() != in2->Opcode()) ) {
      Node  *in100 = in10->in(0);
      Node *in1000 = (in100 != NULL && in100->is_Proj()) ? in100->in(0) : NULL;
      // Check that control for in10 comes from other branch of IF from in3
      if( in1000 != NULL && in1000->is_If() &&
          in30 == in1000 && (in3->Opcode() != in100->Opcode()) ) {
        // Control pattern checks
        top_if = (IfNode*)in1000;
        bot_if = (IfNode*)in10;
      }
    }
  }

  return (top_if != NULL);
}


//------------------------------check_convf2i_clipping-------------------------
// Helper function for RegionNode's identification of FP clipping
// Verify that the value input to the phi comes from "ConvF2I; LShift; RShift"
static bool check_convf2i_clipping( PhiNode *phi, uint idx, ConvF2INode * &convf2i, Node *min, Node *max) {
  convf2i = NULL;

  // Check for the RShiftNode
  Node *rshift = phi->in(idx);
  assert( rshift, "Previous checks ensure phi input is present");
  if( rshift->Opcode() != Op_RShiftI )  { return false; }

  // Check for the LShiftNode
  Node *lshift = rshift->in(1);
  assert( lshift, "Previous checks ensure phi input is present");
  if( lshift->Opcode() != Op_LShiftI )  { return false; }

  // Check for the ConvF2INode
  Node *conv = lshift->in(1);
  if( conv->Opcode() != Op_ConvF2I ) { return false; }

  // Check that shift amounts are only to get sign bits set after F2I
  jint max_cutoff     = max->get_int();
  jint min_cutoff     = min->get_int();
  jint left_shift     = lshift->in(2)->get_int();
  jint right_shift    = rshift->in(2)->get_int();
  jint max_post_shift = nth_bit(BitsPerJavaInteger - left_shift - 1);
  if( left_shift != right_shift ||
      0 > left_shift || left_shift >= BitsPerJavaInteger ||
      max_post_shift < max_cutoff ||
      max_post_shift < -min_cutoff ) {
    // Shifts are necessary but current transformation eliminates them
    return false;
  }

  // OK to return the result of ConvF2I without shifting
  convf2i = (ConvF2INode*)conv;
  return true;
}


//------------------------------check_compare_clipping-------------------------
// Helper function for RegionNode's identification of FP clipping
static bool check_compare_clipping( bool less_than, IfNode *iff, ConNode *limit, Node * & input ) {
  Node *i1 = iff->in(1);
  if ( !i1->is_Bool() ) { return false; }
  BoolNode *bool1 = i1->as_Bool();
  if(       less_than && bool1->_test._test != BoolTest::le ) { return false; }
  else if( !less_than && bool1->_test._test != BoolTest::lt ) { return false; }
  const Node *cmpF = bool1->in(1);
  if( cmpF->Opcode() != Op_CmpF )      { return false; }
  // Test that the float value being compared against
  // is equivalent to the int value used as a limit
  Node *nodef = cmpF->in(2);
  if( nodef->Opcode() != Op_ConF ) { return false; }
  jfloat conf = nodef->getf();
  jint   coni = limit->get_int();
  if( ((int)conf) != coni )        { return false; }
  input = cmpF->in(1);
  return true;
}

//------------------------------is_unreachable_region--------------------------
// Find if the Region node is reachable from the root.
bool RegionNode::is_unreachable_region(PhaseGVN *phase) const {
  assert(req() == 2, "");

  // First, cut the simple case of fallthrough region when NONE of
  // region's phis references itself directly or through a data node.
  uint max = outcnt();
  uint i;
  for (i = 0; i < max; i++) {
    Node* phi = raw_out(i);
    if (phi != NULL && phi->is_Phi()) {
      assert(phase->eqv(phi->in(0), this) && phi->req() == 2, "");
      if (phi->outcnt() == 0)
        continue; // Safe case - no loops
      if (phi->outcnt() == 1) {
        Node* u = phi->raw_out(0);
        // Skip if only one use is an other Phi or Call or Uncommon trap.
        // It is safe to consider this case as fallthrough.
        if (u != NULL && (u->is_Phi() || u->is_CFG()))
          continue;
      }
      // Check when phi references itself directly or through an other node.
      if (phi->as_Phi()->simple_data_loop_check(phi->in(1)) >= PhiNode::Unsafe)
        break; // Found possible unsafe data loop.
    }
  }
  if (i >= max)
    return false; // An unsafe case was NOT found - don't need graph walk.

  // Unsafe case - check if the Region node is reachable from root.
  ResourceMark rm;

  Arena *a = Thread::current()->resource_area();
  Node_List nstack(a);
  VectorSet visited(a);

  // Mark all control nodes reachable from root outputs
  Node *n = (Node*)phase->C->root();
  nstack.push(n);
  visited.set(n->_idx);
  while (nstack.size() != 0) {
    n = nstack.pop();
    uint max = n->outcnt();
    for (uint i = 0; i < max; i++) {
      Node* m = n->raw_out(i);
      if (m != NULL && m->is_CFG()) {
        if (phase->eqv(m, this)) {
          return false; // We reached the Region node - it is not dead.
        }
        if (!visited.test_set(m->_idx))
          nstack.push(m);
      }
    }
  }

  return true; // The Region node is unreachable - it is dead.
}

//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.  Must preserve
// the CFG, but we can still strip out dead paths.
Node *RegionNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if( !can_reshape && !in(0) ) return NULL;     // Already degraded to a Copy
  assert(!in(0) || !in(0)->is_Root(), "not a specially hidden merge");

  // Check for RegionNode with no Phi users and both inputs come from either
  // arm of the same IF.  If found, then the control-flow split is useless.
  bool has_phis = false;
  if (can_reshape) {            // Need DU info to check for Phi users
    has_phis = (has_phi() != NULL);       // Cache result
    if (!has_phis) {            // No Phi users?  Nothing merging?
      for (uint i = 1; i < req()-1; i++) {
        Node *if1 = in(i);
        if( !if1 ) continue;
        Node *iff = if1->in(0);
        if( !iff || !iff->is_If() ) continue;
        for( uint j=i+1; j<req(); j++ ) {
          if( in(j) && in(j)->in(0) == iff &&
              if1->Opcode() != in(j)->Opcode() ) {
            // Add the IF Projections to the worklist. They (and the IF itself)
            // will be eliminated if dead.
            phase->is_IterGVN()->add_users_to_worklist(iff);
            set_req(i, iff->in(0));// Skip around the useless IF diamond
            set_req(j, NULL);
            return this;      // Record progress
          }
        }
      }
    }
  }

  // Remove TOP or NULL input paths. If only 1 input path remains, this Region
  // degrades to a copy.
  bool add_to_worklist = false;
  int cnt = 0;                  // Count of values merging
  DEBUG_ONLY( int cnt_orig = req(); ) // Save original inputs count
  int del_it = 0;               // The last input path we delete
  // For all inputs...
  for( uint i=1; i<req(); ++i ){// For all paths in
    Node *n = in(i);            // Get the input
    if( n != NULL ) {
      // Remove useless control copy inputs
      if( n->is_Region() && n->as_Region()->is_copy() ) {
        set_req(i, n->nonnull_req());
        i--;
        continue;
      }
      if( n->is_Proj() ) {      // Remove useless rethrows
        Node *call = n->in(0);
        if (call->is_Call() && call->as_Call()->entry_point() == OptoRuntime::rethrow_stub()) {
          set_req(i, call->in(0));
          i--;
          continue;
        }
      }
      if( phase->type(n) == Type::TOP ) {
        set_req(i, NULL);       // Ignore TOP inputs
        i--;
        continue;
      }
      cnt++;                    // One more value merging

    } else if (can_reshape) {   // Else found dead path with DU info
      PhaseIterGVN *igvn = phase->is_IterGVN();
      del_req(i);               // Yank path from self
      del_it = i;
      uint max = outcnt();
      DUIterator j;
      bool progress = true;
      while(progress) {         // Need to establish property over all users
        progress = false;
        for (j = outs(); has_out(j); j++) {
          Node *n = out(j);
          if( n->req() != req() && n->is_Phi() ) {
            assert( n->in(0) == this, "" );
            igvn->hash_delete(n); // Yank from hash before hacking edges
            n->set_req_X(i,NULL,igvn);// Correct DU info
            n->del_req(i);        // Yank path from Phis
            if( max != outcnt() ) {
              progress = true;
              j = refresh_out_pos(j);
              max = outcnt();
            }
          }
        }
      }
      add_to_worklist = true;
      i--;
    }
  }

  if (can_reshape && cnt == 1) {
    // Is it dead loop?
    // If it is LoopNopde it had 2 (+1 itself) inputs and
    // one of them was cut. The loop is dead if it was EntryContol.
    assert(!this->is_Loop() || cnt_orig == 3, "Loop node should have 3 inputs");
    if (this->is_Loop() && del_it == LoopNode::EntryControl ||
       !this->is_Loop() && has_phis && is_unreachable_region(phase)) {
      // Yes,  the region will be removed during the next step below.
      // Cut the backedge input and remove phis since no data paths left.
      // We don't cut outputs to other nodes here since we need to put them
      // on the worklist.
      del_req(1);
      cnt = 0;
      assert( req() == 1, "no more inputs expected" );
      uint max = outcnt();
      bool progress = true;
      Node *top = phase->C->top();
      PhaseIterGVN *igvn = phase->is_IterGVN();
      DUIterator j;
      while(progress) {
        progress = false;
        for (j = outs(); has_out(j); j++) {
          Node *n = out(j);
          if( n->is_Phi() ) {
            assert( igvn->eqv(n->in(0), this), "" );
            assert( n->req() == 2 &&  n->in(1) != NULL, "Only one data input expected" );
            // Break dead loop data path.
            // Eagerly replace phis with top to avoid phis copies generation.
            igvn->add_users_to_worklist(n);
            igvn->hash_delete(n); // Yank from hash before hacking edges
            igvn->subsume_node(n, top);
            if( max != outcnt() ) {
              progress = true;
              j = refresh_out_pos(j);
              max = outcnt();
            }
          }
        }
      }
      add_to_worklist = true;
    }
  }
  if (add_to_worklist) {
    phase->is_IterGVN()->add_users_to_worklist(this); // Revisit collapsed Phis
  }

  if( cnt <= 1 ) {              // Only 1 path in?
    set_req(0, NULL);           // Null control input for region copy
    if( cnt == 0 && !can_reshape) { // Parse phase - leave the node as it is.
      // No inputs or all inputs are NULL.
      return NULL;
    } else if (can_reshape) {   // Optimization phase - remove the node
      PhaseIterGVN *igvn = phase->is_IterGVN();
      Node *parent_ctrl;
      if( cnt == 0 ) {
        assert( req() == 1, "no inputs expected" );
        // During IGVN phase such region will be subsumed by TOP node
        // so region's phis will have TOP as control node.
        // Kill phis here to avoid it. PhiNode::is_copy() will be always false.
        // Also set other user's input to top.
        parent_ctrl = phase->C->top();
      } else {
        // The fallthrough case since we already checked dead loops above.
        parent_ctrl = in(1);
        assert(parent_ctrl != NULL, "Region is a copy of some non-null control");
        assert(!igvn->eqv(parent_ctrl, this), "Close dead loop");
      }
      if (!add_to_worklist)
        igvn->add_users_to_worklist(this); // Check for further allowed opts
      for (DUIterator_Last imin, i = last_outs(imin); i >= imin; --i) {
        Node* n = last_out(i);
        igvn->hash_delete(n); // Remove from worklist before modifying edges
        if( n->is_Phi() ) {   // Collapse all Phis
          // Eagerly replace phis to avoid copies generation.
          igvn->add_users_to_worklist(n);
          igvn->hash_delete(n); // Yank from hash before hacking edges
          if( cnt == 0 ) {
            assert( n->req() == 1, "No data inputs expected" );
            igvn->subsume_node(n, parent_ctrl); // replaced by top
          } else {
            assert( n->req() == 2 &&  n->in(1) != NULL, "Only one data input expected" );
            Node* in1 = n->in(1);               // replaced by unique input
            if( n->as_Phi()->is_unsafe_data_reference(in1) )
              in1 = phase->C->top();            // replaced by top
            igvn->subsume_node(n, in1);
          }
        }
        else if( n->is_Region() ) { // Update all incoming edges
          assert( !igvn->eqv(n, this), "Must be removed from DefUse edges");
          uint uses_found = 0;
          for( uint k=1; k < n->req(); k++ ) {
            if( n->in(k) == this ) {
              n->set_req(k, parent_ctrl);
              uses_found++;
            }
          }
          if( uses_found > 1 ) { // (--i) done at the end of the loop.
            i -= (uses_found - 1);
          }
        }
        else {
          assert( igvn->eqv(n->in(0), this), "Expect RegionNode to be control parent");
          n->set_req(0, parent_ctrl);
        }
#ifdef ASSERT
        for( uint k=0; k < n->req(); k++ ) {
          assert( !igvn->eqv(n->in(k), this), "All uses of RegionNode should be gone");
        }
#endif
      }
      // Remove the RegionNode itself from DefUse info
      igvn->remove_dead_node(this);
      return NULL;
    }
    return this;                // Record progress
  }


  // If a Region flows into a Region, merge into one big happy merge.
  if (can_reshape) {
    Node *m = merge_region(this, phase);
    if (m != NULL)  return m;
  }

  // Check if this region is the root of a clipping idiom on floats
  if( ConvertFloat2IntClipping && can_reshape && req() == 4 ) {
    // Check that only one use is a Phi and that it simplifies to two constants +
    PhiNode* phi = has_unique_phi();
    if (phi != NULL) {          // One Phi user
      // Check inputs to the Phi
      ConNode *min;
      ConNode *max;
      Node    *val;
      uint     min_idx;
      uint     max_idx;
      uint     val_idx;
      if( check_phi_clipping( phi, min, min_idx, max, max_idx, val, val_idx )  ) {
        IfNode *top_if;
        IfNode *bot_if;
        if( check_if_clipping( this, bot_if, top_if ) ) {
          // Control pattern checks, now verify compares
          Node   *top_in = NULL;   // value being compared against
          Node   *bot_in = NULL;
          if( check_compare_clipping( true,  bot_if, min, bot_in ) &&
              check_compare_clipping( false, top_if, max, top_in ) ) {
            if( bot_in == top_in ) {
              PhaseIterGVN *gvn = phase->is_IterGVN();
              assert( gvn != NULL, "Only had DefUse info in IterGVN");
              // Only remaining check is that bot_in == top_in == (Phi's val + mods)

              // Check for the ConvF2INode
              ConvF2INode *convf2i;
              if( check_convf2i_clipping( phi, val_idx, convf2i, min, max ) &&
                convf2i->in(1) == bot_in ) {
                // Matched pattern, including LShiftI; RShiftI, replace with integer compares
                // max test
                Node *cmp   = gvn->register_new_node_with_optimizer(new (phase->C, 3) CmpINode( convf2i, min ));
                Node *boo   = gvn->register_new_node_with_optimizer(new (phase->C, 2) BoolNode( cmp, BoolTest::lt ));
                IfNode *iff = (IfNode*)gvn->register_new_node_with_optimizer(new (phase->C, 2) IfNode( top_if->in(0), boo, PROB_UNLIKELY_MAG(5), top_if->_fcnt ));
                Node *if_min= gvn->register_new_node_with_optimizer(new (phase->C, 1) IfTrueNode (iff));
                Node *ifF   = gvn->register_new_node_with_optimizer(new (phase->C, 1) IfFalseNode(iff));
                // min test
                cmp         = gvn->register_new_node_with_optimizer(new (phase->C, 3) CmpINode( convf2i, max ));
                boo         = gvn->register_new_node_with_optimizer(new (phase->C, 2) BoolNode( cmp, BoolTest::gt ));
                iff         = (IfNode*)gvn->register_new_node_with_optimizer(new (phase->C, 2) IfNode( ifF, boo, PROB_UNLIKELY_MAG(5), bot_if->_fcnt ));
                Node *if_max= gvn->register_new_node_with_optimizer(new (phase->C, 1) IfTrueNode (iff));
                ifF         = gvn->register_new_node_with_optimizer(new (phase->C, 1) IfFalseNode(iff));
                // update input edges to region node
                set_req_X( min_idx, if_min, gvn );
                set_req_X( max_idx, if_max, gvn );
                set_req_X( val_idx, ifF,    gvn );
                // remove unnecessary 'LShiftI; RShiftI' idiom
                gvn->hash_delete(phi);
                phi->set_req_X( val_idx, convf2i, gvn );
                gvn->hash_find_insert(phi);
                // Return transformed region node
                return this;
              }
            }
          }
        }
      }
    }
  }

  return NULL;
}



const RegMask &RegionNode::out_RegMask() const {
  return RegMask::Empty;
}

// Find the one non-null required input.  RegionNode only
Node *Node::nonnull_req() const {
  assert( is_Region(), "" );
  for( uint i = 1; i < _cnt; i++ )
    if( in(i) )
      return in(i);
  ShouldNotReachHere();
  return NULL;
}


//=============================================================================
// note that these functions assume that the _adr_type field is flattened
uint PhiNode::hash() const {
  const Type* at = _adr_type;
  return TypeNode::hash() + (at ? at->hash() : 0);
}
uint PhiNode::cmp( const Node &n ) const {
  return TypeNode::cmp(n) && _adr_type == ((PhiNode&)n)._adr_type;
}
static inline
const TypePtr* flatten_phi_adr_type(const TypePtr* at) {
  if (at == NULL || at == TypePtr::BOTTOM)  return at;
  return Compile::current()->alias_type(at)->adr_type();
}

//----------------------------make---------------------------------------------
// create a new phi with edges matching r and set (initially) to x
PhiNode* PhiNode::make(Node* r, Node* x, const Type *t, const TypePtr* at) {
  uint preds = r->req();   // Number of predecessor paths
  assert(t != Type::MEMORY || at == flatten_phi_adr_type(at), "flatten at");
  PhiNode* p = new (Compile::current(), preds) PhiNode(r, t, at);
  for (uint j = 1; j < preds; j++) {
    // Fill in all inputs, except those which the region does not yet have
    if (r->in(j) != NULL)
      p->init_req(j, x);
  }
  return p;
}
PhiNode* PhiNode::make(Node* r, Node* x) {
  const Type*    t  = x->bottom_type();
  const TypePtr* at = NULL;
  if (t == Type::MEMORY)  at = flatten_phi_adr_type(x->adr_type());
  return make(r, x, t, at);
}
PhiNode* PhiNode::make_blank(Node* r, Node* x) {
  const Type*    t  = x->bottom_type();
  const TypePtr* at = NULL;
  if (t == Type::MEMORY)  at = flatten_phi_adr_type(x->adr_type());
  return new (Compile::current(), r->req()) PhiNode(r, t, at);
}


//------------------------slice_memory-----------------------------------------
// create a new phi with narrowed memory type
PhiNode* PhiNode::slice_memory(const TypePtr* adr_type) const {
  PhiNode* mem = (PhiNode*) clone();
  *(const TypePtr**)&mem->_adr_type = adr_type;
  // convert self-loops, or else we get a bad graph
  for (uint i = 1; i < req(); i++) {
    if ((const Node*)in(i) == this)  mem->set_req(i, mem);
  }
  mem->verify_adr_type();
  return mem;
}

//------------------------split_out_instance-----------------------------------
// Split out an instance type from a bottom phi.
PhiNode* PhiNode::split_out_instance(const TypePtr* at, PhaseIterGVN *igvn) const {
  const TypeOopPtr *t_oop = at->isa_oopptr();
  assert(t_oop != NULL && t_oop->is_known_instance(), "expecting instance oopptr");
  const TypePtr *t = adr_type();
  assert(type() == Type::MEMORY &&
         (t == TypePtr::BOTTOM || t == TypeRawPtr::BOTTOM ||
          t->isa_oopptr() && !t->is_oopptr()->is_known_instance() &&
          t->is_oopptr()->cast_to_exactness(true)
           ->is_oopptr()->cast_to_ptr_type(t_oop->ptr())
           ->is_oopptr()->cast_to_instance_id(t_oop->instance_id()) == t_oop),
         "bottom or raw memory required");

  // Check if an appropriate node already exists.
  Node *region = in(0);
  for (DUIterator_Fast kmax, k = region->fast_outs(kmax); k < kmax; k++) {
    Node* use = region->fast_out(k);
    if( use->is_Phi()) {
      PhiNode *phi2 = use->as_Phi();
      if (phi2->type() == Type::MEMORY && phi2->adr_type() == at) {
        return phi2;
      }
    }
  }
  Compile *C = igvn->C;
  Arena *a = Thread::current()->resource_area();
  Node_Array node_map = new Node_Array(a);
  Node_Stack stack(a, C->unique() >> 4);
  PhiNode *nphi = slice_memory(at);
  igvn->register_new_node_with_optimizer( nphi );
  node_map.map(_idx, nphi);
  stack.push((Node *)this, 1);
  while(!stack.is_empty()) {
    PhiNode *ophi = stack.node()->as_Phi();
    uint i = stack.index();
    assert(i >= 1, "not control edge");
    stack.pop();
    nphi = node_map[ophi->_idx]->as_Phi();
    for (; i < ophi->req(); i++) {
      Node *in = ophi->in(i);
      if (in == NULL || igvn->type(in) == Type::TOP)
        continue;
      Node *opt = MemNode::optimize_simple_memory_chain(in, at, igvn);
      PhiNode *optphi = opt->is_Phi() ? opt->as_Phi() : NULL;
      if (optphi != NULL && optphi->adr_type() == TypePtr::BOTTOM) {
        opt = node_map[optphi->_idx];
        if (opt == NULL) {
          stack.push(ophi, i);
          nphi = optphi->slice_memory(at);
          igvn->register_new_node_with_optimizer( nphi );
          node_map.map(optphi->_idx, nphi);
          ophi = optphi;
          i = 0; // will get incremented at top of loop
          continue;
        }
      }
      nphi->set_req(i, opt);
    }
  }
  return nphi;
}

//------------------------verify_adr_type--------------------------------------
#ifdef ASSERT
void PhiNode::verify_adr_type(VectorSet& visited, const TypePtr* at) const {
  if (visited.test_set(_idx))  return;  //already visited

  // recheck constructor invariants:
  verify_adr_type(false);

  // recheck local phi/phi consistency:
  assert(_adr_type == at || _adr_type == TypePtr::BOTTOM,
         "adr_type must be consistent across phi nest");

  // walk around
  for (uint i = 1; i < req(); i++) {
    Node* n = in(i);
    if (n == NULL)  continue;
    const Node* np = in(i);
    if (np->is_Phi()) {
      np->as_Phi()->verify_adr_type(visited, at);
    } else if (n->bottom_type() == Type::TOP
               || (n->is_Mem() && n->in(MemNode::Address)->bottom_type() == Type::TOP)) {
      // ignore top inputs
    } else {
      const TypePtr* nat = flatten_phi_adr_type(n->adr_type());
      // recheck phi/non-phi consistency at leaves:
      assert((nat != NULL) == (at != NULL), "");
      assert(nat == at || nat == TypePtr::BOTTOM,
             "adr_type must be consistent at leaves of phi nest");
    }
  }
}

// Verify a whole nest of phis rooted at this one.
void PhiNode::verify_adr_type(bool recursive) const {
  if (is_error_reported())  return;  // muzzle asserts when debugging an error
  if (Node::in_dump())      return;  // muzzle asserts when printing

  assert((_type == Type::MEMORY) == (_adr_type != NULL), "adr_type for memory phis only");

  if (!VerifyAliases)       return;  // verify thoroughly only if requested

  assert(_adr_type == flatten_phi_adr_type(_adr_type),
         "Phi::adr_type must be pre-normalized");

  if (recursive) {
    VectorSet visited(Thread::current()->resource_area());
    verify_adr_type(visited, _adr_type);
  }
}
#endif


//------------------------------Value------------------------------------------
// Compute the type of the PhiNode
const Type *PhiNode::Value( PhaseTransform *phase ) const {
  Node *r = in(0);              // RegionNode
  if( !r )                      // Copy or dead
    return in(1) ? phase->type(in(1)) : Type::TOP;

  // Note: During parsing, phis are often transformed before their regions.
  // This means we have to use type_or_null to defend against untyped regions.
  if( phase->type_or_null(r) == Type::TOP )  // Dead code?
    return Type::TOP;

  // Check for trip-counted loop.  If so, be smarter.
  CountedLoopNode *l = r->is_CountedLoop() ? r->as_CountedLoop() : NULL;
  if( l && l->can_be_counted_loop(phase) &&
      ((const Node*)l->phi() == this) ) { // Trip counted loop!
    // protect against init_trip() or limit() returning NULL
    const Node *init   = l->init_trip();
    const Node *limit  = l->limit();
    if( init != NULL && limit != NULL && l->stride_is_con() ) {
      const TypeInt *lo = init ->bottom_type()->isa_int();
      const TypeInt *hi = limit->bottom_type()->isa_int();
      if( lo && hi ) {            // Dying loops might have TOP here
        int stride = l->stride_con();
        if( stride < 0 ) {          // Down-counter loop
          const TypeInt *tmp = lo; lo = hi; hi = tmp;
          stride = -stride;
        }
        if( lo->_hi < hi->_lo )     // Reversed endpoints are well defined :-(
          return TypeInt::make(lo->_lo,hi->_hi,3);
      }
    }
  }

  // Until we have harmony between classes and interfaces in the type
  // lattice, we must tread carefully around phis which implicitly
  // convert the one to the other.
  const TypePtr* ttp = _type->make_ptr();
  const TypeInstPtr* ttip = (ttp != NULL) ? ttp->isa_instptr() : NULL;
  const TypeKlassPtr* ttkp = (ttp != NULL) ? ttp->isa_klassptr() : NULL;
  bool is_intf = false;
  if (ttip != NULL) {
    ciKlass* k = ttip->klass();
    if (k->is_loaded() && k->is_interface())
      is_intf = true;
  }
  if (ttkp != NULL) {
    ciKlass* k = ttkp->klass();
    if (k->is_loaded() && k->is_interface())
      is_intf = true;
  }

  // Default case: merge all inputs
  const Type *t = Type::TOP;        // Merged type starting value
  for (uint i = 1; i < req(); ++i) {// For all paths in
    // Reachable control path?
    if (r->in(i) && phase->type(r->in(i)) == Type::CONTROL) {
      const Type* ti = phase->type(in(i));
      // We assume that each input of an interface-valued Phi is a true
      // subtype of that interface.  This might not be true of the meet
      // of all the input types.  The lattice is not distributive in
      // such cases.  Ward off asserts in type.cpp by refusing to do
      // meets between interfaces and proper classes.
      const TypePtr* tip = ti->make_ptr();
      const TypeInstPtr* tiip = (tip != NULL) ? tip->isa_instptr() : NULL;
      if (tiip) {
        bool ti_is_intf = false;
        ciKlass* k = tiip->klass();
        if (k->is_loaded() && k->is_interface())
          ti_is_intf = true;
        if (is_intf != ti_is_intf)
          { t = _type; break; }
      }
      t = t->meet(ti);
    }
  }

  // The worst-case type (from ciTypeFlow) should be consistent with "t".
  // That is, we expect that "t->higher_equal(_type)" holds true.
  // There are various exceptions:
  // - Inputs which are phis might in fact be widened unnecessarily.
  //   For example, an input might be a widened int while the phi is a short.
  // - Inputs might be BotPtrs but this phi is dependent on a null check,
  //   and postCCP has removed the cast which encodes the result of the check.
  // - The type of this phi is an interface, and the inputs are classes.
  // - Value calls on inputs might produce fuzzy results.
  //   (Occurrences of this case suggest improvements to Value methods.)
  //
  // It is not possible to see Type::BOTTOM values as phi inputs,
  // because the ciTypeFlow pre-pass produces verifier-quality types.
  const Type* ft = t->filter(_type);  // Worst case type

#ifdef ASSERT
  // The following logic has been moved into TypeOopPtr::filter.
  const Type* jt = t->join(_type);
  if( jt->empty() ) {           // Emptied out???

    // Check for evil case of 't' being a class and '_type' expecting an
    // interface.  This can happen because the bytecodes do not contain
    // enough type info to distinguish a Java-level interface variable
    // from a Java-level object variable.  If we meet 2 classes which
    // both implement interface I, but their meet is at 'j/l/O' which
    // doesn't implement I, we have no way to tell if the result should
    // be 'I' or 'j/l/O'.  Thus we'll pick 'j/l/O'.  If this then flows
    // into a Phi which "knows" it's an Interface type we'll have to
    // uplift the type.
    if( !t->empty() && ttip && ttip->is_loaded() && ttip->klass()->is_interface() )
      { assert(ft == _type, ""); } // Uplift to interface
    else if( !t->empty() && ttkp && ttkp->is_loaded() && ttkp->klass()->is_interface() )
      { assert(ft == _type, ""); } // Uplift to interface
    // Otherwise it's something stupid like non-overlapping int ranges
    // found on dying counted loops.
    else
      { assert(ft == Type::TOP, ""); } // Canonical empty value
  }

  else {

    // If we have an interface-typed Phi and we narrow to a class type, the join
    // should report back the class.  However, if we have a J/L/Object
    // class-typed Phi and an interface flows in, it's possible that the meet &
    // join report an interface back out.  This isn't possible but happens
    // because the type system doesn't interact well with interfaces.
    const TypePtr *jtp = jt->make_ptr();
    const TypeInstPtr *jtip = (jtp != NULL) ? jtp->isa_instptr() : NULL;
    const TypeKlassPtr *jtkp = (jtp != NULL) ? jtp->isa_klassptr() : NULL;
    if( jtip && ttip ) {
      if( jtip->is_loaded() &&  jtip->klass()->is_interface() &&
          ttip->is_loaded() && !ttip->klass()->is_interface() ) {
        // Happens in a CTW of rt.jar, 320-341, no extra flags
        assert(ft == ttip->cast_to_ptr_type(jtip->ptr()) ||
               ft->isa_narrowoop() && ft->make_ptr() == ttip->cast_to_ptr_type(jtip->ptr()), "");
        jt = ft;
      }
    }
    if( jtkp && ttkp ) {
      if( jtkp->is_loaded() &&  jtkp->klass()->is_interface() &&
          ttkp->is_loaded() && !ttkp->klass()->is_interface() ) {
        assert(ft == ttkp->cast_to_ptr_type(jtkp->ptr()) ||
               ft->isa_narrowoop() && ft->make_ptr() == ttkp->cast_to_ptr_type(jtkp->ptr()), "");
        jt = ft;
      }
    }
    if (jt != ft && jt->base() == ft->base()) {
      if (jt->isa_int() &&
          jt->is_int()->_lo == ft->is_int()->_lo &&
          jt->is_int()->_hi == ft->is_int()->_hi)
        jt = ft;
      if (jt->isa_long() &&
          jt->is_long()->_lo == ft->is_long()->_lo &&
          jt->is_long()->_hi == ft->is_long()->_hi)
        jt = ft;
    }
    if (jt != ft) {
      tty->print("merge type:  "); t->dump(); tty->cr();
      tty->print("kill type:   "); _type->dump(); tty->cr();
      tty->print("join type:   "); jt->dump(); tty->cr();
      tty->print("filter type: "); ft->dump(); tty->cr();
    }
    assert(jt == ft, "");
  }
#endif //ASSERT

  // Deal with conversion problems found in data loops.
  ft = phase->saturate(ft, phase->type_or_null(this), _type);

  return ft;
}


//------------------------------is_diamond_phi---------------------------------
// Does this Phi represent a simple well-shaped diamond merge?  Return the
// index of the true path or 0 otherwise.
int PhiNode::is_diamond_phi() const {
  // Check for a 2-path merge
  Node *region = in(0);
  if( !region ) return 0;
  if( region->req() != 3 ) return 0;
  if(         req() != 3 ) return 0;
  // Check that both paths come from the same If
  Node *ifp1 = region->in(1);
  Node *ifp2 = region->in(2);
  if( !ifp1 || !ifp2 ) return 0;
  Node *iff = ifp1->in(0);
  if( !iff || !iff->is_If() ) return 0;
  if( iff != ifp2->in(0) ) return 0;
  // Check for a proper bool/cmp
  const Node *b = iff->in(1);
  if( !b->is_Bool() ) return 0;
  const Node *cmp = b->in(1);
  if( !cmp->is_Cmp() ) return 0;

  // Check for branching opposite expected
  if( ifp2->Opcode() == Op_IfTrue ) {
    assert( ifp1->Opcode() == Op_IfFalse, "" );
    return 2;
  } else {
    assert( ifp1->Opcode() == Op_IfTrue, "" );
    return 1;
  }
}

//----------------------------check_cmove_id-----------------------------------
// Check for CMove'ing a constant after comparing against the constant.
// Happens all the time now, since if we compare equality vs a constant in
// the parser, we "know" the variable is constant on one path and we force
// it.  Thus code like "if( x==0 ) {/*EMPTY*/}" ends up inserting a
// conditional move: "x = (x==0)?0:x;".  Yucko.  This fix is slightly more
// general in that we don't need constants.  Since CMove's are only inserted
// in very special circumstances, we do it here on generic Phi's.
Node* PhiNode::is_cmove_id(PhaseTransform* phase, int true_path) {
  assert(true_path !=0, "only diamond shape graph expected");

  // is_diamond_phi() has guaranteed the correctness of the nodes sequence:
  // phi->region->if_proj->ifnode->bool->cmp
  Node*     region = in(0);
  Node*     iff    = region->in(1)->in(0);
  BoolNode* b      = iff->in(1)->as_Bool();
  Node*     cmp    = b->in(1);
  Node*     tval   = in(true_path);
  Node*     fval   = in(3-true_path);
  Node*     id     = CMoveNode::is_cmove_id(phase, cmp, tval, fval, b);
  if (id == NULL)
    return NULL;

  // Either value might be a cast that depends on a branch of 'iff'.
  // Since the 'id' value will float free of the diamond, either
  // decast or return failure.
  Node* ctl = id->in(0);
  if (ctl != NULL && ctl->in(0) == iff) {
    if (id->is_ConstraintCast()) {
      return id->in(1);
    } else {
      // Don't know how to disentangle this value.
      return NULL;
    }
  }

  return id;
}

//------------------------------Identity---------------------------------------
// Check for Region being Identity.
Node *PhiNode::Identity( PhaseTransform *phase ) {
  // Check for no merging going on
  // (There used to be special-case code here when this->region->is_Loop.
  // It would check for a tributary phi on the backedge that the main phi
  // trivially, perhaps with a single cast.  The unique_input method
  // does all this and more, by reducing such tributaries to 'this'.)
  Node* uin = unique_input(phase);
  if (uin != NULL) {
    return uin;
  }

  int true_path = is_diamond_phi();
  if (true_path != 0) {
    Node* id = is_cmove_id(phase, true_path);
    if (id != NULL)  return id;
  }

  return this;                     // No identity
}

//-----------------------------unique_input------------------------------------
// Find the unique value, discounting top, self-loops, and casts.
// Return top if there are no inputs, and self if there are multiple.
Node* PhiNode::unique_input(PhaseTransform* phase) {
  //  1) One unique direct input, or
  //  2) some of the inputs have an intervening ConstraintCast and
  //     the type of input is the same or sharper (more specific)
  //     than the phi's type.
  //  3) an input is a self loop
  //
  //  1) input   or   2) input     or   3) input __
  //     /   \           /   \               \  /  \
  //     \   /          |    cast             phi  cast
  //      phi            \   /               /  \  /
  //                      phi               /    --

  Node* r = in(0);                      // RegionNode
  if (r == NULL)  return in(1);         // Already degraded to a Copy
  Node* uncasted_input = NULL; // The unique uncasted input (ConstraintCasts removed)
  Node* direct_input   = NULL; // The unique direct input

  for (uint i = 1, cnt = req(); i < cnt; ++i) {
    Node* rc = r->in(i);
    if (rc == NULL || phase->type(rc) == Type::TOP)
      continue;                 // ignore unreachable control path
    Node* n = in(i);
    if (n == NULL)
      continue;
    Node* un = n->uncast();
    if (un == NULL || un == this || phase->type(un) == Type::TOP) {
      continue; // ignore if top, or in(i) and "this" are in a data cycle
    }
    // Check for a unique uncasted input
    if (uncasted_input == NULL) {
      uncasted_input = un;
    } else if (uncasted_input != un) {
      uncasted_input = NodeSentinel; // no unique uncasted input
    }
    // Check for a unique direct input
    if (direct_input == NULL) {
      direct_input = n;
    } else if (direct_input != n) {
      direct_input = NodeSentinel; // no unique direct input
    }
  }
  if (direct_input == NULL) {
    return phase->C->top();        // no inputs
  }
  assert(uncasted_input != NULL,"");

  if (direct_input != NodeSentinel) {
    return direct_input;           // one unique direct input
  }
  if (uncasted_input != NodeSentinel &&
      phase->type(uncasted_input)->higher_equal(type())) {
    return uncasted_input;         // one unique uncasted input
  }

  // Nothing.
  return NULL;
}

//------------------------------is_x2logic-------------------------------------
// Check for simple convert-to-boolean pattern
// If:(C Bool) Region:(IfF IfT) Phi:(Region 0 1)
// Convert Phi to an ConvIB.
static Node *is_x2logic( PhaseGVN *phase, PhiNode *phi, int true_path ) {
  assert(true_path !=0, "only diamond shape graph expected");
  // Convert the true/false index into an expected 0/1 return.
  // Map 2->0 and 1->1.
  int flipped = 2-true_path;

  // is_diamond_phi() has guaranteed the correctness of the nodes sequence:
  // phi->region->if_proj->ifnode->bool->cmp
  Node *region = phi->in(0);
  Node *iff = region->in(1)->in(0);
  BoolNode *b = (BoolNode*)iff->in(1);
  const CmpNode *cmp = (CmpNode*)b->in(1);

  Node *zero = phi->in(1);
  Node *one  = phi->in(2);
  const Type *tzero = phase->type( zero );
  const Type *tone  = phase->type( one  );

  // Check for compare vs 0
  const Type *tcmp = phase->type(cmp->in(2));
  if( tcmp != TypeInt::ZERO && tcmp != TypePtr::NULL_PTR ) {
    // Allow cmp-vs-1 if the other input is bounded by 0-1
    if( !(tcmp == TypeInt::ONE && phase->type(cmp->in(1)) == TypeInt::BOOL) )
      return NULL;
    flipped = 1-flipped;        // Test is vs 1 instead of 0!
  }

  // Check for setting zero/one opposite expected
  if( tzero == TypeInt::ZERO ) {
    if( tone == TypeInt::ONE ) {
    } else return NULL;
  } else if( tzero == TypeInt::ONE ) {
    if( tone == TypeInt::ZERO ) {
      flipped = 1-flipped;
    } else return NULL;
  } else return NULL;

  // Check for boolean test backwards
  if( b->_test._test == BoolTest::ne ) {
  } else if( b->_test._test == BoolTest::eq ) {
    flipped = 1-flipped;
  } else return NULL;

  // Build int->bool conversion
  Node *n = new (phase->C, 2) Conv2BNode( cmp->in(1) );
  if( flipped )
    n = new (phase->C, 3) XorINode( phase->transform(n), phase->intcon(1) );

  return n;
}

//------------------------------is_cond_add------------------------------------
// Check for simple conditional add pattern:  "(P < Q) ? X+Y : X;"
// To be profitable the control flow has to disappear; there can be no other
// values merging here.  We replace the test-and-branch with:
// "(sgn(P-Q))&Y) + X".  Basically, convert "(P < Q)" into 0 or -1 by
// moving the carry bit from (P-Q) into a register with 'sbb EAX,EAX'.
// Then convert Y to 0-or-Y and finally add.
// This is a key transform for SpecJava _201_compress.
static Node* is_cond_add(PhaseGVN *phase, PhiNode *phi, int true_path) {
  assert(true_path !=0, "only diamond shape graph expected");

  // is_diamond_phi() has guaranteed the correctness of the nodes sequence:
  // phi->region->if_proj->ifnode->bool->cmp
  RegionNode *region = (RegionNode*)phi->in(0);
  Node *iff = region->in(1)->in(0);
  BoolNode* b = iff->in(1)->as_Bool();
  const CmpNode *cmp = (CmpNode*)b->in(1);

  // Make sure only merging this one phi here
  if (region->has_unique_phi() != phi)  return NULL;

  // Make sure each arm of the diamond has exactly one output, which we assume
  // is the region.  Otherwise, the control flow won't disappear.
  if (region->in(1)->outcnt() != 1) return NULL;
  if (region->in(2)->outcnt() != 1) return NULL;

  // Check for "(P < Q)" of type signed int
  if (b->_test._test != BoolTest::lt)  return NULL;
  if (cmp->Opcode() != Op_CmpI)        return NULL;

  Node *p = cmp->in(1);
  Node *q = cmp->in(2);
  Node *n1 = phi->in(  true_path);
  Node *n2 = phi->in(3-true_path);

  int op = n1->Opcode();
  if( op != Op_AddI           // Need zero as additive identity
      /*&&op != Op_SubI &&
      op != Op_AddP &&
      op != Op_XorI &&
      op != Op_OrI*/ )
    return NULL;

  Node *x = n2;
  Node *y = n1->in(1);
  if( n2 == n1->in(1) ) {
    y = n1->in(2);
  } else if( n2 == n1->in(1) ) {
  } else return NULL;

  // Not so profitable if compare and add are constants
  if( q->is_Con() && phase->type(q) != TypeInt::ZERO && y->is_Con() )
    return NULL;

  Node *cmplt = phase->transform( new (phase->C, 3) CmpLTMaskNode(p,q) );
  Node *j_and   = phase->transform( new (phase->C, 3) AndINode(cmplt,y) );
  return new (phase->C, 3) AddINode(j_and,x);
}

//------------------------------is_absolute------------------------------------
// Check for absolute value.
static Node* is_absolute( PhaseGVN *phase, PhiNode *phi_root, int true_path) {
  assert(true_path !=0, "only diamond shape graph expected");

  int  cmp_zero_idx = 0;        // Index of compare input where to look for zero
  int  phi_x_idx = 0;           // Index of phi input where to find naked x

  // ABS ends with the merge of 2 control flow paths.
  // Find the false path from the true path. With only 2 inputs, 3 - x works nicely.
  int false_path = 3 - true_path;

  // is_diamond_phi() has guaranteed the correctness of the nodes sequence:
  // phi->region->if_proj->ifnode->bool->cmp
  BoolNode *bol = phi_root->in(0)->in(1)->in(0)->in(1)->as_Bool();

  // Check bool sense
  switch( bol->_test._test ) {
  case BoolTest::lt: cmp_zero_idx = 1; phi_x_idx = true_path;  break;
  case BoolTest::le: cmp_zero_idx = 2; phi_x_idx = false_path; break;
  case BoolTest::gt: cmp_zero_idx = 2; phi_x_idx = true_path;  break;
  case BoolTest::ge: cmp_zero_idx = 1; phi_x_idx = false_path; break;
  default:           return NULL;                              break;
  }

  // Test is next
  Node *cmp = bol->in(1);
  const Type *tzero = NULL;
  switch( cmp->Opcode() ) {
  case Op_CmpF:    tzero = TypeF::ZERO; break; // Float ABS
  case Op_CmpD:    tzero = TypeD::ZERO; break; // Double ABS
  default: return NULL;
  }

  // Find zero input of compare; the other input is being abs'd
  Node *x = NULL;
  bool flip = false;
  if( phase->type(cmp->in(cmp_zero_idx)) == tzero ) {
    x = cmp->in(3 - cmp_zero_idx);
  } else if( phase->type(cmp->in(3 - cmp_zero_idx)) == tzero ) {
    // The test is inverted, we should invert the result...
    x = cmp->in(cmp_zero_idx);
    flip = true;
  } else {
    return NULL;
  }

  // Next get the 2 pieces being selected, one is the original value
  // and the other is the negated value.
  if( phi_root->in(phi_x_idx) != x ) return NULL;

  // Check other phi input for subtract node
  Node *sub = phi_root->in(3 - phi_x_idx);

  // Allow only Sub(0,X) and fail out for all others; Neg is not OK
  if( tzero == TypeF::ZERO ) {
    if( sub->Opcode() != Op_SubF ||
        sub->in(2) != x ||
        phase->type(sub->in(1)) != tzero ) return NULL;
    x = new (phase->C, 2) AbsFNode(x);
    if (flip) {
      x = new (phase->C, 3) SubFNode(sub->in(1), phase->transform(x));
    }
  } else {
    if( sub->Opcode() != Op_SubD ||
        sub->in(2) != x ||
        phase->type(sub->in(1)) != tzero ) return NULL;
    x = new (phase->C, 2) AbsDNode(x);
    if (flip) {
      x = new (phase->C, 3) SubDNode(sub->in(1), phase->transform(x));
    }
  }

  return x;
}

//------------------------------split_once-------------------------------------
// Helper for split_flow_path
static void split_once(PhaseIterGVN *igvn, Node *phi, Node *val, Node *n, Node *newn) {
  igvn->hash_delete(n);         // Remove from hash before hacking edges

  uint j = 1;
  for( uint i = phi->req()-1; i > 0; i-- ) {
    if( phi->in(i) == val ) {   // Found a path with val?
      // Add to NEW Region/Phi, no DU info
      newn->set_req( j++, n->in(i) );
      // Remove from OLD Region/Phi
      n->del_req(i);
    }
  }

  // Register the new node but do not transform it.  Cannot transform until the
  // entire Region/Phi conglerate has been hacked as a single huge transform.
  igvn->register_new_node_with_optimizer( newn );
  // Now I can point to the new node.
  n->add_req(newn);
  igvn->_worklist.push(n);
}

//------------------------------split_flow_path--------------------------------
// Check for merging identical values and split flow paths
static Node* split_flow_path(PhaseGVN *phase, PhiNode *phi) {
  BasicType bt = phi->type()->basic_type();
  if( bt == T_ILLEGAL || type2size[bt] <= 0 )
    return NULL;                // Bail out on funny non-value stuff
  if( phi->req() <= 3 )         // Need at least 2 matched inputs and a
    return NULL;                // third unequal input to be worth doing

  // Scan for a constant
  uint i;
  for( i = 1; i < phi->req()-1; i++ ) {
    Node *n = phi->in(i);
    if( !n ) return NULL;
    if( phase->type(n) == Type::TOP ) return NULL;
    if( n->Opcode() == Op_ConP || n->Opcode() == Op_ConN )
      break;
  }
  if( i >= phi->req() )         // Only split for constants
    return NULL;

  Node *val = phi->in(i);       // Constant to split for
  uint hit = 0;                 // Number of times it occurs

  for( ; i < phi->req(); i++ ){ // Count occurances of constant
    Node *n = phi->in(i);
    if( !n ) return NULL;
    if( phase->type(n) == Type::TOP ) return NULL;
    if( phi->in(i) == val )
      hit++;
  }

  if( hit <= 1 ||               // Make sure we find 2 or more
      hit == phi->req()-1 )     // and not ALL the same value
    return NULL;

  // Now start splitting out the flow paths that merge the same value.
  // Split first the RegionNode.
  PhaseIterGVN *igvn = phase->is_IterGVN();
  Node *r = phi->region();
  RegionNode *newr = new (phase->C, hit+1) RegionNode(hit+1);
  split_once(igvn, phi, val, r, newr);

  // Now split all other Phis than this one
  for (DUIterator_Fast kmax, k = r->fast_outs(kmax); k < kmax; k++) {
    Node* phi2 = r->fast_out(k);
    if( phi2->is_Phi() && phi2->as_Phi() != phi ) {
      PhiNode *newphi = PhiNode::make_blank(newr, phi2);
      split_once(igvn, phi, val, phi2, newphi);
    }
  }

  // Clean up this guy
  igvn->hash_delete(phi);
  for( i = phi->req()-1; i > 0; i-- ) {
    if( phi->in(i) == val ) {
      phi->del_req(i);
    }
  }
  phi->add_req(val);

  return phi;
}

//=============================================================================
//------------------------------simple_data_loop_check-------------------------
//  Try to determing if the phi node in a simple safe/unsafe data loop.
//  Returns:
// enum LoopSafety { Safe = 0, Unsafe, UnsafeLoop };
// Safe       - safe case when the phi and it's inputs reference only safe data
//              nodes;
// Unsafe     - the phi and it's inputs reference unsafe data nodes but there
//              is no reference back to the phi - need a graph walk
//              to determine if it is in a loop;
// UnsafeLoop - unsafe case when the phi references itself directly or through
//              unsafe data node.
//  Note: a safe data node is a node which could/never reference itself during
//  GVN transformations. For now it is Con, Proj, Phi, CastPP, CheckCastPP.
//  I mark Phi nodes as safe node not only because they can reference itself
//  but also to prevent mistaking the fallthrough case inside an outer loop
//  as dead loop when the phi references itselfs through an other phi.
PhiNode::LoopSafety PhiNode::simple_data_loop_check(Node *in) const {
  // It is unsafe loop if the phi node references itself directly.
  if (in == (Node*)this)
    return UnsafeLoop; // Unsafe loop
  // Unsafe loop if the phi node references itself through an unsafe data node.
  // Exclude cases with null inputs or data nodes which could reference
  // itself (safe for dead loops).
  if (in != NULL && !in->is_dead_loop_safe()) {
    // Check inputs of phi's inputs also.
    // It is much less expensive then full graph walk.
    uint cnt = in->req();
    uint i = (in->is_Proj() && !in->is_CFG())  ? 0 : 1;
    for (; i < cnt; ++i) {
      Node* m = in->in(i);
      if (m == (Node*)this)
        return UnsafeLoop; // Unsafe loop
      if (m != NULL && !m->is_dead_loop_safe()) {
        // Check the most common case (about 30% of all cases):
        // phi->Load/Store->AddP->(ConP ConP Con)/(Parm Parm Con).
        Node *m1 = (m->is_AddP() && m->req() > 3) ? m->in(1) : NULL;
        if (m1 == (Node*)this)
          return UnsafeLoop; // Unsafe loop
        if (m1 != NULL && m1 == m->in(2) &&
            m1->is_dead_loop_safe() && m->in(3)->is_Con()) {
          continue; // Safe case
        }
        // The phi references an unsafe node - need full analysis.
        return Unsafe;
      }
    }
  }
  return Safe; // Safe case - we can optimize the phi node.
}

//------------------------------is_unsafe_data_reference-----------------------
// If phi can be reached through the data input - it is data loop.
bool PhiNode::is_unsafe_data_reference(Node *in) const {
  assert(req() > 1, "");
  // First, check simple cases when phi references itself directly or
  // through an other node.
  LoopSafety safety = simple_data_loop_check(in);
  if (safety == UnsafeLoop)
    return true;  // phi references itself - unsafe loop
  else if (safety == Safe)
    return false; // Safe case - phi could be replaced with the unique input.

  // Unsafe case when we should go through data graph to determine
  // if the phi references itself.

  ResourceMark rm;

  Arena *a = Thread::current()->resource_area();
  Node_List nstack(a);
  VectorSet visited(a);

  nstack.push(in); // Start with unique input.
  visited.set(in->_idx);
  while (nstack.size() != 0) {
    Node* n = nstack.pop();
    uint cnt = n->req();
    uint i = (n->is_Proj() && !n->is_CFG()) ? 0 : 1;
    for (; i < cnt; i++) {
      Node* m = n->in(i);
      if (m == (Node*)this) {
        return true;    // Data loop
      }
      if (m != NULL && !m->is_dead_loop_safe()) { // Only look for unsafe cases.
        if (!visited.test_set(m->_idx))
          nstack.push(m);
      }
    }
  }
  return false; // The phi is not reachable from its inputs
}


//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.  Must preserve
// the CFG, but we can still strip out dead paths.
Node *PhiNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // The next should never happen after 6297035 fix.
  if( is_copy() )               // Already degraded to a Copy ?
    return NULL;                // No change

  Node *r = in(0);              // RegionNode
  assert(r->in(0) == NULL || !r->in(0)->is_Root(), "not a specially hidden merge");

  // Note: During parsing, phis are often transformed before their regions.
  // This means we have to use type_or_null to defend against untyped regions.
  if( phase->type_or_null(r) == Type::TOP ) // Dead code?
    return NULL;                // No change

  Node *top = phase->C->top();

  // The are 2 situations when only one valid phi's input is left
  // (in addition to Region input).
  // One: region is not loop - replace phi with this input.
  // Two: region is loop - replace phi with top since this data path is dead
  //                       and we need to break the dead data loop.
  Node* progress = NULL;        // Record if any progress made
  for( uint j = 1; j < req(); ++j ){ // For all paths in
    // Check unreachable control paths
    Node* rc = r->in(j);
    Node* n = in(j);            // Get the input
    if (rc == NULL || phase->type(rc) == Type::TOP) {
      if (n != top) {           // Not already top?
        set_req(j, top);        // Nuke it down
        progress = this;        // Record progress
      }
    }
  }

  Node* uin = unique_input(phase);
  if (uin == top) {             // Simplest case: no alive inputs.
    if (can_reshape)            // IGVN transformation
      return top;
    else
      return NULL;              // Identity will return TOP
  } else if (uin != NULL) {
    // Only one not-NULL unique input path is left.
    // Determine if this input is backedge of a loop.
    // (Skip new phis which have no uses and dead regions).
    if( outcnt() > 0 && r->in(0) != NULL ) {
      // First, take the short cut when we know it is a loop and
      // the EntryControl data path is dead.
      assert(!r->is_Loop() || r->req() == 3, "Loop node should have 3 inputs");
      // Then, check if there is a data loop when phi references itself directly
      // or through other data nodes.
      if( r->is_Loop() && !phase->eqv_uncast(uin, in(LoopNode::EntryControl)) ||
         !r->is_Loop() && is_unsafe_data_reference(uin) ) {
        // Break this data loop to avoid creation of a dead loop.
        if (can_reshape) {
          return top;
        } else {
          // We can't return top if we are in Parse phase - cut inputs only
          // let Identity to handle the case.
          replace_edge(uin, top);
          return NULL;
        }
      }
    }

    // One unique input.
    debug_only(Node* ident = Identity(phase));
    // The unique input must eventually be detected by the Identity call.
#ifdef ASSERT
    if (ident != uin && !ident->is_top()) {
      // print this output before failing assert
      r->dump(3);
      this->dump(3);
      ident->dump();
      uin->dump();
    }
#endif
    assert(ident == uin || ident->is_top(), "Identity must clean this up");
    return NULL;
  }


  Node* opt = NULL;
  int true_path = is_diamond_phi();
  if( true_path != 0 ) {
    // Check for CMove'ing identity. If it would be unsafe,
    // handle it here. In the safe case, let Identity handle it.
    Node* unsafe_id = is_cmove_id(phase, true_path);
    if( unsafe_id != NULL && is_unsafe_data_reference(unsafe_id) )
      opt = unsafe_id;

    // Check for simple convert-to-boolean pattern
    if( opt == NULL )
      opt = is_x2logic(phase, this, true_path);

    // Check for absolute value
    if( opt == NULL )
      opt = is_absolute(phase, this, true_path);

    // Check for conditional add
    if( opt == NULL && can_reshape )
      opt = is_cond_add(phase, this, true_path);

    // These 4 optimizations could subsume the phi:
    // have to check for a dead data loop creation.
    if( opt != NULL ) {
      if( opt == unsafe_id || is_unsafe_data_reference(opt) ) {
        // Found dead loop.
        if( can_reshape )
          return top;
        // We can't return top if we are in Parse phase - cut inputs only
        // to stop further optimizations for this phi. Identity will return TOP.
        assert(req() == 3, "only diamond merge phi here");
        set_req(1, top);
        set_req(2, top);
        return NULL;
      } else {
        return opt;
      }
    }
  }

  // Check for merging identical values and split flow paths
  if (can_reshape) {
    opt = split_flow_path(phase, this);
    // This optimization only modifies phi - don't need to check for dead loop.
    assert(opt == NULL || phase->eqv(opt, this), "do not elide phi");
    if (opt != NULL)  return opt;
  }

  // Split phis through memory merges, so that the memory merges will go away.
  // Piggy-back this transformation on the search for a unique input....
  // It will be as if the merged memory is the unique value of the phi.
  // (Do not attempt this optimization unless parsing is complete.
  // It would make the parser's memory-merge logic sick.)
  // (MergeMemNode is not dead_loop_safe - need to check for dead loop.)
  if (progress == NULL && can_reshape && type() == Type::MEMORY) {
    // see if this phi should be sliced
    uint merge_width = 0;
    bool saw_self = false;
    for( uint i=1; i<req(); ++i ) {// For all paths in
      Node *ii = in(i);
      if (ii->is_MergeMem()) {
        MergeMemNode* n = ii->as_MergeMem();
        merge_width = MAX2(merge_width, n->req());
        saw_self = saw_self || phase->eqv(n->base_memory(), this);
      }
    }

    // This restriction is temporarily necessary to ensure termination:
    if (!saw_self && adr_type() == TypePtr::BOTTOM)  merge_width = 0;

    if (merge_width > Compile::AliasIdxRaw) {
      // found at least one non-empty MergeMem
      const TypePtr* at = adr_type();
      if (at != TypePtr::BOTTOM) {
        // Patch the existing phi to select an input from the merge:
        // Phi:AT1(...MergeMem(m0, m1, m2)...) into
        //     Phi:AT1(...m1...)
        int alias_idx = phase->C->get_alias_index(at);
        for (uint i=1; i<req(); ++i) {
          Node *ii = in(i);
          if (ii->is_MergeMem()) {
            MergeMemNode* n = ii->as_MergeMem();
            // compress paths and change unreachable cycles to TOP
            // If not, we can update the input infinitely along a MergeMem cycle
            // Equivalent code is in MemNode::Ideal_common
            Node *m  = phase->transform(n);
            if (outcnt() == 0) {  // Above transform() may kill us!
              progress = phase->C->top();
              break;
            }
            // If tranformed to a MergeMem, get the desired slice
            // Otherwise the returned node represents memory for every slice
            Node *new_mem = (m->is_MergeMem()) ?
                             m->as_MergeMem()->memory_at(alias_idx) : m;
            // Update input if it is progress over what we have now
            if (new_mem != ii) {
              set_req(i, new_mem);
              progress = this;
            }
          }
        }
      } else {
        // We know that at least one MergeMem->base_memory() == this
        // (saw_self == true). If all other inputs also references this phi
        // (directly or through data nodes) - it is dead loop.
        bool saw_safe_input = false;
        for (uint j = 1; j < req(); ++j) {
          Node *n = in(j);
          if (n->is_MergeMem() && n->as_MergeMem()->base_memory() == this)
            continue;              // skip known cases
          if (!is_unsafe_data_reference(n)) {
            saw_safe_input = true; // found safe input
            break;
          }
        }
        if (!saw_safe_input)
          return top; // all inputs reference back to this phi - dead loop

        // Phi(...MergeMem(m0, m1:AT1, m2:AT2)...) into
        //     MergeMem(Phi(...m0...), Phi:AT1(...m1...), Phi:AT2(...m2...))
        PhaseIterGVN *igvn = phase->is_IterGVN();
        Node* hook = new (phase->C, 1) Node(1);
        PhiNode* new_base = (PhiNode*) clone();
        // Must eagerly register phis, since they participate in loops.
        if (igvn) {
          igvn->register_new_node_with_optimizer(new_base);
          hook->add_req(new_base);
        }
        MergeMemNode* result = MergeMemNode::make(phase->C, new_base);
        for (uint i = 1; i < req(); ++i) {
          Node *ii = in(i);
          if (ii->is_MergeMem()) {
            MergeMemNode* n = ii->as_MergeMem();
            for (MergeMemStream mms(result, n); mms.next_non_empty2(); ) {
              // If we have not seen this slice yet, make a phi for it.
              bool made_new_phi = false;
              if (mms.is_empty()) {
                Node* new_phi = new_base->slice_memory(mms.adr_type(phase->C));
                made_new_phi = true;
                if (igvn) {
                  igvn->register_new_node_with_optimizer(new_phi);
                  hook->add_req(new_phi);
                }
                mms.set_memory(new_phi);
              }
              Node* phi = mms.memory();
              assert(made_new_phi || phi->in(i) == n, "replace the i-th merge by a slice");
              phi->set_req(i, mms.memory2());
            }
          }
        }
        // Distribute all self-loops.
        { // (Extra braces to hide mms.)
          for (MergeMemStream mms(result); mms.next_non_empty(); ) {
            Node* phi = mms.memory();
            for (uint i = 1; i < req(); ++i) {
              if (phi->in(i) == this)  phi->set_req(i, phi);
            }
          }
        }
        // now transform the new nodes, and return the mergemem
        for (MergeMemStream mms(result); mms.next_non_empty(); ) {
          Node* phi = mms.memory();
          mms.set_memory(phase->transform(phi));
        }
        if (igvn) { // Unhook.
          igvn->hash_delete(hook);
          for (uint i = 1; i < hook->req(); i++) {
            hook->set_req(i, NULL);
          }
        }
        // Replace self with the result.
        return result;
      }
    }
    //
    // Other optimizations on the memory chain
    //
    const TypePtr* at = adr_type();
    for( uint i=1; i<req(); ++i ) {// For all paths in
      Node *ii = in(i);
      Node *new_in = MemNode::optimize_memory_chain(ii, at, phase);
      if (ii != new_in ) {
        set_req(i, new_in);
        progress = this;
      }
    }
  }

#ifdef _LP64
  // Push DecodeN down through phi.
  // The rest of phi graph will transform by split EncodeP node though phis up.
  if (UseCompressedOops && can_reshape && progress == NULL) {
    bool may_push = true;
    bool has_decodeN = false;
    Node* in_decodeN = NULL;
    for (uint i=1; i<req(); ++i) {// For all paths in
      Node *ii = in(i);
      if (ii->is_DecodeN() && ii->bottom_type() == bottom_type()) {
        has_decodeN = true;
        in_decodeN = ii->in(1);
      } else if (!ii->is_Phi()) {
        may_push = false;
      }
    }

    if (has_decodeN && may_push) {
      PhaseIterGVN *igvn = phase->is_IterGVN();
      // Note: in_decodeN is used only to define the type of new phi here.
      PhiNode *new_phi = PhiNode::make_blank(in(0), in_decodeN);
      uint orig_cnt = req();
      for (uint i=1; i<req(); ++i) {// For all paths in
        Node *ii = in(i);
        Node* new_ii = NULL;
        if (ii->is_DecodeN()) {
          assert(ii->bottom_type() == bottom_type(), "sanity");
          new_ii = ii->in(1);
        } else {
          assert(ii->is_Phi(), "sanity");
          if (ii->as_Phi() == this) {
            new_ii = new_phi;
          } else {
            new_ii = new (phase->C, 2) EncodePNode(ii, in_decodeN->bottom_type());
            igvn->register_new_node_with_optimizer(new_ii);
          }
        }
        new_phi->set_req(i, new_ii);
      }
      igvn->register_new_node_with_optimizer(new_phi, this);
      progress = new (phase->C, 2) DecodeNNode(new_phi, bottom_type());
    }
  }
#endif

  return progress;              // Return any progress
}

//------------------------------is_tripcount-----------------------------------
bool PhiNode::is_tripcount() const {
  return (in(0) != NULL && in(0)->is_CountedLoop() &&
          in(0)->as_CountedLoop()->phi() == this);
}

//------------------------------out_RegMask------------------------------------
const RegMask &PhiNode::in_RegMask(uint i) const {
  return i ? out_RegMask() : RegMask::Empty;
}

const RegMask &PhiNode::out_RegMask() const {
  uint ideal_reg = Matcher::base2reg[_type->base()];
  assert( ideal_reg != Node::NotAMachineReg, "invalid type at Phi" );
  if( ideal_reg == 0 ) return RegMask::Empty;
  return *(Compile::current()->matcher()->idealreg2spillmask[ideal_reg]);
}

#ifndef PRODUCT
void PhiNode::dump_spec(outputStream *st) const {
  TypeNode::dump_spec(st);
  if (is_tripcount()) {
    st->print(" #tripcount");
  }
}
#endif


//=============================================================================
const Type *GotoNode::Value( PhaseTransform *phase ) const {
  // If the input is reachable, then we are executed.
  // If the input is not reachable, then we are not executed.
  return phase->type(in(0));
}

Node *GotoNode::Identity( PhaseTransform *phase ) {
  return in(0);                // Simple copy of incoming control
}

const RegMask &GotoNode::out_RegMask() const {
  return RegMask::Empty;
}

//=============================================================================
const RegMask &JumpNode::out_RegMask() const {
  return RegMask::Empty;
}

//=============================================================================
const RegMask &JProjNode::out_RegMask() const {
  return RegMask::Empty;
}

//=============================================================================
const RegMask &CProjNode::out_RegMask() const {
  return RegMask::Empty;
}



//=============================================================================

uint PCTableNode::hash() const { return Node::hash() + _size; }
uint PCTableNode::cmp( const Node &n ) const
{ return _size == ((PCTableNode&)n)._size; }

const Type *PCTableNode::bottom_type() const {
  const Type** f = TypeTuple::fields(_size);
  for( uint i = 0; i < _size; i++ ) f[i] = Type::CONTROL;
  return TypeTuple::make(_size, f);
}

//------------------------------Value------------------------------------------
// Compute the type of the PCTableNode.  If reachable it is a tuple of
// Control, otherwise the table targets are not reachable
const Type *PCTableNode::Value( PhaseTransform *phase ) const {
  if( phase->type(in(0)) == Type::CONTROL )
    return bottom_type();
  return Type::TOP;             // All paths dead?  Then so are we
}

//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.  Strip out
// control copies
Node *PCTableNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  return remove_dead_region(phase, can_reshape) ? this : NULL;
}

//=============================================================================
uint JumpProjNode::hash() const {
  return Node::hash() + _dest_bci;
}

uint JumpProjNode::cmp( const Node &n ) const {
  return ProjNode::cmp(n) &&
    _dest_bci == ((JumpProjNode&)n)._dest_bci;
}

#ifndef PRODUCT
void JumpProjNode::dump_spec(outputStream *st) const {
  ProjNode::dump_spec(st);
   st->print("@bci %d ",_dest_bci);
}
#endif

//=============================================================================
//------------------------------Value------------------------------------------
// Check for being unreachable, or for coming from a Rethrow.  Rethrow's cannot
// have the default "fall_through_index" path.
const Type *CatchNode::Value( PhaseTransform *phase ) const {
  // Unreachable?  Then so are all paths from here.
  if( phase->type(in(0)) == Type::TOP ) return Type::TOP;
  // First assume all paths are reachable
  const Type** f = TypeTuple::fields(_size);
  for( uint i = 0; i < _size; i++ ) f[i] = Type::CONTROL;
  // Identify cases that will always throw an exception
  // () rethrow call
  // () virtual or interface call with NULL receiver
  // () call is a check cast with incompatible arguments
  if( in(1)->is_Proj() ) {
    Node *i10 = in(1)->in(0);
    if( i10->is_Call() ) {
      CallNode *call = i10->as_Call();
      // Rethrows always throw exceptions, never return
      if (call->entry_point() == OptoRuntime::rethrow_stub()) {
        f[CatchProjNode::fall_through_index] = Type::TOP;
      } else if( call->req() > TypeFunc::Parms ) {
        const Type *arg0 = phase->type( call->in(TypeFunc::Parms) );
        // Check for null reciever to virtual or interface calls
        if( call->is_CallDynamicJava() &&
            arg0->higher_equal(TypePtr::NULL_PTR) ) {
          f[CatchProjNode::fall_through_index] = Type::TOP;
        }
      } // End of if not a runtime stub
    } // End of if have call above me
  } // End of slot 1 is not a projection
  return TypeTuple::make(_size, f);
}

//=============================================================================
uint CatchProjNode::hash() const {
  return Node::hash() + _handler_bci;
}


uint CatchProjNode::cmp( const Node &n ) const {
  return ProjNode::cmp(n) &&
    _handler_bci == ((CatchProjNode&)n)._handler_bci;
}


//------------------------------Identity---------------------------------------
// If only 1 target is possible, choose it if it is the main control
Node *CatchProjNode::Identity( PhaseTransform *phase ) {
  // If my value is control and no other value is, then treat as ID
  const TypeTuple *t = phase->type(in(0))->is_tuple();
  if (t->field_at(_con) != Type::CONTROL)  return this;
  // If we remove the last CatchProj and elide the Catch/CatchProj, then we
  // also remove any exception table entry.  Thus we must know the call
  // feeding the Catch will not really throw an exception.  This is ok for
  // the main fall-thru control (happens when we know a call can never throw
  // an exception) or for "rethrow", because a further optimnization will
  // yank the rethrow (happens when we inline a function that can throw an
  // exception and the caller has no handler).  Not legal, e.g., for passing
  // a NULL receiver to a v-call, or passing bad types to a slow-check-cast.
  // These cases MUST throw an exception via the runtime system, so the VM
  // will be looking for a table entry.
  Node *proj = in(0)->in(1);    // Expect a proj feeding CatchNode
  CallNode *call;
  if (_con != TypeFunc::Control && // Bail out if not the main control.
      !(proj->is_Proj() &&      // AND NOT a rethrow
        proj->in(0)->is_Call() &&
        (call = proj->in(0)->as_Call()) &&
        call->entry_point() == OptoRuntime::rethrow_stub()))
    return this;

  // Search for any other path being control
  for (uint i = 0; i < t->cnt(); i++) {
    if (i != _con && t->field_at(i) == Type::CONTROL)
      return this;
  }
  // Only my path is possible; I am identity on control to the jump
  return in(0)->in(0);
}


#ifndef PRODUCT
void CatchProjNode::dump_spec(outputStream *st) const {
  ProjNode::dump_spec(st);
  st->print("@bci %d ",_handler_bci);
}
#endif

//=============================================================================
//------------------------------Identity---------------------------------------
// Check for CreateEx being Identity.
Node *CreateExNode::Identity( PhaseTransform *phase ) {
  if( phase->type(in(1)) == Type::TOP ) return in(1);
  if( phase->type(in(0)) == Type::TOP ) return in(0);
  // We only come from CatchProj, unless the CatchProj goes away.
  // If the CatchProj is optimized away, then we just carry the
  // exception oop through.
  CallNode *call = in(1)->in(0)->as_Call();

  return ( in(0)->is_CatchProj() && in(0)->in(0)->in(1) == in(1) )
    ? this
    : call->in(TypeFunc::Parms);
}

//=============================================================================
//------------------------------Value------------------------------------------
// Check for being unreachable.
const Type *NeverBranchNode::Value( PhaseTransform *phase ) const {
  if (!in(0) || in(0)->is_top()) return Type::TOP;
  return bottom_type();
}

//------------------------------Ideal------------------------------------------
// Check for no longer being part of a loop
Node *NeverBranchNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if (can_reshape && !in(0)->is_Loop()) {
    // Dead code elimination can sometimes delete this projection so
    // if it's not there, there's nothing to do.
    Node* fallthru = proj_out(0);
    if (fallthru != NULL) {
      phase->is_IterGVN()->subsume_node(fallthru, in(0));
    }
    return phase->C->top();
  }
  return NULL;
}

#ifndef PRODUCT
void NeverBranchNode::format( PhaseRegAlloc *ra_, outputStream *st) const {
  st->print("%s", Name());
}
#endif
