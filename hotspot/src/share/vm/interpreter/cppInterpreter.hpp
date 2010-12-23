/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_INTERPRETER_CPPINTERPRETER_HPP
#define SHARE_VM_INTERPRETER_CPPINTERPRETER_HPP

#include "interpreter/abstractInterpreter.hpp"

#ifdef CC_INTERP

// This file contains the platform-independent parts
// of the c++ interpreter

class CppInterpreter: public AbstractInterpreter {
  friend class VMStructs;
  friend class Interpreter; // contains()
  friend class InterpreterGenerator; // result handlers
  friend class CppInterpreterGenerator; // result handlers
 public:


 protected:

  // tosca result -> stack result
  static address    _tosca_to_stack[number_of_result_handlers];  // converts tosca to C++ interpreter stack result
  // stack result -> stack result
  static address    _stack_to_stack[number_of_result_handlers];  // pass result between C++ interpreter calls
  // stack result -> native abi result
  static address    _stack_to_native_abi[number_of_result_handlers];  // converts C++ interpreter results to native abi

  // this is to allow frame and only frame to use contains().
  friend class      frame;

 public:
  // Initialization/debugging
  static void       initialize();
  // this only returns whether a pc is within generated code for the interpreter.

  // This is a moderately dubious interface for the c++ interpreter. Only
  // frame code and debug.cpp should be using it.
  static bool       contains(address pc);

 public:


  // No displatch table to switch so no need for these to do anything special
  static void notice_safepoints() {}
  static void ignore_safepoints() {}

  static address    native_result_to_tosca()                    { return (address)_native_abi_to_tosca; } // aka result handler
  static address    tosca_result_to_stack()                     { return (address)_tosca_to_stack; }
  static address    stack_result_to_stack()                     { return (address)_stack_to_stack; }
  static address    stack_result_to_native()                    { return (address)_stack_to_native_abi; }

  static address    native_result_to_tosca(int index)           { return _native_abi_to_tosca[index]; } // aka result handler
  static address    tosca_result_to_stack(int index)            { return _tosca_to_stack[index]; }
  static address    stack_result_to_stack(int index)            { return _stack_to_stack[index]; }
  static address    stack_result_to_native(int index)           { return _stack_to_native_abi[index]; }

  static address    return_entry  (TosState state, int length);
  static address    deopt_entry   (TosState state, int length);

#ifdef TARGET_ARCH_x86
# include "cppInterpreter_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "cppInterpreter_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "cppInterpreter_zero.hpp"
#endif


};

#endif // CC_INTERP

#endif // SHARE_VM_INTERPRETER_CPPINTERPRETER_HPP
