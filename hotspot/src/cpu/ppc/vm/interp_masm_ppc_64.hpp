/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_PPC_VM_INTERP_MASM_PPC_64_HPP
#define CPU_PPC_VM_INTERP_MASM_PPC_64_HPP

#include "assembler_ppc.inline.hpp"
#include "interpreter/invocationCounter.hpp"

// This file specializes the assembler with interpreter-specific macros


class InterpreterMacroAssembler: public MacroAssembler {

 public:
  InterpreterMacroAssembler(CodeBuffer* code) : MacroAssembler(code) {}

  // Handy address generation macros
#define thread_(field_name) in_bytes(JavaThread::field_name ## _offset()), R16_thread
#define method_(field_name) in_bytes(Method::field_name ## _offset()), R19_method

#ifdef CC_INTERP
#define state_(field_name)  in_bytes(byte_offset_of(BytecodeInterpreter, field_name)), R14_state
#define prev_state_(field_name)  in_bytes(byte_offset_of(BytecodeInterpreter, field_name)), R15_prev_state
#endif

  void get_method_counters(Register method, Register Rcounters, Label& skip);
  void increment_invocation_counter(Register iv_be_count, Register Rtmp1, Register Rtmp2_r0);

  // Object locking
  void lock_object  (Register lock_reg, Register obj_reg);
  void unlock_object(Register lock_reg);

  // Debugging
  void verify_oop(Register reg, TosState state = atos);    // only if +VerifyOops && state == atos

  // support for jvmdi/jvmpi
  void notify_method_entry();
  void notify_method_exit(bool save_result, TosState state);

  // Convert the current TOP_IJAVA_FRAME into a PARENT_IJAVA_FRAME
  // (using parent_frame_resize) and push a new interpreter
  // TOP_IJAVA_FRAME (using frame_size).
  void push_interpreter_frame(Register top_frame_size, Register parent_frame_resize,
                              Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register pc=noreg);

  // Pop the topmost TOP_IJAVA_FRAME and convert the previous
  // PARENT_IJAVA_FRAME back into a TOP_IJAVA_FRAME.
  void pop_interpreter_frame(Register tmp1, Register tmp2, Register tmp3, Register tmp4);

  // Turn state's interpreter frame into the current TOP_IJAVA_FRAME.
  void pop_interpreter_frame_to_state(Register state, Register tmp1, Register tmp2, Register tmp3);

  // Set SP to initial caller's sp, but before fix the back chain.
  void resize_frame_to_initial_caller(Register tmp1, Register tmp2);

  // Pop the current interpreter state (without popping the
  // correspoding frame) and restore R14_state and R15_prev_state
  // accordingly. Use prev_state_may_be_0 to indicate whether
  // prev_state may be 0 in order to generate an extra check before
  // retrieving prev_state_(_prev_link).
  void pop_interpreter_state(bool prev_state_may_be_0);

  void restore_prev_state();
};

#endif // CPU_PPC_VM_INTERP_MASM_PPC_64_HPP
