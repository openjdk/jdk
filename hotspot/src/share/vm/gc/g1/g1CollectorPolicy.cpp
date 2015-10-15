/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/concurrentG1Refine.hpp"
#include "gc/g1/concurrentMark.hpp"
#include "gc/g1/concurrentMarkThread.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectorPolicy.hpp"
#include "gc/g1/g1ErgoVerbose.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1Log.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/shared/gcPolicyCounters.hpp"
#include "runtime/arguments.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/debug.hpp"

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
static double young_cards_per_entry_ratio_defaults[] = {
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

G1CollectorPolicy::G1CollectorPolicy() :
  _predictor(G1ConfidencePercent / 100.0),
  _parallel_gc_threads(ParallelGCThreads),

  _recent_gc_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),
  _stop_world_start(0.0),

  _concurrent_mark_remark_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),
  _concurrent_mark_cleanup_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),

  _alloc_rate_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _prev_collection_pause_end_ms(0.0),
  _rs_length_diff_seq(new TruncatedSeq(TruncatedSeqLength)),
  _cost_per_card_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _cost_scan_hcc_seq(new TruncatedSeq(TruncatedSeqLength)),
  _young_cards_per_entry_ratio_seq(new TruncatedSeq(TruncatedSeqLength)),
  _mixed_cards_per_entry_ratio_seq(new TruncatedSeq(TruncatedSeqLength)),
  _cost_per_entry_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _mixed_cost_per_entry_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _cost_per_byte_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _cost_per_byte_ms_during_cm_seq(new TruncatedSeq(TruncatedSeqLength)),
  _constant_other_time_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _young_other_cost_per_region_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
  _non_young_other_cost_per_region_ms_seq(
                                         new TruncatedSeq(TruncatedSeqLength)),

  _pending_cards_seq(new TruncatedSeq(TruncatedSeqLength)),
  _rs_lengths_seq(new TruncatedSeq(TruncatedSeqLength)),

  _pause_time_target_ms((double) MaxGCPauseMillis),

  _recent_prev_end_times_for_all_gcs_sec(
                                new TruncatedSeq(NumPrevPausesForHeuristics)),

  _recent_avg_pause_time_ratio(0.0),
  _rs_lengths_prediction(0),
  _max_survivor_regions(0),

  _eden_used_bytes_before_gc(0),
  _survivor_used_bytes_before_gc(0),
  _heap_used_bytes_before_gc(0),
  _metaspace_used_bytes_before_gc(0),
  _eden_capacity_bytes_before_gc(0),
  _heap_capacity_bytes_before_gc(0),

  _eden_cset_region_length(0),
  _survivor_cset_region_length(0),
  _old_cset_region_length(0),

  _collection_set(NULL),
  _collection_set_bytes_used_before(0),

  // Incremental CSet attributes
  _inc_cset_build_state(Inactive),
  _inc_cset_head(NULL),
  _inc_cset_tail(NULL),
  _inc_cset_bytes_used_before(0),
  _inc_cset_max_finger(NULL),
  _inc_cset_recorded_rs_lengths(0),
  _inc_cset_recorded_rs_lengths_diffs(0),
  _inc_cset_predicted_elapsed_time_ms(0.0),
  _inc_cset_predicted_elapsed_time_ms_diffs(0.0),

  // add here any more surv rate groups
  _recorded_survivor_regions(0),
  _recorded_survivor_head(NULL),
  _recorded_survivor_tail(NULL),
  _survivors_age_table(true),

  _gc_overhead_perc(0.0) {

  // SurvRateGroups below must be initialized after the predictor because they
  // indirectly use it through this object passed to their constructor.
  _short_lived_surv_rate_group =
    new SurvRateGroup(&_predictor, "Short Lived", G1YoungSurvRateNumRegionsSummary);
  _survivor_surv_rate_group =
    new SurvRateGroup(&_predictor, "Survivor", G1YoungSurvRateNumRegionsSummary);

  // Set up the region size and associated fields. Given that the
  // policy is created before the heap, we have to set this up here,
  // so it's done as soon as possible.

  // It would have been natural to pass initial_heap_byte_size() and
  // max_heap_byte_size() to setup_heap_region_size() but those have
  // not been set up at this point since they should be aligned with
  // the region size. So, there is a circular dependency here. We base
  // the region size on the heap size, but the heap size should be
  // aligned with the region size. To get around this we use the
  // unaligned values for the heap.
  HeapRegion::setup_heap_region_size(InitialHeapSize, MaxHeapSize);
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

  _recent_prev_end_times_for_all_gcs_sec->add(os::elapsedTime());
  _prev_collection_pause_end_ms = os::elapsedTime() * 1000.0;

  _phase_times = new G1GCPhaseTimes(_parallel_gc_threads);

  int index = MIN2(_parallel_gc_threads - 1, 7);

  _rs_length_diff_seq->add(rs_length_diff_defaults[index]);
  _cost_per_card_ms_seq->add(cost_per_card_ms_defaults[index]);
  _cost_scan_hcc_seq->add(0.0);
  _young_cards_per_entry_ratio_seq->add(
                                  young_cards_per_entry_ratio_defaults[index]);
  _cost_per_entry_ms_seq->add(cost_per_entry_ms_defaults[index]);
  _cost_per_byte_ms_seq->add(cost_per_byte_ms_defaults[index]);
  _constant_other_time_ms_seq->add(constant_other_time_ms_defaults[index]);
  _young_other_cost_per_region_ms_seq->add(
                               young_other_cost_per_region_ms_defaults[index]);
  _non_young_other_cost_per_region_ms_seq->add(
                           non_young_other_cost_per_region_ms_defaults[index]);

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

  // start conservatively (around 50ms is about right)
  _concurrent_mark_remark_times_ms->add(0.05);
  _concurrent_mark_cleanup_times_ms->add(0.20);
  _tenuring_threshold = MaxTenuringThreshold;

  assert(GCTimeRatio > 0,
         "we should have set it to a default value set_g1_gc_flags() "
         "if a user set it to 0");
  _gc_overhead_perc = 100.0 * (1.0 / (1.0 + GCTimeRatio));

  uintx reserve_perc = G1ReservePercent;
  // Put an artificial ceiling on this so that it's not set to a silly value.
  if (reserve_perc > 50) {
    reserve_perc = 50;
    warning("G1ReservePercent is set to a value that is too large, "
            "it's been updated to " UINTX_FORMAT, reserve_perc);
  }
  _reserve_factor = (double) reserve_perc / 100.0;
  // This will be set when the heap is expanded
  // for the first time during initialization.
  _reserve_regions = 0;

  _collectionSetChooser = new CollectionSetChooser();
}

double G1CollectorPolicy::get_new_prediction(TruncatedSeq const* seq) const {
  return _predictor.get_new_prediction(seq);
}

void G1CollectorPolicy::initialize_alignments() {
  _space_alignment = HeapRegion::GrainBytes;
  size_t card_table_alignment = GenRemSet::max_alignment_constraint();
  size_t page_size = UseLargePages ? os::large_page_size() : os::vm_page_size();
  _heap_alignment = MAX3(card_table_alignment, _space_alignment, page_size);
}

void G1CollectorPolicy::initialize_flags() {
  if (G1HeapRegionSize != HeapRegion::GrainBytes) {
    FLAG_SET_ERGO(size_t, G1HeapRegionSize, HeapRegion::GrainBytes);
  }

  if (SurvivorRatio < 1) {
    vm_exit_during_initialization("Invalid survivor ratio specified");
  }
  CollectorPolicy::initialize_flags();
  _young_gen_sizer = new G1YoungGenSizer(); // Must be after call to initialize_flags
}

void G1CollectorPolicy::post_heap_initialize() {
  uintx max_regions = G1CollectedHeap::heap()->max_regions();
  size_t max_young_size = (size_t)_young_gen_sizer->max_young_length(max_regions) * HeapRegion::GrainBytes;
  if (max_young_size != MaxNewSize) {
    FLAG_SET_ERGO(size_t, MaxNewSize, max_young_size);
  }
}

G1CollectorState* G1CollectorPolicy::collector_state() const { return _g1->collector_state(); }

G1YoungGenSizer::G1YoungGenSizer() : _sizer_kind(SizerDefaults), _adaptive_size(true),
        _min_desired_young_length(0), _max_desired_young_length(0) {
  if (FLAG_IS_CMDLINE(NewRatio)) {
    if (FLAG_IS_CMDLINE(NewSize) || FLAG_IS_CMDLINE(MaxNewSize)) {
      warning("-XX:NewSize and -XX:MaxNewSize override -XX:NewRatio");
    } else {
      _sizer_kind = SizerNewRatio;
      _adaptive_size = false;
      return;
    }
  }

  if (NewSize > MaxNewSize) {
    if (FLAG_IS_CMDLINE(MaxNewSize)) {
      warning("NewSize (" SIZE_FORMAT "k) is greater than the MaxNewSize (" SIZE_FORMAT "k). "
              "A new max generation size of " SIZE_FORMAT "k will be used.",
              NewSize/K, MaxNewSize/K, NewSize/K);
    }
    MaxNewSize = NewSize;
  }

  if (FLAG_IS_CMDLINE(NewSize)) {
    _min_desired_young_length = MAX2((uint) (NewSize / HeapRegion::GrainBytes),
                                     1U);
    if (FLAG_IS_CMDLINE(MaxNewSize)) {
      _max_desired_young_length =
                             MAX2((uint) (MaxNewSize / HeapRegion::GrainBytes),
                                  1U);
      _sizer_kind = SizerMaxAndNewSize;
      _adaptive_size = _min_desired_young_length == _max_desired_young_length;
    } else {
      _sizer_kind = SizerNewSizeOnly;
    }
  } else if (FLAG_IS_CMDLINE(MaxNewSize)) {
    _max_desired_young_length =
                             MAX2((uint) (MaxNewSize / HeapRegion::GrainBytes),
                                  1U);
    _sizer_kind = SizerMaxNewSizeOnly;
  }
}

uint G1YoungGenSizer::calculate_default_min_length(uint new_number_of_heap_regions) {
  uint default_value = (new_number_of_heap_regions * G1NewSizePercent) / 100;
  return MAX2(1U, default_value);
}

uint G1YoungGenSizer::calculate_default_max_length(uint new_number_of_heap_regions) {
  uint default_value = (new_number_of_heap_regions * G1MaxNewSizePercent) / 100;
  return MAX2(1U, default_value);
}

void G1YoungGenSizer::recalculate_min_max_young_length(uint number_of_heap_regions, uint* min_young_length, uint* max_young_length) {
  assert(number_of_heap_regions > 0, "Heap must be initialized");

  switch (_sizer_kind) {
    case SizerDefaults:
      *min_young_length = calculate_default_min_length(number_of_heap_regions);
      *max_young_length = calculate_default_max_length(number_of_heap_regions);
      break;
    case SizerNewSizeOnly:
      *max_young_length = calculate_default_max_length(number_of_heap_regions);
      *max_young_length = MAX2(*min_young_length, *max_young_length);
      break;
    case SizerMaxNewSizeOnly:
      *min_young_length = calculate_default_min_length(number_of_heap_regions);
      *min_young_length = MIN2(*min_young_length, *max_young_length);
      break;
    case SizerMaxAndNewSize:
      // Do nothing. Values set on the command line, don't update them at runtime.
      break;
    case SizerNewRatio:
      *min_young_length = number_of_heap_regions / (NewRatio + 1);
      *max_young_length = *min_young_length;
      break;
    default:
      ShouldNotReachHere();
  }

  assert(*min_young_length <= *max_young_length, "Invalid min/max young gen size values");
}

uint G1YoungGenSizer::max_young_length(uint number_of_heap_regions) {
  // We need to pass the desired values because recalculation may not update these
  // values in some cases.
  uint temp = _min_desired_young_length;
  uint result = _max_desired_young_length;
  recalculate_min_max_young_length(number_of_heap_regions, &temp, &result);
  return result;
}

void G1YoungGenSizer::heap_size_changed(uint new_number_of_heap_regions) {
  recalculate_min_max_young_length(new_number_of_heap_regions, &_min_desired_young_length,
          &_max_desired_young_length);
}

void G1CollectorPolicy::init() {
  // Set aside an initial future to_space.
  _g1 = G1CollectedHeap::heap();

  assert(Heap_lock->owned_by_self(), "Locking discipline.");

  initialize_gc_policy_counters();

  if (adaptive_young_list_length()) {
    _young_list_fixed_length = 0;
  } else {
    _young_list_fixed_length = _young_gen_sizer->min_desired_young_length();
  }
  _free_regions_at_end_of_collection = _g1->num_free_regions();

  update_young_list_target_length();
  // We may immediately start allocating regions and placing them on the
  // collection set list. Initialize the per-collection set info
  start_incremental_cset_building();
}

// Create the jstat counters for the policy.
void G1CollectorPolicy::initialize_gc_policy_counters() {
  _gc_policy_counters = new GCPolicyCounters("GarbageFirst", 1, 3);
}

bool G1CollectorPolicy::predict_will_fit(uint young_length,
                                         double base_time_ms,
                                         uint base_free_regions,
                                         double target_pause_time_ms) const {
  if (young_length >= base_free_regions) {
    // end condition 1: not enough space for the young regions
    return false;
  }

  double accum_surv_rate = accum_yg_surv_rate_pred((int) young_length - 1);
  size_t bytes_to_copy =
               (size_t) (accum_surv_rate * (double) HeapRegion::GrainBytes);
  double copy_time_ms = predict_object_copy_time_ms(bytes_to_copy);
  double young_other_time_ms = predict_young_other_time_ms(young_length);
  double pause_time_ms = base_time_ms + copy_time_ms + young_other_time_ms;
  if (pause_time_ms > target_pause_time_ms) {
    // end condition 2: prediction is over the target pause time
    return false;
  }

  size_t free_bytes = (base_free_regions - young_length) * HeapRegion::GrainBytes;
  if ((2.0 /* magic */ * _predictor.sigma()) * bytes_to_copy > free_bytes) {
    // end condition 3: out-of-space (conservatively!)
    return false;
  }

  // success!
  return true;
}

void G1CollectorPolicy::record_new_heap_size(uint new_number_of_regions) {
  // re-calculate the necessary reserve
  double reserve_regions_d = (double) new_number_of_regions * _reserve_factor;
  // We use ceiling so that if reserve_regions_d is > 0.0 (but
  // smaller than 1.0) we'll get 1.
  _reserve_regions = (uint) ceil(reserve_regions_d);

  _young_gen_sizer->heap_size_changed(new_number_of_regions);
}

uint G1CollectorPolicy::calculate_young_list_desired_min_length(
                                                       uint base_min_length) const {
  uint desired_min_length = 0;
  if (adaptive_young_list_length()) {
    if (_alloc_rate_ms_seq->num() > 3) {
      double now_sec = os::elapsedTime();
      double when_ms = _mmu_tracker->when_max_gc_sec(now_sec) * 1000.0;
      double alloc_rate_ms = predict_alloc_rate_ms();
      desired_min_length = (uint) ceil(alloc_rate_ms * when_ms);
    } else {
      // otherwise we don't have enough info to make the prediction
    }
  }
  desired_min_length += base_min_length;
  // make sure we don't go below any user-defined minimum bound
  return MAX2(_young_gen_sizer->min_desired_young_length(), desired_min_length);
}

uint G1CollectorPolicy::calculate_young_list_desired_max_length() const {
  // Here, we might want to also take into account any additional
  // constraints (i.e., user-defined minimum bound). Currently, we
  // effectively don't set this bound.
  return _young_gen_sizer->max_desired_young_length();
}

void G1CollectorPolicy::update_young_list_target_length(size_t rs_lengths) {
  if (rs_lengths == (size_t) -1) {
    // if it's set to the default value (-1), we should predict it;
    // otherwise, use the given value.
    rs_lengths = (size_t) get_new_prediction(_rs_lengths_seq);
  }

  // Calculate the absolute and desired min bounds.

  // This is how many young regions we already have (currently: the survivors).
  uint base_min_length = recorded_survivor_regions();
  uint desired_min_length = calculate_young_list_desired_min_length(base_min_length);
  // This is the absolute minimum young length. Ensure that we
  // will at least have one eden region available for allocation.
  uint absolute_min_length = base_min_length + MAX2(_g1->young_list()->eden_length(), (uint)1);
  // If we shrank the young list target it should not shrink below the current size.
  desired_min_length = MAX2(desired_min_length, absolute_min_length);
  // Calculate the absolute and desired max bounds.

  // We will try our best not to "eat" into the reserve.
  uint absolute_max_length = 0;
  if (_free_regions_at_end_of_collection > _reserve_regions) {
    absolute_max_length = _free_regions_at_end_of_collection - _reserve_regions;
  }
  uint desired_max_length = calculate_young_list_desired_max_length();
  if (desired_max_length > absolute_max_length) {
    desired_max_length = absolute_max_length;
  }

  uint young_list_target_length = 0;
  if (adaptive_young_list_length()) {
    if (collector_state()->gcs_are_young()) {
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
    // The user asked for a fixed young gen so we'll fix the young gen
    // whether the next GC is young or mixed.
    young_list_target_length = _young_list_fixed_length;
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

uint
G1CollectorPolicy::calculate_young_list_target_length(size_t rs_lengths,
                                                     uint base_min_length,
                                                     uint desired_min_length,
                                                     uint desired_max_length) const {
  assert(adaptive_young_list_length(), "pre-condition");
  assert(collector_state()->gcs_are_young(), "only call this for young GCs");

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
  uint min_young_length = desired_min_length - base_min_length;
  assert(desired_max_length > base_min_length, "invariant");
  uint max_young_length = desired_max_length - base_min_length;

  double target_pause_time_ms = _mmu_tracker->max_gc_time() * 1000.0;
  double survivor_regions_evac_time = predict_survivor_regions_evac_time();
  size_t pending_cards = (size_t) get_new_prediction(_pending_cards_seq);
  size_t adj_rs_lengths = rs_lengths + predict_rs_length_diff();
  size_t scanned_cards = predict_young_card_num(adj_rs_lengths);
  double base_time_ms =
    predict_base_elapsed_time_ms(pending_cards, scanned_cards) +
    survivor_regions_evac_time;
  uint available_free_regions = _free_regions_at_end_of_collection;
  uint base_free_regions = 0;
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
      uint diff = (max_young_length - min_young_length) / 2;
      while (diff > 0) {
        uint young_length = min_young_length + diff;
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

double G1CollectorPolicy::predict_survivor_regions_evac_time() const {
  double survivor_regions_evac_time = 0.0;
  for (HeapRegion * r = _recorded_survivor_head;
       r != NULL && r != _recorded_survivor_tail->get_next_young_region();
       r = r->get_next_young_region()) {
    survivor_regions_evac_time += predict_region_elapsed_time_ms(r, collector_state()->gcs_are_young());
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
  _full_collection_start_sec = os::elapsedTime();
  record_heap_size_info_at_start(true /* full */);
  // Release the future to-space so that it is available for compaction into.
  collector_state()->set_full_collection(true);
}

void G1CollectorPolicy::record_full_collection_end() {
  // Consider this like a collection pause for the purposes of allocation
  // since last pause.
  double end_sec = os::elapsedTime();
  double full_gc_time_sec = end_sec - _full_collection_start_sec;
  double full_gc_time_ms = full_gc_time_sec * 1000.0;

  _trace_old_gen_time_data.record_full_collection(full_gc_time_ms);

  update_recent_gc_times(end_sec, full_gc_time_ms);

  collector_state()->set_full_collection(false);

  // "Nuke" the heuristics that control the young/mixed GC
  // transitions and make sure we start with young GCs after the Full GC.
  collector_state()->set_gcs_are_young(true);
  collector_state()->set_last_young_gc(false);
  collector_state()->set_initiate_conc_mark_if_possible(false);
  collector_state()->set_during_initial_mark_pause(false);
  collector_state()->set_in_marking_window(false);
  collector_state()->set_in_marking_window_im(false);

  _short_lived_surv_rate_group->start_adding_regions();
  // also call this on any additional surv rate groups

  record_survivor_regions(0, NULL, NULL);

  _free_regions_at_end_of_collection = _g1->num_free_regions();
  // Reset survivors SurvRateGroup.
  _survivor_surv_rate_group->reset();
  update_young_list_target_length();
  _collectionSetChooser->clear();
}

void G1CollectorPolicy::record_stop_world_start() {
  _stop_world_start = os::elapsedTime();
}

void G1CollectorPolicy::record_collection_pause_start(double start_time_sec) {
  // We only need to do this here as the policy will only be applied
  // to the GC we're about to start. so, no point is calculating this
  // every time we calculate / recalculate the target young length.
  update_survivors_policy();

  assert(_g1->used() == _g1->recalculate_used(),
         "sanity, used: " SIZE_FORMAT " recalculate_used: " SIZE_FORMAT,
         _g1->used(), _g1->recalculate_used());

  double s_w_t_ms = (start_time_sec - _stop_world_start) * 1000.0;
  _trace_young_gen_time_data.record_start_collection(s_w_t_ms);
  _stop_world_start = 0.0;

  record_heap_size_info_at_start(false /* full */);

  phase_times()->record_cur_collection_start_sec(start_time_sec);
  _pending_cards = _g1->pending_card_num();

  _collection_set_bytes_used_before = 0;
  _bytes_copied_during_gc = 0;

  collector_state()->set_last_gc_was_young(false);

  // do that for any other surv rate groups
  _short_lived_surv_rate_group->stop_adding_regions();
  _survivors_age_table.clear();

  assert( verify_young_ages(), "region age verification" );
}

void G1CollectorPolicy::record_concurrent_mark_init_end(double
                                                   mark_init_elapsed_time_ms) {
  collector_state()->set_during_marking(true);
  assert(!collector_state()->initiate_conc_mark_if_possible(), "we should have cleared it by now");
  collector_state()->set_during_initial_mark_pause(false);
  _cur_mark_stop_world_time_ms = mark_init_elapsed_time_ms;
}

void G1CollectorPolicy::record_concurrent_mark_remark_start() {
  _mark_remark_start_sec = os::elapsedTime();
  collector_state()->set_during_marking(false);
}

void G1CollectorPolicy::record_concurrent_mark_remark_end() {
  double end_time_sec = os::elapsedTime();
  double elapsed_time_ms = (end_time_sec - _mark_remark_start_sec)*1000.0;
  _concurrent_mark_remark_times_ms->add(elapsed_time_ms);
  _cur_mark_stop_world_time_ms += elapsed_time_ms;
  _prev_collection_pause_end_ms += elapsed_time_ms;

  _mmu_tracker->add_pause(_mark_remark_start_sec, end_time_sec);
}

void G1CollectorPolicy::record_concurrent_mark_cleanup_start() {
  _mark_cleanup_start_sec = os::elapsedTime();
}

void G1CollectorPolicy::record_concurrent_mark_cleanup_completed() {
  collector_state()->set_last_young_gc(true);
  collector_state()->set_in_marking_window(false);
}

void G1CollectorPolicy::record_concurrent_pause() {
  if (_stop_world_start > 0.0) {
    double yield_ms = (os::elapsedTime() - _stop_world_start) * 1000.0;
    _trace_young_gen_time_data.record_yield_time(yield_ms);
  }
}

bool G1CollectorPolicy::need_to_start_conc_mark(const char* source, size_t alloc_word_size) {
  if (_g1->concurrent_mark()->cmThread()->during_cycle()) {
    return false;
  }

  size_t marking_initiating_used_threshold =
    (_g1->capacity() / 100) * InitiatingHeapOccupancyPercent;
  size_t cur_used_bytes = _g1->non_young_capacity_bytes();
  size_t alloc_byte_size = alloc_word_size * HeapWordSize;

  if ((cur_used_bytes + alloc_byte_size) > marking_initiating_used_threshold) {
    if (collector_state()->gcs_are_young() && !collector_state()->last_young_gc()) {
      ergo_verbose5(ErgoConcCycles,
        "request concurrent cycle initiation",
        ergo_format_reason("occupancy higher than threshold")
        ergo_format_byte("occupancy")
        ergo_format_byte("allocation request")
        ergo_format_byte_perc("threshold")
        ergo_format_str("source"),
        cur_used_bytes,
        alloc_byte_size,
        marking_initiating_used_threshold,
        (double) InitiatingHeapOccupancyPercent,
        source);
      return true;
    } else {
      ergo_verbose5(ErgoConcCycles,
        "do not request concurrent cycle initiation",
        ergo_format_reason("still doing mixed collections")
        ergo_format_byte("occupancy")
        ergo_format_byte("allocation request")
        ergo_format_byte_perc("threshold")
        ergo_format_str("source"),
        cur_used_bytes,
        alloc_byte_size,
        marking_initiating_used_threshold,
        (double) InitiatingHeapOccupancyPercent,
        source);
    }
  }

  return false;
}

// Anything below that is considered to be zero
#define MIN_TIMER_GRANULARITY 0.0000001

void G1CollectorPolicy::record_collection_pause_end(double pause_time_ms, size_t cards_scanned) {
  double end_time_sec = os::elapsedTime();
  assert(_cur_collection_pause_used_regions_at_start >= cset_region_length(),
         "otherwise, the subtraction below does not make sense");
  size_t rs_size =
            _cur_collection_pause_used_regions_at_start - cset_region_length();
  size_t cur_used_bytes = _g1->used();
  assert(cur_used_bytes == _g1->recalculate_used(), "It should!");
  bool last_pause_included_initial_mark = false;
  bool update_stats = !_g1->evacuation_failed();

#ifndef PRODUCT
  if (G1YoungSurvRateVerbose) {
    gclog_or_tty->cr();
    _short_lived_surv_rate_group->print();
    // do that for any other surv rate groups too
  }
#endif // PRODUCT

  last_pause_included_initial_mark = collector_state()->during_initial_mark_pause();
  if (last_pause_included_initial_mark) {
    record_concurrent_mark_init_end(0.0);
  } else if (need_to_start_conc_mark("end of GC")) {
    // Note: this might have already been set, if during the last
    // pause we decided to start a cycle but at the beginning of
    // this pause we decided to postpone it. That's OK.
    collector_state()->set_initiate_conc_mark_if_possible(true);
  }

  _mmu_tracker->add_pause(end_time_sec - pause_time_ms/1000.0, end_time_sec);

  if (update_stats) {
    _trace_young_gen_time_data.record_end_collection(pause_time_ms, phase_times());
    // this is where we update the allocation rate of the application
    double app_time_ms =
      (phase_times()->cur_collection_start_sec() * 1000.0 - _prev_collection_pause_end_ms);
    if (app_time_ms < MIN_TIMER_GRANULARITY) {
      // This usually happens due to the timer not having the required
      // granularity. Some Linuxes are the usual culprits.
      // We'll just set it to something (arbitrarily) small.
      app_time_ms = 1.0;
    }
    // We maintain the invariant that all objects allocated by mutator
    // threads will be allocated out of eden regions. So, we can use
    // the eden region number allocated since the previous GC to
    // calculate the application's allocate rate. The only exception
    // to that is humongous objects that are allocated separately. But
    // given that humongous object allocations do not really affect
    // either the pause's duration nor when the next pause will take
    // place we can safely ignore them here.
    uint regions_allocated = eden_cset_region_length();
    double alloc_rate_ms = (double) regions_allocated / app_time_ms;
    _alloc_rate_ms_seq->add(alloc_rate_ms);

    double interval_ms =
      (end_time_sec - _recent_prev_end_times_for_all_gcs_sec->oldest()) * 1000.0;
    update_recent_gc_times(end_time_sec, pause_time_ms);
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

  bool new_in_marking_window = collector_state()->in_marking_window();
  bool new_in_marking_window_im = false;
  if (last_pause_included_initial_mark) {
    new_in_marking_window = true;
    new_in_marking_window_im = true;
  }

  if (collector_state()->last_young_gc()) {
    // This is supposed to to be the "last young GC" before we start
    // doing mixed GCs. Here we decide whether to start mixed GCs or not.

    if (!last_pause_included_initial_mark) {
      if (next_gc_should_be_mixed("start mixed GCs",
                                  "do not start mixed GCs")) {
        collector_state()->set_gcs_are_young(false);
      }
    } else {
      ergo_verbose0(ErgoMixedGCs,
                    "do not start mixed GCs",
                    ergo_format_reason("concurrent cycle is about to start"));
    }
    collector_state()->set_last_young_gc(false);
  }

  if (!collector_state()->last_gc_was_young()) {
    // This is a mixed GC. Here we decide whether to continue doing
    // mixed GCs or not.

    if (!next_gc_should_be_mixed("continue mixed GCs",
                                 "do not continue mixed GCs")) {
      collector_state()->set_gcs_are_young(true);
    }
  }

  _short_lived_surv_rate_group->start_adding_regions();
  // Do that for any other surv rate groups

  if (update_stats) {
    double cost_per_card_ms = 0.0;
    double cost_scan_hcc = phase_times()->average_time_ms(G1GCPhaseTimes::ScanHCC);
    if (_pending_cards > 0) {
      cost_per_card_ms = (phase_times()->average_time_ms(G1GCPhaseTimes::UpdateRS) - cost_scan_hcc) / (double) _pending_cards;
      _cost_per_card_ms_seq->add(cost_per_card_ms);
    }
    _cost_scan_hcc_seq->add(cost_scan_hcc);

    double cost_per_entry_ms = 0.0;
    if (cards_scanned > 10) {
      cost_per_entry_ms = phase_times()->average_time_ms(G1GCPhaseTimes::ScanRS) / (double) cards_scanned;
      if (collector_state()->last_gc_was_young()) {
        _cost_per_entry_ms_seq->add(cost_per_entry_ms);
      } else {
        _mixed_cost_per_entry_ms_seq->add(cost_per_entry_ms);
      }
    }

    if (_max_rs_lengths > 0) {
      double cards_per_entry_ratio =
        (double) cards_scanned / (double) _max_rs_lengths;
      if (collector_state()->last_gc_was_young()) {
        _young_cards_per_entry_ratio_seq->add(cards_per_entry_ratio);
      } else {
        _mixed_cards_per_entry_ratio_seq->add(cards_per_entry_ratio);
      }
    }

    // This is defensive. For a while _max_rs_lengths could get
    // smaller than _recorded_rs_lengths which was causing
    // rs_length_diff to get very large and mess up the RSet length
    // predictions. The reason was unsafe concurrent updates to the
    // _inc_cset_recorded_rs_lengths field which the code below guards
    // against (see CR 7118202). This bug has now been fixed (see CR
    // 7119027). However, I'm still worried that
    // _inc_cset_recorded_rs_lengths might still end up somewhat
    // inaccurate. The concurrent refinement thread calculates an
    // RSet's length concurrently with other CR threads updating it
    // which might cause it to calculate the length incorrectly (if,
    // say, it's in mid-coarsening). So I'll leave in the defensive
    // conditional below just in case.
    size_t rs_length_diff = 0;
    if (_max_rs_lengths > _recorded_rs_lengths) {
      rs_length_diff = _max_rs_lengths - _recorded_rs_lengths;
    }
    _rs_length_diff_seq->add((double) rs_length_diff);

    size_t freed_bytes = _heap_used_bytes_before_gc - cur_used_bytes;
    size_t copied_bytes = _collection_set_bytes_used_before - freed_bytes;
    double cost_per_byte_ms = 0.0;

    if (copied_bytes > 0) {
      cost_per_byte_ms = phase_times()->average_time_ms(G1GCPhaseTimes::ObjCopy) / (double) copied_bytes;
      if (collector_state()->in_marking_window()) {
        _cost_per_byte_ms_during_cm_seq->add(cost_per_byte_ms);
      } else {
        _cost_per_byte_ms_seq->add(cost_per_byte_ms);
      }
    }

    double all_other_time_ms = pause_time_ms -
      (phase_times()->average_time_ms(G1GCPhaseTimes::UpdateRS) + phase_times()->average_time_ms(G1GCPhaseTimes::ScanRS) +
          phase_times()->average_time_ms(G1GCPhaseTimes::ObjCopy) + phase_times()->average_time_ms(G1GCPhaseTimes::Termination));

    double young_other_time_ms = 0.0;
    if (young_cset_region_length() > 0) {
      young_other_time_ms =
        phase_times()->young_cset_choice_time_ms() +
        phase_times()->young_free_cset_time_ms();
      _young_other_cost_per_region_ms_seq->add(young_other_time_ms /
                                          (double) young_cset_region_length());
    }
    double non_young_other_time_ms = 0.0;
    if (old_cset_region_length() > 0) {
      non_young_other_time_ms =
        phase_times()->non_young_cset_choice_time_ms() +
        phase_times()->non_young_free_cset_time_ms();

      _non_young_other_cost_per_region_ms_seq->add(non_young_other_time_ms /
                                            (double) old_cset_region_length());
    }

    double constant_other_time_ms = all_other_time_ms -
      (young_other_time_ms + non_young_other_time_ms);
    _constant_other_time_ms_seq->add(constant_other_time_ms);

    double survival_ratio = 0.0;
    if (_collection_set_bytes_used_before > 0) {
      survival_ratio = (double) _bytes_copied_during_gc /
                                   (double) _collection_set_bytes_used_before;
    }

    _pending_cards_seq->add((double) _pending_cards);
    _rs_lengths_seq->add((double) _max_rs_lengths);
  }

  collector_state()->set_in_marking_window(new_in_marking_window);
  collector_state()->set_in_marking_window_im(new_in_marking_window_im);
  _free_regions_at_end_of_collection = _g1->num_free_regions();
  update_young_list_target_length();

  // Note that _mmu_tracker->max_gc_time() returns the time in seconds.
  double update_rs_time_goal_ms = _mmu_tracker->max_gc_time() * MILLIUNITS * G1RSetUpdatingPauseTimePercent / 100.0;

  double scan_hcc_time_ms = phase_times()->average_time_ms(G1GCPhaseTimes::ScanHCC);

  if (update_rs_time_goal_ms < scan_hcc_time_ms) {
    ergo_verbose2(ErgoTiming,
                  "adjust concurrent refinement thresholds",
                  ergo_format_reason("Scanning the HCC expected to take longer than Update RS time goal")
                  ergo_format_ms("Update RS time goal")
                  ergo_format_ms("Scan HCC time"),
                  update_rs_time_goal_ms,
                  scan_hcc_time_ms);

    update_rs_time_goal_ms = 0;
  } else {
    update_rs_time_goal_ms -= scan_hcc_time_ms;
  }
  adjust_concurrent_refinement(phase_times()->average_time_ms(G1GCPhaseTimes::UpdateRS) - scan_hcc_time_ms,
                               phase_times()->sum_thread_work_items(G1GCPhaseTimes::UpdateRS),
                               update_rs_time_goal_ms);

  _collectionSetChooser->verify();
}

#define EXT_SIZE_FORMAT "%.1f%s"
#define EXT_SIZE_PARAMS(bytes)                                  \
  byte_size_in_proper_unit((double)(bytes)),                    \
  proper_unit_for_byte_size((bytes))

void G1CollectorPolicy::record_heap_size_info_at_start(bool full) {
  YoungList* young_list = _g1->young_list();
  _eden_used_bytes_before_gc = young_list->eden_used_bytes();
  _survivor_used_bytes_before_gc = young_list->survivor_used_bytes();
  _heap_capacity_bytes_before_gc = _g1->capacity();
  _heap_used_bytes_before_gc = _g1->used();
  _cur_collection_pause_used_regions_at_start = _g1->num_used_regions();

  _eden_capacity_bytes_before_gc =
         (_young_list_target_length * HeapRegion::GrainBytes) - _survivor_used_bytes_before_gc;

  if (full) {
    _metaspace_used_bytes_before_gc = MetaspaceAux::used_bytes();
  }
}

void G1CollectorPolicy::print_heap_transition(size_t bytes_before) const {
  size_t bytes_after = _g1->used();
  size_t capacity = _g1->capacity();

  gclog_or_tty->print(" " SIZE_FORMAT "%s->" SIZE_FORMAT "%s(" SIZE_FORMAT "%s)",
      byte_size_in_proper_unit(bytes_before),
      proper_unit_for_byte_size(bytes_before),
      byte_size_in_proper_unit(bytes_after),
      proper_unit_for_byte_size(bytes_after),
      byte_size_in_proper_unit(capacity),
      proper_unit_for_byte_size(capacity));
}

void G1CollectorPolicy::print_heap_transition() const {
  print_heap_transition(_heap_used_bytes_before_gc);
}

void G1CollectorPolicy::print_detailed_heap_transition(bool full) const {
  YoungList* young_list = _g1->young_list();

  size_t eden_used_bytes_after_gc = young_list->eden_used_bytes();
  size_t survivor_used_bytes_after_gc = young_list->survivor_used_bytes();
  size_t heap_used_bytes_after_gc = _g1->used();

  size_t heap_capacity_bytes_after_gc = _g1->capacity();
  size_t eden_capacity_bytes_after_gc =
    (_young_list_target_length * HeapRegion::GrainBytes) - survivor_used_bytes_after_gc;

  gclog_or_tty->print(
    "   [Eden: " EXT_SIZE_FORMAT "(" EXT_SIZE_FORMAT ")->" EXT_SIZE_FORMAT "(" EXT_SIZE_FORMAT ") "
    "Survivors: " EXT_SIZE_FORMAT "->" EXT_SIZE_FORMAT " "
    "Heap: " EXT_SIZE_FORMAT "(" EXT_SIZE_FORMAT ")->"
    EXT_SIZE_FORMAT "(" EXT_SIZE_FORMAT ")]",
    EXT_SIZE_PARAMS(_eden_used_bytes_before_gc),
    EXT_SIZE_PARAMS(_eden_capacity_bytes_before_gc),
    EXT_SIZE_PARAMS(eden_used_bytes_after_gc),
    EXT_SIZE_PARAMS(eden_capacity_bytes_after_gc),
    EXT_SIZE_PARAMS(_survivor_used_bytes_before_gc),
    EXT_SIZE_PARAMS(survivor_used_bytes_after_gc),
    EXT_SIZE_PARAMS(_heap_used_bytes_before_gc),
    EXT_SIZE_PARAMS(_heap_capacity_bytes_before_gc),
    EXT_SIZE_PARAMS(heap_used_bytes_after_gc),
    EXT_SIZE_PARAMS(heap_capacity_bytes_after_gc));

  if (full) {
    MetaspaceAux::print_metaspace_change(_metaspace_used_bytes_before_gc);
  }

  gclog_or_tty->cr();
}

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

    int processing_threshold_delta = MAX2((int)(cg1r->green_zone() * _predictor.sigma()), 1);
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

size_t G1CollectorPolicy::predict_rs_length_diff() const {
  return (size_t) get_new_prediction(_rs_length_diff_seq);
}

double G1CollectorPolicy::predict_alloc_rate_ms() const {
  return get_new_prediction(_alloc_rate_ms_seq);
}

double G1CollectorPolicy::predict_cost_per_card_ms() const {
  return get_new_prediction(_cost_per_card_ms_seq);
}

double G1CollectorPolicy::predict_scan_hcc_ms() const {
  return get_new_prediction(_cost_scan_hcc_seq);
}

double G1CollectorPolicy::predict_rs_update_time_ms(size_t pending_cards) const {
  return pending_cards * predict_cost_per_card_ms() + predict_scan_hcc_ms();
}

double G1CollectorPolicy::predict_young_cards_per_entry_ratio() const {
  return get_new_prediction(_young_cards_per_entry_ratio_seq);
}

double G1CollectorPolicy::predict_mixed_cards_per_entry_ratio() const {
  if (_mixed_cards_per_entry_ratio_seq->num() < 2) {
    return predict_young_cards_per_entry_ratio();
  } else {
    return get_new_prediction(_mixed_cards_per_entry_ratio_seq);
  }
}

size_t G1CollectorPolicy::predict_young_card_num(size_t rs_length) const {
  return (size_t) (rs_length * predict_young_cards_per_entry_ratio());
}

size_t G1CollectorPolicy::predict_non_young_card_num(size_t rs_length) const {
  return (size_t)(rs_length * predict_mixed_cards_per_entry_ratio());
}

double G1CollectorPolicy::predict_rs_scan_time_ms(size_t card_num) const {
  if (collector_state()->gcs_are_young()) {
    return card_num * get_new_prediction(_cost_per_entry_ms_seq);
  } else {
    return predict_mixed_rs_scan_time_ms(card_num);
  }
}

double G1CollectorPolicy::predict_mixed_rs_scan_time_ms(size_t card_num) const {
  if (_mixed_cost_per_entry_ms_seq->num() < 3) {
    return card_num * get_new_prediction(_cost_per_entry_ms_seq);
  } else {
    return card_num * get_new_prediction(_mixed_cost_per_entry_ms_seq);
  }
}

double G1CollectorPolicy::predict_object_copy_time_ms_during_cm(size_t bytes_to_copy) const {
  if (_cost_per_byte_ms_during_cm_seq->num() < 3) {
    return (1.1 * bytes_to_copy) * get_new_prediction(_cost_per_byte_ms_seq);
  } else {
    return bytes_to_copy * get_new_prediction(_cost_per_byte_ms_during_cm_seq);
  }
}

double G1CollectorPolicy::predict_object_copy_time_ms(size_t bytes_to_copy) const {
  if (collector_state()->during_concurrent_mark()) {
    return predict_object_copy_time_ms_during_cm(bytes_to_copy);
  } else {
    return bytes_to_copy * get_new_prediction(_cost_per_byte_ms_seq);
  }
}

double G1CollectorPolicy::predict_constant_other_time_ms() const {
  return get_new_prediction(_constant_other_time_ms_seq);
}

double G1CollectorPolicy::predict_young_other_time_ms(size_t young_num) const {
  return young_num * get_new_prediction(_young_other_cost_per_region_ms_seq);
}

double G1CollectorPolicy::predict_non_young_other_time_ms(size_t non_young_num) const {
  return non_young_num * get_new_prediction(_non_young_other_cost_per_region_ms_seq);
}

double G1CollectorPolicy::predict_remark_time_ms() const {
  return get_new_prediction(_concurrent_mark_remark_times_ms);
}

double G1CollectorPolicy::predict_cleanup_time_ms() const {
  return get_new_prediction(_concurrent_mark_cleanup_times_ms);
}

double G1CollectorPolicy::predict_yg_surv_rate(int age, SurvRateGroup* surv_rate_group) const {
  TruncatedSeq* seq = surv_rate_group->get_seq(age);
  guarantee(seq->num() > 0, "There should be some young gen survivor samples available. Tried to access with age %d", age);
  double pred = get_new_prediction(seq);
  if (pred > 1.0) {
    pred = 1.0;
  }
  return pred;
}

double G1CollectorPolicy::predict_yg_surv_rate(int age) const {
  return predict_yg_surv_rate(age, _short_lived_surv_rate_group);
}

double G1CollectorPolicy::accum_yg_surv_rate_pred(int age) const {
  return _short_lived_surv_rate_group->accum_surv_rate_pred(age);
}

double G1CollectorPolicy::predict_base_elapsed_time_ms(size_t pending_cards,
                                                       size_t scanned_cards) const {
  return
    predict_rs_update_time_ms(pending_cards) +
    predict_rs_scan_time_ms(scanned_cards) +
    predict_constant_other_time_ms();
}

double G1CollectorPolicy::predict_base_elapsed_time_ms(size_t pending_cards) const {
  size_t rs_length = predict_rs_length_diff();
  size_t card_num;
  if (collector_state()->gcs_are_young()) {
    card_num = predict_young_card_num(rs_length);
  } else {
    card_num = predict_non_young_card_num(rs_length);
  }
  return predict_base_elapsed_time_ms(pending_cards, card_num);
}

size_t G1CollectorPolicy::predict_bytes_to_copy(HeapRegion* hr) const {
  size_t bytes_to_copy;
  if (hr->is_marked())
    bytes_to_copy = hr->max_live_bytes();
  else {
    assert(hr->is_young() && hr->age_in_surv_rate_group() != -1, "invariant");
    int age = hr->age_in_surv_rate_group();
    double yg_surv_rate = predict_yg_surv_rate(age, hr->surv_rate_group());
    bytes_to_copy = (size_t) (hr->used() * yg_surv_rate);
  }
  return bytes_to_copy;
}

double G1CollectorPolicy::predict_region_elapsed_time_ms(HeapRegion* hr,
                                                         bool for_young_gc) const {
  size_t rs_length = hr->rem_set()->occupied();
  size_t card_num;

  // Predicting the number of cards is based on which type of GC
  // we're predicting for.
  if (for_young_gc) {
    card_num = predict_young_card_num(rs_length);
  } else {
    card_num = predict_non_young_card_num(rs_length);
  }
  size_t bytes_to_copy = predict_bytes_to_copy(hr);

  double region_elapsed_time_ms =
    predict_rs_scan_time_ms(card_num) +
    predict_object_copy_time_ms(bytes_to_copy);

  // The prediction of the "other" time for this region is based
  // upon the region type and NOT the GC type.
  if (hr->is_young()) {
    region_elapsed_time_ms += predict_young_other_time_ms(1);
  } else {
    region_elapsed_time_ms += predict_non_young_other_time_ms(1);
  }
  return region_elapsed_time_ms;
}

void G1CollectorPolicy::init_cset_region_lengths(uint eden_cset_region_length,
                                                 uint survivor_cset_region_length) {
  _eden_cset_region_length     = eden_cset_region_length;
  _survivor_cset_region_length = survivor_cset_region_length;
  _old_cset_region_length      = 0;
}

void G1CollectorPolicy::set_recorded_rs_lengths(size_t rs_lengths) {
  _recorded_rs_lengths = rs_lengths;
}

void G1CollectorPolicy::update_recent_gc_times(double end_time_sec,
                                               double elapsed_ms) {
  _recent_gc_times_ms->add(elapsed_ms);
  _recent_prev_end_times_for_all_gcs_sec->add(end_time_sec);
  _prev_collection_pause_end_ms = end_time_sec * 1000.0;
}

size_t G1CollectorPolicy::expansion_amount() const {
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

void G1CollectorPolicy::print_tracing_info() const {
  _trace_young_gen_time_data.print();
  _trace_old_gen_time_data.print();
}

void G1CollectorPolicy::print_yg_surv_rate_info() const {
#ifndef PRODUCT
  _short_lived_surv_rate_group->print_surv_rate_summary();
  // add this call for any other surv rate groups
#endif // PRODUCT
}

bool G1CollectorPolicy::is_young_list_full() const {
  uint young_list_length = _g1->young_list()->length();
  uint young_list_target_length = _young_list_target_length;
  return young_list_length >= young_list_target_length;
}

bool G1CollectorPolicy::can_expand_young_list() const {
  uint young_list_length = _g1->young_list()->length();
  uint young_list_max_length = _young_list_max_length;
  return young_list_length < young_list_max_length;
}

void G1CollectorPolicy::update_max_gc_locker_expansion() {
  uint expansion_region_num = 0;
  if (GCLockerEdenExpansionPercent > 0) {
    double perc = (double) GCLockerEdenExpansionPercent / 100.0;
    double expansion_region_num_d = perc * (double) _young_list_target_length;
    // We use ceiling so that if expansion_region_num_d is > 0.0 (but
    // less than 1.0) we'll get 1.
    expansion_region_num = (uint) ceil(expansion_region_num_d);
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
  _max_survivor_regions = (uint) ceil(max_survivor_regions_d);

  _tenuring_threshold = _survivors_age_table.compute_tenuring_threshold(
        HeapRegion::GrainWords * _max_survivor_regions, counters());
}

bool G1CollectorPolicy::force_initial_mark_if_outside_cycle(
                                                     GCCause::Cause gc_cause) {
  bool during_cycle = _g1->concurrent_mark()->cmThread()->during_cycle();
  if (!during_cycle) {
    ergo_verbose1(ErgoConcCycles,
                  "request concurrent cycle initiation",
                  ergo_format_reason("requested by GC cause")
                  ergo_format_str("GC cause"),
                  GCCause::to_string(gc_cause));
    collector_state()->set_initiate_conc_mark_if_possible(true);
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

  // First, collector_state()->during_initial_mark_pause() should not be already set. We
  // will set it here if we have to. However, it should be cleared by
  // the end of the pause (it's only set for the duration of an
  // initial-mark pause).
  assert(!collector_state()->during_initial_mark_pause(), "pre-condition");

  if (collector_state()->initiate_conc_mark_if_possible()) {
    // We had noticed on a previous pause that the heap occupancy has
    // gone over the initiating threshold and we should start a
    // concurrent marking cycle. So we might initiate one.

    bool during_cycle = _g1->concurrent_mark()->cmThread()->during_cycle();
    if (!during_cycle) {
      // The concurrent marking thread is not "during a cycle", i.e.,
      // it has completed the last one. So we can go ahead and
      // initiate a new cycle.

      collector_state()->set_during_initial_mark_pause(true);
      // We do not allow mixed GCs during marking.
      if (!collector_state()->gcs_are_young()) {
        collector_state()->set_gcs_are_young(true);
        ergo_verbose0(ErgoMixedGCs,
                      "end mixed GCs",
                      ergo_format_reason("concurrent cycle is about to start"));
      }

      // And we can now clear initiate_conc_mark_if_possible() as
      // we've already acted on it.
      collector_state()->set_initiate_conc_mark_if_possible(false);

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

class ParKnownGarbageHRClosure: public HeapRegionClosure {
  G1CollectedHeap* _g1h;
  CSetChooserParUpdater _cset_updater;

public:
  ParKnownGarbageHRClosure(CollectionSetChooser* hrSorted,
                           uint chunk_size) :
    _g1h(G1CollectedHeap::heap()),
    _cset_updater(hrSorted, true /* parallel */, chunk_size) { }

  bool doHeapRegion(HeapRegion* r) {
    // Do we have any marking information for this region?
    if (r->is_marked()) {
      // We will skip any region that's currently used as an old GC
      // alloc region (we should not consider those for collection
      // before we fill them up).
      if (_cset_updater.should_add(r) && !_g1h->is_old_gc_alloc_region(r)) {
        _cset_updater.add_region(r);
      }
    }
    return false;
  }
};

class ParKnownGarbageTask: public AbstractGangTask {
  CollectionSetChooser* _hrSorted;
  uint _chunk_size;
  G1CollectedHeap* _g1;
  HeapRegionClaimer _hrclaimer;

public:
  ParKnownGarbageTask(CollectionSetChooser* hrSorted, uint chunk_size, uint n_workers) :
      AbstractGangTask("ParKnownGarbageTask"),
      _hrSorted(hrSorted), _chunk_size(chunk_size),
      _g1(G1CollectedHeap::heap()), _hrclaimer(n_workers) {}

  void work(uint worker_id) {
    ParKnownGarbageHRClosure parKnownGarbageCl(_hrSorted, _chunk_size);
    _g1->heap_region_par_iterate(&parKnownGarbageCl, worker_id, &_hrclaimer);
  }
};

uint G1CollectorPolicy::calculate_parallel_work_chunk_size(uint n_workers, uint n_regions) const {
  assert(n_workers > 0, "Active gc workers should be greater than 0");
  const uint overpartition_factor = 4;
  const uint min_chunk_size = MAX2(n_regions / n_workers, 1U);
  return MAX2(n_regions / (n_workers * overpartition_factor), min_chunk_size);
}

void
G1CollectorPolicy::record_concurrent_mark_cleanup_end() {
  _collectionSetChooser->clear();

  WorkGang* workers = _g1->workers();
  uint n_workers = workers->active_workers();

  uint n_regions = _g1->num_regions();
  uint chunk_size = calculate_parallel_work_chunk_size(n_workers, n_regions);
  _collectionSetChooser->prepare_for_par_region_addition(n_workers, n_regions, chunk_size);
  ParKnownGarbageTask par_known_garbage_task(_collectionSetChooser, chunk_size, n_workers);
  workers->run_task(&par_known_garbage_task);

  _collectionSetChooser->sort_regions();

  double end_sec = os::elapsedTime();
  double elapsed_time_ms = (end_sec - _mark_cleanup_start_sec) * 1000.0;
  _concurrent_mark_cleanup_times_ms->add(elapsed_time_ms);
  _cur_mark_stop_world_time_ms += elapsed_time_ms;
  _prev_collection_pause_end_ms += elapsed_time_ms;
  _mmu_tracker->add_pause(_mark_cleanup_start_sec, end_sec);
}

// Add the heap region at the head of the non-incremental collection set
void G1CollectorPolicy::add_old_region_to_cset(HeapRegion* hr) {
  assert(_inc_cset_build_state == Active, "Precondition");
  assert(hr->is_old(), "the region should be old");

  assert(!hr->in_collection_set(), "should not already be in the CSet");
  _g1->register_old_region_with_cset(hr);
  hr->set_next_in_collection_set(_collection_set);
  _collection_set = hr;
  _collection_set_bytes_used_before += hr->used();
  size_t rs_length = hr->rem_set()->occupied();
  _recorded_rs_lengths += rs_length;
  _old_cset_region_length += 1;
}

// Initialize the per-collection-set information
void G1CollectorPolicy::start_incremental_cset_building() {
  assert(_inc_cset_build_state == Inactive, "Precondition");

  _inc_cset_head = NULL;
  _inc_cset_tail = NULL;
  _inc_cset_bytes_used_before = 0;

  _inc_cset_max_finger = 0;
  _inc_cset_recorded_rs_lengths = 0;
  _inc_cset_recorded_rs_lengths_diffs = 0;
  _inc_cset_predicted_elapsed_time_ms = 0.0;
  _inc_cset_predicted_elapsed_time_ms_diffs = 0.0;
  _inc_cset_build_state = Active;
}

void G1CollectorPolicy::finalize_incremental_cset_building() {
  assert(_inc_cset_build_state == Active, "Precondition");
  assert(SafepointSynchronize::is_at_safepoint(), "should be at a safepoint");

  // The two "main" fields, _inc_cset_recorded_rs_lengths and
  // _inc_cset_predicted_elapsed_time_ms, are updated by the thread
  // that adds a new region to the CSet. Further updates by the
  // concurrent refinement thread that samples the young RSet lengths
  // are accumulated in the *_diffs fields. Here we add the diffs to
  // the "main" fields.

  if (_inc_cset_recorded_rs_lengths_diffs >= 0) {
    _inc_cset_recorded_rs_lengths += _inc_cset_recorded_rs_lengths_diffs;
  } else {
    // This is defensive. The diff should in theory be always positive
    // as RSets can only grow between GCs. However, given that we
    // sample their size concurrently with other threads updating them
    // it's possible that we might get the wrong size back, which
    // could make the calculations somewhat inaccurate.
    size_t diffs = (size_t) (-_inc_cset_recorded_rs_lengths_diffs);
    if (_inc_cset_recorded_rs_lengths >= diffs) {
      _inc_cset_recorded_rs_lengths -= diffs;
    } else {
      _inc_cset_recorded_rs_lengths = 0;
    }
  }
  _inc_cset_predicted_elapsed_time_ms +=
                                     _inc_cset_predicted_elapsed_time_ms_diffs;

  _inc_cset_recorded_rs_lengths_diffs = 0;
  _inc_cset_predicted_elapsed_time_ms_diffs = 0.0;
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

  double region_elapsed_time_ms = predict_region_elapsed_time_ms(hr, collector_state()->gcs_are_young());
  size_t used_bytes = hr->used();
  _inc_cset_recorded_rs_lengths += rs_length;
  _inc_cset_predicted_elapsed_time_ms += region_elapsed_time_ms;
  _inc_cset_bytes_used_before += used_bytes;

  // Cache the values we have added to the aggregated information
  // in the heap region in case we have to remove this region from
  // the incremental collection set, or it is updated by the
  // rset sampling code
  hr->set_recorded_rs_length(rs_length);
  hr->set_predicted_elapsed_time_ms(region_elapsed_time_ms);
}

void G1CollectorPolicy::update_incremental_cset_info(HeapRegion* hr,
                                                     size_t new_rs_length) {
  // Update the CSet information that is dependent on the new RS length
  assert(hr->is_young(), "Precondition");
  assert(!SafepointSynchronize::is_at_safepoint(),
                                               "should not be at a safepoint");

  // We could have updated _inc_cset_recorded_rs_lengths and
  // _inc_cset_predicted_elapsed_time_ms directly but we'd need to do
  // that atomically, as this code is executed by a concurrent
  // refinement thread, potentially concurrently with a mutator thread
  // allocating a new region and also updating the same fields. To
  // avoid the atomic operations we accumulate these updates on two
  // separate fields (*_diffs) and we'll just add them to the "main"
  // fields at the start of a GC.

  ssize_t old_rs_length = (ssize_t) hr->recorded_rs_length();
  ssize_t rs_lengths_diff = (ssize_t) new_rs_length - old_rs_length;
  _inc_cset_recorded_rs_lengths_diffs += rs_lengths_diff;

  double old_elapsed_time_ms = hr->predicted_elapsed_time_ms();
  double new_region_elapsed_time_ms = predict_region_elapsed_time_ms(hr, collector_state()->gcs_are_young());
  double elapsed_ms_diff = new_region_elapsed_time_ms - old_elapsed_time_ms;
  _inc_cset_predicted_elapsed_time_ms_diffs += elapsed_ms_diff;

  hr->set_recorded_rs_length(new_rs_length);
  hr->set_predicted_elapsed_time_ms(new_region_elapsed_time_ms);
}

void G1CollectorPolicy::add_region_to_incremental_cset_common(HeapRegion* hr) {
  assert(hr->is_young(), "invariant");
  assert(hr->young_index_in_cset() > -1, "should have already been set");
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
  _g1->register_young_region_with_cset(hr);
  assert(hr->next_in_collection_set() == NULL, "invariant");
}

// Add the region at the RHS of the incremental cset
void G1CollectorPolicy::add_region_to_incremental_cset_rhs(HeapRegion* hr) {
  // We should only ever be appending survivors at the end of a pause
  assert(hr->is_survivor(), "Logic");

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
  assert(hr->is_eden(), "Logic");

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
    st->print_cr("  " HR_FORMAT ", P: " PTR_FORMAT "N: " PTR_FORMAT ", age: %4d",
                 HR_FORMAT_PARAMS(csr),
                 p2i(csr->prev_top_at_mark_start()), p2i(csr->next_top_at_mark_start()),
                 csr->age_in_surv_rate_group_cond());
    csr = next;
  }
}
#endif // !PRODUCT

double G1CollectorPolicy::reclaimable_bytes_perc(size_t reclaimable_bytes) const {
  // Returns the given amount of reclaimable bytes (that represents
  // the amount of reclaimable space still to be collected) as a
  // percentage of the current heap capacity.
  size_t capacity_bytes = _g1->capacity();
  return (double) reclaimable_bytes * 100.0 / (double) capacity_bytes;
}

bool G1CollectorPolicy::next_gc_should_be_mixed(const char* true_action_str,
                                                const char* false_action_str) const {
  CollectionSetChooser* cset_chooser = _collectionSetChooser;
  if (cset_chooser->is_empty()) {
    ergo_verbose0(ErgoMixedGCs,
                  false_action_str,
                  ergo_format_reason("candidate old regions not available"));
    return false;
  }

  // Is the amount of uncollected reclaimable space above G1HeapWastePercent?
  size_t reclaimable_bytes = cset_chooser->remaining_reclaimable_bytes();
  double reclaimable_perc = reclaimable_bytes_perc(reclaimable_bytes);
  double threshold = (double) G1HeapWastePercent;
  if (reclaimable_perc <= threshold) {
    ergo_verbose4(ErgoMixedGCs,
              false_action_str,
              ergo_format_reason("reclaimable percentage not over threshold")
              ergo_format_region("candidate old regions")
              ergo_format_byte_perc("reclaimable")
              ergo_format_perc("threshold"),
              cset_chooser->remaining_regions(),
              reclaimable_bytes,
              reclaimable_perc, threshold);
    return false;
  }

  ergo_verbose4(ErgoMixedGCs,
                true_action_str,
                ergo_format_reason("candidate old regions available")
                ergo_format_region("candidate old regions")
                ergo_format_byte_perc("reclaimable")
                ergo_format_perc("threshold"),
                cset_chooser->remaining_regions(),
                reclaimable_bytes,
                reclaimable_perc, threshold);
  return true;
}

uint G1CollectorPolicy::calc_min_old_cset_length() const {
  // The min old CSet region bound is based on the maximum desired
  // number of mixed GCs after a cycle. I.e., even if some old regions
  // look expensive, we should add them to the CSet anyway to make
  // sure we go through the available old regions in no more than the
  // maximum desired number of mixed GCs.
  //
  // The calculation is based on the number of marked regions we added
  // to the CSet chooser in the first place, not how many remain, so
  // that the result is the same during all mixed GCs that follow a cycle.

  const size_t region_num = (size_t) _collectionSetChooser->length();
  const size_t gc_num = (size_t) MAX2(G1MixedGCCountTarget, (uintx) 1);
  size_t result = region_num / gc_num;
  // emulate ceiling
  if (result * gc_num < region_num) {
    result += 1;
  }
  return (uint) result;
}

uint G1CollectorPolicy::calc_max_old_cset_length() const {
  // The max old CSet region bound is based on the threshold expressed
  // as a percentage of the heap size. I.e., it should bound the
  // number of old regions added to the CSet irrespective of how many
  // of them are available.

  const G1CollectedHeap* g1h = G1CollectedHeap::heap();
  const size_t region_num = g1h->num_regions();
  const size_t perc = (size_t) G1OldCSetRegionThresholdPercent;
  size_t result = region_num * perc / 100;
  // emulate ceiling
  if (100 * result < region_num * perc) {
    result += 1;
  }
  return (uint) result;
}


double G1CollectorPolicy::finalize_young_cset_part(double target_pause_time_ms) {
  double young_start_time_sec = os::elapsedTime();

  YoungList* young_list = _g1->young_list();
  finalize_incremental_cset_building();

  guarantee(target_pause_time_ms > 0.0,
            "target_pause_time_ms = %1.6lf should be positive", target_pause_time_ms);
  guarantee(_collection_set == NULL, "Precondition");

  double base_time_ms = predict_base_elapsed_time_ms(_pending_cards);
  double time_remaining_ms = MAX2(target_pause_time_ms - base_time_ms, 0.0);

  ergo_verbose4(ErgoCSetConstruction | ErgoHigh,
                "start choosing CSet",
                ergo_format_size("_pending_cards")
                ergo_format_ms("predicted base time")
                ergo_format_ms("remaining time")
                ergo_format_ms("target pause time"),
                _pending_cards, base_time_ms, time_remaining_ms, target_pause_time_ms);

  collector_state()->set_last_gc_was_young(collector_state()->gcs_are_young());

  if (collector_state()->last_gc_was_young()) {
    _trace_young_gen_time_data.increment_young_collection_count();
  } else {
    _trace_young_gen_time_data.increment_mixed_collection_count();
  }

  // The young list is laid with the survivor regions from the previous
  // pause are appended to the RHS of the young list, i.e.
  //   [Newly Young Regions ++ Survivors from last pause].

  uint survivor_region_length = young_list->survivor_length();
  uint eden_region_length = young_list->eden_length();
  init_cset_region_lengths(eden_region_length, survivor_region_length);

  HeapRegion* hr = young_list->first_survivor_region();
  while (hr != NULL) {
    assert(hr->is_survivor(), "badly formed young list");
    // There is a convention that all the young regions in the CSet
    // are tagged as "eden", so we do this for the survivors here. We
    // use the special set_eden_pre_gc() as it doesn't check that the
    // region is free (which is not the case here).
    hr->set_eden_pre_gc();
    hr = hr->get_next_young_region();
  }

  // Clear the fields that point to the survivor list - they are all young now.
  young_list->clear_survivors();

  _collection_set = _inc_cset_head;
  _collection_set_bytes_used_before = _inc_cset_bytes_used_before;
  time_remaining_ms = MAX2(time_remaining_ms - _inc_cset_predicted_elapsed_time_ms, 0.0);

  ergo_verbose4(ErgoCSetConstruction | ErgoHigh,
                "add young regions to CSet",
                ergo_format_region("eden")
                ergo_format_region("survivors")
                ergo_format_ms("predicted young region time")
                ergo_format_ms("target pause time"),
                eden_region_length, survivor_region_length,
                _inc_cset_predicted_elapsed_time_ms,
                target_pause_time_ms);

  // The number of recorded young regions is the incremental
  // collection set's current size
  set_recorded_rs_lengths(_inc_cset_recorded_rs_lengths);

  double young_end_time_sec = os::elapsedTime();
  phase_times()->record_young_cset_choice_time_ms((young_end_time_sec - young_start_time_sec) * 1000.0);

  return time_remaining_ms;
}

void G1CollectorPolicy::finalize_old_cset_part(double time_remaining_ms) {
  double non_young_start_time_sec = os::elapsedTime();
  double predicted_old_time_ms = 0.0;


  if (!collector_state()->gcs_are_young()) {
    CollectionSetChooser* cset_chooser = _collectionSetChooser;
    cset_chooser->verify();
    const uint min_old_cset_length = calc_min_old_cset_length();
    const uint max_old_cset_length = calc_max_old_cset_length();

    uint expensive_region_num = 0;
    bool check_time_remaining = adaptive_young_list_length();

    HeapRegion* hr = cset_chooser->peek();
    while (hr != NULL) {
      if (old_cset_region_length() >= max_old_cset_length) {
        // Added maximum number of old regions to the CSet.
        ergo_verbose2(ErgoCSetConstruction,
                      "finish adding old regions to CSet",
                      ergo_format_reason("old CSet region num reached max")
                      ergo_format_region("old")
                      ergo_format_region("max"),
                      old_cset_region_length(), max_old_cset_length);
        break;
      }


      // Stop adding regions if the remaining reclaimable space is
      // not above G1HeapWastePercent.
      size_t reclaimable_bytes = cset_chooser->remaining_reclaimable_bytes();
      double reclaimable_perc = reclaimable_bytes_perc(reclaimable_bytes);
      double threshold = (double) G1HeapWastePercent;
      if (reclaimable_perc <= threshold) {
        // We've added enough old regions that the amount of uncollected
        // reclaimable space is at or below the waste threshold. Stop
        // adding old regions to the CSet.
        ergo_verbose5(ErgoCSetConstruction,
                      "finish adding old regions to CSet",
                      ergo_format_reason("reclaimable percentage not over threshold")
                      ergo_format_region("old")
                      ergo_format_region("max")
                      ergo_format_byte_perc("reclaimable")
                      ergo_format_perc("threshold"),
                      old_cset_region_length(),
                      max_old_cset_length,
                      reclaimable_bytes,
                      reclaimable_perc, threshold);
        break;
      }

      double predicted_time_ms = predict_region_elapsed_time_ms(hr, collector_state()->gcs_are_young());
      if (check_time_remaining) {
        if (predicted_time_ms > time_remaining_ms) {
          // Too expensive for the current CSet.

          if (old_cset_region_length() >= min_old_cset_length) {
            // We have added the minimum number of old regions to the CSet,
            // we are done with this CSet.
            ergo_verbose4(ErgoCSetConstruction,
                          "finish adding old regions to CSet",
                          ergo_format_reason("predicted time is too high")
                          ergo_format_ms("predicted time")
                          ergo_format_ms("remaining time")
                          ergo_format_region("old")
                          ergo_format_region("min"),
                          predicted_time_ms, time_remaining_ms,
                          old_cset_region_length(), min_old_cset_length);
            break;
          }

          // We'll add it anyway given that we haven't reached the
          // minimum number of old regions.
          expensive_region_num += 1;
        }
      } else {
        if (old_cset_region_length() >= min_old_cset_length) {
          // In the non-auto-tuning case, we'll finish adding regions
          // to the CSet if we reach the minimum.
          ergo_verbose2(ErgoCSetConstruction,
                        "finish adding old regions to CSet",
                        ergo_format_reason("old CSet region num reached min")
                        ergo_format_region("old")
                        ergo_format_region("min"),
                        old_cset_region_length(), min_old_cset_length);
          break;
        }
      }

      // We will add this region to the CSet.
      time_remaining_ms = MAX2(time_remaining_ms - predicted_time_ms, 0.0);
      predicted_old_time_ms += predicted_time_ms;
      cset_chooser->pop(); // already have region via peek()
      _g1->old_set_remove(hr);
      add_old_region_to_cset(hr);

      hr = cset_chooser->peek();
    }
    if (hr == NULL) {
      ergo_verbose0(ErgoCSetConstruction,
                    "finish adding old regions to CSet",
                    ergo_format_reason("candidate old regions not available"));
    }

    if (expensive_region_num > 0) {
      // We print the information once here at the end, predicated on
      // whether we added any apparently expensive regions or not, to
      // avoid generating output per region.
      ergo_verbose4(ErgoCSetConstruction,
                    "added expensive regions to CSet",
                    ergo_format_reason("old CSet region num not reached min")
                    ergo_format_region("old")
                    ergo_format_region("expensive")
                    ergo_format_region("min")
                    ergo_format_ms("remaining time"),
                    old_cset_region_length(),
                    expensive_region_num,
                    min_old_cset_length,
                    time_remaining_ms);
    }

    cset_chooser->verify();
  }

  stop_incremental_cset_building();

  ergo_verbose3(ErgoCSetConstruction,
                "finish choosing CSet",
                ergo_format_region("old")
                ergo_format_ms("predicted old region time")
                ergo_format_ms("time remaining"),
                old_cset_region_length(),
                predicted_old_time_ms, time_remaining_ms);

  double non_young_end_time_sec = os::elapsedTime();
  phase_times()->record_non_young_cset_choice_time_ms((non_young_end_time_sec - non_young_start_time_sec) * 1000.0);
}

void TraceYoungGenTimeData::record_start_collection(double time_to_stop_the_world_ms) {
  if(TraceYoungGenTime) {
    _all_stop_world_times_ms.add(time_to_stop_the_world_ms);
  }
}

void TraceYoungGenTimeData::record_yield_time(double yield_time_ms) {
  if(TraceYoungGenTime) {
    _all_yield_times_ms.add(yield_time_ms);
  }
}

void TraceYoungGenTimeData::record_end_collection(double pause_time_ms, G1GCPhaseTimes* phase_times) {
  if(TraceYoungGenTime) {
    _total.add(pause_time_ms);
    _other.add(pause_time_ms - phase_times->accounted_time_ms());
    _root_region_scan_wait.add(phase_times->root_region_scan_wait_time_ms());
    _parallel.add(phase_times->cur_collection_par_time_ms());
    _ext_root_scan.add(phase_times->average_time_ms(G1GCPhaseTimes::ExtRootScan));
    _satb_filtering.add(phase_times->average_time_ms(G1GCPhaseTimes::SATBFiltering));
    _update_rs.add(phase_times->average_time_ms(G1GCPhaseTimes::UpdateRS));
    _scan_rs.add(phase_times->average_time_ms(G1GCPhaseTimes::ScanRS));
    _obj_copy.add(phase_times->average_time_ms(G1GCPhaseTimes::ObjCopy));
    _termination.add(phase_times->average_time_ms(G1GCPhaseTimes::Termination));

    double parallel_known_time = phase_times->average_time_ms(G1GCPhaseTimes::ExtRootScan) +
      phase_times->average_time_ms(G1GCPhaseTimes::SATBFiltering) +
      phase_times->average_time_ms(G1GCPhaseTimes::UpdateRS) +
      phase_times->average_time_ms(G1GCPhaseTimes::ScanRS) +
      phase_times->average_time_ms(G1GCPhaseTimes::ObjCopy) +
      phase_times->average_time_ms(G1GCPhaseTimes::Termination);

    double parallel_other_time = phase_times->cur_collection_par_time_ms() - parallel_known_time;
    _parallel_other.add(parallel_other_time);
    _clear_ct.add(phase_times->cur_clear_ct_time_ms());
  }
}

void TraceYoungGenTimeData::increment_young_collection_count() {
  if(TraceYoungGenTime) {
    ++_young_pause_num;
  }
}

void TraceYoungGenTimeData::increment_mixed_collection_count() {
  if(TraceYoungGenTime) {
    ++_mixed_pause_num;
  }
}

void TraceYoungGenTimeData::print_summary(const char* str,
                                          const NumberSeq* seq) const {
  double sum = seq->sum();
  gclog_or_tty->print_cr("%-27s = %8.2lf s (avg = %8.2lf ms)",
                str, sum / 1000.0, seq->avg());
}

void TraceYoungGenTimeData::print_summary_sd(const char* str,
                                             const NumberSeq* seq) const {
  print_summary(str, seq);
  gclog_or_tty->print_cr("%45s = %5d, std dev = %8.2lf ms, max = %8.2lf ms)",
                "(num", seq->num(), seq->sd(), seq->maximum());
}

void TraceYoungGenTimeData::print() const {
  if (!TraceYoungGenTime) {
    return;
  }

  gclog_or_tty->print_cr("ALL PAUSES");
  print_summary_sd("   Total", &_total);
  gclog_or_tty->cr();
  gclog_or_tty->cr();
  gclog_or_tty->print_cr("   Young GC Pauses: %8d", _young_pause_num);
  gclog_or_tty->print_cr("   Mixed GC Pauses: %8d", _mixed_pause_num);
  gclog_or_tty->cr();

  gclog_or_tty->print_cr("EVACUATION PAUSES");

  if (_young_pause_num == 0 && _mixed_pause_num == 0) {
    gclog_or_tty->print_cr("none");
  } else {
    print_summary_sd("   Evacuation Pauses", &_total);
    print_summary("      Root Region Scan Wait", &_root_region_scan_wait);
    print_summary("      Parallel Time", &_parallel);
    print_summary("         Ext Root Scanning", &_ext_root_scan);
    print_summary("         SATB Filtering", &_satb_filtering);
    print_summary("         Update RS", &_update_rs);
    print_summary("         Scan RS", &_scan_rs);
    print_summary("         Object Copy", &_obj_copy);
    print_summary("         Termination", &_termination);
    print_summary("         Parallel Other", &_parallel_other);
    print_summary("      Clear CT", &_clear_ct);
    print_summary("      Other", &_other);
  }
  gclog_or_tty->cr();

  gclog_or_tty->print_cr("MISC");
  print_summary_sd("   Stop World", &_all_stop_world_times_ms);
  print_summary_sd("   Yields", &_all_yield_times_ms);
}

void TraceOldGenTimeData::record_full_collection(double full_gc_time_ms) {
  if (TraceOldGenTime) {
    _all_full_gc_times.add(full_gc_time_ms);
  }
}

void TraceOldGenTimeData::print() const {
  if (!TraceOldGenTime) {
    return;
  }

  if (_all_full_gc_times.num() > 0) {
    gclog_or_tty->print("\n%4d full_gcs: total time = %8.2f s",
      _all_full_gc_times.num(),
      _all_full_gc_times.sum() / 1000.0);
    gclog_or_tty->print_cr(" (avg = %8.2fms).", _all_full_gc_times.avg());
    gclog_or_tty->print_cr("                     [std. dev = %8.2f ms, max = %8.2f ms]",
      _all_full_gc_times.sd(),
      _all_full_gc_times.maximum());
  }
}
