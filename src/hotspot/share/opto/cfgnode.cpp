/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/objArrayKlass.hpp"
#include "opto/addnode.hpp"
#include "opto/castnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/loopnode.hpp"
#include "opto/machnode.hpp"
#include "opto/movenode.hpp"
#include "opto/narrowptrnode.hpp"
#include "opto/mulnode.hpp"
#include "opto/phaseX.hpp"
#include "opto/regalloc.hpp"
#include "opto/regmask.hpp"
#include "opto/runtime.hpp"
#include "opto/subnode.hpp"
#include "opto/vectornode.hpp"
#include "utilities/vmError.hpp"

// Portions of code courtesy of Clifford Click

// Optimization - Graph Style

//=============================================================================
//------------------------------Value------------------------------------------
// Compute the type of the RegionNode.
const Type* RegionNode::Value(PhaseGVN* phase) const {
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
Node* RegionNode::Identity(PhaseGVN* phase) {
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
    return nullptr;
  Node *progress = nullptr;        // Progress flag
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
          return nullptr;        // Only flatten if no Phi users
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
      igvn->_worklist.push(r);
      // Clobber pointer to the now dead 'r'
      region->set_req(i, phase->C->top());
    }
  }

  return progress;
}



//--------------------------------has_phi--------------------------------------
// Helper function: Return any PhiNode that uses this region or null
PhiNode* RegionNode::has_phi() const {
  for (DUIterator_Fast imax, i = fast_outs(imax); i < imax; i++) {
    Node* phi = fast_out(i);
    if (phi->is_Phi()) {   // Check for Phi users
      assert(phi->in(0) == (Node*)this, "phi uses region only via in(0)");
      return phi->as_Phi();  // this one is good enough
    }
  }

  return nullptr;
}


//-----------------------------has_unique_phi----------------------------------
// Helper function: Return the only PhiNode that uses this region or null
PhiNode* RegionNode::has_unique_phi() const {
  // Check that only one use is a Phi
  PhiNode* only_phi = nullptr;
  for (DUIterator_Fast imax, i = fast_outs(imax); i < imax; i++) {
    Node* phi = fast_out(i);
    if (phi->is_Phi()) {   // Check for Phi users
      assert(phi->in(0) == (Node*)this, "phi uses region only via in(0)");
      if (only_phi == nullptr) {
        only_phi = phi->as_Phi();
      } else {
        return nullptr;  // multiple phis
      }
    }
  }

  return only_phi;
}


//------------------------------check_phi_clipping-----------------------------
// Helper function for RegionNode's identification of FP clipping
// Check inputs to the Phi
static bool check_phi_clipping( PhiNode *phi, ConNode * &min, uint &min_idx, ConNode * &max, uint &max_idx, Node * &val, uint &val_idx ) {
  min     = nullptr;
  max     = nullptr;
  val     = nullptr;
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
          if( min == nullptr ) {
            min     = n->Opcode() == Op_ConI ? (ConNode*)n : nullptr;
            min_idx = j;
          } else {
            max     = n->Opcode() == Op_ConI ? (ConNode*)n : nullptr;
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
  top_if = nullptr;
  bot_if = nullptr;

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
    if( in10 != nullptr && in10->is_If() &&
        in20 != nullptr && in20->is_If() &&
        in30 != nullptr && in30->is_If() && in10 == in20 &&
        (in1->Opcode() != in2->Opcode()) ) {
      Node  *in100 = in10->in(0);
      Node *in1000 = (in100 != nullptr && in100->is_Proj()) ? in100->in(0) : nullptr;
      // Check that control for in10 comes from other branch of IF from in3
      if( in1000 != nullptr && in1000->is_If() &&
          in30 == in1000 && (in3->Opcode() != in100->Opcode()) ) {
        // Control pattern checks
        top_if = (IfNode*)in1000;
        bot_if = (IfNode*)in10;
      }
    }
  }

  return (top_if != nullptr);
}


//------------------------------check_convf2i_clipping-------------------------
// Helper function for RegionNode's identification of FP clipping
// Verify that the value input to the phi comes from "ConvF2I; LShift; RShift"
static bool check_convf2i_clipping( PhiNode *phi, uint idx, ConvF2INode * &convf2i, Node *min, Node *max) {
  convf2i = nullptr;

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
// Check if the RegionNode is part of an unsafe loop and unreachable from root.
bool RegionNode::is_unreachable_region(const PhaseGVN* phase) {
  Node* top = phase->C->top();
  assert(req() == 2 || (req() == 3 && in(1) != nullptr && in(2) == top), "sanity check arguments");
  if (_is_unreachable_region) {
    // Return cached result from previous evaluation which should still be valid
    assert(is_unreachable_from_root(phase), "walk the graph again and check if its indeed unreachable");
    return true;
  }

  // First, cut the simple case of fallthrough region when NONE of
  // region's phis references itself directly or through a data node.
  if (is_possible_unsafe_loop(phase)) {
    // If we have a possible unsafe loop, check if the region node is actually unreachable from root.
    if (is_unreachable_from_root(phase)) {
      _is_unreachable_region = true;
      return true;
    }
  }
  return false;
}

bool RegionNode::is_possible_unsafe_loop(const PhaseGVN* phase) const {
  uint max = outcnt();
  uint i;
  for (i = 0; i < max; i++) {
    Node* n = raw_out(i);
    if (n != nullptr && n->is_Phi()) {
      PhiNode* phi = n->as_Phi();
      assert(phi->in(0) == this, "sanity check phi");
      if (phi->outcnt() == 0) {
        continue; // Safe case - no loops
      }
      if (phi->outcnt() == 1) {
        Node* u = phi->raw_out(0);
        // Skip if only one use is an other Phi or Call or Uncommon trap.
        // It is safe to consider this case as fallthrough.
        if (u != nullptr && (u->is_Phi() || u->is_CFG())) {
          continue;
        }
      }
      // Check when phi references itself directly or through an other node.
      if (phi->as_Phi()->simple_data_loop_check(phi->in(1)) >= PhiNode::Unsafe) {
        break; // Found possible unsafe data loop.
      }
    }
  }
  if (i >= max) {
    return false; // An unsafe case was NOT found - don't need graph walk.
  }
  return true;
}

bool RegionNode::is_unreachable_from_root(const PhaseGVN* phase) const {
  ResourceMark rm;
  Node_List nstack;
  VectorSet visited;

  // Mark all control nodes reachable from root outputs
  Node* n = (Node*)phase->C->root();
  nstack.push(n);
  visited.set(n->_idx);
  while (nstack.size() != 0) {
    n = nstack.pop();
    uint max = n->outcnt();
    for (uint i = 0; i < max; i++) {
      Node* m = n->raw_out(i);
      if (m != nullptr && m->is_CFG()) {
        if (m == this) {
          return false; // We reached the Region node - it is not dead.
        }
        if (!visited.test_set(m->_idx))
          nstack.push(m);
      }
    }
  }
  return true; // The Region node is unreachable - it is dead.
}

#ifdef ASSERT
// Is this region in an infinite subgraph?
// (no path to root except through false NeverBranch exit)
bool RegionNode::is_in_infinite_subgraph() {
  ResourceMark rm;
  Unique_Node_List worklist;
  worklist.push(this);
  return RegionNode::are_all_nodes_in_infinite_subgraph(worklist);
}

// Are all nodes in worklist in infinite subgraph?
// (no path to root except through false NeverBranch exit)
// worklist is directly used for the traversal
bool RegionNode::are_all_nodes_in_infinite_subgraph(Unique_Node_List& worklist) {
  // BFS traversal down the CFG, except through NeverBranch exits
  for (uint i = 0; i < worklist.size(); ++i) {
    Node* n = worklist.at(i);
    assert(n->is_CFG(), "only traverse CFG");
    if (n->is_Root()) {
      // Found root -> there was an exit!
      return false;
    } else if (n->is_NeverBranch()) {
      // Only follow the loop-internal projection, not the NeverBranch exit
      ProjNode* proj = n->as_NeverBranch()->proj_out_or_null(0);
      assert(proj != nullptr, "must find loop-internal projection of NeverBranch");
      worklist.push(proj);
    } else {
      // Traverse all CFG outputs
      for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
        Node* use = n->fast_out(i);
        if (use->is_CFG()) {
          worklist.push(use);
        }
      }
    }
  }
  // No exit found for any loop -> all are infinite
  return true;
}
#endif //ASSERT

void RegionNode::set_loop_status(RegionNode::LoopStatus status) {
  assert(loop_status() == RegionNode::LoopStatus::NeverIrreducibleEntry, "why set our status again?");
  _loop_status = status;
}

#ifdef ASSERT
void RegionNode::verify_can_be_irreducible_entry() const {
  assert(loop_status() == RegionNode::LoopStatus::MaybeIrreducibleEntry, "must be marked irreducible");
  assert(!is_Loop(), "LoopNode cannot be irreducible loop entry");
}
#endif //ASSERT

void RegionNode::try_clean_mem_phis(PhaseIterGVN* igvn) {
  // Incremental inlining + PhaseStringOpts sometimes produce:
  //
  // cmpP with 1 top input
  //           |
  //          If
  //         /  \
  //   IfFalse  IfTrue  /- Some Node
  //         \  /      /    /
  //        Region    / /-MergeMem
  //             \---Phi
  //
  //
  // It's expected by PhaseStringOpts that the Region goes away and is
  // replaced by If's control input but because there's still a Phi,
  // the Region stays in the graph. The top input from the cmpP is
  // propagated forward and a subgraph that is useful goes away. The
  // code in PhiNode::try_clean_memory_phi() replaces the Phi with the
  // MergeMem in order to remove the Region if its last phi dies.

  if (!is_diamond()) {
    return;
  }

  for (DUIterator_Fast imax, i = fast_outs(imax); i < imax; i++) {
    Node* phi = fast_out(i);
    if (phi->is_Phi() && phi->as_Phi()->try_clean_memory_phi(igvn)) {
      --i;
      --imax;
    }
  }
}

// Does this region merge a simple diamond formed by a proper IfNode?
//
//              Cmp
//              /
//     ctrl   Bool
//       \    /
//       IfNode
//      /      \
//  IfFalse   IfTrue
//      \      /
//       Region
bool RegionNode::is_diamond() const {
  if (req() != 3) {
    return false;
  }

  Node* left_path = in(1);
  Node* right_path = in(2);
  if (left_path == nullptr || right_path == nullptr) {
    return false;
  }
  Node* diamond_if = left_path->in(0);
  if (diamond_if == nullptr || !diamond_if->is_If() || diamond_if != right_path->in(0)) {
    // Not an IfNode merging a diamond or TOP.
    return false;
  }

  // Check for a proper bool/cmp
  const Node* bol = diamond_if->in(1);
  if (!bol->is_Bool()) {
    return false;
  }
  const Node* cmp = bol->in(1);
  if (!cmp->is_Cmp()) {
    return false;
  }
  return true;
}
//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.  Must preserve
// the CFG, but we can still strip out dead paths.
Node *RegionNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if( !can_reshape && !in(0) ) return nullptr;     // Already degraded to a Copy
  assert(!in(0) || !in(0)->is_Root(), "not a specially hidden merge");

  // Check for RegionNode with no Phi users and both inputs come from either
  // arm of the same IF.  If found, then the control-flow split is useless.
  bool has_phis = false;
  if (can_reshape) {            // Need DU info to check for Phi users
    try_clean_mem_phis(phase->is_IterGVN());
    has_phis = (has_phi() != nullptr);       // Cache result

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
            set_req(j, nullptr);
            return this;      // Record progress
          }
        }
      }
    }
  }

  // Remove TOP or null input paths. If only 1 input path remains, this Region
  // degrades to a copy.
  bool add_to_worklist = true;
  bool modified = false;
  int cnt = 0;                  // Count of values merging
  DEBUG_ONLY( int cnt_orig = req(); ) // Save original inputs count
  DEBUG_ONLY( uint outcnt_orig = outcnt(); )
  int del_it = 0;               // The last input path we delete
  bool found_top = false; // irreducible loops need to check reachability if we find TOP
  // For all inputs...
  for( uint i=1; i<req(); ++i ){// For all paths in
    Node *n = in(i);            // Get the input
    if( n != nullptr ) {
      // Remove useless control copy inputs
      if( n->is_Region() && n->as_Region()->is_copy() ) {
        set_req(i, n->nonnull_req());
        modified = true;
        i--;
        continue;
      }
      if( n->is_Proj() ) {      // Remove useless rethrows
        Node *call = n->in(0);
        if (call->is_Call() && call->as_Call()->entry_point() == OptoRuntime::rethrow_stub()) {
          set_req(i, call->in(0));
          modified = true;
          i--;
          continue;
        }
      }
      if( phase->type(n) == Type::TOP ) {
        set_req_X(i, nullptr, phase); // Ignore TOP inputs
        modified = true;
        found_top = true;
        i--;
        continue;
      }
      cnt++;                    // One more value merging
    } else if (can_reshape) {   // Else found dead path with DU info
      PhaseIterGVN *igvn = phase->is_IterGVN();
      del_req(i);               // Yank path from self
      del_it = i;

      for (DUIterator_Fast jmax, j = fast_outs(jmax); j < jmax; j++) {
        Node* use = fast_out(j);

        if (use->req() != req() && use->is_Phi()) {
          assert(use->in(0) == this, "unexpected control input");
          igvn->hash_delete(use);          // Yank from hash before hacking edges
          use->set_req_X(i, nullptr, igvn);// Correct DU info
          use->del_req(i);                 // Yank path from Phis
        }
      }

      if (add_to_worklist) {
        igvn->add_users_to_worklist(this);
        add_to_worklist = false;
      }

      i--;
    }
  }

  assert(outcnt() == outcnt_orig, "not expect to remove any use");

  if (can_reshape && found_top && loop_status() == RegionNode::LoopStatus::MaybeIrreducibleEntry) {
    // Is it a dead irreducible loop?
    // If an irreducible loop loses one of the multiple entries
    // that went into the loop head, or any secondary entries,
    // we need to verify if the irreducible loop is still reachable,
    // as the special logic in is_unreachable_region only works
    // for reducible loops.
    if (is_unreachable_from_root(phase)) {
      // The irreducible loop is dead - must remove it
      PhaseIterGVN* igvn = phase->is_IterGVN();
      remove_unreachable_subgraph(igvn);
      return nullptr;
    }
  } else if (can_reshape && cnt == 1) {
    // Is it dead loop?
    // If it is LoopNopde it had 2 (+1 itself) inputs and
    // one of them was cut. The loop is dead if it was EntryContol.
    // Loop node may have only one input because entry path
    // is removed in PhaseIdealLoop::Dominators().
    assert(!this->is_Loop() || cnt_orig <= 3, "Loop node should have 3 or less inputs");
    if ((this->is_Loop() && (del_it == LoopNode::EntryControl ||
                             (del_it == 0 && is_unreachable_region(phase)))) ||
        (!this->is_Loop() && has_phis && is_unreachable_region(phase))) {
      PhaseIterGVN* igvn = phase->is_IterGVN();
      remove_unreachable_subgraph(igvn);
      return nullptr;
    }
  }

  if( cnt <= 1 ) {              // Only 1 path in?
    set_req(0, nullptr);        // Null control input for region copy
    if( cnt == 0 && !can_reshape) { // Parse phase - leave the node as it is.
      // No inputs or all inputs are null.
      return nullptr;
    } else if (can_reshape) {   // Optimization phase - remove the node
      PhaseIterGVN *igvn = phase->is_IterGVN();
      // Strip mined (inner) loop is going away, remove outer loop.
      if (is_CountedLoop() &&
          as_Loop()->is_strip_mined()) {
        Node* outer_sfpt = as_CountedLoop()->outer_safepoint();
        Node* outer_out = as_CountedLoop()->outer_loop_exit();
        if (outer_sfpt != nullptr && outer_out != nullptr) {
          Node* in = outer_sfpt->in(0);
          igvn->replace_node(outer_out, in);
          LoopNode* outer = as_CountedLoop()->outer_loop();
          igvn->replace_input_of(outer, LoopNode::LoopBackControl, igvn->C->top());
        }
      }
      if (is_CountedLoop()) {
        Node* opaq = as_CountedLoop()->is_canonical_loop_entry();
        if (opaq != nullptr) {
          // This is not a loop anymore. No need to keep the Opaque1 node on the test that guards the loop as it won't be
          // subject to further loop opts.
          assert(opaq->Opcode() == Op_OpaqueZeroTripGuard, "");
          igvn->replace_node(opaq, opaq->in(1));
        }
      }
      Node *parent_ctrl;
      if( cnt == 0 ) {
        assert( req() == 1, "no inputs expected" );
        // During IGVN phase such region will be subsumed by TOP node
        // so region's phis will have TOP as control node.
        // Kill phis here to avoid it.
        // Also set other user's input to top.
        parent_ctrl = phase->C->top();
      } else {
        // The fallthrough case since we already checked dead loops above.
        parent_ctrl = in(1);
        assert(parent_ctrl != nullptr, "Region is a copy of some non-null control");
        assert(parent_ctrl != this, "Close dead loop");
      }
      if (add_to_worklist) {
        igvn->add_users_to_worklist(this); // Check for further allowed opts
      }
      for (DUIterator_Last imin, i = last_outs(imin); i >= imin; --i) {
        Node* n = last_out(i);
        igvn->hash_delete(n); // Remove from worklist before modifying edges
        if (n->outcnt() == 0) {
          int uses_found = n->replace_edge(this, phase->C->top(), igvn);
          if (uses_found > 1) { // (--i) done at the end of the loop.
            i -= (uses_found - 1);
          }
          continue;
        }
        if( n->is_Phi() ) {   // Collapse all Phis
          // Eagerly replace phis to avoid regionless phis.
          Node* in;
          if( cnt == 0 ) {
            assert( n->req() == 1, "No data inputs expected" );
            in = parent_ctrl; // replaced by top
          } else {
            assert( n->req() == 2 &&  n->in(1) != nullptr, "Only one data input expected" );
            in = n->in(1);               // replaced by unique input
            if( n->as_Phi()->is_unsafe_data_reference(in) )
              in = phase->C->top();      // replaced by top
          }
          igvn->replace_node(n, in);
        }
        else if( n->is_Region() ) { // Update all incoming edges
          assert(n != this, "Must be removed from DefUse edges");
          int uses_found = n->replace_edge(this, parent_ctrl, igvn);
          if (uses_found > 1) { // (--i) done at the end of the loop.
            i -= (uses_found - 1);
          }
        }
        else {
          assert(n->in(0) == this, "Expect RegionNode to be control parent");
          n->set_req(0, parent_ctrl);
        }
#ifdef ASSERT
        for( uint k=0; k < n->req(); k++ ) {
          assert(n->in(k) != this, "All uses of RegionNode should be gone");
        }
#endif
      }
      // Remove the RegionNode itself from DefUse info
      igvn->remove_dead_node(this);
      return nullptr;
    }
    return this;                // Record progress
  }


  // If a Region flows into a Region, merge into one big happy merge.
  if (can_reshape) {
    Node *m = merge_region(this, phase);
    if (m != nullptr)  return m;
  }

  // Check if this region is the root of a clipping idiom on floats
  if( ConvertFloat2IntClipping && can_reshape && req() == 4 ) {
    // Check that only one use is a Phi and that it simplifies to two constants +
    PhiNode* phi = has_unique_phi();
    if (phi != nullptr) {          // One Phi user
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
          Node   *top_in = nullptr;   // value being compared against
          Node   *bot_in = nullptr;
          if( check_compare_clipping( true,  bot_if, min, bot_in ) &&
              check_compare_clipping( false, top_if, max, top_in ) ) {
            if( bot_in == top_in ) {
              PhaseIterGVN *gvn = phase->is_IterGVN();
              assert( gvn != nullptr, "Only had DefUse info in IterGVN");
              // Only remaining check is that bot_in == top_in == (Phi's val + mods)

              // Check for the ConvF2INode
              ConvF2INode *convf2i;
              if( check_convf2i_clipping( phi, val_idx, convf2i, min, max ) &&
                convf2i->in(1) == bot_in ) {
                // Matched pattern, including LShiftI; RShiftI, replace with integer compares
                // max test
                Node *cmp   = gvn->register_new_node_with_optimizer(new CmpINode( convf2i, min ));
                Node *boo   = gvn->register_new_node_with_optimizer(new BoolNode( cmp, BoolTest::lt ));
                IfNode *iff = (IfNode*)gvn->register_new_node_with_optimizer(new IfNode( top_if->in(0), boo, PROB_UNLIKELY_MAG(5), top_if->_fcnt ));
                Node *if_min= gvn->register_new_node_with_optimizer(new IfTrueNode (iff));
                Node *ifF   = gvn->register_new_node_with_optimizer(new IfFalseNode(iff));
                // min test
                cmp         = gvn->register_new_node_with_optimizer(new CmpINode( convf2i, max ));
                boo         = gvn->register_new_node_with_optimizer(new BoolNode( cmp, BoolTest::gt ));
                iff         = (IfNode*)gvn->register_new_node_with_optimizer(new IfNode( ifF, boo, PROB_UNLIKELY_MAG(5), bot_if->_fcnt ));
                Node *if_max= gvn->register_new_node_with_optimizer(new IfTrueNode (iff));
                ifF         = gvn->register_new_node_with_optimizer(new IfFalseNode(iff));
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

  if (can_reshape) {
    modified |= optimize_trichotomy(phase->is_IterGVN());
  }

  return modified ? this : nullptr;
}

//--------------------------remove_unreachable_subgraph----------------------
// This region and therefore all nodes on the input control path(s) are unreachable
// from root. To avoid incomplete removal of unreachable subgraphs, walk up the CFG
// and aggressively replace all nodes by top.
// If a control node "def" with a single control output "use" has its single output
// "use" replaced with top, then "use" removes itself. This has the consequence that
// when we visit "use", it already has all inputs removed. They are lost and we cannot
// traverse them. This is why we fist find all unreachable nodes, and then remove
// them in a second step.
void RegionNode::remove_unreachable_subgraph(PhaseIterGVN* igvn) {
  Node* top = igvn->C->top();
  ResourceMark rm;
  Unique_Node_List unreachable; // visit each only once
  unreachable.push(this);
  // Recursively find all control inputs.
  for (uint i = 0; i < unreachable.size(); i++) {
    Node* n = unreachable.at(i);
    for (uint i = 0; i < n->req(); ++i) {
      Node* m = n->in(i);
      assert(m == nullptr || !m->is_Root(), "Should be unreachable from root");
      if (m != nullptr && m->is_CFG()) {
        unreachable.push(m);
      }
    }
  }
  // Remove all unreachable nodes.
  for (uint i = 0; i < unreachable.size(); i++) {
    Node* n = unreachable.at(i);
    if (n->is_Region()) {
      // Eagerly replace phis with top to avoid regionless phis.
      n->set_req(0, nullptr);
      bool progress = true;
      uint max = n->outcnt();
      DUIterator j;
      while (progress) {
        progress = false;
        for (j = n->outs(); n->has_out(j); j++) {
          Node* u = n->out(j);
          if (u->is_Phi()) {
            igvn->replace_node(u, top);
            if (max != n->outcnt()) {
              progress = true;
              j = n->refresh_out_pos(j);
              max = n->outcnt();
            }
          }
        }
      }
    }
    igvn->replace_node(n, top);
  }
}

//------------------------------optimize_trichotomy--------------------------
// Optimize nested comparisons of the following kind:
//
// int compare(int a, int b) {
//   return (a < b) ? -1 : (a == b) ? 0 : 1;
// }
//
// Shape 1:
// if (compare(a, b) == 1) { ... } -> if (a > b) { ... }
//
// Shape 2:
// if (compare(a, b) == 0) { ... } -> if (a == b) { ... }
//
// Above code leads to the following IR shapes where both Ifs compare the
// same value and two out of three region inputs idx1 and idx2 map to
// the same value and control flow.
//
// (1)   If                 (2)   If
//      /  \                     /  \
//   Proj  Proj               Proj  Proj
//     |      \                |      \
//     |       If              |      If                      If
//     |      /  \             |     /  \                    /  \
//     |   Proj  Proj          |  Proj  Proj      ==>     Proj  Proj
//     |   /      /            \    |    /                  |    /
//    Region     /              \   |   /                   |   /
//         \    /                \  |  /                    |  /
//         Region                Region                    Region
//
// The method returns true if 'this' is modified and false otherwise.
bool RegionNode::optimize_trichotomy(PhaseIterGVN* igvn) {
  int idx1 = 1, idx2 = 2;
  Node* region = nullptr;
  if (req() == 3 && in(1) != nullptr && in(2) != nullptr) {
    // Shape 1: Check if one of the inputs is a region that merges two control
    // inputs and has no other users (especially no Phi users).
    region = in(1)->isa_Region() ? in(1) : in(2)->isa_Region();
    if (region == nullptr || region->outcnt() != 2 || region->req() != 3) {
      return false; // No suitable region input found
    }
  } else if (req() == 4) {
    // Shape 2: Check if two control inputs map to the same value of the unique phi
    // user and treat these as if they would come from another region (shape (1)).
    PhiNode* phi = has_unique_phi();
    if (phi == nullptr) {
      return false; // No unique phi user
    }
    if (phi->in(idx1) != phi->in(idx2)) {
      idx2 = 3;
      if (phi->in(idx1) != phi->in(idx2)) {
        idx1 = 2;
        if (phi->in(idx1) != phi->in(idx2)) {
          return false; // No equal phi inputs found
        }
      }
    }
    assert(phi->in(idx1) == phi->in(idx2), "must be"); // Region is merging same value
    region = this;
  }
  if (region == nullptr || region->in(idx1) == nullptr || region->in(idx2) == nullptr) {
    return false; // Region does not merge two control inputs
  }
  // At this point we know that region->in(idx1) and region->(idx2) map to the same
  // value and control flow. Now search for ifs that feed into these region inputs.
  ProjNode* proj1 = region->in(idx1)->isa_Proj();
  ProjNode* proj2 = region->in(idx2)->isa_Proj();
  if (proj1 == nullptr || proj1->outcnt() != 1 ||
      proj2 == nullptr || proj2->outcnt() != 1) {
    return false; // No projection inputs with region as unique user found
  }
  assert(proj1 != proj2, "should be different projections");
  IfNode* iff1 = proj1->in(0)->isa_If();
  IfNode* iff2 = proj2->in(0)->isa_If();
  if (iff1 == nullptr || iff1->outcnt() != 2 ||
      iff2 == nullptr || iff2->outcnt() != 2) {
    return false; // No ifs found
  }
  if (iff1 == iff2) {
    igvn->add_users_to_worklist(iff1); // Make sure dead if is eliminated
    igvn->replace_input_of(region, idx1, iff1->in(0));
    igvn->replace_input_of(region, idx2, igvn->C->top());
    return (region == this); // Remove useless if (both projections map to the same control/value)
  }
  BoolNode* bol1 = iff1->in(1)->isa_Bool();
  BoolNode* bol2 = iff2->in(1)->isa_Bool();
  if (bol1 == nullptr || bol2 == nullptr) {
    return false; // No bool inputs found
  }
  Node* cmp1 = bol1->in(1);
  Node* cmp2 = bol2->in(1);
  bool commute = false;
  if (!cmp1->is_Cmp() || !cmp2->is_Cmp()) {
    return false; // No comparison
  } else if (cmp1->Opcode() == Op_CmpF || cmp1->Opcode() == Op_CmpD ||
             cmp2->Opcode() == Op_CmpF || cmp2->Opcode() == Op_CmpD ||
             cmp1->Opcode() == Op_CmpP || cmp1->Opcode() == Op_CmpN ||
             cmp2->Opcode() == Op_CmpP || cmp2->Opcode() == Op_CmpN ||
             cmp1->is_SubTypeCheck() || cmp2->is_SubTypeCheck()) {
    // Floats and pointers don't exactly obey trichotomy. To be on the safe side, don't transform their tests.
    // SubTypeCheck is not commutative
    return false;
  } else if (cmp1 != cmp2) {
    if (cmp1->in(1) == cmp2->in(2) &&
        cmp1->in(2) == cmp2->in(1)) {
      commute = true; // Same but swapped inputs, commute the test
    } else {
      return false; // Ifs are not comparing the same values
    }
  }
  proj1 = proj1->other_if_proj();
  proj2 = proj2->other_if_proj();
  if (!((proj1->unique_ctrl_out_or_null() == iff2 &&
         proj2->unique_ctrl_out_or_null() == this) ||
        (proj2->unique_ctrl_out_or_null() == iff1 &&
         proj1->unique_ctrl_out_or_null() == this))) {
    return false; // Ifs are not connected through other projs
  }
  // Found 'iff -> proj -> iff -> proj -> this' shape where all other projs are merged
  // through 'region' and map to the same value. Merge the boolean tests and replace
  // the ifs by a single comparison.
  BoolTest test1 = (proj1->_con == 1) ? bol1->_test : bol1->_test.negate();
  BoolTest test2 = (proj2->_con == 1) ? bol2->_test : bol2->_test.negate();
  test1 = commute ? test1.commute() : test1;
  // After possibly commuting test1, if we can merge test1 & test2, then proj2/iff2/bol2 are the nodes to refine.
  BoolTest::mask res = test1.merge(test2);
  if (res == BoolTest::illegal) {
    return false; // Unable to merge tests
  }
  // Adjust iff1 to always pass (only iff2 will remain)
  igvn->replace_input_of(iff1, 1, igvn->intcon(proj1->_con));
  if (res == BoolTest::never) {
    // Merged test is always false, adjust iff2 to always fail
    igvn->replace_input_of(iff2, 1, igvn->intcon(1 - proj2->_con));
  } else {
    // Replace bool input of iff2 with merged test
    BoolNode* new_bol = new BoolNode(bol2->in(1), res);
    igvn->replace_input_of(iff2, 1, igvn->transform((proj2->_con == 1) ? new_bol : new_bol->negate(igvn)));
    if (new_bol->outcnt() == 0) {
      igvn->remove_dead_node(new_bol);
    }
  }
  return false;
}

const RegMask &RegionNode::out_RegMask() const {
  return RegMask::Empty;
}

#ifndef PRODUCT
void RegionNode::dump_spec(outputStream* st) const {
  Node::dump_spec(st);
  switch (loop_status()) {
  case RegionNode::LoopStatus::MaybeIrreducibleEntry:
    st->print("#irreducible ");
    break;
  case RegionNode::LoopStatus::Reducible:
    st->print("#reducible ");
    break;
  case RegionNode::LoopStatus::NeverIrreducibleEntry:
    break; // nothing
  }
}
#endif

// Find the one non-null required input.  RegionNode only
Node *Node::nonnull_req() const {
  assert( is_Region(), "" );
  for( uint i = 1; i < _cnt; i++ )
    if( in(i) )
      return in(i);
  ShouldNotReachHere();
  return nullptr;
}


//=============================================================================
// note that these functions assume that the _adr_type field is flattened
uint PhiNode::hash() const {
  const Type* at = _adr_type;
  return TypeNode::hash() + (at ? at->hash() : 0);
}
bool PhiNode::cmp( const Node &n ) const {
  return TypeNode::cmp(n) && _adr_type == ((PhiNode&)n)._adr_type;
}
static inline
const TypePtr* flatten_phi_adr_type(const TypePtr* at) {
  if (at == nullptr || at == TypePtr::BOTTOM)  return at;
  return Compile::current()->alias_type(at)->adr_type();
}

//----------------------------make---------------------------------------------
// create a new phi with edges matching r and set (initially) to x
PhiNode* PhiNode::make(Node* r, Node* x, const Type *t, const TypePtr* at) {
  uint preds = r->req();   // Number of predecessor paths
  assert(t != Type::MEMORY || at == flatten_phi_adr_type(at), "flatten at");
  PhiNode* p = new PhiNode(r, t, at);
  for (uint j = 1; j < preds; j++) {
    // Fill in all inputs, except those which the region does not yet have
    if (r->in(j) != nullptr)
      p->init_req(j, x);
  }
  return p;
}
PhiNode* PhiNode::make(Node* r, Node* x) {
  const Type*    t  = x->bottom_type();
  const TypePtr* at = nullptr;
  if (t == Type::MEMORY)  at = flatten_phi_adr_type(x->adr_type());
  return make(r, x, t, at);
}
PhiNode* PhiNode::make_blank(Node* r, Node* x) {
  const Type*    t  = x->bottom_type();
  const TypePtr* at = nullptr;
  if (t == Type::MEMORY)  at = flatten_phi_adr_type(x->adr_type());
  return new PhiNode(r, t, at);
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
  assert(t_oop != nullptr && t_oop->is_known_instance(), "expecting instance oopptr");

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
  Node_Array node_map;
  Node_Stack stack(C->live_nodes() >> 4);
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
      if (in == nullptr || igvn->type(in) == Type::TOP)
        continue;
      Node *opt = MemNode::optimize_simple_memory_chain(in, t_oop, nullptr, igvn);
      PhiNode *optphi = opt->is_Phi() ? opt->as_Phi() : nullptr;
      if (optphi != nullptr && optphi->adr_type() == TypePtr::BOTTOM) {
        opt = node_map[optphi->_idx];
        if (opt == nullptr) {
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
    if (n == nullptr)  continue;
    const Node* np = in(i);
    if (np->is_Phi()) {
      np->as_Phi()->verify_adr_type(visited, at);
    } else if (n->bottom_type() == Type::TOP
               || (n->is_Mem() && n->in(MemNode::Address)->bottom_type() == Type::TOP)) {
      // ignore top inputs
    } else {
      const TypePtr* nat = flatten_phi_adr_type(n->adr_type());
      // recheck phi/non-phi consistency at leaves:
      assert((nat != nullptr) == (at != nullptr), "");
      assert(nat == at || nat == TypePtr::BOTTOM,
             "adr_type must be consistent at leaves of phi nest");
    }
  }
}

// Verify a whole nest of phis rooted at this one.
void PhiNode::verify_adr_type(bool recursive) const {
  if (VMError::is_error_reported())  return;  // muzzle asserts when debugging an error
  if (Node::in_dump())               return;  // muzzle asserts when printing

  assert((_type == Type::MEMORY) == (_adr_type != nullptr), "adr_type for memory phis only");

  if (!VerifyAliases)       return;  // verify thoroughly only if requested

  assert(_adr_type == flatten_phi_adr_type(_adr_type),
         "Phi::adr_type must be pre-normalized");

  if (recursive) {
    VectorSet visited;
    verify_adr_type(visited, _adr_type);
  }
}
#endif


//------------------------------Value------------------------------------------
// Compute the type of the PhiNode
const Type* PhiNode::Value(PhaseGVN* phase) const {
  Node *r = in(0);              // RegionNode
  if( !r )                      // Copy or dead
    return in(1) ? phase->type(in(1)) : Type::TOP;

  // Note: During parsing, phis are often transformed before their regions.
  // This means we have to use type_or_null to defend against untyped regions.
  if( phase->type_or_null(r) == Type::TOP )  // Dead code?
    return Type::TOP;

  // Check for trip-counted loop.  If so, be smarter.
  BaseCountedLoopNode* l = r->is_BaseCountedLoop() ? r->as_BaseCountedLoop() : nullptr;
  if (l && ((const Node*)l->phi() == this)) { // Trip counted loop!
    // protect against init_trip() or limit() returning null
    if (l->can_be_counted_loop(phase)) {
      const Node* init = l->init_trip();
      const Node* limit = l->limit();
      const Node* stride = l->stride();
      if (init != nullptr && limit != nullptr && stride != nullptr) {
        const TypeInteger* lo = phase->type(init)->isa_integer(l->bt());
        const TypeInteger* hi = phase->type(limit)->isa_integer(l->bt());
        const TypeInteger* stride_t = phase->type(stride)->isa_integer(l->bt());
        if (lo != nullptr && hi != nullptr && stride_t != nullptr) { // Dying loops might have TOP here
          assert(stride_t->is_con(), "bad stride type");
          BoolTest::mask bt = l->loopexit()->test_trip();
          // If the loop exit condition is "not equal", the condition
          // would not trigger if init > limit (if stride > 0) or if
          // init < limit if (stride > 0) so we can't deduce bounds
          // for the iv from the exit condition.
          if (bt != BoolTest::ne) {
            jlong stride_con = stride_t->get_con_as_long(l->bt());
            if (stride_con < 0) {          // Down-counter loop
              swap(lo, hi);
              jlong iv_range_lower_limit = lo->lo_as_long();
              // Prevent overflow when adding one below
              if (iv_range_lower_limit < max_signed_integer(l->bt())) {
                // The loop exit condition is: iv + stride > limit (iv is this Phi). So the loop iterates until
                // iv + stride <= limit
                // We know that: limit >= lo->lo_as_long() and stride <= -1
                // So when the loop exits, iv has to be at most lo->lo_as_long() + 1
                iv_range_lower_limit += 1; // lo is after decrement
                // Exact bounds for the phi can be computed when ABS(stride) greater than 1 if bounds are constant.
                if (lo->is_con() && hi->is_con() && hi->lo_as_long() > lo->hi_as_long() && stride_con != -1) {
                  julong uhi = static_cast<julong>(hi->lo_as_long());
                  julong ulo = static_cast<julong>(lo->hi_as_long());
                  julong diff = ((uhi - ulo - 1) / (-stride_con)) * (-stride_con);
                  julong ufirst = hi->lo_as_long() - diff;
                  iv_range_lower_limit = reinterpret_cast<jlong &>(ufirst);
                  assert(iv_range_lower_limit >= lo->lo_as_long() + 1, "should end up with narrower range");
                }
              }
              return TypeInteger::make(MIN2(iv_range_lower_limit, hi->lo_as_long()), hi->hi_as_long(), 3, l->bt())->filter_speculative(_type);
            } else if (stride_con >= 0) {
              jlong iv_range_upper_limit = hi->hi_as_long();
              // Prevent overflow when subtracting one below
              if (iv_range_upper_limit > min_signed_integer(l->bt())) {
                // The loop exit condition is: iv + stride < limit (iv is this Phi). So the loop iterates until
                // iv + stride >= limit
                // We know that: limit <= hi->hi_as_long() and stride >= 1
                // So when the loop exits, iv has to be at most hi->hi_as_long() - 1
                iv_range_upper_limit -= 1;
                // Exact bounds for the phi can be computed when ABS(stride) greater than 1 if bounds are constant.
                if (lo->is_con() && hi->is_con() && hi->lo_as_long() > lo->hi_as_long() && stride_con != 1) {
                  julong uhi = static_cast<julong>(hi->lo_as_long());
                  julong ulo = static_cast<julong>(lo->hi_as_long());
                  julong diff = ((uhi - ulo - 1) / stride_con) * stride_con;
                  julong ulast = lo->hi_as_long() + diff;
                  iv_range_upper_limit = reinterpret_cast<jlong &>(ulast);
                  assert(iv_range_upper_limit <= hi->hi_as_long() - 1, "should end up with narrower range");
                }
              }
              return TypeInteger::make(lo->lo_as_long(), MAX2(lo->hi_as_long(), iv_range_upper_limit), 3, l->bt())->filter_speculative(_type);
            }
          }
        }
      }
    } else if (l->in(LoopNode::LoopBackControl) != nullptr &&
               in(LoopNode::EntryControl) != nullptr &&
               phase->type(l->in(LoopNode::LoopBackControl)) == Type::TOP) {
      // During CCP, if we saturate the type of a counted loop's Phi
      // before the special code for counted loop above has a chance
      // to run (that is as long as the type of the backedge's control
      // is top), we might end up with non monotonic types
      return phase->type(in(LoopNode::EntryControl))->filter_speculative(_type);
    }
  }

  // Default case: merge all inputs
  const Type *t = Type::TOP;        // Merged type starting value
  for (uint i = 1; i < req(); ++i) {// For all paths in
    // Reachable control path?
    if (r->in(i) && phase->type(r->in(i)) == Type::CONTROL) {
      const Type* ti = phase->type(in(i));
      t = t->meet_speculative(ti);
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
  const Type* ft = t->filter_speculative(_type);  // Worst case type

#ifdef ASSERT
  // The following logic has been moved into TypeOopPtr::filter.
  const Type* jt = t->join_speculative(_type);
  if (jt->empty()) {           // Emptied out???
    // Otherwise it's something stupid like non-overlapping int ranges
    // found on dying counted loops.
    assert(ft == Type::TOP, ""); // Canonical empty value
  }

  else {

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
  ft = phase->saturate_and_maybe_push_to_igvn_worklist(this, ft);
  return ft;
}

// Does this Phi represent a simple well-shaped diamond merge?  Return the
// index of the true path or 0 otherwise.
int PhiNode::is_diamond_phi() const {
  Node* region = in(0);
  assert(region != nullptr && region->is_Region(), "phi must have region");
  if (!region->as_Region()->is_diamond()) {
    return 0;
  }

  if (region->in(1)->is_IfTrue()) {
    assert(region->in(2)->is_IfFalse(), "bad If");
    return 1;
  } else {
    // Flipped projections.
    assert(region->in(2)->is_IfTrue(), "bad If");
    return 2;
  }
}

// Do the following transformation if we find the corresponding graph shape, remove the involved memory phi and return
// true. Otherwise, return false if the transformation cannot be applied.
//
//           If                                     If
//          /  \                                   /  \
//    IfFalse  IfTrue  /- Some Node          IfFalse  IfTrue
//          \  /      /    /                       \  /        Some Node
//         Region    / /-MergeMem     ===>        Region          |
//          /   \---Phi                             |          MergeMem
// [other phis]      \                        [other phis]        |
//                   use                                         use
bool PhiNode::try_clean_memory_phi(PhaseIterGVN* igvn) {
  if (_type != Type::MEMORY) {
    return false;
  }
  assert(is_diamond_phi() > 0, "sanity");
  assert(req() == 3, "same as region");
  const Node* region = in(0);
  for (uint i = 1; i < 3; i++) {
    Node* phi_input = in(i);
    if (phi_input != nullptr && phi_input->is_MergeMem() && region->in(i)->outcnt() == 1) {
      // Nothing is control-dependent on path #i except the region itself.
      MergeMemNode* merge_mem = phi_input->as_MergeMem();
      uint j = 3 - i;
      Node* other_phi_input = in(j);
      if (other_phi_input != nullptr && other_phi_input == merge_mem->base_memory()) {
        // merge_mem is a successor memory to other_phi_input, and is not pinned inside the diamond, so push it out.
        // This will allow the diamond to collapse completely if there are no other phis left.
        igvn->replace_node(this, merge_mem);
        return true;
      }
    }
  }
  return false;
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
  if (id == nullptr)
    return nullptr;

  // Either value might be a cast that depends on a branch of 'iff'.
  // Since the 'id' value will float free of the diamond, either
  // decast or return failure.
  Node* ctl = id->in(0);
  if (ctl != nullptr && ctl->in(0) == iff) {
    if (id->is_ConstraintCast()) {
      return id->in(1);
    } else {
      // Don't know how to disentangle this value.
      return nullptr;
    }
  }

  return id;
}

//------------------------------Identity---------------------------------------
// Check for Region being Identity.
Node* PhiNode::Identity(PhaseGVN* phase) {
  if (must_wait_for_region_in_irreducible_loop(phase)) {
    return this;
  }
  // Check for no merging going on
  // (There used to be special-case code here when this->region->is_Loop.
  // It would check for a tributary phi on the backedge that the main phi
  // trivially, perhaps with a single cast.  The unique_input method
  // does all this and more, by reducing such tributaries to 'this'.)
  Node* uin = unique_input(phase, false);
  if (uin != nullptr) {
    return uin;
  }

  int true_path = is_diamond_phi();
  // Delay CMove'ing identity if Ideal has not had the chance to handle unsafe cases, yet.
  if (true_path != 0 && !(phase->is_IterGVN() && wait_for_region_igvn(phase))) {
    Node* id = is_cmove_id(phase, true_path);
    if (id != nullptr) {
      return id;
    }
  }

  // Looking for phis with identical inputs.  If we find one that has
  // type TypePtr::BOTTOM, replace the current phi with the bottom phi.
  if (phase->is_IterGVN() && type() == Type::MEMORY && adr_type() !=
      TypePtr::BOTTOM && !adr_type()->is_known_instance()) {
    uint phi_len = req();
    Node* phi_reg = region();
    for (DUIterator_Fast imax, i = phi_reg->fast_outs(imax); i < imax; i++) {
      Node* u = phi_reg->fast_out(i);
      if (u->is_Phi() && u->as_Phi()->type() == Type::MEMORY &&
          u->adr_type() == TypePtr::BOTTOM && u->in(0) == phi_reg &&
          u->req() == phi_len) {
        for (uint j = 1; j < phi_len; j++) {
          if (in(j) != u->in(j)) {
            u = nullptr;
            break;
          }
        }
        if (u != nullptr) {
          return u;
        }
      }
    }
  }

  return this;                     // No identity
}

//-----------------------------unique_input------------------------------------
// Find the unique value, discounting top, self-loops, and casts.
// Return top if there are no inputs, and self if there are multiple.
Node* PhiNode::unique_input(PhaseValues* phase, bool uncast) {
  //  1) One unique direct input,
  // or if uncast is true:
  //  2) some of the inputs have an intervening ConstraintCast
  //  3) an input is a self loop
  //
  //  1) input   or   2) input     or   3) input __
  //     /   \           /   \               \  /  \
  //     \   /          |    cast             phi  cast
  //      phi            \   /               /  \  /
  //                      phi               /    --

  Node* r = in(0);                      // RegionNode
  Node* input = nullptr; // The unique direct input (maybe uncasted = ConstraintCasts removed)

  for (uint i = 1, cnt = req(); i < cnt; ++i) {
    Node* rc = r->in(i);
    if (rc == nullptr || phase->type(rc) == Type::TOP)
      continue;                 // ignore unreachable control path
    Node* n = in(i);
    if (n == nullptr)
      continue;
    Node* un = n;
    if (uncast) {
#ifdef ASSERT
      Node* m = un->uncast();
#endif
      while (un != nullptr && un->req() == 2 && un->is_ConstraintCast()) {
        Node* next = un->in(1);
        if (phase->type(next)->isa_rawptr() && phase->type(un)->isa_oopptr()) {
          // risk exposing raw ptr at safepoint
          break;
        }
        un = next;
      }
      assert(m == un || un->in(1) == m, "Only expected at CheckCastPP from allocation");
    }
    if (un == nullptr || un == this || phase->type(un) == Type::TOP) {
      continue; // ignore if top, or in(i) and "this" are in a data cycle
    }
    // Check for a unique input (maybe uncasted)
    if (input == nullptr) {
      input = un;
    } else if (input != un) {
      input = NodeSentinel; // no unique input
    }
  }
  if (input == nullptr) {
    return phase->C->top();        // no inputs
  }

  if (input != NodeSentinel) {
    return input;           // one unique direct input
  }

  // Nothing.
  return nullptr;
}

//------------------------------is_x2logic-------------------------------------
// Check for simple convert-to-boolean pattern
// If:(C Bool) Region:(IfF IfT) Phi:(Region 0 1)
// Convert Phi to an ConvIB.
static Node *is_x2logic( PhaseGVN *phase, PhiNode *phi, int true_path ) {
  assert(true_path !=0, "only diamond shape graph expected");

  // If we're late in the optimization process, we may have already expanded Conv2B nodes
  if (phase->C->post_loop_opts_phase() && !Matcher::match_rule_supported(Op_Conv2B)) {
    return nullptr;
  }

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
      return nullptr;
    flipped = 1-flipped;        // Test is vs 1 instead of 0!
  }

  // Check for setting zero/one opposite expected
  if( tzero == TypeInt::ZERO ) {
    if( tone == TypeInt::ONE ) {
    } else return nullptr;
  } else if( tzero == TypeInt::ONE ) {
    if( tone == TypeInt::ZERO ) {
      flipped = 1-flipped;
    } else return nullptr;
  } else return nullptr;

  // Check for boolean test backwards
  if( b->_test._test == BoolTest::ne ) {
  } else if( b->_test._test == BoolTest::eq ) {
    flipped = 1-flipped;
  } else return nullptr;

  // Build int->bool conversion
  Node* n = new Conv2BNode(cmp->in(1));
  if (flipped) {
    n = new XorINode(phase->transform(n), phase->intcon(1));
  }

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
  if (region->has_unique_phi() != phi)  return nullptr;

  // Make sure each arm of the diamond has exactly one output, which we assume
  // is the region.  Otherwise, the control flow won't disappear.
  if (region->in(1)->outcnt() != 1) return nullptr;
  if (region->in(2)->outcnt() != 1) return nullptr;

  // Check for "(P < Q)" of type signed int
  if (b->_test._test != BoolTest::lt)  return nullptr;
  if (cmp->Opcode() != Op_CmpI)        return nullptr;

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
    return nullptr;

  Node *x = n2;
  Node *y = nullptr;
  if( x == n1->in(1) ) {
    y = n1->in(2);
  } else if( x == n1->in(2) ) {
    y = n1->in(1);
  } else return nullptr;

  // Not so profitable if compare and add are constants
  if( q->is_Con() && phase->type(q) != TypeInt::ZERO && y->is_Con() )
    return nullptr;

  Node *cmplt = phase->transform( new CmpLTMaskNode(p,q) );
  Node *j_and   = phase->transform( new AndINode(cmplt,y) );
  return new AddINode(j_and,x);
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
  Node *cmp = bol->in(1);

  // Check bool sense
  if (cmp->Opcode() == Op_CmpF || cmp->Opcode() == Op_CmpD) {
    switch (bol->_test._test) {
    case BoolTest::lt: cmp_zero_idx = 1; phi_x_idx = true_path;  break;
    case BoolTest::le: cmp_zero_idx = 2; phi_x_idx = false_path; break;
    case BoolTest::gt: cmp_zero_idx = 2; phi_x_idx = true_path;  break;
    case BoolTest::ge: cmp_zero_idx = 1; phi_x_idx = false_path; break;
    default:           return nullptr;                           break;
    }
  } else if (cmp->Opcode() == Op_CmpI || cmp->Opcode() == Op_CmpL) {
    switch (bol->_test._test) {
    case BoolTest::lt:
    case BoolTest::le: cmp_zero_idx = 2; phi_x_idx = false_path; break;
    case BoolTest::gt:
    case BoolTest::ge: cmp_zero_idx = 2; phi_x_idx = true_path;  break;
    default:           return nullptr;                           break;
    }
  }

  // Test is next
  const Type *tzero = nullptr;
  switch (cmp->Opcode()) {
  case Op_CmpI:    tzero = TypeInt::ZERO; break;  // Integer ABS
  case Op_CmpL:    tzero = TypeLong::ZERO; break; // Long ABS
  case Op_CmpF:    tzero = TypeF::ZERO; break; // Float ABS
  case Op_CmpD:    tzero = TypeD::ZERO; break; // Double ABS
  default: return nullptr;
  }

  // Find zero input of compare; the other input is being abs'd
  Node *x = nullptr;
  bool flip = false;
  if( phase->type(cmp->in(cmp_zero_idx)) == tzero ) {
    x = cmp->in(3 - cmp_zero_idx);
  } else if( phase->type(cmp->in(3 - cmp_zero_idx)) == tzero ) {
    // The test is inverted, we should invert the result...
    x = cmp->in(cmp_zero_idx);
    flip = true;
  } else {
    return nullptr;
  }

  // Next get the 2 pieces being selected, one is the original value
  // and the other is the negated value.
  if( phi_root->in(phi_x_idx) != x ) return nullptr;

  // Check other phi input for subtract node
  Node *sub = phi_root->in(3 - phi_x_idx);

  bool is_sub = sub->Opcode() == Op_SubF || sub->Opcode() == Op_SubD ||
                sub->Opcode() == Op_SubI || sub->Opcode() == Op_SubL;

  // Allow only Sub(0,X) and fail out for all others; Neg is not OK
  if (!is_sub || phase->type(sub->in(1)) != tzero || sub->in(2) != x) return nullptr;

  if (tzero == TypeF::ZERO) {
    x = new AbsFNode(x);
    if (flip) {
      x = new SubFNode(sub->in(1), phase->transform(x));
    }
  } else if (tzero == TypeD::ZERO) {
    x = new AbsDNode(x);
    if (flip) {
      x = new SubDNode(sub->in(1), phase->transform(x));
    }
  } else if (tzero == TypeInt::ZERO && Matcher::match_rule_supported(Op_AbsI)) {
    x = new AbsINode(x);
    if (flip) {
      x = new SubINode(sub->in(1), phase->transform(x));
    }
  } else if (tzero == TypeLong::ZERO && Matcher::match_rule_supported(Op_AbsL)) {
    x = new AbsLNode(x);
    if (flip) {
      x = new SubLNode(sub->in(1), phase->transform(x));
    }
  } else return nullptr;

  return x;
}

//------------------------------split_once-------------------------------------
// Helper for split_flow_path
static void split_once(PhaseIterGVN *igvn, Node *phi, Node *val, Node *n, Node *newn) {
  igvn->hash_delete(n);         // Remove from hash before hacking edges

  uint j = 1;
  for (uint i = phi->req()-1; i > 0; i--) {
    if (phi->in(i) == val) {   // Found a path with val?
      // Add to NEW Region/Phi, no DU info
      newn->set_req( j++, n->in(i) );
      // Remove from OLD Region/Phi
      n->del_req(i);
    }
  }

  // Register the new node but do not transform it.  Cannot transform until the
  // entire Region/Phi conglomerate has been hacked as a single huge transform.
  igvn->register_new_node_with_optimizer( newn );

  // Now I can point to the new node.
  n->add_req(newn);
  igvn->_worklist.push(n);
}

//------------------------------split_flow_path--------------------------------
// Check for merging identical values and split flow paths
static Node* split_flow_path(PhaseGVN *phase, PhiNode *phi) {
  // This optimization tries to find two or more inputs of phi with the same constant value
  // It then splits them into a separate Phi, and according Region. If this is a loop-entry,
  // and the loop entry has multiple fall-in edges, and some of those fall-in edges have that
  // constant, and others not, we may split the fall-in edges into separate Phi's, and create
  // an irreducible loop. For reducible loops, this never seems to happen, as the multiple
  // fall-in edges are already merged before the loop head during parsing. But with irreducible
  // loops present the order or merging during parsing can sometimes prevent this.
  if (phase->C->has_irreducible_loop()) {
    // Avoid this optimization if any irreducible loops are present. Else we may create
    // an irreducible loop that we do not detect.
    return nullptr;
  }
  BasicType bt = phi->type()->basic_type();
  if( bt == T_ILLEGAL || type2size[bt] <= 0 )
    return nullptr;             // Bail out on funny non-value stuff
  if( phi->req() <= 3 )         // Need at least 2 matched inputs and a
    return nullptr;             // third unequal input to be worth doing

  // Scan for a constant
  uint i;
  for( i = 1; i < phi->req()-1; i++ ) {
    Node *n = phi->in(i);
    if( !n ) return nullptr;
    if( phase->type(n) == Type::TOP ) return nullptr;
    if( n->Opcode() == Op_ConP || n->Opcode() == Op_ConN || n->Opcode() == Op_ConNKlass )
      break;
  }
  if( i >= phi->req() )         // Only split for constants
    return nullptr;

  Node *val = phi->in(i);       // Constant to split for
  uint hit = 0;                 // Number of times it occurs
  Node *r = phi->region();

  for( ; i < phi->req(); i++ ){ // Count occurrences of constant
    Node *n = phi->in(i);
    if( !n ) return nullptr;
    if( phase->type(n) == Type::TOP ) return nullptr;
    if( phi->in(i) == val ) {
      hit++;
      if (Node::may_be_loop_entry(r->in(i))) {
        return nullptr; // don't split loop entry path
      }
    }
  }

  if( hit <= 1 ||               // Make sure we find 2 or more
      hit == phi->req()-1 )     // and not ALL the same value
    return nullptr;

  // Now start splitting out the flow paths that merge the same value.
  // Split first the RegionNode.
  PhaseIterGVN *igvn = phase->is_IterGVN();
  RegionNode *newr = new RegionNode(hit+1);
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

// Returns the BasicType of a given convert node and a type, with special handling to ensure that conversions to
// and from half float will return the SHORT basic type, as that wouldn't be returned typically from TypeInt.
static BasicType get_convert_type(Node* convert, const Type* type) {
  int convert_op = convert->Opcode();
  if (type->isa_int() && (convert_op == Op_ConvHF2F || convert_op == Op_ConvF2HF)) {
    return T_SHORT;
  }

  return type->basic_type();
}

//=============================================================================
//------------------------------simple_data_loop_check-------------------------
//  Try to determining if the phi node in a simple safe/unsafe data loop.
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
//  as dead loop when the phi references itself through an other phi.
PhiNode::LoopSafety PhiNode::simple_data_loop_check(Node *in) const {
  // It is unsafe loop if the phi node references itself directly.
  if (in == (Node*)this)
    return UnsafeLoop; // Unsafe loop
  // Unsafe loop if the phi node references itself through an unsafe data node.
  // Exclude cases with null inputs or data nodes which could reference
  // itself (safe for dead loops).
  if (in != nullptr && !in->is_dead_loop_safe()) {
    // Check inputs of phi's inputs also.
    // It is much less expensive then full graph walk.
    uint cnt = in->req();
    uint i = (in->is_Proj() && !in->is_CFG())  ? 0 : 1;
    for (; i < cnt; ++i) {
      Node* m = in->in(i);
      if (m == (Node*)this)
        return UnsafeLoop; // Unsafe loop
      if (m != nullptr && !m->is_dead_loop_safe()) {
        // Check the most common case (about 30% of all cases):
        // phi->Load/Store->AddP->(ConP ConP Con)/(Parm Parm Con).
        Node *m1 = (m->is_AddP() && m->req() > 3) ? m->in(1) : nullptr;
        if (m1 == (Node*)this)
          return UnsafeLoop; // Unsafe loop
        if (m1 != nullptr && m1 == m->in(2) &&
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

  Node_List nstack;
  VectorSet visited;

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
      if (m != nullptr && !m->is_dead_loop_safe()) { // Only look for unsafe cases.
        if (!visited.test_set(m->_idx))
          nstack.push(m);
      }
    }
  }
  return false; // The phi is not reachable from its inputs
}

// Is this Phi's region or some inputs to the region enqueued for IGVN
// and so could cause the region to be optimized out?
bool PhiNode::wait_for_region_igvn(PhaseGVN* phase) {
  PhaseIterGVN* igvn = phase->is_IterGVN();
  Unique_Node_List& worklist = igvn->_worklist;
  bool delay = false;
  Node* r = in(0);
  for (uint j = 1; j < req(); j++) {
    Node* rc = r->in(j);
    Node* n = in(j);

    if (rc == nullptr || !rc->is_Proj()) { continue; }
    if (worklist.member(rc)) {
      delay = true;
      break;
    }

    if (rc->in(0) == nullptr || !rc->in(0)->is_If()) { continue; }
    if (worklist.member(rc->in(0))) {
      delay = true;
      break;
    }

    if (rc->in(0)->in(1) == nullptr || !rc->in(0)->in(1)->is_Bool()) { continue; }
    if (worklist.member(rc->in(0)->in(1))) {
      delay = true;
      break;
    }

    if (rc->in(0)->in(1)->in(1) == nullptr || !rc->in(0)->in(1)->in(1)->is_Cmp()) { continue; }
    if (worklist.member(rc->in(0)->in(1)->in(1))) {
      delay = true;
      break;
    }
  }

  if (delay) {
    worklist.push(this);
  }
  return delay;
}

// If the Phi's Region is in an irreducible loop, and the Region
// has had an input removed, but not yet transformed, it could be
// that the Region (and this Phi) are not reachable from Root.
// If we allow the Phi to collapse before the Region, this may lead
// to dead-loop data. Wait for the Region to check for reachability,
// and potentially remove the dead code.
bool PhiNode::must_wait_for_region_in_irreducible_loop(PhaseGVN* phase) const {
  RegionNode* region = in(0)->as_Region();
  if (region->loop_status() == RegionNode::LoopStatus::MaybeIrreducibleEntry) {
    Node* top = phase->C->top();
    for (uint j = 1; j < req(); j++) {
      Node* rc = region->in(j); // for each control input
      if (rc == nullptr || phase->type(rc) == Type::TOP) {
        // Region is missing a control input
        Node* n = in(j);
        if (n != nullptr && n != top) {
          // Phi still has its input, so region just lost its input
          return true;
        }
      }
    }
  }
  return false;
}

//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.  Must preserve
// the CFG, but we can still strip out dead paths.
Node *PhiNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  Node *r = in(0);              // RegionNode
  assert(r != nullptr && r->is_Region(), "this phi must have a region");
  assert(r->in(0) == nullptr || !r->in(0)->is_Root(), "not a specially hidden merge");

  // Note: During parsing, phis are often transformed before their regions.
  // This means we have to use type_or_null to defend against untyped regions.
  if( phase->type_or_null(r) == Type::TOP ) // Dead code?
    return nullptr;                // No change

  Node *top = phase->C->top();
  bool new_phi = (outcnt() == 0); // transforming new Phi
  // No change for igvn if new phi is not hooked
  if (new_phi && can_reshape)
    return nullptr;

  if (must_wait_for_region_in_irreducible_loop(phase)) {
    return nullptr;
  }

  // The are 2 situations when only one valid phi's input is left
  // (in addition to Region input).
  // One: region is not loop - replace phi with this input.
  // Two: region is loop - replace phi with top since this data path is dead
  //                       and we need to break the dead data loop.
  Node* progress = nullptr;        // Record if any progress made
  for( uint j = 1; j < req(); ++j ){ // For all paths in
    // Check unreachable control paths
    Node* rc = r->in(j);
    Node* n = in(j);            // Get the input
    if (rc == nullptr || phase->type(rc) == Type::TOP) {
      if (n != top) {           // Not already top?
        PhaseIterGVN *igvn = phase->is_IterGVN();
        if (can_reshape && igvn != nullptr) {
          igvn->_worklist.push(r);
        }
        // Nuke it down
        set_req_X(j, top, phase);
        progress = this;        // Record progress
      }
    }
  }

  if (can_reshape && outcnt() == 0) {
    // set_req() above may kill outputs if Phi is referenced
    // only by itself on the dead (top) control path.
    return top;
  }

  bool uncasted = false;
  Node* uin = unique_input(phase, false);
  if (uin == nullptr && can_reshape &&
      // If there is a chance that the region can be optimized out do
      // not add a cast node that we can't remove yet.
      !wait_for_region_igvn(phase)) {
    uncasted = true;
    uin = unique_input(phase, true);
  }
  if (uin == top) {             // Simplest case: no alive inputs.
    if (can_reshape)            // IGVN transformation
      return top;
    else
      return nullptr;              // Identity will return TOP
  } else if (uin != nullptr) {
    // Only one not-null unique input path is left.
    // Determine if this input is backedge of a loop.
    // (Skip new phis which have no uses and dead regions).
    if (outcnt() > 0 && r->in(0) != nullptr) {
      if (is_data_loop(r->as_Region(), uin, phase)) {
        // Break this data loop to avoid creation of a dead loop.
        if (can_reshape) {
          return top;
        } else {
          // We can't return top if we are in Parse phase - cut inputs only
          // let Identity to handle the case.
          replace_edge(uin, top, phase);
          return nullptr;
        }
      }
    }

    if (uncasted) {
      // Add cast nodes between the phi to be removed and its unique input.
      // Wait until after parsing for the type information to propagate from the casts.
      assert(can_reshape, "Invalid during parsing");
      const Type* phi_type = bottom_type();
      // Add casts to carry the control dependency of the Phi that is
      // going away
      Node* cast = nullptr;
      const TypeTuple* extra_types = collect_types(phase);
      if (phi_type->isa_ptr()) {
        const Type* uin_type = phase->type(uin);
        if (!phi_type->isa_oopptr() && !uin_type->isa_oopptr()) {
          cast = new CastPPNode(r, uin, phi_type, ConstraintCastNode::StrongDependency, extra_types);
        } else {
          // Use a CastPP for a cast to not null and a CheckCastPP for
          // a cast to a new klass (and both if both null-ness and
          // klass change).

          // If the type of phi is not null but the type of uin may be
          // null, uin's type must be casted to not null
          if (phi_type->join(TypePtr::NOTNULL) == phi_type->remove_speculative() &&
              uin_type->join(TypePtr::NOTNULL) != uin_type->remove_speculative()) {
            cast = new CastPPNode(r, uin, TypePtr::NOTNULL, ConstraintCastNode::StrongDependency, extra_types);
          }

          // If the type of phi and uin, both casted to not null,
          // differ the klass of uin must be (check)cast'ed to match
          // that of phi
          if (phi_type->join_speculative(TypePtr::NOTNULL) != uin_type->join_speculative(TypePtr::NOTNULL)) {
            Node* n = uin;
            if (cast != nullptr) {
              cast = phase->transform(cast);
              n = cast;
            }
            cast = new CheckCastPPNode(r, uin, phi_type, ConstraintCastNode::StrongDependency, extra_types);
          }
          if (cast == nullptr) {
            cast = new CastPPNode(r, uin, phi_type, ConstraintCastNode::StrongDependency, extra_types);
          }
        }
      } else {
        cast = ConstraintCastNode::make_cast_for_type(r, uin, phi_type, ConstraintCastNode::StrongDependency, extra_types);
      }
      assert(cast != nullptr, "cast should be set");
      cast = phase->transform(cast);
      // set all inputs to the new cast(s) so the Phi is removed by Identity
      PhaseIterGVN* igvn = phase->is_IterGVN();
      for (uint i = 1; i < req(); i++) {
        set_req_X(i, cast, igvn);
      }
      uin = cast;
    }

    // One unique input.
    debug_only(Node* ident = Identity(phase));
    // The unique input must eventually be detected by the Identity call.
#ifdef ASSERT
    if (ident != uin && !ident->is_top() && !must_wait_for_region_in_irreducible_loop(phase)) {
      // print this output before failing assert
      r->dump(3);
      this->dump(3);
      ident->dump();
      uin->dump();
    }
#endif
    // Identity may not return the expected uin, if it has to wait for the region, in irreducible case
    assert(ident == uin || ident->is_top() || must_wait_for_region_in_irreducible_loop(phase), "Identity must clean this up");
    return nullptr;
  }

  Node* opt = nullptr;
  int true_path = is_diamond_phi();
  if (true_path != 0 &&
      // If one of the diamond's branch is in the process of dying then, the Phi's input for that branch might transform
      // to top. If that happens replacing the Phi with an operation that consumes the Phi's inputs will cause the Phi
      // to be replaced by top. To prevent that, delay the transformation until the branch has a chance to be removed.
      !(can_reshape && wait_for_region_igvn(phase))) {
    // Check for CMove'ing identity. If it would be unsafe,
    // handle it here. In the safe case, let Identity handle it.
    Node* unsafe_id = is_cmove_id(phase, true_path);
    if( unsafe_id != nullptr && is_unsafe_data_reference(unsafe_id) )
      opt = unsafe_id;

    // Check for simple convert-to-boolean pattern
    if( opt == nullptr )
      opt = is_x2logic(phase, this, true_path);

    // Check for absolute value
    if( opt == nullptr )
      opt = is_absolute(phase, this, true_path);

    // Check for conditional add
    if( opt == nullptr && can_reshape )
      opt = is_cond_add(phase, this, true_path);

    // These 4 optimizations could subsume the phi:
    // have to check for a dead data loop creation.
    if( opt != nullptr ) {
      if( opt == unsafe_id || is_unsafe_data_reference(opt) ) {
        // Found dead loop.
        if( can_reshape )
          return top;
        // We can't return top if we are in Parse phase - cut inputs only
        // to stop further optimizations for this phi. Identity will return TOP.
        assert(req() == 3, "only diamond merge phi here");
        set_req(1, top);
        set_req(2, top);
        return nullptr;
      } else {
        return opt;
      }
    }
  }

  // Check for merging identical values and split flow paths
  if (can_reshape) {
    opt = split_flow_path(phase, this);
    // This optimization only modifies phi - don't need to check for dead loop.
    assert(opt == nullptr || opt == this, "do not elide phi");
    if (opt != nullptr)  return opt;
  }

  if (in(1) != nullptr && in(1)->Opcode() == Op_AddP && can_reshape) {
    // Try to undo Phi of AddP:
    // (Phi (AddP base address offset) (AddP base2 address2 offset2))
    // becomes:
    // newbase := (Phi base base2)
    // newaddress := (Phi address address2)
    // newoffset := (Phi offset offset2)
    // (AddP newbase newaddress newoffset)
    //
    // This occurs as a result of unsuccessful split_thru_phi and
    // interferes with taking advantage of addressing modes. See the
    // clone_shift_expressions code in matcher.cpp
    Node* addp = in(1);
    Node* base = addp->in(AddPNode::Base);
    Node* address = addp->in(AddPNode::Address);
    Node* offset = addp->in(AddPNode::Offset);
    if (base != nullptr && address != nullptr && offset != nullptr &&
        !base->is_top() && !address->is_top() && !offset->is_top()) {
      const Type* base_type = base->bottom_type();
      const Type* address_type = address->bottom_type();
      // make sure that all the inputs are similar to the first one,
      // i.e. AddP with base == address and same offset as first AddP
      bool doit = true;
      for (uint i = 2; i < req(); i++) {
        if (in(i) == nullptr ||
            in(i)->Opcode() != Op_AddP ||
            in(i)->in(AddPNode::Base) == nullptr ||
            in(i)->in(AddPNode::Address) == nullptr ||
            in(i)->in(AddPNode::Offset) == nullptr ||
            in(i)->in(AddPNode::Base)->is_top() ||
            in(i)->in(AddPNode::Address)->is_top() ||
            in(i)->in(AddPNode::Offset)->is_top()) {
          doit = false;
          break;
        }
        if (in(i)->in(AddPNode::Base) != base) {
          base = nullptr;
        }
        if (in(i)->in(AddPNode::Offset) != offset) {
          offset = nullptr;
        }
        if (in(i)->in(AddPNode::Address) != address) {
          address = nullptr;
        }
        // Accumulate type for resulting Phi
        base_type = base_type->meet_speculative(in(i)->in(AddPNode::Base)->bottom_type());
        address_type = address_type->meet_speculative(in(i)->in(AddPNode::Address)->bottom_type());
      }
      if (doit && base == nullptr) {
        // Check for neighboring AddP nodes in a tree.
        // If they have a base, use that it.
        for (DUIterator_Fast kmax, k = this->fast_outs(kmax); k < kmax; k++) {
          Node* u = this->fast_out(k);
          if (u->is_AddP()) {
            Node* base2 = u->in(AddPNode::Base);
            if (base2 != nullptr && !base2->is_top()) {
              if (base == nullptr)
                base = base2;
              else if (base != base2)
                { doit = false; break; }
            }
          }
        }
      }
      if (doit) {
        if (base == nullptr) {
          base = new PhiNode(in(0), base_type, nullptr);
          for (uint i = 1; i < req(); i++) {
            base->init_req(i, in(i)->in(AddPNode::Base));
          }
          phase->is_IterGVN()->register_new_node_with_optimizer(base);
        }
        if (address == nullptr) {
          address = new PhiNode(in(0), address_type, nullptr);
          for (uint i = 1; i < req(); i++) {
            address->init_req(i, in(i)->in(AddPNode::Address));
          }
          phase->is_IterGVN()->register_new_node_with_optimizer(address);
        }
        if (offset == nullptr) {
          offset = new PhiNode(in(0), TypeX_X, nullptr);
          for (uint i = 1; i < req(); i++) {
            offset->init_req(i, in(i)->in(AddPNode::Offset));
          }
          phase->is_IterGVN()->register_new_node_with_optimizer(offset);
        }
        return new AddPNode(base, address, offset);
      }
    }
  }

  // Split phis through memory merges, so that the memory merges will go away.
  // Piggy-back this transformation on the search for a unique input....
  // It will be as if the merged memory is the unique value of the phi.
  // (Do not attempt this optimization unless parsing is complete.
  // It would make the parser's memory-merge logic sick.)
  // (MergeMemNode is not dead_loop_safe - need to check for dead loop.)
  if (progress == nullptr && can_reshape && type() == Type::MEMORY) {
    // see if this phi should be sliced
    uint merge_width = 0;
    bool saw_self = false;
    for( uint i=1; i<req(); ++i ) {// For all paths in
      Node *ii = in(i);
      // TOP inputs should not be counted as safe inputs because if the
      // Phi references itself through all other inputs then splitting the
      // Phi through memory merges would create dead loop at later stage.
      if (ii == top) {
        return nullptr; // Delay optimization until graph is cleaned.
      }
      if (ii->is_MergeMem()) {
        MergeMemNode* n = ii->as_MergeMem();
        merge_width = MAX2(merge_width, n->req());
        saw_self = saw_self || (n->base_memory() == this);
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
              return top;
            }
            // If transformed to a MergeMem, get the desired slice
            // Otherwise the returned node represents memory for every slice
            Node *new_mem = (m->is_MergeMem()) ?
                             m->as_MergeMem()->memory_at(alias_idx) : m;
            // Update input if it is progress over what we have now
            if (new_mem != ii) {
              set_req_X(i, new_mem, phase->is_IterGVN());
              progress = this;
            }
          }
        }
      } else {
        // We know that at least one MergeMem->base_memory() == this
        // (saw_self == true). If all other inputs also references this phi
        // (directly or through data nodes) - it is a dead loop.
        bool saw_safe_input = false;
        for (uint j = 1; j < req(); ++j) {
          Node* n = in(j);
          if (n->is_MergeMem()) {
            MergeMemNode* mm = n->as_MergeMem();
            if (mm->base_memory() == this || mm->base_memory() == mm->empty_memory()) {
              // Skip this input if it references back to this phi or if the memory path is dead
              continue;
            }
          }
          if (!is_unsafe_data_reference(n)) {
            saw_safe_input = true; // found safe input
            break;
          }
        }
        if (!saw_safe_input) {
          // There is a dead loop: All inputs are either dead or reference back to this phi
          return top;
        }

        // Phi(...MergeMem(m0, m1:AT1, m2:AT2)...) into
        //     MergeMem(Phi(...m0...), Phi:AT1(...m1...), Phi:AT2(...m2...))
        PhaseIterGVN* igvn = phase->is_IterGVN();
        assert(igvn != nullptr, "sanity check");
        Node* hook = new Node(1);
        PhiNode* new_base = (PhiNode*) clone();
        // Must eagerly register phis, since they participate in loops.
        igvn->register_new_node_with_optimizer(new_base);
        hook->add_req(new_base);

        MergeMemNode* result = MergeMemNode::make(new_base);
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
                igvn->register_new_node_with_optimizer(new_phi);
                hook->add_req(new_phi);
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
        // Already replace this phi node to cut it off from the graph to not interfere in dead loop checks during the
        // transformations of the new phi nodes below. Otherwise, we could wrongly conclude that there is no dead loop
        // because we are finding this phi node again. Also set the type of the new MergeMem node in case we are also
        // visiting it in the transformations below.
        igvn->replace_node(this, result);
        igvn->set_type(result, result->bottom_type());

        // now transform the new nodes, and return the mergemem
        for (MergeMemStream mms(result); mms.next_non_empty(); ) {
          Node* phi = mms.memory();
          mms.set_memory(phase->transform(phi));
        }
        hook->destruct(igvn);
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
      Node *new_in = MemNode::optimize_memory_chain(ii, at, nullptr, phase);
      if (ii != new_in ) {
        set_req(i, new_in);
        progress = this;
      }
    }
  }

#ifdef _LP64
  // Push DecodeN/DecodeNKlass down through phi.
  // The rest of phi graph will transform by split EncodeP node though phis up.
  if ((UseCompressedOops || UseCompressedClassPointers) && can_reshape && progress == nullptr) {
    bool may_push = true;
    bool has_decodeN = false;
    bool is_decodeN = false;
    for (uint i=1; i<req(); ++i) {// For all paths in
      Node *ii = in(i);
      if (ii->is_DecodeNarrowPtr() && ii->bottom_type() == bottom_type()) {
        // Do optimization if a non dead path exist.
        if (ii->in(1)->bottom_type() != Type::TOP) {
          has_decodeN = true;
          is_decodeN = ii->is_DecodeN();
        }
      } else if (!ii->is_Phi()) {
        may_push = false;
      }
    }

    if (has_decodeN && may_push) {
      PhaseIterGVN *igvn = phase->is_IterGVN();
      // Make narrow type for new phi.
      const Type* narrow_t;
      if (is_decodeN) {
        narrow_t = TypeNarrowOop::make(this->bottom_type()->is_ptr());
      } else {
        narrow_t = TypeNarrowKlass::make(this->bottom_type()->is_ptr());
      }
      PhiNode* new_phi = new PhiNode(r, narrow_t);
      uint orig_cnt = req();
      for (uint i=1; i<req(); ++i) {// For all paths in
        Node *ii = in(i);
        Node* new_ii = nullptr;
        if (ii->is_DecodeNarrowPtr()) {
          assert(ii->bottom_type() == bottom_type(), "sanity");
          new_ii = ii->in(1);
        } else {
          assert(ii->is_Phi(), "sanity");
          if (ii->as_Phi() == this) {
            new_ii = new_phi;
          } else {
            if (is_decodeN) {
              new_ii = new EncodePNode(ii, narrow_t);
            } else {
              new_ii = new EncodePKlassNode(ii, narrow_t);
            }
            igvn->register_new_node_with_optimizer(new_ii);
          }
        }
        new_phi->set_req(i, new_ii);
      }
      igvn->register_new_node_with_optimizer(new_phi, this);
      if (is_decodeN) {
        progress = new DecodeNNode(new_phi, bottom_type());
      } else {
        progress = new DecodeNKlassNode(new_phi, bottom_type());
      }
    }
  }
#endif

  // Try to convert a Phi with two duplicated convert nodes into a phi of the pre-conversion type and the convert node
  // proceeding the phi, to de-duplicate the convert node and compact the IR.
  if (can_reshape && progress == nullptr) {
    ConvertNode* convert = in(1)->isa_Convert();
    if (convert != nullptr) {
      int conv_op = convert->Opcode();
      bool ok = true;

      // Check the rest of the inputs
      for (uint i = 2; i < req(); i++) {
        // Make sure that all inputs are of the same type of convert node
        if (in(i)->Opcode() != conv_op) {
          ok = false;
          break;
        }
      }

      if (ok) {
        // Find the local bottom type to set as the type of the phi
        const Type* source_type = Type::get_const_basic_type(convert->in_type()->basic_type());
        const Type* dest_type = convert->bottom_type();

        PhiNode* newphi = new PhiNode(in(0), source_type, nullptr);
        // Set inputs to the new phi be the inputs of the convert
        for (uint i = 1; i < req(); i++) {
          newphi->init_req(i, in(i)->in(1));
        }

        phase->is_IterGVN()->register_new_node_with_optimizer(newphi, this);

        return ConvertNode::create_convert(get_convert_type(convert, source_type), get_convert_type(convert, dest_type), newphi);
      }
    }
  }

  // Phi (VB ... VB) => VB (Phi ...) (Phi ...)
  if (EnableVectorReboxing && can_reshape && progress == nullptr && type()->isa_oopptr()) {
    progress = merge_through_phi(this, phase->is_IterGVN());
  }

  return progress;              // Return any progress
}

static int compare_types(const Type* const& e1, const Type* const& e2) {
  return (intptr_t)e1 - (intptr_t)e2;
}

// Collect types at casts that are going to be eliminated at that Phi and store them in a TypeTuple.
// Sort the types using an arbitrary order so a list of some types always hashes to the same TypeTuple (and TypeTuple
// pointer comparison is enough to tell if 2 list of types are the same or not)
const TypeTuple* PhiNode::collect_types(PhaseGVN* phase) const {
  const Node* region = in(0);
  const Type* phi_type = bottom_type();
  ResourceMark rm;
  GrowableArray<const Type*> types;
  for (uint i = 1; i < req(); i++) {
    if (region->in(i) == nullptr || phase->type(region->in(i)) == Type::TOP) {
      continue;
    }
    Node* in = Node::in(i);
    const Type* t = phase->type(in);
    if (in == nullptr || in == this || t == Type::TOP) {
      continue;
    }
    if (t != phi_type && t->higher_equal_speculative(phi_type)) {
      types.insert_sorted<compare_types>(t);
    }
    while (in != nullptr && in->is_ConstraintCast()) {
      Node* next = in->in(1);
      if (phase->type(next)->isa_rawptr() && phase->type(in)->isa_oopptr()) {
        break;
      }
      ConstraintCastNode* cast = in->as_ConstraintCast();
      for (int j = 0; j < cast->extra_types_count(); ++j) {
        const Type* extra_t = cast->extra_type_at(j);
        if (extra_t != phi_type && extra_t->higher_equal_speculative(phi_type)) {
          types.insert_sorted<compare_types>(extra_t);
        }
      }
      in = next;
    }
  }
  const Type **flds = (const Type **)(phase->C->type_arena()->AmallocWords(types.length()*sizeof(Type*)));
  for (int i = 0; i < types.length(); ++i) {
    flds[i] = types.at(i);
  }
  return TypeTuple::make(types.length(), flds);
}

Node* PhiNode::clone_through_phi(Node* root_phi, const Type* t, uint c, PhaseIterGVN* igvn) {
  Node_Stack stack(1);
  VectorSet  visited;
  Node_List  node_map;

  stack.push(root_phi, 1); // ignore control
  visited.set(root_phi->_idx);

  Node* new_phi = new PhiNode(root_phi->in(0), t);
  node_map.map(root_phi->_idx, new_phi);

  while (stack.is_nonempty()) {
    Node* n   = stack.node();
    uint  idx = stack.index();
    assert(n->is_Phi(), "not a phi");
    if (idx < n->req()) {
      stack.set_index(idx + 1);
      Node* def = n->in(idx);
      if (def == nullptr) {
        continue; // ignore dead path
      } else if (def->is_Phi()) { // inner node
        Node* new_phi = node_map[n->_idx];
        if (!visited.test_set(def->_idx)) { // not visited yet
          node_map.map(def->_idx, new PhiNode(def->in(0), t));
          stack.push(def, 1); // ignore control
        }
        Node* new_in = node_map[def->_idx];
        new_phi->set_req(idx, new_in);
      } else if (def->Opcode() == Op_VectorBox) { // leaf
        assert(n->is_Phi(), "not a phi");
        Node* new_phi = node_map[n->_idx];
        new_phi->set_req(idx, def->in(c));
      } else {
        assert(false, "not optimizeable");
        return nullptr;
      }
    } else {
      Node* new_phi = node_map[n->_idx];
      igvn->register_new_node_with_optimizer(new_phi, n);
      stack.pop();
    }
  }
  return new_phi;
}

Node* PhiNode::merge_through_phi(Node* root_phi, PhaseIterGVN* igvn) {
  Node_Stack stack(1);
  VectorSet  visited;

  stack.push(root_phi, 1); // ignore control
  visited.set(root_phi->_idx);

  VectorBoxNode* cached_vbox = nullptr;
  while (stack.is_nonempty()) {
    Node* n   = stack.node();
    uint  idx = stack.index();
    if (idx < n->req()) {
      stack.set_index(idx + 1);
      Node* in = n->in(idx);
      if (in == nullptr) {
        continue; // ignore dead path
      } else if (in->isa_Phi()) {
        if (!visited.test_set(in->_idx)) {
          stack.push(in, 1); // ignore control
        }
      } else if (in->Opcode() == Op_VectorBox) {
        VectorBoxNode* vbox = static_cast<VectorBoxNode*>(in);
        if (cached_vbox == nullptr) {
          cached_vbox = vbox;
        } else if (vbox->vec_type() != cached_vbox->vec_type()) {
          // TODO: vector type mismatch can be handled with additional reinterpret casts
          assert(Type::cmp(vbox->vec_type(), cached_vbox->vec_type()) != 0, "inconsistent");
          return nullptr; // not optimizable: vector type mismatch
        } else if (vbox->box_type() != cached_vbox->box_type()) {
          assert(Type::cmp(vbox->box_type(), cached_vbox->box_type()) != 0, "inconsistent");
          return nullptr; // not optimizable: box type mismatch
        }
      } else {
        return nullptr; // not optimizable: neither Phi nor VectorBox
      }
    } else {
      stack.pop();
    }
  }
  if (cached_vbox == nullptr) {
    // We have a Phi dead-loop (no data-input). Phi nodes are considered safe,
    // so just avoid this optimization.
    return nullptr;
  }
  const TypeInstPtr* btype = cached_vbox->box_type();
  const TypeVect*    vtype = cached_vbox->vec_type();
  Node* new_vbox_phi = clone_through_phi(root_phi, btype, VectorBoxNode::Box,   igvn);
  Node* new_vect_phi = clone_through_phi(root_phi, vtype, VectorBoxNode::Value, igvn);
  return new VectorBoxNode(igvn->C, new_vbox_phi, new_vect_phi, btype, vtype);
}

bool PhiNode::is_data_loop(RegionNode* r, Node* uin, const PhaseGVN* phase) {
  // First, take the short cut when we know it is a loop and the EntryControl data path is dead.
  // The loop node may only have one input because the entry path was removed in PhaseIdealLoop::Dominators().
  // Then, check if there is a data loop when the phi references itself directly or through other data nodes.
  assert(!r->is_Loop() || r->req() <= 3, "Loop node should have 3 or less inputs");
  const bool is_loop = (r->is_Loop() && r->req() == 3);
  const Node* top = phase->C->top();
  if (is_loop) {
    return !uin->eqv_uncast(in(LoopNode::EntryControl));
  } else {
    // We have a data loop either with an unsafe data reference or if a region is unreachable.
    return is_unsafe_data_reference(uin)
           || (r->req() == 3 && (r->in(1) != top && r->in(2) == top && r->is_unreachable_region(phase)));
  }
}

//------------------------------is_tripcount-----------------------------------
bool PhiNode::is_tripcount(BasicType bt) const {
  return (in(0) != nullptr && in(0)->is_BaseCountedLoop() &&
          in(0)->as_BaseCountedLoop()->bt() == bt &&
          in(0)->as_BaseCountedLoop()->phi() == this);
}

//------------------------------out_RegMask------------------------------------
const RegMask &PhiNode::in_RegMask(uint i) const {
  return i ? out_RegMask() : RegMask::Empty;
}

const RegMask &PhiNode::out_RegMask() const {
  uint ideal_reg = _type->ideal_reg();
  assert( ideal_reg != Node::NotAMachineReg, "invalid type at Phi" );
  if( ideal_reg == 0 ) return RegMask::Empty;
  assert(ideal_reg != Op_RegFlags, "flags register is not spillable");
  return *(Compile::current()->matcher()->idealreg2spillmask[ideal_reg]);
}

#ifndef PRODUCT
void PhiNode::dump_spec(outputStream *st) const {
  TypeNode::dump_spec(st);
  if (is_tripcount(T_INT) || is_tripcount(T_LONG)) {
    st->print(" #tripcount");
  }
}
#endif


//=============================================================================
const Type* GotoNode::Value(PhaseGVN* phase) const {
  // If the input is reachable, then we are executed.
  // If the input is not reachable, then we are not executed.
  return phase->type(in(0));
}

Node* GotoNode::Identity(PhaseGVN* phase) {
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
bool PCTableNode::cmp( const Node &n ) const
{ return _size == ((PCTableNode&)n)._size; }

const Type *PCTableNode::bottom_type() const {
  const Type** f = TypeTuple::fields(_size);
  for( uint i = 0; i < _size; i++ ) f[i] = Type::CONTROL;
  return TypeTuple::make(_size, f);
}

//------------------------------Value------------------------------------------
// Compute the type of the PCTableNode.  If reachable it is a tuple of
// Control, otherwise the table targets are not reachable
const Type* PCTableNode::Value(PhaseGVN* phase) const {
  if( phase->type(in(0)) == Type::CONTROL )
    return bottom_type();
  return Type::TOP;             // All paths dead?  Then so are we
}

//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.  Strip out
// control copies
Node *PCTableNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  return remove_dead_region(phase, can_reshape) ? this : nullptr;
}

//=============================================================================
uint JumpProjNode::hash() const {
  return Node::hash() + _dest_bci;
}

bool JumpProjNode::cmp( const Node &n ) const {
  return ProjNode::cmp(n) &&
    _dest_bci == ((JumpProjNode&)n)._dest_bci;
}

#ifndef PRODUCT
void JumpProjNode::dump_spec(outputStream *st) const {
  ProjNode::dump_spec(st);
  st->print("@bci %d ",_dest_bci);
}

void JumpProjNode::dump_compact_spec(outputStream *st) const {
  ProjNode::dump_compact_spec(st);
  st->print("(%d)%d@%d", _switch_val, _proj_no, _dest_bci);
}
#endif

//=============================================================================
//------------------------------Value------------------------------------------
// Check for being unreachable, or for coming from a Rethrow.  Rethrow's cannot
// have the default "fall_through_index" path.
const Type* CatchNode::Value(PhaseGVN* phase) const {
  // Unreachable?  Then so are all paths from here.
  if( phase->type(in(0)) == Type::TOP ) return Type::TOP;
  // First assume all paths are reachable
  const Type** f = TypeTuple::fields(_size);
  for( uint i = 0; i < _size; i++ ) f[i] = Type::CONTROL;
  // Identify cases that will always throw an exception
  // () rethrow call
  // () virtual or interface call with null receiver
  // () call is a check cast with incompatible arguments
  if( in(1)->is_Proj() ) {
    Node *i10 = in(1)->in(0);
    if( i10->is_Call() ) {
      CallNode *call = i10->as_Call();
      // Rethrows always throw exceptions, never return
      if (call->entry_point() == OptoRuntime::rethrow_stub()) {
        f[CatchProjNode::fall_through_index] = Type::TOP;
      } else if (call->is_AllocateArray()) {
        Node* klass_node = call->in(AllocateNode::KlassNode);
        Node* length = call->in(AllocateNode::ALength);
        const Type* length_type = phase->type(length);
        const Type* klass_type = phase->type(klass_node);
        Node* valid_length_test = call->in(AllocateNode::ValidLengthTest);
        const Type* valid_length_test_t = phase->type(valid_length_test);
        if (length_type == Type::TOP || klass_type == Type::TOP || valid_length_test_t == Type::TOP ||
            valid_length_test_t->is_int()->is_con(0)) {
          f[CatchProjNode::fall_through_index] = Type::TOP;
        }
      } else if( call->req() > TypeFunc::Parms ) {
        const Type *arg0 = phase->type( call->in(TypeFunc::Parms) );
        // Check for null receiver to virtual or interface calls
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


bool CatchProjNode::cmp( const Node &n ) const {
  return ProjNode::cmp(n) &&
    _handler_bci == ((CatchProjNode&)n)._handler_bci;
}


//------------------------------Identity---------------------------------------
// If only 1 target is possible, choose it if it is the main control
Node* CatchProjNode::Identity(PhaseGVN* phase) {
  // If my value is control and no other value is, then treat as ID
  const TypeTuple *t = phase->type(in(0))->is_tuple();
  if (t->field_at(_con) != Type::CONTROL)  return this;
  // If we remove the last CatchProj and elide the Catch/CatchProj, then we
  // also remove any exception table entry.  Thus we must know the call
  // feeding the Catch will not really throw an exception.  This is ok for
  // the main fall-thru control (happens when we know a call can never throw
  // an exception) or for "rethrow", because a further optimization will
  // yank the rethrow (happens when we inline a function that can throw an
  // exception and the caller has no handler).  Not legal, e.g., for passing
  // a null receiver to a v-call, or passing bad types to a slow-check-cast.
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
Node* CreateExNode::Identity(PhaseGVN* phase) {
  if( phase->type(in(1)) == Type::TOP ) return in(1);
  if( phase->type(in(0)) == Type::TOP ) return in(0);
  if (phase->type(in(0)->in(0)) == Type::TOP) {
    assert(in(0)->is_CatchProj(), "control is CatchProj");
    return phase->C->top(); // dead code
  }
  // We only come from CatchProj, unless the CatchProj goes away.
  // If the CatchProj is optimized away, then we just carry the
  // exception oop through.
  CallNode *call = in(1)->in(0)->as_Call();

  return (in(0)->is_CatchProj() && in(0)->in(0)->is_Catch() &&
          in(0)->in(0)->in(1) == in(1)) ? this : call->in(TypeFunc::Parms);
}

//=============================================================================
//------------------------------Value------------------------------------------
// Check for being unreachable.
const Type* NeverBranchNode::Value(PhaseGVN* phase) const {
  if (!in(0) || in(0)->is_top()) return Type::TOP;
  return bottom_type();
}

//------------------------------Ideal------------------------------------------
// Check for no longer being part of a loop
Node *NeverBranchNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if (can_reshape && !in(0)->is_Region()) {
    // Dead code elimination can sometimes delete this projection so
    // if it's not there, there's nothing to do.
    Node* fallthru = proj_out_or_null(0);
    if (fallthru != nullptr) {
      phase->is_IterGVN()->replace_node(fallthru, in(0));
    }
    return phase->C->top();
  }
  return nullptr;
}

#ifndef PRODUCT
void NeverBranchNode::format( PhaseRegAlloc *ra_, outputStream *st) const {
  st->print("%s", Name());
}
#endif

#ifndef PRODUCT
void BlackholeNode::format(PhaseRegAlloc* ra, outputStream* st) const {
  st->print("blackhole ");
  bool first = true;
  for (uint i = 0; i < req(); i++) {
    Node* n = in(i);
    if (n != nullptr && OptoReg::is_valid(ra->get_reg_first(n))) {
      if (first) {
        first = false;
      } else {
        st->print(", ");
      }
      char buf[128];
      ra->dump_register(n, buf, sizeof(buf));
      st->print("%s", buf);
    }
  }
  st->cr();
}
#endif

