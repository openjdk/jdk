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

// Optimization - Graph Style

#include "incls/_precompiled.incl"
#include "incls/_connode.cpp.incl"

//=============================================================================
//------------------------------hash-------------------------------------------
uint ConNode::hash() const {
  return (uintptr_t)in(TypeFunc::Control) + _type->hash();
}

//------------------------------make-------------------------------------------
ConNode *ConNode::make( Compile* C, const Type *t ) {
  switch( t->basic_type() ) {
  case T_INT:       return new (C, 1) ConINode( t->is_int() );
  case T_LONG:      return new (C, 1) ConLNode( t->is_long() );
  case T_FLOAT:     return new (C, 1) ConFNode( t->is_float_constant() );
  case T_DOUBLE:    return new (C, 1) ConDNode( t->is_double_constant() );
  case T_VOID:      return new (C, 1) ConNode ( Type::TOP );
  case T_OBJECT:    return new (C, 1) ConPNode( t->is_oopptr() );
  case T_ARRAY:     return new (C, 1) ConPNode( t->is_aryptr() );
  case T_ADDRESS:   return new (C, 1) ConPNode( t->is_ptr() );
  case T_NARROWOOP: return new (C, 1) ConNNode( t->is_narrowoop() );
    // Expected cases:  TypePtr::NULL_PTR, any is_rawptr()
    // Also seen: AnyPtr(TopPTR *+top); from command line:
    //   r -XX:+PrintOpto -XX:CIStart=285 -XX:+CompileTheWorld -XX:CompileTheWorldStartAt=660
    // %%%% Stop using TypePtr::NULL_PTR to represent nulls:  use either TypeRawPtr::NULL_PTR
    // or else TypeOopPtr::NULL_PTR.  Then set Type::_basic_type[AnyPtr] = T_ILLEGAL
  }
  ShouldNotReachHere();
  return NULL;
}

//=============================================================================
/*
The major change is for CMoveP and StrComp.  They have related but slightly
different problems.  They both take in TWO oops which are both null-checked
independently before the using Node.  After CCP removes the CastPP's they need
to pick up the guarding test edge - in this case TWO control edges.  I tried
various solutions, all have problems:

(1) Do nothing.  This leads to a bug where we hoist a Load from a CMoveP or a
StrComp above a guarding null check.  I've seen both cases in normal -Xcomp
testing.

(2) Plug the control edge from 1 of the 2 oops in.  Apparent problem here is
to figure out which test post-dominates.  The real problem is that it doesn't
matter which one you pick.  After you pick up, the dominating-test elider in
IGVN can remove the test and allow you to hoist up to the dominating test on
the chosen oop bypassing the test on the not-chosen oop.  Seen in testing.
Oops.

(3) Leave the CastPP's in.  This makes the graph more accurate in some sense;
we get to keep around the knowledge that an oop is not-null after some test.
Alas, the CastPP's interfere with GVN (some values are the regular oop, some
are the CastPP of the oop, all merge at Phi's which cannot collapse, etc).
This cost us 10% on SpecJVM, even when I removed some of the more trivial
cases in the optimizer.  Removing more useless Phi's started allowing Loads to
illegally float above null checks.  I gave up on this approach.

(4) Add BOTH control edges to both tests.  Alas, too much code knows that
control edges are in slot-zero ONLY.  Many quick asserts fail; no way to do
this one.  Note that I really want to allow the CMoveP to float and add both
control edges to the dependent Load op - meaning I can select early but I
cannot Load until I pass both tests.

(5) Do not hoist CMoveP and StrComp.  To this end I added the v-call
depends_only_on_test().  No obvious performance loss on Spec, but we are
clearly conservative on CMoveP (also so on StrComp but that's unlikely to
matter ever).

*/


//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.
// Move constants to the right.
Node *CMoveNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if( in(0) && remove_dead_region(phase, can_reshape) ) return this;
  // Don't bother trying to transform a dead node
  if( in(0) && in(0)->is_top() )  return NULL;
  assert( !phase->eqv(in(Condition), this) &&
          !phase->eqv(in(IfFalse), this) &&
          !phase->eqv(in(IfTrue), this), "dead loop in CMoveNode::Ideal" );
  if( phase->type(in(Condition)) == Type::TOP )
    return NULL; // return NULL when Condition is dead

  if( in(IfFalse)->is_Con() && !in(IfTrue)->is_Con() ) {
    if( in(Condition)->is_Bool() ) {
      BoolNode* b  = in(Condition)->as_Bool();
      BoolNode* b2 = b->negate(phase);
      return make( phase->C, in(Control), phase->transform(b2), in(IfTrue), in(IfFalse), _type );
    }
  }
  return NULL;
}

//------------------------------is_cmove_id------------------------------------
// Helper function to check for CMOVE identity.  Shared with PhiNode::Identity
Node *CMoveNode::is_cmove_id( PhaseTransform *phase, Node *cmp, Node *t, Node *f, BoolNode *b ) {
  // Check for Cmp'ing and CMove'ing same values
  if( (phase->eqv(cmp->in(1),f) &&
       phase->eqv(cmp->in(2),t)) ||
      // Swapped Cmp is OK
      (phase->eqv(cmp->in(2),f) &&
       phase->eqv(cmp->in(1),t)) ) {
    // Give up this identity check for floating points because it may choose incorrect
    // value around 0.0 and -0.0
    if ( cmp->Opcode()==Op_CmpF || cmp->Opcode()==Op_CmpD )
      return NULL;
    // Check for "(t==f)?t:f;" and replace with "f"
    if( b->_test._test == BoolTest::eq )
      return f;
    // Allow the inverted case as well
    // Check for "(t!=f)?t:f;" and replace with "t"
    if( b->_test._test == BoolTest::ne )
      return t;
  }
  return NULL;
}

//------------------------------Identity---------------------------------------
// Conditional-move is an identity if both inputs are the same, or the test
// true or false.
Node *CMoveNode::Identity( PhaseTransform *phase ) {
  if( phase->eqv(in(IfFalse),in(IfTrue)) ) // C-moving identical inputs?
    return in(IfFalse);         // Then it doesn't matter
  if( phase->type(in(Condition)) == TypeInt::ZERO )
    return in(IfFalse);         // Always pick left(false) input
  if( phase->type(in(Condition)) == TypeInt::ONE )
    return in(IfTrue);          // Always pick right(true) input

  // Check for CMove'ing a constant after comparing against the constant.
  // Happens all the time now, since if we compare equality vs a constant in
  // the parser, we "know" the variable is constant on one path and we force
  // it.  Thus code like "if( x==0 ) {/*EMPTY*/}" ends up inserting a
  // conditional move: "x = (x==0)?0:x;".  Yucko.  This fix is slightly more
  // general in that we don't need constants.
  if( in(Condition)->is_Bool() ) {
    BoolNode *b = in(Condition)->as_Bool();
    Node *cmp = b->in(1);
    if( cmp->is_Cmp() ) {
      Node *id = is_cmove_id( phase, cmp, in(IfTrue), in(IfFalse), b );
      if( id ) return id;
    }
  }

  return this;
}

//------------------------------Value------------------------------------------
// Result is the meet of inputs
const Type *CMoveNode::Value( PhaseTransform *phase ) const {
  if( phase->type(in(Condition)) == Type::TOP )
    return Type::TOP;
  return phase->type(in(IfFalse))->meet(phase->type(in(IfTrue)));
}

//------------------------------make-------------------------------------------
// Make a correctly-flavored CMove.  Since _type is directly determined
// from the inputs we do not need to specify it here.
CMoveNode *CMoveNode::make( Compile *C, Node *c, Node *bol, Node *left, Node *right, const Type *t ) {
  switch( t->basic_type() ) {
  case T_INT:     return new (C, 4) CMoveINode( bol, left, right, t->is_int() );
  case T_FLOAT:   return new (C, 4) CMoveFNode( bol, left, right, t );
  case T_DOUBLE:  return new (C, 4) CMoveDNode( bol, left, right, t );
  case T_LONG:    return new (C, 4) CMoveLNode( bol, left, right, t->is_long() );
  case T_OBJECT:  return new (C, 4) CMovePNode( c, bol, left, right, t->is_oopptr() );
  case T_ADDRESS: return new (C, 4) CMovePNode( c, bol, left, right, t->is_ptr() );
  case T_NARROWOOP: return new (C, 4) CMoveNNode( c, bol, left, right, t );
  default:
    ShouldNotReachHere();
    return NULL;
  }
}

//=============================================================================
//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.
// Check for conversions to boolean
Node *CMoveINode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Try generic ideal's first
  Node *x = CMoveNode::Ideal(phase, can_reshape);
  if( x ) return x;

  // If zero is on the left (false-case, no-move-case) it must mean another
  // constant is on the right (otherwise the shared CMove::Ideal code would
  // have moved the constant to the right).  This situation is bad for Intel
  // and a don't-care for Sparc.  It's bad for Intel because the zero has to
  // be manifested in a register with a XOR which kills flags, which are live
  // on input to the CMoveI, leading to a situation which causes excessive
  // spilling on Intel.  For Sparc, if the zero in on the left the Sparc will
  // zero a register via G0 and conditionally-move the other constant.  If the
  // zero is on the right, the Sparc will load the first constant with a
  // 13-bit set-lo and conditionally move G0.  See bug 4677505.
  if( phase->type(in(IfFalse)) == TypeInt::ZERO && !(phase->type(in(IfTrue)) == TypeInt::ZERO) ) {
    if( in(Condition)->is_Bool() ) {
      BoolNode* b  = in(Condition)->as_Bool();
      BoolNode* b2 = b->negate(phase);
      return make( phase->C, in(Control), phase->transform(b2), in(IfTrue), in(IfFalse), _type );
    }
  }

  // Now check for booleans
  int flip = 0;

  // Check for picking from zero/one
  if( phase->type(in(IfFalse)) == TypeInt::ZERO && phase->type(in(IfTrue)) == TypeInt::ONE ) {
    flip = 1 - flip;
  } else if( phase->type(in(IfFalse)) == TypeInt::ONE && phase->type(in(IfTrue)) == TypeInt::ZERO ) {
  } else return NULL;

  // Check for eq/ne test
  if( !in(1)->is_Bool() ) return NULL;
  BoolNode *bol = in(1)->as_Bool();
  if( bol->_test._test == BoolTest::eq ) {
  } else if( bol->_test._test == BoolTest::ne ) {
    flip = 1-flip;
  } else return NULL;

  // Check for vs 0 or 1
  if( !bol->in(1)->is_Cmp() ) return NULL;
  const CmpNode *cmp = bol->in(1)->as_Cmp();
  if( phase->type(cmp->in(2)) == TypeInt::ZERO ) {
  } else if( phase->type(cmp->in(2)) == TypeInt::ONE ) {
    // Allow cmp-vs-1 if the other input is bounded by 0-1
    if( phase->type(cmp->in(1)) != TypeInt::BOOL )
      return NULL;
    flip = 1 - flip;
  } else return NULL;

  // Convert to a bool (flipped)
  // Build int->bool conversion
#ifndef PRODUCT
  if( PrintOpto ) tty->print_cr("CMOV to I2B");
#endif
  Node *n = new (phase->C, 2) Conv2BNode( cmp->in(1) );
  if( flip )
    n = new (phase->C, 3) XorINode( phase->transform(n), phase->intcon(1) );

  return n;
}

//=============================================================================
//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.
// Check for absolute value
Node *CMoveFNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Try generic ideal's first
  Node *x = CMoveNode::Ideal(phase, can_reshape);
  if( x ) return x;

  int  cmp_zero_idx = 0;        // Index of compare input where to look for zero
  int  phi_x_idx = 0;           // Index of phi input where to find naked x

  // Find the Bool
  if( !in(1)->is_Bool() ) return NULL;
  BoolNode *bol = in(1)->as_Bool();
  // Check bool sense
  switch( bol->_test._test ) {
  case BoolTest::lt: cmp_zero_idx = 1; phi_x_idx = IfTrue;  break;
  case BoolTest::le: cmp_zero_idx = 2; phi_x_idx = IfFalse; break;
  case BoolTest::gt: cmp_zero_idx = 2; phi_x_idx = IfTrue;  break;
  case BoolTest::ge: cmp_zero_idx = 1; phi_x_idx = IfFalse; break;
  default:           return NULL;                           break;
  }

  // Find zero input of CmpF; the other input is being abs'd
  Node *cmpf = bol->in(1);
  if( cmpf->Opcode() != Op_CmpF ) return NULL;
  Node *X = NULL;
  bool flip = false;
  if( phase->type(cmpf->in(cmp_zero_idx)) == TypeF::ZERO ) {
    X = cmpf->in(3 - cmp_zero_idx);
  } else if (phase->type(cmpf->in(3 - cmp_zero_idx)) == TypeF::ZERO) {
    // The test is inverted, we should invert the result...
    X = cmpf->in(cmp_zero_idx);
    flip = true;
  } else {
    return NULL;
  }

  // If X is found on the appropriate phi input, find the subtract on the other
  if( X != in(phi_x_idx) ) return NULL;
  int phi_sub_idx = phi_x_idx == IfTrue ? IfFalse : IfTrue;
  Node *sub = in(phi_sub_idx);

  // Allow only SubF(0,X) and fail out for all others; NegF is not OK
  if( sub->Opcode() != Op_SubF ||
      sub->in(2) != X ||
      phase->type(sub->in(1)) != TypeF::ZERO ) return NULL;

  Node *abs = new (phase->C, 2) AbsFNode( X );
  if( flip )
    abs = new (phase->C, 3) SubFNode(sub->in(1), phase->transform(abs));

  return abs;
}

//=============================================================================
//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.
// Check for absolute value
Node *CMoveDNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Try generic ideal's first
  Node *x = CMoveNode::Ideal(phase, can_reshape);
  if( x ) return x;

  int  cmp_zero_idx = 0;        // Index of compare input where to look for zero
  int  phi_x_idx = 0;           // Index of phi input where to find naked x

  // Find the Bool
  if( !in(1)->is_Bool() ) return NULL;
  BoolNode *bol = in(1)->as_Bool();
  // Check bool sense
  switch( bol->_test._test ) {
  case BoolTest::lt: cmp_zero_idx = 1; phi_x_idx = IfTrue;  break;
  case BoolTest::le: cmp_zero_idx = 2; phi_x_idx = IfFalse; break;
  case BoolTest::gt: cmp_zero_idx = 2; phi_x_idx = IfTrue;  break;
  case BoolTest::ge: cmp_zero_idx = 1; phi_x_idx = IfFalse; break;
  default:           return NULL;                           break;
  }

  // Find zero input of CmpD; the other input is being abs'd
  Node *cmpd = bol->in(1);
  if( cmpd->Opcode() != Op_CmpD ) return NULL;
  Node *X = NULL;
  bool flip = false;
  if( phase->type(cmpd->in(cmp_zero_idx)) == TypeD::ZERO ) {
    X = cmpd->in(3 - cmp_zero_idx);
  } else if (phase->type(cmpd->in(3 - cmp_zero_idx)) == TypeD::ZERO) {
    // The test is inverted, we should invert the result...
    X = cmpd->in(cmp_zero_idx);
    flip = true;
  } else {
    return NULL;
  }

  // If X is found on the appropriate phi input, find the subtract on the other
  if( X != in(phi_x_idx) ) return NULL;
  int phi_sub_idx = phi_x_idx == IfTrue ? IfFalse : IfTrue;
  Node *sub = in(phi_sub_idx);

  // Allow only SubD(0,X) and fail out for all others; NegD is not OK
  if( sub->Opcode() != Op_SubD ||
      sub->in(2) != X ||
      phase->type(sub->in(1)) != TypeD::ZERO ) return NULL;

  Node *abs = new (phase->C, 2) AbsDNode( X );
  if( flip )
    abs = new (phase->C, 3) SubDNode(sub->in(1), phase->transform(abs));

  return abs;
}


//=============================================================================
// If input is already higher or equal to cast type, then this is an identity.
Node *ConstraintCastNode::Identity( PhaseTransform *phase ) {
  return phase->type(in(1))->higher_equal(_type) ? in(1) : this;
}

//------------------------------Value------------------------------------------
// Take 'join' of input and cast-up type
const Type *ConstraintCastNode::Value( PhaseTransform *phase ) const {
  if( in(0) && phase->type(in(0)) == Type::TOP ) return Type::TOP;
  const Type* ft = phase->type(in(1))->filter(_type);

#ifdef ASSERT
  // Previous versions of this function had some special case logic,
  // which is no longer necessary.  Make sure of the required effects.
  switch (Opcode()) {
  case Op_CastII:
    {
      const Type* t1 = phase->type(in(1));
      if( t1 == Type::TOP )  assert(ft == Type::TOP, "special case #1");
      const Type* rt = t1->join(_type);
      if (rt->empty())       assert(ft == Type::TOP, "special case #2");
      break;
    }
  case Op_CastPP:
    if (phase->type(in(1)) == TypePtr::NULL_PTR &&
        _type->isa_ptr() && _type->is_ptr()->_ptr == TypePtr::NotNull)
      assert(ft == Type::TOP, "special case #3");
    break;
  }
#endif //ASSERT

  return ft;
}

//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.  Strip out
// control copies
Node *ConstraintCastNode::Ideal(PhaseGVN *phase, bool can_reshape){
  return (in(0) && remove_dead_region(phase, can_reshape)) ? this : NULL;
}

//------------------------------Ideal_DU_postCCP-------------------------------
// Throw away cast after constant propagation
Node *ConstraintCastNode::Ideal_DU_postCCP( PhaseCCP *ccp ) {
  const Type *t = ccp->type(in(1));
  ccp->hash_delete(this);
  set_type(t);                   // Turn into ID function
  ccp->hash_insert(this);
  return this;
}


//=============================================================================

//------------------------------Ideal_DU_postCCP-------------------------------
// If not converting int->oop, throw away cast after constant propagation
Node *CastPPNode::Ideal_DU_postCCP( PhaseCCP *ccp ) {
  const Type *t = ccp->type(in(1));
  if (!t->isa_oop_ptr() || (in(1)->is_DecodeN() && Universe::narrow_oop_use_implicit_null_checks())) {
    return NULL; // do not transform raw pointers or narrow oops
  }
  return ConstraintCastNode::Ideal_DU_postCCP(ccp);
}



//=============================================================================
//------------------------------Identity---------------------------------------
// If input is already higher or equal to cast type, then this is an identity.
Node *CheckCastPPNode::Identity( PhaseTransform *phase ) {
  // Toned down to rescue meeting at a Phi 3 different oops all implementing
  // the same interface.  CompileTheWorld starting at 502, kd12rc1.zip.
  return (phase->type(in(1)) == phase->type(this)) ? in(1) : this;
}

// Determine whether "n" is a node which can cause an alias of one of its inputs.  Node types
// which can create aliases are: CheckCastPP, Phi, and any store (if there is also a load from
// the location.)
// Note:  this checks for aliases created in this compilation, not ones which may
//        be potentially created at call sites.
static bool can_cause_alias(Node *n, PhaseTransform *phase) {
  bool possible_alias = false;

  if (n->is_Store()) {
    possible_alias = !n->as_Store()->value_never_loaded(phase);
  } else {
    int opc = n->Opcode();
    possible_alias = n->is_Phi() ||
        opc == Op_CheckCastPP ||
        opc == Op_StorePConditional ||
        opc == Op_CompareAndSwapP ||
        opc == Op_CompareAndSwapN;
  }
  return possible_alias;
}

//------------------------------Value------------------------------------------
// Take 'join' of input and cast-up type, unless working with an Interface
const Type *CheckCastPPNode::Value( PhaseTransform *phase ) const {
  if( in(0) && phase->type(in(0)) == Type::TOP ) return Type::TOP;

  const Type *inn = phase->type(in(1));
  if( inn == Type::TOP ) return Type::TOP;  // No information yet

  const TypePtr *in_type   = inn->isa_ptr();
  const TypePtr *my_type   = _type->isa_ptr();
  const Type *result = _type;
  if( in_type != NULL && my_type != NULL ) {
    TypePtr::PTR   in_ptr    = in_type->ptr();
    if( in_ptr == TypePtr::Null ) {
      result = in_type;
    } else if( in_ptr == TypePtr::Constant ) {
      // Casting a constant oop to an interface?
      // (i.e., a String to a Comparable?)
      // Then return the interface.
      const TypeOopPtr *jptr = my_type->isa_oopptr();
      assert( jptr, "" );
      result =  (jptr->klass()->is_interface() || !in_type->higher_equal(_type))
        ? my_type->cast_to_ptr_type( TypePtr::NotNull )
        : in_type;
    } else {
      result =  my_type->cast_to_ptr_type( my_type->join_ptr(in_ptr) );
    }
  }
  return result;

  // JOIN NOT DONE HERE BECAUSE OF INTERFACE ISSUES.
  // FIX THIS (DO THE JOIN) WHEN UNION TYPES APPEAR!

  //
  // Remove this code after overnight run indicates no performance
  // loss from not performing JOIN at CheckCastPPNode
  //
  // const TypeInstPtr *in_oop = in->isa_instptr();
  // const TypeInstPtr *my_oop = _type->isa_instptr();
  // // If either input is an 'interface', return destination type
  // assert (in_oop == NULL || in_oop->klass() != NULL, "");
  // assert (my_oop == NULL || my_oop->klass() != NULL, "");
  // if( (in_oop && in_oop->klass()->klass_part()->is_interface())
  //   ||(my_oop && my_oop->klass()->klass_part()->is_interface()) ) {
  //   TypePtr::PTR  in_ptr = in->isa_ptr() ? in->is_ptr()->_ptr : TypePtr::BotPTR;
  //   // Preserve cast away nullness for interfaces
  //   if( in_ptr == TypePtr::NotNull && my_oop && my_oop->_ptr == TypePtr::BotPTR ) {
  //     return my_oop->cast_to_ptr_type(TypePtr::NotNull);
  //   }
  //   return _type;
  // }
  //
  // // Neither the input nor the destination type is an interface,
  //
  // // history: JOIN used to cause weird corner case bugs
  // //          return (in == TypeOopPtr::NULL_PTR) ? in : _type;
  // // JOIN picks up NotNull in common instance-of/check-cast idioms, both oops.
  // // JOIN does not preserve NotNull in other cases, e.g. RawPtr vs InstPtr
  // const Type *join = in->join(_type);
  // // Check if join preserved NotNull'ness for pointers
  // if( join->isa_ptr() && _type->isa_ptr() ) {
  //   TypePtr::PTR join_ptr = join->is_ptr()->_ptr;
  //   TypePtr::PTR type_ptr = _type->is_ptr()->_ptr;
  //   // If there isn't any NotNull'ness to preserve
  //   // OR if join preserved NotNull'ness then return it
  //   if( type_ptr == TypePtr::BotPTR  || type_ptr == TypePtr::Null ||
  //       join_ptr == TypePtr::NotNull || join_ptr == TypePtr::Constant ) {
  //     return join;
  //   }
  //   // ELSE return same old type as before
  //   return _type;
  // }
  // // Not joining two pointers
  // return join;
}

//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.  Strip out
// control copies
Node *CheckCastPPNode::Ideal(PhaseGVN *phase, bool can_reshape){
  return (in(0) && remove_dead_region(phase, can_reshape)) ? this : NULL;
}


Node* DecodeNNode::Identity(PhaseTransform* phase) {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return in(1);

  if (in(1)->is_EncodeP()) {
    // (DecodeN (EncodeP p)) -> p
    return in(1)->in(1);
  }
  return this;
}

const Type *DecodeNNode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if (t == Type::TOP) return Type::TOP;
  if (t == TypeNarrowOop::NULL_PTR) return TypePtr::NULL_PTR;

  assert(t->isa_narrowoop(), "only  narrowoop here");
  return t->make_ptr();
}

Node* EncodePNode::Identity(PhaseTransform* phase) {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return in(1);

  if (in(1)->is_DecodeN()) {
    // (EncodeP (DecodeN p)) -> p
    return in(1)->in(1);
  }
  return this;
}

const Type *EncodePNode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if (t == Type::TOP) return Type::TOP;
  if (t == TypePtr::NULL_PTR) return TypeNarrowOop::NULL_PTR;

  assert(t->isa_oopptr(), "only oopptr here");
  return t->make_narrowoop();
}


Node *EncodePNode::Ideal_DU_postCCP( PhaseCCP *ccp ) {
  return MemNode::Ideal_common_DU_postCCP(ccp, this, in(1));
}

//=============================================================================
//------------------------------Identity---------------------------------------
Node *Conv2BNode::Identity( PhaseTransform *phase ) {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return in(1);
  if( t == TypeInt::ZERO ) return in(1);
  if( t == TypeInt::ONE ) return in(1);
  if( t == TypeInt::BOOL ) return in(1);
  return this;
}

//------------------------------Value------------------------------------------
const Type *Conv2BNode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return Type::TOP;
  if( t == TypeInt::ZERO ) return TypeInt::ZERO;
  if( t == TypePtr::NULL_PTR ) return TypeInt::ZERO;
  const TypePtr *tp = t->isa_ptr();
  if( tp != NULL ) {
    if( tp->ptr() == TypePtr::AnyNull ) return Type::TOP;
    if( tp->ptr() == TypePtr::Constant) return TypeInt::ONE;
    if (tp->ptr() == TypePtr::NotNull)  return TypeInt::ONE;
    return TypeInt::BOOL;
  }
  if (t->base() != Type::Int) return TypeInt::BOOL;
  const TypeInt *ti = t->is_int();
  if( ti->_hi < 0 || ti->_lo > 0 ) return TypeInt::ONE;
  return TypeInt::BOOL;
}


// The conversions operations are all Alpha sorted.  Please keep it that way!
//=============================================================================
//------------------------------Value------------------------------------------
const Type *ConvD2FNode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return Type::TOP;
  if( t == Type::DOUBLE ) return Type::FLOAT;
  const TypeD *td = t->is_double_constant();
  return TypeF::make( (float)td->getd() );
}

//------------------------------Identity---------------------------------------
// Float's can be converted to doubles with no loss of bits.  Hence
// converting a float to a double and back to a float is a NOP.
Node *ConvD2FNode::Identity(PhaseTransform *phase) {
  return (in(1)->Opcode() == Op_ConvF2D) ? in(1)->in(1) : this;
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type *ConvD2INode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return Type::TOP;
  if( t == Type::DOUBLE ) return TypeInt::INT;
  const TypeD *td = t->is_double_constant();
  return TypeInt::make( SharedRuntime::d2i( td->getd() ) );
}

//------------------------------Ideal------------------------------------------
// If converting to an int type, skip any rounding nodes
Node *ConvD2INode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if( in(1)->Opcode() == Op_RoundDouble )
    set_req(1,in(1)->in(1));
  return NULL;
}

//------------------------------Identity---------------------------------------
// Int's can be converted to doubles with no loss of bits.  Hence
// converting an integer to a double and back to an integer is a NOP.
Node *ConvD2INode::Identity(PhaseTransform *phase) {
  return (in(1)->Opcode() == Op_ConvI2D) ? in(1)->in(1) : this;
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type *ConvD2LNode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return Type::TOP;
  if( t == Type::DOUBLE ) return TypeLong::LONG;
  const TypeD *td = t->is_double_constant();
  return TypeLong::make( SharedRuntime::d2l( td->getd() ) );
}

//------------------------------Identity---------------------------------------
Node *ConvD2LNode::Identity(PhaseTransform *phase) {
  // Remove ConvD2L->ConvL2D->ConvD2L sequences.
  if( in(1)       ->Opcode() == Op_ConvL2D &&
      in(1)->in(1)->Opcode() == Op_ConvD2L )
    return in(1)->in(1);
  return this;
}

//------------------------------Ideal------------------------------------------
// If converting to an int type, skip any rounding nodes
Node *ConvD2LNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if( in(1)->Opcode() == Op_RoundDouble )
    set_req(1,in(1)->in(1));
  return NULL;
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type *ConvF2DNode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return Type::TOP;
  if( t == Type::FLOAT ) return Type::DOUBLE;
  const TypeF *tf = t->is_float_constant();
#ifndef IA64
  return TypeD::make( (double)tf->getf() );
#else
  float x = tf->getf();
  return TypeD::make( (x == 0.0f) ? (double)x : (double)x + ia64_double_zero );
#endif
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type *ConvF2INode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP )       return Type::TOP;
  if( t == Type::FLOAT ) return TypeInt::INT;
  const TypeF *tf = t->is_float_constant();
  return TypeInt::make( SharedRuntime::f2i( tf->getf() ) );
}

//------------------------------Identity---------------------------------------
Node *ConvF2INode::Identity(PhaseTransform *phase) {
  // Remove ConvF2I->ConvI2F->ConvF2I sequences.
  if( in(1)       ->Opcode() == Op_ConvI2F &&
      in(1)->in(1)->Opcode() == Op_ConvF2I )
    return in(1)->in(1);
  return this;
}

//------------------------------Ideal------------------------------------------
// If converting to an int type, skip any rounding nodes
Node *ConvF2INode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if( in(1)->Opcode() == Op_RoundFloat )
    set_req(1,in(1)->in(1));
  return NULL;
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type *ConvF2LNode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP )       return Type::TOP;
  if( t == Type::FLOAT ) return TypeLong::LONG;
  const TypeF *tf = t->is_float_constant();
  return TypeLong::make( SharedRuntime::f2l( tf->getf() ) );
}

//------------------------------Identity---------------------------------------
Node *ConvF2LNode::Identity(PhaseTransform *phase) {
  // Remove ConvF2L->ConvL2F->ConvF2L sequences.
  if( in(1)       ->Opcode() == Op_ConvL2F &&
      in(1)->in(1)->Opcode() == Op_ConvF2L )
    return in(1)->in(1);
  return this;
}

//------------------------------Ideal------------------------------------------
// If converting to an int type, skip any rounding nodes
Node *ConvF2LNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if( in(1)->Opcode() == Op_RoundFloat )
    set_req(1,in(1)->in(1));
  return NULL;
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type *ConvI2DNode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return Type::TOP;
  const TypeInt *ti = t->is_int();
  if( ti->is_con() ) return TypeD::make( (double)ti->get_con() );
  return bottom_type();
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type *ConvI2FNode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return Type::TOP;
  const TypeInt *ti = t->is_int();
  if( ti->is_con() ) return TypeF::make( (float)ti->get_con() );
  return bottom_type();
}

//------------------------------Identity---------------------------------------
Node *ConvI2FNode::Identity(PhaseTransform *phase) {
  // Remove ConvI2F->ConvF2I->ConvI2F sequences.
  if( in(1)       ->Opcode() == Op_ConvF2I &&
      in(1)->in(1)->Opcode() == Op_ConvI2F )
    return in(1)->in(1);
  return this;
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type *ConvI2LNode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return Type::TOP;
  const TypeInt *ti = t->is_int();
  const Type* tl = TypeLong::make(ti->_lo, ti->_hi, ti->_widen);
  // Join my declared type against my incoming type.
  tl = tl->filter(_type);
  return tl;
}

#ifdef _LP64
static inline bool long_ranges_overlap(jlong lo1, jlong hi1,
                                       jlong lo2, jlong hi2) {
  // Two ranges overlap iff one range's low point falls in the other range.
  return (lo2 <= lo1 && lo1 <= hi2) || (lo1 <= lo2 && lo2 <= hi1);
}
#endif

//------------------------------Ideal------------------------------------------
Node *ConvI2LNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  const TypeLong* this_type = this->type()->is_long();
  Node* this_changed = NULL;

  // If _major_progress, then more loop optimizations follow.  Do NOT
  // remove this node's type assertion until no more loop ops can happen.
  // The progress bit is set in the major loop optimizations THEN comes the
  // call to IterGVN and any chance of hitting this code.  Cf. Opaque1Node.
  if (can_reshape && !phase->C->major_progress()) {
    const TypeInt* in_type = phase->type(in(1))->isa_int();
    if (in_type != NULL && this_type != NULL &&
        (in_type->_lo != this_type->_lo ||
         in_type->_hi != this_type->_hi)) {
      // Although this WORSENS the type, it increases GVN opportunities,
      // because I2L nodes with the same input will common up, regardless
      // of slightly differing type assertions.  Such slight differences
      // arise routinely as a result of loop unrolling, so this is a
      // post-unrolling graph cleanup.  Choose a type which depends only
      // on my input.  (Exception:  Keep a range assertion of >=0 or <0.)
      jlong lo1 = this_type->_lo;
      jlong hi1 = this_type->_hi;
      int   w1  = this_type->_widen;
      if (lo1 != (jint)lo1 ||
          hi1 != (jint)hi1 ||
          lo1 > hi1) {
        // Overflow leads to wraparound, wraparound leads to range saturation.
        lo1 = min_jint; hi1 = max_jint;
      } else if (lo1 >= 0) {
        // Keep a range assertion of >=0.
        lo1 = 0;        hi1 = max_jint;
      } else if (hi1 < 0) {
        // Keep a range assertion of <0.
        lo1 = min_jint; hi1 = -1;
      } else {
        lo1 = min_jint; hi1 = max_jint;
      }
      const TypeLong* wtype = TypeLong::make(MAX2((jlong)in_type->_lo, lo1),
                                             MIN2((jlong)in_type->_hi, hi1),
                                             MAX2((int)in_type->_widen, w1));
      if (wtype != type()) {
        set_type(wtype);
        // Note: this_type still has old type value, for the logic below.
        this_changed = this;
      }
    }
  }

#ifdef _LP64
  // Convert ConvI2L(AddI(x, y)) to AddL(ConvI2L(x), ConvI2L(y)) ,
  // but only if x and y have subranges that cannot cause 32-bit overflow,
  // under the assumption that x+y is in my own subrange this->type().

  // This assumption is based on a constraint (i.e., type assertion)
  // established in Parse::array_addressing or perhaps elsewhere.
  // This constraint has been adjoined to the "natural" type of
  // the incoming argument in(0).  We know (because of runtime
  // checks) - that the result value I2L(x+y) is in the joined range.
  // Hence we can restrict the incoming terms (x, y) to values such
  // that their sum also lands in that range.

  // This optimization is useful only on 64-bit systems, where we hope
  // the addition will end up subsumed in an addressing mode.
  // It is necessary to do this when optimizing an unrolled array
  // copy loop such as x[i++] = y[i++].

  // On 32-bit systems, it's better to perform as much 32-bit math as
  // possible before the I2L conversion, because 32-bit math is cheaper.
  // There's no common reason to "leak" a constant offset through the I2L.
  // Addressing arithmetic will not absorb it as part of a 64-bit AddL.

  Node* z = in(1);
  int op = z->Opcode();
  if (op == Op_AddI || op == Op_SubI) {
    Node* x = z->in(1);
    Node* y = z->in(2);
    assert (x != z && y != z, "dead loop in ConvI2LNode::Ideal");
    if (phase->type(x) == Type::TOP)  return this_changed;
    if (phase->type(y) == Type::TOP)  return this_changed;
    const TypeInt*  tx = phase->type(x)->is_int();
    const TypeInt*  ty = phase->type(y)->is_int();
    const TypeLong* tz = this_type;
    jlong xlo = tx->_lo;
    jlong xhi = tx->_hi;
    jlong ylo = ty->_lo;
    jlong yhi = ty->_hi;
    jlong zlo = tz->_lo;
    jlong zhi = tz->_hi;
    jlong vbit = CONST64(1) << BitsPerInt;
    int widen =  MAX2(tx->_widen, ty->_widen);
    if (op == Op_SubI) {
      jlong ylo0 = ylo;
      ylo = -yhi;
      yhi = -ylo0;
    }
    // See if x+y can cause positive overflow into z+2**32
    if (long_ranges_overlap(xlo+ylo, xhi+yhi, zlo+vbit, zhi+vbit)) {
      return this_changed;
    }
    // See if x+y can cause negative overflow into z-2**32
    if (long_ranges_overlap(xlo+ylo, xhi+yhi, zlo-vbit, zhi-vbit)) {
      return this_changed;
    }
    // Now it's always safe to assume x+y does not overflow.
    // This is true even if some pairs x,y might cause overflow, as long
    // as that overflow value cannot fall into [zlo,zhi].

    // Confident that the arithmetic is "as if infinite precision",
    // we can now use z's range to put constraints on those of x and y.
    // The "natural" range of x [xlo,xhi] can perhaps be narrowed to a
    // more "restricted" range by intersecting [xlo,xhi] with the
    // range obtained by subtracting y's range from the asserted range
    // of the I2L conversion.  Here's the interval arithmetic algebra:
    //    x == z-y == [zlo,zhi]-[ylo,yhi] == [zlo,zhi]+[-yhi,-ylo]
    //    => x in [zlo-yhi, zhi-ylo]
    //    => x in [zlo-yhi, zhi-ylo] INTERSECT [xlo,xhi]
    //    => x in [xlo MAX zlo-yhi, xhi MIN zhi-ylo]
    jlong rxlo = MAX2(xlo, zlo - yhi);
    jlong rxhi = MIN2(xhi, zhi - ylo);
    // And similarly, x changing place with y:
    jlong rylo = MAX2(ylo, zlo - xhi);
    jlong ryhi = MIN2(yhi, zhi - xlo);
    if (rxlo > rxhi || rylo > ryhi) {
      return this_changed;  // x or y is dying; don't mess w/ it
    }
    if (op == Op_SubI) {
      jlong rylo0 = rylo;
      rylo = -ryhi;
      ryhi = -rylo0;
    }

    Node* cx = phase->transform( new (phase->C, 2) ConvI2LNode(x, TypeLong::make(rxlo, rxhi, widen)) );
    Node* cy = phase->transform( new (phase->C, 2) ConvI2LNode(y, TypeLong::make(rylo, ryhi, widen)) );
    switch (op) {
    case Op_AddI:  return new (phase->C, 3) AddLNode(cx, cy);
    case Op_SubI:  return new (phase->C, 3) SubLNode(cx, cy);
    default:       ShouldNotReachHere();
    }
  }
#endif //_LP64

  return this_changed;
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type *ConvL2DNode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return Type::TOP;
  const TypeLong *tl = t->is_long();
  if( tl->is_con() ) return TypeD::make( (double)tl->get_con() );
  return bottom_type();
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type *ConvL2FNode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return Type::TOP;
  const TypeLong *tl = t->is_long();
  if( tl->is_con() ) return TypeF::make( (float)tl->get_con() );
  return bottom_type();
}

//=============================================================================
//----------------------------Identity-----------------------------------------
Node *ConvL2INode::Identity( PhaseTransform *phase ) {
  // Convert L2I(I2L(x)) => x
  if (in(1)->Opcode() == Op_ConvI2L)  return in(1)->in(1);
  return this;
}

//------------------------------Value------------------------------------------
const Type *ConvL2INode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return Type::TOP;
  const TypeLong *tl = t->is_long();
  if (tl->is_con())
    // Easy case.
    return TypeInt::make((jint)tl->get_con());
  return bottom_type();
}

//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.
// Blow off prior masking to int
Node *ConvL2INode::Ideal(PhaseGVN *phase, bool can_reshape) {
  Node *andl = in(1);
  uint andl_op = andl->Opcode();
  if( andl_op == Op_AndL ) {
    // Blow off prior masking to int
    if( phase->type(andl->in(2)) == TypeLong::make( 0xFFFFFFFF ) ) {
      set_req(1,andl->in(1));
      return this;
    }
  }

  // Swap with a prior add: convL2I(addL(x,y)) ==> addI(convL2I(x),convL2I(y))
  // This replaces an 'AddL' with an 'AddI'.
  if( andl_op == Op_AddL ) {
    // Don't do this for nodes which have more than one user since
    // we'll end up computing the long add anyway.
    if (andl->outcnt() > 1) return NULL;

    Node* x = andl->in(1);
    Node* y = andl->in(2);
    assert( x != andl && y != andl, "dead loop in ConvL2INode::Ideal" );
    if (phase->type(x) == Type::TOP)  return NULL;
    if (phase->type(y) == Type::TOP)  return NULL;
    Node *add1 = phase->transform(new (phase->C, 2) ConvL2INode(x));
    Node *add2 = phase->transform(new (phase->C, 2) ConvL2INode(y));
    return new (phase->C, 3) AddINode(add1,add2);
  }

  // Disable optimization: LoadL->ConvL2I ==> LoadI.
  // It causes problems (sizes of Load and Store nodes do not match)
  // in objects initialization code and Escape Analysis.
  return NULL;
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type *CastX2PNode::Value( PhaseTransform *phase ) const {
  const Type* t = phase->type(in(1));
  if (t->base() == Type_X && t->singleton()) {
    uintptr_t bits = (uintptr_t) t->is_intptr_t()->get_con();
    if (bits == 0)   return TypePtr::NULL_PTR;
    return TypeRawPtr::make((address) bits);
  }
  return CastX2PNode::bottom_type();
}

//------------------------------Idealize---------------------------------------
static inline bool fits_in_int(const Type* t, bool but_not_min_int = false) {
  if (t == Type::TOP)  return false;
  const TypeX* tl = t->is_intptr_t();
  jint lo = min_jint;
  jint hi = max_jint;
  if (but_not_min_int)  ++lo;  // caller wants to negate the value w/o overflow
  return (tl->_lo >= lo) && (tl->_hi <= hi);
}

static inline Node* addP_of_X2P(PhaseGVN *phase,
                                Node* base,
                                Node* dispX,
                                bool negate = false) {
  if (negate) {
    dispX = new (phase->C, 3) SubXNode(phase->MakeConX(0), phase->transform(dispX));
  }
  return new (phase->C, 4) AddPNode(phase->C->top(),
                          phase->transform(new (phase->C, 2) CastX2PNode(base)),
                          phase->transform(dispX));
}

Node *CastX2PNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // convert CastX2P(AddX(x, y)) to AddP(CastX2P(x), y) if y fits in an int
  int op = in(1)->Opcode();
  Node* x;
  Node* y;
  switch (op) {
  case Op_SubX:
    x = in(1)->in(1);
    y = in(1)->in(2);
    if (fits_in_int(phase->type(y), true)) {
      return addP_of_X2P(phase, x, y, true);
    }
    break;
  case Op_AddX:
    x = in(1)->in(1);
    y = in(1)->in(2);
    if (fits_in_int(phase->type(y))) {
      return addP_of_X2P(phase, x, y);
    }
    if (fits_in_int(phase->type(x))) {
      return addP_of_X2P(phase, y, x);
    }
    break;
  }
  return NULL;
}

//------------------------------Identity---------------------------------------
Node *CastX2PNode::Identity( PhaseTransform *phase ) {
  if (in(1)->Opcode() == Op_CastP2X)  return in(1)->in(1);
  return this;
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type *CastP2XNode::Value( PhaseTransform *phase ) const {
  const Type* t = phase->type(in(1));
  if (t->base() == Type::RawPtr && t->singleton()) {
    uintptr_t bits = (uintptr_t) t->is_rawptr()->get_con();
    return TypeX::make(bits);
  }
  return CastP2XNode::bottom_type();
}

Node *CastP2XNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  return (in(0) && remove_dead_region(phase, can_reshape)) ? this : NULL;
}

//------------------------------Identity---------------------------------------
Node *CastP2XNode::Identity( PhaseTransform *phase ) {
  if (in(1)->Opcode() == Op_CastX2P)  return in(1)->in(1);
  return this;
}


//=============================================================================
//------------------------------Identity---------------------------------------
// Remove redundant roundings
Node *RoundFloatNode::Identity( PhaseTransform *phase ) {
  assert(Matcher::strict_fp_requires_explicit_rounding, "should only generate for Intel");
  // Do not round constants
  if (phase->type(in(1))->base() == Type::FloatCon)  return in(1);
  int op = in(1)->Opcode();
  // Redundant rounding
  if( op == Op_RoundFloat ) return in(1);
  // Already rounded
  if( op == Op_Parm ) return in(1);
  if( op == Op_LoadF ) return in(1);
  return this;
}

//------------------------------Value------------------------------------------
const Type *RoundFloatNode::Value( PhaseTransform *phase ) const {
  return phase->type( in(1) );
}

//=============================================================================
//------------------------------Identity---------------------------------------
// Remove redundant roundings.  Incoming arguments are already rounded.
Node *RoundDoubleNode::Identity( PhaseTransform *phase ) {
  assert(Matcher::strict_fp_requires_explicit_rounding, "should only generate for Intel");
  // Do not round constants
  if (phase->type(in(1))->base() == Type::DoubleCon)  return in(1);
  int op = in(1)->Opcode();
  // Redundant rounding
  if( op == Op_RoundDouble ) return in(1);
  // Already rounded
  if( op == Op_Parm ) return in(1);
  if( op == Op_LoadD ) return in(1);
  if( op == Op_ConvF2D ) return in(1);
  if( op == Op_ConvI2D ) return in(1);
  return this;
}

//------------------------------Value------------------------------------------
const Type *RoundDoubleNode::Value( PhaseTransform *phase ) const {
  return phase->type( in(1) );
}


//=============================================================================
// Do not allow value-numbering
uint Opaque1Node::hash() const { return NO_HASH; }
uint Opaque1Node::cmp( const Node &n ) const {
  return (&n == this);          // Always fail except on self
}

//------------------------------Identity---------------------------------------
// If _major_progress, then more loop optimizations follow.  Do NOT remove
// the opaque Node until no more loop ops can happen.  Note the timing of
// _major_progress; it's set in the major loop optimizations THEN comes the
// call to IterGVN and any chance of hitting this code.  Hence there's no
// phase-ordering problem with stripping Opaque1 in IGVN followed by some
// more loop optimizations that require it.
Node *Opaque1Node::Identity( PhaseTransform *phase ) {
  return phase->C->major_progress() ? this : in(1);
}

//=============================================================================
// A node to prevent unwanted optimizations.  Allows constant folding.  Stops
// value-numbering, most Ideal calls or Identity functions.  This Node is
// specifically designed to prevent the pre-increment value of a loop trip
// counter from being live out of the bottom of the loop (hence causing the
// pre- and post-increment values both being live and thus requiring an extra
// temp register and an extra move).  If we "accidentally" optimize through
// this kind of a Node, we'll get slightly pessimal, but correct, code.  Thus
// it's OK to be slightly sloppy on optimizations here.

// Do not allow value-numbering
uint Opaque2Node::hash() const { return NO_HASH; }
uint Opaque2Node::cmp( const Node &n ) const {
  return (&n == this);          // Always fail except on self
}


//------------------------------Value------------------------------------------
const Type *MoveL2DNode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return Type::TOP;
  const TypeLong *tl = t->is_long();
  if( !tl->is_con() ) return bottom_type();
  JavaValue v;
  v.set_jlong(tl->get_con());
  return TypeD::make( v.get_jdouble() );
}

//------------------------------Value------------------------------------------
const Type *MoveI2FNode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return Type::TOP;
  const TypeInt *ti = t->is_int();
  if( !ti->is_con() )   return bottom_type();
  JavaValue v;
  v.set_jint(ti->get_con());
  return TypeF::make( v.get_jfloat() );
}

//------------------------------Value------------------------------------------
const Type *MoveF2INode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP )       return Type::TOP;
  if( t == Type::FLOAT ) return TypeInt::INT;
  const TypeF *tf = t->is_float_constant();
  JavaValue v;
  v.set_jfloat(tf->getf());
  return TypeInt::make( v.get_jint() );
}

//------------------------------Value------------------------------------------
const Type *MoveD2LNode::Value( PhaseTransform *phase ) const {
  const Type *t = phase->type( in(1) );
  if( t == Type::TOP ) return Type::TOP;
  if( t == Type::DOUBLE ) return TypeLong::LONG;
  const TypeD *td = t->is_double_constant();
  JavaValue v;
  v.set_jdouble(td->getd());
  return TypeLong::make( v.get_jlong() );
}

//------------------------------Value------------------------------------------
const Type* CountLeadingZerosINode::Value(PhaseTransform* phase) const {
  const Type* t = phase->type(in(1));
  if (t == Type::TOP) return Type::TOP;
  const TypeInt* ti = t->isa_int();
  if (ti && ti->is_con()) {
    jint i = ti->get_con();
    // HD, Figure 5-6
    if (i == 0)
      return TypeInt::make(BitsPerInt);
    int n = 1;
    unsigned int x = i;
    if (x >> 16 == 0) { n += 16; x <<= 16; }
    if (x >> 24 == 0) { n +=  8; x <<=  8; }
    if (x >> 28 == 0) { n +=  4; x <<=  4; }
    if (x >> 30 == 0) { n +=  2; x <<=  2; }
    n -= x >> 31;
    return TypeInt::make(n);
  }
  return TypeInt::INT;
}

//------------------------------Value------------------------------------------
const Type* CountLeadingZerosLNode::Value(PhaseTransform* phase) const {
  const Type* t = phase->type(in(1));
  if (t == Type::TOP) return Type::TOP;
  const TypeLong* tl = t->isa_long();
  if (tl && tl->is_con()) {
    jlong l = tl->get_con();
    // HD, Figure 5-6
    if (l == 0)
      return TypeInt::make(BitsPerLong);
    int n = 1;
    unsigned int x = (((julong) l) >> 32);
    if (x == 0) { n += 32; x = (int) l; }
    if (x >> 16 == 0) { n += 16; x <<= 16; }
    if (x >> 24 == 0) { n +=  8; x <<=  8; }
    if (x >> 28 == 0) { n +=  4; x <<=  4; }
    if (x >> 30 == 0) { n +=  2; x <<=  2; }
    n -= x >> 31;
    return TypeInt::make(n);
  }
  return TypeInt::INT;
}

//------------------------------Value------------------------------------------
const Type* CountTrailingZerosINode::Value(PhaseTransform* phase) const {
  const Type* t = phase->type(in(1));
  if (t == Type::TOP) return Type::TOP;
  const TypeInt* ti = t->isa_int();
  if (ti && ti->is_con()) {
    jint i = ti->get_con();
    // HD, Figure 5-14
    int y;
    if (i == 0)
      return TypeInt::make(BitsPerInt);
    int n = 31;
    y = i << 16; if (y != 0) { n = n - 16; i = y; }
    y = i <<  8; if (y != 0) { n = n -  8; i = y; }
    y = i <<  4; if (y != 0) { n = n -  4; i = y; }
    y = i <<  2; if (y != 0) { n = n -  2; i = y; }
    y = i <<  1; if (y != 0) { n = n -  1; }
    return TypeInt::make(n);
  }
  return TypeInt::INT;
}

//------------------------------Value------------------------------------------
const Type* CountTrailingZerosLNode::Value(PhaseTransform* phase) const {
  const Type* t = phase->type(in(1));
  if (t == Type::TOP) return Type::TOP;
  const TypeLong* tl = t->isa_long();
  if (tl && tl->is_con()) {
    jlong l = tl->get_con();
    // HD, Figure 5-14
    int x, y;
    if (l == 0)
      return TypeInt::make(BitsPerLong);
    int n = 63;
    y = (int) l; if (y != 0) { n = n - 32; x = y; } else x = (((julong) l) >> 32);
    y = x << 16; if (y != 0) { n = n - 16; x = y; }
    y = x <<  8; if (y != 0) { n = n -  8; x = y; }
    y = x <<  4; if (y != 0) { n = n -  4; x = y; }
    y = x <<  2; if (y != 0) { n = n -  2; x = y; }
    y = x <<  1; if (y != 0) { n = n -  1; }
    return TypeInt::make(n);
  }
  return TypeInt::INT;
}
