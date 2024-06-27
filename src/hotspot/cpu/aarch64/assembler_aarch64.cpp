/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2020 Red Hat Inc. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "asm/macroAssembler.hpp"
#include "compiler/disassembler.hpp"
#include "immediate_aarch64.hpp"
#include "memory/resourceArea.hpp"
#include "metaprogramming/primitiveConversions.hpp"

#ifndef PRODUCT
const uintptr_t Assembler::asm_bp = 0x0000ffffac221240;
#endif

static float unpack(unsigned value);

short Assembler::SIMD_Size_in_bytes[] = {
  // T8B, T16B, T4H, T8H, T2S, T4S, T1D, T2D, T1Q
       8,   16,   8,  16,   8,  16,   8,  16,  16
};

Assembler::SIMD_Arrangement Assembler::_esize2arrangement_table[9][2] = {
  // esize        isQ:false             isQ:true
  /*   0  */      {INVALID_ARRANGEMENT, INVALID_ARRANGEMENT},
  /*   1  */      {T8B,                 T16B},
  /*   2  */      {T4H,                 T8H},
  /*   3  */      {INVALID_ARRANGEMENT, INVALID_ARRANGEMENT},
  /*   4  */      {T2S,                 T4S},
  /*   5  */      {INVALID_ARRANGEMENT, INVALID_ARRANGEMENT},
  /*   6  */      {INVALID_ARRANGEMENT, INVALID_ARRANGEMENT},
  /*   7  */      {INVALID_ARRANGEMENT, INVALID_ARRANGEMENT},
  /*   8  */      {T1D,                 T2D}
  };

Assembler::SIMD_RegVariant Assembler::_esize2regvariant[9] = {
  INVALID,
  B,
  H,
  INVALID,
  S,
  INVALID,
  INVALID,
  INVALID,
  D,
};

Assembler::SIMD_Arrangement Assembler::esize2arrangement(unsigned esize, bool isQ) {
  guarantee(esize < ARRAY_SIZE(_esize2arrangement_table) &&
         _esize2arrangement_table[esize][isQ] != INVALID_ARRANGEMENT, "unsupported element size");
  return _esize2arrangement_table[esize][isQ];
}

Assembler::SIMD_RegVariant Assembler::elemBytes_to_regVariant(unsigned esize) {
  guarantee(esize < ARRAY_SIZE(_esize2regvariant) && _esize2regvariant[esize] != INVALID,
         "unsupported element size");
  return _esize2regvariant[esize];
}

Assembler::SIMD_RegVariant Assembler::elemType_to_regVariant(BasicType bt) {
  return elemBytes_to_regVariant(type2aelembytes(bt));
}

unsigned Assembler::regVariant_to_elemBits(Assembler::SIMD_RegVariant T){
  guarantee(T != Q, "Invalid register variant");
  return 1 << (T + 3);
}

void Assembler::emit_data64(jlong data,
                            relocInfo::relocType rtype,
                            int format) {
  if (rtype == relocInfo::none) {
    emit_int64(data);
  } else {
    emit_data64(data, Relocation::spec_simple(rtype), format);
  }
}

void Assembler::emit_data64(jlong data,
                            RelocationHolder const& rspec,
                            int format) {

  assert(inst_mark() != nullptr, "must be inside InstructionMark");
  // Do not use AbstractAssembler::relocate, which is not intended for
  // embedded words.  Instead, relocate to the enclosing instruction.
  code_section()->relocate(inst_mark(), rspec, format);
  emit_int64(data);
}

extern "C" {
  void das(uint64_t start, int len) {
    ResourceMark rm;
    len <<= 2;
    if (len < 0)
      Disassembler::decode((address)start + len, (address)start);
    else
      Disassembler::decode((address)start, (address)start + len);
  }
}

#define __ as->

void Address::lea(MacroAssembler *as, Register r) const {
  switch(_mode) {
  case base_plus_offset: {
    if (offset() == 0 && base() == r) // it's a nop
      break;
    if (offset() > 0)
      __ add(r, base(), offset());
    else
      __ sub(r, base(), -offset());
    break;
  }
  case base_plus_offset_reg: {
    __ add(r, base(), index(), ext().op(), MAX2(ext().shift(), 0));
    break;
  }
  case literal: {
    as->code_section()->relocate(as->inst_mark(), rspec());
    if (rspec().type() == relocInfo::none)
      __ mov(r, target());
    else
      __ movptr(r, (uint64_t)target());
    break;
  }
  default:
    ShouldNotReachHere();
  }
}

#undef __

#define starti Instruction_aarch64 current_insn(this);

#define f current_insn.f
#define sf current_insn.sf
#define rf current_insn.rf
#define srf current_insn.srf
#define zrf current_insn.zrf
#define prf current_insn.prf
#define pgrf current_insn.pgrf
#define fixed current_insn.fixed

  void Assembler::adr(Register Rd, address adr) {
    intptr_t offset = adr - pc();
    int offset_lo = offset & 3;
    offset >>= 2;
    starti;
    f(0, 31), f(offset_lo, 30, 29), f(0b10000, 28, 24), sf(offset, 23, 5);
    rf(Rd, 0);
  }

  void Assembler::_adrp(Register Rd, address adr) {
    uint64_t pc_page = (uint64_t)pc() >> 12;
    uint64_t adr_page = (uint64_t)adr >> 12;
    intptr_t offset = adr_page - pc_page;
    int offset_lo = offset & 3;
    offset >>= 2;
    starti;
    f(1, 31), f(offset_lo, 30, 29), f(0b10000, 28, 24), sf(offset, 23, 5);
    zrf(Rd, 0);
  }

// This encoding is similar (but not quite identical) to the encoding used
// by literal ld/st. see JDK-8324123.
// PRFM does not support writeback or pre/post index.
void Assembler::prfm(const Address &adr, prfop pfop) {
  Address::mode mode = adr.getMode();
  // PRFM does not support pre/post index
  guarantee((mode != Address::pre) && (mode != Address::post), "prfm does not support pre/post indexing");
  if (mode == Address::literal) {
    starti;
    f(0b11, 31, 30), f(0b011, 29, 27), f(0b000, 26, 24);
    f(pfop, 4, 0);
    int64_t offset = (adr.target() - pc()) >> 2;
    sf(offset, 23, 5);
  } else {
    assert((mode == Address::base_plus_offset)
            || (mode == Address::base_plus_offset_reg), "must be base_plus_offset/base_plus_offset_reg");
    ld_st2(as_Register(pfop), adr, 0b11, 0b10);
  }
}

// An "all-purpose" add/subtract immediate, per ARM documentation:
// A "programmer-friendly" assembler may accept a negative immediate
// between -(2^24 -1) and -1 inclusive, causing it to convert a
// requested ADD operation to a SUB, or vice versa, and then encode
// the absolute value of the immediate as for uimm24.
void Assembler::add_sub_immediate(Instruction_aarch64 &current_insn,
                                  Register Rd, Register Rn, unsigned uimm, int op,
                                  int negated_op) {
  bool sets_flags = op & 1;   // this op sets flags
  union {
    unsigned u;
    int imm;
  };
  u = uimm;
  bool shift = false;
  bool neg = imm < 0;
  if (neg) {
    imm = -imm;
    op = negated_op;
  }
  assert(Rd != sp || imm % 16 == 0, "misaligned stack");
  if (imm >= (1 << 11)
      && ((imm >> 12) << 12 == imm)) {
    imm >>= 12;
    shift = true;
  }
  f(op, 31, 29), f(0b10001, 28, 24), f(shift, 23, 22), f(imm, 21, 10);

  // add/subtract immediate ops with the S bit set treat r31 as zr;
  // with S unset they use sp.
  if (sets_flags)
    zrf(Rd, 0);
  else
    srf(Rd, 0);

  srf(Rn, 5);
}

#undef f
#undef sf
#undef rf
#undef srf
#undef zrf
#undef prf
#undef pgrf
#undef fixed

#undef starti

#ifdef ASSERT

void Address::assert_is_literal() const {
  assert(_mode == literal, "addressing mode is non-literal: %d", _mode);
}

void Address::assert_is_nonliteral() const {
  assert(_mode != literal, "unexpected literal addressing mode");
  assert(_mode != no_mode, "unexpected no_mode addressing mode");
}

#endif // ASSERT

static RelocationHolder address_relocation(address target, relocInfo::relocType rtype) {
  switch (rtype) {
  case relocInfo::oop_type:
  case relocInfo::metadata_type:
    // Oops are a special case. Normally they would be their own section
    // but in cases like icBuffer they are literals in the code stream that
    // we don't have a section for. We use none so that we get a literal address
    // which is always patchable.
    return RelocationHolder::none;
  case relocInfo::external_word_type:
    return external_word_Relocation::spec(target);
  case relocInfo::internal_word_type:
    return internal_word_Relocation::spec(target);
  case relocInfo::opt_virtual_call_type:
    return opt_virtual_call_Relocation::spec();
  case relocInfo::static_call_type:
    return static_call_Relocation::spec();
  case relocInfo::runtime_call_type:
    return runtime_call_Relocation::spec();
  case relocInfo::poll_type:
  case relocInfo::poll_return_type:
    return Relocation::spec_simple(rtype);
  case relocInfo::none:
    return RelocationHolder::none;
  default:
    ShouldNotReachHere();
    return RelocationHolder::none;
  }
}

Address::Address(address target, relocInfo::relocType rtype) :
  _mode(literal),
  _literal(target, address_relocation(target, rtype))
{}

void Assembler::b(const Address &dest) {
  code_section()->relocate(pc(), dest.rspec());
  b(dest.target());
}

void Assembler::bl(const Address &dest) {
  code_section()->relocate(pc(), dest.rspec());
  bl(dest.target());
}

void Assembler::adr(Register r, const Address &dest) {
  code_section()->relocate(pc(), dest.rspec());
  adr(r, dest.target());
}

void Assembler::br(Condition cc, Label &L) {
  if (L.is_bound()) {
    br(cc, target(L));
  } else {
    L.add_patch_at(code(), locator());
    br(cc, pc());
  }
}

void Assembler::wrap_label(Label &L,
                                 Assembler::uncond_branch_insn insn) {
  if (L.is_bound()) {
    (this->*insn)(target(L));
  } else {
    L.add_patch_at(code(), locator());
    (this->*insn)(pc());
  }
}

void Assembler::wrap_label(Register r, Label &L,
                                 compare_and_branch_insn insn) {
  if (L.is_bound()) {
    (this->*insn)(r, target(L));
  } else {
    L.add_patch_at(code(), locator());
    (this->*insn)(r, pc());
  }
}

void Assembler::wrap_label(Register r, int bitpos, Label &L,
                                 test_and_branch_insn insn) {
  if (L.is_bound()) {
    (this->*insn)(r, bitpos, target(L));
  } else {
    L.add_patch_at(code(), locator());
    (this->*insn)(r, bitpos, pc());
  }
}

void Assembler::wrap_label(Label &L, prfop op, prefetch_insn insn) {
  if (L.is_bound()) {
    (this->*insn)(target(L), op);
  } else {
    L.add_patch_at(code(), locator());
    (this->*insn)(pc(), op);
  }
}

bool Assembler::operand_valid_for_add_sub_immediate(int64_t imm) {
  return operand_valid_for_immediate_bits(imm, 12);
}

bool Assembler::operand_valid_for_sve_add_sub_immediate(int64_t imm) {
  return operand_valid_for_immediate_bits(imm, 8);
}

bool Assembler::operand_valid_for_logical_immediate(bool is32, uint64_t imm) {
  return encode_logical_immediate(is32, imm) != 0xffffffff;
}

// Check immediate encoding for movi.
// Return the shift amount which can be {0, 8, 16, 24} for B/H/S types. As the D type
// movi does not have shift variant, in this case the return value is the immediate
// after encoding.
// Return -1 if the input imm64 can not be encoded.
int Assembler::operand_valid_for_movi_immediate(uint64_t imm64, SIMD_Arrangement T) {
  if (T == T1D || T == T2D) {
     // To encode into movi, the 64-bit imm must be in the form of
     // 'aaaaaaaabbbbbbbbccccccccddddddddeeeeeeeeffffffffgggggggghhhhhhhh'
     // and encoded in "a:b:c:d:e:f:g:h".
     uint64_t tmp = imm64;
     uint64_t one_byte = 0;
     for (int i = 0; i < 8; i++) {
       one_byte = tmp & 0xffULL;
       if (one_byte != 0xffULL && one_byte != 0) {
         return -1; // can not be encoded
       }
       tmp = tmp >> 8;
     }

     imm64 &= 0x0101010101010101ULL;
     imm64 |= (imm64 >> 7);
     imm64 |= (imm64 >> 14);
     imm64 |= (imm64 >> 28);

     return imm64 & 0xff;
  }

  uint32_t imm32 = imm64 & 0xffffffffULL;
  if (T == T8B || T == T16B) {       // 8-bit variant
    if (0 == (imm32 & ~0xff))        return 0;
  } else if(T == T4H || T == T8H) {  // 16-bit variant
    if (0 == (imm32 & ~0xff))        return 0;
    if (0 == (imm32 & ~0xff00))      return 8;
  } else if (T == T2S || T == T4S) { // 32-bit variant
    if (0 == (imm32 & ~0xff))        return 0;
    if (0 == (imm32 & ~0xff00))      return 8;
    if (0 == (imm32 & ~0xff0000))    return 16;
    if (0 == (imm32 & ~0xff000000))  return 24;
  } else {
    assert(false, "unsupported");
    ShouldNotReachHere();
  }

  return -1;
}

bool Assembler::operand_valid_for_sve_logical_immediate(unsigned elembits, uint64_t imm) {
  return encode_sve_logical_immediate(elembits, imm) != 0xffffffff;
}

static uint64_t doubleTo64Bits(jdouble d) {
  union {
    jdouble double_value;
    uint64_t double_bits;
  };

  double_value = d;
  return double_bits;
}

bool Assembler::operand_valid_for_float_immediate(double imm) {
  // If imm is all zero bits we can use ZR as the source of a
  // floating-point value.
  if (doubleTo64Bits(imm) == 0)
    return true;

  // Otherwise try to encode imm then convert the encoded value back
  // and make sure it's the exact same bit pattern.
  unsigned result = encoding_for_fp_immediate(imm);
  return doubleTo64Bits(imm) == fp_immediate_for_encoding(result, true);
}

int AbstractAssembler::code_fill_byte() {
  return 0;
}

// n.b. this is implemented in subclass MacroAssembler
void Assembler::bang_stack_with_offset(int offset) { Unimplemented(); }

bool asm_util::operand_valid_for_immediate_bits(int64_t imm, unsigned nbits) {
  guarantee(nbits == 8 || nbits == 12, "invalid nbits value");
  uint64_t uimm = (uint64_t)uabs((jlong)imm);
  if (uimm < (UCONST64(1) << nbits))
    return true;
  if (uimm < (UCONST64(1) << (2 * nbits))
      && ((uimm >> nbits) << nbits == uimm)) {
    return true;
  }
  return false;
}

// and now the routines called by the assembler which encapsulate the
// above encode and decode functions

uint32_t
asm_util::encode_logical_immediate(bool is32, uint64_t imm)
{
  if (is32) {
    /* Allow all zeros or all ones in top 32-bits, so that
       constant expressions like ~1 are permitted. */
    if (imm >> 32 != 0 && imm >> 32 != 0xffffffff)
      return 0xffffffff;
    /* Replicate the 32 lower bits to the 32 upper bits.  */
    imm &= 0xffffffff;
    imm |= imm << 32;
  }

  return encoding_for_logical_immediate(imm);
}

uint32_t
asm_util::encode_sve_logical_immediate(unsigned elembits, uint64_t imm) {
  guarantee(elembits == 8 || elembits == 16 ||
            elembits == 32 || elembits == 64, "unsupported element size");
  uint64_t upper = UCONST64(-1) << (elembits/2) << (elembits/2);
  /* Allow all zeros or all ones in top bits, so that
   * constant expressions like ~1 are permitted. */
  if ((imm & ~upper) != imm && (imm | upper) != imm)
    return 0xffffffff;

  // Replicate the immediate in different element sizes to 64 bits.
  imm &= ~upper;
  for (unsigned i = elembits; i < 64; i *= 2) {
    imm |= (imm << i);
  }

  return encoding_for_logical_immediate(imm);
}

unsigned Assembler::pack(double value) {
  float val = (float)value;
  unsigned result = encoding_for_fp_immediate(val);
  guarantee(unpack(result) == value,
            "Invalid floating-point immediate operand");
  return result;
}

// Packed operands for  Floating-point Move (immediate)

static float unpack(unsigned value) {
  unsigned ival = fp_immediate_for_encoding(value, 0);
  return PrimitiveConversions::cast<float>(ival);
}

address Assembler::locate_next_instruction(address inst) {
  return inst + Assembler::instruction_size;
}
