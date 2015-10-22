/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_METHODCOUNTERS_HPP
#define SHARE_VM_OOPS_METHODCOUNTERS_HPP

#include "oops/metadata.hpp"
#include "compiler/compilerOracle.hpp"
#include "interpreter/invocationCounter.hpp"
#include "runtime/arguments.hpp"

class MethodCounters: public MetaspaceObj {
 friend class VMStructs;
 private:
  int               _interpreter_invocation_count; // Count of times invoked (reused as prev_event_count in tiered)
  u2                _interpreter_throwout_count; // Count of times method was exited via exception while interpreting
  u2                _number_of_breakpoints;      // fullspeed debugging support
  InvocationCounter _invocation_counter;         // Incremented before each activation of the method - used to trigger frequency-based optimizations
  InvocationCounter _backedge_counter;           // Incremented before each backedge taken - used to trigger frequencey-based optimizations
  // NMethod age is a counter for warm methods detection in the code cache sweeper.
  // The counter is reset by the sweeper and is decremented by some of the compiled
  // code. The counter values are interpreted as follows:
  // 1. (HotMethodDetection..INT_MAX] - initial value, no counters inserted
  // 2. [1..HotMethodDetectionLimit)  - the method is warm, the counter is used
  //                                    to figure out which methods can be flushed.
  // 3. (INT_MIN..0]                  - method is hot and will deopt and get
  //                                    recompiled without the counters
  int               _nmethod_age;
  int               _interpreter_invocation_limit;        // per-method InterpreterInvocationLimit
  int               _interpreter_backward_branch_limit;   // per-method InterpreterBackwardBranchLimit
  int               _interpreter_profile_limit;           // per-method InterpreterProfileLimit
  int               _invoke_mask;                         // per-method Tier0InvokeNotifyFreqLog
  int               _backedge_mask;                       // per-method Tier0BackedgeNotifyFreqLog
#ifdef TIERED
  float             _rate;                        // Events (invocation and backedge counter increments) per millisecond
  jlong             _prev_time;                   // Previous time the rate was acquired
  u1                _highest_comp_level;          // Highest compile level this method has ever seen.
  u1                _highest_osr_comp_level;      // Same for OSR level
#endif

  MethodCounters(methodHandle mh) : _interpreter_invocation_count(0),
                                    _interpreter_throwout_count(0),
                                    _number_of_breakpoints(0),
                                    _nmethod_age(INT_MAX)
#ifdef TIERED
                                 , _rate(0),
                                   _prev_time(0),
                                   _highest_comp_level(0),
                                   _highest_osr_comp_level(0)
#endif
  {
    invocation_counter()->init();
    backedge_counter()->init();

    if (StressCodeAging) {
      set_nmethod_age(HotMethodDetectionLimit);
    }

    // Set per-method thresholds.
    double scale = 1.0;
    CompilerOracle::has_option_value(mh, "CompileThresholdScaling", scale);

    int compile_threshold = Arguments::scaled_compile_threshold(CompileThreshold, scale);
    _interpreter_invocation_limit = compile_threshold << InvocationCounter::count_shift;
    if (ProfileInterpreter) {
      // If interpreter profiling is enabled, the backward branch limit
      // is compared against the method data counter rather than an invocation
      // counter, therefore no shifting of bits is required.
      _interpreter_backward_branch_limit = (compile_threshold * (OnStackReplacePercentage - InterpreterProfilePercentage)) / 100;
    } else {
      _interpreter_backward_branch_limit = ((compile_threshold * OnStackReplacePercentage) / 100) << InvocationCounter::count_shift;
    }
    _interpreter_profile_limit = ((compile_threshold * InterpreterProfilePercentage) / 100) << InvocationCounter::count_shift;
    _invoke_mask = right_n_bits(Arguments::scaled_freq_log(Tier0InvokeNotifyFreqLog, scale)) << InvocationCounter::count_shift;
    _backedge_mask = right_n_bits(Arguments::scaled_freq_log(Tier0BackedgeNotifyFreqLog, scale)) << InvocationCounter::count_shift;
  }

 public:
  static MethodCounters* allocate(methodHandle mh, TRAPS);

  void deallocate_contents(ClassLoaderData* loader_data) {}
  DEBUG_ONLY(bool on_stack() { return false; })  // for template

  static int size() { return sizeof(MethodCounters) / wordSize; }

  bool is_klass() const { return false; }

  void clear_counters();

  int interpreter_invocation_count() {
    return _interpreter_invocation_count;
  }
  void set_interpreter_invocation_count(int count) {
    _interpreter_invocation_count = count;
  }
  int increment_interpreter_invocation_count() {
    return ++_interpreter_invocation_count;
  }

  void interpreter_throwout_increment() {
    if (_interpreter_throwout_count < 65534) {
      _interpreter_throwout_count++;
    }
  }
  int  interpreter_throwout_count() const {
    return _interpreter_throwout_count;
  }
  void set_interpreter_throwout_count(int count) {
    _interpreter_throwout_count = count;
  }

  u2   number_of_breakpoints() const   { return _number_of_breakpoints; }
  void incr_number_of_breakpoints()    { ++_number_of_breakpoints; }
  void decr_number_of_breakpoints()    { --_number_of_breakpoints; }
  void clear_number_of_breakpoints()   { _number_of_breakpoints = 0; }

#ifdef TIERED
  jlong prev_time() const                        { return _prev_time; }
  void set_prev_time(jlong time)                 { _prev_time = time; }
  float rate() const                             { return _rate; }
  void set_rate(float rate)                      { _rate = rate; }
#endif

  int highest_comp_level() const;
  void set_highest_comp_level(int level);
  int highest_osr_comp_level() const;
  void set_highest_osr_comp_level(int level);

  // invocation counter
  InvocationCounter* invocation_counter() { return &_invocation_counter; }
  InvocationCounter* backedge_counter()   { return &_backedge_counter; }

  int nmethod_age() {
    return _nmethod_age;
  }
  void set_nmethod_age(int age) {
    _nmethod_age = age;
  }
  void reset_nmethod_age() {
    set_nmethod_age(HotMethodDetectionLimit);
  }

  static bool is_nmethod_hot(int age)       { return age <= 0; }
  static bool is_nmethod_warm(int age)      { return age < HotMethodDetectionLimit; }
  static bool is_nmethod_age_unset(int age) { return age > HotMethodDetectionLimit; }

  static ByteSize nmethod_age_offset() {
    return byte_offset_of(MethodCounters, _nmethod_age);
  }

  static ByteSize interpreter_invocation_counter_offset() {
    return byte_offset_of(MethodCounters, _interpreter_invocation_count);
  }

  static ByteSize invocation_counter_offset()    {
    return byte_offset_of(MethodCounters, _invocation_counter);
  }

  static ByteSize backedge_counter_offset()      {
    return byte_offset_of(MethodCounters, _backedge_counter);
  }

  static int interpreter_invocation_counter_offset_in_bytes() {
    return offset_of(MethodCounters, _interpreter_invocation_count);
  }

  static ByteSize interpreter_invocation_limit_offset() {
    return byte_offset_of(MethodCounters, _interpreter_invocation_limit);
  }

  static ByteSize interpreter_backward_branch_limit_offset() {
    return byte_offset_of(MethodCounters, _interpreter_backward_branch_limit);
  }

  static ByteSize interpreter_profile_limit_offset() {
    return byte_offset_of(MethodCounters, _interpreter_profile_limit);
  }

  static ByteSize invoke_mask_offset() {
    return byte_offset_of(MethodCounters, _invoke_mask);
  }

  static ByteSize backedge_mask_offset() {
    return byte_offset_of(MethodCounters, _backedge_mask);
  }
};
#endif //SHARE_VM_OOPS_METHODCOUNTERS_HPP
