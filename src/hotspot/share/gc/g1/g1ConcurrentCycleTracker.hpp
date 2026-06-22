/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1CONCURRENTCYCLETRACKER_HPP
#define SHARE_GC_G1_G1CONCURRENTCYCLETRACKER_HPP

#include "gc/g1/g1CollectorState.hpp"
#include "utilities/globalDefinitions.hpp"

struct G1AllocationIntervalStats;

// The sampling interval for G1ConcurrentCycleTracker covers the concurrent cycle
// from the end of the Concurrent Start GC to start of the first Mixed GC.
struct G1ConcurrentCycleStats {
  double _cycle_duration_s;
  size_t _non_humongous_allocated_bytes;
  size_t _peak_extra_humongous_occupancy_bytes;

  G1ConcurrentCycleStats(double cycle_duration_s,
                         size_t non_humongous_allocated_bytes,
                         size_t peak_extra_humongous_occupancy_bytes)
  : _cycle_duration_s(cycle_duration_s),
    _non_humongous_allocated_bytes(non_humongous_allocated_bytes),
    _peak_extra_humongous_occupancy_bytes(peak_extra_humongous_occupancy_bytes)
  { }
};

class G1ConcurrentCycleTracker {
  using Pause = G1CollectorState::Pause;

  enum class CycleState {
    Inactive,
    Active,
    Complete,
  };

  CycleState _state;
  double _cycle_start_time_s;
  double _cycle_end_time_s;
  double _total_pause_time_s;

  // allocation accounting
  size_t _humongous_bytes_at_start;
  size_t _non_humongous_allocated_bytes;
  size_t _peak_extra_humongous_occupancy_bytes;

  void reset();

  bool is_active() const {
    return _state == CycleState::Active;
  }
  void update_allocation_stats(G1AllocationIntervalStats interval_stats);

  void add_pause(double pause_duration_s) {
    _total_pause_time_s += pause_duration_s;
  }

  void record_cycle_start(double cycle_start_time_s, size_t humongous_bytes_after_pause);

  void complete_cycle(double cycle_end_time_s);

 public:
  G1ConcurrentCycleTracker();

  void record_allocation_interval(Pause pause_type,
                                  bool is_periodic_gc,
                                  double pause_start_time_s,
                                  double pause_end_time_s,
                                  G1AllocationIntervalStats interval_stats);

  void abort_cycle() {
    reset();
  }

  bool has_completed_cycle() const {
    return _state == CycleState::Complete;
  }

  size_t non_humongous_allocated_bytes() const {
    return _non_humongous_allocated_bytes;
  }

  size_t peak_extra_humongous_occupancy_bytes() const {
    return _peak_extra_humongous_occupancy_bytes;
  }

  G1ConcurrentCycleStats get_and_reset_cycle_stats();
};

#endif // SHARE_GC_G1_G1CONCURRENTCYCLETRACKER_HPP
