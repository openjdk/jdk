/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/callnode.hpp"
#include "opto/chaitin.hpp"
#include "opto/live.hpp"
#include "opto/machnode.hpp"


// Compute live-in/live-out.  We use a totally incremental algorithm.  The LIVE
// problem is monotonic.  The steady-state solution looks like this: pull a
// block from the worklist.  It has a set of delta's - values which are newly
// live-in from the block.  Push these to the live-out sets of all predecessor
// blocks.  At each predecessor, the new live-out values are ANDed with what is
// already live-out (extra stuff is added to the live-out sets).  Then the
// remaining new live-out values are ANDed with what is locally defined.
// Leftover bits become the new live-in for the predecessor block, and the pred
// block is put on the worklist.
//   The locally live-in stuff is computed once and added to predecessor
// live-out sets.  This separate compilation is done in the outer loop below.
PhaseLive::PhaseLive(const PhaseCFG &cfg, const LRG_List &names, Arena *arena, bool keep_deltas)
  : Phase(LIVE),
  _cfg(cfg),
  _names(names),
  _arena(arena),
  _live(0),
  _livein(0),
  _keep_deltas(keep_deltas) {
}

void PhaseLive::compute(uint maxlrg) {
  _maxlrg   = maxlrg;
  _worklist = new (_arena) Block_List();

  // Init the sparse live arrays.  This data is live on exit from here!
  // The _live info is the live-out info.
  _live = (IndexSet*)_arena->Amalloc(sizeof(IndexSet) * _cfg.number_of_blocks());
  uint i;
  for (i = 0; i < _cfg.number_of_blocks(); i++) {
    _live[i].initialize(_maxlrg);
  }

  if (_keep_deltas) {
    _livein = (IndexSet*)_arena->Amalloc(sizeof(IndexSet) * _cfg.number_of_blocks());
    for (i = 0; i < _cfg.number_of_blocks(); i++) {
      _livein[i].initialize(_maxlrg);
    }
  }

  // Init the sparse arrays for delta-sets.
  ResourceMark rm;              // Nuke temp storage on exit

  // Does the memory used by _defs and _deltas get reclaimed?  Does it matter?  TT

  // Array of values defined locally in blocks
  _defs = NEW_RESOURCE_ARRAY(IndexSet,_cfg.number_of_blocks());
  for (i = 0; i < _cfg.number_of_blocks(); i++) {
    _defs[i].initialize(_maxlrg);
  }

  // Array of delta-set pointers, indexed by block pre_order-1.
  _deltas = NEW_RESOURCE_ARRAY(IndexSet*,_cfg.number_of_blocks());
  memset( _deltas, 0, sizeof(IndexSet*)* _cfg.number_of_blocks());

  _free_IndexSet = NULL;

  // Blocks having done pass-1
  VectorSet first_pass(Thread::current()->resource_area());

  // Outer loop: must compute local live-in sets and push into predecessors.
  for (uint j = _cfg.number_of_blocks(); j > 0; j--) {
    Block* block = _cfg.get_block(j - 1);

    // Compute the local live-in set.  Start with any new live-out bits.
    IndexSet* use = getset(block);
    IndexSet* def = &_defs[block->_pre_order-1];
    DEBUG_ONLY(IndexSet *def_outside = getfreeset();)
    uint i;
    for (i = block->number_of_nodes(); i > 1; i--) {
      Node* n = block->get_node(i-1);
      if (n->is_Phi()) {
        break;
      }

      uint r = _names.at(n->_idx);
      assert(!def_outside->member(r), "Use of external LRG overlaps the same LRG defined in this block");
      def->insert( r );
      use->remove( r );
      uint cnt = n->req();
      for (uint k = 1; k < cnt; k++) {
        Node *nk = n->in(k);
        uint nkidx = nk->_idx;
        if (_cfg.get_block_for_node(nk) != block) {
          uint u = _names.at(nkidx);
          use->insert(u);
          DEBUG_ONLY(def_outside->insert(u);)
        }
      }
    }
#ifdef ASSERT
    def_outside->set_next(_free_IndexSet);
    _free_IndexSet = def_outside;     // Drop onto free list
#endif
    // Remove anything defined by Phis and the block start instruction
    for (uint k = i; k > 0; k--) {
      uint r = _names.at(block->get_node(k - 1)->_idx);
      def->insert(r);
      use->remove(r);
    }

    // Push these live-in things to predecessors
    for (uint l = 1; l < block->num_preds(); l++) {
      Block* p = _cfg.get_block_for_node(block->pred(l));
      add_liveout(p, use, first_pass);

      // PhiNode uses go in the live-out set of prior blocks.
      for (uint k = i; k > 0; k--) {
        Node *phi = block->get_node(k - 1);
        if (l < phi->req()) {
          add_liveout(p, _names.at(phi->in(l)->_idx), first_pass);
        }
      }
    }
    freeset(block);
    first_pass.set(block->_pre_order);

    // Inner loop: blocks that picked up new live-out values to be propagated
    while (_worklist->size()) {
      Block* block = _worklist->pop();
      IndexSet *delta = getset(block);
      assert( delta->count(), "missing delta set" );

      // Add new-live-in to predecessors live-out sets
      for (uint l = 1; l < block->num_preds(); l++) {
        Block* predecessor = _cfg.get_block_for_node(block->pred(l));
        add_liveout(predecessor, delta, first_pass);
      }

      freeset(block);
    } // End of while-worklist-not-empty

  } // End of for-all-blocks-outer-loop

  // We explicitly clear all of the IndexSets which we are about to release.
  // This allows us to recycle their internal memory into IndexSet's free list.

  for (i = 0; i < _cfg.number_of_blocks(); i++) {
    _defs[i].clear();
    if (_deltas[i]) {
      // Is this always true?
      _deltas[i]->clear();
    }
  }
  IndexSet *free = _free_IndexSet;
  while (free != NULL) {
    IndexSet *temp = free;
    free = free->next();
    temp->clear();
  }

}

#ifndef PRODUCT
void PhaseLive::stats(uint iters) const {
}
#endif

// Get an IndexSet for a block.  Return existing one, if any.  Make a new
// empty one if a prior one does not exist.
IndexSet *PhaseLive::getset( Block *p ) {
  IndexSet *delta = _deltas[p->_pre_order-1];
  if( !delta )                  // Not on worklist?
    // Get a free set; flag as being on worklist
    delta = _deltas[p->_pre_order-1] = getfreeset();
  return delta;                 // Return set of new live-out items
}

// Pull from free list, or allocate.  Internal allocation on the returned set
// is always from thread local storage.
IndexSet *PhaseLive::getfreeset( ) {
  IndexSet *f = _free_IndexSet;
  if( !f ) {
    f = new IndexSet;
//    f->set_arena(Thread::current()->resource_area());
    f->initialize(_maxlrg, Thread::current()->resource_area());
  } else {
    // Pull from free list
    _free_IndexSet = f->next();
  //f->_cnt = 0;                        // Reset to empty
//    f->set_arena(Thread::current()->resource_area());
    f->initialize(_maxlrg, Thread::current()->resource_area());
  }
  return f;
}

// Free an IndexSet from a block.
void PhaseLive::freeset( Block *p ) {
  IndexSet *f = _deltas[p->_pre_order-1];
  if ( _keep_deltas ) {
    add_livein(p, f);
  }
  f->set_next(_free_IndexSet);
  _free_IndexSet = f;           // Drop onto free list
  _deltas[p->_pre_order-1] = NULL;
}

// Add a live-out value to a given blocks live-out set.  If it is new, then
// also add it to the delta set and stick the block on the worklist.
void PhaseLive::add_liveout( Block *p, uint r, VectorSet &first_pass ) {
  IndexSet *live = &_live[p->_pre_order-1];
  if( live->insert(r) ) {       // If actually inserted...
    // We extended the live-out set.  See if the value is generated locally.
    // If it is not, then we must extend the live-in set.
    if( !_defs[p->_pre_order-1].member( r ) ) {
      if( !_deltas[p->_pre_order-1] && // Not on worklist?
          first_pass.test(p->_pre_order) )
        _worklist->push(p);     // Actually go on worklist if already 1st pass
      getset(p)->insert(r);
    }
  }
}

// Add a vector of live-out values to a given blocks live-out set.
void PhaseLive::add_liveout( Block *p, IndexSet *lo, VectorSet &first_pass ) {
  IndexSet *live = &_live[p->_pre_order-1];
  IndexSet *defs = &_defs[p->_pre_order-1];
  IndexSet *on_worklist = _deltas[p->_pre_order-1];
  IndexSet *delta = on_worklist ? on_worklist : getfreeset();

  IndexSetIterator elements(lo);
  uint r;
  while ((r = elements.next()) != 0) {
    if( live->insert(r) &&      // If actually inserted...
        !defs->member( r ) )    // and not defined locally
      delta->insert(r);         // Then add to live-in set
  }

  if( delta->count() ) {                // If actually added things
    _deltas[p->_pre_order-1] = delta; // Flag as on worklist now
    if( !on_worklist &&         // Not on worklist?
        first_pass.test(p->_pre_order) )
      _worklist->push(p);       // Actually go on worklist if already 1st pass
  } else {                      // Nothing there; just free it
    delta->set_next(_free_IndexSet);
    _free_IndexSet = delta;     // Drop onto free list
  }
}

// Add a vector of live-in values to a given blocks live-in set.
void PhaseLive::add_livein(Block *p, IndexSet *lo) {
  IndexSet *livein = &_livein[p->_pre_order-1];
  IndexSetIterator elements(lo);
  uint r;
  while ((r = elements.next()) != 0) {
    livein->insert(r);         // Then add to live-in set
  }
}

#ifndef PRODUCT
// Dump the live-out set for a block
void PhaseLive::dump( const Block *b ) const {
  tty->print("Block %d: ",b->_pre_order);
  if ( _keep_deltas ) {
    tty->print("LiveIn: ");  _livein[b->_pre_order-1].dump();
  }
  tty->print("LiveOut: ");  _live[b->_pre_order-1].dump();
  uint cnt = b->number_of_nodes();
  for( uint i=0; i<cnt; i++ ) {
    tty->print("L%d/", _names.at(b->get_node(i)->_idx));
    b->get_node(i)->dump();
  }
  tty->print("\n");
}

// Verify that base pointers and derived pointers are still sane.
void PhaseChaitin::verify_base_ptrs( ResourceArea *a ) const {
#ifdef ASSERT
  Unique_Node_List worklist(a);
  for (uint i = 0; i < _cfg.number_of_blocks(); i++) {
    Block* block = _cfg.get_block(i);
    for (uint j = block->end_idx() + 1; j > 1; j--) {
      Node* n = block->get_node(j-1);
      if (n->is_Phi()) {
        break;
      }
      // Found a safepoint?
      if (n->is_MachSafePoint()) {
        MachSafePointNode *sfpt = n->as_MachSafePoint();
        JVMState* jvms = sfpt->jvms();
        if (jvms != NULL) {
          // Now scan for a live derived pointer
          if (jvms->oopoff() < sfpt->req()) {
            // Check each derived/base pair
            for (uint idx = jvms->oopoff(); idx < sfpt->req(); idx++) {
              Node *check = sfpt->in(idx);
              bool is_derived = ((idx - jvms->oopoff()) & 1) == 0;
              // search upwards through spills and spill phis for AddP
              worklist.clear();
              worklist.push(check);
              uint k = 0;
              while( k < worklist.size() ) {
                check = worklist.at(k);
                assert(check,"Bad base or derived pointer");
                // See PhaseChaitin::find_base_for_derived() for all cases.
                int isc = check->is_Copy();
                if( isc ) {
                  worklist.push(check->in(isc));
                } else if( check->is_Phi() ) {
                  for (uint m = 1; m < check->req(); m++)
                    worklist.push(check->in(m));
                } else if( check->is_Con() ) {
                  if (is_derived) {
                    // Derived is NULL+offset
                    assert(!is_derived || check->bottom_type()->is_ptr()->ptr() == TypePtr::Null,"Bad derived pointer");
                  } else {
                    assert(check->bottom_type()->is_ptr()->_offset == 0,"Bad base pointer");
                    // Base either ConP(NULL) or loadConP
                    if (check->is_Mach()) {
                      assert(check->as_Mach()->ideal_Opcode() == Op_ConP,"Bad base pointer");
                    } else {
                      assert(check->Opcode() == Op_ConP &&
                             check->bottom_type()->is_ptr()->ptr() == TypePtr::Null,"Bad base pointer");
                    }
                  }
                } else if( check->bottom_type()->is_ptr()->_offset == 0 ) {
                  if(check->is_Proj() || check->is_Mach() &&
                     (check->as_Mach()->ideal_Opcode() == Op_CreateEx ||
                      check->as_Mach()->ideal_Opcode() == Op_ThreadLocal ||
                      check->as_Mach()->ideal_Opcode() == Op_CMoveP ||
                      check->as_Mach()->ideal_Opcode() == Op_CheckCastPP ||
#ifdef _LP64
                      UseCompressedOops && check->as_Mach()->ideal_Opcode() == Op_CastPP ||
                      UseCompressedOops && check->as_Mach()->ideal_Opcode() == Op_DecodeN ||
                      UseCompressedClassPointers && check->as_Mach()->ideal_Opcode() == Op_DecodeNKlass ||
#endif
                      check->as_Mach()->ideal_Opcode() == Op_LoadP ||
                      check->as_Mach()->ideal_Opcode() == Op_LoadKlass)) {
                    // Valid nodes
                  } else {
                    check->dump();
                    assert(false,"Bad base or derived pointer");
                  }
                } else {
                  assert(is_derived,"Bad base pointer");
                  assert(check->is_Mach() && check->as_Mach()->ideal_Opcode() == Op_AddP,"Bad derived pointer");
                }
                k++;
                assert(k < 100000,"Derived pointer checking in infinite loop");
              } // End while
            }
          } // End of check for derived pointers
        } // End of Kcheck for debug info
      } // End of if found a safepoint
    } // End of forall instructions in block
  } // End of forall blocks
#endif
}

// Verify that graphs and base pointers are still sane.
void PhaseChaitin::verify( ResourceArea *a, bool verify_ifg ) const {
#ifdef ASSERT
  if( VerifyOpto || VerifyRegisterAllocator ) {
    _cfg.verify();
    verify_base_ptrs(a);
    if(verify_ifg)
      _ifg->verify(this);
  }
#endif
}

#endif
