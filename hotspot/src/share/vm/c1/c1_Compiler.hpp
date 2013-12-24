/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_C1_C1_COMPILER_HPP
#define SHARE_VM_C1_C1_COMPILER_HPP

#include "compiler/abstractCompiler.hpp"

// There is one instance of the Compiler per CompilerThread.

class Compiler: public AbstractCompiler {
 private:
  static void init_c1_runtime();
  BufferBlob* init_buffer_blob();

 public:
  // Creation
  Compiler();
  ~Compiler();

  // Name of this compiler
  virtual const char* name()                     { return "C1"; }

  virtual bool is_c1()                           { return true; };

  // Missing feature tests
  virtual bool supports_native()                 { return true; }
  virtual bool supports_osr   ()                 { return true; }

  // Initialization
  virtual void initialize();

  // Compilation entry point for methods
  virtual void compile_method(ciEnv* env, ciMethod* target, int entry_bci);

  // Print compilation timers and statistics
  virtual void print_timers();
};

#endif // SHARE_VM_C1_C1_COMPILER_HPP
