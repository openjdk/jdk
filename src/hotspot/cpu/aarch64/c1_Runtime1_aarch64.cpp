/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2021, Red Hat Inc. All rights reserved.
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

#include "asm/assembler.hpp"
#include "c1/c1_CodeStubs.hpp"
#include "c1/c1_Defs.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "c1/c1_Runtime1.hpp"
#include "compiler/disassembler.hpp"
#include "compiler/oopMap.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/universe.hpp"
#include "nativeInst_aarch64.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "register_aarch64.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/powerOfTwo.hpp"
#include "vmreg_aarch64.inline.hpp"


// Implementation of StubAssembler

int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, int args_size) {
  // setup registers
  assert(!(oop_result1->is_valid() || metadata_result->is_valid()) || oop_result1 != metadata_result, "registers must be different");
  assert(oop_result1 != rthread && metadata_result != rthread, "registers must be different");
  assert(args_size >= 0, "illegal args_size");
  bool align_stack = false;

  mov(c_rarg0, rthread);
  set_num_rt_args(0); // Nothing on stack

  Label retaddr;
  set_last_Java_frame(sp, rfp, retaddr, rscratch1);

  // do the call
  lea(rscratch1, RuntimeAddress(entry));
  blr(rscratch1);
  bind(retaddr);
  int call_offset = offset();
  // verify callee-saved register
#ifdef ASSERT
  push(r0, sp);
  { Label L;
    get_thread(r0);
    cmp(rthread, r0);
    br(Assembler::EQ, L);
    stop("StubAssembler::call_RT: rthread not callee saved?");
    bind(L);
  }
  pop(r0, sp);
#endif
  reset_last_Java_frame(true);

  // check for pending exceptions
  { Label L;
    // check for pending exceptions (java_thread is set upon return)
    ldr(rscratch1, Address(rthread, in_bytes(Thread::pending_exception_offset())));
    cbz(rscratch1, L);
    // exception pending => remove activation and forward to exception handler
    // make sure that the vm_results are cleared
    if (oop_result1->is_valid()) {
      str(zr, Address(rthread, JavaThread::vm_result_oop_offset()));
    }
    if (metadata_result->is_valid()) {
      str(zr, Address(rthread, JavaThread::vm_result_metadata_offset()));
    }
    if (frame_size() == no_frame_size) {
      leave();
      far_jump(RuntimeAddress(StubRoutines::forward_exception_entry()));
    } else if (_stub_id == (int)StubId::c1_forward_exception_id) {
      should_not_reach_here();
    } else {
      far_jump(RuntimeAddress(Runtime1::entry_for(StubId::c1_forward_exception_id)));
    }
    bind(L);
  }
  // get oop results if there are any and reset the values in the thread
  if (oop_result1->is_valid()) {
    get_vm_result_oop(oop_result1, rthread);
  }
  if (metadata_result->is_valid()) {
    get_vm_result_metadata(metadata_result, rthread);
  }
  return call_offset;
}


int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, Register arg1) {
  mov(c_rarg1, arg1);
  return call_RT(oop_result1, metadata_result, entry, 1);
}


int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, Register arg1, Register arg2) {
  if (c_rarg1 == arg2) {
    if (c_rarg2 == arg1) {
      mov(rscratch1, arg1);
      mov(arg1, arg2);
      mov(arg2, rscratch1);
    } else {
      mov(c_rarg2, arg2);
      mov(c_rarg1, arg1);
    }
  } else {
    mov(c_rarg1, arg1);
    mov(c_rarg2, arg2);
  }
  return call_RT(oop_result1, metadata_result, entry, 2);
}


int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, Register arg1, Register arg2, Register arg3) {
  // if there is any conflict use the stack
  if (arg1 == c_rarg2 || arg1 == c_rarg3 ||
      arg2 == c_rarg1 || arg2 == c_rarg3 ||
      arg3 == c_rarg1 || arg3 == c_rarg2) {
    stp(arg3, arg2, Address(pre(sp, -2 * wordSize)));
    stp(arg1, zr, Address(pre(sp, -2 * wordSize)));
    ldp(c_rarg1, zr, Address(post(sp, 2 * wordSize)));
    ldp(c_rarg3, c_rarg2, Address(post(sp, 2 * wordSize)));
  } else {
    mov(c_rarg1, arg1);
    mov(c_rarg2, arg2);
    mov(c_rarg3, arg3);
  }
  return call_RT(oop_result1, metadata_result, entry, 3);
}

enum return_state_t {
  does_not_return, requires_return, requires_pop_epilogue_return
};

// Implementation of StubFrame

class StubFrame: public StackObj {
 private:
  StubAssembler* _sasm;
  return_state_t _return_state;

 public:
  StubFrame(StubAssembler* sasm, const char* name, bool must_gc_arguments, return_state_t return_state=requires_return);
  void load_argument(int offset_in_words, Register reg);

  ~StubFrame();
};;

void StubAssembler::prologue(const char* name, bool must_gc_arguments) {
  set_info(name, must_gc_arguments);
  enter();
}

void StubAssembler::epilogue(bool use_pop) {
  // Avoid using a leave instruction when this frame may
  // have been frozen, since the current value of rfp
  // restored from the stub would be invalid. We still
  // must restore the rfp value saved on enter though.
  if (use_pop) {
    ldp(rfp, lr, Address(post(sp, 2 * wordSize)));
    authenticate_return_address();
  } else {
    leave();
  }
  ret(lr);
}

#define __ _sasm->

StubFrame::StubFrame(StubAssembler* sasm, const char* name, bool must_gc_arguments, return_state_t return_state) {
  _sasm = sasm;
  _return_state = return_state;
  __ prologue(name, must_gc_arguments);
}

// load parameters that were stored with LIR_Assembler::store_parameter
// Note: offsets for store_parameter and load_argument must match
void StubFrame::load_argument(int offset_in_words, Register reg) {
  __ load_parameter(offset_in_words, reg);
}

StubFrame::~StubFrame() {
  if (_return_state == does_not_return) {
    __ should_not_reach_here();
  } else {
    __ epilogue(_return_state == requires_pop_epilogue_return);
  }
}

#undef __


// Implementation of Runtime1

#define __ sasm->

const int float_regs_as_doubles_size_in_slots = pd_nof_fpu_regs_frame_map * 2;

// Stack layout for saving/restoring  all the registers needed during a runtime
// call (this includes deoptimization)
// Note: note that users of this frame may well have arguments to some runtime
// while these values are on the stack. These positions neglect those arguments
// but the code in save_live_registers will take the argument count into
// account.
//

enum reg_save_layout {
  reg_save_frame_size = 32 /* float */ + 32 /* integer */
};

// Save off registers which might be killed by calls into the runtime.
// Tries to smart of about FP registers.  In particular we separate
// saving and describing the FPU registers for deoptimization since we
// have to save the FPU registers twice if we describe them.  The
// deopt blob is the only thing which needs to describe FPU registers.
// In all other cases it should be sufficient to simply save their
// current value.

static int cpu_reg_save_offsets[FrameMap::nof_cpu_regs];
static int fpu_reg_save_offsets[FrameMap::nof_fpu_regs];
static int reg_save_size_in_words;
static int frame_size_in_bytes = -1;

static OopMap* generate_oop_map(StubAssembler* sasm, bool save_fpu_registers) {
  int frame_size_in_bytes = reg_save_frame_size * BytesPerWord;
  sasm->set_frame_size(frame_size_in_bytes / BytesPerWord);
  int frame_size_in_slots = frame_size_in_bytes / sizeof(jint);
  OopMap* oop_map = new OopMap(frame_size_in_slots, 0);

  for (int i = 0; i < FrameMap::nof_caller_save_cpu_regs(); i++) {
    LIR_Opr opr = FrameMap::caller_save_cpu_reg_at(i);
    Register r = opr->as_register();
    int reg_num = r->encoding();
    int sp_offset = cpu_reg_save_offsets[reg_num];
    oop_map->set_callee_saved(VMRegImpl::stack2reg(cpu_reg_save_offsets[reg_num]), r->as_VMReg());
  }

  Register r = rthread;
  int reg_num = r->encoding();
  oop_map->set_callee_saved(VMRegImpl::stack2reg(cpu_reg_save_offsets[reg_num]), r->as_VMReg());

  if (save_fpu_registers) {
    for (int i = 0; i < FrameMap::nof_fpu_regs; i++) {
      FloatRegister r = as_FloatRegister(i);
      {
        int sp_offset = fpu_reg_save_offsets[i];
        oop_map->set_callee_saved(VMRegImpl::stack2reg(sp_offset),
                                  r->as_VMReg());
      }
    }
  }
  return oop_map;
}

static OopMap* save_live_registers(StubAssembler* sasm,
                                   bool save_fpu_registers = true) {
  __ block_comment("save_live_registers");

  __ push(RegSet::range(r0, r29), sp);         // integer registers except lr & sp

  if (save_fpu_registers) {
    for (int i = 31; i>= 0; i -= 4) {
      __ sub(sp, sp, 4 * wordSize); // no pre-increment for st1. Emulate it without modifying other registers
      __ st1(as_FloatRegister(i-3), as_FloatRegister(i-2), as_FloatRegister(i-1),
          as_FloatRegister(i), __ T1D, Address(sp));
    }
  } else {
    __ add(sp, sp, -32 * wordSize);
  }

  return generate_oop_map(sasm, save_fpu_registers);
}

static void restore_live_registers(StubAssembler* sasm, bool restore_fpu_registers = true) {
  if (restore_fpu_registers) {
    for (int i = 0; i < 32; i += 4)
      __ ld1(as_FloatRegister(i), as_FloatRegister(i+1), as_FloatRegister(i+2),
          as_FloatRegister(i+3), __ T1D, Address(__ post(sp, 4 * wordSize)));
  } else {
    __ add(sp, sp, 32 * wordSize);
  }

  __ pop(RegSet::range(r0, r29), sp);
}

static void restore_live_registers_except_r0(StubAssembler* sasm, bool restore_fpu_registers = true)  {

  if (restore_fpu_registers) {
    for (int i = 0; i < 32; i += 4)
      __ ld1(as_FloatRegister(i), as_FloatRegister(i+1), as_FloatRegister(i+2),
          as_FloatRegister(i+3), __ T1D, Address(__ post(sp, 4 * wordSize)));
  } else {
    __ add(sp, sp, 32 * wordSize);
  }

  __ ldp(zr, r1, Address(__ post(sp, 16)));
  __ pop(RegSet::range(r2, r29), sp);
}



void Runtime1::initialize_pd() {
  int i;
  int sp_offset = 0;

  // all float registers are saved explicitly
  assert(FrameMap::nof_fpu_regs == 32, "double registers not handled here");
  for (i = 0; i < FrameMap::nof_fpu_regs; i++) {
    fpu_reg_save_offsets[i] = sp_offset;
    sp_offset += 2;   // SP offsets are in halfwords
  }

  for (i = 0; i < FrameMap::nof_cpu_regs; i++) {
    Register r = as_Register(i);
    cpu_reg_save_offsets[i] = sp_offset;
    sp_offset += 2;   // SP offsets are in halfwords
  }
}

// return: offset in 64-bit words.
uint Runtime1::runtime_blob_current_thread_offset(frame f) {
  CodeBlob* cb = f.cb();
  assert(cb == Runtime1::blob_for(StubId::c1_monitorenter_id) ||
         cb == Runtime1::blob_for(StubId::c1_monitorenter_nofpu_id), "must be");
  assert(cb != nullptr && cb->is_runtime_stub(), "invalid frame");
  int offset = cpu_reg_save_offsets[rthread->encoding()];
  return offset / 2;   // SP offsets are in halfwords
}

// target: the entry point of the method that creates and posts the exception oop
// has_argument: true if the exception needs arguments (passed in rscratch1 and rscratch2)

OopMapSet* Runtime1::generate_exception_throw(StubAssembler* sasm, address target, bool has_argument) {
  // make a frame and preserve the caller's caller-save registers
  OopMap* oop_map = save_live_registers(sasm);
  int call_offset;
  if (!has_argument) {
    call_offset = __ call_RT(noreg, noreg, target);
  } else {
    __ mov(c_rarg1, rscratch1);
    __ mov(c_rarg2, rscratch2);
    call_offset = __ call_RT(noreg, noreg, target);
  }
  OopMapSet* oop_maps = new OopMapSet();
  oop_maps->add_gc_map(call_offset, oop_map);
  return oop_maps;
}


OopMapSet* Runtime1::generate_handle_exception(StubId id, StubAssembler *sasm) {
  __ block_comment("generate_handle_exception");

  // incoming parameters
  const Register exception_oop = r0;
  const Register exception_pc  = r3;
  // other registers used in this stub

  // Save registers, if required.
  OopMapSet* oop_maps = new OopMapSet();
  OopMap* oop_map = nullptr;
  switch (id) {
  case StubId::c1_forward_exception_id:
    // We're handling an exception in the context of a compiled frame.
    // The registers have been saved in the standard places.  Perform
    // an exception lookup in the caller and dispatch to the handler
    // if found.  Otherwise unwind and dispatch to the callers
    // exception handler.
    oop_map = generate_oop_map(sasm, 1 /*thread*/);

    // load and clear pending exception oop into r0
    __ ldr(exception_oop, Address(rthread, Thread::pending_exception_offset()));
    __ str(zr, Address(rthread, Thread::pending_exception_offset()));

    // load issuing PC (the return address for this stub) into r3
    __ ldr(exception_pc, Address(rfp, 1*BytesPerWord));
    __ authenticate_return_address(exception_pc);

    // make sure that the vm_results are cleared (may be unnecessary)
    __ str(zr, Address(rthread, JavaThread::vm_result_oop_offset()));
    __ str(zr, Address(rthread, JavaThread::vm_result_metadata_offset()));
    break;
  case StubId::c1_handle_exception_nofpu_id:
  case StubId::c1_handle_exception_id:
    // At this point all registers MAY be live.
    oop_map = save_live_registers(sasm, id != StubId::c1_handle_exception_nofpu_id);
    break;
  case StubId::c1_handle_exception_from_callee_id: {
    // At this point all registers except exception oop (r0) and
    // exception pc (lr) are dead.
    const int frame_size = 2 /*fp, return address*/;
    oop_map = new OopMap(frame_size * VMRegImpl::slots_per_word, 0);
    sasm->set_frame_size(frame_size);
    break;
  }
  default: ShouldNotReachHere();
  }

  // verify that only r0 and r3 are valid at this time
  __ invalidate_registers(false, true, true, false, true, true);
  // verify that r0 contains a valid exception
  __ verify_not_null_oop(exception_oop);

#ifdef ASSERT
  // check that fields in JavaThread for exception oop and issuing pc are
  // empty before writing to them
  Label oop_empty;
  __ ldr(rscratch1, Address(rthread, JavaThread::exception_oop_offset()));
  __ cbz(rscratch1, oop_empty);
  __ stop("exception oop already set");
  __ bind(oop_empty);

  Label pc_empty;
  __ ldr(rscratch1, Address(rthread, JavaThread::exception_pc_offset()));
  __ cbz(rscratch1, pc_empty);
  __ stop("exception pc already set");
  __ bind(pc_empty);
#endif

  // save exception oop and issuing pc into JavaThread
  // (exception handler will load it from here)
  __ str(exception_oop, Address(rthread, JavaThread::exception_oop_offset()));
  __ str(exception_pc, Address(rthread, JavaThread::exception_pc_offset()));

  // patch throwing pc into return address (has bci & oop map)
  __ protect_return_address(exception_pc);
  __ str(exception_pc, Address(rfp, 1*BytesPerWord));

  // compute the exception handler.
  // the exception oop and the throwing pc are read from the fields in JavaThread
  int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, exception_handler_for_pc));
  oop_maps->add_gc_map(call_offset, oop_map);

  // r0: handler address
  //      will be the deopt blob if nmethod was deoptimized while we looked up
  //      handler regardless of whether handler existed in the nmethod.

  // only r0 is valid at this time, all other registers have been destroyed by the runtime call
  __ invalidate_registers(false, true, true, true, true, true);

  // patch the return address, this stub will directly return to the exception handler
  __ protect_return_address(r0);
  __ str(r0, Address(rfp, 1*BytesPerWord));

  switch (id) {
  case StubId::c1_forward_exception_id:
  case StubId::c1_handle_exception_nofpu_id:
  case StubId::c1_handle_exception_id:
    // Restore the registers that were saved at the beginning.
    restore_live_registers(sasm, id != StubId::c1_handle_exception_nofpu_id);
    break;
  case StubId::c1_handle_exception_from_callee_id:
    break;
  default:  ShouldNotReachHere();
  }

  return oop_maps;
}


void Runtime1::generate_unwind_exception(StubAssembler *sasm) {
  // incoming parameters
  const Register exception_oop = r0;
  // callee-saved copy of exception_oop during runtime call
  const Register exception_oop_callee_saved = r19;
  // other registers used in this stub
  const Register exception_pc = r3;
  const Register handler_addr = r1;

  if (AbortVMOnException) {
    __ mov(rscratch1, exception_oop);
    __ enter();
    save_live_registers(sasm);
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, check_abort_on_vm_exception), rscratch1);
    restore_live_registers(sasm);
    __ leave();
  }

  // verify that only r0, is valid at this time
  __ invalidate_registers(false, true, true, true, true, true);

#ifdef ASSERT
  // check that fields in JavaThread for exception oop and issuing pc are empty
  Label oop_empty;
  __ ldr(rscratch1, Address(rthread, JavaThread::exception_oop_offset()));
  __ cbz(rscratch1, oop_empty);
  __ stop("exception oop must be empty");
  __ bind(oop_empty);

  Label pc_empty;
  __ ldr(rscratch1, Address(rthread, JavaThread::exception_pc_offset()));
  __ cbz(rscratch1, pc_empty);
  __ stop("exception pc must be empty");
  __ bind(pc_empty);
#endif

  // Save our return address because
  // exception_handler_for_return_address will destroy it.  We also
  // save exception_oop
  __ mov(r3, lr);
  __ protect_return_address();
  __ stp(lr, exception_oop, Address(__ pre(sp, -2 * wordSize)));

  // search the exception handler address of the caller (using the return address)
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address), rthread, r3);
  // r0: exception handler address of the caller

  // Only R0 is valid at this time; all other registers have been
  // destroyed by the call.
  __ invalidate_registers(false, true, true, true, false, true);

  // move result of call into correct register
  __ mov(handler_addr, r0);

  // get throwing pc (= return address).
  // lr has been destroyed by the call
  __ ldp(lr, exception_oop, Address(__ post(sp, 2 * wordSize)));
  __ authenticate_return_address();
  __ mov(r3, lr);

  __ verify_not_null_oop(exception_oop);

  // continue at exception handler (return address removed)
  // note: do *not* remove arguments when unwinding the
  //       activation since the caller assumes having
  //       all arguments on the stack when entering the
  //       runtime to determine the exception handler
  //       (GC happens at call site with arguments!)
  // r0: exception oop
  // r3: throwing pc
  // r1: exception handler
  __ br(handler_addr);
}



OopMapSet* Runtime1::generate_patching(StubAssembler* sasm, address target) {
  // use the maximum number of runtime-arguments here because it is difficult to
  // distinguish each RT-Call.
  // Note: This number affects also the RT-Call in generate_handle_exception because
  //       the oop-map is shared for all calls.
  DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
  assert(deopt_blob != nullptr, "deoptimization blob must have been created");

  OopMap* oop_map = save_live_registers(sasm);

  __ mov(c_rarg0, rthread);
  Label retaddr;
  __ set_last_Java_frame(sp, rfp, retaddr, rscratch1);
  // do the call
  __ lea(rscratch1, RuntimeAddress(target));
  __ blr(rscratch1);
  __ bind(retaddr);
  OopMapSet* oop_maps = new OopMapSet();
  oop_maps->add_gc_map(__ offset(), oop_map);
  // verify callee-saved register
#ifdef ASSERT
  { Label L;
    __ get_thread(rscratch1);
    __ cmp(rthread, rscratch1);
    __ br(Assembler::EQ, L);
    __ stop("StubAssembler::call_RT: rthread not callee saved?");
    __ bind(L);
  }
#endif

  __ reset_last_Java_frame(true);

#ifdef ASSERT
  // check that fields in JavaThread for exception oop and issuing pc are empty
  Label oop_empty;
  __ ldr(rscratch1, Address(rthread, Thread::pending_exception_offset()));
  __ cbz(rscratch1, oop_empty);
  __ stop("exception oop must be empty");
  __ bind(oop_empty);

  Label pc_empty;
  __ ldr(rscratch1, Address(rthread, JavaThread::exception_pc_offset()));
  __ cbz(rscratch1, pc_empty);
  __ stop("exception pc must be empty");
  __ bind(pc_empty);
#endif

  // Runtime will return true if the nmethod has been deoptimized, this is the
  // expected scenario and anything else is  an error. Note that we maintain a
  // check on the result purely as a defensive measure.
  Label no_deopt;
  __ cbz(r0, no_deopt);                                // Have we deoptimized?

  // Perform a re-execute. The proper return  address is already on the stack,
  // we just need  to restore registers, pop  all of our frame  but the return
  // address and jump to the deopt blob.
  restore_live_registers(sasm);
  __ leave();
  __ far_jump(RuntimeAddress(deopt_blob->unpack_with_reexecution()));

  __ bind(no_deopt);
  __ stop("deopt not performed");

  return oop_maps;
}


OopMapSet* Runtime1::generate_code_for(StubId id, StubAssembler* sasm) {

  const Register exception_oop = r0;
  const Register exception_pc  = r3;

  // for better readability
  const bool must_gc_arguments = true;
  const bool dont_gc_arguments = false;

  // default value; overwritten for some optimized stubs that are called from methods that do not use the fpu
  bool save_fpu_registers = true;

  // stub code & info for the different stubs
  OopMapSet* oop_maps = nullptr;
  OopMap* oop_map = nullptr;
  switch (id) {
    {
    case StubId::c1_forward_exception_id:
      {
        oop_maps = generate_handle_exception(id, sasm);
        __ leave();
        __ ret(lr);
      }
      break;

    case StubId::c1_throw_div0_exception_id:
      { StubFrame f(sasm, "throw_div0_exception", dont_gc_arguments, does_not_return);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_div0_exception), false);
      }
      break;

    case StubId::c1_throw_null_pointer_exception_id:
      { StubFrame f(sasm, "throw_null_pointer_exception", dont_gc_arguments, does_not_return);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_null_pointer_exception), false);
      }
      break;

    case StubId::c1_new_instance_id:
    case StubId::c1_fast_new_instance_id:
    case StubId::c1_fast_new_instance_init_check_id:
      {
        Register klass = r3; // Incoming
        Register obj   = r0; // Result

        if (id == StubId::c1_new_instance_id) {
          __ set_info("new_instance", dont_gc_arguments);
        } else if (id == StubId::c1_fast_new_instance_id) {
          __ set_info("fast new_instance", dont_gc_arguments);
        } else {
          assert(id == StubId::c1_fast_new_instance_init_check_id, "bad StubId");
          __ set_info("fast new_instance init check", dont_gc_arguments);
        }

        __ enter();
        OopMap* map = save_live_registers(sasm);
        int call_offset = __ call_RT(obj, noreg, CAST_FROM_FN_PTR(address, new_instance), klass);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers_except_r0(sasm);
        __ verify_oop(obj);
        __ leave();
        __ ret(lr);

        // r0,: new instance
      }

      break;

    case StubId::c1_counter_overflow_id:
      {
        Register bci = r0, method = r1;
        __ enter();
        OopMap* map = save_live_registers(sasm);
        // Retrieve bci
        __ ldrw(bci, Address(rfp, 2*BytesPerWord));
        // And a pointer to the Method*
        __ ldr(method, Address(rfp, 3*BytesPerWord));
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, counter_overflow), bci, method);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm);
        __ leave();
        __ ret(lr);
      }
      break;

    case StubId::c1_new_type_array_id:
    case StubId::c1_new_object_array_id:
      {
        Register length   = r19; // Incoming
        Register klass    = r3; // Incoming
        Register obj      = r0; // Result

        if (id == StubId::c1_new_type_array_id) {
          __ set_info("new_type_array", dont_gc_arguments);
        } else {
          __ set_info("new_object_array", dont_gc_arguments);
        }

#ifdef ASSERT
        // assert object type is really an array of the proper kind
        {
          Label ok;
          Register t0 = obj;
          __ ldrw(t0, Address(klass, Klass::layout_helper_offset()));
          __ asrw(t0, t0, Klass::_lh_array_tag_shift);
          int tag = ((id == StubId::c1_new_type_array_id)
                     ? Klass::_lh_array_tag_type_value
                     : Klass::_lh_array_tag_obj_value);
          __ mov(rscratch1, tag);
          __ cmpw(t0, rscratch1);
          __ br(Assembler::EQ, ok);
          __ stop("assert(is an array klass)");
          __ should_not_reach_here();
          __ bind(ok);
        }
#endif // ASSERT

        __ enter();
        OopMap* map = save_live_registers(sasm);
        int call_offset;
        if (id == StubId::c1_new_type_array_id) {
          call_offset = __ call_RT(obj, noreg, CAST_FROM_FN_PTR(address, new_type_array), klass, length);
        } else {
          call_offset = __ call_RT(obj, noreg, CAST_FROM_FN_PTR(address, new_object_array), klass, length);
        }

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers_except_r0(sasm);

        __ verify_oop(obj);
        __ leave();
        __ ret(lr);

        // r0: new array
      }
      break;

    case StubId::c1_new_multi_array_id:
      { StubFrame f(sasm, "new_multi_array", dont_gc_arguments);
        // r0,: klass
        // r19,: rank
        // r2: address of 1st dimension
        OopMap* map = save_live_registers(sasm);
        __ mov(c_rarg1, r0);
        __ mov(c_rarg3, r2);
        __ mov(c_rarg2, r19);
        int call_offset = __ call_RT(r0, noreg, CAST_FROM_FN_PTR(address, new_multi_array), r1, r2, r3);

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers_except_r0(sasm);

        // r0,: new multi array
        __ verify_oop(r0);
      }
      break;

    case StubId::c1_register_finalizer_id:
      {
        __ set_info("register_finalizer", dont_gc_arguments);

        // This is called via call_runtime so the arguments
        // will be place in C abi locations

        __ verify_oop(c_rarg0);

        // load the klass and check the has finalizer flag
        Label register_finalizer;
        Register t = r5;
        __ load_klass(t, r0);
        __ ldrb(t, Address(t, Klass::misc_flags_offset()));
        __ tbnz(t, exact_log2(KlassFlags::_misc_has_finalizer), register_finalizer);
        __ ret(lr);

        __ bind(register_finalizer);
        __ enter();
        OopMap* oop_map = save_live_registers(sasm);
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, SharedRuntime::register_finalizer), r0);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);

        // Now restore all the live registers
        restore_live_registers(sasm);

        __ leave();
        __ ret(lr);
      }
      break;

    case StubId::c1_throw_class_cast_exception_id:
      { StubFrame f(sasm, "throw_class_cast_exception", dont_gc_arguments, does_not_return);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_class_cast_exception), true);
      }
      break;

    case StubId::c1_throw_incompatible_class_change_error_id:
      { StubFrame f(sasm, "throw_incompatible_class_cast_exception", dont_gc_arguments, does_not_return);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_incompatible_class_change_error), false);
      }
      break;

    case StubId::c1_slow_subtype_check_id:
      {
        // Typical calling sequence:
        // __ push(klass_RInfo);  // object klass or other subclass
        // __ push(sup_k_RInfo);  // array element klass or other superclass
        // __ bl(slow_subtype_check);
        // Note that the subclass is pushed first, and is therefore deepest.
        enum layout {
          r0_off, r0_off_hi,
          r2_off, r2_off_hi,
          r4_off, r4_off_hi,
          r5_off, r5_off_hi,
          sup_k_off, sup_k_off_hi,
          klass_off, klass_off_hi,
          framesize,
          result_off = sup_k_off
        };

        __ set_info("slow_subtype_check", dont_gc_arguments);
        __ push(RegSet::of(r0, r2, r4, r5), sp);

        // This is called by pushing args and not with C abi
        // __ ldr(r4, Address(sp, (klass_off) * VMRegImpl::stack_slot_size)); // subclass
        // __ ldr(r0, Address(sp, (sup_k_off) * VMRegImpl::stack_slot_size)); // superclass

        __ ldp(r4, r0, Address(sp, (sup_k_off) * VMRegImpl::stack_slot_size));

        Label miss;
        __ check_klass_subtype_slow_path(/*sub_klass*/r4,
                                         /*super_klass*/r0,
                                         /*temp_reg*/r2,
                                         /*temp2_reg*/r5,
                                         /*L_success*/nullptr,
                                         /*L_failure*/&miss);
        // Need extras for table lookup: r1, r3, vtemp

        // fallthrough on success:
        __ mov(rscratch1, 1);
        __ str(rscratch1, Address(sp, (result_off) * VMRegImpl::stack_slot_size)); // result
        __ pop(RegSet::of(r0, r2, r4, r5), sp);
        __ ret(lr);

        __ bind(miss);
        __ str(zr, Address(sp, (result_off) * VMRegImpl::stack_slot_size)); // result
        __ pop(RegSet::of(r0, r2, r4, r5), sp);
        __ ret(lr);
      }
      break;

    case StubId::c1_monitorenter_nofpu_id:
      save_fpu_registers = false;
      // fall through
    case StubId::c1_monitorenter_id:
      {
        StubFrame f(sasm, "monitorenter", dont_gc_arguments, requires_pop_epilogue_return);
        OopMap* map = save_live_registers(sasm, save_fpu_registers);

        // Called with store_parameter and not C abi

        f.load_argument(1, r0); // r0,: object
        f.load_argument(0, r1); // r1,: lock address

        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, monitorenter), r0, r1);

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm, save_fpu_registers);
      }
      break;

    case StubId::c1_is_instance_of_id:
      {
        // Mirror: c_rarg0
        // Object: c_rarg1
        // Temps: r3, r4, r5, r6
        // Result: r0

        // Get the Klass* into c_rarg6
        Register klass = c_rarg6, obj = c_rarg1, result = r0;
        __ ldr(klass, Address(c_rarg0, java_lang_Class::klass_offset()));

        Label fail, is_secondary, success;

        __ cbz(klass, fail); // Klass is null
        __ cbz(obj, fail); // obj is null

        __ ldrw(r3, Address(klass, in_bytes(Klass::super_check_offset_offset())));
        __ cmpw(r3, in_bytes(Klass::secondary_super_cache_offset()));
        __ br(Assembler::EQ, is_secondary); // Klass is a secondary superclass

        // Klass is a concrete class
        __ load_klass(r5, obj);
        __ ldr(rscratch1, Address(r5, r3));
        __ cmp(klass, rscratch1);
        __ cset(result, Assembler::EQ);
        __ ret(lr);

        __ bind(is_secondary);

        __ load_klass(obj, obj);

        // This is necessary because I am never in my own secondary_super list.
        __ cmp(obj, klass);
        __ br(Assembler::EQ, success);

        __ lookup_secondary_supers_table_var(obj, klass,
                                             /*temps*/r3, r4, r5, v0,
                                             result,
                                             &success);
        __ bind(fail);
        __ mov(result, 0);
        __ ret(lr);

        __ bind(success);
        __ mov(result, 1);
        __ ret(lr);
      }
      break;

    case StubId::c1_monitorexit_nofpu_id:
      save_fpu_registers = false;
      // fall through
    case StubId::c1_monitorexit_id:
      {
        StubFrame f(sasm, "monitorexit", dont_gc_arguments);
        OopMap* map = save_live_registers(sasm, save_fpu_registers);

        // Called with store_parameter and not C abi

        f.load_argument(0, r0); // r0,: lock address

        // note: really a leaf routine but must setup last java sp
        //       => use call_RT for now (speed can be improved by
        //       doing last java sp setup manually)
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, monitorexit), r0);

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm, save_fpu_registers);
      }
      break;

    case StubId::c1_deoptimize_id:
      {
        StubFrame f(sasm, "deoptimize", dont_gc_arguments, does_not_return);
        OopMap* oop_map = save_live_registers(sasm);
        f.load_argument(0, c_rarg1);
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, deoptimize), c_rarg1);

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);
        restore_live_registers(sasm);
        DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
        assert(deopt_blob != nullptr, "deoptimization blob must have been created");
        __ leave();
        __ far_jump(RuntimeAddress(deopt_blob->unpack_with_reexecution()));
      }
      break;

    case StubId::c1_throw_range_check_failed_id:
      { StubFrame f(sasm, "range_check_failed", dont_gc_arguments, does_not_return);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_range_check_exception), true);
      }
      break;

    case StubId::c1_unwind_exception_id:
      { __ set_info("unwind_exception", dont_gc_arguments);
        // note: no stubframe since we are about to leave the current
        //       activation and we are calling a leaf VM function only.
        generate_unwind_exception(sasm);
      }
      break;

    case StubId::c1_access_field_patching_id:
      { StubFrame f(sasm, "access_field_patching", dont_gc_arguments, does_not_return);
        // we should set up register map
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, access_field_patching));
      }
      break;

    case StubId::c1_load_klass_patching_id:
      { StubFrame f(sasm, "load_klass_patching", dont_gc_arguments, does_not_return);
        // we should set up register map
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_klass_patching));
      }
      break;

    case StubId::c1_load_mirror_patching_id:
      { StubFrame f(sasm, "load_mirror_patching", dont_gc_arguments, does_not_return);
        // we should set up register map
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_mirror_patching));
      }
      break;

    case StubId::c1_load_appendix_patching_id:
      { StubFrame f(sasm, "load_appendix_patching", dont_gc_arguments, does_not_return);
        // we should set up register map
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_appendix_patching));
      }
      break;

    case StubId::c1_handle_exception_nofpu_id:
    case StubId::c1_handle_exception_id:
      { StubFrame f(sasm, "handle_exception", dont_gc_arguments);
        oop_maps = generate_handle_exception(id, sasm);
      }
      break;

    case StubId::c1_handle_exception_from_callee_id:
      { StubFrame f(sasm, "handle_exception_from_callee", dont_gc_arguments);
        oop_maps = generate_handle_exception(id, sasm);
      }
      break;

    case StubId::c1_throw_index_exception_id:
      { StubFrame f(sasm, "index_range_check_failed", dont_gc_arguments, does_not_return);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_index_exception), true);
      }
      break;

    case StubId::c1_throw_array_store_exception_id:
      { StubFrame f(sasm, "throw_array_store_exception", dont_gc_arguments, does_not_return);
        // tos + 0: link
        //     + 1: return address
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_array_store_exception), true);
      }
      break;

    case StubId::c1_predicate_failed_trap_id:
      {
        StubFrame f(sasm, "predicate_failed_trap", dont_gc_arguments, does_not_return);

        OopMap* map = save_live_registers(sasm);

        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, predicate_failed_trap));
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm);
        __ leave();
        DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
        assert(deopt_blob != nullptr, "deoptimization blob must have been created");

        __ far_jump(RuntimeAddress(deopt_blob->unpack_with_reexecution()));
      }
      break;

    case StubId::c1_dtrace_object_alloc_id:
      { // c_rarg0: object
        StubFrame f(sasm, "dtrace_object_alloc", dont_gc_arguments);
        save_live_registers(sasm);

        __ call_VM_leaf(CAST_FROM_FN_PTR(address, static_cast<int (*)(oopDesc*)>(SharedRuntime::dtrace_object_alloc)), c_rarg0);

        restore_live_registers(sasm);
      }
      break;

    default:
      { StubFrame f(sasm, "unimplemented entry", dont_gc_arguments, does_not_return);
        __ mov(r0, (int)id);
        __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, unimplemented_entry), r0);
      }
      break;
    }
  }
  return oop_maps;
}

#undef __

const char *Runtime1::pd_name_for_address(address entry) { Unimplemented(); }
