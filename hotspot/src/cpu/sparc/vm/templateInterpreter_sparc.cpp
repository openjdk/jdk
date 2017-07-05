/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/interpreter.hpp"
#include "oops/constMethod.hpp"
#include "oops/method.hpp"
#include "runtime/arguments.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/synchronizer.hpp"
#include "utilities/macros.hpp"

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

int AbstractInterpreter::BasicType_as_index(BasicType type) {
  int i = 0;
  switch (type) {
    case T_BOOLEAN: i = 0; break;
    case T_CHAR   : i = 1; break;
    case T_BYTE   : i = 2; break;
    case T_SHORT  : i = 3; break;
    case T_INT    : i = 4; break;
    case T_LONG   : i = 5; break;
    case T_VOID   : i = 6; break;
    case T_FLOAT  : i = 7; break;
    case T_DOUBLE : i = 8; break;
    case T_OBJECT : i = 9; break;
    case T_ARRAY  : i = 9; break;
    default       : ShouldNotReachHere();
  }
  assert(0 <= i && i < AbstractInterpreter::number_of_result_handlers, "index out of bounds");
  return i;
}

bool AbstractInterpreter::can_be_compiled(methodHandle m) {
  // No special entry points that preclude compilation
  return true;
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

int AbstractInterpreter::size_activation(int max_stack,
                                         int temps,
                                         int extra_args,
                                         int monitors,
                                         int callee_params,
                                         int callee_locals,
                                         bool is_top_frame) {
  // Note: This calculation must exactly parallel the frame setup
  // in TemplateInterpreterGenerator::generate_fixed_frame.

  int monitor_size           = monitors * frame::interpreter_frame_monitor_size();

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

  int rounded_cls = round_to((callee_locals - callee_params), WordsPerLong);
  assert(rounded_cls == round_to(rounded_cls, WordsPerLong), "must align");

  int raw_frame_size = size_activation_helper(rounded_cls, max_stack, monitor_size);

  return raw_frame_size;
}

void AbstractInterpreter::layout_activation(Method* method,
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
  // Set up the following variables:
  //   - Lmethod
  //   - Llocals
  //   - Lmonitors (to the indicated number of monitors)
  //   - Lesp (to the indicated number of temps)
  // The frame caller on entry is a description of the caller of the
  // frame we are about to layout. We are guaranteed that we will be
  // able to fill in a new interpreter frame as its callee (i.e. the
  // stack space is allocated and the amount was determined by an
  // earlier call to the size_activation() method).  On return caller
  // while describe the interpreter frame we just layed out.

  // The skeleton frame must already look like an interpreter frame
  // even if not fully filled out.
  assert(interpreter_frame->is_interpreted_frame(), "Must be interpreted frame");

  int rounded_vm_local_words = round_to(frame::interpreter_frame_vm_local_words,WordsPerLong);
  int monitor_size           = moncount * frame::interpreter_frame_monitor_size();
  assert(monitor_size == round_to(monitor_size, WordsPerLong), "must align");

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
    tty->print_cr("caller fp=" INTPTR_FORMAT " sp=" INTPTR_FORMAT, p2i(caller->fp()), p2i(caller->sp()));
    tty->print_cr("save area = " INTPTR_FORMAT ", " INTPTR_FORMAT, p2i(caller->sp()), p2i(caller->sp() + 16));
    tty->print_cr("save area = " INTPTR_FORMAT ", " INTPTR_FORMAT, p2i(caller->fp()), p2i(caller->fp() + 16));
    tty->print_cr("interpreter fp=" INTPTR_FORMAT ", " INTPTR_FORMAT, p2i(interpreter_frame->fp()), p2i(interpreter_frame->sp()));
    tty->print_cr("save area = " INTPTR_FORMAT ", " INTPTR_FORMAT, p2i(interpreter_frame->sp()), p2i(interpreter_frame->sp() + 16));
    tty->print_cr("save area = " INTPTR_FORMAT ", " INTPTR_FORMAT, p2i(interpreter_frame->fp()), p2i(interpreter_frame->fp() + 16));
    tty->print_cr("Llocals = " INTPTR_FORMAT, p2i(locals));
    tty->print_cr("Lesp = " INTPTR_FORMAT, p2i(esp));
    tty->print_cr("Lmonitors = " INTPTR_FORMAT, p2i(monitors));
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
