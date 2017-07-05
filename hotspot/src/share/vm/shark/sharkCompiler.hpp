/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2008, 2009 Red Hat, Inc.
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

#ifndef SHARE_VM_SHARK_SHARKCOMPILER_HPP
#define SHARE_VM_SHARK_SHARKCOMPILER_HPP

#include "ci/ciEnv.hpp"
#include "ci/ciMethod.hpp"
#include "compiler/abstractCompiler.hpp"
#include "compiler/compileBroker.hpp"
#include "shark/llvmHeaders.hpp"
#include "shark/sharkMemoryManager.hpp"

class SharkContext;

class SharkCompiler : public AbstractCompiler {
 public:
  // Creation
  SharkCompiler();

  // Name of this compiler
  const char *name()     { return "shark"; }

  // Missing feature tests
  bool supports_native() { return true; }
  bool supports_osr()    { return true; }

  // Customization
  bool needs_adapters()  { return false; }
  bool needs_stubs()     { return false; }

  // Initialization
  void initialize();

  // Compile a normal (bytecode) method and install it in the VM
  void compile_method(ciEnv* env, ciMethod* target, int entry_bci);

  // Generate a wrapper for a native (JNI) method
  nmethod* generate_native_wrapper(MacroAssembler* masm,
                                   methodHandle    target,
                                   BasicType*      arg_types,
                                   BasicType       return_type);

  // Free compiled methods (and native wrappers)
  void free_compiled_method(address code);

  // Each thread generating IR needs its own context.  The normal
  // context is used for bytecode methods, and is protected from
  // multiple simultaneous accesses by being restricted to the
  // compiler thread.  The native context is used for JNI methods,
  // and is protected from multiple simultaneous accesses by the
  // adapter handler library lock.
 private:
  SharkContext* _normal_context;
  SharkContext* _native_context;

 public:
  SharkContext* context() const {
    if (JavaThread::current()->is_Compiler_thread()) {
      return _normal_context;
    }
    else {
      assert(AdapterHandlerLibrary_lock->owned_by_self(), "should be");
      return _native_context;
    }
  }

  // The LLVM execution engine is the JIT we use to generate native
  // code.  It is thread safe, but we need to protect it with a lock
  // of our own because otherwise LLVM's lock and HotSpot's locks
  // interleave and deadlock.  The SharkMemoryManager is not thread
  // safe, and is protected by the same lock as the execution engine.
 private:
  Monitor*               _execution_engine_lock;
  SharkMemoryManager*    _memory_manager;
  llvm::ExecutionEngine* _execution_engine;

 private:
  Monitor* execution_engine_lock() const {
    return _execution_engine_lock;
  }
  SharkMemoryManager* memory_manager() const {
    assert(execution_engine_lock()->owned_by_self(), "should be");
    return _memory_manager;
  }
  llvm::ExecutionEngine* execution_engine() const {
    assert(execution_engine_lock()->owned_by_self(), "should be");
    return _execution_engine;
  }

  // Global access
 public:
  static SharkCompiler* compiler() {
    AbstractCompiler *compiler = CompileBroker::compiler(CompLevel_simple);
    assert(compiler->is_shark() && compiler->is_initialized(), "should be");
    return (SharkCompiler *) compiler;
  }

  // Helpers
 private:
  static const char* methodname(const char* klass, const char* method);
  void generate_native_code(SharkEntry*     entry,
                            llvm::Function* function,
                            const char*     name);
  void free_queued_methods();
};

#endif // SHARE_VM_SHARK_SHARKCOMPILER_HPP
