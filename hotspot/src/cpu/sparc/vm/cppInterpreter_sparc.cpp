/*
 * Copyright (c) 2007, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/cppInterpreter.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterGenerator.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "interpreter/interp_masm.hpp"
#include "oops/arrayOop.hpp"
#include "oops/methodData.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "runtime/arguments.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/timer.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"
#ifdef SHARK
#include "shark/shark_globals.hpp"
#endif

#ifdef CC_INTERP

// Routine exists to make tracebacks look decent in debugger
// while "shadow" interpreter frames are on stack. It is also
// used to distinguish interpreter frames.

extern "C" void RecursiveInterpreterActivation(interpreterState istate) {
  ShouldNotReachHere();
}

bool CppInterpreter::contains(address pc) {
  return ( _code->contains(pc) ||
         ( pc == (CAST_FROM_FN_PTR(address, RecursiveInterpreterActivation) + frame::pc_return_offset)));
}

#define STATE(field_name) Lstate, in_bytes(byte_offset_of(BytecodeInterpreter, field_name))
#define __ _masm->

Label frame_manager_entry; // c++ interpreter entry point this holds that entry point label.

static address unctrap_frame_manager_entry  = NULL;

static address interpreter_return_address  = NULL;
static address deopt_frame_manager_return_atos  = NULL;
static address deopt_frame_manager_return_btos  = NULL;
static address deopt_frame_manager_return_itos  = NULL;
static address deopt_frame_manager_return_ltos  = NULL;
static address deopt_frame_manager_return_ftos  = NULL;
static address deopt_frame_manager_return_dtos  = NULL;
static address deopt_frame_manager_return_vtos  = NULL;

const Register prevState = G1_scratch;

void InterpreterGenerator::save_native_result(void) {
  // result potentially in O0/O1: save it across calls
  __ stf(FloatRegisterImpl::D, F0, STATE(_native_fresult));
#ifdef _LP64
  __ stx(O0, STATE(_native_lresult));
#else
  __ std(O0, STATE(_native_lresult));
#endif
}

void InterpreterGenerator::restore_native_result(void) {

  // Restore any method result value
  __ ldf(FloatRegisterImpl::D, STATE(_native_fresult), F0);
#ifdef _LP64
  __ ldx(STATE(_native_lresult), O0);
#else
  __ ldd(STATE(_native_lresult), O0);
#endif
}

// A result handler converts/unboxes a native call result into
// a java interpreter/compiler result. The current frame is an
// interpreter frame. The activation frame unwind code must be
// consistent with that of TemplateTable::_return(...). In the
// case of native methods, the caller's SP was not modified.
address CppInterpreterGenerator::generate_result_handler_for(BasicType type) {
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
      __ ld_ptr(STATE(_oop_temp), Itos_i);
      __ verify_oop(Itos_i);
      break;
    default       : ShouldNotReachHere();
  }
  __ ret();                           // return from interpreter activation
  __ delayed()->restore(I5_savedSP, G0, SP);  // remove interpreter frame
  NOT_PRODUCT(__ emit_int32(0);)       // marker for disassembly
  return entry;
}

// tosca based result to c++ interpreter stack based result.
// Result goes to address in L1_scratch

address CppInterpreterGenerator::generate_tosca_to_stack_converter(BasicType type) {
  // A result is in the native abi result register from a native method call.
  // We need to return this result to the interpreter by pushing the result on the interpreter's
  // stack. This is relatively simple the destination is in L1_scratch
  // i.e. L1_scratch is the first free element on the stack. If we "push" a return value we must
  // adjust L1_scratch
  address entry = __ pc();
  switch (type) {
    case T_BOOLEAN:
      // !0 => true; 0 => false
      __ subcc(G0, O0, G0);
      __ addc(G0, 0, O0);
      __ st(O0, L1_scratch, 0);
      __ sub(L1_scratch, wordSize, L1_scratch);
      break;

    // cannot use and3, 0xFFFF too big as immediate value!
    case T_CHAR   :
      __ sll(O0, 16, O0);
      __ srl(O0, 16, O0);
      __ st(O0, L1_scratch, 0);
      __ sub(L1_scratch, wordSize, L1_scratch);
      break;

    case T_BYTE   :
      __ sll(O0, 24, O0);
      __ sra(O0, 24, O0);
      __ st(O0, L1_scratch, 0);
      __ sub(L1_scratch, wordSize, L1_scratch);
      break;

    case T_SHORT  :
      __ sll(O0, 16, O0);
      __ sra(O0, 16, O0);
      __ st(O0, L1_scratch, 0);
      __ sub(L1_scratch, wordSize, L1_scratch);
      break;
    case T_LONG   :
#ifndef _LP64
#if defined(COMPILER2)
  // All return values are where we want them, except for Longs.  C2 returns
  // longs in G1 in the 32-bit build whereas the interpreter wants them in O0/O1.
  // Since the interpreter will return longs in G1 and O0/O1 in the 32bit
  // build even if we are returning from interpreted we just do a little
  // stupid shuffing.
  // Note: I tried to make c2 return longs in O0/O1 and G1 so we wouldn't have to
  // do this here. Unfortunately if we did a rethrow we'd see an machepilog node
  // first which would move g1 -> O0/O1 and destroy the exception we were throwing.
      __ stx(G1, L1_scratch, -wordSize);
#else
      // native result is in O0, O1
      __ st(O1, L1_scratch, 0);                      // Low order
      __ st(O0, L1_scratch, -wordSize);              // High order
#endif /* COMPILER2 */
#else
      __ stx(O0, L1_scratch, -wordSize);
#endif
      __ sub(L1_scratch, 2*wordSize, L1_scratch);
      break;

    case T_INT    :
      __ st(O0, L1_scratch, 0);
      __ sub(L1_scratch, wordSize, L1_scratch);
      break;

    case T_VOID   : /* nothing to do */
      break;

    case T_FLOAT  :
      __ stf(FloatRegisterImpl::S, F0, L1_scratch, 0);
      __ sub(L1_scratch, wordSize, L1_scratch);
      break;

    case T_DOUBLE :
      // Every stack slot is aligned on 64 bit, However is this
      // the correct stack slot on 64bit?? QQQ
      __ stf(FloatRegisterImpl::D, F0, L1_scratch, -wordSize);
      __ sub(L1_scratch, 2*wordSize, L1_scratch);
      break;
    case T_OBJECT :
      __ verify_oop(O0);
      __ st_ptr(O0, L1_scratch, 0);
      __ sub(L1_scratch, wordSize, L1_scratch);
      break;
    default       : ShouldNotReachHere();
  }
  __ retl();                          // return from interpreter activation
  __ delayed()->nop();                // schedule this better
  NOT_PRODUCT(__ emit_int32(0);)       // marker for disassembly
  return entry;
}

address CppInterpreterGenerator::generate_stack_to_stack_converter(BasicType type) {
  // A result is in the java expression stack of the interpreted method that has just
  // returned. Place this result on the java expression stack of the caller.
  //
  // The current interpreter activation in Lstate is for the method just returning its
  // result. So we know that the result of this method is on the top of the current
  // execution stack (which is pre-pushed) and will be return to the top of the caller
  // stack. The top of the callers stack is the bottom of the locals of the current
  // activation.
  // Because of the way activation are managed by the frame manager the value of esp is
  // below both the stack top of the current activation and naturally the stack top
  // of the calling activation. This enable this routine to leave the return address
  // to the frame manager on the stack and do a vanilla return.
  //
  // On entry: O0 - points to source (callee stack top)
  //           O1 - points to destination (caller stack top [i.e. free location])
  // destroys O2, O3
  //

  address entry = __ pc();
  switch (type) {
    case T_VOID:  break;
      break;
    case T_FLOAT  :
    case T_BOOLEAN:
    case T_CHAR   :
    case T_BYTE   :
    case T_SHORT  :
    case T_INT    :
      // 1 word result
      __ ld(O0, 0, O2);
      __ st(O2, O1, 0);
      __ sub(O1, wordSize, O1);
      break;
    case T_DOUBLE  :
    case T_LONG    :
      // return top two words on current expression stack to caller's expression stack
      // The caller's expression stack is adjacent to the current frame manager's intepretState
      // except we allocated one extra word for this intepretState so we won't overwrite it
      // when we return a two word result.
#ifdef _LP64
      __ ld_ptr(O0, 0, O2);
      __ st_ptr(O2, O1, -wordSize);
#else
      __ ld(O0, 0, O2);
      __ ld(O0, wordSize, O3);
      __ st(O3, O1, 0);
      __ st(O2, O1, -wordSize);
#endif
      __ sub(O1, 2*wordSize, O1);
      break;
    case T_OBJECT :
      __ ld_ptr(O0, 0, O2);
      __ verify_oop(O2);                                               // verify it
      __ st_ptr(O2, O1, 0);
      __ sub(O1, wordSize, O1);
      break;
    default       : ShouldNotReachHere();
  }
  __ retl();
  __ delayed()->nop(); // QQ schedule this better
  return entry;
}

address CppInterpreterGenerator::generate_stack_to_native_abi_converter(BasicType type) {
  // A result is in the java expression stack of the interpreted method that has just
  // returned. Place this result in the native abi that the caller expects.
  // We are in a new frame registers we set must be in caller (i.e. callstub) frame.
  //
  // Similar to generate_stack_to_stack_converter above. Called at a similar time from the
  // frame manager execept in this situation the caller is native code (c1/c2/call_stub)
  // and so rather than return result onto caller's java expression stack we return the
  // result in the expected location based on the native abi.
  // On entry: O0 - source (stack top)
  // On exit result in expected output register
  // QQQ schedule this better

  address entry = __ pc();
  switch (type) {
    case T_VOID:  break;
      break;
    case T_FLOAT  :
      __ ldf(FloatRegisterImpl::S, O0, 0, F0);
      break;
    case T_BOOLEAN:
    case T_CHAR   :
    case T_BYTE   :
    case T_SHORT  :
    case T_INT    :
      // 1 word result
      __ ld(O0, 0, O0->after_save());
      break;
    case T_DOUBLE  :
      __ ldf(FloatRegisterImpl::D, O0, 0, F0);
      break;
    case T_LONG    :
      // return top two words on current expression stack to caller's expression stack
      // The caller's expression stack is adjacent to the current frame manager's interpretState
      // except we allocated one extra word for this intepretState so we won't overwrite it
      // when we return a two word result.
#ifdef _LP64
      __ ld_ptr(O0, 0, O0->after_save());
#else
      __ ld(O0, wordSize, O1->after_save());
      __ ld(O0, 0, O0->after_save());
#endif
#if defined(COMPILER2) && !defined(_LP64)
      // C2 expects long results in G1 we can't tell if we're returning to interpreted
      // or compiled so just be safe use G1 and O0/O1

      // Shift bits into high (msb) of G1
      __ sllx(Otos_l1->after_save(), 32, G1);
      // Zero extend low bits
      __ srl (Otos_l2->after_save(), 0, Otos_l2->after_save());
      __ or3 (Otos_l2->after_save(), G1, G1);
#endif /* COMPILER2 */
      break;
    case T_OBJECT :
      __ ld_ptr(O0, 0, O0->after_save());
      __ verify_oop(O0->after_save());                                               // verify it
      break;
    default       : ShouldNotReachHere();
  }
  __ retl();
  __ delayed()->nop();
  return entry;
}

address CppInterpreter::return_entry(TosState state, int length, Bytecodes::Code code) {
  // make it look good in the debugger
  return CAST_FROM_FN_PTR(address, RecursiveInterpreterActivation) + frame::pc_return_offset;
}

address CppInterpreter::deopt_entry(TosState state, int length) {
  address ret = NULL;
  if (length != 0) {
    switch (state) {
      case atos: ret = deopt_frame_manager_return_atos; break;
      case btos: ret = deopt_frame_manager_return_btos; break;
      case ctos:
      case stos:
      case itos: ret = deopt_frame_manager_return_itos; break;
      case ltos: ret = deopt_frame_manager_return_ltos; break;
      case ftos: ret = deopt_frame_manager_return_ftos; break;
      case dtos: ret = deopt_frame_manager_return_dtos; break;
      case vtos: ret = deopt_frame_manager_return_vtos; break;
    }
  } else {
    ret = unctrap_frame_manager_entry;  // re-execute the bytecode ( e.g. uncommon trap)
  }
  assert(ret != NULL, "Not initialized");
  return ret;
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
void InterpreterGenerator::generate_counter_incr(Label* overflow, Label* profile_method, Label* profile_method_continue) {
  Label done;
  const Register Rcounters = G3_scratch;

  __ ld_ptr(STATE(_method), G5_method);
  __ get_method_counters(G5_method, Rcounters, done);

  // Update standard invocation counters
  __ increment_invocation_counter(Rcounters, O0, G4_scratch);
  if (ProfileInterpreter) {
    Address interpreter_invocation_counter(Rcounters,
            in_bytes(MethodCounters::interpreter_invocation_counter_offset()));
    __ ld(interpreter_invocation_counter, G4_scratch);
    __ inc(G4_scratch);
    __ st(G4_scratch, interpreter_invocation_counter);
  }

  AddressLiteral invocation_limit((address)&InvocationCounter::InterpreterInvocationLimit);
  __ load_contents(invocation_limit, G3_scratch);
  __ cmp(O0, G3_scratch);
  __ br(Assembler::greaterEqualUnsigned, false, Assembler::pn, *overflow);
  __ delayed()->nop();
  __ bind(done);
}

address InterpreterGenerator::generate_empty_entry(void) {

  // A method that does nothing but return...

  address entry = __ pc();
  Label slow_path;

  // do nothing for empty methods (do not even increment invocation counter)
  if ( UseFastEmptyMethods) {
    // If we need a safepoint check, generate full interpreter entry.
    AddressLiteral sync_state(SafepointSynchronize::address_of_state());
    __ load_contents(sync_state, G3_scratch);
    __ cmp(G3_scratch, SafepointSynchronize::_not_synchronized);
    __ br(Assembler::notEqual, false, Assembler::pn, frame_manager_entry);
    __ delayed()->nop();

    // Code: _return
    __ retl();
    __ delayed()->mov(O5_savedSP, SP);
    return entry;
  }
  return NULL;
}

address InterpreterGenerator::generate_Reference_get_entry(void) {
#if INCLUDE_ALL_GCS
  if (UseG1GC) {
    // We need to generate have a routine that generates code to:
    //   * load the value in the referent field
    //   * passes that value to the pre-barrier.
    //
    // In the case of G1 this will record the value of the
    // referent in an SATB buffer if marking is active.
    // This will cause concurrent marking to mark the referent
    // field as live.
    Unimplemented();
  }
#endif // INCLUDE_ALL_GCS

  // If G1 is not enabled then attempt to go through the accessor entry point
  // Reference.get is an accessor
  return NULL;
}

//
// Interpreter stub for calling a native method. (C++ interpreter)
// This sets up a somewhat different looking stack for calling the native method
// than the typical interpreter frame setup.
//

address InterpreterGenerator::generate_native_entry(bool synchronized) {
  address entry = __ pc();

  // the following temporary registers are used during frame creation
  const Register Gtmp1 = G3_scratch ;
  const Register Gtmp2 = G1_scratch;
  const Register RconstMethod = Gtmp1;
  const Address constMethod(G5_method, in_bytes(Method::const_offset()));
  const Address size_of_parameters(RconstMethod, in_bytes(ConstMethod::size_of_parameters_offset()));

  bool inc_counter  = UseCompiler || CountCompiledCalls;

  // make sure registers are different!
  assert_different_registers(G2_thread, G5_method, Gargs, Gtmp1, Gtmp2);

  const Address access_flags      (G5_method, in_bytes(Method::access_flags_offset()));

  Label Lentry;
  __ bind(Lentry);

  const Register Glocals_size = G3;
  assert_different_registers(Glocals_size, G4_scratch, Gframe_size);

  // make sure method is native & not abstract
  // rethink these assertions - they can be simplified and shared (gri 2/25/2000)
#ifdef ASSERT
  __ ld(access_flags, Gtmp1);
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

  __ ld_ptr(constMethod, RconstMethod);
  __ lduh(size_of_parameters, Gtmp1);
  __ sll(Gtmp1, LogBytesPerWord, Gtmp2);       // parameter size in bytes
  __ add(Gargs, Gtmp2, Gargs);                 // points to first local + BytesPerWord
  // NEW
  __ add(Gargs, -wordSize, Gargs);             // points to first local[0]
  // generate the code to allocate the interpreter stack frame
  // NEW FRAME ALLOCATED HERE
  // save callers original sp
  // __ mov(SP, I5_savedSP->after_restore());

  generate_compute_interpreter_state(Lstate, G0, true);

  // At this point Lstate points to new interpreter state
  //

  const Address do_not_unlock_if_synchronized(G2_thread,
      in_bytes(JavaThread::do_not_unlock_if_synchronized_offset()));
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
  if (inc_counter) {
    generate_counter_incr(&invocation_counter_overflow, NULL, NULL);
  }
  Label Lcontinue;
  __ bind(Lcontinue);

  bang_stack_shadow_pages(true);
  // reset the _do_not_unlock_if_synchronized flag
  __ stbool(G0, do_not_unlock_if_synchronized);

  // check for synchronized methods
  // Must happen AFTER invocation_counter check, so method is not locked
  // if counter overflows.

  if (synchronized) {
    lock_method();
    // Don't see how G2_thread is preserved here...
    // __ verify_thread(); QQQ destroys L0,L1 can't use
  } else {
#ifdef ASSERT
    { Label ok;
      __ ld_ptr(STATE(_method), G5_method);
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

//   __ verify_thread(); kills L1,L2 can't  use at the moment

  // jvmti/jvmpi support
  __ notify_method_entry();

  // native call

  // (note that O0 is never an oop--at most it is a handle)
  // It is important not to smash any handles created by this call,
  // until any oop handle in O0 is dereferenced.

  // (note that the space for outgoing params is preallocated)

  // get signature handler

  Label pending_exception_present;

  { Label L;
    __ ld_ptr(STATE(_method), G5_method);
    __ ld_ptr(Address(G5_method, in_bytes(Method::signature_handler_offset())), G3_scratch);
    __ tst(G3_scratch);
    __ brx(Assembler::notZero, false, Assembler::pt, L);
    __ delayed()->nop();
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::prepare_native_call), G5_method, false);
    __ ld_ptr(STATE(_method), G5_method);

    Address exception_addr(G2_thread, in_bytes(Thread::pending_exception_offset()));
    __ ld_ptr(exception_addr, G3_scratch);
    __ br_notnull_short(G3_scratch, Assembler::pn, pending_exception_present);
    __ ld_ptr(Address(G5_method, in_bytes(Method::signature_handler_offset())), G3_scratch);
    __ bind(L);
  }

  // Push a new frame so that the args will really be stored in
  // Copy a few locals across so the new frame has the variables
  // we need but these values will be dead at the jni call and
  // therefore not gc volatile like the values in the current
  // frame (Lstate in particular)

  // Flush the state pointer to the register save area
  // Which is the only register we need for a stack walk.
  __ st_ptr(Lstate, SP, (Lstate->sp_offset_in_saved_window() * wordSize) + STACK_BIAS);

  __ mov(Lstate, O1);         // Need to pass the state pointer across the frame

  // Calculate current frame size
  __ sub(SP, FP, O3);         // Calculate negative of current frame size
  __ save(SP, O3, SP);        // Allocate an identical sized frame

  __ mov(I1, Lstate);          // In the "natural" register.

  // Note I7 has leftover trash. Slow signature handler will fill it in
  // should we get there. Normal jni call will set reasonable last_Java_pc
  // below (and fix I7 so the stack trace doesn't have a meaningless frame
  // in it).


  // call signature handler
  __ ld_ptr(STATE(_method), Lmethod);
  __ ld_ptr(STATE(_locals), Llocals);

  __ callr(G3_scratch, 0);
  __ delayed()->nop();
  __ ld_ptr(STATE(_thread), G2_thread);        // restore thread (shouldn't be needed)

  { Label not_static;

    __ ld_ptr(STATE(_method), G5_method);
    __ ld(access_flags, O0);
    __ btst(JVM_ACC_STATIC, O0);
    __ br( Assembler::zero, false, Assembler::pt, not_static);
    __ delayed()->
      // get native function entry point(O0 is a good temp until the very end)
       ld_ptr(Address(G5_method, in_bytes(Method::native_function_offset())), O0);
    // for static methods insert the mirror argument
    const int mirror_offset = in_bytes(Klass::java_mirror_offset());

    __ ld_ptr(Address(G5_method, in_bytes(Method:: const_offset())), O1);
    __ ld_ptr(Address(O1, in_bytes(ConstMethod::constants_offset())), O1);
    __ ld_ptr(Address(O1, ConstantPool::pool_holder_offset_in_bytes()), O1);
    __ ld_ptr(O1, mirror_offset, O1);
    // where the mirror handle body is allocated:
#ifdef ASSERT
    if (!PrintSignatureHandlers)  // do not dirty the output with this
    { Label L;
      __ tst(O1);
      __ brx(Assembler::notZero, false, Assembler::pt, L);
      __ delayed()->nop();
      __ stop("mirror is missing");
      __ bind(L);
    }
#endif // ASSERT
    __ st_ptr(O1, STATE(_oop_temp));
    __ add(STATE(_oop_temp), O1);            // this is really an LEA not an add
    __ bind(not_static);
  }

  // At this point, arguments have been copied off of stack into
  // their JNI positions, which are O1..O5 and SP[68..].
  // Oops are boxed in-place on the stack, with handles copied to arguments.
  // The result handler is in Lscratch.  O0 will shortly hold the JNIEnv*.

#ifdef ASSERT
  { Label L;
    __ tst(O0);
    __ brx(Assembler::notZero, false, Assembler::pt, L);
    __ delayed()->nop();
    __ stop("native entry point is missing");
    __ bind(L);
  }
#endif // ASSERT

  //
  // setup the java frame anchor
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
  Address flags(G2_thread,
                in_bytes(JavaThread::frame_anchor_offset()) + in_bytes(JavaFrameAnchor::flags_offset()));
  __ set(JavaFrameAnchor::flushed, G3_scratch);
  __ st(G3_scratch, flags);

  // Transition from _thread_in_Java to _thread_in_native. We are already safepoint ready.

  Address thread_state(G2_thread, in_bytes(JavaThread::thread_state_offset()));
#ifdef ASSERT
  { Label L;
    __ ld(thread_state, G3_scratch);
    __ cmp(G3_scratch, _thread_in_Java);
    __ br(Assembler::equal, false, Assembler::pt, L);
    __ delayed()->nop();
    __ stop("Wrong thread state in native stub");
    __ bind(L);
  }
#endif // ASSERT
  __ set(_thread_in_native, G3_scratch);
  __ st(G3_scratch, thread_state);

  // Call the jni method, using the delay slot to set the JNIEnv* argument.
  __ callr(O0, 0);
  __ delayed()->
     add(G2_thread, in_bytes(JavaThread::jni_environment_offset()), O0);
  __ ld_ptr(STATE(_thread), G2_thread);  // restore thread

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
      // Write serialization page so VM thread can do a pseudo remote membar.
      // We use the current thread pointer to calculate a thread specific
      // offset to write to within the page. This minimizes bus traffic
      // due to cache line collision.
      __ serialize_memory(G2_thread, G1_scratch, G3_scratch);
    }
    __ load_contents(sync_state, G3_scratch);
    __ cmp(G3_scratch, SafepointSynchronize::_not_synchronized);


    Label L;
    Address suspend_state(G2_thread, in_bytes(JavaThread::suspend_flags_offset()));
    __ br(Assembler::notEqual, false, Assembler::pn, L);
    __ delayed()->
      ld(suspend_state, G3_scratch);
    __ cmp(G3_scratch, 0);
    __ br(Assembler::equal, false, Assembler::pt, no_block);
    __ delayed()->nop();
    __ bind(L);

    // Block.  Save any potential method result value before the operation and
    // use a leaf call to leave the last_Java_frame setup undisturbed.
    save_native_result();
    __ call_VM_leaf(noreg,
                    CAST_FROM_FN_PTR(address, JavaThread::check_safepoint_and_suspend_for_native_trans),
                    G2_thread);
    __ ld_ptr(STATE(_thread), G2_thread);  // restore thread
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


  // thread state is thread_in_native_trans. Any safepoint blocking has
  // happened in the trampoline we are ready to switch to thread_in_Java.

  __ set(_thread_in_Java, G3_scratch);
  __ st(G3_scratch, thread_state);

  // If we have an oop result store it where it will be safe for any further gc
  // until we return now that we've released the handle it might be protected by

  {
    Label no_oop, store_result;

    __ set((intptr_t)AbstractInterpreter::result_handler(T_OBJECT), G3_scratch);
    __ cmp(G3_scratch, Lscratch);
    __ brx(Assembler::notEqual, false, Assembler::pt, no_oop);
    __ delayed()->nop();
    __ addcc(G0, O0, O0);
    __ brx(Assembler::notZero, true, Assembler::pt, store_result);     // if result is not NULL:
    __ delayed()->ld_ptr(O0, 0, O0);                                   // unbox it
    __ mov(G0, O0);

    __ bind(store_result);
    // Store it where gc will look for it and result handler expects it.
    __ st_ptr(O0, STATE(_oop_temp));

    __ bind(no_oop);

  }

  // reset handle block
  __ ld_ptr(G2_thread, in_bytes(JavaThread::active_handles_offset()), G3_scratch);
  __ st(G0, G3_scratch, JNIHandleBlock::top_offset_in_bytes());


  // handle exceptions (exception handling will handle unlocking!)
  { Label L;
    Address exception_addr (G2_thread, in_bytes(Thread::pending_exception_offset()));

    __ ld_ptr(exception_addr, Gtemp);
    __ tst(Gtemp);
    __ brx(Assembler::equal, false, Assembler::pt, L);
    __ delayed()->nop();
    __ bind(pending_exception_present);
    // With c++ interpreter we just leave it pending caller will do the correct thing. However...
    // Like x86 we ignore the result of the native call and leave the method locked. This
    // seems wrong to leave things locked.

    __ br(Assembler::always, false, Assembler::pt, StubRoutines::forward_exception_entry(), relocInfo::runtime_call_type);
    __ delayed()->restore(I5_savedSP, G0, SP);  // remove interpreter frame

    __ bind(L);
  }

  // jvmdi/jvmpi support (preserves thread register)
  __ notify_method_exit(true, ilgl, InterpreterMacroAssembler::NotifyJVMTI);

  if (synchronized) {
    // save and restore any potential method result value around the unlocking operation
    save_native_result();

    const int entry_size            = frame::interpreter_frame_monitor_size() * wordSize;
    // Get the initial monitor we allocated
    __ sub(Lstate, entry_size, O1);                        // initial monitor
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

#ifdef ASSERT
  {
    Label ok;
    __ cmp(I5_savedSP, FP);
    __ brx(Assembler::greaterEqualUnsigned, false, Assembler::pt, ok);
    __ delayed()->nop();
    __ stop("bad I5_savedSP value");
    __ should_not_reach_here();
    __ bind(ok);
  }
#endif
  // Calls result handler which POPS FRAME
  if (TraceJumps) {
    // Move target to register that is recordable
    __ mov(Lscratch, G3_scratch);
    __ JMP(G3_scratch, 0);
  } else {
    __ jmp(Lscratch, 0);
  }
  __ delayed()->nop();

  if (inc_counter) {
    // handle invocation counter overflow
    __ bind(invocation_counter_overflow);
    generate_counter_overflow(Lcontinue);
  }


  return entry;
}

void CppInterpreterGenerator::generate_compute_interpreter_state(const Register state,
                                                              const Register prev_state,
                                                              bool native) {

  // On entry
  // G5_method - caller's method
  // Gargs - points to initial parameters (i.e. locals[0])
  // G2_thread - valid? (C1 only??)
  // "prev_state" - contains any previous frame manager state which we must save a link
  //
  // On return
  // "state" is a pointer to the newly allocated  state object. We must allocate and initialize
  // a new interpretState object and the method expression stack.

  assert_different_registers(state, prev_state);
  assert_different_registers(prev_state, G3_scratch);
  const Register Gtmp = G3_scratch;
  const Address constMethod       (G5_method, in_bytes(Method::const_offset()));
  const Address access_flags      (G5_method, in_bytes(Method::access_flags_offset()));

  // slop factor is two extra slots on the expression stack so that
  // we always have room to store a result when returning from a call without parameters
  // that returns a result.

  const int slop_factor = 2*wordSize;

  const int fixed_size = ((sizeof(BytecodeInterpreter) + slop_factor) >> LogBytesPerWord) + // what is the slop factor?
                         Method::extra_stack_entries() + // extra stack for jsr 292
                         frame::memory_parameter_word_sp_offset +  // register save area + param window
                         (native ?  frame::interpreter_frame_extra_outgoing_argument_words : 0); // JNI, class

  // XXX G5_method valid

  // Now compute new frame size

  if (native) {
    const Register RconstMethod = Gtmp;
    const Address size_of_parameters(RconstMethod, in_bytes(ConstMethod::size_of_parameters_offset()));
    __ ld_ptr(constMethod, RconstMethod);
    __ lduh( size_of_parameters, Gtmp );
    __ calc_mem_param_words(Gtmp, Gtmp);     // space for native call parameters passed on the stack in words
  } else {
    // Full size expression stack
    __ ld_ptr(constMethod, Gtmp);
    __ lduh(Gtmp, in_bytes(ConstMethod::max_stack_offset()), Gtmp);
  }
  __ add(Gtmp, fixed_size, Gtmp);           // plus the fixed portion

  __ neg(Gtmp);                               // negative space for stack/parameters in words
  __ and3(Gtmp, -WordsPerLong, Gtmp);        // make multiple of 2 (SP must be 2-word aligned)
  __ sll(Gtmp, LogBytesPerWord, Gtmp);       // negative space for frame in bytes

  // Need to do stack size check here before we fault on large frames

  Label stack_ok;

  const int max_pages = StackShadowPages > (StackRedPages+StackYellowPages) ? StackShadowPages :
                                                                              (StackRedPages+StackYellowPages);


  __ ld_ptr(G2_thread, in_bytes(Thread::stack_base_offset()), O0);
  __ ld_ptr(G2_thread, in_bytes(Thread::stack_size_offset()), O1);
  // compute stack bottom
  __ sub(O0, O1, O0);

  // Avoid touching the guard pages
  // Also a fudge for frame size of BytecodeInterpreter::run
  // It varies from 1k->4k depending on build type
  const int fudge = 6 * K;

  __ set(fudge + (max_pages * os::vm_page_size()), O1);

  __ add(O0, O1, O0);
  __ sub(O0, Gtmp, O0);
  __ cmp(SP, O0);
  __ brx(Assembler::greaterUnsigned, false, Assembler::pt, stack_ok);
  __ delayed()->nop();

     // throw exception return address becomes throwing pc

  __ call_VM(Oexception, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_StackOverflowError));
  __ stop("never reached");

  __ bind(stack_ok);

  __ save(SP, Gtmp, SP);                      // setup new frame and register window

  // New window I7 call_stub or previous activation
  // O6 - register save area, BytecodeInterpreter just below it, args/locals just above that
  //
  __ sub(FP, sizeof(BytecodeInterpreter), state);        // Point to new Interpreter state
  __ add(state, STACK_BIAS, state );         // Account for 64bit bias

#define XXX_STATE(field_name) state, in_bytes(byte_offset_of(BytecodeInterpreter, field_name))

  // Initialize a new Interpreter state
  // orig_sp - caller's original sp
  // G2_thread - thread
  // Gargs - &locals[0] (unbiased?)
  // G5_method - method
  // SP (biased) - accounts for full size java stack, BytecodeInterpreter object, register save area, and register parameter save window


  __ set(0xdead0004, O1);


  __ st_ptr(Gargs, XXX_STATE(_locals));
  __ st_ptr(G0, XXX_STATE(_oop_temp));

  __ st_ptr(state, XXX_STATE(_self_link));                // point to self
  __ st_ptr(prev_state->after_save(), XXX_STATE(_prev_link)); // Chain interpreter states
  __ st_ptr(G2_thread, XXX_STATE(_thread));               // Store javathread

  if (native) {
    __ st_ptr(G0, XXX_STATE(_bcp));
  } else {
    __ ld_ptr(G5_method, in_bytes(Method::const_offset()), O2); // get ConstMethod*
    __ add(O2, in_bytes(ConstMethod::codes_offset()), O2);        // get bcp
    __ st_ptr(O2, XXX_STATE(_bcp));
  }

  __ st_ptr(G0, XXX_STATE(_mdx));
  __ st_ptr(G5_method, XXX_STATE(_method));

  __ set((int) BytecodeInterpreter::method_entry, O1);
  __ st(O1, XXX_STATE(_msg));

  __ ld_ptr(constMethod, O3);
  __ ld_ptr(O3, in_bytes(ConstMethod::constants_offset()), O3);
  __ ld_ptr(O3, ConstantPool::cache_offset_in_bytes(), O2);
  __ st_ptr(O2, XXX_STATE(_constants));

  __ st_ptr(G0, XXX_STATE(_result._to_call._callee));

  // Monitor base is just start of BytecodeInterpreter object;
  __ mov(state, O2);
  __ st_ptr(O2, XXX_STATE(_monitor_base));

  // Do we need a monitor for synchonized method?
  {
    __ ld(access_flags, O1);
    Label done;
    Label got_obj;
    __ btst(JVM_ACC_SYNCHRONIZED, O1);
    __ br( Assembler::zero, false, Assembler::pt, done);

    const int mirror_offset = in_bytes(Klass::java_mirror_offset());
    __ delayed()->btst(JVM_ACC_STATIC, O1);
    __ ld_ptr(XXX_STATE(_locals), O1);
    __ br( Assembler::zero, true, Assembler::pt, got_obj);
    __ delayed()->ld_ptr(O1, 0, O1);                  // get receiver for not-static case
    __ ld_ptr(constMethod, O1);
    __ ld_ptr( O1, in_bytes(ConstMethod::constants_offset()), O1);
    __ ld_ptr( O1, ConstantPool::pool_holder_offset_in_bytes(), O1);
    // lock the mirror, not the Klass*
    __ ld_ptr( O1, mirror_offset, O1);

    __ bind(got_obj);

  #ifdef ASSERT
    __ tst(O1);
    __ breakpoint_trap(Assembler::zero, Assembler::ptr_cc);
  #endif // ASSERT

    const int entry_size            = frame::interpreter_frame_monitor_size() * wordSize;
    __ sub(SP, entry_size, SP);                         // account for initial monitor
    __ sub(O2, entry_size, O2);                        // initial monitor
    __ st_ptr(O1, O2, BasicObjectLock::obj_offset_in_bytes()); // and allocate it for interpreter use
    __ bind(done);
  }

  // Remember initial frame bottom

  __ st_ptr(SP, XXX_STATE(_frame_bottom));

  __ st_ptr(O2, XXX_STATE(_stack_base));

  __ sub(O2, wordSize, O2);                    // prepush
  __ st_ptr(O2, XXX_STATE(_stack));                // PREPUSH

  // Full size expression stack
  __ ld_ptr(constMethod, O3);
  __ lduh(O3, in_bytes(ConstMethod::max_stack_offset()), O3);
  __ inc(O3, Method::extra_stack_entries());
  __ sll(O3, LogBytesPerWord, O3);
  __ sub(O2, O3, O3);
//  __ sub(O3, wordSize, O3);                    // so prepush doesn't look out of bounds
  __ st_ptr(O3, XXX_STATE(_stack_limit));

  if (!native) {
    //
    // Code to initialize locals
    //
    Register init_value = noreg;    // will be G0 if we must clear locals
    // Now zero locals
    if (true /* zerolocals */ || ClearInterpreterLocals) {
      // explicitly initialize locals
      init_value = G0;
    } else {
    #ifdef ASSERT
      // initialize locals to a garbage pattern for better debugging
      init_value = O3;
      __ set( 0x0F0F0F0F, init_value );
    #endif // ASSERT
    }
    if (init_value != noreg) {
      Label clear_loop;
      const Register RconstMethod = O1;
      const Address size_of_parameters(RconstMethod, in_bytes(ConstMethod::size_of_parameters_offset()));
      const Address size_of_locals    (RconstMethod, in_bytes(ConstMethod::size_of_locals_offset()));

      // NOTE: If you change the frame layout, this code will need to
      // be updated!
      __ ld_ptr( constMethod, RconstMethod );
      __ lduh( size_of_locals, O2 );
      __ lduh( size_of_parameters, O1 );
      __ sll( O2, LogBytesPerWord, O2);
      __ sll( O1, LogBytesPerWord, O1 );
      __ ld_ptr(XXX_STATE(_locals), L2_scratch);
      __ sub( L2_scratch, O2, O2 );
      __ sub( L2_scratch, O1, O1 );

      __ bind( clear_loop );
      __ inc( O2, wordSize );

      __ cmp( O2, O1 );
      __ br( Assembler::lessEqualUnsigned, true, Assembler::pt, clear_loop );
      __ delayed()->st_ptr( init_value, O2, 0 );
    }
  }
}
// Find preallocated  monitor and lock method (C++ interpreter)
//
void InterpreterGenerator::lock_method(void) {
// Lock the current method.
// Destroys registers L2_scratch, L3_scratch, O0
//
// Find everything relative to Lstate

#ifdef ASSERT
  __ ld_ptr(STATE(_method), L2_scratch);
  __ ld(L2_scratch, in_bytes(Method::access_flags_offset()), O0);

 { Label ok;
   __ btst(JVM_ACC_SYNCHRONIZED, O0);
   __ br( Assembler::notZero, false, Assembler::pt, ok);
   __ delayed()->nop();
   __ stop("method doesn't need synchronization");
   __ bind(ok);
  }
#endif // ASSERT

  // monitor is already allocated at stack base
  // and the lockee is already present
  __ ld_ptr(STATE(_stack_base), L2_scratch);
  __ ld_ptr(L2_scratch, BasicObjectLock::obj_offset_in_bytes(), O0);   // get object
  __ lock_object(L2_scratch, O0);

}

//  Generate code for handling resuming a deopted method
void CppInterpreterGenerator::generate_deopt_handling() {

  Label return_from_deopt_common;

  // deopt needs to jump to here to enter the interpreter (return a result)
  deopt_frame_manager_return_atos  = __ pc();

  // O0/O1 live
  __ ba(return_from_deopt_common);
  __ delayed()->set(AbstractInterpreter::BasicType_as_index(T_OBJECT), L3_scratch);    // Result stub address array index


  // deopt needs to jump to here to enter the interpreter (return a result)
  deopt_frame_manager_return_btos  = __ pc();

  // O0/O1 live
  __ ba(return_from_deopt_common);
  __ delayed()->set(AbstractInterpreter::BasicType_as_index(T_BOOLEAN), L3_scratch);    // Result stub address array index

  // deopt needs to jump to here to enter the interpreter (return a result)
  deopt_frame_manager_return_itos  = __ pc();

  // O0/O1 live
  __ ba(return_from_deopt_common);
  __ delayed()->set(AbstractInterpreter::BasicType_as_index(T_INT), L3_scratch);    // Result stub address array index

  // deopt needs to jump to here to enter the interpreter (return a result)

  deopt_frame_manager_return_ltos  = __ pc();
#if !defined(_LP64) && defined(COMPILER2)
  // All return values are where we want them, except for Longs.  C2 returns
  // longs in G1 in the 32-bit build whereas the interpreter wants them in O0/O1.
  // Since the interpreter will return longs in G1 and O0/O1 in the 32bit
  // build even if we are returning from interpreted we just do a little
  // stupid shuffing.
  // Note: I tried to make c2 return longs in O0/O1 and G1 so we wouldn't have to
  // do this here. Unfortunately if we did a rethrow we'd see an machepilog node
  // first which would move g1 -> O0/O1 and destroy the exception we were throwing.

  __ srl (G1, 0,O1);
  __ srlx(G1,32,O0);
#endif /* !_LP64 && COMPILER2 */
  // O0/O1 live
  __ ba(return_from_deopt_common);
  __ delayed()->set(AbstractInterpreter::BasicType_as_index(T_LONG), L3_scratch);    // Result stub address array index

  // deopt needs to jump to here to enter the interpreter (return a result)

  deopt_frame_manager_return_ftos  = __ pc();
  // O0/O1 live
  __ ba(return_from_deopt_common);
  __ delayed()->set(AbstractInterpreter::BasicType_as_index(T_FLOAT), L3_scratch);    // Result stub address array index

  // deopt needs to jump to here to enter the interpreter (return a result)
  deopt_frame_manager_return_dtos  = __ pc();

  // O0/O1 live
  __ ba(return_from_deopt_common);
  __ delayed()->set(AbstractInterpreter::BasicType_as_index(T_DOUBLE), L3_scratch);    // Result stub address array index

  // deopt needs to jump to here to enter the interpreter (return a result)
  deopt_frame_manager_return_vtos  = __ pc();

  // O0/O1 live
  __ set(AbstractInterpreter::BasicType_as_index(T_VOID), L3_scratch);

  // Deopt return common
  // an index is present that lets us move any possible result being
  // return to the interpreter's stack
  //
  __ bind(return_from_deopt_common);

  // Result if any is in native abi result (O0..O1/F0..F1). The java expression
  // stack is in the state that the  calling convention left it.
  // Copy the result from native abi result and place it on java expression stack.

  // Current interpreter state is present in Lstate

  // Get current pre-pushed top of interpreter stack
  // Any result (if any) is in native abi
  // result type index is in L3_scratch

  __ ld_ptr(STATE(_stack), L1_scratch);                                          // get top of java expr stack

  __ set((intptr_t)CppInterpreter::_tosca_to_stack, L4_scratch);
  __ sll(L3_scratch, LogBytesPerWord, L3_scratch);
  __ ld_ptr(L4_scratch, L3_scratch, Lscratch);                                       // get typed result converter address
  __ jmpl(Lscratch, G0, O7);                                         // and convert it
  __ delayed()->nop();

  // L1_scratch points to top of stack (prepushed)
  __ st_ptr(L1_scratch, STATE(_stack));
}

// Generate the code to handle a more_monitors message from the c++ interpreter
void CppInterpreterGenerator::generate_more_monitors() {

  Label entry, loop;
  const int entry_size = frame::interpreter_frame_monitor_size() * wordSize;
  // 1. compute new pointers                                // esp: old expression stack top
  __ delayed()->ld_ptr(STATE(_stack_base), L4_scratch);            // current expression stack bottom
  __ sub(L4_scratch, entry_size, L4_scratch);
  __ st_ptr(L4_scratch, STATE(_stack_base));

  __ sub(SP, entry_size, SP);                  // Grow stack
  __ st_ptr(SP, STATE(_frame_bottom));

  __ ld_ptr(STATE(_stack_limit), L2_scratch);
  __ sub(L2_scratch, entry_size, L2_scratch);
  __ st_ptr(L2_scratch, STATE(_stack_limit));

  __ ld_ptr(STATE(_stack), L1_scratch);                // Get current stack top
  __ sub(L1_scratch, entry_size, L1_scratch);
  __ st_ptr(L1_scratch, STATE(_stack));
  __ ba(entry);
  __ delayed()->add(L1_scratch, wordSize, L1_scratch);        // first real entry (undo prepush)

  // 2. move expression stack

  __ bind(loop);
  __ st_ptr(L3_scratch, Address(L1_scratch, 0));
  __ add(L1_scratch, wordSize, L1_scratch);
  __ bind(entry);
  __ cmp(L1_scratch, L4_scratch);
  __ br(Assembler::notEqual, false, Assembler::pt, loop);
  __ delayed()->ld_ptr(L1_scratch, entry_size, L3_scratch);

  // now zero the slot so we can find it.
  __ st_ptr(G0, L4_scratch, BasicObjectLock::obj_offset_in_bytes());

}

// Initial entry to C++ interpreter from the call_stub.
// This entry point is called the frame manager since it handles the generation
// of interpreter activation frames via requests directly from the vm (via call_stub)
// and via requests from the interpreter. The requests from the call_stub happen
// directly thru the entry point. Requests from the interpreter happen via returning
// from the interpreter and examining the message the interpreter has returned to
// the frame manager. The frame manager can take the following requests:

// NO_REQUEST - error, should never happen.
// MORE_MONITORS - need a new monitor. Shuffle the expression stack on down and
//                 allocate a new monitor.
// CALL_METHOD - setup a new activation to call a new method. Very similar to what
//               happens during entry during the entry via the call stub.
// RETURN_FROM_METHOD - remove an activation. Return to interpreter or call stub.
//
// Arguments:
//
// ebx: Method*
// ecx: receiver - unused (retrieved from stack as needed)
// esi: previous frame manager state (NULL from the call_stub/c1/c2)
//
//
// Stack layout at entry
//
// [ return address     ] <--- esp
// [ parameter n        ]
//   ...
// [ parameter 1        ]
// [ expression stack   ]
//
//
// We are free to blow any registers we like because the call_stub which brought us here
// initially has preserved the callee save registers already.
//
//

static address interpreter_frame_manager = NULL;

#ifdef ASSERT
  #define VALIDATE_STATE(scratch, marker)                         \
  {                                                               \
    Label skip;                                                   \
    __ ld_ptr(STATE(_self_link), scratch);                        \
    __ cmp(Lstate, scratch);                                      \
    __ brx(Assembler::equal, false, Assembler::pt, skip);         \
    __ delayed()->nop();                                          \
    __ breakpoint_trap();                                         \
    __ emit_int32(marker);                                         \
    __ bind(skip);                                                \
  }
#else
  #define VALIDATE_STATE(scratch, marker)
#endif /* ASSERT */

void CppInterpreterGenerator::adjust_callers_stack(Register args) {
//
// Adjust caller's stack so that all the locals can be contiguous with
// the parameters.
// Worries about stack overflow make this a pain.
//
// Destroys args, G3_scratch, G3_scratch
// In/Out O5_savedSP (sender's original SP)
//
//  assert_different_registers(state, prev_state);
  const Register Gtmp = G3_scratch;
  const Register RconstMethod = G3_scratch;
  const Register tmp = O2;
  const Address constMethod(G5_method, in_bytes(Method::const_offset()));
  const Address size_of_parameters(RconstMethod, in_bytes(ConstMethod::size_of_parameters_offset()));
  const Address size_of_locals    (RconstMethod, in_bytes(ConstMethod::size_of_locals_offset()));

  __ ld_ptr(constMethod, RconstMethod);
  __ lduh(size_of_parameters, tmp);
  __ sll(tmp, LogBytesPerWord, Gargs);       // parameter size in bytes
  __ add(args, Gargs, Gargs);                // points to first local + BytesPerWord
  // NEW
  __ add(Gargs, -wordSize, Gargs);             // points to first local[0]
  // determine extra space for non-argument locals & adjust caller's SP
  // Gtmp1: parameter size in words
  __ lduh(size_of_locals, Gtmp);
  __ compute_extra_locals_size_in_bytes(tmp, Gtmp, Gtmp);

#if 1
  // c2i adapters place the final interpreter argument in the register save area for O0/I0
  // the call_stub will place the final interpreter argument at
  // frame::memory_parameter_word_sp_offset. This is mostly not noticable for either asm
  // or c++ interpreter. However with the c++ interpreter when we do a recursive call
  // and try to make it look good in the debugger we will store the argument to
  // RecursiveInterpreterActivation in the register argument save area. Without allocating
  // extra space for the compiler this will overwrite locals in the local array of the
  // interpreter.
  // QQQ still needed with frameless adapters???

  const int c2i_adjust_words = frame::memory_parameter_word_sp_offset - frame::callee_register_argument_save_area_sp_offset;

  __ add(Gtmp, c2i_adjust_words*wordSize, Gtmp);
#endif // 1


  __ sub(SP, Gtmp, SP);                      // just caller's frame for the additional space we need.
}

address InterpreterGenerator::generate_normal_entry(bool synchronized) {

  // G5_method: Method*
  // G2_thread: thread (unused)
  // Gargs:   bottom of args (sender_sp)
  // O5: sender's sp

  // A single frame manager is plenty as we don't specialize for synchronized. We could and
  // the code is pretty much ready. Would need to change the test below and for good measure
  // modify generate_interpreter_state to only do the (pre) sync stuff stuff for synchronized
  // routines. Not clear this is worth it yet.

  if (interpreter_frame_manager) {
    return interpreter_frame_manager;
  }

  __ bind(frame_manager_entry);

  // the following temporary registers are used during frame creation
  const Register Gtmp1 = G3_scratch;
  // const Register Lmirror = L1;     // native mirror (native calls only)

  const Address constMethod       (G5_method, in_bytes(Method::const_offset()));
  const Address access_flags      (G5_method, in_bytes(Method::access_flags_offset()));

  address entry_point = __ pc();
  __ mov(G0, prevState);                                                 // no current activation


  Label re_dispatch;

  __ bind(re_dispatch);

  // Interpreter needs to have locals completely contiguous. In order to do that
  // We must adjust the caller's stack pointer for any locals beyond just the
  // parameters
  adjust_callers_stack(Gargs);

  // O5_savedSP still contains sender's sp

  // NEW FRAME

  generate_compute_interpreter_state(Lstate, prevState, false);

  // At this point a new interpreter frame and state object are created and initialized
  // Lstate has the pointer to the new activation
  // Any stack banging or limit check should already be done.

  Label call_interpreter;

  __ bind(call_interpreter);


#if 1
  __ set(0xdead002, Lmirror);
  __ set(0xdead002, L2_scratch);
  __ set(0xdead003, L3_scratch);
  __ set(0xdead004, L4_scratch);
  __ set(0xdead005, Lscratch);
  __ set(0xdead006, Lscratch2);
  __ set(0xdead007, L7_scratch);

  __ set(0xdeaf002, O2);
  __ set(0xdeaf003, O3);
  __ set(0xdeaf004, O4);
  __ set(0xdeaf005, O5);
#endif

  // Call interpreter (stack bang complete) enter here if message is
  // set and we know stack size is valid

  Label call_interpreter_2;

  __ bind(call_interpreter_2);

#ifdef ASSERT
  {
    Label skip;
    __ ld_ptr(STATE(_frame_bottom), G3_scratch);
    __ cmp(G3_scratch, SP);
    __ brx(Assembler::equal, false, Assembler::pt, skip);
    __ delayed()->nop();
    __ stop("SP not restored to frame bottom");
    __ bind(skip);
  }
#endif

  VALIDATE_STATE(G3_scratch, 4);
  __ set_last_Java_frame(SP, noreg);
  __ mov(Lstate, O0);                 // (arg) pointer to current state

  __ call(CAST_FROM_FN_PTR(address,
                           JvmtiExport::can_post_interpreter_events() ?
                                                                  BytecodeInterpreter::runWithChecks
                                                                : BytecodeInterpreter::run),
         relocInfo::runtime_call_type);

  __ delayed()->nop();

  __ ld_ptr(STATE(_thread), G2_thread);
  __ reset_last_Java_frame();

  // examine msg from interpreter to determine next action
  __ ld_ptr(STATE(_thread), G2_thread);                                  // restore G2_thread

  __ ld(STATE(_msg), L1_scratch);                                       // Get new message

  Label call_method;
  Label return_from_interpreted_method;
  Label throw_exception;
  Label do_OSR;
  Label bad_msg;
  Label resume_interpreter;

  __ cmp(L1_scratch, (int)BytecodeInterpreter::call_method);
  __ br(Assembler::equal, false, Assembler::pt, call_method);
  __ delayed()->cmp(L1_scratch, (int)BytecodeInterpreter::return_from_method);
  __ br(Assembler::equal, false, Assembler::pt, return_from_interpreted_method);
  __ delayed()->cmp(L1_scratch, (int)BytecodeInterpreter::throwing_exception);
  __ br(Assembler::equal, false, Assembler::pt, throw_exception);
  __ delayed()->cmp(L1_scratch, (int)BytecodeInterpreter::do_osr);
  __ br(Assembler::equal, false, Assembler::pt, do_OSR);
  __ delayed()->cmp(L1_scratch, (int)BytecodeInterpreter::more_monitors);
  __ br(Assembler::notEqual, false, Assembler::pt, bad_msg);

  // Allocate more monitor space, shuffle expression stack....

  generate_more_monitors();

  // new monitor slot allocated, resume the interpreter.

  __ set((int)BytecodeInterpreter::got_monitors, L1_scratch);
  VALIDATE_STATE(G3_scratch, 5);
  __ ba(call_interpreter);
  __ delayed()->st(L1_scratch, STATE(_msg));

  // uncommon trap needs to jump to here to enter the interpreter (re-execute current bytecode)
  unctrap_frame_manager_entry  = __ pc();

  // QQQ what message do we send

  __ ba(call_interpreter);
  __ delayed()->ld_ptr(STATE(_frame_bottom), SP);                  // restore to full stack frame

  //=============================================================================
  // Returning from a compiled method into a deopted method. The bytecode at the
  // bcp has completed. The result of the bytecode is in the native abi (the tosca
  // for the template based interpreter). Any stack space that was used by the
  // bytecode that has completed has been removed (e.g. parameters for an invoke)
  // so all that we have to do is place any pending result on the expression stack
  // and resume execution on the next bytecode.

  generate_deopt_handling();

  // ready to resume the interpreter

  __ set((int)BytecodeInterpreter::deopt_resume, L1_scratch);
  __ ba(call_interpreter);
  __ delayed()->st(L1_scratch, STATE(_msg));

  // Current frame has caught an exception we need to dispatch to the
  // handler. We can get here because a native interpreter frame caught
  // an exception in which case there is no handler and we must rethrow
  // If it is a vanilla interpreted frame the we simply drop into the
  // interpreter and let it do the lookup.

  Interpreter::_rethrow_exception_entry = __ pc();

  Label return_with_exception;
  Label unwind_and_forward;

  // O0: exception
  // O7: throwing pc

  // We want exception in the thread no matter what we ultimately decide about frame type.

  Address exception_addr (G2_thread, in_bytes(Thread::pending_exception_offset()));
  __ verify_thread();
  __ st_ptr(O0, exception_addr);

  // get the Method*
  __ ld_ptr(STATE(_method), G5_method);

  // if this current frame vanilla or native?

  __ ld(access_flags, Gtmp1);
  __ btst(JVM_ACC_NATIVE, Gtmp1);
  __ br(Assembler::zero, false, Assembler::pt, return_with_exception);  // vanilla interpreted frame handle directly
  __ delayed()->nop();

  // We drop thru to unwind a native interpreted frame with a pending exception
  // We jump here for the initial interpreter frame with exception pending
  // We unwind the current acivation and forward it to our caller.

  __ bind(unwind_and_forward);

  // Unwind frame and jump to forward exception. unwinding will place throwing pc in O7
  // as expected by forward_exception.

  __ restore(FP, G0, SP);                  // unwind interpreter state frame
  __ br(Assembler::always, false, Assembler::pt, StubRoutines::forward_exception_entry(), relocInfo::runtime_call_type);
  __ delayed()->mov(I5_savedSP->after_restore(), SP);

  // Return point from a call which returns a result in the native abi
  // (c1/c2/jni-native). This result must be processed onto the java
  // expression stack.
  //
  // A pending exception may be present in which case there is no result present

  address return_from_native_method = __ pc();

  VALIDATE_STATE(G3_scratch, 6);

  // Result if any is in native abi result (O0..O1/F0..F1). The java expression
  // stack is in the state that the  calling convention left it.
  // Copy the result from native abi result and place it on java expression stack.

  // Current interpreter state is present in Lstate

  // Exception pending?

  __ ld_ptr(STATE(_frame_bottom), SP);                             // restore to full stack frame
  __ ld_ptr(exception_addr, Lscratch);                                         // get any pending exception
  __ tst(Lscratch);                                                            // exception pending?
  __ brx(Assembler::notZero, false, Assembler::pt, return_with_exception);
  __ delayed()->nop();

  // Process the native abi result to java expression stack

  __ ld_ptr(STATE(_result._to_call._callee), L4_scratch);                        // called method
  __ ld_ptr(STATE(_stack), L1_scratch);                                          // get top of java expr stack
  // get parameter size
  __ ld_ptr(L4_scratch, in_bytes(Method::const_offset()), L2_scratch);
  __ lduh(L2_scratch, in_bytes(ConstMethod::size_of_parameters_offset()), L2_scratch);
  __ sll(L2_scratch, LogBytesPerWord, L2_scratch     );                           // parameter size in bytes
  __ add(L1_scratch, L2_scratch, L1_scratch);                                      // stack destination for result
  __ ld(L4_scratch, in_bytes(Method::result_index_offset()), L3_scratch); // called method result type index

  // tosca is really just native abi
  __ set((intptr_t)CppInterpreter::_tosca_to_stack, L4_scratch);
  __ sll(L3_scratch, LogBytesPerWord, L3_scratch);
  __ ld_ptr(L4_scratch, L3_scratch, Lscratch);                                       // get typed result converter address
  __ jmpl(Lscratch, G0, O7);                                                   // and convert it
  __ delayed()->nop();

  // L1_scratch points to top of stack (prepushed)

  __ ba(resume_interpreter);
  __ delayed()->mov(L1_scratch, O1);

  // An exception is being caught on return to a vanilla interpreter frame.
  // Empty the stack and resume interpreter

  __ bind(return_with_exception);

  __ ld_ptr(STATE(_frame_bottom), SP);                             // restore to full stack frame
  __ ld_ptr(STATE(_stack_base), O1);                               // empty java expression stack
  __ ba(resume_interpreter);
  __ delayed()->sub(O1, wordSize, O1);                             // account for prepush

  // Return from interpreted method we return result appropriate to the caller (i.e. "recursive"
  // interpreter call, or native) and unwind this interpreter activation.
  // All monitors should be unlocked.

  __ bind(return_from_interpreted_method);

  VALIDATE_STATE(G3_scratch, 7);

  Label return_to_initial_caller;

  // Interpreted result is on the top of the completed activation expression stack.
  // We must return it to the top of the callers stack if caller was interpreted
  // otherwise we convert to native abi result and return to call_stub/c1/c2
  // The caller's expression stack was truncated by the call however the current activation
  // has enough stuff on the stack that we have usable space there no matter what. The
  // other thing that makes it easy is that the top of the caller's stack is stored in STATE(_locals)
  // for the current activation

  __ ld_ptr(STATE(_prev_link), L1_scratch);
  __ ld_ptr(STATE(_method), L2_scratch);                               // get method just executed
  __ ld(L2_scratch, in_bytes(Method::result_index_offset()), L2_scratch);
  __ tst(L1_scratch);
  __ brx(Assembler::zero, false, Assembler::pt, return_to_initial_caller);
  __ delayed()->sll(L2_scratch, LogBytesPerWord, L2_scratch);

  // Copy result to callers java stack

  __ set((intptr_t)CppInterpreter::_stack_to_stack, L4_scratch);
  __ ld_ptr(L4_scratch, L2_scratch, Lscratch);                          // get typed result converter address
  __ ld_ptr(STATE(_stack), O0);                                       // current top (prepushed)
  __ ld_ptr(STATE(_locals), O1);                                      // stack destination

  // O0 - will be source, O1 - will be destination (preserved)
  __ jmpl(Lscratch, G0, O7);                                          // and convert it
  __ delayed()->add(O0, wordSize, O0);                                // get source (top of current expr stack)

  // O1 == &locals[0]

  // Result is now on caller's stack. Just unwind current activation and resume

  Label unwind_recursive_activation;


  __ bind(unwind_recursive_activation);

  // O1 == &locals[0] (really callers stacktop) for activation now returning
  // returning to interpreter method from "recursive" interpreter call
  // result converter left O1 pointing to top of the( prepushed) java stack for method we are returning
  // to. Now all we must do is unwind the state from the completed call

  // Must restore stack
  VALIDATE_STATE(G3_scratch, 8);

  // Return to interpreter method after a method call (interpreted/native/c1/c2) has completed.
  // Result if any is already on the caller's stack. All we must do now is remove the now dead
  // frame and tell interpreter to resume.


  __ mov(O1, I1);                                                     // pass back new stack top across activation
  // POP FRAME HERE ==================================
  __ restore(FP, G0, SP);                                             // unwind interpreter state frame
  __ ld_ptr(STATE(_frame_bottom), SP);                                // restore to full stack frame


  // Resume the interpreter. The current frame contains the current interpreter
  // state object.
  //
  // O1 == new java stack pointer

  __ bind(resume_interpreter);
  VALIDATE_STATE(G3_scratch, 10);

  // A frame we have already used before so no need to bang stack so use call_interpreter_2 entry

  __ set((int)BytecodeInterpreter::method_resume, L1_scratch);
  __ st(L1_scratch, STATE(_msg));
  __ ba(call_interpreter_2);
  __ delayed()->st_ptr(O1, STATE(_stack));

  // interpreter returning to native code (call_stub/c1/c2)
  // convert result and unwind initial activation
  // L2_scratch - scaled result type index

  __ bind(return_to_initial_caller);

  __ set((intptr_t)CppInterpreter::_stack_to_native_abi, L4_scratch);
  __ ld_ptr(L4_scratch, L2_scratch, Lscratch);                           // get typed result converter address
  __ ld_ptr(STATE(_stack), O0);                                        // current top (prepushed)
  __ jmpl(Lscratch, G0, O7);                                           // and convert it
  __ delayed()->add(O0, wordSize, O0);                                 // get source (top of current expr stack)

  Label unwind_initial_activation;
  __ bind(unwind_initial_activation);

  // RETURN TO CALL_STUB/C1/C2 code (result if any in I0..I1/(F0/..F1)
  // we can return here with an exception that wasn't handled by interpreted code
  // how does c1/c2 see it on return?

  // compute resulting sp before/after args popped depending upon calling convention
  // __ ld_ptr(STATE(_saved_sp), Gtmp1);
  //
  // POP FRAME HERE ==================================
  __ restore(FP, G0, SP);
  __ retl();
  __ delayed()->mov(I5_savedSP->after_restore(), SP);

  // OSR request, unwind the current frame and transfer to the OSR entry
  // and enter OSR nmethod

  __ bind(do_OSR);
  Label remove_initial_frame;
  __ ld_ptr(STATE(_prev_link), L1_scratch);
  __ ld_ptr(STATE(_result._osr._osr_buf), G1_scratch);

  // We are going to pop this frame. Is there another interpreter frame underneath
  // it or is it callstub/compiled?

  __ tst(L1_scratch);
  __ brx(Assembler::zero, false, Assembler::pt, remove_initial_frame);
  __ delayed()->ld_ptr(STATE(_result._osr._osr_entry), G3_scratch);

  // Frame underneath is an interpreter frame simply unwind
  // POP FRAME HERE ==================================
  __ restore(FP, G0, SP);                                             // unwind interpreter state frame
  __ mov(I5_savedSP->after_restore(), SP);

  // Since we are now calling native need to change our "return address" from the
  // dummy RecursiveInterpreterActivation to a return from native

  __ set((intptr_t)return_from_native_method - 8, O7);

  __ jmpl(G3_scratch, G0, G0);
  __ delayed()->mov(G1_scratch, O0);

  __ bind(remove_initial_frame);

  // POP FRAME HERE ==================================
  __ restore(FP, G0, SP);
  __ mov(I5_savedSP->after_restore(), SP);
  __ jmpl(G3_scratch, G0, G0);
  __ delayed()->mov(G1_scratch, O0);

  // Call a new method. All we do is (temporarily) trim the expression stack
  // push a return address to bring us back to here and leap to the new entry.
  // At this point we have a topmost frame that was allocated by the frame manager
  // which contains the current method interpreted state. We trim this frame
  // of excess java expression stack entries and then recurse.

  __ bind(call_method);

  // stack points to next free location and not top element on expression stack
  // method expects sp to be pointing to topmost element

  __ ld_ptr(STATE(_thread), G2_thread);
  __ ld_ptr(STATE(_result._to_call._callee), G5_method);


  // SP already takes in to account the 2 extra words we use for slop
  // when we call a "static long no_params()" method. So if
  // we trim back sp by the amount of unused java expression stack
  // there will be automagically the 2 extra words we need.
  // We also have to worry about keeping SP aligned.

  __ ld_ptr(STATE(_stack), Gargs);
  __ ld_ptr(STATE(_stack_limit), L1_scratch);

  // compute the unused java stack size
  __ sub(Gargs, L1_scratch, L2_scratch);                       // compute unused space

  // Round down the unused space to that stack is always 16-byte aligned
  // by making the unused space a multiple of the size of two longs.

  __ and3(L2_scratch, -2*BytesPerLong, L2_scratch);

  // Now trim the stack
  __ add(SP, L2_scratch, SP);


  // Now point to the final argument (account for prepush)
  __ add(Gargs, wordSize, Gargs);
#ifdef ASSERT
  // Make sure we have space for the window
  __ sub(Gargs, SP, L1_scratch);
  __ cmp(L1_scratch, 16*wordSize);
  {
    Label skip;
    __ brx(Assembler::greaterEqual, false, Assembler::pt, skip);
    __ delayed()->nop();
    __ stop("killed stack");
    __ bind(skip);
  }
#endif // ASSERT

  // Create a new frame where we can store values that make it look like the interpreter
  // really recursed.

  // prepare to recurse or call specialized entry

  // First link the registers we need

  // make the pc look good in debugger
  __ set(CAST_FROM_FN_PTR(intptr_t, RecursiveInterpreterActivation), O7);
  // argument too
  __ mov(Lstate, I0);

  // Record our sending SP
  __ mov(SP, O5_savedSP);

  __ ld_ptr(STATE(_result._to_call._callee_entry_point), L2_scratch);
  __ set((intptr_t) entry_point, L1_scratch);
  __ cmp(L1_scratch, L2_scratch);
  __ brx(Assembler::equal, false, Assembler::pt, re_dispatch);
  __ delayed()->mov(Lstate, prevState);                                // link activations

  // method uses specialized entry, push a return so we look like call stub setup
  // this path will handle fact that result is returned in registers and not
  // on the java stack.

  __ set((intptr_t)return_from_native_method - 8, O7);
  __ jmpl(L2_scratch, G0, G0);                               // Do specialized entry
  __ delayed()->nop();

  //
  // Bad Message from interpreter
  //
  __ bind(bad_msg);
  __ stop("Bad message from interpreter");

  // Interpreted method "returned" with an exception pass it on...
  // Pass result, unwind activation and continue/return to interpreter/call_stub
  // We handle result (if any) differently based on return to interpreter or call_stub

  __ bind(throw_exception);
  __ ld_ptr(STATE(_prev_link), L1_scratch);
  __ tst(L1_scratch);
  __ brx(Assembler::zero, false, Assembler::pt, unwind_and_forward);
  __ delayed()->nop();

  __ ld_ptr(STATE(_locals), O1); // get result of popping callee's args
  __ ba(unwind_recursive_activation);
  __ delayed()->nop();

  interpreter_frame_manager = entry_point;
  return entry_point;
}

InterpreterGenerator::InterpreterGenerator(StubQueue* code)
 : CppInterpreterGenerator(code) {
   generate_all(); // down here so it can be "virtual"
}


static int size_activation_helper(int callee_extra_locals, int max_stack, int monitor_size) {

  // Figure out the size of an interpreter frame (in words) given that we have a fully allocated
  // expression stack, the callee will have callee_extra_locals (so we can account for
  // frame extension) and monitor_size for monitors. Basically we need to calculate
  // this exactly like generate_fixed_frame/generate_compute_interpreter_state.
  //
  //
  // The big complicating thing here is that we must ensure that the stack stays properly
  // aligned. This would be even uglier if monitor size wasn't modulo what the stack
  // needs to be aligned for). We are given that the sp (fp) is already aligned by
  // the caller so we must ensure that it is properly aligned for our callee.
  //
  // Ths c++ interpreter always makes sure that we have a enough extra space on the
  // stack at all times to deal with the "stack long no_params()" method issue. This
  // is "slop_factor" here.
  const int slop_factor = 2;

  const int fixed_size = sizeof(BytecodeInterpreter)/wordSize +           // interpreter state object
                         frame::memory_parameter_word_sp_offset;   // register save area + param window
  return (round_to(max_stack +
                   slop_factor +
                   fixed_size +
                   monitor_size +
                   (callee_extra_locals * Interpreter::stackElementWords), WordsPerLong));

}

int AbstractInterpreter::size_top_interpreter_activation(Method* method) {

  // See call_stub code
  int call_stub_size  = round_to(7 + frame::memory_parameter_word_sp_offset,
                                 WordsPerLong);    // 7 + register save area

  // Save space for one monitor to get into the interpreted method in case
  // the method is synchronized
  int monitor_size    = method->is_synchronized() ?
                                1*frame::interpreter_frame_monitor_size() : 0;
  return size_activation_helper(method->max_locals(), method->max_stack(),
                                monitor_size) + call_stub_size;
}

void BytecodeInterpreter::layout_interpreterState(interpreterState to_fill,
                                           frame* caller,
                                           frame* current,
                                           Method* method,
                                           intptr_t* locals,
                                           intptr_t* stack,
                                           intptr_t* stack_base,
                                           intptr_t* monitor_base,
                                           intptr_t* frame_bottom,
                                           bool is_top_frame
                                           )
{
  // What about any vtable?
  //
  to_fill->_thread = JavaThread::current();
  // This gets filled in later but make it something recognizable for now
  to_fill->_bcp = method->code_base();
  to_fill->_locals = locals;
  to_fill->_constants = method->constants()->cache();
  to_fill->_method = method;
  to_fill->_mdx = NULL;
  to_fill->_stack = stack;
  if (is_top_frame && JavaThread::current()->popframe_forcing_deopt_reexecution() ) {
    to_fill->_msg = deopt_resume2;
  } else {
    to_fill->_msg = method_resume;
  }
  to_fill->_result._to_call._bcp_advance = 0;
  to_fill->_result._to_call._callee_entry_point = NULL; // doesn't matter to anyone
  to_fill->_result._to_call._callee = NULL; // doesn't matter to anyone
  to_fill->_prev_link = NULL;

  // Fill in the registers for the frame

  // Need to install _sender_sp. Actually not too hard in C++!
  // When the skeletal frames are layed out we fill in a value
  // for _sender_sp. That value is only correct for the oldest
  // skeletal frame constructed (because there is only a single
  // entry for "caller_adjustment". While the skeletal frames
  // exist that is good enough. We correct that calculation
  // here and get all the frames correct.

  // to_fill->_sender_sp = locals - (method->size_of_parameters() - 1);

  *current->register_addr(Lstate) = (intptr_t) to_fill;
  // skeletal already places a useful value here and this doesn't account
  // for alignment so don't bother.
  // *current->register_addr(I5_savedSP) =     (intptr_t) locals - (method->size_of_parameters() - 1);

  if (caller->is_interpreted_frame()) {
    interpreterState prev  = caller->get_interpreterState();
    to_fill->_prev_link = prev;
    // Make the prev callee look proper
    prev->_result._to_call._callee = method;
    if (*prev->_bcp == Bytecodes::_invokeinterface) {
      prev->_result._to_call._bcp_advance = 5;
    } else {
      prev->_result._to_call._bcp_advance = 3;
    }
  }
  to_fill->_oop_temp = NULL;
  to_fill->_stack_base = stack_base;
  // Need +1 here because stack_base points to the word just above the first expr stack entry
  // and stack_limit is supposed to point to the word just below the last expr stack entry.
  // See generate_compute_interpreter_state.
  to_fill->_stack_limit = stack_base - (method->max_stack() + 1);
  to_fill->_monitor_base = (BasicObjectLock*) monitor_base;

  // sparc specific
  to_fill->_frame_bottom = frame_bottom;
  to_fill->_self_link = to_fill;
#ifdef ASSERT
  to_fill->_native_fresult = 123456.789;
  to_fill->_native_lresult = CONST64(0xdeadcafedeafcafe);
#endif
}

void BytecodeInterpreter::pd_layout_interpreterState(interpreterState istate, address last_Java_pc, intptr_t* last_Java_fp) {
  istate->_last_Java_pc = (intptr_t*) last_Java_pc;
}

static int frame_size_helper(int max_stack,
                             int moncount,
                             int callee_param_size,
                             int callee_locals_size,
                             bool is_top_frame,
                             int& monitor_size,
                             int& full_frame_words) {
  int extra_locals_size = callee_locals_size - callee_param_size;
  monitor_size = (sizeof(BasicObjectLock) * moncount) / wordSize;
  full_frame_words = size_activation_helper(extra_locals_size, max_stack, monitor_size);
  int short_frame_words = size_activation_helper(extra_locals_size, max_stack, monitor_size);
  int frame_words = is_top_frame ? full_frame_words : short_frame_words;

  return frame_words;
}

int AbstractInterpreter::size_activation(int max_stack,
                                         int tempcount,
                                         int extra_args,
                                         int moncount,
                                         int callee_param_size,
                                         int callee_locals_size,
                                         bool is_top_frame) {
  assert(extra_args == 0, "NEED TO FIX");
  // NOTE: return size is in words not bytes
  // Calculate the amount our frame will be adjust by the callee. For top frame
  // this is zero.

  // NOTE: ia64 seems to do this wrong (or at least backwards) in that it
  // calculates the extra locals based on itself. Not what the callee does
  // to it. So it ignores last_frame_adjust value. Seems suspicious as far
  // as getting sender_sp correct.

  int unused_monitor_size = 0;
  int unused_full_frame_words = 0;
  return frame_size_helper(max_stack, moncount, callee_param_size, callee_locals_size, is_top_frame,
                           unused_monitor_size, unused_full_frame_words);
}
void AbstractInterpreter::layout_activation(Method* method,
                                            int tempcount, // Number of slots on java expression stack in use
                                            int popframe_extra_args,
                                            int moncount,  // Number of active monitors
                                            int caller_actual_parameters,
                                            int callee_param_size,
                                            int callee_locals_size,
                                            frame* caller,
                                            frame* interpreter_frame,
                                            bool is_top_frame,
                                            bool is_bottom_frame) {
  assert(popframe_extra_args == 0, "NEED TO FIX");
  // NOTE this code must exactly mimic what InterpreterGenerator::generate_compute_interpreter_state()
  // does as far as allocating an interpreter frame.
  // Set up the method, locals, and monitors.
  // The frame interpreter_frame is guaranteed to be the right size,
  // as determined by a previous call to the size_activation() method.
  // It is also guaranteed to be walkable even though it is in a skeletal state
  // NOTE: tempcount is the current size of the java expression stack. For top most
  //       frames we will allocate a full sized expression stack and not the curback
  //       version that non-top frames have.

  int monitor_size = 0;
  int full_frame_words = 0;
  int frame_words = frame_size_helper(method->max_stack(), moncount, callee_param_size, callee_locals_size,
                                      is_top_frame, monitor_size, full_frame_words);

  /*
    We must now fill in all the pieces of the frame. This means both
    the interpreterState and the registers.
  */

  // MUCHO HACK

  intptr_t* frame_bottom = interpreter_frame->sp() - (full_frame_words - frame_words);
  // 'interpreter_frame->sp()' is unbiased while 'frame_bottom' must be a biased value in 64bit mode.
  assert(((intptr_t)frame_bottom & 0xf) == 0, "SP biased in layout_activation");
  frame_bottom = (intptr_t*)((intptr_t)frame_bottom - STACK_BIAS);

  /* Now fillin the interpreterState object */

  interpreterState cur_state = (interpreterState) ((intptr_t)interpreter_frame->fp() -  sizeof(BytecodeInterpreter));


  intptr_t* locals;

  // Calculate the postion of locals[0]. This is painful because of
  // stack alignment (same as ia64). The problem is that we can
  // not compute the location of locals from fp(). fp() will account
  // for the extra locals but it also accounts for aligning the stack
  // and we can't determine if the locals[0] was misaligned but max_locals
  // was enough to have the
  // calculate postion of locals. fp already accounts for extra locals.
  // +2 for the static long no_params() issue.

  if (caller->is_interpreted_frame()) {
    // locals must agree with the caller because it will be used to set the
    // caller's tos when we return.
    interpreterState prev  = caller->get_interpreterState();
    // stack() is prepushed.
    locals = prev->stack() + method->size_of_parameters();
  } else {
    // Lay out locals block in the caller adjacent to the register window save area.
    //
    // Compiled frames do not allocate a varargs area which is why this if
    // statement is needed.
    //
    intptr_t* fp = interpreter_frame->fp();
    int local_words = method->max_locals() * Interpreter::stackElementWords;

    if (caller->is_compiled_frame()) {
      locals = fp + frame::register_save_words + local_words - 1;
    } else {
      locals = fp + frame::memory_parameter_word_sp_offset + local_words - 1;
    }

  }
  // END MUCHO HACK

  intptr_t* monitor_base = (intptr_t*) cur_state;
  intptr_t* stack_base =  monitor_base - monitor_size;
  /* +1 because stack is always prepushed */
  intptr_t* stack = stack_base - (tempcount + 1);


  BytecodeInterpreter::layout_interpreterState(cur_state,
                                               caller,
                                               interpreter_frame,
                                               method,
                                               locals,
                                               stack,
                                               stack_base,
                                               monitor_base,
                                               frame_bottom,
                                               is_top_frame);

  BytecodeInterpreter::pd_layout_interpreterState(cur_state, interpreter_return_address, interpreter_frame->fp());
}

#endif // CC_INTERP
