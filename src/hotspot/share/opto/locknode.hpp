/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_LOCKNODE_HPP
#define SHARE_OPTO_LOCKNODE_HPP

#include "opto/node.hpp"
#include "opto/opcodes.hpp"
#include "opto/subnode.hpp"

class RTMLockingCounters;

//------------------------------BoxLockNode------------------------------------
class BoxLockNode : public Node {
private:
  const int     _slot; // stack slot
  RegMask     _inmask; // OptoReg corresponding to stack slot
  enum {
    Regular = 0,       // Normal locking region
    Local,             // EA found that local not escaping object is used for locking
    Nested,            // This region is inside other region which use the same object
    Coarsened,         // Some lock/unlock in region were marked as coarsened
    Unbalanced,        // This region become unbalanced after coarsened lock/unlock were eliminated
                       // or it is locking region from OSR when locking is done in Interpreter
    Eliminated         // All lock/unlock in region were eliminated
  } _kind;

#ifdef ASSERT
  const char* _kind_name[6] = {
   "Regular",
   "Local",
   "Nested",
   "Coarsened",
   "Unbalanced",
   "Eliminated"
  };
#endif

  // Allowed transitions of _kind:
  //   Regular -> Local, Nested, Coarsened
  //   Local   -> Eliminated
  //   Nested  -> Eliminated
  //   Coarsened -> Local, Nested, Unbalanced
  // EA and nested lock elimination can overwrite Coarsened kind.
  // Also allow transition to the same kind.

public:
  BoxLockNode( int lock );
  virtual int Opcode() const;
  virtual void emit(CodeBuffer &cbuf, PhaseRegAlloc *ra_) const;
  virtual uint size(PhaseRegAlloc *ra_) const;
  virtual const RegMask &in_RegMask(uint) const;
  virtual const RegMask &out_RegMask() const;
  virtual uint size_of() const;
  virtual uint hash() const;
  virtual bool cmp( const Node &n ) const;
  virtual const class Type *bottom_type() const { return TypeRawPtr::BOTTOM; }
  virtual uint ideal_reg() const { return Op_RegP; }
  virtual Node* Identity(PhaseGVN* phase);

  static OptoReg::Name reg(Node* box_node);
  static BoxLockNode* box_node(Node* box_node);
  static bool same_slot(Node* box1, Node* box2) {
    return box1->as_BoxLock()->_slot == box2->as_BoxLock()->_slot;
  }
  int stack_slot() const { return _slot; }

  bool is_regular()    const { return _kind == Regular; }
  bool is_local()      const { return _kind == Local; }
  bool is_nested()     const { return _kind == Nested; }
  bool is_coarsened()  const { return _kind == Coarsened; }
  bool is_eliminated() const { return _kind == Eliminated; }
  bool is_unbalanced() const { return _kind == Unbalanced; }

  void set_local()      {
    assert((_kind == Regular || _kind == Local || _kind == Coarsened),
           "incorrect kind for Local transitioni: %s", _kind_name[(int)_kind]);
    _kind = Local;
  }
  void set_nested()     {
    assert((_kind == Regular || _kind == Nested || _kind == Coarsened),
           "incorrect kind for Nested transition: %s", _kind_name[(int)_kind]);
    _kind = Nested;
  }
  void set_coarsened()  {
    assert((_kind == Regular || _kind == Coarsened),
           "incorrect kind for Coarsened transition: %s", _kind_name[(int)_kind]);
    _kind = Coarsened;
  }
  void set_eliminated() {
    assert((_kind == Local || _kind == Nested),
           "incorrect kind for Eliminated transition: %s", _kind_name[(int)_kind]);
    _kind = Eliminated;
  }
  void set_unbalanced() {
    assert((_kind == Coarsened || _kind == Unbalanced),
           "incorrect kind for Unbalanced transition: %s", _kind_name[(int)_kind]);
    _kind = Unbalanced;
  }

  // Is BoxLock node used for one simple lock region?
  bool is_simple_lock_region(LockNode** unique_lock, Node* obj, Node** bad_lock);

#ifndef PRODUCT
  virtual void format( PhaseRegAlloc *, outputStream *st ) const;
  virtual void dump_spec(outputStream *st) const { st->print("  Lock %d",_slot); }
#endif
};

//------------------------------FastLockNode-----------------------------------
class FastLockNode: public CmpNode {
private:
  RTMLockingCounters*       _rtm_counters; // RTM lock counters for inflated locks
  RTMLockingCounters* _stack_rtm_counters; // RTM lock counters for stack locks

public:
  FastLockNode(Node *ctrl, Node *oop, Node *box) : CmpNode(oop,box) {
    init_req(0,ctrl);
    init_class_id(Class_FastLock);
    _rtm_counters = nullptr;
    _stack_rtm_counters = nullptr;
  }
  Node* obj_node() const { return in(1); }
  Node* box_node() const { return in(2); }
  void  set_box_node(Node* box) { set_req(2, box); }

  // FastLock and FastUnlockNode do not hash, we need one for each corresponding
  // LockNode/UnLockNode to avoid creating Phi's.
  virtual uint hash() const ;                  // { return NO_HASH; }
  virtual uint size_of() const;
  virtual bool cmp( const Node &n ) const ;    // Always fail, except on self
  virtual int Opcode() const;
  virtual const Type* Value(PhaseGVN* phase) const { return TypeInt::CC; }
  const Type *sub(const Type *t1, const Type *t2) const { return TypeInt::CC;}

  void create_rtm_lock_counter(JVMState* state);
  RTMLockingCounters*       rtm_counters() const { return _rtm_counters; }
  RTMLockingCounters* stack_rtm_counters() const { return _stack_rtm_counters; }
};


//------------------------------FastUnlockNode---------------------------------
class FastUnlockNode: public CmpNode {
public:
  FastUnlockNode(Node *ctrl, Node *oop, Node *box) : CmpNode(oop,box) {
    init_req(0,ctrl);
    init_class_id(Class_FastUnlock);
  }
  Node* obj_node() const { return in(1); }
  Node* box_node() const { return in(2); }


  // FastLock and FastUnlockNode do not hash, we need one for each corresponding
  // LockNode/UnLockNode to avoid creating Phi's.
  virtual uint hash() const ;                  // { return NO_HASH; }
  virtual bool cmp( const Node &n ) const ;    // Always fail, except on self
  virtual int Opcode() const;
  virtual const Type* Value(PhaseGVN* phase) const { return TypeInt::CC; }
  const Type *sub(const Type *t1, const Type *t2) const { return TypeInt::CC;}

};

#endif // SHARE_OPTO_LOCKNODE_HPP
