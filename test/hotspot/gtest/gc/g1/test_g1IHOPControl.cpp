/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectorState.inline.hpp"
#include "gc/g1/g1IHOPControl.hpp"
#include "gc/g1/g1OldGenAllocationTracker.hpp"
#include "gc/g1/g1Predictions.hpp"
#include "unittest.hpp"

struct GCPauseData {
  double _gc_start_time_s = 0;
  double _gc_pause_duration_s = 0;
  size_t _desired_young_bytes = 0;
  size_t _non_hum_alloc_bytes = 0;
  size_t _hum_alloc_bytes = 0;
  size_t _total_hum_after_gc_bytes = 0;
  size_t _gc_trigger_hum_bytes = 0;

  GCPauseData(double gc_start_time_s,
              double gc_pause_duration_s,
              size_t desired_young_bytes,
              size_t non_hum_alloc_bytes,
              size_t hum_alloc_bytes,
              size_t total_hum_after_gc_bytes,
              size_t gc_trigger_hum_bytes = 0)
      : _gc_start_time_s(gc_start_time_s),
        _gc_pause_duration_s(gc_pause_duration_s),
        _desired_young_bytes(desired_young_bytes),
        _non_hum_alloc_bytes(non_hum_alloc_bytes),
        _hum_alloc_bytes(hum_alloc_bytes),
        _total_hum_after_gc_bytes(total_hum_after_gc_bytes),
        _gc_trigger_hum_bytes(gc_trigger_hum_bytes) {}
};

struct G1IHOPTestController {
  G1ConcurrentCycleTracker _conc_cycle_tracker;
  G1OldGenAllocationTracker _alloc_tracker;
  G1Predictions _pred;
  G1IHOPControl _ihop_control;

  G1IHOPTestController(bool adaptive, size_t ihop, size_t target_occupancy)
   : _conc_cycle_tracker(),
     _alloc_tracker(),
     _pred(0.50),
     _ihop_control(ihop, adaptive, &_pred, 0 /* heap_reserve_percent */, 0 /* heap_waste_percent */)
  {
    _ihop_control.update_target_occupancy(target_occupancy);
  }

  void end_mutator_phase(GCPauseData pause_data, G1CollectorState::Pause pause_type) {

    _alloc_tracker.add_allocated_bytes_since_last_gc(pause_data._non_hum_alloc_bytes);
    _alloc_tracker.add_allocated_humongous_bytes_since_last_gc(pause_data._hum_alloc_bytes);

    G1MutatorPeriodStatsBytes period_stats = _alloc_tracker.end_mutator_period(pause_data._total_hum_after_gc_bytes);

    if (pause_data._gc_trigger_hum_bytes > 0) {
      // Mirror G1Policy::record_pause() and the successful allocation tracking
      period_stats.record_humongous_allocation(pause_data._gc_trigger_hum_bytes);
      _alloc_tracker.record_collection_pause_humongous_allocation(pause_data._gc_trigger_hum_bytes);
    }

    _conc_cycle_tracker.record_mutator_period(pause_type,
                                              false /* is_periodic_gc */,
                                              pause_data._gc_start_time_s,
                                              pause_data._gc_start_time_s + pause_data._gc_pause_duration_s,
                                              period_stats);

    if (pause_type != G1CollectorState::Pause::Mixed &&
        pause_type != G1CollectorState::Pause::Full) {
      _ihop_control.record_expected_young_gen_size(pause_data._desired_young_bytes);
    }
  }

  void mutator_phase_end_with_conc_start(GCPauseData pause_data) {
    end_mutator_phase(pause_data, G1CollectorState::Pause::ConcurrentStartFull);
  }

  void mutator_phase_end_with_normal_gc(GCPauseData pause_data) {
    end_mutator_phase(pause_data, G1CollectorState::Pause::Normal);
  }

  void mutator_phase_end_with_mixed_gc(GCPauseData pause_data) {

    end_mutator_phase(pause_data, G1CollectorState::Pause::Mixed);

    G1ConcurrentCycleStats cycle_stats = _conc_cycle_tracker.get_and_reset_cycle_stats();

    _ihop_control.record_concurrent_cycle(cycle_stats._cycle_duration_s,
                                          cycle_stats._non_hum_allocated_bytes,
                                          cycle_stats._peak_extra_humongous_allocated);
  }

  size_t threshold() {
    return _ihop_control.old_gen_threshold_for_conc_mark_start();
  }
};

static void add_multiple_samples(G1IHOPTestController* ctrl,
                                 double mutator_duration_s,
                                 double gc_pause_duration_s,
                                 size_t young_reserve_bytes,
                                 size_t non_hum_bytes,
                                 size_t hum_alloc_bytes,
                                 size_t num_samples) {
  double gc_start_time_s = mutator_duration_s;

  for (size_t i = 0; i < num_samples; i++) {
    ctrl->mutator_phase_end_with_conc_start({
      gc_start_time_s,
      gc_pause_duration_s,
      young_reserve_bytes,
      non_hum_bytes,
      hum_alloc_bytes,
      hum_alloc_bytes  /* _total_hum_after_gc_bytes */
    });

    gc_start_time_s += (gc_pause_duration_s + mutator_duration_s);

    ctrl->mutator_phase_end_with_normal_gc({
      gc_start_time_s,
      gc_pause_duration_s,
      young_reserve_bytes,
      non_hum_bytes,
      hum_alloc_bytes,
      hum_alloc_bytes  /* _total_hum_after_gc_bytes */
    });

    gc_start_time_s += (gc_pause_duration_s + mutator_duration_s);

    ctrl->mutator_phase_end_with_mixed_gc({
      gc_start_time_s,
      gc_pause_duration_s,
      young_reserve_bytes,
      non_hum_bytes,
      hum_alloc_bytes,
      hum_alloc_bytes  /* _total_hum_after_gc_bytes */
    });
    gc_start_time_s += (gc_pause_duration_s + mutator_duration_s);
  }
}

static size_t old_gen_threshold(size_t target_occupancy_bytes,
                                size_t young_reserve_bytes,
                                size_t non_hum_bytes,
                                size_t hum_bytes,
                                double cycle_duration_s) {
  size_t needed_during_cycle = young_reserve_bytes + non_hum_bytes * cycle_duration_s + hum_bytes;
  return needed_during_cycle < target_occupancy_bytes ?
         target_occupancy_bytes - needed_during_cycle : 0;
}

TEST_VM(G1IHOPControl, allocation_tracker_incr) {
  // Test requires G1
  if (!UseG1GC) {
    return;
  }

  size_t initial_ihop = InitiatingHeapOccupancyPercent;
  G1IHOPTestController ctrl(false /* adaptive */, initial_ihop, 100 /* target_occupancy */);
  double const time_step_s = 1.0;
  double gc_start_time_s = time_step_s;
  double gc_pause_duration_s = 1.0;
  ctrl.mutator_phase_end_with_conc_start({
    gc_start_time_s,
    gc_pause_duration_s,
    10  /* _desired_young_bytes */,
    0   /* _non_hum_alloc_bytes */,
    0   /* _hum_alloc_bytes */,
    0   /* _total_hum_after_gc_bytes */
  });

  gc_start_time_s += (gc_pause_duration_s + time_step_s);

  ctrl.mutator_phase_end_with_normal_gc({
    gc_start_time_s,
    gc_pause_duration_s,
    10  /* _desired_young_bytes */,
    20  /* _non_hum_alloc_bytes */,
    30  /* _hum_alloc_bytes */,
    25  /* _total_hum_after_gc_bytes */
  });

  EXPECT_EQ(20u, ctrl._conc_cycle_tracker.non_hum_bytes_allocated());
  EXPECT_EQ(30u, ctrl._conc_cycle_tracker.peak_extra_humongous_reserve_bytes());

  gc_start_time_s += (gc_pause_duration_s + time_step_s);
  ctrl.mutator_phase_end_with_normal_gc({
    gc_start_time_s,
    gc_pause_duration_s,
    10  /* _desired_young_bytes */,
    5   /* _non_hum_alloc_bytes */,
    10  /* _hum_alloc_bytes */,
    10  /* _total_hum_after_gc_bytes */
  });

  // Peak Humongous should be:
  //  hum_after_gc (from previous gc) + _hum_alloc_bytes
  EXPECT_EQ(25u, ctrl._conc_cycle_tracker.non_hum_bytes_allocated());
  EXPECT_EQ(35u, ctrl._conc_cycle_tracker.peak_extra_humongous_reserve_bytes());
}

TEST_VM(G1IHOPControl, non_adaptive_ihop) {
  // Test requires G1
  if (!UseG1GC) {
    return;
  }

  size_t initial_ihop = InitiatingHeapOccupancyPercent;

  G1IHOPTestController ctrl(false /* adaptive */, initial_ihop, 100 /* target_occupancy */);

  add_multiple_samples(&ctrl,
                       1.0 /* mutator_duration_s */,
                       1.0 /* gc_pause_duration_s */,
                       10  /* young_reserve */,
                       20  /* non_hum_bytes */,
                       0   /* hum_alloc_bytes */,
                       100 /* num_samples */);

  EXPECT_EQ(initial_ihop, ctrl._ihop_control.old_gen_threshold_for_conc_mark_start());
}

TEST_VM(G1IHOPControl, adaptive_ihop_not_enough_samples) {
  // Test requires G1
  if (!UseG1GC) {
    return;
  }

  size_t initial_ihop = InitiatingHeapOccupancyPercent;

  G1IHOPTestController ctrl(true /* adaptive */, initial_ihop, 100 /* target_occupancy */);

  add_multiple_samples(&ctrl,
                       1.0 /* mutator_duration_s */,
                       1.0 /* gc_pause_duration_s */,
                       10  /* young_reserve */,
                       20  /* non_hum_bytes */,
                       0   /* hum_alloc_bytes */,
                       G1AdaptiveIHOPNumInitialSamples - 1 /* num_samples */);

  EXPECT_EQ(initial_ihop, ctrl._ihop_control.old_gen_threshold_for_conc_mark_start());
}

TEST_VM(G1IHOPControl, adaptive_ihop_non_humongous_only) {
  // Test requires G1
  if (!UseG1GC) {
    return;
  }

  size_t initial_ihop = InitiatingHeapOccupancyPercent;
  // G1Predictions require 5 or more samples to skip special considerations for
  // small samples.
  size_t num_samples = 5;

  G1IHOPTestController ctrl(true /* adaptive */, initial_ihop, 100 /* target_occupancy */);

  // We run 2 mutator periods for each concurrent cycle
  double total_cycle_duration_s = 2;
  add_multiple_samples(&ctrl,
                       1.0 /* mutator_duration_s */,
                       1.0 /* gc_pause_duration_s */,
                       10  /* young_reserve */,
                       20  /* non_hum_bytes */,
                       0   /* hum_alloc_bytes */,
                       num_samples);

  size_t expected_threshold = old_gen_threshold(100 /* target_occupancy */,
                                                10  /* young_reserve */,
                                                20  /* non_hum_bytes */,
                                                0   /* hum_bytes */,
                                                total_cycle_duration_s);

  EXPECT_EQ(expected_threshold, ctrl._ihop_control.old_gen_threshold_for_conc_mark_start());
}

TEST_VM(G1IHOPControl, adaptive_ihop_peak_humongous_only) {
  // Test requires G1
  if (!UseG1GC) {
    return;
  }

  size_t initial_ihop = InitiatingHeapOccupancyPercent;
  // G1Predictions require 5 or more samples to skip special considerations for
  // small samples.
  size_t num_samples = 5;

  G1IHOPTestController ctrl(true /* adaptive */, initial_ihop, 100 /* target_occupancy */);

  add_multiple_samples(&ctrl,
                       1.0 /* mutator_duration_s */,
                       1.0 /* gc_pause_duration_s */,
                       10  /* young_reserve */,
                       0   /* non_hum_bytes */,
                       30  /* hum_alloc_bytes */,
                       num_samples);

  double total_cycle_duration_s = 2;
  size_t expected_threshold = old_gen_threshold(100 /* target_occupancy_bytes */,
                                                10  /* young_reserve_bytes */,
                                                0   /* non_hum_bytes */,
                                                30  /* hum_alloc_bytes */,
                                                total_cycle_duration_s);

  EXPECT_EQ(expected_threshold, ctrl._ihop_control.old_gen_threshold_for_conc_mark_start());
}

TEST_VM(G1IHOPControl, adaptive_ihop_combined) {
  // Test requires G1
  if (!UseG1GC) {
    return;
  }

  size_t initial_ihop = InitiatingHeapOccupancyPercent;
  // G1Predictions require 5 or more samples to skip special considerations for
  // small samples.
  size_t num_samples = 5;

  G1IHOPTestController ctrl(true /* adaptive */, initial_ihop, 100 /* target_occupancy */);

  add_multiple_samples(&ctrl,
                       1.0 /* mutator_duration_s */,
                       1.0 /* gc_pause_duration_s */,
                       10  /* young_reserve */,
                       20  /* non_hum_bytes */,
                       30  /* hum_alloc_bytes */,
                       num_samples);

  double total_cycle_duration_s = 2;
  size_t expected_threshold = old_gen_threshold(100 /* target_occupancy */,
                                                10  /* young_reserve */,
                                                20  /* non_hum_bytes */,
                                                30  /* hum_bytes */,
                                                total_cycle_duration_s);

  EXPECT_EQ(expected_threshold, ctrl._ihop_control.old_gen_threshold_for_conc_mark_start());
}

TEST_VM(G1IHOPControl, adaptive_ihop_high_alloc_pressure) {
  // Test requires G1
  if (!UseG1GC) {
    return;
  }

  size_t initial_ihop = InitiatingHeapOccupancyPercent;
  // G1Predictions require 5 or more samples to skip special considerations for
  // small samples.
  size_t num_samples = 5;

  G1IHOPTestController ctrl(true /* adaptive */, initial_ihop, 100 /* target_occupancy */);

  add_multiple_samples(&ctrl,
                       1.0 /* mutator_duration_s */,
                       1.0 /* gc_pause_duration_s */,
                       10  /* young_reserve */,
                       70  /* non_hum_bytes */,
                       35  /* hum_alloc_bytes */,
                       num_samples);

  size_t expected_threshold = 0;

  EXPECT_EQ(expected_threshold, ctrl._ihop_control.old_gen_threshold_for_conc_mark_start());
}

TEST_VM(G1IHOPControl, adaptive_ihop_young_reserve) {
  // Test requires G1
  if (!UseG1GC) {
    return;
  }

  size_t initial_ihop = InitiatingHeapOccupancyPercent;
  // G1Predictions require 5 or more samples to skip special considerations for
  // small samples.
  size_t num_samples = 5;

  G1IHOPTestController ctrl_small(true /* adaptive */, initial_ihop, 100 /* target_occupancy */);

  add_multiple_samples(&ctrl_small,
                       1.0 /* mutator_duration_s */,
                       1.0 /* gc_pause_duration_s */,
                       10  /* young_reserve */,
                       20  /* non_hum_bytes */,
                       30  /* hum_alloc_bytes */,
                       num_samples);

  double total_cycle_duration_s = 2;
  size_t expected_small_young = old_gen_threshold(100 /* target_occupancy */,
                                                  10  /* young_reserve */,
                                                  20  /* non_hum_bytes */,
                                                  30  /* hum_bytes */,
                                                  total_cycle_duration_s);

  G1IHOPTestController ctrl_large(true /* adaptive */, initial_ihop, 100 /* target_occupancy */);

  add_multiple_samples(&ctrl_large,
                       1.0 /* mutator_duration_s */,
                       1.0 /* gc_pause_duration_s */,
                       25  /* young_reserve */,
                       20  /* non_hum_bytes */,
                       30  /* hum_alloc_bytes */,
                       num_samples);

  size_t expected_large_young = old_gen_threshold(100 /* target_occupancy */,
                                                  25  /* young_reserve */,
                                                  20  /* non_hum_bytes */,
                                                  30  /* hum_bytes */,
                                                  total_cycle_duration_s);

  EXPECT_EQ(expected_small_young, ctrl_small._ihop_control.old_gen_threshold_for_conc_mark_start());
  EXPECT_EQ(expected_large_young, ctrl_large._ihop_control.old_gen_threshold_for_conc_mark_start());

  EXPECT_LT(expected_large_young, expected_small_young);
}

TEST_VM(G1IHOPControl, adaptive_ihop_recovers_after_spike) {
  // Test requires G1
  if (!UseG1GC) {
    return;
  }

  size_t initial_ihop = InitiatingHeapOccupancyPercent;
  // G1Predictions require 5 or more samples to skip special considerations for
  // small samples.

  G1IHOPTestController ctrl(true /* adaptive */, initial_ihop, 100 /* target_occupancy */);

  add_multiple_samples(&ctrl,
                       1.0 /* mutator_duration_s */,
                       1.0 /* gc_pause_duration_s */,
                       10  /* young_reserve */,
                       20  /* non_hum_bytes */,
                       10  /* hum_alloc_bytes */,
                       20  /* num_samples */);

  size_t before_spike = ctrl._ihop_control.old_gen_threshold_for_conc_mark_start();

  add_multiple_samples(&ctrl,
                       1.0 /* mutator_duration_s */,
                       1.0 /* gc_pause_duration_s */,
                       10  /* young_reserve */,
                       40  /* non_hum_bytes */,
                       30  /* hum_alloc_bytes */,
                       5   /* num_samples */);

  size_t during_spike = ctrl._ihop_control.old_gen_threshold_for_conc_mark_start();

  add_multiple_samples(&ctrl,
                       1.0 /* mutator_duration_s */,
                       1.0 /* gc_pause_duration_s */,
                       10  /* young_reserve */,
                       20  /* non_hum_bytes */,
                       5   /* hum_alloc_bytes */,
                       20  /* num_samples */);

  size_t after_recovery = ctrl._ihop_control.old_gen_threshold_for_conc_mark_start();

  EXPECT_LT(during_spike, before_spike);
  EXPECT_GT(after_recovery, during_spike);
}

TEST_VM(G1IHOPControl, adaptive_ihop_reuse_eagerly_reclaimed) {
  // Test requires G1
  if (!UseG1GC) {
    return;
  }

  size_t initial_ihop = InitiatingHeapOccupancyPercent;

  size_t target_occupancy = 100;
  size_t young_reserve    = 10;

  G1IHOPTestController ctrl(true /* adaptive */, initial_ihop, target_occupancy);

  double mutator_duration_s = 1.0;
  double gc_pause_duration_s = 1.0;
  double gc_start_time_s = mutator_duration_s;
  size_t h_t0 = 100;
  ctrl.mutator_phase_end_with_conc_start({
      gc_start_time_s,
      gc_pause_duration_s,
      10    /* young_reserve_bytes */,
      10    /* non_hum_bytes */,
      h_t0  /* hum_alloc_bytes */,
      h_t0  /* total_hum_after_gc_bytes */
    });

  // First mutator phase:
  // No new humongous allocations in this phase.
  // h_t1 < h_t0 (eager reclaim)
  gc_start_time_s += (gc_pause_duration_s + mutator_duration_s);
  size_t h_t1 = 60;
  ctrl.mutator_phase_end_with_normal_gc({
      gc_start_time_s,
      gc_pause_duration_s,
      10   /* young_reserve_bytes */,
      0    /* non_hum_bytes */,
      0    /* hum_alloc_bytes */,
      h_t1 /* total_hum_after_gc_bytes */
    });

  EXPECT_EQ(0ul, ctrl._conc_cycle_tracker.peak_extra_humongous_reserve_bytes());

  // Second mutator phase:
  size_t hum_alloc_bytes = 50;
  size_t h_t2 = 60;
  gc_start_time_s += (gc_pause_duration_s + mutator_duration_s);
  ctrl.mutator_phase_end_with_normal_gc({
      gc_start_time_s,
      gc_pause_duration_s,
      10   /* young_reserve_bytes */,
      0    /* non_hum_bytes */,
      hum_alloc_bytes,
      h_t2 /* total_hum_after_gc_bytes */
    });
  // Expected:
  // delta_after_previous_gc = 60 - 100 = -40
  // delta_before_this_gc    = -40 + 50 = 10
  // peak extra reserve      = 10
  EXPECT_EQ(10ul, ctrl._conc_cycle_tracker.peak_extra_humongous_reserve_bytes());
}

TEST_VM(G1IHOPControl, adaptive_ihop_eager_reclaim_reduces_extra_humongous_reserve) {
  // Test requires G1
  if (!UseG1GC) {
    return;
  }

  size_t initial_ihop = InitiatingHeapOccupancyPercent;
  // G1Predictions require 5 or more samples to skip special considerations for
  // small samples.
  size_t num_samples = 5;

  size_t target_occupancy = 100;
  size_t young_reserve    = 10;
  double mutator_duration_s = 1.0;
  double gc_pause_duration_s = 1.0;
  double gc_start_time_s = mutator_duration_s;

  G1IHOPTestController ctrl(true /* adaptive */, initial_ihop, target_occupancy);

  for (size_t i = 0; i < num_samples; i++) {
    size_t h_t0 = 100;
    ctrl.mutator_phase_end_with_conc_start({
        gc_start_time_s,
        gc_pause_duration_s,
        young_reserve,
        10   /* non_hum_bytes */,
        h_t0 /* hum_alloc_bytes */,
        h_t0 /* total_hum_after_gc_bytes */
      });

    // First mutator phase:
    // No new humongous allocations in this phase.
    // h_t1 < h_t0 (eager reclaim)
    gc_start_time_s += (gc_pause_duration_s + mutator_duration_s);
    size_t h_t1 = 60;
    ctrl.mutator_phase_end_with_normal_gc({
        gc_start_time_s,
        gc_pause_duration_s,
        young_reserve,
        20   /* non_hum_bytes */,
        0    /* hum_alloc_bytes */,
        h_t1 /* total_hum_after_gc_bytes */
      });

    EXPECT_EQ(0ul, ctrl._conc_cycle_tracker.peak_extra_humongous_reserve_bytes());

    // Second mutator phase:
    size_t hum_alloc_bytes = 50;
    size_t h_t2 = 60;
    gc_start_time_s += (gc_pause_duration_s + mutator_duration_s);
    ctrl.mutator_phase_end_with_mixed_gc({
        gc_start_time_s,
        gc_pause_duration_s,
        10   /* young_reserve_bytes */,
        0    /* non_hum_bytes */,
        hum_alloc_bytes,
        h_t2 /* total_hum_after_gc_bytes */
      });

    gc_start_time_s += (gc_pause_duration_s + mutator_duration_s);
  }

  // Expected:
  // predicted_needed = young_reserve + non_hum_bytes + peak_extra_hum_reserve
  //                  = 10 + 20 + 10
  // threshold        = target - predicted_needed
  //                  = 100 - 40
  EXPECT_EQ(60ul, ctrl._ihop_control.old_gen_threshold_for_conc_mark_start());
}

TEST_VM(G1IHOPControl, adaptive_ihop_cycle_duration) {
  // Test requires G1
  if (!UseG1GC) {
    return;
  }

  size_t initial_ihop = InitiatingHeapOccupancyPercent;
  // G1Predictions require 5 or more samples to skip special considerations for
  // small samples.
  size_t num_samples = 5;

  G1IHOPTestController ctrl_a(true /* adaptive */, initial_ihop, 100 /* target_occupancy */);
  G1IHOPTestController ctrl_b(true /* adaptive */, initial_ihop, 100 /* target_occupancy */);

  add_multiple_samples(&ctrl_a,
                       1.0 /* mutator_duration_s */,
                       1.0 /* gc_pause_duration_s */,
                       10  /* young_reserve */,
                       40  /* non_hum_bytes */,
                       30  /* hum_bytes */,
                       num_samples);

  add_multiple_samples(&ctrl_b,
                       1.0 /* mutator_duration_s */,
                       2.0 /* gc_pause_duration_s */,
                       10  /* young_reserve */,
                       40  /* non_hum_bytes */,
                       30  /* hum_bytes */,
                       num_samples);

  EXPECT_EQ(ctrl_a._ihop_control.old_gen_threshold_for_conc_mark_start(),
            ctrl_b._ihop_control.old_gen_threshold_for_conc_mark_start());
}

TEST_VM(G1IHOPControl, adaptive_ihop_cycle_duration_scales) {
  // Test requires G1
  if (!UseG1GC) {
    return;
  }

  size_t initial_ihop = InitiatingHeapOccupancyPercent;
  // G1Predictions require 5 or more samples to skip special considerations for
  // small samples.
  size_t num_samples = 5;
  size_t target_occupancy = 200;

  G1IHOPTestController ctrl_short(true /* adaptive */, initial_ihop, target_occupancy);
  G1IHOPTestController ctrl_long(true /* adaptive */, initial_ihop, target_occupancy);

  double mutator_duration_s = 1.0;
  double short_cycle_duration_s = mutator_duration_s * 2;
  add_multiple_samples(&ctrl_short,
                       1.0 /* mutator_duration_s */,
                       1.0 /* gc_pause_duration_s */,
                       10  /* young_reserve */,
                       40  /* non_hum_bytes */,
                       30  /* hum_bytes */,
                       num_samples);

  double long_mutator_duration_s = 4.0;
  double long_cycle_duration_s = long_mutator_duration_s * 2;
  add_multiple_samples(&ctrl_long,
                       long_mutator_duration_s,
                       1.0 /* gc_pause_duration_s */,
                       10  /* young_reserve */,
                       80  /* non_hum_bytes */,
                       30  /* hum_bytes */,
                       num_samples);

  size_t expected_short = old_gen_threshold(200 /* target_occupancy */,
                                            10  /* young_reserve */,
                                            40  /* non_hum_bytes */,
                                            30  /* hum_bytes */,
                                            short_cycle_duration_s);

  size_t expected_long = old_gen_threshold(200 /* target_occupancy */,
                                           10  /* young_reserve */,
                                           (80 / long_mutator_duration_s)  /* non_hum_bytes */,
                                           30  /* hum_bytes */,
                                           long_cycle_duration_s);

  EXPECT_EQ(ctrl_short._ihop_control.old_gen_threshold_for_conc_mark_start(), expected_short);


  EXPECT_EQ(ctrl_long._ihop_control.old_gen_threshold_for_conc_mark_start(), expected_long);

  EXPECT_LT(ctrl_long._ihop_control.old_gen_threshold_for_conc_mark_start(),
            ctrl_short._ihop_control.old_gen_threshold_for_conc_mark_start());
}

TEST_VM(G1IHOPControl, adaptive_ihop_gc_humongous_allocation) {
  // Test requires G1
  if (!UseG1GC) {
    return;
  }

  size_t initial_ihop = InitiatingHeapOccupancyPercent;
  // G1Predictions require 5 or more samples to skip special considerations for
  // small samples.
  size_t num_samples = 5;

  size_t target_occupancy_bytes = 100;
  size_t young_reserve_bytes    = 10;
  size_t trigger_hum_bytes = 30;

  double mutator_duration_s = 1.0;
  double gc_pause_duration_s = 1.0;
  double gc_start_time_s = mutator_duration_s;

  G1IHOPTestController ctrl(true /* adaptive */, initial_ihop, target_occupancy_bytes);

  for (size_t i = 0; i < num_samples; i++) {
    ctrl.mutator_phase_end_with_conc_start({
        gc_start_time_s,
        gc_pause_duration_s,
        young_reserve_bytes,
        10 /* non_hum_bytes */,
        0  /* hum_alloc_bytes */,
        0  /* total_hum_after_gc_bytes */
      });

    // First mutator phase:
    // No new humongous allocations in this phase.
    // h_t1 < h_t0 (eager reclaim)
    gc_start_time_s += (gc_pause_duration_s + mutator_duration_s);
    size_t h_t1 = 60;
    ctrl.mutator_phase_end_with_normal_gc({
        gc_start_time_s,
        gc_pause_duration_s,
        young_reserve_bytes,
        20 /* non_hum_bytes */,
        0  /* hum_alloc_bytes */,
        0  /* total_hum_after_gc_bytes */,
        trigger_hum_bytes
      });

    EXPECT_EQ(trigger_hum_bytes, ctrl._conc_cycle_tracker.peak_extra_humongous_reserve_bytes());

    // Second mutator phase:
    gc_start_time_s += (gc_pause_duration_s + mutator_duration_s);
    ctrl.mutator_phase_end_with_mixed_gc({
        gc_start_time_s,
        gc_pause_duration_s,
        10   /* young_reserve_bytes */,
        10   /* non_hum_bytes */,
        0,   /* hum_alloc_bytes */
        trigger_hum_bytes /* total_hum_after_gc_bytes */
      });

    gc_start_time_s += (gc_pause_duration_s + mutator_duration_s);
  }

  // Expected:
  // predicted_needed = young_reserve + non_hum_bytes + peak_extra_hum_reserve
  //                  = 10 + 30 + 30
  // threshold        = target - predicted_needed
  //                  = 100 - 70
  EXPECT_EQ(30ul, ctrl._ihop_control.old_gen_threshold_for_conc_mark_start());
}

// Models humongous allocation that triggers a concurrent cycle. Make sure that this
// allocation is not counted against the peak reserve because conceptually it is
// considered as already allocated during concurrent cycle start.
TEST_VM(G1IHOPControl, adaptive_ihop_humongous_allocation_causes_conc_start) {
  // Test requires G1
  if (!UseG1GC) {
    return;
  }

  size_t initial_ihop = InitiatingHeapOccupancyPercent;

  size_t target_occupancy = 100;

  G1IHOPTestController ctrl(true /* adaptive */, initial_ihop, target_occupancy);

  double mutator_duration_s = 1.0;
  double gc_pause_duration_s = 1.0;
  double gc_start_time_s = mutator_duration_s;
  size_t h_t0 = 100;
  ctrl.mutator_phase_end_with_conc_start({
      gc_start_time_s,
      gc_pause_duration_s,
      10    /* young_reserve_bytes */,
      10    /* non_hum_bytes */,
      0     /* hum_alloc_bytes */,
      0     /* total_hum_after_gc_bytes */,
      h_t0  /* gc_trigger_hum_bytes */
    });

  // No new humongous allocations in this phase.
  // h_t1 == h_t0 (no humongous allocation, no eager reclaim, keep the same)
  size_t h_t1 = h_t0;
  gc_start_time_s += (gc_pause_duration_s + mutator_duration_s);

  ctrl.mutator_phase_end_with_normal_gc({
      gc_start_time_s,
      gc_pause_duration_s,
      10   /* young_reserve_bytes */,
      0    /* non_hum_bytes */,
      0    /* hum_alloc_bytes */,
      h_t1 /* total_hum_after_gc_bytes */
    });

  EXPECT_EQ(0ul, ctrl._conc_cycle_tracker.peak_extra_humongous_reserve_bytes());
}
