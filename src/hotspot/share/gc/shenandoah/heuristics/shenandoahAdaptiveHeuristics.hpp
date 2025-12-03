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
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahRegulatorThread.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"
#include "memory/allocation.hpp"
#include "utilities/numberSeq.hpp"

/**
 * ShenanoahAllocationRate maintains a truncated history of recently sampled allocation rates for the purpose of providing
 * informed estimates of current and future allocation rates based on weighted averages and standard deviations of the
 * truncated history.  More recently sampled allocations are weighted more heavily than older samples when computing
 * averages and standard deviations.
 */
class ShenandoahAllocationRate : public CHeapObj<mtGC> {
 public:
  explicit ShenandoahAllocationRate();

  // Reset the _last_sample_value to zero, _last_sample_time to current time.
  void allocation_counter_reset();

  // Force an allocation rate sample to be taken, even if the time since last sample is not greater than
  // 1s/ShenandoahAdaptiveSampleFrequencyHz, except when current_time - _last_sample_time < MinSampleTime (2 ms).
  // The sampled allocation rate is computed from (allocated - _last_sample_value) / (current_time - _last_sample_time).
  // Return the newly computed rate if the sample is taken, zero if it is not an appropriate time to add a sample.
  // In the case that a new sample is not taken, overwrite unaccounted_bytes_allocated with bytes allocated since
  // the previous sample was taken (allocated - _last_sample_value).  Otherwise, overwrite unaccounted_bytes_allocated
  // with 0.
  double force_sample(size_t allocated, size_t &unaccounted_bytes_allocated);

  // Add an allocation rate sample if the time since last sample is greater than 1s/ShenandoahAdaptiveSampleFrequencyHz.
  // The sampled allocation rate is computed from (allocated - _last_sample_value) / (current_time - _last_sample_time).
  // Return the newly computed rate if the sample is taken, zero if it is not an appropriate time to add a sample.
  double sample(size_t allocated);

  // Return an estimate of the upper bound on allocation rate, with the upper bound computed as the weighted average
  // of recently sampled instantaneous allocation rates added to sds times the standard deviation computed for the
  // sequence of recently sampled average allocation rates.
  double upper_bound(double sds) const;

  // Test whether rate significantly diverges from the computed average allocation rate.  If so, return true.
  // Otherwise, return false.  Significant divergence is recognized if (rate - _rate.avg()) / _rate.sd() > threshold.
  bool is_spiking(double rate, double threshold) const;

  double interval() const {
    return _interval_sec;
  }
  double last_sample_time() const {
    return _last_sample_time;
  }

 private:

  // Return the instantaneous rate calculated from (allocated - _last_sample_value) / (time - _last_sample_time).
  // Return Sentinel value 0.0 if (time - _last_sample_time) == 0 or if (allocated <= _last_sample_value).
  double instantaneous_rate(double time, size_t allocated) const;

  // Time at which previous allocation rate sample was collected.
  double _last_sample_time;

  // Bytes allocated as of the time at which previous allocation rate sample was collected.
  size_t _last_sample_value;

  // The desired interval of time between consecutive samples of the allocation rate.
  double _interval_sec;

#undef KELVIN_VERBOSE
#ifdef KELVIN_VERBOSE
public:
#endif

  // Holds a sequence of the most recently sampled instantaneous allocation rates
  TruncatedSeq _rate;

  // Holds a sequence of the most recently computed weighted average of allocation rates, with each weighted average
  // computed immediately after an instantaneous rate was sampled
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

  virtual void post_initialize();

  virtual void choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                     RegionData* data, size_t size,
                                                     size_t actual_free) override;

  virtual void adjust_penalty(intx step);

  // At the end of GC(N), we idle GC until necessary to start the next GC.  Compute the threshold of memory that can be allocated
  // before we need to start the next GC.
  void start_idle_span() override;

  // If old-generation marking finishes during an idle span and immediate old-generation garbage is identified, we will rebuild
  // the free set.  If this happens, resume_idle_span() recomputes the threshold of memory that can be allocated before we need
  // to start the next GC.
  void resume_idle_span() override;

  // As we begin to do evacuation, adjust the trigger threshold to not account for headroom, as we are now free to allocate
  // everything that remains in the mutator set up until that is exhausted.  Our hope is that we finish GC before the
  // remaining mutator memory is fully depleted.
  void start_evac_span() override;

  // Having observed a new allocation rate sample, add this to the acceleration history so that we can determine if allocation
  // rate is accelerating.
  void add_rate_to_acceleration_history(double timestamp, double rate);

  // Compute and return the current allocation rate, the current rate of acceleration, and the amount of memory that we expect
  // to consume if we start GC right now and gc takes predicted_cycle_time to complete.
  size_t accelerated_consumption(double& acceleration, double& current_rate,
                                 double avg_rate_words_per_sec, double predicted_cycle_time) const;

  virtual void record_cycle_start() override;
  virtual void record_success_concurrent() override;
  virtual void record_success_degenerated() override;
  virtual void record_success_full() override;

  virtual bool should_start_gc() override;

  virtual const char* name() override     { return "Adaptive"; }
  virtual bool is_diagnostic() override   { return false; }
  virtual bool is_experimental() override { return false; }

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

  // _trigger_threshold, represented in words, is the amount of memory that we allow ourselves to allocate while concurrent
  // GC is running.  If anticipated consumption of mutator memory during GC (e.g. average alloc rate * average GC time)
  // exceeds _trigger_threshold, we need to start GC now.  Note that we intend NOT to allocate the headroom reserve,
  // so this is not included in the _trigger_threshold.
  void recalculate_trigger_threshold(size_t mutator_available);

  // Returns number of words that can be allocated before we need to trigger next GC.
  inline size_t allocatable() const {
    size_t allocated_words = _freeset->get_mutator_allocations_since_rebuild();
    size_t result = (allocated_words < _trigger_threshold)? _trigger_threshold - allocated_words: 0;
#undef KELVIN_ALLOCATABLE
#ifdef KELVIN_ALLOCATABLE
    log_info(gc)("allocatable returns %zu words from allocated %zu, trigger_threshold: %zu",
                 result, allocated_words, _trigger_threshold);
#endif
    return result;
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
  uint _spike_acceleration_buffer_size;
  uint _spike_acceleration_first_sample_index;
  uint _spike_acceleration_num_samples;
  double* const _spike_acceleration_rate_samples; // holds rates in words/second
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

  inline void accept_trigger_with_type(Trigger trigger_type) {
    _last_trigger = trigger_type;
    ShenandoahHeuristics::accept_trigger();
  }

public:
  // Sample the allocation rate at GC trigger time if possible.  Return the number of allocated bytes that were
  // not accounted for in the sample.  This must be called before resetting bytes allocated since gc start.
  virtual size_t force_alloc_rate_sample(size_t bytes_allocated) override {
    size_t unaccounted_bytes;
    _allocation_rate.force_sample(bytes_allocated, unaccounted_bytes);
    return unaccounted_bytes;
  }
};

#endif // SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHADAPTIVEHEURISTICS_HPP
