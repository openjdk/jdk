/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "interp_masm_ppc_64.hpp"
#include "interpreter/interpreterRuntime.hpp"

#ifdef PRODUCT
#define BLOCK_COMMENT(str) // nothing
#else
#define BLOCK_COMMENT(str) block_comment(str)
#endif

void InterpreterMacroAssembler::null_check_throw(Register a, int offset, Register temp_reg) {
#ifdef CC_INTERP
  address exception_entry = StubRoutines::throw_NullPointerException_at_call_entry();
#else
  address exception_entry = Interpreter::throw_NullPointerException_entry();
#endif
  MacroAssembler::null_check_throw(a, offset, temp_reg, exception_entry);
}

// Lock object
//
// Registers alive
//   monitor - Address of the BasicObjectLock to be used for locking,
//             which must be initialized with the object to lock.
//   object  - Address of the object to be locked.
//
void InterpreterMacroAssembler::lock_object(Register monitor, Register object) {
  if (UseHeavyMonitors) {
    call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::monitorenter),
            monitor, /*check_for_exceptions=*/true CC_INTERP_ONLY(&& false));
  } else {
    // template code:
    //
    // markOop displaced_header = obj->mark().set_unlocked();
    // monitor->lock()->set_displaced_header(displaced_header);
    // if (Atomic::cmpxchg_ptr(/*ex=*/monitor, /*addr*/obj->mark_addr(), /*cmp*/displaced_header) == displaced_header) {
    //   // We stored the monitor address into the object's mark word.
    // } else if (THREAD->is_lock_owned((address)displaced_header))
    //   // Simple recursive case.
    //   monitor->lock()->set_displaced_header(NULL);
    // } else {
    //   // Slow path.
    //   InterpreterRuntime::monitorenter(THREAD, monitor);
    // }

    const Register displaced_header = R7_ARG5;
    const Register object_mark_addr = R8_ARG6;
    const Register current_header   = R9_ARG7;
    const Register tmp              = R10_ARG8;

    Label done;
    Label cas_failed, slow_case;

    assert_different_registers(displaced_header, object_mark_addr, current_header, tmp);


    // markOop displaced_header = obj->mark().set_unlocked();

    // Load markOop from object into displaced_header.
    ld(displaced_header, oopDesc::mark_offset_in_bytes(), object);

    if (UseBiasedLocking) {
      biased_locking_enter(CCR0, object, displaced_header, tmp, current_header, done, &slow_case);
    }

    // Set displaced_header to be (markOop of object | UNLOCK_VALUE).
    ori(displaced_header, displaced_header, markOopDesc::unlocked_value);


    // monitor->lock()->set_displaced_header(displaced_header);

    // Initialize the box (Must happen before we update the object mark!).
    std(displaced_header, BasicObjectLock::lock_offset_in_bytes() +
        BasicLock::displaced_header_offset_in_bytes(), monitor);

    // if (Atomic::cmpxchg_ptr(/*ex=*/monitor, /*addr*/obj->mark_addr(), /*cmp*/displaced_header) == displaced_header) {

    // Store stack address of the BasicObjectLock (this is monitor) into object.
    addi(object_mark_addr, object, oopDesc::mark_offset_in_bytes());

    // Must fence, otherwise, preceding store(s) may float below cmpxchg.
    // CmpxchgX sets CCR0 to cmpX(current, displaced).
    fence(); // TODO: replace by MacroAssembler::MemBarRel | MacroAssembler::MemBarAcq ?
    cmpxchgd(/*flag=*/CCR0,
             /*current_value=*/current_header,
             /*compare_value=*/displaced_header, /*exchange_value=*/monitor,
             /*where=*/object_mark_addr,
             MacroAssembler::MemBarRel | MacroAssembler::MemBarAcq,
             MacroAssembler::cmpxchgx_hint_acquire_lock(),
             noreg,
             &cas_failed);

    // If the compare-and-exchange succeeded, then we found an unlocked
    // object and we have now locked it.
    b(done);
    bind(cas_failed);

    // } else if (THREAD->is_lock_owned((address)displaced_header))
    //   // Simple recursive case.
    //   monitor->lock()->set_displaced_header(NULL);

    // We did not see an unlocked object so try the fast recursive case.

    // Check if owner is self by comparing the value in the markOop of object
    // (current_header) with the stack pointer.
    sub(current_header, current_header, R1_SP);

    assert(os::vm_page_size() > 0xfff, "page size too small - change the constant");
    load_const_optimized(tmp,
                         (address) (~(os::vm_page_size()-1) |
                                    markOopDesc::lock_mask_in_place));

    and_(R0/*==0?*/, current_header, tmp);
    // If condition is true we are done and hence we can store 0 in the displaced
    // header indicating it is a recursive lock.
    bne(CCR0, slow_case);
    release();
    std(R0/*==0!*/, BasicObjectLock::lock_offset_in_bytes() +
        BasicLock::displaced_header_offset_in_bytes(), monitor);
    b(done);


    // } else {
    //   // Slow path.
    //   InterpreterRuntime::monitorenter(THREAD, monitor);

    // None of the above fast optimizations worked so we have to get into the
    // slow case of monitor enter.
    bind(slow_case);
    call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::monitorenter),
            monitor, /*check_for_exceptions=*/true CC_INTERP_ONLY(&& false));
    // }

    bind(done);
  }
}

// Unlocks an object. Used in monitorexit bytecode and remove_activation.
//
// Registers alive
//   monitor - Address of the BasicObjectLock to be used for locking,
//             which must be initialized with the object to lock.
//
// Throw IllegalMonitorException if object is not locked by current thread.
void InterpreterMacroAssembler::unlock_object(Register monitor, bool check_for_exceptions) {
  if (UseHeavyMonitors) {
    call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::monitorexit),
            monitor, /*check_for_exceptions=*/false);
  } else {

    // template code:
    //
    // if ((displaced_header = monitor->displaced_header()) == NULL) {
    //   // Recursive unlock.  Mark the monitor unlocked by setting the object field to NULL.
    //   monitor->set_obj(NULL);
    // } else if (Atomic::cmpxchg_ptr(displaced_header, obj->mark_addr(), monitor) == monitor) {
    //   // We swapped the unlocked mark in displaced_header into the object's mark word.
    //   monitor->set_obj(NULL);
    // } else {
    //   // Slow path.
    //   InterpreterRuntime::monitorexit(THREAD, monitor);
    // }

    const Register object           = R7_ARG5;
    const Register displaced_header = R8_ARG6;
    const Register object_mark_addr = R9_ARG7;
    const Register current_header   = R10_ARG8;

    Label free_slot;
    Label slow_case;

    assert_different_registers(object, displaced_header, object_mark_addr, current_header);

    if (UseBiasedLocking) {
      // The object address from the monitor is in object.
      ld(object, BasicObjectLock::obj_offset_in_bytes(), monitor);
      assert(oopDesc::mark_offset_in_bytes() == 0, "offset of _mark is not 0");
      biased_locking_exit(CCR0, object, displaced_header, free_slot);
    }

    // Test first if we are in the fast recursive case.
    ld(displaced_header, BasicObjectLock::lock_offset_in_bytes() +
           BasicLock::displaced_header_offset_in_bytes(), monitor);

    // If the displaced header is zero, we have a recursive unlock.
    cmpdi(CCR0, displaced_header, 0);
    beq(CCR0, free_slot); // recursive unlock

    // } else if (Atomic::cmpxchg_ptr(displaced_header, obj->mark_addr(), monitor) == monitor) {
    //   // We swapped the unlocked mark in displaced_header into the object's mark word.
    //   monitor->set_obj(NULL);

    // If we still have a lightweight lock, unlock the object and be done.

    // The object address from the monitor is in object.
    if (!UseBiasedLocking) ld(object, BasicObjectLock::obj_offset_in_bytes(), monitor);
    addi(object_mark_addr, object, oopDesc::mark_offset_in_bytes());

    // We have the displaced header in displaced_header. If the lock is still
    // lightweight, it will contain the monitor address and we'll store the
    // displaced header back into the object's mark word.
    // CmpxchgX sets CCR0 to cmpX(current, monitor).
    cmpxchgd(/*flag=*/CCR0,
             /*current_value=*/current_header,
             /*compare_value=*/monitor, /*exchange_value=*/displaced_header,
             /*where=*/object_mark_addr,
             MacroAssembler::MemBarRel,
             MacroAssembler::cmpxchgx_hint_release_lock(),
             noreg,
             &slow_case);
    b(free_slot);

    // } else {
    //   // Slow path.
    //   InterpreterRuntime::monitorexit(THREAD, monitor);

    // The lock has been converted into a heavy lock and hence
    // we need to get into the slow case.
    bind(slow_case);
    call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::monitorexit),
            monitor, check_for_exceptions CC_INTERP_ONLY(&& false));
    // }

    Label done;
    b(done); // Monitor register may be overwritten! Runtime has already freed the slot.

    // Exchange worked, do monitor->set_obj(NULL);
    align(32, 12);
    bind(free_slot);
    li(R0, 0);
    std(R0, BasicObjectLock::obj_offset_in_bytes(), monitor);
    bind(done);
  }
}

void InterpreterMacroAssembler::get_method_counters(Register method,
                                                    Register Rcounters,
                                                    Label& skip) {
  BLOCK_COMMENT("Load and ev. allocate counter object {");
  Label has_counters;
  ld(Rcounters, in_bytes(Method::method_counters_offset()), method);
  cmpdi(CCR0, Rcounters, 0);
  bne(CCR0, has_counters);
  call_VM(noreg, CAST_FROM_FN_PTR(address,
                                  InterpreterRuntime::build_method_counters), method, false);
  ld(Rcounters, in_bytes(Method::method_counters_offset()), method);
  cmpdi(CCR0, Rcounters, 0);
  beq(CCR0, skip); // No MethodCounters, OutOfMemory.
  BLOCK_COMMENT("} Load and ev. allocate counter object");

  bind(has_counters);
}

void InterpreterMacroAssembler::increment_invocation_counter(Register Rcounters, Register iv_be_count, Register Rtmp_r0) {
  assert(UseCompiler, "incrementing must be useful");
  Register invocation_count = iv_be_count;
  Register backedge_count   = Rtmp_r0;
  int delta = InvocationCounter::count_increment;

  // Load each counter in a register.
  //  ld(inv_counter, Rtmp);
  //  ld(be_counter, Rtmp2);
  int inv_counter_offset = in_bytes(MethodCounters::invocation_counter_offset() +
                                    InvocationCounter::counter_offset());
  int be_counter_offset  = in_bytes(MethodCounters::backedge_counter_offset() +
                                    InvocationCounter::counter_offset());

  BLOCK_COMMENT("Increment profiling counters {");

  // Load the backedge counter.
  lwz(backedge_count, be_counter_offset, Rcounters); // is unsigned int
  // Mask the backedge counter.
  Register tmp = invocation_count;
  li(tmp, InvocationCounter::count_mask_value);
  andr(backedge_count, tmp, backedge_count); // Cannot use andi, need sign extension of count_mask_value.

  // Load the invocation counter.
  lwz(invocation_count, inv_counter_offset, Rcounters); // is unsigned int
  // Add the delta to the invocation counter and store the result.
  addi(invocation_count, invocation_count, delta);
  // Store value.
  stw(invocation_count, inv_counter_offset, Rcounters);

  // Add invocation counter + backedge counter.
  add(iv_be_count, backedge_count, invocation_count);

  // Note that this macro must leave the backedge_count + invocation_count in
  // register iv_be_count!
  BLOCK_COMMENT("} Increment profiling counters");
}

void InterpreterMacroAssembler::verify_oop(Register reg, TosState state) {
  if (state == atos) { MacroAssembler::verify_oop(reg); }
}

// Inline assembly for:
//
// if (thread is in interp_only_mode) {
//   InterpreterRuntime::post_method_entry();
// }
// if (*jvmpi::event_flags_array_at_addr(JVMPI_EVENT_METHOD_ENTRY ) ||
//     *jvmpi::event_flags_array_at_addr(JVMPI_EVENT_METHOD_ENTRY2)   ) {
//   SharedRuntime::jvmpi_method_entry(method, receiver);
// }
void InterpreterMacroAssembler::notify_method_entry() {
  // JVMTI
  // Whenever JVMTI puts a thread in interp_only_mode, method
  // entry/exit events are sent for that thread to track stack
  // depth. If it is possible to enter interp_only_mode we add
  // the code to check if the event should be sent.
  if (JvmtiExport::can_post_interpreter_events()) {
    Label jvmti_post_done;

    lwz(R0, in_bytes(JavaThread::interp_only_mode_offset()), R16_thread);
    cmpwi(CCR0, R0, 0);
    beq(CCR0, jvmti_post_done);
    call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::post_method_entry),
            /*check_exceptions=*/false);

    bind(jvmti_post_done);
  }
}


// Inline assembly for:
//
// if (thread is in interp_only_mode) {
//   // save result
//   InterpreterRuntime::post_method_exit();
//   // restore result
// }
// if (*jvmpi::event_flags_array_at_addr(JVMPI_EVENT_METHOD_EXIT)) {
//   // save result
//   SharedRuntime::jvmpi_method_exit();
//   // restore result
// }
//
// Native methods have their result stored in d_tmp and l_tmp.
// Java methods have their result stored in the expression stack.
void InterpreterMacroAssembler::notify_method_exit(bool is_native_method, TosState state) {
  // JVMTI
  // Whenever JVMTI puts a thread in interp_only_mode, method
  // entry/exit events are sent for that thread to track stack
  // depth. If it is possible to enter interp_only_mode we add
  // the code to check if the event should be sent.
  if (JvmtiExport::can_post_interpreter_events()) {
    Label jvmti_post_done;

    lwz(R0, in_bytes(JavaThread::interp_only_mode_offset()), R16_thread);
    cmpwi(CCR0, R0, 0);
    beq(CCR0, jvmti_post_done);
    call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::post_method_exit),
            /*check_exceptions=*/false);

    align(32, 12);
    bind(jvmti_post_done);
  }
}

// Convert the current TOP_IJAVA_FRAME into a PARENT_IJAVA_FRAME
// (using parent_frame_resize) and push a new interpreter
// TOP_IJAVA_FRAME (using frame_size).
void InterpreterMacroAssembler::push_interpreter_frame(Register top_frame_size, Register parent_frame_resize,
                                                       Register tmp1, Register tmp2, Register tmp3,
                                                       Register tmp4, Register pc) {
  assert_different_registers(top_frame_size, parent_frame_resize, tmp1, tmp2, tmp3, tmp4);
  ld(tmp1, _top_ijava_frame_abi(frame_manager_lr), R1_SP);
  mr(tmp2/*top_frame_sp*/, R1_SP);
  // Move initial_caller_sp.
  ld(tmp4, _top_ijava_frame_abi(initial_caller_sp), R1_SP);
  neg(parent_frame_resize, parent_frame_resize);
  resize_frame(parent_frame_resize/*-parent_frame_resize*/, tmp3);

  // Set LR in new parent frame.
  std(tmp1, _abi(lr), R1_SP);
  // Set top_frame_sp info for new parent frame.
  std(tmp2, _parent_ijava_frame_abi(top_frame_sp), R1_SP);
  std(tmp4, _parent_ijava_frame_abi(initial_caller_sp), R1_SP);

  // Push new TOP_IJAVA_FRAME.
  push_frame(top_frame_size, tmp2);

  get_PC_trash_LR(tmp3);
  std(tmp3, _top_ijava_frame_abi(frame_manager_lr), R1_SP);
  // Used for non-initial callers by unextended_sp().
  std(R1_SP, _top_ijava_frame_abi(initial_caller_sp), R1_SP);
}

// Pop the topmost TOP_IJAVA_FRAME and convert the previous
// PARENT_IJAVA_FRAME back into a TOP_IJAVA_FRAME.
void InterpreterMacroAssembler::pop_interpreter_frame(Register tmp1, Register tmp2, Register tmp3, Register tmp4) {
  assert_different_registers(tmp1, tmp2, tmp3, tmp4);

  ld(tmp1/*caller's sp*/, _abi(callers_sp), R1_SP);
  ld(tmp3, _abi(lr), tmp1);

  ld(tmp4, _parent_ijava_frame_abi(initial_caller_sp), tmp1);

  ld(tmp2/*caller's caller's sp*/, _abi(callers_sp), tmp1);
  // Merge top frame.
  std(tmp2, _abi(callers_sp), R1_SP);

  ld(tmp2, _parent_ijava_frame_abi(top_frame_sp), tmp1);

  // Update C stack pointer to caller's top_abi.
  resize_frame_absolute(tmp2/*addr*/, tmp1/*tmp*/, tmp2/*tmp*/);

  // Update LR in top_frame.
  std(tmp3, _top_ijava_frame_abi(frame_manager_lr), R1_SP);

  std(tmp4, _top_ijava_frame_abi(initial_caller_sp), R1_SP);

  // Store the top-frame stack-pointer for c2i adapters.
  std(R1_SP, _top_ijava_frame_abi(top_frame_sp), R1_SP);
}

#ifdef CC_INTERP
// Turn state's interpreter frame into the current TOP_IJAVA_FRAME.
void InterpreterMacroAssembler::pop_interpreter_frame_to_state(Register state, Register tmp1, Register tmp2, Register tmp3) {
  assert_different_registers(R14_state, R15_prev_state, tmp1, tmp2, tmp3);

  if (state == R14_state) {
    ld(tmp1/*state's fp*/, state_(_last_Java_fp));
    ld(tmp2/*state's sp*/, state_(_last_Java_sp));
  } else if (state == R15_prev_state) {
    ld(tmp1/*state's fp*/, prev_state_(_last_Java_fp));
    ld(tmp2/*state's sp*/, prev_state_(_last_Java_sp));
  } else {
    ShouldNotReachHere();
  }

  // Merge top frames.
  std(tmp1, _abi(callers_sp), R1_SP);

  // Tmp2 is new SP.
  // Tmp1 is parent's SP.
  resize_frame_absolute(tmp2/*addr*/, tmp1/*tmp*/, tmp2/*tmp*/);

  // Update LR in top_frame.
  // Must be interpreter frame.
  get_PC_trash_LR(tmp3);
  std(tmp3, _top_ijava_frame_abi(frame_manager_lr), R1_SP);
  // Used for non-initial callers by unextended_sp().
  std(R1_SP, _top_ijava_frame_abi(initial_caller_sp), R1_SP);
}
#endif // CC_INTERP

// Set SP to initial caller's sp, but before fix the back chain.
void InterpreterMacroAssembler::resize_frame_to_initial_caller(Register tmp1, Register tmp2) {
  ld(tmp1, _parent_ijava_frame_abi(initial_caller_sp), R1_SP);
  ld(tmp2, _parent_ijava_frame_abi(callers_sp), R1_SP);
  std(tmp2, _parent_ijava_frame_abi(callers_sp), tmp1); // Fix back chain ...
  mr(R1_SP, tmp1); // ... and resize to initial caller.
}

#ifdef CC_INTERP
// Pop the current interpreter state (without popping the correspoding
// frame) and restore R14_state and R15_prev_state accordingly.
// Use prev_state_may_be_0 to indicate whether prev_state may be 0
// in order to generate an extra check before retrieving prev_state_(_prev_link).
void InterpreterMacroAssembler::pop_interpreter_state(bool prev_state_may_be_0)
{
  // Move prev_state to state and restore prev_state from state_(_prev_link).
  Label prev_state_is_0;
  mr(R14_state, R15_prev_state);

  // Don't retrieve /*state==*/prev_state_(_prev_link)
  // if /*state==*/prev_state is 0.
  if (prev_state_may_be_0) {
    cmpdi(CCR0, R15_prev_state, 0);
    beq(CCR0, prev_state_is_0);
  }

  ld(R15_prev_state, /*state==*/prev_state_(_prev_link));
  bind(prev_state_is_0);
}

void InterpreterMacroAssembler::restore_prev_state() {
  // _prev_link is private, but cInterpreter is a friend.
  ld(R15_prev_state, state_(_prev_link));
}
#endif // CC_INTERP
