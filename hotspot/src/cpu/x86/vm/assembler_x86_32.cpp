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

#include "incls/_precompiled.incl"
#include "incls/_assembler_x86_32.cpp.incl"

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

Address Address::make_array(ArrayAddress adr) {
#ifdef _LP64
  // Not implementable on 64bit machines
  // Should have been handled higher up the call chain.
  ShouldNotReachHere();
#else
  AddressLiteral base = adr.base();
  Address index = adr.index();
  assert(index._disp == 0, "must not have disp"); // maybe it can?
  Address array(index._base, index._index, index._scale, (intptr_t) base.target());
  array._rspec = base._rspec;
  return array;
#endif // _LP64
}

#ifndef _LP64

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
Address Address::make_raw(int base, int index, int scale, int disp) {
  bool valid_index = index != rsp->encoding();
  if (valid_index) {
    Address madr(as_Register(base), as_Register(index), (Address::ScaleFactor)scale, in_ByteSize(disp));
    return madr;
  } else {
    Address madr(as_Register(base), noreg, Address::no_scale, in_ByteSize(disp));
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
  assert(imm32_operand == 0, "default format must be imm32 in this file");
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


void Assembler::emit_arith_b(int op1, int op2, Register dst, int imm8) {
  assert(dst->has_byte_register(), "must have byte register");
  assert(isByte(op1) && isByte(op2), "wrong opcode");
  assert(isByte(imm8), "not a byte");
  assert((op1 & 0x01) == 0, "should be 8bit operation");
  emit_byte(op1);
  emit_byte(op2 | dst->encoding());
  emit_byte(imm8);
}


void Assembler::emit_arith(int op1, int op2, Register dst, int imm32) {
  assert(isByte(op1) && isByte(op2), "wrong opcode");
  assert((op1 & 0x01) == 1, "should be 32bit operation");
  assert((op1 & 0x02) == 0, "sign-extension bit should not be set");
  if (is8bit(imm32)) {
    emit_byte(op1 | 0x02); // set sign bit
    emit_byte(op2 | dst->encoding());
    emit_byte(imm32 & 0xFF);
  } else {
    emit_byte(op1);
    emit_byte(op2 | dst->encoding());
    emit_long(imm32);
  }
}

// immediate-to-memory forms
void Assembler::emit_arith_operand(int op1, Register rm, Address adr, int imm32) {
  assert((op1 & 0x01) == 1, "should be 32bit operation");
  assert((op1 & 0x02) == 0, "sign-extension bit should not be set");
  if (is8bit(imm32)) {
    emit_byte(op1 | 0x02); // set sign bit
    emit_operand(rm,adr);
    emit_byte(imm32 & 0xFF);
  } else {
    emit_byte(op1);
    emit_operand(rm,adr);
    emit_long(imm32);
  }
}

void Assembler::emit_arith(int op1, int op2, Register dst, jobject obj) {
  assert(isByte(op1) && isByte(op2), "wrong opcode");
  assert((op1 & 0x01) == 1, "should be 32bit operation");
  assert((op1 & 0x02) == 0, "sign-extension bit should not be set");
  InstructionMark im(this);
  emit_byte(op1);
  emit_byte(op2 | dst->encoding());
  emit_data((int)obj, relocInfo::oop_type, 0);
}


void Assembler::emit_arith(int op1, int op2, Register dst, Register src) {
  assert(isByte(op1) && isByte(op2), "wrong opcode");
  emit_byte(op1);
  emit_byte(op2 | dst->encoding() << 3 | src->encoding());
}


void Assembler::emit_operand(Register reg,
                             Register base,
                             Register index,
                             Address::ScaleFactor scale,
                             int disp,
                             RelocationHolder const& rspec) {

  relocInfo::relocType rtype = (relocInfo::relocType) rspec.type();
  if (base->is_valid()) {
    if (index->is_valid()) {
      assert(scale != Address::no_scale, "inconsistent address");
      // [base + index*scale + disp]
      if (disp == 0 && rtype == relocInfo::none && base != rbp) {
        // [base + index*scale]
        // [00 reg 100][ss index base]
        assert(index != rsp, "illegal addressing mode");
        emit_byte(0x04 | reg->encoding() << 3);
        emit_byte(scale << 6 | index->encoding() << 3 | base->encoding());
      } else if (is8bit(disp) && rtype == relocInfo::none) {
        // [base + index*scale + imm8]
        // [01 reg 100][ss index base] imm8
        assert(index != rsp, "illegal addressing mode");
        emit_byte(0x44 | reg->encoding() << 3);
        emit_byte(scale << 6 | index->encoding() << 3 | base->encoding());
        emit_byte(disp & 0xFF);
      } else {
        // [base + index*scale + imm32]
        // [10 reg 100][ss index base] imm32
        assert(index != rsp, "illegal addressing mode");
        emit_byte(0x84 | reg->encoding() << 3);
        emit_byte(scale << 6 | index->encoding() << 3 | base->encoding());
        emit_data(disp, rspec, disp32_operand);
      }
    } else if (base == rsp) {
      // [esp + disp]
      if (disp == 0 && rtype == relocInfo::none) {
        // [esp]
        // [00 reg 100][00 100 100]
        emit_byte(0x04 | reg->encoding() << 3);
        emit_byte(0x24);
      } else if (is8bit(disp) && rtype == relocInfo::none) {
        // [esp + imm8]
        // [01 reg 100][00 100 100] imm8
        emit_byte(0x44 | reg->encoding() << 3);
        emit_byte(0x24);
        emit_byte(disp & 0xFF);
      } else {
        // [esp + imm32]
        // [10 reg 100][00 100 100] imm32
        emit_byte(0x84 | reg->encoding() << 3);
        emit_byte(0x24);
        emit_data(disp, rspec, disp32_operand);
      }
    } else {
      // [base + disp]
      assert(base != rsp, "illegal addressing mode");
      if (disp == 0 && rtype == relocInfo::none && base != rbp) {
        // [base]
        // [00 reg base]
        assert(base != rbp, "illegal addressing mode");
        emit_byte(0x00 | reg->encoding() << 3 | base->encoding());
      } else if (is8bit(disp) && rtype == relocInfo::none) {
        // [base + imm8]
        // [01 reg base] imm8
        emit_byte(0x40 | reg->encoding() << 3 | base->encoding());
        emit_byte(disp & 0xFF);
      } else {
        // [base + imm32]
        // [10 reg base] imm32
        emit_byte(0x80 | reg->encoding() << 3 | base->encoding());
        emit_data(disp, rspec, disp32_operand);
      }
    }
  } else {
    if (index->is_valid()) {
      assert(scale != Address::no_scale, "inconsistent address");
      // [index*scale + disp]
      // [00 reg 100][ss index 101] imm32
      assert(index != rsp, "illegal addressing mode");
      emit_byte(0x04 | reg->encoding() << 3);
      emit_byte(scale << 6 | index->encoding() << 3 | 0x05);
      emit_data(disp, rspec, disp32_operand);
    } else {
      // [disp]
      // [00 reg 101] imm32
      emit_byte(0x05 | reg->encoding() << 3);
      emit_data(disp, rspec, disp32_operand);
    }
  }
}

// Secret local extension to Assembler::WhichOperand:
#define end_pc_operand (_WhichOperand_limit)

address Assembler::locate_operand(address inst, WhichOperand which) {
  // Decode the given instruction, and return the address of
  // an embedded 32-bit operand word.

  // If "which" is disp32_operand, selects the displacement portion
  // of an effective address specifier.
  // If "which" is imm32_operand, selects the trailing immediate constant.
  // If "which" is call32_operand, selects the displacement of a call or jump.
  // Caller is responsible for ensuring that there is such an operand,
  // and that it is 32 bits wide.

  // If "which" is end_pc_operand, find the end of the instruction.

  address ip = inst;

  debug_only(bool has_imm32 = false);
  int tail_size = 0;    // other random bytes (#32, #16, etc.) at end of insn

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
    assert(ip == inst+1, "only one prefix allowed");
    goto again_after_prefix;

  case 0xFF: // pushl a; decl a; incl a; call a; jmp a
  case 0x88: // movb a, r
  case 0x89: // movl a, r
  case 0x8A: // movb r, a
  case 0x8B: // movl r, a
  case 0x8F: // popl a
    break;

  case 0x68: // pushl #32(oop?)
    if (which == end_pc_operand)  return ip + 4;
    assert(which == imm32_operand, "pushl has no disp32");
    return ip;                  // not produced by emit_operand

  case 0x66: // movw ... (size prefix)
    switch (0xFF & *ip++) {
    case 0x8B: // movw r, a
    case 0x89: // movw a, r
      break;
    case 0xC7: // movw a, #16
      tail_size = 2;  // the imm16
      break;
    case 0x0F: // several SSE/SSE2 variants
      ip--;    // reparse the 0x0F
      goto again_after_prefix;
    default:
      ShouldNotReachHere();
    }
    break;

  case REP8(0xB8): // movl r, #32(oop?)
    if (which == end_pc_operand)  return ip + 4;
    assert(which == imm32_operand || which == disp32_operand, "");
    return ip;

  case 0x69: // imul r, a, #32
  case 0xC7: // movl a, #32(oop?)
    tail_size = 4;
    debug_only(has_imm32 = true); // has both kinds of operands!
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
      // amd side says it these have both operands but that doesn't
      // appear to be true.
      // debug_only(has_imm32 = true); // has both kinds of operands!
      break;

    case 0xAD: // shrd r, a, %cl
    case 0xAF: // imul r, a
    case 0xBE: // movsxb r, a
    case 0xBF: // movsxw r, a
    case 0xB6: // movzxb r, a
    case 0xB7: // movzxw r, a
    case REP16(0x40): // cmovl cc, r, a
    case 0xB0: // cmpxchgb
    case 0xB1: // cmpxchg
    case 0xC1: // xaddl
    case 0xC7: // cmpxchg8
    case REP16(0x90): // setcc a
      // fall out of the switch to decode the address
      break;
    case 0xAC: // shrd r, a, #8
      tail_size = 1;  // the imm8
      break;
    case REP16(0x80): // jcc rdisp32
      if (which == end_pc_operand)  return ip + 4;
      assert(which == call32_operand, "jcc has no disp32 or imm32");
      return ip;
    default:
      ShouldNotReachHere();
    }
    break;

  case 0x81: // addl a, #32; addl r, #32
    // also: orl, adcl, sbbl, andl, subl, xorl, cmpl
    // in the case of cmpl, the imm32 might be an oop
    tail_size = 4;
    debug_only(has_imm32 = true); // has both kinds of operands!
    break;

  case 0x85: // test r/m, r
    break;

  case 0x83: // addl a, #8; addl r, #8
    // also: orl, adcl, sbbl, andl, subl, xorl, cmpl
    tail_size = 1;
    break;

  case 0x9B:
    switch (0xFF & *ip++) {
    case 0xD9: // fnstcw a
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
  case REP4(0x38): // cmp...
  case 0xF7: // mull a
  case 0x8D: // leal r, a
  case 0x87: // xchg r, a
    break;

  case 0xC1: // sal a, #8; sar a, #8; shl a, #8; shr a, #8
  case 0xC6: // movb a, #8
  case 0x80: // cmpb a, #8
  case 0x6B: // imul r, a, #8
    tail_size = 1; // the imm8
    break;

  case 0xE8: // call rdisp32
  case 0xE9: // jmp  rdisp32
    if (which == end_pc_operand)  return ip + 4;
    assert(which == call32_operand, "call has no disp32 or imm32");
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
    break;

  case 0xF3:                    // For SSE
  case 0xF2:                    // For SSE2
    ip++; ip++;
    break;

  default:
    ShouldNotReachHere();

  #undef REP8
  #undef REP16
  }

  assert(which != call32_operand, "instruction is not a call, jmp, or jcc");
  assert(which != imm32_operand || has_imm32, "instruction has no imm32 field");

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
    // [00 reg  100][00   100  rsp]
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
    // [01 reg  100][00   100  rsp][disp8]
    // [01 reg base]               [disp8]
    ip += 1;                    // skip the disp8
    break;

  case 2:
    // [10 reg  100][ss index base][disp32]
    // [10 reg  100][00   100  rsp][disp32]
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

  assert(which == imm32_operand, "instruction has only an imm32 field");
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
    assert(format == imm32_operand || format == disp32_operand, "format ok");
    opnd = locate_operand(inst, (WhichOperand)format);
  } else {
    assert(format == imm32_operand, "cannot specify a format");
    return;
  }
  assert(opnd == pc(), "must put operand where relocs can find it");
}
#endif



void Assembler::emit_operand(Register reg, Address adr) {
  emit_operand(reg, adr._base, adr._index, adr._scale, adr._disp, adr._rspec);
}


void Assembler::emit_farith(int b1, int b2, int i) {
  assert(isByte(b1) && isByte(b2), "wrong opcode");
  assert(0 <= i &&  i < 8, "illegal stack offset");
  emit_byte(b1);
  emit_byte(b2 + i);
}


void Assembler::pushad() {
  emit_byte(0x60);
}

void Assembler::popad() {
  emit_byte(0x61);
}

void Assembler::pushfd() {
  emit_byte(0x9C);
}

void Assembler::popfd() {
  emit_byte(0x9D);
}

void Assembler::pushl(int imm32) {
  emit_byte(0x68);
  emit_long(imm32);
}

#ifndef _LP64
void Assembler::push_literal32(int32_t imm32, RelocationHolder const& rspec) {
  InstructionMark im(this);
  emit_byte(0x68);
  emit_data(imm32, rspec, 0);
}
#endif // _LP64

void Assembler::pushl(Register src) {
  emit_byte(0x50 | src->encoding());
}


void Assembler::pushl(Address src) {
  InstructionMark im(this);
  emit_byte(0xFF);
  emit_operand(rsi, src);
}

void Assembler::popl(Register dst) {
  emit_byte(0x58 | dst->encoding());
}


void Assembler::popl(Address dst) {
  InstructionMark im(this);
  emit_byte(0x8F);
  emit_operand(rax, dst);
}


void Assembler::prefix(Prefix p) {
  a_byte(p);
}


void Assembler::movb(Register dst, Address src) {
  assert(dst->has_byte_register(), "must have byte register");
  InstructionMark im(this);
  emit_byte(0x8A);
  emit_operand(dst, src);
}


void Assembler::movb(Address dst, int imm8) {
  InstructionMark im(this);
  emit_byte(0xC6);
  emit_operand(rax, dst);
  emit_byte(imm8);
}


void Assembler::movb(Address dst, Register src) {
  assert(src->has_byte_register(), "must have byte register");
  InstructionMark im(this);
  emit_byte(0x88);
  emit_operand(src, dst);
}


void Assembler::movw(Address dst, int imm16) {
  InstructionMark im(this);

  emit_byte(0x66); // switch to 16-bit mode
  emit_byte(0xC7);
  emit_operand(rax, dst);
  emit_word(imm16);
}


void Assembler::movw(Register dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x66);
  emit_byte(0x8B);
  emit_operand(dst, src);
}


void Assembler::movw(Address dst, Register src) {
  InstructionMark im(this);
  emit_byte(0x66);
  emit_byte(0x89);
  emit_operand(src, dst);
}


void Assembler::movl(Register dst, int imm32) {
  emit_byte(0xB8 | dst->encoding());
  emit_long(imm32);
}

#ifndef _LP64
void Assembler::mov_literal32(Register dst, int32_t imm32, RelocationHolder const& rspec) {

  InstructionMark im(this);
  emit_byte(0xB8 | dst->encoding());
  emit_data((int)imm32, rspec, 0);
}
#endif // _LP64

void Assembler::movl(Register dst, Register src) {
  emit_byte(0x8B);
  emit_byte(0xC0 | (dst->encoding() << 3) | src->encoding());
}


void Assembler::movl(Register dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x8B);
  emit_operand(dst, src);
}


void Assembler::movl(Address dst, int imm32) {
  InstructionMark im(this);
  emit_byte(0xC7);
  emit_operand(rax, dst);
  emit_long(imm32);
}

#ifndef _LP64
void Assembler::mov_literal32(Address dst, int32_t imm32,  RelocationHolder const& rspec) {
  InstructionMark im(this);
  emit_byte(0xC7);
  emit_operand(rax, dst);
  emit_data((int)imm32, rspec, 0);
}
#endif // _LP64

void Assembler::movl(Address dst, Register src) {
  InstructionMark im(this);
  emit_byte(0x89);
  emit_operand(src, dst);
}

void Assembler::movsxb(Register dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x0F);
  emit_byte(0xBE);
  emit_operand(dst, src);
}

void Assembler::movsxb(Register dst, Register src) {
  assert(src->has_byte_register(), "must have byte register");
  emit_byte(0x0F);
  emit_byte(0xBE);
  emit_byte(0xC0 | (dst->encoding() << 3) | src->encoding());
}


void Assembler::movsxw(Register dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x0F);
  emit_byte(0xBF);
  emit_operand(dst, src);
}


void Assembler::movsxw(Register dst, Register src) {
  emit_byte(0x0F);
  emit_byte(0xBF);
  emit_byte(0xC0 | (dst->encoding() << 3) | src->encoding());
}


void Assembler::movzxb(Register dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x0F);
  emit_byte(0xB6);
  emit_operand(dst, src);
}


void Assembler::movzxb(Register dst, Register src) {
  assert(src->has_byte_register(), "must have byte register");
  emit_byte(0x0F);
  emit_byte(0xB6);
  emit_byte(0xC0 | (dst->encoding() << 3) | src->encoding());
}


void Assembler::movzxw(Register dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x0F);
  emit_byte(0xB7);
  emit_operand(dst, src);
}


void Assembler::movzxw(Register dst, Register src) {
  emit_byte(0x0F);
  emit_byte(0xB7);
  emit_byte(0xC0 | (dst->encoding() << 3) | src->encoding());
}


void Assembler::cmovl(Condition cc, Register dst, Register src) {
  guarantee(VM_Version::supports_cmov(), "illegal instruction");
  emit_byte(0x0F);
  emit_byte(0x40 | cc);
  emit_byte(0xC0 | (dst->encoding() << 3) | src->encoding());
}


void Assembler::cmovl(Condition cc, Register dst, Address src) {
  guarantee(VM_Version::supports_cmov(), "illegal instruction");
  // The code below seems to be wrong - however the manual is inconclusive
  // do not use for now (remember to enable all callers when fixing this)
  Unimplemented();
  // wrong bytes?
  InstructionMark im(this);
  emit_byte(0x0F);
  emit_byte(0x40 | cc);
  emit_operand(dst, src);
}


void Assembler::prefetcht0(Address src) {
  assert(VM_Version::supports_sse(), "must support");
  InstructionMark im(this);
  emit_byte(0x0F);
  emit_byte(0x18);
  emit_operand(rcx, src); // 1, src
}


void Assembler::prefetcht1(Address src) {
  assert(VM_Version::supports_sse(), "must support");
  InstructionMark im(this);
  emit_byte(0x0F);
  emit_byte(0x18);
  emit_operand(rdx, src); // 2, src
}


void Assembler::prefetcht2(Address src) {
  assert(VM_Version::supports_sse(), "must support");
  InstructionMark im(this);
  emit_byte(0x0F);
  emit_byte(0x18);
  emit_operand(rbx, src); // 3, src
}


void Assembler::prefetchnta(Address src) {
  assert(VM_Version::supports_sse2(), "must support");
  InstructionMark im(this);
  emit_byte(0x0F);
  emit_byte(0x18);
  emit_operand(rax, src); // 0, src
}


void Assembler::prefetchw(Address src) {
  assert(VM_Version::supports_3dnow(), "must support");
  InstructionMark im(this);
  emit_byte(0x0F);
  emit_byte(0x0D);
  emit_operand(rcx, src); // 1, src
}


void Assembler::prefetchr(Address src) {
  assert(VM_Version::supports_3dnow(), "must support");
  InstructionMark im(this);
  emit_byte(0x0F);
  emit_byte(0x0D);
  emit_operand(rax, src); // 0, src
}


void Assembler::adcl(Register dst, int imm32) {
  emit_arith(0x81, 0xD0, dst, imm32);
}


void Assembler::adcl(Register dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x13);
  emit_operand(dst, src);
}


void Assembler::adcl(Register dst, Register src) {
  emit_arith(0x13, 0xC0, dst, src);
}


void Assembler::addl(Address dst, int imm32) {
  InstructionMark im(this);
  emit_arith_operand(0x81,rax,dst,imm32);
}


void Assembler::addl(Address dst, Register src) {
  InstructionMark im(this);
  emit_byte(0x01);
  emit_operand(src, dst);
}


void Assembler::addl(Register dst, int imm32) {
  emit_arith(0x81, 0xC0, dst, imm32);
}


void Assembler::addl(Register dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x03);
  emit_operand(dst, src);
}


void Assembler::addl(Register dst, Register src) {
  emit_arith(0x03, 0xC0, dst, src);
}


void Assembler::andl(Register dst, int imm32) {
  emit_arith(0x81, 0xE0, dst, imm32);
}


void Assembler::andl(Register dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x23);
  emit_operand(dst, src);
}


void Assembler::andl(Register dst, Register src) {
  emit_arith(0x23, 0xC0, dst, src);
}


void Assembler::cmpb(Address dst, int imm8) {
  InstructionMark im(this);
  emit_byte(0x80);
  emit_operand(rdi, dst);
  emit_byte(imm8);
}

void Assembler::cmpw(Address dst, int imm16) {
  InstructionMark im(this);
  emit_byte(0x66);
  emit_byte(0x81);
  emit_operand(rdi, dst);
  emit_word(imm16);
}

void Assembler::cmpl(Address dst, int imm32) {
  InstructionMark im(this);
  emit_byte(0x81);
  emit_operand(rdi, dst);
  emit_long(imm32);
}

#ifndef _LP64
void Assembler::cmp_literal32(Register src1, int32_t imm32, RelocationHolder const& rspec) {
  InstructionMark im(this);
  emit_byte(0x81);
  emit_byte(0xF8 | src1->encoding());
  emit_data(imm32, rspec, 0);
}

void Assembler::cmp_literal32(Address src1, int32_t imm32, RelocationHolder const& rspec) {
  InstructionMark im(this);
  emit_byte(0x81);
  emit_operand(rdi, src1);
  emit_data(imm32, rspec, 0);
}
#endif // _LP64


void Assembler::cmpl(Register dst, int imm32) {
  emit_arith(0x81, 0xF8, dst, imm32);
}


void Assembler::cmpl(Register dst, Register src) {
  emit_arith(0x3B, 0xC0, dst, src);
}


void Assembler::cmpl(Register dst, Address  src) {
  InstructionMark im(this);
  emit_byte(0x3B);
  emit_operand(dst, src);
}


void Assembler::decl(Register dst) {
  // Don't use it directly. Use MacroAssembler::decrement() instead.
  emit_byte(0x48 | dst->encoding());
}


void Assembler::decl(Address dst) {
  // Don't use it directly. Use MacroAssembler::decrement() instead.
  InstructionMark im(this);
  emit_byte(0xFF);
  emit_operand(rcx, dst);
}


void Assembler::idivl(Register src) {
  emit_byte(0xF7);
  emit_byte(0xF8 | src->encoding());
}


void Assembler::cdql() {
  emit_byte(0x99);
}


void Assembler::imull(Register dst, Register src) {
  emit_byte(0x0F);
  emit_byte(0xAF);
  emit_byte(0xC0 | dst->encoding() << 3 | src->encoding());
}


void Assembler::imull(Register dst, Register src, int value) {
  if (is8bit(value)) {
    emit_byte(0x6B);
    emit_byte(0xC0 | dst->encoding() << 3 | src->encoding());
    emit_byte(value);
  } else {
    emit_byte(0x69);
    emit_byte(0xC0 | dst->encoding() << 3 | src->encoding());
    emit_long(value);
  }
}


void Assembler::incl(Register dst) {
  // Don't use it directly. Use MacroAssembler::increment() instead.
  emit_byte(0x40 | dst->encoding());
}


void Assembler::incl(Address dst) {
  // Don't use it directly. Use MacroAssembler::increment() instead.
  InstructionMark im(this);
  emit_byte(0xFF);
  emit_operand(rax, dst);
}


void Assembler::leal(Register dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x8D);
  emit_operand(dst, src);
}

void Assembler::mull(Address src) {
  InstructionMark im(this);
  emit_byte(0xF7);
  emit_operand(rsp, src);
}


void Assembler::mull(Register src) {
  emit_byte(0xF7);
  emit_byte(0xE0 | src->encoding());
}


void Assembler::negl(Register dst) {
  emit_byte(0xF7);
  emit_byte(0xD8 | dst->encoding());
}


void Assembler::notl(Register dst) {
  emit_byte(0xF7);
  emit_byte(0xD0 | dst->encoding());
}


void Assembler::orl(Address dst, int imm32) {
  InstructionMark im(this);
  emit_byte(0x81);
  emit_operand(rcx, dst);
  emit_long(imm32);
}

void Assembler::orl(Register dst, int imm32) {
  emit_arith(0x81, 0xC8, dst, imm32);
}


void Assembler::orl(Register dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x0B);
  emit_operand(dst, src);
}


void Assembler::orl(Register dst, Register src) {
  emit_arith(0x0B, 0xC0, dst, src);
}


void Assembler::rcll(Register dst, int imm8) {
  assert(isShiftCount(imm8), "illegal shift count");
  if (imm8 == 1) {
    emit_byte(0xD1);
    emit_byte(0xD0 | dst->encoding());
  } else {
    emit_byte(0xC1);
    emit_byte(0xD0 | dst->encoding());
    emit_byte(imm8);
  }
}


void Assembler::sarl(Register dst, int imm8) {
  assert(isShiftCount(imm8), "illegal shift count");
  if (imm8 == 1) {
    emit_byte(0xD1);
    emit_byte(0xF8 | dst->encoding());
  } else {
    emit_byte(0xC1);
    emit_byte(0xF8 | dst->encoding());
    emit_byte(imm8);
  }
}


void Assembler::sarl(Register dst) {
  emit_byte(0xD3);
  emit_byte(0xF8 | dst->encoding());
}


void Assembler::sbbl(Address dst, int imm32) {
  InstructionMark im(this);
  emit_arith_operand(0x81,rbx,dst,imm32);
}


void Assembler::sbbl(Register dst, int imm32) {
  emit_arith(0x81, 0xD8, dst, imm32);
}


void Assembler::sbbl(Register dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x1B);
  emit_operand(dst, src);
}


void Assembler::sbbl(Register dst, Register src) {
  emit_arith(0x1B, 0xC0, dst, src);
}


void Assembler::shldl(Register dst, Register src) {
  emit_byte(0x0F);
  emit_byte(0xA5);
  emit_byte(0xC0 | src->encoding() << 3 | dst->encoding());
}


void Assembler::shll(Register dst, int imm8) {
  assert(isShiftCount(imm8), "illegal shift count");
  if (imm8 == 1 ) {
    emit_byte(0xD1);
    emit_byte(0xE0 | dst->encoding());
  } else {
    emit_byte(0xC1);
    emit_byte(0xE0 | dst->encoding());
    emit_byte(imm8);
  }
}


void Assembler::shll(Register dst) {
  emit_byte(0xD3);
  emit_byte(0xE0 | dst->encoding());
}


void Assembler::shrdl(Register dst, Register src) {
  emit_byte(0x0F);
  emit_byte(0xAD);
  emit_byte(0xC0 | src->encoding() << 3 | dst->encoding());
}


void Assembler::shrl(Register dst, int imm8) {
  assert(isShiftCount(imm8), "illegal shift count");
  emit_byte(0xC1);
  emit_byte(0xE8 | dst->encoding());
  emit_byte(imm8);
}


void Assembler::shrl(Register dst) {
  emit_byte(0xD3);
  emit_byte(0xE8 | dst->encoding());
}


void Assembler::subl(Address dst, int imm32) {
  if (is8bit(imm32)) {
    InstructionMark im(this);
    emit_byte(0x83);
    emit_operand(rbp, dst);
    emit_byte(imm32 & 0xFF);
  } else {
    InstructionMark im(this);
    emit_byte(0x81);
    emit_operand(rbp, dst);
    emit_long(imm32);
  }
}


void Assembler::subl(Register dst, int imm32) {
  emit_arith(0x81, 0xE8, dst, imm32);
}


void Assembler::subl(Address dst, Register src) {
  InstructionMark im(this);
  emit_byte(0x29);
  emit_operand(src, dst);
}


void Assembler::subl(Register dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x2B);
  emit_operand(dst, src);
}


void Assembler::subl(Register dst, Register src) {
  emit_arith(0x2B, 0xC0, dst, src);
}


void Assembler::testb(Register dst, int imm8) {
  assert(dst->has_byte_register(), "must have byte register");
  emit_arith_b(0xF6, 0xC0, dst, imm8);
}


void Assembler::testl(Register dst, int imm32) {
  // not using emit_arith because test
  // doesn't support sign-extension of
  // 8bit operands
  if (dst->encoding() == 0) {
    emit_byte(0xA9);
  } else {
    emit_byte(0xF7);
    emit_byte(0xC0 | dst->encoding());
  }
  emit_long(imm32);
}


void Assembler::testl(Register dst, Register src) {
  emit_arith(0x85, 0xC0, dst, src);
}

void Assembler::testl(Register dst, Address  src) {
  InstructionMark im(this);
  emit_byte(0x85);
  emit_operand(dst, src);
}

void Assembler::xaddl(Address dst, Register src) {
  InstructionMark im(this);
  emit_byte(0x0F);
  emit_byte(0xC1);
  emit_operand(src, dst);
}

void Assembler::xorl(Register dst, int imm32) {
  emit_arith(0x81, 0xF0, dst, imm32);
}


void Assembler::xorl(Register dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x33);
  emit_operand(dst, src);
}


void Assembler::xorl(Register dst, Register src) {
  emit_arith(0x33, 0xC0, dst, src);
}


void Assembler::bswap(Register reg) {
  emit_byte(0x0F);
  emit_byte(0xC8 | reg->encoding());
}


void Assembler::lock() {
  if (Atomics & 1) {
     // Emit either nothing, a NOP, or a NOP: prefix
     emit_byte(0x90) ;
  } else {
     emit_byte(0xF0);
  }
}


void Assembler::xchg(Register reg, Address adr) {
  InstructionMark im(this);
  emit_byte(0x87);
  emit_operand(reg, adr);
}


void Assembler::xchgl(Register dst, Register src) {
  emit_byte(0x87);
  emit_byte(0xc0 | dst->encoding() << 3 | src->encoding());
}


// The 32-bit cmpxchg compares the value at adr with the contents of rax,
// and stores reg into adr if so; otherwise, the value at adr is loaded into rax,.
// The ZF is set if the compared values were equal, and cleared otherwise.
void Assembler::cmpxchg(Register reg, Address adr) {
  if (Atomics & 2) {
     // caveat: no instructionmark, so this isn't relocatable.
     // Emit a synthetic, non-atomic, CAS equivalent.
     // Beware.  The synthetic form sets all ICCs, not just ZF.
     // cmpxchg r,[m] is equivalent to rax, = CAS (m, rax, r)
     cmpl (rax, adr) ;
     movl (rax, adr) ;
     if (reg != rax) {
        Label L ;
        jcc (Assembler::notEqual, L) ;
        movl (adr, reg) ;
        bind (L) ;
     }
  } else {
     InstructionMark im(this);
     emit_byte(0x0F);
     emit_byte(0xB1);
     emit_operand(reg, adr);
  }
}

// The 64-bit cmpxchg compares the value at adr with the contents of rdx:rax,
// and stores rcx:rbx into adr if so; otherwise, the value at adr is loaded
// into rdx:rax.  The ZF is set if the compared values were equal, and cleared otherwise.
void Assembler::cmpxchg8(Address adr) {
  InstructionMark im(this);
  emit_byte(0x0F);
  emit_byte(0xc7);
  emit_operand(rcx, adr);
}

void Assembler::hlt() {
  emit_byte(0xF4);
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

void Assembler::nop(int i) {
  assert(i > 0, " ");
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

void Assembler::ret(int imm16) {
  if (imm16 == 0) {
    emit_byte(0xC3);
  } else {
    emit_byte(0xC2);
    emit_word(imm16);
  }
}


void Assembler::set_byte_if_not_zero(Register dst) {
  emit_byte(0x0F);
  emit_byte(0x95);
  emit_byte(0xE0 | dst->encoding());
}


// copies a single word from [esi] to [edi]
void Assembler::smovl() {
  emit_byte(0xA5);
}

// copies data from [esi] to [edi] using rcx double words (m32)
void Assembler::rep_movl() {
  emit_byte(0xF3);
  emit_byte(0xA5);
}


// sets rcx double words (m32) with rax, value at [edi]
void Assembler::rep_set() {
  emit_byte(0xF3);
  emit_byte(0xAB);
}

// scans rcx double words (m32) at [edi] for occurance of rax,
void Assembler::repne_scan() {
  emit_byte(0xF2);
  emit_byte(0xAF);
}


void Assembler::setb(Condition cc, Register dst) {
  assert(0 <= cc && cc < 16, "illegal cc");
  emit_byte(0x0F);
  emit_byte(0x90 | cc);
  emit_byte(0xC0 | dst->encoding());
}

void Assembler::cld() {
  emit_byte(0xfc);
}

void Assembler::std() {
  emit_byte(0xfd);
}

void Assembler::emit_raw (unsigned char b) {
  emit_byte (b) ;
}

// Serializes memory.
void Assembler::membar() {
    // Memory barriers are only needed on multiprocessors
  if (os::is_MP()) {
    if( VM_Version::supports_sse2() ) {
      emit_byte( 0x0F );                // MFENCE; faster blows no regs
      emit_byte( 0xAE );
      emit_byte( 0xF0 );
    } else {
      // All usable chips support "locked" instructions which suffice
      // as barriers, and are much faster than the alternative of
      // using cpuid instruction. We use here a locked add [esp],0.
      // This is conveniently otherwise a no-op except for blowing
      // flags (which we save and restore.)
      pushfd();                // Save eflags register
      lock();
      addl(Address(rsp, 0), 0);// Assert the lock# signal here
      popfd();                 // Restore eflags register
    }
  }
}

// Identify processor type and features
void Assembler::cpuid() {
  // Note: we can't assert VM_Version::supports_cpuid() here
  //       because this instruction is used in the processor
  //       identification code.
  emit_byte( 0x0F );
  emit_byte( 0xA2 );
}

void Assembler::call(Label& L, relocInfo::relocType rtype) {
  if (L.is_bound()) {
    const int long_size = 5;
    int offs = target(L) - pc();
    assert(offs <= 0, "assembler error");
    InstructionMark im(this);
    // 1110 1000 #32-bit disp
    emit_byte(0xE8);
    emit_data(offs - long_size, rtype, 0);
  } else {
    InstructionMark im(this);
    // 1110 1000 #32-bit disp
    L.add_patch_at(code(), locator());
    emit_byte(0xE8);
    emit_data(int(0), rtype, 0);
  }
}

void Assembler::call(Register dst) {
  emit_byte(0xFF);
  emit_byte(0xD0 | dst->encoding());
}


void Assembler::call(Address adr) {
  InstructionMark im(this);
  relocInfo::relocType rtype = adr.reloc();
  if (rtype !=  relocInfo::runtime_call_type) {
    emit_byte(0xFF);
    emit_operand(rdx, adr);
  } else {
    assert(false, "ack");
  }

}

void Assembler::call_literal(address dest, RelocationHolder const& rspec) {
  InstructionMark im(this);
  emit_byte(0xE8);
  intptr_t disp = dest - (_code_pos + sizeof(int32_t));
  assert(dest != NULL, "must have a target");
  emit_data(disp, rspec, call32_operand);

}

void Assembler::jmp(Register entry) {
  emit_byte(0xFF);
  emit_byte(0xE0 | entry->encoding());
}


void Assembler::jmp(Address adr) {
  InstructionMark im(this);
  emit_byte(0xFF);
  emit_operand(rsp, adr);
}

void Assembler::jmp_literal(address dest, RelocationHolder const& rspec) {
  InstructionMark im(this);
  emit_byte(0xE9);
  assert(dest != NULL, "must have a target");
  intptr_t disp = dest - (_code_pos + sizeof(int32_t));
  emit_data(disp, rspec.reloc(), call32_operand);
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

void Assembler::jcc(Condition cc, Label& L, relocInfo::relocType rtype) {
  InstructionMark im(this);
  relocate(rtype);
  assert((0 <= cc) && (cc < 16), "illegal cc");
  if (L.is_bound()) {
    address dst = target(L);
    assert(dst != NULL, "jcc most probably wrong");

    const int short_size = 2;
    const int long_size = 6;
    int offs = (int)dst - ((int)_code_pos);
    if (rtype == relocInfo::none && is8bit(offs - short_size)) {
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
    jcc(cc, L);
  } else {
    InstructionMark im(this);
    L.add_patch_at(code(), locator());
    emit_byte(0x70 | cc);
    emit_byte(0);
  }
}

// FPU instructions

void Assembler::fld1() {
  emit_byte(0xD9);
  emit_byte(0xE8);
}


void Assembler::fldz() {
  emit_byte(0xD9);
  emit_byte(0xEE);
}


void Assembler::fld_s(Address adr) {
  InstructionMark im(this);
  emit_byte(0xD9);
  emit_operand(rax, adr);
}


void Assembler::fld_s (int index) {
  emit_farith(0xD9, 0xC0, index);
}


void Assembler::fld_d(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDD);
  emit_operand(rax, adr);
}


void Assembler::fld_x(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDB);
  emit_operand(rbp, adr);
}


void Assembler::fst_s(Address adr) {
  InstructionMark im(this);
  emit_byte(0xD9);
  emit_operand(rdx, adr);
}


void Assembler::fst_d(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDD);
  emit_operand(rdx, adr);
}


void Assembler::fstp_s(Address adr) {
  InstructionMark im(this);
  emit_byte(0xD9);
  emit_operand(rbx, adr);
}


void Assembler::fstp_d(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDD);
  emit_operand(rbx, adr);
}


void Assembler::fstp_x(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDB);
  emit_operand(rdi, adr);
}


void Assembler::fstp_d(int index) {
  emit_farith(0xDD, 0xD8, index);
}


void Assembler::fild_s(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDB);
  emit_operand(rax, adr);
}


void Assembler::fild_d(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDF);
  emit_operand(rbp, adr);
}


void Assembler::fistp_s(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDB);
  emit_operand(rbx, adr);
}


void Assembler::fistp_d(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDF);
  emit_operand(rdi, adr);
}


void Assembler::fist_s(Address adr) {
  InstructionMark im(this);
  emit_byte(0xDB);
  emit_operand(rdx, adr);
}


void Assembler::fabs() {
  emit_byte(0xD9);
  emit_byte(0xE1);
}


void Assembler::fldln2() {
  emit_byte(0xD9);
  emit_byte(0xED);
}

void Assembler::fyl2x() {
  emit_byte(0xD9);
  emit_byte(0xF1);
}


void Assembler::fldlg2() {
  emit_byte(0xD9);
  emit_byte(0xEC);
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


void Assembler::fsin() {
  emit_byte(0xD9);
  emit_byte(0xFE);
}


void Assembler::fcos() {
  emit_byte(0xD9);
  emit_byte(0xFF);
}

void Assembler::ftan() {
  emit_byte(0xD9);
  emit_byte(0xF2);
  emit_byte(0xDD);
  emit_byte(0xD8);
}

void Assembler::fsqrt() {
  emit_byte(0xD9);
  emit_byte(0xFA);
}


void Assembler::fchs() {
  emit_byte(0xD9);
  emit_byte(0xE0);
}


void Assembler::fadd_s(Address src) {
  InstructionMark im(this);
  emit_byte(0xD8);
  emit_operand(rax, src);
}


void Assembler::fadd_d(Address src) {
  InstructionMark im(this);
  emit_byte(0xDC);
  emit_operand(rax, src);
}


void Assembler::fadd(int i) {
  emit_farith(0xD8, 0xC0, i);
}


void Assembler::fadda(int i) {
  emit_farith(0xDC, 0xC0, i);
}


void Assembler::fsub_d(Address src) {
  InstructionMark im(this);
  emit_byte(0xDC);
  emit_operand(rsp, src);
}


void Assembler::fsub_s(Address src) {
  InstructionMark im(this);
  emit_byte(0xD8);
  emit_operand(rsp, src);
}


void Assembler::fsubr_s(Address src) {
  InstructionMark im(this);
  emit_byte(0xD8);
  emit_operand(rbp, src);
}


void Assembler::fsubr_d(Address src) {
  InstructionMark im(this);
  emit_byte(0xDC);
  emit_operand(rbp, src);
}


void Assembler::fmul_s(Address src) {
  InstructionMark im(this);
  emit_byte(0xD8);
  emit_operand(rcx, src);
}


void Assembler::fmul_d(Address src) {
  InstructionMark im(this);
  emit_byte(0xDC);
  emit_operand(rcx, src);
}


void Assembler::fmul(int i) {
  emit_farith(0xD8, 0xC8, i);
}


void Assembler::fmula(int i) {
  emit_farith(0xDC, 0xC8, i);
}


void Assembler::fdiv_s(Address src) {
  InstructionMark im(this);
  emit_byte(0xD8);
  emit_operand(rsi, src);
}


void Assembler::fdiv_d(Address src) {
  InstructionMark im(this);
  emit_byte(0xDC);
  emit_operand(rsi, src);
}


void Assembler::fdivr_s(Address src) {
  InstructionMark im(this);
  emit_byte(0xD8);
  emit_operand(rdi, src);
}


void Assembler::fdivr_d(Address src) {
  InstructionMark im(this);
  emit_byte(0xDC);
  emit_operand(rdi, src);
}


void Assembler::fsub(int i) {
  emit_farith(0xD8, 0xE0, i);
}


void Assembler::fsuba(int i) {
  emit_farith(0xDC, 0xE8, i);
}


void Assembler::fsubr(int i) {
  emit_farith(0xD8, 0xE8, i);
}


void Assembler::fsubra(int i) {
  emit_farith(0xDC, 0xE0, i);
}


void Assembler::fdiv(int i) {
  emit_farith(0xD8, 0xF0, i);
}


void Assembler::fdiva(int i) {
  emit_farith(0xDC, 0xF8, i);
}


void Assembler::fdivr(int i) {
  emit_farith(0xD8, 0xF8, i);
}


void Assembler::fdivra(int i) {
  emit_farith(0xDC, 0xF0, i);
}


// Note: The Intel manual (Pentium Processor User's Manual, Vol.3, 1994)
//       is erroneous for some of the floating-point instructions below.

void Assembler::fdivp(int i) {
  emit_farith(0xDE, 0xF8, i);                    // ST(0) <- ST(0) / ST(1) and pop (Intel manual wrong)
}


void Assembler::fdivrp(int i) {
  emit_farith(0xDE, 0xF0, i);                    // ST(0) <- ST(1) / ST(0) and pop (Intel manual wrong)
}


void Assembler::fsubp(int i) {
  emit_farith(0xDE, 0xE8, i);                    // ST(0) <- ST(0) - ST(1) and pop (Intel manual wrong)
}


void Assembler::fsubrp(int i) {
  emit_farith(0xDE, 0xE0, i);                    // ST(0) <- ST(1) - ST(0) and pop (Intel manual wrong)
}


void Assembler::faddp(int i) {
  emit_farith(0xDE, 0xC0, i);
}


void Assembler::fmulp(int i) {
  emit_farith(0xDE, 0xC8, i);
}


void Assembler::fprem() {
  emit_byte(0xD9);
  emit_byte(0xF8);
}


void Assembler::fprem1() {
  emit_byte(0xD9);
  emit_byte(0xF5);
}


void Assembler::fxch(int i) {
  emit_farith(0xD9, 0xC8, i);
}


void Assembler::fincstp() {
  emit_byte(0xD9);
  emit_byte(0xF7);
}


void Assembler::fdecstp() {
  emit_byte(0xD9);
  emit_byte(0xF6);
}


void Assembler::ffree(int i) {
  emit_farith(0xDD, 0xC0, i);
}


void Assembler::fcomp_s(Address src) {
  InstructionMark im(this);
  emit_byte(0xD8);
  emit_operand(rbx, src);
}


void Assembler::fcomp_d(Address src) {
  InstructionMark im(this);
  emit_byte(0xDC);
  emit_operand(rbx, src);
}


void Assembler::fcom(int i) {
  emit_farith(0xD8, 0xD0, i);
}


void Assembler::fcomp(int i) {
  emit_farith(0xD8, 0xD8, i);
}


void Assembler::fcompp() {
  emit_byte(0xDE);
  emit_byte(0xD9);
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


void Assembler::ftst() {
  emit_byte(0xD9);
  emit_byte(0xE4);
}


void Assembler::fnstsw_ax() {
  emit_byte(0xdF);
  emit_byte(0xE0);
}


void Assembler::fwait() {
  emit_byte(0x9B);
}


void Assembler::finit() {
  emit_byte(0x9B);
  emit_byte(0xDB);
  emit_byte(0xE3);
}


void Assembler::fldcw(Address src) {
  InstructionMark im(this);
  emit_byte(0xd9);
  emit_operand(rbp, src);
}


void Assembler::fnstcw(Address src) {
  InstructionMark im(this);
  emit_byte(0x9B);
  emit_byte(0xD9);
  emit_operand(rdi, src);
}

void Assembler::fnsave(Address dst) {
  InstructionMark im(this);
  emit_byte(0xDD);
  emit_operand(rsi, dst);
}


void Assembler::frstor(Address src) {
  InstructionMark im(this);
  emit_byte(0xDD);
  emit_operand(rsp, src);
}


void Assembler::fldenv(Address src) {
  InstructionMark im(this);
  emit_byte(0xD9);
  emit_operand(rsp, src);
}


void Assembler::sahf() {
  emit_byte(0x9E);
}

// MMX operations
void Assembler::emit_operand(MMXRegister reg, Address adr) {
  emit_operand((Register)reg, adr._base, adr._index, adr._scale, adr._disp, adr._rspec);
}

void Assembler::movq( MMXRegister dst, Address src ) {
  assert( VM_Version::supports_mmx(), "" );
  emit_byte(0x0F);
  emit_byte(0x6F);
  emit_operand(dst,src);
}

void Assembler::movq( Address dst, MMXRegister src ) {
  assert( VM_Version::supports_mmx(), "" );
  emit_byte(0x0F);
  emit_byte(0x7F);
  emit_operand(src,dst);
}

void Assembler::emms() {
  emit_byte(0x0F);
  emit_byte(0x77);
}




// SSE and SSE2 instructions
inline void Assembler::emit_sse_operand(XMMRegister reg, Address adr) {
  assert(((Register)reg)->encoding() == reg->encoding(), "otherwise typecast is invalid");
  emit_operand((Register)reg, adr._base, adr._index, adr._scale, adr._disp, adr._rspec);
}
inline void Assembler::emit_sse_operand(Register reg, Address adr) {
  emit_operand(reg, adr._base, adr._index, adr._scale, adr._disp, adr._rspec);
}

inline void Assembler::emit_sse_operand(XMMRegister dst, XMMRegister src) {
  emit_byte(0xC0 | dst->encoding() << 3 | src->encoding());
}
inline void Assembler::emit_sse_operand(XMMRegister dst, Register src) {
  emit_byte(0xC0 | dst->encoding() << 3 | src->encoding());
}
inline void Assembler::emit_sse_operand(Register dst, XMMRegister src) {
  emit_byte(0xC0 | dst->encoding() << 3 | src->encoding());
}


// Macro for creation of SSE2 instructions
// The SSE2 instricution set is highly regular, so this macro saves
// a lot of cut&paste
// Each macro expansion creates two methods (same name with different
// parameter list)
//
// Macro parameters:
//  * name: name of the created methods
//  * sse_version: either sse or sse2 for the assertion if instruction supported by processor
//  * prefix: first opcode byte of the instruction (or 0 if no prefix byte)
//  * opcode: last opcode byte of the instruction
//  * conversion instruction have parameters of type Register instead of XMMRegister,
//    so this can also configured with macro parameters
#define emit_sse_instruction(name, sse_version, prefix, opcode, dst_register_type, src_register_type)      \
                                                                         \
  void Assembler:: name (dst_register_type dst, Address src) {           \
    assert(VM_Version::supports_##sse_version(), "");                    \
                                                                         \
    InstructionMark im(this);                                            \
    if (prefix != 0) emit_byte(prefix);                                  \
    emit_byte(0x0F);                                                     \
    emit_byte(opcode);                                                   \
    emit_sse_operand(dst, src);                                          \
  }                                                                      \
                                                                         \
  void Assembler:: name (dst_register_type dst, src_register_type src) { \
    assert(VM_Version::supports_##sse_version(), "");                    \
                                                                         \
    if (prefix != 0) emit_byte(prefix);                                  \
    emit_byte(0x0F);                                                     \
    emit_byte(opcode);                                                   \
    emit_sse_operand(dst, src);                                          \
  }                                                                      \

emit_sse_instruction(addss,  sse,  0xF3, 0x58, XMMRegister, XMMRegister);
emit_sse_instruction(addsd,  sse2, 0xF2, 0x58, XMMRegister, XMMRegister)
emit_sse_instruction(subss,  sse,  0xF3, 0x5C, XMMRegister, XMMRegister)
emit_sse_instruction(subsd,  sse2, 0xF2, 0x5C, XMMRegister, XMMRegister)
emit_sse_instruction(mulss,  sse,  0xF3, 0x59, XMMRegister, XMMRegister)
emit_sse_instruction(mulsd,  sse2, 0xF2, 0x59, XMMRegister, XMMRegister)
emit_sse_instruction(divss,  sse,  0xF3, 0x5E, XMMRegister, XMMRegister)
emit_sse_instruction(divsd,  sse2, 0xF2, 0x5E, XMMRegister, XMMRegister)
emit_sse_instruction(sqrtss, sse,  0xF3, 0x51, XMMRegister, XMMRegister)
emit_sse_instruction(sqrtsd, sse2, 0xF2, 0x51, XMMRegister, XMMRegister)

emit_sse_instruction(pxor,  sse2,  0x66, 0xEF, XMMRegister, XMMRegister)

emit_sse_instruction(comiss,  sse,  0,    0x2F, XMMRegister, XMMRegister)
emit_sse_instruction(comisd,  sse2, 0x66, 0x2F, XMMRegister, XMMRegister)
emit_sse_instruction(ucomiss, sse,  0,    0x2E, XMMRegister, XMMRegister)
emit_sse_instruction(ucomisd, sse2, 0x66, 0x2E, XMMRegister, XMMRegister)

emit_sse_instruction(cvtss2sd,  sse2, 0xF3, 0x5A, XMMRegister, XMMRegister);
emit_sse_instruction(cvtsd2ss,  sse2, 0xF2, 0x5A, XMMRegister, XMMRegister)
emit_sse_instruction(cvtsi2ss,  sse,  0xF3, 0x2A, XMMRegister, Register);
emit_sse_instruction(cvtsi2sd,  sse2, 0xF2, 0x2A, XMMRegister, Register)
emit_sse_instruction(cvtss2si,  sse,  0xF3, 0x2D, Register, XMMRegister);
emit_sse_instruction(cvtsd2si,  sse2, 0xF2, 0x2D, Register, XMMRegister)
emit_sse_instruction(cvttss2si, sse,  0xF3, 0x2C, Register, XMMRegister);
emit_sse_instruction(cvttsd2si, sse2, 0xF2, 0x2C, Register, XMMRegister)

emit_sse_instruction(movss, sse,  0xF3, 0x10, XMMRegister, XMMRegister)
emit_sse_instruction(movsd, sse2, 0xF2, 0x10, XMMRegister, XMMRegister)

emit_sse_instruction(movq,  sse2, 0xF3, 0x7E, XMMRegister, XMMRegister);
emit_sse_instruction(movd,  sse2, 0x66, 0x6E, XMMRegister, Register);
emit_sse_instruction(movdqa, sse2, 0x66, 0x6F, XMMRegister, XMMRegister);

emit_sse_instruction(punpcklbw,  sse2, 0x66, 0x60, XMMRegister, XMMRegister);


// Instruction not covered by macro
void Assembler::movq(Address dst, XMMRegister src) {
  assert(VM_Version::supports_sse2(), "");

  InstructionMark im(this);
  emit_byte(0x66);
  emit_byte(0x0F);
  emit_byte(0xD6);
  emit_sse_operand(src, dst);
}

void Assembler::movd(Address dst, XMMRegister src) {
  assert(VM_Version::supports_sse2(), "");

  InstructionMark im(this);
  emit_byte(0x66);
  emit_byte(0x0F);
  emit_byte(0x7E);
  emit_sse_operand(src, dst);
}

void Assembler::movd(Register dst, XMMRegister src) {
  assert(VM_Version::supports_sse2(), "");

  emit_byte(0x66);
  emit_byte(0x0F);
  emit_byte(0x7E);
  emit_sse_operand(src, dst);
}

void Assembler::movdqa(Address dst, XMMRegister src) {
  assert(VM_Version::supports_sse2(), "");

  InstructionMark im(this);
  emit_byte(0x66);
  emit_byte(0x0F);
  emit_byte(0x7F);
  emit_sse_operand(src, dst);
}

void Assembler::pshufd(XMMRegister dst, XMMRegister src, int mode) {
  assert(isByte(mode), "invalid value");
  assert(VM_Version::supports_sse2(), "");

  emit_byte(0x66);
  emit_byte(0x0F);
  emit_byte(0x70);
  emit_sse_operand(dst, src);
  emit_byte(mode & 0xFF);
}

void Assembler::pshufd(XMMRegister dst, Address src, int mode) {
  assert(isByte(mode), "invalid value");
  assert(VM_Version::supports_sse2(), "");

  InstructionMark im(this);
  emit_byte(0x66);
  emit_byte(0x0F);
  emit_byte(0x70);
  emit_sse_operand(dst, src);
  emit_byte(mode & 0xFF);
}

void Assembler::pshuflw(XMMRegister dst, XMMRegister src, int mode) {
  assert(isByte(mode), "invalid value");
  assert(VM_Version::supports_sse2(), "");

  emit_byte(0xF2);
  emit_byte(0x0F);
  emit_byte(0x70);
  emit_sse_operand(dst, src);
  emit_byte(mode & 0xFF);
}

void Assembler::pshuflw(XMMRegister dst, Address src, int mode) {
  assert(isByte(mode), "invalid value");
  assert(VM_Version::supports_sse2(), "");

  InstructionMark im(this);
  emit_byte(0xF2);
  emit_byte(0x0F);
  emit_byte(0x70);
  emit_sse_operand(dst, src);
  emit_byte(mode & 0xFF);
}

void Assembler::psrlq(XMMRegister dst, int shift) {
  assert(VM_Version::supports_sse2(), "");

  emit_byte(0x66);
  emit_byte(0x0F);
  emit_byte(0x73);
  emit_sse_operand(xmm2, dst);
  emit_byte(shift);
}

void Assembler::movss( Address dst, XMMRegister src ) {
  assert(VM_Version::supports_sse(), "");

  InstructionMark im(this);
  emit_byte(0xF3); // single
  emit_byte(0x0F);
  emit_byte(0x11); // store
  emit_sse_operand(src, dst);
}

void Assembler::movsd( Address dst, XMMRegister src ) {
  assert(VM_Version::supports_sse2(), "");

  InstructionMark im(this);
  emit_byte(0xF2); // double
  emit_byte(0x0F);
  emit_byte(0x11); // store
  emit_sse_operand(src,dst);
}

// New cpus require to use movaps and movapd to avoid partial register stall
// when moving between registers.
void Assembler::movaps(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_sse(), "");

  emit_byte(0x0F);
  emit_byte(0x28);
  emit_sse_operand(dst, src);
}
void Assembler::movapd(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_sse2(), "");

  emit_byte(0x66);
  emit_byte(0x0F);
  emit_byte(0x28);
  emit_sse_operand(dst, src);
}

// New cpus require to use movsd and movss to avoid partial register stall
// when loading from memory. But for old Opteron use movlpd instead of movsd.
// The selection is done in MacroAssembler::movdbl() and movflt().
void Assembler::movlpd(XMMRegister dst, Address src) {
  assert(VM_Version::supports_sse(), "");

  InstructionMark im(this);
  emit_byte(0x66);
  emit_byte(0x0F);
  emit_byte(0x12);
  emit_sse_operand(dst, src);
}

void Assembler::cvtdq2pd(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_sse2(), "");

  emit_byte(0xF3);
  emit_byte(0x0F);
  emit_byte(0xE6);
  emit_sse_operand(dst, src);
}

void Assembler::cvtdq2ps(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_sse2(), "");

  emit_byte(0x0F);
  emit_byte(0x5B);
  emit_sse_operand(dst, src);
}

emit_sse_instruction(andps,  sse,  0,    0x54, XMMRegister, XMMRegister);
emit_sse_instruction(andpd,  sse2, 0x66, 0x54, XMMRegister, XMMRegister);
emit_sse_instruction(andnps, sse,  0,    0x55, XMMRegister, XMMRegister);
emit_sse_instruction(andnpd, sse2, 0x66, 0x55, XMMRegister, XMMRegister);
emit_sse_instruction(orps,   sse,  0,    0x56, XMMRegister, XMMRegister);
emit_sse_instruction(orpd,   sse2, 0x66, 0x56, XMMRegister, XMMRegister);
emit_sse_instruction(xorps,  sse,  0,    0x57, XMMRegister, XMMRegister);
emit_sse_instruction(xorpd,  sse2, 0x66, 0x57, XMMRegister, XMMRegister);


void Assembler::ldmxcsr( Address src) {
  InstructionMark im(this);
  emit_byte(0x0F);
  emit_byte(0xAE);
  emit_operand(rdx /* 2 */, src);
}

void Assembler::stmxcsr( Address dst) {
  InstructionMark im(this);
  emit_byte(0x0F);
  emit_byte(0xAE);
  emit_operand(rbx /* 3 */, dst);
}

// Implementation of MacroAssembler

Address MacroAssembler::as_Address(AddressLiteral adr) {
  // amd64 always does this as a pc-rel
  // we can be absolute or disp based on the instruction type
  // jmp/call are displacements others are absolute
  assert(!adr.is_lval(), "must be rval");

  return Address(adr.target(), adr.rspec());
}

Address MacroAssembler::as_Address(ArrayAddress adr) {
  return Address::make_array(adr);
}

void MacroAssembler::fat_nop() {
  // A 5 byte nop that is safe for patching (see patch_verified_entry)
  emit_byte(0x26); // es:
  emit_byte(0x2e); // cs:
  emit_byte(0x64); // fs:
  emit_byte(0x65); // gs:
  emit_byte(0x90);
}

// 32bit can do a case table jump in one instruction but we no longer allow the base
// to be installed in the Address class
void MacroAssembler::jump(ArrayAddress entry) {
  jmp(as_Address(entry));
}

void MacroAssembler::jump(AddressLiteral dst) {
  jmp_literal(dst.target(), dst.rspec());
}

void MacroAssembler::jump_cc(Condition cc, AddressLiteral dst) {
  assert((0 <= cc) && (cc < 16), "illegal cc");

  InstructionMark im(this);

  relocInfo::relocType rtype = dst.reloc();
  relocate(rtype);
  const int short_size = 2;
  const int long_size = 6;
  int offs = (int)dst.target() - ((int)_code_pos);
  if (rtype == relocInfo::none && is8bit(offs - short_size)) {
    // 0111 tttn #8-bit disp
    emit_byte(0x70 | cc);
    emit_byte((offs - short_size) & 0xFF);
  } else {
    // 0000 1111 1000 tttn #32-bit disp
    emit_byte(0x0F);
    emit_byte(0x80 | cc);
    emit_long(offs - long_size);
  }
}

// Calls
void MacroAssembler::call(Label& L, relocInfo::relocType rtype) {
  Assembler::call(L, rtype);
}

void MacroAssembler::call(Register entry) {
  Assembler::call(entry);
}

void MacroAssembler::call(AddressLiteral entry) {
  Assembler::call_literal(entry.target(), entry.rspec());
}


void MacroAssembler::cmp8(AddressLiteral src1, int8_t imm) {
  Assembler::cmpb(as_Address(src1), imm);
}

void MacroAssembler::cmp32(AddressLiteral src1, int32_t imm) {
  Assembler::cmpl(as_Address(src1), imm);
}

void MacroAssembler::cmp32(Register src1, AddressLiteral src2) {
  if (src2.is_lval()) {
    cmp_literal32(src1, (int32_t) src2.target(), src2.rspec());
  } else {
    Assembler::cmpl(src1, as_Address(src2));
  }
}

void MacroAssembler::cmp32(Register src1, int32_t imm) {
  Assembler::cmpl(src1, imm);
}

void MacroAssembler::cmp32(Register src1, Address src2) {
  Assembler::cmpl(src1, src2);
}

void MacroAssembler::cmpoop(Address src1, jobject obj) {
  cmp_literal32(src1, (int32_t)obj, oop_Relocation::spec_for_immediate());
}

void MacroAssembler::cmpoop(Register src1, jobject obj) {
  cmp_literal32(src1, (int32_t)obj, oop_Relocation::spec_for_immediate());
}

void MacroAssembler::cmpptr(Register src1, AddressLiteral src2) {
  if (src2.is_lval()) {
    // compare the effect address of src2 to src1
    cmp_literal32(src1, (int32_t)src2.target(), src2.rspec());
  } else {
    Assembler::cmpl(src1, as_Address(src2));
  }
}

void MacroAssembler::cmpptr(Address src1, AddressLiteral src2) {
  assert(src2.is_lval(), "not a mem-mem compare");
  cmp_literal32(src1, (int32_t) src2.target(), src2.rspec());
}


void MacroAssembler::cmpxchgptr(Register reg, AddressLiteral adr) {
  cmpxchg(reg, as_Address(adr));
}

void MacroAssembler::increment(AddressLiteral dst) {
  increment(as_Address(dst));
}

void MacroAssembler::increment(ArrayAddress dst) {
  increment(as_Address(dst));
}

void MacroAssembler::lea(Register dst, AddressLiteral adr) {
  // leal(dst, as_Address(adr));
  // see note in movl as to why we musr use a move
  mov_literal32(dst, (int32_t) adr.target(), adr.rspec());
}

void MacroAssembler::lea(Address dst, AddressLiteral adr) {
  // leal(dst, as_Address(adr));
  // see note in movl as to why we musr use a move
  mov_literal32(dst, (int32_t) adr.target(), adr.rspec());
}

void MacroAssembler::mov32(AddressLiteral dst, Register src) {
  Assembler::movl(as_Address(dst), src);
}

void MacroAssembler::mov32(Register dst, AddressLiteral src) {
  Assembler::movl(dst, as_Address(src));
}

void MacroAssembler::movbyte(ArrayAddress dst, int src) {
  movb(as_Address(dst), src);
}

void MacroAssembler::movoop(Address dst, jobject obj) {
  mov_literal32(dst, (int32_t)obj, oop_Relocation::spec_for_immediate());
}

void MacroAssembler::movoop(Register dst, jobject obj) {
  mov_literal32(dst, (int32_t)obj, oop_Relocation::spec_for_immediate());
}

void MacroAssembler::movptr(Register dst, AddressLiteral src) {
  if (src.is_lval()) {
    // essentially an lea
    mov_literal32(dst, (int32_t) src.target(), src.rspec());
  } else {
    // mov 32bits from an absolute address
    movl(dst, as_Address(src));
  }
}

void MacroAssembler::movptr(ArrayAddress dst, Register src) {
  movl(as_Address(dst), src);
}

void MacroAssembler::movptr(Register dst, ArrayAddress src) {
  movl(dst, as_Address(src));
}

void MacroAssembler::movflt(XMMRegister dst, AddressLiteral src) {
  movss(dst, as_Address(src));
}

void MacroAssembler::movdbl(XMMRegister dst, AddressLiteral src) {
  if (UseXmmLoadAndClearUpper) { movsd (dst, as_Address(src)); return; }
  else                         { movlpd(dst, as_Address(src)); return; }
}

void Assembler::pushoop(jobject obj) {
  push_literal32((int32_t)obj, oop_Relocation::spec_for_immediate());
}


void MacroAssembler::pushptr(AddressLiteral src) {
  if (src.is_lval()) {
    push_literal32((int32_t)src.target(), src.rspec());
  } else {
    pushl(as_Address(src));
  }
}

void MacroAssembler::test32(Register src1, AddressLiteral src2) {
  // src2 must be rval
  testl(src1, as_Address(src2));
}

// FPU

void MacroAssembler::fld_x(AddressLiteral src) {
  Assembler::fld_x(as_Address(src));
}

void MacroAssembler::fld_d(AddressLiteral src) {
  fld_d(as_Address(src));
}

void MacroAssembler::fld_s(AddressLiteral src) {
  fld_s(as_Address(src));
}

void MacroAssembler::fldcw(AddressLiteral src) {
  Assembler::fldcw(as_Address(src));
}

void MacroAssembler::ldmxcsr(AddressLiteral src) {
  Assembler::ldmxcsr(as_Address(src));
}

// SSE

void MacroAssembler::andpd(XMMRegister dst, AddressLiteral src) {
  andpd(dst, as_Address(src));
}

void MacroAssembler::comisd(XMMRegister dst, AddressLiteral src) {
  comisd(dst, as_Address(src));
}

void MacroAssembler::comiss(XMMRegister dst, AddressLiteral src) {
  comiss(dst, as_Address(src));
}

void MacroAssembler::movsd(XMMRegister dst, AddressLiteral src) {
  movsd(dst, as_Address(src));
}

void MacroAssembler::movss(XMMRegister dst, AddressLiteral src) {
  movss(dst, as_Address(src));
}

void MacroAssembler::xorpd(XMMRegister dst, AddressLiteral src) {
  xorpd(dst, as_Address(src));
}

void MacroAssembler::xorps(XMMRegister dst, AddressLiteral src) {
  xorps(dst, as_Address(src));
}

void MacroAssembler::ucomisd(XMMRegister dst, AddressLiteral src) {
  ucomisd(dst, as_Address(src));
}

void MacroAssembler::ucomiss(XMMRegister dst, AddressLiteral src) {
  ucomiss(dst, as_Address(src));
}

void MacroAssembler::null_check(Register reg, int offset) {
  if (needs_explicit_null_check(offset)) {
    // provoke OS NULL exception if reg = NULL by
    // accessing M[reg] w/o changing any (non-CC) registers
    cmpl(rax, Address(reg, 0));
    // Note: should probably use testl(rax, Address(reg, 0));
    //       may be shorter code (however, this version of
    //       testl needs to be implemented first)
  } else {
    // nothing to do, (later) access of M[reg + offset]
    // will provoke OS NULL exception if reg = NULL
  }
}


int MacroAssembler::load_unsigned_byte(Register dst, Address src) {
  // According to Intel Doc. AP-526, "Zero-Extension of Short", p.16,
  // and "3.9 Partial Register Penalties", p. 22).
  int off;
  if (VM_Version::is_P6() || src.uses(dst)) {
    off = offset();
    movzxb(dst, src);
  } else {
    xorl(dst, dst);
    off = offset();
    movb(dst, src);
  }
  return off;
}


int MacroAssembler::load_unsigned_word(Register dst, Address src) {
  // According to Intel Doc. AP-526, "Zero-Extension of Short", p.16,
  // and "3.9 Partial Register Penalties", p. 22).
  int off;
  if (VM_Version::is_P6() || src.uses(dst)) {
    off = offset();
    movzxw(dst, src);
  } else {
    xorl(dst, dst);
    off = offset();
    movw(dst, src);
  }
  return off;
}


int MacroAssembler::load_signed_byte(Register dst, Address src) {
  int off;
  if (VM_Version::is_P6()) {
    off = offset();
    movsxb(dst, src);
  } else {
    off = load_unsigned_byte(dst, src);
    shll(dst, 24);
    sarl(dst, 24);
  }
  return off;
}


int MacroAssembler::load_signed_word(Register dst, Address src) {
  int off;
  if (VM_Version::is_P6()) {
    off = offset();
    movsxw(dst, src);
  } else {
    off = load_unsigned_word(dst, src);
    shll(dst, 16);
    sarl(dst, 16);
  }
  return off;
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


void MacroAssembler::increment(Register reg, int value) {
  if (value == min_jint) {addl(reg, value); return; }
  if (value <  0) { decrement(reg, -value); return; }
  if (value == 0) {                       ; return; }
  if (value == 1 && UseIncDec) { incl(reg); return; }
  /* else */      { addl(reg, value)      ; return; }
}

void MacroAssembler::increment(Address dst, int value) {
  if (value == min_jint) {addl(dst, value); return; }
  if (value <  0) { decrement(dst, -value); return; }
  if (value == 0) {                       ; return; }
  if (value == 1 && UseIncDec) { incl(dst); return; }
  /* else */      { addl(dst, value)      ; return; }
}

void MacroAssembler::decrement(Register reg, int value) {
  if (value == min_jint) {subl(reg, value); return; }
  if (value <  0) { increment(reg, -value); return; }
  if (value == 0) {                       ; return; }
  if (value == 1 && UseIncDec) { decl(reg); return; }
  /* else */      { subl(reg, value)      ; return; }
}

void MacroAssembler::decrement(Address dst, int value) {
  if (value == min_jint) {subl(dst, value); return; }
  if (value <  0) { increment(dst, -value); return; }
  if (value == 0) {                       ; return; }
  if (value == 1 && UseIncDec) { decl(dst); return; }
  /* else */      { subl(dst, value)      ; return; }
}

void MacroAssembler::align(int modulus) {
  if (offset() % modulus != 0) nop(modulus - (offset() % modulus));
}


void MacroAssembler::enter() {
  pushl(rbp);
  movl(rbp, rsp);
}


void MacroAssembler::leave() {
  movl(rsp, rbp);
  popl(rbp);
}

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
    movl(Address(java_thread, JavaThread::last_Java_fp_offset()), last_java_fp);
  }

  // last_java_pc is optional

  if (last_java_pc != NULL) {
    lea(Address(java_thread,
                 JavaThread::frame_anchor_offset() + JavaFrameAnchor::last_Java_pc_offset()),
        InternalAddress(last_java_pc));

  }
  movl(Address(java_thread, JavaThread::last_Java_sp_offset()), last_java_sp);
}

void MacroAssembler::reset_last_Java_frame(Register java_thread, bool clear_fp, bool clear_pc) {
  // determine java_thread register
  if (!java_thread->is_valid()) {
    java_thread = rdi;
    get_thread(java_thread);
  }
  // we must set sp to zero to clear frame
  movl(Address(java_thread, JavaThread::last_Java_sp_offset()), 0);
  if (clear_fp) {
    movl(Address(java_thread, JavaThread::last_Java_fp_offset()), 0);
  }

  if (clear_pc)
    movl(Address(java_thread, JavaThread::last_Java_pc_offset()), 0);

}



// Implementation of call_VM versions

void MacroAssembler::call_VM_leaf_base(
  address entry_point,
  int     number_of_arguments
) {
  call(RuntimeAddress(entry_point));
  increment(rsp, number_of_arguments * wordSize);
}


void MacroAssembler::call_VM_base(
  Register oop_result,
  Register java_thread,
  Register last_java_sp,
  address  entry_point,
  int      number_of_arguments,
  bool     check_exceptions
) {
  // determine java_thread register
  if (!java_thread->is_valid()) {
    java_thread = rdi;
    get_thread(java_thread);
  }
  // determine last_java_sp register
  if (!last_java_sp->is_valid()) {
    last_java_sp = rsp;
  }
  // debugging support
  assert(number_of_arguments >= 0   , "cannot have negative number of arguments");
  assert(java_thread != oop_result  , "cannot use the same register for java_thread & oop_result");
  assert(java_thread != last_java_sp, "cannot use the same register for java_thread & last_java_sp");
  // push java thread (becomes first argument of C function)
  pushl(java_thread);
  // set last Java frame before call
  assert(last_java_sp != rbp, "this code doesn't work for last_java_sp == rbp, which currently can't portably work anyway since C2 doesn't save rbp,");
  // Only interpreter should have to set fp
  set_last_Java_frame(java_thread, last_java_sp, rbp, NULL);
  // do the call
  call(RuntimeAddress(entry_point));
  // restore the thread (cannot use the pushed argument since arguments
  // may be overwritten by C code generated by an optimizing compiler);
  // however can use the register value directly if it is callee saved.
  if (java_thread == rdi || java_thread == rsi) {
    // rdi & rsi are callee saved -> nothing to do
#ifdef ASSERT
    guarantee(java_thread != rax, "change this code");
    pushl(rax);
    { Label L;
      get_thread(rax);
      cmpl(java_thread, rax);
      jcc(Assembler::equal, L);
      stop("MacroAssembler::call_VM_base: rdi not callee saved?");
      bind(L);
    }
    popl(rax);
#endif
  } else {
    get_thread(java_thread);
  }
  // reset last Java frame
  // Only interpreter should have to clear fp
  reset_last_Java_frame(java_thread, true, false);
  // discard thread and arguments
  addl(rsp, (1 + number_of_arguments)*wordSize);

#ifndef CC_INTERP
   // C++ interp handles this in the interpreter
  check_and_handle_popframe(java_thread);
  check_and_handle_earlyret(java_thread);
#endif /* CC_INTERP */

  if (check_exceptions) {
    // check for pending exceptions (java_thread is set upon return)
    cmpl(Address(java_thread, Thread::pending_exception_offset()), NULL_WORD);
    jump_cc(Assembler::notEqual,
            RuntimeAddress(StubRoutines::forward_exception_entry()));
  }

  // get oop result if there is one and reset the value in the thread
  if (oop_result->is_valid()) {
    movl(oop_result, Address(java_thread, JavaThread::vm_result_offset()));
    movl(Address(java_thread, JavaThread::vm_result_offset()), NULL_WORD);
    verify_oop(oop_result);
  }
}


void MacroAssembler::check_and_handle_popframe(Register java_thread) {
}

void MacroAssembler::check_and_handle_earlyret(Register java_thread) {
}

void MacroAssembler::call_VM_helper(Register oop_result, address entry_point, int number_of_arguments, bool check_exceptions) {
  leal(rax, Address(rsp, (1 + number_of_arguments) * wordSize));
  call_VM_base(oop_result, noreg, rax, entry_point, number_of_arguments, check_exceptions);
}


void MacroAssembler::call_VM(Register oop_result, address entry_point, bool check_exceptions) {
  Label C, E;
  call(C, relocInfo::none);
  jmp(E);

  bind(C);
  call_VM_helper(oop_result, entry_point, 0, check_exceptions);
  ret(0);

  bind(E);
}


void MacroAssembler::call_VM(Register oop_result, address entry_point, Register arg_1, bool check_exceptions) {
  Label C, E;
  call(C, relocInfo::none);
  jmp(E);

  bind(C);
  pushl(arg_1);
  call_VM_helper(oop_result, entry_point, 1, check_exceptions);
  ret(0);

  bind(E);
}


void MacroAssembler::call_VM(Register oop_result, address entry_point, Register arg_1, Register arg_2, bool check_exceptions) {
  Label C, E;
  call(C, relocInfo::none);
  jmp(E);

  bind(C);
  pushl(arg_2);
  pushl(arg_1);
  call_VM_helper(oop_result, entry_point, 2, check_exceptions);
  ret(0);

  bind(E);
}


void MacroAssembler::call_VM(Register oop_result, address entry_point, Register arg_1, Register arg_2, Register arg_3, bool check_exceptions) {
  Label C, E;
  call(C, relocInfo::none);
  jmp(E);

  bind(C);
  pushl(arg_3);
  pushl(arg_2);
  pushl(arg_1);
  call_VM_helper(oop_result, entry_point, 3, check_exceptions);
  ret(0);

  bind(E);
}


void MacroAssembler::call_VM(Register oop_result, Register last_java_sp, address entry_point, int number_of_arguments, bool check_exceptions) {
  call_VM_base(oop_result, noreg, last_java_sp, entry_point, number_of_arguments, check_exceptions);
}


void MacroAssembler::call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, bool check_exceptions) {
  pushl(arg_1);
  call_VM(oop_result, last_java_sp, entry_point, 1, check_exceptions);
}


void MacroAssembler::call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, Register arg_2, bool check_exceptions) {
  pushl(arg_2);
  pushl(arg_1);
  call_VM(oop_result, last_java_sp, entry_point, 2, check_exceptions);
}


void MacroAssembler::call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, Register arg_2, Register arg_3, bool check_exceptions) {
  pushl(arg_3);
  pushl(arg_2);
  pushl(arg_1);
  call_VM(oop_result, last_java_sp, entry_point, 3, check_exceptions);
}


void MacroAssembler::call_VM_leaf(address entry_point, int number_of_arguments) {
  call_VM_leaf_base(entry_point, number_of_arguments);
}


void MacroAssembler::call_VM_leaf(address entry_point, Register arg_1) {
  pushl(arg_1);
  call_VM_leaf(entry_point, 1);
}


void MacroAssembler::call_VM_leaf(address entry_point, Register arg_1, Register arg_2) {
  pushl(arg_2);
  pushl(arg_1);
  call_VM_leaf(entry_point, 2);
}


void MacroAssembler::call_VM_leaf(address entry_point, Register arg_1, Register arg_2, Register arg_3) {
  pushl(arg_3);
  pushl(arg_2);
  pushl(arg_1);
  call_VM_leaf(entry_point, 3);
}

// Calls to C land
//
// When entering C land, the rbp, & rsp of the last Java frame have to be recorded
// in the (thread-local) JavaThread object. When leaving C land, the last Java fp
// has to be reset to 0. This is required to allow proper stack traversal.

//////////////////////////////////////////////////////////////////////////////////
#ifndef SERIALGC

void MacroAssembler::g1_write_barrier_pre(Register obj,
                                          Register thread,
                                          Register tmp,
                                          Register tmp2,
                                          bool tosca_live) {
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
  cmpl(Address(obj, 0), NULL_WORD);
  jcc(Assembler::equal, done);

  // Can we store original value in the thread's buffer?

  movl(tmp2, Address(obj, 0));
  cmpl(index, 0);
  jcc(Assembler::equal, runtime);
  subl(index, wordSize);
  movl(tmp, buffer);
  addl(tmp, index);
  movl(Address(tmp, 0), tmp2);
  jmp(done);
  bind(runtime);
  // save the live input values
  if(tosca_live) pushl(rax);
  pushl(obj);
  pushl(thread);
  call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_pre), tmp2, thread);
  popl(thread);
  popl(obj);
  if(tosca_live) popl(rax);
  bind(done);

}

void MacroAssembler::g1_write_barrier_post(Register store_addr,
                                           Register new_val,
                                           Register thread,
                                           Register tmp,
                                           Register tmp2) {

  Address queue_index(thread, in_bytes(JavaThread::dirty_card_queue_offset() +
                                       PtrQueue::byte_offset_of_index()));
  Address buffer(thread, in_bytes(JavaThread::dirty_card_queue_offset() +
                                       PtrQueue::byte_offset_of_buf()));
  BarrierSet* bs = Universe::heap()->barrier_set();
  CardTableModRefBS* ct = (CardTableModRefBS*)bs;
  Label done;
  Label runtime;

  // Does store cross heap regions?

  movl(tmp, store_addr); // ebx = edx
  xorl(tmp, new_val);    // ebx ^= eax
  shrl(tmp, HeapRegion::LogOfHRGrainBytes); // ebx <<= 9
  jcc(Assembler::equal, done);

  // crosses regions, storing NULL?

  cmpl(new_val, NULL_WORD);
  jcc(Assembler::equal, done);

  // storing region crossing non-NULL, is card already dirty?

  const Register card_index = tmp;

  movl(card_index, store_addr);       // ebx = edx
  shrl(card_index, CardTableModRefBS::card_shift); // ebx >>= 9
  assert(sizeof(*ct->byte_map_base) == sizeof(jbyte), "adjust this code");

  ExternalAddress cardtable((address)ct->byte_map_base);
  Address index(noreg, card_index, Address::times_1);
  const Register card_addr = tmp;
  leal(card_addr, as_Address(ArrayAddress(cardtable, index)));
  cmpb(Address(card_addr, 0), 0);
  jcc(Assembler::equal, done);

  // storing a region crossing, non-NULL oop, card is clean.
  // dirty card and log.

  movb(Address(card_addr, 0), 0);

  cmpl(queue_index, 0);
  jcc(Assembler::equal, runtime);
  subl(queue_index, wordSize);
  movl(tmp2, buffer);
  addl(tmp2, queue_index);
  movl(Address(tmp2, 0), card_index);
  jmp(done);

  bind(runtime);
  // save the live input values
  pushl(store_addr);
  pushl(new_val);
  pushl(thread);
  call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_post), card_addr, thread);
  popl(thread);
  popl(new_val);
  popl(store_addr);

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
  shrl(obj, CardTableModRefBS::card_shift);
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
  Address cardtable(noreg, obj, Address::times_1, disp);
  movb(cardtable, 0);
}


void MacroAssembler::c2bool(Register x) {
  // implements x == 0 ? 0 : 1
  // note: must only look at least-significant byte of x
  //       since C-style booleans are stored in one byte
  //       only! (was bug)
  andl(x, 0xFF);
  setb(Assembler::notZero, x);
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


void MacroAssembler::lneg(Register hi, Register lo) {
  negl(lo);
  adcl(hi, 0);
  negl(hi);
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
  decrement(x_hi);

  bind(done);
}


void MacroAssembler::save_rax(Register tmp) {
  if (tmp == noreg) pushl(rax);
  else if (tmp != rax) movl(tmp, rax);
}


void MacroAssembler::restore_rax(Register tmp) {
  if (tmp == noreg) popl(rax);
  else if (tmp != rax) movl(rax, tmp);
}


void MacroAssembler::fremr(Register tmp) {
  save_rax(tmp);
  { Label L;
    bind(L);
    fprem();
    fwait(); fnstsw_ax();
    sahf();
    jcc(Assembler::parity, L);
  }
  restore_rax(tmp);
  // Result is in ST0.
  // Note: fxch & fpop to get rid of ST1
  // (otherwise FPU stack could overflow eventually)
  fxch(1);
  fpop();
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
    pushl(tmp);
  }

  Label slow_case, done;

  // x ?<= pi/4
  fld_d(ExternalAddress((address)&pi_4));
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

  // slow case: runtime call
  bind(slow_case);
  // Preserve registers across runtime call
  pushad();
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
      subl(rsp, wordSize*2);
      fstp_d(Address(rsp, 0));
    }
    incoming_argument_and_return_value_offset = 2*wordSize*(num_fpu_regs_in_use-1);
    fld_d(Address(rsp, incoming_argument_and_return_value_offset));
  }
  subl(rsp, wordSize*2);
  fstp_d(Address(rsp, 0));
  // NOTE: we must not use call_VM_leaf here because that requires a
  // complete interpreter frame in debug mode -- same bug as 4387334
  NEEDS_CLEANUP;
  // Need to add stack banging before this runtime call if it needs to
  // be taken; however, there is no generic stack banging routine at
  // the MacroAssembler level
  switch(trig) {
  case 's':
    {
      call(RuntimeAddress(CAST_FROM_FN_PTR(address, SharedRuntime::dsin)));
    }
    break;
  case 'c':
    {
      call(RuntimeAddress(CAST_FROM_FN_PTR(address, SharedRuntime::dcos)));
    }
    break;
  case 't':
    {
      call(RuntimeAddress(CAST_FROM_FN_PTR(address, SharedRuntime::dtan)));
    }
    break;
  default:
    assert(false, "bad intrinsic");
    break;
  }
  addl(rsp, wordSize * 2);
  if (num_fpu_regs_in_use > 1) {
    // Must save return value to stack and then restore entire FPU stack
    fstp_d(Address(rsp, incoming_argument_and_return_value_offset));
    for (int i = 0; i < num_fpu_regs_in_use; i++) {
      fld_d(Address(rsp, 0));
      addl(rsp, wordSize*2);
    }
  }
  popad();

  // Come here with result in F-TOS
  bind(done);

  if (tmp != noreg) {
    popl(tmp);
  }
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
    decrement(dst);
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
    decrement(dst);
  }
  bind(L);
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
    decrement(dst);
  }
  bind(L);
}



void MacroAssembler::fpop() {
  ffree();
  fincstp();
}


void MacroAssembler::sign_extend_short(Register reg) {
  if (VM_Version::is_P6()) {
    movsxw(reg, reg);
  } else {
    shll(reg, 16);
    sarl(reg, 16);
  }
}


void MacroAssembler::sign_extend_byte(Register reg) {
  if (VM_Version::is_P6() && reg->has_byte_register()) {
    movsxb(reg, reg);
  } else {
    shll(reg, 24);
    sarl(reg, 24);
  }
}


void MacroAssembler::division_with_shift (Register reg, int shift_value) {
  assert (shift_value > 0, "illegal shift value");
  Label _is_positive;
  testl (reg, reg);
  jcc (Assembler::positive, _is_positive);
  int offset = (1 << shift_value) - 1 ;

  increment(reg, offset);

  bind (_is_positive);
  sarl(reg, shift_value);
}


void MacroAssembler::round_to(Register reg, int modulus) {
  addl(reg, modulus - 1);
  andl(reg, -modulus);
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

void MacroAssembler::testbool(Register dst) {
  if(sizeof(bool) == 1)
    testb(dst, (int) 0xff);
  else if(sizeof(bool) == 2) {
    // testw implementation needed for two byte bools
    ShouldNotReachHere();
  } else if(sizeof(bool) == 4)
    testl(dst, dst);
  else
    // unsupported
    ShouldNotReachHere();
}

void MacroAssembler::verify_oop(Register reg, const char* s) {
  if (!VerifyOops) return;
  // Pass register number to verify_oop_subroutine
  char* b = new char[strlen(s) + 50];
  sprintf(b, "verify_oop: %s: %s", reg->name(), s);
  pushl(rax);                          // save rax,
  pushl(reg);                          // pass register argument
  ExternalAddress buffer((address) b);
  pushptr(buffer.addr());
  // call indirectly to solve generation ordering problem
  movptr(rax, ExternalAddress(StubRoutines::verify_oop_subroutine_entry_address()));
  call(rax);
}


void MacroAssembler::verify_oop_addr(Address addr, const char* s) {
  if (!VerifyOops) return;
  // QQQ fix this
  // Address adjust(addr.base(), addr.index(), addr.scale(), addr.disp() + BytesPerWord);
  // Pass register number to verify_oop_subroutine
  char* b = new char[strlen(s) + 50];
  sprintf(b, "verify_oop_addr: %s", s);
  pushl(rax);                          // save rax,
  // addr may contain rsp so we will have to adjust it based on the push
  // we just did
  if (addr.uses(rsp)) {
    leal(rax, addr);
    pushl(Address(rax, BytesPerWord));
  } else {
    pushl(addr);
  }
  ExternalAddress buffer((address) b);
  // pass msg argument
  pushptr(buffer.addr());
  // call indirectly to solve generation ordering problem
  movptr(rax, ExternalAddress(StubRoutines::verify_oop_subroutine_entry_address()));
  call(rax);
  // Caller pops the arguments and restores rax, from the stack
}


void MacroAssembler::stop(const char* msg) {
  ExternalAddress message((address)msg);
  // push address of message
  pushptr(message.addr());
  { Label L; call(L, relocInfo::none); bind(L); }     // push eip
  pushad();                                           // push registers
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, MacroAssembler::debug)));
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


void MacroAssembler::debug(int rdi, int rsi, int rbp, int rsp, int rbx, int rdx, int rcx, int rax, int eip, char* msg) {
  // In order to get locks to work, we need to fake a in_VM state
  JavaThread* thread = JavaThread::current();
  JavaThreadState saved_state = thread->thread_state();
  thread->set_thread_state(_thread_in_vm);
  if (ShowMessageBoxOnError) {
    JavaThread* thread = JavaThread::current();
    JavaThreadState saved_state = thread->thread_state();
    thread->set_thread_state(_thread_in_vm);
    ttyLocker ttyl;
    if (CountBytecodes || TraceBytecodes || StopInterpreterAt) {
      BytecodeCounter::print();
    }
    // To see where a verify_oop failed, get $ebx+40/X for this frame.
    // This is the value of eip which points to where verify_oop will return.
    if (os::message_box(msg, "Execution stopped, print registers?")) {
      tty->print_cr("eip = 0x%08x", eip);
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
    ::tty->print_cr("=============== DEBUG MESSAGE: %s ================\n", msg);
    assert(false, "DEBUG MESSAGE");
  }
  ThreadStateTransition::transition(thread, _thread_in_vm, saved_state);
}



void MacroAssembler::os_breakpoint() {
  // instead of directly emitting a breakpoint, call os:breakpoint for better debugability
  // (e.g., MSVC can't call ps() otherwise)
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, os::breakpoint)));
}


void MacroAssembler::push_fTOS() {
  subl(rsp, 2 * wordSize);
  fstp_d(Address(rsp, 0));
}


void MacroAssembler::pop_fTOS() {
  fld_d(Address(rsp, 0));
  addl(rsp, 2 * wordSize);
}


void MacroAssembler::empty_FPU_stack() {
  if (VM_Version::supports_mmx()) {
    emms();
  } else {
    for (int i = 8; i-- > 0; ) ffree(i);
  }
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
    ShouldNotReachHere()
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
  pushl(rsp);                // pass CPU state
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, _print_CPU_state)));
  addl(rsp, wordSize);       // discard argument
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
  pushl(rsp);                // pass CPU state
  ExternalAddress msg((address) s);
  // pass message string s
  pushptr(msg.addr());
  pushl(stack_depth);        // pass stack depth
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, _verify_FPU)));
  addl(rsp, 3 * wordSize);   // discard arguments
  // check for error
  { Label L;
    testl(rax, rax);
    jcc(Assembler::notZero, L);
    int3();                  // break if error condition
    bind(L);
  }
  pop_CPU_state();
}


void MacroAssembler::push_IU_state() {
  pushad();
  pushfd();
}


void MacroAssembler::pop_IU_state() {
  popfd();
  popad();
}


void MacroAssembler::push_FPU_state() {
  subl(rsp, FPUStateSizeInWords * wordSize);
  fnsave(Address(rsp, 0));
  fwait();
}


void MacroAssembler::pop_FPU_state() {
  frstor(Address(rsp, 0));
  addl(rsp, FPUStateSizeInWords * wordSize);
}


void MacroAssembler::push_CPU_state() {
  push_IU_state();
  push_FPU_state();
}


void MacroAssembler::pop_CPU_state() {
  pop_FPU_state();
  pop_IU_state();
}


void MacroAssembler::push_callee_saved_registers() {
  pushl(rsi);
  pushl(rdi);
  pushl(rdx);
  pushl(rcx);
}


void MacroAssembler::pop_callee_saved_registers() {
  popl(rcx);
  popl(rdx);
  popl(rdi);
  popl(rsi);
}


void MacroAssembler::set_word_if_not_zero(Register dst) {
  xorl(dst, dst);
  set_byte_if_not_zero(dst);
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

  movptr(ArrayAddress(page, index), tmp);
}


void MacroAssembler::verify_tlab() {
#ifdef ASSERT
  if (UseTLAB && VerifyOops) {
    Label next, ok;
    Register t1 = rsi;
    Register thread_reg = rbx;

    pushl(t1);
    pushl(thread_reg);
    get_thread(thread_reg);

    movl(t1, Address(thread_reg, in_bytes(JavaThread::tlab_top_offset())));
    cmpl(t1, Address(thread_reg, in_bytes(JavaThread::tlab_start_offset())));
    jcc(Assembler::aboveEqual, next);
    stop("assert(top >= start)");
    should_not_reach_here();

    bind(next);
    movl(t1, Address(thread_reg, in_bytes(JavaThread::tlab_end_offset())));
    cmpl(t1, Address(thread_reg, in_bytes(JavaThread::tlab_top_offset())));
    jcc(Assembler::aboveEqual, ok);
    stop("assert(top <= end)");
    should_not_reach_here();

    bind(ok);
    popl(thread_reg);
    popl(t1);
  }
#endif
}


// Defines obj, preserves var_size_in_bytes
void MacroAssembler::eden_allocate(Register obj, Register var_size_in_bytes, int con_size_in_bytes,
                                   Register t1, Label& slow_case) {
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
      leal(end, Address(obj, con_size_in_bytes));
    } else {
      leal(end, Address(obj, var_size_in_bytes, Address::times_1));
    }
    // if end < obj then we wrapped around => object too long => slow case
    cmpl(end, obj);
    jcc(Assembler::below, slow_case);
    cmpptr(end, ExternalAddress((address) Universe::heap()->end_addr()));
    jcc(Assembler::above, slow_case);
    // Compare obj with the top addr, and if still equal, store the new top addr in
    // end at the address of the top addr pointer. Sets ZF if was equal, and clears
    // it otherwise. Use lock prefix for atomicity on MPs.
    if (os::is_MP()) {
      lock();
    }
    cmpxchgptr(end, heap_top);
    jcc(Assembler::notEqual, retry);
  }
}


// Defines obj, preserves var_size_in_bytes, okay for t2 == var_size_in_bytes.
void MacroAssembler::tlab_allocate(Register obj, Register var_size_in_bytes, int con_size_in_bytes,
                                   Register t1, Register t2, Label& slow_case) {
  assert_different_registers(obj, t1, t2);
  assert_different_registers(obj, var_size_in_bytes, t1);
  Register end = t2;
  Register thread = t1;

  verify_tlab();

  get_thread(thread);

  movl(obj, Address(thread, JavaThread::tlab_top_offset()));
  if (var_size_in_bytes == noreg) {
    leal(end, Address(obj, con_size_in_bytes));
  } else {
    leal(end, Address(obj, var_size_in_bytes, Address::times_1));
  }
  cmpl(end, Address(thread, JavaThread::tlab_end_offset()));
  jcc(Assembler::above, slow_case);

  // update the tlab top pointer
  movl(Address(thread, JavaThread::tlab_top_offset()), end);

  // recover var_size_in_bytes if necessary
  if (var_size_in_bytes == end) {
    subl(var_size_in_bytes, obj);
  }
  verify_tlab();
}

// Preserves rbx, and rdx.
void MacroAssembler::tlab_refill(Label& retry, Label& try_eden, Label& slow_case) {
  Register top = rax;
  Register t1  = rcx;
  Register t2  = rsi;
  Register thread_reg = rdi;
  assert_different_registers(top, thread_reg, t1, t2, /* preserve: */ rbx, rdx);
  Label do_refill, discard_tlab;

  if (CMSIncrementalMode || !Universe::heap()->supports_inline_contig_alloc()) {
    // No allocation in the shared eden.
    jmp(slow_case);
  }

  get_thread(thread_reg);

  movl(top, Address(thread_reg, in_bytes(JavaThread::tlab_top_offset())));
  movl(t1,  Address(thread_reg, in_bytes(JavaThread::tlab_end_offset())));

  // calculate amount of free space
  subl(t1, top);
  shrl(t1, LogHeapWordSize);

  // Retain tlab and allocate object in shared space if
  // the amount free in the tlab is too large to discard.
  cmpl(t1, Address(thread_reg, in_bytes(JavaThread::tlab_refill_waste_limit_offset())));
  jcc(Assembler::lessEqual, discard_tlab);

  // Retain
  movl(t2, ThreadLocalAllocBuffer::refill_waste_limit_increment());
  addl(Address(thread_reg, in_bytes(JavaThread::tlab_refill_waste_limit_offset())), t2);
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
  testl (top, top);
  jcc(Assembler::zero, do_refill);

  // set up the mark word
  movl(Address(top, oopDesc::mark_offset_in_bytes()), (int)markOopDesc::prototype()->copy_set_hash(0x2));
  // set the length to the remaining space
  subl(t1, typeArrayOopDesc::header_size(T_INT));
  addl(t1, ThreadLocalAllocBuffer::alignment_reserve());
  shll(t1, log2_intptr(HeapWordSize/sizeof(jint)));
  movl(Address(top, arrayOopDesc::length_offset_in_bytes()), t1);
  // set klass to intArrayKlass
  // dubious reloc why not an oop reloc?
  movptr(t1, ExternalAddress((address) Universe::intArrayKlassObj_addr()));
  movl(Address(top, oopDesc::klass_offset_in_bytes()), t1);

  // refill the tlab with an eden allocation
  bind(do_refill);
  movl(t1, Address(thread_reg, in_bytes(JavaThread::tlab_size_offset())));
  shll(t1, LogHeapWordSize);
  // add object_size ??
  eden_allocate(top, t1, 0, t2, slow_case);

  // Check that t1 was preserved in eden_allocate.
#ifdef ASSERT
  if (UseTLAB) {
    Label ok;
    Register tsize = rsi;
    assert_different_registers(tsize, thread_reg, t1);
    pushl(tsize);
    movl(tsize, Address(thread_reg, in_bytes(JavaThread::tlab_size_offset())));
    shll(tsize, LogHeapWordSize);
    cmpl(t1, tsize);
    jcc(Assembler::equal, ok);
    stop("assert(t1 != tlab size)");
    should_not_reach_here();

    bind(ok);
    popl(tsize);
  }
#endif
  movl(Address(thread_reg, in_bytes(JavaThread::tlab_start_offset())), top);
  movl(Address(thread_reg, in_bytes(JavaThread::tlab_top_offset())), top);
  addl(top, t1);
  subl(top, ThreadLocalAllocBuffer::alignment_reserve_in_bytes());
  movl(Address(thread_reg, in_bytes(JavaThread::tlab_end_offset())), top);
  verify_tlab();
  jmp(retry);
}


int MacroAssembler::biased_locking_enter(Register lock_reg, Register obj_reg, Register swap_reg, Register tmp_reg,
                                         bool swap_reg_contains_mark,
                                         Label& done, Label* slow_case,
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
    pushl(tmp_reg);
  }
  movl(tmp_reg, swap_reg);
  andl(tmp_reg, markOopDesc::biased_lock_mask_in_place);
  cmpl(tmp_reg, markOopDesc::biased_lock_pattern);
  if (need_tmp_reg) {
    popl(tmp_reg);
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
    pushl(tmp_reg);
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
    popl(tmp_reg);
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
    pushl(tmp_reg);
  }
  get_thread(tmp_reg);
  orl(tmp_reg, swap_reg);
  if (os::is_MP()) {
    lock();
  }
  cmpxchg(tmp_reg, Address(obj_reg, 0));
  if (need_tmp_reg) {
    popl(tmp_reg);
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
    pushl(tmp_reg);
  }
  get_thread(tmp_reg);
  movl(swap_reg, klass_addr);
  orl(tmp_reg, Address(swap_reg, Klass::prototype_header_offset_in_bytes() + klassOopDesc::klass_part_offset_in_bytes()));
  movl(swap_reg, saved_mark_addr);
  if (os::is_MP()) {
    lock();
  }
  cmpxchg(tmp_reg, Address(obj_reg, 0));
  if (need_tmp_reg) {
    popl(tmp_reg);
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
    pushl(tmp_reg);
  }
  movl(tmp_reg, klass_addr);
  movl(tmp_reg, Address(tmp_reg, Klass::prototype_header_offset_in_bytes() + klassOopDesc::klass_part_offset_in_bytes()));
  if (os::is_MP()) {
    lock();
  }
  cmpxchg(tmp_reg, Address(obj_reg, 0));
  if (need_tmp_reg) {
    popl(tmp_reg);
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


void MacroAssembler::biased_locking_exit(Register obj_reg, Register temp_reg, Label& done) {
  assert(UseBiasedLocking, "why call this otherwise?");

  // Check for biased locking unlock case, which is a no-op
  // Note: we do not have to check the thread ID for two reasons.
  // First, the interpreter checks for IllegalMonitorStateException at
  // a higher level. Second, if the bias was revoked while we held the
  // lock, the object could not be rebiased toward another thread, so
  // the bias bit would be clear.
  movl(temp_reg, Address(obj_reg, oopDesc::mark_offset_in_bytes()));
  andl(temp_reg, markOopDesc::biased_lock_mask_in_place);
  cmpl(temp_reg, markOopDesc::biased_lock_pattern);
  jcc(Assembler::equal, done);
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


void MacroAssembler::cond_inc32(Condition cond, AddressLiteral counter_addr) {
  Condition negated_cond = negate_condition(cond);
  Label L;
  jcc(negated_cond, L);
  atomic_incl(counter_addr);
  bind(L);
}

void MacroAssembler::atomic_incl(AddressLiteral counter_addr) {
  pushfd();
  if (os::is_MP())
    lock();
  increment(counter_addr);
  popfd();
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


// Writes to stack successive pages until offset reached to check for
// stack overflow + shadow pages.  This clobbers tmp.
void MacroAssembler::bang_stack_size(Register size, Register tmp) {
  movl(tmp, rsp);
  // Bang stack for total size given plus shadow page size.
  // Bang one page at a time because large size can bang beyond yellow and
  // red zones.
  Label loop;
  bind(loop);
  movl(Address(tmp, (-os::vm_page_size())), size );
  subl(tmp, os::vm_page_size());
  subl(size, os::vm_page_size());
  jcc(Assembler::greater, loop);

  // Bang down shadow pages too.
  // The -1 because we already subtracted 1 page.
  for (int i = 0; i< StackShadowPages-1; i++) {
    movl(Address(tmp, (-i*os::vm_page_size())), size );
  }
}
