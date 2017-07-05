/*
 * Copyright (c) 1999, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_COMPILER_ABSTRACTCOMPILER_HPP
#define SHARE_VM_COMPILER_ABSTRACTCOMPILER_HPP

#include "ci/compilerInterface.hpp"

class AbstractCompiler : public CHeapObj<mtCompiler> {
 private:
  volatile int _num_compiler_threads;

 protected:
  volatile int _compiler_state;
  // Used for tracking global state of compiler runtime initialization
  enum { uninitialized, initializing, initialized, failed, shut_down };

  // This method returns true for the first compiler thread that reaches that methods.
  // This thread will initialize the compiler runtime.
  bool should_perform_init();

  // The (closed set) of concrete compiler classes.
  enum Type {
    none,
    c1,
    c2,
    shark
  };

 private:
  Type _type;

 public:
  AbstractCompiler(Type type) : _type(type), _compiler_state(uninitialized), _num_compiler_threads(0) {}

  // This function determines the compiler thread that will perform the
  // shutdown of the corresponding compiler runtime.
  bool should_perform_shutdown();

  // Name of this compiler
  virtual const char* name() = 0;

  // Missing feature tests
  virtual bool supports_native()                 { return true; }
  virtual bool supports_osr   ()                 { return true; }
  virtual bool can_compile_method(methodHandle method)  { return true; }

  // Determine if the current compiler provides an intrinsic
  // for method 'method'. An intrinsic is available if:
  //  - the intrinsic is enabled (by using the appropriate command-line flag) and
  //  - the platform on which the VM is running supports the intrinsic
  //    (i.e., the platform provides the instructions necessary for the compiler
  //    to generate the intrinsic code).
  //
  // The second parameter, 'compilation_context', is needed to implement functionality
  // related to the DisableIntrinsic command-line flag. The DisableIntrinsic flag can
  // be used to prohibit the C2 compiler (but not the C1 compiler) to use an intrinsic.
  // There are three ways to disable an intrinsic using the DisableIntrinsic flag:
  //
  // (1) -XX:DisableIntrinsic=_hashCode,_getClass
  //     Disables intrinsification of _hashCode and _getClass globally
  //     (i.e., the intrinsified version the methods will not be used at all).
  // (2) -XX:CompileCommand=option,aClass::aMethod,ccstr,DisableIntrinsic,_hashCode
  //     Disables intrinsification of _hashCode if it is called from
  //     aClass::aMethod (but not for any other call site of _hashCode)
  // (3) -XX:CompileCommand=option,java.lang.ref.Reference::get,ccstr,DisableIntrinsic,_Reference_get
  //     Some methods are not compiled by C2. Instead, the C2 compiler
  //     returns directly the intrinsified version of these methods.
  //     The command above forces C2 to compile _Reference_get, but
  //     allows using the intrinsified version of _Reference_get at all
  //     other call sites.
  //
  // From the modes above, (1) disable intrinsics globally, (2) and (3)
  // disable intrinsics on a per-method basis. In cases (2) and (3) the
  // compilation context is aClass::aMethod and java.lang.ref.Reference::get,
  // respectively.
  virtual bool is_intrinsic_available(methodHandle method, methodHandle compilation_context) {
    return false;
  }

  // Determines if an intrinsic is supported by the compiler, that is,
  // the compiler provides the instructions necessary to generate
  // the intrinsic code for method 'method'.
  //
  // The 'is_intrinsic_supported' method is a white list, that is,
  // by default no intrinsics are supported by a compiler except
  // the ones listed in the method. Overriding methods should conform
  // to this behavior.
  virtual bool is_intrinsic_supported(methodHandle method) {
    return false;
  }

  // Implements compiler-specific processing of command-line flags.
  // Processing of command-line flags common to all compilers is implemented
  // in vmIntrinsicss::is_disabled_by_flag.
  virtual bool is_intrinsic_disabled_by_flag(methodHandle method) {
    return false;
  }

  // Compiler type queries.
  bool is_c1()                                   { return _type == c1; }
  bool is_c2()                                   { return _type == c2; }
  bool is_shark()                                { return _type == shark; }

  // Customization
  virtual void initialize () = 0;

  void set_num_compiler_threads(int num) { _num_compiler_threads = num;  }
  int num_compiler_threads()             { return _num_compiler_threads; }

  // Get/set state of compiler objects
  bool is_initialized()           { return _compiler_state == initialized; }
  bool is_failed     ()           { return _compiler_state == failed;}
  void set_state     (int state);
  void set_shut_down ()           { set_state(shut_down); }
  // Compilation entry point for methods
  virtual void compile_method(ciEnv* env, ciMethod* target, int entry_bci) {
    ShouldNotReachHere();
  }


  // Print compilation timers and statistics
  virtual void print_timers() {
    ShouldNotReachHere();
  }
};

#endif // SHARE_VM_COMPILER_ABSTRACTCOMPILER_HPP
