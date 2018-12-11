/*
 * Copyright (c) 2017, 2018, Red Hat, Inc. All rights reserved.
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

#include "gc/shared/workerDataArray.inline.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "utilities/ostream.hpp"

#define GC_PHASE_DECLARE_NAME(type, title) \
  title,

const char* ShenandoahPhaseTimings::_phase_names[] = {
  SHENANDOAH_GC_PHASE_DO(GC_PHASE_DECLARE_NAME)
};

#undef GC_PHASE_DECLARE_NAME

ShenandoahPhaseTimings::ShenandoahPhaseTimings() : _policy(NULL) {
  uint max_workers = MAX2(ConcGCThreads, ParallelGCThreads);
  _worker_times = new ShenandoahWorkerTimings(max_workers);
  _termination_times = new ShenandoahTerminationTimings(max_workers);
  _policy = ShenandoahHeap::heap()->shenandoah_policy();
  assert(_policy != NULL, "Can not be NULL");
}

void ShenandoahPhaseTimings::record_phase_start(Phase phase) {
  _timing_data[phase]._start = os::elapsedTime();
}

void ShenandoahPhaseTimings::record_phase_end(Phase phase) {
  assert(_policy != NULL, "Not yet initialized");
  double end = os::elapsedTime();
  double elapsed = end - _timing_data[phase]._start;
  if (!_policy->is_at_shutdown()) {
    _timing_data[phase]._secs.add(elapsed);
  }
  ShenandoahHeap::heap()->heuristics()->record_phase_time(phase, elapsed);
}

void ShenandoahPhaseTimings::record_phase_time(Phase phase, double time) {
  assert(_policy != NULL, "Not yet initialized");
  if (!_policy->is_at_shutdown()) {
    _timing_data[phase]._secs.add(time);
  }
}

void ShenandoahPhaseTimings::record_workers_start(Phase phase) {
  for (uint i = 0; i < GCParPhasesSentinel; i++) {
    _worker_times->reset(i);
  }
}

void ShenandoahPhaseTimings::record_workers_end(Phase phase) {
  if (_policy->is_at_shutdown()) {
    // Do not record the past-shutdown events
    return;
  }

  guarantee(phase == init_evac ||
            phase == scan_roots ||
            phase == update_roots ||
            phase == init_traversal_gc_work ||
            phase == final_traversal_gc_work ||
            phase == final_traversal_update_roots ||
            phase == final_update_refs_roots ||
            phase == full_gc_roots ||
            phase == degen_gc_update_roots ||
            phase == _num_phases,
            "only in these phases we can add per-thread phase times");
  if (phase != _num_phases) {
    // Merge _phase_time to counters below the given phase.
    for (uint i = 0; i < GCParPhasesSentinel; i++) {
      double t = _worker_times->average(i);
      _timing_data[phase + i + 1]._secs.add(t);
    }
  }
}

void ShenandoahPhaseTimings::print_on(outputStream* out) const {
  out->cr();
  out->print_cr("GC STATISTICS:");
  out->print_cr("  \"(G)\" (gross) pauses include VM time: time to notify and block threads, do the pre-");
  out->print_cr("        and post-safepoint housekeeping. Use -XX:+PrintSafepointStatistics to dissect.");
  out->print_cr("  \"(N)\" (net) pauses are the times spent in the actual GC code.");
  out->print_cr("  \"a\" is average time for each phase, look at levels to see if average makes sense.");
  out->print_cr("  \"lvls\" are quantiles: 0%% (minimum), 25%%, 50%% (median), 75%%, 100%% (maximum).");
  out->cr();

  for (uint i = 0; i < _num_phases; i++) {
    if (_timing_data[i]._secs.maximum() != 0) {
      print_summary_sd(out, _phase_names[i], &(_timing_data[i]._secs));
    }
  }
}

void ShenandoahPhaseTimings::print_summary_sd(outputStream* out, const char* str, const HdrSeq* seq) const {
  out->print_cr("%-27s = %8.2lf s (a = %8.0lf us) (n = " INT32_FORMAT_W(5) ") (lvls, us = %8.0lf, %8.0lf, %8.0lf, %8.0lf, %8.0lf)",
          str,
          seq->sum(),
          seq->avg() * 1000000.0,
          seq->num(),
          seq->percentile(0)  * 1000000.0,
          seq->percentile(25) * 1000000.0,
          seq->percentile(50) * 1000000.0,
          seq->percentile(75) * 1000000.0,
          seq->maximum() * 1000000.0
  );
}

ShenandoahWorkerTimings::ShenandoahWorkerTimings(uint max_gc_threads) :
        _max_gc_threads(max_gc_threads)
{
  assert(max_gc_threads > 0, "Must have some GC threads");

#define GC_PAR_PHASE_DECLARE_WORKER_DATA(type, title) \
  _gc_par_phases[ShenandoahPhaseTimings::type] = new WorkerDataArray<double>(max_gc_threads, title);
  // Root scanning phases
  SHENANDOAH_GC_PAR_PHASE_DO(GC_PAR_PHASE_DECLARE_WORKER_DATA)
#undef GC_PAR_PHASE_DECLARE_WORKER_DATA
}

// record the time a phase took in seconds
void ShenandoahWorkerTimings::record_time_secs(ShenandoahPhaseTimings::GCParPhases phase, uint worker_i, double secs) {
  _gc_par_phases[phase]->set(worker_i, secs);
}

double ShenandoahWorkerTimings::average(uint i) const {
  return _gc_par_phases[i]->average();
}

void ShenandoahWorkerTimings::reset(uint i) {
  _gc_par_phases[i]->reset();
}

void ShenandoahWorkerTimings::print() const {
  for (uint i = 0; i < ShenandoahPhaseTimings::GCParPhasesSentinel; i++) {
    _gc_par_phases[i]->print_summary_on(tty);
  }
}


ShenandoahTerminationTimings::ShenandoahTerminationTimings(uint max_gc_threads) {
  _gc_termination_phase = new WorkerDataArray<double>(max_gc_threads, "Task Termination (ms):");
}

void ShenandoahTerminationTimings::record_time_secs(uint worker_id, double secs) {
  if (_gc_termination_phase->get(worker_id) == WorkerDataArray<double>::uninitialized()) {
    _gc_termination_phase->set(worker_id, secs);
  } else {
    // worker may re-enter termination phase
    _gc_termination_phase->add(worker_id, secs);
  }
}

void ShenandoahTerminationTimings::print() const {
  _gc_termination_phase->print_summary_on(tty);
}

double ShenandoahTerminationTimings::average() const {
  return _gc_termination_phase->average();
}

void ShenandoahTerminationTimings::reset() {
  _gc_termination_phase->reset();
}

