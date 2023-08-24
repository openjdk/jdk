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
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/memnode.hpp"
#include "opto/mulnode.hpp"
#include "opto/phaseX.hpp"
#include "opto/subnode.hpp"
#include "utilities/powerOfTwo.hpp"

// Portions of code courtesy of Clifford Click


//=============================================================================
//------------------------------hash-------------------------------------------
// Hash function over MulNodes.  Needs to be commutative; i.e., I swap
// (commute) inputs to MulNodes willy-nilly so the hash function must return
// the same value in the presence of edge swapping.
uint MulNode::hash() const {
  return (uintptr_t)in(1) + (uintptr_t)in(2) + Opcode();
}

//------------------------------Identity---------------------------------------
// Multiplying a one preserves the other argument
Node* MulNode::Identity(PhaseGVN* phase) {
  const Type *one = mul_id();  // The multiplicative identity
  if( phase->type( in(1) )->higher_equal( one ) ) return in(2);
  if( phase->type( in(2) )->higher_equal( one ) ) return in(1);

  return this;
}

//------------------------------Ideal------------------------------------------
// We also canonicalize the Node, moving constants to the right input,
// and flatten expressions (so that 1+x+2 becomes x+3).
Node *MulNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  Node* in1 = in(1);
  Node* in2 = in(2);
  Node* progress = nullptr;        // Progress flag

  // This code is used by And nodes too, but some conversions are
  // only valid for the actual Mul nodes.
  uint op = Opcode();
  bool real_mul = (op == Op_MulI) || (op == Op_MulL) ||
                  (op == Op_MulF) || (op == Op_MulD);

  // Convert "(-a)*(-b)" into "a*b".
  if (real_mul && in1->is_Sub() && in2->is_Sub()) {
    if (phase->type(in1->in(1))->is_zero_type() &&
        phase->type(in2->in(1))->is_zero_type()) {
      set_req_X(1, in1->in(2), phase);
      set_req_X(2, in2->in(2), phase);
      in1 = in(1);
      in2 = in(2);
      progress = this;
    }
  }

  // convert "max(a,b) * min(a,b)" into "a*b".
  if ((in(1)->Opcode() == max_opcode() && in(2)->Opcode() == min_opcode())
      || (in(1)->Opcode() == min_opcode() && in(2)->Opcode() == max_opcode())) {
    Node *in11 = in(1)->in(1);
    Node *in12 = in(1)->in(2);

    Node *in21 = in(2)->in(1);
    Node *in22 = in(2)->in(2);

    if ((in11 == in21 && in12 == in22) ||
        (in11 == in22 && in12 == in21)) {
      set_req_X(1, in11, phase);
      set_req_X(2, in12, phase);
      in1 = in(1);
      in2 = in(2);
      progress = this;
    }
  }

  const Type* t1 = phase->type(in1);
  const Type* t2 = phase->type(in2);

  // We are OK if right is a constant, or right is a load and
  // left is a non-constant.
  if( !(t2->singleton() ||
        (in(2)->is_Load() && !(t1->singleton() || in(1)->is_Load())) ) ) {
    if( t1->singleton() ||       // Left input is a constant?
        // Otherwise, sort inputs (commutativity) to help value numbering.
        (in(1)->_idx > in(2)->_idx) ) {
      swap_edges(1, 2);
      const Type *t = t1;
      t1 = t2;
      t2 = t;
      progress = this;            // Made progress
    }
  }

  // If the right input is a constant, and the left input is a product of a
  // constant, flatten the expression tree.
  if( t2->singleton() &&        // Right input is a constant?
      op != Op_MulF &&          // Float & double cannot reassociate
      op != Op_MulD ) {
    if( t2 == Type::TOP ) return nullptr;
    Node *mul1 = in(1);
#ifdef ASSERT
    // Check for dead loop
    int op1 = mul1->Opcode();
    if ((mul1 == this) || (in(2) == this) ||
        ((op1 == mul_opcode() || op1 == add_opcode()) &&
         ((mul1->in(1) == this) || (mul1->in(2) == this) ||
          (mul1->in(1) == mul1) || (mul1->in(2) == mul1)))) {
      assert(false, "dead loop in MulNode::Ideal");
    }
#endif

    if( mul1->Opcode() == mul_opcode() ) {  // Left input is a multiply?
      // Mul of a constant?
      const Type *t12 = phase->type( mul1->in(2) );
      if( t12->singleton() && t12 != Type::TOP) { // Left input is an add of a constant?
        // Compute new constant; check for overflow
        const Type *tcon01 = ((MulNode*)mul1)->mul_ring(t2,t12);
        if( tcon01->singleton() ) {
          // The Mul of the flattened expression
          set_req_X(1, mul1->in(1), phase);
          set_req_X(2, phase->makecon(tcon01), phase);
          t2 = tcon01;
          progress = this;      // Made progress
        }
      }
    }
    // If the right input is a constant, and the left input is an add of a
    // constant, flatten the tree: (X+con1)*con0 ==> X*con0 + con1*con0
    const Node *add1 = in(1);
    if( add1->Opcode() == add_opcode() ) {      // Left input is an add?
      // Add of a constant?
      const Type *t12 = phase->type( add1->in(2) );
      if( t12->singleton() && t12 != Type::TOP ) { // Left input is an add of a constant?
        assert( add1->in(1) != add1, "dead loop in MulNode::Ideal" );
        // Compute new constant; check for overflow
        const Type *tcon01 = mul_ring(t2,t12);
        if( tcon01->singleton() ) {

        // Convert (X+con1)*con0 into X*con0
          Node *mul = clone();    // mul = ()*con0
          mul->set_req(1,add1->in(1));  // mul = X*con0
          mul = phase->transform(mul);

          Node *add2 = add1->clone();
          add2->set_req(1, mul);        // X*con0 + con0*con1
          add2->set_req(2, phase->makecon(tcon01) );
          progress = add2;
        }
      }
    } // End of is left input an add
  } // End of is right input a Mul

  return progress;
}

//------------------------------Value-----------------------------------------
const Type* MulNode::Value(PhaseGVN* phase) const {
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  // Either input is TOP ==> the result is TOP
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // Either input is ZERO ==> the result is ZERO.
  // Not valid for floats or doubles since +0.0 * -0.0 --> +0.0
  int op = Opcode();
  if( op == Op_MulI || op == Op_AndI || op == Op_MulL || op == Op_AndL ) {
    const Type *zero = add_id();        // The multiplicative zero
    if( t1->higher_equal( zero ) ) return zero;
    if( t2->higher_equal( zero ) ) return zero;
  }

  // Either input is BOTTOM ==> the result is the local BOTTOM
  if( t1 == Type::BOTTOM || t2 == Type::BOTTOM )
    return bottom_type();

#if defined(IA32)
  // Can't trust native compilers to properly fold strict double
  // multiplication with round-to-zero on this platform.
  if (op == Op_MulD) {
    return TypeD::DOUBLE;
  }
#endif

  return mul_ring(t1,t2);            // Local flavor of type multiplication
}

MulNode* MulNode::make(Node* in1, Node* in2, BasicType bt) {
  switch (bt) {
    case T_INT:
      return new MulINode(in1, in2);
    case T_LONG:
      return new MulLNode(in1, in2);
    default:
      fatal("Not implemented for %s", type2name(bt));
  }
  return nullptr;
}


//=============================================================================
//------------------------------Ideal------------------------------------------
// Check for power-of-2 multiply, then try the regular MulNode::Ideal
Node *MulINode::Ideal(PhaseGVN *phase, bool can_reshape) {
  const jint con = in(2)->find_int_con(0);
  if (con == 0) {
    // If in(2) is not a constant, call Ideal() of the parent class to
    // try to move constant to the right side.
    return MulNode::Ideal(phase, can_reshape);
  }

  // Now we have a constant Node on the right and the constant in con.
  if (con == 1) {
    // By one is handled by Identity call
    return nullptr;
  }

  // Check for negative constant; if so negate the final result
  bool sign_flip = false;

  unsigned int abs_con = uabs(con);
  if (abs_con != (unsigned int)con) {
    sign_flip = true;
  }

  // Get low bit; check for being the only bit
  Node *res = nullptr;
  unsigned int bit1 = submultiple_power_of_2(abs_con);
  if (bit1 == abs_con) {           // Found a power of 2?
    res = new LShiftINode(in(1), phase->intcon(log2i_exact(bit1)));
  } else {
    // Check for constant with 2 bits set
    unsigned int bit2 = abs_con - bit1;
    bit2 = bit2 & (0 - bit2);          // Extract 2nd bit
    if (bit2 + bit1 == abs_con) {    // Found all bits in con?
      Node *n1 = phase->transform(new LShiftINode(in(1), phase->intcon(log2i_exact(bit1))));
      Node *n2 = phase->transform(new LShiftINode(in(1), phase->intcon(log2i_exact(bit2))));
      res = new AddINode(n2, n1);
    } else if (is_power_of_2(abs_con + 1)) {
      // Sleezy: power-of-2 - 1.  Next time be generic.
      unsigned int temp = abs_con + 1;
      Node *n1 = phase->transform(new LShiftINode(in(1), phase->intcon(log2i_exact(temp))));
      res = new SubINode(n1, in(1));
    } else {
      return MulNode::Ideal(phase, can_reshape);
    }
  }

  if (sign_flip) {             // Need to negate result?
    res = phase->transform(res);// Transform, before making the zero con
    res = new SubINode(phase->intcon(0),res);
  }

  return res;                   // Return final result
}

// Classes to perform mul_ring() for MulI/MulLNode.
//
// This class checks if all cross products of the left and right input of a multiplication have the same "overflow value".
// Without overflow/underflow:
// Product is positive? High signed multiplication result: 0
// Product is negative? High signed multiplication result: -1
//
// We normalize these values (see normalize_overflow_value()) such that we get the same "overflow value" by adding 1 if
// the product is negative. This allows us to compare all the cross product "overflow values". If one is different,
// compared to the others, then we know that this multiplication has a different number of over- or underflows compared
// to the others. In this case, we need to use bottom type and cannot guarantee a better type. Otherwise, we can take
// the min und max of all computed cross products as type of this Mul node.
template<typename IntegerType>
class IntegerMulRing {
  using NativeType = std::conditional_t<std::is_same<TypeInt, IntegerType>::value, jint, jlong>;

  NativeType _lo_left;
  NativeType _lo_right;
  NativeType _hi_left;
  NativeType _hi_right;
  NativeType _lo_lo_product;
  NativeType _lo_hi_product;
  NativeType _hi_lo_product;
  NativeType _hi_hi_product;
  short _widen_left;
  short _widen_right;

  static const Type* overflow_type();
  static NativeType multiply_high_signed_overflow_value(NativeType x, NativeType y);

  // Pre-compute cross products which are used at several places
  void compute_cross_products() {
    _lo_lo_product = java_multiply(_lo_left, _lo_right);
    _lo_hi_product = java_multiply(_lo_left, _hi_right);
    _hi_lo_product = java_multiply(_hi_left, _lo_right);
    _hi_hi_product = java_multiply(_hi_left, _hi_right);
  }

  bool cross_products_not_same_overflow() const {
    const NativeType lo_lo_high_product = multiply_high_signed_overflow_value(_lo_left, _lo_right);
    const NativeType lo_hi_high_product = multiply_high_signed_overflow_value(_lo_left, _hi_right);
    const NativeType hi_lo_high_product = multiply_high_signed_overflow_value(_hi_left, _lo_right);
    const NativeType hi_hi_high_product = multiply_high_signed_overflow_value(_hi_left, _hi_right);
    return lo_lo_high_product != lo_hi_high_product ||
           lo_hi_high_product != hi_lo_high_product ||
           hi_lo_high_product != hi_hi_high_product;
  }

  static NativeType normalize_overflow_value(const NativeType x, const NativeType y, NativeType result) {
    return java_multiply(x, y) < 0 ? result + 1 : result;
  }

 public:
  IntegerMulRing(const IntegerType* left, const IntegerType* right) : _lo_left(left->_lo), _lo_right(right->_lo),
    _hi_left(left->_hi), _hi_right(right->_hi), _widen_left(left->_widen), _widen_right(right->_widen)  {
    compute_cross_products();
  }

  // Compute the product type by multiplying the two input type ranges. We take the minimum and maximum of all possible
  // values (requires 4 multiplications of all possible combinations of the two range boundary values). If any of these
  // multiplications overflows/underflows, we need to make sure that they all have the same number of overflows/underflows
  // If that is not the case, we return the bottom type to cover all values due to the inconsistent overflows/underflows).
  const Type* compute() const {
    if (cross_products_not_same_overflow()) {
      return overflow_type();
    }
    const NativeType min = MIN4(_lo_lo_product, _lo_hi_product, _hi_lo_product, _hi_hi_product);
    const NativeType max = MAX4(_lo_lo_product, _lo_hi_product, _hi_lo_product, _hi_hi_product);
    return IntegerType::make(min, max, MAX2(_widen_left, _widen_right));
  }
};


template <>
const Type* IntegerMulRing<TypeInt>::overflow_type() {
  return TypeInt::INT;
}

template <>
jint IntegerMulRing<TypeInt>::multiply_high_signed_overflow_value(const jint x, const jint y) {
  const jlong x_64 = x;
  const jlong y_64 = y;
  const jlong product = x_64 * y_64;
  const jint result = (jint)((uint64_t)product >> 32u);
  return normalize_overflow_value(x, y, result);
}

template <>
const Type* IntegerMulRing<TypeLong>::overflow_type() {
  return TypeLong::LONG;
}

template <>
jlong IntegerMulRing<TypeLong>::multiply_high_signed_overflow_value(const jlong x, const jlong y) {
  const jlong result = multiply_high_signed(x, y);
  return normalize_overflow_value(x, y, result);
}

// Compute the product type of two integer ranges into this node.
const Type* MulINode::mul_ring(const Type* type_left, const Type* type_right) const {
  const IntegerMulRing<TypeInt> integer_mul_ring(type_left->is_int(), type_right->is_int());
  return integer_mul_ring.compute();
}

// Compute the product type of two long ranges into this node.
const Type* MulLNode::mul_ring(const Type* type_left, const Type* type_right) const {
  const IntegerMulRing<TypeLong> integer_mul_ring(type_left->is_long(), type_right->is_long());
  return integer_mul_ring.compute();
}

//=============================================================================
//------------------------------Ideal------------------------------------------
// Check for power-of-2 multiply, then try the regular MulNode::Ideal
Node *MulLNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  const jlong con = in(2)->find_long_con(0);
  if (con == 0) {
    // If in(2) is not a constant, call Ideal() of the parent class to
    // try to move constant to the right side.
    return MulNode::Ideal(phase, can_reshape);
  }

  // Now we have a constant Node on the right and the constant in con.
  if (con == 1) {
    // By one is handled by Identity call
    return nullptr;
  }

  // Check for negative constant; if so negate the final result
  bool sign_flip = false;
  julong abs_con = uabs(con);
  if (abs_con != (julong)con) {
    sign_flip = true;
  }

  // Get low bit; check for being the only bit
  Node *res = nullptr;
  julong bit1 = submultiple_power_of_2(abs_con);
  if (bit1 == abs_con) {           // Found a power of 2?
    res = new LShiftLNode(in(1), phase->intcon(log2i_exact(bit1)));
  } else {

    // Check for constant with 2 bits set
    julong bit2 = abs_con-bit1;
    bit2 = bit2 & (0-bit2);          // Extract 2nd bit
    if (bit2 + bit1 == abs_con) {    // Found all bits in con?
      Node *n1 = phase->transform(new LShiftLNode(in(1), phase->intcon(log2i_exact(bit1))));
      Node *n2 = phase->transform(new LShiftLNode(in(1), phase->intcon(log2i_exact(bit2))));
      res = new AddLNode(n2, n1);

    } else if (is_power_of_2(abs_con+1)) {
      // Sleezy: power-of-2 -1.  Next time be generic.
      julong temp = abs_con + 1;
      Node *n1 = phase->transform( new LShiftLNode(in(1), phase->intcon(log2i_exact(temp))));
      res = new SubLNode(n1, in(1));
    } else {
      return MulNode::Ideal(phase, can_reshape);
    }
  }

  if (sign_flip) {             // Need to negate result?
    res = phase->transform(res);// Transform, before making the zero con
    res = new SubLNode(phase->longcon(0),res);
  }

  return res;                   // Return final result
}

//=============================================================================
//------------------------------mul_ring---------------------------------------
// Compute the product type of two double ranges into this node.
const Type *MulFNode::mul_ring(const Type *t0, const Type *t1) const {
  if( t0 == Type::FLOAT || t1 == Type::FLOAT ) return Type::FLOAT;
  return TypeF::make( t0->getf() * t1->getf() );
}

//------------------------------Ideal---------------------------------------
// Check to see if we are multiplying by a constant 2 and convert to add, then try the regular MulNode::Ideal
Node* MulFNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  const TypeF *t2 = phase->type(in(2))->isa_float_constant();

  // x * 2 -> x + x
  if (t2 != nullptr && t2->getf() == 2) {
    Node* base = in(1);
    return new AddFNode(base, base);
  }

  return MulNode::Ideal(phase, can_reshape);
}

//=============================================================================
//------------------------------mul_ring---------------------------------------
// Compute the product type of two double ranges into this node.
const Type *MulDNode::mul_ring(const Type *t0, const Type *t1) const {
  if( t0 == Type::DOUBLE || t1 == Type::DOUBLE ) return Type::DOUBLE;
  // We must be multiplying 2 double constants.
  return TypeD::make( t0->getd() * t1->getd() );
}

//------------------------------Ideal---------------------------------------
// Check to see if we are multiplying by a constant 2 and convert to add, then try the regular MulNode::Ideal
Node* MulDNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  const TypeD *t2 = phase->type(in(2))->isa_double_constant();

  // x * 2 -> x + x
  if (t2 != nullptr && t2->getd() == 2) {
    Node* base = in(1);
    return new AddDNode(base, base);
  }

  return MulNode::Ideal(phase, can_reshape);
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type* MulHiLNode::Value(PhaseGVN* phase) const {
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  const Type *bot = bottom_type();
  return MulHiValue(t1, t2, bot);
}

const Type* UMulHiLNode::Value(PhaseGVN* phase) const {
  const Type *t1 = phase->type( in(1) );
  const Type *t2 = phase->type( in(2) );
  const Type *bot = bottom_type();
  return MulHiValue(t1, t2, bot);
}

// A common routine used by UMulHiLNode and MulHiLNode
const Type* MulHiValue(const Type *t1, const Type *t2, const Type *bot) {
  // Either input is TOP ==> the result is TOP
  if( t1 == Type::TOP ) return Type::TOP;
  if( t2 == Type::TOP ) return Type::TOP;

  // Either input is BOTTOM ==> the result is the local BOTTOM
  if( (t1 == bot) || (t2 == bot) ||
      (t1 == Type::BOTTOM) || (t2 == Type::BOTTOM) )
    return bot;

  // It is not worth trying to constant fold this stuff!
  return TypeLong::LONG;
}

//=============================================================================
Node *AndINode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // pattern similar to (v1 + (v2 << 2)) & 3 transformed to v1 & 3
  Node* progress = AndIL_add_shift_and_mask(phase, T_INT);
  if (progress != nullptr) {
    return progress;
  }

  // Special case constant AND mask
  const TypeInt *t2 = phase->type( in(2) )->isa_int();
  if( !t2 || !t2->is_con() ) return MulNode::Ideal(phase, can_reshape);
  const int mask = t2->get_con();
  Node *load = in(1);
  uint lop = load->Opcode();

  // Masking bits off of a Character?  Hi bits are already zero.
  if( lop == Op_LoadUS &&
      (mask & 0xFFFF0000) )     // Can we make a smaller mask?
    return new AndINode(load,phase->intcon(mask&0xFFFF));

  // Masking bits off of a Short?  Loading a Character does some masking
  if (can_reshape &&
      load->outcnt() == 1 && load->unique_out() == this) {
    if (lop == Op_LoadS && (mask & 0xFFFF0000) == 0 ) {
      Node* ldus = load->as_Load()->convert_to_unsigned_load(*phase);
      ldus = phase->transform(ldus);
      return new AndINode(ldus, phase->intcon(mask & 0xFFFF));
    }

    // Masking sign bits off of a Byte?  Do an unsigned byte load plus
    // an and.
    if (lop == Op_LoadB && (mask & 0xFFFFFF00) == 0) {
      Node* ldub = load->as_Load()->convert_to_unsigned_load(*phase);
      ldub = phase->transform(ldub);
      return new AndINode(ldub, phase->intcon(mask));
    }
  }

  // Masking off sign bits?  Dont make them!
  if( lop == Op_RShiftI ) {
    const TypeInt *t12 = phase->type(load->in(2))->isa_int();
    if( t12 && t12->is_con() ) { // Shift is by a constant
      int shift = t12->get_con();
      shift &= BitsPerJavaInteger-1;  // semantics of Java shifts
      const int sign_bits_mask = ~right_n_bits(BitsPerJavaInteger - shift);
      // If the AND'ing of the 2 masks has no bits, then only original shifted
      // bits survive.  NO sign-extension bits survive the maskings.
      if( (sign_bits_mask & mask) == 0 ) {
        // Use zero-fill shift instead
        Node *zshift = phase->transform(new URShiftINode(load->in(1),load->in(2)));
        return new AndINode( zshift, in(2) );
      }
    }
  }

  // Check for 'negate/and-1', a pattern emitted when someone asks for
  // 'mod 2'.  Negate leaves the low order bit unchanged (think: complement
  // plus 1) and the mask is of the low order bit.  Skip the negate.
  if( lop == Op_SubI && mask == 1 && load->in(1) &&
      phase->type(load->in(1)) == TypeInt::ZERO )
    return new AndINode( load->in(2), in(2) );

  return MulNode::Ideal(phase, can_reshape);
}

Node *AndLNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // pattern similar to (v1 + (v2 << 2)) & 3 transformed to v1 & 3
  Node* progress = AndIL_add_shift_and_mask(phase, T_LONG);
  if (progress != nullptr) {
    return progress;
  }

  // Special case constant AND mask
  const TypeLong *t2 = phase->type( in(2) )->isa_long();
  if( !t2 || !t2->is_con() ) return MulNode::Ideal(phase, can_reshape);
  const jlong mask = t2->get_con();

  Node* in1 = in(1);
  int op = in1->Opcode();

  // Are we masking a long that was converted from an int with a mask
  // that fits in 32-bits?  Commute them and use an AndINode.  Don't
  // convert masks which would cause a sign extension of the integer
  // value.  This check includes UI2L masks (0x00000000FFFFFFFF) which
  // would be optimized away later in Identity.
  if (op == Op_ConvI2L && (mask & UCONST64(0xFFFFFFFF80000000)) == 0) {
    Node* andi = new AndINode(in1->in(1), phase->intcon(mask));
    andi = phase->transform(andi);
    return new ConvI2LNode(andi);
  }

  // Masking off sign bits?  Dont make them!
  if (op == Op_RShiftL) {
    const TypeInt* t12 = phase->type(in1->in(2))->isa_int();
    if( t12 && t12->is_con() ) { // Shift is by a constant
      int shift = t12->get_con();
      shift &= BitsPerJavaLong - 1;  // semantics of Java shifts
      const julong sign_bits_mask = ~(((julong)CONST64(1) << (julong)(BitsPerJavaLong - shift)) -1);
      // If the AND'ing of the 2 masks has no bits, then only original shifted
      // bits survive.  NO sign-extension bits survive the maskings.
      if( (sign_bits_mask & mask) == 0 ) {
        // Use zero-fill shift instead
        Node *zshift = phase->transform(new URShiftLNode(in1->in(1), in1->in(2)));
        return new AndLNode(zshift, in(2));
      }
    }
  }

  return MulNode::Ideal(phase, can_reshape);
}

template <class CT>
static Node* and_id(Node* n, PhaseGVN* phase) {
  Node* in1 = n->in(1);
  Node* in2 = n->in(2);

  // x | x => x
  if (in1 == in2) {
    return in1;
  }

  const CT* i1 = CT::try_cast(phase->type(in1));
  const CT* i2 = CT::try_cast(phase->type(in2));
  if (i1 == nullptr || i2 == nullptr) {
    return n;
  }

  // If all unset bits of x are unset in y then return y
  if ((~i1->_ones & ~i2->_zeros) == 0) {
    return in2;
  }
  if ((~i2->_ones & ~i1->_zeros) == 0) {
    return in1;
  }
  return n;
}

Node* AndINode::Identity(PhaseGVN* phase) {
  return and_id<TypeInt>(this, phase);
}

Node* AndLNode::Identity(PhaseGVN* phase) {
  return and_id<TypeLong>(this, phase);
}

template <class CT>
static const Type* and_mul_ring(const Type* t1, const Type* t2) {
  using T = decltype(CT::_lo);
  using U = decltype(CT::_ulo);
  const CT* i1 = CT::cast(t1);
  const CT* i2 = CT::cast(t2);

  // Identity, if all unset bits of x are unset in y then return y
  if ((~i1->_ones & ~i2->_zeros) == 0) {
    return i2;
  }
  if ((~i2->_ones & ~i1->_zeros) == 0) {
    return i1;
  }

  T lo = std::numeric_limits<T>::min();
  T hi = std::numeric_limits<T>::max();
  U ulo = 0;
  U uhi = MIN2(i1->_uhi, i2->_uhi);
  U zeros = i1->_zeros | i2->_zeros;
  U ones = i1->_ones & i2->_ones;
  int w = MAX2(i1->_widen, i2->_widen);
  return CT::make(lo, hi, ulo, uhi, zeros, ones, w);
}

template <class CT>
static const Type* and_value(Node* in1, Node* in2, PhaseGVN* phase) {
  const Type* t1 = phase->type(in1);
  const Type* t2 = phase->type(in2);
  if (t1 == Type::TOP || t2 == Type::TOP) {
    return Type::TOP;
  }

  return and_mul_ring<CT>(t1, t2);
}

const Type* AndINode::mul_ring(const Type* t1, const Type* t2) const {
  return and_mul_ring<TypeInt>(t1, t2);
}

const Type* AndLNode::mul_ring(const Type* t1, const Type* t2) const {
  return and_mul_ring<TypeLong>(t1, t2);
}

const Type* AndINode::Value(PhaseGVN* phase) const {
  return and_value<TypeInt>(in(1), in(2), phase);
}

const Type* AndLNode::Value(PhaseGVN* phase) const {
  return and_value<TypeLong>(in(1), in(2), phase);
}

//=============================================================================

LShiftNode* LShiftNode::make(Node* in1, Node* in2, BasicType bt) {
  switch (bt) {
    case T_INT:
      return new LShiftINode(in1, in2);
    case T_LONG:
      return new LShiftLNode(in1, in2);
    default:
      fatal("Not implemented for %s", type2name(bt));
  }
  return nullptr;
}

//=============================================================================

static bool const_shift_count(PhaseGVN* phase, Node* shiftNode, int* count) {
  const TypeInt* tcount = phase->type(shiftNode->in(2))->isa_int();
  if (tcount != nullptr && tcount->is_con()) {
    *count = tcount->get_con();
    return true;
  }
  return false;
}

static int maskShiftAmount(PhaseGVN* phase, Node* shiftNode, int nBits) {
  int count = 0;
  if (const_shift_count(phase, shiftNode, &count)) {
    int maskedShift = count & (nBits - 1);
    if (maskedShift == 0) {
      // Let Identity() handle 0 shift count.
      return 0;
    }

    if (count != maskedShift) {
      shiftNode->set_req(2, phase->intcon(maskedShift)); // Replace shift count with masked value.
      PhaseIterGVN* igvn = phase->is_IterGVN();
      if (igvn) {
        igvn->rehash_node_delayed(shiftNode);
      }
    }
    return maskedShift;
  }
  return 0;
}

//------------------------------Ideal------------------------------------------
// If the right input is a constant, and the left input is an add of a
// constant, flatten the tree: (X+con1)<<con0 ==> X<<con0 + con1<<con0
Node *LShiftINode::Ideal(PhaseGVN *phase, bool can_reshape) {
  int con = maskShiftAmount(phase, this, BitsPerJavaInteger);
  if (con == 0) {
    return nullptr;
  }

  // Left input is an add?
  Node *add1 = in(1);
  int add1_op = add1->Opcode();
  if( add1_op == Op_AddI ) {    // Left input is an add?
    assert( add1 != add1->in(1), "dead loop in LShiftINode::Ideal" );

    // Transform is legal, but check for profit.  Avoid breaking 'i2s'
    // and 'i2b' patterns which typically fold into 'StoreC/StoreB'.
    if( con < 16 ) {
      // Left input is an add of the same number?
      if (add1->in(1) == add1->in(2)) {
        // Convert "(x + x) << c0" into "x << (c0 + 1)"
        // In general, this optimization cannot be applied for c0 == 31 since
        // 2x << 31 != x << 32 = x << 0 = x (e.g. x = 1: 2 << 31 = 0 != 1)
        return new LShiftINode(add1->in(1), phase->intcon(con + 1));
      }

      // Left input is an add of a constant?
      const TypeInt *t12 = phase->type(add1->in(2))->isa_int();
      if( t12 && t12->is_con() ){ // Left input is an add of a con?
        // Compute X << con0
        Node *lsh = phase->transform( new LShiftINode( add1->in(1), in(2) ) );
        // Compute X<<con0 + (con1<<con0)
        return new AddINode( lsh, phase->intcon(t12->get_con() << con));
      }
    }
  }

  // Check for "(x >> C1) << C2"
  if (add1_op == Op_RShiftI || add1_op == Op_URShiftI) {
    int add1Con = 0;
    const_shift_count(phase, add1, &add1Con);

    // Special case C1 == C2, which just masks off low bits
    if (add1Con > 0 && con == add1Con) {
      // Convert to "(x & -(1 << C2))"
      return new AndINode(add1->in(1), phase->intcon(java_negate(jint(1 << con))));
    } else {
      // Wait until the right shift has been sharpened to the correct count
      if (add1Con > 0 && add1Con < BitsPerJavaInteger) {
        // As loop parsing can produce LShiftI nodes, we should wait until the graph is fully formed
        // to apply optimizations, otherwise we can inadvertently stop vectorization opportunities.
        if (phase->is_IterGVN()) {
          if (con > add1Con) {
            // Creates "(x << (C2 - C1)) & -(1 << C2)"
            Node* lshift = phase->transform(new LShiftINode(add1->in(1), phase->intcon(con - add1Con)));
            return new AndINode(lshift, phase->intcon(java_negate(jint(1 << con))));
          } else {
            assert(con < add1Con, "must be (%d < %d)", con, add1Con);
            // Creates "(x >> (C1 - C2)) & -(1 << C2)"

            // Handle logical and arithmetic shifts
            Node* rshift;
            if (add1_op == Op_RShiftI) {
              rshift = phase->transform(new RShiftINode(add1->in(1), phase->intcon(add1Con - con)));
            } else {
              rshift = phase->transform(new URShiftINode(add1->in(1), phase->intcon(add1Con - con)));
            }

            return new AndINode(rshift, phase->intcon(java_negate(jint(1 << con))));
          }
        } else {
          phase->record_for_igvn(this);
        }
      }
    }
  }

  // Check for "((x >> C1) & Y) << C2"
  if (add1_op == Op_AndI) {
    Node *add2 = add1->in(1);
    int add2_op = add2->Opcode();
    if (add2_op == Op_RShiftI || add2_op == Op_URShiftI) {
      // Special case C1 == C2, which just masks off low bits
      if (add2->in(2) == in(2)) {
        // Convert to "(x & (Y << C2))"
        Node* y_sh = phase->transform(new LShiftINode(add1->in(2), phase->intcon(con)));
        return new AndINode(add2->in(1), y_sh);
      }

      int add2Con = 0;
      const_shift_count(phase, add2, &add2Con);
      if (add2Con > 0 && add2Con < BitsPerJavaInteger) {
        if (phase->is_IterGVN()) {
          // Convert to "((x >> C1) << C2) & (Y << C2)"

          // Make "(x >> C1) << C2", which will get folded away by the rule above
          Node* x_sh = phase->transform(new LShiftINode(add2, phase->intcon(con)));
          // Make "Y << C2", which will simplify when Y is a constant
          Node* y_sh = phase->transform(new LShiftINode(add1->in(2), phase->intcon(con)));

          return new AndINode(x_sh, y_sh);
        } else {
          phase->record_for_igvn(this);
        }
      }
    }
  }

  // Check for ((x & ((1<<(32-c0))-1)) << c0) which ANDs off high bits
  // before shifting them away.
  const jint bits_mask = right_n_bits(BitsPerJavaInteger-con);
  if( add1_op == Op_AndI &&
      phase->type(add1->in(2)) == TypeInt::make( bits_mask ) )
    return new LShiftINode( add1->in(1), in(2) );

  return nullptr;
}

Node *LShiftLNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  int con = maskShiftAmount(phase, this, BitsPerJavaLong);
  if (con == 0) {
    return nullptr;
  }

  // Left input is an add?
  Node *add1 = in(1);
  int add1_op = add1->Opcode();
  if( add1_op == Op_AddL ) {    // Left input is an add?
    // Avoid dead data cycles from dead loops
    assert( add1 != add1->in(1), "dead loop in LShiftLNode::Ideal" );

    // Left input is an add of the same number?
    if (con != (BitsPerJavaLong - 1) && add1->in(1) == add1->in(2)) {
      // Convert "(x + x) << c0" into "x << (c0 + 1)"
      // Can only be applied if c0 != 63 because:
      // (x + x) << 63 = 2x << 63, while
      // (x + x) << 63 --transform--> x << 64 = x << 0 = x (!= 2x << 63, for example for x = 1)
      // According to the Java spec, chapter 15.19, we only consider the six lowest-order bits of the right-hand operand
      // (i.e. "right-hand operand" & 0b111111). Therefore, x << 64 is the same as x << 0 (64 = 0b10000000 & 0b0111111 = 0).
      return new LShiftLNode(add1->in(1), phase->intcon(con + 1));
    }

    // Left input is an add of a constant?
    const TypeLong *t12 = phase->type(add1->in(2))->isa_long();
    if( t12 && t12->is_con() ){ // Left input is an add of a con?
      // Compute X << con0
      Node *lsh = phase->transform( new LShiftLNode( add1->in(1), in(2) ) );
      // Compute X<<con0 + (con1<<con0)
      return new AddLNode( lsh, phase->longcon(t12->get_con() << con));
    }
  }

  // Check for "(x >> C1) << C2"
  if (add1_op == Op_RShiftL || add1_op == Op_URShiftL) {
    int add1Con = 0;
    const_shift_count(phase, add1, &add1Con);

    // Special case C1 == C2, which just masks off low bits
    if (add1Con > 0 && con == add1Con) {
      // Convert to "(x & -(1 << C2))"
      return new AndLNode(add1->in(1), phase->longcon(java_negate(jlong(CONST64(1) << con))));
    } else {
      // Wait until the right shift has been sharpened to the correct count
      if (add1Con > 0 && add1Con < BitsPerJavaLong) {
        // As loop parsing can produce LShiftI nodes, we should wait until the graph is fully formed
        // to apply optimizations, otherwise we can inadvertently stop vectorization opportunities.
        if (phase->is_IterGVN()) {
          if (con > add1Con) {
            // Creates "(x << (C2 - C1)) & -(1 << C2)"
            Node* lshift = phase->transform(new LShiftLNode(add1->in(1), phase->intcon(con - add1Con)));
            return new AndLNode(lshift, phase->longcon(java_negate(jlong(CONST64(1) << con))));
          } else {
            assert(con < add1Con, "must be (%d < %d)", con, add1Con);
            // Creates "(x >> (C1 - C2)) & -(1 << C2)"

            // Handle logical and arithmetic shifts
            Node* rshift;
            if (add1_op == Op_RShiftL) {
              rshift = phase->transform(new RShiftLNode(add1->in(1), phase->intcon(add1Con - con)));
            } else {
              rshift = phase->transform(new URShiftLNode(add1->in(1), phase->intcon(add1Con - con)));
            }

            return new AndLNode(rshift, phase->longcon(java_negate(jlong(CONST64(1) << con))));
          }
        } else {
          phase->record_for_igvn(this);
        }
      }
    }
  }

  // Check for "((x >> C1) & Y) << C2"
  if (add1_op == Op_AndL) {
    Node* add2 = add1->in(1);
    int add2_op = add2->Opcode();
    if (add2_op == Op_RShiftL || add2_op == Op_URShiftL) {
      // Special case C1 == C2, which just masks off low bits
      if (add2->in(2) == in(2)) {
        // Convert to "(x & (Y << C2))"
        Node* y_sh = phase->transform(new LShiftLNode(add1->in(2), phase->intcon(con)));
        return new AndLNode(add2->in(1), y_sh);
      }

      int add2Con = 0;
      const_shift_count(phase, add2, &add2Con);
      if (add2Con > 0 && add2Con < BitsPerJavaLong) {
        if (phase->is_IterGVN()) {
          // Convert to "((x >> C1) << C2) & (Y << C2)"

          // Make "(x >> C1) << C2", which will get folded away by the rule above
          Node* x_sh = phase->transform(new LShiftLNode(add2, phase->intcon(con)));
          // Make "Y << C2", which will simplify when Y is a constant
          Node* y_sh = phase->transform(new LShiftLNode(add1->in(2), phase->intcon(con)));

          return new AndLNode(x_sh, y_sh);
        } else {
          phase->record_for_igvn(this);
        }
      }
    }
  }

  // Check for ((x & ((CONST64(1)<<(64-c0))-1)) << c0) which ANDs off high bits
  // before shifting them away.
  const jlong bits_mask = jlong(max_julong >> con);
  if( add1_op == Op_AndL &&
      phase->type(add1->in(2)) == TypeLong::make( bits_mask ) )
    return new LShiftLNode( add1->in(1), in(2) );

  return nullptr;
}

template <class CT>
static Node* lshift_id(Node* n, PhaseGVN* phase) {
  using T = std::remove_const_t<decltype(CT::_lo)>;
  constexpr juint W = sizeof(T) * 8;
  const TypeInt* i2 = TypeInt::try_cast(phase->type(n->in(2)));
  if (i2 == nullptr) {
    return n;
  }

  i2 = and_mul_ring<TypeInt>(i2, TypeInt::make(W - 1))->is_int();
  if (i2 == TypeInt::ZERO) {
    return n->in(1);
  }
  return n;
}

Node* LShiftINode::Identity(PhaseGVN* phase) {
  return lshift_id<TypeInt>(this, phase);
}

Node* LShiftLNode::Identity(PhaseGVN* phase) {
  return lshift_id<TypeLong>(this, phase);
}

template <class CT>
static const Type* lshift_value(const Node* in1, const Node* in2, PhaseGVN* phase) {
  using T = std::remove_const_t<decltype(CT::_lo)>;
  using U = std::remove_const_t<decltype(CT::_ulo)>;
  constexpr juint W = sizeof(U) * 8;
  const Type* t1 = phase->type(in1);
  const Type* t2 = phase->type(in2);
  if (t1 == Type::TOP || t2 == Type::TOP) {
    return Type::TOP;
  }

  const CT* i1 = CT::cast(t1);
  const TypeInt* i2 = TypeInt::cast(t2);
  i2 = and_mul_ring<TypeInt>(i2, TypeInt::make(W - 1))->is_int();

  T lo = std::numeric_limits<T>::min();
  T hi = std::numeric_limits<T>::max();
  if ((std::numeric_limits<T>::min() >> i2->_uhi) <= i1->_lo &&
      (std::numeric_limits<T>::max() >> i2->_uhi) >= i1->_hi) {
    lo = i1->_lo < 0 ? (i1->_lo << i2->_uhi) : (i1->_lo << i2->_ulo);
    hi = i1->_hi < 0 ? (i1->_hi << i2->_ulo) : (i1->_hi << i2->_uhi);
  }

  U ulo = 0;
  U uhi = std::numeric_limits<U>::max();
  if ((std::numeric_limits<U>::max() >> i2->_uhi) >= i1->_uhi) {
    ulo = i1->_ulo << i2->_ulo;
    uhi = i1->_uhi << i2->_uhi;
  }

  U zeros = std::numeric_limits<U>::max();
  U ones = zeros;
  for (juint s = i2->_ulo; s <= i2->_uhi; s++) {
    if (!i2->contains(s)) {
      continue;
    }

    U current_zeros = i1->_zeros;
    if (s > 0) {
      current_zeros = (i1->_zeros << s) | (std::numeric_limits<U>::max() >> (W - s));
    }
    zeros &= current_zeros;

    U current_ones = i1->_ones << s;
    ones &= current_ones;
  }

  return CT::make(lo, hi, ulo, uhi, zeros, ones, MAX2(i1->_widen, i2->_widen));
}

const Type* LShiftINode::Value(PhaseGVN* phase) const {
  return lshift_value<TypeInt>(in(1), in(2), phase);
}

const Type* LShiftLNode::Value(PhaseGVN* phase) const {
  return lshift_value<TypeLong>(in(1), in(2), phase);
}

//=============================================================================
Node *RShiftINode::Ideal(PhaseGVN *phase, bool can_reshape) {
  // Inputs may be TOP if they are dead.
  const TypeInt *t1 = phase->type(in(1))->isa_int();
  if (!t1) return nullptr;        // Left input is an integer
  const TypeInt *t3;  // type of in(1).in(2)
  int shift = maskShiftAmount(phase, this, BitsPerJavaInteger);
  if (shift == 0) {
    return nullptr;
  }

  // Check for (x & 0xFF000000) >> 24, whose mask can be made smaller.
  // Such expressions arise normally from shift chains like (byte)(x >> 24).
  const Node *mask = in(1);
  if( mask->Opcode() == Op_AndI &&
      (t3 = phase->type(mask->in(2))->isa_int()) &&
      t3->is_con() ) {
    Node *x = mask->in(1);
    jint maskbits = t3->get_con();
    // Convert to "(x >> shift) & (mask >> shift)"
    Node *shr_nomask = phase->transform( new RShiftINode(mask->in(1), in(2)) );
    return new AndINode(shr_nomask, phase->intcon( maskbits >> shift));
  }

  // Check for "(short[i] <<16)>>16" which simply sign-extends
  const Node *shl = in(1);
  if( shl->Opcode() != Op_LShiftI ) return nullptr;

  if( shift == 16 &&
      (t3 = phase->type(shl->in(2))->isa_int()) &&
      t3->is_con(16) ) {
    Node *ld = shl->in(1);
    if( ld->Opcode() == Op_LoadS ) {
      // Sign extension is just useless here.  Return a RShiftI of zero instead
      // returning 'ld' directly.  We cannot return an old Node directly as
      // that is the job of 'Identity' calls and Identity calls only work on
      // direct inputs ('ld' is an extra Node removed from 'this').  The
      // combined optimization requires Identity only return direct inputs.
      set_req_X(1, ld, phase);
      set_req_X(2, phase->intcon(0), phase);
      return this;
    }
    else if (can_reshape &&
             ld->Opcode() == Op_LoadUS &&
             ld->outcnt() == 1 && ld->unique_out() == shl)
      // Replace zero-extension-load with sign-extension-load
      return ld->as_Load()->convert_to_signed_load(*phase);
  }

  // Check for "(byte[i] <<24)>>24" which simply sign-extends
  if( shift == 24 &&
      (t3 = phase->type(shl->in(2))->isa_int()) &&
      t3->is_con(24) ) {
    Node *ld = shl->in(1);
    if (ld->Opcode() == Op_LoadB) {
      // Sign extension is just useless here
      set_req_X(1, ld, phase);
      set_req_X(2, phase->intcon(0), phase);
      return this;
    }
  }

  return nullptr;
}

template <class CT, bool is_signed>
static Node* rshift_id(Node* n, PhaseGVN* phase) {
  using T = std::remove_const_t<decltype(CT::_lo)>;
  using U = std::remove_const_t<decltype(CT::_ulo)>;
  constexpr juint W = sizeof(T) * 8;
  const TypeInt* i2 = TypeInt::try_cast(phase->type(n->in(2)));
  if (i2 == nullptr) {
    return n;
  }

  i2 = and_mul_ring<TypeInt>(i2, TypeInt::make(W - 1))->is_int();
  if (i2 == TypeInt::ZERO) {
    return n->in(1);
  }

  auto lshift_no_ovf = [](const CT* i1, const TypeInt* i2){
    if (is_signed) {
      return (std::numeric_limits<T>::min() >> i2->_uhi) <= i1->_lo &&
             (std::numeric_limits<T>::max() >> i2->_uhi) >= i1->_hi;
    } else {
      return (std::numeric_limits<U>::max() >> i2->_uhi) >= i1->_uhi;
    }
  };

  constexpr int Op_LShift = std::is_same<CT, TypeInt>::value ? Op_LShiftI : Op_LShiftL;
  constexpr int Op_Add = std::is_same<CT, TypeInt>::value ? Op_AddI : Op_AddL;
  constexpr int Op_Sub = std::is_same<CT, TypeInt>::value ? Op_SubI : Op_SubL;
  int op1 = n->in(1)->Opcode();

  // Check for useless sign-masking
  // (X << Y) >> Y = X if X << Y does not overflow
  if (op1 == Op_LShift && n->in(1)->in(2) == n->in(2)) {
    const CT* i11 = CT::try_cast(phase->type(n->in(1)->in(1)));
    if (i11 == nullptr) {
      return n;
    }
    if (lshift_no_ovf(i11, i2)) {
      return n->in(1)->in(1);
    }
  }

  // ((X << Y) + Z) >> Y = X if X << Y does not overflow and Z < (1 << Y)
  if ((op1 == Op_Add || op1 == Op_Sub) &&
      n->in(1)->in(1)->Opcode() == Op_LShift &&
      n->in(1)->in(1)->in(2) == n->in(2)) {
    const CT* i111 = CT::try_cast(phase->type(n->in(1)->in(1)->in(1)));
    const CT* i12 = CT::try_cast(phase->type(n->in(1)->in(2)));
    if (i111 == nullptr || i12 == nullptr) {
      return n;
    }
    if (!lshift_no_ovf(i111, i2)) {
      return n;
    }
    U bound = i2->_ulo;
    if ((op1 == Op_Add && i12->_uhi < bound) ||
        (op1 == Op_Sub && i12->_hi <= 0 && -U(i12->_lo) < bound)) {
      return n->in(1)->in(1)->in(1);
    }
  }

  return n;
}

Node* RShiftINode::Identity(PhaseGVN* phase) {
  return rshift_id<TypeInt, true>(this, phase);
}

Node* RShiftLNode::Identity(PhaseGVN* phase) {
  return rshift_id<TypeLong, true>(this, phase);
}

template <class CT>
static const Type* rshift_value(const Node* in1, const Node* in2, PhaseGVN* phase) {
  using T = std::remove_const_t<decltype(CT::_lo)>;
  using U = std::remove_const_t<decltype(CT::_ulo)>;
  constexpr juint W = sizeof(U) * 8;
  const Type* t1 = phase->type(in1);
  const Type* t2 = phase->type(in2);
  if (t1 == Type::TOP || t2 == Type::TOP) {
    return Type::TOP;
  }

  const CT* i1 = CT::cast(t1);
  const TypeInt* i2 = TypeInt::cast(t2);
  i2 = and_mul_ring<TypeInt>(i2, TypeInt::make(W - 1))->is_int();

  T lo = i1->_lo < 0 ? (i1->_lo >> i2->_ulo) : (i1->_lo >> i2->_uhi);
  T hi = i1->_hi < 0 ? (i1->_hi >> i2->_uhi) : (i1->_hi >> i2->_ulo);

  U ulo = T(i1->_ulo) >= 0 ? (i1->_ulo >> i2->_uhi) : (T(i1->_ulo) >> i2->_ulo);
  U uhi = T(i1->_uhi) >= 0 ? (i1->_uhi >> i2->_ulo) : (T(i1->_uhi) >> i2->_uhi);

  U zeros = std::numeric_limits<U>::max();
  U ones = zeros;
  for (juint s = i2->_ulo; s <= i2->_uhi; s++) {
    if (!i2->contains(s)) {
      continue;
    }

    U current_zeros = T(i1->_zeros) >> s;
    zeros &= current_zeros;

    U current_ones = T(i1->_ones) >> s;
    ones &= current_ones;
  }

  return CT::make(lo, hi, ulo, uhi, zeros, ones, MAX2(i1->_widen, i2->_widen));
}

const Type* RShiftINode::Value(PhaseGVN* phase) const {
  return rshift_value<TypeInt>(in(1), in(2), phase);
}

const Type* RShiftLNode::Value(PhaseGVN* phase) const {
  return rshift_value<TypeLong>(in(1), in(2), phase);
}

//=============================================================================
Node *URShiftINode::Ideal(PhaseGVN *phase, bool can_reshape) {
  int con = maskShiftAmount(phase, this, BitsPerJavaInteger);
  if (con == 0) {
    return nullptr;
  }

  // We'll be wanting the right-shift amount as a mask of that many bits
  const int mask = right_n_bits(BitsPerJavaInteger - con);

  int in1_op = in(1)->Opcode();

  // Check for ((x>>>a)>>>b) and replace with (x>>>(a+b)) when a+b < 32
  if( in1_op == Op_URShiftI ) {
    const TypeInt *t12 = phase->type( in(1)->in(2) )->isa_int();
    if( t12 && t12->is_con() ) { // Right input is a constant
      assert( in(1) != in(1)->in(1), "dead loop in URShiftINode::Ideal" );
      const int con2 = t12->get_con() & 31; // Shift count is always masked
      const int con3 = con+con2;
      if( con3 < 32 )           // Only merge shifts if total is < 32
        return new URShiftINode( in(1)->in(1), phase->intcon(con3) );
    }
  }

  // Check for ((x << z) + Y) >>> z.  Replace with x + con>>>z
  // The idiom for rounding to a power of 2 is "(Q+(2^z-1)) >>> z".
  // If Q is "X << z" the rounding is useless.  Look for patterns like
  // ((X<<Z) + Y) >>> Z  and replace with (X + Y>>>Z) & Z-mask.
  Node *add = in(1);
  const TypeInt *t2 = phase->type(in(2))->isa_int();
  if (in1_op == Op_AddI) {
    Node *lshl = add->in(1);
    if( lshl->Opcode() == Op_LShiftI &&
        phase->type(lshl->in(2)) == t2 ) {
      Node *y_z = phase->transform( new URShiftINode(add->in(2),in(2)) );
      Node *sum = phase->transform( new AddINode( lshl->in(1), y_z ) );
      return new AndINode( sum, phase->intcon(mask) );
    }
  }

  // Check for (x & mask) >>> z.  Replace with (x >>> z) & (mask >>> z)
  // This shortens the mask.  Also, if we are extracting a high byte and
  // storing it to a buffer, the mask will be removed completely.
  Node *andi = in(1);
  if( in1_op == Op_AndI ) {
    const TypeInt *t3 = phase->type( andi->in(2) )->isa_int();
    if( t3 && t3->is_con() ) { // Right input is a constant
      jint mask2 = t3->get_con();
      mask2 >>= con;  // *signed* shift downward (high-order zeroes do not help)
      Node *newshr = phase->transform( new URShiftINode(andi->in(1), in(2)) );
      return new AndINode(newshr, phase->intcon(mask2));
      // The negative values are easier to materialize than positive ones.
      // A typical case from address arithmetic is ((x & ~15) >> 4).
      // It's better to change that to ((x >> 4) & ~0) versus
      // ((x >> 4) & 0x0FFFFFFF).  The difference is greatest in LP64.
    }
  }

  // Check for "(X << z ) >>> z" which simply zero-extends
  Node *shl = in(1);
  if( in1_op == Op_LShiftI &&
      phase->type(shl->in(2)) == t2 )
    return new AndINode( shl->in(1), phase->intcon(mask) );

  // Check for (x >> n) >>> 31. Replace with (x >>> 31)
  Node *shr = in(1);
  if ( in1_op == Op_RShiftI ) {
    Node *in11 = shr->in(1);
    Node *in12 = shr->in(2);
    const TypeInt *t11 = phase->type(in11)->isa_int();
    const TypeInt *t12 = phase->type(in12)->isa_int();
    if ( t11 && t2 && t2->is_con(31) && t12 && t12->is_con() ) {
      return new URShiftINode(in11, phase->intcon(31));
    }
  }

  return nullptr;
}

Node *URShiftLNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  int con = maskShiftAmount(phase, this, BitsPerJavaLong);
  if (con == 0) {
    return nullptr;
  }

  // We'll be wanting the right-shift amount as a mask of that many bits
  const jlong mask = jlong(max_julong >> con);

  // Check for ((x << z) + Y) >>> z.  Replace with x + con>>>z
  // The idiom for rounding to a power of 2 is "(Q+(2^z-1)) >>> z".
  // If Q is "X << z" the rounding is useless.  Look for patterns like
  // ((X<<Z) + Y) >>> Z  and replace with (X + Y>>>Z) & Z-mask.
  Node *add = in(1);
  const TypeInt *t2 = phase->type(in(2))->isa_int();
  if (add->Opcode() == Op_AddL) {
    Node *lshl = add->in(1);
    if( lshl->Opcode() == Op_LShiftL &&
        phase->type(lshl->in(2)) == t2 ) {
      Node *y_z = phase->transform( new URShiftLNode(add->in(2),in(2)) );
      Node *sum = phase->transform( new AddLNode( lshl->in(1), y_z ) );
      return new AndLNode( sum, phase->longcon(mask) );
    }
  }

  // Check for (x & mask) >>> z.  Replace with (x >>> z) & (mask >>> z)
  // This shortens the mask.  Also, if we are extracting a high byte and
  // storing it to a buffer, the mask will be removed completely.
  Node *andi = in(1);
  if( andi->Opcode() == Op_AndL ) {
    const TypeLong *t3 = phase->type( andi->in(2) )->isa_long();
    if( t3 && t3->is_con() ) { // Right input is a constant
      jlong mask2 = t3->get_con();
      mask2 >>= con;  // *signed* shift downward (high-order zeroes do not help)
      Node *newshr = phase->transform( new URShiftLNode(andi->in(1), in(2)) );
      return new AndLNode(newshr, phase->longcon(mask2));
    }
  }

  // Check for "(X << z ) >>> z" which simply zero-extends
  Node *shl = in(1);
  if( shl->Opcode() == Op_LShiftL &&
      phase->type(shl->in(2)) == t2 )
    return new AndLNode( shl->in(1), phase->longcon(mask) );

  // Check for (x >> n) >>> 63. Replace with (x >>> 63)
  Node *shr = in(1);
  if ( shr->Opcode() == Op_RShiftL ) {
    Node *in11 = shr->in(1);
    Node *in12 = shr->in(2);
    const TypeLong *t11 = phase->type(in11)->isa_long();
    const TypeInt *t12 = phase->type(in12)->isa_int();
    if ( t11 && t2 && t2->is_con(63) && t12 && t12->is_con() ) {
      return new URShiftLNode(in11, phase->intcon(63));
    }
  }
  return nullptr;
}

Node* URShiftINode::Identity(PhaseGVN* phase) {
  return rshift_id<TypeInt, false>(this, phase);
}

Node* URShiftLNode::Identity(PhaseGVN* phase) {
  return rshift_id<TypeLong, false>(this, phase);
}

template <class CT>
static const Type* urshift_value(const Node* in1, const Node* in2, PhaseGVN* phase) {
  using T = std::remove_const_t<decltype(CT::_lo)>;
  using U = std::remove_const_t<decltype(CT::_ulo)>;
  constexpr juint W = sizeof(U) * 8;
  const Type* t1 = phase->type(in1);
  const Type* t2 = phase->type(in2);
  if (t1 == Type::TOP || t2 == Type::TOP) {
    return Type::TOP;
  }

  const CT* i1 = CT::cast(t1);
  const TypeInt* i2 = TypeInt::cast(t2);
  i2 = and_mul_ring<TypeInt>(i2, TypeInt::make(W - 1))->is_int();

  T lo = (i1->_lo < 0 && i2->_ulo == 0) ? i1->_lo : (i1->_ulo >> i2->_uhi);
  T hi = (i2->_uhi == 0) ? i1->_hi : (i1->_uhi >> i2->_ulo);

  U ulo = i1->_ulo >> i2->_uhi;
  U uhi = i1->_uhi >> i2->_ulo;

  U zeros = std::numeric_limits<U>::max();
  U ones = zeros;
  for (juint s = i2->_ulo; s <= i2->_uhi; s++) {
    if (!i2->contains(s)) {
      continue;
    }

    U current_zeros = i1->_zeros;
    if (s > 0) {
      current_zeros = (i1->_zeros >> s) | (std::numeric_limits<U>::max() << (W - s));
    }
    zeros &= current_zeros;

    U current_ones = i1->_ones >> s;
    ones &= current_ones;
  }

  return CT::make(lo, hi, ulo, uhi, zeros, ones, MAX2(i1->_widen, i2->_widen));
}

const Type* URShiftINode::Value(PhaseGVN* phase) const {
  return urshift_value<TypeInt>(in(1), in(2), phase);
}

const Type* URShiftLNode::Value(PhaseGVN* phase) const {
  return urshift_value<TypeLong>(in(1), in(2), phase);
}

//=============================================================================
//------------------------------Ideal------------------------------------------
Node* FmaNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  // We canonicalize the node by converting "(-a)*b+c" into "b*(-a)+c"
  // This reduces the number of rules in the matcher, as we only need to check
  // for negations on the second argument, and not the symmetric case where
  // the first argument is negated.
  if (in(1)->is_Neg() && !in(2)->is_Neg()) {
    swap_edges(1, 2);
    return this;
  }
  return nullptr;
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type* FmaDNode::Value(PhaseGVN* phase) const {
  const Type *t1 = phase->type(in(1));
  if (t1 == Type::TOP) return Type::TOP;
  if (t1->base() != Type::DoubleCon) return Type::DOUBLE;
  const Type *t2 = phase->type(in(2));
  if (t2 == Type::TOP) return Type::TOP;
  if (t2->base() != Type::DoubleCon) return Type::DOUBLE;
  const Type *t3 = phase->type(in(3));
  if (t3 == Type::TOP) return Type::TOP;
  if (t3->base() != Type::DoubleCon) return Type::DOUBLE;
#ifndef __STDC_IEC_559__
  return Type::DOUBLE;
#else
  double d1 = t1->getd();
  double d2 = t2->getd();
  double d3 = t3->getd();
  return TypeD::make(fma(d1, d2, d3));
#endif
}

//=============================================================================
//------------------------------Value------------------------------------------
const Type* FmaFNode::Value(PhaseGVN* phase) const {
  const Type *t1 = phase->type(in(1));
  if (t1 == Type::TOP) return Type::TOP;
  if (t1->base() != Type::FloatCon) return Type::FLOAT;
  const Type *t2 = phase->type(in(2));
  if (t2 == Type::TOP) return Type::TOP;
  if (t2->base() != Type::FloatCon) return Type::FLOAT;
  const Type *t3 = phase->type(in(3));
  if (t3 == Type::TOP) return Type::TOP;
  if (t3->base() != Type::FloatCon) return Type::FLOAT;
#ifndef __STDC_IEC_559__
  return Type::FLOAT;
#else
  float f1 = t1->getf();
  float f2 = t2->getf();
  float f3 = t3->getf();
  return TypeF::make(fma(f1, f2, f3));
#endif
}

//=============================================================================
//------------------------------hash-------------------------------------------
// Hash function for MulAddS2INode.  Operation is commutative with commutative pairs.
// The hash function must return the same value when edge swapping is performed.
uint MulAddS2INode::hash() const {
  return (uintptr_t)in(1) + (uintptr_t)in(2) + (uintptr_t)in(3) + (uintptr_t)in(4) + Opcode();
}

//------------------------------Rotate Operations ------------------------------

Node* RotateLeftNode::Identity(PhaseGVN* phase) {
  const Type* t1 = phase->type(in(1));
  if (t1 == Type::TOP) {
    return this;
  }
  int count = 0;
  assert(t1->isa_int() || t1->isa_long(), "Unexpected type");
  int mask = (t1->isa_int() ? BitsPerJavaInteger : BitsPerJavaLong) - 1;
  if (const_shift_count(phase, this, &count) && (count & mask) == 0) {
    // Rotate by a multiple of 32/64 does nothing
    return in(1);
  }
  return this;
}

const Type* RotateLeftNode::Value(PhaseGVN* phase) const {
  const Type* t1 = phase->type(in(1));
  const Type* t2 = phase->type(in(2));
  // Either input is TOP ==> the result is TOP
  if (t1 == Type::TOP || t2 == Type::TOP) {
    return Type::TOP;
  }

  if (t1->isa_int()) {
    const TypeInt* r1 = t1->is_int();
    const TypeInt* r2 = t2->is_int();

    // Left input is ZERO ==> the result is ZERO.
    if (r1 == TypeInt::ZERO) {
      return TypeInt::ZERO;
    }
    // Rotate by zero does nothing
    if (r2 == TypeInt::ZERO) {
      return r1;
    }
    if (r1->is_con() && r2->is_con()) {
      juint r1_con = (juint)r1->get_con();
      juint shift = (juint)(r2->get_con()) & (juint)(BitsPerJavaInteger - 1); // semantics of Java shifts
      return TypeInt::make((r1_con << shift) | (r1_con >> (32 - shift)));
    }
    return TypeInt::INT;
  } else {
    assert(t1->isa_long(), "Type must be a long");
    const TypeLong* r1 = t1->is_long();
    const TypeInt*  r2 = t2->is_int();

    // Left input is ZERO ==> the result is ZERO.
    if (r1 == TypeLong::ZERO) {
      return TypeLong::ZERO;
    }
    // Rotate by zero does nothing
    if (r2 == TypeInt::ZERO) {
      return r1;
    }
    if (r1->is_con() && r2->is_con()) {
      julong r1_con = (julong)r1->get_con();
      julong shift = (julong)(r2->get_con()) & (julong)(BitsPerJavaLong - 1); // semantics of Java shifts
      return TypeLong::make((r1_con << shift) | (r1_con >> (64 - shift)));
    }
    return TypeLong::LONG;
  }
}

Node* RotateLeftNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  const Type* t1 = phase->type(in(1));
  const Type* t2 = phase->type(in(2));
  if (t2->isa_int() && t2->is_int()->is_con()) {
    if (t1->isa_int()) {
      int lshift = t2->is_int()->get_con() & 31;
      return new RotateRightNode(in(1), phase->intcon(32 - (lshift & 31)), TypeInt::INT);
    } else if (t1 != Type::TOP) {
      assert(t1->isa_long(), "Type must be a long");
      int lshift = t2->is_int()->get_con() & 63;
      return new RotateRightNode(in(1), phase->intcon(64 - (lshift & 63)), TypeLong::LONG);
    }
  }
  return nullptr;
}

Node* RotateRightNode::Identity(PhaseGVN* phase) {
  const Type* t1 = phase->type(in(1));
  if (t1 == Type::TOP) {
    return this;
  }
  int count = 0;
  assert(t1->isa_int() || t1->isa_long(), "Unexpected type");
  int mask = (t1->isa_int() ? BitsPerJavaInteger : BitsPerJavaLong) - 1;
  if (const_shift_count(phase, this, &count) && (count & mask) == 0) {
    // Rotate by a multiple of 32/64 does nothing
    return in(1);
  }
  return this;
}

const Type* RotateRightNode::Value(PhaseGVN* phase) const {
  const Type* t1 = phase->type(in(1));
  const Type* t2 = phase->type(in(2));
  // Either input is TOP ==> the result is TOP
  if (t1 == Type::TOP || t2 == Type::TOP) {
    return Type::TOP;
  }

  if (t1->isa_int()) {
    const TypeInt* r1 = t1->is_int();
    const TypeInt* r2 = t2->is_int();

    // Left input is ZERO ==> the result is ZERO.
    if (r1 == TypeInt::ZERO) {
      return TypeInt::ZERO;
    }
    // Rotate by zero does nothing
    if (r2 == TypeInt::ZERO) {
      return r1;
    }
    if (r1->is_con() && r2->is_con()) {
      juint r1_con = (juint)r1->get_con();
      juint shift = (juint)(r2->get_con()) & (juint)(BitsPerJavaInteger - 1); // semantics of Java shifts
      return TypeInt::make((r1_con >> shift) | (r1_con << (32 - shift)));
    }
    return TypeInt::INT;
  } else {
    assert(t1->isa_long(), "Type must be a long");
    const TypeLong* r1 = t1->is_long();
    const TypeInt*  r2 = t2->is_int();
    // Left input is ZERO ==> the result is ZERO.
    if (r1 == TypeLong::ZERO) {
      return TypeLong::ZERO;
    }
    // Rotate by zero does nothing
    if (r2 == TypeInt::ZERO) {
      return r1;
    }
    if (r1->is_con() && r2->is_con()) {
      julong r1_con = (julong)r1->get_con();
      julong shift = (julong)(r2->get_con()) & (julong)(BitsPerJavaLong - 1); // semantics of Java shifts
      return TypeLong::make((r1_con >> shift) | (r1_con << (64 - shift)));
    }
    return TypeLong::LONG;
  }
}

// Given an expression (AndX shift mask) or (AndX mask shift),
// determine if the AndX must always produce zero, because the
// the shift (x<<N) is bitwise disjoint from the mask #M.
// The X in AndX must be I or L, depending on bt.
// Specifically, the following cases fold to zero,
// when the shift value N is large enough to zero out
// all the set positions of the and-mask M.
//   (AndI (LShiftI _ #N) #M) => #0
//   (AndL (LShiftL _ #N) #M) => #0
//   (AndL (ConvI2L (LShiftI _ #N)) #M) => #0
// The M and N values must satisfy ((-1 << N) & M) == 0.
// Because the optimization might work for a non-constant
// mask M, we check the AndX for both operand orders.
bool MulNode::AndIL_shift_and_mask_is_always_zero(PhaseGVN* phase, Node* shift, Node* mask, BasicType bt, bool check_reverse) {
  if (mask == nullptr || shift == nullptr) {
    return false;
  }
  const TypeInteger* mask_t = phase->type(mask)->isa_integer(bt);
  if (mask_t == nullptr || phase->type(shift)->isa_integer(bt) == nullptr) {
    return false;
  }
  shift = shift->uncast();
  if (shift == nullptr) {
    return false;
  }
  if (phase->type(shift)->isa_integer(bt) == nullptr) {
    return false;
  }
  BasicType shift_bt = bt;
  if (bt == T_LONG && shift->Opcode() == Op_ConvI2L) {
    bt = T_INT;
    Node* val = shift->in(1);
    if (val == nullptr) {
      return false;
    }
    val = val->uncast();
    if (val == nullptr) {
      return false;
    }
    if (val->Opcode() == Op_LShiftI) {
      shift_bt = T_INT;
      shift = val;
      if (phase->type(shift)->isa_integer(bt) == nullptr) {
        return false;
      }
    }
  }
  if (shift->Opcode() != Op_LShift(shift_bt)) {
    if (check_reverse &&
        (mask->Opcode() == Op_LShift(bt) ||
         (bt == T_LONG && mask->Opcode() == Op_ConvI2L))) {
      // try it the other way around
      return AndIL_shift_and_mask_is_always_zero(phase, mask, shift, bt, false);
    }
    return false;
  }
  Node* shift2 = shift->in(2);
  if (shift2 == nullptr) {
    return false;
  }
  const Type* shift2_t = phase->type(shift2);
  if (!shift2_t->isa_int() || !shift2_t->is_int()->is_con()) {
    return false;
  }

  jint shift_con = shift2_t->is_int()->get_con() & ((shift_bt == T_INT ? BitsPerJavaInteger : BitsPerJavaLong) - 1);
  if ((((jlong)1) << shift_con) > mask_t->hi_as_long() && mask_t->lo_as_long() >= 0) {
    return true;
  }

  return false;
}

// Given an expression (AndX (AddX v1 (LShiftX v2 #N)) #M)
// determine if the AndX must always produce (AndX v1 #M),
// because the shift (v2<<N) is bitwise disjoint from the mask #M.
// The X in AndX will be I or L, depending on bt.
// Specifically, the following cases fold,
// when the shift value N is large enough to zero out
// all the set positions of the and-mask M.
//   (AndI (AddI v1 (LShiftI _ #N)) #M) => (AndI v1 #M)
//   (AndL (AddI v1 (LShiftL _ #N)) #M) => (AndL v1 #M)
//   (AndL (AddL v1 (ConvI2L (LShiftI _ #N))) #M) => (AndL v1 #M)
// The M and N values must satisfy ((-1 << N) & M) == 0.
// Because the optimization might work for a non-constant
// mask M, and because the AddX operands can come in either
// order, we check for every operand order.
Node* MulNode::AndIL_add_shift_and_mask(PhaseGVN* phase, BasicType bt) {
  Node* add = in(1);
  Node* mask = in(2);
  if (add == nullptr || mask == nullptr) {
    return nullptr;
  }
  int addidx = 0;
  if (add->Opcode() == Op_Add(bt)) {
    addidx = 1;
  } else if (mask->Opcode() == Op_Add(bt)) {
    mask = add;
    addidx = 2;
    add = in(addidx);
  }
  if (addidx > 0) {
    Node* add1 = add->in(1);
    Node* add2 = add->in(2);
    if (add1 != nullptr && add2 != nullptr) {
      if (AndIL_shift_and_mask_is_always_zero(phase, add1, mask, bt, false)) {
        set_req_X(addidx, add2, phase);
        return this;
      } else if (AndIL_shift_and_mask_is_always_zero(phase, add2, mask, bt, false)) {
        set_req_X(addidx, add1, phase);
        return this;
      }
    }
  }
  return nullptr;
}
