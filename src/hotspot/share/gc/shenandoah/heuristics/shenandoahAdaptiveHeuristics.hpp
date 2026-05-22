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

// This general purpose linear estimator associates an independent double input value with a predicted double result value.
// It can be used to predict phase-times, memory sizes, and other values as a function of time.

// Rename to ShenandoahGCQuantityEstimator
class ShenandoahGCQuantityEstimator {
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

  // Return the dependent value for the most recently added sample
  double most_recent_sample();

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


/**
 * ShenandoahAllocationRate maintains a truncated history of recently sampled allocation rates for the purpose of providing
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

  // Return an estimate of the allocation rate.
  double average_rate(double sds) const;

  // Test whether rate significantly diverges from the computed average allocation rate.  If so, return true.
  // Otherwise, return false.  Significant divergence is recognized if (rate - _rate.avg()) / _rate.sd() > threshold.
  bool is_spiking(double rate, double threshold) const;

  double most_recent_instantaneous() {
    return _rate.last();
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

  virtual void initialize() override;

  virtual void post_initialize() override;

  virtual void adjust_penalty(intx step) override;

  // At the end of GC(N), we idle GC until necessary to start the next GC.  Compute the threshold of memory that can be allocated
  // before we need to start the next GC.
  void start_idle_span() override;

  // Having observed a new allocation rate sample, add this to the acceleration history so that we can determine if allocation
  // rate is accelerating.
  void add_rate_to_acceleration_history(double timestamp, double rate);

  // Compute and return the current allocation rate, the current rate of acceleration, and the amount of memory that we expect
  // to consume if we start GC right now and gc takes predicted_cycle_time to complete.
  size_t accelerated_consumption(double& acceleration, double& current_rate,
                                 double avg_rate_words_per_sec, double predicted_cycle_time) const;


  void choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                             RegionData* data, size_t size,
                                             size_t actual_free) override;

  void record_cycle_start() override;
  void record_degenerated() override;
  void record_success_full() override;

  bool should_start_gc() override;

  const char* name() override     { return "Adaptive"; }
  bool is_diagnostic() override   { return false; }
  bool is_experimental() override { return false; }

  virtual void record_phase_duration(ShenandoahMajorGCPhase p, double x, double duration) override;

#ifdef KELVIN_PLANNED_ENHANCEMENTS
  /*
  There are too many moving pieces in this "improvement".  My plan:

  1. First step, figure out what "primitive data" is needed to make effective triggering decisions in "all" circumstances.
  2. Implement and test (with logging details and asserts) all of the primitive-data collecting routines
  3. Implement the enhanced triggering decisions, and test on Extremem and on specjbb and on complete CI test suite.

  Progress:
  1. I have implemeneted the primitive data functions
  2. Next, I need to insert instrumentation into the primitive data functions
  3. Then, I will insert invocations of the primitive data functions
  4. Then I will modify the non-primitive functions to use the new primitive infrastructure


  Primitive functions:

  // feeds into predict_mark_time()
  void log_marked_young_words(double at_gc_start_time, size_t words_marked);

  // Returns the most recently logged value
  size_t marked_young_words()

  // Uses linear prediction to predict a future value
  size_t predict_marked_young_words(double at_gc_start_tiem);

  // The stable remset is the size of remset minus the transient burst size
  // feeds into predict_mark_time()
  void log_stable_remset_words(double at_gc_start_time, size_t remset_words);

  // Uses linear prediction to predict a future value
  size_t predict_stable_remset_words(double at_gc_start_time);

  // A remset burst size is the size by which the remset has grown due to the most recent cycle's old evacuations and promotions,
  // including promotions in place.  All of this data is identified as DIRTY.
  // feeds into predict_mark_time()
  void log_burst_remset_words(size_t remset_words);

  size_t most_recent_burst_remset_words();

  // Log the start of old GC marking
  void log_start_of_old_marking();

  // How many words in old need to be updated, worst case
  size_t total_old_update_words();

  // At tne end of evacuation, log words promoted by evacuation.  This is the growth of old used minus the live data promoted in
  // place.  This is more accurate that promotion reserve.
  void log_words_promoted_by_evacuation(size_t words_promoted);
  size_t most_recent_words_promoted_by_evacuation();

  // At end of choose_collection_set(), call this to identify how many regions will be promoted in place
  void log_words_promoted_in_place(size_t promote_in_place_words))
  void most_recent_words_promoted_in_place()

  // Words evacuated from young to young
  void log_young_evacuation_words(double at_gc_start_time, size_t young_words_evacuated)
  size_t predict_young_evacuation_words(double at_gc_start_time);

  // At the end of marking, we record how much memory was allocated during marking.  This represents an additional "floating
  // garbage" workload that needs to be processed during update refs.
  size_t log_words_allocated_during_mark((size_t words_allocated)
  size_t most_recent_words_allocated_during_mark()

  // At the end of old marking, we update marked words in old.  This includes only the marked words.  It does not include
  // words that were promoted during concurrent marking. This has the side effect of setting recently_promoted_words
  // to represent words promoted during concurrent old marking (words above TAMS within old-gen regions).
  void log_marked_old_words(double at_old_gc_start_time, size_t words_marked, size_t words_promoted_during_mark)

  // Returns most recently logged value of words_marked
  size_t most_recently_marked_old_words();

  // Returns prediction of how much live data will be marked in old during next old-gen GC
  size_t predict_marked_old_words(double at_old_gc_start_time);

  // At the start of each evacuation, we add to the total of recently_promoted_words.  Doing this at final mark allows
  // simpler invocations of predict_update_time().  Note that recently_promoted includes promotion_reserve plus planned
  // promotion in place.
  void add_to_words_promoted_since_start_of_old_gc(size_t recently_promoted);

  // At the end of evacuation, we decrease recently_promoted_words by the unexpended promotion reserve.
  void subtract_from_recently_promoted_words(size_t recently_not_promoted)

  size_t words_promoted_since_start_of_old_gc();

  Nonprimitive functions:

kelvin is here

  // processed_words is sum of marked_young_words plus stable_remset_words plus burst_remset_words
  void log_gc_mark_time(double at_gc_start_time, size_t marked_young_words, size_t stable_remset_words, size_t burst_remset_words,
                        double mark_time);

  // prediction based on sum of predict_marked_young_words() plus predict_stable_remset_words() plus predict_burst_remset_words()
  double predict_mark_time(double at_gc_start_time)
  
  // The linear prediction model predicts evacuation time as a function of total words evacuated.  We sum the first three
  // arguments to represent the independenet value.  evacuation_time is the dependent value.  Predictions include a stdev from
  // the best-fit line.  
  void log_gc_evac_time(size_t words_evacuated_to_young, size_t words_promoted_by_evacuation, size_t words_evacuated_to_old,
                        size_t words_promoted_in_place, double evacuation_time)

   // Use this to predict evacuation time component of an anticipated GC cycle (before we have completed marking)
   // The estimate is based on the total anticipated evacuation load, which is the sum of:
   //     old_generation->get_promoted_reserve() / ShenandoahPromoEvacWaste, plus
   //     old_generation->get_evacuation_reserve() / ShenandoahOldEvacWaste, plus
   //     predict_young_evacuation_words(at_gc_start_time)
   // Note that old_generation->get_promoted_reserve() includes promotions that may be implemented "in place".  Promotion
   // in place is easier than promotion by evacuation, even including the extra costs to be paid during update references
   // to coalesce and fill the pip region. Here, we predict that all promotion is performed by evacuation. Thus, this 
   // represents a conservative approxmation of evacuation time.
   double predict_evac_time_before_trigger(double at_gc_start_time)

   // Use this to predict evacuation time component after we have completed marking. We use this prediction to assess whether
   // it is necessary to surge GC workers. After marking, we know "exactly" how/ many words will be evacuated. This is the
   // sum of all live data for regions in the collection set. Our linear prediction model does not distinguish between words
   // targeting young and old generations (for simplicity, even though it appears that words evacuated to old generation are
   // more expensive than words evacuated to young generation). The linear prediction model does recognize that it is easier
   // to promote words in place than to promote by evacuation.
   double predict_evac_time_after_mark(size_t words_to_be_evacuated, words_to_be_promoted_in_place)

   // At GC trigger time, we call this with conservative parameter values:
   //               anticipated_live_young: predict_marked_young_words(at_gc_start_time) - 
   //                                       (old_generation->get_promoted_reserve() / ShenandaohPromoEvacWaste)
   //   anticipated_floating_garbage_young: current allocation_rate * predict_gc_mark_time (or accelerated consumption)
   //          anticipated_young_evacuated: predict_young_evacuation_words(at_gc_start_time) +
   //                                       old_generation->get_promoted_reserve() / ShenandoahPromoEvacWaste
   //  anticipated_words_promoted_in_place: 0
   //    if we are doing mixed_evacuations:
   //
   //                 anticipated_live_old: marked_old_words() + recently_promoted_words()
   //            anticipated_old_evacuated: old_generation->get_evacuation_reserve() / ShenandoahOldEvacWaste
   //         anticipated_old_remset_words: 0
   //
   //                            otherwise:
   //
   //                 anticipated_live_old: 0
   //            anticipated_old_evacuated: 0
   //         anticipated_old_remset_words: predict_stable_remset_words()
   //                                       + old_generation->get_promoted_reserve() / ShenandoahPromoEvacWaste
   //
   // After mark, we call this with more accurate parameter values:
   //               anticipated_live_young: marked_young_words()
   //                                       - (words_promoted_in_place() + old_generation->get_promoted_reserve())
   //                                       (Note: old_gen->get_promoted_reserve() has been adjusted by choose_cset)
   //   anticipated_floating_garbage_young: words_allocated_duing_mark()
   //          anticipated_young_evacuated: (cset->get_live_bytes_in_tenurable_bytes()
   //                                        + cset->get_live_bytes_in_untenurable_regions()) / HeapWordSize
   //  anticipated_words_promoted_in_place: words_promoted_in_place()
   //    if we are doing mixed_evacuations:
   //
   //                 anticipated_live_old: marked_old_words() + recently_promoted_words()
   //            anticipated_old_evacuated: cset->get_live_bytes_in_tenurable_regions()
   //                                       + cset->get_old_bytes_reserved_for_evacuation()
   //                                       (Note: this does not count opportunistic promotions from untenurable regions.
   //                                        At the end of evacuation, we know exactly how much was promoted by evacuation and
   //                                        we should use that: words_promoted_by_evacuation() as a replacement for
   //                                        cset->get_live_bytes_in_tenurable_regions().  Assert that the replacement is
   //                                        >= the original.)
   //         anticipated_old_remset_words: 0
   //
   //                            otherwise:
   //
   //                 anticipated_live_old: 0
   //            anticipated_old_evacuated: 0
   //         anticipated_old_remset_words: predict_stable_remset_words() + cset->get_live_bytes_in_tenurable_regions()
   //                                       (Note: this does not count opportunistic promotions from untenurable regions.
   //                                        At the end of evacuation, we know exactly how much was promoted by evacuation and
   //                                        we should use that: words_promoted_by_evacuation() as a replacement for
   //                                        cset->get_live_bytes_in_tenurable_regions().  Assert that the replacement is
   //                                        >= the original.)
   //
   // The linear prediction model is based on the total number of words to be evacuated.  This is computed as:
   //    (anticipated_live_young + anticipated_floating_garbage_young - anticipated_young_evacuated) +
   //    (anticipated_live_old + anticipated_words_promoted_in_place - anticipated_old_evacuated) +
   //    anticipated_old_remset_words
   // We assert that (anticipated_old_remset_words == 0) || (anticipated_old_evacuated == 0) 
   double predict_update_time(size_t anticipated_live_young,
                              size_t anticipated_floating_garbage_young, size_t anticipated_young_evacuated,
                              size_t anticipated_live_old,
                              size_t anticipated_old_evacuated,
                              size_t anticipated_words_promoted_in_place, size_t anticipated_old_remset_words)

  set_anticipated_remset_words() should be based on historical precedent.
  1. At the end of each GC cycle, remember how many words were moved into old by that GC cycle.  This includes
     promotion by evacuation, promotion in place, and mixed evacation.  All of these words are "dirty" in the card
     table.  This represents an increase over the "normal" remset scanning effort.  Call it extra_remset_word_count.
  2. At the end of each mark phase, remember how many remset words were processed
  3. Save the normal remset word count, which is the total remset words processed minus the extra words processed.
  4. At the end of each GC, we estimate the number of remset-words by adding the "normal remset word count" to the
     "moved-into-old-word-count. 

  set_anticipated_mark_words() should be based on historical precedent.
   (let's represent this as three independent values:
    get_anticipated_young_live_words(): Use linear prediction of recent behavior.
    get_anticipated_established_remset_words(): Use historical trends MAX2(average, linear prediction). 
    get_anticipated_newly_promoted_remset_words(): The remset scan is "extra big" following a burst of promotions in
       the previous cycle.)

  1. At the end of each mark phase, remember how many promotable words were marked and how many total words were marked
     and how many remset words were processed.  The sum of these values is the x-value that correlates with the y-value,
     in our linear prediction model of mark time.
  2. In non-gen mode, 0 words are promotable
  3. Remember the young_marked_words, which is total words - (promotable words + remset_words)
  4. Use linear prediction on "young marked words" to predict next young marked words.  For simplicity, the x-axis is gcid
     rather than time.
  5. At end of GC cycle, predict that the next GC cycle's mark_words is linear prediction of "young marked words" plus
     promo potential plus normal remset word count, plus extra_remset_word_count.  (Today's promo potential is
     "under-reported", but @kemperw is moment away from committing an improvement.)

  at should_start_gc(), predict mark time and bytes_allocated_during_mark() (use acceleration when appropriate)
  bytes_allocated_during_mark() represents the floating garbage that will have to be updated during update-refs.
  1. I've got a problem here.  I can't predict the GC time until I know update_words.  I can't know update_words
     until I know the time for marking.
  2. Estimate mark_phase based on anticipated_mark_words above()
  3. Use estimate of mark_phase time and "current" allocation rate to predict floating_garbage_words
  4. At the end of each evacuation phase, record total_evacuated_words, promoted_evacuated_words, old_evacuated_words.  save
     total_evacuated_words - (promoted_evacuated_words + old_evacuated_words) as typical_young_evacuated
  5. use historical estimate of typical_young_evacuateds + promotable_words + mixed_evac_words to estimate evac time.
  6. for young collections:
     a. Use historical estimate of young_marked_words + floating_garbage_words + typical_remset_words + promo_evac_words
        + promote_in_place words to calculate update time
  7. for mixed collections
     b. Same, but replace typical_remset_words with old_live_now - planned_old_evac


  anticipated_update_words:

  For Mutator regions, update_watermark represents the point above which it is not necessary to perform update.  We only
  update between bottom and udpate_watermark.

  For Collector and OldCollector regions, we need to update the entire region between bottom() and top().


  At should_start_gc(), we predict update words based on:
    a) We know if we are planning a mixed evac.  Assume mixed evac.  update words is:
         i. anticipated_floating_garbage_introduced_by_mark(): alloc_rate * anticipated_mark_time, plus
        ii. anticipated young live memory minus anticipated young collection set evacuation load
            From above, I have get_anticipated_young_live_words()
            How do I calculate anticipated_young_evacuation_words()?
              Calculate a trend of based on history of recent young GC
               predict_young_words_evacuated(double for_gc_start_time), based on lineaer prediction of words
               evacuated from young by previous GC cycles.  This prediction includes evacuation to survivor space.
               It excludes promotions by evacuation.
           anticipated_young_evacuation_words() is MIN2(young_evac_reserve / EvacWaste, predict_young_words_evacuated()





  After marking, we predict update words based on:


  1. At the end of marking, we record how many words were allocated during mark.  Call this float_garbage_words.
     We also count how many words were "marked" (below TAMS).  We're going to count this
     as the total_live_words.  We also count how much of the marked memory is going to be promoted.  Call this
     promotable_words.  Record live_words as total_live_words - promotable_words.
  2. At the end of marking, we update our estimate of update_words.  It is total_live_words + floating_garbage_words.
     Refine our estimate of remaining GC time based on updated awarness of update_words.



  */
#endif

  void record_mark_end(double now, size_t marked_words) override;
  // In non-generational mode, supply pip_words as zero
  void record_evac_end(double now, size_t evacuated_words, size_t pip_words) override;
  void record_update_end(double now, size_t updated_words) override;
  // In non-generational mode, supply pip_words as zero
  void record_final_roots_end(double now, size_t pip_words) override;

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

  friend class ShenandoahAllocationRate;

  void adjust_last_trigger_parameters(double amount);
  void adjust_margin_of_error(double amount);
  void adjust_spike_threshold(double amount);

protected:
  // Use this to estimate how much time will be required for future mark, evac, update, final-roots phases.

  ShenandoahGCQuantityEstimator _phase_stats[ShenandoahMajorGCPhase::_num_phases] = {
    // input: total words processed (remset size + live marked words; output: time to mark young
    ShenandoahGCQuantityEstimator("mark"),
    // input: total words processed (evac to young, evac to old, scaled promoted-in-place; output: time to evacuate
    ShenandoahGCQuantityEstimator("evac"),
    // input: total words processed (remsets, non-collected memory, etc); output: time to update
    ShenandoahGCQuantityEstimator("update"),
    // input: total words processed (words promoted in place); output: time to do final roots
    ShenandoahGCQuantityEstimator("final-roots") };

  // input: uptime at start of young gc, output: anticipated marked words
  ShenandoahGCQuantityEstimator _marked_young_words("marked_young_words");
  // input: uptime at start of old gc, output: anticipated marked old words
  ShenandoahGCQuantityEstimator _marked_old_words("marked_old_words");
  // input: uptime st start of young gc, output: ancicipated words in stable remembered set
  ShenandoahGCQuantityEstimator _stable_remset_words("stable_remset_wrods");
  // input: uptime at start of young gc, output: anticipated words evacuated from young to young
  ShenandoahGCQuantityEstimator _words_evacuated_from_young_to_young("evacuation_from_young_to_young");
  
  size_t _burst_of_remset_words;
  size_t _words_promoted_by_evacuation;
  size_t _words_promoted_in_place;
  size_t _words_allocated_during_mark;

  // "Live" memory in old is represented by
  //   _words_marked_by_old + _words_promoted_since_start_of_completed_old_gc;
  // Important to initialize to zero.
  size_t _words_marked_by_old = 0;

  // At the start of marking, we reset _words_promoted_during_old_gc to zero.
  //
  // At the end of each GC cycle, we increment both _words_promoted_during_old_gc and
  // _words_promoted_since_start_of_completed_old_gc by the promoted words. Updating _words_promoted_during_old_gc
  // is only useful if we are concurrently marking old. It is a don't care at other times.  Easier to always update
  // than to decide when the update is necessary.
  //
  // Each time we finish old marking:
  //  We overwrite _words_marked_by_old with updated value, and
  //  We copy _words_promoted_during_old_gc to _words_promoted_since_start_of_completed_old_gc
  size_t _words_promoted_during_old_gc;
  size_t _words_promoted_since_start_of_completed_old_gc;

  // Used to record the last trigger that signaled to start a GC.
  // This itself is used to decide whether or not to adjust the margin of
  // error for the average cycle time and allocation rate or the allocation
  // spike detection threshold.
  enum Trigger {
    SPIKE, RATE, OTHER
  };

#define KELVIN_PRIMITIVES

#ifndef KELVIN_REPLACE_DEPRECATED

  // We maintain a history of young words marked for the purpose of predicting future young-gc mark times.
  void log_marked_young_words(double at_gc_start_time, size_t words_marked) {
    _marked_young_words.add_sample(at_gc_start_time, (double) words_marked);
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("log_marked_young_words(at_gc_start_time: %.3f, words_marked: %zu)",
                 at_gc_start_time, words_marked);
#endif
  }

  // Predict how many young words will be marked in the next GC cycle as a function of when the next GC cycle will begin.
  // This assumes a linear model, returning max of average and linear prediction plus stdev.
  size_t predict_marked_young_words(double gc_start_time) {
    // Learning cycles generally prime prediction of marked young words
    size_t result = (siz_t) _marked_young_words.predict_at(gc_start_time);
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("predict_marked_young_words(gcc_start_time: %.3f) returns  %zu)",
                 gc_start_time, result);
#endif
    return result;
  }

  // The stable remset is the size of remset minus the transient burst size.  The transient burst in remset size corresponds
  // to the most recent GC cycle's evacuation into old.  All newly populated old-gen memory is assumed to be dirty.  We account
  // for ths transient burst of remset memory separately from the size of stable remset.  The stable remset is modeled by
  // linear prediction.  The stable remset_words is computed by subtracting most_recent_burst_remset_words() from the total
  // scanned remset words at the end marking.
  void log_stable_remset_words(double at_gc_start_time, size_t remset_words) {
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("log_stable_resmset_words(at_gc_start_time: %.3f, remset_words: %zu)",
                 at_gc_start_time, remset_words);
#endif
    _stable_remset_words.add_sample(at_gc_start_time, (double) remset_words);
  }

  // Uses linear prediction to estimate the size of the stable remset, returning max of average and linear prediction, plus
  // stdev.
  size_t predict_stable_remset_words(double gc_start_time) {
    size_t result = (size_t) _stable_remset_words.predict_at(gc_start_time);
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("predict_stable_remset_words(gcc_start_time: %.3f) returns  %zu",
                 gc_start_time, result);
#endif
    return result;
  }

  // A remset burst size is the size by which the remset has grown due to the most recent cycle's old evacuations and promotions,
  // including promotions in place.  All of this data is identified as DIRTY.  This quantity does not use a linear prediction
  // model.  Rather, it remembers only the amount of memory that was evacuated into old during the previous GC cycle.
  void log_burst_remset_words(size_t remset_words) {
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("log_burst_remset_words(remset_words: %zu)", remset_words);
#endif
    _burst_of_remset_words = remset_words;
  }

  // Return the value most recently stored by log_burst_remset_words()
  size_t most_recent_burst_remset_words() {
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("most_recent_burest_remset_words() returns %zu", _burst_of_reset_words);
#endif
    return _burst_of_remset_words;
  }

  // At tne end of evacuation, log words promoted by evacuation.
  // For regular (not mixed) cycles This is the growth of old used minus the live data promoted in place.
  // For mixed cycles, this is calculated using the following:
  //   expected_old_gen_used = original_old_gen_used - collection_set->get_old_garbage()
  //   total_promotion = new_old_gen_used - expected_old_gen_used
  //   promoted_by_evacuation = total_promotion - in_place_promotion
  // Note that mixed evacuation reduces used within old, but it does not affect live within old.
  void log_words_promoted_by_evacuation(size_t words_promoted) {
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("log_words_promoted_by_evacuation(words_promoted: %zu)", words_promoted);
#endif
    _words_promoted_by_evacuation = words_promoted;
    _words_promoted_during_old+gc += words_promoted;
    _words_promoted_since_start_of_completed_old_gc += words_promoted;
  }

  size_t most_recent_words_promoted_by_evacuation() {
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("most_recent_words_promoted_by_evacuation() returns %zu", _wors_promoted_by_evacuation);
#endif
    return _words_promoted_by_evacuation;
  }

  void log_words_promoted_in_place(size_t promote_in_place_words) {
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("log_words_promoted_in_place(promote_in_place_words: %zu", promote_in_place_words);
#endif
    _words_promoted_in_place = promote_in_place_words;
    _words_promoted_during_old+gc += promote_in_place_words;
    _words_promoted_since_start_of_completed_old_gc += promote_in_place_words;
  }

  size_t most_recent_words_promoted_in_place() {
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("most_recent_words_promoted_in_place() returns %zu", _words_promoted_in_place);
#endif
    return _words_promoted_in_place;
  }

  void log_young_evacuation_words(double at_gc_start_time, size_t young_words_evacuated) {
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("log_young_evacuation_words(at_gc_start_time: %.3f, young_words_evacuated: %zu)"
                 at_gc_start_time, young_words_evacuated);
#endif
    _words_evacuated_from_young_to_young.add_sample(at_gc_start_time, (double) young_words_evacuated);
  }

  size_t predict_young_evacuation_words(double gc_start_time) {
    size_t result = (size_t) _words_evacuated_from_young_to_young.predict_at(gc_start_time);
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("predict_young_evacuation_words(gc_start_time: %.3f) returns %zu", gc_start_time, result);
#endif
    return result;
  }
  
  void log_start_of_old_marking() {
    _words_promoted_during_old_gc = 0;
  }

  void log_words_allocated_during_mark(size_t words_allocated) {
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("log_words_alloaated_during_mark(%zu)", words_allocated);
#endif
    _words_allocated_during_mark = words_allocated;
  }

  size_t most_recent_words_allocated_during_mark() {
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("most_recent_words_allocated_during_mark() returns %zu", _words_allocated_during_mark);
#endif
    return _words_allocated_during_mark;
  }

  void log_marked_old_words(double at_old_gc_start_time, size_t words_marked, words_promoted_during_mark) {
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("log_marked_old_words(at_old_gc_start_time: %.3f, words_marked: %zu, words_promoted_during_mark: %zu)",
                 at_old_gc_start_time, words_marked, words_promoted_during_mark);
#endif
    _marked_old_words.add_sample(at_old_gc_start_time, (ddouble) words_marked);
    _words_promoted_since_start_of_completed_old_gc = words_promoted_during_old_gc;;
  }

  size_t most_recent_marked_old_words() {
    size_t result = (size_t) _marked_old_words.most_recent_sample();
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("most_recent_marked_old_words) returns %zu", result);
#endif
    return result;
  }

  size_t total_old_update_words() {
    return _words_marked_by_old + _words_promoted_since_start_of_completed_old_gc;
  }

  size_t predict_marked_old_words(double old_gc_start_time) {
    size_t result = (size_t) _marked_old_words.predict_at(old_gc_start_time);
    // Learning cycles may not have primted the prediction of old marked words.  If there is not history upon which
    // to predict old marked words, assume all of old is live.
    if (result == 0) {
      ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
      assert(heap->mode()->is_generational(), "Do not queury old marked words unless genertional");
      ShenandoahOldGeneration*  old_gen = heap->old_generation();
      size_t old_used = old_gen->used();
      result = old_used;
    }
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("predict_marked_old_words(%.3f) results %zu", old_gc_start_time, result);
#endif
    return result;
  }

  // At the start of each evacuation, we add to total recently_promoted_words the sum of promoted_reserve and live words in
  // planned promote-in-place regions.  Doing this at final mark allows simpler invocations of predict_update_time() during
  // evacuation.
  void add_to_words_promoted_since_start_of_old_gc(size_t planned_promotion) {
    _words_promoted_since_start_of_old_gc += planned_promotion;
    _words_promoted_during_old_gc += planned_promotion;
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("add_to_words_promoted_since_start_of_old_gc(planned_promotion: %zu), total: %zu",
                 planned_promotion, _words_promoted_since_start_of_old_gc);
#endif
  }

  // At the end of evacuation, we decrease recently_promoted_words by the unexpended promotion reserve.  We proably over-budgeted
  // at the start of evacuation because some of the promotion potential may reside in regions that were not selected for the
  // collection set and/or some of the planned promotions may fail.
  void subtract_from_recently_promoted_words(size_t recently_not_promoted) {
    assert(_words_promoted_since_start_of_old_gc > recently_not_promoted,
           "Expect _words_promoted_since_start_of_old_gc (%z8) > recently_not_promoted (%zu)",
           _words_promoted_since_start_of_old_gc, recently_not_promoted);
    assert(_words_promoted_during_old_gc > recently_not_promoted,
           "Expect _words_promoted_since_start_of_old_gc (%z8) > recently_not_promoted (%zu)",
           _words_promoted_since_start_of_old_gc, recently_not_promoted);
    _words_promoted_since_start_of_old_gc -= recently_not_promoted;
    _words_promoted_during_old_gc -= recently_not_promoted;
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("subtract_from_recently_promoted_words(recently_not_promoted: %zu), total: %zu",
                 recently_not_promoted, _words_promoted_since_start_of_old_gc);
#endif
  }

  // At the end of evacuation, we decrease recently_promoted_words by the unexpended promotion reserve.  We proably over-budgeted
  // at the start of evacuation because some of the promotion potential may reside in regions that were not selected for the
  // collection set and/or some of the planned promotions may fail.

  // It is also possible (in the current implementation) that we will promote more words than the anticipated budget.  This
  // may happen if there are opportunistic promotions from regions that do not reside within aged regions.
  void add_to_recently_promoted_words(size_t excess_recently_promoted) {
    _words_promoted_since_start_of_old_gc += excess_recently_promoted;
    _words_promoted_during_old_gc += excess_recently_promoted;
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("add_to_recently_promoted_words(excess_recently_promoted: %zu), total: %zu",
                 excess_recently_promoted, _words_promoted_since_start_of_old_gc);
#endif
  }


  size_t words_promoted_since_start_of_most_recently_completed_old_gc() {
#ifdef KELVIN_PRIMITIVES
    log_info(gc)("words_promoted_since_start_of_old_gc() returns %zu", _words_promoted_since_start_of_old_gc);
#endif
    return _words_promoted_since_start_of_old_gc;
  }

  void record_success_concurrent() override;

  // Returns number of words that can be allocated before we need to trigger next GC, given available in bytes.
  inline size_t allocatable(size_t available) const {
    return (available > _headroom_adjustment)? (available - _headroom_adjustment) / HeapWordSize: 0;
  }

  double margin_of_error_sd() const override {
    return _margin_of_error_sd;
  }

protected:
  ShenandoahAllocationRate _allocation_rate;

  // Invocations of should_start_gc() happen approximately once per ms.  Queries of allocation rate only happen if a
  // a certain amount of time has passed since the previous query.
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

#ifdef  KELVIN_DEPRECATE

  double predict_gc_time(size_t mark_words) override;

  inline size_t get_anticipated_mark_words() {
    return _anticipated_mark_words;
  }

  inline void set_anticipated_mark_words(size_t words) {
    _anticipated_mark_words = words;
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
#else

  // processed_words is sum of marked_young_words plus stable_remset_words plus burst_remset_words
  void log_gc_mark_time(double at_gc_start_time, size_t marked_young_words, size_t stable_remset_words, size_t burst_remset_words,
                        double mark_time) {
    log_marked_young_words(at_gc_start_time, marked_young_words);
    log_stable_remset_words(at_gc_start_time, stable_remset_words);
    log_burst_remset_words(at_gc_start_time, burst_remset_words);
    size_t total_processed_words = marked_young_words + stable_remset_words + burst_remset_words;
    record_phase_duration(ShenandoahMajorGCPhase::_mark, total_processed_words, mark_time);
  }

  double predict_mark_time(double gc_start_time) {
    size_t anticipated_mark_words = predict_marked_young_words(gc_start_time);
    size_t anticipated_stable_remset_words = predict_stable_remset_words(gc_start_time);
    size_t burst_remset_words = most_recent_burst_remset_words();
    size_t anticipated_mark_words = anticipated_mark_words + anticipated_stable_remset_words + burst_remset_words;
    return predict_mark_time(anticipated_mark_words);
  }

  // Adaptive heuristics do not normally trigger global GC. However, after we have accepted a GC trigger, we may
  // discover that we need to perform a global GC.  With this new knowledge, we recalculate anticipated GC time.
  // The new mark time will be larger than the trigger-specific calculation of mark time because we now know that
  // all of old generation must also be marked. Other GC phases will also be affected. Since we are likely to evacuate
  // both young and old regions, we will need to update all of old generation rather than only the remembered set.
  double predict_global_mark_time_after_trigger(double gc_start_time) {
    // For global marking, we do not use remembered set.  We mark everything starting from roots.
    size_t anticipated_mark_words = predict_marked_young_words(gc_start_time) + predict_marked_old_words(gc_start_time);
    return predict_mark_time(anticipated_mark_words);
  }

  // The linear prediction model predicts evacuation time as a function of total words evacuated.  We sum the first three
  // arguments to represent the independenet value.  evacuation_time is the dependent value.  Predictions include a stdev from
  // the best-fit line. This logs the time spent in evacuation in relation to the total evacuation workload.  It also logs
  // the individual evacuation quantities for purposes of predicting future values of these quantities.

  // Caller has to compute words_evacuated_to_old_from_old from cset->get_old_bytes_reserved_for_evacuation() / HeapWordSize;
  // growth_of_old is measured from change in old_generation->used()
  //   consult most_recent_words_promoted_in_place() to determine live_memory_contained_in_pip_regions
  //   promotion_by_evacuation = growth_of_old - (words_evacuated_to_old_from_old + live_memory_contained_in_pip_regions)
  // words_promoted_in_place = most_recent_words_promoted_in_place()

  voId log_gc_evac_time(double start_gc_time,
                        size_t words_evacuated_to_young, size_t words_promoted_by_evacuation, size_t words_evacuated_to_old,
                        size_t words_promoted_in_place, double evacuation_time) {
    log_young_evacuation_words(start_gc_time, words_evcuated_to_young);
    log_most_recent_words_promoted_by_evacuation(words_promoted_by_evacuation);
    log_most_recent_words_promoted_in_place(words_promoted_in_place);
    log_burst_remset_words(words_promoted_by_evacuation + words_evacuated_to_old + words_promoted_in_place);
    // evacuated words are 5x heavier than promoted-in-place words according to our linear prediction model.
    size_t total_workload =
      5 * (words_evacuated_to_young + words_promoted_by_evacuation + words_evacuated_to_old) + words_promoted_in_place;
    record_phase_duration(ShenandoahMajorGCPhase::_evac, total_workload, evac_time);
  }

   // Use this to predict evacuation time component of an anticipated GC cycle (before we have completed marking)
   // The estimate is based on the total anticipated evacuation load, which is the sum of:
   //     old_generation->get_promoted_reserve() / ShenandoahPromoEvacWaste, plus
   //     old_generation->get_evacuation_reserve() / ShenandoahOldEvacWaste, plus
   //     predict_young_evacuation_words(at_gc_start_time)
   // Note that old_generation->get_promoted_reserve() includes promotions that may be implemented "in place".  Promotion
   // in place is easier than promotion by evacuation, even including the extra costs to be paid during update references
   // to coalesce and fill the pip region. Here, we predict that all promotion is performed by evacuation. Thus, this 
   // represents a conservative approxmation of evacuation time.
  double predict_evac_time_before_trigger(double at_gc_start_time) {
    size_t anticipated_young_evac_words = predict_young_evacuation_words(at_gc_start_time);
    ShenandoahGenerationHeap* gen_heap = ShenandoahGenerationalHeap::heap();
    ShenandoahOldGeneration* old_gen = gen_heap->old_generation();
    size_t anticipated_promo_evac_words  (old_gen->get_promoted_reserve() * 100) / ShenandoahPromoEvacWaste;
    size_t anticipated_old_evac_words = (old_gen->get_evacuation_reserve() * 100) / ShenandoahOldEvacWaste;
    return predict_evac_time(anticipated_young_evac_words + anticipated_promo_evac_words + anticipated_old_evac_words, 0UL);
  }

   // Use this to predict evacuation time component after we have completed marking. We use this prediction to assess whether
   // it is necessary to surge GC workers. After marking, we know "exactly" how/ many words will be evacuated. This is the
   // sum of all live data for regions in the collection set. Our linear prediction model does not distinguish between words
   // targeting young and old generations (for simplicity, even though it appears that words evacuated to old generation are
   // more expensive than words evacuated to young generation). The linear prediction model does recognize that it is easier
   // to promote words in place than to promote by evacuation.
  double predict_evac_time_after_mark(size_t words_evacuated_to_young, size_t words_promoted_by_evacuation,
                                      size_t words_evacuated_to_old, size_t words_to_be_promoted_in_place) {
    return predict_evac_time(words_evacuated_to_young + words_promoted_by_evacuation + words_evacuated_to_old,
                             words_to_be_promoted_in_place);
  }

  void log_update_time(size_t total_remset_words, size_t young_updated_words, size_t old_updated_words, double update_time) {
    assert((burst_remset_words + stable_remset_words == 0) || (old_updated_words == 0),
           "Do not scan remembered set during update of mixed evacuation");
    assert(total_remset_words > most_recent_burst_remset_words(), "Sanity");
    size_t stable_remset_words = total_remset_words - most_recent_burst_remset_words();

    size_t total_update_words = burst_remset_words + stable_remset_words + young_updated_words + old_updated_words;
    record_phase_duration(ShenandoahMajorGCPhse::_update, total_update_words, update_time);
  }

   // The linear prediction model is based on the total number of words to be evacuated.  This is computed as:
   //    (anticipated_live_young + anticipated_floating_garbage_young - anticipated_young_evacuated) +
   //    (anticipated_live_old + anticipated_words_promoted_in_place - anticipated_old_evacuated) +
   //    anticipated_old_remset_words
   // We assert that (anticipated_old_remset_words == 0) || (anticipated_old_evacuated == 0) 
  double  predict_update_time_before_trigger(double at_gc_start_time, double predicted_mark_time) {
    size_t anticipated_young_evac = predict_young_evacuation_words(at_gc_start_time);
    size_t anticipated_young_marked = predict_marked_young_words(at_gc_start_time);

    double avg_alloc_rate = _allocation_rate.upper_bound(_margin_of_error_sd);
    size_t anticipated_allocations = avg_alloc_rate * predicted_mark_time;
    // This is a bit imprecise, as we don't know when the most recent instantaneous rate was "sampled". We want to make
    // conservative approximations here, so consider it anyway.
    double instantaneous_rate = _allocation_rate.most_recent_instantaneous();
    bool is_spiking = _allocation_rate.is_spiking(instantaneous_rate, _spike_threshold_sd);
    if (is_spiking) {
      size_t instantaneous_allocations = instantaneous_rate * predicted_mark_time;
      if (instantaneous_allocations > anticipated_allocations) {
        anticipated_allocations = instantaneous_allocations;
      }
    }
    double acceleration;
    double momentary_rate;
    size_t accelerated_allocations = accelerated_consumption(acceleration, momentary_rate,
                                                             avg_alloc_rate / HeapWordSize, predicted_mark_time);
    if (accelerated_allocations > anticipated_allocations) {
      anticipated_allocations = accelerated_allocations;
    }

    // The burst remset inherited from previous GC cycle will have been processed and eliminated by the time we update.
    size_t anticipated_steady_remset = predict_stable_remset_words(at_gc_start_time);

    ShenandoahGenerationHeap* gen_heap = ShenandoahGenerationalHeap::heap();
    ShenandoahOldGeneration* old_gen = gen_heap->old_generation();
    size_t anticipated_promotions = (old_gen->get_promoted_reserve() * 100) / ShenandoahOldEvacWaste;
    size_t anticipated_old_evac_words = (old_gen->get_evacuation_reserve() * 100) / ShenandoahOldEvacWaste;
    bool is_mixed = (anticipated_old_evac_words > 0)?
    if (is_mixed) {
      size_t old_live = most_recent_marked_old_words() + words_promoted_since_start_of_most_recently_completed_old_gc();
      size_t anticipated_old_update = (old_live - anticipated_old_evac) + anticipated_promotions;
      size_t total_update_words =
        (anticipated_young_marked - anticipated_young_evac) + anticipated_allocations + anticipated_old_update;

      return predict_update_time(total_update_words);
    } else {                    // Regular young GC is not mixed
      size_t total_update_words =
        (anticipated_young_marked - anticipated_young_evac) + anticipated_allocations + anticipated_steady_remset;
    }
  }

  // After trigger, we might know this is a global GC.  So adjust our estimage of duration.
  size_t predict_global_update_time_after_trigger(double at_gc_start_time) {
    // same as predict_update_time_before_trigger() with mixed evac
    size_t anticipated_young_evac = predict_young_evacuation_words(at_gc_start_time);
    size_t anticipated_young_marked = predict_marked_young_words(at_gc_start_time);

    double avg_alloc_rate = _allocation_rate.upper_bound(_margin_of_error_sd);
    size_t anticipated_allocations = avg_alloc_rate * predicted_mark_time;
    // This is a bit imprecise, as we don't know when the most recent instantaneous rate was "sampled". We want to make
    // conservative approximations here, so consider it anyway.
    double instantaneous_rate = _allocation_rate.most_recent_instantaneous();
    bool is_spiking = _allocation_rate.is_spiking(instantaneous_rate, _spike_threshold_sd);
    if (is_spiking) {
      size_t instantaneous_allocations = instantaneous_rate * predicted_mark_time;
      if (instantaneous_allocations > anticipated_allocations) {
        anticipated_allocations = instantaneous_allocations;
      }
    }
    double acceleration;
    double momentary_rate;
    size_t accelerated_allocations = accelerated_consumption(acceleration, momentary_rate,
                                                             avg_alloc_rate / HeapWordSize, predicted_mark_time);
    if (accelerated_allocations > anticipated_allocations) {
      anticipated_allocations = accelerated_allocations;
    }

    // The burst remset inherited from previous GC cycle will have been processed and eliminated by the time we update.
    size_t anticipated_steady_remset = predict_stable_remset_words(at_gc_start_time);

    ShenandoahGenerationHeap* gen_heap = ShenandoahGenerationalHeap::heap();
    ShenandoahOldGeneration* old_gen = gen_heap->old_generation();
    size_t anticipated_promotions = (old_gen->get_promoted_reserve() * 100) / ShenandoahOldEvacWaste;

    // Assume sharing of young and old evac reserves.  Anything not consumed by anticipated_young_evac will be
    //  evacuated from old, approximately.
    size_t anticipated_old_evac_words =
      (old_gen->get_evacuation_reserve() + young_gen->get_evacuation_reserve()
       - anticipated_young_evac * ShenandoahEvacWaste / 100) * 100) / ShenandoahOldEvacWaste;

    size_t old_live = most_recent_marked_old_words() + words_promoted_since_start_of_most_recently_completed_old_gc();
    size_t anticipated_old_update = (old_live - anticipated_old_evac) + anticipated_promotions;
    size_t total_update_words =
      (anticipated_young_marked - anticipated_young_evac) + anticipated_allocations + anticipated_old_update;

    return predict_update_time(total_update_words);
  }

  size_t predict_update_time_after_mark() {
    // now we know exactly how much old, young, allocated_during_mark, etc to be updated.
    size_t young_evac = collection_set->get_live_bytes_in_untenurable_regions() / HeapWordSize;
    size_t young_promo = collection_set->get_live_bytes_in_tenurable_regions() / HeapWordSize;
    size_t old_evac = collection_set->get_live_bytes_in_old_regions() / HeapWordSize;

    size_t young_live = ;

    // kelvin to do: what if we just started 

    size_t old_live = most_recent_marked_old_words() + words_promoted_since_start_of_most_recently_completed_old_gc();

  }



#endif


  ShenandoahFreeSet* _free_set;

  // This represents the time at which the allocation rate was most recently sampled for the purpose of detecting acceleration.
  double _previous_acceleration_sample_timestamp;
  size_t _total_allocations_at_start_of_idle;

  // bytes of headroom at which we should trigger GC
  size_t _headroom_adjustment;

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

  // In preparation for a span during which GC will be idle, compute the headroom adjustment that will be used to
  // detect when GC needs to trigger.
  void compute_headroom_adjustment() override;

  // Add a normal (young or bootstrap) GC time to the GC time history.
  void add_gc_time(double timestamp_at_start, double duration);

  // Add a degenerated (young or bootstrap) GC time to the GC time history.
  void add_degenerated_gc_time(double timestamp_at_start, double duration);

  // Predict the GC time of the next GC cycle.  This uses a linear prediction model if the next GC is anticipated to be
  // young or bootstrap without promotion.  If the next GC is anticipated to be mixed GC or is anticipated to have promotions,
  // the prediction is based on phase accounting.
  double predict_gc_time(double timestamp_at_start);

  // Keep track of SPIKE_ACCELERATION_SAMPLE_SIZE most recent spike allocation rate measurements. Note that it is
  // typical to experience a small spike following end of GC cycle, as mutator threads refresh their TLABs.  But
  // there is generally an abundance of memory at this time as well, so this will not generally trigger GC.
  uint _spike_acceleration_buffer_size;
  uint _spike_acceleration_first_sample_index;
  uint _spike_acceleration_num_samples;
  double* const _spike_acceleration_rate_samples; // holds rates in words/second
  double* const _spike_acceleration_rate_timestamps;

  // A conservative minimum threshold of free space that we'll try to maintain when possible.
  // For example, we might trigger a concurrent gc if we are likely to drop below
  // this threshold, or we might consider this when dynamically resizing generations
  // in the generational case. Controlled by global flag ShenandoahMinFreeThreshold.
  size_t min_free_threshold();

  void accept_trigger_with_type(Trigger trigger_type) {
    _last_trigger = trigger_type;
    ShenandoahHeuristics::accept_trigger();
  }

public:
  // Sample the allocation rate at GC trigger time if possible.  Return the number of allocated bytes that were
  // not accounted for in the sample.  This must be called before resetting bytes allocated since gc start.
  size_t force_alloc_rate_sample(size_t bytes_allocated) override {
    size_t unaccounted_bytes;
    _allocation_rate.force_sample(bytes_allocated, unaccounted_bytes);
    return unaccounted_bytes;
  }
};

#endif // SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHADAPTIVEHEURISTICS_HPP
