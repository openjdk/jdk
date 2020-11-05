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

#ifndef SHARE_VM_GC_SHENANDOAH_HEURISTICS_SHENANDOAHREACTIVEHEURISTICS_HPP
#define SHARE_VM_GC_SHENANDOAH_HEURISTICS_SHENANDOAHREACTIVEHEURISTICS_HPP

#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahAdaptiveHeuristics.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "utilities/numberSeq.hpp"

class ShenandoahReactiveHeuristics;

class ShenandoahAllocationRate : public CHeapObj<mtGC> {
 public:
  ShenandoahAllocationRate(ShenandoahReactiveHeuristics* heuristics);

  void sample(size_t bytes_allocated_since_gc_start);

  double upper_bound(double standard_deviations) const;

  void allocation_counter_reset();

  bool is_spiking(double instantaneous_rate) const;

  double instantaneous_rate(size_t bytes_allocated_since_gc_start) const;

 private:
  ShenandoahReactiveHeuristics *_heuristics;
  size_t _last_sample_time;
  size_t _last_sample_value;
  uint _interval_ns;
  TruncatedSeq _rate;
  TruncatedSeq _rate_avg;
};

/*
 * This class differs from ShenandoahAdaptiveHeuristics in the following ways:
 *
 *  1. It maintains a decaying moving average of the allocation rate and GC
 *     cycle duration. This heuristic also pads these moving averages with a
 *     margin of error based on the standard deviation. The margin of error
 *     makes this heuristic more likely to start a GC than the 'adaptive'
 *     heuristic. The margin of error is adjusted based on the outcome of each
 *     GC cycle.
 *
 *  2. It 'reacts' to sudden changes in the allocation rate. In addition to
 *     folding observations of the allocation rate into the moving average, this
 *     heuristic also considers how 'far away' the observed sample is from the
 *     moving average. If the latest sample exceeds a 'spike threshold' (measured
 *     in standard deviations) over the moving average allocation rate, a new
 *     concurrent cycle is started. This spike threshold is also adjusted based
 *     on the outcome of each GC cycle.
 *
 * These properties tend to increase the overall number of concurrent cycles,
 * while decreasing the number of degenerated or full cycles.
 */
class ShenandoahReactiveHeuristics : public ShenandoahAdaptiveHeuristics {
  friend class ShenandoahAllocationRate;

  // Used to record the last trigger that signaled to start a GC.
  // This itself is used to decide whether or not to adjust the margin of
  // error for the average cycle time and allocation rate or the allocation
  // spike detection threshold.
  enum Trigger {
    SPIKE, RATE, OTHER
  };

 public:
  ShenandoahReactiveHeuristics();

  virtual ~ShenandoahReactiveHeuristics();

  virtual void record_cycle_start();
  virtual void record_success_concurrent();
  virtual void record_success_degenerated();
  virtual void record_success_full();

  virtual bool should_start_gc() const;

  virtual const char* name()     { return "Reactive"; }
  virtual bool is_diagnostic()   { return false; }
  virtual bool is_experimental() { return true; }

 private:
  // These are used to adjust the margin of error and the spike threshold
  // in response to GC cycle outcomes. These values are shared, but the
  // margin of error and spike threshold trend in opposite directions.
  const static double FULL_PENALTY_SD;
  const static double DEGENERATE_PENALTY_SD;

  const static double MINIMUM_CONFIDENCE;
  const static double MAXIMUM_CONFIDENCE;

  bool is_allocation_rate_too_high(size_t capacity, size_t available,
                                   size_t bytes_allocated_since_gc_start) const;

  void adjust_last_trigger_parameters(double amount);
  void adjust_margin_of_error(double amount);
  void adjust_spike_threshold(double amount);

  ShenandoahAllocationRate _allocation_rate;

  // Record the available heap at the start of the cycle so that we can
  // evaluate the outcome of the cycle. This lets us 'react' to concurrent
  // cycles that did not degenerate, but perhaps did not reclaim as much
  // memory as we would like.
  size_t _available_at_cycle_start;

  // The margin of error expressed in standard deviations to add to our
  // average cycle time and allocation rate. As this value increases we
  // tend to over estimate the rate at which mutators will deplete the
  // heap. In other words, erring on the side of caution will trigger more
  // concurrent GCs.
  double _margin_of_error_sd;

  // The allocation spike threshold is expressed in standard deviations.
  // If the standard deviation of the most recent sample of the allocation
  // rate exceeds this threshold, a GC cycle is started. As this value
  // decreases the sensitivity to allocation spikes increases. In other
  // words, lowering the spike threshold will tend to increase the number
  // of concurrent GCs.
  double _spike_threshold_sd;

  // Remember which trigger is responsible for the last GC cycle. When the
  // outcome of the cycle is evaluated we will adjust the parameters for the
  // corresponding triggers. Note that successful outcomes will raise
  // the spike threshold and lower the margin of error.
  Trigger _last_trigger;

  // Keep track of the available memory at the end of a GC cycle. This
  // establishes what is 'normal' for the application and is used as a
  // source of feedback to adjust trigger parameters.
  TruncatedSeq _available;
};

#endif // SHARE_VM_GC_SHENANDOAH_HEURISTICS_SHENANDOAHREACTIVEHEURISTICS_HPP
