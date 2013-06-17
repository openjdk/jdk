/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_CONCURRENTMARKSWEEP_CMSADAPTIVESIZEPOLICY_HPP
#define SHARE_VM_GC_IMPLEMENTATION_CONCURRENTMARKSWEEP_CMSADAPTIVESIZEPOLICY_HPP

#include "gc_implementation/shared/adaptiveSizePolicy.hpp"
#include "runtime/timer.hpp"

// This class keeps statistical information and computes the
// size of the heap for the concurrent mark sweep collector.
//
// Cost for garbage collector include cost for
//   minor collection
//   concurrent collection
//      stop-the-world component
//      concurrent component
//   major compacting collection
//      uses decaying cost

// Forward decls
class elapsedTimer;

class CMSAdaptiveSizePolicy : public AdaptiveSizePolicy {
 friend class CMSGCAdaptivePolicyCounters;
 friend class CMSCollector;
 private:

  // Total number of processors available
  int _processor_count;
  // Number of processors used by the concurrent phases of GC
  // This number is assumed to be the same for all concurrent
  // phases.
  int _concurrent_processor_count;

  // Time that the mutators run exclusive of a particular
  // phase.  For example, the time the mutators run excluding
  // the time during which the cms collector runs concurrently
  // with the mutators.
  //   Between end of most recent cms reset and start of initial mark
                // This may be redundant
  double _latest_cms_reset_end_to_initial_mark_start_secs;
  //   Between end of the most recent initial mark and start of remark
  double _latest_cms_initial_mark_end_to_remark_start_secs;
  //   Between end of most recent collection and start of
  //   a concurrent collection
  double _latest_cms_collection_end_to_collection_start_secs;
  //   Times of the concurrent phases of the most recent
  //   concurrent collection
  double _latest_cms_concurrent_marking_time_secs;
  double _latest_cms_concurrent_precleaning_time_secs;
  double _latest_cms_concurrent_sweeping_time_secs;
  //   Between end of most recent STW MSC and start of next STW MSC
  double _latest_cms_msc_end_to_msc_start_time_secs;
  //   Between end of most recent MS and start of next MS
  //   This does not include any time spent during a concurrent
  // collection.
  double _latest_cms_ms_end_to_ms_start;
  //   Between start and end of the initial mark of the most recent
  // concurrent collection.
  double _latest_cms_initial_mark_start_to_end_time_secs;
  //   Between start and end of the remark phase of the most recent
  // concurrent collection
  double _latest_cms_remark_start_to_end_time_secs;
  //   Between start and end of the most recent MS STW marking phase
  double _latest_cms_ms_marking_start_to_end_time_secs;

  // Pause time timers
  static elapsedTimer _STW_timer;
  // Concurrent collection timer.  Used for total of all concurrent phases
  // during 1 collection cycle.
  static elapsedTimer _concurrent_timer;

  // When the size of the generation is changed, the size
  // of the change will rounded up or down (depending on the
  // type of change) by this value.
  size_t _generation_alignment;

  // If this variable is true, the size of the young generation
  // may be changed in order to reduce the pause(s) of the
  // collection of the tenured generation in order to meet the
  // pause time goal.  It is common to change the size of the
  // tenured generation in order to meet the pause time goal
  // for the tenured generation.  With the CMS collector for
  // the tenured generation, the size of the young generation
  // can have an significant affect on the pause times for collecting the
  // tenured generation.
  // This is a duplicate of a variable in PSAdaptiveSizePolicy.  It
  // is duplicated because it is not clear that it is general enough
  // to go into AdaptiveSizePolicy.
  int _change_young_gen_for_maj_pauses;

  // Variable that is set to true after a collection.
  bool _first_after_collection;

  // Fraction of collections that are of each type
  double concurrent_fraction() const;
  double STW_msc_fraction() const;
  double STW_ms_fraction() const;

  // This call cannot be put into the epilogue as long as some
  // of the counters can be set during concurrent phases.
  virtual void clear_generation_free_space_flags();

  void set_first_after_collection() { _first_after_collection = true; }

 protected:
  // Average of the sum of the concurrent times for
  // one collection in seconds.
  AdaptiveWeightedAverage* _avg_concurrent_time;
  // Average time between concurrent collections in seconds.
  AdaptiveWeightedAverage* _avg_concurrent_interval;
  // Average cost of the concurrent part of a collection
  // in seconds.
  AdaptiveWeightedAverage* _avg_concurrent_gc_cost;

  // Average of the initial pause of a concurrent collection in seconds.
  AdaptivePaddedAverage* _avg_initial_pause;
  // Average of the remark pause of a concurrent collection in seconds.
  AdaptivePaddedAverage* _avg_remark_pause;

  // Average of the stop-the-world (STW) (initial mark + remark)
  // times in seconds for concurrent collections.
  AdaptiveWeightedAverage* _avg_cms_STW_time;
  // Average of the STW collection cost for concurrent collections.
  AdaptiveWeightedAverage* _avg_cms_STW_gc_cost;

  // Average of the bytes free at the start of the sweep.
  AdaptiveWeightedAverage* _avg_cms_free_at_sweep;
  // Average of the bytes free at the end of the collection.
  AdaptiveWeightedAverage* _avg_cms_free;
  // Average of the bytes promoted between cms collections.
  AdaptiveWeightedAverage* _avg_cms_promo;

  // stop-the-world (STW) mark-sweep-compact
  // Average of the pause time in seconds for STW mark-sweep-compact
  // collections.
  AdaptiveWeightedAverage* _avg_msc_pause;
  // Average of the interval in seconds between STW mark-sweep-compact
  // collections.
  AdaptiveWeightedAverage* _avg_msc_interval;
  // Average of the collection costs for STW mark-sweep-compact
  // collections.
  AdaptiveWeightedAverage* _avg_msc_gc_cost;

  // Averages for mark-sweep collections.
  // The collection may have started as a background collection
  // that completes in a stop-the-world (STW) collection.
  // Average of the pause time in seconds for mark-sweep
  // collections.
  AdaptiveWeightedAverage* _avg_ms_pause;
  // Average of the interval in seconds between mark-sweep
  // collections.
  AdaptiveWeightedAverage* _avg_ms_interval;
  // Average of the collection costs for mark-sweep
  // collections.
  AdaptiveWeightedAverage* _avg_ms_gc_cost;

  // These variables contain a linear fit of
  // a generation size as the independent variable
  // and a pause time as the dependent variable.
  // For example _remark_pause_old_estimator
  // is a fit of the old generation size as the
  // independent variable and the remark pause
  // as the dependent variable.
  //   remark pause time vs. cms gen size
  LinearLeastSquareFit* _remark_pause_old_estimator;
  //   initial pause time vs. cms gen size
  LinearLeastSquareFit* _initial_pause_old_estimator;
  //   remark pause time vs. young gen size
  LinearLeastSquareFit* _remark_pause_young_estimator;
  //   initial pause time vs. young gen size
  LinearLeastSquareFit* _initial_pause_young_estimator;

  // Accessors
  int processor_count() const { return _processor_count; }
  int concurrent_processor_count() const { return _concurrent_processor_count; }

  AdaptiveWeightedAverage* avg_concurrent_time() const {
    return _avg_concurrent_time;
  }

  AdaptiveWeightedAverage* avg_concurrent_interval() const {
    return _avg_concurrent_interval;
  }

  AdaptiveWeightedAverage* avg_concurrent_gc_cost() const {
    return _avg_concurrent_gc_cost;
  }

  AdaptiveWeightedAverage* avg_cms_STW_time() const {
    return _avg_cms_STW_time;
  }

  AdaptiveWeightedAverage* avg_cms_STW_gc_cost() const {
    return _avg_cms_STW_gc_cost;
  }

  AdaptivePaddedAverage* avg_initial_pause() const {
    return _avg_initial_pause;
  }

  AdaptivePaddedAverage* avg_remark_pause() const {
    return _avg_remark_pause;
  }

  AdaptiveWeightedAverage* avg_cms_free() const {
    return _avg_cms_free;
  }

  AdaptiveWeightedAverage* avg_cms_free_at_sweep() const {
    return _avg_cms_free_at_sweep;
  }

  AdaptiveWeightedAverage* avg_msc_pause() const {
    return _avg_msc_pause;
  }

  AdaptiveWeightedAverage* avg_msc_interval() const {
    return _avg_msc_interval;
  }

  AdaptiveWeightedAverage* avg_msc_gc_cost() const {
    return _avg_msc_gc_cost;
  }

  AdaptiveWeightedAverage* avg_ms_pause() const {
    return _avg_ms_pause;
  }

  AdaptiveWeightedAverage* avg_ms_interval() const {
    return _avg_ms_interval;
  }

  AdaptiveWeightedAverage* avg_ms_gc_cost() const {
    return _avg_ms_gc_cost;
  }

  LinearLeastSquareFit* remark_pause_old_estimator() {
    return _remark_pause_old_estimator;
  }
  LinearLeastSquareFit* initial_pause_old_estimator() {
    return _initial_pause_old_estimator;
  }
  LinearLeastSquareFit* remark_pause_young_estimator() {
    return _remark_pause_young_estimator;
  }
  LinearLeastSquareFit* initial_pause_young_estimator() {
    return _initial_pause_young_estimator;
  }

  // These *slope() methods return the slope
  // m for the linear fit of an independent
  // variable vs. a dependent variable.  For
  // example
  //  remark_pause = m * old_generation_size + c
  // These may be used to determine if an
  // adjustment should be made to achieve a goal.
  // For example, if remark_pause_old_slope() is
  // positive, a reduction of the old generation
  // size has on average resulted in the reduction
  // of the remark pause.
  float remark_pause_old_slope() {
    return _remark_pause_old_estimator->slope();
  }

  float initial_pause_old_slope() {
    return _initial_pause_old_estimator->slope();
  }

  float remark_pause_young_slope() {
    return _remark_pause_young_estimator->slope();
  }

  float initial_pause_young_slope() {
    return _initial_pause_young_estimator->slope();
  }

  // Update estimators
  void update_minor_pause_old_estimator(double minor_pause_in_ms);

  // Fraction of processors used by the concurrent phases.
  double concurrent_processor_fraction();

  // Returns the total times for the concurrent part of the
  // latest collection in seconds.
  double concurrent_collection_time();

  // Return the total times for the concurrent part of the
  // latest collection in seconds where the times of the various
  // concurrent phases are scaled by the processor fraction used
  // during the phase.
  double scaled_concurrent_collection_time();

  // Dimensionless concurrent GC cost for all the concurrent phases.
  double concurrent_collection_cost(double interval_in_seconds);

  // Dimensionless GC cost
  double collection_cost(double pause_in_seconds, double interval_in_seconds);

  virtual GCPolicyKind kind() const { return _gc_cms_adaptive_size_policy; }

  virtual double time_since_major_gc() const;

  // This returns the maximum average for the concurrent, ms, and
  // msc collections.  This is meant to be used for the calculation
  // of the decayed major gc cost and is not in general the
  // average of all the different types of major collections.
  virtual double major_gc_interval_average_for_decay() const;

 public:
  CMSAdaptiveSizePolicy(size_t init_eden_size,
                        size_t init_promo_size,
                        size_t init_survivor_size,
                        double max_gc_minor_pause_sec,
                        double max_gc_pause_sec,
                        uint gc_cost_ratio);

  // The timers for the stop-the-world phases measure a total
  // stop-the-world time.  The timer is started and stopped
  // for each phase but is only reset after the final checkpoint.
  void checkpoint_roots_initial_begin();
  void checkpoint_roots_initial_end(GCCause::Cause gc_cause);
  void checkpoint_roots_final_begin();
  void checkpoint_roots_final_end(GCCause::Cause gc_cause);

  // Methods for gathering information about the
  // concurrent marking phase of the collection.
  // Records the mutator times and
  // resets the concurrent timer.
  void concurrent_marking_begin();
  // Resets concurrent phase timer in the begin methods and
  // saves the time for a phase in the end methods.
  void concurrent_marking_end();
  void concurrent_sweeping_begin();
  void concurrent_sweeping_end();
  // Similar to the above (e.g., concurrent_marking_end()) and
  // is used for both the precleaning an abortable precleaing
  // phases.
  void concurrent_precleaning_begin();
  void concurrent_precleaning_end();
  // Stops the concurrent phases time.  Gathers
  // information and resets the timer.
  void concurrent_phases_end(GCCause::Cause gc_cause,
                              size_t cur_eden,
                              size_t cur_promo);

  // Methods for gather information about STW Mark-Sweep-Compact
  void msc_collection_begin();
  void msc_collection_end(GCCause::Cause gc_cause);

  // Methods for gather information about Mark-Sweep done
  // in the foreground.
  void ms_collection_begin();
  void ms_collection_end(GCCause::Cause gc_cause);

  // Cost for a mark-sweep tenured gen collection done in the foreground
  double ms_gc_cost() const {
    return MAX2(0.0F, _avg_ms_gc_cost->average());
  }

  // Cost of collecting the tenured generation.  Includes
  // concurrent collection and STW collection costs
  double cms_gc_cost() const;

  // Cost of STW mark-sweep-compact tenured gen collection.
  double msc_gc_cost() const {
    return MAX2(0.0F, _avg_msc_gc_cost->average());
  }

  //
  double compacting_gc_cost() const {
    double result = MIN2(1.0, minor_gc_cost() + msc_gc_cost());
    assert(result >= 0.0, "Both minor and major costs are non-negative");
    return result;
  }

   // Restarts the concurrent phases timer.
   void concurrent_phases_resume();

   // Time beginning and end of the marking phase for
   // a synchronous MS collection.  A MS collection
   // that finishes in the foreground can have started
   // in the background.  These methods capture the
   // completion of the marking (after the initial
   // marking) that is done in the foreground.
   void ms_collection_marking_begin();
   void ms_collection_marking_end(GCCause::Cause gc_cause);

   static elapsedTimer* concurrent_timer_ptr() {
     return &_concurrent_timer;
   }

  AdaptiveWeightedAverage* avg_cms_promo() const {
    return _avg_cms_promo;
  }

  int change_young_gen_for_maj_pauses() {
    return _change_young_gen_for_maj_pauses;
  }
  void set_change_young_gen_for_maj_pauses(int v) {
    _change_young_gen_for_maj_pauses = v;
  }

  void clear_internal_time_intervals();


  // Either calculated_promo_size_in_bytes() or promo_size()
  // should be deleted.
  size_t promo_size() { return _promo_size; }
  void set_promo_size(size_t v) { _promo_size = v; }

  // Cost of GC for all types of collections.
  virtual double gc_cost() const;

  size_t generation_alignment() { return _generation_alignment; }

  virtual void compute_eden_space_size(size_t cur_eden,
                                       size_t max_eden_size);
  // Calculates new survivor space size;  returns a new tenuring threshold
  // value. Stores new survivor size in _survivor_size.
  virtual uint compute_survivor_space_size_and_threshold(
                                                bool   is_survivor_overflow,
                                                uint   tenuring_threshold,
                                                size_t survivor_limit);

  virtual void compute_tenured_generation_free_space(size_t cur_tenured_free,
                                           size_t max_tenured_available,
                                           size_t cur_eden);

  size_t eden_decrement_aligned_down(size_t cur_eden);
  size_t eden_increment_aligned_up(size_t cur_eden);

  size_t adjust_eden_for_pause_time(size_t cur_eden);
  size_t adjust_eden_for_throughput(size_t cur_eden);
  size_t adjust_eden_for_footprint(size_t cur_eden);

  size_t promo_decrement_aligned_down(size_t cur_promo);
  size_t promo_increment_aligned_up(size_t cur_promo);

  size_t adjust_promo_for_pause_time(size_t cur_promo);
  size_t adjust_promo_for_throughput(size_t cur_promo);
  size_t adjust_promo_for_footprint(size_t cur_promo, size_t cur_eden);

  // Scale down the input size by the ratio of the cost to collect the
  // generation to the total GC cost.
  size_t scale_by_gen_gc_cost(size_t base_change, double gen_gc_cost);

  // Return the value and clear it.
  bool get_and_clear_first_after_collection();

  // Printing support
  virtual bool print_adaptive_size_policy_on(outputStream* st) const;
};

#endif // SHARE_VM_GC_IMPLEMENTATION_CONCURRENTMARKSWEEP_CMSADAPTIVESIZEPOLICY_HPP
