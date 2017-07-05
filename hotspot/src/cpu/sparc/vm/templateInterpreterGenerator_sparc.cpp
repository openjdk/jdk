/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/macroAssembler.hpp"
#include "interpreter/bytecodeHistogram.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "interpreter/interp_masm.hpp"
#include "interpreter/templateInterpreterGenerator.hpp"
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

#ifndef FAST_DISPATCH
#define FAST_DISPATCH 1
#endif
#undef FAST_DISPATCH

// Size of interpreter code.  Increase if too small.  Interpreter will
// fail with a guarantee ("not enough space for interpreter generation");
// if too small.
// Run with +PrintInterpreter to get the VM to print out the size.
// Max size with JVMTI
#ifdef _LP64
  // The sethi() instruction generates lots more instructions when shell
  // stack limit is unlimited, so that's why this is much bigger.
int TemplateInterpreter::InterpreterCodeSize = 260 * K;
#else
int TemplateInterpreter::InterpreterCodeSize = 230 * K;
#endif

// Generation of Interpreter
//
// The TemplateInterpreterGenerator generates the interpreter into Interpreter::_code.


#define __ _masm->


//----------------------------------------------------------------------------------------------------

#ifndef _LP64
address TemplateInterpreterGenerator::generate_slow_signature_handler() {
  address entry = __ pc();
  Argument argv(0, true);

  // We are in the jni transition frame. Save the last_java_frame corresponding to the
  // outer interpreter frame
  //
  __ set_last_Java_frame(FP, noreg);
  // make sure the interpreter frame we've pushed has a valid return pc
  __ mov(O7, I7);
  __ mov(Lmethod, G3_scratch);
  __ mov(Llocals, G4_scratch);
  __ save_frame(0);
  __ mov(G2_thread, L7_thread_cache);
  __ add(argv.address_in_frame(), O3);
  __ mov(G2_thread, O0);
  __ mov(G3_scratch, O1);
  __ call(CAST_FROM_FN_PTR(address, InterpreterRuntime::slow_signature_handler), relocInfo::runtime_call_type);
  __ delayed()->mov(G4_scratch, O2);
  __ mov(L7_thread_cache, G2_thread);
  __ reset_last_Java_frame();

  // load the register arguments (the C code packed them as varargs)
  for (Argument ldarg = argv.successor(); ldarg.is_register(); ldarg = ldarg.successor()) {
      __ ld_ptr(ldarg.address_in_frame(), ldarg.as_register());
  }
  __ ret();
  __ delayed()->
     restore(O0, 0, Lscratch);  // caller's Lscratch gets the result handler
  return entry;
}


#else
// LP64 passes floating point arguments in F1, F3, F5, etc. instead of
// O0, O1, O2 etc..
// Doubles are passed in D0, D2, D4
// We store the signature of the first 16 arguments in the first argument
// slot because it will be overwritten prior to calling the native
// function, with the pointer to the JNIEnv.
// If LP64 there can be up to 16 floating point arguments in registers
// or 6 integer registers.
address TemplateInterpreterGenerator::generate_slow_signature_handler() {

  enum {
    non_float  = 0,
    float_sig  = 1,
    double_sig = 2,
    sig_mask   = 3
  };

  address entry = __ pc();
  Argument argv(0, true);

  // We are in the jni transition frame. Save the last_java_frame corresponding to the
  // outer interpreter frame
  //
  __ set_last_Java_frame(FP, noreg);
  // make sure the interpreter frame we've pushed has a valid return pc
  __ mov(O7, I7);
  __ mov(Lmethod, G3_scratch);
  __ mov(Llocals, G4_scratch);
  __ save_frame(0);
  __ mov(G2_thread, L7_thread_cache);
  __ add(argv.address_in_frame(), O3);
  __ mov(G2_thread, O0);
  __ mov(G3_scratch, O1);
  __ call(CAST_FROM_FN_PTR(address, InterpreterRuntime::slow_signature_handler), relocInfo::runtime_call_type);
  __ delayed()->mov(G4_scratch, O2);
  __ mov(L7_thread_cache, G2_thread);
  __ reset_last_Java_frame();


  // load the register arguments (the C code packed them as varargs)
  Address Sig = argv.address_in_frame();        // Argument 0 holds the signature
  __ ld_ptr( Sig, G3_scratch );                   // Get register argument signature word into G3_scratch
  __ mov( G3_scratch, G4_scratch);
  __ srl( G4_scratch, 2, G4_scratch);             // Skip Arg 0
  Label done;
  for (Argument ldarg = argv.successor(); ldarg.is_float_register(); ldarg = ldarg.successor()) {
    Label NonFloatArg;
    Label LoadFloatArg;
    Label LoadDoubleArg;
    Label NextArg;
    Address a = ldarg.address_in_frame();
    __ andcc(G4_scratch, sig_mask, G3_scratch);
    __ br(Assembler::zero, false, Assembler::pt, NonFloatArg);
    __ delayed()->nop();

    __ cmp(G3_scratch, float_sig );
    __ br(Assembler::equal, false, Assembler::pt, LoadFloatArg);
    __ delayed()->nop();

    __ cmp(G3_scratch, double_sig );
    __ br(Assembler::equal, false, Assembler::pt, LoadDoubleArg);
    __ delayed()->nop();

    __ bind(NonFloatArg);
    // There are only 6 integer register arguments!
    if ( ldarg.is_register() )
      __ ld_ptr(ldarg.address_in_frame(), ldarg.as_register());
    else {
    // Optimization, see if there are any more args and get out prior to checking
    // all 16 float registers.  My guess is that this is rare.
    // If is_register is false, then we are done the first six integer args.
      __ br_null_short(G4_scratch, Assembler::pt, done);
    }
    __ ba(NextArg);
    __ delayed()->srl( G4_scratch, 2, G4_scratch );

    __ bind(LoadFloatArg);
    __ ldf( FloatRegisterImpl::S, a, ldarg.as_float_register(), 4);
    __ ba(NextArg);
    __ delayed()->srl( G4_scratch, 2, G4_scratch );

    __ bind(LoadDoubleArg);
    __ ldf( FloatRegisterImpl::D, a, ldarg.as_double_register() );
    __ ba(NextArg);
    __ delayed()->srl( G4_scratch, 2, G4_scratch );

    __ bind(NextArg);

  }

  __ bind(done);
  __ ret();
  __ delayed()->
     restore(O0, 0, Lscratch);  // caller's Lscratch gets the result handler
  return entry;
}
#endif

void TemplateInterpreterGenerator::generate_counter_overflow(Label& Lcontinue) {

  // Generate code to initiate compilation on the counter overflow.

  // InterpreterRuntime::frequency_counter_overflow takes two arguments,
  // the first indicates if the counter overflow occurs at a backwards branch (NULL bcp)
  // and the second is only used when the first is true.  We pass zero for both.
  // The call returns the address of the verified entry point for the method or NULL
  // if the compilation did not complete (either went background or bailed out).
  __ set((int)false, O2);
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::frequency_counter_overflow), O2, O2, true);
  // returns verified_entry_point or NULL
  // we ignore it in any case
  __ ba_short(Lcontinue);

}


// End of helpers

// Various method entries

// Abstract method entry
// Attempt to execute abstract method. Throw exception
//
address TemplateInterpreterGenerator::generate_abstract_entry(void) {
  address entry = __ pc();
  // abstract method entry
  // throw exception
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_AbstractMethodError));
  // the call_VM checks for exception, so we should never return here.
  __ should_not_reach_here();
  return entry;

}

void TemplateInterpreterGenerator::save_native_result(void) {
  // result potentially in O0/O1: save it across calls
  const Address& l_tmp = InterpreterMacroAssembler::l_tmp;

  // result potentially in F0/F1: save it across calls
  const Address& d_tmp = InterpreterMacroAssembler::d_tmp;

  // save and restore any potential method result value around the unlocking operation
  __ stf(FloatRegisterImpl::D, F0, d_tmp);
#ifdef _LP64
  __ stx(O0, l_tmp);
#else
  __ std(O0, l_tmp);
#endif
}

void TemplateInterpreterGenerator::restore_native_result(void) {
  const Address& l_tmp = InterpreterMacroAssembler::l_tmp;
  const Address& d_tmp = InterpreterMacroAssembler::d_tmp;

  // Restore any method result value
  __ ldf(FloatRegisterImpl::D, d_tmp, F0);
#ifdef _LP64
  __ ldx(l_tmp, O0);
#else
  __ ldd(l_tmp, O0);
#endif
}

address TemplateInterpreterGenerator::generate_exception_handler_common(const char* name, const char* message, bool pass_oop) {
  assert(!pass_oop || message == NULL, "either oop or message but not both");
  address entry = __ pc();
  // expression stack must be empty before entering the VM if an exception happened
  __ empty_expression_stack();
  // load exception object
  __ set((intptr_t)name, G3_scratch);
  if (pass_oop) {
    __ call_VM(Oexception, CAST_FROM_FN_PTR(address, InterpreterRuntime::create_klass_exception), G3_scratch, Otos_i);
  } else {
    __ set((intptr_t)message, G4_scratch);
    __ call_VM(Oexception, CAST_FROM_FN_PTR(address, InterpreterRuntime::create_exception), G3_scratch, G4_scratch);
  }
  // throw exception
  assert(Interpreter::throw_exception_entry() != NULL, "generate it first");
  AddressLiteral thrower(Interpreter::throw_exception_entry());
  __ jump_to(thrower, G3_scratch);
  __ delayed()->nop();
  return entry;
}

address TemplateInterpreterGenerator::generate_ClassCastException_handler() {
  address entry = __ pc();
  // expression stack must be empty before entering the VM if an exception
  // happened
  __ empty_expression_stack();
  // load exception object
  __ call_VM(Oexception,
             CAST_FROM_FN_PTR(address,
                              InterpreterRuntime::throw_ClassCastException),
             Otos_i);
  __ should_not_reach_here();
  return entry;
}


address TemplateInterpreterGenerator::generate_ArrayIndexOutOfBounds_handler(const char* name) {
  address entry = __ pc();
  // expression stack must be empty before entering the VM if an exception happened
  __ empty_expression_stack();
  // convention: expect aberrant index in register G3_scratch, then shuffle the
  // index to G4_scratch for the VM call
  __ mov(G3_scratch, G4_scratch);
  __ set((intptr_t)name, G3_scratch);
  __ call_VM(Oexception, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_ArrayIndexOutOfBoundsException), G3_scratch, G4_scratch);
  __ should_not_reach_here();
  return entry;
}


address TemplateInterpreterGenerator::generate_StackOverflowError_handler() {
  address entry = __ pc();
  // expression stack must be empty before entering the VM if an exception happened
  __ empty_expression_stack();
  __ call_VM(Oexception, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_StackOverflowError));
  __ should_not_reach_here();
  return entry;
}


address TemplateInterpreterGenerator::generate_return_entry_for(TosState state, int step, size_t index_size) {
  address entry = __ pc();

  if (state == atos) {
    __ profile_return_type(O0, G3_scratch, G1_scratch);
  }

#if !defined(_LP64) && defined(COMPILER2)
  // All return values are where we want them, except for Longs.  C2 returns
  // longs in G1 in the 32-bit build whereas the interpreter wants them in O0/O1.
  // Since the interpreter will return longs in G1 and O0/O1 in the 32bit
  // build even if we are returning from interpreted we just do a little
  // stupid shuffing.
  // Note: I tried to make c2 return longs in O0/O1 and G1 so we wouldn't have to
  // do this here. Unfortunately if we did a rethrow we'd see an machepilog node
  // first which would move g1 -> O0/O1 and destroy the exception we were throwing.

  if (state == ltos) {
    __ srl (G1,  0, O1);
    __ srlx(G1, 32, O0);
  }
#endif // !_LP64 && COMPILER2

  // The callee returns with the stack possibly adjusted by adapter transition
  // We remove that possible adjustment here.
  // All interpreter local registers are untouched. Any result is passed back
  // in the O0/O1 or float registers. Before continuing, the arguments must be
  // popped from the java expression stack; i.e., Lesp must be adjusted.

  __ mov(Llast_SP, SP);   // Remove any adapter added stack space.

  const Register cache = G3_scratch;
  const Register index  = G1_scratch;
  __ get_cache_and_index_at_bcp(cache, index, 1, index_size);

  const Register flags = cache;
  __ ld_ptr(cache, ConstantPoolCache::base_offset() + ConstantPoolCacheEntry::flags_offset(), flags);
  const Register parameter_size = flags;
  __ and3(flags, ConstantPoolCacheEntry::parameter_size_mask, parameter_size);  // argument size in words
  __ sll(parameter_size, Interpreter::logStackElementSize, parameter_size);     // each argument size in bytes
  __ add(Lesp, parameter_size, Lesp);                                           // pop arguments
  __ dispatch_next(state, step);

  return entry;
}


address TemplateInterpreterGenerator::generate_deopt_entry_for(TosState state, int step) {
  address entry = __ pc();
  __ get_constant_pool_cache(LcpoolCache); // load LcpoolCache
#if INCLUDE_JVMCI
  // Check if we need to take lock at entry of synchronized method.  This can
  // only occur on method entry so emit it only for vtos with step 0.
  if (UseJVMCICompiler && state == vtos && step == 0) {
    Label L;
    Address pending_monitor_enter_addr(G2_thread, JavaThread::pending_monitorenter_offset());
    __ ldbool(pending_monitor_enter_addr, Gtemp);  // Load if pending monitor enter
    __ cmp_and_br_short(Gtemp, G0, Assembler::equal, Assembler::pn, L);
    // Clear flag.
    __ stbool(G0, pending_monitor_enter_addr);
    // Take lock.
    lock_method();
    __ bind(L);
  } else {
#ifdef ASSERT
    if (UseJVMCICompiler) {
      Label L;
      Address pending_monitor_enter_addr(G2_thread, JavaThread::pending_monitorenter_offset());
      __ ldbool(pending_monitor_enter_addr, Gtemp);  // Load if pending monitor enter
      __ cmp_and_br_short(Gtemp, G0, Assembler::equal, Assembler::pn, L);
      __ stop("unexpected pending monitor in deopt entry");
      __ bind(L);
    }
#endif
  }
#endif
  { Label L;
    Address exception_addr(G2_thread, Thread::pending_exception_offset());
    __ ld_ptr(exception_addr, Gtemp);  // Load pending exception.
    __ br_null_short(Gtemp, Assembler::pt, L);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_pending_exception));
    __ should_not_reach_here();
    __ bind(L);
  }
  __ dispatch_next(state, step);
  return entry;
}

// A result handler converts/unboxes a native call result into
// a java interpreter/compiler result. The current frame is an
// interpreter frame. The activation frame unwind code must be
// consistent with that of TemplateTable::_return(...). In the
// case of native methods, the caller's SP was not modified.
address TemplateInterpreterGenerator::generate_result_handler_for(BasicType type) {
  address entry = __ pc();
  Register Itos_i  = Otos_i ->after_save();
  Register Itos_l  = Otos_l ->after_save();
  Register Itos_l1 = Otos_l1->after_save();
  Register Itos_l2 = Otos_l2->after_save();
  switch (type) {
    case T_BOOLEAN: __ subcc(G0, O0, G0); __ addc(G0, 0, Itos_i); break; // !0 => true; 0 => false
    case T_CHAR   : __ sll(O0, 16, O0); __ srl(O0, 16, Itos_i);   break; // cannot use and3, 0xFFFF too big as immediate value!
    case T_BYTE   : __ sll(O0, 24, O0); __ sra(O0, 24, Itos_i);   break;
    case T_SHORT  : __ sll(O0, 16, O0); __ sra(O0, 16, Itos_i);   break;
    case T_LONG   :
#ifndef _LP64
                    __ mov(O1, Itos_l2);  // move other half of long
#endif              // ifdef or no ifdef, fall through to the T_INT case
    case T_INT    : __ mov(O0, Itos_i);                         break;
    case T_VOID   : /* nothing to do */                         break;
    case T_FLOAT  : assert(F0 == Ftos_f, "fix this code" );     break;
    case T_DOUBLE : assert(F0 == Ftos_d, "fix this code" );     break;
    case T_OBJECT :
      __ ld_ptr(FP, (frame::interpreter_frame_oop_temp_offset*wordSize) + STACK_BIAS, Itos_i);
      __ verify_oop(Itos_i);
      break;
    default       : ShouldNotReachHere();
  }
  __ ret();                           // return from interpreter activation
  __ delayed()->restore(I5_savedSP, G0, SP);  // remove interpreter frame
  NOT_PRODUCT(__ emit_int32(0);)       // marker for disassembly
  return entry;
}

address TemplateInterpreterGenerator::generate_safept_entry_for(TosState state, address runtime_entry) {
  address entry = __ pc();
  __ push(state);
  __ call_VM(noreg, runtime_entry);
  __ dispatch_via(vtos, Interpreter::normal_table(vtos));
  return entry;
}


address TemplateInterpreterGenerator::generate_continuation_for(TosState state) {
  address entry = __ pc();
  __ dispatch_next(state);
  return entry;
}

//
// Helpers for commoning out cases in the various type of method entries.
//

// increment invocation count & check for overflow
//
// Note: checking for negative value instead of overflow
//       so we have a 'sticky' overflow test
//
// Lmethod: method
// ??: invocation counter
//
void TemplateInterpreterGenerator::generate_counter_incr(Label* overflow, Label* profile_method, Label* profile_method_continue) {
  // Note: In tiered we increment either counters in MethodCounters* or in
  // MDO depending if we're profiling or not.
  const Register G3_method_counters = G3_scratch;
  Label done;

  if (TieredCompilation) {
    const int increment = InvocationCounter::count_increment;
    Label no_mdo;
    if (ProfileInterpreter) {
      // If no method data exists, go to profile_continue.
      __ ld_ptr(Lmethod, Method::method_data_offset(), G4_scratch);
      __ br_null_short(G4_scratch, Assembler::pn, no_mdo);
      // Increment counter
      Address mdo_invocation_counter(G4_scratch,
                                     in_bytes(MethodData::invocation_counter_offset()) +
                                     in_bytes(InvocationCounter::counter_offset()));
      Address mask(G4_scratch, in_bytes(MethodData::invoke_mask_offset()));
      __ increment_mask_and_jump(mdo_invocation_counter, increment, mask,
                                 G3_scratch, Lscratch,
                                 Assembler::zero, overflow);
      __ ba_short(done);
    }

    // Increment counter in MethodCounters*
    __ bind(no_mdo);
    Address invocation_counter(G3_method_counters,
            in_bytes(MethodCounters::invocation_counter_offset()) +
            in_bytes(InvocationCounter::counter_offset()));
    __ get_method_counters(Lmethod, G3_method_counters, done);
    Address mask(G3_method_counters, in_bytes(MethodCounters::invoke_mask_offset()));
    __ increment_mask_and_jump(invocation_counter, increment, mask,
                               G4_scratch, Lscratch,
                               Assembler::zero, overflow);
    __ bind(done);
  } else { // not TieredCompilation
    // Update standard invocation counters
    __ get_method_counters(Lmethod, G3_method_counters, done);
    __ increment_invocation_counter(G3_method_counters, O0, G4_scratch);
    if (ProfileInterpreter) {
      Address interpreter_invocation_counter(G3_method_counters,
            in_bytes(MethodCounters::interpreter_invocation_counter_offset()));
      __ ld(interpreter_invocation_counter, G4_scratch);
      __ inc(G4_scratch);
      __ st(G4_scratch, interpreter_invocation_counter);
    }

    if (ProfileInterpreter && profile_method != NULL) {
      // Test to see if we should create a method data oop
      Address profile_limit(G3_method_counters, in_bytes(MethodCounters::interpreter_profile_limit_offset()));
      __ ld(profile_limit, G1_scratch);
      __ cmp_and_br_short(O0, G1_scratch, Assembler::lessUnsigned, Assembler::pn, *profile_method_continue);

      // if no method data exists, go to profile_method
      __ test_method_data_pointer(*profile_method);
    }

    Address invocation_limit(G3_method_counters, in_bytes(MethodCounters::interpreter_invocation_limit_offset()));
    __ ld(invocation_limit, G3_scratch);
    __ cmp(O0, G3_scratch);
    __ br(Assembler::greaterEqualUnsigned, false, Assembler::pn, *overflow); // Far distance
    __ delayed()->nop();
    __ bind(done);
  }

}

// Allocate monitor and lock method (asm interpreter)
// ebx - Method*
//
void TemplateInterpreterGenerator::lock_method() {
  __ ld(Lmethod, in_bytes(Method::access_flags_offset()), O0);  // Load access flags.

#ifdef ASSERT
 { Label ok;
   __ btst(JVM_ACC_SYNCHRONIZED, O0);
   __ br( Assembler::notZero, false, Assembler::pt, ok);
   __ delayed()->nop();
   __ stop("method doesn't need synchronization");
   __ bind(ok);
  }
#endif // ASSERT

  // get synchronization object to O0
  { Label done;
    __ btst(JVM_ACC_STATIC, O0);
    __ br( Assembler::zero, true, Assembler::pt, done);
    __ delayed()->ld_ptr(Llocals, Interpreter::local_offset_in_bytes(0), O0); // get receiver for not-static case

    // lock the mirror, not the Klass*
    __ load_mirror(O0, Lmethod);

#ifdef ASSERT
    __ tst(O0);
    __ breakpoint_trap(Assembler::zero, Assembler::ptr_cc);
#endif // ASSERT

    __ bind(done);
  }

  __ add_monitor_to_stack(true, noreg, noreg);  // allocate monitor elem
  __ st_ptr( O0, Lmonitors, BasicObjectLock::obj_offset_in_bytes());   // store object
  // __ untested("lock_object from method entry");
  __ lock_object(Lmonitors, O0);
}

// See if we've got enough room on the stack for locals plus overhead below
// JavaThread::stack_overflow_limit(). If not, throw a StackOverflowError
// without going through the signal handler, i.e., reserved and yellow zones
// will not be made usable. The shadow zone must suffice to handle the
// overflow.
void TemplateInterpreterGenerator::generate_stack_overflow_check(Register Rframe_size,
                                                                 Register Rscratch) {
  const int page_size = os::vm_page_size();
  Label after_frame_check;

  assert_different_registers(Rframe_size, Rscratch);

  __ set(page_size, Rscratch);
  __ cmp_and_br_short(Rframe_size, Rscratch, Assembler::lessEqual, Assembler::pt, after_frame_check);

  // Get the stack overflow limit, and in debug, verify it is non-zero.
  __ ld_ptr(G2_thread, JavaThread::stack_overflow_limit_offset(), Rscratch);
#ifdef ASSERT
  Label limit_ok;
  __ br_notnull_short(Rscratch, Assembler::pn, limit_ok);
  __ stop("stack overflow limit is zero in generate_stack_overflow_check");
  __ bind(limit_ok);
#endif

  // Add in the size of the frame (which is the same as subtracting it from the
  // SP, which would take another register.
  __ add(Rscratch, Rframe_size, Rscratch);

  // The frame is greater than one page in size, so check against
  // the bottom of the stack.
  __ cmp_and_brx_short(SP, Rscratch, Assembler::greaterUnsigned, Assembler::pt, after_frame_check);

  // The stack will overflow, throw an exception.

  // Note that SP is restored to sender's sp (in the delay slot). This
  // is necessary if the sender's frame is an extended compiled frame
  // (see gen_c2i_adapter()) and safer anyway in case of JSR292
  // adaptations.

  // Note also that the restored frame is not necessarily interpreted.
  // Use the shared runtime version of the StackOverflowError.
  assert(StubRoutines::throw_StackOverflowError_entry() != NULL, "stub not yet generated");
  AddressLiteral stub(StubRoutines::throw_StackOverflowError_entry());
  __ jump_to(stub, Rscratch);
  __ delayed()->mov(O5_savedSP, SP);

  // If you get to here, then there is enough stack space.
  __ bind(after_frame_check);
}


//
// Generate a fixed interpreter frame. This is identical setup for interpreted
// methods and for native methods hence the shared code.


//----------------------------------------------------------------------------------------------------
// Stack frame layout
//
// When control flow reaches any of the entry types for the interpreter
// the following holds ->
//
// C2 Calling Conventions:
//
// The entry code below assumes that the following registers are set
// when coming in:
//    G5_method: holds the Method* of the method to call
//    Lesp:    points to the TOS of the callers expression stack
//             after having pushed all the parameters
//
// The entry code does the following to setup an interpreter frame
//   pop parameters from the callers stack by adjusting Lesp
//   set O0 to Lesp
//   compute X = (max_locals - num_parameters)
//   bump SP up by X to accomadate the extra locals
//   compute X = max_expression_stack
//               + vm_local_words
//               + 16 words of register save area
//   save frame doing a save sp, -X, sp growing towards lower addresses
//   set Lbcp, Lmethod, LcpoolCache
//   set Llocals to i0
//   set Lmonitors to FP - rounded_vm_local_words
//   set Lesp to Lmonitors - 4
//
//  The frame has now been setup to do the rest of the entry code

// Try this optimization:  Most method entries could live in a
// "one size fits all" stack frame without all the dynamic size
// calculations.  It might be profitable to do all this calculation
// statically and approximately for "small enough" methods.

//-----------------------------------------------------------------------------------------------

// C1 Calling conventions
//
// Upon method entry, the following registers are setup:
//
// g2 G2_thread: current thread
// g5 G5_method: method to activate
// g4 Gargs  : pointer to last argument
//
//
// Stack:
//
// +---------------+ <--- sp
// |               |
// : reg save area :
// |               |
// +---------------+ <--- sp + 0x40
// |               |
// : extra 7 slots :      note: these slots are not really needed for the interpreter (fix later)
// |               |
// +---------------+ <--- sp + 0x5c
// |               |
// :     free      :
// |               |
// +---------------+ <--- Gargs
// |               |
// :   arguments   :
// |               |
// +---------------+
// |               |
//
//
//
// AFTER FRAME HAS BEEN SETUP for method interpretation the stack looks like:
//
// +---------------+ <--- sp
// |               |
// : reg save area :
// |               |
// +---------------+ <--- sp + 0x40
// |               |
// : extra 7 slots :      note: these slots are not really needed for the interpreter (fix later)
// |               |
// +---------------+ <--- sp + 0x5c
// |               |
// :               :
// |               | <--- Lesp
// +---------------+ <--- Lmonitors (fp - 0x18)
// |   VM locals   |
// +---------------+ <--- fp
// |               |
// : reg save area :
// |               |
// +---------------+ <--- fp + 0x40
// |               |
// : extra 7 slots :      note: these slots are not really needed for the interpreter (fix later)
// |               |
// +---------------+ <--- fp + 0x5c
// |               |
// :     free      :
// |               |
// +---------------+
// |               |
// : nonarg locals :
// |               |
// +---------------+
// |               |
// :   arguments   :
// |               | <--- Llocals
// +---------------+ <--- Gargs
// |               |

void TemplateInterpreterGenerator::generate_fixed_frame(bool native_call) {
  //
  //
  // The entry code sets up a new interpreter frame in 4 steps:
  //
  // 1) Increase caller's SP by for the extra local space needed:
  //    (check for overflow)
  //    Efficient implementation of xload/xstore bytecodes requires
  //    that arguments and non-argument locals are in a contigously
  //    addressable memory block => non-argument locals must be
  //    allocated in the caller's frame.
  //
  // 2) Create a new stack frame and register window:
  //    The new stack frame must provide space for the standard
  //    register save area, the maximum java expression stack size,
  //    the monitor slots (0 slots initially), and some frame local
  //    scratch locations.
  //
  // 3) The following interpreter activation registers must be setup:
  //    Lesp       : expression stack pointer
  //    Lbcp       : bytecode pointer
  //    Lmethod    : method
  //    Llocals    : locals pointer
  //    Lmonitors  : monitor pointer
  //    LcpoolCache: constant pool cache
  //
  // 4) Initialize the non-argument locals if necessary:
  //    Non-argument locals may need to be initialized to NULL
  //    for GC to work. If the oop-map information is accurate
  //    (in the absence of the JSR problem), no initialization
  //    is necessary.
  //
  // (gri - 2/25/2000)


  int rounded_vm_local_words = round_to( frame::interpreter_frame_vm_local_words, WordsPerLong );

  const int extra_space =
    rounded_vm_local_words +                   // frame local scratch space
    Method::extra_stack_entries() +            // extra stack for jsr 292
    frame::memory_parameter_word_sp_offset +   // register save area
    (native_call ? frame::interpreter_frame_extra_outgoing_argument_words : 0);

  const Register Glocals_size = G3;
  const Register RconstMethod = Glocals_size;
  const Register Otmp1 = O3;
  const Register Otmp2 = O4;
  // Lscratch can't be used as a temporary because the call_stub uses
  // it to assert that the stack frame was setup correctly.
  const Address constMethod       (G5_method, Method::const_offset());
  const Address size_of_parameters(RconstMethod, ConstMethod::size_of_parameters_offset());

  __ ld_ptr( constMethod, RconstMethod );
  __ lduh( size_of_parameters, Glocals_size);

  // Gargs points to first local + BytesPerWord
  // Set the saved SP after the register window save
  //
  assert_different_registers(Gargs, Glocals_size, Gframe_size, O5_savedSP);
  __ sll(Glocals_size, Interpreter::logStackElementSize, Otmp1);
  __ add(Gargs, Otmp1, Gargs);

  if (native_call) {
    __ calc_mem_param_words( Glocals_size, Gframe_size );
    __ add( Gframe_size,  extra_space, Gframe_size);
    __ round_to( Gframe_size, WordsPerLong );
    __ sll( Gframe_size, LogBytesPerWord, Gframe_size );

    // Native calls don't need the stack size check since they have no
    // expression stack and the arguments are already on the stack and
    // we only add a handful of words to the stack.
  } else {

    //
    // Compute number of locals in method apart from incoming parameters
    //
    const Address size_of_locals(Otmp1, ConstMethod::size_of_locals_offset());
    __ ld_ptr(constMethod, Otmp1);
    __ lduh(size_of_locals, Otmp1);
    __ sub(Otmp1, Glocals_size, Glocals_size);
    __ round_to(Glocals_size, WordsPerLong);
    __ sll(Glocals_size, Interpreter::logStackElementSize, Glocals_size);

    // See if the frame is greater than one page in size. If so,
    // then we need to verify there is enough stack space remaining.
    // Frame_size = (max_stack + extra_space) * BytesPerWord;
    __ ld_ptr(constMethod, Gframe_size);
    __ lduh(Gframe_size, in_bytes(ConstMethod::max_stack_offset()), Gframe_size);
    __ add(Gframe_size, extra_space, Gframe_size);
    __ round_to(Gframe_size, WordsPerLong);
    __ sll(Gframe_size, Interpreter::logStackElementSize, Gframe_size);

    // Add in java locals size for stack overflow check only
    __ add(Gframe_size, Glocals_size, Gframe_size);

    const Register Otmp2 = O4;
    assert_different_registers(Otmp1, Otmp2, O5_savedSP);
    generate_stack_overflow_check(Gframe_size, Otmp1);

    __ sub(Gframe_size, Glocals_size, Gframe_size);

    //
    // bump SP to accomodate the extra locals
    //
    __ sub(SP, Glocals_size, SP);
  }

  //
  // now set up a stack frame with the size computed above
  //
  __ neg( Gframe_size );
  __ save( SP, Gframe_size, SP );

  //
  // now set up all the local cache registers
  //
  // NOTE: At this point, Lbyte_code/Lscratch has been modified. Note
  // that all present references to Lbyte_code initialize the register
  // immediately before use
  if (native_call) {
    __ mov(G0, Lbcp);
  } else {
    __ ld_ptr(G5_method, Method::const_offset(), Lbcp);
    __ add(Lbcp, in_bytes(ConstMethod::codes_offset()), Lbcp);
  }
  __ mov( G5_method, Lmethod);                 // set Lmethod
  // Get mirror and store it in the frame as GC root for this Method*
  Register mirror = LcpoolCache;
  __ load_mirror(mirror, Lmethod);
  __ st_ptr(mirror, FP, (frame::interpreter_frame_mirror_offset * wordSize) + STACK_BIAS);
  __ get_constant_pool_cache( LcpoolCache );   // set LcpoolCache
  __ sub(FP, rounded_vm_local_words * BytesPerWord, Lmonitors ); // set Lmonitors
#ifdef _LP64
  __ add( Lmonitors, STACK_BIAS, Lmonitors );   // Account for 64 bit stack bias
#endif
  __ sub(Lmonitors, BytesPerWord, Lesp);       // set Lesp

  // setup interpreter activation registers
  __ sub(Gargs, BytesPerWord, Llocals);        // set Llocals

  if (ProfileInterpreter) {
#ifdef FAST_DISPATCH
    // FAST_DISPATCH and ProfileInterpreter are mutually exclusive since
    // they both use I2.
    assert(0, "FAST_DISPATCH and +ProfileInterpreter are mutually exclusive");
#endif // FAST_DISPATCH
    __ set_method_data_pointer();
  }

}

// Method entry for java.lang.ref.Reference.get.
address TemplateInterpreterGenerator::generate_Reference_get_entry(void) {
#if INCLUDE_ALL_GCS
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

  address entry = __ pc();

  const int referent_offset = java_lang_ref_Reference::referent_offset;
  guarantee(referent_offset > 0, "referent offset not initialized");

  if (UseG1GC) {
     Label slow_path;

    // In the G1 code we don't check if we need to reach a safepoint. We
    // continue and the thread will safepoint at the next bytecode dispatch.

    // Check if local 0 != NULL
    // If the receiver is null then it is OK to jump to the slow path.
    __ ld_ptr(Gargs, G0, Otos_i ); // get local 0
    // check if local 0 == NULL and go the slow path
    __ cmp_and_brx_short(Otos_i, 0, Assembler::equal, Assembler::pn, slow_path);


    // Load the value of the referent field.
    if (Assembler::is_simm13(referent_offset)) {
      __ load_heap_oop(Otos_i, referent_offset, Otos_i);
    } else {
      __ set(referent_offset, G3_scratch);
      __ load_heap_oop(Otos_i, G3_scratch, Otos_i);
    }

    // Generate the G1 pre-barrier code to log the value of
    // the referent field in an SATB buffer. Note with
    // these parameters the pre-barrier does not generate
    // the load of the previous value

    __ g1_write_barrier_pre(noreg /* obj */, noreg /* index */, 0 /* offset */,
                            Otos_i /* pre_val */,
                            G3_scratch /* tmp */,
                            true /* preserve_o_regs */);

    // _areturn
    __ retl();                      // return from leaf routine
    __ delayed()->mov(O5_savedSP, SP);

    // Generate regular method entry
    __ bind(slow_path);
    __ jump_to_entry(Interpreter::entry_for_kind(Interpreter::zerolocals));
    return entry;
  }
#endif // INCLUDE_ALL_GCS

  // If G1 is not enabled then attempt to go through the accessor entry point
  // Reference.get is an accessor
  return NULL;
}

/**
 * Method entry for static native methods:
 *   int java.util.zip.CRC32.update(int crc, int b)
 */
address TemplateInterpreterGenerator::generate_CRC32_update_entry() {

  if (UseCRC32Intrinsics) {
    address entry = __ pc();

    Label L_slow_path;
    // If we need a safepoint check, generate full interpreter entry.
    ExternalAddress state(SafepointSynchronize::address_of_state());
    __ set(ExternalAddress(SafepointSynchronize::address_of_state()), O2);
    __ set(SafepointSynchronize::_not_synchronized, O3);
    __ cmp_and_br_short(O2, O3, Assembler::notEqual, Assembler::pt, L_slow_path);

    // Load parameters
    const Register crc   = O0; // initial crc
    const Register val   = O1; // byte to update with
    const Register table = O2; // address of 256-entry lookup table

    __ ldub(Gargs, 3, val);
    __ lduw(Gargs, 8, crc);

    __ set(ExternalAddress(StubRoutines::crc_table_addr()), table);

    __ not1(crc); // ~crc
    __ clruwu(crc);
    __ update_byte_crc32(crc, val, table);
    __ not1(crc); // ~crc

    // result in O0
    __ retl();
    __ delayed()->nop();

    // generate a vanilla native entry as the slow path
    __ bind(L_slow_path);
    __ jump_to_entry(Interpreter::entry_for_kind(Interpreter::native));
    return entry;
  }
  return NULL;
}

/**
 * Method entry for static native methods:
 *   int java.util.zip.CRC32.updateBytes(int crc, byte[] b, int off, int len)
 *   int java.util.zip.CRC32.updateByteBuffer(int crc, long buf, int off, int len)
 */
address TemplateInterpreterGenerator::generate_CRC32_updateBytes_entry(AbstractInterpreter::MethodKind kind) {

  if (UseCRC32Intrinsics) {
    address entry = __ pc();

    Label L_slow_path;
    // If we need a safepoint check, generate full interpreter entry.
    ExternalAddress state(SafepointSynchronize::address_of_state());
    __ set(ExternalAddress(SafepointSynchronize::address_of_state()), O2);
    __ set(SafepointSynchronize::_not_synchronized, O3);
    __ cmp_and_br_short(O2, O3, Assembler::notEqual, Assembler::pt, L_slow_path);

    // Load parameters from the stack
    const Register crc    = O0; // initial crc
    const Register buf    = O1; // source java byte array address
    const Register len    = O2; // len
    const Register offset = O3; // offset

    // Arguments are reversed on java expression stack
    // Calculate address of start element
    if (kind == Interpreter::java_util_zip_CRC32_updateByteBuffer) {
      __ lduw(Gargs, 0,  len);
      __ lduw(Gargs, 8,  offset);
      __ ldx( Gargs, 16, buf);
      __ lduw(Gargs, 32, crc);
      __ add(buf, offset, buf);
    } else {
      __ lduw(Gargs, 0,  len);
      __ lduw(Gargs, 8,  offset);
      __ ldx( Gargs, 16, buf);
      __ lduw(Gargs, 24, crc);
      __ add(buf, arrayOopDesc::base_offset_in_bytes(T_BYTE), buf); // account for the header size
      __ add(buf ,offset, buf);
    }

    // Call the crc32 kernel
    __ MacroAssembler::save_thread(L7_thread_cache);
    __ kernel_crc32(crc, buf, len, O3);
    __ MacroAssembler::restore_thread(L7_thread_cache);

    // result in O0
    __ retl();
    __ delayed()->nop();

    // generate a vanilla native entry as the slow path
    __ bind(L_slow_path);
    __ jump_to_entry(Interpreter::entry_for_kind(Interpreter::native));
    return entry;
  }
  return NULL;
}

/**
 * Method entry for intrinsic-candidate (non-native) methods:
 *   int java.util.zip.CRC32C.updateBytes(int crc, byte[] b, int off, int end)
 *   int java.util.zip.CRC32C.updateDirectByteBuffer(int crc, long buf, int off, int end)
 * Unlike CRC32, CRC32C does not have any methods marked as native
 * CRC32C also uses an "end" variable instead of the length variable CRC32 uses
 */
address TemplateInterpreterGenerator::generate_CRC32C_updateBytes_entry(AbstractInterpreter::MethodKind kind) {

  if (UseCRC32CIntrinsics) {
    address entry = __ pc();

    // Load parameters from the stack
    const Register crc    = O0; // initial crc
    const Register buf    = O1; // source java byte array address
    const Register offset = O2; // offset
    const Register end    = O3; // index of last element to process
    const Register len    = O2; // len argument to the kernel
    const Register table  = O3; // crc32c lookup table address

    // Arguments are reversed on java expression stack
    // Calculate address of start element
    if (kind == Interpreter::java_util_zip_CRC32C_updateDirectByteBuffer) {
      __ lduw(Gargs, 0,  end);
      __ lduw(Gargs, 8,  offset);
      __ ldx( Gargs, 16, buf);
      __ lduw(Gargs, 32, crc);
      __ add(buf, offset, buf);
      __ sub(end, offset, len);
    } else {
      __ lduw(Gargs, 0,  end);
      __ lduw(Gargs, 8,  offset);
      __ ldx( Gargs, 16, buf);
      __ lduw(Gargs, 24, crc);
      __ add(buf, arrayOopDesc::base_offset_in_bytes(T_BYTE), buf); // account for the header size
      __ add(buf, offset, buf);
      __ sub(end, offset, len);
    }

    // Call the crc32c kernel
    __ MacroAssembler::save_thread(L7_thread_cache);
    __ kernel_crc32c(crc, buf, len, table);
    __ MacroAssembler::restore_thread(L7_thread_cache);

    // result in O0
    __ retl();
    __ delayed()->nop();

    return entry;
  }
  return NULL;
}

// Not supported
address TemplateInterpreterGenerator::generate_math_entry(AbstractInterpreter::MethodKind kind) {
  return NULL;
}

// TODO: rather than touching all pages, check against stack_overflow_limit and bang yellow page to
// generate exception
void TemplateInterpreterGenerator::bang_stack_shadow_pages(bool native_call) {
  // Quick & dirty stack overflow checking: bang the stack & handle trap.
  // Note that we do the banging after the frame is setup, since the exception
  // handling code expects to find a valid interpreter frame on the stack.
  // Doing the banging earlier fails if the caller frame is not an interpreter
  // frame.
  // (Also, the exception throwing code expects to unlock any synchronized
  // method receiever, so do the banging after locking the receiver.)

  // Bang each page in the shadow zone. We can't assume it's been done for
  // an interpreter frame with greater than a page of locals, so each page
  // needs to be checked.  Only true for non-native.
  if (UseStackBanging) {
    const int page_size = os::vm_page_size();
    const int n_shadow_pages = ((int)JavaThread::stack_shadow_zone_size()) / page_size;
    const int start_page = native_call ? n_shadow_pages : 1;
    for (int pages = start_page; pages <= n_shadow_pages; pages++) {
      __ bang_stack_with_offset(pages*page_size);
    }
  }
}

//
// Interpreter stub for calling a native method. (asm interpreter)
// This sets up a somewhat different looking stack for calling the native method
// than the typical interpreter frame setup.
//

address TemplateInterpreterGenerator::generate_native_entry(bool synchronized) {
  address entry = __ pc();

  // the following temporary registers are used during frame creation
  const Register Gtmp1 = G3_scratch ;
  const Register Gtmp2 = G1_scratch;
  bool inc_counter  = UseCompiler || CountCompiledCalls || LogTouchedMethods;

  // make sure registers are different!
  assert_different_registers(G2_thread, G5_method, Gargs, Gtmp1, Gtmp2);

  const Address Laccess_flags(Lmethod, Method::access_flags_offset());

  const Register Glocals_size = G3;
  assert_different_registers(Glocals_size, G4_scratch, Gframe_size);

  // make sure method is native & not abstract
  // rethink these assertions - they can be simplified and shared (gri 2/25/2000)
#ifdef ASSERT
  __ ld(G5_method, Method::access_flags_offset(), Gtmp1);
  {
    Label L;
    __ btst(JVM_ACC_NATIVE, Gtmp1);
    __ br(Assembler::notZero, false, Assembler::pt, L);
    __ delayed()->nop();
    __ stop("tried to execute non-native method as native");
    __ bind(L);
  }
  { Label L;
    __ btst(JVM_ACC_ABSTRACT, Gtmp1);
    __ br(Assembler::zero, false, Assembler::pt, L);
    __ delayed()->nop();
    __ stop("tried to execute abstract method as non-abstract");
    __ bind(L);
  }
#endif // ASSERT

 // generate the code to allocate the interpreter stack frame
  generate_fixed_frame(true);

  //
  // No locals to initialize for native method
  //

  // this slot will be set later, we initialize it to null here just in
  // case we get a GC before the actual value is stored later
  __ st_ptr(G0, FP, (frame::interpreter_frame_oop_temp_offset * wordSize) + STACK_BIAS);

  const Address do_not_unlock_if_synchronized(G2_thread,
    JavaThread::do_not_unlock_if_synchronized_offset());
  // Since at this point in the method invocation the exception handler
  // would try to exit the monitor of synchronized methods which hasn't
  // been entered yet, we set the thread local variable
  // _do_not_unlock_if_synchronized to true. If any exception was thrown by
  // runtime, exception handling i.e. unlock_if_synchronized_method will
  // check this thread local flag.
  // This flag has two effects, one is to force an unwind in the topmost
  // interpreter frame and not perform an unlock while doing so.

  __ movbool(true, G3_scratch);
  __ stbool(G3_scratch, do_not_unlock_if_synchronized);

  // increment invocation counter and check for overflow
  //
  // Note: checking for negative value instead of overflow
  //       so we have a 'sticky' overflow test (may be of
  //       importance as soon as we have true MT/MP)
  Label invocation_counter_overflow;
  Label Lcontinue;
  if (inc_counter) {
    generate_counter_incr(&invocation_counter_overflow, NULL, NULL);

  }
  __ bind(Lcontinue);

  bang_stack_shadow_pages(true);

  // reset the _do_not_unlock_if_synchronized flag
  __ stbool(G0, do_not_unlock_if_synchronized);

  // check for synchronized methods
  // Must happen AFTER invocation_counter check and stack overflow check,
  // so method is not locked if overflows.

  if (synchronized) {
    lock_method();
  } else {
#ifdef ASSERT
    { Label ok;
      __ ld(Laccess_flags, O0);
      __ btst(JVM_ACC_SYNCHRONIZED, O0);
      __ br( Assembler::zero, false, Assembler::pt, ok);
      __ delayed()->nop();
      __ stop("method needs synchronization");
      __ bind(ok);
    }
#endif // ASSERT
  }


  // start execution
  __ verify_thread();

  // JVMTI support
  __ notify_method_entry();

  // native call

  // (note that O0 is never an oop--at most it is a handle)
  // It is important not to smash any handles created by this call,
  // until any oop handle in O0 is dereferenced.

  // (note that the space for outgoing params is preallocated)

  // get signature handler
  { Label L;
    Address signature_handler(Lmethod, Method::signature_handler_offset());
    __ ld_ptr(signature_handler, G3_scratch);
    __ br_notnull_short(G3_scratch, Assembler::pt, L);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::prepare_native_call), Lmethod);
    __ ld_ptr(signature_handler, G3_scratch);
    __ bind(L);
  }

  // Push a new frame so that the args will really be stored in
  // Copy a few locals across so the new frame has the variables
  // we need but these values will be dead at the jni call and
  // therefore not gc volatile like the values in the current
  // frame (Lmethod in particular)

  // Flush the method pointer to the register save area
  __ st_ptr(Lmethod, SP, (Lmethod->sp_offset_in_saved_window() * wordSize) + STACK_BIAS);
  __ mov(Llocals, O1);

  // calculate where the mirror handle body is allocated in the interpreter frame:
  __ add(FP, (frame::interpreter_frame_oop_temp_offset * wordSize) + STACK_BIAS, O2);

  // Calculate current frame size
  __ sub(SP, FP, O3);         // Calculate negative of current frame size
  __ save(SP, O3, SP);        // Allocate an identical sized frame

  // Note I7 has leftover trash. Slow signature handler will fill it in
  // should we get there. Normal jni call will set reasonable last_Java_pc
  // below (and fix I7 so the stack trace doesn't have a meaningless frame
  // in it).

  // Load interpreter frame's Lmethod into same register here

  __ ld_ptr(FP, (Lmethod->sp_offset_in_saved_window() * wordSize) + STACK_BIAS, Lmethod);

  __ mov(I1, Llocals);
  __ mov(I2, Lscratch2);     // save the address of the mirror


  // ONLY Lmethod and Llocals are valid here!

  // call signature handler, It will move the arg properly since Llocals in current frame
  // matches that in outer frame

  __ callr(G3_scratch, 0);
  __ delayed()->nop();

  // Result handler is in Lscratch

  // Reload interpreter frame's Lmethod since slow signature handler may block
  __ ld_ptr(FP, (Lmethod->sp_offset_in_saved_window() * wordSize) + STACK_BIAS, Lmethod);

  { Label not_static;

    __ ld(Laccess_flags, O0);
    __ btst(JVM_ACC_STATIC, O0);
    __ br( Assembler::zero, false, Assembler::pt, not_static);
    // get native function entry point(O0 is a good temp until the very end)
    __ delayed()->ld_ptr(Lmethod, in_bytes(Method::native_function_offset()), O0);
    // for static methods insert the mirror argument
    __ load_mirror(O1, Lmethod);
#ifdef ASSERT
    if (!PrintSignatureHandlers)  // do not dirty the output with this
    { Label L;
      __ br_notnull_short(O1, Assembler::pt, L);
      __ stop("mirror is missing");
      __ bind(L);
    }
#endif // ASSERT
    __ st_ptr(O1, Lscratch2, 0);
    __ mov(Lscratch2, O1);
    __ bind(not_static);
  }

  // At this point, arguments have been copied off of stack into
  // their JNI positions, which are O1..O5 and SP[68..].
  // Oops are boxed in-place on the stack, with handles copied to arguments.
  // The result handler is in Lscratch.  O0 will shortly hold the JNIEnv*.

#ifdef ASSERT
  { Label L;
    __ br_notnull_short(O0, Assembler::pt, L);
    __ stop("native entry point is missing");
    __ bind(L);
  }
#endif // ASSERT

  //
  // setup the frame anchor
  //
  // The scavenge function only needs to know that the PC of this frame is
  // in the interpreter method entry code, it doesn't need to know the exact
  // PC and hence we can use O7 which points to the return address from the
  // previous call in the code stream (signature handler function)
  //
  // The other trick is we set last_Java_sp to FP instead of the usual SP because
  // we have pushed the extra frame in order to protect the volatile register(s)
  // in that frame when we return from the jni call
  //

  __ set_last_Java_frame(FP, O7);
  __ mov(O7, I7);  // make dummy interpreter frame look like one above,
                   // not meaningless information that'll confuse me.

  // flush the windows now. We don't care about the current (protection) frame
  // only the outer frames

  __ flushw();

  // mark windows as flushed
  Address flags(G2_thread, JavaThread::frame_anchor_offset() + JavaFrameAnchor::flags_offset());
  __ set(JavaFrameAnchor::flushed, G3_scratch);
  __ st(G3_scratch, flags);

  // Transition from _thread_in_Java to _thread_in_native. We are already safepoint ready.

  Address thread_state(G2_thread, JavaThread::thread_state_offset());
#ifdef ASSERT
  { Label L;
    __ ld(thread_state, G3_scratch);
    __ cmp_and_br_short(G3_scratch, _thread_in_Java, Assembler::equal, Assembler::pt, L);
    __ stop("Wrong thread state in native stub");
    __ bind(L);
  }
#endif // ASSERT
  __ set(_thread_in_native, G3_scratch);
  __ st(G3_scratch, thread_state);

  // Call the jni method, using the delay slot to set the JNIEnv* argument.
  __ save_thread(L7_thread_cache); // save Gthread
  __ callr(O0, 0);
  __ delayed()->
     add(L7_thread_cache, in_bytes(JavaThread::jni_environment_offset()), O0);

  // Back from jni method Lmethod in this frame is DEAD, DEAD, DEAD

  __ restore_thread(L7_thread_cache); // restore G2_thread
  __ reinit_heapbase();

  // must we block?

  // Block, if necessary, before resuming in _thread_in_Java state.
  // In order for GC to work, don't clear the last_Java_sp until after blocking.
  { Label no_block;
    AddressLiteral sync_state(SafepointSynchronize::address_of_state());

    // Switch thread to "native transition" state before reading the synchronization state.
    // This additional state is necessary because reading and testing the synchronization
    // state is not atomic w.r.t. GC, as this scenario demonstrates:
    //     Java thread A, in _thread_in_native state, loads _not_synchronized and is preempted.
    //     VM thread changes sync state to synchronizing and suspends threads for GC.
    //     Thread A is resumed to finish this native method, but doesn't block here since it
    //     didn't see any synchronization is progress, and escapes.
    __ set(_thread_in_native_trans, G3_scratch);
    __ st(G3_scratch, thread_state);
    if(os::is_MP()) {
      if (UseMembar) {
        // Force this write out before the read below
        __ membar(Assembler::StoreLoad);
      } else {
        // Write serialization page so VM thread can do a pseudo remote membar.
        // We use the current thread pointer to calculate a thread specific
        // offset to write to within the page. This minimizes bus traffic
        // due to cache line collision.
        __ serialize_memory(G2_thread, G1_scratch, G3_scratch);
      }
    }
    __ load_contents(sync_state, G3_scratch);
    __ cmp(G3_scratch, SafepointSynchronize::_not_synchronized);

    Label L;
    __ br(Assembler::notEqual, false, Assembler::pn, L);
    __ delayed()->ld(G2_thread, JavaThread::suspend_flags_offset(), G3_scratch);
    __ cmp_and_br_short(G3_scratch, 0, Assembler::equal, Assembler::pt, no_block);
    __ bind(L);

    // Block.  Save any potential method result value before the operation and
    // use a leaf call to leave the last_Java_frame setup undisturbed.
    save_native_result();
    __ call_VM_leaf(L7_thread_cache,
                    CAST_FROM_FN_PTR(address, JavaThread::check_special_condition_for_native_trans),
                    G2_thread);

    // Restore any method result value
    restore_native_result();
    __ bind(no_block);
  }

  // Clear the frame anchor now

  __ reset_last_Java_frame();

  // Move the result handler address
  __ mov(Lscratch, G3_scratch);
  // return possible result to the outer frame
#ifndef __LP64
  __ mov(O0, I0);
  __ restore(O1, G0, O1);
#else
  __ restore(O0, G0, O0);
#endif /* __LP64 */

  // Move result handler to expected register
  __ mov(G3_scratch, Lscratch);

  // Back in normal (native) interpreter frame. State is thread_in_native_trans
  // switch to thread_in_Java.

  __ set(_thread_in_Java, G3_scratch);
  __ st(G3_scratch, thread_state);

  if (CheckJNICalls) {
    // clear_pending_jni_exception_check
    __ st_ptr(G0, G2_thread, JavaThread::pending_jni_exception_check_fn_offset());
  }

  // reset handle block
  __ ld_ptr(G2_thread, JavaThread::active_handles_offset(), G3_scratch);
  __ st(G0, G3_scratch, JNIHandleBlock::top_offset_in_bytes());

  // If we have an oop result store it where it will be safe for any further gc
  // until we return now that we've released the handle it might be protected by

  {
    Label no_oop, store_result;

    __ set((intptr_t)AbstractInterpreter::result_handler(T_OBJECT), G3_scratch);
    __ cmp_and_brx_short(G3_scratch, Lscratch, Assembler::notEqual, Assembler::pt, no_oop);
    // Unbox oop result, e.g. JNIHandles::resolve value in O0.
    __ br_null(O0, false, Assembler::pn, store_result); // Use NULL as-is.
    __ delayed()->andcc(O0, JNIHandles::weak_tag_mask, G0); // Test for jweak
    __ brx(Assembler::zero, true, Assembler::pt, store_result);
    __ delayed()->ld_ptr(O0, 0, O0); // Maybe resolve (untagged) jobject.
    // Resolve jweak.
    __ ld_ptr(O0, -JNIHandles::weak_tag_value, O0);
#if INCLUDE_ALL_GCS
    if (UseG1GC) {
      __ g1_write_barrier_pre(noreg /* obj */,
                              noreg /* index */,
                              0 /* offset */,
                              O0 /* pre_val */,
                              G3_scratch /* tmp */,
                              true /* preserve_o_regs */);
    }
#endif // INCLUDE_ALL_GCS
    __ bind(store_result);
    // Store it where gc will look for it and result handler expects it.
    __ st_ptr(O0, FP, (frame::interpreter_frame_oop_temp_offset*wordSize) + STACK_BIAS);

    __ bind(no_oop);

  }


  // handle exceptions (exception handling will handle unlocking!)
  { Label L;
    Address exception_addr(G2_thread, Thread::pending_exception_offset());
    __ ld_ptr(exception_addr, Gtemp);
    __ br_null_short(Gtemp, Assembler::pt, L);
    // Note: This could be handled more efficiently since we know that the native
    //       method doesn't have an exception handler. We could directly return
    //       to the exception handler for the caller.
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_pending_exception));
    __ should_not_reach_here();
    __ bind(L);
  }

  // JVMTI support (preserves thread register)
  __ notify_method_exit(true, ilgl, InterpreterMacroAssembler::NotifyJVMTI);

  if (synchronized) {
    // save and restore any potential method result value around the unlocking operation
    save_native_result();

    __ add( __ top_most_monitor(), O1);
    __ unlock_object(O1);

    restore_native_result();
  }

#if defined(COMPILER2) && !defined(_LP64)

  // C2 expects long results in G1 we can't tell if we're returning to interpreted
  // or compiled so just be safe.

  __ sllx(O0, 32, G1);          // Shift bits into high G1
  __ srl (O1, 0, O1);           // Zero extend O1
  __ or3 (O1, G1, G1);          // OR 64 bits into G1

#endif /* COMPILER2 && !_LP64 */

  // dispose of return address and remove activation
#ifdef ASSERT
  {
    Label ok;
    __ cmp_and_brx_short(I5_savedSP, FP, Assembler::greaterEqualUnsigned, Assembler::pt, ok);
    __ stop("bad I5_savedSP value");
    __ should_not_reach_here();
    __ bind(ok);
  }
#endif
  __ jmp(Lscratch, 0);
  __ delayed()->nop();


  if (inc_counter) {
    // handle invocation counter overflow
    __ bind(invocation_counter_overflow);
    generate_counter_overflow(Lcontinue);
  }



  return entry;
}


// Generic method entry to (asm) interpreter
address TemplateInterpreterGenerator::generate_normal_entry(bool synchronized) {
  address entry = __ pc();

  bool inc_counter  = UseCompiler || CountCompiledCalls || LogTouchedMethods;

  // the following temporary registers are used during frame creation
  const Register Gtmp1 = G3_scratch ;
  const Register Gtmp2 = G1_scratch;

  // make sure registers are different!
  assert_different_registers(G2_thread, G5_method, Gargs, Gtmp1, Gtmp2);

  const Address constMethod       (G5_method, Method::const_offset());
  // Seems like G5_method is live at the point this is used. So we could make this look consistent
  // and use in the asserts.
  const Address access_flags      (Lmethod,   Method::access_flags_offset());

  const Register Glocals_size = G3;
  assert_different_registers(Glocals_size, G4_scratch, Gframe_size);

  // make sure method is not native & not abstract
  // rethink these assertions - they can be simplified and shared (gri 2/25/2000)
#ifdef ASSERT
  __ ld(G5_method, Method::access_flags_offset(), Gtmp1);
  {
    Label L;
    __ btst(JVM_ACC_NATIVE, Gtmp1);
    __ br(Assembler::zero, false, Assembler::pt, L);
    __ delayed()->nop();
    __ stop("tried to execute native method as non-native");
    __ bind(L);
  }
  { Label L;
    __ btst(JVM_ACC_ABSTRACT, Gtmp1);
    __ br(Assembler::zero, false, Assembler::pt, L);
    __ delayed()->nop();
    __ stop("tried to execute abstract method as non-abstract");
    __ bind(L);
  }
#endif // ASSERT

  // generate the code to allocate the interpreter stack frame

  generate_fixed_frame(false);

#ifdef FAST_DISPATCH
  __ set((intptr_t)Interpreter::dispatch_table(), IdispatchTables);
                                          // set bytecode dispatch table base
#endif

  //
  // Code to initialize the extra (i.e. non-parm) locals
  //
  Register init_value = noreg;    // will be G0 if we must clear locals
  // The way the code was setup before zerolocals was always true for vanilla java entries.
  // It could only be false for the specialized entries like accessor or empty which have
  // no extra locals so the testing was a waste of time and the extra locals were always
  // initialized. We removed this extra complication to already over complicated code.

  init_value = G0;
  Label clear_loop;

  const Register RconstMethod = O1;
  const Address size_of_parameters(RconstMethod, ConstMethod::size_of_parameters_offset());
  const Address size_of_locals    (RconstMethod, ConstMethod::size_of_locals_offset());

  // NOTE: If you change the frame layout, this code will need to
  // be updated!
  __ ld_ptr( constMethod, RconstMethod );
  __ lduh( size_of_locals, O2 );
  __ lduh( size_of_parameters, O1 );
  __ sll( O2, Interpreter::logStackElementSize, O2);
  __ sll( O1, Interpreter::logStackElementSize, O1 );
  __ sub( Llocals, O2, O2 );
  __ sub( Llocals, O1, O1 );

  __ bind( clear_loop );
  __ inc( O2, wordSize );

  __ cmp( O2, O1 );
  __ brx( Assembler::lessEqualUnsigned, true, Assembler::pt, clear_loop );
  __ delayed()->st_ptr( init_value, O2, 0 );

  const Address do_not_unlock_if_synchronized(G2_thread,
    JavaThread::do_not_unlock_if_synchronized_offset());
  // Since at this point in the method invocation the exception handler
  // would try to exit the monitor of synchronized methods which hasn't
  // been entered yet, we set the thread local variable
  // _do_not_unlock_if_synchronized to true. If any exception was thrown by
  // runtime, exception handling i.e. unlock_if_synchronized_method will
  // check this thread local flag.
  __ movbool(true, G3_scratch);
  __ stbool(G3_scratch, do_not_unlock_if_synchronized);

  __ profile_parameters_type(G1_scratch, G3_scratch, G4_scratch, Lscratch);
  // increment invocation counter and check for overflow
  //
  // Note: checking for negative value instead of overflow
  //       so we have a 'sticky' overflow test (may be of
  //       importance as soon as we have true MT/MP)
  Label invocation_counter_overflow;
  Label profile_method;
  Label profile_method_continue;
  Label Lcontinue;
  if (inc_counter) {
    generate_counter_incr(&invocation_counter_overflow, &profile_method, &profile_method_continue);
    if (ProfileInterpreter) {
      __ bind(profile_method_continue);
    }
  }
  __ bind(Lcontinue);

  bang_stack_shadow_pages(false);

  // reset the _do_not_unlock_if_synchronized flag
  __ stbool(G0, do_not_unlock_if_synchronized);

  // check for synchronized methods
  // Must happen AFTER invocation_counter check and stack overflow check,
  // so method is not locked if overflows.

  if (synchronized) {
    lock_method();
  } else {
#ifdef ASSERT
    { Label ok;
      __ ld(access_flags, O0);
      __ btst(JVM_ACC_SYNCHRONIZED, O0);
      __ br( Assembler::zero, false, Assembler::pt, ok);
      __ delayed()->nop();
      __ stop("method needs synchronization");
      __ bind(ok);
    }
#endif // ASSERT
  }

  // start execution

  __ verify_thread();

  // jvmti support
  __ notify_method_entry();

  // start executing instructions
  __ dispatch_next(vtos);


  if (inc_counter) {
    if (ProfileInterpreter) {
      // We have decided to profile this method in the interpreter
      __ bind(profile_method);

      __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::profile_method));
      __ set_method_data_pointer_for_bcp();
      __ ba_short(profile_method_continue);
    }

    // handle invocation counter overflow
    __ bind(invocation_counter_overflow);
    generate_counter_overflow(Lcontinue);
  }


  return entry;
}

//----------------------------------------------------------------------------------------------------
// Exceptions
void TemplateInterpreterGenerator::generate_throw_exception() {

  // Entry point in previous activation (i.e., if the caller was interpreted)
  Interpreter::_rethrow_exception_entry = __ pc();
  // O0: exception

  // entry point for exceptions thrown within interpreter code
  Interpreter::_throw_exception_entry = __ pc();
  __ verify_thread();
  // expression stack is undefined here
  // O0: exception, i.e. Oexception
  // Lbcp: exception bcp
  __ verify_oop(Oexception);


  // expression stack must be empty before entering the VM in case of an exception
  __ empty_expression_stack();
  // find exception handler address and preserve exception oop
  // call C routine to find handler and jump to it
  __ call_VM(O1, CAST_FROM_FN_PTR(address, InterpreterRuntime::exception_handler_for_exception), Oexception);
  __ push_ptr(O1); // push exception for exception handler bytecodes

  __ JMP(O0, 0); // jump to exception handler (may be remove activation entry!)
  __ delayed()->nop();


  // if the exception is not handled in the current frame
  // the frame is removed and the exception is rethrown
  // (i.e. exception continuation is _rethrow_exception)
  //
  // Note: At this point the bci is still the bxi for the instruction which caused
  //       the exception and the expression stack is empty. Thus, for any VM calls
  //       at this point, GC will find a legal oop map (with empty expression stack).

  // in current activation
  // tos: exception
  // Lbcp: exception bcp

  //
  // JVMTI PopFrame support
  //

  Interpreter::_remove_activation_preserving_args_entry = __ pc();
  Address popframe_condition_addr(G2_thread, JavaThread::popframe_condition_offset());
  // Set the popframe_processing bit in popframe_condition indicating that we are
  // currently handling popframe, so that call_VMs that may happen later do not trigger new
  // popframe handling cycles.

  __ ld(popframe_condition_addr, G3_scratch);
  __ or3(G3_scratch, JavaThread::popframe_processing_bit, G3_scratch);
  __ stw(G3_scratch, popframe_condition_addr);

  // Empty the expression stack, as in normal exception handling
  __ empty_expression_stack();
  __ unlock_if_synchronized_method(vtos, /* throw_monitor_exception */ false, /* install_monitor_exception */ false);

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
    __ call_VM_leaf(L7_thread_cache, CAST_FROM_FN_PTR(address, InterpreterRuntime::interpreter_contains), I7);
    __ br_notnull_short(O0, Assembler::pt, caller_not_deoptimized);

    const Register Gtmp1 = G3_scratch;
    const Register Gtmp2 = G1_scratch;
    const Register RconstMethod = Gtmp1;
    const Address constMethod(Lmethod, Method::const_offset());
    const Address size_of_parameters(RconstMethod, ConstMethod::size_of_parameters_offset());

    // Compute size of arguments for saving when returning to deoptimized caller
    __ ld_ptr(constMethod, RconstMethod);
    __ lduh(size_of_parameters, Gtmp1);
    __ sll(Gtmp1, Interpreter::logStackElementSize, Gtmp1);
    __ sub(Llocals, Gtmp1, Gtmp2);
    __ add(Gtmp2, wordSize, Gtmp2);
    // Save these arguments
    __ call_VM_leaf(L7_thread_cache, CAST_FROM_FN_PTR(address, Deoptimization::popframe_preserve_args), G2_thread, Gtmp1, Gtmp2);
    // Inform deoptimization that it is responsible for restoring these arguments
    __ set(JavaThread::popframe_force_deopt_reexecution_bit, Gtmp1);
    Address popframe_condition_addr(G2_thread, JavaThread::popframe_condition_offset());
    __ st(Gtmp1, popframe_condition_addr);

    // Return from the current method
    // The caller's SP was adjusted upon method entry to accomodate
    // the callee's non-argument locals. Undo that adjustment.
    __ ret();
    __ delayed()->restore(I5_savedSP, G0, SP);

    __ bind(caller_not_deoptimized);
  }

  // Clear the popframe condition flag
  __ stw(G0 /* popframe_inactive */, popframe_condition_addr);

  // Get out of the current method (how this is done depends on the particular compiler calling
  // convention that the interpreter currently follows)
  // The caller's SP was adjusted upon method entry to accomodate
  // the callee's non-argument locals. Undo that adjustment.
  __ restore(I5_savedSP, G0, SP);
  // The method data pointer was incremented already during
  // call profiling. We have to restore the mdp for the current bcp.
  if (ProfileInterpreter) {
    __ set_method_data_pointer_for_bcp();
  }

#if INCLUDE_JVMTI
  {
    Label L_done;

    __ ldub(Address(Lbcp, 0), G1_scratch);  // Load current bytecode
    __ cmp_and_br_short(G1_scratch, Bytecodes::_invokestatic, Assembler::notEqual, Assembler::pn, L_done);

    // The member name argument must be restored if _invokestatic is re-executed after a PopFrame call.
    // Detect such a case in the InterpreterRuntime function and return the member name argument, or NULL.

    __ call_VM(G1_scratch, CAST_FROM_FN_PTR(address, InterpreterRuntime::member_name_arg_or_null), I0, Lmethod, Lbcp);

    __ br_null(G1_scratch, false, Assembler::pn, L_done);
    __ delayed()->nop();

    __ st_ptr(G1_scratch, Lesp, wordSize);
    __ bind(L_done);
  }
#endif // INCLUDE_JVMTI

  // Resume bytecode interpretation at the current bcp
  __ dispatch_next(vtos);
  // end of JVMTI PopFrame support

  Interpreter::_remove_activation_entry = __ pc();

  // preserve exception over this code sequence (remove activation calls the vm, but oopmaps are not correct here)
  __ pop_ptr(Oexception);                                  // get exception

  // Intel has the following comment:
  //// remove the activation (without doing throws on illegalMonitorExceptions)
  // They remove the activation without checking for bad monitor state.
  // %%% We should make sure this is the right semantics before implementing.

  __ set_vm_result(Oexception);
  __ unlock_if_synchronized_method(vtos, /* throw_monitor_exception */ false);

  __ notify_method_exit(false, vtos, InterpreterMacroAssembler::SkipNotifyJVMTI);

  __ get_vm_result(Oexception);
  __ verify_oop(Oexception);

    const int return_reg_adjustment = frame::pc_return_offset;
  Address issuing_pc_addr(I7, return_reg_adjustment);

  // We are done with this activation frame; find out where to go next.
  // The continuation point will be an exception handler, which expects
  // the following registers set up:
  //
  // Oexception: exception
  // Oissuing_pc: the local call that threw exception
  // Other On: garbage
  // In/Ln:  the contents of the caller's register window
  //
  // We do the required restore at the last possible moment, because we
  // need to preserve some state across a runtime call.
  // (Remember that the caller activation is unknown--it might not be
  // interpreted, so things like Lscratch are useless in the caller.)

  // Although the Intel version uses call_C, we can use the more
  // compact call_VM.  (The only real difference on SPARC is a
  // harmlessly ignored [re]set_last_Java_frame, compared with
  // the Intel code which lacks this.)
  __ mov(Oexception,      Oexception ->after_save());  // get exception in I0 so it will be on O0 after restore
  __ add(issuing_pc_addr, Oissuing_pc->after_save());  // likewise set I1 to a value local to the caller
  __ super_call_VM_leaf(L7_thread_cache,
                        CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address),
                        G2_thread, Oissuing_pc->after_save());

  // The caller's SP was adjusted upon method entry to accomodate
  // the callee's non-argument locals. Undo that adjustment.
  __ JMP(O0, 0);                         // return exception handler in caller
  __ delayed()->restore(I5_savedSP, G0, SP);

  // (same old exception object is already in Oexception; see above)
  // Note that an "issuing PC" is actually the next PC after the call
}


//
// JVMTI ForceEarlyReturn support
//

address TemplateInterpreterGenerator::generate_earlyret_entry_for(TosState state) {
  address entry = __ pc();

  __ empty_expression_stack();
  __ load_earlyret_value(state);

  __ ld_ptr(G2_thread, JavaThread::jvmti_thread_state_offset(), G3_scratch);
  Address cond_addr(G3_scratch, JvmtiThreadState::earlyret_state_offset());

  // Clear the earlyret state
  __ stw(G0 /* JvmtiThreadState::earlyret_inactive */, cond_addr);

  __ remove_activation(state,
                       /* throw_monitor_exception */ false,
                       /* install_monitor_exception */ false);

  // The caller's SP was adjusted upon method entry to accomodate
  // the callee's non-argument locals. Undo that adjustment.
  __ ret();                             // return to caller
  __ delayed()->restore(I5_savedSP, G0, SP);

  return entry;
} // end of JVMTI ForceEarlyReturn support


//------------------------------------------------------------------------------------------------------------------------
// Helper for vtos entry point generation

void TemplateInterpreterGenerator::set_vtos_entry_points(Template* t, address& bep, address& cep, address& sep, address& aep, address& iep, address& lep, address& fep, address& dep, address& vep) {
  assert(t->is_valid() && t->tos_in() == vtos, "illegal template");
  Label L;
  aep = __ pc(); __ push_ptr(); __ ba_short(L);
  fep = __ pc(); __ push_f();   __ ba_short(L);
  dep = __ pc(); __ push_d();   __ ba_short(L);
  lep = __ pc(); __ push_l();   __ ba_short(L);
  iep = __ pc(); __ push_i();
  bep = cep = sep = iep;                        // there aren't any
  vep = __ pc(); __ bind(L);                    // fall through
  generate_and_dispatch(t);
}

// --------------------------------------------------------------------------------

// Non-product code
#ifndef PRODUCT
address TemplateInterpreterGenerator::generate_trace_code(TosState state) {
  address entry = __ pc();

  __ push(state);
  __ mov(O7, Lscratch); // protect return address within interpreter

  // Pass a 0 (not used in sparc) and the top of stack to the bytecode tracer
  __ mov( Otos_l2, G3_scratch );
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::trace_bytecode), G0, Otos_l1, G3_scratch);
  __ mov(Lscratch, O7); // restore return address
  __ pop(state);
  __ retl();
  __ delayed()->nop();

  return entry;
}


// helpers for generate_and_dispatch

void TemplateInterpreterGenerator::count_bytecode() {
  __ inc_counter(&BytecodeCounter::_counter_value, G3_scratch, G4_scratch);
}


void TemplateInterpreterGenerator::histogram_bytecode(Template* t) {
  __ inc_counter(&BytecodeHistogram::_counters[t->bytecode()], G3_scratch, G4_scratch);
}


void TemplateInterpreterGenerator::histogram_bytecode_pair(Template* t) {
  AddressLiteral index   (&BytecodePairHistogram::_index);
  AddressLiteral counters((address) &BytecodePairHistogram::_counters);

  // get index, shift out old bytecode, bring in new bytecode, and store it
  // _index = (_index >> log2_number_of_codes) |
  //          (bytecode << log2_number_of_codes);

  __ load_contents(index, G4_scratch);
  __ srl( G4_scratch, BytecodePairHistogram::log2_number_of_codes, G4_scratch );
  __ set( ((int)t->bytecode()) << BytecodePairHistogram::log2_number_of_codes,  G3_scratch );
  __ or3( G3_scratch,  G4_scratch, G4_scratch );
  __ store_contents(G4_scratch, index, G3_scratch);

  // bump bucket contents
  // _counters[_index] ++;

  __ set(counters, G3_scratch);                       // loads into G3_scratch
  __ sll( G4_scratch, LogBytesPerWord, G4_scratch );  // Index is word address
  __ add (G3_scratch, G4_scratch, G3_scratch);        // Add in index
  __ ld (G3_scratch, 0, G4_scratch);
  __ inc (G4_scratch);
  __ st (G4_scratch, 0, G3_scratch);
}


void TemplateInterpreterGenerator::trace_bytecode(Template* t) {
  // Call a little run-time stub to avoid blow-up for each bytecode.
  // The run-time runtime saves the right registers, depending on
  // the tosca in-state for the given template.
  address entry = Interpreter::trace_code(t->tos_in());
  guarantee(entry != NULL, "entry must have been generated");
  __ call(entry, relocInfo::none);
  __ delayed()->nop();
}


void TemplateInterpreterGenerator::stop_interpreter_at() {
  AddressLiteral counter(&BytecodeCounter::_counter_value);
  __ load_contents(counter, G3_scratch);
  AddressLiteral stop_at(&StopInterpreterAt);
  __ load_ptr_contents(stop_at, G4_scratch);
  __ cmp(G3_scratch, G4_scratch);
  __ breakpoint_trap(Assembler::equal, Assembler::icc);
}
#endif // not PRODUCT
