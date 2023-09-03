/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1POLICY_HPP
#define SHARE_GC_G1_G1POLICY_HPP

#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1ConcurrentStartToMixedTimeTracker.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1HeapRegionAttr.hpp"
#include "gc/g1/g1MMUTracker.hpp"
#include "gc/g1/g1OldGenAllocationTracker.hpp"
#include "gc/g1/g1RemSetTrackingPolicy.hpp"
#include "gc/g1/g1Predictions.hpp"
#include "gc/g1/g1YoungGenSizer.hpp"
#include "gc/shared/gcCause.hpp"
#include "runtime/atomic.hpp"
#include "utilities/pair.hpp"
#include "utilities/ticks.hpp"

// A G1Policy makes policy decisions that determine the
// characteristics of the collector.  Examples include:
//   * choice of collection set.
//   * when to collect.

class HeapRegion;
class G1CollectionSet;
class G1CollectionCandidateList;
class G1CollectionSetCandidates;
class G1CollectionSetChooser;
class G1CollectionCandidateRegionList;
class G1IHOPControl;
class G1Analytics;
class G1SurvivorRegions;
class GCPolicyCounters;
class STWGCTimer;

class G1Policy: public CHeapObj<mtGC> {
 private:

  static G1IHOPControl* create_ihop_control(const G1OldGenAllocationTracker* old_gen_alloc_tracker,
                                            const G1Predictions* predictor);
  // Update the IHOP control with necessary statistics.
  void update_ihop_prediction(double mutator_time_s,
                              bool this_gc_was_young_only);
  void report_ihop_statistics();

  G1Predictions _predictor;
  G1Analytics* _analytics;
  G1RemSetTrackingPolicy _remset_tracker;
  G1MMUTracker* _mmu_tracker;

  // Tracking the allocation in the old generation between
  // two GCs.
  G1OldGenAllocationTracker _old_gen_alloc_tracker;
  G1IHOPControl* _ihop_control;

  GCPolicyCounters* _policy_counters;

  double _full_collection_start_sec;

  // Desired young gen length without taking actually available free regions into
  // account.
  volatile uint _young_list_desired_length;
  // Actual target length given available free memory.
  volatile uint _young_list_target_length;
  // The max number of regions we can extend the eden by while the GC
  // locker is active. This should be >= _young_list_target_length;
  volatile uint _young_list_max_length;

  // The survivor rate groups below must be initialized after the predictor because they
  // indirectly use it through the "this" object passed to their constructor.
  G1SurvRateGroup* _eden_surv_rate_group;
  G1SurvRateGroup* _survivor_surv_rate_group;

  double _reserve_factor;
  // This will be set when the heap is expanded
  // for the first time during initialization.
  uint   _reserve_regions;

  G1YoungGenSizer _young_gen_sizer;

  uint _free_regions_at_end_of_collection;

  size_t _rs_length;

  size_t _pending_cards_at_gc_start;

  G1ConcurrentStartToMixedTimeTracker _concurrent_start_to_mixed;

  bool should_update_surv_rate_group_predictors() {
    return collector_state()->in_young_only_phase() && !collector_state()->mark_or_rebuild_in_progress();
  }

  double logged_cards_processing_time() const;
public:
  const G1Predictions& predictor() const { return _predictor; }
  const G1Analytics* analytics()   const { return const_cast<const G1Analytics*>(_analytics); }

  G1RemSetTrackingPolicy* remset_tracker() { return &_remset_tracker; }

  G1OldGenAllocationTracker* old_gen_alloc_tracker() { return &_old_gen_alloc_tracker; }

  void set_region_eden(HeapRegion* hr) {
    hr->set_eden();
    hr->install_surv_rate_group(_eden_surv_rate_group);
  }

  void set_region_survivor(HeapRegion* hr) {
    assert(hr->is_survivor(), "pre-condition");
    hr->install_surv_rate_group(_survivor_surv_rate_group);
  }

  void record_rs_length(size_t rs_length) {
    _rs_length = rs_length;
  }

  double predict_base_time_ms(size_t pending_cards) const;

private:
  // Base time contains handling remembered sets and constant other time of the
  // whole young gen, refinement buffers, and copying survivors.
  // Basically everything but copying eden regions.
  double predict_base_time_ms(size_t pending_cards, size_t rs_length, size_t code_root_length) const;

  // Copy time for a region is copying live data.
  double predict_region_copy_time_ms(HeapRegion* hr, bool for_young_only_phase) const;
  // Merge-scan time for a region is handling card-based remembered sets of that region
  // (as a single unit).
  double predict_region_merge_scan_time(HeapRegion* hr, bool for_young_only_phase) const;
  // Code root scan time prediction for the given region.
  double predict_region_code_root_scan_time(HeapRegion* hr, bool for_young_only_phase) const;
  // Non-copy time for a region is handling remembered sets and other time.
  double predict_region_non_copy_time_ms(HeapRegion* hr, bool for_young_only_phase) const;

public:

  // Predict other time for count young regions.
  double predict_young_region_other_time_ms(uint count) const;
  // Predict copying live data time for count eden regions. Return the predict bytes if
  // bytes_to_copy is non-null.
  double predict_eden_copy_time_ms(uint count, size_t* bytes_to_copy = nullptr) const;
  // Total time for a region is handling remembered sets (as a single unit), copying its live data
  // and other time.
  double predict_region_total_time_ms(HeapRegion* hr, bool for_young_only_phase) const;

  void cset_regions_freed() {
    bool update = should_update_surv_rate_group_predictors();

    _eden_surv_rate_group->all_surviving_words_recorded(predictor(), update);
    _survivor_surv_rate_group->all_surviving_words_recorded(predictor(), update);
  }

  G1MMUTracker* mmu_tracker() {
    return _mmu_tracker;
  }

  const G1MMUTracker* mmu_tracker() const {
    return _mmu_tracker;
  }

  double max_pause_time_ms() const {
    return _mmu_tracker->max_gc_time() * 1000.0;
  }

private:
  G1CollectionSet* _collection_set;
  G1CollectionSetCandidates* candidates() const;

  double average_time_ms(G1GCPhaseTimes::GCParPhases phase) const;
  double other_time_ms(double pause_time_ms) const;

  double young_other_time_ms() const;
  double non_young_other_time_ms() const;
  double constant_other_time_ms(double pause_time_ms) const;

  G1CollectionSetChooser* cset_chooser() const;

  // Stash a pointer to the g1 heap.
  G1CollectedHeap* _g1h;

  STWGCTimer*     _phase_times_timer;
  // Lazily initialized
  mutable G1GCPhaseTimes* _phase_times;

  // This set of variables tracks the collector efficiency, in order to
  // determine whether we should initiate a new marking.
  double _mark_remark_start_sec;
  double _mark_cleanup_start_sec;

  // Updates the internal young gen maximum and target and desired lengths.
  // If no parameters are passed, predict pending cards, card set remset length and
  // code root remset length using the prediction model.
  void update_young_length_bounds();
  void update_young_length_bounds(size_t pending_cards, size_t rs_length, size_t code_root_rs_length);

  // Calculate and return the minimum desired eden length based on the MMU target.
  uint calculate_desired_eden_length_by_mmu() const;

  // Calculate the desired eden length meeting the pause time goal.
  // The parameters are: rs_length represents the prediction of how large the
  // young RSet lengths will be, min_eden_length and max_eden_length are the bounds
  // (inclusive) within eden can grow.
  uint calculate_desired_eden_length_by_pause(double base_time_ms,
                                              uint min_eden_length,
                                              uint max_eden_length) const;

  // Calculate the desired eden length that can fit into the pause time
  // goal before young only gcs.
  uint calculate_desired_eden_length_before_young_only(double base_time_ms,
                                                       uint min_eden_length,
                                                       uint max_eden_length) const;

  // Calculates the desired eden length before mixed gc so that after adding the
  // minimum amount of old gen regions from the collection set, the eden fits into
  // the pause time goal.
  uint calculate_desired_eden_length_before_mixed(double base_time_ms,
                                                  uint min_eden_length,
                                                  uint max_eden_length) const;

  // Calculate desired young length based on current situation without taking actually
  // available free regions into account.
  uint calculate_young_desired_length(size_t pending_cards, size_t rs_length, size_t code_root_rs_length) const;
  // Limit the given desired young length to available free regions.
  uint calculate_young_target_length(uint desired_young_length) const;
  // The GCLocker might cause us to need more regions than the target. Calculate
  // the maximum number of regions to use in that case.
  uint calculate_young_max_length(uint target_young_length) const;

  size_t predict_bytes_to_copy(HeapRegion* hr) const;
  double predict_survivor_regions_evac_time() const;
  double predict_retained_regions_evac_time() const;

  // Check whether a given young length (young_length) fits into the
  // given target pause time and whether the prediction for the amount
  // of objects to be copied for the given length will fit into the
  // given free space (expressed by base_free_regions).  It is used by
  // calculate_young_list_target_length().
  bool predict_will_fit(uint young_length, double base_time_ms,
                        uint base_free_regions, double target_pause_time_ms) const;

public:
  size_t pending_cards_at_gc_start() const { return _pending_cards_at_gc_start; }

  // The minimum number of retained regions we will add to the CSet during a young GC.
  uint min_retained_old_cset_length() const;
  // Calculate the minimum number of old regions we'll add to the CSet
  // during a single mixed GC given the initial number of regions selected during
  // marking.
  uint calc_min_old_cset_length(uint num_candidate_regions) const;

  // Calculate the maximum number of old regions we'll add to the CSet
  // during a mixed GC.
  uint calc_max_old_cset_length() const;

private:
  void abandon_collection_set_candidates();
  // Sets up marking if proper conditions are met.
  void maybe_start_marking();
  // Manage time-to-mixed tracking.
  void update_time_to_mixed_tracking(G1GCPauseType gc_type, double start, double end);
  // Record the given STW pause with the given start and end times (in s).
  void record_pause(G1GCPauseType gc_type,
                    double start,
                    double end,
                    bool evacuation_failure = false);

  void update_gc_pause_time_ratios(G1GCPauseType gc_type, double start_sec, double end_sec);

  // Indicate that we aborted marking before doing any mixed GCs.
  void abort_time_to_mixed_tracking();

public:

  G1Policy(STWGCTimer* gc_timer);

  virtual ~G1Policy();

  G1CollectorState* collector_state() const;

  G1GCPhaseTimes* phase_times() const;

  // Check the current value of the young list RSet length and
  // compare it against the last prediction. If the current value is
  // higher, recalculate the young list target length prediction.
  void revise_young_list_target_length(size_t rs_length, size_t code_root_rs_length);

  // This should be called after the heap is resized.
  void record_new_heap_size(uint new_number_of_regions);

  void init(G1CollectedHeap* g1h, G1CollectionSet* collection_set);

  // Record the start and end of the young gc pause.
  void record_young_gc_pause_start();
  void record_young_gc_pause_end(bool evacuation_failed);

  bool need_to_start_conc_mark(const char* source, size_t alloc_word_size = 0);

  bool concurrent_operation_is_full_mark(const char* msg = nullptr);

  bool about_to_start_mixed_phase() const;

  // Record the start and end of the actual collection part of the evacuation pause.
  void record_young_collection_start();
  void record_young_collection_end(bool concurrent_operation_is_full_mark, bool evacuation_failure);

  // Record the start and end of a full collection.
  void record_full_collection_start();
  void record_full_collection_end();

  // Must currently be called while the world is stopped.
  void record_concurrent_mark_init_end();

  // Record start and end of remark.
  void record_concurrent_mark_remark_start();
  void record_concurrent_mark_remark_end();

  // Record start, end, and completion of cleanup.
  void record_concurrent_mark_cleanup_start();
  void record_concurrent_mark_cleanup_end(bool has_rebuilt_remembered_sets);

  bool next_gc_should_be_mixed() const;

  // Amount of allowed waste in bytes in the collection set.
  size_t allowed_waste_in_collection_set() const;
  // Calculate and fill in the initial and optional old gen candidate regions from
  // the given candidate list and the remaining time.
  // Returns the remaining time.
  double select_candidates_from_marking(G1CollectionCandidateList* marking_list,
                                        double time_remaining_ms,
                                        G1CollectionCandidateRegionList* initial_old_regions,
                                        G1CollectionCandidateRegionList* optional_old_regions);

  void select_candidates_from_retained(G1CollectionCandidateList* retained_list,
                                       double time_remaining_ms,
                                       G1CollectionCandidateRegionList* initial_old_regions,
                                       G1CollectionCandidateRegionList* optional_old_regions);

  // Calculate the number of optional regions from the given collection set candidates,
  // the remaining time and the maximum number of these regions and return the number
  // of actually selected regions in num_optional_regions.
  void calculate_optional_collection_set_regions(G1CollectionCandidateRegionList* optional_old_regions,
                                                 double time_remaining_ms,
                                                 G1CollectionCandidateRegionList* selected);

private:

  // Predict the number of bytes of surviving objects from survivor and old
  // regions and update the associated members.
  void update_survival_estimates_for_next_collection();

  // Set the state to start a concurrent marking cycle and clear
  // _initiate_conc_mark_if_possible because it has now been
  // acted on.
  void initiate_conc_mark();

public:
  // This sets the initiate_conc_mark_if_possible() flag to start a
  // new cycle, as long as we are not already in one. It's best if it
  // is called during a safepoint when the test whether a cycle is in
  // progress or not is stable.
  bool force_concurrent_start_if_outside_cycle(GCCause::Cause gc_cause);

  // Decide whether this garbage collection pause should be a concurrent start
  // pause and update the collector state accordingly.
  // We decide on a concurrent start pause if initiate_conc_mark_if_possible() is
  // true, the concurrent marking thread has completed its work for the previous
  // cycle, and we are not shutting down the VM.
  // This must be called at the very beginning of an evacuation pause.
  void decide_on_concurrent_start_pause();

  uint young_list_desired_length() const { return Atomic::load(&_young_list_desired_length); }
  uint young_list_target_length() const { return Atomic::load(&_young_list_target_length); }
  uint young_list_max_length() const { return Atomic::load(&_young_list_max_length); }

  bool should_allocate_mutator_region() const;

  bool can_expand_young_list() const;

  bool use_adaptive_young_list_length() const;

  // Return an estimate of the number of bytes used in young gen.
  // precondition: holding Heap_lock
  size_t estimate_used_young_bytes_locked() const;

  void transfer_survivors_to_cset(const G1SurvivorRegions* survivors);

  // Record and log stats and pending cards before not-full collection.
  // thread_buffer_cards is the number of cards that were in per-thread
  // buffers.  pending_cards includes thread_buffer_cards.
  void record_concurrent_refinement_stats(size_t pending_cards,
                                          size_t thread_buffer_cards);

  bool should_retain_evac_failed_region(HeapRegion* r) const {
    return should_retain_evac_failed_region(r->hrm_index());
  }
  bool should_retain_evac_failed_region(uint index) const;

private:
  //
  // Survivor regions policy.
  //

  // Current tenuring threshold, set to 0 if the collector reaches the
  // maximum amount of survivors regions.
  uint _tenuring_threshold;

  // The limit on the number of regions allocated for survivors.
  uint _max_survivor_regions;

  AgeTable _survivors_age_table;

  size_t desired_survivor_size(uint max_regions) const;

  // Fraction used when predicting how many optional regions to include in
  // the CSet. This fraction of the available time is used for optional regions,
  // the rest is used to add old regions to the normal CSet.
  double optional_prediction_fraction() const { return 0.2; }

public:
  // Fraction used when evacuating the optional regions. This fraction of the
  // remaining time is used to choose what regions to include in the evacuation.
  double optional_evacuation_fraction() const { return 0.75; }

  // Returns the total time that to at most reserve for handling retained regions.
  double max_time_for_retaining() const { return max_pause_time_ms() * optional_prediction_fraction(); }

  uint tenuring_threshold() const { return _tenuring_threshold; }

  uint max_survivor_regions() {
    return _max_survivor_regions;
  }

  void start_adding_survivor_regions() {
    _survivor_surv_rate_group->start_adding_regions();
  }

  void stop_adding_survivor_regions() {
    _survivor_surv_rate_group->stop_adding_regions();
  }

  void record_age_table(AgeTable* age_table) {
    _survivors_age_table.merge(age_table);
  }

  void print_age_table();

  void update_survivors_policy();
};

#endif // SHARE_GC_G1_G1POLICY_HPP
