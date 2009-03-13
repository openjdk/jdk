/*
 * Copyright 1998-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_postaloc.cpp.incl"

// see if this register kind does not requires two registers
static bool is_single_register(uint x) {
#ifdef _LP64
  return (x != Op_RegD && x != Op_RegL && x != Op_RegP);
#else
  return (x != Op_RegD && x != Op_RegL);
#endif
}

//---------------------------may_be_copy_of_callee-----------------------------
// Check to see if we can possibly be a copy of a callee-save value.
bool PhaseChaitin::may_be_copy_of_callee( Node *def ) const {
  // Short circuit if there are no callee save registers
  if (_matcher.number_of_saved_registers() == 0) return false;

  // Expect only a spill-down and reload on exit for callee-save spills.
  // Chains of copies cannot be deep.
  // 5008997 - This is wishful thinking. Register allocator seems to
  // be splitting live ranges for callee save registers to such
  // an extent that in large methods the chains can be very long
  // (50+). The conservative answer is to return true if we don't
  // know as this prevents optimizations from occurring.

  const int limit = 60;
  int i;
  for( i=0; i < limit; i++ ) {
    if( def->is_Proj() && def->in(0)->is_Start() &&
        _matcher.is_save_on_entry(lrgs(n2lidx(def)).reg()) )
      return true;              // Direct use of callee-save proj
    if( def->is_Copy() )        // Copies carry value through
      def = def->in(def->is_Copy());
    else if( def->is_Phi() )    // Phis can merge it from any direction
      def = def->in(1);
    else
      break;
    guarantee(def != NULL, "must not resurrect dead copy");
  }
  // If we reached the end and didn't find a callee save proj
  // then this may be a callee save proj so we return true
  // as the conservative answer. If we didn't reach then end
  // we must have discovered that it was not a callee save
  // else we would have returned.
  return i == limit;
}



//------------------------------yank_if_dead-----------------------------------
// Removed an edge from 'old'.  Yank if dead.  Return adjustment counts to
// iterators in the current block.
int PhaseChaitin::yank_if_dead( Node *old, Block *current_block, Node_List *value, Node_List *regnd ) {
  int blk_adjust=0;
  while (old->outcnt() == 0 && old != C->top()) {
    Block *oldb = _cfg._bbs[old->_idx];
    oldb->find_remove(old);
    // Count 1 if deleting an instruction from the current block
    if( oldb == current_block ) blk_adjust++;
    _cfg._bbs.map(old->_idx,NULL);
    OptoReg::Name old_reg = lrgs(n2lidx(old)).reg();
    if( regnd && (*regnd)[old_reg]==old ) { // Instruction is currently available?
      value->map(old_reg,NULL);  // Yank from value/regnd maps
      regnd->map(old_reg,NULL);  // This register's value is now unknown
    }
    Node *tmp = old->req() > 1 ? old->in(1) : NULL;
    old->disconnect_inputs(NULL);
    if( !tmp ) break;
    old = tmp;
  }
  return blk_adjust;
}

//------------------------------use_prior_register-----------------------------
// Use the prior value instead of the current value, in an effort to make
// the current value go dead.  Return block iterator adjustment, in case
// we yank some instructions from this block.
int PhaseChaitin::use_prior_register( Node *n, uint idx, Node *def, Block *current_block, Node_List &value, Node_List &regnd ) {
  // No effect?
  if( def == n->in(idx) ) return 0;
  // Def is currently dead and can be removed?  Do not resurrect
  if( def->outcnt() == 0 ) return 0;

  // Not every pair of physical registers are assignment compatible,
  // e.g. on sparc floating point registers are not assignable to integer
  // registers.
  const LRG &def_lrg = lrgs(n2lidx(def));
  OptoReg::Name def_reg = def_lrg.reg();
  const RegMask &use_mask = n->in_RegMask(idx);
  bool can_use = ( RegMask::can_represent(def_reg) ? (use_mask.Member(def_reg) != 0)
                                                   : (use_mask.is_AllStack() != 0));
  // Check for a copy to or from a misaligned pair.
  can_use = can_use && !use_mask.is_misaligned_Pair() && !def_lrg.mask().is_misaligned_Pair();

  if (!can_use)
    return 0;

  // Capture the old def in case it goes dead...
  Node *old = n->in(idx);

  // Save-on-call copies can only be elided if the entire copy chain can go
  // away, lest we get the same callee-save value alive in 2 locations at
  // once.  We check for the obvious trivial case here.  Although it can
  // sometimes be elided with cooperation outside our scope, here we will just
  // miss the opportunity.  :-(
  if( may_be_copy_of_callee(def) ) {
    if( old->outcnt() > 1 ) return 0; // We're the not last user
    int idx = old->is_Copy();
    assert( idx, "chain of copies being removed" );
    Node *old2 = old->in(idx);  // Chain of copies
    if( old2->outcnt() > 1 ) return 0; // old is not the last user
    int idx2 = old2->is_Copy();
    if( !idx2 ) return 0;       // Not a chain of 2 copies
    if( def != old2->in(idx2) ) return 0; // Chain of exactly 2 copies
  }

  // Use the new def
  n->set_req(idx,def);
  _post_alloc++;

  // Is old def now dead?  We successfully yanked a copy?
  return yank_if_dead(old,current_block,&value,&regnd);
}


//------------------------------skip_copies------------------------------------
// Skip through any number of copies (that don't mod oop-i-ness)
Node *PhaseChaitin::skip_copies( Node *c ) {
  int idx = c->is_Copy();
  uint is_oop = lrgs(n2lidx(c))._is_oop;
  while (idx != 0) {
    guarantee(c->in(idx) != NULL, "must not resurrect dead copy");
    if (lrgs(n2lidx(c->in(idx)))._is_oop != is_oop)
      break;  // casting copy, not the same value
    c = c->in(idx);
    idx = c->is_Copy();
  }
  return c;
}

//------------------------------elide_copy-------------------------------------
// Remove (bypass) copies along Node n, edge k.
int PhaseChaitin::elide_copy( Node *n, int k, Block *current_block, Node_List &value, Node_List &regnd, bool can_change_regs ) {
  int blk_adjust = 0;

  uint nk_idx = n2lidx(n->in(k));
  OptoReg::Name nk_reg = lrgs(nk_idx ).reg();

  // Remove obvious same-register copies
  Node *x = n->in(k);
  int idx;
  while( (idx=x->is_Copy()) != 0 ) {
    Node *copy = x->in(idx);
    guarantee(copy != NULL, "must not resurrect dead copy");
    if( lrgs(n2lidx(copy)).reg() != nk_reg ) break;
    blk_adjust += use_prior_register(n,k,copy,current_block,value,regnd);
    if( n->in(k) != copy ) break; // Failed for some cutout?
    x = copy;                   // Progress, try again
  }

  // Phis and 2-address instructions cannot change registers so easily - their
  // outputs must match their input.
  if( !can_change_regs )
    return blk_adjust;          // Only check stupid copies!

  // Loop backedges won't have a value-mapping yet
  if( &value == NULL ) return blk_adjust;

  // Skip through all copies to the _value_ being used.  Do not change from
  // int to pointer.  This attempts to jump through a chain of copies, where
  // intermediate copies might be illegal, i.e., value is stored down to stack
  // then reloaded BUT survives in a register the whole way.
  Node *val = skip_copies(n->in(k));

  if( val == x ) return blk_adjust; // No progress?

  bool single = is_single_register(val->ideal_reg());
  uint val_idx = n2lidx(val);
  OptoReg::Name val_reg = lrgs(val_idx).reg();

  // See if it happens to already be in the correct register!
  // (either Phi's direct register, or the common case of the name
  // never-clobbered original-def register)
  if( value[val_reg] == val &&
      // Doubles check both halves
      ( single || value[val_reg-1] == val ) ) {
    blk_adjust += use_prior_register(n,k,regnd[val_reg],current_block,value,regnd);
    if( n->in(k) == regnd[val_reg] ) // Success!  Quit trying
      return blk_adjust;
  }

  // See if we can skip the copy by changing registers.  Don't change from
  // using a register to using the stack unless we know we can remove a
  // copy-load.  Otherwise we might end up making a pile of Intel cisc-spill
  // ops reading from memory instead of just loading once and using the
  // register.

  // Also handle duplicate copies here.
  const Type *t = val->is_Con() ? val->bottom_type() : NULL;

  // Scan all registers to see if this value is around already
  for( uint reg = 0; reg < (uint)_max_reg; reg++ ) {
    if (reg == (uint)nk_reg) {
      // Found ourselves so check if there is only one user of this
      // copy and keep on searching for a better copy if so.
      bool ignore_self = true;
      x = n->in(k);
      DUIterator_Fast imax, i = x->fast_outs(imax);
      Node* first = x->fast_out(i); i++;
      while (i < imax && ignore_self) {
        Node* use = x->fast_out(i); i++;
        if (use != first) ignore_self = false;
      }
      if (ignore_self) continue;
    }

    Node *vv = value[reg];
    if( !single ) {             // Doubles check for aligned-adjacent pair
      if( (reg&1)==0 ) continue;  // Wrong half of a pair
      if( vv != value[reg-1] ) continue; // Not a complete pair
    }
    if( vv == val ||            // Got a direct hit?
        (t && vv && vv->bottom_type() == t && vv->is_Mach() &&
         vv->as_Mach()->rule() == val->as_Mach()->rule()) ) { // Or same constant?
      assert( !n->is_Phi(), "cannot change registers at a Phi so easily" );
      if( OptoReg::is_stack(nk_reg) || // CISC-loading from stack OR
          OptoReg::is_reg(reg) || // turning into a register use OR
          regnd[reg]->outcnt()==1 ) { // last use of a spill-load turns into a CISC use
        blk_adjust += use_prior_register(n,k,regnd[reg],current_block,value,regnd);
        if( n->in(k) == regnd[reg] ) // Success!  Quit trying
          return blk_adjust;
      } // End of if not degrading to a stack
    } // End of if found value in another register
  } // End of scan all machine registers
  return blk_adjust;
}


//
// Check if nreg already contains the constant value val.  Normal copy
// elimination doesn't doesn't work on constants because multiple
// nodes can represent the same constant so the type and rule of the
// MachNode must be checked to ensure equivalence.
//
bool PhaseChaitin::eliminate_copy_of_constant(Node* val, Node* n,
                                              Block *current_block,
                                              Node_List& value, Node_List& regnd,
                                              OptoReg::Name nreg, OptoReg::Name nreg2) {
  if (value[nreg] != val && val->is_Con() &&
      value[nreg] != NULL && value[nreg]->is_Con() &&
      (nreg2 == OptoReg::Bad || value[nreg] == value[nreg2]) &&
      value[nreg]->bottom_type() == val->bottom_type() &&
      value[nreg]->as_Mach()->rule() == val->as_Mach()->rule()) {
    // This code assumes that two MachNodes representing constants
    // which have the same rule and the same bottom type will produce
    // identical effects into a register.  This seems like it must be
    // objectively true unless there are hidden inputs to the nodes
    // but if that were to change this code would need to updated.
    // Since they are equivalent the second one if redundant and can
    // be removed.
    //
    // n will be replaced with the old value but n might have
    // kills projections associated with it so remove them now so that
    // yank_if_dead will be able to eliminate the copy once the uses
    // have been transferred to the old[value].
    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* use = n->fast_out(i);
      if (use->is_Proj() && use->outcnt() == 0) {
        // Kill projections have no users and one input
        use->set_req(0, C->top());
        yank_if_dead(use, current_block, &value, &regnd);
        --i; --imax;
      }
    }
    _post_alloc++;
    return true;
  }
  return false;
}


//------------------------------post_allocate_copy_removal---------------------
// Post-Allocation peephole copy removal.  We do this in 1 pass over the
// basic blocks.  We maintain a mapping of registers to Nodes (an  array of
// Nodes indexed by machine register or stack slot number).  NULL means that a
// register is not mapped to any Node.  We can (want to have!) have several
// registers map to the same Node.  We walk forward over the instructions
// updating the mapping as we go.  At merge points we force a NULL if we have
// to merge 2 different Nodes into the same register.  Phi functions will give
// us a new Node if there is a proper value merging.  Since the blocks are
// arranged in some RPO, we will visit all parent blocks before visiting any
// successor blocks (except at loops).
//
// If we find a Copy we look to see if the Copy's source register is a stack
// slot and that value has already been loaded into some machine register; if
// so we use machine register directly.  This turns a Load into a reg-reg
// Move.  We also look for reloads of identical constants.
//
// When we see a use from a reg-reg Copy, we will attempt to use the copy's
// source directly and make the copy go dead.
void PhaseChaitin::post_allocate_copy_removal() {
  NOT_PRODUCT( Compile::TracePhase t3("postAllocCopyRemoval", &_t_postAllocCopyRemoval, TimeCompiler); )
  ResourceMark rm;

  // Need a mapping from basic block Node_Lists.  We need a Node_List to
  // map from register number to value-producing Node.
  Node_List **blk2value = NEW_RESOURCE_ARRAY( Node_List *, _cfg._num_blocks+1);
  memset( blk2value, 0, sizeof(Node_List*)*(_cfg._num_blocks+1) );
  // Need a mapping from basic block Node_Lists.  We need a Node_List to
  // map from register number to register-defining Node.
  Node_List **blk2regnd = NEW_RESOURCE_ARRAY( Node_List *, _cfg._num_blocks+1);
  memset( blk2regnd, 0, sizeof(Node_List*)*(_cfg._num_blocks+1) );

  // We keep unused Node_Lists on a free_list to avoid wasting
  // memory.
  GrowableArray<Node_List*> free_list = GrowableArray<Node_List*>(16);

  // For all blocks
  for( uint i = 0; i < _cfg._num_blocks; i++ ) {
    uint j;
    Block *b = _cfg._blocks[i];

    // Count of Phis in block
    uint phi_dex;
    for( phi_dex = 1; phi_dex < b->_nodes.size(); phi_dex++ ) {
      Node *phi = b->_nodes[phi_dex];
      if( !phi->is_Phi() )
        break;
    }

    // If any predecessor has not been visited, we do not know the state
    // of registers at the start.  Check for this, while updating copies
    // along Phi input edges
    bool missing_some_inputs = false;
    Block *freed = NULL;
    for( j = 1; j < b->num_preds(); j++ ) {
      Block *pb = _cfg._bbs[b->pred(j)->_idx];
      // Remove copies along phi edges
      for( uint k=1; k<phi_dex; k++ )
        elide_copy( b->_nodes[k], j, b, *blk2value[pb->_pre_order], *blk2regnd[pb->_pre_order], false );
      if( blk2value[pb->_pre_order] ) { // Have a mapping on this edge?
        // See if this predecessor's mappings have been used by everybody
        // who wants them.  If so, free 'em.
        uint k;
        for( k=0; k<pb->_num_succs; k++ ) {
          Block *pbsucc = pb->_succs[k];
          if( !blk2value[pbsucc->_pre_order] && pbsucc != b )
            break;              // Found a future user
        }
        if( k >= pb->_num_succs ) { // No more uses, free!
          freed = pb;           // Record last block freed
          free_list.push(blk2value[pb->_pre_order]);
          free_list.push(blk2regnd[pb->_pre_order]);
        }
      } else {                  // This block has unvisited (loopback) inputs
        missing_some_inputs = true;
      }
    }


    // Extract Node_List mappings.  If 'freed' is non-zero, we just popped
    // 'freed's blocks off the list
    Node_List &regnd = *(free_list.is_empty() ? new Node_List() : free_list.pop());
    Node_List &value = *(free_list.is_empty() ? new Node_List() : free_list.pop());
    assert( !freed || blk2value[freed->_pre_order] == &value, "" );
    value.map(_max_reg,NULL);
    regnd.map(_max_reg,NULL);
    // Set mappings as OUR mappings
    blk2value[b->_pre_order] = &value;
    blk2regnd[b->_pre_order] = &regnd;

    // Initialize value & regnd for this block
    if( missing_some_inputs ) {
      // Some predecessor has not yet been visited; zap map to empty
      for( uint k = 0; k < (uint)_max_reg; k++ ) {
        value.map(k,NULL);
        regnd.map(k,NULL);
      }
    } else {
      if( !freed ) {            // Didn't get a freebie prior block
        // Must clone some data
        freed = _cfg._bbs[b->pred(1)->_idx];
        Node_List &f_value = *blk2value[freed->_pre_order];
        Node_List &f_regnd = *blk2regnd[freed->_pre_order];
        for( uint k = 0; k < (uint)_max_reg; k++ ) {
          value.map(k,f_value[k]);
          regnd.map(k,f_regnd[k]);
        }
      }
      // Merge all inputs together, setting to NULL any conflicts.
      for( j = 1; j < b->num_preds(); j++ ) {
        Block *pb = _cfg._bbs[b->pred(j)->_idx];
        if( pb == freed ) continue; // Did self already via freelist
        Node_List &p_regnd = *blk2regnd[pb->_pre_order];
        for( uint k = 0; k < (uint)_max_reg; k++ ) {
          if( regnd[k] != p_regnd[k] ) { // Conflict on reaching defs?
            value.map(k,NULL); // Then no value handy
            regnd.map(k,NULL);
          }
        }
      }
    }

    // For all Phi's
    for( j = 1; j < phi_dex; j++ ) {
      uint k;
      Node *phi = b->_nodes[j];
      uint pidx = n2lidx(phi);
      OptoReg::Name preg = lrgs(n2lidx(phi)).reg();

      // Remove copies remaining on edges.  Check for junk phi.
      Node *u = NULL;
      for( k=1; k<phi->req(); k++ ) {
        Node *x = phi->in(k);
        if( phi != x && u != x ) // Found a different input
          u = u ? NodeSentinel : x; // Capture unique input, or NodeSentinel for 2nd input
      }
      if( u != NodeSentinel ) {    // Junk Phi.  Remove
        b->_nodes.remove(j--); phi_dex--;
        _cfg._bbs.map(phi->_idx,NULL);
        phi->replace_by(u);
        phi->disconnect_inputs(NULL);
        continue;
      }
      // Note that if value[pidx] exists, then we merged no new values here
      // and the phi is useless.  This can happen even with the above phi
      // removal for complex flows.  I cannot keep the better known value here
      // because locally the phi appears to define a new merged value.  If I
      // keep the better value then a copy of the phi, being unable to use the
      // global flow analysis, can't "peek through" the phi to the original
      // reaching value and so will act like it's defining a new value.  This
      // can lead to situations where some uses are from the old and some from
      // the new values.  Not illegal by itself but throws the over-strong
      // assert in scheduling.
      if( pidx ) {
        value.map(preg,phi);
        regnd.map(preg,phi);
        OptoReg::Name preg_lo = OptoReg::add(preg,-1);
        if( !is_single_register(phi->ideal_reg()) ) {
          value.map(preg_lo,phi);
          regnd.map(preg_lo,phi);
        }
      }
    }

    // For all remaining instructions
    for( j = phi_dex; j < b->_nodes.size(); j++ ) {
      Node *n = b->_nodes[j];

      if( n->outcnt() == 0 &&   // Dead?
          n != C->top() &&      // (ignore TOP, it has no du info)
          !n->is_Proj() ) {     // fat-proj kills
        j -= yank_if_dead(n,b,&value,&regnd);
        continue;
      }

      // Improve reaching-def info.  Occasionally post-alloc's liveness gives
      // up (at loop backedges, because we aren't doing a full flow pass).
      // The presence of a live use essentially asserts that the use's def is
      // alive and well at the use (or else the allocator fubar'd).  Take
      // advantage of this info to set a reaching def for the use-reg.
      uint k;
      for( k = 1; k < n->req(); k++ ) {
        Node *def = n->in(k);   // n->in(k) is a USE; def is the DEF for this USE
        guarantee(def != NULL, "no disconnected nodes at this point");
        uint useidx = n2lidx(def); // useidx is the live range index for this USE

        if( useidx ) {
          OptoReg::Name ureg = lrgs(useidx).reg();
          if( !value[ureg] ) {
            int idx;            // Skip occasional useless copy
            while( (idx=def->is_Copy()) != 0 &&
                   def->in(idx) != NULL &&  // NULL should not happen
                   ureg == lrgs(n2lidx(def->in(idx))).reg() )
              def = def->in(idx);
            Node *valdef = skip_copies(def); // tighten up val through non-useless copies
            value.map(ureg,valdef); // record improved reaching-def info
            regnd.map(ureg,   def);
            // Record other half of doubles
            OptoReg::Name ureg_lo = OptoReg::add(ureg,-1);
            if( !is_single_register(def->ideal_reg()) &&
                ( !RegMask::can_represent(ureg_lo) ||
                  lrgs(useidx).mask().Member(ureg_lo) ) && // Nearly always adjacent
                !value[ureg_lo] ) {
              value.map(ureg_lo,valdef); // record improved reaching-def info
              regnd.map(ureg_lo,   def);
            }
          }
        }
      }

      const uint two_adr = n->is_Mach() ? n->as_Mach()->two_adr() : 0;

      // Remove copies along input edges
      for( k = 1; k < n->req(); k++ )
        j -= elide_copy( n, k, b, value, regnd, two_adr!=k );

      // Unallocated Nodes define no registers
      uint lidx = n2lidx(n);
      if( !lidx ) continue;

      // Update the register defined by this instruction
      OptoReg::Name nreg = lrgs(lidx).reg();
      // Skip through all copies to the _value_ being defined.
      // Do not change from int to pointer
      Node *val = skip_copies(n);

      uint n_ideal_reg = n->ideal_reg();
      if( is_single_register(n_ideal_reg) ) {
        // If Node 'n' does not change the value mapped by the register,
        // then 'n' is a useless copy.  Do not update the register->node
        // mapping so 'n' will go dead.
        if( value[nreg] != val ) {
          if (eliminate_copy_of_constant(val, n, b, value, regnd, nreg, OptoReg::Bad)) {
            n->replace_by(regnd[nreg]);
            j -= yank_if_dead(n,b,&value,&regnd);
          } else {
            // Update the mapping: record new Node defined by the register
            regnd.map(nreg,n);
            // Update mapping for defined *value*, which is the defined
            // Node after skipping all copies.
            value.map(nreg,val);
          }
        } else if( !may_be_copy_of_callee(n) && regnd[nreg]->outcnt() != 0 ) {
          assert( n->is_Copy(), "" );
          n->replace_by(regnd[nreg]);
          j -= yank_if_dead(n,b,&value,&regnd);
        }
      } else {
        // If the value occupies a register pair, record same info
        // in both registers.
        OptoReg::Name nreg_lo = OptoReg::add(nreg,-1);
        if( RegMask::can_represent(nreg_lo) &&     // Either a spill slot, or
            !lrgs(lidx).mask().Member(nreg_lo) ) { // Nearly always adjacent
          // Sparc occasionally has non-adjacent pairs.
          // Find the actual other value
          RegMask tmp = lrgs(lidx).mask();
          tmp.Remove(nreg);
          nreg_lo = tmp.find_first_elem();
        }
        if( value[nreg] != val || value[nreg_lo] != val ) {
          if (eliminate_copy_of_constant(val, n, b, value, regnd, nreg, nreg_lo)) {
            n->replace_by(regnd[nreg]);
            j -= yank_if_dead(n,b,&value,&regnd);
          } else {
            regnd.map(nreg   , n );
            regnd.map(nreg_lo, n );
            value.map(nreg   ,val);
            value.map(nreg_lo,val);
          }
        } else if( !may_be_copy_of_callee(n) && regnd[nreg]->outcnt() != 0 ) {
          assert( n->is_Copy(), "" );
          n->replace_by(regnd[nreg]);
          j -= yank_if_dead(n,b,&value,&regnd);
        }
      }

      // Fat projections kill many registers
      if( n_ideal_reg == MachProjNode::fat_proj ) {
        RegMask rm = n->out_RegMask();
        // wow, what an expensive iterator...
        nreg = rm.find_first_elem();
        while( OptoReg::is_valid(nreg)) {
          rm.Remove(nreg);
          value.map(nreg,n);
          regnd.map(nreg,n);
          nreg = rm.find_first_elem();
        }
      }

    } // End of for all instructions in the block

  } // End for all blocks
}
