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
#include "gc/g1/g1OldGenAllocationTracker.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/debug.hpp"

void G1ConcurrentCycleTracker::reset() {
  _state = CycleState::Inactive;
  _total_pause_time_s = 0.0;
  _cycle_start_time_s = 0.0;
  _cycle_end_time_s = 0.0;

  _humongous_bytes_at_start = 0;
  _non_humongous_allocated_bytes = 0;
  _peak_extra_humongous_occupancy_bytes = 0;
}

void G1ConcurrentCycleTracker::update_allocation_stats(G1AllocationIntervalStats interval_stats) {
  if (!is_active()) {
    return;
  }

  _non_humongous_allocated_bytes += interval_stats._non_humongous_allocated_bytes;

  intptr_t delta_before = checked_cast<intptr_t>(interval_stats._total_humongous_before_bytes) -
                          checked_cast<intptr_t>(_humongous_bytes_at_start);

  intptr_t delta_after = delta_before +
                         checked_cast<intptr_t>(interval_stats._humongous_allocated_bytes);

  if (delta_after > 0) {
    _peak_extra_humongous_occupancy_bytes = MAX2(_peak_extra_humongous_occupancy_bytes, checked_cast<size_t>(delta_after));
  }
}

G1ConcurrentCycleTracker::G1ConcurrentCycleTracker()
: _state(CycleState::Inactive),
  _cycle_start_time_s(0.0),
  _cycle_end_time_s(0.0),
  _total_pause_time_s(0.0),
  _humongous_bytes_at_start(0),
  _non_humongous_allocated_bytes(0),
  _peak_extra_humongous_occupancy_bytes(0)
{ }

void G1ConcurrentCycleTracker::record_cycle_start(double cycle_start_time_s, size_t humongous_bytes_after_pause) {
  assert(_state == CycleState::Inactive, "Concurrent start out of order.");
  _cycle_start_time_s = cycle_start_time_s;
  _humongous_bytes_at_start = humongous_bytes_after_pause;
  _state = CycleState::Active;
}

void G1ConcurrentCycleTracker::record_allocation_interval(Pause pause_type,
                                                          bool is_periodic_gc,
                                                          double pause_start_time_s,
                                                          double pause_end_time_s,
                                                          G1AllocationIntervalStats interval_stats) {
  if (is_periodic_gc || pause_type == Pause::Full || pause_type == Pause::ConcurrentStartUndo) {
    reset();
    return;
  }

  if (pause_type == Pause::ConcurrentStartFull) {
    record_cycle_start(pause_end_time_s, interval_stats._total_humongous_after_bytes);
    return;
  }

  if (!is_active()) {
    return;
  }

  update_allocation_stats(interval_stats);

  if (pause_type == Pause::Mixed) {
    complete_cycle(pause_start_time_s);
    return;
  }

  assert(pause_type == Pause::Normal ||
         pause_type == Pause::PrepareMixed ||
         pause_type == Pause::Remark ||
         pause_type == Pause::Cleanup,
         "Unhandled pause type");

  add_pause(pause_end_time_s - pause_start_time_s);
}

void G1ConcurrentCycleTracker::complete_cycle(double cycle_end_time_s) {
  precond(is_active());

  _cycle_end_time_s = cycle_end_time_s;
  _state = CycleState::Complete;
}

G1ConcurrentCycleStats G1ConcurrentCycleTracker::get_and_reset_cycle_stats() {
  precond(has_completed_cycle());

  double cycle_duration = (_cycle_end_time_s - _cycle_start_time_s - _total_pause_time_s);

  G1ConcurrentCycleStats stats{
    cycle_duration,
    _non_humongous_allocated_bytes,
    _peak_extra_humongous_occupancy_bytes
  };

  reset();
  return stats;
}
