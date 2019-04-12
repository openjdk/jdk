/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahTimingTracker.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "runtime/os.hpp"


ShenandoahPhaseTimings::Phase ShenandoahTerminationTracker::_current_termination_phase = ShenandoahPhaseTimings::_num_phases;

ShenandoahWorkerTimingsTracker::ShenandoahWorkerTimingsTracker(ShenandoahWorkerTimings* worker_times,
                                                              ShenandoahPhaseTimings::GCParPhases phase, uint worker_id) :
  _phase(phase), _worker_times(worker_times), _worker_id(worker_id) {
  if (_worker_times != NULL) {
    _start_time = os::elapsedTime();
  }
}

ShenandoahWorkerTimingsTracker::~ShenandoahWorkerTimingsTracker() {
  if (_worker_times != NULL) {
    _worker_times->record_time_secs(_phase, _worker_id, os::elapsedTime() - _start_time);
  }

  if (ShenandoahGCPhase::is_root_work_phase()) {
    ShenandoahPhaseTimings::Phase root_phase = ShenandoahGCPhase::current_phase();
    ShenandoahPhaseTimings::Phase cur_phase = (ShenandoahPhaseTimings::Phase)((int)root_phase + (int)_phase + 1);
    _event.commit(GCId::current(), _worker_id, ShenandoahPhaseTimings::phase_name(cur_phase));
  }
}

ShenandoahTerminationTimingsTracker::ShenandoahTerminationTimingsTracker(uint worker_id) :
  _worker_id(worker_id)  {
  if (ShenandoahTerminationTrace) {
    _start_time = os::elapsedTime();
  }
}

ShenandoahTerminationTimingsTracker::~ShenandoahTerminationTimingsTracker() {
  if (ShenandoahTerminationTrace) {
    ShenandoahHeap::heap()->phase_timings()->termination_times()->record_time_secs(_worker_id, os::elapsedTime() - _start_time);
  }
}

ShenandoahTerminationTracker::ShenandoahTerminationTracker(ShenandoahPhaseTimings::Phase phase) : _phase(phase) {
  assert(_current_termination_phase == ShenandoahPhaseTimings::_num_phases, "Should be invalid");
  assert(phase == ShenandoahPhaseTimings::termination ||
         phase == ShenandoahPhaseTimings::final_traversal_gc_termination ||
         phase == ShenandoahPhaseTimings::full_gc_mark_termination ||
         phase == ShenandoahPhaseTimings::conc_termination ||
         phase == ShenandoahPhaseTimings::conc_traversal_termination ||
         phase == ShenandoahPhaseTimings::weakrefs_termination ||
         phase == ShenandoahPhaseTimings::full_gc_weakrefs_termination,
         "Only these phases");

  assert(!Thread::current()->is_Worker_thread() &&
             (Thread::current()->is_VM_thread() ||
              Thread::current()->is_ConcurrentGC_thread()),
        "Called from wrong thread");

  _current_termination_phase = phase;
  ShenandoahHeap::heap()->phase_timings()->termination_times()->reset();
}

ShenandoahTerminationTracker::~ShenandoahTerminationTracker() {
  assert(_phase == _current_termination_phase, "Can not change phase");
  ShenandoahPhaseTimings* phase_times = ShenandoahHeap::heap()->phase_timings();

  double t = phase_times->termination_times()->average();
  phase_times->record_phase_time(_phase, t);
  debug_only(_current_termination_phase = ShenandoahPhaseTimings::_num_phases;)
}
