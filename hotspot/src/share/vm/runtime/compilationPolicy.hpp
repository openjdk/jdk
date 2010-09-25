/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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

// The CompilationPolicy selects which method (if any) should be compiled.
// It also decides which methods must always be compiled (i.e., are never
// interpreted).
class CompileTask;
class CompileQueue;

class CompilationPolicy : public CHeapObj {
  static CompilationPolicy* _policy;
  // Accumulated time
  static elapsedTimer       _accumulated_time;

  static bool               _in_vm_startup;
public:
  static  void set_in_vm_startup(bool in_vm_startup) { _in_vm_startup = in_vm_startup; }
  static  void completed_vm_startup();
  static  bool delay_compilation_during_startup()    { return _in_vm_startup; }

  // m must be compiled before executing it
  static bool must_be_compiled(methodHandle m, int comp_level = CompLevel_all);
  // m is allowed to be compiled
  static bool can_be_compiled(methodHandle m, int comp_level = CompLevel_all);
  static bool is_compilation_enabled();
  static void set_policy(CompilationPolicy* policy) { _policy = policy; }
  static CompilationPolicy* policy()                { return _policy; }

  // Profiling
  elapsedTimer* accumulated_time() { return &_accumulated_time; }
  void print_time() PRODUCT_RETURN;
  virtual int compiler_count(CompLevel comp_level) = 0;
  // main notification entry, return a pointer to an nmethod if the OSR is required,
  // returns NULL otherwise.
  virtual nmethod* event(methodHandle method, methodHandle inlinee, int branch_bci, int bci, CompLevel comp_level, TRAPS) = 0;
  // safepoint() is called at the end of the safepoint
  virtual void do_safepoint_work() = 0;
  // reprofile request
  virtual void reprofile(ScopeDesc* trap_scope, bool is_osr) = 0;
  // delay_compilation(method) can be called by any component of the runtime to notify the policy
  // that it's recommended to delay the complation of this method.
  virtual void delay_compilation(methodOop method) = 0;
  // disable_compilation() is called whenever the runtime decides to disable compilation of the
  // specified method.
  virtual void disable_compilation(methodOop method) = 0;
  // Select task is called by CompileBroker. The queue is guaranteed to have at least one
  // element and is locked. The function should select one and return it.
  virtual CompileTask* select_task(CompileQueue* compile_queue) = 0;
  // Tell the runtime if we think a given method is adequately profiled.
  virtual bool is_mature(methodOop method) = 0;
  // Do policy initialization
  virtual void initialize() = 0;
};

// A base class for baseline policies.
class NonTieredCompPolicy : public CompilationPolicy {
  int _compiler_count;
protected:
  static void trace_frequency_counter_overflow(methodHandle m, int branch_bci, int bci);
  static void trace_osr_request(methodHandle method, nmethod* osr, int bci);
  static void trace_osr_completion(nmethod* osr_nm);
  void reset_counter_for_invocation_event(methodHandle method);
  void reset_counter_for_back_branch_event(methodHandle method);
public:
  NonTieredCompPolicy() : _compiler_count(0) { }
  virtual int compiler_count(CompLevel comp_level);
  virtual void do_safepoint_work();
  virtual void reprofile(ScopeDesc* trap_scope, bool is_osr);
  virtual void delay_compilation(methodOop method);
  virtual void disable_compilation(methodOop method);
  virtual bool is_mature(methodOop method);
  virtual void initialize();
  virtual CompileTask* select_task(CompileQueue* compile_queue);
  virtual nmethod* event(methodHandle method, methodHandle inlinee, int branch_bci, int bci, CompLevel comp_level, TRAPS);
  virtual void method_invocation_event(methodHandle m, TRAPS) = 0;
  virtual void method_back_branch_event(methodHandle m, int bci, TRAPS) = 0;
};

class SimpleCompPolicy : public NonTieredCompPolicy {
 public:
  virtual void method_invocation_event(methodHandle m, TRAPS);
  virtual void method_back_branch_event(methodHandle m, int bci, TRAPS);
};

// StackWalkCompPolicy - existing C2 policy

#ifdef COMPILER2
class StackWalkCompPolicy : public NonTieredCompPolicy {
 public:
  virtual void method_invocation_event(methodHandle m, TRAPS);
  virtual void method_back_branch_event(methodHandle m, int bci, TRAPS);

 private:
  RFrame* findTopInlinableFrame(GrowableArray<RFrame*>* stack);
  RFrame* senderOf(RFrame* rf, GrowableArray<RFrame*>* stack);

  // the following variables hold values computed by the last inlining decision
  // they are used for performance debugging only (print better messages)
  static const char* _msg;            // reason for not inlining

  static const char* shouldInline   (methodHandle callee, float frequency, int cnt);
  // positive filter: should send be inlined?  returns NULL (--> yes) or rejection msg
  static const char* shouldNotInline(methodHandle callee);
  // negative filter: should send NOT be inlined?  returns NULL (--> inline) or rejection msg

};
#endif
