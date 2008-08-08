/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_assembler_x86_64.cpp.incl"

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
  return Address();
#else
  AddressLiteral base = adr.base();
  Address index = adr.index();
  assert(index._disp == 0, "must not have disp"); // maybe it can?
  Address array(index._base, index._index, index._scale, (intptr_t) base.target());
  array._rspec = base._rspec;
  return array;
#endif // _LP64
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
    case relocInfo::none:
      break;
    default:
      ShouldNotReachHere();
  }
}

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
  if (adr.reloc() != relocInfo::external_word_type &&
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


// make this go away eventually
void Assembler::emit_data(jint data,
                          relocInfo::relocType rtype,
                          int format) {
  if (rtype == relocInfo::none) {
    emit_long(data);
  } else {
    emit_data(data, Relocation::spec_simple(rtype), format);
  }
}

void Assembler::emit_data(jint data,
                          RelocationHolder const& rspec,
                          int format) {
  assert(imm64_operand == 0, "default format must be imm64 in this file");
  assert(imm64_operand != format, "must not be imm64");
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
  assert(imm64_operand == 0, "default format must be imm64 in this file");
  assert(imm64_operand == format, "must be imm64");
  assert(inst_mark() != NULL, "must be inside InstructionMark");
  // Do not use AbstractAssembler::relocate, which is not intended for
  // embedded words.  Instead, relocate to the enclosing instruction.
  code_section()->relocate(inst_mark(), rspec, format);
#ifdef ASSERT
  check_relocation(rspec, format);
#endif
  emit_long64(data);
}

void Assembler::emit_arith_b(int op1, int op2, Register dst, int imm8) {
  assert(isByte(op1) && isByte(op2), "wrong opcode");
  assert(isByte(imm8), "not a byte");
  assert((op1 & 0x01) == 0, "should be 8bit operation");
  int dstenc = dst->encoding();
  if (dstenc >= 8) {
    dstenc -= 8;
  }
  emit_byte(op1);
  emit_byte(op2 | dstenc);
  emit_byte(imm8);
}

void Assembler::emit_arith(int op1, int op2, Register dst, int imm32) {
  assert(isByte(op1) && isByte(op2), "wrong opcode");
  assert((op1 & 0x01) == 1, "should be 32bit operation");
  assert((op1 & 0x02) == 0, "sign-extension bit should not be set");
  int dstenc = dst->encoding();
  if (dstenc >= 8) {
    dstenc -= 8;
  }
  if (is8bit(imm32)) {
    emit_byte(op1 | 0x02); // set sign bit
    emit_byte(op2 | dstenc);
    emit_byte(imm32 & 0xFF);
  } else {
    emit_byte(op1);
    emit_byte(op2 | dstenc);
    emit_long(imm32);
  }
}

// immediate-to-memory forms
void Assembler::emit_arith_operand(int op1,
                                   Register rm, Address adr,
                                   int imm32) {
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


void Assembler::emit_arith(int op1, int op2, Register dst, Register src) {
  assert(isByte(op1) && isByte(op2), "wrong opcode");
  int dstenc = dst->encoding();
  int srcenc = src->encoding();
  if (dstenc >= 8) {
    dstenc -= 8;
  }
  if (srcenc >= 8) {
    srcenc -= 8;
  }
  emit_byte(op1);
  emit_byte(op2 | dstenc << 3 | srcenc);
}

void Assembler::emit_operand(Register reg, Register base, Register index,
                             Address::ScaleFactor scale, int disp,
                             RelocationHolder const& rspec,
                             int rip_relative_correction) {
  relocInfo::relocType rtype = (relocInfo::relocType) rspec.type();
  int regenc = reg->encoding();
  if (regenc >= 8) {
    regenc -= 8;
  }
  if (base->is_valid()) {
    if (index->is_valid()) {
      assert(scale != Address::no_scale, "inconsistent address");
      int indexenc = index->encoding();
      if (indexenc >= 8) {
        indexenc -= 8;
      }
      int baseenc = base->encoding();
      if (baseenc >= 8) {
        baseenc -= 8;
      }
      // [base + index*scale + disp]
      if (disp == 0 && rtype == relocInfo::none  &&
          base != rbp && base != r13) {
        // [base + index*scale]
        // [00 reg 100][ss index base]
        assert(index != rsp, "illegal addressing mode");
        emit_byte(0x04 | regenc << 3);
        emit_byte(scale << 6 | indexenc << 3 | baseenc);
      } else if (is8bit(disp) && rtype == relocInfo::none) {
        // [base + index*scale + imm8]
        // [01 reg 100][ss index base] imm8
        assert(index != rsp, "illegal addressing mode");
        emit_byte(0x44 | regenc << 3);
        emit_byte(scale << 6 | indexenc << 3 | baseenc);
        emit_byte(disp & 0xFF);
      } else {
        // [base + index*scale + disp32]
        // [10 reg 100][ss index base] disp32
        assert(index != rsp, "illegal addressing mode");
        emit_byte(0x84 | regenc << 3);
        emit_byte(scale << 6 | indexenc << 3 | baseenc);
        emit_data(disp, rspec, disp32_operand);
      }
    } else if (base == rsp || base == r12) {
      // [rsp + disp]
      if (disp == 0 && rtype == relocInfo::none) {
        // [rsp]
        // [00 reg 100][00 100 100]
        emit_byte(0x04 | regenc << 3);
        emit_byte(0x24);
      } else if (is8bit(disp) && rtype == relocInfo::none) {
        // [rsp + imm8]
        // [01 reg 100][00 100 100] disp8
        emit_byte(0x44 | regenc << 3);
        emit_byte(0x24);
        emit_byte(disp & 0xFF);
      } else {
        // [rsp + imm32]
        // [10 reg 100][00 100 100] disp32
        emit_byte(0x84 | regenc << 3);
        emit_byte(0x24);
        emit_data(disp, rspec, disp32_operand);
      }
    } else {
      // [base + disp]
      assert(base != rsp && base != r12, "illegal addressing mode");
      int baseenc = base->encoding();
      if (baseenc >= 8) {
        baseenc -= 8;
      }
      if (disp == 0 && rtype == relocInfo::none &&
          base != rbp && base != r13) {
        // [base]
        // [00 reg base]
        emit_byte(0x00 | regenc << 3 | baseenc);
      } else if (is8bit(disp) && rtype == relocInfo::none) {
        // [base + disp8]
        // [01 reg base] disp8
        emit_byte(0x40 | regenc << 3 | baseenc);
        emit_byte(disp & 0xFF);
      } else {
        // [base + disp32]
        // [10 reg base] disp32
        emit_byte(0x80 | regenc << 3 | baseenc);
        emit_data(disp, rspec, disp32_operand);
      }
    }
  } else {
    if (index->is_valid()) {
      assert(scale != Address::no_scale, "inconsistent address");
      int indexenc = index->encoding();
      if (indexenc >= 8) {
        indexenc -= 8;
      }
      // [index*scale + disp]
      // [00 reg 100][ss index 101] disp32
      assert(index != rsp, "illegal addressing mode");
      emit_byte(0x04 | regenc << 3);
      emit_byte(scale << 6 | indexenc << 3 | 0x05);
      emit_data(disp, rspec, disp32_operand);
#ifdef _LP64
    } else if (rtype != relocInfo::none ) {
      // [disp] RIP-RELATIVE
      // [00 000 101] disp32

      emit_byte(0x05 | regenc << 3);
      // Note that the RIP-rel. correction applies to the generated
      // disp field, but _not_ to the target address in the rspec.

      // disp was created by converting the target address minus the pc
      // at the start of the instruction. That needs more correction here.
      // intptr_t disp = target - next_ip;
      assert(inst_mark() != NULL, "must be inside InstructionMark");
      address next_ip = pc() + sizeof(int32_t) + rip_relative_correction;
      int64_t adjusted = (int64_t) disp -  (next_ip - inst_mark());
      assert(is_simm32(adjusted),
             "must be 32bit offset (RIP relative address)");
      emit_data((int) adjusted, rspec, disp32_operand);

#endif // _LP64
    } else {
      // [disp] ABSOLUTE
      // [00 reg 100][00 100 101] disp32
      emit_byte(0x04 | regenc << 3);
      emit_byte(0x25);
      emit_data(disp, rspec, disp32_operand);
    }
  }
}

void Assembler::emit_operand(XMMRegister reg, Register base, Register index,
                             Address::ScaleFactor scale, int disp,
                             RelocationHolder const& rspec,
                             int rip_relative_correction) {
  relocInfo::relocType rtype = (relocInfo::relocType) rspec.type();
  int regenc = reg->encoding();
  if (regenc >= 8) {
    regenc -= 8;
  }
  if (base->is_valid()) {
    if (index->is_valid()) {
      assert(scale != Address::no_scale, "inconsistent address");
      int indexenc = index->encoding();
      if (indexenc >= 8) {
        indexenc -= 8;
      }
      int baseenc = base->encoding();
      if (baseenc >= 8) {
        baseenc -= 8;
      }
      // [base + index*scale + disp]
      if (disp == 0 && rtype == relocInfo::none  &&
          base != rbp && base != r13) {
        // [base + index*scale]
        // [00 reg 100][ss index base]
        assert(index != rsp, "illegal addressing mode");
        emit_byte(0x04 | regenc << 3);
        emit_byte(scale << 6 | indexenc << 3 | baseenc);
      } else if (is8bit(disp) && rtype == relocInfo::none) {
        // [base + index*scale + disp8]
        // [01 reg 100][ss index base] disp8
        assert(index != rsp, "illegal addressing mode");
        emit_byte(0x44 | regenc << 3);
        emit_byte(scale << 6 | indexenc << 3 | baseenc);
        emit_byte(disp & 0xFF);
      } else {
        // [base + index*scale + disp32]
        // [10 reg 100][ss index base] disp32
        assert(index != rsp, "illegal addressing mode");
        emit_byte(0x84 | regenc << 3);
        emit_byte(scale << 6 | indexenc << 3 | baseenc);
        emit_data(disp, rspec, disp32_operand);
      }
    } else if (base == rsp || base == r12) {
      // [rsp + disp]
      if (disp == 0 && rtype == relocInfo::none) {
        // [rsp]
        // [00 reg 100][00 100 100]
        emit_byte(0x04 | regenc << 3);
        emit_byte(0x24);
      } else if (is8bit(disp) && rtype == relocInfo::none) {
        // [rsp + imm8]
        // [01 reg 100][00 100 100] disp8
        emit_byte(0x44 | regenc << 3);
        emit_byte(0x24);
        emit_byte(disp & 0xFF);
      } else {
        // [rsp + imm32]
        // [10 reg 100][00 100 100] disp32
        emit_byte(0x84 | regenc << 3);
        emit_byte(0x24);
        emit_data(disp, rspec, disp32_operand);
      }
    } else {
      // [base + disp]
      assert(base != rsp && base != r12, "illegal addressing mode");
      int baseenc = base->encoding();
      if (baseenc >= 8) {
        baseenc -= 8;
      }
      if (disp == 0 && rtype == relocInfo::none &&
          base != rbp && base != r13) {
        // [base]
        // [00 reg base]
        emit_byte(0x00 | regenc << 3 | baseenc);
      } else if (is8bit(disp) && rtype == relocInfo::none) {
        // [base + imm8]
        // [01 reg base] disp8
        emit_byte(0x40 | regenc << 3 | baseenc);
        emit_byte(disp & 0xFF);
      } else {
        // [base + imm32]
        // [10 reg base] disp32
        emit_byte(0x80 | regenc << 3 | baseenc);
        emit_data(disp, rspec, disp32_operand);
      }
    }
  } else {
    if (index->is_valid()) {
      assert(scale != Address::no_scale, "inconsistent address");
      int indexenc = index->encoding();
      if (indexenc >= 8) {
        indexenc -= 8;
      }
      // [index*scale + disp]
      // [00 reg 100][ss index 101] disp32
      assert(index != rsp, "illegal addressing mode");
      emit_byte(0x04 | regenc << 3);
      emit_byte(scale << 6 | indexenc << 3 | 0x05);
      emit_data(disp, rspec, disp32_operand);
#ifdef _LP64
    } else if ( rtype != relocInfo::none ) {
      // [disp] RIP-RELATIVE
      // [00 reg 101] disp32
      emit_byte(0x05 | regenc << 3);
      // Note that the RIP-rel. correction applies to the generated
      // disp field, but _not_ to the target address in the rspec.

      // disp was created by converting the target address minus the pc
      // at the start of the instruction. That needs more correction here.
      // intptr_t disp = target - next_ip;

      assert(inst_mark() != NULL, "must be inside InstructionMark");
      address next_ip = pc() + sizeof(int32_t) + rip_relative_correction;

      int64_t adjusted = (int64_t) disp -  (next_ip - inst_mark());
      assert(is_simm32(adjusted),
             "must be 32bit offset (RIP relative address)");
      emit_data((int) adjusted, rspec, disp32_operand);
#endif // _LP64
    } else {
      // [disp] ABSOLUTE
      // [00 reg 100][00 100 101] disp32
      emit_byte(0x04 | regenc << 3);
      emit_byte(0x25);
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
    assert(0, "shouldn't have that prefix");
    assert(ip == inst + 1 || ip == inst + 2, "only two prefixes allowed");
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
//     assert(ip == inst + 1, "only one prefix allowed");
    goto again_after_prefix;

  case REX_W:
  case REX_WB:
  case REX_WX:
  case REX_WXB:
  case REX_WR:
  case REX_WRB:
  case REX_WRX:
  case REX_WRXB:
    is_64bit = true;
//     assert(ip == inst + 1, "only one prefix allowed");
    goto again_after_prefix;

  case 0xFF: // pushq a; decl a; incl a; call a; jmp a
  case 0x88: // movb a, r
  case 0x89: // movl a, r
  case 0x8A: // movb r, a
  case 0x8B: // movl r, a
  case 0x8F: // popl a
    debug_only(has_disp32 = true;)
    break;

  case 0x68: // pushq #32
    if (which == end_pc_operand) {
      return ip + 4;
    }
    assert(0, "pushq has no disp32 or imm64");
    ShouldNotReachHere();

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
      goto again_after_size_prefix2;
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

  case REP8(0xB8): // movl/q r, #32/#64(oop?)
    if (which == end_pc_operand)  return ip + (is_64bit ? 8 : 4);
    assert((which == call32_operand || which == imm64_operand) && is_64bit ||
           which == narrow_oop_operand && !is_64bit, "");
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
    case 0x57: // xorps
    case 0x6E: // movd
    case 0x7E: // movd
    case 0xAE: // ldmxcsr   a
      debug_only(has_disp32 = true); // has both kinds of operands!
      break;
    case 0xAD: // shrd r, a, %cl
    case 0xAF: // imul r, a
    case 0xBE: // movsbl r, a
    case 0xBF: // movswl r, a
    case 0xB6: // movzbl r, a
    case 0xB7: // movzwl r, a
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
      assert(which == call32_operand, "jcc has no disp32 or imm64");
      return ip;
    default:
      ShouldNotReachHere();
    }
    break;

  case 0x81: // addl a, #32; addl r, #32
    // also: orl, adcl, sbbl, andl, subl, xorl, cmpl
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
  case 0x87: // xchg r, a
    debug_only(has_disp32 = true);
    break;
  case REP4(0x38): // cmp...
  case 0x8D: // lea r, a
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
    debug_only(has_disp32 = true);
    break;

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
  assert(which != imm64_operand, "instruction is not a movq reg, imm64");
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

  assert(0, "fix locate_operand");
  return ip;
}

address Assembler::locate_next_instruction(address inst) {
  // Secretly share code with locate_operand:
  return locate_operand(inst, end_pc_operand);
}

#ifdef ASSERT
void Assembler::check_relocation(RelocationHolder const& rspec, int format) {
  address inst = inst_mark();
  assert(inst != NULL && inst < pc(),
         "must point to beginning of instruction");
  address opnd;

  Relocation* r = rspec.reloc();
  if (r->type() == relocInfo::none) {
    return;
  } else if (r->is_call() || format == call32_operand) {
    opnd = locate_operand(inst, call32_operand);
  } else if (r->is_data()) {
    assert(format == imm64_operand || format == disp32_operand ||
           format == narrow_oop_operand, "format ok");
    opnd = locate_operand(inst, (WhichOperand) format);
  } else {
    assert(format == 0, "cannot specify a format");
    return;
  }
  assert(opnd == pc(), "must put operand where relocs can find it");
}
#endif

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

void Assembler::emit_operand(Register reg, Address adr,
                             int rip_relative_correction) {
  emit_operand(reg, adr._base, adr._index, adr._scale, adr._disp,
               adr._rspec,
               rip_relative_correction);
}

void Assembler::emit_operand(XMMRegister reg, Address adr,
                             int rip_relative_correction) {
  emit_operand(reg, adr._base, adr._index, adr._scale, adr._disp,
               adr._rspec,
               rip_relative_correction);
}

void Assembler::emit_farith(int b1, int b2, int i) {
  assert(isByte(b1) && isByte(b2), "wrong opcode");
  assert(0 <= i &&  i < 8, "illegal stack offset");
  emit_byte(b1);
  emit_byte(b2 + i);
}

// pushad is invalid, use this instead.
// NOTE: Kills flags!!
void Assembler::pushaq() {
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

// popad is invalid, use this instead
// NOTE: Kills flags!!
void Assembler::popaq() {
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

void Assembler::pushfq() {
  emit_byte(0x9C);
}

void Assembler::popfq() {
  emit_byte(0x9D);
}

void Assembler::pushq(int imm32) {
  emit_byte(0x68);
  emit_long(imm32);
}

void Assembler::pushq(Register src) {
  int encode = prefix_and_encode(src->encoding());

  emit_byte(0x50 | encode);
}

void Assembler::pushq(Address src) {
  InstructionMark im(this);
  prefix(src);
  emit_byte(0xFF);
  emit_operand(rsi, src);
}

void Assembler::popq(Register dst) {
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0x58 | encode);
}

void Assembler::popq(Address dst) {
  InstructionMark im(this);
  prefix(dst);
  emit_byte(0x8F);
  emit_operand(rax, dst);
}

void Assembler::prefix(Prefix p) {
  a_byte(p);
}

void Assembler::movb(Register dst, Address src) {
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
  InstructionMark im(this);
  prefix(dst, src, true);
  emit_byte(0x88);
  emit_operand(src, dst);
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

// Uses zero extension.
void Assembler::movl(Register dst, int imm32) {
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

void Assembler::movl(Address dst, int imm32) {
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

void Assembler::mov64(Register dst, intptr_t imm64) {
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

void Assembler::mov64(Address dst, intptr_t imm32) {
  assert(is_simm32(imm32), "lost bits");
  InstructionMark im(this);
  prefixq(dst);
  emit_byte(0xC7);
  emit_operand(rax, dst, 4);
  emit_long(imm32);
}

void Assembler::movq(Address dst, Register src) {
  InstructionMark im(this);
  prefixq(dst, src);
  emit_byte(0x89);
  emit_operand(src, dst);
}

void Assembler::movsbl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0xBE);
  emit_operand(dst, src);
}

void Assembler::movsbl(Register dst, Register src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding(), true);
  emit_byte(0x0F);
  emit_byte(0xBE);
  emit_byte(0xC0 | encode);
}

void Assembler::movswl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0xBF);
  emit_operand(dst, src);
}

void Assembler::movswl(Register dst, Register src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xBF);
  emit_byte(0xC0 | encode);
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

void Assembler::movzbl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0xB6);
  emit_operand(dst, src);
}

void Assembler::movzbl(Register dst, Register src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding(), true);
  emit_byte(0x0F);
  emit_byte(0xB6);
  emit_byte(0xC0 | encode);
}

void Assembler::movzwl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0xB7);
  emit_operand(dst, src);
}

void Assembler::movzwl(Register dst, Register src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xB7);
  emit_byte(0xC0 | encode);
}

void Assembler::movss(XMMRegister dst, XMMRegister src) {
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x10);
  emit_byte(0xC0 | encode);
}

void Assembler::movss(XMMRegister dst, Address src) {
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x10);
  emit_operand(dst, src);
}

void Assembler::movss(Address dst, XMMRegister src) {
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(dst, src);
  emit_byte(0x0F);
  emit_byte(0x11);
  emit_operand(src, dst);
}

void Assembler::movsd(XMMRegister dst, XMMRegister src) {
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x10);
  emit_byte(0xC0 | encode);
}

void Assembler::movsd(XMMRegister dst, Address src) {
  InstructionMark im(this);
  emit_byte(0xF2);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x10);
  emit_operand(dst, src);
}

void Assembler::movsd(Address dst, XMMRegister src) {
  InstructionMark im(this);
  emit_byte(0xF2);
  prefix(dst, src);
  emit_byte(0x0F);
  emit_byte(0x11);
  emit_operand(src, dst);
}

// New cpus require to use movsd and movss to avoid partial register stall
// when loading from memory. But for old Opteron use movlpd instead of movsd.
// The selection is done in MacroAssembler::movdbl() and movflt().
void Assembler::movlpd(XMMRegister dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x66);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x12);
  emit_operand(dst, src);
}

void Assembler::movapd(XMMRegister dst, XMMRegister src) {
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

void Assembler::movdl(XMMRegister dst, Register src) {
  emit_byte(0x66);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x6E);
  emit_byte(0xC0 | encode);
}

void Assembler::movdl(Register dst, XMMRegister src) {
  emit_byte(0x66);
  // swap src/dst to get correct prefix
  int encode = prefix_and_encode(src->encoding(), dst->encoding());
  emit_byte(0x0F);
  emit_byte(0x7E);
  emit_byte(0xC0 | encode);
}

void Assembler::movdq(XMMRegister dst, Register src) {
  emit_byte(0x66);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x6E);
  emit_byte(0xC0 | encode);
}

void Assembler::movdq(Register dst, XMMRegister src) {
  emit_byte(0x66);
  // swap src/dst to get correct prefix
  int encode = prefixq_and_encode(src->encoding(), dst->encoding());
  emit_byte(0x0F);
  emit_byte(0x7E);
  emit_byte(0xC0 | encode);
}

void Assembler::pxor(XMMRegister dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x66);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0xEF);
  emit_operand(dst, src);
}

void Assembler::pxor(XMMRegister dst, XMMRegister src) {
  InstructionMark im(this);
  emit_byte(0x66);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xEF);
  emit_byte(0xC0 | encode);
}

void Assembler::movdqa(XMMRegister dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x66);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x6F);
  emit_operand(dst, src);
}

void Assembler::movdqa(XMMRegister dst, XMMRegister src) {
  emit_byte(0x66);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x6F);
  emit_byte(0xC0 | encode);
}

void Assembler::movdqa(Address dst, XMMRegister src) {
  InstructionMark im(this);
  emit_byte(0x66);
  prefix(dst, src);
  emit_byte(0x0F);
  emit_byte(0x7F);
  emit_operand(src, dst);
}

void Assembler::movq(XMMRegister dst, Address src) {
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x7E);
  emit_operand(dst, src);
}

void Assembler::movq(Address dst, XMMRegister src) {
  InstructionMark im(this);
  emit_byte(0x66);
  prefix(dst, src);
  emit_byte(0x0F);
  emit_byte(0xD6);
  emit_operand(src, dst);
}

void Assembler::pshufd(XMMRegister dst, XMMRegister src, int mode) {
  assert(isByte(mode), "invalid value");
  emit_byte(0x66);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x70);
  emit_byte(0xC0 | encode);
  emit_byte(mode & 0xFF);
}

void Assembler::pshufd(XMMRegister dst, Address src, int mode) {
  assert(isByte(mode), "invalid value");
  InstructionMark im(this);
  emit_byte(0x66);
  emit_byte(0x0F);
  emit_byte(0x70);
  emit_operand(dst, src);
  emit_byte(mode & 0xFF);
}

void Assembler::pshuflw(XMMRegister dst, XMMRegister src, int mode) {
  assert(isByte(mode), "invalid value");
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x70);
  emit_byte(0xC0 | encode);
  emit_byte(mode & 0xFF);
}

void Assembler::pshuflw(XMMRegister dst, Address src, int mode) {
  assert(isByte(mode), "invalid value");
  InstructionMark im(this);
  emit_byte(0xF2);
  emit_byte(0x0F);
  emit_byte(0x70);
  emit_operand(dst, src);
  emit_byte(mode & 0xFF);
}

void Assembler::cmovl(Condition cc, Register dst, Register src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x40 | cc);
  emit_byte(0xC0 | encode);
}

void Assembler::cmovl(Condition cc, Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x40 | cc);
  emit_operand(dst, src);
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

void Assembler::prefetch_prefix(Address src) {
  prefix(src);
  emit_byte(0x0F);
}

void Assembler::prefetcht0(Address src) {
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_byte(0x18);
  emit_operand(rcx, src); // 1, src
}

void Assembler::prefetcht1(Address src) {
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_byte(0x18);
  emit_operand(rdx, src); // 2, src
}

void Assembler::prefetcht2(Address src) {
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_byte(0x18);
  emit_operand(rbx, src); // 3, src
}

void Assembler::prefetchnta(Address src) {
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_byte(0x18);
  emit_operand(rax, src); // 0, src
}

void Assembler::prefetchw(Address src) {
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_byte(0x0D);
  emit_operand(rcx, src); // 1, src
}

void Assembler::adcl(Register dst, int imm32) {
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

void Assembler::adcq(Register dst, int imm32) {
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

void Assembler::addl(Address dst, int imm32) {
  InstructionMark im(this);
  prefix(dst);
  emit_arith_operand(0x81, rax, dst,imm32);
}

void Assembler::addl(Address dst, Register src) {
  InstructionMark im(this);
  prefix(dst, src);
  emit_byte(0x01);
  emit_operand(src, dst);
}

void Assembler::addl(Register dst, int imm32) {
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

void Assembler::addq(Address dst, int imm32) {
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

void Assembler::addq(Register dst, int imm32) {
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

void Assembler::andl(Register dst, int imm32) {
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

void Assembler::andq(Register dst, int imm32) {
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

void Assembler::cmpb(Address dst, int imm8) {
  InstructionMark im(this);
  prefix(dst);
  emit_byte(0x80);
  emit_operand(rdi, dst, 1);
  emit_byte(imm8);
}

void Assembler::cmpl(Address dst, int imm32) {
  InstructionMark im(this);
  prefix(dst);
  emit_byte(0x81);
  emit_operand(rdi, dst, 4);
  emit_long(imm32);
}

void Assembler::cmpl(Register dst, int imm32) {
  prefix(dst);
  emit_arith(0x81, 0xF8, dst, imm32);
}

void Assembler::cmpl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x3B, 0xC0, dst, src);
}

void Assembler::cmpl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x3B);
  emit_operand(dst, src);
}

void Assembler::cmpq(Address dst, int imm32) {
  InstructionMark im(this);
  prefixq(dst);
  emit_byte(0x81);
  emit_operand(rdi, dst, 4);
  emit_long(imm32);
}

void Assembler::cmpq(Register dst, int imm32) {
  (void) prefixq_and_encode(dst->encoding());
  emit_arith(0x81, 0xF8, dst, imm32);
}

void Assembler::cmpq(Address dst, Register src) {
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

void Assembler::ucomiss(XMMRegister dst, XMMRegister src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2E);
  emit_byte(0xC0 | encode);
}

void Assembler::ucomisd(XMMRegister dst, XMMRegister src) {
  emit_byte(0x66);
  ucomiss(dst, src);
}

void Assembler::decl(Register dst) {
  // Don't use it directly. Use MacroAssembler::decrementl() instead.
  // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0xFF);
  emit_byte(0xC8 | encode);
}

void Assembler::decl(Address dst) {
  // Don't use it directly. Use MacroAssembler::decrementl() instead.
  InstructionMark im(this);
  prefix(dst);
  emit_byte(0xFF);
  emit_operand(rcx, dst);
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

void Assembler::idivl(Register src) {
  int encode = prefix_and_encode(src->encoding());
  emit_byte(0xF7);
  emit_byte(0xF8 | encode);
}

void Assembler::idivq(Register src) {
  int encode = prefixq_and_encode(src->encoding());
  emit_byte(0xF7);
  emit_byte(0xF8 | encode);
}

void Assembler::cdql() {
  emit_byte(0x99);
}

void Assembler::cdqq() {
  prefix(REX_W);
  emit_byte(0x99);
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

void Assembler::incl(Address dst) {
  // Don't use it directly. Use MacroAssembler::incrementl() instead.
  InstructionMark im(this);
  prefix(dst);
  emit_byte(0xFF);
  emit_operand(rax, dst);
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

void Assembler::leal(Register dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x67); // addr32
  prefix(src, dst);
  emit_byte(0x8D);
  emit_operand(dst, src);
}

void Assembler::leaq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_byte(0x8D);
  emit_operand(dst, src);
}

void Assembler::mull(Address src) {
  InstructionMark im(this);
  // was missing
  prefix(src);
  emit_byte(0xF7);
  emit_operand(rsp, src);
}

void Assembler::mull(Register src) {
  // was missing
  int encode = prefix_and_encode(src->encoding());
  emit_byte(0xF7);
  emit_byte(0xE0 | encode);
}

void Assembler::negl(Register dst) {
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0xF7);
  emit_byte(0xD8 | encode);
}

void Assembler::negq(Register dst) {
  int encode = prefixq_and_encode(dst->encoding());
  emit_byte(0xF7);
  emit_byte(0xD8 | encode);
}

void Assembler::notl(Register dst) {
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0xF7);
  emit_byte(0xD0 | encode);
}

void Assembler::notq(Register dst) {
  int encode = prefixq_and_encode(dst->encoding());
  emit_byte(0xF7);
  emit_byte(0xD0 | encode);
}

void Assembler::orl(Address dst, int imm32) {
  InstructionMark im(this);
  prefix(dst);
  emit_byte(0x81);
  emit_operand(rcx, dst, 4);
  emit_long(imm32);
}

void Assembler::orl(Register dst, int imm32) {
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

void Assembler::orq(Address dst, int imm32) {
  InstructionMark im(this);
  prefixq(dst);
  emit_byte(0x81);
  emit_operand(rcx, dst, 4);
  emit_long(imm32);
}

void Assembler::orq(Register dst, int imm32) {
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

void Assembler::sbbl(Address dst, int imm32) {
  InstructionMark im(this);
  prefix(dst);
  emit_arith_operand(0x81, rbx, dst, imm32);
}

void Assembler::sbbl(Register dst, int imm32) {
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

void Assembler::sbbq(Address dst, int imm32) {
  InstructionMark im(this);
  prefixq(dst);
  emit_arith_operand(0x81, rbx, dst, imm32);
}

void Assembler::sbbq(Register dst, int imm32) {
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

void Assembler::subl(Address dst, int imm32) {
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

void Assembler::subl(Register dst, int imm32) {
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

void Assembler::subq(Address dst, int imm32) {
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

void Assembler::subq(Register dst, int imm32) {
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

void Assembler::testb(Register dst, int imm8) {
  (void) prefix_and_encode(dst->encoding(), true);
  emit_arith_b(0xF6, 0xC0, dst, imm8);
}

void Assembler::testl(Register dst, int imm32) {
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

void Assembler::testq(Register dst, int imm32) {
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

void Assembler::xaddl(Address dst, Register src) {
  InstructionMark im(this);
  prefix(dst, src);
  emit_byte(0x0F);
  emit_byte(0xC1);
  emit_operand(src, dst);
}

void Assembler::xaddq(Address dst, Register src) {
  InstructionMark im(this);
  prefixq(dst, src);
  emit_byte(0x0F);
  emit_byte(0xC1);
  emit_operand(src, dst);
}

void Assembler::xorl(Register dst, int imm32) {
  prefix(dst);
  emit_arith(0x81, 0xF0, dst, imm32);
}

void Assembler::xorl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x33, 0xC0, dst, src);
}

void Assembler::xorl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x33);
  emit_operand(dst, src);
}

void Assembler::xorq(Register dst, int imm32) {
  (void) prefixq_and_encode(dst->encoding());
  emit_arith(0x81, 0xF0, dst, imm32);
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

void Assembler::bswapl(Register reg) {
  int encode = prefix_and_encode(reg->encoding());
  emit_byte(0x0F);
  emit_byte(0xC8 | encode);
}

void Assembler::bswapq(Register reg) {
  int encode = prefixq_and_encode(reg->encoding());
  emit_byte(0x0F);
  emit_byte(0xC8 | encode);
}

void Assembler::lock() {
  emit_byte(0xF0);
}

void Assembler::xchgl(Register dst, Address src) {
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

void Assembler::cmpxchgl(Register reg, Address adr) {
  InstructionMark im(this);
  prefix(adr, reg);
  emit_byte(0x0F);
  emit_byte(0xB1);
  emit_operand(reg, adr);
}

void Assembler::cmpxchgq(Register reg, Address adr) {
  InstructionMark im(this);
  prefixq(adr, reg);
  emit_byte(0x0F);
  emit_byte(0xB1);
  emit_operand(reg, adr);
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

// copies a single word from [esi] to [edi]
void Assembler::smovl() {
  emit_byte(0xA5);
}

// copies data from [rsi] to [rdi] using rcx words (m32)
void Assembler::rep_movl() {
  // REP
  emit_byte(0xF3);
  // MOVSL
  emit_byte(0xA5);
}

// copies data from [rsi] to [rdi] using rcx double words (m64)
void Assembler::rep_movq() {
  // REP
  emit_byte(0xF3);
  // MOVSQ
  prefix(REX_W);
  emit_byte(0xA5);
}

// sets rcx double words (m64) with rax value at [rdi]
void Assembler::rep_set() {
  // REP
  emit_byte(0xF3);
  // STOSQ
  prefix(REX_W);
  emit_byte(0xAB);
}

// scans rcx double words (m64) at [rdi] for occurance of rax
void Assembler::repne_scanq() {
  // REPNE/REPNZ
  emit_byte(0xF2);
  // SCASQ
  prefix(REX_W);
  emit_byte(0xAF);
}

void Assembler::repne_scanl() {
  // REPNE/REPNZ
  emit_byte(0xF2);
  // SCASL
  emit_byte(0xAF);
}


void Assembler::setb(Condition cc, Register dst) {
  assert(0 <= cc && cc < 16, "illegal cc");
  int encode = prefix_and_encode(dst->encoding(), true);
  emit_byte(0x0F);
  emit_byte(0x90 | cc);
  emit_byte(0xC0 | encode);
}

void Assembler::clflush(Address adr) {
  prefix(adr);
  emit_byte(0x0F);
  emit_byte(0xAE);
  emit_operand(rdi, adr);
}

void Assembler::call(Label& L, relocInfo::relocType rtype) {
  if (L.is_bound()) {
    const int long_size = 5;
    int offs = (int)( target(L) - pc() );
    assert(offs <= 0, "assembler error");
    InstructionMark im(this);
    // 1110 1000 #32-bit disp
    emit_byte(0xE8);
    emit_data(offs - long_size, rtype, disp32_operand);
  } else {
    InstructionMark im(this);
    // 1110 1000 #32-bit disp
    L.add_patch_at(code(), locator());

    emit_byte(0xE8);
    emit_data(int(0), rtype, disp32_operand);
  }
}

void Assembler::call_literal(address entry, RelocationHolder const& rspec) {
  assert(entry != NULL, "call most probably wrong");
  InstructionMark im(this);
  emit_byte(0xE8);
  intptr_t disp = entry - (_code_pos + sizeof(int32_t));
  assert(is_simm32(disp), "must be 32bit offset (call2)");
  // Technically, should use call32_operand, but this format is
  // implied by the fact that we're emitting a call instruction.
  emit_data((int) disp, rspec, disp32_operand);
}


void Assembler::call(Register dst) {
  // This was originally using a 32bit register encoding
  // and surely we want 64bit!
  // this is a 32bit encoding but in 64bit mode the default
  // operand size is 64bit so there is no need for the
  // wide prefix. So prefix only happens if we use the
  // new registers. Much like push/pop.
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

void Assembler::jmp(Register reg) {
  int encode = prefix_and_encode(reg->encoding());
  emit_byte(0xFF);
  emit_byte(0xE0 | encode);
}

void Assembler::jmp(Address adr) {
  InstructionMark im(this);
  prefix(adr);
  emit_byte(0xFF);
  emit_operand(rsp, adr);
}

void Assembler::jmp_literal(address dest, RelocationHolder const& rspec) {
  InstructionMark im(this);
  emit_byte(0xE9);
  assert(dest != NULL, "must have a target");
  intptr_t disp = dest - (_code_pos + sizeof(int32_t));
  assert(is_simm32(disp), "must be 32bit offset (jmp)");
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
    const int long_size = 6;
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

// FP instructions

void Assembler::fxsave(Address dst) {
  prefixq(dst);
  emit_byte(0x0F);
  emit_byte(0xAE);
  emit_operand(as_Register(0), dst);
}

void Assembler::fxrstor(Address src) {
  prefixq(src);
  emit_byte(0x0F);
  emit_byte(0xAE);
  emit_operand(as_Register(1), src);
}

void Assembler::ldmxcsr(Address src) {
  InstructionMark im(this);
  prefix(src);
  emit_byte(0x0F);
  emit_byte(0xAE);
  emit_operand(as_Register(2), src);
}

void Assembler::stmxcsr(Address dst) {
  InstructionMark im(this);
  prefix(dst);
  emit_byte(0x0F);
  emit_byte(0xAE);
  emit_operand(as_Register(3), dst);
}

void Assembler::addss(XMMRegister dst, XMMRegister src) {
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x58);
  emit_byte(0xC0 | encode);
}

void Assembler::addss(XMMRegister dst, Address src) {
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x58);
  emit_operand(dst, src);
}

void Assembler::subss(XMMRegister dst, XMMRegister src) {
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x5C);
  emit_byte(0xC0 | encode);
}

void Assembler::subss(XMMRegister dst, Address src) {
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x5C);
  emit_operand(dst, src);
}

void Assembler::mulss(XMMRegister dst, XMMRegister src) {
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x59);
  emit_byte(0xC0 | encode);
}

void Assembler::mulss(XMMRegister dst, Address src) {
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x59);
  emit_operand(dst, src);
}

void Assembler::divss(XMMRegister dst, XMMRegister src) {
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x5E);
  emit_byte(0xC0 | encode);
}

void Assembler::divss(XMMRegister dst, Address src) {
  InstructionMark im(this);
  emit_byte(0xF3);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x5E);
  emit_operand(dst, src);
}

void Assembler::addsd(XMMRegister dst, XMMRegister src) {
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x58);
  emit_byte(0xC0 | encode);
}

void Assembler::addsd(XMMRegister dst, Address src) {
  InstructionMark im(this);
  emit_byte(0xF2);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x58);
  emit_operand(dst, src);
}

void Assembler::subsd(XMMRegister dst, XMMRegister src) {
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x5C);
  emit_byte(0xC0 | encode);
}

void Assembler::subsd(XMMRegister dst, Address src) {
  InstructionMark im(this);
  emit_byte(0xF2);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x5C);
  emit_operand(dst, src);
}

void Assembler::mulsd(XMMRegister dst, XMMRegister src) {
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x59);
  emit_byte(0xC0 | encode);
}

void Assembler::mulsd(XMMRegister dst, Address src) {
  InstructionMark im(this);
  emit_byte(0xF2);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x59);
  emit_operand(dst, src);
}

void Assembler::divsd(XMMRegister dst, XMMRegister src) {
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x5E);
  emit_byte(0xC0 | encode);
}

void Assembler::divsd(XMMRegister dst, Address src) {
  InstructionMark im(this);
  emit_byte(0xF2);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x5E);
  emit_operand(dst, src);
}

void Assembler::sqrtsd(XMMRegister dst, XMMRegister src) {
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x51);
  emit_byte(0xC0 | encode);
}

void Assembler::sqrtsd(XMMRegister dst, Address src) {
  InstructionMark im(this);
  emit_byte(0xF2);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x51);
  emit_operand(dst, src);
}

void Assembler::xorps(XMMRegister dst, XMMRegister src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x57);
  emit_byte(0xC0 | encode);
}

void Assembler::xorps(XMMRegister dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x57);
  emit_operand(dst, src);
}

void Assembler::xorpd(XMMRegister dst, XMMRegister src) {
  emit_byte(0x66);
  xorps(dst, src);
}

void Assembler::xorpd(XMMRegister dst, Address src) {
  InstructionMark im(this);
  emit_byte(0x66);
  prefix(src, dst);
  emit_byte(0x0F);
  emit_byte(0x57);
  emit_operand(dst, src);
}

void Assembler::cvtsi2ssl(XMMRegister dst, Register src) {
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2A);
  emit_byte(0xC0 | encode);
}

void Assembler::cvtsi2ssq(XMMRegister dst, Register src) {
  emit_byte(0xF3);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2A);
  emit_byte(0xC0 | encode);
}

void Assembler::cvtsi2sdl(XMMRegister dst, Register src) {
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2A);
  emit_byte(0xC0 | encode);
}

void Assembler::cvtsi2sdq(XMMRegister dst, Register src) {
  emit_byte(0xF2);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2A);
  emit_byte(0xC0 | encode);
}

void Assembler::cvttss2sil(Register dst, XMMRegister src) {
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2C);
  emit_byte(0xC0 | encode);
}

void Assembler::cvttss2siq(Register dst, XMMRegister src) {
  emit_byte(0xF3);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2C);
  emit_byte(0xC0 | encode);
}

void Assembler::cvttsd2sil(Register dst, XMMRegister src) {
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2C);
  emit_byte(0xC0 | encode);
}

void Assembler::cvttsd2siq(Register dst, XMMRegister src) {
  emit_byte(0xF2);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x2C);
  emit_byte(0xC0 | encode);
}

void Assembler::cvtss2sd(XMMRegister dst, XMMRegister src) {
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x5A);
  emit_byte(0xC0 | encode);
}

void Assembler::cvtdq2pd(XMMRegister dst, XMMRegister src) {
  emit_byte(0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0xE6);
  emit_byte(0xC0 | encode);
}

void Assembler::cvtdq2ps(XMMRegister dst, XMMRegister src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x5B);
  emit_byte(0xC0 | encode);
}

void Assembler::cvtsd2ss(XMMRegister dst, XMMRegister src) {
  emit_byte(0xF2);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x5A);
  emit_byte(0xC0 | encode);
}

void Assembler::punpcklbw(XMMRegister dst, XMMRegister src) {
  emit_byte(0x66);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_byte(0x0F);
  emit_byte(0x60);
  emit_byte(0xC0 | encode);
}

// Implementation of MacroAssembler

// On 32 bit it returns a vanilla displacement on 64 bit is a rip relative displacement
Address MacroAssembler::as_Address(AddressLiteral adr) {
  assert(!adr.is_lval(), "must be rval");
  assert(reachable(adr), "must be");
  return Address((int)(intptr_t)(adr.target() - pc()), adr.target(), adr.reloc());
}

Address MacroAssembler::as_Address(ArrayAddress adr) {
#ifdef _LP64
  AddressLiteral base = adr.base();
  lea(rscratch1, base);
  Address index = adr.index();
  assert(index._disp == 0, "must not have disp"); // maybe it can?
  Address array(rscratch1, index._index, index._scale, index._disp);
  return array;
#else
  return Address::make_array(adr);
#endif // _LP64

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

// 32bit can do a case table jump in one instruction but we no longer allow the base
// to be installed in the Address class
void MacroAssembler::jump(ArrayAddress entry) {
#ifdef _LP64
  lea(rscratch1, entry.base());
  Address dispatch = entry.index();
  assert(dispatch._base == noreg, "must be");
  dispatch._base = rscratch1;
  jmp(dispatch);
#else
  jmp(as_Address(entry));
#endif // _LP64
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

// Wouldn't need if AddressLiteral version had new name
void MacroAssembler::call(Label& L, relocInfo::relocType rtype) {
  Assembler::call(L, rtype);
}

// Wouldn't need if AddressLiteral version had new name
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

void MacroAssembler::cmp8(AddressLiteral src1, int8_t src2) {
  if (reachable(src1)) {
    cmpb(as_Address(src1), src2);
  } else {
    lea(rscratch1, src1);
    cmpb(Address(rscratch1, 0), src2);
  }
}

void MacroAssembler::cmp32(AddressLiteral src1, int32_t src2) {
  if (reachable(src1)) {
    cmpl(as_Address(src1), src2);
  } else {
    lea(rscratch1, src1);
    cmpl(Address(rscratch1, 0), src2);
  }
}

void MacroAssembler::cmp32(Register src1, AddressLiteral src2) {
  if (reachable(src2)) {
    cmpl(src1, as_Address(src2));
  } else {
    lea(rscratch1, src2);
    cmpl(src1, Address(rscratch1, 0));
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

void MacroAssembler::cmp64(Register src1, AddressLiteral src2) {
  assert(!src2.is_lval(), "should use cmpptr");

  if (reachable(src2)) {
#ifdef _LP64
    cmpq(src1, as_Address(src2));
#else
    ShouldNotReachHere();
#endif // _LP64
  } else {
    lea(rscratch1, src2);
    Assembler::cmpq(src1, Address(rscratch1, 0));
  }
}

void MacroAssembler::cmpxchgptr(Register reg, AddressLiteral adr) {
  if (reachable(adr)) {
#ifdef _LP64
    cmpxchgq(reg, as_Address(adr));
#else
    cmpxchgl(reg, as_Address(adr));
#endif // _LP64
  } else {
    lea(rscratch1, adr);
    cmpxchgq(reg, Address(rscratch1, 0));
  }
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

void MacroAssembler::lea(Register dst, Address src) {
#ifdef _LP64
  leaq(dst, src);
#else
  leal(dst, src);
#endif // _LP64
}

void MacroAssembler::lea(Register dst, AddressLiteral src) {
#ifdef _LP64
    mov_literal64(dst, (intptr_t)src.target(), src.rspec());
#else
    mov_literal32(dst, (intptr_t)src.target(), src.rspec());
#endif // _LP64
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

void MacroAssembler::movoop(Register dst, jobject obj) {
  mov_literal64(dst, (intptr_t)obj, oop_Relocation::spec_for_immediate());
}

void MacroAssembler::movoop(Address dst, jobject obj) {
  mov_literal64(rscratch1, (intptr_t)obj, oop_Relocation::spec_for_immediate());
  movq(dst, rscratch1);
}

void MacroAssembler::movptr(Register dst, AddressLiteral src) {
#ifdef _LP64
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
#else
  if (src.is_lval()) {
    mov_literal32(dst, (intptr_t)src.target(), src.rspec());
  } else {
    movl(dst, as_Address(src));
  }
#endif // LP64
}

void MacroAssembler::movptr(ArrayAddress dst, Register src) {
#ifdef _LP64
  movq(as_Address(dst), src);
#else
  movl(as_Address(dst), src);
#endif // _LP64
}

void MacroAssembler::pushoop(jobject obj) {
#ifdef _LP64
  movoop(rscratch1, obj);
  pushq(rscratch1);
#else
  push_literal32((int32_t)obj, oop_Relocation::spec_for_immediate());
#endif // _LP64
}

void MacroAssembler::pushptr(AddressLiteral src) {
#ifdef _LP64
  lea(rscratch1, src);
  if (src.is_lval()) {
    pushq(rscratch1);
  } else {
    pushq(Address(rscratch1, 0));
  }
#else
  if (src.is_lval()) {
    push_literal((int32_t)src.target(), src.rspec());
  else {
    pushl(as_Address(src));
  }
#endif // _LP64
}

void MacroAssembler::ldmxcsr(AddressLiteral src) {
  if (reachable(src)) {
    Assembler::ldmxcsr(as_Address(src));
  } else {
    lea(rscratch1, src);
    Assembler::ldmxcsr(Address(rscratch1, 0));
  }
}

void MacroAssembler::movlpd(XMMRegister dst, AddressLiteral src) {
  if (reachable(src)) {
    movlpd(dst, as_Address(src));
  } else {
    lea(rscratch1, src);
    movlpd(dst, Address(rscratch1, 0));
  }
}

void MacroAssembler::movss(XMMRegister dst, AddressLiteral src) {
  if (reachable(src)) {
    movss(dst, as_Address(src));
  } else {
    lea(rscratch1, src);
    movss(dst, Address(rscratch1, 0));
  }
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

void MacroAssembler::null_check(Register reg, int offset) {
  if (needs_explicit_null_check(offset)) {
    // provoke OS NULL exception if reg = NULL by
    // accessing M[reg] w/o changing any (non-CC) registers
    cmpq(rax, Address(reg, 0));
    // Note: should probably use testl(rax, Address(reg, 0));
    //       may be shorter code (however, this version of
    //       testl needs to be implemented first)
  } else {
    // nothing to do, (later) access of M[reg + offset]
    // will provoke OS NULL exception if reg = NULL
  }
}

int MacroAssembler::load_unsigned_byte(Register dst, Address src) {
  int off = offset();
  movzbl(dst, src);
  return off;
}

int MacroAssembler::load_unsigned_word(Register dst, Address src) {
  int off = offset();
  movzwl(dst, src);
  return off;
}

int MacroAssembler::load_signed_byte(Register dst, Address src) {
  int off = offset();
  movsbl(dst, src);
  return off;
}

int MacroAssembler::load_signed_word(Register dst, Address src) {
  int off = offset();
  movswl(dst, src);
  return off;
}

void MacroAssembler::incrementl(Register reg, int value) {
  if (value == min_jint) { addl(reg, value); return; }
  if (value <  0) { decrementl(reg, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { incl(reg) ; return; }
  /* else */      { addl(reg, value)       ; return; }
}

void MacroAssembler::decrementl(Register reg, int value) {
  if (value == min_jint) { subl(reg, value); return; }
  if (value <  0) { incrementl(reg, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { decl(reg) ; return; }
  /* else */      { subl(reg, value)       ; return; }
}

void MacroAssembler::incrementq(Register reg, int value) {
  if (value == min_jint) { addq(reg, value); return; }
  if (value <  0) { decrementq(reg, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { incq(reg) ; return; }
  /* else */      { addq(reg, value)       ; return; }
}

void MacroAssembler::decrementq(Register reg, int value) {
  if (value == min_jint) { subq(reg, value); return; }
  if (value <  0) { incrementq(reg, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { decq(reg) ; return; }
  /* else */      { subq(reg, value)       ; return; }
}

void MacroAssembler::incrementl(Address dst, int value) {
  if (value == min_jint) { addl(dst, value); return; }
  if (value <  0) { decrementl(dst, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { incl(dst) ; return; }
  /* else */      { addl(dst, value)       ; return; }
}

void MacroAssembler::decrementl(Address dst, int value) {
  if (value == min_jint) { subl(dst, value); return; }
  if (value <  0) { incrementl(dst, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { decl(dst) ; return; }
  /* else */      { subl(dst, value)       ; return; }
}

void MacroAssembler::incrementq(Address dst, int value) {
  if (value == min_jint) { addq(dst, value); return; }
  if (value <  0) { decrementq(dst, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { incq(dst) ; return; }
  /* else */      { addq(dst, value)       ; return; }
}

void MacroAssembler::decrementq(Address dst, int value) {
  if (value == min_jint) { subq(dst, value); return; }
  if (value <  0) { incrementq(dst, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { decq(dst) ; return; }
  /* else */      { subq(dst, value)       ; return; }
}

void MacroAssembler::align(int modulus) {
  if (offset() % modulus != 0) {
    nop(modulus - (offset() % modulus));
  }
}

void MacroAssembler::enter() {
  pushq(rbp);
  movq(rbp, rsp);
}

void MacroAssembler::leave() {
  emit_byte(0xC9); // LEAVE
}

// C++ bool manipulation

void MacroAssembler::movbool(Register dst, Address src) {
  if(sizeof(bool) == 1)
    movb(dst, src);
  else if(sizeof(bool) == 2)
    movw(dst, src);
  else if(sizeof(bool) == 4)
    movl(dst, src);
  else {
    // unsupported
    ShouldNotReachHere();
  }
}

void MacroAssembler::movbool(Address dst, bool boolconst) {
  if(sizeof(bool) == 1)
    movb(dst, (int) boolconst);
  else if(sizeof(bool) == 2)
    movw(dst, (int) boolconst);
  else if(sizeof(bool) == 4)
    movl(dst, (int) boolconst);
  else {
    // unsupported
    ShouldNotReachHere();
  }
}

void MacroAssembler::movbool(Address dst, Register src) {
  if(sizeof(bool) == 1)
    movb(dst, src);
  else if(sizeof(bool) == 2)
    movw(dst, src);
  else if(sizeof(bool) == 4)
    movl(dst, src);
  else {
    // unsupported
    ShouldNotReachHere();
  }
}

void MacroAssembler::testbool(Register dst) {
  if(sizeof(bool) == 1)
    testb(dst, (int) 0xff);
  else if(sizeof(bool) == 2) {
    // need testw impl
    ShouldNotReachHere();
  } else if(sizeof(bool) == 4)
    testl(dst, dst);
  else {
    // unsupported
    ShouldNotReachHere();
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
    movq(Address(r15_thread, JavaThread::last_Java_fp_offset()),
         last_java_fp);
  }

  // last_java_pc is optional
  if (last_java_pc != NULL) {
    Address java_pc(r15_thread,
                    JavaThread::frame_anchor_offset() + JavaFrameAnchor::last_Java_pc_offset());
    lea(rscratch1, InternalAddress(last_java_pc));
    movq(java_pc, rscratch1);
  }

  movq(Address(r15_thread, JavaThread::last_Java_sp_offset()), last_java_sp);
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


// Implementation of call_VM versions

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


void MacroAssembler::call_VM_base(Register oop_result,
                                  Register java_thread,
                                  Register last_java_sp,
                                  address entry_point,
                                  int num_args,
                                  bool check_exceptions) {
  // determine last_java_sp register
  if (!last_java_sp->is_valid()) {
    last_java_sp = rsp;
  }

  // debugging support
  assert(num_args >= 0, "cannot have negative number of arguments");
  assert(r15_thread != oop_result,
         "cannot use the same register for java_thread & oop_result");
  assert(r15_thread != last_java_sp,
         "cannot use the same register for java_thread & last_java_sp");

  // set last Java frame before call

  // This sets last_Java_fp which is only needed from interpreted frames
  // and should really be done only from the interp_masm version before
  // calling the underlying call_VM. That doesn't happen yet so we set
  // last_Java_fp here even though some callers don't need it and
  // also clear it below.
  set_last_Java_frame(last_java_sp, rbp, NULL);

  {
    Label L, E;

    // Align stack if necessary
#ifdef _WIN64
    assert(num_args <= 4, "only register arguments supported");
    // Windows always allocates space for it's register args
    subq(rsp, frame::arg_reg_save_area_bytes);
#endif
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

#ifdef ASSERT
  pushq(rax);
  {
    Label L;
    get_thread(rax);
    cmpq(r15_thread, rax);
    jcc(Assembler::equal, L);
    stop("MacroAssembler::call_VM_base: register not callee saved?");
    bind(L);
  }
  popq(rax);
#endif

  // reset last Java frame
  // This really shouldn't have to clear fp set note above at the
  // call to set_last_Java_frame
  reset_last_Java_frame(true, false);

  check_and_handle_popframe(noreg);
  check_and_handle_earlyret(noreg);

  if (check_exceptions) {
    cmpq(Address(r15_thread, Thread::pending_exception_offset()), (int) NULL);
    // This used to conditionally jump to forward_exception however it is
    // possible if we relocate that the branch will not reach. So we must jump
    // around so we can always reach
    Label ok;
    jcc(Assembler::equal, ok);
    jump(RuntimeAddress(StubRoutines::forward_exception_entry()));
    bind(ok);
  }

  // get oop result if there is one and reset the value in the thread
  if (oop_result->is_valid()) {
    movq(oop_result, Address(r15_thread, JavaThread::vm_result_offset()));
    movptr(Address(r15_thread, JavaThread::vm_result_offset()), NULL_WORD);
    verify_oop(oop_result, "broken oop in call_VM_base");
  }
}

void MacroAssembler::check_and_handle_popframe(Register java_thread) {}
void MacroAssembler::check_and_handle_earlyret(Register java_thread) {}

void MacroAssembler::call_VM_helper(Register oop_result,
                                    address entry_point,
                                    int num_args,
                                    bool check_exceptions) {
  // Java thread becomes first argument of C function
  movq(c_rarg0, r15_thread);

  // We've pushed one address, correct last_Java_sp
  leaq(rax, Address(rsp, wordSize));

  call_VM_base(oop_result, noreg, rax, entry_point, num_args,
               check_exceptions);
}


void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             bool check_exceptions) {
  Label C, E;
  Assembler::call(C, relocInfo::none);
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
  assert(rax != arg_1, "smashed argument");
  assert(c_rarg0 != arg_1, "smashed argument");

  Label C, E;
  Assembler::call(C, relocInfo::none);
  jmp(E);

  bind(C);
  // c_rarg0 is reserved for thread
  if (c_rarg1 != arg_1) {
    movq(c_rarg1, arg_1);
  }
  call_VM_helper(oop_result, entry_point, 1, check_exceptions);
  ret(0);

  bind(E);
}

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             bool check_exceptions) {
  assert(rax != arg_1, "smashed argument");
  assert(rax != arg_2, "smashed argument");
  assert(c_rarg0 != arg_1, "smashed argument");
  assert(c_rarg0 != arg_2, "smashed argument");
  assert(c_rarg1 != arg_2, "smashed argument");
  assert(c_rarg2 != arg_1, "smashed argument");

  Label C, E;
  Assembler::call(C, relocInfo::none);
  jmp(E);

  bind(C);
  // c_rarg0 is reserved for thread
  if (c_rarg1 != arg_1) {
    movq(c_rarg1, arg_1);
  }
  if (c_rarg2 != arg_2) {
    movq(c_rarg2, arg_2);
  }
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
  assert(rax != arg_1, "smashed argument");
  assert(rax != arg_2, "smashed argument");
  assert(rax != arg_3, "smashed argument");
  assert(c_rarg0 != arg_1, "smashed argument");
  assert(c_rarg0 != arg_2, "smashed argument");
  assert(c_rarg0 != arg_3, "smashed argument");
  assert(c_rarg1 != arg_2, "smashed argument");
  assert(c_rarg1 != arg_3, "smashed argument");
  assert(c_rarg2 != arg_1, "smashed argument");
  assert(c_rarg2 != arg_3, "smashed argument");
  assert(c_rarg3 != arg_1, "smashed argument");
  assert(c_rarg3 != arg_2, "smashed argument");

  Label C, E;
  Assembler::call(C, relocInfo::none);
  jmp(E);

  bind(C);
  // c_rarg0 is reserved for thread
  if (c_rarg1 != arg_1) {
    movq(c_rarg1, arg_1);
  }
  if (c_rarg2 != arg_2) {
    movq(c_rarg2, arg_2);
  }
  if (c_rarg3 != arg_3) {
    movq(c_rarg3, arg_3);
  }
  call_VM_helper(oop_result, entry_point, 3, check_exceptions);
  ret(0);

  bind(E);
}

void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             int num_args,
                             bool check_exceptions) {
  call_VM_base(oop_result, noreg, last_java_sp, entry_point, num_args,
               check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             Register arg_1,
                             bool check_exceptions) {
  assert(c_rarg0 != arg_1, "smashed argument");
  assert(c_rarg1 != last_java_sp, "smashed argument");
  // c_rarg0 is reserved for thread
  if (c_rarg1 != arg_1) {
    movq(c_rarg1, arg_1);
  }
  call_VM(oop_result, last_java_sp, entry_point, 1, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             bool check_exceptions) {
  assert(c_rarg0 != arg_1, "smashed argument");
  assert(c_rarg0 != arg_2, "smashed argument");
  assert(c_rarg1 != arg_2, "smashed argument");
  assert(c_rarg1 != last_java_sp, "smashed argument");
  assert(c_rarg2 != arg_1, "smashed argument");
  assert(c_rarg2 != last_java_sp, "smashed argument");
  // c_rarg0 is reserved for thread
  if (c_rarg1 != arg_1) {
    movq(c_rarg1, arg_1);
  }
  if (c_rarg2 != arg_2) {
    movq(c_rarg2, arg_2);
  }
  call_VM(oop_result, last_java_sp, entry_point, 2, check_exceptions);
}


void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             Register arg_3,
                             bool check_exceptions) {
  assert(c_rarg0 != arg_1, "smashed argument");
  assert(c_rarg0 != arg_2, "smashed argument");
  assert(c_rarg0 != arg_3, "smashed argument");
  assert(c_rarg1 != arg_2, "smashed argument");
  assert(c_rarg1 != arg_3, "smashed argument");
  assert(c_rarg1 != last_java_sp, "smashed argument");
  assert(c_rarg2 != arg_1, "smashed argument");
  assert(c_rarg2 != arg_3, "smashed argument");
  assert(c_rarg2 != last_java_sp, "smashed argument");
  assert(c_rarg3 != arg_1, "smashed argument");
  assert(c_rarg3 != arg_2, "smashed argument");
  assert(c_rarg3 != last_java_sp, "smashed argument");
  // c_rarg0 is reserved for thread
  if (c_rarg1 != arg_1) {
    movq(c_rarg1, arg_1);
  }
  if (c_rarg2 != arg_2) {
    movq(c_rarg2, arg_2);
  }
  if (c_rarg3 != arg_3) {
    movq(c_rarg2, arg_3);
  }
  call_VM(oop_result, last_java_sp, entry_point, 3, check_exceptions);
}

void MacroAssembler::call_VM_leaf(address entry_point, int num_args) {
  call_VM_leaf_base(entry_point, num_args);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_1) {
  if (c_rarg0 != arg_1) {
    movq(c_rarg0, arg_1);
  }
  call_VM_leaf(entry_point, 1);
}

void MacroAssembler::call_VM_leaf(address entry_point,
                                  Register arg_1,
                                  Register arg_2) {
  assert(c_rarg0 != arg_2, "smashed argument");
  assert(c_rarg1 != arg_1, "smashed argument");
  if (c_rarg0 != arg_1) {
    movq(c_rarg0, arg_1);
  }
  if (c_rarg1 != arg_2) {
    movq(c_rarg1, arg_2);
  }
  call_VM_leaf(entry_point, 2);
}

void MacroAssembler::call_VM_leaf(address entry_point,
                                  Register arg_1,
                                  Register arg_2,
                                  Register arg_3) {
  assert(c_rarg0 != arg_2, "smashed argument");
  assert(c_rarg0 != arg_3, "smashed argument");
  assert(c_rarg1 != arg_1, "smashed argument");
  assert(c_rarg1 != arg_3, "smashed argument");
  assert(c_rarg2 != arg_1, "smashed argument");
  assert(c_rarg2 != arg_2, "smashed argument");
  if (c_rarg0 != arg_1) {
    movq(c_rarg0, arg_1);
  }
  if (c_rarg1 != arg_2) {
    movq(c_rarg1, arg_2);
  }
  if (c_rarg2 != arg_3) {
    movq(c_rarg2, arg_3);
  }
  call_VM_leaf(entry_point, 3);
}


// Calls to C land
//
// When entering C land, the rbp & rsp of the last Java frame have to
// be recorded in the (thread-local) JavaThread object. When leaving C
// land, the last Java fp has to be reset to 0. This is required to
// allow proper stack traversal.
void MacroAssembler::store_check(Register obj) {
  // Does a store check for the oop in register obj. The content of
  // register obj is destroyed afterwards.
  store_check_part_1(obj);
  store_check_part_2(obj);
}

void MacroAssembler::store_check(Register obj, Address dst) {
  store_check(obj);
}

// split the store check operation so that other instructions can be
// scheduled inbetween
void MacroAssembler::store_check_part_1(Register obj) {
  BarrierSet* bs = Universe::heap()->barrier_set();
  assert(bs->kind() == BarrierSet::CardTableModRef, "Wrong barrier set kind");
  shrq(obj, CardTableModRefBS::card_shift);
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

void MacroAssembler::c2bool(Register x) {
  // implements x == 0 ? 0 : 1
  // note: must only look at least-significant byte of x
  //       since C-style booleans are stored in one byte
  //       only! (was bug)
  andl(x, 0xFF);
  setb(Assembler::notZero, x);
}

int MacroAssembler::corrected_idivl(Register reg) {
  // Full implementation of Java idiv and irem; checks for special
  // case as described in JVM spec., p.243 & p.271.  The function
  // returns the (pc) offset of the idivl instruction - may be needed
  // for implicit exceptions.
  //
  //         normal case                           special case
  //
  // input : eax: dividend                         min_int
  //         reg: divisor   (may not be eax/edx)   -1
  //
  // output: eax: quotient  (= eax idiv reg)       min_int
  //         edx: remainder (= eax irem reg)       0
  assert(reg != rax && reg != rdx, "reg cannot be rax or rdx register");
  const int min_int = 0x80000000;
  Label normal_case, special_case;

  // check for special case
  cmpl(rax, min_int);
  jcc(Assembler::notEqual, normal_case);
  xorl(rdx, rdx); // prepare edx for possible special case (where
                  // remainder = 0)
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

void MacroAssembler::push_IU_state() {
  pushfq();     // Push flags first because pushaq kills them
  subq(rsp, 8); // Make sure rsp stays 16-byte aligned
  pushaq();
}

void MacroAssembler::pop_IU_state() {
  popaq();
  addq(rsp, 8);
  popfq();
}

void MacroAssembler::push_FPU_state() {
  subq(rsp, FPUStateSizeInWords * wordSize);
  fxsave(Address(rsp, 0));
}

void MacroAssembler::pop_FPU_state() {
  fxrstor(Address(rsp, 0));
  addq(rsp, FPUStateSizeInWords * wordSize);
}

// Save Integer and Float state
// Warning: Stack must be 16 byte aligned
void MacroAssembler::push_CPU_state() {
  push_IU_state();
  push_FPU_state();
}

void MacroAssembler::pop_CPU_state() {
  pop_FPU_state();
  pop_IU_state();
}

void MacroAssembler::sign_extend_short(Register reg) {
  movswl(reg, reg);
}

void MacroAssembler::sign_extend_byte(Register reg) {
  movsbl(reg, reg);
}

void MacroAssembler::division_with_shift(Register reg, int shift_value) {
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

void MacroAssembler::round_to_l(Register reg, int modulus) {
  addl(reg, modulus - 1);
  andl(reg, -modulus);
}

void MacroAssembler::round_to_q(Register reg, int modulus) {
  addq(reg, modulus - 1);
  andq(reg, -modulus);
}

void MacroAssembler::verify_oop(Register reg, const char* s) {
  if (!VerifyOops) {
    return;
  }

  // Pass register number to verify_oop_subroutine
  char* b = new char[strlen(s) + 50];
  sprintf(b, "verify_oop: %s: %s", reg->name(), s);

  pushq(rax); // save rax, restored by receiver

  // pass args on stack, only touch rax
  pushq(reg);
  // avoid using pushptr, as it modifies scratch registers
  // and our contract is not to modify anything
  ExternalAddress buffer((address)b);
  movptr(rax, buffer.addr());
  pushq(rax);

  // call indirectly to solve generation ordering problem
  movptr(rax, ExternalAddress(StubRoutines::verify_oop_subroutine_entry_address()));
  call(rax); // no alignment requirement
  // everything popped by receiver
}

void MacroAssembler::verify_oop_addr(Address addr, const char* s) {
  if (!VerifyOops) return;
  // Pass register number to verify_oop_subroutine
  char* b = new char[strlen(s) + 50];
  sprintf(b, "verify_oop_addr: %s", s);
  pushq(rax);                          // save rax
  movq(addr, rax);
  pushq(rax);                          // pass register argument


  // avoid using pushptr, as it modifies scratch registers
  // and our contract is not to modify anything
  ExternalAddress buffer((address)b);
  movptr(rax, buffer.addr());
  pushq(rax);

  // call indirectly to solve generation ordering problem
  movptr(rax, ExternalAddress(StubRoutines::verify_oop_subroutine_entry_address()));
  call(rax); // no alignment requirement
  // everything popped by receiver
}


void MacroAssembler::stop(const char* msg) {
  address rip = pc();
  pushaq(); // get regs on stack
  lea(c_rarg0, ExternalAddress((address) msg));
  lea(c_rarg1, InternalAddress(rip));
  movq(c_rarg2, rsp); // pass pointer to regs array
  andq(rsp, -16); // align stack as required by ABI
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, MacroAssembler::debug)));
  hlt();
}

void MacroAssembler::warn(const char* msg) {
  pushq(r12);
  movq(r12, rsp);
  andq(rsp, -16);     // align stack as required by push_CPU_state and call

  push_CPU_state();   // keeps alignment at 16 bytes
  lea(c_rarg0, ExternalAddress((address) msg));
  call_VM_leaf(CAST_FROM_FN_PTR(address, warning), c_rarg0);
  pop_CPU_state();

  movq(rsp, r12);
  popq(r12);
}

#ifndef PRODUCT
extern "C" void findpc(intptr_t x);
#endif

void MacroAssembler::debug(char* msg, int64_t pc, int64_t regs[]) {
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

void MacroAssembler::os_breakpoint() {
  // instead of directly emitting a breakpoint, call os:breakpoint for
  // better debugability
  // This shouldn't need alignment, it's an empty function
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, os::breakpoint)));
}

// Write serialization page so VM thread can do a pseudo remote membar.
// We use the current thread pointer to calculate a thread specific
// offset to write to within the page. This minimizes bus traffic
// due to cache line collision.
void MacroAssembler::serialize_memory(Register thread,
                                      Register tmp) {

  movl(tmp, thread);
  shrl(tmp, os::get_serialize_page_shift_count());
  andl(tmp, (os::vm_page_size() - sizeof(int)));

  Address index(noreg, tmp, Address::times_1);
  ExternalAddress page(os::get_memory_serialize_page());

  movptr(ArrayAddress(page, index), tmp);
}

void MacroAssembler::verify_tlab() {
#ifdef ASSERT
  if (UseTLAB) {
    Label next, ok;
    Register t1 = rsi;

    pushq(t1);

    movq(t1, Address(r15_thread, in_bytes(JavaThread::tlab_top_offset())));
    cmpq(t1, Address(r15_thread, in_bytes(JavaThread::tlab_start_offset())));
    jcc(Assembler::aboveEqual, next);
    stop("assert(top >= start)");
    should_not_reach_here();

    bind(next);
    movq(t1, Address(r15_thread, in_bytes(JavaThread::tlab_end_offset())));
    cmpq(t1, Address(r15_thread, in_bytes(JavaThread::tlab_top_offset())));
    jcc(Assembler::aboveEqual, ok);
    stop("assert(top <= end)");
    should_not_reach_here();

    bind(ok);

    popq(t1);
  }
#endif
}

// Defines obj, preserves var_size_in_bytes
void MacroAssembler::eden_allocate(Register obj,
                                   Register var_size_in_bytes,
                                   int con_size_in_bytes,
                                   Register t1,
                                   Label& slow_case) {
  assert(obj == rax, "obj must be in rax for cmpxchg");
  assert_different_registers(obj, var_size_in_bytes, t1);
  Register end = t1;
  Label retry;
  bind(retry);
  ExternalAddress heap_top((address) Universe::heap()->top_addr());
  movptr(obj, heap_top);
  if (var_size_in_bytes == noreg) {
    leaq(end, Address(obj, con_size_in_bytes));
  } else {
    leaq(end, Address(obj, var_size_in_bytes, Address::times_1));
  }
  // if end < obj then we wrapped around => object too long => slow case
  cmpq(end, obj);
  jcc(Assembler::below, slow_case);
  cmpptr(end, ExternalAddress((address) Universe::heap()->end_addr()));

  jcc(Assembler::above, slow_case);
  // Compare obj with the top addr, and if still equal, store the new
  // top addr in end at the address of the top addr pointer. Sets ZF
  // if was equal, and clears it otherwise. Use lock prefix for
  // atomicity on MPs.
  if (os::is_MP()) {
    lock();
  }
  cmpxchgptr(end, heap_top);
  // if someone beat us on the allocation, try again, otherwise continue
  jcc(Assembler::notEqual, retry);
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

  verify_tlab();

  movq(obj, Address(r15_thread, JavaThread::tlab_top_offset()));
  if (var_size_in_bytes == noreg) {
    leaq(end, Address(obj, con_size_in_bytes));
  } else {
    leaq(end, Address(obj, var_size_in_bytes, Address::times_1));
  }
  cmpq(end, Address(r15_thread, JavaThread::tlab_end_offset()));
  jcc(Assembler::above, slow_case);

  // update the tlab top pointer
  movq(Address(r15_thread, JavaThread::tlab_top_offset()), end);

  // recover var_size_in_bytes if necessary
  if (var_size_in_bytes == end) {
    subq(var_size_in_bytes, obj);
  }
  verify_tlab();
}

// Preserves rbx and rdx.
void MacroAssembler::tlab_refill(Label& retry,
                                 Label& try_eden,
                                 Label& slow_case) {
  Register top = rax;
  Register t1 = rcx;
  Register t2 = rsi;
  Register t3 = r10;
  Register thread_reg = r15_thread;
  assert_different_registers(top, thread_reg, t1, t2, t3,
                             /* preserve: */ rbx, rdx);
  Label do_refill, discard_tlab;

  if (CMSIncrementalMode || !Universe::heap()->supports_inline_contig_alloc()) {
    // No allocation in the shared eden.
    jmp(slow_case);
  }

  movq(top, Address(thread_reg, in_bytes(JavaThread::tlab_top_offset())));
  movq(t1, Address(thread_reg, in_bytes(JavaThread::tlab_end_offset())));

  // calculate amount of free space
  subq(t1, top);
  shrq(t1, LogHeapWordSize);

  // Retain tlab and allocate object in shared space if
  // the amount free in the tlab is too large to discard.
  cmpq(t1, Address(thread_reg, // size_t
                   in_bytes(JavaThread::tlab_refill_waste_limit_offset())));
  jcc(Assembler::lessEqual, discard_tlab);

  // Retain
  mov64(t2, ThreadLocalAllocBuffer::refill_waste_limit_increment());
  addq(Address(thread_reg,  // size_t
               in_bytes(JavaThread::tlab_refill_waste_limit_offset())),
       t2);
  if (TLABStats) {
    // increment number of slow_allocations
    addl(Address(thread_reg, // unsigned int
                 in_bytes(JavaThread::tlab_slow_allocations_offset())),
         1);
  }
  jmp(try_eden);

  bind(discard_tlab);
  if (TLABStats) {
    // increment number of refills
    addl(Address(thread_reg, // unsigned int
                 in_bytes(JavaThread::tlab_number_of_refills_offset())),
         1);
    // accumulate wastage -- t1 is amount free in tlab
    addl(Address(thread_reg, // unsigned int
                 in_bytes(JavaThread::tlab_fast_refill_waste_offset())),
         t1);
  }

  // if tlab is currently allocated (top or end != null) then
  // fill [top, end + alignment_reserve) with array object
  testq(top, top);
  jcc(Assembler::zero, do_refill);

  // set up the mark word
  mov64(t3, (int64_t) markOopDesc::prototype()->copy_set_hash(0x2));
  movq(Address(top, oopDesc::mark_offset_in_bytes()), t3);
  // set the length to the remaining space
  subq(t1, typeArrayOopDesc::header_size(T_INT));
  addq(t1, (int)ThreadLocalAllocBuffer::alignment_reserve());
  shlq(t1, log2_intptr(HeapWordSize / sizeof(jint)));
  movq(Address(top, arrayOopDesc::length_offset_in_bytes()), t1);
  // set klass to intArrayKlass
  movptr(t1, ExternalAddress((address) Universe::intArrayKlassObj_addr()));
  // store klass last.  concurrent gcs assumes klass length is valid if
  // klass field is not null.
  store_klass(top, t1);

  // refill the tlab with an eden allocation
  bind(do_refill);
  movq(t1, Address(thread_reg, in_bytes(JavaThread::tlab_size_offset())));
  shlq(t1, LogHeapWordSize);
  // add object_size ??
  eden_allocate(top, t1, 0, t2, slow_case);

  // Check that t1 was preserved in eden_allocate.
#ifdef ASSERT
  if (UseTLAB) {
    Label ok;
    Register tsize = rsi;
    assert_different_registers(tsize, thread_reg, t1);
    pushq(tsize);
    movq(tsize, Address(thread_reg, in_bytes(JavaThread::tlab_size_offset())));
    shlq(tsize, LogHeapWordSize);
    cmpq(t1, tsize);
    jcc(Assembler::equal, ok);
    stop("assert(t1 != tlab size)");
    should_not_reach_here();

    bind(ok);
    popq(tsize);
  }
#endif
  movq(Address(thread_reg, in_bytes(JavaThread::tlab_start_offset())), top);
  movq(Address(thread_reg, in_bytes(JavaThread::tlab_top_offset())), top);
  addq(top, t1);
  subq(top, (int)ThreadLocalAllocBuffer::alignment_reserve_in_bytes());
  movq(Address(thread_reg, in_bytes(JavaThread::tlab_end_offset())), top);
  verify_tlab();
  jmp(retry);
}


int MacroAssembler::biased_locking_enter(Register lock_reg, Register obj_reg, Register swap_reg, Register tmp_reg,
                                         bool swap_reg_contains_mark,
                                         Label& done, Label* slow_case,
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


void MacroAssembler::biased_locking_exit(Register obj_reg, Register temp_reg, Label& done) {
  assert(UseBiasedLocking, "why call this otherwise?");

  // Check for biased locking unlock case, which is a no-op
  // Note: we do not have to check the thread ID for two reasons.
  // First, the interpreter checks for IllegalMonitorStateException at
  // a higher level. Second, if the bias was revoked while we held the
  // lock, the object could not be rebiased toward another thread, so
  // the bias bit would be clear.
  movq(temp_reg, Address(obj_reg, oopDesc::mark_offset_in_bytes()));
  andq(temp_reg, markOopDesc::biased_lock_mask_in_place);
  cmpq(temp_reg, markOopDesc::biased_lock_pattern);
  jcc(Assembler::equal, done);
}


void MacroAssembler::load_klass(Register dst, Register src) {
  if (UseCompressedOops) {
    movl(dst, Address(src, oopDesc::klass_offset_in_bytes()));
    decode_heap_oop_not_null(dst);
  } else {
    movq(dst, Address(src, oopDesc::klass_offset_in_bytes()));
  }
}

void MacroAssembler::load_prototype_header(Register dst, Register src) {
  if (UseCompressedOops) {
    movl(dst, Address(src, oopDesc::klass_offset_in_bytes()));
    movq(dst, Address(r12_heapbase, dst, Address::times_8, Klass::prototype_header_offset_in_bytes() + klassOopDesc::klass_part_offset_in_bytes()));
  } else {
    movq(dst, Address(src, oopDesc::klass_offset_in_bytes()));
    movq(dst, Address(dst, Klass::prototype_header_offset_in_bytes() + klassOopDesc::klass_part_offset_in_bytes()));
  }
}

void MacroAssembler::store_klass(Register dst, Register src) {
  if (UseCompressedOops) {
    encode_heap_oop_not_null(src);
    movl(Address(dst, oopDesc::klass_offset_in_bytes()), src);
  } else {
    movq(Address(dst, oopDesc::klass_offset_in_bytes()), src);
  }
}

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

// Algorithm must match oop.inline.hpp encode_heap_oop.
void MacroAssembler::encode_heap_oop(Register r) {
  assert (UseCompressedOops, "should be compressed");
#ifdef ASSERT
  if (CheckCompressedOops) {
    Label ok;
    pushq(rscratch1); // cmpptr trashes rscratch1
    cmpptr(r12_heapbase, ExternalAddress((address)Universe::heap_base_addr()));
    jcc(Assembler::equal, ok);
    stop("MacroAssembler::encode_heap_oop: heap base corrupted?");
    bind(ok);
    popq(rscratch1);
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
  subq(r, r12_heapbase);
  shrq(r, LogMinObjAlignmentInBytes);
}

void MacroAssembler::encode_heap_oop_not_null(Register dst, Register src) {
  assert (UseCompressedOops, "should be compressed");
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
  subq(dst, r12_heapbase);
  shrq(dst, LogMinObjAlignmentInBytes);
}

void  MacroAssembler::decode_heap_oop(Register r) {
  assert (UseCompressedOops, "should be compressed");
#ifdef ASSERT
  if (CheckCompressedOops) {
    Label ok;
    pushq(rscratch1);
    cmpptr(r12_heapbase,
           ExternalAddress((address)Universe::heap_base_addr()));
    jcc(Assembler::equal, ok);
    stop("MacroAssembler::decode_heap_oop: heap base corrupted?");
    bind(ok);
    popq(rscratch1);
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
  // Cannot assert, unverified entry point counts instructions (see .ad file)
  // vtableStubs also counts instructions in pd_code_size_limit.
  // Also do not verify_oop as this is called by verify_oop.
  assert(Address::times_8 == LogMinObjAlignmentInBytes, "decode alg wrong");
  leaq(r, Address(r12_heapbase, r, Address::times_8, 0));
}

void  MacroAssembler::decode_heap_oop_not_null(Register dst, Register src) {
  assert (UseCompressedOops, "should only be used for compressed headers");
  // Cannot assert, unverified entry point counts instructions (see .ad file)
  // vtableStubs also counts instructions in pd_code_size_limit.
  // Also do not verify_oop as this is called by verify_oop.
  assert(Address::times_8 == LogMinObjAlignmentInBytes, "decode alg wrong");
  leaq(dst, Address(r12_heapbase, src, Address::times_8, 0));
}

void  MacroAssembler::set_narrow_oop(Register dst, jobject obj) {
  assert(oop_recorder() != NULL, "this assembler needs an OopRecorder");
  int oop_index = oop_recorder()->find_index(obj);
  RelocationHolder rspec = oop_Relocation::spec(oop_index);

  // movl dst,obj
  InstructionMark im(this);
  int encode = prefix_and_encode(dst->encoding());
  emit_byte(0xB8 | encode);
  emit_data(oop_index, rspec, narrow_oop_operand);
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
  pushfq();
  if (os::is_MP())
    lock();
  incrementl(counter_addr);
  popfq();
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

void MacroAssembler::bang_stack_size(Register size, Register tmp) {
  movq(tmp, rsp);
  // Bang stack for total size given plus shadow page size.
  // Bang one page at a time because large size can bang beyond yellow and
  // red zones.
  Label loop;
  bind(loop);
  movl(Address(tmp, (-os::vm_page_size())), size );
  subq(tmp, os::vm_page_size());
  subl(size, os::vm_page_size());
  jcc(Assembler::greater, loop);

  // Bang down shadow pages too.
  // The -1 because we already subtracted 1 page.
  for (int i = 0; i< StackShadowPages-1; i++) {
    movq(Address(tmp, (-i*os::vm_page_size())), size );
  }
}

void MacroAssembler::reinit_heapbase() {
  if (UseCompressedOops) {
    movptr(r12_heapbase, ExternalAddress((address)Universe::heap_base_addr()));
  }
}
