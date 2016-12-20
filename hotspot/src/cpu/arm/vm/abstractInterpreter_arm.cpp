/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/bytecode.hpp"
#include "interpreter/interpreter.hpp"
#include "oops/constMethod.hpp"
#include "oops/method.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/synchronizer.hpp"
#include "utilities/macros.hpp"

int AbstractInterpreter::BasicType_as_index(BasicType type) {
  int i = 0;
  switch (type) {
#ifdef AARCH64
    case T_BOOLEAN: i = 0; break;
    case T_CHAR   : i = 1; break;
    case T_BYTE   : i = 2; break;
    case T_SHORT  : i = 3; break;
    case T_INT    : // fall through
    case T_LONG   : // fall through
    case T_VOID   : // fall through
    case T_FLOAT  : // fall through
    case T_DOUBLE : i = 4; break;
    case T_OBJECT : // fall through
    case T_ARRAY  : i = 5; break;
#else
    case T_VOID   : i = 0; break;
    case T_BOOLEAN: i = 1; break;
    case T_CHAR   : i = 2; break;
    case T_BYTE   : i = 3; break;
    case T_SHORT  : i = 4; break;
    case T_INT    : i = 5; break;
    case T_OBJECT : // fall through
    case T_ARRAY  : i = 6; break;
    case T_LONG   : i = 7; break;
    case T_FLOAT  : i = 8; break;
    case T_DOUBLE : i = 9; break;
#endif // AARCH64
    default       : ShouldNotReachHere();
  }
  assert(0 <= i && i < AbstractInterpreter::number_of_result_handlers, "index out of bounds");
  return i;
}

// These should never be compiled since the interpreter will prefer
// the compiled version to the intrinsic version.
bool AbstractInterpreter::can_be_compiled(methodHandle m) {
  switch (method_kind(m)) {
    case Interpreter::java_lang_math_sin     : // fall thru
    case Interpreter::java_lang_math_cos     : // fall thru
    case Interpreter::java_lang_math_tan     : // fall thru
    case Interpreter::java_lang_math_abs     : // fall thru
    case Interpreter::java_lang_math_log     : // fall thru
    case Interpreter::java_lang_math_log10   : // fall thru
    case Interpreter::java_lang_math_sqrt    :
      return false;
    default:
      return true;
  }
}

// How much stack a method activation needs in words.
int AbstractInterpreter::size_top_interpreter_activation(Method* method) {
  const int stub_code = AARCH64_ONLY(24) NOT_AARCH64(12);  // see generate_call_stub
  // Save space for one monitor to get into the interpreted method in case
  // the method is synchronized
  int monitor_size    = method->is_synchronized() ?
                                1*frame::interpreter_frame_monitor_size() : 0;

  // total overhead size: monitor_size + (sender SP, thru expr stack bottom).
  // be sure to change this if you add/subtract anything to/from the overhead area
  const int overhead_size = monitor_size +
                            (frame::sender_sp_offset - frame::interpreter_frame_initial_sp_offset);
  const int method_stack = (method->max_locals() + method->max_stack()) *
                           Interpreter::stackElementWords;
  return overhead_size + method_stack + stub_code;
}

// asm based interpreter deoptimization helpers
int AbstractInterpreter::size_activation(int max_stack,
                                         int tempcount,
                                         int extra_args,
                                         int moncount,
                                         int callee_param_count,
                                         int callee_locals,
                                         bool is_top_frame) {
  // Note: This calculation must exactly parallel the frame setup
  // in TemplateInterpreterGenerator::generate_fixed_frame.
  // fixed size of an interpreter frame:
  int overhead = frame::sender_sp_offset - frame::interpreter_frame_initial_sp_offset;

  // Our locals were accounted for by the caller (or last_frame_adjust on the transistion)
  // Since the callee parameters already account for the callee's params we only need to account for
  // the extra locals.

  int size = overhead +
         ((callee_locals - callee_param_count)*Interpreter::stackElementWords) +
         (moncount*frame::interpreter_frame_monitor_size()) +
         tempcount*Interpreter::stackElementWords + extra_args;

#ifdef AARCH64
  size = round_to(size, StackAlignmentInBytes/BytesPerWord);
#endif // AARCH64

  return size;
}

void AbstractInterpreter::layout_activation(Method* method,
                                            int tempcount,
                                            int popframe_extra_args,
                                            int moncount,
                                            int caller_actual_parameters,
                                            int callee_param_count,
                                            int callee_locals,
                                            frame* caller,
                                            frame* interpreter_frame,
                                            bool is_top_frame,
                                            bool is_bottom_frame) {

  // Set up the method, locals, and monitors.
  // The frame interpreter_frame is guaranteed to be the right size,
  // as determined by a previous call to the size_activation() method.
  // It is also guaranteed to be walkable even though it is in a skeletal state
  // NOTE: return size is in words not bytes

  // fixed size of an interpreter frame:
  int max_locals = method->max_locals() * Interpreter::stackElementWords;
  int extra_locals = (method->max_locals() - method->size_of_parameters()) * Interpreter::stackElementWords;

#ifdef ASSERT
  assert(caller->sp() == interpreter_frame->sender_sp(), "Frame not properly walkable");
#endif

  interpreter_frame->interpreter_frame_set_method(method);
  // NOTE the difference in using sender_sp and interpreter_frame_sender_sp
  // interpreter_frame_sender_sp is the original sp of the caller (the unextended_sp)
  // and sender_sp is (fp + sender_sp_offset*wordSize)

#ifdef AARCH64
  intptr_t* locals;
  if (caller->is_interpreted_frame()) {
    // attach locals to the expression stack of caller interpreter frame
    locals = caller->interpreter_frame_tos_address() + caller_actual_parameters*Interpreter::stackElementWords - 1;
  } else {
    assert (is_bottom_frame, "should be");
    locals = interpreter_frame->fp() + frame::sender_sp_offset + method->max_locals() - 1;
  }

  if (TraceDeoptimization) {
    tty->print_cr("layout_activation:");

    if (caller->is_entry_frame()) {
      tty->print("entry ");
    }
    if (caller->is_compiled_frame()) {
      tty->print("compiled ");
    }
    if (caller->is_interpreted_frame()) {
      tty->print("interpreted ");
    }
    tty->print_cr("caller: sp=%p, unextended_sp=%p, fp=%p, pc=%p", caller->sp(), caller->unextended_sp(), caller->fp(), caller->pc());
    tty->print_cr("interpreter_frame: sp=%p, unextended_sp=%p, fp=%p, pc=%p", interpreter_frame->sp(), interpreter_frame->unextended_sp(), interpreter_frame->fp(), interpreter_frame->pc());
    tty->print_cr("method: max_locals = %d, size_of_parameters = %d", method->max_locals(), method->size_of_parameters());
    tty->print_cr("caller_actual_parameters = %d", caller_actual_parameters);
    tty->print_cr("locals = %p", locals);
  }

#ifdef ASSERT
  if (caller_actual_parameters != method->size_of_parameters()) {
    assert(caller->is_interpreted_frame(), "adjusted caller_actual_parameters, but caller is not interpreter frame");
    Bytecode_invoke inv(caller->interpreter_frame_method(), caller->interpreter_frame_bci());

    if (is_bottom_frame) {
      assert(caller_actual_parameters == 0, "invalid adjusted caller_actual_parameters value for bottom frame");
      assert(inv.is_invokedynamic() || inv.is_invokehandle(), "adjusted caller_actual_parameters for bottom frame, but not invokedynamic/invokehandle");
    } else {
      assert(caller_actual_parameters == method->size_of_parameters()+1, "invalid adjusted caller_actual_parameters value");
      assert(!inv.is_invokedynamic() && MethodHandles::has_member_arg(inv.klass(), inv.name()), "adjusted caller_actual_parameters, but no member arg");
    }
  }
  if (caller->is_interpreted_frame()) {
    intptr_t* locals_base = (locals - method->max_locals()*Interpreter::stackElementWords + 1);
    locals_base = (intptr_t*)round_down((intptr_t)locals_base, StackAlignmentInBytes);
    assert(interpreter_frame->sender_sp() <= locals_base, "interpreter-to-interpreter frame chaining");

  } else if (caller->is_compiled_frame()) {
    assert(locals + 1 <= caller->unextended_sp(), "compiled-to-interpreter frame chaining");

  } else {
    assert(caller->is_entry_frame(), "should be");
    assert(locals + 1 <= caller->fp(), "entry-to-interpreter frame chaining");
  }
#endif // ASSERT

#else
  intptr_t* locals = interpreter_frame->sender_sp() + max_locals - 1;
#endif // AARCH64

  interpreter_frame->interpreter_frame_set_locals(locals);
  BasicObjectLock* montop = interpreter_frame->interpreter_frame_monitor_begin();
  BasicObjectLock* monbot = montop - moncount;
  interpreter_frame->interpreter_frame_set_monitor_end(monbot);

  // Set last_sp
  intptr_t* stack_top = (intptr_t*) monbot  -
    tempcount*Interpreter::stackElementWords -
    popframe_extra_args;
#ifdef AARCH64
  interpreter_frame->interpreter_frame_set_stack_top(stack_top);

  intptr_t* extended_sp = (intptr_t*) monbot  -
    (method->max_stack() + 1) * Interpreter::stackElementWords - // +1 is reserved slot for exception handler
    popframe_extra_args;
  extended_sp = (intptr_t*)round_down((intptr_t)extended_sp, StackAlignmentInBytes);
  interpreter_frame->interpreter_frame_set_extended_sp(extended_sp);
#else
  interpreter_frame->interpreter_frame_set_last_sp(stack_top);
#endif // AARCH64

  // All frames but the initial (oldest) interpreter frame we fill in have a
  // value for sender_sp that allows walking the stack but isn't
  // truly correct. Correct the value here.

#ifdef AARCH64
  if (caller->is_interpreted_frame()) {
    intptr_t* sender_sp = (intptr_t*)round_down((intptr_t)caller->interpreter_frame_tos_address(), StackAlignmentInBytes);
    interpreter_frame->set_interpreter_frame_sender_sp(sender_sp);

  } else {
    // in case of non-interpreter caller sender_sp of the oldest frame is already
    // set to valid value
  }
#else
  if (extra_locals != 0 &&
      interpreter_frame->sender_sp() == interpreter_frame->interpreter_frame_sender_sp() ) {
    interpreter_frame->set_interpreter_frame_sender_sp(caller->sp() + extra_locals);
  }
#endif // AARCH64

  *interpreter_frame->interpreter_frame_cache_addr() =
    method->constants()->cache();
  *interpreter_frame->interpreter_frame_mirror_addr() =
    method->method_holder()->java_mirror();
}
