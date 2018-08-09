/*
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_TIEREDTHRESHOLDPOLICY_HPP
#define SHARE_VM_RUNTIME_TIEREDTHRESHOLDPOLICY_HPP

#include "code/nmethod.hpp"
#include "oops/methodData.hpp"
#include "runtime/compilationPolicy.hpp"
#include "utilities/globalDefinitions.hpp"

#ifdef TIERED

class CompileTask;
class CompileQueue;
/*
 *  The system supports 5 execution levels:
 *  * level 0 - interpreter
 *  * level 1 - C1 with full optimization (no profiling)
 *  * level 2 - C1 with invocation and backedge counters
 *  * level 3 - C1 with full profiling (level 2 + MDO)
 *  * level 4 - C2
 *
 * Levels 0, 2 and 3 periodically notify the runtime about the current value of the counters
 * (invocation counters and backedge counters). The frequency of these notifications is
 * different at each level. These notifications are used by the policy to decide what transition
 * to make.
 *
 * Execution starts at level 0 (interpreter), then the policy can decide either to compile the
 * method at level 3 or level 2. The decision is based on the following factors:
 *    1. The length of the C2 queue determines the next level. The observation is that level 2
 * is generally faster than level 3 by about 30%, therefore we would want to minimize the time
 * a method spends at level 3. We should only spend the time at level 3 that is necessary to get
 * adequate profiling. So, if the C2 queue is long enough it is more beneficial to go first to
 * level 2, because if we transitioned to level 3 we would be stuck there until our C2 compile
 * request makes its way through the long queue. When the load on C2 recedes we are going to
 * recompile at level 3 and start gathering profiling information.
 *    2. The length of C1 queue is used to dynamically adjust the thresholds, so as to introduce
 * additional filtering if the compiler is overloaded. The rationale is that by the time a
 * method gets compiled it can become unused, so it doesn't make sense to put too much onto the
 * queue.
 *
 * After profiling is completed at level 3 the transition is made to level 4. Again, the length
 * of the C2 queue is used as a feedback to adjust the thresholds.
 *
 * After the first C1 compile some basic information is determined about the code like the number
 * of the blocks and the number of the loops. Based on that it can be decided that a method
 * is trivial and compiling it with C1 will yield the same code. In this case the method is
 * compiled at level 1 instead of 4.
 *
 * We also support profiling at level 0. If C1 is slow enough to produce the level 3 version of
 * the code and the C2 queue is sufficiently small we can decide to start profiling in the
 * interpreter (and continue profiling in the compiled code once the level 3 version arrives).
 * If the profiling at level 0 is fully completed before level 3 version is produced, a level 2
 * version is compiled instead in order to run faster waiting for a level 4 version.
 *
 * Compile queues are implemented as priority queues - for each method in the queue we compute
 * the event rate (the number of invocation and backedge counter increments per unit of time).
 * When getting an element off the queue we pick the one with the largest rate. Maintaining the
 * rate also allows us to remove stale methods (the ones that got on the queue but stopped
 * being used shortly after that).
*/

/* Command line options:
 * - Tier?InvokeNotifyFreqLog and Tier?BackedgeNotifyFreqLog control the frequency of method
 *   invocation and backedge notifications. Basically every n-th invocation or backedge a mutator thread
 *   makes a call into the runtime.
 *
 * - Tier?InvocationThreshold, Tier?CompileThreshold, Tier?BackEdgeThreshold, Tier?MinInvocationThreshold control
 *   compilation thresholds.
 *   Level 2 thresholds are not used and are provided for option-compatibility and potential future use.
 *   Other thresholds work as follows:
 *
 *   Transition from interpreter (level 0) to C1 with full profiling (level 3) happens when
 *   the following predicate is true (X is the level):
 *
 *   i > TierXInvocationThreshold * s || (i > TierXMinInvocationThreshold * s  && i + b > TierXCompileThreshold * s),
 *
 *   where $i$ is the number of method invocations, $b$ number of backedges and $s$ is the scaling
 *   coefficient that will be discussed further.
 *   The intuition is to equalize the time that is spend profiling each method.
 *   The same predicate is used to control the transition from level 3 to level 4 (C2). It should be
 *   noted though that the thresholds are relative. Moreover i and b for the 0->3 transition come
 *   from Method* and for 3->4 transition they come from MDO (since profiled invocations are
 *   counted separately). Finally, if a method does not contain anything worth profiling, a transition
 *   from level 3 to level 4 occurs without considering thresholds (e.g., with fewer invocations than
 *   what is specified by Tier4InvocationThreshold).
 *
 *   OSR transitions are controlled simply with b > TierXBackEdgeThreshold * s predicates.
 *
 * - Tier?LoadFeedback options are used to automatically scale the predicates described above depending
 *   on the compiler load. The scaling coefficients are computed as follows:
 *
 *   s = queue_size_X / (TierXLoadFeedback * compiler_count_X) + 1,
 *
 *   where queue_size_X is the current size of the compiler queue of level X, and compiler_count_X
 *   is the number of level X compiler threads.
 *
 *   Basically these parameters describe how many methods should be in the compile queue
 *   per compiler thread before the scaling coefficient increases by one.
 *
 *   This feedback provides the mechanism to automatically control the flow of compilation requests
 *   depending on the machine speed, mutator load and other external factors.
 *
 * - Tier3DelayOn and Tier3DelayOff parameters control another important feedback loop.
 *   Consider the following observation: a method compiled with full profiling (level 3)
 *   is about 30% slower than a method at level 2 (just invocation and backedge counters, no MDO).
 *   Normally, the following transitions will occur: 0->3->4. The problem arises when the C2 queue
 *   gets congested and the 3->4 transition is delayed. While the method is the C2 queue it continues
 *   executing at level 3 for much longer time than is required by the predicate and at suboptimal speed.
 *   The idea is to dynamically change the behavior of the system in such a way that if a substantial
 *   load on C2 is detected we would first do the 0->2 transition allowing a method to run faster.
 *   And then when the load decreases to allow 2->3 transitions.
 *
 *   Tier3Delay* parameters control this switching mechanism.
 *   Tier3DelayOn is the number of methods in the C2 queue per compiler thread after which the policy
 *   no longer does 0->3 transitions but does 0->2 transitions instead.
 *   Tier3DelayOff switches the original behavior back when the number of methods in the C2 queue
 *   per compiler thread falls below the specified amount.
 *   The hysteresis is necessary to avoid jitter.
 *
 * - TieredCompileTaskTimeout is the amount of time an idle method can spend in the compile queue.
 *   Basically, since we use the event rate d(i + b)/dt as a value of priority when selecting a method to
 *   compile from the compile queue, we also can detect stale methods for which the rate has been
 *   0 for some time in the same iteration. Stale methods can appear in the queue when an application
 *   abruptly changes its behavior.
 *
 * - TieredStopAtLevel, is used mostly for testing. It allows to bypass the policy logic and stick
 *   to a given level. For example it's useful to set TieredStopAtLevel = 1 in order to compile everything
 *   with pure c1.
 *
 * - Tier0ProfilingStartPercentage allows the interpreter to start profiling when the inequalities in the
 *   0->3 predicate are already exceeded by the given percentage but the level 3 version of the
 *   method is still not ready. We can even go directly from level 0 to 4 if c1 doesn't produce a compiled
 *   version in time. This reduces the overall transition to level 4 and decreases the startup time.
 *   Note that this behavior is also guarded by the Tier3Delay mechanism: when the c2 queue is too long
 *   these is not reason to start profiling prematurely.
 *
 * - TieredRateUpdateMinTime and TieredRateUpdateMaxTime are parameters of the rate computation.
 *   Basically, the rate is not computed more frequently than TieredRateUpdateMinTime and is considered
 *   to be zero if no events occurred in TieredRateUpdateMaxTime.
 */

class TieredThresholdPolicy : public CompilationPolicy {
  jlong _start_time;
  int _c1_count, _c2_count;

  // Check if the counter is big enough and set carry (effectively infinity).
  inline void set_carry_if_necessary(InvocationCounter *counter);
  // Set carry flags in the counters (in Method* and MDO).
  inline void handle_counter_overflow(Method* method);
  // Call and loop predicates determine whether a transition to a higher compilation
  // level should be performed (pointers to predicate functions are passed to common_TF().
  // Predicates also take compiler load into account.
  typedef bool (TieredThresholdPolicy::*Predicate)(int i, int b, CompLevel cur_level, Method* method);
  bool call_predicate(int i, int b, CompLevel cur_level, Method* method);
  bool loop_predicate(int i, int b, CompLevel cur_level, Method* method);
  // Common transition function. Given a predicate determines if a method should transition to another level.
  CompLevel common(Predicate p, Method* method, CompLevel cur_level, bool disable_feedback = false);
  // Transition functions.
  // call_event determines if a method should be compiled at a different
  // level with a regular invocation entry.
  CompLevel call_event(Method* method, CompLevel cur_level, JavaThread* thread);
  // loop_event checks if a method should be OSR compiled at a different
  // level.
  CompLevel loop_event(Method* method, CompLevel cur_level, JavaThread* thread);
  void print_counters(const char* prefix, const methodHandle& mh);
  // Has a method been long around?
  // We don't remove old methods from the compile queue even if they have
  // very low activity (see select_task()).
  inline bool is_old(Method* method);
  // Was a given method inactive for a given number of milliseconds.
  // If it is, we would remove it from the queue (see select_task()).
  inline bool is_stale(jlong t, jlong timeout, Method* m);
  // Compute the weight of the method for the compilation scheduling
  inline double weight(Method* method);
  // Apply heuristics and return true if x should be compiled before y
  inline bool compare_methods(Method* x, Method* y);
  // Compute event rate for a given method. The rate is the number of event (invocations + backedges)
  // per millisecond.
  inline void update_rate(jlong t, Method* m);
  // Compute threshold scaling coefficient
  inline double threshold_scale(CompLevel level, int feedback_k);
  // If a method is old enough and is still in the interpreter we would want to
  // start profiling without waiting for the compiled method to arrive. This function
  // determines whether we should do that.
  inline bool should_create_mdo(Method* method, CompLevel cur_level);
  // Create MDO if necessary.
  void create_mdo(const methodHandle& mh, JavaThread* thread);
  // Is method profiled enough?
  bool is_method_profiled(Method* method);

  double _increase_threshold_at_ratio;

  bool maybe_switch_to_aot(const methodHandle& mh, CompLevel cur_level, CompLevel next_level, JavaThread* thread);

protected:
  int c1_count() const     { return _c1_count; }
  int c2_count() const     { return _c2_count; }
  void set_c1_count(int x) { _c1_count = x;    }
  void set_c2_count(int x) { _c2_count = x;    }

  enum EventType { CALL, LOOP, COMPILE, REMOVE_FROM_QUEUE, UPDATE_IN_QUEUE, REPROFILE, MAKE_NOT_ENTRANT };
  void print_event(EventType type, const methodHandle& mh, const methodHandle& imh, int bci, CompLevel level);
  // Print policy-specific information if necessary
  virtual void print_specific(EventType type, const methodHandle& mh, const methodHandle& imh, int bci, CompLevel level);
  // Check if the method can be compiled, change level if necessary
  void compile(const methodHandle& mh, int bci, CompLevel level, JavaThread* thread);
  // Submit a given method for compilation
  virtual void submit_compile(const methodHandle& mh, int bci, CompLevel level, JavaThread* thread);
  // Simple methods are as good being compiled with C1 as C2.
  // This function tells if it's such a function.
  inline bool is_trivial(Method* method);

  // Predicate helpers are used by .*_predicate() methods as well as others.
  // They check the given counter values, multiplied by the scale against the thresholds.
  template<CompLevel level> static inline bool call_predicate_helper(int i, int b, double scale, Method* method);
  template<CompLevel level> static inline bool loop_predicate_helper(int i, int b, double scale, Method* method);

  // Get a compilation level for a given method.
  static CompLevel comp_level(Method* method);
  virtual void method_invocation_event(const methodHandle& method, const methodHandle& inlinee,
                                       CompLevel level, CompiledMethod* nm, JavaThread* thread);
  virtual void method_back_branch_event(const methodHandle& method, const methodHandle& inlinee,
                                        int bci, CompLevel level, CompiledMethod* nm, JavaThread* thread);

  void set_increase_threshold_at_ratio() { _increase_threshold_at_ratio = 100 / (100 - (double)IncreaseFirstTierCompileThresholdAt); }
  void set_start_time(jlong t) { _start_time = t;    }
  jlong start_time() const     { return _start_time; }

public:
  TieredThresholdPolicy() : _start_time(0), _c1_count(0), _c2_count(0) { }
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
  virtual nmethod* event(const methodHandle& method, const methodHandle& inlinee,
                         int branch_bci, int bci, CompLevel comp_level, CompiledMethod* nm, JavaThread* thread);
  // Select task is called by CompileBroker. We should return a task or NULL.
  virtual CompileTask* select_task(CompileQueue* compile_queue);
  // Tell the runtime if we think a given method is adequately profiled.
  virtual bool is_mature(Method* method);
  // Initialize: set compiler thread count
  virtual void initialize();
  virtual bool should_not_inline(ciEnv* env, ciMethod* callee);
};

#endif // TIERED

#endif // SHARE_VM_RUNTIME_TIEREDTHRESHOLDPOLICY_HPP
