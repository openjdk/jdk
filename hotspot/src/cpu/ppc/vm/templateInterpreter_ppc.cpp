/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2013, 2015 SAP AG. All rights reserved.
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
#ifndef CC_INTERP
#include "asm/macroAssembler.inline.hpp"
#include "interpreter/bytecodeHistogram.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterGenerator.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "interpreter/interp_masm.hpp"
#include "interpreter/templateTable.hpp"
#include "oops/arrayOop.hpp"
#include "oops/methodData.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "runtime/arguments.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/timer.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"

#undef __
#define __ _masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#define BIND(label)        __ bind(label); BLOCK_COMMENT(#label ":")

//-----------------------------------------------------------------------------

// Actually we should never reach here since we do stack overflow checks before pushing any frame.
address TemplateInterpreterGenerator::generate_StackOverflowError_handler() {
  address entry = __ pc();
  __ unimplemented("generate_StackOverflowError_handler");
  return entry;
}

address TemplateInterpreterGenerator::generate_ArrayIndexOutOfBounds_handler(const char* name) {
  address entry = __ pc();
  __ empty_expression_stack();
  __ load_const_optimized(R4_ARG2, (address) name);
  // Index is in R17_tos.
  __ mr(R5_ARG3, R17_tos);
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_ArrayIndexOutOfBoundsException));
  return entry;
}

#if 0
// Call special ClassCastException constructor taking object to cast
// and target class as arguments.
address TemplateInterpreterGenerator::generate_ClassCastException_verbose_handler() {
  address entry = __ pc();

  // Expression stack must be empty before entering the VM if an
  // exception happened.
  __ empty_expression_stack();

  // Thread will be loaded to R3_ARG1.
  // Target class oop is in register R5_ARG3 by convention!
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_ClassCastException_verbose), R17_tos, R5_ARG3);
  // Above call must not return here since exception pending.
  DEBUG_ONLY(__ should_not_reach_here();)
  return entry;
}
#endif

address TemplateInterpreterGenerator::generate_ClassCastException_handler() {
  address entry = __ pc();
  // Expression stack must be empty before entering the VM if an
  // exception happened.
  __ empty_expression_stack();

  // Load exception object.
  // Thread will be loaded to R3_ARG1.
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_ClassCastException), R17_tos);
#ifdef ASSERT
  // Above call must not return here since exception pending.
  __ should_not_reach_here();
#endif
  return entry;
}

address TemplateInterpreterGenerator::generate_exception_handler_common(const char* name, const char* message, bool pass_oop) {
  address entry = __ pc();
  //__ untested("generate_exception_handler_common");
  Register Rexception = R17_tos;

  // Expression stack must be empty before entering the VM if an exception happened.
  __ empty_expression_stack();

  __ load_const_optimized(R4_ARG2, (address) name, R11_scratch1);
  if (pass_oop) {
    __ mr(R5_ARG3, Rexception);
    __ call_VM(Rexception, CAST_FROM_FN_PTR(address, InterpreterRuntime::create_klass_exception), false);
  } else {
    __ load_const_optimized(R5_ARG3, (address) message, R11_scratch1);
    __ call_VM(Rexception, CAST_FROM_FN_PTR(address, InterpreterRuntime::create_exception), false);
  }

  // Throw exception.
  __ mr(R3_ARG1, Rexception);
  __ load_const_optimized(R11_scratch1, Interpreter::throw_exception_entry(), R12_scratch2);
  __ mtctr(R11_scratch1);
  __ bctr();

  return entry;
}

address TemplateInterpreterGenerator::generate_continuation_for(TosState state) {
  address entry = __ pc();
  __ unimplemented("generate_continuation_for");
  return entry;
}

// This entry is returned to when a call returns to the interpreter.
// When we arrive here, we expect that the callee stack frame is already popped.
address TemplateInterpreterGenerator::generate_return_entry_for(TosState state, int step, size_t index_size) {
  address entry = __ pc();

  // Move the value out of the return register back to the TOS cache of current frame.
  switch (state) {
    case ltos:
    case btos:
    case ctos:
    case stos:
    case atos:
    case itos: __ mr(R17_tos, R3_RET); break;   // RET -> TOS cache
    case ftos:
    case dtos: __ fmr(F15_ftos, F1_RET); break; // TOS cache -> GR_FRET
    case vtos: break;                           // Nothing to do, this was a void return.
    default  : ShouldNotReachHere();
  }

  __ restore_interpreter_state(R11_scratch1); // Sets R11_scratch1 = fp.
  __ ld(R12_scratch2, _ijava_state_neg(top_frame_sp), R11_scratch1);
  __ resize_frame_absolute(R12_scratch2, R11_scratch1, R0);

  // Compiled code destroys templateTableBase, reload.
  __ load_const_optimized(R25_templateTableBase, (address)Interpreter::dispatch_table((TosState)0), R12_scratch2);

  if (state == atos) {
    __ profile_return_type(R3_RET, R11_scratch1, R12_scratch2);
  }

  const Register cache = R11_scratch1;
  const Register size  = R12_scratch2;
  __ get_cache_and_index_at_bcp(cache, 1, index_size);

  // Get least significant byte of 64 bit value:
#if defined(VM_LITTLE_ENDIAN)
  __ lbz(size, in_bytes(ConstantPoolCache::base_offset() + ConstantPoolCacheEntry::flags_offset()), cache);
#else
  __ lbz(size, in_bytes(ConstantPoolCache::base_offset() + ConstantPoolCacheEntry::flags_offset()) + 7, cache);
#endif
  __ sldi(size, size, Interpreter::logStackElementSize);
  __ add(R15_esp, R15_esp, size);
  __ dispatch_next(state, step);
  return entry;
}

address TemplateInterpreterGenerator::generate_deopt_entry_for(TosState state, int step) {
  address entry = __ pc();
  // If state != vtos, we're returning from a native method, which put it's result
  // into the result register. So move the value out of the return register back
  // to the TOS cache of current frame.

  switch (state) {
    case ltos:
    case btos:
    case ctos:
    case stos:
    case atos:
    case itos: __ mr(R17_tos, R3_RET); break;   // GR_RET -> TOS cache
    case ftos:
    case dtos: __ fmr(F15_ftos, F1_RET); break; // TOS cache -> GR_FRET
    case vtos: break;                           // Nothing to do, this was a void return.
    default  : ShouldNotReachHere();
  }

  // Load LcpoolCache @@@ should be already set!
  __ get_constant_pool_cache(R27_constPoolCache);

  // Handle a pending exception, fall through if none.
  __ check_and_forward_exception(R11_scratch1, R12_scratch2);

  // Start executing bytecodes.
  __ dispatch_next(state, step);

  return entry;
}

// A result handler converts the native result into java format.
// Use the shared code between c++ and template interpreter.
address TemplateInterpreterGenerator::generate_result_handler_for(BasicType type) {
  return AbstractInterpreterGenerator::generate_result_handler_for(type);
}

address TemplateInterpreterGenerator::generate_safept_entry_for(TosState state, address runtime_entry) {
  address entry = __ pc();

  __ push(state);
  __ call_VM(noreg, runtime_entry);
  __ dispatch_via(vtos, Interpreter::_normal_table.table_for(vtos));

  return entry;
}

// Helpers for commoning out cases in the various type of method entries.

// Increment invocation count & check for overflow.
//
// Note: checking for negative value instead of overflow
//       so we have a 'sticky' overflow test.
//
void TemplateInterpreterGenerator::generate_counter_incr(Label* overflow, Label* profile_method, Label* profile_method_continue) {
  // Note: In tiered we increment either counters in method or in MDO depending if we're profiling or not.
  Register Rscratch1   = R11_scratch1;
  Register Rscratch2   = R12_scratch2;
  Register R3_counters = R3_ARG1;
  Label done;

  if (TieredCompilation) {
    const int increment = InvocationCounter::count_increment;
    Label no_mdo;
    if (ProfileInterpreter) {
      const Register Rmdo = R3_counters;
      // If no method data exists, go to profile_continue.
      __ ld(Rmdo, in_bytes(Method::method_data_offset()), R19_method);
      __ cmpdi(CCR0, Rmdo, 0);
      __ beq(CCR0, no_mdo);

      // Increment backedge counter in the MDO.
      const int mdo_ic_offs = in_bytes(MethodData::invocation_counter_offset()) + in_bytes(InvocationCounter::counter_offset());
      __ lwz(Rscratch2, mdo_ic_offs, Rmdo);
      __ lwz(Rscratch1, in_bytes(MethodData::invoke_mask_offset()), Rmdo);
      __ addi(Rscratch2, Rscratch2, increment);
      __ stw(Rscratch2, mdo_ic_offs, Rmdo);
      __ and_(Rscratch1, Rscratch2, Rscratch1);
      __ bne(CCR0, done);
      __ b(*overflow);
    }

    // Increment counter in MethodCounters*.
    const int mo_bc_offs = in_bytes(MethodCounters::invocation_counter_offset()) + in_bytes(InvocationCounter::counter_offset());
    __ bind(no_mdo);
    __ get_method_counters(R19_method, R3_counters, done);
    __ lwz(Rscratch2, mo_bc_offs, R3_counters);
    __ lwz(Rscratch1, in_bytes(MethodCounters::invoke_mask_offset()), R3_counters);
    __ addi(Rscratch2, Rscratch2, increment);
    __ stw(Rscratch2, mo_bc_offs, R3_counters);
    __ and_(Rscratch1, Rscratch2, Rscratch1);
    __ beq(CCR0, *overflow);

    __ bind(done);

  } else {

    // Update standard invocation counters.
    Register Rsum_ivc_bec = R4_ARG2;
    __ get_method_counters(R19_method, R3_counters, done);
    __ increment_invocation_counter(R3_counters, Rsum_ivc_bec, R12_scratch2);
    // Increment interpreter invocation counter.
    if (ProfileInterpreter) {  // %%% Merge this into methodDataOop.
      __ lwz(R12_scratch2, in_bytes(MethodCounters::interpreter_invocation_counter_offset()), R3_counters);
      __ addi(R12_scratch2, R12_scratch2, 1);
      __ stw(R12_scratch2, in_bytes(MethodCounters::interpreter_invocation_counter_offset()), R3_counters);
    }
    // Check if we must create a method data obj.
    if (ProfileInterpreter && profile_method != NULL) {
      const Register profile_limit = Rscratch1;
      __ lwz(profile_limit, in_bytes(MethodCounters::interpreter_profile_limit_offset()), R3_counters);
      // Test to see if we should create a method data oop.
      __ cmpw(CCR0, Rsum_ivc_bec, profile_limit);
      __ blt(CCR0, *profile_method_continue);
      // If no method data exists, go to profile_method.
      __ test_method_data_pointer(*profile_method);
    }
    // Finally check for counter overflow.
    if (overflow) {
      const Register invocation_limit = Rscratch1;
      __ lwz(invocation_limit, in_bytes(MethodCounters::interpreter_invocation_limit_offset()), R3_counters);
      __ cmpw(CCR0, Rsum_ivc_bec, invocation_limit);
      __ bge(CCR0, *overflow);
    }

    __ bind(done);
  }
}

// Generate code to initiate compilation on invocation counter overflow.
void TemplateInterpreterGenerator::generate_counter_overflow(Label& continue_entry) {
  // Generate code to initiate compilation on the counter overflow.

  // InterpreterRuntime::frequency_counter_overflow takes one arguments,
  // which indicates if the counter overflow occurs at a backwards branch (NULL bcp)
  // We pass zero in.
  // The call returns the address of the verified entry point for the method or NULL
  // if the compilation did not complete (either went background or bailed out).
  //
  // Unlike the C++ interpreter above: Check exceptions!
  // Assumption: Caller must set the flag "do_not_unlock_if_sychronized" if the monitor of a sync'ed
  // method has not yet been created. Thus, no unlocking of a non-existing monitor can occur.

  __ li(R4_ARG2, 0);
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::frequency_counter_overflow), R4_ARG2, true);

  // Returns verified_entry_point or NULL.
  // We ignore it in any case.
  __ b(continue_entry);
}

void TemplateInterpreterGenerator::generate_stack_overflow_check(Register Rmem_frame_size, Register Rscratch1) {
  assert_different_registers(Rmem_frame_size, Rscratch1);
  __ generate_stack_overflow_check_with_compare_and_throw(Rmem_frame_size, Rscratch1);
}

void TemplateInterpreterGenerator::unlock_method(bool check_exceptions) {
  __ unlock_object(R26_monitor, check_exceptions);
}

// Lock the current method, interpreter register window must be set up!
void TemplateInterpreterGenerator::lock_method(Register Rflags, Register Rscratch1, Register Rscratch2, bool flags_preloaded) {
  const Register Robj_to_lock = Rscratch2;

  {
    if (!flags_preloaded) {
      __ lwz(Rflags, method_(access_flags));
    }

#ifdef ASSERT
    // Check if methods needs synchronization.
    {
      Label Lok;
      __ testbitdi(CCR0, R0, Rflags, JVM_ACC_SYNCHRONIZED_BIT);
      __ btrue(CCR0,Lok);
      __ stop("method doesn't need synchronization");
      __ bind(Lok);
    }
#endif // ASSERT
  }

  // Get synchronization object to Rscratch2.
  {
    const int mirror_offset = in_bytes(Klass::java_mirror_offset());
    Label Lstatic;
    Label Ldone;

    __ testbitdi(CCR0, R0, Rflags, JVM_ACC_STATIC_BIT);
    __ btrue(CCR0, Lstatic);

    // Non-static case: load receiver obj from stack and we're done.
    __ ld(Robj_to_lock, R18_locals);
    __ b(Ldone);

    __ bind(Lstatic); // Static case: Lock the java mirror
    __ ld(Robj_to_lock, in_bytes(Method::const_offset()), R19_method);
    __ ld(Robj_to_lock, in_bytes(ConstMethod::constants_offset()), Robj_to_lock);
    __ ld(Robj_to_lock, ConstantPool::pool_holder_offset_in_bytes(), Robj_to_lock);
    __ ld(Robj_to_lock, mirror_offset, Robj_to_lock);

    __ bind(Ldone);
    __ verify_oop(Robj_to_lock);
  }

  // Got the oop to lock => execute!
  __ add_monitor_to_stack(true, Rscratch1, R0);

  __ std(Robj_to_lock, BasicObjectLock::obj_offset_in_bytes(), R26_monitor);
  __ lock_object(R26_monitor, Robj_to_lock);
}

// Generate a fixed interpreter frame for pure interpreter
// and I2N native transition frames.
//
// Before (stack grows downwards):
//
//         |  ...         |
//         |------------- |
//         |  java arg0   |
//         |  ...         |
//         |  java argn   |
//         |              |   <-   R15_esp
//         |              |
//         |--------------|
//         | abi_112      |
//         |              |   <-   R1_SP
//         |==============|
//
//
// After:
//
//         |  ...         |
//         |  java arg0   |<-   R18_locals
//         |  ...         |
//         |  java argn   |
//         |--------------|
//         |              |
//         |  java locals |
//         |              |
//         |--------------|
//         |  abi_48      |
//         |==============|
//         |              |
//         |   istate     |
//         |              |
//         |--------------|
//         |   monitor    |<-   R26_monitor
//         |--------------|
//         |              |<-   R15_esp
//         | expression   |
//         | stack        |
//         |              |
//         |--------------|
//         |              |
//         | abi_112      |<-   R1_SP
//         |==============|
//
// The top most frame needs an abi space of 112 bytes. This space is needed,
// since we call to c. The c function may spill their arguments to the caller
// frame. When we call to java, we don't need these spill slots. In order to save
// space on the stack, we resize the caller. However, java local reside in
// the caller frame and the frame has to be increased. The frame_size for the
// current frame was calculated based on max_stack as size for the expression
// stack. At the call, just a part of the expression stack might be used.
// We don't want to waste this space and cut the frame back accordingly.
// The resulting amount for resizing is calculated as follows:
// resize =   (number_of_locals - number_of_arguments) * slot_size
//          + (R1_SP - R15_esp) + 48
//
// The size for the callee frame is calculated:
// framesize = 112 + max_stack + monitor + state_size
//
// maxstack:   Max number of slots on the expression stack, loaded from the method.
// monitor:    We statically reserve room for one monitor object.
// state_size: We save the current state of the interpreter to this area.
//
void TemplateInterpreterGenerator::generate_fixed_frame(bool native_call, Register Rsize_of_parameters, Register Rsize_of_locals) {
  Register parent_frame_resize = R6_ARG4, // Frame will grow by this number of bytes.
           top_frame_size      = R7_ARG5,
           Rconst_method       = R8_ARG6;

  assert_different_registers(Rsize_of_parameters, Rsize_of_locals, parent_frame_resize, top_frame_size);

  __ ld(Rconst_method, method_(const));
  __ lhz(Rsize_of_parameters /* number of params */,
         in_bytes(ConstMethod::size_of_parameters_offset()), Rconst_method);
  if (native_call) {
    // If we're calling a native method, we reserve space for the worst-case signature
    // handler varargs vector, which is max(Argument::n_register_parameters, parameter_count+2).
    // We add two slots to the parameter_count, one for the jni
    // environment and one for a possible native mirror.
    Label skip_native_calculate_max_stack;
    __ addi(top_frame_size, Rsize_of_parameters, 2);
    __ cmpwi(CCR0, top_frame_size, Argument::n_register_parameters);
    __ bge(CCR0, skip_native_calculate_max_stack);
    __ li(top_frame_size, Argument::n_register_parameters);
    __ bind(skip_native_calculate_max_stack);
    __ sldi(Rsize_of_parameters, Rsize_of_parameters, Interpreter::logStackElementSize);
    __ sldi(top_frame_size, top_frame_size, Interpreter::logStackElementSize);
    __ sub(parent_frame_resize, R1_SP, R15_esp); // <0, off by Interpreter::stackElementSize!
    assert(Rsize_of_locals == noreg, "Rsize_of_locals not initialized"); // Only relevant value is Rsize_of_parameters.
  } else {
    __ lhz(Rsize_of_locals /* number of params */, in_bytes(ConstMethod::size_of_locals_offset()), Rconst_method);
    __ sldi(Rsize_of_parameters, Rsize_of_parameters, Interpreter::logStackElementSize);
    __ sldi(Rsize_of_locals, Rsize_of_locals, Interpreter::logStackElementSize);
    __ lhz(top_frame_size, in_bytes(ConstMethod::max_stack_offset()), Rconst_method);
    __ sub(R11_scratch1, Rsize_of_locals, Rsize_of_parameters); // >=0
    __ sub(parent_frame_resize, R1_SP, R15_esp); // <0, off by Interpreter::stackElementSize!
    __ sldi(top_frame_size, top_frame_size, Interpreter::logStackElementSize);
    __ add(parent_frame_resize, parent_frame_resize, R11_scratch1);
  }

  // Compute top frame size.
  __ addi(top_frame_size, top_frame_size, frame::abi_reg_args_size + frame::ijava_state_size);

  // Cut back area between esp and max_stack.
  __ addi(parent_frame_resize, parent_frame_resize, frame::abi_minframe_size - Interpreter::stackElementSize);

  __ round_to(top_frame_size, frame::alignment_in_bytes);
  __ round_to(parent_frame_resize, frame::alignment_in_bytes);
  // parent_frame_resize = (locals-parameters) - (ESP-SP-ABI48) Rounded to frame alignment size.
  // Enlarge by locals-parameters (not in case of native_call), shrink by ESP-SP-ABI48.

  {
    // --------------------------------------------------------------------------
    // Stack overflow check

    Label cont;
    __ add(R11_scratch1, parent_frame_resize, top_frame_size);
    generate_stack_overflow_check(R11_scratch1, R12_scratch2);
  }

  // Set up interpreter state registers.

  __ add(R18_locals, R15_esp, Rsize_of_parameters);
  __ ld(R27_constPoolCache, in_bytes(ConstMethod::constants_offset()), Rconst_method);
  __ ld(R27_constPoolCache, ConstantPool::cache_offset_in_bytes(), R27_constPoolCache);

  // Set method data pointer.
  if (ProfileInterpreter) {
    Label zero_continue;
    __ ld(R28_mdx, method_(method_data));
    __ cmpdi(CCR0, R28_mdx, 0);
    __ beq(CCR0, zero_continue);
    __ addi(R28_mdx, R28_mdx, in_bytes(MethodData::data_offset()));
    __ bind(zero_continue);
  }

  if (native_call) {
    __ li(R14_bcp, 0); // Must initialize.
  } else {
    __ add(R14_bcp, in_bytes(ConstMethod::codes_offset()), Rconst_method);
  }

  // Resize parent frame.
  __ mflr(R12_scratch2);
  __ neg(parent_frame_resize, parent_frame_resize);
  __ resize_frame(parent_frame_resize, R11_scratch1);
  __ std(R12_scratch2, _abi(lr), R1_SP);

  __ addi(R26_monitor, R1_SP, - frame::ijava_state_size);
  __ addi(R15_esp, R26_monitor, - Interpreter::stackElementSize);

  // Store values.
  // R15_esp, R14_bcp, R26_monitor, R28_mdx are saved at java calls
  // in InterpreterMacroAssembler::call_from_interpreter.
  __ std(R19_method, _ijava_state_neg(method), R1_SP);
  __ std(R21_sender_SP, _ijava_state_neg(sender_sp), R1_SP);
  __ std(R27_constPoolCache, _ijava_state_neg(cpoolCache), R1_SP);
  __ std(R18_locals, _ijava_state_neg(locals), R1_SP);

  // Note: esp, bcp, monitor, mdx live in registers. Hence, the correct version can only
  // be found in the frame after save_interpreter_state is done. This is always true
  // for non-top frames. But when a signal occurs, dumping the top frame can go wrong,
  // because e.g. frame::interpreter_frame_bcp() will not access the correct value
  // (Enhanced Stack Trace).
  // The signal handler does not save the interpreter state into the frame.
  __ li(R0, 0);
#ifdef ASSERT
  // Fill remaining slots with constants.
  __ load_const_optimized(R11_scratch1, 0x5afe);
  __ load_const_optimized(R12_scratch2, 0xdead);
#endif
  // We have to initialize some frame slots for native calls (accessed by GC).
  if (native_call) {
    __ std(R26_monitor, _ijava_state_neg(monitors), R1_SP);
    __ std(R14_bcp, _ijava_state_neg(bcp), R1_SP);
    if (ProfileInterpreter) { __ std(R28_mdx, _ijava_state_neg(mdx), R1_SP); }
  }
#ifdef ASSERT
  else {
    __ std(R12_scratch2, _ijava_state_neg(monitors), R1_SP);
    __ std(R12_scratch2, _ijava_state_neg(bcp), R1_SP);
    __ std(R12_scratch2, _ijava_state_neg(mdx), R1_SP);
  }
  __ std(R11_scratch1, _ijava_state_neg(ijava_reserved), R1_SP);
  __ std(R12_scratch2, _ijava_state_neg(esp), R1_SP);
  __ std(R12_scratch2, _ijava_state_neg(lresult), R1_SP);
  __ std(R12_scratch2, _ijava_state_neg(fresult), R1_SP);
#endif
  __ subf(R12_scratch2, top_frame_size, R1_SP);
  __ std(R0, _ijava_state_neg(oop_tmp), R1_SP);
  __ std(R12_scratch2, _ijava_state_neg(top_frame_sp), R1_SP);

  // Push top frame.
  __ push_frame(top_frame_size, R11_scratch1);
}

// End of helpers


// Support abs and sqrt like in compiler.
// For others we can use a normal (native) entry.

inline bool math_entry_available(AbstractInterpreter::MethodKind kind) {
  if (!InlineIntrinsics) return false;

  return ((kind==Interpreter::java_lang_math_sqrt && VM_Version::has_fsqrt()) ||
          (kind==Interpreter::java_lang_math_abs));
}

address TemplateInterpreterGenerator::generate_math_entry(AbstractInterpreter::MethodKind kind) {
  if (!math_entry_available(kind)) {
    NOT_PRODUCT(__ should_not_reach_here();)
    return NULL;
  }

  address entry = __ pc();

  __ lfd(F1_RET, Interpreter::stackElementSize, R15_esp);

  // Pop c2i arguments (if any) off when we return.
#ifdef ASSERT
  __ ld(R9_ARG7, 0, R1_SP);
  __ ld(R10_ARG8, 0, R21_sender_SP);
  __ cmpd(CCR0, R9_ARG7, R10_ARG8);
  __ asm_assert_eq("backlink", 0x545);
#endif // ASSERT
  __ mr(R1_SP, R21_sender_SP); // Cut the stack back to where the caller started.

  if (kind == Interpreter::java_lang_math_sqrt) {
    __ fsqrt(F1_RET, F1_RET);
  } else if (kind == Interpreter::java_lang_math_abs) {
    __ fabs(F1_RET, F1_RET);
  } else {
    ShouldNotReachHere();
  }

  // And we're done.
  __ blr();

  __ flush();

  return entry;
}

// Interpreter stub for calling a native method. (asm interpreter)
// This sets up a somewhat different looking stack for calling the
// native method than the typical interpreter frame setup.
//
// On entry:
//   R19_method    - method
//   R16_thread    - JavaThread*
//   R15_esp       - intptr_t* sender tos
//
//   abstract stack (grows up)
//     [  IJava (caller of JNI callee)  ]  <-- ASP
//        ...
address TemplateInterpreterGenerator::generate_native_entry(bool synchronized) {

  address entry = __ pc();

  const bool inc_counter = UseCompiler || CountCompiledCalls || LogTouchedMethods;

  // -----------------------------------------------------------------------------
  // Allocate a new frame that represents the native callee (i2n frame).
  // This is not a full-blown interpreter frame, but in particular, the
  // following registers are valid after this:
  // - R19_method
  // - R18_local (points to start of argumuments to native function)
  //
  //   abstract stack (grows up)
  //     [  IJava (caller of JNI callee)  ]  <-- ASP
  //        ...

  const Register signature_handler_fd = R11_scratch1;
  const Register pending_exception    = R0;
  const Register result_handler_addr  = R31;
  const Register native_method_fd     = R11_scratch1;
  const Register access_flags         = R22_tmp2;
  const Register active_handles       = R11_scratch1; // R26_monitor saved to state.
  const Register sync_state           = R12_scratch2;
  const Register sync_state_addr      = sync_state;   // Address is dead after use.
  const Register suspend_flags        = R11_scratch1;

  //=============================================================================
  // Allocate new frame and initialize interpreter state.

  Label exception_return;
  Label exception_return_sync_check;
  Label stack_overflow_return;

  // Generate new interpreter state and jump to stack_overflow_return in case of
  // a stack overflow.
  //generate_compute_interpreter_state(stack_overflow_return);

  Register size_of_parameters = R22_tmp2;

  generate_fixed_frame(true, size_of_parameters, noreg /* unused */);

  //=============================================================================
  // Increment invocation counter. On overflow, entry to JNI method
  // will be compiled.
  Label invocation_counter_overflow, continue_after_compile;
  if (inc_counter) {
    if (synchronized) {
      // Since at this point in the method invocation the exception handler
      // would try to exit the monitor of synchronized methods which hasn't
      // been entered yet, we set the thread local variable
      // _do_not_unlock_if_synchronized to true. If any exception was thrown by
      // runtime, exception handling i.e. unlock_if_synchronized_method will
      // check this thread local flag.
      // This flag has two effects, one is to force an unwind in the topmost
      // interpreter frame and not perform an unlock while doing so.
      __ li(R0, 1);
      __ stb(R0, in_bytes(JavaThread::do_not_unlock_if_synchronized_offset()), R16_thread);
    }
    generate_counter_incr(&invocation_counter_overflow, NULL, NULL);

    BIND(continue_after_compile);
    // Reset the _do_not_unlock_if_synchronized flag.
    if (synchronized) {
      __ li(R0, 0);
      __ stb(R0, in_bytes(JavaThread::do_not_unlock_if_synchronized_offset()), R16_thread);
    }
  }

  // access_flags = method->access_flags();
  // Load access flags.
  assert(access_flags->is_nonvolatile(),
         "access_flags must be in a non-volatile register");
  // Type check.
  assert(4 == sizeof(AccessFlags), "unexpected field size");
  __ lwz(access_flags, method_(access_flags));

  // We don't want to reload R19_method and access_flags after calls
  // to some helper functions.
  assert(R19_method->is_nonvolatile(),
         "R19_method must be a non-volatile register");

  // Check for synchronized methods. Must happen AFTER invocation counter
  // check, so method is not locked if counter overflows.

  if (synchronized) {
    lock_method(access_flags, R11_scratch1, R12_scratch2, true);

    // Update monitor in state.
    __ ld(R11_scratch1, 0, R1_SP);
    __ std(R26_monitor, _ijava_state_neg(monitors), R11_scratch1);
  }

  // jvmti/jvmpi support
  __ notify_method_entry();

  //=============================================================================
  // Get and call the signature handler.

  __ ld(signature_handler_fd, method_(signature_handler));
  Label call_signature_handler;

  __ cmpdi(CCR0, signature_handler_fd, 0);
  __ bne(CCR0, call_signature_handler);

  // Method has never been called. Either generate a specialized
  // handler or point to the slow one.
  //
  // Pass parameter 'false' to avoid exception check in call_VM.
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::prepare_native_call), R19_method, false);

  // Check for an exception while looking up the target method. If we
  // incurred one, bail.
  __ ld(pending_exception, thread_(pending_exception));
  __ cmpdi(CCR0, pending_exception, 0);
  __ bne(CCR0, exception_return_sync_check); // Has pending exception.

  // Reload signature handler, it may have been created/assigned in the meanwhile.
  __ ld(signature_handler_fd, method_(signature_handler));
  __ twi_0(signature_handler_fd); // Order wrt. load of klass mirror and entry point (isync is below).

  BIND(call_signature_handler);

  // Before we call the signature handler we push a new frame to
  // protect the interpreter frame volatile registers when we return
  // from jni but before we can get back to Java.

  // First set the frame anchor while the SP/FP registers are
  // convenient and the slow signature handler can use this same frame
  // anchor.

  // We have a TOP_IJAVA_FRAME here, which belongs to us.
  __ set_top_ijava_frame_at_SP_as_last_Java_frame(R1_SP, R12_scratch2/*tmp*/);

  // Now the interpreter frame (and its call chain) have been
  // invalidated and flushed. We are now protected against eager
  // being enabled in native code. Even if it goes eager the
  // registers will be reloaded as clean and we will invalidate after
  // the call so no spurious flush should be possible.

  // Call signature handler and pass locals address.
  //
  // Our signature handlers copy required arguments to the C stack
  // (outgoing C args), R3_ARG1 to R10_ARG8, and FARG1 to FARG13.
  __ mr(R3_ARG1, R18_locals);
#if !defined(ABI_ELFv2)
  __ ld(signature_handler_fd, 0, signature_handler_fd);
#endif

  __ call_stub(signature_handler_fd);

  // Remove the register parameter varargs slots we allocated in
  // compute_interpreter_state. SP+16 ends up pointing to the ABI
  // outgoing argument area.
  //
  // Not needed on PPC64.
  //__ add(SP, SP, Argument::n_register_parameters*BytesPerWord);

  assert(result_handler_addr->is_nonvolatile(), "result_handler_addr must be in a non-volatile register");
  // Save across call to native method.
  __ mr(result_handler_addr, R3_RET);

  __ isync(); // Acquire signature handler before trying to fetch the native entry point and klass mirror.

  // Set up fixed parameters and call the native method.
  // If the method is static, get mirror into R4_ARG2.
  {
    Label method_is_not_static;
    // Access_flags is non-volatile and still, no need to restore it.

    // Restore access flags.
    __ testbitdi(CCR0, R0, access_flags, JVM_ACC_STATIC_BIT);
    __ bfalse(CCR0, method_is_not_static);

    // constants = method->constants();
    __ ld(R11_scratch1, in_bytes(Method::const_offset()), R19_method);
    __ ld(R11_scratch1, in_bytes(ConstMethod::constants_offset()), R11_scratch1);
    // pool_holder = method->constants()->pool_holder();
    __ ld(R11_scratch1/*pool_holder*/, ConstantPool::pool_holder_offset_in_bytes(),
          R11_scratch1/*constants*/);

    const int mirror_offset = in_bytes(Klass::java_mirror_offset());

    // mirror = pool_holder->klass_part()->java_mirror();
    __ ld(R0/*mirror*/, mirror_offset, R11_scratch1/*pool_holder*/);
    // state->_native_mirror = mirror;

    __ ld(R11_scratch1, 0, R1_SP);
    __ std(R0/*mirror*/, _ijava_state_neg(oop_tmp), R11_scratch1);
    // R4_ARG2 = &state->_oop_temp;
    __ addi(R4_ARG2, R11_scratch1, _ijava_state_neg(oop_tmp));
    BIND(method_is_not_static);
  }

  // At this point, arguments have been copied off the stack into
  // their JNI positions. Oops are boxed in-place on the stack, with
  // handles copied to arguments. The result handler address is in a
  // register.

  // Pass JNIEnv address as first parameter.
  __ addir(R3_ARG1, thread_(jni_environment));

  // Load the native_method entry before we change the thread state.
  __ ld(native_method_fd, method_(native_function));

  //=============================================================================
  // Transition from _thread_in_Java to _thread_in_native. As soon as
  // we make this change the safepoint code needs to be certain that
  // the last Java frame we established is good. The pc in that frame
  // just needs to be near here not an actual return address.

  // We use release_store_fence to update values like the thread state, where
  // we don't want the current thread to continue until all our prior memory
  // accesses (including the new thread state) are visible to other threads.
  __ li(R0, _thread_in_native);
  __ release();

  // TODO PPC port assert(4 == JavaThread::sz_thread_state(), "unexpected field size");
  __ stw(R0, thread_(thread_state));

  if (UseMembar) {
    __ fence();
  }

  //=============================================================================
  // Call the native method. Argument registers must not have been
  // overwritten since "__ call_stub(signature_handler);" (except for
  // ARG1 and ARG2 for static methods).
  __ call_c(native_method_fd);

  __ li(R0, 0);
  __ ld(R11_scratch1, 0, R1_SP);
  __ std(R3_RET, _ijava_state_neg(lresult), R11_scratch1);
  __ stfd(F1_RET, _ijava_state_neg(fresult), R11_scratch1);
  __ std(R0/*mirror*/, _ijava_state_neg(oop_tmp), R11_scratch1); // reset

  // Note: C++ interpreter needs the following here:
  // The frame_manager_lr field, which we use for setting the last
  // java frame, gets overwritten by the signature handler. Restore
  // it now.
  //__ get_PC_trash_LR(R11_scratch1);
  //__ std(R11_scratch1, _top_ijava_frame_abi(frame_manager_lr), R1_SP);

  // Because of GC R19_method may no longer be valid.

  // Block, if necessary, before resuming in _thread_in_Java state.
  // In order for GC to work, don't clear the last_Java_sp until after
  // blocking.

  //=============================================================================
  // Switch thread to "native transition" state before reading the
  // synchronization state. This additional state is necessary
  // because reading and testing the synchronization state is not
  // atomic w.r.t. GC, as this scenario demonstrates: Java thread A,
  // in _thread_in_native state, loads _not_synchronized and is
  // preempted. VM thread changes sync state to synchronizing and
  // suspends threads for GC. Thread A is resumed to finish this
  // native method, but doesn't block here since it didn't see any
  // synchronization in progress, and escapes.

  // We use release_store_fence to update values like the thread state, where
  // we don't want the current thread to continue until all our prior memory
  // accesses (including the new thread state) are visible to other threads.
  __ li(R0/*thread_state*/, _thread_in_native_trans);
  __ release();
  __ stw(R0/*thread_state*/, thread_(thread_state));
  if (UseMembar) {
    __ fence();
  }
  // Write serialization page so that the VM thread can do a pseudo remote
  // membar. We use the current thread pointer to calculate a thread
  // specific offset to write to within the page. This minimizes bus
  // traffic due to cache line collision.
  else {
    __ serialize_memory(R16_thread, R11_scratch1, R12_scratch2);
  }

  // Now before we return to java we must look for a current safepoint
  // (a new safepoint can not start since we entered native_trans).
  // We must check here because a current safepoint could be modifying
  // the callers registers right this moment.

  // Acquire isn't strictly necessary here because of the fence, but
  // sync_state is declared to be volatile, so we do it anyway
  // (cmp-br-isync on one path, release (same as acquire on PPC64) on the other path).
  int sync_state_offs = __ load_const_optimized(sync_state_addr, SafepointSynchronize::address_of_state(), /*temp*/R0, true);

  // TODO PPC port assert(4 == SafepointSynchronize::sz_state(), "unexpected field size");
  __ lwz(sync_state, sync_state_offs, sync_state_addr);

  // TODO PPC port assert(4 == Thread::sz_suspend_flags(), "unexpected field size");
  __ lwz(suspend_flags, thread_(suspend_flags));

  Label sync_check_done;
  Label do_safepoint;
  // No synchronization in progress nor yet synchronized.
  __ cmpwi(CCR0, sync_state, SafepointSynchronize::_not_synchronized);
  // Not suspended.
  __ cmpwi(CCR1, suspend_flags, 0);

  __ bne(CCR0, do_safepoint);
  __ beq(CCR1, sync_check_done);
  __ bind(do_safepoint);
  __ isync();
  // Block. We do the call directly and leave the current
  // last_Java_frame setup undisturbed. We must save any possible
  // native result across the call. No oop is present.

  __ mr(R3_ARG1, R16_thread);
#if defined(ABI_ELFv2)
  __ call_c(CAST_FROM_FN_PTR(address, JavaThread::check_special_condition_for_native_trans),
            relocInfo::none);
#else
  __ call_c(CAST_FROM_FN_PTR(FunctionDescriptor*, JavaThread::check_special_condition_for_native_trans),
            relocInfo::none);
#endif

  __ bind(sync_check_done);

  //=============================================================================
  // <<<<<< Back in Interpreter Frame >>>>>

  // We are in thread_in_native_trans here and back in the normal
  // interpreter frame. We don't have to do anything special about
  // safepoints and we can switch to Java mode anytime we are ready.

  // Note: frame::interpreter_frame_result has a dependency on how the
  // method result is saved across the call to post_method_exit. For
  // native methods it assumes that the non-FPU/non-void result is
  // saved in _native_lresult and a FPU result in _native_fresult. If
  // this changes then the interpreter_frame_result implementation
  // will need to be updated too.

  // On PPC64, we have stored the result directly after the native call.

  //=============================================================================
  // Back in Java

  // We use release_store_fence to update values like the thread state, where
  // we don't want the current thread to continue until all our prior memory
  // accesses (including the new thread state) are visible to other threads.
  __ li(R0/*thread_state*/, _thread_in_Java);
  __ release();
  __ stw(R0/*thread_state*/, thread_(thread_state));
  if (UseMembar) {
    __ fence();
  }

  __ reset_last_Java_frame();

  // Jvmdi/jvmpi support. Whether we've got an exception pending or
  // not, and whether unlocking throws an exception or not, we notify
  // on native method exit. If we do have an exception, we'll end up
  // in the caller's context to handle it, so if we don't do the
  // notify here, we'll drop it on the floor.
  __ notify_method_exit(true/*native method*/,
                        ilgl /*illegal state (not used for native methods)*/,
                        InterpreterMacroAssembler::NotifyJVMTI,
                        false /*check_exceptions*/);

  //=============================================================================
  // Handle exceptions

  if (synchronized) {
    // Don't check for exceptions since we're still in the i2n frame. Do that
    // manually afterwards.
    unlock_method(false);
  }

  // Reset active handles after returning from native.
  // thread->active_handles()->clear();
  __ ld(active_handles, thread_(active_handles));
  // TODO PPC port assert(4 == JNIHandleBlock::top_size_in_bytes(), "unexpected field size");
  __ li(R0, 0);
  __ stw(R0, JNIHandleBlock::top_offset_in_bytes(), active_handles);

  Label exception_return_sync_check_already_unlocked;
  __ ld(R0/*pending_exception*/, thread_(pending_exception));
  __ cmpdi(CCR0, R0/*pending_exception*/, 0);
  __ bne(CCR0, exception_return_sync_check_already_unlocked);

  //-----------------------------------------------------------------------------
  // No exception pending.

  // Move native method result back into proper registers and return.
  // Invoke result handler (may unbox/promote).
  __ ld(R11_scratch1, 0, R1_SP);
  __ ld(R3_RET, _ijava_state_neg(lresult), R11_scratch1);
  __ lfd(F1_RET, _ijava_state_neg(fresult), R11_scratch1);
  __ call_stub(result_handler_addr);

  __ merge_frames(/*top_frame_sp*/ R21_sender_SP, /*return_pc*/ R0, R11_scratch1, R12_scratch2);

  // Must use the return pc which was loaded from the caller's frame
  // as the VM uses return-pc-patching for deoptimization.
  __ mtlr(R0);
  __ blr();

  //-----------------------------------------------------------------------------
  // An exception is pending. We call into the runtime only if the
  // caller was not interpreted. If it was interpreted the
  // interpreter will do the correct thing. If it isn't interpreted
  // (call stub/compiled code) we will change our return and continue.

  BIND(exception_return_sync_check);

  if (synchronized) {
    // Don't check for exceptions since we're still in the i2n frame. Do that
    // manually afterwards.
    unlock_method(false);
  }
  BIND(exception_return_sync_check_already_unlocked);

  const Register return_pc = R31;

  __ ld(return_pc, 0, R1_SP);
  __ ld(return_pc, _abi(lr), return_pc);

  // Get the address of the exception handler.
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address),
                  R16_thread,
                  return_pc /* return pc */);
  __ merge_frames(/*top_frame_sp*/ R21_sender_SP, noreg, R11_scratch1, R12_scratch2);

  // Load the PC of the the exception handler into LR.
  __ mtlr(R3_RET);

  // Load exception into R3_ARG1 and clear pending exception in thread.
  __ ld(R3_ARG1/*exception*/, thread_(pending_exception));
  __ li(R4_ARG2, 0);
  __ std(R4_ARG2, thread_(pending_exception));

  // Load the original return pc into R4_ARG2.
  __ mr(R4_ARG2/*issuing_pc*/, return_pc);

  // Return to exception handler.
  __ blr();

  //=============================================================================
  // Counter overflow.

  if (inc_counter) {
    // Handle invocation counter overflow.
    __ bind(invocation_counter_overflow);

    generate_counter_overflow(continue_after_compile);
  }

  return entry;
}

// Generic interpreted method entry to (asm) interpreter.
//
address TemplateInterpreterGenerator::generate_normal_entry(bool synchronized) {
  bool inc_counter = UseCompiler || CountCompiledCalls || LogTouchedMethods;
  address entry = __ pc();
  // Generate the code to allocate the interpreter stack frame.
  Register Rsize_of_parameters = R4_ARG2, // Written by generate_fixed_frame.
           Rsize_of_locals     = R5_ARG3; // Written by generate_fixed_frame.

  generate_fixed_frame(false, Rsize_of_parameters, Rsize_of_locals);

  // --------------------------------------------------------------------------
  // Zero out non-parameter locals.
  // Note: *Always* zero out non-parameter locals as Sparc does. It's not
  // worth to ask the flag, just do it.
  Register Rslot_addr = R6_ARG4,
           Rnum       = R7_ARG5;
  Label Lno_locals, Lzero_loop;

  // Set up the zeroing loop.
  __ subf(Rnum, Rsize_of_parameters, Rsize_of_locals);
  __ subf(Rslot_addr, Rsize_of_parameters, R18_locals);
  __ srdi_(Rnum, Rnum, Interpreter::logStackElementSize);
  __ beq(CCR0, Lno_locals);
  __ li(R0, 0);
  __ mtctr(Rnum);

  // The zero locals loop.
  __ bind(Lzero_loop);
  __ std(R0, 0, Rslot_addr);
  __ addi(Rslot_addr, Rslot_addr, -Interpreter::stackElementSize);
  __ bdnz(Lzero_loop);

  __ bind(Lno_locals);

  // --------------------------------------------------------------------------
  // Counter increment and overflow check.
  Label invocation_counter_overflow,
        profile_method,
        profile_method_continue;
  if (inc_counter || ProfileInterpreter) {

    Register Rdo_not_unlock_if_synchronized_addr = R11_scratch1;
    if (synchronized) {
      // Since at this point in the method invocation the exception handler
      // would try to exit the monitor of synchronized methods which hasn't
      // been entered yet, we set the thread local variable
      // _do_not_unlock_if_synchronized to true. If any exception was thrown by
      // runtime, exception handling i.e. unlock_if_synchronized_method will
      // check this thread local flag.
      // This flag has two effects, one is to force an unwind in the topmost
      // interpreter frame and not perform an unlock while doing so.
      __ li(R0, 1);
      __ stb(R0, in_bytes(JavaThread::do_not_unlock_if_synchronized_offset()), R16_thread);
    }

    // Argument and return type profiling.
    __ profile_parameters_type(R3_ARG1, R4_ARG2, R5_ARG3, R6_ARG4);

    // Increment invocation counter and check for overflow.
    if (inc_counter) {
      generate_counter_incr(&invocation_counter_overflow, &profile_method, &profile_method_continue);
    }

    __ bind(profile_method_continue);

    // Reset the _do_not_unlock_if_synchronized flag.
    if (synchronized) {
      __ li(R0, 0);
      __ stb(R0, in_bytes(JavaThread::do_not_unlock_if_synchronized_offset()), R16_thread);
    }
  }

  // --------------------------------------------------------------------------
  // Locking of synchronized methods. Must happen AFTER invocation_counter
  // check and stack overflow check, so method is not locked if overflows.
  if (synchronized) {
    lock_method(R3_ARG1, R4_ARG2, R5_ARG3);
  }
#ifdef ASSERT
  else {
    Label Lok;
    __ lwz(R0, in_bytes(Method::access_flags_offset()), R19_method);
    __ andi_(R0, R0, JVM_ACC_SYNCHRONIZED);
    __ asm_assert_eq("method needs synchronization", 0x8521);
    __ bind(Lok);
  }
#endif // ASSERT

  __ verify_thread();

  // --------------------------------------------------------------------------
  // JVMTI support
  __ notify_method_entry();

  // --------------------------------------------------------------------------
  // Start executing instructions.
  __ dispatch_next(vtos);

  // --------------------------------------------------------------------------
  // Out of line counter overflow and MDO creation code.
  if (ProfileInterpreter) {
    // We have decided to profile this method in the interpreter.
    __ bind(profile_method);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::profile_method));
    __ set_method_data_pointer_for_bcp();
    __ b(profile_method_continue);
  }

  if (inc_counter) {
    // Handle invocation counter overflow.
    __ bind(invocation_counter_overflow);
    generate_counter_overflow(profile_method_continue);
  }
  return entry;
}

// CRC32 Intrinsics.
//
// Contract on scratch and work registers.
// =======================================
//
// On ppc, the register set {R2..R12} is available in the interpreter as scratch/work registers.
// You should, however, keep in mind that {R3_ARG1..R10_ARG8} is the C-ABI argument register set.
// You can't rely on these registers across calls.
//
// The generators for CRC32_update and for CRC32_updateBytes use the
// scratch/work register set internally, passing the work registers
// as arguments to the MacroAssembler emitters as required.
//
// R3_ARG1..R6_ARG4 are preset to hold the incoming java arguments.
// Their contents is not constant but may change according to the requirements
// of the emitted code.
//
// All other registers from the scratch/work register set are used "internally"
// and contain garbage (i.e. unpredictable values) once blr() is reached.
// Basically, only R3_RET contains a defined value which is the function result.
//
/**
 * Method entry for static native methods:
 *   int java.util.zip.CRC32.update(int crc, int b)
 */
address InterpreterGenerator::generate_CRC32_update_entry() {
  if (UseCRC32Intrinsics) {
    address start = __ pc();  // Remember stub start address (is rtn value).
    Label slow_path;

    // Safepoint check
    const Register sync_state = R11_scratch1;
    int sync_state_offs = __ load_const_optimized(sync_state, SafepointSynchronize::address_of_state(), /*temp*/R0, true);
    __ lwz(sync_state, sync_state_offs, sync_state);
    __ cmpwi(CCR0, sync_state, SafepointSynchronize::_not_synchronized);
    __ bne(CCR0, slow_path);

    // We don't generate local frame and don't align stack because
    // we not even call stub code (we generate the code inline)
    // and there is no safepoint on this path.

    // Load java parameters.
    // R15_esp is callers operand stack pointer, i.e. it points to the parameters.
    const Register argP    = R15_esp;
    const Register crc     = R3_ARG1;  // crc value
    const Register data    = R4_ARG2;  // address of java byte value (kernel_crc32 needs address)
    const Register dataLen = R5_ARG3;  // source data len (1 byte). Not used because calling the single-byte emitter.
    const Register table   = R6_ARG4;  // address of crc32 table
    const Register tmp     = dataLen;  // Reuse unused len register to show we don't actually need a separate tmp here.

    BLOCK_COMMENT("CRC32_update {");

    // Arguments are reversed on java expression stack
#ifdef VM_LITTLE_ENDIAN
    __ addi(data, argP, 0+1*wordSize); // (stack) address of byte value. Emitter expects address, not value.
                                       // Being passed as an int, the single byte is at offset +0.
#else
    __ addi(data, argP, 3+1*wordSize); // (stack) address of byte value. Emitter expects address, not value.
                                       // Being passed from java as an int, the single byte is at offset +3.
#endif
    __ lwz(crc,  2*wordSize, argP);    // Current crc state, zero extend to 64 bit to have a clean register.

    StubRoutines::ppc64::generate_load_crc_table_addr(_masm, table);
    __ kernel_crc32_singleByte(crc, data, dataLen, table, tmp);

    // Restore caller sp for c2i case and return.
    __ mr(R1_SP, R21_sender_SP); // Cut the stack back to where the caller started.
    __ blr();

    // Generate a vanilla native entry as the slow path.
    BLOCK_COMMENT("} CRC32_update");
    BIND(slow_path);
    __ jump_to_entry(Interpreter::entry_for_kind(Interpreter::native), R11_scratch1);
    return start;
  }

  return NULL;
}

// CRC32 Intrinsics.
/**
 * Method entry for static native methods:
 *   int java.util.zip.CRC32.updateBytes(     int crc, byte[] b,  int off, int len)
 *   int java.util.zip.CRC32.updateByteBuffer(int crc, long* buf, int off, int len)
 */
address InterpreterGenerator::generate_CRC32_updateBytes_entry(AbstractInterpreter::MethodKind kind) {
  if (UseCRC32Intrinsics) {
    address start = __ pc();  // Remember stub start address (is rtn value).
    Label slow_path;

    // Safepoint check
    const Register sync_state = R11_scratch1;
    int sync_state_offs = __ load_const_optimized(sync_state, SafepointSynchronize::address_of_state(), /*temp*/R0, true);
    __ lwz(sync_state, sync_state_offs, sync_state);
    __ cmpwi(CCR0, sync_state, SafepointSynchronize::_not_synchronized);
    __ bne(CCR0, slow_path);

    // We don't generate local frame and don't align stack because
    // we not even call stub code (we generate the code inline)
    // and there is no safepoint on this path.

    // Load parameters.
    // Z_esp is callers operand stack pointer, i.e. it points to the parameters.
    const Register argP    = R15_esp;
    const Register crc     = R3_ARG1;  // crc value
    const Register data    = R4_ARG2;  // address of java byte array
    const Register dataLen = R5_ARG3;  // source data len
    const Register table   = R6_ARG4;  // address of crc32 table

    const Register t0      = R9;       // scratch registers for crc calculation
    const Register t1      = R10;
    const Register t2      = R11;
    const Register t3      = R12;

    const Register tc0     = R2;       // registers to hold pre-calculated column addresses
    const Register tc1     = R7;
    const Register tc2     = R8;
    const Register tc3     = table;    // table address is reconstructed at the end of kernel_crc32_* emitters

    const Register tmp     = t0;       // Only used very locally to calculate byte buffer address.

    // Arguments are reversed on java expression stack.
    // Calculate address of start element.
    if (kind == Interpreter::java_util_zip_CRC32_updateByteBuffer) { // Used for "updateByteBuffer direct".
      BLOCK_COMMENT("CRC32_updateByteBuffer {");
      // crc     @ (SP + 5W) (32bit)
      // buf     @ (SP + 3W) (64bit ptr to long array)
      // off     @ (SP + 2W) (32bit)
      // dataLen @ (SP + 1W) (32bit)
      // data = buf + off
      __ ld(  data,    3*wordSize, argP);  // start of byte buffer
      __ lwa( tmp,     2*wordSize, argP);  // byte buffer offset
      __ lwa( dataLen, 1*wordSize, argP);  // #bytes to process
      __ lwz( crc,     5*wordSize, argP);  // current crc state
      __ add( data, data, tmp);            // Add byte buffer offset.
    } else {                                                         // Used for "updateBytes update".
      BLOCK_COMMENT("CRC32_updateBytes {");
      // crc     @ (SP + 4W) (32bit)
      // buf     @ (SP + 3W) (64bit ptr to byte array)
      // off     @ (SP + 2W) (32bit)
      // dataLen @ (SP + 1W) (32bit)
      // data = buf + off + base_offset
      __ ld(  data,    3*wordSize, argP);  // start of byte buffer
      __ lwa( tmp,     2*wordSize, argP);  // byte buffer offset
      __ lwa( dataLen, 1*wordSize, argP);  // #bytes to process
      __ add( data, data, tmp);            // add byte buffer offset
      __ lwz( crc,     4*wordSize, argP);  // current crc state
      __ addi(data, data, arrayOopDesc::base_offset_in_bytes(T_BYTE));
    }

    StubRoutines::ppc64::generate_load_crc_table_addr(_masm, table);

    // Performance measurements show the 1word and 2word variants to be almost equivalent,
    // with very light advantages for the 1word variant. We chose the 1word variant for
    // code compactness.
    __ kernel_crc32_1word(crc, data, dataLen, table, t0, t1, t2, t3, tc0, tc1, tc2, tc3);

    // Restore caller sp for c2i case and return.
    __ mr(R1_SP, R21_sender_SP); // Cut the stack back to where the caller started.
    __ blr();

    // Generate a vanilla native entry as the slow path.
    BLOCK_COMMENT("} CRC32_updateBytes(Buffer)");
    BIND(slow_path);
    __ jump_to_entry(Interpreter::entry_for_kind(Interpreter::native), R11_scratch1);
    return start;
  }

  return NULL;
}

// These should never be compiled since the interpreter will prefer
// the compiled version to the intrinsic version.
bool AbstractInterpreter::can_be_compiled(methodHandle m) {
  return !math_entry_available(method_kind(m));
}

// How much stack a method activation needs in stack slots.
// We must calc this exactly like in generate_fixed_frame.
// Note: This returns the conservative size assuming maximum alignment.
int AbstractInterpreter::size_top_interpreter_activation(Method* method) {
  const int max_alignment_size = 2;
  const int abi_scratch = frame::abi_reg_args_size;
  return method->max_locals() + method->max_stack() +
         frame::interpreter_frame_monitor_size() + max_alignment_size + abi_scratch;
}

// Returns number of stackElementWords needed for the interpreter frame with the
// given sections.
// This overestimates the stack by one slot in case of alignments.
int AbstractInterpreter::size_activation(int max_stack,
                                         int temps,
                                         int extra_args,
                                         int monitors,
                                         int callee_params,
                                         int callee_locals,
                                         bool is_top_frame) {
  // Note: This calculation must exactly parallel the frame setup
  // in InterpreterGenerator::generate_fixed_frame.
  assert(Interpreter::stackElementWords == 1, "sanity");
  const int max_alignment_space = StackAlignmentInBytes / Interpreter::stackElementSize;
  const int abi_scratch = is_top_frame ? (frame::abi_reg_args_size / Interpreter::stackElementSize) :
                                         (frame::abi_minframe_size / Interpreter::stackElementSize);
  const int size =
    max_stack                                                +
    (callee_locals - callee_params)                          +
    monitors * frame::interpreter_frame_monitor_size()       +
    max_alignment_space                                      +
    abi_scratch                                              +
    frame::ijava_state_size / Interpreter::stackElementSize;

  // Fixed size of an interpreter frame, align to 16-byte.
  return (size & -2);
}

// Fills a sceletal interpreter frame generated during deoptimizations.
//
// Parameters:
//
// interpreter_frame != NULL:
//   set up the method, locals, and monitors.
//   The frame interpreter_frame, if not NULL, is guaranteed to be the
//   right size, as determined by a previous call to this method.
//   It is also guaranteed to be walkable even though it is in a skeletal state
//
// is_top_frame == true:
//   We're processing the *oldest* interpreter frame!
//
// pop_frame_extra_args:
//   If this is != 0 we are returning to a deoptimized frame by popping
//   off the callee frame. We want to re-execute the call that called the
//   callee interpreted, but since the return to the interpreter would pop
//   the arguments off advance the esp by dummy popframe_extra_args slots.
//   Popping off those will establish the stack layout as it was before the call.
//
void AbstractInterpreter::layout_activation(Method* method,
                                            int tempcount,
                                            int popframe_extra_args,
                                            int moncount,
                                            int caller_actual_parameters,
                                            int callee_param_count,
                                            int callee_locals_count,
                                            frame* caller,
                                            frame* interpreter_frame,
                                            bool is_top_frame,
                                            bool is_bottom_frame) {

  const int abi_scratch = is_top_frame ? (frame::abi_reg_args_size / Interpreter::stackElementSize) :
                                         (frame::abi_minframe_size / Interpreter::stackElementSize);

  intptr_t* locals_base  = (caller->is_interpreted_frame()) ?
    caller->interpreter_frame_esp() + caller_actual_parameters :
    caller->sp() + method->max_locals() - 1 + (frame::abi_minframe_size / Interpreter::stackElementSize);

  intptr_t* monitor_base = caller->sp() - frame::ijava_state_size / Interpreter::stackElementSize;
  intptr_t* monitor      = monitor_base - (moncount * frame::interpreter_frame_monitor_size());
  intptr_t* esp_base     = monitor - 1;
  intptr_t* esp          = esp_base - tempcount - popframe_extra_args;
  intptr_t* sp           = (intptr_t *) (((intptr_t) (esp_base - callee_locals_count + callee_param_count - method->max_stack()- abi_scratch)) & -StackAlignmentInBytes);
  intptr_t* sender_sp    = caller->sp() + (frame::abi_minframe_size - frame::abi_reg_args_size) / Interpreter::stackElementSize;
  intptr_t* top_frame_sp = is_top_frame ? sp : sp + (frame::abi_minframe_size - frame::abi_reg_args_size) / Interpreter::stackElementSize;

  interpreter_frame->interpreter_frame_set_method(method);
  interpreter_frame->interpreter_frame_set_locals(locals_base);
  interpreter_frame->interpreter_frame_set_cpcache(method->constants()->cache());
  interpreter_frame->interpreter_frame_set_esp(esp);
  interpreter_frame->interpreter_frame_set_monitor_end((BasicObjectLock *)monitor);
  interpreter_frame->interpreter_frame_set_top_frame_sp(top_frame_sp);
  if (!is_bottom_frame) {
    interpreter_frame->interpreter_frame_set_sender_sp(sender_sp);
  }
}

// =============================================================================
// Exceptions

void TemplateInterpreterGenerator::generate_throw_exception() {
  Register Rexception    = R17_tos,
           Rcontinuation = R3_RET;

  // --------------------------------------------------------------------------
  // Entry point if an method returns with a pending exception (rethrow).
  Interpreter::_rethrow_exception_entry = __ pc();
  {
    __ restore_interpreter_state(R11_scratch1); // Sets R11_scratch1 = fp.
    __ ld(R12_scratch2, _ijava_state_neg(top_frame_sp), R11_scratch1);
    __ resize_frame_absolute(R12_scratch2, R11_scratch1, R0);

    // Compiled code destroys templateTableBase, reload.
    __ load_const_optimized(R25_templateTableBase, (address)Interpreter::dispatch_table((TosState)0), R11_scratch1);
  }

  // Entry point if a interpreted method throws an exception (throw).
  Interpreter::_throw_exception_entry = __ pc();
  {
    __ mr(Rexception, R3_RET);

    __ verify_thread();
    __ verify_oop(Rexception);

    // Expression stack must be empty before entering the VM in case of an exception.
    __ empty_expression_stack();
    // Find exception handler address and preserve exception oop.
    // Call C routine to find handler and jump to it.
    __ call_VM(Rexception, CAST_FROM_FN_PTR(address, InterpreterRuntime::exception_handler_for_exception), Rexception);
    __ mtctr(Rcontinuation);
    // Push exception for exception handler bytecodes.
    __ push_ptr(Rexception);

    // Jump to exception handler (may be remove activation entry!).
    __ bctr();
  }

  // If the exception is not handled in the current frame the frame is
  // removed and the exception is rethrown (i.e. exception
  // continuation is _rethrow_exception).
  //
  // Note: At this point the bci is still the bxi for the instruction
  // which caused the exception and the expression stack is
  // empty. Thus, for any VM calls at this point, GC will find a legal
  // oop map (with empty expression stack).

  // In current activation
  // tos: exception
  // bcp: exception bcp

  // --------------------------------------------------------------------------
  // JVMTI PopFrame support

  Interpreter::_remove_activation_preserving_args_entry = __ pc();
  {
    // Set the popframe_processing bit in popframe_condition indicating that we are
    // currently handling popframe, so that call_VMs that may happen later do not
    // trigger new popframe handling cycles.
    __ lwz(R11_scratch1, in_bytes(JavaThread::popframe_condition_offset()), R16_thread);
    __ ori(R11_scratch1, R11_scratch1, JavaThread::popframe_processing_bit);
    __ stw(R11_scratch1, in_bytes(JavaThread::popframe_condition_offset()), R16_thread);

    // Empty the expression stack, as in normal exception handling.
    __ empty_expression_stack();
    __ unlock_if_synchronized_method(vtos, /* throw_monitor_exception */ false, /* install_monitor_exception */ false);

    // Check to see whether we are returning to a deoptimized frame.
    // (The PopFrame call ensures that the caller of the popped frame is
    // either interpreted or compiled and deoptimizes it if compiled.)
    // Note that we don't compare the return PC against the
    // deoptimization blob's unpack entry because of the presence of
    // adapter frames in C2.
    Label Lcaller_not_deoptimized;
    Register return_pc = R3_ARG1;
    __ ld(return_pc, 0, R1_SP);
    __ ld(return_pc, _abi(lr), return_pc);
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, InterpreterRuntime::interpreter_contains), return_pc);
    __ cmpdi(CCR0, R3_RET, 0);
    __ bne(CCR0, Lcaller_not_deoptimized);

    // The deoptimized case.
    // In this case, we can't call dispatch_next() after the frame is
    // popped, but instead must save the incoming arguments and restore
    // them after deoptimization has occurred.
    __ ld(R4_ARG2, in_bytes(Method::const_offset()), R19_method);
    __ lhz(R4_ARG2 /* number of params */, in_bytes(ConstMethod::size_of_parameters_offset()), R4_ARG2);
    __ slwi(R4_ARG2, R4_ARG2, Interpreter::logStackElementSize);
    __ addi(R5_ARG3, R18_locals, Interpreter::stackElementSize);
    __ subf(R5_ARG3, R4_ARG2, R5_ARG3);
    // Save these arguments.
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, Deoptimization::popframe_preserve_args), R16_thread, R4_ARG2, R5_ARG3);

    // Inform deoptimization that it is responsible for restoring these arguments.
    __ load_const_optimized(R11_scratch1, JavaThread::popframe_force_deopt_reexecution_bit);
    __ stw(R11_scratch1, in_bytes(JavaThread::popframe_condition_offset()), R16_thread);

    // Return from the current method into the deoptimization blob. Will eventually
    // end up in the deopt interpeter entry, deoptimization prepared everything that
    // we will reexecute the call that called us.
    __ merge_frames(/*top_frame_sp*/ R21_sender_SP, /*reload return_pc*/ return_pc, R11_scratch1, R12_scratch2);
    __ mtlr(return_pc);
    __ blr();

    // The non-deoptimized case.
    __ bind(Lcaller_not_deoptimized);

    // Clear the popframe condition flag.
    __ li(R0, 0);
    __ stw(R0, in_bytes(JavaThread::popframe_condition_offset()), R16_thread);

    // Get out of the current method and re-execute the call that called us.
    __ merge_frames(/*top_frame_sp*/ R21_sender_SP, /*return_pc*/ noreg, R11_scratch1, R12_scratch2);
    __ restore_interpreter_state(R11_scratch1);
    __ ld(R12_scratch2, _ijava_state_neg(top_frame_sp), R11_scratch1);
    __ resize_frame_absolute(R12_scratch2, R11_scratch1, R0);
    if (ProfileInterpreter) {
      __ set_method_data_pointer_for_bcp();
      __ ld(R11_scratch1, 0, R1_SP);
      __ std(R28_mdx, _ijava_state_neg(mdx), R11_scratch1);
    }
#if INCLUDE_JVMTI
    Label L_done;

    __ lbz(R11_scratch1, 0, R14_bcp);
    __ cmpwi(CCR0, R11_scratch1, Bytecodes::_invokestatic);
    __ bne(CCR0, L_done);

    // The member name argument must be restored if _invokestatic is re-executed after a PopFrame call.
    // Detect such a case in the InterpreterRuntime function and return the member name argument, or NULL.
    __ ld(R4_ARG2, 0, R18_locals);
    __ MacroAssembler::call_VM(R4_ARG2, CAST_FROM_FN_PTR(address, InterpreterRuntime::member_name_arg_or_null), R4_ARG2, R19_method, R14_bcp, false);
    __ restore_interpreter_state(R11_scratch1, /*bcp_and_mdx_only*/ true);
    __ cmpdi(CCR0, R4_ARG2, 0);
    __ beq(CCR0, L_done);
    __ std(R4_ARG2, wordSize, R15_esp);
    __ bind(L_done);
#endif // INCLUDE_JVMTI
    __ dispatch_next(vtos);
  }
  // end of JVMTI PopFrame support

  // --------------------------------------------------------------------------
  // Remove activation exception entry.
  // This is jumped to if an interpreted method can't handle an exception itself
  // (we come from the throw/rethrow exception entry above). We're going to call
  // into the VM to find the exception handler in the caller, pop the current
  // frame and return the handler we calculated.
  Interpreter::_remove_activation_entry = __ pc();
  {
    __ pop_ptr(Rexception);
    __ verify_thread();
    __ verify_oop(Rexception);
    __ std(Rexception, in_bytes(JavaThread::vm_result_offset()), R16_thread);

    __ unlock_if_synchronized_method(vtos, /* throw_monitor_exception */ false, true);
    __ notify_method_exit(false, vtos, InterpreterMacroAssembler::SkipNotifyJVMTI, false);

    __ get_vm_result(Rexception);

    // We are done with this activation frame; find out where to go next.
    // The continuation point will be an exception handler, which expects
    // the following registers set up:
    //
    // RET:  exception oop
    // ARG2: Issuing PC (see generate_exception_blob()), only used if the caller is compiled.

    Register return_pc = R31; // Needs to survive the runtime call.
    __ ld(return_pc, 0, R1_SP);
    __ ld(return_pc, _abi(lr), return_pc);
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address), R16_thread, return_pc);

    // Remove the current activation.
    __ merge_frames(/*top_frame_sp*/ R21_sender_SP, /*return_pc*/ noreg, R11_scratch1, R12_scratch2);

    __ mr(R4_ARG2, return_pc);
    __ mtlr(R3_RET);
    __ mr(R3_RET, Rexception);
    __ blr();
  }
}

// JVMTI ForceEarlyReturn support.
// Returns "in the middle" of a method with a "fake" return value.
address TemplateInterpreterGenerator::generate_earlyret_entry_for(TosState state) {

  Register Rscratch1 = R11_scratch1,
           Rscratch2 = R12_scratch2;

  address entry = __ pc();
  __ empty_expression_stack();

  __ load_earlyret_value(state, Rscratch1);

  __ ld(Rscratch1, in_bytes(JavaThread::jvmti_thread_state_offset()), R16_thread);
  // Clear the earlyret state.
  __ li(R0, 0);
  __ stw(R0, in_bytes(JvmtiThreadState::earlyret_state_offset()), Rscratch1);

  __ remove_activation(state, false, false);
  // Copied from TemplateTable::_return.
  // Restoration of lr done by remove_activation.
  switch (state) {
    case ltos:
    case btos:
    case ctos:
    case stos:
    case atos:
    case itos: __ mr(R3_RET, R17_tos); break;
    case ftos:
    case dtos: __ fmr(F1_RET, F15_ftos); break;
    case vtos: // This might be a constructor. Final fields (and volatile fields on PPC64) need
               // to get visible before the reference to the object gets stored anywhere.
               __ membar(Assembler::StoreStore); break;
    default  : ShouldNotReachHere();
  }
  __ blr();

  return entry;
} // end of ForceEarlyReturn support

//-----------------------------------------------------------------------------
// Helper for vtos entry point generation

void TemplateInterpreterGenerator::set_vtos_entry_points(Template* t,
                                                         address& bep,
                                                         address& cep,
                                                         address& sep,
                                                         address& aep,
                                                         address& iep,
                                                         address& lep,
                                                         address& fep,
                                                         address& dep,
                                                         address& vep) {
  assert(t->is_valid() && t->tos_in() == vtos, "illegal template");
  Label L;

  aep = __ pc();  __ push_ptr();  __ b(L);
  fep = __ pc();  __ push_f();    __ b(L);
  dep = __ pc();  __ push_d();    __ b(L);
  lep = __ pc();  __ push_l();    __ b(L);
  __ align(32, 12, 24); // align L
  bep = cep = sep =
  iep = __ pc();  __ push_i();
  vep = __ pc();
  __ bind(L);
  generate_and_dispatch(t);
}

//-----------------------------------------------------------------------------
// Generation of individual instructions

// helpers for generate_and_dispatch

InterpreterGenerator::InterpreterGenerator(StubQueue* code)
  : TemplateInterpreterGenerator(code) {
  generate_all(); // Down here so it can be "virtual".
}

//-----------------------------------------------------------------------------

// Non-product code
#ifndef PRODUCT
address TemplateInterpreterGenerator::generate_trace_code(TosState state) {
  //__ flush_bundle();
  address entry = __ pc();

  const char *bname = NULL;
  uint tsize = 0;
  switch(state) {
  case ftos:
    bname = "trace_code_ftos {";
    tsize = 2;
    break;
  case btos:
    bname = "trace_code_btos {";
    tsize = 2;
    break;
  case ctos:
    bname = "trace_code_ctos {";
    tsize = 2;
    break;
  case stos:
    bname = "trace_code_stos {";
    tsize = 2;
    break;
  case itos:
    bname = "trace_code_itos {";
    tsize = 2;
    break;
  case ltos:
    bname = "trace_code_ltos {";
    tsize = 3;
    break;
  case atos:
    bname = "trace_code_atos {";
    tsize = 2;
    break;
  case vtos:
    // Note: In case of vtos, the topmost of stack value could be a int or doubl
    // In case of a double (2 slots) we won't see the 2nd stack value.
    // Maybe we simply should print the topmost 3 stack slots to cope with the problem.
    bname = "trace_code_vtos {";
    tsize = 2;

    break;
  case dtos:
    bname = "trace_code_dtos {";
    tsize = 3;
    break;
  default:
    ShouldNotReachHere();
  }
  BLOCK_COMMENT(bname);

  // Support short-cut for TraceBytecodesAt.
  // Don't call into the VM if we don't want to trace to speed up things.
  Label Lskip_vm_call;
  if (TraceBytecodesAt > 0 && TraceBytecodesAt < max_intx) {
    int offs1 = __ load_const_optimized(R11_scratch1, (address) &TraceBytecodesAt, R0, true);
    int offs2 = __ load_const_optimized(R12_scratch2, (address) &BytecodeCounter::_counter_value, R0, true);
    __ ld(R11_scratch1, offs1, R11_scratch1);
    __ lwa(R12_scratch2, offs2, R12_scratch2);
    __ cmpd(CCR0, R12_scratch2, R11_scratch1);
    __ blt(CCR0, Lskip_vm_call);
  }

  __ push(state);
  // Load 2 topmost expression stack values.
  __ ld(R6_ARG4, tsize*Interpreter::stackElementSize, R15_esp);
  __ ld(R5_ARG3, Interpreter::stackElementSize, R15_esp);
  __ mflr(R31);
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, SharedRuntime::trace_bytecode), /* unused */ R4_ARG2, R5_ARG3, R6_ARG4, false);
  __ mtlr(R31);
  __ pop(state);

  if (TraceBytecodesAt > 0 && TraceBytecodesAt < max_intx) {
    __ bind(Lskip_vm_call);
  }
  __ blr();
  BLOCK_COMMENT("} trace_code");
  return entry;
}

void TemplateInterpreterGenerator::count_bytecode() {
  int offs = __ load_const_optimized(R11_scratch1, (address) &BytecodeCounter::_counter_value, R12_scratch2, true);
  __ lwz(R12_scratch2, offs, R11_scratch1);
  __ addi(R12_scratch2, R12_scratch2, 1);
  __ stw(R12_scratch2, offs, R11_scratch1);
}

void TemplateInterpreterGenerator::histogram_bytecode(Template* t) {
  int offs = __ load_const_optimized(R11_scratch1, (address) &BytecodeHistogram::_counters[t->bytecode()], R12_scratch2, true);
  __ lwz(R12_scratch2, offs, R11_scratch1);
  __ addi(R12_scratch2, R12_scratch2, 1);
  __ stw(R12_scratch2, offs, R11_scratch1);
}

void TemplateInterpreterGenerator::histogram_bytecode_pair(Template* t) {
  const Register addr = R11_scratch1,
                 tmp  = R12_scratch2;
  // Get index, shift out old bytecode, bring in new bytecode, and store it.
  // _index = (_index >> log2_number_of_codes) |
  //          (bytecode << log2_number_of_codes);
  int offs1 = __ load_const_optimized(addr, (address)&BytecodePairHistogram::_index, tmp, true);
  __ lwz(tmp, offs1, addr);
  __ srwi(tmp, tmp, BytecodePairHistogram::log2_number_of_codes);
  __ ori(tmp, tmp, ((int) t->bytecode()) << BytecodePairHistogram::log2_number_of_codes);
  __ stw(tmp, offs1, addr);

  // Bump bucket contents.
  // _counters[_index] ++;
  int offs2 = __ load_const_optimized(addr, (address)&BytecodePairHistogram::_counters, R0, true);
  __ sldi(tmp, tmp, LogBytesPerInt);
  __ add(addr, tmp, addr);
  __ lwz(tmp, offs2, addr);
  __ addi(tmp, tmp, 1);
  __ stw(tmp, offs2, addr);
}

void TemplateInterpreterGenerator::trace_bytecode(Template* t) {
  // Call a little run-time stub to avoid blow-up for each bytecode.
  // The run-time runtime saves the right registers, depending on
  // the tosca in-state for the given template.

  assert(Interpreter::trace_code(t->tos_in()) != NULL,
         "entry must have been generated");

  // Note: we destroy LR here.
  __ bl(Interpreter::trace_code(t->tos_in()));
}

void TemplateInterpreterGenerator::stop_interpreter_at() {
  Label L;
  int offs1 = __ load_const_optimized(R11_scratch1, (address) &StopInterpreterAt, R0, true);
  int offs2 = __ load_const_optimized(R12_scratch2, (address) &BytecodeCounter::_counter_value, R0, true);
  __ ld(R11_scratch1, offs1, R11_scratch1);
  __ lwa(R12_scratch2, offs2, R12_scratch2);
  __ cmpd(CCR0, R12_scratch2, R11_scratch1);
  __ bne(CCR0, L);
  __ illtrap();
  __ bind(L);
}

#endif // !PRODUCT
#endif // !CC_INTERP
