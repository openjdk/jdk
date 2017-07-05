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

#ifndef SHARE_VM_INTERPRETER_CPPINTERPRETER_HPP
#define SHARE_VM_INTERPRETER_CPPINTERPRETER_HPP

#include "interpreter/abstractInterpreter.hpp"
#ifdef CC_INTERP

class InterpreterCodelet;

// This file contains the platform-independent parts
// of the c++ interpreter

class CppInterpreter: public AbstractInterpreter {
  friend class VMStructs;
 public:
  // Initialization/debugging
  static void       initialize();
  // this only returns whether a pc is within generated code for the interpreter.

  // These are moderately dubious interfaces for the c++ interpreter. Only
  // frame code and debug.cpp should be using it.
  static bool       contains(address pc);
  static InterpreterCodelet* codelet_containing(address pc);

 public:


  // No displatch table to switch so no need for these to do anything special
  static void notice_safepoints() {}
  static void ignore_safepoints() {}

  static address    return_entry  (TosState state, int length, Bytecodes::Code code);
  static address    deopt_entry   (TosState state, int length);

  static void invoke_method(Method* method, address entry_point, TRAPS);
  static void invoke_osr(Method* method,
                         address   entry_point,
                         address   osr_buf,
                         TRAPS);
#ifdef TARGET_ARCH_zero
# include "cppInterpreter_zero.hpp"
#endif

};

#endif // CC_INTERP

#endif // SHARE_VM_INTERPRETER_CPPINTERPRETER_HPP
