/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "c1/c1_Defs.hpp"
#include "c1/c1_FrameMap.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "c1/c1_Runtime1.hpp"
#include "ci/ciUtilities.hpp"
#include "compiler/compilerDefinitions.inline.hpp"
#include "compiler/oopMap.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/universe.hpp"
#include "nativeInst_x86.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "register_x86.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/macros.hpp"
#include "vmreg_x86.inline.hpp"

// Implementation of StubAssembler

int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, int args_size) {
  // setup registers
  const Register thread = r15_thread;
  assert(!(oop_result1->is_valid() || metadata_result->is_valid()) || oop_result1 != metadata_result, "registers must be different");
  assert(oop_result1 != thread && metadata_result != thread, "registers must be different");
  assert(args_size >= 0, "illegal args_size");
  bool align_stack = false;

  // At a method handle call, the stack may not be properly aligned
  // when returning with an exception.
  align_stack = (stub_id() == (int)StubId::c1_handle_exception_from_callee_id);

  mov(c_rarg0, thread);
  set_num_rt_args(0); // Nothing on stack

  int call_offset = -1;
  if (!align_stack) {
    set_last_Java_frame(noreg, rbp, nullptr, rscratch1);
  } else {
    address the_pc = pc();
    call_offset = offset();
    set_last_Java_frame(noreg, rbp, the_pc, rscratch1);
    andptr(rsp, -(StackAlignmentInBytes));    // Align stack
  }

  // do the call
  call(RuntimeAddress(entry));
  if (!align_stack) {
    call_offset = offset();
  }
  // verify callee-saved register
#ifdef ASSERT
  guarantee(thread != rax, "change this code");
  push(rax);
  { Label L;
    get_thread_slow(rax);
    cmpptr(thread, rax);
    jcc(Assembler::equal, L);
    int3();
    stop("StubAssembler::call_RT: rdi not callee saved?");
    bind(L);
  }
  pop(rax);
#endif
  reset_last_Java_frame(true);

  // check for pending exceptions
  { Label L;
    cmpptr(Address(thread, Thread::pending_exception_offset()), NULL_WORD);
    jcc(Assembler::equal, L);
    // exception pending => remove activation and forward to exception handler
    movptr(rax, Address(thread, Thread::pending_exception_offset()));
    // make sure that the vm_results are cleared
    if (oop_result1->is_valid()) {
      movptr(Address(thread, JavaThread::vm_result_oop_offset()), NULL_WORD);
    }
    if (metadata_result->is_valid()) {
      movptr(Address(thread, JavaThread::vm_result_metadata_offset()), NULL_WORD);
    }
    if (frame_size() == no_frame_size) {
      leave();
      jump(RuntimeAddress(StubRoutines::forward_exception_entry()));
    } else if (_stub_id == (int)StubId::c1_forward_exception_id) {
      should_not_reach_here();
    } else {
      jump(RuntimeAddress(Runtime1::entry_for(StubId::c1_forward_exception_id)));
    }
    bind(L);
  }
  // get oop results if there are any and reset the values in the thread
  if (oop_result1->is_valid()) {
    get_vm_result_oop(oop_result1);
  }
  if (metadata_result->is_valid()) {
    get_vm_result_metadata(metadata_result);
  }

  assert(call_offset >= 0, "Should be set");
  return call_offset;
}


int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, Register arg1) {
  mov(c_rarg1, arg1);
  return call_RT(oop_result1, metadata_result, entry, 1);
}


int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, Register arg1, Register arg2) {
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
  return call_RT(oop_result1, metadata_result, entry, 2);
}


int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, Register arg1, Register arg2, Register arg3) {
  // if there is any conflict use the stack
  if (arg1 == c_rarg2 || arg1 == c_rarg3 ||
      arg2 == c_rarg1 || arg2 == c_rarg3 ||
      arg3 == c_rarg1 || arg3 == c_rarg2) {
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
  return call_RT(oop_result1, metadata_result, entry, 3);
}


// Implementation of StubFrame

class StubFrame: public StackObj {
 private:
  StubAssembler* _sasm;
  bool _use_pop_on_epilog;

 public:
  StubFrame(StubAssembler* sasm, const char* name, bool must_gc_arguments, bool use_pop_on_epilog = false);
  void load_argument(int offset_in_words, Register reg);

  ~StubFrame();
};

void StubAssembler::prologue(const char* name, bool must_gc_arguments) {
  set_info(name, must_gc_arguments);
  enter();
}

void StubAssembler::epilogue(bool use_pop) {
  // Avoid using a leave instruction when this frame may
  // have been frozen, since the current value of rbp
  // restored from the stub would be invalid. We still
  // must restore the rbp value saved on enter though.
  use_pop ? pop(rbp) : leave();
  ret(0);
}

#define __ _sasm->

StubFrame::StubFrame(StubAssembler* sasm, const char* name, bool must_gc_arguments, bool use_pop_on_epilog) {
  _sasm = sasm;
  _use_pop_on_epilog = use_pop_on_epilog;
  __ prologue(name, must_gc_arguments);
}

// load parameters that were stored with LIR_Assembler::store_parameter
// Note: offsets for store_parameter and load_argument must match
void StubFrame::load_argument(int offset_in_words, Register reg) {
  __ load_parameter(offset_in_words, reg);
}


StubFrame::~StubFrame() {
  __ epilogue(_use_pop_on_epilog);
}

#undef __


// Implementation of Runtime1

const int float_regs_as_doubles_size_in_slots = pd_nof_fpu_regs_frame_map * 2;
const int xmm_regs_as_doubles_size_in_slots = FrameMap::nof_xmm_regs * 2;

// Stack layout for saving/restoring  all the registers needed during a runtime
// call (this includes deoptimization)
// Note: note that users of this frame may well have arguments to some runtime
// while these values are on the stack. These positions neglect those arguments
// but the code in save_live_registers will take the argument count into
// account.
//
#define SLOT2(x) x,
#define SLOT_PER_WORD 2

enum reg_save_layout {
  // 64bit needs to keep stack 16 byte aligned. So we add some alignment dummies to make that
  // happen and will assert if the stack size we create is misaligned
  align_dummy_0, align_dummy_1,
#ifdef _WIN64
  // Windows always allocates space for it's argument registers (see
  // frame::arg_reg_save_area_bytes).
  arg_reg_save_1, arg_reg_save_1H,                                                          // 0, 4
  arg_reg_save_2, arg_reg_save_2H,                                                          // 8, 12
  arg_reg_save_3, arg_reg_save_3H,                                                          // 16, 20
  arg_reg_save_4, arg_reg_save_4H,                                                          // 24, 28
#endif // _WIN64
  xmm_regs_as_doubles_off,                                                                  // 32
  float_regs_as_doubles_off = xmm_regs_as_doubles_off + xmm_regs_as_doubles_size_in_slots,  // 160
  fpu_state_off = float_regs_as_doubles_off + float_regs_as_doubles_size_in_slots,          // 224
  // fpu_state_end_off is exclusive
  fpu_state_end_off = fpu_state_off + (FPUStateSizeInWords / SLOT_PER_WORD),                // 352
  marker = fpu_state_end_off, SLOT2(markerH)                                                // 352, 356
  extra_space_offset,                                                                       // 360
  r15_off = extra_space_offset, r15H_off,                                                   // 360, 364
  r14_off, r14H_off,                                                                        // 368, 372
  r13_off, r13H_off,                                                                        // 376, 380
  r12_off, r12H_off,                                                                        // 384, 388
  r11_off, r11H_off,                                                                        // 392, 396
  r10_off, r10H_off,                                                                        // 400, 404
  r9_off, r9H_off,                                                                          // 408, 412
  r8_off, r8H_off,                                                                          // 416, 420
  rdi_off, rdiH_off,                                                                        // 424, 428
  rsi_off, SLOT2(rsiH_off)                                                                  // 432, 436
  rbp_off, SLOT2(rbpH_off)                                                                  // 440, 444
  rsp_off, SLOT2(rspH_off)                                                                  // 448, 452
  rbx_off, SLOT2(rbxH_off)                                                                  // 456, 460
  rdx_off, SLOT2(rdxH_off)                                                                  // 464, 468
  rcx_off, SLOT2(rcxH_off)                                                                  // 472, 476
  rax_off, SLOT2(raxH_off)                                                                  // 480, 484
  saved_rbp_off, SLOT2(saved_rbpH_off)                                                      // 488, 492
  return_off, SLOT2(returnH_off)                                                            // 496, 500
  reg_save_frame_size   // As noted: neglects any parameters to runtime                     // 504
};

// Save off registers which might be killed by calls into the runtime.
// Tries to smart of about FP registers.  In particular we separate
// saving and describing the FPU registers for deoptimization since we
// have to save the FPU registers twice if we describe them and on P4
// saving FPU registers which don't contain anything appears
// expensive.  The deopt blob is the only thing which needs to
// describe FPU registers.  In all other cases it should be sufficient
// to simply save their current value.
//
static OopMap* generate_oop_map(StubAssembler* sasm, int num_rt_args,
                                bool save_fpu_registers = true) {

  // In 64bit all the args are in regs so there are no additional stack slots
  num_rt_args = 0;
  assert((reg_save_frame_size * VMRegImpl::stack_slot_size) % 16 == 0, "must be 16 byte aligned");
  int frame_size_in_slots = reg_save_frame_size + num_rt_args; // args + thread
  sasm->set_frame_size(frame_size_in_slots / VMRegImpl::slots_per_word);

  // record saved value locations in an OopMap
  // locations are offsets from sp after runtime call; num_rt_args is number of arguments in call, including thread
  OopMap* map = new OopMap(frame_size_in_slots, 0);
  map->set_callee_saved(VMRegImpl::stack2reg(rax_off + num_rt_args), rax->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(rcx_off + num_rt_args), rcx->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(rdx_off + num_rt_args), rdx->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(rbx_off + num_rt_args), rbx->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(rsi_off + num_rt_args), rsi->as_VMReg());
  map->set_callee_saved(VMRegImpl::stack2reg(rdi_off + num_rt_args), rdi->as_VMReg());
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

  int xmm_bypass_limit = FrameMap::get_num_caller_save_xmms();

  if (save_fpu_registers) {
    int xmm_off = xmm_regs_as_doubles_off;
    for (int n = 0; n < FrameMap::nof_xmm_regs; n++) {
      if (n < xmm_bypass_limit) {
        VMReg xmm_name_0 = as_XMMRegister(n)->as_VMReg();
        map->set_callee_saved(VMRegImpl::stack2reg(xmm_off + num_rt_args), xmm_name_0);
        // %%% This is really a waste but we'll keep things as they were for now
        if (true) {
          map->set_callee_saved(VMRegImpl::stack2reg(xmm_off + 1 + num_rt_args), xmm_name_0->next());
        }
      }
      xmm_off += 2;
    }
    assert(xmm_off == float_regs_as_doubles_off, "incorrect number of xmm registers");
  }

  return map;
}

#define __ this->

void C1_MacroAssembler::save_live_registers_no_oop_map(bool save_fpu_registers) {
  __ block_comment("save_live_registers");

  // Push CPU state in multiple of 16 bytes
  __ save_legacy_gprs();

  __ subptr(rsp, extra_space_offset * VMRegImpl::stack_slot_size);

#ifdef ASSERT
  __ movptr(Address(rsp, marker * VMRegImpl::stack_slot_size), (int32_t)0xfeedbeef);
#endif

  if (save_fpu_registers) {
    // save XMM registers
    // XMM registers can contain float or double values, but this is not known here,
    // so always save them as doubles.
    // note that float values are _not_ converted automatically, so for float values
    // the second word contains only garbage data.
    int xmm_bypass_limit = FrameMap::get_num_caller_save_xmms();
    int offset = 0;
    for (int n = 0; n < xmm_bypass_limit; n++) {
      XMMRegister xmm_name = as_XMMRegister(n);
      __ movdbl(Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + offset), xmm_name);
      offset += 8;
    }
  }
}

#undef __
#define __ sasm->

static void restore_fpu(C1_MacroAssembler* sasm, bool restore_fpu_registers) {
  if (restore_fpu_registers) {
    // restore XMM registers
    int xmm_bypass_limit = FrameMap::get_num_caller_save_xmms();
    int offset = 0;
    for (int n = 0; n < xmm_bypass_limit; n++) {
      XMMRegister xmm_name = as_XMMRegister(n);
      __ movdbl(xmm_name, Address(rsp, xmm_regs_as_doubles_off * VMRegImpl::stack_slot_size + offset));
      offset += 8;
    }
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

#undef __
#define __ this->

void C1_MacroAssembler::restore_live_registers(bool restore_fpu_registers) {
  __ block_comment("restore_live_registers");

  restore_fpu(this, restore_fpu_registers);
  __ restore_legacy_gprs();
}


void C1_MacroAssembler::restore_live_registers_except_rax(bool restore_fpu_registers) {
  __ block_comment("restore_live_registers_except_rax");

  restore_fpu(this, restore_fpu_registers);

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
}

#undef __
#define __ sasm->

static OopMap* save_live_registers(StubAssembler* sasm, int num_rt_args,
                                   bool save_fpu_registers = true) {
  __ save_live_registers_no_oop_map(save_fpu_registers);
  return generate_oop_map(sasm, num_rt_args, save_fpu_registers);
}

static void restore_live_registers(StubAssembler* sasm, bool restore_fpu_registers = true) {
  __ restore_live_registers(restore_fpu_registers);
}

static void restore_live_registers_except_rax(StubAssembler* sasm, bool restore_fpu_registers = true) {
  sasm->restore_live_registers_except_rax(restore_fpu_registers);
}


void Runtime1::initialize_pd() {
  // nothing to do
}

// return: offset in 64-bit words.
uint Runtime1::runtime_blob_current_thread_offset(frame f) {
  return r15_off / 2;  // rsp offsets are in halfwords
}

// Target: the entry point of the method that creates and posts the exception oop.
// has_argument: true if the exception needs arguments (passed on the stack because
//               registers must be preserved).
OopMapSet* Runtime1::generate_exception_throw(StubAssembler* sasm, address target, bool has_argument) {
  // Preserve all registers.
  int num_rt_args = has_argument ? (2 + 1) : 1;
  OopMap* oop_map = save_live_registers(sasm, num_rt_args);

  // Now all registers are saved and can be used freely.
  // Verify that no old value is used accidentally.
  __ invalidate_registers(true, true, true, true, true, true);

  // Registers used by this stub.
  const Register temp_reg = rbx;

  // Load arguments for exception that are passed as arguments into the stub.
  if (has_argument) {
    __ movptr(c_rarg1, Address(rbp, 2*BytesPerWord));
    __ movptr(c_rarg2, Address(rbp, 3*BytesPerWord));
  }
  int call_offset = __ call_RT(noreg, noreg, target, num_rt_args - 1);

  OopMapSet* oop_maps = new OopMapSet();
  oop_maps->add_gc_map(call_offset, oop_map);

  __ stop("should not reach here");

  return oop_maps;
}


OopMapSet* Runtime1::generate_handle_exception(StubId id, StubAssembler *sasm) {
  __ block_comment("generate_handle_exception");

  // incoming parameters
  const Register exception_oop = rax;
  const Register exception_pc  = rdx;
  // other registers used in this stub
  const Register thread = r15_thread;

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

    // load and clear pending exception oop into RAX
    __ movptr(exception_oop, Address(thread, Thread::pending_exception_offset()));
    __ movptr(Address(thread, Thread::pending_exception_offset()), NULL_WORD);

    // load issuing PC (the return address for this stub) into rdx
    __ movptr(exception_pc, Address(rbp, 1*BytesPerWord));

    // make sure that the vm_results are cleared (may be unnecessary)
    __ movptr(Address(thread, JavaThread::vm_result_oop_offset()),   NULL_WORD);
    __ movptr(Address(thread, JavaThread::vm_result_metadata_offset()), NULL_WORD);
    break;
  case StubId::c1_handle_exception_nofpu_id:
  case StubId::c1_handle_exception_id:
    // At this point all registers MAY be live.
    oop_map = save_live_registers(sasm, 1 /*thread*/, id != StubId::c1_handle_exception_nofpu_id);
    break;
  case StubId::c1_handle_exception_from_callee_id: {
    // At this point all registers except exception oop (RAX) and
    // exception pc (RDX) are dead.
    const int frame_size = 2 /*BP, return address*/ WIN64_ONLY(+ frame::arg_reg_save_area_bytes / BytesPerWord);
    oop_map = new OopMap(frame_size * VMRegImpl::slots_per_word, 0);
    sasm->set_frame_size(frame_size);
    WIN64_ONLY(__ subq(rsp, frame::arg_reg_save_area_bytes));
    break;
  }
  default:  ShouldNotReachHere();
  }

  // verify that only rax, and rdx is valid at this time
  __ invalidate_registers(false, true, true, false, true, true);
  // verify that rax, contains a valid exception
  __ verify_not_null_oop(exception_oop);

#ifdef ASSERT
  // check that fields in JavaThread for exception oop and issuing pc are
  // empty before writing to them
  Label oop_empty;
  __ cmpptr(Address(thread, JavaThread::exception_oop_offset()), NULL_WORD);
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
  __ movptr(Address(thread, JavaThread::exception_pc_offset()),  exception_pc);

  // patch throwing pc into return address (has bci & oop map)
  __ movptr(Address(rbp, 1*BytesPerWord), exception_pc);

  // compute the exception handler.
  // the exception oop and the throwing pc are read from the fields in JavaThread
  int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, exception_handler_for_pc));
  oop_maps->add_gc_map(call_offset, oop_map);

  // rax: handler address
  //      will be the deopt blob if nmethod was deoptimized while we looked up
  //      handler regardless of whether handler existed in the nmethod.

  // only rax, is valid at this time, all other registers have been destroyed by the runtime call
  __ invalidate_registers(false, true, true, true, true, true);

  // patch the return address, this stub will directly return to the exception handler
  __ movptr(Address(rbp, 1*BytesPerWord), rax);

  switch (id) {
  case StubId::c1_forward_exception_id:
  case StubId::c1_handle_exception_nofpu_id:
  case StubId::c1_handle_exception_id:
    // Restore the registers that were saved at the beginning.
    restore_live_registers(sasm, id != StubId::c1_handle_exception_nofpu_id);
    break;
  case StubId::c1_handle_exception_from_callee_id:
    // WIN64_ONLY: No need to add frame::arg_reg_save_area_bytes to SP
    // since we do a leave anyway.

    // Pop the return address.
    __ leave();
    __ pop(rcx);
    __ jmp(rcx);  // jump to exception handler
    break;
  default:  ShouldNotReachHere();
  }

  return oop_maps;
}


void Runtime1::generate_unwind_exception(StubAssembler *sasm) {
  // incoming parameters
  const Register exception_oop = rax;
  // callee-saved copy of exception_oop during runtime call
  const Register exception_oop_callee_saved = r14;
  // other registers used in this stub
  const Register exception_pc = rdx;
  const Register handler_addr = rbx;
  const Register thread = r15_thread;

  if (AbortVMOnException) {
    __ enter();
    save_live_registers(sasm, 2);
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, check_abort_on_vm_exception), rax);
    restore_live_registers(sasm);
    __ leave();
  }

  // verify that only rax, is valid at this time
  __ invalidate_registers(false, true, true, true, true, true);

#ifdef ASSERT
  // check that fields in JavaThread for exception oop and issuing pc are empty
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

  // save exception_oop in callee-saved register to preserve it during runtime calls
  __ verify_not_null_oop(exception_oop);
  __ movptr(exception_oop_callee_saved, exception_oop);

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
  assert(deopt_blob != nullptr, "deoptimization blob must have been created");

  OopMap* oop_map = save_live_registers(sasm, num_rt_args);

  const Register thread = r15_thread;
  // No need to worry about dummy
  __ mov(c_rarg0, thread);
  __ set_last_Java_frame(noreg, rbp, nullptr, rscratch1);
  // do the call
  __ call(RuntimeAddress(target));
  OopMapSet* oop_maps = new OopMapSet();
  oop_maps->add_gc_map(__ offset(), oop_map);
  // verify callee-saved register
#ifdef ASSERT
  guarantee(thread != rax, "change this code");
  __ push_ppx(rax);
  { Label L;
    __ get_thread_slow(rax);
    __ cmpptr(thread, rax);
    __ jcc(Assembler::equal, L);
    __ stop("StubAssembler::call_RT: rdi/r15 not callee saved?");
    __ bind(L);
  }
  __ pop_ppx(rax);
#endif
  __ reset_last_Java_frame(true);

  // check for pending exceptions
  { Label L;
    __ cmpptr(Address(thread, Thread::pending_exception_offset()), NULL_WORD);
    __ jcc(Assembler::equal, L);
    // exception pending => remove activation and forward to exception handler

    __ testptr(rax, rax);                                   // have we deoptimized?
    __ jump_cc(Assembler::equal,
               RuntimeAddress(Runtime1::entry_for(StubId::c1_forward_exception_id)));

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
    __ cmpptr(Address(thread, JavaThread::exception_oop_offset()), NULL_WORD);
    __ jcc(Assembler::equal, oop_empty);
    __ stop("exception oop must be empty");
    __ bind(oop_empty);

    Label pc_empty;
    __ cmpptr(Address(thread, JavaThread::exception_pc_offset()), NULL_WORD);
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

  Label cont;

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


OopMapSet* Runtime1::generate_code_for(StubId id, StubAssembler* sasm) {

  // for better readability
  const bool must_gc_arguments = true;
  const bool dont_gc_arguments = false;

  // default value; overwritten for some optimized stubs that are called from methods that do not use the fpu
  bool save_fpu_registers = true;

  // stub code & info for the different stubs
  OopMapSet* oop_maps = nullptr;
  switch (id) {
    case StubId::c1_forward_exception_id:
      {
        oop_maps = generate_handle_exception(id, sasm);
        __ leave();
        __ ret(0);
      }
      break;

    case StubId::c1_new_instance_id:
    case StubId::c1_fast_new_instance_id:
    case StubId::c1_fast_new_instance_init_check_id:
      {
        Register klass = rdx; // Incoming
        Register obj   = rax; // Result

        if (id == StubId::c1_new_instance_id) {
          __ set_info("new_instance", dont_gc_arguments);
        } else if (id == StubId::c1_fast_new_instance_id) {
          __ set_info("fast new_instance", dont_gc_arguments);
        } else {
          assert(id == StubId::c1_fast_new_instance_init_check_id, "bad StubId");
          __ set_info("fast new_instance init check", dont_gc_arguments);
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

    case StubId::c1_counter_overflow_id:
      {
        Register bci = rax, method = rbx;
        __ enter();
        OopMap* map = save_live_registers(sasm, 3);
        // Retrieve bci
        __ movl(bci, Address(rbp, 2*BytesPerWord));
        // And a pointer to the Method*
        __ movptr(method, Address(rbp, 3*BytesPerWord));
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, counter_overflow), bci, method);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm);
        __ leave();
        __ ret(0);
      }
      break;

    case StubId::c1_new_type_array_id:
    case StubId::c1_new_object_array_id:
      {
        Register length   = rbx; // Incoming
        Register klass    = rdx; // Incoming
        Register obj      = rax; // Result

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
          __ movl(t0, Address(klass, Klass::layout_helper_offset()));
          __ sarl(t0, Klass::_lh_array_tag_shift);
          int tag = ((id == StubId::c1_new_type_array_id)
                     ? Klass::_lh_array_tag_type_value
                     : Klass::_lh_array_tag_obj_value);
          __ cmpl(t0, tag);
          __ jcc(Assembler::equal, ok);
          __ stop("assert(is an array klass)");
          __ should_not_reach_here();
          __ bind(ok);
        }
#endif // ASSERT

        __ enter();
        OopMap* map = save_live_registers(sasm, 3);
        int call_offset;
        if (id == StubId::c1_new_type_array_id) {
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

    case StubId::c1_new_multi_array_id:
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

    case StubId::c1_register_finalizer_id:
      {
        __ set_info("register_finalizer", dont_gc_arguments);

        // This is called via call_runtime so the arguments
        // will be place in C abi locations

        __ verify_oop(c_rarg0);
        __ mov(rax, c_rarg0);

        // load the klass and check the has finalizer flag
        Label register_finalizer;
        Register t = rsi;
        __ load_klass(t, rax, rscratch1);
        __ testb(Address(t, Klass::misc_flags_offset()), KlassFlags::_misc_has_finalizer);
        __ jcc(Assembler::notZero, register_finalizer);
        __ ret(0);

        __ bind(register_finalizer);
        __ enter();
        OopMap* oop_map = save_live_registers(sasm, 2 /*num_rt_args */);
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, SharedRuntime::register_finalizer), rax);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);

        // Now restore all the live registers
        restore_live_registers(sasm);

        __ leave();
        __ ret(0);
      }
      break;

    case StubId::c1_throw_range_check_failed_id:
      { StubFrame f(sasm, "range_check_failed", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_range_check_exception), true);
      }
      break;

    case StubId::c1_throw_index_exception_id:
      { StubFrame f(sasm, "index_range_check_failed", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_index_exception), true);
      }
      break;

    case StubId::c1_throw_div0_exception_id:
      { StubFrame f(sasm, "throw_div0_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_div0_exception), false);
      }
      break;

    case StubId::c1_throw_null_pointer_exception_id:
      { StubFrame f(sasm, "throw_null_pointer_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_null_pointer_exception), false);
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

    case StubId::c1_unwind_exception_id:
      { __ set_info("unwind_exception", dont_gc_arguments);
        // note: no stubframe since we are about to leave the current
        //       activation and we are calling a leaf VM function only.
        generate_unwind_exception(sasm);
      }
      break;

    case StubId::c1_throw_array_store_exception_id:
      { StubFrame f(sasm, "throw_array_store_exception", dont_gc_arguments);
        // tos + 0: link
        //     + 1: return address
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_array_store_exception), true);
      }
      break;

    case StubId::c1_throw_class_cast_exception_id:
      { StubFrame f(sasm, "throw_class_cast_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_class_cast_exception), true);
      }
      break;

    case StubId::c1_throw_incompatible_class_change_error_id:
      { StubFrame f(sasm, "throw_incompatible_class_cast_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_incompatible_class_change_error), false);
      }
      break;

    case StubId::c1_slow_subtype_check_id:
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
        __ push_ppx(rdi);
        __ push_ppx(rsi);
        __ push_ppx(rcx);
        __ push_ppx(rax);

        // This is called by pushing args and not with C abi
        __ movptr(rsi, Address(rsp, (klass_off) * VMRegImpl::stack_slot_size)); // subclass
        __ movptr(rax, Address(rsp, (sup_k_off) * VMRegImpl::stack_slot_size)); // superclass

        Label miss;
        __ check_klass_subtype_slow_path(rsi, rax, rcx, rdi, nullptr, &miss);

        // fallthrough on success:
        __ movptr(Address(rsp, (result_off) * VMRegImpl::stack_slot_size), 1); // result
        __ pop_ppx(rax);
        __ pop_ppx(rcx);
        __ pop_ppx(rsi);
        __ pop_ppx(rdi);
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

    case StubId::c1_is_instance_of_id:
      {
        // Mirror: c_rarg0  (Windows: rcx, SysV: rdi)
        // Object: c_rarg1  (Windows: rdx, SysV: rsi)
        // ObjClass: r9
        // Temps:  rcx, r8, r10, r11
        // Result: rax

        Register klass = r9, obj = c_rarg1, result = rax;
        Register temp0 = rcx, temp1 = r8, temp2 = r10, temp3 = r11;

        // Get the Klass* into r9. c_rarg0 is now dead.
        __ movptr(klass, Address(c_rarg0, java_lang_Class::klass_offset()));

        Label done, is_secondary, same;

        __ xorq(result, result);
        __ testq(klass, klass);
        __ jcc(Assembler::equal, done); // Klass is null

        __ testq(obj, obj);
        __ jcc(Assembler::equal, done); // obj is null

        __ movl(temp0, Address(klass, in_bytes(Klass::super_check_offset_offset())));
        __ cmpl(temp0, in_bytes(Klass::secondary_super_cache_offset()));
        __ jcc(Assembler::equal, is_secondary); // Klass is a secondary superclass

        // Klass is a concrete class
        __ load_klass(temp2, obj, /*tmp*/temp1);
        __ cmpptr(klass, Address(temp2, temp0));
        __ setcc(Assembler::equal, result);
        __ ret(0);

        __ bind(is_secondary);

        __ load_klass(obj, obj, /*tmp*/temp1);

        // This is necessary because I am never in my own secondary_super list.
        __ cmpptr(obj, klass);
        __ jcc(Assembler::equal, same);

        __ lookup_secondary_supers_table_var(obj, klass,
                                             /*temps*/temp0, temp1, temp2, temp3,
                                             result);
        __ testq(result, result);

        __ bind(same);
        __ setcc(Assembler::equal, result);

        __ bind(done);
        __ ret(0);
      }
      break;

    case StubId::c1_monitorenter_nofpu_id:
      save_fpu_registers = false;
      // fall through
    case StubId::c1_monitorenter_id:
      {
        StubFrame f(sasm, "monitorenter", dont_gc_arguments, true /* use_pop_on_epilog */);
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

    case StubId::c1_monitorexit_nofpu_id:
      save_fpu_registers = false;
      // fall through
    case StubId::c1_monitorexit_id:
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

    case StubId::c1_deoptimize_id:
      {
        StubFrame f(sasm, "deoptimize", dont_gc_arguments);
        const int num_rt_args = 2;  // thread, trap_request
        OopMap* oop_map = save_live_registers(sasm, num_rt_args);
        f.load_argument(0, rax);
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, deoptimize), rax);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);
        restore_live_registers(sasm);
        DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
        assert(deopt_blob != nullptr, "deoptimization blob must have been created");
        __ leave();
        __ jump(RuntimeAddress(deopt_blob->unpack_with_reexecution()));
      }
      break;

    case StubId::c1_access_field_patching_id:
      { StubFrame f(sasm, "access_field_patching", dont_gc_arguments);
        // we should set up register map
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, access_field_patching));
      }
      break;

    case StubId::c1_load_klass_patching_id:
      { StubFrame f(sasm, "load_klass_patching", dont_gc_arguments);
        // we should set up register map
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_klass_patching));
      }
      break;

    case StubId::c1_load_mirror_patching_id:
      { StubFrame f(sasm, "load_mirror_patching", dont_gc_arguments);
        // we should set up register map
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_mirror_patching));
      }
      break;

    case StubId::c1_load_appendix_patching_id:
      { StubFrame f(sasm, "load_appendix_patching", dont_gc_arguments);
        // we should set up register map
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_appendix_patching));
      }
      break;

    case StubId::c1_dtrace_object_alloc_id:
      { // rax,: object
        StubFrame f(sasm, "dtrace_object_alloc", dont_gc_arguments);
        // we can't gc here so skip the oopmap but make sure that all
        // the live registers get saved.
        save_live_registers(sasm, 1);

        __ mov(c_rarg0, rax);
        __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, static_cast<int (*)(oopDesc*)>(SharedRuntime::dtrace_object_alloc))));

        restore_live_registers(sasm);
      }
      break;

    case StubId::c1_fpu2long_stub_id:
      {
        Label done;
        __ cvttsd2siq(rax, Address(rsp, wordSize));
        __ cmp64(rax, ExternalAddress((address) StubRoutines::x86::double_sign_flip()));
        __ jccb(Assembler::notEqual, done);
        __ movq(rax, Address(rsp, wordSize));
        __ subptr(rsp, 8);
        __ movq(Address(rsp, 0), rax);
        __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, StubRoutines::x86::d2l_fixup())));
        __ pop(rax);
        __ bind(done);
        __ ret(0);
      }
      break;

    case StubId::c1_predicate_failed_trap_id:
      {
        StubFrame f(sasm, "predicate_failed_trap", dont_gc_arguments);

        OopMap* map = save_live_registers(sasm, 1);

        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, predicate_failed_trap));
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm);
        __ leave();
        DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
        assert(deopt_blob != nullptr, "deoptimization blob must have been created");

        __ jump(RuntimeAddress(deopt_blob->unpack_with_reexecution()));
      }
      break;

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

const char *Runtime1::pd_name_for_address(address entry) {
  return "<unknown function>";
}
