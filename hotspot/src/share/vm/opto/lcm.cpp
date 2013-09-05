/*
 * Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/block.hpp"
#include "opto/c2compiler.hpp"
#include "opto/callnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/machnode.hpp"
#include "opto/runtime.hpp"
#ifdef TARGET_ARCH_MODEL_x86_32
# include "adfiles/ad_x86_32.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_x86_64
# include "adfiles/ad_x86_64.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_sparc
# include "adfiles/ad_sparc.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_zero
# include "adfiles/ad_zero.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_arm
# include "adfiles/ad_arm.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_ppc_32
# include "adfiles/ad_ppc_32.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_ppc_64
# include "adfiles/ad_ppc_64.hpp"
#endif

// Optimization - Graph Style

//------------------------------implicit_null_check----------------------------
// Detect implicit-null-check opportunities.  Basically, find NULL checks
// with suitable memory ops nearby.  Use the memory op to do the NULL check.
// I can generate a memory op if there is not one nearby.
// The proj is the control projection for the not-null case.
// The val is the pointer being checked for nullness or
// decodeHeapOop_not_null node if it did not fold into address.
void Block::implicit_null_check(PhaseCFG *cfg, Node *proj, Node *val, int allowed_reasons) {
  // Assume if null check need for 0 offset then always needed
  // Intel solaris doesn't support any null checks yet and no
  // mechanism exists (yet) to set the switches at an os_cpu level
  if( !ImplicitNullChecks || MacroAssembler::needs_explicit_null_check(0)) return;

  // Make sure the ptr-is-null path appears to be uncommon!
  float f = end()->as_MachIf()->_prob;
  if( proj->Opcode() == Op_IfTrue ) f = 1.0f - f;
  if( f > PROB_UNLIKELY_MAG(4) ) return;

  uint bidx = 0;                // Capture index of value into memop
  bool was_store;               // Memory op is a store op

  // Get the successor block for if the test ptr is non-null
  Block* not_null_block;  // this one goes with the proj
  Block* null_block;
  if (_nodes[_nodes.size()-1] == proj) {
    null_block     = _succs[0];
    not_null_block = _succs[1];
  } else {
    assert(_nodes[_nodes.size()-2] == proj, "proj is one or the other");
    not_null_block = _succs[0];
    null_block     = _succs[1];
  }
  while (null_block->is_Empty() == Block::empty_with_goto) {
    null_block     = null_block->_succs[0];
  }

  // Search the exception block for an uncommon trap.
  // (See Parse::do_if and Parse::do_ifnull for the reason
  // we need an uncommon trap.  Briefly, we need a way to
  // detect failure of this optimization, as in 6366351.)
  {
    bool found_trap = false;
    for (uint i1 = 0; i1 < null_block->_nodes.size(); i1++) {
      Node* nn = null_block->_nodes[i1];
      if (nn->is_MachCall() &&
          nn->as_MachCall()->entry_point() == SharedRuntime::uncommon_trap_blob()->entry_point()) {
        const Type* trtype = nn->in(TypeFunc::Parms)->bottom_type();
        if (trtype->isa_int() && trtype->is_int()->is_con()) {
          jint tr_con = trtype->is_int()->get_con();
          Deoptimization::DeoptReason reason = Deoptimization::trap_request_reason(tr_con);
          Deoptimization::DeoptAction action = Deoptimization::trap_request_action(tr_con);
          assert((int)reason < (int)BitsPerInt, "recode bit map");
          if (is_set_nth_bit(allowed_reasons, (int) reason)
              && action != Deoptimization::Action_none) {
            // This uncommon trap is sure to recompile, eventually.
            // When that happens, C->too_many_traps will prevent
            // this transformation from happening again.
            found_trap = true;
          }
        }
        break;
      }
    }
    if (!found_trap) {
      // We did not find an uncommon trap.
      return;
    }
  }

  // Check for decodeHeapOop_not_null node which did not fold into address
  bool is_decoden = ((intptr_t)val) & 1;
  val = (Node*)(((intptr_t)val) & ~1);

  assert(!is_decoden || (val->in(0) == NULL) && val->is_Mach() &&
         (val->as_Mach()->ideal_Opcode() == Op_DecodeN), "sanity");

  // Search the successor block for a load or store who's base value is also
  // the tested value.  There may be several.
  Node_List *out = new Node_List(Thread::current()->resource_area());
  MachNode *best = NULL;        // Best found so far
  for (DUIterator i = val->outs(); val->has_out(i); i++) {
    Node *m = val->out(i);
    if( !m->is_Mach() ) continue;
    MachNode *mach = m->as_Mach();
    was_store = false;
    int iop = mach->ideal_Opcode();
    switch( iop ) {
    case Op_LoadB:
    case Op_LoadUB:
    case Op_LoadUS:
    case Op_LoadD:
    case Op_LoadF:
    case Op_LoadI:
    case Op_LoadL:
    case Op_LoadP:
    case Op_LoadN:
    case Op_LoadS:
    case Op_LoadKlass:
    case Op_LoadNKlass:
    case Op_LoadRange:
    case Op_LoadD_unaligned:
    case Op_LoadL_unaligned:
      assert(mach->in(2) == val, "should be address");
      break;
    case Op_StoreB:
    case Op_StoreC:
    case Op_StoreCM:
    case Op_StoreD:
    case Op_StoreF:
    case Op_StoreI:
    case Op_StoreL:
    case Op_StoreP:
    case Op_StoreN:
    case Op_StoreNKlass:
      was_store = true;         // Memory op is a store op
      // Stores will have their address in slot 2 (memory in slot 1).
      // If the value being nul-checked is in another slot, it means we
      // are storing the checked value, which does NOT check the value!
      if( mach->in(2) != val ) continue;
      break;                    // Found a memory op?
    case Op_StrComp:
    case Op_StrEquals:
    case Op_StrIndexOf:
    case Op_AryEq:
    case Op_EncodeISOArray:
      // Not a legit memory op for implicit null check regardless of
      // embedded loads
      continue;
    default:                    // Also check for embedded loads
      if( !mach->needs_anti_dependence_check() )
        continue;               // Not an memory op; skip it
      if( must_clone[iop] ) {
        // Do not move nodes which produce flags because
        // RA will try to clone it to place near branch and
        // it will cause recompilation, see clone_node().
        continue;
      }
      {
        // Check that value is used in memory address in
        // instructions with embedded load (CmpP val1,(val2+off)).
        Node* base;
        Node* index;
        const MachOper* oper = mach->memory_inputs(base, index);
        if (oper == NULL || oper == (MachOper*)-1) {
          continue;             // Not an memory op; skip it
        }
        if (val == base ||
            val == index && val->bottom_type()->isa_narrowoop()) {
          break;                // Found it
        } else {
          continue;             // Skip it
        }
      }
      break;
    }
    // check if the offset is not too high for implicit exception
    {
      intptr_t offset = 0;
      const TypePtr *adr_type = NULL;  // Do not need this return value here
      const Node* base = mach->get_base_and_disp(offset, adr_type);
      if (base == NULL || base == NodeSentinel) {
        // Narrow oop address doesn't have base, only index
        if( val->bottom_type()->isa_narrowoop() &&
            MacroAssembler::needs_explicit_null_check(offset) )
          continue;             // Give up if offset is beyond page size
        // cannot reason about it; is probably not implicit null exception
      } else {
        const TypePtr* tptr;
        if (UseCompressedOops && (Universe::narrow_oop_shift() == 0 ||
                                  Universe::narrow_klass_shift() == 0)) {
          // 32-bits narrow oop can be the base of address expressions
          tptr = base->get_ptr_type();
        } else {
          // only regular oops are expected here
          tptr = base->bottom_type()->is_ptr();
        }
        // Give up if offset is not a compile-time constant
        if( offset == Type::OffsetBot || tptr->_offset == Type::OffsetBot )
          continue;
        offset += tptr->_offset; // correct if base is offseted
        if( MacroAssembler::needs_explicit_null_check(offset) )
          continue;             // Give up is reference is beyond 4K page size
      }
    }

    // Check ctrl input to see if the null-check dominates the memory op
    Block *cb = cfg->get_block_for_node(mach);
    cb = cb->_idom;             // Always hoist at least 1 block
    if( !was_store ) {          // Stores can be hoisted only one block
      while( cb->_dom_depth > (_dom_depth + 1))
        cb = cb->_idom;         // Hoist loads as far as we want
      // The non-null-block should dominate the memory op, too. Live
      // range spilling will insert a spill in the non-null-block if it is
      // needs to spill the memory op for an implicit null check.
      if (cb->_dom_depth == (_dom_depth + 1)) {
        if (cb != not_null_block) continue;
        cb = cb->_idom;
      }
    }
    if( cb != this ) continue;

    // Found a memory user; see if it can be hoisted to check-block
    uint vidx = 0;              // Capture index of value into memop
    uint j;
    for( j = mach->req()-1; j > 0; j-- ) {
      if( mach->in(j) == val ) {
        vidx = j;
        // Ignore DecodeN val which could be hoisted to where needed.
        if( is_decoden ) continue;
      }
      // Block of memory-op input
      Block *inb = cfg->get_block_for_node(mach->in(j));
      Block *b = this;          // Start from nul check
      while( b != inb && b->_dom_depth > inb->_dom_depth )
        b = b->_idom;           // search upwards for input
      // See if input dominates null check
      if( b != inb )
        break;
    }
    if( j > 0 )
      continue;
    Block *mb = cfg->get_block_for_node(mach);
    // Hoisting stores requires more checks for the anti-dependence case.
    // Give up hoisting if we have to move the store past any load.
    if( was_store ) {
      Block *b = mb;            // Start searching here for a local load
      // mach use (faulting) trying to hoist
      // n might be blocker to hoisting
      while( b != this ) {
        uint k;
        for( k = 1; k < b->_nodes.size(); k++ ) {
          Node *n = b->_nodes[k];
          if( n->needs_anti_dependence_check() &&
              n->in(LoadNode::Memory) == mach->in(StoreNode::Memory) )
            break;              // Found anti-dependent load
        }
        if( k < b->_nodes.size() )
          break;                // Found anti-dependent load
        // Make sure control does not do a merge (would have to check allpaths)
        if( b->num_preds() != 2 ) break;
        b = cfg->get_block_for_node(b->pred(1)); // Move up to predecessor block
      }
      if( b != this ) continue;
    }

    // Make sure this memory op is not already being used for a NullCheck
    Node *e = mb->end();
    if( e->is_MachNullCheck() && e->in(1) == mach )
      continue;                 // Already being used as a NULL check

    // Found a candidate!  Pick one with least dom depth - the highest
    // in the dom tree should be closest to the null check.
    if (best == NULL || cfg->get_block_for_node(mach)->_dom_depth < cfg->get_block_for_node(best)->_dom_depth) {
      best = mach;
      bidx = vidx;
    }
  }
  // No candidate!
  if (best == NULL) {
    return;
  }

  // ---- Found an implicit null check
  extern int implicit_null_checks;
  implicit_null_checks++;

  if( is_decoden ) {
    // Check if we need to hoist decodeHeapOop_not_null first.
    Block *valb = cfg->get_block_for_node(val);
    if( this != valb && this->_dom_depth < valb->_dom_depth ) {
      // Hoist it up to the end of the test block.
      valb->find_remove(val);
      this->add_inst(val);
      cfg->map_node_to_block(val, this);
      // DecodeN on x86 may kill flags. Check for flag-killing projections
      // that also need to be hoisted.
      for (DUIterator_Fast jmax, j = val->fast_outs(jmax); j < jmax; j++) {
        Node* n = val->fast_out(j);
        if( n->is_MachProj() ) {
          cfg->get_block_for_node(n)->find_remove(n);
          this->add_inst(n);
          cfg->map_node_to_block(n, this);
        }
      }
    }
  }
  // Hoist the memory candidate up to the end of the test block.
  Block *old_block = cfg->get_block_for_node(best);
  old_block->find_remove(best);
  add_inst(best);
  cfg->map_node_to_block(best, this);

  // Move the control dependence
  if (best->in(0) && best->in(0) == old_block->_nodes[0])
    best->set_req(0, _nodes[0]);

  // Check for flag-killing projections that also need to be hoisted
  // Should be DU safe because no edge updates.
  for (DUIterator_Fast jmax, j = best->fast_outs(jmax); j < jmax; j++) {
    Node* n = best->fast_out(j);
    if( n->is_MachProj() ) {
      cfg->get_block_for_node(n)->find_remove(n);
      add_inst(n);
      cfg->map_node_to_block(n, this);
    }
  }

  Compile *C = cfg->C;
  // proj==Op_True --> ne test; proj==Op_False --> eq test.
  // One of two graph shapes got matched:
  //   (IfTrue  (If (Bool NE (CmpP ptr NULL))))
  //   (IfFalse (If (Bool EQ (CmpP ptr NULL))))
  // NULL checks are always branch-if-eq.  If we see a IfTrue projection
  // then we are replacing a 'ne' test with a 'eq' NULL check test.
  // We need to flip the projections to keep the same semantics.
  if( proj->Opcode() == Op_IfTrue ) {
    // Swap order of projections in basic block to swap branch targets
    Node *tmp1 = _nodes[end_idx()+1];
    Node *tmp2 = _nodes[end_idx()+2];
    _nodes.map(end_idx()+1, tmp2);
    _nodes.map(end_idx()+2, tmp1);
    Node *tmp = new (C) Node(C->top()); // Use not NULL input
    tmp1->replace_by(tmp);
    tmp2->replace_by(tmp1);
    tmp->replace_by(tmp2);
    tmp->destruct();
  }

  // Remove the existing null check; use a new implicit null check instead.
  // Since schedule-local needs precise def-use info, we need to correct
  // it as well.
  Node *old_tst = proj->in(0);
  MachNode *nul_chk = new (C) MachNullCheckNode(old_tst->in(0),best,bidx);
  _nodes.map(end_idx(),nul_chk);
  cfg->map_node_to_block(nul_chk, this);
  // Redirect users of old_test to nul_chk
  for (DUIterator_Last i2min, i2 = old_tst->last_outs(i2min); i2 >= i2min; --i2)
    old_tst->last_out(i2)->set_req(0, nul_chk);
  // Clean-up any dead code
  for (uint i3 = 0; i3 < old_tst->req(); i3++)
    old_tst->set_req(i3, NULL);

  cfg->latency_from_uses(nul_chk);
  cfg->latency_from_uses(best);
}


//------------------------------select-----------------------------------------
// Select a nice fellow from the worklist to schedule next. If there is only
// one choice, then use it. Projections take top priority for correctness
// reasons - if I see a projection, then it is next.  There are a number of
// other special cases, for instructions that consume condition codes, et al.
// These are chosen immediately. Some instructions are required to immediately
// precede the last instruction in the block, and these are taken last. Of the
// remaining cases (most), choose the instruction with the greatest latency
// (that is, the most number of pseudo-cycles required to the end of the
// routine). If there is a tie, choose the instruction with the most inputs.
Node *Block::select(PhaseCFG *cfg, Node_List &worklist, GrowableArray<int> &ready_cnt, VectorSet &next_call, uint sched_slot) {

  // If only a single entry on the stack, use it
  uint cnt = worklist.size();
  if (cnt == 1) {
    Node *n = worklist[0];
    worklist.map(0,worklist.pop());
    return n;
  }

  uint choice  = 0; // Bigger is most important
  uint latency = 0; // Bigger is scheduled first
  uint score   = 0; // Bigger is better
  int idx = -1;     // Index in worklist
  int cand_cnt = 0; // Candidate count

  for( uint i=0; i<cnt; i++ ) { // Inspect entire worklist
    // Order in worklist is used to break ties.
    // See caller for how this is used to delay scheduling
    // of induction variable increments to after the other
    // uses of the phi are scheduled.
    Node *n = worklist[i];      // Get Node on worklist

    int iop = n->is_Mach() ? n->as_Mach()->ideal_Opcode() : 0;
    if( n->is_Proj() ||         // Projections always win
        n->Opcode()== Op_Con || // So does constant 'Top'
        iop == Op_CreateEx ||   // Create-exception must start block
        iop == Op_CheckCastPP
        ) {
      worklist.map(i,worklist.pop());
      return n;
    }

    // Final call in a block must be adjacent to 'catch'
    Node *e = end();
    if( e->is_Catch() && e->in(0)->in(0) == n )
      continue;

    // Memory op for an implicit null check has to be at the end of the block
    if( e->is_MachNullCheck() && e->in(1) == n )
      continue;

    // Schedule IV increment last.
    if (e->is_Mach() && e->as_Mach()->ideal_Opcode() == Op_CountedLoopEnd &&
        e->in(1)->in(1) == n && n->is_iteratively_computed())
      continue;

    uint n_choice  = 2;

    // See if this instruction is consumed by a branch. If so, then (as the
    // branch is the last instruction in the basic block) force it to the
    // end of the basic block
    if ( must_clone[iop] ) {
      // See if any use is a branch
      bool found_machif = false;

      for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
        Node* use = n->fast_out(j);

        // The use is a conditional branch, make them adjacent
        if (use->is_MachIf() && cfg->get_block_for_node(use) == this) {
          found_machif = true;
          break;
        }

        // More than this instruction pending for successor to be ready,
        // don't choose this if other opportunities are ready
        if (ready_cnt.at(use->_idx) > 1)
          n_choice = 1;
      }

      // loop terminated, prefer not to use this instruction
      if (found_machif)
        continue;
    }

    // See if this has a predecessor that is "must_clone", i.e. sets the
    // condition code. If so, choose this first
    for (uint j = 0; j < n->req() ; j++) {
      Node *inn = n->in(j);
      if (inn) {
        if (inn->is_Mach() && must_clone[inn->as_Mach()->ideal_Opcode()] ) {
          n_choice = 3;
          break;
        }
      }
    }

    // MachTemps should be scheduled last so they are near their uses
    if (n->is_MachTemp()) {
      n_choice = 1;
    }

    uint n_latency = cfg->get_latency_for_node(n);
    uint n_score   = n->req();   // Many inputs get high score to break ties

    // Keep best latency found
    cand_cnt++;
    if (choice < n_choice ||
        (choice == n_choice &&
         ((StressLCM && Compile::randomized_select(cand_cnt)) ||
          (!StressLCM &&
           (latency < n_latency ||
            (latency == n_latency &&
             (score < n_score))))))) {
      choice  = n_choice;
      latency = n_latency;
      score   = n_score;
      idx     = i;               // Also keep index in worklist
    }
  } // End of for all ready nodes in worklist

  assert(idx >= 0, "index should be set");
  Node *n = worklist[(uint)idx];      // Get the winner

  worklist.map((uint)idx, worklist.pop());     // Compress worklist
  return n;
}


//------------------------------set_next_call----------------------------------
void Block::set_next_call( Node *n, VectorSet &next_call, PhaseCFG* cfg) {
  if( next_call.test_set(n->_idx) ) return;
  for( uint i=0; i<n->len(); i++ ) {
    Node *m = n->in(i);
    if( !m ) continue;  // must see all nodes in block that precede call
    if (cfg->get_block_for_node(m) == this) {
      set_next_call(m, next_call, cfg);
    }
  }
}

//------------------------------needed_for_next_call---------------------------
// Set the flag 'next_call' for each Node that is needed for the next call to
// be scheduled.  This flag lets me bias scheduling so Nodes needed for the
// next subroutine call get priority - basically it moves things NOT needed
// for the next call till after the call.  This prevents me from trying to
// carry lots of stuff live across a call.
void Block::needed_for_next_call(Node *this_call, VectorSet &next_call, PhaseCFG* cfg) {
  // Find the next control-defining Node in this block
  Node* call = NULL;
  for (DUIterator_Fast imax, i = this_call->fast_outs(imax); i < imax; i++) {
    Node* m = this_call->fast_out(i);
    if(cfg->get_block_for_node(m) == this && // Local-block user
        m != this_call &&       // Not self-start node
        m->is_MachCall() )
      call = m;
      break;
  }
  if (call == NULL)  return;    // No next call (e.g., block end is near)
  // Set next-call for all inputs to this call
  set_next_call(call, next_call, cfg);
}

//------------------------------add_call_kills-------------------------------------
void Block::add_call_kills(MachProjNode *proj, RegMask& regs, const char* save_policy, bool exclude_soe) {
  // Fill in the kill mask for the call
  for( OptoReg::Name r = OptoReg::Name(0); r < _last_Mach_Reg; r=OptoReg::add(r,1) ) {
    if( !regs.Member(r) ) {     // Not already defined by the call
      // Save-on-call register?
      if ((save_policy[r] == 'C') ||
          (save_policy[r] == 'A') ||
          ((save_policy[r] == 'E') && exclude_soe)) {
        proj->_rout.Insert(r);
      }
    }
  }
}


//------------------------------sched_call-------------------------------------
uint Block::sched_call( Matcher &matcher, PhaseCFG* cfg, uint node_cnt, Node_List &worklist, GrowableArray<int> &ready_cnt, MachCallNode *mcall, VectorSet &next_call ) {
  RegMask regs;

  // Schedule all the users of the call right now.  All the users are
  // projection Nodes, so they must be scheduled next to the call.
  // Collect all the defined registers.
  for (DUIterator_Fast imax, i = mcall->fast_outs(imax); i < imax; i++) {
    Node* n = mcall->fast_out(i);
    assert( n->is_MachProj(), "" );
    int n_cnt = ready_cnt.at(n->_idx)-1;
    ready_cnt.at_put(n->_idx, n_cnt);
    assert( n_cnt == 0, "" );
    // Schedule next to call
    _nodes.map(node_cnt++, n);
    // Collect defined registers
    regs.OR(n->out_RegMask());
    // Check for scheduling the next control-definer
    if( n->bottom_type() == Type::CONTROL )
      // Warm up next pile of heuristic bits
      needed_for_next_call(n, next_call, cfg);

    // Children of projections are now all ready
    for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
      Node* m = n->fast_out(j); // Get user
      if(cfg->get_block_for_node(m) != this) {
        continue;
      }
      if( m->is_Phi() ) continue;
      int m_cnt = ready_cnt.at(m->_idx)-1;
      ready_cnt.at_put(m->_idx, m_cnt);
      if( m_cnt == 0 )
        worklist.push(m);
    }

  }

  // Act as if the call defines the Frame Pointer.
  // Certainly the FP is alive and well after the call.
  regs.Insert(matcher.c_frame_pointer());

  // Set all registers killed and not already defined by the call.
  uint r_cnt = mcall->tf()->range()->cnt();
  int op = mcall->ideal_Opcode();
  MachProjNode *proj = new (matcher.C) MachProjNode( mcall, r_cnt+1, RegMask::Empty, MachProjNode::fat_proj );
  cfg->map_node_to_block(proj, this);
  _nodes.insert(node_cnt++, proj);

  // Select the right register save policy.
  const char * save_policy;
  switch (op) {
    case Op_CallRuntime:
    case Op_CallLeaf:
    case Op_CallLeafNoFP:
      // Calling C code so use C calling convention
      save_policy = matcher._c_reg_save_policy;
      break;

    case Op_CallStaticJava:
    case Op_CallDynamicJava:
      // Calling Java code so use Java calling convention
      save_policy = matcher._register_save_policy;
      break;

    default:
      ShouldNotReachHere();
  }

  // When using CallRuntime mark SOE registers as killed by the call
  // so values that could show up in the RegisterMap aren't live in a
  // callee saved register since the register wouldn't know where to
  // find them.  CallLeaf and CallLeafNoFP are ok because they can't
  // have debug info on them.  Strictly speaking this only needs to be
  // done for oops since idealreg2debugmask takes care of debug info
  // references but there no way to handle oops differently than other
  // pointers as far as the kill mask goes.
  bool exclude_soe = op == Op_CallRuntime;

  // If the call is a MethodHandle invoke, we need to exclude the
  // register which is used to save the SP value over MH invokes from
  // the mask.  Otherwise this register could be used for
  // deoptimization information.
  if (op == Op_CallStaticJava) {
    MachCallStaticJavaNode* mcallstaticjava = (MachCallStaticJavaNode*) mcall;
    if (mcallstaticjava->_method_handle_invoke)
      proj->_rout.OR(Matcher::method_handle_invoke_SP_save_mask());
  }

  add_call_kills(proj, regs, save_policy, exclude_soe);

  return node_cnt;
}


//------------------------------schedule_local---------------------------------
// Topological sort within a block.  Someday become a real scheduler.
bool Block::schedule_local(PhaseCFG *cfg, Matcher &matcher, GrowableArray<int> &ready_cnt, VectorSet &next_call) {
  // Already "sorted" are the block start Node (as the first entry), and
  // the block-ending Node and any trailing control projections.  We leave
  // these alone.  PhiNodes and ParmNodes are made to follow the block start
  // Node.  Everything else gets topo-sorted.

#ifndef PRODUCT
    if (cfg->trace_opto_pipelining()) {
      tty->print_cr("# --- schedule_local B%d, before: ---", _pre_order);
      for (uint i = 0;i < _nodes.size();i++) {
        tty->print("# ");
        _nodes[i]->fast_dump();
      }
      tty->print_cr("#");
    }
#endif

  // RootNode is already sorted
  if( _nodes.size() == 1 ) return true;

  // Move PhiNodes and ParmNodes from 1 to cnt up to the start
  uint node_cnt = end_idx();
  uint phi_cnt = 1;
  uint i;
  for( i = 1; i<node_cnt; i++ ) { // Scan for Phi
    Node *n = _nodes[i];
    if( n->is_Phi() ||          // Found a PhiNode or ParmNode
        (n->is_Proj()  && n->in(0) == head()) ) {
      // Move guy at 'phi_cnt' to the end; makes a hole at phi_cnt
      _nodes.map(i,_nodes[phi_cnt]);
      _nodes.map(phi_cnt++,n);  // swap Phi/Parm up front
    } else {                    // All others
      // Count block-local inputs to 'n'
      uint cnt = n->len();      // Input count
      uint local = 0;
      for( uint j=0; j<cnt; j++ ) {
        Node *m = n->in(j);
        if( m && cfg->get_block_for_node(m) == this && !m->is_top() )
          local++;              // One more block-local input
      }
      ready_cnt.at_put(n->_idx, local); // Count em up

#ifdef ASSERT
      if( UseConcMarkSweepGC || UseG1GC ) {
        if( n->is_Mach() && n->as_Mach()->ideal_Opcode() == Op_StoreCM ) {
          // Check the precedence edges
          for (uint prec = n->req(); prec < n->len(); prec++) {
            Node* oop_store = n->in(prec);
            if (oop_store != NULL) {
              assert(cfg->get_block_for_node(oop_store)->_dom_depth <= this->_dom_depth, "oop_store must dominate card-mark");
            }
          }
        }
      }
#endif

      // A few node types require changing a required edge to a precedence edge
      // before allocation.
      if( n->is_Mach() && n->req() > TypeFunc::Parms &&
          (n->as_Mach()->ideal_Opcode() == Op_MemBarAcquire ||
           n->as_Mach()->ideal_Opcode() == Op_MemBarVolatile) ) {
        // MemBarAcquire could be created without Precedent edge.
        // del_req() replaces the specified edge with the last input edge
        // and then removes the last edge. If the specified edge > number of
        // edges the last edge will be moved outside of the input edges array
        // and the edge will be lost. This is why this code should be
        // executed only when Precedent (== TypeFunc::Parms) edge is present.
        Node *x = n->in(TypeFunc::Parms);
        n->del_req(TypeFunc::Parms);
        n->add_prec(x);
      }
    }
  }
  for(uint i2=i; i2<_nodes.size(); i2++ ) // Trailing guys get zapped count
    ready_cnt.at_put(_nodes[i2]->_idx, 0);

  // All the prescheduled guys do not hold back internal nodes
  uint i3;
  for(i3 = 0; i3<phi_cnt; i3++ ) {  // For all pre-scheduled
    Node *n = _nodes[i3];       // Get pre-scheduled
    for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
      Node* m = n->fast_out(j);
      if (cfg->get_block_for_node(m) == this) { // Local-block user
        int m_cnt = ready_cnt.at(m->_idx)-1;
        ready_cnt.at_put(m->_idx, m_cnt);   // Fix ready count
      }
    }
  }

  Node_List delay;
  // Make a worklist
  Node_List worklist;
  for(uint i4=i3; i4<node_cnt; i4++ ) {    // Put ready guys on worklist
    Node *m = _nodes[i4];
    if( !ready_cnt.at(m->_idx) ) {   // Zero ready count?
      if (m->is_iteratively_computed()) {
        // Push induction variable increments last to allow other uses
        // of the phi to be scheduled first. The select() method breaks
        // ties in scheduling by worklist order.
        delay.push(m);
      } else if (m->is_Mach() && m->as_Mach()->ideal_Opcode() == Op_CreateEx) {
        // Force the CreateEx to the top of the list so it's processed
        // first and ends up at the start of the block.
        worklist.insert(0, m);
      } else {
        worklist.push(m);         // Then on to worklist!
      }
    }
  }
  while (delay.size()) {
    Node* d = delay.pop();
    worklist.push(d);
  }

  // Warm up the 'next_call' heuristic bits
  needed_for_next_call(_nodes[0], next_call, cfg);

#ifndef PRODUCT
    if (cfg->trace_opto_pipelining()) {
      for (uint j=0; j<_nodes.size(); j++) {
        Node     *n = _nodes[j];
        int     idx = n->_idx;
        tty->print("#   ready cnt:%3d  ", ready_cnt.at(idx));
        tty->print("latency:%3d  ", cfg->get_latency_for_node(n));
        tty->print("%4d: %s\n", idx, n->Name());
      }
    }
#endif

  uint max_idx = (uint)ready_cnt.length();
  // Pull from worklist and schedule
  while( worklist.size() ) {    // Worklist is not ready

#ifndef PRODUCT
    if (cfg->trace_opto_pipelining()) {
      tty->print("#   ready list:");
      for( uint i=0; i<worklist.size(); i++ ) { // Inspect entire worklist
        Node *n = worklist[i];      // Get Node on worklist
        tty->print(" %d", n->_idx);
      }
      tty->cr();
    }
#endif

    // Select and pop a ready guy from worklist
    Node* n = select(cfg, worklist, ready_cnt, next_call, phi_cnt);
    _nodes.map(phi_cnt++,n);    // Schedule him next

#ifndef PRODUCT
    if (cfg->trace_opto_pipelining()) {
      tty->print("#    select %d: %s", n->_idx, n->Name());
      tty->print(", latency:%d", cfg->get_latency_for_node(n));
      n->dump();
      if (Verbose) {
        tty->print("#   ready list:");
        for( uint i=0; i<worklist.size(); i++ ) { // Inspect entire worklist
          Node *n = worklist[i];      // Get Node on worklist
          tty->print(" %d", n->_idx);
        }
        tty->cr();
      }
    }

#endif
    if( n->is_MachCall() ) {
      MachCallNode *mcall = n->as_MachCall();
      phi_cnt = sched_call(matcher, cfg, phi_cnt, worklist, ready_cnt, mcall, next_call);
      continue;
    }

    if (n->is_Mach() && n->as_Mach()->has_call()) {
      RegMask regs;
      regs.Insert(matcher.c_frame_pointer());
      regs.OR(n->out_RegMask());

      MachProjNode *proj = new (matcher.C) MachProjNode( n, 1, RegMask::Empty, MachProjNode::fat_proj );
      cfg->map_node_to_block(proj, this);
      _nodes.insert(phi_cnt++, proj);

      add_call_kills(proj, regs, matcher._c_reg_save_policy, false);
    }

    // Children are now all ready
    for (DUIterator_Fast i5max, i5 = n->fast_outs(i5max); i5 < i5max; i5++) {
      Node* m = n->fast_out(i5); // Get user
      if (cfg->get_block_for_node(m) != this) {
        continue;
      }
      if( m->is_Phi() ) continue;
      if (m->_idx >= max_idx) { // new node, skip it
        assert(m->is_MachProj() && n->is_Mach() && n->as_Mach()->has_call(), "unexpected node types");
        continue;
      }
      int m_cnt = ready_cnt.at(m->_idx)-1;
      ready_cnt.at_put(m->_idx, m_cnt);
      if( m_cnt == 0 )
        worklist.push(m);
    }
  }

  if( phi_cnt != end_idx() ) {
    // did not schedule all.  Retry, Bailout, or Die
    Compile* C = matcher.C;
    if (C->subsume_loads() == true && !C->failing()) {
      // Retry with subsume_loads == false
      // If this is the first failure, the sentinel string will "stick"
      // to the Compile object, and the C2Compiler will see it and retry.
      C->record_failure(C2Compiler::retry_no_subsuming_loads());
    }
    // assert( phi_cnt == end_idx(), "did not schedule all" );
    return false;
  }

#ifndef PRODUCT
  if (cfg->trace_opto_pipelining()) {
    tty->print_cr("#");
    tty->print_cr("# after schedule_local");
    for (uint i = 0;i < _nodes.size();i++) {
      tty->print("# ");
      _nodes[i]->fast_dump();
    }
    tty->cr();
  }
#endif


  return true;
}

//--------------------------catch_cleanup_fix_all_inputs-----------------------
static void catch_cleanup_fix_all_inputs(Node *use, Node *old_def, Node *new_def) {
  for (uint l = 0; l < use->len(); l++) {
    if (use->in(l) == old_def) {
      if (l < use->req()) {
        use->set_req(l, new_def);
      } else {
        use->rm_prec(l);
        use->add_prec(new_def);
        l--;
      }
    }
  }
}

//------------------------------catch_cleanup_find_cloned_def------------------
static Node *catch_cleanup_find_cloned_def(Block *use_blk, Node *def, Block *def_blk, PhaseCFG* cfg, int n_clone_idx) {
  assert( use_blk != def_blk, "Inter-block cleanup only");

  // The use is some block below the Catch.  Find and return the clone of the def
  // that dominates the use. If there is no clone in a dominating block, then
  // create a phi for the def in a dominating block.

  // Find which successor block dominates this use.  The successor
  // blocks must all be single-entry (from the Catch only; I will have
  // split blocks to make this so), hence they all dominate.
  while( use_blk->_dom_depth > def_blk->_dom_depth+1 )
    use_blk = use_blk->_idom;

  // Find the successor
  Node *fixup = NULL;

  uint j;
  for( j = 0; j < def_blk->_num_succs; j++ )
    if( use_blk == def_blk->_succs[j] )
      break;

  if( j == def_blk->_num_succs ) {
    // Block at same level in dom-tree is not a successor.  It needs a
    // PhiNode, the PhiNode uses from the def and IT's uses need fixup.
    Node_Array inputs = new Node_List(Thread::current()->resource_area());
    for(uint k = 1; k < use_blk->num_preds(); k++) {
      Block* block = cfg->get_block_for_node(use_blk->pred(k));
      inputs.map(k, catch_cleanup_find_cloned_def(block, def, def_blk, cfg, n_clone_idx));
    }

    // Check to see if the use_blk already has an identical phi inserted.
    // If it exists, it will be at the first position since all uses of a
    // def are processed together.
    Node *phi = use_blk->_nodes[1];
    if( phi->is_Phi() ) {
      fixup = phi;
      for (uint k = 1; k < use_blk->num_preds(); k++) {
        if (phi->in(k) != inputs[k]) {
          // Not a match
          fixup = NULL;
          break;
        }
      }
    }

    // If an existing PhiNode was not found, make a new one.
    if (fixup == NULL) {
      Node *new_phi = PhiNode::make(use_blk->head(), def);
      use_blk->_nodes.insert(1, new_phi);
      cfg->map_node_to_block(new_phi, use_blk);
      for (uint k = 1; k < use_blk->num_preds(); k++) {
        new_phi->set_req(k, inputs[k]);
      }
      fixup = new_phi;
    }

  } else {
    // Found the use just below the Catch.  Make it use the clone.
    fixup = use_blk->_nodes[n_clone_idx];
  }

  return fixup;
}

//--------------------------catch_cleanup_intra_block--------------------------
// Fix all input edges in use that reference "def".  The use is in the same
// block as the def and both have been cloned in each successor block.
static void catch_cleanup_intra_block(Node *use, Node *def, Block *blk, int beg, int n_clone_idx) {

  // Both the use and def have been cloned. For each successor block,
  // get the clone of the use, and make its input the clone of the def
  // found in that block.

  uint use_idx = blk->find_node(use);
  uint offset_idx = use_idx - beg;
  for( uint k = 0; k < blk->_num_succs; k++ ) {
    // Get clone in each successor block
    Block *sb = blk->_succs[k];
    Node *clone = sb->_nodes[offset_idx+1];
    assert( clone->Opcode() == use->Opcode(), "" );

    // Make use-clone reference the def-clone
    catch_cleanup_fix_all_inputs(clone, def, sb->_nodes[n_clone_idx]);
  }
}

//------------------------------catch_cleanup_inter_block---------------------
// Fix all input edges in use that reference "def".  The use is in a different
// block than the def.
static void catch_cleanup_inter_block(Node *use, Block *use_blk, Node *def, Block *def_blk, PhaseCFG* cfg, int n_clone_idx) {
  if( !use_blk ) return;        // Can happen if the use is a precedence edge

  Node *new_def = catch_cleanup_find_cloned_def(use_blk, def, def_blk, cfg, n_clone_idx);
  catch_cleanup_fix_all_inputs(use, def, new_def);
}

//------------------------------call_catch_cleanup-----------------------------
// If we inserted any instructions between a Call and his CatchNode,
// clone the instructions on all paths below the Catch.
void Block::call_catch_cleanup(PhaseCFG* cfg, Compile* C) {

  // End of region to clone
  uint end = end_idx();
  if( !_nodes[end]->is_Catch() ) return;
  // Start of region to clone
  uint beg = end;
  while(!_nodes[beg-1]->is_MachProj() ||
        !_nodes[beg-1]->in(0)->is_MachCall() ) {
    beg--;
    assert(beg > 0,"Catch cleanup walking beyond block boundary");
  }
  // Range of inserted instructions is [beg, end)
  if( beg == end ) return;

  // Clone along all Catch output paths.  Clone area between the 'beg' and
  // 'end' indices.
  for( uint i = 0; i < _num_succs; i++ ) {
    Block *sb = _succs[i];
    // Clone the entire area; ignoring the edge fixup for now.
    for( uint j = end; j > beg; j-- ) {
      // It is safe here to clone a node with anti_dependence
      // since clones dominate on each path.
      Node *clone = _nodes[j-1]->clone();
      sb->_nodes.insert( 1, clone );
      cfg->map_node_to_block(clone, sb);
    }
  }


  // Fixup edges.  Check the def-use info per cloned Node
  for(uint i2 = beg; i2 < end; i2++ ) {
    uint n_clone_idx = i2-beg+1; // Index of clone of n in each successor block
    Node *n = _nodes[i2];        // Node that got cloned
    // Need DU safe iterator because of edge manipulation in calls.
    Unique_Node_List *out = new Unique_Node_List(Thread::current()->resource_area());
    for (DUIterator_Fast j1max, j1 = n->fast_outs(j1max); j1 < j1max; j1++) {
      out->push(n->fast_out(j1));
    }
    uint max = out->size();
    for (uint j = 0; j < max; j++) {// For all users
      Node *use = out->pop();
      Block *buse = cfg->get_block_for_node(use);
      if( use->is_Phi() ) {
        for( uint k = 1; k < use->req(); k++ )
          if( use->in(k) == n ) {
            Block* block = cfg->get_block_for_node(buse->pred(k));
            Node *fixup = catch_cleanup_find_cloned_def(block, n, this, cfg, n_clone_idx);
            use->set_req(k, fixup);
          }
      } else {
        if (this == buse) {
          catch_cleanup_intra_block(use, n, this, beg, n_clone_idx);
        } else {
          catch_cleanup_inter_block(use, buse, n, this, cfg, n_clone_idx);
        }
      }
    } // End for all users

  } // End of for all Nodes in cloned area

  // Remove the now-dead cloned ops
  for(uint i3 = beg; i3 < end; i3++ ) {
    _nodes[beg]->disconnect_inputs(NULL, C);
    _nodes.remove(beg);
  }

  // If the successor blocks have a CreateEx node, move it back to the top
  for(uint i4 = 0; i4 < _num_succs; i4++ ) {
    Block *sb = _succs[i4];
    uint new_cnt = end - beg;
    // Remove any newly created, but dead, nodes.
    for( uint j = new_cnt; j > 0; j-- ) {
      Node *n = sb->_nodes[j];
      if (n->outcnt() == 0 &&
          (!n->is_Proj() || n->as_Proj()->in(0)->outcnt() == 1) ){
        n->disconnect_inputs(NULL, C);
        sb->_nodes.remove(j);
        new_cnt--;
      }
    }
    // If any newly created nodes remain, move the CreateEx node to the top
    if (new_cnt > 0) {
      Node *cex = sb->_nodes[1+new_cnt];
      if( cex->is_Mach() && cex->as_Mach()->ideal_Opcode() == Op_CreateEx ) {
        sb->_nodes.remove(1+new_cnt);
        sb->_nodes.insert(1,cex);
      }
    }
  }
}
