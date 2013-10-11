/*
 * Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "c1/c1_Defs.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "c1/c1_Runtime1.hpp"
#include "interpreter/interpreter.hpp"
#include "nativeInst_sparc.hpp"
#include "oops/compiledICHolder.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "register_sparc.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/macros.hpp"
#include "vmreg_sparc.inline.hpp"

// Implementation of StubAssembler

int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry_point, int number_of_arguments) {
  // for sparc changing the number of arguments doesn't change
  // anything about the frame size so we'll always lie and claim that
  // we are only passing 1 argument.
  set_num_rt_args(1);

  assert_not_delayed();
  // bang stack before going to runtime
  set(-os::vm_page_size() + STACK_BIAS, G3_scratch);
  st(G0, SP, G3_scratch);

  // debugging support
  assert(number_of_arguments >= 0   , "cannot have negative number of arguments");

  set_last_Java_frame(SP, noreg);
  if (VerifyThread)  mov(G2_thread, O0); // about to be smashed; pass early
  save_thread(L7_thread_cache);
  // do the call
  call(entry_point, relocInfo::runtime_call_type);
  if (!VerifyThread) {
    delayed()->mov(G2_thread, O0);  // pass thread as first argument
  } else {
    delayed()->nop();             // (thread already passed)
  }
  int call_offset = offset();  // offset of return address
  restore_thread(L7_thread_cache);
  reset_last_Java_frame();

  // check for pending exceptions
  { Label L;
    Address exception_addr(G2_thread, Thread::pending_exception_offset());
    ld_ptr(exception_addr, Gtemp);
    br_null_short(Gtemp, pt, L);
    Address vm_result_addr(G2_thread, JavaThread::vm_result_offset());
    st_ptr(G0, vm_result_addr);
    Address vm_result_addr_2(G2_thread, JavaThread::vm_result_2_offset());
    st_ptr(G0, vm_result_addr_2);

    if (frame_size() == no_frame_size) {
      // we use O7 linkage so that forward_exception_entry has the issuing PC
      call(StubRoutines::forward_exception_entry(), relocInfo::runtime_call_type);
      delayed()->restore();
    } else if (_stub_id == Runtime1::forward_exception_id) {
      should_not_reach_here();
    } else {
      AddressLiteral exc(Runtime1::entry_for(Runtime1::forward_exception_id));
      jump_to(exc, G4);
      delayed()->nop();
    }
    bind(L);
  }

  // get oop result if there is one and reset the value in the thread
  if (oop_result1->is_valid()) {                    // get oop result if there is one and reset it in the thread
    get_vm_result  (oop_result1);
  } else {
    // be a little paranoid and clear the result
    Address vm_result_addr(G2_thread, JavaThread::vm_result_offset());
    st_ptr(G0, vm_result_addr);
  }

  // get second result if there is one and reset the value in the thread
  if (metadata_result->is_valid()) {
    get_vm_result_2  (metadata_result);
  } else {
    // be a little paranoid and clear the result
    Address vm_result_addr_2(G2_thread, JavaThread::vm_result_2_offset());
    st_ptr(G0, vm_result_addr_2);
  }

  return call_offset;
}


int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, Register arg1) {
  // O0 is reserved for the thread
  mov(arg1, O1);
  return call_RT(oop_result1, metadata_result, entry, 1);
}


int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, Register arg1, Register arg2) {
  // O0 is reserved for the thread
  mov(arg1, O1);
  mov(arg2, O2); assert(arg2 != O1, "smashed argument");
  return call_RT(oop_result1, metadata_result, entry, 2);
}


int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, Register arg1, Register arg2, Register arg3) {
  // O0 is reserved for the thread
  mov(arg1, O1);
  mov(arg2, O2); assert(arg2 != O1,               "smashed argument");
  mov(arg3, O3); assert(arg3 != O1 && arg3 != O2, "smashed argument");
  return call_RT(oop_result1, metadata_result, entry, 3);
}


// Implementation of Runtime1

#define __ sasm->

static int cpu_reg_save_offsets[FrameMap::nof_cpu_regs];
static int fpu_reg_save_offsets[FrameMap::nof_fpu_regs];
static int reg_save_size_in_words;
static int frame_size_in_bytes = -1;

static OopMap* generate_oop_map(StubAssembler* sasm, bool save_fpu_registers) {
  assert(frame_size_in_bytes == __ total_frame_size_in_bytes(reg_save_size_in_words),
         "mismatch in calculation");
  sasm->set_frame_size(frame_size_in_bytes / BytesPerWord);
  int frame_size_in_slots = frame_size_in_bytes / sizeof(jint);
  OopMap* oop_map = new OopMap(frame_size_in_slots, 0);

  int i;
  for (i = 0; i < FrameMap::nof_cpu_regs; i++) {
    Register r = as_Register(i);
    if (r == G1 || r == G3 || r == G4 || r == G5) {
      int sp_offset = cpu_reg_save_offsets[i];
      oop_map->set_callee_saved(VMRegImpl::stack2reg(sp_offset),
                                r->as_VMReg());
    }
  }

  if (save_fpu_registers) {
    for (i = 0; i < FrameMap::nof_fpu_regs; i++) {
      FloatRegister r = as_FloatRegister(i);
      int sp_offset = fpu_reg_save_offsets[i];
      oop_map->set_callee_saved(VMRegImpl::stack2reg(sp_offset),
                                r->as_VMReg());
    }
  }
  return oop_map;
}

static OopMap* save_live_registers(StubAssembler* sasm, bool save_fpu_registers = true) {
  assert(frame_size_in_bytes == __ total_frame_size_in_bytes(reg_save_size_in_words),
         "mismatch in calculation");
  __ save_frame_c1(frame_size_in_bytes);

  // Record volatile registers as callee-save values in an OopMap so their save locations will be
  // propagated to the caller frame's RegisterMap during StackFrameStream construction (needed for
  // deoptimization; see compiledVFrame::create_stack_value).  The caller's I, L and O registers
  // are saved in register windows - I's and L's in the caller's frame and O's in the stub frame
  // (as the stub's I's) when the runtime routine called by the stub creates its frame.
  // OopMap frame sizes are in c2 stack slot sizes (sizeof(jint))

  int i;
  for (i = 0; i < FrameMap::nof_cpu_regs; i++) {
    Register r = as_Register(i);
    if (r == G1 || r == G3 || r == G4 || r == G5) {
      int sp_offset = cpu_reg_save_offsets[i];
      __ st_ptr(r, SP, (sp_offset * BytesPerWord) + STACK_BIAS);
    }
  }

  if (save_fpu_registers) {
    for (i = 0; i < FrameMap::nof_fpu_regs; i++) {
      FloatRegister r = as_FloatRegister(i);
      int sp_offset = fpu_reg_save_offsets[i];
      __ stf(FloatRegisterImpl::S, r, SP, (sp_offset * BytesPerWord) + STACK_BIAS);
    }
  }

  return generate_oop_map(sasm, save_fpu_registers);
}

static void restore_live_registers(StubAssembler* sasm, bool restore_fpu_registers = true) {
  for (int i = 0; i < FrameMap::nof_cpu_regs; i++) {
    Register r = as_Register(i);
    if (r == G1 || r == G3 || r == G4 || r == G5) {
      __ ld_ptr(SP, (cpu_reg_save_offsets[i] * BytesPerWord) + STACK_BIAS, r);
    }
  }

  if (restore_fpu_registers) {
    for (int i = 0; i < FrameMap::nof_fpu_regs; i++) {
      FloatRegister r = as_FloatRegister(i);
      __ ldf(FloatRegisterImpl::S, SP, (fpu_reg_save_offsets[i] * BytesPerWord) + STACK_BIAS, r);
    }
  }
}


void Runtime1::initialize_pd() {
  // compute word offsets from SP at which live (non-windowed) registers are captured by stub routines
  //
  // A stub routine will have a frame that is at least large enough to hold
  // a register window save area (obviously) and the volatile g registers
  // and floating registers. A user of save_live_registers can have a frame
  // that has more scratch area in it (although typically they will use L-regs).
  // in that case the frame will look like this (stack growing down)
  //
  // FP -> |             |
  //       | scratch mem |
  //       |   "      "  |
  //       --------------
  //       | float regs  |
  //       |   "    "    |
  //       ---------------
  //       | G regs      |
  //       | "  "        |
  //       ---------------
  //       | abi reg.    |
  //       | window save |
  //       | area        |
  // SP -> ---------------
  //
  int i;
  int sp_offset = round_to(frame::register_save_words, 2); //  start doubleword aligned

  // only G int registers are saved explicitly; others are found in register windows
  for (i = 0; i < FrameMap::nof_cpu_regs; i++) {
    Register r = as_Register(i);
    if (r == G1 || r == G3 || r == G4 || r == G5) {
      cpu_reg_save_offsets[i] = sp_offset;
      sp_offset++;
    }
  }

  // all float registers are saved explicitly
  assert(FrameMap::nof_fpu_regs == 32, "double registers not handled here");
  for (i = 0; i < FrameMap::nof_fpu_regs; i++) {
    fpu_reg_save_offsets[i] = sp_offset;
    sp_offset++;
  }
  reg_save_size_in_words = sp_offset - frame::memory_parameter_word_sp_offset;
  // this should match assembler::total_frame_size_in_bytes, which
  // isn't callable from this context.  It's checked by an assert when
  // it's used though.
  frame_size_in_bytes = align_size_up(sp_offset * wordSize, 8);
}


OopMapSet* Runtime1::generate_exception_throw(StubAssembler* sasm, address target, bool has_argument) {
  // make a frame and preserve the caller's caller-save registers
  OopMap* oop_map = save_live_registers(sasm);
  int call_offset;
  if (!has_argument) {
    call_offset = __ call_RT(noreg, noreg, target);
  } else {
    call_offset = __ call_RT(noreg, noreg, target, G4);
  }
  OopMapSet* oop_maps = new OopMapSet();
  oop_maps->add_gc_map(call_offset, oop_map);

  __ should_not_reach_here();
  return oop_maps;
}


OopMapSet* Runtime1::generate_stub_call(StubAssembler* sasm, Register result, address target,
                                        Register arg1, Register arg2, Register arg3) {
  // make a frame and preserve the caller's caller-save registers
  OopMap* oop_map = save_live_registers(sasm);

  int call_offset;
  if (arg1 == noreg) {
    call_offset = __ call_RT(result, noreg, target);
  } else if (arg2 == noreg) {
    call_offset = __ call_RT(result, noreg, target, arg1);
  } else if (arg3 == noreg) {
    call_offset = __ call_RT(result, noreg, target, arg1, arg2);
  } else {
    call_offset = __ call_RT(result, noreg, target, arg1, arg2, arg3);
  }
  OopMapSet* oop_maps = NULL;

  oop_maps = new OopMapSet();
  oop_maps->add_gc_map(call_offset, oop_map);
  restore_live_registers(sasm);

  __ ret();
  __ delayed()->restore();

  return oop_maps;
}


OopMapSet* Runtime1::generate_patching(StubAssembler* sasm, address target) {
  // make a frame and preserve the caller's caller-save registers
  OopMap* oop_map = save_live_registers(sasm);

  // call the runtime patching routine, returns non-zero if nmethod got deopted.
  int call_offset = __ call_RT(noreg, noreg, target);
  OopMapSet* oop_maps = new OopMapSet();
  oop_maps->add_gc_map(call_offset, oop_map);

  // re-execute the patched instruction or, if the nmethod was deoptmized, return to the
  // deoptimization handler entry that will cause re-execution of the current bytecode
  DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
  assert(deopt_blob != NULL, "deoptimization blob must have been created");

  Label no_deopt;
  __ br_null_short(O0, Assembler::pt, no_deopt);

  // return to the deoptimization handler entry for unpacking and rexecute
  // if we simply returned the we'd deopt as if any call we patched had just
  // returned.

  restore_live_registers(sasm);

  AddressLiteral dest(deopt_blob->unpack_with_reexecution());
  __ jump_to(dest, O0);
  __ delayed()->restore();

  __ bind(no_deopt);
  restore_live_registers(sasm);
  __ ret();
  __ delayed()->restore();

  return oop_maps;
}

OopMapSet* Runtime1::generate_code_for(StubID id, StubAssembler* sasm) {

  OopMapSet* oop_maps = NULL;
  // for better readability
  const bool must_gc_arguments = true;
  const bool dont_gc_arguments = false;

  // stub code & info for the different stubs
  switch (id) {
    case forward_exception_id:
      {
        oop_maps = generate_handle_exception(id, sasm);
      }
      break;

    case new_instance_id:
    case fast_new_instance_id:
    case fast_new_instance_init_check_id:
      {
        Register G5_klass = G5; // Incoming
        Register O0_obj   = O0; // Outgoing

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
          Register G1_obj_size = G1;
          Register G3_t1 = G3;
          Register G4_t2 = G4;
          assert_different_registers(G5_klass, G1_obj_size, G3_t1, G4_t2);

          // Push a frame since we may do dtrace notification for the
          // allocation which requires calling out and we don't want
          // to stomp the real return address.
          __ save_frame(0);

          if (id == fast_new_instance_init_check_id) {
            // make sure the klass is initialized
            __ ldub(G5_klass, in_bytes(InstanceKlass::init_state_offset()), G3_t1);
            __ cmp_and_br_short(G3_t1, InstanceKlass::fully_initialized, Assembler::notEqual, Assembler::pn, slow_path);
          }
#ifdef ASSERT
          // assert object can be fast path allocated
          {
            Label ok, not_ok;
          __ ld(G5_klass, in_bytes(Klass::layout_helper_offset()), G1_obj_size);
          // make sure it's an instance (LH > 0)
          __ cmp_and_br_short(G1_obj_size, 0, Assembler::lessEqual, Assembler::pn, not_ok);
          __ btst(Klass::_lh_instance_slow_path_bit, G1_obj_size);
          __ br(Assembler::zero, false, Assembler::pn, ok);
          __ delayed()->nop();
          __ bind(not_ok);
          __ stop("assert(can be fast path allocated)");
          __ should_not_reach_here();
          __ bind(ok);
          }
#endif // ASSERT
          // if we got here then the TLAB allocation failed, so try
          // refilling the TLAB or allocating directly from eden.
          Label retry_tlab, try_eden;
          __ tlab_refill(retry_tlab, try_eden, slow_path); // preserves G5_klass

          __ bind(retry_tlab);

          // get the instance size
          __ ld(G5_klass, in_bytes(Klass::layout_helper_offset()), G1_obj_size);

          __ tlab_allocate(O0_obj, G1_obj_size, 0, G3_t1, slow_path);

          __ initialize_object(O0_obj, G5_klass, G1_obj_size, 0, G3_t1, G4_t2);
          __ verify_oop(O0_obj);
          __ mov(O0, I0);
          __ ret();
          __ delayed()->restore();

          __ bind(try_eden);
          // get the instance size
          __ ld(G5_klass, in_bytes(Klass::layout_helper_offset()), G1_obj_size);
          __ eden_allocate(O0_obj, G1_obj_size, 0, G3_t1, G4_t2, slow_path);
          __ incr_allocated_bytes(G1_obj_size, G3_t1, G4_t2);

          __ initialize_object(O0_obj, G5_klass, G1_obj_size, 0, G3_t1, G4_t2);
          __ verify_oop(O0_obj);
          __ mov(O0, I0);
          __ ret();
          __ delayed()->restore();

          __ bind(slow_path);

          // pop this frame so generate_stub_call can push it's own
          __ restore();
        }

        oop_maps = generate_stub_call(sasm, I0, CAST_FROM_FN_PTR(address, new_instance), G5_klass);
        // I0->O0: new instance
      }

      break;

    case counter_overflow_id:
        // G4 contains bci, G5 contains method
      oop_maps = generate_stub_call(sasm, noreg, CAST_FROM_FN_PTR(address, counter_overflow), G4, G5);
      break;

    case new_type_array_id:
    case new_object_array_id:
      {
        Register G5_klass = G5; // Incoming
        Register G4_length = G4; // Incoming
        Register O0_obj   = O0; // Outgoing

        Address klass_lh(G5_klass, Klass::layout_helper_offset());
        assert(Klass::_lh_header_size_shift % BitsPerByte == 0, "bytewise");
        assert(Klass::_lh_header_size_mask == 0xFF, "bytewise");
        // Use this offset to pick out an individual byte of the layout_helper:
        const int klass_lh_header_size_offset = ((BytesPerInt - 1)  // 3 - 2 selects byte {0,1,0,0}
                                                 - Klass::_lh_header_size_shift / BitsPerByte);

        if (id == new_type_array_id) {
          __ set_info("new_type_array", dont_gc_arguments);
        } else {
          __ set_info("new_object_array", dont_gc_arguments);
        }

#ifdef ASSERT
        // assert object type is really an array of the proper kind
        {
          Label ok;
          Register G3_t1 = G3;
          __ ld(klass_lh, G3_t1);
          __ sra(G3_t1, Klass::_lh_array_tag_shift, G3_t1);
          int tag = ((id == new_type_array_id)
                     ? Klass::_lh_array_tag_type_value
                     : Klass::_lh_array_tag_obj_value);
          __ cmp_and_brx_short(G3_t1, tag, Assembler::equal, Assembler::pt, ok);
          __ stop("assert(is an array klass)");
          __ should_not_reach_here();
          __ bind(ok);
        }
#endif // ASSERT

        if (UseTLAB && FastTLABRefill) {
          Label slow_path;
          Register G1_arr_size = G1;
          Register G3_t1 = G3;
          Register O1_t2 = O1;
          assert_different_registers(G5_klass, G4_length, G1_arr_size, G3_t1, O1_t2);

          // check that array length is small enough for fast path
          __ set(C1_MacroAssembler::max_array_allocation_length, G3_t1);
          __ cmp_and_br_short(G4_length, G3_t1, Assembler::greaterUnsigned, Assembler::pn, slow_path);

          // if we got here then the TLAB allocation failed, so try
          // refilling the TLAB or allocating directly from eden.
          Label retry_tlab, try_eden;
          __ tlab_refill(retry_tlab, try_eden, slow_path); // preserves G4_length and G5_klass

          __ bind(retry_tlab);

          // get the allocation size: (length << (layout_helper & 0x1F)) + header_size
          __ ld(klass_lh, G3_t1);
          __ sll(G4_length, G3_t1, G1_arr_size);
          __ srl(G3_t1, Klass::_lh_header_size_shift, G3_t1);
          __ and3(G3_t1, Klass::_lh_header_size_mask, G3_t1);
          __ add(G1_arr_size, G3_t1, G1_arr_size);
          __ add(G1_arr_size, MinObjAlignmentInBytesMask, G1_arr_size);  // align up
          __ and3(G1_arr_size, ~MinObjAlignmentInBytesMask, G1_arr_size);

          __ tlab_allocate(O0_obj, G1_arr_size, 0, G3_t1, slow_path);  // preserves G1_arr_size

          __ initialize_header(O0_obj, G5_klass, G4_length, G3_t1, O1_t2);
          __ ldub(klass_lh, G3_t1, klass_lh_header_size_offset);
          __ sub(G1_arr_size, G3_t1, O1_t2);  // body length
          __ add(O0_obj, G3_t1, G3_t1);       // body start
          __ initialize_body(G3_t1, O1_t2);
          __ verify_oop(O0_obj);
          __ retl();
          __ delayed()->nop();

          __ bind(try_eden);
          // get the allocation size: (length << (layout_helper & 0x1F)) + header_size
          __ ld(klass_lh, G3_t1);
          __ sll(G4_length, G3_t1, G1_arr_size);
          __ srl(G3_t1, Klass::_lh_header_size_shift, G3_t1);
          __ and3(G3_t1, Klass::_lh_header_size_mask, G3_t1);
          __ add(G1_arr_size, G3_t1, G1_arr_size);
          __ add(G1_arr_size, MinObjAlignmentInBytesMask, G1_arr_size);
          __ and3(G1_arr_size, ~MinObjAlignmentInBytesMask, G1_arr_size);

          __ eden_allocate(O0_obj, G1_arr_size, 0, G3_t1, O1_t2, slow_path);  // preserves G1_arr_size
          __ incr_allocated_bytes(G1_arr_size, G3_t1, O1_t2);

          __ initialize_header(O0_obj, G5_klass, G4_length, G3_t1, O1_t2);
          __ ldub(klass_lh, G3_t1, klass_lh_header_size_offset);
          __ sub(G1_arr_size, G3_t1, O1_t2);  // body length
          __ add(O0_obj, G3_t1, G3_t1);       // body start
          __ initialize_body(G3_t1, O1_t2);
          __ verify_oop(O0_obj);
          __ retl();
          __ delayed()->nop();

          __ bind(slow_path);
        }

        if (id == new_type_array_id) {
          oop_maps = generate_stub_call(sasm, I0, CAST_FROM_FN_PTR(address, new_type_array), G5_klass, G4_length);
        } else {
          oop_maps = generate_stub_call(sasm, I0, CAST_FROM_FN_PTR(address, new_object_array), G5_klass, G4_length);
        }
        // I0 -> O0: new array
      }
      break;

    case new_multi_array_id:
      { // O0: klass
        // O1: rank
        // O2: address of 1st dimension
        __ set_info("new_multi_array", dont_gc_arguments);
        oop_maps = generate_stub_call(sasm, I0, CAST_FROM_FN_PTR(address, new_multi_array), I0, I1, I2);
        // I0 -> O0: new multi array
      }
      break;

    case register_finalizer_id:
      {
        __ set_info("register_finalizer", dont_gc_arguments);

        // load the klass and check the has finalizer flag
        Label register_finalizer;
        Register t = O1;
        __ load_klass(O0, t);
        __ ld(t, in_bytes(Klass::access_flags_offset()), t);
        __ set(JVM_ACC_HAS_FINALIZER, G3);
        __ andcc(G3, t, G0);
        __ br(Assembler::notZero, false, Assembler::pt, register_finalizer);
        __ delayed()->nop();

        // do a leaf return
        __ retl();
        __ delayed()->nop();

        __ bind(register_finalizer);
        OopMap* oop_map = save_live_registers(sasm);
        int call_offset = __ call_RT(noreg, noreg,
                                     CAST_FROM_FN_PTR(address, SharedRuntime::register_finalizer), I0);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);

        // Now restore all the live registers
        restore_live_registers(sasm);

        __ ret();
        __ delayed()->restore();
      }
      break;

    case throw_range_check_failed_id:
      { __ set_info("range_check_failed", dont_gc_arguments); // arguments will be discarded
        // G4: index
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_range_check_exception), true);
      }
      break;

    case throw_index_exception_id:
      { __ set_info("index_range_check_failed", dont_gc_arguments); // arguments will be discarded
        // G4: index
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_index_exception), true);
      }
      break;

    case throw_div0_exception_id:
      { __ set_info("throw_div0_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_div0_exception), false);
      }
      break;

    case throw_null_pointer_exception_id:
      { __ set_info("throw_null_pointer_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_null_pointer_exception), false);
      }
      break;

    case handle_exception_id:
      { __ set_info("handle_exception", dont_gc_arguments);
        oop_maps = generate_handle_exception(id, sasm);
      }
      break;

    case handle_exception_from_callee_id:
      { __ set_info("handle_exception_from_callee", dont_gc_arguments);
        oop_maps = generate_handle_exception(id, sasm);
      }
      break;

    case unwind_exception_id:
      {
        // O0: exception
        // I7: address of call to this method

        __ set_info("unwind_exception", dont_gc_arguments);
        __ mov(Oexception, Oexception->after_save());
        __ add(I7, frame::pc_return_offset, Oissuing_pc->after_save());

        __ call_VM_leaf(L7_thread_cache, CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address),
                        G2_thread, Oissuing_pc->after_save());
        __ verify_not_null_oop(Oexception->after_save());

        // Restore SP from L7 if the exception PC is a method handle call site.
        __ mov(O0, G5);  // Save the target address.
        __ lduw(Address(G2_thread, JavaThread::is_method_handle_return_offset()), L0);
        __ tst(L0);  // Condition codes are preserved over the restore.
        __ restore();

        __ jmp(G5, 0);
        __ delayed()->movcc(Assembler::notZero, false, Assembler::icc, L7_mh_SP_save, SP);  // Restore SP if required.
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
        // G4: object
        __ set_info("throw_class_cast_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_class_cast_exception), true);
      }
      break;

    case throw_incompatible_class_change_error_id:
      {
        __ set_info("throw_incompatible_class_cast_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_incompatible_class_change_error), false);
      }
      break;

    case slow_subtype_check_id:
      { // Support for uint StubRoutine::partial_subtype_check( Klass sub, Klass super );
        // Arguments :
        //
        //      ret  : G3
        //      sub  : G3, argument, destroyed
        //      super: G1, argument, not changed
        //      raddr: O7, blown by call
        Label miss;

        __ save_frame(0);               // Blow no registers!

        __ check_klass_subtype_slow_path(G3, G1, L0, L1, L2, L4, NULL, &miss);

        __ mov(1, G3);
        __ ret();                       // Result in G5 is 'true'
        __ delayed()->restore();        // free copy or add can go here

        __ bind(miss);
        __ mov(0, G3);
        __ ret();                       // Result in G5 is 'false'
        __ delayed()->restore();        // free copy or add can go here
      }

    case monitorenter_nofpu_id:
    case monitorenter_id:
      { // G4: object
        // G5: lock address
        __ set_info("monitorenter", dont_gc_arguments);

        int save_fpu_registers = (id == monitorenter_id);
        // make a frame and preserve the caller's caller-save registers
        OopMap* oop_map = save_live_registers(sasm, save_fpu_registers);

        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, monitorenter), G4, G5);

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);
        restore_live_registers(sasm, save_fpu_registers);

        __ ret();
        __ delayed()->restore();
      }
      break;

    case monitorexit_nofpu_id:
    case monitorexit_id:
      { // G4: lock address
        // note: really a leaf routine but must setup last java sp
        //       => use call_RT for now (speed can be improved by
        //       doing last java sp setup manually)
        __ set_info("monitorexit", dont_gc_arguments);

        int save_fpu_registers = (id == monitorexit_id);
        // make a frame and preserve the caller's caller-save registers
        OopMap* oop_map = save_live_registers(sasm, save_fpu_registers);

        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, monitorexit), G4);

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);
        restore_live_registers(sasm, save_fpu_registers);

        __ ret();
        __ delayed()->restore();
      }
      break;

    case deoptimize_id:
      {
        __ set_info("deoptimize", dont_gc_arguments);
        OopMap* oop_map = save_live_registers(sasm);
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, deoptimize));
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);
        restore_live_registers(sasm);
        DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
        assert(deopt_blob != NULL, "deoptimization blob must have been created");
        AddressLiteral dest(deopt_blob->unpack_with_reexecution());
        __ jump_to(dest, O0);
        __ delayed()->restore();
      }
      break;

    case access_field_patching_id:
      { __ set_info("access_field_patching", dont_gc_arguments);
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, access_field_patching));
      }
      break;

    case load_klass_patching_id:
      { __ set_info("load_klass_patching", dont_gc_arguments);
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_klass_patching));
      }
      break;

    case load_mirror_patching_id:
      { __ set_info("load_mirror_patching", dont_gc_arguments);
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_mirror_patching));
      }
      break;

    case load_appendix_patching_id:
      { __ set_info("load_appendix_patching", dont_gc_arguments);
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_appendix_patching));
      }
      break;

    case dtrace_object_alloc_id:
      { // O0: object
        __ set_info("dtrace_object_alloc", dont_gc_arguments);
        // we can't gc here so skip the oopmap but make sure that all
        // the live registers get saved.
        save_live_registers(sasm);

        __ save_thread(L7_thread_cache);
        __ call(CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_object_alloc),
                relocInfo::runtime_call_type);
        __ delayed()->mov(I0, O0);
        __ restore_thread(L7_thread_cache);

        restore_live_registers(sasm);
        __ ret();
        __ delayed()->restore();
      }
      break;

#if INCLUDE_ALL_GCS
    case g1_pre_barrier_slow_id:
      { // G4: previous value of memory
        BarrierSet* bs = Universe::heap()->barrier_set();
        if (bs->kind() != BarrierSet::G1SATBCTLogging) {
          __ save_frame(0);
          __ set((int)id, O1);
          __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, unimplemented_entry), I0);
          __ should_not_reach_here();
          break;
        }

        __ set_info("g1_pre_barrier_slow_id", dont_gc_arguments);

        Register pre_val = G4;
        Register tmp  = G1_scratch;
        Register tmp2 = G3_scratch;

        Label refill, restart;
        bool with_frame = false; // I don't know if we can do with-frame.
        int satb_q_index_byte_offset =
          in_bytes(JavaThread::satb_mark_queue_offset() +
                   PtrQueue::byte_offset_of_index());
        int satb_q_buf_byte_offset =
          in_bytes(JavaThread::satb_mark_queue_offset() +
                   PtrQueue::byte_offset_of_buf());

        __ bind(restart);
        // Load the index into the SATB buffer. PtrQueue::_index is a
        // size_t so ld_ptr is appropriate
        __ ld_ptr(G2_thread, satb_q_index_byte_offset, tmp);

        // index == 0?
        __ cmp_and_brx_short(tmp, G0, Assembler::equal, Assembler::pn, refill);

        __ ld_ptr(G2_thread, satb_q_buf_byte_offset, tmp2);
        __ sub(tmp, oopSize, tmp);

        __ st_ptr(pre_val, tmp2, tmp);  // [_buf + index] := <address_of_card>
        // Use return-from-leaf
        __ retl();
        __ delayed()->st_ptr(tmp, G2_thread, satb_q_index_byte_offset);

        __ bind(refill);
        __ save_frame(0);

        __ mov(pre_val, L0);
        __ mov(tmp,     L1);
        __ mov(tmp2,    L2);

        __ call_VM_leaf(L7_thread_cache,
                        CAST_FROM_FN_PTR(address,
                                         SATBMarkQueueSet::handle_zero_index_for_thread),
                                         G2_thread);

        __ mov(L0, pre_val);
        __ mov(L1, tmp);
        __ mov(L2, tmp2);

        __ br(Assembler::always, /*annul*/false, Assembler::pt, restart);
        __ delayed()->restore();
      }
      break;

    case g1_post_barrier_slow_id:
      {
        BarrierSet* bs = Universe::heap()->barrier_set();
        if (bs->kind() != BarrierSet::G1SATBCTLogging) {
          __ save_frame(0);
          __ set((int)id, O1);
          __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, unimplemented_entry), I0);
          __ should_not_reach_here();
          break;
        }

        __ set_info("g1_post_barrier_slow_id", dont_gc_arguments);

        Register addr = G4;
        Register cardtable = G5;
        Register tmp  = G1_scratch;
        Register tmp2 = G3_scratch;
        jbyte* byte_map_base = ((CardTableModRefBS*)bs)->byte_map_base;

        Label not_already_dirty, restart, refill;

#ifdef _LP64
        __ srlx(addr, CardTableModRefBS::card_shift, addr);
#else
        __ srl(addr, CardTableModRefBS::card_shift, addr);
#endif

        AddressLiteral rs(byte_map_base);
        __ set(rs, cardtable);         // cardtable := <card table base>
        __ ldub(addr, cardtable, tmp); // tmp := [addr + cardtable]

        assert(CardTableModRefBS::dirty_card_val() == 0, "otherwise check this code");
        __ cmp_and_br_short(tmp, G0, Assembler::notEqual, Assembler::pt, not_already_dirty);

        // We didn't take the branch, so we're already dirty: return.
        // Use return-from-leaf
        __ retl();
        __ delayed()->nop();

        // Not dirty.
        __ bind(not_already_dirty);

        // Get cardtable + tmp into a reg by itself
        __ add(addr, cardtable, tmp2);

        // First, dirty it.
        __ stb(G0, tmp2, 0);  // [cardPtr] := 0  (i.e., dirty).

        Register tmp3 = cardtable;
        Register tmp4 = tmp;

        // these registers are now dead
        addr = cardtable = tmp = noreg;

        int dirty_card_q_index_byte_offset =
          in_bytes(JavaThread::dirty_card_queue_offset() +
                   PtrQueue::byte_offset_of_index());
        int dirty_card_q_buf_byte_offset =
          in_bytes(JavaThread::dirty_card_queue_offset() +
                   PtrQueue::byte_offset_of_buf());

        __ bind(restart);

        // Get the index into the update buffer. PtrQueue::_index is
        // a size_t so ld_ptr is appropriate here.
        __ ld_ptr(G2_thread, dirty_card_q_index_byte_offset, tmp3);

        // index == 0?
        __ cmp_and_brx_short(tmp3, G0, Assembler::equal,  Assembler::pn, refill);

        __ ld_ptr(G2_thread, dirty_card_q_buf_byte_offset, tmp4);
        __ sub(tmp3, oopSize, tmp3);

        __ st_ptr(tmp2, tmp4, tmp3);  // [_buf + index] := <address_of_card>
        // Use return-from-leaf
        __ retl();
        __ delayed()->st_ptr(tmp3, G2_thread, dirty_card_q_index_byte_offset);

        __ bind(refill);
        __ save_frame(0);

        __ mov(tmp2, L0);
        __ mov(tmp3, L1);
        __ mov(tmp4, L2);

        __ call_VM_leaf(L7_thread_cache,
                        CAST_FROM_FN_PTR(address,
                                         DirtyCardQueueSet::handle_zero_index_for_thread),
                                         G2_thread);

        __ mov(L0, tmp2);
        __ mov(L1, tmp3);
        __ mov(L2, tmp4);

        __ br(Assembler::always, /*annul*/false, Assembler::pt, restart);
        __ delayed()->restore();
      }
      break;
#endif // INCLUDE_ALL_GCS

    case predicate_failed_trap_id:
      {
        __ set_info("predicate_failed_trap", dont_gc_arguments);
        OopMap* oop_map = save_live_registers(sasm);

        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, predicate_failed_trap));

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);

        DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
        assert(deopt_blob != NULL, "deoptimization blob must have been created");
        restore_live_registers(sasm);

        AddressLiteral dest(deopt_blob->unpack_with_reexecution());
        __ jump_to(dest, O0);
        __ delayed()->restore();
      }
      break;

    default:
      { __ set_info("unimplemented entry", dont_gc_arguments);
        __ save_frame(0);
        __ set((int)id, O1);
        __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, unimplemented_entry), O1);
        __ should_not_reach_here();
      }
      break;
  }
  return oop_maps;
}


OopMapSet* Runtime1::generate_handle_exception(StubID id, StubAssembler* sasm) {
  __ block_comment("generate_handle_exception");

  // Save registers, if required.
  OopMapSet* oop_maps = new OopMapSet();
  OopMap* oop_map = NULL;
  switch (id) {
  case forward_exception_id:
    // We're handling an exception in the context of a compiled frame.
    // The registers have been saved in the standard places.  Perform
    // an exception lookup in the caller and dispatch to the handler
    // if found.  Otherwise unwind and dispatch to the callers
    // exception handler.
     oop_map = generate_oop_map(sasm, true);

     // transfer the pending exception to the exception_oop
     __ ld_ptr(G2_thread, in_bytes(JavaThread::pending_exception_offset()), Oexception);
     __ ld_ptr(Oexception, 0, G0);
     __ st_ptr(G0, G2_thread, in_bytes(JavaThread::pending_exception_offset()));
     __ add(I7, frame::pc_return_offset, Oissuing_pc);
    break;
  case handle_exception_id:
    // At this point all registers MAY be live.
    oop_map = save_live_registers(sasm);
    __ mov(Oexception->after_save(),  Oexception);
    __ mov(Oissuing_pc->after_save(), Oissuing_pc);
    break;
  case handle_exception_from_callee_id:
    // At this point all registers except exception oop (Oexception)
    // and exception pc (Oissuing_pc) are dead.
    oop_map = new OopMap(frame_size_in_bytes / sizeof(jint), 0);
    sasm->set_frame_size(frame_size_in_bytes / BytesPerWord);
    __ save_frame_c1(frame_size_in_bytes);
    __ mov(Oexception->after_save(),  Oexception);
    __ mov(Oissuing_pc->after_save(), Oissuing_pc);
    break;
  default:  ShouldNotReachHere();
  }

  __ verify_not_null_oop(Oexception);

#ifdef ASSERT
  // check that fields in JavaThread for exception oop and issuing pc are
  // empty before writing to them
  Label oop_empty;
  Register scratch = I7;  // We can use I7 here because it's overwritten later anyway.
  __ ld_ptr(Address(G2_thread, JavaThread::exception_oop_offset()), scratch);
  __ br_null(scratch, false, Assembler::pt, oop_empty);
  __ delayed()->nop();
  __ stop("exception oop already set");
  __ bind(oop_empty);

  Label pc_empty;
  __ ld_ptr(Address(G2_thread, JavaThread::exception_pc_offset()), scratch);
  __ br_null(scratch, false, Assembler::pt, pc_empty);
  __ delayed()->nop();
  __ stop("exception pc already set");
  __ bind(pc_empty);
#endif

  // save the exception and issuing pc in the thread
  __ st_ptr(Oexception,  G2_thread, in_bytes(JavaThread::exception_oop_offset()));
  __ st_ptr(Oissuing_pc, G2_thread, in_bytes(JavaThread::exception_pc_offset()));

  // use the throwing pc as the return address to lookup (has bci & oop map)
  __ mov(Oissuing_pc, I7);
  __ sub(I7, frame::pc_return_offset, I7);
  int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, exception_handler_for_pc));
  oop_maps->add_gc_map(call_offset, oop_map);

  // Note: if nmethod has been deoptimized then regardless of
  // whether it had a handler or not we will deoptimize
  // by entering the deopt blob with a pending exception.

  // Restore the registers that were saved at the beginning, remove
  // the frame and jump to the exception handler.
  switch (id) {
  case forward_exception_id:
  case handle_exception_id:
    restore_live_registers(sasm);
    __ jmp(O0, 0);
    __ delayed()->restore();
    break;
  case handle_exception_from_callee_id:
    // Restore SP from L7 if the exception PC is a method handle call site.
    __ mov(O0, G5);  // Save the target address.
    __ lduw(Address(G2_thread, JavaThread::is_method_handle_return_offset()), L0);
    __ tst(L0);  // Condition codes are preserved over the restore.
    __ restore();

    __ jmp(G5, 0);  // jump to the exception handler
    __ delayed()->movcc(Assembler::notZero, false, Assembler::icc, L7_mh_SP_save, SP);  // Restore SP if required.
    break;
  default:  ShouldNotReachHere();
  }

  return oop_maps;
}


#undef __

const char *Runtime1::pd_name_for_address(address entry) {
  return "<unknown function>";
}
