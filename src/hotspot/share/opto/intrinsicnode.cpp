/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "opto/addnode.hpp"
#include "opto/intrinsicnode.hpp"
#include "opto/memnode.hpp"
#include "opto/mulnode.hpp"
#include "opto/phaseX.hpp"
#include "utilities/count_leading_zeros.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/population_count.hpp"

//=============================================================================
// Do not match memory edge.
uint StrIntrinsicNode::match_edge(uint idx) const {
  return idx == 2 || idx == 3;
}

//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.  Strip out
// control copies
Node* StrIntrinsicNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  if (remove_dead_region(phase, can_reshape)) return this;
  // Don't bother trying to transform a dead node
  if (in(0) && in(0)->is_top())  return nullptr;

  if (can_reshape) {
    Node* mem = phase->transform(in(MemNode::Memory));
    // If transformed to a MergeMem, get the desired slice
    uint alias_idx = phase->C->get_alias_index(adr_type());
    mem = mem->is_MergeMem() ? mem->as_MergeMem()->memory_at(alias_idx) : mem;
    if (mem != in(MemNode::Memory)) {
      set_req_X(MemNode::Memory, mem, phase);
      return this;
    }
  }
  return nullptr;
}

//------------------------------Value------------------------------------------
const Type* StrIntrinsicNode::Value(PhaseGVN* phase) const {
  if (in(0) && phase->type(in(0)) == Type::TOP) return Type::TOP;
  return bottom_type();
}

uint StrIntrinsicNode::size_of() const { return sizeof(*this); }

//=============================================================================
//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.  Strip out
// control copies
Node* StrCompressedCopyNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  return remove_dead_region(phase, can_reshape) ? this : nullptr;
}

//=============================================================================
//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.  Strip out
// control copies
Node* StrInflatedCopyNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  return remove_dead_region(phase, can_reshape) ? this : nullptr;
}

uint VectorizedHashCodeNode::match_edge(uint idx) const {
  // Do not match memory edge.
  return idx >= 2 && idx <=  5; // VectorizedHashCodeNode (Binary ary1 cnt1) (Binary result bt)
}

Node* VectorizedHashCodeNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  return remove_dead_region(phase, can_reshape) ? this : nullptr;
}

const Type* VectorizedHashCodeNode::Value(PhaseGVN* phase) const {
  if (in(0) && phase->type(in(0)) == Type::TOP) return Type::TOP;
  return bottom_type();
}


//=============================================================================
//------------------------------match_edge-------------------------------------
// Do not match memory edge
uint EncodeISOArrayNode::match_edge(uint idx) const {
  return idx == 2 || idx == 3; // EncodeISOArray src (Binary dst len)
}

//------------------------------Ideal------------------------------------------
// Return a node which is more "ideal" than the current node.  Strip out
// control copies
Node* EncodeISOArrayNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  return remove_dead_region(phase, can_reshape) ? this : nullptr;
}

//------------------------------Value------------------------------------------
const Type* EncodeISOArrayNode::Value(PhaseGVN* phase) const {
  if (in(0) && phase->type(in(0)) == Type::TOP) return Type::TOP;
  return bottom_type();
}

//------------------------------CopySign-----------------------------------------
CopySignDNode* CopySignDNode::make(PhaseGVN& gvn, Node* in1, Node* in2) {
  return new CopySignDNode(in1, in2, gvn.makecon(TypeD::ZERO));
}

//------------------------------Signum-------------------------------------------
SignumDNode* SignumDNode::make(PhaseGVN& gvn, Node* in) {
  return new SignumDNode(in, gvn.makecon(TypeD::ZERO), gvn.makecon(TypeD::ONE));
}

SignumFNode* SignumFNode::make(PhaseGVN& gvn, Node* in) {
  return new SignumFNode(in, gvn.makecon(TypeF::ZERO), gvn.makecon(TypeF::ONE));
}

Node* CompressBitsNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  Node* src = in(1);
  Node* mask = in(2);
  if (bottom_type()->isa_int()) {
    if (mask->Opcode() == Op_LShiftI && phase->type(mask->in(1))->isa_int() && phase->type(mask->in(1))->is_int()->is_con()) {
      // compress(x, 1 << n) == (x >> n & 1)
      if (phase->type(mask->in(1))->higher_equal(TypeInt::ONE)) {
        Node* rshift = phase->transform(new RShiftINode(in(1), mask->in(2)));
        return new AndINode(rshift, phase->makecon(TypeInt::ONE));
      // compress(x, -1 << n) == x >>> n
      } else if (phase->type(mask->in(1))->higher_equal(TypeInt::MINUS_1)) {
        return new URShiftINode(in(1), mask->in(2));
      }
    }
    // compress(expand(x, m), m) == x & compress(m, m)
    if (src->Opcode() == Op_ExpandBits &&
        src->in(2) == mask) {
      Node* compr = phase->transform(new CompressBitsNode(mask, mask, TypeInt::INT));
      return new AndINode(compr, src->in(1));
    }
  } else {
    assert(bottom_type()->isa_long(), "");
    if (mask->Opcode() == Op_LShiftL && phase->type(mask->in(1))->isa_long() && phase->type(mask->in(1))->is_long()->is_con()) {
      // compress(x, 1 << n) == (x >> n & 1)
      if (phase->type(mask->in(1))->higher_equal(TypeLong::ONE)) {
        Node* rshift = phase->transform(new RShiftLNode(in(1), mask->in(2)));
        return new AndLNode(rshift, phase->makecon(TypeLong::ONE));
      // compress(x, -1 << n) == x >>> n
      } else if (phase->type(mask->in(1))->higher_equal(TypeLong::MINUS_1)) {
        return new URShiftLNode(in(1), mask->in(2));
      }
    }
    // compress(expand(x, m), m) == x & compress(m, m)
    if (src->Opcode() == Op_ExpandBits &&
        src->in(2) == mask) {
      Node* compr = phase->transform(new CompressBitsNode(mask, mask, TypeLong::LONG));
      return new AndLNode(compr, src->in(1));
    }
  }
  return nullptr;
}

static Node* compress_expand_identity(PhaseGVN* phase, Node* n) {
  BasicType bt = n->bottom_type()->basic_type();
  // compress(x, 0) == 0, expand(x, 0) == 0
  if(phase->type(n->in(2))->higher_equal(TypeInteger::zero(bt))) return n->in(2);
  // compress(x, -1) == x, expand(x, -1) == x
  if(phase->type(n->in(2))->higher_equal(TypeInteger::minus_1(bt))) return n->in(1);
  // expand(-1, x) == x
  if(n->Opcode() == Op_ExpandBits &&
     phase->type(n->in(1))->higher_equal(TypeInteger::minus_1(bt))) return n->in(2);
  return n;
}

Node* CompressBitsNode::Identity(PhaseGVN* phase) {
  return compress_expand_identity(phase, this);
}

Node* ExpandBitsNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  Node* src = in(1);
  Node* mask = in(2);
  if (bottom_type()->isa_int()) {
    if (mask->Opcode() == Op_LShiftI && phase->type(mask->in(1))->isa_int() && phase->type(mask->in(1))->is_int()->is_con()) {
      // expand(x, 1 << n) == (x & 1) << n
      if (phase->type(mask->in(1))->higher_equal(TypeInt::ONE)) {
        Node* andnode = phase->transform(new AndINode(in(1), phase->makecon(TypeInt::ONE)));
        return new LShiftINode(andnode, mask->in(2));
      // expand(x, -1 << n) == x << n
      } else if (phase->type(mask->in(1))->higher_equal(TypeInt::MINUS_1)) {
        return new LShiftINode(in(1), mask->in(2));
      }
    }
    // expand(compress(x, m), m) == x & m
    if (src->Opcode() == Op_CompressBits &&
        src->in(2) == mask) {
      return new AndINode(src->in(1), mask);
    }
  } else {
    assert(bottom_type()->isa_long(), "");
    if (mask->Opcode() == Op_LShiftL && phase->type(mask->in(1))->isa_long() && phase->type(mask->in(1))->is_long()->is_con()) {
      // expand(x, 1 << n) == (x & 1) << n
      if (phase->type(mask->in(1))->higher_equal(TypeLong::ONE)) {
        Node* andnode = phase->transform(new AndLNode(in(1), phase->makecon(TypeLong::ONE)));
        return new LShiftLNode(andnode, mask->in(2));
      // expand(x, -1 << n) == x << n
      } else if (phase->type(mask->in(1))->higher_equal(TypeLong::MINUS_1)) {
        return new LShiftLNode(in(1), mask->in(2));
      }
    }
    // expand(compress(x, m), m) == x & m
    if (src->Opcode() == Op_CompressBits &&
        src->in(2) == mask) {
      return new AndLNode(src->in(1), mask);
    }
  }
  return nullptr;
}

Node* ExpandBitsNode::Identity(PhaseGVN* phase) {
  return compress_expand_identity(phase, this);
}

static const Type* bitshuffle_value(const TypeInteger* src_type, const TypeInteger* mask_type, int opc, BasicType bt) {

  jlong hi = bt == T_INT ? max_jint : max_jlong;
  jlong lo = bt == T_INT ? min_jint : min_jlong;
  assert(bt == T_INT || bt == T_LONG, "");

  // Rule 1: Bit compression selects the source bits corresponding to true mask bits,
  // packs them and places them contiguously at destination bit positions
  // starting from least significant bit, remaining higher order bits are set
  // to zero.

  // Rule 2: Bit expansion is a reverse process, which sequentially reads source bits
  // starting from LSB and places them at bit positions in result value where
  // corresponding mask bits are 1. Thus, bit expansion for non-negative mask
  // value will always generate a +ve value, this is because sign bit of result
  // will never be set to 1 as corresponding mask bit is always 0.

  // Case A) Constant mask
  if (mask_type->is_con()) {
    jlong maskcon = mask_type->get_con_as_long(bt);
    if (opc == Op_CompressBits) {
      // Case A.1 bit compression:-
      // For an outlier mask value of -1 upper bound of the result equals
      // maximum integral value, for any other mask value its computed using
      // following formula
      //       Result.Hi = 1 << popcount(mask_bits) - 1
      //
      // For mask values other than -1, lower bound of the result is estimated
      // as zero, by assuming at least one mask bit is zero and corresponding source
      // bit will be masked, hence result of bit compression will always be
      // non-negative value. For outlier mask value of -1, assume all source bits
      // apart from most significant bit were set to 0, thereby resulting in
      // a minimum integral value.
      // e.g.
      //  src = 0xXXXXXXXX (non-constant source)
      //  mask = 0xEFFFFFFF (constant mask)
      //  result.hi = 0x7FFFFFFF
      //  result.lo = 0
      if (maskcon != -1L) {
        int bitcount = population_count(static_cast<julong>(bt == T_INT ? maskcon & 0xFFFFFFFFL : maskcon));
        hi = (1UL << bitcount) - 1;
        lo = 0L;
      } else {
        // preserve originally assigned hi (MAX_INT/LONG) and lo (MIN_INT/LONG) values
        // for unknown source bits.
        assert(hi == (bt == T_INT ? max_jint : max_jlong), "");
        assert(lo == (bt == T_INT ? min_jint : min_jlong), "");
      }
    } else {
      // Case A.2 bit expansion:-
      assert(opc == Op_ExpandBits, "");
      if (maskcon >= 0L) {
        //   Case A.2.1 constant mask >= 0
        //     Result.Hi = mask, optimistically assuming all source bits
        //     read starting from least significant bit positions are 1.
        //     Result.Lo = 0, because at least one bit in mask is zero.
        //   e.g.
        //    src = 0xXXXXXXXX (non-constant source)
        //    mask = 0x7FFFFFFF (constant mask >= 0)
        //    result.hi = 0x7FFFFFFF
        //    result.lo = 0
        hi = maskcon;
        lo = 0L;
      } else {
        //   Case A.2.2) mask < 0
        //     For constant mask strictly less than zero, the maximum result value will be
        //     the same as the mask value with its sign bit flipped, assuming all source bits
        //     except the MSB bit are set(one).
        //
        //     To compute minimum result value we assume all but last read source bit as zero,
        //     this is because sign bit of result will always be set to 1 while other bit
        //     corresponding to set mask bit should be zero.
        //   e.g.
        //    src = 0xXXXXXXXX (non-constant source)
        //    mask = 0xEFFFFFFF (constant mask)
        //    result.hi = 0xEFFFFFFF ^ 0x80000000 = 0x6FFFFFFF
        //    result.lo = 0x80000000
        //
        hi = maskcon ^ lo;
        // lo still retains MIN_INT/LONG.
        assert(lo == (bt == T_INT ? min_jint : min_jlong), "");
      }
    }
  }

  // Case B) Non-constant mask.
  if (!mask_type->is_con()) {
    if ( opc == Op_CompressBits) {
      int result_bit_width;
      int mask_bit_width = bt == T_INT ? 32 : 64;
      if ((mask_type->lo_as_long() < 0L && mask_type->hi_as_long() >= -1L)) {
        // Case B.1 The mask value range includes -1, hence we may use all bits,
        //          the result has the whole value range.
        result_bit_width = mask_bit_width;
      } else if (mask_type->hi_as_long() < -1L) {
        // Case B.2 Mask value range is strictly less than -1, this indicates presence of at least
        // one unset(zero) bit in mask value, thus as per Rule 1, bit compression will always
        // result in a non-negative value. This guarantees that MSB bit of result value will
        // always be set to zero.
        result_bit_width = mask_bit_width - 1;
      } else {
        assert(mask_type->lo_as_long() >= 0, "");
        // Case B.3 Mask value range only includes non-negative values. Since all integral
        // types honours an invariant that TypeInteger._lo <= TypeInteger._hi, thus computing
        // leading zero bits of upper bound of mask value will allow us to ascertain
        // optimistic upper bound of result i.e. all the bits other than leading zero bits
        // can be assumed holding 1 value.
        jlong clz = count_leading_zeros(mask_type->hi_as_long());
        // Here, result of clz is w.r.t to long argument, hence for integer argument
        // we explicitly subtract 32 from the result.
        clz = bt == T_INT ? clz - 32 : clz;
        result_bit_width = mask_bit_width - clz;
      }
      // If the number of bits required to for the mask value range is less than the
      // full bit width of the integral type, then the MSB bit is guaranteed to be zero,
      // thus the compression result will never be a -ve value and we can safely set the
      // lower bound of the bit compression to zero.
      lo = result_bit_width == mask_bit_width ? lo : 0L;

      assert(hi == (bt == T_INT ? max_jint : max_jlong), "");
      assert(lo == (bt == T_INT ? min_jint : min_jlong) || lo == 0, "");

      if (src_type->lo_as_long() >= 0) {
        // Lemma 1: For strictly non-negative src, the result of the compression will never be
        // greater than src.
        // Proof: Since src is a non-negative value, its most significant bit is always 0.
        // Thus even if the corresponding MSB of the mask is one, the result will be a +ve
        // value. There are three possible cases
        //   a. All the mask bits corresponding to set source bits are unset(zero).
        //   b. All the mask bits corresponding to set source bits are set(one)
        //   c. Some mask bits corresponding to set source bits are set(one) while others are unset(zero)
        //
        // Case a. results into an allzero result, while Case b. gives us the upper bound which is equals source
        // value, while for Case c. the result will lie within [0, src]
        //
        hi = src_type->hi_as_long();
        lo = 0L;
      }

      if (result_bit_width < mask_bit_width) {
        // Rule 3:
        // We can further constrain the upper bound of bit compression if the number of bits
        // which can be set(one) is less than the maximum number of bits of integral type.
        hi = MIN2((jlong)((1UL << result_bit_width) - 1L), hi);
      }
    } else {
      assert(opc == Op_ExpandBits, "");
      jlong max_mask = mask_type->hi_as_long();
      jlong min_mask = mask_type->lo_as_long();
      // Since mask here a range and not a constant value, hence being
      // conservative in determining the value range of result.
      if (min_mask >= 0L) {
        // Lemma 2: Based on the integral type invariant ie. TypeInteger.lo <= TypeInteger.hi,
        // if the lower bound of non-constant mask is a non-negative value then result can never
        // be greater than the mask.
        // Proof: Since lower bound of the mask is a non-negative value, hence most significant
        // bit of its entire value must be unset(zero). If all the lower order 'n' source bits
        // where n corresponds to popcount of mask are set(ones) then upper bound of the result equals
        // mask. In order to compute the lower bound, we pssimistically assume all the lower order 'n'
        // source bits are unset(zero) there by resuling into a zero value.
        hi = max_mask;
        lo = 0;
      } else {
        // preserve the lo and hi bounds estimated till now.
      }
    }
  }

  return bt == T_INT ? static_cast<const Type*>(TypeInt::make(lo, hi, Type::WidenMax)) :
                       static_cast<const Type*>(TypeLong::make(lo, hi, Type::WidenMax));
}

jlong CompressBitsNode::compress_bits(jlong src, jlong mask, int bit_count) {
  jlong res = 0;
  for (int i = 0, j = 0; i < bit_count; i++) {
    if(mask & 0x1) {
      res |= (src & 0x1) << j++;
    }
    src >>= 1;
    mask >>= 1;
  }
  return res;
}

const Type* CompressBitsNode::Value(PhaseGVN* phase) const {
  const Type* t1 = phase->type(in(1));
  const Type* t2 = phase->type(in(2));
  if (t1 == Type::TOP || t2 == Type::TOP) {
    return Type::TOP;
  }

  BasicType bt = bottom_type()->basic_type();
  const TypeInteger* src_type = t1->is_integer(bt);
  const TypeInteger* mask_type = t2->is_integer(bt);
  int w = bt == T_INT ? 32 : 64;

  // Constant fold if both src and mask are constants.
  if (src_type->is_con() && mask_type->is_con()) {
    jlong src = src_type->get_con_as_long(bt);
    jlong mask = mask_type->get_con_as_long(bt);
    jlong res = compress_bits(src, mask, w);
    return bt == T_INT ? static_cast<const Type*>(TypeInt::make(res)) :
                         static_cast<const Type*>(TypeLong::make(res));
  }

  // Result is zero if src is zero irrespective of mask value.
  if (src_type == TypeInteger::zero(bt)) {
     return TypeInteger::zero(bt);
  }

  return bitshuffle_value(src_type, mask_type, Op_CompressBits, bt);
}

jlong ExpandBitsNode::expand_bits(jlong src, jlong mask, int bit_count) {
  jlong res = 0;
  for (int i = 0; i < bit_count; i++) {
    if(mask & 0x1) {
      res |= (src & 0x1) << i;
      src >>= 1;
    }
    mask >>= 1;
  }
  return res;
}

const Type* ExpandBitsNode::Value(PhaseGVN* phase) const {
  const Type* t1 = phase->type(in(1));
  const Type* t2 = phase->type(in(2));
  if (t1 == Type::TOP || t2 == Type::TOP) {
    return Type::TOP;
  }

  BasicType bt = bottom_type()->basic_type();
  const TypeInteger* src_type = t1->is_integer(bt);
  const TypeInteger* mask_type = t2->is_integer(bt);
  int w = bt == T_INT ? 32 : 64;

  // Constant fold if both src and mask are constants.
  if (src_type->is_con() && mask_type->is_con()) {
     jlong src = src_type->get_con_as_long(bt);
     jlong mask = mask_type->get_con_as_long(bt);
     jlong res = expand_bits(src, mask, w);
     return bt == T_INT ? static_cast<const Type*>(TypeInt::make(res)) :
                          static_cast<const Type*>(TypeLong::make(res));
  }

  // Result is zero if src is zero irrespective of mask value.
  if (src_type == TypeInteger::zero(bt)) {
     return TypeInteger::zero(bt);
  }

  return bitshuffle_value(src_type, mask_type, Op_ExpandBits, bt);
}
