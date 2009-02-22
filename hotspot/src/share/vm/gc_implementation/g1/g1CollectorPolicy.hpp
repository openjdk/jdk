/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// A G1CollectorPolicy makes policy decisions that determine the
// characteristics of the collector.  Examples include:
//   * choice of collection set.
//   * when to collect.

class HeapRegion;
class CollectionSetChooser;

// Yes, this is a bit unpleasant... but it saves replicating the same thing
// over and over again and introducing subtle problems through small typos and
// cutting and pasting mistakes. The macros below introduces a number
// sequnce into the following two classes and the methods that access it.

#define define_num_seq(name)                                                  \
private:                                                                      \
  NumberSeq _all_##name##_times_ms;                                           \
public:                                                                       \
  void record_##name##_time_ms(double ms) {                                   \
    _all_##name##_times_ms.add(ms);                                           \
  }                                                                           \
  NumberSeq* get_##name##_seq() {                                             \
    return &_all_##name##_times_ms;                                           \
  }

class MainBodySummary;
class PopPreambleSummary;

class PauseSummary: public CHeapObj {
  define_num_seq(total)
    define_num_seq(other)

public:
  virtual MainBodySummary*    main_body_summary()    { return NULL; }
  virtual PopPreambleSummary* pop_preamble_summary() { return NULL; }
};

class MainBodySummary: public CHeapObj {
  define_num_seq(satb_drain) // optional
  define_num_seq(parallel) // parallel only
    define_num_seq(ext_root_scan)
    define_num_seq(mark_stack_scan)
    define_num_seq(scan_only)
    define_num_seq(update_rs)
    define_num_seq(scan_rs)
    define_num_seq(scan_new_refs) // Only for temp use; added to
                                  // in parallel case.
    define_num_seq(obj_copy)
    define_num_seq(termination) // parallel only
    define_num_seq(parallel_other) // parallel only
  define_num_seq(mark_closure)
  define_num_seq(clear_ct)  // parallel only
};

class PopPreambleSummary: public CHeapObj {
  define_num_seq(pop_preamble)
    define_num_seq(pop_update_rs)
    define_num_seq(pop_scan_rs)
    define_num_seq(pop_closure_app)
    define_num_seq(pop_evacuation)
    define_num_seq(pop_other)
};

class NonPopSummary: public PauseSummary,
                     public MainBodySummary {
public:
  virtual MainBodySummary*    main_body_summary()    { return this; }
};

class PopSummary: public PauseSummary,
                  public MainBodySummary,
                  public PopPreambleSummary {
public:
  virtual MainBodySummary*    main_body_summary()    { return this; }
  virtual PopPreambleSummary* pop_preamble_summary() { return this; }
};

class NonPopAbandonedSummary: public PauseSummary {
};

class PopAbandonedSummary: public PauseSummary,
                           public PopPreambleSummary {
public:
  virtual PopPreambleSummary* pop_preamble_summary() { return this; }
};

class G1CollectorPolicy: public CollectorPolicy {
protected:
  // The number of pauses during the execution.
  long _n_pauses;

  // either equal to the number of parallel threads, if ParallelGCThreads
  // has been set, or 1 otherwise
  int _parallel_gc_threads;

  enum SomePrivateConstants {
    NumPrevPausesForHeuristics = 10,
    NumPrevGCsForHeuristics = 10,
    NumAPIs = HeapRegion::MaxAge
  };

  G1MMUTracker* _mmu_tracker;

  void initialize_flags();

  void initialize_all() {
    initialize_flags();
    initialize_size_info();
    initialize_perm_generation(PermGen::MarkSweepCompact);
  }

  virtual size_t default_init_heap_size() {
    // Pick some reasonable default.
    return 8*M;
  }


  double _cur_collection_start_sec;
  size_t _cur_collection_pause_used_at_start_bytes;
  size_t _cur_collection_pause_used_regions_at_start;
  size_t _prev_collection_pause_used_at_end_bytes;
  double _cur_collection_par_time_ms;
  double _cur_satb_drain_time_ms;
  double _cur_clear_ct_time_ms;
  bool   _satb_drain_time_set;
  double _cur_popular_preamble_start_ms;
  double _cur_popular_preamble_time_ms;
  double _cur_popular_compute_rc_time_ms;
  double _cur_popular_evac_time_ms;

  double _cur_CH_strong_roots_end_sec;
  double _cur_CH_strong_roots_dur_ms;
  double _cur_G1_strong_roots_end_sec;
  double _cur_G1_strong_roots_dur_ms;

  // Statistics for recent GC pauses.  See below for how indexed.
  TruncatedSeq* _recent_CH_strong_roots_times_ms;
  TruncatedSeq* _recent_G1_strong_roots_times_ms;
  TruncatedSeq* _recent_evac_times_ms;
  // These exclude marking times.
  TruncatedSeq* _recent_pause_times_ms;
  TruncatedSeq* _recent_gc_times_ms;

  TruncatedSeq* _recent_CS_bytes_used_before;
  TruncatedSeq* _recent_CS_bytes_surviving;

  TruncatedSeq* _recent_rs_sizes;

  TruncatedSeq* _concurrent_mark_init_times_ms;
  TruncatedSeq* _concurrent_mark_remark_times_ms;
  TruncatedSeq* _concurrent_mark_cleanup_times_ms;

  NonPopSummary*           _non_pop_summary;
  PopSummary*              _pop_summary;
  NonPopAbandonedSummary*  _non_pop_abandoned_summary;
  PopAbandonedSummary*     _pop_abandoned_summary;

  NumberSeq* _all_pause_times_ms;
  NumberSeq* _all_full_gc_times_ms;
  double _stop_world_start;
  NumberSeq* _all_stop_world_times_ms;
  NumberSeq* _all_yield_times_ms;

  size_t     _region_num_young;
  size_t     _region_num_tenured;
  size_t     _prev_region_num_young;
  size_t     _prev_region_num_tenured;

  NumberSeq* _all_mod_union_times_ms;

  int        _aux_num;
  NumberSeq* _all_aux_times_ms;
  double*    _cur_aux_start_times_ms;
  double*    _cur_aux_times_ms;
  bool*      _cur_aux_times_set;

  double* _par_last_ext_root_scan_times_ms;
  double* _par_last_mark_stack_scan_times_ms;
  double* _par_last_scan_only_times_ms;
  double* _par_last_scan_only_regions_scanned;
  double* _par_last_update_rs_start_times_ms;
  double* _par_last_update_rs_times_ms;
  double* _par_last_update_rs_processed_buffers;
  double* _par_last_scan_rs_start_times_ms;
  double* _par_last_scan_rs_times_ms;
  double* _par_last_scan_new_refs_times_ms;
  double* _par_last_obj_copy_times_ms;
  double* _par_last_termination_times_ms;

  // there are two pases during popular pauses, so we need to store
  // somewhere the results of the first pass
  double* _pop_par_last_update_rs_start_times_ms;
  double* _pop_par_last_update_rs_times_ms;
  double* _pop_par_last_update_rs_processed_buffers;
  double* _pop_par_last_scan_rs_start_times_ms;
  double* _pop_par_last_scan_rs_times_ms;
  double* _pop_par_last_closure_app_times_ms;

  double _pop_compute_rc_start;
  double _pop_evac_start;

  // indicates that we are in young GC mode
  bool _in_young_gc_mode;

  // indicates whether we are in full young or partially young GC mode
  bool _full_young_gcs;

  // if true, then it tries to dynamically adjust the length of the
  // young list
  bool _adaptive_young_list_length;
  size_t _young_list_min_length;
  size_t _young_list_target_length;
  size_t _young_list_so_prefix_length;
  size_t _young_list_fixed_length;

  size_t _young_cset_length;
  bool   _last_young_gc_full;

  double _target_pause_time_ms;

  unsigned              _full_young_pause_num;
  unsigned              _partial_young_pause_num;

  bool                  _during_marking;
  bool                  _in_marking_window;
  bool                  _in_marking_window_im;

  SurvRateGroup*        _short_lived_surv_rate_group;
  SurvRateGroup*        _survivor_surv_rate_group;
  // add here any more surv rate groups

  bool during_marking() {
    return _during_marking;
  }

  // <NEW PREDICTION>

private:
  enum PredictionConstants {
    TruncatedSeqLength = 10
  };

  TruncatedSeq* _alloc_rate_ms_seq;
  double        _prev_collection_pause_end_ms;

  TruncatedSeq* _pending_card_diff_seq;
  TruncatedSeq* _rs_length_diff_seq;
  TruncatedSeq* _cost_per_card_ms_seq;
  TruncatedSeq* _cost_per_scan_only_region_ms_seq;
  TruncatedSeq* _fully_young_cards_per_entry_ratio_seq;
  TruncatedSeq* _partially_young_cards_per_entry_ratio_seq;
  TruncatedSeq* _cost_per_entry_ms_seq;
  TruncatedSeq* _partially_young_cost_per_entry_ms_seq;
  TruncatedSeq* _cost_per_byte_ms_seq;
  TruncatedSeq* _constant_other_time_ms_seq;
  TruncatedSeq* _young_other_cost_per_region_ms_seq;
  TruncatedSeq* _non_young_other_cost_per_region_ms_seq;

  TruncatedSeq* _pending_cards_seq;
  TruncatedSeq* _scanned_cards_seq;
  TruncatedSeq* _rs_lengths_seq;

  TruncatedSeq* _cost_per_byte_ms_during_cm_seq;
  TruncatedSeq* _cost_per_scan_only_region_ms_during_cm_seq;

  TruncatedSeq* _young_gc_eff_seq;

  TruncatedSeq* _max_conc_overhead_seq;

  size_t _recorded_young_regions;
  size_t _recorded_scan_only_regions;
  size_t _recorded_non_young_regions;
  size_t _recorded_region_num;

  size_t _free_regions_at_end_of_collection;
  size_t _scan_only_regions_at_end_of_collection;

  size_t _recorded_rs_lengths;
  size_t _max_rs_lengths;

  size_t _recorded_marked_bytes;
  size_t _recorded_young_bytes;

  size_t _predicted_pending_cards;
  size_t _predicted_cards_scanned;
  size_t _predicted_rs_lengths;
  size_t _predicted_bytes_to_copy;

  double _predicted_survival_ratio;
  double _predicted_rs_update_time_ms;
  double _predicted_rs_scan_time_ms;
  double _predicted_scan_only_scan_time_ms;
  double _predicted_object_copy_time_ms;
  double _predicted_constant_other_time_ms;
  double _predicted_young_other_time_ms;
  double _predicted_non_young_other_time_ms;
  double _predicted_pause_time_ms;

  double _vtime_diff_ms;

  double _recorded_young_free_cset_time_ms;
  double _recorded_non_young_free_cset_time_ms;

  double _sigma;
  double _expensive_region_limit_ms;

  size_t _rs_lengths_prediction;

  size_t _known_garbage_bytes;
  double _known_garbage_ratio;

  double sigma() {
    return _sigma;
  }

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

protected:
  double _pause_time_target_ms;
  double _recorded_young_cset_choice_time_ms;
  double _recorded_non_young_cset_choice_time_ms;
  bool   _within_target;
  size_t _pending_cards;
  size_t _max_pending_cards;

public:

  void set_region_short_lived(HeapRegion* hr) {
    hr->install_surv_rate_group(_short_lived_surv_rate_group);
  }

  void set_region_survivors(HeapRegion* hr) {
    hr->install_surv_rate_group(_survivor_surv_rate_group);
  }

#ifndef PRODUCT
  bool verify_young_ages();
#endif // PRODUCT

  void tag_scan_only(size_t short_lived_scan_only_length);

  double get_new_prediction(TruncatedSeq* seq) {
    return MAX2(seq->davg() + sigma() * seq->dsd(),
                seq->davg() * confidence_factor(seq->num()));
  }

  size_t young_cset_length() {
    return _young_cset_length;
  }

  void record_max_rs_lengths(size_t rs_lengths) {
    _max_rs_lengths = rs_lengths;
  }

  size_t predict_pending_card_diff() {
    double prediction = get_new_neg_prediction(_pending_card_diff_seq);
    if (prediction < 0.00001)
      return 0;
    else
      return (size_t) prediction;
  }

  size_t predict_pending_cards() {
    size_t max_pending_card_num = _g1->max_pending_card_num();
    size_t diff = predict_pending_card_diff();
    size_t prediction;
    if (diff > max_pending_card_num)
      prediction = max_pending_card_num;
    else
      prediction = max_pending_card_num - diff;

    return prediction;
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

  double predict_fully_young_cards_per_entry_ratio() {
    return get_new_prediction(_fully_young_cards_per_entry_ratio_seq);
  }

  double predict_partially_young_cards_per_entry_ratio() {
    if (_partially_young_cards_per_entry_ratio_seq->num() < 2)
      return predict_fully_young_cards_per_entry_ratio();
    else
      return get_new_prediction(_partially_young_cards_per_entry_ratio_seq);
  }

  size_t predict_young_card_num(size_t rs_length) {
    return (size_t) ((double) rs_length *
                     predict_fully_young_cards_per_entry_ratio());
  }

  size_t predict_non_young_card_num(size_t rs_length) {
    return (size_t) ((double) rs_length *
                     predict_partially_young_cards_per_entry_ratio());
  }

  double predict_rs_scan_time_ms(size_t card_num) {
    if (full_young_gcs())
      return (double) card_num * get_new_prediction(_cost_per_entry_ms_seq);
    else
      return predict_partially_young_rs_scan_time_ms(card_num);
  }

  double predict_partially_young_rs_scan_time_ms(size_t card_num) {
    if (_partially_young_cost_per_entry_ms_seq->num() < 3)
      return (double) card_num * get_new_prediction(_cost_per_entry_ms_seq);
    else
      return (double) card_num *
        get_new_prediction(_partially_young_cost_per_entry_ms_seq);
  }

  double predict_scan_only_time_ms_during_cm(size_t scan_only_region_num) {
    if (_cost_per_scan_only_region_ms_during_cm_seq->num() < 3)
      return 1.5 * (double) scan_only_region_num *
        get_new_prediction(_cost_per_scan_only_region_ms_seq);
    else
      return (double) scan_only_region_num *
        get_new_prediction(_cost_per_scan_only_region_ms_during_cm_seq);
  }

  double predict_scan_only_time_ms(size_t scan_only_region_num) {
    if (_in_marking_window_im)
      return predict_scan_only_time_ms_during_cm(scan_only_region_num);
    else
      return (double) scan_only_region_num *
        get_new_prediction(_cost_per_scan_only_region_ms_seq);
  }

  double predict_object_copy_time_ms_during_cm(size_t bytes_to_copy) {
    if (_cost_per_byte_ms_during_cm_seq->num() < 3)
      return 1.1 * (double) bytes_to_copy *
        get_new_prediction(_cost_per_byte_ms_seq);
    else
      return (double) bytes_to_copy *
        get_new_prediction(_cost_per_byte_ms_during_cm_seq);
  }

  double predict_object_copy_time_ms(size_t bytes_to_copy) {
    if (_in_marking_window && !_in_marking_window_im)
      return predict_object_copy_time_ms_during_cm(bytes_to_copy);
    else
      return (double) bytes_to_copy *
        get_new_prediction(_cost_per_byte_ms_seq);
  }

  double predict_constant_other_time_ms() {
    return get_new_prediction(_constant_other_time_ms_seq);
  }

  double predict_young_other_time_ms(size_t young_num) {
    return
      (double) young_num *
      get_new_prediction(_young_other_cost_per_region_ms_seq);
  }

  double predict_non_young_other_time_ms(size_t non_young_num) {
    return
      (double) non_young_num *
      get_new_prediction(_non_young_other_cost_per_region_ms_seq);
  }

  void check_if_region_is_too_expensive(double predicted_time_ms);

  double predict_young_collection_elapsed_time_ms(size_t adjustment);
  double predict_base_elapsed_time_ms(size_t pending_cards);
  double predict_base_elapsed_time_ms(size_t pending_cards,
                                      size_t scanned_cards);
  size_t predict_bytes_to_copy(HeapRegion* hr);
  double predict_region_elapsed_time_ms(HeapRegion* hr, bool young);

  // for use by: calculate_optimal_so_length(length)
  void predict_gc_eff(size_t young_region_num,
                      size_t so_length,
                      double base_time_ms,
                      double *gc_eff,
                      double *pause_time_ms);

  // for use by: calculate_young_list_target_config(rs_length)
  bool predict_gc_eff(size_t young_region_num,
                      size_t so_length,
                      double base_time_with_so_ms,
                      size_t init_free_regions,
                      double target_pause_time_ms,
                      double* gc_eff);

  void start_recording_regions();
  void record_cset_region(HeapRegion* hr, bool young);
  void record_scan_only_regions(size_t scan_only_length);
  void end_recording_regions();

  void record_vtime_diff_ms(double vtime_diff_ms) {
    _vtime_diff_ms = vtime_diff_ms;
  }

  void record_young_free_cset_time_ms(double time_ms) {
    _recorded_young_free_cset_time_ms = time_ms;
  }

  void record_non_young_free_cset_time_ms(double time_ms) {
    _recorded_non_young_free_cset_time_ms = time_ms;
  }

  double predict_young_gc_eff() {
    return get_new_neg_prediction(_young_gc_eff_seq);
  }

  double predict_survivor_regions_evac_time();

  // </NEW PREDICTION>

public:
  void cset_regions_freed() {
    bool propagate = _last_young_gc_full && !_in_marking_window;
    _short_lived_surv_rate_group->all_surviving_words_recorded(propagate);
    _survivor_surv_rate_group->all_surviving_words_recorded(propagate);
    // also call it on any more surv rate groups
  }

  void set_known_garbage_bytes(size_t known_garbage_bytes) {
    _known_garbage_bytes = known_garbage_bytes;
    size_t heap_bytes = _g1->capacity();
    _known_garbage_ratio = (double) _known_garbage_bytes / (double) heap_bytes;
  }

  void decrease_known_garbage_bytes(size_t known_garbage_bytes) {
    guarantee( _known_garbage_bytes >= known_garbage_bytes, "invariant" );

    _known_garbage_bytes -= known_garbage_bytes;
    size_t heap_bytes = _g1->capacity();
    _known_garbage_ratio = (double) _known_garbage_bytes / (double) heap_bytes;
  }

  G1MMUTracker* mmu_tracker() {
    return _mmu_tracker;
  }

  double predict_init_time_ms() {
    return get_new_prediction(_concurrent_mark_init_times_ms);
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

protected:
  void print_stats (int level, const char* str, double value);
  void print_stats (int level, const char* str, int value);
  void print_par_stats (int level, const char* str, double* data) {
    print_par_stats(level, str, data, true);
  }
  void print_par_stats (int level, const char* str, double* data, bool summary);
  void print_par_buffers (int level, const char* str, double* data, bool summary);

  void check_other_times(int level,
                         NumberSeq* other_times_ms,
                         NumberSeq* calc_other_times_ms) const;

  void print_summary (PauseSummary* stats) const;
  void print_abandoned_summary(PauseSummary* non_pop_summary,
                               PauseSummary* pop_summary) const;

  void print_summary (int level, const char* str, NumberSeq* seq) const;
  void print_summary_sd (int level, const char* str, NumberSeq* seq) const;

  double avg_value (double* data);
  double max_value (double* data);
  double sum_of_values (double* data);
  double max_sum (double* data1, double* data2);

  int _last_satb_drain_processed_buffers;
  int _last_update_rs_processed_buffers;
  double _last_pause_time_ms;

  size_t _bytes_in_to_space_before_gc;
  size_t _bytes_in_to_space_after_gc;
  size_t bytes_in_to_space_during_gc() {
    return
      _bytes_in_to_space_after_gc - _bytes_in_to_space_before_gc;
  }
  size_t _bytes_in_collection_set_before_gc;
  // Used to count used bytes in CS.
  friend class CountCSClosure;

  // Statistics kept per GC stoppage, pause or full.
  TruncatedSeq* _recent_prev_end_times_for_all_gcs_sec;

  // We track markings.
  int _num_markings;
  double _mark_thread_startup_sec;       // Time at startup of marking thread

  // Add a new GC of the given duration and end time to the record.
  void update_recent_gc_times(double end_time_sec, double elapsed_ms);

  // The head of the list (via "next_in_collection_set()") representing the
  // current collection set.
  HeapRegion* _collection_set;
  size_t _collection_set_size;
  size_t _collection_set_bytes_used_before;

  // Info about marking.
  int _n_marks; // Sticky at 2, so we know when we've done at least 2.

  // The number of collection pauses at the end of the last mark.
  size_t _n_pauses_at_mark_end;

  // ==== This section is for stats related to starting Conc Refinement on time.
  size_t _conc_refine_enabled;
  size_t _conc_refine_zero_traversals;
  size_t _conc_refine_max_traversals;
  // In # of heap regions.
  size_t _conc_refine_current_delta;

  // At the beginning of a collection pause, update the variables above,
  // especially the "delta".
  void update_conc_refine_data();
  // ====

  // Stash a pointer to the g1 heap.
  G1CollectedHeap* _g1;

  // The average time in ms per collection pause, averaged over recent pauses.
  double recent_avg_time_for_pauses_ms();

  // The average time in ms for processing CollectedHeap strong roots, per
  // collection pause, averaged over recent pauses.
  double recent_avg_time_for_CH_strong_ms();

  // The average time in ms for processing the G1 remembered set, per
  // pause, averaged over recent pauses.
  double recent_avg_time_for_G1_strong_ms();

  // The average time in ms for "evacuating followers", per pause, averaged
  // over recent pauses.
  double recent_avg_time_for_evac_ms();

  // The number of "recent" GCs recorded in the number sequences
  int number_of_recent_gcs();

  // The average survival ratio, computed by the total number of bytes
  // suriviving / total number of bytes before collection over the last
  // several recent pauses.
  double recent_avg_survival_fraction();
  // The survival fraction of the most recent pause; if there have been no
  // pauses, returns 1.0.
  double last_survival_fraction();

  // Returns a "conservative" estimate of the recent survival rate, i.e.,
  // one that may be higher than "recent_avg_survival_fraction".
  // This is conservative in several ways:
  //   If there have been few pauses, it will assume a potential high
  //     variance, and err on the side of caution.
  //   It puts a lower bound (currently 0.1) on the value it will return.
  //   To try to detect phase changes, if the most recent pause ("latest") has a
  //     higher-than average ("avg") survival rate, it returns that rate.
  // "work" version is a utility function; young is restricted to young regions.
  double conservative_avg_survival_fraction_work(double avg,
                                                 double latest);

  // The arguments are the two sequences that keep track of the number of bytes
  //   surviving and the total number of bytes before collection, resp.,
  //   over the last evereal recent pauses
  // Returns the survival rate for the category in the most recent pause.
  // If there have been no pauses, returns 1.0.
  double last_survival_fraction_work(TruncatedSeq* surviving,
                                     TruncatedSeq* before);

  // The arguments are the two sequences that keep track of the number of bytes
  //   surviving and the total number of bytes before collection, resp.,
  //   over the last several recent pauses
  // Returns the average survival ration over the last several recent pauses
  // If there have been no pauses, return 1.0
  double recent_avg_survival_fraction_work(TruncatedSeq* surviving,
                                           TruncatedSeq* before);

  double conservative_avg_survival_fraction() {
    double avg = recent_avg_survival_fraction();
    double latest = last_survival_fraction();
    return conservative_avg_survival_fraction_work(avg, latest);
  }

  // The ratio of gc time to elapsed time, computed over recent pauses.
  double _recent_avg_pause_time_ratio;

  double recent_avg_pause_time_ratio() {
    return _recent_avg_pause_time_ratio;
  }

  // Number of pauses between concurrent marking.
  size_t _pauses_btwn_concurrent_mark;

  size_t _n_marks_since_last_pause;

  // True iff CM has been initiated.
  bool _conc_mark_initiated;

  // True iff CM should be initiated
  bool _should_initiate_conc_mark;
  bool _should_revert_to_full_young_gcs;
  bool _last_full_young_gc;

  // This set of variables tracks the collector efficiency, in order to
  // determine whether we should initiate a new marking.
  double _cur_mark_stop_world_time_ms;
  double _mark_init_start_sec;
  double _mark_remark_start_sec;
  double _mark_cleanup_start_sec;
  double _mark_closure_time_ms;

  void   calculate_young_list_min_length();
  void   calculate_young_list_target_config();
  void   calculate_young_list_target_config(size_t rs_lengths);
  size_t calculate_optimal_so_length(size_t young_list_length);

public:

  G1CollectorPolicy();

  virtual G1CollectorPolicy* as_g1_policy() { return this; }

  virtual CollectorPolicy::Name kind() {
    return CollectorPolicy::G1CollectorPolicyKind;
  }

  void check_prediction_validity();

  size_t bytes_in_collection_set() {
    return _bytes_in_collection_set_before_gc;
  }

  size_t bytes_in_to_space() {
    return bytes_in_to_space_during_gc();
  }

  unsigned calc_gc_alloc_time_stamp() {
    return _all_pause_times_ms->num() + 1;
  }

protected:

  // Count the number of bytes used in the CS.
  void count_CS_bytes_used();

  // Together these do the base cleanup-recording work.  Subclasses might
  // want to put something between them.
  void record_concurrent_mark_cleanup_end_work1(size_t freed_bytes,
                                                size_t max_live_bytes);
  void record_concurrent_mark_cleanup_end_work2();

public:

  virtual void init();

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

  GenRemSet::Name  rem_set_name()     { return GenRemSet::CardTable; }

  // The number of collection pauses so far.
  long n_pauses() const { return _n_pauses; }

  // Update the heuristic info to record a collection pause of the given
  // start time, where the given number of bytes were used at the start.
  // This may involve changing the desired size of a collection set.

  virtual void record_stop_world_start();

  virtual void record_collection_pause_start(double start_time_sec,
                                             size_t start_used);

  virtual void record_popular_pause_preamble_start();
  virtual void record_popular_pause_preamble_end();

  // Must currently be called while the world is stopped.
  virtual void record_concurrent_mark_init_start();
  virtual void record_concurrent_mark_init_end();
  void record_concurrent_mark_init_end_pre(double
                                           mark_init_elapsed_time_ms);

  void record_mark_closure_time(double mark_closure_time_ms);

  virtual void record_concurrent_mark_remark_start();
  virtual void record_concurrent_mark_remark_end();

  virtual void record_concurrent_mark_cleanup_start();
  virtual void record_concurrent_mark_cleanup_end(size_t freed_bytes,
                                                  size_t max_live_bytes);
  virtual void record_concurrent_mark_cleanup_completed();

  virtual void record_concurrent_pause();
  virtual void record_concurrent_pause_end();

  virtual void record_collection_pause_end_CH_strong_roots();
  virtual void record_collection_pause_end_G1_strong_roots();

  virtual void record_collection_pause_end(bool popular, bool abandoned);

  // Record the fact that a full collection occurred.
  virtual void record_full_collection_start();
  virtual void record_full_collection_end();

  void record_ext_root_scan_time(int worker_i, double ms) {
    _par_last_ext_root_scan_times_ms[worker_i] = ms;
  }

  void record_mark_stack_scan_time(int worker_i, double ms) {
    _par_last_mark_stack_scan_times_ms[worker_i] = ms;
  }

  void record_scan_only_time(int worker_i, double ms, int n) {
    _par_last_scan_only_times_ms[worker_i] = ms;
    _par_last_scan_only_regions_scanned[worker_i] = (double) n;
  }

  void record_satb_drain_time(double ms) {
    _cur_satb_drain_time_ms = ms;
    _satb_drain_time_set    = true;
  }

  void record_satb_drain_processed_buffers (int processed_buffers) {
    _last_satb_drain_processed_buffers = processed_buffers;
  }

  void record_mod_union_time(double ms) {
    _all_mod_union_times_ms->add(ms);
  }

  void record_update_rs_start_time(int thread, double ms) {
    _par_last_update_rs_start_times_ms[thread] = ms;
  }

  void record_update_rs_time(int thread, double ms) {
    _par_last_update_rs_times_ms[thread] = ms;
  }

  void record_update_rs_processed_buffers (int thread,
                                           double processed_buffers) {
    _par_last_update_rs_processed_buffers[thread] = processed_buffers;
  }

  void record_scan_rs_start_time(int thread, double ms) {
    _par_last_scan_rs_start_times_ms[thread] = ms;
  }

  void record_scan_rs_time(int thread, double ms) {
    _par_last_scan_rs_times_ms[thread] = ms;
  }

  void record_scan_new_refs_time(int thread, double ms) {
    _par_last_scan_new_refs_times_ms[thread] = ms;
  }

  double get_scan_new_refs_time(int thread) {
    return _par_last_scan_new_refs_times_ms[thread];
  }

  void reset_obj_copy_time(int thread) {
    _par_last_obj_copy_times_ms[thread] = 0.0;
  }

  void reset_obj_copy_time() {
    reset_obj_copy_time(0);
  }

  void record_obj_copy_time(int thread, double ms) {
    _par_last_obj_copy_times_ms[thread] += ms;
  }

  void record_obj_copy_time(double ms) {
    record_obj_copy_time(0, ms);
  }

  void record_termination_time(int thread, double ms) {
    _par_last_termination_times_ms[thread] = ms;
  }

  void record_termination_time(double ms) {
    record_termination_time(0, ms);
  }

  void record_pause_time(double ms) {
    _last_pause_time_ms = ms;
  }

  void record_clear_ct_time(double ms) {
    _cur_clear_ct_time_ms = ms;
  }

  void record_par_time(double ms) {
    _cur_collection_par_time_ms = ms;
  }

  void record_aux_start_time(int i) {
    guarantee(i < _aux_num, "should be within range");
    _cur_aux_start_times_ms[i] = os::elapsedTime() * 1000.0;
  }

  void record_aux_end_time(int i) {
    guarantee(i < _aux_num, "should be within range");
    double ms = os::elapsedTime() * 1000.0 - _cur_aux_start_times_ms[i];
    _cur_aux_times_set[i] = true;
    _cur_aux_times_ms[i] += ms;
  }

  void record_pop_compute_rc_start();
  void record_pop_compute_rc_end();

  void record_pop_evac_start();
  void record_pop_evac_end();

  // Record the fact that "bytes" bytes allocated in a region.
  void record_before_bytes(size_t bytes);
  void record_after_bytes(size_t bytes);

  // Returns "true" if this is a good time to do a collection pause.
  // The "word_size" argument, if non-zero, indicates the size of an
  // allocation request that is prompting this query.
  virtual bool should_do_collection_pause(size_t word_size) = 0;

  // Choose a new collection set.  Marks the chosen regions as being
  // "in_collection_set", and links them together.  The head and number of
  // the collection set are available via access methods.
  // If "pop_region" is non-NULL, it is a popular region that has already
  // been added to the collection set.
  virtual void choose_collection_set(HeapRegion* pop_region = NULL) = 0;

  void clear_collection_set() { _collection_set = NULL; }

  // The head of the list (via "next_in_collection_set()") representing the
  // current collection set.
  HeapRegion* collection_set() { return _collection_set; }

  // Sets the collection set to the given single region.
  virtual void set_single_region_collection_set(HeapRegion* hr);

  // The number of elements in the current collection set.
  size_t collection_set_size() { return _collection_set_size; }

  // Add "hr" to the CS.
  void add_to_collection_set(HeapRegion* hr);

  bool should_initiate_conc_mark()      { return _should_initiate_conc_mark; }
  void set_should_initiate_conc_mark()  { _should_initiate_conc_mark = true; }
  void unset_should_initiate_conc_mark(){ _should_initiate_conc_mark = false; }

  void checkpoint_conc_overhead();

  // If an expansion would be appropriate, because recent GC overhead had
  // exceeded the desired limit, return an amount to expand by.
  virtual size_t expansion_amount();

  // note start of mark thread
  void note_start_of_mark_thread();

  // The marked bytes of the "r" has changed; reclassify it's desirability
  // for marking.  Also asserts that "r" is eligible for a CS.
  virtual void note_change_in_marked_bytes(HeapRegion* r) = 0;

#ifndef PRODUCT
  // Check any appropriate marked bytes info, asserting false if
  // something's wrong, else returning "true".
  virtual bool assertMarkedBytesDataOK() = 0;
#endif

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

  bool should_add_next_region_to_young_list();

  bool in_young_gc_mode() {
    return _in_young_gc_mode;
  }
  void set_in_young_gc_mode(bool in_young_gc_mode) {
    _in_young_gc_mode = in_young_gc_mode;
  }

  bool full_young_gcs() {
    return _full_young_gcs;
  }
  void set_full_young_gcs(bool full_young_gcs) {
    _full_young_gcs = full_young_gcs;
  }

  bool adaptive_young_list_length() {
    return _adaptive_young_list_length;
  }
  void set_adaptive_young_list_length(bool adaptive_young_list_length) {
    _adaptive_young_list_length = adaptive_young_list_length;
  }

  inline double get_gc_eff_factor() {
    double ratio = _known_garbage_ratio;

    double square = ratio * ratio;
    // square = square * square;
    double ret = square * 9.0 + 1.0;
#if 0
    gclog_or_tty->print_cr("ratio = %1.2lf, ret = %1.2lf", ratio, ret);
#endif // 0
    guarantee(0.0 <= ret && ret < 10.0, "invariant!");
    return ret;
  }

  //
  // Survivor regions policy.
  //
protected:

  // Current tenuring threshold, set to 0 if the collector reaches the
  // maximum amount of suvivors regions.
  int _tenuring_threshold;

  // The limit on the number of regions allocated for survivors.
  size_t _max_survivor_regions;

  // The amount of survor regions after a collection.
  size_t _recorded_survivor_regions;
  // List of survivor regions.
  HeapRegion* _recorded_survivor_head;
  HeapRegion* _recorded_survivor_tail;

  ageTable _survivors_age_table;

public:

  inline GCAllocPurpose
    evacuation_destination(HeapRegion* src_region, int age, size_t word_sz) {
      if (age < _tenuring_threshold && src_region->is_young()) {
        return GCAllocForSurvived;
      } else {
        return GCAllocForTenured;
      }
  }

  inline bool track_object_age(GCAllocPurpose purpose) {
    return purpose == GCAllocForSurvived;
  }

  inline GCAllocPurpose alternative_purpose(int purpose) {
    return GCAllocForTenured;
  }

  static const size_t REGIONS_UNLIMITED = ~(size_t)0;

  size_t max_regions(int purpose);

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

  void record_survivor_regions(size_t      regions,
                               HeapRegion* head,
                               HeapRegion* tail) {
    _recorded_survivor_regions = regions;
    _recorded_survivor_head    = head;
    _recorded_survivor_tail    = tail;
  }

  void record_thread_age_table(ageTable* age_table)
  {
    _survivors_age_table.merge_par(age_table);
  }

  // Calculates survivor space parameters.
  void calculate_survivors_policy();

};

// This encapsulates a particular strategy for a g1 Collector.
//
//      Start a concurrent mark when our heap size is n bytes
//            greater then our heap size was at the last concurrent
//            mark.  Where n is a function of the CMSTriggerRatio
//            and the MinHeapFreeRatio.
//
//      Start a g1 collection pause when we have allocated the
//            average number of bytes currently being freed in
//            a collection, but only if it is at least one region
//            full
//
//      Resize Heap based on desired
//      allocation space, where desired allocation space is
//      a function of survival rate and desired future to size.
//
//      Choose collection set by first picking all older regions
//      which have a survival rate which beats our projected young
//      survival rate.  Then fill out the number of needed regions
//      with young regions.

class G1CollectorPolicy_BestRegionsFirst: public G1CollectorPolicy {
  CollectionSetChooser* _collectionSetChooser;
  // If the estimated is less then desirable, resize if possible.
  void expand_if_possible(size_t numRegions);

  virtual void choose_collection_set(HeapRegion* pop_region = NULL);
  virtual void record_collection_pause_start(double start_time_sec,
                                             size_t start_used);
  virtual void record_concurrent_mark_cleanup_end(size_t freed_bytes,
                                                  size_t max_live_bytes);
  virtual void record_full_collection_end();

public:
  G1CollectorPolicy_BestRegionsFirst() {
    _collectionSetChooser = new CollectionSetChooser();
  }
  void record_collection_pause_end(bool popular, bool abandoned);
  bool should_do_collection_pause(size_t word_size);
  virtual void set_single_region_collection_set(HeapRegion* hr);
  // This is not needed any more, after the CSet choosing code was
  // changed to use the pause prediction work. But let's leave the
  // hook in just in case.
  void note_change_in_marked_bytes(HeapRegion* r) { }
#ifndef PRODUCT
  bool assertMarkedBytesDataOK();
#endif
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

// Local Variables: ***
// c-indentation-style: gnu ***
// End: ***
