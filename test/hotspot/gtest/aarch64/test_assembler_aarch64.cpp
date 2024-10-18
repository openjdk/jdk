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
#include "nativeInst_aarch64.hpp"
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

constexpr uint32_t test_encode_dmb_ld = 0xd50339bf;
constexpr uint32_t test_encode_dmb_st = 0xd5033abf;
constexpr uint32_t test_encode_dmb    = 0xd5033bbf;
constexpr uint32_t test_encode_nop    = 0xd503201f;

static void asm_dump(address start, address end) {
  ResourceMark rm;
  stringStream ss;
  ss.print_cr("Insns:");
  Disassembler::decode(start, end, &ss);
  printf("%s\n", ss.as_string());
}

void test_merge_dmb() {
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
    test_encode_dmb_st,
    test_encode_nop,
    test_encode_dmb_ld,
    test_encode_nop,
    test_encode_dmb,
    test_encode_nop,
    test_encode_dmb,
  };
  // !AlwaysMergeDMB
  static const unsigned int insns2[] = {
    test_encode_dmb_st,
    test_encode_nop,
    test_encode_dmb_ld,
    test_encode_nop,
    test_encode_dmb,
    test_encode_nop,
    test_encode_dmb_ld,
    test_encode_dmb_st,
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

TEST_VM(AssemblerAArch64, merge_dmb_1) {
  FlagSetting fs(AlwaysMergeDMB, true);
  test_merge_dmb();
}

TEST_VM(AssemblerAArch64, merge_dmb_2) {
  FlagSetting fs(AlwaysMergeDMB, false);
  test_merge_dmb();
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

void expect_dmbld(void* addr) {
  if (*((uint32_t *) addr) != test_encode_dmb_ld) {
    tty->print_cr("Expected dmb.ld");
    FAIL();
  }
}

void expect_dmbst(void* addr) {
  if (*((uint32_t *) addr) != test_encode_dmb_st) {
    tty->print_cr("Expected dmb.st");
    FAIL();
  }
}

void expect_dmb(void* addr) {
  if (*((uint32_t *) addr) != test_encode_dmb) {
    tty->print_cr("Expected dmb");
    FAIL();
  }
}

void expect_any_dmb(void* addr) {
  uint32_t encode = *((uint32_t *) addr);
  if (encode != test_encode_dmb && encode != test_encode_dmb_ld && encode != test_encode_dmb_st) {
    tty->print_cr("Expected a dmb.* instruction");
    FAIL();
  }
}

void expect_different_dmb_kind(void* addr) {
  uint32_t pos1 = *((uint32_t *) addr);
  uint32_t pos2 = *(((uint32_t *) addr) + 1);
  if (pos1 == pos2) {
    tty->print_cr("Expected different dmb kind");
    FAIL();
  }
}

void expect_dmb_at_least_one(void* addr) {
  uint32_t pos1 = *((uint32_t *) addr);
  uint32_t pos2 = *(((uint32_t *) addr) + 1);
  if (pos1 != test_encode_dmb && pos2 != test_encode_dmb) {
    tty->print_cr("Expected at least one dmb");
    FAIL();
  }
}

void expect_dmb_none(void* addr) {
  uint32_t pos1 = *((uint32_t *) addr);
  uint32_t pos2 = *(((uint32_t *) addr) + 1);
  if (pos1 == test_encode_dmb || pos2 == test_encode_dmb) {
    tty->print_cr("Expected no dmb");
    FAIL();
  }
}

void test_merge_dmb_all_kinds() {
  BufferBlob* b = BufferBlob::create("aarch64Test", 20000);
  CodeBuffer code(b);
  MacroAssembler _masm(&code);

  constexpr int count = 5;
  struct {
    const char* label;
    Assembler::Membar_mask_bits flavor;
    // Two groups of two bits describing the ordering, can be OR-ed to figure out composite semantics.
    // First group describes ops before the barrier. Second group describes ops after the barrier.
    // "01" means "load", "10" means "store", "100" means "any".
    int mask;
  } kind[count] = {
          {"storestore", Assembler::StoreStore, 0b010010},
          {"loadstore",  Assembler::LoadStore,  0b001010},
          {"loadload",   Assembler::LoadLoad,   0b001001},
          {"storeload",  Assembler::StoreLoad,  0b100100}, // quirk: StoreLoad is as powerful as AnyAny
          {"anyany",     Assembler::AnyAny,     0b100100},
  };

  for (int b1 = 0; b1 < count; b1++) {
    for (int b2 = 0; b2 < count; b2++) {
      for (int b3 = 0; b3 < count; b3++) {
        for (int b4 = 0; b4 < count; b4++) {
          // tty->print_cr("%s + %s + %s + %s", kind[b1].label, kind[b2].label, kind[b3].label, kind[b4].label);

          address start = __ pc();
          __ membar(kind[b1].flavor);
          __ membar(kind[b2].flavor);
          __ membar(kind[b3].flavor);
          __ membar(kind[b4].flavor);
          address end = __ pc();
          __ nop();

          size_t size = pointer_delta(end, start, 1);
          if (AlwaysMergeDMB) {
            // Expect only a single barrier.
            EXPECT_EQ(size, (size_t) NativeMembar::instruction_size);
          } else {
            EXPECT_LE(size, (size_t) NativeMembar::instruction_size * 2);
          }

          // Composite ordering for this group of barriers.
          int composite_mask = kind[b1].mask | kind[b2].mask | kind[b3].mask | kind[b4].mask;

          if (size == NativeMembar::instruction_size) {
            // If there is a single barrier, we can easily test its type.
            switch (composite_mask) {
              case 0b001001:
              case 0b001010:
              case 0b001011:
              case 0b001101:
              case 0b001110:
              case 0b001111:
                // Any combination of Load(Load|Store|Any) gets dmb.ld
                expect_dmbld(start);
                break;
              case 0b010010:
                // Only StoreStore gets dmb.st
                expect_dmbst(start);
                break;
              default:
                // Everything else gets folded into full dmb
                expect_dmb(start);
                break;
            }
          } else if (size == 2 * NativeMembar::instruction_size) {
            // There are two barriers. Make a few sanity checks.
            // They must be different kind
            expect_any_dmb(start);
            expect_any_dmb(start + NativeMembar::instruction_size);
            expect_different_dmb_kind(start);
            if ((composite_mask & 0b100100) != 0) {
              // There was "any" barrier in the group, a full dmb is expected
              expect_dmb_at_least_one(start);
            } else {
              // Otherwise expect no full dmb
              expect_dmb_none(start);
            }
          } else {
            // Merging code does not produce this result.
            FAIL();
          }
        }
      }
    }
  }

  BufferBlob::free(b);
}

TEST_VM(AssemblerAArch64, merge_dmb_all_kinds_1) {
  FlagSetting fs(AlwaysMergeDMB, true);
  test_merge_dmb_all_kinds();
}

TEST_VM(AssemblerAArch64, merge_dmb_all_kinds_2) {
  FlagSetting fs(AlwaysMergeDMB, false);
  test_merge_dmb_all_kinds();
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
