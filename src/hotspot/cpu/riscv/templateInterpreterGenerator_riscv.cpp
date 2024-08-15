/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2020, Red Hat Inc. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "classfile/javaClasses.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "interpreter/bytecodeHistogram.hpp"
#include "interpreter/bytecodeTracer.hpp"
#include "interpreter/interp_masm.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "interpreter/templateInterpreterGenerator.hpp"
#include "interpreter/templateTable.hpp"
#include "memory/resourceArea.hpp"
#include "oops/arrayOop.hpp"
#include "oops/method.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/oop.inline.hpp"
#include "oops/resolvedIndyEntry.hpp"
#include "oops/resolvedMethodEntry.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "runtime/arguments.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/timer.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/debug.hpp"
#include "utilities/powerOfTwo.hpp"
#include <sys/types.h>

#ifndef PRODUCT
#include "oops/method.hpp"
#endif // !PRODUCT

// Size of interpreter code.  Increase if too small.  Interpreter will
// fail with a guarantee ("not enough space for interpreter generation");
// if too small.
// Run with +PrintInterpreter to get the VM to print out the size.
// Max size with JVMTI
int TemplateInterpreter::InterpreterCodeSize = 256 * 1024;

#define __ _masm->

//-----------------------------------------------------------------------------

address TemplateInterpreterGenerator::generate_slow_signature_handler() {
  address entry = __ pc();

  __ andi(esp, esp, -16);
  __ mv(c_rarg3, esp);
  // xmethod
  // xlocals
  // c_rarg3: first stack arg - wordSize
  // adjust sp

  __ addi(sp, c_rarg3, -18 * wordSize);
  __ addi(sp, sp, -2 * wordSize);
  __ sd(ra, Address(sp, 0));

  __ call_VM(noreg,
             CAST_FROM_FN_PTR(address,
                              InterpreterRuntime::slow_signature_handler),
             xmethod, xlocals, c_rarg3);

  // x10: result handler

  // Stack layout:
  // sp: return address           <- sp
  //      1 garbage
  //      8 integer args (if static first is unused)
  //      1 float/double identifiers
  //      8 double args
  //        stack args              <- esp
  //        garbage
  //        expression stack bottom
  //        bcp (null)
  //        ...

  // Restore ra
  __ ld(ra, Address(sp, 0));
  __ addi(sp, sp , 2 * wordSize);

  // Do FP first so we can use c_rarg3 as temp
  __ lwu(c_rarg3, Address(sp, 9 * wordSize)); // float/double identifiers

  for (int i = 0; i < Argument::n_float_register_parameters_c; i++) {
    const FloatRegister r = g_FPArgReg[i];
    Label d, done;

    __ test_bit(t0, c_rarg3, i);
    __ bnez(t0, d);
    __ flw(r, Address(sp, (10 + i) * wordSize));
    __ j(done);
    __ bind(d);
    __ fld(r, Address(sp, (10 + i) * wordSize));
    __ bind(done);
  }

  // c_rarg0 contains the result from the call of
  // InterpreterRuntime::slow_signature_handler so we don't touch it
  // here.  It will be loaded with the JNIEnv* later.
  for (int i = 1; i < Argument::n_int_register_parameters_c; i++) {
    const Register rm = g_INTArgReg[i];
    __ ld(rm, Address(sp, i * wordSize));
  }

  __ addi(sp, sp, 18 * wordSize);
  __ ret();

  return entry;
}

// Various method entries
address TemplateInterpreterGenerator::generate_math_entry(AbstractInterpreter::MethodKind kind) {
  // xmethod: Method*
  // x19_sender_sp: sender sp
  // esp: args

  // These don't need a safepoint check because they aren't virtually
  // callable. We won't enter these intrinsics from compiled code.
  // If in the future we added an intrinsic which was virtually callable
  // we'd have to worry about how to safepoint so that this code is used.

  // mathematical functions inlined by compiler
  // (interpreter must provide identical implementation
  // in order to avoid monotonicity bugs when switching
  // from interpreter to compiler in the middle of some
  // computation)
  //
  // stack:
  //        [ arg ] <-- esp
  //        [ arg ]
  // retaddr in ra

  address fn = nullptr;
  address entry_point = nullptr;
  Register continuation = ra;
  switch (kind) {
    case Interpreter::java_lang_math_abs:
      entry_point = __ pc();
      __ fld(f10, Address(esp));
      __ fabs_d(f10, f10);
      __ mv(sp, x19_sender_sp); // Restore caller's SP
      break;
    case Interpreter::java_lang_math_sqrt:
      entry_point = __ pc();
      __ fld(f10, Address(esp));
      __ fsqrt_d(f10, f10);
      __ mv(sp, x19_sender_sp);
      break;
    case Interpreter::java_lang_math_sin :
      entry_point = __ pc();
      __ fld(f10, Address(esp));
      __ mv(sp, x19_sender_sp);
      __ mv(x9, ra);
      continuation = x9;  // The first callee-saved register
      if (StubRoutines::dsin() == nullptr) {
        fn = CAST_FROM_FN_PTR(address, SharedRuntime::dsin);
      } else {
        fn = CAST_FROM_FN_PTR(address, StubRoutines::dsin());
      }
      __ call(fn);
      break;
    case Interpreter::java_lang_math_cos :
      entry_point = __ pc();
      __ fld(f10, Address(esp));
      __ mv(sp, x19_sender_sp);
      __ mv(x9, ra);
      continuation = x9;  // The first callee-saved register
      if (StubRoutines::dcos() == nullptr) {
        fn = CAST_FROM_FN_PTR(address, SharedRuntime::dcos);
      } else {
        fn = CAST_FROM_FN_PTR(address, StubRoutines::dcos());
      }
      __ call(fn);
      break;
    case Interpreter::java_lang_math_tan :
      entry_point = __ pc();
      __ fld(f10, Address(esp));
      __ mv(sp, x19_sender_sp);
      __ mv(x9, ra);
      continuation = x9;  // The first callee-saved register
      if (StubRoutines::dtan() == nullptr) {
        fn = CAST_FROM_FN_PTR(address, SharedRuntime::dtan);
      } else {
        fn = CAST_FROM_FN_PTR(address, StubRoutines::dtan());
      }
      __ call(fn);
      break;
    case Interpreter::java_lang_math_log :
      entry_point = __ pc();
      __ fld(f10, Address(esp));
      __ mv(sp, x19_sender_sp);
      __ mv(x9, ra);
      continuation = x9;  // The first callee-saved register
      if (StubRoutines::dlog() == nullptr) {
        fn = CAST_FROM_FN_PTR(address, SharedRuntime::dlog);
      } else {
        fn = CAST_FROM_FN_PTR(address, StubRoutines::dlog());
      }
      __ call(fn);
      break;
    case Interpreter::java_lang_math_log10 :
      entry_point = __ pc();
      __ fld(f10, Address(esp));
      __ mv(sp, x19_sender_sp);
      __ mv(x9, ra);
      continuation = x9;  // The first callee-saved register
      if (StubRoutines::dlog10() == nullptr) {
        fn = CAST_FROM_FN_PTR(address, SharedRuntime::dlog10);
      } else {
        fn = CAST_FROM_FN_PTR(address, StubRoutines::dlog10());
      }
      __ call(fn);
      break;
    case Interpreter::java_lang_math_exp :
      entry_point = __ pc();
      __ fld(f10, Address(esp));
      __ mv(sp, x19_sender_sp);
      __ mv(x9, ra);
      continuation = x9;  // The first callee-saved register
      if (StubRoutines::dexp() == nullptr) {
        fn = CAST_FROM_FN_PTR(address, SharedRuntime::dexp);
      } else {
        fn = CAST_FROM_FN_PTR(address, StubRoutines::dexp());
      }
      __ call(fn);
      break;
    case Interpreter::java_lang_math_pow :
      entry_point = __ pc();
      __ mv(x9, ra);
      continuation = x9;
      __ fld(f10, Address(esp, 2 * Interpreter::stackElementSize));
      __ fld(f11, Address(esp));
      __ mv(sp, x19_sender_sp);
      if (StubRoutines::dpow() == nullptr) {
        fn = CAST_FROM_FN_PTR(address, SharedRuntime::dpow);
      } else {
        fn = CAST_FROM_FN_PTR(address, StubRoutines::dpow());
      }
      __ call(fn);
      break;
    case Interpreter::java_lang_math_fmaD :
      if (UseFMA) {
        entry_point = __ pc();
        __ fld(f10, Address(esp, 4 * Interpreter::stackElementSize));
        __ fld(f11, Address(esp, 2 * Interpreter::stackElementSize));
        __ fld(f12, Address(esp));
        __ fmadd_d(f10, f10, f11, f12);
        __ mv(sp, x19_sender_sp); // Restore caller's SP
      }
      break;
    case Interpreter::java_lang_math_fmaF :
      if (UseFMA) {
        entry_point = __ pc();
        __ flw(f10, Address(esp, 2 * Interpreter::stackElementSize));
        __ flw(f11, Address(esp, Interpreter::stackElementSize));
        __ flw(f12, Address(esp));
        __ fmadd_s(f10, f10, f11, f12);
        __ mv(sp, x19_sender_sp); // Restore caller's SP
      }
      break;
    default:
      ;
  }
  if (entry_point != nullptr) {
    __ jr(continuation);
  }

  return entry_point;
}

// Abstract method entry
// Attempt to execute abstract method. Throw exception
address TemplateInterpreterGenerator::generate_abstract_entry(void) {
  // xmethod: Method*
  // x19_sender_sp: sender SP

  address entry_point = __ pc();

  // abstract method entry

  //  pop return address, reset last_sp to null
  __ empty_expression_stack();
  __ restore_bcp();      // bcp must be correct for exception handler   (was destroyed)
  __ restore_locals();   // make sure locals pointer is correct as well (was destroyed)

  // throw exception
  __ call_VM(noreg, CAST_FROM_FN_PTR(address,
                                     InterpreterRuntime::throw_AbstractMethodErrorWithMethod),
                                     xmethod);
  // the call_VM checks for exception, so we should never return here.
  __ should_not_reach_here();

  return entry_point;
}

address TemplateInterpreterGenerator::generate_StackOverflowError_handler() {
  address entry = __ pc();

#ifdef ASSERT
  {
    Label L;
    __ ld(t0, Address(fp, frame::interpreter_frame_monitor_block_top_offset * wordSize));
    __ shadd(t0, t0, fp, t0, LogBytesPerWord);
    // maximal sp for current fp (stack grows negative)
    // check if frame is complete
    __ bge(t0, sp, L);
    __ stop ("interpreter frame not set up");
    __ bind(L);
  }
#endif // ASSERT
  // Restore bcp under the assumption that the current frame is still
  // interpreted
  __ restore_bcp();

  // expression stack must be empty before entering the VM if an
  // exception happened
  __ empty_expression_stack();
  // throw exception
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_StackOverflowError));
  return entry;
}

address TemplateInterpreterGenerator::generate_ArrayIndexOutOfBounds_handler() {
  address entry = __ pc();
  // expression stack must be empty before entering the VM if an
  // exception happened
  __ empty_expression_stack();
  // setup parameters

  // convention: expect aberrant index in register x11
  __ zero_extend(c_rarg2, x11, 32);
  // convention: expect array in register x13
  __ mv(c_rarg1, x13);
  __ call_VM(noreg,
             CAST_FROM_FN_PTR(address,
                              InterpreterRuntime::
                              throw_ArrayIndexOutOfBoundsException),
             c_rarg1, c_rarg2);
  return entry;
}

address TemplateInterpreterGenerator::generate_ClassCastException_handler() {
  address entry = __ pc();

  // object is at TOS
  __ pop_reg(c_rarg1);

  // expression stack must be empty before entering the VM if an
  // exception happened
  __ empty_expression_stack();

  __ call_VM(noreg,
             CAST_FROM_FN_PTR(address,
                              InterpreterRuntime::
                              throw_ClassCastException),
             c_rarg1);
  return entry;
}

address TemplateInterpreterGenerator::generate_exception_handler_common(
  const char* name, const char* message, bool pass_oop) {
  assert(!pass_oop || message == nullptr, "either oop or message but not both");
  address entry = __ pc();
  if (pass_oop) {
    // object is at TOS
    __ pop_reg(c_rarg2);
  }
  // expression stack must be empty before entering the VM if an
  // exception happened
  __ empty_expression_stack();
  // setup parameters
  __ la(c_rarg1, Address((address)name));
  if (pass_oop) {
    __ call_VM(x10, CAST_FROM_FN_PTR(address,
                                     InterpreterRuntime::
                                     create_klass_exception),
               c_rarg1, c_rarg2);
  } else {
    // kind of lame ExternalAddress can't take null because
    // external_word_Relocation will assert.
    if (message != nullptr) {
      __ la(c_rarg2, Address((address)message));
    } else {
      __ mv(c_rarg2, NULL_WORD);
    }
    __ call_VM(x10,
               CAST_FROM_FN_PTR(address, InterpreterRuntime::create_exception),
               c_rarg1, c_rarg2);
  }
  // throw exception
  __ j(address(Interpreter::throw_exception_entry()));
  return entry;
}

address TemplateInterpreterGenerator::generate_return_entry_for(TosState state, int step, size_t index_size) {
  address entry = __ pc();

  // Restore stack bottom in case i2c adjusted stack
  __ ld(t0, Address(fp, frame::interpreter_frame_last_sp_offset * wordSize));
  __ shadd(esp, t0, fp,  t0,  LogBytesPerWord);
  // and null it as marker that esp is now tos until next java call
  __ sd(zr, Address(fp, frame::interpreter_frame_last_sp_offset * wordSize));
  __ restore_bcp();
  __ restore_locals();
  __ restore_constant_pool_cache();
  __ get_method(xmethod);

  if (state == atos) {
    Register obj = x10;
    Register mdp = x11;
    Register tmp = x12;
    __ ld(mdp, Address(xmethod, Method::method_data_offset()));
    __ profile_return_type(mdp, obj, tmp);
  }

  const Register cache = x11;
  const Register index = x12;

  if (index_size == sizeof(u4)) {
    __ load_resolved_indy_entry(cache, index);
    __ load_unsigned_short(cache, Address(cache, in_bytes(ResolvedIndyEntry::num_parameters_offset())));
    __ shadd(esp, cache, esp, t0, 3);
  } else {
    // Pop N words from the stack
    assert(index_size == sizeof(u2), "Can only be u2");
    __ load_method_entry(cache, index);
    __ load_unsigned_short(cache, Address(cache, in_bytes(ResolvedMethodEntry::num_parameters_offset())));

    __ shadd(esp, cache, esp, t0, 3);
  }

  // Restore machine SP
  __ restore_sp_after_call();

  __ check_and_handle_popframe(xthread);
  __ check_and_handle_earlyret(xthread);

  __ get_dispatch();
  __ dispatch_next(state, step);

  return entry;
}

address TemplateInterpreterGenerator::generate_deopt_entry_for(TosState state,
                                                               int step,
                                                               address continuation) {
  address entry = __ pc();
  __ restore_bcp();
  __ restore_locals();
  __ restore_constant_pool_cache();
  __ get_method(xmethod);
  __ get_dispatch();

  __ restore_sp_after_call();  // Restore SP to extended SP

  // Restore expression stack pointer
  __ ld(t0, Address(fp, frame::interpreter_frame_last_sp_offset * wordSize));
  __ shadd(esp, t0, fp,  t0,  LogBytesPerWord);
  // null last_sp until next java call
  __ sd(zr, Address(fp, frame::interpreter_frame_last_sp_offset * wordSize));

  // handle exceptions
  {
    Label L;
    __ ld(t0, Address(xthread, Thread::pending_exception_offset()));
    __ beqz(t0, L);
    __ call_VM(noreg,
               CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_pending_exception));
    __ should_not_reach_here();
    __ bind(L);
  }

  if (continuation == nullptr) {
    __ dispatch_next(state, step);
  } else {
    __ jump_to_entry(continuation);
  }
  return entry;
}

address TemplateInterpreterGenerator::generate_result_handler_for(BasicType type) {
  address entry = __ pc();
  if (type == T_OBJECT) {
    // retrieve result from frame
    __ ld(x10, Address(fp, frame::interpreter_frame_oop_temp_offset * wordSize));
    // and verify it
    __ verify_oop(x10);
  } else {
   __ cast_primitive_type(type, x10);
  }

  __ ret();                                  // return from result handler
  return entry;
}

address TemplateInterpreterGenerator::generate_safept_entry_for(TosState state,
                                                                address runtime_entry) {
  assert_cond(runtime_entry != nullptr);
  address entry = __ pc();
  __ push(state);
  __ push_cont_fastpath(xthread);
  __ call_VM(noreg, runtime_entry);
  __ pop_cont_fastpath(xthread);
  __ membar(MacroAssembler::AnyAny);
  __ dispatch_via(vtos, Interpreter::_normal_table.table_for(vtos));
  return entry;
}

// Helpers for commoning out cases in the various type of method entries.
//


// increment invocation count & check for overflow
//
// Note: checking for negative value instead of overflow
//       so we have a 'sticky' overflow test
//
// xmethod: method
//
void TemplateInterpreterGenerator::generate_counter_incr(Label* overflow) {
  Label done;
  // Note: In tiered we increment either counters in Method* or in MDO depending if we're profiling or not.
  int increment = InvocationCounter::count_increment;
  Label no_mdo;
  if (ProfileInterpreter) {
    // Are we profiling?
    __ ld(x10, Address(xmethod, Method::method_data_offset()));
    __ beqz(x10, no_mdo);
    // Increment counter in the MDO
    const Address mdo_invocation_counter(x10, in_bytes(MethodData::invocation_counter_offset()) +
                                         in_bytes(InvocationCounter::counter_offset()));
    const Address mask(x10, in_bytes(MethodData::invoke_mask_offset()));
    __ increment_mask_and_jump(mdo_invocation_counter, increment, mask, t0, t1, false, overflow);
    __ j(done);
  }
  __ bind(no_mdo);
  // Increment counter in MethodCounters
  const Address invocation_counter(t1,
                                   MethodCounters::invocation_counter_offset() +
                                   InvocationCounter::counter_offset());
  __ get_method_counters(xmethod, t1, done);
  const Address mask(t1, in_bytes(MethodCounters::invoke_mask_offset()));
  __ increment_mask_and_jump(invocation_counter, increment, mask, t0, x11, false, overflow);
  __ bind(done);
}

void TemplateInterpreterGenerator::generate_counter_overflow(Label& do_continue) {
  __ mv(c_rarg1, zr);
  __ call_VM(noreg,
             CAST_FROM_FN_PTR(address, InterpreterRuntime::frequency_counter_overflow), c_rarg1);
  __ j(do_continue);
}

// See if we've got enough room on the stack for locals plus overhead
// below JavaThread::stack_overflow_limit(). If not, throw a StackOverflowError
// without going through the signal handler, i.e., reserved and yellow zones
// will not be made usable. The shadow zone must suffice to handle the
// overflow.
// The expression stack grows down incrementally, so the normal guard
// page mechanism will work for that.
//
// NOTE: Since the additional locals are also always pushed (wasn't
// obvious in generate_method_entry) so the guard should work for them
// too.
//
// Args:
//      x13: number of additional locals this frame needs (what we must check)
//      xmethod: Method*
//
// Kills:
//      x10
void TemplateInterpreterGenerator::generate_stack_overflow_check(void) {

  // monitor entry size: see picture of stack set
  // (generate_method_entry) and frame_amd64.hpp
  const int entry_size = frame::interpreter_frame_monitor_size_in_bytes();

  // total overhead size: entry_size + (saved fp through expr stack
  // bottom).  be sure to change this if you add/subtract anything
  // to/from the overhead area
  const int overhead_size =
    -(frame::interpreter_frame_initial_sp_offset * wordSize) + entry_size;

  const int page_size = (int)os::vm_page_size();

  Label after_frame_check;

  // see if the frame is greater than one page in size. If so,
  // then we need to verify there is enough stack space remaining
  // for the additional locals.
  __ mv(t0, (page_size - overhead_size) / Interpreter::stackElementSize);
  __ bleu(x13, t0, after_frame_check);

  // compute sp as if this were going to be the last frame on
  // the stack before the red zone

  // locals + overhead, in bytes
  __ mv(x10, overhead_size);
  __ shadd(x10, x13, x10, t0, Interpreter::logStackElementSize);  // 2 slots per parameter.

  const Address stack_limit(xthread, JavaThread::stack_overflow_limit_offset());
  __ ld(t0, stack_limit);

#ifdef ASSERT
  Label limit_okay;
  // Verify that thread stack limit is non-zero.
  __ bnez(t0, limit_okay);
  __ stop("stack overflow limit is zero");
  __ bind(limit_okay);
#endif

  // Add stack limit to locals.
  __ add(x10, x10, t0);

  // Check against the current stack bottom.
  __ bgtu(sp, x10, after_frame_check);

  // Remove the incoming args, peeling the machine SP back to where it
  // was in the caller.  This is not strictly necessary, but unless we
  // do so the stack frame may have a garbage FP; this ensures a
  // correct call stack that we can always unwind.  The ANDI should be
  // unnecessary because the sender SP in x19 is always aligned, but
  // it doesn't hurt.
  __ andi(sp, x19_sender_sp, -16);

  // Note: the restored frame is not necessarily interpreted.
  // Use the shared runtime version of the StackOverflowError.
  assert(StubRoutines::throw_StackOverflowError_entry() != nullptr, "stub not yet generated");
  __ far_jump(RuntimeAddress(StubRoutines::throw_StackOverflowError_entry()));

  // all done with frame size check
  __ bind(after_frame_check);
}

// Allocate monitor and lock method (asm interpreter)
//
// Args:
//      xmethod: Method*
//      xlocals: locals
//
// Kills:
//      x10
//      c_rarg0, c_rarg1, c_rarg2, c_rarg3, ...(param regs)
//      t0, t1 (temporary regs)
void TemplateInterpreterGenerator::lock_method() {
  // synchronize method
  const Address access_flags(xmethod, Method::access_flags_offset());
  const Address monitor_block_top(fp, frame::interpreter_frame_monitor_block_top_offset * wordSize);
  const int entry_size = frame::interpreter_frame_monitor_size_in_bytes();

#ifdef ASSERT
  __ lwu(x10, access_flags);
  __ verify_access_flags(x10, JVM_ACC_SYNCHRONIZED, "method doesn't need synchronization", false);
#endif // ASSERT

  // get synchronization object
  {
    Label done;
    __ lwu(x10, access_flags);
    __ andi(t0, x10, JVM_ACC_STATIC);
    // get receiver (assume this is frequent case)
    __ ld(x10, Address(xlocals, Interpreter::local_offset_in_bytes(0)));
    __ beqz(t0, done);
    __ load_mirror(x10, xmethod, x15, t1);

#ifdef ASSERT
    {
      Label L;
      __ bnez(x10, L);
      __ stop("synchronization object is null");
      __ bind(L);
    }
#endif // ASSERT

    __ bind(done);
  }

  // add space for monitor & lock
  __ check_extended_sp();
  __ add(sp, sp, - entry_size); // add space for a monitor entry
  __ add(esp, esp, - entry_size);
  __ sub(t0, sp, fp);
  __ srai(t0, t0, Interpreter::logStackElementSize);
  __ sd(t0, Address(fp, frame::interpreter_frame_extended_sp_offset * wordSize));
  __ sub(t0, esp, fp);
  __ srai(t0, t0, Interpreter::logStackElementSize);
  __ sd(t0, monitor_block_top);  // set new monitor block top
  // store object
  __ sd(x10, Address(esp, BasicObjectLock::obj_offset()));
  __ mv(c_rarg1, esp); // object address
  __ lock_object(c_rarg1);
}

// Generate a fixed interpreter frame. This is identical setup for
// interpreted methods and for native methods hence the shared code.
//
// Args:
//      ra: return address
//      xmethod: Method*
//      xlocals: pointer to locals
//      xcpool: cp cache
//      stack_pointer: previous sp
void TemplateInterpreterGenerator::generate_fixed_frame(bool native_call) {
  // initialize fixed part of activation frame
  if (native_call) {
    __ add(esp, sp, - 14 * wordSize);
    __ mv(xbcp, zr);
    __ add(sp, sp, - 14 * wordSize);
    // add 2 zero-initialized slots for native calls
    __ sd(zr, Address(sp, 13 * wordSize));
    __ sd(zr, Address(sp, 12 * wordSize));
  } else {
    __ add(esp, sp, - 12 * wordSize);
    __ ld(t0, Address(xmethod, Method::const_offset()));     // get ConstMethod
    __ add(xbcp, t0, in_bytes(ConstMethod::codes_offset())); // get codebase
    __ add(sp, sp, - 12 * wordSize);
  }
  __ sd(xbcp, Address(sp, wordSize));
  __ mv(t0, frame::interpreter_frame_initial_sp_offset);
  __ sd(t0, Address(sp, 0));

  if (ProfileInterpreter) {
    Label method_data_continue;
    __ ld(t0, Address(xmethod, Method::method_data_offset()));
    __ beqz(t0, method_data_continue);
    __ la(t0, Address(t0, in_bytes(MethodData::data_offset())));
    __ bind(method_data_continue);
  }

  __ sd(xmethod, Address(sp, 7 * wordSize));
  __ sd(ProfileInterpreter ? t0 : zr, Address(sp, 6 * wordSize));

  __ sd(ra, Address(sp, 11 * wordSize));
  __ sd(fp, Address(sp, 10 * wordSize));
  __ la(fp, Address(sp, 12 * wordSize)); // include ra & fp

  __ ld(xcpool, Address(xmethod, Method::const_offset()));
  __ ld(xcpool, Address(xcpool, ConstMethod::constants_offset()));
  __ ld(xcpool, Address(xcpool, ConstantPool::cache_offset()));
  __ sd(xcpool, Address(sp, 3 * wordSize));
  __ sub(t0, xlocals, fp);
  __ srai(t0, t0, Interpreter::logStackElementSize);   // t0 = xlocals - fp();
  // Store relativized xlocals, see frame::interpreter_frame_locals().
  __ sd(t0, Address(sp, 2 * wordSize));

  // set sender sp
  // leave last_sp as null
  __ sd(x19_sender_sp, Address(sp, 9 * wordSize));
  __ sd(zr, Address(sp, 8 * wordSize));

  // Get mirror and store it in the frame as GC root for this Method*
  __ load_mirror(t2, xmethod, x15, t1);
  __ sd(t2, Address(sp, 4 * wordSize));

  if (!native_call) {
    __ ld(t0, Address(xmethod, Method::const_offset()));
    __ lhu(t0, Address(t0, ConstMethod::max_stack_offset()));
    __ add(t0, t0, MAX2(3, Method::extra_stack_entries()));
    __ slli(t0, t0, 3);
    __ sub(t0, sp, t0);
    __ andi(t0, t0, -16);
    __ sub(t1, t0, fp);
    __ srai(t1, t1, Interpreter::logStackElementSize);
    // Store extended SP
    __ sd(t1, Address(sp, 5 * wordSize));
    // Move SP out of the way
    __ mv(sp, t0);
  } else {
    // Make sure there is room for the exception oop pushed in case method throws
    // an exception (see TemplateInterpreterGenerator::generate_throw_exception())
    __ sub(t0, sp, 2 * wordSize);
    __ sub(t1, t0, fp);
    __ srai(t1, t1, Interpreter::logStackElementSize);
    __ sd(t1, Address(sp, 5 * wordSize));
    __ mv(sp, t0);
  }
}

// End of helpers

// Various method entries
//------------------------------------------------------------------------------------------------------------------------
//
//

// Method entry for java.lang.ref.Reference.get.
address TemplateInterpreterGenerator::generate_Reference_get_entry(void) {
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
  // This code is based on generate_accessor_entry.
  //
  // xmethod: Method*
  // x19_sender_sp: senderSP must preserve for slow path, set SP to it on fast path

  // ra is live.  It must be saved around calls.

  address entry = __ pc();

  const int referent_offset = java_lang_ref_Reference::referent_offset();
  guarantee(referent_offset > 0, "referent offset not initialized");

  Label slow_path;
  const Register local_0 = c_rarg0;
  // Check if local 0 isn't null
  // If the receiver is null then it is OK to jump to the slow path.
  __ ld(local_0, Address(esp, 0));
  __ beqz(local_0, slow_path);

  // Load the value of the referent field.
  const Address field_address(local_0, referent_offset);
  BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
  bs->load_at(_masm, IN_HEAP | ON_WEAK_OOP_REF, T_OBJECT, local_0, field_address, /*tmp1*/ t0, /*tmp2*/ t1);

  // areturn
  __ andi(sp, x19_sender_sp, -16);  // done with stack
  __ ret();

  // generate a vanilla interpreter entry as the slow path
  __ bind(slow_path);
  __ jump_to_entry(Interpreter::entry_for_kind(Interpreter::zerolocals));
  return entry;
}

/**
 * Method entry for static native methods:
 *   int java.util.zip.CRC32.update(int crc, int b)
 */
address TemplateInterpreterGenerator::generate_CRC32_update_entry() {
  // TODO: Unimplemented generate_CRC32_update_entry
  return nullptr;
}

/**
 * Method entry for static native methods:
 *   int java.util.zip.CRC32.updateBytes(int crc, byte[] b, int off, int len)
 *   int java.util.zip.CRC32.updateByteBuffer(int crc, long buf, int off, int len)
 */
address TemplateInterpreterGenerator::generate_CRC32_updateBytes_entry(AbstractInterpreter::MethodKind kind) {
  // TODO: Unimplemented generate_CRC32_updateBytes_entry
  return nullptr;
}

/**
 * Method entry for intrinsic-candidate (non-native) methods:
 *   int java.util.zip.CRC32C.updateBytes(int crc, byte[] b, int off, int end)
 *   int java.util.zip.CRC32C.updateDirectByteBuffer(int crc, long buf, int off, int end)
 * Unlike CRC32, CRC32C does not have any methods marked as native
 * CRC32C also uses an "end" variable instead of the length variable CRC32 uses
 */
address TemplateInterpreterGenerator::generate_CRC32C_updateBytes_entry(AbstractInterpreter::MethodKind kind) {
  // TODO: Unimplemented generate_CRC32C_updateBytes_entry
  return nullptr;
}

// Not supported
address TemplateInterpreterGenerator::generate_Float_intBitsToFloat_entry() { return nullptr; }
address TemplateInterpreterGenerator::generate_Float_floatToRawIntBits_entry() { return nullptr; }
address TemplateInterpreterGenerator::generate_Double_longBitsToDouble_entry() { return nullptr; }
address TemplateInterpreterGenerator::generate_Double_doubleToRawLongBits_entry() { return nullptr; }
address TemplateInterpreterGenerator::generate_Float_float16ToFloat_entry() { return nullptr; }
address TemplateInterpreterGenerator::generate_Float_floatToFloat16_entry() { return nullptr; }

void TemplateInterpreterGenerator::bang_stack_shadow_pages(bool native_call) {
  // See more discussion in stackOverflow.hpp.

  const int shadow_zone_size = checked_cast<int>(StackOverflow::stack_shadow_zone_size());
  const int page_size = (int)os::vm_page_size();
  const int n_shadow_pages = shadow_zone_size / page_size;

#ifdef ASSERT
  Label L_good_limit;
  __ ld(t0, Address(xthread, JavaThread::shadow_zone_safe_limit()));
  __ bnez(t0, L_good_limit);
  __ stop("shadow zone safe limit is not initialized");
  __ bind(L_good_limit);

  Label L_good_watermark;
  __ ld(t0, Address(xthread, JavaThread::shadow_zone_growth_watermark()));
  __ bnez(t0, L_good_watermark);
  __ stop("shadow zone growth watermark is not initialized");
  __ bind(L_good_watermark);
#endif

  Label L_done;

  __ ld(t0, Address(xthread, JavaThread::shadow_zone_growth_watermark()));
  __ bgtu(sp, t0, L_done);

  for (int p = 1; p <= n_shadow_pages; p++) {
    __ bang_stack_with_offset(p * page_size);
  }

  // Record the new watermark, but only if the update is above the safe limit.
  // Otherwise, the next time around the check above would pass the safe limit.
  __ ld(t0, Address(xthread, JavaThread::shadow_zone_safe_limit()));
  __ bleu(sp, t0, L_done);
  __ sd(sp, Address(xthread, JavaThread::shadow_zone_growth_watermark()));

  __ bind(L_done);
}

// Interpreter stub for calling a native method. (asm interpreter)
// This sets up a somewhat different looking stack for calling the
// native method than the typical interpreter frame setup.
address TemplateInterpreterGenerator::generate_native_entry(bool synchronized) {
  // determine code generation flags
  bool inc_counter = UseCompiler || CountCompiledCalls;

  // x11: Method*
  // x30: sender sp

  address entry_point = __ pc();

  const Address constMethod       (xmethod, Method::const_offset());
  const Address access_flags      (xmethod, Method::access_flags_offset());
  const Address size_of_parameters(x12, ConstMethod::
                                   size_of_parameters_offset());

  // get parameter size (always needed)
  __ ld(x12, constMethod);
  __ load_unsigned_short(x12, size_of_parameters);

  // Native calls don't need the stack size check since they have no
  // expression stack and the arguments are already on the stack and
  // we only add a handful of words to the stack.

  // xmethod: Method*
  // x12: size of parameters
  // x30: sender sp

  // for natives the size of locals is zero

  // compute beginning of parameters (xlocals)
  __ shadd(xlocals, x12, esp, xlocals, 3);
  __ addi(xlocals, xlocals, -wordSize);

  // Pull SP back to minimum size: this avoids holes in the stack
  __ andi(sp, esp, -16);

  // initialize fixed part of activation frame
  generate_fixed_frame(true);

  // make sure method is native & not abstract
#ifdef ASSERT
  __ lwu(x10, access_flags);
  __ verify_access_flags(x10, JVM_ACC_NATIVE, "tried to execute non-native method as native", false);
  __ verify_access_flags(x10, JVM_ACC_ABSTRACT, "tried to execute abstract method in interpreter");
#endif

  // Since at this point in the method invocation the exception
  // handler would try to exit the monitor of synchronized methods
  // which hasn't been entered yet, we set the thread local variable
  // _do_not_unlock_if_synchronized to true. The remove_activation
  // will check this flag.

  const Address do_not_unlock_if_synchronized(xthread,
                                              in_bytes(JavaThread::do_not_unlock_if_synchronized_offset()));
  __ mv(t1, true);
  __ sb(t1, do_not_unlock_if_synchronized);

  // increment invocation count & check for overflow
  Label invocation_counter_overflow;
  if (inc_counter) {
    generate_counter_incr(&invocation_counter_overflow);
  }

  Label continue_after_compile;
  __ bind(continue_after_compile);

  bang_stack_shadow_pages(true);

  // reset the _do_not_unlock_if_synchronized flag
  __ sb(zr, do_not_unlock_if_synchronized);

  // check for synchronized methods
  // Must happen AFTER invocation_counter check and stack overflow check,
  // so method is not locked if overflows.
  if (synchronized) {
    lock_method();
  } else {
    // no synchronization necessary
#ifdef ASSERT
    __ lwu(x10, access_flags);
    __ verify_access_flags(x10, JVM_ACC_SYNCHRONIZED, "method needs synchronization");
#endif
  }

  // start execution
#ifdef ASSERT
  __ verify_frame_setup();
#endif

  // jvmti support
  __ notify_method_entry();

  // work registers
  const Register t = x18;
  const Register result_handler = x19;

  // allocate space for parameters
  __ ld(t, Address(xmethod, Method::const_offset()));
  __ load_unsigned_short(t, Address(t, ConstMethod::size_of_parameters_offset()));

  __ slli(t, t, Interpreter::logStackElementSize);
  __ sub(x30, esp, t);
  __ andi(sp, x30, -16);
  __ mv(esp, x30);

  // get signature handler
  {
    Label L;
    __ ld(t, Address(xmethod, Method::signature_handler_offset()));
    __ bnez(t, L);
    __ call_VM(noreg,
               CAST_FROM_FN_PTR(address,
                                InterpreterRuntime::prepare_native_call),
               xmethod);
    __ ld(t, Address(xmethod, Method::signature_handler_offset()));
    __ bind(L);
  }

  // call signature handler
  assert(InterpreterRuntime::SignatureHandlerGenerator::from() == xlocals,
         "adjust this code");
  assert(InterpreterRuntime::SignatureHandlerGenerator::to() == sp,
         "adjust this code");
  assert(InterpreterRuntime::SignatureHandlerGenerator::temp() == t0,
         "adjust this code");

  // The generated handlers do not touch xmethod (the method).
  // However, large signatures cannot be cached and are generated
  // each time here.  The slow-path generator can do a GC on return,
  // so we must reload it after the call.
  __ jalr(t);
  __ get_method(xmethod);        // slow path can do a GC, reload xmethod


  // result handler is in x10
  // set result handler
  __ mv(result_handler, x10);
  // pass mirror handle if static call
  {
    Label L;
    __ lwu(t, Address(xmethod, Method::access_flags_offset()));
    __ test_bit(t0, t, exact_log2(JVM_ACC_STATIC));
    __ beqz(t0, L);
    // get mirror
    __ load_mirror(t, xmethod, x28, t1);
    // copy mirror into activation frame
    __ sd(t, Address(fp, frame::interpreter_frame_oop_temp_offset * wordSize));
    // pass handle to mirror
    __ addi(c_rarg1, fp, frame::interpreter_frame_oop_temp_offset * wordSize);
    __ bind(L);
  }

  // get native function entry point in x28
  {
    Label L;
    __ ld(x28, Address(xmethod, Method::native_function_offset()));
    ExternalAddress unsatisfied(SharedRuntime::native_method_throw_unsatisfied_link_error_entry());
    __ la(t, unsatisfied);
    __ load_long_misaligned(t1, Address(t, 0), t0, 2); // 2 bytes aligned, but not 4 or 8

    __ bne(x28, t1, L);
    __ call_VM(noreg,
               CAST_FROM_FN_PTR(address,
                                InterpreterRuntime::prepare_native_call),
               xmethod);
    __ get_method(xmethod);
    __ ld(x28, Address(xmethod, Method::native_function_offset()));
    __ bind(L);
  }

  // pass JNIEnv
  __ add(c_rarg0, xthread, in_bytes(JavaThread::jni_environment_offset()));

  // It is enough that the pc() points into the right code
  // segment. It does not have to be the correct return pc.
  Label native_return;
  __ set_last_Java_frame(esp, fp, native_return, x30);

  // change thread state
#ifdef ASSERT
  {
    Label L;
    __ lwu(t, Address(xthread, JavaThread::thread_state_offset()));
    __ addi(t0, zr, (u1)_thread_in_Java);
    __ beq(t, t0, L);
    __ stop("Wrong thread state in native stub");
    __ bind(L);
  }
#endif

  // Change state to native
  __ la(t1, Address(xthread, JavaThread::thread_state_offset()));
  __ mv(t0, _thread_in_native);
  __ membar(MacroAssembler::LoadStore | MacroAssembler::StoreStore);
  __ sw(t0, Address(t1));

  // Call the native method.
  __ jalr(x28);
  __ bind(native_return);
  __ get_method(xmethod);
  // result potentially in x10 or f10

  // Restore cpu control state after JNI call
  __ restore_cpu_control_state_after_jni(t0);

  // make room for the pushes we're about to do
  __ sub(t0, esp, 4 * wordSize);
  __ andi(sp, t0, -16);

  // NOTE: The order of these pushes is known to frame::interpreter_frame_result
  // in order to extract the result of a method call. If the order of these
  // pushes change or anything else is added to the stack then the code in
  // interpreter_frame_result must also change.
  __ push(dtos);
  __ push(ltos);

  // change thread state
  // Force all preceding writes to be observed prior to thread state change
  __ membar(MacroAssembler::LoadStore | MacroAssembler::StoreStore);

  __ mv(t0, _thread_in_native_trans);
  __ sw(t0, Address(xthread, JavaThread::thread_state_offset()));

  // Force this write out before the read below
  if (!UseSystemMemoryBarrier) {
    __ membar(MacroAssembler::AnyAny);
  }

  // check for safepoint operation in progress and/or pending suspend requests
  {
    Label L, Continue;

    // We need an acquire here to ensure that any subsequent load of the
    // global SafepointSynchronize::_state flag is ordered after this load
    // of the thread-local polling word. We don't want this poll to
    // return false (i.e. not safepointing) and a later poll of the global
    // SafepointSynchronize::_state spuriously to return true.
    //
    // This is to avoid a race when we're in a native->Java transition
    // racing the code which wakes up from a safepoint.
    __ safepoint_poll(L, true /* at_return */, true /* acquire */, false /* in_nmethod */);
    __ lwu(t1, Address(xthread, JavaThread::suspend_flags_offset()));
    __ beqz(t1, Continue);
    __ bind(L);

    // Don't use call_VM as it will see a possible pending exception
    // and forward it and never return here preventing us from
    // clearing _last_native_pc down below. So we do a runtime call by
    // hand.
    //
    __ mv(c_rarg0, xthread);
    __ rt_call(CAST_FROM_FN_PTR(address, JavaThread::check_special_condition_for_native_trans));
    __ get_method(xmethod);
    __ reinit_heapbase();
    __ bind(Continue);
  }

  // change thread state
  // Force all preceding writes to be observed prior to thread state change
  __ membar(MacroAssembler::LoadStore | MacroAssembler::StoreStore);

  __ mv(t0, _thread_in_Java);
  __ sw(t0, Address(xthread, JavaThread::thread_state_offset()));

  // reset_last_Java_frame
  __ reset_last_Java_frame(true);

  if (CheckJNICalls) {
    // clear_pending_jni_exception_check
    __ sd(zr, Address(xthread, JavaThread::pending_jni_exception_check_fn_offset()));
  }

  // reset handle block
  __ ld(t, Address(xthread, JavaThread::active_handles_offset()));
  __ sd(zr, Address(t, JNIHandleBlock::top_offset()));

  // If result is an oop unbox and store it in frame where gc will see it
  // and result handler will pick it up

  {
    Label no_oop;
    __ la(t, ExternalAddress(AbstractInterpreter::result_handler(T_OBJECT)));
    __ bne(t, result_handler, no_oop);
    // Unbox oop result, e.g. JNIHandles::resolve result.
    __ pop(ltos);
    __ resolve_jobject(x10, t, t1);
    __ sd(x10, Address(fp, frame::interpreter_frame_oop_temp_offset * wordSize));
    // keep stack depth as expected by pushing oop which will eventually be discarded
    __ push(ltos);
    __ bind(no_oop);
  }

  {
    Label no_reguard;
    __ lwu(t0, Address(xthread, in_bytes(JavaThread::stack_guard_state_offset())));
    __ addi(t1, zr, (u1)StackOverflow::stack_guard_yellow_reserved_disabled);
    __ bne(t0, t1, no_reguard);

    __ push_call_clobbered_registers();
    __ mv(c_rarg0, xthread);
    __ rt_call(CAST_FROM_FN_PTR(address, SharedRuntime::reguard_yellow_pages));
    __ pop_call_clobbered_registers();
    __ bind(no_reguard);
  }

  // The method register is junk from after the thread_in_native transition
  // until here.  Also can't call_VM until the bcp has been
  // restored.  Need bcp for throwing exception below so get it now.
  __ get_method(xmethod);

  // restore bcp to have legal interpreter frame, i.e., bci == 0 <=>
  // xbcp == code_base()
  __ ld(xbcp, Address(xmethod, Method::const_offset()));   // get ConstMethod*
  __ add(xbcp, xbcp, in_bytes(ConstMethod::codes_offset()));          // get codebase
  // handle exceptions (exception handling will handle unlocking!)
  {
    Label L;
    __ ld(t0, Address(xthread, Thread::pending_exception_offset()));
    __ beqz(t0, L);
    // Note: At some point we may want to unify this with the code
    // used in call_VM_base(); i.e., we should use the
    // StubRoutines::forward_exception code. For now this doesn't work
    // here because the sp is not correctly set at this point.
    __ MacroAssembler::call_VM(noreg,
                               CAST_FROM_FN_PTR(address,
                               InterpreterRuntime::throw_pending_exception));
    __ should_not_reach_here();
    __ bind(L);
  }

  // do unlocking if necessary
  {
    Label L;
    __ lwu(t, Address(xmethod, Method::access_flags_offset()));
    __ test_bit(t0, t, exact_log2(JVM_ACC_SYNCHRONIZED));
    __ beqz(t0, L);
    // the code below should be shared with interpreter macro
    // assembler implementation
    {
      Label unlock;
      // BasicObjectLock will be first in list, since this is a
      // synchronized method. However, need to check that the object
      // has not been unlocked by an explicit monitorexit bytecode.

      // monitor expect in c_rarg1 for slow unlock path
      __ la(c_rarg1, Address(fp,   // address of first monitor
                             (intptr_t)(frame::interpreter_frame_initial_sp_offset *
                                        wordSize - sizeof(BasicObjectLock))));

      __ ld(t, Address(c_rarg1, BasicObjectLock::obj_offset()));
      __ bnez(t, unlock);

      // Entry already unlocked, need to throw exception
      __ MacroAssembler::call_VM(noreg,
                                 CAST_FROM_FN_PTR(address,
                                                  InterpreterRuntime::throw_illegal_monitor_state_exception));
      __ should_not_reach_here();

      __ bind(unlock);
      __ unlock_object(c_rarg1);
    }
    __ bind(L);
  }

  // jvmti support
  // Note: This must happen _after_ handling/throwing any exceptions since
  //       the exception handler code notifies the runtime of method exits
  //       too. If this happens before, method entry/exit notifications are
  //       not properly paired (was bug - gri 11/22/99).
  __ notify_method_exit(vtos, InterpreterMacroAssembler::NotifyJVMTI);

  __ pop(ltos);
  __ pop(dtos);

  __ jalr(result_handler);

  // remove activation
  __ ld(esp, Address(fp, frame::interpreter_frame_sender_sp_offset * wordSize)); // get sender sp
  // remove frame anchor
  __ leave();

  // restore sender sp
  __ mv(sp, esp);

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
  const bool inc_counter  = UseCompiler || CountCompiledCalls;

  // t0: sender sp
  address entry_point = __ pc();

  const Address constMethod(xmethod, Method::const_offset());
  const Address access_flags(xmethod, Method::access_flags_offset());
  const Address size_of_parameters(x13,
                                   ConstMethod::size_of_parameters_offset());
  const Address size_of_locals(x13, ConstMethod::size_of_locals_offset());

  // get parameter size (always needed)
  // need to load the const method first
  __ ld(x13, constMethod);
  __ load_unsigned_short(x12, size_of_parameters);

  // x12: size of parameters

  __ load_unsigned_short(x13, size_of_locals); // get size of locals in words
  __ sub(x13, x13, x12); // x13 = no. of additional locals

  // see if we've got enough room on the stack for locals plus overhead.
  generate_stack_overflow_check();

  // compute beginning of parameters (xlocals)
  __ shadd(xlocals, x12, esp, t1, 3);
  __ add(xlocals, xlocals, -wordSize);

  // Make room for additional locals
  __ slli(t1, x13, 3);
  __ sub(t0, esp, t1);

  // Padding between locals and fixed part of activation frame to ensure
  // SP is always 16-byte aligned.
  __ andi(sp, t0, -16);

  // x13 - # of additional locals
  // allocate space for locals
  // explicitly initialize locals
  {
    Label exit, loop;
    __ blez(x13, exit); // do nothing if x13 <= 0
    __ bind(loop);
    __ sd(zr, Address(t0));
    __ add(t0, t0, wordSize);
    __ add(x13, x13, -1); // until everything initialized
    __ bnez(x13, loop);
    __ bind(exit);
  }

  // And the base dispatch table
  __ get_dispatch();

  // initialize fixed part of activation frame
  generate_fixed_frame(false);

  // make sure method is not native & not abstract
#ifdef ASSERT
  __ lwu(x10, access_flags);
  __ verify_access_flags(x10, JVM_ACC_NATIVE, "tried to execute native method as non-native");
  __ verify_access_flags(x10, JVM_ACC_ABSTRACT, "tried to execute abstract method in interpreter");
#endif

  // Since at this point in the method invocation the exception
  // handler would try to exit the monitor of synchronized methods
  // which hasn't been entered yet, we set the thread local variable
  // _do_not_unlock_if_synchronized to true. The remove_activation
  // will check this flag.

  const Address do_not_unlock_if_synchronized(xthread,
                                              in_bytes(JavaThread::do_not_unlock_if_synchronized_offset()));
  __ mv(t1, true);
  __ sb(t1, do_not_unlock_if_synchronized);

  Label no_mdp;
  const Register mdp = x13;
  __ ld(mdp, Address(xmethod, Method::method_data_offset()));
  __ beqz(mdp, no_mdp);
  __ add(mdp, mdp, in_bytes(MethodData::data_offset()));
  __ profile_parameters_type(mdp, x11, x12, x14); // use x11, x12, x14 as tmp registers
  __ bind(no_mdp);

  // increment invocation count & check for overflow
  Label invocation_counter_overflow;
  if (inc_counter) {
    generate_counter_incr(&invocation_counter_overflow);
  }

  Label continue_after_compile;
  __ bind(continue_after_compile);

  bang_stack_shadow_pages(false);

  // reset the _do_not_unlock_if_synchronized flag
  __ sb(zr, do_not_unlock_if_synchronized);

  // check for synchronized methods
  // Must happen AFTER invocation_counter check and stack overflow check,
  // so method is not locked if overflows.
  if (synchronized) {
    // Allocate monitor and lock method
    lock_method();
  } else {
    // no synchronization necessary
#ifdef ASSERT
    __ lwu(x10, access_flags);
    __ verify_access_flags(x10, JVM_ACC_SYNCHRONIZED, "method needs synchronization");
#endif
  }

  // start execution
#ifdef ASSERT
  __ verify_frame_setup();
#endif

  // jvmti support
  __ notify_method_entry();

  __ dispatch_next(vtos);

  // invocation counter overflow
  if (inc_counter) {
    // Handle overflow of counter and compile method
    __ bind(invocation_counter_overflow);
    generate_counter_overflow(continue_after_compile);
  }

  return entry_point;
}

// Method entry for java.lang.Thread.currentThread
address TemplateInterpreterGenerator::generate_currentThread() {
  address entry_point = __ pc();

  __ ld(x10, Address(xthread, JavaThread::vthread_offset()));
  __ resolve_oop_handle(x10, t0, t1);
  __ ret();

  return entry_point;
}

//-----------------------------------------------------------------------------
// Exceptions

void TemplateInterpreterGenerator::generate_throw_exception() {
  // Entry point in previous activation (i.e., if the caller was
  // interpreted)
  Interpreter::_rethrow_exception_entry = __ pc();
  // Restore sp to interpreter_frame_last_sp even though we are going
  // to empty the expression stack for the exception processing.
  __ sd(zr, Address(fp, frame::interpreter_frame_last_sp_offset * wordSize));
  // x10: exception
  // x13: return address/pc that threw exception
  __ restore_bcp();    // xbcp points to call/send
  __ restore_locals();
  __ restore_constant_pool_cache();
  __ reinit_heapbase();  // restore xheapbase as heapbase.
  __ get_dispatch();

  // Entry point for exceptions thrown within interpreter code
  Interpreter::_throw_exception_entry = __ pc();
  // If we came here via a NullPointerException on the receiver of a
  // method, xthread may be corrupt.
  __ get_method(xmethod);
  // expression stack is undefined here
  // x10: exception
  // xbcp: exception bcp
  __ verify_oop(x10);
  __ mv(c_rarg1, x10);

  // expression stack must be empty before entering the VM in case of
  // an exception
  __ empty_expression_stack();
  // find exception handler address and preserve exception oop
  __ call_VM(x13,
             CAST_FROM_FN_PTR(address,
                          InterpreterRuntime::exception_handler_for_exception),
             c_rarg1);

  // Restore machine SP
  __ restore_sp_after_call();

  // x10: exception handler entry point
  // x13: preserved exception oop
  // xbcp: bcp for exception handler
  __ push_ptr(x13); // push exception which is now the only value on the stack
  __ jr(x10); // jump to exception handler (may be _remove_activation_entry!)

  // If the exception is not handled in the current frame the frame is
  // removed and the exception is rethrown (i.e. exception
  // continuation is _rethrow_exception).
  //
  // Note: At this point the bci is still the bxi for the instruction
  // which caused the exception and the expression stack is
  // empty. Thus, for any VM calls at this point, GC will find a legal
  // oop map (with empty expression stack).

  //
  // JVMTI PopFrame support
  //

  Interpreter::_remove_activation_preserving_args_entry = __ pc();
  __ empty_expression_stack();
  // Set the popframe_processing bit in pending_popframe_condition
  // indicating that we are currently handling popframe, so that
  // call_VMs that may happen later do not trigger new popframe
  // handling cycles.
  __ lwu(x13, Address(xthread, JavaThread::popframe_condition_offset()));
  __ ori(x13, x13, JavaThread::popframe_processing_bit);
  __ sw(x13, Address(xthread, JavaThread::popframe_condition_offset()));

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
    __ ld(c_rarg1, Address(fp, frame::return_addr_offset * wordSize));
    __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, InterpreterRuntime::interpreter_contains), c_rarg1);
    __ bnez(x10, caller_not_deoptimized);

    // Compute size of arguments for saving when returning to
    // deoptimized caller
    __ get_method(x10);
    __ ld(x10, Address(x10, Method::const_offset()));
    __ load_unsigned_short(x10, Address(x10, in_bytes(ConstMethod::
                                                      size_of_parameters_offset())));
    __ slli(x10, x10, Interpreter::logStackElementSize);
    __ restore_locals();
    __ sub(xlocals, xlocals, x10);
    __ add(xlocals, xlocals, wordSize);
    // Save these arguments
    __ super_call_VM_leaf(CAST_FROM_FN_PTR(address,
                                           Deoptimization::
                                           popframe_preserve_args),
                          xthread, x10, xlocals);

    __ remove_activation(vtos,
                         /* throw_monitor_exception */ false,
                         /* install_monitor_exception */ false,
                         /* notify_jvmdi */ false);

    // Inform deoptimization that it is responsible for restoring
    // these arguments
    __ mv(t0, JavaThread::popframe_force_deopt_reexecution_bit);
    __ sw(t0, Address(xthread, JavaThread::popframe_condition_offset()));

    // Continue in deoptimization handler
    __ ret();

    __ bind(caller_not_deoptimized);
  }

  __ remove_activation(vtos,
                       /* throw_monitor_exception */ false,
                       /* install_monitor_exception */ false,
                       /* notify_jvmdi */ false);

  // Restore the last_sp and null it out
  __ ld(t0, Address(fp, frame::interpreter_frame_last_sp_offset * wordSize));
  __ shadd(esp, t0, fp,  t0,  LogBytesPerWord);
  __ sd(zr, Address(fp, frame::interpreter_frame_last_sp_offset * wordSize));

  __ restore_bcp();
  __ restore_locals();
  __ restore_constant_pool_cache();
  __ get_method(xmethod);
  __ get_dispatch();

  // The method data pointer was incremented already during
  // call profiling. We have to restore the mdp for the current bcp.
  if (ProfileInterpreter) {
    __ set_method_data_pointer_for_bcp();
  }

  // Clear the popframe condition flag
  __ sw(zr, Address(xthread, JavaThread::popframe_condition_offset()));
  assert(JavaThread::popframe_inactive == 0, "fix popframe_inactive");

#if INCLUDE_JVMTI
  {
    Label L_done;

    __ lbu(t0, Address(xbcp, 0));
    __ mv(t1, Bytecodes::_invokestatic);
    __ bne(t1, t0, L_done);

    // The member name argument must be restored if _invokestatic is re-executed after a PopFrame call.
    // Detect such a case in the InterpreterRuntime function and return the member name argument,or null.

    __ ld(c_rarg0, Address(xlocals, 0));
    __ call_VM(x10, CAST_FROM_FN_PTR(address, InterpreterRuntime::member_name_arg_or_null),c_rarg0, xmethod, xbcp);

    __ beqz(x10, L_done);

    __ sd(x10, Address(esp, 0));
    __ bind(L_done);
  }
#endif // INCLUDE_JVMTI

  // Restore machine SP
  __ restore_sp_after_call();

  __ dispatch_next(vtos);
  // end of PopFrame support

  Interpreter::_remove_activation_entry = __ pc();

  // preserve exception over this code sequence
  __ pop_ptr(x10);
  __ sd(x10, Address(xthread, JavaThread::vm_result_offset()));
  // remove the activation (without doing throws on illegalMonitorExceptions)
  __ remove_activation(vtos, false, true, false);
  // restore exception
  __ get_vm_result(x10, xthread);

  // In between activations - previous activation type unknown yet
  // compute continuation point - the continuation point expects the
  // following registers set up:
  //
  // x10: exception
  // ra: return address/pc that threw exception
  // sp: expression stack of caller
  // fp: fp of caller
  // FIXME: There's no point saving ra here because VM calls don't trash it
  __ sub(sp, sp, 2 * wordSize);
  __ sd(x10, Address(sp, 0));                   // save exception
  __ sd(ra, Address(sp, wordSize));             // save return address
  __ super_call_VM_leaf(CAST_FROM_FN_PTR(address,
                                         SharedRuntime::exception_handler_for_return_address),
                        xthread, ra);
  __ mv(x11, x10);                              // save exception handler
  __ ld(x10, Address(sp, 0));                   // restore exception
  __ ld(ra, Address(sp, wordSize));             // restore return address
  __ add(sp, sp, 2 * wordSize);
  // We might be returning to a deopt handler that expects x13 to
  // contain the exception pc
  __ mv(x13, ra);
  // Note that an "issuing PC" is actually the next PC after the call
  __ jr(x11);                                   // jump to exception
                                                // handler of caller
}

//
// JVMTI ForceEarlyReturn support
//
address TemplateInterpreterGenerator::generate_earlyret_entry_for(TosState state)  {
  address entry = __ pc();

  __ restore_bcp();
  __ restore_locals();
  __ empty_expression_stack();
  __ load_earlyret_value(state);

  __ ld(t0, Address(xthread, JavaThread::jvmti_thread_state_offset()));
  Address cond_addr(t0, JvmtiThreadState::earlyret_state_offset());

  // Clear the earlyret state
  assert(JvmtiThreadState::earlyret_inactive == 0, "should be");
  __ sd(zr, cond_addr);

  __ remove_activation(state,
                       false, /* throw_monitor_exception */
                       false, /* install_monitor_exception */
                       true); /* notify_jvmdi */
  __ ret();

  return entry;
}
// end of ForceEarlyReturn support

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
  assert(t != nullptr && t->is_valid() && t->tos_in() == vtos, "illegal template");
  Label L;
  aep = __ pc();  __ push_ptr();  __ j(L);
  fep = __ pc();  __ push_f();    __ j(L);
  dep = __ pc();  __ push_d();    __ j(L);
  lep = __ pc();  __ push_l();    __ j(L);
  bep = cep = sep =
  iep = __ pc();  __ push_i();
  vep = __ pc();
  __ bind(L);
  generate_and_dispatch(t);
}

//-----------------------------------------------------------------------------

// Non-product code
#ifndef PRODUCT
address TemplateInterpreterGenerator::generate_trace_code(TosState state) {
  address entry = __ pc();

  __ push_reg(ra);
  __ push(state);
  __ push_reg(RegSet::range(x10, x17) + RegSet::range(x5, x7) + RegSet::range(x28, x31), sp);
  __ mv(c_rarg2, x10);  // Pass itos
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::trace_bytecode), c_rarg1, c_rarg2, c_rarg3);
  __ pop_reg(RegSet::range(x10, x17) + RegSet::range(x5, x7) + RegSet::range(x28, x31), sp);
  __ pop(state);
  __ pop_reg(ra);
  __ ret();                                   // return from result handler

  return entry;
}

void TemplateInterpreterGenerator::count_bytecode() {
  __ mv(x7, (address) &BytecodeCounter::_counter_value);
  __ atomic_addw(noreg, 1, x7);
}

void TemplateInterpreterGenerator::histogram_bytecode(Template* t) {
  __ mv(x7, (address) &BytecodeHistogram::_counters[t->bytecode()]);
  __ atomic_addw(noreg, 1, x7);
}

void TemplateInterpreterGenerator::histogram_bytecode_pair(Template* t) {
  // Calculate new index for counter:
  //   _index = (_index >> log2_number_of_codes) |
  //            (bytecode << log2_number_of_codes);
  Register index_addr = t1;
  Register index = t0;
  __ mv(index_addr, (address) &BytecodePairHistogram::_index);
  __ lw(index, index_addr);
  __ mv(x7, ((int)t->bytecode()) << BytecodePairHistogram::log2_number_of_codes);
  __ srli(index, index, BytecodePairHistogram::log2_number_of_codes);
  __ orrw(index, x7, index);
  __ sw(index, index_addr);
  // Bump bucket contents:
  //   _counters[_index] ++;
  Register counter_addr = t1;
  __ mv(x7, (address) &BytecodePairHistogram::_counters);
  __ shadd(counter_addr, index, x7, counter_addr, LogBytesPerInt);
  __ atomic_addw(noreg, 1, counter_addr);
 }

void TemplateInterpreterGenerator::trace_bytecode(Template* t) {
  // Call a little run-time stub to avoid blow-up for each bytecode.
  // The run-time runtime saves the right registers, depending on
  // the tosca in-state for the given template.

  assert(Interpreter::trace_code(t->tos_in()) != nullptr, "entry must have been generated");
  __ rt_call(Interpreter::trace_code(t->tos_in()));
  __ reinit_heapbase();
}

void TemplateInterpreterGenerator::stop_interpreter_at() {
  Label L;
  __ push_reg(t0);
  __ mv(t0, (address) &BytecodeCounter::_counter_value);
  __ ld(t0, Address(t0));
  __ mv(t1, StopInterpreterAt);
  __ bne(t0, t1, L);
  __ ebreak();
  __ bind(L);
  __ pop_reg(t0);
}

#endif // !PRODUCT
