/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/concurrentMarkThread.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSet.hpp"
#include "gc/g1/g1CollectorPolicy.hpp"
#include "gc/g1/g1ConcurrentMark.hpp"
#include "gc/g1/g1IHOPControl.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/shared/gcPolicyCounters.hpp"
#include "runtime/arguments.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/debug.hpp"
#include "utilities/pair.hpp"

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

  _recent_gc_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),

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

  // add here any more surv rate groups
  _recorded_survivor_regions(0),
  _recorded_survivor_head(NULL),
  _recorded_survivor_tail(NULL),
  _survivors_age_table(true),

  _gc_overhead_perc(0.0),

  _bytes_allocated_in_old_since_last_gc(0),
  _ihop_control(NULL),
  _initial_mark_to_mixed() {

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

  _recent_prev_end_times_for_all_gcs_sec->add(os::elapsedTime());
  _prev_collection_pause_end_ms = os::elapsedTime() * 1000.0;
  clear_ratio_check_data();

  _phase_times = new G1GCPhaseTimes(ParallelGCThreads);

  int index = MIN2(ParallelGCThreads - 1, 7u);

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

  _ihop_control = create_ihop_control();
}

G1CollectorPolicy::~G1CollectorPolicy() {
  delete _ihop_control;
}

double G1CollectorPolicy::get_new_prediction(TruncatedSeq const* seq) const {
  return _predictor.get_new_prediction(seq);
}

size_t G1CollectorPolicy::get_new_size_prediction(TruncatedSeq const* seq) const {
  return (size_t)get_new_prediction(seq);
}

void G1CollectorPolicy::initialize_alignments() {
  _space_alignment = HeapRegion::GrainBytes;
  size_t card_table_alignment = CardTableRS::ct_max_alignment_constraint();
  size_t page_size = UseLargePages ? os::large_page_size() : os::vm_page_size();
  _heap_alignment = MAX3(card_table_alignment, _space_alignment, page_size);
}

G1CollectorState* G1CollectorPolicy::collector_state() const { return _g1->collector_state(); }

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
// every time the heap size changes.
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

  bool adaptive_young_list_length() const {
    return _adaptive_size;
  }
};


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

void G1CollectorPolicy::post_heap_initialize() {
  uintx max_regions = G1CollectedHeap::heap()->max_regions();
  size_t max_young_size = (size_t)_young_gen_sizer->max_young_length(max_regions) * HeapRegion::GrainBytes;
  if (max_young_size != MaxNewSize) {
    FLAG_SET_ERGO(size_t, MaxNewSize, max_young_size);
  }
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


void G1CollectorPolicy::init() {
  // Set aside an initial future to_space.
  _g1 = G1CollectedHeap::heap();
  _collection_set = _g1->collection_set();
  _collection_set->set_policy(this);

  assert(Heap_lock->owned_by_self(), "Locking discipline.");

  initialize_gc_policy_counters();

  if (adaptive_young_list_length()) {
    _young_list_fixed_length = 0;
  } else {
    _young_list_fixed_length = _young_gen_sizer->min_desired_young_length();
  }
  _free_regions_at_end_of_collection = _g1->num_free_regions();

  update_young_list_max_and_target_length();
  // We may immediately start allocating regions and placing them on the
  // collection set list. Initialize the per-collection set info
  _collection_set->start_incremental_building();
}

void G1CollectorPolicy::note_gc_start(uint num_active_workers) {
  phase_times()->note_gc_start(num_active_workers);
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

  // When copying, we will likely need more bytes free than is live in the region.
  // Add some safety margin to factor in the confidence of our guess, and the
  // natural expected waste.
  // (100.0 / G1ConfidencePercent) is a scale factor that expresses the uncertainty
  // of the calculation: the lower the confidence, the more headroom.
  // (100 + TargetPLABWastePct) represents the increase in expected bytes during
  // copying due to anticipated waste in the PLABs.
  double safety_factor = (100.0 / G1ConfidencePercent) * (100 + TargetPLABWastePct) / 100.0;
  size_t expected_bytes_to_copy = (size_t)(safety_factor * bytes_to_copy);

  if (expected_bytes_to_copy > free_bytes) {
    // end condition 3: out-of-space
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

  _ihop_control->update_target_occupancy(new_number_of_regions * HeapRegion::GrainBytes);
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

uint G1CollectorPolicy::update_young_list_max_and_target_length() {
  return update_young_list_max_and_target_length(get_new_size_prediction(_rs_lengths_seq));
}

uint G1CollectorPolicy::update_young_list_max_and_target_length(size_t rs_lengths) {
  uint unbounded_target_length = update_young_list_target_length(rs_lengths);
  update_max_gc_locker_expansion();
  return unbounded_target_length;
}

uint G1CollectorPolicy::update_young_list_target_length(size_t rs_lengths) {
  YoungTargetLengths young_lengths = young_list_target_lengths(rs_lengths);
  _young_list_target_length = young_lengths.first;
  return young_lengths.second;
}

G1CollectorPolicy::YoungTargetLengths G1CollectorPolicy::young_list_target_lengths(size_t rs_lengths) const {
  YoungTargetLengths result;

  // Calculate the absolute and desired min bounds first.

  // This is how many young regions we already have (currently: the survivors).
  uint base_min_length = recorded_survivor_regions();
  uint desired_min_length = calculate_young_list_desired_min_length(base_min_length);
  // This is the absolute minimum young length. Ensure that we
  // will at least have one eden region available for allocation.
  uint absolute_min_length = base_min_length + MAX2(_g1->young_list()->eden_length(), (uint)1);
  // If we shrank the young list target it should not shrink below the current size.
  desired_min_length = MAX2(desired_min_length, absolute_min_length);
  // Calculate the absolute and desired max bounds.

  uint desired_max_length = calculate_young_list_desired_max_length();

  uint young_list_target_length = 0;
  if (adaptive_young_list_length()) {
    if (collector_state()->gcs_are_young()) {
      young_list_target_length =
                        calculate_young_list_target_length(rs_lengths,
                                                           base_min_length,
                                                           desired_min_length,
                                                           desired_max_length);
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

  result.second = young_list_target_length;

  // We will try our best not to "eat" into the reserve.
  uint absolute_max_length = 0;
  if (_free_regions_at_end_of_collection > _reserve_regions) {
    absolute_max_length = _free_regions_at_end_of_collection - _reserve_regions;
  }
  if (desired_max_length > absolute_max_length) {
    desired_max_length = absolute_max_length;
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

  result.first = young_list_target_length;
  return result;
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
  size_t pending_cards = get_new_size_prediction(_pending_cards_seq);
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

void G1CollectorPolicy::revise_young_list_target_length_if_necessary(size_t rs_lengths) {
  guarantee( adaptive_young_list_length(), "should not call this otherwise" );

  if (rs_lengths > _rs_lengths_prediction) {
    // add 10% to avoid having to recalculate often
    size_t rs_lengths_prediction = rs_lengths * 1100 / 1000;
    update_rs_lengths_prediction(rs_lengths_prediction);

    update_young_list_max_and_target_length(rs_lengths_prediction);
  }
}

void G1CollectorPolicy::update_rs_lengths_prediction() {
  update_rs_lengths_prediction(get_new_size_prediction(_rs_lengths_seq));
}

void G1CollectorPolicy::update_rs_lengths_prediction(size_t prediction) {
  if (collector_state()->gcs_are_young() && adaptive_young_list_length()) {
    _rs_lengths_prediction = prediction;
  }
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
      log_error(gc, verify)("## %s: encountered NULL surv_rate_group", name);
      ret = false;
    }

    if (surv_rate_group == group) {
      int age = curr->age_in_surv_rate_group();

      if (age < 0) {
        log_error(gc, verify)("## %s: encountered negative age", name);
        ret = false;
      }

      if (age <= prev_age) {
        log_error(gc, verify)("## %s: region ages are not strictly increasing (%d, %d)", name, age, prev_age);
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
  // Release the future to-space so that it is available for compaction into.
  collector_state()->set_full_collection(true);
}

void G1CollectorPolicy::record_full_collection_end() {
  // Consider this like a collection pause for the purposes of allocation
  // since last pause.
  double end_sec = os::elapsedTime();
  double full_gc_time_sec = end_sec - _full_collection_start_sec;
  double full_gc_time_ms = full_gc_time_sec * 1000.0;

  update_recent_gc_times(end_sec, full_gc_time_ms);

  collector_state()->set_full_collection(false);

  // "Nuke" the heuristics that control the young/mixed GC
  // transitions and make sure we start with young GCs after the Full GC.
  collector_state()->set_gcs_are_young(true);
  collector_state()->set_last_young_gc(false);
  collector_state()->set_initiate_conc_mark_if_possible(need_to_start_conc_mark("end of Full GC", 0));
  collector_state()->set_during_initial_mark_pause(false);
  collector_state()->set_in_marking_window(false);
  collector_state()->set_in_marking_window_im(false);

  _short_lived_surv_rate_group->start_adding_regions();
  // also call this on any additional surv rate groups

  record_survivor_regions(0, NULL, NULL);

  _free_regions_at_end_of_collection = _g1->num_free_regions();
  // Reset survivors SurvRateGroup.
  _survivor_surv_rate_group->reset();
  update_young_list_max_and_target_length();
  update_rs_lengths_prediction();
  cset_chooser()->clear();

  _bytes_allocated_in_old_since_last_gc = 0;

  record_pause(FullGC, _full_collection_start_sec, end_sec);
}

void G1CollectorPolicy::record_collection_pause_start(double start_time_sec) {
  // We only need to do this here as the policy will only be applied
  // to the GC we're about to start. so, no point is calculating this
  // every time we calculate / recalculate the target young length.
  update_survivors_policy();

  assert(_g1->used() == _g1->recalculate_used(),
         "sanity, used: " SIZE_FORMAT " recalculate_used: " SIZE_FORMAT,
         _g1->used(), _g1->recalculate_used());

  phase_times()->record_cur_collection_start_sec(start_time_sec);
  _pending_cards = _g1->pending_card_num();

  _collection_set->reset_bytes_used_before();
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
}

void G1CollectorPolicy::record_concurrent_mark_remark_start() {
  _mark_remark_start_sec = os::elapsedTime();
  collector_state()->set_during_marking(false);
}

void G1CollectorPolicy::record_concurrent_mark_remark_end() {
  double end_time_sec = os::elapsedTime();
  double elapsed_time_ms = (end_time_sec - _mark_remark_start_sec)*1000.0;
  _concurrent_mark_remark_times_ms->add(elapsed_time_ms);
  _prev_collection_pause_end_ms += elapsed_time_ms;

  record_pause(Remark, _mark_remark_start_sec, end_time_sec);
}

void G1CollectorPolicy::record_concurrent_mark_cleanup_start() {
  _mark_cleanup_start_sec = os::elapsedTime();
}

void G1CollectorPolicy::record_concurrent_mark_cleanup_completed() {
  bool should_continue_with_reclaim = next_gc_should_be_mixed("request last young-only gc",
                                                              "skip last young-only gc");
  collector_state()->set_last_young_gc(should_continue_with_reclaim);
  // We skip the marking phase.
  if (!should_continue_with_reclaim) {
    abort_time_to_mixed_tracking();
  }
  collector_state()->set_in_marking_window(false);
}

double G1CollectorPolicy::average_time_ms(G1GCPhaseTimes::GCParPhases phase) const {
  return phase_times()->average_time_ms(phase);
}

double G1CollectorPolicy::young_other_time_ms() const {
  return phase_times()->young_cset_choice_time_ms() +
         phase_times()->young_free_cset_time_ms();
}

double G1CollectorPolicy::non_young_other_time_ms() const {
  return phase_times()->non_young_cset_choice_time_ms() +
         phase_times()->non_young_free_cset_time_ms();

}

double G1CollectorPolicy::other_time_ms(double pause_time_ms) const {
  return pause_time_ms -
         average_time_ms(G1GCPhaseTimes::UpdateRS) -
         average_time_ms(G1GCPhaseTimes::ScanRS) -
         average_time_ms(G1GCPhaseTimes::ObjCopy) -
         average_time_ms(G1GCPhaseTimes::Termination);
}

double G1CollectorPolicy::constant_other_time_ms(double pause_time_ms) const {
  return other_time_ms(pause_time_ms) - young_other_time_ms() - non_young_other_time_ms();
}

CollectionSetChooser* G1CollectorPolicy::cset_chooser() const {
  return _collection_set->cset_chooser();
}

bool G1CollectorPolicy::about_to_start_mixed_phase() const {
  return _g1->concurrent_mark()->cmThread()->during_cycle() || collector_state()->last_young_gc();
}

bool G1CollectorPolicy::need_to_start_conc_mark(const char* source, size_t alloc_word_size) {
  if (about_to_start_mixed_phase()) {
    return false;
  }

  size_t marking_initiating_used_threshold = _ihop_control->get_conc_mark_start_threshold();

  size_t cur_used_bytes = _g1->non_young_capacity_bytes();
  size_t alloc_byte_size = alloc_word_size * HeapWordSize;
  size_t marking_request_bytes = cur_used_bytes + alloc_byte_size;

  bool result = false;
  if (marking_request_bytes > marking_initiating_used_threshold) {
    result = collector_state()->gcs_are_young() && !collector_state()->last_young_gc();
    log_debug(gc, ergo, ihop)("%s occupancy: " SIZE_FORMAT "B allocation request: " SIZE_FORMAT "B threshold: " SIZE_FORMAT "B (%1.2f) source: %s",
                              result ? "Request concurrent cycle initiation (occupancy higher than threshold)" : "Do not request concurrent cycle initiation (still doing mixed collections)",
                              cur_used_bytes, alloc_byte_size, marking_initiating_used_threshold, (double) marking_initiating_used_threshold / _g1->capacity() * 100, source);
  }

  return result;
}

// Anything below that is considered to be zero
#define MIN_TIMER_GRANULARITY 0.0000001

void G1CollectorPolicy::record_collection_pause_end(double pause_time_ms, size_t cards_scanned, size_t heap_used_bytes_before_gc) {
  double end_time_sec = os::elapsedTime();

  size_t cur_used_bytes = _g1->used();
  assert(cur_used_bytes == _g1->recalculate_used(), "It should!");
  bool last_pause_included_initial_mark = false;
  bool update_stats = !_g1->evacuation_failed();

  NOT_PRODUCT(_short_lived_surv_rate_group->print());

  record_pause(young_gc_pause_kind(), end_time_sec - pause_time_ms / 1000.0, end_time_sec);

  last_pause_included_initial_mark = collector_state()->during_initial_mark_pause();
  if (last_pause_included_initial_mark) {
    record_concurrent_mark_init_end(0.0);
  } else {
    maybe_start_marking();
  }

  double app_time_ms = (phase_times()->cur_collection_start_sec() * 1000.0 - _prev_collection_pause_end_ms);
  if (app_time_ms < MIN_TIMER_GRANULARITY) {
    // This usually happens due to the timer not having the required
    // granularity. Some Linuxes are the usual culprits.
    // We'll just set it to something (arbitrarily) small.
    app_time_ms = 1.0;
  }

  if (update_stats) {
    // We maintain the invariant that all objects allocated by mutator
    // threads will be allocated out of eden regions. So, we can use
    // the eden region number allocated since the previous GC to
    // calculate the application's allocate rate. The only exception
    // to that is humongous objects that are allocated separately. But
    // given that humongous object allocations do not really affect
    // either the pause's duration nor when the next pause will take
    // place we can safely ignore them here.
    uint regions_allocated = _collection_set->eden_region_length();
    double alloc_rate_ms = (double) regions_allocated / app_time_ms;
    _alloc_rate_ms_seq->add(alloc_rate_ms);

    double interval_ms =
      (end_time_sec - _recent_prev_end_times_for_all_gcs_sec->oldest()) * 1000.0;
    update_recent_gc_times(end_time_sec, pause_time_ms);
    _recent_avg_pause_time_ratio = _recent_gc_times_ms->sum()/interval_ms;
    if (recent_avg_pause_time_ratio() < 0.0 ||
        (recent_avg_pause_time_ratio() - 1.0 > 0.0)) {
      // Clip ratio between 0.0 and 1.0, and continue. This will be fixed in
      // CR 6902692 by redoing the manner in which the ratio is incrementally computed.
      if (_recent_avg_pause_time_ratio < 0.0) {
        _recent_avg_pause_time_ratio = 0.0;
      } else {
        assert(_recent_avg_pause_time_ratio - 1.0 > 0.0, "Ctl-point invariant");
        _recent_avg_pause_time_ratio = 1.0;
      }
    }

    // Compute the ratio of just this last pause time to the entire time range stored
    // in the vectors. Comparing this pause to the entire range, rather than only the
    // most recent interval, has the effect of smoothing over a possible transient 'burst'
    // of more frequent pauses that don't really reflect a change in heap occupancy.
    // This reduces the likelihood of a needless heap expansion being triggered.
    _last_pause_time_ratio =
      (pause_time_ms * _recent_prev_end_times_for_all_gcs_sec->num()) / interval_ms;
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
    assert(!last_pause_included_initial_mark, "The last young GC is not allowed to be an initial mark GC");

    if (next_gc_should_be_mixed("start mixed GCs",
                                "do not start mixed GCs")) {
      collector_state()->set_gcs_are_young(false);
    } else {
      // We aborted the mixed GC phase early.
      abort_time_to_mixed_tracking();
    }

    collector_state()->set_last_young_gc(false);
  }

  if (!collector_state()->last_gc_was_young()) {
    // This is a mixed GC. Here we decide whether to continue doing
    // mixed GCs or not.
    if (!next_gc_should_be_mixed("continue mixed GCs",
                                 "do not continue mixed GCs")) {
      collector_state()->set_gcs_are_young(true);

      maybe_start_marking();
    }
  }

  _short_lived_surv_rate_group->start_adding_regions();
  // Do that for any other surv rate groups

  double scan_hcc_time_ms = ConcurrentG1Refine::hot_card_cache_enabled() ? average_time_ms(G1GCPhaseTimes::ScanHCC) : 0.0;

  if (update_stats) {
    double cost_per_card_ms = 0.0;
    if (_pending_cards > 0) {
      cost_per_card_ms = (average_time_ms(G1GCPhaseTimes::UpdateRS) - scan_hcc_time_ms) / (double) _pending_cards;
      _cost_per_card_ms_seq->add(cost_per_card_ms);
    }
    _cost_scan_hcc_seq->add(scan_hcc_time_ms);

    double cost_per_entry_ms = 0.0;
    if (cards_scanned > 10) {
      cost_per_entry_ms = average_time_ms(G1GCPhaseTimes::ScanRS) / (double) cards_scanned;
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
    size_t recorded_rs_lengths = _collection_set->recorded_rs_lengths();
    if (_max_rs_lengths > recorded_rs_lengths) {
      rs_length_diff = _max_rs_lengths - recorded_rs_lengths;
    }
    _rs_length_diff_seq->add((double) rs_length_diff);

    size_t freed_bytes = heap_used_bytes_before_gc - cur_used_bytes;
    size_t copied_bytes = _collection_set->bytes_used_before() - freed_bytes;
    double cost_per_byte_ms = 0.0;

    if (copied_bytes > 0) {
      cost_per_byte_ms = average_time_ms(G1GCPhaseTimes::ObjCopy) / (double) copied_bytes;
      if (collector_state()->in_marking_window()) {
        _cost_per_byte_ms_during_cm_seq->add(cost_per_byte_ms);
      } else {
        _cost_per_byte_ms_seq->add(cost_per_byte_ms);
      }
    }

    if (_collection_set->young_region_length() > 0) {
      _young_other_cost_per_region_ms_seq->add(young_other_time_ms() /
                                               _collection_set->young_region_length());
    }

    if (_collection_set->old_region_length() > 0) {
      _non_young_other_cost_per_region_ms_seq->add(non_young_other_time_ms() /
                                                   _collection_set->old_region_length());
    }

    _constant_other_time_ms_seq->add(constant_other_time_ms(pause_time_ms));

    _pending_cards_seq->add((double) _pending_cards);
    _rs_lengths_seq->add((double) _max_rs_lengths);
  }

  collector_state()->set_in_marking_window(new_in_marking_window);
  collector_state()->set_in_marking_window_im(new_in_marking_window_im);
  _free_regions_at_end_of_collection = _g1->num_free_regions();
  // IHOP control wants to know the expected young gen length if it were not
  // restrained by the heap reserve. Using the actual length would make the
  // prediction too small and the limit the young gen every time we get to the
  // predicted target occupancy.
  size_t last_unrestrained_young_length = update_young_list_max_and_target_length();
  update_rs_lengths_prediction();

  update_ihop_prediction(app_time_ms / 1000.0,
                         _bytes_allocated_in_old_since_last_gc,
                         last_unrestrained_young_length * HeapRegion::GrainBytes);
  _bytes_allocated_in_old_since_last_gc = 0;

  _ihop_control->send_trace_event(_g1->gc_tracer_stw());

  // Note that _mmu_tracker->max_gc_time() returns the time in seconds.
  double update_rs_time_goal_ms = _mmu_tracker->max_gc_time() * MILLIUNITS * G1RSetUpdatingPauseTimePercent / 100.0;

  if (update_rs_time_goal_ms < scan_hcc_time_ms) {
    log_debug(gc, ergo, refine)("Adjust concurrent refinement thresholds (scanning the HCC expected to take longer than Update RS time goal)."
                                "Update RS time goal: %1.2fms Scan HCC time: %1.2fms",
                                update_rs_time_goal_ms, scan_hcc_time_ms);

    update_rs_time_goal_ms = 0;
  } else {
    update_rs_time_goal_ms -= scan_hcc_time_ms;
  }
  adjust_concurrent_refinement(average_time_ms(G1GCPhaseTimes::UpdateRS) - scan_hcc_time_ms,
                               phase_times()->sum_thread_work_items(G1GCPhaseTimes::UpdateRS),
                               update_rs_time_goal_ms);

  cset_chooser()->verify();
}

G1IHOPControl* G1CollectorPolicy::create_ihop_control() const {
  if (G1UseAdaptiveIHOP) {
    return new G1AdaptiveIHOPControl(InitiatingHeapOccupancyPercent,
                                     &_predictor,
                                     G1ReservePercent,
                                     G1HeapWastePercent);
  } else {
    return new G1StaticIHOPControl(InitiatingHeapOccupancyPercent);
  }
}

void G1CollectorPolicy::update_ihop_prediction(double mutator_time_s,
                                               size_t mutator_alloc_bytes,
                                               size_t young_gen_size) {
  // Always try to update IHOP prediction. Even evacuation failures give information
  // about e.g. whether to start IHOP earlier next time.

  // Avoid using really small application times that might create samples with
  // very high or very low values. They may be caused by e.g. back-to-back gcs.
  double const min_valid_time = 1e-6;

  bool report = false;

  double marking_to_mixed_time = -1.0;
  if (!collector_state()->last_gc_was_young() && _initial_mark_to_mixed.has_result()) {
    marking_to_mixed_time = _initial_mark_to_mixed.last_marking_time();
    assert(marking_to_mixed_time > 0.0,
           "Initial mark to mixed time must be larger than zero but is %.3f",
           marking_to_mixed_time);
    if (marking_to_mixed_time > min_valid_time) {
      _ihop_control->update_marking_length(marking_to_mixed_time);
      report = true;
    }
  }

  // As an approximation for the young gc promotion rates during marking we use
  // all of them. In many applications there are only a few if any young gcs during
  // marking, which makes any prediction useless. This increases the accuracy of the
  // prediction.
  if (collector_state()->last_gc_was_young() && mutator_time_s > min_valid_time) {
    _ihop_control->update_allocation_info(mutator_time_s, mutator_alloc_bytes, young_gen_size);
    report = true;
  }

  if (report) {
    report_ihop_statistics();
  }
}

void G1CollectorPolicy::report_ihop_statistics() {
  _ihop_control->print();
}

void G1CollectorPolicy::print_phases() {
  phase_times()->print();
}

void G1CollectorPolicy::adjust_concurrent_refinement(double update_rs_time,
                                                     double update_rs_processed_buffers,
                                                     double goal_ms) {
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  ConcurrentG1Refine *cg1r = G1CollectedHeap::heap()->concurrent_g1_refine();

  if (G1UseAdaptiveConcRefinement) {
    const int k_gy = 3, k_gr = 6;
    const double inc_k = 1.1, dec_k = 0.9;

    size_t g = cg1r->green_zone();
    if (update_rs_time > goal_ms) {
      g = (size_t)(g * dec_k);  // Can become 0, that's OK. That would mean a mutator-only processing.
    } else {
      if (update_rs_time < goal_ms && update_rs_processed_buffers > g) {
        g = (size_t)MAX2(g * inc_k, g + 1.0);
      }
    }
    // Change the refinement threads params
    cg1r->set_green_zone(g);
    cg1r->set_yellow_zone(g * k_gy);
    cg1r->set_red_zone(g * k_gr);
    cg1r->reinitialize_threads();

    size_t processing_threshold_delta = MAX2<size_t>(cg1r->green_zone() * _predictor.sigma(), 1);
    size_t processing_threshold = MIN2(cg1r->green_zone() + processing_threshold_delta,
                                    cg1r->yellow_zone());
    // Change the barrier params
    dcqs.set_process_completed_threshold((int)processing_threshold);
    dcqs.set_max_completed_queue((int)cg1r->red_zone());
  }

  size_t curr_queue_size = dcqs.completed_buffers_num();
  if (curr_queue_size >= cg1r->yellow_zone()) {
    dcqs.set_completed_queue_padding(curr_queue_size);
  } else {
    dcqs.set_completed_queue_padding(0);
  }
  dcqs.notify_if_necessary();
}

size_t G1CollectorPolicy::predict_rs_length_diff() const {
  return get_new_size_prediction(_rs_length_diff_seq);
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

void G1CollectorPolicy::update_recent_gc_times(double end_time_sec,
                                               double elapsed_ms) {
  _recent_gc_times_ms->add(elapsed_ms);
  _recent_prev_end_times_for_all_gcs_sec->add(end_time_sec);
  _prev_collection_pause_end_ms = end_time_sec * 1000.0;
}

void G1CollectorPolicy::clear_ratio_check_data() {
  _ratio_over_threshold_count = 0;
  _ratio_over_threshold_sum = 0.0;
  _pauses_since_start = 0;
}

size_t G1CollectorPolicy::expansion_amount() {
  double recent_gc_overhead = recent_avg_pause_time_ratio() * 100.0;
  double last_gc_overhead = _last_pause_time_ratio * 100.0;
  double threshold = _gc_overhead_perc;
  size_t expand_bytes = 0;

  // If the heap is at less than half its maximum size, scale the threshold down,
  // to a limit of 1. Thus the smaller the heap is, the more likely it is to expand,
  // though the scaling code will likely keep the increase small.
  if (_g1->capacity() <= _g1->max_capacity() / 2) {
    threshold *= (double)_g1->capacity() / (double)(_g1->max_capacity() / 2);
    threshold = MAX2(threshold, 1.0);
  }

  // If the last GC time ratio is over the threshold, increment the count of
  // times it has been exceeded, and add this ratio to the sum of exceeded
  // ratios.
  if (last_gc_overhead > threshold) {
    _ratio_over_threshold_count++;
    _ratio_over_threshold_sum += last_gc_overhead;
  }

  // Check if we've had enough GC time ratio checks that were over the
  // threshold to trigger an expansion. We'll also expand if we've
  // reached the end of the history buffer and the average of all entries
  // is still over the threshold. This indicates a smaller number of GCs were
  // long enough to make the average exceed the threshold.
  bool filled_history_buffer = _pauses_since_start == NumPrevPausesForHeuristics;
  if ((_ratio_over_threshold_count == MinOverThresholdForGrowth) ||
      (filled_history_buffer && (recent_gc_overhead > threshold))) {
    size_t min_expand_bytes = HeapRegion::GrainBytes;
    size_t reserved_bytes = _g1->max_capacity();
    size_t committed_bytes = _g1->capacity();
    size_t uncommitted_bytes = reserved_bytes - committed_bytes;
    size_t expand_bytes_via_pct =
      uncommitted_bytes * G1ExpandByPercentOfAvailable / 100;
    double scale_factor = 1.0;

    // If the current size is less than 1/4 of the Initial heap size, expand
    // by half of the delta between the current and Initial sizes. IE, grow
    // back quickly.
    //
    // Otherwise, take the current size, or G1ExpandByPercentOfAvailable % of
    // the available expansion space, whichever is smaller, as the base
    // expansion size. Then possibly scale this size according to how much the
    // threshold has (on average) been exceeded by. If the delta is small
    // (less than the StartScaleDownAt value), scale the size down linearly, but
    // not by less than MinScaleDownFactor. If the delta is large (greater than
    // the StartScaleUpAt value), scale up, but adding no more than MaxScaleUpFactor
    // times the base size. The scaling will be linear in the range from
    // StartScaleUpAt to (StartScaleUpAt + ScaleUpRange). In other words,
    // ScaleUpRange sets the rate of scaling up.
    if (committed_bytes < InitialHeapSize / 4) {
      expand_bytes = (InitialHeapSize - committed_bytes) / 2;
    } else {
      double const MinScaleDownFactor = 0.2;
      double const MaxScaleUpFactor = 2;
      double const StartScaleDownAt = _gc_overhead_perc;
      double const StartScaleUpAt = _gc_overhead_perc * 1.5;
      double const ScaleUpRange = _gc_overhead_perc * 2.0;

      double ratio_delta;
      if (filled_history_buffer) {
        ratio_delta = recent_gc_overhead - threshold;
      } else {
        ratio_delta = (_ratio_over_threshold_sum/_ratio_over_threshold_count) - threshold;
      }

      expand_bytes = MIN2(expand_bytes_via_pct, committed_bytes);
      if (ratio_delta < StartScaleDownAt) {
        scale_factor = ratio_delta / StartScaleDownAt;
        scale_factor = MAX2(scale_factor, MinScaleDownFactor);
      } else if (ratio_delta > StartScaleUpAt) {
        scale_factor = 1 + ((ratio_delta - StartScaleUpAt) / ScaleUpRange);
        scale_factor = MIN2(scale_factor, MaxScaleUpFactor);
      }
    }

    log_debug(gc, ergo, heap)("Attempt heap expansion (recent GC overhead higher than threshold after GC) "
                              "recent GC overhead: %1.2f %% threshold: %1.2f %% uncommitted: " SIZE_FORMAT "B base expansion amount and scale: " SIZE_FORMAT "B (%1.2f%%)",
                              recent_gc_overhead, threshold, uncommitted_bytes, expand_bytes, scale_factor * 100);

    expand_bytes = static_cast<size_t>(expand_bytes * scale_factor);

    // Ensure the expansion size is at least the minimum growth amount
    // and at most the remaining uncommitted byte size.
    expand_bytes = MAX2(expand_bytes, min_expand_bytes);
    expand_bytes = MIN2(expand_bytes, uncommitted_bytes);

    clear_ratio_check_data();
  } else {
    // An expansion was not triggered. If we've started counting, increment
    // the number of checks we've made in the current window.  If we've
    // reached the end of the window without resizing, clear the counters to
    // start again the next time we see a ratio above the threshold.
    if (_ratio_over_threshold_count > 0) {
      _pauses_since_start++;
      if (_pauses_since_start > NumPrevPausesForHeuristics) {
        clear_ratio_check_data();
      }
    }
  }

  return expand_bytes;
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

bool G1CollectorPolicy::adaptive_young_list_length() const {
  return _young_gen_sizer->adaptive_young_list_length();
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

bool G1CollectorPolicy::force_initial_mark_if_outside_cycle(GCCause::Cause gc_cause) {
  // We actually check whether we are marking here and not if we are in a
  // reclamation phase. This means that we will schedule a concurrent mark
  // even while we are still in the process of reclaiming memory.
  bool during_cycle = _g1->concurrent_mark()->cmThread()->during_cycle();
  if (!during_cycle) {
    log_debug(gc, ergo)("Request concurrent cycle initiation (requested by GC cause). GC cause: %s", GCCause::to_string(gc_cause));
    collector_state()->set_initiate_conc_mark_if_possible(true);
    return true;
  } else {
    log_debug(gc, ergo)("Do not request concurrent cycle initiation (concurrent cycle already in progress). GC cause: %s", GCCause::to_string(gc_cause));
    return false;
  }
}

void G1CollectorPolicy::initiate_conc_mark() {
  collector_state()->set_during_initial_mark_pause(true);
  collector_state()->set_initiate_conc_mark_if_possible(false);
}

void G1CollectorPolicy::decide_on_conc_mark_initiation() {
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

    if (!about_to_start_mixed_phase() && collector_state()->gcs_are_young()) {
      // Initiate a new initial mark if there is no marking or reclamation going on.
      initiate_conc_mark();
      log_debug(gc, ergo)("Initiate concurrent cycle (concurrent cycle initiation requested)");
    } else if (_g1->is_user_requested_concurrent_full_gc(_g1->gc_cause())) {
      // Initiate a user requested initial mark. An initial mark must be young only
      // GC, so the collector state must be updated to reflect this.
      collector_state()->set_gcs_are_young(true);
      collector_state()->set_last_young_gc(false);

      abort_time_to_mixed_tracking();
      initiate_conc_mark();
      log_debug(gc, ergo)("Initiate concurrent cycle (user requested concurrent cycle)");
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
      log_debug(gc, ergo)("Do not initiate concurrent cycle (concurrent cycle already in progress)");
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

void G1CollectorPolicy::record_concurrent_mark_cleanup_end() {
  cset_chooser()->clear();

  WorkGang* workers = _g1->workers();
  uint n_workers = workers->active_workers();

  uint n_regions = _g1->num_regions();
  uint chunk_size = calculate_parallel_work_chunk_size(n_workers, n_regions);
  cset_chooser()->prepare_for_par_region_addition(n_workers, n_regions, chunk_size);
  ParKnownGarbageTask par_known_garbage_task(cset_chooser(), chunk_size, n_workers);
  workers->run_task(&par_known_garbage_task);

  cset_chooser()->sort_regions();

  double end_sec = os::elapsedTime();
  double elapsed_time_ms = (end_sec - _mark_cleanup_start_sec) * 1000.0;
  _concurrent_mark_cleanup_times_ms->add(elapsed_time_ms);
  _prev_collection_pause_end_ms += elapsed_time_ms;

  record_pause(Cleanup, _mark_cleanup_start_sec, end_sec);
}

double G1CollectorPolicy::reclaimable_bytes_perc(size_t reclaimable_bytes) const {
  // Returns the given amount of reclaimable bytes (that represents
  // the amount of reclaimable space still to be collected) as a
  // percentage of the current heap capacity.
  size_t capacity_bytes = _g1->capacity();
  return (double) reclaimable_bytes * 100.0 / (double) capacity_bytes;
}

void G1CollectorPolicy::maybe_start_marking() {
  if (need_to_start_conc_mark("end of GC")) {
    // Note: this might have already been set, if during the last
    // pause we decided to start a cycle but at the beginning of
    // this pause we decided to postpone it. That's OK.
    collector_state()->set_initiate_conc_mark_if_possible(true);
  }
}

G1CollectorPolicy::PauseKind G1CollectorPolicy::young_gc_pause_kind() const {
  assert(!collector_state()->full_collection(), "must be");
  if (collector_state()->during_initial_mark_pause()) {
    assert(collector_state()->last_gc_was_young(), "must be");
    assert(!collector_state()->last_young_gc(), "must be");
    return InitialMarkGC;
  } else if (collector_state()->last_young_gc()) {
    assert(!collector_state()->during_initial_mark_pause(), "must be");
    assert(collector_state()->last_gc_was_young(), "must be");
    return LastYoungGC;
  } else if (!collector_state()->last_gc_was_young()) {
    assert(!collector_state()->during_initial_mark_pause(), "must be");
    assert(!collector_state()->last_young_gc(), "must be");
    return MixedGC;
  } else {
    assert(collector_state()->last_gc_was_young(), "must be");
    assert(!collector_state()->during_initial_mark_pause(), "must be");
    assert(!collector_state()->last_young_gc(), "must be");
    return YoungOnlyGC;
  }
}

void G1CollectorPolicy::record_pause(PauseKind kind, double start, double end) {
  // Manage the MMU tracker. For some reason it ignores Full GCs.
  if (kind != FullGC) {
    _mmu_tracker->add_pause(start, end);
  }
  // Manage the mutator time tracking from initial mark to first mixed gc.
  switch (kind) {
    case FullGC:
      abort_time_to_mixed_tracking();
      break;
    case Cleanup:
    case Remark:
    case YoungOnlyGC:
    case LastYoungGC:
      _initial_mark_to_mixed.add_pause(end - start);
      break;
    case InitialMarkGC:
      _initial_mark_to_mixed.record_initial_mark_end(end);
      break;
    case MixedGC:
      _initial_mark_to_mixed.record_mixed_gc_start(start);
      break;
    default:
      ShouldNotReachHere();
  }
}

void G1CollectorPolicy::abort_time_to_mixed_tracking() {
  _initial_mark_to_mixed.reset();
}

bool G1CollectorPolicy::next_gc_should_be_mixed(const char* true_action_str,
                                                const char* false_action_str) const {
  if (cset_chooser()->is_empty()) {
    log_debug(gc, ergo)("%s (candidate old regions not available)", false_action_str);
    return false;
  }

  // Is the amount of uncollected reclaimable space above G1HeapWastePercent?
  size_t reclaimable_bytes = cset_chooser()->remaining_reclaimable_bytes();
  double reclaimable_perc = reclaimable_bytes_perc(reclaimable_bytes);
  double threshold = (double) G1HeapWastePercent;
  if (reclaimable_perc <= threshold) {
    log_debug(gc, ergo)("%s (reclaimable percentage not over threshold). candidate old regions: %u reclaimable: " SIZE_FORMAT " (%1.2f) threshold: " UINTX_FORMAT,
                        false_action_str, cset_chooser()->remaining_regions(), reclaimable_bytes, reclaimable_perc, G1HeapWastePercent);
    return false;
  }
  log_debug(gc, ergo)("%s (candidate old regions available). candidate old regions: %u reclaimable: " SIZE_FORMAT " (%1.2f) threshold: " UINTX_FORMAT,
                      true_action_str, cset_chooser()->remaining_regions(), reclaimable_bytes, reclaimable_perc, G1HeapWastePercent);
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

  const size_t region_num = (size_t) cset_chooser()->length();
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

void G1CollectorPolicy::finalize_collection_set(double target_pause_time_ms) {
  double time_remaining_ms = _collection_set->finalize_young_part(target_pause_time_ms);
  _collection_set->finalize_old_part(time_remaining_ms);
}

