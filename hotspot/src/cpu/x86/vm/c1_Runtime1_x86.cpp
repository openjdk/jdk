/*
 * Copyright 1999-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
  const Register thread = rdi; // is callee-saved register (Visual C++ calling conventions)
  assert(!(oop_result1->is_valid() || oop_result2->is_valid()) || oop_result1 != oop_result2, "registers must be different");
  assert(oop_result1 != thread && oop_result2 != thread, "registers must be different");
  assert(args_size >= 0, "illegal args_size");

  set_num_rt_args(1 + args_size);

  // push java thread (becomes first argument of C function)
  get_thread(thread);
  pushl(thread);

  set_last_Java_frame(thread, noreg, rbp, NULL);
  // do the call
  call(RuntimeAddress(entry));
  int call_offset = offset();
  // verify callee-saved register
#ifdef ASSERT
  guarantee(thread != rax, "change this code");
  pushl(rax);
  { Label L;
    get_thread(rax);
    cmpl(thread, rax);
    jcc(Assembler::equal, L);
    int3();
    stop("StubAssembler::call_RT: rdi not callee saved?");
    bind(L);
  }
  popl(rax);
#endif
  reset_last_Java_frame(thread, true, false);

  // discard thread and arguments
  addl(rsp, (1 + args_size)*BytesPerWord);

  // check for pending exceptions
  { Label L;
    cmpl(Address(thread, Thread::pending_exception_offset()), NULL_WORD);
    jcc(Assembler::equal, L);
    // exception pending => remove activation and forward to exception handler
    movl(rax, Address(thread, Thread::pending_exception_offset()));
    // make sure that the vm_results are cleared
    if (oop_result1->is_valid()) {
      movl(Address(thread, JavaThread::vm_result_offset()), NULL_WORD);
    }
    if (oop_result2->is_valid()) {
      movl(Address(thread, JavaThread::vm_result_2_offset()), NULL_WORD);
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
    movl(oop_result1, Address(thread, JavaThread::vm_result_offset()));
    movl(Address(thread, JavaThread::vm_result_offset()), NULL_WORD);
    verify_oop(oop_result1);
  }
  if (oop_result2->is_valid()) {
    movl(oop_result2, Address(thread, JavaThread::vm_result_2_offset()));
    movl(Address(thread, JavaThread::vm_result_2_offset()), NULL_WORD);
    verify_oop(oop_result2);
  }
  return call_offset;
}


int StubAssembler::call_RT(Register oop_result1, Register oop_result2, address entry, Register arg1) {
  pushl(arg1);
  return call_RT(oop_result1, oop_result2, entry, 1);
}


int StubAssembler::call_RT(Register oop_result1, Register oop_result2, address entry, Register arg1, Register arg2) {
  pushl(arg2);
  pushl(arg1);
  return call_RT(oop_result1, oop_result2, entry, 2);
}


int StubAssembler::call_RT(Register oop_result1, Register oop_result2, address entry, Register arg1, Register arg2, Register arg3) {
  pushl(arg3);
  pushl(arg2);
  pushl(arg1);
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

  __ movl(reg, Address(rbp, (offset_in_words + 2) * BytesPerWord));
}


StubFrame::~StubFrame() {
  __ leave();
  __ ret(0);
}

#undef __


// Implementation of Runtime1

#define __ sasm->

const int float_regs_as_doubles_size_in_words = 16;
const int xmm_regs_as_doubles_size_in_words = 16;

// Stack layout for saving/restoring  all the registers needed during a runtime
// call (this includes deoptimization)
// Note: note that users of this frame may well have arguments to some runtime
// while these values are on the stack. These positions neglect those arguments
// but the code in save_live_registers will take the argument count into
// account.
//
enum reg_save_layout {
  dummy1,
  dummy2,
  // Two temps to be used as needed by users of save/restore callee registers
  temp_2_off,
  temp_1_off,
  xmm_regs_as_doubles_off,
  float_regs_as_doubles_off = xmm_regs_as_doubles_off + xmm_regs_as_doubles_size_in_words,
  fpu_state_off = float_regs_as_doubles_off + float_regs_as_doubles_size_in_words,
  fpu_state_end_off = fpu_state_off + FPUStateSizeInWords,
  marker = fpu_state_end_off,
  extra_space_offset,
  rdi_off = extra_space_offset,
  rsi_off,
  rbp_off,
  rsp_off,
  rbx_off,
  rdx_off,
  rcx_off,
  rax_off,
  saved_rbp_off,
  return_off,
  reg_save_frame_size,  // As noted: neglects any parameters to runtime

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
  int frame_size = reg_save_frame_size + num_rt_args; // args + thread
  sasm->set_frame_size(frame_size);

  // record saved value locations in an OopMap
  // locations are offsets from sp after runtime call; num_rt_args is number of arguments in call, including thread
  OopMap* map = new OopMap(frame_size, 0);
  map->set_callee_saved(VMRegImpl::stack2reg(rax_off + num_rt_args), rax->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(rcx_off + num_rt_args), rcx->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(rdx_off + num_rt_args), rdx->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(rbx_off + num_rt_args), rbx->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(rsi_off + num_rt_args), rsi->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(rdi_off + num_rt_args), rdi->as_VMReg());

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

  int frame_size = reg_save_frame_size + num_rt_args; // args + thread
  // frame_size = round_to(frame_size, 4);
  sasm->set_frame_size(frame_size);

  __ pushad();         // integer registers

  // assert(float_regs_as_doubles_off % 2 == 0, "misaligned offset");
  // assert(xmm_regs_as_doubles_off % 2 == 0, "misaligned offset");

  __ subl(rsp, extra_space_offset * wordSize);

#ifdef ASSERT
  __ movl(Address(rsp, marker * wordSize), 0xfeedbeef);
#endif

  if (save_fpu_registers) {
    if (UseSSE < 2) {
      // save FPU stack
      __ fnsave(Address(rsp, fpu_state_off * wordSize));
      __ fwait();

#ifdef ASSERT
      Label ok;
      __ cmpw(Address(rsp, fpu_state_off * wordSize), StubRoutines::fpu_cntrl_wrd_std());
      __ jccb(Assembler::equal, ok);
      __ stop("corrupted control word detected");
      __ bind(ok);
#endif

      // Reset the control word to guard against exceptions being unmasked
      // since fstp_d can cause FPU stack underflow exceptions.  Write it
      // into the on stack copy and then reload that to make sure that the
      // current and future values are correct.
      __ movw(Address(rsp, fpu_state_off * wordSize), StubRoutines::fpu_cntrl_wrd_std());
      __ frstor(Address(rsp, fpu_state_off * wordSize));

      // Save the FPU registers in de-opt-able form
      __ fstp_d(Address(rsp, float_regs_as_doubles_off * BytesPerWord +  0));
      __ fstp_d(Address(rsp, float_regs_as_doubles_off * BytesPerWord +  8));
      __ fstp_d(Address(rsp, float_regs_as_doubles_off * BytesPerWord + 16));
      __ fstp_d(Address(rsp, float_regs_as_doubles_off * BytesPerWord + 24));
      __ fstp_d(Address(rsp, float_regs_as_doubles_off * BytesPerWord + 32));
      __ fstp_d(Address(rsp, float_regs_as_doubles_off * BytesPerWord + 40));
      __ fstp_d(Address(rsp, float_regs_as_doubles_off * BytesPerWord + 48));
      __ fstp_d(Address(rsp, float_regs_as_doubles_off * BytesPerWord + 56));
    }

    if (UseSSE >= 2) {
      // save XMM registers
      // XMM registers can contain float or double values, but this is not known here,
      // so always save them as doubles.
      // note that float values are _not_ converted automatically, so for float values
      // the second word contains only garbage data.
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * wordSize +  0), xmm0);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * wordSize +  8), xmm1);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * wordSize + 16), xmm2);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * wordSize + 24), xmm3);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * wordSize + 32), xmm4);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * wordSize + 40), xmm5);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * wordSize + 48), xmm6);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * wordSize + 56), xmm7);
    } else if (UseSSE == 1) {
      // save XMM registers as float because double not supported without SSE2
      __ movflt(Address(rsp, xmm_regs_as_doubles_off * wordSize +  0), xmm0);
      __ movflt(Address(rsp, xmm_regs_as_doubles_off * wordSize +  8), xmm1);
      __ movflt(Address(rsp, xmm_regs_as_doubles_off * wordSize + 16), xmm2);
      __ movflt(Address(rsp, xmm_regs_as_doubles_off * wordSize + 24), xmm3);
      __ movflt(Address(rsp, xmm_regs_as_doubles_off * wordSize + 32), xmm4);
      __ movflt(Address(rsp, xmm_regs_as_doubles_off * wordSize + 40), xmm5);
      __ movflt(Address(rsp, xmm_regs_as_doubles_off * wordSize + 48), xmm6);
      __ movflt(Address(rsp, xmm_regs_as_doubles_off * wordSize + 56), xmm7);
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
      __ movdbl(xmm0, Address(rsp, xmm_regs_as_doubles_off * wordSize +  0));
      __ movdbl(xmm1, Address(rsp, xmm_regs_as_doubles_off * wordSize +  8));
      __ movdbl(xmm2, Address(rsp, xmm_regs_as_doubles_off * wordSize + 16));
      __ movdbl(xmm3, Address(rsp, xmm_regs_as_doubles_off * wordSize + 24));
      __ movdbl(xmm4, Address(rsp, xmm_regs_as_doubles_off * wordSize + 32));
      __ movdbl(xmm5, Address(rsp, xmm_regs_as_doubles_off * wordSize + 40));
      __ movdbl(xmm6, Address(rsp, xmm_regs_as_doubles_off * wordSize + 48));
      __ movdbl(xmm7, Address(rsp, xmm_regs_as_doubles_off * wordSize + 56));
    } else if (UseSSE == 1) {
      // restore XMM registers
      __ movflt(xmm0, Address(rsp, xmm_regs_as_doubles_off * wordSize +  0));
      __ movflt(xmm1, Address(rsp, xmm_regs_as_doubles_off * wordSize +  8));
      __ movflt(xmm2, Address(rsp, xmm_regs_as_doubles_off * wordSize + 16));
      __ movflt(xmm3, Address(rsp, xmm_regs_as_doubles_off * wordSize + 24));
      __ movflt(xmm4, Address(rsp, xmm_regs_as_doubles_off * wordSize + 32));
      __ movflt(xmm5, Address(rsp, xmm_regs_as_doubles_off * wordSize + 40));
      __ movflt(xmm6, Address(rsp, xmm_regs_as_doubles_off * wordSize + 48));
      __ movflt(xmm7, Address(rsp, xmm_regs_as_doubles_off * wordSize + 56));
    }

    if (UseSSE < 2) {
      __ frstor(Address(rsp, fpu_state_off * wordSize));
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
    __ cmpl(Address(rsp, marker * wordSize), 0xfeedbeef);
    __ jcc(Assembler::equal, ok);
    __ stop("bad offsets in frame");
    __ bind(ok);
  }
#endif

  __ addl(rsp, extra_space_offset * wordSize);
}


static void restore_live_registers(StubAssembler* sasm, bool restore_fpu_registers = true) {
  __ block_comment("restore_live_registers");

  restore_fpu(sasm, restore_fpu_registers);
  __ popad();
}


static void restore_live_registers_except_rax(StubAssembler* sasm, bool restore_fpu_registers = true) {
  __ block_comment("restore_live_registers_except_rax");

  restore_fpu(sasm, restore_fpu_registers);

  __ popl(rdi);
  __ popl(rsi);
  __ popl(rbp);
  __ popl(rbx); // skip this value
  __ popl(rbx);
  __ popl(rdx);
  __ popl(rcx);
  __ addl(rsp, 4);
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
    __ movl(temp_reg, Address(rbp, 2*BytesPerWord));
    __ pushl(temp_reg);
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
  const Register thread = rdi;

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
  __ get_thread(thread);

#ifdef ASSERT
  // check that fields in JavaThread for exception oop and issuing pc are
  // empty before writing to them
  Label oop_empty;
  __ cmpl(Address(thread, JavaThread::exception_oop_offset()), 0);
  __ jcc(Assembler::equal, oop_empty);
  __ stop("exception oop already set");
  __ bind(oop_empty);

  Label pc_empty;
  __ cmpl(Address(thread, JavaThread::exception_pc_offset()), 0);
  __ jcc(Assembler::equal, pc_empty);
  __ stop("exception pc already set");
  __ bind(pc_empty);
#endif

  // save exception oop and issuing pc into JavaThread
  // (exception handler will load it from here)
  __ movl(Address(thread, JavaThread::exception_oop_offset()), exception_oop);
  __ movl(Address(thread, JavaThread::exception_pc_offset()), exception_pc);

  // save real return address (pc that called this stub)
  __ movl(real_return_addr, Address(rbp, 1*BytesPerWord));
  __ movl(Address(rsp, temp_1_off * BytesPerWord), real_return_addr);

  // patch throwing pc into return address (has bci & oop map)
  __ movl(Address(rbp, 1*BytesPerWord), exception_pc);

  // compute the exception handler.
  // the exception oop and the throwing pc are read from the fields in JavaThread
  int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, exception_handler_for_pc));
  oop_maps->add_gc_map(call_offset, oop_map);

  // rax,: handler address or NULL if no handler exists
  //      will be the deopt blob if nmethod was deoptimized while we looked up
  //      handler regardless of whether handler existed in the nmethod.

  // only rax, is valid at this time, all other registers have been destroyed by the runtime call
  __ invalidate_registers(false, true, true, true, true, true);

  // Do we have an exception handler in the nmethod?
  Label no_handler;
  Label done;
  __ testl(rax, rax);
  __ jcc(Assembler::zero, no_handler);

  // exception handler found
  // patch the return address -> the stub will directly return to the exception handler
  __ movl(Address(rbp, 1*BytesPerWord), rax);

  // restore registers
  restore_live_registers(sasm, save_fpu_registers);

  // return to exception handler
  __ leave();
  __ ret(0);

  __ bind(no_handler);
  // no exception handler found in this method, so the exception is
  // forwarded to the caller (using the unwind code of the nmethod)
  // there is no need to restore the registers

  // restore the real return address that was saved before the RT-call
  __ movl(real_return_addr, Address(rsp, temp_1_off * BytesPerWord));
  __ movl(Address(rbp, 1*BytesPerWord), real_return_addr);

  // load address of JavaThread object for thread-local data
  __ get_thread(thread);
  // restore exception oop into rax, (convention for unwind code)
  __ movl(exception_oop, Address(thread, JavaThread::exception_oop_offset()));

  // clear exception fields in JavaThread because they are no longer needed
  // (fields must be cleared because they are processed by GC otherwise)
  __ movl(Address(thread, JavaThread::exception_oop_offset()), NULL_WORD);
  __ movl(Address(thread, JavaThread::exception_pc_offset()), NULL_WORD);

  // pop the stub frame off
  __ leave();

  generate_unwind_exception(sasm);
  __ stop("should not reach here");
}


void Runtime1::generate_unwind_exception(StubAssembler *sasm) {
  // incoming parameters
  const Register exception_oop = rax;
  // other registers used in this stub
  const Register exception_pc = rdx;
  const Register handler_addr = rbx;
  const Register thread = rdi;

  // verify that only rax, is valid at this time
  __ invalidate_registers(false, true, true, true, true, true);

#ifdef ASSERT
  // check that fields in JavaThread for exception oop and issuing pc are empty
  __ get_thread(thread);
  Label oop_empty;
  __ cmpl(Address(thread, JavaThread::exception_oop_offset()), 0);
  __ jcc(Assembler::equal, oop_empty);
  __ stop("exception oop must be empty");
  __ bind(oop_empty);

  Label pc_empty;
  __ cmpl(Address(thread, JavaThread::exception_pc_offset()), 0);
  __ jcc(Assembler::equal, pc_empty);
  __ stop("exception pc must be empty");
  __ bind(pc_empty);
#endif

  // clear the FPU stack in case any FPU results are left behind
  __ empty_FPU_stack();

  // leave activation of nmethod
  __ leave();
  // store return address (is on top of stack after leave)
  __ movl(exception_pc, Address(rsp, 0));

  __ verify_oop(exception_oop);

  // save exception oop from rax, to stack before call
  __ pushl(exception_oop);

  // search the exception handler address of the caller (using the return address)
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address), exception_pc);
  // rax,: exception handler address of the caller

  // only rax, is valid at this time, all other registers have been destroyed by the call
  __ invalidate_registers(false, true, true, true, true, true);

  // move result of call into correct register
  __ movl(handler_addr, rax);

  // restore exception oop in rax, (required convention of exception handler)
  __ popl(exception_oop);

  __ verify_oop(exception_oop);

  // get throwing pc (= return address).
  // rdx has been destroyed by the call, so it must be set again
  // the pop is also necessary to simulate the effect of a ret(0)
  __ popl(exception_pc);

  // verify that that there is really a valid exception in rax,
  __ verify_not_null_oop(exception_oop);

  // continue at exception handler (return address removed)
  // note: do *not* remove arguments when unwinding the
  //       activation since the caller assumes having
  //       all arguments on the stack when entering the
  //       runtime to determine the exception handler
  //       (GC happens at call site with arguments!)
  // rax,: exception oop
  // rdx: throwing pc
  // rbx,: exception handler
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

  __ pushl(rax); // push dummy

  const Register thread = rdi; // is callee-saved register (Visual C++ calling conventions)
  // push java thread (becomes first argument of C function)
  __ get_thread(thread);
  __ pushl(thread);
  __ set_last_Java_frame(thread, noreg, rbp, NULL);
  // do the call
  __ call(RuntimeAddress(target));
  OopMapSet* oop_maps = new OopMapSet();
  oop_maps->add_gc_map(__ offset(), oop_map);
  // verify callee-saved register
#ifdef ASSERT
  guarantee(thread != rax, "change this code");
  __ pushl(rax);
  { Label L;
    __ get_thread(rax);
    __ cmpl(thread, rax);
    __ jcc(Assembler::equal, L);
    __ stop("StubAssembler::call_RT: rdi not callee saved?");
    __ bind(L);
  }
  __ popl(rax);
#endif
  __ reset_last_Java_frame(thread, true, false);
  __ popl(rcx); // discard thread arg
  __ popl(rcx); // discard dummy

  // check for pending exceptions
  { Label L;
    __ cmpl(Address(thread, Thread::pending_exception_offset()), NULL_WORD);
    __ jcc(Assembler::equal, L);
    // exception pending => remove activation and forward to exception handler

    __ testl(rax, rax);                                   // have we deoptimized?
    __ jump_cc(Assembler::equal,
               RuntimeAddress(Runtime1::entry_for(Runtime1::forward_exception_id)));

    // the deopt blob expects exceptions in the special fields of
    // JavaThread, so copy and clear pending exception.

    // load and clear pending exception
    __ movl(rax, Address(thread, Thread::pending_exception_offset()));
    __ movl(Address(thread, Thread::pending_exception_offset()), NULL_WORD);

    // check that there is really a valid exception
    __ verify_not_null_oop(rax);

    // load throwing pc: this is the return address of the stub
    __ movl(rdx, Address(rsp, return_off * BytesPerWord));

#ifdef ASSERT
    // check that fields in JavaThread for exception oop and issuing pc are empty
    Label oop_empty;
    __ cmpoop(Address(thread, JavaThread::exception_oop_offset()), 0);
    __ jcc(Assembler::equal, oop_empty);
    __ stop("exception oop must be empty");
    __ bind(oop_empty);

    Label pc_empty;
    __ cmpl(Address(thread, JavaThread::exception_pc_offset()), 0);
    __ jcc(Assembler::equal, pc_empty);
    __ stop("exception pc must be empty");
    __ bind(pc_empty);
#endif

    // store exception oop and throwing pc to JavaThread
    __ movl(Address(thread, JavaThread::exception_oop_offset()), rax);
    __ movl(Address(thread, JavaThread::exception_pc_offset()), rdx);

    restore_live_registers(sasm);

    __ leave();
    __ addl(rsp, 4);  // remove return address from stack

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

  __ testl(rax, rax);                                   // have we deoptimized?
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

        const Register thread = rdi;
        const Register exception_oop = rax;
        const Register exception_pc = rdx;

        // load pending exception oop into rax,
        __ movl(exception_oop, Address(thread, Thread::pending_exception_offset()));
        // clear pending exception
        __ movl(Address(thread, Thread::pending_exception_offset()), NULL_WORD);

        // load issuing PC (the return address for this stub) into rdx
        __ movl(exception_pc, Address(rbp, 1*BytesPerWord));

        // make sure that the vm_results are cleared (may be unnecessary)
        __ movl(Address(thread, JavaThread::vm_result_offset()), NULL_WORD);
        __ movl(Address(thread, JavaThread::vm_result_2_offset()), NULL_WORD);

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

          __ pushl(rdi);
          __ pushl(rbx);

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

          // get the instance size
          __ movl(obj_size, Address(klass, klassOopDesc::header_size() * HeapWordSize + Klass::layout_helper_offset_in_bytes()));
          __ tlab_allocate(obj, obj_size, 0, t1, t2, slow_path);
          __ initialize_object(obj, klass, obj_size, 0, t1, t2);
          __ verify_oop(obj);
          __ popl(rbx);
          __ popl(rdi);
          __ ret(0);

          __ bind(try_eden);
          // get the instance size
          __ movl(obj_size, Address(klass, klassOopDesc::header_size() * HeapWordSize + Klass::layout_helper_offset_in_bytes()));
          __ eden_allocate(obj, obj_size, 0, t1, slow_path);
          __ initialize_object(obj, klass, obj_size, 0, t1, t2);
          __ verify_oop(obj);
          __ popl(rbx);
          __ popl(rdi);
          __ ret(0);

          __ bind(slow_path);
          __ popl(rbx);
          __ popl(rdi);
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
          __ movl(t1, Address(klass, klassOopDesc::header_size() * HeapWordSize + Klass::layout_helper_offset_in_bytes()));
          __ movl(arr_size, length);
          assert(t1 == rcx, "fixed register usage");
          __ shll(arr_size /* by t1=rcx, mod 32 */);
          __ shrl(t1, Klass::_lh_header_size_shift);
          __ andl(t1, Klass::_lh_header_size_mask);
          __ addl(arr_size, t1);
          __ addl(arr_size, MinObjAlignmentInBytesMask); // align up
          __ andl(arr_size, ~MinObjAlignmentInBytesMask);

          __ tlab_allocate(obj, arr_size, 0, t1, t2, slow_path);  // preserves arr_size

          __ initialize_header(obj, klass, length, t1, t2);
          __ movb(t1, Address(klass, klassOopDesc::header_size() * HeapWordSize + Klass::layout_helper_offset_in_bytes() + (Klass::_lh_header_size_shift / BitsPerByte)));
          assert(Klass::_lh_header_size_shift % BitsPerByte == 0, "bytewise");
          assert(Klass::_lh_header_size_mask <= 0xFF, "bytewise");
          __ andl(t1, Klass::_lh_header_size_mask);
          __ subl(arr_size, t1);  // body length
          __ addl(t1, obj);       // body start
          __ initialize_body(t1, arr_size, 0, t2);
          __ verify_oop(obj);
          __ ret(0);

          __ bind(try_eden);
          // get the allocation size: round_up(hdr + length << (layout_helper & 0x1F))
          __ movl(t1, Address(klass, klassOopDesc::header_size() * HeapWordSize + Klass::layout_helper_offset_in_bytes()));
          __ movl(arr_size, length);
          assert(t1 == rcx, "fixed register usage");
          __ shll(arr_size /* by t1=rcx, mod 32 */);
          __ shrl(t1, Klass::_lh_header_size_shift);
          __ andl(t1, Klass::_lh_header_size_mask);
          __ addl(arr_size, t1);
          __ addl(arr_size, MinObjAlignmentInBytesMask); // align up
          __ andl(arr_size, ~MinObjAlignmentInBytesMask);

          __ eden_allocate(obj, arr_size, 0, t1, slow_path);  // preserves arr_size

          __ initialize_header(obj, klass, length, t1, t2);
          __ movb(t1, Address(klass, klassOopDesc::header_size() * HeapWordSize + Klass::layout_helper_offset_in_bytes() + (Klass::_lh_header_size_shift / BitsPerByte)));
          assert(Klass::_lh_header_size_shift % BitsPerByte == 0, "bytewise");
          assert(Klass::_lh_header_size_mask <= 0xFF, "bytewise");
          __ andl(t1, Klass::_lh_header_size_mask);
          __ subl(arr_size, t1);  // body length
          __ addl(t1, obj);       // body start
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

        // The object is passed on the stack and we haven't pushed a
        // frame yet so it's one work away from top of stack.
        __ movl(rax, Address(rsp, 1 * BytesPerWord));
        __ verify_oop(rax);

        // load the klass and check the has finalizer flag
        Label register_finalizer;
        Register t = rsi;
        __ movl(t, Address(rax, oopDesc::klass_offset_in_bytes()));
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
        enum layout {
          rax_off,
          rcx_off,
          rsi_off,
          rdi_off,
          saved_rbp_off,
          return_off,
          sub_off,
          super_off,
          framesize
        };

        __ set_info("slow_subtype_check", dont_gc_arguments);
        __ pushl(rdi);
        __ pushl(rsi);
        __ pushl(rcx);
        __ pushl(rax);
        __ movl(rsi, Address(rsp, (super_off - 1) * BytesPerWord)); // super
        __ movl(rax, Address(rsp, (sub_off   - 1) * BytesPerWord)); // sub

        __ movl(rdi,Address(rsi,sizeof(oopDesc) + Klass::secondary_supers_offset_in_bytes()));
        __ movl(rcx,Address(rdi,arrayOopDesc::length_offset_in_bytes()));
        __ addl(rdi,arrayOopDesc::base_offset_in_bytes(T_OBJECT));

        Label miss;
        __ repne_scan();
        __ jcc(Assembler::notEqual, miss);
        __ movl(Address(rsi,sizeof(oopDesc) + Klass::secondary_super_cache_offset_in_bytes()), rax);
        __ movl(Address(rsp, (super_off   - 1) * BytesPerWord), 1); // result
        __ popl(rax);
        __ popl(rcx);
        __ popl(rsi);
        __ popl(rdi);
        __ ret(0);

        __ bind(miss);
        __ movl(Address(rsp, (super_off   - 1) * BytesPerWord), 0); // result
        __ popl(rax);
        __ popl(rcx);
        __ popl(rsi);
        __ popl(rdi);
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

        __ pushl(rax);
        __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_object_alloc)));
        __ popl(rax);

        restore_live_registers(sasm);
      }
      break;

    case fpu2long_stub_id:
      {
        // rax, and rdx are destroyed, but should be free since the result is returned there
        // preserve rsi,ecx
        __ pushl(rsi);
        __ pushl(rcx);

        // check for NaN
        Label return0, do_return, return_min_jlong, do_convert;

        Address value_high_word(rsp, 8);
        Address value_low_word(rsp, 4);
        Address result_high_word(rsp, 16);
        Address result_low_word(rsp, 12);

        __ subl(rsp, 20);
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
        __ movzxw(rax, Address(rsp, 0));
        __ orl(rax, 0xc00);
        __ movw(Address(rsp, 2), rax);
        __ fldcw(Address(rsp, 2));
        __ fwait();
        __ fistp_d(result_low_word);
        __ fldcw(Address(rsp, 0));
        __ fwait();
        __ movl(rax, result_low_word);
        __ movl(rdx, result_high_word);
        __ movl(rcx, rax);
        // What the heck is the point of the next instruction???
        __ xorl(rcx, 0x0);
        __ movl(rsi, 0x80000000);
        __ xorl(rsi, rdx);
        __ orl(rcx, rsi);
        __ jcc(Assembler::notEqual, do_return);
        __ fldz();
        __ fcomp_d(value_low_word);
        __ fnstsw_ax();
        __ sahf();
        __ jcc(Assembler::above, return_min_jlong);
        // return max_jlong
        __ movl(rdx, 0x7fffffff);
        __ movl(rax, 0xffffffff);
        __ jmp(do_return);

        __ bind(return_min_jlong);
        __ movl(rdx, 0x80000000);
        __ xorl(rax, rax);
        __ jmp(do_return);

        __ bind(return0);
        __ fpop();
        __ xorl(rdx,rdx);
        __ xorl(rax,rax);

        __ bind(do_return);
        __ addl(rsp, 20);
        __ popl(rcx);
        __ popl(rsi);
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
          __ movl(rax, (int)id);
          __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, unimplemented_entry), rax);
          __ should_not_reach_here();
          break;
        }

        __ pushl(rax);
        __ pushl(rdx);

        const Register pre_val = rax;
        const Register thread = rax;
        const Register tmp = rdx;

        __ get_thread(thread);

        Address in_progress(thread, in_bytes(JavaThread::satb_mark_queue_offset() +
                                             PtrQueue::byte_offset_of_active()));

        Address queue_index(thread, in_bytes(JavaThread::satb_mark_queue_offset() +
                                             PtrQueue::byte_offset_of_index()));
        Address buffer(thread, in_bytes(JavaThread::satb_mark_queue_offset() +
                                        PtrQueue::byte_offset_of_buf()));


        Label done;
        Label runtime;

        // Can we store original value in the thread's buffer?

        __ cmpl(queue_index, 0);
        __ jcc(Assembler::equal, runtime);
        __ subl(queue_index, wordSize);
        __ movl(tmp, buffer);
        __ addl(tmp, queue_index);
        // prev_val (rax)
        f.load_argument(0, pre_val);
        __ movl(Address(tmp, 0), pre_val);
        __ jmp(done);

        __ bind(runtime);
        // load the pre-value
        __ pushl(rcx);
        f.load_argument(0, rcx);
        __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_pre), rcx, thread);
        __ popl(rcx);

        __ bind(done);
        __ popl(rdx);
        __ popl(rax);
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

        const Register card_index = rdx;

        const Register thread = rax;
        Address queue_index(thread, in_bytes(JavaThread::dirty_card_queue_offset() +
                                             PtrQueue::byte_offset_of_index()));
        Address buffer(thread, in_bytes(JavaThread::dirty_card_queue_offset() +
                                        PtrQueue::byte_offset_of_buf()));

        __ pushl(rax);
        __ pushl(rdx);

        __ movl(card_index, store_addr);
        __ get_thread(rax);
        __ shrl(card_index, CardTableModRefBS::card_shift);
        assert(sizeof(*ct->byte_map_base) == sizeof(jbyte), "adjust this code");

        ExternalAddress cardtable((address)ct->byte_map_base);
        Address index(noreg, card_index, Address::times_1);
        const Register card_addr = rdx;
        __ leal(card_addr, __ as_Address(ArrayAddress(cardtable, index)));
        __ cmpb(Address(card_addr, 0), 0);
        __ jcc(Assembler::equal, done);

        // storing region crossing non-NULL, card is clean.
        // dirty card and log.

        __ movb(Address(card_addr, 0), 0);

        __ cmpl(queue_index, 0);
        __ jcc(Assembler::equal, runtime);
        __ subl(queue_index, wordSize);

        const Register buffer_addr = rbx;
        __ pushl(rbx);

        __ movl(buffer_addr, buffer);
        __ addl(buffer_addr, queue_index);
        __ movl(Address(buffer_addr, 0), card_addr);
        __ popl(rbx);
        __ jmp(done);

        __ bind(runtime);
        __ pushl(rcx);
        __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::g1_wb_post), card_addr, thread);
        __ popl(rcx);

        __ bind(done);
        __ popl(rdx);
        __ popl(rax);

      }
      break;
#endif // !SERIALGC

    default:
      { StubFrame f(sasm, "unimplemented entry", dont_gc_arguments);
        __ movl(rax, (int)id);
        __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, unimplemented_entry), rax);
        __ should_not_reach_here();
      }
      break;
  }
  return oop_maps;
}

#undef __
