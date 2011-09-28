/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc_implementation/g1/concurrentG1Refine.hpp"
#include "gc_implementation/g1/concurrentMark.hpp"
#include "gc_implementation/g1/concurrentMarkThread.inline.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1CollectorPolicy.hpp"
#include "gc_implementation/g1/g1ErgoVerbose.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#include "gc_implementation/shared/gcPolicyCounters.hpp"
#include "runtime/arguments.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/debug.hpp"

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

// Help class for avoiding interleaved logging
class LineBuffer: public StackObj {

private:
  static const int BUFFER_LEN = 1024;
  static const int INDENT_CHARS = 3;
  char _buffer[BUFFER_LEN];
  int _indent_level;
  int _cur;

  void vappend(const char* format, va_list ap) {
    int res = vsnprintf(&_buffer[_cur], BUFFER_LEN - _cur, format, ap);
    if (res != -1) {
      _cur += res;
    } else {
      DEBUG_ONLY(warning("buffer too small in LineBuffer");)
      _buffer[BUFFER_LEN -1] = 0;
      _cur = BUFFER_LEN; // vsnprintf above should not add to _buffer if we are called again
    }
  }

public:
  explicit LineBuffer(int indent_level): _indent_level(indent_level), _cur(0) {
    for (; (_cur < BUFFER_LEN && _cur < (_indent_level * INDENT_CHARS)); _cur++) {
      _buffer[_cur] = ' ';
    }
  }

#ifndef PRODUCT
  ~LineBuffer() {
    assert(_cur == _indent_level * INDENT_CHARS, "pending data in buffer - append_and_print_cr() not called?");
  }
#endif

  void append(const char* format, ...) {
    va_list ap;
    va_start(ap, format);
    vappend(format, ap);
    va_end(ap);
  }

  void append_and_print_cr(const char* format, ...) {
    va_list ap;
    va_start(ap, format);
    vappend(format, ap);
    va_end(ap);
    gclog_or_tty->print_cr("%s", _buffer);
    _cur = _indent_level * INDENT_CHARS;
  }
};

G1CollectorPolicy::G1CollectorPolicy() :
  _parallel_gc_threads(G1CollectedHeap::use_parallel_gc_threads()
                        ? ParallelGCThreads : 1),

  _n_pauses(0),
  _recent_rs_scan_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),
  _recent_pause_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),
  _recent_rs_sizes(new TruncatedSeq(NumPrevPausesForHeuristics)),
  _recent_gc_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),
  _all_pause_times_ms(new NumberSeq()),
  _stop_world_start(0.0),
  _all_stop_world_times_ms(new NumberSeq()),
  _all_yield_times_ms(new NumberSeq()),
  _using_new_ratio_calculations(false),

  _all_mod_union_times_ms(new NumberSeq()),

  _summary(new Summary()),

  _cur_clear_ct_time_ms(0.0),

  _cur_ref_proc_time_ms(0.0),
  _cur_ref_enq_time_ms(0.0),

#ifndef PRODUCT
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

  _concurrent_mark_remark_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),
  _concurrent_mark_cleanup_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),

  // <NEW PREDICTION>

  _alloc_rate_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _prev_collection_pause_end_ms(0.0),
  _pending_card_diff_seq(new TruncatedSeq(TruncatedSeqLength)),
  _rs_length_diff_seq(new TruncatedSeq(TruncatedSeqLength)),
  _cost_per_card_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _fully_young_cards_per_entry_ratio_seq(new TruncatedSeq(TruncatedSeqLength)),
  _partially_young_cards_per_entry_ratio_seq(
                                         new TruncatedSeq(TruncatedSeqLength)),
  _cost_per_entry_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _partially_young_cost_per_entry_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _cost_per_byte_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _cost_per_byte_ms_during_cm_seq(new TruncatedSeq(TruncatedSeqLength)),
  _constant_other_time_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _young_other_cost_per_region_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _non_young_other_cost_per_region_ms_seq(
                                         new TruncatedSeq(TruncatedSeqLength)),

  _pending_cards_seq(new TruncatedSeq(TruncatedSeqLength)),
  _scanned_cards_seq(new TruncatedSeq(TruncatedSeqLength)),
  _rs_lengths_seq(new TruncatedSeq(TruncatedSeqLength)),

  _pause_time_target_ms((double) MaxGCPauseMillis),

  // </NEW PREDICTION>

  _full_young_gcs(true),
  _full_young_pause_num(0),
  _partial_young_pause_num(0),

  _during_marking(false),
  _in_marking_window(false),
  _in_marking_window_im(false),

  _known_garbage_ratio(0.0),
  _known_garbage_bytes(0),

  _young_gc_eff_seq(new TruncatedSeq(TruncatedSeqLength)),

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
  _initiate_conc_mark_if_possible(false),
  _during_initial_mark_pause(false),
  _should_revert_to_full_young_gcs(false),
  _last_full_young_gc(false),

  _eden_bytes_before_gc(0),
  _survivor_bytes_before_gc(0),
  _capacity_before_gc(0),

  _prev_collection_pause_used_at_end_bytes(0),

  _collection_set(NULL),
  _collection_set_size(0),
  _collection_set_bytes_used_before(0),

  // Incremental CSet attributes
  _inc_cset_build_state(Inactive),
  _inc_cset_head(NULL),
  _inc_cset_tail(NULL),
  _inc_cset_size(0),
  _inc_cset_young_index(0),
  _inc_cset_bytes_used_before(0),
  _inc_cset_max_finger(NULL),
  _inc_cset_recorded_young_bytes(0),
  _inc_cset_recorded_rs_lengths(0),
  _inc_cset_predicted_elapsed_time_ms(0.0),
  _inc_cset_predicted_bytes_to_copy(0),

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
  _survivors_age_table(true),

  _gc_overhead_perc(0.0) {

  // Set up the region size and associated fields. Given that the
  // policy is created before the heap, we have to set this up here,
  // so it's done as soon as possible.
  HeapRegion::setup_heap_region_size(Arguments::min_heap_size());
  HeapRegionRemSet::setup_remset_size();

  G1ErgoVerbose::initialize();
  if (PrintAdaptiveSizePolicy) {
    // Currently, we only use a single switch for all the heuristics.
    G1ErgoVerbose::set_enabled(true);
    // Given that we don't currently have a verboseness level
    // parameter, we'll hardcode this to high. This can be easily
    // changed in the future.
    G1ErgoVerbose::set_level(ErgoHigh);
  } else {
    G1ErgoVerbose::set_enabled(false);
  }

  // Verify PLAB sizes
  const uint region_size = HeapRegion::GrainWords;
  if (YoungPLABSize > region_size || OldPLABSize > region_size) {
    char buffer[128];
    jio_snprintf(buffer, sizeof(buffer), "%sPLABSize should be at most %u",
                 OldPLABSize > region_size ? "Old" : "Young", region_size);
    vm_exit_during_initialization(buffer);
  }

  _recent_prev_end_times_for_all_gcs_sec->add(os::elapsedTime());
  _prev_collection_pause_end_ms = os::elapsedTime() * 1000.0;

  _par_last_gc_worker_start_times_ms = new double[_parallel_gc_threads];
  _par_last_ext_root_scan_times_ms = new double[_parallel_gc_threads];
  _par_last_mark_stack_scan_times_ms = new double[_parallel_gc_threads];

  _par_last_update_rs_times_ms = new double[_parallel_gc_threads];
  _par_last_update_rs_processed_buffers = new double[_parallel_gc_threads];

  _par_last_scan_rs_times_ms = new double[_parallel_gc_threads];

  _par_last_obj_copy_times_ms = new double[_parallel_gc_threads];

  _par_last_termination_times_ms = new double[_parallel_gc_threads];
  _par_last_termination_attempts = new double[_parallel_gc_threads];
  _par_last_gc_worker_end_times_ms = new double[_parallel_gc_threads];
  _par_last_gc_worker_times_ms = new double[_parallel_gc_threads];

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

  // Below, we might need to calculate the pause time target based on
  // the pause interval. When we do so we are going to give G1 maximum
  // flexibility and allow it to do pauses when it needs to. So, we'll
  // arrange that the pause interval to be pause time target + 1 to
  // ensure that a) the pause time target is maximized with respect to
  // the pause interval and b) we maintain the invariant that pause
  // time target < pause interval. If the user does not want this
  // maximum flexibility, they will have to set the pause interval
  // explicitly.

  // First make sure that, if either parameter is set, its value is
  // reasonable.
  if (!FLAG_IS_DEFAULT(MaxGCPauseMillis)) {
    if (MaxGCPauseMillis < 1) {
      vm_exit_during_initialization("MaxGCPauseMillis should be "
                                    "greater than 0");
    }
  }
  if (!FLAG_IS_DEFAULT(GCPauseIntervalMillis)) {
    if (GCPauseIntervalMillis < 1) {
      vm_exit_during_initialization("GCPauseIntervalMillis should be "
                                    "greater than 0");
    }
  }

  // Then, if the pause time target parameter was not set, set it to
  // the default value.
  if (FLAG_IS_DEFAULT(MaxGCPauseMillis)) {
    if (FLAG_IS_DEFAULT(GCPauseIntervalMillis)) {
      // The default pause time target in G1 is 200ms
      FLAG_SET_DEFAULT(MaxGCPauseMillis, 200);
    } else {
      // We do not allow the pause interval to be set without the
      // pause time target
      vm_exit_during_initialization("GCPauseIntervalMillis cannot be set "
                                    "without setting MaxGCPauseMillis");
    }
  }

  // Then, if the interval parameter was not set, set it according to
  // the pause time target (this will also deal with the case when the
  // pause time target is the default value).
  if (FLAG_IS_DEFAULT(GCPauseIntervalMillis)) {
    FLAG_SET_DEFAULT(GCPauseIntervalMillis, MaxGCPauseMillis + 1);
  }

  // Finally, make sure that the two parameters are consistent.
  if (MaxGCPauseMillis >= GCPauseIntervalMillis) {
    char buffer[256];
    jio_snprintf(buffer, 256,
                 "MaxGCPauseMillis (%u) should be less than "
                 "GCPauseIntervalMillis (%u)",
                 MaxGCPauseMillis, GCPauseIntervalMillis);
    vm_exit_during_initialization(buffer);
  }

  double max_gc_time = (double) MaxGCPauseMillis / 1000.0;
  double time_slice  = (double) GCPauseIntervalMillis / 1000.0;
  _mmu_tracker = new G1MMUTrackerQueue(time_slice, max_gc_time);
  _sigma = (double) G1ConfidencePercent / 100.0;

  // start conservatively (around 50ms is about right)
  _concurrent_mark_remark_times_ms->add(0.05);
  _concurrent_mark_cleanup_times_ms->add(0.20);
  _tenuring_threshold = MaxTenuringThreshold;
  // _max_survivor_regions will be calculated by
  // update_young_list_target_length() during initialization.
  _max_survivor_regions = 0;

  assert(GCTimeRatio > 0,
         "we should have set it to a default value set_g1_gc_flags() "
         "if a user set it to 0");
  _gc_overhead_perc = 100.0 * (1.0 / (1.0 + GCTimeRatio));

  uintx reserve_perc = G1ReservePercent;
  // Put an artificial ceiling on this so that it's not set to a silly value.
  if (reserve_perc > 50) {
    reserve_perc = 50;
    warning("G1ReservePercent is set to a value that is too large, "
            "it's been updated to %u", reserve_perc);
  }
  _reserve_factor = (double) reserve_perc / 100.0;
  // This will be set when the heap is expanded
  // for the first time during initialization.
  _reserve_regions = 0;

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

// The easiest way to deal with the parsing of the NewSize /
// MaxNewSize / etc. parameteres is to re-use the code in the
// TwoGenerationCollectorPolicy class. This is similar to what
// ParallelScavenge does with its GenerationSizer class (see
// ParallelScavengeHeap::initialize()). We might change this in the
// future, but it's a good start.
class G1YoungGenSizer : public TwoGenerationCollectorPolicy {
private:
  size_t size_to_region_num(size_t byte_size) {
    return MAX2((size_t) 1, byte_size / HeapRegion::GrainBytes);
  }

public:
  G1YoungGenSizer() {
    initialize_flags();
    initialize_size_info();
  }
  size_t min_young_region_num() {
    return size_to_region_num(_min_gen0_size);
  }
  size_t initial_young_region_num() {
    return size_to_region_num(_initial_gen0_size);
  }
  size_t max_young_region_num() {
    return size_to_region_num(_max_gen0_size);
  }
};

void G1CollectorPolicy::update_young_list_size_using_newratio(size_t number_of_heap_regions) {
  assert(number_of_heap_regions > 0, "Heap must be initialized");
  size_t young_size = number_of_heap_regions / (NewRatio + 1);
  _min_desired_young_length = young_size;
  _max_desired_young_length = young_size;
}

void G1CollectorPolicy::init() {
  // Set aside an initial future to_space.
  _g1 = G1CollectedHeap::heap();

  assert(Heap_lock->owned_by_self(), "Locking discipline.");

  initialize_gc_policy_counters();

  G1YoungGenSizer sizer;
  size_t initial_region_num = sizer.initial_young_region_num();
  _min_desired_young_length = sizer.min_young_region_num();
  _max_desired_young_length = sizer.max_young_region_num();

  if (FLAG_IS_CMDLINE(NewRatio)) {
    if (FLAG_IS_CMDLINE(NewSize) || FLAG_IS_CMDLINE(MaxNewSize)) {
      warning("-XX:NewSize and -XX:MaxNewSize override -XX:NewRatio");
    } else {
      // Treat NewRatio as a fixed size that is only recalculated when the heap size changes
      update_young_list_size_using_newratio(_g1->n_regions());
      _using_new_ratio_calculations = true;
    }
  }

  // GenCollectorPolicy guarantees that min <= initial <= max.
  // Asserting here just to state that we rely on this property.
  assert(_min_desired_young_length <= _max_desired_young_length, "Invalid min/max young gen size values");
  assert(initial_region_num <= _max_desired_young_length, "Initial young gen size too large");
  assert(_min_desired_young_length <= initial_region_num, "Initial young gen size too small");

  set_adaptive_young_list_length(_min_desired_young_length < _max_desired_young_length);
  if (adaptive_young_list_length()) {
    _young_list_fixed_length = 0;
  } else {
    _young_list_fixed_length = initial_region_num;
  }
  _free_regions_at_end_of_collection = _g1->free_regions();
  update_young_list_target_length();
  _prev_eden_capacity = _young_list_target_length * HeapRegion::GrainBytes;

  // We may immediately start allocating regions and placing them on the
  // collection set list. Initialize the per-collection set info
  start_incremental_cset_building();
}

// Create the jstat counters for the policy.
void G1CollectorPolicy::initialize_gc_policy_counters() {
  _gc_policy_counters = new GCPolicyCounters("GarbageFirst", 1, 3);
}

bool G1CollectorPolicy::predict_will_fit(size_t young_length,
                                         double base_time_ms,
                                         size_t base_free_regions,
                                         double target_pause_time_ms) {
  if (young_length >= base_free_regions) {
    // end condition 1: not enough space for the young regions
    return false;
  }

  double accum_surv_rate = accum_yg_surv_rate_pred((int)(young_length - 1));
  size_t bytes_to_copy =
               (size_t) (accum_surv_rate * (double) HeapRegion::GrainBytes);
  double copy_time_ms = predict_object_copy_time_ms(bytes_to_copy);
  double young_other_time_ms = predict_young_other_time_ms(young_length);
  double pause_time_ms = base_time_ms + copy_time_ms + young_other_time_ms;
  if (pause_time_ms > target_pause_time_ms) {
    // end condition 2: prediction is over the target pause time
    return false;
  }

  size_t free_bytes =
                  (base_free_regions - young_length) * HeapRegion::GrainBytes;
  if ((2.0 * sigma()) * (double) bytes_to_copy > (double) free_bytes) {
    // end condition 3: out-of-space (conservatively!)
    return false;
  }

  // success!
  return true;
}

void G1CollectorPolicy::record_new_heap_size(size_t new_number_of_regions) {
  // re-calculate the necessary reserve
  double reserve_regions_d = (double) new_number_of_regions * _reserve_factor;
  // We use ceiling so that if reserve_regions_d is > 0.0 (but
  // smaller than 1.0) we'll get 1.
  _reserve_regions = (size_t) ceil(reserve_regions_d);

  if (_using_new_ratio_calculations) {
    // -XX:NewRatio was specified so we need to update the
    // young gen length when the heap size has changed.
    update_young_list_size_using_newratio(new_number_of_regions);
  }
}

size_t G1CollectorPolicy::calculate_young_list_desired_min_length(
                                                     size_t base_min_length) {
  size_t desired_min_length = 0;
  if (adaptive_young_list_length()) {
    if (_alloc_rate_ms_seq->num() > 3) {
      double now_sec = os::elapsedTime();
      double when_ms = _mmu_tracker->when_max_gc_sec(now_sec) * 1000.0;
      double alloc_rate_ms = predict_alloc_rate_ms();
      desired_min_length = (size_t) ceil(alloc_rate_ms * when_ms);
    } else {
      // otherwise we don't have enough info to make the prediction
    }
  }
  desired_min_length += base_min_length;
  // make sure we don't go below any user-defined minimum bound
  return MAX2(_min_desired_young_length, desired_min_length);
}

size_t G1CollectorPolicy::calculate_young_list_desired_max_length() {
  // Here, we might want to also take into account any additional
  // constraints (i.e., user-defined minimum bound). Currently, we
  // effectively don't set this bound.
  return _max_desired_young_length;
}

void G1CollectorPolicy::update_young_list_target_length(size_t rs_lengths) {
  if (rs_lengths == (size_t) -1) {
    // if it's set to the default value (-1), we should predict it;
    // otherwise, use the given value.
    rs_lengths = (size_t) get_new_prediction(_rs_lengths_seq);
  }

  // Calculate the absolute and desired min bounds.

  // This is how many young regions we already have (currently: the survivors).
  size_t base_min_length = recorded_survivor_regions();
  // This is the absolute minimum young length, which ensures that we
  // can allocate one eden region in the worst-case.
  size_t absolute_min_length = base_min_length + 1;
  size_t desired_min_length =
                     calculate_young_list_desired_min_length(base_min_length);
  if (desired_min_length < absolute_min_length) {
    desired_min_length = absolute_min_length;
  }

  // Calculate the absolute and desired max bounds.

  // We will try our best not to "eat" into the reserve.
  size_t absolute_max_length = 0;
  if (_free_regions_at_end_of_collection > _reserve_regions) {
    absolute_max_length = _free_regions_at_end_of_collection - _reserve_regions;
  }
  size_t desired_max_length = calculate_young_list_desired_max_length();
  if (desired_max_length > absolute_max_length) {
    desired_max_length = absolute_max_length;
  }

  size_t young_list_target_length = 0;
  if (adaptive_young_list_length()) {
    if (full_young_gcs()) {
      young_list_target_length =
                        calculate_young_list_target_length(rs_lengths,
                                                           base_min_length,
                                                           desired_min_length,
                                                           desired_max_length);
      _rs_lengths_prediction = rs_lengths;
    } else {
      // Don't calculate anything and let the code below bound it to
      // the desired_min_length, i.e., do the next GC as soon as
      // possible to maximize how many old regions we can add to it.
    }
  } else {
    if (full_young_gcs()) {
      young_list_target_length = _young_list_fixed_length;
    } else {
      // A bit arbitrary: during partially-young GCs we allocate half
      // the young regions to try to add old regions to the CSet.
      young_list_target_length = _young_list_fixed_length / 2;
      // We choose to accept that we might go under the desired min
      // length given that we intentionally ask for a smaller young gen.
      desired_min_length = absolute_min_length;
    }
  }

  // Make sure we don't go over the desired max length, nor under the
  // desired min length. In case they clash, desired_min_length wins
  // which is why that test is second.
  if (young_list_target_length > desired_max_length) {
    young_list_target_length = desired_max_length;
  }
  if (young_list_target_length < desired_min_length) {
    young_list_target_length = desired_min_length;
  }

  assert(young_list_target_length > recorded_survivor_regions(),
         "we should be able to allocate at least one eden region");
  assert(young_list_target_length >= absolute_min_length, "post-condition");
  _young_list_target_length = young_list_target_length;

  update_max_gc_locker_expansion();
}

size_t
G1CollectorPolicy::calculate_young_list_target_length(size_t rs_lengths,
                                                   size_t base_min_length,
                                                   size_t desired_min_length,
                                                   size_t desired_max_length) {
  assert(adaptive_young_list_length(), "pre-condition");
  assert(full_young_gcs(), "only call this for fully-young GCs");

  // In case some edge-condition makes the desired max length too small...
  if (desired_max_length <= desired_min_length) {
    return desired_min_length;
  }

  // We'll adjust min_young_length and max_young_length not to include
  // the already allocated young regions (i.e., so they reflect the
  // min and max eden regions we'll allocate). The base_min_length
  // will be reflected in the predictions by the
  // survivor_regions_evac_time prediction.
  assert(desired_min_length > base_min_length, "invariant");
  size_t min_young_length = desired_min_length - base_min_length;
  assert(desired_max_length > base_min_length, "invariant");
  size_t max_young_length = desired_max_length - base_min_length;

  double target_pause_time_ms = _mmu_tracker->max_gc_time() * 1000.0;
  double survivor_regions_evac_time = predict_survivor_regions_evac_time();
  size_t pending_cards = (size_t) get_new_prediction(_pending_cards_seq);
  size_t adj_rs_lengths = rs_lengths + predict_rs_length_diff();
  size_t scanned_cards = predict_young_card_num(adj_rs_lengths);
  double base_time_ms =
    predict_base_elapsed_time_ms(pending_cards, scanned_cards) +
    survivor_regions_evac_time;
  size_t available_free_regions = _free_regions_at_end_of_collection;
  size_t base_free_regions = 0;
  if (available_free_regions > _reserve_regions) {
    base_free_regions = available_free_regions - _reserve_regions;
  }

  // Here, we will make sure that the shortest young length that
  // makes sense fits within the target pause time.

  if (predict_will_fit(min_young_length, base_time_ms,
                       base_free_regions, target_pause_time_ms)) {
    // The shortest young length will fit into the target pause time;
    // we'll now check whether the absolute maximum number of young
    // regions will fit in the target pause time. If not, we'll do
    // a binary search between min_young_length and max_young_length.
    if (predict_will_fit(max_young_length, base_time_ms,
                         base_free_regions, target_pause_time_ms)) {
      // The maximum young length will fit into the target pause time.
      // We are done so set min young length to the maximum length (as
      // the result is assumed to be returned in min_young_length).
      min_young_length = max_young_length;
    } else {
      // The maximum possible number of young regions will not fit within
      // the target pause time so we'll search for the optimal
      // length. The loop invariants are:
      //
      // min_young_length < max_young_length
      // min_young_length is known to fit into the target pause time
      // max_young_length is known not to fit into the target pause time
      //
      // Going into the loop we know the above hold as we've just
      // checked them. Every time around the loop we check whether
      // the middle value between min_young_length and
      // max_young_length fits into the target pause time. If it
      // does, it becomes the new min. If it doesn't, it becomes
      // the new max. This way we maintain the loop invariants.

      assert(min_young_length < max_young_length, "invariant");
      size_t diff = (max_young_length - min_young_length) / 2;
      while (diff > 0) {
        size_t young_length = min_young_length + diff;
        if (predict_will_fit(young_length, base_time_ms,
                             base_free_regions, target_pause_time_ms)) {
          min_young_length = young_length;
        } else {
          max_young_length = young_length;
        }
        assert(min_young_length <  max_young_length, "invariant");
        diff = (max_young_length - min_young_length) / 2;
      }
      // The results is min_young_length which, according to the
      // loop invariants, should fit within the target pause time.

      // These are the post-conditions of the binary search above:
      assert(min_young_length < max_young_length,
             "otherwise we should have discovered that max_young_length "
             "fits into the pause target and not done the binary search");
      assert(predict_will_fit(min_young_length, base_time_ms,
                              base_free_regions, target_pause_time_ms),
             "min_young_length, the result of the binary search, should "
             "fit into the pause target");
      assert(!predict_will_fit(min_young_length + 1, base_time_ms,
                               base_free_regions, target_pause_time_ms),
             "min_young_length, the result of the binary search, should be "
             "optimal, so no larger length should fit into the pause target");
    }
  } else {
    // Even the minimum length doesn't fit into the pause time
    // target, return it as the result nevertheless.
  }
  return base_min_length + min_young_length;
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

void G1CollectorPolicy::revise_young_list_target_length_if_necessary() {
  guarantee( adaptive_young_list_length(), "should not call this otherwise" );

  size_t rs_lengths = _g1->young_list()->sampled_rs_lengths();
  if (rs_lengths > _rs_lengths_prediction) {
    // add 10% to avoid having to recalculate often
    size_t rs_lengths_prediction = rs_lengths * 1100 / 1000;
    update_young_list_target_length(rs_lengths_prediction);
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
  HeapRegion* head = _g1->young_list()->first_region();
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
  clear_initiate_conc_mark_if_possible();
  clear_during_initial_mark_pause();
  _known_garbage_bytes = 0;
  _known_garbage_ratio = 0.0;
  _in_marking_window = false;
  _in_marking_window_im = false;

  _short_lived_surv_rate_group->start_adding_regions();
  // also call this on any additional surv rate groups

  record_survivor_regions(0, NULL, NULL);

  _prev_region_num_young   = _region_num_young;
  _prev_region_num_tenured = _region_num_tenured;

  _free_regions_at_end_of_collection = _g1->free_regions();
  // Reset survivors SurvRateGroup.
  _survivor_surv_rate_group->reset();
  update_young_list_target_length();
}

void G1CollectorPolicy::record_stop_world_start() {
  _stop_world_start = os::elapsedTime();
}

void G1CollectorPolicy::record_collection_pause_start(double start_time_sec,
                                                      size_t start_used) {
  if (PrintGCDetails) {
    gclog_or_tty->stamp(PrintGCTimeStamps);
    gclog_or_tty->print("[GC pause");
    gclog_or_tty->print(" (%s)", full_young_gcs() ? "young" : "partial");
  }

  // We only need to do this here as the policy will only be applied
  // to the GC we're about to start. so, no point is calculating this
  // every time we calculate / recalculate the target young length.
  update_survivors_policy();

  assert(_g1->used() == _g1->recalculate_used(),
         err_msg("sanity, used: "SIZE_FORMAT" recalculate_used: "SIZE_FORMAT,
                 _g1->used(), _g1->recalculate_used()));

  double s_w_t_ms = (start_time_sec - _stop_world_start) * 1000.0;
  _all_stop_world_times_ms->add(s_w_t_ms);
  _stop_world_start = 0.0;

  _cur_collection_start_sec = start_time_sec;
  _cur_collection_pause_used_at_start_bytes = start_used;
  _cur_collection_pause_used_regions_at_start = _g1->used_regions();
  _pending_cards = _g1->pending_card_num();
  _max_pending_cards = _g1->max_pending_card_num();

  _bytes_in_collection_set_before_gc = 0;
  _bytes_copied_during_gc = 0;

  YoungList* young_list = _g1->young_list();
  _eden_bytes_before_gc = young_list->eden_used_bytes();
  _survivor_bytes_before_gc = young_list->survivor_used_bytes();
  _capacity_before_gc = _g1->capacity();

#ifdef DEBUG
  // initialise these to something well known so that we can spot
  // if they are not set properly

  for (int i = 0; i < _parallel_gc_threads; ++i) {
    _par_last_gc_worker_start_times_ms[i] = -1234.0;
    _par_last_ext_root_scan_times_ms[i] = -1234.0;
    _par_last_mark_stack_scan_times_ms[i] = -1234.0;
    _par_last_update_rs_times_ms[i] = -1234.0;
    _par_last_update_rs_processed_buffers[i] = -1234.0;
    _par_last_scan_rs_times_ms[i] = -1234.0;
    _par_last_obj_copy_times_ms[i] = -1234.0;
    _par_last_termination_times_ms[i] = -1234.0;
    _par_last_termination_attempts[i] = -1234.0;
    _par_last_gc_worker_end_times_ms[i] = -1234.0;
    _par_last_gc_worker_times_ms[i] = -1234.0;
  }
#endif

  for (int i = 0; i < _aux_num; ++i) {
    _cur_aux_times_ms[i] = 0.0;
    _cur_aux_times_set[i] = false;
  }

  _satb_drain_time_set = false;
  _last_satb_drain_processed_buffers = -1;

  _last_young_gc_full = false;

  // do that for any other surv rate groups
  _short_lived_surv_rate_group->stop_adding_regions();
  _survivors_age_table.clear();

  assert( verify_young_ages(), "region age verification" );
}

void G1CollectorPolicy::record_mark_closure_time(double mark_closure_time_ms) {
  _mark_closure_time_ms = mark_closure_time_ms;
}

void G1CollectorPolicy::record_concurrent_mark_init_end(double
                                                   mark_init_elapsed_time_ms) {
  _during_marking = true;
  assert(!initiate_conc_mark_if_possible(), "we should have cleared it by now");
  clear_during_initial_mark_pause();
  _cur_mark_stop_world_time_ms = mark_init_elapsed_time_ms;
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
  if (_n_marks < 2) {
    _n_marks++;
  }
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
  _n_pauses_at_mark_end = _n_pauses;
  _n_marks_since_last_pause++;
}

void
G1CollectorPolicy::record_concurrent_mark_cleanup_completed() {
  _should_revert_to_full_young_gcs = false;
  _last_full_young_gc = true;
  _in_marking_window = false;
}

void G1CollectorPolicy::record_concurrent_pause() {
  if (_stop_world_start > 0.0) {
    double yield_ms = (os::elapsedTime() - _stop_world_start) * 1000.0;
    _all_yield_times_ms->add(yield_ms);
  }
}

void G1CollectorPolicy::record_concurrent_pause_end() {
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

void G1CollectorPolicy::print_par_stats(int level,
                                        const char* str,
                                        double* data) {
  double min = data[0], max = data[0];
  double total = 0.0;
  LineBuffer buf(level);
  buf.append("[%s (ms):", str);
  for (uint i = 0; i < ParallelGCThreads; ++i) {
    double val = data[i];
    if (val < min)
      min = val;
    if (val > max)
      max = val;
    total += val;
    buf.append("  %3.1lf", val);
  }
  buf.append_and_print_cr("");
  double avg = total / (double) ParallelGCThreads;
  buf.append_and_print_cr(" Avg: %5.1lf, Min: %5.1lf, Max: %5.1lf, Diff: %5.1lf]",
    avg, min, max, max - min);
}

void G1CollectorPolicy::print_par_sizes(int level,
                                        const char* str,
                                        double* data) {
  double min = data[0], max = data[0];
  double total = 0.0;
  LineBuffer buf(level);
  buf.append("[%s :", str);
  for (uint i = 0; i < ParallelGCThreads; ++i) {
    double val = data[i];
    if (val < min)
      min = val;
    if (val > max)
      max = val;
    total += val;
    buf.append(" %d", (int) val);
  }
  buf.append_and_print_cr("");
  double avg = total / (double) ParallelGCThreads;
  buf.append_and_print_cr(" Sum: %d, Avg: %d, Min: %d, Max: %d, Diff: %d]",
    (int)total, (int)avg, (int)min, (int)max, (int)max - (int)min);
}

void G1CollectorPolicy::print_stats (int level,
                                     const char* str,
                                     double value) {
  LineBuffer(level).append_and_print_cr("[%s: %5.1lf ms]", str, value);
}

void G1CollectorPolicy::print_stats (int level,
                                     const char* str,
                                     int value) {
  LineBuffer(level).append_and_print_cr("[%s: %d]", str, value);
}

double G1CollectorPolicy::avg_value (double* data) {
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    double ret = 0.0;
    for (uint i = 0; i < ParallelGCThreads; ++i)
      ret += data[i];
    return ret / (double) ParallelGCThreads;
  } else {
    return data[0];
  }
}

double G1CollectorPolicy::max_value (double* data) {
  if (G1CollectedHeap::use_parallel_gc_threads()) {
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
  if (G1CollectedHeap::use_parallel_gc_threads()) {
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

  if (G1CollectedHeap::use_parallel_gc_threads()) {
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

void G1CollectorPolicy::record_collection_pause_end() {
  double end_time_sec = os::elapsedTime();
  double elapsed_ms = _last_pause_time_ms;
  bool parallel = G1CollectedHeap::use_parallel_gc_threads();
  size_t rs_size =
    _cur_collection_pause_used_regions_at_start - collection_set_size();
  size_t cur_used_bytes = _g1->used();
  assert(cur_used_bytes == _g1->recalculate_used(), "It should!");
  bool last_pause_included_initial_mark = false;
  bool update_stats = !_g1->evacuation_failed();

#ifndef PRODUCT
  if (G1YoungSurvRateVerbose) {
    gclog_or_tty->print_cr("");
    _short_lived_surv_rate_group->print();
    // do that for any other surv rate groups too
  }
#endif // PRODUCT

  last_pause_included_initial_mark = during_initial_mark_pause();
  if (last_pause_included_initial_mark)
    record_concurrent_mark_init_end(0.0);

  size_t marking_initiating_used_threshold =
    (_g1->capacity() / 100) * InitiatingHeapOccupancyPercent;

  if (!_g1->mark_in_progress() && !_last_full_young_gc) {
    assert(!last_pause_included_initial_mark, "invariant");
    if (cur_used_bytes > marking_initiating_used_threshold) {
      if (cur_used_bytes > _prev_collection_pause_used_at_end_bytes) {
        assert(!during_initial_mark_pause(), "we should not see this here");

        ergo_verbose3(ErgoConcCycles,
                      "request concurrent cycle initiation",
                      ergo_format_reason("occupancy higher than threshold")
                      ergo_format_byte("occupancy")
                      ergo_format_byte_perc("threshold"),
                      cur_used_bytes,
                      marking_initiating_used_threshold,
                      (double) InitiatingHeapOccupancyPercent);

        // Note: this might have already been set, if during the last
        // pause we decided to start a cycle but at the beginning of
        // this pause we decided to postpone it. That's OK.
        set_initiate_conc_mark_if_possible();
      } else {
        ergo_verbose2(ErgoConcCycles,
                  "do not request concurrent cycle initiation",
                  ergo_format_reason("occupancy lower than previous occupancy")
                  ergo_format_byte("occupancy")
                  ergo_format_byte("previous occupancy"),
                  cur_used_bytes,
                  _prev_collection_pause_used_at_end_bytes);
      }
    }
  }

  _prev_collection_pause_used_at_end_bytes = cur_used_bytes;

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

  double ext_root_scan_time = avg_value(_par_last_ext_root_scan_times_ms);
  double mark_stack_scan_time = avg_value(_par_last_mark_stack_scan_times_ms);
  double update_rs_time = avg_value(_par_last_update_rs_times_ms);
  double update_rs_processed_buffers =
    sum_of_values(_par_last_update_rs_processed_buffers);
  double scan_rs_time = avg_value(_par_last_scan_rs_times_ms);
  double obj_copy_time = avg_value(_par_last_obj_copy_times_ms);
  double termination_time = avg_value(_par_last_termination_times_ms);

  double parallel_known_time = update_rs_time +
                               ext_root_scan_time +
                               mark_stack_scan_time +
                               scan_rs_time +
                               obj_copy_time +
                               termination_time;

  double parallel_other_time = _cur_collection_par_time_ms - parallel_known_time;

  PauseSummary* summary = _summary;

  if (update_stats) {
    _recent_rs_scan_times_ms->add(scan_rs_time);
    _recent_pause_times_ms->add(elapsed_ms);
    _recent_rs_sizes->add(rs_size);

    MainBodySummary* body_summary = summary->main_body_summary();
    guarantee(body_summary != NULL, "should not be null!");

    if (_satb_drain_time_set)
      body_summary->record_satb_drain_time_ms(_cur_satb_drain_time_ms);
    else
      body_summary->record_satb_drain_time_ms(0.0);

    body_summary->record_ext_root_scan_time_ms(ext_root_scan_time);
    body_summary->record_mark_stack_scan_time_ms(mark_stack_scan_time);
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

  if (G1PolicyVerbose > 1) {
    gclog_or_tty->print_cr("      ET: %10.6f ms           (avg: %10.6f ms)\n"
                           "       ET-RS:  %10.6f ms      (avg: %10.6f ms)\n"
                           "      |RS|: " SIZE_FORMAT,
                           elapsed_ms, recent_avg_time_for_pauses_ms(),
                           scan_rs_time, recent_avg_time_for_rs_scan_ms(),
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

  if (_satb_drain_time_set) {
    other_time_ms -= _cur_satb_drain_time_ms;
  }

  if (parallel) {
    other_time_ms -= _cur_collection_par_time_ms + _cur_clear_ct_time_ms;
  } else {
    other_time_ms -=
      update_rs_time +
      ext_root_scan_time + mark_stack_scan_time +
      scan_rs_time + obj_copy_time;
  }

  if (PrintGCDetails) {
    gclog_or_tty->print_cr("%s, %1.8lf secs]",
                           (last_pause_included_initial_mark) ? " (initial-mark)" : "",
                           elapsed_ms / 1000.0);

    if (_satb_drain_time_set) {
      print_stats(1, "SATB Drain Time", _cur_satb_drain_time_ms);
    }
    if (_last_satb_drain_processed_buffers >= 0) {
      print_stats(2, "Processed Buffers", _last_satb_drain_processed_buffers);
    }
    if (parallel) {
      print_stats(1, "Parallel Time", _cur_collection_par_time_ms);
      print_par_stats(2, "GC Worker Start Time", _par_last_gc_worker_start_times_ms);
      print_par_stats(2, "Update RS", _par_last_update_rs_times_ms);
      print_par_sizes(3, "Processed Buffers", _par_last_update_rs_processed_buffers);
      print_par_stats(2, "Ext Root Scanning", _par_last_ext_root_scan_times_ms);
      print_par_stats(2, "Mark Stack Scanning", _par_last_mark_stack_scan_times_ms);
      print_par_stats(2, "Scan RS", _par_last_scan_rs_times_ms);
      print_par_stats(2, "Object Copy", _par_last_obj_copy_times_ms);
      print_par_stats(2, "Termination", _par_last_termination_times_ms);
      print_par_sizes(3, "Termination Attempts", _par_last_termination_attempts);
      print_par_stats(2, "GC Worker End Time", _par_last_gc_worker_end_times_ms);

      for (int i = 0; i < _parallel_gc_threads; i++) {
        _par_last_gc_worker_times_ms[i] = _par_last_gc_worker_end_times_ms[i] - _par_last_gc_worker_start_times_ms[i];
      }
      print_par_stats(2, "GC Worker Times", _par_last_gc_worker_times_ms);

      print_stats(2, "Parallel Other", parallel_other_time);
      print_stats(1, "Clear CT", _cur_clear_ct_time_ms);
    } else {
      print_stats(1, "Update RS", update_rs_time);
      print_stats(2, "Processed Buffers",
                  (int)update_rs_processed_buffers);
      print_stats(1, "Ext Root Scanning", ext_root_scan_time);
      print_stats(1, "Mark Stack Scanning", mark_stack_scan_time);
      print_stats(1, "Scan RS", scan_rs_time);
      print_stats(1, "Object Copying", obj_copy_time);
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
    print_stats(2, "Choose CSet", _recorded_young_cset_choice_time_ms);
    print_stats(2, "Ref Proc", _cur_ref_proc_time_ms);
    print_stats(2, "Ref Enq", _cur_ref_enq_time_ms);

    for (int i = 0; i < _aux_num; ++i) {
      if (_cur_aux_times_set[i]) {
        char buffer[96];
        sprintf(buffer, "Aux%d", i);
        print_stats(1, buffer, _cur_aux_times_ms[i]);
      }
    }
  }

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
  if (during_initial_mark_pause()) {
    new_in_marking_window = true;
    new_in_marking_window_im = true;
  }

  if (_last_full_young_gc) {
    if (!last_pause_included_initial_mark) {
      ergo_verbose2(ErgoPartiallyYoungGCs,
                    "start partially-young GCs",
                    ergo_format_byte_perc("known garbage"),
                    _known_garbage_bytes, _known_garbage_ratio * 100.0);
      set_full_young_gcs(false);
    } else {
      ergo_verbose0(ErgoPartiallyYoungGCs,
                    "do not start partially-young GCs",
                    ergo_format_reason("concurrent cycle is about to start"));
    }
    _last_full_young_gc = false;
  }

  if ( !_last_young_gc_full ) {
    if (_should_revert_to_full_young_gcs) {
      ergo_verbose2(ErgoPartiallyYoungGCs,
                    "end partially-young GCs",
                    ergo_format_reason("partially-young GCs end requested")
                    ergo_format_byte_perc("known garbage"),
                    _known_garbage_bytes, _known_garbage_ratio * 100.0);
      set_full_young_gcs(true);
    } else if (_known_garbage_ratio < 0.05) {
      ergo_verbose3(ErgoPartiallyYoungGCs,
               "end partially-young GCs",
               ergo_format_reason("known garbage percent lower than threshold")
               ergo_format_byte_perc("known garbage")
               ergo_format_perc("threshold"),
               _known_garbage_bytes, _known_garbage_ratio * 100.0,
               0.05 * 100.0);
      set_full_young_gcs(true);
    } else if (adaptive_young_list_length() &&
              (get_gc_eff_factor() * cur_efficiency < predict_young_gc_eff())) {
      ergo_verbose5(ErgoPartiallyYoungGCs,
                    "end partially-young GCs",
                    ergo_format_reason("current GC efficiency lower than "
                                       "predicted fully-young GC efficiency")
                    ergo_format_double("GC efficiency factor")
                    ergo_format_double("current GC efficiency")
                    ergo_format_double("predicted fully-young GC efficiency")
                    ergo_format_byte_perc("known garbage"),
                    get_gc_eff_factor(), cur_efficiency,
                    predict_young_gc_eff(),
                    _known_garbage_bytes, _known_garbage_ratio * 100.0);
      set_full_young_gcs(true);
    }
  }
  _should_revert_to_full_young_gcs = false;

  if (_last_young_gc_full && !_during_marking) {
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
      (update_rs_time + scan_rs_time + obj_copy_time +
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
      survival_ratio = (double) _bytes_copied_during_gc /
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
                    "REGIONS %d %d %d "
                    "PENDING_CARDS %d %d "
                    "CARDS_SCANNED %d %d "
                    "RS_LENGTHS %d %d "
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
                    _recorded_non_young_regions,
                    _predicted_pending_cards, _pending_cards,
                    _predicted_cards_scanned, cards_scanned,
                    _predicted_rs_lengths, _max_rs_lengths,
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
  update_young_list_target_length();

  // Note that _mmu_tracker->max_gc_time() returns the time in seconds.
  double update_rs_time_goal_ms = _mmu_tracker->max_gc_time() * MILLIUNITS * G1RSetUpdatingPauseTimePercent / 100.0;
  adjust_concurrent_refinement(update_rs_time, update_rs_processed_buffers, update_rs_time_goal_ms);
  // </NEW PREDICTION>
}

#define EXT_SIZE_FORMAT "%d%s"
#define EXT_SIZE_PARAMS(bytes)                                  \
  byte_size_in_proper_unit((bytes)),                            \
  proper_unit_for_byte_size((bytes))

void G1CollectorPolicy::print_heap_transition() {
  if (PrintGCDetails) {
    YoungList* young_list = _g1->young_list();
    size_t eden_bytes = young_list->eden_used_bytes();
    size_t survivor_bytes = young_list->survivor_used_bytes();
    size_t used_before_gc = _cur_collection_pause_used_at_start_bytes;
    size_t used = _g1->used();
    size_t capacity = _g1->capacity();
    size_t eden_capacity =
      (_young_list_target_length * HeapRegion::GrainBytes) - survivor_bytes;

    gclog_or_tty->print_cr(
      "   [Eden: "EXT_SIZE_FORMAT"("EXT_SIZE_FORMAT")->"EXT_SIZE_FORMAT"("EXT_SIZE_FORMAT") "
      "Survivors: "EXT_SIZE_FORMAT"->"EXT_SIZE_FORMAT" "
      "Heap: "EXT_SIZE_FORMAT"("EXT_SIZE_FORMAT")->"
      EXT_SIZE_FORMAT"("EXT_SIZE_FORMAT")]",
      EXT_SIZE_PARAMS(_eden_bytes_before_gc),
      EXT_SIZE_PARAMS(_prev_eden_capacity),
      EXT_SIZE_PARAMS(eden_bytes),
      EXT_SIZE_PARAMS(eden_capacity),
      EXT_SIZE_PARAMS(_survivor_bytes_before_gc),
      EXT_SIZE_PARAMS(survivor_bytes),
      EXT_SIZE_PARAMS(used_before_gc),
      EXT_SIZE_PARAMS(_capacity_before_gc),
      EXT_SIZE_PARAMS(used),
      EXT_SIZE_PARAMS(capacity));

    _prev_eden_capacity = eden_capacity;
  } else if (PrintGC) {
    _g1->print_size_transition(gclog_or_tty,
                               _cur_collection_pause_used_at_start_bytes,
                               _g1->used(), _g1->capacity());
  }
}

// <NEW PREDICTION>

void G1CollectorPolicy::adjust_concurrent_refinement(double update_rs_time,
                                                     double update_rs_processed_buffers,
                                                     double goal_ms) {
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  ConcurrentG1Refine *cg1r = G1CollectedHeap::heap()->concurrent_g1_refine();

  if (G1UseAdaptiveConcRefinement) {
    const int k_gy = 3, k_gr = 6;
    const double inc_k = 1.1, dec_k = 0.9;

    int g = cg1r->green_zone();
    if (update_rs_time > goal_ms) {
      g = (int)(g * dec_k);  // Can become 0, that's OK. That would mean a mutator-only processing.
    } else {
      if (update_rs_time < goal_ms && update_rs_processed_buffers > g) {
        g = (int)MAX2(g * inc_k, g + 1.0);
      }
    }
    // Change the refinement threads params
    cg1r->set_green_zone(g);
    cg1r->set_yellow_zone(g * k_gy);
    cg1r->set_red_zone(g * k_gr);
    cg1r->reinitialize_threads();

    int processing_threshold_delta = MAX2((int)(cg1r->green_zone() * sigma()), 1);
    int processing_threshold = MIN2(cg1r->green_zone() + processing_threshold_delta,
                                    cg1r->yellow_zone());
    // Change the barrier params
    dcqs.set_process_completed_threshold(processing_threshold);
    dcqs.set_max_completed_queue(cg1r->red_zone());
  }

  int curr_queue_size = dcqs.completed_buffers_num();
  if (curr_queue_size >= cg1r->yellow_zone()) {
    dcqs.set_completed_queue_padding(curr_queue_size);
  } else {
    dcqs.set_completed_queue_padding(0);
  }
  dcqs.notify_if_necessary();
}

double
G1CollectorPolicy::
predict_young_collection_elapsed_time_ms(size_t adjustment) {
  guarantee( adjustment == 0 || adjustment == 1, "invariant" );

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  size_t young_num = g1h->young_list()->length();
  if (young_num == 0)
    return 0.0;

  young_num += adjustment;
  size_t pending_cards = predict_pending_cards();
  size_t rs_lengths = g1h->young_list()->sampled_rs_lengths() +
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
  _recorded_young_regions         = 0;
  _recorded_non_young_regions     = 0;

#if PREDICTIONS_VERBOSE
  _recorded_marked_bytes          = 0;
  _recorded_young_bytes           = 0;
  _predicted_bytes_to_copy        = 0;
  _predicted_rs_lengths           = 0;
  _predicted_cards_scanned        = 0;
#endif // PREDICTIONS_VERBOSE
}

void
G1CollectorPolicy::record_cset_region_info(HeapRegion* hr, bool young) {
#if PREDICTIONS_VERBOSE
  if (!young) {
    _recorded_marked_bytes += hr->max_live_bytes();
  }
  _predicted_bytes_to_copy += predict_bytes_to_copy(hr);
#endif // PREDICTIONS_VERBOSE

  size_t rs_length = hr->rem_set()->occupied();
  _recorded_rs_lengths += rs_length;
}

void
G1CollectorPolicy::record_non_young_cset_region(HeapRegion* hr) {
  assert(!hr->is_young(), "should not call this");
  ++_recorded_non_young_regions;
  record_cset_region_info(hr, false);
}

void
G1CollectorPolicy::set_recorded_young_regions(size_t n_regions) {
  _recorded_young_regions = n_regions;
}

void G1CollectorPolicy::set_recorded_young_bytes(size_t bytes) {
#if PREDICTIONS_VERBOSE
  _recorded_young_bytes = bytes;
#endif // PREDICTIONS_VERBOSE
}

void G1CollectorPolicy::set_recorded_rs_lengths(size_t rs_lengths) {
  _recorded_rs_lengths = rs_lengths;
}

void G1CollectorPolicy::set_predicted_bytes_to_copy(size_t bytes) {
  _predicted_bytes_to_copy = bytes;
}

void
G1CollectorPolicy::end_recording_regions() {
  // The _predicted_pause_time_ms field is referenced in code
  // not under PREDICTIONS_VERBOSE. Let's initialize it.
  _predicted_pause_time_ms = -1.0;

#if PREDICTIONS_VERBOSE
  _predicted_pending_cards = predict_pending_cards();
  _predicted_rs_lengths = _recorded_rs_lengths + predict_rs_length_diff();
  if (full_young_gcs())
    _predicted_cards_scanned += predict_young_card_num(_predicted_rs_lengths);
  else
    _predicted_cards_scanned +=
      predict_non_young_card_num(_predicted_rs_lengths);
  _recorded_region_num = _recorded_young_regions + _recorded_non_young_regions;

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
    ergo_verbose2(ErgoPartiallyYoungGCs,
              "request partially-young GCs end",
              ergo_format_reason("predicted region time higher than threshold")
              ergo_format_ms("predicted region time")
              ergo_format_ms("threshold"),
              predicted_time_ms, _expensive_region_limit_ms);
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
  if (_recent_pause_times_ms->num() == 0) {
    return (double) MaxGCPauseMillis;
  }
  return _recent_pause_times_ms->avg();
}

double G1CollectorPolicy::recent_avg_time_for_rs_scan_ms() {
  if (_recent_rs_scan_times_ms->num() == 0) {
    return (double)MaxGCPauseMillis/3.0;
  }
  return _recent_rs_scan_times_ms->avg();
}

int G1CollectorPolicy::number_of_recent_gcs() {
  assert(_recent_rs_scan_times_ms->num() ==
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
      assert((true || G1CollectedHeap::use_parallel_gc_threads()) ||
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
    assert((true || G1CollectedHeap::use_parallel_gc_threads()) ||
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
  double recent_gc_overhead = recent_avg_pause_time_ratio() * 100.0;
  double threshold = _gc_overhead_perc;
  if (recent_gc_overhead > threshold) {
    // We will double the existing space, or take
    // G1ExpandByPercentOfAvailable % of the available expansion
    // space, whichever is smaller, bounded below by a minimum
    // expansion (unless that's all that's left.)
    const size_t min_expand_bytes = 1*M;
    size_t reserved_bytes = _g1->max_capacity();
    size_t committed_bytes = _g1->capacity();
    size_t uncommitted_bytes = reserved_bytes - committed_bytes;
    size_t expand_bytes;
    size_t expand_bytes_via_pct =
      uncommitted_bytes * G1ExpandByPercentOfAvailable / 100;
    expand_bytes = MIN2(expand_bytes_via_pct, committed_bytes);
    expand_bytes = MAX2(expand_bytes, min_expand_bytes);
    expand_bytes = MIN2(expand_bytes, uncommitted_bytes);

    ergo_verbose5(ErgoHeapSizing,
                  "attempt heap expansion",
                  ergo_format_reason("recent GC overhead higher than "
                                     "threshold after GC")
                  ergo_format_perc("recent GC overhead")
                  ergo_format_perc("threshold")
                  ergo_format_byte("uncommitted")
                  ergo_format_byte_perc("calculated expansion amount"),
                  recent_gc_overhead, threshold,
                  uncommitted_bytes,
                  expand_bytes_via_pct, (double) G1ExpandByPercentOfAvailable);

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

void G1CollectorPolicy::print_summary (int level,
                                       const char* str,
                                       NumberSeq* seq) const {
  double sum = seq->sum();
  LineBuffer(level + 1).append_and_print_cr("%-24s = %8.2lf s (avg = %8.2lf ms)",
                str, sum / 1000.0, seq->avg());
}

void G1CollectorPolicy::print_summary_sd (int level,
                                          const char* str,
                                          NumberSeq* seq) const {
  print_summary(level, str, seq);
  LineBuffer(level + 6).append_and_print_cr("(num = %5d, std dev = %8.2lf ms, max = %8.2lf ms)",
                seq->num(), seq->sd(), seq->maximum());
}

void G1CollectorPolicy::check_other_times(int level,
                                        NumberSeq* other_times_ms,
                                        NumberSeq* calc_other_times_ms) const {
  bool should_print = false;
  LineBuffer buf(level + 2);

  double max_sum = MAX2(fabs(other_times_ms->sum()),
                        fabs(calc_other_times_ms->sum()));
  double min_sum = MIN2(fabs(other_times_ms->sum()),
                        fabs(calc_other_times_ms->sum()));
  double sum_ratio = max_sum / min_sum;
  if (sum_ratio > 1.1) {
    should_print = true;
    buf.append_and_print_cr("## CALCULATED OTHER SUM DOESN'T MATCH RECORDED ###");
  }

  double max_avg = MAX2(fabs(other_times_ms->avg()),
                        fabs(calc_other_times_ms->avg()));
  double min_avg = MIN2(fabs(other_times_ms->avg()),
                        fabs(calc_other_times_ms->avg()));
  double avg_ratio = max_avg / min_avg;
  if (avg_ratio > 1.1) {
    should_print = true;
    buf.append_and_print_cr("## CALCULATED OTHER AVG DOESN'T MATCH RECORDED ###");
  }

  if (other_times_ms->sum() < -0.01) {
    buf.append_and_print_cr("## RECORDED OTHER SUM IS NEGATIVE ###");
  }

  if (other_times_ms->avg() < -0.01) {
    buf.append_and_print_cr("## RECORDED OTHER AVG IS NEGATIVE ###");
  }

  if (calc_other_times_ms->sum() < -0.01) {
    should_print = true;
    buf.append_and_print_cr("## CALCULATED OTHER SUM IS NEGATIVE ###");
  }

  if (calc_other_times_ms->avg() < -0.01) {
    should_print = true;
    buf.append_and_print_cr("## CALCULATED OTHER AVG IS NEGATIVE ###");
  }

  if (should_print)
    print_summary(level, "Other(Calc)", calc_other_times_ms);
}

void G1CollectorPolicy::print_summary(PauseSummary* summary) const {
  bool parallel = G1CollectedHeap::use_parallel_gc_threads();
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
        print_summary(2, "Scan RS", body_summary->get_scan_rs_seq());
        print_summary(2, "Object Copy", body_summary->get_obj_copy_seq());
        print_summary(2, "Termination", body_summary->get_termination_seq());
        print_summary(2, "Other", body_summary->get_parallel_other_seq());
        {
          NumberSeq* other_parts[] = {
            body_summary->get_update_rs_seq(),
            body_summary->get_ext_root_scan_seq(),
            body_summary->get_mark_stack_scan_seq(),
            body_summary->get_scan_rs_seq(),
            body_summary->get_obj_copy_seq(),
            body_summary->get_termination_seq()
          };
          NumberSeq calc_other_times_ms(body_summary->get_parallel_seq(),
                                        6, other_parts);
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
        print_summary(1, "Scan RS", body_summary->get_scan_rs_seq());
        print_summary(1, "Object Copy", body_summary->get_obj_copy_seq());
      }
    }
    print_summary(1, "Other", summary->get_other_seq());
    {
      if (body_summary != NULL) {
        NumberSeq calc_other_times_ms;
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
            body_summary->get_scan_rs_seq(),
            body_summary->get_obj_copy_seq()
          };
          calc_other_times_ms = NumberSeq(summary->get_total_seq(),
                                                6, other_parts);
        }
        check_other_times(1,  summary->get_other_seq(), &calc_other_times_ms);
      }
    }
  } else {
    LineBuffer(1).append_and_print_cr("none");
  }
  LineBuffer(0).append_and_print_cr("");
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

void G1CollectorPolicy::update_region_num(bool young) {
  if (young) {
    ++_region_num_young;
  } else {
    ++_region_num_tenured;
  }
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

void G1CollectorPolicy::update_max_gc_locker_expansion() {
  size_t expansion_region_num = 0;
  if (GCLockerEdenExpansionPercent > 0) {
    double perc = (double) GCLockerEdenExpansionPercent / 100.0;
    double expansion_region_num_d = perc * (double) _young_list_target_length;
    // We use ceiling so that if expansion_region_num_d is > 0.0 (but
    // less than 1.0) we'll get 1.
    expansion_region_num = (size_t) ceil(expansion_region_num_d);
  } else {
    assert(expansion_region_num == 0, "sanity");
  }
  _young_list_max_length = _young_list_target_length + expansion_region_num;
  assert(_young_list_target_length <= _young_list_max_length, "post-condition");
}

// Calculates survivor space parameters.
void G1CollectorPolicy::update_survivors_policy() {
  double max_survivor_regions_d =
                 (double) _young_list_target_length / (double) SurvivorRatio;
  // We use ceiling so that if max_survivor_regions_d is > 0.0 (but
  // smaller than 1.0) we'll get 1.
  _max_survivor_regions = (size_t) ceil(max_survivor_regions_d);

  _tenuring_threshold = _survivors_age_table.compute_tenuring_threshold(
        HeapRegion::GrainWords * _max_survivor_regions);
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

bool G1CollectorPolicy::force_initial_mark_if_outside_cycle(
                                                     GCCause::Cause gc_cause) {
  bool during_cycle = _g1->concurrent_mark()->cmThread()->during_cycle();
  if (!during_cycle) {
    ergo_verbose1(ErgoConcCycles,
                  "request concurrent cycle initiation",
                  ergo_format_reason("requested by GC cause")
                  ergo_format_str("GC cause"),
                  GCCause::to_string(gc_cause));
    set_initiate_conc_mark_if_possible();
    return true;
  } else {
    ergo_verbose1(ErgoConcCycles,
                  "do not request concurrent cycle initiation",
                  ergo_format_reason("concurrent cycle already in progress")
                  ergo_format_str("GC cause"),
                  GCCause::to_string(gc_cause));
    return false;
  }
}

void
G1CollectorPolicy::decide_on_conc_mark_initiation() {
  // We are about to decide on whether this pause will be an
  // initial-mark pause.

  // First, during_initial_mark_pause() should not be already set. We
  // will set it here if we have to. However, it should be cleared by
  // the end of the pause (it's only set for the duration of an
  // initial-mark pause).
  assert(!during_initial_mark_pause(), "pre-condition");

  if (initiate_conc_mark_if_possible()) {
    // We had noticed on a previous pause that the heap occupancy has
    // gone over the initiating threshold and we should start a
    // concurrent marking cycle. So we might initiate one.

    bool during_cycle = _g1->concurrent_mark()->cmThread()->during_cycle();
    if (!during_cycle) {
      // The concurrent marking thread is not "during a cycle", i.e.,
      // it has completed the last one. So we can go ahead and
      // initiate a new cycle.

      set_during_initial_mark_pause();
      // We do not allow non-full young GCs during marking.
      if (!full_young_gcs()) {
        set_full_young_gcs(true);
        ergo_verbose0(ErgoPartiallyYoungGCs,
                      "end partially-young GCs",
                      ergo_format_reason("concurrent cycle is about to start"));
      }

      // And we can now clear initiate_conc_mark_if_possible() as
      // we've already acted on it.
      clear_initiate_conc_mark_if_possible();

      ergo_verbose0(ErgoConcCycles,
                  "initiate concurrent cycle",
                  ergo_format_reason("concurrent cycle initiation requested"));
    } else {
      // The concurrent marking thread is still finishing up the
      // previous cycle. If we start one right now the two cycles
      // overlap. In particular, the concurrent marking thread might
      // be in the process of clearing the next marking bitmap (which
      // we will use for the next cycle if we start one). Starting a
      // cycle now will be bad given that parts of the marking
      // information might get cleared by the marking thread. And we
      // cannot wait for the marking thread to finish the cycle as it
      // periodically yields while clearing the next marking bitmap
      // and, if it's in a yield point, it's waiting for us to
      // finish. So, at this point we will not start a cycle and we'll
      // let the concurrent marking thread complete the last one.
      ergo_verbose0(ErgoConcCycles,
                    "do not initiate concurrent cycle",
                    ergo_format_reason("concurrent cycle already in progress"));
    }
  }
}

void
G1CollectorPolicy_BestRegionsFirst::
record_collection_pause_start(double start_time_sec, size_t start_used) {
  G1CollectorPolicy::record_collection_pause_start(start_time_sec, start_used);
}

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
      gclog_or_tty->print_cr("     Thread %d called %d times, added %d regions to list.",
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
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    const size_t OverpartitionFactor = 4;
    const size_t MinWorkUnit = 8;
    const size_t WorkUnit =
      MAX2(_g1->n_regions() / (ParallelGCThreads * OverpartitionFactor),
           MinWorkUnit);
    _collectionSetChooser->prepareForAddMarkedHeapRegionsPar(_g1->n_regions(),
                                                             WorkUnit);
    ParKnownGarbageTask parKnownGarbageTask(_collectionSetChooser,
                                            (int) WorkUnit);
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

// Add the heap region at the head of the non-incremental collection set
void G1CollectorPolicy::
add_to_collection_set(HeapRegion* hr) {
  assert(_inc_cset_build_state == Active, "Precondition");
  assert(!hr->is_young(), "non-incremental add of young region");

  if (_g1->mark_in_progress())
    _g1->concurrent_mark()->registerCSetRegion(hr);

  assert(!hr->in_collection_set(), "should not already be in the CSet");
  hr->set_in_collection_set(true);
  hr->set_next_in_collection_set(_collection_set);
  _collection_set = hr;
  _collection_set_size++;
  _collection_set_bytes_used_before += hr->used();
  _g1->register_region_with_in_cset_fast_test(hr);
}

// Initialize the per-collection-set information
void G1CollectorPolicy::start_incremental_cset_building() {
  assert(_inc_cset_build_state == Inactive, "Precondition");

  _inc_cset_head = NULL;
  _inc_cset_tail = NULL;
  _inc_cset_size = 0;
  _inc_cset_bytes_used_before = 0;

  _inc_cset_young_index = 0;

  _inc_cset_max_finger = 0;
  _inc_cset_recorded_young_bytes = 0;
  _inc_cset_recorded_rs_lengths = 0;
  _inc_cset_predicted_elapsed_time_ms = 0;
  _inc_cset_predicted_bytes_to_copy = 0;
  _inc_cset_build_state = Active;
}

void G1CollectorPolicy::add_to_incremental_cset_info(HeapRegion* hr, size_t rs_length) {
  // This routine is used when:
  // * adding survivor regions to the incremental cset at the end of an
  //   evacuation pause,
  // * adding the current allocation region to the incremental cset
  //   when it is retired, and
  // * updating existing policy information for a region in the
  //   incremental cset via young list RSet sampling.
  // Therefore this routine may be called at a safepoint by the
  // VM thread, or in-between safepoints by mutator threads (when
  // retiring the current allocation region) or a concurrent
  // refine thread (RSet sampling).

  double region_elapsed_time_ms = predict_region_elapsed_time_ms(hr, true);
  size_t used_bytes = hr->used();

  _inc_cset_recorded_rs_lengths += rs_length;
  _inc_cset_predicted_elapsed_time_ms += region_elapsed_time_ms;

  _inc_cset_bytes_used_before += used_bytes;

  // Cache the values we have added to the aggregated informtion
  // in the heap region in case we have to remove this region from
  // the incremental collection set, or it is updated by the
  // rset sampling code
  hr->set_recorded_rs_length(rs_length);
  hr->set_predicted_elapsed_time_ms(region_elapsed_time_ms);

#if PREDICTIONS_VERBOSE
  size_t bytes_to_copy = predict_bytes_to_copy(hr);
  _inc_cset_predicted_bytes_to_copy += bytes_to_copy;

  // Record the number of bytes used in this region
  _inc_cset_recorded_young_bytes += used_bytes;

  // Cache the values we have added to the aggregated informtion
  // in the heap region in case we have to remove this region from
  // the incremental collection set, or it is updated by the
  // rset sampling code
  hr->set_predicted_bytes_to_copy(bytes_to_copy);
#endif // PREDICTIONS_VERBOSE
}

void G1CollectorPolicy::remove_from_incremental_cset_info(HeapRegion* hr) {
  // This routine is currently only called as part of the updating of
  // existing policy information for regions in the incremental cset that
  // is performed by the concurrent refine thread(s) as part of young list
  // RSet sampling. Therefore we should not be at a safepoint.

  assert(!SafepointSynchronize::is_at_safepoint(), "should not be at safepoint");
  assert(hr->is_young(), "it should be");

  size_t used_bytes = hr->used();
  size_t old_rs_length = hr->recorded_rs_length();
  double old_elapsed_time_ms = hr->predicted_elapsed_time_ms();

  // Subtract the old recorded/predicted policy information for
  // the given heap region from the collection set info.
  _inc_cset_recorded_rs_lengths -= old_rs_length;
  _inc_cset_predicted_elapsed_time_ms -= old_elapsed_time_ms;

  _inc_cset_bytes_used_before -= used_bytes;

  // Clear the values cached in the heap region
  hr->set_recorded_rs_length(0);
  hr->set_predicted_elapsed_time_ms(0);

#if PREDICTIONS_VERBOSE
  size_t old_predicted_bytes_to_copy = hr->predicted_bytes_to_copy();
  _inc_cset_predicted_bytes_to_copy -= old_predicted_bytes_to_copy;

  // Subtract the number of bytes used in this region
  _inc_cset_recorded_young_bytes -= used_bytes;

  // Clear the values cached in the heap region
  hr->set_predicted_bytes_to_copy(0);
#endif // PREDICTIONS_VERBOSE
}

void G1CollectorPolicy::update_incremental_cset_info(HeapRegion* hr, size_t new_rs_length) {
  // Update the collection set information that is dependent on the new RS length
  assert(hr->is_young(), "Precondition");

  remove_from_incremental_cset_info(hr);
  add_to_incremental_cset_info(hr, new_rs_length);
}

void G1CollectorPolicy::add_region_to_incremental_cset_common(HeapRegion* hr) {
  assert( hr->is_young(), "invariant");
  assert( hr->young_index_in_cset() == -1, "invariant" );
  assert(_inc_cset_build_state == Active, "Precondition");

  // We need to clear and set the cached recorded/cached collection set
  // information in the heap region here (before the region gets added
  // to the collection set). An individual heap region's cached values
  // are calculated, aggregated with the policy collection set info,
  // and cached in the heap region here (initially) and (subsequently)
  // by the Young List sampling code.

  size_t rs_length = hr->rem_set()->occupied();
  add_to_incremental_cset_info(hr, rs_length);

  HeapWord* hr_end = hr->end();
  _inc_cset_max_finger = MAX2(_inc_cset_max_finger, hr_end);

  assert(!hr->in_collection_set(), "invariant");
  hr->set_in_collection_set(true);
  assert( hr->next_in_collection_set() == NULL, "invariant");

  _inc_cset_size++;
  _g1->register_region_with_in_cset_fast_test(hr);

  hr->set_young_index_in_cset((int) _inc_cset_young_index);
  ++_inc_cset_young_index;
}

// Add the region at the RHS of the incremental cset
void G1CollectorPolicy::add_region_to_incremental_cset_rhs(HeapRegion* hr) {
  // We should only ever be appending survivors at the end of a pause
  assert( hr->is_survivor(), "Logic");

  // Do the 'common' stuff
  add_region_to_incremental_cset_common(hr);

  // Now add the region at the right hand side
  if (_inc_cset_tail == NULL) {
    assert(_inc_cset_head == NULL, "invariant");
    _inc_cset_head = hr;
  } else {
    _inc_cset_tail->set_next_in_collection_set(hr);
  }
  _inc_cset_tail = hr;
}

// Add the region to the LHS of the incremental cset
void G1CollectorPolicy::add_region_to_incremental_cset_lhs(HeapRegion* hr) {
  // Survivors should be added to the RHS at the end of a pause
  assert(!hr->is_survivor(), "Logic");

  // Do the 'common' stuff
  add_region_to_incremental_cset_common(hr);

  // Add the region at the left hand side
  hr->set_next_in_collection_set(_inc_cset_head);
  if (_inc_cset_head == NULL) {
    assert(_inc_cset_tail == NULL, "Invariant");
    _inc_cset_tail = hr;
  }
  _inc_cset_head = hr;
}

#ifndef PRODUCT
void G1CollectorPolicy::print_collection_set(HeapRegion* list_head, outputStream* st) {
  assert(list_head == inc_cset_head() || list_head == collection_set(), "must be");

  st->print_cr("\nCollection_set:");
  HeapRegion* csr = list_head;
  while (csr != NULL) {
    HeapRegion* next = csr->next_in_collection_set();
    assert(csr->in_collection_set(), "bad CS");
    st->print_cr("  [%08x-%08x], t: %08x, P: %08x, N: %08x, C: %08x, "
                 "age: %4d, y: %d, surv: %d",
                        csr->bottom(), csr->end(),
                        csr->top(),
                        csr->prev_top_at_mark_start(),
                        csr->next_top_at_mark_start(),
                        csr->top_at_conc_mark_count(),
                        csr->age_in_surv_rate_group_cond(),
                        csr->is_young(),
                        csr->is_survivor());
    csr = next;
  }
}
#endif // !PRODUCT

void
G1CollectorPolicy_BestRegionsFirst::choose_collection_set(
                                                  double target_pause_time_ms) {
  // Set this here - in case we're not doing young collections.
  double non_young_start_time_sec = os::elapsedTime();

  YoungList* young_list = _g1->young_list();

  start_recording_regions();

  guarantee(target_pause_time_ms > 0.0,
            err_msg("target_pause_time_ms = %1.6lf should be positive",
                    target_pause_time_ms));
  guarantee(_collection_set == NULL, "Precondition");

  double base_time_ms = predict_base_elapsed_time_ms(_pending_cards);
  double predicted_pause_time_ms = base_time_ms;

  double time_remaining_ms = target_pause_time_ms - base_time_ms;

  ergo_verbose3(ErgoCSetConstruction | ErgoHigh,
                "start choosing CSet",
                ergo_format_ms("predicted base time")
                ergo_format_ms("remaining time")
                ergo_format_ms("target pause time"),
                base_time_ms, time_remaining_ms, target_pause_time_ms);

  // the 10% and 50% values are arbitrary...
  double threshold = 0.10 * target_pause_time_ms;
  if (time_remaining_ms < threshold) {
    double prev_time_remaining_ms = time_remaining_ms;
    time_remaining_ms = 0.50 * target_pause_time_ms;
    _within_target = false;
    ergo_verbose3(ErgoCSetConstruction,
                  "adjust remaining time",
                  ergo_format_reason("remaining time lower than threshold")
                  ergo_format_ms("remaining time")
                  ergo_format_ms("threshold")
                  ergo_format_ms("adjusted remaining time"),
                  prev_time_remaining_ms, threshold, time_remaining_ms);
  } else {
    _within_target = true;
  }

  size_t expansion_bytes = _g1->expansion_regions() * HeapRegion::GrainBytes;

  HeapRegion* hr;
  double young_start_time_sec = os::elapsedTime();

  _collection_set_bytes_used_before = 0;
  _collection_set_size = 0;
  _young_cset_length  = 0;
  _last_young_gc_full = full_young_gcs() ? true : false;

  if (_last_young_gc_full) {
    ++_full_young_pause_num;
  } else {
    ++_partial_young_pause_num;
  }

  // The young list is laid with the survivor regions from the previous
  // pause are appended to the RHS of the young list, i.e.
  //   [Newly Young Regions ++ Survivors from last pause].

  size_t survivor_region_num = young_list->survivor_length();
  size_t eden_region_num = young_list->length() - survivor_region_num;
  size_t old_region_num = 0;
  hr = young_list->first_survivor_region();
  while (hr != NULL) {
    assert(hr->is_survivor(), "badly formed young list");
    hr->set_young();
    hr = hr->get_next_young_region();
  }

  // Clear the fields that point to the survivor list - they are all young now.
  young_list->clear_survivors();

  if (_g1->mark_in_progress())
    _g1->concurrent_mark()->register_collection_set_finger(_inc_cset_max_finger);

  _young_cset_length = _inc_cset_young_index;
  _collection_set = _inc_cset_head;
  _collection_set_size = _inc_cset_size;
  _collection_set_bytes_used_before = _inc_cset_bytes_used_before;
  time_remaining_ms -= _inc_cset_predicted_elapsed_time_ms;
  predicted_pause_time_ms += _inc_cset_predicted_elapsed_time_ms;

  ergo_verbose3(ErgoCSetConstruction | ErgoHigh,
                "add young regions to CSet",
                ergo_format_region("eden")
                ergo_format_region("survivors")
                ergo_format_ms("predicted young region time"),
                eden_region_num, survivor_region_num,
                _inc_cset_predicted_elapsed_time_ms);

  // The number of recorded young regions is the incremental
  // collection set's current size
  set_recorded_young_regions(_inc_cset_size);
  set_recorded_rs_lengths(_inc_cset_recorded_rs_lengths);
  set_recorded_young_bytes(_inc_cset_recorded_young_bytes);
#if PREDICTIONS_VERBOSE
  set_predicted_bytes_to_copy(_inc_cset_predicted_bytes_to_copy);
#endif // PREDICTIONS_VERBOSE

  assert(_inc_cset_size == young_list->length(), "Invariant");

  double young_end_time_sec = os::elapsedTime();
  _recorded_young_cset_choice_time_ms =
    (young_end_time_sec - young_start_time_sec) * 1000.0;

  // We are doing young collections so reset this.
  non_young_start_time_sec = young_end_time_sec;

  if (!full_young_gcs()) {
    bool should_continue = true;
    NumberSeq seq;
    double avg_prediction = 100000000000000000.0; // something very large

    size_t prev_collection_set_size = _collection_set_size;
    double prev_predicted_pause_time_ms = predicted_pause_time_ms;
    do {
      hr = _collectionSetChooser->getNextMarkedRegion(time_remaining_ms,
                                                      avg_prediction);
      if (hr != NULL) {
        double predicted_time_ms = predict_region_elapsed_time_ms(hr, false);
        time_remaining_ms -= predicted_time_ms;
        predicted_pause_time_ms += predicted_time_ms;
        add_to_collection_set(hr);
        record_non_young_cset_region(hr);
        seq.add(predicted_time_ms);
        avg_prediction = seq.avg() + seq.sd();
      }

      should_continue = true;
      if (hr == NULL) {
        // No need for an ergo verbose message here,
        // getNextMarkRegion() does this when it returns NULL.
        should_continue = false;
      } else {
        if (adaptive_young_list_length()) {
          if (time_remaining_ms < 0.0) {
            ergo_verbose1(ErgoCSetConstruction,
                          "stop adding old regions to CSet",
                          ergo_format_reason("remaining time is lower than 0")
                          ergo_format_ms("remaining time"),
                          time_remaining_ms);
            should_continue = false;
          }
        } else {
          if (_collection_set_size >= _young_list_fixed_length) {
            ergo_verbose2(ErgoCSetConstruction,
                          "stop adding old regions to CSet",
                          ergo_format_reason("CSet length reached target")
                          ergo_format_region("CSet")
                          ergo_format_region("young target"),
                          _collection_set_size, _young_list_fixed_length);
            should_continue = false;
          }
        }
      }
    } while (should_continue);

    if (!adaptive_young_list_length() &&
        _collection_set_size < _young_list_fixed_length) {
      ergo_verbose2(ErgoCSetConstruction,
                    "request partially-young GCs end",
                    ergo_format_reason("CSet length lower than target")
                    ergo_format_region("CSet")
                    ergo_format_region("young target"),
                    _collection_set_size, _young_list_fixed_length);
      _should_revert_to_full_young_gcs  = true;
    }

    old_region_num = _collection_set_size - prev_collection_set_size;

    ergo_verbose2(ErgoCSetConstruction | ErgoHigh,
                  "add old regions to CSet",
                  ergo_format_region("old")
                  ergo_format_ms("predicted old region time"),
                  old_region_num,
                  predicted_pause_time_ms - prev_predicted_pause_time_ms);
  }

  stop_incremental_cset_building();

  count_CS_bytes_used();

  end_recording_regions();

  ergo_verbose5(ErgoCSetConstruction,
                "finish choosing CSet",
                ergo_format_region("eden")
                ergo_format_region("survivors")
                ergo_format_region("old")
                ergo_format_ms("predicted pause time")
                ergo_format_ms("target pause time"),
                eden_region_num, survivor_region_num, old_region_num,
                predicted_pause_time_ms, target_pause_time_ms);

  double non_young_end_time_sec = os::elapsedTime();
  _recorded_non_young_cset_choice_time_ms =
    (non_young_end_time_sec - non_young_start_time_sec) * 1000.0;
}

void G1CollectorPolicy_BestRegionsFirst::record_full_collection_end() {
  G1CollectorPolicy::record_full_collection_end();
  _collectionSetChooser->updateAfterFullCollection();
}

void G1CollectorPolicy_BestRegionsFirst::
record_collection_pause_end() {
  G1CollectorPolicy::record_collection_pause_end();
  assert(assertMarkedBytesDataOK(), "Marked regions not OK at pause end.");
}
