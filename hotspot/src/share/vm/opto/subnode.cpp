/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "compiler/compileLog.hpp"
#include "memory/allocation.inline.hpp"
#include "opto/addnode.hpp"
#include "opto/callnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/connode.hpp"
#include "opto/loopnode.hpp"
#include "opto/matcher.hpp"
#include "opto/mulnode.hpp"
#include "opto/opcodes.hpp"
#include "opto/phaseX.hpp"
#include "opto/subnode.hpp"
#include "runtime/sharedRuntime.hpp"

// Portions of code courtesy of Clifford Click

// Optimization - Graph Style

#include "math.h"

//=============================================================================
//------------------------------Identity---------------------------------------
// If right input is a constant 0, return the left input.
Node *SubNode::Identity( PhaseTransform *phase ) {
  assert(in(1) != this, "Must already have called Value");
  assert(in(2) != this, "Must already have called Value");

  // Remove double negation
  const Type *zero = add_id();
  if( phase->type( in(1) )->higher_equal( zero ) &&
      in(2)->Opcode() == Opcode() &&
      phase->type( in(2)->in(1) )->higher_equal( zero ) ) {
    return in(2)->in(2);
  }

  // Convert "(X+Y) - Y" into X and "(X+Y) - X" into Y
  if( in(1)->Opcode() == Op_AddI ) {
    if( phase->eqv(in(1)->in(2),in(2)) )
      return in(1)->in(1);
    if (phase->eqv(in(1)->in(1),in(2)))
      return in(1)->in(2);

    // Also catch: "(X + Opaque2(Y)) - Y".  In this case, 'Y' is a loop-varying
    // trip counter and X is likely to be loop-invariant (that's how O2 Nodes
    // are originally used, although the optimizer sometimes jiggers things).
    // This folding through an O2 removes a loop-exit use of a loop-varying
    // value and generally lowers register pressure in and around the loop.
    if( in(1)->in(2)->Opcode() == Op_Opaque2 &&
        phase->eqv(in(1)->in(2)->in(1),in(2)) )
      return in(1)->in(1);
  }

  return ( phase->type( in(2) )->higher_equal( zero ) ) ? in(1) : this;
}

//------------------------------Value------------------------------------------
// A subtract node differences it's two inputs.
const Type *SubNode::Value( PhaseTransform *phase ) const {
  const Node* in1 = in(1);
  const Node* in2 = in(2);
  // Either input is TOP ==> the result is TOP
  const Type* t1 = (in1 == this) ? Type::TOP : phase->type(in1);
  if( t1 == Type::TOP ) return Type::TOP;
  const Type* t2 = (in2 == this) ? Type::TOP : phase->type(in2);
  if( t2 == Type::TOP ) return Type::TOP;

  // Not correct for SubFnode and AddFNode (must check for infinity)
  // Equal?  Subtract is zero
  if (phase->eqv_uncast(in1, in2))  return add_id();

  // Either input is BOTTOM ==> the result is the local BOTTOM
  if( t1 == Type::BOTTOM || t2 == Type::BOTTOM )
    return bottom_type();

  return sub(t1,t2);            // Local flavor of type subtraction

}

//=============================================================================

//------------------------------Helper function--------------------------------
static bool ok_to_convert(Node* inc, Node* iv) {
    // Do not collapse (x+c0)-y if "+" is a loop increment, because the
    // "-" is loop invariant and collapsing extends the live-range of "x"
    // to overlap with the "+", forcing another register to be used in
    // the loop.
    // This test will be clearer with '&&' (apply DeMorgan's rule)
    // but I like the early cutouts that happen here.
    const PhiNode *phi;
    if( ( !inc->in(1)->is_Phi() ||
          !(phi=inc->in(1)->as_Phi()) ||
          phi->is_copy() ||
          !phi->region()->is_CountedLoop() ||
          inc != phi->region()->as_CountedLoop()->incr() )
       &&
        // Do not collapse (x+c0)-iv if "iv" is a loop induction variable,
        // because "x" maybe invariant.
        ( !iv->is_loop_iv() )
      ) {
      return true;
    } else {
      return false;
    }
}
//------------------------------Ideal------------------------------------------
Node *SubINode::Ideal(PhaseGVN *phase, bool can_reshape){
  Node *in1 = in(1);
  Node *in2 = in(2);
  uint op1 = in1->Opcode();
  uint op2 = in2->Opcode();

#ifdef ASSERT
  // Check for dead loop
  if( phase->eqv( in1, this ) || phase->eqv( in2, this ) ||
      ( op1 == Op_AddI || op1 == Op_SubI ) &&
      ( phase->eqv( in1->in(1), this ) || phase->eqv( in1->in(2), this ) ||
        phase->eqv( in1->in(1), in1  ) || phase->eqv( in1->in(2), in1 ) ) )
    assert(false, "dead loop in SubINode::Ideal");
#endif

  const Type *t2 = phase->type( in2 );
  if( t2 == Type::TOP ) return NULL;
  // Convert "x-c0" into "x+ -c0".
  if( t2->base() == Type::Int ){        // Might be bottom or top...
    const TypeInt *i = t2->is_int();
    if( i->is_con() )
      return new (phase->C, 3) AddINode(in1, phase->intcon(-i->get_con()));
  }

  // Convert "(x+c0) - y" into (x-y) + c0"
  // Do not collapse (x+c0)-y if "+" is a loop increment or
  // if "y" is a loop induction variable.
  if( op1 == Op_AddI && ok_to_convert(in1, in2) ) {
    const Type *tadd = phase->type( in1->in(2) );
    if( tadd->singleton() && tadd != Type::TOP ) {
      Node *sub2 = phase->transform( new (phase->C, 3) SubINode( in1->in(1), in2 ));
      return new (phase->C, 3) AddINode( sub2, in1->in(2) );
    }
  }


  // Convert "x - (y+c0)" into "(x-y) - c0"
  // Need the same check as in above optimization but reversed.
  if (op2 == Op_AddI && ok_to_convert(in2, in1)) {
    Node* in21 = in2->in(1);
    Node* in22 = in2->in(2);
    const TypeInt* tcon = phase->type(in22)->isa_int();
    if (tcon != NULL && tcon->is_con()) {
      Node* sub2 = phase->transform( new (phase->C, 3) SubINode(in1, in21) );
      Node* neg_c0 = phase->intcon(- tcon->get_con());
      return new (phase->C, 3) AddINode(sub2, neg_c0);
    }
  }

  const Type *t1 = phase->type( in1 );
  if( t1 == Type::TOP ) return NULL;

#ifdef ASSERT
  // Check for dead loop
  if( ( op2 == Op_AddI || op2 == Op_SubI ) &&
      ( phase->eqv( in2->in(1), this ) || phase->eqv( in2->in(2), this ) ||
        phase->eqv( in2->in(1), in2  ) || phase->eqv( in2->in(2), in2  ) ) )
    assert(false, "dead loop in SubINode::Ideal");
#endif

  // Convert "x - (x+y)" into "-y"
  if( op2 == Op_AddI &&
      phase->eqv( in1, in2->in(1) ) )
    return new (phase->C, 3) SubINode( phase->intcon(0),in2->in(2));
  // Convert "(x-y) - x" into "-y"
  if( op1 == Op_SubI &&
      phase->eqv( in1->in(1), in2 ) )
    return new (phase->C, 3) SubINode( phase->intcon(0),in1->in(2));
  // Convert "x - (y+x)" into "-y"
  if( op2 == Op_AddI &&
      phase->eqv( in1, in2->in(2) ) )
    return new (phase->C, 3) SubINode( phase->intcon(0),in2->in(1));

  // Convert "0 - (x-y)" into "y-x"
  if( t1 == TypeInt::ZERO && op2 == Op_SubI )
    return new (phase->C, 3) SubINode( in2->in(2), in2->in(1) );

  // Convert "0 - (x+con)" into "-con-x"
  jint con;
  if( t1 == TypeInt::ZERO && op2 == Op_AddI &&
      (con = in2->in(2)->find_int_con(0)) != 0 )
    return new (phase->C, 3) SubINode( phase->intcon(-con), in2->in(1) );

  // Convert "(X+A) - (X+B)" into "A - B"
  if( op1 == Op_AddI && op2 == Op_AddI && in1->in(1) == in2->in(1) )
    return new (phase->C, 3) SubINode( in1->in(2), in2->in(2) );

  // Convert "(A+X) - (B+X)" into "A - B"
  if( op1 == Op_AddI && op2 == Op_AddI && in1->in(2) == in2->in(2) )
    return new (phase->C, 3) SubINode( in1->in(1), in2->in(1) );

  // Convert "(A+X) - (X+B)" into "A - B"
  if( op1 == Op_AddI && op2 == Op_AddI && in1->in(2) == in2->in(1) )
    return new (phase->C, 3) SubINode( in1->in(1), in2->in(2) );

  // Convert "(X+A) - (B+X)" into "A - B"
  if( op1 == Op_AddI && op2 == Op_AddI && in1->in(1) == in2->in(2) )
    return new (phase->C, 3) SubINode( in1->in(2), in2->in(1) );

  // Convert "A-(B-C)" into (A+C)-B", since add is commutative and generally
  // nicer to optimize than subtract.
  if( op2 == Op_SubI && in2->outcnt() == 1) {
    Node *add1 = phase->transform( new (phase->C, 3) AddINode( in1, in2->in(2) ) );
    return new (phase->C, 3) SubINode( add1, in2->in(1) );
  }

  return NULL;
}

//------------------------------sub--------------------------------------------
// A subtract node differences it's two inputs.
const Type *SubINode::sub( const Type *t1, const Type *t2 ) const {
  const TypeInt *r0 = t1->is_int(); // Handy access
  const TypeInt *r1 = t2->is_int();
  int32 lo = r0->_lo - r1->_hi;
  int32 hi = r0->_hi - r1->_lo;

  // We next check for 32-bit overflow.
  // If that happens, we just assume all integers are possible.
  if( (((r0->_lo ^ r1->_hi) >= 0) ||    // lo ends have same signs OR
       ((r0->_lo ^      lo) >= 0)) &&   // lo results have same signs AND
      (((r0->_hi ^ r1->_lo) >= 0) ||    // hi ends have same signs OR
       ((r0->_hi ^      hi) >= 0)) )    // hi results have same signs
    return TypeInt::make(lo,hi,MAX2(r0->_widen,r1->_widen));
  else                          // Overflow; assume all integers
    return TypeInt::INT;
}

//=============================================================================
//------------------------------Ideal------------------------------------------
Node *SubLNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  Node *in1 = in(1);
  Node *in2 = in(2);
  uint op1 = in1->Opcode();
  uint op2 = in2->Opcode();

#ifdef ASSERT
  // Check for dead loop
  if( phase->eqv( in1, this ) || phase->eqv( in2, this ) ||
      ( op1 == Op_AddL || op1 == Op_SubL ) &&
      ( phase->eqv( in1->in(1), this ) || phase->eqv( in1->in(2), this ) ||
        phase->eqv( in1->in(1), in1  ) || phase->eqv( in1->in(2), in1  ) ) )
    assert(false, "dead loop in SubLNode::Ideal");
#endif

  if( phase->type( in2 ) == Type::TOP ) return NULL;
  const TypeLong *i = phase->type( in2 )->isa_long();
  // Convert "x-c0" into "x+ -c0".
  if( i &&                      // Might be bottom or top...
      i->is_con() )
    return new (phase->C, 3) AddLNode(in1, phase->longcon(-i->get_con()));

  // Convert "(x+c0) - y" into (x-y) + c0"
  // Do not collapse (x+c0)-y if "+" is a loop increment or
  // if "y" is a loop induction variable.
  if( op1 == Op_AddL && ok_to_convert(in1, in2) ) {
    Node *in11 = in1->in(1);
    const Type *tadd = phase->type( in1->in(2) );
    if( tadd->singleton() && tadd != Type::TOP ) {
      Node *sub2 = phase->transform( new (phase->C, 3) SubLNode( in11, in2 ));
      return new (phase->C, 3) AddLNode( sub2, in1->in(2) );
    }
  }

  // Convert "x - (y+c0)" into "(x-y) - c0"
  // Need the same check as in above optimization but reversed.
  if (op2 == Op_AddL && ok_to_convert(in2, in1)) {
    Node* in21 = in2->in(1);
    Node* in22 = in2->in(2);
    const TypeLong* tcon = phase->type(in22)->isa_long();
    if (tcon != NULL && tcon->is_con()) {
      Node* sub2 = phase->transform( new (phase->C, 3) SubLNode(in1, in21) );
      Node* neg_c0 = phase->longcon(- tcon->get_con());
      return new (phase->C, 3) AddLNode(sub2, neg_c0);
    }
  }

  const Type *t1 = phase->type( in1 );
  if( t1 == Type::TOP ) return NULL;

#ifdef ASSERT
  // Check for dead loop
  if( ( op2 == Op_AddL || op2 == Op_SubL ) &&
      ( phase->eqv( in2->in(1), this ) || phase->eqv( in2->in(2), this ) ||
        phase->eqv( in2->in(1), in2  ) || phase->eqv( in2->in(2), in2  ) ) )
    assert(false, "dead loop in SubLNode::Ideal");
#endif

  // Convert "x - (x+y)" into "-y"
  if( op2 == Op_AddL &&
      phase->eqv( in1, in2->in(1) ) )
    return new (phase->C, 3) SubLNode( phase->makecon(TypeLong::ZERO), in2->in(2));
  // Convert "x - (y+x)" into "-y"
  if( op2 == Op_AddL &&
      phase->eqv( in1, in2->in(2) ) )
    return new (phase->C, 3) SubLNode( phase->makecon(TypeLong::ZERO),in2->in(1));

  // Convert "0 - (x-y)" into "y-x"
  if( phase->type( in1 ) == TypeLong::ZERO && op2 == Op_SubL )
    return new (phase->C, 3) SubLNode( in2->in(2), in2->in(1) );

  // Convert "(X+A) - (X+B)" into "A - B"
  if( op1 == Op_AddL && op2 == Op_AddL && in1->in(1) == in2->in(1) )
    return new (phase->C, 3) SubLNode( in1->in(2), in2->in(2) );

  // Convert "(A+X) - (B+X)" into "A - B"
  if( op1 == Op_AddL && op2 == Op_AddL && in1->in(2) == in2->in(2) )
    return new (phase->C, 3) SubLNode( in1->in(1), in2->in(1) );

  // Convert "A-(B-C)" into (A+C)-B"
  if( op2 == Op_SubL && in2->outcnt() == 1) {
    Node *add1 = phase->transform( new (phase->C, 3) AddLNode( in1, in2->in(2) ) );
    return new (phase->C, 3) SubLNode( add1, in2->in(1) );
  }

  return NULL;
}

//------------------------------sub--------------------------------------------
// A subtract node differences it's two inputs.
const Type *SubLNode::sub( const Type *t1, const Type *t2 ) const {
  const TypeLong *r0 = t1->is_long(); // Handy access
  const TypeLong *r1 = t2->is_long();
  jlong lo = r0->_lo - r1->_hi;
  jlong hi = r0->_hi - r1->_lo;

  // We next check for 32-bit overflow.
  // If that happens, we just assume all integers are possible.
  if( (((r0->_lo ^ r1->_hi) >= 0) ||    // lo ends have same signs OR
       ((r0->_lo ^      lo) >= 0)) &&   // lo results have same signs AND
      (((r0->_hi ^ r1->_lo) >= 0) ||    // hi ends have same signs OR
       ((r0->_hi ^      hi) >= 0)) )    // hi results have same signs
    return TypeLong::make(lo,hi,MAX2(r0->_widen,r1->_widen));
  else                          // Overflow; assume all integers
    return TypeLong::LONG;
}

//=============================================================================
//------------------------------Value------------------------------------------
// A subtract node differences its two inputs.
const Type *SubFPNode::Value( PhaseTransform *phase ) const {
  const Node* in1 = in(1);
  const Node* in2 = in(2);
  // Either input is TOP ==> the result is TOP
  const Type* t1 = (in1 == this) ? Type::TOP : phase->type(in1);
  if( t1 == Type::TOP ) return Type::TOP;
  const Type* t2 = (in2 == this) ? Type::TOP : phase->type(in2);
  if( t2 == Type::TOP ) return Type::TOP;

  // if both operands are infinity of same sign, the result is NaN; do
  // not replace with zero
  if( (t1->is_finite() && t2->is_finite()) ) {
    if( phase->eqv(in1, in2) ) return add_id();
  }

  // Either input is BOTTOM ==> the result is the local BOTTOM
  const Type *bot = bottom_type();
  if( (t1 == bot) || (t2 == bot) ||
      (t1 == Type::BOTTOM) || (t2 == Type::BOTTOM) )
    return bot;

  return sub(t1,t2);            // Local flavor of type subtraction
}


//=============================================================================
//------------------------------Ideal------------------------------------------
Node *SubFNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  const Type *t2 = phase->type( in(2) );
  // Convert "x-c0" into "x+ -c0".
  if( t2->base() == Type::FloatCon ) {  // Might be bottom or top...
    // return new (phase->C, 3) AddFNode(in(1), phase->makecon( TypeF::make(-t2->getf()) ) );
  }

  // Not associative because of boundary conditions (infinity)
  if( IdealizedNumerics && !phase->C->method()->is_strict() ) {
    // Convert "x - (x+y)" into "-y"
    if( in(2)->is_Add() &&
        phase->eqv(in(1),in(2)->in(1) ) )
      return new (phase->C, 3) SubFNode( phase->makecon(TypeF::ZERO),in(2)->in(2));
  }

  // Cannot replace 0.0-X with -X because a 'fsub' bytecode computes
  // 0.0-0.0 as +0.0, while a 'fneg' bytecode computes -0.0.
  //if( phase->type(in(1)) == TypeF::ZERO )
  //return new (phase->C, 2) NegFNode(in(2));

  return NULL;
}

//------------------------------sub--------------------------------------------
// A subtract node differences its two inputs.
const Type *SubFNode::sub( const Type *t1, const Type *t2 ) const {
  // no folding if one of operands is infinity or NaN, do not do constant folding
  if( g_isfinite(t1->getf()) && g_isfinite(t2->getf()) ) {
    return TypeF::make( t1->getf() - t2->getf() );
  }
  else if( g_isnan(t1->getf()) ) {
    return t1;
  }
  else if( g_isnan(t2->getf()) ) {
    return t2;
  }
  else {
    return Type::FLOAT;
  }
}

//=============================================================================
//------------------------------Ideal------------------------------------------
Node *SubDNode::Ideal(PhaseGVN *phase, bool can_reshape){
  const Type *t2 = phase->type( in(2) );
  // Convert "x-c0" into "x+ -c0".
  if( t2->base() == Type::DoubleCon ) { // Might be bottom or top...
    // return new (phase->C, 3) AddDNode(in(1), phase->makecon( TypeD::make(-t2->getd()) ) );
  }

  // Not associative because of boundary conditions (infinity)
  if( IdealizedNumerics && !phase->C->method()->is_strict() ) {
    // Convert "x - (x+y)" into "-y"
    if( in(2)->is_Add() &&
        phase->eqv(in(1),in(2)->in(1) ) )
      return new (phase->C, 3) SubDNode( phase->makecon(TypeD::ZERO),in(2)->in(2));
  }

  // Cannot replace 0.0-X with -X because a 'dsub' bytecode computes
  // 0.0-0.0 as +0.0, while a 'dneg' bytecode computes -0.0.
  //if( phase->type(in(1)) == TypeD::ZERO )
  //return new (phase->C, 2) NegDNode(in(2));

  return NULL;
}

//------------------------------sub--------------------------------------------
// A subtract node differences its two inputs.
const Type *SubDNode::sub( const Type *t1, const Type *t2 ) const {
  // no folding if one of operands is infinity or NaN, do not do constant folding
  if( g_isfinite(t1->getd()) && g_isfinite(t2->getd()) ) {
    return TypeD::make( t1->getd() - t2->getd() );
  }
  else if( g_isnan(t1->getd()) ) {
    return t1;
  }
  else if( g_isnan(t2->getd()) ) {
    return t2;
  }
  else {
    return Type::DOUBLE;
  }
}

//=============================================================================
//------------------------------Idealize---------------------------------------
// Unlike SubNodes, compare must still flatten return value to the
// range -1, 0, 1.
// And optimizations like those for (X + Y) - X fail if overflow happens.
Node *CmpNode::Identity( PhaseTransform *phase ) {
  return this;
}

//=============================================================================
//------------------------------cmp--------------------------------------------
// Simplify a CmpI (compare 2 integers) node, based on local information.
// If both inputs are constants, compare them.
const Type *CmpINode::sub( const Type *t1, const Type *t2 ) const {
  const TypeInt *r0 = t1->is_int(); // Handy access
  const TypeInt *r1 = t2->is_int();

  if( r0->_hi < r1->_lo )       // Range is always low?
    return TypeInt::CC_LT;
  else if( r0->_lo > r1->_hi )  // Range is always high?
    return TypeInt::CC_GT;

  else if( r0->is_con() && r1->is_con() ) { // comparing constants?
    assert(r0->get_con() == r1->get_con(), "must be equal");
    return TypeInt::CC_EQ;      // Equal results.
  } else if( r0->_hi == r1->_lo ) // Range is never high?
    return TypeInt::CC_LE;
  else if( r0->_lo == r1->_hi ) // Range is never low?
    return TypeInt::CC_GE;
  return TypeInt::CC;           // else use worst case results
}

// Simplify a CmpU (compare 2 integers) node, based on local information.
// If both inputs are constants, compare them.
const Type *CmpUNode::sub( const Type *t1, const Type *t2 ) const {
  assert(!t1->isa_ptr(), "obsolete usage of CmpU");

  // comparing two unsigned ints
  const TypeInt *r0 = t1->is_int();   // Handy access
  const TypeInt *r1 = t2->is_int();

  // Current installed version
  // Compare ranges for non-overlap
  juint lo0 = r0->_lo;
  juint hi0 = r0->_hi;
  juint lo1 = r1->_lo;
  juint hi1 = r1->_hi;

  // If either one has both negative and positive values,
  // it therefore contains both 0 and -1, and since [0..-1] is the
  // full unsigned range, the type must act as an unsigned bottom.
  bool bot0 = ((jint)(lo0 ^ hi0) < 0);
  bool bot1 = ((jint)(lo1 ^ hi1) < 0);

  if (bot0 || bot1) {
    // All unsigned values are LE -1 and GE 0.
    if (lo0 == 0 && hi0 == 0) {
      return TypeInt::CC_LE;            //   0 <= bot
    } else if (lo1 == 0 && hi1 == 0) {
      return TypeInt::CC_GE;            // bot >= 0
    }
  } else {
    // We can use ranges of the form [lo..hi] if signs are the same.
    assert(lo0 <= hi0 && lo1 <= hi1, "unsigned ranges are valid");
    // results are reversed, '-' > '+' for unsigned compare
    if (hi0 < lo1) {
      return TypeInt::CC_LT;            // smaller
    } else if (lo0 > hi1) {
      return TypeInt::CC_GT;            // greater
    } else if (hi0 == lo1 && lo0 == hi1) {
      return TypeInt::CC_EQ;            // Equal results
    } else if (lo0 >= hi1) {
      return TypeInt::CC_GE;
    } else if (hi0 <= lo1) {
      // Check for special case in Hashtable::get.  (See below.)
      if ((jint)lo0 >= 0 && (jint)lo1 >= 0 &&
          in(1)->Opcode() == Op_ModI &&
          in(1)->in(2) == in(2) )
        return TypeInt::CC_LT;
      return TypeInt::CC_LE;
    }
  }
  // Check for special case in Hashtable::get - the hash index is
  // mod'ed to the table size so the following range check is useless.
  // Check for: (X Mod Y) CmpU Y, where the mod result and Y both have
  // to be positive.
  // (This is a gross hack, since the sub method never
  // looks at the structure of the node in any other case.)
  if ((jint)lo0 >= 0 && (jint)lo1 >= 0 &&
      in(1)->Opcode() == Op_ModI &&
      in(1)->in(2)->uncast() == in(2)->uncast())
    return TypeInt::CC_LT;
  return TypeInt::CC;                   // else use worst case results
}

//------------------------------Idealize---------------------------------------
Node *CmpINode::Ideal( PhaseGVN *phase, bool can_reshape ) {
  if (phase->type(in(2))->higher_equal(TypeInt::ZERO)) {
    switch (in(1)->Opcode()) {
    case Op_CmpL3:              // Collapse a CmpL3/CmpI into a CmpL
      return new (phase->C, 3) CmpLNode(in(1)->in(1),in(1)->in(2));
    case Op_CmpF3:              // Collapse a CmpF3/CmpI into a CmpF
      return new (phase->C, 3) CmpFNode(in(1)->in(1),in(1)->in(2));
    case Op_CmpD3:              // Collapse a CmpD3/CmpI into a CmpD
      return new (phase->C, 3) CmpDNode(in(1)->in(1),in(1)->in(2));
    //case Op_SubI:
      // If (x - y) cannot overflow, then ((x - y) <?> 0)
      // can be turned into (x <?> y).
      // This is handled (with more general cases) by Ideal_sub_algebra.
    }
  }
  return NULL;                  // No change
}


//=============================================================================
// Simplify a CmpL (compare 2 longs ) node, based on local information.
// If both inputs are constants, compare them.
const Type *CmpLNode::sub( const Type *t1, const Type *t2 ) const {
  const TypeLong *r0 = t1->is_long(); // Handy access
  const TypeLong *r1 = t2->is_long();

  if( r0->_hi < r1->_lo )       // Range is always low?
    return TypeInt::CC_LT;
  else if( r0->_lo > r1->_hi )  // Range is always high?
    return TypeInt::CC_GT;

  else if( r0->is_con() && r1->is_con() ) { // comparing constants?
    assert(r0->get_con() == r1->get_con(), "must be equal");
    return TypeInt::CC_EQ;      // Equal results.
  } else if( r0->_hi == r1->_lo ) // Range is never high?
    return TypeInt::CC_LE;
  else if( r0->_lo == r1->_hi ) // Range is never low?
    return TypeInt::CC_GE;
  return TypeInt::CC;           // else use worst case results
}

//=============================================================================
//------------------------------sub--------------------------------------------
// Simplify an CmpP (compare 2 pointers) node, based on local information.
// If both inputs are constants, compare them.
const Type *CmpPNode::sub( const Type *t1, const Type *t2 ) const {
  const TypePtr *r0 = t1->is_ptr(); // Handy access
  const TypePtr *r1 = t2->is_ptr();

  // Undefined inputs makes for an undefined result
  if( TypePtr::above_centerline(r0->_ptr) ||
      TypePtr::above_centerline(r1->_ptr) )
    return Type::TOP;

  if (r0 == r1 && r0->singleton()) {
    // Equal pointer constants (klasses, nulls, etc.)
    return TypeInt::CC_EQ;
  }

  // See if it is 2 unrelated classes.
  const TypeOopPtr* p0 = r0->isa_oopptr();
  const TypeOopPtr* p1 = r1->isa_oopptr();
  if (p0 && p1) {
    Node* in1 = in(1)->uncast();
    Node* in2 = in(2)->uncast();
    AllocateNode* alloc1 = AllocateNode::Ideal_allocation(in1, NULL);
    AllocateNode* alloc2 = AllocateNode::Ideal_allocation(in2, NULL);
    if (MemNode::detect_ptr_independence(in1, alloc1, in2, alloc2, NULL)) {
      return TypeInt::CC_GT;  // different pointers
    }
    ciKlass* klass0 = p0->klass();
    bool    xklass0 = p0->klass_is_exact();
    ciKlass* klass1 = p1->klass();
    bool    xklass1 = p1->klass_is_exact();
    int kps = (p0->isa_klassptr()?1:0) + (p1->isa_klassptr()?1:0);
    if (klass0 && klass1 &&
        kps != 1 &&             // both or neither are klass pointers
        klass0->is_loaded() && !klass0->is_interface() && // do not trust interfaces
        klass1->is_loaded() && !klass1->is_interface() &&
        (!klass0->is_obj_array_klass() ||
         !klass0->as_obj_array_klass()->base_element_klass()->is_interface()) &&
        (!klass1->is_obj_array_klass() ||
         !klass1->as_obj_array_klass()->base_element_klass()->is_interface())) {
      bool unrelated_classes = false;
      // See if neither subclasses the other, or if the class on top
      // is precise.  In either of these cases, the compare is known
      // to fail if at least one of the pointers is provably not null.
      if (klass0->equals(klass1)   ||   // if types are unequal but klasses are
          !klass0->is_java_klass() ||   // types not part of Java language?
          !klass1->is_java_klass()) {   // types not part of Java language?
        // Do nothing; we know nothing for imprecise types
      } else if (klass0->is_subtype_of(klass1)) {
        // If klass1's type is PRECISE, then classes are unrelated.
        unrelated_classes = xklass1;
      } else if (klass1->is_subtype_of(klass0)) {
        // If klass0's type is PRECISE, then classes are unrelated.
        unrelated_classes = xklass0;
      } else {                  // Neither subtypes the other
        unrelated_classes = true;
      }
      if (unrelated_classes) {
        // The oops classes are known to be unrelated. If the joined PTRs of
        // two oops is not Null and not Bottom, then we are sure that one
        // of the two oops is non-null, and the comparison will always fail.
        TypePtr::PTR jp = r0->join_ptr(r1->_ptr);
        if (jp != TypePtr::Null && jp != TypePtr::BotPTR) {
          return TypeInt::CC_GT;
        }
      }
    }
  }

  // Known constants can be compared exactly
  // Null can be distinguished from any NotNull pointers
  // Unknown inputs makes an unknown result
  if( r0->singleton() ) {
    intptr_t bits0 = r0->get_con();
    if( r1->singleton() )
      return bits0 == r1->get_con() ? TypeInt::CC_EQ : TypeInt::CC_GT;
    return ( r1->_ptr == TypePtr::NotNull && bits0==0 ) ? TypeInt::CC_GT : TypeInt::CC;
  } else if( r1->singleton() ) {
    intptr_t bits1 = r1->get_con();
    return ( r0->_ptr == TypePtr::NotNull && bits1==0 ) ? TypeInt::CC_GT : TypeInt::CC;
  } else
    return TypeInt::CC;
}

//------------------------------Ideal------------------------------------------
// Check for the case of comparing an unknown klass loaded from the primary
// super-type array vs a known klass with no subtypes.  This amounts to
// checking to see an unknown klass subtypes a known klass with no subtypes;
// this only happens on an exact match.  We can shorten this test by 1 load.
Node *CmpPNode::Ideal( PhaseGVN *phase, bool can_reshape ) {
  // Constant pointer on right?
  const TypeKlassPtr* t2 = phase->type(in(2))->isa_klassptr();
  if (t2 == NULL || !t2->klass_is_exact())
    return NULL;
  // Get the constant klass we are comparing to.
  ciKlass* superklass = t2->klass();

  // Now check for LoadKlass on left.
  Node* ldk1 = in(1);
  if (ldk1->is_DecodeN()) {
    ldk1 = ldk1->in(1);
    if (ldk1->Opcode() != Op_LoadNKlass )
      return NULL;
  } else if (ldk1->Opcode() != Op_LoadKlass )
    return NULL;
  // Take apart the address of the LoadKlass:
  Node* adr1 = ldk1->in(MemNode::Address);
  intptr_t con2 = 0;
  Node* ldk2 = AddPNode::Ideal_base_and_offset(adr1, phase, con2);
  if (ldk2 == NULL)
    return NULL;
  if (con2 == oopDesc::klass_offset_in_bytes()) {
    // We are inspecting an object's concrete class.
    // Short-circuit the check if the query is abstract.
    if (superklass->is_interface() ||
        superklass->is_abstract()) {
      // Make it come out always false:
      this->set_req(2, phase->makecon(TypePtr::NULL_PTR));
      return this;
    }
  }

  // Check for a LoadKlass from primary supertype array.
  // Any nested loadklass from loadklass+con must be from the p.s. array.
  if (ldk2->is_DecodeN()) {
    // Keep ldk2 as DecodeN since it could be used in CmpP below.
    if (ldk2->in(1)->Opcode() != Op_LoadNKlass )
      return NULL;
  } else if (ldk2->Opcode() != Op_LoadKlass)
    return NULL;

  // Verify that we understand the situation
  if (con2 != (intptr_t) superklass->super_check_offset())
    return NULL;                // Might be element-klass loading from array klass

  // If 'superklass' has no subklasses and is not an interface, then we are
  // assured that the only input which will pass the type check is
  // 'superklass' itself.
  //
  // We could be more liberal here, and allow the optimization on interfaces
  // which have a single implementor.  This would require us to increase the
  // expressiveness of the add_dependency() mechanism.
  // %%% Do this after we fix TypeOopPtr:  Deps are expressive enough now.

  // Object arrays must have their base element have no subtypes
  while (superklass->is_obj_array_klass()) {
    ciType* elem = superklass->as_obj_array_klass()->element_type();
    superklass = elem->as_klass();
  }
  if (superklass->is_instance_klass()) {
    ciInstanceKlass* ik = superklass->as_instance_klass();
    if (ik->has_subklass() || ik->is_interface())  return NULL;
    // Add a dependency if there is a chance that a subclass will be added later.
    if (!ik->is_final()) {
      phase->C->dependencies()->assert_leaf_type(ik);
    }
  }

  // Bypass the dependent load, and compare directly
  this->set_req(1,ldk2);

  return this;
}

//=============================================================================
//------------------------------sub--------------------------------------------
// Simplify an CmpN (compare 2 pointers) node, based on local information.
// If both inputs are constants, compare them.
const Type *CmpNNode::sub( const Type *t1, const Type *t2 ) const {
  const TypePtr *r0 = t1->make_ptr(); // Handy access
  const TypePtr *r1 = t2->make_ptr();

  // Undefined inputs makes for an undefined result
  if( TypePtr::above_centerline(r0->_ptr) ||
      TypePtr::above_centerline(r1->_ptr) )
    return Type::TOP;

  if (r0 == r1 && r0->singleton()) {
    // Equal pointer constants (klasses, nulls, etc.)
    return TypeInt::CC_EQ;
  }

  // See if it is 2 unrelated classes.
  const TypeOopPtr* p0 = r0->isa_oopptr();
  const TypeOopPtr* p1 = r1->isa_oopptr();
  if (p0 && p1) {
    ciKlass* klass0 = p0->klass();
    bool    xklass0 = p0->klass_is_exact();
    ciKlass* klass1 = p1->klass();
    bool    xklass1 = p1->klass_is_exact();
    int kps = (p0->isa_klassptr()?1:0) + (p1->isa_klassptr()?1:0);
    if (klass0 && klass1 &&
        kps != 1 &&             // both or neither are klass pointers
        !klass0->is_interface() && // do not trust interfaces
        !klass1->is_interface()) {
      bool unrelated_classes = false;
      // See if neither subclasses the other, or if the class on top
      // is precise.  In either of these cases, the compare is known
      // to fail if at least one of the pointers is provably not null.
      if (klass0->equals(klass1)   ||   // if types are unequal but klasses are
          !klass0->is_java_klass() ||   // types not part of Java language?
          !klass1->is_java_klass()) {   // types not part of Java language?
        // Do nothing; we know nothing for imprecise types
      } else if (klass0->is_subtype_of(klass1)) {
        // If klass1's type is PRECISE, then classes are unrelated.
        unrelated_classes = xklass1;
      } else if (klass1->is_subtype_of(klass0)) {
        // If klass0's type is PRECISE, then classes are unrelated.
        unrelated_classes = xklass0;
      } else {                  // Neither subtypes the other
        unrelated_classes = true;
      }
      if (unrelated_classes) {
        // The oops classes are known to be unrelated. If the joined PTRs of
        // two oops is not Null and not Bottom, then we are sure that one
        // of the two oops is non-null, and the comparison will always fail.
        TypePtr::PTR jp = r0->join_ptr(r1->_ptr);
        if (jp != TypePtr::Null && jp != TypePtr::BotPTR) {
          return TypeInt::CC_GT;
        }
      }
    }
  }

  // Known constants can be compared exactly
  // Null can be distinguished from any NotNull pointers
  // Unknown inputs makes an unknown result
  if( r0->singleton() ) {
    intptr_t bits0 = r0->get_con();
    if( r1->singleton() )
      return bits0 == r1->get_con() ? TypeInt::CC_EQ : TypeInt::CC_GT;
    return ( r1->_ptr == TypePtr::NotNull && bits0==0 ) ? TypeInt::CC_GT : TypeInt::CC;
  } else if( r1->singleton() ) {
    intptr_t bits1 = r1->get_con();
    return ( r0->_ptr == TypePtr::NotNull && bits1==0 ) ? TypeInt::CC_GT : TypeInt::CC;
  } else
    return TypeInt::CC;
}

//------------------------------Ideal------------------------------------------
Node *CmpNNode::Ideal( PhaseGVN *phase, bool can_reshape ) {
  return NULL;
}

//=============================================================================
//------------------------------Value------------------------------------------
// Simplify an CmpF (compare 2 floats ) node, based on local information.
// If both inputs are constants, compare them.
const Type *CmpFNode::Value( PhaseTransform *phase ) const {
  const Node* in1 = in(1);
  const Node* in2 = in(2);
  // Either input is TOP ==> the result is TOP
  const Type* t1 = (in1 == this) ? Type::TOP : phase->type(in1);
  if( t1 == Type::TOP ) return Type::TOP;
  const Type* t2 = (in2 == this) ? Type::TOP : phase->type(in2);
  if( t2 == Type::TOP ) return Type::TOP;

  // Not constants?  Don't know squat - even if they are the same
  // value!  If they are NaN's they compare to LT instead of EQ.
  const TypeF *tf1 = t1->isa_float_constant();
  const TypeF *tf2 = t2->isa_float_constant();
  if( !tf1 || !tf2 ) return TypeInt::CC;

  // This implements the Java bytecode fcmpl, so unordered returns -1.
  if( tf1->is_nan() || tf2->is_nan() )
    return TypeInt::CC_LT;

  if( tf1->_f < tf2->_f ) return TypeInt::CC_LT;
  if( tf1->_f > tf2->_f ) return TypeInt::CC_GT;
  assert( tf1->_f == tf2->_f, "do not understand FP behavior" );
  return TypeInt::CC_EQ;
}


//=============================================================================
//------------------------------Value------------------------------------------
// Simplify an CmpD (compare 2 doubles ) node, based on local information.
// If both inputs are constants, compare them.
const Type *CmpDNode::Value( PhaseTransform *phase ) const {
  const Node* in1 = in(1);
  const Node* in2 = in(2);
  // Either input is TOP ==> the result is TOP
  const Type* t1 = (in1 == this) ? Type::TOP : phase->type(in1);
  if( t1 == Type::TOP ) return Type::TOP;
  const Type* t2 = (in2 == this) ? Type::TOP : phase->type(in2);
  if( t2 == Type::TOP ) return Type::TOP;

  // Not constants?  Don't know squat - even if they are the same
  // value!  If they are NaN's they compare to LT instead of EQ.
  const TypeD *td1 = t1->isa_double_constant();
  const TypeD *td2 = t2->isa_double_constant();
  if( !td1 || !td2 ) return TypeInt::CC;

  // This implements the Java bytecode dcmpl, so unordered returns -1.
  if( td1->is_nan() || td2->is_nan() )
    return TypeInt::CC_LT;

  if( td1->_d < td2->_d ) return TypeInt::CC_LT;
  if( td1->_d > td2->_d ) return TypeInt::CC_GT;
  assert( td1->_d == td2->_d, "do not understand FP behavior" );
  return TypeInt::CC_EQ;
}

//------------------------------Ideal------------------------------------------
Node *CmpDNode::Ideal(PhaseGVN *phase, bool can_reshape){
  // Check if we can change this to a CmpF and remove a ConvD2F operation.
  // Change  (CMPD (F2D (float)) (ConD value))
  // To      (CMPF      (float)  (ConF value))
  // Valid when 'value' does not lose precision as a float.
  // Benefits: eliminates conversion, does not require 24-bit mode

  // NaNs prevent commuting operands.  This transform works regardless of the
  // order of ConD and ConvF2D inputs by preserving the original order.
  int idx_f2d = 1;              // ConvF2D on left side?
  if( in(idx_f2d)->Opcode() != Op_ConvF2D )
    idx_f2d = 2;                // No, swap to check for reversed args
  int idx_con = 3-idx_f2d;      // Check for the constant on other input

  if( ConvertCmpD2CmpF &&
      in(idx_f2d)->Opcode() == Op_ConvF2D &&
      in(idx_con)->Opcode() == Op_ConD ) {
    const TypeD *t2 = in(idx_con)->bottom_type()->is_double_constant();
    double t2_value_as_double = t2->_d;
    float  t2_value_as_float  = (float)t2_value_as_double;
    if( t2_value_as_double == (double)t2_value_as_float ) {
      // Test value can be represented as a float
      // Eliminate the conversion to double and create new comparison
      Node *new_in1 = in(idx_f2d)->in(1);
      Node *new_in2 = phase->makecon( TypeF::make(t2_value_as_float) );
      if( idx_f2d != 1 ) {      // Must flip args to match original order
        Node *tmp = new_in1;
        new_in1 = new_in2;
        new_in2 = tmp;
      }
      CmpFNode *new_cmp = (Opcode() == Op_CmpD3)
        ? new (phase->C, 3) CmpF3Node( new_in1, new_in2 )
        : new (phase->C, 3) CmpFNode ( new_in1, new_in2 ) ;
      return new_cmp;           // Changed to CmpFNode
    }
    // Testing value required the precision of a double
  }
  return NULL;                  // No change
}


//=============================================================================
//------------------------------cc2logical-------------------------------------
// Convert a condition code type to a logical type
const Type *BoolTest::cc2logical( const Type *CC ) const {
  if( CC == Type::TOP ) return Type::TOP;
  if( CC->base() != Type::Int ) return TypeInt::BOOL; // Bottom or worse
  const TypeInt *ti = CC->is_int();
  if( ti->is_con() ) {          // Only 1 kind of condition codes set?
    // Match low order 2 bits
    int tmp = ((ti->get_con()&3) == (_test&3)) ? 1 : 0;
    if( _test & 4 ) tmp = 1-tmp;     // Optionally complement result
    return TypeInt::make(tmp);       // Boolean result
  }

  if( CC == TypeInt::CC_GE ) {
    if( _test == ge ) return TypeInt::ONE;
    if( _test == lt ) return TypeInt::ZERO;
  }
  if( CC == TypeInt::CC_LE ) {
    if( _test == le ) return TypeInt::ONE;
    if( _test == gt ) return TypeInt::ZERO;
  }

  return TypeInt::BOOL;
}

//------------------------------dump_spec-------------------------------------
// Print special per-node info
#ifndef PRODUCT
void BoolTest::dump_on(outputStream *st) const {
  const char *msg[] = {"eq","gt","??","lt","ne","le","??","ge"};
  st->print(msg[_test]);
}
#endif

//=============================================================================
uint BoolNode::hash() const { return (Node::hash() << 3)|(_test._test+1); }
uint BoolNode::size_of() const { return sizeof(BoolNode); }

//------------------------------operator==-------------------------------------
uint BoolNode::cmp( const Node &n ) const {
  const BoolNode *b = (const BoolNode *)&n; // Cast up
  return (_test._test == b->_test._test);
}

//------------------------------clone_cmp--------------------------------------
// Clone a compare/bool tree
static Node *clone_cmp( Node *cmp, Node *cmp1, Node *cmp2, PhaseGVN *gvn, BoolTest::mask test ) {
  Node *ncmp = cmp->clone();
  ncmp->set_req(1,cmp1);
  ncmp->set_req(2,cmp2);
  ncmp = gvn->transform( ncmp );
  return new (gvn->C, 2) BoolNode( ncmp, test );
}

//-------------------------------make_predicate--------------------------------
Node* BoolNode::make_predicate(Node* test_value, PhaseGVN* phase) {
  if (test_value->is_Con())   return test_value;
  if (test_value->is_Bool())  return test_value;
  Compile* C = phase->C;
  if (test_value->is_CMove() &&
      test_value->in(CMoveNode::Condition)->is_Bool()) {
    BoolNode*   bol   = test_value->in(CMoveNode::Condition)->as_Bool();
    const Type* ftype = phase->type(test_value->in(CMoveNode::IfFalse));
    const Type* ttype = phase->type(test_value->in(CMoveNode::IfTrue));
    if (ftype == TypeInt::ZERO && !TypeInt::ZERO->higher_equal(ttype)) {
      return bol;
    } else if (ttype == TypeInt::ZERO && !TypeInt::ZERO->higher_equal(ftype)) {
      return phase->transform( bol->negate(phase) );
    }
    // Else fall through.  The CMove gets in the way of the test.
    // It should be the case that make_predicate(bol->as_int_value()) == bol.
  }
  Node* cmp = new (C, 3) CmpINode(test_value, phase->intcon(0));
  cmp = phase->transform(cmp);
  Node* bol = new (C, 2) BoolNode(cmp, BoolTest::ne);
  return phase->transform(bol);
}

//--------------------------------as_int_value---------------------------------
Node* BoolNode::as_int_value(PhaseGVN* phase) {
  // Inverse to make_predicate.  The CMove probably boils down to a Conv2B.
  Node* cmov = CMoveNode::make(phase->C, NULL, this,
                               phase->intcon(0), phase->intcon(1),
                               TypeInt::BOOL);
  return phase->transform(cmov);
}

//----------------------------------negate-------------------------------------
BoolNode* BoolNode::negate(PhaseGVN* phase) {
  Compile* C = phase->C;
  return new (C, 2) BoolNode(in(1), _test.negate());
}


//------------------------------Ideal------------------------------------------
Node *BoolNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Change "bool tst (cmp con x)" into "bool ~tst (cmp x con)".
  // This moves the constant to the right.  Helps value-numbering.
  Node *cmp = in(1);
  if( !cmp->is_Sub() ) return NULL;
  int cop = cmp->Opcode();
  if( cop == Op_FastLock || cop == Op_FastUnlock ) return NULL;
  Node *cmp1 = cmp->in(1);
  Node *cmp2 = cmp->in(2);
  if( !cmp1 ) return NULL;

  // Constant on left?
  Node *con = cmp1;
  uint op2 = cmp2->Opcode();
  // Move constants to the right of compare's to canonicalize.
  // Do not muck with Opaque1 nodes, as this indicates a loop
  // guard that cannot change shape.
  if( con->is_Con() && !cmp2->is_Con() && op2 != Op_Opaque1 &&
      // Because of NaN's, CmpD and CmpF are not commutative
      cop != Op_CmpD && cop != Op_CmpF &&
      // Protect against swapping inputs to a compare when it is used by a
      // counted loop exit, which requires maintaining the loop-limit as in(2)
      !is_counted_loop_exit_test() ) {
    // Ok, commute the constant to the right of the cmp node.
    // Clone the Node, getting a new Node of the same class
    cmp = cmp->clone();
    // Swap inputs to the clone
    cmp->swap_edges(1, 2);
    cmp = phase->transform( cmp );
    return new (phase->C, 2) BoolNode( cmp, _test.commute() );
  }

  // Change "bool eq/ne (cmp (xor X 1) 0)" into "bool ne/eq (cmp X 0)".
  // The XOR-1 is an idiom used to flip the sense of a bool.  We flip the
  // test instead.
  int cmp1_op = cmp1->Opcode();
  const TypeInt* cmp2_type = phase->type(cmp2)->isa_int();
  if (cmp2_type == NULL)  return NULL;
  Node* j_xor = cmp1;
  if( cmp2_type == TypeInt::ZERO &&
      cmp1_op == Op_XorI &&
      j_xor->in(1) != j_xor &&          // An xor of itself is dead
      phase->type( j_xor->in(2) ) == TypeInt::ONE &&
      (_test._test == BoolTest::eq ||
       _test._test == BoolTest::ne) ) {
    Node *ncmp = phase->transform(new (phase->C, 3) CmpINode(j_xor->in(1),cmp2));
    return new (phase->C, 2) BoolNode( ncmp, _test.negate() );
  }

  // Change "bool eq/ne (cmp (Conv2B X) 0)" into "bool eq/ne (cmp X 0)".
  // This is a standard idiom for branching on a boolean value.
  Node *c2b = cmp1;
  if( cmp2_type == TypeInt::ZERO &&
      cmp1_op == Op_Conv2B &&
      (_test._test == BoolTest::eq ||
       _test._test == BoolTest::ne) ) {
    Node *ncmp = phase->transform(phase->type(c2b->in(1))->isa_int()
       ? (Node*)new (phase->C, 3) CmpINode(c2b->in(1),cmp2)
       : (Node*)new (phase->C, 3) CmpPNode(c2b->in(1),phase->makecon(TypePtr::NULL_PTR))
    );
    return new (phase->C, 2) BoolNode( ncmp, _test._test );
  }

  // Comparing a SubI against a zero is equal to comparing the SubI
  // arguments directly.  This only works for eq and ne comparisons
  // due to possible integer overflow.
  if ((_test._test == BoolTest::eq || _test._test == BoolTest::ne) &&
        (cop == Op_CmpI) &&
        (cmp1->Opcode() == Op_SubI) &&
        ( cmp2_type == TypeInt::ZERO ) ) {
    Node *ncmp = phase->transform( new (phase->C, 3) CmpINode(cmp1->in(1),cmp1->in(2)));
    return new (phase->C, 2) BoolNode( ncmp, _test._test );
  }

  // Change (-A vs 0) into (A vs 0) by commuting the test.  Disallow in the
  // most general case because negating 0x80000000 does nothing.  Needed for
  // the CmpF3/SubI/CmpI idiom.
  if( cop == Op_CmpI &&
      cmp1->Opcode() == Op_SubI &&
      cmp2_type == TypeInt::ZERO &&
      phase->type( cmp1->in(1) ) == TypeInt::ZERO &&
      phase->type( cmp1->in(2) )->higher_equal(TypeInt::SYMINT) ) {
    Node *ncmp = phase->transform( new (phase->C, 3) CmpINode(cmp1->in(2),cmp2));
    return new (phase->C, 2) BoolNode( ncmp, _test.commute() );
  }

  //  The transformation below is not valid for either signed or unsigned
  //  comparisons due to wraparound concerns at MAX_VALUE and MIN_VALUE.
  //  This transformation can be resurrected when we are able to
  //  make inferences about the range of values being subtracted from
  //  (or added to) relative to the wraparound point.
  //
  //    // Remove +/-1's if possible.
  //    // "X <= Y-1" becomes "X <  Y"
  //    // "X+1 <= Y" becomes "X <  Y"
  //    // "X <  Y+1" becomes "X <= Y"
  //    // "X-1 <  Y" becomes "X <= Y"
  //    // Do not this to compares off of the counted-loop-end.  These guys are
  //    // checking the trip counter and they want to use the post-incremented
  //    // counter.  If they use the PRE-incremented counter, then the counter has
  //    // to be incremented in a private block on a loop backedge.
  //    if( du && du->cnt(this) && du->out(this)[0]->Opcode() == Op_CountedLoopEnd )
  //      return NULL;
  //  #ifndef PRODUCT
  //    // Do not do this in a wash GVN pass during verification.
  //    // Gets triggered by too many simple optimizations to be bothered with
  //    // re-trying it again and again.
  //    if( !phase->allow_progress() ) return NULL;
  //  #endif
  //    // Not valid for unsigned compare because of corner cases in involving zero.
  //    // For example, replacing "X-1 <u Y" with "X <=u Y" fails to throw an
  //    // exception in case X is 0 (because 0-1 turns into 4billion unsigned but
  //    // "0 <=u Y" is always true).
  //    if( cmp->Opcode() == Op_CmpU ) return NULL;
  //    int cmp2_op = cmp2->Opcode();
  //    if( _test._test == BoolTest::le ) {
  //      if( cmp1_op == Op_AddI &&
  //          phase->type( cmp1->in(2) ) == TypeInt::ONE )
  //        return clone_cmp( cmp, cmp1->in(1), cmp2, phase, BoolTest::lt );
  //      else if( cmp2_op == Op_AddI &&
  //         phase->type( cmp2->in(2) ) == TypeInt::MINUS_1 )
  //        return clone_cmp( cmp, cmp1, cmp2->in(1), phase, BoolTest::lt );
  //    } else if( _test._test == BoolTest::lt ) {
  //      if( cmp1_op == Op_AddI &&
  //          phase->type( cmp1->in(2) ) == TypeInt::MINUS_1 )
  //        return clone_cmp( cmp, cmp1->in(1), cmp2, phase, BoolTest::le );
  //      else if( cmp2_op == Op_AddI &&
  //         phase->type( cmp2->in(2) ) == TypeInt::ONE )
  //        return clone_cmp( cmp, cmp1, cmp2->in(1), phase, BoolTest::le );
  //    }

  return NULL;
}

//------------------------------Value------------------------------------------
// Simplify a Bool (convert condition codes to boolean (1 or 0)) node,
// based on local information.   If the input is constant, do it.
const Type *BoolNode::Value( PhaseTransform *phase ) const {
  return _test.cc2logical( phase->type( in(1) ) );
}

//------------------------------dump_spec--------------------------------------
// Dump special per-node info
#ifndef PRODUCT
void BoolNode::dump_spec(outputStream *st) const {
  st->print("[");
  _test.dump_on(st);
  st->print("]");
}
#endif

//------------------------------is_counted_loop_exit_test--------------------------------------
// Returns true if node is used by a counted loop node.
bool BoolNode::is_counted_loop_exit_test() {
  for( DUIterator_Fast imax, i = fast_outs(imax); i < imax; i++ ) {
    Node* use = fast_out(i);
    if (use->is_CountedLoopEnd()) {
      return true;
    }
  }
  return false;
}

//=============================================================================
//------------------------------NegNode----------------------------------------
Node *NegFNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if( in(1)->Opcode() == Op_SubF )
    return new (phase->C, 3) SubFNode( in(1)->in(2), in(1)->in(1) );
  return NULL;
}

Node *NegDNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if( in(1)->Opcode() == Op_SubD )
    return new (phase->C, 3) SubDNode( in(1)->in(2), in(1)->in(1) );
  return NULL;
}


//=============================================================================
//------------------------------Value------------------------------------------
// Compute sqrt
const Type *SqrtDNode::Value( PhaseTransform *phase ) const {
  const Type *t1 = phase->type( in(1) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t1->base() != Type::DoubleCon ) return Type::DOUBLE;
  double d = t1->getd();
  if( d < 0.0 ) return Type::DOUBLE;
  return TypeD::make( sqrt( d ) );
}

//=============================================================================
//------------------------------Value------------------------------------------
// Compute cos
const Type *CosDNode::Value( PhaseTransform *phase ) const {
  const Type *t1 = phase->type( in(1) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t1->base() != Type::DoubleCon ) return Type::DOUBLE;
  double d = t1->getd();
  return TypeD::make( StubRoutines::intrinsic_cos( d ) );
}

//=============================================================================
//------------------------------Value------------------------------------------
// Compute sin
const Type *SinDNode::Value( PhaseTransform *phase ) const {
  const Type *t1 = phase->type( in(1) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t1->base() != Type::DoubleCon ) return Type::DOUBLE;
  double d = t1->getd();
  return TypeD::make( StubRoutines::intrinsic_sin( d ) );
}

//=============================================================================
//------------------------------Value------------------------------------------
// Compute tan
const Type *TanDNode::Value( PhaseTransform *phase ) const {
  const Type *t1 = phase->type( in(1) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t1->base() != Type::DoubleCon ) return Type::DOUBLE;
  double d = t1->getd();
  return TypeD::make( StubRoutines::intrinsic_tan( d ) );
}

//=============================================================================
//------------------------------Value------------------------------------------
// Compute log
const Type *LogDNode::Value( PhaseTransform *phase ) const {
  const Type *t1 = phase->type( in(1) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t1->base() != Type::DoubleCon ) return Type::DOUBLE;
  double d = t1->getd();
  return TypeD::make( StubRoutines::intrinsic_log( d ) );
}

//=============================================================================
//------------------------------Value------------------------------------------
// Compute log10
const Type *Log10DNode::Value( PhaseTransform *phase ) const {
  const Type *t1 = phase->type( in(1) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t1->base() != Type::DoubleCon ) return Type::DOUBLE;
  double d = t1->getd();
  return TypeD::make( StubRoutines::intrinsic_log10( d ) );
}

//=============================================================================
//------------------------------Value------------------------------------------
// Compute exp
const Type *ExpDNode::Value( PhaseTransform *phase ) const {
  const Type *t1 = phase->type( in(1) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t1->base() != Type::DoubleCon ) return Type::DOUBLE;
  double d = t1->getd();
  return TypeD::make( StubRoutines::intrinsic_exp( d ) );
}


//=============================================================================
//------------------------------Value------------------------------------------
// Compute pow
const Type *PowDNode::Value( PhaseTransform *phase ) const {
  const Type *t1 = phase->type( in(1) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t1->base() != Type::DoubleCon ) return Type::DOUBLE;
  const Type *t2 = phase->type( in(2) );
  if( t2 == Type::TOP ) return Type::TOP;
  if( t2->base() != Type::DoubleCon ) return Type::DOUBLE;
  double d1 = t1->getd();
  double d2 = t2->getd();
  if( d1 < 0.0 ) return Type::DOUBLE;
  if( d2 < 0.0 ) return Type::DOUBLE;
  return TypeD::make( StubRoutines::intrinsic_pow( d1, d2 ) );
}
