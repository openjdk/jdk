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

#include "incls/_precompiled.incl"
#include "incls/_assembler_x86.cpp.incl"

// Implementation of AddressLiteral

AddressLiteral::AddressLiteral(address target, relocInfo::relocType rtype) {
  _is_lval = false;
  _target = target;
  switch (rtype) {
  case relocInfo::oop_type:
    // Oops are a special case. Normally they would be their own section
    // but in cases like icBuffer they are literals in the code stream that
    // we don't have a section for. We use none so that we get a literal address
    // which is always patchable.
    break;
  case relocInfo::external_word_type:
    _rspec = external_word_Relocation::spec(target);
    break;
  case relocInfo::internal_word_type:
    _rspec = internal_word_Relocation::spec(target);
    break;
  case relocInfo::opt_virtual_call_type:
    _rspec = opt_virtual_call_Relocation::spec();
    break;
  case relocInfo::static_call_type:
    _rspec = static_call_Relocation::spec();
    break;
  case relocInfo::runtime_call_type:
    _rspec = runtime_call_Relocation::spec();
    break;
  case relocInfo::poll_type:
  case relocInfo::poll_return_type:
    _rspec = Relocation::spec_simple(rtype);
    break;
  case relocInfo::none:
    break;
  default:
    ShouldNotReachHere();
    break;
  }
}

// Implementation of Address

#ifdef _LP64

Address Address::make_array(ArrayAddress adr) {
  // Not implementable on 64bit machines
  // Should have been handled higher up the call chain.
  ShouldNotReachHere();
  return Address();
}

// exceedingly dangerous constructor
Address::Address(int disp, address loc, relocInfo::relocType rtype) {
  _base  = noreg;
  _index = noreg;
  _scale = no_scale;
  _disp  = disp;
  switch (rtype) {
    case relocInfo::external_word_type:
      _rspec = external_word_Relocation::spec(loc);
      break;
    case relocInfo::internal_word_type:
      _rspec = internal_word_Relocation::spec(loc);
      break;
    case relocInfo::runtime_call_type:
      // HMM
      _rspec = runtime_call_Relocation::spec();
      break;
    case relocInfo::poll_type:
    case relocInfo::poll_return_type:
      _rspec = Relocation::spec_simple(rtype);
      break;
    case relocInfo::none:
      break;
    default:
      ShouldNotReachHere();
  }
}
#else // LP64

Address Address::make_array(ArrayAddress adr) {
  AddressLiteral base = adr.base();
  Address index = adr.index();
  assert(index._disp == 0, "must not have disp"); // maybe it can?
  Address array(index._base, index._index, index._scale, (intptr_t) base.target());
  array._rspec = base._rspec;
  return array;
}

// exceedingly dangerous constructor
Address::Address(address loc, RelocationHolder spec) {
  _base  = noreg;
  _index = noreg;
  _scale = no_scale;
  _disp  = (intptr_t) loc;
  _rspec = spec;
}

#endif // _LP64



// Convert the raw encoding form into the form expected by the constructor for
// Address.  An index of 4 (rsp) corresponds to having no index, so convert
// that to noreg for the Address constructor.
Address Address::make_raw(int base, int index, int scale, int disp, bool disp_is_oop) {
  RelocationHolder rspec;
  if (disp_is_oop) {
    rspec = Relocation::spec_simple(relocInfo::oop_type);
  }
  bool valid_index = index != rsp->encoding();
  if (valid_index) {
    Address madr(as_Register(base), as_Register(index), (Address::ScaleFactor)scale, in_ByteSize(disp));
    madr._rspec = rspec;
    return madr;
  } else {
    Address madr(as_Register(base), noreg, Address::no_scale, in_ByteSize(disp));
    madr._rspec = rspec;
    return madr;
  }
}

// Implementation of Assembler

int AbstractAssembler::code_fill_byte() {
  return (u_char)'\xF4'; // hlt
}

// make this go away someday
void Assembler::emit_data(jint data, relocInfo::relocType rtype, int format) {
  if (rtype == relocInfo::none)
        emit_long(data);
  else  emit_data(data, Relocation::spec_simple(rtype), format);
}

void Assembler::emit_data(jint data, RelocationHolder const& rspec, int format) {
  assert(imm_operand == 0, "default format must be immediate in this file");
  assert(inst_mark() != NULL, "must be inside InstructionMark");
  if (rspec.type() !=  relocInfo::none) {
    #ifdef ASSERT
      check_relocation(rspec, format);
    #endif
    // Do not use AbstractAssembler::relocate, which is not intended for
    // embedded words.  Instead, relocate to the enclosing instruction.

    // hack. call32 is too wide for mask so use disp32
    if (format == call32_operand)
      code_section()->relocate(inst_mark(), rspec, disp32_operand);
    else
      code_section()->relocate(inst_mark(), rspec, format);
  }
  emit_long(data);
}

static int encode(Register r) {
  int enc = r->encoding();
  if (enc >= 8) {
    enc -= 8;
  }
  return enc;
}

static int encode(XMMRegister r) {
  int enc = r->encoding();
  if (enc >= 8) {
    enc -= 8;
  }
  return enc;
}

void Assembler::emit_arith_b(int op1, int op2, Register dst, int imm8) {
  assert(dst->has_byte_register(), "must have byte register");
  assert(isByte(op1) && isByte(op2), "wrong opcode");
  assert(isByte(imm8), "not a byte");
  assert((op1 & 0x01) == 0, "should be 8bit operation");
  emit_byte(op1);
  emit_byte(op2 | encode(dst));
  emit_byte(imm8);
}


void Assembler::emit_arith(int op1, int op2, Register dst, int32_t imm32) {
  assert(isByte(op1) && isByte(op2), "wrong opcode");
  assert((op1 & 0x01) == 1, "should be 32bit operation");
  assert((op1 & 0x02) == 0, "sign-extension bit should not be set");
  if (is8bit(imm32)) {
    emit_byte(op1 | 0x02); // set sign bit
    emit_byte(op2 | encode(dst));
    emit_byte(imm32 & 0xFF);
  } else {
    emit_byte(op1);
    emit_byte(op2 | encode(dst));
    emit_long(imm32);
  }
}

// immediate-to-memory forms
void Assembler::emit_arith_operand(int op1, Register rm, Address adr, int32_t imm32) {
  assert((op1 & 0x01) == 1, "should be 32bit operation");
  assert((op1 & 0x02) == 0, "sign-extension bit should not be set");
  if (is8bit(imm32)) {
    emit_byte(op1 | 0x02); // set sign bit
    emit_operand(rm, adr, 1);
    emit_byte(imm32 & 0xFF);
  } else {
    emit_byte(op1);
    emit_operand(rm, adr, 4);
    emit_long(imm32);
  }
}

void Assembler::emit_arith(int op1, int op2, Register dst, jobject obj) {
  LP64_ONLY(ShouldNotReachHere());
  assert(isByte(op1) && isByte(op2), "wrong opcode");
  assert((op1 & 0x01) == 1, "should be 32bit operation");
  assert((op1 & 0x02) == 0, "sign-extension bit should not be set");
  InstructionMark im(this);
  emit_byte(op1);
  emit_byte(op2 | encode(dst));
  emit_data((intptr_t)obj, relocInfo::oop_type, 0);
}


void Assembler::emit_arith(int op1, int op2, Register dst, Register src) {
  assert(isByte(op1) && isByte(op2), "wrong opcode");
  emit_byte(op1);
  emit_byte(op2 | encode(dst) << 3 | encode(src));
}


void Assembler::emit_operand(Register reg, Register base, Register index,
                             Address::ScaleFactor scale, int disp,
                             RelocationHolder const& rspec,
                             int rip_relative_correction) {
  relocInfo::relocType rtype = (relocInfo::relocType) rspec.type();

  // Encode the registers as needed in the fields they are used in

  int regenc = encode(reg) << 3;
  int indexenc = index->is_valid() ? encode(index) << 3 : 0;
  int baseenc = base->is_valid() ? encode(base) : 0;

  if (base->is_valid()) {
    if (index->is_valid()) {
      assert(scale != Address::no_scale, "inconsistent address");
      // [base + index*scale + disp]
      if (disp == 0 && rtype == relocInfo::none  &&
          base != rbp LP64_ONLY(&& base != r13)) {
        // [base + index*scale]
        // [00 reg 100][ss index base]
        assert(index != rsp, "illegal addressing mode");
        emit_byte(0x04 | regenc);
        emit_byte(scale << 6 | indexenc | baseenc);
      } else if (is8bit(disp) && rtype == relocInfo::none) {
        // [base + index*scale + imm8]
        // [01 reg 100][ss index base] imm8
        assert(index != rsp, "illegal addressing mode");
        emit_byte(0x44 | regenc);
        emit_byte(scale << 6 | indexenc | baseenc);
        emit_byte(disp & 0xFF);
      } else {
        // [base + index*scale + disp32]
        // [10 reg 100][ss index base] disp32
        assert(index != rsp, "illegal addressing mode");
        emit_byte(0x84 | regenc);
        emit_byte(scale << 6 | indexenc | baseenc);
        emit_data(disp, rspec, disp32_operand);
      }
    } else if (base == rsp LP64_ONLY(|| base == r12)) {
      // [rsp + disp]
      if (disp == 0 && rtype == relocInfo::none) {
        // [rsp]
        // [00 reg 100][00 100 100]
        emit_byte(0x04 | regenc);
        emit_byte(0x24);
      } else if (is8bit(disp) && rtype == relocInfo::none) {
        // [rsp + imm8]
        // [01 reg 100][00 100 100] disp8
        emit_byte(0x44 | regenc);
        emit_byte(0x24);
        emit_byte(disp & 0xFF);
      } else {
        // [rsp + imm32]
        // [10 reg 100][00 100 100] disp32
        emit_byte(0x84 | regenc);
        emit_byte(0x24);
        emit_data(disp, rspec, disp32_operand);
      }
    } else {
      // [base + disp]
      assert(base != rsp LP64_ONLY(&& base != r12), "illegal addressing mode");
      if (disp == 0 && rtype == relocInfo::none &&
          base != rbp LP64_ONLY(&& base != r13)) {
        // [base]
        // [00 reg base]
        emit_byte(0x00 | regenc | baseenc);
      } else if (is8bit(disp) && rtype == relocInfo::none) {
        // [base + disp8]
        // [01 reg base] disp8
        emit_byte(0x40 | regenc | baseenc);
        emit_byte(disp & 0xFF);
      } else {
        // [base + disp32]
        // [10 reg base] disp32
        emit_byte(0x80 | regenc | baseenc);
        emit_data(disp, rspec, disp32_operand);
      }
    }
  } else {
    if (index->is_valid()) {
      assert(scale != Address::no_scale, "inconsistent address");
      // [index*scale + disp]
      // [00 reg 100][ss index 101] disp32
      assert(index != rsp, "illegal addressing mode");
      emit_byte(0x04 | regenc);
      emit_byte(scale << 6 | indexenc | 0x05);
      emit_data(disp, rspec, disp32_operand);
    } else if (rtype != relocInfo::none ) {
      // [disp] (64bit) RIP-RELATIVE (32bit) abs
      // [00 000 101] disp32

      emit_byte(0x05 | regenc);
      // Note that the RIP-rel. correction applies to the generated
      // disp field, but _not_ to the target address in the rspec.

      // disp was created by converting the target address minus the pc
      // at the start of the instruction. That needs more correction here.
      // intptr_t disp = target - next_ip;
      assert(inst_mark() != NULL, "must be inside InstructionMark");
      address next_ip = pc() + sizeof(int32_t) + rip_relative_correction;
      int64_t adjusted = disp;
      // Do rip-rel adjustment for 64bit
      LP64_ONLY(adjusted -=  (next_ip - inst_mark()));
      assert(is_simm32(adjusted),
             "must be 32bit offset (RIP relative address)");
      emit_data((int32_t) adjusted, rspec, disp32_operand);

    } else {
      // 32bit never did this, did everything as the rip-rel/disp code above
      // [disp] ABSOLUTE
      // [00 reg 100][00 100 101] disp32
      emit_byte(0x04 | regenc);
      emit_byte(0x25);
      emit_data(disp, rspec, disp32_operand);
    }
  }
}

void Assembler::emit_operand(XMMRegister reg, Register base, Register index,
                             Address::ScaleFactor scale, int disp,
                             RelocationHolder const& rspec) {
  emit_operand((Register)reg, base, index, scale, disp, rspec);
}

// Secret local extension to Assembler::WhichOperand:
#define end_pc_operand (_WhichOperand_limit)

address Assembler::locate_operand(address inst, WhichOperand which) {
  // Decode the given instruction, and return the address of
  // an embedded 32-bit operand word.

  // If "which" is disp32_operand, selects the displacement portion
  // of an effective address specifier.
  // If "which" is imm64_operand, selects the trailing immediate constant.
  // If "which" is call32_operand, selects the displacement of a call or jump.
  // Caller is responsible for ensuring that there is such an operand,
  // and that it is 32/64 bits wide.

  // If "which" is end_pc_operand, find the end of the instruction.

  address ip = inst;
  bool is_64bit = false;

  debug_only(bool has_disp32 = false);
  int tail_size = 0; // other random bytes (#32, #16, etc.) at end of insn

  again_after_prefix:
  switch (0xFF & *ip++) {

  // These convenience macros generate groups of "case" labels for the switch.
#define REP4(x) (x)+0: case (x)+1: case (x)+2: case (x)+3
#define REP8(x) (x)+0: case (x)+1: case (x)+2: case (x)+3: \
             case (x)+4: case (x)+5: case (x)+6: case (x)+7
#define REP16(x) REP8((x)+0): \
              case REP8((x)+8)

  case CS_segment:
  case SS_segment:
  case DS_segment:
  case ES_segment:
  case FS_segment:
  case GS_segment:
    // Seems dubious
    LP64_ONLY(assert(false, "shouldn't have that prefix"));
    assert(ip == inst+1, "only one prefix allowed");
    goto again_after_prefix;

  case 0x67:
  case REX:
  case REX_B:
  case REX_X:
  case REX_XB:
  case REX_R:
  case REX_RB:
  case REX_RX:
  case REX_RXB:
    NOT_LP64(assert(false, "64bit prefixes"));
    goto again_after_prefix;

  case REX_W:
  case REX_WB:
  case REX_WX:
  case REX_WXB:
  case REX_WR:
  case REX_WRB:
  case REX_WRX:
  case REX_WRXB:
    NOT_LP64(assert(false, "64bit prefixes"));
    is_64bit = true;
    goto again_after_prefix;

  case 0xFF: // pushq a; decl a; incl a; call a; jmp a
  case 0x88: // movb a, r
  case 0x89: // movl a, r
  case 0x8A: // movb r, a
  case 0x8B: // movl r, a
  case 0x8F: // popl a
    debug_only(has_disp32 = true);
    break;

  case 0x68: // pushq #32
    if (which == end_pc_operand) {
      return ip + 4;
    }
    assert(which == imm_operand && !is_64bit, "pushl has no disp32 or 64bit immediate");
    return ip;                  // not produced by emit_operand

  case 0x66: // movw ... (size prefix)
    again_after_size_prefix2:
    switch (0xFF & *ip++) {
    case REX:
    case REX_B:
    case REX_X:
    case REX_XB:
    case REX_R:
    case REX_RB:
    case REX_RX:
    case REX_RXB:
    case REX_W:
    case REX_WB:
    case REX_WX:
    case REX_WXB:
    case REX_WR:
    case REX_WRB:
    case REX_WRX:
    case REX_WRXB:
      NOT_LP64(assert(false, "64bit prefix found"));
      goto again_after_size_prefix2;
    case 0x8B: // movw r, a
    case 0x89: // movw a, r
      debug_only(has_disp32 = true);
      break;
    case 0xC7: // movw a, #16
      debug_only(has_disp32 = true);
      tail_size = 2;  // the imm16
      break;
    case 0x0F: // several SSE/SSE2 variants
      ip--;    // reparse the 0x0F
      goto again_after_prefix;
    default:
      ShouldNotReachHere();
    }
    break;

  case REP8(0xB8): // movl/q r, #32/#64(oop?)
    if (which == end_pc_operand)  return ip + (is_64bit ? 8 : 4);
    // these asserts are somewhat nonsensical
#ifndef _LP64
    assert(which == imm_operand || which == disp32_operand, "");
#else
    assert((which == call32_operand || which == imm_operand) && is_64bit ||
           which == narrow_oop_operand && !is_64bit, "");
#endif // _LP64
    return ip;

  case 0x69: // imul r, a, #32
  case 0xC7: // movl a, #32(oop?)
    tail_size = 4;
    debug_only(has_disp32 = true); // has both kinds of operands!
    break;

  case 0x0F: // movx..., etc.
    switch (0xFF & *ip++) {
    case 0x12: // movlps
    case 0x28: // movaps
    case 0x2E: // ucomiss
    case 0x2F: // comiss
    case 0x54: // andps
    case 0x55: // andnps
    case 0x56: // orps
    case 0x57: // xorps
    case 0x6E: // movd
    case 0x7E: // movd
    case 0xAE: // ldmxcsr   a
      // 64bit side says it these have both operands but that doesn't
      // appear to be true
      debug_only(has_disp32 = true);
      break;

    case 0xAD: // shrd r, a, %cl
    case 0xAF: // imul r, a
    case 0xBE: // movsbl r, a (movsxb)
    case 0xBF: // movswl r, a (movsxw)
    case 0xB6: // movzbl r, a (movzxb)
    case 0xB7: // movzwl r, a (movzxw)
    case REP16(0x40): // cmovl cc, r, a
    case 0xB0: // cmpxchgb
    case 0xB1: // cmpxchg
    case 0xC1: // xaddl
    case 0xC7: // cmpxchg8
    case REP16(0x90): // setcc a
      debug_only(has_disp32 = true);
      // fall out of the switch to decode the address
      break;

    case 0xAC: // shrd r, a, #8
      debug_only(has_disp32 = true);
      tail_size = 1;  // the imm8
      break;

    case REP16(0x80): // jcc rdisp32
      if (which == end_pc_operand)  return ip + 4;
      assert(which == call32_operand, "jcc has no disp32 or imm");
      return ip;
    default:
      ShouldNotReachHere();
    }
    break;

  case 0x81: // addl a, #32; addl r, #32
    // also: orl, adcl, sbbl, andl, subl, xorl, cmpl
    // on 32bit in the case of cmpl, the imm might be an oop
    tail_size = 4;
    debug_only(has_disp32 = true); // has both kinds of operands!
    break;

  case 0x83: // addl a, #8; addl r, #8
    // also: orl, adcl, sbbl, andl, subl, xorl, cmpl
    debug_only(has_disp32 = true); // has both kinds of operands!
    tail_size = 1;
    break;

  case 0x9B:
    switch (0xFF & *ip++) {
    case 0xD9: // fnstcw a
      debug_only(has_disp32 = true);
      break;
    default:
      ShouldNotReachHere();
    }
    break;

  case REP4(0x00): // addb a, r; addl a, r; addb r, a; addl r, a
  case REP4(0x10): // adc...
  case REP4(0x20): // and...
  case REP4(0x30): // xor...
  case REP4(0x08): // or...
  case REP4(0x18): // sbb...
  case REP4(0x28): // sub...
  case 0xF7: // mull a
  case 0x8D: // lea r, a
  case 0x87: // xchg r, a
  case REP4(0x38): // cmp...
  case 0x85: // test r, a
    debug_only(has_disp32 = true); // has both kinds of operands!
    break;

  case 0xC1: // sal a, #8; sar a, #8; shl a, #8; shr a, #8
  case 0xC6: // movb a, #8
  case 0x80: // cmpb a, #8
  case 0x6B: // imul r, a, #8
    debug_only(has_disp32 = true); // has both kinds of operands!
    tail_size = 1; // the imm8
    break;

  case 0xE8: // call rdisp32
  case 0xE9: // jmp  rdisp32
    if (which == end_pc_operand)  return ip + 4;
    assert(which == call32_operand, "call has no disp32 or imm");
    return ip;

  case 0xD1: // sal a, 1; sar a, 1; shl a, 1; shr a, 1
  case 0xD3: // sal a, %cl; sar a, %cl; shl a, %cl; shr a, %cl
  case 0xD9: // fld_s a; fst_s a; fstp_s a; fldcw a
  case 0xDD: // fld_d a; fst_d a; fstp_d a
  case 0xDB: // fild_s a; fistp_s a; fld_x a; fstp_x a
  case 0xDF: // fild_d a; fistp_d a
  case 0xD8: // fadd_s a; fsubr_s a; fmul_s a; fdivr_s a; fcomp_s a
  case 0xDC: // fadd_d a; fsubr_d a; fmul_d a; fdivr_d a; fcomp_d a
  case 0xDE: // faddp_d a; fsubrp_d a; fmulp_d a; fdivrp_d a; fcompp_d a
    debug_only(has_disp32 = true);
    break;

  case 0xF0:                    // Lock
    assert(os::is_MP(), "only on MP");
    goto again_after_prefix;

  case 0xF3:                    // For SSE
  case 0xF2:                    // For SSE2
    switch (0xFF & *ip++) {
    case REX:
    case REX_B:
    case REX_X:
    case REX_XB:
    case REX_R:
    case REX_RB:
    case REX_RX:
    case REX_RXB:
    case REX_W:
    case REX_WB:
    case REX_WX:
    case REX_WXB:
    case REX_WR:
    case REX_WRB:
    case REX_WRX:
    case REX_WRXB:
      NOT_LP64(assert(false, "found 64bit prefix"));
      ip++;
    default:
      ip++;
    }
    debug_only(has_disp32 = true); // has both kinds of operands!
    break;

  default:
    ShouldNotReachHere();

#undef REP8
#undef REP16
  }

  assert(which != call32_operand, "instruction is not a call, jmp, or jcc");
#ifdef _LP64
  assert(which != imm_operand, "instruction is not a movq reg, imm64");
#else
  // assert(which != imm_operand || has_imm32, "instruction has no imm32 field");
  assert(which != imm_operand || has_disp32, "instruction has no imm32 field");
#endif // LP64
  assert(which != disp32_operand || has_disp32, "instruction has no disp32 field");

  // parse the output of emit_operand
  int op2 = 0xFF & *ip++;
  int base = op2 & 0x07;
  int op3 = -1;
  const int b100 = 4;
  const int b101 = 5;
  if (base == b100 && (op2 >> 6) != 3) {
    op3 = 0xFF & *ip++;
    base = op3 & 0x07;   // refetch the base
  }
  // now ip points at the disp (if any)

  switch (op2 >> 6) {
  case 0:
    // [00 reg  100][ss index base]
    // [00 reg  100][00   100  esp]
    // [00 reg base]
    // [00 reg  100][ss index  101][disp32]
    // [00 reg  101]               [disp32]

    if (base == b101) {
      if (which == disp32_operand)
        return ip;              // caller wants the disp32
      ip += 4;                  // skip the disp32
    }
    break;

  case 1:
    // [01 reg  100][ss index base][disp8]
    // [01 reg  100][00   100  esp][disp8]
    // [01 reg base]               [disp8]
    ip += 1;                    // skip the disp8
    break;

  case 2:
    // [10 reg  100][ss index base][disp32]
    // [10 reg  100][00   100  esp][disp32]
    // [10 reg base]               [disp32]
    if (which == disp32_operand)
      return ip;                // caller wants the disp32
    ip += 4;                    // skip the disp32
    break;

  case 3:
    // [11 reg base]  (not a memory addressing mode)
    break;
  }

  if (which == end_pc_operand) {
    return ip + tail_size;
  }

#ifdef _LP64
  assert(which == narrow_oop_operand && !is_64bit, "instruction is not a movl adr, imm32");
#else
  assert(which == imm_operand, "instruction has only an imm field");
#endif // LP64
  return ip;
}

address Assembler::locate_next_instruction(address inst) {
  // Secretly share code with locate_operand:
  return locate_operand(inst, end_pc_operand);
}


#ifdef ASSERT
void Assembler::check_relocation(RelocationHolder const& rspec, int format) {
  address inst = inst_mark();
  assert(inst != NULL && inst < pc(), "must point to beginning of instruction");
  address opnd;

  Relocation* r = rspec.reloc();
  if (r->type() == relocInfo::none) {
    return;
  } else if (r->is_call() || format == call32_operand) {
    // assert(format == imm32_operand, "cannot specify a nonzero format");
    opnd = locate_operand(inst, call32_operand);
  } else if (r->is_data()) {
    assert(format == imm_operand || format == disp32_operand
           LP64_ONLY(|| format == narrow_oop_operand), "format ok");
    opnd = locate_operand(inst, (WhichOperand)format);
  } else {
    assert(format == imm_operand, "cannot specify a format");
    return;
  }
  assert(opnd == pc(), "must put operand where relocs can find it");
}
#endif // ASSERT

void Assembler::emit_operand32(Register reg, Address adr) {
  assert(reg->encoding() < 8, "no extended registers");
  assert(!adr.base_needs_rex() && !adr.index_needs_rex(), "no extended registers");
  emit_operand(reg, adr._base, adr._index, adr._scale, adr._disp,
               adr._rspec);
}

void Assembler::emit_operand(Register reg, Address adr,
                             int rip_relative_correction) {
  emit_operand(reg, adr._base, adr._index, adr._scale, adr._disp,
               adr._rspec,
               rip_relative_correction);
}

void Assembler::emit_operand(XMMRegister reg, Address adr) {
  emit_operand(reg, adr._base, adr._index, adr._scale, adr._disp,
               adr._rspec);
}

// MMX operations
void Assembler::emit_operand(MMXRegister reg, Address adr) {
  assert(!adr.base_needs_rex() && !adr.index_needs_rex(), "no extended registers");
  emit_operand((Register)reg, adr._base, adr._index, adr._scale, adr._disp, adr._rspec);
}

// work around gcc (3.2.1-7a) bug
void Assembler::emit_operand(Address adr, MMXRegister reg) {
  assert(!adr.base_needs_rex() && !adr.index_needs_rex(), "no extended registers");
  emit_operand((Register)reg, adr._base, adr._index, adr._scale, adr._disp, adr._rspec);
}


void Assembler::emit_farith(int b1, int b2, int i) {
  assert(isByte(b1) && isByte(b2), "wrong opcode");
  assert(0 <= i &&  i < 8, "illegal stack offset");
  emit_byte(b1);
  emit_byte(b2 + i);
}


// Now the Assembler instruction (identical for 32/64 bits)

void Assembler::adcl(Register dst, int32_t imm32) {
  prefix(dst);
  emit_arith(0x81, 0xD0, dst, imm32);
}

void Assembler::adcl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x13);
  emit_operand(dst, src);
}

void Assembler::adcl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x13, 0xC0, dst, src);
}

void Assembler::addl(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefix(dst);
  emit_arith_operand(0x81, rax, dst, imm32);
}

void Assembler::addl(Address dst, Register src) {
  InstructionMark im(this);
  prefix(dst, src);
  emit_byte(0x01);
  emit_operand(src, dst);
}

void Assembler::addl(Register dst, int32_t imm32) {
  prefix(dst);
  emit_arith(0x81, 0xC0, dst, imm32);
}

void Assembler::addl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x03);
  emit_operand(dst, src);
}

void Assembler::addl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x03, 0xC0, dst, src);
}

void Assembler::addr_nop_4() {
  // 4 bytes: NOP DWORD PTR [EAX+0]
  emit_byte(0x0F);
  emit_byte(0x1F);
  emit_byte(0x40); // emit_rm(cbuf, 0x1, EAX_enc, EAX_enc);
  emit_byte(0);    // 8-bits offset (1 byte)
}

void Assembler::addr_nop_5() {
  // 5 bytes: NOP DWORD PTR [EAX+EAX*0+0] 8-bits offset
  emit_byte(0x0F);
  emit_byte(0x1F);
  emit_byte(0x44); // emit_rm(cbuf, 0x1, EAX_enc, 0x4);
  emit_byte(0x00); // emit_rm(cbuf, 0x0, EAX_enc, EAX_enc);
  emit_byte(0);    // 8-bits offset (1 byte)
}

void Assembler::addr_nop_7() {
  // 7 bytes: NOP DWORD PTR [EAX+0] 32-bits offset
  emit_byte(0x0F);
  emit_byte(0x1F);
  emit_byte(0x80); // emit_rm(cbuf, 0x2, EAX_enc, EAX_enc);
  emit_long(0);    // 32-bits offset (4 bytes)
}

void Assembler::addr_nop_8() {
  // 8 bytes: NOP DWORD PTR [EAX+EAX*0+0] 32-bits offset
  emit_byte(0x0F);
  emit_byte(0x1F);
  emit_byte(0x84); // emit_rm(cbuf, 0x2, EAX_enc, 0x4);
  emit_byte(0x00); // emit_rm(cbuf, 0x0, EAX_enc, EAX_enc);
  emit_long(0);    // 32-bits offset (4 bytes)
}

void Assembler::addsd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x58);
  emit_byte(0xC0 | encode);
}

void Assembler::addsd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0xF2);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x58);
  emit_operand(dst, src);
}

void Assembler::addss(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x58);
  emit_byte(0xC0 | encode);
}

void Assembler::addss(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x58);
  emit_operand(dst, src);
}

void Assembler::andl(Register dst, int32_t imm32) {
  prefix(dst);
  emit_arith(0x81, 0xE0, dst, imm32);
}

void Assembler::andl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x23);
  emit_operand(dst, src);
}

void Assembler::andl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x23, 0xC0, dst, src);
}

void Assembler::andpd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0x66);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x54);
  emit_operand(dst, src);
}

void Assembler::bsfl(Register dst, Register src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xBC);
  emit_byte(0xC0 | encode);
}

void Assembler::bsrl(Register dst, Register src) {
  assert(!VM_Version::supports_lzcnt(), "encoding is treated as LZCNT");
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xBD);
  emit_byte(0xC0 | encode);
}

void Assembler::bswapl(Register reg) { // bswap
  int encode = prefix_and_encode(reg->encoding());
  emit_byte(0x0F);
  emit_byte(0xC8 | encode);
}

void Assembler::call(Label& L, relocInfo::relocType rtype) {
  // suspect disp32 is always good
  int operand = LP64_ONLY(disp32_operand) NOT_LP64(imm_operand);

  if (L.is_bound()) {
    const int long_size = 5;
    int offs = (int)( target(L) - pc() );
    assert(offs <= 0, "assembler error");
    InstructionMark im(this);
    // 1110 1000 #32-bit disp
    emit_byte(0xE8);
    emit_data(offs - long_size, rtype, operand);
  } else {
    InstructionMark im(this);
    // 1110 1000 #32-bit disp
    L.add_patch_at(code(), locator());

    emit_byte(0xE8);
    emit_data(int(0), rtype, operand);
  }
}

void Assembler::call(Register dst) {
  // This was originally using a 32bit register encoding
  // and surely we want 64bit!
  // this is a 32bit encoding but in 64bit mode the default
  // operand size is 64bit so there is no need for the
  // wide prefix. So prefix only happens if we use the
  // new registers. Much like push/pop.
  int x = offset();
  // this may be true but dbx disassembles it as if it
  // were 32bits...
  // int encode = prefix_and_encode(dst->encoding());
  // if (offset() != x) assert(dst->encoding() >= 8, "what?");
  int encode = prefixq_and_encode(dst->encoding());

  emit_byte(0xFF);
  emit_byte(0xD0 | encode);
}


void Assembler::call(Address adr) {
  InstructionMark im(this);
  prefix(adr);
  emit_byte(0xFF);
  emit_operand(rdx, adr);
}

void Assembler::call_literal(address entry, RelocationHolder const& rspec) {
  assert(entry != NULL, "call most probably wrong");
  InstructionMark im(this);
  emit_byte(0xE8);
  intptr_t disp = entry - (_code_pos + sizeof(int32_t));
  assert(is_simm32(disp), "must be 32bit offset (call2)");
  // Technically, should use call32_operand, but this format is
  // implied by the fact that we're emitting a call instruction.

  int operand = LP64_ONLY(disp32_operand) NOT_LP64(call32_operand);
  emit_data((int) disp, rspec, operand);
}

void Assembler::cdql() {
  emit_byte(0x99);
}

void Assembler::cmovl(Condition cc, Register dst, Register src) {
  NOT_LP64(guarantee(VM_Version::supports_cmov(), "illegal instruction"));
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x40 | cc);
  emit_byte(0xC0 | encode);
}


void Assembler::cmovl(Condition cc, Register dst, Address src) {
  NOT_LP64(guarantee(VM_Version::supports_cmov(), "illegal instruction"));
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x40 | cc);
  emit_operand(dst, src);
}

void Assembler::cmpb(Address dst, int imm8) {
  InstructionMark im(this);
  prefix(dst);
  emit_byte(0x80);
  emit_operand(rdi, dst, 1);
  emit_byte(imm8);
}

void Assembler::cmpl(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefix(dst);
  emit_byte(0x81);
  emit_operand(rdi, dst, 4);
  emit_long(imm32);
}

void Assembler::cmpl(Register dst, int32_t imm32) {
  prefix(dst);
  emit_arith(0x81, 0xF8, dst, imm32);
}

void Assembler::cmpl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x3B, 0xC0, dst, src);
}


void Assembler::cmpl(Register dst, Address  src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x3B);
  emit_operand(dst, src);
}

void Assembler::cmpw(Address dst, int imm16) {
  InstructionMark im(this);
  assert(!dst.base_needs_rex() && !dst.index_needs_rex(), "no extended registers");
  emit_byte(0x66);
  emit_byte(0x81);
  emit_operand(rdi, dst, 2);
  emit_word(imm16);
}

// The 32-bit cmpxchg compares the value at adr with the contents of rax,
// and stores reg into adr if so; otherwise, the value at adr is loaded into rax,.
// The ZF is set if the compared values were equal, and cleared otherwise.
void Assembler::cmpxchgl(Register reg, Address adr) { // cmpxchg
  if (Atomics & 2) {
     // caveat: no instructionmark, so this isn't relocatable.
     // Emit a synthetic, non-atomic, CAS equivalent.
     // Beware.  The synthetic form sets all ICCs, not just ZF.
     // cmpxchg r,[m] is equivalent to rax, = CAS (m, rax, r)
     cmpl(rax, adr);
     movl(rax, adr);
     if (reg != rax) {
        Label L ;
        jcc(Assembler::notEqual, L);
        movl(adr, reg);
        bind(L);
     }
  } else {
     InstructionMark im(this);
     prefix(adr, reg);
     emit_byte(0x0F);
     emit_byte(0xB1);
     emit_operand(reg, adr);
  }
}

void Assembler::comisd(XMMRegister dst, Address src) {
  // NOTE: dbx seems to decode this as comiss even though the
  // 0x66 is there. Strangly ucomisd comes out correct
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0x66);
  comiss(dst, src);
}

void Assembler::comiss(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));

  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x2F);
  emit_operand(dst, src);
}

void Assembler::cvtdq2pd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xE6);
  emit_byte(0xC0 | encode);
}

void Assembler::cvtdq2ps(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x5B);
  emit_byte(0xC0 | encode);
}

void Assembler::cvtsd2ss(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x5A);
  emit_byte(0xC0 | encode);
}

void Assembler::cvtsi2sdl(XMMRegister dst, Register src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2A);
  emit_byte(0xC0 | encode);
}

void Assembler::cvtsi2ssl(XMMRegister dst, Register src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2A);
  emit_byte(0xC0 | encode);
}

void Assembler::cvtss2sd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x5A);
  emit_byte(0xC0 | encode);
}

void Assembler::cvttsd2sil(Register dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2C);
  emit_byte(0xC0 | encode);
}

void Assembler::cvttss2sil(Register dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2C);
  emit_byte(0xC0 | encode);
}

void Assembler::decl(Address dst) {
  // Don't use it directly. Use MacroAssembler::decrement() instead.
  InstructionMark im(this);
  prefix(dst);
  emit_byte(0xFF);
  emit_operand(rcx, dst);
}

void Assembler::divsd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0xF2);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x5E);
  emit_operand(dst, src);
}

void Assembler::divsd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x5E);
  emit_byte(0xC0 | encode);
}

void Assembler::divss(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x5E);
  emit_operand(dst, src);
}

void Assembler::divss(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x5E);
  emit_byte(0xC0 | encode);
}

void Assembler::emms() {
  NOT_LP64(assert(VM_Version::supports_mmx(), ""));
  emit_byte(0x0F);
  emit_byte(0x77);
}

void Assembler::hlt() {
  emit_byte(0xF4);
}

void Assembler::idivl(Register src) {
  int encode = prefix_and_encode(src->encoding());
  emit_byte(0xF7);
  emit_byte(0xF8 | encode);
}

void Assembler::imull(Register dst, Register src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xAF);
  emit_byte(0xC0 | encode);
}


void Assembler::imull(Register dst, Register src, int value) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  if (is8bit(value)) {
    emit_byte(0x6B);
    emit_byte(0xC0 | encode);
    emit_byte(value);
  } else {
    emit_byte(0x69);
    emit_byte(0xC0 | encode);
    emit_long(value);
  }
}

void Assembler::incl(Address dst) {
  // Don't use it directly. Use MacroAssembler::increment() instead.
  InstructionMark im(this);
  prefix(dst);
  emit_byte(0xFF);
  emit_operand(rax, dst);
}

void Assembler::jcc(Condition cc, Label& L, relocInfo::relocType rtype) {
  InstructionMark im(this);
  relocate(rtype);
  assert((0 <= cc) && (cc < 16), "illegal cc");
  if (L.is_bound()) {
    address dst = target(L);
    assert(dst != NULL, "jcc most probably wrong");

    const int short_size = 2;
    const int long_size = 6;
    intptr_t offs = (intptr_t)dst - (intptr_t)_code_pos;
    if (rtype == relocInfo::none && is8bit(offs - short_size)) {
      // 0111 tttn #8-bit disp
      emit_byte(0x70 | cc);
      emit_byte((offs - short_size) & 0xFF);
    } else {
      // 0000 1111 1000 tttn #32-bit disp
      assert(is_simm32(offs - long_size),
             "must be 32bit offset (call4)");
      emit_byte(0x0F);
      emit_byte(0x80 | cc);
      emit_long(offs - long_size);
    }
  } else {
    // Note: could eliminate cond. jumps to this jump if condition
    //       is the same however, seems to be rather unlikely case.
    // Note: use jccb() if label to be bound is very close to get
    //       an 8-bit displacement
    L.add_patch_at(code(), locator());
    emit_byte(0x0F);
    emit_byte(0x80 | cc);
    emit_long(0);
  }
}

void Assembler::jccb(Condition cc, Label& L) {
  if (L.is_bound()) {
    const int short_size = 2;
    address entry = target(L);
    assert(is8bit((intptr_t)entry - ((intptr_t)_code_pos + short_size)),
           "Dispacement too large for a short jmp");
    intptr_t offs = (intptr_t)entry - (intptr_t)_code_pos;
    // 0111 tttn #8-bit disp
    emit_byte(0x70 | cc);
    emit_byte((offs - short_size) & 0xFF);
  } else {
    InstructionMark im(this);
    L.add_patch_at(code(), locator());
    emit_byte(0x70 | cc);
    emit_byte(0);
  }
}

void Assembler::jmp(Address adr) {
  InstructionMark im(this);
  prefix(adr);
  emit_byte(0xFF);
  emit_operand(rsp, adr);
}

void Assembler::jmp(Label& L, relocInfo::relocType rtype) {
  if (L.is_bound()) {
    address entry = target(L);
    assert(entry != NULL, "jmp most probably wrong");
    InstructionMark im(this);
    const int short_size = 2;
    const int long_size = 5;
    intptr_t offs = entry - _code_pos;
    if (rtype == relocInfo::none && is8bit(offs - short_size)) {
      emit_byte(0xEB);
      emit_byte((offs - short_size) & 0xFF);
    } else {
      emit_byte(0xE9);
      emit_long(offs - long_size);
    }
  } else {
    // By default, forward jumps are always 32-bit displacements, since
    // we can't yet know where the label will be bound.  If you're sure that
    // the forward jump will not run beyond 256 bytes, use jmpb to
    // force an 8-bit displacement.
    InstructionMark im(this);
    relocate(rtype);
    L.add_patch_at(code(), locator());
    emit_byte(0xE9);
    emit_long(0);
  }
}

void Assembler::jmp(Register entry) {
  int encode = prefix_and_encode(entry->encoding());
  emit_byte(0xFF);
  emit_byte(0xE0 | encode);
}

void Assembler::jmp_literal(address dest, RelocationHolder const& rspec) {
  InstructionMark im(this);
  emit_byte(0xE9);
  assert(dest != NULL, "must have a target");
  intptr_t disp = dest - (_code_pos + sizeof(int32_t));
  assert(is_simm32(disp), "must be 32bit offset (jmp)");
  emit_data(disp, rspec.reloc(), call32_operand);
}

void Assembler::jmpb(Label& L) {
  if (L.is_bound()) {
    const int short_size = 2;
    address entry = target(L);
    assert(is8bit((entry - _code_pos) + short_size),
           "Dispacement too large for a short jmp");
    assert(entry != NULL, "jmp most probably wrong");
    intptr_t offs = entry - _code_pos;
    emit_byte(0xEB);
    emit_byte((offs - short_size) & 0xFF);
  } else {
    InstructionMark im(this);
    L.add_patch_at(code(), locator());
    emit_byte(0xEB);
    emit_byte(0);
  }
}

void Assembler::ldmxcsr( Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  InstructionMark im(this);
  prefix(src);
  emit_byte(0x0F);
  emit_byte(0xAE);
  emit_operand(as_Register(2), src);
}

void Assembler::leal(Register dst, Address src) {
  InstructionMark im(this);
#ifdef _LP64
  emit_byte(0x67); // addr32
  prefix(src, dst);
#endif // LP64
  emit_byte(0x8D);
  emit_operand(dst, src);
}

void Assembler::lock() {
  if (Atomics & 1) {
     // Emit either nothing, a NOP, or a NOP: prefix
     emit_byte(0x90) ;
  } else {
     emit_byte(0xF0);
  }
}

void Assembler::lzcntl(Register dst, Register src) {
  assert(VM_Version::supports_lzcnt(), "encoding is treated as BSR");
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xBD);
  emit_byte(0xC0 | encode);
}

// Emit mfence instruction
void Assembler::mfence() {
  NOT_LP64(assert(VM_Version::supports_sse2(), "unsupported");)
  emit_byte( 0x0F );
  emit_byte( 0xAE );
  emit_byte( 0xF0 );
}

void Assembler::mov(Register dst, Register src) {
  LP64_ONLY(movq(dst, src)) NOT_LP64(movl(dst, src));
}

void Assembler::movapd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  int dstenc = dst->encoding();
  int srcenc = src->encoding();
  emit_byte(0x66);
  if (dstenc < 8) {
    if (srcenc >= 8) {
      prefix(REX_B);
      srcenc -= 8;
    }
  } else {
    if (srcenc < 8) {
      prefix(REX_R);
    } else {
      prefix(REX_RB);
      srcenc -= 8;
    }
    dstenc -= 8;
  }
  emit_byte(0x0F);
  emit_byte(0x28);
  emit_byte(0xC0 | dstenc << 3 | srcenc);
}

void Assembler::movaps(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  int dstenc = dst->encoding();
  int srcenc = src->encoding();
  if (dstenc < 8) {
    if (srcenc >= 8) {
      prefix(REX_B);
      srcenc -= 8;
    }
  } else {
    if (srcenc < 8) {
      prefix(REX_R);
    } else {
      prefix(REX_RB);
      srcenc -= 8;
    }
    dstenc -= 8;
  }
  emit_byte(0x0F);
  emit_byte(0x28);
  emit_byte(0xC0 | dstenc << 3 | srcenc);
}

void Assembler::movb(Register dst, Address src) {
  NOT_LP64(assert(dst->has_byte_register(), "must have byte register"));
  InstructionMark im(this);
  prefix(src, dst, true);
  emit_byte(0x8A);
  emit_operand(dst, src);
}


void Assembler::movb(Address dst, int imm8) {
  InstructionMark im(this);
   prefix(dst);
  emit_byte(0xC6);
  emit_operand(rax, dst, 1);
  emit_byte(imm8);
}


void Assembler::movb(Address dst, Register src) {
  assert(src->has_byte_register(), "must have byte register");
  InstructionMark im(this);
  prefix(dst, src, true);
  emit_byte(0x88);
  emit_operand(src, dst);
}

void Assembler::movdl(XMMRegister dst, Register src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0x66);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x6E);
  emit_byte(0xC0 | encode);
}

void Assembler::movdl(Register dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0x66);
  // swap src/dst to get correct prefix
  int encode = prefix_and_encode(src->encoding(), dst->encoding());
  emit_byte(0x0F);
  emit_byte(0x7E);
  emit_byte(0xC0 | encode);
}

void Assembler::movdqa(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0x66);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x6F);
  emit_operand(dst, src);
}

void Assembler::movdqa(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0x66);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x6F);
  emit_byte(0xC0 | encode);
}

void Assembler::movdqa(Address dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0x66);
  prefix(dst, src);
  emit_byte(0x0F);
  emit_byte(0x7F);
  emit_operand(src, dst);
}

void Assembler::movdqu(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x6F);
  emit_operand(dst, src);
}

void Assembler::movdqu(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0xF3);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x6F);
  emit_byte(0xC0 | encode);
}

void Assembler::movdqu(Address dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(dst, src);
  emit_byte(0x0F);
  emit_byte(0x7F);
  emit_operand(src, dst);
}

// Uses zero extension on 64bit

void Assembler::movl(Register dst, int32_t imm32) {
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0xB8 | encode);
  emit_long(imm32);
}

void Assembler::movl(Register dst, Register src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x8B);
  emit_byte(0xC0 | encode);
}

void Assembler::movl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x8B);
  emit_operand(dst, src);
}

void Assembler::movl(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefix(dst);
  emit_byte(0xC7);
  emit_operand(rax, dst, 4);
  emit_long(imm32);
}

void Assembler::movl(Address dst, Register src) {
  InstructionMark im(this);
  prefix(dst, src);
  emit_byte(0x89);
  emit_operand(src, dst);
}

// New cpus require to use movsd and movss to avoid partial register stall
// when loading from memory. But for old Opteron use movlpd instead of movsd.
// The selection is done in MacroAssembler::movdbl() and movflt().
void Assembler::movlpd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0x66);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x12);
  emit_operand(dst, src);
}

void Assembler::movq( MMXRegister dst, Address src ) {
  assert( VM_Version::supports_mmx(), "" );
  emit_byte(0x0F);
  emit_byte(0x6F);
  emit_operand(dst, src);
}

void Assembler::movq( Address dst, MMXRegister src ) {
  assert( VM_Version::supports_mmx(), "" );
  emit_byte(0x0F);
  emit_byte(0x7F);
  // workaround gcc (3.2.1-7a) bug
  // In that version of gcc with only an emit_operand(MMX, Address)
  // gcc will tail jump and try and reverse the parameters completely
  // obliterating dst in the process. By having a version available
  // that doesn't need to swap the args at the tail jump the bug is
  // avoided.
  emit_operand(dst, src);
}

void Assembler::movq(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x7E);
  emit_operand(dst, src);
}

void Assembler::movq(Address dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0x66);
  prefix(dst, src);
  emit_byte(0x0F);
  emit_byte(0xD6);
  emit_operand(src, dst);
}

void Assembler::movsbl(Register dst, Address src) { // movsxb
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0xBE);
  emit_operand(dst, src);
}

void Assembler::movsbl(Register dst, Register src) { // movsxb
  NOT_LP64(assert(src->has_byte_register(), "must have byte register"));
  int encode = prefix_and_encode(dst->encoding(), src->encoding(), true);
  emit_byte(0x0F);
  emit_byte(0xBE);
  emit_byte(0xC0 | encode);
}

void Assembler::movsd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x10);
  emit_byte(0xC0 | encode);
}

void Assembler::movsd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0xF2);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x10);
  emit_operand(dst, src);
}

void Assembler::movsd(Address dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0xF2);
  prefix(dst, src);
  emit_byte(0x0F);
  emit_byte(0x11);
  emit_operand(src, dst);
}

void Assembler::movss(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x10);
  emit_byte(0xC0 | encode);
}

void Assembler::movss(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x10);
  emit_operand(dst, src);
}

void Assembler::movss(Address dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(dst, src);
  emit_byte(0x0F);
  emit_byte(0x11);
  emit_operand(src, dst);
}

void Assembler::movswl(Register dst, Address src) { // movsxw
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0xBF);
  emit_operand(dst, src);
}

void Assembler::movswl(Register dst, Register src) { // movsxw
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xBF);
  emit_byte(0xC0 | encode);
}

void Assembler::movw(Address dst, int imm16) {
  InstructionMark im(this);

  emit_byte(0x66); // switch to 16-bit mode
  prefix(dst);
  emit_byte(0xC7);
  emit_operand(rax, dst, 2);
  emit_word(imm16);
}

void Assembler::movw(Register dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x66);
  prefix(src, dst);
  emit_byte(0x8B);
  emit_operand(dst, src);
}

void Assembler::movw(Address dst, Register src) {
  InstructionMark im(this);
  emit_byte(0x66);
  prefix(dst, src);
  emit_byte(0x89);
  emit_operand(src, dst);
}

void Assembler::movzbl(Register dst, Address src) { // movzxb
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0xB6);
  emit_operand(dst, src);
}

void Assembler::movzbl(Register dst, Register src) { // movzxb
  NOT_LP64(assert(src->has_byte_register(), "must have byte register"));
  int encode = prefix_and_encode(dst->encoding(), src->encoding(), true);
  emit_byte(0x0F);
  emit_byte(0xB6);
  emit_byte(0xC0 | encode);
}

void Assembler::movzwl(Register dst, Address src) { // movzxw
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0xB7);
  emit_operand(dst, src);
}

void Assembler::movzwl(Register dst, Register src) { // movzxw
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xB7);
  emit_byte(0xC0 | encode);
}

void Assembler::mull(Address src) {
  InstructionMark im(this);
  prefix(src);
  emit_byte(0xF7);
  emit_operand(rsp, src);
}

void Assembler::mull(Register src) {
  int encode = prefix_and_encode(src->encoding());
  emit_byte(0xF7);
  emit_byte(0xE0 | encode);
}

void Assembler::mulsd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0xF2);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x59);
  emit_operand(dst, src);
}

void Assembler::mulsd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x59);
  emit_byte(0xC0 | encode);
}

void Assembler::mulss(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x59);
  emit_operand(dst, src);
}

void Assembler::mulss(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x59);
  emit_byte(0xC0 | encode);
}

void Assembler::negl(Register dst) {
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0xF7);
  emit_byte(0xD8 | encode);
}

void Assembler::nop(int i) {
#ifdef ASSERT
  assert(i > 0, " ");
  // The fancy nops aren't currently recognized by debuggers making it a
  // pain to disassemble code while debugging. If asserts are on clearly
  // speed is not an issue so simply use the single byte traditional nop
  // to do alignment.

  for (; i > 0 ; i--) emit_byte(0x90);
  return;

#endif // ASSERT

  if (UseAddressNop && VM_Version::is_intel()) {
    //
    // Using multi-bytes nops "0x0F 0x1F [address]" for Intel
    //  1: 0x90
    //  2: 0x66 0x90
    //  3: 0x66 0x66 0x90 (don't use "0x0F 0x1F 0x00" - need patching safe padding)
    //  4: 0x0F 0x1F 0x40 0x00
    //  5: 0x0F 0x1F 0x44 0x00 0x00
    //  6: 0x66 0x0F 0x1F 0x44 0x00 0x00
    //  7: 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00
    //  8: 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
    //  9: 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
    // 10: 0x66 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
    // 11: 0x66 0x66 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00

    // The rest coding is Intel specific - don't use consecutive address nops

    // 12: 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00 0x66 0x66 0x66 0x90
    // 13: 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00 0x66 0x66 0x66 0x90
    // 14: 0x66 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00 0x66 0x66 0x66 0x90
    // 15: 0x66 0x66 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00 0x66 0x66 0x66 0x90

    while(i >= 15) {
      // For Intel don't generate consecutive addess nops (mix with regular nops)
      i -= 15;
      emit_byte(0x66);   // size prefix
      emit_byte(0x66);   // size prefix
      emit_byte(0x66);   // size prefix
      addr_nop_8();
      emit_byte(0x66);   // size prefix
      emit_byte(0x66);   // size prefix
      emit_byte(0x66);   // size prefix
      emit_byte(0x90);   // nop
    }
    switch (i) {
      case 14:
        emit_byte(0x66); // size prefix
      case 13:
        emit_byte(0x66); // size prefix
      case 12:
        addr_nop_8();
        emit_byte(0x66); // size prefix
        emit_byte(0x66); // size prefix
        emit_byte(0x66); // size prefix
        emit_byte(0x90); // nop
        break;
      case 11:
        emit_byte(0x66); // size prefix
      case 10:
        emit_byte(0x66); // size prefix
      case 9:
        emit_byte(0x66); // size prefix
      case 8:
        addr_nop_8();
        break;
      case 7:
        addr_nop_7();
        break;
      case 6:
        emit_byte(0x66); // size prefix
      case 5:
        addr_nop_5();
        break;
      case 4:
        addr_nop_4();
        break;
      case 3:
        // Don't use "0x0F 0x1F 0x00" - need patching safe padding
        emit_byte(0x66); // size prefix
      case 2:
        emit_byte(0x66); // size prefix
      case 1:
        emit_byte(0x90); // nop
        break;
      default:
        assert(i == 0, " ");
    }
    return;
  }
  if (UseAddressNop && VM_Version::is_amd()) {
    //
    // Using multi-bytes nops "0x0F 0x1F [address]" for AMD.
    //  1: 0x90
    //  2: 0x66 0x90
    //  3: 0x66 0x66 0x90 (don't use "0x0F 0x1F 0x00" - need patching safe padding)
    //  4: 0x0F 0x1F 0x40 0x00
    //  5: 0x0F 0x1F 0x44 0x00 0x00
    //  6: 0x66 0x0F 0x1F 0x44 0x00 0x00
    //  7: 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00
    //  8: 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
    //  9: 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
    // 10: 0x66 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
    // 11: 0x66 0x66 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00

    // The rest coding is AMD specific - use consecutive address nops

    // 12: 0x66 0x0F 0x1F 0x44 0x00 0x00 0x66 0x0F 0x1F 0x44 0x00 0x00
    // 13: 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00 0x66 0x0F 0x1F 0x44 0x00 0x00
    // 14: 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00
    // 15: 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00
    // 16: 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
    //     Size prefixes (0x66) are added for larger sizes

    while(i >= 22) {
      i -= 11;
      emit_byte(0x66); // size prefix
      emit_byte(0x66); // size prefix
      emit_byte(0x66); // size prefix
      addr_nop_8();
    }
    // Generate first nop for size between 21-12
    switch (i) {
      case 21:
        i -= 1;
        emit_byte(0x66); // size prefix
      case 20:
      case 19:
        i -= 1;
        emit_byte(0x66); // size prefix
      case 18:
      case 17:
        i -= 1;
        emit_byte(0x66); // size prefix
      case 16:
      case 15:
        i -= 8;
        addr_nop_8();
        break;
      case 14:
      case 13:
        i -= 7;
        addr_nop_7();
        break;
      case 12:
        i -= 6;
        emit_byte(0x66); // size prefix
        addr_nop_5();
        break;
      default:
        assert(i < 12, " ");
    }

    // Generate second nop for size between 11-1
    switch (i) {
      case 11:
        emit_byte(0x66); // size prefix
      case 10:
        emit_byte(0x66); // size prefix
      case 9:
        emit_byte(0x66); // size prefix
      case 8:
        addr_nop_8();
        break;
      case 7:
        addr_nop_7();
        break;
      case 6:
        emit_byte(0x66); // size prefix
      case 5:
        addr_nop_5();
        break;
      case 4:
        addr_nop_4();
        break;
      case 3:
        // Don't use "0x0F 0x1F 0x00" - need patching safe padding
        emit_byte(0x66); // size prefix
      case 2:
        emit_byte(0x66); // size prefix
      case 1:
        emit_byte(0x90); // nop
        break;
      default:
        assert(i == 0, " ");
    }
    return;
  }

  // Using nops with size prefixes "0x66 0x90".
  // From AMD Optimization Guide:
  //  1: 0x90
  //  2: 0x66 0x90
  //  3: 0x66 0x66 0x90
  //  4: 0x66 0x66 0x66 0x90
  //  5: 0x66 0x66 0x90 0x66 0x90
  //  6: 0x66 0x66 0x90 0x66 0x66 0x90
  //  7: 0x66 0x66 0x66 0x90 0x66 0x66 0x90
  //  8: 0x66 0x66 0x66 0x90 0x66 0x66 0x66 0x90
  //  9: 0x66 0x66 0x90 0x66 0x66 0x90 0x66 0x66 0x90
  // 10: 0x66 0x66 0x66 0x90 0x66 0x66 0x90 0x66 0x66 0x90
  //
  while(i > 12) {
    i -= 4;
    emit_byte(0x66); // size prefix
    emit_byte(0x66);
    emit_byte(0x66);
    emit_byte(0x90); // nop
  }
  // 1 - 12 nops
  if(i > 8) {
    if(i > 9) {
      i -= 1;
      emit_byte(0x66);
    }
    i -= 3;
    emit_byte(0x66);
    emit_byte(0x66);
    emit_byte(0x90);
  }
  // 1 - 8 nops
  if(i > 4) {
    if(i > 6) {
      i -= 1;
      emit_byte(0x66);
    }
    i -= 3;
    emit_byte(0x66);
    emit_byte(0x66);
    emit_byte(0x90);
  }
  switch (i) {
    case 4:
      emit_byte(0x66);
    case 3:
      emit_byte(0x66);
    case 2:
      emit_byte(0x66);
    case 1:
      emit_byte(0x90);
      break;
    default:
      assert(i == 0, " ");
  }
}

void Assembler::notl(Register dst) {
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0xF7);
  emit_byte(0xD0 | encode );
}

void Assembler::orl(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefix(dst);
  emit_byte(0x81);
  emit_operand(rcx, dst, 4);
  emit_long(imm32);
}

void Assembler::orl(Register dst, int32_t imm32) {
  prefix(dst);
  emit_arith(0x81, 0xC8, dst, imm32);
}


void Assembler::orl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x0B);
  emit_operand(dst, src);
}


void Assembler::orl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x0B, 0xC0, dst, src);
}

void Assembler::pcmpestri(XMMRegister dst, Address src, int imm8) {
  assert(VM_Version::supports_sse4_2(), "");

  InstructionMark im(this);
  emit_byte(0x66);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x3A);
  emit_byte(0x61);
  emit_operand(dst, src);
  emit_byte(imm8);
}

void Assembler::pcmpestri(XMMRegister dst, XMMRegister src, int imm8) {
  assert(VM_Version::supports_sse4_2(), "");

  emit_byte(0x66);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x3A);
  emit_byte(0x61);
  emit_byte(0xC0 | encode);
  emit_byte(imm8);
}

// generic
void Assembler::pop(Register dst) {
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0x58 | encode);
}

void Assembler::popcntl(Register dst, Address src) {
  assert(VM_Version::supports_popcnt(), "must support");
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0xB8);
  emit_operand(dst, src);
}

void Assembler::popcntl(Register dst, Register src) {
  assert(VM_Version::supports_popcnt(), "must support");
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xB8);
  emit_byte(0xC0 | encode);
}

void Assembler::popf() {
  emit_byte(0x9D);
}

#ifndef _LP64 // no 32bit push/pop on amd64
void Assembler::popl(Address dst) {
  // NOTE: this will adjust stack by 8byte on 64bits
  InstructionMark im(this);
  prefix(dst);
  emit_byte(0x8F);
  emit_operand(rax, dst);
}
#endif

void Assembler::prefetch_prefix(Address src) {
  prefix(src);
  emit_byte(0x0F);
}

void Assembler::prefetchnta(Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), "must support"));
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_byte(0x18);
  emit_operand(rax, src); // 0, src
}

void Assembler::prefetchr(Address src) {
  NOT_LP64(assert(VM_Version::supports_3dnow(), "must support"));
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_byte(0x0D);
  emit_operand(rax, src); // 0, src
}

void Assembler::prefetcht0(Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), "must support"));
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_byte(0x18);
  emit_operand(rcx, src); // 1, src
}

void Assembler::prefetcht1(Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), "must support"));
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_byte(0x18);
  emit_operand(rdx, src); // 2, src
}

void Assembler::prefetcht2(Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), "must support"));
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_byte(0x18);
  emit_operand(rbx, src); // 3, src
}

void Assembler::prefetchw(Address src) {
  NOT_LP64(assert(VM_Version::supports_3dnow(), "must support"));
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_byte(0x0D);
  emit_operand(rcx, src); // 1, src
}

void Assembler::prefix(Prefix p) {
  a_byte(p);
}

void Assembler::pshufd(XMMRegister dst, XMMRegister src, int mode) {
  assert(isByte(mode), "invalid value");
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));

  emit_byte(0x66);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x70);
  emit_byte(0xC0 | encode);
  emit_byte(mode & 0xFF);

}

void Assembler::pshufd(XMMRegister dst, Address src, int mode) {
  assert(isByte(mode), "invalid value");
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));

  InstructionMark im(this);
  emit_byte(0x66);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x70);
  emit_operand(dst, src);
  emit_byte(mode & 0xFF);
}

void Assembler::pshuflw(XMMRegister dst, XMMRegister src, int mode) {
  assert(isByte(mode), "invalid value");
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));

  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x70);
  emit_byte(0xC0 | encode);
  emit_byte(mode & 0xFF);
}

void Assembler::pshuflw(XMMRegister dst, Address src, int mode) {
  assert(isByte(mode), "invalid value");
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));

  InstructionMark im(this);
  emit_byte(0xF2);
  prefix(src, dst); // QQ new
  emit_byte(0x0F);
  emit_byte(0x70);
  emit_operand(dst, src);
  emit_byte(mode & 0xFF);
}

void Assembler::psrlq(XMMRegister dst, int shift) {
  // HMM Table D-1 says sse2 or mmx
  NOT_LP64(assert(VM_Version::supports_sse(), ""));

  int encode = prefixq_and_encode(xmm2->encoding(), dst->encoding());
  emit_byte(0x66);
  emit_byte(0x0F);
  emit_byte(0x73);
  emit_byte(0xC0 | encode);
  emit_byte(shift);
}

void Assembler::ptest(XMMRegister dst, Address src) {
  assert(VM_Version::supports_sse4_1(), "");

  InstructionMark im(this);
  emit_byte(0x66);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x38);
  emit_byte(0x17);
  emit_operand(dst, src);
}

void Assembler::ptest(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_sse4_1(), "");

  emit_byte(0x66);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x38);
  emit_byte(0x17);
  emit_byte(0xC0 | encode);
}

void Assembler::punpcklbw(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0x66);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x60);
  emit_byte(0xC0 | encode);
}

void Assembler::push(int32_t imm32) {
  // in 64bits we push 64bits onto the stack but only
  // take a 32bit immediate
  emit_byte(0x68);
  emit_long(imm32);
}

void Assembler::push(Register src) {
  int encode = prefix_and_encode(src->encoding());

  emit_byte(0x50 | encode);
}

void Assembler::pushf() {
  emit_byte(0x9C);
}

#ifndef _LP64 // no 32bit push/pop on amd64
void Assembler::pushl(Address src) {
  // Note this will push 64bit on 64bit
  InstructionMark im(this);
  prefix(src);
  emit_byte(0xFF);
  emit_operand(rsi, src);
}
#endif

void Assembler::pxor(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0x66);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0xEF);
  emit_operand(dst, src);
}

void Assembler::pxor(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0x66);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xEF);
  emit_byte(0xC0 | encode);
}

void Assembler::rcll(Register dst, int imm8) {
  assert(isShiftCount(imm8), "illegal shift count");
  int encode = prefix_and_encode(dst->encoding());
  if (imm8 == 1) {
    emit_byte(0xD1);
    emit_byte(0xD0 | encode);
  } else {
    emit_byte(0xC1);
    emit_byte(0xD0 | encode);
    emit_byte(imm8);
  }
}

// copies data from [esi] to [edi] using rcx pointer sized words
// generic
void Assembler::rep_mov() {
  emit_byte(0xF3);
  // MOVSQ
  LP64_ONLY(prefix(REX_W));
  emit_byte(0xA5);
}

// sets rcx pointer sized words with rax, value at [edi]
// generic
void Assembler::rep_set() { // rep_set
  emit_byte(0xF3);
  // STOSQ
  LP64_ONLY(prefix(REX_W));
  emit_byte(0xAB);
}

// scans rcx pointer sized words at [edi] for occurance of rax,
// generic
void Assembler::repne_scan() { // repne_scan
  emit_byte(0xF2);
  // SCASQ
  LP64_ONLY(prefix(REX_W));
  emit_byte(0xAF);
}

#ifdef _LP64
// scans rcx 4 byte words at [edi] for occurance of rax,
// generic
void Assembler::repne_scanl() { // repne_scan
  emit_byte(0xF2);
  // SCASL
  emit_byte(0xAF);
}
#endif

void Assembler::ret(int imm16) {
  if (imm16 == 0) {
    emit_byte(0xC3);
  } else {
    emit_byte(0xC2);
    emit_word(imm16);
  }
}

void Assembler::sahf() {
#ifdef _LP64
  // Not supported in 64bit mode
  ShouldNotReachHere();
#endif
  emit_byte(0x9E);
}

void Assembler::sarl(Register dst, int imm8) {
  int encode = prefix_and_encode(dst->encoding());
  assert(isShiftCount(imm8), "illegal shift count");
  if (imm8 == 1) {
    emit_byte(0xD1);
    emit_byte(0xF8 | encode);
  } else {
    emit_byte(0xC1);
    emit_byte(0xF8 | encode);
    emit_byte(imm8);
  }
}

void Assembler::sarl(Register dst) {
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0xD3);
  emit_byte(0xF8 | encode);
}

void Assembler::sbbl(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefix(dst);
  emit_arith_operand(0x81, rbx, dst, imm32);
}

void Assembler::sbbl(Register dst, int32_t imm32) {
  prefix(dst);
  emit_arith(0x81, 0xD8, dst, imm32);
}


void Assembler::sbbl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x1B);
  emit_operand(dst, src);
}

void Assembler::sbbl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x1B, 0xC0, dst, src);
}

void Assembler::setb(Condition cc, Register dst) {
  assert(0 <= cc && cc < 16, "illegal cc");
  int encode = prefix_and_encode(dst->encoding(), true);
  emit_byte(0x0F);
  emit_byte(0x90 | cc);
  emit_byte(0xC0 | encode);
}

void Assembler::shll(Register dst, int imm8) {
  assert(isShiftCount(imm8), "illegal shift count");
  int encode = prefix_and_encode(dst->encoding());
  if (imm8 == 1 ) {
    emit_byte(0xD1);
    emit_byte(0xE0 | encode);
  } else {
    emit_byte(0xC1);
    emit_byte(0xE0 | encode);
    emit_byte(imm8);
  }
}

void Assembler::shll(Register dst) {
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0xD3);
  emit_byte(0xE0 | encode);
}

void Assembler::shrl(Register dst, int imm8) {
  assert(isShiftCount(imm8), "illegal shift count");
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0xC1);
  emit_byte(0xE8 | encode);
  emit_byte(imm8);
}

void Assembler::shrl(Register dst) {
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0xD3);
  emit_byte(0xE8 | encode);
}

// copies a single word from [esi] to [edi]
void Assembler::smovl() {
  emit_byte(0xA5);
}

void Assembler::sqrtsd(XMMRegister dst, XMMRegister src) {
  // HMM Table D-1 says sse2
  // NOT_LP64(assert(VM_Version::supports_sse(), ""));
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x51);
  emit_byte(0xC0 | encode);
}

void Assembler::stmxcsr( Address dst) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  InstructionMark im(this);
  prefix(dst);
  emit_byte(0x0F);
  emit_byte(0xAE);
  emit_operand(as_Register(3), dst);
}

void Assembler::subl(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefix(dst);
  if (is8bit(imm32)) {
    emit_byte(0x83);
    emit_operand(rbp, dst, 1);
    emit_byte(imm32 & 0xFF);
  } else {
    emit_byte(0x81);
    emit_operand(rbp, dst, 4);
    emit_long(imm32);
  }
}

void Assembler::subl(Register dst, int32_t imm32) {
  prefix(dst);
  emit_arith(0x81, 0xE8, dst, imm32);
}

void Assembler::subl(Address dst, Register src) {
  InstructionMark im(this);
  prefix(dst, src);
  emit_byte(0x29);
  emit_operand(src, dst);
}

void Assembler::subl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x2B);
  emit_operand(dst, src);
}

void Assembler::subl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x2B, 0xC0, dst, src);
}

void Assembler::subsd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x5C);
  emit_byte(0xC0 | encode);
}

void Assembler::subsd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0xF2);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x5C);
  emit_operand(dst, src);
}

void Assembler::subss(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x5C);
  emit_byte(0xC0 | encode);
}

void Assembler::subss(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x5C);
  emit_operand(dst, src);
}

void Assembler::testb(Register dst, int imm8) {
  NOT_LP64(assert(dst->has_byte_register(), "must have byte register"));
  (void) prefix_and_encode(dst->encoding(), true);
  emit_arith_b(0xF6, 0xC0, dst, imm8);
}

void Assembler::testl(Register dst, int32_t imm32) {
  // not using emit_arith because test
  // doesn't support sign-extension of
  // 8bit operands
  int encode = dst->encoding();
  if (encode == 0) {
    emit_byte(0xA9);
  } else {
    encode = prefix_and_encode(encode);
    emit_byte(0xF7);
    emit_byte(0xC0 | encode);
  }
  emit_long(imm32);
}

void Assembler::testl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x85, 0xC0, dst, src);
}

void Assembler::testl(Register dst, Address  src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x85);
  emit_operand(dst, src);
}

void Assembler::ucomisd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0x66);
  ucomiss(dst, src);
}

void Assembler::ucomisd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0x66);
  ucomiss(dst, src);
}

void Assembler::ucomiss(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));

  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x2E);
  emit_operand(dst, src);
}

void Assembler::ucomiss(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2E);
  emit_byte(0xC0 | encode);
}


void Assembler::xaddl(Address dst, Register src) {
  InstructionMark im(this);
  prefix(dst, src);
  emit_byte(0x0F);
  emit_byte(0xC1);
  emit_operand(src, dst);
}

void Assembler::xchgl(Register dst, Address src) { // xchg
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x87);
  emit_operand(dst, src);
}

void Assembler::xchgl(Register dst, Register src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x87);
  emit_byte(0xc0 | encode);
}

void Assembler::xorl(Register dst, int32_t imm32) {
  prefix(dst);
  emit_arith(0x81, 0xF0, dst, imm32);
}

void Assembler::xorl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x33);
  emit_operand(dst, src);
}

void Assembler::xorl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x33, 0xC0, dst, src);
}

void Assembler::xorpd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0x66);
  xorps(dst, src);
}

void Assembler::xorpd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0x66);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x57);
  emit_operand(dst, src);
}


void Assembler::xorps(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x57);
  emit_byte(0xC0 | encode);
}

void Assembler::xorps(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x57);
  emit_operand(dst, src);
}

#ifndef _LP64
// 32bit only pieces of the assembler

void Assembler::cmp_literal32(Register src1, int32_t imm32, RelocationHolder const& rspec) {
  // NO PREFIX AS NEVER 64BIT
  InstructionMark im(this);
  emit_byte(0x81);
  emit_byte(0xF8 | src1->encoding());
  emit_data(imm32, rspec, 0);
}

void Assembler::cmp_literal32(Address src1, int32_t imm32, RelocationHolder const& rspec) {
  // NO PREFIX AS NEVER 64BIT (not even 32bit versions of 64bit regs
  InstructionMark im(this);
  emit_byte(0x81);
  emit_operand(rdi, src1);
  emit_data(imm32, rspec, 0);
}

// The 64-bit (32bit platform) cmpxchg compares the value at adr with the contents of rdx:rax,
// and stores rcx:rbx into adr if so; otherwise, the value at adr is loaded
// into rdx:rax.  The ZF is set if the compared values were equal, and cleared otherwise.
void Assembler::cmpxchg8(Address adr) {
  InstructionMark im(this);
  emit_byte(0x0F);
  emit_byte(0xc7);
  emit_operand(rcx, adr);
}

void Assembler::decl(Register dst) {
  // Don't use it directly. Use MacroAssembler::decrementl() instead.
 emit_byte(0x48 | dst->encoding());
}

#endif // _LP64

// 64bit typically doesn't use the x87 but needs to for the trig funcs

void Assembler::fabs() {
  emit_byte(0xD9);
  emit_byte(0xE1);
}

void Assembler::fadd(int i) {
  emit_farith(0xD8, 0xC0, i);
}

void Assembler::fadd_d(Address src) {
  InstructionMark im(this);
  emit_byte(0xDC);
  emit_operand32(rax, src);
}

void Assembler::fadd_s(Address src) {
  InstructionMark im(this);
  emit_byte(0xD8);
  emit_operand32(rax, src);
}

void Assembler::fadda(int i) {
  emit_farith(0xDC, 0xC0, i);
}

void Assembler::faddp(int i) {
  emit_farith(0xDE, 0xC0, i);
}

void Assembler::fchs() {
  emit_byte(0xD9);
  emit_byte(0xE0);
}

void Assembler::fcom(int i) {
  emit_farith(0xD8, 0xD0, i);
}

void Assembler::fcomp(int i) {
  emit_farith(0xD8, 0xD8, i);
}

void Assembler::fcomp_d(Address src) {
  InstructionMark im(this);
  emit_byte(0xDC);
  emit_operand32(rbx, src);
}

void Assembler::fcomp_s(Address src) {
  InstructionMark im(this);
  emit_byte(0xD8);
  emit_operand32(rbx, src);
}

void Assembler::fcompp() {
  emit_byte(0xDE);
  emit_byte(0xD9);
}

void Assembler::fcos() {
  emit_byte(0xD9);
  emit_byte(0xFF);
}

void Assembler::fdecstp() {
  emit_byte(0xD9);
  emit_byte(0xF6);
}

void Assembler::fdiv(int i) {
  emit_farith(0xD8, 0xF0, i);
}

void Assembler::fdiv_d(Address src) {
  InstructionMark im(this);
  emit_byte(0xDC);
  emit_operand32(rsi, src);
}

void Assembler::fdiv_s(Address src) {
  InstructionMark im(this);
  emit_byte(0xD8);
  emit_operand32(rsi, src);
}

void Assembler::fdiva(int i) {
  emit_farith(0xDC, 0xF8, i);
}

// Note: The Intel manual (Pentium Processor User's Manual, Vol.3, 1994)
//       is erroneous for some of the floating-point instructions below.

void Assembler::fdivp(int i) {
  emit_farith(0xDE, 0xF8, i);                    // ST(0) <- ST(0) / ST(1) and pop (Intel manual wrong)
}

void Assembler::fdivr(int i) {
  emit_farith(0xD8, 0xF8, i);
}

void Assembler::fdivr_d(Address src) {
  InstructionMark im(this);
  emit_byte(0xDC);
  emit_operand32(rdi, src);
}

void Assembler::fdivr_s(Address src) {
  InstructionMark im(this);
  emit_byte(0xD8);
  emit_operand32(rdi, src);
}

void Assembler::fdivra(int i) {
  emit_farith(0xDC, 0xF0, i);
}

void Assembler::fdivrp(int i) {
  emit_farith(0xDE, 0xF0, i);                    // ST(0) <- ST(1) / ST(0) and pop (Intel manual wrong)
}

void Assembler::ffree(int i) {
  emit_farith(0xDD, 0xC0, i);
}

void Assembler::fild_d(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDF);
  emit_operand32(rbp, adr);
}

void Assembler::fild_s(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDB);
  emit_operand32(rax, adr);
}

void Assembler::fincstp() {
  emit_byte(0xD9);
  emit_byte(0xF7);
}

void Assembler::finit() {
  emit_byte(0x9B);
  emit_byte(0xDB);
  emit_byte(0xE3);
}

void Assembler::fist_s(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDB);
  emit_operand32(rdx, adr);
}

void Assembler::fistp_d(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDF);
  emit_operand32(rdi, adr);
}

void Assembler::fistp_s(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDB);
  emit_operand32(rbx, adr);
}

void Assembler::fld1() {
  emit_byte(0xD9);
  emit_byte(0xE8);
}

void Assembler::fld_d(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDD);
  emit_operand32(rax, adr);
}

void Assembler::fld_s(Address adr) {
  InstructionMark im(this);
  emit_byte(0xD9);
  emit_operand32(rax, adr);
}


void Assembler::fld_s(int index) {
  emit_farith(0xD9, 0xC0, index);
}

void Assembler::fld_x(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDB);
  emit_operand32(rbp, adr);
}

void Assembler::fldcw(Address src) {
  InstructionMark im(this);
  emit_byte(0xd9);
  emit_operand32(rbp, src);
}

void Assembler::fldenv(Address src) {
  InstructionMark im(this);
  emit_byte(0xD9);
  emit_operand32(rsp, src);
}

void Assembler::fldlg2() {
  emit_byte(0xD9);
  emit_byte(0xEC);
}

void Assembler::fldln2() {
  emit_byte(0xD9);
  emit_byte(0xED);
}

void Assembler::fldz() {
  emit_byte(0xD9);
  emit_byte(0xEE);
}

void Assembler::flog() {
  fldln2();
  fxch();
  fyl2x();
}

void Assembler::flog10() {
  fldlg2();
  fxch();
  fyl2x();
}

void Assembler::fmul(int i) {
  emit_farith(0xD8, 0xC8, i);
}

void Assembler::fmul_d(Address src) {
  InstructionMark im(this);
  emit_byte(0xDC);
  emit_operand32(rcx, src);
}

void Assembler::fmul_s(Address src) {
  InstructionMark im(this);
  emit_byte(0xD8);
  emit_operand32(rcx, src);
}

void Assembler::fmula(int i) {
  emit_farith(0xDC, 0xC8, i);
}

void Assembler::fmulp(int i) {
  emit_farith(0xDE, 0xC8, i);
}

void Assembler::fnsave(Address dst) {
  InstructionMark im(this);
  emit_byte(0xDD);
  emit_operand32(rsi, dst);
}

void Assembler::fnstcw(Address src) {
  InstructionMark im(this);
  emit_byte(0x9B);
  emit_byte(0xD9);
  emit_operand32(rdi, src);
}

void Assembler::fnstsw_ax() {
  emit_byte(0xdF);
  emit_byte(0xE0);
}

void Assembler::fprem() {
  emit_byte(0xD9);
  emit_byte(0xF8);
}

void Assembler::fprem1() {
  emit_byte(0xD9);
  emit_byte(0xF5);
}

void Assembler::frstor(Address src) {
  InstructionMark im(this);
  emit_byte(0xDD);
  emit_operand32(rsp, src);
}

void Assembler::fsin() {
  emit_byte(0xD9);
  emit_byte(0xFE);
}

void Assembler::fsqrt() {
  emit_byte(0xD9);
  emit_byte(0xFA);
}

void Assembler::fst_d(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDD);
  emit_operand32(rdx, adr);
}

void Assembler::fst_s(Address adr) {
  InstructionMark im(this);
  emit_byte(0xD9);
  emit_operand32(rdx, adr);
}

void Assembler::fstp_d(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDD);
  emit_operand32(rbx, adr);
}

void Assembler::fstp_d(int index) {
  emit_farith(0xDD, 0xD8, index);
}

void Assembler::fstp_s(Address adr) {
  InstructionMark im(this);
  emit_byte(0xD9);
  emit_operand32(rbx, adr);
}

void Assembler::fstp_x(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDB);
  emit_operand32(rdi, adr);
}

void Assembler::fsub(int i) {
  emit_farith(0xD8, 0xE0, i);
}

void Assembler::fsub_d(Address src) {
  InstructionMark im(this);
  emit_byte(0xDC);
  emit_operand32(rsp, src);
}

void Assembler::fsub_s(Address src) {
  InstructionMark im(this);
  emit_byte(0xD8);
  emit_operand32(rsp, src);
}

void Assembler::fsuba(int i) {
  emit_farith(0xDC, 0xE8, i);
}

void Assembler::fsubp(int i) {
  emit_farith(0xDE, 0xE8, i);                    // ST(0) <- ST(0) - ST(1) and pop (Intel manual wrong)
}

void Assembler::fsubr(int i) {
  emit_farith(0xD8, 0xE8, i);
}

void Assembler::fsubr_d(Address src) {
  InstructionMark im(this);
  emit_byte(0xDC);
  emit_operand32(rbp, src);
}

void Assembler::fsubr_s(Address src) {
  InstructionMark im(this);
  emit_byte(0xD8);
  emit_operand32(rbp, src);
}

void Assembler::fsubra(int i) {
  emit_farith(0xDC, 0xE0, i);
}

void Assembler::fsubrp(int i) {
  emit_farith(0xDE, 0xE0, i);                    // ST(0) <- ST(1) - ST(0) and pop (Intel manual wrong)
}

void Assembler::ftan() {
  emit_byte(0xD9);
  emit_byte(0xF2);
  emit_byte(0xDD);
  emit_byte(0xD8);
}

void Assembler::ftst() {
  emit_byte(0xD9);
  emit_byte(0xE4);
}

void Assembler::fucomi(int i) {
  // make sure the instruction is supported (introduced for P6, together with cmov)
  guarantee(VM_Version::supports_cmov(), "illegal instruction");
  emit_farith(0xDB, 0xE8, i);
}

void Assembler::fucomip(int i) {
  // make sure the instruction is supported (introduced for P6, together with cmov)
  guarantee(VM_Version::supports_cmov(), "illegal instruction");
  emit_farith(0xDF, 0xE8, i);
}

void Assembler::fwait() {
  emit_byte(0x9B);
}

void Assembler::fxch(int i) {
  emit_farith(0xD9, 0xC8, i);
}

void Assembler::fyl2x() {
  emit_byte(0xD9);
  emit_byte(0xF1);
}


#ifndef _LP64

void Assembler::incl(Register dst) {
  // Don't use it directly. Use MacroAssembler::incrementl() instead.
 emit_byte(0x40 | dst->encoding());
}

void Assembler::lea(Register dst, Address src) {
  leal(dst, src);
}

void Assembler::mov_literal32(Address dst, int32_t imm32,  RelocationHolder const& rspec) {
  InstructionMark im(this);
  emit_byte(0xC7);
  emit_operand(rax, dst);
  emit_data((int)imm32, rspec, 0);
}

void Assembler::mov_literal32(Register dst, int32_t imm32, RelocationHolder const& rspec) {
  InstructionMark im(this);
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0xB8 | encode);
  emit_data((int)imm32, rspec, 0);
}

void Assembler::popa() { // 32bit
  emit_byte(0x61);
}

void Assembler::push_literal32(int32_t imm32, RelocationHolder const& rspec) {
  InstructionMark im(this);
  emit_byte(0x68);
  emit_data(imm32, rspec, 0);
}

void Assembler::pusha() { // 32bit
  emit_byte(0x60);
}

void Assembler::set_byte_if_not_zero(Register dst) {
  emit_byte(0x0F);
  emit_byte(0x95);
  emit_byte(0xE0 | dst->encoding());
}

void Assembler::shldl(Register dst, Register src) {
  emit_byte(0x0F);
  emit_byte(0xA5);
  emit_byte(0xC0 | src->encoding() << 3 | dst->encoding());
}

void Assembler::shrdl(Register dst, Register src) {
  emit_byte(0x0F);
  emit_byte(0xAD);
  emit_byte(0xC0 | src->encoding() << 3 | dst->encoding());
}

#else // LP64

void Assembler::set_byte_if_not_zero(Register dst) {
  int enc = prefix_and_encode(dst->encoding(), true);
  emit_byte(0x0F);
  emit_byte(0x95);
  emit_byte(0xE0 | enc);
}

// 64bit only pieces of the assembler
// This should only be used by 64bit instructions that can use rip-relative
// it cannot be used by instructions that want an immediate value.

bool Assembler::reachable(AddressLiteral adr) {
  int64_t disp;
  // None will force a 64bit literal to the code stream. Likely a placeholder
  // for something that will be patched later and we need to certain it will
  // always be reachable.
  if (adr.reloc() == relocInfo::none) {
    return false;
  }
  if (adr.reloc() == relocInfo::internal_word_type) {
    // This should be rip relative and easily reachable.
    return true;
  }
  if (adr.reloc() == relocInfo::virtual_call_type ||
      adr.reloc() == relocInfo::opt_virtual_call_type ||
      adr.reloc() == relocInfo::static_call_type ||
      adr.reloc() == relocInfo::static_stub_type ) {
    // This should be rip relative within the code cache and easily
    // reachable until we get huge code caches. (At which point
    // ic code is going to have issues).
    return true;
  }
  if (adr.reloc() != relocInfo::external_word_type &&
      adr.reloc() != relocInfo::poll_return_type &&  // these are really external_word but need special
      adr.reloc() != relocInfo::poll_type &&         // relocs to identify them
      adr.reloc() != relocInfo::runtime_call_type ) {
    return false;
  }

  // Stress the correction code
  if (ForceUnreachable) {
    // Must be runtimecall reloc, see if it is in the codecache
    // Flipping stuff in the codecache to be unreachable causes issues
    // with things like inline caches where the additional instructions
    // are not handled.
    if (CodeCache::find_blob(adr._target) == NULL) {
      return false;
    }
  }
  // For external_word_type/runtime_call_type if it is reachable from where we
  // are now (possibly a temp buffer) and where we might end up
  // anywhere in the codeCache then we are always reachable.
  // This would have to change if we ever save/restore shared code
  // to be more pessimistic.

  disp = (int64_t)adr._target - ((int64_t)CodeCache::low_bound() + sizeof(int));
  if (!is_simm32(disp)) return false;
  disp = (int64_t)adr._target - ((int64_t)CodeCache::high_bound() + sizeof(int));
  if (!is_simm32(disp)) return false;

  disp = (int64_t)adr._target - ((int64_t)_code_pos + sizeof(int));

  // Because rip relative is a disp + address_of_next_instruction and we
  // don't know the value of address_of_next_instruction we apply a fudge factor
  // to make sure we will be ok no matter the size of the instruction we get placed into.
  // We don't have to fudge the checks above here because they are already worst case.

  // 12 == override/rex byte, opcode byte, rm byte, sib byte, a 4-byte disp , 4-byte literal
  // + 4 because better safe than sorry.
  const int fudge = 12 + 4;
  if (disp < 0) {
    disp -= fudge;
  } else {
    disp += fudge;
  }
  return is_simm32(disp);
}

void Assembler::emit_data64(jlong data,
                            relocInfo::relocType rtype,
                            int format) {
  if (rtype == relocInfo::none) {
    emit_long64(data);
  } else {
    emit_data64(data, Relocation::spec_simple(rtype), format);
  }
}

void Assembler::emit_data64(jlong data,
                            RelocationHolder const& rspec,
                            int format) {
  assert(imm_operand == 0, "default format must be immediate in this file");
  assert(imm_operand == format, "must be immediate");
  assert(inst_mark() != NULL, "must be inside InstructionMark");
  // Do not use AbstractAssembler::relocate, which is not intended for
  // embedded words.  Instead, relocate to the enclosing instruction.
  code_section()->relocate(inst_mark(), rspec, format);
#ifdef ASSERT
  check_relocation(rspec, format);
#endif
  emit_long64(data);
}

int Assembler::prefix_and_encode(int reg_enc, bool byteinst) {
  if (reg_enc >= 8) {
    prefix(REX_B);
    reg_enc -= 8;
  } else if (byteinst && reg_enc >= 4) {
    prefix(REX);
  }
  return reg_enc;
}

int Assembler::prefixq_and_encode(int reg_enc) {
  if (reg_enc < 8) {
    prefix(REX_W);
  } else {
    prefix(REX_WB);
    reg_enc -= 8;
  }
  return reg_enc;
}

int Assembler::prefix_and_encode(int dst_enc, int src_enc, bool byteinst) {
  if (dst_enc < 8) {
    if (src_enc >= 8) {
      prefix(REX_B);
      src_enc -= 8;
    } else if (byteinst && src_enc >= 4) {
      prefix(REX);
    }
  } else {
    if (src_enc < 8) {
      prefix(REX_R);
    } else {
      prefix(REX_RB);
      src_enc -= 8;
    }
    dst_enc -= 8;
  }
  return dst_enc << 3 | src_enc;
}

int Assembler::prefixq_and_encode(int dst_enc, int src_enc) {
  if (dst_enc < 8) {
    if (src_enc < 8) {
      prefix(REX_W);
    } else {
      prefix(REX_WB);
      src_enc -= 8;
    }
  } else {
    if (src_enc < 8) {
      prefix(REX_WR);
    } else {
      prefix(REX_WRB);
      src_enc -= 8;
    }
    dst_enc -= 8;
  }
  return dst_enc << 3 | src_enc;
}

void Assembler::prefix(Register reg) {
  if (reg->encoding() >= 8) {
    prefix(REX_B);
  }
}

void Assembler::prefix(Address adr) {
  if (adr.base_needs_rex()) {
    if (adr.index_needs_rex()) {
      prefix(REX_XB);
    } else {
      prefix(REX_B);
    }
  } else {
    if (adr.index_needs_rex()) {
      prefix(REX_X);
    }
  }
}

void Assembler::prefixq(Address adr) {
  if (adr.base_needs_rex()) {
    if (adr.index_needs_rex()) {
      prefix(REX_WXB);
    } else {
      prefix(REX_WB);
    }
  } else {
    if (adr.index_needs_rex()) {
      prefix(REX_WX);
    } else {
      prefix(REX_W);
    }
  }
}


void Assembler::prefix(Address adr, Register reg, bool byteinst) {
  if (reg->encoding() < 8) {
    if (adr.base_needs_rex()) {
      if (adr.index_needs_rex()) {
        prefix(REX_XB);
      } else {
        prefix(REX_B);
      }
    } else {
      if (adr.index_needs_rex()) {
        prefix(REX_X);
      } else if (reg->encoding() >= 4 ) {
        prefix(REX);
      }
    }
  } else {
    if (adr.base_needs_rex()) {
      if (adr.index_needs_rex()) {
        prefix(REX_RXB);
      } else {
        prefix(REX_RB);
      }
    } else {
      if (adr.index_needs_rex()) {
        prefix(REX_RX);
      } else {
        prefix(REX_R);
      }
    }
  }
}

void Assembler::prefixq(Address adr, Register src) {
  if (src->encoding() < 8) {
    if (adr.base_needs_rex()) {
      if (adr.index_needs_rex()) {
        prefix(REX_WXB);
      } else {
        prefix(REX_WB);
      }
    } else {
      if (adr.index_needs_rex()) {
        prefix(REX_WX);
      } else {
        prefix(REX_W);
      }
    }
  } else {
    if (adr.base_needs_rex()) {
      if (adr.index_needs_rex()) {
        prefix(REX_WRXB);
      } else {
        prefix(REX_WRB);
      }
    } else {
      if (adr.index_needs_rex()) {
        prefix(REX_WRX);
      } else {
        prefix(REX_WR);
      }
    }
  }
}

void Assembler::prefix(Address adr, XMMRegister reg) {
  if (reg->encoding() < 8) {
    if (adr.base_needs_rex()) {
      if (adr.index_needs_rex()) {
        prefix(REX_XB);
      } else {
        prefix(REX_B);
      }
    } else {
      if (adr.index_needs_rex()) {
        prefix(REX_X);
      }
    }
  } else {
    if (adr.base_needs_rex()) {
      if (adr.index_needs_rex()) {
        prefix(REX_RXB);
      } else {
        prefix(REX_RB);
      }
    } else {
      if (adr.index_needs_rex()) {
        prefix(REX_RX);
      } else {
        prefix(REX_R);
      }
    }
  }
}

void Assembler::adcq(Register dst, int32_t imm32) {
  (void) prefixq_and_encode(dst->encoding());
  emit_arith(0x81, 0xD0, dst, imm32);
}

void Assembler::adcq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x13);
  emit_operand(dst, src);
}

void Assembler::adcq(Register dst, Register src) {
  (int) prefixq_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x13, 0xC0, dst, src);
}

void Assembler::addq(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefixq(dst);
  emit_arith_operand(0x81, rax, dst,imm32);
}

void Assembler::addq(Address dst, Register src) {
  InstructionMark im(this);
  prefixq(dst, src);
  emit_byte(0x01);
  emit_operand(src, dst);
}

void Assembler::addq(Register dst, int32_t imm32) {
  (void) prefixq_and_encode(dst->encoding());
  emit_arith(0x81, 0xC0, dst, imm32);
}

void Assembler::addq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x03);
  emit_operand(dst, src);
}

void Assembler::addq(Register dst, Register src) {
  (void) prefixq_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x03, 0xC0, dst, src);
}

void Assembler::andq(Register dst, int32_t imm32) {
  (void) prefixq_and_encode(dst->encoding());
  emit_arith(0x81, 0xE0, dst, imm32);
}

void Assembler::andq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x23);
  emit_operand(dst, src);
}

void Assembler::andq(Register dst, Register src) {
  (int) prefixq_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x23, 0xC0, dst, src);
}

void Assembler::bsfq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xBC);
  emit_byte(0xC0 | encode);
}

void Assembler::bsrq(Register dst, Register src) {
  assert(!VM_Version::supports_lzcnt(), "encoding is treated as LZCNT");
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xBD);
  emit_byte(0xC0 | encode);
}

void Assembler::bswapq(Register reg) {
  int encode = prefixq_and_encode(reg->encoding());
  emit_byte(0x0F);
  emit_byte(0xC8 | encode);
}

void Assembler::cdqq() {
  prefix(REX_W);
  emit_byte(0x99);
}

void Assembler::clflush(Address adr) {
  prefix(adr);
  emit_byte(0x0F);
  emit_byte(0xAE);
  emit_operand(rdi, adr);
}

void Assembler::cmovq(Condition cc, Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x40 | cc);
  emit_byte(0xC0 | encode);
}

void Assembler::cmovq(Condition cc, Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x0F);
  emit_byte(0x40 | cc);
  emit_operand(dst, src);
}

void Assembler::cmpq(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefixq(dst);
  emit_byte(0x81);
  emit_operand(rdi, dst, 4);
  emit_long(imm32);
}

void Assembler::cmpq(Register dst, int32_t imm32) {
  (void) prefixq_and_encode(dst->encoding());
  emit_arith(0x81, 0xF8, dst, imm32);
}

void Assembler::cmpq(Address dst, Register src) {
  InstructionMark im(this);
  prefixq(dst, src);
  emit_byte(0x3B);
  emit_operand(src, dst);
}

void Assembler::cmpq(Register dst, Register src) {
  (void) prefixq_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x3B, 0xC0, dst, src);
}

void Assembler::cmpq(Register dst, Address  src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x3B);
  emit_operand(dst, src);
}

void Assembler::cmpxchgq(Register reg, Address adr) {
  InstructionMark im(this);
  prefixq(adr, reg);
  emit_byte(0x0F);
  emit_byte(0xB1);
  emit_operand(reg, adr);
}

void Assembler::cvtsi2sdq(XMMRegister dst, Register src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0xF2);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2A);
  emit_byte(0xC0 | encode);
}

void Assembler::cvtsi2ssq(XMMRegister dst, Register src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_byte(0xF3);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2A);
  emit_byte(0xC0 | encode);
}

void Assembler::cvttsd2siq(Register dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_byte(0xF2);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2C);
  emit_byte(0xC0 | encode);
}

void Assembler::cvttss2siq(Register dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_byte(0xF3);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2C);
  emit_byte(0xC0 | encode);
}

void Assembler::decl(Register dst) {
  // Don't use it directly. Use MacroAssembler::decrementl() instead.
  // Use two-byte form (one-byte form is a REX prefix in 64-bit mode)
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0xFF);
  emit_byte(0xC8 | encode);
}

void Assembler::decq(Register dst) {
  // Don't use it directly. Use MacroAssembler::decrementq() instead.
  // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
  int encode = prefixq_and_encode(dst->encoding());
  emit_byte(0xFF);
  emit_byte(0xC8 | encode);
}

void Assembler::decq(Address dst) {
  // Don't use it directly. Use MacroAssembler::decrementq() instead.
  InstructionMark im(this);
  prefixq(dst);
  emit_byte(0xFF);
  emit_operand(rcx, dst);
}

void Assembler::fxrstor(Address src) {
  prefixq(src);
  emit_byte(0x0F);
  emit_byte(0xAE);
  emit_operand(as_Register(1), src);
}

void Assembler::fxsave(Address dst) {
  prefixq(dst);
  emit_byte(0x0F);
  emit_byte(0xAE);
  emit_operand(as_Register(0), dst);
}

void Assembler::idivq(Register src) {
  int encode = prefixq_and_encode(src->encoding());
  emit_byte(0xF7);
  emit_byte(0xF8 | encode);
}

void Assembler::imulq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xAF);
  emit_byte(0xC0 | encode);
}

void Assembler::imulq(Register dst, Register src, int value) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  if (is8bit(value)) {
    emit_byte(0x6B);
    emit_byte(0xC0 | encode);
    emit_byte(value);
  } else {
    emit_byte(0x69);
    emit_byte(0xC0 | encode);
    emit_long(value);
  }
}

void Assembler::incl(Register dst) {
  // Don't use it directly. Use MacroAssembler::incrementl() instead.
  // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0xFF);
  emit_byte(0xC0 | encode);
}

void Assembler::incq(Register dst) {
  // Don't use it directly. Use MacroAssembler::incrementq() instead.
  // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
  int encode = prefixq_and_encode(dst->encoding());
  emit_byte(0xFF);
  emit_byte(0xC0 | encode);
}

void Assembler::incq(Address dst) {
  // Don't use it directly. Use MacroAssembler::incrementq() instead.
  InstructionMark im(this);
  prefixq(dst);
  emit_byte(0xFF);
  emit_operand(rax, dst);
}

void Assembler::lea(Register dst, Address src) {
  leaq(dst, src);
}

void Assembler::leaq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x8D);
  emit_operand(dst, src);
}

void Assembler::mov64(Register dst, int64_t imm64) {
  InstructionMark im(this);
  int encode = prefixq_and_encode(dst->encoding());
  emit_byte(0xB8 | encode);
  emit_long64(imm64);
}

void Assembler::mov_literal64(Register dst, intptr_t imm64, RelocationHolder const& rspec) {
  InstructionMark im(this);
  int encode = prefixq_and_encode(dst->encoding());
  emit_byte(0xB8 | encode);
  emit_data64(imm64, rspec);
}

void Assembler::mov_narrow_oop(Register dst, int32_t imm32, RelocationHolder const& rspec) {
  InstructionMark im(this);
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0xB8 | encode);
  emit_data((int)imm32, rspec, narrow_oop_operand);
}

void Assembler::mov_narrow_oop(Address dst, int32_t imm32,  RelocationHolder const& rspec) {
  InstructionMark im(this);
  prefix(dst);
  emit_byte(0xC7);
  emit_operand(rax, dst, 4);
  emit_data((int)imm32, rspec, narrow_oop_operand);
}

void Assembler::cmp_narrow_oop(Register src1, int32_t imm32, RelocationHolder const& rspec) {
  InstructionMark im(this);
  int encode = prefix_and_encode(src1->encoding());
  emit_byte(0x81);
  emit_byte(0xF8 | encode);
  emit_data((int)imm32, rspec, narrow_oop_operand);
}

void Assembler::cmp_narrow_oop(Address src1, int32_t imm32, RelocationHolder const& rspec) {
  InstructionMark im(this);
  prefix(src1);
  emit_byte(0x81);
  emit_operand(rax, src1, 4);
  emit_data((int)imm32, rspec, narrow_oop_operand);
}

void Assembler::lzcntq(Register dst, Register src) {
  assert(VM_Version::supports_lzcnt(), "encoding is treated as BSR");
  emit_byte(0xF3);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xBD);
  emit_byte(0xC0 | encode);
}

void Assembler::movdq(XMMRegister dst, Register src) {
  // table D-1 says MMX/SSE2
  NOT_LP64(assert(VM_Version::supports_sse2() || VM_Version::supports_mmx(), ""));
  emit_byte(0x66);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x6E);
  emit_byte(0xC0 | encode);
}

void Assembler::movdq(Register dst, XMMRegister src) {
  // table D-1 says MMX/SSE2
  NOT_LP64(assert(VM_Version::supports_sse2() || VM_Version::supports_mmx(), ""));
  emit_byte(0x66);
  // swap src/dst to get correct prefix
  int encode = prefixq_and_encode(src->encoding(), dst->encoding());
  emit_byte(0x0F);
  emit_byte(0x7E);
  emit_byte(0xC0 | encode);
}

void Assembler::movq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x8B);
  emit_byte(0xC0 | encode);
}

void Assembler::movq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x8B);
  emit_operand(dst, src);
}

void Assembler::movq(Address dst, Register src) {
  InstructionMark im(this);
  prefixq(dst, src);
  emit_byte(0x89);
  emit_operand(src, dst);
}

void Assembler::movsbq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x0F);
  emit_byte(0xBE);
  emit_operand(dst, src);
}

void Assembler::movsbq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xBE);
  emit_byte(0xC0 | encode);
}

void Assembler::movslq(Register dst, int32_t imm32) {
  // dbx shows movslq(rcx, 3) as movq     $0x0000000049000000,(%rbx)
  // and movslq(r8, 3); as movl     $0x0000000048000000,(%rbx)
  // as a result we shouldn't use until tested at runtime...
  ShouldNotReachHere();
  InstructionMark im(this);
  int encode = prefixq_and_encode(dst->encoding());
  emit_byte(0xC7 | encode);
  emit_long(imm32);
}

void Assembler::movslq(Address dst, int32_t imm32) {
  assert(is_simm32(imm32), "lost bits");
  InstructionMark im(this);
  prefixq(dst);
  emit_byte(0xC7);
  emit_operand(rax, dst, 4);
  emit_long(imm32);
}

void Assembler::movslq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x63);
  emit_operand(dst, src);
}

void Assembler::movslq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x63);
  emit_byte(0xC0 | encode);
}

void Assembler::movswq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x0F);
  emit_byte(0xBF);
  emit_operand(dst, src);
}

void Assembler::movswq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xBF);
  emit_byte(0xC0 | encode);
}

void Assembler::movzbq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x0F);
  emit_byte(0xB6);
  emit_operand(dst, src);
}

void Assembler::movzbq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xB6);
  emit_byte(0xC0 | encode);
}

void Assembler::movzwq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x0F);
  emit_byte(0xB7);
  emit_operand(dst, src);
}

void Assembler::movzwq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xB7);
  emit_byte(0xC0 | encode);
}

void Assembler::negq(Register dst) {
  int encode = prefixq_and_encode(dst->encoding());
  emit_byte(0xF7);
  emit_byte(0xD8 | encode);
}

void Assembler::notq(Register dst) {
  int encode = prefixq_and_encode(dst->encoding());
  emit_byte(0xF7);
  emit_byte(0xD0 | encode);
}

void Assembler::orq(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefixq(dst);
  emit_byte(0x81);
  emit_operand(rcx, dst, 4);
  emit_long(imm32);
}

void Assembler::orq(Register dst, int32_t imm32) {
  (void) prefixq_and_encode(dst->encoding());
  emit_arith(0x81, 0xC8, dst, imm32);
}

void Assembler::orq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x0B);
  emit_operand(dst, src);
}

void Assembler::orq(Register dst, Register src) {
  (void) prefixq_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x0B, 0xC0, dst, src);
}

void Assembler::popa() { // 64bit
  movq(r15, Address(rsp, 0));
  movq(r14, Address(rsp, wordSize));
  movq(r13, Address(rsp, 2 * wordSize));
  movq(r12, Address(rsp, 3 * wordSize));
  movq(r11, Address(rsp, 4 * wordSize));
  movq(r10, Address(rsp, 5 * wordSize));
  movq(r9,  Address(rsp, 6 * wordSize));
  movq(r8,  Address(rsp, 7 * wordSize));
  movq(rdi, Address(rsp, 8 * wordSize));
  movq(rsi, Address(rsp, 9 * wordSize));
  movq(rbp, Address(rsp, 10 * wordSize));
  // skip rsp
  movq(rbx, Address(rsp, 12 * wordSize));
  movq(rdx, Address(rsp, 13 * wordSize));
  movq(rcx, Address(rsp, 14 * wordSize));
  movq(rax, Address(rsp, 15 * wordSize));

  addq(rsp, 16 * wordSize);
}

void Assembler::popcntq(Register dst, Address src) {
  assert(VM_Version::supports_popcnt(), "must support");
  InstructionMark im(this);
  emit_byte(0xF3);
  prefixq(src, dst);
  emit_byte(0x0F);
  emit_byte(0xB8);
  emit_operand(dst, src);
}

void Assembler::popcntq(Register dst, Register src) {
  assert(VM_Version::supports_popcnt(), "must support");
  emit_byte(0xF3);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xB8);
  emit_byte(0xC0 | encode);
}

void Assembler::popq(Address dst) {
  InstructionMark im(this);
  prefixq(dst);
  emit_byte(0x8F);
  emit_operand(rax, dst);
}

void Assembler::pusha() { // 64bit
  // we have to store original rsp.  ABI says that 128 bytes
  // below rsp are local scratch.
  movq(Address(rsp, -5 * wordSize), rsp);

  subq(rsp, 16 * wordSize);

  movq(Address(rsp, 15 * wordSize), rax);
  movq(Address(rsp, 14 * wordSize), rcx);
  movq(Address(rsp, 13 * wordSize), rdx);
  movq(Address(rsp, 12 * wordSize), rbx);
  // skip rsp
  movq(Address(rsp, 10 * wordSize), rbp);
  movq(Address(rsp, 9 * wordSize), rsi);
  movq(Address(rsp, 8 * wordSize), rdi);
  movq(Address(rsp, 7 * wordSize), r8);
  movq(Address(rsp, 6 * wordSize), r9);
  movq(Address(rsp, 5 * wordSize), r10);
  movq(Address(rsp, 4 * wordSize), r11);
  movq(Address(rsp, 3 * wordSize), r12);
  movq(Address(rsp, 2 * wordSize), r13);
  movq(Address(rsp, wordSize), r14);
  movq(Address(rsp, 0), r15);
}

void Assembler::pushq(Address src) {
  InstructionMark im(this);
  prefixq(src);
  emit_byte(0xFF);
  emit_operand(rsi, src);
}

void Assembler::rclq(Register dst, int imm8) {
  assert(isShiftCount(imm8 >> 1), "illegal shift count");
  int encode = prefixq_and_encode(dst->encoding());
  if (imm8 == 1) {
    emit_byte(0xD1);
    emit_byte(0xD0 | encode);
  } else {
    emit_byte(0xC1);
    emit_byte(0xD0 | encode);
    emit_byte(imm8);
  }
}
void Assembler::sarq(Register dst, int imm8) {
  assert(isShiftCount(imm8 >> 1), "illegal shift count");
  int encode = prefixq_and_encode(dst->encoding());
  if (imm8 == 1) {
    emit_byte(0xD1);
    emit_byte(0xF8 | encode);
  } else {
    emit_byte(0xC1);
    emit_byte(0xF8 | encode);
    emit_byte(imm8);
  }
}

void Assembler::sarq(Register dst) {
  int encode = prefixq_and_encode(dst->encoding());
  emit_byte(0xD3);
  emit_byte(0xF8 | encode);
}
void Assembler::sbbq(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefixq(dst);
  emit_arith_operand(0x81, rbx, dst, imm32);
}

void Assembler::sbbq(Register dst, int32_t imm32) {
  (void) prefixq_and_encode(dst->encoding());
  emit_arith(0x81, 0xD8, dst, imm32);
}

void Assembler::sbbq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x1B);
  emit_operand(dst, src);
}

void Assembler::sbbq(Register dst, Register src) {
  (void) prefixq_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x1B, 0xC0, dst, src);
}

void Assembler::shlq(Register dst, int imm8) {
  assert(isShiftCount(imm8 >> 1), "illegal shift count");
  int encode = prefixq_and_encode(dst->encoding());
  if (imm8 == 1) {
    emit_byte(0xD1);
    emit_byte(0xE0 | encode);
  } else {
    emit_byte(0xC1);
    emit_byte(0xE0 | encode);
    emit_byte(imm8);
  }
}

void Assembler::shlq(Register dst) {
  int encode = prefixq_and_encode(dst->encoding());
  emit_byte(0xD3);
  emit_byte(0xE0 | encode);
}

void Assembler::shrq(Register dst, int imm8) {
  assert(isShiftCount(imm8 >> 1), "illegal shift count");
  int encode = prefixq_and_encode(dst->encoding());
  emit_byte(0xC1);
  emit_byte(0xE8 | encode);
  emit_byte(imm8);
}

void Assembler::shrq(Register dst) {
  int encode = prefixq_and_encode(dst->encoding());
  emit_byte(0xD3);
  emit_byte(0xE8 | encode);
}

void Assembler::sqrtsd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  emit_byte(0xF2);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x51);
  emit_operand(dst, src);
}

void Assembler::subq(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefixq(dst);
  if (is8bit(imm32)) {
    emit_byte(0x83);
    emit_operand(rbp, dst, 1);
    emit_byte(imm32 & 0xFF);
  } else {
    emit_byte(0x81);
    emit_operand(rbp, dst, 4);
    emit_long(imm32);
  }
}

void Assembler::subq(Register dst, int32_t imm32) {
  (void) prefixq_and_encode(dst->encoding());
  emit_arith(0x81, 0xE8, dst, imm32);
}

void Assembler::subq(Address dst, Register src) {
  InstructionMark im(this);
  prefixq(dst, src);
  emit_byte(0x29);
  emit_operand(src, dst);
}

void Assembler::subq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x2B);
  emit_operand(dst, src);
}

void Assembler::subq(Register dst, Register src) {
  (void) prefixq_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x2B, 0xC0, dst, src);
}

void Assembler::testq(Register dst, int32_t imm32) {
  // not using emit_arith because test
  // doesn't support sign-extension of
  // 8bit operands
  int encode = dst->encoding();
  if (encode == 0) {
    prefix(REX_W);
    emit_byte(0xA9);
  } else {
    encode = prefixq_and_encode(encode);
    emit_byte(0xF7);
    emit_byte(0xC0 | encode);
  }
  emit_long(imm32);
}

void Assembler::testq(Register dst, Register src) {
  (void) prefixq_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x85, 0xC0, dst, src);
}

void Assembler::xaddq(Address dst, Register src) {
  InstructionMark im(this);
  prefixq(dst, src);
  emit_byte(0x0F);
  emit_byte(0xC1);
  emit_operand(src, dst);
}

void Assembler::xchgq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x87);
  emit_operand(dst, src);
}

void Assembler::xchgq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x87);
  emit_byte(0xc0 | encode);
}

void Assembler::xorq(Register dst, Register src) {
  (void) prefixq_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x33, 0xC0, dst, src);
}

void Assembler::xorq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x33);
  emit_operand(dst, src);
}

#endif // !LP64

static Assembler::Condition reverse[] = {
    Assembler::noOverflow     /* overflow      = 0x0 */ ,
    Assembler::overflow       /* noOverflow    = 0x1 */ ,
    Assembler::aboveEqual     /* carrySet      = 0x2, below         = 0x2 */ ,
    Assembler::below          /* aboveEqual    = 0x3, carryClear    = 0x3 */ ,
    Assembler::notZero        /* zero          = 0x4, equal         = 0x4 */ ,
    Assembler::zero           /* notZero       = 0x5, notEqual      = 0x5 */ ,
    Assembler::above          /* belowEqual    = 0x6 */ ,
    Assembler::belowEqual     /* above         = 0x7 */ ,
    Assembler::positive       /* negative      = 0x8 */ ,
    Assembler::negative       /* positive      = 0x9 */ ,
    Assembler::noParity       /* parity        = 0xa */ ,
    Assembler::parity         /* noParity      = 0xb */ ,
    Assembler::greaterEqual   /* less          = 0xc */ ,
    Assembler::less           /* greaterEqual  = 0xd */ ,
    Assembler::greater        /* lessEqual     = 0xe */ ,
    Assembler::lessEqual      /* greater       = 0xf, */

};


// Implementation of MacroAssembler

// First all the versions that have distinct versions depending on 32/64 bit
// Unless the difference is trivial (1 line or so).

#ifndef _LP64

// 32bit versions

Address MacroAssembler::as_Address(AddressLiteral adr) {
  return Address(adr.target(), adr.rspec());
}

Address MacroAssembler::as_Address(ArrayAddress adr) {
  return Address::make_array(adr);
}

int MacroAssembler::biased_locking_enter(Register lock_reg,
                                         Register obj_reg,
                                         Register swap_reg,
                                         Register tmp_reg,
                                         bool swap_reg_contains_mark,
                                         Label& done,
                                         Label* slow_case,
                                         BiasedLockingCounters* counters) {
  assert(UseBiasedLocking, "why call this otherwise?");
  assert(swap_reg == rax, "swap_reg must be rax, for cmpxchg");
  assert_different_registers(lock_reg, obj_reg, swap_reg);

  if (PrintBiasedLockingStatistics && counters == NULL)
    counters = BiasedLocking::counters();

  bool need_tmp_reg = false;
  if (tmp_reg == noreg) {
    need_tmp_reg = true;
    tmp_reg = lock_reg;
  } else {
    assert_different_registers(lock_reg, obj_reg, swap_reg, tmp_reg);
  }
  assert(markOopDesc::age_shift == markOopDesc::lock_bits + markOopDesc::biased_lock_bits, "biased locking makes assumptions about bit layout");
  Address mark_addr      (obj_reg, oopDesc::mark_offset_in_bytes());
  Address klass_addr     (obj_reg, oopDesc::klass_offset_in_bytes());
  Address saved_mark_addr(lock_reg, 0);

  // Biased locking
  // See whether the lock is currently biased toward our thread and
  // whether the epoch is still valid
  // Note that the runtime guarantees sufficient alignment of JavaThread
  // pointers to allow age to be placed into low bits
  // First check to see whether biasing is even enabled for this object
  Label cas_label;
  int null_check_offset = -1;
  if (!swap_reg_contains_mark) {
    null_check_offset = offset();
    movl(swap_reg, mark_addr);
  }
  if (need_tmp_reg) {
    push(tmp_reg);
  }
  movl(tmp_reg, swap_reg);
  andl(tmp_reg, markOopDesc::biased_lock_mask_in_place);
  cmpl(tmp_reg, markOopDesc::biased_lock_pattern);
  if (need_tmp_reg) {
    pop(tmp_reg);
  }
  jcc(Assembler::notEqual, cas_label);
  // The bias pattern is present in the object's header. Need to check
  // whether the bias owner and the epoch are both still current.
  // Note that because there is no current thread register on x86 we
  // need to store off the mark word we read out of the object to
  // avoid reloading it and needing to recheck invariants below. This
  // store is unfortunate but it makes the overall code shorter and
  // simpler.
  movl(saved_mark_addr, swap_reg);
  if (need_tmp_reg) {
    push(tmp_reg);
  }
  get_thread(tmp_reg);
  xorl(swap_reg, tmp_reg);
  if (swap_reg_contains_mark) {
    null_check_offset = offset();
  }
  movl(tmp_reg, klass_addr);
  xorl(swap_reg, Address(tmp_reg, Klass::prototype_header_offset_in_bytes() + klassOopDesc::klass_part_offset_in_bytes()));
  andl(swap_reg, ~((int) markOopDesc::age_mask_in_place));
  if (need_tmp_reg) {
    pop(tmp_reg);
  }
  if (counters != NULL) {
    cond_inc32(Assembler::zero,
               ExternalAddress((address)counters->biased_lock_entry_count_addr()));
  }
  jcc(Assembler::equal, done);

  Label try_revoke_bias;
  Label try_rebias;

  // At this point we know that the header has the bias pattern and
  // that we are not the bias owner in the current epoch. We need to
  // figure out more details about the state of the header in order to
  // know what operations can be legally performed on the object's
  // header.

  // If the low three bits in the xor result aren't clear, that means
  // the prototype header is no longer biased and we have to revoke
  // the bias on this object.
  testl(swap_reg, markOopDesc::biased_lock_mask_in_place);
  jcc(Assembler::notZero, try_revoke_bias);

  // Biasing is still enabled for this data type. See whether the
  // epoch of the current bias is still valid, meaning that the epoch
  // bits of the mark word are equal to the epoch bits of the
  // prototype header. (Note that the prototype header's epoch bits
  // only change at a safepoint.) If not, attempt to rebias the object
  // toward the current thread. Note that we must be absolutely sure
  // that the current epoch is invalid in order to do this because
  // otherwise the manipulations it performs on the mark word are
  // illegal.
  testl(swap_reg, markOopDesc::epoch_mask_in_place);
  jcc(Assembler::notZero, try_rebias);

  // The epoch of the current bias is still valid but we know nothing
  // about the owner; it might be set or it might be clear. Try to
  // acquire the bias of the object using an atomic operation. If this
  // fails we will go in to the runtime to revoke the object's bias.
  // Note that we first construct the presumed unbiased header so we
  // don't accidentally blow away another thread's valid bias.
  movl(swap_reg, saved_mark_addr);
  andl(swap_reg,
       markOopDesc::biased_lock_mask_in_place | markOopDesc::age_mask_in_place | markOopDesc::epoch_mask_in_place);
  if (need_tmp_reg) {
    push(tmp_reg);
  }
  get_thread(tmp_reg);
  orl(tmp_reg, swap_reg);
  if (os::is_MP()) {
    lock();
  }
  cmpxchgptr(tmp_reg, Address(obj_reg, 0));
  if (need_tmp_reg) {
    pop(tmp_reg);
  }
  // If the biasing toward our thread failed, this means that
  // another thread succeeded in biasing it toward itself and we
  // need to revoke that bias. The revocation will occur in the
  // interpreter runtime in the slow case.
  if (counters != NULL) {
    cond_inc32(Assembler::zero,
               ExternalAddress((address)counters->anonymously_biased_lock_entry_count_addr()));
  }
  if (slow_case != NULL) {
    jcc(Assembler::notZero, *slow_case);
  }
  jmp(done);

  bind(try_rebias);
  // At this point we know the epoch has expired, meaning that the
  // current "bias owner", if any, is actually invalid. Under these
  // circumstances _only_, we are allowed to use the current header's
  // value as the comparison value when doing the cas to acquire the
  // bias in the current epoch. In other words, we allow transfer of
  // the bias from one thread to another directly in this situation.
  //
  // FIXME: due to a lack of registers we currently blow away the age
  // bits in this situation. Should attempt to preserve them.
  if (need_tmp_reg) {
    push(tmp_reg);
  }
  get_thread(tmp_reg);
  movl(swap_reg, klass_addr);
  orl(tmp_reg, Address(swap_reg, Klass::prototype_header_offset_in_bytes() + klassOopDesc::klass_part_offset_in_bytes()));
  movl(swap_reg, saved_mark_addr);
  if (os::is_MP()) {
    lock();
  }
  cmpxchgptr(tmp_reg, Address(obj_reg, 0));
  if (need_tmp_reg) {
    pop(tmp_reg);
  }
  // If the biasing toward our thread failed, then another thread
  // succeeded in biasing it toward itself and we need to revoke that
  // bias. The revocation will occur in the runtime in the slow case.
  if (counters != NULL) {
    cond_inc32(Assembler::zero,
               ExternalAddress((address)counters->rebiased_lock_entry_count_addr()));
  }
  if (slow_case != NULL) {
    jcc(Assembler::notZero, *slow_case);
  }
  jmp(done);

  bind(try_revoke_bias);
  // The prototype mark in the klass doesn't have the bias bit set any
  // more, indicating that objects of this data type are not supposed
  // to be biased any more. We are going to try to reset the mark of
  // this object to the prototype value and fall through to the
  // CAS-based locking scheme. Note that if our CAS fails, it means
  // that another thread raced us for the privilege of revoking the
  // bias of this particular object, so it's okay to continue in the
  // normal locking code.
  //
  // FIXME: due to a lack of registers we currently blow away the age
  // bits in this situation. Should attempt to preserve them.
  movl(swap_reg, saved_mark_addr);
  if (need_tmp_reg) {
    push(tmp_reg);
  }
  movl(tmp_reg, klass_addr);
  movl(tmp_reg, Address(tmp_reg, Klass::prototype_header_offset_in_bytes() + klassOopDesc::klass_part_offset_in_bytes()));
  if (os::is_MP()) {
    lock();
  }
  cmpxchgptr(tmp_reg, Address(obj_reg, 0));
  if (need_tmp_reg) {
    pop(tmp_reg);
  }
  // Fall through to the normal CAS-based lock, because no matter what
  // the result of the above CAS, some thread must have succeeded in
  // removing the bias bit from the object's header.
  if (counters != NULL) {
    cond_inc32(Assembler::zero,
               ExternalAddress((address)counters->revoked_lock_entry_count_addr()));
  }

  bind(cas_label);

  return null_check_offset;
}
void MacroAssembler::call_VM_leaf_base(address entry_point,
                                       int number_of_arguments) {
  call(RuntimeAddress(entry_point));
  increment(rsp, number_of_arguments * wordSize);
}

void MacroAssembler::cmpoop(Address src1, jobject obj) {
  cmp_literal32(src1, (int32_t)obj, oop_Relocation::spec_for_immediate());
}

void MacroAssembler::cmpoop(Register src1, jobject obj) {
  cmp_literal32(src1, (int32_t)obj, oop_Relocation::spec_for_immediate());
}

void MacroAssembler::extend_sign(Register hi, Register lo) {
  // According to Intel Doc. AP-526, "Integer Divide", p.18.
  if (VM_Version::is_P6() && hi == rdx && lo == rax) {
    cdql();
  } else {
    movl(hi, lo);
    sarl(hi, 31);
  }
}

void MacroAssembler::fat_nop() {
  // A 5 byte nop that is safe for patching (see patch_verified_entry)
  emit_byte(0x26); // es:
  emit_byte(0x2e); // cs:
  emit_byte(0x64); // fs:
  emit_byte(0x65); // gs:
  emit_byte(0x90);
}

void MacroAssembler::jC2(Register tmp, Label& L) {
  // set parity bit if FPU flag C2 is set (via rax)
  save_rax(tmp);
  fwait(); fnstsw_ax();
  sahf();
  restore_rax(tmp);
  // branch
  jcc(Assembler::parity, L);
}

void MacroAssembler::jnC2(Register tmp, Label& L) {
  // set parity bit if FPU flag C2 is set (via rax)
  save_rax(tmp);
  fwait(); fnstsw_ax();
  sahf();
  restore_rax(tmp);
  // branch
  jcc(Assembler::noParity, L);
}

// 32bit can do a case table jump in one instruction but we no longer allow the base
// to be installed in the Address class
void MacroAssembler::jump(ArrayAddress entry) {
  jmp(as_Address(entry));
}

// Note: y_lo will be destroyed
void MacroAssembler::lcmp2int(Register x_hi, Register x_lo, Register y_hi, Register y_lo) {
  // Long compare for Java (semantics as described in JVM spec.)
  Label high, low, done;

  cmpl(x_hi, y_hi);
  jcc(Assembler::less, low);
  jcc(Assembler::greater, high);
  // x_hi is the return register
  xorl(x_hi, x_hi);
  cmpl(x_lo, y_lo);
  jcc(Assembler::below, low);
  jcc(Assembler::equal, done);

  bind(high);
  xorl(x_hi, x_hi);
  increment(x_hi);
  jmp(done);

  bind(low);
  xorl(x_hi, x_hi);
  decrementl(x_hi);

  bind(done);
}

void MacroAssembler::lea(Register dst, AddressLiteral src) {
    mov_literal32(dst, (int32_t)src.target(), src.rspec());
}

void MacroAssembler::lea(Address dst, AddressLiteral adr) {
  // leal(dst, as_Address(adr));
  // see note in movl as to why we must use a move
  mov_literal32(dst, (int32_t) adr.target(), adr.rspec());
}

void MacroAssembler::leave() {
  mov(rsp, rbp);
  pop(rbp);
}

void MacroAssembler::lmul(int x_rsp_offset, int y_rsp_offset) {
  // Multiplication of two Java long values stored on the stack
  // as illustrated below. Result is in rdx:rax.
  //
  // rsp ---> [  ??  ] \               \
  //            ....    | y_rsp_offset  |
  //          [ y_lo ] /  (in bytes)    | x_rsp_offset
  //          [ y_hi ]                  | (in bytes)
  //            ....                    |
  //          [ x_lo ]                 /
  //          [ x_hi ]
  //            ....
  //
  // Basic idea: lo(result) = lo(x_lo * y_lo)
  //             hi(result) = hi(x_lo * y_lo) + lo(x_hi * y_lo) + lo(x_lo * y_hi)
  Address x_hi(rsp, x_rsp_offset + wordSize); Address x_lo(rsp, x_rsp_offset);
  Address y_hi(rsp, y_rsp_offset + wordSize); Address y_lo(rsp, y_rsp_offset);
  Label quick;
  // load x_hi, y_hi and check if quick
  // multiplication is possible
  movl(rbx, x_hi);
  movl(rcx, y_hi);
  movl(rax, rbx);
  orl(rbx, rcx);                                 // rbx, = 0 <=> x_hi = 0 and y_hi = 0
  jcc(Assembler::zero, quick);                   // if rbx, = 0 do quick multiply
  // do full multiplication
  // 1st step
  mull(y_lo);                                    // x_hi * y_lo
  movl(rbx, rax);                                // save lo(x_hi * y_lo) in rbx,
  // 2nd step
  movl(rax, x_lo);
  mull(rcx);                                     // x_lo * y_hi
  addl(rbx, rax);                                // add lo(x_lo * y_hi) to rbx,
  // 3rd step
  bind(quick);                                   // note: rbx, = 0 if quick multiply!
  movl(rax, x_lo);
  mull(y_lo);                                    // x_lo * y_lo
  addl(rdx, rbx);                                // correct hi(x_lo * y_lo)
}

void MacroAssembler::lneg(Register hi, Register lo) {
  negl(lo);
  adcl(hi, 0);
  negl(hi);
}

void MacroAssembler::lshl(Register hi, Register lo) {
  // Java shift left long support (semantics as described in JVM spec., p.305)
  // (basic idea for shift counts s >= n: x << s == (x << n) << (s - n))
  // shift value is in rcx !
  assert(hi != rcx, "must not use rcx");
  assert(lo != rcx, "must not use rcx");
  const Register s = rcx;                        // shift count
  const int      n = BitsPerWord;
  Label L;
  andl(s, 0x3f);                                 // s := s & 0x3f (s < 0x40)
  cmpl(s, n);                                    // if (s < n)
  jcc(Assembler::less, L);                       // else (s >= n)
  movl(hi, lo);                                  // x := x << n
  xorl(lo, lo);
  // Note: subl(s, n) is not needed since the Intel shift instructions work rcx mod n!
  bind(L);                                       // s (mod n) < n
  shldl(hi, lo);                                 // x := x << s
  shll(lo);
}


void MacroAssembler::lshr(Register hi, Register lo, bool sign_extension) {
  // Java shift right long support (semantics as described in JVM spec., p.306 & p.310)
  // (basic idea for shift counts s >= n: x >> s == (x >> n) >> (s - n))
  assert(hi != rcx, "must not use rcx");
  assert(lo != rcx, "must not use rcx");
  const Register s = rcx;                        // shift count
  const int      n = BitsPerWord;
  Label L;
  andl(s, 0x3f);                                 // s := s & 0x3f (s < 0x40)
  cmpl(s, n);                                    // if (s < n)
  jcc(Assembler::less, L);                       // else (s >= n)
  movl(lo, hi);                                  // x := x >> n
  if (sign_extension) sarl(hi, 31);
  else                xorl(hi, hi);
  // Note: subl(s, n) is not needed since the Intel shift instructions work rcx mod n!
  bind(L);                                       // s (mod n) < n
  shrdl(lo, hi);                                 // x := x >> s
  if (sign_extension) sarl(hi);
  else                shrl(hi);
}

void MacroAssembler::movoop(Register dst, jobject obj) {
  mov_literal32(dst, (int32_t)obj, oop_Relocation::spec_for_immediate());
}

void MacroAssembler::movoop(Address dst, jobject obj) {
  mov_literal32(dst, (int32_t)obj, oop_Relocation::spec_for_immediate());
}

void MacroAssembler::movptr(Register dst, AddressLiteral src) {
  if (src.is_lval()) {
    mov_literal32(dst, (intptr_t)src.target(), src.rspec());
  } else {
    movl(dst, as_Address(src));
  }
}

void MacroAssembler::movptr(ArrayAddress dst, Register src) {
  movl(as_Address(dst), src);
}

void MacroAssembler::movptr(Register dst, ArrayAddress src) {
  movl(dst, as_Address(src));
}

// src should NEVER be a real pointer. Use AddressLiteral for true pointers
void MacroAssembler::movptr(Address dst, intptr_t src) {
  movl(dst, src);
}


void MacroAssembler::movsd(XMMRegister dst, AddressLiteral src) {
  movsd(dst, as_Address(src));
}

void MacroAssembler::pop_callee_saved_registers() {
  pop(rcx);
  pop(rdx);
  pop(rdi);
  pop(rsi);
}

void MacroAssembler::pop_fTOS() {
  fld_d(Address(rsp, 0));
  addl(rsp, 2 * wordSize);
}

void MacroAssembler::push_callee_saved_registers() {
  push(rsi);
  push(rdi);
  push(rdx);
  push(rcx);
}

void MacroAssembler::push_fTOS() {
  subl(rsp, 2 * wordSize);
  fstp_d(Address(rsp, 0));
}


void MacroAssembler::pushoop(jobject obj) {
  push_literal32((int32_t)obj, oop_Relocation::spec_for_immediate());
}


void MacroAssembler::pushptr(AddressLiteral src) {
  if (src.is_lval()) {
    push_literal32((int32_t)src.target(), src.rspec());
  } else {
    pushl(as_Address(src));
  }
}

void MacroAssembler::set_word_if_not_zero(Register dst) {
  xorl(dst, dst);
  set_byte_if_not_zero(dst);
}

static void pass_arg0(MacroAssembler* masm, Register arg) {
  masm->push(arg);
}

static void pass_arg1(MacroAssembler* masm, Register arg) {
  masm->push(arg);
}

static void pass_arg2(MacroAssembler* masm, Register arg) {
  masm->push(arg);
}

static void pass_arg3(MacroAssembler* masm, Register arg) {
  masm->push(arg);
}

#ifndef PRODUCT
extern "C" void findpc(intptr_t x);
#endif

void MacroAssembler::debug32(int rdi, int rsi, int rbp, int rsp, int rbx, int rdx, int rcx, int rax, int eip, char* msg) {
  // In order to get locks to work, we need to fake a in_VM state
  JavaThread* thread = JavaThread::current();
  JavaThreadState saved_state = thread->thread_state();
  thread->set_thread_state(_thread_in_vm);
  if (ShowMessageBoxOnError) {
    JavaThread* thread = JavaThread::current();
    JavaThreadState saved_state = thread->thread_state();
    thread->set_thread_state(_thread_in_vm);
    if (CountBytecodes || TraceBytecodes || StopInterpreterAt) {
      ttyLocker ttyl;
      BytecodeCounter::print();
    }
    // To see where a verify_oop failed, get $ebx+40/X for this frame.
    // This is the value of eip which points to where verify_oop will return.
    if (os::message_box(msg, "Execution stopped, print registers?")) {
      ttyLocker ttyl;
      tty->print_cr("eip = 0x%08x", eip);
#ifndef PRODUCT
      tty->cr();
      findpc(eip);
      tty->cr();
#endif
      tty->print_cr("rax, = 0x%08x", rax);
      tty->print_cr("rbx, = 0x%08x", rbx);
      tty->print_cr("rcx = 0x%08x", rcx);
      tty->print_cr("rdx = 0x%08x", rdx);
      tty->print_cr("rdi = 0x%08x", rdi);
      tty->print_cr("rsi = 0x%08x", rsi);
      tty->print_cr("rbp, = 0x%08x", rbp);
      tty->print_cr("rsp = 0x%08x", rsp);
      BREAKPOINT;
    }
  } else {
    ttyLocker ttyl;
    ::tty->print_cr("=============== DEBUG MESSAGE: %s ================\n", msg);
    assert(false, "DEBUG MESSAGE");
  }
  ThreadStateTransition::transition(thread, _thread_in_vm, saved_state);
}

void MacroAssembler::stop(const char* msg) {
  ExternalAddress message((address)msg);
  // push address of message
  pushptr(message.addr());
  { Label L; call(L, relocInfo::none); bind(L); }     // push eip
  pusha();                                           // push registers
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, MacroAssembler::debug32)));
  hlt();
}

void MacroAssembler::warn(const char* msg) {
  push_CPU_state();

  ExternalAddress message((address) msg);
  // push address of message
  pushptr(message.addr());

  call(RuntimeAddress(CAST_FROM_FN_PTR(address, warning)));
  addl(rsp, wordSize);       // discard argument
  pop_CPU_state();
}

#else // _LP64

// 64 bit versions

Address MacroAssembler::as_Address(AddressLiteral adr) {
  // amd64 always does this as a pc-rel
  // we can be absolute or disp based on the instruction type
  // jmp/call are displacements others are absolute
  assert(!adr.is_lval(), "must be rval");
  assert(reachable(adr), "must be");
  return Address((int32_t)(intptr_t)(adr.target() - pc()), adr.target(), adr.reloc());

}

Address MacroAssembler::as_Address(ArrayAddress adr) {
  AddressLiteral base = adr.base();
  lea(rscratch1, base);
  Address index = adr.index();
  assert(index._disp == 0, "must not have disp"); // maybe it can?
  Address array(rscratch1, index._index, index._scale, index._disp);
  return array;
}

int MacroAssembler::biased_locking_enter(Register lock_reg,
                                         Register obj_reg,
                                         Register swap_reg,
                                         Register tmp_reg,
                                         bool swap_reg_contains_mark,
                                         Label& done,
                                         Label* slow_case,
                                         BiasedLockingCounters* counters) {
  assert(UseBiasedLocking, "why call this otherwise?");
  assert(swap_reg == rax, "swap_reg must be rax for cmpxchgq");
  assert(tmp_reg != noreg, "tmp_reg must be supplied");
  assert_different_registers(lock_reg, obj_reg, swap_reg, tmp_reg);
  assert(markOopDesc::age_shift == markOopDesc::lock_bits + markOopDesc::biased_lock_bits, "biased locking makes assumptions about bit layout");
  Address mark_addr      (obj_reg, oopDesc::mark_offset_in_bytes());
  Address saved_mark_addr(lock_reg, 0);

  if (PrintBiasedLockingStatistics && counters == NULL)
    counters = BiasedLocking::counters();

  // Biased locking
  // See whether the lock is currently biased toward our thread and
  // whether the epoch is still valid
  // Note that the runtime guarantees sufficient alignment of JavaThread
  // pointers to allow age to be placed into low bits
  // First check to see whether biasing is even enabled for this object
  Label cas_label;
  int null_check_offset = -1;
  if (!swap_reg_contains_mark) {
    null_check_offset = offset();
    movq(swap_reg, mark_addr);
  }
  movq(tmp_reg, swap_reg);
  andq(tmp_reg, markOopDesc::biased_lock_mask_in_place);
  cmpq(tmp_reg, markOopDesc::biased_lock_pattern);
  jcc(Assembler::notEqual, cas_label);
  // The bias pattern is present in the object's header. Need to check
  // whether the bias owner and the epoch are both still current.
  load_prototype_header(tmp_reg, obj_reg);
  orq(tmp_reg, r15_thread);
  xorq(tmp_reg, swap_reg);
  andq(tmp_reg, ~((int) markOopDesc::age_mask_in_place));
  if (counters != NULL) {
    cond_inc32(Assembler::zero,
               ExternalAddress((address) counters->anonymously_biased_lock_entry_count_addr()));
  }
  jcc(Assembler::equal, done);

  Label try_revoke_bias;
  Label try_rebias;

  // At this point we know that the header has the bias pattern and
  // that we are not the bias owner in the current epoch. We need to
  // figure out more details about the state of the header in order to
  // know what operations can be legally performed on the object's
  // header.

  // If the low three bits in the xor result aren't clear, that means
  // the prototype header is no longer biased and we have to revoke
  // the bias on this object.
  testq(tmp_reg, markOopDesc::biased_lock_mask_in_place);
  jcc(Assembler::notZero, try_revoke_bias);

  // Biasing is still enabled for this data type. See whether the
  // epoch of the current bias is still valid, meaning that the epoch
  // bits of the mark word are equal to the epoch bits of the
  // prototype header. (Note that the prototype header's epoch bits
  // only change at a safepoint.) If not, attempt to rebias the object
  // toward the current thread. Note that we must be absolutely sure
  // that the current epoch is invalid in order to do this because
  // otherwise the manipulations it performs on the mark word are
  // illegal.
  testq(tmp_reg, markOopDesc::epoch_mask_in_place);
  jcc(Assembler::notZero, try_rebias);

  // The epoch of the current bias is still valid but we know nothing
  // about the owner; it might be set or it might be clear. Try to
  // acquire the bias of the object using an atomic operation. If this
  // fails we will go in to the runtime to revoke the object's bias.
  // Note that we first construct the presumed unbiased header so we
  // don't accidentally blow away another thread's valid bias.
  andq(swap_reg,
       markOopDesc::biased_lock_mask_in_place | markOopDesc::age_mask_in_place | markOopDesc::epoch_mask_in_place);
  movq(tmp_reg, swap_reg);
  orq(tmp_reg, r15_thread);
  if (os::is_MP()) {
    lock();
  }
  cmpxchgq(tmp_reg, Address(obj_reg, 0));
  // If the biasing toward our thread failed, this means that
  // another thread succeeded in biasing it toward itself and we
  // need to revoke that bias. The revocation will occur in the
  // interpreter runtime in the slow case.
  if (counters != NULL) {
    cond_inc32(Assembler::zero,
               ExternalAddress((address) counters->anonymously_biased_lock_entry_count_addr()));
  }
  if (slow_case != NULL) {
    jcc(Assembler::notZero, *slow_case);
  }
  jmp(done);

  bind(try_rebias);
  // At this point we know the epoch has expired, meaning that the
  // current "bias owner", if any, is actually invalid. Under these
  // circumstances _only_, we are allowed to use the current header's
  // value as the comparison value when doing the cas to acquire the
  // bias in the current epoch. In other words, we allow transfer of
  // the bias from one thread to another directly in this situation.
  //
  // FIXME: due to a lack of registers we currently blow away the age
  // bits in this situation. Should attempt to preserve them.
  load_prototype_header(tmp_reg, obj_reg);
  orq(tmp_reg, r15_thread);
  if (os::is_MP()) {
    lock();
  }
  cmpxchgq(tmp_reg, Address(obj_reg, 0));
  // If the biasing toward our thread failed, then another thread
  // succeeded in biasing it toward itself and we need to revoke that
  // bias. The revocation will occur in the runtime in the slow case.
  if (counters != NULL) {
    cond_inc32(Assembler::zero,
               ExternalAddress((address) counters->rebiased_lock_entry_count_addr()));
  }
  if (slow_case != NULL) {
    jcc(Assembler::notZero, *slow_case);
  }
  jmp(done);

  bind(try_revoke_bias);
  // The prototype mark in the klass doesn't have the bias bit set any
  // more, indicating that objects of this data type are not supposed
  // to be biased any more. We are going to try to reset the mark of
  // this object to the prototype value and fall through to the
  // CAS-based locking scheme. Note that if our CAS fails, it means
  // that another thread raced us for the privilege of revoking the
  // bias of this particular object, so it's okay to continue in the
  // normal locking code.
  //
  // FIXME: due to a lack of registers we currently blow away the age
  // bits in this situation. Should attempt to preserve them.
  load_prototype_header(tmp_reg, obj_reg);
  if (os::is_MP()) {
    lock();
  }
  cmpxchgq(tmp_reg, Address(obj_reg, 0));
  // Fall through to the normal CAS-based lock, because no matter what
  // the result of the above CAS, some thread must have succeeded in
  // removing the bias bit from the object's header.
  if (counters != NULL) {
    cond_inc32(Assembler::zero,
               ExternalAddress((address) counters->revoked_lock_entry_count_addr()));
  }

  bind(cas_label);

  return null_check_offset;
}

void MacroAssembler::call_VM_leaf_base(address entry_point, int num_args) {
  Label L, E;

#ifdef _WIN64
  // Windows always allocates space for it's register args
  assert(num_args <= 4, "only register arguments supported");
  subq(rsp,  frame::arg_reg_save_area_bytes);
#endif

  // Align stack if necessary
  testl(rsp, 15);
  jcc(Assembler::zero, L);

  subq(rsp, 8);
  {
    call(RuntimeAddress(entry_point));
  }
  addq(rsp, 8);
  jmp(E);

  bind(L);
  {
    call(RuntimeAddress(entry_point));
  }

  bind(E);

#ifdef _WIN64
  // restore stack pointer
  addq(rsp, frame::arg_reg_save_area_bytes);
#endif

}

void MacroAssembler::cmp64(Register src1, AddressLiteral src2) {
  assert(!src2.is_lval(), "should use cmpptr");

  if (reachable(src2)) {
    cmpq(src1, as_Address(src2));
  } else {
    lea(rscratch1, src2);
    Assembler::cmpq(src1, Address(rscratch1, 0));
  }
}

int MacroAssembler::corrected_idivq(Register reg) {
  // Full implementation of Java ldiv and lrem; checks for special
  // case as described in JVM spec., p.243 & p.271.  The function
  // returns the (pc) offset of the idivl instruction - may be needed
  // for implicit exceptions.
  //
  //         normal case                           special case
  //
  // input : rax: dividend                         min_long
  //         reg: divisor   (may not be eax/edx)   -1
  //
  // output: rax: quotient  (= rax idiv reg)       min_long
  //         rdx: remainder (= rax irem reg)       0
  assert(reg != rax && reg != rdx, "reg cannot be rax or rdx register");
  static const int64_t min_long = 0x8000000000000000;
  Label normal_case, special_case;

  // check for special case
  cmp64(rax, ExternalAddress((address) &min_long));
  jcc(Assembler::notEqual, normal_case);
  xorl(rdx, rdx); // prepare rdx for possible special case (where
                  // remainder = 0)
  cmpq(reg, -1);
  jcc(Assembler::equal, special_case);

  // handle normal case
  bind(normal_case);
  cdqq();
  int idivq_offset = offset();
  idivq(reg);

  // normal and special case exit
  bind(special_case);

  return idivq_offset;
}

void MacroAssembler::decrementq(Register reg, int value) {
  if (value == min_jint) { subq(reg, value); return; }
  if (value <  0) { incrementq(reg, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { decq(reg) ; return; }
  /* else */      { subq(reg, value)       ; return; }
}

void MacroAssembler::decrementq(Address dst, int value) {
  if (value == min_jint) { subq(dst, value); return; }
  if (value <  0) { incrementq(dst, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { decq(dst) ; return; }
  /* else */      { subq(dst, value)       ; return; }
}

void MacroAssembler::fat_nop() {
  // A 5 byte nop that is safe for patching (see patch_verified_entry)
  // Recommened sequence from 'Software Optimization Guide for the AMD
  // Hammer Processor'
  emit_byte(0x66);
  emit_byte(0x66);
  emit_byte(0x90);
  emit_byte(0x66);
  emit_byte(0x90);
}

void MacroAssembler::incrementq(Register reg, int value) {
  if (value == min_jint) { addq(reg, value); return; }
  if (value <  0) { decrementq(reg, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { incq(reg) ; return; }
  /* else */      { addq(reg, value)       ; return; }
}

void MacroAssembler::incrementq(Address dst, int value) {
  if (value == min_jint) { addq(dst, value); return; }
  if (value <  0) { decrementq(dst, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { incq(dst) ; return; }
  /* else */      { addq(dst, value)       ; return; }
}

// 32bit can do a case table jump in one instruction but we no longer allow the base
// to be installed in the Address class
void MacroAssembler::jump(ArrayAddress entry) {
  lea(rscratch1, entry.base());
  Address dispatch = entry.index();
  assert(dispatch._base == noreg, "must be");
  dispatch._base = rscratch1;
  jmp(dispatch);
}

void MacroAssembler::lcmp2int(Register x_hi, Register x_lo, Register y_hi, Register y_lo) {
  ShouldNotReachHere(); // 64bit doesn't use two regs
  cmpq(x_lo, y_lo);
}

void MacroAssembler::lea(Register dst, AddressLiteral src) {
    mov_literal64(dst, (intptr_t)src.target(), src.rspec());
}

void MacroAssembler::lea(Address dst, AddressLiteral adr) {
  mov_literal64(rscratch1, (intptr_t)adr.target(), adr.rspec());
  movptr(dst, rscratch1);
}

void MacroAssembler::leave() {
  // %%% is this really better? Why not on 32bit too?
  emit_byte(0xC9); // LEAVE
}

void MacroAssembler::lneg(Register hi, Register lo) {
  ShouldNotReachHere(); // 64bit doesn't use two regs
  negq(lo);
}

void MacroAssembler::movoop(Register dst, jobject obj) {
  mov_literal64(dst, (intptr_t)obj, oop_Relocation::spec_for_immediate());
}

void MacroAssembler::movoop(Address dst, jobject obj) {
  mov_literal64(rscratch1, (intptr_t)obj, oop_Relocation::spec_for_immediate());
  movq(dst, rscratch1);
}

void MacroAssembler::movptr(Register dst, AddressLiteral src) {
  if (src.is_lval()) {
    mov_literal64(dst, (intptr_t)src.target(), src.rspec());
  } else {
    if (reachable(src)) {
      movq(dst, as_Address(src));
    } else {
      lea(rscratch1, src);
      movq(dst, Address(rscratch1,0));
    }
  }
}

void MacroAssembler::movptr(ArrayAddress dst, Register src) {
  movq(as_Address(dst), src);
}

void MacroAssembler::movptr(Register dst, ArrayAddress src) {
  movq(dst, as_Address(src));
}

// src should NEVER be a real pointer. Use AddressLiteral for true pointers
void MacroAssembler::movptr(Address dst, intptr_t src) {
  mov64(rscratch1, src);
  movq(dst, rscratch1);
}

// These are mostly for initializing NULL
void MacroAssembler::movptr(Address dst, int32_t src) {
  movslq(dst, src);
}

void MacroAssembler::movptr(Register dst, int32_t src) {
  mov64(dst, (intptr_t)src);
}

void MacroAssembler::pushoop(jobject obj) {
  movoop(rscratch1, obj);
  push(rscratch1);
}

void MacroAssembler::pushptr(AddressLiteral src) {
  lea(rscratch1, src);
  if (src.is_lval()) {
    push(rscratch1);
  } else {
    pushq(Address(rscratch1, 0));
  }
}

void MacroAssembler::reset_last_Java_frame(bool clear_fp,
                                           bool clear_pc) {
  // we must set sp to zero to clear frame
  movptr(Address(r15_thread, JavaThread::last_Java_sp_offset()), NULL_WORD);
  // must clear fp, so that compiled frames are not confused; it is
  // possible that we need it only for debugging
  if (clear_fp) {
    movptr(Address(r15_thread, JavaThread::last_Java_fp_offset()), NULL_WORD);
  }

  if (clear_pc) {
    movptr(Address(r15_thread, JavaThread::last_Java_pc_offset()), NULL_WORD);
  }
}

void MacroAssembler::set_last_Java_frame(Register last_java_sp,
                                         Register last_java_fp,
                                         address  last_java_pc) {
  // determine last_java_sp register
  if (!last_java_sp->is_valid()) {
    last_java_sp = rsp;
  }

  // last_java_fp is optional
  if (last_java_fp->is_valid()) {
    movptr(Address(r15_thread, JavaThread::last_Java_fp_offset()),
           last_java_fp);
  }

  // last_java_pc is optional
  if (last_java_pc != NULL) {
    Address java_pc(r15_thread,
                    JavaThread::frame_anchor_offset() + JavaFrameAnchor::last_Java_pc_offset());
    lea(rscratch1, InternalAddress(last_java_pc));
    movptr(java_pc, rscratch1);
  }

  movptr(Address(r15_thread, JavaThread::last_Java_sp_offset()), last_java_sp);
}

static void pass_arg0(MacroAssembler* masm, Register arg) {
  if (c_rarg0 != arg ) {
    masm->mov(c_rarg0, arg);
  }
}

static void pass_arg1(MacroAssembler* masm, Register arg) {
  if (c_rarg1 != arg ) {
    masm->mov(c_rarg1, arg);
  }
}

static void pass_arg2(MacroAssembler* masm, Register arg) {
  if (c_rarg2 != arg ) {
    masm->mov(c_rarg2, arg);
  }
}

static void pass_arg3(MacroAssembler* masm, Register arg) {
  if (c_rarg3 != arg ) {
    masm->mov(c_rarg3, arg);
  }
}

void MacroAssembler::stop(const char* msg) {
  address rip = pc();
  pusha(); // get regs on stack
  lea(c_rarg0, ExternalAddress((address) msg));
  lea(c_rarg1, InternalAddress(rip));
  movq(c_rarg2, rsp); // pass pointer to regs array
  andq(rsp, -16); // align stack as required by ABI
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, MacroAssembler::debug64)));
  hlt();
}

void MacroAssembler::warn(const char* msg) {
  push(r12);
  movq(r12, rsp);
  andq(rsp, -16);     // align stack as required by push_CPU_state and call

  push_CPU_state();   // keeps alignment at 16 bytes
  lea(c_rarg0, ExternalAddress((address) msg));
  call_VM_leaf(CAST_FROM_FN_PTR(address, warning), c_rarg0);
  pop_CPU_state();

  movq(rsp, r12);
  pop(r12);
}

#ifndef PRODUCT
extern "C" void findpc(intptr_t x);
#endif

void MacroAssembler::debug64(char* msg, int64_t pc, int64_t regs[]) {
  // In order to get locks to work, we need to fake a in_VM state
  if (ShowMessageBoxOnError ) {
    JavaThread* thread = JavaThread::current();
    JavaThreadState saved_state = thread->thread_state();
    thread->set_thread_state(_thread_in_vm);
#ifndef PRODUCT
    if (CountBytecodes || TraceBytecodes || StopInterpreterAt) {
      ttyLocker ttyl;
      BytecodeCounter::print();
    }
#endif
    // To see where a verify_oop failed, get $ebx+40/X for this frame.
    // XXX correct this offset for amd64
    // This is the value of eip which points to where verify_oop will return.
    if (os::message_box(msg, "Execution stopped, print registers?")) {
      ttyLocker ttyl;
      tty->print_cr("rip = 0x%016lx", pc);
#ifndef PRODUCT
      tty->cr();
      findpc(pc);
      tty->cr();
#endif
      tty->print_cr("rax = 0x%016lx", regs[15]);
      tty->print_cr("rbx = 0x%016lx", regs[12]);
      tty->print_cr("rcx = 0x%016lx", regs[14]);
      tty->print_cr("rdx = 0x%016lx", regs[13]);
      tty->print_cr("rdi = 0x%016lx", regs[8]);
      tty->print_cr("rsi = 0x%016lx", regs[9]);
      tty->print_cr("rbp = 0x%016lx", regs[10]);
      tty->print_cr("rsp = 0x%016lx", regs[11]);
      tty->print_cr("r8  = 0x%016lx", regs[7]);
      tty->print_cr("r9  = 0x%016lx", regs[6]);
      tty->print_cr("r10 = 0x%016lx", regs[5]);
      tty->print_cr("r11 = 0x%016lx", regs[4]);
      tty->print_cr("r12 = 0x%016lx", regs[3]);
      tty->print_cr("r13 = 0x%016lx", regs[2]);
      tty->print_cr("r14 = 0x%016lx", regs[1]);
      tty->print_cr("r15 = 0x%016lx", regs[0]);
      BREAKPOINT;
    }
    ThreadStateTransition::transition(thread, _thread_in_vm, saved_state);
  } else {
    ttyLocker ttyl;
    ::tty->print_cr("=============== DEBUG MESSAGE: %s ================\n",
                    msg);
  }
}

#endif // _LP64

// Now versions that are common to 32/64 bit

void MacroAssembler::addptr(Register dst, int32_t imm32) {
  LP64_ONLY(addq(dst, imm32)) NOT_LP64(addl(dst, imm32));
}

void MacroAssembler::addptr(Register dst, Register src) {
  LP64_ONLY(addq(dst, src)) NOT_LP64(addl(dst, src));
}

void MacroAssembler::addptr(Address dst, Register src) {
  LP64_ONLY(addq(dst, src)) NOT_LP64(addl(dst, src));
}

void MacroAssembler::align(int modulus) {
  if (offset() % modulus != 0) {
    nop(modulus - (offset() % modulus));
  }
}

void MacroAssembler::andpd(XMMRegister dst, AddressLiteral src) {
  if (reachable(src)) {
    andpd(dst, as_Address(src));
  } else {
    lea(rscratch1, src);
    andpd(dst, Address(rscratch1, 0));
  }
}

void MacroAssembler::andptr(Register dst, int32_t imm32) {
  LP64_ONLY(andq(dst, imm32)) NOT_LP64(andl(dst, imm32));
}

void MacroAssembler::atomic_incl(AddressLiteral counter_addr) {
  pushf();
  if (os::is_MP())
    lock();
  incrementl(counter_addr);
  popf();
}

// Writes to stack successive pages until offset reached to check for
// stack overflow + shadow pages.  This clobbers tmp.
void MacroAssembler::bang_stack_size(Register size, Register tmp) {
  movptr(tmp, rsp);
  // Bang stack for total size given plus shadow page size.
  // Bang one page at a time because large size can bang beyond yellow and
  // red zones.
  Label loop;
  bind(loop);
  movl(Address(tmp, (-os::vm_page_size())), size );
  subptr(tmp, os::vm_page_size());
  subl(size, os::vm_page_size());
  jcc(Assembler::greater, loop);

  // Bang down shadow pages too.
  // The -1 because we already subtracted 1 page.
  for (int i = 0; i< StackShadowPages-1; i++) {
    // this could be any sized move but this is can be a debugging crumb
    // so the bigger the better.
    movptr(Address(tmp, (-i*os::vm_page_size())), size );
  }
}

void MacroAssembler::biased_locking_exit(Register obj_reg, Register temp_reg, Label& done) {
  assert(UseBiasedLocking, "why call this otherwise?");

  // Check for biased locking unlock case, which is a no-op
  // Note: we do not have to check the thread ID for two reasons.
  // First, the interpreter checks for IllegalMonitorStateException at
  // a higher level. Second, if the bias was revoked while we held the
  // lock, the object could not be rebiased toward another thread, so
  // the bias bit would be clear.
  movptr(temp_reg, Address(obj_reg, oopDesc::mark_offset_in_bytes()));
  andptr(temp_reg, markOopDesc::biased_lock_mask_in_place);
  cmpptr(temp_reg, markOopDesc::biased_lock_pattern);
  jcc(Assembler::equal, done);
}

void MacroAssembler::c2bool(Register x) {
  // implements x == 0 ? 0 : 1
  // note: must only look at least-significant byte of x
  //       since C-style booleans are stored in one byte
  //       only! (was bug)
  andl(x, 0xFF);
  setb(Assembler::notZero, x);
}

// Wouldn't need if AddressLiteral version had new name
void MacroAssembler::call(Label& L, relocInfo::relocType rtype) {
  Assembler::call(L, rtype);
}

void MacroAssembler::call(Register entry) {
  Assembler::call(entry);
}

void MacroAssembler::call(AddressLiteral entry) {
  if (reachable(entry)) {
    Assembler::call_literal(entry.target(), entry.rspec());
  } else {
    lea(rscratch1, entry);
    Assembler::call(rscratch1);
  }
}

// Implementation of call_VM versions

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             bool check_exceptions) {
  Label C, E;
  call(C, relocInfo::none);
  jmp(E);

  bind(C);
  call_VM_helper(oop_result, entry_point, 0, check_exceptions);
  ret(0);

  bind(E);
}

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             Register arg_1,
                             bool check_exceptions) {
  Label C, E;
  call(C, relocInfo::none);
  jmp(E);

  bind(C);
  pass_arg1(this, arg_1);
  call_VM_helper(oop_result, entry_point, 1, check_exceptions);
  ret(0);

  bind(E);
}

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             bool check_exceptions) {
  Label C, E;
  call(C, relocInfo::none);
  jmp(E);

  bind(C);

  LP64_ONLY(assert(arg_1 != c_rarg2, "smashed arg"));

  pass_arg2(this, arg_2);
  pass_arg1(this, arg_1);
  call_VM_helper(oop_result, entry_point, 2, check_exceptions);
  ret(0);

  bind(E);
}

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             Register arg_3,
                             bool check_exceptions) {
  Label C, E;
  call(C, relocInfo::none);
  jmp(E);

  bind(C);

  LP64_ONLY(assert(arg_1 != c_rarg3, "smashed arg"));
  LP64_ONLY(assert(arg_2 != c_rarg3, "smashed arg"));
  pass_arg3(this, arg_3);

  LP64_ONLY(assert(arg_1 != c_rarg2, "smashed arg"));
  pass_arg2(this, arg_2);

  pass_arg1(this, arg_1);
  call_VM_helper(oop_result, entry_point, 3, check_exceptions);
  ret(0);

  bind(E);
}

void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             int number_of_arguments,
                             bool check_exceptions) {
  Register thread = LP64_ONLY(r15_thread) NOT_LP64(noreg);
  call_VM_base(oop_result, thread, last_java_sp, entry_point, number_of_arguments, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             Register arg_1,
                             bool check_exceptions) {
  pass_arg1(this, arg_1);
  call_VM(oop_result, last_java_sp, entry_point, 1, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             bool check_exceptions) {

  LP64_ONLY(assert(arg_1 != c_rarg2, "smashed arg"));
  pass_arg2(this, arg_2);
  pass_arg1(this, arg_1);
  call_VM(oop_result, last_java_sp, entry_point, 2, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             Register arg_3,
                             bool check_exceptions) {
  LP64_ONLY(assert(arg_1 != c_rarg3, "smashed arg"));
  LP64_ONLY(assert(arg_2 != c_rarg3, "smashed arg"));
  pass_arg3(this, arg_3);
  LP64_ONLY(assert(arg_1 != c_rarg2, "smashed arg"));
  pass_arg2(this, arg_2);
  pass_arg1(this, arg_1);
  call_VM(oop_result, last_java_sp, entry_point, 3, check_exceptions);
}

void MacroAssembler::call_VM_base(Register oop_result,
                                  Register java_thread,
                                  Register last_java_sp,
                                  address  entry_point,
                                  int      number_of_arguments,
                                  bool     check_exceptions) {
  // determine java_thread register
  if (!java_thread->is_valid()) {
#ifdef _LP64
    java_thread = r15_thread;
#else
    java_thread = rdi;
    get_thread(java_thread);
#endif // LP64
  }
  // determine last_java_sp register
  if (!last_java_sp->is_valid()) {
    last_java_sp = rsp;
  }
  // debugging support
  assert(number_of_arguments >= 0   , "cannot have negative number of arguments");
  LP64_ONLY(assert(java_thread == r15_thread, "unexpected register"));
  assert(java_thread != oop_result  , "cannot use the same register for java_thread & oop_result");
  assert(java_thread != last_java_sp, "cannot use the same register for java_thread & last_java_sp");

  // push java thread (becomes first argument of C function)

  NOT_LP64(push(java_thread); number_of_arguments++);
  LP64_ONLY(mov(c_rarg0, r15_thread));

  // set last Java frame before call
  assert(last_java_sp != rbp, "can't use ebp/rbp");

  // Only interpreter should have to set fp
  set_last_Java_frame(java_thread, last_java_sp, rbp, NULL);

  // do the call, remove parameters
  MacroAssembler::call_VM_leaf_base(entry_point, number_of_arguments);

  // restore the thread (cannot use the pushed argument since arguments
  // may be overwritten by C code generated by an optimizing compiler);
  // however can use the register value directly if it is callee saved.
  if (LP64_ONLY(true ||) java_thread == rdi || java_thread == rsi) {
    // rdi & rsi (also r15) are callee saved -> nothing to do
#ifdef ASSERT
    guarantee(java_thread != rax, "change this code");
    push(rax);
    { Label L;
      get_thread(rax);
      cmpptr(java_thread, rax);
      jcc(Assembler::equal, L);
      stop("MacroAssembler::call_VM_base: rdi not callee saved?");
      bind(L);
    }
    pop(rax);
#endif
  } else {
    get_thread(java_thread);
  }
  // reset last Java frame
  // Only interpreter should have to clear fp
  reset_last_Java_frame(java_thread, true, false);

#ifndef CC_INTERP
   // C++ interp handles this in the interpreter
  check_and_handle_popframe(java_thread);
  check_and_handle_earlyret(java_thread);
#endif /* CC_INTERP */

  if (check_exceptions) {
    // check for pending exceptions (java_thread is set upon return)
    cmpptr(Address(java_thread, Thread::pending_exception_offset()), (int32_t) NULL_WORD);
#ifndef _LP64
    jump_cc(Assembler::notEqual,
            RuntimeAddress(StubRoutines::forward_exception_entry()));
#else
    // This used to conditionally jump to forward_exception however it is
    // possible if we relocate that the branch will not reach. So we must jump
    // around so we can always reach

    Label ok;
    jcc(Assembler::equal, ok);
    jump(RuntimeAddress(StubRoutines::forward_exception_entry()));
    bind(ok);
#endif // LP64
  }

  // get oop result if there is one and reset the value in the thread
  if (oop_result->is_valid()) {
    movptr(oop_result, Address(java_thread, JavaThread::vm_result_offset()));
    movptr(Address(java_thread, JavaThread::vm_result_offset()), NULL_WORD);
    verify_oop(oop_result, "broken oop in call_VM_base");
  }
}

void MacroAssembler::call_VM_helper(Register oop_result, address entry_point, int number_of_arguments, bool check_exceptions) {

  // Calculate the value for last_Java_sp
  // somewhat subtle. call_VM does an intermediate call
  // which places a return address on the stack just under the
  // stack pointer as the user finsihed with it. This allows
  // use to retrieve last_Java_pc from last_Java_sp[-1].
  // On 32bit we then have to push additional args on the stack to accomplish
  // the actual requested call. On 64bit call_VM only can use register args
  // so the only extra space is the return address that call_VM created.
  // This hopefully explains the calculations here.

#ifdef _LP64
  // We've pushed one address, correct last_Java_sp
  lea(rax, Address(rsp, wordSize));
#else
  lea(rax, Address(rsp, (1 + number_of_arguments) * wordSize));
#endif // LP64

  call_VM_base(oop_result, noreg, rax, entry_point, number_of_arguments, check_exceptions);

}

void MacroAssembler::call_VM_leaf(address entry_point, int number_of_arguments) {
  call_VM_leaf_base(entry_point, number_of_arguments);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_0) {
  pass_arg0(this, arg_0);
  call_VM_leaf(entry_point, 1);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_0, Register arg_1) {

  LP64_ONLY(assert(arg_0 != c_rarg1, "smashed arg"));
  pass_arg1(this, arg_1);
  pass_arg0(this, arg_0);
  call_VM_leaf(entry_point, 2);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_0, Register arg_1, Register arg_2) {
  LP64_ONLY(assert(arg_0 != c_rarg2, "smashed arg"));
  LP64_ONLY(assert(arg_1 != c_rarg2, "smashed arg"));
  pass_arg2(this, arg_2);
  LP64_ONLY(assert(arg_0 != c_rarg1, "smashed arg"));
  pass_arg1(this, arg_1);
  pass_arg0(this, arg_0);
  call_VM_leaf(entry_point, 3);
}

void MacroAssembler::check_and_handle_earlyret(Register java_thread) {
}

void MacroAssembler::check_and_handle_popframe(Register java_thread) {
}

void MacroAssembler::cmp32(AddressLiteral src1, int32_t imm) {
  if (reachable(src1)) {
    cmpl(as_Address(src1), imm);
  } else {
    lea(rscratch1, src1);
    cmpl(Address(rscratch1, 0), imm);
  }
}

void MacroAssembler::cmp32(Register src1, AddressLiteral src2) {
  assert(!src2.is_lval(), "use cmpptr");
  if (reachable(src2)) {
    cmpl(src1, as_Address(src2));
  } else {
    lea(rscratch1, src2);
    cmpl(src1, Address(rscratch1, 0));
  }
}

void MacroAssembler::cmp32(Register src1, int32_t imm) {
  Assembler::cmpl(src1, imm);
}

void MacroAssembler::cmp32(Register src1, Address src2) {
  Assembler::cmpl(src1, src2);
}

void MacroAssembler::cmpsd2int(XMMRegister opr1, XMMRegister opr2, Register dst, bool unordered_is_less) {
  ucomisd(opr1, opr2);

  Label L;
  if (unordered_is_less) {
    movl(dst, -1);
    jcc(Assembler::parity, L);
    jcc(Assembler::below , L);
    movl(dst, 0);
    jcc(Assembler::equal , L);
    increment(dst);
  } else { // unordered is greater
    movl(dst, 1);
    jcc(Assembler::parity, L);
    jcc(Assembler::above , L);
    movl(dst, 0);
    jcc(Assembler::equal , L);
    decrementl(dst);
  }
  bind(L);
}

void MacroAssembler::cmpss2int(XMMRegister opr1, XMMRegister opr2, Register dst, bool unordered_is_less) {
  ucomiss(opr1, opr2);

  Label L;
  if (unordered_is_less) {
    movl(dst, -1);
    jcc(Assembler::parity, L);
    jcc(Assembler::below , L);
    movl(dst, 0);
    jcc(Assembler::equal , L);
    increment(dst);
  } else { // unordered is greater
    movl(dst, 1);
    jcc(Assembler::parity, L);
    jcc(Assembler::above , L);
    movl(dst, 0);
    jcc(Assembler::equal , L);
    decrementl(dst);
  }
  bind(L);
}


void MacroAssembler::cmp8(AddressLiteral src1, int imm) {
  if (reachable(src1)) {
    cmpb(as_Address(src1), imm);
  } else {
    lea(rscratch1, src1);
    cmpb(Address(rscratch1, 0), imm);
  }
}

void MacroAssembler::cmpptr(Register src1, AddressLiteral src2) {
#ifdef _LP64
  if (src2.is_lval()) {
    movptr(rscratch1, src2);
    Assembler::cmpq(src1, rscratch1);
  } else if (reachable(src2)) {
    cmpq(src1, as_Address(src2));
  } else {
    lea(rscratch1, src2);
    Assembler::cmpq(src1, Address(rscratch1, 0));
  }
#else
  if (src2.is_lval()) {
    cmp_literal32(src1, (int32_t) src2.target(), src2.rspec());
  } else {
    cmpl(src1, as_Address(src2));
  }
#endif // _LP64
}

void MacroAssembler::cmpptr(Address src1, AddressLiteral src2) {
  assert(src2.is_lval(), "not a mem-mem compare");
#ifdef _LP64
  // moves src2's literal address
  movptr(rscratch1, src2);
  Assembler::cmpq(src1, rscratch1);
#else
  cmp_literal32(src1, (int32_t) src2.target(), src2.rspec());
#endif // _LP64
}

void MacroAssembler::locked_cmpxchgptr(Register reg, AddressLiteral adr) {
  if (reachable(adr)) {
    if (os::is_MP())
      lock();
    cmpxchgptr(reg, as_Address(adr));
  } else {
    lea(rscratch1, adr);
    if (os::is_MP())
      lock();
    cmpxchgptr(reg, Address(rscratch1, 0));
  }
}

void MacroAssembler::cmpxchgptr(Register reg, Address adr) {
  LP64_ONLY(cmpxchgq(reg, adr)) NOT_LP64(cmpxchgl(reg, adr));
}

void MacroAssembler::comisd(XMMRegister dst, AddressLiteral src) {
  if (reachable(src)) {
    comisd(dst, as_Address(src));
  } else {
    lea(rscratch1, src);
    comisd(dst, Address(rscratch1, 0));
  }
}

void MacroAssembler::comiss(XMMRegister dst, AddressLiteral src) {
  if (reachable(src)) {
    comiss(dst, as_Address(src));
  } else {
    lea(rscratch1, src);
    comiss(dst, Address(rscratch1, 0));
  }
}


void MacroAssembler::cond_inc32(Condition cond, AddressLiteral counter_addr) {
  Condition negated_cond = negate_condition(cond);
  Label L;
  jcc(negated_cond, L);
  atomic_incl(counter_addr);
  bind(L);
}

int MacroAssembler::corrected_idivl(Register reg) {
  // Full implementation of Java idiv and irem; checks for
  // special case as described in JVM spec., p.243 & p.271.
  // The function returns the (pc) offset of the idivl
  // instruction - may be needed for implicit exceptions.
  //
  //         normal case                           special case
  //
  // input : rax,: dividend                         min_int
  //         reg: divisor   (may not be rax,/rdx)   -1
  //
  // output: rax,: quotient  (= rax, idiv reg)       min_int
  //         rdx: remainder (= rax, irem reg)       0
  assert(reg != rax && reg != rdx, "reg cannot be rax, or rdx register");
  const int min_int = 0x80000000;
  Label normal_case, special_case;

  // check for special case
  cmpl(rax, min_int);
  jcc(Assembler::notEqual, normal_case);
  xorl(rdx, rdx); // prepare rdx for possible special case (where remainder = 0)
  cmpl(reg, -1);
  jcc(Assembler::equal, special_case);

  // handle normal case
  bind(normal_case);
  cdql();
  int idivl_offset = offset();
  idivl(reg);

  // normal and special case exit
  bind(special_case);

  return idivl_offset;
}



void MacroAssembler::decrementl(Register reg, int value) {
  if (value == min_jint) {subl(reg, value) ; return; }
  if (value <  0) { incrementl(reg, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { decl(reg) ; return; }
  /* else */      { subl(reg, value)       ; return; }
}

void MacroAssembler::decrementl(Address dst, int value) {
  if (value == min_jint) {subl(dst, value) ; return; }
  if (value <  0) { incrementl(dst, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { decl(dst) ; return; }
  /* else */      { subl(dst, value)       ; return; }
}

void MacroAssembler::division_with_shift (Register reg, int shift_value) {
  assert (shift_value > 0, "illegal shift value");
  Label _is_positive;
  testl (reg, reg);
  jcc (Assembler::positive, _is_positive);
  int offset = (1 << shift_value) - 1 ;

  if (offset == 1) {
    incrementl(reg);
  } else {
    addl(reg, offset);
  }

  bind (_is_positive);
  sarl(reg, shift_value);
}

// !defined(COMPILER2) is because of stupid core builds
#if !defined(_LP64) || defined(COMPILER1) || !defined(COMPILER2)
void MacroAssembler::empty_FPU_stack() {
  if (VM_Version::supports_mmx()) {
    emms();
  } else {
    for (int i = 8; i-- > 0; ) ffree(i);
  }
}
#endif // !LP64 || C1 || !C2


// Defines obj, preserves var_size_in_bytes
void MacroAssembler::eden_allocate(Register obj,
                                   Register var_size_in_bytes,
                                   int con_size_in_bytes,
                                   Register t1,
                                   Label& slow_case) {
  assert(obj == rax, "obj must be in rax, for cmpxchg");
  assert_different_registers(obj, var_size_in_bytes, t1);
  if (CMSIncrementalMode || !Universe::heap()->supports_inline_contig_alloc()) {
    jmp(slow_case);
  } else {
    Register end = t1;
    Label retry;
    bind(retry);
    ExternalAddress heap_top((address) Universe::heap()->top_addr());
    movptr(obj, heap_top);
    if (var_size_in_bytes == noreg) {
      lea(end, Address(obj, con_size_in_bytes));
    } else {
      lea(end, Address(obj, var_size_in_bytes, Address::times_1));
    }
    // if end < obj then we wrapped around => object too long => slow case
    cmpptr(end, obj);
    jcc(Assembler::below, slow_case);
    cmpptr(end, ExternalAddress((address) Universe::heap()->end_addr()));
    jcc(Assembler::above, slow_case);
    // Compare obj with the top addr, and if still equal, store the new top addr in
    // end at the address of the top addr pointer. Sets ZF if was equal, and clears
    // it otherwise. Use lock prefix for atomicity on MPs.
    locked_cmpxchgptr(end, heap_top);
    jcc(Assembler::notEqual, retry);
  }
}

void MacroAssembler::enter() {
  push(rbp);
  mov(rbp, rsp);
}

void MacroAssembler::fcmp(Register tmp) {
  fcmp(tmp, 1, true, true);
}

void MacroAssembler::fcmp(Register tmp, int index, bool pop_left, bool pop_right) {
  assert(!pop_right || pop_left, "usage error");
  if (VM_Version::supports_cmov()) {
    assert(tmp == noreg, "unneeded temp");
    if (pop_left) {
      fucomip(index);
    } else {
      fucomi(index);
    }
    if (pop_right) {
      fpop();
    }
  } else {
    assert(tmp != noreg, "need temp");
    if (pop_left) {
      if (pop_right) {
        fcompp();
      } else {
        fcomp(index);
      }
    } else {
      fcom(index);
    }
    // convert FPU condition into eflags condition via rax,
    save_rax(tmp);
    fwait(); fnstsw_ax();
    sahf();
    restore_rax(tmp);
  }
  // condition codes set as follows:
  //
  // CF (corresponds to C0) if x < y
  // PF (corresponds to C2) if unordered
  // ZF (corresponds to C3) if x = y
}

void MacroAssembler::fcmp2int(Register dst, bool unordered_is_less) {
  fcmp2int(dst, unordered_is_less, 1, true, true);
}

void MacroAssembler::fcmp2int(Register dst, bool unordered_is_less, int index, bool pop_left, bool pop_right) {
  fcmp(VM_Version::supports_cmov() ? noreg : dst, index, pop_left, pop_right);
  Label L;
  if (unordered_is_less) {
    movl(dst, -1);
    jcc(Assembler::parity, L);
    jcc(Assembler::below , L);
    movl(dst, 0);
    jcc(Assembler::equal , L);
    increment(dst);
  } else { // unordered is greater
    movl(dst, 1);
    jcc(Assembler::parity, L);
    jcc(Assembler::above , L);
    movl(dst, 0);
    jcc(Assembler::equal , L);
    decrementl(dst);
  }
  bind(L);
}

void MacroAssembler::fld_d(AddressLiteral src) {
  fld_d(as_Address(src));
}

void MacroAssembler::fld_s(AddressLiteral src) {
  fld_s(as_Address(src));
}

void MacroAssembler::fld_x(AddressLiteral src) {
  Assembler::fld_x(as_Address(src));
}

void MacroAssembler::fldcw(AddressLiteral src) {
  Assembler::fldcw(as_Address(src));
}

void MacroAssembler::fpop() {
  ffree();
  fincstp();
}

void MacroAssembler::fremr(Register tmp) {
  save_rax(tmp);
  { Label L;
    bind(L);
    fprem();
    fwait(); fnstsw_ax();
#ifdef _LP64
    testl(rax, 0x400);
    jcc(Assembler::notEqual, L);
#else
    sahf();
    jcc(Assembler::parity, L);
#endif // _LP64
  }
  restore_rax(tmp);
  // Result is in ST0.
  // Note: fxch & fpop to get rid of ST1
  // (otherwise FPU stack could overflow eventually)
  fxch(1);
  fpop();
}


void MacroAssembler::incrementl(AddressLiteral dst) {
  if (reachable(dst)) {
    incrementl(as_Address(dst));
  } else {
    lea(rscratch1, dst);
    incrementl(Address(rscratch1, 0));
  }
}

void MacroAssembler::incrementl(ArrayAddress dst) {
  incrementl(as_Address(dst));
}

void MacroAssembler::incrementl(Register reg, int value) {
  if (value == min_jint) {addl(reg, value) ; return; }
  if (value <  0) { decrementl(reg, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { incl(reg) ; return; }
  /* else */      { addl(reg, value)       ; return; }
}

void MacroAssembler::incrementl(Address dst, int value) {
  if (value == min_jint) {addl(dst, value) ; return; }
  if (value <  0) { decrementl(dst, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { incl(dst) ; return; }
  /* else */      { addl(dst, value)       ; return; }
}

void MacroAssembler::jump(AddressLiteral dst) {
  if (reachable(dst)) {
    jmp_literal(dst.target(), dst.rspec());
  } else {
    lea(rscratch1, dst);
    jmp(rscratch1);
  }
}

void MacroAssembler::jump_cc(Condition cc, AddressLiteral dst) {
  if (reachable(dst)) {
    InstructionMark im(this);
    relocate(dst.reloc());
    const int short_size = 2;
    const int long_size = 6;
    int offs = (intptr_t)dst.target() - ((intptr_t)_code_pos);
    if (dst.reloc() == relocInfo::none && is8bit(offs - short_size)) {
      // 0111 tttn #8-bit disp
      emit_byte(0x70 | cc);
      emit_byte((offs - short_size) & 0xFF);
    } else {
      // 0000 1111 1000 tttn #32-bit disp
      emit_byte(0x0F);
      emit_byte(0x80 | cc);
      emit_long(offs - long_size);
    }
  } else {
#ifdef ASSERT
    warning("reversing conditional branch");
#endif /* ASSERT */
    Label skip;
    jccb(reverse[cc], skip);
    lea(rscratch1, dst);
    Assembler::jmp(rscratch1);
    bind(skip);
  }
}

void MacroAssembler::ldmxcsr(AddressLiteral src) {
  if (reachable(src)) {
    Assembler::ldmxcsr(as_Address(src));
  } else {
    lea(rscratch1, src);
    Assembler::ldmxcsr(Address(rscratch1, 0));
  }
}

int MacroAssembler::load_signed_byte(Register dst, Address src) {
  int off;
  if (LP64_ONLY(true ||) VM_Version::is_P6()) {
    off = offset();
    movsbl(dst, src); // movsxb
  } else {
    off = load_unsigned_byte(dst, src);
    shll(dst, 24);
    sarl(dst, 24);
  }
  return off;
}

// Note: load_signed_short used to be called load_signed_word.
// Although the 'w' in x86 opcodes refers to the term "word" in the assembler
// manual, which means 16 bits, that usage is found nowhere in HotSpot code.
// The term "word" in HotSpot means a 32- or 64-bit machine word.
int MacroAssembler::load_signed_short(Register dst, Address src) {
  int off;
  if (LP64_ONLY(true ||) VM_Version::is_P6()) {
    // This is dubious to me since it seems safe to do a signed 16 => 64 bit
    // version but this is what 64bit has always done. This seems to imply
    // that users are only using 32bits worth.
    off = offset();
    movswl(dst, src); // movsxw
  } else {
    off = load_unsigned_short(dst, src);
    shll(dst, 16);
    sarl(dst, 16);
  }
  return off;
}

int MacroAssembler::load_unsigned_byte(Register dst, Address src) {
  // According to Intel Doc. AP-526, "Zero-Extension of Short", p.16,
  // and "3.9 Partial Register Penalties", p. 22).
  int off;
  if (LP64_ONLY(true || ) VM_Version::is_P6() || src.uses(dst)) {
    off = offset();
    movzbl(dst, src); // movzxb
  } else {
    xorl(dst, dst);
    off = offset();
    movb(dst, src);
  }
  return off;
}

// Note: load_unsigned_short used to be called load_unsigned_word.
int MacroAssembler::load_unsigned_short(Register dst, Address src) {
  // According to Intel Doc. AP-526, "Zero-Extension of Short", p.16,
  // and "3.9 Partial Register Penalties", p. 22).
  int off;
  if (LP64_ONLY(true ||) VM_Version::is_P6() || src.uses(dst)) {
    off = offset();
    movzwl(dst, src); // movzxw
  } else {
    xorl(dst, dst);
    off = offset();
    movw(dst, src);
  }
  return off;
}

void MacroAssembler::load_sized_value(Register dst, Address src,
                                      size_t size_in_bytes, bool is_signed) {
  switch (size_in_bytes) {
#ifndef _LP64
  // For case 8, caller is responsible for manually loading
  // the second word into another register.
  case  8: movl(dst, src); break;
#else
  case  8: movq(dst, src); break;
#endif
  case  4: movl(dst, src); break;
  case  2: is_signed ? load_signed_short(dst, src) : load_unsigned_short(dst, src); break;
  case  1: is_signed ? load_signed_byte( dst, src) : load_unsigned_byte( dst, src); break;
  default: ShouldNotReachHere();
  }
}

void MacroAssembler::mov32(AddressLiteral dst, Register src) {
  if (reachable(dst)) {
    movl(as_Address(dst), src);
  } else {
    lea(rscratch1, dst);
    movl(Address(rscratch1, 0), src);
  }
}

void MacroAssembler::mov32(Register dst, AddressLiteral src) {
  if (reachable(src)) {
    movl(dst, as_Address(src));
  } else {
    lea(rscratch1, src);
    movl(dst, Address(rscratch1, 0));
  }
}

// C++ bool manipulation

void MacroAssembler::movbool(Register dst, Address src) {
  if(sizeof(bool) == 1)
    movb(dst, src);
  else if(sizeof(bool) == 2)
    movw(dst, src);
  else if(sizeof(bool) == 4)
    movl(dst, src);
  else
    // unsupported
    ShouldNotReachHere();
}

void MacroAssembler::movbool(Address dst, bool boolconst) {
  if(sizeof(bool) == 1)
    movb(dst, (int) boolconst);
  else if(sizeof(bool) == 2)
    movw(dst, (int) boolconst);
  else if(sizeof(bool) == 4)
    movl(dst, (int) boolconst);
  else
    // unsupported
    ShouldNotReachHere();
}

void MacroAssembler::movbool(Address dst, Register src) {
  if(sizeof(bool) == 1)
    movb(dst, src);
  else if(sizeof(bool) == 2)
    movw(dst, src);
  else if(sizeof(bool) == 4)
    movl(dst, src);
  else
    // unsupported
    ShouldNotReachHere();
}

void MacroAssembler::movbyte(ArrayAddress dst, int src) {
  movb(as_Address(dst), src);
}

void MacroAssembler::movdbl(XMMRegister dst, AddressLiteral src) {
  if (reachable(src)) {
    if (UseXmmLoadAndClearUpper) {
      movsd (dst, as_Address(src));
    } else {
      movlpd(dst, as_Address(src));
    }
  } else {
    lea(rscratch1, src);
    if (UseXmmLoadAndClearUpper) {
      movsd (dst, Address(rscratch1, 0));
    } else {
      movlpd(dst, Address(rscratch1, 0));
    }
  }
}

void MacroAssembler::movflt(XMMRegister dst, AddressLiteral src) {
  if (reachable(src)) {
    movss(dst, as_Address(src));
  } else {
    lea(rscratch1, src);
    movss(dst, Address(rscratch1, 0));
  }
}

void MacroAssembler::movptr(Register dst, Register src) {
  LP64_ONLY(movq(dst, src)) NOT_LP64(movl(dst, src));
}

void MacroAssembler::movptr(Register dst, Address src) {
  LP64_ONLY(movq(dst, src)) NOT_LP64(movl(dst, src));
}

// src should NEVER be a real pointer. Use AddressLiteral for true pointers
void MacroAssembler::movptr(Register dst, intptr_t src) {
  LP64_ONLY(mov64(dst, src)) NOT_LP64(movl(dst, src));
}

void MacroAssembler::movptr(Address dst, Register src) {
  LP64_ONLY(movq(dst, src)) NOT_LP64(movl(dst, src));
}

void MacroAssembler::movss(XMMRegister dst, AddressLiteral src) {
  if (reachable(src)) {
    movss(dst, as_Address(src));
  } else {
    lea(rscratch1, src);
    movss(dst, Address(rscratch1, 0));
  }
}

void MacroAssembler::null_check(Register reg, int offset) {
  if (needs_explicit_null_check(offset)) {
    // provoke OS NULL exception if reg = NULL by
    // accessing M[reg] w/o changing any (non-CC) registers
    // NOTE: cmpl is plenty here to provoke a segv
    cmpptr(rax, Address(reg, 0));
    // Note: should probably use testl(rax, Address(reg, 0));
    //       may be shorter code (however, this version of
    //       testl needs to be implemented first)
  } else {
    // nothing to do, (later) access of M[reg + offset]
    // will provoke OS NULL exception if reg = NULL
  }
}

void MacroAssembler::os_breakpoint() {
  // instead of directly emitting a breakpoint, call os:breakpoint for better debugability
  // (e.g., MSVC can't call ps() otherwise)
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, os::breakpoint)));
}

void MacroAssembler::pop_CPU_state() {
  pop_FPU_state();
  pop_IU_state();
}

void MacroAssembler::pop_FPU_state() {
  NOT_LP64(frstor(Address(rsp, 0));)
  LP64_ONLY(fxrstor(Address(rsp, 0));)
  addptr(rsp, FPUStateSizeInWords * wordSize);
}

void MacroAssembler::pop_IU_state() {
  popa();
  LP64_ONLY(addq(rsp, 8));
  popf();
}

// Save Integer and Float state
// Warning: Stack must be 16 byte aligned (64bit)
void MacroAssembler::push_CPU_state() {
  push_IU_state();
  push_FPU_state();
}

void MacroAssembler::push_FPU_state() {
  subptr(rsp, FPUStateSizeInWords * wordSize);
#ifndef _LP64
  fnsave(Address(rsp, 0));
  fwait();
#else
  fxsave(Address(rsp, 0));
#endif // LP64
}

void MacroAssembler::push_IU_state() {
  // Push flags first because pusha kills them
  pushf();
  // Make sure rsp stays 16-byte aligned
  LP64_ONLY(subq(rsp, 8));
  pusha();
}

void MacroAssembler::reset_last_Java_frame(Register java_thread, bool clear_fp, bool clear_pc) {
  // determine java_thread register
  if (!java_thread->is_valid()) {
    java_thread = rdi;
    get_thread(java_thread);
  }
  // we must set sp to zero to clear frame
  movptr(Address(java_thread, JavaThread::last_Java_sp_offset()), NULL_WORD);
  if (clear_fp) {
    movptr(Address(java_thread, JavaThread::last_Java_fp_offset()), NULL_WORD);
  }

  if (clear_pc)
    movptr(Address(java_thread, JavaThread::last_Java_pc_offset()), NULL_WORD);

}

void MacroAssembler::restore_rax(Register tmp) {
  if (tmp == noreg) pop(rax);
  else if (tmp != rax) mov(rax, tmp);
}

void MacroAssembler::round_to(Register reg, int modulus) {
  addptr(reg, modulus - 1);
  andptr(reg, -modulus);
}

void MacroAssembler::save_rax(Register tmp) {
  if (tmp == noreg) push(rax);
  else if (tmp != rax) mov(tmp, rax);
}

// Write serialization page so VM thread can do a pseudo remote membar.
// We use the current thread pointer to calculate a thread specific
// offset to write to within the page. This minimizes bus traffic
// due to cache line collision.
void MacroAssembler::serialize_memory(Register thread, Register tmp) {
  movl(tmp, thread);
  shrl(tmp, os::get_serialize_page_shift_count());
  andl(tmp, (os::vm_page_size() - sizeof(int)));

  Address index(noreg, tmp, Address::times_1);
  ExternalAddress page(os::get_memory_serialize_page());

  // Size of store must match masking code above
  movl(as_Address(ArrayAddress(page, index)), tmp);
}

// Calls to C land
//
// When entering C land, the rbp, & rsp of the last Java frame have to be recorded
// in the (thread-local) JavaThread object. When leaving C land, the last Java fp
// has to be reset to 0. This is required to allow proper stack traversal.
void MacroAssembler::set_last_Java_frame(Register java_thread,
                                         Register last_java_sp,
                                         Register last_java_fp,
                                         address  last_java_pc) {
  // determine java_thread register
  if (!java_thread->is_valid()) {
    java_thread = rdi;
    get_thread(java_thread);
  }
  // determine last_java_sp register
  if (!last_java_sp->is_valid()) {
    last_java_sp = rsp;
  }

  // last_java_fp is optional

  if (last_java_fp->is_valid()) {
    movptr(Address(java_thread, JavaThread::last_Java_fp_offset()), last_java_fp);
  }

  // last_java_pc is optional

  if (last_java_pc != NULL) {
    lea(Address(java_thread,
                 JavaThread::frame_anchor_offset() + JavaFrameAnchor::last_Java_pc_offset()),
        InternalAddress(last_java_pc));

  }
  movptr(Address(java_thread, JavaThread::last_Java_sp_offset()), last_java_sp);
}

void MacroAssembler::shlptr(Register dst, int imm8) {
  LP64_ONLY(shlq(dst, imm8)) NOT_LP64(shll(dst, imm8));
}

void MacroAssembler::shrptr(Register dst, int imm8) {
  LP64_ONLY(shrq(dst, imm8)) NOT_LP64(shrl(dst, imm8));
}

void MacroAssembler::sign_extend_byte(Register reg) {
  if (LP64_ONLY(true ||) (VM_Version::is_P6() && reg->has_byte_register())) {
    movsbl(reg, reg); // movsxb
  } else {
    shll(reg, 24);
    sarl(reg, 24);
  }
}

void MacroAssembler::sign_extend_short(Register reg) {
  if (LP64_ONLY(true ||) VM_Version::is_P6()) {
    movswl(reg, reg); // movsxw
  } else {
    shll(reg, 16);
    sarl(reg, 16);
  }
}

//////////////////////////////////////////////////////////////////////////////////
#ifndef SERIALGC

void MacroAssembler::g1_write_barrier_pre(Register obj,
#ifndef _LP64
                                          Register thread,
#endif
                                          Register tmp,
                                          Register tmp2,
                                          bool tosca_live) {
  LP64_ONLY(Register thread = r15_thread;)
  Address in_progress(thread, in_bytes(JavaThread::satb_mark_queue_offset() +
                                       PtrQueue::byte_offset_of_active()));

  Address index(thread, in_bytes(JavaThread::satb_mark_queue_offset() +
                                       PtrQueue::byte_offset_of_index()));
  Address buffer(thread, in_bytes(JavaThread::satb_mark_queue_offset() +
                                       PtrQueue::byte_offset_of_buf()));


  Label done;
  Label runtime;

  // if (!marking_in_progress) goto done;
  if (in_bytes(PtrQueue::byte_width_of_active()) == 4) {
    cmpl(in_progress, 0);
  } else {
    assert(in_bytes(PtrQueue::byte_width_of_active()) == 1, "Assumption");
    cmpb(in_progress, 0);
  }
  jcc(Assembler::equal, done);

  // if (x.f == NULL) goto done;
#ifdef _LP64
  load_heap_oop(tmp2, Address(obj, 0));
#else
  movptr(tmp2, Address(obj, 0));
#endif
  cmpptr(tmp2, (int32_t) NULL_WORD);
  jcc(Assembler::equal, done);

  // Can we store original value in the thread's buffer?

#ifdef _LP64
  movslq(tmp, index);
  cmpq(tmp, 0);
#else
  cmpl(index, 0);
#endif
  jcc(Assembler::equal, runtime);
#ifdef _LP64
  subq(tmp, wordSize);
  movl(index, tmp);
  addq(tmp, buffer);
#else
  subl(index, wordSize);
  movl(tmp, buffer);
  addl(tmp, index);
#endif
  movptr(Address(tmp, 0), tmp2);
  jmp(done);
  bind(runtime);
  // save the live input values
  if(tosca_live) push(rax);
  push(obj);
#ifdef _LP64
  call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_pre), tmp2, r15_thread);
#else
  push(thread);
  call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_pre), tmp2, thread);
  pop(thread);
#endif
  pop(obj);
  if(tosca_live) pop(rax);
  bind(done);

}

void MacroAssembler::g1_write_barrier_post(Register store_addr,
                                           Register new_val,
#ifndef _LP64
                                           Register thread,
#endif
                                           Register tmp,
                                           Register tmp2) {

  LP64_ONLY(Register thread = r15_thread;)
  Address queue_index(thread, in_bytes(JavaThread::dirty_card_queue_offset() +
                                       PtrQueue::byte_offset_of_index()));
  Address buffer(thread, in_bytes(JavaThread::dirty_card_queue_offset() +
                                       PtrQueue::byte_offset_of_buf()));
  BarrierSet* bs = Universe::heap()->barrier_set();
  CardTableModRefBS* ct = (CardTableModRefBS*)bs;
  Label done;
  Label runtime;

  // Does store cross heap regions?

  movptr(tmp, store_addr);
  xorptr(tmp, new_val);
  shrptr(tmp, HeapRegion::LogOfHRGrainBytes);
  jcc(Assembler::equal, done);

  // crosses regions, storing NULL?

  cmpptr(new_val, (int32_t) NULL_WORD);
  jcc(Assembler::equal, done);

  // storing region crossing non-NULL, is card already dirty?

  ExternalAddress cardtable((address) ct->byte_map_base);
  assert(sizeof(*ct->byte_map_base) == sizeof(jbyte), "adjust this code");
#ifdef _LP64
  const Register card_addr = tmp;

  movq(card_addr, store_addr);
  shrq(card_addr, CardTableModRefBS::card_shift);

  lea(tmp2, cardtable);

  // get the address of the card
  addq(card_addr, tmp2);
#else
  const Register card_index = tmp;

  movl(card_index, store_addr);
  shrl(card_index, CardTableModRefBS::card_shift);

  Address index(noreg, card_index, Address::times_1);
  const Register card_addr = tmp;
  lea(card_addr, as_Address(ArrayAddress(cardtable, index)));
#endif
  cmpb(Address(card_addr, 0), 0);
  jcc(Assembler::equal, done);

  // storing a region crossing, non-NULL oop, card is clean.
  // dirty card and log.

  movb(Address(card_addr, 0), 0);

  cmpl(queue_index, 0);
  jcc(Assembler::equal, runtime);
  subl(queue_index, wordSize);
  movptr(tmp2, buffer);
#ifdef _LP64
  movslq(rscratch1, queue_index);
  addq(tmp2, rscratch1);
  movq(Address(tmp2, 0), card_addr);
#else
  addl(tmp2, queue_index);
  movl(Address(tmp2, 0), card_index);
#endif
  jmp(done);

  bind(runtime);
  // save the live input values
  push(store_addr);
  push(new_val);
#ifdef _LP64
  call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_post), card_addr, r15_thread);
#else
  push(thread);
  call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_post), card_addr, thread);
  pop(thread);
#endif
  pop(new_val);
  pop(store_addr);

  bind(done);

}

#endif // SERIALGC
//////////////////////////////////////////////////////////////////////////////////


void MacroAssembler::store_check(Register obj) {
  // Does a store check for the oop in register obj. The content of
  // register obj is destroyed afterwards.
  store_check_part_1(obj);
  store_check_part_2(obj);
}

void MacroAssembler::store_check(Register obj, Address dst) {
  store_check(obj);
}


// split the store check operation so that other instructions can be scheduled inbetween
void MacroAssembler::store_check_part_1(Register obj) {
  BarrierSet* bs = Universe::heap()->barrier_set();
  assert(bs->kind() == BarrierSet::CardTableModRef, "Wrong barrier set kind");
  shrptr(obj, CardTableModRefBS::card_shift);
}

void MacroAssembler::store_check_part_2(Register obj) {
  BarrierSet* bs = Universe::heap()->barrier_set();
  assert(bs->kind() == BarrierSet::CardTableModRef, "Wrong barrier set kind");
  CardTableModRefBS* ct = (CardTableModRefBS*)bs;
  assert(sizeof(*ct->byte_map_base) == sizeof(jbyte), "adjust this code");

  // The calculation for byte_map_base is as follows:
  // byte_map_base = _byte_map - (uintptr_t(low_bound) >> card_shift);
  // So this essentially converts an address to a displacement and
  // it will never need to be relocated. On 64bit however the value may be too
  // large for a 32bit displacement

  intptr_t disp = (intptr_t) ct->byte_map_base;
  if (is_simm32(disp)) {
    Address cardtable(noreg, obj, Address::times_1, disp);
    movb(cardtable, 0);
  } else {
    // By doing it as an ExternalAddress disp could be converted to a rip-relative
    // displacement and done in a single instruction given favorable mapping and
    // a smarter version of as_Address. Worst case it is two instructions which
    // is no worse off then loading disp into a register and doing as a simple
    // Address() as above.
    // We can't do as ExternalAddress as the only style since if disp == 0 we'll
    // assert since NULL isn't acceptable in a reloci (see 6644928). In any case
    // in some cases we'll get a single instruction version.

    ExternalAddress cardtable((address)disp);
    Address index(noreg, obj, Address::times_1);
    movb(as_Address(ArrayAddress(cardtable, index)), 0);
  }
}

void MacroAssembler::subptr(Register dst, int32_t imm32) {
  LP64_ONLY(subq(dst, imm32)) NOT_LP64(subl(dst, imm32));
}

void MacroAssembler::subptr(Register dst, Register src) {
  LP64_ONLY(subq(dst, src)) NOT_LP64(subl(dst, src));
}

void MacroAssembler::test32(Register src1, AddressLiteral src2) {
  // src2 must be rval

  if (reachable(src2)) {
    testl(src1, as_Address(src2));
  } else {
    lea(rscratch1, src2);
    testl(src1, Address(rscratch1, 0));
  }
}

// C++ bool manipulation
void MacroAssembler::testbool(Register dst) {
  if(sizeof(bool) == 1)
    testb(dst, 0xff);
  else if(sizeof(bool) == 2) {
    // testw implementation needed for two byte bools
    ShouldNotReachHere();
  } else if(sizeof(bool) == 4)
    testl(dst, dst);
  else
    // unsupported
    ShouldNotReachHere();
}

void MacroAssembler::testptr(Register dst, Register src) {
  LP64_ONLY(testq(dst, src)) NOT_LP64(testl(dst, src));
}

// Defines obj, preserves var_size_in_bytes, okay for t2 == var_size_in_bytes.
void MacroAssembler::tlab_allocate(Register obj,
                                   Register var_size_in_bytes,
                                   int con_size_in_bytes,
                                   Register t1,
                                   Register t2,
                                   Label& slow_case) {
  assert_different_registers(obj, t1, t2);
  assert_different_registers(obj, var_size_in_bytes, t1);
  Register end = t2;
  Register thread = NOT_LP64(t1) LP64_ONLY(r15_thread);

  verify_tlab();

  NOT_LP64(get_thread(thread));

  movptr(obj, Address(thread, JavaThread::tlab_top_offset()));
  if (var_size_in_bytes == noreg) {
    lea(end, Address(obj, con_size_in_bytes));
  } else {
    lea(end, Address(obj, var_size_in_bytes, Address::times_1));
  }
  cmpptr(end, Address(thread, JavaThread::tlab_end_offset()));
  jcc(Assembler::above, slow_case);

  // update the tlab top pointer
  movptr(Address(thread, JavaThread::tlab_top_offset()), end);

  // recover var_size_in_bytes if necessary
  if (var_size_in_bytes == end) {
    subptr(var_size_in_bytes, obj);
  }
  verify_tlab();
}

// Preserves rbx, and rdx.
void MacroAssembler::tlab_refill(Label& retry,
                                 Label& try_eden,
                                 Label& slow_case) {
  Register top = rax;
  Register t1  = rcx;
  Register t2  = rsi;
  Register thread_reg = NOT_LP64(rdi) LP64_ONLY(r15_thread);
  assert_different_registers(top, thread_reg, t1, t2, /* preserve: */ rbx, rdx);
  Label do_refill, discard_tlab;

  if (CMSIncrementalMode || !Universe::heap()->supports_inline_contig_alloc()) {
    // No allocation in the shared eden.
    jmp(slow_case);
  }

  NOT_LP64(get_thread(thread_reg));

  movptr(top, Address(thread_reg, in_bytes(JavaThread::tlab_top_offset())));
  movptr(t1,  Address(thread_reg, in_bytes(JavaThread::tlab_end_offset())));

  // calculate amount of free space
  subptr(t1, top);
  shrptr(t1, LogHeapWordSize);

  // Retain tlab and allocate object in shared space if
  // the amount free in the tlab is too large to discard.
  cmpptr(t1, Address(thread_reg, in_bytes(JavaThread::tlab_refill_waste_limit_offset())));
  jcc(Assembler::lessEqual, discard_tlab);

  // Retain
  // %%% yuck as movptr...
  movptr(t2, (int32_t) ThreadLocalAllocBuffer::refill_waste_limit_increment());
  addptr(Address(thread_reg, in_bytes(JavaThread::tlab_refill_waste_limit_offset())), t2);
  if (TLABStats) {
    // increment number of slow_allocations
    addl(Address(thread_reg, in_bytes(JavaThread::tlab_slow_allocations_offset())), 1);
  }
  jmp(try_eden);

  bind(discard_tlab);
  if (TLABStats) {
    // increment number of refills
    addl(Address(thread_reg, in_bytes(JavaThread::tlab_number_of_refills_offset())), 1);
    // accumulate wastage -- t1 is amount free in tlab
    addl(Address(thread_reg, in_bytes(JavaThread::tlab_fast_refill_waste_offset())), t1);
  }

  // if tlab is currently allocated (top or end != null) then
  // fill [top, end + alignment_reserve) with array object
  testptr (top, top);
  jcc(Assembler::zero, do_refill);

  // set up the mark word
  movptr(Address(top, oopDesc::mark_offset_in_bytes()), (intptr_t)markOopDesc::prototype()->copy_set_hash(0x2));
  // set the length to the remaining space
  subptr(t1, typeArrayOopDesc::header_size(T_INT));
  addptr(t1, (int32_t)ThreadLocalAllocBuffer::alignment_reserve());
  shlptr(t1, log2_intptr(HeapWordSize/sizeof(jint)));
  movptr(Address(top, arrayOopDesc::length_offset_in_bytes()), t1);
  // set klass to intArrayKlass
  // dubious reloc why not an oop reloc?
  movptr(t1, ExternalAddress((address) Universe::intArrayKlassObj_addr()));
  // store klass last.  concurrent gcs assumes klass length is valid if
  // klass field is not null.
  store_klass(top, t1);

  // refill the tlab with an eden allocation
  bind(do_refill);
  movptr(t1, Address(thread_reg, in_bytes(JavaThread::tlab_size_offset())));
  shlptr(t1, LogHeapWordSize);
  // add object_size ??
  eden_allocate(top, t1, 0, t2, slow_case);

  // Check that t1 was preserved in eden_allocate.
#ifdef ASSERT
  if (UseTLAB) {
    Label ok;
    Register tsize = rsi;
    assert_different_registers(tsize, thread_reg, t1);
    push(tsize);
    movptr(tsize, Address(thread_reg, in_bytes(JavaThread::tlab_size_offset())));
    shlptr(tsize, LogHeapWordSize);
    cmpptr(t1, tsize);
    jcc(Assembler::equal, ok);
    stop("assert(t1 != tlab size)");
    should_not_reach_here();

    bind(ok);
    pop(tsize);
  }
#endif
  movptr(Address(thread_reg, in_bytes(JavaThread::tlab_start_offset())), top);
  movptr(Address(thread_reg, in_bytes(JavaThread::tlab_top_offset())), top);
  addptr(top, t1);
  subptr(top, (int32_t)ThreadLocalAllocBuffer::alignment_reserve_in_bytes());
  movptr(Address(thread_reg, in_bytes(JavaThread::tlab_end_offset())), top);
  verify_tlab();
  jmp(retry);
}

static const double     pi_4 =  0.7853981633974483;

void MacroAssembler::trigfunc(char trig, int num_fpu_regs_in_use) {
  // A hand-coded argument reduction for values in fabs(pi/4, pi/2)
  // was attempted in this code; unfortunately it appears that the
  // switch to 80-bit precision and back causes this to be
  // unprofitable compared with simply performing a runtime call if
  // the argument is out of the (-pi/4, pi/4) range.

  Register tmp = noreg;
  if (!VM_Version::supports_cmov()) {
    // fcmp needs a temporary so preserve rbx,
    tmp = rbx;
    push(tmp);
  }

  Label slow_case, done;

  ExternalAddress pi4_adr = (address)&pi_4;
  if (reachable(pi4_adr)) {
    // x ?<= pi/4
    fld_d(pi4_adr);
    fld_s(1);                // Stack:  X  PI/4  X
    fabs();                  // Stack: |X| PI/4  X
    fcmp(tmp);
    jcc(Assembler::above, slow_case);

    // fastest case: -pi/4 <= x <= pi/4
    switch(trig) {
    case 's':
      fsin();
      break;
    case 'c':
      fcos();
      break;
    case 't':
      ftan();
      break;
    default:
      assert(false, "bad intrinsic");
      break;
    }
    jmp(done);
  }

  // slow case: runtime call
  bind(slow_case);
  // Preserve registers across runtime call
  pusha();
  int incoming_argument_and_return_value_offset = -1;
  if (num_fpu_regs_in_use > 1) {
    // Must preserve all other FPU regs (could alternatively convert
    // SharedRuntime::dsin and dcos into assembly routines known not to trash
    // FPU state, but can not trust C compiler)
    NEEDS_CLEANUP;
    // NOTE that in this case we also push the incoming argument to
    // the stack and restore it later; we also use this stack slot to
    // hold the return value from dsin or dcos.
    for (int i = 0; i < num_fpu_regs_in_use; i++) {
      subptr(rsp, sizeof(jdouble));
      fstp_d(Address(rsp, 0));
    }
    incoming_argument_and_return_value_offset = sizeof(jdouble)*(num_fpu_regs_in_use-1);
    fld_d(Address(rsp, incoming_argument_and_return_value_offset));
  }
  subptr(rsp, sizeof(jdouble));
  fstp_d(Address(rsp, 0));
#ifdef _LP64
  movdbl(xmm0, Address(rsp, 0));
#endif // _LP64

  // NOTE: we must not use call_VM_leaf here because that requires a
  // complete interpreter frame in debug mode -- same bug as 4387334
  // MacroAssembler::call_VM_leaf_base is perfectly safe and will
  // do proper 64bit abi

  NEEDS_CLEANUP;
  // Need to add stack banging before this runtime call if it needs to
  // be taken; however, there is no generic stack banging routine at
  // the MacroAssembler level
  switch(trig) {
  case 's':
    {
      MacroAssembler::call_VM_leaf_base(CAST_FROM_FN_PTR(address, SharedRuntime::dsin), 0);
    }
    break;
  case 'c':
    {
      MacroAssembler::call_VM_leaf_base(CAST_FROM_FN_PTR(address, SharedRuntime::dcos), 0);
    }
    break;
  case 't':
    {
      MacroAssembler::call_VM_leaf_base(CAST_FROM_FN_PTR(address, SharedRuntime::dtan), 0);
    }
    break;
  default:
    assert(false, "bad intrinsic");
    break;
  }
#ifdef _LP64
    movsd(Address(rsp, 0), xmm0);
    fld_d(Address(rsp, 0));
#endif // _LP64
  addptr(rsp, sizeof(jdouble));
  if (num_fpu_regs_in_use > 1) {
    // Must save return value to stack and then restore entire FPU stack
    fstp_d(Address(rsp, incoming_argument_and_return_value_offset));
    for (int i = 0; i < num_fpu_regs_in_use; i++) {
      fld_d(Address(rsp, 0));
      addptr(rsp, sizeof(jdouble));
    }
  }
  popa();

  // Come here with result in F-TOS
  bind(done);

  if (tmp != noreg) {
    pop(tmp);
  }
}


// Look up the method for a megamorphic invokeinterface call.
// The target method is determined by <intf_klass, itable_index>.
// The receiver klass is in recv_klass.
// On success, the result will be in method_result, and execution falls through.
// On failure, execution transfers to the given label.
void MacroAssembler::lookup_interface_method(Register recv_klass,
                                             Register intf_klass,
                                             RegisterOrConstant itable_index,
                                             Register method_result,
                                             Register scan_temp,
                                             Label& L_no_such_interface) {
  assert_different_registers(recv_klass, intf_klass, method_result, scan_temp);
  assert(itable_index.is_constant() || itable_index.as_register() == method_result,
         "caller must use same register for non-constant itable index as for method");

  // Compute start of first itableOffsetEntry (which is at the end of the vtable)
  int vtable_base = instanceKlass::vtable_start_offset() * wordSize;
  int itentry_off = itableMethodEntry::method_offset_in_bytes();
  int scan_step   = itableOffsetEntry::size() * wordSize;
  int vte_size    = vtableEntry::size() * wordSize;
  Address::ScaleFactor times_vte_scale = Address::times_ptr;
  assert(vte_size == wordSize, "else adjust times_vte_scale");

  movl(scan_temp, Address(recv_klass, instanceKlass::vtable_length_offset() * wordSize));

  // %%% Could store the aligned, prescaled offset in the klassoop.
  lea(scan_temp, Address(recv_klass, scan_temp, times_vte_scale, vtable_base));
  if (HeapWordsPerLong > 1) {
    // Round up to align_object_offset boundary
    // see code for instanceKlass::start_of_itable!
    round_to(scan_temp, BytesPerLong);
  }

  // Adjust recv_klass by scaled itable_index, so we can free itable_index.
  assert(itableMethodEntry::size() * wordSize == wordSize, "adjust the scaling in the code below");
  lea(recv_klass, Address(recv_klass, itable_index, Address::times_ptr, itentry_off));

  // for (scan = klass->itable(); scan->interface() != NULL; scan += scan_step) {
  //   if (scan->interface() == intf) {
  //     result = (klass + scan->offset() + itable_index);
  //   }
  // }
  Label search, found_method;

  for (int peel = 1; peel >= 0; peel--) {
    movptr(method_result, Address(scan_temp, itableOffsetEntry::interface_offset_in_bytes()));
    cmpptr(intf_klass, method_result);

    if (peel) {
      jccb(Assembler::equal, found_method);
    } else {
      jccb(Assembler::notEqual, search);
      // (invert the test to fall through to found_method...)
    }

    if (!peel)  break;

    bind(search);

    // Check that the previous entry is non-null.  A null entry means that
    // the receiver class doesn't implement the interface, and wasn't the
    // same as when the caller was compiled.
    testptr(method_result, method_result);
    jcc(Assembler::zero, L_no_such_interface);
    addptr(scan_temp, scan_step);
  }

  bind(found_method);

  // Got a hit.
  movl(scan_temp, Address(scan_temp, itableOffsetEntry::offset_offset_in_bytes()));
  movptr(method_result, Address(recv_klass, scan_temp, Address::times_1));
}


void MacroAssembler::check_klass_subtype(Register sub_klass,
                           Register super_klass,
                           Register temp_reg,
                           Label& L_success) {
  Label L_failure;
  check_klass_subtype_fast_path(sub_klass, super_klass, temp_reg,        &L_success, &L_failure, NULL);
  check_klass_subtype_slow_path(sub_klass, super_klass, temp_reg, noreg, &L_success, NULL);
  bind(L_failure);
}


void MacroAssembler::check_klass_subtype_fast_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register temp_reg,
                                                   Label* L_success,
                                                   Label* L_failure,
                                                   Label* L_slow_path,
                                        RegisterOrConstant super_check_offset) {
  assert_different_registers(sub_klass, super_klass, temp_reg);
  bool must_load_sco = (super_check_offset.constant_or_zero() == -1);
  if (super_check_offset.is_register()) {
    assert_different_registers(sub_klass, super_klass,
                               super_check_offset.as_register());
  } else if (must_load_sco) {
    assert(temp_reg != noreg, "supply either a temp or a register offset");
  }

  Label L_fallthrough;
  int label_nulls = 0;
  if (L_success == NULL)   { L_success   = &L_fallthrough; label_nulls++; }
  if (L_failure == NULL)   { L_failure   = &L_fallthrough; label_nulls++; }
  if (L_slow_path == NULL) { L_slow_path = &L_fallthrough; label_nulls++; }
  assert(label_nulls <= 1, "at most one NULL in the batch");

  int sc_offset = (klassOopDesc::header_size() * HeapWordSize +
                   Klass::secondary_super_cache_offset_in_bytes());
  int sco_offset = (klassOopDesc::header_size() * HeapWordSize +
                    Klass::super_check_offset_offset_in_bytes());
  Address super_check_offset_addr(super_klass, sco_offset);

  // Hacked jcc, which "knows" that L_fallthrough, at least, is in
  // range of a jccb.  If this routine grows larger, reconsider at
  // least some of these.
#define local_jcc(assembler_cond, label)                                \
  if (&(label) == &L_fallthrough)  jccb(assembler_cond, label);         \
  else                             jcc( assembler_cond, label) /*omit semi*/

  // Hacked jmp, which may only be used just before L_fallthrough.
#define final_jmp(label)                                                \
  if (&(label) == &L_fallthrough) { /*do nothing*/ }                    \
  else                            jmp(label)                /*omit semi*/

  // If the pointers are equal, we are done (e.g., String[] elements).
  // This self-check enables sharing of secondary supertype arrays among
  // non-primary types such as array-of-interface.  Otherwise, each such
  // type would need its own customized SSA.
  // We move this check to the front of the fast path because many
  // type checks are in fact trivially successful in this manner,
  // so we get a nicely predicted branch right at the start of the check.
  cmpptr(sub_klass, super_klass);
  local_jcc(Assembler::equal, *L_success);

  // Check the supertype display:
  if (must_load_sco) {
    // Positive movl does right thing on LP64.
    movl(temp_reg, super_check_offset_addr);
    super_check_offset = RegisterOrConstant(temp_reg);
  }
  Address super_check_addr(sub_klass, super_check_offset, Address::times_1, 0);
  cmpptr(super_klass, super_check_addr); // load displayed supertype

  // This check has worked decisively for primary supers.
  // Secondary supers are sought in the super_cache ('super_cache_addr').
  // (Secondary supers are interfaces and very deeply nested subtypes.)
  // This works in the same check above because of a tricky aliasing
  // between the super_cache and the primary super display elements.
  // (The 'super_check_addr' can address either, as the case requires.)
  // Note that the cache is updated below if it does not help us find
  // what we need immediately.
  // So if it was a primary super, we can just fail immediately.
  // Otherwise, it's the slow path for us (no success at this point).

  if (super_check_offset.is_register()) {
    local_jcc(Assembler::equal, *L_success);
    cmpl(super_check_offset.as_register(), sc_offset);
    if (L_failure == &L_fallthrough) {
      local_jcc(Assembler::equal, *L_slow_path);
    } else {
      local_jcc(Assembler::notEqual, *L_failure);
      final_jmp(*L_slow_path);
    }
  } else if (super_check_offset.as_constant() == sc_offset) {
    // Need a slow path; fast failure is impossible.
    if (L_slow_path == &L_fallthrough) {
      local_jcc(Assembler::equal, *L_success);
    } else {
      local_jcc(Assembler::notEqual, *L_slow_path);
      final_jmp(*L_success);
    }
  } else {
    // No slow path; it's a fast decision.
    if (L_failure == &L_fallthrough) {
      local_jcc(Assembler::equal, *L_success);
    } else {
      local_jcc(Assembler::notEqual, *L_failure);
      final_jmp(*L_success);
    }
  }

  bind(L_fallthrough);

#undef local_jcc
#undef final_jmp
}


void MacroAssembler::check_klass_subtype_slow_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register temp_reg,
                                                   Register temp2_reg,
                                                   Label* L_success,
                                                   Label* L_failure,
                                                   bool set_cond_codes) {
  assert_different_registers(sub_klass, super_klass, temp_reg);
  if (temp2_reg != noreg)
    assert_different_registers(sub_klass, super_klass, temp_reg, temp2_reg);
#define IS_A_TEMP(reg) ((reg) == temp_reg || (reg) == temp2_reg)

  Label L_fallthrough;
  int label_nulls = 0;
  if (L_success == NULL)   { L_success   = &L_fallthrough; label_nulls++; }
  if (L_failure == NULL)   { L_failure   = &L_fallthrough; label_nulls++; }
  assert(label_nulls <= 1, "at most one NULL in the batch");

  // a couple of useful fields in sub_klass:
  int ss_offset = (klassOopDesc::header_size() * HeapWordSize +
                   Klass::secondary_supers_offset_in_bytes());
  int sc_offset = (klassOopDesc::header_size() * HeapWordSize +
                   Klass::secondary_super_cache_offset_in_bytes());
  Address secondary_supers_addr(sub_klass, ss_offset);
  Address super_cache_addr(     sub_klass, sc_offset);

  // Do a linear scan of the secondary super-klass chain.
  // This code is rarely used, so simplicity is a virtue here.
  // The repne_scan instruction uses fixed registers, which we must spill.
  // Don't worry too much about pre-existing connections with the input regs.

  assert(sub_klass != rax, "killed reg"); // killed by mov(rax, super)
  assert(sub_klass != rcx, "killed reg"); // killed by lea(rcx, &pst_counter)

  // Get super_klass value into rax (even if it was in rdi or rcx).
  bool pushed_rax = false, pushed_rcx = false, pushed_rdi = false;
  if (super_klass != rax || UseCompressedOops) {
    if (!IS_A_TEMP(rax)) { push(rax); pushed_rax = true; }
    mov(rax, super_klass);
  }
  if (!IS_A_TEMP(rcx)) { push(rcx); pushed_rcx = true; }
  if (!IS_A_TEMP(rdi)) { push(rdi); pushed_rdi = true; }

#ifndef PRODUCT
  int* pst_counter = &SharedRuntime::_partial_subtype_ctr;
  ExternalAddress pst_counter_addr((address) pst_counter);
  NOT_LP64(  incrementl(pst_counter_addr) );
  LP64_ONLY( lea(rcx, pst_counter_addr) );
  LP64_ONLY( incrementl(Address(rcx, 0)) );
#endif //PRODUCT

  // We will consult the secondary-super array.
  movptr(rdi, secondary_supers_addr);
  // Load the array length.  (Positive movl does right thing on LP64.)
  movl(rcx, Address(rdi, arrayOopDesc::length_offset_in_bytes()));
  // Skip to start of data.
  addptr(rdi, arrayOopDesc::base_offset_in_bytes(T_OBJECT));

  // Scan RCX words at [RDI] for an occurrence of RAX.
  // Set NZ/Z based on last compare.
#ifdef _LP64
  // This part is tricky, as values in supers array could be 32 or 64 bit wide
  // and we store values in objArrays always encoded, thus we need to encode
  // the value of rax before repne.  Note that rax is dead after the repne.
  if (UseCompressedOops) {
    encode_heap_oop_not_null(rax);
    // The superclass is never null; it would be a basic system error if a null
    // pointer were to sneak in here.  Note that we have already loaded the
    // Klass::super_check_offset from the super_klass in the fast path,
    // so if there is a null in that register, we are already in the afterlife.
    repne_scanl();
  } else
#endif // _LP64
    repne_scan();

  // Unspill the temp. registers:
  if (pushed_rdi)  pop(rdi);
  if (pushed_rcx)  pop(rcx);
  if (pushed_rax)  pop(rax);

  if (set_cond_codes) {
    // Special hack for the AD files:  rdi is guaranteed non-zero.
    assert(!pushed_rdi, "rdi must be left non-NULL");
    // Also, the condition codes are properly set Z/NZ on succeed/failure.
  }

  if (L_failure == &L_fallthrough)
        jccb(Assembler::notEqual, *L_failure);
  else  jcc(Assembler::notEqual, *L_failure);

  // Success.  Cache the super we found and proceed in triumph.
  movptr(super_cache_addr, super_klass);

  if (L_success != &L_fallthrough) {
    jmp(*L_success);
  }

#undef IS_A_TEMP

  bind(L_fallthrough);
}


void MacroAssembler::ucomisd(XMMRegister dst, AddressLiteral src) {
  ucomisd(dst, as_Address(src));
}

void MacroAssembler::ucomiss(XMMRegister dst, AddressLiteral src) {
  ucomiss(dst, as_Address(src));
}

void MacroAssembler::xorpd(XMMRegister dst, AddressLiteral src) {
  if (reachable(src)) {
    xorpd(dst, as_Address(src));
  } else {
    lea(rscratch1, src);
    xorpd(dst, Address(rscratch1, 0));
  }
}

void MacroAssembler::xorps(XMMRegister dst, AddressLiteral src) {
  if (reachable(src)) {
    xorps(dst, as_Address(src));
  } else {
    lea(rscratch1, src);
    xorps(dst, Address(rscratch1, 0));
  }
}

void MacroAssembler::verify_oop(Register reg, const char* s) {
  if (!VerifyOops) return;

  // Pass register number to verify_oop_subroutine
  char* b = new char[strlen(s) + 50];
  sprintf(b, "verify_oop: %s: %s", reg->name(), s);
  push(rax);                          // save rax,
  push(reg);                          // pass register argument
  ExternalAddress buffer((address) b);
  // avoid using pushptr, as it modifies scratch registers
  // and our contract is not to modify anything
  movptr(rax, buffer.addr());
  push(rax);
  // call indirectly to solve generation ordering problem
  movptr(rax, ExternalAddress(StubRoutines::verify_oop_subroutine_entry_address()));
  call(rax);
}


RegisterOrConstant MacroAssembler::delayed_value_impl(intptr_t* delayed_value_addr,
                                                      Register tmp,
                                                      int offset) {
  intptr_t value = *delayed_value_addr;
  if (value != 0)
    return RegisterOrConstant(value + offset);

  // load indirectly to solve generation ordering problem
  movptr(tmp, ExternalAddress((address) delayed_value_addr));

#ifdef ASSERT
  Label L;
  testptr(tmp, tmp);
  jccb(Assembler::notZero, L);
  hlt();
  bind(L);
#endif

  if (offset != 0)
    addptr(tmp, offset);

  return RegisterOrConstant(tmp);
}


// registers on entry:
//  - rax ('check' register): required MethodType
//  - rcx: method handle
//  - rdx, rsi, or ?: killable temp
void MacroAssembler::check_method_handle_type(Register mtype_reg, Register mh_reg,
                                              Register temp_reg,
                                              Label& wrong_method_type) {
  if (UseCompressedOops)  unimplemented();  // field accesses must decode
  // compare method type against that of the receiver
  cmpptr(mtype_reg, Address(mh_reg, delayed_value(java_dyn_MethodHandle::type_offset_in_bytes, temp_reg)));
  jcc(Assembler::notEqual, wrong_method_type);
}


// A method handle has a "vmslots" field which gives the size of its
// argument list in JVM stack slots.  This field is either located directly
// in every method handle, or else is indirectly accessed through the
// method handle's MethodType.  This macro hides the distinction.
void MacroAssembler::load_method_handle_vmslots(Register vmslots_reg, Register mh_reg,
                                                Register temp_reg) {
  assert_different_registers(vmslots_reg, mh_reg, temp_reg);
  if (UseCompressedOops)  unimplemented();  // field accesses must decode
  // load mh.type.form.vmslots
  if (java_dyn_MethodHandle::vmslots_offset_in_bytes() != 0) {
    // hoist vmslots into every mh to avoid dependent load chain
    movl(vmslots_reg, Address(mh_reg, delayed_value(java_dyn_MethodHandle::vmslots_offset_in_bytes, temp_reg)));
  } else {
    Register temp2_reg = vmslots_reg;
    movptr(temp2_reg, Address(mh_reg,    delayed_value(java_dyn_MethodHandle::type_offset_in_bytes, temp_reg)));
    movptr(temp2_reg, Address(temp2_reg, delayed_value(java_dyn_MethodType::form_offset_in_bytes, temp_reg)));
    movl(vmslots_reg, Address(temp2_reg, delayed_value(java_dyn_MethodTypeForm::vmslots_offset_in_bytes, temp_reg)));
  }
}


// registers on entry:
//  - rcx: method handle
//  - rdx: killable temp (interpreted only)
//  - rax: killable temp (compiled only)
void MacroAssembler::jump_to_method_handle_entry(Register mh_reg, Register temp_reg) {
  assert(mh_reg == rcx, "caller must put MH object in rcx");
  assert_different_registers(mh_reg, temp_reg);

  if (UseCompressedOops)  unimplemented();  // field accesses must decode

  // pick out the interpreted side of the handler
  movptr(temp_reg, Address(mh_reg, delayed_value(java_dyn_MethodHandle::vmentry_offset_in_bytes, temp_reg)));

  // off we go...
  jmp(Address(temp_reg, MethodHandleEntry::from_interpreted_entry_offset_in_bytes()));

  // for the various stubs which take control at this point,
  // see MethodHandles::generate_method_handle_stub
}


Address MacroAssembler::argument_address(RegisterOrConstant arg_slot,
                                         int extra_slot_offset) {
  // cf. TemplateTable::prepare_invoke(), if (load_receiver).
  int stackElementSize = Interpreter::stackElementSize;
  int offset = Interpreter::expr_offset_in_bytes(extra_slot_offset+0);
#ifdef ASSERT
  int offset1 = Interpreter::expr_offset_in_bytes(extra_slot_offset+1);
  assert(offset1 - offset == stackElementSize, "correct arithmetic");
#endif
  Register             scale_reg    = noreg;
  Address::ScaleFactor scale_factor = Address::no_scale;
  if (arg_slot.is_constant()) {
    offset += arg_slot.as_constant() * stackElementSize;
  } else {
    scale_reg    = arg_slot.as_register();
    scale_factor = Address::times(stackElementSize);
  }
  offset += wordSize;           // return PC is on stack
  return Address(rsp, scale_reg, scale_factor, offset);
}


void MacroAssembler::verify_oop_addr(Address addr, const char* s) {
  if (!VerifyOops) return;

  // Address adjust(addr.base(), addr.index(), addr.scale(), addr.disp() + BytesPerWord);
  // Pass register number to verify_oop_subroutine
  char* b = new char[strlen(s) + 50];
  sprintf(b, "verify_oop_addr: %s", s);

  push(rax);                          // save rax,
  // addr may contain rsp so we will have to adjust it based on the push
  // we just did
  // NOTE: 64bit seemed to have had a bug in that it did movq(addr, rax); which
  // stores rax into addr which is backwards of what was intended.
  if (addr.uses(rsp)) {
    lea(rax, addr);
    pushptr(Address(rax, BytesPerWord));
  } else {
    pushptr(addr);
  }

  ExternalAddress buffer((address) b);
  // pass msg argument
  // avoid using pushptr, as it modifies scratch registers
  // and our contract is not to modify anything
  movptr(rax, buffer.addr());
  push(rax);

  // call indirectly to solve generation ordering problem
  movptr(rax, ExternalAddress(StubRoutines::verify_oop_subroutine_entry_address()));
  call(rax);
  // Caller pops the arguments and restores rax, from the stack
}

void MacroAssembler::verify_tlab() {
#ifdef ASSERT
  if (UseTLAB && VerifyOops) {
    Label next, ok;
    Register t1 = rsi;
    Register thread_reg = NOT_LP64(rbx) LP64_ONLY(r15_thread);

    push(t1);
    NOT_LP64(push(thread_reg));
    NOT_LP64(get_thread(thread_reg));

    movptr(t1, Address(thread_reg, in_bytes(JavaThread::tlab_top_offset())));
    cmpptr(t1, Address(thread_reg, in_bytes(JavaThread::tlab_start_offset())));
    jcc(Assembler::aboveEqual, next);
    stop("assert(top >= start)");
    should_not_reach_here();

    bind(next);
    movptr(t1, Address(thread_reg, in_bytes(JavaThread::tlab_end_offset())));
    cmpptr(t1, Address(thread_reg, in_bytes(JavaThread::tlab_top_offset())));
    jcc(Assembler::aboveEqual, ok);
    stop("assert(top <= end)");
    should_not_reach_here();

    bind(ok);
    NOT_LP64(pop(thread_reg));
    pop(t1);
  }
#endif
}

class ControlWord {
 public:
  int32_t _value;

  int  rounding_control() const        { return  (_value >> 10) & 3      ; }
  int  precision_control() const       { return  (_value >>  8) & 3      ; }
  bool precision() const               { return ((_value >>  5) & 1) != 0; }
  bool underflow() const               { return ((_value >>  4) & 1) != 0; }
  bool overflow() const                { return ((_value >>  3) & 1) != 0; }
  bool zero_divide() const             { return ((_value >>  2) & 1) != 0; }
  bool denormalized() const            { return ((_value >>  1) & 1) != 0; }
  bool invalid() const                 { return ((_value >>  0) & 1) != 0; }

  void print() const {
    // rounding control
    const char* rc;
    switch (rounding_control()) {
      case 0: rc = "round near"; break;
      case 1: rc = "round down"; break;
      case 2: rc = "round up  "; break;
      case 3: rc = "chop      "; break;
    };
    // precision control
    const char* pc;
    switch (precision_control()) {
      case 0: pc = "24 bits "; break;
      case 1: pc = "reserved"; break;
      case 2: pc = "53 bits "; break;
      case 3: pc = "64 bits "; break;
    };
    // flags
    char f[9];
    f[0] = ' ';
    f[1] = ' ';
    f[2] = (precision   ()) ? 'P' : 'p';
    f[3] = (underflow   ()) ? 'U' : 'u';
    f[4] = (overflow    ()) ? 'O' : 'o';
    f[5] = (zero_divide ()) ? 'Z' : 'z';
    f[6] = (denormalized()) ? 'D' : 'd';
    f[7] = (invalid     ()) ? 'I' : 'i';
    f[8] = '\x0';
    // output
    printf("%04x  masks = %s, %s, %s", _value & 0xFFFF, f, rc, pc);
  }

};

class StatusWord {
 public:
  int32_t _value;

  bool busy() const                    { return ((_value >> 15) & 1) != 0; }
  bool C3() const                      { return ((_value >> 14) & 1) != 0; }
  bool C2() const                      { return ((_value >> 10) & 1) != 0; }
  bool C1() const                      { return ((_value >>  9) & 1) != 0; }
  bool C0() const                      { return ((_value >>  8) & 1) != 0; }
  int  top() const                     { return  (_value >> 11) & 7      ; }
  bool error_status() const            { return ((_value >>  7) & 1) != 0; }
  bool stack_fault() const             { return ((_value >>  6) & 1) != 0; }
  bool precision() const               { return ((_value >>  5) & 1) != 0; }
  bool underflow() const               { return ((_value >>  4) & 1) != 0; }
  bool overflow() const                { return ((_value >>  3) & 1) != 0; }
  bool zero_divide() const             { return ((_value >>  2) & 1) != 0; }
  bool denormalized() const            { return ((_value >>  1) & 1) != 0; }
  bool invalid() const                 { return ((_value >>  0) & 1) != 0; }

  void print() const {
    // condition codes
    char c[5];
    c[0] = (C3()) ? '3' : '-';
    c[1] = (C2()) ? '2' : '-';
    c[2] = (C1()) ? '1' : '-';
    c[3] = (C0()) ? '0' : '-';
    c[4] = '\x0';
    // flags
    char f[9];
    f[0] = (error_status()) ? 'E' : '-';
    f[1] = (stack_fault ()) ? 'S' : '-';
    f[2] = (precision   ()) ? 'P' : '-';
    f[3] = (underflow   ()) ? 'U' : '-';
    f[4] = (overflow    ()) ? 'O' : '-';
    f[5] = (zero_divide ()) ? 'Z' : '-';
    f[6] = (denormalized()) ? 'D' : '-';
    f[7] = (invalid     ()) ? 'I' : '-';
    f[8] = '\x0';
    // output
    printf("%04x  flags = %s, cc =  %s, top = %d", _value & 0xFFFF, f, c, top());
  }

};

class TagWord {
 public:
  int32_t _value;

  int tag_at(int i) const              { return (_value >> (i*2)) & 3; }

  void print() const {
    printf("%04x", _value & 0xFFFF);
  }

};

class FPU_Register {
 public:
  int32_t _m0;
  int32_t _m1;
  int16_t _ex;

  bool is_indefinite() const           {
    return _ex == -1 && _m1 == (int32_t)0xC0000000 && _m0 == 0;
  }

  void print() const {
    char  sign = (_ex < 0) ? '-' : '+';
    const char* kind = (_ex == 0x7FFF || _ex == (int16_t)-1) ? "NaN" : "   ";
    printf("%c%04hx.%08x%08x  %s", sign, _ex, _m1, _m0, kind);
  };

};

class FPU_State {
 public:
  enum {
    register_size       = 10,
    number_of_registers =  8,
    register_mask       =  7
  };

  ControlWord  _control_word;
  StatusWord   _status_word;
  TagWord      _tag_word;
  int32_t      _error_offset;
  int32_t      _error_selector;
  int32_t      _data_offset;
  int32_t      _data_selector;
  int8_t       _register[register_size * number_of_registers];

  int tag_for_st(int i) const          { return _tag_word.tag_at((_status_word.top() + i) & register_mask); }
  FPU_Register* st(int i) const        { return (FPU_Register*)&_register[register_size * i]; }

  const char* tag_as_string(int tag) const {
    switch (tag) {
      case 0: return "valid";
      case 1: return "zero";
      case 2: return "special";
      case 3: return "empty";
    }
    ShouldNotReachHere();
    return NULL;
  }

  void print() const {
    // print computation registers
    { int t = _status_word.top();
      for (int i = 0; i < number_of_registers; i++) {
        int j = (i - t) & register_mask;
        printf("%c r%d = ST%d = ", (j == 0 ? '*' : ' '), i, j);
        st(j)->print();
        printf(" %s\n", tag_as_string(_tag_word.tag_at(i)));
      }
    }
    printf("\n");
    // print control registers
    printf("ctrl = "); _control_word.print(); printf("\n");
    printf("stat = "); _status_word .print(); printf("\n");
    printf("tags = "); _tag_word    .print(); printf("\n");
  }

};

class Flag_Register {
 public:
  int32_t _value;

  bool overflow() const                { return ((_value >> 11) & 1) != 0; }
  bool direction() const               { return ((_value >> 10) & 1) != 0; }
  bool sign() const                    { return ((_value >>  7) & 1) != 0; }
  bool zero() const                    { return ((_value >>  6) & 1) != 0; }
  bool auxiliary_carry() const         { return ((_value >>  4) & 1) != 0; }
  bool parity() const                  { return ((_value >>  2) & 1) != 0; }
  bool carry() const                   { return ((_value >>  0) & 1) != 0; }

  void print() const {
    // flags
    char f[8];
    f[0] = (overflow       ()) ? 'O' : '-';
    f[1] = (direction      ()) ? 'D' : '-';
    f[2] = (sign           ()) ? 'S' : '-';
    f[3] = (zero           ()) ? 'Z' : '-';
    f[4] = (auxiliary_carry()) ? 'A' : '-';
    f[5] = (parity         ()) ? 'P' : '-';
    f[6] = (carry          ()) ? 'C' : '-';
    f[7] = '\x0';
    // output
    printf("%08x  flags = %s", _value, f);
  }

};

class IU_Register {
 public:
  int32_t _value;

  void print() const {
    printf("%08x  %11d", _value, _value);
  }

};

class IU_State {
 public:
  Flag_Register _eflags;
  IU_Register   _rdi;
  IU_Register   _rsi;
  IU_Register   _rbp;
  IU_Register   _rsp;
  IU_Register   _rbx;
  IU_Register   _rdx;
  IU_Register   _rcx;
  IU_Register   _rax;

  void print() const {
    // computation registers
    printf("rax,  = "); _rax.print(); printf("\n");
    printf("rbx,  = "); _rbx.print(); printf("\n");
    printf("rcx  = "); _rcx.print(); printf("\n");
    printf("rdx  = "); _rdx.print(); printf("\n");
    printf("rdi  = "); _rdi.print(); printf("\n");
    printf("rsi  = "); _rsi.print(); printf("\n");
    printf("rbp,  = "); _rbp.print(); printf("\n");
    printf("rsp  = "); _rsp.print(); printf("\n");
    printf("\n");
    // control registers
    printf("flgs = "); _eflags.print(); printf("\n");
  }
};


class CPU_State {
 public:
  FPU_State _fpu_state;
  IU_State  _iu_state;

  void print() const {
    printf("--------------------------------------------------\n");
    _iu_state .print();
    printf("\n");
    _fpu_state.print();
    printf("--------------------------------------------------\n");
  }

};


static void _print_CPU_state(CPU_State* state) {
  state->print();
};


void MacroAssembler::print_CPU_state() {
  push_CPU_state();
  push(rsp);                // pass CPU state
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, _print_CPU_state)));
  addptr(rsp, wordSize);       // discard argument
  pop_CPU_state();
}


static bool _verify_FPU(int stack_depth, char* s, CPU_State* state) {
  static int counter = 0;
  FPU_State* fs = &state->_fpu_state;
  counter++;
  // For leaf calls, only verify that the top few elements remain empty.
  // We only need 1 empty at the top for C2 code.
  if( stack_depth < 0 ) {
    if( fs->tag_for_st(7) != 3 ) {
      printf("FPR7 not empty\n");
      state->print();
      assert(false, "error");
      return false;
    }
    return true;                // All other stack states do not matter
  }

  assert((fs->_control_word._value & 0xffff) == StubRoutines::_fpu_cntrl_wrd_std,
         "bad FPU control word");

  // compute stack depth
  int i = 0;
  while (i < FPU_State::number_of_registers && fs->tag_for_st(i)  < 3) i++;
  int d = i;
  while (i < FPU_State::number_of_registers && fs->tag_for_st(i) == 3) i++;
  // verify findings
  if (i != FPU_State::number_of_registers) {
    // stack not contiguous
    printf("%s: stack not contiguous at ST%d\n", s, i);
    state->print();
    assert(false, "error");
    return false;
  }
  // check if computed stack depth corresponds to expected stack depth
  if (stack_depth < 0) {
    // expected stack depth is -stack_depth or less
    if (d > -stack_depth) {
      // too many elements on the stack
      printf("%s: <= %d stack elements expected but found %d\n", s, -stack_depth, d);
      state->print();
      assert(false, "error");
      return false;
    }
  } else {
    // expected stack depth is stack_depth
    if (d != stack_depth) {
      // wrong stack depth
      printf("%s: %d stack elements expected but found %d\n", s, stack_depth, d);
      state->print();
      assert(false, "error");
      return false;
    }
  }
  // everything is cool
  return true;
}


void MacroAssembler::verify_FPU(int stack_depth, const char* s) {
  if (!VerifyFPU) return;
  push_CPU_state();
  push(rsp);                // pass CPU state
  ExternalAddress msg((address) s);
  // pass message string s
  pushptr(msg.addr());
  push(stack_depth);        // pass stack depth
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, _verify_FPU)));
  addptr(rsp, 3 * wordSize);   // discard arguments
  // check for error
  { Label L;
    testl(rax, rax);
    jcc(Assembler::notZero, L);
    int3();                  // break if error condition
    bind(L);
  }
  pop_CPU_state();
}

void MacroAssembler::load_klass(Register dst, Register src) {
#ifdef _LP64
  if (UseCompressedOops) {
    movl(dst, Address(src, oopDesc::klass_offset_in_bytes()));
    decode_heap_oop_not_null(dst);
  } else
#endif
    movptr(dst, Address(src, oopDesc::klass_offset_in_bytes()));
}

void MacroAssembler::load_prototype_header(Register dst, Register src) {
#ifdef _LP64
  if (UseCompressedOops) {
    assert (Universe::heap() != NULL, "java heap should be initialized");
    movl(dst, Address(src, oopDesc::klass_offset_in_bytes()));
    if (Universe::narrow_oop_shift() != 0) {
      assert(Address::times_8 == LogMinObjAlignmentInBytes &&
             Address::times_8 == Universe::narrow_oop_shift(), "decode alg wrong");
      movq(dst, Address(r12_heapbase, dst, Address::times_8, Klass::prototype_header_offset_in_bytes() + klassOopDesc::klass_part_offset_in_bytes()));
    } else {
      movq(dst, Address(dst, Klass::prototype_header_offset_in_bytes() + klassOopDesc::klass_part_offset_in_bytes()));
    }
  } else
#endif
  {
    movptr(dst, Address(src, oopDesc::klass_offset_in_bytes()));
    movptr(dst, Address(dst, Klass::prototype_header_offset_in_bytes() + klassOopDesc::klass_part_offset_in_bytes()));
  }
}

void MacroAssembler::store_klass(Register dst, Register src) {
#ifdef _LP64
  if (UseCompressedOops) {
    encode_heap_oop_not_null(src);
    movl(Address(dst, oopDesc::klass_offset_in_bytes()), src);
  } else
#endif
    movptr(Address(dst, oopDesc::klass_offset_in_bytes()), src);
}

#ifdef _LP64
void MacroAssembler::store_klass_gap(Register dst, Register src) {
  if (UseCompressedOops) {
    // Store to klass gap in destination
    movl(Address(dst, oopDesc::klass_gap_offset_in_bytes()), src);
  }
}

void MacroAssembler::load_heap_oop(Register dst, Address src) {
  if (UseCompressedOops) {
    movl(dst, src);
    decode_heap_oop(dst);
  } else {
    movq(dst, src);
  }
}

void MacroAssembler::store_heap_oop(Address dst, Register src) {
  if (UseCompressedOops) {
    assert(!dst.uses(src), "not enough registers");
    encode_heap_oop(src);
    movl(dst, src);
  } else {
    movq(dst, src);
  }
}

// Used for storing NULLs.
void MacroAssembler::store_heap_oop_null(Address dst) {
  if (UseCompressedOops) {
    movl(dst, (int32_t)NULL_WORD);
  } else {
    movslq(dst, (int32_t)NULL_WORD);
  }
}

// Algorithm must match oop.inline.hpp encode_heap_oop.
void MacroAssembler::encode_heap_oop(Register r) {
  assert (UseCompressedOops, "should be compressed");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  if (Universe::narrow_oop_base() == NULL) {
    verify_oop(r, "broken oop in encode_heap_oop");
    if (Universe::narrow_oop_shift() != 0) {
      assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
      shrq(r, LogMinObjAlignmentInBytes);
    }
    return;
  }
#ifdef ASSERT
  if (CheckCompressedOops) {
    Label ok;
    push(rscratch1); // cmpptr trashes rscratch1
    cmpptr(r12_heapbase, ExternalAddress((address)Universe::narrow_oop_base_addr()));
    jcc(Assembler::equal, ok);
    stop("MacroAssembler::encode_heap_oop: heap base corrupted?");
    bind(ok);
    pop(rscratch1);
  }
#endif
  verify_oop(r, "broken oop in encode_heap_oop");
  testq(r, r);
  cmovq(Assembler::equal, r, r12_heapbase);
  subq(r, r12_heapbase);
  shrq(r, LogMinObjAlignmentInBytes);
}

void MacroAssembler::encode_heap_oop_not_null(Register r) {
  assert (UseCompressedOops, "should be compressed");
  assert (Universe::heap() != NULL, "java heap should be initialized");
#ifdef ASSERT
  if (CheckCompressedOops) {
    Label ok;
    testq(r, r);
    jcc(Assembler::notEqual, ok);
    stop("null oop passed to encode_heap_oop_not_null");
    bind(ok);
  }
#endif
  verify_oop(r, "broken oop in encode_heap_oop_not_null");
  if (Universe::narrow_oop_base() != NULL) {
    subq(r, r12_heapbase);
  }
  if (Universe::narrow_oop_shift() != 0) {
    assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
    shrq(r, LogMinObjAlignmentInBytes);
  }
}

void MacroAssembler::encode_heap_oop_not_null(Register dst, Register src) {
  assert (UseCompressedOops, "should be compressed");
  assert (Universe::heap() != NULL, "java heap should be initialized");
#ifdef ASSERT
  if (CheckCompressedOops) {
    Label ok;
    testq(src, src);
    jcc(Assembler::notEqual, ok);
    stop("null oop passed to encode_heap_oop_not_null2");
    bind(ok);
  }
#endif
  verify_oop(src, "broken oop in encode_heap_oop_not_null2");
  if (dst != src) {
    movq(dst, src);
  }
  if (Universe::narrow_oop_base() != NULL) {
    subq(dst, r12_heapbase);
  }
  if (Universe::narrow_oop_shift() != 0) {
    assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
    shrq(dst, LogMinObjAlignmentInBytes);
  }
}

void  MacroAssembler::decode_heap_oop(Register r) {
  assert (UseCompressedOops, "should be compressed");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  if (Universe::narrow_oop_base() == NULL) {
    if (Universe::narrow_oop_shift() != 0) {
      assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
      shlq(r, LogMinObjAlignmentInBytes);
    }
    verify_oop(r, "broken oop in decode_heap_oop");
    return;
  }
#ifdef ASSERT
  if (CheckCompressedOops) {
    Label ok;
    push(rscratch1);
    cmpptr(r12_heapbase,
           ExternalAddress((address)Universe::narrow_oop_base_addr()));
    jcc(Assembler::equal, ok);
    stop("MacroAssembler::decode_heap_oop: heap base corrupted?");
    bind(ok);
    pop(rscratch1);
  }
#endif

  Label done;
  shlq(r, LogMinObjAlignmentInBytes);
  jccb(Assembler::equal, done);
  addq(r, r12_heapbase);
#if 0
   // alternate decoding probably a wash.
   testq(r, r);
   jccb(Assembler::equal, done);
   leaq(r, Address(r12_heapbase, r, Address::times_8, 0));
#endif
  bind(done);
  verify_oop(r, "broken oop in decode_heap_oop");
}

void  MacroAssembler::decode_heap_oop_not_null(Register r) {
  assert (UseCompressedOops, "should only be used for compressed headers");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  // Cannot assert, unverified entry point counts instructions (see .ad file)
  // vtableStubs also counts instructions in pd_code_size_limit.
  // Also do not verify_oop as this is called by verify_oop.
  if (Universe::narrow_oop_shift() != 0) {
    assert (Address::times_8 == LogMinObjAlignmentInBytes &&
            Address::times_8 == Universe::narrow_oop_shift(), "decode alg wrong");
    // Don't use Shift since it modifies flags.
    leaq(r, Address(r12_heapbase, r, Address::times_8, 0));
  } else {
    assert (Universe::narrow_oop_base() == NULL, "sanity");
  }
}

void  MacroAssembler::decode_heap_oop_not_null(Register dst, Register src) {
  assert (UseCompressedOops, "should only be used for compressed headers");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  // Cannot assert, unverified entry point counts instructions (see .ad file)
  // vtableStubs also counts instructions in pd_code_size_limit.
  // Also do not verify_oop as this is called by verify_oop.
  if (Universe::narrow_oop_shift() != 0) {
    assert (Address::times_8 == LogMinObjAlignmentInBytes &&
            Address::times_8 == Universe::narrow_oop_shift(), "decode alg wrong");
    leaq(dst, Address(r12_heapbase, src, Address::times_8, 0));
  } else if (dst != src) {
    assert (Universe::narrow_oop_base() == NULL, "sanity");
    movq(dst, src);
  }
}

void  MacroAssembler::set_narrow_oop(Register dst, jobject obj) {
  assert (UseCompressedOops, "should only be used for compressed headers");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  assert (oop_recorder() != NULL, "this assembler needs an OopRecorder");
  int oop_index = oop_recorder()->find_index(obj);
  RelocationHolder rspec = oop_Relocation::spec(oop_index);
  mov_narrow_oop(dst, oop_index, rspec);
}

void  MacroAssembler::set_narrow_oop(Address dst, jobject obj) {
  assert (UseCompressedOops, "should only be used for compressed headers");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  assert (oop_recorder() != NULL, "this assembler needs an OopRecorder");
  int oop_index = oop_recorder()->find_index(obj);
  RelocationHolder rspec = oop_Relocation::spec(oop_index);
  mov_narrow_oop(dst, oop_index, rspec);
}

void  MacroAssembler::cmp_narrow_oop(Register dst, jobject obj) {
  assert (UseCompressedOops, "should only be used for compressed headers");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  assert (oop_recorder() != NULL, "this assembler needs an OopRecorder");
  int oop_index = oop_recorder()->find_index(obj);
  RelocationHolder rspec = oop_Relocation::spec(oop_index);
  Assembler::cmp_narrow_oop(dst, oop_index, rspec);
}

void  MacroAssembler::cmp_narrow_oop(Address dst, jobject obj) {
  assert (UseCompressedOops, "should only be used for compressed headers");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  assert (oop_recorder() != NULL, "this assembler needs an OopRecorder");
  int oop_index = oop_recorder()->find_index(obj);
  RelocationHolder rspec = oop_Relocation::spec(oop_index);
  Assembler::cmp_narrow_oop(dst, oop_index, rspec);
}

void MacroAssembler::reinit_heapbase() {
  if (UseCompressedOops) {
    movptr(r12_heapbase, ExternalAddress((address)Universe::narrow_oop_base_addr()));
  }
}
#endif // _LP64

// IndexOf substring.
void MacroAssembler::string_indexof(Register str1, Register str2,
                                    Register cnt1, Register cnt2, Register result,
                                    XMMRegister vec, Register tmp) {
  assert(UseSSE42Intrinsics, "SSE4.2 is required");

  Label RELOAD_SUBSTR, PREP_FOR_SCAN, SCAN_TO_SUBSTR,
        SCAN_SUBSTR, RET_NOT_FOUND, CLEANUP;

  push(str1); // string addr
  push(str2); // substr addr
  push(cnt2); // substr count
  jmpb(PREP_FOR_SCAN);

  // Substr count saved at sp
  // Substr saved at sp+1*wordSize
  // String saved at sp+2*wordSize

  // Reload substr for rescan
  bind(RELOAD_SUBSTR);
  movl(cnt2, Address(rsp, 0));
  movptr(str2, Address(rsp, wordSize));
  // We came here after the beginninig of the substring was
  // matched but the rest of it was not so we need to search
  // again. Start from the next element after the previous match.
  subptr(str1, result); // Restore counter
  shrl(str1, 1);
  addl(cnt1, str1);
  decrementl(cnt1);
  lea(str1, Address(result, 2)); // Reload string

  // Load substr
  bind(PREP_FOR_SCAN);
  movdqu(vec, Address(str2, 0));
  addl(cnt1, 8);  // prime the loop
  subptr(str1, 16);

  // Scan string for substr in 16-byte vectors
  bind(SCAN_TO_SUBSTR);
  subl(cnt1, 8);
  addptr(str1, 16);

  // pcmpestri
  //   inputs:
  //     xmm - substring
  //     rax - substring length (elements count)
  //     mem - scaned string
  //     rdx - string length (elements count)
  //     0xd - mode: 1100 (substring search) + 01 (unsigned shorts)
  //   outputs:
  //     rcx - matched index in string
  assert(cnt1 == rdx && cnt2 == rax && tmp == rcx, "pcmpestri");

  pcmpestri(vec, Address(str1, 0), 0x0d);
  jcc(Assembler::above, SCAN_TO_SUBSTR);      // CF == 0 && ZF == 0
  jccb(Assembler::aboveEqual, RET_NOT_FOUND); // CF == 0

  // Fallthrough: found a potential substr

  // Make sure string is still long enough
  subl(cnt1, tmp);
  cmpl(cnt1, cnt2);
  jccb(Assembler::negative, RET_NOT_FOUND);
  // Compute start addr of substr
  lea(str1, Address(str1, tmp, Address::times_2));
  movptr(result, str1); // save

  // Compare potential substr
  addl(cnt1, 8);     // prime the loop
  addl(cnt2, 8);
  subptr(str1, 16);
  subptr(str2, 16);

  // Scan 16-byte vectors of string and substr
  bind(SCAN_SUBSTR);
  subl(cnt1, 8);
  subl(cnt2, 8);
  addptr(str1, 16);
  addptr(str2, 16);
  movdqu(vec, Address(str2, 0));
  pcmpestri(vec, Address(str1, 0), 0x0d);
  jcc(Assembler::noOverflow, RELOAD_SUBSTR); // OF == 0
  jcc(Assembler::positive, SCAN_SUBSTR);     // SF == 0

  // Compute substr offset
  subptr(result, Address(rsp, 2*wordSize));
  shrl(result, 1); // index
  jmpb(CLEANUP);

  bind(RET_NOT_FOUND);
  movl(result, -1);

  bind(CLEANUP);
  addptr(rsp, 3*wordSize);
}

// Compare strings.
void MacroAssembler::string_compare(Register str1, Register str2,
                                    Register cnt1, Register cnt2, Register result,
                                    XMMRegister vec1, XMMRegister vec2) {
  Label LENGTH_DIFF_LABEL, POP_LABEL, DONE_LABEL, WHILE_HEAD_LABEL;

  // Compute the minimum of the string lengths and the
  // difference of the string lengths (stack).
  // Do the conditional move stuff
  movl(result, cnt1);
  subl(cnt1, cnt2);
  push(cnt1);
  if (VM_Version::supports_cmov()) {
    cmovl(Assembler::lessEqual, cnt2, result);
  } else {
    Label GT_LABEL;
    jccb(Assembler::greater, GT_LABEL);
    movl(cnt2, result);
    bind(GT_LABEL);
  }

  // Is the minimum length zero?
  testl(cnt2, cnt2);
  jcc(Assembler::zero, LENGTH_DIFF_LABEL);

  // Load first characters
  load_unsigned_short(result, Address(str1, 0));
  load_unsigned_short(cnt1, Address(str2, 0));

  // Compare first characters
  subl(result, cnt1);
  jcc(Assembler::notZero,  POP_LABEL);
  decrementl(cnt2);
  jcc(Assembler::zero, LENGTH_DIFF_LABEL);

  {
    // Check after comparing first character to see if strings are equivalent
    Label LSkip2;
    // Check if the strings start at same location
    cmpptr(str1, str2);
    jccb(Assembler::notEqual, LSkip2);

    // Check if the length difference is zero (from stack)
    cmpl(Address(rsp, 0), 0x0);
    jcc(Assembler::equal,  LENGTH_DIFF_LABEL);

    // Strings might not be equivalent
    bind(LSkip2);
  }

  // Advance to next character
  addptr(str1, 2);
  addptr(str2, 2);

  if (UseSSE42Intrinsics) {
    // With SSE4.2, use double quad vector compare
    Label COMPARE_VECTORS, VECTOR_NOT_EQUAL, COMPARE_TAIL;
    // Setup to compare 16-byte vectors
    movl(cnt1, cnt2);
    andl(cnt2, 0xfffffff8); // cnt2 holds the vector count
    andl(cnt1, 0x00000007); // cnt1 holds the tail count
    testl(cnt2, cnt2);
    jccb(Assembler::zero, COMPARE_TAIL);

    lea(str2, Address(str2, cnt2, Address::times_2));
    lea(str1, Address(str1, cnt2, Address::times_2));
    negptr(cnt2);

    bind(COMPARE_VECTORS);
    movdqu(vec1, Address(str1, cnt2, Address::times_2));
    movdqu(vec2, Address(str2, cnt2, Address::times_2));
    pxor(vec1, vec2);
    ptest(vec1, vec1);
    jccb(Assembler::notZero, VECTOR_NOT_EQUAL);
    addptr(cnt2, 8);
    jcc(Assembler::notZero, COMPARE_VECTORS);
    jmpb(COMPARE_TAIL);

    // Mismatched characters in the vectors
    bind(VECTOR_NOT_EQUAL);
    lea(str1, Address(str1, cnt2, Address::times_2));
    lea(str2, Address(str2, cnt2, Address::times_2));
    movl(cnt1, 8);

    // Compare tail (< 8 chars), or rescan last vectors to
    // find 1st mismatched characters
    bind(COMPARE_TAIL);
    testl(cnt1, cnt1);
    jccb(Assembler::zero, LENGTH_DIFF_LABEL);
    movl(cnt2, cnt1);
    // Fallthru to tail compare
  }

  // Shift str2 and str1 to the end of the arrays, negate min
  lea(str1, Address(str1, cnt2, Address::times_2, 0));
  lea(str2, Address(str2, cnt2, Address::times_2, 0));
  negptr(cnt2);

    // Compare the rest of the characters
  bind(WHILE_HEAD_LABEL);
  load_unsigned_short(result, Address(str1, cnt2, Address::times_2, 0));
  load_unsigned_short(cnt1, Address(str2, cnt2, Address::times_2, 0));
  subl(result, cnt1);
  jccb(Assembler::notZero, POP_LABEL);
  increment(cnt2);
  jcc(Assembler::notZero, WHILE_HEAD_LABEL);

  // Strings are equal up to min length.  Return the length difference.
  bind(LENGTH_DIFF_LABEL);
  pop(result);
  jmpb(DONE_LABEL);

  // Discard the stored length difference
  bind(POP_LABEL);
  addptr(rsp, wordSize);

  // That's it
  bind(DONE_LABEL);
}

// Compare char[] arrays aligned to 4 bytes or substrings.
void MacroAssembler::char_arrays_equals(bool is_array_equ, Register ary1, Register ary2,
                                        Register limit, Register result, Register chr,
                                        XMMRegister vec1, XMMRegister vec2) {
  Label TRUE_LABEL, FALSE_LABEL, DONE, COMPARE_VECTORS, COMPARE_CHAR;

  int length_offset  = arrayOopDesc::length_offset_in_bytes();
  int base_offset    = arrayOopDesc::base_offset_in_bytes(T_CHAR);

  // Check the input args
  cmpptr(ary1, ary2);
  jcc(Assembler::equal, TRUE_LABEL);

  if (is_array_equ) {
    // Need additional checks for arrays_equals.
    testptr(ary1, ary1);
    jcc(Assembler::zero, FALSE_LABEL);
    testptr(ary2, ary2);
    jcc(Assembler::zero, FALSE_LABEL);

    // Check the lengths
    movl(limit, Address(ary1, length_offset));
    cmpl(limit, Address(ary2, length_offset));
    jcc(Assembler::notEqual, FALSE_LABEL);
  }

  // count == 0
  testl(limit, limit);
  jcc(Assembler::zero, TRUE_LABEL);

  if (is_array_equ) {
    // Load array address
    lea(ary1, Address(ary1, base_offset));
    lea(ary2, Address(ary2, base_offset));
  }

  shll(limit, 1);      // byte count != 0
  movl(result, limit); // copy

  if (UseSSE42Intrinsics) {
    // With SSE4.2, use double quad vector compare
    Label COMPARE_WIDE_VECTORS, COMPARE_TAIL;
    // Compare 16-byte vectors
    andl(result, 0x0000000e);  //   tail count (in bytes)
    andl(limit, 0xfffffff0);   // vector count (in bytes)
    jccb(Assembler::zero, COMPARE_TAIL);

    lea(ary1, Address(ary1, limit, Address::times_1));
    lea(ary2, Address(ary2, limit, Address::times_1));
    negptr(limit);

    bind(COMPARE_WIDE_VECTORS);
    movdqu(vec1, Address(ary1, limit, Address::times_1));
    movdqu(vec2, Address(ary2, limit, Address::times_1));
    pxor(vec1, vec2);
    ptest(vec1, vec1);
    jccb(Assembler::notZero, FALSE_LABEL);
    addptr(limit, 16);
    jcc(Assembler::notZero, COMPARE_WIDE_VECTORS);

    bind(COMPARE_TAIL); // limit is zero
    movl(limit, result);
    // Fallthru to tail compare
  }

  // Compare 4-byte vectors
  andl(limit, 0xfffffffc); // vector count (in bytes)
  jccb(Assembler::zero, COMPARE_CHAR);

  lea(ary1, Address(ary1, limit, Address::times_1));
  lea(ary2, Address(ary2, limit, Address::times_1));
  negptr(limit);

  bind(COMPARE_VECTORS);
  movl(chr, Address(ary1, limit, Address::times_1));
  cmpl(chr, Address(ary2, limit, Address::times_1));
  jccb(Assembler::notEqual, FALSE_LABEL);
  addptr(limit, 4);
  jcc(Assembler::notZero, COMPARE_VECTORS);

  // Compare trailing char (final 2 bytes), if any
  bind(COMPARE_CHAR);
  testl(result, 0x2);   // tail  char
  jccb(Assembler::zero, TRUE_LABEL);
  load_unsigned_short(chr, Address(ary1, 0));
  load_unsigned_short(limit, Address(ary2, 0));
  cmpl(chr, limit);
  jccb(Assembler::notEqual, FALSE_LABEL);

  bind(TRUE_LABEL);
  movl(result, 1);   // return true
  jmpb(DONE);

  bind(FALSE_LABEL);
  xorl(result, result); // return false

  // That's it
  bind(DONE);
}

Assembler::Condition MacroAssembler::negate_condition(Assembler::Condition cond) {
  switch (cond) {
    // Note some conditions are synonyms for others
    case Assembler::zero:         return Assembler::notZero;
    case Assembler::notZero:      return Assembler::zero;
    case Assembler::less:         return Assembler::greaterEqual;
    case Assembler::lessEqual:    return Assembler::greater;
    case Assembler::greater:      return Assembler::lessEqual;
    case Assembler::greaterEqual: return Assembler::less;
    case Assembler::below:        return Assembler::aboveEqual;
    case Assembler::belowEqual:   return Assembler::above;
    case Assembler::above:        return Assembler::belowEqual;
    case Assembler::aboveEqual:   return Assembler::below;
    case Assembler::overflow:     return Assembler::noOverflow;
    case Assembler::noOverflow:   return Assembler::overflow;
    case Assembler::negative:     return Assembler::positive;
    case Assembler::positive:     return Assembler::negative;
    case Assembler::parity:       return Assembler::noParity;
    case Assembler::noParity:     return Assembler::parity;
  }
  ShouldNotReachHere(); return Assembler::overflow;
}

SkipIfEqual::SkipIfEqual(
    MacroAssembler* masm, const bool* flag_addr, bool value) {
  _masm = masm;
  _masm->cmp8(ExternalAddress((address)flag_addr), value);
  _masm->jcc(Assembler::equal, _label);
}

SkipIfEqual::~SkipIfEqual() {
  _masm->bind(_label);
}
