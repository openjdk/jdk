/*
 * Copyright (c) 2000, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_COMPILER_COMPILATIONPOLICY_HPP
#define SHARE_COMPILER_COMPILATIONPOLICY_HPP

#include "code/nmethod.hpp"
#include "compiler/compileBroker.hpp"
#include "memory/allocation.hpp"
#include "runtime/vmOperations.hpp"
#include "utilities/growableArray.hpp"

// The CompilationPolicy selects which method (if any) should be compiled.
// It also decides which methods must always be compiled (i.e., are never
// interpreted).
class CompileTask;
class CompileQueue;

class CompilationPolicy : public CHeapObj<mtCompiler> {
  static CompilationPolicy* _policy;

  // m must be compiled before executing it
  static bool must_be_compiled(const methodHandle& m, int comp_level = CompLevel_all);

public:
  // If m must_be_compiled then request a compilation from the CompileBroker.
  // This supports the -Xcomp option.
  static void compile_if_required(const methodHandle& m, TRAPS);

  // m is allowed to be compiled
  static bool can_be_compiled(const methodHandle& m, int comp_level = CompLevel_all);
  // m is allowed to be osr compiled
  static bool can_be_osr_compiled(const methodHandle& m, int comp_level = CompLevel_all);
  static bool is_compilation_enabled();
  static void set_policy(CompilationPolicy* policy) { _policy = policy; }
  static CompilationPolicy* policy()                { return _policy; }

  static CompileTask* select_task_helper(CompileQueue* compile_queue);

  // Return initial compile level that is used with Xcomp
  virtual CompLevel initial_compile_level(const methodHandle& method) = 0;
  virtual int compiler_count(CompLevel comp_level) = 0;
  // main notification entry, return a pointer to an nmethod if the OSR is required,
  // returns NULL otherwise.
  virtual nmethod* event(const methodHandle& method, const methodHandle& inlinee, int branch_bci, int bci, CompLevel comp_level, CompiledMethod* nm, JavaThread* thread) = 0;
  // safepoint() is called at the end of the safepoint
  virtual void do_safepoint_work() = 0;
  // reprofile request
  virtual void reprofile(ScopeDesc* trap_scope, bool is_osr) = 0;
  // delay_compilation(method) can be called by any component of the runtime to notify the policy
  // that it's recommended to delay the compilation of this method.
  virtual void delay_compilation(Method* method) = 0;
  // Select task is called by CompileBroker. The queue is guaranteed to have at least one
  // element and is locked. The function should select one and return it.
  virtual CompileTask* select_task(CompileQueue* compile_queue) = 0;
  // Tell the runtime if we think a given method is adequately profiled.
  virtual bool is_mature(Method* method) = 0;
  // Do policy initialization
  virtual void initialize() = 0;
  virtual bool should_not_inline(ciEnv* env, ciMethod* method) { return false; }
};

// A simple compilation policy.
class SimpleCompPolicy : public CompilationPolicy {
  int _compiler_count;
 private:
  static void trace_frequency_counter_overflow(const methodHandle& m, int branch_bci, int bci);
  static void trace_osr_request(const methodHandle& method, nmethod* osr, int bci);
  static void trace_osr_completion(nmethod* osr_nm);
  void reset_counter_for_invocation_event(const methodHandle& method);
  void reset_counter_for_back_branch_event(const methodHandle& method);
  void method_invocation_event(const methodHandle& m, JavaThread* thread);
  void method_back_branch_event(const methodHandle& m, int bci, JavaThread* thread);
 public:
  SimpleCompPolicy() : _compiler_count(0) { }
  virtual CompLevel initial_compile_level(const methodHandle& m) { return CompLevel_highest_tier; }
  virtual int compiler_count(CompLevel comp_level);
  virtual void do_safepoint_work();
  virtual void reprofile(ScopeDesc* trap_scope, bool is_osr);
  virtual void delay_compilation(Method* method);
  virtual bool is_mature(Method* method);
  virtual void initialize();
  virtual CompileTask* select_task(CompileQueue* compile_queue);
  virtual nmethod* event(const methodHandle& method, const methodHandle& inlinee, int branch_bci, int bci, CompLevel comp_level, CompiledMethod* nm, JavaThread* thread);
};


#endif // SHARE_COMPILER_COMPILATIONPOLICY_HPP
