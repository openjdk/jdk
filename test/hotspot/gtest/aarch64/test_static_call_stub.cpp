/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#if defined(AARCH64) && !defined(ZERO)

#include "asm/macroAssembler.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "code/relocInfo.hpp"
#include "memory/resourceArea.hpp"
#include "nativeInst_aarch64.hpp"
#include "oops/method.hpp"
#include "runtime/sharedRuntime.hpp"
#include "unittest.hpp"
#include "utilities/align.hpp"

#define __ _masm.

static address emit_static_call_stub(MacroAssembler& _masm) {
  EXPECT_TRUE(__ ensure_static_call_dispatch_adapter());
  EXPECT_NE(__ start_a_stub(MacroAssembler::max_static_call_stub_size()), nullptr);
  address body = __ pc();
  __ emit_static_call_stub();
  const int emitted = (int)(__ pc() - body);
  EXPECT_EQ(emitted, nativeStaticCallStub_at(body)->size());
  EXPECT_LE(emitted, (int)NativeStaticCallStub::max_instruction_size);
  __ end_a_stub();
  return body;
}

static void init_stubs_section(CodeBuffer* code, int reloc_count = 4) {
  code->initialize_stubs_size(256);
  code->stubs()->initialize_shared_locs(NEW_RESOURCE_ARRAY(relocInfo, reloc_count), reloc_count);
}

static void count_and_check_metadata_relocs(CodeBuffer& code, const int* stub_offsets, int n) {
  int metadata_count = 0;
  RelocIterator iter(code.stubs());
  while (iter.next()) {
    if (iter.type() != relocInfo::metadata_type) {
      continue;
    }
    address at = iter.addr();
    EXPECT_TRUE(NativeInstruction::is_ldr_gpr_literal_at(at))
        << "reloc anchored on non-ldr at offset " << (intptr_t)(at - code.stubs()->start());

    bool matched = false;
    for (int i = 0; i < n; i++) {
      if (at == code.stubs()->start() + stub_offsets[i]) {
        matched = true;
        break;
      }
    }

    EXPECT_TRUE(matched) << "reloc not anchored on a known stub body";
    metadata_count++;
    EXPECT_FALSE(iter.metadata_reloc()->metadata_is_immediate())
        << "stub metadata reloc must be indexed, not immediate";
  }
  EXPECT_EQ(metadata_count, n) << "expected one metadata reloc per stub";
}

static address decode_stub_branch_target(address stub) {
  const uint32_t* w = (const uint32_t*)stub;
  int64_t br_off = (((int64_t)(int32_t)(w[1] << 6)) >> 6) << 2; // sign-extend imm26<<2
  return stub + NativeInstruction::instruction_size + br_off;
}

TEST_VM(StaticCallStub, layout) {
  ResourceMark rm;

  CodeBuffer code("staticCallStubTest", 512, 0);
  ASSERT_NE(code.blob(), nullptr);
  init_stubs_section(&code);
  MacroAssembler _masm(&code);

  address stub = emit_static_call_stub(_masm);
  const uint32_t* w = (const uint32_t*)stub;

  // word 0: ldr rmethod, <slot literal>.
  // word 1: unconditional backward b.
  EXPECT_TRUE(NativeInstruction::is_ldr_gpr_literal_at(stub)) << "word 0 not ldr-literal";
  EXPECT_EQ(w[0] & 0x1fu, (uint32_t)rmethod->encoding()) << "word 0 Rt not rmethod";
  EXPECT_EQ(w[1] >> 26, 0b000101u) << "word 1 not unconditional b";

  // The slot after the body might be padded to make it 8-byte aligned.
  NativeStaticCallStub* ncs = nativeStaticCallStub_at(stub);
  address slot = align_up(stub + NativeStaticCallStub::body_size, wordSize);
  EXPECT_EQ(ncs->size(), (int)(slot + wordSize - stub));
  int64_t ldr_imm19 = ((int64_t)(int32_t)(w[0] << 8)) >> 13; // sign-extend imm19
  EXPECT_EQ(stub + ldr_imm19 * 4, slot) << "ldr-literal does not target the slot";

  address br_target = decode_stub_branch_target(stub);
  EXPECT_EQ(br_target, code.stubs()->start() + code.static_call_dispatch_adapter_offset())
      << "b does not target the dispatch adapter";
  EXPECT_TRUE(is_NativeStaticCallStub_at(stub));
}

TEST_VM(StaticCallStub, set_method) {
  ResourceMark rm;

  CodeBuffer code("staticCallStubTest", 512, 0);
  ASSERT_NE(code.blob(), nullptr);
  init_stubs_section(&code);
  MacroAssembler _masm(&code);

  address stub = emit_static_call_stub(_masm);

  NativeStaticCallStub* ncs = nativeStaticCallStub_at(stub);
  EXPECT_EQ(ncs->method(), (Method*)nullptr);

  Method* fake = (Method*)(intptr_t)0xdeadbeef00;
  ncs->set_method(fake);
  EXPECT_EQ(ncs->method(), fake);
  ncs->set_method(nullptr);
  EXPECT_EQ(ncs->method(), (Method*)nullptr);
}

TEST_VM(StaticCallStub, relocs) {
  ResourceMark rm;

  CodeBuffer code("staticCallStubTest", 512, 0);
  ASSERT_NE(code.blob(), nullptr);
  init_stubs_section(&code, 8);
  MacroAssembler _masm(&code);

  address stubs_before = code.stubs()->start();

  constexpr int N = 4;
  int stub_offsets[N];
  for (int i = 0; i < N; i++) {
    address stub = emit_static_call_stub(_masm);
    ASSERT_NE(stub, nullptr);
    stub_offsets[i] = (int)(stub - code.stubs()->start());
  }

  EXPECT_EQ(code.stubs()->start(), stubs_before);

  // As the adapter is up to 28 bytes, the first stub might need padding. Its size might
  // be 20 bytes. Later stubs need no padding. Their size will be 16 bytes.
  int expected_size = NativeStaticCallStub::body_size * N + wordSize * N +
                      MacroAssembler::max_static_call_dispatch_adapter_size() +
                      NativeInstruction::instruction_size;
  EXPECT_LE((int)code.stubs()->size(), expected_size);

  count_and_check_metadata_relocs(code, stub_offsets, N);
}

TEST_VM(StaticCallStub, buffer_expansion) {
  ResourceMark rm;

  CodeBuffer code("staticCallStubTest", 512, 0);
  ASSERT_NE(code.blob(), nullptr);
  init_stubs_section(&code, 12);
  MacroAssembler _masm(&code);

  constexpr int N = 6;
  int stub_offsets[N];

  for (int i = 0; i < N; i++) {
    stub_offsets[i] = (int)(emit_static_call_stub(_masm) - code.stubs()->start());

    if (i == 3) {
      address stubs_before = code.stubs()->start();
      code.insts()->maybe_expand_to_ensure_remaining(1024);
      ASSERT_NE(code.stubs()->start(), stubs_before);
      EXPECT_EQ(code.static_call_dispatch_adapter_offset(), 0);
    }
  }

  for (int i = 0; i < N; i++) {
    address stub = code.stubs()->start() + stub_offsets[i];
    EXPECT_TRUE(is_NativeStaticCallStub_at(stub)) << "stub " << i << " invalid after expansion";
    EXPECT_EQ(decode_stub_branch_target(stub), code.stubs()->start())
        << "stub " << i << " backward b does not target the adapter";
  }

  count_and_check_metadata_relocs(code, stub_offsets, N);
}

#endif // AARCH64 && !ZERO
