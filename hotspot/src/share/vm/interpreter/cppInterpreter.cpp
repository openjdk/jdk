/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_cppInterpreter.cpp.incl"

#ifdef CC_INTERP
# define __ _masm->

void CppInterpreter::initialize() {
  if (_code != NULL) return;
  AbstractInterpreter::initialize();

  // generate interpreter
  { ResourceMark rm;
    TraceTime timer("Interpreter generation", TraceStartupTime);
    int code_size = InterpreterCodeSize;
    NOT_PRODUCT(code_size *= 4;)  // debug uses extra interpreter code space
    _code = new StubQueue(new InterpreterCodeletInterface, code_size, NULL,
                          "Interpreter");
    InterpreterGenerator g(_code);
    if (PrintInterpreter) print();
  }


  // Allow c++ interpreter to do one initialization now that switches are set, etc.
  BytecodeInterpreter start_msg(BytecodeInterpreter::initialize);
  if (JvmtiExport::can_post_interpreter_events())
    BytecodeInterpreter::runWithChecks(&start_msg);
  else
    BytecodeInterpreter::run(&start_msg);
}


address    CppInterpreter::_tosca_to_stack         [AbstractInterpreter::number_of_result_handlers];
address    CppInterpreter::_stack_to_stack         [AbstractInterpreter::number_of_result_handlers];
address    CppInterpreter::_stack_to_native_abi    [AbstractInterpreter::number_of_result_handlers];

CppInterpreterGenerator::CppInterpreterGenerator(StubQueue* _code): AbstractInterpreterGenerator(_code) {
}

static const BasicType types[Interpreter::number_of_result_handlers] = {
  T_BOOLEAN,
  T_CHAR   ,
  T_BYTE   ,
  T_SHORT  ,
  T_INT    ,
  T_LONG   ,
  T_VOID   ,
  T_FLOAT  ,
  T_DOUBLE ,
  T_OBJECT
};

void CppInterpreterGenerator::generate_all() {
  AbstractInterpreterGenerator::generate_all();

  { CodeletMark cm(_masm, "result handlers for native calls");
    // The various result converter stublets.
    int is_generated[Interpreter::number_of_result_handlers];
    memset(is_generated, 0, sizeof(is_generated));
    int _tosca_to_stack_is_generated[Interpreter::number_of_result_handlers];
    int _stack_to_stack_is_generated[Interpreter::number_of_result_handlers];
    int _stack_to_native_abi_is_generated[Interpreter::number_of_result_handlers];

    memset(_tosca_to_stack_is_generated, 0, sizeof(_tosca_to_stack_is_generated));
    memset(_stack_to_stack_is_generated, 0, sizeof(_stack_to_stack_is_generated));
    memset(_stack_to_native_abi_is_generated, 0, sizeof(_stack_to_native_abi_is_generated));
    for (int i = 0; i < Interpreter::number_of_result_handlers; i++) {
      BasicType type = types[i];
      if (!is_generated[Interpreter::BasicType_as_index(type)]++) {
        Interpreter::_native_abi_to_tosca[Interpreter::BasicType_as_index(type)] = generate_result_handler_for(type);
      }
      if (!_tosca_to_stack_is_generated[Interpreter::BasicType_as_index(type)]++) {
        Interpreter::_tosca_to_stack[Interpreter::BasicType_as_index(type)] = generate_tosca_to_stack_converter(type);
      }
      if (!_stack_to_stack_is_generated[Interpreter::BasicType_as_index(type)]++) {
        Interpreter::_stack_to_stack[Interpreter::BasicType_as_index(type)] = generate_stack_to_stack_converter(type);
      }
      if (!_stack_to_native_abi_is_generated[Interpreter::BasicType_as_index(type)]++) {
        Interpreter::_stack_to_native_abi[Interpreter::BasicType_as_index(type)] = generate_stack_to_native_abi_converter(type);
      }
    }
  }


#define method_entry(kind) Interpreter::_entry_table[Interpreter::kind] = generate_method_entry(Interpreter::kind)

  { CodeletMark cm(_masm, "(kind = frame_manager)");
    // all non-native method kinds
    method_entry(zerolocals);
    method_entry(zerolocals_synchronized);
    method_entry(empty);
    method_entry(accessor);
    method_entry(abstract);
    method_entry(method_handle);
    method_entry(java_lang_math_sin   );
    method_entry(java_lang_math_cos   );
    method_entry(java_lang_math_tan   );
    method_entry(java_lang_math_abs   );
    method_entry(java_lang_math_sqrt  );
    method_entry(java_lang_math_log   );
    method_entry(java_lang_math_log10 );
    Interpreter::_native_entry_begin = Interpreter::code()->code_end();
    method_entry(native);
    method_entry(native_synchronized);
    Interpreter::_native_entry_end = Interpreter::code()->code_end();
  }


#undef method_entry

}

#endif // CC_INTERP
