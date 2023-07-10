/*
 * Copyright (c) 1999, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "interpreter/interpreter.hpp"
#include "memory/universe.hpp"
#include "nativeInst_riscv.hpp"
#include "oops/compiledICHolder.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "register_riscv.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/powerOfTwo.hpp"
#include "vmreg_riscv.inline.hpp"


// Implementation of StubAssembler

int StubAssembler::call_RT(Register oop_result, Register metadata_result, address entry, int args_size) {
  // setup registers
  assert(!(oop_result->is_valid() || metadata_result->is_valid()) || oop_result != metadata_result,
         "registers must be different");
  assert(oop_result != xthread && metadata_result != xthread, "registers must be different");
  assert(args_size >= 0, "illegal args_size");
  bool align_stack = false;

  mv(c_rarg0, xthread);
  set_num_rt_args(0); // Nothing on stack

  Label retaddr;
  set_last_Java_frame(sp, fp, retaddr, t0);

  // do the call
  RuntimeAddress target(entry);
  relocate(target.rspec(), [&] {
    int32_t offset;
    la_patchable(t0, target, offset);
    jalr(x1, t0, offset);
  });
  bind(retaddr);
  int call_offset = offset();
  // verify callee-saved register
#ifdef ASSERT
  push_reg(x10, sp);
  { Label L;
    get_thread(x10);
    beq(xthread, x10, L);
    stop("StubAssembler::call_RT: xthread not callee saved?");
    bind(L);
  }
  pop_reg(x10, sp);
#endif
  reset_last_Java_frame(true);

  // check for pending exceptions
  { Label L;
    // check for pending exceptions (java_thread is set upon return)
    ld(t0, Address(xthread, in_bytes(Thread::pending_exception_offset())));
    beqz(t0, L);
    // exception pending => remove activation and forward to exception handler
    // make sure that the vm_results are cleared
    if (oop_result->is_valid()) {
      sd(zr, Address(xthread, JavaThread::vm_result_offset()));
    }
    if (metadata_result->is_valid()) {
      sd(zr, Address(xthread, JavaThread::vm_result_2_offset()));
    }
    if (frame_size() == no_frame_size) {
      leave();
      far_jump(RuntimeAddress(StubRoutines::forward_exception_entry()));
    } else if (_stub_id == Runtime1::forward_exception_id) {
      should_not_reach_here();
    } else {
      far_jump(RuntimeAddress(Runtime1::entry_for(Runtime1::forward_exception_id)));
    }
    bind(L);
  }
  // get oop results if there are any and reset the values in the thread
  if (oop_result->is_valid()) {
    get_vm_result(oop_result, xthread);
  }
  if (metadata_result->is_valid()) {
    get_vm_result_2(metadata_result, xthread);
  }
  return call_offset;
}

int StubAssembler::call_RT(Register oop_result, Register metadata_result, address entry, Register arg1) {
  mv(c_rarg1, arg1);
  return call_RT(oop_result, metadata_result, entry, 1);
}

int StubAssembler::call_RT(Register oop_result, Register metadata_result, address entry, Register arg1, Register arg2) {
  const int arg_num = 2;
  if (c_rarg1 == arg2) {
    if (c_rarg2 == arg1) {
      xorr(arg1, arg1, arg2);
      xorr(arg2, arg1, arg2);
      xorr(arg1, arg1, arg2);
    } else {
      mv(c_rarg2, arg2);
      mv(c_rarg1, arg1);
    }
  } else {
    mv(c_rarg1, arg1);
    mv(c_rarg2, arg2);
  }
  return call_RT(oop_result, metadata_result, entry, arg_num);
}

int StubAssembler::call_RT(Register oop_result, Register metadata_result, address entry, Register arg1, Register arg2, Register arg3) {
  const int arg_num = 3;
  // if there is any conflict use the stack
  if (arg1 == c_rarg2 || arg1 == c_rarg3 ||
      arg2 == c_rarg1 || arg2 == c_rarg3 ||
      arg3 == c_rarg1 || arg3 == c_rarg2) {
    const int arg1_sp_offset = 0;
    const int arg2_sp_offset = 1;
    const int arg3_sp_offset = 2;
    addi(sp, sp, -(arg_num + 1) * wordSize);
    sd(arg1, Address(sp, arg1_sp_offset * wordSize));
    sd(arg2, Address(sp, arg2_sp_offset * wordSize));
    sd(arg3, Address(sp, arg3_sp_offset * wordSize));

    ld(c_rarg1, Address(sp, arg1_sp_offset * wordSize));
    ld(c_rarg2, Address(sp, arg2_sp_offset * wordSize));
    ld(c_rarg3, Address(sp, arg3_sp_offset * wordSize));
    addi(sp, sp, (arg_num + 1) * wordSize);
  } else {
    mv(c_rarg1, arg1);
    mv(c_rarg2, arg2);
    mv(c_rarg3, arg3);
  }
  return call_RT(oop_result, metadata_result, entry, arg_num);
}

enum return_state_t {
  does_not_return, requires_return
};

// Implementation of StubFrame

class StubFrame: public StackObj {
 private:
  StubAssembler* _sasm;
  bool _return_state;

 public:
  StubFrame(StubAssembler* sasm, const char* name, bool must_gc_arguments, return_state_t return_state=requires_return);
  void load_argument(int offset_in_words, Register reg);

  ~StubFrame();
};;

void StubAssembler::prologue(const char* name, bool must_gc_arguments) {
  set_info(name, must_gc_arguments);
  enter();
}

void StubAssembler::epilogue() {
  leave();
  ret();
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
  if (_return_state == requires_return) {
    __ epilogue();
  } else {
    __ should_not_reach_here();
  }
  _sasm = NULL;
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
  reg_save_frame_size = 32 /* float */ + 30 /* integer excluding x3, x4 */
};

// Save off registers which might be killed by calls into the runtime.
// Tries to smart of about FPU registers.  In particular we separate
// saving and describing the FPU registers for deoptimization since we
// have to save the FPU registers twice if we describe them.  The
// deopt blob is the only thing which needs to describe FPU registers.
// In all other cases it should be sufficient to simply save their
// current value.

static int cpu_reg_save_offsets[FrameMap::nof_cpu_regs];
static int fpu_reg_save_offsets[FrameMap::nof_fpu_regs];

static OopMap* generate_oop_map(StubAssembler* sasm, bool save_fpu_registers) {
  int frame_size_in_bytes = reg_save_frame_size * BytesPerWord;
  sasm->set_frame_size(frame_size_in_bytes / BytesPerWord);
  int frame_size_in_slots = frame_size_in_bytes / sizeof(jint);
  OopMap* oop_map = new OopMap(frame_size_in_slots, 0);
  assert_cond(oop_map != NULL);

  // caller save registers only, see FrameMap::initialize
  // in c1_FrameMap_riscv.cpp for detail.
  const static Register caller_save_cpu_regs[FrameMap::max_nof_caller_save_cpu_regs] = {
    x7, x10, x11, x12, x13, x14, x15, x16, x17, x28, x29, x30, x31
  };

  for (int i = 0; i < FrameMap::max_nof_caller_save_cpu_regs; i++) {
    Register r = caller_save_cpu_regs[i];
    int sp_offset = cpu_reg_save_offsets[r->encoding()];
    oop_map->set_callee_saved(VMRegImpl::stack2reg(sp_offset),
                              r->as_VMReg());
  }

  // fpu_regs
  if (save_fpu_registers) {
    for (int i = 0; i < FrameMap::nof_fpu_regs; i++) {
      FloatRegister r = as_FloatRegister(i);
      int sp_offset = fpu_reg_save_offsets[i];
      oop_map->set_callee_saved(VMRegImpl::stack2reg(sp_offset),
                                r->as_VMReg());
    }
  }
  return oop_map;
}

static OopMap* save_live_registers(StubAssembler* sasm,
                                   bool save_fpu_registers = true) {
  __ block_comment("save_live_registers");

  // if the number of pushed regs is odd, one slot will be reserved for alignment
  __ push_reg(RegSet::range(x5, x31), sp);    // integer registers except ra(x1) & sp(x2) & gp(x3) & tp(x4)

  if (save_fpu_registers) {
    // float registers
    __ addi(sp, sp, -(FrameMap::nof_fpu_regs * wordSize));
    for (int i = 0; i < FrameMap::nof_fpu_regs; i++) {
      __ fsd(as_FloatRegister(i), Address(sp, i * wordSize));
    }
  } else {
    // we define reg_save_layout = 62 as the fixed frame size,
    // we should also sub 32 * wordSize to sp when save_fpu_registers == false
    __ addi(sp, sp, -32 * wordSize);
  }

  return generate_oop_map(sasm, save_fpu_registers);
}

static void restore_live_registers(StubAssembler* sasm, bool restore_fpu_registers = true) {
  if (restore_fpu_registers) {
    for (int i = 0; i < FrameMap::nof_fpu_regs; i++) {
      __ fld(as_FloatRegister(i), Address(sp, i * wordSize));
    }
    __ addi(sp, sp, FrameMap::nof_fpu_regs * wordSize);
  } else {
    // we define reg_save_layout = 64 as the fixed frame size,
    // we should also add 32 * wordSize to sp when save_fpu_registers == false
    __ addi(sp, sp, 32 * wordSize);
  }

  // if the number of popped regs is odd, the reserved slot for alignment will be removed
  __ pop_reg(RegSet::range(x5, x31), sp);   // integer registers except ra(x1) & sp(x2) & gp(x3) & tp(x4)
}

static void restore_live_registers_except_r10(StubAssembler* sasm, bool restore_fpu_registers = true) {
  if (restore_fpu_registers) {
    for (int i = 0; i < FrameMap::nof_fpu_regs; i++) {
      __ fld(as_FloatRegister(i), Address(sp, i * wordSize));
    }
    __ addi(sp, sp, FrameMap::nof_fpu_regs * wordSize);
  } else {
    // we define reg_save_layout = 64 as the fixed frame size,
    // we should also add 32 * wordSize to sp when save_fpu_registers == false
    __ addi(sp, sp, 32 * wordSize);
  }

  // pop integer registers except ra(x1) & sp(x2) & gp(x3) & tp(x4) & x10
  // there is one reserved slot for alignment on the stack in save_live_registers().
  __ pop_reg(RegSet::range(x5, x9), sp);   // pop x5 ~ x9 with the reserved slot for alignment
  __ pop_reg(RegSet::range(x11, x31), sp); // pop x11 ~ x31; x10 will be automatically skipped here
}

void Runtime1::initialize_pd() {
  int i = 0;
  int sp_offset = 0;
  const int step = 2; // SP offsets are in halfwords

  // all float registers are saved explicitly
  for (i = 0; i < FrameMap::nof_fpu_regs; i++) {
    fpu_reg_save_offsets[i] = sp_offset;
    sp_offset += step;
  }

  // a slot reserved for stack 16-byte alignment, see MacroAssembler::push_reg
  sp_offset += step;
  // we save x5 ~ x31, except x0 ~ x4: loop starts from x5
  for (i = 5; i < FrameMap::nof_cpu_regs; i++) {
    cpu_reg_save_offsets[i] = sp_offset;
    sp_offset += step;
  }
}

// target: the entry point of the method that creates and posts the exception oop
// has_argument: true if the exception needs arguments (passed in t0 and t1)

OopMapSet* Runtime1::generate_exception_throw(StubAssembler* sasm, address target, bool has_argument) {
  // make a frame and preserve the caller's caller-save registers
  OopMap* oop_map = save_live_registers(sasm);
  assert_cond(oop_map != NULL);
  int call_offset = 0;
  if (!has_argument) {
    call_offset = __ call_RT(noreg, noreg, target);
  } else {
    __ mv(c_rarg1, t0);
    __ mv(c_rarg2, t1);
    call_offset = __ call_RT(noreg, noreg, target);
  }
  OopMapSet* oop_maps = new OopMapSet();
  assert_cond(oop_maps != NULL);
  oop_maps->add_gc_map(call_offset, oop_map);

  return oop_maps;
}

OopMapSet* Runtime1::generate_handle_exception(StubID id, StubAssembler *sasm) {
  __ block_comment("generate_handle_exception");

  // incoming parameters
  const Register exception_oop = x10;
  const Register exception_pc  = x13;

  OopMapSet* oop_maps = new OopMapSet();
  assert_cond(oop_maps != NULL);
  OopMap* oop_map = NULL;

  switch (id) {
    case forward_exception_id:
      // We're handling an exception in the context of a compiled frame.
      // The registers have been saved in the standard places.  Perform
      // an exception lookup in the caller and dispatch to the handler
      // if found.  Otherwise unwind and dispatch to the callers
      // exception handler.
      oop_map = generate_oop_map(sasm, 1 /* thread */);

      // load and clear pending exception oop into x10
      __ ld(exception_oop, Address(xthread, Thread::pending_exception_offset()));
      __ sd(zr, Address(xthread, Thread::pending_exception_offset()));

      // load issuing PC (the return address for this stub) into x13
      __ ld(exception_pc, Address(fp, frame::return_addr_offset * BytesPerWord));

      // make sure that the vm_results are cleared (may be unnecessary)
      __ sd(zr, Address(xthread, JavaThread::vm_result_offset()));
      __ sd(zr, Address(xthread, JavaThread::vm_result_2_offset()));
      break;
    case handle_exception_nofpu_id:
    case handle_exception_id:
      // At this point all registers MAY be live.
      oop_map = save_live_registers(sasm, id != handle_exception_nofpu_id);
      break;
    case handle_exception_from_callee_id: {
      // At this point all registers except exception oop (x10) and
      // exception pc (ra) are dead.
      const int frame_size = 2 /* fp, return address */;
      oop_map = new OopMap(frame_size * VMRegImpl::slots_per_word, 0);
      sasm->set_frame_size(frame_size);
      break;
    }
    default: ShouldNotReachHere();
  }

  // verify that only x10 and x13 are valid at this time
  __ invalidate_registers(false, true, true, false, true, true);
  // verify that x10 contains a valid exception
  __ verify_not_null_oop(exception_oop);

#ifdef ASSERT
  // check that fields in JavaThread for exception oop and issuing pc are
  // empty before writing to them
  Label oop_empty;
  __ ld(t0, Address(xthread, JavaThread::exception_oop_offset()));
  __ beqz(t0, oop_empty);
  __ stop("exception oop already set");
  __ bind(oop_empty);

  Label pc_empty;
  __ ld(t0, Address(xthread, JavaThread::exception_pc_offset()));
  __ beqz(t0, pc_empty);
  __ stop("exception pc already set");
  __ bind(pc_empty);
#endif

  // save exception oop and issuing pc into JavaThread
  // (exception handler will load it from here)
  __ sd(exception_oop, Address(xthread, JavaThread::exception_oop_offset()));
  __ sd(exception_pc, Address(xthread, JavaThread::exception_pc_offset()));

  // patch throwing pc into return address (has bci & oop map)
  __ sd(exception_pc, Address(fp, frame::return_addr_offset * BytesPerWord));

  // compute the exception handler.
  // the exception oop and the throwing pc are read from the fields in JavaThread
  int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, exception_handler_for_pc));
  guarantee(oop_map != NULL, "NULL oop_map!");
  oop_maps->add_gc_map(call_offset, oop_map);

  // x10: handler address
  //      will be the deopt blob if nmethod was deoptimized while we looked up
  //      handler regardless of whether handler existed in the nmethod.

  // only x10 is valid at this time, all other registers have been destroyed by the runtime call
  __ invalidate_registers(false, true, true, true, true, true);

  // patch the return address, this stub will directly return to the exception handler
  __ sd(x10, Address(fp, frame::return_addr_offset * BytesPerWord));

  switch (id) {
    case forward_exception_id:
    case handle_exception_nofpu_id:
    case handle_exception_id:
      // Restore the registers that were saved at the beginning.
      restore_live_registers(sasm, id != handle_exception_nofpu_id);
      break;
    case handle_exception_from_callee_id:
      break;
    default: ShouldNotReachHere();
  }

  return oop_maps;
}


void Runtime1::generate_unwind_exception(StubAssembler *sasm) {
  // incoming parameters
  const Register exception_oop = x10;
  // other registers used in this stub
  const Register handler_addr = x11;

  // verify that only x10, is valid at this time
  __ invalidate_registers(false, true, true, true, true, true);

#ifdef ASSERT
  // check that fields in JavaThread for exception oop and issuing pc are empty
  Label oop_empty;
  __ ld(t0, Address(xthread, JavaThread::exception_oop_offset()));
  __ beqz(t0, oop_empty);
  __ stop("exception oop must be empty");
  __ bind(oop_empty);

  Label pc_empty;
  __ ld(t0, Address(xthread, JavaThread::exception_pc_offset()));
  __ beqz(t0, pc_empty);
  __ stop("exception pc must be empty");
  __ bind(pc_empty);
#endif

  // Save our return address because
  // exception_handler_for_return_address will destroy it.  We also
  // save exception_oop
  __ addi(sp, sp, -2 * wordSize);
  __ sd(exception_oop, Address(sp, wordSize));
  __ sd(ra, Address(sp));

  // search the exception handler address of the caller (using the return address)
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address), xthread, ra);
  // x10: exception handler address of the caller

  // Only x10 is valid at this time; all other registers have been
  // destroyed by the call.
  __ invalidate_registers(false, true, true, true, false, true);

  // move result of call into correct register
  __ mv(handler_addr, x10);

  // get throwing pc (= return address).
  // ra has been destroyed by the call
  __ ld(ra, Address(sp));
  __ ld(exception_oop, Address(sp, wordSize));
  __ addi(sp, sp, 2 * wordSize);
  __ mv(x13, ra);

  __ verify_not_null_oop(exception_oop);

  // continue at exception handler (return address removed)
  // note: do *not* remove arguments when unwinding the
  //       activation since the caller assumes having
  //       all arguments on the stack when entering the
  //       runtime to determine the exception handler
  //       (GC happens at call site with arguments!)
  // x10: exception oop
  // x13: throwing pc
  // x11: exception handler
  __ jr(handler_addr);
}

OopMapSet* Runtime1::generate_patching(StubAssembler* sasm, address target) {
  // use the maximum number of runtime-arguments here because it is difficult to
  // distinguish each RT-Call.
  // Note: This number affects also the RT-Call in generate_handle_exception because
  //       the oop-map is shared for all calls.
  DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
  assert(deopt_blob != NULL, "deoptimization blob must have been created");

  OopMap* oop_map = save_live_registers(sasm);
  assert_cond(oop_map != NULL);

  __ mv(c_rarg0, xthread);
  Label retaddr;
  __ set_last_Java_frame(sp, fp, retaddr, t0);
  // do the call
  RuntimeAddress addr(target);
  __ relocate(addr.rspec(), [&] {
    int32_t offset;
    __ la_patchable(t0, addr, offset);
    __ jalr(x1, t0, offset);
  });
  __ bind(retaddr);
  OopMapSet* oop_maps = new OopMapSet();
  assert_cond(oop_maps != NULL);
  oop_maps->add_gc_map(__ offset(), oop_map);
  // verify callee-saved register
#ifdef ASSERT
  { Label L;
    __ get_thread(t0);
    __ beq(xthread, t0, L);
    __ stop("StubAssembler::call_RT: xthread not callee saved?");
    __ bind(L);
  }
#endif
  __ reset_last_Java_frame(true);

#ifdef ASSERT
  // Check that fields in JavaThread for exception oop and issuing pc are empty
  Label oop_empty;
  __ ld(t0, Address(xthread, Thread::pending_exception_offset()));
  __ beqz(t0, oop_empty);
  __ stop("exception oop must be empty");
  __ bind(oop_empty);

  Label pc_empty;
  __ ld(t0, Address(xthread, JavaThread::exception_pc_offset()));
  __ beqz(t0, pc_empty);
  __ stop("exception pc must be empty");
  __ bind(pc_empty);
#endif

  // Runtime will return true if the nmethod has been deoptimized, this is the
  // expected scenario and anything else is an error. Note that we maintain a
  // check on the result purely as a defensive measure.
  Label no_deopt;
  __ beqz(x10, no_deopt);                                // Have we deoptimized?

  // Perform a re-execute. The proper return address is already on the stack,
  // we just need to restore registers, pop all of our frames but the return
  // address and jump to the deopt blob.

  restore_live_registers(sasm);
  __ leave();
  __ far_jump(RuntimeAddress(deopt_blob->unpack_with_reexecution()));

  __ bind(no_deopt);
  __ stop("deopt not performed");

  return oop_maps;
}

OopMapSet* Runtime1::generate_code_for(StubID id, StubAssembler* sasm) {
  // for better readability
  const bool dont_gc_arguments = false;

  // default value; overwritten for some optimized stubs that are called from methods that do not use the fpu
  bool save_fpu_registers = true;

  // stub code & info for the different stubs
  OopMapSet* oop_maps = NULL;
  switch (id) {
    {
    case forward_exception_id:
      {
        oop_maps = generate_handle_exception(id, sasm);
        __ leave();
        __ ret();
      }
      break;

    case throw_div0_exception_id:
      {
        StubFrame f(sasm, "throw_div0_exception", dont_gc_arguments, does_not_return);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_div0_exception), false);
      }
      break;

    case throw_null_pointer_exception_id:
      { StubFrame f(sasm, "throw_null_pointer_exception", dont_gc_arguments, does_not_return);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_null_pointer_exception), false);
      }
      break;

    case new_instance_id:
    case fast_new_instance_id:
    case fast_new_instance_init_check_id:
      {
        Register klass = x13; // Incoming
        Register obj   = x10; // Result

        if (id == new_instance_id) {
          __ set_info("new_instance", dont_gc_arguments);
        } else if (id == fast_new_instance_id) {
          __ set_info("fast new_instance", dont_gc_arguments);
        } else {
          assert(id == fast_new_instance_init_check_id, "bad StubID");
          __ set_info("fast new_instance init check", dont_gc_arguments);
        }

        // If TLAB is disabled, see if there is support for inlining contiguous
        // allocations.
        // Otherwise, just go to the slow path.
        if ((id == fast_new_instance_id || id == fast_new_instance_init_check_id) &&
            !UseTLAB && Universe::heap()->supports_inline_contig_alloc()) {
          Label slow_path;
          Register obj_size   = x12;
          Register tmp1       = x9;
          Register tmp2       = x14;
          assert_different_registers(klass, obj, obj_size, tmp1, tmp2);

          const int sp_offset = 2;
          const int x9_offset = 1;
          const int zr_offset = 0;
          __ addi(sp, sp, -(sp_offset * wordSize));
          __ sd(x9, Address(sp, x9_offset * wordSize));
          __ sd(zr, Address(sp, zr_offset * wordSize));

          if (id == fast_new_instance_init_check_id) {
            // make sure the klass is initialized
            __ lbu(t0, Address(klass, InstanceKlass::init_state_offset()));
            __ mv(t1, InstanceKlass::fully_initialized);
            __ bne(t0, t1, slow_path);
          }

#ifdef ASSERT
          // assert object can be fast path allocated
          {
            Label ok, not_ok;
            __ lw(obj_size, Address(klass, Klass::layout_helper_offset()));
            // make sure it's an instance. For instances, layout helper is a positive number.
            // For arrays, layout helper is a negative number
            __ blez(obj_size, not_ok);
            __ andi(t0, obj_size, Klass::_lh_instance_slow_path_bit);
            __ beqz(t0, ok);
            __ bind(not_ok);
            __ stop("assert(can be fast path allocated)");
            __ should_not_reach_here();
            __ bind(ok);
          }
#endif // ASSERT

          // get the instance size
          __ lwu(obj_size, Address(klass, Klass::layout_helper_offset()));

          __ eden_allocate(obj, obj_size, 0, tmp1, slow_path);

          __ initialize_object(obj, klass, obj_size, 0, tmp1, tmp2, /* is_tlab_allocated */ false);
          __ verify_oop(obj);
          __ ld(x9, Address(sp, x9_offset * wordSize));
          __ ld(zr, Address(sp, zr_offset * wordSize));
          __ addi(sp, sp, sp_offset * wordSize);
          __ ret();

          __ bind(slow_path);
          __ ld(x9, Address(sp, x9_offset * wordSize));
          __ ld(zr, Address(sp, zr_offset * wordSize));
          __ addi(sp, sp, sp_offset * wordSize);
        }

        __ enter();
        OopMap* map = save_live_registers(sasm);
        assert_cond(map != NULL);
        int call_offset = __ call_RT(obj, noreg, CAST_FROM_FN_PTR(address, new_instance), klass);
        oop_maps = new OopMapSet();
        assert_cond(oop_maps != NULL);
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers_except_r10(sasm);
        __ verify_oop(obj);
        __ leave();
        __ ret();

        // x10: new instance
      }

      break;

    case counter_overflow_id:
      {
        Register bci = x10;
        Register method = x11;
        __ enter();
        OopMap* map = save_live_registers(sasm);
        assert_cond(map != NULL);

        const int bci_off = 0;
        const int method_off = 1;
        // Retrieve bci
        __ lw(bci, Address(fp, bci_off * BytesPerWord));
        // And a pointer to the Method*
        __ ld(method, Address(fp, method_off * BytesPerWord));
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, counter_overflow), bci, method);
        oop_maps = new OopMapSet();
        assert_cond(oop_maps != NULL);
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm);
        __ leave();
        __ ret();
      }
      break;

    case new_type_array_id:
    case new_object_array_id:
      {
        Register length   = x9;  // Incoming
        Register klass    = x13; // Incoming
        Register obj      = x10; // Result

        if (id == new_type_array_id) {
          __ set_info("new_type_array", dont_gc_arguments);
        } else {
          __ set_info("new_object_array", dont_gc_arguments);
        }

#ifdef ASSERT
        // assert object type is really an array of the proper kind
        {
          Label ok;
          Register tmp = obj;
          __ lwu(tmp, Address(klass, Klass::layout_helper_offset()));
          __ sraiw(tmp, tmp, Klass::_lh_array_tag_shift);
          int tag = ((id == new_type_array_id) ? Klass::_lh_array_tag_type_value : Klass::_lh_array_tag_obj_value);
          __ mv(t0, tag);
          __ beq(t0, tmp, ok);
          __ stop("assert(is an array klass)");
          __ should_not_reach_here();
          __ bind(ok);
        }
#endif // ASSERT

        // If TLAB is disabled, see if there is support for inlining contiguous
        // allocations.
        // Otherwise, just go to the slow path.
        if (!UseTLAB && Universe::heap()->supports_inline_contig_alloc()) {
          Register arr_size   = x14;
          Register tmp1       = x12;
          Register tmp2       = x15;
          Label slow_path;
          assert_different_registers(length, klass, obj, arr_size, tmp1, tmp2);

          // check that array length is small enough for fast path.
          __ mv(t0, C1_MacroAssembler::max_array_allocation_length);
          __ bgtu(length, t0, slow_path);

          // get the allocation size: round_up(hdr + length << (layout_helper & 0x1F))
          __ lwu(tmp1, Address(klass, Klass::layout_helper_offset()));
          __ andi(t0, tmp1, 0x1f);
          __ sll(arr_size, length, t0);
          int lh_header_size_width = exact_log2(Klass::_lh_header_size_mask + 1);
          int lh_header_size_msb = Klass::_lh_header_size_shift + lh_header_size_width;
          __ slli(tmp1, tmp1, XLEN - lh_header_size_msb);
          __ srli(tmp1, tmp1, XLEN - lh_header_size_width);
          __ add(arr_size, arr_size, tmp1);
          __ addi(arr_size, arr_size, MinObjAlignmentInBytesMask); // align up
          __ andi(arr_size, arr_size, ~(uint)MinObjAlignmentInBytesMask);

          __ eden_allocate(obj, arr_size, 0, tmp1, slow_path); // preserves arr_size

          __ initialize_header(obj, klass, length, tmp1, tmp2);
          __ lbu(tmp1, Address(klass,
                               in_bytes(Klass::layout_helper_offset()) +
                               (Klass::_lh_header_size_shift / BitsPerByte)));
          assert(Klass::_lh_header_size_shift % BitsPerByte == 0, "bytewise");
          assert(Klass::_lh_header_size_mask <= 0xFF, "bytewise");
          __ andi(tmp1, tmp1, Klass::_lh_header_size_mask);
          __ sub(arr_size, arr_size, tmp1); // body length
          __ add(tmp1, tmp1, obj);       // body start
          __ initialize_body(tmp1, arr_size, 0, tmp2);
          __ membar(MacroAssembler::StoreStore);
          __ verify_oop(obj);

          __ ret();

          __ bind(slow_path);
        }

        __ enter();
        OopMap* map = save_live_registers(sasm);
        assert_cond(map != NULL);
        int call_offset = 0;
        if (id == new_type_array_id) {
          call_offset = __ call_RT(obj, noreg, CAST_FROM_FN_PTR(address, new_type_array), klass, length);
        } else {
          call_offset = __ call_RT(obj, noreg, CAST_FROM_FN_PTR(address, new_object_array), klass, length);
        }

        oop_maps = new OopMapSet();
        assert_cond(oop_maps != NULL);
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers_except_r10(sasm);

        __ verify_oop(obj);
        __ leave();
        __ ret();

        // x10: new array
      }
      break;

    case new_multi_array_id:
      {
        StubFrame f(sasm, "new_multi_array", dont_gc_arguments);
        // x10: klass
        // x9: rank
        // x12: address of 1st dimension
        OopMap* map = save_live_registers(sasm);
        assert_cond(map != NULL);
        __ mv(c_rarg1, x10);
        __ mv(c_rarg3, x12);
        __ mv(c_rarg2, x9);
        int call_offset = __ call_RT(x10, noreg, CAST_FROM_FN_PTR(address, new_multi_array), x11, x12, x13);

        oop_maps = new OopMapSet();
        assert_cond(oop_maps != NULL);
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers_except_r10(sasm);

        // x10: new multi array
        __ verify_oop(x10);
      }
      break;

    case register_finalizer_id:
      {
        __ set_info("register_finalizer", dont_gc_arguments);

        // This is called via call_runtime so the arguments
        // will be place in C abi locations
        __ verify_oop(c_rarg0);

        // load the klass and check the has finalizer flag
        Label register_finalizer;
        Register t = x15;
        __ load_klass(t, x10);
        __ lwu(t, Address(t, Klass::access_flags_offset()));
        __ test_bit(t0, t, exact_log2(JVM_ACC_HAS_FINALIZER));
        __ bnez(t0, register_finalizer);
        __ ret();

        __ bind(register_finalizer);
        __ enter();
        OopMap* oop_map = save_live_registers(sasm);
        assert_cond(oop_map != NULL);
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, SharedRuntime::register_finalizer), x10);
        oop_maps = new OopMapSet();
        assert_cond(oop_maps != NULL);
        oop_maps->add_gc_map(call_offset, oop_map);

        // Now restore all the live registers
        restore_live_registers(sasm);

        __ leave();
        __ ret();
      }
      break;

    case throw_class_cast_exception_id:
      {
        StubFrame f(sasm, "throw_class_cast_exception", dont_gc_arguments, does_not_return);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_class_cast_exception), true);
      }
      break;

    case throw_incompatible_class_change_error_id:
      {
        StubFrame f(sasm, "throw_incompatible_class_cast_exception", dont_gc_arguments, does_not_return);
        oop_maps = generate_exception_throw(sasm,
                                            CAST_FROM_FN_PTR(address, throw_incompatible_class_change_error), false);
      }
      break;

    case slow_subtype_check_id:
      {
        // Typical calling sequence:
        // push klass_RInfo (object klass or other subclass)
        // push sup_k_RInfo (array element klass or other superclass)
        // jump to slow_subtype_check
        // Note that the subclass is pushed first, and is therefore deepest.
        enum layout {
          x10_off, x10_off_hi,
          x12_off, x12_off_hi,
          x14_off, x14_off_hi,
          x15_off, x15_off_hi,
          sup_k_off, sup_k_off_hi,
          klass_off, klass_off_hi,
          framesize,
          result_off = sup_k_off
        };

        __ set_info("slow_subtype_check", dont_gc_arguments);
        __ push_reg(RegSet::of(x10, x12, x14, x15), sp);

        __ ld(x14, Address(sp, (klass_off) * VMRegImpl::stack_slot_size)); // sub klass
        __ ld(x10, Address(sp, (sup_k_off) * VMRegImpl::stack_slot_size)); // super klass

        Label miss;
        __ check_klass_subtype_slow_path(x14, x10, x12, x15, NULL, &miss);

        // fallthrough on success:
        __ mv(t0, 1);
        __ sd(t0, Address(sp, (result_off) * VMRegImpl::stack_slot_size)); // result
        __ pop_reg(RegSet::of(x10, x12, x14, x15), sp);
        __ ret();

        __ bind(miss);
        __ sd(zr, Address(sp, (result_off) * VMRegImpl::stack_slot_size)); // result
        __ pop_reg(RegSet::of(x10, x12, x14, x15), sp);
        __ ret();
      }
      break;

    case monitorenter_nofpu_id:
      save_fpu_registers = false;
      // fall through
    case monitorenter_id:
      {
        StubFrame f(sasm, "monitorenter", dont_gc_arguments);
        OopMap* map = save_live_registers(sasm, save_fpu_registers);
        assert_cond(map != NULL);

        // Called with store_parameter and not C abi
        f.load_argument(1, x10); // x10: object
        f.load_argument(0, x11); // x11: lock address

        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, monitorenter), x10, x11);

        oop_maps = new OopMapSet();
        assert_cond(oop_maps != NULL);
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
        OopMap* map = save_live_registers(sasm, save_fpu_registers);
        assert_cond(map != NULL);

        // Called with store_parameter and not C abi
        f.load_argument(0, x10); // x10: lock address

        // note: really a leaf routine but must setup last java sp
        //       => use call_RT for now (speed can be improved by
        //       doing last java sp setup manually)
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, monitorexit), x10);

        oop_maps = new OopMapSet();
        assert_cond(oop_maps != NULL);
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm, save_fpu_registers);
      }
      break;

    case deoptimize_id:
      {
        StubFrame f(sasm, "deoptimize", dont_gc_arguments, does_not_return);
        OopMap* oop_map = save_live_registers(sasm);
        assert_cond(oop_map != NULL);
        f.load_argument(0, c_rarg1);
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, deoptimize), c_rarg1);

        oop_maps = new OopMapSet();
        assert_cond(oop_maps != NULL);
        oop_maps->add_gc_map(call_offset, oop_map);
        restore_live_registers(sasm);
        DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
        assert(deopt_blob != NULL, "deoptimization blob must have been created");
        __ leave();
        __ far_jump(RuntimeAddress(deopt_blob->unpack_with_reexecution()));
      }
      break;

    case throw_range_check_failed_id:
      {
        StubFrame f(sasm, "range_check_failed", dont_gc_arguments, does_not_return);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_range_check_exception), true);
      }
      break;

    case unwind_exception_id:
      {
        __ set_info("unwind_exception", dont_gc_arguments);
        // note: no stubframe since we are about to leave the current
        //       activation and we are calling a leaf VM function only.
        generate_unwind_exception(sasm);
      }
      break;

    case access_field_patching_id:
      {
        StubFrame f(sasm, "access_field_patching", dont_gc_arguments, does_not_return);
        // we should set up register map
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, access_field_patching));
      }
      break;

    case load_klass_patching_id:
      {
        StubFrame f(sasm, "load_klass_patching", dont_gc_arguments, does_not_return);
        // we should set up register map
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_klass_patching));
      }
      break;

    case load_mirror_patching_id:
      {
        StubFrame f(sasm, "load_mirror_patching", dont_gc_arguments, does_not_return);
        // we should set up register map
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_mirror_patching));
      }
      break;

    case load_appendix_patching_id:
      {
        StubFrame f(sasm, "load_appendix_patching", dont_gc_arguments, does_not_return);
        // we should set up register map
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_appendix_patching));
      }
      break;

    case handle_exception_nofpu_id:
    case handle_exception_id:
      {
        StubFrame f(sasm, "handle_exception", dont_gc_arguments);
        oop_maps = generate_handle_exception(id, sasm);
      }
      break;

    case handle_exception_from_callee_id:
      {
        StubFrame f(sasm, "handle_exception_from_callee", dont_gc_arguments);
        oop_maps = generate_handle_exception(id, sasm);
      }
      break;

    case throw_index_exception_id:
      {
        StubFrame f(sasm, "index_range_check_failed", dont_gc_arguments, does_not_return);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_index_exception), true);
      }
      break;

    case throw_array_store_exception_id:
      {
        StubFrame f(sasm, "throw_array_store_exception", dont_gc_arguments, does_not_return);
        // tos + 0: link
        //     + 1: return address
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_array_store_exception), true);
      }
      break;

    case predicate_failed_trap_id:
      {
        StubFrame f(sasm, "predicate_failed_trap", dont_gc_arguments, does_not_return);

        OopMap* map = save_live_registers(sasm);
        assert_cond(map != NULL);

        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, predicate_failed_trap));
        oop_maps = new OopMapSet();
        assert_cond(oop_maps != NULL);
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm);
        __ leave();
        DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
        assert(deopt_blob != NULL, "deoptimization blob must have been created");

        __ far_jump(RuntimeAddress(deopt_blob->unpack_with_reexecution()));
      }
      break;

    case dtrace_object_alloc_id:
      { // c_rarg0: object
        StubFrame f(sasm, "dtrace_object_alloc", dont_gc_arguments);
        save_live_registers(sasm);

        __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_object_alloc), c_rarg0);

        restore_live_registers(sasm);
      }
      break;

    default:
      {
        StubFrame f(sasm, "unimplemented entry", dont_gc_arguments, does_not_return);
        __ mv(x10, (int)id);
        __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, unimplemented_entry), x10);
        __ should_not_reach_here();
      }
      break;
    }
  }
  return oop_maps;
}

#undef __

const char *Runtime1::pd_name_for_address(address entry) { Unimplemented(); return 0; }
