/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#if defined(AARCH64) && !defined(ZERO)

#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "asm/macroAssembler.hpp"
#include "compiler/disassembler.hpp"
#include "memory/resourceArea.hpp"
#include "unittest.hpp"

#define __ _masm.

static void asm_check(const unsigned int *insns, const unsigned int *insns1, size_t len) {
  bool ok = true;
  for (unsigned int i = 0; i < len; i++) {
    if (insns[i] != insns1[i]) {
      ResourceMark rm;
      stringStream ss;
      ss.print_cr("Ours:");
      Disassembler::decode((address)&insns1[i], (address)&insns1[i+1], &ss);
      ss.print_cr("Theirs:");
      Disassembler::decode((address)&insns[i], (address)&insns[i+1], &ss);

      EXPECT_EQ(insns[i], insns1[i]) << ss.as_string();
    }
  }
}

TEST_VM(AssemblerAArch64, validate) {
  // Smoke test for assembler
  BufferBlob* b = BufferBlob::create("aarch64Test", 500000);
  CodeBuffer code(b);
  Assembler _masm(&code);
  address entry = __ pc();

  // python aarch64-asmtest.py | expand > asmtest.out.h
#include "asmtest.out.h"

  asm_check((unsigned int *)entry, insns, sizeof insns / sizeof insns[0]);

  {
    address PC = __ pc();
    __ ld1(v0, __ T16B, Address(r16));      // No offset
    __ ld1(v0, __ T8H, __ post(r16, 16));   // Post-index
    __ ld2(v0, v1, __ T8H, __ post(r24, 16 * 2));   // Post-index
    __ ld1(v0, __ T16B, __ post(r16, r17)); // Register post-index
    static const unsigned int vector_insns[] = {
       0x4c407200, // ld1   {v0.16b}, [x16]
       0x4cdf7600, // ld1   {v0.8h}, [x16], #16
       0x4cdf8700, // ld2   {v0.8h, v1.8h}, [x24], #32
       0x4cd17200, // ld1   {v0.16b}, [x16], x17
      };
    asm_check((unsigned int *)PC, vector_insns,
              sizeof vector_insns / sizeof vector_insns[0]);
  }

  BufferBlob::free(b);
}

static void asm_dump(address start, address end) {
  ResourceMark rm;
  stringStream ss;
  ss.print_cr("Insns:");
  Disassembler::decode(start, end, &ss);
  printf("%s\n", ss.as_string());
}

TEST_VM(AssemblerAArch64, merge_dmb) {
  BufferBlob* b = BufferBlob::create("aarch64Test", 400);
  CodeBuffer code(b);
  MacroAssembler _masm(&code);

  {
    // merge with same type
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // merge with high rank
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // merge with different type
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
  }
  asm_dump(code.insts()->start(), code.insts()->end());
  // AlwaysMergeDMB
  static const unsigned int insns1[] = {
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    0xd5033bbf, // dmb.ish
  };
  // !AlwaysMergeDMB
  static const unsigned int insns2[] = {
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
  };
  if (AlwaysMergeDMB) {
    EXPECT_EQ(code.insts()->size(), (CodeSection::csize_t)(sizeof insns1));
    asm_check((const unsigned int *)code.insts()->start(), insns1, sizeof insns1 / sizeof insns1[0]);
  } else {
    EXPECT_EQ(code.insts()->size(), (CodeSection::csize_t)(sizeof insns2));
    asm_check((const unsigned int *)code.insts()->start(), insns2, sizeof insns2 / sizeof insns2[0]);
  }

  BufferBlob::free(b);
}

TEST_VM(AssemblerAArch64, merge_dmb_block_by_label) {
  BufferBlob* b = BufferBlob::create("aarch64Test", 400);
  CodeBuffer code(b);
  MacroAssembler _masm(&code);

  {
    Label l;
    // merge can not cross the label
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ bind(l);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
  }
  asm_dump(code.insts()->start(), code.insts()->end());
  static const unsigned int insns[] = {
    0xd5033abf, // dmb.ishst
    0xd5033abf, // dmb.ishst
  };
  EXPECT_EQ(code.insts()->size(), (CodeSection::csize_t)(sizeof insns));
  asm_check((const unsigned int *)code.insts()->start(), insns, sizeof insns / sizeof insns[0]);

  BufferBlob::free(b);
}

TEST_VM(AssemblerAArch64, merge_dmb_after_expand) {
  ResourceMark rm;
  BufferBlob* b = BufferBlob::create("aarch64Test", 400);
  CodeBuffer code(b);
  code.set_blob(b);
  MacroAssembler _masm(&code);

  {
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    code.insts()->maybe_expand_to_ensure_remaining(50000);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
  }
  asm_dump(code.insts()->start(), code.insts()->end());
  static const unsigned int insns[] = {
    0xd5033abf, // dmb.ishst
  };
  EXPECT_EQ(code.insts()->size(), (CodeSection::csize_t)(sizeof insns));
  asm_check((const unsigned int *)code.insts()->start(), insns, sizeof insns / sizeof insns[0]);
}

TEST_VM(AssemblerAArch64, merge_dmb_all_kinds) {
  BufferBlob* b = BufferBlob::create("aarch64Test", 20000);
  CodeBuffer code(b);
  MacroAssembler _masm(&code);

  {
    // case 1
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 2
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 3
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 4
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 5
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 6
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 7
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 8
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 9
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 10
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 11
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 12
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 13
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 14
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 15
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 16
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 17
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 18
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 19
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 20
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 21
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 22
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 23
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 24
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 25
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 26
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 27
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 28
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 29
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 30
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 31
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 32
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 33
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 34
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 35
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 36
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 37
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 38
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 39
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 40
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 41
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 42
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 43
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 44
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 45
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 46
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 47
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 48
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 49
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 50
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 51
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 52
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 53
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 54
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 55
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 56
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 57
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 58
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 59
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 60
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 61
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 62
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 63
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 64
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 65
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 66
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 67
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 68
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 69
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 70
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 71
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 72
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 73
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 74
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 75
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 76
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 77
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 78
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 79
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 80
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 81
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 82
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 83
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 84
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 85
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 86
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 87
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 88
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 89
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 90
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 91
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 92
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 93
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 94
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 95
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 96
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 97
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 98
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 99
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 100
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 101
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 102
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 103
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 104
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 105
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 106
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 107
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 108
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 109
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 110
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 111
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 112
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 113
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 114
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 115
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 116
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 117
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 118
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 119
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 120
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 121
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 122
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 123
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 124
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 125
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 126
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 127
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 128
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 129
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 130
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 131
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 132
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 133
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 134
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 135
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 136
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 137
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 138
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 139
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 140
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 141
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 142
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 143
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 144
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 145
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 146
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 147
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 148
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 149
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 150
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 151
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 152
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 153
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 154
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 155
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 156
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 157
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 158
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 159
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 160
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 161
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 162
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 163
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 164
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 165
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 166
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 167
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 168
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 169
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 170
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 171
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 172
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 173
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 174
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 175
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 176
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 177
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 178
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 179
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 180
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 181
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 182
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 183
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 184
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 185
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 186
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 187
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 188
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 189
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 190
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 191
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 192
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 193
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 194
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 195
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 196
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 197
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 198
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 199
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 200
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 201
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 202
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 203
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 204
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 205
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 206
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 207
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 208
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 209
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 210
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 211
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 212
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 213
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 214
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 215
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 216
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 217
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 218
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 219
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 220
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 221
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 222
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 223
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 224
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 225
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 226
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 227
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 228
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 229
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 230
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 231
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 232
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 233
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 234
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 235
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 236
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 237
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 238
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 239
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 240
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 241
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 242
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 243
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 244
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 245
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 246
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 247
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 248
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 249
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 250
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 251
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 252
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 253
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 254
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 255
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 256
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 257
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 258
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 259
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 260
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 261
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 262
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 263
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 264
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 265
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 266
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 267
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 268
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 269
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 270
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 271
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 272
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 273
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 274
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 275
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 276
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 277
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 278
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 279
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 280
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 281
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 282
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 283
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 284
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 285
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 286
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 287
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 288
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 289
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 290
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 291
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 292
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 293
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 294
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 295
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 296
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 297
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 298
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 299
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 300
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 301
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 302
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 303
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 304
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 305
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 306
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 307
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 308
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 309
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 310
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 311
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 312
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 313
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 314
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 315
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 316
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 317
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 318
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 319
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 320
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 321
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 322
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 323
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 324
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 325
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 326
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 327
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 328
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 329
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 330
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 331
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 332
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 333
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 334
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 335
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 336
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 337
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 338
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 339
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 340
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 341
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 342
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 343
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 344
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 345
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 346
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 347
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 348
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 349
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 350
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 351
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 352
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 353
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 354
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 355
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 356
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 357
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 358
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 359
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 360
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 361
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 362
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 363
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 364
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 365
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 366
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 367
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 368
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 369
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 370
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 371
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 372
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 373
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 374
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 375
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 376
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 377
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 378
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 379
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 380
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 381
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 382
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 383
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 384
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 385
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 386
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 387
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 388
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 389
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 390
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 391
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 392
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 393
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 394
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 395
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 396
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 397
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 398
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 399
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 400
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 401
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 402
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 403
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 404
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 405
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 406
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 407
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 408
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 409
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 410
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 411
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 412
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 413
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 414
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 415
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 416
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 417
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 418
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 419
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 420
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 421
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 422
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 423
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 424
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 425
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 426
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 427
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 428
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 429
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 430
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 431
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 432
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 433
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 434
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 435
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 436
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 437
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 438
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 439
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 440
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 441
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 442
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 443
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 444
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 445
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 446
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 447
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 448
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 449
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 450
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 451
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 452
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 453
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 454
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 455
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 456
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 457
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 458
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 459
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 460
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 461
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 462
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 463
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 464
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 465
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 466
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 467
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 468
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 469
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 470
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 471
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 472
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 473
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 474
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 475
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 476
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 477
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 478
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 479
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 480
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 481
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 482
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 483
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 484
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 485
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 486
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 487
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 488
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 489
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 490
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 491
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 492
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 493
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 494
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 495
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 496
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 497
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 498
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 499
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 500
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 501
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 502
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 503
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 504
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 505
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 506
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 507
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 508
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 509
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 510
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 511
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 512
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 513
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 514
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 515
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 516
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 517
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 518
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 519
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 520
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 521
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 522
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 523
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 524
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 525
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 526
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 527
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 528
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 529
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 530
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 531
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 532
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 533
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 534
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 535
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 536
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 537
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 538
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 539
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 540
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 541
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 542
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 543
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 544
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 545
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 546
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 547
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 548
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 549
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 550
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 551
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 552
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 553
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 554
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 555
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 556
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 557
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 558
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 559
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 560
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 561
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 562
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 563
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 564
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 565
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 566
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 567
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 568
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 569
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 570
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 571
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 572
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 573
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 574
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 575
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 576
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 577
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 578
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 579
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 580
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 581
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 582
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 583
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 584
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 585
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 586
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 587
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 588
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 589
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 590
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 591
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 592
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 593
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 594
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 595
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 596
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 597
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 598
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 599
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 600
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 601
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 602
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 603
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 604
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 605
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 606
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 607
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 608
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 609
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 610
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 611
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 612
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 613
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 614
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 615
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 616
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 617
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 618
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 619
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 620
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
    // case 621
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadLoad);
    __ nop();
    // case 622
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::LoadStore);
    __ nop();
    // case 623
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreLoad);
    __ nop();
    // case 624
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::StoreStore);
    __ nop();
    // case 625
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ membar(Assembler::Membar_mask_bits::AnyAny);
    __ nop();
  }
  asm_dump(code.insts()->start(), code.insts()->end());
  // AlwaysMergeDMB
  static const unsigned int insns1[] = {
    // case 1
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 2
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 3
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 4
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 5
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 6
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 7
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 8
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 9
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 10
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 11
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 12
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 13
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 14
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 15
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 16
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 17
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 18
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 19
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 20
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 21
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 22
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 23
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 24
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 25
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 26
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 27
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 28
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 29
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 30
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 31
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 32
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 33
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 34
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 35
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 36
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 37
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 38
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 39
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 40
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 41
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 42
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 43
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 44
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 45
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 46
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 47
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 48
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 49
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 50
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 51
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 52
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 53
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 54
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 55
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 56
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 57
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 58
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 59
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 60
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 61
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 62
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 63
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 64
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 65
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 66
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 67
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 68
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 69
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 70
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 71
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 72
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 73
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 74
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 75
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 76
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 77
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 78
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 79
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 80
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 81
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 82
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 83
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 84
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 85
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 86
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 87
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 88
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 89
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 90
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 91
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 92
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 93
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 94
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 95
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 96
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 97
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 98
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 99
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 100
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 101
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 102
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 103
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 104
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 105
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 106
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 107
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 108
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 109
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 110
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 111
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 112
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 113
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 114
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 115
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 116
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 117
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 118
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 119
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 120
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 121
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 122
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 123
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 124
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 125
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 126
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 127
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 128
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 129
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 130
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 131
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 132
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 133
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 134
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 135
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 136
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 137
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 138
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 139
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 140
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 141
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 142
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 143
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 144
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 145
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 146
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 147
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 148
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 149
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 150
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 151
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 152
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 153
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 154
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 155
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 156
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 157
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 158
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 159
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 160
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 161
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 162
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 163
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 164
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 165
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 166
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 167
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 168
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 169
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 170
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 171
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 172
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 173
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 174
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 175
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 176
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 177
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 178
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 179
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 180
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 181
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 182
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 183
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 184
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 185
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 186
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 187
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 188
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 189
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 190
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 191
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 192
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 193
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 194
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 195
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 196
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 197
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 198
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 199
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 200
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 201
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 202
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 203
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 204
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 205
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 206
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 207
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 208
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 209
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 210
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 211
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 212
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 213
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 214
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 215
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 216
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 217
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 218
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 219
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 220
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 221
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 222
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 223
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 224
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 225
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 226
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 227
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 228
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 229
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 230
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 231
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 232
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 233
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 234
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 235
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 236
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 237
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 238
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 239
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 240
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 241
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 242
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 243
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 244
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 245
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 246
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 247
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 248
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 249
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 250
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 251
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 252
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 253
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 254
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 255
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 256
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 257
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 258
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 259
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 260
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 261
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 262
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 263
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 264
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 265
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 266
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 267
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 268
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 269
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 270
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 271
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 272
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 273
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 274
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 275
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 276
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 277
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 278
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 279
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 280
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 281
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 282
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 283
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 284
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 285
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 286
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 287
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 288
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 289
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 290
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 291
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 292
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 293
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 294
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 295
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 296
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 297
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 298
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 299
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 300
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 301
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 302
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 303
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 304
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 305
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 306
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 307
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 308
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 309
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 310
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 311
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 312
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 313
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 314
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 315
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 316
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 317
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 318
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 319
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 320
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 321
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 322
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 323
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 324
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 325
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 326
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 327
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 328
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 329
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 330
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 331
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 332
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 333
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 334
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 335
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 336
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 337
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 338
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 339
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 340
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 341
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 342
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 343
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 344
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 345
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 346
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 347
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 348
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 349
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 350
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 351
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 352
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 353
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 354
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 355
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 356
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 357
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 358
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 359
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 360
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 361
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 362
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 363
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 364
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 365
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 366
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 367
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 368
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 369
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 370
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 371
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 372
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 373
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 374
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 375
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 376
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 377
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 378
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 379
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 380
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 381
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 382
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 383
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 384
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 385
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 386
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 387
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 388
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 389
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 390
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 391
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 392
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 393
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 394
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 395
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 396
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 397
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 398
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 399
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 400
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 401
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 402
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 403
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 404
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 405
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 406
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 407
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 408
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 409
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 410
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 411
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 412
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 413
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 414
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 415
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 416
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 417
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 418
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 419
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 420
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 421
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 422
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 423
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 424
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 425
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 426
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 427
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 428
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 429
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 430
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 431
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 432
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 433
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 434
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 435
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 436
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 437
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 438
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 439
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 440
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 441
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 442
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 443
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 444
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 445
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 446
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 447
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 448
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 449
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 450
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 451
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 452
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 453
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 454
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 455
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 456
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 457
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 458
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 459
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 460
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 461
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 462
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 463
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 464
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 465
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 466
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 467
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 468
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 469
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 470
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 471
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 472
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 473
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 474
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 475
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 476
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 477
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 478
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 479
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 480
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 481
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 482
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 483
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 484
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 485
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 486
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 487
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 488
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 489
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 490
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 491
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 492
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 493
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 494
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 495
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 496
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 497
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 498
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 499
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 500
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 501
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 502
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 503
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 504
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 505
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 506
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 507
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 508
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 509
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 510
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 511
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 512
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 513
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 514
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 515
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 516
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 517
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 518
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 519
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 520
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 521
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 522
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 523
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 524
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 525
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 526
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 527
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 528
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 529
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 530
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 531
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 532
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 533
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 534
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 535
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 536
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 537
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 538
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 539
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 540
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 541
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 542
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 543
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 544
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 545
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 546
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 547
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 548
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 549
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 550
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 551
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 552
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 553
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 554
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 555
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 556
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 557
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 558
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 559
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 560
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 561
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 562
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 563
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 564
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 565
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 566
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 567
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 568
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 569
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 570
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 571
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 572
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 573
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 574
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 575
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 576
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 577
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 578
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 579
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 580
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 581
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 582
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 583
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 584
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 585
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 586
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 587
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 588
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 589
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 590
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 591
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 592
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 593
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 594
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 595
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 596
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 597
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 598
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 599
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 600
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 601
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 602
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 603
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 604
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 605
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 606
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 607
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 608
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 609
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 610
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 611
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 612
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 613
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 614
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 615
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 616
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 617
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 618
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 619
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 620
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 621
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 622
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 623
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 624
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 625
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
  };
  // !AlwaysMergeDMB
  static const unsigned int insns2[] = {
    // case 1
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 2
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 3
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 4
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 5
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 6
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 7
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 8
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 9
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 10
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 11
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 12
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 13
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 14
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 15
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 16
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 17
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 18
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 19
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 20
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 21
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 22
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 23
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 24
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 25
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 26
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 27
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 28
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 29
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 30
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 31
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 32
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 33
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 34
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 35
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 36
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 37
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 38
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 39
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 40
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 41
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 42
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 43
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 44
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 45
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 46
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 47
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 48
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 49
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 50
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 51
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 52
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 53
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 54
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 55
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 56
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 57
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 58
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 59
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 60
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 61
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 62
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 63
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 64
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 65
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 66
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 67
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 68
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 69
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 70
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 71
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 72
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 73
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 74
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 75
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 76
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 77
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 78
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 79
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 80
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 81
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 82
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 83
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 84
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 85
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 86
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 87
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 88
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 89
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 90
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 91
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 92
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 93
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 94
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 95
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 96
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 97
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 98
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 99
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 100
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 101
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 102
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 103
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 104
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 105
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 106
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 107
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 108
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 109
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 110
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 111
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 112
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 113
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 114
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 115
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 116
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 117
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 118
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 119
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 120
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 121
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 122
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 123
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 124
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 125
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 126
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 127
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 128
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 129
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 130
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 131
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 132
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 133
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 134
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 135
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 136
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 137
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 138
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 139
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 140
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 141
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 142
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 143
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 144
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 145
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 146
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 147
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 148
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 149
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 150
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 151
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 152
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 153
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 154
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 155
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 156
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 157
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 158
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 159
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 160
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 161
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 162
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 163
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 164
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 165
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 166
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 167
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 168
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 169
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 170
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 171
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 172
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 173
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 174
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 175
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 176
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 177
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 178
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 179
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 180
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 181
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 182
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 183
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 184
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 185
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 186
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 187
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 188
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 189
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 190
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 191
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 192
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 193
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 194
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 195
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 196
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 197
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 198
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 199
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 200
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 201
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 202
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 203
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 204
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 205
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 206
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 207
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 208
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 209
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 210
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 211
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 212
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 213
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 214
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 215
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 216
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 217
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 218
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 219
    0xd50339bf, // dmb.ishld
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 220
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 221
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 222
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 223
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 224
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 225
    0xd50339bf, // dmb.ishld
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 226
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 227
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 228
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 229
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 230
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 231
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 232
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 233
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 234
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 235
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 236
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 237
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 238
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 239
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 240
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 241
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 242
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 243
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 244
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 245
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 246
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 247
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 248
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 249
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 250
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 251
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 252
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 253
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 254
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 255
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 256
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 257
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 258
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 259
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 260
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 261
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 262
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 263
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 264
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 265
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 266
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 267
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 268
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 269
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 270
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 271
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 272
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 273
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 274
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 275
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 276
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 277
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 278
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 279
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 280
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 281
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 282
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 283
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 284
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 285
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 286
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 287
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 288
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 289
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 290
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 291
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 292
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 293
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 294
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 295
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 296
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 297
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 298
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 299
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 300
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 301
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 302
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 303
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 304
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 305
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 306
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 307
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 308
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 309
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 310
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 311
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 312
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 313
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 314
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 315
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 316
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 317
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 318
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 319
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 320
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 321
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 322
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 323
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 324
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 325
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 326
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 327
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 328
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 329
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 330
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 331
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 332
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 333
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 334
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 335
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 336
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 337
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 338
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 339
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 340
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 341
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 342
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 343
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 344
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 345
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 346
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 347
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 348
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 349
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 350
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 351
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 352
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 353
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 354
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 355
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 356
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 357
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 358
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 359
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 360
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 361
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 362
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 363
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 364
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 365
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 366
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 367
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 368
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 369
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 370
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 371
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 372
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 373
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 374
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 375
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 376
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 377
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 378
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 379
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 380
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 381
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 382
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 383
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 384
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 385
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 386
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 387
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 388
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 389
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 390
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 391
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 392
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 393
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 394
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 395
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 396
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 397
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 398
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 399
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 400
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 401
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 402
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 403
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 404
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 405
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 406
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 407
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 408
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 409
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 410
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 411
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 412
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 413
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 414
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 415
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 416
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 417
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 418
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 419
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 420
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 421
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 422
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 423
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 424
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 425
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 426
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 427
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 428
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 429
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 430
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 431
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 432
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 433
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 434
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 435
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 436
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 437
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 438
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 439
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 440
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 441
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 442
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 443
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 444
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 445
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 446
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 447
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 448
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 449
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 450
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 451
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 452
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 453
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 454
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 455
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 456
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 457
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 458
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 459
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 460
    0xd5033abf, // dmb.ishst
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 461
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 462
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 463
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 464
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 465
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 466
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 467
    0xd5033abf, // dmb.ishst
    0xd50339bf, // dmb.ishld
    0xd503201f, // nop
    // case 468
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 469
    0xd5033abf, // dmb.ishst
    0xd503201f, // nop
    // case 470
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 471
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 472
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 473
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 474
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 475
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 476
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 477
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 478
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 479
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 480
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 481
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 482
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 483
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 484
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 485
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 486
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 487
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 488
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 489
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 490
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 491
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 492
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 493
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 494
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 495
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 496
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 497
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 498
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 499
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 500
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 501
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 502
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 503
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 504
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 505
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 506
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 507
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 508
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 509
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 510
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 511
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 512
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 513
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 514
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 515
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 516
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 517
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 518
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 519
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 520
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 521
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 522
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 523
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 524
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 525
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 526
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 527
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 528
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 529
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 530
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 531
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 532
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 533
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 534
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 535
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 536
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 537
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 538
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 539
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 540
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 541
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 542
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 543
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 544
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 545
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 546
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 547
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 548
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 549
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 550
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 551
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 552
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 553
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 554
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 555
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 556
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 557
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 558
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 559
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 560
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 561
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 562
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 563
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 564
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 565
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 566
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 567
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 568
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 569
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 570
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 571
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 572
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 573
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 574
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 575
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 576
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 577
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 578
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 579
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 580
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 581
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 582
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 583
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 584
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 585
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 586
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 587
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 588
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 589
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 590
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 591
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 592
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 593
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 594
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 595
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 596
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 597
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 598
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 599
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 600
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 601
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 602
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 603
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 604
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 605
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 606
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 607
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 608
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 609
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 610
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 611
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 612
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 613
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 614
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 615
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 616
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 617
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 618
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 619
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 620
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 621
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 622
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 623
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 624
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
    // case 625
    0xd5033bbf, // dmb.ish
    0xd503201f, // nop
  };
  if (AlwaysMergeDMB) {
    EXPECT_EQ(code.insts()->size(), (CodeSection::csize_t)(sizeof insns1));
    asm_check((const unsigned int *)code.insts()->start(), insns1, sizeof insns1 / sizeof insns1[0]);
  } else {
    EXPECT_EQ(code.insts()->size(), (CodeSection::csize_t)(sizeof insns2));
    asm_check((const unsigned int *)code.insts()->start(), insns2, sizeof insns2 / sizeof insns2[0]);
  }

  BufferBlob::free(b);
}

TEST_VM(AssemblerAArch64, merge_ldst) {
  BufferBlob* b = BufferBlob::create("aarch64Test", 400);
  CodeBuffer code(b);
  MacroAssembler _masm(&code);

  {
    Label l;
    // merge ld/st into ldp/stp
    __ ldr(r0, Address(sp, 8));
    __ ldr(r1, Address(sp, 0));
    __ nop();
    __ str(r0, Address(sp, 0));
    __ str(r1, Address(sp, 8));
    __ nop();
    __ ldrw(r0, Address(sp, 0));
    __ ldrw(r1, Address(sp, 4));
    __ nop();
    __ strw(r0, Address(sp, 4));
    __ strw(r1, Address(sp, 0));
    __ nop();
    // can not merge
    __ ldrw(r0, Address(sp, 4));
    __ ldr(r1, Address(sp, 8));
    __ nop();
    __ ldrw(r0, Address(sp, 0));
    __ ldrw(r1, Address(sp, 8));
    __ nop();
    __ str(r0, Address(sp, 0));
    __ bind(l);                     // block by label
    __ str(r1, Address(sp, 8));
    __ nop();
  }
  asm_dump(code.insts()->start(), code.insts()->end());
  static const unsigned int insns1[] = {
    0xa94003e1, // ldp x1, x0, [sp]
    0xd503201f, // nop
    0xa90007e0, // stp x0, x1, [sp]
    0xd503201f, // nop
    0x294007e0, // ldp w0, w1, [sp]
    0xd503201f, // nop
    0x290003e1, // stp w1, w0, [sp]
    0xd503201f, // nop
    0xb94007e0, // ldr w0, [sp, 4]
    0xf94007e1, // ldr x1, [sp, 8]
    0xd503201f, // nop
    0xb94003e0, // ldr w0, [sp]
    0xb9400be1, // ldr w1, [sp, 8]
    0xd503201f, // nop
    0xf90003e0, // str x0, [sp]
    0xf90007e1, // str x1, [sp, 8]
    0xd503201f, // nop
  };
  EXPECT_EQ(code.insts()->size(), (CodeSection::csize_t)(sizeof insns1));
  asm_check((const unsigned int *)code.insts()->start(), insns1, sizeof insns1 / sizeof insns1[0]);

  BufferBlob::free(b);
}

TEST_VM(AssemblerAArch64, merge_ldst_after_expand) {
  ResourceMark rm;
  BufferBlob* b = BufferBlob::create("aarch64Test", 400);
  CodeBuffer code(b);
  code.set_blob(b);
  MacroAssembler _masm(&code);

  {
    __ ldr(r0, Address(sp, 8));
    code.insts()->maybe_expand_to_ensure_remaining(10000);
    __ ldr(r1, Address(sp, 0));
    __ nop();
    __ str(r0, Address(sp, 0));
    code.insts()->maybe_expand_to_ensure_remaining(100000);
    __ str(r1, Address(sp, 8));
    __ nop();
  }
  asm_dump(code.insts()->start(), code.insts()->end());
  static const unsigned int insns[] = {
    0xa94003e1, // ldp x1, x0, [sp]
    0xd503201f, // nop
    0xa90007e0, // stp x0, x1, [sp]
    0xd503201f, // nop
  };
  EXPECT_EQ(code.insts()->size(), (CodeSection::csize_t)(sizeof insns));
  asm_check((const unsigned int *)code.insts()->start(), insns, sizeof insns / sizeof insns[0]);
}

#endif  // AARCH64
