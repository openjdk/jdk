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
#include "code/compiledIC.hpp"
#include "code/nativeInst.hpp"
#include "code/nmethod.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"

// ----------------------------------------------------------------------------
#if COMPILER2_OR_JVMCI
#define __ _masm.
// emit call stub, compiled java to interpreter
address CompiledDirectCall::emit_to_interp_stub(CodeBuffer &cbuf, address mark) {
  // Stub is fixed up when the corresponding call is converted from calling
  // compiled code to calling interpreted code.
  // set (empty), R9
  // b -1

  if (mark == nullptr) {
    mark = cbuf.insts_mark();  // get mark within main instrs section
  }

  MacroAssembler _masm(&cbuf);

  address base = __ start_a_stub(to_interp_stub_size());
  if (base == nullptr) {
    return nullptr;  // CodeBuffer::expand failed
  }

  // static stub relocation stores the instruction address of the call
  __ relocate(static_stub_Relocation::spec(mark));

  InlinedMetadata object_literal(nullptr);
  // single instruction, see NativeMovConstReg::next_instruction_address() in
  // CompiledDirectCall::set_to_interpreted()
  __ ldr_literal(Rmethod, object_literal);

  __ set_inst_mark(); // Who uses this?

  bool near_range = __ cache_fully_reachable();
  InlinedAddress dest((address)-1);
  address branch_site = __ pc();
  if (near_range) {
    __ b(branch_site); // special NativeJump -1 destination
  } else {
    // Can't trash LR, FP, or argument registers
    __ indirect_jump(dest, Rtemp);
  }
  __ bind_literal(object_literal); // includes spec_for_immediate reloc
  if (!near_range) {
    __ bind_literal(dest); // special NativeJump -1 destination
  }

  assert(__ pc() - base <= to_interp_stub_size(), "wrong stub size");

  // Update current stubs pointer and restore code_end.
  __ end_a_stub();
  return base;
}
#undef __

// Relocation entries for call stub, compiled java to interpreter.
int CompiledDirectCall::reloc_to_interp_stub() {
  return 10;  // 4 in emit_to_interp_stub + 1 in Java_Static_Call
}
#endif // COMPILER2_OR_JVMCI

int CompiledDirectCall::to_trampoline_stub_size() {
  // ARM doesn't use trampolines.
  return 0;
}

// size of C2 call stub, compiled java to interpreter
int CompiledDirectCall::to_interp_stub_size() {
  return 8 * NativeInstruction::instruction_size;
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
  NativeMovConstReg* method_holder = nativeMovConstReg_at(stub);
  NativeJump*        jump          = nativeJump_at(method_holder->next_instruction_address());
  method_holder->set_data(0);
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
  NativeMovConstReg* method_holder = nativeMovConstReg_at(stub);
  NativeJump*        jump          = nativeJump_at(method_holder->next_instruction_address());

  // Verify state.
  assert(is_clean() || is_call_to_compiled() || is_call_to_interpreted(), "sanity check");
}

#endif // !PRODUCT
