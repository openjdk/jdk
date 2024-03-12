/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2018, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2021, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "code/compiledIC.hpp"
#include "code/nmethod.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"

// ----------------------------------------------------------------------------

#define __ _masm.
address CompiledDirectCall::emit_to_interp_stub(CodeBuffer &cbuf, address mark) {
  precond(cbuf.stubs()->start() != badAddress);
  precond(cbuf.stubs()->end() != badAddress);
  // Stub is fixed up when the corresponding call is converted from
  // calling compiled code to calling interpreted code.
  // mv xmethod, 0
  // jalr -4 # to self

  if (mark == nullptr) {
    mark = cbuf.insts_mark();  // Get mark within main instrs section.
  }

  // Note that the code buffer's insts_mark is always relative to insts.
  // That's why we must use the macroassembler to generate a stub.
  MacroAssembler _masm(&cbuf);

  address base = __ start_a_stub(to_interp_stub_size());
  int offset = __ offset();
  if (base == nullptr) {
    return nullptr;  // CodeBuffer::expand failed
  }
  // static stub relocation stores the instruction address of the call
  __ relocate(static_stub_Relocation::spec(mark));

  __ emit_static_call_stub();

  assert((__ offset() - offset) <= (int)to_interp_stub_size(), "stub too big");
  __ end_a_stub();
  return base;
}
#undef __

int CompiledDirectCall::to_interp_stub_size() {
  return MacroAssembler::static_call_stub_size();
}

int CompiledDirectCall::to_trampoline_stub_size() {
  // Somewhat pessimistically, we count 4 instructions here (although
  // there are only 3) because we sometimes emit an alignment nop.
  // Trampoline stubs are always word aligned.
  return MacroAssembler::max_trampoline_stub_size();
}

// Relocation entries for call stub, compiled java to interpreter.
int CompiledDirectCall::reloc_to_interp_stub() {
  return 4; // 3 in emit_to_interp_stub + 1 in emit_call
}

void CompiledDirectCall::set_to_interpreted(const methodHandle& callee, address entry) {
  address stub = find_stub();
  guarantee(stub != nullptr, "stub not found");

  // Creation also verifies the object.
  NativeMovConstReg* method_holder
    = nativeMovConstReg_at(stub);
#ifdef ASSERT
  NativeGeneralJump* jump = nativeGeneralJump_at(method_holder->next_instruction_address());

  verify_mt_safe(callee, entry, method_holder, jump);
#endif
  // Update stub.
  method_holder->set_data((intptr_t)callee());
  NativeGeneralJump::insert_unconditional(method_holder->next_instruction_address(), entry);
  ICache::invalidate_range(stub, to_interp_stub_size());
  // Update jump to call.
  set_destination_mt_safe(stub);
}

void CompiledDirectCall::set_stub_to_clean(static_stub_Relocation* static_stub) {
  // Reset stub.
  address stub = static_stub->addr();
  assert(stub != nullptr, "stub not found");
  assert(CompiledICLocker::is_safe(stub), "mt unsafe call");
  // Creation also verifies the object.
  NativeMovConstReg* method_holder
    = nativeMovConstReg_at(stub);
  method_holder->set_data(0);
  NativeJump* jump = nativeJump_at(method_holder->next_instruction_address());
  jump->set_jump_destination((address)-1);
}

//-----------------------------------------------------------------------------
// Non-product mode code
#ifndef PRODUCT

void CompiledDirectCall::verify() {
  // Verify call.
  _call->verify();
  _call->verify_alignment();

  // Verify stub.
  address stub = find_stub();
  assert(stub != nullptr, "no stub found for static call");
  // Creation also verifies the object.
  NativeMovConstReg* method_holder
    = nativeMovConstReg_at(stub);
  NativeJump* jump = nativeJump_at(method_holder->next_instruction_address());

  // Verify state.
  assert(is_clean() || is_call_to_compiled() || is_call_to_interpreted(), "sanity check");
}

#endif // !PRODUCT
