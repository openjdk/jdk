/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1COLLECTORPOLICY_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1COLLECTORPOLICY_HPP

#include "gc_implementation/g1/collectionSetChooser.hpp"
#include "gc_implementation/g1/g1MMUTracker.hpp"
#include "memory/collectorPolicy.hpp"

// A G1CollectorPolicy makes policy decisions that determine the
// characteristics of the collector.  Examples include:
//   * choice of collection set.
//   * when to collect.

class HeapRegion;
class CollectionSetChooser;
class G1GCPhaseTimes;

// TraceGen0Time collects data on _both_ young and mixed evacuation pauses
// (the latter may contain non-young regions - i.e. regions that are
// technically in Gen1) while TraceGen1Time collects data about full GCs.
class TraceGen0TimeData : public CHeapObj<mtGC> {
 private:
  unsigned  _young_pause_num;
  unsigned  _mixed_pause_num;

  NumberSeq _all_stop_world_times_ms;
  NumberSeq _all_yield_times_ms;

  NumberSeq _total;
  NumberSeq _other;
  NumberSeq _root_region_scan_wait;
  NumberSeq _parallel;
  NumberSeq _ext_root_scan;
  NumberSeq _satb_filtering;
  NumberSeq _update_rs;
  NumberSeq _scan_rs;
  NumberSeq _obj_copy;
  NumberSeq _termination;
  NumberSeq _parallel_other;
  NumberSeq _clear_ct;

  void print_summary(const char* str, const NumberSeq* seq) const;
  void print_summary_sd(const char* str, const NumberSeq* seq) const;

public:
   TraceGen0TimeData() : _young_pause_num(0), _mixed_pause_num(0) {};
  void record_start_collection(double time_to_stop_the_world_ms);
  void record_yield_time(double yield_time_ms);
  void record_end_collection(double pause_time_ms, G1GCPhaseTimes* phase_times);
  void increment_young_collection_count();
  void increment_mixed_collection_count();
  void print() const;
};

class TraceGen1TimeData : public CHeapObj<mtGC> {
 private:
  NumberSeq _all_full_gc_times;

 public:
  void record_full_collection(double full_gc_time_ms);
  void print() const;
};

// There are three command line options related to the young gen size:
// NewSize, MaxNewSize and NewRatio (There is also -Xmn, but that is
// just a short form for NewSize==MaxNewSize). G1 will use its internal
// heuristics to calculate the actual young gen size, so these options
// basically only limit the range within which G1 can pick a young gen
// size. Also, these are general options taking byte sizes. G1 will
// internally work with a number of regions instead. So, some rounding
// will occur.
//
// If nothing related to the the young gen size is set on the command
// line we should allow the young gen to be between G1NewSizePercent
// and G1MaxNewSizePercent of the heap size. This means that every time
// the heap size changes, the limits for the young gen size will be
// recalculated.
//
// If only -XX:NewSize is set we should use the specified value as the
// minimum size for young gen. Still using G1MaxNewSizePercent of the
// heap as maximum.
//
// If only -XX:MaxNewSize is set we should use the specified value as the
// maximum size for young gen. Still using G1NewSizePercent of the heap
// as minimum.
//
// If -XX:NewSize and -XX:MaxNewSize are both specified we use these values.
// No updates when the heap size changes. There is a special case when
// NewSize==MaxNewSize. This is interpreted as "fixed" and will use a
// different heuristic for calculating the collection set when we do mixed
// collection.
//
// If only -XX:NewRatio is set we should use the specified ratio of the heap
// as both min and max. This will be interpreted as "fixed" just like the
// NewSize==MaxNewSize case above. But we will update the min and max
// everytime the heap size changes.
//
// NewSize and MaxNewSize override NewRatio. So, NewRatio is ignored if it is
// combined with either NewSize or MaxNewSize. (A warning message is printed.)
class G1YoungGenSizer : public CHeapObj<mtGC> {
private:
  enum SizerKind {
    SizerDefaults,
    SizerNewSizeOnly,
    SizerMaxNewSizeOnly,
    SizerMaxAndNewSize,
    SizerNewRatio
  };
  SizerKind _sizer_kind;
  uint _min_desired_young_length;
  uint _max_desired_young_length;
  bool _adaptive_size;
  uint calculate_default_min_length(uint new_number_of_heap_regions);
  uint calculate_default_max_length(uint new_number_of_heap_regions);

  // Update the given values for minimum and maximum young gen length in regions
  // given the number of heap regions depending on the kind of sizing algorithm.
  void recalculate_min_max_young_length(uint number_of_heap_regions, uint* min_young_length, uint* max_young_length);

public:
  G1YoungGenSizer();
  // Calculate the maximum length of the young gen given the number of regions
  // depending on the sizing algorithm.
  uint max_young_length(uint number_of_heap_regions);

  void heap_size_changed(uint new_number_of_heap_regions);
  uint min_desired_young_length() {
    return _min_desired_young_length;
  }
  uint max_desired_young_length() {
    return _max_desired_young_length;
  }
  bool adaptive_young_list_length() {
    return _adaptive_size;
  }
};

class G1CollectorPolicy: public CollectorPolicy {
private:
  // either equal to the number of parallel threads, if ParallelGCThreads
  // has been set, or 1 otherwise
  int _parallel_gc_threads;

  // The number of GC threads currently active.
  uintx _no_of_gc_threads;

  enum SomePrivateConstants {
    NumPrevPausesForHeuristics = 10
  };

  G1MMUTracker* _mmu_tracker;

  void initialize_alignments();
  void initialize_flags();

  CollectionSetChooser* _collectionSetChooser;

  double _full_collection_start_sec;
  uint   _cur_collection_pause_used_regions_at_start;

  // These exclude marking times.
  TruncatedSeq* _recent_gc_times_ms;

  TruncatedSeq* _concurrent_mark_remark_times_ms;
  TruncatedSeq* _concurrent_mark_cleanup_times_ms;

  TraceGen0TimeData _trace_gen0_time_data;
  TraceGen1TimeData _trace_gen1_time_data;

  double _stop_world_start;

  // indicates whether we are in young or mixed GC mode
  bool _gcs_are_young;

  uint _young_list_target_length;
  uint _young_list_fixed_length;

  // The max number of regions we can extend the eden by while the GC
  // locker is active. This should be >= _young_list_target_length;
  uint _young_list_max_length;

  bool                  _last_gc_was_young;

  bool                  _during_marking;
  bool                  _in_marking_window;
  bool                  _in_marking_window_im;

  SurvRateGroup*        _short_lived_surv_rate_group;
  SurvRateGroup*        _survivor_surv_rate_group;
  // add here any more surv rate groups

  double                _gc_overhead_perc;

  double _reserve_factor;
  uint _reserve_regions;

  bool during_marking() {
    return _during_marking;
  }

  enum PredictionConstants {
    TruncatedSeqLength = 10
  };

  TruncatedSeq* _alloc_rate_ms_seq;
  double        _prev_collection_pause_end_ms;

  TruncatedSeq* _rs_length_diff_seq;
  TruncatedSeq* _cost_per_card_ms_seq;
  TruncatedSeq* _young_cards_per_entry_ratio_seq;
  TruncatedSeq* _mixed_cards_per_entry_ratio_seq;
  TruncatedSeq* _cost_per_entry_ms_seq;
  TruncatedSeq* _mixed_cost_per_entry_ms_seq;
  TruncatedSeq* _cost_per_byte_ms_seq;
  TruncatedSeq* _constant_other_time_ms_seq;
  TruncatedSeq* _young_other_cost_per_region_ms_seq;
  TruncatedSeq* _non_young_other_cost_per_region_ms_seq;

  TruncatedSeq* _pending_cards_seq;
  TruncatedSeq* _rs_lengths_seq;

  TruncatedSeq* _cost_per_byte_ms_during_cm_seq;

  G1YoungGenSizer* _young_gen_sizer;

  uint _eden_cset_region_length;
  uint _survivor_cset_region_length;
  uint _old_cset_region_length;

  void init_cset_region_lengths(uint eden_cset_region_length,
                                uint survivor_cset_region_length);

  uint eden_cset_region_length()     { return _eden_cset_region_length;     }
  uint survivor_cset_region_length() { return _survivor_cset_region_length; }
  uint old_cset_region_length()      { return _old_cset_region_length;      }

  uint _free_regions_at_end_of_collection;

  size_t _recorded_rs_lengths;
  size_t _max_rs_lengths;
  double _sigma;

  size_t _rs_lengths_prediction;

  double sigma() { return _sigma; }

  // A function that prevents us putting too much stock in small sample
  // sets.  Returns a number between 2.0 and 1.0, depending on the number
  // of samples.  5 or more samples yields one; fewer scales linearly from
  // 2.0 at 1 sample to 1.0 at 5.
  double confidence_factor(int samples) {
    if (samples > 4) return 1.0;
    else return  1.0 + sigma() * ((double)(5 - samples))/2.0;
  }

  double get_new_neg_prediction(TruncatedSeq* seq) {
    return seq->davg() - sigma() * seq->dsd();
  }

#ifndef PRODUCT
  bool verify_young_ages(HeapRegion* head, SurvRateGroup *surv_rate_group);
#endif // PRODUCT

  void adjust_concurrent_refinement(double update_rs_time,
                                    double update_rs_processed_buffers,
                                    double goal_ms);

  uintx no_of_gc_threads() { return _no_of_gc_threads; }
  void set_no_of_gc_threads(uintx v) { _no_of_gc_threads = v; }

  double _pause_time_target_ms;

  size_t _pending_cards;

public:
  // Accessors

  void set_region_eden(HeapRegion* hr, int young_index_in_cset) {
    hr->set_young();
    hr->install_surv_rate_group(_short_lived_surv_rate_group);
    hr->set_young_index_in_cset(young_index_in_cset);
  }

  void set_region_survivor(HeapRegion* hr, int young_index_in_cset) {
    assert(hr->is_young() && hr->is_survivor(), "pre-condition");
    hr->install_surv_rate_group(_survivor_surv_rate_group);
    hr->set_young_index_in_cset(young_index_in_cset);
  }

#ifndef PRODUCT
  bool verify_young_ages();
#endif // PRODUCT

  double get_new_prediction(TruncatedSeq* seq) {
    return MAX2(seq->davg() + sigma() * seq->dsd(),
                seq->davg() * confidence_factor(seq->num()));
  }

  void record_max_rs_lengths(size_t rs_lengths) {
    _max_rs_lengths = rs_lengths;
  }

  size_t predict_rs_length_diff() {
    return (size_t) get_new_prediction(_rs_length_diff_seq);
  }

  double predict_alloc_rate_ms() {
    return get_new_prediction(_alloc_rate_ms_seq);
  }

  double predict_cost_per_card_ms() {
    return get_new_prediction(_cost_per_card_ms_seq);
  }

  double predict_rs_update_time_ms(size_t pending_cards) {
    return (double) pending_cards * predict_cost_per_card_ms();
  }

  double predict_young_cards_per_entry_ratio() {
    return get_new_prediction(_young_cards_per_entry_ratio_seq);
  }

  double predict_mixed_cards_per_entry_ratio() {
    if (_mixed_cards_per_entry_ratio_seq->num() < 2) {
      return predict_young_cards_per_entry_ratio();
    } else {
      return get_new_prediction(_mixed_cards_per_entry_ratio_seq);
    }
  }

  size_t predict_young_card_num(size_t rs_length) {
    return (size_t) ((double) rs_length *
                     predict_young_cards_per_entry_ratio());
  }

  size_t predict_non_young_card_num(size_t rs_length) {
    return (size_t) ((double) rs_length *
                     predict_mixed_cards_per_entry_ratio());
  }

  double predict_rs_scan_time_ms(size_t card_num) {
    if (gcs_are_young()) {
      return (double) card_num * get_new_prediction(_cost_per_entry_ms_seq);
    } else {
      return predict_mixed_rs_scan_time_ms(card_num);
    }
  }

  double predict_mixed_rs_scan_time_ms(size_t card_num) {
    if (_mixed_cost_per_entry_ms_seq->num() < 3) {
      return (double) card_num * get_new_prediction(_cost_per_entry_ms_seq);
    } else {
      return (double) (card_num *
                       get_new_prediction(_mixed_cost_per_entry_ms_seq));
    }
  }

  double predict_object_copy_time_ms_during_cm(size_t bytes_to_copy) {
    if (_cost_per_byte_ms_during_cm_seq->num() < 3) {
      return (1.1 * (double) bytes_to_copy) *
              get_new_prediction(_cost_per_byte_ms_seq);
    } else {
      return (double) bytes_to_copy *
             get_new_prediction(_cost_per_byte_ms_during_cm_seq);
    }
  }

  double predict_object_copy_time_ms(size_t bytes_to_copy) {
    if (_in_marking_window && !_in_marking_window_im) {
      return predict_object_copy_time_ms_during_cm(bytes_to_copy);
    } else {
      return (double) bytes_to_copy *
              get_new_prediction(_cost_per_byte_ms_seq);
    }
  }

  double predict_constant_other_time_ms() {
    return get_new_prediction(_constant_other_time_ms_seq);
  }

  double predict_young_other_time_ms(size_t young_num) {
    return (double) young_num *
           get_new_prediction(_young_other_cost_per_region_ms_seq);
  }

  double predict_non_young_other_time_ms(size_t non_young_num) {
    return (double) non_young_num *
           get_new_prediction(_non_young_other_cost_per_region_ms_seq);
  }

  double predict_base_elapsed_time_ms(size_t pending_cards);
  double predict_base_elapsed_time_ms(size_t pending_cards,
                                      size_t scanned_cards);
  size_t predict_bytes_to_copy(HeapRegion* hr);
  double predict_region_elapsed_time_ms(HeapRegion* hr, bool for_young_gc);

  void set_recorded_rs_lengths(size_t rs_lengths);

  uint cset_region_length()       { return young_cset_region_length() +
                                           old_cset_region_length(); }
  uint young_cset_region_length() { return eden_cset_region_length() +
                                           survivor_cset_region_length(); }

  double predict_survivor_regions_evac_time();

  void cset_regions_freed() {
    bool propagate = _last_gc_was_young && !_in_marking_window;
    _short_lived_surv_rate_group->all_surviving_words_recorded(propagate);
    _survivor_surv_rate_group->all_surviving_words_recorded(propagate);
    // also call it on any more surv rate groups
  }

  G1MMUTracker* mmu_tracker() {
    return _mmu_tracker;
  }

  double max_pause_time_ms() {
    return _mmu_tracker->max_gc_time() * 1000.0;
  }

  double predict_remark_time_ms() {
    return get_new_prediction(_concurrent_mark_remark_times_ms);
  }

  double predict_cleanup_time_ms() {
    return get_new_prediction(_concurrent_mark_cleanup_times_ms);
  }

  // Returns an estimate of the survival rate of the region at yg-age
  // "yg_age".
  double predict_yg_surv_rate(int age, SurvRateGroup* surv_rate_group) {
    TruncatedSeq* seq = surv_rate_group->get_seq(age);
    if (seq->num() == 0)
      gclog_or_tty->print("BARF! age is %d", age);
    guarantee( seq->num() > 0, "invariant" );
    double pred = get_new_prediction(seq);
    if (pred > 1.0)
      pred = 1.0;
    return pred;
  }

  double predict_yg_surv_rate(int age) {
    return predict_yg_surv_rate(age, _short_lived_surv_rate_group);
  }

  double accum_yg_surv_rate_pred(int age) {
    return _short_lived_surv_rate_group->accum_surv_rate_pred(age);
  }

private:
  // Statistics kept per GC stoppage, pause or full.
  TruncatedSeq* _recent_prev_end_times_for_all_gcs_sec;

  // Add a new GC of the given duration and end time to the record.
  void update_recent_gc_times(double end_time_sec, double elapsed_ms);

  // The head of the list (via "next_in_collection_set()") representing the
  // current collection set. Set from the incrementally built collection
  // set at the start of the pause.
  HeapRegion* _collection_set;

  // The number of bytes in the collection set before the pause. Set from
  // the incrementally built collection set at the start of an evacuation
  // pause, and incremented in finalize_cset() when adding old regions
  // (if any) to the collection set.
  size_t _collection_set_bytes_used_before;

  // The number of bytes copied during the GC.
  size_t _bytes_copied_during_gc;

  // The associated information that is maintained while the incremental
  // collection set is being built with young regions. Used to populate
  // the recorded info for the evacuation pause.

  enum CSetBuildType {
    Active,             // We are actively building the collection set
    Inactive            // We are not actively building the collection set
  };

  CSetBuildType _inc_cset_build_state;

  // The head of the incrementally built collection set.
  HeapRegion* _inc_cset_head;

  // The tail of the incrementally built collection set.
  HeapRegion* _inc_cset_tail;

  // The number of bytes in the incrementally built collection set.
  // Used to set _collection_set_bytes_used_before at the start of
  // an evacuation pause.
  size_t _inc_cset_bytes_used_before;

  // Used to record the highest end of heap region in collection set
  HeapWord* _inc_cset_max_finger;

  // The RSet lengths recorded for regions in the CSet. It is updated
  // by the thread that adds a new region to the CSet. We assume that
  // only one thread can be allocating a new CSet region (currently,
  // it does so after taking the Heap_lock) hence no need to
  // synchronize updates to this field.
  size_t _inc_cset_recorded_rs_lengths;

  // A concurrent refinement thread periodcially samples the young
  // region RSets and needs to update _inc_cset_recorded_rs_lengths as
  // the RSets grow. Instead of having to syncronize updates to that
  // field we accumulate them in this field and add it to
  // _inc_cset_recorded_rs_lengths_diffs at the start of a GC.
  ssize_t _inc_cset_recorded_rs_lengths_diffs;

  // The predicted elapsed time it will take to collect the regions in
  // the CSet. This is updated by the thread that adds a new region to
  // the CSet. See the comment for _inc_cset_recorded_rs_lengths about
  // MT-safety assumptions.
  double _inc_cset_predicted_elapsed_time_ms;

  // See the comment for _inc_cset_recorded_rs_lengths_diffs.
  double _inc_cset_predicted_elapsed_time_ms_diffs;

  // Stash a pointer to the g1 heap.
  G1CollectedHeap* _g1;

  G1GCPhaseTimes* _phase_times;

  // The ratio of gc time to elapsed time, computed over recent pauses.
  double _recent_avg_pause_time_ratio;

  double recent_avg_pause_time_ratio() {
    return _recent_avg_pause_time_ratio;
  }

  // At the end of a pause we check the heap occupancy and we decide
  // whether we will start a marking cycle during the next pause. If
  // we decide that we want to do that, we will set this parameter to
  // true. So, this parameter will stay true between the end of a
  // pause and the beginning of a subsequent pause (not necessarily
  // the next one, see the comments on the next field) when we decide
  // that we will indeed start a marking cycle and do the initial-mark
  // work.
  volatile bool _initiate_conc_mark_if_possible;

  // If initiate_conc_mark_if_possible() is set at the beginning of a
  // pause, it is a suggestion that the pause should start a marking
  // cycle by doing the initial-mark work. However, it is possible
  // that the concurrent marking thread is still finishing up the
  // previous marking cycle (e.g., clearing the next marking
  // bitmap). If that is the case we cannot start a new cycle and
  // we'll have to wait for the concurrent marking thread to finish
  // what it is doing. In this case we will postpone the marking cycle
  // initiation decision for the next pause. When we eventually decide
  // to start a cycle, we will set _during_initial_mark_pause which
  // will stay true until the end of the initial-mark pause and it's
  // the condition that indicates that a pause is doing the
  // initial-mark work.
  volatile bool _during_initial_mark_pause;

  bool _last_young_gc;

  // This set of variables tracks the collector efficiency, in order to
  // determine whether we should initiate a new marking.
  double _cur_mark_stop_world_time_ms;
  double _mark_remark_start_sec;
  double _mark_cleanup_start_sec;

  // Update the young list target length either by setting it to the
  // desired fixed value or by calculating it using G1's pause
  // prediction model. If no rs_lengths parameter is passed, predict
  // the RS lengths using the prediction model, otherwise use the
  // given rs_lengths as the prediction.
  void update_young_list_target_length(size_t rs_lengths = (size_t) -1);

  // Calculate and return the minimum desired young list target
  // length. This is the minimum desired young list length according
  // to the user's inputs.
  uint calculate_young_list_desired_min_length(uint base_min_length);

  // Calculate and return the maximum desired young list target
  // length. This is the maximum desired young list length according
  // to the user's inputs.
  uint calculate_young_list_desired_max_length();

  // Calculate and return the maximum young list target length that
  // can fit into the pause time goal. The parameters are: rs_lengths
  // represent the prediction of how large the young RSet lengths will
  // be, base_min_length is the alreay existing number of regions in
  // the young list, min_length and max_length are the desired min and
  // max young list length according to the user's inputs.
  uint calculate_young_list_target_length(size_t rs_lengths,
                                          uint base_min_length,
                                          uint desired_min_length,
                                          uint desired_max_length);

  // Check whether a given young length (young_length) fits into the
  // given target pause time and whether the prediction for the amount
  // of objects to be copied for the given length will fit into the
  // given free space (expressed by base_free_regions).  It is used by
  // calculate_young_list_target_length().
  bool predict_will_fit(uint young_length, double base_time_ms,
                        uint base_free_regions, double target_pause_time_ms);

  // Calculate the minimum number of old regions we'll add to the CSet
  // during a mixed GC.
  uint calc_min_old_cset_length();

  // Calculate the maximum number of old regions we'll add to the CSet
  // during a mixed GC.
  uint calc_max_old_cset_length();

  // Returns the given amount of uncollected reclaimable space
  // as a percentage of the current heap capacity.
  double reclaimable_bytes_perc(size_t reclaimable_bytes);

public:

  G1CollectorPolicy();

  virtual G1CollectorPolicy* as_g1_policy() { return this; }

  virtual CollectorPolicy::Name kind() {
    return CollectorPolicy::G1CollectorPolicyKind;
  }

  G1GCPhaseTimes* phase_times() const { return _phase_times; }

  // Check the current value of the young list RSet lengths and
  // compare it against the last prediction. If the current value is
  // higher, recalculate the young list target length prediction.
  void revise_young_list_target_length_if_necessary();

  // This should be called after the heap is resized.
  void record_new_heap_size(uint new_number_of_regions);

  void init();

  // Create jstat counters for the policy.
  virtual void initialize_gc_policy_counters();

  virtual HeapWord* mem_allocate_work(size_t size,
                                      bool is_tlab,
                                      bool* gc_overhead_limit_was_exceeded);

  // This method controls how a collector handles one or more
  // of its generations being fully allocated.
  virtual HeapWord* satisfy_failed_allocation(size_t size,
                                              bool is_tlab);

  BarrierSet::Name barrier_set_name() { return BarrierSet::G1SATBCTLogging; }

  bool need_to_start_conc_mark(const char* source, size_t alloc_word_size = 0);

  // Record the start and end of an evacuation pause.
  void record_collection_pause_start(double start_time_sec);
  void record_collection_pause_end(double pause_time_ms, EvacuationInfo& evacuation_info);

  // Record the start and end of a full collection.
  void record_full_collection_start();
  void record_full_collection_end();

  // Must currently be called while the world is stopped.
  void record_concurrent_mark_init_end(double mark_init_elapsed_time_ms);

  // Record start and end of remark.
  void record_concurrent_mark_remark_start();
  void record_concurrent_mark_remark_end();

  // Record start, end, and completion of cleanup.
  void record_concurrent_mark_cleanup_start();
  void record_concurrent_mark_cleanup_end(int no_of_gc_threads);
  void record_concurrent_mark_cleanup_completed();

  // Records the information about the heap size for reporting in
  // print_detailed_heap_transition
  void record_heap_size_info_at_start(bool full);

  // Print heap sizing transition (with less and more detail).
  void print_heap_transition();
  void print_detailed_heap_transition(bool full = false);

  void record_stop_world_start();
  void record_concurrent_pause();

  // Record how much space we copied during a GC. This is typically
  // called when a GC alloc region is being retired.
  void record_bytes_copied_during_gc(size_t bytes) {
    _bytes_copied_during_gc += bytes;
  }

  // The amount of space we copied during a GC.
  size_t bytes_copied_during_gc() {
    return _bytes_copied_during_gc;
  }

  // Determine whether there are candidate regions so that the
  // next GC should be mixed. The two action strings are used
  // in the ergo output when the method returns true or false.
  bool next_gc_should_be_mixed(const char* true_action_str,
                               const char* false_action_str);

  // Choose a new collection set.  Marks the chosen regions as being
  // "in_collection_set", and links them together.  The head and number of
  // the collection set are available via access methods.
  void finalize_cset(double target_pause_time_ms, EvacuationInfo& evacuation_info);

  // The head of the list (via "next_in_collection_set()") representing the
  // current collection set.
  HeapRegion* collection_set() { return _collection_set; }

  void clear_collection_set() { _collection_set = NULL; }

  // Add old region "hr" to the CSet.
  void add_old_region_to_cset(HeapRegion* hr);

  // Incremental CSet Support

  // The head of the incrementally built collection set.
  HeapRegion* inc_cset_head() { return _inc_cset_head; }

  // The tail of the incrementally built collection set.
  HeapRegion* inc_set_tail() { return _inc_cset_tail; }

  // Initialize incremental collection set info.
  void start_incremental_cset_building();

  // Perform any final calculations on the incremental CSet fields
  // before we can use them.
  void finalize_incremental_cset_building();

  void clear_incremental_cset() {
    _inc_cset_head = NULL;
    _inc_cset_tail = NULL;
  }

  // Stop adding regions to the incremental collection set
  void stop_incremental_cset_building() { _inc_cset_build_state = Inactive; }

  // Add information about hr to the aggregated information for the
  // incrementally built collection set.
  void add_to_incremental_cset_info(HeapRegion* hr, size_t rs_length);

  // Update information about hr in the aggregated information for
  // the incrementally built collection set.
  void update_incremental_cset_info(HeapRegion* hr, size_t new_rs_length);

private:
  // Update the incremental cset information when adding a region
  // (should not be called directly).
  void add_region_to_incremental_cset_common(HeapRegion* hr);

public:
  // Add hr to the LHS of the incremental collection set.
  void add_region_to_incremental_cset_lhs(HeapRegion* hr);

  // Add hr to the RHS of the incremental collection set.
  void add_region_to_incremental_cset_rhs(HeapRegion* hr);

#ifndef PRODUCT
  void print_collection_set(HeapRegion* list_head, outputStream* st);
#endif // !PRODUCT

  bool initiate_conc_mark_if_possible()       { return _initiate_conc_mark_if_possible;  }
  void set_initiate_conc_mark_if_possible()   { _initiate_conc_mark_if_possible = true;  }
  void clear_initiate_conc_mark_if_possible() { _initiate_conc_mark_if_possible = false; }

  bool during_initial_mark_pause()      { return _during_initial_mark_pause;  }
  void set_during_initial_mark_pause()  { _during_initial_mark_pause = true;  }
  void clear_during_initial_mark_pause(){ _during_initial_mark_pause = false; }

  // This sets the initiate_conc_mark_if_possible() flag to start a
  // new cycle, as long as we are not already in one. It's best if it
  // is called during a safepoint when the test whether a cycle is in
  // progress or not is stable.
  bool force_initial_mark_if_outside_cycle(GCCause::Cause gc_cause);

  // This is called at the very beginning of an evacuation pause (it
  // has to be the first thing that the pause does). If
  // initiate_conc_mark_if_possible() is true, and the concurrent
  // marking thread has completed its work during the previous cycle,
  // it will set during_initial_mark_pause() to so that the pause does
  // the initial-mark work and start a marking cycle.
  void decide_on_conc_mark_initiation();

  // If an expansion would be appropriate, because recent GC overhead had
  // exceeded the desired limit, return an amount to expand by.
  size_t expansion_amount();

  // Print tracing information.
  void print_tracing_info() const;

  // Print stats on young survival ratio
  void print_yg_surv_rate_info() const;

  void finished_recalculating_age_indexes(bool is_survivors) {
    if (is_survivors) {
      _survivor_surv_rate_group->finished_recalculating_age_indexes();
    } else {
      _short_lived_surv_rate_group->finished_recalculating_age_indexes();
    }
    // do that for any other surv rate groups
  }

  bool is_young_list_full() {
    uint young_list_length = _g1->young_list()->length();
    uint young_list_target_length = _young_list_target_length;
    return young_list_length >= young_list_target_length;
  }

  bool can_expand_young_list() {
    uint young_list_length = _g1->young_list()->length();
    uint young_list_max_length = _young_list_max_length;
    return young_list_length < young_list_max_length;
  }

  uint young_list_max_length() {
    return _young_list_max_length;
  }

  bool gcs_are_young() {
    return _gcs_are_young;
  }
  void set_gcs_are_young(bool gcs_are_young) {
    _gcs_are_young = gcs_are_young;
  }

  bool adaptive_young_list_length() {
    return _young_gen_sizer->adaptive_young_list_length();
  }

private:
  //
  // Survivor regions policy.
  //

  // Current tenuring threshold, set to 0 if the collector reaches the
  // maximum amount of survivors regions.
  uint _tenuring_threshold;

  // The limit on the number of regions allocated for survivors.
  uint _max_survivor_regions;

  // For reporting purposes.
  // The value of _heap_bytes_before_gc is also used to calculate
  // the cost of copying.

  size_t _eden_used_bytes_before_gc;         // Eden occupancy before GC
  size_t _survivor_used_bytes_before_gc;     // Survivor occupancy before GC
  size_t _heap_used_bytes_before_gc;         // Heap occupancy before GC
  size_t _metaspace_used_bytes_before_gc;    // Metaspace occupancy before GC

  size_t _eden_capacity_bytes_before_gc;     // Eden capacity before GC
  size_t _heap_capacity_bytes_before_gc;     // Heap capacity before GC

  // The amount of survivor regions after a collection.
  uint _recorded_survivor_regions;
  // List of survivor regions.
  HeapRegion* _recorded_survivor_head;
  HeapRegion* _recorded_survivor_tail;

  ageTable _survivors_age_table;

public:
  uint tenuring_threshold() const { return _tenuring_threshold; }

  inline GCAllocPurpose
    evacuation_destination(HeapRegion* src_region, uint age, size_t word_sz) {
      if (age < _tenuring_threshold && src_region->is_young()) {
        return GCAllocForSurvived;
      } else {
        return GCAllocForTenured;
      }
  }

  inline bool track_object_age(GCAllocPurpose purpose) {
    return purpose == GCAllocForSurvived;
  }

  static const uint REGIONS_UNLIMITED = (uint) -1;

  uint max_regions(int purpose);

  // The limit on regions for a particular purpose is reached.
  void note_alloc_region_limit_reached(int purpose) {
    if (purpose == GCAllocForSurvived) {
      _tenuring_threshold = 0;
    }
  }

  void note_start_adding_survivor_regions() {
    _survivor_surv_rate_group->start_adding_regions();
  }

  void note_stop_adding_survivor_regions() {
    _survivor_surv_rate_group->stop_adding_regions();
  }

  void record_survivor_regions(uint regions,
                               HeapRegion* head,
                               HeapRegion* tail) {
    _recorded_survivor_regions = regions;
    _recorded_survivor_head    = head;
    _recorded_survivor_tail    = tail;
  }

  uint recorded_survivor_regions() {
    return _recorded_survivor_regions;
  }

  void record_thread_age_table(ageTable* age_table) {
    _survivors_age_table.merge_par(age_table);
  }

  void update_max_gc_locker_expansion();

  // Calculates survivor space parameters.
  void update_survivors_policy();

  virtual void post_heap_initialize();
};

// This should move to some place more general...

// If we have "n" measurements, and we've kept track of their "sum" and the
// "sum_of_squares" of the measurements, this returns the variance of the
// sequence.
inline double variance(int n, double sum_of_squares, double sum) {
  double n_d = (double)n;
  double avg = sum/n_d;
  return (sum_of_squares - 2.0 * avg * sum + n_d * avg * avg) / n_d;
}

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1COLLECTORPOLICY_HPP
