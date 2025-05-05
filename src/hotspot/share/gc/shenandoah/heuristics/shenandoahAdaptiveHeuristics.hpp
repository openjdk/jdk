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
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"
#include "memory/allocation.hpp"
#include "utilities/numberSeq.hpp"

class ShenandoahPhaseTimeEstimator {
 private:
  static const uint MaxSamples = 8;

  const char* _name;
  bool   _changed;
  bool   _changed_no_stdev;
  uint   _first_index;
  uint   _num_samples;
  double _sum_of_x;
  double _sum_of_y;
  double _sum_of_xx;
  double _sum_of_xy;
  double _most_recent_start;
  double _x_values[MaxSamples];
  double _y_values[MaxSamples];
  double _most_recent_prediction_x_value;
  double _most_recent_prediction;
  double _most_recent_prediction_x_value_no_stdev;
  double _most_recent_prediction_no_stdev;
  double _most_recent_bytes_allocated;

 public:
  explicit ShenandoahPhaseTimeEstimator(const char *name);

  void add_sample(double independent_variable, double dependent_variable);

  // Return conservative prediction of time required for next execution of this phase,
  //   which is Max(average_prediction, linear_prediction),
  //   average_prediction is average + std_dev, and
  //   linear_prediction is determined best-fit line + std_dev of this calculation
  double predict_at(double independent_value);

  double predict_at_without_stdev(double independent_value);

  void set_most_recent_start_time(double now) {
    _most_recent_start = now;
  }

  double get_most_recent_start_time() {
    return _most_recent_start;
  }

  void set_most_recent_bytes_allocated(size_t bytes) {
    _most_recent_bytes_allocated = bytes;
  }

  size_t get_most_recent_bytes_allocated() {
    return _most_recent_bytes_allocated;
  }
};

class ShenandoahAllocationRate : public CHeapObj<mtGC> {
 public:
  explicit ShenandoahAllocationRate();
  void allocation_counter_reset();

  double sample(size_t allocated);

  double upper_bound(double sds) const;
  double average_rate(double sds) const;
  bool is_spiking(double rate, double threshold) const;
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

  virtual void choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                     RegionData* data, size_t size,
                                                     size_t actual_free);

  virtual void record_cycle_start();
  void record_success_concurrent();
  void record_success_degenerated();
  void record_success_full();

  virtual bool should_start_gc();

  virtual const char* name()     { return "Adaptive"; }
  virtual bool is_diagnostic()   { return false; }
  virtual bool is_experimental() { return false; }

  virtual void record_phase_duration(ShenandoahMajorGCPhase p, double x, double duration) override;

  void record_mark_end(double now, size_t marked_words) override;
  // In non-generational mode, supply pip_words as zero
  void record_evac_end(double now, size_t evacuated_words, size_t pip_words) override;
  void record_update_end(double now, size_t updated_words) override;
  // In non-generational mode, supply pip_words as zero
  void record_final_roots_end(double now, size_t pip_words) override;

  uint should_surge_phase(ShenandoahMajorGCPhase phase, double now) override;

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

  friend class ShenandoahAllocationRate;

  void adjust_last_trigger_parameters(double amount);
  void adjust_margin_of_error(double amount);
  void adjust_spike_threshold(double amount);

protected:
  // Use this to estimate how much time will be required for future mark, evac, update, final-roots phases.
  ShenandoahPhaseTimeEstimator _phase_stats[ShenandoahMajorGCPhase::_num_phases] = {
    ShenandoahPhaseTimeEstimator("mark"),
    ShenandoahPhaseTimeEstimator("evac"),
    ShenandoahPhaseTimeEstimator("update"),
    ShenandoahPhaseTimeEstimator("final-roots") };

  // Used to record the last trigger that signaled to start a GC.
  // This itself is used to decide whether or not to adjust the margin of
  // error for the average cycle time and allocation rate or the allocation
  // spike detection threshold.
  enum Trigger {
    SPIKE, RATE, OTHER
  };

  ShenandoahAllocationRate _allocation_rate;

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

  // How many total words were evacuated in the most recently completed GC?
  size_t _words_most_recently_evacuated;

  // How many words do we expect to mark in the next GC?
  // (aka how many words did we evacuate from most recently completed GC?)
  size_t _anticipated_mark_words;

  // How many words do we expect to evacuate in the next GC?
  // (aka how many words did we evacuate from most recently completed GC?)
  size_t _anticipated_evac_words;

  // How many words do we expect to update in the next GC?
  size_t _anticipated_update_words;

  double predict_mark_time(size_t anticipated_marked_words) override;
  double predict_evac_time(size_t anticipated_evac_words, size_t anticipated_pip_words) override;
  double predict_update_time(size_t anticipated_update_words) override;
  double predict_final_roots_time(size_t pip_words) override;
  
  double predict_mark_time_nonconservative(size_t anticipated_marked_words) override;
  double predict_evac_time_nonconservative(size_t anticipated_evac_words, size_t anticipated_pip_words) override;
  double predict_update_time_nonconservative(size_t anticipated_update_words) override;
  double predict_final_roots_time_nonconservative(size_t pip_words) override;
  
  double predict_gc_time() override;
  double predict_gc_time_nonconservative() override;

  inline size_t get_anticipated_mark_words() {
    return _anticipated_mark_words;
  }

  inline void set_anticipated_evac_words(size_t words) {
#undef KELVIN_ANTICIPATION
#ifdef KELVIN_ANTICIPATION
    log_info(gc)("SAH::set_anticipated_evac_words(%zu)", words);
#endif
    _anticipated_evac_words = words;
  }

  inline size_t get_anticipated_evac_words() {
    return _anticipated_evac_words;
  }

  inline void set_anticipated_update_words(size_t words) {
#ifdef KELVIN_ANTICIPATION
    log_info(gc)("SAH::set_anticipated_update_words(%zu)", words);
#endif
    _anticipated_update_words =  words;
  }

  inline size_t get_anticipated_update_words() {
    return _anticipated_update_words;
  }

  // A conservative minimum threshold of free space that we'll try to maintain when possible.
  // For example, we might trigger a concurrent gc if we are likely to drop below
  // this threshold, or we might consider this when dynamically resizing generations
  // in the generational case. Controlled by global flag ShenandoahMinFreeThreshold.
  size_t min_free_threshold();

  inline void accept_trigger_with_type(Trigger trigger_type) {
    _last_trigger = trigger_type;
    ShenandoahHeuristics::accept_trigger();
  }
};

#endif // SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHADAPTIVEHEURISTICS_HPP
