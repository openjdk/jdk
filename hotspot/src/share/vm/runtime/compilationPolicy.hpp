/*
 * Copyright (c) 2000, 2006, Oracle and/or its affiliates. All rights reserved.
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

class CompilationPolicy : public CHeapObj {
 private:
  static CompilationPolicy* _policy;
  // Accumulated time
  static elapsedTimer       _accumulated_time;

  static bool               _in_vm_startup;

 public:
  virtual void method_invocation_event(methodHandle m, TRAPS) = 0;
  virtual void method_back_branch_event(methodHandle m, int branch_bci, int loop_top_bci, TRAPS) = 0;
  virtual int compilation_level(methodHandle m, int branch_bci) = 0;

  void reset_counter_for_invocation_event(methodHandle method);
  void reset_counter_for_back_branch_event(methodHandle method);

  static  void set_in_vm_startup(bool in_vm_startup) { _in_vm_startup = in_vm_startup; }
  static  void completed_vm_startup();
  static  bool delayCompilationDuringStartup() { return _in_vm_startup; }

  static bool mustBeCompiled(methodHandle m);      // m must be compiled before executing it
  static bool canBeCompiled(methodHandle m);       // m is allowed to be compiled

  static void set_policy(CompilationPolicy* policy) { _policy = policy; }
  static CompilationPolicy* policy() { return _policy; }

  // Profiling
  elapsedTimer* accumulated_time() { return &_accumulated_time; }
  void print_time() PRODUCT_RETURN;
};

class SimpleCompPolicy : public CompilationPolicy {
 public:
  void method_invocation_event( methodHandle m, TRAPS);
  void method_back_branch_event(methodHandle m, int branch_bci, int loop_top_bci, TRAPS);
  int compilation_level(methodHandle m, int branch_bci);
};

// StackWalkCompPolicy - existing C2 policy

#ifdef COMPILER2
class StackWalkCompPolicy : public CompilationPolicy {
 public:
  void method_invocation_event(methodHandle m, TRAPS);
  void method_back_branch_event(methodHandle m, int branch_bci, int loop_top_bci, TRAPS);
  int compilation_level(methodHandle m, int branch_bci);

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
