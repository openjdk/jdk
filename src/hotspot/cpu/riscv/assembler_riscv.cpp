/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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

#include <stdio.h>
#include <sys/types.h>

#include "precompiled.hpp"
#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "compiler/disassembler.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/sharedRuntime.hpp"

int AbstractAssembler::code_fill_byte() {
  return 0;
}

void Assembler::add(Register Rd, Register Rn, int64_t increment, Register temp) {
  if (is_imm_in_range(increment, 12, 0)) {
    addi(Rd, Rn, increment);
  } else {
    assert_different_registers(Rn, temp);
    li(temp, increment);
    add(Rd, Rn, temp);
  }
}

void Assembler::addw(Register Rd, Register Rn, int64_t increment, Register temp) {
  if (is_imm_in_range(increment, 12, 0)) {
    addiw(Rd, Rn, increment);
  } else {
    assert_different_registers(Rn, temp);
    li(temp, increment);
    addw(Rd, Rn, temp);
  }
}

void Assembler::sub(Register Rd, Register Rn, int64_t decrement, Register temp) {
  if (is_imm_in_range(-decrement, 12, 0)) {
    addi(Rd, Rn, -decrement);
  } else {
    assert_different_registers(Rn, temp);
    li(temp, decrement);
    sub(Rd, Rn, temp);
  }
}

void Assembler::subw(Register Rd, Register Rn, int64_t decrement, Register temp) {
  if (is_imm_in_range(-decrement, 12, 0)) {
    addiw(Rd, Rn, -decrement);
  } else {
    assert_different_registers(Rn, temp);
    li(temp, decrement);
    subw(Rd, Rn, temp);
  }
}

void Assembler::zext_w(Register Rd, Register Rs) {
  add_uw(Rd, Rs, zr);
}

void Assembler::_li(Register Rd, int64_t imm) {
  // int64_t is in range 0x8000 0000 0000 0000 ~ 0x7fff ffff ffff ffff
  int shift = 12;
  int64_t upper = imm, lower = imm;
  // Split imm to a lower 12-bit sign-extended part and the remainder,
  // because addi will sign-extend the lower imm.
  lower = ((int32_t)imm << 20) >> 20;
  upper -= lower;

  // Test whether imm is a 32-bit integer.
  if (!(((imm) & ~(int64_t)0x7fffffff) == 0 ||
        (((imm) & ~(int64_t)0x7fffffff) == ~(int64_t)0x7fffffff))) {
    while (((upper >> shift) & 1) == 0) { shift++; }
    upper >>= shift;
    li(Rd, upper);
    slli(Rd, Rd, shift);
    if (lower != 0) {
      addi(Rd, Rd, lower);
    }
  } else {
    // 32-bit integer
    Register hi_Rd = zr;
    if (upper != 0) {
      lui(Rd, (int32_t)upper);
      hi_Rd = Rd;
    }
    if (lower != 0 || hi_Rd == zr) {
      addiw(Rd, hi_Rd, lower);
    }
  }
}

void Assembler::li64(Register Rd, int64_t imm) {
   // Load upper 32 bits. upper = imm[63:32], but if imm[31] == 1 or
   // (imm[31:28] == 0x7ff && imm[19] == 1), upper = imm[63:32] + 1.
   int64_t lower = imm & 0xffffffff;
   lower -= ((lower << 44) >> 44);
   int64_t tmp_imm = ((uint64_t)(imm & 0xffffffff00000000)) + (uint64_t)lower;
   int32_t upper = (tmp_imm - (int32_t)lower) >> 32;

   // Load upper 32 bits
   int64_t up = upper, lo = upper;
   lo = (lo << 52) >> 52;
   up -= lo;
   up = (int32_t)up;
   lui(Rd, up);
   addi(Rd, Rd, lo);

   // Load the rest 32 bits.
   slli(Rd, Rd, 12);
   addi(Rd, Rd, (int32_t)lower >> 20);
   slli(Rd, Rd, 12);
   lower = ((int32_t)imm << 12) >> 20;
   addi(Rd, Rd, lower);
   slli(Rd, Rd, 8);
   lower = imm & 0xff;
   addi(Rd, Rd, lower);
}

void Assembler::li32(Register Rd, int32_t imm) {
  // int32_t is in range 0x8000 0000 ~ 0x7fff ffff, and imm[31] is the sign bit
  int64_t upper = imm, lower = imm;
  lower = (imm << 20) >> 20;
  upper -= lower;
  upper = (int32_t)upper;
  // lui Rd, imm[31:12] + imm[11]
  lui(Rd, upper);
  // use addiw to distinguish li32 to li64
  addiw(Rd, Rd, lower);
}

#define INSN(NAME, REGISTER)                                       \
  void Assembler::NAME(const address &dest, Register temp) {       \
    assert_cond(dest != NULL);                                     \
    int64_t distance = dest - pc();                                \
    if (is_imm_in_range(distance, 20, 1)) {                        \
      jal(REGISTER, distance);                                     \
    } else {                                                       \
      assert(temp != noreg, "temp must not be empty register!");   \
      int32_t offset = 0;                                          \
      movptr_with_offset(temp, dest, offset);                      \
      jalr(REGISTER, temp, offset);                                \
    }                                                              \
  }                                                                \
  void Assembler::NAME(Label &l, Register temp) {                  \
    jal(REGISTER, l, temp);                                        \
  }                                                                \

  INSN(j,   x0);
  INSN(jal, x1);

#undef INSN

#define INSN(NAME, REGISTER)                                       \
  void Assembler::NAME(Register Rs) {                              \
    jalr(REGISTER, Rs, 0);                                         \
  }

  INSN(jr,   x0);
  INSN(jalr, x1);

#undef INSN

void Assembler::ret() {
  jalr(x0, x1, 0);
}

#define INSN(NAME, REGISTER)                                      \
  void Assembler::NAME(const address &dest, Register temp) {      \
    assert_cond(dest != NULL);                                    \
    assert(temp != noreg, "temp must not be empty register!");    \
    int64_t distance = dest - pc();                               \
    if (is_offset_in_range(distance, 32)) {                       \
      auipc(temp, distance + 0x800);                              \
      jalr(REGISTER, temp, ((int32_t)distance << 20) >> 20);      \
    } else {                                                      \
      int32_t offset = 0;                                         \
      movptr_with_offset(temp, dest, offset);                     \
      jalr(REGISTER, temp, offset);                               \
    }                                                             \
  }

  INSN(call, x1);
  INSN(tail, x0);

#undef INSN

#define INSN(NAME, REGISTER)                                   \
  void Assembler::NAME(const Address &adr, Register temp) {    \
    switch (adr.getMode()) {                                   \
      case Address::literal: {                                 \
        code_section()->relocate(pc(), adr.rspec());           \
        NAME(adr.target(), temp);                              \
        break;                                                 \
      }                                                        \
      case Address::base_plus_offset: {                        \
        int32_t offset = 0;                                    \
        baseOffset(temp, adr, offset);                         \
        jalr(REGISTER, temp, offset);                          \
        break;                                                 \
      }                                                        \
      default:                                                 \
        ShouldNotReachHere();                                  \
    }                                                          \
  }

  INSN(j,    x0);
  INSN(jal,  x1);
  INSN(call, x1);
  INSN(tail, x0);

#undef INSN

void Assembler::wrap_label(Register r1, Register r2, Label &L, compare_and_branch_insn insn,
                           compare_and_branch_label_insn neg_insn, bool is_far) {
  if (is_far) {
    Label done;
    (this->*neg_insn)(r1, r2, done, /* is_far */ false);
    j(L);
    bind(done);
  } else {
    if (L.is_bound()) {
      (this->*insn)(r1, r2, target(L));
    } else {
      L.add_patch_at(code(), locator());
      (this->*insn)(r1, r2, pc());
    }
  }
}

void Assembler::wrap_label(Register Rt, Label &L, Register tmp, load_insn_by_temp insn) {
  if (L.is_bound()) {
    (this->*insn)(Rt, target(L), tmp);
  } else {
    L.add_patch_at(code(), locator());
    (this->*insn)(Rt, pc(), tmp);
  }
}

void Assembler::wrap_label(Register Rt, Label &L, jal_jalr_insn insn) {
  if (L.is_bound()) {
    (this->*insn)(Rt, target(L));
  } else {
    L.add_patch_at(code(), locator());
    (this->*insn)(Rt, pc());
  }
}

void Assembler::movptr_with_offset(Register Rd, address addr, int32_t &offset) {
  uintptr_t imm64 = (uintptr_t)addr;
#ifndef PRODUCT
  {
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "0x%" PRIx64, imm64);
    block_comment(buffer);
  }
#endif
  assert(is_unsigned_imm_in_range(imm64, 47, 0) || (imm64 == (uintptr_t)-1),
         "48-bit overflow in address constant");
  // Load upper 32 bits
  int32_t imm = imm64 >> 16;
  int64_t upper = imm, lower = imm;
  lower = (lower << 52) >> 52;
  upper -= lower;
  upper = (int32_t)upper;
  lui(Rd, upper);
  addi(Rd, Rd, lower);

  // Load the rest 16 bits.
  slli(Rd, Rd, 11);
  addi(Rd, Rd, (imm64 >> 5) & 0x7ff);
  slli(Rd, Rd, 5);

  // This offset will be used by following jalr/ld.
  offset = imm64 & 0x1f;
}

void Assembler::movptr(Register Rd, uintptr_t imm64) {
  movptr(Rd, (address)imm64);
}

void Assembler::movptr(Register Rd, address addr) {
  int offset = 0;
  movptr_with_offset(Rd, addr, offset);
  addi(Rd, Rd, offset);
}

void Assembler::ifence() {
  fence_i();
  if (UseConservativeFence) {
    fence(ir, ir);
  }
}

#define INSN(NAME, NEG_INSN)                                                         \
  void Assembler::NAME(Register Rs, Register Rt, const address &dest) {              \
    NEG_INSN(Rt, Rs, dest);                                                          \
  }                                                                                  \
  void Assembler::NAME(Register Rs, Register Rt, Label &l, bool is_far) {            \
    NEG_INSN(Rt, Rs, l, is_far);                                                     \
  }

  INSN(bgt,  blt);
  INSN(ble,  bge);
  INSN(bgtu, bltu);
  INSN(bleu, bgeu);
#undef INSN

#undef __

Address::Address(address target, relocInfo::relocType rtype) : _base(noreg), _offset(0), _mode(literal) {
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
      _rspec = RelocationHolder::none;
      break;
    default:
      ShouldNotReachHere();
  }
}
