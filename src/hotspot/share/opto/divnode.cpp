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
#include <limits>
#include <type_traits>
#include "memory/allocation.inline.hpp"
#include "opto/addnode.hpp"
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/divnode.hpp"
#include "opto/machnode.hpp"
#include "opto/movenode.hpp"
#include "opto/matcher.hpp"
#include "opto/mulnode.hpp"
#include "opto/phaseX.hpp"
#include "opto/subnode.hpp"
#include "utilities/powerOfTwo.hpp"


// Portions of code courtesy of Clifford Click

template <class T>
static T max_unsigned_from_signed_bounds(T slo, T shi) {
  static_assert(std::is_unsigned<T>::value, "must be");
  return slo <= shi ? shi : std::numeric_limits<T>::max();
}

static bool jint_mul_no_ovf(jint lo, jint hi, juint c) {
  jlong prod_lo = jlong(lo) * jlong(c);
  jlong prod_hi = jlong(hi) * jlong(c);
  return jlong(jint(prod_lo)) == prod_lo && jlong(jint(prod_hi)) == prod_hi;
}

static bool juint_mul_no_ovf(juint hi, juint c) {
  julong prod_hi = julong(hi) * julong(c);
  return julong(juint(prod_hi)) == prod_hi;
}

static bool jlong_mul_no_ovf(jlong lo, jlong hi, julong c) {
  if (jlong(c) < 0) {
    return lo == 0 && hi == 0;
  }

  jlong prod_lo_hi = multiply_high_signed(lo, c);
  jlong prod_lo_lo = java_multiply(lo, c);
  jlong prod_hi_hi = multiply_high_signed(hi, c);
  jlong prod_hi_lo = java_multiply(hi, c);
  return ((prod_lo_hi == 0 && prod_lo_lo >= 0) || (prod_lo_hi == -1 && prod_lo_lo < 0)) &&
      ((prod_hi_hi == 0 && prod_hi_lo >= 0) || (prod_hi_hi == -1 && prod_hi_lo < 0));
}

static bool julong_mul_no_ovf(julong hi, julong c) {
  julong prod_hi_hi = multiply_high_unsigned(hi, c);
  return prod_hi_hi == 0;
}

static bool uint128_t_mul_no_ovf(julong hi, julong c_wrapped) {
  julong mul_hi_c_wrapped = multiply_high_unsigned(hi, c_wrapped);
  julong mul_hi_wrapped = mul_hi_c_wrapped + hi;
  return mul_hi_wrapped >= mul_hi_c_wrapped;
}

/*
magic_divide_constants in utilities/javaArithmetic.hpp calculates the constant c, s
such that division(x / d) = floor(x * c / m) + (x < 0 ? 1 : 0) for every integer x in
the input range. The functions in this file try to derive from the formula in real
arithmetic to arrive at a formula in int/long arithmetic. More details can be found in
each individual function.
*/

// Convert a division by constant divisor into an alternate Ideal graph.
static Node* transform_int_divide(PhaseGVN* phase, Node* dividend, jint divisor) {
  // Check for invalid divisors
  assert(divisor != 0 && divisor != 1,
         "bad divisor for transforming to long multiply");
  if (divisor == -1) {
    return new SubINode(phase->intcon(0), dividend);
  }

  bool d_pos = divisor >= 0;
  juint d = ABS<jint>(divisor);
  constexpr int N = 32;
  const TypeInt* dti = phase->type(dividend)->is_int();
  juint min_neg = dti->_lo < 0 ? -juint(dti->_lo) : 0;
  juint max_pos = dti->_hi > 0 ? juint(dti->_hi) : 0;
  if (min_neg < d && max_pos < d) {
    return phase->intcon(0);
  }

  if (is_power_of_2(d)) {
    // division by +/- a power of 2
    juint l = log2i_exact(d);

    // See if we can simply do a shift without rounding
    bool needs_rounding = true;
    if (dti->_lo >= 0) {
      // we don't need to round a positive dividend
      needs_rounding = false;
    } else if (dividend->Opcode() == Op_AndI) {
      // An AND mask of sufficient size clears the low bits and
      // I can avoid rounding.
      const TypeInt* andconi_t = phase->type(dividend->in(2))->isa_int();
      if (andconi_t && andconi_t->is_con()) {
        juint andconi = andconi_t->get_con();
        if (count_trailing_zeros(andconi) >= l) {
          if (-andconi == d) {
            // Remove AND if it clears bits which will be shifted
            dividend = dividend->in(1);
          }
          needs_rounding = false;
        }
      }
    }

    if (needs_rounding) {
      // Divide-by-power-of-2 can be made into a shift, but you have to do
      // more math for the rounding.  You need to add 0 for positive
      // numbers, and "i-1" for negative numbers.  Example: i=4, so the
      // shift is by 2.  You need to add 3 to negative dividends and 0 to
      // positive ones.  So (-7+3)>>2 becomes -1, (-4+3)>>2 becomes -1,
      // (-2+3)>>2 becomes 0, etc.

      // Compute 0 or -1, based on sign bit
      Node* sign = phase->transform(new RShiftINode(dividend, phase->intcon(N - 1)));
      // Mask sign bit to the low sign bits
      Node* round = phase->transform(new URShiftINode(sign, phase->intcon(N - l)));
      // Round up before shifting
      dividend = phase->transform(new AddINode(dividend, round));
    }

    // Shift for division
    Node* q = new RShiftINode(dividend, phase->intcon(l));
    if (!d_pos) {
      q = new SubINode(phase->intcon(0), phase->transform(q));
    }

    return q;
  }

  juint magic_const;
  bool magic_const_ovf;
  juint shift_const;
  magic_divide_constants<juint>(d, min_neg, max_pos, 0, magic_const, magic_const_ovf, shift_const);
  assert(!magic_const_ovf, "signed magic constant cannot overflow");

  // x is an i32 and c is a u32, the value of x * c will always lie in the range
  // of an i64, so we can just perform the calculation directly.
  Node* addend0;
  if (jint_mul_no_ovf(dti->_lo, dti->_hi, magic_const)) {
    // If x * c can fit into an i32, we do int multiplication, this may help
    // auto vectorization
    Node* magic = phase->intcon(magic_const);
    Node* mul = phase->transform(new MulINode(dividend, magic));
    addend0 = phase->transform(new RShiftINode(mul, phase->intcon(shift_const)));
  } else {
    Node* magic = phase->longcon(magic_const);
    Node* dividend_long = phase->transform(new ConvI2LNode(dividend));
    Node* mul = phase->transform(new MulLNode(dividend_long, magic));
    addend0 = phase->transform(new RShiftLNode(mul, phase->intcon(shift_const)));
    addend0 = phase->transform(new ConvL2INode(addend0));
  }

  // q = (x * c) >> s + (x < 0 ? 1 : 0) = (x * c) >> s - (x >> (W - 1))
  Node* addend1 = phase->transform(new RShiftINode(dividend, phase->intcon(N - 1)));

  // If the divisor is negative, swap the order of the input addends;
  // this has the effect of negating the quotient
  if (!d_pos) {
    swap(addend0, addend1);
  }
  return new SubINode(addend0, addend1);
}

// Convert an unsigned division by constant divisor into an alternate Ideal graph.
static Node* transform_int_udivide(PhaseGVN* phase, Node* dividend, juint divisor) {
  assert(divisor > 1, "invalid constant divisor");
  constexpr int N = 32;
  const TypeInt* i1 = phase->type(dividend)->is_int();
  juint max_pos = max_unsigned_from_signed_bounds<juint>(i1->_lo, i1->_hi);

  if (max_pos < divisor) {
    return phase->intcon(0);
  }

  // Result
  if (is_power_of_2(divisor)) {
    int l = log2i_exact(divisor);
    return new URShiftINode(dividend, phase->intcon(l));
  }

  juint magic_const;
  bool magic_const_ovf;
  juint shift_const;
  magic_divide_constants<juint>(divisor, 0, max_pos, 0, magic_const, magic_const_ovf, shift_const);

  if (!magic_const_ovf && juint_mul_no_ovf(max_pos, magic_const)) {
    // If x * c can fit into a u32, use int multiplication
    Node* mul = phase->transform(new MulINode(dividend, phase->intcon(magic_const)));
    return new URShiftINode(mul, phase->intcon(shift_const));
  }

  julong magic_const_long = julong(magic_const) + (magic_const_ovf ? julong(max_juint) + 1 : 0);
  // Unsigned extension of dividend
  Node* dividend_long = phase->transform(new ConvI2LNode(dividend));
  dividend_long = phase->transform(new AndLNode(dividend_long, phase->longcon(max_juint)));

  if (julong_mul_no_ovf(julong(max_pos), magic_const_long)) {
    // If x * c can fit into a u64, use long multiplication

    // Java shifts are modular so we need this special case
    if (shift_const == N * 2) {
      return phase->intcon(0);
    }

    // q = (x * c) >> s
    Node* mul = phase->transform(new MulLNode(dividend_long, phase->longcon(magic_const_long)));
    Node* q = phase->transform(new URShiftLNode(mul, phase->intcon(shift_const)));
    return new ConvL2INode(q);
  }

  // Original plan fails, rounding up of 1/divisor does not work, change
  // to rounding down, now it is guaranteed to be correct, according to
  // N-Bit Unsigned Division Via N-Bit Multiply-Add by Arch D. Robison
  magic_divide_constants_round_down(divisor, magic_const, shift_const);

  // q = ((x + 1) * c) >> s, use long arithmetic
  Node* mul = phase->transform(new AddLNode(dividend_long, phase->longcon(1)));
  mul = phase->transform(new MulLNode(mul, phase->longcon(magic_const)));
  Node* q = phase->transform(new URShiftLNode(mul, phase->intcon(shift_const)));
  return new ConvL2INode(q);
}

// Generate ideal node graph for upper half of a 64 bit x 64 bit multiplication
static Node* long_by_long_mulhi(PhaseGVN* phase, Node* dividend, jlong magic_const) {
  // If the architecture supports a 64x64 mulhi, there is
  // no need to synthesize it in ideal nodes.
  if (Matcher::has_match_rule(Op_MulHiL)) {
    Node* v = phase->longcon(magic_const);
    return new MulHiLNode(dividend, v);
  }

  // Taken from Hacker's Delight, Fig. 8-2. Multiply high signed.
  // (http://www.hackersdelight.org/HDcode/mulhs.c)
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

// Convert a division by constant divisor into an alternate Ideal graph.
// Return null if no transformation occurs.
static Node* transform_long_divide(PhaseGVN* phase, Node* dividend, jlong divisor) {
  // Check for invalid divisors
  assert(divisor != 0L && divisor != 1L,
         "bad divisor for transforming to long multiply");
  if (divisor == -1L) {
    return new SubLNode(phase->longcon(0), dividend);
  }

  bool d_pos = divisor >= 0;
  julong d = ABS<jlong>(divisor);
  constexpr int N = 64;
  const TypeLong* dtl = phase->type(dividend)->is_long();
  julong min_neg = dtl->_lo < 0 ? -julong(dtl->_lo) : 0;
  julong max_pos = dtl->_hi > 0 ? julong(dtl->_hi) : 0;
  if (min_neg < d && max_pos < d) {
    return phase->longcon(0);
  }

  if (is_power_of_2(d)) {
    // division by +/- a power of 2
    juint l = log2i_exact(d);

    // See if we can simply do a shift without rounding
    bool needs_rounding = true;
    if (dtl->_lo > 0) {
      // we don't need to round a positive dividend
      needs_rounding = false;
    } else if (dividend->Opcode() == Op_AndL) {
      // An AND mask of sufficient size clears the low bits and
      // I can avoid rounding.
      const TypeLong* andconl_t = phase->type(dividend->in(2))->isa_long();
      if (andconl_t && andconl_t->is_con()) {
        julong andconl = andconl_t->get_con();
        if (count_trailing_zeros(andconl) >= l) {
          if (-andconl == d) {
            // Remove AND if it clears bits which will be shifted
            dividend = dividend->in(1);
          }
          needs_rounding = false;
        }
      }
    }

    if (needs_rounding) {
      // Divide-by-power-of-2 can be made into a shift, but you have to do
      // more math for the rounding.  You need to add 0 for positive
      // numbers, and "i-1" for negative numbers.  Example: i=4, so the
      // shift is by 2.  You need to add 3 to negative dividends and 0 to
      // positive ones.  So (-7+3)>>2 becomes -1, (-4+3)>>2 becomes -1,
      // (-2+3)>>2 becomes 0, etc.

      // Compute 0 or -1, based on sign bit
      Node* sign = phase->transform(new RShiftLNode(dividend, phase->intcon(N - 1)));
      // Mask sign bit to the low sign bits
      Node* round = phase->transform(new URShiftLNode(sign, phase->intcon(N - l)));
      // Round up before shifting
      dividend = phase->transform(new AddLNode(dividend, round));
    }

    // Shift for division
    Node* q = new RShiftLNode(dividend, phase->intcon(l));
    if (!d_pos) {
      q = new SubLNode(phase->longcon(0), phase->transform(q));
    }
    return q;
  }

  if (Matcher::use_asm_for_ldiv_by_con(d)) {
    // Use hardware DIV instruction when
    // it is faster than code generated below.
    return nullptr;
  }

  julong magic_const;
  bool magic_const_ovf;
  juint shift_const;
  magic_divide_constants<julong>(d, min_neg, max_pos, 0, magic_const, magic_const_ovf, shift_const);
  assert(!magic_const_ovf, "signed magic constant cannot overflow");

  Node* addend0;
  if (jlong_mul_no_ovf(dtl->_lo, dtl->_hi, magic_const)) {
    // If c * m can fit into an i64, do the multiplication directly
    Node* mul = phase->transform(new MulLNode(dividend, phase->longcon(magic_const)));
    addend0 = phase->transform(new RShiftLNode(mul, phase->intcon(shift_const)));
  } else {
    if (shift_const < N) {
      // We need i128 arithmetic here, if s < 64 we need to combine the high and low half of the full
      // product, force s to be >= 64 so we only need to use the high half
      magic_divide_constants<julong>(d, min_neg, max_pos, N, magic_const, magic_const_ovf, shift_const);
      assert(!magic_const_ovf, "signed magic constant cannot overflow");
    }

    // Compute the high half of the dividend x magic multiplication
    // (x * c) >> s = ((x * c) >> 64) >> (s - 64) = mul_hi(x, c) >> (s - 64)
    Node* mul_hi = phase->transform(long_by_long_mulhi(phase, dividend, magic_const));
    if (jlong(magic_const) < 0) {
      // The magic multiplier is too large for a 64 bit constant. We've adjusted
      // it down by 2^64, but have to add 1 dividend back in after the multiplication.
      mul_hi = phase->transform(new AddLNode(dividend, mul_hi));
    }

    // Shift over the (adjusted) mulhi
    addend0 = phase->transform(new RShiftLNode(mul_hi, phase->intcon(shift_const - N)));    
  }

  // q = mul_hi(x, c) >> (s - 64) + (x < 0 ? 1 : 0) = mul_hi(x, c) >> (x - 64) - (x >> 63)
  Node *addend1 = phase->transform(new RShiftLNode(dividend, phase->intcon(N - 1)));

  // If the divisor is negative, swap the order of the input addends;
  // this has the effect of negating the quotient.
  if (!d_pos) {
    swap(addend0, addend1);
  }
  return new SubLNode(addend0, addend1);
}

// Convert an unsigned division by constant divisor into an alternate Ideal graph.
// Return null if no transformation occurs.
static Node* transform_long_udivide(PhaseGVN* phase, Node* dividend, julong divisor) {
  assert(divisor > 1, "invalid constant divisor");
  constexpr int N = 64;

  if (is_power_of_2(divisor)) {
    int l = log2i_exact(divisor);
    return new URShiftLNode(dividend, phase->intcon(l));
  }

  if (!Matcher::match_rule_supported(Op_UMulHiL)) {
    return nullptr; // Don't bother
  }

  const TypeLong* i1 = phase->type(dividend)->is_long();
  julong magic_const;
  bool magic_const_ovf;
  juint shift_const;
  julong max_pos = max_unsigned_from_signed_bounds<julong>(i1->_lo, i1->_hi);
  magic_divide_constants<julong>(divisor, 0, max_pos, 0, magic_const, magic_const_ovf, shift_const);

  if (!magic_const_ovf && julong_mul_no_ovf(max_pos, magic_const)) {
    // If x * c can fit into a u64, use long arithmetic
    Node* mul = phase->transform(new MulLNode(dividend, phase->longcon(magic_const)));
    return new URShiftLNode(mul, phase->intcon(shift_const));
  }

  if (shift_const < N) {
    // We need i128 arithmetic here, if s < 64 we need to combine the high and low half of the full
    // product, force s to be >= 64 so we only need to use the high half
    magic_divide_constants<julong>(divisor, 0, max_pos, N, magic_const, magic_const_ovf, shift_const);
  }

  if (!magic_const_ovf || uint128_t_mul_no_ovf(max_pos, magic_const)) {
    // Java shifts are modular so we need this special case
    if (shift_const == N * 2) {
      return phase->longcon(0);
    }

    // q = (x * c) >> s = ((x * c) >> 64) >> (s - 64) = umul_hi(x, c) >> (s - 64)
    Node* mul_hi = phase->transform(new UMulHiLNode(dividend, phase->longcon(magic_const)));
    if (magic_const_ovf) {
      // The magic multiplier is too large for a 64 bit constant. We've adjusted
      // it down by 2^64, but have to add 1 dividend back in after the multiplication.
      mul_hi = phase->transform(new AddLNode(dividend, mul_hi));
    }
    return new URShiftLNode(mul_hi, phase->intcon(shift_const - N));
  }

  if ((divisor & 1) == 0) {
    // x / (2 * d) = (x / 2) / d. This helps decrease the upper bound of the dividend,
    // guarantee that the product of the new dividend and the new magic constant does not
    // overflow
    juint ctz = count_trailing_zeros(divisor);
    dividend = phase->transform(new URShiftLNode(dividend, phase->intcon(ctz)));
    return new UDivLNode(nullptr, dividend, phase->longcon(divisor >> ctz));
  }

  // q = floor((x * c) / 2**(s + 64))) = floor(((x * (c - 2**64)) / 2**64 + x) / 2**s)
  //
  // Given: floor((x / s + y) / n) = floor((floor(x / s) + y) / n), we have
  // q = floor((floor((x * (c - 2**64)) / 2**64) + x) / 2**s)
  //   = floor((mul_hi + x) / 2**s)
  // Let p = floor((mul_hi + x) / 2)
  //       = floor((x - mul_hi) / 2 + mul_hi)
  //       = floor((x - mul_hi) / 2) + mul_hi
  // Since x > mul_hi, this operation can be done precisely using Z/2**64Z arithmetic
  Node* mul_hi = phase->transform(new UMulHiLNode(dividend, phase->longcon(magic_const)));
  Node* diff = phase->transform(new SubLNode(dividend, mul_hi));
  diff = phase->transform(new URShiftLNode(diff, phase->intcon(1)));
  Node* p = phase->transform(new AddLNode(diff, mul_hi));
  return new URShiftLNode(p, phase->intcon(shift_const - N - 1));
}

static Node* divModIdealCommon(Node* n, BasicType bt, PhaseGVN* phase, bool need_const_divisor) {
  // Don't bother trying to transform a dead node
  if (n->in(0) != nullptr && n->in(0)->is_top()) {
    return nullptr;
  }
  const Type* t2 = phase->type(n->in(2));
  if (phase->type(n->in(1)) == Type::TOP || t2 == Type::TOP) {
    return nullptr;
  }

  const TypeInteger* i2 = t2->is_integer(bt);
  // Check for useless control input
  // Check for excluding div-zero case
  if (n->in(0) != nullptr && (i2->lo_as_long() > 0L || i2->hi_as_long() < 0L)) {
    n->set_req(0, nullptr);
    return n;
  }

  if (i2->is_con()) {
    jlong i2_con = i2->get_con_as_long(bt);
    if (i2_con == 0) {
      return nullptr;
    }
    return NodeSentinel;
  }

  return need_const_divisor ? nullptr : NodeSentinel;
}

//=============================================================================
//------------------------------Identity---------------------------------------
// If the divisor is 1, we are an identity on the dividend.
Node* DivINode::Identity(PhaseGVN* phase) {
  return (phase->type( in(2) )->higher_equal(TypeInt::ONE)) ? in(1) : this;
}

//------------------------------Idealize---------------------------------------
// Divides can be changed to multiplies and/or shifts
Node* DivINode::Ideal(PhaseGVN* phase, bool can_reshape) {
  if (in(0) != nullptr && remove_dead_region(phase, can_reshape)) {
    return this;
  }
  
  Node* q = divModIdealCommon(this, T_INT, phase, true);
  if (q != NodeSentinel) {
    return q;
  }

  jint i2_con = phase->type(in(2))->is_int()->get_con();

  if (i2_con == 1) {
    return nullptr;
  }

  q = transform_int_divide(phase, in(1), i2_con);
  assert(q != nullptr, "sanity");
  return q;
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
Node* DivLNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  if (in(0) != nullptr && remove_dead_region(phase, can_reshape)) {
    return this;
  }
  
  Node* q = divModIdealCommon(this, T_LONG, phase, true);
  if (q != NodeSentinel) {
    return q;
  }

  jlong i2_con = phase->type(in(2))->is_long()->get_con();

  if (i2_con == 1) {
    return nullptr;
  }

  q = transform_long_divide(phase, in(1), i2_con);
  assert(q != nullptr || !Matcher::use_asm_for_ldiv_by_con(i2_con), "sanity");
  return q;
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

Node* UDivINode::Identity(PhaseGVN* phase) {
  return (phase->type(in(2))->higher_equal(TypeInt::ONE)) ? in(1) : this;
}

Node* UDivINode::Ideal(PhaseGVN* phase, bool can_reshape) {
  // Check for dead control input
  if (in(0) != nullptr && remove_dead_region(phase, can_reshape)) {
    return this;
  }
  
  Node* q = divModIdealCommon(this, T_INT, phase, false);
  if (q != NodeSentinel) {
    return q;
  }

  const TypeInt* i2 = phase->type(in(2))->is_int();
  // Divisor very large, constant 2**31 can be transform to a shift
  if (i2->_hi <= 0 && i2->_hi > min_jint) {
    Node* cmp = phase->transform(new CmpUNode(in(1), in(2)));
    Node* bol = phase->transform(new BoolNode(cmp, BoolTest::ge));
    return new CMoveINode(bol, phase->intcon(0), phase->intcon(1), TypeInt::BOOL);
  }

  if (!i2->is_con()) {
    return nullptr;
  }
  juint i2_con = i2->get_con();
  if (i2_con == 1) {
    return nullptr;
  }
  if (phase->type(in(1))->is_int()->is_con()) {
    // Don't transform a constant-foldable
    return nullptr;
  }

  q = transform_int_udivide(phase, in(1), i2_con);
  assert(q != nullptr, "sanity");
  return q;
}

const Type* UDivINode::Value(PhaseGVN* phase) const {
  // Either input is TOP ==> the result is TOP
  const Type* t1 = phase->type(in(1));
  const Type* t2 = phase->type(in(2));
  if(t1 == Type::TOP || t2 == Type::TOP) {
    return Type::TOP;
  }

  // x/x == 1 since we always generate the dynamic divisor check for 0.
  if (in(1) == in(2)) {
    return TypeInt::ONE;
  }

  // TODO: Improve Value inference of both signed and unsigned division
  const TypeInt* i1 = t1->is_int();
  const TypeInt* i2 = t2->is_int();
  if (i1->is_con() && i2->is_con()) {
    return TypeInt::make(juint(i1->get_con()) / juint(i2->get_con()));
  }

  return TypeInt::INT;
}

Node* UDivLNode::Identity(PhaseGVN* phase) {
  return (phase->type(in(2))->higher_equal(TypeLong::ONE)) ? in(1) : this;
}

Node* UDivLNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  // Check for dead control input
  if (in(0) != nullptr && remove_dead_region(phase, can_reshape)) {
    return this;
  }
  
  Node* q = divModIdealCommon(this, T_LONG, phase, false);
  if (q != NodeSentinel) {
    return q;
  }

  const TypeLong* i2 = phase->type(in(2))->is_long();
  // Divisor very large, constant 2**63 can be transform to a shift
  if (i2->_hi <= 0 && i2->_hi > min_jlong) {
    Node* cmp = phase->transform(new CmpULNode(in(1), in(2)));
    Node* bol = phase->transform(new BoolNode(cmp, BoolTest::ge));
    return new CMoveLNode(bol, phase->longcon(0), phase->longcon(1), TypeLong::make(0, 1, Type::WidenMin));
  }

  if (!i2->is_con()) {
    return nullptr;
  }
  julong i2_con = i2->get_con();
  if (i2_con == 1) {
    return nullptr;
  }
  if (phase->type(in(1))->is_long()->is_con()) {
    // Don't transform a constant-foldable
    return nullptr;
  }

  q = transform_long_udivide(phase, in(1), i2_con);
  assert(q != nullptr || Matcher::match_rule_supported(Op_UMulHiL), "sanity");
  return q;
}

const Type* UDivLNode::Value(PhaseGVN* phase) const {
  // Either input is TOP ==> the result is TOP
  const Type* t1 = phase->type(in(1));
  const Type* t2 = phase->type(in(2));
  if (t1 == Type::TOP || t2 == Type::TOP) {
    return Type::TOP;
  }

  // x/x == 1 since we always generate the dynamic divisor check for 0.
  if (in(1) == in(2)) {
    return TypeLong::ONE;
  }

  // TODO: Improve Value inference of both signed and unsigned division
  const TypeLong* i1 = t1->is_long();
  const TypeLong* i2 = t2->is_long();
  if (i1->is_con() && i2->is_con()) {
    return TypeLong::make(julong(i1->get_con()) / julong(i2->get_con()));
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

Node* ModINode::Ideal(PhaseGVN* phase, bool can_reshape) {
  // Check for dead control input
  if(in(0) != nullptr && remove_dead_region(phase, can_reshape)) {
    return this;
  }
  
  Node* q = divModIdealCommon(this, T_INT, phase, true);
  if (q != NodeSentinel) {
    return q;
  }

  jint con = phase->type(in(2))->is_int()->get_con();
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
Node *ModLNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Check for dead control input
  if( in(0) && remove_dead_region(phase, can_reshape) )  return this;
  
  Node* q = divModIdealCommon(this, T_LONG, phase, true);
  if (q != NodeSentinel) {
    return q;
  }

  jlong con = phase->type(in(2))->is_long()->get_con();
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

Node* UModINode::Ideal(PhaseGVN* phase, bool can_reshape) {
  // Check for dead control input
  if (in(0) != nullptr && remove_dead_region(phase, can_reshape)) {
    return this;
  }
  Node* q = divModIdealCommon(this, T_INT, phase, true);
  if (q != NodeSentinel) {
    return q;
  }

  if (phase->type(in(1))->is_int()->is_con()) {
    return nullptr;
  }

  juint i2_con = phase->type(in(2))->is_int()->get_con();
  if (is_power_of_2(i2_con)) {
    return new AndINode(in(1), phase->intcon(i2_con - 1));
  }

  // TODO: This can be calculated directly, see https://arxiv.org/abs/1902.01961
  q = transform_int_udivide(phase, in(1), i2_con);
  assert(q != nullptr, "sanity");
  q = phase->transform(q);
  Node* mul = phase->transform(new MulINode(q, phase->intcon(i2_con)));
  return new SubINode(in(1), mul);
}

const Type* UModINode::Value(PhaseGVN* phase) const {
  // Either input is TOP ==> the result is TOP
  const Type* t1 = phase->type(in(1));
  const Type* t2 = phase->type(in(2));
  if (t1 == Type::TOP || t2 == Type::TOP) {
    return Type::TOP;
  }

  // x % x = 0, x % 1 = 0
  if (in(1) == in(2) || t2 == TypeInt::ONE) {
    return TypeInt::ZERO;
  }

  const TypeInt* i1 = t1->is_int();
  const TypeInt* i2 = t2->is_int();

  // constant fold
  if (i1->is_con() && i2->is_con()) {
    return TypeInt::make(juint(i1->get_con()) % juint(i2->get_con()));
  }

  return TypeInt::INT;
}

Node* UModLNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  // Check for dead control input
  if (in(0) != nullptr && remove_dead_region(phase, can_reshape)) {
    return this;
  }
  
  Node* q = divModIdealCommon(this, T_LONG, phase, true);
  if (q != NodeSentinel) {
    return q;
  }

  if (phase->type(in(1))->is_long()->is_con()) {
    return nullptr;
  }

  julong i2_con = phase->type(in(2))->is_long()->get_con();
  if (is_power_of_2(i2_con)) {
    return new AndLNode(in(1), phase->longcon(i2_con - 1));
  }

  q = transform_long_udivide(phase, in(1), i2_con);
  if (q == nullptr) {
    assert(Matcher::match_rule_supported(Op_UMulHiL), "sanity");
    return nullptr;
  }
  q = phase->transform(q);
  Node* mul = phase->transform(new MulLNode(q, phase->longcon(i2_con)));
  return new SubLNode(in(1), mul);
}

const Type* UModLNode::Value(PhaseGVN* phase) const {
  // Either input is TOP ==> the result is TOP
  const Type* t1 = phase->type(in(1));
  const Type* t2 = phase->type(in(2));
  if (t1 == Type::TOP || t2 == Type::TOP) {
    return Type::TOP;
  }

  // x % x = 0, x % 1 = 0
  if (in(1) == in(2) || t2 == TypeLong::ONE) {
    return TypeLong::ZERO;
  }

  const TypeLong* i1 = t1->is_long();
  const TypeLong* i2 = t2->is_long();

  // constant fold
  if (i1->is_con() && i2->is_con()) {
    return TypeLong::make(julong(i1->get_con()) % julong(i2->get_con()));
  }

  return TypeLong::LONG;
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type* ModFNode::Value(PhaseGVN* phase) const {
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

  // If either number is not a constant, we know nothing.
  if ((t1->base() != Type::FloatCon) || (t2->base() != Type::FloatCon)) {
    return Type::FLOAT;         // note: x%x can be either NaN or 0
  }

  float f1 = t1->getf();
  float f2 = t2->getf();
  jint  x1 = jint_cast(f1);     // note:  *(int*)&f1, not just (int)f1
  jint  x2 = jint_cast(f2);

  // If either is a NaN, return an input NaN
  if (g_isnan(f1))    return t1;
  if (g_isnan(f2))    return t2;

  // If an operand is infinity or the divisor is +/- zero, punt.
  if (!g_isfinite(f1) || !g_isfinite(f2) || x2 == 0 || x2 == min_jint)
    return Type::FLOAT;

  // We must be modulo'ing 2 float constants.
  // Make sure that the sign of the fmod is equal to the sign of the dividend
  jint xr = jint_cast(fmod(f1, f2));
  if ((x1 ^ xr) < 0) {
    xr ^= min_jint;
  }

  return TypeF::make(jfloat_cast(xr));
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type* ModDNode::Value(PhaseGVN* phase) const {
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

  // If either number is not a constant, we know nothing.
  if ((t1->base() != Type::DoubleCon) || (t2->base() != Type::DoubleCon)) {
    return Type::DOUBLE;        // note: x%x can be either NaN or 0
  }

  double f1 = t1->getd();
  double f2 = t2->getd();
  jlong  x1 = jlong_cast(f1);   // note:  *(long*)&f1, not just (long)f1
  jlong  x2 = jlong_cast(f2);

  // If either is a NaN, return an input NaN
  if (g_isnan(f1))    return t1;
  if (g_isnan(f2))    return t2;

  // If an operand is infinity or the divisor is +/- zero, punt.
  if (!g_isfinite(f1) || !g_isfinite(f2) || x2 == 0 || x2 == min_jlong)
    return Type::DOUBLE;

  // We must be modulo'ing 2 double constants.
  // Make sure that the sign of the fmod is equal to the sign of the dividend
  jlong xr = jlong_cast(fmod(f1, f2));
  if ((x1 ^ xr) < 0) {
    xr ^= min_jlong;
  }

  return TypeD::make(jdouble_cast(xr));
}

//=============================================================================

DivModNode::DivModNode( Node *c, Node *dividend, Node *divisor ) : MultiNode(3) {
  init_req(0, c);
  init_req(1, dividend);
  init_req(2, divisor);
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
