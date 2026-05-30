/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1ConcurrentCycleTracker.hpp"
#include "utilities/checkedCast.hpp"

void G1ConcurrentCycleTracker::reset() {
  _state = CycleState::InActive;
  _total_gc_pauses_in_cycle = 0.0;
  _cycle_start_time = 0.0;
  _cycle_end_time = 0.0;

  _hum_bytes_at_start = 0;
  _non_hum_bytes_allocated = 0;
  _peak_extra_humongous_reserve_bytes = 0;
}

void G1ConcurrentCycleTracker::update_mutator_stats(double pause_duration,
                                                    G1MutatorPeriodStatsBytes period_stats) {
  if (!is_active()) {
    return;
  }

  _total_gc_pauses_in_cycle += pause_duration;
  _non_hum_bytes_allocated += period_stats._non_hum_allocated;

  intptr_t delta_before_mutator_period = checked_cast<intptr_t>(period_stats._total_hum_before) -
                                         checked_cast<intptr_t>(_hum_bytes_at_start);

  intptr_t delta_after_mutator_period = delta_before_mutator_period +
                                        checked_cast<intptr_t>(period_stats._hum_allocated);

  if (delta_after_mutator_period > 0) {
    _peak_extra_humongous_reserve_bytes = MAX2(_peak_extra_humongous_reserve_bytes, delta_after_mutator_period);
  }
}

G1ConcurrentCycleTracker::G1ConcurrentCycleTracker()
: _state(CycleState::InActive),
  _cycle_start_time(0.0),
  _cycle_end_time(0.0),
  _total_gc_pauses_in_cycle(0.0),
  _hum_bytes_at_start(0),
  _non_hum_bytes_allocated(0),
  _peak_extra_humongous_reserve_bytes(0)
{ }

void G1ConcurrentCycleTracker::record_cycle_start(double start_time, size_t humongous_bytes_after_gc) {
  assert(!is_active(), "Concurrent start out of order.");
  _cycle_start_time = start_time;
  _hum_bytes_at_start = humongous_bytes_after_gc;
  _state = CycleState::Active;
}

void G1ConcurrentCycleTracker::record_mutator_period(Pause gc_type,
                                                     bool is_periodic_gc,
                                                     double start,
                                                     double end,
                                                     G1MutatorPeriodStatsBytes period_stats) {
  // Manage the mutator time tracking from concurrent start to first mixed gc.
  update_mutator_stats(end - start, period_stats);
  if (is_periodic_gc) {
    reset();
  }

  switch (gc_type) {
    case Pause::Full:
    case Pause::ConcurrentStartUndo:
      abort_cycle();
      break;
    case Pause::Cleanup:
    case Pause::Remark:
    case Pause::Normal:
    case Pause::PrepareMixed:
      break;
    case Pause::ConcurrentStartFull:
      // Do not track time-to-mixed time for periodic collections as they are likely
      // to be not representative to regular operation as the mutators are idle at
      // that time. Also only track full concurrent mark cycles.
      if (!is_periodic_gc) {
        record_cycle_start(end, period_stats._total_hum_after);
      }
      break;
    case Pause::Mixed:
      if (is_active()) {
        // we track the first mixed-gc
        complete_cycle(start, end - start);
      }
      break;
    default:
      ShouldNotReachHere();
  }
}

void G1ConcurrentCycleTracker::complete_cycle(double cycle_end_time, double mixed_gc_duration) {
  precond(is_active());
  // We record the pause_time before deciding whether to end the concurrent cycle.
  _total_gc_pauses_in_cycle -= mixed_gc_duration;

  _cycle_end_time = cycle_end_time;
  _state = CycleState::Complete;
}

G1ConcurrentCycleStats G1ConcurrentCycleTracker::get_and_reset_cycle_stats() {
  precond(has_completed_cycle());

  double cycle_duration = (_cycle_end_time - _cycle_start_time - _total_gc_pauses_in_cycle);

  G1ConcurrentCycleStats stats{
    cycle_duration,
    _non_hum_bytes_allocated,
    checked_cast<size_t>(_peak_extra_humongous_reserve_bytes)
  };

  reset();
  return stats;
}
