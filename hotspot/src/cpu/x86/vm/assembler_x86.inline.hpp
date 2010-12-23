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

#ifndef CPU_X86_VM_ASSEMBLER_X86_INLINE_HPP
#define CPU_X86_VM_ASSEMBLER_X86_INLINE_HPP

#include "asm/assembler.inline.hpp"
#include "asm/codeBuffer.hpp"
#include "code/codeCache.hpp"
#include "runtime/handles.inline.hpp"

inline void MacroAssembler::pd_patch_instruction(address branch, address target) {
  unsigned char op = branch[0];
  assert(op == 0xE8 /* call */ ||
         op == 0xE9 /* jmp */ ||
         op == 0xEB /* short jmp */ ||
         (op & 0xF0) == 0x70 /* short jcc */ ||
         op == 0x0F && (branch[1] & 0xF0) == 0x80 /* jcc */,
         "Invalid opcode at patch point");

  if (op == 0xEB || (op & 0xF0) == 0x70) {
    // short offset operators (jmp and jcc)
    char* disp = (char*) &branch[1];
    int imm8 = target - (address) &disp[1];
    guarantee(this->is8bit(imm8), "Short forward jump exceeds 8-bit offset");
    *disp = imm8;
  } else {
    int* disp = (int*) &branch[(op == 0x0F)? 2: 1];
    int imm32 = target - (address) &disp[1];
    *disp = imm32;
  }
}

#ifndef PRODUCT
inline void MacroAssembler::pd_print_patched_instruction(address branch) {
  const char* s;
  unsigned char op = branch[0];
  if (op == 0xE8) {
    s = "call";
  } else if (op == 0xE9 || op == 0xEB) {
    s = "jmp";
  } else if ((op & 0xF0) == 0x70) {
    s = "jcc";
  } else if (op == 0x0F) {
    s = "jcc";
  } else {
    s = "????";
  }
  tty->print("%s (unresolved)", s);
}
#endif // ndef PRODUCT

#ifndef _LP64
inline int Assembler::prefix_and_encode(int reg_enc, bool byteinst) { return reg_enc; }
inline int Assembler::prefixq_and_encode(int reg_enc) { return reg_enc; }

inline int Assembler::prefix_and_encode(int dst_enc, int src_enc, bool byteinst) { return dst_enc << 3 | src_enc; }
inline int Assembler::prefixq_and_encode(int dst_enc, int src_enc) { return dst_enc << 3 | src_enc; }

inline void Assembler::prefix(Register reg) {}
inline void Assembler::prefix(Address adr) {}
inline void Assembler::prefixq(Address adr) {}

inline void Assembler::prefix(Address adr, Register reg,  bool byteinst) {}
inline void Assembler::prefixq(Address adr, Register reg) {}

inline void Assembler::prefix(Address adr, XMMRegister reg) {}
#else
inline void Assembler::emit_long64(jlong x) {
  *(jlong*) _code_pos = x;
  _code_pos += sizeof(jlong);
  code_section()->set_end(_code_pos);
}
#endif // _LP64

#endif // CPU_X86_VM_ASSEMBLER_X86_INLINE_HPP
