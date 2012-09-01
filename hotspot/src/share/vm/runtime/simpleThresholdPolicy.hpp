/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_SIMPLETHRESHOLDPOLICY_HPP
#define SHARE_VM_RUNTIME_SIMPLETHRESHOLDPOLICY_HPP

#include "code/nmethod.hpp"
#include "oops/methodData.hpp"
#include "runtime/compilationPolicy.hpp"
#include "utilities/globalDefinitions.hpp"

class CompileTask;
class CompileQueue;

class SimpleThresholdPolicy : public CompilationPolicy {
  int _c1_count, _c2_count;

  // Check if the counter is big enough and set carry (effectively infinity).
  inline void set_carry_if_necessary(InvocationCounter *counter);
  // Set carry flags in the counters (in Method* and MDO).
  inline void handle_counter_overflow(Method* method);
  // Call and loop predicates determine whether a transition to a higher compilation
  // level should be performed (pointers to predicate functions are passed to common_TF().
  // Predicates also take compiler load into account.
  typedef bool (SimpleThresholdPolicy::*Predicate)(int i, int b, CompLevel cur_level);
  bool call_predicate(int i, int b, CompLevel cur_level);
  bool loop_predicate(int i, int b, CompLevel cur_level);
  // Common transition function. Given a predicate determines if a method should transition to another level.
  CompLevel common(Predicate p, Method* method, CompLevel cur_level);
  // Transition functions.
  // call_event determines if a method should be compiled at a different
  // level with a regular invocation entry.
  CompLevel call_event(Method* method, CompLevel cur_level);
  // loop_event checks if a method should be OSR compiled at a different
  // level.
  CompLevel loop_event(Method* method, CompLevel cur_level);
  void print_counters(const char* prefix, methodHandle mh);
protected:
  int c1_count() const     { return _c1_count; }
  int c2_count() const     { return _c2_count; }
  void set_c1_count(int x) { _c1_count = x;    }
  void set_c2_count(int x) { _c2_count = x;    }

  enum EventType { CALL, LOOP, COMPILE, REMOVE_FROM_QUEUE, UPDATE_IN_QUEUE, REPROFILE, MAKE_NOT_ENTRANT };
  void print_event(EventType type, methodHandle mh, methodHandle imh, int bci, CompLevel level);
  // Print policy-specific information if necessary
  virtual void print_specific(EventType type, methodHandle mh, methodHandle imh, int bci, CompLevel level) { }
  // Check if the method can be compiled, change level if necessary
  void compile(methodHandle mh, int bci, CompLevel level, JavaThread* thread);
  // Submit a given method for compilation
  virtual void submit_compile(methodHandle mh, int bci, CompLevel level, JavaThread* thread);
  // Simple methods are as good being compiled with C1 as C2.
  // This function tells if it's such a function.
  inline bool is_trivial(Method* method);

  // Predicate helpers are used by .*_predicate() methods as well as others.
  // They check the given counter values, multiplied by the scale against the thresholds.
  template<CompLevel level> static inline bool call_predicate_helper(int i, int b, double scale);
  template<CompLevel level> static inline bool loop_predicate_helper(int i, int b, double scale);

  // Get a compilation level for a given method.
  static CompLevel comp_level(Method* method) {
    nmethod *nm = method->code();
    if (nm != NULL && nm->is_in_use()) {
      return (CompLevel)nm->comp_level();
    }
    return CompLevel_none;
  }
  virtual void method_invocation_event(methodHandle method, methodHandle inlinee,
                                       CompLevel level, nmethod* nm, JavaThread* thread);
  virtual void method_back_branch_event(methodHandle method, methodHandle inlinee,
                                        int bci, CompLevel level, nmethod* nm, JavaThread* thread);
public:
  SimpleThresholdPolicy() : _c1_count(0), _c2_count(0) { }
  virtual int compiler_count(CompLevel comp_level) {
    if (is_c1_compile(comp_level)) return c1_count();
    if (is_c2_compile(comp_level)) return c2_count();
    return 0;
  }
  virtual CompLevel initial_compile_level() { return MIN2((CompLevel)TieredStopAtLevel, CompLevel_initial_compile); }
  virtual void do_safepoint_work() { }
  virtual void delay_compilation(Method* method) { }
  virtual void disable_compilation(Method* method) { }
  virtual void reprofile(ScopeDesc* trap_scope, bool is_osr);
  virtual nmethod* event(methodHandle method, methodHandle inlinee,
                         int branch_bci, int bci, CompLevel comp_level, nmethod* nm, JavaThread* thread);
  // Select task is called by CompileBroker. We should return a task or NULL.
  virtual CompileTask* select_task(CompileQueue* compile_queue);
  // Tell the runtime if we think a given method is adequately profiled.
  virtual bool is_mature(Method* method);
  // Initialize: set compiler thread count
  virtual void initialize();
  virtual bool should_not_inline(ciEnv* env, ciMethod* callee) {
    return (env->comp_level() == CompLevel_limited_profile ||
            env->comp_level() == CompLevel_full_profile) &&
            callee->has_loops();
  }
};

#endif // SHARE_VM_RUNTIME_SIMPLETHRESHOLDPOLICY_HPP
