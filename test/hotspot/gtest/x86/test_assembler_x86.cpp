/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

#if defined(X86) && !defined(ZERO)
#ifdef LP64

#include "asm/macroAssembler.hpp"
#include "compiler/disassembler.hpp"
#include "memory/resourceArea.hpp"
#include "unittest.hpp"

#define __ _masm.

static void asm_insn_check(const unsigned char *insns, const unsigned char *insns1, int len) {
  bool ok = true;
  if (memcmp(insns, insns1, len) != 0) {
    ResourceMark rm;
    stringStream ss;
    ss.print_cr("Ours:");
    Disassembler::decode((address)insns1, (address)(insns1+len), &ss);
    ss.print_cr("Theirs:");
    Disassembler::decode((address)insns, (address)(insns+len), &ss);

    EXPECT_EQ(insns, insns1) << ss.as_string();
  }
}

TEST_VM(AssemblerX86_64, validate) {
  // Smoke test for assembler
  BufferBlob* b = BufferBlob::create("x86_64Test", 500000);
  CodeBuffer code(b);
  MacroAssembler _masm(&code);
  address entry = __ pc();

  {
    address PC = __ pc();
    __ movq(rax, Address(noreg, r10, Address::times_8, 0x10));      // No base reg

    static const unsigned char insns[] = {
       0x4a, 0x8b, 0x04, 0xd5, 0x10, 0x00, 0x00, 0x00 // mov 0x10(,%r10,8),%rax
    };
    asm_insn_check(PC, insns, sizeof(insns) / sizeof(unsigned char));
  }

  BufferBlob::free(b);
}

#endif  // LP64
#endif  // X86
