/*
 * Copyright (c) 2018, 2026, Red Hat, Inc. All rights reserved.
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
#include "gc/shenandoah/shenandoahAllocRate.hpp"
#include "gc/shenandoah/shenandoahCycleDuration.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
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

  void record_cycle_start() override;
  void record_success_concurrent() override;
  void record_degenerated() override;

  bool should_start_gc() override;

  const char* name() override     { return "Adaptive"; }
  bool is_diagnostic() override   { return false; }
  bool is_experimental() override { return false; }

  virtual void record_phase_duration(ShenandoahMajorGCPhase p, double x, double duration) override;

  void record_mark_end(double now, size_t marked_words) override;
  // In non-generational mode, supply pip_words as zero
  void record_evac_end(double now, size_t evacuated_words, size_t pip_words) override;
  void record_update_end(double now, size_t updated_words) override;
  // In non-generational mode, supply pip_words as zero
  void record_final_roots_end(double now, size_t pip_words) override;

protected:
  // Use this to estimate how much time will be required for future mark, evac, update, final-roots phases.
  ShenandoahPhaseTimeEstimator _phase_stats[ShenandoahMajorGCPhase::_num_phases] = {
    ShenandoahPhaseTimeEstimator("mark"),
    ShenandoahPhaseTimeEstimator("evac"),
    ShenandoahPhaseTimeEstimator("update"),
    ShenandoahPhaseTimeEstimator("final-roots") };

  // In preparation for a span during which GC will be idle, compute the headroom adjustment that will be used to
  // detect when GC needs to trigger.
  void compute_headroom_adjustment() override;

 private:
  void adjust_margin_of_error(double amount);

  // Returns number of bytes that can be allocated before we need to trigger next GC, given available in bytes.
  size_t allocatable(size_t available) const {
    return available > _headroom_adjustment ? available - _headroom_adjustment : 0;
  }

  double margin_of_error_sd() const override {
    return _margin_of_error_sd;
  }

protected:
  void adjust_penalty(intx step) override;
  void choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                             RegionData* data, size_t size,
                                             size_t actual_free) override;


  ShenandoahCycleDuration _cycles;

  // Used to record the last trigger that signaled to start a GC.
  // This itself is used to decide whether to adjust the margin of
  // error for the average cycle time.
  enum Trigger {
    RATE, OTHER
  };

  // The margin of error expressed in standard deviations to add to our
  // average cycle time and allocation rate. As this value increases we
  // tend to overestimate the rate at which mutators will deplete the
  // heap. In other words, erring on the side of caution will trigger more
  // concurrent GCs.
  double _margin_of_error_sd;

  // Remember which trigger is responsible for the last GC cycle. When the
  // outcome of the cycle is evaluated we will adjust the parameters for the
  // corresponding triggers.
  Trigger _last_trigger;

  // Keep track of the available memory at the end of a GC cycle. This
  // establishes what is 'normal' for the application and is used as a
  // source of feedback to adjust trigger parameters.
  TruncatedSeq _available;

  // How many total words were evacuated in the most recently completed GC?
  size_t _words_most_recently_evacuated;

  // How many words do we expect to mark in the next GC?  Setting this value to zero effectively disables phase-account
  // model prediction of GC time fot he next GC cycle.
  size_t _anticipated_mark_words;

  // How many words do we expect to evacuate in the next GC?  For an anticipated young GC, this is the same as what was
  // evacuated in the previous GC cycle.  For an anticipated mixed-evac GC, this includes the anticipated mixed-evac
  // workload.
  size_t _anticipated_evac_words;

  // How many words do we expect to update in the next GC?
  size_t _anticipated_update_words;

  double predict_mark_time(size_t anticipated_marked_words) override;
  double predict_evac_time(size_t anticipated_evac_words, size_t anticipated_pip_words) override;
  double predict_update_time(size_t anticipated_update_words) override;
  double predict_final_roots_time(size_t pip_words) override;

  double predict_gc_time(size_t mark_words) override;

  inline size_t get_anticipated_mark_words() {
    return _anticipated_mark_words;
  }

  inline void set_anticipated_evac_words(size_t words) {
    _anticipated_evac_words = words;
  }

  inline size_t get_anticipated_evac_words() {
    return _anticipated_evac_words;
  }

  inline void set_anticipated_update_words(size_t words) {
    _anticipated_update_words =  words;
  }

  inline size_t get_anticipated_update_words() {
    return _anticipated_update_words;
  }

  // bytes of headroom at which we should trigger GC
  size_t _headroom_adjustment;

  void add_degenerated_gc_time(double timestamp_at_start, double duration);

  // A conservative minimum threshold of free space that we'll try to maintain when possible.
  // For example, we might trigger a concurrent gc if we are likely to drop below
  // this threshold, or we might consider this when dynamically resizing generations
  // in the generational case. Controlled by global flag ShenandoahMinFreeThreshold.
  size_t min_free_threshold(size_t capacity) const;

  void accept_trigger_with_type(Trigger trigger_type) {
    _last_trigger = trigger_type;
    accept_trigger();
  }

  bool trigger_min_free_threshold(size_t available, size_t capacity);
  bool trigger_learning(size_t available, size_t capacity);
  bool trigger_average_allocation_rate(const ShenandoahAnticipatedConsumption& rate, size_t allocatable_bytes);
  bool trigger_accelerating_allocation_rate(const ShenandoahAnticipatedConsumption& rate, size_t allocatable_bytes);

private:
  void maybe_log_rate_trigger_parameters(const ShenandoahAnticipatedConsumption & consumption, size_t allocatable_bytes) const;
};

#endif // SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHADAPTIVEHEURISTICS_HPP
