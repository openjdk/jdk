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
  NOT_PRODUCT(static void trace_interpreter(const methodHandle& method, intptr_t* fp, address bcp, uintptr_t tos, uintptr_t tos2, outputStream* st);)
  static void print_method_codes(const methodHandle& method, int from, int to, outputStream* st, int flags, bool buffered = true);
};

// Provides tracing-centric context whose lifespan exceeds the printing of
// a single bytecode. For instance, it is needed to determine method switches
// in order to print the appropriate signature once a switch happens.
class BytecodeTracerData : public CHeapObj<mtTracing> {
 private:
  Method*         _current_method; // for method switches
  intptr_t*       _current_fp;     // for self-recursion
  bool            _is_wide;        // to parse the next bytecode properly

 public:
  BytecodeTracerData() : _current_method(nullptr),
                         _current_fp(nullptr),
                         _is_wide(false) {}

  // This field is not GC-ed, and so can contain garbage
  // between critical sections.  Use only pointer-comparison
  // operations on the pointer, except within a critical section.
  Method*         current_method() const                 { return _current_method; }
  void            set_current_method(Method* current)    { _current_method = current; }

  // This field should only ever be used for pointer comparison
  // not be dereferenced.
  intptr_t*       current_fp() const                     { return _current_fp; }
  void            set_current_fp(intptr_t* current)      { _current_fp = current; }

  bool            is_wide() const                        { return _is_wide; }
  void            set_wide(bool wide)                    { _is_wide = wide; }
};

#endif // SHARE_INTERPRETER_BYTECODETRACER_HPP
