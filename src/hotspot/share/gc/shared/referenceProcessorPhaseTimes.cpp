/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/referenceProcessorPhaseTimes.hpp"
#include "gc/shared/referenceProcessor.inline.hpp"
#include "gc/shared/workerDataArray.inline.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"

RefProcWorkerTimeTracker::RefProcWorkerTimeTracker(ReferenceProcessorPhaseTimes::RefProcPhaseNumbers number,
                                                   ReferenceProcessorPhaseTimes* phase_times,
                                                   uint worker_id) :
  _worker_time(NULL), _start_time(os::elapsedTime()), _worker_id(worker_id) {
  assert (phase_times != NULL, "Invariant");

  _worker_time = phase_times->worker_time_sec(phase_times->par_phase(number));
}

RefProcWorkerTimeTracker::RefProcWorkerTimeTracker(ReferenceProcessorPhaseTimes::RefProcParPhases phase,
                                                   ReferenceProcessorPhaseTimes* phase_times,
                                                   uint worker_id) :
  _worker_time(NULL), _start_time(os::elapsedTime()), _worker_id(worker_id) {
  assert (phase_times != NULL, "Invariant");

  _worker_time = phase_times->worker_time_sec(phase);
}

RefProcWorkerTimeTracker::~RefProcWorkerTimeTracker() {
  _worker_time->set(_worker_id, os::elapsedTime() - _start_time);
}

RefProcPhaseTimeBaseTracker::RefProcPhaseTimeBaseTracker(const char* title,
                                                         ReferenceProcessorPhaseTimes* phase_times) :
  _title(title), _phase_times(phase_times), _start_ticks(), _end_ticks() {
  assert(_phase_times != NULL, "Invariant");

  _start_ticks.stamp();
  if (_phase_times->gc_timer() != NULL) {
    _phase_times->gc_timer()->register_gc_phase_start(_title, _start_ticks);
  }
}

static const char* phase_enum_2_phase_string(ReferenceProcessorPhaseTimes::RefProcParPhases phase) {
  switch(phase) {
    case ReferenceProcessorPhaseTimes::SoftRefPhase1:
      return "Phase1";
    case ReferenceProcessorPhaseTimes::SoftRefPhase2:
    case ReferenceProcessorPhaseTimes::WeakRefPhase2:
    case ReferenceProcessorPhaseTimes::FinalRefPhase2:
    case ReferenceProcessorPhaseTimes::PhantomRefPhase2:
      return "Phase2";
    case ReferenceProcessorPhaseTimes::SoftRefPhase3:
    case ReferenceProcessorPhaseTimes::WeakRefPhase3:
    case ReferenceProcessorPhaseTimes::FinalRefPhase3:
    case ReferenceProcessorPhaseTimes::PhantomRefPhase3:
      return "Phase3";
    default:
      ShouldNotReachHere();
      return NULL;
  }
}

static const char* Indents[6] = {"", "  ", "    ", "      ", "        ", "          "};

Ticks RefProcPhaseTimeBaseTracker::end_ticks() {
  // If ASSERT is defined, the default value of Ticks will be -2.
  if (_end_ticks.value() <= 0) {
    _end_ticks.stamp();
  }

  return _end_ticks;
}

double RefProcPhaseTimeBaseTracker::elapsed_time() {
  jlong end_value = end_ticks().value();

  return TimeHelper::counter_to_millis(end_value - _start_ticks.value());
}

RefProcPhaseTimeBaseTracker::~RefProcPhaseTimeBaseTracker() {
  if (_phase_times->gc_timer() != NULL) {
    Ticks ticks = end_ticks();
    _phase_times->gc_timer()->register_gc_phase_end(ticks);
  }
}

RefProcBalanceQueuesTimeTracker::RefProcBalanceQueuesTimeTracker(ReferenceProcessorPhaseTimes* phase_times) :
  RefProcPhaseTimeBaseTracker("Balance queues", phase_times) {}

RefProcBalanceQueuesTimeTracker::~RefProcBalanceQueuesTimeTracker() {
  double elapsed = elapsed_time();
  phase_times()->set_balance_queues_time_ms(phase_times()->processing_ref_type(), elapsed);
}

#define ASSERT_REF_TYPE(ref_type) assert(ref_type >= REF_SOFT && ref_type <= REF_PHANTOM, \
                                         "Invariant (%d)", (int)ref_type)

#define ASSERT_PHASE_NUMBER(phase_number) assert(phase_number >= ReferenceProcessorPhaseTimes::RefPhase1 && \
                                                 phase_number <= ReferenceProcessorPhaseTimes::RefPhaseMax, \
                                                 "Invariant (%d)", phase_number);

static const char* phase_number_2_string(ReferenceProcessorPhaseTimes::RefProcPhaseNumbers phase_number) {
  ASSERT_PHASE_NUMBER(phase_number);

  switch(phase_number) {
    case ReferenceProcessorPhaseTimes::RefPhase1:
      return "Phase1";
    case ReferenceProcessorPhaseTimes::RefPhase2:
      return "Phase2";
    case ReferenceProcessorPhaseTimes::RefPhase3:
      return "Phase3";
    default:
      ShouldNotReachHere();
      return NULL;
  }
}

RefProcParPhaseTimeTracker::RefProcParPhaseTimeTracker(ReferenceProcessorPhaseTimes::RefProcPhaseNumbers phase_number,
                                                       ReferenceProcessorPhaseTimes* phase_times) :
  _phase_number(phase_number),
  RefProcPhaseTimeBaseTracker(phase_number_2_string(phase_number), phase_times) {}

RefProcParPhaseTimeTracker::~RefProcParPhaseTimeTracker() {
  double elapsed = elapsed_time();
  ReferenceProcessorPhaseTimes::RefProcParPhases phase = phase_times()->par_phase(_phase_number);
  phase_times()->set_par_phase_time_ms(phase, elapsed);
}

static const char* ref_type_2_string(ReferenceType ref_type) {
  ASSERT_REF_TYPE(ref_type);

  switch(ref_type) {
    case REF_SOFT:
      return "SoftReference";
    case REF_WEAK:
      return "WeakReference";
    case REF_FINAL:
      return "FinalReference";
    case REF_PHANTOM:
      return "PhantomReference";
    default:
      ShouldNotReachHere();
      return NULL;
  }
}

RefProcPhaseTimesTracker::RefProcPhaseTimesTracker(ReferenceType ref_type,
                                                   ReferenceProcessorPhaseTimes* phase_times,
                                                   ReferenceProcessor* rp) :
  _rp(rp), RefProcPhaseTimeBaseTracker(ref_type_2_string(ref_type), phase_times) {
  phase_times->set_processing_ref_type(ref_type);

  size_t discovered = rp->total_reference_count(ref_type);
  phase_times->set_ref_discovered(ref_type, discovered);
}

RefProcPhaseTimesTracker::~RefProcPhaseTimesTracker() {
  double elapsed = elapsed_time();
  ReferenceProcessorPhaseTimes* times = phase_times();
  ReferenceType ref_type = times->processing_ref_type();
  times->set_ref_proc_time_ms(ref_type, elapsed);

  size_t after_count = _rp->total_reference_count(ref_type);
  size_t discovered = times->ref_discovered(ref_type);
  times->set_ref_cleared(ref_type, discovered - after_count);
}

ReferenceProcessorPhaseTimes::ReferenceProcessorPhaseTimes(GCTimer* gc_timer, uint max_gc_threads) :
  _gc_timer(gc_timer), _processing_is_mt(false) {

  for (int i = 0; i < RefParPhaseMax; i++) {
    _worker_time_sec[i] = new WorkerDataArray<double>(max_gc_threads, "Process lists (ms)");
    _par_phase_time_ms[i] = uninitialized();
  }

  for (int i = 0; i < number_of_subclasses_of_ref; i++) {
    _ref_proc_time_ms[i] = uninitialized();
    _balance_queues_time_ms[i] = uninitialized();
    _ref_cleared[i] = 0;
    _ref_discovered[i] = 0;
    _ref_enqueued[i] = 0;
  }
}

inline int ref_type_2_index(ReferenceType ref_type) {
  return ref_type - REF_SOFT;
}

#define ASSERT_PAR_PHASE(phase) assert(phase >= ReferenceProcessorPhaseTimes::SoftRefPhase1 && \
                                       phase < ReferenceProcessorPhaseTimes::RefParPhaseMax, \
                                       "Invariant (%d)", (int)phase);

WorkerDataArray<double>* ReferenceProcessorPhaseTimes::worker_time_sec(RefProcParPhases par_phase) const {
  ASSERT_PAR_PHASE(par_phase);
  return _worker_time_sec[par_phase];
}

double ReferenceProcessorPhaseTimes::par_phase_time_ms(RefProcParPhases par_phase) const {
  ASSERT_PAR_PHASE(par_phase);
  return _par_phase_time_ms[par_phase];
}

void ReferenceProcessorPhaseTimes::set_par_phase_time_ms(RefProcParPhases par_phase,
                                                         double par_phase_time_ms) {
  ASSERT_PAR_PHASE(par_phase);
  _par_phase_time_ms[par_phase] = par_phase_time_ms;
}

void ReferenceProcessorPhaseTimes::reset() {
  for (int i = 0; i < RefParPhaseMax; i++) {
    _worker_time_sec[i]->reset();
    _par_phase_time_ms[i] = uninitialized();
  }

  for (int i = 0; i < number_of_subclasses_of_ref; i++) {
    _ref_proc_time_ms[i] = uninitialized();
    _balance_queues_time_ms[i] = uninitialized();
    _ref_cleared[i] = 0;
    _ref_discovered[i] = 0;
    _ref_enqueued[i] = 0;
  }

  _total_time_ms = uninitialized();

  _processing_is_mt = false;
}

ReferenceProcessorPhaseTimes::~ReferenceProcessorPhaseTimes() {
  for (int i = 0; i < RefParPhaseMax; i++) {
    delete _worker_time_sec[i];
  }
}

double ReferenceProcessorPhaseTimes::ref_proc_time_ms(ReferenceType ref_type) const {
  ASSERT_REF_TYPE(ref_type);
  return _ref_proc_time_ms[ref_type_2_index(ref_type)];
}

void ReferenceProcessorPhaseTimes::set_ref_proc_time_ms(ReferenceType ref_type,
                                                        double ref_proc_time_ms) {
  ASSERT_REF_TYPE(ref_type);
  _ref_proc_time_ms[ref_type_2_index(ref_type)] = ref_proc_time_ms;
}

size_t ReferenceProcessorPhaseTimes::ref_cleared(ReferenceType ref_type) const {
  ASSERT_REF_TYPE(ref_type);
  return _ref_cleared[ref_type_2_index(ref_type)];
}

void ReferenceProcessorPhaseTimes::set_ref_cleared(ReferenceType ref_type, size_t count) {
  ASSERT_REF_TYPE(ref_type);
  _ref_cleared[ref_type_2_index(ref_type)] = count;
}

size_t ReferenceProcessorPhaseTimes::ref_discovered(ReferenceType ref_type) const {
  ASSERT_REF_TYPE(ref_type);
  return _ref_discovered[ref_type_2_index(ref_type)];
}

void ReferenceProcessorPhaseTimes::set_ref_discovered(ReferenceType ref_type, size_t count) {
  ASSERT_REF_TYPE(ref_type);
  _ref_discovered[ref_type_2_index(ref_type)] = count;
}

size_t ReferenceProcessorPhaseTimes::ref_enqueued(ReferenceType ref_type) const {
  ASSERT_REF_TYPE(ref_type);
  return _ref_enqueued[ref_type_2_index(ref_type)];
}

void ReferenceProcessorPhaseTimes::set_ref_enqueued(ReferenceType ref_type, size_t count) {
  ASSERT_REF_TYPE(ref_type);
  _ref_enqueued[ref_type_2_index(ref_type)] = count;
}

double ReferenceProcessorPhaseTimes::balance_queues_time_ms(ReferenceType ref_type) const {
  ASSERT_REF_TYPE(ref_type);
  return _balance_queues_time_ms[ref_type_2_index(ref_type)];
}

void ReferenceProcessorPhaseTimes::set_balance_queues_time_ms(ReferenceType ref_type, double time_ms) {
  ASSERT_REF_TYPE(ref_type);
  _balance_queues_time_ms[ref_type_2_index(ref_type)] = time_ms;
}

ReferenceProcessorPhaseTimes::RefProcParPhases
ReferenceProcessorPhaseTimes::par_phase(RefProcPhaseNumbers phase_number) const {
  ASSERT_PHASE_NUMBER(phase_number);
  ASSERT_REF_TYPE(_processing_ref_type);

  int result = SoftRefPhase1;

  switch(_processing_ref_type) {
    case REF_SOFT:
      result = (int)SoftRefPhase1;
      result += phase_number;

      assert((RefProcParPhases)result >= SoftRefPhase1 &&
             (RefProcParPhases)result <= SoftRefPhase3,
             "Invariant (%d)", result);
      break;
    case REF_WEAK:
      result = (int)WeakRefPhase2;
      result += (phase_number - 1);
      assert((RefProcParPhases)result >= WeakRefPhase2 &&
             (RefProcParPhases)result <= WeakRefPhase3,
             "Invariant (%d)", result);
      break;
    case REF_FINAL:
      result = (int)FinalRefPhase2;
      result += (phase_number - 1);
      assert((RefProcParPhases)result >= FinalRefPhase2 &&
             (RefProcParPhases)result <= FinalRefPhase3,
             "Invariant (%d)", result);
      break;
    case REF_PHANTOM:
      result = (int)PhantomRefPhase2;
      result += (phase_number - 1);
      assert((RefProcParPhases)result >= PhantomRefPhase2 &&
             (RefProcParPhases)result <= PhantomRefPhase3,
             "Invariant (%d)", result);
      break;
    default:
      ShouldNotReachHere();
  }

  ASSERT_PAR_PHASE(result);

  return (RefProcParPhases)result;
}

#define TIME_FORMAT "%.1lfms"

void ReferenceProcessorPhaseTimes::print_all_references(uint base_indent, bool print_total) const {
  if (print_total) {
    LogTarget(Debug, gc, phases, ref) lt;

    if (lt.is_enabled()) {
      LogStream ls(lt);
      ls.print_cr("%s%s: " TIME_FORMAT,
                  Indents[base_indent], "Reference Processing", total_time_ms());
    }
  }

  uint next_indent = base_indent + 1;
  print_reference(REF_SOFT, next_indent);
  print_reference(REF_WEAK, next_indent);
  print_reference(REF_FINAL, next_indent);
  print_reference(REF_PHANTOM, next_indent);
}

void ReferenceProcessorPhaseTimes::print_reference(ReferenceType ref_type, uint base_indent) const {
  LogTarget(Debug, gc, phases, ref) lt;

  if (lt.is_enabled()) {
    LogStream ls(lt);
    uint next_indent = base_indent + 1;
    ResourceMark rm;

    ls.print_cr("%s%s: " TIME_FORMAT,
                Indents[base_indent], ref_type_2_string(ref_type), ref_proc_time_ms(ref_type));

    double balance_time = balance_queues_time_ms(ref_type);
    if (balance_time != uninitialized()) {
      ls.print_cr("%s%s " TIME_FORMAT, Indents[next_indent], "Balance queues:", balance_time);
    }

    switch(ref_type) {
      case REF_SOFT:
        print_phase(SoftRefPhase1, next_indent);
        print_phase(SoftRefPhase2, next_indent);
        print_phase(SoftRefPhase3, next_indent);
        break;

      case REF_WEAK:
        print_phase(WeakRefPhase2, next_indent);
        print_phase(WeakRefPhase3, next_indent);
        break;

      case REF_FINAL:
        print_phase(FinalRefPhase2, next_indent);
        print_phase(FinalRefPhase3, next_indent);
        break;

      case REF_PHANTOM:
        print_phase(PhantomRefPhase2, next_indent);
        print_phase(PhantomRefPhase3, next_indent);
        break;

      default:
        ShouldNotReachHere();
    }

    ls.print_cr("%s%s " SIZE_FORMAT, Indents[next_indent], "Discovered:", ref_discovered(ref_type));
    ls.print_cr("%s%s " SIZE_FORMAT, Indents[next_indent], "Cleared:", ref_cleared(ref_type));
  }
}

void ReferenceProcessorPhaseTimes::print_phase(RefProcParPhases phase, uint indent) const {
  double phase_time = par_phase_time_ms(phase);
  if (phase_time != uninitialized()) {
    LogTarget(Debug, gc, phases, ref) lt;

    LogStream ls(lt);

    ls.print_cr("%s%s%s " TIME_FORMAT,
                Indents[indent],
                phase_enum_2_phase_string(phase),
                indent == 0 ? "" : ":", /* 0 indent logs don't need colon. */
                phase_time);

    LogTarget(Trace, gc, phases, ref) lt2;
    if (_processing_is_mt && lt2.is_enabled()) {
      LogStream ls(lt2);

      ls.print("%s", Indents[indent + 1]);
      // worker_time_sec is recorded in seconds but it will be printed in milliseconds.
      worker_time_sec(phase)->print_summary_on(&ls, true);
    }
  }
}

#undef ASSERT_REF_TYPE
#undef ASSERT_PHASE_NUMBER
#undef ASSERT_PAR_PHASE
#undef TIME_FORMAT
