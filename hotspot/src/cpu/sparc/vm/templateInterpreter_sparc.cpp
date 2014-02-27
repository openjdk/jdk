/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/interpreterGenerator.hpp"
#include "interpreter/interpreterRuntime.hpp"
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

#ifndef CC_INTERP
#ifndef FAST_DISPATCH
#define FAST_DISPATCH 1
#endif
#undef FAST_DISPATCH


// Generation of Interpreter
//
// The InterpreterGenerator generates the interpreter into Interpreter::_code.


#define __ _masm->


//----------------------------------------------------------------------------------------------------


void InterpreterGenerator::save_native_result(void) {
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

void InterpreterGenerator::restore_native_result(void) {
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
void InterpreterGenerator::generate_counter_incr(Label* overflow, Label* profile_method, Label* profile_method_continue) {
  // Note: In tiered we increment either counters in MethodCounters* or in
  // MDO depending if we're profiling or not.
  const Register Rcounters = G3_scratch;
  Label done;

  if (TieredCompilation) {
    const int increment = InvocationCounter::count_increment;
    const int mask = ((1 << Tier0InvokeNotifyFreqLog) - 1) << InvocationCounter::count_shift;
    Label no_mdo;
    if (ProfileInterpreter) {
      // If no method data exists, go to profile_continue.
      __ ld_ptr(Lmethod, Method::method_data_offset(), G4_scratch);
      __ br_null_short(G4_scratch, Assembler::pn, no_mdo);
      // Increment counter
      Address mdo_invocation_counter(G4_scratch,
                                     in_bytes(MethodData::invocation_counter_offset()) +
                                     in_bytes(InvocationCounter::counter_offset()));
      __ increment_mask_and_jump(mdo_invocation_counter, increment, mask,
                                 G3_scratch, Lscratch,
                                 Assembler::zero, overflow);
      __ ba_short(done);
    }

    // Increment counter in MethodCounters*
    __ bind(no_mdo);
    Address invocation_counter(Rcounters,
            in_bytes(MethodCounters::invocation_counter_offset()) +
            in_bytes(InvocationCounter::counter_offset()));
    __ get_method_counters(Lmethod, Rcounters, done);
    __ increment_mask_and_jump(invocation_counter, increment, mask,
                               G4_scratch, Lscratch,
                               Assembler::zero, overflow);
    __ bind(done);
  } else {
    // Update standard invocation counters
    __ get_method_counters(Lmethod, Rcounters, done);
    __ increment_invocation_counter(Rcounters, O0, G4_scratch);
    if (ProfileInterpreter) {
      Address interpreter_invocation_counter(Rcounters,
            in_bytes(MethodCounters::interpreter_invocation_counter_offset()));
      __ ld(interpreter_invocation_counter, G4_scratch);
      __ inc(G4_scratch);
      __ st(G4_scratch, interpreter_invocation_counter);
    }

    if (ProfileInterpreter && profile_method != NULL) {
      // Test to see if we should create a method data oop
      AddressLiteral profile_limit((address)&InvocationCounter::InterpreterProfileLimit);
      __ load_contents(profile_limit, G3_scratch);
      __ cmp_and_br_short(O0, G3_scratch, Assembler::lessUnsigned, Assembler::pn, *profile_method_continue);

      // if no method data exists, go to profile_method
      __ test_method_data_pointer(*profile_method);
    }

    AddressLiteral invocation_limit((address)&InvocationCounter::InterpreterInvocationLimit);
    __ load_contents(invocation_limit, G3_scratch);
    __ cmp(O0, G3_scratch);
    __ br(Assembler::greaterEqualUnsigned, false, Assembler::pn, *overflow); // Far distance
    __ delayed()->nop();
    __ bind(done);
  }

}

// Allocate monitor and lock method (asm interpreter)
// ebx - Method*
//
void InterpreterGenerator::lock_method(void) {
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
    const int mirror_offset = in_bytes(Klass::java_mirror_offset());
    __ btst(JVM_ACC_STATIC, O0);
    __ br( Assembler::zero, true, Assembler::pt, done);
    __ delayed()->ld_ptr(Llocals, Interpreter::local_offset_in_bytes(0), O0); // get receiver for not-static case

    __ ld_ptr( Lmethod, in_bytes(Method::const_offset()), O0);
    __ ld_ptr( O0, in_bytes(ConstMethod::constants_offset()), O0);
    __ ld_ptr( O0, ConstantPool::pool_holder_offset_in_bytes(), O0);

    // lock the mirror, not the Klass*
    __ ld_ptr( O0, mirror_offset, O0);

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


void TemplateInterpreterGenerator::generate_stack_overflow_check(Register Rframe_size,
                                                         Register Rscratch,
                                                         Register Rscratch2) {
  const int page_size = os::vm_page_size();
  Label after_frame_check;

  assert_different_registers(Rframe_size, Rscratch, Rscratch2);

  __ set(page_size, Rscratch);
  __ cmp_and_br_short(Rframe_size, Rscratch, Assembler::lessEqual, Assembler::pt, after_frame_check);

  // get the stack base, and in debug, verify it is non-zero
  __ ld_ptr( G2_thread, Thread::stack_base_offset(), Rscratch );
#ifdef ASSERT
  Label base_not_zero;
  __ br_notnull_short(Rscratch, Assembler::pn, base_not_zero);
  __ stop("stack base is zero in generate_stack_overflow_check");
  __ bind(base_not_zero);
#endif

  // get the stack size, and in debug, verify it is non-zero
  assert( sizeof(size_t) == sizeof(intptr_t), "wrong load size" );
  __ ld_ptr( G2_thread, Thread::stack_size_offset(), Rscratch2 );
#ifdef ASSERT
  Label size_not_zero;
  __ br_notnull_short(Rscratch2, Assembler::pn, size_not_zero);
  __ stop("stack size is zero in generate_stack_overflow_check");
  __ bind(size_not_zero);
#endif

  // compute the beginning of the protected zone minus the requested frame size
  __ sub( Rscratch, Rscratch2,   Rscratch );
  __ set( (StackRedPages+StackYellowPages) * page_size, Rscratch2 );
  __ add( Rscratch, Rscratch2,   Rscratch );

  // Add in the size of the frame (which is the same as subtracting it from the
  // SP, which would take another register
  __ add( Rscratch, Rframe_size, Rscratch );

  // the frame is greater than one page in size, so check against
  // the bottom of the stack
  __ cmp_and_brx_short(SP, Rscratch, Assembler::greaterUnsigned, Assembler::pt, after_frame_check);

  // the stack will overflow, throw an exception

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

  // if you get to here, then there is enough stack space
  __ bind( after_frame_check );
}


//
// Generate a fixed interpreter frame. This is identical setup for interpreted
// methods and for native methods hence the shared code.

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
  } else {

    //
    // Compute number of locals in method apart from incoming parameters
    //
    const Address size_of_locals    (Otmp1, ConstMethod::size_of_locals_offset());
    __ ld_ptr( constMethod, Otmp1 );
    __ lduh( size_of_locals, Otmp1 );
    __ sub( Otmp1, Glocals_size, Glocals_size );
    __ round_to( Glocals_size, WordsPerLong );
    __ sll( Glocals_size, Interpreter::logStackElementSize, Glocals_size );

    // see if the frame is greater than one page in size. If so,
    // then we need to verify there is enough stack space remaining
    // Frame_size = (max_stack + extra_space) * BytesPerWord;
    __ ld_ptr( constMethod, Gframe_size );
    __ lduh( Gframe_size, in_bytes(ConstMethod::max_stack_offset()), Gframe_size );
    __ add( Gframe_size, extra_space, Gframe_size );
    __ round_to( Gframe_size, WordsPerLong );
    __ sll( Gframe_size, Interpreter::logStackElementSize, Gframe_size);

    // Add in java locals size for stack overflow check only
    __ add( Gframe_size, Glocals_size, Gframe_size );

    const Register Otmp2 = O4;
    assert_different_registers(Otmp1, Otmp2, O5_savedSP);
    generate_stack_overflow_check(Gframe_size, Otmp1, Otmp2);

    __ sub( Gframe_size, Glocals_size, Gframe_size);

    //
    // bump SP to accomodate the extra locals
    //
    __ sub( SP, Glocals_size, SP );
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

// Empty method, generate a very fast return.

address InterpreterGenerator::generate_empty_entry(void) {

  // A method that does nother but return...

  address entry = __ pc();
  Label slow_path;

  // do nothing for empty methods (do not even increment invocation counter)
  if ( UseFastEmptyMethods) {
    // If we need a safepoint check, generate full interpreter entry.
    AddressLiteral sync_state(SafepointSynchronize::address_of_state());
    __ set(sync_state, G3_scratch);
    __ cmp_and_br_short(G3_scratch, SafepointSynchronize::_not_synchronized, Assembler::notEqual, Assembler::pn, slow_path);

    // Code: _return
    __ retl();
    __ delayed()->mov(O5_savedSP, SP);

    __ bind(slow_path);
    (void) generate_normal_entry(false);

    return entry;
  }
  return NULL;
}

// Call an accessor method (assuming it is resolved, otherwise drop into
// vanilla (slow path) entry

// Generates code to elide accessor methods
// Uses G3_scratch and G1_scratch as scratch
address InterpreterGenerator::generate_accessor_entry(void) {

  // Code: _aload_0, _(i|a)getfield, _(i|a)return or any rewrites thereof;
  // parameter size = 1
  // Note: We can only use this code if the getfield has been resolved
  //       and if we don't have a null-pointer exception => check for
  //       these conditions first and use slow path if necessary.
  address entry = __ pc();
  Label slow_path;


  // XXX: for compressed oops pointer loading and decoding doesn't fit in
  // delay slot and damages G1
  if ( UseFastAccessorMethods && !UseCompressedOops ) {
    // Check if we need to reach a safepoint and generate full interpreter
    // frame if so.
    AddressLiteral sync_state(SafepointSynchronize::address_of_state());
    __ load_contents(sync_state, G3_scratch);
    __ cmp(G3_scratch, SafepointSynchronize::_not_synchronized);
    __ cmp_and_br_short(G3_scratch, SafepointSynchronize::_not_synchronized, Assembler::notEqual, Assembler::pn, slow_path);

    // Check if local 0 != NULL
    __ ld_ptr(Gargs, G0, Otos_i ); // get local 0
    // check if local 0 == NULL and go the slow path
    __ br_null_short(Otos_i, Assembler::pn, slow_path);


    // read first instruction word and extract bytecode @ 1 and index @ 2
    // get first 4 bytes of the bytecodes (big endian!)
    __ ld_ptr(G5_method, Method::const_offset(), G1_scratch);
    __ ld(G1_scratch, ConstMethod::codes_offset(), G1_scratch);

    // move index @ 2 far left then to the right most two bytes.
    __ sll(G1_scratch, 2*BitsPerByte, G1_scratch);
    __ srl(G1_scratch, 2*BitsPerByte - exact_log2(in_words(
                      ConstantPoolCacheEntry::size()) * BytesPerWord), G1_scratch);

    // get constant pool cache
    __ ld_ptr(G5_method, Method::const_offset(), G3_scratch);
    __ ld_ptr(G3_scratch, ConstMethod::constants_offset(), G3_scratch);
    __ ld_ptr(G3_scratch, ConstantPool::cache_offset_in_bytes(), G3_scratch);

    // get specific constant pool cache entry
    __ add(G3_scratch, G1_scratch, G3_scratch);

    // Check the constant Pool cache entry to see if it has been resolved.
    // If not, need the slow path.
    ByteSize cp_base_offset = ConstantPoolCache::base_offset();
    __ ld_ptr(G3_scratch, cp_base_offset + ConstantPoolCacheEntry::indices_offset(), G1_scratch);
    __ srl(G1_scratch, 2*BitsPerByte, G1_scratch);
    __ and3(G1_scratch, 0xFF, G1_scratch);
    __ cmp_and_br_short(G1_scratch, Bytecodes::_getfield, Assembler::notEqual, Assembler::pn, slow_path);

    // Get the type and return field offset from the constant pool cache
    __ ld_ptr(G3_scratch, cp_base_offset + ConstantPoolCacheEntry::flags_offset(), G1_scratch);
    __ ld_ptr(G3_scratch, cp_base_offset + ConstantPoolCacheEntry::f2_offset(), G3_scratch);

    Label xreturn_path;
    // Need to differentiate between igetfield, agetfield, bgetfield etc.
    // because they are different sizes.
    // Get the type from the constant pool cache
    __ srl(G1_scratch, ConstantPoolCacheEntry::tos_state_shift, G1_scratch);
    // Make sure we don't need to mask G1_scratch after the above shift
    ConstantPoolCacheEntry::verify_tos_state_shift();
    __ cmp(G1_scratch, atos );
    __ br(Assembler::equal, true, Assembler::pt, xreturn_path);
    __ delayed()->ld_ptr(Otos_i, G3_scratch, Otos_i);
    __ cmp(G1_scratch, itos);
    __ br(Assembler::equal, true, Assembler::pt, xreturn_path);
    __ delayed()->ld(Otos_i, G3_scratch, Otos_i);
    __ cmp(G1_scratch, stos);
    __ br(Assembler::equal, true, Assembler::pt, xreturn_path);
    __ delayed()->ldsh(Otos_i, G3_scratch, Otos_i);
    __ cmp(G1_scratch, ctos);
    __ br(Assembler::equal, true, Assembler::pt, xreturn_path);
    __ delayed()->lduh(Otos_i, G3_scratch, Otos_i);
#ifdef ASSERT
    __ cmp(G1_scratch, btos);
    __ br(Assembler::equal, true, Assembler::pt, xreturn_path);
    __ delayed()->ldsb(Otos_i, G3_scratch, Otos_i);
    __ should_not_reach_here();
#endif
    __ ldsb(Otos_i, G3_scratch, Otos_i);
    __ bind(xreturn_path);

    // _ireturn/_areturn
    __ retl();                      // return from leaf routine
    __ delayed()->mov(O5_savedSP, SP);

    // Generate regular method entry
    __ bind(slow_path);
    (void) generate_normal_entry(false);
    return entry;
  }
  return NULL;
}

// Method entry for java.lang.ref.Reference.get.
address InterpreterGenerator::generate_Reference_get_entry(void) {
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
    (void) generate_normal_entry(false);
    return entry;
  }
#endif // INCLUDE_ALL_GCS

  // If G1 is not enabled then attempt to go through the accessor entry point
  // Reference.get is an accessor
  return generate_accessor_entry();
}

//
// Interpreter stub for calling a native method. (asm interpreter)
// This sets up a somewhat different looking stack for calling the native method
// than the typical interpreter frame setup.
//

address InterpreterGenerator::generate_native_entry(bool synchronized) {
  address entry = __ pc();

  // the following temporary registers are used during frame creation
  const Register Gtmp1 = G3_scratch ;
  const Register Gtmp2 = G1_scratch;
  bool inc_counter  = UseCompiler || CountCompiledCalls;

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
    const int mirror_offset = in_bytes(Klass::java_mirror_offset());

    __ ld_ptr(Lmethod, Method:: const_offset(), O1);
    __ ld_ptr(O1, ConstMethod::constants_offset(), O1);
    __ ld_ptr(O1, ConstantPool::pool_holder_offset_in_bytes(), O1);
    __ ld_ptr(O1, mirror_offset, O1);
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

  // reset handle block
  __ ld_ptr(G2_thread, JavaThread::active_handles_offset(), G3_scratch);
  __ st_ptr(G0, G3_scratch, JNIHandleBlock::top_offset_in_bytes());

  // If we have an oop result store it where it will be safe for any further gc
  // until we return now that we've released the handle it might be protected by

  {
    Label no_oop, store_result;

    __ set((intptr_t)AbstractInterpreter::result_handler(T_OBJECT), G3_scratch);
    __ cmp_and_brx_short(G3_scratch, Lscratch, Assembler::notEqual, Assembler::pt, no_oop);
    __ addcc(G0, O0, O0);
    __ brx(Assembler::notZero, true, Assembler::pt, store_result);     // if result is not NULL:
    __ delayed()->ld_ptr(O0, 0, O0);                                   // unbox it
    __ mov(G0, O0);

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


// Generic method entry to (asm) interpreter
//------------------------------------------------------------------------------------------------------------------------
//
address InterpreterGenerator::generate_normal_entry(bool synchronized) {
  address entry = __ pc();

  bool inc_counter  = UseCompiler || CountCompiledCalls;

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
// Entry points & stack frame layout
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
  const int rounded_vm_local_words =
       round_to(frame::interpreter_frame_vm_local_words,WordsPerLong);
  // callee_locals and max_stack are counts, not the size in frame.
  const int locals_size =
       round_to(callee_extra_locals * Interpreter::stackElementWords, WordsPerLong);
  const int max_stack_words = max_stack * Interpreter::stackElementWords;
  return (round_to((max_stack_words
                   + rounded_vm_local_words
                   + frame::memory_parameter_word_sp_offset), WordsPerLong)
                   // already rounded
                   + locals_size + monitor_size);
}

// How much stack a method top interpreter activation needs in words.
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

int AbstractInterpreter::layout_activation(Method* method,
                                           int tempcount,
                                           int popframe_extra_args,
                                           int moncount,
                                           int caller_actual_parameters,
                                           int callee_param_count,
                                           int callee_local_count,
                                           frame* caller,
                                           frame* interpreter_frame,
                                           bool is_top_frame,
                                           bool is_bottom_frame) {
  // Note: This calculation must exactly parallel the frame setup
  // in InterpreterGenerator::generate_fixed_frame.
  // If f!=NULL, set up the following variables:
  //   - Lmethod
  //   - Llocals
  //   - Lmonitors (to the indicated number of monitors)
  //   - Lesp (to the indicated number of temps)
  // The frame f (if not NULL) on entry is a description of the caller of the frame
  // we are about to layout. We are guaranteed that we will be able to fill in a
  // new interpreter frame as its callee (i.e. the stack space is allocated and
  // the amount was determined by an earlier call to this method with f == NULL).
  // On return f (if not NULL) while describe the interpreter frame we just layed out.

  int monitor_size           = moncount * frame::interpreter_frame_monitor_size();
  int rounded_vm_local_words = round_to(frame::interpreter_frame_vm_local_words,WordsPerLong);

  assert(monitor_size == round_to(monitor_size, WordsPerLong), "must align");
  //
  // Note: if you look closely this appears to be doing something much different
  // than generate_fixed_frame. What is happening is this. On sparc we have to do
  // this dance with interpreter_sp_adjustment because the window save area would
  // appear just below the bottom (tos) of the caller's java expression stack. Because
  // the interpreter want to have the locals completely contiguous generate_fixed_frame
  // will adjust the caller's sp for the "extra locals" (max_locals - parameter_size).
  // Now in generate_fixed_frame the extension of the caller's sp happens in the callee.
  // In this code the opposite occurs the caller adjusts it's own stack base on the callee.
  // This is mostly ok but it does cause a problem when we get to the initial frame (the oldest)
  // because the oldest frame would have adjust its callers frame and yet that frame
  // already exists and isn't part of this array of frames we are unpacking. So at first
  // glance this would seem to mess up that frame. However Deoptimization::fetch_unroll_info_helper()
  // will after it calculates all of the frame's on_stack_size()'s will then figure out the
  // amount to adjust the caller of the initial (oldest) frame and the calculation will all
  // add up. It does seem like it simpler to account for the adjustment here (and remove the
  // callee... parameters here). However this would mean that this routine would have to take
  // the caller frame as input so we could adjust its sp (and set it's interpreter_sp_adjustment)
  // and run the calling loop in the reverse order. This would also would appear to mean making
  // this code aware of what the interactions are when that initial caller fram was an osr or
  // other adapter frame. deoptimization is complicated enough and  hard enough to debug that
  // there is no sense in messing working code.
  //

  int rounded_cls = round_to((callee_local_count - callee_param_count), WordsPerLong);
  assert(rounded_cls == round_to(rounded_cls, WordsPerLong), "must align");

  int raw_frame_size = size_activation_helper(rounded_cls, method->max_stack(),
                                              monitor_size);

  if (interpreter_frame != NULL) {
    // The skeleton frame must already look like an interpreter frame
    // even if not fully filled out.
    assert(interpreter_frame->is_interpreted_frame(), "Must be interpreted frame");

    intptr_t* fp = interpreter_frame->fp();

    JavaThread* thread = JavaThread::current();
    RegisterMap map(thread, false);
    // More verification that skeleton frame is properly walkable
    assert(fp == caller->sp(), "fp must match");

    intptr_t* montop     = fp - rounded_vm_local_words;

    // preallocate monitors (cf. __ add_monitor_to_stack)
    intptr_t* monitors = montop - monitor_size;

    // preallocate stack space
    intptr_t*  esp = monitors - 1 -
                     (tempcount * Interpreter::stackElementWords) -
                     popframe_extra_args;

    int local_words = method->max_locals() * Interpreter::stackElementWords;
    NEEDS_CLEANUP;
    intptr_t* locals;
    if (caller->is_interpreted_frame()) {
      // Can force the locals area to end up properly overlapping the top of the expression stack.
      intptr_t* Lesp_ptr = caller->interpreter_frame_tos_address() - 1;
      // Note that this computation means we replace size_of_parameters() values from the caller
      // interpreter frame's expression stack with our argument locals
      int parm_words  = caller_actual_parameters * Interpreter::stackElementWords;
      locals = Lesp_ptr + parm_words;
      int delta = local_words - parm_words;
      int computed_sp_adjustment = (delta > 0) ? round_to(delta, WordsPerLong) : 0;
      *interpreter_frame->register_addr(I5_savedSP)    = (intptr_t) (fp + computed_sp_adjustment) - STACK_BIAS;
      if (!is_bottom_frame) {
        // Llast_SP is set below for the current frame to SP (with the
        // extra space for the callee's locals). Here we adjust
        // Llast_SP for the caller's frame, removing the extra space
        // for the current method's locals.
        *caller->register_addr(Llast_SP) = *interpreter_frame->register_addr(I5_savedSP);
      } else {
        assert(*caller->register_addr(Llast_SP) >= *interpreter_frame->register_addr(I5_savedSP), "strange Llast_SP");
      }
    } else {
      assert(caller->is_compiled_frame() || caller->is_entry_frame(), "only possible cases");
      // Don't have Lesp available; lay out locals block in the caller
      // adjacent to the register window save area.
      //
      // Compiled frames do not allocate a varargs area which is why this if
      // statement is needed.
      //
      if (caller->is_compiled_frame()) {
        locals = fp + frame::register_save_words + local_words - 1;
      } else {
        locals = fp + frame::memory_parameter_word_sp_offset + local_words - 1;
      }
      if (!caller->is_entry_frame()) {
        // Caller wants his own SP back
        int caller_frame_size = caller->cb()->frame_size();
        *interpreter_frame->register_addr(I5_savedSP) = (intptr_t)(caller->fp() - caller_frame_size) - STACK_BIAS;
      }
    }
    if (TraceDeoptimization) {
      if (caller->is_entry_frame()) {
        // make sure I5_savedSP and the entry frames notion of saved SP
        // agree.  This assertion duplicate a check in entry frame code
        // but catches the failure earlier.
        assert(*caller->register_addr(Lscratch) == *interpreter_frame->register_addr(I5_savedSP),
               "would change callers SP");
      }
      if (caller->is_entry_frame()) {
        tty->print("entry ");
      }
      if (caller->is_compiled_frame()) {
        tty->print("compiled ");
        if (caller->is_deoptimized_frame()) {
          tty->print("(deopt) ");
        }
      }
      if (caller->is_interpreted_frame()) {
        tty->print("interpreted ");
      }
      tty->print_cr("caller fp=0x%x sp=0x%x", caller->fp(), caller->sp());
      tty->print_cr("save area = 0x%x, 0x%x", caller->sp(), caller->sp() + 16);
      tty->print_cr("save area = 0x%x, 0x%x", caller->fp(), caller->fp() + 16);
      tty->print_cr("interpreter fp=0x%x sp=0x%x", interpreter_frame->fp(), interpreter_frame->sp());
      tty->print_cr("save area = 0x%x, 0x%x", interpreter_frame->sp(), interpreter_frame->sp() + 16);
      tty->print_cr("save area = 0x%x, 0x%x", interpreter_frame->fp(), interpreter_frame->fp() + 16);
      tty->print_cr("Llocals = 0x%x", locals);
      tty->print_cr("Lesp = 0x%x", esp);
      tty->print_cr("Lmonitors = 0x%x", monitors);
    }

    if (method->max_locals() > 0) {
      assert(locals < caller->sp() || locals >= (caller->sp() + 16), "locals in save area");
      assert(locals < caller->fp() || locals > (caller->fp() + 16), "locals in save area");
      assert(locals < interpreter_frame->sp() || locals > (interpreter_frame->sp() + 16), "locals in save area");
      assert(locals < interpreter_frame->fp() || locals >= (interpreter_frame->fp() + 16), "locals in save area");
    }
#ifdef _LP64
    assert(*interpreter_frame->register_addr(I5_savedSP) & 1, "must be odd");
#endif

    *interpreter_frame->register_addr(Lmethod)     = (intptr_t) method;
    *interpreter_frame->register_addr(Llocals)     = (intptr_t) locals;
    *interpreter_frame->register_addr(Lmonitors)   = (intptr_t) monitors;
    *interpreter_frame->register_addr(Lesp)        = (intptr_t) esp;
    // Llast_SP will be same as SP as there is no adapter space
    *interpreter_frame->register_addr(Llast_SP)    = (intptr_t) interpreter_frame->sp() - STACK_BIAS;
    *interpreter_frame->register_addr(LcpoolCache) = (intptr_t) method->constants()->cache();
#ifdef FAST_DISPATCH
    *interpreter_frame->register_addr(IdispatchTables) = (intptr_t) Interpreter::dispatch_table();
#endif


#ifdef ASSERT
    BasicObjectLock* mp = (BasicObjectLock*)monitors;

    assert(interpreter_frame->interpreter_frame_method() == method, "method matches");
    assert(interpreter_frame->interpreter_frame_local_at(9) == (intptr_t *)((intptr_t)locals - (9 * Interpreter::stackElementSize)), "locals match");
    assert(interpreter_frame->interpreter_frame_monitor_end()   == mp, "monitor_end matches");
    assert(((intptr_t *)interpreter_frame->interpreter_frame_monitor_begin()) == ((intptr_t *)mp)+monitor_size, "monitor_begin matches");
    assert(interpreter_frame->interpreter_frame_tos_address()-1 == esp, "esp matches");

    // check bounds
    intptr_t* lo = interpreter_frame->sp() + (frame::memory_parameter_word_sp_offset - 1);
    intptr_t* hi = interpreter_frame->fp() - rounded_vm_local_words;
    assert(lo < monitors && montop <= hi, "monitors in bounds");
    assert(lo <= esp && esp < monitors, "esp in bounds");
#endif // ASSERT
  }

  return raw_frame_size;
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
  // Lbcp: exception bcx
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
  if (EnableInvokeDynamic) {
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


InterpreterGenerator::InterpreterGenerator(StubQueue* code)
 : TemplateInterpreterGenerator(code) {
   generate_all(); // down here so it can be "virtual"
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
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, SharedRuntime::trace_bytecode), G0, Otos_l1, G3_scratch);
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
#endif // !CC_INTERP
