/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_addnode.cpp.incl"

#define MAXFLOAT        ((float)3.40282346638528860e+38)

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
Node *AddNode::Identity( PhaseTransform *phase ) {
  const Type *zero = add_id();  // The additive identity
  if( phase->type( in(1) )->higher_equal( zero ) ) return in(2);
  if( phase->type( in(2) )->higher_equal( zero ) ) return in(1);
  return this;
}

//------------------------------commute----------------------------------------
// Commute operands to move loads and constants to the right.
static bool commute( Node *add, int con_left, int con_right ) {
  Node *in1 = add->in(1);
  Node *in2 = add->in(2);

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
  if( in1->is_Phi() && (phi = in1->as_Phi()) && !phi->is_copy() && phi->region()->is_Loop() && phi->in(2)==add)
    return false;
  if( in2->is_Phi() && (phi = in2->as_Phi()) && !phi->is_copy() && phi->region()->is_Loop() && phi->in(2)==add){
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
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  int con_left  = t1->singleton();
  int con_right = t2->singleton();

  // Check for commutative operation desired
  if( commute(this,con_left,con_right) ) return this;

  AddNode *progress = NULL;             // Progress flag

  // Convert "(x+1)+2" into "x+(1+2)".  If the right input is a
  // constant, and the left input is an add of a constant, flatten the
  // expression tree.
  Node *add1 = in(1);
  Node *add2 = in(2);
  int add1_op = add1->Opcode();
  int this_op = Opcode();
  if( con_right && t2 != Type::TOP && // Right input is a constant?
      add1_op == this_op ) { // Left input is an Add?

    // Type of left _in right input
    const Type *t12 = phase->type( add1->in(2) );
    if( t12->singleton() && t12 != Type::TOP ) { // Left input is an add of a constant?
      // Check for rare case of closed data cycle which can happen inside
      // unreachable loops. In these cases the computation is undefined.
#ifdef ASSERT
      Node *add11    = add1->in(1);
      int   add11_op = add11->Opcode();
      if( (add1 == add1->in(1))
         || (add11_op == this_op && add11->in(1) == add1) ) {
        assert(false, "dead loop in AddNode::Ideal");
      }
#endif
      // The Add of the flattened expression
      Node *x1 = add1->in(1);
      Node *x2 = phase->makecon( add1->as_Add()->add_ring( t2, t12 ));
      PhaseIterGVN *igvn = phase->is_IterGVN();
      if( igvn ) {
        set_req_X(2,x2,igvn);
        set_req_X(1,x1,igvn);
      } else {
        set_req(2,x2);
        set_req(1,x1);
      }
      progress = this;            // Made progress
      add1 = in(1);
      add1_op = add1->Opcode();
    }
  }

  // Convert "(x+1)+y" into "(x+y)+1".  Push constants down the expression tree.
  if( add1_op == this_op && !con_right ) {
    Node *a12 = add1->in(2);
    const Type *t12 = phase->type( a12 );
    if( t12->singleton() && t12 != Type::TOP && (add1 != add1->in(1)) &&
       !(add1->in(1)->is_Phi() && add1->in(1)->as_Phi()->is_tripcount()) ) {
      assert(add1->in(1) != this, "dead loop in AddNode::Ideal");
      add2 = add1->clone();
      add2->set_req(2, in(2));
      add2 = phase->transform(add2);
      set_req(1, add2);
      set_req(2, a12);
      progress = this;
      add2 = a12;
    }
  }

  // Convert "x+(y+1)" into "(x+y)+1".  Push constants down the expression tree.
  int add2_op = add2->Opcode();
  if( add2_op == this_op && !con_left ) {
    Node *a22 = add2->in(2);
    const Type *t22 = phase->type( a22 );
    if( t22->singleton() && t22 != Type::TOP && (add2 != add2->in(1)) &&
       !(add2->in(1)->is_Phi() && add2->in(1)->as_Phi()->is_tripcount()) ) {
      assert(add2->in(1) != this, "dead loop in AddNode::Ideal");
      Node *addx = add2->clone();
      addx->set_req(1, in(1));
      addx->set_req(2, add2->in(1));
      addx = phase->transform(addx);
      set_req(1, addx);
      set_req(2, a22);
      progress = this;
    }
  }

  return progress;
}

//------------------------------Value-----------------------------------------
// An add node sums it's two _in.  If one input is an RSD, we must mixin
// the other input's symbols.
const Type *AddNode::Value( PhaseTransform *phase ) const {
  // Either input is TOP ==> the result is TOP
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // Either input is BOTTOM ==> the result is the local BOTTOM
  const Type *bot = bottom_type();
  if( (t1 == bot) || (t2 == bot) ||
      (t1 == Type::BOTTOM) || (t2 == Type::BOTTOM) )
    return bot;

  // Check for an addition involving the additive identity
  const Type *tadd = add_of_identity( t1, t2 );
  if( tadd ) return tadd;

  return add_ring(t1,t2);               // Local flavor of type addition
}

//------------------------------add_identity-----------------------------------
// Check for addition of the identity
const Type *AddNode::add_of_identity( const Type *t1, const Type *t2 ) const {
  const Type *zero = add_id();  // The additive identity
  if( t1->higher_equal( zero ) ) return t2;
  if( t2->higher_equal( zero ) ) return t1;

  return NULL;
}


//=============================================================================
//------------------------------Idealize---------------------------------------
Node *AddINode::Ideal(PhaseGVN *phase, bool can_reshape) {
  Node* in1 = in(1);
  Node* in2 = in(2);
  int op1 = in1->Opcode();
  int op2 = in2->Opcode();
  // Fold (con1-x)+con2 into (con1+con2)-x
  if ( op1 == Op_AddI && op2 == Op_SubI ) {
    // Swap edges to try optimizations below
    in1 = in2;
    in2 = in(1);
    op1 = op2;
    op2 = in2->Opcode();
  }
  if( op1 == Op_SubI ) {
    const Type *t_sub1 = phase->type( in1->in(1) );
    const Type *t_2    = phase->type( in2        );
    if( t_sub1->singleton() && t_2->singleton() && t_sub1 != Type::TOP && t_2 != Type::TOP )
      return new (phase->C, 3) SubINode(phase->makecon( add_ring( t_sub1, t_2 ) ),
                              in1->in(2) );
    // Convert "(a-b)+(c-d)" into "(a+c)-(b+d)"
    if( op2 == Op_SubI ) {
      // Check for dead cycle: d = (a-b)+(c-d)
      assert( in1->in(2) != this && in2->in(2) != this,
              "dead loop in AddINode::Ideal" );
      Node *sub  = new (phase->C, 3) SubINode(NULL, NULL);
      sub->init_req(1, phase->transform(new (phase->C, 3) AddINode(in1->in(1), in2->in(1) ) ));
      sub->init_req(2, phase->transform(new (phase->C, 3) AddINode(in1->in(2), in2->in(2) ) ));
      return sub;
    }
    // Convert "(a-b)+(b+c)" into "(a+c)"
    if( op2 == Op_AddI && in1->in(2) == in2->in(1) ) {
      assert(in1->in(1) != this && in2->in(2) != this,"dead loop in AddINode::Ideal");
      return new (phase->C, 3) AddINode(in1->in(1), in2->in(2));
    }
    // Convert "(a-b)+(c+b)" into "(a+c)"
    if( op2 == Op_AddI && in1->in(2) == in2->in(2) ) {
      assert(in1->in(1) != this && in2->in(1) != this,"dead loop in AddINode::Ideal");
      return new (phase->C, 3) AddINode(in1->in(1), in2->in(1));
    }
    // Convert "(a-b)+(b-c)" into "(a-c)"
    if( op2 == Op_SubI && in1->in(2) == in2->in(1) ) {
      assert(in1->in(1) != this && in2->in(2) != this,"dead loop in AddINode::Ideal");
      return new (phase->C, 3) SubINode(in1->in(1), in2->in(2));
    }
    // Convert "(a-b)+(c-a)" into "(c-b)"
    if( op2 == Op_SubI && in1->in(1) == in2->in(2) ) {
      assert(in1->in(2) != this && in2->in(1) != this,"dead loop in AddINode::Ideal");
      return new (phase->C, 3) SubINode(in2->in(1), in1->in(2));
    }
  }

  // Convert "x+(0-y)" into "(x-y)"
  if( op2 == Op_SubI && phase->type(in2->in(1)) == TypeInt::ZERO )
    return new (phase->C, 3) SubINode(in1, in2->in(2) );

  // Convert "(0-y)+x" into "(x-y)"
  if( op1 == Op_SubI && phase->type(in1->in(1)) == TypeInt::ZERO )
    return new (phase->C, 3) SubINode( in2, in1->in(2) );

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
  if( op1 == Op_URShiftI && op2 == Op_ConI &&
      in1->in(2)->Opcode() == Op_ConI ) {
    jint z = phase->type( in1->in(2) )->is_int()->get_con() & 0x1f; // only least significant 5 bits matter
    jint y = phase->type( in2 )->is_int()->get_con();

    if( z < 5 && -5 < y && y < 0 ) {
      const Type *t_in11 = phase->type(in1->in(1));
      if( t_in11 != Type::TOP && (t_in11->is_int()->_lo >= -(y << z)) ) {
        Node *a = phase->transform( new (phase->C, 3) AddINode( in1->in(1), phase->intcon(y<<z) ) );
        return new (phase->C, 3) URShiftINode( a, in1->in(2) );
      }
    }
  }

  return AddNode::Ideal(phase, can_reshape);
}


//------------------------------Identity---------------------------------------
// Fold (x-y)+y  OR  y+(x-y)  into  x
Node *AddINode::Identity( PhaseTransform *phase ) {
  if( in(1)->Opcode() == Op_SubI && phase->eqv(in(1)->in(2),in(2)) ) {
    return in(1)->in(1);
  }
  else if( in(2)->Opcode() == Op_SubI && phase->eqv(in(2)->in(2),in(1)) ) {
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
  int lo = r0->_lo + r1->_lo;
  int hi = r0->_hi + r1->_hi;
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
Node *AddLNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  Node* in1 = in(1);
  Node* in2 = in(2);
  int op1 = in1->Opcode();
  int op2 = in2->Opcode();
  // Fold (con1-x)+con2 into (con1+con2)-x
  if ( op1 == Op_AddL && op2 == Op_SubL ) {
    // Swap edges to try optimizations below
    in1 = in2;
    in2 = in(1);
    op1 = op2;
    op2 = in2->Opcode();
  }
  // Fold (con1-x)+con2 into (con1+con2)-x
  if( op1 == Op_SubL ) {
    const Type *t_sub1 = phase->type( in1->in(1) );
    const Type *t_2    = phase->type( in2        );
    if( t_sub1->singleton() && t_2->singleton() && t_sub1 != Type::TOP && t_2 != Type::TOP )
      return new (phase->C, 3) SubLNode(phase->makecon( add_ring( t_sub1, t_2 ) ),
                              in1->in(2) );
    // Convert "(a-b)+(c-d)" into "(a+c)-(b+d)"
    if( op2 == Op_SubL ) {
      // Check for dead cycle: d = (a-b)+(c-d)
      assert( in1->in(2) != this && in2->in(2) != this,
              "dead loop in AddLNode::Ideal" );
      Node *sub  = new (phase->C, 3) SubLNode(NULL, NULL);
      sub->init_req(1, phase->transform(new (phase->C, 3) AddLNode(in1->in(1), in2->in(1) ) ));
      sub->init_req(2, phase->transform(new (phase->C, 3) AddLNode(in1->in(2), in2->in(2) ) ));
      return sub;
    }
    // Convert "(a-b)+(b+c)" into "(a+c)"
    if( op2 == Op_AddL && in1->in(2) == in2->in(1) ) {
      assert(in1->in(1) != this && in2->in(2) != this,"dead loop in AddLNode::Ideal");
      return new (phase->C, 3) AddLNode(in1->in(1), in2->in(2));
    }
    // Convert "(a-b)+(c+b)" into "(a+c)"
    if( op2 == Op_AddL && in1->in(2) == in2->in(2) ) {
      assert(in1->in(1) != this && in2->in(1) != this,"dead loop in AddLNode::Ideal");
      return new (phase->C, 3) AddLNode(in1->in(1), in2->in(1));
    }
    // Convert "(a-b)+(b-c)" into "(a-c)"
    if( op2 == Op_SubL && in1->in(2) == in2->in(1) ) {
      assert(in1->in(1) != this && in2->in(2) != this,"dead loop in AddLNode::Ideal");
      return new (phase->C, 3) SubLNode(in1->in(1), in2->in(2));
    }
    // Convert "(a-b)+(c-a)" into "(c-b)"
    if( op2 == Op_SubL && in1->in(1) == in1->in(2) ) {
      assert(in1->in(2) != this && in2->in(1) != this,"dead loop in AddLNode::Ideal");
      return new (phase->C, 3) SubLNode(in2->in(1), in1->in(2));
    }
  }

  // Convert "x+(0-y)" into "(x-y)"
  if( op2 == Op_SubL && phase->type(in2->in(1)) == TypeLong::ZERO )
    return new (phase->C, 3) SubLNode( in1, in2->in(2) );

  // Convert "(0-y)+x" into "(x-y)"
  if( op1 == Op_SubL && phase->type(in1->in(1)) == TypeInt::ZERO )
    return new (phase->C, 3) SubLNode( in2, in1->in(2) );

  // Convert "X+X+X+X+X...+X+Y" into "k*X+Y" or really convert "X+(X+Y)"
  // into "(X<<1)+Y" and let shift-folding happen.
  if( op2 == Op_AddL &&
      in2->in(1) == in1 &&
      op1 != Op_ConL &&
      0 ) {
    Node *shift = phase->transform(new (phase->C, 3) LShiftLNode(in1,phase->intcon(1)));
    return new (phase->C, 3) AddLNode(shift,in2->in(2));
  }

  return AddNode::Ideal(phase, can_reshape);
}


//------------------------------Identity---------------------------------------
// Fold (x-y)+y  OR  y+(x-y)  into  x
Node *AddLNode::Identity( PhaseTransform *phase ) {
  if( in(1)->Opcode() == Op_SubL && phase->eqv(in(1)->in(2),in(2)) ) {
    return in(1)->in(1);
  }
  else if( in(2)->Opcode() == Op_SubL && phase->eqv(in(2)->in(2),in(1)) ) {
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
  jlong lo = r0->_lo + r1->_lo;
  jlong hi = r0->_hi + r1->_hi;
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

  return NULL;
}

//------------------------------add_ring---------------------------------------
// Supplied function returns the sum of the inputs.
// This also type-checks the inputs for sanity.  Guaranteed never to
// be passed a TOP or BOTTOM type, these are filtered out by pre-check.
const Type *AddFNode::add_ring( const Type *t0, const Type *t1 ) const {
  // We must be adding 2 float constants.
  return TypeF::make( t0->getf() + t1->getf() );
}

//------------------------------Ideal------------------------------------------
Node *AddFNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if( IdealizedNumerics && !phase->C->method()->is_strict() ) {
    return AddNode::Ideal(phase, can_reshape); // commutative and associative transforms
  }

  // Floating point additions are not associative because of boundary conditions (infinity)
  return commute(this,
                 phase->type( in(1) )->singleton(),
                 phase->type( in(2) )->singleton() ) ? this : NULL;
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

  return NULL;
}
//------------------------------add_ring---------------------------------------
// Supplied function returns the sum of the inputs.
// This also type-checks the inputs for sanity.  Guaranteed never to
// be passed a TOP or BOTTOM type, these are filtered out by pre-check.
const Type *AddDNode::add_ring( const Type *t0, const Type *t1 ) const {
  // We must be adding 2 double constants.
  return TypeD::make( t0->getd() + t1->getd() );
}

//------------------------------Ideal------------------------------------------
Node *AddDNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if( IdealizedNumerics && !phase->C->method()->is_strict() ) {
    return AddNode::Ideal(phase, can_reshape); // commutative and associative transforms
  }

  // Floating point additions are not associative because of boundary conditions (infinity)
  return commute(this,
                 phase->type( in(1) )->singleton(),
                 phase->type( in(2) )->singleton() ) ? this : NULL;
}


//=============================================================================
//------------------------------Identity---------------------------------------
// If one input is a constant 0, return the other input.
Node *AddPNode::Identity( PhaseTransform *phase ) {
  return ( phase->type( in(Offset) )->higher_equal( TypeX_ZERO ) ) ? in(Address) : this;
}

//------------------------------Idealize---------------------------------------
Node *AddPNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Bail out if dead inputs
  if( phase->type( in(Address) ) == Type::TOP ) return NULL;

  // If the left input is an add of a constant, flatten the expression tree.
  const Node *n = in(Address);
  if (n->is_AddP() && n->in(Base) == in(Base)) {
    const AddPNode *addp = n->as_AddP(); // Left input is an AddP
    assert( !addp->in(Address)->is_AddP() ||
             addp->in(Address)->as_AddP() != addp,
            "dead loop in AddPNode::Ideal" );
    // Type of left input's right input
    const Type *t = phase->type( addp->in(Offset) );
    if( t == Type::TOP ) return NULL;
    const TypeX *t12 = t->is_intptr_t();
    if( t12->is_con() ) {       // Left input is an add of a constant?
      // If the right input is a constant, combine constants
      const Type *temp_t2 = phase->type( in(Offset) );
      if( temp_t2 == Type::TOP ) return NULL;
      const TypeX *t2 = temp_t2->is_intptr_t();
      Node* address;
      Node* offset;
      if( t2->is_con() ) {
        // The Add of the flattened expression
        address = addp->in(Address);
        offset  = phase->MakeConX(t2->get_con() + t12->get_con());
      } else {
        // Else move the constant to the right.  ((A+con)+B) into ((A+B)+con)
        address = phase->transform(new (phase->C, 4) AddPNode(in(Base),addp->in(Address),in(Offset)));
        offset  = addp->in(Offset);
      }
      PhaseIterGVN *igvn = phase->is_IterGVN();
      if( igvn ) {
        set_req_X(Address,address,igvn);
        set_req_X(Offset,offset,igvn);
      } else {
        set_req(Address,address);
        set_req(Offset,offset);
      }
      return this;
    }
  }

  // Raw pointers?
  if( in(Base)->bottom_type() == Type::TOP ) {
    // If this is a NULL+long form (from unsafe accesses), switch to a rawptr.
    if (phase->type(in(Address)) == TypePtr::NULL_PTR) {
      Node* offset = in(Offset);
      return new (phase->C, 2) CastX2PNode(offset);
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
      set_req(Address, phase->transform(new (phase->C, 4) AddPNode(in(Base),in(Address),add->in(1))));
      set_req(Offset, add->in(2));
      return this;              // Made progress
    }
  }

  return NULL;                  // No progress
}

//------------------------------bottom_type------------------------------------
// Bottom-type is the pointer-type with unknown offset.
const Type *AddPNode::bottom_type() const {
  if (in(Address) == NULL)  return TypePtr::BOTTOM;
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
const Type *AddPNode::Value( PhaseTransform *phase ) const {
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
// Return the base, or NULL if failure.
Node* AddPNode::Ideal_base_and_offset(Node* ptr, PhaseTransform* phase,
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
  return NULL;
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
  return count;
}

//------------------------------match_edge-------------------------------------
// Do we Match on this edge index or not?  Do not match base pointer edge
uint AddPNode::match_edge(uint idx) const {
  return idx > Base;
}

//---------------------------mach_bottom_type----------------------------------
// Utility function for use by ADLC.  Implements bottom_type for matched AddP.
const Type *AddPNode::mach_bottom_type( const MachNode* n) {
  Node* base = n->in(Base);
  const Type *t = base->bottom_type();
  if ( t == Type::TOP ) {
    // an untyped pointer
    return TypeRawPtr::BOTTOM;
  }
  const TypePtr* tp = t->isa_oopptr();
  if ( tp == NULL )  return t;
  if ( tp->_offset == TypePtr::OffsetBot )  return tp;

  // We must carefully add up the various offsets...
  intptr_t offset = 0;
  const TypePtr* tptr = NULL;

  uint numopnds = n->num_opnds();
  uint index = n->oper_input_base();
  for ( uint i = 1; i < numopnds; i++ ) {
    MachOper *opnd = n->_opnds[i];
    // Check for any interesting operand info.
    // In particular, check for both memory and non-memory operands.
    // %%%%% Clean this up: use xadd_offset
    intptr_t con = opnd->constant();
    if ( con == TypePtr::OffsetBot )  goto bottom_out;
    offset += con;
    con = opnd->constant_disp();
    if ( con == TypePtr::OffsetBot )  goto bottom_out;
    offset += con;
    if( opnd->scale() != 0 ) goto bottom_out;

    // Check each operand input edge.  Find the 1 allowed pointer
    // edge.  Other edges must be index edges; track exact constant
    // inputs and otherwise assume the worst.
    for ( uint j = opnd->num_edges(); j > 0; j-- ) {
      Node* edge = n->in(index++);
      const Type*    et  = edge->bottom_type();
      const TypeX*   eti = et->isa_intptr_t();
      if ( eti == NULL ) {
        // there must be one pointer among the operands
        guarantee(tptr == NULL, "must be only one pointer operand");
        if (UseCompressedOops && Universe::narrow_oop_shift() == 0) {
          // 32-bits narrow oop can be the base of address expressions
          tptr = et->make_ptr()->isa_oopptr();
        } else {
          // only regular oops are expected here
          tptr = et->isa_oopptr();
        }
        guarantee(tptr != NULL, "non-int operand must be pointer");
        if (tptr->higher_equal(tp->add_offset(tptr->offset())))
          tp = tptr; // Set more precise type for bailout
        continue;
      }
      if ( eti->_hi != eti->_lo )  goto bottom_out;
      offset += eti->_lo;
    }
  }
  guarantee(tptr != NULL, "must be exactly one pointer operand");
  return tptr->add_offset(offset);

 bottom_out:
  return tp->add_offset(TypePtr::OffsetBot);
}

//=============================================================================
//------------------------------Identity---------------------------------------
Node *OrINode::Identity( PhaseTransform *phase ) {
  // x | x => x
  if (phase->eqv(in(1), in(2))) {
    return in(1);
  }

  return AddNode::Identity(phase);
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
Node *OrLNode::Identity( PhaseTransform *phase ) {
  // x | x => x
  if (phase->eqv(in(1), in(2))) {
    return in(1);
  }

  return AddNode::Identity(phase);
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

//=============================================================================
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
Node *MinINode::Ideal(PhaseGVN *phase, bool can_reshape) {
  Node *progress = NULL;
  // Force a right-spline graph
  Node *l = in(1);
  Node *r = in(2);
  // Transform  MinI1( MinI2(a,b), c)  into  MinI1( a, MinI2(b,c) )
  // to force a right-spline graph for the rest of MinINode::Ideal().
  if( l->Opcode() == Op_MinI ) {
    assert( l != l->in(1), "dead loop in MinINode::Ideal" );
    r = phase->transform(new (phase->C, 3) MinINode(l->in(2),r));
    l = l->in(1);
    set_req(1, l);
    set_req(2, r);
    return this;
  }

  // Get left input & constant
  Node *x = l;
  int x_off = 0;
  if( x->Opcode() == Op_AddI && // Check for "x+c0" and collect constant
      x->in(2)->is_Con() ) {
    const Type *t = x->in(2)->bottom_type();
    if( t == Type::TOP ) return NULL;  // No progress
    x_off = t->is_int()->get_con();
    x = x->in(1);
  }

  // Scan a right-spline-tree for MINs
  Node *y = r;
  int y_off = 0;
  // Check final part of MIN tree
  if( y->Opcode() == Op_AddI && // Check for "y+c1" and collect constant
      y->in(2)->is_Con() ) {
    const Type *t = y->in(2)->bottom_type();
    if( t == Type::TOP ) return NULL;  // No progress
    y_off = t->is_int()->get_con();
    y = y->in(1);
  }
  if( x->_idx > y->_idx && r->Opcode() != Op_MinI ) {
    swap_edges(1, 2);
    return this;
  }


  if( r->Opcode() == Op_MinI ) {
    assert( r != r->in(2), "dead loop in MinINode::Ideal" );
    y = r->in(1);
    // Check final part of MIN tree
    if( y->Opcode() == Op_AddI &&// Check for "y+c1" and collect constant
        y->in(2)->is_Con() ) {
      const Type *t = y->in(2)->bottom_type();
      if( t == Type::TOP ) return NULL;  // No progress
      y_off = t->is_int()->get_con();
      y = y->in(1);
    }

    if( x->_idx > y->_idx )
      return new (phase->C, 3) MinINode(r->in(1),phase->transform(new (phase->C, 3) MinINode(l,r->in(2))));

    // See if covers: MIN2(x+c0,MIN2(y+c1,z))
    if( !phase->eqv(x,y) ) return NULL;
    // If (y == x) transform MIN2(x+c0, MIN2(x+c1,z)) into
    // MIN2(x+c0 or x+c1 which less, z).
    return new (phase->C, 3) MinINode(phase->transform(new (phase->C, 3) AddINode(x,phase->intcon(MIN2(x_off,y_off)))),r->in(2));
  } else {
    // See if covers: MIN2(x+c0,y+c1)
    if( !phase->eqv(x,y) ) return NULL;
    // If (y == x) transform MIN2(x+c0,x+c1) into x+c0 or x+c1 which less.
    return new (phase->C, 3) AddINode(x,phase->intcon(MIN2(x_off,y_off)));
  }

}

//------------------------------add_ring---------------------------------------
// Supplied function returns the sum of the inputs.
const Type *MinINode::add_ring( const Type *t0, const Type *t1 ) const {
  const TypeInt *r0 = t0->is_int(); // Handy access
  const TypeInt *r1 = t1->is_int();

  // Otherwise just MIN them bits.
  return TypeInt::make( MIN2(r0->_lo,r1->_lo), MIN2(r0->_hi,r1->_hi), MAX2(r0->_widen,r1->_widen) );
}
