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
#include "asm/assembler.hpp"
#include "interpreter/bytecodeHistogram.hpp"
#include "interpreter/interp_masm.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "interpreter/templateInterpreterGenerator.hpp"
#include "interpreter/templateTable.hpp"
#include "oops/arrayOop.hpp"
#include "oops/methodData.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "prims/methodHandles.hpp"
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

// Size of interpreter code.  Increase if too small.  Interpreter will
// fail with a guarantee ("not enough space for interpreter generation");
// if too small.
// Run with +PrintInterpreter to get the VM to print out the size.
// Max size with JVMTI
int TemplateInterpreter::InterpreterCodeSize = 180 * 1024;

#define __ _masm->

//------------------------------------------------------------------------------------------------------------------------

address TemplateInterpreterGenerator::generate_slow_signature_handler() {
  address entry = __ pc();

  // callee-save register for saving LR, shared with generate_native_entry
  const Register Rsaved_ret_addr = AARCH64_ONLY(R21) NOT_AARCH64(Rtmp_save0);

  __ mov(Rsaved_ret_addr, LR);

  __ mov(R1, Rmethod);
  __ mov(R2, Rlocals);
  __ mov(R3, SP);

#ifdef AARCH64
  // expand expr. stack and extended SP to avoid cutting SP in call_VM
  __ mov(Rstack_top, SP);
  __ str(Rstack_top, Address(FP, frame::interpreter_frame_extended_sp_offset * wordSize));
  __ check_stack_top();

  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::slow_signature_handler), R1, R2, R3, false);

  __ ldp(ZR,      c_rarg1, Address(SP, 2*wordSize, post_indexed));
  __ ldp(c_rarg2, c_rarg3, Address(SP, 2*wordSize, post_indexed));
  __ ldp(c_rarg4, c_rarg5, Address(SP, 2*wordSize, post_indexed));
  __ ldp(c_rarg6, c_rarg7, Address(SP, 2*wordSize, post_indexed));

  __ ldp_d(V0, V1, Address(SP, 2*wordSize, post_indexed));
  __ ldp_d(V2, V3, Address(SP, 2*wordSize, post_indexed));
  __ ldp_d(V4, V5, Address(SP, 2*wordSize, post_indexed));
  __ ldp_d(V6, V7, Address(SP, 2*wordSize, post_indexed));
#else

  // Safer to save R9 (when scratched) since callers may have been
  // written assuming R9 survives. This is suboptimal but
  // probably not important for this slow case call site.
  // Note for R9 saving: slow_signature_handler may copy register
  // arguments above the current SP (passed as R3). It is safe for
  // call_VM to use push and pop to protect additional values on the
  // stack if needed.
  __ call_VM(CAST_FROM_FN_PTR(address, InterpreterRuntime::slow_signature_handler), true /* save R9 if needed*/);
  __ add(SP, SP, wordSize);     // Skip R0
  __ pop(RegisterSet(R1, R3));  // Load arguments passed in registers
#ifdef __ABI_HARD__
  // Few alternatives to an always-load-FP-registers approach:
  // - parse method signature to detect FP arguments
  // - keep a counter/flag on a stack indicationg number of FP arguments in the method.
  // The later has been originally implemented and tested but a conditional path could
  // eliminate any gain imposed by avoiding 8 double word loads.
  __ fldmiad(SP, FloatRegisterSet(D0, 8), writeback);
#endif // __ABI_HARD__
#endif // AARCH64

  __ ret(Rsaved_ret_addr);

  return entry;
}


//
// Various method entries (that c++ and asm interpreter agree upon)
//------------------------------------------------------------------------------------------------------------------------
//
//

// Abstract method entry
// Attempt to execute abstract method. Throw exception
address TemplateInterpreterGenerator::generate_abstract_entry(void) {
  address entry_point = __ pc();

#ifdef AARCH64
  __ restore_sp_after_call(Rtemp);
  __ restore_stack_top();
#endif

  __ empty_expression_stack();

  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_AbstractMethodError));

  DEBUG_ONLY(STOP("generate_abstract_entry");) // Should not reach here
  return entry_point;
}

address TemplateInterpreterGenerator::generate_math_entry(AbstractInterpreter::MethodKind kind) {
  if (!InlineIntrinsics) return NULL; // Generate a vanilla entry

  // TODO: ARM
  return NULL;

  address entry_point = __ pc();
  STOP("generate_math_entry");
  return entry_point;
}

address TemplateInterpreterGenerator::generate_StackOverflowError_handler() {
  address entry = __ pc();

  // Note: There should be a minimal interpreter frame set up when stack
  // overflow occurs since we check explicitly for it now.
  //
#ifdef ASSERT
  { Label L;
    __ sub(Rtemp, FP, - frame::interpreter_frame_monitor_block_top_offset * wordSize);
    __ cmp(SP, Rtemp);  // Rtemp = maximal SP for current FP,
                        //  (stack grows negative)
    __ b(L, ls); // check if frame is complete
    __ stop ("interpreter frame not set up");
    __ bind(L);
  }
#endif // ASSERT

  // Restore bcp under the assumption that the current frame is still
  // interpreted
  __ restore_bcp();

  // expression stack must be empty before entering the VM if an exception
  // happened
  __ empty_expression_stack();

  // throw exception
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_StackOverflowError));

  __ should_not_reach_here();

  return entry;
}

address TemplateInterpreterGenerator::generate_ArrayIndexOutOfBounds_handler(const char* name) {
  address entry = __ pc();

  // index is in R4_ArrayIndexOutOfBounds_index

  InlinedString Lname(name);

  // expression stack must be empty before entering the VM if an exception happened
  __ empty_expression_stack();

  // setup parameters
  __ ldr_literal(R1, Lname);
  __ mov(R2, R4_ArrayIndexOutOfBounds_index);

  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_ArrayIndexOutOfBoundsException), R1, R2);

  __ nop(); // to avoid filling CPU pipeline with invalid instructions
  __ nop();
  __ should_not_reach_here();
  __ bind_literal(Lname);

  return entry;
}

address TemplateInterpreterGenerator::generate_ClassCastException_handler() {
  address entry = __ pc();

  // object is in R2_ClassCastException_obj

  // expression stack must be empty before entering the VM if an exception
  // happened
  __ empty_expression_stack();

  __ mov(R1, R2_ClassCastException_obj);
  __ call_VM(noreg,
             CAST_FROM_FN_PTR(address,
                              InterpreterRuntime::throw_ClassCastException),
             R1);

  __ should_not_reach_here();

  return entry;
}

address TemplateInterpreterGenerator::generate_exception_handler_common(const char* name, const char* message, bool pass_oop) {
  assert(!pass_oop || message == NULL, "either oop or message but not both");
  address entry = __ pc();

  InlinedString Lname(name);
  InlinedString Lmessage(message);

  if (pass_oop) {
    // object is at TOS
    __ pop_ptr(R2);
  }

  // expression stack must be empty before entering the VM if an exception happened
  __ empty_expression_stack();

  // setup parameters
  __ ldr_literal(R1, Lname);

  if (pass_oop) {
    __ call_VM(Rexception_obj, CAST_FROM_FN_PTR(address, InterpreterRuntime::create_klass_exception), R1, R2);
  } else {
    if (message != NULL) {
      __ ldr_literal(R2, Lmessage);
    } else {
      __ mov(R2, 0);
    }
    __ call_VM(Rexception_obj, CAST_FROM_FN_PTR(address, InterpreterRuntime::create_exception), R1, R2);
  }

  // throw exception
  __ b(Interpreter::throw_exception_entry());

  __ nop(); // to avoid filling CPU pipeline with invalid instructions
  __ nop();
  __ bind_literal(Lname);
  if (!pass_oop && (message != NULL)) {
    __ bind_literal(Lmessage);
  }

  return entry;
}

address TemplateInterpreterGenerator::generate_continuation_for(TosState state) {
  // Not used.
  STOP("generate_continuation_for");
  return NULL;
}

address TemplateInterpreterGenerator::generate_return_entry_for(TosState state, int step, size_t index_size) {
  address entry = __ pc();

  __ interp_verify_oop(R0_tos, state, __FILE__, __LINE__);

#ifdef AARCH64
  __ restore_sp_after_call(Rtemp);  // Restore SP to extended SP
  __ restore_stack_top();
#else
  // Restore stack bottom in case i2c adjusted stack
  __ ldr(SP, Address(FP, frame::interpreter_frame_last_sp_offset * wordSize));
  // and NULL it as marker that SP is now tos until next java call
  __ mov(Rtemp, (int)NULL_WORD);
  __ str(Rtemp, Address(FP, frame::interpreter_frame_last_sp_offset * wordSize));
#endif // AARCH64

  __ restore_method();
  __ restore_bcp();
  __ restore_dispatch();
  __ restore_locals();

  const Register Rcache = R2_tmp;
  const Register Rindex = R3_tmp;
  __ get_cache_and_index_at_bcp(Rcache, Rindex, 1, index_size);

  __ add(Rtemp, Rcache, AsmOperand(Rindex, lsl, LogBytesPerWord));
  __ ldrb(Rtemp, Address(Rtemp, ConstantPoolCache::base_offset() + ConstantPoolCacheEntry::flags_offset()));
  __ check_stack_top();
  __ add(Rstack_top, Rstack_top, AsmOperand(Rtemp, lsl, Interpreter::logStackElementSize));

#ifndef AARCH64
  __ convert_retval_to_tos(state);
#endif // !AARCH64

  __ dispatch_next(state, step);

  return entry;
}


address TemplateInterpreterGenerator::generate_deopt_entry_for(TosState state, int step) {
  address entry = __ pc();

  __ interp_verify_oop(R0_tos, state, __FILE__, __LINE__);

#ifdef AARCH64
  __ restore_sp_after_call(Rtemp);  // Restore SP to extended SP
  __ restore_stack_top();
#else
  // The stack is not extended by deopt but we must NULL last_sp as this
  // entry is like a "return".
  __ mov(Rtemp, 0);
  __ str(Rtemp, Address(FP, frame::interpreter_frame_last_sp_offset * wordSize));
#endif // AARCH64

  __ restore_method();
  __ restore_bcp();
  __ restore_dispatch();
  __ restore_locals();

  // handle exceptions
  { Label L;
    __ ldr(Rtemp, Address(Rthread, Thread::pending_exception_offset()));
    __ cbz(Rtemp, L);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_pending_exception));
    __ should_not_reach_here();
    __ bind(L);
  }

  __ dispatch_next(state, step);

  return entry;
}

address TemplateInterpreterGenerator::generate_result_handler_for(BasicType type) {
#ifdef AARCH64
  address entry = __ pc();
  switch (type) {
    case T_BOOLEAN:
      __ tst(R0, 0xff);
      __ cset(R0, ne);
      break;
    case T_CHAR   : __ zero_extend(R0, R0, 16);  break;
    case T_BYTE   : __ sign_extend(R0, R0,  8);  break;
    case T_SHORT  : __ sign_extend(R0, R0, 16);  break;
    case T_INT    : // fall through
    case T_LONG   : // fall through
    case T_VOID   : // fall through
    case T_FLOAT  : // fall through
    case T_DOUBLE : /* nothing to do */          break;
    case T_OBJECT :
      // retrieve result from frame
      __ ldr(R0, Address(FP, frame::interpreter_frame_oop_temp_offset * wordSize));
      // and verify it
      __ verify_oop(R0);
      break;
    default       : ShouldNotReachHere();
  }
  __ ret();
  return entry;
#else
  // Result handlers are not used on 32-bit ARM
  // since the returned value is already in appropriate format.
  __ should_not_reach_here();  // to avoid empty code block

  // The result handler non-zero indicates an object is returned and this is
  // used in the native entry code.
  return type == T_OBJECT ? (address)(-1) : NULL;
#endif // AARCH64
}

address TemplateInterpreterGenerator::generate_safept_entry_for(TosState state, address runtime_entry) {
  address entry = __ pc();
  __ push(state);
  __ call_VM(noreg, runtime_entry);

  // load current bytecode
  __ ldrb(R3_bytecode, Address(Rbcp));
  __ dispatch_only_normal(vtos);
  return entry;
}


// Helpers for commoning out cases in the various type of method entries.
//

// increment invocation count & check for overflow
//
// Note: checking for negative value instead of overflow
//       so we have a 'sticky' overflow test
//
// In: Rmethod.
//
// Uses R0, R1, Rtemp.
//
void TemplateInterpreterGenerator::generate_counter_incr(Label* overflow,
                                                 Label* profile_method,
                                                 Label* profile_method_continue) {
  Label done;
  const Register Rcounters = Rtemp;
  const Address invocation_counter(Rcounters,
                MethodCounters::invocation_counter_offset() +
                InvocationCounter::counter_offset());

  // Note: In tiered we increment either counters in MethodCounters* or
  // in MDO depending if we're profiling or not.
  if (TieredCompilation) {
    int increment = InvocationCounter::count_increment;
    Label no_mdo;
    if (ProfileInterpreter) {
      // Are we profiling?
      __ ldr(R1_tmp, Address(Rmethod, Method::method_data_offset()));
      __ cbz(R1_tmp, no_mdo);
      // Increment counter in the MDO
      const Address mdo_invocation_counter(R1_tmp,
                    in_bytes(MethodData::invocation_counter_offset()) +
                    in_bytes(InvocationCounter::counter_offset()));
      const Address mask(R1_tmp, in_bytes(MethodData::invoke_mask_offset()));
      __ increment_mask_and_jump(mdo_invocation_counter, increment, mask, R0_tmp, Rtemp, eq, overflow);
      __ b(done);
    }
    __ bind(no_mdo);
    __ get_method_counters(Rmethod, Rcounters, done);
    const Address mask(Rcounters, in_bytes(MethodCounters::invoke_mask_offset()));
    __ increment_mask_and_jump(invocation_counter, increment, mask, R0_tmp, R1_tmp, eq, overflow);
    __ bind(done);
  } else { // not TieredCompilation
    const Address backedge_counter(Rcounters,
                  MethodCounters::backedge_counter_offset() +
                  InvocationCounter::counter_offset());

    const Register Ricnt = R0_tmp;  // invocation counter
    const Register Rbcnt = R1_tmp;  // backedge counter

    __ get_method_counters(Rmethod, Rcounters, done);

    if (ProfileInterpreter) {
      const Register Riic = R1_tmp;
      __ ldr_s32(Riic, Address(Rcounters, MethodCounters::interpreter_invocation_counter_offset()));
      __ add(Riic, Riic, 1);
      __ str_32(Riic, Address(Rcounters, MethodCounters::interpreter_invocation_counter_offset()));
    }

    // Update standard invocation counters

    __ ldr_u32(Ricnt, invocation_counter);
    __ ldr_u32(Rbcnt, backedge_counter);

    __ add(Ricnt, Ricnt, InvocationCounter::count_increment);

#ifdef AARCH64
    __ andr(Rbcnt, Rbcnt, (unsigned int)InvocationCounter::count_mask_value); // mask out the status bits
#else
    __ bic(Rbcnt, Rbcnt, ~InvocationCounter::count_mask_value); // mask out the status bits
#endif // AARCH64

    __ str_32(Ricnt, invocation_counter);            // save invocation count
    __ add(Ricnt, Ricnt, Rbcnt);                     // add both counters

    // profile_method is non-null only for interpreted method so
    // profile_method != NULL == !native_call
    // BytecodeInterpreter only calls for native so code is elided.

    if (ProfileInterpreter && profile_method != NULL) {
      assert(profile_method_continue != NULL, "should be non-null");

      // Test to see if we should create a method data oop
      // Reuse R1_tmp as we don't need backedge counters anymore.
      Address profile_limit(Rcounters, in_bytes(MethodCounters::interpreter_profile_limit_offset()));
      __ ldr_s32(R1_tmp, profile_limit);
      __ cmp_32(Ricnt, R1_tmp);
      __ b(*profile_method_continue, lt);

      // if no method data exists, go to profile_method
      __ test_method_data_pointer(R1_tmp, *profile_method);
    }

    Address invoke_limit(Rcounters, in_bytes(MethodCounters::interpreter_invocation_limit_offset()));
    __ ldr_s32(R1_tmp, invoke_limit);
    __ cmp_32(Ricnt, R1_tmp);
    __ b(*overflow, hs);
    __ bind(done);
  }
}

void TemplateInterpreterGenerator::generate_counter_overflow(Label& do_continue) {
  // InterpreterRuntime::frequency_counter_overflow takes one argument
  // indicating if the counter overflow occurs at a backwards branch (non-NULL bcp).
  // The call returns the address of the verified entry point for the method or NULL
  // if the compilation did not complete (either went background or bailed out).
  __ mov(R1, (int)false);
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::frequency_counter_overflow), R1);

  // jump to the interpreted entry.
  __ b(do_continue);
}

void TemplateInterpreterGenerator::generate_stack_overflow_check(void) {
  // Check if we've got enough room on the stack for
  //  - overhead;
  //  - locals;
  //  - expression stack.
  //
  // Registers on entry:
  //
  // R3 = number of additional locals
  // R11 = max expression stack slots (AArch64 only)
  // Rthread
  // Rmethod
  // Registers used: R0, R1, R2, Rtemp.

  const Register Radditional_locals = R3;
  const Register RmaxStack = AARCH64_ONLY(R11) NOT_AARCH64(R2);

  // monitor entry size
  const int entry_size = frame::interpreter_frame_monitor_size() * wordSize;

  // total overhead size: entry_size + (saved registers, thru expr stack bottom).
  // be sure to change this if you add/subtract anything to/from the overhead area
  const int overhead_size = (frame::sender_sp_offset - frame::interpreter_frame_initial_sp_offset)*wordSize + entry_size;

  // Pages reserved for VM runtime calls and subsequent Java calls.
  const int reserved_pages = JavaThread::stack_shadow_zone_size();

  // Thread::stack_size() includes guard pages, and they should not be touched.
  const int guard_pages = JavaThread::stack_guard_zone_size();

  __ ldr(R0, Address(Rthread, Thread::stack_base_offset()));
  __ ldr(R1, Address(Rthread, Thread::stack_size_offset()));
#ifndef AARCH64
  __ ldr(Rtemp, Address(Rmethod, Method::const_offset()));
  __ ldrh(RmaxStack, Address(Rtemp, ConstMethod::max_stack_offset()));
#endif // !AARCH64
  __ sub_slow(Rtemp, SP, overhead_size + reserved_pages + guard_pages + Method::extra_stack_words());

  // reserve space for additional locals
  __ sub(Rtemp, Rtemp, AsmOperand(Radditional_locals, lsl, Interpreter::logStackElementSize));

  // stack size
  __ sub(R0, R0, R1);

  // reserve space for expression stack
  __ sub(Rtemp, Rtemp, AsmOperand(RmaxStack, lsl, Interpreter::logStackElementSize));

  __ cmp(Rtemp, R0);

#ifdef AARCH64
  Label L;
  __ b(L, hi);
  __ mov(SP, Rsender_sp);  // restore SP
  __ b(StubRoutines::throw_StackOverflowError_entry());
  __ bind(L);
#else
  __ mov(SP, Rsender_sp, ls);  // restore SP
  __ b(StubRoutines::throw_StackOverflowError_entry(), ls);
#endif // AARCH64
}


// Allocate monitor and lock method (asm interpreter)
//
void TemplateInterpreterGenerator::lock_method() {
  // synchronize method

  const int entry_size = frame::interpreter_frame_monitor_size() * wordSize;
  assert ((entry_size % StackAlignmentInBytes) == 0, "should keep stack alignment");

  #ifdef ASSERT
    { Label L;
      __ ldr_u32(Rtemp, Address(Rmethod, Method::access_flags_offset()));
      __ tbnz(Rtemp, JVM_ACC_SYNCHRONIZED_BIT, L);
      __ stop("method doesn't need synchronization");
      __ bind(L);
    }
  #endif // ASSERT

  // get synchronization object
  { Label done;
    __ ldr_u32(Rtemp, Address(Rmethod, Method::access_flags_offset()));
#ifdef AARCH64
    __ ldr(R0, Address(Rlocals, Interpreter::local_offset_in_bytes(0))); // get receiver (assume this is frequent case)
    __ tbz(Rtemp, JVM_ACC_STATIC_BIT, done);
#else
    __ tst(Rtemp, JVM_ACC_STATIC);
    __ ldr(R0, Address(Rlocals, Interpreter::local_offset_in_bytes(0)), eq); // get receiver (assume this is frequent case)
    __ b(done, eq);
#endif // AARCH64
    __ load_mirror(R0, Rmethod, Rtemp);
    __ bind(done);
  }

  // add space for monitor & lock

#ifdef AARCH64
  __ check_extended_sp(Rtemp);
  __ sub(SP, SP, entry_size);                  // adjust extended SP
  __ mov(Rtemp, SP);
  __ str(Rtemp, Address(FP, frame::interpreter_frame_extended_sp_offset * wordSize));
#endif // AARCH64

  __ sub(Rstack_top, Rstack_top, entry_size);
  __ check_stack_top_on_expansion();
                                              // add space for a monitor entry
  __ str(Rstack_top, Address(FP, frame::interpreter_frame_monitor_block_top_offset * wordSize));
                                              // set new monitor block top
  __ str(R0, Address(Rstack_top, BasicObjectLock::obj_offset_in_bytes()));
                                              // store object
  __ mov(R1, Rstack_top);                     // monitor entry address
  __ lock_object(R1);
}

#ifdef AARCH64

//
// Generate a fixed interpreter frame. This is identical setup for interpreted methods
// and for native methods hence the shared code.
//
// On entry:
//   R10 = ConstMethod
//   R11 = max expr. stack (in slots), if !native_call
//
// On exit:
//   Rbcp, Rstack_top are initialized, SP is extended
//
void TemplateInterpreterGenerator::generate_fixed_frame(bool native_call) {
  // Incoming registers
  const Register RconstMethod = R10;
  const Register RmaxStack = R11;
  // Temporary registers
  const Register RextendedSP = R0;
  const Register Rcache = R1;
  const Register Rmdp = ProfileInterpreter ? R2 : ZR;

  // Generates the following stack layout (stack grows up in this picture):
  //
  // [ expr. stack bottom ]
  // [ saved Rbcp         ]
  // [ current Rlocals    ]
  // [ cache              ]
  // [ mdx                ]
  // [ mirror             ]
  // [ Method*            ]
  // [ extended SP        ]
  // [ expr. stack top    ]
  // [ sender_sp          ]
  // [ saved FP           ] <--- FP
  // [ saved LR           ]

  // initialize fixed part of activation frame
  __ stp(FP, LR, Address(SP, -2*wordSize, pre_indexed));
  __ mov(FP, SP);                                     // establish new FP

  // setup Rbcp
  if (native_call) {
    __ mov(Rbcp, ZR);                                 // bcp = 0 for native calls
  } else {
    __ add(Rbcp, RconstMethod, in_bytes(ConstMethod::codes_offset())); // get codebase
  }

  // Rstack_top & RextendedSP
  __ sub(Rstack_top, SP, 10*wordSize);
  if (native_call) {
    __ sub(RextendedSP, Rstack_top, round_to(wordSize, StackAlignmentInBytes));    // reserve 1 slot for exception handling
  } else {
    __ sub(RextendedSP, Rstack_top, AsmOperand(RmaxStack, lsl, Interpreter::logStackElementSize));
    __ align_reg(RextendedSP, RextendedSP, StackAlignmentInBytes);
  }
  __ mov(SP, RextendedSP);
  __ check_stack_top();

  // Load Rmdp
  if (ProfileInterpreter) {
    __ ldr(Rtemp, Address(Rmethod, Method::method_data_offset()));
    __ tst(Rtemp, Rtemp);
    __ add(Rtemp, Rtemp, in_bytes(MethodData::data_offset()));
    __ csel(Rmdp, ZR, Rtemp, eq);
  }

  // Load Rcache
  __ ldr(Rtemp, Address(RconstMethod, ConstMethod::constants_offset()));
  __ ldr(Rcache, Address(Rtemp, ConstantPool::cache_offset_in_bytes()));
  // Get mirror and store it in the frame as GC root for this Method*
  __ load_mirror(Rtemp, Rmethod, Rtemp);

  // Build fixed frame
  __ stp(Rstack_top, Rbcp, Address(FP, -10*wordSize));
  __ stp(Rlocals, Rcache,  Address(FP,  -8*wordSize));
  __ stp(Rmdp, Rtemp,          Address(FP,  -6*wordSize));
  __ stp(Rmethod, RextendedSP, Address(FP,  -4*wordSize));
  __ stp(ZR, Rsender_sp,   Address(FP,  -2*wordSize));
  assert(frame::interpreter_frame_initial_sp_offset == -10, "interpreter frame broken");
  assert(frame::interpreter_frame_stack_top_offset  == -2, "stack top broken");
}

#else // AARCH64

//
// Generate a fixed interpreter frame. This is identical setup for interpreted methods
// and for native methods hence the shared code.

void TemplateInterpreterGenerator::generate_fixed_frame(bool native_call) {
  // Generates the following stack layout:
  //
  // [ expr. stack bottom ]
  // [ saved Rbcp         ]
  // [ current Rlocals    ]
  // [ cache              ]
  // [ mdx                ]
  // [ Method*            ]
  // [ last_sp            ]
  // [ sender_sp          ]
  // [ saved FP           ] <--- FP
  // [ saved LR           ]

  // initialize fixed part of activation frame
  __ push(LR);                                        // save return address
  __ push(FP);                                        // save FP
  __ mov(FP, SP);                                     // establish new FP

  __ push(Rsender_sp);

  __ mov(R0, 0);
  __ push(R0);                                        // leave last_sp as null

  // setup Rbcp
  if (native_call) {
    __ mov(Rbcp, 0);                                  // bcp = 0 for native calls
  } else {
    __ ldr(Rtemp, Address(Rmethod, Method::const_offset())); // get ConstMethod*
    __ add(Rbcp, Rtemp, ConstMethod::codes_offset()); // get codebase
  }

  __ push(Rmethod);                                    // save Method*
  // Get mirror and store it in the frame as GC root for this Method*
  __ load_mirror(Rtemp, Rmethod, Rtemp);
  __ push(Rtemp);

  if (ProfileInterpreter) {
    __ ldr(Rtemp, Address(Rmethod, Method::method_data_offset()));
    __ tst(Rtemp, Rtemp);
    __ add(Rtemp, Rtemp, in_bytes(MethodData::data_offset()), ne);
    __ push(Rtemp);                                    // set the mdp (method data pointer)
  } else {
    __ push(R0);
  }

  __ ldr(Rtemp, Address(Rmethod, Method::const_offset()));
  __ ldr(Rtemp, Address(Rtemp, ConstMethod::constants_offset()));
  __ ldr(Rtemp, Address(Rtemp, ConstantPool::cache_offset_in_bytes()));
  __ push(Rtemp);                                      // set constant pool cache
  __ push(Rlocals);                                    // set locals pointer
  __ push(Rbcp);                                       // set bcp
  __ push(R0);                                         // reserve word for pointer to expression stack bottom
  __ str(SP, Address(SP, 0));                          // set expression stack bottom
}

#endif // AARCH64

// End of helpers

//------------------------------------------------------------------------------------------------------------------------
// Entry points
//
// Here we generate the various kind of entries into the interpreter.
// The two main entry type are generic bytecode methods and native call method.
// These both come in synchronized and non-synchronized versions but the
// frame layout they create is very similar. The other method entry
// types are really just special purpose entries that are really entry
// and interpretation all in one. These are for trivial methods like
// accessor, empty, or special math methods.
//
// When control flow reaches any of the entry types for the interpreter
// the following holds ->
//
// Arguments:
//
// Rmethod: Method*
// Rthread: thread
// Rsender_sp:  sender sp
// Rparams (SP on 32-bit ARM): pointer to method parameters
//
// LR: return address
//
// Stack layout immediately at entry
//
// [ optional padding(*)] <--- SP (AArch64)
// [ parameter n        ] <--- Rparams (SP on 32-bit ARM)
//   ...
// [ parameter 1        ]
// [ expression stack   ] (caller's java expression stack)

// Assuming that we don't go to one of the trivial specialized
// entries the stack will look like below when we are ready to execute
// the first bytecode (or call the native routine). The register usage
// will be as the template based interpreter expects.
//
// local variables follow incoming parameters immediately; i.e.
// the return address is saved at the end of the locals.
//
// [ reserved stack (*) ] <--- SP (AArch64)
// [ expr. stack        ] <--- Rstack_top (SP on 32-bit ARM)
// [ monitor entry      ]
//   ...
// [ monitor entry      ]
// [ expr. stack bottom ]
// [ saved Rbcp         ]
// [ current Rlocals    ]
// [ cache              ]
// [ mdx                ]
// [ mirror             ]
// [ Method*            ]
//
// 32-bit ARM:
// [ last_sp            ]
//
// AArch64:
// [ extended SP (*)    ]
// [ stack top (*)      ]
//
// [ sender_sp          ]
// [ saved FP           ] <--- FP
// [ saved LR           ]
// [ optional padding(*)]
// [ local variable m   ]
//   ...
// [ local variable 1   ]
// [ parameter n        ]
//   ...
// [ parameter 1        ] <--- Rlocals
//
// (*) - AArch64 only
//

address TemplateInterpreterGenerator::generate_Reference_get_entry(void) {
#if INCLUDE_ALL_GCS
  if (UseG1GC) {
    // Code: _aload_0, _getfield, _areturn
    // parameter size = 1
    //
    // The code that gets generated by this routine is split into 2 parts:
    //    1. The "intrinsified" code for G1 (or any SATB based GC),
    //    2. The slow path - which is an expansion of the regular method entry.
    //
    // Notes:-
    // * In the G1 code we do not check whether we need to block for
    //   a safepoint. If G1 is enabled then we must execute the specialized
    //   code for Reference.get (except when the Reference object is null)
    //   so that we can log the value in the referent field with an SATB
    //   update buffer.
    //   If the code for the getfield template is modified so that the
    //   G1 pre-barrier code is executed when the current method is
    //   Reference.get() then going through the normal method entry
    //   will be fine.
    // * The G1 code can, however, check the receiver object (the instance
    //   of java.lang.Reference) and jump to the slow path if null. If the
    //   Reference object is null then we obviously cannot fetch the referent
    //   and so we don't need to call the G1 pre-barrier. Thus we can use the
    //   regular method entry code to generate the NPE.
    //
    // This code is based on generate_accessor_enty.
    //
    // Rmethod: Method*
    // Rthread: thread
    // Rsender_sp: sender sp, must be preserved for slow path, set SP to it on fast path
    // Rparams: parameters

    address entry = __ pc();
    Label slow_path;
    const Register Rthis = R0;
    const Register Rret_addr = Rtmp_save1;
    assert_different_registers(Rthis, Rret_addr, Rsender_sp);

    const int referent_offset = java_lang_ref_Reference::referent_offset;
    guarantee(referent_offset > 0, "referent offset not initialized");

    // Check if local 0 != NULL
    // If the receiver is null then it is OK to jump to the slow path.
    __ ldr(Rthis, Address(Rparams));
    __ cbz(Rthis, slow_path);

    // Generate the G1 pre-barrier code to log the value of
    // the referent field in an SATB buffer.

    // Load the value of the referent field.
    __ load_heap_oop(R0, Address(Rthis, referent_offset));

    // Preserve LR
    __ mov(Rret_addr, LR);

    __ g1_write_barrier_pre(noreg,   // store_addr
                            noreg,   // new_val
                            R0,      // pre_val
                            Rtemp,   // tmp1
                            R1_tmp); // tmp2

    // _areturn
    __ mov(SP, Rsender_sp);
    __ ret(Rret_addr);

    // generate a vanilla interpreter entry as the slow path
    __ bind(slow_path);
    __ jump_to_entry(Interpreter::entry_for_kind(Interpreter::zerolocals));
    return entry;
  }
#endif // INCLUDE_ALL_GCS

  // If G1 is not enabled then attempt to go through the normal entry point
  return NULL;
}

// Not supported
address TemplateInterpreterGenerator::generate_CRC32_update_entry() { return NULL; }
address TemplateInterpreterGenerator::generate_CRC32_updateBytes_entry(AbstractInterpreter::MethodKind kind) { return NULL; }
address TemplateInterpreterGenerator::generate_CRC32C_updateBytes_entry(AbstractInterpreter::MethodKind kind) { return NULL; }

//
// Interpreter stub for calling a native method. (asm interpreter)
// This sets up a somewhat different looking stack for calling the native method
// than the typical interpreter frame setup.
//

address TemplateInterpreterGenerator::generate_native_entry(bool synchronized) {
  // determine code generation flags
  bool inc_counter  = UseCompiler || CountCompiledCalls || LogTouchedMethods;

  // Incoming registers:
  //
  // Rmethod: Method*
  // Rthread: thread
  // Rsender_sp: sender sp
  // Rparams: parameters

  address entry_point = __ pc();

  // Register allocation
  const Register Rsize_of_params = AARCH64_ONLY(R20) NOT_AARCH64(R6);
  const Register Rsig_handler    = AARCH64_ONLY(R21) NOT_AARCH64(Rtmp_save0 /* R4 */);
  const Register Rnative_code    = AARCH64_ONLY(R22) NOT_AARCH64(Rtmp_save1 /* R5 */);
  const Register Rresult_handler = AARCH64_ONLY(Rsig_handler) NOT_AARCH64(R6);

#ifdef AARCH64
  const Register RconstMethod = R10; // also used in generate_fixed_frame (should match)
  const Register Rsaved_result = Rnative_code;
  const FloatRegister Dsaved_result = V8;
#else
  const Register Rsaved_result_lo = Rtmp_save0;  // R4
  const Register Rsaved_result_hi = Rtmp_save1;  // R5
  FloatRegister saved_result_fp;
#endif // AARCH64


#ifdef AARCH64
  __ ldr(RconstMethod, Address(Rmethod, Method::const_offset()));
  __ ldrh(Rsize_of_params,  Address(RconstMethod, ConstMethod::size_of_parameters_offset()));
#else
  __ ldr(Rsize_of_params, Address(Rmethod, Method::const_offset()));
  __ ldrh(Rsize_of_params,  Address(Rsize_of_params, ConstMethod::size_of_parameters_offset()));
#endif // AARCH64

  // native calls don't need the stack size check since they have no expression stack
  // and the arguments are already on the stack and we only add a handful of words
  // to the stack

  // compute beginning of parameters (Rlocals)
  __ sub(Rlocals, Rparams, wordSize);
  __ add(Rlocals, Rlocals, AsmOperand(Rsize_of_params, lsl, Interpreter::logStackElementSize));

#ifdef AARCH64
  int extra_stack_reserve = 2*wordSize; // extra space for oop_temp
  if(__ can_post_interpreter_events()) {
    // extra space for saved results
    extra_stack_reserve += 2*wordSize;
  }
  // reserve extra stack space and nullify oop_temp slot
  __ stp(ZR, ZR, Address(SP, -extra_stack_reserve, pre_indexed));
#else
  // reserve stack space for oop_temp
  __ mov(R0, 0);
  __ push(R0);
#endif // AARCH64

  generate_fixed_frame(true); // Note: R9 is now saved in the frame

  // make sure method is native & not abstract
#ifdef ASSERT
  __ ldr_u32(Rtemp, Address(Rmethod, Method::access_flags_offset()));
  {
    Label L;
    __ tbnz(Rtemp, JVM_ACC_NATIVE_BIT, L);
    __ stop("tried to execute non-native method as native");
    __ bind(L);
  }
  { Label L;
    __ tbz(Rtemp, JVM_ACC_ABSTRACT_BIT, L);
    __ stop("tried to execute abstract method in interpreter");
    __ bind(L);
  }
#endif

  // increment invocation count & check for overflow
  Label invocation_counter_overflow;
  if (inc_counter) {
    if (synchronized) {
      // Avoid unlocking method's monitor in case of exception, as it has not
      // been locked yet.
      __ set_do_not_unlock_if_synchronized(true, Rtemp);
    }
    generate_counter_incr(&invocation_counter_overflow, NULL, NULL);
  }

  Label continue_after_compile;
  __ bind(continue_after_compile);

  if (inc_counter && synchronized) {
    __ set_do_not_unlock_if_synchronized(false, Rtemp);
  }

  // check for synchronized methods
  // Must happen AFTER invocation_counter check and stack overflow check,
  // so method is not locked if overflows.
  //
  if (synchronized) {
    lock_method();
  } else {
    // no synchronization necessary
#ifdef ASSERT
      { Label L;
        __ ldr_u32(Rtemp, Address(Rmethod, Method::access_flags_offset()));
        __ tbz(Rtemp, JVM_ACC_SYNCHRONIZED_BIT, L);
        __ stop("method needs synchronization");
        __ bind(L);
      }
#endif
  }

  // start execution
#ifdef ASSERT
  { Label L;
    __ ldr(Rtemp, Address(FP, frame::interpreter_frame_monitor_block_top_offset * wordSize));
    __ cmp(Rtemp, Rstack_top);
    __ b(L, eq);
    __ stop("broken stack frame setup in interpreter");
    __ bind(L);
  }
#endif
  __ check_extended_sp(Rtemp);

  // jvmti/dtrace support
  __ notify_method_entry();
#if R9_IS_SCRATCHED
  __ restore_method();
#endif

  {
    Label L;
    __ ldr(Rsig_handler, Address(Rmethod, Method::signature_handler_offset()));
    __ cbnz(Rsig_handler, L);
    __ mov(R1, Rmethod);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::prepare_native_call), R1, true);
    __ ldr(Rsig_handler, Address(Rmethod, Method::signature_handler_offset()));
    __ bind(L);
  }

  {
    Label L;
    __ ldr(Rnative_code, Address(Rmethod, Method::native_function_offset()));
    __ cbnz(Rnative_code, L);
    __ mov(R1, Rmethod);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::prepare_native_call), R1);
    __ ldr(Rnative_code, Address(Rmethod, Method::native_function_offset()));
    __ bind(L);
  }

  // Allocate stack space for arguments

#ifdef AARCH64
  __ sub(Rtemp, SP, Rsize_of_params, ex_uxtw, LogBytesPerWord);
  __ align_reg(SP, Rtemp, StackAlignmentInBytes);

  // Allocate more stack space to accomodate all arguments passed on GP and FP registers:
  // 8 * wordSize for GPRs
  // 8 * wordSize for FPRs
  int reg_arguments = round_to(8*wordSize + 8*wordSize, StackAlignmentInBytes);
#else

  // C functions need aligned stack
  __ bic(SP, SP, StackAlignmentInBytes - 1);
  // Multiply by BytesPerLong instead of BytesPerWord, because calling convention
  // may require empty slots due to long alignment, e.g. func(int, jlong, int, jlong)
  __ sub(SP, SP, AsmOperand(Rsize_of_params, lsl, LogBytesPerLong));

#ifdef __ABI_HARD__
  // Allocate more stack space to accomodate all GP as well as FP registers:
  // 4 * wordSize
  // 8 * BytesPerLong
  int reg_arguments = round_to((4*wordSize) + (8*BytesPerLong), StackAlignmentInBytes);
#else
  // Reserve at least 4 words on the stack for loading
  // of parameters passed on registers (R0-R3).
  // See generate_slow_signature_handler().
  // It is also used for JNIEnv & class additional parameters.
  int reg_arguments = 4 * wordSize;
#endif // __ABI_HARD__
#endif // AARCH64

  __ sub(SP, SP, reg_arguments);


  // Note: signature handler blows R4 (32-bit ARM) or R21 (AArch64) besides all scratch registers.
  // See AbstractInterpreterGenerator::generate_slow_signature_handler().
  __ call(Rsig_handler);
#if R9_IS_SCRATCHED
  __ restore_method();
#endif
  __ mov(Rresult_handler, R0);

  // Pass JNIEnv and mirror for static methods
  {
    Label L;
    __ ldr_u32(Rtemp, Address(Rmethod, Method::access_flags_offset()));
    __ add(R0, Rthread, in_bytes(JavaThread::jni_environment_offset()));
    __ tbz(Rtemp, JVM_ACC_STATIC_BIT, L);
    __ load_mirror(Rtemp, Rmethod, Rtemp);
    __ add(R1, FP, frame::interpreter_frame_oop_temp_offset * wordSize);
    __ str(Rtemp, Address(R1, 0));
    __ bind(L);
  }

  __ set_last_Java_frame(SP, FP, true, Rtemp);

  // Changing state to _thread_in_native must be the last thing to do
  // before the jump to native code. At this moment stack must be
  // safepoint-safe and completely prepared for stack walking.
#ifdef ASSERT
  {
    Label L;
    __ ldr_u32(Rtemp, Address(Rthread, JavaThread::thread_state_offset()));
    __ cmp_32(Rtemp, _thread_in_Java);
    __ b(L, eq);
    __ stop("invalid thread state");
    __ bind(L);
  }
#endif

#ifdef AARCH64
  __ mov(Rtemp, _thread_in_native);
  __ add(Rtemp2, Rthread, in_bytes(JavaThread::thread_state_offset()));
  // STLR is used to force all preceding writes to be observed prior to thread state change
  __ stlr_w(Rtemp, Rtemp2);
#else
  // Force all preceding writes to be observed prior to thread state change
  __ membar(MacroAssembler::StoreStore, Rtemp);

  __ mov(Rtemp, _thread_in_native);
  __ str(Rtemp, Address(Rthread, JavaThread::thread_state_offset()));
#endif // AARCH64

  __ call(Rnative_code);
#if R9_IS_SCRATCHED
  __ restore_method();
#endif

  // Set FPSCR/FPCR to a known state
  if (AlwaysRestoreFPU) {
    __ restore_default_fp_mode();
  }

  // Do safepoint check
  __ mov(Rtemp, _thread_in_native_trans);
  __ str_32(Rtemp, Address(Rthread, JavaThread::thread_state_offset()));

    // Force this write out before the read below
  __ membar(MacroAssembler::StoreLoad, Rtemp);

  __ ldr_global_s32(Rtemp, SafepointSynchronize::address_of_state());

  // Protect the return value in the interleaved code: save it to callee-save registers.
#ifdef AARCH64
  __ mov(Rsaved_result, R0);
  __ fmov_d(Dsaved_result, D0);
#else
  __ mov(Rsaved_result_lo, R0);
  __ mov(Rsaved_result_hi, R1);
#ifdef __ABI_HARD__
  // preserve native FP result in a callee-saved register
  saved_result_fp = D8;
  __ fcpyd(saved_result_fp, D0);
#else
  saved_result_fp = fnoreg;
#endif // __ABI_HARD__
#endif // AARCH64

  {
    __ ldr_u32(R3, Address(Rthread, JavaThread::suspend_flags_offset()));
    __ cmp(Rtemp, SafepointSynchronize::_not_synchronized);
    __ cond_cmp(R3, 0, eq);

#ifdef AARCH64
    Label L;
    __ b(L, eq);
    __ mov(R0, Rthread);
    __ call(CAST_FROM_FN_PTR(address, JavaThread::check_special_condition_for_native_trans), relocInfo::none);
    __ bind(L);
#else
  __ mov(R0, Rthread, ne);
  __ call(CAST_FROM_FN_PTR(address, JavaThread::check_special_condition_for_native_trans), relocInfo::none, ne);
#if R9_IS_SCRATCHED
  __ restore_method();
#endif
#endif // AARCH64
  }

  // Perform Native->Java thread transition
  __ mov(Rtemp, _thread_in_Java);
  __ str_32(Rtemp, Address(Rthread, JavaThread::thread_state_offset()));

  // Zero handles and last_java_sp
  __ reset_last_Java_frame(Rtemp);
  __ ldr(R3, Address(Rthread, JavaThread::active_handles_offset()));
  __ str_32(__ zero_register(Rtemp), Address(R3, JNIHandleBlock::top_offset_in_bytes()));
  if (CheckJNICalls) {
    __ str(__ zero_register(Rtemp), Address(Rthread, JavaThread::pending_jni_exception_check_fn_offset()));
  }

  // Unbox oop result, e.g. JNIHandles::resolve result if it's an oop.
  {
    Label Lnot_oop;
#ifdef AARCH64
    __ mov_slow(Rtemp, AbstractInterpreter::result_handler(T_OBJECT));
    __ cmp(Rresult_handler, Rtemp);
    __ b(Lnot_oop, ne);
#else // !AARCH64
    // For ARM32, Rresult_handler is -1 for oop result, 0 otherwise.
    __ cbz(Rresult_handler, Lnot_oop);
#endif // !AARCH64
    Register value = AARCH64_ONLY(Rsaved_result) NOT_AARCH64(Rsaved_result_lo);
    __ resolve_jobject(value,   // value
                       Rtemp,   // tmp1
                       R1_tmp); // tmp2
    // Store resolved result in frame for GC visibility.
    __ str(value, Address(FP, frame::interpreter_frame_oop_temp_offset * wordSize));
    __ bind(Lnot_oop);
  }

#ifdef AARCH64
  // Restore SP (drop native parameters area), to keep SP in sync with extended_sp in frame
  __ restore_sp_after_call(Rtemp);
  __ check_stack_top();
#endif // AARCH64

  // reguard stack if StackOverflow exception happened while in native.
  {
    __ ldr_u32(Rtemp, Address(Rthread, JavaThread::stack_guard_state_offset()));
    __ cmp_32(Rtemp, JavaThread::stack_guard_yellow_reserved_disabled);
#ifdef AARCH64
    Label L;
    __ b(L, ne);
    __ call(CAST_FROM_FN_PTR(address, SharedRuntime::reguard_yellow_pages), relocInfo::none);
    __ bind(L);
#else
  __ call(CAST_FROM_FN_PTR(address, SharedRuntime::reguard_yellow_pages), relocInfo::none, eq);
#if R9_IS_SCRATCHED
  __ restore_method();
#endif
#endif // AARCH64
  }

  // check pending exceptions
  {
    __ ldr(Rtemp, Address(Rthread, Thread::pending_exception_offset()));
#ifdef AARCH64
    Label L;
    __ cbz(Rtemp, L);
    __ mov_pc_to(Rexception_pc);
    __ b(StubRoutines::forward_exception_entry());
    __ bind(L);
#else
    __ cmp(Rtemp, 0);
    __ mov(Rexception_pc, PC, ne);
    __ b(StubRoutines::forward_exception_entry(), ne);
#endif // AARCH64
  }

  if (synchronized) {
    // address of first monitor
    __ sub(R1, FP, - (frame::interpreter_frame_monitor_block_bottom_offset - frame::interpreter_frame_monitor_size()) * wordSize);
    __ unlock_object(R1);
  }

  // jvmti/dtrace support
  // Note: This must happen _after_ handling/throwing any exceptions since
  //       the exception handler code notifies the runtime of method exits
  //       too. If this happens before, method entry/exit notifications are
  //       not properly paired (was bug - gri 11/22/99).
#ifdef AARCH64
  __ notify_method_exit(vtos, InterpreterMacroAssembler::NotifyJVMTI, true, Rsaved_result, noreg, Dsaved_result);
#else
  __ notify_method_exit(vtos, InterpreterMacroAssembler::NotifyJVMTI, true, Rsaved_result_lo, Rsaved_result_hi, saved_result_fp);
#endif // AARCH64

  // Restore the result. Oop result is restored from the stack.
#ifdef AARCH64
  __ mov(R0, Rsaved_result);
  __ fmov_d(D0, Dsaved_result);

  __ blr(Rresult_handler);
#else
  __ cmp(Rresult_handler, 0);
  __ ldr(R0, Address(FP, frame::interpreter_frame_oop_temp_offset * wordSize), ne);
  __ mov(R0, Rsaved_result_lo, eq);
  __ mov(R1, Rsaved_result_hi);

#ifdef __ABI_HARD__
  // reload native FP result
  __ fcpyd(D0, D8);
#endif // __ABI_HARD__

#ifdef ASSERT
  if (VerifyOops) {
    Label L;
    __ cmp(Rresult_handler, 0);
    __ b(L, eq);
    __ verify_oop(R0);
    __ bind(L);
  }
#endif // ASSERT
#endif // AARCH64

  // Restore FP/LR, sender_sp and return
#ifdef AARCH64
  __ ldr(Rtemp, Address(FP, frame::interpreter_frame_sender_sp_offset * wordSize));
  __ ldp(FP, LR, Address(FP));
  __ mov(SP, Rtemp);
#else
  __ mov(Rtemp, FP);
  __ ldmia(FP, RegisterSet(FP) | RegisterSet(LR));
  __ ldr(SP, Address(Rtemp, frame::interpreter_frame_sender_sp_offset * wordSize));
#endif // AARCH64

  __ ret();

  if (inc_counter) {
    // Handle overflow of counter and compile method
    __ bind(invocation_counter_overflow);
    generate_counter_overflow(continue_after_compile);
  }

  return entry_point;
}

//
// Generic interpreted method entry to (asm) interpreter
//
address TemplateInterpreterGenerator::generate_normal_entry(bool synchronized) {
  // determine code generation flags
  bool inc_counter  = UseCompiler || CountCompiledCalls || LogTouchedMethods;

  // Rmethod: Method*
  // Rthread: thread
  // Rsender_sp: sender sp (could differ from SP if we were called via c2i)
  // Rparams: pointer to the last parameter in the stack

  address entry_point = __ pc();

  const Register RconstMethod = AARCH64_ONLY(R10) NOT_AARCH64(R3);

#ifdef AARCH64
  const Register RmaxStack = R11;
  const Register RlocalsBase = R12;
#endif // AARCH64

  __ ldr(RconstMethod, Address(Rmethod, Method::const_offset()));

  __ ldrh(R2, Address(RconstMethod, ConstMethod::size_of_parameters_offset()));
  __ ldrh(R3, Address(RconstMethod, ConstMethod::size_of_locals_offset()));

  // setup Rlocals
  __ sub(Rlocals, Rparams, wordSize);
  __ add(Rlocals, Rlocals, AsmOperand(R2, lsl, Interpreter::logStackElementSize));

  __ sub(R3, R3, R2); // number of additional locals

#ifdef AARCH64
  // setup RmaxStack
  __ ldrh(RmaxStack, Address(RconstMethod, ConstMethod::max_stack_offset()));
  __ add(RmaxStack, RmaxStack, MAX2(1, Method::extra_stack_entries())); // reserve slots for exception handler and JSR292 appendix argument
#endif // AARCH64

  // see if we've got enough room on the stack for locals plus overhead.
  generate_stack_overflow_check();

#ifdef AARCH64

  // allocate space for locals
  {
    __ sub(RlocalsBase, Rparams, AsmOperand(R3, lsl, Interpreter::logStackElementSize));
    __ align_reg(SP, RlocalsBase, StackAlignmentInBytes);
  }

  // explicitly initialize locals
  {
    Label zero_loop, done;
    __ cbz(R3, done);

    __ tbz(R3, 0, zero_loop);
    __ subs(R3, R3, 1);
    __ str(ZR, Address(RlocalsBase, wordSize, post_indexed));
    __ b(done, eq);

    __ bind(zero_loop);
    __ subs(R3, R3, 2);
    __ stp(ZR, ZR, Address(RlocalsBase, 2*wordSize, post_indexed));
    __ b(zero_loop, ne);

    __ bind(done);
  }

#else
  // allocate space for locals
  // explicitly initialize locals

  // Loop is unrolled 4 times
  Label loop;
  __ mov(R0, 0);
  __ bind(loop);

  // #1
  __ subs(R3, R3, 1);
  __ push(R0, ge);

  // #2
  __ subs(R3, R3, 1, ge);
  __ push(R0, ge);

  // #3
  __ subs(R3, R3, 1, ge);
  __ push(R0, ge);

  // #4
  __ subs(R3, R3, 1, ge);
  __ push(R0, ge);

  __ b(loop, gt);
#endif // AARCH64

  // initialize fixed part of activation frame
  generate_fixed_frame(false);

  __ restore_dispatch();

  // make sure method is not native & not abstract
#ifdef ASSERT
  __ ldr_u32(Rtemp, Address(Rmethod, Method::access_flags_offset()));
  {
    Label L;
    __ tbz(Rtemp, JVM_ACC_NATIVE_BIT, L);
    __ stop("tried to execute native method as non-native");
    __ bind(L);
  }
  { Label L;
    __ tbz(Rtemp, JVM_ACC_ABSTRACT_BIT, L);
    __ stop("tried to execute abstract method in interpreter");
    __ bind(L);
  }
#endif

  // increment invocation count & check for overflow
  Label invocation_counter_overflow;
  Label profile_method;
  Label profile_method_continue;
  if (inc_counter) {
    if (synchronized) {
      // Avoid unlocking method's monitor in case of exception, as it has not
      // been locked yet.
      __ set_do_not_unlock_if_synchronized(true, Rtemp);
    }
    generate_counter_incr(&invocation_counter_overflow, &profile_method, &profile_method_continue);
    if (ProfileInterpreter) {
      __ bind(profile_method_continue);
    }
  }
  Label continue_after_compile;
  __ bind(continue_after_compile);

  if (inc_counter && synchronized) {
    __ set_do_not_unlock_if_synchronized(false, Rtemp);
  }
#if R9_IS_SCRATCHED
  __ restore_method();
#endif

  // check for synchronized methods
  // Must happen AFTER invocation_counter check and stack overflow check,
  // so method is not locked if overflows.
  //
  if (synchronized) {
    // Allocate monitor and lock method
    lock_method();
  } else {
    // no synchronization necessary
#ifdef ASSERT
      { Label L;
        __ ldr_u32(Rtemp, Address(Rmethod, Method::access_flags_offset()));
        __ tbz(Rtemp, JVM_ACC_SYNCHRONIZED_BIT, L);
        __ stop("method needs synchronization");
        __ bind(L);
      }
#endif
  }

  // start execution
#ifdef ASSERT
  { Label L;
    __ ldr(Rtemp, Address(FP, frame::interpreter_frame_monitor_block_top_offset * wordSize));
    __ cmp(Rtemp, Rstack_top);
    __ b(L, eq);
    __ stop("broken stack frame setup in interpreter");
    __ bind(L);
  }
#endif
  __ check_extended_sp(Rtemp);

  // jvmti support
  __ notify_method_entry();
#if R9_IS_SCRATCHED
  __ restore_method();
#endif

  __ dispatch_next(vtos);

  // invocation counter overflow
  if (inc_counter) {
    if (ProfileInterpreter) {
      // We have decided to profile this method in the interpreter
      __ bind(profile_method);

      __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::profile_method));
      __ set_method_data_pointer_for_bcp();

      __ b(profile_method_continue);
    }

    // Handle overflow of counter and compile method
    __ bind(invocation_counter_overflow);
    generate_counter_overflow(continue_after_compile);
  }

  return entry_point;
}

//------------------------------------------------------------------------------------------------------------------------
// Exceptions

void TemplateInterpreterGenerator::generate_throw_exception() {
  // Entry point in previous activation (i.e., if the caller was interpreted)
  Interpreter::_rethrow_exception_entry = __ pc();
  // Rexception_obj: exception

#ifndef AARCH64
  // Clear interpreter_frame_last_sp.
  __ mov(Rtemp, 0);
  __ str(Rtemp, Address(FP, frame::interpreter_frame_last_sp_offset * wordSize));
#endif // !AARCH64

#if R9_IS_SCRATCHED
  __ restore_method();
#endif
  __ restore_bcp();
  __ restore_dispatch();
  __ restore_locals();

#ifdef AARCH64
  __ restore_sp_after_call(Rtemp);
#endif // AARCH64

  // Entry point for exceptions thrown within interpreter code
  Interpreter::_throw_exception_entry = __ pc();

  // expression stack is undefined here
  // Rexception_obj: exception
  // Rbcp: exception bcp
  __ verify_oop(Rexception_obj);

  // expression stack must be empty before entering the VM in case of an exception
  __ empty_expression_stack();
  // find exception handler address and preserve exception oop
  __ mov(R1, Rexception_obj);
  __ call_VM(Rexception_obj, CAST_FROM_FN_PTR(address, InterpreterRuntime::exception_handler_for_exception), R1);
  // R0: exception handler entry point
  // Rexception_obj: preserved exception oop
  // Rbcp: bcp for exception handler
  __ push_ptr(Rexception_obj);                    // push exception which is now the only value on the stack
  __ jump(R0);                                    // jump to exception handler (may be _remove_activation_entry!)

  // If the exception is not handled in the current frame the frame is removed and
  // the exception is rethrown (i.e. exception continuation is _rethrow_exception).
  //
  // Note: At this point the bci is still the bxi for the instruction which caused
  //       the exception and the expression stack is empty. Thus, for any VM calls
  //       at this point, GC will find a legal oop map (with empty expression stack).

  // In current activation
  // tos: exception
  // Rbcp: exception bcp

  //
  // JVMTI PopFrame support
  //
   Interpreter::_remove_activation_preserving_args_entry = __ pc();

#ifdef AARCH64
  __ restore_sp_after_call(Rtemp); // restore SP to extended SP
#endif // AARCH64

  __ empty_expression_stack();

  // Set the popframe_processing bit in _popframe_condition indicating that we are
  // currently handling popframe, so that call_VMs that may happen later do not trigger new
  // popframe handling cycles.

  __ ldr_s32(Rtemp, Address(Rthread, JavaThread::popframe_condition_offset()));
  __ orr(Rtemp, Rtemp, (unsigned)JavaThread::popframe_processing_bit);
  __ str_32(Rtemp, Address(Rthread, JavaThread::popframe_condition_offset()));

  {
    // Check to see whether we are returning to a deoptimized frame.
    // (The PopFrame call ensures that the caller of the popped frame is
    // either interpreted or compiled and deoptimizes it if compiled.)
    // In this case, we can't call dispatch_next() after the frame is
    // popped, but instead must save the incoming arguments and restore
    // them after deoptimization has occurred.
    //
    // Note that we don't compare the return PC against the
    // deoptimization blob's unpack entry because of the presence of
    // adapter frames in C2.
    Label caller_not_deoptimized;
    __ ldr(R0, Address(FP, frame::return_addr_offset * wordSize));
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, InterpreterRuntime::interpreter_contains), R0);
    __ cbnz_32(R0, caller_not_deoptimized);
#ifdef AARCH64
    __ NOT_TESTED();
#endif

    // Compute size of arguments for saving when returning to deoptimized caller
    __ restore_method();
    __ ldr(R0, Address(Rmethod, Method::const_offset()));
    __ ldrh(R0, Address(R0, ConstMethod::size_of_parameters_offset()));

    __ logical_shift_left(R1, R0, Interpreter::logStackElementSize);
    // Save these arguments
    __ restore_locals();
    __ sub(R2, Rlocals, R1);
    __ add(R2, R2, wordSize);
    __ mov(R0, Rthread);
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, Deoptimization::popframe_preserve_args), R0, R1, R2);

    __ remove_activation(vtos, LR,
                         /* throw_monitor_exception */ false,
                         /* install_monitor_exception */ false,
                         /* notify_jvmdi */ false);

    // Inform deoptimization that it is responsible for restoring these arguments
    __ mov(Rtemp, JavaThread::popframe_force_deopt_reexecution_bit);
    __ str_32(Rtemp, Address(Rthread, JavaThread::popframe_condition_offset()));

    // Continue in deoptimization handler
    __ ret();

    __ bind(caller_not_deoptimized);
  }

  __ remove_activation(vtos, R4,
                       /* throw_monitor_exception */ false,
                       /* install_monitor_exception */ false,
                       /* notify_jvmdi */ false);

#ifndef AARCH64
  // Finish with popframe handling
  // A previous I2C followed by a deoptimization might have moved the
  // outgoing arguments further up the stack. PopFrame expects the
  // mutations to those outgoing arguments to be preserved and other
  // constraints basically require this frame to look exactly as
  // though it had previously invoked an interpreted activation with
  // no space between the top of the expression stack (current
  // last_sp) and the top of stack. Rather than force deopt to
  // maintain this kind of invariant all the time we call a small
  // fixup routine to move the mutated arguments onto the top of our
  // expression stack if necessary.
  __ mov(R1, SP);
  __ ldr(R2, Address(FP, frame::interpreter_frame_last_sp_offset * wordSize));
  // PC must point into interpreter here
  __ set_last_Java_frame(SP, FP, true, Rtemp);
  __ mov(R0, Rthread);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, InterpreterRuntime::popframe_move_outgoing_args), R0, R1, R2);
  __ reset_last_Java_frame(Rtemp);
#endif // !AARCH64

#ifdef AARCH64
  __ restore_sp_after_call(Rtemp);
  __ restore_stack_top();
#else
  // Restore the last_sp and null it out
  __ ldr(SP, Address(FP, frame::interpreter_frame_last_sp_offset * wordSize));
  __ mov(Rtemp, (int)NULL_WORD);
  __ str(Rtemp, Address(FP, frame::interpreter_frame_last_sp_offset * wordSize));
#endif // AARCH64

  __ restore_bcp();
  __ restore_dispatch();
  __ restore_locals();
  __ restore_method();

  // The method data pointer was incremented already during
  // call profiling. We have to restore the mdp for the current bcp.
  if (ProfileInterpreter) {
    __ set_method_data_pointer_for_bcp();
  }

  // Clear the popframe condition flag
  assert(JavaThread::popframe_inactive == 0, "adjust this code");
  __ str_32(__ zero_register(Rtemp), Address(Rthread, JavaThread::popframe_condition_offset()));

#if INCLUDE_JVMTI
  {
    Label L_done;

    __ ldrb(Rtemp, Address(Rbcp, 0));
    __ cmp(Rtemp, Bytecodes::_invokestatic);
    __ b(L_done, ne);

    // The member name argument must be restored if _invokestatic is re-executed after a PopFrame call.
    // Detect such a case in the InterpreterRuntime function and return the member name argument, or NULL.

    // get local0
    __ ldr(R1, Address(Rlocals, 0));
    __ mov(R2, Rmethod);
    __ mov(R3, Rbcp);
    __ call_VM(R0, CAST_FROM_FN_PTR(address, InterpreterRuntime::member_name_arg_or_null), R1, R2, R3);

    __ cbz(R0, L_done);

    __ str(R0, Address(Rstack_top));
    __ bind(L_done);
  }
#endif // INCLUDE_JVMTI

  __ dispatch_next(vtos);
  // end of PopFrame support

  Interpreter::_remove_activation_entry = __ pc();

  // preserve exception over this code sequence
  __ pop_ptr(R0_tos);
  __ str(R0_tos, Address(Rthread, JavaThread::vm_result_offset()));
  // remove the activation (without doing throws on illegalMonitorExceptions)
  __ remove_activation(vtos, Rexception_pc, false, true, false);
  // restore exception
  __ get_vm_result(Rexception_obj, Rtemp);

  // Inbetween activations - previous activation type unknown yet
  // compute continuation point - the continuation point expects
  // the following registers set up:
  //
  // Rexception_obj: exception
  // Rexception_pc: return address/pc that threw exception
  // SP: expression stack of caller
  // FP: frame pointer of caller
  __ mov(c_rarg0, Rthread);
  __ mov(c_rarg1, Rexception_pc);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address), c_rarg0, c_rarg1);
  // Note that an "issuing PC" is actually the next PC after the call

  __ jump(R0);                             // jump to exception handler of caller
}


//
// JVMTI ForceEarlyReturn support
//
address TemplateInterpreterGenerator::generate_earlyret_entry_for(TosState state) {
  address entry = __ pc();

#ifdef AARCH64
  __ restore_sp_after_call(Rtemp); // restore SP to extended SP
#endif // AARCH64

  __ restore_bcp();
  __ restore_dispatch();
  __ restore_locals();

  __ empty_expression_stack();

  __ load_earlyret_value(state);

  // Clear the earlyret state
  __ ldr(Rtemp, Address(Rthread, JavaThread::jvmti_thread_state_offset()));

  assert(JvmtiThreadState::earlyret_inactive == 0, "adjust this code");
  __ str_32(__ zero_register(R2), Address(Rtemp, JvmtiThreadState::earlyret_state_offset()));

  __ remove_activation(state, LR,
                       false, /* throw_monitor_exception */
                       false, /* install_monitor_exception */
                       true); /* notify_jvmdi */

#ifndef AARCH64
  // According to interpreter calling conventions, result is returned in R0/R1,
  // so ftos (S0) and dtos (D0) are moved to R0/R1.
  // This conversion should be done after remove_activation, as it uses
  // push(state) & pop(state) to preserve return value.
  __ convert_tos_to_retval(state);
#endif // !AARCH64
  __ ret();

  return entry;
} // end of ForceEarlyReturn support


//------------------------------------------------------------------------------------------------------------------------
// Helper for vtos entry point generation

void TemplateInterpreterGenerator::set_vtos_entry_points (Template* t, address& bep, address& cep, address& sep, address& aep, address& iep, address& lep, address& fep, address& dep, address& vep) {
  assert(t->is_valid() && t->tos_in() == vtos, "illegal template");
  Label L;

#ifdef __SOFTFP__
  dep = __ pc();                // fall through
#else
  fep = __ pc(); __ push(ftos); __ b(L);
  dep = __ pc(); __ push(dtos); __ b(L);
#endif // __SOFTFP__

  lep = __ pc(); __ push(ltos); __ b(L);

  if (AARCH64_ONLY(true) NOT_AARCH64(VerifyOops)) {  // can't share atos entry with itos on AArch64 or if VerifyOops
    aep = __ pc(); __ push(atos); __ b(L);
  } else {
    aep = __ pc();              // fall through
  }

#ifdef __SOFTFP__
  fep = __ pc();                // fall through
#endif // __SOFTFP__

  bep = cep = sep =             // fall through
  iep = __ pc(); __ push(itos); // fall through
  vep = __ pc(); __ bind(L);    // fall through
  generate_and_dispatch(t);
}

//------------------------------------------------------------------------------------------------------------------------

// Non-product code
#ifndef PRODUCT
address TemplateInterpreterGenerator::generate_trace_code(TosState state) {
  address entry = __ pc();

  // prepare expression stack
  __ push(state);       // save tosca

  // pass tosca registers as arguments
  __ mov(R2, R0_tos);
#ifdef AARCH64
  __ mov(R3, ZR);
#else
  __ mov(R3, R1_tos_hi);
#endif // AARCH64
  __ mov(R1, LR);       // save return address

  // call tracer
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::trace_bytecode), R1, R2, R3);

  __ mov(LR, R0);       // restore return address
  __ pop(state);        // restore tosca

  // return
  __ ret();

  return entry;
}


void TemplateInterpreterGenerator::count_bytecode() {
  __ inc_global_counter((address) &BytecodeCounter::_counter_value, 0, Rtemp, R2_tmp, true);
}


void TemplateInterpreterGenerator::histogram_bytecode(Template* t) {
  __ inc_global_counter((address)&BytecodeHistogram::_counters[0], sizeof(BytecodeHistogram::_counters[0]) * t->bytecode(), Rtemp, R2_tmp, true);
}


void TemplateInterpreterGenerator::histogram_bytecode_pair(Template* t) {
  const Register Rindex_addr = R2_tmp;
  Label Lcontinue;
  InlinedAddress Lcounters((address)BytecodePairHistogram::_counters);
  InlinedAddress Lindex((address)&BytecodePairHistogram::_index);
  const Register Rcounters_addr = R2_tmp;
  const Register Rindex = R4_tmp;

  // calculate new index for counter:
  // index = (_index >> log2_number_of_codes) | (bytecode << log2_number_of_codes).
  // (_index >> log2_number_of_codes) is previous bytecode

  __ ldr_literal(Rindex_addr, Lindex);
  __ ldr_s32(Rindex, Address(Rindex_addr));
  __ mov_slow(Rtemp, ((int)t->bytecode()) << BytecodePairHistogram::log2_number_of_codes);
  __ orr(Rindex, Rtemp, AsmOperand(Rindex, lsr, BytecodePairHistogram::log2_number_of_codes));
  __ str_32(Rindex, Address(Rindex_addr));

  // Rindex (R4) contains index of counter

  __ ldr_literal(Rcounters_addr, Lcounters);
  __ ldr_s32(Rtemp, Address::indexed_32(Rcounters_addr, Rindex));
  __ adds_32(Rtemp, Rtemp, 1);
  __ b(Lcontinue, mi);                           // avoid overflow
  __ str_32(Rtemp, Address::indexed_32(Rcounters_addr, Rindex));

  __ b(Lcontinue);

  __ bind_literal(Lindex);
  __ bind_literal(Lcounters);

  __ bind(Lcontinue);
}


void TemplateInterpreterGenerator::trace_bytecode(Template* t) {
  // Call a little run-time stub to avoid blow-up for each bytecode.
  // The run-time runtime saves the right registers, depending on
  // the tosca in-state for the given template.
  assert(Interpreter::trace_code(t->tos_in()) != NULL,
         "entry must have been generated");
  address trace_entry = Interpreter::trace_code(t->tos_in());
  __ call(trace_entry, relocInfo::none);
}


void TemplateInterpreterGenerator::stop_interpreter_at() {
  Label Lcontinue;
  const Register stop_at = R2_tmp;

  __ ldr_global_s32(Rtemp, (address) &BytecodeCounter::_counter_value);
  __ mov_slow(stop_at, StopInterpreterAt);

  // test bytecode counter
  __ cmp(Rtemp, stop_at);
  __ b(Lcontinue, ne);

  __ trace_state("stop_interpreter_at");
  __ breakpoint();

  __ bind(Lcontinue);
}
#endif // !PRODUCT
