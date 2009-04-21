/*
 * Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_locknode.cpp.incl"

//=============================================================================
const RegMask &BoxLockNode::in_RegMask(uint i) const {
  return _inmask;
}

const RegMask &BoxLockNode::out_RegMask() const {
  return *Matcher::idealreg2regmask[Op_RegP];
}

uint BoxLockNode::size_of() const { return sizeof(*this); }

BoxLockNode::BoxLockNode( int slot ) : Node( Compile::current()->root() ),
                                       _slot(slot), _is_eliminated(false) {
  init_class_id(Class_BoxLock);
  init_flags(Flag_rematerialize);
  OptoReg::Name reg = OptoReg::stack2reg(_slot);
  _inmask.Insert(reg);
}

//-----------------------------hash--------------------------------------------
uint BoxLockNode::hash() const {
  return Node::hash() + _slot + (_is_eliminated ? Compile::current()->fixed_slots() : 0);
}

//------------------------------cmp--------------------------------------------
uint BoxLockNode::cmp( const Node &n ) const {
  const BoxLockNode &bn = (const BoxLockNode &)n;
  return bn._slot == _slot && bn._is_eliminated == _is_eliminated;
}

OptoReg::Name BoxLockNode::stack_slot(Node* box_node) {
  // Chase down the BoxNode
  while (!box_node->is_BoxLock()) {
    //    if (box_node->is_SpillCopy()) {
    //      Node *m = box_node->in(1);
    //      if (m->is_Mach() && m->as_Mach()->ideal_Opcode() == Op_StoreP) {
    //        box_node = m->in(m->as_Mach()->operand_index(2));
    //        continue;
    //      }
    //    }
    assert(box_node->is_SpillCopy() || box_node->is_Phi(), "Bad spill of Lock.");
    box_node = box_node->in(1);
  }
  return box_node->in_RegMask(0).find_first_elem();
}

//=============================================================================
//-----------------------------hash--------------------------------------------
uint FastLockNode::hash() const { return NO_HASH; }

//------------------------------cmp--------------------------------------------
uint FastLockNode::cmp( const Node &n ) const {
  return (&n == this);                // Always fail except on self
}

//=============================================================================
//-----------------------------hash--------------------------------------------
uint FastUnlockNode::hash() const { return NO_HASH; }

//------------------------------cmp--------------------------------------------
uint FastUnlockNode::cmp( const Node &n ) const {
  return (&n == this);                // Always fail except on self
}

//
// Create a counter which counts the number of times this lock is acquired
//
void FastLockNode::create_lock_counter(JVMState* state) {
  BiasedLockingNamedCounter* blnc = (BiasedLockingNamedCounter*)
           OptoRuntime::new_named_counter(state, NamedCounter::BiasedLockingCounter);
  _counters = blnc->counters();
}

//=============================================================================
//------------------------------do_monitor_enter-------------------------------
void Parse::do_monitor_enter() {
  kill_dead_locals();

  // Null check; get casted pointer.
  Node *obj = do_null_check(peek(), T_OBJECT);
  // Check for locking null object
  if (stopped()) return;

  // the monitor object is not part of debug info expression stack
  pop();

  // Insert a FastLockNode which takes as arguments the current thread pointer,
  // the obj pointer & the address of the stack slot pair used for the lock.
  shared_lock(obj);
}

//------------------------------do_monitor_exit--------------------------------
void Parse::do_monitor_exit() {
  kill_dead_locals();

  pop();                        // Pop oop to unlock
  // Because monitors are guaranteed paired (else we bail out), we know
  // the matching Lock for this Unlock.  Hence we know there is no need
  // for a null check on Unlock.
  shared_unlock(map()->peek_monitor_box(), map()->peek_monitor_obj());
}
