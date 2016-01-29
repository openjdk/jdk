/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/bytecodeInterpreter.hpp"
#include "interpreter/cppInterpreterGenerator.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"

#ifdef CC_INTERP

#ifdef ZERO
# include "entry_zero.hpp"
#else
#error "Only Zero CppInterpreter is supported"
#endif

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
    CppInterpreterGenerator g(_code);
    if (PrintInterpreter) print();
  }

  // Allow c++ interpreter to do one initialization now that switches are set, etc.
  BytecodeInterpreter start_msg(BytecodeInterpreter::initialize);
  if (JvmtiExport::can_post_interpreter_events())
    BytecodeInterpreter::runWithChecks(&start_msg);
  else
    BytecodeInterpreter::run(&start_msg);
}


void CppInterpreter::invoke_method(Method* method, address entry_point, TRAPS) {
  ((ZeroEntry *) entry_point)->invoke(method, THREAD);
}

void CppInterpreter::invoke_osr(Method* method,
                                address   entry_point,
                                address   osr_buf,
                                TRAPS) {
  ((ZeroEntry *) entry_point)->invoke_osr(method, osr_buf, THREAD);
}



InterpreterCodelet* CppInterpreter::codelet_containing(address pc) {
  // FIXME: I'm pretty sure _code is null and this is never called, which is why it's copied.
  return (InterpreterCodelet*)_code->stub_containing(pc);
}

#endif // CC_INTERP
