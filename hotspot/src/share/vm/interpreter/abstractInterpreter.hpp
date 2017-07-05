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

#ifndef SHARE_VM_INTERPRETER_ABSTRACTINTERPRETER_HPP
#define SHARE_VM_INTERPRETER_ABSTRACTINTERPRETER_HPP

#include "code/stubs.hpp"
#include "interpreter/bytecodes.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/top.hpp"
#ifdef TARGET_ARCH_x86
# include "interp_masm_x86.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_sparc
# include "interp_masm_sparc.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_zero
# include "interp_masm_zero.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_arm
# include "interp_masm_arm.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_ppc
# include "interp_masm_ppc.hpp"
#endif

// This file contains the platform-independent parts
// of the abstract interpreter and the abstract interpreter generator.

// Organization of the interpreter(s). There exists two different interpreters in hotpot
// an assembly language version (aka template interpreter) and a high level language version
// (aka c++ interpreter). Th division of labor is as follows:

// Template Interpreter          C++ Interpreter        Functionality
//
// templateTable*                bytecodeInterpreter*   actual interpretation of bytecodes
//
// templateInterpreter*          cppInterpreter*        generation of assembly code that creates
//                                                      and manages interpreter runtime frames.
//                                                      Also code for populating interpreter
//                                                      frames created during deoptimization.
//
// For both template and c++ interpreter. There are common files for aspects of the interpreter
// that are generic to both interpreters. This is the layout:
//
// abstractInterpreter.hpp: generic description of the interpreter.
// interpreter*:            generic frame creation and handling.
//

//------------------------------------------------------------------------------------------------------------------------
// The C++ interface to the bytecode interpreter(s).

class AbstractInterpreter: AllStatic {
  friend class VMStructs;
  friend class Interpreter;
  friend class CppInterpreterGenerator;
 public:
  enum MethodKind {
    zerolocals,                                                 // method needs locals initialization
    zerolocals_synchronized,                                    // method needs locals initialization & is synchronized
    native,                                                     // native method
    native_synchronized,                                        // native method & is synchronized
    empty,                                                      // empty method (code: _return)
    accessor,                                                   // accessor method (code: _aload_0, _getfield, _(a|i)return)
    abstract,                                                   // abstract method (throws an AbstractMethodException)
    method_handle_invoke_FIRST,                                 // java.lang.invoke.MethodHandles::invokeExact, etc.
    method_handle_invoke_LAST                                   = (method_handle_invoke_FIRST
                                                                   + (vmIntrinsics::LAST_MH_SIG_POLY
                                                                      - vmIntrinsics::FIRST_MH_SIG_POLY)),
    java_lang_math_sin,                                         // implementation of java.lang.Math.sin   (x)
    java_lang_math_cos,                                         // implementation of java.lang.Math.cos   (x)
    java_lang_math_tan,                                         // implementation of java.lang.Math.tan   (x)
    java_lang_math_abs,                                         // implementation of java.lang.Math.abs   (x)
    java_lang_math_sqrt,                                        // implementation of java.lang.Math.sqrt  (x)
    java_lang_math_log,                                         // implementation of java.lang.Math.log   (x)
    java_lang_math_log10,                                       // implementation of java.lang.Math.log10 (x)
    java_lang_math_pow,                                         // implementation of java.lang.Math.pow   (x,y)
    java_lang_math_exp,                                         // implementation of java.lang.Math.exp   (x)
    java_lang_ref_reference_get,                                // implementation of java.lang.ref.Reference.get()
    java_util_zip_CRC32_update,                                 // implementation of java.util.zip.CRC32.update()
    java_util_zip_CRC32_updateBytes,                            // implementation of java.util.zip.CRC32.updateBytes()
    java_util_zip_CRC32_updateByteBuffer,                       // implementation of java.util.zip.CRC32.updateByteBuffer()
    number_of_method_entries,
    invalid = -1
  };

  // Conversion from the part of the above enum to vmIntrinsics::_invokeExact, etc.
  static vmIntrinsics::ID method_handle_intrinsic(MethodKind kind) {
    if (kind >= method_handle_invoke_FIRST && kind <= method_handle_invoke_LAST)
      return (vmIntrinsics::ID)( vmIntrinsics::FIRST_MH_SIG_POLY + (kind - method_handle_invoke_FIRST) );
    else
      return vmIntrinsics::_none;
  }

  enum SomeConstants {
    number_of_result_handlers = 10                              // number of result handlers for native calls
  };

 protected:
  static StubQueue* _code;                                      // the interpreter code (codelets)

  static bool       _notice_safepoints;                         // true if safepoints are activated

  static address    _native_entry_begin;                        // Region for native entry code
  static address    _native_entry_end;

  // method entry points
  static address    _entry_table[number_of_method_entries];     // entry points for a given method
  static address    _native_abi_to_tosca[number_of_result_handlers];  // for native method result handlers
  static address    _slow_signature_handler;                              // the native method generic (slow) signature handler

  static address    _rethrow_exception_entry;                   // rethrows an activation in previous frame

  friend class      AbstractInterpreterGenerator;
  friend class              InterpreterGenerator;
  friend class      InterpreterMacroAssembler;

 public:
  // Initialization/debugging
  static void       initialize();
  static StubQueue* code()                                      { return _code; }


  // Method activation
  static MethodKind method_kind(methodHandle m);
  static address    entry_for_kind(MethodKind k)                { assert(0 <= k && k < number_of_method_entries, "illegal kind"); return _entry_table[k]; }
  static address    entry_for_method(methodHandle m)            { return entry_for_kind(method_kind(m)); }

  // used for bootstrapping method handles:
  static void       set_entry_for_kind(MethodKind k, address e);

  static void       print_method_kind(MethodKind kind)          PRODUCT_RETURN;

  static bool       can_be_compiled(methodHandle m);

  // Runtime support

  // length = invoke bytecode length (to advance to next bytecode)
  static address deopt_entry(TosState state, int length) { ShouldNotReachHere(); return NULL; }
  static address return_entry(TosState state, int length, Bytecodes::Code code) { ShouldNotReachHere(); return NULL; }

  static address    rethrow_exception_entry()                   { return _rethrow_exception_entry; }

  // Activation size in words for a method that is just being called.
  // Parameters haven't been pushed so count them too.
  static int        size_top_interpreter_activation(Method* method);

  // Deoptimization support
  // Compute the entry address for continuation after
  static address deopt_continue_after_entry(Method* method,
                                            address bcp,
                                            int callee_parameters,
                                            bool is_top_frame);
  // Compute the entry address for reexecution
  static address deopt_reexecute_entry(Method* method, address bcp);
  // Deoptimization should reexecute this bytecode
  static bool    bytecode_should_reexecute(Bytecodes::Code code);

  // share implementation of size_activation and layout_activation:
  static int        size_activation(Method* method,
                                    int temps,
                                    int popframe_args,
                                    int monitors,
                                    int caller_actual_parameters,
                                    int callee_params,
                                    int callee_locals,
                                    bool is_top_frame,
                                    bool is_bottom_frame) {
    return layout_activation(method,
                             temps,
                             popframe_args,
                             monitors,
                             caller_actual_parameters,
                             callee_params,
                             callee_locals,
                             (frame*)NULL,
                             (frame*)NULL,
                             is_top_frame,
                             is_bottom_frame);
  }

  static int       layout_activation(Method* method,
                                     int temps,
                                     int popframe_args,
                                     int monitors,
                                     int caller_actual_parameters,
                                     int callee_params,
                                     int callee_locals,
                                     frame* caller,
                                     frame* interpreter_frame,
                                     bool is_top_frame,
                                     bool is_bottom_frame);

  // Runtime support
  static bool       is_not_reached(                       methodHandle method, int bci);
  // Safepoint support
  static void       notice_safepoints()                         { ShouldNotReachHere(); } // stops the thread when reaching a safepoint
  static void       ignore_safepoints()                         { ShouldNotReachHere(); } // ignores safepoints

  // Support for native calls
  static address    slow_signature_handler()                    { return _slow_signature_handler; }
  static address    result_handler(BasicType type)              { return _native_abi_to_tosca[BasicType_as_index(type)]; }
  static int        BasicType_as_index(BasicType type);         // computes index into result_handler_by_index table
  static bool       in_native_entry(address pc)                 { return _native_entry_begin <= pc && pc < _native_entry_end; }
  // Debugging/printing
  static void       print();                                    // prints the interpreter code

 public:
  // Interpreter helpers
  const static int stackElementWords   = 1;
  const static int stackElementSize    = stackElementWords * wordSize;
  const static int logStackElementSize = LogBytesPerWord;

  // Local values relative to locals[n]
  static int  local_offset_in_bytes(int n) {
    return ((frame::interpreter_frame_expression_stack_direction() * n) * stackElementSize);
  }

  // access to stacked values according to type:
  static oop* oop_addr_in_slot(intptr_t* slot_addr) {
    return (oop*) slot_addr;
  }
  static jint* int_addr_in_slot(intptr_t* slot_addr) {
    if ((int) sizeof(jint) < wordSize && !Bytes::is_Java_byte_ordering_different())
      // big-endian LP64
      return (jint*)(slot_addr + 1) - 1;
    else
      return (jint*) slot_addr;
  }
  static jlong long_in_slot(intptr_t* slot_addr) {
    if (sizeof(intptr_t) >= sizeof(jlong)) {
      return *(jlong*) slot_addr;
    } else {
      return Bytes::get_native_u8((address)slot_addr);
    }
  }
  static void set_long_in_slot(intptr_t* slot_addr, jlong value) {
    if (sizeof(intptr_t) >= sizeof(jlong)) {
      *(jlong*) slot_addr = value;
    } else {
      Bytes::put_native_u8((address)slot_addr, value);
    }
  }
  static void get_jvalue_in_slot(intptr_t* slot_addr, BasicType type, jvalue* value) {
    switch (type) {
    case T_BOOLEAN: value->z = *int_addr_in_slot(slot_addr);            break;
    case T_CHAR:    value->c = *int_addr_in_slot(slot_addr);            break;
    case T_BYTE:    value->b = *int_addr_in_slot(slot_addr);            break;
    case T_SHORT:   value->s = *int_addr_in_slot(slot_addr);            break;
    case T_INT:     value->i = *int_addr_in_slot(slot_addr);            break;
    case T_LONG:    value->j = long_in_slot(slot_addr);                 break;
    case T_FLOAT:   value->f = *(jfloat*)int_addr_in_slot(slot_addr);   break;
    case T_DOUBLE:  value->d = jdouble_cast(long_in_slot(slot_addr));   break;
    case T_OBJECT:  value->l = (jobject)*oop_addr_in_slot(slot_addr);   break;
    default:        ShouldNotReachHere();
    }
  }
  static void set_jvalue_in_slot(intptr_t* slot_addr, BasicType type, jvalue* value) {
    switch (type) {
    case T_BOOLEAN: *int_addr_in_slot(slot_addr) = (value->z != 0);     break;
    case T_CHAR:    *int_addr_in_slot(slot_addr) = value->c;            break;
    case T_BYTE:    *int_addr_in_slot(slot_addr) = value->b;            break;
    case T_SHORT:   *int_addr_in_slot(slot_addr) = value->s;            break;
    case T_INT:     *int_addr_in_slot(slot_addr) = value->i;            break;
    case T_LONG:    set_long_in_slot(slot_addr, value->j);              break;
    case T_FLOAT:   *(jfloat*)int_addr_in_slot(slot_addr) = value->f;   break;
    case T_DOUBLE:  set_long_in_slot(slot_addr, jlong_cast(value->d));  break;
    case T_OBJECT:  *oop_addr_in_slot(slot_addr) = (oop) value->l;      break;
    default:        ShouldNotReachHere();
    }
  }
};

//------------------------------------------------------------------------------------------------------------------------
// The interpreter generator.

class Template;
class AbstractInterpreterGenerator: public StackObj {
 protected:
  InterpreterMacroAssembler* _masm;

  // shared code sequences
  // Converter for native abi result to tosca result
  address generate_result_handler_for(BasicType type);
  address generate_slow_signature_handler();

  // entry point generator
  address generate_method_entry(AbstractInterpreter::MethodKind kind);

  void bang_stack_shadow_pages(bool native_call);

  void generate_all();
  void initialize_method_handle_entries();

 public:
  AbstractInterpreterGenerator(StubQueue* _code);
};

#endif // SHARE_VM_INTERPRETER_ABSTRACTINTERPRETER_HPP
