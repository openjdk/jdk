/*
 * Copyright (c) 2017, 2021, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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


#include "gc/shared/workerDataArray.inline.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/ostream.hpp"

#define SHENANDOAH_PHASE_NAME_FORMAT "%-30s"
#define SHENANDOAH_S_TIME_FORMAT "%8.3lf"
#define SHENANDOAH_US_TIME_FORMAT "%8.0lf"
#define SHENANDOAH_US_WORKER_TIME_FORMAT "%3.0lf"
#define SHENANDOAH_US_WORKER_NOTIME_FORMAT "%3s"
#define SHENANDOAH_PARALLELISM_FORMAT "%4.2lf"

#define SHENANDOAH_PHASE_DECLARE_DESC(name, desc, has_worker_phase) desc,
#define SHENANDOAH_PHASE_DECLARE_HAS_WORKER_PHASE(name, desc, has_worker_phase) has_worker_phase,

const char* ShenandoahPhaseTimings::_desc[] = {
  SHENANDOAH_PHASE_DO(SHENANDOAH_PHASE_DECLARE_DESC)
};

bool ShenandoahPhaseTimings::_has_worker_phase[] = {
  SHENANDOAH_PHASE_DO(SHENANDOAH_PHASE_DECLARE_HAS_WORKER_PHASE)
};

#undef SHENANDOAH_PHASE_DECLARE_DESC
#undef SHENANDOAH_PHASE_DECLARE_HAS_WORKER_PHASE

ShenandoahPhaseTimings::ShenandoahPhaseTimings(uint max_workers) :
  _max_workers(max_workers) {
  assert(_max_workers > 0, "Must have some GC threads");

  // Initialize everything to sane defaults
  for (uint i = 0; i < _num_phases; i++) {
    _worker_data[i] = nullptr;
    _cycle_data[i] = uninitialized();
  }

  // Then punch in the worker-related data.
  for (uint i = 0; i < _num_phases; i++) {
    if (has_worker_phases(Phase(i))) {
      int c = i + 1;
#define SHENANDOAH_WORKER_DATA_INIT(name, desc, has_worker_phase) \
      _worker_data[c++] = new ShenandoahWorkerData(nullptr, desc, _max_workers);
      SHENANDOAH_WORKER_PHASE_DO(,, SHENANDOAH_WORKER_DATA_INIT)
#undef SHENANDOAH_WORKER_DATA_INIT
    }
  }

  _policy = ShenandoahHeap::heap()->shenandoah_policy();
  assert(_policy != nullptr, "Can not be null");
}

ShenandoahPhaseTimings::Phase ShenandoahPhaseTimings::compute_phase_slot(Phase phase, WorkerPhase worker_phase) {
  assert(has_worker_phases(phase), "Phase should accept worker phase times: %s", phase_desc(phase));
  Phase p = Phase(phase + 1 + worker_phase);
  assert(p >= 0 && p < _num_phases, "Out of bound for: %s", phase_desc(phase));
  return p;
}

ShenandoahWorkerData* ShenandoahPhaseTimings::worker_data(Phase phase, WorkerPhase worker_phase) {
  Phase p = compute_phase_slot(phase, worker_phase);
  ShenandoahWorkerData* wd = _worker_data[p];
  assert(wd != nullptr, "Counter initialized: %s", phase_desc(p));
  return wd;
}

bool ShenandoahPhaseTimings::is_root_work_phase(Phase phase) {
  switch (phase) {
    case finish_mark:
    case degen_gc_update_roots:
    case full_gc_mark:
    case full_gc_update_roots:
    case full_gc_adjust_roots:
      return true;
    default:
      return false;
  }
}

void ShenandoahPhaseTimings::set_cycle_data(Phase phase, double time, bool should_aggregate) {
  const double cycle_data = _cycle_data[phase];
  if (should_aggregate) {
    _cycle_data[phase] = (cycle_data == uninitialized()) ? time :  (cycle_data + time);
  } else {
    assert(cycle_data == uninitialized(), "Should not be set yet: %s, current value: %lf", phase_desc(phase), cycle_data);
    _cycle_data[phase] = time;
  }
}

void ShenandoahPhaseTimings::record_phase_time(Phase phase, double time, bool should_aggregate) {
  if (!_policy->is_at_shutdown()) {
    set_cycle_data(phase, time, should_aggregate);
  }
}

void ShenandoahPhaseTimings::record_workers_start(Phase phase) {
  assert(has_worker_phases(phase), "Phase should accept worker phase times: %s", phase_desc(phase));

  // Special case: these phases can enter multiple times, need to reset
  // their worker data every time.
  if (phase == heap_iteration_roots) {
    for (uint i = 0; i < _num_par_phases; i++) {
      worker_data(phase, WorkerPhase(i))->reset();
    }
  }

#ifdef ASSERT
  for (uint i = 0; i < _num_par_phases; i++) {
    ShenandoahWorkerData* wd = worker_data(phase, WorkerPhase(i));
    for (uint c = 0; c < _max_workers; c++) {
      assert(wd->get(c) == ShenandoahWorkerData::uninitialized(),
             "Should not be set: %s", phase_desc(compute_phase_slot(phase, WorkerPhase(i))));
    }
  }
#endif
}

void ShenandoahPhaseTimings::record_workers_end(Phase phase) {
  assert(has_worker_phases(phase), "Phase should accept worker phase times: %s", phase_desc(phase));
}

void ShenandoahPhaseTimings::flush_par_workers_to_cycle() {
  for (uint pi = 0; pi < _num_phases; pi++) {
    Phase phase = Phase(pi);
    if (has_worker_phases(phase)) {
      for (uint i = 0; i < _num_par_phases; i++) {
        ShenandoahWorkerData* wd = worker_data(phase, WorkerPhase(i));
        double worker_sum = uninitialized();
        for (uint c = 0; c < _max_workers; c++) {
          double worker_time = wd->get(c);
          if (worker_time != ShenandoahWorkerData::uninitialized()) {
            if (worker_sum == uninitialized()) {
              worker_sum = worker_time;
            } else {
              worker_sum += worker_time;
            }
          }
        }
        if (worker_sum != uninitialized()) {
          // add to each line in phase
          set_cycle_data(Phase(phase + i + 1), worker_sum);
        }
      }
    }
  }
}

void ShenandoahPhaseTimings::flush_cycle_to_global() {
  for (uint i = 0; i < _num_phases; i++) {
    if (_cycle_data[i] != uninitialized()) {
      _global_data[i].add(_cycle_data[i]);
      _cycle_data[i] = uninitialized();
    }
    if (_worker_data[i] != nullptr) {
      _worker_data[i]->reset();
    }
  }
  OrderAccess::fence();
}

void ShenandoahPhaseTimings::print_cycle_on(outputStream* out) const {
  out->cr();
  out->print_cr("  All times are wall-clock times, except for ones explicitly marked as \"total\", those are");
  out->print_cr("  sum over all workers. Dividing the total over the root stage time estimates parallelism.");
  out->cr();
  for (uint i = 0; i < _num_phases; i++) {
    double v = _cycle_data[i] * 1000000.0;
    if (v > 0) {
      out->print(SHENANDOAH_PHASE_NAME_FORMAT " " SHENANDOAH_US_TIME_FORMAT " us", _desc[i], v);

      if (has_worker_phases(Phase(i))) {
        double total = 0;
        for (uint pi = 0; pi < _num_par_phases; pi++) {
          uint idx = i + 1 + pi;
          if (_cycle_data[idx] != uninitialized()) {
            total += _cycle_data[idx];
          }
        }
        if (total > 0) {
          out->print(" with " SHENANDOAH_PARALLELISM_FORMAT "x parallelism", total * 1000000.0 / v);
        }
      }

      if (_worker_data[i] != nullptr) {
        out->print(" total, per worker: ");
        for (uint c = 0; c < _max_workers; c++) {
          double tv = _worker_data[i]->get(c);
          if (tv != ShenandoahWorkerData::uninitialized()) {
            out->print(SHENANDOAH_US_WORKER_TIME_FORMAT ", ", tv * 1000000.0);
          } else {
            out->print(SHENANDOAH_US_WORKER_NOTIME_FORMAT ", ", "---");
          }
        }
      }
      out->cr();
    }
  }
}

void ShenandoahPhaseTimings::print_global_on(outputStream* out) const {
  out->cr();
  out->print_cr("GC STATISTICS:");
  out->print_cr("  \"(G)\" (gross) pauses include VM time: time to notify and block threads, do the pre-");
  out->print_cr("        and post-safepoint housekeeping. Use -Xlog:safepoint+stats to dissect.");
  out->print_cr("  \"(N)\" (net) pauses are the times spent in the actual GC code.");
  out->print_cr("  \"a\" is average time for each phase, look at levels to see if average makes sense.");
  out->print_cr("  \"lvls\" are quantiles: 0%% (minimum), 25%%, 50%% (median), 75%%, 100%% (maximum).");
  out->cr();
  out->print_cr("  All times are wall-clock times, except for ones explicitly marked as \"total\", those are");
  out->print_cr("  sum over all workers. Dividing the total over the root stage time estimates parallelism.");
  out->cr();

  for (uint i = 0; i < _num_phases; i++) {
    if (_global_data[i].maximum() != 0) {
      out->print_cr(SHENANDOAH_PHASE_NAME_FORMAT " = " SHENANDOAH_S_TIME_FORMAT " s "
                    "(a = " SHENANDOAH_US_TIME_FORMAT " us) "
                    "(n = " INT32_FORMAT_W(5) ") (lvls, us = "
                    SHENANDOAH_US_TIME_FORMAT ", "
                    SHENANDOAH_US_TIME_FORMAT ", "
                    SHENANDOAH_US_TIME_FORMAT ", "
                    SHENANDOAH_US_TIME_FORMAT ", "
                    SHENANDOAH_US_TIME_FORMAT ")",
                    _desc[i],
                    _global_data[i].sum(),
                    _global_data[i].avg() * 1000000.0,
                    _global_data[i].num(),
                    _global_data[i].percentile(0) * 1000000.0,
                    _global_data[i].percentile(25) * 1000000.0,
                    _global_data[i].percentile(50) * 1000000.0,
                    _global_data[i].percentile(75) * 1000000.0,
                    _global_data[i].maximum() * 1000000.0
      );
    }
  }
}

ShenandoahWorkerTimingsTracker::ShenandoahWorkerTimingsTracker(ShenandoahPhaseTimings::Phase phase,
        ShenandoahPhaseTimings::WorkerPhase worker_phase, uint worker_id, bool cumulative) :
        _timings(ShenandoahHeap::heap()->phase_timings()),
        _phase(phase), _worker_phase(worker_phase), _worker_id(worker_id) {

  assert(_timings->worker_data(_phase, _worker_phase)->get(_worker_id) == ShenandoahWorkerData::uninitialized() || cumulative,
         "Should not be set yet: %s", ShenandoahPhaseTimings::phase_desc(_timings->compute_phase_slot(_phase, _worker_phase)));
  _start_time = os::elapsedTime();
}

ShenandoahWorkerTimingsTracker::~ShenandoahWorkerTimingsTracker() {
  _timings->worker_data(_phase, _worker_phase)->set_or_add(_worker_id, os::elapsedTime() - _start_time);

  if (ShenandoahPhaseTimings::is_root_work_phase(_phase)) {
    ShenandoahPhaseTimings::Phase root_phase = _phase;
    ShenandoahPhaseTimings::Phase cur_phase = _timings->compute_phase_slot(root_phase, _worker_phase);
    _event.commit(GCId::current(), _worker_id, ShenandoahPhaseTimings::phase_desc(cur_phase));
  }
}
