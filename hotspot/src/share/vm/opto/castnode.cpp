/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/addnode.hpp"
#include "opto/castnode.hpp"
#include "opto/connode.hpp"
#include "opto/matcher.hpp"
#include "opto/phaseX.hpp"
#include "opto/subnode.hpp"
#include "opto/type.hpp"

//=============================================================================
// If input is already higher or equal to cast type, then this is an identity.
Node *ConstraintCastNode::Identity( PhaseTransform *phase ) {
  return phase->type(in(1))->higher_equal_speculative(_type) ? in(1) : this;
}

//------------------------------Value------------------------------------------
// Take 'join' of input and cast-up type
const Type *ConstraintCastNode::Value( PhaseTransform *phase ) const {
  if( in(0) && phase->type(in(0)) == Type::TOP ) return Type::TOP;
  const Type* ft = phase->type(in(1))->filter_speculative(_type);

#ifdef ASSERT
  // Previous versions of this function had some special case logic,
  // which is no longer necessary.  Make sure of the required effects.
  switch (Opcode()) {
    case Op_CastII:
    {
      const Type* t1 = phase->type(in(1));
      if( t1 == Type::TOP )  assert(ft == Type::TOP, "special case #1");
      const Type* rt = t1->join_speculative(_type);
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

uint CastIINode::size_of() const {
  return sizeof(*this);
}

uint CastIINode::cmp(const Node &n) const {
  return TypeNode::cmp(n) && ((CastIINode&)n)._carry_dependency == _carry_dependency;
}

Node *CastIINode::Identity(PhaseTransform *phase) {
  if (_carry_dependency) {
    return this;
  }
  return ConstraintCastNode::Identity(phase);
}

const Type *CastIINode::Value(PhaseTransform *phase) const {
  const Type *res = ConstraintCastNode::Value(phase);

  // Try to improve the type of the CastII if we recognize a CmpI/If
  // pattern.
  if (_carry_dependency) {
    if (in(0) != NULL && in(0)->in(0) != NULL && in(0)->in(0)->is_If()) {
      assert(in(0)->is_IfFalse() || in(0)->is_IfTrue(), "should be If proj");
      Node* proj = in(0);
      if (proj->in(0)->in(1)->is_Bool()) {
        Node* b = proj->in(0)->in(1);
        if (b->in(1)->Opcode() == Op_CmpI) {
          Node* cmp = b->in(1);
          if (cmp->in(1) == in(1) && phase->type(cmp->in(2))->isa_int()) {
            const TypeInt* in2_t = phase->type(cmp->in(2))->is_int();
            const Type* t = TypeInt::INT;
            BoolTest test = b->as_Bool()->_test;
            if (proj->is_IfFalse()) {
              test = test.negate();
            }
            BoolTest::mask m = test._test;
            jlong lo_long = min_jint;
            jlong hi_long = max_jint;
            if (m == BoolTest::le || m == BoolTest::lt) {
              hi_long = in2_t->_hi;
              if (m == BoolTest::lt) {
                hi_long -= 1;
              }
            } else if (m == BoolTest::ge || m == BoolTest::gt) {
              lo_long = in2_t->_lo;
              if (m == BoolTest::gt) {
                lo_long += 1;
              }
            } else if (m == BoolTest::eq) {
              lo_long = in2_t->_lo;
              hi_long = in2_t->_hi;
            } else if (m == BoolTest::ne) {
              // can't do any better
            } else {
              stringStream ss;
              test.dump_on(&ss);
              fatal("unexpected comparison %s", ss.as_string());
            }
            int lo_int = (int)lo_long;
            int hi_int = (int)hi_long;

            if (lo_long != (jlong)lo_int) {
              lo_int = min_jint;
            }
            if (hi_long != (jlong)hi_int) {
              hi_int = max_jint;
            }

            t = TypeInt::make(lo_int, hi_int, Type::WidenMax);

            res = res->filter_speculative(t);

            return res;
          }
        }
      }
    }
  }
  return res;
}

#ifndef PRODUCT
void CastIINode::dump_spec(outputStream *st) const {
  TypeNode::dump_spec(st);
  if (_carry_dependency) {
    st->print(" carry dependency");
  }
}
#endif

//=============================================================================
//------------------------------Identity---------------------------------------
// If input is already higher or equal to cast type, then this is an identity.
Node *CheckCastPPNode::Identity( PhaseTransform *phase ) {
  // Toned down to rescue meeting at a Phi 3 different oops all implementing
  // the same interface.  CompileTheWorld starting at 502, kd12rc1.zip.
  return (phase->type(in(1)) == phase->type(this)) ? in(1) : this;
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
    if (in_ptr == TypePtr::Null) {
      result = in_type;
    } else if (in_ptr == TypePtr::Constant) {
      const TypeOopPtr *jptr = my_type->isa_oopptr();
      assert(jptr, "");
      result = !in_type->higher_equal(_type)
      ? my_type->cast_to_ptr_type(TypePtr::NotNull)
      : in_type;
    } else {
      result =  my_type->cast_to_ptr_type( my_type->join_ptr(in_ptr) );
    }
  }

  // This is the code from TypePtr::xmeet() that prevents us from
  // having 2 ways to represent the same type. We have to replicate it
  // here because we don't go through meet/join.
  if (result->remove_speculative() == result->speculative()) {
    result = result->remove_speculative();
  }

  // Same as above: because we don't go through meet/join, remove the
  // speculative type if we know we won't use it.
  return result->cleanup_speculative();

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
  // if( (in_oop && in_oop->klass()->is_interface())
  //   ||(my_oop && my_oop->klass()->is_interface()) ) {
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

//=============================================================================
//------------------------------Value------------------------------------------
const Type *CastX2PNode::Value( PhaseTransform *phase ) const {
  const Type* t = phase->type(in(1));
  if (t == Type::TOP) return Type::TOP;
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
    dispX = new SubXNode(phase->MakeConX(0), phase->transform(dispX));
  }
  return new AddPNode(phase->C->top(),
                      phase->transform(new CastX2PNode(base)),
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
    // Avoid ideal transformations ping-pong between this and AddP for raw pointers.
    if (phase->find_intptr_t_con(x, -1) == 0)
    break;
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
  if (t == Type::TOP) return Type::TOP;
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
