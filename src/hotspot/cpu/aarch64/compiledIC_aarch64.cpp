/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2018, Red Hat Inc. All rights reserved.
 * Copyright 2025 Arm Limited and/or its affiliates.
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

#include "asm/macroAssembler.inline.hpp"
#include "code/compiledIC.hpp"
#include "code/nmethod.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"

// ----------------------------------------------------------------------------

#define __ masm->
address CompiledDirectCall::emit_to_interp_stub(MacroAssembler *masm, address mark) {
  precond(__ code()->stubs()->start() != badAddress);
  precond(__ code()->stubs()->end() != badAddress);

  // Stub is fixed up when the corresponding call is converted from
  // calling compiled code to calling interpreted code.
  // mov rmethod, 0
  // jmp -4 # to self

  if (mark == nullptr) {
    mark = __ inst_mark();  // Get mark within main instrs section.
  }

  address base = __ start_a_stub(to_interp_stub_size());
  int offset = __ offset();
  if (base == nullptr) {
    return nullptr;  // CodeBuffer::expand failed
  }
  // static stub relocation stores the instruction address of the call
  __ relocate(static_stub_Relocation::spec(mark));

  {
    __ emit_static_call_stub();
  }

  assert((__ offset() - offset) <= (int)to_interp_stub_size(), "stub too big");
  __ end_a_stub();
  return base;
}
#undef __

int CompiledDirectCall::to_interp_stub_size() {
  return MacroAssembler::static_call_stub_size();
}

int CompiledDirectCall::to_trampoline_stub_size() {
  // Somewhat pessimistically, we count 3 instructions here (although
  // there are only two) because we sometimes emit an alignment nop.
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
    = nativeMovConstReg_at(stub + NativeInstruction::instruction_size);

#ifdef ASSERT
  NativeJump* jump = MacroAssembler::codestub_branch_needs_far_jump()
                         ? nativeGeneralJump_at(method_holder->next_instruction_address())
                         : nativeJump_at(method_holder->next_instruction_address());
  verify_mt_safe(callee, entry, method_holder, jump);
#endif

  // Update stub.
  method_holder->set_data((intptr_t)callee());
  MacroAssembler::pd_patch_instruction(method_holder->next_instruction_address(), entry);
  ICache::invalidate_range(stub, to_interp_stub_size());

  // This code is executed while other threads are running. We must
  // ensure that at all times the execution path is valid. A racing
  // thread either observes a call (possibly via a trampoline) to
  // SharedRuntime::resolve_static_call_C or a complete call to the
  // interpreter.
  //
  // If a racing thread observes an updated direct branch at a call
  // site, it must also observe all of the updated instructions in the
  // static call stub.
  //
  // To ensure this, we first update the static call stub, then the
  // trampoline, and finally the direct branch at the call site.
  //
  // AArch64 stub_via_BL
  // {
  // 0:X0=instr:"MOV w0, #2";
  // 0:X1=instr:"BL .+16";
  // 0:X10=P1:new;
  // 0:X11=P1:L0;
  // }
  //
  // P0              |  P1            ;
  // STR W0, [X10]   |L0:             ;
  // DC CVAU, X10    |  BL old        ;
  // DSB ISH         |  B end         ;
  // IC IVAU, X10    |old:            ;
  // DSB ISH         |  MOV w0, #0    ;
  //                 |  RET           ;
  // STR W1, [X11]   |new:            ;
  //                 |  MOV w0, #1    ;
  //                 |  RET           ;
  //                 |end:            ;
  // forall(1:X0=0 \/ 1:X0=2)
  //
  // We maintain an invariant: every call site either points directly
  // to the call destination or to the call site's trampoline. The
  // trampoline points to the call destination. Even if the trampoline
  // is not in use, and therefore not reachable, it still points to
  // the call destination.
  //
  // If a racing thread reaches the static call stub via the trampoline,
  // we must ensure that it observes the fully updated 'MOV' instructions.
  // Initially we place an ISB at the start of the static call stub.
  // After updating the 'MOV's, we rewrite the ISB with 'B .+4'. A racing
  // thread either observes the ISB or the branch. Once the stub has been
  // rewritten and the instruction and data caches have been synchronized
  // to the point of unification by ICache::invalidate_range, either
  // observation is sufficient to ensure that the subsequent instructions
  // are observed.
  //
  // As confirmed by the litmus test below, when a racing executing
  // thread reaches the static call stub:
  //   - If it observes the 'B .+4', it will also observe the updated 'MOV's.
  //   - Or, it will execute the 'ISB' - the instruction fetch ensures
  //     the updated 'MOV's are observed.
  //
  // AArch64 stub_via_BR
  // {
  // [target] = P1:old;
  //
  //                               1:X0 = 0;
  // 0:X1 = instr:"MOV X0, #3";
  // 0:X2 = instr:"b .+4";
  // 0:X3 = target;                1:X3 = target;
  // 0:X4 = P1:new;
  // 0:X5 = P1:patch;
  // }
  //
  // P0                          | P1                        ;
  // STR W1, [X5]                |  LDR X2, [X3]             ;
  // DC CVAU, X5                 |  BR X2                    ;
  // DSB ISH                     |new:                       ;
  // IC IVAU, X5                 |  ISB                      ;
  // DSB ISH                     |patch:                     ;
  // STR W2, [X4]                |  MOV X0, #2               ;
  // STR X4, [X3]                |  B end                    ;
  //                             |old:                       ;
  //                             |  MOV X0, #1               ;
  //                             |  B end                    ;
  //                             |end:                       ;
  // forall (1:X0=1 \/ 1:X0=3)

  NativeJump::insert(stub, stub + NativeJump::instruction_size);

  address trampoline_stub_addr = _call->get_trampoline();
  if (trampoline_stub_addr != nullptr) {
    nativeCallTrampolineStub_at(trampoline_stub_addr)->set_destination(stub);
  }

  // Update jump to call.
  _call->set_destination(stub);
}

void CompiledDirectCall::set_stub_to_clean(static_stub_Relocation* static_stub) {
  // Reset stub.
  address stub = static_stub->addr();
  assert(stub != nullptr, "stub not found");
  assert(CompiledICLocker::is_safe(stub), "mt unsafe call");
  // Patch 'b .+4' to 'isb'.
  CodeBuffer stub_first_instruction(stub, Assembler::instruction_size);
  Assembler assembler(&stub_first_instruction);
  assembler.isb();
  // Creation also verifies the object.
  NativeMovConstReg* method_holder
    = nativeMovConstReg_at(stub + NativeInstruction::instruction_size);
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
    = nativeMovConstReg_at(stub + NativeInstruction::instruction_size);
  NativeJump* jump = nativeJump_at(method_holder->next_instruction_address());

  // Verify state.
  assert(is_clean() || is_call_to_compiled() || is_call_to_interpreted(), "sanity check");
}

#endif // !PRODUCT
