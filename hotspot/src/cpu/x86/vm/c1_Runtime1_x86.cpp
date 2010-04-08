/*
 * Copyright 1999-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_c1_Runtime1_x86.cpp.incl"


// Implementation of StubAssembler

int StubAssembler::call_RT(Register oop_result1, Register oop_result2, address entry, int args_size) {
  // setup registers
  const Register thread = NOT_LP64(rdi) LP64_ONLY(r15_thread); // is callee-saved register (Visual C++ calling conventions)
  assert(!(oop_result1->is_valid() || oop_result2->is_valid()) || oop_result1 != oop_result2, "registers must be different");
  assert(oop_result1 != thread && oop_result2 != thread, "registers must be different");
  assert(args_size >= 0, "illegal args_size");

#ifdef _LP64
  mov(c_rarg0, thread);
  set_num_rt_args(0); // Nothing on stack
#else
  set_num_rt_args(1 + args_size);

  // push java thread (becomes first argument of C function)
  get_thread(thread);
  push(thread);
#endif // _LP64

  set_last_Java_frame(thread, noreg, rbp, NULL);

  // do the call
  call(RuntimeAddress(entry));
  int call_offset = offset();
  // verify callee-saved register
#ifdef ASSERT
  guarantee(thread != rax, "change this code");
  push(rax);
  { Label L;
    get_thread(rax);
    cmpptr(thread, rax);
    jcc(Assembler::equal, L);
    int3();
    stop("StubAssembler::call_RT: rdi not callee saved?");
    bind(L);
  }
  pop(rax);
#endif
  reset_last_Java_frame(thread, true, false);

  // discard thread and arguments
  NOT_LP64(addptr(rsp, num_rt_args()*BytesPerWord));

  // check for pending exceptions
  { Label L;
    cmpptr(Address(thread, Thread::pending_exception_offset()), (int32_t)NULL_WORD);
    jcc(Assembler::equal, L);
    // exception pending => remove activation and forward to exception handler
    movptr(rax, Address(thread, Thread::pending_exception_offset()));
    // make sure that the vm_results are cleared
    if (oop_result1->is_valid()) {
      movptr(Address(thread, JavaThread::vm_result_offset()), NULL_WORD);
    }
    if (oop_result2->is_valid()) {
      movptr(Address(thread, JavaThread::vm_result_2_offset()), NULL_WORD);
    }
    if (frame_size() == no_frame_size) {
      leave();
      jump(RuntimeAddress(StubRoutines::forward_exception_entry()));
    } else if (_stub_id == Runtime1::forward_exception_id) {
      should_not_reach_here();
    } else {
      jump(RuntimeAddress(Runtime1::entry_for(Runtime1::forward_exception_id)));
    }
    bind(L);
  }
  // get oop results if there are any and reset the values in the thread
  if (oop_result1->is_valid()) {
    movptr(oop_result1, Address(thread, JavaThread::vm_result_offset()));
    movptr(Address(thread, JavaThread::vm_result_offset()), NULL_WORD);
    verify_oop(oop_result1);
  }
  if (oop_result2->is_valid()) {
    movptr(oop_result2, Address(thread, JavaThread::vm_result_2_offset()));
    movptr(Address(thread, JavaThread::vm_result_2_offset()), NULL_WORD);
    verify_oop(oop_result2);
  }
  return call_offset;
}


int StubAssembler::call_RT(Register oop_result1, Register oop_result2, address entry, Register arg1) {
#ifdef _LP64
  mov(c_rarg1, arg1);
#else
  push(arg1);
#endif // _LP64
  return call_RT(oop_result1, oop_result2, entry, 1);
}


int StubAssembler::call_RT(Register oop_result1, Register oop_result2, address entry, Register arg1, Register arg2) {
#ifdef _LP64
  if (c_rarg1 == arg2) {
    if (c_rarg2 == arg1) {
      xchgq(arg1, arg2);
    } else {
      mov(c_rarg2, arg2);
      mov(c_rarg1, arg1);
    }
  } else {
    mov(c_rarg1, arg1);
    mov(c_rarg2, arg2);
  }
#else
  push(arg2);
  push(arg1);
#endif // _LP64
  return call_RT(oop_result1, oop_result2, entry, 2);
}


int StubAssembler::call_RT(Register oop_result1, Register oop_result2, address entry, Register arg1, Register arg2, Register arg3) {
#ifdef _LP64
  // if there is any conflict use the stack
  if (arg1 == c_rarg2 || arg1 == c_rarg3 ||
      arg2 == c_rarg1 || arg1 == c_rarg3 ||
      arg3 == c_rarg1 || arg1 == c_rarg2) {
    push(arg3);
    push(arg2);
    push(arg1);
    pop(c_rarg1);
    pop(c_rarg2);
    pop(c_rarg3);
  } else {
    mov(c_rarg1, arg1);
    mov(c_rarg2, arg2);
    mov(c_rarg3, arg3);
  }
#else
  push(arg3);
  push(arg2);
  push(arg1);
#endif // _LP64
  return call_RT(oop_result1, oop_result2, entry, 3);
}


// Implementation of StubFrame

class StubFrame: public StackObj {
 private:
  StubAssembler* _sasm;

 public:
  StubFrame(StubAssembler* sasm, const char* name, bool must_gc_arguments);
  void load_argument(int offset_in_words, Register reg);

  ~StubFrame();
};


#define __ _sasm->

StubFrame::StubFrame(StubAssembler* sasm, const char* name, bool must_gc_arguments) {
  _sasm = sasm;
  __ set_info(name, must_gc_arguments);
  __ enter();
}

// load parameters that were stored with LIR_Assembler::store_parameter
// Note: offsets for store_parameter and load_argument must match
void StubFrame::load_argument(int offset_in_words, Register reg) {
  // rbp, + 0: link
  //     + 1: return address
  //     + 2: argument with offset 0
  //     + 3: argument with offset 1
  //     + 4: ...

  __ movptr(reg, Address(rbp, (offset_in_words + 2) * BytesPerWord));
}


StubFrame::~StubFrame() {
  __ leave();
  __ ret(0);
}

#undef __


// Implementation of Runtime1

#define __ sasm->

const int float_regs_as_doubles_size_in_slots = pd_nof_fpu_regs_frame_map * 2;
const int xmm_regs_as_doubles_size_in_slots = FrameMap::nof_xmm_regs * 2;

// Stack layout for saving/restoring  all the registers needed during a runtime
// call (this includes deoptimization)
// Note: note that users of this frame may well have arguments to some runtime
// while these values are on the stack. These positions neglect those arguments
// but the code in save_live_registers will take the argument count into
// account.
//
#ifdef _LP64
  #define SLOT2(x) x,
  #define SLOT_PER_WORD 2
#else
  #define SLOT2(x)
  #define SLOT_PER_WORD 1
#endif // _LP64

enum reg_save_layout {
  // 64bit needs to keep stack 16 byte aligned. So we add some alignment dummies to make that
  // happen and will assert if the stack size we create is misaligned
#ifdef _LP64
  align_dummy_0, align_dummy_1,
#endif // _LP64
  dummy1, SLOT2(dummy1H)                                                                    // 0, 4
  dummy2, SLOT2(dummy2H)                                                                    // 8, 12
  // Two temps to be used as needed by users of save/restore callee registers
  temp_2_off, SLOT2(temp_2H_off)                                                            // 16, 20
  temp_1_off, SLOT2(temp_1H_off)                                                            // 24, 28
  xmm_regs_as_doubles_off,                                                                  // 32
  float_regs_as_doubles_off = xmm_regs_as_doubles_off + xmm_regs_as_doubles_size_in_slots,  // 160
  fpu_state_off = float_regs_as_doubles_off + float_regs_as_doubles_size_in_slots,          // 224
  // fpu_state_end_off is exclusive
  fpu_state_end_off = fpu_state_off + (FPUStateSizeInWords / SLOT_PER_WORD),                // 352
  marker = fpu_state_end_off, SLOT2(markerH)                                                // 352, 356
  extra_space_offset,                                                                       // 360
#ifdef _LP64
  r15_off = extra_space_offset, r15H_off,                                                   // 360, 364
  r14_off, r14H_off,                                                                        // 368, 372
  r13_off, r13H_off,                                                                        // 376, 380
  r12_off, r12H_off,                                                                        // 384, 388
  r11_off, r11H_off,                                                                        // 392, 396
  r10_off, r10H_off,                                                                        // 400, 404
  r9_off, r9H_off,                                                                          // 408, 412
  r8_off, r8H_off,                                                                          // 416, 420
  rdi_off, rdiH_off,                                                                        // 424, 428
#else
  rdi_off = extra_space_offset,
#endif // _LP64
  rsi_off, SLOT2(rsiH_off)                                                                  // 432, 436
  rbp_off, SLOT2(rbpH_off)                                                                  // 440, 444
  rsp_off, SLOT2(rspH_off)                                                                  // 448, 452
  rbx_off, SLOT2(rbxH_off)                                                                  // 456, 460
  rdx_off, SLOT2(rdxH_off)                                                                  // 464, 468
  rcx_off, SLOT2(rcxH_off)                                                                  // 472, 476
  rax_off, SLOT2(raxH_off)                                                                  // 480, 484
  saved_rbp_off, SLOT2(saved_rbpH_off)                                                      // 488, 492
  return_off, SLOT2(returnH_off)                                                            // 496, 500
  reg_save_frame_size,  // As noted: neglects any parameters to runtime                     // 504

#ifdef _WIN64
  c_rarg0_off = rcx_off,
#else
  c_rarg0_off = rdi_off,
#endif // WIN64

  // equates

  // illegal instruction handler
  continue_dest_off = temp_1_off,

  // deoptimization equates
  fp0_off = float_regs_as_doubles_off, // slot for java float/double return value
  xmm0_off = xmm_regs_as_doubles_off,  // slot for java float/double return value
  deopt_type = temp_2_off,             // slot for type of deopt in progress
  ret_type = temp_1_off                // slot for return type
};



// Save off registers which might be killed by calls into the runtime.
// Tries to smart of about FP registers.  In particular we separate
// saving and describing the FPU registers for deoptimization since we
// have to save the FPU registers twice if we describe them and on P4
// saving FPU registers which don't contain anything appears
// expensive.  The deopt blob is the only thing which needs to
// describe FPU registers.  In all other cases it should be sufficient
// to simply save their current value.

static OopMap* generate_oop_map(StubAssembler* sasm, int num_rt_args,
                                bool save_fpu_registers = true) {

  // In 64bit all the args are in regs so there are no additional stack slots
  LP64_ONLY(num_rt_args = 0);
  LP64_ONLY(assert((reg_save_frame_size * VMRegImpl::stack_slot_size) % 16 == 0, "must be 16 byte aligned");)
  int frame_size_in_slots = reg_save_frame_size + num_rt_args; // args + thread
  sasm->set_frame_size(frame_size_in_slots / VMRegImpl::slots_per_word );

  // record saved value locations in an OopMap
  // locations are offsets from sp after runtime call; num_rt_args is number of arguments in call, including thread
  OopMap* map = new OopMap(frame_size_in_slots, 0);
  map->set_callee_saved(VMRegImpl::stack2reg(rax_off + num_rt_args), rax->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(rcx_off + num_rt_args), rcx->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(rdx_off + num_rt_args), rdx->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(rbx_off + num_rt_args), rbx->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(rsi_off + num_rt_args), rsi->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(rdi_off + num_rt_args), rdi->as_VMReg());
#ifdef _LP64
  map->set_callee_saved(VMRegImpl::stack2reg(r8_off + num_rt_args),  r8->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(r9_off + num_rt_args),  r9->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(r10_off + num_rt_args), r10->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(r11_off + num_rt_args), r11->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(r12_off + num_rt_args), r12->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(r13_off + num_rt_args), r13->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(r14_off + num_rt_args), r14->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(r15_off + num_rt_args), r15->as_VMReg());

  // This is stupid but needed.
  map->set_callee_saved(VMRegImpl::stack2reg(raxH_off + num_rt_args), rax->as_VMReg()->next());
  map->set_callee_saved(VMRegImpl::stack2reg(rcxH_off + num_rt_args), rcx->as_VMReg()->next());
  map->set_callee_saved(VMRegImpl::stack2reg(rdxH_off + num_rt_args), rdx->as_VMReg()->next());
  map->set_callee_saved(VMRegImpl::stack2reg(rbxH_off + num_rt_args), rbx->as_VMReg()->next());
  map->set_callee_saved(VMRegImpl::stack2reg(rsiH_off + num_rt_args), rsi->as_VMReg()->next());
  map->set_callee_saved(VMRegImpl::stack2reg(rdiH_off + num_rt_args), rdi->as_VMReg()->next());

  map->set_callee_saved(VMRegImpl::stack2reg(r8H_off + num_rt_args),  r8->as_VMReg()->next());
  map->set_callee_saved(VMRegImpl::stack2reg(r9H_off + num_rt_args),  r9->as_VMReg()->next());
  map->set_callee_saved(VMRegImpl::stack2reg(r10H_off + num_rt_args), r10->as_VMReg()->next());
  map->set_callee_saved(VMRegImpl::stack2reg(r11H_off + num_rt_args), r11->as_VMReg()->next());
  map->set_callee_saved(VMRegImpl::stack2reg(r12H_off + num_rt_args), r12->as_VMReg()->next());
  map->set_callee_saved(VMRegImpl::stack2reg(r13H_off + num_rt_args), r13->as_VMReg()->next());
  map->set_callee_saved(VMRegImpl::stack2reg(r14H_off + num_rt_args), r14->as_VMReg()->next());
  map->set_callee_saved(VMRegImpl::stack2reg(r15H_off + num_rt_args), r15->as_VMReg()->next());
#endif // _LP64

  if (save_fpu_registers) {
    if (UseSSE < 2) {
      int fpu_off = float_regs_as_doubles_off;
      for (int n = 0; n < FrameMap::nof_fpu_regs; n++) {
        VMReg fpu_name_0 = FrameMap::fpu_regname(n);
        map->set_callee_saved(VMRegImpl::stack2reg(fpu_off +     num_rt_args), fpu_name_0);
        // %%% This is really a waste but we'll keep things as they were for now
        if (true) {
          map->set_callee_saved(VMRegImpl::stack2reg(fpu_off + 1 + num_rt_args), fpu_name_0->next());
        }
        fpu_off += 2;
      }
      assert(fpu_off == fpu_state_off, "incorrect number of fpu stack slots");
    }

    if (UseSSE >= 2) {
      int xmm_off = xmm_regs_as_doubles_off;
      for (int n = 0; n < FrameMap::nof_xmm_regs; n++) {
        VMReg xmm_name_0 = as_XMMRegister(n)->as_VMReg();
        map->set_callee_saved(VMRegImpl::stack2reg(xmm_off +     num_rt_args), xmm_name_0);
        // %%% This is really a waste but we'll keep things as they were for now
        if (true) {
          map->set_callee_saved(VMRegImpl::stack2reg(xmm_off + 1 + num_rt_args), xmm_name_0->next());
        }
        xmm_off += 2;
      }
      assert(xmm_off == float_regs_as_doubles_off, "incorrect number of xmm registers");

    } else if (UseSSE == 1) {
      int xmm_off = xmm_regs_as_doubles_off;
      for (int n = 0; n < FrameMap::nof_xmm_regs; n++) {
        VMReg xmm_name_0 = as_XMMRegister(n)->as_VMReg();
        map->set_callee_saved(VMRegImpl::stack2reg(xmm_off +     num_rt_args), xmm_name_0);
        xmm_off += 2;
      }
      assert(xmm_off == float_regs_as_doubles_off, "incorrect number of xmm registers");
    }
  }

  return map;
}

static OopMap* save_live_registers(StubAssembler* sasm, int num_rt_args,
                                   bool save_fpu_registers = true) {
  __ block_comment("save_live_registers");

  // 64bit passes the args in regs to the c++ runtime
  int frame_size_in_slots = reg_save_frame_size NOT_LP64(+ num_rt_args); // args + thread
  // frame_size = round_to(frame_size, 4);
  sasm->set_frame_size(frame_size_in_slots / VMRegImpl::slots_per_word );

  __ pusha();         // integer registers

  // assert(float_regs_as_doubles_off % 2 == 0, "misaligned offset");
  // assert(xmm_regs_as_doubles_off % 2 == 0, "misaligned offset");

  __ subptr(rsp, extra_space_offset * VMRegImpl::stack_slot_size);

#ifdef ASSERT
  __ movptr(Address(rsp, marker * VMRegImpl::stack_slot_size), (int32_t)0xfeedbeef);
#endif

  if (save_fpu_registers) {
    if (UseSSE < 2) {
      // save FPU stack
      __ fnsave(Address(rsp, fpu_state_off * VMRegImpl::stack_slot_size));
      __ fwait();

#ifdef ASSERT
      Label ok;
      __ cmpw(Address(rsp, fpu_state_off * VMRegImpl::stack_slot_size), StubRoutines::fpu_cntrl_wrd_std());
      __ jccb(Assembler::equal, ok);
      __ stop("corrupted control word detected");
      __ bind(ok);
#endif

      // Reset the control word to guard against exceptions being unmasked
      // since fstp_d can cause FPU stack underflow exceptions.  Write it
      // into the on stack copy and then reload that to make sure that the
      // current and future values are correct.
      __ movw(Address(rsp, fpu_state_off * VMRegImpl::stack_slot_size), StubRoutines::fpu_cntrl_wrd_std());
      __ frstor(Address(rsp, fpu_state_off * VMRegImpl::stack_slot_size));

      // Save the FPU registers in de-opt-able form
      __ fstp_d(Address(rsp, float_regs_as_doubles_off * VMRegImpl::stack_slot_size +  0));
      __ fstp_d(Address(rsp, float_regs_as_doubles_off * VMRegImpl::stack_slot_size +  8));
      __ fstp_d(Address(rsp, float_regs_as_doubles_off * VMRegImpl::stack_slot_size + 16));
      __ fstp_d(Address(rsp, float_regs_as_doubles_off * VMRegImpl::stack_slot_size + 24));
      __ fstp_d(Address(rsp, float_regs_as_doubles_off * VMRegImpl::stack_slot_size + 32));
      __ fstp_d(Address(rsp, float_regs_as_doubles_off * VMRegImpl::stack_slot_size + 40));
      __ fstp_d(Address(rsp, float_regs_as_doubles_off * VMRegImpl::stack_slot_size + 48));
      __ fstp_d(Address(rsp, float_regs_as_doubles_off * VMRegImpl::stack_slot_size + 56));
    }

    if (UseSSE >= 2) {
      // save XMM registers
      // XMM registers can contain float or double values, but this is not known here,
      // so always save them as doubles.
      // note that float values are _not_ converted automatically, so for float values
      // the second word contains only garbage data.
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size +  0), xmm0);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size +  8), xmm1);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 16), xmm2);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 24), xmm3);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 32), xmm4);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 40), xmm5);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 48), xmm6);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 56), xmm7);
#ifdef _LP64
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 64), xmm8);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 72), xmm9);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 80), xmm10);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 88), xmm11);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 96), xmm12);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 104), xmm13);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 112), xmm14);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 120), xmm15);
#endif // _LP64
    } else if (UseSSE == 1) {
      // save XMM registers as float because double not supported without SSE2
      __ movflt(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size +  0), xmm0);
      __ movflt(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size +  8), xmm1);
      __ movflt(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 16), xmm2);
      __ movflt(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 24), xmm3);
      __ movflt(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 32), xmm4);
      __ movflt(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 40), xmm5);
      __ movflt(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 48), xmm6);
      __ movflt(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 56), xmm7);
    }
  }

  // FPU stack must be empty now
  __ verify_FPU(0, "save_live_registers");

  return generate_oop_map(sasm, num_rt_args, save_fpu_registers);
}


static void restore_fpu(StubAssembler* sasm, bool restore_fpu_registers = true) {
  if (restore_fpu_registers) {
    if (UseSSE >= 2) {
      // restore XMM registers
      __ movdbl(xmm0, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size +  0));
      __ movdbl(xmm1, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size +  8));
      __ movdbl(xmm2, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 16));
      __ movdbl(xmm3, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 24));
      __ movdbl(xmm4, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 32));
      __ movdbl(xmm5, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 40));
      __ movdbl(xmm6, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 48));
      __ movdbl(xmm7, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 56));
#ifdef _LP64
      __ movdbl(xmm8, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 64));
      __ movdbl(xmm9, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 72));
      __ movdbl(xmm10, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 80));
      __ movdbl(xmm11, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 88));
      __ movdbl(xmm12, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 96));
      __ movdbl(xmm13, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 104));
      __ movdbl(xmm14, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 112));
      __ movdbl(xmm15, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 120));
#endif // _LP64
    } else if (UseSSE == 1) {
      // restore XMM registers
      __ movflt(xmm0, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size +  0));
      __ movflt(xmm1, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size +  8));
      __ movflt(xmm2, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 16));
      __ movflt(xmm3, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 24));
      __ movflt(xmm4, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 32));
      __ movflt(xmm5, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 40));
      __ movflt(xmm6, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 48));
      __ movflt(xmm7, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + 56));
    }

    if (UseSSE < 2) {
      __ frstor(Address(rsp, fpu_state_off * VMRegImpl::stack_slot_size));
    } else {
      // check that FPU stack is really empty
      __ verify_FPU(0, "restore_live_registers");
    }

  } else {
    // check that FPU stack is really empty
    __ verify_FPU(0, "restore_live_registers");
  }

#ifdef ASSERT
  {
    Label ok;
    __ cmpptr(Address(rsp, marker * VMRegImpl::stack_slot_size), (int32_t)0xfeedbeef);
    __ jcc(Assembler::equal, ok);
    __ stop("bad offsets in frame");
    __ bind(ok);
  }
#endif // ASSERT

  __ addptr(rsp, extra_space_offset * VMRegImpl::stack_slot_size);
}


static void restore_live_registers(StubAssembler* sasm, bool restore_fpu_registers = true) {
  __ block_comment("restore_live_registers");

  restore_fpu(sasm, restore_fpu_registers);
  __ popa();
}


static void restore_live_registers_except_rax(StubAssembler* sasm, bool restore_fpu_registers = true) {
  __ block_comment("restore_live_registers_except_rax");

  restore_fpu(sasm, restore_fpu_registers);

#ifdef _LP64
  __ movptr(r15, Address(rsp, 0));
  __ movptr(r14, Address(rsp, wordSize));
  __ movptr(r13, Address(rsp, 2 * wordSize));
  __ movptr(r12, Address(rsp, 3 * wordSize));
  __ movptr(r11, Address(rsp, 4 * wordSize));
  __ movptr(r10, Address(rsp, 5 * wordSize));
  __ movptr(r9,  Address(rsp, 6 * wordSize));
  __ movptr(r8,  Address(rsp, 7 * wordSize));
  __ movptr(rdi, Address(rsp, 8 * wordSize));
  __ movptr(rsi, Address(rsp, 9 * wordSize));
  __ movptr(rbp, Address(rsp, 10 * wordSize));
  // skip rsp
  __ movptr(rbx, Address(rsp, 12 * wordSize));
  __ movptr(rdx, Address(rsp, 13 * wordSize));
  __ movptr(rcx, Address(rsp, 14 * wordSize));

  __ addptr(rsp, 16 * wordSize);
#else

  __ pop(rdi);
  __ pop(rsi);
  __ pop(rbp);
  __ pop(rbx); // skip this value
  __ pop(rbx);
  __ pop(rdx);
  __ pop(rcx);
  __ addptr(rsp, BytesPerWord);
#endif // _LP64
}


void Runtime1::initialize_pd() {
  // nothing to do
}


// target: the entry point of the method that creates and posts the exception oop
// has_argument: true if the exception needs an argument (passed on stack because registers must be preserved)

OopMapSet* Runtime1::generate_exception_throw(StubAssembler* sasm, address target, bool has_argument) {
  // preserve all registers
  int num_rt_args = has_argument ? 2 : 1;
  OopMap* oop_map = save_live_registers(sasm, num_rt_args);

  // now all registers are saved and can be used freely
  // verify that no old value is used accidentally
  __ invalidate_registers(true, true, true, true, true, true);

  // registers used by this stub
  const Register temp_reg = rbx;

  // load argument for exception that is passed as an argument into the stub
  if (has_argument) {
#ifdef _LP64
    __ movptr(c_rarg1, Address(rbp, 2*BytesPerWord));
#else
    __ movptr(temp_reg, Address(rbp, 2*BytesPerWord));
    __ push(temp_reg);
#endif // _LP64
  }
  int call_offset = __ call_RT(noreg, noreg, target, num_rt_args - 1);

  OopMapSet* oop_maps = new OopMapSet();
  oop_maps->add_gc_map(call_offset, oop_map);

  __ stop("should not reach here");

  return oop_maps;
}


void Runtime1::generate_handle_exception(StubAssembler *sasm, OopMapSet* oop_maps, OopMap* oop_map, bool save_fpu_registers) {
  // incoming parameters
  const Register exception_oop = rax;
  const Register exception_pc = rdx;
  // other registers used in this stub
  const Register real_return_addr = rbx;
  const Register thread = NOT_LP64(rdi) LP64_ONLY(r15_thread);

  __ block_comment("generate_handle_exception");

#ifdef TIERED
  // C2 can leave the fpu stack dirty
  if (UseSSE < 2 ) {
    __ empty_FPU_stack();
  }
#endif // TIERED

  // verify that only rax, and rdx is valid at this time
  __ invalidate_registers(false, true, true, false, true, true);
  // verify that rax, contains a valid exception
  __ verify_not_null_oop(exception_oop);

  // load address of JavaThread object for thread-local data
  NOT_LP64(__ get_thread(thread);)

#ifdef ASSERT
  // check that fields in JavaThread for exception oop and issuing pc are
  // empty before writing to them
  Label oop_empty;
  __ cmpptr(Address(thread, JavaThread::exception_oop_offset()), (int32_t) NULL_WORD);
  __ jcc(Assembler::equal, oop_empty);
  __ stop("exception oop already set");
  __ bind(oop_empty);

  Label pc_empty;
  __ cmpptr(Address(thread, JavaThread::exception_pc_offset()), 0);
  __ jcc(Assembler::equal, pc_empty);
  __ stop("exception pc already set");
  __ bind(pc_empty);
#endif

  // save exception oop and issuing pc into JavaThread
  // (exception handler will load it from here)
  __ movptr(Address(thread, JavaThread::exception_oop_offset()), exception_oop);
  __ movptr(Address(thread, JavaThread::exception_pc_offset()), exception_pc);

  // save real return address (pc that called this stub)
  __ movptr(real_return_addr, Address(rbp, 1*BytesPerWord));
  __ movptr(Address(rsp, temp_1_off * VMRegImpl::stack_slot_size), real_return_addr);

  // patch throwing pc into return address (has bci & oop map)
  __ movptr(Address(rbp, 1*BytesPerWord), exception_pc);

  // compute the exception handler.
  // the exception oop and the throwing pc are read from the fields in JavaThread
  int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, exception_handler_for_pc));
  oop_maps->add_gc_map(call_offset, oop_map);

  // rax,: handler address
  //      will be the deopt blob if nmethod was deoptimized while we looked up
  //      handler regardless of whether handler existed in the nmethod.

  // only rax, is valid at this time, all other registers have been destroyed by the runtime call
  __ invalidate_registers(false, true, true, true, true, true);

#ifdef ASSERT
  // Do we have an exception handler in the nmethod?
  Label done;
  __ testptr(rax, rax);
  __ jcc(Assembler::notZero, done);
  __ stop("no handler found");
  __ bind(done);
#endif

  // exception handler found
  // patch the return address -> the stub will directly return to the exception handler
  __ movptr(Address(rbp, 1*BytesPerWord), rax);

  // restore registers
  restore_live_registers(sasm, save_fpu_registers);

  // return to exception handler
  __ leave();
  __ ret(0);

}


void Runtime1::generate_unwind_exception(StubAssembler *sasm) {
  // incoming parameters
  const Register exception_oop = rax;
  // callee-saved copy of exception_oop during runtime call
  const Register exception_oop_callee_saved = NOT_LP64(rsi) LP64_ONLY(r14);
  // other registers used in this stub
  const Register exception_pc = rdx;
  const Register handler_addr = rbx;
  const Register thread = NOT_LP64(rdi) LP64_ONLY(r15_thread);

  // verify that only rax, is valid at this time
  __ invalidate_registers(false, true, true, true, true, true);

#ifdef ASSERT
  // check that fields in JavaThread for exception oop and issuing pc are empty
  NOT_LP64(__ get_thread(thread);)
  Label oop_empty;
  __ cmpptr(Address(thread, JavaThread::exception_oop_offset()), 0);
  __ jcc(Assembler::equal, oop_empty);
  __ stop("exception oop must be empty");
  __ bind(oop_empty);

  Label pc_empty;
  __ cmpptr(Address(thread, JavaThread::exception_pc_offset()), 0);
  __ jcc(Assembler::equal, pc_empty);
  __ stop("exception pc must be empty");
  __ bind(pc_empty);
#endif

  // clear the FPU stack in case any FPU results are left behind
  __ empty_FPU_stack();

  // save exception_oop in callee-saved register to preserve it during runtime calls
  __ verify_not_null_oop(exception_oop);
  __ movptr(exception_oop_callee_saved, exception_oop);

  NOT_LP64(__ get_thread(thread);)
  // Get return address (is on top of stack after leave).
  __ movptr(exception_pc, Address(rsp, 0));

  // search the exception handler address of the caller (using the return address)
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address), thread, exception_pc);
  // rax: exception handler address of the caller

  // Only RAX and RSI are valid at this time, all other registers have been destroyed by the call.
  __ invalidate_registers(false, true, true, true, false, true);

  // move result of call into correct register
  __ movptr(handler_addr, rax);

  // Restore exception oop to RAX (required convention of exception handler).
  __ movptr(exception_oop, exception_oop_callee_saved);

  // verify that there is really a valid exception in rax
  __ verify_not_null_oop(exception_oop);

  // get throwing pc (= return address).
  // rdx has been destroyed by the call, so it must be set again
  // the pop is also necessary to simulate the effect of a ret(0)
  __ pop(exception_pc);

  // Restore SP from BP if the exception PC is a MethodHandle call site.
  NOT_LP64(__ get_thread(thread);)
  __ cmpl(Address(thread, JavaThread::is_method_handle_return_offset()), 0);
  __ cmovptr(Assembler::notEqual, rsp, rbp);

  // continue at exception handler (return address removed)
  // note: do *not* remove arguments when unwinding the
  //       activation since the caller assumes having
  //       all arguments on the stack when entering the
  //       runtime to determine the exception handler
  //       (GC happens at call site with arguments!)
  // rax: exception oop
  // rdx: throwing pc
  // rbx: exception handler
  __ jmp(handler_addr);
}


OopMapSet* Runtime1::generate_patching(StubAssembler* sasm, address target) {
  // use the maximum number of runtime-arguments here because it is difficult to
  // distinguish each RT-Call.
  // Note: This number affects also the RT-Call in generate_handle_exception because
  //       the oop-map is shared for all calls.
  const int num_rt_args = 2;  // thread + dummy

  DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
  assert(deopt_blob != NULL, "deoptimization blob must have been created");

  OopMap* oop_map = save_live_registers(sasm, num_rt_args);

#ifdef _LP64
  const Register thread = r15_thread;
  // No need to worry about dummy
  __ mov(c_rarg0, thread);
#else
  __ push(rax); // push dummy

  const Register thread = rdi; // is callee-saved register (Visual C++ calling conventions)
  // push java thread (becomes first argument of C function)
  __ get_thread(thread);
  __ push(thread);
#endif // _LP64
  __ set_last_Java_frame(thread, noreg, rbp, NULL);
  // do the call
  __ call(RuntimeAddress(target));
  OopMapSet* oop_maps = new OopMapSet();
  oop_maps->add_gc_map(__ offset(), oop_map);
  // verify callee-saved register
#ifdef ASSERT
  guarantee(thread != rax, "change this code");
  __ push(rax);
  { Label L;
    __ get_thread(rax);
    __ cmpptr(thread, rax);
    __ jcc(Assembler::equal, L);
    __ stop("StubAssembler::call_RT: rdi/r15 not callee saved?");
    __ bind(L);
  }
  __ pop(rax);
#endif
  __ reset_last_Java_frame(thread, true, false);
#ifndef _LP64
  __ pop(rcx); // discard thread arg
  __ pop(rcx); // discard dummy
#endif // _LP64

  // check for pending exceptions
  { Label L;
    __ cmpptr(Address(thread, Thread::pending_exception_offset()), (int32_t)NULL_WORD);
    __ jcc(Assembler::equal, L);
    // exception pending => remove activation and forward to exception handler

    __ testptr(rax, rax);                                   // have we deoptimized?
    __ jump_cc(Assembler::equal,
               RuntimeAddress(Runtime1::entry_for(Runtime1::forward_exception_id)));

    // the deopt blob expects exceptions in the special fields of
    // JavaThread, so copy and clear pending exception.

    // load and clear pending exception
    __ movptr(rax, Address(thread, Thread::pending_exception_offset()));
    __ movptr(Address(thread, Thread::pending_exception_offset()), NULL_WORD);

    // check that there is really a valid exception
    __ verify_not_null_oop(rax);

    // load throwing pc: this is the return address of the stub
    __ movptr(rdx, Address(rsp, return_off * VMRegImpl::stack_slot_size));

#ifdef ASSERT
    // check that fields in JavaThread for exception oop and issuing pc are empty
    Label oop_empty;
    __ cmpptr(Address(thread, JavaThread::exception_oop_offset()), (int32_t)NULL_WORD);
    __ jcc(Assembler::equal, oop_empty);
    __ stop("exception oop must be empty");
    __ bind(oop_empty);

    Label pc_empty;
    __ cmpptr(Address(thread, JavaThread::exception_pc_offset()), (int32_t)NULL_WORD);
    __ jcc(Assembler::equal, pc_empty);
    __ stop("exception pc must be empty");
    __ bind(pc_empty);
#endif

    // store exception oop and throwing pc to JavaThread
    __ movptr(Address(thread, JavaThread::exception_oop_offset()), rax);
    __ movptr(Address(thread, JavaThread::exception_pc_offset()), rdx);

    restore_live_registers(sasm);

    __ leave();
    __ addptr(rsp, BytesPerWord);  // remove return address from stack

    // Forward the exception directly to deopt blob. We can blow no
    // registers and must leave throwing pc on the stack.  A patch may
    // have values live in registers so the entry point with the
    // exception in tls.
    __ jump(RuntimeAddress(deopt_blob->unpack_with_exception_in_tls()));

    __ bind(L);
  }


  // Runtime will return true if the nmethod has been deoptimized during
  // the patching process. In that case we must do a deopt reexecute instead.

  Label reexecuteEntry, cont;

  __ testptr(rax, rax);                                 // have we deoptimized?
  __ jcc(Assembler::equal, cont);                       // no

  // Will reexecute. Proper return address is already on the stack we just restore
  // registers, pop all of our frame but the return address and jump to the deopt blob
  restore_live_registers(sasm);
  __ leave();
  __ jump(RuntimeAddress(deopt_blob->unpack_with_reexecution()));

  __ bind(cont);
  restore_live_registers(sasm);
  __ leave();
  __ ret(0);

  return oop_maps;

}


OopMapSet* Runtime1::generate_code_for(StubID id, StubAssembler* sasm) {

  // for better readability
  const bool must_gc_arguments = true;
  const bool dont_gc_arguments = false;

  // default value; overwritten for some optimized stubs that are called from methods that do not use the fpu
  bool save_fpu_registers = true;

  // stub code & info for the different stubs
  OopMapSet* oop_maps = NULL;
  switch (id) {
    case forward_exception_id:
      {
        // we're handling an exception in the context of a compiled
        // frame.  The registers have been saved in the standard
        // places.  Perform an exception lookup in the caller and
        // dispatch to the handler if found.  Otherwise unwind and
        // dispatch to the callers exception handler.

        const Register thread = NOT_LP64(rdi) LP64_ONLY(r15_thread);
        const Register exception_oop = rax;
        const Register exception_pc = rdx;

        // load pending exception oop into rax,
        __ movptr(exception_oop, Address(thread, Thread::pending_exception_offset()));
        // clear pending exception
        __ movptr(Address(thread, Thread::pending_exception_offset()), NULL_WORD);

        // load issuing PC (the return address for this stub) into rdx
        __ movptr(exception_pc, Address(rbp, 1*BytesPerWord));

        // make sure that the vm_results are cleared (may be unnecessary)
        __ movptr(Address(thread, JavaThread::vm_result_offset()), NULL_WORD);
        __ movptr(Address(thread, JavaThread::vm_result_2_offset()), NULL_WORD);

        // verify that that there is really a valid exception in rax,
        __ verify_not_null_oop(exception_oop);


        oop_maps = new OopMapSet();
        OopMap* oop_map = generate_oop_map(sasm, 1);
        generate_handle_exception(sasm, oop_maps, oop_map);
        __ stop("should not reach here");
      }
      break;

    case new_instance_id:
    case fast_new_instance_id:
    case fast_new_instance_init_check_id:
      {
        Register klass = rdx; // Incoming
        Register obj   = rax; // Result

        if (id == new_instance_id) {
          __ set_info("new_instance", dont_gc_arguments);
        } else if (id == fast_new_instance_id) {
          __ set_info("fast new_instance", dont_gc_arguments);
        } else {
          assert(id == fast_new_instance_init_check_id, "bad StubID");
          __ set_info("fast new_instance init check", dont_gc_arguments);
        }

        if ((id == fast_new_instance_id || id == fast_new_instance_init_check_id) &&
            UseTLAB && FastTLABRefill) {
          Label slow_path;
          Register obj_size = rcx;
          Register t1       = rbx;
          Register t2       = rsi;
          assert_different_registers(klass, obj, obj_size, t1, t2);

          __ push(rdi);
          __ push(rbx);

          if (id == fast_new_instance_init_check_id) {
            // make sure the klass is initialized
            __ cmpl(Address(klass, instanceKlass::init_state_offset_in_bytes() + sizeof(oopDesc)), instanceKlass::fully_initialized);
            __ jcc(Assembler::notEqual, slow_path);
          }

#ifdef ASSERT
          // assert object can be fast path allocated
          {
            Label ok, not_ok;
            __ movl(obj_size, Address(klass, Klass::layout_helper_offset_in_bytes() + sizeof(oopDesc)));
            __ cmpl(obj_size, 0);  // make sure it's an instance (LH > 0)
            __ jcc(Assembler::lessEqual, not_ok);
            __ testl(obj_size, Klass::_lh_instance_slow_path_bit);
            __ jcc(Assembler::zero, ok);
            __ bind(not_ok);
            __ stop("assert(can be fast path allocated)");
            __ should_not_reach_here();
            __ bind(ok);
          }
#endif // ASSERT

          // if we got here then the TLAB allocation failed, so try
          // refilling the TLAB or allocating directly from eden.
          Label retry_tlab, try_eden;
          __ tlab_refill(retry_tlab, try_eden, slow_path); // does not destroy rdx (klass)

          __ bind(retry_tlab);

          // get the instance size (size is postive so movl is fine for 64bit)
          __ movl(obj_size, Address(klass, klassOopDesc::header_size() * HeapWordSize + Klass::layout_helper_offset_in_bytes()));
          __ tlab_allocate(obj, obj_size, 0, t1, t2, slow_path);
          __ initialize_object(obj, klass, obj_size, 0, t1, t2);
          __ verify_oop(obj);
          __ pop(rbx);
          __ pop(rdi);
          __ ret(0);

          __ bind(try_eden);
          // get the instance size (size is postive so movl is fine for 64bit)
          __ movl(obj_size, Address(klass, klassOopDesc::header_size() * HeapWordSize + Klass::layout_helper_offset_in_bytes()));
          __ eden_allocate(obj, obj_size, 0, t1, slow_path);
          __ initialize_object(obj, klass, obj_size, 0, t1, t2);
          __ verify_oop(obj);
          __ pop(rbx);
          __ pop(rdi);
          __ ret(0);

          __ bind(slow_path);
          __ pop(rbx);
          __ pop(rdi);
        }

        __ enter();
        OopMap* map = save_live_registers(sasm, 2);
        int call_offset = __ call_RT(obj, noreg, CAST_FROM_FN_PTR(address, new_instance), klass);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers_except_rax(sasm);
        __ verify_oop(obj);
        __ leave();
        __ ret(0);

        // rax,: new instance
      }

      break;

#ifdef TIERED
    case counter_overflow_id:
      {
        Register bci = rax;
        __ enter();
        OopMap* map = save_live_registers(sasm, 2);
        // Retrieve bci
        __ movl(bci, Address(rbp, 2*BytesPerWord));
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, counter_overflow), bci);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm);
        __ leave();
        __ ret(0);
      }
      break;
#endif // TIERED

    case new_type_array_id:
    case new_object_array_id:
      {
        Register length   = rbx; // Incoming
        Register klass    = rdx; // Incoming
        Register obj      = rax; // Result

        if (id == new_type_array_id) {
          __ set_info("new_type_array", dont_gc_arguments);
        } else {
          __ set_info("new_object_array", dont_gc_arguments);
        }

#ifdef ASSERT
        // assert object type is really an array of the proper kind
        {
          Label ok;
          Register t0 = obj;
          __ movl(t0, Address(klass, Klass::layout_helper_offset_in_bytes() + sizeof(oopDesc)));
          __ sarl(t0, Klass::_lh_array_tag_shift);
          int tag = ((id == new_type_array_id)
                     ? Klass::_lh_array_tag_type_value
                     : Klass::_lh_array_tag_obj_value);
          __ cmpl(t0, tag);
          __ jcc(Assembler::equal, ok);
          __ stop("assert(is an array klass)");
          __ should_not_reach_here();
          __ bind(ok);
        }
#endif // ASSERT

        if (UseTLAB && FastTLABRefill) {
          Register arr_size = rsi;
          Register t1       = rcx;  // must be rcx for use as shift count
          Register t2       = rdi;
          Label slow_path;
          assert_different_registers(length, klass, obj, arr_size, t1, t2);

          // check that array length is small enough for fast path.
          __ cmpl(length, C1_MacroAssembler::max_array_allocation_length);
          __ jcc(Assembler::above, slow_path);

          // if we got here then the TLAB allocation failed, so try
          // refilling the TLAB or allocating directly from eden.
          Label retry_tlab, try_eden;
          __ tlab_refill(retry_tlab, try_eden, slow_path); // preserves rbx, & rdx

          __ bind(retry_tlab);

          // get the allocation size: round_up(hdr + length << (layout_helper & 0x1F))
          // since size is postive movl does right thing on 64bit
          __ movl(t1, Address(klass, klassOopDesc::header_size() * HeapWordSize + Klass::layout_helper_offset_in_bytes()));
          // since size is postive movl does right thing on 64bit
          __ movl(arr_size, length);
          assert(t1 == rcx, "fixed register usage");
          __ shlptr(arr_size /* by t1=rcx, mod 32 */);
          __ shrptr(t1, Klass::_lh_header_size_shift);
          __ andptr(t1, Klass::_lh_header_size_mask);
          __ addptr(arr_size, t1);
          __ addptr(arr_size, MinObjAlignmentInBytesMask); // align up
          __ andptr(arr_size, ~MinObjAlignmentInBytesMask);

          __ tlab_allocate(obj, arr_size, 0, t1, t2, slow_path);  // preserves arr_size

          __ initialize_header(obj, klass, length, t1, t2);
          __ movb(t1, Address(klass, klassOopDesc::header_size() * HeapWordSize + Klass::layout_helper_offset_in_bytes() + (Klass::_lh_header_size_shift / BitsPerByte)));
          assert(Klass::_lh_header_size_shift % BitsPerByte == 0, "bytewise");
          assert(Klass::_lh_header_size_mask <= 0xFF, "bytewise");
          __ andptr(t1, Klass::_lh_header_size_mask);
          __ subptr(arr_size, t1);  // body length
          __ addptr(t1, obj);       // body start
          __ initialize_body(t1, arr_size, 0, t2);
          __ verify_oop(obj);
          __ ret(0);

          __ bind(try_eden);
          // get the allocation size: round_up(hdr + length << (layout_helper & 0x1F))
          // since size is postive movl does right thing on 64bit
          __ movl(t1, Address(klass, klassOopDesc::header_size() * HeapWordSize + Klass::layout_helper_offset_in_bytes()));
          // since size is postive movl does right thing on 64bit
          __ movl(arr_size, length);
          assert(t1 == rcx, "fixed register usage");
          __ shlptr(arr_size /* by t1=rcx, mod 32 */);
          __ shrptr(t1, Klass::_lh_header_size_shift);
          __ andptr(t1, Klass::_lh_header_size_mask);
          __ addptr(arr_size, t1);
          __ addptr(arr_size, MinObjAlignmentInBytesMask); // align up
          __ andptr(arr_size, ~MinObjAlignmentInBytesMask);

          __ eden_allocate(obj, arr_size, 0, t1, slow_path);  // preserves arr_size

          __ initialize_header(obj, klass, length, t1, t2);
          __ movb(t1, Address(klass, klassOopDesc::header_size() * HeapWordSize + Klass::layout_helper_offset_in_bytes() + (Klass::_lh_header_size_shift / BitsPerByte)));
          assert(Klass::_lh_header_size_shift % BitsPerByte == 0, "bytewise");
          assert(Klass::_lh_header_size_mask <= 0xFF, "bytewise");
          __ andptr(t1, Klass::_lh_header_size_mask);
          __ subptr(arr_size, t1);  // body length
          __ addptr(t1, obj);       // body start
          __ initialize_body(t1, arr_size, 0, t2);
          __ verify_oop(obj);
          __ ret(0);

          __ bind(slow_path);
        }

        __ enter();
        OopMap* map = save_live_registers(sasm, 3);
        int call_offset;
        if (id == new_type_array_id) {
          call_offset = __ call_RT(obj, noreg, CAST_FROM_FN_PTR(address, new_type_array), klass, length);
        } else {
          call_offset = __ call_RT(obj, noreg, CAST_FROM_FN_PTR(address, new_object_array), klass, length);
        }

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers_except_rax(sasm);

        __ verify_oop(obj);
        __ leave();
        __ ret(0);

        // rax,: new array
      }
      break;

    case new_multi_array_id:
      { StubFrame f(sasm, "new_multi_array", dont_gc_arguments);
        // rax,: klass
        // rbx,: rank
        // rcx: address of 1st dimension
        OopMap* map = save_live_registers(sasm, 4);
        int call_offset = __ call_RT(rax, noreg, CAST_FROM_FN_PTR(address, new_multi_array), rax, rbx, rcx);

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers_except_rax(sasm);

        // rax,: new multi array
        __ verify_oop(rax);
      }
      break;

    case register_finalizer_id:
      {
        __ set_info("register_finalizer", dont_gc_arguments);

        // This is called via call_runtime so the arguments
        // will be place in C abi locations

#ifdef _LP64
        __ verify_oop(c_rarg0);
        __ mov(rax, c_rarg0);
#else
        // The object is passed on the stack and we haven't pushed a
        // frame yet so it's one work away from top of stack.
        __ movptr(rax, Address(rsp, 1 * BytesPerWord));
        __ verify_oop(rax);
#endif // _LP64

        // load the klass and check the has finalizer flag
        Label register_finalizer;
        Register t = rsi;
        __ movptr(t, Address(rax, oopDesc::klass_offset_in_bytes()));
        __ movl(t, Address(t, Klass::access_flags_offset_in_bytes() + sizeof(oopDesc)));
        __ testl(t, JVM_ACC_HAS_FINALIZER);
        __ jcc(Assembler::notZero, register_finalizer);
        __ ret(0);

        __ bind(register_finalizer);
        __ enter();
        OopMap* oop_map = save_live_registers(sasm, 2 /*num_rt_args */);
        int call_offset = __ call_RT(noreg, noreg,
                                     CAST_FROM_FN_PTR(address, SharedRuntime::register_finalizer), rax);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);

        // Now restore all the live registers
        restore_live_registers(sasm);

        __ leave();
        __ ret(0);
      }
      break;

    case throw_range_check_failed_id:
      { StubFrame f(sasm, "range_check_failed", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_range_check_exception), true);
      }
      break;

    case throw_index_exception_id:
      { StubFrame f(sasm, "index_range_check_failed", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_index_exception), true);
      }
      break;

    case throw_div0_exception_id:
      { StubFrame f(sasm, "throw_div0_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_div0_exception), false);
      }
      break;

    case throw_null_pointer_exception_id:
      { StubFrame f(sasm, "throw_null_pointer_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_null_pointer_exception), false);
      }
      break;

    case handle_exception_nofpu_id:
      save_fpu_registers = false;
      // fall through
    case handle_exception_id:
      { StubFrame f(sasm, "handle_exception", dont_gc_arguments);
        oop_maps = new OopMapSet();
        OopMap* oop_map = save_live_registers(sasm, 1, save_fpu_registers);
        generate_handle_exception(sasm, oop_maps, oop_map, save_fpu_registers);
      }
      break;

    case unwind_exception_id:
      { __ set_info("unwind_exception", dont_gc_arguments);
        // note: no stubframe since we are about to leave the current
        //       activation and we are calling a leaf VM function only.
        generate_unwind_exception(sasm);
      }
      break;

    case throw_array_store_exception_id:
      { StubFrame f(sasm, "throw_array_store_exception", dont_gc_arguments);
        // tos + 0: link
        //     + 1: return address
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_array_store_exception), false);
      }
      break;

    case throw_class_cast_exception_id:
      { StubFrame f(sasm, "throw_class_cast_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_class_cast_exception), true);
      }
      break;

    case throw_incompatible_class_change_error_id:
      { StubFrame f(sasm, "throw_incompatible_class_cast_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_incompatible_class_change_error), false);
      }
      break;

    case slow_subtype_check_id:
      {
        // Typical calling sequence:
        // __ push(klass_RInfo);  // object klass or other subclass
        // __ push(sup_k_RInfo);  // array element klass or other superclass
        // __ call(slow_subtype_check);
        // Note that the subclass is pushed first, and is therefore deepest.
        // Previous versions of this code reversed the names 'sub' and 'super'.
        // This was operationally harmless but made the code unreadable.
        enum layout {
          rax_off, SLOT2(raxH_off)
          rcx_off, SLOT2(rcxH_off)
          rsi_off, SLOT2(rsiH_off)
          rdi_off, SLOT2(rdiH_off)
          // saved_rbp_off, SLOT2(saved_rbpH_off)
          return_off, SLOT2(returnH_off)
          sup_k_off, SLOT2(sup_kH_off)
          klass_off, SLOT2(superH_off)
          framesize,
          result_off = klass_off  // deepest argument is also the return value
        };

        __ set_info("slow_subtype_check", dont_gc_arguments);
        __ push(rdi);
        __ push(rsi);
        __ push(rcx);
        __ push(rax);

        // This is called by pushing args and not with C abi
        __ movptr(rsi, Address(rsp, (klass_off) * VMRegImpl::stack_slot_size)); // subclass
        __ movptr(rax, Address(rsp, (sup_k_off) * VMRegImpl::stack_slot_size)); // superclass

        Label miss;
        __ check_klass_subtype_slow_path(rsi, rax, rcx, rdi, NULL, &miss);

        // fallthrough on success:
        __ movptr(Address(rsp, (result_off) * VMRegImpl::stack_slot_size), 1); // result
        __ pop(rax);
        __ pop(rcx);
        __ pop(rsi);
        __ pop(rdi);
        __ ret(0);

        __ bind(miss);
        __ movptr(Address(rsp, (result_off) * VMRegImpl::stack_slot_size), NULL_WORD); // result
        __ pop(rax);
        __ pop(rcx);
        __ pop(rsi);
        __ pop(rdi);
        __ ret(0);
      }
      break;

    case monitorenter_nofpu_id:
      save_fpu_registers = false;
      // fall through
    case monitorenter_id:
      {
        StubFrame f(sasm, "monitorenter", dont_gc_arguments);
        OopMap* map = save_live_registers(sasm, 3, save_fpu_registers);

        // Called with store_parameter and not C abi

        f.load_argument(1, rax); // rax,: object
        f.load_argument(0, rbx); // rbx,: lock address

        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, monitorenter), rax, rbx);

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
        StubFrame f(sasm, "monitorexit", dont_gc_arguments);
        OopMap* map = save_live_registers(sasm, 2, save_fpu_registers);

        // Called with store_parameter and not C abi

        f.load_argument(0, rax); // rax,: lock address

        // note: really a leaf routine but must setup last java sp
        //       => use call_RT for now (speed can be improved by
        //       doing last java sp setup manually)
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, monitorexit), rax);

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm, save_fpu_registers);

      }
      break;

    case access_field_patching_id:
      { StubFrame f(sasm, "access_field_patching", dont_gc_arguments);
        // we should set up register map
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, access_field_patching));
      }
      break;

    case load_klass_patching_id:
      { StubFrame f(sasm, "load_klass_patching", dont_gc_arguments);
        // we should set up register map
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_klass_patching));
      }
      break;

    case jvmti_exception_throw_id:
      { // rax,: exception oop
        StubFrame f(sasm, "jvmti_exception_throw", dont_gc_arguments);
        // Preserve all registers across this potentially blocking call
        const int num_rt_args = 2;  // thread, exception oop
        OopMap* map = save_live_registers(sasm, num_rt_args);
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, Runtime1::post_jvmti_exception_throw), rax);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm);
      }
      break;

    case dtrace_object_alloc_id:
      { // rax,: object
        StubFrame f(sasm, "dtrace_object_alloc", dont_gc_arguments);
        // we can't gc here so skip the oopmap but make sure that all
        // the live registers get saved.
        save_live_registers(sasm, 1);

        __ NOT_LP64(push(rax)) LP64_ONLY(mov(c_rarg0, rax));
        __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_object_alloc)));
        NOT_LP64(__ pop(rax));

        restore_live_registers(sasm);
      }
      break;

    case fpu2long_stub_id:
      {
        // rax, and rdx are destroyed, but should be free since the result is returned there
        // preserve rsi,ecx
        __ push(rsi);
        __ push(rcx);
        LP64_ONLY(__ push(rdx);)

        // check for NaN
        Label return0, do_return, return_min_jlong, do_convert;

        Address value_high_word(rsp, wordSize + 4);
        Address value_low_word(rsp, wordSize);
        Address result_high_word(rsp, 3*wordSize + 4);
        Address result_low_word(rsp, 3*wordSize);

        __ subptr(rsp, 32);                    // more than enough on 32bit
        __ fst_d(value_low_word);
        __ movl(rax, value_high_word);
        __ andl(rax, 0x7ff00000);
        __ cmpl(rax, 0x7ff00000);
        __ jcc(Assembler::notEqual, do_convert);
        __ movl(rax, value_high_word);
        __ andl(rax, 0xfffff);
        __ orl(rax, value_low_word);
        __ jcc(Assembler::notZero, return0);

        __ bind(do_convert);
        __ fnstcw(Address(rsp, 0));
        __ movzwl(rax, Address(rsp, 0));
        __ orl(rax, 0xc00);
        __ movw(Address(rsp, 2), rax);
        __ fldcw(Address(rsp, 2));
        __ fwait();
        __ fistp_d(result_low_word);
        __ fldcw(Address(rsp, 0));
        __ fwait();
        // This gets the entire long in rax on 64bit
        __ movptr(rax, result_low_word);
        // testing of high bits
        __ movl(rdx, result_high_word);
        __ mov(rcx, rax);
        // What the heck is the point of the next instruction???
        __ xorl(rcx, 0x0);
        __ movl(rsi, 0x80000000);
        __ xorl(rsi, rdx);
        __ orl(rcx, rsi);
        __ jcc(Assembler::notEqual, do_return);
        __ fldz();
        __ fcomp_d(value_low_word);
        __ fnstsw_ax();
#ifdef _LP64
        __ testl(rax, 0x4100);  // ZF & CF == 0
        __ jcc(Assembler::equal, return_min_jlong);
#else
        __ sahf();
        __ jcc(Assembler::above, return_min_jlong);
#endif // _LP64
        // return max_jlong
#ifndef _LP64
        __ movl(rdx, 0x7fffffff);
        __ movl(rax, 0xffffffff);
#else
        __ mov64(rax, CONST64(0x7fffffffffffffff));
#endif // _LP64
        __ jmp(do_return);

        __ bind(return_min_jlong);
#ifndef _LP64
        __ movl(rdx, 0x80000000);
        __ xorl(rax, rax);
#else
        __ mov64(rax, CONST64(0x8000000000000000));
#endif // _LP64
        __ jmp(do_return);

        __ bind(return0);
        __ fpop();
#ifndef _LP64
        __ xorptr(rdx,rdx);
        __ xorptr(rax,rax);
#else
        __ xorptr(rax, rax);
#endif // _LP64

        __ bind(do_return);
        __ addptr(rsp, 32);
        LP64_ONLY(__ pop(rdx);)
        __ pop(rcx);
        __ pop(rsi);
        __ ret(0);
      }
      break;

#ifndef SERIALGC
    case g1_pre_barrier_slow_id:
      {
        StubFrame f(sasm, "g1_pre_barrier", dont_gc_arguments);
        // arg0 : previous value of memory

        BarrierSet* bs = Universe::heap()->barrier_set();
        if (bs->kind() != BarrierSet::G1SATBCTLogging) {
          __ movptr(rax, (int)id);
          __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, unimplemented_entry), rax);
          __ should_not_reach_here();
          break;
        }

        __ push(rax);
        __ push(rdx);

        const Register pre_val = rax;
        const Register thread = NOT_LP64(rax) LP64_ONLY(r15_thread);
        const Register tmp = rdx;

        NOT_LP64(__ get_thread(thread);)

        Address in_progress(thread, in_bytes(JavaThread::satb_mark_queue_offset() +
                                             PtrQueue::byte_offset_of_active()));

        Address queue_index(thread, in_bytes(JavaThread::satb_mark_queue_offset() +
                                             PtrQueue::byte_offset_of_index()));
        Address buffer(thread, in_bytes(JavaThread::satb_mark_queue_offset() +
                                        PtrQueue::byte_offset_of_buf()));


        Label done;
        Label runtime;

        // Can we store original value in the thread's buffer?

        LP64_ONLY(__ movslq(tmp, queue_index);)
#ifdef _LP64
        __ cmpq(tmp, 0);
#else
        __ cmpl(queue_index, 0);
#endif
        __ jcc(Assembler::equal, runtime);
#ifdef _LP64
        __ subq(tmp, wordSize);
        __ movl(queue_index, tmp);
        __ addq(tmp, buffer);
#else
        __ subl(queue_index, wordSize);
        __ movl(tmp, buffer);
        __ addl(tmp, queue_index);
#endif

        // prev_val (rax)
        f.load_argument(0, pre_val);
        __ movptr(Address(tmp, 0), pre_val);
        __ jmp(done);

        __ bind(runtime);
        // load the pre-value
        __ push(rcx);
        f.load_argument(0, rcx);
        __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_pre), rcx, thread);
        __ pop(rcx);

        __ bind(done);
        __ pop(rdx);
        __ pop(rax);
      }
      break;

    case g1_post_barrier_slow_id:
      {
        StubFrame f(sasm, "g1_post_barrier", dont_gc_arguments);


        // arg0: store_address
        Address store_addr(rbp, 2*BytesPerWord);

        BarrierSet* bs = Universe::heap()->barrier_set();
        CardTableModRefBS* ct = (CardTableModRefBS*)bs;
        Label done;
        Label runtime;

        // At this point we know new_value is non-NULL and the new_value crosses regsion.
        // Must check to see if card is already dirty

        const Register thread = NOT_LP64(rax) LP64_ONLY(r15_thread);

        Address queue_index(thread, in_bytes(JavaThread::dirty_card_queue_offset() +
                                             PtrQueue::byte_offset_of_index()));
        Address buffer(thread, in_bytes(JavaThread::dirty_card_queue_offset() +
                                        PtrQueue::byte_offset_of_buf()));

        __ push(rax);
        __ push(rdx);

        NOT_LP64(__ get_thread(thread);)
        ExternalAddress cardtable((address)ct->byte_map_base);
        assert(sizeof(*ct->byte_map_base) == sizeof(jbyte), "adjust this code");

        const Register card_addr = rdx;
#ifdef _LP64
        const Register tmp = rscratch1;
        f.load_argument(0, card_addr);
        __ shrq(card_addr, CardTableModRefBS::card_shift);
        __ lea(tmp, cardtable);
        // get the address of the card
        __ addq(card_addr, tmp);
#else
        const Register card_index = rdx;
        f.load_argument(0, card_index);
        __ shrl(card_index, CardTableModRefBS::card_shift);

        Address index(noreg, card_index, Address::times_1);
        __ leal(card_addr, __ as_Address(ArrayAddress(cardtable, index)));
#endif

        __ cmpb(Address(card_addr, 0), 0);
        __ jcc(Assembler::equal, done);

        // storing region crossing non-NULL, card is clean.
        // dirty card and log.

        __ movb(Address(card_addr, 0), 0);

        __ cmpl(queue_index, 0);
        __ jcc(Assembler::equal, runtime);
        __ subl(queue_index, wordSize);

        const Register buffer_addr = rbx;
        __ push(rbx);

        __ movptr(buffer_addr, buffer);

#ifdef _LP64
        __ movslq(rscratch1, queue_index);
        __ addptr(buffer_addr, rscratch1);
#else
        __ addptr(buffer_addr, queue_index);
#endif
        __ movptr(Address(buffer_addr, 0), card_addr);

        __ pop(rbx);
        __ jmp(done);

        __ bind(runtime);
        NOT_LP64(__ push(rcx);)
        __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_post), card_addr, thread);
        NOT_LP64(__ pop(rcx);)

        __ bind(done);
        __ pop(rdx);
        __ pop(rax);

      }
      break;
#endif // !SERIALGC

    default:
      { StubFrame f(sasm, "unimplemented entry", dont_gc_arguments);
        __ movptr(rax, (int)id);
        __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, unimplemented_entry), rax);
        __ should_not_reach_here();
      }
      break;
  }
  return oop_maps;
}

#undef __
