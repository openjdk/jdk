/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHADAPTIVEHEURISTICS_HPP
#define SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHADAPTIVEHEURISTICS_HPP

#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahAllocRate.inline.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "utilities/numberSeq.hpp"

class ShenandoahCycleDuration {

  const static size_t GC_TIME_SAMPLE_SIZE;

  // Keep track of GC_TIME_SAMPLE_SIZE most recent concurrent GC cycle times
  uint _gc_time_first_sample_index;
  uint _gc_time_num_samples;
  double* const _gc_time_timestamps;
  double* const _gc_time_samples;
  double* const _gc_time_xy;    // timestamp * sample
  double* const _gc_time_xx;    // timestamp squared
  double _gc_time_sum_of_timestamps;
  double _gc_time_sum_of_samples;
  double _gc_time_sum_of_xy;
  double _gc_time_sum_of_xx;

  double _gc_time_m;            // slope
  double _gc_time_b;            // y-intercept
  double _gc_time_sd;           // sd on deviance from prediction

public:

  ShenandoahCycleDuration();
  ~ShenandoahCycleDuration();

  void record_duration(double timestamp_at_start, double duration);
  double predict_duration(double timestamp_at_start, double margin_of_error) const;
};

/*
 * The adaptive heuristic tracks the allocation behavior and average cycle
 * time of the application. It attempts to start a cycle with enough time
 * to complete before the available memory is exhausted. It errors on the
 * side of starting cycles early to avoid allocation failures (degenerated
 * cycles).
 *
 * This heuristic limits the number of regions for evacuation such that the
 * evacuation reserve is respected. This helps it avoid allocation failures
 * during evacuation. It preferentially selects regions with the most garbage.
 */
class ShenandoahAdaptiveHeuristics : public ShenandoahHeuristics {
public:
  explicit ShenandoahAdaptiveHeuristics(ShenandoahSpaceInfo* space_info);

  void initialize() override;

  void post_initialize() override;

  // At the end of GC(N), we idle GC until necessary to start the next GC.  Compute the threshold of memory that can be allocated
  // before we need to start the next GC.
  void start_idle_span() override;

  void record_success_concurrent() override;
  void record_degenerated() override;
  void record_success_full() override;

  bool trigger_average_allocation_rate(size_t allocatable_words, double avg_alloc_rate);

  bool trigger_accelerating_allocation_rate(ShenandoahAllocRate<> &new_rate, size_t allocatable_words,
                                            double avg_alloc_rate);

  bool should_start_gc() override;

  const char* name() override     { return "Adaptive"; }
  bool is_diagnostic() override   { return false; }
  bool is_experimental() override { return false; }

 private:
  // These are used to adjust the margin of error and the spike threshold
  // in response to GC cycle outcomes. These values are shared, but the
  // margin of error and spike threshold trend in opposite directions.
  const static double FULL_PENALTY_SD;
  const static double DEGENERATE_PENALTY_SD;

  const static double MINIMUM_CONFIDENCE;
  const static double MAXIMUM_CONFIDENCE;

  const static double LOWEST_EXPECTED_AVAILABLE_AT_END;
  const static double HIGHEST_EXPECTED_AVAILABLE_AT_END;

  void adjust_last_trigger_parameters(double amount);
  void adjust_margin_of_error(double amount);
  void adjust_spike_threshold(double amount);

  // Returns number of words that can be allocated before we need to trigger next GC, given available in bytes.
  inline size_t allocatable(size_t available) const {
    return (available > _headroom_adjustment)? (available - _headroom_adjustment) / HeapWordSize: 0;
  }

protected:
  void adjust_penalty(intx step) override;
  void choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                             RegionData* data, size_t size,
                                             size_t actual_free) override;


  ShenandoahCycleDuration _cycles;

  // Used to record the last trigger that signaled to start a GC.
  // This itself is used to decide whether or not to adjust the margin of
  // error for the average cycle time and allocation rate or the allocation
  // spike detection threshold.
  enum Trigger {
    SPIKE, RATE, OTHER
  };

  // The margin of error expressed in standard deviations to add to our
  // average cycle time and allocation rate. As this value increases we
  // tend to overestimate the rate at which mutators will deplete the
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

  ShenandoahFreeSet* _free_set;

  // This represents the time at which the allocation rate was most recently sampled for the purpose of detecting acceleration.
  double _previous_acceleration_sample_timestamp;

  // bytes of headroom at which we should trigger GC
  size_t _headroom_adjustment;

  // In preparation for a span during which GC will be idle, compute the headroom adjustment that will be used to
  // detect when GC needs to trigger.
  void compute_headroom_adjustment() override;

  void add_degenerated_gc_time(double timestamp_at_start, double duration);

  // A conservative minimum threshold of free space that we'll try to maintain when possible.
  // For example, we might trigger a concurrent gc if we are likely to drop below
  // this threshold, or we might consider this when dynamically resizing generations
  // in the generational case. Controlled by global flag ShenandoahMinFreeThreshold.
  size_t min_free_threshold();

  void accept_trigger_with_type(Trigger trigger_type) {
    _last_trigger = trigger_type;
    accept_trigger();
  }

  bool trigger_min_free_threshold(size_t available);
  bool trigger_learning(size_t available, size_t capacity);
};

#endif // SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHADAPTIVEHEURISTICS_HPP
