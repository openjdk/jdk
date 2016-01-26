/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2015 SAP AG. All rights reserved.
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
#include "prims/methodHandles.hpp"
#include "runtime/arguments.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/timer.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/debug.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#endif

#define __ _masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) // nothing
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

address AbstractInterpreterGenerator::generate_slow_signature_handler() {
  // Slow_signature handler that respects the PPC C calling conventions.
  //
  // We get called by the native entry code with our output register
  // area == 8. First we call InterpreterRuntime::get_result_handler
  // to copy the pointer to the signature string temporarily to the
  // first C-argument and to return the result_handler in
  // R3_RET. Since native_entry will copy the jni-pointer to the
  // first C-argument slot later on, it is OK to occupy this slot
  // temporarilly. Then we copy the argument list on the java
  // expression stack into native varargs format on the native stack
  // and load arguments into argument registers. Integer arguments in
  // the varargs vector will be sign-extended to 8 bytes.
  //
  // On entry:
  //   R3_ARG1        - intptr_t*     Address of java argument list in memory.
  //   R15_prev_state - BytecodeInterpreter* Address of interpreter state for
  //     this method
  //   R19_method
  //
  // On exit (just before return instruction):
  //   R3_RET            - contains the address of the result_handler.
  //   R4_ARG2           - is not updated for static methods and contains "this" otherwise.
  //   R5_ARG3-R10_ARG8: - When the (i-2)th Java argument is not of type float or double,
  //                       ARGi contains this argument. Otherwise, ARGi is not updated.
  //   F1_ARG1-F13_ARG13 - contain the first 13 arguments of type float or double.

  const int LogSizeOfTwoInstructions = 3;

  // FIXME: use Argument:: GL: Argument names different numbers!
  const int max_fp_register_arguments  = 13;
  const int max_int_register_arguments = 6;  // first 2 are reserved

  const Register arg_java       = R21_tmp1;
  const Register arg_c          = R22_tmp2;
  const Register signature      = R23_tmp3;  // is string
  const Register sig_byte       = R24_tmp4;
  const Register fpcnt          = R25_tmp5;
  const Register argcnt         = R26_tmp6;
  const Register intSlot        = R27_tmp7;
  const Register target_sp      = R28_tmp8;
  const FloatRegister floatSlot = F0;

  address entry = __ function_entry();

  __ save_LR_CR(R0);
  __ save_nonvolatile_gprs(R1_SP, _spill_nonvolatiles_neg(r14));
  // We use target_sp for storing arguments in the C frame.
  __ mr(target_sp, R1_SP);
  __ push_frame_reg_args_nonvolatiles(0, R11_scratch1);

  __ mr(arg_java, R3_ARG1);

  __ call_VM_leaf(CAST_FROM_FN_PTR(address, InterpreterRuntime::get_signature), R16_thread, R19_method);

  // Signature is in R3_RET. Signature is callee saved.
  __ mr(signature, R3_RET);

  // Get the result handler.
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, InterpreterRuntime::get_result_handler), R16_thread, R19_method);

  {
    Label L;
    // test if static
    // _access_flags._flags must be at offset 0.
    // TODO PPC port: requires change in shared code.
    //assert(in_bytes(AccessFlags::flags_offset()) == 0,
    //       "MethodDesc._access_flags == MethodDesc._access_flags._flags");
    // _access_flags must be a 32 bit value.
    assert(sizeof(AccessFlags) == 4, "wrong size");
    __ lwa(R11_scratch1/*access_flags*/, method_(access_flags));
    // testbit with condition register.
    __ testbitdi(CCR0, R0, R11_scratch1/*access_flags*/, JVM_ACC_STATIC_BIT);
    __ btrue(CCR0, L);
    // For non-static functions, pass "this" in R4_ARG2 and copy it
    // to 2nd C-arg slot.
    // We need to box the Java object here, so we use arg_java
    // (address of current Java stack slot) as argument and don't
    // dereference it as in case of ints, floats, etc.
    __ mr(R4_ARG2, arg_java);
    __ addi(arg_java, arg_java, -BytesPerWord);
    __ std(R4_ARG2, _abi(carg_2), target_sp);
    __ bind(L);
  }

  // Will be incremented directly after loop_start. argcnt=0
  // corresponds to 3rd C argument.
  __ li(argcnt, -1);
  // arg_c points to 3rd C argument
  __ addi(arg_c, target_sp, _abi(carg_3));
  // no floating-point args parsed so far
  __ li(fpcnt, 0);

  Label move_intSlot_to_ARG, move_floatSlot_to_FARG;
  Label loop_start, loop_end;
  Label do_int, do_long, do_float, do_double, do_dontreachhere, do_object, do_array, do_boxed;

  // signature points to '(' at entry
#ifdef ASSERT
  __ lbz(sig_byte, 0, signature);
  __ cmplwi(CCR0, sig_byte, '(');
  __ bne(CCR0, do_dontreachhere);
#endif

  __ bind(loop_start);

  __ addi(argcnt, argcnt, 1);
  __ lbzu(sig_byte, 1, signature);

  __ cmplwi(CCR0, sig_byte, ')'); // end of signature
  __ beq(CCR0, loop_end);

  __ cmplwi(CCR0, sig_byte, 'B'); // byte
  __ beq(CCR0, do_int);

  __ cmplwi(CCR0, sig_byte, 'C'); // char
  __ beq(CCR0, do_int);

  __ cmplwi(CCR0, sig_byte, 'D'); // double
  __ beq(CCR0, do_double);

  __ cmplwi(CCR0, sig_byte, 'F'); // float
  __ beq(CCR0, do_float);

  __ cmplwi(CCR0, sig_byte, 'I'); // int
  __ beq(CCR0, do_int);

  __ cmplwi(CCR0, sig_byte, 'J'); // long
  __ beq(CCR0, do_long);

  __ cmplwi(CCR0, sig_byte, 'S'); // short
  __ beq(CCR0, do_int);

  __ cmplwi(CCR0, sig_byte, 'Z'); // boolean
  __ beq(CCR0, do_int);

  __ cmplwi(CCR0, sig_byte, 'L'); // object
  __ beq(CCR0, do_object);

  __ cmplwi(CCR0, sig_byte, '['); // array
  __ beq(CCR0, do_array);

  //  __ cmplwi(CCR0, sig_byte, 'V'); // void cannot appear since we do not parse the return type
  //  __ beq(CCR0, do_void);

  __ bind(do_dontreachhere);

  __ unimplemented("ShouldNotReachHere in slow_signature_handler", 120);

  __ bind(do_array);

  {
    Label start_skip, end_skip;

    __ bind(start_skip);
    __ lbzu(sig_byte, 1, signature);
    __ cmplwi(CCR0, sig_byte, '[');
    __ beq(CCR0, start_skip); // skip further brackets
    __ cmplwi(CCR0, sig_byte, '9');
    __ bgt(CCR0, end_skip);   // no optional size
    __ cmplwi(CCR0, sig_byte, '0');
    __ bge(CCR0, start_skip); // skip optional size
    __ bind(end_skip);

    __ cmplwi(CCR0, sig_byte, 'L');
    __ beq(CCR0, do_object);  // for arrays of objects, the name of the object must be skipped
    __ b(do_boxed);          // otherwise, go directly to do_boxed
  }

  __ bind(do_object);
  {
    Label L;
    __ bind(L);
    __ lbzu(sig_byte, 1, signature);
    __ cmplwi(CCR0, sig_byte, ';');
    __ bne(CCR0, L);
   }
  // Need to box the Java object here, so we use arg_java (address of
  // current Java stack slot) as argument and don't dereference it as
  // in case of ints, floats, etc.
  Label do_null;
  __ bind(do_boxed);
  __ ld(R0,0, arg_java);
  __ cmpdi(CCR0, R0, 0);
  __ li(intSlot,0);
  __ beq(CCR0, do_null);
  __ mr(intSlot, arg_java);
  __ bind(do_null);
  __ std(intSlot, 0, arg_c);
  __ addi(arg_java, arg_java, -BytesPerWord);
  __ addi(arg_c, arg_c, BytesPerWord);
  __ cmplwi(CCR0, argcnt, max_int_register_arguments);
  __ blt(CCR0, move_intSlot_to_ARG);
  __ b(loop_start);

  __ bind(do_int);
  __ lwa(intSlot, 0, arg_java);
  __ std(intSlot, 0, arg_c);
  __ addi(arg_java, arg_java, -BytesPerWord);
  __ addi(arg_c, arg_c, BytesPerWord);
  __ cmplwi(CCR0, argcnt, max_int_register_arguments);
  __ blt(CCR0, move_intSlot_to_ARG);
  __ b(loop_start);

  __ bind(do_long);
  __ ld(intSlot, -BytesPerWord, arg_java);
  __ std(intSlot, 0, arg_c);
  __ addi(arg_java, arg_java, - 2 * BytesPerWord);
  __ addi(arg_c, arg_c, BytesPerWord);
  __ cmplwi(CCR0, argcnt, max_int_register_arguments);
  __ blt(CCR0, move_intSlot_to_ARG);
  __ b(loop_start);

  __ bind(do_float);
  __ lfs(floatSlot, 0, arg_java);
#if defined(LINUX)
  // Linux uses ELF ABI. Both original ELF and ELFv2 ABIs have float
  // in the least significant word of an argument slot.
#if defined(VM_LITTLE_ENDIAN)
  __ stfs(floatSlot, 0, arg_c);
#else
  __ stfs(floatSlot, 4, arg_c);
#endif
#elif defined(AIX)
  // Although AIX runs on big endian CPU, float is in most significant
  // word of an argument slot.
  __ stfs(floatSlot, 0, arg_c);
#else
#error "unknown OS"
#endif
  __ addi(arg_java, arg_java, -BytesPerWord);
  __ addi(arg_c, arg_c, BytesPerWord);
  __ cmplwi(CCR0, fpcnt, max_fp_register_arguments);
  __ blt(CCR0, move_floatSlot_to_FARG);
  __ b(loop_start);

  __ bind(do_double);
  __ lfd(floatSlot, - BytesPerWord, arg_java);
  __ stfd(floatSlot, 0, arg_c);
  __ addi(arg_java, arg_java, - 2 * BytesPerWord);
  __ addi(arg_c, arg_c, BytesPerWord);
  __ cmplwi(CCR0, fpcnt, max_fp_register_arguments);
  __ blt(CCR0, move_floatSlot_to_FARG);
  __ b(loop_start);

  __ bind(loop_end);

  __ pop_frame();
  __ restore_nonvolatile_gprs(R1_SP, _spill_nonvolatiles_neg(r14));
  __ restore_LR_CR(R0);

  __ blr();

  Label move_int_arg, move_float_arg;
  __ bind(move_int_arg); // each case must consist of 2 instructions (otherwise adapt LogSizeOfTwoInstructions)
  __ mr(R5_ARG3, intSlot);  __ b(loop_start);
  __ mr(R6_ARG4, intSlot);  __ b(loop_start);
  __ mr(R7_ARG5, intSlot);  __ b(loop_start);
  __ mr(R8_ARG6, intSlot);  __ b(loop_start);
  __ mr(R9_ARG7, intSlot);  __ b(loop_start);
  __ mr(R10_ARG8, intSlot); __ b(loop_start);

  __ bind(move_float_arg); // each case must consist of 2 instructions (otherwise adapt LogSizeOfTwoInstructions)
  __ fmr(F1_ARG1, floatSlot);   __ b(loop_start);
  __ fmr(F2_ARG2, floatSlot);   __ b(loop_start);
  __ fmr(F3_ARG3, floatSlot);   __ b(loop_start);
  __ fmr(F4_ARG4, floatSlot);   __ b(loop_start);
  __ fmr(F5_ARG5, floatSlot);   __ b(loop_start);
  __ fmr(F6_ARG6, floatSlot);   __ b(loop_start);
  __ fmr(F7_ARG7, floatSlot);   __ b(loop_start);
  __ fmr(F8_ARG8, floatSlot);   __ b(loop_start);
  __ fmr(F9_ARG9, floatSlot);   __ b(loop_start);
  __ fmr(F10_ARG10, floatSlot); __ b(loop_start);
  __ fmr(F11_ARG11, floatSlot); __ b(loop_start);
  __ fmr(F12_ARG12, floatSlot); __ b(loop_start);
  __ fmr(F13_ARG13, floatSlot); __ b(loop_start);

  __ bind(move_intSlot_to_ARG);
  __ sldi(R0, argcnt, LogSizeOfTwoInstructions);
  __ load_const(R11_scratch1, move_int_arg); // Label must be bound here.
  __ add(R11_scratch1, R0, R11_scratch1);
  __ mtctr(R11_scratch1/*branch_target*/);
  __ bctr();
  __ bind(move_floatSlot_to_FARG);
  __ sldi(R0, fpcnt, LogSizeOfTwoInstructions);
  __ addi(fpcnt, fpcnt, 1);
  __ load_const(R11_scratch1, move_float_arg); // Label must be bound here.
  __ add(R11_scratch1, R0, R11_scratch1);
  __ mtctr(R11_scratch1/*branch_target*/);
  __ bctr();

  return entry;
}

address AbstractInterpreterGenerator::generate_result_handler_for(BasicType type) {
  //
  // Registers alive
  //   R3_RET
  //   LR
  //
  // Registers updated
  //   R3_RET
  //

  Label done;
  address entry = __ pc();

  switch (type) {
  case T_BOOLEAN:
    // convert !=0 to 1
    __ neg(R0, R3_RET);
    __ orr(R0, R3_RET, R0);
    __ srwi(R3_RET, R0, 31);
    break;
  case T_BYTE:
     // sign extend 8 bits
     __ extsb(R3_RET, R3_RET);
     break;
  case T_CHAR:
     // zero extend 16 bits
     __ clrldi(R3_RET, R3_RET, 48);
     break;
  case T_SHORT:
     // sign extend 16 bits
     __ extsh(R3_RET, R3_RET);
     break;
  case T_INT:
     // sign extend 32 bits
     __ extsw(R3_RET, R3_RET);
     break;
  case T_LONG:
     break;
  case T_OBJECT:
    // unbox result if not null
    __ cmpdi(CCR0, R3_RET, 0);
    __ beq(CCR0, done);
    __ ld(R3_RET, 0, R3_RET);
    __ verify_oop(R3_RET);
    break;
  case T_FLOAT:
     break;
  case T_DOUBLE:
     break;
  case T_VOID:
     break;
  default: ShouldNotReachHere();
  }

  __ BIND(done);
  __ blr();

  return entry;
}

// Abstract method entry.
//
address TemplateInterpreterGenerator::generate_abstract_entry(void) {
  address entry = __ pc();

  //
  // Registers alive
  //   R16_thread     - JavaThread*
  //   R19_method     - callee's method (method to be invoked)
  //   R1_SP          - SP prepared such that caller's outgoing args are near top
  //   LR             - return address to caller
  //
  // Stack layout at this point:
  //
  //   0       [TOP_IJAVA_FRAME_ABI]         <-- R1_SP
  //           alignment (optional)
  //           [outgoing Java arguments]
  //           ...
  //   PARENT  [PARENT_IJAVA_FRAME_ABI]
  //            ...
  //

  // Can't use call_VM here because we have not set up a new
  // interpreter state. Make the call to the vm and make it look like
  // our caller set up the JavaFrameAnchor.
  __ set_top_ijava_frame_at_SP_as_last_Java_frame(R1_SP, R12_scratch2/*tmp*/);

  // Push a new C frame and save LR.
  __ save_LR_CR(R0);
  __ push_frame_reg_args(0, R11_scratch1);

  // This is not a leaf but we have a JavaFrameAnchor now and we will
  // check (create) exceptions afterward so this is ok.
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_AbstractMethodError),
                  R16_thread);

  // Pop the C frame and restore LR.
  __ pop_frame();
  __ restore_LR_CR(R0);

  // Reset JavaFrameAnchor from call_VM_leaf above.
  __ reset_last_Java_frame();

  // We don't know our caller, so jump to the general forward exception stub,
  // which will also pop our full frame off. Satisfy the interface of
  // SharedRuntime::generate_forward_exception()
  __ load_const_optimized(R11_scratch1, StubRoutines::forward_exception_entry(), R0);
  __ mtctr(R11_scratch1);
  __ bctr();

  return entry;
}

// Interpreter intrinsic for WeakReference.get().
// 1. Don't push a full blown frame and go on dispatching, but fetch the value
//    into R8 and return quickly
// 2. If G1 is active we *must* execute this intrinsic for corrrectness:
//    It contains a GC barrier which puts the reference into the satb buffer
//    to indicate that someone holds a strong reference to the object the
//    weak ref points to!
address TemplateInterpreterGenerator::generate_Reference_get_entry(void) {
  // Code: _aload_0, _getfield, _areturn
  // parameter size = 1
  //
  // The code that gets generated by this routine is split into 2 parts:
  //    1. the "intrinsified" code for G1 (or any SATB based GC),
  //    2. the slow path - which is an expansion of the regular method entry.
  //
  // Notes:
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

  if (UseG1GC) {
    address entry = __ pc();

    const int referent_offset = java_lang_ref_Reference::referent_offset;
    guarantee(referent_offset > 0, "referent offset not initialized");

    Label slow_path;

    // Debugging not possible, so can't use __ skip_if_jvmti_mode(slow_path, GR31_SCRATCH);

    // In the G1 code we don't check if we need to reach a safepoint. We
    // continue and the thread will safepoint at the next bytecode dispatch.

    // If the receiver is null then it is OK to jump to the slow path.
    __ ld(R3_RET, Interpreter::stackElementSize, R15_esp); // get receiver

    // Check if receiver == NULL and go the slow path.
    __ cmpdi(CCR0, R3_RET, 0);
    __ beq(CCR0, slow_path);

    // Load the value of the referent field.
    __ load_heap_oop(R3_RET, referent_offset, R3_RET);

    // Generate the G1 pre-barrier code to log the value of
    // the referent field in an SATB buffer. Note with
    // these parameters the pre-barrier does not generate
    // the load of the previous value.

    // Restore caller sp for c2i case.
#ifdef ASSERT
      __ ld(R9_ARG7, 0, R1_SP);
      __ ld(R10_ARG8, 0, R21_sender_SP);
      __ cmpd(CCR0, R9_ARG7, R10_ARG8);
      __ asm_assert_eq("backlink", 0x544);
#endif // ASSERT
    __ mr(R1_SP, R21_sender_SP); // Cut the stack back to where the caller started.

    __ g1_write_barrier_pre(noreg,         // obj
                            noreg,         // offset
                            R3_RET,        // pre_val
                            R11_scratch1,  // tmp
                            R12_scratch2,  // tmp
                            true);         // needs_frame

    __ blr();

    // Generate regular method entry.
    __ bind(slow_path);
    __ jump_to_entry(Interpreter::entry_for_kind(Interpreter::zerolocals), R11_scratch1);
    return entry;
  }

  return NULL;
}
