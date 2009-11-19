/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_g1CollectorPolicy.cpp.incl"

#define PREDICTIONS_VERBOSE 0

// <NEW PREDICTION>

// Different defaults for different number of GC threads
// They were chosen by running GCOld and SPECjbb on debris with different
//   numbers of GC threads and choosing them based on the results

// all the same
static double rs_length_diff_defaults[] = {
  0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
};

static double cost_per_card_ms_defaults[] = {
  0.01, 0.005, 0.005, 0.003, 0.003, 0.002, 0.002, 0.0015
};

static double cost_per_scan_only_region_ms_defaults[] = {
  1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0
};

// all the same
static double fully_young_cards_per_entry_ratio_defaults[] = {
  1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0
};

static double cost_per_entry_ms_defaults[] = {
  0.015, 0.01, 0.01, 0.008, 0.008, 0.0055, 0.0055, 0.005
};

static double cost_per_byte_ms_defaults[] = {
  0.00006, 0.00003, 0.00003, 0.000015, 0.000015, 0.00001, 0.00001, 0.000009
};

// these should be pretty consistent
static double constant_other_time_ms_defaults[] = {
  5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0
};


static double young_other_cost_per_region_ms_defaults[] = {
  0.3, 0.2, 0.2, 0.15, 0.15, 0.12, 0.12, 0.1
};

static double non_young_other_cost_per_region_ms_defaults[] = {
  1.0, 0.7, 0.7, 0.5, 0.5, 0.42, 0.42, 0.30
};

// </NEW PREDICTION>

G1CollectorPolicy::G1CollectorPolicy() :
  _parallel_gc_threads((ParallelGCThreads > 0) ? ParallelGCThreads : 1),
  _n_pauses(0),
  _recent_CH_strong_roots_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),
  _recent_G1_strong_roots_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),
  _recent_evac_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),
  _recent_pause_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),
  _recent_rs_sizes(new TruncatedSeq(NumPrevPausesForHeuristics)),
  _recent_gc_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),
  _all_pause_times_ms(new NumberSeq()),
  _stop_world_start(0.0),
  _all_stop_world_times_ms(new NumberSeq()),
  _all_yield_times_ms(new NumberSeq()),

  _all_mod_union_times_ms(new NumberSeq()),

  _summary(new Summary()),
  _abandoned_summary(new AbandonedSummary()),

#ifndef PRODUCT
  _cur_clear_ct_time_ms(0.0),
  _min_clear_cc_time_ms(-1.0),
  _max_clear_cc_time_ms(-1.0),
  _cur_clear_cc_time_ms(0.0),
  _cum_clear_cc_time_ms(0.0),
  _num_cc_clears(0L),
#endif

  _region_num_young(0),
  _region_num_tenured(0),
  _prev_region_num_young(0),
  _prev_region_num_tenured(0),

  _aux_num(10),
  _all_aux_times_ms(new NumberSeq[_aux_num]),
  _cur_aux_start_times_ms(new double[_aux_num]),
  _cur_aux_times_ms(new double[_aux_num]),
  _cur_aux_times_set(new bool[_aux_num]),

  _concurrent_mark_init_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),
  _concurrent_mark_remark_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),
  _concurrent_mark_cleanup_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),

  // <NEW PREDICTION>

  _alloc_rate_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _prev_collection_pause_end_ms(0.0),
  _pending_card_diff_seq(new TruncatedSeq(TruncatedSeqLength)),
  _rs_length_diff_seq(new TruncatedSeq(TruncatedSeqLength)),
  _cost_per_card_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _cost_per_scan_only_region_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _fully_young_cards_per_entry_ratio_seq(new TruncatedSeq(TruncatedSeqLength)),
  _partially_young_cards_per_entry_ratio_seq(
                                         new TruncatedSeq(TruncatedSeqLength)),
  _cost_per_entry_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _partially_young_cost_per_entry_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _cost_per_byte_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _cost_per_byte_ms_during_cm_seq(new TruncatedSeq(TruncatedSeqLength)),
  _cost_per_scan_only_region_ms_during_cm_seq(new TruncatedSeq(TruncatedSeqLength)),
  _constant_other_time_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _young_other_cost_per_region_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _non_young_other_cost_per_region_ms_seq(
                                         new TruncatedSeq(TruncatedSeqLength)),

  _pending_cards_seq(new TruncatedSeq(TruncatedSeqLength)),
  _scanned_cards_seq(new TruncatedSeq(TruncatedSeqLength)),
  _rs_lengths_seq(new TruncatedSeq(TruncatedSeqLength)),

  _pause_time_target_ms((double) MaxGCPauseMillis),

  // </NEW PREDICTION>

  _in_young_gc_mode(false),
  _full_young_gcs(true),
  _full_young_pause_num(0),
  _partial_young_pause_num(0),

  _during_marking(false),
  _in_marking_window(false),
  _in_marking_window_im(false),

  _known_garbage_ratio(0.0),
  _known_garbage_bytes(0),

  _young_gc_eff_seq(new TruncatedSeq(TruncatedSeqLength)),
  _target_pause_time_ms(-1.0),

   _recent_prev_end_times_for_all_gcs_sec(new TruncatedSeq(NumPrevPausesForHeuristics)),

  _recent_CS_bytes_used_before(new TruncatedSeq(NumPrevPausesForHeuristics)),
  _recent_CS_bytes_surviving(new TruncatedSeq(NumPrevPausesForHeuristics)),

  _recent_avg_pause_time_ratio(0.0),
  _num_markings(0),
  _n_marks(0),
  _n_pauses_at_mark_end(0),

  _all_full_gc_times_ms(new NumberSeq()),

  // G1PausesBtwnConcMark defaults to -1
  // so the hack is to do the cast  QQQ FIXME
  _pauses_btwn_concurrent_mark((size_t)G1PausesBtwnConcMark),
  _n_marks_since_last_pause(0),
  _conc_mark_initiated(false),
  _should_initiate_conc_mark(false),
  _should_revert_to_full_young_gcs(false),
  _last_full_young_gc(false),

  _prev_collection_pause_used_at_end_bytes(0),

  _collection_set(NULL),
#ifdef _MSC_VER // the use of 'this' below gets a warning, make it go away
#pragma warning( disable:4355 ) // 'this' : used in base member initializer list
#endif // _MSC_VER

  _short_lived_surv_rate_group(new SurvRateGroup(this, "Short Lived",
                                                 G1YoungSurvRateNumRegionsSummary)),
  _survivor_surv_rate_group(new SurvRateGroup(this, "Survivor",
                                              G1YoungSurvRateNumRegionsSummary)),
  // add here any more surv rate groups
  _recorded_survivor_regions(0),
  _recorded_survivor_head(NULL),
  _recorded_survivor_tail(NULL),
  _survivors_age_table(true)

{
  // Set up the region size and associated fields. Given that the
  // policy is created before the heap, we have to set this up here,
  // so it's done as soon as possible.
  HeapRegion::setup_heap_region_size(Arguments::min_heap_size());

  _recent_prev_end_times_for_all_gcs_sec->add(os::elapsedTime());
  _prev_collection_pause_end_ms = os::elapsedTime() * 1000.0;

  _par_last_ext_root_scan_times_ms = new double[_parallel_gc_threads];
  _par_last_mark_stack_scan_times_ms = new double[_parallel_gc_threads];
  _par_last_scan_only_times_ms = new double[_parallel_gc_threads];
  _par_last_scan_only_regions_scanned = new double[_parallel_gc_threads];

  _par_last_update_rs_start_times_ms = new double[_parallel_gc_threads];
  _par_last_update_rs_times_ms = new double[_parallel_gc_threads];
  _par_last_update_rs_processed_buffers = new double[_parallel_gc_threads];

  _par_last_scan_rs_start_times_ms = new double[_parallel_gc_threads];
  _par_last_scan_rs_times_ms = new double[_parallel_gc_threads];
  _par_last_scan_new_refs_times_ms = new double[_parallel_gc_threads];

  _par_last_obj_copy_times_ms = new double[_parallel_gc_threads];

  _par_last_termination_times_ms = new double[_parallel_gc_threads];

  // start conservatively
  _expensive_region_limit_ms = 0.5 * (double) MaxGCPauseMillis;

  // <NEW PREDICTION>

  int index;
  if (ParallelGCThreads == 0)
    index = 0;
  else if (ParallelGCThreads > 8)
    index = 7;
  else
    index = ParallelGCThreads - 1;

  _pending_card_diff_seq->add(0.0);
  _rs_length_diff_seq->add(rs_length_diff_defaults[index]);
  _cost_per_card_ms_seq->add(cost_per_card_ms_defaults[index]);
  _cost_per_scan_only_region_ms_seq->add(
                                 cost_per_scan_only_region_ms_defaults[index]);
  _fully_young_cards_per_entry_ratio_seq->add(
                            fully_young_cards_per_entry_ratio_defaults[index]);
  _cost_per_entry_ms_seq->add(cost_per_entry_ms_defaults[index]);
  _cost_per_byte_ms_seq->add(cost_per_byte_ms_defaults[index]);
  _constant_other_time_ms_seq->add(constant_other_time_ms_defaults[index]);
  _young_other_cost_per_region_ms_seq->add(
                               young_other_cost_per_region_ms_defaults[index]);
  _non_young_other_cost_per_region_ms_seq->add(
                           non_young_other_cost_per_region_ms_defaults[index]);

  // </NEW PREDICTION>

  double time_slice  = (double) GCPauseIntervalMillis / 1000.0;
  double max_gc_time = (double) MaxGCPauseMillis / 1000.0;
  guarantee(max_gc_time < time_slice,
            "Max GC time should not be greater than the time slice");
  _mmu_tracker = new G1MMUTrackerQueue(time_slice, max_gc_time);
  _sigma = (double) G1ConfidencePercent / 100.0;

  // start conservatively (around 50ms is about right)
  _concurrent_mark_init_times_ms->add(0.05);
  _concurrent_mark_remark_times_ms->add(0.05);
  _concurrent_mark_cleanup_times_ms->add(0.20);
  _tenuring_threshold = MaxTenuringThreshold;

  if (G1UseSurvivorSpaces) {
    // if G1FixedSurvivorSpaceSize is 0 which means the size is not
    // fixed, then _max_survivor_regions will be calculated at
    // calculate_young_list_target_config during initialization
    _max_survivor_regions = G1FixedSurvivorSpaceSize / HeapRegion::GrainBytes;
  } else {
    _max_survivor_regions = 0;
  }

  initialize_all();
}

// Increment "i", mod "len"
static void inc_mod(int& i, int len) {
  i++; if (i == len) i = 0;
}

void G1CollectorPolicy::initialize_flags() {
  set_min_alignment(HeapRegion::GrainBytes);
  set_max_alignment(GenRemSet::max_alignment_constraint(rem_set_name()));
  if (SurvivorRatio < 1) {
    vm_exit_during_initialization("Invalid survivor ratio specified");
  }
  CollectorPolicy::initialize_flags();
}

void G1CollectorPolicy::init() {
  // Set aside an initial future to_space.
  _g1 = G1CollectedHeap::heap();
  size_t regions = Universe::heap()->capacity() / HeapRegion::GrainBytes;

  assert(Heap_lock->owned_by_self(), "Locking discipline.");

  if (G1SteadyStateUsed < 50) {
    vm_exit_during_initialization("G1SteadyStateUsed must be at least 50%.");
  }

  initialize_gc_policy_counters();

  if (G1Gen) {
    _in_young_gc_mode = true;

    if (G1YoungGenSize == 0) {
      set_adaptive_young_list_length(true);
      _young_list_fixed_length = 0;
    } else {
      set_adaptive_young_list_length(false);
      _young_list_fixed_length = (G1YoungGenSize / HeapRegion::GrainBytes);
    }
     _free_regions_at_end_of_collection = _g1->free_regions();
     _scan_only_regions_at_end_of_collection = 0;
     calculate_young_list_min_length();
     guarantee( _young_list_min_length == 0, "invariant, not enough info" );
     calculate_young_list_target_config();
   } else {
     _young_list_fixed_length = 0;
    _in_young_gc_mode = false;
  }
}

// Create the jstat counters for the policy.
void G1CollectorPolicy::initialize_gc_policy_counters()
{
  _gc_policy_counters = new GCPolicyCounters("GarbageFirst", 1, 2 + G1Gen);
}

void G1CollectorPolicy::calculate_young_list_min_length() {
  _young_list_min_length = 0;

  if (!adaptive_young_list_length())
    return;

  if (_alloc_rate_ms_seq->num() > 3) {
    double now_sec = os::elapsedTime();
    double when_ms = _mmu_tracker->when_max_gc_sec(now_sec) * 1000.0;
    double alloc_rate_ms = predict_alloc_rate_ms();
    int min_regions = (int) ceil(alloc_rate_ms * when_ms);
    int current_region_num = (int) _g1->young_list_length();
    _young_list_min_length = min_regions + current_region_num;
  }
}

void G1CollectorPolicy::calculate_young_list_target_config() {
  if (adaptive_young_list_length()) {
    size_t rs_lengths = (size_t) get_new_prediction(_rs_lengths_seq);
    calculate_young_list_target_config(rs_lengths);
  } else {
    if (full_young_gcs())
      _young_list_target_length = _young_list_fixed_length;
    else
      _young_list_target_length = _young_list_fixed_length / 2;
    _young_list_target_length = MAX2(_young_list_target_length, (size_t)1);
    size_t so_length = calculate_optimal_so_length(_young_list_target_length);
    guarantee( so_length < _young_list_target_length, "invariant" );
    _young_list_so_prefix_length = so_length;
  }
  calculate_survivors_policy();
}

// This method calculate the optimal scan-only set for a fixed young
// gen size. I couldn't work out how to reuse the more elaborate one,
// i.e. calculate_young_list_target_config(rs_length), as the loops are
// fundamentally different (the other one finds a config for different
// S-O lengths, whereas here we need to do the opposite).
size_t G1CollectorPolicy::calculate_optimal_so_length(
                                                    size_t young_list_length) {
  if (!G1UseScanOnlyPrefix)
    return 0;

  if (_all_pause_times_ms->num() < 3) {
    // we won't use a scan-only set at the beginning to allow the rest
    // of the predictors to warm up
    return 0;
  }

  if (_cost_per_scan_only_region_ms_seq->num() < 3) {
    // then, we'll only set the S-O set to 1 for a little bit of time,
    // to get enough information on the scanning cost
    return 1;
  }

  size_t pending_cards = (size_t) get_new_prediction(_pending_cards_seq);
  size_t rs_lengths = (size_t) get_new_prediction(_rs_lengths_seq);
  size_t adj_rs_lengths = rs_lengths + predict_rs_length_diff();
  size_t scanned_cards;
  if (full_young_gcs())
    scanned_cards = predict_young_card_num(adj_rs_lengths);
  else
    scanned_cards = predict_non_young_card_num(adj_rs_lengths);
  double base_time_ms = predict_base_elapsed_time_ms(pending_cards,
                                                     scanned_cards);

  size_t so_length = 0;
  double max_gc_eff = 0.0;
  for (size_t i = 0; i < young_list_length; ++i) {
    double gc_eff = 0.0;
    double pause_time_ms = 0.0;
    predict_gc_eff(young_list_length, i, base_time_ms,
                   &gc_eff, &pause_time_ms);
    if (gc_eff > max_gc_eff) {
      max_gc_eff = gc_eff;
      so_length = i;
    }
  }

  // set it to 95% of the optimal to make sure we sample the "area"
  // around the optimal length to get up-to-date survival rate data
  return so_length * 950 / 1000;
}

// This is a really cool piece of code! It finds the best
// target configuration (young length / scan-only prefix length) so
// that GC efficiency is maximized and that we also meet a pause
// time. It's a triple nested loop. These loops are explained below
// from the inside-out :-)
//
// (a) The innermost loop will try to find the optimal young length
// for a fixed S-O length. It uses a binary search to speed up the
// process. We assume that, for a fixed S-O length, as we add more
// young regions to the CSet, the GC efficiency will only go up (I'll
// skip the proof). So, using a binary search to optimize this process
// makes perfect sense.
//
// (b) The middle loop will fix the S-O length before calling the
// innermost one. It will vary it between two parameters, increasing
// it by a given increment.
//
// (c) The outermost loop will call the middle loop three times.
//   (1) The first time it will explore all possible S-O length values
//   from 0 to as large as it can get, using a coarse increment (to
//   quickly "home in" to where the optimal seems to be).
//   (2) The second time it will explore the values around the optimal
//   that was found by the first iteration using a fine increment.
//   (3) Once the optimal config has been determined by the second
//   iteration, we'll redo the calculation, but setting the S-O length
//   to 95% of the optimal to make sure we sample the "area"
//   around the optimal length to get up-to-date survival rate data
//
// Termination conditions for the iterations are several: the pause
// time is over the limit, we do not have enough to-space, etc.

void G1CollectorPolicy::calculate_young_list_target_config(size_t rs_lengths) {
  guarantee( adaptive_young_list_length(), "pre-condition" );

  double start_time_sec = os::elapsedTime();
  size_t min_reserve_perc = MAX2((size_t)2, (size_t)G1MinReservePercent);
  min_reserve_perc = MIN2((size_t) 50, min_reserve_perc);
  size_t reserve_regions =
    (size_t) ((double) min_reserve_perc * (double) _g1->n_regions() / 100.0);

  if (full_young_gcs() && _free_regions_at_end_of_collection > 0) {
    // we are in fully-young mode and there are free regions in the heap

    double survivor_regions_evac_time =
        predict_survivor_regions_evac_time();

    size_t min_so_length = 0;
    size_t max_so_length = 0;

    if (G1UseScanOnlyPrefix) {
      if (_all_pause_times_ms->num() < 3) {
        // we won't use a scan-only set at the beginning to allow the rest
        // of the predictors to warm up
        min_so_length = 0;
        max_so_length = 0;
      } else if (_cost_per_scan_only_region_ms_seq->num() < 3) {
        // then, we'll only set the S-O set to 1 for a little bit of time,
        // to get enough information on the scanning cost
        min_so_length = 1;
        max_so_length = 1;
      } else if (_in_marking_window || _last_full_young_gc) {
        // no S-O prefix during a marking phase either, as at the end
        // of the marking phase we'll have to use a very small young
        // length target to fill up the rest of the CSet with
        // non-young regions and, if we have lots of scan-only regions
        // left-over, we will not be able to add any more non-young
        // regions.
        min_so_length = 0;
        max_so_length = 0;
      } else {
        // this is the common case; we'll never reach the maximum, we
        // one of the end conditions will fire well before that
        // (hopefully!)
        min_so_length = 0;
        max_so_length = _free_regions_at_end_of_collection - 1;
      }
    } else {
      // no S-O prefix, as the switch is not set, but we still need to
      // do one iteration to calculate the best young target that
      // meets the pause time; this way we reuse the same code instead
      // of replicating it
      min_so_length = 0;
      max_so_length = 0;
    }

    double target_pause_time_ms = _mmu_tracker->max_gc_time() * 1000.0;
    size_t pending_cards = (size_t) get_new_prediction(_pending_cards_seq);
    size_t adj_rs_lengths = rs_lengths + predict_rs_length_diff();
    size_t scanned_cards;
    if (full_young_gcs())
      scanned_cards = predict_young_card_num(adj_rs_lengths);
    else
      scanned_cards = predict_non_young_card_num(adj_rs_lengths);
    // calculate this once, so that we don't have to recalculate it in
    // the innermost loop
    double base_time_ms = predict_base_elapsed_time_ms(pending_cards, scanned_cards)
                          + survivor_regions_evac_time;
    // the result
    size_t final_young_length = 0;
    size_t final_so_length = 0;
    double final_gc_eff = 0.0;
    // we'll also keep track of how many times we go into the inner loop
    // this is for profiling reasons
    size_t calculations = 0;

    // this determines which of the three iterations the outer loop is in
    typedef enum {
      pass_type_coarse,
      pass_type_fine,
      pass_type_final
    } pass_type_t;

    // range of the outer loop's iteration
    size_t from_so_length   = min_so_length;
    size_t to_so_length     = max_so_length;
    guarantee( from_so_length <= to_so_length, "invariant" );

    // this will keep the S-O length that's found by the second
    // iteration of the outer loop; we'll keep it just in case the third
    // iteration fails to find something
    size_t fine_so_length   = 0;

    // the increment step for the coarse (first) iteration
    size_t so_coarse_increments = 5;

    // the common case, we'll start with the coarse iteration
    pass_type_t pass = pass_type_coarse;
    size_t so_length_incr = so_coarse_increments;

    if (from_so_length == to_so_length) {
      // not point in doing the coarse iteration, we'll go directly into
      // the fine one (we essentially trying to find the optimal young
      // length for a fixed S-O length).
      so_length_incr = 1;
      pass = pass_type_final;
    } else if (to_so_length - from_so_length < 3 * so_coarse_increments) {
      // again, the range is too short so no point in foind the coarse
      // iteration either
      so_length_incr = 1;
      pass = pass_type_fine;
    }

    bool done = false;
    // this is the outermost loop
    while (!done) {
#ifdef TRACE_CALC_YOUNG_CONFIG
      // leave this in for debugging, just in case
      gclog_or_tty->print_cr("searching between " SIZE_FORMAT " and " SIZE_FORMAT
                             ", incr " SIZE_FORMAT ", pass %s",
                             from_so_length, to_so_length, so_length_incr,
                             (pass == pass_type_coarse) ? "coarse" :
                             (pass == pass_type_fine) ? "fine" : "final");
#endif // TRACE_CALC_YOUNG_CONFIG

      size_t so_length = from_so_length;
      size_t init_free_regions =
        MAX2((size_t)0,
             _free_regions_at_end_of_collection +
             _scan_only_regions_at_end_of_collection - reserve_regions);

      // this determines whether a configuration was found
      bool gc_eff_set = false;
      // this is the middle loop
      while (so_length <= to_so_length) {
        // base time, which excludes region-related time; again we
        // calculate it once to avoid recalculating it in the
        // innermost loop
        double base_time_with_so_ms =
                           base_time_ms + predict_scan_only_time_ms(so_length);
        // it's already over the pause target, go around
        if (base_time_with_so_ms > target_pause_time_ms)
          break;

        size_t starting_young_length = so_length+1;

        // we make sure that the short young length that makes sense
        // (one more than the S-O length) is feasible
        size_t min_young_length = starting_young_length;
        double min_gc_eff;
        bool min_ok;
        ++calculations;
        min_ok = predict_gc_eff(min_young_length, so_length,
                                base_time_with_so_ms,
                                init_free_regions, target_pause_time_ms,
                                &min_gc_eff);

        if (min_ok) {
          // the shortest young length is indeed feasible; we'll know
          // set up the max young length and we'll do a binary search
          // between min_young_length and max_young_length
          size_t max_young_length = _free_regions_at_end_of_collection - 1;
          double max_gc_eff = 0.0;
          bool max_ok = false;

          // the innermost loop! (finally!)
          while (max_young_length > min_young_length) {
            // we'll make sure that min_young_length is always at a
            // feasible config
            guarantee( min_ok, "invariant" );

            ++calculations;
            max_ok = predict_gc_eff(max_young_length, so_length,
                                    base_time_with_so_ms,
                                    init_free_regions, target_pause_time_ms,
                                    &max_gc_eff);

            size_t diff = (max_young_length - min_young_length) / 2;
            if (max_ok) {
              min_young_length = max_young_length;
              min_gc_eff = max_gc_eff;
              min_ok = true;
            }
            max_young_length = min_young_length + diff;
          }

          // the innermost loop found a config
          guarantee( min_ok, "invariant" );
          if (min_gc_eff > final_gc_eff) {
            // it's the best config so far, so we'll keep it
            final_gc_eff = min_gc_eff;
            final_young_length = min_young_length;
            final_so_length = so_length;
            gc_eff_set = true;
          }
        }

        // incremental the fixed S-O length and go around
        so_length += so_length_incr;
      }

      // this is the end of the outermost loop and we need to decide
      // what to do during the next iteration
      if (pass == pass_type_coarse) {
        // we just did the coarse pass (first iteration)

        if (!gc_eff_set)
          // we didn't find a feasible config so we'll just bail out; of
          // course, it might be the case that we missed it; but I'd say
          // it's a bit unlikely
          done = true;
        else {
          // We did find a feasible config with optimal GC eff during
          // the first pass. So the second pass we'll only consider the
          // S-O lengths around that config with a fine increment.

          guarantee( so_length_incr == so_coarse_increments, "invariant" );
          guarantee( final_so_length >= min_so_length, "invariant" );

#ifdef TRACE_CALC_YOUNG_CONFIG
          // leave this in for debugging, just in case
          gclog_or_tty->print_cr("  coarse pass: SO length " SIZE_FORMAT,
                                 final_so_length);
#endif // TRACE_CALC_YOUNG_CONFIG

          from_so_length =
            (final_so_length - min_so_length > so_coarse_increments) ?
            final_so_length - so_coarse_increments + 1 : min_so_length;
          to_so_length =
            (max_so_length - final_so_length > so_coarse_increments) ?
            final_so_length + so_coarse_increments - 1 : max_so_length;

          pass = pass_type_fine;
          so_length_incr = 1;
        }
      } else if (pass == pass_type_fine) {
        // we just finished the second pass

        if (!gc_eff_set) {
          // we didn't find a feasible config (yes, it's possible;
          // notice that, sometimes, we go directly into the fine
          // iteration and skip the coarse one) so we bail out
          done = true;
        } else {
          // We did find a feasible config with optimal GC eff
          guarantee( so_length_incr == 1, "invariant" );

          if (final_so_length == 0) {
            // The config is of an empty S-O set, so we'll just bail out
            done = true;
          } else {
            // we'll go around once more, setting the S-O length to 95%
            // of the optimal
            size_t new_so_length = 950 * final_so_length / 1000;

#ifdef TRACE_CALC_YOUNG_CONFIG
            // leave this in for debugging, just in case
            gclog_or_tty->print_cr("  fine pass: SO length " SIZE_FORMAT
                                   ", setting it to " SIZE_FORMAT,
                                    final_so_length, new_so_length);
#endif // TRACE_CALC_YOUNG_CONFIG

            from_so_length = new_so_length;
            to_so_length = new_so_length;
            fine_so_length = final_so_length;

            pass = pass_type_final;
          }
        }
      } else if (pass == pass_type_final) {
        // we just finished the final (third) pass

        if (!gc_eff_set)
          // we didn't find a feasible config, so we'll just use the one
          // we found during the second pass, which we saved
          final_so_length = fine_so_length;

        // and we're done!
        done = true;
      } else {
        guarantee( false, "should never reach here" );
      }

      // we now go around the outermost loop
    }

    // we should have at least one region in the target young length
    _young_list_target_length =
        MAX2((size_t) 1, final_young_length + _recorded_survivor_regions);
    if (final_so_length >= final_young_length)
      // and we need to ensure that the S-O length is not greater than
      // the target young length (this is being a bit careful)
      final_so_length = 0;
    _young_list_so_prefix_length = final_so_length;
    guarantee( !_in_marking_window || !_last_full_young_gc ||
               _young_list_so_prefix_length == 0, "invariant" );

    // let's keep an eye of how long we spend on this calculation
    // right now, I assume that we'll print it when we need it; we
    // should really adde it to the breakdown of a pause
    double end_time_sec = os::elapsedTime();
    double elapsed_time_ms = (end_time_sec - start_time_sec) * 1000.0;

#ifdef TRACE_CALC_YOUNG_CONFIG
    // leave this in for debugging, just in case
    gclog_or_tty->print_cr("target = %1.1lf ms, young = " SIZE_FORMAT
                           ", SO = " SIZE_FORMAT ", "
                           "elapsed %1.2lf ms, calcs: " SIZE_FORMAT " (%s%s) "
                           SIZE_FORMAT SIZE_FORMAT,
                           target_pause_time_ms,
                           _young_list_target_length - _young_list_so_prefix_length,
                           _young_list_so_prefix_length,
                           elapsed_time_ms,
                           calculations,
                           full_young_gcs() ? "full" : "partial",
                           should_initiate_conc_mark() ? " i-m" : "",
                           _in_marking_window,
                           _in_marking_window_im);
#endif // TRACE_CALC_YOUNG_CONFIG

    if (_young_list_target_length < _young_list_min_length) {
      // bummer; this means that, if we do a pause when the optimal
      // config dictates, we'll violate the pause spacing target (the
      // min length was calculate based on the application's current
      // alloc rate);

      // so, we have to bite the bullet, and allocate the minimum
      // number. We'll violate our target, but we just can't meet it.

      size_t so_length = 0;
      // a note further up explains why we do not want an S-O length
      // during marking
      if (!_in_marking_window && !_last_full_young_gc)
        // but we can still try to see whether we can find an optimal
        // S-O length
        so_length = calculate_optimal_so_length(_young_list_min_length);

#ifdef TRACE_CALC_YOUNG_CONFIG
      // leave this in for debugging, just in case
      gclog_or_tty->print_cr("adjusted target length from "
                             SIZE_FORMAT " to " SIZE_FORMAT
                             ", SO " SIZE_FORMAT,
                             _young_list_target_length, _young_list_min_length,
                             so_length);
#endif // TRACE_CALC_YOUNG_CONFIG

      _young_list_target_length =
        MAX2(_young_list_min_length, (size_t)1);
      _young_list_so_prefix_length = so_length;
    }
  } else {
    // we are in a partially-young mode or we've run out of regions (due
    // to evacuation failure)

#ifdef TRACE_CALC_YOUNG_CONFIG
    // leave this in for debugging, just in case
    gclog_or_tty->print_cr("(partial) setting target to " SIZE_FORMAT
                           ", SO " SIZE_FORMAT,
                           _young_list_min_length, 0);
#endif // TRACE_CALC_YOUNG_CONFIG

    // we'll do the pause as soon as possible and with no S-O prefix
    // (see above for the reasons behind the latter)
    _young_list_target_length =
      MAX2(_young_list_min_length, (size_t) 1);
    _young_list_so_prefix_length = 0;
  }

  _rs_lengths_prediction = rs_lengths;
}

// This is used by: calculate_optimal_so_length(length). It returns
// the GC eff and predicted pause time for a particular config
void
G1CollectorPolicy::predict_gc_eff(size_t young_length,
                                  size_t so_length,
                                  double base_time_ms,
                                  double* ret_gc_eff,
                                  double* ret_pause_time_ms) {
  double so_time_ms = predict_scan_only_time_ms(so_length);
  double accum_surv_rate_adj = 0.0;
  if (so_length > 0)
    accum_surv_rate_adj = accum_yg_surv_rate_pred((int)(so_length - 1));
  double accum_surv_rate =
    accum_yg_surv_rate_pred((int)(young_length - 1)) - accum_surv_rate_adj;
  size_t bytes_to_copy =
    (size_t) (accum_surv_rate * (double) HeapRegion::GrainBytes);
  double copy_time_ms = predict_object_copy_time_ms(bytes_to_copy);
  double young_other_time_ms =
                       predict_young_other_time_ms(young_length - so_length);
  double pause_time_ms =
                base_time_ms + so_time_ms + copy_time_ms + young_other_time_ms;
  size_t reclaimed_bytes =
    (young_length - so_length) * HeapRegion::GrainBytes - bytes_to_copy;
  double gc_eff = (double) reclaimed_bytes / pause_time_ms;

  *ret_gc_eff = gc_eff;
  *ret_pause_time_ms = pause_time_ms;
}

// This is used by: calculate_young_list_target_config(rs_length). It
// returns the GC eff of a particular config. It returns false if that
// config violates any of the end conditions of the search in the
// calling method, or true upon success. The end conditions were put
// here since it's called twice and it was best not to replicate them
// in the caller. Also, passing the parameteres avoids having to
// recalculate them in the innermost loop.
bool
G1CollectorPolicy::predict_gc_eff(size_t young_length,
                                  size_t so_length,
                                  double base_time_with_so_ms,
                                  size_t init_free_regions,
                                  double target_pause_time_ms,
                                  double* ret_gc_eff) {
  *ret_gc_eff = 0.0;

  if (young_length >= init_free_regions)
    // end condition 1: not enough space for the young regions
    return false;

  double accum_surv_rate_adj = 0.0;
  if (so_length > 0)
    accum_surv_rate_adj = accum_yg_surv_rate_pred((int)(so_length - 1));
  double accum_surv_rate =
    accum_yg_surv_rate_pred((int)(young_length - 1)) - accum_surv_rate_adj;
  size_t bytes_to_copy =
    (size_t) (accum_surv_rate * (double) HeapRegion::GrainBytes);
  double copy_time_ms = predict_object_copy_time_ms(bytes_to_copy);
  double young_other_time_ms =
                       predict_young_other_time_ms(young_length - so_length);
  double pause_time_ms =
                   base_time_with_so_ms + copy_time_ms + young_other_time_ms;

  if (pause_time_ms > target_pause_time_ms)
    // end condition 2: over the target pause time
    return false;

  size_t reclaimed_bytes =
    (young_length - so_length) * HeapRegion::GrainBytes - bytes_to_copy;
  size_t free_bytes =
                 (init_free_regions - young_length) * HeapRegion::GrainBytes;

  if ((2.0 + sigma()) * (double) bytes_to_copy > (double) free_bytes)
    // end condition 3: out of to-space (conservatively)
    return false;

  // success!
  double gc_eff = (double) reclaimed_bytes / pause_time_ms;
  *ret_gc_eff = gc_eff;

  return true;
}

double G1CollectorPolicy::predict_survivor_regions_evac_time() {
  double survivor_regions_evac_time = 0.0;
  for (HeapRegion * r = _recorded_survivor_head;
       r != NULL && r != _recorded_survivor_tail->get_next_young_region();
       r = r->get_next_young_region()) {
    survivor_regions_evac_time += predict_region_elapsed_time_ms(r, true);
  }
  return survivor_regions_evac_time;
}

void G1CollectorPolicy::check_prediction_validity() {
  guarantee( adaptive_young_list_length(), "should not call this otherwise" );

  size_t rs_lengths = _g1->young_list_sampled_rs_lengths();
  if (rs_lengths > _rs_lengths_prediction) {
    // add 10% to avoid having to recalculate often
    size_t rs_lengths_prediction = rs_lengths * 1100 / 1000;
    calculate_young_list_target_config(rs_lengths_prediction);
  }
}

HeapWord* G1CollectorPolicy::mem_allocate_work(size_t size,
                                               bool is_tlab,
                                               bool* gc_overhead_limit_was_exceeded) {
  guarantee(false, "Not using this policy feature yet.");
  return NULL;
}

// This method controls how a collector handles one or more
// of its generations being fully allocated.
HeapWord* G1CollectorPolicy::satisfy_failed_allocation(size_t size,
                                                       bool is_tlab) {
  guarantee(false, "Not using this policy feature yet.");
  return NULL;
}


#ifndef PRODUCT
bool G1CollectorPolicy::verify_young_ages() {
  HeapRegion* head = _g1->young_list_first_region();
  return
    verify_young_ages(head, _short_lived_surv_rate_group);
  // also call verify_young_ages on any additional surv rate groups
}

bool
G1CollectorPolicy::verify_young_ages(HeapRegion* head,
                                     SurvRateGroup *surv_rate_group) {
  guarantee( surv_rate_group != NULL, "pre-condition" );

  const char* name = surv_rate_group->name();
  bool ret = true;
  int prev_age = -1;

  for (HeapRegion* curr = head;
       curr != NULL;
       curr = curr->get_next_young_region()) {
    SurvRateGroup* group = curr->surv_rate_group();
    if (group == NULL && !curr->is_survivor()) {
      gclog_or_tty->print_cr("## %s: encountered NULL surv_rate_group", name);
      ret = false;
    }

    if (surv_rate_group == group) {
      int age = curr->age_in_surv_rate_group();

      if (age < 0) {
        gclog_or_tty->print_cr("## %s: encountered negative age", name);
        ret = false;
      }

      if (age <= prev_age) {
        gclog_or_tty->print_cr("## %s: region ages are not strictly increasing "
                               "(%d, %d)", name, age, prev_age);
        ret = false;
      }
      prev_age = age;
    }
  }

  return ret;
}
#endif // PRODUCT

void G1CollectorPolicy::record_full_collection_start() {
  _cur_collection_start_sec = os::elapsedTime();
  // Release the future to-space so that it is available for compaction into.
  _g1->set_full_collection();
}

void G1CollectorPolicy::record_full_collection_end() {
  // Consider this like a collection pause for the purposes of allocation
  // since last pause.
  double end_sec = os::elapsedTime();
  double full_gc_time_sec = end_sec - _cur_collection_start_sec;
  double full_gc_time_ms = full_gc_time_sec * 1000.0;

  _all_full_gc_times_ms->add(full_gc_time_ms);

  update_recent_gc_times(end_sec, full_gc_time_ms);

  _g1->clear_full_collection();

  // "Nuke" the heuristics that control the fully/partially young GC
  // transitions and make sure we start with fully young GCs after the
  // Full GC.
  set_full_young_gcs(true);
  _last_full_young_gc = false;
  _should_revert_to_full_young_gcs = false;
  _should_initiate_conc_mark = false;
  _known_garbage_bytes = 0;
  _known_garbage_ratio = 0.0;
  _in_marking_window = false;
  _in_marking_window_im = false;

  _short_lived_surv_rate_group->record_scan_only_prefix(0);
  _short_lived_surv_rate_group->start_adding_regions();
  // also call this on any additional surv rate groups

  record_survivor_regions(0, NULL, NULL);

  _prev_region_num_young   = _region_num_young;
  _prev_region_num_tenured = _region_num_tenured;

  _free_regions_at_end_of_collection = _g1->free_regions();
  _scan_only_regions_at_end_of_collection = 0;
  // Reset survivors SurvRateGroup.
  _survivor_surv_rate_group->reset();
  calculate_young_list_min_length();
  calculate_young_list_target_config();
 }

void G1CollectorPolicy::record_before_bytes(size_t bytes) {
  _bytes_in_to_space_before_gc += bytes;
}

void G1CollectorPolicy::record_after_bytes(size_t bytes) {
  _bytes_in_to_space_after_gc += bytes;
}

void G1CollectorPolicy::record_stop_world_start() {
  _stop_world_start = os::elapsedTime();
}

void G1CollectorPolicy::record_collection_pause_start(double start_time_sec,
                                                      size_t start_used) {
  if (PrintGCDetails) {
    gclog_or_tty->stamp(PrintGCTimeStamps);
    gclog_or_tty->print("[GC pause");
    if (in_young_gc_mode())
      gclog_or_tty->print(" (%s)", full_young_gcs() ? "young" : "partial");
  }

  assert(_g1->used_regions() == _g1->recalculate_used_regions(),
         "sanity");
  assert(_g1->used() == _g1->recalculate_used(), "sanity");

  double s_w_t_ms = (start_time_sec - _stop_world_start) * 1000.0;
  _all_stop_world_times_ms->add(s_w_t_ms);
  _stop_world_start = 0.0;

  _cur_collection_start_sec = start_time_sec;
  _cur_collection_pause_used_at_start_bytes = start_used;
  _cur_collection_pause_used_regions_at_start = _g1->used_regions();
  _pending_cards = _g1->pending_card_num();
  _max_pending_cards = _g1->max_pending_card_num();

  _bytes_in_to_space_before_gc = 0;
  _bytes_in_to_space_after_gc = 0;
  _bytes_in_collection_set_before_gc = 0;

#ifdef DEBUG
  // initialise these to something well known so that we can spot
  // if they are not set properly

  for (int i = 0; i < _parallel_gc_threads; ++i) {
    _par_last_ext_root_scan_times_ms[i] = -666.0;
    _par_last_mark_stack_scan_times_ms[i] = -666.0;
    _par_last_scan_only_times_ms[i] = -666.0;
    _par_last_scan_only_regions_scanned[i] = -666.0;
    _par_last_update_rs_start_times_ms[i] = -666.0;
    _par_last_update_rs_times_ms[i] = -666.0;
    _par_last_update_rs_processed_buffers[i] = -666.0;
    _par_last_scan_rs_start_times_ms[i] = -666.0;
    _par_last_scan_rs_times_ms[i] = -666.0;
    _par_last_scan_new_refs_times_ms[i] = -666.0;
    _par_last_obj_copy_times_ms[i] = -666.0;
    _par_last_termination_times_ms[i] = -666.0;
  }
#endif

  for (int i = 0; i < _aux_num; ++i) {
    _cur_aux_times_ms[i] = 0.0;
    _cur_aux_times_set[i] = false;
  }

  _satb_drain_time_set = false;
  _last_satb_drain_processed_buffers = -1;

  if (in_young_gc_mode())
    _last_young_gc_full = false;


  // do that for any other surv rate groups
  _short_lived_surv_rate_group->stop_adding_regions();
  size_t short_lived_so_length = _young_list_so_prefix_length;
  _short_lived_surv_rate_group->record_scan_only_prefix(short_lived_so_length);
  tag_scan_only(short_lived_so_length);

  if (G1UseSurvivorSpaces) {
    _survivors_age_table.clear();
  }

  assert( verify_young_ages(), "region age verification" );
}

void G1CollectorPolicy::tag_scan_only(size_t short_lived_scan_only_length) {
  // done in a way that it can be extended for other surv rate groups too...

  HeapRegion* head = _g1->young_list_first_region();
  bool finished_short_lived = (short_lived_scan_only_length == 0);

  if (finished_short_lived)
    return;

  for (HeapRegion* curr = head;
       curr != NULL;
       curr = curr->get_next_young_region()) {
    SurvRateGroup* surv_rate_group = curr->surv_rate_group();
    int age = curr->age_in_surv_rate_group();

    if (surv_rate_group == _short_lived_surv_rate_group) {
      if ((size_t)age < short_lived_scan_only_length)
        curr->set_scan_only();
      else
        finished_short_lived = true;
    }


    if (finished_short_lived)
      return;
  }

  guarantee( false, "we should never reach here" );
}

void G1CollectorPolicy::record_mark_closure_time(double mark_closure_time_ms) {
  _mark_closure_time_ms = mark_closure_time_ms;
}

void G1CollectorPolicy::record_concurrent_mark_init_start() {
  _mark_init_start_sec = os::elapsedTime();
  guarantee(!in_young_gc_mode(), "should not do be here in young GC mode");
}

void G1CollectorPolicy::record_concurrent_mark_init_end_pre(double
                                                   mark_init_elapsed_time_ms) {
  _during_marking = true;
  _should_initiate_conc_mark = false;
  _cur_mark_stop_world_time_ms = mark_init_elapsed_time_ms;
}

void G1CollectorPolicy::record_concurrent_mark_init_end() {
  double end_time_sec = os::elapsedTime();
  double elapsed_time_ms = (end_time_sec - _mark_init_start_sec) * 1000.0;
  _concurrent_mark_init_times_ms->add(elapsed_time_ms);
  record_concurrent_mark_init_end_pre(elapsed_time_ms);

  _mmu_tracker->add_pause(_mark_init_start_sec, end_time_sec, true);
}

void G1CollectorPolicy::record_concurrent_mark_remark_start() {
  _mark_remark_start_sec = os::elapsedTime();
  _during_marking = false;
}

void G1CollectorPolicy::record_concurrent_mark_remark_end() {
  double end_time_sec = os::elapsedTime();
  double elapsed_time_ms = (end_time_sec - _mark_remark_start_sec)*1000.0;
  _concurrent_mark_remark_times_ms->add(elapsed_time_ms);
  _cur_mark_stop_world_time_ms += elapsed_time_ms;
  _prev_collection_pause_end_ms += elapsed_time_ms;

  _mmu_tracker->add_pause(_mark_remark_start_sec, end_time_sec, true);
}

void G1CollectorPolicy::record_concurrent_mark_cleanup_start() {
  _mark_cleanup_start_sec = os::elapsedTime();
}

void
G1CollectorPolicy::record_concurrent_mark_cleanup_end(size_t freed_bytes,
                                                      size_t max_live_bytes) {
  record_concurrent_mark_cleanup_end_work1(freed_bytes, max_live_bytes);
  record_concurrent_mark_cleanup_end_work2();
}

void
G1CollectorPolicy::
record_concurrent_mark_cleanup_end_work1(size_t freed_bytes,
                                         size_t max_live_bytes) {
  if (_n_marks < 2) _n_marks++;
  if (G1PolicyVerbose > 0)
    gclog_or_tty->print_cr("At end of marking, max_live is " SIZE_FORMAT " MB "
                           " (of " SIZE_FORMAT " MB heap).",
                           max_live_bytes/M, _g1->capacity()/M);
}

// The important thing about this is that it includes "os::elapsedTime".
void G1CollectorPolicy::record_concurrent_mark_cleanup_end_work2() {
  double end_time_sec = os::elapsedTime();
  double elapsed_time_ms = (end_time_sec - _mark_cleanup_start_sec)*1000.0;
  _concurrent_mark_cleanup_times_ms->add(elapsed_time_ms);
  _cur_mark_stop_world_time_ms += elapsed_time_ms;
  _prev_collection_pause_end_ms += elapsed_time_ms;

  _mmu_tracker->add_pause(_mark_cleanup_start_sec, end_time_sec, true);

  _num_markings++;

  // We did a marking, so reset the "since_last_mark" variables.
  double considerConcMarkCost = 1.0;
  // If there are available processors, concurrent activity is free...
  if (Threads::number_of_non_daemon_threads() * 2 <
      os::active_processor_count()) {
    considerConcMarkCost = 0.0;
  }
  _n_pauses_at_mark_end = _n_pauses;
  _n_marks_since_last_pause++;
  _conc_mark_initiated = false;
}

void
G1CollectorPolicy::record_concurrent_mark_cleanup_completed() {
  if (in_young_gc_mode()) {
    _should_revert_to_full_young_gcs = false;
    _last_full_young_gc = true;
    _in_marking_window = false;
    if (adaptive_young_list_length())
      calculate_young_list_target_config();
  }
}

void G1CollectorPolicy::record_concurrent_pause() {
  if (_stop_world_start > 0.0) {
    double yield_ms = (os::elapsedTime() - _stop_world_start) * 1000.0;
    _all_yield_times_ms->add(yield_ms);
  }
}

void G1CollectorPolicy::record_concurrent_pause_end() {
}

void G1CollectorPolicy::record_collection_pause_end_CH_strong_roots() {
  _cur_CH_strong_roots_end_sec = os::elapsedTime();
  _cur_CH_strong_roots_dur_ms =
    (_cur_CH_strong_roots_end_sec - _cur_collection_start_sec) * 1000.0;
}

void G1CollectorPolicy::record_collection_pause_end_G1_strong_roots() {
  _cur_G1_strong_roots_end_sec = os::elapsedTime();
  _cur_G1_strong_roots_dur_ms =
    (_cur_G1_strong_roots_end_sec - _cur_CH_strong_roots_end_sec) * 1000.0;
}

template<class T>
T sum_of(T* sum_arr, int start, int n, int N) {
  T sum = (T)0;
  for (int i = 0; i < n; i++) {
    int j = (start + i) % N;
    sum += sum_arr[j];
  }
  return sum;
}

void G1CollectorPolicy::print_par_stats (int level,
                                         const char* str,
                                         double* data,
                                         bool summary) {
  double min = data[0], max = data[0];
  double total = 0.0;
  int j;
  for (j = 0; j < level; ++j)
    gclog_or_tty->print("   ");
  gclog_or_tty->print("[%s (ms):", str);
  for (uint i = 0; i < ParallelGCThreads; ++i) {
    double val = data[i];
    if (val < min)
      min = val;
    if (val > max)
      max = val;
    total += val;
    gclog_or_tty->print("  %3.1lf", val);
  }
  if (summary) {
    gclog_or_tty->print_cr("");
    double avg = total / (double) ParallelGCThreads;
    gclog_or_tty->print(" ");
    for (j = 0; j < level; ++j)
      gclog_or_tty->print("   ");
    gclog_or_tty->print("Avg: %5.1lf, Min: %5.1lf, Max: %5.1lf",
                        avg, min, max);
  }
  gclog_or_tty->print_cr("]");
}

void G1CollectorPolicy::print_par_buffers (int level,
                                         const char* str,
                                         double* data,
                                         bool summary) {
  double min = data[0], max = data[0];
  double total = 0.0;
  int j;
  for (j = 0; j < level; ++j)
    gclog_or_tty->print("   ");
  gclog_or_tty->print("[%s :", str);
  for (uint i = 0; i < ParallelGCThreads; ++i) {
    double val = data[i];
    if (val < min)
      min = val;
    if (val > max)
      max = val;
    total += val;
    gclog_or_tty->print(" %d", (int) val);
  }
  if (summary) {
    gclog_or_tty->print_cr("");
    double avg = total / (double) ParallelGCThreads;
    gclog_or_tty->print(" ");
    for (j = 0; j < level; ++j)
      gclog_or_tty->print("   ");
    gclog_or_tty->print("Sum: %d, Avg: %d, Min: %d, Max: %d",
               (int)total, (int)avg, (int)min, (int)max);
  }
  gclog_or_tty->print_cr("]");
}

void G1CollectorPolicy::print_stats (int level,
                                     const char* str,
                                     double value) {
  for (int j = 0; j < level; ++j)
    gclog_or_tty->print("   ");
  gclog_or_tty->print_cr("[%s: %5.1lf ms]", str, value);
}

void G1CollectorPolicy::print_stats (int level,
                                     const char* str,
                                     int value) {
  for (int j = 0; j < level; ++j)
    gclog_or_tty->print("   ");
  gclog_or_tty->print_cr("[%s: %d]", str, value);
}

double G1CollectorPolicy::avg_value (double* data) {
  if (ParallelGCThreads > 0) {
    double ret = 0.0;
    for (uint i = 0; i < ParallelGCThreads; ++i)
      ret += data[i];
    return ret / (double) ParallelGCThreads;
  } else {
    return data[0];
  }
}

double G1CollectorPolicy::max_value (double* data) {
  if (ParallelGCThreads > 0) {
    double ret = data[0];
    for (uint i = 1; i < ParallelGCThreads; ++i)
      if (data[i] > ret)
        ret = data[i];
    return ret;
  } else {
    return data[0];
  }
}

double G1CollectorPolicy::sum_of_values (double* data) {
  if (ParallelGCThreads > 0) {
    double sum = 0.0;
    for (uint i = 0; i < ParallelGCThreads; i++)
      sum += data[i];
    return sum;
  } else {
    return data[0];
  }
}

double G1CollectorPolicy::max_sum (double* data1,
                                   double* data2) {
  double ret = data1[0] + data2[0];

  if (ParallelGCThreads > 0) {
    for (uint i = 1; i < ParallelGCThreads; ++i) {
      double data = data1[i] + data2[i];
      if (data > ret)
        ret = data;
    }
  }
  return ret;
}

// Anything below that is considered to be zero
#define MIN_TIMER_GRANULARITY 0.0000001

void G1CollectorPolicy::record_collection_pause_end(bool abandoned) {
  double end_time_sec = os::elapsedTime();
  double elapsed_ms = _last_pause_time_ms;
  bool parallel = ParallelGCThreads > 0;
  double evac_ms = (end_time_sec - _cur_G1_strong_roots_end_sec) * 1000.0;
  size_t rs_size =
    _cur_collection_pause_used_regions_at_start - collection_set_size();
  size_t cur_used_bytes = _g1->used();
  assert(cur_used_bytes == _g1->recalculate_used(), "It should!");
  bool last_pause_included_initial_mark = false;
  bool update_stats = !abandoned && !_g1->evacuation_failed();

#ifndef PRODUCT
  if (G1YoungSurvRateVerbose) {
    gclog_or_tty->print_cr("");
    _short_lived_surv_rate_group->print();
    // do that for any other surv rate groups too
  }
#endif // PRODUCT

  if (in_young_gc_mode()) {
    last_pause_included_initial_mark = _should_initiate_conc_mark;
    if (last_pause_included_initial_mark)
      record_concurrent_mark_init_end_pre(0.0);

    size_t min_used_targ =
      (_g1->capacity() / 100) * (G1SteadyStateUsed - G1SteadyStateUsedDelta);

    if (cur_used_bytes > min_used_targ) {
      if (cur_used_bytes <= _prev_collection_pause_used_at_end_bytes) {
      } else if (!_g1->mark_in_progress() && !_last_full_young_gc) {
        _should_initiate_conc_mark = true;
      }
    }

    _prev_collection_pause_used_at_end_bytes = cur_used_bytes;
  }

  _mmu_tracker->add_pause(end_time_sec - elapsed_ms/1000.0,
                          end_time_sec, false);

  guarantee(_cur_collection_pause_used_regions_at_start >=
            collection_set_size(),
            "Negative RS size?");

  // This assert is exempted when we're doing parallel collection pauses,
  // because the fragmentation caused by the parallel GC allocation buffers
  // can lead to more memory being used during collection than was used
  // before. Best leave this out until the fragmentation problem is fixed.
  // Pauses in which evacuation failed can also lead to negative
  // collections, since no space is reclaimed from a region containing an
  // object whose evacuation failed.
  // Further, we're now always doing parallel collection.  But I'm still
  // leaving this here as a placeholder for a more precise assertion later.
  // (DLD, 10/05.)
  assert((true || parallel) // Always using GC LABs now.
         || _g1->evacuation_failed()
         || _cur_collection_pause_used_at_start_bytes >= cur_used_bytes,
         "Negative collection");

  size_t freed_bytes =
    _cur_collection_pause_used_at_start_bytes - cur_used_bytes;
  size_t surviving_bytes = _collection_set_bytes_used_before - freed_bytes;
  double survival_fraction =
    (double)surviving_bytes/
    (double)_collection_set_bytes_used_before;

  _n_pauses++;

  if (update_stats) {
    _recent_CH_strong_roots_times_ms->add(_cur_CH_strong_roots_dur_ms);
    _recent_G1_strong_roots_times_ms->add(_cur_G1_strong_roots_dur_ms);
    _recent_evac_times_ms->add(evac_ms);
    _recent_pause_times_ms->add(elapsed_ms);

    _recent_rs_sizes->add(rs_size);

    // We exempt parallel collection from this check because Alloc Buffer
    // fragmentation can produce negative collections.  Same with evac
    // failure.
    // Further, we're now always doing parallel collection.  But I'm still
    // leaving this here as a placeholder for a more precise assertion later.
    // (DLD, 10/05.
    assert((true || parallel)
           || _g1->evacuation_failed()
           || surviving_bytes <= _collection_set_bytes_used_before,
           "Or else negative collection!");
    _recent_CS_bytes_used_before->add(_collection_set_bytes_used_before);
    _recent_CS_bytes_surviving->add(surviving_bytes);

    // this is where we update the allocation rate of the application
    double app_time_ms =
      (_cur_collection_start_sec * 1000.0 - _prev_collection_pause_end_ms);
    if (app_time_ms < MIN_TIMER_GRANULARITY) {
      // This usually happens due to the timer not having the required
      // granularity. Some Linuxes are the usual culprits.
      // We'll just set it to something (arbitrarily) small.
      app_time_ms = 1.0;
    }
    size_t regions_allocated =
      (_region_num_young - _prev_region_num_young) +
      (_region_num_tenured - _prev_region_num_tenured);
    double alloc_rate_ms = (double) regions_allocated / app_time_ms;
    _alloc_rate_ms_seq->add(alloc_rate_ms);
    _prev_region_num_young   = _region_num_young;
    _prev_region_num_tenured = _region_num_tenured;

    double interval_ms =
      (end_time_sec - _recent_prev_end_times_for_all_gcs_sec->oldest()) * 1000.0;
    update_recent_gc_times(end_time_sec, elapsed_ms);
    _recent_avg_pause_time_ratio = _recent_gc_times_ms->sum()/interval_ms;
    if (recent_avg_pause_time_ratio() < 0.0 ||
        (recent_avg_pause_time_ratio() - 1.0 > 0.0)) {
#ifndef PRODUCT
      // Dump info to allow post-facto debugging
      gclog_or_tty->print_cr("recent_avg_pause_time_ratio() out of bounds");
      gclog_or_tty->print_cr("-------------------------------------------");
      gclog_or_tty->print_cr("Recent GC Times (ms):");
      _recent_gc_times_ms->dump();
      gclog_or_tty->print_cr("(End Time=%3.3f) Recent GC End Times (s):", end_time_sec);
      _recent_prev_end_times_for_all_gcs_sec->dump();
      gclog_or_tty->print_cr("GC = %3.3f, Interval = %3.3f, Ratio = %3.3f",
                             _recent_gc_times_ms->sum(), interval_ms, recent_avg_pause_time_ratio());
      // In debug mode, terminate the JVM if the user wants to debug at this point.
      assert(!G1FailOnFPError, "Debugging data for CR 6898948 has been dumped above");
#endif  // !PRODUCT
      // Clip ratio between 0.0 and 1.0, and continue. This will be fixed in
      // CR 6902692 by redoing the manner in which the ratio is incrementally computed.
      if (_recent_avg_pause_time_ratio < 0.0) {
        _recent_avg_pause_time_ratio = 0.0;
      } else {
        assert(_recent_avg_pause_time_ratio - 1.0 > 0.0, "Ctl-point invariant");
        _recent_avg_pause_time_ratio = 1.0;
      }
    }
  }

  if (G1PolicyVerbose > 1) {
    gclog_or_tty->print_cr("   Recording collection pause(%d)", _n_pauses);
  }

  PauseSummary* summary;
  if (abandoned) {
    summary = _abandoned_summary;
  } else {
    summary = _summary;
  }

  double ext_root_scan_time = avg_value(_par_last_ext_root_scan_times_ms);
  double mark_stack_scan_time = avg_value(_par_last_mark_stack_scan_times_ms);
  double scan_only_time = avg_value(_par_last_scan_only_times_ms);
  double scan_only_regions_scanned =
    sum_of_values(_par_last_scan_only_regions_scanned);
  double update_rs_time = avg_value(_par_last_update_rs_times_ms);
  double update_rs_processed_buffers =
    sum_of_values(_par_last_update_rs_processed_buffers);
  double scan_rs_time = avg_value(_par_last_scan_rs_times_ms);
  double obj_copy_time = avg_value(_par_last_obj_copy_times_ms);
  double termination_time = avg_value(_par_last_termination_times_ms);

  double parallel_other_time = _cur_collection_par_time_ms -
    (update_rs_time + ext_root_scan_time + mark_stack_scan_time +
     scan_only_time + scan_rs_time + obj_copy_time + termination_time);
  if (update_stats) {
    MainBodySummary* body_summary = summary->main_body_summary();
    guarantee(body_summary != NULL, "should not be null!");

    if (_satb_drain_time_set)
      body_summary->record_satb_drain_time_ms(_cur_satb_drain_time_ms);
    else
      body_summary->record_satb_drain_time_ms(0.0);
    body_summary->record_ext_root_scan_time_ms(ext_root_scan_time);
    body_summary->record_mark_stack_scan_time_ms(mark_stack_scan_time);
    body_summary->record_scan_only_time_ms(scan_only_time);
    body_summary->record_update_rs_time_ms(update_rs_time);
    body_summary->record_scan_rs_time_ms(scan_rs_time);
    body_summary->record_obj_copy_time_ms(obj_copy_time);
    if (parallel) {
      body_summary->record_parallel_time_ms(_cur_collection_par_time_ms);
      body_summary->record_clear_ct_time_ms(_cur_clear_ct_time_ms);
      body_summary->record_termination_time_ms(termination_time);
      body_summary->record_parallel_other_time_ms(parallel_other_time);
    }
    body_summary->record_mark_closure_time_ms(_mark_closure_time_ms);
  }

  if (G1PolicyVerbose > 1) {
    gclog_or_tty->print_cr("      ET: %10.6f ms           (avg: %10.6f ms)\n"
                           "        CH Strong: %10.6f ms    (avg: %10.6f ms)\n"
                           "        G1 Strong: %10.6f ms    (avg: %10.6f ms)\n"
                           "        Evac:      %10.6f ms    (avg: %10.6f ms)\n"
                           "       ET-RS:  %10.6f ms      (avg: %10.6f ms)\n"
                           "      |RS|: " SIZE_FORMAT,
                           elapsed_ms, recent_avg_time_for_pauses_ms(),
                           _cur_CH_strong_roots_dur_ms, recent_avg_time_for_CH_strong_ms(),
                           _cur_G1_strong_roots_dur_ms, recent_avg_time_for_G1_strong_ms(),
                           evac_ms, recent_avg_time_for_evac_ms(),
                           scan_rs_time,
                           recent_avg_time_for_pauses_ms() -
                           recent_avg_time_for_G1_strong_ms(),
                           rs_size);

    gclog_or_tty->print_cr("       Used at start: " SIZE_FORMAT"K"
                           "       At end " SIZE_FORMAT "K\n"
                           "       garbage      : " SIZE_FORMAT "K"
                           "       of     " SIZE_FORMAT "K\n"
                           "       survival     : %6.2f%%  (%6.2f%% avg)",
                           _cur_collection_pause_used_at_start_bytes/K,
                           _g1->used()/K, freed_bytes/K,
                           _collection_set_bytes_used_before/K,
                           survival_fraction*100.0,
                           recent_avg_survival_fraction()*100.0);
    gclog_or_tty->print_cr("       Recent %% gc pause time: %6.2f",
                           recent_avg_pause_time_ratio() * 100.0);
  }

  double other_time_ms = elapsed_ms;

  if (!abandoned) {
    if (_satb_drain_time_set)
      other_time_ms -= _cur_satb_drain_time_ms;

    if (parallel)
      other_time_ms -= _cur_collection_par_time_ms + _cur_clear_ct_time_ms;
    else
      other_time_ms -=
        update_rs_time +
        ext_root_scan_time + mark_stack_scan_time + scan_only_time +
        scan_rs_time + obj_copy_time;
  }

  if (PrintGCDetails) {
    gclog_or_tty->print_cr("%s%s, %1.8lf secs]",
                           abandoned ? " (abandoned)" : "",
                           (last_pause_included_initial_mark) ? " (initial-mark)" : "",
                           elapsed_ms / 1000.0);

    if (!abandoned) {
      if (_satb_drain_time_set) {
        print_stats(1, "SATB Drain Time", _cur_satb_drain_time_ms);
      }
      if (_last_satb_drain_processed_buffers >= 0) {
        print_stats(2, "Processed Buffers", _last_satb_drain_processed_buffers);
      }
      if (parallel) {
        print_stats(1, "Parallel Time", _cur_collection_par_time_ms);
        print_par_stats(2, "Update RS (Start)", _par_last_update_rs_start_times_ms, false);
        print_par_stats(2, "Update RS", _par_last_update_rs_times_ms);
        print_par_buffers(3, "Processed Buffers",
                          _par_last_update_rs_processed_buffers, true);
        print_par_stats(2, "Ext Root Scanning", _par_last_ext_root_scan_times_ms);
        print_par_stats(2, "Mark Stack Scanning", _par_last_mark_stack_scan_times_ms);
        print_par_stats(2, "Scan-Only Scanning", _par_last_scan_only_times_ms);
        print_par_buffers(3, "Scan-Only Regions",
                          _par_last_scan_only_regions_scanned, true);
        print_par_stats(2, "Scan RS", _par_last_scan_rs_times_ms);
        print_par_stats(2, "Object Copy", _par_last_obj_copy_times_ms);
        print_par_stats(2, "Termination", _par_last_termination_times_ms);
        print_stats(2, "Other", parallel_other_time);
        print_stats(1, "Clear CT", _cur_clear_ct_time_ms);
      } else {
        print_stats(1, "Update RS", update_rs_time);
        print_stats(2, "Processed Buffers",
                    (int)update_rs_processed_buffers);
        print_stats(1, "Ext Root Scanning", ext_root_scan_time);
        print_stats(1, "Mark Stack Scanning", mark_stack_scan_time);
        print_stats(1, "Scan-Only Scanning", scan_only_time);
        print_stats(1, "Scan RS", scan_rs_time);
        print_stats(1, "Object Copying", obj_copy_time);
      }
    }
#ifndef PRODUCT
    print_stats(1, "Cur Clear CC", _cur_clear_cc_time_ms);
    print_stats(1, "Cum Clear CC", _cum_clear_cc_time_ms);
    print_stats(1, "Min Clear CC", _min_clear_cc_time_ms);
    print_stats(1, "Max Clear CC", _max_clear_cc_time_ms);
    if (_num_cc_clears > 0) {
      print_stats(1, "Avg Clear CC", _cum_clear_cc_time_ms / ((double)_num_cc_clears));
    }
#endif
    print_stats(1, "Other", other_time_ms);
    for (int i = 0; i < _aux_num; ++i) {
      if (_cur_aux_times_set[i]) {
        char buffer[96];
        sprintf(buffer, "Aux%d", i);
        print_stats(1, buffer, _cur_aux_times_ms[i]);
      }
    }
  }
  if (PrintGCDetails)
    gclog_or_tty->print("   [");
  if (PrintGC || PrintGCDetails)
    _g1->print_size_transition(gclog_or_tty,
                               _cur_collection_pause_used_at_start_bytes,
                               _g1->used(), _g1->capacity());
  if (PrintGCDetails)
    gclog_or_tty->print_cr("]");

  _all_pause_times_ms->add(elapsed_ms);
  if (update_stats) {
    summary->record_total_time_ms(elapsed_ms);
    summary->record_other_time_ms(other_time_ms);
  }
  for (int i = 0; i < _aux_num; ++i)
    if (_cur_aux_times_set[i])
      _all_aux_times_ms[i].add(_cur_aux_times_ms[i]);

  // Reset marks-between-pauses counter.
  _n_marks_since_last_pause = 0;

  // Update the efficiency-since-mark vars.
  double proc_ms = elapsed_ms * (double) _parallel_gc_threads;
  if (elapsed_ms < MIN_TIMER_GRANULARITY) {
    // This usually happens due to the timer not having the required
    // granularity. Some Linuxes are the usual culprits.
    // We'll just set it to something (arbitrarily) small.
    proc_ms = 1.0;
  }
  double cur_efficiency = (double) freed_bytes / proc_ms;

  bool new_in_marking_window = _in_marking_window;
  bool new_in_marking_window_im = false;
  if (_should_initiate_conc_mark) {
    new_in_marking_window = true;
    new_in_marking_window_im = true;
  }

  if (in_young_gc_mode()) {
    if (_last_full_young_gc) {
      set_full_young_gcs(false);
      _last_full_young_gc = false;
    }

    if ( !_last_young_gc_full ) {
      if ( _should_revert_to_full_young_gcs ||
           _known_garbage_ratio < 0.05 ||
           (adaptive_young_list_length() &&
           (get_gc_eff_factor() * cur_efficiency < predict_young_gc_eff())) ) {
        set_full_young_gcs(true);
      }
    }
    _should_revert_to_full_young_gcs = false;

    if (_last_young_gc_full && !_during_marking)
      _young_gc_eff_seq->add(cur_efficiency);
  }

  _short_lived_surv_rate_group->start_adding_regions();
  // do that for any other surv rate groupsx

  // <NEW PREDICTION>

  if (update_stats) {
    double pause_time_ms = elapsed_ms;

    size_t diff = 0;
    if (_max_pending_cards >= _pending_cards)
      diff = _max_pending_cards - _pending_cards;
    _pending_card_diff_seq->add((double) diff);

    double cost_per_card_ms = 0.0;
    if (_pending_cards > 0) {
      cost_per_card_ms = update_rs_time / (double) _pending_cards;
      _cost_per_card_ms_seq->add(cost_per_card_ms);
    }

    double cost_per_scan_only_region_ms = 0.0;
    if (scan_only_regions_scanned > 0.0) {
      cost_per_scan_only_region_ms =
        scan_only_time / scan_only_regions_scanned;
      if (_in_marking_window_im)
        _cost_per_scan_only_region_ms_during_cm_seq->add(cost_per_scan_only_region_ms);
      else
        _cost_per_scan_only_region_ms_seq->add(cost_per_scan_only_region_ms);
    }

    size_t cards_scanned = _g1->cards_scanned();

    double cost_per_entry_ms = 0.0;
    if (cards_scanned > 10) {
      cost_per_entry_ms = scan_rs_time / (double) cards_scanned;
      if (_last_young_gc_full)
        _cost_per_entry_ms_seq->add(cost_per_entry_ms);
      else
        _partially_young_cost_per_entry_ms_seq->add(cost_per_entry_ms);
    }

    if (_max_rs_lengths > 0) {
      double cards_per_entry_ratio =
        (double) cards_scanned / (double) _max_rs_lengths;
      if (_last_young_gc_full)
        _fully_young_cards_per_entry_ratio_seq->add(cards_per_entry_ratio);
      else
        _partially_young_cards_per_entry_ratio_seq->add(cards_per_entry_ratio);
    }

    size_t rs_length_diff = _max_rs_lengths - _recorded_rs_lengths;
    if (rs_length_diff >= 0)
      _rs_length_diff_seq->add((double) rs_length_diff);

    size_t copied_bytes = surviving_bytes;
    double cost_per_byte_ms = 0.0;
    if (copied_bytes > 0) {
      cost_per_byte_ms = obj_copy_time / (double) copied_bytes;
      if (_in_marking_window)
        _cost_per_byte_ms_during_cm_seq->add(cost_per_byte_ms);
      else
        _cost_per_byte_ms_seq->add(cost_per_byte_ms);
    }

    double all_other_time_ms = pause_time_ms -
      (update_rs_time + scan_only_time + scan_rs_time + obj_copy_time +
       _mark_closure_time_ms + termination_time);

    double young_other_time_ms = 0.0;
    if (_recorded_young_regions > 0) {
      young_other_time_ms =
        _recorded_young_cset_choice_time_ms +
        _recorded_young_free_cset_time_ms;
      _young_other_cost_per_region_ms_seq->add(young_other_time_ms /
                                             (double) _recorded_young_regions);
    }
    double non_young_other_time_ms = 0.0;
    if (_recorded_non_young_regions > 0) {
      non_young_other_time_ms =
        _recorded_non_young_cset_choice_time_ms +
        _recorded_non_young_free_cset_time_ms;

      _non_young_other_cost_per_region_ms_seq->add(non_young_other_time_ms /
                                         (double) _recorded_non_young_regions);
    }

    double constant_other_time_ms = all_other_time_ms -
      (young_other_time_ms + non_young_other_time_ms);
    _constant_other_time_ms_seq->add(constant_other_time_ms);

    double survival_ratio = 0.0;
    if (_bytes_in_collection_set_before_gc > 0) {
      survival_ratio = (double) bytes_in_to_space_during_gc() /
        (double) _bytes_in_collection_set_before_gc;
    }

    _pending_cards_seq->add((double) _pending_cards);
    _scanned_cards_seq->add((double) cards_scanned);
    _rs_lengths_seq->add((double) _max_rs_lengths);

    double expensive_region_limit_ms =
      (double) MaxGCPauseMillis - predict_constant_other_time_ms();
    if (expensive_region_limit_ms < 0.0) {
      // this means that the other time was predicted to be longer than
      // than the max pause time
      expensive_region_limit_ms = (double) MaxGCPauseMillis;
    }
    _expensive_region_limit_ms = expensive_region_limit_ms;

    if (PREDICTIONS_VERBOSE) {
      gclog_or_tty->print_cr("");
      gclog_or_tty->print_cr("PREDICTIONS %1.4lf %d "
                    "REGIONS %d %d %d %d "
                    "PENDING_CARDS %d %d "
                    "CARDS_SCANNED %d %d "
                    "RS_LENGTHS %d %d "
                    "SCAN_ONLY_SCAN %1.6lf %1.6lf "
                    "RS_UPDATE %1.6lf %1.6lf RS_SCAN %1.6lf %1.6lf "
                    "SURVIVAL_RATIO %1.6lf %1.6lf "
                    "OBJECT_COPY %1.6lf %1.6lf OTHER_CONSTANT %1.6lf %1.6lf "
                    "OTHER_YOUNG %1.6lf %1.6lf "
                    "OTHER_NON_YOUNG %1.6lf %1.6lf "
                    "VTIME_DIFF %1.6lf TERMINATION %1.6lf "
                    "ELAPSED %1.6lf %1.6lf ",
                    _cur_collection_start_sec,
                    (!_last_young_gc_full) ? 2 :
                    (last_pause_included_initial_mark) ? 1 : 0,
                    _recorded_region_num,
                    _recorded_young_regions,
                    _recorded_scan_only_regions,
                    _recorded_non_young_regions,
                    _predicted_pending_cards, _pending_cards,
                    _predicted_cards_scanned, cards_scanned,
                    _predicted_rs_lengths, _max_rs_lengths,
                    _predicted_scan_only_scan_time_ms, scan_only_time,
                    _predicted_rs_update_time_ms, update_rs_time,
                    _predicted_rs_scan_time_ms, scan_rs_time,
                    _predicted_survival_ratio, survival_ratio,
                    _predicted_object_copy_time_ms, obj_copy_time,
                    _predicted_constant_other_time_ms, constant_other_time_ms,
                    _predicted_young_other_time_ms, young_other_time_ms,
                    _predicted_non_young_other_time_ms,
                    non_young_other_time_ms,
                    _vtime_diff_ms, termination_time,
                    _predicted_pause_time_ms, elapsed_ms);
    }

    if (G1PolicyVerbose > 0) {
      gclog_or_tty->print_cr("Pause Time, predicted: %1.4lfms (predicted %s), actual: %1.4lfms",
                    _predicted_pause_time_ms,
                    (_within_target) ? "within" : "outside",
                    elapsed_ms);
    }

  }

  _in_marking_window = new_in_marking_window;
  _in_marking_window_im = new_in_marking_window_im;
  _free_regions_at_end_of_collection = _g1->free_regions();
  _scan_only_regions_at_end_of_collection = _g1->young_list_length();
  calculate_young_list_min_length();
  calculate_young_list_target_config();

  // </NEW PREDICTION>

  _target_pause_time_ms = -1.0;
}

// <NEW PREDICTION>

double
G1CollectorPolicy::
predict_young_collection_elapsed_time_ms(size_t adjustment) {
  guarantee( adjustment == 0 || adjustment == 1, "invariant" );

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  size_t young_num = g1h->young_list_length();
  if (young_num == 0)
    return 0.0;

  young_num += adjustment;
  size_t pending_cards = predict_pending_cards();
  size_t rs_lengths = g1h->young_list_sampled_rs_lengths() +
                      predict_rs_length_diff();
  size_t card_num;
  if (full_young_gcs())
    card_num = predict_young_card_num(rs_lengths);
  else
    card_num = predict_non_young_card_num(rs_lengths);
  size_t young_byte_size = young_num * HeapRegion::GrainBytes;
  double accum_yg_surv_rate =
    _short_lived_surv_rate_group->accum_surv_rate(adjustment);

  size_t bytes_to_copy =
    (size_t) (accum_yg_surv_rate * (double) HeapRegion::GrainBytes);

  return
    predict_rs_update_time_ms(pending_cards) +
    predict_rs_scan_time_ms(card_num) +
    predict_object_copy_time_ms(bytes_to_copy) +
    predict_young_other_time_ms(young_num) +
    predict_constant_other_time_ms();
}

double
G1CollectorPolicy::predict_base_elapsed_time_ms(size_t pending_cards) {
  size_t rs_length = predict_rs_length_diff();
  size_t card_num;
  if (full_young_gcs())
    card_num = predict_young_card_num(rs_length);
  else
    card_num = predict_non_young_card_num(rs_length);
  return predict_base_elapsed_time_ms(pending_cards, card_num);
}

double
G1CollectorPolicy::predict_base_elapsed_time_ms(size_t pending_cards,
                                                size_t scanned_cards) {
  return
    predict_rs_update_time_ms(pending_cards) +
    predict_rs_scan_time_ms(scanned_cards) +
    predict_constant_other_time_ms();
}

double
G1CollectorPolicy::predict_region_elapsed_time_ms(HeapRegion* hr,
                                                  bool young) {
  size_t rs_length = hr->rem_set()->occupied();
  size_t card_num;
  if (full_young_gcs())
    card_num = predict_young_card_num(rs_length);
  else
    card_num = predict_non_young_card_num(rs_length);
  size_t bytes_to_copy = predict_bytes_to_copy(hr);

  double region_elapsed_time_ms =
    predict_rs_scan_time_ms(card_num) +
    predict_object_copy_time_ms(bytes_to_copy);

  if (young)
    region_elapsed_time_ms += predict_young_other_time_ms(1);
  else
    region_elapsed_time_ms += predict_non_young_other_time_ms(1);

  return region_elapsed_time_ms;
}

size_t
G1CollectorPolicy::predict_bytes_to_copy(HeapRegion* hr) {
  size_t bytes_to_copy;
  if (hr->is_marked())
    bytes_to_copy = hr->max_live_bytes();
  else {
    guarantee( hr->is_young() && hr->age_in_surv_rate_group() != -1,
               "invariant" );
    int age = hr->age_in_surv_rate_group();
    double yg_surv_rate = predict_yg_surv_rate(age, hr->surv_rate_group());
    bytes_to_copy = (size_t) ((double) hr->used() * yg_surv_rate);
  }

  return bytes_to_copy;
}

void
G1CollectorPolicy::start_recording_regions() {
  _recorded_rs_lengths            = 0;
  _recorded_scan_only_regions     = 0;
  _recorded_young_regions         = 0;
  _recorded_non_young_regions     = 0;

#if PREDICTIONS_VERBOSE
  _predicted_rs_lengths           = 0;
  _predicted_cards_scanned        = 0;

  _recorded_marked_bytes          = 0;
  _recorded_young_bytes           = 0;
  _predicted_bytes_to_copy        = 0;
#endif // PREDICTIONS_VERBOSE
}

void
G1CollectorPolicy::record_cset_region(HeapRegion* hr, bool young) {
  if (young) {
    ++_recorded_young_regions;
  } else {
    ++_recorded_non_young_regions;
  }
#if PREDICTIONS_VERBOSE
  if (young) {
    _recorded_young_bytes += hr->used();
  } else {
    _recorded_marked_bytes += hr->max_live_bytes();
  }
  _predicted_bytes_to_copy += predict_bytes_to_copy(hr);
#endif // PREDICTIONS_VERBOSE

  size_t rs_length = hr->rem_set()->occupied();
  _recorded_rs_lengths += rs_length;
}

void
G1CollectorPolicy::record_scan_only_regions(size_t scan_only_length) {
  _recorded_scan_only_regions = scan_only_length;
}

void
G1CollectorPolicy::end_recording_regions() {
#if PREDICTIONS_VERBOSE
  _predicted_pending_cards = predict_pending_cards();
  _predicted_rs_lengths = _recorded_rs_lengths + predict_rs_length_diff();
  if (full_young_gcs())
    _predicted_cards_scanned += predict_young_card_num(_predicted_rs_lengths);
  else
    _predicted_cards_scanned +=
      predict_non_young_card_num(_predicted_rs_lengths);
  _recorded_region_num = _recorded_young_regions + _recorded_non_young_regions;

  _predicted_scan_only_scan_time_ms =
    predict_scan_only_time_ms(_recorded_scan_only_regions);
  _predicted_rs_update_time_ms =
    predict_rs_update_time_ms(_g1->pending_card_num());
  _predicted_rs_scan_time_ms =
    predict_rs_scan_time_ms(_predicted_cards_scanned);
  _predicted_object_copy_time_ms =
    predict_object_copy_time_ms(_predicted_bytes_to_copy);
  _predicted_constant_other_time_ms =
    predict_constant_other_time_ms();
  _predicted_young_other_time_ms =
    predict_young_other_time_ms(_recorded_young_regions);
  _predicted_non_young_other_time_ms =
    predict_non_young_other_time_ms(_recorded_non_young_regions);

  _predicted_pause_time_ms =
    _predicted_scan_only_scan_time_ms +
    _predicted_rs_update_time_ms +
    _predicted_rs_scan_time_ms +
    _predicted_object_copy_time_ms +
    _predicted_constant_other_time_ms +
    _predicted_young_other_time_ms +
    _predicted_non_young_other_time_ms;
#endif // PREDICTIONS_VERBOSE
}

void G1CollectorPolicy::check_if_region_is_too_expensive(double
                                                           predicted_time_ms) {
  // I don't think we need to do this when in young GC mode since
  // marking will be initiated next time we hit the soft limit anyway...
  if (predicted_time_ms > _expensive_region_limit_ms) {
    if (!in_young_gc_mode()) {
        set_full_young_gcs(true);
      _should_initiate_conc_mark = true;
    } else
      // no point in doing another partial one
      _should_revert_to_full_young_gcs = true;
  }
}

// </NEW PREDICTION>


void G1CollectorPolicy::update_recent_gc_times(double end_time_sec,
                                               double elapsed_ms) {
  _recent_gc_times_ms->add(elapsed_ms);
  _recent_prev_end_times_for_all_gcs_sec->add(end_time_sec);
  _prev_collection_pause_end_ms = end_time_sec * 1000.0;
}

double G1CollectorPolicy::recent_avg_time_for_pauses_ms() {
  if (_recent_pause_times_ms->num() == 0) return (double) MaxGCPauseMillis;
  else return _recent_pause_times_ms->avg();
}

double G1CollectorPolicy::recent_avg_time_for_CH_strong_ms() {
  if (_recent_CH_strong_roots_times_ms->num() == 0)
    return (double)MaxGCPauseMillis/3.0;
  else return _recent_CH_strong_roots_times_ms->avg();
}

double G1CollectorPolicy::recent_avg_time_for_G1_strong_ms() {
  if (_recent_G1_strong_roots_times_ms->num() == 0)
    return (double)MaxGCPauseMillis/3.0;
  else return _recent_G1_strong_roots_times_ms->avg();
}

double G1CollectorPolicy::recent_avg_time_for_evac_ms() {
  if (_recent_evac_times_ms->num() == 0) return (double)MaxGCPauseMillis/3.0;
  else return _recent_evac_times_ms->avg();
}

int G1CollectorPolicy::number_of_recent_gcs() {
  assert(_recent_CH_strong_roots_times_ms->num() ==
         _recent_G1_strong_roots_times_ms->num(), "Sequence out of sync");
  assert(_recent_G1_strong_roots_times_ms->num() ==
         _recent_evac_times_ms->num(), "Sequence out of sync");
  assert(_recent_evac_times_ms->num() ==
         _recent_pause_times_ms->num(), "Sequence out of sync");
  assert(_recent_pause_times_ms->num() ==
         _recent_CS_bytes_used_before->num(), "Sequence out of sync");
  assert(_recent_CS_bytes_used_before->num() ==
         _recent_CS_bytes_surviving->num(), "Sequence out of sync");
  return _recent_pause_times_ms->num();
}

double G1CollectorPolicy::recent_avg_survival_fraction() {
  return recent_avg_survival_fraction_work(_recent_CS_bytes_surviving,
                                           _recent_CS_bytes_used_before);
}

double G1CollectorPolicy::last_survival_fraction() {
  return last_survival_fraction_work(_recent_CS_bytes_surviving,
                                     _recent_CS_bytes_used_before);
}

double
G1CollectorPolicy::recent_avg_survival_fraction_work(TruncatedSeq* surviving,
                                                     TruncatedSeq* before) {
  assert(surviving->num() == before->num(), "Sequence out of sync");
  if (before->sum() > 0.0) {
      double recent_survival_rate = surviving->sum() / before->sum();
      // We exempt parallel collection from this check because Alloc Buffer
      // fragmentation can produce negative collections.
      // Further, we're now always doing parallel collection.  But I'm still
      // leaving this here as a placeholder for a more precise assertion later.
      // (DLD, 10/05.)
      assert((true || ParallelGCThreads > 0) ||
             _g1->evacuation_failed() ||
             recent_survival_rate <= 1.0, "Or bad frac");
      return recent_survival_rate;
  } else {
    return 1.0; // Be conservative.
  }
}

double
G1CollectorPolicy::last_survival_fraction_work(TruncatedSeq* surviving,
                                               TruncatedSeq* before) {
  assert(surviving->num() == before->num(), "Sequence out of sync");
  if (surviving->num() > 0 && before->last() > 0.0) {
    double last_survival_rate = surviving->last() / before->last();
    // We exempt parallel collection from this check because Alloc Buffer
    // fragmentation can produce negative collections.
    // Further, we're now always doing parallel collection.  But I'm still
    // leaving this here as a placeholder for a more precise assertion later.
    // (DLD, 10/05.)
    assert((true || ParallelGCThreads > 0) ||
           last_survival_rate <= 1.0, "Or bad frac");
    return last_survival_rate;
  } else {
    return 1.0;
  }
}

static const int survival_min_obs = 5;
static double survival_min_obs_limits[] = { 0.9, 0.7, 0.5, 0.3, 0.1 };
static const double min_survival_rate = 0.1;

double
G1CollectorPolicy::conservative_avg_survival_fraction_work(double avg,
                                                           double latest) {
  double res = avg;
  if (number_of_recent_gcs() < survival_min_obs) {
    res = MAX2(res, survival_min_obs_limits[number_of_recent_gcs()]);
  }
  res = MAX2(res, latest);
  res = MAX2(res, min_survival_rate);
  // In the parallel case, LAB fragmentation can produce "negative
  // collections"; so can evac failure.  Cap at 1.0
  res = MIN2(res, 1.0);
  return res;
}

size_t G1CollectorPolicy::expansion_amount() {
  if ((int)(recent_avg_pause_time_ratio() * 100.0) > G1GCPercent) {
    // We will double the existing space, or take
    // G1ExpandByPercentOfAvailable % of the available expansion
    // space, whichever is smaller, bounded below by a minimum
    // expansion (unless that's all that's left.)
    const size_t min_expand_bytes = 1*M;
    size_t reserved_bytes = _g1->g1_reserved_obj_bytes();
    size_t committed_bytes = _g1->capacity();
    size_t uncommitted_bytes = reserved_bytes - committed_bytes;
    size_t expand_bytes;
    size_t expand_bytes_via_pct =
      uncommitted_bytes * G1ExpandByPercentOfAvailable / 100;
    expand_bytes = MIN2(expand_bytes_via_pct, committed_bytes);
    expand_bytes = MAX2(expand_bytes, min_expand_bytes);
    expand_bytes = MIN2(expand_bytes, uncommitted_bytes);
    if (G1PolicyVerbose > 1) {
      gclog_or_tty->print("Decided to expand: ratio = %5.2f, "
                 "committed = %d%s, uncommited = %d%s, via pct = %d%s.\n"
                 "                   Answer = %d.\n",
                 recent_avg_pause_time_ratio(),
                 byte_size_in_proper_unit(committed_bytes),
                 proper_unit_for_byte_size(committed_bytes),
                 byte_size_in_proper_unit(uncommitted_bytes),
                 proper_unit_for_byte_size(uncommitted_bytes),
                 byte_size_in_proper_unit(expand_bytes_via_pct),
                 proper_unit_for_byte_size(expand_bytes_via_pct),
                 byte_size_in_proper_unit(expand_bytes),
                 proper_unit_for_byte_size(expand_bytes));
    }
    return expand_bytes;
  } else {
    return 0;
  }
}

void G1CollectorPolicy::note_start_of_mark_thread() {
  _mark_thread_startup_sec = os::elapsedTime();
}

class CountCSClosure: public HeapRegionClosure {
  G1CollectorPolicy* _g1_policy;
public:
  CountCSClosure(G1CollectorPolicy* g1_policy) :
    _g1_policy(g1_policy) {}
  bool doHeapRegion(HeapRegion* r) {
    _g1_policy->_bytes_in_collection_set_before_gc += r->used();
    return false;
  }
};

void G1CollectorPolicy::count_CS_bytes_used() {
  CountCSClosure cs_closure(this);
  _g1->collection_set_iterate(&cs_closure);
}

static void print_indent(int level) {
  for (int j = 0; j < level+1; ++j)
    gclog_or_tty->print("   ");
}

void G1CollectorPolicy::print_summary (int level,
                                       const char* str,
                                       NumberSeq* seq) const {
  double sum = seq->sum();
  print_indent(level);
  gclog_or_tty->print_cr("%-24s = %8.2lf s (avg = %8.2lf ms)",
                str, sum / 1000.0, seq->avg());
}

void G1CollectorPolicy::print_summary_sd (int level,
                                          const char* str,
                                          NumberSeq* seq) const {
  print_summary(level, str, seq);
  print_indent(level + 5);
  gclog_or_tty->print_cr("(num = %5d, std dev = %8.2lf ms, max = %8.2lf ms)",
                seq->num(), seq->sd(), seq->maximum());
}

void G1CollectorPolicy::check_other_times(int level,
                                        NumberSeq* other_times_ms,
                                        NumberSeq* calc_other_times_ms) const {
  bool should_print = false;

  double max_sum = MAX2(fabs(other_times_ms->sum()),
                        fabs(calc_other_times_ms->sum()));
  double min_sum = MIN2(fabs(other_times_ms->sum()),
                        fabs(calc_other_times_ms->sum()));
  double sum_ratio = max_sum / min_sum;
  if (sum_ratio > 1.1) {
    should_print = true;
    print_indent(level + 1);
    gclog_or_tty->print_cr("## CALCULATED OTHER SUM DOESN'T MATCH RECORDED ###");
  }

  double max_avg = MAX2(fabs(other_times_ms->avg()),
                        fabs(calc_other_times_ms->avg()));
  double min_avg = MIN2(fabs(other_times_ms->avg()),
                        fabs(calc_other_times_ms->avg()));
  double avg_ratio = max_avg / min_avg;
  if (avg_ratio > 1.1) {
    should_print = true;
    print_indent(level + 1);
    gclog_or_tty->print_cr("## CALCULATED OTHER AVG DOESN'T MATCH RECORDED ###");
  }

  if (other_times_ms->sum() < -0.01) {
    print_indent(level + 1);
    gclog_or_tty->print_cr("## RECORDED OTHER SUM IS NEGATIVE ###");
  }

  if (other_times_ms->avg() < -0.01) {
    print_indent(level + 1);
    gclog_or_tty->print_cr("## RECORDED OTHER AVG IS NEGATIVE ###");
  }

  if (calc_other_times_ms->sum() < -0.01) {
    should_print = true;
    print_indent(level + 1);
    gclog_or_tty->print_cr("## CALCULATED OTHER SUM IS NEGATIVE ###");
  }

  if (calc_other_times_ms->avg() < -0.01) {
    should_print = true;
    print_indent(level + 1);
    gclog_or_tty->print_cr("## CALCULATED OTHER AVG IS NEGATIVE ###");
  }

  if (should_print)
    print_summary(level, "Other(Calc)", calc_other_times_ms);
}

void G1CollectorPolicy::print_summary(PauseSummary* summary) const {
  bool parallel = ParallelGCThreads > 0;
  MainBodySummary*    body_summary = summary->main_body_summary();
  if (summary->get_total_seq()->num() > 0) {
    print_summary_sd(0, "Evacuation Pauses", summary->get_total_seq());
    if (body_summary != NULL) {
      print_summary(1, "SATB Drain", body_summary->get_satb_drain_seq());
      if (parallel) {
        print_summary(1, "Parallel Time", body_summary->get_parallel_seq());
        print_summary(2, "Update RS", body_summary->get_update_rs_seq());
        print_summary(2, "Ext Root Scanning",
                      body_summary->get_ext_root_scan_seq());
        print_summary(2, "Mark Stack Scanning",
                      body_summary->get_mark_stack_scan_seq());
        print_summary(2, "Scan-Only Scanning",
                      body_summary->get_scan_only_seq());
        print_summary(2, "Scan RS", body_summary->get_scan_rs_seq());
        print_summary(2, "Object Copy", body_summary->get_obj_copy_seq());
        print_summary(2, "Termination", body_summary->get_termination_seq());
        print_summary(2, "Other", body_summary->get_parallel_other_seq());
        {
          NumberSeq* other_parts[] = {
            body_summary->get_update_rs_seq(),
            body_summary->get_ext_root_scan_seq(),
            body_summary->get_mark_stack_scan_seq(),
            body_summary->get_scan_only_seq(),
            body_summary->get_scan_rs_seq(),
            body_summary->get_obj_copy_seq(),
            body_summary->get_termination_seq()
          };
          NumberSeq calc_other_times_ms(body_summary->get_parallel_seq(),
                                        7, other_parts);
          check_other_times(2, body_summary->get_parallel_other_seq(),
                            &calc_other_times_ms);
        }
        print_summary(1, "Mark Closure", body_summary->get_mark_closure_seq());
        print_summary(1, "Clear CT", body_summary->get_clear_ct_seq());
      } else {
        print_summary(1, "Update RS", body_summary->get_update_rs_seq());
        print_summary(1, "Ext Root Scanning",
                      body_summary->get_ext_root_scan_seq());
        print_summary(1, "Mark Stack Scanning",
                      body_summary->get_mark_stack_scan_seq());
        print_summary(1, "Scan-Only Scanning",
                      body_summary->get_scan_only_seq());
        print_summary(1, "Scan RS", body_summary->get_scan_rs_seq());
        print_summary(1, "Object Copy", body_summary->get_obj_copy_seq());
      }
    }
    print_summary(1, "Other", summary->get_other_seq());
    {
      NumberSeq calc_other_times_ms;
      if (body_summary != NULL) {
        // not abandoned
        if (parallel) {
          // parallel
          NumberSeq* other_parts[] = {
            body_summary->get_satb_drain_seq(),
            body_summary->get_parallel_seq(),
            body_summary->get_clear_ct_seq()
          };
          calc_other_times_ms = NumberSeq(summary->get_total_seq(),
                                          3, other_parts);
        } else {
          // serial
          NumberSeq* other_parts[] = {
            body_summary->get_satb_drain_seq(),
            body_summary->get_update_rs_seq(),
            body_summary->get_ext_root_scan_seq(),
            body_summary->get_mark_stack_scan_seq(),
            body_summary->get_scan_only_seq(),
            body_summary->get_scan_rs_seq(),
            body_summary->get_obj_copy_seq()
          };
          calc_other_times_ms = NumberSeq(summary->get_total_seq(),
                                          7, other_parts);
        }
      } else {
        // abandoned
        calc_other_times_ms = NumberSeq();
      }
      check_other_times(1,  summary->get_other_seq(), &calc_other_times_ms);
    }
  } else {
    print_indent(0);
    gclog_or_tty->print_cr("none");
  }
  gclog_or_tty->print_cr("");
}

void
G1CollectorPolicy::print_abandoned_summary(PauseSummary* summary) const {
  bool printed = false;
  if (summary->get_total_seq()->num() > 0) {
    printed = true;
    print_summary(summary);
  }
  if (!printed) {
    print_indent(0);
    gclog_or_tty->print_cr("none");
    gclog_or_tty->print_cr("");
  }
}

void G1CollectorPolicy::print_tracing_info() const {
  if (TraceGen0Time) {
    gclog_or_tty->print_cr("ALL PAUSES");
    print_summary_sd(0, "Total", _all_pause_times_ms);
    gclog_or_tty->print_cr("");
    gclog_or_tty->print_cr("");
    gclog_or_tty->print_cr("   Full Young GC Pauses:    %8d", _full_young_pause_num);
    gclog_or_tty->print_cr("   Partial Young GC Pauses: %8d", _partial_young_pause_num);
    gclog_or_tty->print_cr("");

    gclog_or_tty->print_cr("EVACUATION PAUSES");
    print_summary(_summary);

    gclog_or_tty->print_cr("ABANDONED PAUSES");
    print_abandoned_summary(_abandoned_summary);

    gclog_or_tty->print_cr("MISC");
    print_summary_sd(0, "Stop World", _all_stop_world_times_ms);
    print_summary_sd(0, "Yields", _all_yield_times_ms);
    for (int i = 0; i < _aux_num; ++i) {
      if (_all_aux_times_ms[i].num() > 0) {
        char buffer[96];
        sprintf(buffer, "Aux%d", i);
        print_summary_sd(0, buffer, &_all_aux_times_ms[i]);
      }
    }

    size_t all_region_num = _region_num_young + _region_num_tenured;
    gclog_or_tty->print_cr("   New Regions %8d, Young %8d (%6.2lf%%), "
               "Tenured %8d (%6.2lf%%)",
               all_region_num,
               _region_num_young,
               (double) _region_num_young / (double) all_region_num * 100.0,
               _region_num_tenured,
               (double) _region_num_tenured / (double) all_region_num * 100.0);
  }
  if (TraceGen1Time) {
    if (_all_full_gc_times_ms->num() > 0) {
      gclog_or_tty->print("\n%4d full_gcs: total time = %8.2f s",
                 _all_full_gc_times_ms->num(),
                 _all_full_gc_times_ms->sum() / 1000.0);
      gclog_or_tty->print_cr(" (avg = %8.2fms).", _all_full_gc_times_ms->avg());
      gclog_or_tty->print_cr("                     [std. dev = %8.2f ms, max = %8.2f ms]",
                    _all_full_gc_times_ms->sd(),
                    _all_full_gc_times_ms->maximum());
    }
  }
}

void G1CollectorPolicy::print_yg_surv_rate_info() const {
#ifndef PRODUCT
  _short_lived_surv_rate_group->print_surv_rate_summary();
  // add this call for any other surv rate groups
#endif // PRODUCT
}

bool
G1CollectorPolicy::should_add_next_region_to_young_list() {
  assert(in_young_gc_mode(), "should be in young GC mode");
  bool ret;
  size_t young_list_length = _g1->young_list_length();
  size_t young_list_max_length = _young_list_target_length;
  if (G1FixedEdenSize) {
    young_list_max_length -= _max_survivor_regions;
  }
  if (young_list_length < young_list_max_length) {
    ret = true;
    ++_region_num_young;
  } else {
    ret = false;
    ++_region_num_tenured;
  }

  return ret;
}

#ifndef PRODUCT
// for debugging, bit of a hack...
static char*
region_num_to_mbs(int length) {
  static char buffer[64];
  double bytes = (double) (length * HeapRegion::GrainBytes);
  double mbs = bytes / (double) (1024 * 1024);
  sprintf(buffer, "%7.2lfMB", mbs);
  return buffer;
}
#endif // PRODUCT

size_t G1CollectorPolicy::max_regions(int purpose) {
  switch (purpose) {
    case GCAllocForSurvived:
      return _max_survivor_regions;
    case GCAllocForTenured:
      return REGIONS_UNLIMITED;
    default:
      ShouldNotReachHere();
      return REGIONS_UNLIMITED;
  };
}

// Calculates survivor space parameters.
void G1CollectorPolicy::calculate_survivors_policy()
{
  if (!G1UseSurvivorSpaces) {
    return;
  }
  if (G1FixedSurvivorSpaceSize == 0) {
    _max_survivor_regions = _young_list_target_length / SurvivorRatio;
  } else {
    _max_survivor_regions = G1FixedSurvivorSpaceSize / HeapRegion::GrainBytes;
  }

  if (G1FixedTenuringThreshold) {
    _tenuring_threshold = MaxTenuringThreshold;
  } else {
    _tenuring_threshold = _survivors_age_table.compute_tenuring_threshold(
        HeapRegion::GrainWords * _max_survivor_regions);
  }
}

bool
G1CollectorPolicy_BestRegionsFirst::should_do_collection_pause(size_t
                                                               word_size) {
  assert(_g1->regions_accounted_for(), "Region leakage!");
  // Initiate a pause when we reach the steady-state "used" target.
  size_t used_hard = (_g1->capacity() / 100) * G1SteadyStateUsed;
  size_t used_soft =
   MAX2((_g1->capacity() / 100) * (G1SteadyStateUsed - G1SteadyStateUsedDelta),
        used_hard/2);
  size_t used = _g1->used();

  double max_pause_time_ms = _mmu_tracker->max_gc_time() * 1000.0;

  size_t young_list_length = _g1->young_list_length();
  size_t young_list_max_length = _young_list_target_length;
  if (G1FixedEdenSize) {
    young_list_max_length -= _max_survivor_regions;
  }
  bool reached_target_length = young_list_length >= young_list_max_length;

  if (in_young_gc_mode()) {
    if (reached_target_length) {
      assert( young_list_length > 0 && _g1->young_list_length() > 0,
              "invariant" );
      _target_pause_time_ms = max_pause_time_ms;
      return true;
    }
  } else {
    guarantee( false, "should not reach here" );
  }

  return false;
}

#ifndef PRODUCT
class HRSortIndexIsOKClosure: public HeapRegionClosure {
  CollectionSetChooser* _chooser;
public:
  HRSortIndexIsOKClosure(CollectionSetChooser* chooser) :
    _chooser(chooser) {}

  bool doHeapRegion(HeapRegion* r) {
    if (!r->continuesHumongous()) {
      assert(_chooser->regionProperlyOrdered(r), "Ought to be.");
    }
    return false;
  }
};

bool G1CollectorPolicy_BestRegionsFirst::assertMarkedBytesDataOK() {
  HRSortIndexIsOKClosure cl(_collectionSetChooser);
  _g1->heap_region_iterate(&cl);
  return true;
}
#endif

void
G1CollectorPolicy_BestRegionsFirst::
record_collection_pause_start(double start_time_sec, size_t start_used) {
  G1CollectorPolicy::record_collection_pause_start(start_time_sec, start_used);
}

class NextNonCSElemFinder: public HeapRegionClosure {
  HeapRegion* _res;
public:
  NextNonCSElemFinder(): _res(NULL) {}
  bool doHeapRegion(HeapRegion* r) {
    if (!r->in_collection_set()) {
      _res = r;
      return true;
    } else {
      return false;
    }
  }
  HeapRegion* res() { return _res; }
};

class KnownGarbageClosure: public HeapRegionClosure {
  CollectionSetChooser* _hrSorted;

public:
  KnownGarbageClosure(CollectionSetChooser* hrSorted) :
    _hrSorted(hrSorted)
  {}

  bool doHeapRegion(HeapRegion* r) {
    // We only include humongous regions in collection
    // sets when concurrent mark shows that their contained object is
    // unreachable.

    // Do we have any marking information for this region?
    if (r->is_marked()) {
      // We don't include humongous regions in collection
      // sets because we collect them immediately at the end of a marking
      // cycle.  We also don't include young regions because we *must*
      // include them in the next collection pause.
      if (!r->isHumongous() && !r->is_young()) {
        _hrSorted->addMarkedHeapRegion(r);
      }
    }
    return false;
  }
};

class ParKnownGarbageHRClosure: public HeapRegionClosure {
  CollectionSetChooser* _hrSorted;
  jint _marked_regions_added;
  jint _chunk_size;
  jint _cur_chunk_idx;
  jint _cur_chunk_end; // Cur chunk [_cur_chunk_idx, _cur_chunk_end)
  int _worker;
  int _invokes;

  void get_new_chunk() {
    _cur_chunk_idx = _hrSorted->getParMarkedHeapRegionChunk(_chunk_size);
    _cur_chunk_end = _cur_chunk_idx + _chunk_size;
  }
  void add_region(HeapRegion* r) {
    if (_cur_chunk_idx == _cur_chunk_end) {
      get_new_chunk();
    }
    assert(_cur_chunk_idx < _cur_chunk_end, "postcondition");
    _hrSorted->setMarkedHeapRegion(_cur_chunk_idx, r);
    _marked_regions_added++;
    _cur_chunk_idx++;
  }

public:
  ParKnownGarbageHRClosure(CollectionSetChooser* hrSorted,
                           jint chunk_size,
                           int worker) :
    _hrSorted(hrSorted), _chunk_size(chunk_size), _worker(worker),
    _marked_regions_added(0), _cur_chunk_idx(0), _cur_chunk_end(0),
    _invokes(0)
  {}

  bool doHeapRegion(HeapRegion* r) {
    // We only include humongous regions in collection
    // sets when concurrent mark shows that their contained object is
    // unreachable.
    _invokes++;

    // Do we have any marking information for this region?
    if (r->is_marked()) {
      // We don't include humongous regions in collection
      // sets because we collect them immediately at the end of a marking
      // cycle.
      // We also do not include young regions in collection sets
      if (!r->isHumongous() && !r->is_young()) {
        add_region(r);
      }
    }
    return false;
  }
  jint marked_regions_added() { return _marked_regions_added; }
  int invokes() { return _invokes; }
};

class ParKnownGarbageTask: public AbstractGangTask {
  CollectionSetChooser* _hrSorted;
  jint _chunk_size;
  G1CollectedHeap* _g1;
public:
  ParKnownGarbageTask(CollectionSetChooser* hrSorted, jint chunk_size) :
    AbstractGangTask("ParKnownGarbageTask"),
    _hrSorted(hrSorted), _chunk_size(chunk_size),
    _g1(G1CollectedHeap::heap())
  {}

  void work(int i) {
    ParKnownGarbageHRClosure parKnownGarbageCl(_hrSorted, _chunk_size, i);
    // Back to zero for the claim value.
    _g1->heap_region_par_iterate_chunked(&parKnownGarbageCl, i,
                                         HeapRegion::InitialClaimValue);
    jint regions_added = parKnownGarbageCl.marked_regions_added();
    _hrSorted->incNumMarkedHeapRegions(regions_added);
    if (G1PrintParCleanupStats) {
      gclog_or_tty->print("     Thread %d called %d times, added %d regions to list.\n",
                 i, parKnownGarbageCl.invokes(), regions_added);
    }
  }
};

void
G1CollectorPolicy_BestRegionsFirst::
record_concurrent_mark_cleanup_end(size_t freed_bytes,
                                   size_t max_live_bytes) {
  double start;
  if (G1PrintParCleanupStats) start = os::elapsedTime();
  record_concurrent_mark_cleanup_end_work1(freed_bytes, max_live_bytes);

  _collectionSetChooser->clearMarkedHeapRegions();
  double clear_marked_end;
  if (G1PrintParCleanupStats) {
    clear_marked_end = os::elapsedTime();
    gclog_or_tty->print_cr("  clear marked regions + work1: %8.3f ms.",
                  (clear_marked_end - start)*1000.0);
  }
  if (ParallelGCThreads > 0) {
    const size_t OverpartitionFactor = 4;
    const size_t MinChunkSize = 8;
    const size_t ChunkSize =
      MAX2(_g1->n_regions() / (ParallelGCThreads * OverpartitionFactor),
           MinChunkSize);
    _collectionSetChooser->prepareForAddMarkedHeapRegionsPar(_g1->n_regions(),
                                                             ChunkSize);
    ParKnownGarbageTask parKnownGarbageTask(_collectionSetChooser,
                                            (int) ChunkSize);
    _g1->workers()->run_task(&parKnownGarbageTask);

    assert(_g1->check_heap_region_claim_values(HeapRegion::InitialClaimValue),
           "sanity check");
  } else {
    KnownGarbageClosure knownGarbagecl(_collectionSetChooser);
    _g1->heap_region_iterate(&knownGarbagecl);
  }
  double known_garbage_end;
  if (G1PrintParCleanupStats) {
    known_garbage_end = os::elapsedTime();
    gclog_or_tty->print_cr("  compute known garbage: %8.3f ms.",
                  (known_garbage_end - clear_marked_end)*1000.0);
  }
  _collectionSetChooser->sortMarkedHeapRegions();
  double sort_end;
  if (G1PrintParCleanupStats) {
    sort_end = os::elapsedTime();
    gclog_or_tty->print_cr("  sorting: %8.3f ms.",
                  (sort_end - known_garbage_end)*1000.0);
  }

  record_concurrent_mark_cleanup_end_work2();
  double work2_end;
  if (G1PrintParCleanupStats) {
    work2_end = os::elapsedTime();
    gclog_or_tty->print_cr("  work2: %8.3f ms.",
                  (work2_end - sort_end)*1000.0);
  }
}

// Add the heap region to the collection set and return the conservative
// estimate of the number of live bytes.
void G1CollectorPolicy::
add_to_collection_set(HeapRegion* hr) {
  if (G1PrintRegions) {
    gclog_or_tty->print_cr("added region to cset %d:["PTR_FORMAT", "PTR_FORMAT"], "
                  "top "PTR_FORMAT", young %s",
                  hr->hrs_index(), hr->bottom(), hr->end(),
                  hr->top(), (hr->is_young()) ? "YES" : "NO");
  }

  if (_g1->mark_in_progress())
    _g1->concurrent_mark()->registerCSetRegion(hr);

  assert(!hr->in_collection_set(),
              "should not already be in the CSet");
  hr->set_in_collection_set(true);
  hr->set_next_in_collection_set(_collection_set);
  _collection_set = hr;
  _collection_set_size++;
  _collection_set_bytes_used_before += hr->used();
  _g1->register_region_with_in_cset_fast_test(hr);
}

void
G1CollectorPolicy_BestRegionsFirst::
choose_collection_set() {
  double non_young_start_time_sec;
  start_recording_regions();

  guarantee(_target_pause_time_ms > -1.0
            NOT_PRODUCT(|| Universe::heap()->gc_cause() == GCCause::_scavenge_alot),
            "_target_pause_time_ms should have been set!");
#ifndef PRODUCT
  if (_target_pause_time_ms <= -1.0) {
    assert(ScavengeALot && Universe::heap()->gc_cause() == GCCause::_scavenge_alot, "Error");
    _target_pause_time_ms = _mmu_tracker->max_gc_time() * 1000.0;
  }
#endif
  assert(_collection_set == NULL, "Precondition");

  double base_time_ms = predict_base_elapsed_time_ms(_pending_cards);
  double predicted_pause_time_ms = base_time_ms;

  double target_time_ms = _target_pause_time_ms;
  double time_remaining_ms = target_time_ms - base_time_ms;

  // the 10% and 50% values are arbitrary...
  if (time_remaining_ms < 0.10*target_time_ms) {
    time_remaining_ms = 0.50 * target_time_ms;
    _within_target = false;
  } else {
    _within_target = true;
  }

  // We figure out the number of bytes available for future to-space.
  // For new regions without marking information, we must assume the
  // worst-case of complete survival.  If we have marking information for a
  // region, we can bound the amount of live data.  We can add a number of
  // such regions, as long as the sum of the live data bounds does not
  // exceed the available evacuation space.
  size_t max_live_bytes = _g1->free_regions() * HeapRegion::GrainBytes;

  size_t expansion_bytes =
    _g1->expansion_regions() * HeapRegion::GrainBytes;

  _collection_set_bytes_used_before = 0;
  _collection_set_size = 0;

  // Adjust for expansion and slop.
  max_live_bytes = max_live_bytes + expansion_bytes;

  assert(_g1->regions_accounted_for(), "Region leakage!");

  HeapRegion* hr;
  if (in_young_gc_mode()) {
    double young_start_time_sec = os::elapsedTime();

    if (G1PolicyVerbose > 0) {
      gclog_or_tty->print_cr("Adding %d young regions to the CSet",
                    _g1->young_list_length());
    }
    _young_cset_length  = 0;
    _last_young_gc_full = full_young_gcs() ? true : false;
    if (_last_young_gc_full)
      ++_full_young_pause_num;
    else
      ++_partial_young_pause_num;
    hr = _g1->pop_region_from_young_list();
    while (hr != NULL) {

      assert( hr->young_index_in_cset() == -1, "invariant" );
      assert( hr->age_in_surv_rate_group() != -1, "invariant" );
      hr->set_young_index_in_cset((int) _young_cset_length);

      ++_young_cset_length;
      double predicted_time_ms = predict_region_elapsed_time_ms(hr, true);
      time_remaining_ms -= predicted_time_ms;
      predicted_pause_time_ms += predicted_time_ms;
      assert(!hr->in_collection_set(), "invariant");
      add_to_collection_set(hr);
      record_cset_region(hr, true);
      max_live_bytes -= MIN2(hr->max_live_bytes(), max_live_bytes);
      if (G1PolicyVerbose > 0) {
        gclog_or_tty->print_cr("  Added [" PTR_FORMAT ", " PTR_FORMAT") to CS.",
                      hr->bottom(), hr->end());
        gclog_or_tty->print_cr("    (" SIZE_FORMAT " KB left in heap.)",
                      max_live_bytes/K);
      }
      hr = _g1->pop_region_from_young_list();
    }

    record_scan_only_regions(_g1->young_list_scan_only_length());

    double young_end_time_sec = os::elapsedTime();
    _recorded_young_cset_choice_time_ms =
      (young_end_time_sec - young_start_time_sec) * 1000.0;

    non_young_start_time_sec = os::elapsedTime();

    if (_young_cset_length > 0 && _last_young_gc_full) {
      // don't bother adding more regions...
      goto choose_collection_set_end;
    }
  }

  if (!in_young_gc_mode() || !full_young_gcs()) {
    bool should_continue = true;
    NumberSeq seq;
    double avg_prediction = 100000000000000000.0; // something very large
    do {
      hr = _collectionSetChooser->getNextMarkedRegion(time_remaining_ms,
                                                      avg_prediction);
      if (hr != NULL) {
        double predicted_time_ms = predict_region_elapsed_time_ms(hr, false);
        time_remaining_ms -= predicted_time_ms;
        predicted_pause_time_ms += predicted_time_ms;
        add_to_collection_set(hr);
        record_cset_region(hr, false);
        max_live_bytes -= MIN2(hr->max_live_bytes(), max_live_bytes);
        if (G1PolicyVerbose > 0) {
          gclog_or_tty->print_cr("    (" SIZE_FORMAT " KB left in heap.)",
                        max_live_bytes/K);
        }
        seq.add(predicted_time_ms);
        avg_prediction = seq.avg() + seq.sd();
      }
      should_continue =
        ( hr != NULL) &&
        ( (adaptive_young_list_length()) ? time_remaining_ms > 0.0
          : _collection_set_size < _young_list_fixed_length );
    } while (should_continue);

    if (!adaptive_young_list_length() &&
        _collection_set_size < _young_list_fixed_length)
      _should_revert_to_full_young_gcs  = true;
  }

choose_collection_set_end:
  count_CS_bytes_used();

  end_recording_regions();

  double non_young_end_time_sec = os::elapsedTime();
  _recorded_non_young_cset_choice_time_ms =
    (non_young_end_time_sec - non_young_start_time_sec) * 1000.0;
}

void G1CollectorPolicy_BestRegionsFirst::record_full_collection_end() {
  G1CollectorPolicy::record_full_collection_end();
  _collectionSetChooser->updateAfterFullCollection();
}

void G1CollectorPolicy_BestRegionsFirst::
expand_if_possible(size_t numRegions) {
  size_t expansion_bytes = numRegions * HeapRegion::GrainBytes;
  _g1->expand(expansion_bytes);
}

void G1CollectorPolicy_BestRegionsFirst::
record_collection_pause_end(bool abandoned) {
  G1CollectorPolicy::record_collection_pause_end(abandoned);
  assert(assertMarkedBytesDataOK(), "Marked regions not OK at pause end.");
}
