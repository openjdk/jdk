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
#include "memory/allocation.inline.hpp"
#include "opto/addnode.hpp"
#include "opto/castnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/connode.hpp"
#include "opto/machnode.hpp"
#include "opto/movenode.hpp"
#include "opto/mulnode.hpp"
#include "opto/phaseX.hpp"
#include "opto/subnode.hpp"

// Portions of code courtesy of Clifford Click

// Classic Add functionality.  This covers all the usual 'add' behaviors for
// an algebraic ring.  Add-integer, add-float, add-double, and binary-or are
// all inherited from this class.  The various identity values are supplied
// by virtual functions.


//=============================================================================
//------------------------------hash-------------------------------------------
// Hash function over AddNodes.  Needs to be commutative; i.e., I swap
// (commute) inputs to AddNodes willy-nilly so the hash function must return
// the same value in the presence of edge swapping.
uint AddNode::hash() const {
  return (uintptr_t)in(1) + (uintptr_t)in(2) + Opcode();
}

//------------------------------Identity---------------------------------------
// If either input is a constant 0, return the other input.
Node* AddNode::Identity(PhaseGVN* phase) {
  const Type *zero = add_id();  // The additive identity
  if( phase->type( in(1) )->higher_equal( zero ) ) return in(2);
  if( phase->type( in(2) )->higher_equal( zero ) ) return in(1);
  return this;
}

//------------------------------commute----------------------------------------
// Commute operands to move loads and constants to the right.
static bool commute(PhaseGVN* phase, Node* add) {
  Node *in1 = add->in(1);
  Node *in2 = add->in(2);

  // convert "max(a,b) + min(a,b)" into "a+b".
  if ((in1->Opcode() == add->as_Add()->max_opcode() && in2->Opcode() == add->as_Add()->min_opcode())
      || (in1->Opcode() == add->as_Add()->min_opcode() && in2->Opcode() == add->as_Add()->max_opcode())) {
    Node *in11 = in1->in(1);
    Node *in12 = in1->in(2);

    Node *in21 = in2->in(1);
    Node *in22 = in2->in(2);

    if ((in11 == in21 && in12 == in22) ||
        (in11 == in22 && in12 == in21)) {
      add->set_req_X(1, in11, phase);
      add->set_req_X(2, in12, phase);
      return true;
    }
  }

  bool con_left = phase->type(in1)->singleton();
  bool con_right = phase->type(in2)->singleton();

  // Convert "1+x" into "x+1".
  // Right is a constant; leave it
  if( con_right ) return false;
  // Left is a constant; move it right.
  if( con_left ) {
    add->swap_edges(1, 2);
    return true;
  }

  // Convert "Load+x" into "x+Load".
  // Now check for loads
  if (in2->is_Load()) {
    if (!in1->is_Load()) {
      // already x+Load to return
      return false;
    }
    // both are loads, so fall through to sort inputs by idx
  } else if( in1->is_Load() ) {
    // Left is a Load and Right is not; move it right.
    add->swap_edges(1, 2);
    return true;
  }

  PhiNode *phi;
  // Check for tight loop increments: Loop-phi of Add of loop-phi
  if (in1->is_Phi() && (phi = in1->as_Phi()) && phi->region()->is_Loop() && phi->in(2) == add)
    return false;
  if (in2->is_Phi() && (phi = in2->as_Phi()) && phi->region()->is_Loop() && phi->in(2) == add) {
    add->swap_edges(1, 2);
    return true;
  }

  // Otherwise, sort inputs (commutativity) to help value numbering.
  if( in1->_idx > in2->_idx ) {
    add->swap_edges(1, 2);
    return true;
  }
  return false;
}

//------------------------------Idealize---------------------------------------
// If we get here, we assume we are associative!
Node *AddNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  const Type *t1 = phase->type(in(1));
  const Type *t2 = phase->type(in(2));
  bool con_left  = t1->singleton();
  bool con_right = t2->singleton();

  // Check for commutative operation desired
  if (commute(phase, this)) return this;

  AddNode *progress = nullptr;             // Progress flag

  // Convert "(x+1)+2" into "x+(1+2)".  If the right input is a
  // constant, and the left input is an add of a constant, flatten the
  // expression tree.
  Node *add1 = in(1);
  Node *add2 = in(2);
  int add1_op = add1->Opcode();
  int this_op = Opcode();
  if (con_right && t2 != Type::TOP && // Right input is a constant?
      add1_op == this_op) { // Left input is an Add?

    // Type of left _in right input
    const Type *t12 = phase->type(add1->in(2));
    if (t12->singleton() && t12 != Type::TOP) { // Left input is an add of a constant?
      // Check for rare case of closed data cycle which can happen inside
      // unreachable loops. In these cases the computation is undefined.
#ifdef ASSERT
      Node *add11    = add1->in(1);
      int   add11_op = add11->Opcode();
      if ((add1 == add1->in(1))
          || (add11_op == this_op && add11->in(1) == add1)) {
        assert(false, "dead loop in AddNode::Ideal");
      }
#endif
      // The Add of the flattened expression
      Node *x1 = add1->in(1);
      Node *x2 = phase->makecon(add1->as_Add()->add_ring(t2, t12));
      set_req_X(2, x2, phase);
      set_req_X(1, x1, phase);
      progress = this;            // Made progress
      add1 = in(1);
      add1_op = add1->Opcode();
    }
  }

  // Convert "(x+1)+y" into "(x+y)+1".  Push constants down the expression tree.
  if (add1_op == this_op && !con_right) {
    Node *a12 = add1->in(2);
    const Type *t12 = phase->type( a12 );
    if (t12->singleton() && t12 != Type::TOP && (add1 != add1->in(1)) &&
        !(add1->in(1)->is_Phi() && (add1->in(1)->as_Phi()->is_tripcount(T_INT) || add1->in(1)->as_Phi()->is_tripcount(T_LONG)))) {
      assert(add1->in(1) != this, "dead loop in AddNode::Ideal");
      add2 = add1->clone();
      add2->set_req(2, in(2));
      add2 = phase->transform(add2);
      set_req_X(1, add2, phase);
      set_req_X(2, a12, phase);
      progress = this;
      add2 = a12;
    }
  }

  // Convert "x+(y+1)" into "(x+y)+1".  Push constants down the expression tree.
  int add2_op = add2->Opcode();
  if (add2_op == this_op && !con_left) {
    Node *a22 = add2->in(2);
    const Type *t22 = phase->type( a22 );
    if (t22->singleton() && t22 != Type::TOP && (add2 != add2->in(1)) &&
        !(add2->in(1)->is_Phi() && (add2->in(1)->as_Phi()->is_tripcount(T_INT) || add2->in(1)->as_Phi()->is_tripcount(T_LONG)))) {
      assert(add2->in(1) != this, "dead loop in AddNode::Ideal");
      Node *addx = add2->clone();
      addx->set_req(1, in(1));
      addx->set_req(2, add2->in(1));
      addx = phase->transform(addx);
      set_req_X(1, addx, phase);
      set_req_X(2, a22, phase);
      progress = this;
    }
  }

  return progress;
}

//------------------------------Value-----------------------------------------
// An add node sums it's two _in.  If one input is an RSD, we must mixin
// the other input's symbols.
const Type* AddNode::Value(PhaseGVN* phase) const {
  // Either input is TOP ==> the result is TOP
  const Type* t1 = phase->type(in(1));
  const Type* t2 = phase->type(in(2));
  if (t1 == Type::TOP || t2 == Type::TOP) {
    return Type::TOP;
  }

  // Check for an addition involving the additive identity
  const Type* tadd = add_of_identity(t1, t2);
  if (tadd != nullptr) {
    return tadd;
  }

  return add_ring(t1, t2);               // Local flavor of type addition
}

//------------------------------add_identity-----------------------------------
// Check for addition of the identity
const Type *AddNode::add_of_identity( const Type *t1, const Type *t2 ) const {
  const Type *zero = add_id();  // The additive identity
  if( t1->higher_equal( zero ) ) return t2;
  if( t2->higher_equal( zero ) ) return t1;

  return nullptr;
}

AddNode* AddNode::make(Node* in1, Node* in2, BasicType bt) {
  switch (bt) {
    case T_INT:
      return new AddINode(in1, in2);
    case T_LONG:
      return new AddLNode(in1, in2);
    default:
      fatal("Not implemented for %s", type2name(bt));
  }
  return nullptr;
}

//=============================================================================
//------------------------------Idealize---------------------------------------
Node* AddNode::IdealIL(PhaseGVN* phase, bool can_reshape, BasicType bt) {
  Node* in1 = in(1);
  Node* in2 = in(2);
  int op1 = in1->Opcode();
  int op2 = in2->Opcode();
  // Fold (con1-x)+con2 into (con1+con2)-x
  if (op1 == Op_Add(bt) && op2 == Op_Sub(bt)) {
    // Swap edges to try optimizations below
    in1 = in2;
    in2 = in(1);
    op1 = op2;
    op2 = in2->Opcode();
  }
  if (op1 == Op_Sub(bt)) {
    const Type* t_sub1 = phase->type(in1->in(1));
    const Type* t_2    = phase->type(in2       );
    if (t_sub1->singleton() && t_2->singleton() && t_sub1 != Type::TOP && t_2 != Type::TOP) {
      return SubNode::make(phase->makecon(add_ring(t_sub1, t_2)), in1->in(2), bt);
    }
    // Convert "(a-b)+(c-d)" into "(a+c)-(b+d)"
    if (op2 == Op_Sub(bt)) {
      // Check for dead cycle: d = (a-b)+(c-d)
      assert( in1->in(2) != this && in2->in(2) != this,
              "dead loop in AddINode::Ideal" );
      Node* sub = SubNode::make(nullptr, nullptr, bt);
      Node* sub_in1;
      PhaseIterGVN* igvn = phase->is_IterGVN();
      // During IGVN, if both inputs of the new AddNode are a tree of SubNodes, this same transformation will be applied
      // to every node of the tree. Calling transform() causes the transformation to be applied recursively, once per
      // tree node whether some subtrees are identical or not. Pushing to the IGVN worklist instead, causes the transform
      // to be applied once per unique subtrees (because all uses of a subtree are updated with the result of the
      // transformation). In case of a large tree, this can make a difference in compilation time.
      if (igvn != nullptr) {
        sub_in1 = igvn->register_new_node_with_optimizer(AddNode::make(in1->in(1), in2->in(1), bt));
      } else {
        sub_in1 = phase->transform(AddNode::make(in1->in(1), in2->in(1), bt));
      }
      Node* sub_in2;
      if (igvn != nullptr) {
        sub_in2 = igvn->register_new_node_with_optimizer(AddNode::make(in1->in(2), in2->in(2), bt));
      } else {
        sub_in2 = phase->transform(AddNode::make(in1->in(2), in2->in(2), bt));
      }
      sub->init_req(1, sub_in1);
      sub->init_req(2, sub_in2);
      return sub;
    }
    // Convert "(a-b)+(b+c)" into "(a+c)"
    if (op2 == Op_Add(bt) && in1->in(2) == in2->in(1)) {
      assert(in1->in(1) != this && in2->in(2) != this,"dead loop in AddINode::Ideal/AddLNode::Ideal");
      return AddNode::make(in1->in(1), in2->in(2), bt);
    }
    // Convert "(a-b)+(c+b)" into "(a+c)"
    if (op2 == Op_Add(bt) && in1->in(2) == in2->in(2)) {
      assert(in1->in(1) != this && in2->in(1) != this,"dead loop in AddINode::Ideal/AddLNode::Ideal");
      return AddNode::make(in1->in(1), in2->in(1), bt);
    }
  }

  // Convert (con - y) + x into "(x - y) + con"
  if (op1 == Op_Sub(bt) && in1->in(1)->Opcode() == Op_ConIL(bt)
      && in1 != in1->in(2) && !(in1->in(2)->is_Phi() && in1->in(2)->as_Phi()->is_tripcount(bt))) {
    return AddNode::make(phase->transform(SubNode::make(in2, in1->in(2), bt)), in1->in(1), bt);
  }

  // Convert x + (con - y) into "(x - y) + con"
  if (op2 == Op_Sub(bt) && in2->in(1)->Opcode() == Op_ConIL(bt)
      && in2 != in2->in(2) && !(in2->in(2)->is_Phi() && in2->in(2)->as_Phi()->is_tripcount(bt))) {
    return AddNode::make(phase->transform(SubNode::make(in1, in2->in(2), bt)), in2->in(1), bt);
  }

  // Associative
  if (op1 == Op_Mul(bt) && op2 == Op_Mul(bt)) {
    Node* add_in1 = nullptr;
    Node* add_in2 = nullptr;
    Node* mul_in = nullptr;

    if (in1->in(1) == in2->in(1)) {
      // Convert "a*b+a*c into a*(b+c)
      add_in1 = in1->in(2);
      add_in2 = in2->in(2);
      mul_in = in1->in(1);
    } else if (in1->in(2) == in2->in(1)) {
      // Convert a*b+b*c into b*(a+c)
      add_in1 = in1->in(1);
      add_in2 = in2->in(2);
      mul_in = in1->in(2);
    } else if (in1->in(2) == in2->in(2)) {
      // Convert a*c+b*c into (a+b)*c
      add_in1 = in1->in(1);
      add_in2 = in2->in(1);
      mul_in = in1->in(2);
    } else if (in1->in(1) == in2->in(2)) {
      // Convert a*b+c*a into a*(b+c)
      add_in1 = in1->in(2);
      add_in2 = in2->in(1);
      mul_in = in1->in(1);
    }

    if (mul_in != nullptr) {
      Node* add = phase->transform(AddNode::make(add_in1, add_in2, bt));
      return MulNode::make(mul_in, add, bt);
    }
  }

  // Convert (x >>> rshift) + (x << lshift) into RotateRight(x, rshift)
  if (Matcher::match_rule_supported(Op_RotateRight) &&
      ((op1 == Op_URShift(bt) && op2 == Op_LShift(bt)) || (op1 == Op_LShift(bt) && op2 == Op_URShift(bt))) &&
      in1->in(1) != nullptr && in1->in(1) == in2->in(1)) {
    Node* rshift = op1 == Op_URShift(bt) ? in1->in(2) : in2->in(2);
    Node* lshift = op1 == Op_URShift(bt) ? in2->in(2) : in1->in(2);
    if (rshift != nullptr && lshift != nullptr) {
      const TypeInt* rshift_t = phase->type(rshift)->isa_int();
      const TypeInt* lshift_t = phase->type(lshift)->isa_int();
      int bits = bt == T_INT ? 32 : 64;
      int mask = bt == T_INT ? 0x1F : 0x3F;
      if (lshift_t != nullptr && lshift_t->is_con() &&
          rshift_t != nullptr && rshift_t->is_con() &&
          ((lshift_t->get_con() & mask) == (bits - (rshift_t->get_con() & mask)))) {
        return new RotateRightNode(in1->in(1), phase->intcon(rshift_t->get_con() & mask), TypeInteger::bottom(bt));
      }
    }
  }

  return AddNode::Ideal(phase, can_reshape);
}


Node* AddINode::Ideal(PhaseGVN* phase, bool can_reshape) {
  Node* in1 = in(1);
  Node* in2 = in(2);
  int op1 = in1->Opcode();
  int op2 = in2->Opcode();

  // Convert (x>>>z)+y into (x+(y<<z))>>>z for small constant z and y.
  // Helps with array allocation math constant folding
  // See 4790063:
  // Unrestricted transformation is unsafe for some runtime values of 'x'
  // ( x ==  0, z == 1, y == -1 ) fails
  // ( x == -5, z == 1, y ==  1 ) fails
  // Transform works for small z and small negative y when the addition
  // (x + (y << z)) does not cross zero.
  // Implement support for negative y and (x >= -(y << z))
  // Have not observed cases where type information exists to support
  // positive y and (x <= -(y << z))
  if (op1 == Op_URShiftI && op2 == Op_ConI &&
      in1->in(2)->Opcode() == Op_ConI) {
    jint z = phase->type(in1->in(2))->is_int()->get_con() & 0x1f; // only least significant 5 bits matter
    jint y = phase->type(in2)->is_int()->get_con();

    if (z < 5 && -5 < y && y < 0) {
      const Type* t_in11 = phase->type(in1->in(1));
      if( t_in11 != Type::TOP && (t_in11->is_int()->_lo >= -(y << z))) {
        Node* a = phase->transform(new AddINode( in1->in(1), phase->intcon(y<<z)));
        return new URShiftINode(a, in1->in(2));
      }
    }
  }

  return AddNode::IdealIL(phase, can_reshape, T_INT);
}


//------------------------------Identity---------------------------------------
// Fold (x-y)+y  OR  y+(x-y)  into  x
Node* AddINode::Identity(PhaseGVN* phase) {
  if (in(1)->Opcode() == Op_SubI && in(1)->in(2) == in(2)) {
    return in(1)->in(1);
  } else if (in(2)->Opcode() == Op_SubI && in(2)->in(2) == in(1)) {
    return in(2)->in(1);
  }
  return AddNode::Identity(phase);
}


//------------------------------add_ring---------------------------------------
// Supplied function returns the sum of the inputs.  Guaranteed never
// to be passed a TOP or BOTTOM type, these are filtered out by
// pre-check.
const Type *AddINode::add_ring( const Type *t0, const Type *t1 ) const {
  const TypeInt *r0 = t0->is_int(); // Handy access
  const TypeInt *r1 = t1->is_int();
  int lo = java_add(r0->_lo, r1->_lo);
  int hi = java_add(r0->_hi, r1->_hi);
  if( !(r0->is_con() && r1->is_con()) ) {
    // Not both constants, compute approximate result
    if( (r0->_lo & r1->_lo) < 0 && lo >= 0 ) {
      lo = min_jint; hi = max_jint; // Underflow on the low side
    }
    if( (~(r0->_hi | r1->_hi)) < 0 && hi < 0 ) {
      lo = min_jint; hi = max_jint; // Overflow on the high side
    }
    if( lo > hi ) {               // Handle overflow
      lo = min_jint; hi = max_jint;
    }
  } else {
    // both constants, compute precise result using 'lo' and 'hi'
    // Semantics define overflow and underflow for integer addition
    // as expected.  In particular: 0x80000000 + 0x80000000 --> 0x0
  }
  return TypeInt::make( lo, hi, MAX2(r0->_widen,r1->_widen) );
}


//=============================================================================
//------------------------------Idealize---------------------------------------
Node* AddLNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  return AddNode::IdealIL(phase, can_reshape, T_LONG);
}


//------------------------------Identity---------------------------------------
// Fold (x-y)+y  OR  y+(x-y)  into  x
Node* AddLNode::Identity(PhaseGVN* phase) {
  if (in(1)->Opcode() == Op_SubL && in(1)->in(2) == in(2)) {
    return in(1)->in(1);
  } else if (in(2)->Opcode() == Op_SubL && in(2)->in(2) == in(1)) {
    return in(2)->in(1);
  }
  return AddNode::Identity(phase);
}


//------------------------------add_ring---------------------------------------
// Supplied function returns the sum of the inputs.  Guaranteed never
// to be passed a TOP or BOTTOM type, these are filtered out by
// pre-check.
const Type *AddLNode::add_ring( const Type *t0, const Type *t1 ) const {
  const TypeLong *r0 = t0->is_long(); // Handy access
  const TypeLong *r1 = t1->is_long();
  jlong lo = java_add(r0->_lo, r1->_lo);
  jlong hi = java_add(r0->_hi, r1->_hi);
  if( !(r0->is_con() && r1->is_con()) ) {
    // Not both constants, compute approximate result
    if( (r0->_lo & r1->_lo) < 0 && lo >= 0 ) {
      lo =min_jlong; hi = max_jlong; // Underflow on the low side
    }
    if( (~(r0->_hi | r1->_hi)) < 0 && hi < 0 ) {
      lo = min_jlong; hi = max_jlong; // Overflow on the high side
    }
    if( lo > hi ) {               // Handle overflow
      lo = min_jlong; hi = max_jlong;
    }
  } else {
    // both constants, compute precise result using 'lo' and 'hi'
    // Semantics define overflow and underflow for integer addition
    // as expected.  In particular: 0x80000000 + 0x80000000 --> 0x0
  }
  return TypeLong::make( lo, hi, MAX2(r0->_widen,r1->_widen) );
}


//=============================================================================
//------------------------------add_of_identity--------------------------------
// Check for addition of the identity
const Type *AddFNode::add_of_identity( const Type *t1, const Type *t2 ) const {
  // x ADD 0  should return x unless 'x' is a -zero
  //
  // const Type *zero = add_id();     // The additive identity
  // jfloat f1 = t1->getf();
  // jfloat f2 = t2->getf();
  //
  // if( t1->higher_equal( zero ) ) return t2;
  // if( t2->higher_equal( zero ) ) return t1;

  return nullptr;
}

//------------------------------add_ring---------------------------------------
// Supplied function returns the sum of the inputs.
// This also type-checks the inputs for sanity.  Guaranteed never to
// be passed a TOP or BOTTOM type, these are filtered out by pre-check.
const Type *AddFNode::add_ring( const Type *t0, const Type *t1 ) const {
  if (!t0->isa_float_constant() || !t1->isa_float_constant()) {
    return bottom_type();
  }
  return TypeF::make( t0->getf() + t1->getf() );
}

//------------------------------Ideal------------------------------------------
Node *AddFNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Floating point additions are not associative because of boundary conditions (infinity)
  return commute(phase, this) ? this : nullptr;
}


//=============================================================================
//------------------------------add_of_identity--------------------------------
// Check for addition of the identity
const Type *AddDNode::add_of_identity( const Type *t1, const Type *t2 ) const {
  // x ADD 0  should return x unless 'x' is a -zero
  //
  // const Type *zero = add_id();     // The additive identity
  // jfloat f1 = t1->getf();
  // jfloat f2 = t2->getf();
  //
  // if( t1->higher_equal( zero ) ) return t2;
  // if( t2->higher_equal( zero ) ) return t1;

  return nullptr;
}
//------------------------------add_ring---------------------------------------
// Supplied function returns the sum of the inputs.
// This also type-checks the inputs for sanity.  Guaranteed never to
// be passed a TOP or BOTTOM type, these are filtered out by pre-check.
const Type *AddDNode::add_ring( const Type *t0, const Type *t1 ) const {
  if (!t0->isa_double_constant() || !t1->isa_double_constant()) {
    return bottom_type();
  }
  return TypeD::make( t0->getd() + t1->getd() );
}

//------------------------------Ideal------------------------------------------
Node *AddDNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Floating point additions are not associative because of boundary conditions (infinity)
  return commute(phase, this) ? this : nullptr;
}


//=============================================================================
//------------------------------Identity---------------------------------------
// If one input is a constant 0, return the other input.
Node* AddPNode::Identity(PhaseGVN* phase) {
  return ( phase->type( in(Offset) )->higher_equal( TypeX_ZERO ) ) ? in(Address) : this;
}

//------------------------------Idealize---------------------------------------
Node *AddPNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Bail out if dead inputs
  if( phase->type( in(Address) ) == Type::TOP ) return nullptr;

  // If the left input is an add of a constant, flatten the expression tree.
  const Node *n = in(Address);
  if (n->is_AddP() && n->in(Base) == in(Base)) {
    const AddPNode *addp = n->as_AddP(); // Left input is an AddP
    assert( !addp->in(Address)->is_AddP() ||
             addp->in(Address)->as_AddP() != addp,
            "dead loop in AddPNode::Ideal" );
    // Type of left input's right input
    const Type *t = phase->type( addp->in(Offset) );
    if( t == Type::TOP ) return nullptr;
    const TypeX *t12 = t->is_intptr_t();
    if( t12->is_con() ) {       // Left input is an add of a constant?
      // If the right input is a constant, combine constants
      const Type *temp_t2 = phase->type( in(Offset) );
      if( temp_t2 == Type::TOP ) return nullptr;
      const TypeX *t2 = temp_t2->is_intptr_t();
      Node* address;
      Node* offset;
      if( t2->is_con() ) {
        // The Add of the flattened expression
        address = addp->in(Address);
        offset  = phase->MakeConX(t2->get_con() + t12->get_con());
      } else {
        // Else move the constant to the right.  ((A+con)+B) into ((A+B)+con)
        address = phase->transform(new AddPNode(in(Base),addp->in(Address),in(Offset)));
        offset  = addp->in(Offset);
      }
      set_req_X(Address, address, phase);
      set_req_X(Offset, offset, phase);
      return this;
    }
  }

  // Raw pointers?
  if( in(Base)->bottom_type() == Type::TOP ) {
    // If this is a null+long form (from unsafe accesses), switch to a rawptr.
    if (phase->type(in(Address)) == TypePtr::NULL_PTR) {
      Node* offset = in(Offset);
      return new CastX2PNode(offset);
    }
  }

  // If the right is an add of a constant, push the offset down.
  // Convert: (ptr + (offset+con)) into (ptr+offset)+con.
  // The idea is to merge array_base+scaled_index groups together,
  // and only have different constant offsets from the same base.
  const Node *add = in(Offset);
  if( add->Opcode() == Op_AddX && add->in(1) != add ) {
    const Type *t22 = phase->type( add->in(2) );
    if( t22->singleton() && (t22 != Type::TOP) ) {  // Right input is an add of a constant?
      set_req(Address, phase->transform(new AddPNode(in(Base),in(Address),add->in(1))));
      set_req_X(Offset, add->in(2), phase); // puts add on igvn worklist if needed
      return this;              // Made progress
    }
  }

  return nullptr;                  // No progress
}

//------------------------------bottom_type------------------------------------
// Bottom-type is the pointer-type with unknown offset.
const Type *AddPNode::bottom_type() const {
  if (in(Address) == nullptr)  return TypePtr::BOTTOM;
  const TypePtr *tp = in(Address)->bottom_type()->isa_ptr();
  if( !tp ) return Type::TOP;   // TOP input means TOP output
  assert( in(Offset)->Opcode() != Op_ConP, "" );
  const Type *t = in(Offset)->bottom_type();
  if( t == Type::TOP )
    return tp->add_offset(Type::OffsetTop);
  const TypeX *tx = t->is_intptr_t();
  intptr_t txoffset = Type::OffsetBot;
  if (tx->is_con()) {   // Left input is an add of a constant?
    txoffset = tx->get_con();
  }
  return tp->add_offset(txoffset);
}

//------------------------------Value------------------------------------------
const Type* AddPNode::Value(PhaseGVN* phase) const {
  // Either input is TOP ==> the result is TOP
  const Type *t1 = phase->type( in(Address) );
  const Type *t2 = phase->type( in(Offset) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // Left input is a pointer
  const TypePtr *p1 = t1->isa_ptr();
  // Right input is an int
  const TypeX *p2 = t2->is_intptr_t();
  // Add 'em
  intptr_t p2offset = Type::OffsetBot;
  if (p2->is_con()) {   // Left input is an add of a constant?
    p2offset = p2->get_con();
  }
  return p1->add_offset(p2offset);
}

//------------------------Ideal_base_and_offset--------------------------------
// Split an oop pointer into a base and offset.
// (The offset might be Type::OffsetBot in the case of an array.)
// Return the base, or null if failure.
Node* AddPNode::Ideal_base_and_offset(Node* ptr, PhaseValues* phase,
                                      // second return value:
                                      intptr_t& offset) {
  if (ptr->is_AddP()) {
    Node* base = ptr->in(AddPNode::Base);
    Node* addr = ptr->in(AddPNode::Address);
    Node* offs = ptr->in(AddPNode::Offset);
    if (base == addr || base->is_top()) {
      offset = phase->find_intptr_t_con(offs, Type::OffsetBot);
      if (offset != Type::OffsetBot) {
        return addr;
      }
    }
  }
  offset = Type::OffsetBot;
  return nullptr;
}

//------------------------------unpack_offsets----------------------------------
// Collect the AddP offset values into the elements array, giving up
// if there are more than length.
int AddPNode::unpack_offsets(Node* elements[], int length) {
  int count = 0;
  Node* addr = this;
  Node* base = addr->in(AddPNode::Base);
  while (addr->is_AddP()) {
    if (addr->in(AddPNode::Base) != base) {
      // give up
      return -1;
    }
    elements[count++] = addr->in(AddPNode::Offset);
    if (count == length) {
      // give up
      return -1;
    }
    addr = addr->in(AddPNode::Address);
  }
  if (addr != base) {
    return -1;
  }
  return count;
}

//------------------------------match_edge-------------------------------------
// Do we Match on this edge index or not?  Do not match base pointer edge
uint AddPNode::match_edge(uint idx) const {
  return idx > Base;
}

//=============================================================================
//------------------------------Identity---------------------------------------
Node* OrINode::Identity(PhaseGVN* phase) {
  // x | x => x
  if (in(1) == in(2)) {
    return in(1);
  }

  return AddNode::Identity(phase);
}

// Find shift value for Integer or Long OR.
Node* rotate_shift(PhaseGVN* phase, Node* lshift, Node* rshift, int mask) {
  // val << norm_con_shift | val >> ({32|64} - norm_con_shift) => rotate_left val, norm_con_shift
  const TypeInt* lshift_t = phase->type(lshift)->isa_int();
  const TypeInt* rshift_t = phase->type(rshift)->isa_int();
  if (lshift_t != nullptr && lshift_t->is_con() &&
      rshift_t != nullptr && rshift_t->is_con() &&
      ((lshift_t->get_con() & mask) == ((mask + 1) - (rshift_t->get_con() & mask)))) {
    return phase->intcon(lshift_t->get_con() & mask);
  }
  // val << var_shift | val >> ({0|32|64} - var_shift) => rotate_left val, var_shift
  if (rshift->Opcode() == Op_SubI && rshift->in(2) == lshift && rshift->in(1)->is_Con()){
    const TypeInt* shift_t = phase->type(rshift->in(1))->isa_int();
    if (shift_t != nullptr && shift_t->is_con() &&
        (shift_t->get_con() == 0 || shift_t->get_con() == (mask + 1))) {
      return lshift;
    }
  }
  return nullptr;
}

Node* OrINode::Ideal(PhaseGVN* phase, bool can_reshape) {
  int lopcode = in(1)->Opcode();
  int ropcode = in(2)->Opcode();
  if (Matcher::match_rule_supported(Op_RotateLeft) &&
      lopcode == Op_LShiftI && ropcode == Op_URShiftI && in(1)->in(1) == in(2)->in(1)) {
    Node* lshift = in(1)->in(2);
    Node* rshift = in(2)->in(2);
    Node* shift = rotate_shift(phase, lshift, rshift, 0x1F);
    if (shift != nullptr) {
      return new RotateLeftNode(in(1)->in(1), shift, TypeInt::INT);
    }
    return nullptr;
  }
  if (Matcher::match_rule_supported(Op_RotateRight) &&
      lopcode == Op_URShiftI && ropcode == Op_LShiftI && in(1)->in(1) == in(2)->in(1)) {
    Node* rshift = in(1)->in(2);
    Node* lshift = in(2)->in(2);
    Node* shift = rotate_shift(phase, rshift, lshift, 0x1F);
    if (shift != nullptr) {
      return new RotateRightNode(in(1)->in(1), shift, TypeInt::INT);
    }
  }
  return nullptr;
}

//------------------------------add_ring---------------------------------------
// Supplied function returns the sum of the inputs IN THE CURRENT RING.  For
// the logical operations the ring's ADD is really a logical OR function.
// This also type-checks the inputs for sanity.  Guaranteed never to
// be passed a TOP or BOTTOM type, these are filtered out by pre-check.
const Type *OrINode::add_ring( const Type *t0, const Type *t1 ) const {
  const TypeInt *r0 = t0->is_int(); // Handy access
  const TypeInt *r1 = t1->is_int();

  // If both args are bool, can figure out better types
  if ( r0 == TypeInt::BOOL ) {
    if ( r1 == TypeInt::ONE) {
      return TypeInt::ONE;
    } else if ( r1 == TypeInt::BOOL ) {
      return TypeInt::BOOL;
    }
  } else if ( r0 == TypeInt::ONE ) {
    if ( r1 == TypeInt::BOOL ) {
      return TypeInt::ONE;
    }
  }

  // If either input is not a constant, just return all integers.
  if( !r0->is_con() || !r1->is_con() )
    return TypeInt::INT;        // Any integer, but still no symbols.

  // Otherwise just OR them bits.
  return TypeInt::make( r0->get_con() | r1->get_con() );
}

//=============================================================================
//------------------------------Identity---------------------------------------
Node* OrLNode::Identity(PhaseGVN* phase) {
  // x | x => x
  if (in(1) == in(2)) {
    return in(1);
  }

  return AddNode::Identity(phase);
}

Node* OrLNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  int lopcode = in(1)->Opcode();
  int ropcode = in(2)->Opcode();
  if (Matcher::match_rule_supported(Op_RotateLeft) &&
      lopcode == Op_LShiftL && ropcode == Op_URShiftL && in(1)->in(1) == in(2)->in(1)) {
    Node* lshift = in(1)->in(2);
    Node* rshift = in(2)->in(2);
    Node* shift = rotate_shift(phase, lshift, rshift, 0x3F);
    if (shift != nullptr) {
      return new RotateLeftNode(in(1)->in(1), shift, TypeLong::LONG);
    }
    return nullptr;
  }
  if (Matcher::match_rule_supported(Op_RotateRight) &&
      lopcode == Op_URShiftL && ropcode == Op_LShiftL && in(1)->in(1) == in(2)->in(1)) {
    Node* rshift = in(1)->in(2);
    Node* lshift = in(2)->in(2);
    Node* shift = rotate_shift(phase, rshift, lshift, 0x3F);
    if (shift != nullptr) {
      return new RotateRightNode(in(1)->in(1), shift, TypeLong::LONG);
    }
  }
  return nullptr;
}

//------------------------------add_ring---------------------------------------
const Type *OrLNode::add_ring( const Type *t0, const Type *t1 ) const {
  const TypeLong *r0 = t0->is_long(); // Handy access
  const TypeLong *r1 = t1->is_long();

  // If either input is not a constant, just return all integers.
  if( !r0->is_con() || !r1->is_con() )
    return TypeLong::LONG;      // Any integer, but still no symbols.

  // Otherwise just OR them bits.
  return TypeLong::make( r0->get_con() | r1->get_con() );
}

//---------------------------Helper -------------------------------------------
/* Decide if the given node is used only in arithmetic expressions(add or sub).
 */
static bool is_used_in_only_arithmetic(Node* n, BasicType bt) {
  for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
    Node* u = n->fast_out(i);
    if (u->Opcode() != Op_Add(bt) && u->Opcode() != Op_Sub(bt)) {
      return false;
    }
  }
  return true;
}

//=============================================================================
//------------------------------Idealize---------------------------------------
Node* XorINode::Ideal(PhaseGVN* phase, bool can_reshape) {
  Node* in1 = in(1);
  Node* in2 = in(2);

  // Convert ~x into -1-x when ~x is used in an arithmetic expression
  // or x itself is an expression.
  if (phase->type(in2) == TypeInt::MINUS_1) { // follows LHS^(-1), i.e., ~LHS
    if (phase->is_IterGVN()) {
      if (is_used_in_only_arithmetic(this, T_INT)
          // LHS is arithmetic
          || (in1->Opcode() == Op_AddI || in1->Opcode() == Op_SubI)) {
        return new SubINode(in2, in1);
      }
    } else {
      // graph could be incomplete in GVN so we postpone to IGVN
      phase->record_for_igvn(this);
    }
  }

  // Propagate xor through constant cmoves. This pattern can occur after expansion of Conv2B nodes.
  const TypeInt* in2_type = phase->type(in2)->isa_int();
  if (in1->Opcode() == Op_CMoveI && in2_type != nullptr && in2_type->is_con()) {
    int in2_val = in2_type->get_con();

    // Get types of both sides of the CMove
    const TypeInt* left = phase->type(in1->in(CMoveNode::IfFalse))->isa_int();
    const TypeInt* right = phase->type(in1->in(CMoveNode::IfTrue))->isa_int();

    // Ensure that both sides are int constants
    if (left != nullptr && right != nullptr && left->is_con() && right->is_con()) {
      Node* cond = in1->in(CMoveNode::Condition);

      // Check that the comparison is a bool and that the cmp node type is correct
      if (cond->is_Bool()) {
        int cmp_op = cond->in(1)->Opcode();

        if (cmp_op == Op_CmpI || cmp_op == Op_CmpP) {
          int l_val = left->get_con();
          int r_val = right->get_con();

          return new CMoveINode(cond, phase->intcon(l_val ^ in2_val), phase->intcon(r_val ^ in2_val), TypeInt::INT);
        }
      }
    }
  }

  return AddNode::Ideal(phase, can_reshape);
}

const Type* XorINode::Value(PhaseGVN* phase) const {
  Node* in1 = in(1);
  Node* in2 = in(2);
  const Type* t1 = phase->type(in1);
  const Type* t2 = phase->type(in2);
  if (t1 == Type::TOP || t2 == Type::TOP) {
    return Type::TOP;
  }
  // x ^ x ==> 0
  if (in1->eqv_uncast(in2)) {
    return add_id();
  }
  // result of xor can only have bits sets where any of the
  // inputs have bits set. lo can always become 0.
  const TypeInt* t1i = t1->is_int();
  const TypeInt* t2i = t2->is_int();
  if ((t1i->_lo >= 0) &&
      (t1i->_hi > 0)  &&
      (t2i->_lo >= 0) &&
      (t2i->_hi > 0)) {
    // hi - set all bits below the highest bit. Using round_down to avoid overflow.
    const TypeInt* t1x = TypeInt::make(0, round_down_power_of_2(t1i->_hi) + (round_down_power_of_2(t1i->_hi) - 1), t1i->_widen);
    const TypeInt* t2x = TypeInt::make(0, round_down_power_of_2(t2i->_hi) + (round_down_power_of_2(t2i->_hi) - 1), t2i->_widen);
    return t1x->meet(t2x);
  }
  return AddNode::Value(phase);
}


//------------------------------add_ring---------------------------------------
// Supplied function returns the sum of the inputs IN THE CURRENT RING.  For
// the logical operations the ring's ADD is really a logical OR function.
// This also type-checks the inputs for sanity.  Guaranteed never to
// be passed a TOP or BOTTOM type, these are filtered out by pre-check.
const Type *XorINode::add_ring( const Type *t0, const Type *t1 ) const {
  const TypeInt *r0 = t0->is_int(); // Handy access
  const TypeInt *r1 = t1->is_int();

  // Complementing a boolean?
  if( r0 == TypeInt::BOOL && ( r1 == TypeInt::ONE
                               || r1 == TypeInt::BOOL))
    return TypeInt::BOOL;

  if( !r0->is_con() || !r1->is_con() ) // Not constants
    return TypeInt::INT;        // Any integer, but still no symbols.

  // Otherwise just XOR them bits.
  return TypeInt::make( r0->get_con() ^ r1->get_con() );
}

//=============================================================================
//------------------------------add_ring---------------------------------------
const Type *XorLNode::add_ring( const Type *t0, const Type *t1 ) const {
  const TypeLong *r0 = t0->is_long(); // Handy access
  const TypeLong *r1 = t1->is_long();

  // If either input is not a constant, just return all integers.
  if( !r0->is_con() || !r1->is_con() )
    return TypeLong::LONG;      // Any integer, but still no symbols.

  // Otherwise just OR them bits.
  return TypeLong::make( r0->get_con() ^ r1->get_con() );
}

Node* XorLNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  Node* in1 = in(1);
  Node* in2 = in(2);

  // Convert ~x into -1-x when ~x is used in an arithmetic expression
  // or x itself is an arithmetic expression.
  if (phase->type(in2) == TypeLong::MINUS_1) { // follows LHS^(-1), i.e., ~LHS
    if (phase->is_IterGVN()) {
      if (is_used_in_only_arithmetic(this, T_LONG)
          // LHS is arithmetic
          || (in1->Opcode() == Op_AddL || in1->Opcode() == Op_SubL)) {
        return new SubLNode(in2, in1);
      }
    } else {
      // graph could be incomplete in GVN so we postpone to IGVN
      phase->record_for_igvn(this);
    }
  }
  return AddNode::Ideal(phase, can_reshape);
}

const Type* XorLNode::Value(PhaseGVN* phase) const {
  Node* in1 = in(1);
  Node* in2 = in(2);
  const Type* t1 = phase->type(in1);
  const Type* t2 = phase->type(in2);
  if (t1 == Type::TOP || t2 == Type::TOP) {
    return Type::TOP;
  }
  // x ^ x ==> 0
  if (in1->eqv_uncast(in2)) {
    return add_id();
  }
  // result of xor can only have bits sets where any of the
  // inputs have bits set. lo can always become 0.
  const TypeLong* t1l = t1->is_long();
  const TypeLong* t2l = t2->is_long();
  if ((t1l->_lo >= 0) &&
      (t1l->_hi > 0)  &&
      (t2l->_lo >= 0) &&
      (t2l->_hi > 0)) {
    // hi - set all bits below the highest bit. Using round_down to avoid overflow.
    const TypeLong* t1x = TypeLong::make(0, round_down_power_of_2(t1l->_hi) + (round_down_power_of_2(t1l->_hi) - 1), t1l->_widen);
    const TypeLong* t2x = TypeLong::make(0, round_down_power_of_2(t2l->_hi) + (round_down_power_of_2(t2l->_hi) - 1), t2l->_widen);
    return t1x->meet(t2x);
  }
  return AddNode::Value(phase);
}

Node* build_min_max_int(Node* a, Node* b, bool is_max) {
  if (is_max) {
    return new MaxINode(a, b);
  } else {
    return new MinINode(a, b);
  }
}

Node* MaxNode::build_min_max(Node* a, Node* b, bool is_max, bool is_unsigned, const Type* t, PhaseGVN& gvn) {
  bool is_int = gvn.type(a)->isa_int();
  assert(is_int || gvn.type(a)->isa_long(), "int or long inputs");
  assert(is_int == (gvn.type(b)->isa_int() != nullptr), "inconsistent inputs");
  BasicType bt = is_int ? T_INT: T_LONG;
  Node* hook = nullptr;
  if (gvn.is_IterGVN()) {
    // Make sure a and b are not destroyed
    hook = new Node(2);
    hook->init_req(0, a);
    hook->init_req(1, b);
  }
  Node* res = nullptr;
  if (is_int && !is_unsigned) {
    res = gvn.transform(build_min_max_int(a, b, is_max));
    assert(gvn.type(res)->is_int()->_lo >= t->is_int()->_lo && gvn.type(res)->is_int()->_hi <= t->is_int()->_hi, "type doesn't match");
  } else {
    Node* cmp = nullptr;
    if (is_max) {
      cmp = gvn.transform(CmpNode::make(a, b, bt, is_unsigned));
    } else {
      cmp = gvn.transform(CmpNode::make(b, a, bt, is_unsigned));
    }
    Node* bol = gvn.transform(new BoolNode(cmp, BoolTest::lt));
    res = gvn.transform(CMoveNode::make(nullptr, bol, a, b, t));
  }
  if (hook != nullptr) {
    hook->destruct(&gvn);
  }
  return res;
}

Node* MaxNode::build_min_max_diff_with_zero(Node* a, Node* b, bool is_max, const Type* t, PhaseGVN& gvn) {
  bool is_int = gvn.type(a)->isa_int();
  assert(is_int || gvn.type(a)->isa_long(), "int or long inputs");
  assert(is_int == (gvn.type(b)->isa_int() != nullptr), "inconsistent inputs");
  BasicType bt = is_int ? T_INT: T_LONG;
  Node* zero = gvn.integercon(0, bt);
  Node* hook = nullptr;
  if (gvn.is_IterGVN()) {
    // Make sure a and b are not destroyed
    hook = new Node(2);
    hook->init_req(0, a);
    hook->init_req(1, b);
  }
  Node* cmp = nullptr;
  if (is_max) {
    cmp = gvn.transform(CmpNode::make(a, b, bt, false));
  } else {
    cmp = gvn.transform(CmpNode::make(b, a, bt, false));
  }
  Node* sub = gvn.transform(SubNode::make(a, b, bt));
  Node* bol = gvn.transform(new BoolNode(cmp, BoolTest::lt));
  Node* res = gvn.transform(CMoveNode::make(nullptr, bol, sub, zero, t));
  if (hook != nullptr) {
    hook->destruct(&gvn);
  }
  return res;
}

// Check if addition of an integer with type 't' and a constant 'c' can overflow.
static bool can_overflow(const TypeInt* t, jint c) {
  jint t_lo = t->_lo;
  jint t_hi = t->_hi;
  return ((c < 0 && (java_add(t_lo, c) > t_lo)) ||
          (c > 0 && (java_add(t_hi, c) < t_hi)));
}

// Let <x, x_off> = x_operands and <y, y_off> = y_operands.
// If x == y and neither add(x, x_off) nor add(y, y_off) overflow, return
// add(x, op(x_off, y_off)). Otherwise, return nullptr.
Node* MaxNode::extract_add(PhaseGVN* phase, ConstAddOperands x_operands, ConstAddOperands y_operands) {
  Node* x = x_operands.first;
  Node* y = y_operands.first;
  int opcode = Opcode();
  assert(opcode == Op_MaxI || opcode == Op_MinI, "Unexpected opcode");
  const TypeInt* tx = phase->type(x)->isa_int();
  jint x_off = x_operands.second;
  jint y_off = y_operands.second;
  if (x == y && tx != nullptr &&
      !can_overflow(tx, x_off) &&
      !can_overflow(tx, y_off)) {
    jint c = opcode == Op_MinI ? MIN2(x_off, y_off) : MAX2(x_off, y_off);
    return new AddINode(x, phase->intcon(c));
  }
  return nullptr;
}

// Try to cast n as an integer addition with a constant. Return:
//   <x, C>,       if n == add(x, C), where 'C' is a non-TOP constant;
//   <nullptr, 0>, if n == add(x, C), where 'C' is a TOP constant; or
//   <n, 0>,       otherwise.
static ConstAddOperands as_add_with_constant(Node* n) {
  if (n->Opcode() != Op_AddI) {
    return ConstAddOperands(n, 0);
  }
  Node* x = n->in(1);
  Node* c = n->in(2);
  if (!c->is_Con()) {
    return ConstAddOperands(n, 0);
  }
  const Type* c_type = c->bottom_type();
  if (c_type == Type::TOP) {
    return ConstAddOperands(nullptr, 0);
  }
  return ConstAddOperands(x, c_type->is_int()->get_con());
}

Node* MaxNode::IdealI(PhaseGVN* phase, bool can_reshape) {
  int opcode = Opcode();
  assert(opcode == Op_MinI || opcode == Op_MaxI, "Unexpected opcode");
  // Try to transform the following pattern, in any of its four possible
  // permutations induced by op's commutativity:
  //     op(op(add(inner, inner_off), inner_other), add(outer, outer_off))
  // into
  //     op(add(inner, op(inner_off, outer_off)), inner_other),
  // where:
  //     op is either MinI or MaxI, and
  //     inner == outer, and
  //     the additions cannot overflow.
  for (uint inner_op_index = 1; inner_op_index <= 2; inner_op_index++) {
    if (in(inner_op_index)->Opcode() != opcode) {
      continue;
    }
    Node* outer_add = in(inner_op_index == 1 ? 2 : 1);
    ConstAddOperands outer_add_operands = as_add_with_constant(outer_add);
    if (outer_add_operands.first == nullptr) {
      return nullptr; // outer_add has a TOP input, no need to continue.
    }
    // One operand is a MinI/MaxI and the other is an integer addition with
    // constant. Test the operands of the inner MinI/MaxI.
    for (uint inner_add_index = 1; inner_add_index <= 2; inner_add_index++) {
      Node* inner_op = in(inner_op_index);
      Node* inner_add = inner_op->in(inner_add_index);
      ConstAddOperands inner_add_operands = as_add_with_constant(inner_add);
      if (inner_add_operands.first == nullptr) {
        return nullptr; // inner_add has a TOP input, no need to continue.
      }
      // Try to extract the inner add.
      Node* add_extracted = extract_add(phase, inner_add_operands, outer_add_operands);
      if (add_extracted == nullptr) {
        continue;
      }
      Node* add_transformed = phase->transform(add_extracted);
      Node* inner_other = inner_op->in(inner_add_index == 1 ? 2 : 1);
      return build_min_max_int(add_transformed, inner_other, opcode == Op_MaxI);
    }
  }
  // Try to transform
  //     op(add(x, x_off), add(y, y_off))
  // into
  //     add(x, op(x_off, y_off)),
  // where:
  //     op is either MinI or MaxI, and
  //     inner == outer, and
  //     the additions cannot overflow.
  ConstAddOperands xC = as_add_with_constant(in(1));
  ConstAddOperands yC = as_add_with_constant(in(2));
  if (xC.first == nullptr || yC.first == nullptr) return nullptr;
  return extract_add(phase, xC, yC);
}

// Ideal transformations for MaxINode
Node* MaxINode::Ideal(PhaseGVN* phase, bool can_reshape) {
  return IdealI(phase, can_reshape);
}

//=============================================================================
//------------------------------add_ring---------------------------------------
// Supplied function returns the sum of the inputs.
const Type *MaxINode::add_ring( const Type *t0, const Type *t1 ) const {
  const TypeInt *r0 = t0->is_int(); // Handy access
  const TypeInt *r1 = t1->is_int();

  // Otherwise just MAX them bits.
  return TypeInt::make( MAX2(r0->_lo,r1->_lo), MAX2(r0->_hi,r1->_hi), MAX2(r0->_widen,r1->_widen) );
}

//=============================================================================
//------------------------------Idealize---------------------------------------
// MINs show up in range-check loop limit calculations.  Look for
// "MIN2(x+c0,MIN2(y,x+c1))".  Pick the smaller constant: "MIN2(x+c0,y)"
Node* MinINode::Ideal(PhaseGVN* phase, bool can_reshape) {
  return IdealI(phase, can_reshape);
}

//------------------------------add_ring---------------------------------------
// Supplied function returns the sum of the inputs.
const Type *MinINode::add_ring( const Type *t0, const Type *t1 ) const {
  const TypeInt *r0 = t0->is_int(); // Handy access
  const TypeInt *r1 = t1->is_int();

  // Otherwise just MIN them bits.
  return TypeInt::make( MIN2(r0->_lo,r1->_lo), MIN2(r0->_hi,r1->_hi), MAX2(r0->_widen,r1->_widen) );
}

// Collapse the "addition with overflow-protection" pattern, and the symmetrical
// "subtraction with underflow-protection" pattern. These are created during the
// unrolling, when we have to adjust the limit by subtracting the stride, but want
// to protect against underflow: MaxL(SubL(limit, stride), min_jint).
// If we have more than one of those in a sequence:
//
//   x  con2
//   |  |
//   AddL  clamp2
//     |    |
//    Max/MinL con1
//          |  |
//          AddL  clamp1
//            |    |
//           Max/MinL (n)
//
// We want to collapse it to:
//
//   x  con1  con2
//   |    |    |
//   |   AddLNode (new_con)
//   |    |
//  AddLNode  clamp1
//        |    |
//       Max/MinL (n)
//
// Note: we assume that SubL was already replaced by an AddL, and that the stride
// has its sign flipped: SubL(limit, stride) -> AddL(limit, -stride).
Node* fold_subI_no_underflow_pattern(Node* n, PhaseGVN* phase) {
  assert(n->Opcode() == Op_MaxL || n->Opcode() == Op_MinL, "sanity");
  // Check that the two clamps have the correct values.
  jlong clamp = (n->Opcode() == Op_MaxL) ? min_jint : max_jint;
  auto is_clamp = [&](Node* c) {
    const TypeLong* t = phase->type(c)->isa_long();
    return t != nullptr && t->is_con() &&
           t->get_con() == clamp;
  };
  // Check that the constants are negative if MaxL, and positive if MinL.
  auto is_sub_con = [&](Node* c) {
    const TypeLong* t = phase->type(c)->isa_long();
    return t != nullptr && t->is_con() &&
           t->get_con() < max_jint && t->get_con() > min_jint &&
           (t->get_con() < 0) == (n->Opcode() == Op_MaxL);
  };
  // Verify the graph level by level:
  Node* add1   = n->in(1);
  Node* clamp1 = n->in(2);
  if (add1->Opcode() == Op_AddL && is_clamp(clamp1)) {
    Node* max2 = add1->in(1);
    Node* con1 = add1->in(2);
    if (max2->Opcode() == n->Opcode() && is_sub_con(con1)) {
      Node* add2   = max2->in(1);
      Node* clamp2 = max2->in(2);
      if (add2->Opcode() == Op_AddL && is_clamp(clamp2)) {
        Node* x    = add2->in(1);
        Node* con2 = add2->in(2);
        if (is_sub_con(con2)) {
          Node* new_con = phase->transform(new AddLNode(con1, con2));
          Node* new_sub = phase->transform(new AddLNode(x, new_con));
          n->set_req_X(1, new_sub, phase);
          return n;
        }
      }
    }
  }
  return nullptr;
}

const Type* MaxLNode::add_ring(const Type* t0, const Type* t1) const {
  const TypeLong* r0 = t0->is_long();
  const TypeLong* r1 = t1->is_long();

  return TypeLong::make(MAX2(r0->_lo, r1->_lo), MAX2(r0->_hi, r1->_hi), MAX2(r0->_widen, r1->_widen));
}

Node* MaxLNode::Identity(PhaseGVN* phase) {
  const TypeLong* t1 = phase->type(in(1))->is_long();
  const TypeLong* t2 = phase->type(in(2))->is_long();

  // Can we determine maximum statically?
  if (t1->_lo >= t2->_hi) {
    return in(1);
  } else if (t2->_lo >= t1->_hi) {
    return in(2);
  }

  return MaxNode::Identity(phase);
}

Node* MaxLNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  Node* n = AddNode::Ideal(phase, can_reshape);
  if (n != nullptr) {
    return n;
  }
  if (can_reshape) {
    return fold_subI_no_underflow_pattern(this, phase);
  }
  return nullptr;
}

const Type* MinLNode::add_ring(const Type* t0, const Type* t1) const {
  const TypeLong* r0 = t0->is_long();
  const TypeLong* r1 = t1->is_long();

  return TypeLong::make(MIN2(r0->_lo, r1->_lo), MIN2(r0->_hi, r1->_hi), MIN2(r0->_widen, r1->_widen));
}

Node* MinLNode::Identity(PhaseGVN* phase) {
  const TypeLong* t1 = phase->type(in(1))->is_long();
  const TypeLong* t2 = phase->type(in(2))->is_long();

  // Can we determine minimum statically?
  if (t1->_lo >= t2->_hi) {
    return in(2);
  } else if (t2->_lo >= t1->_hi) {
    return in(1);
  }

  return MaxNode::Identity(phase);
}

Node* MinLNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  Node* n = AddNode::Ideal(phase, can_reshape);
  if (n != nullptr) {
    return n;
  }
  if (can_reshape) {
    return fold_subI_no_underflow_pattern(this, phase);
  }
  return nullptr;
}

//------------------------------add_ring---------------------------------------
const Type* MinFNode::add_ring(const Type* t0, const Type* t1 ) const {
  const TypeF* r0 = t0->isa_float_constant();
  const TypeF* r1 = t1->isa_float_constant();
  if (r0 == nullptr || r1 == nullptr) {
    return bottom_type();
  }

  if (r0->is_nan()) {
    return r0;
  }
  if (r1->is_nan()) {
    return r1;
  }

  float f0 = r0->getf();
  float f1 = r1->getf();
  if (f0 != 0.0f || f1 != 0.0f) {
    return f0 < f1 ? r0 : r1;
  }

  // handle min of 0.0, -0.0 case.
  return (jint_cast(f0) < jint_cast(f1)) ? r0 : r1;
}

//------------------------------add_ring---------------------------------------
const Type* MinDNode::add_ring(const Type* t0, const Type* t1) const {
  const TypeD* r0 = t0->isa_double_constant();
  const TypeD* r1 = t1->isa_double_constant();
  if (r0 == nullptr || r1 == nullptr) {
    return bottom_type();
  }

  if (r0->is_nan()) {
    return r0;
  }
  if (r1->is_nan()) {
    return r1;
  }

  double d0 = r0->getd();
  double d1 = r1->getd();
  if (d0 != 0.0 || d1 != 0.0) {
    return d0 < d1 ? r0 : r1;
  }

  // handle min of 0.0, -0.0 case.
  return (jlong_cast(d0) < jlong_cast(d1)) ? r0 : r1;
}

//------------------------------add_ring---------------------------------------
const Type* MaxFNode::add_ring(const Type* t0, const Type* t1) const {
  const TypeF* r0 = t0->isa_float_constant();
  const TypeF* r1 = t1->isa_float_constant();
  if (r0 == nullptr || r1 == nullptr) {
    return bottom_type();
  }

  if (r0->is_nan()) {
    return r0;
  }
  if (r1->is_nan()) {
    return r1;
  }

  float f0 = r0->getf();
  float f1 = r1->getf();
  if (f0 != 0.0f || f1 != 0.0f) {
    return f0 > f1 ? r0 : r1;
  }

  // handle max of 0.0,-0.0 case.
  return (jint_cast(f0) > jint_cast(f1)) ? r0 : r1;
}

//------------------------------add_ring---------------------------------------
const Type* MaxDNode::add_ring(const Type* t0, const Type* t1) const {
  const TypeD* r0 = t0->isa_double_constant();
  const TypeD* r1 = t1->isa_double_constant();
  if (r0 == nullptr || r1 == nullptr) {
    return bottom_type();
  }

  if (r0->is_nan()) {
    return r0;
  }
  if (r1->is_nan()) {
    return r1;
  }

  double d0 = r0->getd();
  double d1 = r1->getd();
  if (d0 != 0.0 || d1 != 0.0) {
    return d0 > d1 ? r0 : r1;
  }

  // handle max of 0.0, -0.0 case.
  return (jlong_cast(d0) > jlong_cast(d1)) ? r0 : r1;
}
