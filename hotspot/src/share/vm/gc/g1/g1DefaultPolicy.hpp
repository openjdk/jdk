/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1DEFAULTPOLICY_HPP
#define SHARE_VM_GC_G1_G1DEFAULTPOLICY_HPP

#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1InCSetState.hpp"
#include "gc/g1/g1InitialMarkToMixedTimeTracker.hpp"
#include "gc/g1/g1MMUTracker.hpp"
#include "gc/g1/g1Predictions.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1YoungGenSizer.hpp"
#include "gc/shared/gcCause.hpp"
#include "utilities/pair.hpp"

class HeapRegion;
class G1CollectionSet;
class CollectionSetChooser;
class G1IHOPControl;
class G1Analytics;
class G1SurvivorRegions;
class G1YoungGenSizer;
class GCPolicyCounters;

class G1DefaultPolicy: public G1Policy {
 private:

  static G1IHOPControl* create_ihop_control(const G1Predictions* predictor);
  // Update the IHOP control with necessary statistics.
  void update_ihop_prediction(double mutator_time_s,
                              size_t mutator_alloc_bytes,
                              size_t young_gen_size);
  void report_ihop_statistics();

  G1Predictions _predictor;
  G1Analytics* _analytics;
  G1MMUTracker* _mmu_tracker;
  G1IHOPControl* _ihop_control;

  GCPolicyCounters* _policy_counters;

  double _full_collection_start_sec;

  jlong _collection_pause_end_millis;

  uint _young_list_target_length;
  uint _young_list_fixed_length;

  // The max number of regions we can extend the eden by while the GC
  // locker is active. This should be >= _young_list_target_length;
  uint _young_list_max_length;

  // SurvRateGroups below must be initialized after the predictor because they
  // indirectly use it through this object passed to their constructor.
  SurvRateGroup* _short_lived_surv_rate_group;
  SurvRateGroup* _survivor_surv_rate_group;

  double _reserve_factor;
  // This will be set when the heap is expanded
  // for the first time during initialization.
  uint   _reserve_regions;

  G1YoungGenSizer _young_gen_sizer;

  uint _free_regions_at_end_of_collection;

  size_t _max_rs_lengths;

  size_t _rs_lengths_prediction;

  size_t _pending_cards;

  // The amount of allocated bytes in old gen during the last mutator and the following
  // young GC phase.
  size_t _bytes_allocated_in_old_since_last_gc;

  G1InitialMarkToMixedTimeTracker _initial_mark_to_mixed;
public:
  const G1Predictions& predictor() const { return _predictor; }
  const G1Analytics* analytics()   const { return const_cast<const G1Analytics*>(_analytics); }

  void add_bytes_allocated_in_old_since_last_gc(size_t bytes) { _bytes_allocated_in_old_since_last_gc += bytes; }

  void set_region_eden(HeapRegion* hr) {
    hr->set_eden();
    hr->install_surv_rate_group(_short_lived_surv_rate_group);
  }

  void set_region_survivor(HeapRegion* hr) {
    assert(hr->is_survivor(), "pre-condition");
    hr->install_surv_rate_group(_survivor_surv_rate_group);
  }

  void record_max_rs_lengths(size_t rs_lengths) {
    _max_rs_lengths = rs_lengths;
  }


  double predict_base_elapsed_time_ms(size_t pending_cards) const;
  double predict_base_elapsed_time_ms(size_t pending_cards,
                                      size_t scanned_cards) const;
  size_t predict_bytes_to_copy(HeapRegion* hr) const;
  double predict_region_elapsed_time_ms(HeapRegion* hr, bool for_young_gc) const;

  double predict_survivor_regions_evac_time() const;

  bool should_update_surv_rate_group_predictors() {
    return collector_state()->last_gc_was_young() && !collector_state()->in_marking_window();
  }

  void cset_regions_freed() {
    bool update = should_update_surv_rate_group_predictors();

    _short_lived_surv_rate_group->all_surviving_words_recorded(predictor(), update);
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

  double predict_yg_surv_rate(int age, SurvRateGroup* surv_rate_group) const;

  double predict_yg_surv_rate(int age) const;

  double accum_yg_surv_rate_pred(int age) const;

protected:
  G1CollectionSet* _collection_set;
  virtual double average_time_ms(G1GCPhaseTimes::GCParPhases phase) const;
  virtual double other_time_ms(double pause_time_ms) const;

  double young_other_time_ms() const;
  double non_young_other_time_ms() const;
  double constant_other_time_ms(double pause_time_ms) const;

  CollectionSetChooser* cset_chooser() const;
private:

  // The number of bytes copied during the GC.
  size_t _bytes_copied_during_gc;

  // Stash a pointer to the g1 heap.
  G1CollectedHeap* _g1;

  G1GCPhaseTimes* _phase_times;

  // This set of variables tracks the collector efficiency, in order to
  // determine whether we should initiate a new marking.
  double _mark_remark_start_sec;
  double _mark_cleanup_start_sec;

  // Updates the internal young list maximum and target lengths. Returns the
  // unbounded young list target length.
  uint update_young_list_max_and_target_length();
  uint update_young_list_max_and_target_length(size_t rs_lengths);

  // Update the young list target length either by setting it to the
  // desired fixed value or by calculating it using G1's pause
  // prediction model. If no rs_lengths parameter is passed, predict
  // the RS lengths using the prediction model, otherwise use the
  // given rs_lengths as the prediction.
  // Returns the unbounded young list target length.
  uint update_young_list_target_length(size_t rs_lengths);

  // Calculate and return the minimum desired young list target
  // length. This is the minimum desired young list length according
  // to the user's inputs.
  uint calculate_young_list_desired_min_length(uint base_min_length) const;

  // Calculate and return the maximum desired young list target
  // length. This is the maximum desired young list length according
  // to the user's inputs.
  uint calculate_young_list_desired_max_length() const;

  // Calculate and return the maximum young list target length that
  // can fit into the pause time goal. The parameters are: rs_lengths
  // represent the prediction of how large the young RSet lengths will
  // be, base_min_length is the already existing number of regions in
  // the young list, min_length and max_length are the desired min and
  // max young list length according to the user's inputs.
  uint calculate_young_list_target_length(size_t rs_lengths,
                                          uint base_min_length,
                                          uint desired_min_length,
                                          uint desired_max_length) const;

  // Result of the bounded_young_list_target_length() method, containing both the
  // bounded as well as the unbounded young list target lengths in this order.
  typedef Pair<uint, uint, StackObj> YoungTargetLengths;
  YoungTargetLengths young_list_target_lengths(size_t rs_lengths) const;

  void update_rs_lengths_prediction();
  void update_rs_lengths_prediction(size_t prediction);

  // Check whether a given young length (young_length) fits into the
  // given target pause time and whether the prediction for the amount
  // of objects to be copied for the given length will fit into the
  // given free space (expressed by base_free_regions).  It is used by
  // calculate_young_list_target_length().
  bool predict_will_fit(uint young_length, double base_time_ms,
                        uint base_free_regions, double target_pause_time_ms) const;

public:
  size_t pending_cards() const { return _pending_cards; }

  uint calc_min_old_cset_length() const;
  uint calc_max_old_cset_length() const;

  double reclaimable_bytes_perc(size_t reclaimable_bytes) const;

  jlong collection_pause_end_millis() { return _collection_pause_end_millis; }

private:
  // Sets up marking if proper conditions are met.
  void maybe_start_marking();

  // The kind of STW pause.
  enum PauseKind {
    FullGC,
    YoungOnlyGC,
    MixedGC,
    LastYoungGC,
    InitialMarkGC,
    Cleanup,
    Remark
  };

  // Calculate PauseKind from internal state.
  PauseKind young_gc_pause_kind() const;
  // Record the given STW pause with the given start and end times (in s).
  void record_pause(PauseKind kind, double start, double end);
  // Indicate that we aborted marking before doing any mixed GCs.
  void abort_time_to_mixed_tracking();
public:

  G1DefaultPolicy();

  virtual ~G1DefaultPolicy();

  G1CollectorState* collector_state() const;

  G1GCPhaseTimes* phase_times() const { return _phase_times; }

  void revise_young_list_target_length_if_necessary(size_t rs_lengths);

  void record_new_heap_size(uint new_number_of_regions);

  void init(G1CollectedHeap* g1h, G1CollectionSet* collection_set);

  virtual void note_gc_start();

  bool need_to_start_conc_mark(const char* source, size_t alloc_word_size = 0);

  bool about_to_start_mixed_phase() const;

  void record_collection_pause_start(double start_time_sec);
  void record_collection_pause_end(double pause_time_ms, size_t cards_scanned, size_t heap_used_bytes_before_gc);

  void record_full_collection_start();
  void record_full_collection_end();

  void record_concurrent_mark_init_end(double mark_init_elapsed_time_ms);

  void record_concurrent_mark_remark_start();
  void record_concurrent_mark_remark_end();

  void record_concurrent_mark_cleanup_start();
  void record_concurrent_mark_cleanup_end();
  void record_concurrent_mark_cleanup_completed();

  virtual void print_phases();

  void record_bytes_copied_during_gc(size_t bytes) {
    _bytes_copied_during_gc += bytes;
  }

  size_t bytes_copied_during_gc() const {
    return _bytes_copied_during_gc;
  }

  bool next_gc_should_be_mixed(const char* true_action_str,
                               const char* false_action_str) const;

  virtual void finalize_collection_set(double target_pause_time_ms, G1SurvivorRegions* survivor);
private:
  // Set the state to start a concurrent marking cycle and clear
  // _initiate_conc_mark_if_possible because it has now been
  // acted on.
  void initiate_conc_mark();

public:
  bool force_initial_mark_if_outside_cycle(GCCause::Cause gc_cause);

  void decide_on_conc_mark_initiation();

  void finished_recalculating_age_indexes(bool is_survivors) {
    if (is_survivors) {
      _survivor_surv_rate_group->finished_recalculating_age_indexes();
    } else {
      _short_lived_surv_rate_group->finished_recalculating_age_indexes();
    }
  }

  size_t young_list_target_length() const { return _young_list_target_length; }

  bool should_allocate_mutator_region() const;

  bool can_expand_young_list() const;

  uint young_list_max_length() const {
    return _young_list_max_length;
  }

  bool adaptive_young_list_length() const;

  virtual bool should_process_references() const {
    return true;
  }

  void transfer_survivors_to_cset(const G1SurvivorRegions* survivors);

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

protected:
  size_t desired_survivor_size() const;
public:
  uint tenuring_threshold() const { return _tenuring_threshold; }

  uint max_survivor_regions() {
    return _max_survivor_regions;
  }

  void note_start_adding_survivor_regions() {
    _survivor_surv_rate_group->start_adding_regions();
  }

  void note_stop_adding_survivor_regions() {
    _survivor_surv_rate_group->stop_adding_regions();
  }

  void record_age_table(AgeTable* age_table) {
    _survivors_age_table.merge(age_table);
  }

  void print_age_table();

  void update_max_gc_locker_expansion();

  void update_survivors_policy();
};

#endif // SHARE_VM_GC_G1_G1DEFAULTPOLICY_HPP
