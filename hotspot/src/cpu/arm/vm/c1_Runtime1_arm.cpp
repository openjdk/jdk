/*
 * Copyright (c) 2008, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/macroAssembler.hpp"
#include "c1/c1_Defs.hpp"
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "c1/c1_Runtime1.hpp"
#include "interpreter/interpreter.hpp"
#include "nativeInst_arm.hpp"
#include "oops/compiledICHolder.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "register_arm.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/vframeArray.hpp"
#include "vmreg_arm.inline.hpp"
#if INCLUDE_ALL_GCS
#include "gc/g1/g1SATBCardTableModRefBS.hpp"
#endif

// Note: Rtemp usage is this file should not impact C2 and should be
// correct as long as it is not implicitly used in lower layers (the
// arm [macro]assembler) and used with care in the other C1 specific
// files.

// Implementation of StubAssembler

int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, int args_size) {
  mov(R0, Rthread);

  int call_offset = set_last_Java_frame(SP, FP, false, Rtemp);

  call(entry);
  if (call_offset == -1) { // PC not saved
    call_offset = offset();
  }
  reset_last_Java_frame(Rtemp);

  assert(frame_size() != no_frame_size, "frame must be fixed");
  if (_stub_id != Runtime1::forward_exception_id) {
    ldr(R3, Address(Rthread, Thread::pending_exception_offset()));
  }

  if (oop_result1->is_valid()) {
    assert_different_registers(oop_result1, R3, Rtemp);
    get_vm_result(oop_result1, Rtemp);
  }
  if (metadata_result->is_valid()) {
    assert_different_registers(metadata_result, R3, Rtemp);
    get_vm_result_2(metadata_result, Rtemp);
  }

  // Check for pending exception
  // unpack_with_exception_in_tls path is taken through
  // Runtime1::exception_handler_for_pc
  if (_stub_id != Runtime1::forward_exception_id) {
    assert(frame_size() != no_frame_size, "cannot directly call forward_exception_id");
#ifdef AARCH64
    Label skip;
    cbz(R3, skip);
    jump(Runtime1::entry_for(Runtime1::forward_exception_id), relocInfo::runtime_call_type, Rtemp);
    bind(skip);
#else
    cmp(R3, 0);
    jump(Runtime1::entry_for(Runtime1::forward_exception_id), relocInfo::runtime_call_type, Rtemp, ne);
#endif // AARCH64
  } else {
#ifdef ASSERT
    // Should not have pending exception in forward_exception stub
    ldr(R3, Address(Rthread, Thread::pending_exception_offset()));
    cmp(R3, 0);
    breakpoint(ne);
#endif // ASSERT
  }
  return call_offset;
}


int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, Register arg1) {
  if (arg1 != R1) {
    mov(R1, arg1);
  }
  return call_RT(oop_result1, metadata_result, entry, 1);
}


int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, Register arg1, Register arg2) {
  assert(arg1 == R1 && arg2 == R2, "cannot handle otherwise");
  return call_RT(oop_result1, metadata_result, entry, 2);
}


int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, Register arg1, Register arg2, Register arg3) {
  assert(arg1 == R1 && arg2 == R2 && arg3 == R3, "cannot handle otherwise");
  return call_RT(oop_result1, metadata_result, entry, 3);
}


#define __ sasm->

// TODO: ARM - does this duplicate RegisterSaver in SharedRuntime?
#ifdef AARCH64

  //
  // On AArch64 registers save area has the following layout:
  //
  // |---------------------|
  // | return address (LR) |
  // | FP                  |
  // |---------------------|
  // | D31                 |
  // | ...                 |
  // | D0                  |
  // |---------------------|
  // | padding             |
  // |---------------------|
  // | R28                 |
  // | ...                 |
  // | R0                  |
  // |---------------------| <-- SP
  //

enum RegisterLayout {
  number_of_saved_gprs = 29,
  number_of_saved_fprs = FloatRegisterImpl::number_of_registers,

  R0_offset  = 0,
  D0_offset  = R0_offset + number_of_saved_gprs + 1,
  FP_offset  = D0_offset + number_of_saved_fprs,
  LR_offset  = FP_offset + 1,

  reg_save_size = LR_offset + 1,

  arg1_offset = reg_save_size * wordSize,
  arg2_offset = (reg_save_size + 1) * wordSize
};

#else

enum RegisterLayout {
  fpu_save_size = pd_nof_fpu_regs_reg_alloc,
#ifndef __SOFTFP__
  D0_offset = 0,
#endif
  R0_offset = fpu_save_size,
  R1_offset,
  R2_offset,
  R3_offset,
  R4_offset,
  R5_offset,
  R6_offset,
#if (FP_REG_NUM != 7)
  R7_offset,
#endif
  R8_offset,
  R9_offset,
  R10_offset,
#if (FP_REG_NUM != 11)
  R11_offset,
#endif
  R12_offset,
  FP_offset,
  LR_offset,
  reg_save_size,
  arg1_offset = reg_save_size * wordSize,
  arg2_offset = (reg_save_size + 1) * wordSize
};

#endif // AARCH64

static OopMap* generate_oop_map(StubAssembler* sasm, bool save_fpu_registers = HaveVFP) {
  sasm->set_frame_size(reg_save_size /* in words */);

  // Record saved value locations in an OopMap.
  // Locations are offsets from sp after runtime call.
  OopMap* map = new OopMap(VMRegImpl::slots_per_word * reg_save_size, 0);

#ifdef AARCH64
  for (int i = 0; i < number_of_saved_gprs; i++) {
    map->set_callee_saved(VMRegImpl::stack2reg((R0_offset + i) * VMRegImpl::slots_per_word), as_Register(i)->as_VMReg());
  }
  map->set_callee_saved(VMRegImpl::stack2reg(FP_offset * VMRegImpl::slots_per_word), FP->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(LR_offset * VMRegImpl::slots_per_word), LR->as_VMReg());

  if (save_fpu_registers) {
    for (int i = 0; i < number_of_saved_fprs; i++) {
      map->set_callee_saved(VMRegImpl::stack2reg((D0_offset + i) * VMRegImpl::slots_per_word), as_FloatRegister(i)->as_VMReg());
    }
  }
#else
  int j=0;
  for (int i = R0_offset; i < R10_offset; i++) {
    if (j == FP_REG_NUM) {
      // skip the FP register, saved below
      j++;
    }
    map->set_callee_saved(VMRegImpl::stack2reg(i), as_Register(j)->as_VMReg());
    j++;
  }
  assert(j == R10->encoding(), "must be");
#if (FP_REG_NUM != 11)
  // add R11, if not saved as FP
  map->set_callee_saved(VMRegImpl::stack2reg(R11_offset), R11->as_VMReg());
#endif
  map->set_callee_saved(VMRegImpl::stack2reg(FP_offset), FP->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(LR_offset), LR->as_VMReg());

  if (save_fpu_registers) {
    for (int i = 0; i < fpu_save_size; i++) {
      map->set_callee_saved(VMRegImpl::stack2reg(i), as_FloatRegister(i)->as_VMReg());
    }
  }
#endif // AARCH64

  return map;
}

static OopMap* save_live_registers(StubAssembler* sasm, bool save_fpu_registers = HaveVFP) {
  __ block_comment("save_live_registers");
  sasm->set_frame_size(reg_save_size /* in words */);

#ifdef AARCH64
  assert((reg_save_size * wordSize) % StackAlignmentInBytes == 0, "SP should be aligned");

  __ raw_push(FP, LR);

  __ sub(SP, SP, (reg_save_size - 2) * wordSize);

  for (int i = 0; i < round_down(number_of_saved_gprs, 2); i += 2) {
    __ stp(as_Register(i), as_Register(i+1), Address(SP, (R0_offset + i) * wordSize));
  }

  if (is_odd(number_of_saved_gprs)) {
    int i = number_of_saved_gprs - 1;
    __ str(as_Register(i), Address(SP, (R0_offset + i) * wordSize));
  }

  if (save_fpu_registers) {
    assert (is_even(number_of_saved_fprs), "adjust this code");
    for (int i = 0; i < number_of_saved_fprs; i += 2) {
      __ stp_d(as_FloatRegister(i), as_FloatRegister(i+1), Address(SP, (D0_offset + i) * wordSize));
    }
  }
#else
  __ push(RegisterSet(FP) | RegisterSet(LR));
  __ push(RegisterSet(R0, R6) | RegisterSet(R8, R10) | R12 | altFP_7_11);
  if (save_fpu_registers) {
    __ fstmdbd(SP, FloatRegisterSet(D0, fpu_save_size / 2), writeback);
  } else {
    __ sub(SP, SP, fpu_save_size * wordSize);
  }
#endif // AARCH64

  return generate_oop_map(sasm, save_fpu_registers);
}


static void restore_live_registers(StubAssembler* sasm,
                                   bool restore_R0,
                                   bool restore_FP_LR,
                                   bool do_return,
                                   bool restore_fpu_registers = HaveVFP) {
  __ block_comment("restore_live_registers");

#ifdef AARCH64
  if (restore_R0) {
    __ ldr(R0, Address(SP, R0_offset * wordSize));
  }

  assert(is_odd(number_of_saved_gprs), "adjust this code");
  for (int i = 1; i < number_of_saved_gprs; i += 2) {
    __ ldp(as_Register(i), as_Register(i+1), Address(SP, (R0_offset + i) * wordSize));
  }

  if (restore_fpu_registers) {
    assert (is_even(number_of_saved_fprs), "adjust this code");
    for (int i = 0; i < number_of_saved_fprs; i += 2) {
      __ ldp_d(as_FloatRegister(i), as_FloatRegister(i+1), Address(SP, (D0_offset + i) * wordSize));
    }
  }

  __ add(SP, SP, (reg_save_size - 2) * wordSize);

  if (restore_FP_LR) {
    __ raw_pop(FP, LR);
    if (do_return) {
      __ ret();
    }
  } else {
    assert (!do_return, "return without restoring FP/LR");
  }
#else
  if (restore_fpu_registers) {
    __ fldmiad(SP, FloatRegisterSet(D0, fpu_save_size / 2), writeback);
    if (!restore_R0) {
      __ add(SP, SP, (R1_offset - fpu_save_size) * wordSize);
    }
  } else {
    __ add(SP, SP, (restore_R0 ? fpu_save_size : R1_offset) * wordSize);
  }
  __ pop(RegisterSet((restore_R0 ? R0 : R1), R6) | RegisterSet(R8, R10) | R12 | altFP_7_11);
  if (restore_FP_LR) {
    __ pop(RegisterSet(FP) | RegisterSet(do_return ? PC : LR));
  } else {
    assert (!do_return, "return without restoring FP/LR");
  }
#endif // AARCH64
}


static void restore_live_registers_except_R0(StubAssembler* sasm, bool restore_fpu_registers = HaveVFP) {
  restore_live_registers(sasm, false, true, true, restore_fpu_registers);
}

static void restore_live_registers(StubAssembler* sasm, bool restore_fpu_registers = HaveVFP) {
  restore_live_registers(sasm, true, true, true, restore_fpu_registers);
}

#ifndef AARCH64
static void restore_live_registers_except_FP_LR(StubAssembler* sasm, bool restore_fpu_registers = HaveVFP) {
  restore_live_registers(sasm, true, false, false, restore_fpu_registers);
}
#endif // !AARCH64

static void restore_live_registers_without_return(StubAssembler* sasm, bool restore_fpu_registers = HaveVFP) {
  restore_live_registers(sasm, true, true, false, restore_fpu_registers);
}


void Runtime1::initialize_pd() {
}


OopMapSet* Runtime1::generate_exception_throw(StubAssembler* sasm, address target, bool has_argument) {
  OopMap* oop_map = save_live_registers(sasm);

  if (has_argument) {
    __ ldr(R1, Address(SP, arg1_offset));
  }

  int call_offset = __ call_RT(noreg, noreg, target);
  OopMapSet* oop_maps = new OopMapSet();
  oop_maps->add_gc_map(call_offset, oop_map);

  DEBUG_ONLY(STOP("generate_exception_throw");)  // Should not reach here
  return oop_maps;
}


static void restore_sp_for_method_handle(StubAssembler* sasm) {
  // Restore SP from its saved reg (FP) if the exception PC is a MethodHandle call site.
  __ ldr_s32(Rtemp, Address(Rthread, JavaThread::is_method_handle_return_offset()));
#ifdef AARCH64
  Label skip;
  __ cbz(Rtemp, skip);
  __ mov(SP, Rmh_SP_save);
  __ bind(skip);
#else
  __ cmp(Rtemp, 0);
  __ mov(SP, Rmh_SP_save, ne);
#endif // AARCH64
}


OopMapSet* Runtime1::generate_handle_exception(StubID id, StubAssembler* sasm) {
  __ block_comment("generate_handle_exception");

  bool save_fpu_registers = false;

  // Save registers, if required.
  OopMapSet* oop_maps = new OopMapSet();
  OopMap* oop_map = NULL;

  switch (id) {
  case forward_exception_id: {
    save_fpu_registers = HaveVFP;
    oop_map = generate_oop_map(sasm);
    __ ldr(Rexception_obj, Address(Rthread, Thread::pending_exception_offset()));
    __ ldr(Rexception_pc, Address(SP, LR_offset * wordSize));
    Register zero = __ zero_register(Rtemp);
    __ str(zero, Address(Rthread, Thread::pending_exception_offset()));
    break;
  }
  case handle_exception_id:
    save_fpu_registers = HaveVFP;
    // fall-through
  case handle_exception_nofpu_id:
    // At this point all registers MAY be live.
    oop_map = save_live_registers(sasm, save_fpu_registers);
    break;
  case handle_exception_from_callee_id:
    // At this point all registers except exception oop (R4/R19) and
    // exception pc (R5/R20) are dead.
    oop_map = save_live_registers(sasm);  // TODO it's not required to save all registers
    break;
  default:  ShouldNotReachHere();
  }

  __ str(Rexception_obj, Address(Rthread, JavaThread::exception_oop_offset()));
  __ str(Rexception_pc, Address(Rthread, JavaThread::exception_pc_offset()));

  __ str(Rexception_pc, Address(SP, LR_offset * wordSize)); // patch throwing pc into return address

  int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, exception_handler_for_pc));
  oop_maps->add_gc_map(call_offset, oop_map);

  // Exception handler found
  __ str(R0, Address(SP, LR_offset * wordSize)); // patch the return address

  // Restore the registers that were saved at the beginning, remove
  // frame and jump to the exception handler.
  switch (id) {
  case forward_exception_id:
  case handle_exception_nofpu_id:
  case handle_exception_id:
    restore_live_registers(sasm, save_fpu_registers);
    // Note: the restore live registers includes the jump to LR (patched to R0)
    break;
  case handle_exception_from_callee_id:
    restore_live_registers_without_return(sasm); // must not jump immediatly to handler
    restore_sp_for_method_handle(sasm);
    __ ret();
    break;
  default:  ShouldNotReachHere();
  }

  DEBUG_ONLY(STOP("generate_handle_exception");)  // Should not reach here

  return oop_maps;
}


void Runtime1::generate_unwind_exception(StubAssembler* sasm) {
  // FP no longer used to find the frame start
  // on entry, remove_frame() has already been called (restoring FP and LR)

  // search the exception handler address of the caller (using the return address)
  __ mov(c_rarg0, Rthread);
  __ mov(Rexception_pc, LR);
  __ mov(c_rarg1, LR);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address), c_rarg0, c_rarg1);

  // Exception oop should be still in Rexception_obj and pc in Rexception_pc
  // Jump to handler
  __ verify_not_null_oop(Rexception_obj);

  // JSR292 extension
  restore_sp_for_method_handle(sasm);

  __ jump(R0);
}


OopMapSet* Runtime1::generate_patching(StubAssembler* sasm, address target) {
  OopMap* oop_map = save_live_registers(sasm);

  // call the runtime patching routine, returns non-zero if nmethod got deopted.
  int call_offset = __ call_RT(noreg, noreg, target);
  OopMapSet* oop_maps = new OopMapSet();
  oop_maps->add_gc_map(call_offset, oop_map);

  DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
  assert(deopt_blob != NULL, "deoptimization blob must have been created");

  __ cmp_32(R0, 0);

#ifdef AARCH64
  Label call_deopt;

  restore_live_registers_without_return(sasm);
  __ b(call_deopt, ne);
  __ ret();

  __ bind(call_deopt);
#else
  restore_live_registers_except_FP_LR(sasm);
  __ pop(RegisterSet(FP) | RegisterSet(PC), eq);

  // Deoptimization needed
  // TODO: ARM - no need to restore FP & LR because unpack_with_reexecution() stores them back
  __ pop(RegisterSet(FP) | RegisterSet(LR));
#endif // AARCH64

  __ jump(deopt_blob->unpack_with_reexecution(), relocInfo::runtime_call_type, Rtemp);

  DEBUG_ONLY(STOP("generate_patching");)  // Should not reach here
  return oop_maps;
}


OopMapSet* Runtime1::generate_code_for(StubID id, StubAssembler* sasm) {
  const bool must_gc_arguments = true;
  const bool dont_gc_arguments = false;

  OopMapSet* oop_maps = NULL;
  bool save_fpu_registers = HaveVFP;

  switch (id) {
    case forward_exception_id:
      {
        oop_maps = generate_handle_exception(id, sasm);
        // does not return on ARM
      }
      break;

#if INCLUDE_ALL_GCS
    case g1_pre_barrier_slow_id:
      {
        // Input:
        // - pre_val pushed on the stack

        __ set_info("g1_pre_barrier_slow_id", dont_gc_arguments);

        // save at least the registers that need saving if the runtime is called
#ifdef AARCH64
        __ raw_push(R0, R1);
        __ raw_push(R2, R3);
        const int nb_saved_regs = 4;
#else // AARCH64
        const RegisterSet saved_regs = RegisterSet(R0,R3) | RegisterSet(R12) | RegisterSet(LR);
        const int nb_saved_regs = 6;
        assert(nb_saved_regs == saved_regs.size(), "fix nb_saved_regs");
        __ push(saved_regs);
#endif // AARCH64

        const Register r_pre_val_0  = R0; // must be R0, to be ready for the runtime call
        const Register r_index_1    = R1;
        const Register r_buffer_2   = R2;

        Address queue_active(Rthread, in_bytes(JavaThread::satb_mark_queue_offset() +
                                               SATBMarkQueue::byte_offset_of_active()));
        Address queue_index(Rthread, in_bytes(JavaThread::satb_mark_queue_offset() +
                                              SATBMarkQueue::byte_offset_of_index()));
        Address buffer(Rthread, in_bytes(JavaThread::satb_mark_queue_offset() +
                                         SATBMarkQueue::byte_offset_of_buf()));

        Label done;
        Label runtime;

        // Is marking still active?
        assert(in_bytes(SATBMarkQueue::byte_width_of_active()) == 1, "Assumption");
        __ ldrb(R1, queue_active);
        __ cbz(R1, done);

        __ ldr(r_index_1, queue_index);
        __ ldr(r_pre_val_0, Address(SP, nb_saved_regs*wordSize));
        __ ldr(r_buffer_2, buffer);

        __ subs(r_index_1, r_index_1, wordSize);
        __ b(runtime, lt);

        __ str(r_index_1, queue_index);
        __ str(r_pre_val_0, Address(r_buffer_2, r_index_1));

        __ bind(done);

#ifdef AARCH64
        __ raw_pop(R2, R3);
        __ raw_pop(R0, R1);
#else // AARCH64
        __ pop(saved_regs);
#endif // AARCH64

        __ ret();

        __ bind(runtime);

        save_live_registers(sasm);

        assert(r_pre_val_0 == c_rarg0, "pre_val should be in R0");
        __ mov(c_rarg1, Rthread);
        __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_pre), c_rarg0, c_rarg1);

        restore_live_registers_without_return(sasm);

        __ b(done);
      }
      break;
    case g1_post_barrier_slow_id:
      {
        // Input:
        // - store_addr, pushed on the stack

        __ set_info("g1_post_barrier_slow_id", dont_gc_arguments);

        BarrierSet* bs = Universe::heap()->barrier_set();
        CardTableModRefBS* ct = barrier_set_cast<CardTableModRefBS>(bs);
        Label done;
        Label recheck;
        Label runtime;

        Address queue_index(Rthread, in_bytes(JavaThread::dirty_card_queue_offset() +
                                              DirtyCardQueue::byte_offset_of_index()));
        Address buffer(Rthread, in_bytes(JavaThread::dirty_card_queue_offset() +
                                         DirtyCardQueue::byte_offset_of_buf()));

        AddressLiteral cardtable((address)ct->byte_map_base, relocInfo::none);
        assert(sizeof(*ct->byte_map_base) == sizeof(jbyte), "adjust this code");

        // save at least the registers that need saving if the runtime is called
#ifdef AARCH64
        __ raw_push(R0, R1);
        __ raw_push(R2, R3);
        const int nb_saved_regs = 4;
#else // AARCH64
        const RegisterSet saved_regs = RegisterSet(R0,R3) | RegisterSet(R12) | RegisterSet(LR);
        const int nb_saved_regs = 6;
        assert(nb_saved_regs == saved_regs.size(), "fix nb_saved_regs");
        __ push(saved_regs);
#endif // AARCH64

        const Register r_card_addr_0 = R0; // must be R0 for the slow case
        const Register r_obj_0 = R0;
        const Register r_card_base_1 = R1;
        const Register r_tmp2 = R2;
        const Register r_index_2 = R2;
        const Register r_buffer_3 = R3;
        const Register tmp1 = Rtemp;

        __ ldr(r_obj_0, Address(SP, nb_saved_regs*wordSize));
        // Note: there is a comment in x86 code about not using
        // ExternalAddress / lea, due to relocation not working
        // properly for that address. Should be OK for arm, where we
        // explicitly specify that 'cardtable' has a relocInfo::none
        // type.
        __ lea(r_card_base_1, cardtable);
        __ add(r_card_addr_0, r_card_base_1, AsmOperand(r_obj_0, lsr, CardTableModRefBS::card_shift));

        // first quick check without barrier
        __ ldrb(r_tmp2, Address(r_card_addr_0));

        __ cmp(r_tmp2, (int)G1SATBCardTableModRefBS::g1_young_card_val());
        __ b(recheck, ne);

        __ bind(done);

#ifdef AARCH64
        __ raw_pop(R2, R3);
        __ raw_pop(R0, R1);
#else // AARCH64
        __ pop(saved_regs);
#endif // AARCH64

        __ ret();

        __ bind(recheck);

        __ membar(MacroAssembler::Membar_mask_bits(MacroAssembler::StoreLoad), tmp1);

        // reload card state after the barrier that ensures the stored oop was visible
        __ ldrb(r_tmp2, Address(r_card_addr_0));

        assert(CardTableModRefBS::dirty_card_val() == 0, "adjust this code");
        __ cbz(r_tmp2, done);

        // storing region crossing non-NULL, card is clean.
        // dirty card and log.

        assert(0 == (int)CardTableModRefBS::dirty_card_val(), "adjust this code");
        if (((intptr_t)ct->byte_map_base & 0xff) == 0) {
          // Card table is aligned so the lowest byte of the table address base is zero.
          __ strb(r_card_base_1, Address(r_card_addr_0));
        } else {
          __ strb(__ zero_register(r_tmp2), Address(r_card_addr_0));
        }

        __ ldr(r_index_2, queue_index);
        __ ldr(r_buffer_3, buffer);

        __ subs(r_index_2, r_index_2, wordSize);
        __ b(runtime, lt); // go to runtime if now negative

        __ str(r_index_2, queue_index);

        __ str(r_card_addr_0, Address(r_buffer_3, r_index_2));

        __ b(done);

        __ bind(runtime);

        save_live_registers(sasm);

        assert(r_card_addr_0 == c_rarg0, "card_addr should be in R0");
        __ mov(c_rarg1, Rthread);
        __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_post), c_rarg0, c_rarg1);

        restore_live_registers_without_return(sasm);

        __ b(done);
      }
      break;
#endif // INCLUDE_ALL_GCS
    case new_instance_id:
    case fast_new_instance_id:
    case fast_new_instance_init_check_id:
      {
        const Register result = R0;
        const Register klass  = R1;

        if (UseTLAB && FastTLABRefill && id != new_instance_id) {
          // We come here when TLAB allocation failed.
          // In this case we either refill TLAB or allocate directly from eden.
          Label retry_tlab, try_eden, slow_case, slow_case_no_pop;

          // Make sure the class is fully initialized
          if (id == fast_new_instance_init_check_id) {
            __ ldrb(result, Address(klass, InstanceKlass::init_state_offset()));
            __ cmp(result, InstanceKlass::fully_initialized);
            __ b(slow_case_no_pop, ne);
          }

          // Free some temporary registers
          const Register obj_size = R4;
          const Register tmp1     = R5;
          const Register tmp2     = LR;
          const Register obj_end  = Rtemp;

          __ raw_push(R4, R5, LR);

          __ tlab_refill(result, obj_size, tmp1, tmp2, obj_end, try_eden, slow_case);

          __ bind(retry_tlab);
          __ ldr_u32(obj_size, Address(klass, Klass::layout_helper_offset()));
          __ tlab_allocate(result, obj_end, tmp1, obj_size, slow_case);              // initializes result and obj_end
          __ initialize_object(result, obj_end, klass, noreg /* len */, tmp1, tmp2,
                               instanceOopDesc::header_size() * HeapWordSize, -1,
                               /* is_tlab_allocated */ true);
          __ raw_pop_and_ret(R4, R5);

          __ bind(try_eden);
          __ ldr_u32(obj_size, Address(klass, Klass::layout_helper_offset()));
          __ eden_allocate(result, obj_end, tmp1, tmp2, obj_size, slow_case);        // initializes result and obj_end
          __ incr_allocated_bytes(obj_size, tmp2);
          __ initialize_object(result, obj_end, klass, noreg /* len */, tmp1, tmp2,
                               instanceOopDesc::header_size() * HeapWordSize, -1,
                               /* is_tlab_allocated */ false);
          __ raw_pop_and_ret(R4, R5);

          __ bind(slow_case);
          __ raw_pop(R4, R5, LR);

          __ bind(slow_case_no_pop);
        }

        OopMap* map = save_live_registers(sasm);
        int call_offset = __ call_RT(result, noreg, CAST_FROM_FN_PTR(address, new_instance), klass);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);

        // MacroAssembler::StoreStore useless (included in the runtime exit path)

        restore_live_registers_except_R0(sasm);
      }
      break;

    case counter_overflow_id:
      {
        OopMap* oop_map = save_live_registers(sasm);
        __ ldr(R1, Address(SP, arg1_offset));
        __ ldr(R2, Address(SP, arg2_offset));
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, counter_overflow), R1, R2);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);
        restore_live_registers(sasm);
      }
      break;

    case new_type_array_id:
    case new_object_array_id:
      {
        if (id == new_type_array_id) {
          __ set_info("new_type_array", dont_gc_arguments);
        } else {
          __ set_info("new_object_array", dont_gc_arguments);
        }

        const Register result = R0;
        const Register klass  = R1;
        const Register length = R2;

        if (UseTLAB && FastTLABRefill) {
          // We come here when TLAB allocation failed.
          // In this case we either refill TLAB or allocate directly from eden.
          Label retry_tlab, try_eden, slow_case, slow_case_no_pop;

#ifdef AARCH64
          __ mov_slow(Rtemp, C1_MacroAssembler::max_array_allocation_length);
          __ cmp_32(length, Rtemp);
#else
          __ cmp_32(length, C1_MacroAssembler::max_array_allocation_length);
#endif // AARCH64
          __ b(slow_case_no_pop, hs);

          // Free some temporary registers
          const Register arr_size = R4;
          const Register tmp1     = R5;
          const Register tmp2     = LR;
          const Register tmp3     = Rtemp;
          const Register obj_end  = tmp3;

          __ raw_push(R4, R5, LR);

          __ tlab_refill(result, arr_size, tmp1, tmp2, tmp3, try_eden, slow_case);

          __ bind(retry_tlab);
          // Get the allocation size: round_up((length << (layout_helper & 0xff)) + header_size)
          __ ldr_u32(tmp1, Address(klass, Klass::layout_helper_offset()));
          __ mov(arr_size, MinObjAlignmentInBytesMask);
          __ and_32(tmp2, tmp1, (unsigned int)(Klass::_lh_header_size_mask << Klass::_lh_header_size_shift));

#ifdef AARCH64
          __ lslv_w(tmp3, length, tmp1);
          __ add(arr_size, arr_size, tmp3);
#else
          __ add(arr_size, arr_size, AsmOperand(length, lsl, tmp1));
#endif // AARCH64

          __ add(arr_size, arr_size, AsmOperand(tmp2, lsr, Klass::_lh_header_size_shift));
          __ align_reg(arr_size, arr_size, MinObjAlignmentInBytes);

          // tlab_allocate initializes result and obj_end, and preserves tmp2 which contains header_size
          __ tlab_allocate(result, obj_end, tmp1, arr_size, slow_case);

          assert_different_registers(result, obj_end, klass, length, tmp1, tmp2);
          __ initialize_header(result, klass, length, tmp1);

          __ add(tmp2, result, AsmOperand(tmp2, lsr, Klass::_lh_header_size_shift));
          if (!ZeroTLAB) {
            __ initialize_body(tmp2, obj_end, tmp1);
          }

          __ membar(MacroAssembler::StoreStore, tmp1);

          __ raw_pop_and_ret(R4, R5);

          __ bind(try_eden);
          // Get the allocation size: round_up((length << (layout_helper & 0xff)) + header_size)
          __ ldr_u32(tmp1, Address(klass, Klass::layout_helper_offset()));
          __ mov(arr_size, MinObjAlignmentInBytesMask);
          __ and_32(tmp2, tmp1, (unsigned int)(Klass::_lh_header_size_mask << Klass::_lh_header_size_shift));

#ifdef AARCH64
          __ lslv_w(tmp3, length, tmp1);
          __ add(arr_size, arr_size, tmp3);
#else
          __ add(arr_size, arr_size, AsmOperand(length, lsl, tmp1));
#endif // AARCH64

          __ add(arr_size, arr_size, AsmOperand(tmp2, lsr, Klass::_lh_header_size_shift));
          __ align_reg(arr_size, arr_size, MinObjAlignmentInBytes);

          // eden_allocate destroys tmp2, so reload header_size after allocation
          // eden_allocate initializes result and obj_end
          __ eden_allocate(result, obj_end, tmp1, tmp2, arr_size, slow_case);
          __ incr_allocated_bytes(arr_size, tmp2);
          __ ldrb(tmp2, Address(klass, in_bytes(Klass::layout_helper_offset()) +
                                       Klass::_lh_header_size_shift / BitsPerByte));
          __ initialize_object(result, obj_end, klass, length, tmp1, tmp2, tmp2, -1, /* is_tlab_allocated */ false);
          __ raw_pop_and_ret(R4, R5);

          __ bind(slow_case);
          __ raw_pop(R4, R5, LR);
          __ bind(slow_case_no_pop);
        }

        OopMap* map = save_live_registers(sasm);
        int call_offset;
        if (id == new_type_array_id) {
          call_offset = __ call_RT(result, noreg, CAST_FROM_FN_PTR(address, new_type_array), klass, length);
        } else {
          call_offset = __ call_RT(result, noreg, CAST_FROM_FN_PTR(address, new_object_array), klass, length);
        }
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);

        // MacroAssembler::StoreStore useless (included in the runtime exit path)

        restore_live_registers_except_R0(sasm);
      }
      break;

    case new_multi_array_id:
      {
        __ set_info("new_multi_array", dont_gc_arguments);

        // R0: klass
        // R2: rank
        // SP: address of 1st dimension
        const Register result = R0;
        OopMap* map = save_live_registers(sasm);

        __ mov(R1, R0);
        __ add(R3, SP, arg1_offset);
        int call_offset = __ call_RT(result, noreg, CAST_FROM_FN_PTR(address, new_multi_array), R1, R2, R3);

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);

        // MacroAssembler::StoreStore useless (included in the runtime exit path)

        restore_live_registers_except_R0(sasm);
      }
      break;

    case register_finalizer_id:
      {
        __ set_info("register_finalizer", dont_gc_arguments);

        // Do not call runtime if JVM_ACC_HAS_FINALIZER flag is not set
        __ load_klass(Rtemp, R0);
        __ ldr_u32(Rtemp, Address(Rtemp, Klass::access_flags_offset()));

#ifdef AARCH64
        Label L;
        __ tbnz(Rtemp, exact_log2(JVM_ACC_HAS_FINALIZER), L);
        __ ret();
        __ bind(L);
#else
        __ tst(Rtemp, JVM_ACC_HAS_FINALIZER);
        __ bx(LR, eq);
#endif // AARCH64

        // Call VM
        OopMap* map = save_live_registers(sasm);
        oop_maps = new OopMapSet();
        int call_offset = __ call_RT(noreg, noreg,
                                     CAST_FROM_FN_PTR(address, SharedRuntime::register_finalizer), R0);
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm);
      }
      break;

    case throw_range_check_failed_id:
      {
        __ set_info("range_check_failed", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_range_check_exception), true);
      }
      break;

    case throw_index_exception_id:
      {
        __ set_info("index_range_check_failed", dont_gc_arguments);
#ifdef AARCH64
        __ NOT_TESTED();
#endif
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_index_exception), true);
      }
      break;

    case throw_div0_exception_id:
      {
        __ set_info("throw_div0_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_div0_exception), false);
      }
      break;

    case throw_null_pointer_exception_id:
      {
        __ set_info("throw_null_pointer_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_null_pointer_exception), false);
      }
      break;

    case handle_exception_nofpu_id:
    case handle_exception_id:
      {
        __ set_info("handle_exception", dont_gc_arguments);
        oop_maps = generate_handle_exception(id, sasm);
      }
      break;

    case handle_exception_from_callee_id:
      {
        __ set_info("handle_exception_from_callee", dont_gc_arguments);
        oop_maps = generate_handle_exception(id, sasm);
      }
      break;

    case unwind_exception_id:
      {
        __ set_info("unwind_exception", dont_gc_arguments);
        generate_unwind_exception(sasm);
      }
      break;

    case throw_array_store_exception_id:
      {
        __ set_info("throw_array_store_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_array_store_exception), true);
      }
      break;

    case throw_class_cast_exception_id:
      {
        __ set_info("throw_class_cast_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_class_cast_exception), true);
      }
      break;

    case throw_incompatible_class_change_error_id:
      {
        __ set_info("throw_incompatible_class_cast_exception", dont_gc_arguments);
#ifdef AARCH64
        __ NOT_TESTED();
#endif
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_incompatible_class_change_error), false);
      }
      break;

    case slow_subtype_check_id:
      {
        // (in)  R0 - sub, destroyed,
        // (in)  R1 - super, not changed
        // (out) R0 - result: 1 if check passed, 0 otherwise
        __ raw_push(R2, R3, LR);

        // Load an array of secondary_supers
        __ ldr(R2, Address(R0, Klass::secondary_supers_offset()));
        // Length goes to R3
        __ ldr_s32(R3, Address(R2, Array<Klass*>::length_offset_in_bytes()));
        __ add(R2, R2, Array<Klass*>::base_offset_in_bytes());

        Label loop, miss;
        __ bind(loop);
        __ cbz(R3, miss);
        __ ldr(LR, Address(R2, wordSize, post_indexed));
        __ sub(R3, R3, 1);
        __ cmp(LR, R1);
        __ b(loop, ne);

        // We get here if an equal cache entry is found
        __ str(R1, Address(R0, Klass::secondary_super_cache_offset()));
        __ mov(R0, 1);
        __ raw_pop_and_ret(R2, R3);

        // A cache entry not found - return false
        __ bind(miss);
        __ mov(R0, 0);
        __ raw_pop_and_ret(R2, R3);
      }
      break;

    case monitorenter_nofpu_id:
      save_fpu_registers = false;
      // fall through
    case monitorenter_id:
      {
        __ set_info("monitorenter", dont_gc_arguments);
        const Register obj  = R1;
        const Register lock = R2;
        OopMap* map = save_live_registers(sasm, save_fpu_registers);
        __ ldr(obj, Address(SP, arg1_offset));
        __ ldr(lock, Address(SP, arg2_offset));
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, monitorenter), obj, lock);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm, save_fpu_registers);
      }
      break;

    case monitorexit_nofpu_id:
      save_fpu_registers = false;
      // fall through
    case monitorexit_id:
      {
        __ set_info("monitorexit", dont_gc_arguments);
        const Register lock = R1;
        OopMap* map = save_live_registers(sasm, save_fpu_registers);
        __ ldr(lock, Address(SP, arg1_offset));
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, monitorexit), lock);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm, save_fpu_registers);
      }
      break;

    case deoptimize_id:
      {
        __ set_info("deoptimize", dont_gc_arguments);
        OopMap* oop_map = save_live_registers(sasm);
        const Register trap_request = R1;
        __ ldr(trap_request, Address(SP, arg1_offset));
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, deoptimize), trap_request);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);
        restore_live_registers_without_return(sasm);
        DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
        assert(deopt_blob != NULL, "deoptimization blob must have been created");
        __ jump(deopt_blob->unpack_with_reexecution(), relocInfo::runtime_call_type, AARCH64_ONLY(Rtemp) NOT_AARCH64(noreg));
      }
      break;

    case access_field_patching_id:
      {
        __ set_info("access_field_patching", dont_gc_arguments);
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, access_field_patching));
      }
      break;

    case load_klass_patching_id:
      {
        __ set_info("load_klass_patching", dont_gc_arguments);
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_klass_patching));
      }
      break;

    case load_appendix_patching_id:
      {
        __ set_info("load_appendix_patching", dont_gc_arguments);
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_appendix_patching));
      }
      break;

    case load_mirror_patching_id:
      {
        __ set_info("load_mirror_patching", dont_gc_arguments);
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_mirror_patching));
      }
      break;

    case predicate_failed_trap_id:
      {
        __ set_info("predicate_failed_trap", dont_gc_arguments);

        OopMap* oop_map = save_live_registers(sasm);
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, predicate_failed_trap));

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);

        restore_live_registers_without_return(sasm);

        DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
        assert(deopt_blob != NULL, "deoptimization blob must have been created");
        __ jump(deopt_blob->unpack_with_reexecution(), relocInfo::runtime_call_type, Rtemp);
      }
      break;

    default:
      {
        __ set_info("unimplemented entry", dont_gc_arguments);
        STOP("unimplemented entry");
      }
      break;
  }
  return oop_maps;
}

#undef __

#ifdef __SOFTFP__
const char *Runtime1::pd_name_for_address(address entry) {

#define FUNCTION_CASE(a, f) \
  if ((intptr_t)a == CAST_FROM_FN_PTR(intptr_t, f))  return #f

  FUNCTION_CASE(entry, __aeabi_fadd_glibc);
  FUNCTION_CASE(entry, __aeabi_fmul);
  FUNCTION_CASE(entry, __aeabi_fsub_glibc);
  FUNCTION_CASE(entry, __aeabi_fdiv);

  // __aeabi_XXXX_glibc: Imported code from glibc soft-fp bundle for calculation accuracy improvement. See CR 6757269.
  FUNCTION_CASE(entry, __aeabi_dadd_glibc);
  FUNCTION_CASE(entry, __aeabi_dmul);
  FUNCTION_CASE(entry, __aeabi_dsub_glibc);
  FUNCTION_CASE(entry, __aeabi_ddiv);

  FUNCTION_CASE(entry, __aeabi_f2d);
  FUNCTION_CASE(entry, __aeabi_d2f);
  FUNCTION_CASE(entry, __aeabi_i2f);
  FUNCTION_CASE(entry, __aeabi_i2d);
  FUNCTION_CASE(entry, __aeabi_f2iz);

  FUNCTION_CASE(entry, SharedRuntime::fcmpl);
  FUNCTION_CASE(entry, SharedRuntime::fcmpg);
  FUNCTION_CASE(entry, SharedRuntime::dcmpl);
  FUNCTION_CASE(entry, SharedRuntime::dcmpg);

  FUNCTION_CASE(entry, SharedRuntime::unordered_fcmplt);
  FUNCTION_CASE(entry, SharedRuntime::unordered_dcmplt);
  FUNCTION_CASE(entry, SharedRuntime::unordered_fcmple);
  FUNCTION_CASE(entry, SharedRuntime::unordered_dcmple);
  FUNCTION_CASE(entry, SharedRuntime::unordered_fcmpge);
  FUNCTION_CASE(entry, SharedRuntime::unordered_dcmpge);
  FUNCTION_CASE(entry, SharedRuntime::unordered_fcmpgt);
  FUNCTION_CASE(entry, SharedRuntime::unordered_dcmpgt);

  FUNCTION_CASE(entry, SharedRuntime::fneg);
  FUNCTION_CASE(entry, SharedRuntime::dneg);

  FUNCTION_CASE(entry, __aeabi_fcmpeq);
  FUNCTION_CASE(entry, __aeabi_fcmplt);
  FUNCTION_CASE(entry, __aeabi_fcmple);
  FUNCTION_CASE(entry, __aeabi_fcmpge);
  FUNCTION_CASE(entry, __aeabi_fcmpgt);

  FUNCTION_CASE(entry, __aeabi_dcmpeq);
  FUNCTION_CASE(entry, __aeabi_dcmplt);
  FUNCTION_CASE(entry, __aeabi_dcmple);
  FUNCTION_CASE(entry, __aeabi_dcmpge);
  FUNCTION_CASE(entry, __aeabi_dcmpgt);
#undef FUNCTION_CASE
  return "";
}
#else  // __SOFTFP__
const char *Runtime1::pd_name_for_address(address entry) {
  return "<unknown function>";
}
#endif // __SOFTFP__
