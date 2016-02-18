/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_VM_JVMCI_JVMCI_COMPILER_HPP
#define SHARE_VM_JVMCI_JVMCI_COMPILER_HPP

#include "compiler/abstractCompiler.hpp"
#include "jvmci/jvmciEnv.hpp"
#include "utilities/exceptions.hpp"

class JVMCICompiler : public AbstractCompiler {
private:
  bool _bootstrapping;

  /**
   * Number of methods successfully compiled by a call to
   * JVMCICompiler::compile_method().
   */
  volatile int _methods_compiled;

  static JVMCICompiler* _instance;

  static elapsedTimer _codeInstallTimer;

  static void abort_on_pending_exception(Handle exception, const char* message, bool dump_core = false);

public:
  JVMCICompiler();

  static JVMCICompiler* instance(TRAPS) {
    if (!EnableJVMCI) {
      THROW_MSG_NULL(vmSymbols::java_lang_InternalError(), "JVMCI is not enabled")
    }
    return _instance;
  }

  virtual const char* name() { return "JVMCI"; }

  virtual bool supports_native()                 { return true; }
  virtual bool supports_osr   ()                 { return true; }

  bool is_jvmci()                                { return true; }
  bool is_c1   ()                                { return false; }
  bool is_c2   ()                                { return false; }

  bool needs_stubs            () { return false; }

  // Initialization
  virtual void initialize();

  void bootstrap();

  // Compilation entry point for methods
  virtual void compile_method(ciEnv* env, ciMethod* target, int entry_bci, DirectiveSet* directive);

  void compile_method(const methodHandle& target, int entry_bci, JVMCIEnv* env);

  virtual bool is_trivial(Method* method);

  // Print compilation timers and statistics
  virtual void print_timers();

  /**
   * Gets the number of methods that have been successfully compiled by
   * a call to JVMCICompiler::compile_method().
   */
  int methods_compiled() { return _methods_compiled; }

  // Print compilation timers and statistics
  static void print_compilation_timers();

  static elapsedTimer* codeInstallTimer() { return &_codeInstallTimer; }
};

#endif // SHARE_VM_JVMCI_JVMCI_COMPILER_HPP
