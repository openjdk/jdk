/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "precompiled.hpp"
#include "compiler/compiler_globals.hpp"
#include "memory/metaspaceClosure.hpp"
#include "oops/methodCounters.hpp"
#include "runtime/handles.inline.hpp"

MethodCounters::MethodCounters(const methodHandle& mh) :
#if INCLUDE_AOT
  _method(mh()),
#endif
  _nmethod_age(INT_MAX)
#ifdef TIERED
  , _rate(0),
  _prev_time(0),
  _highest_comp_level(0),
  _highest_osr_comp_level(0)
#endif
{
  set_interpreter_invocation_count(0);
  set_interpreter_throwout_count(0);
  JVMTI_ONLY(clear_number_of_breakpoints());
  invocation_counter()->init();
  backedge_counter()->init();

  if (StressCodeAging) {
    set_nmethod_age(HotMethodDetectionLimit);
  }

  // Set per-method thresholds.
  double scale = 1.0;
  CompilerOracle::has_option_value(mh, CompileCommand::CompileThresholdScaling, scale);

  int compile_threshold = CompilerConfig::scaled_compile_threshold(CompileThreshold, scale);
  _interpreter_invocation_limit = compile_threshold << InvocationCounter::count_shift;
  if (ProfileInterpreter) {
    // If interpreter profiling is enabled, the backward branch limit
    // is compared against the method data counter rather than an invocation
    // counter, therefore no shifting of bits is required.
    _interpreter_backward_branch_limit = (int)((int64_t)compile_threshold * (OnStackReplacePercentage - InterpreterProfilePercentage) / 100);
  } else {
    _interpreter_backward_branch_limit = (int)(((int64_t)compile_threshold * OnStackReplacePercentage / 100) << InvocationCounter::count_shift);
  }
  _interpreter_profile_limit = ((compile_threshold * InterpreterProfilePercentage) / 100) << InvocationCounter::count_shift;
  _invoke_mask = right_n_bits(CompilerConfig::scaled_freq_log(Tier0InvokeNotifyFreqLog, scale)) << InvocationCounter::count_shift;
  _backedge_mask = right_n_bits(CompilerConfig::scaled_freq_log(Tier0BackedgeNotifyFreqLog, scale)) << InvocationCounter::count_shift;
}

MethodCounters* MethodCounters::allocate(const methodHandle& mh, TRAPS) {
  ClassLoaderData* loader_data = mh->method_holder()->class_loader_data();
  return new(loader_data, method_counters_size(), MetaspaceObj::MethodCountersType, THREAD) MethodCounters(mh);
}

void MethodCounters::clear_counters() {
  invocation_counter()->reset();
  backedge_counter()->reset();
  set_interpreter_throwout_count(0);
  set_interpreter_invocation_count(0);
  set_nmethod_age(INT_MAX);
#ifdef TIERED
  set_prev_time(0);
  set_rate(0);
  set_highest_comp_level(0);
  set_highest_osr_comp_level(0);
#endif
}


int MethodCounters::highest_comp_level() const {
#ifdef TIERED
  return _highest_comp_level;
#else
  return CompLevel_none;
#endif
}

void MethodCounters::set_highest_comp_level(int level) {
#ifdef TIERED
  _highest_comp_level = level;
#endif
}

int MethodCounters::highest_osr_comp_level() const {
#ifdef TIERED
  return _highest_osr_comp_level;
#else
  return CompLevel_none;
#endif
}

void MethodCounters::set_highest_osr_comp_level(int level) {
#ifdef TIERED
  _highest_osr_comp_level = level;
#endif
}

void MethodCounters::metaspace_pointers_do(MetaspaceClosure* it) {
  log_trace(cds)("Iter(MethodCounters): %p", this);
#if INCLUDE_AOT
  it->push(&_method);
#endif
}

void MethodCounters::print_value_on(outputStream* st) const {
  assert(is_methodCounters(), "must be methodCounters");
  st->print("method counters");
  print_address_on(st);
}


