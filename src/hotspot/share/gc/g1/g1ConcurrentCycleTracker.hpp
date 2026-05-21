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
#include "utilities/checkedCast.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

struct G1MutatorPeriodStatsBytes {
  size_t _non_hum_allocated;
  size_t _hum_allocated;
  size_t _total_hum_before;
  size_t _total_hum_after;

  G1MutatorPeriodStatsBytes(size_t non_hum_allocated,
                            size_t hum_allocated,
                            size_t total_hum_before,
                            size_t total_hum_after)
    : _non_hum_allocated(non_hum_allocated),
      _hum_allocated(hum_allocated),
      _total_hum_before(total_hum_before),
      _total_hum_after(total_hum_after)
    { }

  void record_humongous_allocation(size_t humongous_allocation_bytes) {
    _hum_allocated += humongous_allocation_bytes;
    _total_hum_after += humongous_allocation_bytes;
  }
};

// The Concurrent Cycle is the interval After the Concurrent-Start-GC until
// the first Mixed-GC.
struct G1ConcurrentCycleStats {
  double _cycle_duration_s;
  size_t _non_hum_allocated_bytes;
  size_t _peak_extra_humongous_allocated;

  G1ConcurrentCycleStats(double cycle_duration_s,
                         size_t non_hum_allocated_bytes,
                         size_t peak_extra_humongous_allocated)
  : _cycle_duration_s(cycle_duration_s),
    _non_hum_allocated_bytes(non_hum_allocated_bytes),
    _peak_extra_humongous_allocated(peak_extra_humongous_allocated)
  { }
};

class G1ConcurrentCycleTracker{
  using Pause = G1CollectorState::Pause;

  enum class CycleState {
    InActive,
    Active,
    Complete,
  };

  CycleState _state;
  double _cycle_start_time;
  double _cycle_end_time;
  double _total_gc_pauses_in_cycle;

  // allocation accounting
  size_t _hum_bytes_at_start;
  size_t _non_hum_bytes_allocated;
  intptr_t _peak_extra_humongous_reserve_bytes;
private:

  void reset();

  bool is_active() const {
    return _state == CycleState::Active;
  }
  void update_mutator_stats(double pause_duration, G1MutatorPeriodStatsBytes period_stats);

 public:
  G1ConcurrentCycleTracker();

  void record_cycle_start(double start_time, size_t humongous_bytes_after_gc);

  void record_mutator_period(Pause gc_type,
                             bool is_periodic_gc,
                             double start,
                             double end,
                             G1MutatorPeriodStatsBytes period_stats);

  void complete_cycle(double cycle_end_time, double mixed_gc_duration);

  void abort_cycle() {
    reset();
  }

  bool has_completed_cycle() const {
    return _state == CycleState::Complete;
  }

  G1ConcurrentCycleStats get_and_reset_cycle_stats();

  size_t non_hum_bytes_allocated() const {
    return _non_hum_bytes_allocated;
  }

  size_t peak_extra_humongous_reserve_bytes() const {
    return checked_cast<size_t>(_peak_extra_humongous_reserve_bytes);
  }
};

#endif // SHARE_GC_G1_G1CONCURRENTCYCLETRACKER_HPP
