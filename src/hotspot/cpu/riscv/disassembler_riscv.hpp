/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_RISCV_DISASSEMBLER_RISCV_HPP
#define CPU_RISCV_DISASSEMBLER_RISCV_HPP

static int pd_instruction_alignment() {
  return 1;
}

static const char* pd_cpu_opts() {
  return "";
}

// special-case instruction decoding.
// There may be cases where the binutils disassembler doesn't do
// the perfect job. In those cases, decode_instruction0 may kick in
// and do it right.
// If nothing had to be done, just return "here", otherwise return "here + instr_len(here)"
static address decode_instruction0(address here, outputStream* st, address virtual_begin = nullptr) {
  // The Zilx indexed integer loads reuse the AMO major opcode with an
  // addressing mode selected by funct5 and a width/signedness selected by
  // funct3. Stock binutils/hsdis does not know these encodings, so decode them
  // here and print them as "mnemonic rd, (rs2), rs1". See the "Zilx"
  // unprivileged extension specification.
  // TODO: We should remove this part when binutils supports Zilx.
  if (Assembler::instr_len(here) == Assembler::instruction_size) {
    // RISC-V instructions are only 2-byte aligned, so read the 32-bit word
    // through the alignment-safe accessor rather than dereferencing a
    // uint32_t*, which would be an unaligned access on a 2-byte-aligned pc.
    uint32_t insn = Assembler::ld_instr(here);
    uint32_t opcode = insn & 0x7f;
    uint32_t aq_rl  = (insn >> 25) & 0x3;
    uint32_t funct5 = (insn >> 27) & 0x1f;
    if (opcode == 0b0101111 && aq_rl == 0 &&
        (funct5 == 0b10010 || funct5 == 0b11010 || funct5 == 0b11110)) {
      uint32_t rd     = (insn >> 7)  & 0x1f;  // destination
      uint32_t funct3 = (insn >> 12) & 0x7;   // width/sign
      uint32_t rs1    = (insn >> 15) & 0x1f;  // index
      uint32_t rs2    = (insn >> 20) & 0x1f;  // base

      // mnemonic[funct5-mode][funct3-width]; nullptr entries are reserved.
      static const char* const zilx_mnemonics[3][8] = {
        // unscaled (funct5 == 0b10010): byte forms are reserved
        { nullptr, "lxh",  "lxw",  "lxd",  nullptr, "lxhu",  "lxwu",  nullptr },
        // scaled (funct5 == 0b11010)
        { "lxsb",  "lxsh", "lxsw", "lxsd", "lxsbu", "lxshu", "lxswu", nullptr },
        // scaled, zero-extended 32-bit index (funct5 == 0b11110)
        { "lxsuwb","lxsuwh","lxsuww","lxsuwd","lxsuwbu","lxsuwhu","lxsuwwu", nullptr }
      };
      int mode = (funct5 == 0b10010) ? 0 : (funct5 == 0b11010) ? 1 : 2;
      const char* mnemonic = zilx_mnemonics[mode][funct3];
      if (mnemonic != nullptr) {
        st->print("%-8s%s, (%s), %s", mnemonic,
                  as_Register(rd)->name(), as_Register(rs2)->name(), as_Register(rs1)->name());
        return here + 4;
      }
    }
  }
  return here;
}

// platform-specific instruction annotations (like value of loaded constants)
static void annotate(address pc, outputStream* st) {}

#endif // CPU_RISCV_DISASSEMBLER_RISCV_HPP
