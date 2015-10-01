/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "gc/shared/cardTableModRefBS.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/resourceArea.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1SATBCardTableModRefBS.hpp"
#include "gc/g1/heapRegion.hpp"
#endif // INCLUDE_ALL_GCS

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#define STOP(error) stop(error)
#else
#define BLOCK_COMMENT(str) block_comment(str)
#define STOP(error) block_comment(error); stop(error)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")
// Implementation of AddressLiteral

// A 2-D table for managing compressed displacement(disp8) on EVEX enabled platforms.
unsigned char tuple_table[Assembler::EVEX_ETUP + 1][Assembler::AVX_512bit + 1] = {
  // -----------------Table 4.5 -------------------- //
  16, 32, 64,  // EVEX_FV(0)
  4,  4,  4,   // EVEX_FV(1) - with Evex.b
  16, 32, 64,  // EVEX_FV(2) - with Evex.w
  8,  8,  8,   // EVEX_FV(3) - with Evex.w and Evex.b
  8,  16, 32,  // EVEX_HV(0)
  4,  4,  4,   // EVEX_HV(1) - with Evex.b
  // -----------------Table 4.6 -------------------- //
  16, 32, 64,  // EVEX_FVM(0)
  1,  1,  1,   // EVEX_T1S(0)
  2,  2,  2,   // EVEX_T1S(1)
  4,  4,  4,   // EVEX_T1S(2)
  8,  8,  8,   // EVEX_T1S(3)
  4,  4,  4,   // EVEX_T1F(0)
  8,  8,  8,   // EVEX_T1F(1)
  8,  8,  8,   // EVEX_T2(0)
  0,  16, 16,  // EVEX_T2(1)
  0,  16, 16,  // EVEX_T4(0)
  0,  0,  32,  // EVEX_T4(1)
  0,  0,  32,  // EVEX_T8(0)
  8,  16, 32,  // EVEX_HVM(0)
  4,  8,  16,  // EVEX_QVM(0)
  2,  4,  8,   // EVEX_OVM(0)
  16, 16, 16,  // EVEX_M128(0)
  8,  32, 64,  // EVEX_DUP(0)
  0,  0,  0    // EVEX_NTUP
};

AddressLiteral::AddressLiteral(address target, relocInfo::relocType rtype) {
  _is_lval = false;
  _target = target;
  switch (rtype) {
  case relocInfo::oop_type:
  case relocInfo::metadata_type:
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
Address Address::make_raw(int base, int index, int scale, int disp, relocInfo::relocType disp_reloc) {
  RelocationHolder rspec;
  if (disp_reloc != relocInfo::none) {
    rspec = Relocation::spec_simple(disp_reloc);
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
    emit_int32(data);
  else
    emit_data(data, Relocation::spec_simple(rtype), format);
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
  emit_int32(data);
}

static int encode(Register r) {
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
  emit_int8(op1);
  emit_int8(op2 | encode(dst));
  emit_int8(imm8);
}


void Assembler::emit_arith(int op1, int op2, Register dst, int32_t imm32) {
  assert(isByte(op1) && isByte(op2), "wrong opcode");
  assert((op1 & 0x01) == 1, "should be 32bit operation");
  assert((op1 & 0x02) == 0, "sign-extension bit should not be set");
  if (is8bit(imm32)) {
    emit_int8(op1 | 0x02); // set sign bit
    emit_int8(op2 | encode(dst));
    emit_int8(imm32 & 0xFF);
  } else {
    emit_int8(op1);
    emit_int8(op2 | encode(dst));
    emit_int32(imm32);
  }
}

// Force generation of a 4 byte immediate value even if it fits into 8bit
void Assembler::emit_arith_imm32(int op1, int op2, Register dst, int32_t imm32) {
  assert(isByte(op1) && isByte(op2), "wrong opcode");
  assert((op1 & 0x01) == 1, "should be 32bit operation");
  assert((op1 & 0x02) == 0, "sign-extension bit should not be set");
  emit_int8(op1);
  emit_int8(op2 | encode(dst));
  emit_int32(imm32);
}

// immediate-to-memory forms
void Assembler::emit_arith_operand(int op1, Register rm, Address adr, int32_t imm32) {
  assert((op1 & 0x01) == 1, "should be 32bit operation");
  assert((op1 & 0x02) == 0, "sign-extension bit should not be set");
  if (is8bit(imm32)) {
    emit_int8(op1 | 0x02); // set sign bit
    emit_operand(rm, adr, 1);
    emit_int8(imm32 & 0xFF);
  } else {
    emit_int8(op1);
    emit_operand(rm, adr, 4);
    emit_int32(imm32);
  }
}


void Assembler::emit_arith(int op1, int op2, Register dst, Register src) {
  assert(isByte(op1) && isByte(op2), "wrong opcode");
  emit_int8(op1);
  emit_int8(op2 | encode(dst) << 3 | encode(src));
}


bool Assembler::query_compressed_disp_byte(int disp, bool is_evex_inst, int vector_len,
                                           int cur_tuple_type, int in_size_in_bits, int cur_encoding) {
  int mod_idx = 0;
  // We will test if the displacement fits the compressed format and if so
  // apply the compression to the displacment iff the result is8bit.
  if (VM_Version::supports_evex() && is_evex_inst) {
    switch (cur_tuple_type) {
    case EVEX_FV:
      if ((cur_encoding & VEX_W) == VEX_W) {
        mod_idx += 2 + ((cur_encoding & EVEX_Rb) == EVEX_Rb) ? 1 : 0;
      } else {
        mod_idx = ((cur_encoding & EVEX_Rb) == EVEX_Rb) ? 1 : 0;
      }
      break;

    case EVEX_HV:
      mod_idx = ((cur_encoding & EVEX_Rb) == EVEX_Rb) ? 1 : 0;
      break;

    case EVEX_FVM:
      break;

    case EVEX_T1S:
      switch (in_size_in_bits) {
      case EVEX_8bit:
        break;

      case EVEX_16bit:
        mod_idx = 1;
        break;

      case EVEX_32bit:
        mod_idx = 2;
        break;

      case EVEX_64bit:
        mod_idx = 3;
        break;
      }
      break;

    case EVEX_T1F:
    case EVEX_T2:
    case EVEX_T4:
      mod_idx = (in_size_in_bits == EVEX_64bit) ? 1 : 0;
      break;

    case EVEX_T8:
      break;

    case EVEX_HVM:
      break;

    case EVEX_QVM:
      break;

    case EVEX_OVM:
      break;

    case EVEX_M128:
      break;

    case EVEX_DUP:
      break;

    default:
      assert(0, "no valid evex tuple_table entry");
      break;
    }

    if (vector_len >= AVX_128bit && vector_len <= AVX_512bit) {
      int disp_factor = tuple_table[cur_tuple_type + mod_idx][vector_len];
      if ((disp % disp_factor) == 0) {
        int new_disp = disp / disp_factor;
        if ((-0x80 <= new_disp && new_disp < 0x80)) {
          disp = new_disp;
        }
      } else {
        return false;
      }
    }
  }
  return (-0x80 <= disp && disp < 0x80);
}


bool Assembler::emit_compressed_disp_byte(int &disp) {
  int mod_idx = 0;
  // We will test if the displacement fits the compressed format and if so
  // apply the compression to the displacment iff the result is8bit.
  if (VM_Version::supports_evex() && _is_evex_instruction) {
    switch (_tuple_type) {
    case EVEX_FV:
      if ((_evex_encoding & VEX_W) == VEX_W) {
        mod_idx += 2 + ((_evex_encoding & EVEX_Rb) == EVEX_Rb) ? 1 : 0;
      } else {
        mod_idx = ((_evex_encoding & EVEX_Rb) == EVEX_Rb) ? 1 : 0;
      }
      break;

    case EVEX_HV:
      mod_idx = ((_evex_encoding & EVEX_Rb) == EVEX_Rb) ? 1 : 0;
      break;

    case EVEX_FVM:
      break;

    case EVEX_T1S:
      switch (_input_size_in_bits) {
      case EVEX_8bit:
        break;

      case EVEX_16bit:
        mod_idx = 1;
        break;

      case EVEX_32bit:
        mod_idx = 2;
        break;

      case EVEX_64bit:
        mod_idx = 3;
        break;
      }
      break;

    case EVEX_T1F:
    case EVEX_T2:
    case EVEX_T4:
      mod_idx = (_input_size_in_bits == EVEX_64bit) ? 1 : 0;
      break;

    case EVEX_T8:
      break;

    case EVEX_HVM:
      break;

    case EVEX_QVM:
      break;

    case EVEX_OVM:
      break;

    case EVEX_M128:
      break;

    case EVEX_DUP:
      break;

    default:
      assert(0, "no valid evex tuple_table entry");
      break;
    }

    if (_avx_vector_len >= AVX_128bit && _avx_vector_len <= AVX_512bit) {
      int disp_factor = tuple_table[_tuple_type + mod_idx][_avx_vector_len];
      if ((disp % disp_factor) == 0) {
        int new_disp = disp / disp_factor;
        if (is8bit(new_disp)) {
          disp = new_disp;
        }
      } else {
        return false;
      }
    }
  }
  return is8bit(disp);
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
        emit_int8(0x04 | regenc);
        emit_int8(scale << 6 | indexenc | baseenc);
      } else if (emit_compressed_disp_byte(disp) && rtype == relocInfo::none) {
        // [base + index*scale + imm8]
        // [01 reg 100][ss index base] imm8
        assert(index != rsp, "illegal addressing mode");
        emit_int8(0x44 | regenc);
        emit_int8(scale << 6 | indexenc | baseenc);
        emit_int8(disp & 0xFF);
      } else {
        // [base + index*scale + disp32]
        // [10 reg 100][ss index base] disp32
        assert(index != rsp, "illegal addressing mode");
        emit_int8(0x84 | regenc);
        emit_int8(scale << 6 | indexenc | baseenc);
        emit_data(disp, rspec, disp32_operand);
      }
    } else if (base == rsp LP64_ONLY(|| base == r12)) {
      // [rsp + disp]
      if (disp == 0 && rtype == relocInfo::none) {
        // [rsp]
        // [00 reg 100][00 100 100]
        emit_int8(0x04 | regenc);
        emit_int8(0x24);
      } else if (emit_compressed_disp_byte(disp) && rtype == relocInfo::none) {
        // [rsp + imm8]
        // [01 reg 100][00 100 100] disp8
        emit_int8(0x44 | regenc);
        emit_int8(0x24);
        emit_int8(disp & 0xFF);
      } else {
        // [rsp + imm32]
        // [10 reg 100][00 100 100] disp32
        emit_int8(0x84 | regenc);
        emit_int8(0x24);
        emit_data(disp, rspec, disp32_operand);
      }
    } else {
      // [base + disp]
      assert(base != rsp LP64_ONLY(&& base != r12), "illegal addressing mode");
      if (disp == 0 && rtype == relocInfo::none &&
          base != rbp LP64_ONLY(&& base != r13)) {
        // [base]
        // [00 reg base]
        emit_int8(0x00 | regenc | baseenc);
      } else if (emit_compressed_disp_byte(disp) && rtype == relocInfo::none) {
        // [base + disp8]
        // [01 reg base] disp8
        emit_int8(0x40 | regenc | baseenc);
        emit_int8(disp & 0xFF);
      } else {
        // [base + disp32]
        // [10 reg base] disp32
        emit_int8(0x80 | regenc | baseenc);
        emit_data(disp, rspec, disp32_operand);
      }
    }
  } else {
    if (index->is_valid()) {
      assert(scale != Address::no_scale, "inconsistent address");
      // [index*scale + disp]
      // [00 reg 100][ss index 101] disp32
      assert(index != rsp, "illegal addressing mode");
      emit_int8(0x04 | regenc);
      emit_int8(scale << 6 | indexenc | 0x05);
      emit_data(disp, rspec, disp32_operand);
    } else if (rtype != relocInfo::none ) {
      // [disp] (64bit) RIP-RELATIVE (32bit) abs
      // [00 000 101] disp32

      emit_int8(0x05 | regenc);
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
      emit_int8(0x04 | regenc);
      emit_int8(0x25);
      emit_data(disp, rspec, disp32_operand);
    }
  }
  _is_evex_instruction = false;
}

void Assembler::emit_operand(XMMRegister reg, Register base, Register index,
                             Address::ScaleFactor scale, int disp,
                             RelocationHolder const& rspec) {
  if (UseAVX > 2) {
    int xreg_enc = reg->encoding();
    if (xreg_enc > 15) {
      XMMRegister new_reg = as_XMMRegister(xreg_enc & 0xf);
      emit_operand((Register)new_reg, base, index, scale, disp, rspec);
      return;
    }
  }
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
    assert(which == imm_operand || which == disp32_operand,
           err_msg("which %d is_64_bit %d ip " INTPTR_FORMAT, which, is_64bit, p2i(ip)));
#else
    assert((which == call32_operand || which == imm_operand) && is_64bit ||
           which == narrow_oop_operand && !is_64bit,
           err_msg("which %d is_64_bit %d ip " INTPTR_FORMAT, which, is_64bit, p2i(ip)));
#endif // _LP64
    return ip;

  case 0x69: // imul r, a, #32
  case 0xC7: // movl a, #32(oop?)
    tail_size = 4;
    debug_only(has_disp32 = true); // has both kinds of operands!
    break;

  case 0x0F: // movx..., etc.
    switch (0xFF & *ip++) {
    case 0x3A: // pcmpestri
      tail_size = 1;
    case 0x38: // ptest, pmovzxbw
      ip++; // skip opcode
      debug_only(has_disp32 = true); // has both kinds of operands!
      break;

    case 0x70: // pshufd r, r/a, #8
      debug_only(has_disp32 = true); // has both kinds of operands!
    case 0x73: // psrldq r, #8
      tail_size = 1;
      break;

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
    case 0xAE: // ldmxcsr, stmxcsr, fxrstor, fxsave, clflush
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

    case 0xC4: // pinsrw r, a, #8
      debug_only(has_disp32 = true);
    case 0xC5: // pextrw r, r, #8
      tail_size = 1;  // the imm8
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

  case 0xC4: // VEX_3bytes
  case 0xC5: // VEX_2bytes
    assert((UseAVX > 0), "shouldn't have VEX prefix");
    assert(ip == inst+1, "no prefixes allowed");
    // C4 and C5 are also used as opcodes for PINSRW and PEXTRW instructions
    // but they have prefix 0x0F and processed when 0x0F processed above.
    //
    // In 32-bit mode the VEX first byte C4 and C5 alias onto LDS and LES
    // instructions (these instructions are not supported in 64-bit mode).
    // To distinguish them bits [7:6] are set in the VEX second byte since
    // ModRM byte can not be of the form 11xxxxxx in 32-bit mode. To set
    // those VEX bits REX and vvvv bits are inverted.
    //
    // Fortunately C2 doesn't generate these instructions so we don't need
    // to check for them in product version.

    // Check second byte
    NOT_LP64(assert((0xC0 & *ip) == 0xC0, "shouldn't have LDS and LES instructions"));

    // First byte
    if ((0xFF & *inst) == VEX_3bytes) {
      ip++; // third byte
      is_64bit = ((VEX_W & *ip) == VEX_W);
    }
    ip++; // opcode
    // To find the end of instruction (which == end_pc_operand).
    switch (0xFF & *ip) {
    case 0x61: // pcmpestri r, r/a, #8
    case 0x70: // pshufd r, r/a, #8
    case 0x73: // psrldq r, #8
      tail_size = 1;  // the imm8
      break;
    default:
      break;
    }
    ip++; // skip opcode
    debug_only(has_disp32 = true); // has both kinds of operands!
    break;

  case 0x62: // EVEX_4bytes
    assert((UseAVX > 0), "shouldn't have EVEX prefix");
    assert(ip == inst+1, "no prefixes allowed");
    // no EVEX collisions, all instructions that have 0x62 opcodes
    // have EVEX versions and are subopcodes of 0x66
    ip++; // skip P0 and exmaine W in P1
    is_64bit = ((VEX_W & *ip) == VEX_W);
    ip++; // move to P2
    ip++; // skip P2, move to opcode
    // To find the end of instruction (which == end_pc_operand).
    switch (0xFF & *ip) {
    case 0x61: // pcmpestri r, r/a, #8
    case 0x70: // pshufd r, r/a, #8
    case 0x73: // psrldq r, #8
      tail_size = 1;  // the imm8
      break;
    default:
      break;
    }
    ip++; // skip opcode
    debug_only(has_disp32 = true); // has both kinds of operands!
    break;

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

  case 0xE8: // call rdisp32
  case 0xE9: // jmp  rdisp32
    if (which == end_pc_operand)  return ip + 4;
    assert(which == call32_operand, "call has no disp32 or imm");
    return ip;

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
  emit_int8(b1);
  emit_int8(b2 + i);
}


// Now the Assembler instructions (identical for 32/64 bits)

void Assembler::adcl(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefix(dst);
  emit_arith_operand(0x81, rdx, dst, imm32);
}

void Assembler::adcl(Address dst, Register src) {
  InstructionMark im(this);
  prefix(dst, src);
  emit_int8(0x11);
  emit_operand(src, dst);
}

void Assembler::adcl(Register dst, int32_t imm32) {
  prefix(dst);
  emit_arith(0x81, 0xD0, dst, imm32);
}

void Assembler::adcl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_int8(0x13);
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
  emit_int8(0x01);
  emit_operand(src, dst);
}

void Assembler::addl(Register dst, int32_t imm32) {
  prefix(dst);
  emit_arith(0x81, 0xC0, dst, imm32);
}

void Assembler::addl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_int8(0x03);
  emit_operand(dst, src);
}

void Assembler::addl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x03, 0xC0, dst, src);
}

void Assembler::addr_nop_4() {
  assert(UseAddressNop, "no CPU support");
  // 4 bytes: NOP DWORD PTR [EAX+0]
  emit_int8(0x0F);
  emit_int8(0x1F);
  emit_int8(0x40); // emit_rm(cbuf, 0x1, EAX_enc, EAX_enc);
  emit_int8(0);    // 8-bits offset (1 byte)
}

void Assembler::addr_nop_5() {
  assert(UseAddressNop, "no CPU support");
  // 5 bytes: NOP DWORD PTR [EAX+EAX*0+0] 8-bits offset
  emit_int8(0x0F);
  emit_int8(0x1F);
  emit_int8(0x44); // emit_rm(cbuf, 0x1, EAX_enc, 0x4);
  emit_int8(0x00); // emit_rm(cbuf, 0x0, EAX_enc, EAX_enc);
  emit_int8(0);    // 8-bits offset (1 byte)
}

void Assembler::addr_nop_7() {
  assert(UseAddressNop, "no CPU support");
  // 7 bytes: NOP DWORD PTR [EAX+0] 32-bits offset
  emit_int8(0x0F);
  emit_int8(0x1F);
  emit_int8((unsigned char)0x80);
                   // emit_rm(cbuf, 0x2, EAX_enc, EAX_enc);
  emit_int32(0);   // 32-bits offset (4 bytes)
}

void Assembler::addr_nop_8() {
  assert(UseAddressNop, "no CPU support");
  // 8 bytes: NOP DWORD PTR [EAX+EAX*0+0] 32-bits offset
  emit_int8(0x0F);
  emit_int8(0x1F);
  emit_int8((unsigned char)0x84);
                   // emit_rm(cbuf, 0x2, EAX_enc, 0x4);
  emit_int8(0x00); // emit_rm(cbuf, 0x0, EAX_enc, EAX_enc);
  emit_int32(0);   // 32-bits offset (4 bytes)
}

void Assembler::addsd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0x58, dst, src, VEX_SIMD_F2);
  } else {
    emit_simd_arith(0x58, dst, src, VEX_SIMD_F2);
  }
}

void Assembler::addsd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_64bit;
    emit_simd_arith_q(0x58, dst, src, VEX_SIMD_F2);
  } else {
    emit_simd_arith(0x58, dst, src, VEX_SIMD_F2);
  }
}

void Assembler::addss(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_simd_arith(0x58, dst, src, VEX_SIMD_F3);
}

void Assembler::addss(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_simd_arith(0x58, dst, src, VEX_SIMD_F3);
}

void Assembler::aesdec(XMMRegister dst, Address src) {
  assert(VM_Version::supports_aes(), "");
  InstructionMark im(this);
  simd_prefix(dst, dst, src, VEX_SIMD_66, /* no_mask_reg */ false,
              VEX_OPCODE_0F_38, /* rex_w */ false, AVX_128bit, /* legacy_mode */ true);
  emit_int8((unsigned char)0xDE);
  emit_operand(dst, src);
}

void Assembler::aesdec(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_aes(), "");
  int encode = simd_prefix_and_encode(dst, dst, src, VEX_SIMD_66, /* no_mask_reg */ false,
                                      VEX_OPCODE_0F_38,  /* rex_w */ false, AVX_128bit, /* legacy_mode */ true);
  emit_int8((unsigned char)0xDE);
  emit_int8(0xC0 | encode);
}

void Assembler::aesdeclast(XMMRegister dst, Address src) {
  assert(VM_Version::supports_aes(), "");
  InstructionMark im(this);
  simd_prefix(dst, dst, src, VEX_SIMD_66, /* no_mask_reg */ false,
              VEX_OPCODE_0F_38, /* rex_w */ false, AVX_128bit,  /* legacy_mode */ true);
  emit_int8((unsigned char)0xDF);
  emit_operand(dst, src);
}

void Assembler::aesdeclast(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_aes(), "");
  int encode = simd_prefix_and_encode(dst, dst, src, VEX_SIMD_66, /* no_mask_reg */ false,
                                      VEX_OPCODE_0F_38,  /* rex_w */ false, AVX_128bit, /* legacy_mode */ true);
  emit_int8((unsigned char)0xDF);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::aesenc(XMMRegister dst, Address src) {
  assert(VM_Version::supports_aes(), "");
  InstructionMark im(this);
  simd_prefix(dst, dst, src, VEX_SIMD_66, /* no_mask_reg */ false,
              VEX_OPCODE_0F_38, /* rex_w */ false, AVX_128bit, /* legacy_mode */ true);
  emit_int8((unsigned char)0xDC);
  emit_operand(dst, src);
}

void Assembler::aesenc(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_aes(), "");
  int encode = simd_prefix_and_encode(dst, dst, src, VEX_SIMD_66, /* no_mask_reg */ false,
                                      VEX_OPCODE_0F_38, /* rex_w */ false, AVX_128bit, /* legacy_mode */ true);
  emit_int8((unsigned char)0xDC);
  emit_int8(0xC0 | encode);
}

void Assembler::aesenclast(XMMRegister dst, Address src) {
  assert(VM_Version::supports_aes(), "");
  InstructionMark im(this);
  simd_prefix(dst, dst, src, VEX_SIMD_66, /* no_mask_reg */ false,
              VEX_OPCODE_0F_38, /* rex_w */ false, AVX_128bit,  /* legacy_mode */ true);
  emit_int8((unsigned char)0xDD);
  emit_operand(dst, src);
}

void Assembler::aesenclast(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_aes(), "");
  int encode = simd_prefix_and_encode(dst, dst, src, VEX_SIMD_66, /* no_mask_reg */ false,
                                      VEX_OPCODE_0F_38, /* rex_w */ false, AVX_128bit, /* legacy_mode */ true);
  emit_int8((unsigned char)0xDD);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::andl(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefix(dst);
  emit_int8((unsigned char)0x81);
  emit_operand(rsp, dst, 4);
  emit_int32(imm32);
}

void Assembler::andl(Register dst, int32_t imm32) {
  prefix(dst);
  emit_arith(0x81, 0xE0, dst, imm32);
}

void Assembler::andl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_int8(0x23);
  emit_operand(dst, src);
}

void Assembler::andl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x23, 0xC0, dst, src);
}

void Assembler::andnl(Register dst, Register src1, Register src2) {
  assert(VM_Version::supports_bmi1(), "bit manipulation instructions not supported");
  int encode = vex_prefix_0F38_and_encode_legacy(dst, src1, src2);
  emit_int8((unsigned char)0xF2);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::andnl(Register dst, Register src1, Address src2) {
  InstructionMark im(this);
  assert(VM_Version::supports_bmi1(), "bit manipulation instructions not supported");
  vex_prefix_0F38_legacy(dst, src1, src2);
  emit_int8((unsigned char)0xF2);
  emit_operand(dst, src2);
}

void Assembler::bsfl(Register dst, Register src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)0xBC);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::bsrl(Register dst, Register src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)0xBD);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::bswapl(Register reg) { // bswap
  int encode = prefix_and_encode(reg->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)(0xC8 | encode));
}

void Assembler::blsil(Register dst, Register src) {
  assert(VM_Version::supports_bmi1(), "bit manipulation instructions not supported");
  int encode = vex_prefix_0F38_and_encode_legacy(rbx, dst, src);
  emit_int8((unsigned char)0xF3);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::blsil(Register dst, Address src) {
  InstructionMark im(this);
  assert(VM_Version::supports_bmi1(), "bit manipulation instructions not supported");
  vex_prefix_0F38_legacy(rbx, dst, src);
  emit_int8((unsigned char)0xF3);
  emit_operand(rbx, src);
}

void Assembler::blsmskl(Register dst, Register src) {
  assert(VM_Version::supports_bmi1(), "bit manipulation instructions not supported");
  int encode = vex_prefix_0F38_and_encode_legacy(rdx, dst, src);
  emit_int8((unsigned char)0xF3);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::blsmskl(Register dst, Address src) {
  InstructionMark im(this);
  assert(VM_Version::supports_bmi1(), "bit manipulation instructions not supported");
  vex_prefix_0F38_legacy(rdx, dst, src);
  emit_int8((unsigned char)0xF3);
  emit_operand(rdx, src);
}

void Assembler::blsrl(Register dst, Register src) {
  assert(VM_Version::supports_bmi1(), "bit manipulation instructions not supported");
  int encode = vex_prefix_0F38_and_encode_legacy(rcx, dst, src);
  emit_int8((unsigned char)0xF3);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::blsrl(Register dst, Address src) {
  InstructionMark im(this);
  assert(VM_Version::supports_bmi1(), "bit manipulation instructions not supported");
  vex_prefix_0F38_legacy(rcx, dst, src);
  emit_int8((unsigned char)0xF3);
  emit_operand(rcx, src);
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
    emit_int8((unsigned char)0xE8);
    emit_data(offs - long_size, rtype, operand);
  } else {
    InstructionMark im(this);
    // 1110 1000 #32-bit disp
    L.add_patch_at(code(), locator());

    emit_int8((unsigned char)0xE8);
    emit_data(int(0), rtype, operand);
  }
}

void Assembler::call(Register dst) {
  int encode = prefix_and_encode(dst->encoding());
  emit_int8((unsigned char)0xFF);
  emit_int8((unsigned char)(0xD0 | encode));
}


void Assembler::call(Address adr) {
  InstructionMark im(this);
  prefix(adr);
  emit_int8((unsigned char)0xFF);
  emit_operand(rdx, adr);
}

void Assembler::call_literal(address entry, RelocationHolder const& rspec) {
  assert(entry != NULL, "call most probably wrong");
  InstructionMark im(this);
  emit_int8((unsigned char)0xE8);
  intptr_t disp = entry - (pc() + sizeof(int32_t));
  assert(is_simm32(disp), "must be 32bit offset (call2)");
  // Technically, should use call32_operand, but this format is
  // implied by the fact that we're emitting a call instruction.

  int operand = LP64_ONLY(disp32_operand) NOT_LP64(call32_operand);
  emit_data((int) disp, rspec, operand);
}

void Assembler::cdql() {
  emit_int8((unsigned char)0x99);
}

void Assembler::cld() {
  emit_int8((unsigned char)0xFC);
}

void Assembler::cmovl(Condition cc, Register dst, Register src) {
  NOT_LP64(guarantee(VM_Version::supports_cmov(), "illegal instruction"));
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8(0x40 | cc);
  emit_int8((unsigned char)(0xC0 | encode));
}


void Assembler::cmovl(Condition cc, Register dst, Address src) {
  NOT_LP64(guarantee(VM_Version::supports_cmov(), "illegal instruction"));
  prefix(src, dst);
  emit_int8(0x0F);
  emit_int8(0x40 | cc);
  emit_operand(dst, src);
}

void Assembler::cmpb(Address dst, int imm8) {
  InstructionMark im(this);
  prefix(dst);
  emit_int8((unsigned char)0x80);
  emit_operand(rdi, dst, 1);
  emit_int8(imm8);
}

void Assembler::cmpl(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefix(dst);
  emit_int8((unsigned char)0x81);
  emit_operand(rdi, dst, 4);
  emit_int32(imm32);
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
  emit_int8((unsigned char)0x3B);
  emit_operand(dst, src);
}

void Assembler::cmpw(Address dst, int imm16) {
  InstructionMark im(this);
  assert(!dst.base_needs_rex() && !dst.index_needs_rex(), "no extended registers");
  emit_int8(0x66);
  emit_int8((unsigned char)0x81);
  emit_operand(rdi, dst, 2);
  emit_int16(imm16);
}

// The 32-bit cmpxchg compares the value at adr with the contents of rax,
// and stores reg into adr if so; otherwise, the value at adr is loaded into rax,.
// The ZF is set if the compared values were equal, and cleared otherwise.
void Assembler::cmpxchgl(Register reg, Address adr) { // cmpxchg
  InstructionMark im(this);
  prefix(adr, reg);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xB1);
  emit_operand(reg, adr);
}

// The 8-bit cmpxchg compares the value at adr with the contents of rax,
// and stores reg into adr if so; otherwise, the value at adr is loaded into rax,.
// The ZF is set if the compared values were equal, and cleared otherwise.
void Assembler::cmpxchgb(Register reg, Address adr) { // cmpxchg
  InstructionMark im(this);
  prefix(adr, reg, true);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xB0);
  emit_operand(reg, adr);
}

void Assembler::comisd(XMMRegister dst, Address src) {
  // NOTE: dbx seems to decode this as comiss even though the
  // 0x66 is there. Strangly ucomisd comes out correct
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_64bit;
    emit_simd_arith_nonds_q(0x2F, dst, src, VEX_SIMD_66, /* no_mask_reg */ true);
  } else {
    emit_simd_arith_nonds(0x2F, dst, src, VEX_SIMD_66);
  }
}

void Assembler::comisd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_nonds_q(0x2F, dst, src, VEX_SIMD_66, /* no_mask_reg */ true);
  } else {
    emit_simd_arith_nonds(0x2F, dst, src, VEX_SIMD_66);
  }
}

void Assembler::comiss(XMMRegister dst, Address src) {
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_simd_arith_nonds(0x2F, dst, src, VEX_SIMD_NONE, /* no_mask_reg */ true);
}

void Assembler::comiss(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_simd_arith_nonds(0x2F, dst, src, VEX_SIMD_NONE, /* no_mask_reg */ true);
}

void Assembler::cpuid() {
  emit_int8(0x0F);
  emit_int8((unsigned char)0xA2);
}

// Opcode / Instruction                      Op /  En  64 - Bit Mode     Compat / Leg Mode Description                  Implemented
// F2 0F 38 F0 / r       CRC32 r32, r / m8   RM        Valid             Valid             Accumulate CRC32 on r / m8.  v
// F2 REX 0F 38 F0 / r   CRC32 r32, r / m8*  RM        Valid             N.E.              Accumulate CRC32 on r / m8.  -
// F2 REX.W 0F 38 F0 / r CRC32 r64, r / m8   RM        Valid             N.E.              Accumulate CRC32 on r / m8.  -
//
// F2 0F 38 F1 / r       CRC32 r32, r / m16  RM        Valid             Valid             Accumulate CRC32 on r / m16. v
//
// F2 0F 38 F1 / r       CRC32 r32, r / m32  RM        Valid             Valid             Accumulate CRC32 on r / m32. v
//
// F2 REX.W 0F 38 F1 / r CRC32 r64, r / m64  RM        Valid             N.E.              Accumulate CRC32 on r / m64. v
void Assembler::crc32(Register crc, Register v, int8_t sizeInBytes) {
  assert(VM_Version::supports_sse4_2(), "");
  int8_t w = 0x01;
  Prefix p = Prefix_EMPTY;

  emit_int8((int8_t)0xF2);
  switch (sizeInBytes) {
  case 1:
    w = 0;
    break;
  case 2:
  case 4:
    break;
  LP64_ONLY(case 8:)
    // This instruction is not valid in 32 bits
    // Note:
    // http://www.intel.com/content/dam/www/public/us/en/documents/manuals/64-ia-32-architectures-software-developer-instruction-set-reference-manual-325383.pdf
    //
    // Page B - 72   Vol. 2C says
    // qwreg2 to qwreg            1111 0010 : 0100 1R0B : 0000 1111 : 0011 1000 : 1111 0000 : 11 qwreg1 qwreg2
    // mem64 to qwreg             1111 0010 : 0100 1R0B : 0000 1111 : 0011 1000 : 1111 0000 : mod qwreg r / m
    //                                                                            F0!!!
    // while 3 - 208 Vol. 2A
    // F2 REX.W 0F 38 F1 / r       CRC32 r64, r / m64             RM         Valid      N.E.Accumulate CRC32 on r / m64.
    //
    // the 0 on a last bit is reserved for a different flavor of this instruction :
    // F2 REX.W 0F 38 F0 / r       CRC32 r64, r / m8              RM         Valid      N.E.Accumulate CRC32 on r / m8.
    p = REX_W;
    break;
  default:
    assert(0, "Unsupported value for a sizeInBytes argument");
    break;
  }
  LP64_ONLY(prefix(crc, v, p);)
  emit_int8((int8_t)0x0F);
  emit_int8(0x38);
  emit_int8((int8_t)(0xF0 | w));
  emit_int8(0xC0 | ((crc->encoding() & 0x7) << 3) | (v->encoding() & 7));
}

void Assembler::crc32(Register crc, Address adr, int8_t sizeInBytes) {
  assert(VM_Version::supports_sse4_2(), "");
  InstructionMark im(this);
  int8_t w = 0x01;
  Prefix p = Prefix_EMPTY;

  emit_int8((int8_t)0xF2);
  switch (sizeInBytes) {
  case 1:
    w = 0;
    break;
  case 2:
  case 4:
    break;
  LP64_ONLY(case 8:)
    // This instruction is not valid in 32 bits
    p = REX_W;
    break;
  default:
    assert(0, "Unsupported value for a sizeInBytes argument");
    break;
  }
  LP64_ONLY(prefix(crc, adr, p);)
  emit_int8((int8_t)0x0F);
  emit_int8(0x38);
  emit_int8((int8_t)(0xF0 | w));
  emit_operand(crc, adr);
}

void Assembler::cvtdq2pd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith_nonds(0xE6, dst, src, VEX_SIMD_F3, /* no_mask_reg */ false, /* legacy_mode */ true);
}

void Assembler::cvtdq2ps(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith_nonds(0x5B, dst, src, VEX_SIMD_NONE, /* no_mask_reg */ false, /* legacy_mode */ true);
}

void Assembler::cvtsd2ss(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0x5A, dst, src, VEX_SIMD_F2);
  } else {
    emit_simd_arith(0x5A, dst, src, VEX_SIMD_F2);
  }
}

void Assembler::cvtsd2ss(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1F;
    _input_size_in_bits = EVEX_64bit;
    emit_simd_arith_q(0x5A, dst, src, VEX_SIMD_F2);
  } else {
    emit_simd_arith(0x5A, dst, src, VEX_SIMD_F2);
  }
}

void Assembler::cvtsi2sdl(XMMRegister dst, Register src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  int encode = simd_prefix_and_encode(dst, dst, src, VEX_SIMD_F2, VM_Version::supports_evex());
  emit_int8(0x2A);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::cvtsi2sdl(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
    emit_simd_arith(0x2A, dst, src, VEX_SIMD_F2, /* no_mask_reg */ true);
  } else {
    emit_simd_arith(0x2A, dst, src, VEX_SIMD_F2);
  }
}

void Assembler::cvtsi2ssl(XMMRegister dst, Register src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  int encode = simd_prefix_and_encode(dst, dst, src, VEX_SIMD_F3, /* no_mask_reg */ true);
  emit_int8(0x2A);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::cvtsi2ssl(XMMRegister dst, Address src) {
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_simd_arith(0x2A, dst, src, VEX_SIMD_F3, /* no_mask_reg */ true);
}

void Assembler::cvtsi2ssq(XMMRegister dst, Register src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  int encode = simd_prefix_and_encode_q(dst, dst, src, VEX_SIMD_F3, /* no_mask_reg */ true);
  emit_int8(0x2A);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::cvtss2sd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0x5A, dst, src, VEX_SIMD_F3);
}

void Assembler::cvtss2sd(XMMRegister dst, Address src) {
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0x5A, dst, src, VEX_SIMD_F3);
}


void Assembler::cvttsd2sil(Register dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  int encode = simd_prefix_and_encode(dst, src, VEX_SIMD_F2, VEX_OPCODE_0F, /* no_mask_reg */ true);
  emit_int8(0x2C);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::cvttss2sil(Register dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  int encode = simd_prefix_and_encode(dst, src, VEX_SIMD_F3, VEX_OPCODE_0F, /* no_mask_reg */ true);
  emit_int8(0x2C);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::decl(Address dst) {
  // Don't use it directly. Use MacroAssembler::decrement() instead.
  InstructionMark im(this);
  prefix(dst);
  emit_int8((unsigned char)0xFF);
  emit_operand(rcx, dst);
}

void Assembler::divsd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_64bit;
    emit_simd_arith_q(0x5E, dst, src, VEX_SIMD_F2);
  } else {
    emit_simd_arith(0x5E, dst, src, VEX_SIMD_F2);
  }
}

void Assembler::divsd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0x5E, dst, src, VEX_SIMD_F2);
  } else {
    emit_simd_arith(0x5E, dst, src, VEX_SIMD_F2);
  }
}

void Assembler::divss(XMMRegister dst, Address src) {
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_simd_arith(0x5E, dst, src, VEX_SIMD_F3);
}

void Assembler::divss(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_simd_arith(0x5E, dst, src, VEX_SIMD_F3);
}

void Assembler::emms() {
  NOT_LP64(assert(VM_Version::supports_mmx(), ""));
  emit_int8(0x0F);
  emit_int8(0x77);
}

void Assembler::hlt() {
  emit_int8((unsigned char)0xF4);
}

void Assembler::idivl(Register src) {
  int encode = prefix_and_encode(src->encoding());
  emit_int8((unsigned char)0xF7);
  emit_int8((unsigned char)(0xF8 | encode));
}

void Assembler::divl(Register src) { // Unsigned
  int encode = prefix_and_encode(src->encoding());
  emit_int8((unsigned char)0xF7);
  emit_int8((unsigned char)(0xF0 | encode));
}

void Assembler::imull(Register dst, Register src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)0xAF);
  emit_int8((unsigned char)(0xC0 | encode));
}


void Assembler::imull(Register dst, Register src, int value) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  if (is8bit(value)) {
    emit_int8(0x6B);
    emit_int8((unsigned char)(0xC0 | encode));
    emit_int8(value & 0xFF);
  } else {
    emit_int8(0x69);
    emit_int8((unsigned char)(0xC0 | encode));
    emit_int32(value);
  }
}

void Assembler::imull(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_int8(0x0F);
  emit_int8((unsigned char) 0xAF);
  emit_operand(dst, src);
}


void Assembler::incl(Address dst) {
  // Don't use it directly. Use MacroAssembler::increment() instead.
  InstructionMark im(this);
  prefix(dst);
  emit_int8((unsigned char)0xFF);
  emit_operand(rax, dst);
}

void Assembler::jcc(Condition cc, Label& L, bool maybe_short) {
  InstructionMark im(this);
  assert((0 <= cc) && (cc < 16), "illegal cc");
  if (L.is_bound()) {
    address dst = target(L);
    assert(dst != NULL, "jcc most probably wrong");

    const int short_size = 2;
    const int long_size = 6;
    intptr_t offs = (intptr_t)dst - (intptr_t)pc();
    if (maybe_short && is8bit(offs - short_size)) {
      // 0111 tttn #8-bit disp
      emit_int8(0x70 | cc);
      emit_int8((offs - short_size) & 0xFF);
    } else {
      // 0000 1111 1000 tttn #32-bit disp
      assert(is_simm32(offs - long_size),
             "must be 32bit offset (call4)");
      emit_int8(0x0F);
      emit_int8((unsigned char)(0x80 | cc));
      emit_int32(offs - long_size);
    }
  } else {
    // Note: could eliminate cond. jumps to this jump if condition
    //       is the same however, seems to be rather unlikely case.
    // Note: use jccb() if label to be bound is very close to get
    //       an 8-bit displacement
    L.add_patch_at(code(), locator());
    emit_int8(0x0F);
    emit_int8((unsigned char)(0x80 | cc));
    emit_int32(0);
  }
}

void Assembler::jccb(Condition cc, Label& L) {
  if (L.is_bound()) {
    const int short_size = 2;
    address entry = target(L);
#ifdef ASSERT
    intptr_t dist = (intptr_t)entry - ((intptr_t)pc() + short_size);
    intptr_t delta = short_branch_delta();
    if (delta != 0) {
      dist += (dist < 0 ? (-delta) :delta);
    }
    assert(is8bit(dist), "Dispacement too large for a short jmp");
#endif
    intptr_t offs = (intptr_t)entry - (intptr_t)pc();
    // 0111 tttn #8-bit disp
    emit_int8(0x70 | cc);
    emit_int8((offs - short_size) & 0xFF);
  } else {
    InstructionMark im(this);
    L.add_patch_at(code(), locator());
    emit_int8(0x70 | cc);
    emit_int8(0);
  }
}

void Assembler::jmp(Address adr) {
  InstructionMark im(this);
  prefix(adr);
  emit_int8((unsigned char)0xFF);
  emit_operand(rsp, adr);
}

void Assembler::jmp(Label& L, bool maybe_short) {
  if (L.is_bound()) {
    address entry = target(L);
    assert(entry != NULL, "jmp most probably wrong");
    InstructionMark im(this);
    const int short_size = 2;
    const int long_size = 5;
    intptr_t offs = entry - pc();
    if (maybe_short && is8bit(offs - short_size)) {
      emit_int8((unsigned char)0xEB);
      emit_int8((offs - short_size) & 0xFF);
    } else {
      emit_int8((unsigned char)0xE9);
      emit_int32(offs - long_size);
    }
  } else {
    // By default, forward jumps are always 32-bit displacements, since
    // we can't yet know where the label will be bound.  If you're sure that
    // the forward jump will not run beyond 256 bytes, use jmpb to
    // force an 8-bit displacement.
    InstructionMark im(this);
    L.add_patch_at(code(), locator());
    emit_int8((unsigned char)0xE9);
    emit_int32(0);
  }
}

void Assembler::jmp(Register entry) {
  int encode = prefix_and_encode(entry->encoding());
  emit_int8((unsigned char)0xFF);
  emit_int8((unsigned char)(0xE0 | encode));
}

void Assembler::jmp_literal(address dest, RelocationHolder const& rspec) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xE9);
  assert(dest != NULL, "must have a target");
  intptr_t disp = dest - (pc() + sizeof(int32_t));
  assert(is_simm32(disp), "must be 32bit offset (jmp)");
  emit_data(disp, rspec.reloc(), call32_operand);
}

void Assembler::jmpb(Label& L) {
  if (L.is_bound()) {
    const int short_size = 2;
    address entry = target(L);
    assert(entry != NULL, "jmp most probably wrong");
#ifdef ASSERT
    intptr_t dist = (intptr_t)entry - ((intptr_t)pc() + short_size);
    intptr_t delta = short_branch_delta();
    if (delta != 0) {
      dist += (dist < 0 ? (-delta) :delta);
    }
    assert(is8bit(dist), "Dispacement too large for a short jmp");
#endif
    intptr_t offs = entry - pc();
    emit_int8((unsigned char)0xEB);
    emit_int8((offs - short_size) & 0xFF);
  } else {
    InstructionMark im(this);
    L.add_patch_at(code(), locator());
    emit_int8((unsigned char)0xEB);
    emit_int8(0);
  }
}

void Assembler::ldmxcsr( Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  InstructionMark im(this);
  prefix(src);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xAE);
  emit_operand(as_Register(2), src);
}

void Assembler::leal(Register dst, Address src) {
  InstructionMark im(this);
#ifdef _LP64
  emit_int8(0x67); // addr32
  prefix(src, dst);
#endif // LP64
  emit_int8((unsigned char)0x8D);
  emit_operand(dst, src);
}

void Assembler::lfence() {
  emit_int8(0x0F);
  emit_int8((unsigned char)0xAE);
  emit_int8((unsigned char)0xE8);
}

void Assembler::lock() {
  emit_int8((unsigned char)0xF0);
}

void Assembler::lzcntl(Register dst, Register src) {
  assert(VM_Version::supports_lzcnt(), "encoding is treated as BSR");
  emit_int8((unsigned char)0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)0xBD);
  emit_int8((unsigned char)(0xC0 | encode));
}

// Emit mfence instruction
void Assembler::mfence() {
  NOT_LP64(assert(VM_Version::supports_sse2(), "unsupported");)
  emit_int8(0x0F);
  emit_int8((unsigned char)0xAE);
  emit_int8((unsigned char)0xF0);
}

void Assembler::mov(Register dst, Register src) {
  LP64_ONLY(movq(dst, src)) NOT_LP64(movl(dst, src));
}

void Assembler::movapd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_avx512novl()) {
    int vector_len = AVX_512bit;
    int dst_enc = dst->encoding();
    int src_enc = src->encoding();
    int encode = vex_prefix_and_encode(dst_enc, 0, src_enc, VEX_SIMD_66, VEX_OPCODE_0F,
                                       /* vex_w */ true, vector_len, /* legacy_mode */ false, /* no_mask_reg */ false);
    emit_int8(0x28);
    emit_int8((unsigned char)(0xC0 | encode));
  } else if (VM_Version::supports_evex()) {
    emit_simd_arith_nonds_q(0x28, dst, src, VEX_SIMD_66);
  } else {
    emit_simd_arith_nonds(0x28, dst, src, VEX_SIMD_66);
  }
}

void Assembler::movaps(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  if (VM_Version::supports_avx512novl()) {
    int vector_len = AVX_512bit;
    int encode = vex_prefix_and_encode(dst, xnoreg, src, VEX_SIMD_NONE, vector_len);
    emit_int8(0x28);
    emit_int8((unsigned char)(0xC0 | encode));
  } else {
    emit_simd_arith_nonds(0x28, dst, src, VEX_SIMD_NONE);
  }
}

void Assembler::movlhps(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  int encode = simd_prefix_and_encode(dst, src, src, VEX_SIMD_NONE, /* no_mask_reg */ true);
  emit_int8(0x16);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::movb(Register dst, Address src) {
  NOT_LP64(assert(dst->has_byte_register(), "must have byte register"));
  InstructionMark im(this);
  prefix(src, dst, true);
  emit_int8((unsigned char)0x8A);
  emit_operand(dst, src);
}

void Assembler::kmovql(KRegister dst, KRegister src) {
  NOT_LP64(assert(VM_Version::supports_evex(), ""));
  int encode = kreg_prefix_and_encode(dst, knoreg, src, VEX_SIMD_NONE,
                                      /* no_mask_reg */ true, VEX_OPCODE_0F, /* rex_w */ true);
  emit_int8((unsigned char)0x90);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::kmovql(KRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_evex(), ""));
  int dst_enc = dst->encoding();
  int nds_enc = 0;
  vex_prefix(src, nds_enc, dst_enc, VEX_SIMD_NONE,
             VEX_OPCODE_0F, /* vex_w */  true, AVX_128bit, /* legacy_mode */ true, /* no_reg_mask */ true);
  emit_int8((unsigned char)0x90);
  emit_operand((Register)dst, src);
}

void Assembler::kmovql(Address dst, KRegister src) {
  NOT_LP64(assert(VM_Version::supports_evex(), ""));
  int src_enc = src->encoding();
  int nds_enc = 0;
  vex_prefix(dst, nds_enc, src_enc, VEX_SIMD_NONE,
             VEX_OPCODE_0F, /* vex_w */ true, AVX_128bit, /* legacy_mode */ true, /* no_reg_mask */ true);
  emit_int8((unsigned char)0x90);
  emit_operand((Register)src, dst);
}

void Assembler::kmovql(KRegister dst, Register src) {
  NOT_LP64(assert(VM_Version::supports_evex(), ""));
  VexSimdPrefix pre = !_legacy_mode_bw ? VEX_SIMD_F2 : VEX_SIMD_NONE;
  int encode = kreg_prefix_and_encode(dst, knoreg, src, pre, /* no_mask_reg */ true,
                                      VEX_OPCODE_0F, /* legacy_mode */ !_legacy_mode_bw);
  emit_int8((unsigned char)0x92);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::kmovdl(KRegister dst, Register src) {
  NOT_LP64(assert(VM_Version::supports_evex(), ""));
  VexSimdPrefix pre = !_legacy_mode_bw ? VEX_SIMD_F2 : VEX_SIMD_NONE;
  int encode = kreg_prefix_and_encode(dst, knoreg, src, pre, /* no_mask_reg */ true);
  emit_int8((unsigned char)0x92);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::kmovwl(KRegister dst, Register src) {
  NOT_LP64(assert(VM_Version::supports_evex(), ""));
  int encode = kreg_prefix_and_encode(dst, knoreg, src, VEX_SIMD_NONE, /* no_mask_reg */ true);
  emit_int8((unsigned char)0x92);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::movb(Address dst, int imm8) {
  InstructionMark im(this);
   prefix(dst);
  emit_int8((unsigned char)0xC6);
  emit_operand(rax, dst, 1);
  emit_int8(imm8);
}


void Assembler::movb(Address dst, Register src) {
  assert(src->has_byte_register(), "must have byte register");
  InstructionMark im(this);
  prefix(dst, src, true);
  emit_int8((unsigned char)0x88);
  emit_operand(src, dst);
}

void Assembler::movdl(XMMRegister dst, Register src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  int encode = simd_prefix_and_encode(dst, src, VEX_SIMD_66, /* no_mask_reg */ true);
  emit_int8(0x6E);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::movdl(Register dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  // swap src/dst to get correct prefix
  int encode = simd_prefix_and_encode(src, dst, VEX_SIMD_66, /* no_mask_reg */ true);
  emit_int8(0x7E);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::movdl(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  InstructionMark im(this);
  simd_prefix(dst, src, VEX_SIMD_66, /* no_reg_mask */ true);
  emit_int8(0x6E);
  emit_operand(dst, src);
}

void Assembler::movdl(Address dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  InstructionMark im(this);
  simd_prefix(dst, src, VEX_SIMD_66, /* no_reg_mask */ true);
  emit_int8(0x7E);
  emit_operand(src, dst);
}

void Assembler::movdqa(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith_nonds(0x6F, dst, src, VEX_SIMD_66);
}

void Assembler::movdqa(XMMRegister dst, Address src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FVM;
  }
  emit_simd_arith_nonds(0x6F, dst, src, VEX_SIMD_66);
}

void Assembler::movdqu(XMMRegister dst, Address src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FVM;
  }
  emit_simd_arith_nonds(0x6F, dst, src, VEX_SIMD_F3);
}

void Assembler::movdqu(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith_nonds(0x6F, dst, src, VEX_SIMD_F3);
}

void Assembler::movdqu(Address dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FVM;
  }
  InstructionMark im(this);
  simd_prefix(dst, src, VEX_SIMD_F3, /* no_mask_reg */ false);
  emit_int8(0x7F);
  emit_operand(src, dst);
}

// Move Unaligned 256bit Vector
void Assembler::vmovdqu(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "");
  int vector_len = AVX_256bit;
  int encode = vex_prefix_and_encode(dst, xnoreg, src, VEX_SIMD_F3, vector_len);
  emit_int8(0x6F);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::vmovdqu(XMMRegister dst, Address src) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FVM;
  }
  InstructionMark im(this);
  int vector_len = AVX_256bit;
  vex_prefix(dst, xnoreg, src, VEX_SIMD_F3, vector_len);
  emit_int8(0x6F);
  emit_operand(dst, src);
}

void Assembler::vmovdqu(Address dst, XMMRegister src) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FVM;
  }
  InstructionMark im(this);
  int vector_len = AVX_256bit;
  // swap src<->dst for encoding
  assert(src != xnoreg, "sanity");
  vex_prefix(src, xnoreg, dst, VEX_SIMD_F3, vector_len);
  emit_int8(0x7F);
  emit_operand(src, dst);
}

// Move Unaligned EVEX enabled Vector (programmable : 8,16,32,64)
void Assembler::evmovdqul(XMMRegister dst, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "");
  int src_enc = src->encoding();
  int dst_enc = dst->encoding();
  int encode = vex_prefix_and_encode(dst_enc, 0, src_enc, VEX_SIMD_F3, VEX_OPCODE_0F,
                                     /* vex_w */ false, vector_len, /* legacy_mode */ false, /* no_mask_reg */ false);
  emit_int8(0x6F);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::evmovdqul(XMMRegister dst, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "");
  InstructionMark im(this);
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FVM;
  }
  vex_prefix(dst, xnoreg, src, VEX_SIMD_F3, vector_len);
  emit_int8(0x6F);
  emit_operand(dst, src);
}

void Assembler::evmovdqul(Address dst, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "");
  InstructionMark im(this);
  assert(src != xnoreg, "sanity");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FVM;
  }
  // swap src<->dst for encoding
  vex_prefix(src, xnoreg, dst, VEX_SIMD_F3, vector_len);
  emit_int8(0x7F);
  emit_operand(src, dst);
}

void Assembler::evmovdquq(XMMRegister dst, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "");
  int src_enc = src->encoding();
  int dst_enc = dst->encoding();
  int encode = vex_prefix_and_encode(dst_enc, 0, src_enc, VEX_SIMD_F3, VEX_OPCODE_0F,
                                     /* vex_w */ true, vector_len, /* legacy_mode */ false, /* no_mask_reg */ false);
  emit_int8(0x6F);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::evmovdquq(XMMRegister dst, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 2, "");
  InstructionMark im(this);
  _tuple_type = EVEX_FVM;
  vex_prefix_q(dst, xnoreg, src, VEX_SIMD_F3, vector_len);
  emit_int8(0x6F);
  emit_operand(dst, src);
}

void Assembler::evmovdquq(Address dst, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 2, "");
  InstructionMark im(this);
  assert(src != xnoreg, "sanity");
  _tuple_type = EVEX_FVM;
  // swap src<->dst for encoding
  vex_prefix_q(src, xnoreg, dst, VEX_SIMD_F3, vector_len);
  emit_int8(0x7F);
  emit_operand(src, dst);
}

// Uses zero extension on 64bit

void Assembler::movl(Register dst, int32_t imm32) {
  int encode = prefix_and_encode(dst->encoding());
  emit_int8((unsigned char)(0xB8 | encode));
  emit_int32(imm32);
}

void Assembler::movl(Register dst, Register src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_int8((unsigned char)0x8B);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::movl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_int8((unsigned char)0x8B);
  emit_operand(dst, src);
}

void Assembler::movl(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefix(dst);
  emit_int8((unsigned char)0xC7);
  emit_operand(rax, dst, 4);
  emit_int32(imm32);
}

void Assembler::movl(Address dst, Register src) {
  InstructionMark im(this);
  prefix(dst, src);
  emit_int8((unsigned char)0x89);
  emit_operand(src, dst);
}

// New cpus require to use movsd and movss to avoid partial register stall
// when loading from memory. But for old Opteron use movlpd instead of movsd.
// The selection is done in MacroAssembler::movdbl() and movflt().
void Assembler::movlpd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
    emit_simd_arith_q(0x12, dst, src, VEX_SIMD_66, /* no_mask_reg */ true);
  } else {
    emit_simd_arith(0x12, dst, src, VEX_SIMD_66, /* no_mask_reg */ true);
  }
}

void Assembler::movq( MMXRegister dst, Address src ) {
  assert( VM_Version::supports_mmx(), "" );
  emit_int8(0x0F);
  emit_int8(0x6F);
  emit_operand(dst, src);
}

void Assembler::movq( Address dst, MMXRegister src ) {
  assert( VM_Version::supports_mmx(), "" );
  emit_int8(0x0F);
  emit_int8(0x7F);
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
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_64bit;
    simd_prefix_q(dst, xnoreg, src, VEX_SIMD_F3, /* no_mask_reg */ true);
  } else {
    simd_prefix(dst, src, VEX_SIMD_F3, /* no_mask_reg */ true);
  }
  emit_int8(0x7E);
  emit_operand(dst, src);
}

void Assembler::movq(Address dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_64bit;
    simd_prefix(src, xnoreg, dst, VEX_SIMD_66, /* no_mask_reg */ true,
                VEX_OPCODE_0F, /* rex_w */ true);
  } else {
    simd_prefix(dst, src, VEX_SIMD_66, /* no_mask_reg */ true);
  }
  emit_int8((unsigned char)0xD6);
  emit_operand(src, dst);
}

void Assembler::movsbl(Register dst, Address src) { // movsxb
  InstructionMark im(this);
  prefix(src, dst);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xBE);
  emit_operand(dst, src);
}

void Assembler::movsbl(Register dst, Register src) { // movsxb
  NOT_LP64(assert(src->has_byte_register(), "must have byte register"));
  int encode = prefix_and_encode(dst->encoding(), src->encoding(), true);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xBE);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::movsd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0x10, dst, src, VEX_SIMD_F2, /* no_mask_reg */ true);
  } else {
    emit_simd_arith(0x10, dst, src, VEX_SIMD_F2);
  }
}

void Assembler::movsd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_64bit;
    emit_simd_arith_nonds_q(0x10, dst, src, VEX_SIMD_F2, /* no_mask_reg */ true);
  } else {
    emit_simd_arith_nonds(0x10, dst, src, VEX_SIMD_F2);
  }
}

void Assembler::movsd(Address dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  InstructionMark im(this);
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_64bit;
    simd_prefix_q(src, xnoreg, dst, VEX_SIMD_F2);
  } else {
    simd_prefix(src, xnoreg, dst, VEX_SIMD_F2, /* no_mask_reg */ false);
  }
  emit_int8(0x11);
  emit_operand(src, dst);
}

void Assembler::movss(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_simd_arith(0x10, dst, src, VEX_SIMD_F3, /* no_mask_reg */ true);
}

void Assembler::movss(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_simd_arith_nonds(0x10, dst, src, VEX_SIMD_F3, /* no_mask_reg */ true);
}

void Assembler::movss(Address dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  InstructionMark im(this);
  simd_prefix(dst, src, VEX_SIMD_F3, /* no_mask_reg */ false);
  emit_int8(0x11);
  emit_operand(src, dst);
}

void Assembler::movswl(Register dst, Address src) { // movsxw
  InstructionMark im(this);
  prefix(src, dst);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xBF);
  emit_operand(dst, src);
}

void Assembler::movswl(Register dst, Register src) { // movsxw
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)0xBF);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::movw(Address dst, int imm16) {
  InstructionMark im(this);

  emit_int8(0x66); // switch to 16-bit mode
  prefix(dst);
  emit_int8((unsigned char)0xC7);
  emit_operand(rax, dst, 2);
  emit_int16(imm16);
}

void Assembler::movw(Register dst, Address src) {
  InstructionMark im(this);
  emit_int8(0x66);
  prefix(src, dst);
  emit_int8((unsigned char)0x8B);
  emit_operand(dst, src);
}

void Assembler::movw(Address dst, Register src) {
  InstructionMark im(this);
  emit_int8(0x66);
  prefix(dst, src);
  emit_int8((unsigned char)0x89);
  emit_operand(src, dst);
}

void Assembler::movzbl(Register dst, Address src) { // movzxb
  InstructionMark im(this);
  prefix(src, dst);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xB6);
  emit_operand(dst, src);
}

void Assembler::movzbl(Register dst, Register src) { // movzxb
  NOT_LP64(assert(src->has_byte_register(), "must have byte register"));
  int encode = prefix_and_encode(dst->encoding(), src->encoding(), true);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xB6);
  emit_int8(0xC0 | encode);
}

void Assembler::movzwl(Register dst, Address src) { // movzxw
  InstructionMark im(this);
  prefix(src, dst);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xB7);
  emit_operand(dst, src);
}

void Assembler::movzwl(Register dst, Register src) { // movzxw
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)0xB7);
  emit_int8(0xC0 | encode);
}

void Assembler::mull(Address src) {
  InstructionMark im(this);
  prefix(src);
  emit_int8((unsigned char)0xF7);
  emit_operand(rsp, src);
}

void Assembler::mull(Register src) {
  int encode = prefix_and_encode(src->encoding());
  emit_int8((unsigned char)0xF7);
  emit_int8((unsigned char)(0xE0 | encode));
}

void Assembler::mulsd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_64bit;
    emit_simd_arith_q(0x59, dst, src, VEX_SIMD_F2);
  } else {
    emit_simd_arith(0x59, dst, src, VEX_SIMD_F2);
  }
}

void Assembler::mulsd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0x59, dst, src, VEX_SIMD_F2);
  } else {
    emit_simd_arith(0x59, dst, src, VEX_SIMD_F2);
  }
}

void Assembler::mulss(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_simd_arith(0x59, dst, src, VEX_SIMD_F3);
}

void Assembler::mulss(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_simd_arith(0x59, dst, src, VEX_SIMD_F3);
}

void Assembler::negl(Register dst) {
  int encode = prefix_and_encode(dst->encoding());
  emit_int8((unsigned char)0xF7);
  emit_int8((unsigned char)(0xD8 | encode));
}

void Assembler::nop(int i) {
#ifdef ASSERT
  assert(i > 0, " ");
  // The fancy nops aren't currently recognized by debuggers making it a
  // pain to disassemble code while debugging. If asserts are on clearly
  // speed is not an issue so simply use the single byte traditional nop
  // to do alignment.

  for (; i > 0 ; i--) emit_int8((unsigned char)0x90);
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
      emit_int8(0x66);   // size prefix
      emit_int8(0x66);   // size prefix
      emit_int8(0x66);   // size prefix
      addr_nop_8();
      emit_int8(0x66);   // size prefix
      emit_int8(0x66);   // size prefix
      emit_int8(0x66);   // size prefix
      emit_int8((unsigned char)0x90);
                         // nop
    }
    switch (i) {
      case 14:
        emit_int8(0x66); // size prefix
      case 13:
        emit_int8(0x66); // size prefix
      case 12:
        addr_nop_8();
        emit_int8(0x66); // size prefix
        emit_int8(0x66); // size prefix
        emit_int8(0x66); // size prefix
        emit_int8((unsigned char)0x90);
                         // nop
        break;
      case 11:
        emit_int8(0x66); // size prefix
      case 10:
        emit_int8(0x66); // size prefix
      case 9:
        emit_int8(0x66); // size prefix
      case 8:
        addr_nop_8();
        break;
      case 7:
        addr_nop_7();
        break;
      case 6:
        emit_int8(0x66); // size prefix
      case 5:
        addr_nop_5();
        break;
      case 4:
        addr_nop_4();
        break;
      case 3:
        // Don't use "0x0F 0x1F 0x00" - need patching safe padding
        emit_int8(0x66); // size prefix
      case 2:
        emit_int8(0x66); // size prefix
      case 1:
        emit_int8((unsigned char)0x90);
                         // nop
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
      emit_int8(0x66); // size prefix
      emit_int8(0x66); // size prefix
      emit_int8(0x66); // size prefix
      addr_nop_8();
    }
    // Generate first nop for size between 21-12
    switch (i) {
      case 21:
        i -= 1;
        emit_int8(0x66); // size prefix
      case 20:
      case 19:
        i -= 1;
        emit_int8(0x66); // size prefix
      case 18:
      case 17:
        i -= 1;
        emit_int8(0x66); // size prefix
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
        emit_int8(0x66); // size prefix
        addr_nop_5();
        break;
      default:
        assert(i < 12, " ");
    }

    // Generate second nop for size between 11-1
    switch (i) {
      case 11:
        emit_int8(0x66); // size prefix
      case 10:
        emit_int8(0x66); // size prefix
      case 9:
        emit_int8(0x66); // size prefix
      case 8:
        addr_nop_8();
        break;
      case 7:
        addr_nop_7();
        break;
      case 6:
        emit_int8(0x66); // size prefix
      case 5:
        addr_nop_5();
        break;
      case 4:
        addr_nop_4();
        break;
      case 3:
        // Don't use "0x0F 0x1F 0x00" - need patching safe padding
        emit_int8(0x66); // size prefix
      case 2:
        emit_int8(0x66); // size prefix
      case 1:
        emit_int8((unsigned char)0x90);
                         // nop
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
    emit_int8(0x66); // size prefix
    emit_int8(0x66);
    emit_int8(0x66);
    emit_int8((unsigned char)0x90);
                     // nop
  }
  // 1 - 12 nops
  if(i > 8) {
    if(i > 9) {
      i -= 1;
      emit_int8(0x66);
    }
    i -= 3;
    emit_int8(0x66);
    emit_int8(0x66);
    emit_int8((unsigned char)0x90);
  }
  // 1 - 8 nops
  if(i > 4) {
    if(i > 6) {
      i -= 1;
      emit_int8(0x66);
    }
    i -= 3;
    emit_int8(0x66);
    emit_int8(0x66);
    emit_int8((unsigned char)0x90);
  }
  switch (i) {
    case 4:
      emit_int8(0x66);
    case 3:
      emit_int8(0x66);
    case 2:
      emit_int8(0x66);
    case 1:
      emit_int8((unsigned char)0x90);
      break;
    default:
      assert(i == 0, " ");
  }
}

void Assembler::notl(Register dst) {
  int encode = prefix_and_encode(dst->encoding());
  emit_int8((unsigned char)0xF7);
  emit_int8((unsigned char)(0xD0 | encode));
}

void Assembler::orl(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefix(dst);
  emit_arith_operand(0x81, rcx, dst, imm32);
}

void Assembler::orl(Register dst, int32_t imm32) {
  prefix(dst);
  emit_arith(0x81, 0xC8, dst, imm32);
}

void Assembler::orl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_int8(0x0B);
  emit_operand(dst, src);
}

void Assembler::orl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x0B, 0xC0, dst, src);
}

void Assembler::orl(Address dst, Register src) {
  InstructionMark im(this);
  prefix(dst, src);
  emit_int8(0x09);
  emit_operand(src, dst);
}

void Assembler::packuswb(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  assert((UseAVX > 0), "SSE mode requires address alignment 16 bytes");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_simd_arith(0x67, dst, src, VEX_SIMD_66, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::packuswb(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0x67, dst, src, VEX_SIMD_66, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::vpackuswb(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(UseAVX > 0, "some form of AVX must be enabled");
  emit_vex_arith(0x67, dst, nds, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::vpermq(XMMRegister dst, XMMRegister src, int imm8, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx2(), "");
  int encode = simd_prefix_and_encode(dst, xnoreg, src, VEX_SIMD_66, /* no_mask_reg */ false,
                                      VEX_OPCODE_0F_3A, /* rex_w */ true, vector_len);
  emit_int8(0x00);
  emit_int8(0xC0 | encode);
  emit_int8(imm8);
}

void Assembler::pause() {
  emit_int8((unsigned char)0xF3);
  emit_int8((unsigned char)0x90);
}

void Assembler::pcmpestri(XMMRegister dst, Address src, int imm8) {
  assert(VM_Version::supports_sse4_2(), "");
  InstructionMark im(this);
  simd_prefix(dst, xnoreg, src, VEX_SIMD_66, /* no_mask_reg */ false, VEX_OPCODE_0F_3A,
              /* rex_w */ false, AVX_128bit, /* legacy_mode */ true);
  emit_int8(0x61);
  emit_operand(dst, src);
  emit_int8(imm8);
}

void Assembler::pcmpestri(XMMRegister dst, XMMRegister src, int imm8) {
  assert(VM_Version::supports_sse4_2(), "");
  int encode = simd_prefix_and_encode(dst, xnoreg, src, VEX_SIMD_66, /* no_mask_reg */ false,
                                      VEX_OPCODE_0F_3A, /* rex_w */ false, AVX_128bit, /* legacy_mode */ true);
  emit_int8(0x61);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8(imm8);
}

void Assembler::pextrd(Register dst, XMMRegister src, int imm8) {
  assert(VM_Version::supports_sse4_1(), "");
  int encode = simd_prefix_and_encode(as_XMMRegister(dst->encoding()), xnoreg, src, VEX_SIMD_66, /* no_mask_reg */ true,
                                      VEX_OPCODE_0F_3A, /* rex_w */ false, AVX_128bit, /* legacy_mode */ _legacy_mode_dq);
  emit_int8(0x16);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8(imm8);
}

void Assembler::pextrq(Register dst, XMMRegister src, int imm8) {
  assert(VM_Version::supports_sse4_1(), "");
  int encode = simd_prefix_and_encode(as_XMMRegister(dst->encoding()), xnoreg, src, VEX_SIMD_66, /* no_mask_reg */  true,
                                      VEX_OPCODE_0F_3A, /* rex_w */ true, AVX_128bit, /* legacy_mode */ _legacy_mode_dq);
  emit_int8(0x16);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8(imm8);
}

void Assembler::pinsrd(XMMRegister dst, Register src, int imm8) {
  assert(VM_Version::supports_sse4_1(), "");
  int encode = simd_prefix_and_encode(dst, dst, as_XMMRegister(src->encoding()), VEX_SIMD_66, /* no_mask_reg */ true,
                                      VEX_OPCODE_0F_3A, /* rex_w */ false, AVX_128bit, /* legacy_mode */ _legacy_mode_dq);
  emit_int8(0x22);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8(imm8);
}

void Assembler::pinsrq(XMMRegister dst, Register src, int imm8) {
  assert(VM_Version::supports_sse4_1(), "");
  int encode = simd_prefix_and_encode(dst, dst, as_XMMRegister(src->encoding()), VEX_SIMD_66, /* no_mask_reg */ true,
                                      VEX_OPCODE_0F_3A, /* rex_w */ true, AVX_128bit, /* legacy_mode */ _legacy_mode_dq);
  emit_int8(0x22);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8(imm8);
}

void Assembler::pmovzxbw(XMMRegister dst, Address src) {
  assert(VM_Version::supports_sse4_1(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_HVM;
  }
  InstructionMark im(this);
  simd_prefix(dst, src, VEX_SIMD_66, /* no_mask_reg */ false, VEX_OPCODE_0F_38);
  emit_int8(0x30);
  emit_operand(dst, src);
}

void Assembler::pmovzxbw(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_sse4_1(), "");
  int encode = simd_prefix_and_encode(dst, xnoreg, src, VEX_SIMD_66, /* no_mask_reg */ false, VEX_OPCODE_0F_38);
  emit_int8(0x30);
  emit_int8((unsigned char)(0xC0 | encode));
}

// generic
void Assembler::pop(Register dst) {
  int encode = prefix_and_encode(dst->encoding());
  emit_int8(0x58 | encode);
}

void Assembler::popcntl(Register dst, Address src) {
  assert(VM_Version::supports_popcnt(), "must support");
  InstructionMark im(this);
  emit_int8((unsigned char)0xF3);
  prefix(src, dst);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xB8);
  emit_operand(dst, src);
}

void Assembler::popcntl(Register dst, Register src) {
  assert(VM_Version::supports_popcnt(), "must support");
  emit_int8((unsigned char)0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)0xB8);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::popf() {
  emit_int8((unsigned char)0x9D);
}

#ifndef _LP64 // no 32bit push/pop on amd64
void Assembler::popl(Address dst) {
  // NOTE: this will adjust stack by 8byte on 64bits
  InstructionMark im(this);
  prefix(dst);
  emit_int8((unsigned char)0x8F);
  emit_operand(rax, dst);
}
#endif

void Assembler::prefetch_prefix(Address src) {
  prefix(src);
  emit_int8(0x0F);
}

void Assembler::prefetchnta(Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), "must support"));
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_int8(0x18);
  emit_operand(rax, src); // 0, src
}

void Assembler::prefetchr(Address src) {
  assert(VM_Version::supports_3dnow_prefetch(), "must support");
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_int8(0x0D);
  emit_operand(rax, src); // 0, src
}

void Assembler::prefetcht0(Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), "must support"));
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_int8(0x18);
  emit_operand(rcx, src); // 1, src
}

void Assembler::prefetcht1(Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), "must support"));
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_int8(0x18);
  emit_operand(rdx, src); // 2, src
}

void Assembler::prefetcht2(Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), "must support"));
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_int8(0x18);
  emit_operand(rbx, src); // 3, src
}

void Assembler::prefetchw(Address src) {
  assert(VM_Version::supports_3dnow_prefetch(), "must support");
  InstructionMark im(this);
  prefetch_prefix(src);
  emit_int8(0x0D);
  emit_operand(rcx, src); // 1, src
}

void Assembler::prefix(Prefix p) {
  emit_int8(p);
}

void Assembler::pshufb(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_ssse3(), "");
  int encode = simd_prefix_and_encode(dst, dst, src, VEX_SIMD_66, /* no_mask_reg */ false,
                                      VEX_OPCODE_0F_38, /* rex_w */ false, AVX_128bit, /* legacy_mode */ _legacy_mode_bw);
  emit_int8(0x00);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::pshufb(XMMRegister dst, Address src) {
  assert(VM_Version::supports_ssse3(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FVM;
  }
  InstructionMark im(this);
  simd_prefix(dst, dst, src, VEX_SIMD_66, /* no_mask_reg */ false,
              VEX_OPCODE_0F_38, /* rex_w */ false, AVX_128bit, /* legacy_mode */ _legacy_mode_bw);
  emit_int8(0x00);
  emit_operand(dst, src);
}

void Assembler::pshufd(XMMRegister dst, XMMRegister src, int mode) {
  _instruction_uses_vl = true;
  assert(isByte(mode), "invalid value");
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith_nonds(0x70, dst, src, VEX_SIMD_66);
  emit_int8(mode & 0xFF);
}

void Assembler::pshufd(XMMRegister dst, Address src, int mode) {
  _instruction_uses_vl = true;
  assert(isByte(mode), "invalid value");
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  assert((UseAVX > 0), "SSE mode requires address alignment 16 bytes");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  InstructionMark im(this);
  simd_prefix(dst, src, VEX_SIMD_66, /* no_mask_reg */ false);
  emit_int8(0x70);
  emit_operand(dst, src);
  emit_int8(mode & 0xFF);
}

void Assembler::pshuflw(XMMRegister dst, XMMRegister src, int mode) {
  assert(isByte(mode), "invalid value");
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith_nonds(0x70, dst, src, VEX_SIMD_F2, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
  emit_int8(mode & 0xFF);
}

void Assembler::pshuflw(XMMRegister dst, Address src, int mode) {
  assert(isByte(mode), "invalid value");
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  assert((UseAVX > 0), "SSE mode requires address alignment 16 bytes");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FVM;
  }
  InstructionMark im(this);
  simd_prefix(dst, xnoreg, src, VEX_SIMD_F2, /* no_mask_reg */ false,
              VEX_OPCODE_0F, /* rex_w */ false, AVX_128bit, /* legacy_mode */ _legacy_mode_bw);
  emit_int8(0x70);
  emit_operand(dst, src);
  emit_int8(mode & 0xFF);
}

void Assembler::psrldq(XMMRegister dst, int shift) {
  // Shift left 128 bit value in dst XMMRegister by shift number of bytes.
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  // XMM3 is for /3 encoding: 66 0F 73 /3 ib
  int encode = simd_prefix_and_encode(xmm3, dst, dst, VEX_SIMD_66, /* no_mask_reg */ true,
                                      VEX_OPCODE_0F, /* rex_w */ false, AVX_128bit, /* legacy_mode */ _legacy_mode_bw);
  emit_int8(0x73);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8(shift);
}

void Assembler::pslldq(XMMRegister dst, int shift) {
  // Shift left 128 bit value in dst XMMRegister by shift number of bytes.
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  // XMM7 is for /7 encoding: 66 0F 73 /7 ib
  int encode = simd_prefix_and_encode(xmm7, dst, dst, VEX_SIMD_66, /* no_mask_reg */ true,
                                      VEX_OPCODE_0F, /* rex_w */ false, AVX_128bit, /* legacy_mode */ _legacy_mode_bw);
  emit_int8(0x73);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8(shift);
}

void Assembler::ptest(XMMRegister dst, Address src) {
  assert(VM_Version::supports_sse4_1(), "");
  assert((UseAVX > 0), "SSE mode requires address alignment 16 bytes");
  InstructionMark im(this);
  simd_prefix(dst, xnoreg, src, VEX_SIMD_66, /* no_mask_reg */ false,
              VEX_OPCODE_0F_38, /* rex_w */ false, AVX_128bit, /* legacy_mode */ true);
  emit_int8(0x17);
  emit_operand(dst, src);
}

void Assembler::ptest(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_sse4_1(), "");
  int encode = simd_prefix_and_encode(dst, xnoreg, src, VEX_SIMD_66, /* no_mask_reg */ false,
                                      VEX_OPCODE_0F_38, /* rex_w */ false, AVX_128bit, /* legacy_mode */ true);
  emit_int8(0x17);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::vptest(XMMRegister dst, Address src) {
  assert(VM_Version::supports_avx(), "");
  InstructionMark im(this);
  int vector_len = AVX_256bit;
  assert(dst != xnoreg, "sanity");
  int dst_enc = dst->encoding();
  // swap src<->dst for encoding
  vex_prefix(src, 0, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_38, /* rex_w */ false,
             vector_len, /* legacy_mode  */ true, /* no_mask_reg */ false);
  emit_int8(0x17);
  emit_operand(dst, src);
}

void Assembler::vptest(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_avx(), "");
  int vector_len = AVX_256bit;
  int encode = vex_prefix_and_encode(dst, xnoreg, src, VEX_SIMD_66, vector_len, VEX_OPCODE_0F_38, /* legacy_mode */ true);
  emit_int8(0x17);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::punpcklbw(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  assert((UseAVX > 0), "SSE mode requires address alignment 16 bytes");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FVM;
  }
  emit_simd_arith(0x60, dst, src, VEX_SIMD_66, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_vlbw);
}

void Assembler::punpcklbw(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0x60, dst, src, VEX_SIMD_66, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_vlbw);
}

void Assembler::punpckldq(XMMRegister dst, Address src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  assert((UseAVX > 0), "SSE mode requires address alignment 16 bytes");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_simd_arith(0x62, dst, src, VEX_SIMD_66);
}

void Assembler::punpckldq(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0x62, dst, src, VEX_SIMD_66);
}

void Assembler::punpcklqdq(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0x6C, dst, src, VEX_SIMD_66);
  } else {
    emit_simd_arith(0x6C, dst, src, VEX_SIMD_66);
  }
}

void Assembler::push(int32_t imm32) {
  // in 64bits we push 64bits onto the stack but only
  // take a 32bit immediate
  emit_int8(0x68);
  emit_int32(imm32);
}

void Assembler::push(Register src) {
  int encode = prefix_and_encode(src->encoding());

  emit_int8(0x50 | encode);
}

void Assembler::pushf() {
  emit_int8((unsigned char)0x9C);
}

#ifndef _LP64 // no 32bit push/pop on amd64
void Assembler::pushl(Address src) {
  // Note this will push 64bit on 64bit
  InstructionMark im(this);
  prefix(src);
  emit_int8((unsigned char)0xFF);
  emit_operand(rsi, src);
}
#endif

void Assembler::rcll(Register dst, int imm8) {
  assert(isShiftCount(imm8), "illegal shift count");
  int encode = prefix_and_encode(dst->encoding());
  if (imm8 == 1) {
    emit_int8((unsigned char)0xD1);
    emit_int8((unsigned char)(0xD0 | encode));
  } else {
    emit_int8((unsigned char)0xC1);
    emit_int8((unsigned char)0xD0 | encode);
    emit_int8(imm8);
  }
}

void Assembler::rdtsc() {
  emit_int8((unsigned char)0x0F);
  emit_int8((unsigned char)0x31);
}

// copies data from [esi] to [edi] using rcx pointer sized words
// generic
void Assembler::rep_mov() {
  emit_int8((unsigned char)0xF3);
  // MOVSQ
  LP64_ONLY(prefix(REX_W));
  emit_int8((unsigned char)0xA5);
}

// sets rcx bytes with rax, value at [edi]
void Assembler::rep_stosb() {
  emit_int8((unsigned char)0xF3); // REP
  LP64_ONLY(prefix(REX_W));
  emit_int8((unsigned char)0xAA); // STOSB
}

// sets rcx pointer sized words with rax, value at [edi]
// generic
void Assembler::rep_stos() {
  emit_int8((unsigned char)0xF3); // REP
  LP64_ONLY(prefix(REX_W));       // LP64:STOSQ, LP32:STOSD
  emit_int8((unsigned char)0xAB);
}

// scans rcx pointer sized words at [edi] for occurance of rax,
// generic
void Assembler::repne_scan() { // repne_scan
  emit_int8((unsigned char)0xF2);
  // SCASQ
  LP64_ONLY(prefix(REX_W));
  emit_int8((unsigned char)0xAF);
}

#ifdef _LP64
// scans rcx 4 byte words at [edi] for occurance of rax,
// generic
void Assembler::repne_scanl() { // repne_scan
  emit_int8((unsigned char)0xF2);
  // SCASL
  emit_int8((unsigned char)0xAF);
}
#endif

void Assembler::ret(int imm16) {
  if (imm16 == 0) {
    emit_int8((unsigned char)0xC3);
  } else {
    emit_int8((unsigned char)0xC2);
    emit_int16(imm16);
  }
}

void Assembler::sahf() {
#ifdef _LP64
  // Not supported in 64bit mode
  ShouldNotReachHere();
#endif
  emit_int8((unsigned char)0x9E);
}

void Assembler::sarl(Register dst, int imm8) {
  int encode = prefix_and_encode(dst->encoding());
  assert(isShiftCount(imm8), "illegal shift count");
  if (imm8 == 1) {
    emit_int8((unsigned char)0xD1);
    emit_int8((unsigned char)(0xF8 | encode));
  } else {
    emit_int8((unsigned char)0xC1);
    emit_int8((unsigned char)(0xF8 | encode));
    emit_int8(imm8);
  }
}

void Assembler::sarl(Register dst) {
  int encode = prefix_and_encode(dst->encoding());
  emit_int8((unsigned char)0xD3);
  emit_int8((unsigned char)(0xF8 | encode));
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
  emit_int8(0x1B);
  emit_operand(dst, src);
}

void Assembler::sbbl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x1B, 0xC0, dst, src);
}

void Assembler::setb(Condition cc, Register dst) {
  assert(0 <= cc && cc < 16, "illegal cc");
  int encode = prefix_and_encode(dst->encoding(), true);
  emit_int8(0x0F);
  emit_int8((unsigned char)0x90 | cc);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::shll(Register dst, int imm8) {
  assert(isShiftCount(imm8), "illegal shift count");
  int encode = prefix_and_encode(dst->encoding());
  if (imm8 == 1 ) {
    emit_int8((unsigned char)0xD1);
    emit_int8((unsigned char)(0xE0 | encode));
  } else {
    emit_int8((unsigned char)0xC1);
    emit_int8((unsigned char)(0xE0 | encode));
    emit_int8(imm8);
  }
}

void Assembler::shll(Register dst) {
  int encode = prefix_and_encode(dst->encoding());
  emit_int8((unsigned char)0xD3);
  emit_int8((unsigned char)(0xE0 | encode));
}

void Assembler::shrl(Register dst, int imm8) {
  assert(isShiftCount(imm8), "illegal shift count");
  int encode = prefix_and_encode(dst->encoding());
  emit_int8((unsigned char)0xC1);
  emit_int8((unsigned char)(0xE8 | encode));
  emit_int8(imm8);
}

void Assembler::shrl(Register dst) {
  int encode = prefix_and_encode(dst->encoding());
  emit_int8((unsigned char)0xD3);
  emit_int8((unsigned char)(0xE8 | encode));
}

// copies a single word from [esi] to [edi]
void Assembler::smovl() {
  emit_int8((unsigned char)0xA5);
}

void Assembler::sqrtsd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0x51, dst, src, VEX_SIMD_F2);
  } else {
    emit_simd_arith(0x51, dst, src, VEX_SIMD_F2);
  }
}

void Assembler::sqrtsd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_64bit;
    emit_simd_arith_q(0x51, dst, src, VEX_SIMD_F2);
  } else {
    emit_simd_arith(0x51, dst, src, VEX_SIMD_F2);
  }
}

void Assembler::sqrtss(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_simd_arith(0x51, dst, src, VEX_SIMD_F3);
}

void Assembler::std() {
  emit_int8((unsigned char)0xFD);
}

void Assembler::sqrtss(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_simd_arith(0x51, dst, src, VEX_SIMD_F3);
}

void Assembler::stmxcsr( Address dst) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  InstructionMark im(this);
  prefix(dst);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xAE);
  emit_operand(as_Register(3), dst);
}

void Assembler::subl(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefix(dst);
  emit_arith_operand(0x81, rbp, dst, imm32);
}

void Assembler::subl(Address dst, Register src) {
  InstructionMark im(this);
  prefix(dst, src);
  emit_int8(0x29);
  emit_operand(src, dst);
}

void Assembler::subl(Register dst, int32_t imm32) {
  prefix(dst);
  emit_arith(0x81, 0xE8, dst, imm32);
}

// Force generation of a 4 byte immediate value even if it fits into 8bit
void Assembler::subl_imm32(Register dst, int32_t imm32) {
  prefix(dst);
  emit_arith_imm32(0x81, 0xE8, dst, imm32);
}

void Assembler::subl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_int8(0x2B);
  emit_operand(dst, src);
}

void Assembler::subl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x2B, 0xC0, dst, src);
}

void Assembler::subsd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0x5C, dst, src, VEX_SIMD_F2);
  } else {
    emit_simd_arith(0x5C, dst, src, VEX_SIMD_F2);
  }
}

void Assembler::subsd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_64bit;
  }
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0x5C, dst, src, VEX_SIMD_F2);
  } else {
    emit_simd_arith(0x5C, dst, src, VEX_SIMD_F2);
  }
}

void Assembler::subss(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_simd_arith(0x5C, dst, src, VEX_SIMD_F3);
}

void Assembler::subss(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_simd_arith(0x5C, dst, src, VEX_SIMD_F3);
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
    emit_int8((unsigned char)0xA9);
  } else {
    encode = prefix_and_encode(encode);
    emit_int8((unsigned char)0xF7);
    emit_int8((unsigned char)(0xC0 | encode));
  }
  emit_int32(imm32);
}

void Assembler::testl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x85, 0xC0, dst, src);
}

void Assembler::testl(Register dst, Address  src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_int8((unsigned char)0x85);
  emit_operand(dst, src);
}

void Assembler::tzcntl(Register dst, Register src) {
  assert(VM_Version::supports_bmi1(), "tzcnt instruction not supported");
  emit_int8((unsigned char)0xF3);
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)0xBC);
  emit_int8((unsigned char)0xC0 | encode);
}

void Assembler::tzcntq(Register dst, Register src) {
  assert(VM_Version::supports_bmi1(), "tzcnt instruction not supported");
  emit_int8((unsigned char)0xF3);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)0xBC);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::ucomisd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_64bit;
    emit_simd_arith_nonds_q(0x2E, dst, src, VEX_SIMD_66, /* no_mask_reg */ true);
  } else {
    emit_simd_arith_nonds(0x2E, dst, src, VEX_SIMD_66);
  }
}

void Assembler::ucomisd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_nonds_q(0x2E, dst, src, VEX_SIMD_66, /* no_mask_reg */ true);
  } else {
    emit_simd_arith_nonds(0x2E, dst, src, VEX_SIMD_66);
  }
}

void Assembler::ucomiss(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_simd_arith_nonds(0x2E, dst, src, VEX_SIMD_NONE, /* no_mask_reg */ true);
}

void Assembler::ucomiss(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_simd_arith_nonds(0x2E, dst, src, VEX_SIMD_NONE, /* no_mask_reg */ true);
}

void Assembler::xabort(int8_t imm8) {
  emit_int8((unsigned char)0xC6);
  emit_int8((unsigned char)0xF8);
  emit_int8((unsigned char)(imm8 & 0xFF));
}

void Assembler::xaddl(Address dst, Register src) {
  InstructionMark im(this);
  prefix(dst, src);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xC1);
  emit_operand(src, dst);
}

void Assembler::xbegin(Label& abort, relocInfo::relocType rtype) {
  InstructionMark im(this);
  relocate(rtype);
  if (abort.is_bound()) {
    address entry = target(abort);
    assert(entry != NULL, "abort entry NULL");
    intptr_t offset = entry - pc();
    emit_int8((unsigned char)0xC7);
    emit_int8((unsigned char)0xF8);
    emit_int32(offset - 6); // 2 opcode + 4 address
  } else {
    abort.add_patch_at(code(), locator());
    emit_int8((unsigned char)0xC7);
    emit_int8((unsigned char)0xF8);
    emit_int32(0);
  }
}

void Assembler::xchgl(Register dst, Address src) { // xchg
  InstructionMark im(this);
  prefix(src, dst);
  emit_int8((unsigned char)0x87);
  emit_operand(dst, src);
}

void Assembler::xchgl(Register dst, Register src) {
  int encode = prefix_and_encode(dst->encoding(), src->encoding());
  emit_int8((unsigned char)0x87);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::xend() {
  emit_int8((unsigned char)0x0F);
  emit_int8((unsigned char)0x01);
  emit_int8((unsigned char)0xD5);
}

void Assembler::xgetbv() {
  emit_int8(0x0F);
  emit_int8(0x01);
  emit_int8((unsigned char)0xD0);
}

void Assembler::xorl(Register dst, int32_t imm32) {
  prefix(dst);
  emit_arith(0x81, 0xF0, dst, imm32);
}

void Assembler::xorl(Register dst, Address src) {
  InstructionMark im(this);
  prefix(src, dst);
  emit_int8(0x33);
  emit_operand(dst, src);
}

void Assembler::xorl(Register dst, Register src) {
  (void) prefix_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x33, 0xC0, dst, src);
}


// AVX 3-operands scalar float-point arithmetic instructions

void Assembler::vaddsd(XMMRegister dst, XMMRegister nds, Address src) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_64bit;
    emit_vex_arith_q(0x58, dst, nds, src, VEX_SIMD_F2, AVX_128bit);
  } else {
    emit_vex_arith(0x58, dst, nds, src, VEX_SIMD_F2, AVX_128bit);
  }
}

void Assembler::vaddsd(XMMRegister dst, XMMRegister nds, XMMRegister src) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    emit_vex_arith_q(0x58, dst, nds, src, VEX_SIMD_F2, AVX_128bit);
  } else {
    emit_vex_arith(0x58, dst, nds, src, VEX_SIMD_F2, AVX_128bit);
  }
}

void Assembler::vaddss(XMMRegister dst, XMMRegister nds, Address src) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_vex_arith(0x58, dst, nds, src, VEX_SIMD_F3, AVX_128bit);
}

void Assembler::vaddss(XMMRegister dst, XMMRegister nds, XMMRegister src) {
  assert(VM_Version::supports_avx(), "");
  emit_vex_arith(0x58, dst, nds, src, VEX_SIMD_F3, AVX_128bit);
}

void Assembler::vdivsd(XMMRegister dst, XMMRegister nds, Address src) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_64bit;
    emit_vex_arith_q(0x5E, dst, nds, src, VEX_SIMD_F2, AVX_128bit);
  } else {
    emit_vex_arith(0x5E, dst, nds, src, VEX_SIMD_F2, AVX_128bit);
  }
}

void Assembler::vdivsd(XMMRegister dst, XMMRegister nds, XMMRegister src) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    emit_vex_arith_q(0x5E, dst, nds, src, VEX_SIMD_F2, AVX_128bit);
  } else {
    emit_vex_arith(0x5E, dst, nds, src, VEX_SIMD_F2, AVX_128bit);
  }
}

void Assembler::vdivss(XMMRegister dst, XMMRegister nds, Address src) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_vex_arith(0x5E, dst, nds, src, VEX_SIMD_F3, AVX_128bit);
}

void Assembler::vdivss(XMMRegister dst, XMMRegister nds, XMMRegister src) {
  assert(VM_Version::supports_avx(), "");
  emit_vex_arith(0x5E, dst, nds, src, VEX_SIMD_F3, AVX_128bit);
}

void Assembler::vmulsd(XMMRegister dst, XMMRegister nds, Address src) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_64bit;
    emit_vex_arith_q(0x59, dst, nds, src, VEX_SIMD_F2, AVX_128bit);
  } else {
    emit_vex_arith(0x59, dst, nds, src, VEX_SIMD_F2, AVX_128bit);
  }
}

void Assembler::vmulsd(XMMRegister dst, XMMRegister nds, XMMRegister src) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    emit_vex_arith_q(0x59, dst, nds, src, VEX_SIMD_F2, AVX_128bit);
  } else {
    emit_vex_arith(0x59, dst, nds, src, VEX_SIMD_F2, AVX_128bit);
  }
}

void Assembler::vmulss(XMMRegister dst, XMMRegister nds, Address src) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_vex_arith(0x59, dst, nds, src, VEX_SIMD_F3, AVX_128bit);
}

void Assembler::vmulss(XMMRegister dst, XMMRegister nds, XMMRegister src) {
  assert(VM_Version::supports_avx(), "");
  emit_vex_arith(0x59, dst, nds, src, VEX_SIMD_F3, AVX_128bit);
}

void Assembler::vsubsd(XMMRegister dst, XMMRegister nds, Address src) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_64bit;
    emit_vex_arith_q(0x5C, dst, nds, src, VEX_SIMD_F2, AVX_128bit);
  } else {
    emit_vex_arith(0x5C, dst, nds, src, VEX_SIMD_F2, AVX_128bit);
  }
}

void Assembler::vsubsd(XMMRegister dst, XMMRegister nds, XMMRegister src) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    emit_vex_arith_q(0x5C, dst, nds, src, VEX_SIMD_F2, AVX_128bit);
  } else {
    emit_vex_arith(0x5C, dst, nds, src, VEX_SIMD_F2, AVX_128bit);
  }
}

void Assembler::vsubss(XMMRegister dst, XMMRegister nds, Address src) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_vex_arith(0x5C, dst, nds, src, VEX_SIMD_F3, AVX_128bit);
}

void Assembler::vsubss(XMMRegister dst, XMMRegister nds, XMMRegister src) {
  assert(VM_Version::supports_avx(), "");
  emit_vex_arith(0x5C, dst, nds, src, VEX_SIMD_F3, AVX_128bit);
}

//====================VECTOR ARITHMETIC=====================================

// Float-point vector arithmetic

void Assembler::addpd(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0x58, dst, src, VEX_SIMD_66);
  } else {
    emit_simd_arith(0x58, dst, src, VEX_SIMD_66);
  }
}

void Assembler::addps(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0x58, dst, src, VEX_SIMD_NONE);
}

void Assembler::vaddpd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    emit_vex_arith_q(0x58, dst, nds, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0x58, dst, nds, src, VEX_SIMD_66, vector_len);
  }
}

void Assembler::vaddps(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  emit_vex_arith(0x58, dst, nds, src, VEX_SIMD_NONE, vector_len);
}

void Assembler::vaddpd(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_64bit;
    emit_vex_arith_q(0x58, dst, nds, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0x58, dst, nds, src, VEX_SIMD_66, vector_len);
  }
}

void Assembler::vaddps(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_vex_arith(0x58, dst, nds, src, VEX_SIMD_NONE, vector_len);
}

void Assembler::subpd(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0x5C, dst, src, VEX_SIMD_66);
  } else {
    emit_simd_arith(0x5C, dst, src, VEX_SIMD_66);
  }
}

void Assembler::subps(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0x5C, dst, src, VEX_SIMD_NONE);
}

void Assembler::vsubpd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    emit_vex_arith_q(0x5C, dst, nds, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0x5C, dst, nds, src, VEX_SIMD_66, vector_len);
  }
}

void Assembler::vsubps(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  emit_vex_arith(0x5C, dst, nds, src, VEX_SIMD_NONE, vector_len);
}

void Assembler::vsubpd(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_64bit;
    emit_vex_arith_q(0x5C, dst, nds, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0x5C, dst, nds, src, VEX_SIMD_66, vector_len);
  }
}

void Assembler::vsubps(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_vex_arith(0x5C, dst, nds, src, VEX_SIMD_NONE, vector_len);
}

void Assembler::mulpd(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0x59, dst, src, VEX_SIMD_66);
  } else {
    emit_simd_arith(0x59, dst, src, VEX_SIMD_66);
  }
}

void Assembler::mulps(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0x59, dst, src, VEX_SIMD_NONE);
}

void Assembler::vmulpd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    emit_vex_arith_q(0x59, dst, nds, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0x59, dst, nds, src, VEX_SIMD_66, vector_len);
  }
}

void Assembler::vmulps(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  emit_vex_arith(0x59, dst, nds, src, VEX_SIMD_NONE, vector_len);
}

void Assembler::vmulpd(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_64bit;
    emit_vex_arith_q(0x59, dst, nds, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0x59, dst, nds, src, VEX_SIMD_66, vector_len);
  }
}

void Assembler::vmulps(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_vex_arith(0x59, dst, nds, src, VEX_SIMD_NONE, vector_len);
}

void Assembler::divpd(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0x5E, dst, src, VEX_SIMD_66);
  } else {
    emit_simd_arith(0x5E, dst, src, VEX_SIMD_66);
  }
}

void Assembler::divps(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0x5E, dst, src, VEX_SIMD_NONE);
}

void Assembler::vdivpd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    emit_vex_arith_q(0x5E, dst, nds, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0x5E, dst, nds, src, VEX_SIMD_66, vector_len);
  }
}

void Assembler::vdivps(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  emit_vex_arith(0x5E, dst, nds, src, VEX_SIMD_NONE, vector_len);
}

void Assembler::vdivpd(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_64bit;
    emit_vex_arith_q(0x5E, dst, nds, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0x5E, dst, nds, src, VEX_SIMD_66, vector_len);
  }
}

void Assembler::vdivps(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_vex_arith(0x5E, dst, nds, src, VEX_SIMD_NONE, vector_len);
}

void Assembler::vsqrtpd(XMMRegister dst, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    emit_vex_arith_q(0x51, dst, xnoreg, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0x51, dst, xnoreg, src, VEX_SIMD_66, vector_len);
  }
}

void Assembler::vsqrtpd(XMMRegister dst, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_64bit;
    emit_vex_arith_q(0x51, dst, xnoreg, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0x51, dst, xnoreg, src, VEX_SIMD_66, vector_len);
  }
}

void Assembler::andpd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_avx512dq()) {
    emit_simd_arith_q(0x54, dst, src, VEX_SIMD_66);
  } else {
    emit_simd_arith(0x54, dst, src, VEX_SIMD_66, /* no_mask_reg */ false, /* legacy_mode */ true);
  }
}

void Assembler::andps(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_simd_arith(0x54, dst, src, VEX_SIMD_NONE, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_dq);
}

void Assembler::andps(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_simd_arith(0x54, dst, src, VEX_SIMD_NONE, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_dq);
}

void Assembler::andpd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_avx512dq()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_64bit;
    emit_simd_arith_q(0x54, dst, src, VEX_SIMD_66);
  } else {
    emit_simd_arith(0x54, dst, src, VEX_SIMD_66, /* no_mask_reg */ false, /* legacy_mode */ true);
  }
}

void Assembler::vandpd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_avx512dq()) {
    emit_vex_arith_q(0x54, dst, nds, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0x54, dst, nds, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ true);
  }
}

void Assembler::vandps(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(VM_Version::supports_avx(), "");
  emit_vex_arith(0x54, dst, nds, src, VEX_SIMD_NONE, vector_len, /* no_mask_reg */ false,  /* legacy_mode */ _legacy_mode_dq);
}

void Assembler::vandpd(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_avx512dq()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_64bit;
    emit_vex_arith_q(0x54, dst, nds, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0x54, dst, nds, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ true);
  }
}

void Assembler::vandps(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_vex_arith(0x54, dst, nds, src, VEX_SIMD_NONE, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_dq);
}

void Assembler::xorpd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_avx512dq()) {
    emit_simd_arith_q(0x57, dst, src, VEX_SIMD_66);
  } else {
    emit_simd_arith(0x57, dst, src, VEX_SIMD_66, /* no_mask_reg */ false, /* legacy_mode */ true);
  }
}

void Assembler::xorps(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  emit_simd_arith(0x57, dst, src, VEX_SIMD_NONE, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_dq);
}

void Assembler::xorpd(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_avx512dq()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_64bit;
    emit_simd_arith_q(0x57, dst, src, VEX_SIMD_66);
  } else {
    emit_simd_arith(0x57, dst, src, VEX_SIMD_66, /* no_mask_reg */ false, /* legacy_mode */ true);
  }
}

void Assembler::xorps(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_simd_arith(0x57, dst, src, VEX_SIMD_NONE, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_dq);
}

void Assembler::vxorpd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_avx512dq()) {
    emit_vex_arith_q(0x57, dst, nds, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0x57, dst, nds, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ true);
  }
}

void Assembler::vxorps(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(VM_Version::supports_avx(), "");
  emit_vex_arith(0x57, dst, nds, src, VEX_SIMD_NONE, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_dq);
}

void Assembler::vxorpd(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_avx512dq()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_64bit;
    emit_vex_arith_q(0x57, dst, nds, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0x57, dst, nds, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ true);
  }
}

void Assembler::vxorps(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  assert(VM_Version::supports_avx(), "");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_vex_arith(0x57, dst, nds, src, VEX_SIMD_NONE, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_dq);
}

// Integer vector arithmetic
void Assembler::vphaddw(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(VM_Version::supports_avx() && (vector_len == 0) ||
         VM_Version::supports_avx2(), "256 bit integer vectors requires AVX2");
  int encode = vex_prefix_and_encode(dst, nds, src, VEX_SIMD_66, vector_len, VEX_OPCODE_0F_38, /* legacy_mode */ true);
  emit_int8(0x01);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::vphaddd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(VM_Version::supports_avx() && (vector_len == 0) ||
         VM_Version::supports_avx2(), "256 bit integer vectors requires AVX2");
  int encode = vex_prefix_and_encode(dst, nds, src, VEX_SIMD_66, vector_len, VEX_OPCODE_0F_38, /* legacy_mode */ true);
  emit_int8(0x02);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::paddb(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0xFC, dst, src, VEX_SIMD_66, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::paddw(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0xFD, dst, src, VEX_SIMD_66, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::paddd(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0xFE, dst, src, VEX_SIMD_66);
}

void Assembler::paddq(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0xD4, dst, src, VEX_SIMD_66);
  } else {
    emit_simd_arith(0xD4, dst, src, VEX_SIMD_66);
  }
}

void Assembler::phaddw(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse3(), ""));
  int encode = simd_prefix_and_encode(dst, dst, src, VEX_SIMD_66, /* no_mask_reg */ false,
                                      VEX_OPCODE_0F_38, /* rex_w */ false, AVX_128bit, /* legacy_mode */ true);
  emit_int8(0x01);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::phaddd(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse3(), ""));
  int encode = simd_prefix_and_encode(dst, dst, src, VEX_SIMD_66, /* no_mask_reg */ false,
                                      VEX_OPCODE_0F_38, /* rex_w */ false, AVX_128bit, /* legacy_mode */ true);
  emit_int8(0x02);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::vpaddb(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  emit_vex_arith(0xFC, dst, nds, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::vpaddw(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  emit_vex_arith(0xFD, dst, nds, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::vpaddd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  emit_vex_arith(0xFE, dst, nds, src, VEX_SIMD_66, vector_len);
}

void Assembler::vpaddq(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    emit_vex_arith_q(0xD4, dst, nds, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0xD4, dst, nds, src, VEX_SIMD_66, vector_len);
  }
}

void Assembler::vpaddb(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FVM;
  }
  emit_vex_arith(0xFC, dst, nds, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::vpaddw(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FVM;
  }
  emit_vex_arith(0xFD, dst, nds, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::vpaddd(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_vex_arith(0xFE, dst, nds, src, VEX_SIMD_66, vector_len);
}

void Assembler::vpaddq(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_64bit;
    emit_vex_arith_q(0xD4, dst, nds, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0xD4, dst, nds, src, VEX_SIMD_66, vector_len);
  }
}

void Assembler::psubb(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0xF8, dst, src, VEX_SIMD_66, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::psubw(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0xF9, dst, src, VEX_SIMD_66, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::psubd(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0xFA, dst, src, VEX_SIMD_66);
}

void Assembler::psubq(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0xFB, dst, src, VEX_SIMD_66);
  } else {
    emit_simd_arith(0xFB, dst, src, VEX_SIMD_66);
  }
}

void Assembler::vpsubb(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  emit_vex_arith(0xF8, dst, nds, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::vpsubw(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  emit_vex_arith(0xF9, dst, nds, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::vpsubd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  emit_vex_arith(0xFA, dst, nds, src, VEX_SIMD_66, vector_len);
}

void Assembler::vpsubq(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    emit_vex_arith_q(0xFB, dst, nds, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0xFB, dst, nds, src, VEX_SIMD_66, vector_len);
  }
}

void Assembler::vpsubb(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FVM;
  }
  emit_vex_arith(0xF8, dst, nds, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::vpsubw(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FVM;
  }
  emit_vex_arith(0xF9, dst, nds, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::vpsubd(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_vex_arith(0xFA, dst, nds, src, VEX_SIMD_66, vector_len);
}

void Assembler::vpsubq(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_64bit;
    emit_vex_arith_q(0xFB, dst, nds, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0xFB, dst, nds, src, VEX_SIMD_66, vector_len);
  }
}

void Assembler::pmullw(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0xD5, dst, src, VEX_SIMD_66, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::pmulld(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_sse4_1(), "");
  int encode = simd_prefix_and_encode(dst, dst, src, VEX_SIMD_66,
                                      /* no_mask_reg */ false, VEX_OPCODE_0F_38);
  emit_int8(0x40);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::vpmullw(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  emit_vex_arith(0xD5, dst, nds, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::vpmulld(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  int encode = vex_prefix_and_encode(dst, nds, src, VEX_SIMD_66, vector_len, VEX_OPCODE_0F_38);
  emit_int8(0x40);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::vpmullq(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(UseAVX > 2, "requires some form of AVX");
  int src_enc = src->encoding();
  int dst_enc = dst->encoding();
  int nds_enc = nds->is_valid() ? nds->encoding() : 0;
  int encode = vex_prefix_and_encode(dst_enc, nds_enc, src_enc, VEX_SIMD_66, VEX_OPCODE_0F_38,
                                     /* vex_w */ true, vector_len, /* legacy_mode */ _legacy_mode_dq, /* no_mask_reg */ false);
  emit_int8(0x40);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::vpmullw(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FVM;
  }
  emit_vex_arith(0xD5, dst, nds, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::vpmulld(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  InstructionMark im(this);
  int dst_enc = dst->encoding();
  int nds_enc = nds->is_valid() ? nds->encoding() : 0;
  vex_prefix(src, nds_enc, dst_enc, VEX_SIMD_66,
             VEX_OPCODE_0F_38, /* vex_w */ false, vector_len);
  emit_int8(0x40);
  emit_operand(dst, src);
}

void Assembler::vpmullq(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_64bit;
  }
  InstructionMark im(this);
  int dst_enc = dst->encoding();
  int nds_enc = nds->is_valid() ? nds->encoding() : 0;
  vex_prefix(src, nds_enc, dst_enc, VEX_SIMD_66,
             VEX_OPCODE_0F_38, /* vex_w */ true, vector_len, /* legacy_mode */ _legacy_mode_dq);
  emit_int8(0x40);
  emit_operand(dst, src);
}

// Shift packed integers left by specified number of bits.
void Assembler::psllw(XMMRegister dst, int shift) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  // XMM6 is for /6 encoding: 66 0F 71 /6 ib
  int encode = simd_prefix_and_encode(xmm6, dst, dst, VEX_SIMD_66, /* no_mask_reg */ false, VEX_OPCODE_0F,
                                      /* rex_w */ false, AVX_128bit, /* legacy_mode */ _legacy_mode_bw);
  emit_int8(0x71);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8(shift & 0xFF);
}

void Assembler::pslld(XMMRegister dst, int shift) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  // XMM6 is for /6 encoding: 66 0F 72 /6 ib
  int encode = simd_prefix_and_encode(xmm6, dst, dst, VEX_SIMD_66, /* no_mask_reg */ false);
  emit_int8(0x72);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8(shift & 0xFF);
}

void Assembler::psllq(XMMRegister dst, int shift) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  // XMM6 is for /6 encoding: 66 0F 73 /6 ib
  int encode = simd_prefix_and_encode(xmm6, dst, dst, VEX_SIMD_66, /* no_mask_reg */ false, VEX_OPCODE_0F, /* rex_w */ true);
  emit_int8(0x73);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8(shift & 0xFF);
}

void Assembler::psllw(XMMRegister dst, XMMRegister shift) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0xF1, dst, shift, VEX_SIMD_66, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::pslld(XMMRegister dst, XMMRegister shift) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0xF2, dst, shift, VEX_SIMD_66);
}

void Assembler::psllq(XMMRegister dst, XMMRegister shift) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0xF3, dst, shift, VEX_SIMD_66);
  } else {
    emit_simd_arith(0xF3, dst, shift, VEX_SIMD_66);
  }
}

void Assembler::vpsllw(XMMRegister dst, XMMRegister src, int shift, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  // XMM6 is for /6 encoding: 66 0F 71 /6 ib
  emit_vex_arith(0x71, xmm6, dst, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
  emit_int8(shift & 0xFF);
}

void Assembler::vpslld(XMMRegister dst, XMMRegister src, int shift, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  // XMM6 is for /6 encoding: 66 0F 72 /6 ib
  emit_vex_arith(0x72, xmm6, dst, src, VEX_SIMD_66, vector_len);
  emit_int8(shift & 0xFF);
}

void Assembler::vpsllq(XMMRegister dst, XMMRegister src, int shift, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  // XMM6 is for /6 encoding: 66 0F 73 /6 ib
  if (VM_Version::supports_evex()) {
    emit_vex_arith_q(0x73, xmm6, dst, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0x73, xmm6, dst, src, VEX_SIMD_66, vector_len);
  }
  emit_int8(shift & 0xFF);
}

void Assembler::vpsllw(XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  emit_vex_arith(0xF1, dst, src, shift, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::vpslld(XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  emit_vex_arith(0xF2, dst, src, shift, VEX_SIMD_66, vector_len);
}

void Assembler::vpsllq(XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    emit_vex_arith_q(0xF3, dst, src, shift, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0xF3, dst, src, shift, VEX_SIMD_66, vector_len);
  }
}

// Shift packed integers logically right by specified number of bits.
void Assembler::psrlw(XMMRegister dst, int shift) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  // XMM2 is for /2 encoding: 66 0F 71 /2 ib
  int encode = simd_prefix_and_encode(xmm2, dst, dst, VEX_SIMD_66, /* no_mask_reg */ false,
                                      VEX_OPCODE_0F, /* rex_w */ false, AVX_128bit, /* legacy_mode */ _legacy_mode_bw);
  emit_int8(0x71);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8(shift & 0xFF);
}

void Assembler::psrld(XMMRegister dst, int shift) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  // XMM2 is for /2 encoding: 66 0F 72 /2 ib
  int encode = simd_prefix_and_encode(xmm2, dst, dst, VEX_SIMD_66, /* no_mask_reg */ false);
  emit_int8(0x72);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8(shift & 0xFF);
}

void Assembler::psrlq(XMMRegister dst, int shift) {
  _instruction_uses_vl = true;
  // Do not confuse it with psrldq SSE2 instruction which
  // shifts 128 bit value in xmm register by number of bytes.
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  // XMM2 is for /2 encoding: 66 0F 73 /2 ib
  int encode = simd_prefix_and_encode(xmm2, dst, dst, VEX_SIMD_66, /* no_mask_reg */ false,
                                      VEX_OPCODE_0F, /* rex_w */ VM_Version::supports_evex());
  emit_int8(0x73);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8(shift & 0xFF);
}

void Assembler::psrlw(XMMRegister dst, XMMRegister shift) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0xD1, dst, shift, VEX_SIMD_66, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::psrld(XMMRegister dst, XMMRegister shift) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0xD2, dst, shift, VEX_SIMD_66);
}

void Assembler::psrlq(XMMRegister dst, XMMRegister shift) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    emit_simd_arith_q(0xD3, dst, shift, VEX_SIMD_66);
  } else {
    emit_simd_arith(0xD3, dst, shift, VEX_SIMD_66);
  }
}

void Assembler::vpsrlw(XMMRegister dst, XMMRegister src, int shift, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  // XMM2 is for /2 encoding: 66 0F 71 /2 ib
  emit_vex_arith(0x71, xmm2, dst, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
  emit_int8(shift & 0xFF);
}

void Assembler::vpsrld(XMMRegister dst, XMMRegister src, int shift, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  // XMM2 is for /2 encoding: 66 0F 72 /2 ib
  emit_vex_arith(0x72, xmm2, dst, src, VEX_SIMD_66, vector_len);
  emit_int8(shift & 0xFF);
}

void Assembler::vpsrlq(XMMRegister dst, XMMRegister src, int shift, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  // XMM2 is for /2 encoding: 66 0F 73 /2 ib
  if (VM_Version::supports_evex()) {
    emit_vex_arith_q(0x73, xmm2, dst, src, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0x73, xmm2, dst, src, VEX_SIMD_66, vector_len);
  }
  emit_int8(shift & 0xFF);
}

void Assembler::vpsrlw(XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  emit_vex_arith(0xD1, dst, src, shift, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::vpsrld(XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  emit_vex_arith(0xD2, dst, src, shift, VEX_SIMD_66, vector_len);
}

void Assembler::vpsrlq(XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    emit_vex_arith_q(0xD3, dst, src, shift, VEX_SIMD_66, vector_len);
  } else {
    emit_vex_arith(0xD3, dst, src, shift, VEX_SIMD_66, vector_len);
  }
}

// Shift packed integers arithmetically right by specified number of bits.
void Assembler::psraw(XMMRegister dst, int shift) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  // XMM4 is for /4 encoding: 66 0F 71 /4 ib
  int encode = simd_prefix_and_encode(xmm4, dst, dst, VEX_SIMD_66, /* no_mask_reg */ false,
                                      VEX_OPCODE_0F, /* rex_w */ false, AVX_128bit, /* legacy_mode */ _legacy_mode_bw);
  emit_int8(0x71);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8(shift & 0xFF);
}

void Assembler::psrad(XMMRegister dst, int shift) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  // XMM4 is for /4 encoding: 66 0F 72 /4 ib
  int encode = simd_prefix_and_encode(xmm4, dst, dst, VEX_SIMD_66, /* no_mask_reg */ false);
  emit_int8(0x72);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8(shift & 0xFF);
}

void Assembler::psraw(XMMRegister dst, XMMRegister shift) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0xE1, dst, shift, VEX_SIMD_66, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::psrad(XMMRegister dst, XMMRegister shift) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0xE2, dst, shift, VEX_SIMD_66);
}

void Assembler::vpsraw(XMMRegister dst, XMMRegister src, int shift, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  // XMM4 is for /4 encoding: 66 0F 71 /4 ib
  emit_vex_arith(0x71, xmm4, dst, src, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
  emit_int8(shift & 0xFF);
}

void Assembler::vpsrad(XMMRegister dst, XMMRegister src, int shift, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  // XMM4 is for /4 encoding: 66 0F 71 /4 ib
  emit_vex_arith(0x72, xmm4, dst, src, VEX_SIMD_66, vector_len);
  emit_int8(shift & 0xFF);
}

void Assembler::vpsraw(XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len) {
  assert(UseAVX > 0, "requires some form of AVX");
  emit_vex_arith(0xE1, dst, src, shift, VEX_SIMD_66, vector_len, /* no_mask_reg */ false, /* legacy_mode */ _legacy_mode_bw);
}

void Assembler::vpsrad(XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  emit_vex_arith(0xE2, dst, src, shift, VEX_SIMD_66, vector_len);
}


// AND packed integers
void Assembler::pand(XMMRegister dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0xDB, dst, src, VEX_SIMD_66);
}

void Assembler::vpand(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  emit_vex_arith(0xDB, dst, nds, src, VEX_SIMD_66, vector_len);
}

void Assembler::vpand(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_vex_arith(0xDB, dst, nds, src, VEX_SIMD_66, vector_len);
}

void Assembler::por(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0xEB, dst, src, VEX_SIMD_66);
}

void Assembler::vpor(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  emit_vex_arith(0xEB, dst, nds, src, VEX_SIMD_66, vector_len);
}

void Assembler::vpor(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_vex_arith(0xEB, dst, nds, src, VEX_SIMD_66, vector_len);
}

void Assembler::pxor(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  emit_simd_arith(0xEF, dst, src, VEX_SIMD_66);
}

void Assembler::vpxor(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  emit_vex_arith(0xEF, dst, nds, src, VEX_SIMD_66, vector_len);
}

void Assembler::vpxor(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 0, "requires some form of AVX");
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_FV;
    _input_size_in_bits = EVEX_32bit;
  }
  emit_vex_arith(0xEF, dst, nds, src, VEX_SIMD_66, vector_len);
}


void Assembler::vinsertf128h(XMMRegister dst, XMMRegister nds, XMMRegister src) {
  assert(VM_Version::supports_avx(), "");
  int vector_len = AVX_256bit;
  if (VM_Version::supports_evex()) {
    vector_len = AVX_512bit;
  }
  int encode = vex_prefix_and_encode(dst, nds, src, VEX_SIMD_66, vector_len, VEX_OPCODE_0F_3A);
  emit_int8(0x18);
  emit_int8((unsigned char)(0xC0 | encode));
  // 0x00 - insert into lower 128 bits
  // 0x01 - insert into upper 128 bits
  emit_int8(0x01);
}

void Assembler::vinsertf64x4h(XMMRegister dst, XMMRegister nds, XMMRegister src) {
  assert(VM_Version::supports_evex(), "");
  int vector_len = AVX_512bit;
  int src_enc = src->encoding();
  int dst_enc = dst->encoding();
  int nds_enc = nds->is_valid() ? nds->encoding() : 0;
  int encode = vex_prefix_and_encode(dst_enc, nds_enc, src_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A,
                                     /* vex_w */ true, vector_len, /* legacy_mode */ false, /* no_mask_reg */ false);
  emit_int8(0x1A);
  emit_int8((unsigned char)(0xC0 | encode));
  // 0x00 - insert into lower 256 bits
  // 0x01 - insert into upper 256 bits
  emit_int8(0x01);
}

void Assembler::vinsertf64x4h(XMMRegister dst, Address src) {
  assert(VM_Version::supports_evex(), "");
  _tuple_type = EVEX_T4;
  _input_size_in_bits = EVEX_64bit;
  InstructionMark im(this);
  int vector_len = AVX_512bit;
  assert(dst != xnoreg, "sanity");
  int dst_enc = dst->encoding();
  // swap src<->dst for encoding
  vex_prefix(src, dst_enc, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A, /* vex_w */ true, vector_len);
  emit_int8(0x1A);
  emit_operand(dst, src);
  // 0x01 - insert into upper 128 bits
  emit_int8(0x01);
}

void Assembler::vinsertf32x4h(XMMRegister dst, XMMRegister nds, XMMRegister src, int value) {
  assert(VM_Version::supports_evex(), "");
  int vector_len = AVX_512bit;
  int src_enc = src->encoding();
  int dst_enc = dst->encoding();
  int nds_enc = nds->is_valid() ? nds->encoding() : 0;
  int encode = vex_prefix_and_encode(dst_enc, nds_enc, src_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A,
                                     /* vex_w */ false, vector_len, /* legacy_mode */ false, /* no_mask_reg */ false);
  emit_int8(0x18);
  emit_int8((unsigned char)(0xC0 | encode));
  // 0x00 - insert into q0 128 bits (0..127)
  // 0x01 - insert into q1 128 bits (128..255)
  // 0x02 - insert into q2 128 bits (256..383)
  // 0x03 - insert into q3 128 bits (384..511)
  emit_int8(value & 0x3);
}

void Assembler::vinsertf32x4h(XMMRegister dst, Address src, int value) {
  assert(VM_Version::supports_evex(), "");
  _tuple_type = EVEX_T4;
  _input_size_in_bits = EVEX_32bit;
  InstructionMark im(this);
  int vector_len = AVX_512bit;
  assert(dst != xnoreg, "sanity");
  int dst_enc = dst->encoding();
  // swap src<->dst for encoding
  vex_prefix(src, dst_enc, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A, /* vex_w */ false, vector_len);
  emit_int8(0x18);
  emit_operand(dst, src);
  // 0x00 - insert into q0 128 bits (0..127)
  // 0x01 - insert into q1 128 bits (128..255)
  // 0x02 - insert into q2 128 bits (256..383)
  // 0x03 - insert into q3 128 bits (384..511)
  emit_int8(value & 0x3);
}

void Assembler::vinsertf128h(XMMRegister dst, Address src) {
  assert(VM_Version::supports_avx(), "");
  int vector_len = AVX_256bit;
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T4;
    _input_size_in_bits = EVEX_32bit;
    vector_len = AVX_512bit;
  }
  InstructionMark im(this);
  assert(dst != xnoreg, "sanity");
  int dst_enc = dst->encoding();
  // swap src<->dst for encoding
  vex_prefix(src, dst_enc, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A, /* vex_w */ false, vector_len);
  emit_int8(0x18);
  emit_operand(dst, src);
  // 0x01 - insert into upper 128 bits
  emit_int8(0x01);
}

void Assembler::vextractf128h(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_avx(), "");
  int vector_len = AVX_256bit;
  if (VM_Version::supports_evex()) {
    vector_len = AVX_512bit;
  }
  int encode = vex_prefix_and_encode(src, xnoreg, dst, VEX_SIMD_66, vector_len, VEX_OPCODE_0F_3A);
  emit_int8(0x19);
  emit_int8((unsigned char)(0xC0 | encode));
  // 0x00 - insert into lower 128 bits
  // 0x01 - insert into upper 128 bits
  emit_int8(0x01);
}

void Assembler::vextractf128h(Address dst, XMMRegister src) {
  assert(VM_Version::supports_avx(), "");
  int vector_len = AVX_256bit;
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T4;
    _input_size_in_bits = EVEX_32bit;
    vector_len = AVX_512bit;
  }
  InstructionMark im(this);
  assert(src != xnoreg, "sanity");
  int src_enc = src->encoding();
  vex_prefix(dst, 0, src_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A, /* vex_w */ false, vector_len);
  emit_int8(0x19);
  emit_operand(src, dst);
  // 0x01 - extract from upper 128 bits
  emit_int8(0x01);
}

void Assembler::vinserti128h(XMMRegister dst, XMMRegister nds, XMMRegister src) {
  assert(VM_Version::supports_avx2(), "");
  int vector_len = AVX_256bit;
  if (VM_Version::supports_evex()) {
    vector_len = AVX_512bit;
  }
  int encode = vex_prefix_and_encode(dst, nds, src, VEX_SIMD_66, vector_len, VEX_OPCODE_0F_3A);
  emit_int8(0x38);
  emit_int8((unsigned char)(0xC0 | encode));
  // 0x00 - insert into lower 128 bits
  // 0x01 - insert into upper 128 bits
  emit_int8(0x01);
}

void Assembler::vinserti64x4h(XMMRegister dst, XMMRegister nds, XMMRegister src) {
  assert(VM_Version::supports_evex(), "");
  int vector_len = AVX_512bit;
  int src_enc = src->encoding();
  int dst_enc = dst->encoding();
  int nds_enc = nds->is_valid() ? nds->encoding() : 0;
  int encode = vex_prefix_and_encode(dst_enc, nds_enc, src_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A,
                                     /* vex_w */ true, vector_len, /* legacy_mode */ false, /* no_reg_mask */ false);
  emit_int8(0x38);
  emit_int8((unsigned char)(0xC0 | encode));
  // 0x00 - insert into lower 256 bits
  // 0x01 - insert into upper 256 bits
  emit_int8(0x01);
}

void Assembler::vinserti128h(XMMRegister dst, Address src) {
  assert(VM_Version::supports_avx2(), "");
  int vector_len = AVX_256bit;
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T4;
    _input_size_in_bits = EVEX_32bit;
    vector_len = AVX_512bit;
  }
  InstructionMark im(this);
  assert(dst != xnoreg, "sanity");
  int dst_enc = dst->encoding();
  // swap src<->dst for encoding
  vex_prefix(src, dst_enc, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A, /* vex_w */ false, vector_len);
  emit_int8(0x38);
  emit_operand(dst, src);
  // 0x01 - insert into upper 128 bits
  emit_int8(0x01);
}

void Assembler::vextracti128h(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_avx(), "");
  int vector_len = AVX_256bit;
  if (VM_Version::supports_evex()) {
    vector_len = AVX_512bit;
  }
  int encode = vex_prefix_and_encode(src, xnoreg, dst, VEX_SIMD_66, vector_len, VEX_OPCODE_0F_3A);
  emit_int8(0x39);
  emit_int8((unsigned char)(0xC0 | encode));
  // 0x00 - insert into lower 128 bits
  // 0x01 - insert into upper 128 bits
  emit_int8(0x01);
}

void Assembler::vextracti128h(Address dst, XMMRegister src) {
  assert(VM_Version::supports_avx2(), "");
  int vector_len = AVX_256bit;
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T4;
    _input_size_in_bits = EVEX_32bit;
    vector_len = AVX_512bit;
  }
  InstructionMark im(this);
  assert(src != xnoreg, "sanity");
  int src_enc = src->encoding();
  vex_prefix(dst, 0, src_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A, /* vex_w */ false, vector_len);
  emit_int8(0x39);
  emit_operand(src, dst);
  // 0x01 - extract from upper 128 bits
  emit_int8(0x01);
}

void Assembler::vextracti64x4h(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_evex(), "");
  int vector_len = AVX_512bit;
  int src_enc = src->encoding();
  int dst_enc = dst->encoding();
  int encode = vex_prefix_and_encode(src_enc, 0, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A,
                                     /* vex_w */ true, vector_len, /* legacy_mode */ false, /* no_mask_reg */ false);
  emit_int8(0x3B);
  emit_int8((unsigned char)(0xC0 | encode));
  // 0x01 - extract from upper 256 bits
  emit_int8(0x01);
}

void Assembler::vextracti64x2h(XMMRegister dst, XMMRegister src, int value) {
  assert(VM_Version::supports_evex(), "");
  int vector_len = AVX_512bit;
  int src_enc = src->encoding();
  int dst_enc = dst->encoding();
  int encode;
  if (VM_Version::supports_avx512dq()) {
    encode = vex_prefix_and_encode(src_enc, 0, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A,
                                   /* vex_w */ true, vector_len, /* legacy_mode */ false, /* no_mask_reg */ false);
  } else {
    encode = vex_prefix_and_encode(src_enc, 0, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A,
                                   /* vex_w */ false, vector_len, /* legacy_mode */ true, /* no_mask_reg */ false);
  }
  emit_int8(0x39);
  emit_int8((unsigned char)(0xC0 | encode));
  // 0x01 - extract from bits 255:128
  // 0x02 - extract from bits 383:256
  // 0x03 - extract from bits 511:384
  emit_int8(value & 0x3);
}

void Assembler::vextractf64x4h(XMMRegister dst, XMMRegister src) {
  assert(VM_Version::supports_evex(), "");
  int vector_len = AVX_512bit;
  int src_enc = src->encoding();
  int dst_enc = dst->encoding();
  int encode = vex_prefix_and_encode(src_enc, 0, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A,
                                     /* vex_w */ true, vector_len, /* legacy_mode */ false, /* no_mask_reg */ false);
  emit_int8(0x1B);
  emit_int8((unsigned char)(0xC0 | encode));
  // 0x01 - extract from upper 256 bits
  emit_int8(0x01);
}

void Assembler::vextractf64x4h(Address dst, XMMRegister src) {
  assert(VM_Version::supports_evex(), "");
  _tuple_type = EVEX_T4;
  _input_size_in_bits = EVEX_64bit;
  InstructionMark im(this);
  int vector_len = AVX_512bit;
  assert(src != xnoreg, "sanity");
  int src_enc = src->encoding();
  vex_prefix(dst, 0, src_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A,
             /* vex_w */ true, vector_len);
  emit_int8(0x1B);
  emit_operand(src, dst);
  // 0x01 - extract from upper 256 bits
  emit_int8(0x01);
}

void Assembler::vextractf32x4h(XMMRegister dst, XMMRegister src, int value) {
  assert(VM_Version::supports_evex(), "");
  int vector_len = AVX_512bit;
  int src_enc = src->encoding();
  int dst_enc = dst->encoding();
  int encode = vex_prefix_and_encode(src_enc, 0, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A,
                                     /* vex_w */ false, vector_len, /* legacy_mode */ false, /* no_mask_reg */ false);
  emit_int8(0x19);
  emit_int8((unsigned char)(0xC0 | encode));
  // 0x00 - extract from bits 127:0
  // 0x01 - extract from bits 255:128
  // 0x02 - extract from bits 383:256
  // 0x03 - extract from bits 511:384
  emit_int8(value & 0x3);
}

void Assembler::vextractf32x4h(Address dst, XMMRegister src, int value) {
  assert(VM_Version::supports_evex(), "");
  _tuple_type = EVEX_T4;
  _input_size_in_bits = EVEX_32bit;
  InstructionMark im(this);
  int vector_len = AVX_512bit;
  assert(src != xnoreg, "sanity");
  int src_enc = src->encoding();
  vex_prefix(dst, 0, src_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A, /* vex_w */ false, vector_len);
  emit_int8(0x19);
  emit_operand(src, dst);
  // 0x00 - extract from bits 127:0
  // 0x01 - extract from bits 255:128
  // 0x02 - extract from bits 383:256
  // 0x03 - extract from bits 511:384
  emit_int8(value & 0x3);
}

void Assembler::vextractf64x2h(XMMRegister dst, XMMRegister src, int value) {
  assert(VM_Version::supports_evex(), "");
  int vector_len = AVX_512bit;
  int src_enc = src->encoding();
  int dst_enc = dst->encoding();
  int encode = vex_prefix_and_encode(src_enc, 0, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_3A,
                                     /* vex_w */ !_legacy_mode_dq, vector_len, /* legacy_mode */ false, /* no_mask_reg */ false);
  emit_int8(0x19);
  emit_int8((unsigned char)(0xC0 | encode));
  // 0x01 - extract from bits 255:128
  // 0x02 - extract from bits 383:256
  // 0x03 - extract from bits 511:384
  emit_int8(value & 0x3);
}

// duplicate 4-bytes integer data from src into 8 locations in dest
void Assembler::vpbroadcastd(XMMRegister dst, XMMRegister src) {
  _instruction_uses_vl = true;
  assert(UseAVX > 1, "");
  int vector_len = AVX_256bit;
  int encode = vex_prefix_and_encode(dst, xnoreg, src, VEX_SIMD_66, vector_len, VEX_OPCODE_0F_38);
  emit_int8(0x58);
  emit_int8((unsigned char)(0xC0 | encode));
}

// duplicate 1-byte integer data from src into 16||32|64 locations in dest : requires AVX512BW and AVX512VL
void Assembler::evpbroadcastb(XMMRegister dst, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 1, "");
  int encode = vex_prefix_and_encode(dst, xnoreg, src, VEX_SIMD_66, vector_len, VEX_OPCODE_0F_38);
  emit_int8(0x78);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::evpbroadcastb(XMMRegister dst, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 1, "");
  _tuple_type = EVEX_T1S;
  _input_size_in_bits = EVEX_8bit;
  InstructionMark im(this);
  assert(dst != xnoreg, "sanity");
  int dst_enc = dst->encoding();
  // swap src<->dst for encoding
  vex_prefix(src, dst_enc, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_38, /* vex_w */ false, vector_len);
  emit_int8(0x78);
  emit_operand(dst, src);
}

// duplicate 2-byte integer data from src into 8|16||32 locations in dest : requires AVX512BW and AVX512VL
void Assembler::evpbroadcastw(XMMRegister dst, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 1, "");
  int encode = vex_prefix_and_encode(dst, xnoreg, src, VEX_SIMD_66, vector_len, VEX_OPCODE_0F_38);
  emit_int8(0x79);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::evpbroadcastw(XMMRegister dst, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 1, "");
  _tuple_type = EVEX_T1S;
  _input_size_in_bits = EVEX_16bit;
  InstructionMark im(this);
  assert(dst != xnoreg, "sanity");
  int dst_enc = dst->encoding();
  // swap src<->dst for encoding
  vex_prefix(src, dst_enc, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_38, /* vex_w */ false, vector_len);
  emit_int8(0x79);
  emit_operand(dst, src);
}

// duplicate 4-byte integer data from src into 4|8|16 locations in dest : requires AVX512VL
void Assembler::evpbroadcastd(XMMRegister dst, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 1, "");
  int encode = vex_prefix_and_encode(dst, xnoreg, src, VEX_SIMD_66, vector_len, VEX_OPCODE_0F_38);
  emit_int8(0x58);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::evpbroadcastd(XMMRegister dst, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 1, "");
  _tuple_type = EVEX_T1S;
  _input_size_in_bits = EVEX_32bit;
  InstructionMark im(this);
  assert(dst != xnoreg, "sanity");
  int dst_enc = dst->encoding();
  // swap src<->dst for encoding
  vex_prefix(src, dst_enc, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_38, /* vex_w */ false, vector_len);
  emit_int8(0x58);
  emit_operand(dst, src);
}

// duplicate 8-byte integer data from src into 4|8|16 locations in dest : requires AVX512VL
void Assembler::evpbroadcastq(XMMRegister dst, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 1, "");
  int encode = vex_prefix_and_encode(dst->encoding(), 0, src->encoding(), VEX_SIMD_66, VEX_OPCODE_0F_38,
                                     /* vex_w */ true, vector_len, /* legacy_mode */ false, /* no_mask_reg */ false);
  emit_int8(0x59);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::evpbroadcastq(XMMRegister dst, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 1, "");
  _tuple_type = EVEX_T1S;
  _input_size_in_bits = EVEX_64bit;
  InstructionMark im(this);
  assert(dst != xnoreg, "sanity");
  int dst_enc = dst->encoding();
  // swap src<->dst for encoding
  vex_prefix(src, dst_enc, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_38, /* vex_w */ true, vector_len);
  emit_int8(0x59);
  emit_operand(dst, src);
}

// duplicate single precision fp from src into 4|8|16 locations in dest : requires AVX512VL
void Assembler::evpbroadcastss(XMMRegister dst, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 1, "");
  int encode = vex_prefix_and_encode(dst->encoding(), 0, src->encoding(), VEX_SIMD_66, VEX_OPCODE_0F_38,
                                     /* vex_w */ false, vector_len, /* legacy_mode */ false, /*no_mask_reg */ false);
  emit_int8(0x18);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::evpbroadcastss(XMMRegister dst, Address src, int vector_len) {
  assert(UseAVX > 1, "");
  _tuple_type = EVEX_T1S;
  _input_size_in_bits = EVEX_32bit;
  InstructionMark im(this);
  assert(dst != xnoreg, "sanity");
  int dst_enc = dst->encoding();
  // swap src<->dst for encoding
  vex_prefix(src, 0, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_38, /* vex_w */ false, vector_len);
  emit_int8(0x18);
  emit_operand(dst, src);
}

// duplicate double precision fp from src into 2|4|8 locations in dest : requires AVX512VL
void Assembler::evpbroadcastsd(XMMRegister dst, XMMRegister src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 1, "");
  int encode = vex_prefix_and_encode(dst->encoding(), 0, src->encoding(), VEX_SIMD_66, VEX_OPCODE_0F_38,
                                     /*vex_w */ true, vector_len, /* legacy_mode */ false, /*no_mask_reg */ false);
  emit_int8(0x19);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::evpbroadcastsd(XMMRegister dst, Address src, int vector_len) {
  _instruction_uses_vl = true;
  assert(UseAVX > 1, "");
  _tuple_type = EVEX_T1S;
  _input_size_in_bits = EVEX_64bit;
  InstructionMark im(this);
  assert(dst != xnoreg, "sanity");
  int dst_enc = dst->encoding();
  // swap src<->dst for encoding
  vex_prefix(src, 0, dst_enc, VEX_SIMD_66, VEX_OPCODE_0F_38, /* vex_w */ true, vector_len);
  emit_int8(0x19);
  emit_operand(dst, src);
}

// duplicate 1-byte integer data from src into 16||32|64 locations in dest : requires AVX512BW and AVX512VL
void Assembler::evpbroadcastb(XMMRegister dst, Register src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_evex(), "");
  int encode = vex_prefix_and_encode(dst->encoding(), 0, src->encoding(), VEX_SIMD_66, VEX_OPCODE_0F_38,
                                     /*vex_w */ false, vector_len, /* legacy_mode */ false, /*no_mask_reg */ false);
  emit_int8(0x7A);
  emit_int8((unsigned char)(0xC0 | encode));
}

// duplicate 2-byte integer data from src into 8|16||32 locations in dest : requires AVX512BW and AVX512VL
void Assembler::evpbroadcastw(XMMRegister dst, Register src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_evex(), "");
  int encode = vex_prefix_and_encode(dst->encoding(), 0, src->encoding(), VEX_SIMD_66, VEX_OPCODE_0F_38,
                                     /* vex_w */ false, vector_len, /* legacy_mode */ false, /*no_mask_reg */ false);
  emit_int8(0x7B);
  emit_int8((unsigned char)(0xC0 | encode));
}

// duplicate 4-byte integer data from src into 4|8|16 locations in dest : requires AVX512VL
void Assembler::evpbroadcastd(XMMRegister dst, Register src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_evex(), "");
  int encode = vex_prefix_and_encode(dst->encoding(), 0, src->encoding(), VEX_SIMD_66, VEX_OPCODE_0F_38,
                                     /* vex_w */ false, vector_len, /* legacy_mode */ false, /*no_mask_reg */ false);
  emit_int8(0x7C);
  emit_int8((unsigned char)(0xC0 | encode));
}

// duplicate 8-byte integer data from src into 4|8|16 locations in dest : requires AVX512VL
void Assembler::evpbroadcastq(XMMRegister dst, Register src, int vector_len) {
  _instruction_uses_vl = true;
  assert(VM_Version::supports_evex(), "");
  int encode = vex_prefix_and_encode(dst->encoding(), 0, src->encoding(), VEX_SIMD_66, VEX_OPCODE_0F_38,
                                     /* vex_w */ true, vector_len, /* legacy_mode */ false, /*no_mask_reg */ false);
  emit_int8(0x7C);
  emit_int8((unsigned char)(0xC0 | encode));
}

// Carry-Less Multiplication Quadword
void Assembler::pclmulqdq(XMMRegister dst, XMMRegister src, int mask) {
  assert(VM_Version::supports_clmul(), "");
  int encode = simd_prefix_and_encode(dst, dst, src, VEX_SIMD_66, /* no_mask_reg */ false,
                                      VEX_OPCODE_0F_3A, /* rex_w */ false, AVX_128bit, /* legacy_mode */ true);
  emit_int8(0x44);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8((unsigned char)mask);
}

// Carry-Less Multiplication Quadword
void Assembler::vpclmulqdq(XMMRegister dst, XMMRegister nds, XMMRegister src, int mask) {
  assert(VM_Version::supports_avx() && VM_Version::supports_clmul(), "");
  int vector_len = AVX_128bit;
  int encode = vex_prefix_and_encode(dst, nds, src, VEX_SIMD_66, vector_len, VEX_OPCODE_0F_3A, /* legacy_mode */ true);
  emit_int8(0x44);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8((unsigned char)mask);
}

void Assembler::vzeroupper() {
  assert(VM_Version::supports_avx(), "");
  if (UseAVX < 3)
  {
    (void)vex_prefix_and_encode(xmm0, xmm0, xmm0, VEX_SIMD_NONE);
    emit_int8(0x77);
  }
}


#ifndef _LP64
// 32bit only pieces of the assembler

void Assembler::cmp_literal32(Register src1, int32_t imm32, RelocationHolder const& rspec) {
  // NO PREFIX AS NEVER 64BIT
  InstructionMark im(this);
  emit_int8((unsigned char)0x81);
  emit_int8((unsigned char)(0xF8 | src1->encoding()));
  emit_data(imm32, rspec, 0);
}

void Assembler::cmp_literal32(Address src1, int32_t imm32, RelocationHolder const& rspec) {
  // NO PREFIX AS NEVER 64BIT (not even 32bit versions of 64bit regs
  InstructionMark im(this);
  emit_int8((unsigned char)0x81);
  emit_operand(rdi, src1);
  emit_data(imm32, rspec, 0);
}

// The 64-bit (32bit platform) cmpxchg compares the value at adr with the contents of rdx:rax,
// and stores rcx:rbx into adr if so; otherwise, the value at adr is loaded
// into rdx:rax.  The ZF is set if the compared values were equal, and cleared otherwise.
void Assembler::cmpxchg8(Address adr) {
  InstructionMark im(this);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xC7);
  emit_operand(rcx, adr);
}

void Assembler::decl(Register dst) {
  // Don't use it directly. Use MacroAssembler::decrementl() instead.
 emit_int8(0x48 | dst->encoding());
}

#endif // _LP64

// 64bit typically doesn't use the x87 but needs to for the trig funcs

void Assembler::fabs() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xE1);
}

void Assembler::fadd(int i) {
  emit_farith(0xD8, 0xC0, i);
}

void Assembler::fadd_d(Address src) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xDC);
  emit_operand32(rax, src);
}

void Assembler::fadd_s(Address src) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xD8);
  emit_operand32(rax, src);
}

void Assembler::fadda(int i) {
  emit_farith(0xDC, 0xC0, i);
}

void Assembler::faddp(int i) {
  emit_farith(0xDE, 0xC0, i);
}

void Assembler::fchs() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xE0);
}

void Assembler::fcom(int i) {
  emit_farith(0xD8, 0xD0, i);
}

void Assembler::fcomp(int i) {
  emit_farith(0xD8, 0xD8, i);
}

void Assembler::fcomp_d(Address src) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xDC);
  emit_operand32(rbx, src);
}

void Assembler::fcomp_s(Address src) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xD8);
  emit_operand32(rbx, src);
}

void Assembler::fcompp() {
  emit_int8((unsigned char)0xDE);
  emit_int8((unsigned char)0xD9);
}

void Assembler::fcos() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xFF);
}

void Assembler::fdecstp() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xF6);
}

void Assembler::fdiv(int i) {
  emit_farith(0xD8, 0xF0, i);
}

void Assembler::fdiv_d(Address src) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xDC);
  emit_operand32(rsi, src);
}

void Assembler::fdiv_s(Address src) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xD8);
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
  emit_int8((unsigned char)0xDC);
  emit_operand32(rdi, src);
}

void Assembler::fdivr_s(Address src) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xD8);
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
  emit_int8((unsigned char)0xDF);
  emit_operand32(rbp, adr);
}

void Assembler::fild_s(Address adr) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xDB);
  emit_operand32(rax, adr);
}

void Assembler::fincstp() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xF7);
}

void Assembler::finit() {
  emit_int8((unsigned char)0x9B);
  emit_int8((unsigned char)0xDB);
  emit_int8((unsigned char)0xE3);
}

void Assembler::fist_s(Address adr) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xDB);
  emit_operand32(rdx, adr);
}

void Assembler::fistp_d(Address adr) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xDF);
  emit_operand32(rdi, adr);
}

void Assembler::fistp_s(Address adr) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xDB);
  emit_operand32(rbx, adr);
}

void Assembler::fld1() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xE8);
}

void Assembler::fld_d(Address adr) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xDD);
  emit_operand32(rax, adr);
}

void Assembler::fld_s(Address adr) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xD9);
  emit_operand32(rax, adr);
}


void Assembler::fld_s(int index) {
  emit_farith(0xD9, 0xC0, index);
}

void Assembler::fld_x(Address adr) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xDB);
  emit_operand32(rbp, adr);
}

void Assembler::fldcw(Address src) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xD9);
  emit_operand32(rbp, src);
}

void Assembler::fldenv(Address src) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xD9);
  emit_operand32(rsp, src);
}

void Assembler::fldlg2() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xEC);
}

void Assembler::fldln2() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xED);
}

void Assembler::fldz() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xEE);
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
  emit_int8((unsigned char)0xDC);
  emit_operand32(rcx, src);
}

void Assembler::fmul_s(Address src) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xD8);
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
  emit_int8((unsigned char)0xDD);
  emit_operand32(rsi, dst);
}

void Assembler::fnstcw(Address src) {
  InstructionMark im(this);
  emit_int8((unsigned char)0x9B);
  emit_int8((unsigned char)0xD9);
  emit_operand32(rdi, src);
}

void Assembler::fnstsw_ax() {
  emit_int8((unsigned char)0xDF);
  emit_int8((unsigned char)0xE0);
}

void Assembler::fprem() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xF8);
}

void Assembler::fprem1() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xF5);
}

void Assembler::frstor(Address src) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xDD);
  emit_operand32(rsp, src);
}

void Assembler::fsin() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xFE);
}

void Assembler::fsqrt() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xFA);
}

void Assembler::fst_d(Address adr) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xDD);
  emit_operand32(rdx, adr);
}

void Assembler::fst_s(Address adr) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xD9);
  emit_operand32(rdx, adr);
}

void Assembler::fstp_d(Address adr) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xDD);
  emit_operand32(rbx, adr);
}

void Assembler::fstp_d(int index) {
  emit_farith(0xDD, 0xD8, index);
}

void Assembler::fstp_s(Address adr) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xD9);
  emit_operand32(rbx, adr);
}

void Assembler::fstp_x(Address adr) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xDB);
  emit_operand32(rdi, adr);
}

void Assembler::fsub(int i) {
  emit_farith(0xD8, 0xE0, i);
}

void Assembler::fsub_d(Address src) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xDC);
  emit_operand32(rsp, src);
}

void Assembler::fsub_s(Address src) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xD8);
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
  emit_int8((unsigned char)0xDC);
  emit_operand32(rbp, src);
}

void Assembler::fsubr_s(Address src) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xD8);
  emit_operand32(rbp, src);
}

void Assembler::fsubra(int i) {
  emit_farith(0xDC, 0xE0, i);
}

void Assembler::fsubrp(int i) {
  emit_farith(0xDE, 0xE0, i);                    // ST(0) <- ST(1) - ST(0) and pop (Intel manual wrong)
}

void Assembler::ftan() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xF2);
  emit_int8((unsigned char)0xDD);
  emit_int8((unsigned char)0xD8);
}

void Assembler::ftst() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xE4);
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
  emit_int8((unsigned char)0x9B);
}

void Assembler::fxch(int i) {
  emit_farith(0xD9, 0xC8, i);
}

void Assembler::fyl2x() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xF1);
}

void Assembler::frndint() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xFC);
}

void Assembler::f2xm1() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xF0);
}

void Assembler::fldl2e() {
  emit_int8((unsigned char)0xD9);
  emit_int8((unsigned char)0xEA);
}

// SSE SIMD prefix byte values corresponding to VexSimdPrefix encoding.
static int simd_pre[4] = { 0, 0x66, 0xF3, 0xF2 };
// SSE opcode second byte values (first is 0x0F) corresponding to VexOpcode encoding.
static int simd_opc[4] = { 0,    0, 0x38, 0x3A };

// Generate SSE legacy REX prefix and SIMD opcode based on VEX encoding.
void Assembler::rex_prefix(Address adr, XMMRegister xreg, VexSimdPrefix pre, VexOpcode opc, bool rex_w) {
  if (pre > 0) {
    emit_int8(simd_pre[pre]);
  }
  if (rex_w) {
    prefixq(adr, xreg);
  } else {
    prefix(adr, xreg);
  }
  if (opc > 0) {
    emit_int8(0x0F);
    int opc2 = simd_opc[opc];
    if (opc2 > 0) {
      emit_int8(opc2);
    }
  }
}

int Assembler::rex_prefix_and_encode(int dst_enc, int src_enc, VexSimdPrefix pre, VexOpcode opc, bool rex_w) {
  if (pre > 0) {
    emit_int8(simd_pre[pre]);
  }
  int encode = (rex_w) ? prefixq_and_encode(dst_enc, src_enc) :
                          prefix_and_encode(dst_enc, src_enc);
  if (opc > 0) {
    emit_int8(0x0F);
    int opc2 = simd_opc[opc];
    if (opc2 > 0) {
      emit_int8(opc2);
    }
  }
  return encode;
}


void Assembler::vex_prefix(bool vex_r, bool vex_b, bool vex_x, bool vex_w, int nds_enc, VexSimdPrefix pre, VexOpcode opc, int vector_len) {
  if (vex_b || vex_x || vex_w || (opc == VEX_OPCODE_0F_38) || (opc == VEX_OPCODE_0F_3A)) {
    prefix(VEX_3bytes);

    int byte1 = (vex_r ? VEX_R : 0) | (vex_x ? VEX_X : 0) | (vex_b ? VEX_B : 0);
    byte1 = (~byte1) & 0xE0;
    byte1 |= opc;
    emit_int8(byte1);

    int byte2 = ((~nds_enc) & 0xf) << 3;
    byte2 |= (vex_w ? VEX_W : 0) | ((vector_len > 0) ? 4 : 0) | pre;
    emit_int8(byte2);
  } else {
    prefix(VEX_2bytes);

    int byte1 = vex_r ? VEX_R : 0;
    byte1 = (~byte1) & 0x80;
    byte1 |= ((~nds_enc) & 0xf) << 3;
    byte1 |= ((vector_len > 0 ) ? 4 : 0) | pre;
    emit_int8(byte1);
  }
}

// This is a 4 byte encoding
void Assembler::evex_prefix(bool vex_r, bool vex_b, bool vex_x, bool vex_w, bool evex_r, bool evex_v,
                            int nds_enc, VexSimdPrefix pre, VexOpcode opc,
                            bool is_extended_context, bool is_merge_context,
                            int vector_len, bool no_mask_reg ){
  // EVEX 0x62 prefix
  prefix(EVEX_4bytes);
  _evex_encoding = (vex_w ? VEX_W : 0) | (evex_r ? EVEX_Rb : 0);

  // P0: byte 2, initialized to RXBR`00mm
  // instead of not'd
  int byte2 = (vex_r ? VEX_R : 0) | (vex_x ? VEX_X : 0) | (vex_b ? VEX_B : 0) | (evex_r ? EVEX_Rb : 0);
  byte2 = (~byte2) & 0xF0;
  // confine opc opcode extensions in mm bits to lower two bits
  // of form {0F, 0F_38, 0F_3A}
  byte2 |= opc;
  emit_int8(byte2);

  // P1: byte 3 as Wvvvv1pp
  int byte3 = ((~nds_enc) & 0xf) << 3;
  // p[10] is always 1
  byte3 |= EVEX_F;
  byte3 |= (vex_w & 1) << 7;
  // confine pre opcode extensions in pp bits to lower two bits
  // of form {66, F3, F2}
  byte3 |= pre;
  emit_int8(byte3);

  // P2: byte 4 as zL'Lbv'aaa
  int byte4 = (no_mask_reg) ? 0 : 1; // kregs are implemented in the low 3 bits as aaa (hard code k1, it will be initialized for now)
  // EVEX.v` for extending EVEX.vvvv or VIDX
  byte4 |= (evex_v ? 0: EVEX_V);
  // third EXEC.b for broadcast actions
  byte4 |= (is_extended_context ? EVEX_Rb : 0);
  // fourth EVEX.L'L for vector length : 0 is 128, 1 is 256, 2 is 512, currently we do not support 1024
  byte4 |= ((vector_len) & 0x3) << 5;
  // last is EVEX.z for zero/merge actions
  byte4 |= (is_merge_context ? EVEX_Z : 0);
  emit_int8(byte4);
}

void Assembler::vex_prefix(Address adr, int nds_enc, int xreg_enc, VexSimdPrefix pre,
                           VexOpcode opc, bool vex_w, int vector_len, bool legacy_mode, bool no_mask_reg) {
  bool vex_r = ((xreg_enc & 8) == 8) ? 1 : 0;
  bool vex_b = adr.base_needs_rex();
  bool vex_x = adr.index_needs_rex();
  _avx_vector_len = vector_len;

  // if vector length is turned off, revert to AVX for vectors smaller than 512-bit
  if (_legacy_mode_vl && _instruction_uses_vl) {
    switch (vector_len) {
    case AVX_128bit:
    case AVX_256bit:
      legacy_mode = true;
      break;
    }
  }

  if ((UseAVX > 2) && (legacy_mode == false))
  {
    bool evex_r = (xreg_enc >= 16);
    bool evex_v = (nds_enc >= 16);
    _is_evex_instruction = true;
    evex_prefix(vex_r, vex_b, vex_x, vex_w, evex_r, evex_v, nds_enc, pre, opc, false, false, vector_len, no_mask_reg);
  } else {
    vex_prefix(vex_r, vex_b, vex_x, vex_w, nds_enc, pre, opc, vector_len);
  }
  _instruction_uses_vl = false;
}

int Assembler::vex_prefix_and_encode(int dst_enc, int nds_enc, int src_enc, VexSimdPrefix pre, VexOpcode opc,
                                     bool vex_w, int vector_len, bool legacy_mode, bool no_mask_reg ) {
  bool vex_r = ((dst_enc & 8) == 8) ? 1 : 0;
  bool vex_b = ((src_enc & 8) == 8) ? 1 : 0;
  bool vex_x = false;
  _avx_vector_len = vector_len;

  // if vector length is turned off, revert to AVX for vectors smaller than 512-bit
  if (_legacy_mode_vl && _instruction_uses_vl) {
    switch (vector_len) {
    case AVX_128bit:
    case AVX_256bit:
      legacy_mode = true;
      break;
    }
  }

  if ((UseAVX > 2) && (legacy_mode == false))
  {
    bool evex_r = (dst_enc >= 16);
    bool evex_v = (nds_enc >= 16);
    // can use vex_x as bank extender on rm encoding
    vex_x = (src_enc >= 16);
    evex_prefix(vex_r, vex_b, vex_x, vex_w, evex_r, evex_v, nds_enc, pre, opc, false, false, vector_len, no_mask_reg);
  } else {
    vex_prefix(vex_r, vex_b, vex_x, vex_w, nds_enc, pre, opc, vector_len);
  }

  _instruction_uses_vl = false;

  // return modrm byte components for operands
  return (((dst_enc & 7) << 3) | (src_enc & 7));
}


void Assembler::simd_prefix(XMMRegister xreg, XMMRegister nds, Address adr, VexSimdPrefix pre,
                            bool no_mask_reg, VexOpcode opc, bool rex_w, int vector_len, bool legacy_mode) {
  if (UseAVX > 0) {
    int xreg_enc = xreg->encoding();
    int  nds_enc = nds->is_valid() ? nds->encoding() : 0;
    vex_prefix(adr, nds_enc, xreg_enc, pre, opc, rex_w, vector_len, legacy_mode, no_mask_reg);
  } else {
    assert((nds == xreg) || (nds == xnoreg), "wrong sse encoding");
    rex_prefix(adr, xreg, pre, opc, rex_w);
  }
}

int Assembler::simd_prefix_and_encode(XMMRegister dst, XMMRegister nds, XMMRegister src, VexSimdPrefix pre,
                                      bool no_mask_reg, VexOpcode opc, bool rex_w, int vector_len, bool legacy_mode) {
  int dst_enc = dst->encoding();
  int src_enc = src->encoding();
  if (UseAVX > 0) {
    int nds_enc = nds->is_valid() ? nds->encoding() : 0;
    return vex_prefix_and_encode(dst_enc, nds_enc, src_enc, pre, opc, rex_w, vector_len, legacy_mode, no_mask_reg);
  } else {
    assert((nds == dst) || (nds == src) || (nds == xnoreg), "wrong sse encoding");
    return rex_prefix_and_encode(dst_enc, src_enc, pre, opc, rex_w);
  }
}

int Assembler::kreg_prefix_and_encode(KRegister dst, KRegister nds, KRegister src, VexSimdPrefix pre,
                                      bool no_mask_reg, VexOpcode opc, bool rex_w, int vector_len) {
  int dst_enc = dst->encoding();
  int src_enc = src->encoding();
  int nds_enc = nds->is_valid() ? nds->encoding() : 0;
  return vex_prefix_and_encode(dst_enc, nds_enc, src_enc, pre, opc, rex_w, vector_len, true, no_mask_reg);
}

int Assembler::kreg_prefix_and_encode(KRegister dst, KRegister nds, Register src, VexSimdPrefix pre,
                                      bool no_mask_reg, VexOpcode opc, bool rex_w, int vector_len) {
  int dst_enc = dst->encoding();
  int src_enc = src->encoding();
  int nds_enc = nds->is_valid() ? nds->encoding() : 0;
  return vex_prefix_and_encode(dst_enc, nds_enc, src_enc, pre, opc, rex_w, vector_len, true, no_mask_reg);
}

void Assembler::emit_simd_arith(int opcode, XMMRegister dst, Address src, VexSimdPrefix pre, bool no_mask_reg, bool legacy_mode) {
  InstructionMark im(this);
  simd_prefix(dst, dst, src, pre, no_mask_reg, VEX_OPCODE_0F, false, AVX_128bit, legacy_mode);
  emit_int8(opcode);
  emit_operand(dst, src);
}

void Assembler::emit_simd_arith_q(int opcode, XMMRegister dst, Address src, VexSimdPrefix pre, bool no_mask_reg) {
  InstructionMark im(this);
  simd_prefix_q(dst, dst, src, pre, no_mask_reg);
  emit_int8(opcode);
  emit_operand(dst, src);
}

void Assembler::emit_simd_arith(int opcode, XMMRegister dst, XMMRegister src, VexSimdPrefix pre, bool no_mask_reg, bool legacy_mode) {
  int encode = simd_prefix_and_encode(dst, dst, src, pre, no_mask_reg, VEX_OPCODE_0F, false, AVX_128bit, legacy_mode);
  emit_int8(opcode);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::emit_simd_arith_q(int opcode, XMMRegister dst, XMMRegister src, VexSimdPrefix pre, bool no_mask_reg) {
  int encode = simd_prefix_and_encode(dst, dst, src, pre, no_mask_reg, VEX_OPCODE_0F, true, AVX_128bit);
  emit_int8(opcode);
  emit_int8((unsigned char)(0xC0 | encode));
}

// Versions with no second source register (non-destructive source).
void Assembler::emit_simd_arith_nonds(int opcode, XMMRegister dst, Address src, VexSimdPrefix pre, bool opNoRegMask) {
  InstructionMark im(this);
  simd_prefix(dst, xnoreg, src, pre, opNoRegMask);
  emit_int8(opcode);
  emit_operand(dst, src);
}

void Assembler::emit_simd_arith_nonds_q(int opcode, XMMRegister dst, Address src, VexSimdPrefix pre, bool opNoRegMask) {
  InstructionMark im(this);
  simd_prefix_q(dst, xnoreg, src, pre, opNoRegMask);
  emit_int8(opcode);
  emit_operand(dst, src);
}

void Assembler::emit_simd_arith_nonds(int opcode, XMMRegister dst, XMMRegister src, VexSimdPrefix pre, bool no_mask_reg, bool legacy_mode) {
  int encode = simd_prefix_and_encode(dst, xnoreg, src, pre, no_mask_reg, VEX_OPCODE_0F, false, AVX_128bit, legacy_mode);
  emit_int8(opcode);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::emit_simd_arith_nonds_q(int opcode, XMMRegister dst, XMMRegister src, VexSimdPrefix pre, bool no_mask_reg) {
  int encode = simd_prefix_and_encode(dst, xnoreg, src, pre, no_mask_reg, VEX_OPCODE_0F, true);
  emit_int8(opcode);
  emit_int8((unsigned char)(0xC0 | encode));
}

// 3-operands AVX instructions
void Assembler::emit_vex_arith(int opcode, XMMRegister dst, XMMRegister nds, Address src,
                               VexSimdPrefix pre, int vector_len, bool no_mask_reg, bool legacy_mode) {
  InstructionMark im(this);
  vex_prefix(dst, nds, src, pre, vector_len, no_mask_reg, legacy_mode);
  emit_int8(opcode);
  emit_operand(dst, src);
}

void Assembler::emit_vex_arith_q(int opcode, XMMRegister dst, XMMRegister nds,
                                 Address src, VexSimdPrefix pre, int vector_len, bool no_mask_reg) {
  InstructionMark im(this);
  vex_prefix_q(dst, nds, src, pre, vector_len, no_mask_reg);
  emit_int8(opcode);
  emit_operand(dst, src);
}

void Assembler::emit_vex_arith(int opcode, XMMRegister dst, XMMRegister nds, XMMRegister src,
                               VexSimdPrefix pre, int vector_len, bool no_mask_reg, bool legacy_mode) {
  int encode = vex_prefix_and_encode(dst, nds, src, pre, vector_len, VEX_OPCODE_0F, legacy_mode, no_mask_reg);
  emit_int8(opcode);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::emit_vex_arith_q(int opcode, XMMRegister dst, XMMRegister nds, XMMRegister src,
                                 VexSimdPrefix pre, int vector_len, bool no_mask_reg) {
  int src_enc = src->encoding();
  int dst_enc = dst->encoding();
  int nds_enc = nds->is_valid() ? nds->encoding() : 0;
  int encode = vex_prefix_and_encode(dst_enc, nds_enc, src_enc, pre, VEX_OPCODE_0F, true, vector_len, false, no_mask_reg);
  emit_int8(opcode);
  emit_int8((unsigned char)(0xC0 | encode));
}

#ifndef _LP64

void Assembler::incl(Register dst) {
  // Don't use it directly. Use MacroAssembler::incrementl() instead.
  emit_int8(0x40 | dst->encoding());
}

void Assembler::lea(Register dst, Address src) {
  leal(dst, src);
}

void Assembler::mov_literal32(Address dst, int32_t imm32,  RelocationHolder const& rspec) {
  InstructionMark im(this);
  emit_int8((unsigned char)0xC7);
  emit_operand(rax, dst);
  emit_data((int)imm32, rspec, 0);
}

void Assembler::mov_literal32(Register dst, int32_t imm32, RelocationHolder const& rspec) {
  InstructionMark im(this);
  int encode = prefix_and_encode(dst->encoding());
  emit_int8((unsigned char)(0xB8 | encode));
  emit_data((int)imm32, rspec, 0);
}

void Assembler::popa() { // 32bit
  emit_int8(0x61);
}

void Assembler::push_literal32(int32_t imm32, RelocationHolder const& rspec) {
  InstructionMark im(this);
  emit_int8(0x68);
  emit_data(imm32, rspec, 0);
}

void Assembler::pusha() { // 32bit
  emit_int8(0x60);
}

void Assembler::set_byte_if_not_zero(Register dst) {
  emit_int8(0x0F);
  emit_int8((unsigned char)0x95);
  emit_int8((unsigned char)(0xE0 | dst->encoding()));
}

void Assembler::shldl(Register dst, Register src) {
  emit_int8(0x0F);
  emit_int8((unsigned char)0xA5);
  emit_int8((unsigned char)(0xC0 | src->encoding() << 3 | dst->encoding()));
}

// 0F A4 / r ib
void Assembler::shldl(Register dst, Register src, int8_t imm8) {
  emit_int8(0x0F);
  emit_int8((unsigned char)0xA4);
  emit_int8((unsigned char)(0xC0 | src->encoding() << 3 | dst->encoding()));
  emit_int8(imm8);
}

void Assembler::shrdl(Register dst, Register src) {
  emit_int8(0x0F);
  emit_int8((unsigned char)0xAD);
  emit_int8((unsigned char)(0xC0 | src->encoding() << 3 | dst->encoding()));
}

#else // LP64

void Assembler::set_byte_if_not_zero(Register dst) {
  int enc = prefix_and_encode(dst->encoding(), true);
  emit_int8(0x0F);
  emit_int8((unsigned char)0x95);
  emit_int8((unsigned char)(0xE0 | enc));
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

  disp = (int64_t)adr._target - ((int64_t)pc() + sizeof(int));

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

// Check if the polling page is not reachable from the code cache using rip-relative
// addressing.
bool Assembler::is_polling_page_far() {
  intptr_t addr = (intptr_t)os::get_polling_page();
  return ForceUnreachable ||
         !is_simm32(addr - (intptr_t)CodeCache::low_bound()) ||
         !is_simm32(addr - (intptr_t)CodeCache::high_bound());
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
  assert(imm_operand == 0, "default format must be immediate in this file");
  assert(imm_operand == format, "must be immediate");
  assert(inst_mark() != NULL, "must be inside InstructionMark");
  // Do not use AbstractAssembler::relocate, which is not intended for
  // embedded words.  Instead, relocate to the enclosing instruction.
  code_section()->relocate(inst_mark(), rspec, format);
#ifdef ASSERT
  check_relocation(rspec, format);
#endif
  emit_int64(data);
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

void Assembler::prefix(Register dst, Register src, Prefix p) {
  if (src->encoding() >= 8) {
    p = (Prefix)(p | REX_B);
  }
  if (dst->encoding() >= 8) {
    p = (Prefix)( p | REX_R);
  }
  if (p != Prefix_EMPTY) {
    // do not generate an empty prefix
    prefix(p);
  }
}

void Assembler::prefix(Register dst, Address adr, Prefix p) {
  if (adr.base_needs_rex()) {
    if (adr.index_needs_rex()) {
      assert(false, "prefix(Register dst, Address adr, Prefix p) does not support handling of an X");
    } else {
      prefix(REX_B);
    }
  } else {
    if (adr.index_needs_rex()) {
      assert(false, "prefix(Register dst, Address adr, Prefix p) does not support handling of an X");
    }
  }
  if (dst->encoding() >= 8) {
    p = (Prefix)(p | REX_R);
  }
  if (p != Prefix_EMPTY) {
    // do not generate an empty prefix
    prefix(p);
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
      } else if (byteinst && reg->encoding() >= 4 ) {
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

void Assembler::prefixq(Address adr, XMMRegister src) {
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

void Assembler::adcq(Register dst, int32_t imm32) {
  (void) prefixq_and_encode(dst->encoding());
  emit_arith(0x81, 0xD0, dst, imm32);
}

void Assembler::adcq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8(0x13);
  emit_operand(dst, src);
}

void Assembler::adcq(Register dst, Register src) {
  (void) prefixq_and_encode(dst->encoding(), src->encoding());
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
  emit_int8(0x01);
  emit_operand(src, dst);
}

void Assembler::addq(Register dst, int32_t imm32) {
  (void) prefixq_and_encode(dst->encoding());
  emit_arith(0x81, 0xC0, dst, imm32);
}

void Assembler::addq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8(0x03);
  emit_operand(dst, src);
}

void Assembler::addq(Register dst, Register src) {
  (void) prefixq_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x03, 0xC0, dst, src);
}

void Assembler::adcxq(Register dst, Register src) {
  //assert(VM_Version::supports_adx(), "adx instructions not supported");
  emit_int8((unsigned char)0x66);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8(0x38);
  emit_int8((unsigned char)0xF6);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::adoxq(Register dst, Register src) {
  //assert(VM_Version::supports_adx(), "adx instructions not supported");
  emit_int8((unsigned char)0xF3);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8(0x38);
  emit_int8((unsigned char)0xF6);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::andq(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefixq(dst);
  emit_int8((unsigned char)0x81);
  emit_operand(rsp, dst, 4);
  emit_int32(imm32);
}

void Assembler::andq(Register dst, int32_t imm32) {
  (void) prefixq_and_encode(dst->encoding());
  emit_arith(0x81, 0xE0, dst, imm32);
}

void Assembler::andq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8(0x23);
  emit_operand(dst, src);
}

void Assembler::andq(Register dst, Register src) {
  (void) prefixq_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x23, 0xC0, dst, src);
}

void Assembler::andnq(Register dst, Register src1, Register src2) {
  assert(VM_Version::supports_bmi1(), "bit manipulation instructions not supported");
  int encode = vex_prefix_0F38_and_encode_q_legacy(dst, src1, src2);
  emit_int8((unsigned char)0xF2);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::andnq(Register dst, Register src1, Address src2) {
  InstructionMark im(this);
  assert(VM_Version::supports_bmi1(), "bit manipulation instructions not supported");
  vex_prefix_0F38_q_legacy(dst, src1, src2);
  emit_int8((unsigned char)0xF2);
  emit_operand(dst, src2);
}

void Assembler::bsfq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)0xBC);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::bsrq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)0xBD);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::bswapq(Register reg) {
  int encode = prefixq_and_encode(reg->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)(0xC8 | encode));
}

void Assembler::blsiq(Register dst, Register src) {
  assert(VM_Version::supports_bmi1(), "bit manipulation instructions not supported");
  int encode = vex_prefix_0F38_and_encode_q_legacy(rbx, dst, src);
  emit_int8((unsigned char)0xF3);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::blsiq(Register dst, Address src) {
  InstructionMark im(this);
  assert(VM_Version::supports_bmi1(), "bit manipulation instructions not supported");
  vex_prefix_0F38_q_legacy(rbx, dst, src);
  emit_int8((unsigned char)0xF3);
  emit_operand(rbx, src);
}

void Assembler::blsmskq(Register dst, Register src) {
  assert(VM_Version::supports_bmi1(), "bit manipulation instructions not supported");
  int encode = vex_prefix_0F38_and_encode_q_legacy(rdx, dst, src);
  emit_int8((unsigned char)0xF3);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::blsmskq(Register dst, Address src) {
  InstructionMark im(this);
  assert(VM_Version::supports_bmi1(), "bit manipulation instructions not supported");
  vex_prefix_0F38_q_legacy(rdx, dst, src);
  emit_int8((unsigned char)0xF3);
  emit_operand(rdx, src);
}

void Assembler::blsrq(Register dst, Register src) {
  assert(VM_Version::supports_bmi1(), "bit manipulation instructions not supported");
  int encode = vex_prefix_0F38_and_encode_q_legacy(rcx, dst, src);
  emit_int8((unsigned char)0xF3);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::blsrq(Register dst, Address src) {
  InstructionMark im(this);
  assert(VM_Version::supports_bmi1(), "bit manipulation instructions not supported");
  vex_prefix_0F38_q_legacy(rcx, dst, src);
  emit_int8((unsigned char)0xF3);
  emit_operand(rcx, src);
}

void Assembler::cdqq() {
  prefix(REX_W);
  emit_int8((unsigned char)0x99);
}

void Assembler::clflush(Address adr) {
  prefix(adr);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xAE);
  emit_operand(rdi, adr);
}

void Assembler::cmovq(Condition cc, Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8(0x40 | cc);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::cmovq(Condition cc, Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8(0x0F);
  emit_int8(0x40 | cc);
  emit_operand(dst, src);
}

void Assembler::cmpq(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefixq(dst);
  emit_int8((unsigned char)0x81);
  emit_operand(rdi, dst, 4);
  emit_int32(imm32);
}

void Assembler::cmpq(Register dst, int32_t imm32) {
  (void) prefixq_and_encode(dst->encoding());
  emit_arith(0x81, 0xF8, dst, imm32);
}

void Assembler::cmpq(Address dst, Register src) {
  InstructionMark im(this);
  prefixq(dst, src);
  emit_int8(0x3B);
  emit_operand(src, dst);
}

void Assembler::cmpq(Register dst, Register src) {
  (void) prefixq_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x3B, 0xC0, dst, src);
}

void Assembler::cmpq(Register dst, Address  src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8(0x3B);
  emit_operand(dst, src);
}

void Assembler::cmpxchgq(Register reg, Address adr) {
  InstructionMark im(this);
  prefixq(adr, reg);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xB1);
  emit_operand(reg, adr);
}

void Assembler::cvtsi2sdq(XMMRegister dst, Register src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  int encode = simd_prefix_and_encode_q(dst, dst, src, VEX_SIMD_F2, /* no_mask_reg */ true);
  emit_int8(0x2A);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::cvtsi2sdq(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  InstructionMark im(this);
  simd_prefix_q(dst, dst, src, VEX_SIMD_F2, /* no_mask_reg */ true);
  emit_int8(0x2A);
  emit_operand(dst, src);
}

void Assembler::cvtsi2ssq(XMMRegister dst, Address src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  if (VM_Version::supports_evex()) {
    _tuple_type = EVEX_T1S;
    _input_size_in_bits = EVEX_32bit;
  }
  InstructionMark im(this);
  simd_prefix_q(dst, dst, src, VEX_SIMD_F3, /* no_mask_reg */ true);
  emit_int8(0x2A);
  emit_operand(dst, src);
}

void Assembler::cvttsd2siq(Register dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  int encode = simd_prefix_and_encode_q(dst, src, VEX_SIMD_F2, VEX_OPCODE_0F, /* no_mask_reg */ true);
  emit_int8(0x2C);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::cvttss2siq(Register dst, XMMRegister src) {
  NOT_LP64(assert(VM_Version::supports_sse(), ""));
  int encode = simd_prefix_and_encode_q(dst, src, VEX_SIMD_F3, VEX_OPCODE_0F, /* no_mask_reg */ true);
  emit_int8(0x2C);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::decl(Register dst) {
  // Don't use it directly. Use MacroAssembler::decrementl() instead.
  // Use two-byte form (one-byte form is a REX prefix in 64-bit mode)
  int encode = prefix_and_encode(dst->encoding());
  emit_int8((unsigned char)0xFF);
  emit_int8((unsigned char)(0xC8 | encode));
}

void Assembler::decq(Register dst) {
  // Don't use it directly. Use MacroAssembler::decrementq() instead.
  // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
  int encode = prefixq_and_encode(dst->encoding());
  emit_int8((unsigned char)0xFF);
  emit_int8(0xC8 | encode);
}

void Assembler::decq(Address dst) {
  // Don't use it directly. Use MacroAssembler::decrementq() instead.
  InstructionMark im(this);
  prefixq(dst);
  emit_int8((unsigned char)0xFF);
  emit_operand(rcx, dst);
}

void Assembler::fxrstor(Address src) {
  prefixq(src);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xAE);
  emit_operand(as_Register(1), src);
}

void Assembler::xrstor(Address src) {
  prefixq(src);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xAE);
  emit_operand(as_Register(5), src);
}

void Assembler::fxsave(Address dst) {
  prefixq(dst);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xAE);
  emit_operand(as_Register(0), dst);
}

void Assembler::xsave(Address dst) {
  prefixq(dst);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xAE);
  emit_operand(as_Register(4), dst);
}

void Assembler::idivq(Register src) {
  int encode = prefixq_and_encode(src->encoding());
  emit_int8((unsigned char)0xF7);
  emit_int8((unsigned char)(0xF8 | encode));
}

void Assembler::imulq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)0xAF);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::imulq(Register dst, Register src, int value) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  if (is8bit(value)) {
    emit_int8(0x6B);
    emit_int8((unsigned char)(0xC0 | encode));
    emit_int8(value & 0xFF);
  } else {
    emit_int8(0x69);
    emit_int8((unsigned char)(0xC0 | encode));
    emit_int32(value);
  }
}

void Assembler::imulq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8(0x0F);
  emit_int8((unsigned char) 0xAF);
  emit_operand(dst, src);
}

void Assembler::incl(Register dst) {
  // Don't use it directly. Use MacroAssembler::incrementl() instead.
  // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
  int encode = prefix_and_encode(dst->encoding());
  emit_int8((unsigned char)0xFF);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::incq(Register dst) {
  // Don't use it directly. Use MacroAssembler::incrementq() instead.
  // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
  int encode = prefixq_and_encode(dst->encoding());
  emit_int8((unsigned char)0xFF);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::incq(Address dst) {
  // Don't use it directly. Use MacroAssembler::incrementq() instead.
  InstructionMark im(this);
  prefixq(dst);
  emit_int8((unsigned char)0xFF);
  emit_operand(rax, dst);
}

void Assembler::lea(Register dst, Address src) {
  leaq(dst, src);
}

void Assembler::leaq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8((unsigned char)0x8D);
  emit_operand(dst, src);
}

void Assembler::mov64(Register dst, int64_t imm64) {
  InstructionMark im(this);
  int encode = prefixq_and_encode(dst->encoding());
  emit_int8((unsigned char)(0xB8 | encode));
  emit_int64(imm64);
}

void Assembler::mov_literal64(Register dst, intptr_t imm64, RelocationHolder const& rspec) {
  InstructionMark im(this);
  int encode = prefixq_and_encode(dst->encoding());
  emit_int8(0xB8 | encode);
  emit_data64(imm64, rspec);
}

void Assembler::mov_narrow_oop(Register dst, int32_t imm32, RelocationHolder const& rspec) {
  InstructionMark im(this);
  int encode = prefix_and_encode(dst->encoding());
  emit_int8((unsigned char)(0xB8 | encode));
  emit_data((int)imm32, rspec, narrow_oop_operand);
}

void Assembler::mov_narrow_oop(Address dst, int32_t imm32,  RelocationHolder const& rspec) {
  InstructionMark im(this);
  prefix(dst);
  emit_int8((unsigned char)0xC7);
  emit_operand(rax, dst, 4);
  emit_data((int)imm32, rspec, narrow_oop_operand);
}

void Assembler::cmp_narrow_oop(Register src1, int32_t imm32, RelocationHolder const& rspec) {
  InstructionMark im(this);
  int encode = prefix_and_encode(src1->encoding());
  emit_int8((unsigned char)0x81);
  emit_int8((unsigned char)(0xF8 | encode));
  emit_data((int)imm32, rspec, narrow_oop_operand);
}

void Assembler::cmp_narrow_oop(Address src1, int32_t imm32, RelocationHolder const& rspec) {
  InstructionMark im(this);
  prefix(src1);
  emit_int8((unsigned char)0x81);
  emit_operand(rax, src1, 4);
  emit_data((int)imm32, rspec, narrow_oop_operand);
}

void Assembler::lzcntq(Register dst, Register src) {
  assert(VM_Version::supports_lzcnt(), "encoding is treated as BSR");
  emit_int8((unsigned char)0xF3);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)0xBD);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::movdq(XMMRegister dst, Register src) {
  // table D-1 says MMX/SSE2
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  int encode = simd_prefix_and_encode_q(dst, src, VEX_SIMD_66, /* no_mask_reg */ true);
  emit_int8(0x6E);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::movdq(Register dst, XMMRegister src) {
  // table D-1 says MMX/SSE2
  NOT_LP64(assert(VM_Version::supports_sse2(), ""));
  // swap src/dst to get correct prefix
  int encode = simd_prefix_and_encode_q(src, dst, VEX_SIMD_66, /* no_mask_reg */ true);
  emit_int8(0x7E);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::movq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_int8((unsigned char)0x8B);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::movq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8((unsigned char)0x8B);
  emit_operand(dst, src);
}

void Assembler::movq(Address dst, Register src) {
  InstructionMark im(this);
  prefixq(dst, src);
  emit_int8((unsigned char)0x89);
  emit_operand(src, dst);
}

void Assembler::movsbq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xBE);
  emit_operand(dst, src);
}

void Assembler::movsbq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)0xBE);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::movslq(Register dst, int32_t imm32) {
  // dbx shows movslq(rcx, 3) as movq     $0x0000000049000000,(%rbx)
  // and movslq(r8, 3); as movl     $0x0000000048000000,(%rbx)
  // as a result we shouldn't use until tested at runtime...
  ShouldNotReachHere();
  InstructionMark im(this);
  int encode = prefixq_and_encode(dst->encoding());
  emit_int8((unsigned char)(0xC7 | encode));
  emit_int32(imm32);
}

void Assembler::movslq(Address dst, int32_t imm32) {
  assert(is_simm32(imm32), "lost bits");
  InstructionMark im(this);
  prefixq(dst);
  emit_int8((unsigned char)0xC7);
  emit_operand(rax, dst, 4);
  emit_int32(imm32);
}

void Assembler::movslq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8(0x63);
  emit_operand(dst, src);
}

void Assembler::movslq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x63);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::movswq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xBF);
  emit_operand(dst, src);
}

void Assembler::movswq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_int8((unsigned char)0x0F);
  emit_int8((unsigned char)0xBF);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::movzbq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8((unsigned char)0x0F);
  emit_int8((unsigned char)0xB6);
  emit_operand(dst, src);
}

void Assembler::movzbq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_int8(0x0F);
  emit_int8((unsigned char)0xB6);
  emit_int8(0xC0 | encode);
}

void Assembler::movzwq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8((unsigned char)0x0F);
  emit_int8((unsigned char)0xB7);
  emit_operand(dst, src);
}

void Assembler::movzwq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_int8((unsigned char)0x0F);
  emit_int8((unsigned char)0xB7);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::mulq(Address src) {
  InstructionMark im(this);
  prefixq(src);
  emit_int8((unsigned char)0xF7);
  emit_operand(rsp, src);
}

void Assembler::mulq(Register src) {
  int encode = prefixq_and_encode(src->encoding());
  emit_int8((unsigned char)0xF7);
  emit_int8((unsigned char)(0xE0 | encode));
}

void Assembler::mulxq(Register dst1, Register dst2, Register src) {
  assert(VM_Version::supports_bmi2(), "bit manipulation instructions not supported");
  int encode = vex_prefix_and_encode(dst1->encoding(), dst2->encoding(), src->encoding(), VEX_SIMD_F2, VEX_OPCODE_0F_38,
                                    /* vex_w */ true, AVX_128bit, /* legacy_mode */ true, /* no_mask_reg */ false);
  emit_int8((unsigned char)0xF6);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::negq(Register dst) {
  int encode = prefixq_and_encode(dst->encoding());
  emit_int8((unsigned char)0xF7);
  emit_int8((unsigned char)(0xD8 | encode));
}

void Assembler::notq(Register dst) {
  int encode = prefixq_and_encode(dst->encoding());
  emit_int8((unsigned char)0xF7);
  emit_int8((unsigned char)(0xD0 | encode));
}

void Assembler::orq(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefixq(dst);
  emit_int8((unsigned char)0x81);
  emit_operand(rcx, dst, 4);
  emit_int32(imm32);
}

void Assembler::orq(Register dst, int32_t imm32) {
  (void) prefixq_and_encode(dst->encoding());
  emit_arith(0x81, 0xC8, dst, imm32);
}

void Assembler::orq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8(0x0B);
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
  emit_int8((unsigned char)0xF3);
  prefixq(src, dst);
  emit_int8((unsigned char)0x0F);
  emit_int8((unsigned char)0xB8);
  emit_operand(dst, src);
}

void Assembler::popcntq(Register dst, Register src) {
  assert(VM_Version::supports_popcnt(), "must support");
  emit_int8((unsigned char)0xF3);
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_int8((unsigned char)0x0F);
  emit_int8((unsigned char)0xB8);
  emit_int8((unsigned char)(0xC0 | encode));
}

void Assembler::popq(Address dst) {
  InstructionMark im(this);
  prefixq(dst);
  emit_int8((unsigned char)0x8F);
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
  emit_int8((unsigned char)0xFF);
  emit_operand(rsi, src);
}

void Assembler::rclq(Register dst, int imm8) {
  assert(isShiftCount(imm8 >> 1), "illegal shift count");
  int encode = prefixq_and_encode(dst->encoding());
  if (imm8 == 1) {
    emit_int8((unsigned char)0xD1);
    emit_int8((unsigned char)(0xD0 | encode));
  } else {
    emit_int8((unsigned char)0xC1);
    emit_int8((unsigned char)(0xD0 | encode));
    emit_int8(imm8);
  }
}

void Assembler::rcrq(Register dst, int imm8) {
  assert(isShiftCount(imm8 >> 1), "illegal shift count");
  int encode = prefixq_and_encode(dst->encoding());
  if (imm8 == 1) {
    emit_int8((unsigned char)0xD1);
    emit_int8((unsigned char)(0xD8 | encode));
  } else {
    emit_int8((unsigned char)0xC1);
    emit_int8((unsigned char)(0xD8 | encode));
    emit_int8(imm8);
  }
}

void Assembler::rorq(Register dst, int imm8) {
  assert(isShiftCount(imm8 >> 1), "illegal shift count");
  int encode = prefixq_and_encode(dst->encoding());
  if (imm8 == 1) {
    emit_int8((unsigned char)0xD1);
    emit_int8((unsigned char)(0xC8 | encode));
  } else {
    emit_int8((unsigned char)0xC1);
    emit_int8((unsigned char)(0xc8 | encode));
    emit_int8(imm8);
  }
}

void Assembler::rorxq(Register dst, Register src, int imm8) {
  assert(VM_Version::supports_bmi2(), "bit manipulation instructions not supported");
  int encode = vex_prefix_and_encode(dst->encoding(), 0, src->encoding(), VEX_SIMD_F2, VEX_OPCODE_0F_3A,
                                     /* vex_w */ true, AVX_128bit, /* legacy_mode */ true, /* no_mask_reg */ false);
  emit_int8((unsigned char)0xF0);
  emit_int8((unsigned char)(0xC0 | encode));
  emit_int8(imm8);
}

void Assembler::sarq(Register dst, int imm8) {
  assert(isShiftCount(imm8 >> 1), "illegal shift count");
  int encode = prefixq_and_encode(dst->encoding());
  if (imm8 == 1) {
    emit_int8((unsigned char)0xD1);
    emit_int8((unsigned char)(0xF8 | encode));
  } else {
    emit_int8((unsigned char)0xC1);
    emit_int8((unsigned char)(0xF8 | encode));
    emit_int8(imm8);
  }
}

void Assembler::sarq(Register dst) {
  int encode = prefixq_and_encode(dst->encoding());
  emit_int8((unsigned char)0xD3);
  emit_int8((unsigned char)(0xF8 | encode));
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
  emit_int8(0x1B);
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
    emit_int8((unsigned char)0xD1);
    emit_int8((unsigned char)(0xE0 | encode));
  } else {
    emit_int8((unsigned char)0xC1);
    emit_int8((unsigned char)(0xE0 | encode));
    emit_int8(imm8);
  }
}

void Assembler::shlq(Register dst) {
  int encode = prefixq_and_encode(dst->encoding());
  emit_int8((unsigned char)0xD3);
  emit_int8((unsigned char)(0xE0 | encode));
}

void Assembler::shrq(Register dst, int imm8) {
  assert(isShiftCount(imm8 >> 1), "illegal shift count");
  int encode = prefixq_and_encode(dst->encoding());
  emit_int8((unsigned char)0xC1);
  emit_int8((unsigned char)(0xE8 | encode));
  emit_int8(imm8);
}

void Assembler::shrq(Register dst) {
  int encode = prefixq_and_encode(dst->encoding());
  emit_int8((unsigned char)0xD3);
  emit_int8(0xE8 | encode);
}

void Assembler::subq(Address dst, int32_t imm32) {
  InstructionMark im(this);
  prefixq(dst);
  emit_arith_operand(0x81, rbp, dst, imm32);
}

void Assembler::subq(Address dst, Register src) {
  InstructionMark im(this);
  prefixq(dst, src);
  emit_int8(0x29);
  emit_operand(src, dst);
}

void Assembler::subq(Register dst, int32_t imm32) {
  (void) prefixq_and_encode(dst->encoding());
  emit_arith(0x81, 0xE8, dst, imm32);
}

// Force generation of a 4 byte immediate value even if it fits into 8bit
void Assembler::subq_imm32(Register dst, int32_t imm32) {
  (void) prefixq_and_encode(dst->encoding());
  emit_arith_imm32(0x81, 0xE8, dst, imm32);
}

void Assembler::subq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8(0x2B);
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
    emit_int8((unsigned char)0xA9);
  } else {
    encode = prefixq_and_encode(encode);
    emit_int8((unsigned char)0xF7);
    emit_int8((unsigned char)(0xC0 | encode));
  }
  emit_int32(imm32);
}

void Assembler::testq(Register dst, Register src) {
  (void) prefixq_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x85, 0xC0, dst, src);
}

void Assembler::xaddq(Address dst, Register src) {
  InstructionMark im(this);
  prefixq(dst, src);
  emit_int8(0x0F);
  emit_int8((unsigned char)0xC1);
  emit_operand(src, dst);
}

void Assembler::xchgq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8((unsigned char)0x87);
  emit_operand(dst, src);
}

void Assembler::xchgq(Register dst, Register src) {
  int encode = prefixq_and_encode(dst->encoding(), src->encoding());
  emit_int8((unsigned char)0x87);
  emit_int8((unsigned char)(0xc0 | encode));
}

void Assembler::xorq(Register dst, Register src) {
  (void) prefixq_and_encode(dst->encoding(), src->encoding());
  emit_arith(0x33, 0xC0, dst, src);
}

void Assembler::xorq(Register dst, Address src) {
  InstructionMark im(this);
  prefixq(src, dst);
  emit_int8(0x33);
  emit_operand(dst, src);
}

#endif // !LP64
