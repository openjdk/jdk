/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.inline.hpp"
#include "opto/addnode.hpp"
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/divnode.hpp"
#include "opto/machnode.hpp"
#include "opto/matcher.hpp"
#include "opto/movenode.hpp"
#include "opto/mulnode.hpp"
#include "opto/phaseX.hpp"
#include "opto/runtime.hpp"
#include "opto/subnode.hpp"
#include "utilities/powerOfTwo.hpp"

// Portions of code courtesy of Clifford Click

// Optimization - Graph Style

#include <math.h>

ModFloatingNode::ModFloatingNode(Compile* C, const TypeFunc* tf, address addr, const char* name) : CallLeafPureNode(tf, addr, name, TypeRawPtr::BOTTOM) {
  add_flag(Flag_is_macro);
  C->add_macro_node(this);
}

ModDNode::ModDNode(Compile* C, Node* a, Node* b) : ModFloatingNode(C, OptoRuntime::Math_DD_D_Type(), CAST_FROM_FN_PTR(address, SharedRuntime::drem), "drem") {
  init_req(TypeFunc::Parms + 0, a);
  init_req(TypeFunc::Parms + 1, C->top());
  init_req(TypeFunc::Parms + 2, b);
  init_req(TypeFunc::Parms + 3, C->top());
}

ModFNode::ModFNode(Compile* C, Node* a, Node* b) : ModFloatingNode(C, OptoRuntime::modf_Type(), CAST_FROM_FN_PTR(address, SharedRuntime::frem), "frem") {
  init_req(TypeFunc::Parms + 0, a);
  init_req(TypeFunc::Parms + 1, b);
}

//----------------------magic_int_divide_constants-----------------------------
// Compute magic multiplier and shift constant for converting a 32 bit divide
// by constant into a multiply/shift/add series. Return false if calculations
// fail.
//
// Borrowed almost verbatim from Hacker's Delight by Henry S. Warren, Jr. with
// minor type name and parameter changes.
static bool magic_int_divide_constants(jint d, jint &M, jint &s) {
  int32_t p;
  uint32_t ad, anc, delta, q1, r1, q2, r2, t;
  const uint32_t two31 = 0x80000000L;     // 2**31.

  ad = ABS(d);
  if (d == 0 || d == 1) return false;
  t = two31 + ((uint32_t)d >> 31);
  anc = t - 1 - t%ad;     // Absolute value of nc.
  p = 31;                 // Init. p.
  q1 = two31/anc;         // Init. q1 = 2**p/|nc|.
  r1 = two31 - q1*anc;    // Init. r1 = rem(2**p, |nc|).
  q2 = two31/ad;          // Init. q2 = 2**p/|d|.
  r2 = two31 - q2*ad;     // Init. r2 = rem(2**p, |d|).
  do {
    p = p + 1;
    q1 = 2*q1;            // Update q1 = 2**p/|nc|.
    r1 = 2*r1;            // Update r1 = rem(2**p, |nc|).
    if (r1 >= anc) {      // (Must be an unsigned
      q1 = q1 + 1;        // comparison here).
      r1 = r1 - anc;
    }
    q2 = 2*q2;            // Update q2 = 2**p/|d|.
    r2 = 2*r2;            // Update r2 = rem(2**p, |d|).
    if (r2 >= ad) {       // (Must be an unsigned
      q2 = q2 + 1;        // comparison here).
      r2 = r2 - ad;
    }
    delta = ad - r2;
  } while (q1 < delta || (q1 == delta && r1 == 0));

  M = q2 + 1;
  if (d < 0) M = -M;      // Magic number and
  s = p - 32;             // shift amount to return.

  return true;
}

//--------------------------transform_int_divide-------------------------------
// Convert a division by constant divisor into an alternate Ideal graph.
// Return null if no transformation occurs.
static Node *transform_int_divide( PhaseGVN *phase, Node *dividend, jint divisor ) {

  // Check for invalid divisors
  assert( divisor != 0 && divisor != min_jint,
          "bad divisor for transforming to long multiply" );

  bool d_pos = divisor >= 0;
  jint d = d_pos ? divisor : -divisor;
  const int N = 32;

  // Result
  Node *q = nullptr;

  if (d == 1) {
    // division by +/- 1
    if (!d_pos) {
      // Just negate the value
      q = new SubINode(phase->intcon(0), dividend);
    }
  } else if ( is_power_of_2(d) ) {
    // division by +/- a power of 2

    // See if we can simply do a shift without rounding
    bool needs_rounding = true;
    const Type *dt = phase->type(dividend);
    const TypeInt *dti = dt->isa_int();
    if (dti && dti->_lo >= 0) {
      // we don't need to round a positive dividend
      needs_rounding = false;
    } else if( dividend->Opcode() == Op_AndI ) {
      // An AND mask of sufficient size clears the low bits and
      // I can avoid rounding.
      const TypeInt *andconi_t = phase->type( dividend->in(2) )->isa_int();
      if( andconi_t && andconi_t->is_con() ) {
        jint andconi = andconi_t->get_con();
        if( andconi < 0 && is_power_of_2(-andconi) && (-andconi) >= d ) {
          if( (-andconi) == d ) // Remove AND if it clears bits which will be shifted
            dividend = dividend->in(1);
          needs_rounding = false;
        }
      }
    }

    // Add rounding to the shift to handle the sign bit
    int l = log2i_graceful(d - 1) + 1;
    if (needs_rounding) {
      // Divide-by-power-of-2 can be made into a shift, but you have to do
      // more math for the rounding.  You need to add 0 for positive
      // numbers, and "i-1" for negative numbers.  Example: i=4, so the
      // shift is by 2.  You need to add 3 to negative dividends and 0 to
      // positive ones.  So (-7+3)>>2 becomes -1, (-4+3)>>2 becomes -1,
      // (-2+3)>>2 becomes 0, etc.

      // Compute 0 or -1, based on sign bit
      Node *sign = phase->transform(new RShiftINode(dividend, phase->intcon(N - 1)));
      // Mask sign bit to the low sign bits
      Node *round = phase->transform(new URShiftINode(sign, phase->intcon(N - l)));
      // Round up before shifting
      dividend = phase->transform(new AddINode(dividend, round));
    }

    // Shift for division
    q = new RShiftINode(dividend, phase->intcon(l));

    if (!d_pos) {
      q = new SubINode(phase->intcon(0), phase->transform(q));
    }
  } else {
    // Attempt the jint constant divide -> multiply transform found in
    //   "Division by Invariant Integers using Multiplication"
    //     by Granlund and Montgomery
    // See also "Hacker's Delight", chapter 10 by Warren.

    jint magic_const;
    jint shift_const;
    if (magic_int_divide_constants(d, magic_const, shift_const)) {
      Node *magic = phase->longcon(magic_const);
      Node *dividend_long = phase->transform(new ConvI2LNode(dividend));

      // Compute the high half of the dividend x magic multiplication
      Node *mul_hi = phase->transform(new MulLNode(dividend_long, magic));

      if (magic_const < 0) {
        mul_hi = phase->transform(new RShiftLNode(mul_hi, phase->intcon(N)));
        mul_hi = phase->transform(new ConvL2INode(mul_hi));

        // The magic multiplier is too large for a 32 bit constant. We've adjusted
        // it down by 2^32, but have to add 1 dividend back in after the multiplication.
        // This handles the "overflow" case described by Granlund and Montgomery.
        mul_hi = phase->transform(new AddINode(dividend, mul_hi));

        // Shift over the (adjusted) mulhi
        if (shift_const != 0) {
          mul_hi = phase->transform(new RShiftINode(mul_hi, phase->intcon(shift_const)));
        }
      } else {
        // No add is required, we can merge the shifts together.
        mul_hi = phase->transform(new RShiftLNode(mul_hi, phase->intcon(N + shift_const)));
        mul_hi = phase->transform(new ConvL2INode(mul_hi));
      }

      // Get a 0 or -1 from the sign of the dividend.
      Node *addend0 = mul_hi;
      Node *addend1 = phase->transform(new RShiftINode(dividend, phase->intcon(N-1)));

      // If the divisor is negative, swap the order of the input addends;
      // this has the effect of negating the quotient.
      if (!d_pos) {
        Node *temp = addend0; addend0 = addend1; addend1 = temp;
      }

      // Adjust the final quotient by subtracting -1 (adding 1)
      // from the mul_hi.
      q = new SubINode(addend0, addend1);
    }
  }

  return q;
}

//---------------------magic_long_divide_constants-----------------------------
// Compute magic multiplier and shift constant for converting a 64 bit divide
// by constant into a multiply/shift/add series. Return false if calculations
// fail.
//
// Borrowed almost verbatim from Hacker's Delight by Henry S. Warren, Jr. with
// minor type name and parameter changes.  Adjusted to 64 bit word width.
static bool magic_long_divide_constants(jlong d, jlong &M, jint &s) {
  int64_t p;
  uint64_t ad, anc, delta, q1, r1, q2, r2, t;
  const uint64_t two63 = UCONST64(0x8000000000000000);     // 2**63.

  ad = ABS(d);
  if (d == 0 || d == 1) return false;
  t = two63 + ((uint64_t)d >> 63);
  anc = t - 1 - t%ad;     // Absolute value of nc.
  p = 63;                 // Init. p.
  q1 = two63/anc;         // Init. q1 = 2**p/|nc|.
  r1 = two63 - q1*anc;    // Init. r1 = rem(2**p, |nc|).
  q2 = two63/ad;          // Init. q2 = 2**p/|d|.
  r2 = two63 - q2*ad;     // Init. r2 = rem(2**p, |d|).
  do {
    p = p + 1;
    q1 = 2*q1;            // Update q1 = 2**p/|nc|.
    r1 = 2*r1;            // Update r1 = rem(2**p, |nc|).
    if (r1 >= anc) {      // (Must be an unsigned
      q1 = q1 + 1;        // comparison here).
      r1 = r1 - anc;
    }
    q2 = 2*q2;            // Update q2 = 2**p/|d|.
    r2 = 2*r2;            // Update r2 = rem(2**p, |d|).
    if (r2 >= ad) {       // (Must be an unsigned
      q2 = q2 + 1;        // comparison here).
      r2 = r2 - ad;
    }
    delta = ad - r2;
  } while (q1 < delta || (q1 == delta && r1 == 0));

  M = q2 + 1;
  if (d < 0) M = -M;      // Magic number and
  s = p - 64;             // shift amount to return.

  return true;
}

//---------------------long_by_long_mulhi--------------------------------------
// Generate ideal node graph for upper half of a 64 bit x 64 bit multiplication
static Node* long_by_long_mulhi(PhaseGVN* phase, Node* dividend, jlong magic_const) {
  // If the architecture supports a 64x64 mulhi, there is
  // no need to synthesize it in ideal nodes.
  if (Matcher::has_match_rule(Op_MulHiL)) {
    Node* v = phase->longcon(magic_const);
    return new MulHiLNode(dividend, v);
  }

  // Taken from Hacker's Delight, Fig. 8-2. Multiply high signed.
  //
  // int mulhs(int u, int v) {
  //    unsigned u0, v0, w0;
  //    int u1, v1, w1, w2, t;
  //
  //    u0 = u & 0xFFFF;  u1 = u >> 16;
  //    v0 = v & 0xFFFF;  v1 = v >> 16;
  //    w0 = u0*v0;
  //    t  = u1*v0 + (w0 >> 16);
  //    w1 = t & 0xFFFF;
  //    w2 = t >> 16;
  //    w1 = u0*v1 + w1;
  //    return u1*v1 + w2 + (w1 >> 16);
  // }
  //
  // Note: The version above is for 32x32 multiplications, while the
  // following inline comments are adapted to 64x64.

  const int N = 64;

  // Dummy node to keep intermediate nodes alive during construction
  Node* hook = new Node(4);

  // u0 = u & 0xFFFFFFFF;  u1 = u >> 32;
  Node* u0 = phase->transform(new AndLNode(dividend, phase->longcon(0xFFFFFFFF)));
  Node* u1 = phase->transform(new RShiftLNode(dividend, phase->intcon(N / 2)));
  hook->init_req(0, u0);
  hook->init_req(1, u1);

  // v0 = v & 0xFFFFFFFF;  v1 = v >> 32;
  Node* v0 = phase->longcon(magic_const & 0xFFFFFFFF);
  Node* v1 = phase->longcon(magic_const >> (N / 2));

  // w0 = u0*v0;
  Node* w0 = phase->transform(new MulLNode(u0, v0));

  // t = u1*v0 + (w0 >> 32);
  Node* u1v0 = phase->transform(new MulLNode(u1, v0));
  Node* temp = phase->transform(new URShiftLNode(w0, phase->intcon(N / 2)));
  Node* t    = phase->transform(new AddLNode(u1v0, temp));
  hook->init_req(2, t);

  // w1 = t & 0xFFFFFFFF;
  Node* w1 = phase->transform(new AndLNode(t, phase->longcon(0xFFFFFFFF)));
  hook->init_req(3, w1);

  // w2 = t >> 32;
  Node* w2 = phase->transform(new RShiftLNode(t, phase->intcon(N / 2)));

  // w1 = u0*v1 + w1;
  Node* u0v1 = phase->transform(new MulLNode(u0, v1));
  w1         = phase->transform(new AddLNode(u0v1, w1));

  // return u1*v1 + w2 + (w1 >> 32);
  Node* u1v1  = phase->transform(new MulLNode(u1, v1));
  Node* temp1 = phase->transform(new AddLNode(u1v1, w2));
  Node* temp2 = phase->transform(new RShiftLNode(w1, phase->intcon(N / 2)));

  // Remove the bogus extra edges used to keep things alive
  hook->destruct(phase);

  return new AddLNode(temp1, temp2);
}


//--------------------------transform_long_divide------------------------------
// Convert a division by constant divisor into an alternate Ideal graph.
// Return null if no transformation occurs.
static Node *transform_long_divide( PhaseGVN *phase, Node *dividend, jlong divisor ) {
  // Check for invalid divisors
  assert( divisor != 0L && divisor != min_jlong,
          "bad divisor for transforming to long multiply" );

  bool d_pos = divisor >= 0;
  jlong d = d_pos ? divisor : -divisor;
  const int N = 64;

  // Result
  Node *q = nullptr;

  if (d == 1) {
    // division by +/- 1
    if (!d_pos) {
      // Just negate the value
      q = new SubLNode(phase->longcon(0), dividend);
    }
  } else if ( is_power_of_2(d) ) {

    // division by +/- a power of 2

    // See if we can simply do a shift without rounding
    bool needs_rounding = true;
    const Type *dt = phase->type(dividend);
    const TypeLong *dtl = dt->isa_long();

    if (dtl && dtl->_lo > 0) {
      // we don't need to round a positive dividend
      needs_rounding = false;
    } else if( dividend->Opcode() == Op_AndL ) {
      // An AND mask of sufficient size clears the low bits and
      // I can avoid rounding.
      const TypeLong *andconl_t = phase->type( dividend->in(2) )->isa_long();
      if( andconl_t && andconl_t->is_con() ) {
        jlong andconl = andconl_t->get_con();
        if( andconl < 0 && is_power_of_2(-andconl) && (-andconl) >= d ) {
          if( (-andconl) == d ) // Remove AND if it clears bits which will be shifted
            dividend = dividend->in(1);
          needs_rounding = false;
        }
      }
    }

    // Add rounding to the shift to handle the sign bit
    int l = log2i_graceful(d - 1) + 1;
    if (needs_rounding) {
      // Divide-by-power-of-2 can be made into a shift, but you have to do
      // more math for the rounding.  You need to add 0 for positive
      // numbers, and "i-1" for negative numbers.  Example: i=4, so the
      // shift is by 2.  You need to add 3 to negative dividends and 0 to
      // positive ones.  So (-7+3)>>2 becomes -1, (-4+3)>>2 becomes -1,
      // (-2+3)>>2 becomes 0, etc.

      // Compute 0 or -1, based on sign bit
      Node *sign = phase->transform(new RShiftLNode(dividend, phase->intcon(N - 1)));
      // Mask sign bit to the low sign bits
      Node *round = phase->transform(new URShiftLNode(sign, phase->intcon(N - l)));
      // Round up before shifting
      dividend = phase->transform(new AddLNode(dividend, round));
    }

    // Shift for division
    q = new RShiftLNode(dividend, phase->intcon(l));

    if (!d_pos) {
      q = new SubLNode(phase->longcon(0), phase->transform(q));
    }
  } else if ( !Matcher::use_asm_for_ldiv_by_con(d) ) { // Use hardware DIV instruction when
                                                       // it is faster than code generated below.
    // Attempt the jlong constant divide -> multiply transform found in
    //   "Division by Invariant Integers using Multiplication"
    //     by Granlund and Montgomery
    // See also "Hacker's Delight", chapter 10 by Warren.

    jlong magic_const;
    jint shift_const;
    if (magic_long_divide_constants(d, magic_const, shift_const)) {
      // Compute the high half of the dividend x magic multiplication
      Node *mul_hi = phase->transform(long_by_long_mulhi(phase, dividend, magic_const));

      // The high half of the 128-bit multiply is computed.
      if (magic_const < 0) {
        // The magic multiplier is too large for a 64 bit constant. We've adjusted
        // it down by 2^64, but have to add 1 dividend back in after the multiplication.
        // This handles the "overflow" case described by Granlund and Montgomery.
        mul_hi = phase->transform(new AddLNode(dividend, mul_hi));
      }

      // Shift over the (adjusted) mulhi
      if (shift_const != 0) {
        mul_hi = phase->transform(new RShiftLNode(mul_hi, phase->intcon(shift_const)));
      }

      // Get a 0 or -1 from the sign of the dividend.
      Node *addend0 = mul_hi;
      Node *addend1 = phase->transform(new RShiftLNode(dividend, phase->intcon(N-1)));

      // If the divisor is negative, swap the order of the input addends;
      // this has the effect of negating the quotient.
      if (!d_pos) {
        Node *temp = addend0; addend0 = addend1; addend1 = temp;
      }

      // Adjust the final quotient by subtracting -1 (adding 1)
      // from the mul_hi.
      q = new SubLNode(addend0, addend1);
    }
  }

  return q;
}

template <typename TypeClass, typename Unsigned>
Node* unsigned_div_ideal(PhaseGVN* phase, bool can_reshape, Node* div) {
  // Check for dead control input
  if (div->in(0) != nullptr && div->remove_dead_region(phase, can_reshape)) {
    return div;
  }
  // Don't bother trying to transform a dead node
  if (div->in(0) != nullptr && div->in(0)->is_top()) {
    return nullptr;
  }

  const Type* t = phase->type(div->in(2));
  if (t == Type::TOP) {
    return nullptr;
  }
  const TypeClass* type_divisor = t->cast<TypeClass>();

  // Check for useless control input
  // Check for excluding div-zero case
  if (div->in(0) != nullptr && (type_divisor->_hi < 0 || type_divisor->_lo > 0)) {
    div->set_req(0, nullptr); // Yank control input
    return div;
  }

  if (!type_divisor->is_con()) {
    return nullptr;
  }
  Unsigned divisor = static_cast<Unsigned>(type_divisor->get_con()); // Get divisor

  if (divisor == 0 || divisor == 1) {
    return nullptr; // Dividing by zero constant does not idealize
  }

  if (is_power_of_2(divisor)) {
    return make_urshift<TypeClass>(div->in(1), phase->intcon(log2i_graceful(divisor)));
  }

  return nullptr;
}


//=============================================================================
//------------------------------Identity---------------------------------------
// If the divisor is 1, we are an identity on the dividend.
Node* DivINode::Identity(PhaseGVN* phase) {
  return (phase->type( in(2) )->higher_equal(TypeInt::ONE)) ? in(1) : this;
}

//------------------------------Idealize---------------------------------------
// Divides can be changed to multiplies and/or shifts
Node *DivINode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if (in(0) && remove_dead_region(phase, can_reshape))  return this;
  // Don't bother trying to transform a dead node
  if( in(0) && in(0)->is_top() )  return nullptr;

  const Type *t = phase->type( in(2) );
  if( t == TypeInt::ONE )      // Identity?
    return nullptr;            // Skip it

  const TypeInt *ti = t->isa_int();
  if( !ti ) return nullptr;

  // Check for useless control input
  // Check for excluding div-zero case
  if (in(0) && (ti->_hi < 0 || ti->_lo > 0)) {
    set_req(0, nullptr);           // Yank control input
    return this;
  }

  if( !ti->is_con() ) return nullptr;
  jint i = ti->get_con();       // Get divisor

  if (i == 0) return nullptr;   // Dividing by zero constant does not idealize

  // Dividing by MININT does not optimize as a power-of-2 shift.
  if( i == min_jint ) return nullptr;

  return transform_int_divide( phase, in(1), i );
}

//------------------------------Value------------------------------------------
// A DivINode divides its inputs.  The third input is a Control input, used to
// prevent hoisting the divide above an unsafe test.
const Type* DivINode::Value(PhaseGVN* phase) const {
  // Either input is TOP ==> the result is TOP
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // x/x == 1 since we always generate the dynamic divisor check for 0.
  if (in(1) == in(2)) {
    return TypeInt::ONE;
  }

  // Either input is BOTTOM ==> the result is the local BOTTOM
  const Type *bot = bottom_type();
  if( (t1 == bot) || (t2 == bot) ||
      (t1 == Type::BOTTOM) || (t2 == Type::BOTTOM) )
    return bot;

  // Divide the two numbers.  We approximate.
  // If divisor is a constant and not zero
  const TypeInt *i1 = t1->is_int();
  const TypeInt *i2 = t2->is_int();
  int widen = MAX2(i1->_widen, i2->_widen);

  if( i2->is_con() && i2->get_con() != 0 ) {
    int32_t d = i2->get_con(); // Divisor
    jint lo, hi;
    if( d >= 0 ) {
      lo = i1->_lo/d;
      hi = i1->_hi/d;
    } else {
      if( d == -1 && i1->_lo == min_jint ) {
        // 'min_jint/-1' throws arithmetic exception during compilation
        lo = min_jint;
        // do not support holes, 'hi' must go to either min_jint or max_jint:
        // [min_jint, -10]/[-1,-1] ==> [min_jint] UNION [10,max_jint]
        hi = i1->_hi == min_jint ? min_jint : max_jint;
      } else {
        lo = i1->_hi/d;
        hi = i1->_lo/d;
      }
    }
    return TypeInt::make(lo, hi, widen);
  }

  // If the dividend is a constant
  if( i1->is_con() ) {
    int32_t d = i1->get_con();
    if( d < 0 ) {
      if( d == min_jint ) {
        //  (-min_jint) == min_jint == (min_jint / -1)
        return TypeInt::make(min_jint, max_jint/2 + 1, widen);
      } else {
        return TypeInt::make(d, -d, widen);
      }
    }
    return TypeInt::make(-d, d, widen);
  }

  // Otherwise we give up all hope
  return TypeInt::INT;
}


//=============================================================================
//------------------------------Identity---------------------------------------
// If the divisor is 1, we are an identity on the dividend.
Node* DivLNode::Identity(PhaseGVN* phase) {
  return (phase->type( in(2) )->higher_equal(TypeLong::ONE)) ? in(1) : this;
}

//------------------------------Idealize---------------------------------------
// Dividing by a power of 2 is a shift.
Node *DivLNode::Ideal( PhaseGVN *phase, bool can_reshape) {
  if (in(0) && remove_dead_region(phase, can_reshape))  return this;
  // Don't bother trying to transform a dead node
  if( in(0) && in(0)->is_top() )  return nullptr;

  const Type *t = phase->type( in(2) );
  if( t == TypeLong::ONE )      // Identity?
    return nullptr;             // Skip it

  const TypeLong *tl = t->isa_long();
  if( !tl ) return nullptr;

  // Check for useless control input
  // Check for excluding div-zero case
  if (in(0) && (tl->_hi < 0 || tl->_lo > 0)) {
    set_req(0, nullptr);         // Yank control input
    return this;
  }

  if( !tl->is_con() ) return nullptr;
  jlong l = tl->get_con();      // Get divisor

  if (l == 0) return nullptr;   // Dividing by zero constant does not idealize

  // Dividing by MINLONG does not optimize as a power-of-2 shift.
  if( l == min_jlong ) return nullptr;

  return transform_long_divide( phase, in(1), l );
}

//------------------------------Value------------------------------------------
// A DivLNode divides its inputs.  The third input is a Control input, used to
// prevent hoisting the divide above an unsafe test.
const Type* DivLNode::Value(PhaseGVN* phase) const {
  // Either input is TOP ==> the result is TOP
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // x/x == 1 since we always generate the dynamic divisor check for 0.
  if (in(1) == in(2)) {
    return TypeLong::ONE;
  }

  // Either input is BOTTOM ==> the result is the local BOTTOM
  const Type *bot = bottom_type();
  if( (t1 == bot) || (t2 == bot) ||
      (t1 == Type::BOTTOM) || (t2 == Type::BOTTOM) )
    return bot;

  // Divide the two numbers.  We approximate.
  // If divisor is a constant and not zero
  const TypeLong *i1 = t1->is_long();
  const TypeLong *i2 = t2->is_long();
  int widen = MAX2(i1->_widen, i2->_widen);

  if( i2->is_con() && i2->get_con() != 0 ) {
    jlong d = i2->get_con();    // Divisor
    jlong lo, hi;
    if( d >= 0 ) {
      lo = i1->_lo/d;
      hi = i1->_hi/d;
    } else {
      if( d == CONST64(-1) && i1->_lo == min_jlong ) {
        // 'min_jlong/-1' throws arithmetic exception during compilation
        lo = min_jlong;
        // do not support holes, 'hi' must go to either min_jlong or max_jlong:
        // [min_jlong, -10]/[-1,-1] ==> [min_jlong] UNION [10,max_jlong]
        hi = i1->_hi == min_jlong ? min_jlong : max_jlong;
      } else {
        lo = i1->_hi/d;
        hi = i1->_lo/d;
      }
    }
    return TypeLong::make(lo, hi, widen);
  }

  // If the dividend is a constant
  if( i1->is_con() ) {
    jlong d = i1->get_con();
    if( d < 0 ) {
      if( d == min_jlong ) {
        //  (-min_jlong) == min_jlong == (min_jlong / -1)
        return TypeLong::make(min_jlong, max_jlong/2 + 1, widen);
      } else {
        return TypeLong::make(d, -d, widen);
      }
    }
    return TypeLong::make(-d, d, widen);
  }

  // Otherwise we give up all hope
  return TypeLong::LONG;
}


//=============================================================================
//------------------------------Value------------------------------------------
// An DivFNode divides its inputs.  The third input is a Control input, used to
// prevent hoisting the divide above an unsafe test.
const Type* DivFNode::Value(PhaseGVN* phase) const {
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

  // x/x == 1, we ignore 0/0.
  // Note: if t1 and t2 are zero then result is NaN (JVMS page 213)
  // Does not work for variables because of NaN's
  if (in(1) == in(2) && t1->base() == Type::FloatCon &&
      !g_isnan(t1->getf()) && g_isfinite(t1->getf()) && t1->getf() != 0.0) { // could be negative ZERO or NaN
    return TypeF::ONE;
  }

  if( t2 == TypeF::ONE )
    return t1;

  // If divisor is a constant and not zero, divide them numbers
  if( t1->base() == Type::FloatCon &&
      t2->base() == Type::FloatCon &&
      t2->getf() != 0.0 ) // could be negative zero
    return TypeF::make( t1->getf()/t2->getf() );

  // If the dividend is a constant zero
  // Note: if t1 and t2 are zero then result is NaN (JVMS page 213)
  // Test TypeF::ZERO is not sufficient as it could be negative zero

  if( t1 == TypeF::ZERO && !g_isnan(t2->getf()) && t2->getf() != 0.0 )
    return TypeF::ZERO;

  // Otherwise we give up all hope
  return Type::FLOAT;
}

//------------------------------isA_Copy---------------------------------------
// Dividing by self is 1.
// If the divisor is 1, we are an identity on the dividend.
Node* DivFNode::Identity(PhaseGVN* phase) {
  return (phase->type( in(2) ) == TypeF::ONE) ? in(1) : this;
}


//------------------------------Idealize---------------------------------------
Node *DivFNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if (in(0) && remove_dead_region(phase, can_reshape))  return this;
  // Don't bother trying to transform a dead node
  if( in(0) && in(0)->is_top() )  return nullptr;

  const Type *t2 = phase->type( in(2) );
  if( t2 == TypeF::ONE )         // Identity?
    return nullptr;              // Skip it

  const TypeF *tf = t2->isa_float_constant();
  if( !tf ) return nullptr;
  if( tf->base() != Type::FloatCon ) return nullptr;

  // Check for out of range values
  if( tf->is_nan() || !tf->is_finite() ) return nullptr;

  // Get the value
  float f = tf->getf();
  int exp;

  // Only for special case of dividing by a power of 2
  if( frexp((double)f, &exp) != 0.5 ) return nullptr;

  // Limit the range of acceptable exponents
  if( exp < -126 || exp > 126 ) return nullptr;

  // Compute the reciprocal
  float reciprocal = ((float)1.0) / f;

  assert( frexp((double)reciprocal, &exp) == 0.5, "reciprocal should be power of 2" );

  // return multiplication by the reciprocal
  return (new MulFNode(in(1), phase->makecon(TypeF::make(reciprocal))));
}
//=============================================================================
//------------------------------Value------------------------------------------
// An DivHFNode divides its inputs.  The third input is a Control input, used to
// prevent hoisting the divide above an unsafe test.
const Type* DivHFNode::Value(PhaseGVN* phase) const {
  // Either input is TOP ==> the result is TOP
  const Type* t1 = phase->type(in(1));
  const Type* t2 = phase->type(in(2));
  if(t1 == Type::TOP) { return Type::TOP; }
  if(t2 == Type::TOP) { return Type::TOP; }

  // Either input is BOTTOM ==> the result is the local BOTTOM
  const Type* bot = bottom_type();
  if((t1 == bot) || (t2 == bot) ||
     (t1 == Type::BOTTOM) || (t2 == Type::BOTTOM)) {
    return bot;
  }

  if (t1->base() == Type::HalfFloatCon &&
      t2->base() == Type::HalfFloatCon)  {
    // IEEE 754 floating point comparison treats 0.0 and -0.0 as equals.

    // Division of a zero by a zero results in NaN.
    if (t1->getf() == 0.0f && t2->getf() == 0.0f) {
      return TypeH::make(NAN);
    }

    // As per C++ standard section 7.6.5 (expr.mul), behavior is undefined only if
    // the second operand is 0.0. In all other situations, we can expect a standard-compliant
    // C++ compiler to generate code following IEEE 754 semantics.
    if (t2->getf() == 0.0) {
      // If either operand is NaN, the result is NaN
      if (g_isnan(t1->getf())) {
        return TypeH::make(NAN);
      } else {
        // Division of a nonzero finite value by a zero results in a signed infinity. Also,
        // division of an infinity by a finite value results in a signed infinity.
        bool res_sign_neg = (jint_cast(t1->getf()) < 0) ^ (jint_cast(t2->getf()) < 0);
        const TypeF* res = res_sign_neg ? TypeF::NEG_INF : TypeF::POS_INF;
        return TypeH::make(res->getf());
      }
    }

    return TypeH::make(t1->getf() / t2->getf());
  }

  // Otherwise we give up all hope
  return Type::HALF_FLOAT;
}

//-----------------------------------------------------------------------------
// Dividing by self is 1.
// IF the divisor is 1, we are an identity on the dividend.
Node* DivHFNode::Identity(PhaseGVN* phase) {
  return (phase->type( in(2) ) == TypeH::ONE) ? in(1) : this;
}


//------------------------------Idealize---------------------------------------
Node* DivHFNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  if (in(0) != nullptr && remove_dead_region(phase, can_reshape))  return this;
  // Don't bother trying to transform a dead node
  if (in(0) != nullptr && in(0)->is_top())  { return nullptr; }

  const Type* t2 = phase->type(in(2));
  if (t2 == TypeH::ONE) {      // Identity?
    return nullptr;            // Skip it
  }
  const TypeH* tf = t2->isa_half_float_constant();
  if(tf == nullptr) { return nullptr; }
  if(tf->base() != Type::HalfFloatCon) { return nullptr; }

  // Check for out of range values
  if(tf->is_nan() || !tf->is_finite()) { return nullptr; }

  // Get the value
  float f = tf->getf();
  int exp;

  // Consider the following geometric progression series of POT(power of two) numbers.
  // 0.5 x 2^0 = 0.5, 0.5 x 2^1 = 1.0, 0.5 x 2^2 = 2.0, 0.5 x 2^3 = 4.0 ... 0.5 x 2^n,
  // In all the above cases, normalized mantissa returned by frexp routine will
  // be exactly equal to 0.5 while exponent will be 0,1,2,3...n
  // Perform division to multiplication transform only if divisor is a POT value.
  if(frexp((double)f, &exp) != 0.5) { return nullptr; }

  // Limit the range of acceptable exponents
  if(exp < -14 || exp > 15) { return nullptr; }

  // Since divisor is a POT number, hence its reciprocal will never
  // overflow 11 bits precision range of Float16
  // value if exponent returned by frexp routine strictly lie
  // within the exponent range of normal min(0x1.0P-14) and
  // normal max(0x1.ffcP+15) values.
  // Thus we can safely compute the reciprocal of divisor without
  // any concerns about the precision loss and transform the division
  // into a multiplication operation.
  float reciprocal = ((float)1.0) / f;

  assert(frexp((double)reciprocal, &exp) == 0.5, "reciprocal should be power of 2");

  // return multiplication by the reciprocal
  return (new MulHFNode(in(1), phase->makecon(TypeH::make(reciprocal))));
}

//=============================================================================
//------------------------------Value------------------------------------------
// An DivDNode divides its inputs.  The third input is a Control input, used to
// prevent hoisting the divide above an unsafe test.
const Type* DivDNode::Value(PhaseGVN* phase) const {
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

  // x/x == 1, we ignore 0/0.
  // Note: if t1 and t2 are zero then result is NaN (JVMS page 213)
  // Does not work for variables because of NaN's
  if (in(1) == in(2) && t1->base() == Type::DoubleCon &&
      !g_isnan(t1->getd()) && g_isfinite(t1->getd()) && t1->getd() != 0.0) { // could be negative ZERO or NaN
    return TypeD::ONE;
  }

  if( t2 == TypeD::ONE )
    return t1;

  // IA32 would only execute this for non-strict FP, which is never the
  // case now.
#if ! defined(IA32)
  // If divisor is a constant and not zero, divide them numbers
  if( t1->base() == Type::DoubleCon &&
      t2->base() == Type::DoubleCon &&
      t2->getd() != 0.0 ) // could be negative zero
    return TypeD::make( t1->getd()/t2->getd() );
#endif

  // If the dividend is a constant zero
  // Note: if t1 and t2 are zero then result is NaN (JVMS page 213)
  // Test TypeF::ZERO is not sufficient as it could be negative zero
  if( t1 == TypeD::ZERO && !g_isnan(t2->getd()) && t2->getd() != 0.0 )
    return TypeD::ZERO;

  // Otherwise we give up all hope
  return Type::DOUBLE;
}


//------------------------------isA_Copy---------------------------------------
// Dividing by self is 1.
// If the divisor is 1, we are an identity on the dividend.
Node* DivDNode::Identity(PhaseGVN* phase) {
  return (phase->type( in(2) ) == TypeD::ONE) ? in(1) : this;
}

//------------------------------Idealize---------------------------------------
Node *DivDNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if (in(0) && remove_dead_region(phase, can_reshape))  return this;
  // Don't bother trying to transform a dead node
  if( in(0) && in(0)->is_top() )  return nullptr;

  const Type *t2 = phase->type( in(2) );
  if( t2 == TypeD::ONE )         // Identity?
    return nullptr;              // Skip it

  const TypeD *td = t2->isa_double_constant();
  if( !td ) return nullptr;
  if( td->base() != Type::DoubleCon ) return nullptr;

  // Check for out of range values
  if( td->is_nan() || !td->is_finite() ) return nullptr;

  // Get the value
  double d = td->getd();
  int exp;

  // Only for special case of dividing by a power of 2
  if( frexp(d, &exp) != 0.5 ) return nullptr;

  // Limit the range of acceptable exponents
  if( exp < -1021 || exp > 1022 ) return nullptr;

  // Compute the reciprocal
  double reciprocal = 1.0 / d;

  assert( frexp(reciprocal, &exp) == 0.5, "reciprocal should be power of 2" );

  // return multiplication by the reciprocal
  return (new MulDNode(in(1), phase->makecon(TypeD::make(reciprocal))));
}

//=============================================================================
//------------------------------Identity---------------------------------------
// If the divisor is 1, we are an identity on the dividend.
Node* UDivINode::Identity(PhaseGVN* phase) {
  return (phase->type( in(2) )->higher_equal(TypeInt::ONE)) ? in(1) : this;
}
//------------------------------Value------------------------------------------
// A UDivINode divides its inputs.  The third input is a Control input, used to
// prevent hoisting the divide above an unsafe test.
const Type* UDivINode::Value(PhaseGVN* phase) const {
  // Either input is TOP ==> the result is TOP
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // x/x == 1 since we always generate the dynamic divisor check for 0.
  if (in(1) == in(2)) {
    return TypeInt::ONE;
  }

  // Either input is BOTTOM ==> the result is the local BOTTOM
  const Type *bot = bottom_type();
  if( (t1 == bot) || (t2 == bot) ||
      (t1 == Type::BOTTOM) || (t2 == Type::BOTTOM) )
    return bot;

  // Otherwise we give up all hope
  return TypeInt::INT;
}

//------------------------------Idealize---------------------------------------
Node *UDivINode::Ideal(PhaseGVN *phase, bool can_reshape) {
  return unsigned_div_ideal<TypeInt, juint>(phase, can_reshape, this);
}

//=============================================================================
//------------------------------Identity---------------------------------------
// If the divisor is 1, we are an identity on the dividend.
Node* UDivLNode::Identity(PhaseGVN* phase) {
  return (phase->type( in(2) )->higher_equal(TypeLong::ONE)) ? in(1) : this;
}
//------------------------------Value------------------------------------------
// A UDivLNode divides its inputs.  The third input is a Control input, used to
// prevent hoisting the divide above an unsafe test.
const Type* UDivLNode::Value(PhaseGVN* phase) const {
  // Either input is TOP ==> the result is TOP
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // x/x == 1 since we always generate the dynamic divisor check for 0.
  if (in(1) == in(2)) {
    return TypeLong::ONE;
  }

  // Either input is BOTTOM ==> the result is the local BOTTOM
  const Type *bot = bottom_type();
  if( (t1 == bot) || (t2 == bot) ||
      (t1 == Type::BOTTOM) || (t2 == Type::BOTTOM) )
    return bot;

  // Otherwise we give up all hope
  return TypeLong::LONG;
}

//------------------------------Idealize---------------------------------------
Node *UDivLNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  return unsigned_div_ideal<TypeLong, julong>(phase, can_reshape, this);
}

//=============================================================================
//------------------------------Idealize---------------------------------------
Node *ModINode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Check for dead control input
  if( in(0) && remove_dead_region(phase, can_reshape) )  return this;
  // Don't bother trying to transform a dead node
  if( in(0) && in(0)->is_top() )  return nullptr;

  // Get the modulus
  const Type *t = phase->type( in(2) );
  if( t == Type::TOP ) return nullptr;
  const TypeInt *ti = t->is_int();

  // Check for useless control input
  // Check for excluding mod-zero case
  if (in(0) && (ti->_hi < 0 || ti->_lo > 0)) {
    set_req(0, nullptr);        // Yank control input
    return this;
  }

  // See if we are MOD'ing by 2^k or 2^k-1.
  if( !ti->is_con() ) return nullptr;
  jint con = ti->get_con();

  Node *hook = new Node(1);

  // First, special check for modulo 2^k-1
  if( con >= 0 && con < max_jint && is_power_of_2(con+1) ) {
    uint k = exact_log2(con+1);  // Extract k

    // Basic algorithm by David Detlefs.  See fastmod_int.java for gory details.
    static int unroll_factor[] = { 999, 999, 29, 14, 9, 7, 5, 4, 4, 3, 3, 2, 2, 2, 2, 2, 1 /*past here we assume 1 forever*/};
    int trip_count = 1;
    if( k < ARRAY_SIZE(unroll_factor))  trip_count = unroll_factor[k];

    // If the unroll factor is not too large, and if conditional moves are
    // ok, then use this case
    if( trip_count <= 5 && ConditionalMoveLimit != 0 ) {
      Node *x = in(1);            // Value being mod'd
      Node *divisor = in(2);      // Also is mask

      hook->init_req(0, x);       // Add a use to x to prevent him from dying
      // Generate code to reduce X rapidly to nearly 2^k-1.
      for( int i = 0; i < trip_count; i++ ) {
        Node *xl = phase->transform( new AndINode(x,divisor) );
        Node *xh = phase->transform( new RShiftINode(x,phase->intcon(k)) ); // Must be signed
        x = phase->transform( new AddINode(xh,xl) );
        hook->set_req(0, x);
      }

      // Generate sign-fixup code.  Was original value positive?
      // int hack_res = (i >= 0) ? divisor : 1;
      Node *cmp1 = phase->transform( new CmpINode( in(1), phase->intcon(0) ) );
      Node *bol1 = phase->transform( new BoolNode( cmp1, BoolTest::ge ) );
      Node *cmov1= phase->transform( new CMoveINode(bol1, phase->intcon(1), divisor, TypeInt::POS) );
      // if( x >= hack_res ) x -= divisor;
      Node *sub  = phase->transform( new SubINode( x, divisor ) );
      Node *cmp2 = phase->transform( new CmpINode( x, cmov1 ) );
      Node *bol2 = phase->transform( new BoolNode( cmp2, BoolTest::ge ) );
      // Convention is to not transform the return value of an Ideal
      // since Ideal is expected to return a modified 'this' or a new node.
      Node *cmov2= new CMoveINode(bol2, x, sub, TypeInt::INT);
      // cmov2 is now the mod

      // Now remove the bogus extra edges used to keep things alive
      hook->destruct(phase);
      return cmov2;
    }
  }

  // Fell thru, the unroll case is not appropriate. Transform the modulo
  // into a long multiply/int multiply/subtract case

  // Cannot handle mod 0, and min_jint isn't handled by the transform
  if( con == 0 || con == min_jint ) return nullptr;

  // Get the absolute value of the constant; at this point, we can use this
  jint pos_con = (con >= 0) ? con : -con;

  // integer Mod 1 is always 0
  if( pos_con == 1 ) return new ConINode(TypeInt::ZERO);

  int log2_con = -1;

  // If this is a power of two, they maybe we can mask it
  if (is_power_of_2(pos_con)) {
    log2_con = log2i_exact(pos_con);

    const Type *dt = phase->type(in(1));
    const TypeInt *dti = dt->isa_int();

    // See if this can be masked, if the dividend is non-negative
    if( dti && dti->_lo >= 0 )
      return ( new AndINode( in(1), phase->intcon( pos_con-1 ) ) );
  }

  // Save in(1) so that it cannot be changed or deleted
  hook->init_req(0, in(1));

  // Divide using the transform from DivI to MulL
  Node *result = transform_int_divide( phase, in(1), pos_con );
  if (result != nullptr) {
    Node *divide = phase->transform(result);

    // Re-multiply, using a shift if this is a power of two
    Node *mult = nullptr;

    if( log2_con >= 0 )
      mult = phase->transform( new LShiftINode( divide, phase->intcon( log2_con ) ) );
    else
      mult = phase->transform( new MulINode( divide, phase->intcon( pos_con ) ) );

    // Finally, subtract the multiplied divided value from the original
    result = new SubINode( in(1), mult );
  }

  // Now remove the bogus extra edges used to keep things alive
  hook->destruct(phase);

  // return the value
  return result;
}

//------------------------------Value------------------------------------------
const Type* ModINode::Value(PhaseGVN* phase) const {
  // Either input is TOP ==> the result is TOP
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // We always generate the dynamic check for 0.
  // 0 MOD X is 0
  if( t1 == TypeInt::ZERO ) return TypeInt::ZERO;
  // X MOD X is 0
  if (in(1) == in(2)) {
    return TypeInt::ZERO;
  }

  // Either input is BOTTOM ==> the result is the local BOTTOM
  const Type *bot = bottom_type();
  if( (t1 == bot) || (t2 == bot) ||
      (t1 == Type::BOTTOM) || (t2 == Type::BOTTOM) )
    return bot;

  const TypeInt *i1 = t1->is_int();
  const TypeInt *i2 = t2->is_int();
  if( !i1->is_con() || !i2->is_con() ) {
    if( i1->_lo >= 0 && i2->_lo >= 0 )
      return TypeInt::POS;
    // If both numbers are not constants, we know little.
    return TypeInt::INT;
  }
  // Mod by zero?  Throw exception at runtime!
  if( !i2->get_con() ) return TypeInt::POS;

  // We must be modulo'ing 2 float constants.
  // Check for min_jint % '-1', result is defined to be '0'.
  if( i1->get_con() == min_jint && i2->get_con() == -1 )
    return TypeInt::ZERO;

  return TypeInt::make( i1->get_con() % i2->get_con() );
}

//=============================================================================
//------------------------------Idealize---------------------------------------

template <typename TypeClass, typename Unsigned>
static Node* unsigned_mod_ideal(PhaseGVN* phase, bool can_reshape, Node* mod) {
  // Check for dead control input
  if (mod->in(0) != nullptr && mod->remove_dead_region(phase, can_reshape)) {
    return mod;
  }
  // Don't bother trying to transform a dead node
  if (mod->in(0) != nullptr && mod->in(0)->is_top()) {
    return nullptr;
  }

  // Get the modulus
  const Type* t = phase->type(mod->in(2));
  if (t == Type::TOP) {
    return nullptr;
  }
  const TypeClass* type_divisor = t->cast<TypeClass>();

  // Check for useless control input
  // Check for excluding mod-zero case
  if (mod->in(0) != nullptr && (type_divisor->_hi < 0 || type_divisor->_lo > 0)) {
    mod->set_req(0, nullptr); // Yank control input
    return mod;
  }

  if (!type_divisor->is_con()) {
    return nullptr;
  }
  Unsigned divisor = static_cast<Unsigned>(type_divisor->get_con());

  if (divisor == 0) {
    return nullptr;
  }

  if (is_power_of_2(divisor)) {
    return make_and<TypeClass>(mod->in(1), phase->makecon(TypeClass::make(divisor - 1)));
  }

  return nullptr;
}

template <typename TypeClass, typename Unsigned, typename Signed>
static const Type* unsigned_mod_value(PhaseGVN* phase, const Node* mod) {
  const Type* t1 = phase->type(mod->in(1));
  const Type* t2 = phase->type(mod->in(2));
  if (t1 == Type::TOP) {
    return Type::TOP;
  }
  if (t2 == Type::TOP) {
    return Type::TOP;
  }

  // 0 MOD X is 0
  if (t1 == TypeClass::ZERO) {
    return TypeClass::ZERO;
  }
  // X MOD X is 0
  if (mod->in(1) == mod->in(2)) {
    return TypeClass::ZERO;
  }

  // Either input is BOTTOM ==> the result is the local BOTTOM
  const Type* bot = mod->bottom_type();
  if ((t1 == bot) || (t2 == bot) ||
      (t1 == Type::BOTTOM) || (t2 == Type::BOTTOM)) {
    return bot;
  }

  const TypeClass* type_divisor = t2->cast<TypeClass>();
  if (type_divisor->is_con() && type_divisor->get_con() == 1) {
    return TypeClass::ZERO;
  }

  // Mod by zero?  Throw an exception at runtime!
  if (type_divisor->is_con() && type_divisor->get_con() == 0) {
    return TypeClass::POS;
  }

  const TypeClass* type_dividend = t1->cast<TypeClass>();
  if (type_dividend->is_con() && type_divisor->is_con()) {
    Unsigned dividend = static_cast<Unsigned>(type_dividend->get_con());
    Unsigned divisor = static_cast<Unsigned>(type_divisor->get_con());
    return TypeClass::make(static_cast<Signed>(dividend % divisor));
  }

  return bot;
}

Node* UModINode::Ideal(PhaseGVN* phase, bool can_reshape) {
  return unsigned_mod_ideal<TypeInt, juint>(phase, can_reshape, this);
}

const Type* UModINode::Value(PhaseGVN* phase) const {
  return unsigned_mod_value<TypeInt, juint, jint>(phase, this);
}

//=============================================================================
//------------------------------Idealize---------------------------------------
Node *ModLNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Check for dead control input
  if( in(0) && remove_dead_region(phase, can_reshape) )  return this;
  // Don't bother trying to transform a dead node
  if( in(0) && in(0)->is_top() )  return nullptr;

  // Get the modulus
  const Type *t = phase->type( in(2) );
  if( t == Type::TOP ) return nullptr;
  const TypeLong *tl = t->is_long();

  // Check for useless control input
  // Check for excluding mod-zero case
  if (in(0) && (tl->_hi < 0 || tl->_lo > 0)) {
    set_req(0, nullptr);        // Yank control input
    return this;
  }

  // See if we are MOD'ing by 2^k or 2^k-1.
  if( !tl->is_con() ) return nullptr;
  jlong con = tl->get_con();

  Node *hook = new Node(1);

  // Expand mod
  if(con >= 0 && con < max_jlong && is_power_of_2(con + 1)) {
    uint k = log2i_exact(con + 1);  // Extract k

    // Basic algorithm by David Detlefs.  See fastmod_long.java for gory details.
    // Used to help a popular random number generator which does a long-mod
    // of 2^31-1 and shows up in SpecJBB and SciMark.
    static int unroll_factor[] = { 999, 999, 61, 30, 20, 15, 12, 10, 8, 7, 6, 6, 5, 5, 4, 4, 4, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1 /*past here we assume 1 forever*/};
    int trip_count = 1;
    if( k < ARRAY_SIZE(unroll_factor)) trip_count = unroll_factor[k];

    // If the unroll factor is not too large, and if conditional moves are
    // ok, then use this case
    if( trip_count <= 5 && ConditionalMoveLimit != 0 ) {
      Node *x = in(1);            // Value being mod'd
      Node *divisor = in(2);      // Also is mask

      hook->init_req(0, x);       // Add a use to x to prevent him from dying
      // Generate code to reduce X rapidly to nearly 2^k-1.
      for( int i = 0; i < trip_count; i++ ) {
        Node *xl = phase->transform( new AndLNode(x,divisor) );
        Node *xh = phase->transform( new RShiftLNode(x,phase->intcon(k)) ); // Must be signed
        x = phase->transform( new AddLNode(xh,xl) );
        hook->set_req(0, x);    // Add a use to x to prevent him from dying
      }

      // Generate sign-fixup code.  Was original value positive?
      // long hack_res = (i >= 0) ? divisor : CONST64(1);
      Node *cmp1 = phase->transform( new CmpLNode( in(1), phase->longcon(0) ) );
      Node *bol1 = phase->transform( new BoolNode( cmp1, BoolTest::ge ) );
      Node *cmov1= phase->transform( new CMoveLNode(bol1, phase->longcon(1), divisor, TypeLong::LONG) );
      // if( x >= hack_res ) x -= divisor;
      Node *sub  = phase->transform( new SubLNode( x, divisor ) );
      Node *cmp2 = phase->transform( new CmpLNode( x, cmov1 ) );
      Node *bol2 = phase->transform( new BoolNode( cmp2, BoolTest::ge ) );
      // Convention is to not transform the return value of an Ideal
      // since Ideal is expected to return a modified 'this' or a new node.
      Node *cmov2= new CMoveLNode(bol2, x, sub, TypeLong::LONG);
      // cmov2 is now the mod

      // Now remove the bogus extra edges used to keep things alive
      hook->destruct(phase);
      return cmov2;
    }
  }

  // Fell thru, the unroll case is not appropriate. Transform the modulo
  // into a long multiply/int multiply/subtract case

  // Cannot handle mod 0, and min_jlong isn't handled by the transform
  if( con == 0 || con == min_jlong ) return nullptr;

  // Get the absolute value of the constant; at this point, we can use this
  jlong pos_con = (con >= 0) ? con : -con;

  // integer Mod 1 is always 0
  if( pos_con == 1 ) return new ConLNode(TypeLong::ZERO);

  int log2_con = -1;

  // If this is a power of two, then maybe we can mask it
  if (is_power_of_2(pos_con)) {
    log2_con = log2i_exact(pos_con);

    const Type *dt = phase->type(in(1));
    const TypeLong *dtl = dt->isa_long();

    // See if this can be masked, if the dividend is non-negative
    if( dtl && dtl->_lo >= 0 )
      return ( new AndLNode( in(1), phase->longcon( pos_con-1 ) ) );
  }

  // Save in(1) so that it cannot be changed or deleted
  hook->init_req(0, in(1));

  // Divide using the transform from DivL to MulL
  Node *result = transform_long_divide( phase, in(1), pos_con );
  if (result != nullptr) {
    Node *divide = phase->transform(result);

    // Re-multiply, using a shift if this is a power of two
    Node *mult = nullptr;

    if( log2_con >= 0 )
      mult = phase->transform( new LShiftLNode( divide, phase->intcon( log2_con ) ) );
    else
      mult = phase->transform( new MulLNode( divide, phase->longcon( pos_con ) ) );

    // Finally, subtract the multiplied divided value from the original
    result = new SubLNode( in(1), mult );
  }

  // Now remove the bogus extra edges used to keep things alive
  hook->destruct(phase);

  // return the value
  return result;
}

//------------------------------Value------------------------------------------
const Type* ModLNode::Value(PhaseGVN* phase) const {
  // Either input is TOP ==> the result is TOP
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // We always generate the dynamic check for 0.
  // 0 MOD X is 0
  if( t1 == TypeLong::ZERO ) return TypeLong::ZERO;
  // X MOD X is 0
  if (in(1) == in(2)) {
    return TypeLong::ZERO;
  }

  // Either input is BOTTOM ==> the result is the local BOTTOM
  const Type *bot = bottom_type();
  if( (t1 == bot) || (t2 == bot) ||
      (t1 == Type::BOTTOM) || (t2 == Type::BOTTOM) )
    return bot;

  const TypeLong *i1 = t1->is_long();
  const TypeLong *i2 = t2->is_long();
  if( !i1->is_con() || !i2->is_con() ) {
    if( i1->_lo >= CONST64(0) && i2->_lo >= CONST64(0) )
      return TypeLong::POS;
    // If both numbers are not constants, we know little.
    return TypeLong::LONG;
  }
  // Mod by zero?  Throw exception at runtime!
  if( !i2->get_con() ) return TypeLong::POS;

  // We must be modulo'ing 2 float constants.
  // Check for min_jint % '-1', result is defined to be '0'.
  if( i1->get_con() == min_jlong && i2->get_con() == -1 )
    return TypeLong::ZERO;

  return TypeLong::make( i1->get_con() % i2->get_con() );
}

Node *UModLNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  return unsigned_mod_ideal<TypeLong, julong>(phase, can_reshape, this);
}

const Type* UModLNode::Value(PhaseGVN* phase) const {
  return unsigned_mod_value<TypeLong, julong, jlong>(phase, this);
}

const Type* ModFNode::get_result_if_constant(const Type* dividend, const Type* divisor) const {
  // If either number is not a constant, we know nothing.
  if ((dividend->base() != Type::FloatCon) || (divisor->base() != Type::FloatCon)) {
    return nullptr; // note: x%x can be either NaN or 0
  }

  float dividend_f = dividend->getf();
  float divisor_f = divisor->getf();
  jint dividend_i = jint_cast(dividend_f); // note:  *(int*)&f1, not just (int)f1
  jint divisor_i = jint_cast(divisor_f);

  // If either is a NaN, return an input NaN
  if (g_isnan(dividend_f)) {
    return dividend;
  }
  if (g_isnan(divisor_f)) {
    return divisor;
  }

  // If an operand is infinity or the divisor is +/- zero, punt.
  if (!g_isfinite(dividend_f) || !g_isfinite(divisor_f) || divisor_i == 0 || divisor_i == min_jint) {
    return nullptr;
  }

  // We must be modulo'ing 2 float constants.
  // Make sure that the sign of the fmod is equal to the sign of the dividend
  jint xr = jint_cast(fmod(dividend_f, divisor_f));
  if ((dividend_i ^ xr) < 0) {
    xr ^= min_jint;
  }

  return TypeF::make(jfloat_cast(xr));
}

const Type* ModDNode::get_result_if_constant(const Type* dividend, const Type* divisor) const {
  // If either number is not a constant, we know nothing.
  if ((dividend->base() != Type::DoubleCon) || (divisor->base() != Type::DoubleCon)) {
    return nullptr; // note: x%x can be either NaN or 0
  }

  double dividend_d = dividend->getd();
  double divisor_d = divisor->getd();
  jlong dividend_l = jlong_cast(dividend_d); // note:  *(long*)&f1, not just (long)f1
  jlong divisor_l = jlong_cast(divisor_d);

  // If either is a NaN, return an input NaN
  if (g_isnan(dividend_d)) {
    return dividend;
  }
  if (g_isnan(divisor_d)) {
    return divisor;
  }

  // If an operand is infinity or the divisor is +/- zero, punt.
  if (!g_isfinite(dividend_d) || !g_isfinite(divisor_d) || divisor_l == 0 || divisor_l == min_jlong) {
    return nullptr;
  }

  // We must be modulo'ing 2 double constants.
  // Make sure that the sign of the fmod is equal to the sign of the dividend
  jlong xr = jlong_cast(fmod(dividend_d, divisor_d));
  if ((dividend_l ^ xr) < 0) {
    xr ^= min_jlong;
  }

  return TypeD::make(jdouble_cast(xr));
}

Node* ModFloatingNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  if (can_reshape) {
    PhaseIterGVN* igvn = phase->is_IterGVN();

    // Either input is TOP ==> the result is TOP
    const Type* dividend_type = phase->type(dividend());
    const Type* divisor_type = phase->type(divisor());
    if (dividend_type == Type::TOP || divisor_type == Type::TOP) {
      return phase->C->top();
    }
    const Type* constant_result = get_result_if_constant(dividend_type, divisor_type);
    if (constant_result != nullptr) {
      return make_tuple_of_input_state_and_constant_result(igvn, constant_result);
    }
  }

  return CallLeafPureNode::Ideal(phase, can_reshape);
}

/* Give a tuple node for ::Ideal to return, made of the input state (control to return addr)
 * and the given constant result. Idealization of projections will make sure to transparently
 * propagate the input state and replace the result by the said constant.
 */
TupleNode* ModFloatingNode::make_tuple_of_input_state_and_constant_result(PhaseIterGVN* phase, const Type* con) const {
  Node* con_node = phase->makecon(con);
  TupleNode* tuple = TupleNode::make(
      tf()->range(),
      in(TypeFunc::Control),
      in(TypeFunc::I_O),
      in(TypeFunc::Memory),
      in(TypeFunc::FramePtr),
      in(TypeFunc::ReturnAdr),
      con_node);

  return tuple;
}

//=============================================================================

DivModNode::DivModNode( Node *c, Node *dividend, Node *divisor ) : MultiNode(3) {
  init_req(0, c);
  init_req(1, dividend);
  init_req(2, divisor);
}

DivModNode* DivModNode::make(Node* div_or_mod, BasicType bt, bool is_unsigned) {
  assert(bt == T_INT || bt == T_LONG, "only int or long input pattern accepted");

  if (bt == T_INT) {
    if (is_unsigned) {
      return UDivModINode::make(div_or_mod);
    } else {
      return DivModINode::make(div_or_mod);
    }
  } else {
    if (is_unsigned) {
      return UDivModLNode::make(div_or_mod);
    } else {
      return DivModLNode::make(div_or_mod);
    }
  }
}

//------------------------------make------------------------------------------
DivModINode* DivModINode::make(Node* div_or_mod) {
  Node* n = div_or_mod;
  assert(n->Opcode() == Op_DivI || n->Opcode() == Op_ModI,
         "only div or mod input pattern accepted");

  DivModINode* divmod = new DivModINode(n->in(0), n->in(1), n->in(2));
  Node*        dproj  = new ProjNode(divmod, DivModNode::div_proj_num);
  Node*        mproj  = new ProjNode(divmod, DivModNode::mod_proj_num);
  return divmod;
}

//------------------------------make------------------------------------------
DivModLNode* DivModLNode::make(Node* div_or_mod) {
  Node* n = div_or_mod;
  assert(n->Opcode() == Op_DivL || n->Opcode() == Op_ModL,
         "only div or mod input pattern accepted");

  DivModLNode* divmod = new DivModLNode(n->in(0), n->in(1), n->in(2));
  Node*        dproj  = new ProjNode(divmod, DivModNode::div_proj_num);
  Node*        mproj  = new ProjNode(divmod, DivModNode::mod_proj_num);
  return divmod;
}

//------------------------------match------------------------------------------
// return result(s) along with their RegMask info
Node *DivModINode::match( const ProjNode *proj, const Matcher *match ) {
  uint ideal_reg = proj->ideal_reg();
  RegMask rm;
  if (proj->_con == div_proj_num) {
    rm = match->divI_proj_mask();
  } else {
    assert(proj->_con == mod_proj_num, "must be div or mod projection");
    rm = match->modI_proj_mask();
  }
  return new MachProjNode(this, proj->_con, rm, ideal_reg);
}


//------------------------------match------------------------------------------
// return result(s) along with their RegMask info
Node *DivModLNode::match( const ProjNode *proj, const Matcher *match ) {
  uint ideal_reg = proj->ideal_reg();
  RegMask rm;
  if (proj->_con == div_proj_num) {
    rm = match->divL_proj_mask();
  } else {
    assert(proj->_con == mod_proj_num, "must be div or mod projection");
    rm = match->modL_proj_mask();
  }
  return new MachProjNode(this, proj->_con, rm, ideal_reg);
}

//------------------------------make------------------------------------------
UDivModINode* UDivModINode::make(Node* div_or_mod) {
  Node* n = div_or_mod;
  assert(n->Opcode() == Op_UDivI || n->Opcode() == Op_UModI,
         "only div or mod input pattern accepted");

  UDivModINode* divmod = new UDivModINode(n->in(0), n->in(1), n->in(2));
  Node*        dproj  = new ProjNode(divmod, DivModNode::div_proj_num);
  Node*        mproj  = new ProjNode(divmod, DivModNode::mod_proj_num);
  return divmod;
}

//------------------------------make------------------------------------------
UDivModLNode* UDivModLNode::make(Node* div_or_mod) {
  Node* n = div_or_mod;
  assert(n->Opcode() == Op_UDivL || n->Opcode() == Op_UModL,
         "only div or mod input pattern accepted");

  UDivModLNode* divmod = new UDivModLNode(n->in(0), n->in(1), n->in(2));
  Node*        dproj  = new ProjNode(divmod, DivModNode::div_proj_num);
  Node*        mproj  = new ProjNode(divmod, DivModNode::mod_proj_num);
  return divmod;
}

//------------------------------match------------------------------------------
// return result(s) along with their RegMask info
Node* UDivModINode::match( const ProjNode *proj, const Matcher *match ) {
  uint ideal_reg = proj->ideal_reg();
  RegMask rm;
  if (proj->_con == div_proj_num) {
    rm = match->divI_proj_mask();
  } else {
    assert(proj->_con == mod_proj_num, "must be div or mod projection");
    rm = match->modI_proj_mask();
  }
  return new MachProjNode(this, proj->_con, rm, ideal_reg);
}


//------------------------------match------------------------------------------
// return result(s) along with their RegMask info
Node* UDivModLNode::match( const ProjNode *proj, const Matcher *match ) {
  uint ideal_reg = proj->ideal_reg();
  RegMask rm;
  if (proj->_con == div_proj_num) {
    rm = match->divL_proj_mask();
  } else {
    assert(proj->_con == mod_proj_num, "must be div or mod projection");
    rm = match->modL_proj_mask();
  }
  return new MachProjNode(this, proj->_con, rm, ideal_reg);
}
