/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_INTERPRETER_BYTECODETRACER_HPP
#define SHARE_INTERPRETER_BYTECODETRACER_HPP

#include "interpreter/bytecodes.hpp"
#include "memory/allStatic.hpp"
#include "memory/allocation.hpp"
#include "nmt/memTag.hpp"
#include "opto/subnode.hpp"
#include "utilities/globalDefinitions.hpp"

class Method;
class methodHandle;
class outputStream;
class BytecodeClosure;

// The BytecodeTracer is a helper class used by the interpreter for run-time
// bytecode tracing. If TraceBytecodes turned on, trace_interpreter() will be called
// for each bytecode.
class BytecodeTracer: AllStatic {
 public:
  NOT_PRODUCT(static void trace_interpreter(const methodHandle& method, address bcp, uintptr_t tos, uintptr_t tos2, outputStream* st);)
  static void print_method_codes(const methodHandle& method, int from, int to, outputStream* st, int flags, bool buffered = true);
};

// Provides tracing-centric context on the current method and bytecode that
// the thread is interpreting.
class BytecodeTracerData : public CHeapObj<mtTracing> {
 private:
  Method*         _current_method;
  bool            _is_wide;
  Bytecodes::Code _raw_code;
  address         _next_pc;

 public:
  BytecodeTracerData() : _current_method(nullptr),
                         _is_wide(false),
                         _raw_code(Bytecodes::Code::_illegal),
                         _next_pc(nullptr) {}

  Method*         current_method() const                 { return _current_method; }
  void            set_current_method(Method* current)    { _current_method = current; }
  bool            is_wide() const                        { return _is_wide; }
  void            set_wide(bool wide)                    { _is_wide = wide; }
  Bytecodes::Code raw_code()                             { return _raw_code; }
  void            set_raw_code(Bytecodes::Code raw_code) { _raw_code = Bytecodes::Code(raw_code); }
  address         next_pc() const                        { return _next_pc; }
  void            set_next_pc(address next_pc)           { _next_pc = next_pc; }
};

#endif // SHARE_INTERPRETER_BYTECODETRACER_HPP
