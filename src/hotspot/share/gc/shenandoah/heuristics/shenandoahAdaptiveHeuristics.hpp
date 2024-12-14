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

#include "memory/allocation.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahRegulatorThread.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"
#include "utilities/numberSeq.hpp"

class ShenandoahAllocationRate : public CHeapObj<mtGC> {
 public:
  explicit ShenandoahAllocationRate();
  void allocation_counter_reset();

  double sample(size_t allocated);

  double upper_bound(double sds) const;
  bool is_spiking(double rate, double threshold) const;
  double interval() const {
    return _interval_sec;
  }
  double last_sample_time() const {
    return _last_sample_time;
  }

 private:

  double instantaneous_rate(double time, size_t allocated) const;

  double _last_sample_time;
  size_t _last_sample_value;
  double _interval_sec;
  TruncatedSeq _rate;
  TruncatedSeq _rate_avg;
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
  ShenandoahAdaptiveHeuristics(ShenandoahSpaceInfo* space_info);

  virtual ~ShenandoahAdaptiveHeuristics();

  virtual void initialize();

  virtual void choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                     RegionData* data, size_t size,
                                                     size_t actual_free);

  virtual void adjust_penalty(intx step);

  // At the end of GC(N), we idle GC until necessary to start the next GC.  Compute the threshold of memory that can be allocated
  // before we need to start the next GC.
  void start_idle_span();

  // If old-generation marking finishes during an idle span and immediate old-generation garbage is identified, we will rebuild
  // the free set.  If this happens, resume_idle_span() recomputes the threshold of memory that can be allocated before we need
  // to start the next GC.
  void resume_idle_span(size_t mutator_available);

  // Having observed a new allocation rate sample, add this to the acceleration history so that we can determine if allocation
  // rate is accelerating.
  void add_rate_to_acceleration_history(double timestamp, double rate);

  // Compute and return the current allocation rate, the current rate of acceleration, and the amount of memory that we expect
  // to consume if we start GC right now and gc takes predicted_cycle_time to complete.
  size_t accelerated_consumption(double& acceleration, double& current_rate, double predicted_cycle_time) const;

#ifdef FUTURE_SUPPORT_FOR_ALLOCATION_THROTTLE_DURING_GC
  void start_evac_span(size_t mutator_free);
  // probably also need start_mark_span(), state_update_span()
#endif

  void record_cycle_start();
  void record_success_concurrent();
  void record_success_degenerated();
  void record_success_full();

  virtual bool should_start_gc();

  virtual const char* name()     { return "Adaptive"; }
  virtual bool is_diagnostic()   { return false; }
  virtual bool is_experimental() { return false; }

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

  const static size_t GC_TIME_SAMPLE_SIZE;

  const static double MINIMUM_ALLOC_RATE_SAMPLE_INTERVAL;

  friend class ShenandoahAllocationRate;

  // Used to record the last trigger that signaled to start a GC.
  // This itself is used to decide whether or not to adjust the margin of
  // error for the average cycle time and allocation rate or the allocation
  // spike detection threshold.
  enum Trigger {
    SPIKE, RATE, OTHER
  };

  void adjust_last_trigger_parameters(double amount);
  void adjust_margin_of_error(double amount);
  void adjust_spike_threshold(double amount);

  // We recalculate the trigger threshold at the end of update refs and following the end of concurrent marking.  These two events
  // represents points at which the allocation pool was established (or replenished).  Trigger threshold is only meaningful during
  // times that GC is idle.  A similar approach might be used to throttle allocations between GC cycles and during GC cycles.
  void recalculate_trigger_threshold(size_t mutator_available);

  // Queries are issued only by should_start_gc(), approximately once every 3 ms in the default configuration.
  inline size_t allocated_since_last_query() {
    size_t allocated_words = _freeset->get_mutator_allocations();
    size_t result = allocated_words - _allocated_at_previous_query;
    _allocated_at_previous_query = allocated_words;
    return result;
  }

  // Returns number of words that can be allocated before we need to trigger next GC.
  inline size_t allocatable() const {
    size_t allocated_words = _freeset->get_mutator_allocations();
    return (allocated_words < _trigger_threshold)? _trigger_threshold - allocated_words: 0;
  }

  double get_most_recent_wake_time() const;
  double get_planned_sleep_interval() const;

protected:
  ShenandoahAllocationRate _allocation_rate;

  // Invocations of should_start_gc() happen approximately once per ms.  Approximately every third invocation  of should_start_gc()
  // queries the allocation rate.
  size_t _allocated_at_previous_query;

  double _time_of_previous_allocation_query;


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

  ShenandoahFreeSet* _freeset;
  bool _is_generational;
  ShenandoahRegulatorThread* _regulator_thread;
  ShenandoahController* _control_thread;

  size_t _previous_total_allocations;
  double _previous_allocation_timestamp;
  size_t _total_allocations_at_start_of_idle;
  size_t _trigger_threshold;

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

  void add_gc_time(double timestamp_at_start, double duration);
  void add_degenerated_gc_time(double timestamp_at_start, double duration);
  double predict_gc_time(double timestamp_at_start);

  // Keep track of SPIKE_ACCELERATION_SAMPLE_SIZE most recent spike allocation rate measurements. Note that it is
  // typical to experience a small spike following end of GC cycle, as mutator threads refresh their TLABs.  But
  // there is generally an abundance of memory at this time as well, so this will not generally trigger GC.
  uint _spike_acceleration_first_sample_index;
  uint _spike_acceleration_num_samples;
  double* const _spike_acceleration_rate_samples;
  double* const _spike_acceleration_rate_timestamps;

  size_t _most_recent_headroom_at_start_of_idle;

#ifdef KELVIN_DEPRECATE
  double _acceleration_goodness_ratio;
  size_t _consecutive_goodness;
#endif
  
  // A conservative minimum threshold of free space that we'll try to maintain when possible.
  // For example, we might trigger a concurrent gc if we are likely to drop below
  // this threshold, or we might consider this when dynamically resizing generations
  // in the generational case. Controlled by global flag ShenandoahMinFreeThreshold.
  size_t min_free_threshold();

};

#endif // SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHADAPTIVEHEURISTICS_HPP
