/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeCache.hpp"
#include "code/compiledIC.hpp"
#include "code/nmethod.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"

// ----------------------------------------------------------------------------

#define __ masm->
address CompiledDirectCall::emit_to_interp_stub(MacroAssembler *masm, address mark) {
  // Stub is fixed up when the corresponding call is converted from
  // calling compiled code to calling interpreted code.
  // movq rbx, 0
  // jmp -5 # to self

  if (mark == nullptr) {
    mark = __ inst_mark();  // Get mark within main instrs section.
  }

  address base = __ start_a_stub(to_interp_stub_size());
  if (base == nullptr) {
    return nullptr;  // CodeBuffer::expand failed.
  }
  // Static stub relocation stores the instruction address of the call.
  __ relocate(static_stub_Relocation::spec(mark), Assembler::imm_operand);
  __ emit_static_call_stub();

  assert(__ pc() - base <= to_interp_stub_size(), "wrong stub size");

  // Update current stubs pointer and restore insts_end.
  __ end_a_stub();
  return base;
}
#undef __

int CompiledDirectCall::to_interp_stub_size() {
  return NOT_LP64(10)    // movl; jmp
         LP64_ONLY(15);  // movq (1+1+8); jmp (1+4)
}

int CompiledDirectCall::to_trampoline_stub_size() {
  // x86 doesn't use trampolines.
  return 0;
}

// Relocation entries for call stub, compiled java to interpreter.
int CompiledDirectCall::reloc_to_interp_stub() {
  return 4; // 3 in emit_to_interp_stub + 1 in emit_call
}

void CompiledDirectCall::set_to_interpreted(const methodHandle& callee, address entry) {
  address stub = find_stub();
  guarantee(stub != nullptr, "stub not found");

  // Creation also verifies the object.
  NativeMovConstReg* method_holder = nativeMovConstReg_at(stub);
  NativeJump*        jump          = nativeJump_at(method_holder->next_instruction_address());
  verify_mt_safe(callee, entry, method_holder, jump);

  // Update stub.
  method_holder->set_data((intptr_t)callee());
  jump->set_jump_destination(entry);

  // Update jump to call.
  set_destination_mt_safe(stub);
}

void CompiledDirectCall::set_stub_to_clean(static_stub_Relocation* static_stub) {
  assert(CompiledICLocker::is_safe(static_stub->addr()), "mt unsafe call");
  // Reset stub.
  address stub = static_stub->addr();
  assert(stub != nullptr, "stub not found");
  // Creation also verifies the object.
  NativeMovConstReg* method_holder = nativeMovConstReg_at(stub);
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

#ifdef ASSERT
  CodeBlob *cb = CodeCache::find_blob((address) _call);
  assert(cb != nullptr, "sanity");
#endif

  // Verify stub.
  address stub = find_stub();
  assert(stub != nullptr, "no stub found for static call");
  // Creation also verifies the object.
  NativeMovConstReg* method_holder = nativeMovConstReg_at(stub);
  NativeJump*        jump          = nativeJump_at(method_holder->next_instruction_address());

  // Verify state.
  assert(is_clean() || is_call_to_compiled() || is_call_to_interpreted(), "sanity check");
}
#endif // !PRODUCT
