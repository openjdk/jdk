/*
 * Copyright (c) 2017, 2020, Red Hat, Inc. All rights reserved.
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

ShenandoahPhaseTimings::ShenandoahPhaseTimings() {
  uint max_workers = MAX2(ConcGCThreads, ParallelGCThreads);
  assert(max_workers > 0, "Must have some GC threads");

#define GC_PAR_PHASE_DECLARE_WORKER_DATA(type, title) \
  _gc_par_phases[ShenandoahPhaseTimings::type] = new WorkerDataArray<double>(title, max_workers);
  // Root scanning phases
  SHENANDOAH_GC_PAR_PHASE_DO(,, GC_PAR_PHASE_DECLARE_WORKER_DATA)
#undef GC_PAR_PHASE_DECLARE_WORKER_DATA

  _policy = ShenandoahHeap::heap()->shenandoah_policy();
  assert(_policy != NULL, "Can not be NULL");
}

void ShenandoahPhaseTimings::record_phase_time(Phase phase, double time) {
  if (!_policy->is_at_shutdown()) {
    _timing_data[phase].add(time);
  }
}

void ShenandoahPhaseTimings::record_workers_start(Phase phase) {
  for (uint i = 0; i < GCParPhasesSentinel; i++) {
    _gc_par_phases[i]->reset();
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
            phase == full_gc_purge_par ||
            phase == purge_par ||
            phase == _num_phases,
            "only in these phases we can add per-thread phase times");
  if (phase != _num_phases) {
    double s = 0;
    for (uint i = 1; i < GCParPhasesSentinel; i++) {
      double t = _gc_par_phases[i]->sum();
      _timing_data[phase + i + 1].add(t); // add to each line in phase
      s += t;
    }
    _timing_data[phase + 1].add(s); // add to total for phase
  }
}

void ShenandoahPhaseTimings::print_on(outputStream* out) const {
  out->cr();
  out->print_cr("GC STATISTICS:");
  out->print_cr("  \"(G)\" (gross) pauses include VM time: time to notify and block threads, do the pre-");
  out->print_cr("        and post-safepoint housekeeping. Use -Xlog:safepoint+stats to dissect.");
  out->print_cr("  \"(N)\" (net) pauses are the times spent in the actual GC code.");
  out->print_cr("  \"a\" is average time for each phase, look at levels to see if average makes sense.");
  out->print_cr("  \"lvls\" are quantiles: 0%% (minimum), 25%%, 50%% (median), 75%%, 100%% (maximum).");
  out->cr();
  out->print_cr("  All times are wall-clock times, except per-root-class counters, that are sum over");
  out->print_cr("  all workers. Dividing the <total> over the root stage time estimates parallelism.");
  out->cr();

  for (uint i = 0; i < _num_phases; i++) {
    if (_timing_data[i].maximum() != 0) {
      out->print_cr("%-27s = %8.2lf s (a = %8.0lf us) (n = " INT32_FORMAT_W(5) ") (lvls, us = %8.0lf, %8.0lf, %8.0lf, %8.0lf, %8.0lf)",
                    _phase_names[i],
                    _timing_data[i].sum(),
                    _timing_data[i].avg() * 1000000.0,
                    _timing_data[i].num(),
                    _timing_data[i].percentile(0) * 1000000.0,
                    _timing_data[i].percentile(25) * 1000000.0,
                    _timing_data[i].percentile(50) * 1000000.0,
                    _timing_data[i].percentile(75) * 1000000.0,
                    _timing_data[i].maximum() * 1000000.0
      );
    }
  }
}

void ShenandoahPhaseTimings::record_worker_time(ShenandoahPhaseTimings::GCParPhases phase, uint worker_id, double secs) {
  _gc_par_phases[phase]->set(worker_id, secs);
}

ShenandoahWorkerTimingsTracker::ShenandoahWorkerTimingsTracker(ShenandoahPhaseTimings::GCParPhases phase, uint worker_id) :
        _phase(phase), _timings(ShenandoahHeap::heap()->phase_timings()), _worker_id(worker_id) {
  _start_time = os::elapsedTime();
}

ShenandoahWorkerTimingsTracker::~ShenandoahWorkerTimingsTracker() {
  _timings->record_worker_time(_phase, _worker_id, os::elapsedTime() - _start_time);

  if (ShenandoahGCPhase::is_root_work_phase()) {
    ShenandoahPhaseTimings::Phase root_phase = ShenandoahGCPhase::current_phase();
    ShenandoahPhaseTimings::Phase cur_phase = (ShenandoahPhaseTimings::Phase)((int)root_phase + (int)_phase + 1);
    _event.commit(GCId::current(), _worker_id, ShenandoahPhaseTimings::phase_name(cur_phase));
  }
}

