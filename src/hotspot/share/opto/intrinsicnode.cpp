/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/intrinsicnode.hpp"
#include "opto/addnode.hpp"
#include "opto/mulnode.hpp"
#include "opto/memnode.hpp"
#include "opto/phaseX.hpp"
#include "utilities/population_count.hpp"
#include "utilities/count_leading_zeros.hpp"
#include "utilities/globalDefinitions.hpp"

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
    if (mask->Opcode() == Op_LShiftI && phase->type(mask->in(1))->is_int()->is_con()) {
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
    if (mask->Opcode() == Op_LShiftL && phase->type(mask->in(1))->is_long()->is_con()) {
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
    if (mask->Opcode() == Op_LShiftI && phase->type(mask->in(1))->is_int()->is_con()) {
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
    if (mask->Opcode() == Op_LShiftL && phase->type(mask->in(1))->is_long()->is_con()) {
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

  if(mask_type->is_con() && mask_type->get_con_as_long(bt) != -1L) {
    jlong maskcon = mask_type->get_con_as_long(bt);
    int bitcount = population_count(static_cast<julong>(bt == T_INT ? maskcon & 0xFFFFFFFFL : maskcon));
    if (opc == Op_CompressBits) {
      // Bit compression selects the source bits corresponding to true mask bits
      // and lays them out contiguously at destination bit positions starting from
      // LSB, remaining higher order bits are set to zero.
      // Thus, it will always generate a +ve value i.e. sign bit set to 0 if
      // any bit of constant mask value is zero.
      lo = 0L;
      hi = (1UL << bitcount) - 1;
    } else {
      assert(opc == Op_ExpandBits, "");
      // Expansion sequentially reads source bits starting from LSB
      // and places them over destination at bit positions corresponding
      // set mask bit. Thus bit expansion for non-negative mask value
      // will always generate a +ve value.
      hi = maskcon >= 0L ? maskcon : maskcon ^ lo;
      lo = maskcon >= 0L ? 0L : lo;
    }
  }

  if (!mask_type->is_con()) {
    int mask_max_bw;
    int max_bw = bt == T_INT ? 32 : 64;
    // Case 1) Mask value range includes -1.
    if ((mask_type->lo_as_long() < 0L && mask_type->hi_as_long() >= -1L)) {
      mask_max_bw = max_bw;
    // Case 2) Mask value range is less than -1.
    } else if (mask_type->hi_as_long() < -1L) {
      mask_max_bw = max_bw - 1;
    } else {
    // Case 3) Mask value range only includes +ve values.
      assert(mask_type->lo_as_long() >= 0, "");
      jlong clz = count_leading_zeros(mask_type->hi_as_long());
      clz = bt == T_INT ? clz - 32 : clz;
      mask_max_bw = max_bw - clz;
    }
    if ( opc == Op_CompressBits) {
      lo = mask_max_bw == max_bw ? lo : 0L;
      // Compress operation is inherently an unsigned operation and
      // result value range is primarily dependent on true count
      // of participating mask value.
      hi = mask_max_bw < max_bw ? (1L << mask_max_bw) - 1 : src_type->hi_as_long();
    } else {
      assert(opc == Op_ExpandBits, "");
      jlong max_mask = mask_type->hi_as_long();
      // Since mask here a range and not a constant value, hence being
      // conservative in determining the value range of result.
      lo = mask_type->lo_as_long() >= 0L ? 0L : lo;
      hi = mask_type->lo_as_long() >= 0L ? max_mask : hi;
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

  return bitshuffle_value(src_type, mask_type, Op_ExpandBits, bt);
}
