/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "code/codeBlob.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zBarrierSet.hpp"
#include "gc/z/zBarrierSetAssembler.hpp"
#include "gc/z/zBarrierSetRuntime.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER1
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "gc/z/c1/zBarrierSetC1.hpp"
#endif // COMPILER1

ZBarrierSetAssembler::ZBarrierSetAssembler() :
    _load_barrier_slow_stub(),
    _load_barrier_weak_slow_stub() {}

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#undef __
#define __ masm->

static void call_vm(MacroAssembler* masm,
                    address entry_point,
                    Register arg0,
                    Register arg1) {
  // Setup arguments
  if (arg1 == c_rarg0) {
    if (arg0 == c_rarg1) {
      __ xchgptr(c_rarg1, c_rarg0);
    } else {
      __ movptr(c_rarg1, arg1);
      __ movptr(c_rarg0, arg0);
    }
  } else {
    if (arg0 != c_rarg0) {
      __ movptr(c_rarg0, arg0);
    }
    if (arg1 != c_rarg1) {
      __ movptr(c_rarg1, arg1);
    }
  }

  // Call VM
  __ MacroAssembler::call_VM_leaf_base(entry_point, 2);
}

void ZBarrierSetAssembler::load_at(MacroAssembler* masm,
                                   DecoratorSet decorators,
                                   BasicType type,
                                   Register dst,
                                   Address src,
                                   Register tmp1,
                                   Register tmp_thread) {
  if (!ZBarrierSet::barrier_needed(decorators, type)) {
    // Barrier not needed
    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1, tmp_thread);
    return;
  }

  BLOCK_COMMENT("ZBarrierSetAssembler::load_at {");

  // Allocate scratch register
  Register scratch = tmp1;
  if (tmp1 == noreg) {
    scratch = r12;
    __ push(scratch);
  }

  assert_different_registers(dst, scratch);

  Label done;

  //
  // Fast Path
  //

  // Load address
  __ lea(scratch, src);

  // Load oop at address
  __ movptr(dst, Address(scratch, 0));

  // Test address bad mask
  __ testptr(dst, address_bad_mask_from_thread(r15_thread));
  __ jcc(Assembler::zero, done);

  //
  // Slow path
  //

  // Save registers
  __ push(rax);
  __ push(rcx);
  __ push(rdx);
  __ push(rdi);
  __ push(rsi);
  __ push(r8);
  __ push(r9);
  __ push(r10);
  __ push(r11);

  // We may end up here from generate_native_wrapper, then the method may have
  // floats as arguments, and we must spill them before calling the VM runtime
  // leaf. From the interpreter all floats are passed on the stack.
  assert(Argument::n_float_register_parameters_j == 8, "Assumption");
  const int xmm_size = wordSize * 2;
  const int xmm_spill_size = xmm_size * Argument::n_float_register_parameters_j;
  __ subptr(rsp, xmm_spill_size);
  __ movdqu(Address(rsp, xmm_size * 7), xmm7);
  __ movdqu(Address(rsp, xmm_size * 6), xmm6);
  __ movdqu(Address(rsp, xmm_size * 5), xmm5);
  __ movdqu(Address(rsp, xmm_size * 4), xmm4);
  __ movdqu(Address(rsp, xmm_size * 3), xmm3);
  __ movdqu(Address(rsp, xmm_size * 2), xmm2);
  __ movdqu(Address(rsp, xmm_size * 1), xmm1);
  __ movdqu(Address(rsp, xmm_size * 0), xmm0);

  // Call VM
  call_vm(masm, ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators), dst, scratch);

  // Restore registers
  __ movdqu(xmm0, Address(rsp, xmm_size * 0));
  __ movdqu(xmm1, Address(rsp, xmm_size * 1));
  __ movdqu(xmm2, Address(rsp, xmm_size * 2));
  __ movdqu(xmm3, Address(rsp, xmm_size * 3));
  __ movdqu(xmm4, Address(rsp, xmm_size * 4));
  __ movdqu(xmm5, Address(rsp, xmm_size * 5));
  __ movdqu(xmm6, Address(rsp, xmm_size * 6));
  __ movdqu(xmm7, Address(rsp, xmm_size * 7));
  __ addptr(rsp, xmm_spill_size);

  __ pop(r11);
  __ pop(r10);
  __ pop(r9);
  __ pop(r8);
  __ pop(rsi);
  __ pop(rdi);
  __ pop(rdx);
  __ pop(rcx);

  if (dst == rax) {
    __ addptr(rsp, wordSize);
  } else {
    __ movptr(dst, rax);
    __ pop(rax);
  }

  __ bind(done);

  // Restore scratch register
  if (tmp1 == noreg) {
    __ pop(scratch);
  }

  BLOCK_COMMENT("} ZBarrierSetAssembler::load_at");
}

#ifdef ASSERT

void ZBarrierSetAssembler::store_at(MacroAssembler* masm,
                                    DecoratorSet decorators,
                                    BasicType type,
                                    Address dst,
                                    Register src,
                                    Register tmp1,
                                    Register tmp2) {
  BLOCK_COMMENT("ZBarrierSetAssembler::store_at {");

  // Verify oop store
  if (type == T_OBJECT || type == T_ARRAY) {
    // Note that src could be noreg, which means we
    // are storing null and can skip verification.
    if (src != noreg) {
      Label done;
      __ testptr(src, address_bad_mask_from_thread(r15_thread));
      __ jcc(Assembler::zero, done);
      __ stop("Verify oop store failed");
      __ should_not_reach_here();
      __ bind(done);
    }
  }

  // Store value
  BarrierSetAssembler::store_at(masm, decorators, type, dst, src, tmp1, tmp2);

  BLOCK_COMMENT("} ZBarrierSetAssembler::store_at");
}

#endif // ASSERT

void ZBarrierSetAssembler::arraycopy_prologue(MacroAssembler* masm,
                                              DecoratorSet decorators,
                                              BasicType type,
                                              Register src,
                                              Register dst,
                                              Register count) {
  if (!ZBarrierSet::barrier_needed(decorators, type)) {
    // Barrier not needed
    return;
  }

  BLOCK_COMMENT("ZBarrierSetAssembler::arraycopy_prologue {");

  // Save registers
  __ pusha();

  // Call VM
  call_vm(masm, ZBarrierSetRuntime::load_barrier_on_oop_array_addr(), src, count);

  // Restore registers
  __ popa();

  BLOCK_COMMENT("} ZBarrierSetAssembler::arraycopy_prologue");
}

void ZBarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm,
                                                         Register jni_env,
                                                         Register obj,
                                                         Register tmp,
                                                         Label& slowpath) {
  BLOCK_COMMENT("ZBarrierSetAssembler::try_resolve_jobject_in_native {");

  // Resolve jobject
  BarrierSetAssembler::try_resolve_jobject_in_native(masm, jni_env, obj, tmp, slowpath);

  // Test address bad mask
  __ testptr(obj, address_bad_mask_from_jni_env(jni_env));
  __ jcc(Assembler::notZero, slowpath);

  BLOCK_COMMENT("} ZBarrierSetAssembler::try_resolve_jobject_in_native");
}

#ifdef COMPILER1

#undef __
#define __ ce->masm()->

void ZBarrierSetAssembler::generate_c1_load_barrier_test(LIR_Assembler* ce,
                                                         LIR_Opr ref) const {
  __ testptr(ref->as_register(), address_bad_mask_from_thread(r15_thread));
}

void ZBarrierSetAssembler::generate_c1_load_barrier_stub(LIR_Assembler* ce,
                                                         ZLoadBarrierStubC1* stub) const {
  // Stub entry
  __ bind(*stub->entry());

  Register ref = stub->ref()->as_register();
  Register ref_addr = noreg;
  Register tmp = noreg;

  if (stub->tmp()->is_valid()) {
    // Load address into tmp register
    ce->leal(stub->ref_addr(), stub->tmp());
    ref_addr = tmp = stub->tmp()->as_pointer_register();
  } else {
    // Address already in register
    ref_addr = stub->ref_addr()->as_address_ptr()->base()->as_pointer_register();
  }

  assert_different_registers(ref, ref_addr, noreg);

  // Save rax unless it is the result or tmp register
  if (ref != rax && tmp != rax) {
    __ push(rax);
  }

  // Setup arguments and call runtime stub
  __ subptr(rsp, 2 * BytesPerWord);
  ce->store_parameter(ref_addr, 1);
  ce->store_parameter(ref, 0);
  __ call(RuntimeAddress(stub->runtime_stub()));
  __ addptr(rsp, 2 * BytesPerWord);

  // Verify result
  __ verify_oop(rax, "Bad oop");

  // Move result into place
  if (ref != rax) {
    __ movptr(ref, rax);
  }

  // Restore rax unless it is the result or tmp register
  if (ref != rax && tmp != rax) {
    __ pop(rax);
  }

  // Stub exit
  __ jmp(*stub->continuation());
}

#undef __
#define __ sasm->

void ZBarrierSetAssembler::generate_c1_load_barrier_runtime_stub(StubAssembler* sasm,
                                                                 DecoratorSet decorators) const {
  // Enter and save registers
  __ enter();
  __ save_live_registers_no_oop_map(true /* save_fpu_registers */);

  // Setup arguments
  __ load_parameter(1, c_rarg1);
  __ load_parameter(0, c_rarg0);

  // Call VM
  __ call_VM_leaf(ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators), c_rarg0, c_rarg1);

  // Restore registers and return
  __ restore_live_registers_except_rax(true /* restore_fpu_registers */);
  __ leave();
  __ ret(0);
}

#endif // COMPILER1

#undef __
#define __ cgen->assembler()->

// Generates a register specific stub for calling
// ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded() or
// ZBarrierSetRuntime::load_barrier_on_weak_oop_field_preloaded().
//
// The raddr register serves as both input and output for this stub. When the stub is
// called the raddr register contains the object field address (oop*) where the bad oop
// was loaded from, which caused the slow path to be taken. On return from the stub the
// raddr register contains the good/healed oop returned from
// ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded() or
// ZBarrierSetRuntime::load_barrier_on_weak_oop_field_preloaded().
static address generate_load_barrier_stub(StubCodeGenerator* cgen, Register raddr, DecoratorSet decorators) {
  // Don't generate stub for invalid registers
  if (raddr == rsp || raddr == r15) {
    return NULL;
  }

  // Create stub name
  char name[64];
  const bool weak = (decorators & ON_WEAK_OOP_REF) != 0;
  os::snprintf(name, sizeof(name), "zgc_load_barrier%s_stub_%s", weak ? "_weak" : "", raddr->name());

  __ align(CodeEntryAlignment);
  StubCodeMark mark(cgen, "StubRoutines", os::strdup(name, mtCode));
  address start = __ pc();

  // Save live registers
  if (raddr != rax) {
    __ push(rax);
  }
  if (raddr != rcx) {
    __ push(rcx);
  }
  if (raddr != rdx) {
    __ push(rdx);
  }
  if (raddr != rsi) {
    __ push(rsi);
  }
  if (raddr != rdi) {
    __ push(rdi);
  }
  if (raddr != r8) {
    __ push(r8);
  }
  if (raddr != r9) {
    __ push(r9);
  }
  if (raddr != r10) {
    __ push(r10);
  }
  if (raddr != r11) {
    __ push(r11);
  }

  // Setup arguments
  if (raddr != c_rarg1) {
    __ movq(c_rarg1, raddr);
  }
  __ movq(c_rarg0, Address(raddr, 0));

  // Call barrier function
  __ call_VM_leaf(ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators), c_rarg0, c_rarg1);

  // Move result returned in rax to raddr, if needed
  if (raddr != rax) {
    __ movq(raddr, rax);
  }

  // Restore saved registers
  if (raddr != r11) {
    __ pop(r11);
  }
  if (raddr != r10) {
    __ pop(r10);
  }
  if (raddr != r9) {
    __ pop(r9);
  }
  if (raddr != r8) {
    __ pop(r8);
  }
  if (raddr != rdi) {
    __ pop(rdi);
  }
  if (raddr != rsi) {
    __ pop(rsi);
  }
  if (raddr != rdx) {
    __ pop(rdx);
  }
  if (raddr != rcx) {
    __ pop(rcx);
  }
  if (raddr != rax) {
    __ pop(rax);
  }

  __ ret(0);

  return start;
}

#undef __

static void barrier_stubs_init_inner(const char* label, const DecoratorSet decorators, address* stub) {
  const int nregs = RegisterImpl::number_of_registers;
  const int code_size = nregs * 128; // Rough estimate of code size

  ResourceMark rm;

  CodeBuffer buf(BufferBlob::create(label, code_size));
  StubCodeGenerator cgen(&buf);

  for (int i = 0; i < nregs; i++) {
    const Register reg = as_Register(i);
    stub[i] = generate_load_barrier_stub(&cgen, reg, decorators);
  }
}

void ZBarrierSetAssembler::barrier_stubs_init() {
  barrier_stubs_init_inner("zgc_load_barrier_stubs", ON_STRONG_OOP_REF, _load_barrier_slow_stub);
  barrier_stubs_init_inner("zgc_load_barrier_weak_stubs", ON_WEAK_OOP_REF, _load_barrier_weak_slow_stub);
}

address ZBarrierSetAssembler::load_barrier_slow_stub(Register reg) {
  return _load_barrier_slow_stub[reg->encoding()];
}

address ZBarrierSetAssembler::load_barrier_weak_slow_stub(Register reg) {
  return _load_barrier_weak_slow_stub[reg->encoding()];
}
