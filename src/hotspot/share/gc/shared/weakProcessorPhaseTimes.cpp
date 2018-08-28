/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/weakProcessorPhases.hpp"
#include "gc/shared/weakProcessorPhaseTimes.hpp"
#include "gc/shared/workerDataArray.inline.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

static uint phase_index(WeakProcessorPhase phase) {
  return WeakProcessorPhases::index(phase);
}

static bool is_serial_phase(WeakProcessorPhase phase) {
  return WeakProcessorPhases::is_serial(phase);
}

static void assert_oop_storage_phase(WeakProcessorPhase phase) {
  assert(WeakProcessorPhases::is_oop_storage(phase),
         "Not an oop_storage phase %u", phase_index(phase));
}

const double uninitialized_time = -1.0;

#ifdef ASSERT
static bool is_initialized_time(double t) { return t >= 0.0; }
#endif // ASSERT

static void reset_times(double* times, size_t ntimes) {
  for (size_t i = 0; i < ntimes; ++i) {
    times[i] = uninitialized_time;
  }
}

WeakProcessorPhaseTimes::WeakProcessorPhaseTimes(uint max_threads) :
  _max_threads(max_threads),
  _active_workers(0),
  _total_time_sec(uninitialized_time),
  _worker_phase_times_sec()
{
  assert(_max_threads > 0, "max_threads must not be zero");

  reset_times(_phase_times_sec, ARRAY_SIZE(_phase_times_sec));

  if (_max_threads > 1) {
    WorkerDataArray<double>** wpt = _worker_phase_times_sec;
    FOR_EACH_WEAK_PROCESSOR_OOP_STORAGE_PHASE(phase) {
      const char* description = WeakProcessorPhases::description(phase);
      *wpt++ = new WorkerDataArray<double>(_max_threads, description);
    }
  }
}

WeakProcessorPhaseTimes::~WeakProcessorPhaseTimes() {
  for (size_t i = 0; i < ARRAY_SIZE(_worker_phase_times_sec); ++i) {
    delete _worker_phase_times_sec[i];
  }
}

uint WeakProcessorPhaseTimes::max_threads() const { return _max_threads; }

uint WeakProcessorPhaseTimes::active_workers() const {
  assert(_active_workers != 0, "active workers not set");
  return _active_workers;
}

void WeakProcessorPhaseTimes::set_active_workers(uint n) {
  assert(_active_workers == 0, "active workers already set");
  assert(n > 0, "active workers must be non-zero");
  assert(n <= _max_threads, "active workers must not exceed max threads");
  _active_workers = n;
}

void WeakProcessorPhaseTimes::reset() {
  _active_workers = 0;
  _total_time_sec = uninitialized_time;
  reset_times(_phase_times_sec, ARRAY_SIZE(_phase_times_sec));
  if (_max_threads > 1) {
    for (size_t i = 0; i < ARRAY_SIZE(_worker_phase_times_sec); ++i) {
      _worker_phase_times_sec[i]->reset();
    }
  }
}

double WeakProcessorPhaseTimes::total_time_sec() const {
  assert(is_initialized_time(_total_time_sec), "Total time not set");
  return _total_time_sec;
}

void WeakProcessorPhaseTimes::record_total_time_sec(double time_sec) {
  assert(!is_initialized_time(_total_time_sec), "Already set total time");
  _total_time_sec = time_sec;
}

double WeakProcessorPhaseTimes::phase_time_sec(WeakProcessorPhase phase) const {
  assert(is_initialized_time(_phase_times_sec[phase_index(phase)]),
         "phase time not set %u", phase_index(phase));
  return _phase_times_sec[phase_index(phase)];
}

void WeakProcessorPhaseTimes::record_phase_time_sec(WeakProcessorPhase phase, double time_sec) {
  assert(!is_initialized_time(_phase_times_sec[phase_index(phase)]),
         "Already set time for phase %u", phase_index(phase));
  _phase_times_sec[phase_index(phase)] = time_sec;
}

WorkerDataArray<double>* WeakProcessorPhaseTimes::worker_data(WeakProcessorPhase phase) const {
  assert_oop_storage_phase(phase);
  assert(active_workers() > 1, "No worker data when single-threaded");
  return _worker_phase_times_sec[WeakProcessorPhases::oop_storage_index(phase)];
}

double WeakProcessorPhaseTimes::worker_time_sec(uint worker_id, WeakProcessorPhase phase) const {
  assert(worker_id < active_workers(),
         "invalid worker id %u for %u", worker_id, active_workers());
  if (active_workers() == 1) {
    return phase_time_sec(phase);
  } else {
    return worker_data(phase)->get(worker_id);
  }
}

void WeakProcessorPhaseTimes::record_worker_time_sec(uint worker_id,
                                                     WeakProcessorPhase phase,
                                                     double time_sec) {
  if (active_workers() == 1) {
    record_phase_time_sec(phase, time_sec);
  } else {
    worker_data(phase)->set(worker_id, time_sec);
  }
}

static double elapsed_time_sec(Ticks start_time, Ticks end_time) {
  return (end_time - start_time).seconds();
}

WeakProcessorTimeTracker::WeakProcessorTimeTracker(WeakProcessorPhaseTimes* times) :
  _times(times),
  _start_time(Ticks::now())
{}

WeakProcessorTimeTracker::~WeakProcessorTimeTracker() {
  if (_times != NULL) {
    Ticks end_time = Ticks::now();
    _times->record_total_time_sec(elapsed_time_sec(_start_time, end_time));
  }
}

WeakProcessorPhaseTimeTracker::WeakProcessorPhaseTimeTracker(WeakProcessorPhaseTimes* times,
                                                             WeakProcessorPhase phase,
                                                             uint worker_id) :
  _times(times),
  _phase(phase),
  _worker_id(worker_id),
  _start_time(Ticks::now())
{
  assert_oop_storage_phase(_phase);
  assert(_times == NULL || worker_id < _times->active_workers(),
         "Invalid worker_id %u", worker_id);
}

WeakProcessorPhaseTimeTracker::WeakProcessorPhaseTimeTracker(WeakProcessorPhaseTimes* times,
                                                             WeakProcessorPhase phase) :
  _times(times),
  _phase(phase),
  _worker_id(0),
  _start_time(Ticks::now())
{
  assert(is_serial_phase(phase), "Not a serial phase %u", phase_index(phase));
}

WeakProcessorPhaseTimeTracker::~WeakProcessorPhaseTimeTracker() {
  if (_times != NULL) {
    double time_sec = elapsed_time_sec(_start_time, Ticks::now());
    if (is_serial_phase(_phase)) {
      _times->record_phase_time_sec(_phase, time_sec);
    } else {
      _times->record_worker_time_sec(_worker_id, _phase, time_sec);
    }
  }
}

//////////////////////////////////////////////////////////////////////////////
// Printing times

const char* const indents[] = {"", "  ", "    ", "      ", "        "};
const size_t max_indents_index = ARRAY_SIZE(indents) - 1;

static const char* indent_str(size_t i) {
  return indents[MIN2(i, max_indents_index)];
}

#define TIME_FORMAT "%.1lfms"

void WeakProcessorPhaseTimes::log_st_phase(WeakProcessorPhase phase,
                                           uint indent) const {
  log_debug(gc, phases)("%s%s: " TIME_FORMAT,
                        indent_str(indent),
                        WeakProcessorPhases::description(phase),
                        phase_time_sec(phase) * MILLIUNITS);
}

void WeakProcessorPhaseTimes::log_mt_phase_summary(WeakProcessorPhase phase,
                                                   uint indent) const {
  LogTarget(Debug, gc, phases) lt;
  LogStream ls(lt);
  ls.print("%s", indents[indent]);
  worker_data(phase)->print_summary_on(&ls, true);
}

void WeakProcessorPhaseTimes::log_mt_phase_details(WeakProcessorPhase phase,
                                                   uint indent) const {
  LogTarget(Trace, gc, phases) lt;
  LogStream ls(lt);
  ls.print("%s", indents[indent]);
  worker_data(phase)->print_details_on(&ls);
}

void WeakProcessorPhaseTimes::log_print_phases(uint indent) const {
  if (log_is_enabled(Debug, gc, phases)) {
    bool details_enabled = log_is_enabled(Trace, gc, phases);
    FOR_EACH_WEAK_PROCESSOR_PHASE(phase) {
      if (is_serial_phase(phase) || (active_workers() == 1)) {
        log_st_phase(phase, indent);
      } else {
        log_mt_phase_summary(phase, indent);
        if (details_enabled) {
          log_mt_phase_details(phase, indent + 1);
        }
      }
    }
  }
}

void WeakProcessorPhaseTimes::log_print(uint indent) const {
  if (log_is_enabled(Debug, gc, phases)) {
    log_debug(gc, phases)("%s%s: " TIME_FORMAT,
                          indent_str(indent),
                          "Weak Processing",
                          total_time_sec() * MILLIUNITS);
    log_print_phases(indent + 1);
  }
}
