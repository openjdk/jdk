/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/bytecodeHistogram.hpp"
#include "interpreter/cppInterpreter.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterGenerator.hpp"
#include "interpreter/interpreterRuntime.hpp"
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
#ifdef SHARK
#include "shark/shark_globals.hpp"
#endif

#ifdef CC_INTERP

#define __ _masm->

// Contains is used for identifying interpreter frames during a stack-walk.
// A frame with a PC in InterpretMethod must be identified as a normal C frame.
bool CppInterpreter::contains(address pc) {
  return _code->contains(pc);
}

#ifdef PRODUCT
#define BLOCK_COMMENT(str) // nothing
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

static address interpreter_frame_manager        = NULL;
static address frame_manager_specialized_return = NULL;
static address native_entry                     = NULL;

static address interpreter_return_address       = NULL;

static address unctrap_frame_manager_entry      = NULL;

static address deopt_frame_manager_return_atos  = NULL;
static address deopt_frame_manager_return_btos  = NULL;
static address deopt_frame_manager_return_itos  = NULL;
static address deopt_frame_manager_return_ltos  = NULL;
static address deopt_frame_manager_return_ftos  = NULL;
static address deopt_frame_manager_return_dtos  = NULL;
static address deopt_frame_manager_return_vtos  = NULL;

// A result handler converts/unboxes a native call result into
// a java interpreter/compiler result. The current frame is an
// interpreter frame.
address CppInterpreterGenerator::generate_result_handler_for(BasicType type) {
  return AbstractInterpreterGenerator::generate_result_handler_for(type);
}

// tosca based result to c++ interpreter stack based result.
address CppInterpreterGenerator::generate_tosca_to_stack_converter(BasicType type) {
  //
  // A result is in the native abi result register from a native
  // method call. We need to return this result to the interpreter by
  // pushing the result on the interpreter's stack.
  //
  // Registers alive:
  //   R3_ARG1(R3_RET)/F1_ARG1(F1_RET) - result to move
  //   R4_ARG2                         - address of tos
  //   LR
  //
  // Registers updated:
  //   R3_RET(R3_ARG1)   - address of new tos (== R17_tos for T_VOID)
  //

  int number_of_used_slots = 1;

  const Register tos = R4_ARG2;
  Label done;
  Label is_false;

  address entry = __ pc();

  switch (type) {
  case T_BOOLEAN:
    __ cmpwi(CCR0, R3_RET, 0);
    __ beq(CCR0, is_false);
    __ li(R3_RET, 1);
    __ stw(R3_RET, 0, tos);
    __ b(done);
    __ bind(is_false);
    __ li(R3_RET, 0);
    __ stw(R3_RET, 0, tos);
    break;
  case T_BYTE:
  case T_CHAR:
  case T_SHORT:
  case T_INT:
    __ stw(R3_RET, 0, tos);
    break;
  case T_LONG:
    number_of_used_slots = 2;
    // mark unused slot for debugging
    // long goes to topmost slot
    __ std(R3_RET, -BytesPerWord, tos);
    __ li(R3_RET, 0);
    __ std(R3_RET, 0, tos);
    break;
  case T_OBJECT:
    __ verify_oop(R3_RET);
    __ std(R3_RET, 0, tos);
    break;
  case T_FLOAT:
    __ stfs(F1_RET, 0, tos);
    break;
  case T_DOUBLE:
    number_of_used_slots = 2;
    // mark unused slot for debugging
    __ li(R3_RET, 0);
    __ std(R3_RET, 0, tos);
    // double goes to topmost slot
    __ stfd(F1_RET, -BytesPerWord, tos);
    break;
  case T_VOID:
    number_of_used_slots = 0;
    break;
  default:
    ShouldNotReachHere();
  }

  __ BIND(done);

  // new expression stack top
  __ addi(R3_RET, tos, -BytesPerWord * number_of_used_slots);

  __ blr();

  return entry;
}

address CppInterpreterGenerator::generate_stack_to_stack_converter(BasicType type) {
  //
  // Copy the result from the callee's stack to the caller's stack,
  // caller and callee both being interpreted.
  //
  // Registers alive
  //   R3_ARG1        - address of callee's tos + BytesPerWord
  //   R4_ARG2        - address of caller's tos [i.e. free location]
  //   LR
  //
  //   stack grows upwards, memory grows downwards.
  //
  //   [      free         ]  <-- callee's tos
  //   [  optional result  ]  <-- R3_ARG1
  //   [  optional dummy   ]
  //          ...
  //   [      free         ]  <-- caller's tos, R4_ARG2
  //          ...
  // Registers updated
  //   R3_RET(R3_ARG1) - address of caller's new tos
  //
  //   stack grows upwards, memory grows downwards.
  //
  //   [      free         ]  <-- current tos, R3_RET
  //   [  optional result  ]
  //   [  optional dummy   ]
  //          ...
  //

  const Register from = R3_ARG1;
  const Register ret  = R3_ARG1;
  const Register tos  = R4_ARG2;
  const Register tmp1 = R21_tmp1;
  const Register tmp2 = R22_tmp2;

  address entry = __ pc();

  switch (type) {
  case T_BOOLEAN:
  case T_BYTE:
  case T_CHAR:
  case T_SHORT:
  case T_INT:
  case T_FLOAT:
    __ lwz(tmp1, 0, from);
    __ stw(tmp1, 0, tos);
    // New expression stack top.
    __ addi(ret, tos, - BytesPerWord);
    break;
  case T_LONG:
  case T_DOUBLE:
    // Move both entries for debug purposes even though only one is live.
    __ ld(tmp1, BytesPerWord, from);
    __ ld(tmp2, 0, from);
    __ std(tmp1, 0, tos);
    __ std(tmp2, -BytesPerWord, tos);
    // New expression stack top.
    __ addi(ret, tos, - 2 * BytesPerWord); // two slots
    break;
  case T_OBJECT:
    __ ld(tmp1, 0, from);
    __ verify_oop(tmp1);
    __ std(tmp1, 0, tos);
    // New expression stack top.
    __ addi(ret, tos, - BytesPerWord);
    break;
  case T_VOID:
    // New expression stack top.
    __ mr(ret, tos);
    break;
  default:
    ShouldNotReachHere();
  }

  __ blr();

  return entry;
}

address CppInterpreterGenerator::generate_stack_to_native_abi_converter(BasicType type) {
  //
  // Load a result from the callee's stack into the caller's expecting
  // return register, callee being interpreted, caller being call stub
  // or jit code.
  //
  // Registers alive
  //   R3_ARG1   - callee expression tos + BytesPerWord
  //   LR
  //
  //   stack grows upwards, memory grows downwards.
  //
  //   [      free         ]  <-- callee's tos
  //   [  optional result  ]  <-- R3_ARG1
  //   [  optional dummy   ]
  //          ...
  //
  // Registers updated
  //   R3_RET(R3_ARG1)/F1_RET - result
  //

  const Register from = R3_ARG1;
  const Register ret = R3_ARG1;
  const FloatRegister fret = F1_ARG1;

  address entry = __ pc();

  // Implemented uniformly for both kinds of endianness. The interpreter
  // implements boolean, byte, char, and short as jint (4 bytes).
  switch (type) {
  case T_BOOLEAN:
  case T_CHAR:
    // zero extension
    __ lwz(ret, 0, from);
    break;
  case T_BYTE:
  case T_SHORT:
  case T_INT:
    // sign extension
    __ lwa(ret, 0, from);
    break;
  case T_LONG:
    __ ld(ret, 0, from);
    break;
  case T_OBJECT:
    __ ld(ret, 0, from);
    __ verify_oop(ret);
    break;
  case T_FLOAT:
    __ lfs(fret, 0, from);
    break;
  case T_DOUBLE:
    __ lfd(fret, 0, from);
    break;
  case T_VOID:
    break;
  default:
    ShouldNotReachHere();
  }

  __ blr();

  return entry;
}

address CppInterpreter::return_entry(TosState state, int length) {
  assert(interpreter_return_address != NULL, "Not initialized");
  return interpreter_return_address;
}

address CppInterpreter::deopt_entry(TosState state, int length) {
  address ret = NULL;
  if (length != 0) {
    switch (state) {
      case atos: ret = deopt_frame_manager_return_atos; break;
      case btos: ret = deopt_frame_manager_return_itos; break;
      case ctos:
      case stos:
      case itos: ret = deopt_frame_manager_return_itos; break;
      case ltos: ret = deopt_frame_manager_return_ltos; break;
      case ftos: ret = deopt_frame_manager_return_ftos; break;
      case dtos: ret = deopt_frame_manager_return_dtos; break;
      case vtos: ret = deopt_frame_manager_return_vtos; break;
      default: ShouldNotReachHere();
    }
  } else {
    ret = unctrap_frame_manager_entry;  // re-execute the bytecode (e.g. uncommon trap, popframe)
  }
  assert(ret != NULL, "Not initialized");
  return ret;
}

//
// Helpers for commoning out cases in the various type of method entries.
//

//
// Registers alive
//   R16_thread      - JavaThread*
//   R1_SP           - old stack pointer
//   R19_method      - callee's Method
//   R17_tos         - address of caller's tos (prepushed)
//   R15_prev_state  - address of caller's BytecodeInterpreter or 0
//   return_pc in R21_tmp15 (only when called within generate_native_entry)
//
// Registers updated
//   R14_state       - address of callee's interpreter state
//   R1_SP           - new stack pointer
//   CCR4_is_synced  - current method is synchronized
//
void CppInterpreterGenerator::generate_compute_interpreter_state(Label& stack_overflow_return) {
  //
  // Stack layout at this point:
  //
  //   F1      [TOP_IJAVA_FRAME_ABI]              <-- R1_SP
  //           alignment (optional)
  //           [F1's outgoing Java arguments]     <-- R17_tos
  //           ...
  //   F2      [PARENT_IJAVA_FRAME_ABI]
  //            ...

  //=============================================================================
  // Allocate space for locals other than the parameters, the
  // interpreter state, monitors, and the expression stack.

  const Register local_count        = R21_tmp1;
  const Register parameter_count    = R22_tmp2;
  const Register max_stack          = R23_tmp3;
  // Must not be overwritten within this method!
  // const Register return_pc         = R29_tmp9;

  const ConditionRegister is_synced = CCR4_is_synced;
  const ConditionRegister is_native = CCR6;
  const ConditionRegister is_static = CCR7;

  assert(is_synced != is_native, "condition code registers must be distinct");
  assert(is_synced != is_static, "condition code registers must be distinct");
  assert(is_native != is_static, "condition code registers must be distinct");

  {

  // Local registers
  const Register top_frame_size     = R24_tmp4;
  const Register access_flags       = R25_tmp5;
  const Register state_offset       = R26_tmp6;
  Register mem_stack_limit          = R27_tmp7;
  const Register page_size          = R28_tmp8;

  BLOCK_COMMENT("compute_interpreter_state {");

  // access_flags = method->access_flags();
  // TODO: PPC port: assert(4 == methodOopDesc::sz_access_flags(), "unexpected field size");
  __ lwa(access_flags, method_(access_flags));

  // parameter_count = method->constMethod->size_of_parameters();
  // TODO: PPC port: assert(2 == ConstMethod::sz_size_of_parameters(), "unexpected field size");
  __ ld(max_stack, in_bytes(Method::const_offset()), R19_method);   // Max_stack holds constMethod for a while.
  __ lhz(parameter_count, in_bytes(ConstMethod::size_of_parameters_offset()), max_stack);

  // local_count = method->constMethod()->max_locals();
  // TODO: PPC port: assert(2 == ConstMethod::sz_max_locals(), "unexpected field size");
  __ lhz(local_count, in_bytes(ConstMethod::size_of_locals_offset()), max_stack);

  // max_stack = method->constMethod()->max_stack();
  // TODO: PPC port: assert(2 == ConstMethod::sz_max_stack(), "unexpected field size");
  __ lhz(max_stack, in_bytes(ConstMethod::max_stack_offset()), max_stack);

  if (EnableInvokeDynamic) {
    // Take into account 'extra_stack_entries' needed by method handles (see method.hpp).
    __ addi(max_stack, max_stack, Method::extra_stack_entries());
  }

  // mem_stack_limit = thread->stack_limit();
  __ ld(mem_stack_limit, thread_(stack_overflow_limit));

  // Point locals at the first argument. Method's locals are the
  // parameters on top of caller's expression stack.

  // tos points past last Java argument
  __ sldi(R18_locals, parameter_count, Interpreter::logStackElementSize);
  __ add(R18_locals, R17_tos, R18_locals);

  // R18_locals - i*BytesPerWord points to i-th Java local (i starts at 0)

  // Set is_native, is_synced, is_static - will be used later.
  __ testbitdi(is_native, R0, access_flags, JVM_ACC_NATIVE_BIT);
  __ testbitdi(is_synced, R0, access_flags, JVM_ACC_SYNCHRONIZED_BIT);
  assert(is_synced->is_nonvolatile(), "is_synced must be non-volatile");
  __ testbitdi(is_static, R0, access_flags, JVM_ACC_STATIC_BIT);

  // PARENT_IJAVA_FRAME_ABI
  //
  // frame_size =
  //   round_to((local_count - parameter_count)*BytesPerWord +
  //              2*BytesPerWord +
  //              alignment +
  //              frame::interpreter_frame_cinterpreterstate_size_in_bytes()
  //              sizeof(PARENT_IJAVA_FRAME_ABI)
  //              method->is_synchronized() ? sizeof(BasicObjectLock) : 0 +
  //              max_stack*BytesPerWord,
  //            16)
  //
  // Note that this calculation is exactly mirrored by
  // AbstractInterpreter::layout_activation_impl() [ and
  // AbstractInterpreter::size_activation() ]. Which is used by
  // deoptimization so that it can allocate the proper sized
  // frame. This only happens for interpreted frames so the extra
  // notes below about max_stack below are not important. The other
  // thing to note is that for interpreter frames other than the
  // current activation the size of the stack is the size of the live
  // portion of the stack at the particular bcp and NOT the maximum
  // stack that the method might use.
  //
  // If we're calling a native method, we replace max_stack (which is
  // zero) with space for the worst-case signature handler varargs
  // vector, which is:
  //
  //   max_stack = max(Argument::n_register_parameters, parameter_count+2);
  //
  // We add two slots to the parameter_count, one for the jni
  // environment and one for a possible native mirror.  We allocate
  // space for at least the number of ABI registers, even though
  // InterpreterRuntime::slow_signature_handler won't write more than
  // parameter_count+2 words when it creates the varargs vector at the
  // top of the stack.  The generated slow signature handler will just
  // load trash into registers beyond the necessary number.  We're
  // still going to cut the stack back by the ABI register parameter
  // count so as to get SP+16 pointing at the ABI outgoing parameter
  // area, so we need to allocate at least that much even though we're
  // going to throw it away.
  //

  // Adjust max_stack for native methods:
  Label skip_native_calculate_max_stack;
  __ bfalse(is_native, skip_native_calculate_max_stack);
  // if (is_native) {
  //  max_stack = max(Argument::n_register_parameters, parameter_count+2);
  __ addi(max_stack, parameter_count, 2*Interpreter::stackElementWords);
  __ cmpwi(CCR0, max_stack, Argument::n_register_parameters);
  __ bge(CCR0, skip_native_calculate_max_stack);
  __ li(max_stack,  Argument::n_register_parameters);
  // }
  __ bind(skip_native_calculate_max_stack);
  // max_stack is now in bytes
  __ slwi(max_stack, max_stack, Interpreter::logStackElementSize);

  // Calculate number of non-parameter locals (in slots):
  Label not_java;
  __ btrue(is_native, not_java);
  // if (!is_native) {
  //   local_count = non-parameter local count
  __ sub(local_count, local_count, parameter_count);
  // } else {
  //   // nothing to do: method->max_locals() == 0 for native methods
  // }
  __ bind(not_java);


  // Calculate top_frame_size and parent_frame_resize.
  {
  const Register parent_frame_resize = R12_scratch2;

  BLOCK_COMMENT("Compute top_frame_size.");
  // top_frame_size = TOP_IJAVA_FRAME_ABI
  //                  + size of interpreter state
  __ li(top_frame_size, frame::top_ijava_frame_abi_size
                        + frame::interpreter_frame_cinterpreterstate_size_in_bytes());
  //                  + max_stack
  __ add(top_frame_size, top_frame_size, max_stack);
  //                  + stack slots for a BasicObjectLock for synchronized methods
  {
    Label not_synced;
    __ bfalse(is_synced, not_synced);
    __ addi(top_frame_size, top_frame_size, frame::interpreter_frame_monitor_size_in_bytes());
    __ bind(not_synced);
  }
  // align
  __ round_to(top_frame_size, frame::alignment_in_bytes);


  BLOCK_COMMENT("Compute parent_frame_resize.");
  // parent_frame_resize = R1_SP - R17_tos
  __ sub(parent_frame_resize, R1_SP, R17_tos);
  //__ li(parent_frame_resize, 0);
  //                       + PARENT_IJAVA_FRAME_ABI
  //                       + extra two slots for the no-parameter/no-locals
  //                         method result
  __ addi(parent_frame_resize, parent_frame_resize,
                                      frame::parent_ijava_frame_abi_size
                                    + 2*Interpreter::stackElementSize);
  //                       + (locals_count - params_count)
  __ sldi(R0, local_count, Interpreter::logStackElementSize);
  __ add(parent_frame_resize, parent_frame_resize, R0);
  // align
  __ round_to(parent_frame_resize, frame::alignment_in_bytes);

  //
  // Stack layout at this point:
  //
  // The new frame F0 hasn't yet been pushed, F1 is still the top frame.
  //
  //   F0      [TOP_IJAVA_FRAME_ABI]
  //           alignment (optional)
  //           [F0's full operand stack]
  //           [F0's monitors] (optional)
  //           [F0's BytecodeInterpreter object]
  //   F1      [PARENT_IJAVA_FRAME_ABI]
  //           alignment (optional)
  //           [F0's Java result]
  //           [F0's non-arg Java locals]
  //           [F1's outgoing Java arguments]     <-- R17_tos
  //           ...
  //   F2      [PARENT_IJAVA_FRAME_ABI]
  //            ...


  // Calculate new R14_state
  // and
  // test that the new memory stack pointer is above the limit,
  // throw a StackOverflowError otherwise.
  __ sub(R11_scratch1/*F1's SP*/,  R1_SP, parent_frame_resize);
  __ addi(R14_state, R11_scratch1/*F1's SP*/,
              -frame::interpreter_frame_cinterpreterstate_size_in_bytes());
  __ sub(R11_scratch1/*F0's SP*/,
             R11_scratch1/*F1's SP*/, top_frame_size);

  BLOCK_COMMENT("Test for stack overflow:");
  __ cmpld(CCR0/*is_stack_overflow*/, R11_scratch1, mem_stack_limit);
  __ blt(CCR0/*is_stack_overflow*/, stack_overflow_return);


  //=============================================================================
  // Frame_size doesn't overflow the stack. Allocate new frame and
  // initialize interpreter state.

  // Register state
  //
  //   R15            - local_count
  //   R16            - parameter_count
  //   R17            - max_stack
  //
  //   R18            - frame_size
  //   R19            - access_flags
  //   CCR4_is_synced - is_synced
  //
  //   GR_Lstate      - pointer to the uninitialized new BytecodeInterpreter.

  // _last_Java_pc just needs to be close enough that we can identify
  // the frame as an interpreted frame. It does not need to be the
  // exact return address from either calling
  // BytecodeInterpreter::InterpretMethod or the call to a jni native method.
  // So we can initialize it here with a value of a bundle in this
  // code fragment. We only do this initialization for java frames
  // where InterpretMethod needs a a way to get a good pc value to
  // store in the thread state. For interpreter frames used to call
  // jni native code we just zero the value in the state and move an
  // ip as needed in the native entry code.
  //
  // const Register last_Java_pc_addr     = GR24_SCRATCH;  // QQQ 27
  // const Register last_Java_pc          = GR26_SCRATCH;

  // Must reference stack before setting new SP since Windows
  // will not be able to deliver the exception on a bad SP.
  // Windows also insists that we bang each page one at a time in order
  // for the OS to map in the reserved pages. If we bang only
  // the final page, Windows stops delivering exceptions to our
  // VectoredExceptionHandler and terminates our program.
  // Linux only requires a single bang but it's rare to have
  // to bang more than 1 page so the code is enabled for both OS's.

  // BANG THE STACK
  //
  // Nothing to do for PPC, because updating the SP will automatically
  // bang the page.

  // Up to here we have calculated the delta for the new C-frame and
  // checked for a stack-overflow. Now we can savely update SP and
  // resize the C-frame.

  // R14_state has already been calculated.
  __ push_interpreter_frame(top_frame_size, parent_frame_resize,
                            R25_tmp5, R26_tmp6, R27_tmp7, R28_tmp8);

  }

  //
  // Stack layout at this point:
  //
  //   F0 has been been pushed!
  //
  //   F0      [TOP_IJAVA_FRAME_ABI]              <-- R1_SP
  //           alignment (optional)               (now it's here, if required)
  //           [F0's full operand stack]
  //           [F0's monitors] (optional)
  //           [F0's BytecodeInterpreter object]
  //   F1      [PARENT_IJAVA_FRAME_ABI]
  //           alignment (optional)               (now it's here, if required)
  //           [F0's Java result]
  //           [F0's non-arg Java locals]
  //           [F1's outgoing Java arguments]
  //           ...
  //   F2      [PARENT_IJAVA_FRAME_ABI]
  //           ...
  //
  // R14_state points to F0's BytecodeInterpreter object.
  //

  }

  //=============================================================================
  // new BytecodeInterpreter-object is save, let's initialize it:
  BLOCK_COMMENT("New BytecodeInterpreter-object is save.");

  {
  // Locals
  const Register bytecode_addr = R24_tmp4;
  const Register constants     = R25_tmp5;
  const Register tos           = R26_tmp6;
  const Register stack_base    = R27_tmp7;
  const Register local_addr    = R28_tmp8;
  {
    Label L;
    __ btrue(is_native, L);
    // if (!is_native) {
      // bytecode_addr = constMethod->codes();
      __ ld(bytecode_addr, method_(const));
      __ addi(bytecode_addr, bytecode_addr, in_bytes(ConstMethod::codes_offset()));
    // }
    __ bind(L);
  }

  __ ld(constants, in_bytes(Method::const_offset()), R19_method);
  __ ld(constants, in_bytes(ConstMethod::constants_offset()), constants);

  // state->_prev_link = prev_state;
  __ std(R15_prev_state, state_(_prev_link));

  // For assertions only.
  // TODO: not needed anyway because it coincides with `_monitor_base'. remove!
  // state->_self_link = state;
  DEBUG_ONLY(__ std(R14_state, state_(_self_link));)

  // state->_thread = thread;
  __ std(R16_thread, state_(_thread));

  // state->_method = method;
  __ std(R19_method, state_(_method));

  // state->_locals = locals;
  __ std(R18_locals, state_(_locals));

  // state->_oop_temp = NULL;
  __ li(R0, 0);
  __ std(R0, state_(_oop_temp));

  // state->_last_Java_fp = *R1_SP // Use *R1_SP as fp
  __ ld(R0, _abi(callers_sp), R1_SP);
  __ std(R0, state_(_last_Java_fp));

  BLOCK_COMMENT("load Stack base:");
  {
    // Stack_base.
    // if (!method->synchronized()) {
    //   stack_base = state;
    // } else {
    //   stack_base = (uintptr_t)state - sizeof(BasicObjectLock);
    // }
    Label L;
    __ mr(stack_base, R14_state);
    __ bfalse(is_synced, L);
    __ addi(stack_base, stack_base, -frame::interpreter_frame_monitor_size_in_bytes());
    __ bind(L);
  }

  // state->_mdx = NULL;
  __ li(R0, 0);
  __ std(R0, state_(_mdx));

  {
    // if (method->is_native()) state->_bcp = NULL;
    // else state->_bcp = bytecode_addr;
    Label label1, label2;
    __ bfalse(is_native, label1);
    __ std(R0, state_(_bcp));
    __ b(label2);
    __ bind(label1);
    __ std(bytecode_addr, state_(_bcp));
    __ bind(label2);
  }


  // state->_result._to_call._callee = NULL;
  __ std(R0, state_(_result._to_call._callee));

  // state->_monitor_base = state;
  __ std(R14_state, state_(_monitor_base));

  // state->_msg = BytecodeInterpreter::method_entry;
  __ li(R0, BytecodeInterpreter::method_entry);
  __ stw(R0, state_(_msg));

  // state->_last_Java_sp = R1_SP;
  __ std(R1_SP, state_(_last_Java_sp));

  // state->_stack_base = stack_base;
  __ std(stack_base, state_(_stack_base));

  // tos = stack_base - 1 slot (prepushed);
  // state->_stack.Tos(tos);
  __ addi(tos, stack_base, - Interpreter::stackElementSize);
  __ std(tos,  state_(_stack));


  {
    BLOCK_COMMENT("get last_Java_pc:");
    // if (!is_native) state->_last_Java_pc = <some_ip_in_this_code_buffer>;
    // else state->_last_Java_pc = NULL; (just for neatness)
    Label label1, label2;
    __ btrue(is_native, label1);
    __ get_PC_trash_LR(R0);
    __ std(R0, state_(_last_Java_pc));
    __ b(label2);
    __ bind(label1);
    __ li(R0, 0);
    __ std(R0, state_(_last_Java_pc));
    __ bind(label2);
  }


  // stack_limit = tos - max_stack;
  __ sub(R0, tos, max_stack);
  // state->_stack_limit = stack_limit;
  __ std(R0, state_(_stack_limit));


  // cache = method->constants()->cache();
   __ ld(R0, ConstantPool::cache_offset_in_bytes(), constants);
  // state->_constants = method->constants()->cache();
  __ std(R0, state_(_constants));



  //=============================================================================
  // synchronized method, allocate and initialize method object lock.
  // if (!method->is_synchronized()) goto fill_locals_with_0x0s;
  Label fill_locals_with_0x0s;
  __ bfalse(is_synced, fill_locals_with_0x0s);

  //   pool_holder = method->constants()->pool_holder();
  const int mirror_offset = in_bytes(Klass::java_mirror_offset());
  {
    Label label1, label2;
    // lockee = NULL; for java methods, correct value will be inserted in BytecodeInterpretMethod.hpp
    __ li(R0,0);
    __ bfalse(is_native, label2);

    __ bfalse(is_static, label1);
    // if (method->is_static()) lockee =
    // pool_holder->klass_part()->java_mirror();
    __ ld(R11_scratch1/*pool_holder*/, ConstantPool::pool_holder_offset_in_bytes(), constants);
    __ ld(R0/*lockee*/, mirror_offset, R11_scratch1/*pool_holder*/);
    __ b(label2);

    __ bind(label1);
    // else lockee = *(oop*)locals;
    __ ld(R0/*lockee*/, 0, R18_locals);
    __ bind(label2);

    // monitor->set_obj(lockee);
    __ std(R0/*lockee*/, BasicObjectLock::obj_offset_in_bytes(), stack_base);
  }

  // See if we need to zero the locals
  __ BIND(fill_locals_with_0x0s);


  //=============================================================================
  // fill locals with 0x0s
  Label locals_zeroed;
  __ btrue(is_native, locals_zeroed);

  if (true /* zerolocals */ || ClearInterpreterLocals) {
    // local_count is already num_locals_slots - num_param_slots
    __ sldi(R0, parameter_count, Interpreter::logStackElementSize);
    __ sub(local_addr, R18_locals, R0);
    __ cmpdi(CCR0, local_count, 0);
    __ ble(CCR0, locals_zeroed);

    __ mtctr(local_count);
    //__ ld_const_addr(R0, (address) 0xcafe0000babe);
    __ li(R0, 0);

    Label zero_slot;
    __ bind(zero_slot);

    // first local is at local_addr
    __ std(R0, 0, local_addr);
    __ addi(local_addr, local_addr, -BytesPerWord);
    __ bdnz(zero_slot);
  }

   __ BIND(locals_zeroed);

  }
  BLOCK_COMMENT("} compute_interpreter_state");
}

// Generate code to initiate compilation on invocation counter overflow.
void CppInterpreterGenerator::generate_counter_overflow(Label& continue_entry) {
  // Registers alive
  //   R14_state
  //   R16_thread
  //
  // Registers updated
  //   R14_state
  //   R3_ARG1 (=R3_RET)
  //   R4_ARG2

  // After entering the vm we remove the activation and retry the
  // entry point in case the compilation is complete.

  // InterpreterRuntime::frequency_counter_overflow takes one argument
  // that indicates if the counter overflow occurs at a backwards
  // branch (NULL bcp). We pass zero. The call returns the address
  // of the verified entry point for the method or NULL if the
  // compilation did not complete (either went background or bailed
  // out).
  __ li(R4_ARG2, 0);

  // Pass false to call_VM so it doesn't check for pending exceptions,
  // since at this point in the method invocation the exception
  // handler would try to exit the monitor of synchronized methods
  // which haven't been entered yet.
  //
  // Returns verified_entry_point or NULL, we don't care which.
  //
  // Do not use the variant `frequency_counter_overflow' that returns
  // a structure, because this will change the argument list by a
  // hidden parameter (gcc 4.1).

  __ call_VM(noreg,
             CAST_FROM_FN_PTR(address, InterpreterRuntime::frequency_counter_overflow),
             R4_ARG2,
             false);
  // Returns verified_entry_point or NULL, we don't care which as we ignore it
  // and run interpreted.

  // Reload method, it may have moved.
  __ ld(R19_method, state_(_method));

  // We jump now to the label "continue_after_compile".
  __ b(continue_entry);
}

// Increment invocation count and check for overflow.
//
// R19_method must contain Method* of method to profile.
void CppInterpreterGenerator::generate_counter_incr(Label& overflow) {
  Label done;
  const Register Rcounters             = R12_scratch2;
  const Register iv_be_count           = R11_scratch1;
  const Register invocation_limit      = R12_scratch2;
  const Register invocation_limit_addr = invocation_limit;

  // Load and ev. allocate MethodCounters object.
  __ get_method_counters(R19_method, Rcounters, done);

  // Update standard invocation counters.
  __ increment_invocation_counter(Rcounters, iv_be_count, R0);

  // Compare against limit.
  BLOCK_COMMENT("Compare counter against limit:");
  assert(4 == sizeof(InvocationCounter::InterpreterInvocationLimit),
         "must be 4 bytes");
  __ load_const(invocation_limit_addr, (address)&InvocationCounter::InterpreterInvocationLimit);
  __ lwa(invocation_limit, 0, invocation_limit_addr);
  __ cmpw(CCR0, iv_be_count, invocation_limit);
  __ bge(CCR0, overflow);
  __ bind(done);
}

//
// Call a JNI method.
//
// Interpreter stub for calling a native method. (C++ interpreter)
// This sets up a somewhat different looking stack for calling the native method
// than the typical interpreter frame setup.
//
address CppInterpreterGenerator::generate_native_entry(void) {
  if (native_entry != NULL) return native_entry;
  address entry = __ pc();

  // Read
  //   R16_thread
  //   R15_prev_state  - address of caller's BytecodeInterpreter, if this snippet
  //                     gets called by the frame manager.
  //   R19_method      - callee's Method
  //   R17_tos         - address of caller's tos
  //   R1_SP           - caller's stack pointer
  //   R21_sender_SP   - initial caller sp
  //
  // Update
  //   R14_state       - address of caller's BytecodeInterpreter
  //   R3_RET          - integer result, if any.
  //   F1_RET          - float result, if any.
  //
  //
  // Stack layout at this point:
  //
  //    0       [TOP_IJAVA_FRAME_ABI]         <-- R1_SP
  //            alignment (optional)
  //            [outgoing Java arguments]     <-- R17_tos
  //            ...
  //    PARENT  [PARENT_IJAVA_FRAME_ABI]
  //            ...
  //

  const bool inc_counter = UseCompiler || CountCompiledCalls;

  const Register signature_handler_fd   = R21_tmp1;
  const Register pending_exception      = R22_tmp2;
  const Register result_handler_addr    = R23_tmp3;
  const Register native_method_fd       = R24_tmp4;
  const Register access_flags           = R25_tmp5;
  const Register active_handles         = R26_tmp6;
  const Register sync_state             = R27_tmp7;
  const Register sync_state_addr        = sync_state;     // Address is dead after use.
  const Register suspend_flags          = R24_tmp4;

  const Register return_pc              = R28_tmp8;       // Register will be locked for some time.

  const ConditionRegister is_synced     = CCR4_is_synced; // Live-on-exit from compute_interpreter_state.


  // R1_SP still points to caller's SP at this point.

  // Save initial_caller_sp to caller's abi. The caller frame must be
  // resized before returning to get rid of the c2i arguments (if
  // any).
  // Override the saved SP with the senderSP so we can pop c2i
  // arguments (if any) off when we return
  __ std(R21_sender_SP, _top_ijava_frame_abi(initial_caller_sp), R1_SP);

  // Save LR to caller's frame. We don't use _abi(lr) here, because it is not safe.
  __ mflr(return_pc);
  __ std(return_pc, _top_ijava_frame_abi(frame_manager_lr), R1_SP);

  assert(return_pc->is_nonvolatile(), "return_pc must be a non-volatile register");

  __ verify_method_ptr(R19_method);

  //=============================================================================

  // If this snippet gets called by the frame manager (at label
  // `call_special'), then R15_prev_state is valid. If this snippet
  // is not called by the frame manager, but e.g. by the call stub or
  // by compiled code, then R15_prev_state is invalid.
  {
    // Set R15_prev_state to 0 if we don't return to the frame
    // manager; we will return to the call_stub or to compiled code
    // instead. If R15_prev_state is 0 there will be only one
    // interpreter frame (we will set this up later) in this C frame!
    // So we must take care about retrieving prev_state_(_prev_link)
    // and restoring R1_SP when popping that interpreter.
    Label prev_state_is_valid;

    __ load_const(R11_scratch1/*frame_manager_returnpc_addr*/, (address)&frame_manager_specialized_return);
    __ ld(R12_scratch2/*frame_manager_returnpc*/, 0, R11_scratch1/*frame_manager_returnpc_addr*/);
    __ cmpd(CCR0, return_pc, R12_scratch2/*frame_manager_returnpc*/);
    __ beq(CCR0, prev_state_is_valid);

    __ li(R15_prev_state, 0);

    __ BIND(prev_state_is_valid);
  }

  //=============================================================================
  // Allocate new frame and initialize interpreter state.

  Label exception_return;
  Label exception_return_sync_check;
  Label stack_overflow_return;

  // Generate new interpreter state and jump to stack_overflow_return in case of
  // a stack overflow.
  generate_compute_interpreter_state(stack_overflow_return);

  //=============================================================================
  // Increment invocation counter. On overflow, entry to JNI method
  // will be compiled.
  Label invocation_counter_overflow;
  if (inc_counter) {
    generate_counter_incr(invocation_counter_overflow);
  }

  Label continue_after_compile;
  __ BIND(continue_after_compile);

  // access_flags = method->access_flags();
  // Load access flags.
  assert(access_flags->is_nonvolatile(),
         "access_flags must be in a non-volatile register");
  // Type check.
  // TODO: PPC port: assert(4 == methodOopDesc::sz_access_flags(), "unexpected field size");
  __ lwz(access_flags, method_(access_flags));

  // We don't want to reload R19_method and access_flags after calls
  // to some helper functions.
  assert(R19_method->is_nonvolatile(), "R19_method must be a non-volatile register");

  // Check for synchronized methods. Must happen AFTER invocation counter
  // check, so method is not locked if counter overflows.

  {
    Label method_is_not_synced;
    // Is_synced is still alive.
    assert(is_synced->is_nonvolatile(), "is_synced must be non-volatile");
    __ bfalse(is_synced, method_is_not_synced);

    lock_method();
    // Reload method, it may have moved.
    __ ld(R19_method, state_(_method));

    __ BIND(method_is_not_synced);
  }

  // jvmti/jvmpi support
  __ notify_method_entry();

  // Reload method, it may have moved.
  __ ld(R19_method, state_(_method));

  //=============================================================================
  // Get and call the signature handler

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
  __ bne(CCR0, exception_return_sync_check); // has pending exception

  // reload method
  __ ld(R19_method, state_(_method));

  // Reload signature handler, it may have been created/assigned in the meanwhile
  __ ld(signature_handler_fd, method_(signature_handler));

  __ BIND(call_signature_handler);

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
  // (outgoing C args), R3_ARG1 to R10_ARG8, and F1_ARG1 to
  // F13_ARG13.
  __ mr(R3_ARG1, R18_locals);
  __ ld(signature_handler_fd, 0, signature_handler_fd);
  __ call_stub(signature_handler_fd);
  // reload method
  __ ld(R19_method, state_(_method));

  // Remove the register parameter varargs slots we allocated in
  // compute_interpreter_state. SP+16 ends up pointing to the ABI
  // outgoing argument area.
  //
  // Not needed on PPC64.
  //__ add(SP, SP, Argument::n_register_parameters*BytesPerWord);

  assert(result_handler_addr->is_nonvolatile(), "result_handler_addr must be in a non-volatile register");
  // Save across call to native method.
  __ mr(result_handler_addr, R3_RET);

  // Set up fixed parameters and call the native method.
  // If the method is static, get mirror into R4_ARG2.

  {
    Label method_is_not_static;
    // access_flags is non-volatile and still, no need to restore it

    // restore access flags
    __ testbitdi(CCR0, R0, access_flags, JVM_ACC_STATIC_BIT);
    __ bfalse(CCR0, method_is_not_static);

    // constants = method->constants();
    __ ld(R11_scratch1, in_bytes(Method::const_offset()), R19_method);
    __ ld(R11_scratch1/*constants*/, in_bytes(ConstMethod::constants_offset()), R11_scratch1);
    // pool_holder = method->constants()->pool_holder();
    __ ld(R11_scratch1/*pool_holder*/, ConstantPool::pool_holder_offset_in_bytes(),
          R11_scratch1/*constants*/);

    const int mirror_offset = in_bytes(Klass::java_mirror_offset());

    // mirror = pool_holder->klass_part()->java_mirror();
    __ ld(R0/*mirror*/, mirror_offset, R11_scratch1/*pool_holder*/);
    // state->_native_mirror = mirror;
    __ std(R0/*mirror*/, state_(_oop_temp));
    // R4_ARG2 = &state->_oop_temp;
    __ addir(R4_ARG2, state_(_oop_temp));

    __ BIND(method_is_not_static);
  }

  // At this point, arguments have been copied off the stack into
  // their JNI positions. Oops are boxed in-place on the stack, with
  // handles copied to arguments. The result handler address is in a
  // register.

  // pass JNIEnv address as first parameter
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

  // TODO: PPC port: assert(4 == JavaThread::sz_thread_state(), "unexpected field size");
  __ stw(R0, thread_(thread_state));

  if (UseMembar) {
    __ fence();
  }

  //=============================================================================
  // Call the native method. Argument registers must not have been
  // overwritten since "__ call_stub(signature_handler);" (except for
  // ARG1 and ARG2 for static methods)
  __ call_c(native_method_fd);

  __ std(R3_RET, state_(_native_lresult));
  __ stfd(F1_RET, state_(_native_fresult));

  // The frame_manager_lr field, which we use for setting the last
  // java frame, gets overwritten by the signature handler. Restore
  // it now.
  __ get_PC_trash_LR(R11_scratch1);
  __ std(R11_scratch1, _top_ijava_frame_abi(frame_manager_lr), R1_SP);

  // Because of GC R19_method may no longer be valid.

  // Block, if necessary, before resuming in _thread_in_Java state.
  // In order for GC to work, don't clear the last_Java_sp until after
  // blocking.



  //=============================================================================
  // Switch thread to "native transition" state before reading the
  // synchronization state.  This additional state is necessary
  // because reading and testing the synchronization state is not
  // atomic w.r.t. GC, as this scenario demonstrates: Java thread A,
  // in _thread_in_native state, loads _not_synchronized and is
  // preempted.  VM thread changes sync state to synchronizing and
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
  // sync_state is declared to be volatile, so we do it anyway.
  __ load_const(sync_state_addr, SafepointSynchronize::address_of_state());

  // TODO: PPC port: assert(4 == SafepointSynchronize::sz_state(), "unexpected field size");
  __ lwz(sync_state, 0, sync_state_addr);

  // TODO: PPC port: assert(4 == Thread::sz_suspend_flags(), "unexpected field size");
  __ lwz(suspend_flags, thread_(suspend_flags));

  __ acquire();

  Label sync_check_done;
  Label do_safepoint;
  // No synchronization in progress nor yet synchronized
  __ cmpwi(CCR0, sync_state, SafepointSynchronize::_not_synchronized);
  // not suspended
  __ cmpwi(CCR1, suspend_flags, 0);

  __ bne(CCR0, do_safepoint);
  __ beq(CCR1, sync_check_done);
  __ bind(do_safepoint);
  // Block.  We do the call directly and leave the current
  // last_Java_frame setup undisturbed.  We must save any possible
  // native result acrosss the call. No oop is present

  __ mr(R3_ARG1, R16_thread);
  __ call_c(CAST_FROM_FN_PTR(FunctionDescriptor*, JavaThread::check_special_condition_for_native_trans),
            relocInfo::none);
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
  // back in Java

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

  // Reload GR27_method, call killed it. We can't look at
  // state->_method until we're back in java state because in java
  // state gc can't happen until we get to a safepoint.
  //
  // We've set thread_state to _thread_in_Java already, so restoring
  // R19_method from R14_state works; R19_method is invalid, because
  // GC may have happened.
  __ ld(R19_method, state_(_method)); // reload method, may have moved

  // jvmdi/jvmpi support. Whether we've got an exception pending or
  // not, and whether unlocking throws an exception or not, we notify
  // on native method exit. If we do have an exception, we'll end up
  // in the caller's context to handle it, so if we don't do the
  // notify here, we'll drop it on the floor.

  __ notify_method_exit(true/*native method*/,
                        ilgl /*illegal state (not used for native methods)*/);



  //=============================================================================
  // Handle exceptions

  // See if we must unlock.
  //
  {
    Label method_is_not_synced;
    // is_synced is still alive
    assert(is_synced->is_nonvolatile(), "is_synced must be non-volatile");
    __ bfalse(is_synced, method_is_not_synced);

    unlock_method();

    __ bind(method_is_not_synced);
  }

  // Reset active handles after returning from native.
  // thread->active_handles()->clear();
  __ ld(active_handles, thread_(active_handles));
  // JNIHandleBlock::_top is an int.
  // TODO:  PPC port: assert(4 == JNIHandleBlock::top_size_in_bytes(), "unexpected field size");
  __ li(R0, 0);
  __ stw(R0, JNIHandleBlock::top_offset_in_bytes(), active_handles);

  Label no_pending_exception_from_native_method;
  __ ld(R0/*pending_exception*/, thread_(pending_exception));
  __ cmpdi(CCR0, R0/*pending_exception*/, 0);
  __ beq(CCR0, no_pending_exception_from_native_method);


  //-----------------------------------------------------------------------------
  // An exception is pending. We call into the runtime only if the
  // caller was not interpreted. If it was interpreted the
  // interpreter will do the correct thing. If it isn't interpreted
  // (call stub/compiled code) we will change our return and continue.
  __ BIND(exception_return);

  Label return_to_initial_caller_with_pending_exception;
  __ cmpdi(CCR0, R15_prev_state, 0);
  __ beq(CCR0, return_to_initial_caller_with_pending_exception);

  // We are returning to an interpreter activation, just pop the state,
  // pop our frame, leave the exception pending, and return.
  __ pop_interpreter_state(/*prev_state_may_be_0=*/false);
  __ pop_interpreter_frame(R11_scratch1, R12_scratch2, R21_tmp1 /* set to return pc */, R22_tmp2);
  __ mtlr(R21_tmp1);
  __ blr();

  __ BIND(exception_return_sync_check);

  assert(is_synced->is_nonvolatile(), "is_synced must be non-volatile");
  __ bfalse(is_synced, exception_return);
  unlock_method();
  __ b(exception_return);


  __ BIND(return_to_initial_caller_with_pending_exception);
  // We are returning to a c2i-adapter / call-stub, get the address of the
  // exception handler, pop the frame and return to the handler.

  // First, pop to caller's frame.
  __ pop_interpreter_frame(R11_scratch1, R12_scratch2, R21_tmp1  /* set to return pc */, R22_tmp2);

  __ push_frame_abi112(0, R11_scratch1);
  // Get the address of the exception handler.
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address),
                  R16_thread,
                  R21_tmp1 /* return pc */);
  __ pop_frame();

  // Load the PC of the the exception handler into LR.
  __ mtlr(R3_RET);

  // Load exception into R3_ARG1 and clear pending exception in thread.
  __ ld(R3_ARG1/*exception*/, thread_(pending_exception));
  __ li(R4_ARG2, 0);
  __ std(R4_ARG2, thread_(pending_exception));

  // Load the original return pc into R4_ARG2.
  __ mr(R4_ARG2/*issuing_pc*/, R21_tmp1);

  // Resize frame to get rid of a potential extension.
  __ resize_frame_to_initial_caller(R11_scratch1, R12_scratch2);

  // Return to exception handler.
  __ blr();


  //-----------------------------------------------------------------------------
  // No exception pending.
  __ BIND(no_pending_exception_from_native_method);

  // Move native method result back into proper registers and return.
  // Invoke result handler (may unbox/promote).
  __ ld(R3_RET, state_(_native_lresult));
  __ lfd(F1_RET, state_(_native_fresult));
  __ call_stub(result_handler_addr);

  // We have created a new BytecodeInterpreter object, now we must destroy it.
  //
  // Restore previous R14_state and caller's SP.  R15_prev_state may
  // be 0 here, because our caller may be the call_stub or compiled
  // code.
  __ pop_interpreter_state(/*prev_state_may_be_0=*/true);
  __ pop_interpreter_frame(R11_scratch1, R12_scratch2, R21_tmp1 /* set to return pc */, R22_tmp2);
  // Resize frame to get rid of a potential extension.
  __ resize_frame_to_initial_caller(R11_scratch1, R12_scratch2);

  // Must use the return pc which was loaded from the caller's frame
  // as the VM uses return-pc-patching for deoptimization.
  __ mtlr(R21_tmp1);
  __ blr();



  //=============================================================================
  // We encountered an exception while computing the interpreter
  // state, so R14_state isn't valid. Act as if we just returned from
  // the callee method with a pending exception.
  __ BIND(stack_overflow_return);

  //
  // Register state:
  //   R14_state         invalid; trashed by compute_interpreter_state
  //   R15_prev_state    valid, but may be 0
  //
  //   R1_SP             valid, points to caller's SP; wasn't yet updated by
  //                     compute_interpreter_state
  //

  // Create exception oop and make it pending.

  // Throw the exception via RuntimeStub "throw_StackOverflowError_entry".
  //
  // Previously, we called C-Code directly. As a consequence, a
  // possible GC tried to process the argument oops of the top frame
  // (see RegisterMap::clear, which sets the corresponding flag to
  // true). This lead to crashes because:
  //   1. The top register map did not contain locations for the argument registers
  //   2. The arguments are dead anyway, could be already overwritten in the worst case
  // Solution: Call via special runtime stub that pushes it's own
  // frame. This runtime stub has the flag "CodeBlob::caller_must_gc_arguments()"
  // set to "false", what prevents the dead arguments getting GC'd.
  //
  // 2 cases exist:
  // 1. We were called by the c2i adapter / call stub
  // 2. We were called by the frame manager
  //
  // Both cases are handled by this code:
  // 1. - initial_caller_sp was saved in both cases on entry, so it's safe to load it back even if it was not changed.
  //    - control flow will be:
  //      throw_stackoverflow_stub->VM->throw_stackoverflow_stub->forward_excep->excp_blob of caller method
  // 2. - control flow will be:
  //      throw_stackoverflow_stub->VM->throw_stackoverflow_stub->forward_excep->rethrow_excp_entry of frame manager->resume_method
  //      Since we restored the caller SP above, the rethrow_excp_entry can restore the original interpreter state
  //      registers using the stack and resume the calling method with a pending excp.

  // Pop any c2i extension from the stack, restore LR just to be sure
  __ ld(R0, _top_ijava_frame_abi(frame_manager_lr), R1_SP);
  __ mtlr(R0);
  // Resize frame to get rid of a potential extension.
  __ resize_frame_to_initial_caller(R11_scratch1, R12_scratch2);

  // Load target address of the runtime stub.
  __ load_const(R12_scratch2, (StubRoutines::throw_StackOverflowError_entry()));
  __ mtctr(R12_scratch2);
  __ bctr();


  //=============================================================================
  // Counter overflow.

  if (inc_counter) {
    // Handle invocation counter overflow
    __ bind(invocation_counter_overflow);

    generate_counter_overflow(continue_after_compile);
  }

  native_entry = entry;
  return entry;
}

bool AbstractInterpreter::can_be_compiled(methodHandle m) {
  // No special entry points that preclude compilation.
  return true;
}

// Unlock the current method.
//
void CppInterpreterGenerator::unlock_method(void) {
  // Find preallocated monitor and unlock method. Method monitor is
  // the first one.

  // Registers alive
  //   R14_state
  //
  // Registers updated
  //   volatiles
  //
  const Register monitor = R4_ARG2;

  // Pass address of initial monitor we allocated.
  //
  // First monitor.
  __ addi(monitor, R14_state, -frame::interpreter_frame_monitor_size_in_bytes());

  // Unlock method
  __ unlock_object(monitor);
}

// Lock the current method.
//
void CppInterpreterGenerator::lock_method(void) {
  // Find preallocated monitor and lock method. Method monitor is the
  // first one.

  //
  // Registers alive
  //   R14_state
  //
  // Registers updated
  //   volatiles
  //

  const Register monitor = R4_ARG2;
  const Register object  = R5_ARG3;

  // Pass address of initial monitor we allocated.
  __ addi(monitor, R14_state, -frame::interpreter_frame_monitor_size_in_bytes());

  // Pass object address.
  __ ld(object, BasicObjectLock::obj_offset_in_bytes(), monitor);

  // Lock method.
  __ lock_object(monitor, object);
}

// Generate code for handling resuming a deopted method.
void CppInterpreterGenerator::generate_deopt_handling(Register result_index) {

  //=============================================================================
  // Returning from a compiled method into a deopted method. The
  // bytecode at the bcp has completed. The result of the bytecode is
  // in the native abi (the tosca for the template based
  // interpreter). Any stack space that was used by the bytecode that
  // has completed has been removed (e.g. parameters for an invoke) so
  // all that we have to do is place any pending result on the
  // expression stack and resume execution on the next bytecode.

  Label return_from_deopt_common;

  // R3_RET and F1_RET are live here! Load the array index of the
  // required result stub address and continue at return_from_deopt_common.

  // Deopt needs to jump to here to enter the interpreter (return a result).
  deopt_frame_manager_return_atos = __ pc();
  __ li(result_index, AbstractInterpreter::BasicType_as_index(T_OBJECT));
  __ b(return_from_deopt_common);

  deopt_frame_manager_return_btos = __ pc();
  __ li(result_index, AbstractInterpreter::BasicType_as_index(T_BOOLEAN));
  __ b(return_from_deopt_common);

  deopt_frame_manager_return_itos = __ pc();
  __ li(result_index, AbstractInterpreter::BasicType_as_index(T_INT));
  __ b(return_from_deopt_common);

  deopt_frame_manager_return_ltos = __ pc();
  __ li(result_index, AbstractInterpreter::BasicType_as_index(T_LONG));
  __ b(return_from_deopt_common);

  deopt_frame_manager_return_ftos = __ pc();
  __ li(result_index, AbstractInterpreter::BasicType_as_index(T_FLOAT));
  __ b(return_from_deopt_common);

  deopt_frame_manager_return_dtos = __ pc();
  __ li(result_index, AbstractInterpreter::BasicType_as_index(T_DOUBLE));
  __ b(return_from_deopt_common);

  deopt_frame_manager_return_vtos = __ pc();
  __ li(result_index, AbstractInterpreter::BasicType_as_index(T_VOID));
  // Last one, fall-through to return_from_deopt_common.

  // Deopt return common. An index is present that lets us move any
  // possible result being return to the interpreter's stack.
  //
  __ BIND(return_from_deopt_common);

}

// Generate the code to handle a more_monitors message from the c++ interpreter.
void CppInterpreterGenerator::generate_more_monitors() {

  //
  // Registers alive
  //   R16_thread      - JavaThread*
  //   R15_prev_state  - previous BytecodeInterpreter or 0
  //   R14_state       - BytecodeInterpreter* address of receiver's interpreter state
  //   R1_SP           - old stack pointer
  //
  // Registers updated
  //   R1_SP          - new stack pointer
  //

  // Very-local scratch registers.
  const Register old_tos         = R21_tmp1;
  const Register new_tos         = R22_tmp2;
  const Register stack_base      = R23_tmp3;
  const Register stack_limit     = R24_tmp4;
  const Register slot            = R25_tmp5;
  const Register n_slots         = R25_tmp5;

  // Interpreter state fields.
  const Register msg             = R24_tmp4;

  // Load up relevant interpreter state.

  __ ld(stack_base, state_(_stack_base));                // Old stack_base
  __ ld(old_tos, state_(_stack));                        // Old tos
  __ ld(stack_limit, state_(_stack_limit));              // Old stack_limit

  // extracted monitor_size
  int monitor_size = frame::interpreter_frame_monitor_size_in_bytes();
  assert(Assembler::is_aligned((unsigned int)monitor_size,
                               (unsigned int)frame::alignment_in_bytes),
         "size of a monitor must respect alignment of SP");

  // Save and restore top LR
  __ ld(R12_scratch2, _top_ijava_frame_abi(frame_manager_lr), R1_SP);
  __ resize_frame(-monitor_size, R11_scratch1);// Allocate space for new monitor
  __ std(R12_scratch2, _top_ijava_frame_abi(frame_manager_lr), R1_SP);
    // Initial_caller_sp is used as unextended_sp for non initial callers.
  __ std(R1_SP, _top_ijava_frame_abi(initial_caller_sp), R1_SP);
  __ addi(stack_base, stack_base, -monitor_size);        // New stack_base
  __ addi(new_tos, old_tos, -monitor_size);              // New tos
  __ addi(stack_limit, stack_limit, -monitor_size);      // New stack_limit

  __ std(R1_SP, state_(_last_Java_sp));                  // Update frame_bottom

  __ std(stack_base, state_(_stack_base));               // Update stack_base
  __ std(new_tos, state_(_stack));                       // Update tos
  __ std(stack_limit, state_(_stack_limit));             // Update stack_limit

  __ li(msg, BytecodeInterpreter::got_monitors);         // Tell interpreter we allocated the lock
  __ stw(msg, state_(_msg));

  // Shuffle expression stack down. Recall that stack_base points
  // just above the new expression stack bottom. Old_tos and new_tos
  // are used to scan thru the old and new expression stacks.

  Label copy_slot, copy_slot_finished;
  __ sub(n_slots, stack_base, new_tos);
  __ srdi_(n_slots, n_slots, LogBytesPerWord);           // compute number of slots to copy
  assert(LogBytesPerWord == 3, "conflicts assembler instructions");
  __ beq(CCR0, copy_slot_finished);                       // nothing to copy

  __ mtctr(n_slots);

  // loop
  __ bind(copy_slot);
  __ ldu(slot, BytesPerWord, old_tos);                   // slot = *++old_tos;
  __ stdu(slot, BytesPerWord, new_tos);                  // *++new_tos = slot;
  __ bdnz(copy_slot);

  __ bind(copy_slot_finished);

  // Restart interpreter
  __ li(R0, 0);
  __ std(R0, BasicObjectLock::obj_offset_in_bytes(), stack_base);  // Mark lock as unused
}

address CppInterpreterGenerator::generate_normal_entry(void) {
  if (interpreter_frame_manager != NULL) return interpreter_frame_manager;

  address entry = __ pc();

  address return_from_native_pc = (address) NULL;

  // Initial entry to frame manager (from call_stub or c2i_adapter)

  //
  // Registers alive
  //   R16_thread               - JavaThread*
  //   R19_method               - callee's Method (method to be invoked)
  //   R17_tos                  - address of sender tos (prepushed)
  //   R1_SP                    - SP prepared by call stub such that caller's outgoing args are near top
  //   LR                       - return address to caller (call_stub or c2i_adapter)
  //   R21_sender_SP            - initial caller sp
  //
  // Registers updated
  //   R15_prev_state           - 0
  //
  // Stack layout at this point:
  //
  //   0       [TOP_IJAVA_FRAME_ABI]         <-- R1_SP
  //           alignment (optional)
  //           [outgoing Java arguments]     <-- R17_tos
  //           ...
  //   PARENT  [PARENT_IJAVA_FRAME_ABI]
  //           ...
  //

  // Save initial_caller_sp to caller's abi.
  // The caller frame must be resized before returning to get rid of
  // the c2i part on top of the calling compiled frame (if any).
  // R21_tmp1 must match sender_sp in gen_c2i_adapter.
  // Now override the saved SP with the senderSP so we can pop c2i
  // arguments (if any) off when we return.
  __ std(R21_sender_SP, _top_ijava_frame_abi(initial_caller_sp), R1_SP);

  // Save LR to caller's frame. We don't use _abi(lr) here,
  // because it is not safe.
  __ mflr(R0);
  __ std(R0, _top_ijava_frame_abi(frame_manager_lr), R1_SP);

  // If we come here, it is the first invocation of the frame manager.
  // So there is no previous interpreter state.
  __ li(R15_prev_state, 0);


  // Fall through to where "recursive" invocations go.

  //=============================================================================
  // Dispatch an instance of the interpreter. Recursive activations
  // come here.

  Label re_dispatch;
  __ BIND(re_dispatch);

  //
  // Registers alive
  //    R16_thread        - JavaThread*
  //    R19_method        - callee's Method
  //    R17_tos           - address of caller's tos (prepushed)
  //    R15_prev_state    - address of caller's BytecodeInterpreter or 0
  //    R1_SP             - caller's SP trimmed such that caller's outgoing args are near top.
  //
  // Stack layout at this point:
  //
  //   0       [TOP_IJAVA_FRAME_ABI]
  //           alignment (optional)
  //           [outgoing Java arguments]
  //           ...
  //   PARENT  [PARENT_IJAVA_FRAME_ABI]
  //           ...

  // fall through to interpreted execution

  //=============================================================================
  // Allocate a new Java frame and initialize the new interpreter state.

  Label stack_overflow_return;

  // Create a suitable new Java frame plus a new BytecodeInterpreter instance
  // in the current (frame manager's) C frame.
  generate_compute_interpreter_state(stack_overflow_return);

  // fall through

  //=============================================================================
  // Interpreter dispatch.

  Label call_interpreter;
  __ BIND(call_interpreter);

  //
  // Registers alive
  //   R16_thread       - JavaThread*
  //   R15_prev_state   - previous BytecodeInterpreter or 0
  //   R14_state        - address of receiver's BytecodeInterpreter
  //   R1_SP            - receiver's stack pointer
  //

  // Thread fields.
  const Register pending_exception = R21_tmp1;

  // Interpreter state fields.
  const Register msg               = R24_tmp4;

  // MethodOop fields.
  const Register parameter_count   = R25_tmp5;
  const Register result_index      = R26_tmp6;

  const Register dummy             = R28_tmp8;

  // Address of various interpreter stubs.
  // R29_tmp9 is reserved.
  const Register stub_addr         = R27_tmp7;

  // Uncommon trap needs to jump to here to enter the interpreter
  // (re-execute current bytecode).
  unctrap_frame_manager_entry  = __ pc();

  // If we are profiling, store our fp (BSP) in the thread so we can
  // find it during a tick.
  if (Arguments::has_profile()) {
    // On PPC64 we store the pointer to the current BytecodeInterpreter,
    // instead of the bsp of ia64. This should suffice to be able to
    // find all interesting information.
    __ std(R14_state, thread_(last_interpreter_fp));
  }

  // R16_thread, R14_state and R15_prev_state are nonvolatile
  // registers. There is no need to save these. If we needed to save
  // some state in the current Java frame, this could be a place to do
  // so.

  // Call Java bytecode dispatcher passing "BytecodeInterpreter* istate".
  __ call_VM_leaf(CAST_FROM_FN_PTR(address,
                                   JvmtiExport::can_post_interpreter_events()
                                   ? BytecodeInterpreter::runWithChecks
                                   : BytecodeInterpreter::run),
                  R14_state);

  interpreter_return_address  = __ last_calls_return_pc();

  // R16_thread, R14_state and R15_prev_state have their values preserved.

  // If we are profiling, clear the fp in the thread to tell
  // the profiler that we are no longer in the interpreter.
  if (Arguments::has_profile()) {
    __ li(R11_scratch1, 0);
    __ std(R11_scratch1, thread_(last_interpreter_fp));
  }

  // Load message from bytecode dispatcher.
  // TODO: PPC port: guarantee(4 == BytecodeInterpreter::sz_msg(), "unexpected field size");
  __ lwz(msg, state_(_msg));


  Label more_monitors;
  Label return_from_native;
  Label return_from_native_common;
  Label return_from_native_no_exception;
  Label return_from_interpreted_method;
  Label return_from_recursive_activation;
  Label unwind_recursive_activation;
  Label resume_interpreter;
  Label return_to_initial_caller;
  Label unwind_initial_activation;
  Label unwind_initial_activation_pending_exception;
  Label call_method;
  Label call_special;
  Label retry_method;
  Label retry_method_osr;
  Label popping_frame;
  Label throwing_exception;

  // Branch according to the received message

  __ cmpwi(CCR1, msg, BytecodeInterpreter::call_method);
  __ cmpwi(CCR2, msg, BytecodeInterpreter::return_from_method);

  __ beq(CCR1, call_method);
  __ beq(CCR2, return_from_interpreted_method);

  __ cmpwi(CCR3, msg, BytecodeInterpreter::more_monitors);
  __ cmpwi(CCR4, msg, BytecodeInterpreter::throwing_exception);

  __ beq(CCR3, more_monitors);
  __ beq(CCR4, throwing_exception);

  __ cmpwi(CCR5, msg, BytecodeInterpreter::popping_frame);
  __ cmpwi(CCR6, msg, BytecodeInterpreter::do_osr);

  __ beq(CCR5, popping_frame);
  __ beq(CCR6, retry_method_osr);

  __ stop("bad message from interpreter");


  //=============================================================================
  // Add a monitor just below the existing one(s). State->_stack_base
  // points to the lowest existing one, so we insert the new one just
  // below it and shuffle the expression stack down. Ref. the above
  // stack layout picture, we must update _stack_base, _stack, _stack_limit
  // and _last_Java_sp in the interpreter state.

  __ BIND(more_monitors);

  generate_more_monitors();
  __ b(call_interpreter);

  generate_deopt_handling(result_index);

  // Restoring the R14_state is already done by the deopt_blob.

  // Current tos includes no parameter slots.
  __ ld(R17_tos, state_(_stack));
  __ li(msg, BytecodeInterpreter::deopt_resume);
  __ b(return_from_native_common);

  // We are sent here when we are unwinding from a native method or
  // adapter with an exception pending. We need to notify the interpreter
  // that there is an exception to process.
  // We arrive here also if the frame manager called an (interpreted) target
  // which returns with a StackOverflow exception.
  // The control flow is in this case is:
  // frame_manager->throw_excp_stub->forward_excp->rethrow_excp_entry

  AbstractInterpreter::_rethrow_exception_entry = __ pc();

  // Restore R14_state.
  __ ld(R14_state, 0, R1_SP);
  __ addi(R14_state, R14_state,
              -frame::interpreter_frame_cinterpreterstate_size_in_bytes());

  // Store exception oop into thread object.
  __ std(R3_RET, thread_(pending_exception));
  __ li(msg, BytecodeInterpreter::method_resume /*rethrow_exception*/);
  //
  // NOTE: the interpreter frame as setup be deopt does NOT include
  // any parameter slots (good thing since we have no callee here
  // and couldn't remove them) so we don't have to do any calculations
  // here to figure it out.
  //
  __ ld(R17_tos, state_(_stack));
  __ b(return_from_native_common);


  //=============================================================================
  // Returning from a native method.  Result is in the native abi
  // location so we must move it to the java expression stack.

  __ BIND(return_from_native);
  guarantee(return_from_native_pc == (address) NULL, "precondition");
  return_from_native_pc = __ pc();

  // Restore R14_state.
  __ ld(R14_state, 0, R1_SP);
  __ addi(R14_state, R14_state,
              -frame::interpreter_frame_cinterpreterstate_size_in_bytes());

  //
  // Registers alive
  //   R16_thread
  //   R14_state    - address of caller's BytecodeInterpreter.
  //   R3_RET       - integer result, if any.
  //   F1_RET       - float result, if any.
  //
  // Registers updated
  //   R19_method   - callee's Method
  //   R17_tos      - caller's tos, with outgoing args popped
  //   result_index - index of result handler.
  //   msg          - message for resuming interpreter.
  //

  // Very-local scratch registers.

  const ConditionRegister have_pending_exception = CCR0;

  // Load callee Method, gc may have moved it.
  __ ld(R19_method, state_(_result._to_call._callee));

  // Load address of caller's tos. includes parameter slots.
  __ ld(R17_tos, state_(_stack));

  // Pop callee's parameters.

  __ ld(parameter_count, in_bytes(Method::const_offset()), R19_method);
  __ lhz(parameter_count, in_bytes(ConstMethod::size_of_parameters_offset()), parameter_count);
  __ sldi(parameter_count, parameter_count, Interpreter::logStackElementSize);
  __ add(R17_tos, R17_tos, parameter_count);

  // Result stub address array index
  // TODO: PPC port: assert(4 == methodOopDesc::sz_result_index(), "unexpected field size");
  __ lwa(result_index, method_(result_index));

  __ li(msg, BytecodeInterpreter::method_resume);

  //
  // Registers alive
  //   R16_thread
  //   R14_state    - address of caller's BytecodeInterpreter.
  //   R17_tos      - address of caller's tos with outgoing args already popped
  //   R3_RET       - integer return value, if any.
  //   F1_RET       - float return value, if any.
  //   result_index - index of result handler.
  //   msg          - message for resuming interpreter.
  //
  // Registers updated
  //   R3_RET       - new address of caller's tos, including result, if any
  //

  __ BIND(return_from_native_common);

  // Check for pending exception
  __ ld(pending_exception, thread_(pending_exception));
  __ cmpdi(CCR0, pending_exception, 0);
  __ beq(CCR0, return_from_native_no_exception);

  // If there's a pending exception, we really have no result, so
  // R3_RET is dead. Resume_interpreter assumes the new tos is in
  // R3_RET.
  __ mr(R3_RET, R17_tos);
  // `resume_interpreter' expects R15_prev_state to be alive.
  __ ld(R15_prev_state, state_(_prev_link));
  __ b(resume_interpreter);

  __ BIND(return_from_native_no_exception);

  // No pending exception, copy method result from native ABI register
  // to tos.

  // Address of stub descriptor address array.
  __ load_const(stub_addr, CppInterpreter::tosca_result_to_stack());

  // Pass address of tos to stub.
  __ mr(R4_ARG2, R17_tos);

  // Address of stub descriptor address.
  __ sldi(result_index, result_index, LogBytesPerWord);
  __ add(stub_addr, stub_addr, result_index);

  // Stub descriptor address.
  __ ld(stub_addr, 0, stub_addr);

  // TODO: don't do this via a call, do it in place!
  //
  // call stub via descriptor
  // in R3_ARG1/F1_ARG1: result value (R3_RET or F1_RET)
  __ call_stub(stub_addr);

  // new tos = result of call in R3_RET

  // `resume_interpreter' expects R15_prev_state to be alive.
  __ ld(R15_prev_state, state_(_prev_link));
  __ b(resume_interpreter);

  //=============================================================================
  // We encountered an exception while computing the interpreter
  // state, so R14_state isn't valid. Act as if we just returned from
  // the callee method with a pending exception.
  __ BIND(stack_overflow_return);

  //
  // Registers alive
  //   R16_thread        - JavaThread*
  //   R1_SP             - old stack pointer
  //   R19_method        - callee's Method
  //   R17_tos           - address of caller's tos (prepushed)
  //   R15_prev_state    - address of caller's BytecodeInterpreter or 0
  //   R18_locals        - address of callee's locals array
  //
  // Registers updated
  //   R3_RET           - address of resuming tos, if recursive unwind

  Label Lskip_unextend_SP;

  {
  const ConditionRegister is_initial_call = CCR0;
  const Register tos_save = R21_tmp1;
  const Register tmp = R22_tmp2;

  assert(tos_save->is_nonvolatile(), "need a nonvolatile");

  // Is the exception thrown in the initial Java frame of this frame
  // manager frame?
  __ cmpdi(is_initial_call, R15_prev_state, 0);
  __ bne(is_initial_call, Lskip_unextend_SP);

  // Pop any c2i extension from the stack. This is necessary in the
  // non-recursive case (that is we were called by the c2i adapter,
  // meaning we have to prev state). In this case we entered the frame
  // manager through a special entry which pushes the orignal
  // unextended SP to the stack. Here we load it back.
  __ ld(R0, _top_ijava_frame_abi(frame_manager_lr), R1_SP);
  __ mtlr(R0);
  // Resize frame to get rid of a potential extension.
  __ resize_frame_to_initial_caller(R11_scratch1, R12_scratch2);

  // Fall through

  __ bind(Lskip_unextend_SP);

  // Throw the exception via RuntimeStub "throw_StackOverflowError_entry".
  //
  // Previously, we called C-Code directly. As a consequence, a
  // possible GC tried to process the argument oops of the top frame
  // (see RegisterMap::clear, which sets the corresponding flag to
  // true). This lead to crashes because:
  // 1. The top register map did not contain locations for the argument registers
  // 2. The arguments are dead anyway, could be already overwritten in the worst case
  // Solution: Call via special runtime stub that pushes it's own frame. This runtime stub has the flag
  // "CodeBlob::caller_must_gc_arguments()" set to "false", what prevents the dead arguments getting GC'd.
  //
  // 2 cases exist:
  // 1. We were called by the c2i adapter / call stub
  // 2. We were called by the frame manager
  //
  // Both cases are handled by this code:
  // 1. - initial_caller_sp was saved on stack => Load it back and we're ok
  //    - control flow will be:
  //      throw_stackoverflow_stub->VM->throw_stackoverflow_stub->forward_excep->excp_blob of calling method
  // 2. - control flow will be:
  //      throw_stackoverflow_stub->VM->throw_stackoverflow_stub->forward_excep->
  //        ->rethrow_excp_entry of frame manager->resume_method
  //      Since we restored the caller SP above, the rethrow_excp_entry can restore the original interpreter state
  //      registers using the stack and resume the calling method with a pending excp.

  __ load_const(R3_ARG1, (StubRoutines::throw_StackOverflowError_entry()));
  __ mtctr(R3_ARG1);
  __ bctr();
  }
  //=============================================================================
  // We have popped a frame from an interpreted call. We are assured
  // of returning to an interpreted call by the popframe abi. We have
  // no return value all we have to do is pop the current frame and
  // then make sure that the top of stack (of the caller) gets set to
  // where it was when we entered the callee (i.e. the args are still
  // in place).  Or we are returning to the interpreter. In the first
  // case we must extract result (if any) from the java expression
  // stack and store it in the location the native abi would expect
  // for a call returning this type. In the second case we must simply
  // do a stack to stack move as we unwind.

  __ BIND(popping_frame);

  // Registers alive
  //   R14_state
  //   R15_prev_state
  //   R17_tos
  //
  // Registers updated
  //   R19_method
  //   R3_RET
  //   msg
  {
    Label L;

    // Reload callee method, gc may have moved it.
    __ ld(R19_method, state_(_method));

    // We may be returning to a deoptimized frame in which case the
    // usual assumption of a recursive return is not true.

    // not equal = is recursive call
    __ cmpdi(CCR0, R15_prev_state, 0);

    __ bne(CCR0, L);

    // Pop_frame capability.
    // The pop_frame api says that the underlying frame is a Java frame, in this case
    // (prev_state==null) it must be a compiled frame:
    //
    // Stack at this point: I, C2I + C, ...
    //
    // The outgoing arguments of the call have just been copied (popframe_preserve_args).
    // By the pop_frame api, we must end up in an interpreted frame. So the compiled frame
    // will be deoptimized. Deoptimization will restore the outgoing arguments from
    // popframe_preserve_args, adjust the tos such that it includes the popframe_preserve_args,
    // and adjust the bci such that the call will be executed again.
    // We have no results, just pop the interpreter frame, resize the compiled frame to get rid
    // of the c2i extension and return to the deopt_handler.
    __ b(unwind_initial_activation);

    // is recursive call
    __ bind(L);

    // Resume_interpreter expects the original tos in R3_RET.
    __ ld(R3_RET, prev_state_(_stack));

    // We're done.
    __ li(msg, BytecodeInterpreter::popping_frame);

    __ b(unwind_recursive_activation);
  }


  //=============================================================================

  // We have finished an interpreted call. We are either returning to
  // native (call_stub/c2) or we are returning to the interpreter.
  // When returning to native, we must extract the result (if any)
  // from the java expression stack and store it in the location the
  // native abi expects. When returning to the interpreter we must
  // simply do a stack to stack move as we unwind.

  __ BIND(return_from_interpreted_method);

  //
  // Registers alive
  //   R16_thread     - JavaThread*
  //   R15_prev_state - address of caller's BytecodeInterpreter or 0
  //   R14_state      - address of callee's interpreter state
  //   R1_SP          - callee's stack pointer
  //
  // Registers updated
  //   R19_method     - callee's method
  //   R3_RET         - address of result (new caller's tos),
  //
  // if returning to interpreted
  //   msg  - message for interpreter,
  // if returning to interpreted
  //

  // Check if this is the initial invocation of the frame manager.
  // If so, R15_prev_state will be null.
  __ cmpdi(CCR0, R15_prev_state, 0);

  // Reload callee method, gc may have moved it.
  __ ld(R19_method, state_(_method));

  // Load the method's result type.
  __ lwz(result_index, method_(result_index));

  // Go to return_to_initial_caller if R15_prev_state is null.
  __ beq(CCR0, return_to_initial_caller);

  // Copy callee's result to caller's expression stack via inline stack-to-stack
  // converters.
  {
    Register new_tos   = R3_RET;
    Register from_temp = R4_ARG2;
    Register from      = R5_ARG3;
    Register tos       = R6_ARG4;
    Register tmp1      = R7_ARG5;
    Register tmp2      = R8_ARG6;

    ConditionRegister result_type_is_void   = CCR1;
    ConditionRegister result_type_is_long   = CCR2;
    ConditionRegister result_type_is_double = CCR3;

    Label stack_to_stack_void;
    Label stack_to_stack_double_slot; // T_LONG, T_DOUBLE
    Label stack_to_stack_single_slot; // T_BOOLEAN, T_BYTE, T_CHAR, T_SHORT, T_INT, T_FLOAT, T_OBJECT
    Label stack_to_stack_done;

    // Pass callee's address of tos + BytesPerWord
    __ ld(from_temp, state_(_stack));

    // result type: void
    __ cmpwi(result_type_is_void, result_index, AbstractInterpreter::BasicType_as_index(T_VOID));

    // Pass caller's tos == callee's locals address
    __ ld(tos, state_(_locals));

    // result type: long
    __ cmpwi(result_type_is_long, result_index, AbstractInterpreter::BasicType_as_index(T_LONG));

    __ addi(from, from_temp, Interpreter::stackElementSize);

    // !! don't branch above this line !!

    // handle void
    __ beq(result_type_is_void,   stack_to_stack_void);

    // result type: double
    __ cmpwi(result_type_is_double, result_index, AbstractInterpreter::BasicType_as_index(T_DOUBLE));

    // handle long or double
    __ beq(result_type_is_long, stack_to_stack_double_slot);
    __ beq(result_type_is_double, stack_to_stack_double_slot);

    // fall through to single slot types (incl. object)

    {
      __ BIND(stack_to_stack_single_slot);
      // T_BOOLEAN, T_BYTE, T_CHAR, T_SHORT, T_INT, T_FLOAT, T_OBJECT

      __ ld(tmp1, 0, from);
      __ std(tmp1, 0, tos);
      // New expression stack top
      __ addi(new_tos, tos, - BytesPerWord);

      __ b(stack_to_stack_done);
    }

    {
      __ BIND(stack_to_stack_double_slot);
      // T_LONG, T_DOUBLE

      // Move both entries for debug purposes even though only one is live
      __ ld(tmp1, BytesPerWord, from);
      __ ld(tmp2, 0, from);
      __ std(tmp1, 0, tos);
      __ std(tmp2, -BytesPerWord, tos);

      // new expression stack top
      __ addi(new_tos, tos, - 2 * BytesPerWord); // two slots
      __ b(stack_to_stack_done);
    }

    {
      __ BIND(stack_to_stack_void);
      // T_VOID

      // new expression stack top
      __ mr(new_tos, tos);
      // fall through to stack_to_stack_done
    }

    __ BIND(stack_to_stack_done);
  }

  // new tos = R3_RET

  // Get the message for the interpreter
  __ li(msg, BytecodeInterpreter::method_resume);

  // And fall thru


  //=============================================================================
  // Restore caller's interpreter state and pass pointer to caller's
  // new tos to caller.

  __ BIND(unwind_recursive_activation);

  //
  // Registers alive
  //   R15_prev_state   - address of caller's BytecodeInterpreter
  //   R3_RET           - address of caller's tos
  //   msg              - message for caller's BytecodeInterpreter
  //   R1_SP            - callee's stack pointer
  //
  // Registers updated
  //   R14_state        - address of caller's BytecodeInterpreter
  //   R15_prev_state   - address of its parent or 0
  //

  // Pop callee's interpreter and set R14_state to caller's interpreter.
  __ pop_interpreter_state(/*prev_state_may_be_0=*/false);

  // And fall thru


  //=============================================================================
  // Resume the (calling) interpreter after a call.

  __ BIND(resume_interpreter);

  //
  // Registers alive
  //   R14_state        - address of resuming BytecodeInterpreter
  //   R15_prev_state   - address of its parent or 0
  //   R3_RET           - address of resuming tos
  //   msg              - message for resuming interpreter
  //   R1_SP            - callee's stack pointer
  //
  // Registers updated
  //   R1_SP            - caller's stack pointer
  //

  // Restore C stack pointer of caller (resuming interpreter),
  // R14_state already points to the resuming BytecodeInterpreter.
  __ pop_interpreter_frame_to_state(R14_state, R21_tmp1, R11_scratch1, R12_scratch2);

  // Store new address of tos (holding return value) in interpreter state.
  __ std(R3_RET, state_(_stack));

  // Store message for interpreter.
  __ stw(msg, state_(_msg));

  __ b(call_interpreter);

  //=============================================================================
  // Interpreter returning to native code (call_stub/c1/c2) from
  // initial activation. Convert stack result and unwind activation.

  __ BIND(return_to_initial_caller);

  //
  // Registers alive
  //   R19_method       - callee's Method
  //   R14_state        - address of callee's interpreter state
  //   R16_thread       - JavaThread
  //   R1_SP            - callee's stack pointer
  //
  // Registers updated
  //   R3_RET/F1_RET - result in expected output register
  //

  // If we have an exception pending we have no result and we
  // must figure out where to really return to.
  //
  __ ld(pending_exception, thread_(pending_exception));
  __ cmpdi(CCR0, pending_exception, 0);
  __ bne(CCR0, unwind_initial_activation_pending_exception);

  __ lwa(result_index, method_(result_index));

  // Address of stub descriptor address array.
  __ load_const(stub_addr, CppInterpreter::stack_result_to_native());

  // Pass address of callee's tos + BytesPerWord.
  // Will then point directly to result.
  __ ld(R3_ARG1, state_(_stack));
  __ addi(R3_ARG1, R3_ARG1, Interpreter::stackElementSize);

  // Address of stub descriptor address
  __ sldi(result_index, result_index, LogBytesPerWord);
  __ add(stub_addr, stub_addr, result_index);

  // Stub descriptor address
  __ ld(stub_addr, 0, stub_addr);

  // TODO: don't do this via a call, do it in place!
  //
  // call stub via descriptor
  __ call_stub(stub_addr);

  __ BIND(unwind_initial_activation);

  // Unwind from initial activation. No exception is pending.

  //
  // Stack layout at this point:
  //
  //    0       [TOP_IJAVA_FRAME_ABI]         <-- R1_SP
  //            ...
  //    CALLER  [PARENT_IJAVA_FRAME_ABI]
  //            ...
  //    CALLER  [unextended ABI]
  //            ...
  //
  //  The CALLER frame has a C2I adapter or is an entry-frame.
  //

  // An interpreter frame exists, we may pop the TOP_IJAVA_FRAME and
  // turn the caller's PARENT_IJAVA_FRAME back into a TOP_IJAVA_FRAME.
  // But, we simply restore the return pc from the caller's frame and
  // use the caller's initial_caller_sp as the new SP which pops the
  // interpreter frame and "resizes" the caller's frame to its "unextended"
  // size.

  // get rid of top frame
  __ pop_frame();

  // Load return PC from parent frame.
  __ ld(R21_tmp1, _parent_ijava_frame_abi(lr), R1_SP);

  // Resize frame to get rid of a potential extension.
  __ resize_frame_to_initial_caller(R11_scratch1, R12_scratch2);

  // update LR
  __ mtlr(R21_tmp1);

  // return
  __ blr();

  //=============================================================================
  // Unwind from initial activation. An exception is pending

  __ BIND(unwind_initial_activation_pending_exception);

  //
  // Stack layout at this point:
  //
  //   0       [TOP_IJAVA_FRAME_ABI]         <-- R1_SP
  //           ...
  //   CALLER  [PARENT_IJAVA_FRAME_ABI]
  //           ...
  //   CALLER  [unextended ABI]
  //           ...
  //
  // The CALLER frame has a C2I adapter or is an entry-frame.
  //

  // An interpreter frame exists, we may pop the TOP_IJAVA_FRAME and
  // turn the caller's PARENT_IJAVA_FRAME back into a TOP_IJAVA_FRAME.
  // But, we just pop the current TOP_IJAVA_FRAME and fall through

  __ pop_frame();
  __ ld(R3_ARG1, _top_ijava_frame_abi(lr), R1_SP);

  //
  // Stack layout at this point:
  //
  //   CALLER  [PARENT_IJAVA_FRAME_ABI]      <-- R1_SP
  //           ...
  //   CALLER  [unextended ABI]
  //           ...
  //
  // The CALLER frame has a C2I adapter or is an entry-frame.
  //
  // Registers alive
  //   R16_thread
  //   R3_ARG1 - return address to caller
  //
  // Registers updated
  //   R3_ARG1 - address of pending exception
  //   R4_ARG2 - issuing pc = return address to caller
  //   LR      - address of exception handler stub
  //

  // Resize frame to get rid of a potential extension.
  __ resize_frame_to_initial_caller(R11_scratch1, R12_scratch2);

  __ mr(R14, R3_ARG1);   // R14 := ARG1
  __ mr(R4_ARG2, R3_ARG1);  // ARG2 := ARG1

  // Find the address of the "catch_exception" stub.
  __ push_frame_abi112(0, R11_scratch1);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address),
                  R16_thread,
                  R4_ARG2);
  __ pop_frame();

  // Load continuation address into LR.
  __ mtlr(R3_RET);

  // Load address of pending exception and clear it in thread object.
  __ ld(R3_ARG1/*R3_RET*/, thread_(pending_exception));
  __ li(R4_ARG2, 0);
  __ std(R4_ARG2, thread_(pending_exception));

  // re-load issuing pc
  __ mr(R4_ARG2, R14);

  // Branch to found exception handler.
  __ blr();

  //=============================================================================
  // Call a new method. Compute new args and trim the expression stack
  // to only what we are currently using and then recurse.

  __ BIND(call_method);

  //
  //  Registers alive
  //    R16_thread
  //    R14_state      - address of caller's BytecodeInterpreter
  //    R1_SP          - caller's stack pointer
  //
  //  Registers updated
  //    R15_prev_state - address of caller's BytecodeInterpreter
  //    R17_tos        - address of caller's tos
  //    R19_method     - callee's Method
  //    R1_SP          - trimmed back
  //

  // Very-local scratch registers.

  const Register offset = R21_tmp1;
  const Register tmp    = R22_tmp2;
  const Register self_entry  = R23_tmp3;
  const Register stub_entry  = R24_tmp4;

  const ConditionRegister cr = CCR0;

  // Load the address of the frame manager.
  __ load_const(self_entry, &interpreter_frame_manager);
  __ ld(self_entry, 0, self_entry);

  // Load BytecodeInterpreter._result._to_call._callee (callee's Method).
  __ ld(R19_method, state_(_result._to_call._callee));
  // Load BytecodeInterpreter._stack (outgoing tos).
  __ ld(R17_tos, state_(_stack));

  // Save address of caller's BytecodeInterpreter.
  __ mr(R15_prev_state, R14_state);

  // Load the callee's entry point.
  // Load BytecodeInterpreter._result._to_call._callee_entry_point.
  __ ld(stub_entry, state_(_result._to_call._callee_entry_point));

  // Check whether stub_entry is equal to self_entry.
  __ cmpd(cr, self_entry, stub_entry);
  // if (self_entry == stub_entry)
  //   do a re-dispatch
  __ beq(cr, re_dispatch);
  // else
  //   call the specialized entry (adapter for jni or compiled code)
  __ BIND(call_special);

  //
  // Call the entry generated by `InterpreterGenerator::generate_native_entry'.
  //
  // Registers alive
  //   R16_thread
  //   R15_prev_state    - address of caller's BytecodeInterpreter
  //   R19_method        - callee's Method
  //   R17_tos           - address of caller's tos
  //   R1_SP             - caller's stack pointer
  //

  // Mark return from specialized entry for generate_native_entry.
  guarantee(return_from_native_pc != (address) NULL, "precondition");
  frame_manager_specialized_return = return_from_native_pc;

  // Set sender_SP in case we call interpreter native wrapper which
  // will expect it. Compiled code should not care.
  __ mr(R21_sender_SP, R1_SP);

  // Do a tail call here, and let the link register point to
  // frame_manager_specialized_return which is return_from_native_pc.
  __ load_const(tmp, frame_manager_specialized_return);
  __ call_stub_and_return_to(stub_entry,  tmp /* return_pc=tmp */);


  //=============================================================================
  //
  // InterpretMethod triggered OSR compilation of some Java method M
  // and now asks to run the compiled code.  We call this code the
  // `callee'.
  //
  // This is our current idea on how OSR should look like on PPC64:
  //
  // While interpreting a Java method M the stack is:
  //
  //  (InterpretMethod (M), IJAVA_FRAME (M), ANY_FRAME, ...).
  //
  // After having OSR compiled M, `InterpretMethod' returns to the
  // frame manager, sending the message `retry_method_osr'.  The stack
  // is:
  //
  //  (IJAVA_FRAME (M), ANY_FRAME, ...).
  //
  // The compiler will have generated an `nmethod' suitable for
  // continuing execution of M at the bytecode index at which OSR took
  // place.  So now the frame manager calls the OSR entry.  The OSR
  // entry sets up a JIT_FRAME for M and continues execution of M with
  // initial state determined by the IJAVA_FRAME.
  //
  //  (JIT_FRAME (M), IJAVA_FRAME (M), ANY_FRAME, ...).
  //

  __ BIND(retry_method_osr);
  {
  //
  // Registers alive
  //   R16_thread
  //   R15_prev_state     - address of caller's BytecodeInterpreter
  //   R14_state          - address of callee's BytecodeInterpreter
  //   R1_SP              - callee's SP before call to InterpretMethod
  //
  // Registers updated
  //   R17                - pointer to callee's locals array
  //                       (declared via `interpreter_arg_ptr_reg' in the AD file)
  //   R19_method         - callee's Method
  //   R1_SP              - callee's SP (will become SP of OSR adapter frame)
  //

  // Provide a debugger breakpoint in the frame manager if breakpoints
  // in osr'd methods are requested.
#ifdef COMPILER2
  NOT_PRODUCT( if (OptoBreakpointOSR) { __ illtrap(); } )
#endif

  // Load callee's pointer to locals array from callee's state.
  //  __ ld(R17, state_(_locals));

  // Load osr entry.
  __ ld(R12_scratch2, state_(_result._osr._osr_entry));

  // Load address of temporary osr buffer to arg1.
  __ ld(R3_ARG1, state_(_result._osr._osr_buf));
  __ mtctr(R12_scratch2);

  // Load method oop, gc may move it during execution of osr'd method.
  __ ld(R22_tmp2, state_(_method));
  // Load message 'call_method'.
  __ li(R23_tmp3, BytecodeInterpreter::call_method);

  {
    // Pop the IJAVA frame of the method which we are going to call osr'd.
    Label no_state, skip_no_state;
    __ pop_interpreter_state(/*prev_state_may_be_0=*/true);
    __ cmpdi(CCR0, R14_state,0);
    __ beq(CCR0, no_state);
    // return to interpreter
    __ pop_interpreter_frame_to_state(R14_state, R11_scratch1, R12_scratch2, R21_tmp1);

    // Init _result._to_call._callee and tell gc that it contains a valid oop
    // by setting _msg to 'call_method'.
    __ std(R22_tmp2, state_(_result._to_call._callee));
    // TODO: PPC port: assert(4 == BytecodeInterpreter::sz_msg(), "unexpected field size");
    __ stw(R23_tmp3, state_(_msg));

    __ load_const(R21_tmp1, frame_manager_specialized_return);
    __ b(skip_no_state);
    __ bind(no_state);

    // Return to initial caller.

    // Get rid of top frame.
    __ pop_frame();

    // Load return PC from parent frame.
    __ ld(R21_tmp1, _parent_ijava_frame_abi(lr), R1_SP);

    // Resize frame to get rid of a potential extension.
    __ resize_frame_to_initial_caller(R11_scratch1, R12_scratch2);

    __ bind(skip_no_state);

    // Update LR with return pc.
    __ mtlr(R21_tmp1);
  }
  // Jump to the osr entry point.
  __ bctr();

  }

  //=============================================================================
  // Interpreted method "returned" with an exception, pass it on.
  // Pass no result, unwind activation and continue/return to
  // interpreter/call_stub/c2.

  __ BIND(throwing_exception);

  // Check if this is the initial invocation of the frame manager.  If
  // so, previous interpreter state in R15_prev_state will be null.

  // New tos of caller is callee's first parameter address, that is
  // callee's incoming arguments are popped.
  __ ld(R3_RET, state_(_locals));

  // Check whether this is an initial call.
  __ cmpdi(CCR0, R15_prev_state, 0);
  // Yes, called from the call stub or from generated code via a c2i frame.
  __ beq(CCR0, unwind_initial_activation_pending_exception);

  // Send resume message, interpreter will see the exception first.

  __ li(msg, BytecodeInterpreter::method_resume);
  __ b(unwind_recursive_activation);


  //=============================================================================
  // Push the last instruction out to the code buffer.

  {
    __ unimplemented("end of InterpreterGenerator::generate_normal_entry", 128);
  }

  interpreter_frame_manager = entry;
  return interpreter_frame_manager;
}

// Generate code for various sorts of method entries
//
address AbstractInterpreterGenerator::generate_method_entry(AbstractInterpreter::MethodKind kind) {
  address entry_point = NULL;

  switch (kind) {
    case Interpreter::zerolocals                 :                                                                              break;
    case Interpreter::zerolocals_synchronized    :                                                                              break;
    case Interpreter::native                     : // Fall thru
    case Interpreter::native_synchronized        : entry_point = ((CppInterpreterGenerator*)this)->generate_native_entry();     break;
    case Interpreter::empty                      :                                                                              break;
    case Interpreter::accessor                   : entry_point = ((InterpreterGenerator*)this)->generate_accessor_entry();      break;
    case Interpreter::abstract                   : entry_point = ((InterpreterGenerator*)this)->generate_abstract_entry();      break;
    // These are special interpreter intrinsics which we don't support so far.
    case Interpreter::java_lang_math_sin         :                                                                              break;
    case Interpreter::java_lang_math_cos         :                                                                              break;
    case Interpreter::java_lang_math_tan         :                                                                              break;
    case Interpreter::java_lang_math_abs         :                                                                              break;
    case Interpreter::java_lang_math_log         :                                                                              break;
    case Interpreter::java_lang_math_log10       :                                                                              break;
    case Interpreter::java_lang_math_sqrt        :                                                                              break;
    case Interpreter::java_lang_math_pow         :                                                                              break;
    case Interpreter::java_lang_math_exp         :                                                                              break;
    case Interpreter::java_lang_ref_reference_get: entry_point = ((InterpreterGenerator*)this)->generate_Reference_get_entry(); break;
    default                                      : ShouldNotReachHere();                                                        break;
  }

  if (entry_point) {
    return entry_point;
  }
  return ((InterpreterGenerator*)this)->generate_normal_entry();
}

InterpreterGenerator::InterpreterGenerator(StubQueue* code)
 : CppInterpreterGenerator(code) {
   generate_all(); // down here so it can be "virtual"
}

// How much stack a topmost interpreter method activation needs in words.
int AbstractInterpreter::size_top_interpreter_activation(Method* method) {
  // Computation is in bytes not words to match layout_activation_impl
  // below, but the return is in words.

  //
  //  0       [TOP_IJAVA_FRAME_ABI]                                                    \
  //          alignment (optional)                                             \       |
  //          [operand stack / Java parameters] > stack                        |       |
  //          [monitors] (optional)             > monitors                     |       |
  //          [PARENT_IJAVA_FRAME_ABI]                                \        |       |
  //          [BytecodeInterpreter object]      > interpreter \       |        |       |
  //          alignment (optional)                            | round | parent | round | top
  //          [Java result] (2 slots)           > result      |       |        |       |
  //          [Java non-arg locals]             \ locals      |       |        |       |
  //          [arg locals]                      /             /       /        /       /
  //

  int locals = method->max_locals() * BytesPerWord;
  int interpreter = frame::interpreter_frame_cinterpreterstate_size_in_bytes();
  int result = 2 * BytesPerWord;

  int parent = round_to(interpreter + result + locals, 16) + frame::parent_ijava_frame_abi_size;

  int stack = method->max_stack() * BytesPerWord;
  int monitors = method->is_synchronized() ? frame::interpreter_frame_monitor_size_in_bytes() : 0;
  int top = round_to(parent + monitors + stack, 16) + frame::top_ijava_frame_abi_size;

  return (top / BytesPerWord);
}

void BytecodeInterpreter::layout_interpreterState(interpreterState to_fill,
                                                  frame* caller,
                                                  frame* current,
                                                  Method* method,
                                                  intptr_t* locals,
                                                  intptr_t* stack,
                                                  intptr_t* stack_base,
                                                  intptr_t* monitor_base,
                                                  intptr_t* frame_sp,
                                                  bool is_top_frame) {
  // What about any vtable?
  //
  to_fill->_thread = JavaThread::current();
  // This gets filled in later but make it something recognizable for now.
  to_fill->_bcp = method->code_base();
  to_fill->_locals = locals;
  to_fill->_constants = method->constants()->cache();
  to_fill->_method = method;
  to_fill->_mdx = NULL;
  to_fill->_stack = stack;

  if (is_top_frame && JavaThread::current()->popframe_forcing_deopt_reexecution()) {
    to_fill->_msg = deopt_resume2;
  } else {
    to_fill->_msg = method_resume;
  }
  to_fill->_result._to_call._bcp_advance = 0;
  to_fill->_result._to_call._callee_entry_point = NULL; // doesn't matter to anyone
  to_fill->_result._to_call._callee = NULL; // doesn't matter to anyone
  to_fill->_prev_link = NULL;

  if (caller->is_interpreted_frame()) {
    interpreterState prev  = caller->get_interpreterState();

    // Support MH calls. Make sure the interpreter will return the right address:
    // 1. Caller did ordinary interpreted->compiled call call: Set a prev_state
    //    which makes the CPP interpreter return to frame manager "return_from_interpreted_method"
    //    entry after finishing execution.
    // 2. Caller did a MH call: If the caller has a MethodHandleInvoke in it's
    //    state (invariant: must be the caller of the bottom vframe) we used the
    //    "call_special" entry to do the call, meaning the arguments have not been
    //    popped from the stack. Therefore, don't enter a prev state in this case
    //    in order to return to "return_from_native" frame manager entry which takes
    //    care of popping arguments. Also, don't overwrite the MH.invoke Method in
    //    the prev_state in order to be able to figure out the number of arguments to
    //     pop.
    // The parameter method can represent MethodHandle.invokeExact(...).
    // The MethodHandleCompiler generates these synthetic Methods,
    // including bytecodes, if an invokedynamic call gets inlined. In
    // this case we want to return like from any other interpreted
    // Java call, so we set _prev_link.
    to_fill->_prev_link = prev;

    if (*prev->_bcp == Bytecodes::_invokeinterface || *prev->_bcp == Bytecodes::_invokedynamic) {
      prev->_result._to_call._bcp_advance = 5;
    } else {
      prev->_result._to_call._bcp_advance = 3;
    }
  }
  to_fill->_oop_temp = NULL;
  to_fill->_stack_base = stack_base;
  // Need +1 here because stack_base points to the word just above the
  // first expr stack entry and stack_limit is supposed to point to
  // the word just below the last expr stack entry. See
  // generate_compute_interpreter_state.
  to_fill->_stack_limit = stack_base - (method->max_stack() + 1);
  to_fill->_monitor_base = (BasicObjectLock*) monitor_base;

  to_fill->_frame_bottom = frame_sp;

  // PPC64 specific
  to_fill->_last_Java_pc = NULL;
  to_fill->_last_Java_fp = NULL;
  to_fill->_last_Java_sp = frame_sp;
#ifdef ASSERT
  to_fill->_self_link = to_fill;
  to_fill->_native_fresult = 123456.789;
  to_fill->_native_lresult = CONST64(0xdeafcafedeadc0de);
#endif
}

void BytecodeInterpreter::pd_layout_interpreterState(interpreterState istate,
                                                     address last_Java_pc,
                                                     intptr_t* last_Java_fp) {
  istate->_last_Java_pc = last_Java_pc;
  istate->_last_Java_fp = last_Java_fp;
}

int AbstractInterpreter::layout_activation(Method* method,
                                           int temps,        // Number of slots on java expression stack in use.
                                           int popframe_args,
                                           int monitors,     // Number of active monitors.
                                           int caller_actual_parameters,
                                           int callee_params,// Number of slots for callee parameters.
                                           int callee_locals,// Number of slots for locals.
                                           frame* caller,
                                           frame* interpreter_frame,
                                           bool is_top_frame,
                                           bool is_bottom_frame) {

  // NOTE this code must exactly mimic what
  // InterpreterGenerator::generate_compute_interpreter_state() does
  // as far as allocating an interpreter frame. However there is an
  // exception. With the C++ based interpreter only the top most frame
  // has a full sized expression stack.  The 16 byte slop factor is
  // both the abi scratch area and a place to hold a result from a
  // callee on its way to the callers stack.

  int monitor_size = frame::interpreter_frame_monitor_size_in_bytes() * monitors;
  int frame_size;
  int top_frame_size = round_to(frame::interpreter_frame_cinterpreterstate_size_in_bytes()
                                + monitor_size
                                + (method->max_stack() *Interpreter::stackElementWords * BytesPerWord)
                                + 2*BytesPerWord,
                                frame::alignment_in_bytes)
                      + frame::top_ijava_frame_abi_size;
  if (is_top_frame) {
    frame_size = top_frame_size;
  } else {
    frame_size = round_to(frame::interpreter_frame_cinterpreterstate_size_in_bytes()
                          + monitor_size
                          + ((temps - callee_params + callee_locals) *
                             Interpreter::stackElementWords * BytesPerWord)
                          + 2*BytesPerWord,
                          frame::alignment_in_bytes)
                 + frame::parent_ijava_frame_abi_size;
    assert(popframe_args==0, "non-zero for top_frame only");
  }

  // If we actually have a frame to layout we must now fill in all the pieces.
  if (interpreter_frame != NULL) {

    intptr_t sp = (intptr_t)interpreter_frame->sp();
    intptr_t fp = *(intptr_t *)sp;
    assert(fp == (intptr_t)caller->sp(), "fp must match");
    interpreterState cur_state =
      (interpreterState)(fp - frame::interpreter_frame_cinterpreterstate_size_in_bytes());

    // Now fill in the interpreterState object.

    intptr_t* locals;
    if (caller->is_interpreted_frame()) {
      // Locals must agree with the caller because it will be used to set the
      // caller's tos when we return.
      interpreterState prev  = caller->get_interpreterState();
      // Calculate start of "locals" for MH calls.  For MH calls, the
      // current method() (= MH target) and prev->callee() (=
      // MH.invoke*()) are different and especially have different
      // signatures. To pop the argumentsof the caller, we must use
      // the prev->callee()->size_of_arguments() because that's what
      // the caller actually pushed.  Currently, for synthetic MH
      // calls (deoptimized from inlined MH calls), detected by
      // is_method_handle_invoke(), we use the callee's arguments
      // because here, the caller's and callee's signature match.
      if (true /*!caller->is_at_mh_callsite()*/) {
        locals = prev->stack() + method->size_of_parameters();
      } else {
        // Normal MH call.
        locals = prev->stack() + prev->callee()->size_of_parameters();
      }
    } else {
      bool is_deopted;
      locals = (intptr_t*) (fp + ((method->max_locals() - 1) * BytesPerWord) +
                            frame::parent_ijava_frame_abi_size);
    }

    intptr_t* monitor_base = (intptr_t*) cur_state;
    intptr_t* stack_base   = (intptr_t*) ((intptr_t) monitor_base - monitor_size);

    // Provide pop_frame capability on PPC64, add popframe_args.
    // +1 because stack is always prepushed.
    intptr_t* stack = (intptr_t*) ((intptr_t) stack_base - (temps + popframe_args + 1) * BytesPerWord);

    BytecodeInterpreter::layout_interpreterState(cur_state,
                                                 caller,
                                                 interpreter_frame,
                                                 method,
                                                 locals,
                                                 stack,
                                                 stack_base,
                                                 monitor_base,
                                                 (intptr_t*)(((intptr_t)fp)-top_frame_size),
                                                 is_top_frame);

    BytecodeInterpreter::pd_layout_interpreterState(cur_state, interpreter_return_address,
                                                    interpreter_frame->fp());
  }
  return frame_size/BytesPerWord;
}

#endif // CC_INTERP
