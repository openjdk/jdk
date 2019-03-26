/*
 * Copyright (c) 2001, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1Analytics.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSet.hpp"
#include "gc/g1/g1CollectionSetCandidates.hpp"
#include "gc/g1/g1ConcurrentMark.hpp"
#include "gc/g1/g1ConcurrentMarkThread.inline.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/g1CollectionSetChooser.hpp"
#include "gc/g1/g1HeterogeneousHeapPolicy.hpp"
#include "gc/g1/g1HotCardCache.hpp"
#include "gc/g1/g1IHOPControl.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1SurvivorRegions.hpp"
#include "gc/g1/g1YoungGenSizer.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/shared/gcPolicyCounters.hpp"
#include "logging/logStream.hpp"
#include "runtime/arguments.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/debug.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/pair.hpp"

G1Policy::G1Policy(G1CollectorPolicy* policy, STWGCTimer* gc_timer) :
  _predictor(G1ConfidencePercent / 100.0),
  _analytics(new G1Analytics(&_predictor)),
  _remset_tracker(),
  _mmu_tracker(new G1MMUTrackerQueue(GCPauseIntervalMillis / 1000.0, MaxGCPauseMillis / 1000.0)),
  _ihop_control(create_ihop_control(&_predictor)),
  _policy_counters(new GCPolicyCounters("GarbageFirst", 1, 2)),
  _full_collection_start_sec(0.0),
  _collection_pause_end_millis(os::javaTimeNanos() / NANOSECS_PER_MILLISEC),
  _young_list_target_length(0),
  _young_list_fixed_length(0),
  _young_list_max_length(0),
  _short_lived_surv_rate_group(new SurvRateGroup()),
  _survivor_surv_rate_group(new SurvRateGroup()),
  _reserve_factor((double) G1ReservePercent / 100.0),
  _reserve_regions(0),
  _young_gen_sizer(G1YoungGenSizer::create_gen_sizer(policy)),
  _free_regions_at_end_of_collection(0),
  _max_rs_lengths(0),
  _rs_lengths_prediction(0),
  _pending_cards(0),
  _bytes_allocated_in_old_since_last_gc(0),
  _initial_mark_to_mixed(),
  _collection_set(NULL),
  _bytes_copied_during_gc(0),
  _g1h(NULL),
  _phase_times(new G1GCPhaseTimes(gc_timer, ParallelGCThreads)),
  _mark_remark_start_sec(0),
  _mark_cleanup_start_sec(0),
  _tenuring_threshold(MaxTenuringThreshold),
  _max_survivor_regions(0),
  _survivors_age_table(true)
{
}

G1Policy::~G1Policy() {
  delete _ihop_control;
  delete _young_gen_sizer;
}

G1Policy* G1Policy::create_policy(G1CollectorPolicy* policy, STWGCTimer* gc_timer_stw) {
  if (policy->is_heterogeneous_heap()) {
    return new G1HeterogeneousHeapPolicy(policy, gc_timer_stw);
  } else {
    return new G1Policy(policy, gc_timer_stw);
  }
}

G1CollectorState* G1Policy::collector_state() const { return _g1h->collector_state(); }

void G1Policy::init(G1CollectedHeap* g1h, G1CollectionSet* collection_set) {
  _g1h = g1h;
  _collection_set = collection_set;

  assert(Heap_lock->owned_by_self(), "Locking discipline.");

  if (!adaptive_young_list_length()) {
    _young_list_fixed_length = _young_gen_sizer->min_desired_young_length();
  }
  _young_gen_sizer->adjust_max_new_size(_g1h->max_expandable_regions());

  _free_regions_at_end_of_collection = _g1h->num_free_regions();

  update_young_list_max_and_target_length();
  // We may immediately start allocating regions and placing them on the
  // collection set list. Initialize the per-collection set info
  _collection_set->start_incremental_building();
}

void G1Policy::note_gc_start() {
  phase_times()->note_gc_start();
}

class G1YoungLengthPredictor {
  const bool _during_cm;
  const double _base_time_ms;
  const double _base_free_regions;
  const double _target_pause_time_ms;
  const G1Policy* const _policy;

 public:
  G1YoungLengthPredictor(bool during_cm,
                         double base_time_ms,
                         double base_free_regions,
                         double target_pause_time_ms,
                         const G1Policy* policy) :
    _during_cm(during_cm),
    _base_time_ms(base_time_ms),
    _base_free_regions(base_free_regions),
    _target_pause_time_ms(target_pause_time_ms),
    _policy(policy) {}

  bool will_fit(uint young_length) const {
    if (young_length >= _base_free_regions) {
      // end condition 1: not enough space for the young regions
      return false;
    }

    const double accum_surv_rate = _policy->accum_yg_surv_rate_pred((int) young_length - 1);
    const size_t bytes_to_copy =
                 (size_t) (accum_surv_rate * (double) HeapRegion::GrainBytes);
    const double copy_time_ms =
      _policy->analytics()->predict_object_copy_time_ms(bytes_to_copy, _during_cm);
    const double young_other_time_ms = _policy->analytics()->predict_young_other_time_ms(young_length);
    const double pause_time_ms = _base_time_ms + copy_time_ms + young_other_time_ms;
    if (pause_time_ms > _target_pause_time_ms) {
      // end condition 2: prediction is over the target pause time
      return false;
    }

    const size_t free_bytes = (_base_free_regions - young_length) * HeapRegion::GrainBytes;

    // When copying, we will likely need more bytes free than is live in the region.
    // Add some safety margin to factor in the confidence of our guess, and the
    // natural expected waste.
    // (100.0 / G1ConfidencePercent) is a scale factor that expresses the uncertainty
    // of the calculation: the lower the confidence, the more headroom.
    // (100 + TargetPLABWastePct) represents the increase in expected bytes during
    // copying due to anticipated waste in the PLABs.
    const double safety_factor = (100.0 / G1ConfidencePercent) * (100 + TargetPLABWastePct) / 100.0;
    const size_t expected_bytes_to_copy = (size_t)(safety_factor * bytes_to_copy);

    if (expected_bytes_to_copy > free_bytes) {
      // end condition 3: out-of-space
      return false;
    }

    // success!
    return true;
  }
};

void G1Policy::record_new_heap_size(uint new_number_of_regions) {
  // re-calculate the necessary reserve
  double reserve_regions_d = (double) new_number_of_regions * _reserve_factor;
  // We use ceiling so that if reserve_regions_d is > 0.0 (but
  // smaller than 1.0) we'll get 1.
  _reserve_regions = (uint) ceil(reserve_regions_d);

  _young_gen_sizer->heap_size_changed(new_number_of_regions);

  _ihop_control->update_target_occupancy(new_number_of_regions * HeapRegion::GrainBytes);
}

uint G1Policy::calculate_young_list_desired_min_length(uint base_min_length) const {
  uint desired_min_length = 0;
  if (adaptive_young_list_length()) {
    if (_analytics->num_alloc_rate_ms() > 3) {
      double now_sec = os::elapsedTime();
      double when_ms = _mmu_tracker->when_max_gc_sec(now_sec) * 1000.0;
      double alloc_rate_ms = _analytics->predict_alloc_rate_ms();
      desired_min_length = (uint) ceil(alloc_rate_ms * when_ms);
    } else {
      // otherwise we don't have enough info to make the prediction
    }
  }
  desired_min_length += base_min_length;
  // make sure we don't go below any user-defined minimum bound
  return MAX2(_young_gen_sizer->min_desired_young_length(), desired_min_length);
}

uint G1Policy::calculate_young_list_desired_max_length() const {
  // Here, we might want to also take into account any additional
  // constraints (i.e., user-defined minimum bound). Currently, we
  // effectively don't set this bound.
  return _young_gen_sizer->max_desired_young_length();
}

uint G1Policy::update_young_list_max_and_target_length() {
  return update_young_list_max_and_target_length(_analytics->predict_rs_lengths());
}

uint G1Policy::update_young_list_max_and_target_length(size_t rs_lengths) {
  uint unbounded_target_length = update_young_list_target_length(rs_lengths);
  update_max_gc_locker_expansion();
  return unbounded_target_length;
}

uint G1Policy::update_young_list_target_length(size_t rs_lengths) {
  YoungTargetLengths young_lengths = young_list_target_lengths(rs_lengths);
  _young_list_target_length = young_lengths.first;

  return young_lengths.second;
}

G1Policy::YoungTargetLengths G1Policy::young_list_target_lengths(size_t rs_lengths) const {
  YoungTargetLengths result;

  // Calculate the absolute and desired min bounds first.

  // This is how many young regions we already have (currently: the survivors).
  const uint base_min_length = _g1h->survivor_regions_count();
  uint desired_min_length = calculate_young_list_desired_min_length(base_min_length);
  // This is the absolute minimum young length. Ensure that we
  // will at least have one eden region available for allocation.
  uint absolute_min_length = base_min_length + MAX2(_g1h->eden_regions_count(), (uint)1);
  // If we shrank the young list target it should not shrink below the current size.
  desired_min_length = MAX2(desired_min_length, absolute_min_length);
  // Calculate the absolute and desired max bounds.

  uint desired_max_length = calculate_young_list_desired_max_length();

  uint young_list_target_length = 0;
  if (adaptive_young_list_length()) {
    if (collector_state()->in_young_only_phase()) {
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

  assert(young_list_target_length > base_min_length,
         "we should be able to allocate at least one eden region");
  assert(young_list_target_length >= absolute_min_length, "post-condition");

  result.first = young_list_target_length;
  return result;
}

uint
G1Policy::calculate_young_list_target_length(size_t rs_lengths,
                                                    uint base_min_length,
                                                    uint desired_min_length,
                                                    uint desired_max_length) const {
  assert(adaptive_young_list_length(), "pre-condition");
  assert(collector_state()->in_young_only_phase(), "only call this for young GCs");

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

  const double target_pause_time_ms = _mmu_tracker->max_gc_time() * 1000.0;
  const double survivor_regions_evac_time = predict_survivor_regions_evac_time();
  const size_t pending_cards = _analytics->predict_pending_cards();
  const size_t adj_rs_lengths = rs_lengths + _analytics->predict_rs_length_diff();
  const size_t scanned_cards = _analytics->predict_card_num(adj_rs_lengths, true /* for_young_gc */);
  const double base_time_ms =
    predict_base_elapsed_time_ms(pending_cards, scanned_cards) +
    survivor_regions_evac_time;
  const uint available_free_regions = _free_regions_at_end_of_collection;
  const uint base_free_regions =
    available_free_regions > _reserve_regions ? available_free_regions - _reserve_regions : 0;

  // Here, we will make sure that the shortest young length that
  // makes sense fits within the target pause time.

  G1YoungLengthPredictor p(collector_state()->mark_or_rebuild_in_progress(),
                           base_time_ms,
                           base_free_regions,
                           target_pause_time_ms,
                           this);
  if (p.will_fit(min_young_length)) {
    // The shortest young length will fit into the target pause time;
    // we'll now check whether the absolute maximum number of young
    // regions will fit in the target pause time. If not, we'll do
    // a binary search between min_young_length and max_young_length.
    if (p.will_fit(max_young_length)) {
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
        if (p.will_fit(young_length)) {
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
      assert(p.will_fit(min_young_length),
             "min_young_length, the result of the binary search, should "
             "fit into the pause target");
      assert(!p.will_fit(min_young_length + 1),
             "min_young_length, the result of the binary search, should be "
             "optimal, so no larger length should fit into the pause target");
    }
  } else {
    // Even the minimum length doesn't fit into the pause time
    // target, return it as the result nevertheless.
  }
  return base_min_length + min_young_length;
}

double G1Policy::predict_survivor_regions_evac_time() const {
  double survivor_regions_evac_time = 0.0;
  const GrowableArray<HeapRegion*>* survivor_regions = _g1h->survivor()->regions();

  for (GrowableArrayIterator<HeapRegion*> it = survivor_regions->begin();
       it != survivor_regions->end();
       ++it) {
    survivor_regions_evac_time += predict_region_elapsed_time_ms(*it, collector_state()->in_young_only_phase());
  }
  return survivor_regions_evac_time;
}

void G1Policy::revise_young_list_target_length_if_necessary(size_t rs_lengths) {
  guarantee( adaptive_young_list_length(), "should not call this otherwise" );

  if (rs_lengths > _rs_lengths_prediction) {
    // add 10% to avoid having to recalculate often
    size_t rs_lengths_prediction = rs_lengths * 1100 / 1000;
    update_rs_lengths_prediction(rs_lengths_prediction);

    update_young_list_max_and_target_length(rs_lengths_prediction);
  }
}

void G1Policy::update_rs_lengths_prediction() {
  update_rs_lengths_prediction(_analytics->predict_rs_lengths());
}

void G1Policy::update_rs_lengths_prediction(size_t prediction) {
  if (collector_state()->in_young_only_phase() && adaptive_young_list_length()) {
    _rs_lengths_prediction = prediction;
  }
}

void G1Policy::record_full_collection_start() {
  _full_collection_start_sec = os::elapsedTime();
  // Release the future to-space so that it is available for compaction into.
  collector_state()->set_in_young_only_phase(false);
  collector_state()->set_in_full_gc(true);
  _collection_set->clear_candidates();
}

void G1Policy::record_full_collection_end() {
  // Consider this like a collection pause for the purposes of allocation
  // since last pause.
  double end_sec = os::elapsedTime();
  double full_gc_time_sec = end_sec - _full_collection_start_sec;
  double full_gc_time_ms = full_gc_time_sec * 1000.0;

  _analytics->update_recent_gc_times(end_sec, full_gc_time_ms);

  collector_state()->set_in_full_gc(false);

  // "Nuke" the heuristics that control the young/mixed GC
  // transitions and make sure we start with young GCs after the Full GC.
  collector_state()->set_in_young_only_phase(true);
  collector_state()->set_in_young_gc_before_mixed(false);
  collector_state()->set_initiate_conc_mark_if_possible(need_to_start_conc_mark("end of Full GC", 0));
  collector_state()->set_in_initial_mark_gc(false);
  collector_state()->set_mark_or_rebuild_in_progress(false);
  collector_state()->set_clearing_next_bitmap(false);

  _short_lived_surv_rate_group->start_adding_regions();
  // also call this on any additional surv rate groups

  _free_regions_at_end_of_collection = _g1h->num_free_regions();
  // Reset survivors SurvRateGroup.
  _survivor_surv_rate_group->reset();
  update_young_list_max_and_target_length();
  update_rs_lengths_prediction();

  _bytes_allocated_in_old_since_last_gc = 0;

  record_pause(FullGC, _full_collection_start_sec, end_sec);
}

void G1Policy::record_collection_pause_start(double start_time_sec) {
  // We only need to do this here as the policy will only be applied
  // to the GC we're about to start. so, no point is calculating this
  // every time we calculate / recalculate the target young length.
  update_survivors_policy();

  assert(max_survivor_regions() + _g1h->num_used_regions() <= _g1h->max_regions(),
         "Maximum survivor regions %u plus used regions %u exceeds max regions %u",
         max_survivor_regions(), _g1h->num_used_regions(), _g1h->max_regions());

  assert(_g1h->used() == _g1h->recalculate_used(),
         "sanity, used: " SIZE_FORMAT " recalculate_used: " SIZE_FORMAT,
         _g1h->used(), _g1h->recalculate_used());

  phase_times()->record_cur_collection_start_sec(start_time_sec);
  _pending_cards = _g1h->pending_card_num();

  _collection_set->reset_bytes_used_before();
  _bytes_copied_during_gc = 0;

  // do that for any other surv rate groups
  _short_lived_surv_rate_group->stop_adding_regions();
  _survivors_age_table.clear();

  assert(_g1h->collection_set()->verify_young_ages(), "region age verification failed");
}

void G1Policy::record_concurrent_mark_init_end(double mark_init_elapsed_time_ms) {
  assert(!collector_state()->initiate_conc_mark_if_possible(), "we should have cleared it by now");
  collector_state()->set_in_initial_mark_gc(false);
}

void G1Policy::record_concurrent_mark_remark_start() {
  _mark_remark_start_sec = os::elapsedTime();
}

void G1Policy::record_concurrent_mark_remark_end() {
  double end_time_sec = os::elapsedTime();
  double elapsed_time_ms = (end_time_sec - _mark_remark_start_sec)*1000.0;
  _analytics->report_concurrent_mark_remark_times_ms(elapsed_time_ms);
  _analytics->append_prev_collection_pause_end_ms(elapsed_time_ms);

  record_pause(Remark, _mark_remark_start_sec, end_time_sec);
}

void G1Policy::record_concurrent_mark_cleanup_start() {
  _mark_cleanup_start_sec = os::elapsedTime();
}

double G1Policy::average_time_ms(G1GCPhaseTimes::GCParPhases phase) const {
  return phase_times()->average_time_ms(phase);
}

double G1Policy::young_other_time_ms() const {
  return phase_times()->young_cset_choice_time_ms() +
         phase_times()->average_time_ms(G1GCPhaseTimes::YoungFreeCSet);
}

double G1Policy::non_young_other_time_ms() const {
  return phase_times()->non_young_cset_choice_time_ms() +
         phase_times()->average_time_ms(G1GCPhaseTimes::NonYoungFreeCSet);
}

double G1Policy::other_time_ms(double pause_time_ms) const {
  return pause_time_ms - phase_times()->cur_collection_par_time_ms();
}

double G1Policy::constant_other_time_ms(double pause_time_ms) const {
  return other_time_ms(pause_time_ms) - phase_times()->total_free_cset_time_ms();
}

bool G1Policy::about_to_start_mixed_phase() const {
  return _g1h->concurrent_mark()->cm_thread()->during_cycle() || collector_state()->in_young_gc_before_mixed();
}

bool G1Policy::need_to_start_conc_mark(const char* source, size_t alloc_word_size) {
  if (about_to_start_mixed_phase()) {
    return false;
  }

  size_t marking_initiating_used_threshold = _ihop_control->get_conc_mark_start_threshold();

  size_t cur_used_bytes = _g1h->non_young_capacity_bytes();
  size_t alloc_byte_size = alloc_word_size * HeapWordSize;
  size_t marking_request_bytes = cur_used_bytes + alloc_byte_size;

  bool result = false;
  if (marking_request_bytes > marking_initiating_used_threshold) {
    result = collector_state()->in_young_only_phase() && !collector_state()->in_young_gc_before_mixed();
    log_debug(gc, ergo, ihop)("%s occupancy: " SIZE_FORMAT "B allocation request: " SIZE_FORMAT "B threshold: " SIZE_FORMAT "B (%1.2f) source: %s",
                              result ? "Request concurrent cycle initiation (occupancy higher than threshold)" : "Do not request concurrent cycle initiation (still doing mixed collections)",
                              cur_used_bytes, alloc_byte_size, marking_initiating_used_threshold, (double) marking_initiating_used_threshold / _g1h->capacity() * 100, source);
  }

  return result;
}

// Anything below that is considered to be zero
#define MIN_TIMER_GRANULARITY 0.0000001

void G1Policy::record_collection_pause_end(double pause_time_ms, size_t cards_scanned, size_t heap_used_bytes_before_gc) {
  double end_time_sec = os::elapsedTime();

  size_t cur_used_bytes = _g1h->used();
  assert(cur_used_bytes == _g1h->recalculate_used(), "It should!");
  bool this_pause_included_initial_mark = false;
  bool this_pause_was_young_only = collector_state()->in_young_only_phase();

  bool update_stats = !_g1h->evacuation_failed();

  record_pause(young_gc_pause_kind(), end_time_sec - pause_time_ms / 1000.0, end_time_sec);

  _collection_pause_end_millis = os::javaTimeNanos() / NANOSECS_PER_MILLISEC;

  this_pause_included_initial_mark = collector_state()->in_initial_mark_gc();
  if (this_pause_included_initial_mark) {
    record_concurrent_mark_init_end(0.0);
  } else {
    maybe_start_marking();
  }

  double app_time_ms = (phase_times()->cur_collection_start_sec() * 1000.0 - _analytics->prev_collection_pause_end_ms());
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
    _analytics->report_alloc_rate_ms(alloc_rate_ms);

    double interval_ms =
      (end_time_sec - _analytics->last_known_gc_end_time_sec()) * 1000.0;
    _analytics->update_recent_gc_times(end_time_sec, pause_time_ms);
    _analytics->compute_pause_time_ratio(interval_ms, pause_time_ms);
  }

  if (collector_state()->in_young_gc_before_mixed()) {
    assert(!this_pause_included_initial_mark, "The young GC before mixed is not allowed to be an initial mark GC");
    // This has been the young GC before we start doing mixed GCs. We already
    // decided to start mixed GCs much earlier, so there is nothing to do except
    // advancing the state.
    collector_state()->set_in_young_only_phase(false);
    collector_state()->set_in_young_gc_before_mixed(false);
  } else if (!this_pause_was_young_only) {
    // This is a mixed GC. Here we decide whether to continue doing more
    // mixed GCs or not.
    if (!next_gc_should_be_mixed("continue mixed GCs",
                                 "do not continue mixed GCs")) {
      collector_state()->set_in_young_only_phase(true);

      clear_collection_set_candidates();
      maybe_start_marking();
    }
  }

  _short_lived_surv_rate_group->start_adding_regions();
  // Do that for any other surv rate groups

  double scan_hcc_time_ms = G1HotCardCache::default_use_cache() ? average_time_ms(G1GCPhaseTimes::ScanHCC) : 0.0;

  if (update_stats) {
    double cost_per_card_ms = 0.0;
    if (_pending_cards > 0) {
      cost_per_card_ms = (average_time_ms(G1GCPhaseTimes::UpdateRS)) / (double) _pending_cards;
      _analytics->report_cost_per_card_ms(cost_per_card_ms);
    }
    _analytics->report_cost_scan_hcc(scan_hcc_time_ms);

    double cost_per_entry_ms = 0.0;
    if (cards_scanned > 10) {
      cost_per_entry_ms = average_time_ms(G1GCPhaseTimes::ScanRS) / (double) cards_scanned;
      _analytics->report_cost_per_entry_ms(cost_per_entry_ms, this_pause_was_young_only);
    }

    if (_max_rs_lengths > 0) {
      double cards_per_entry_ratio =
        (double) cards_scanned / (double) _max_rs_lengths;
      _analytics->report_cards_per_entry_ratio(cards_per_entry_ratio, this_pause_was_young_only);
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
    _analytics->report_rs_length_diff((double) rs_length_diff);

    size_t freed_bytes = heap_used_bytes_before_gc - cur_used_bytes;
    size_t copied_bytes = _collection_set->bytes_used_before() - freed_bytes;
    double cost_per_byte_ms = 0.0;

    if (copied_bytes > 0) {
      cost_per_byte_ms = average_time_ms(G1GCPhaseTimes::ObjCopy) / (double) copied_bytes;
      _analytics->report_cost_per_byte_ms(cost_per_byte_ms, collector_state()->mark_or_rebuild_in_progress());
    }

    if (_collection_set->young_region_length() > 0) {
      _analytics->report_young_other_cost_per_region_ms(young_other_time_ms() /
                                                        _collection_set->young_region_length());
    }

    if (_collection_set->old_region_length() > 0) {
      _analytics->report_non_young_other_cost_per_region_ms(non_young_other_time_ms() /
                                                            _collection_set->old_region_length());
    }

    _analytics->report_constant_other_time_ms(constant_other_time_ms(pause_time_ms));

    // Do not update RS lengths and the number of pending cards with information from mixed gc:
    // these are is wildly different to during young only gc and mess up young gen sizing right
    // after the mixed gc phase.
    // During mixed gc we do not use them for young gen sizing.
    if (this_pause_was_young_only) {
      _analytics->report_pending_cards((double) _pending_cards);
      _analytics->report_rs_lengths((double) _max_rs_lengths);
    }
  }

  assert(!(this_pause_included_initial_mark && collector_state()->mark_or_rebuild_in_progress()),
         "If the last pause has been an initial mark, we should not have been in the marking window");
  if (this_pause_included_initial_mark) {
    collector_state()->set_mark_or_rebuild_in_progress(true);
  }

  _free_regions_at_end_of_collection = _g1h->num_free_regions();

  update_rs_lengths_prediction();

  // Do not update dynamic IHOP due to G1 periodic collection as it is highly likely
  // that in this case we are not running in a "normal" operating mode.
  if (_g1h->gc_cause() != GCCause::_g1_periodic_collection) {
    // IHOP control wants to know the expected young gen length if it were not
    // restrained by the heap reserve. Using the actual length would make the
    // prediction too small and the limit the young gen every time we get to the
    // predicted target occupancy.
    size_t last_unrestrained_young_length = update_young_list_max_and_target_length();

    update_ihop_prediction(app_time_ms / 1000.0,
                           _bytes_allocated_in_old_since_last_gc,
                           last_unrestrained_young_length * HeapRegion::GrainBytes,
                           this_pause_was_young_only);
    _bytes_allocated_in_old_since_last_gc = 0;

    _ihop_control->send_trace_event(_g1h->gc_tracer_stw());
  } else {
    // Any garbage collection triggered as periodic collection resets the time-to-mixed
    // measurement. Periodic collection typically means that the application is "inactive", i.e.
    // the marking threads may have received an uncharacterisic amount of cpu time
    // for completing the marking, i.e. are faster than expected.
    // This skews the predicted marking length towards smaller values which might cause
    // the mark start being too late.
    _initial_mark_to_mixed.reset();
  }

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
  _g1h->concurrent_refine()->adjust(average_time_ms(G1GCPhaseTimes::UpdateRS),
                                    phase_times()->sum_thread_work_items(G1GCPhaseTimes::UpdateRS),
                                    update_rs_time_goal_ms);
}

G1IHOPControl* G1Policy::create_ihop_control(const G1Predictions* predictor){
  if (G1UseAdaptiveIHOP) {
    return new G1AdaptiveIHOPControl(InitiatingHeapOccupancyPercent,
                                     predictor,
                                     G1ReservePercent,
                                     G1HeapWastePercent);
  } else {
    return new G1StaticIHOPControl(InitiatingHeapOccupancyPercent);
  }
}

void G1Policy::update_ihop_prediction(double mutator_time_s,
                                      size_t mutator_alloc_bytes,
                                      size_t young_gen_size,
                                      bool this_gc_was_young_only) {
  // Always try to update IHOP prediction. Even evacuation failures give information
  // about e.g. whether to start IHOP earlier next time.

  // Avoid using really small application times that might create samples with
  // very high or very low values. They may be caused by e.g. back-to-back gcs.
  double const min_valid_time = 1e-6;

  bool report = false;

  double marking_to_mixed_time = -1.0;
  if (!this_gc_was_young_only && _initial_mark_to_mixed.has_result()) {
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
  if (this_gc_was_young_only && mutator_time_s > min_valid_time) {
    _ihop_control->update_allocation_info(mutator_time_s, mutator_alloc_bytes, young_gen_size);
    report = true;
  }

  if (report) {
    report_ihop_statistics();
  }
}

void G1Policy::report_ihop_statistics() {
  _ihop_control->print();
}

void G1Policy::print_phases() {
  phase_times()->print();
}

double G1Policy::predict_yg_surv_rate(int age, SurvRateGroup* surv_rate_group) const {
  TruncatedSeq* seq = surv_rate_group->get_seq(age);
  guarantee(seq->num() > 0, "There should be some young gen survivor samples available. Tried to access with age %d", age);
  double pred = _predictor.get_new_prediction(seq);
  if (pred > 1.0) {
    pred = 1.0;
  }
  return pred;
}

double G1Policy::accum_yg_surv_rate_pred(int age) const {
  return _short_lived_surv_rate_group->accum_surv_rate_pred(age);
}

double G1Policy::predict_base_elapsed_time_ms(size_t pending_cards,
                                              size_t scanned_cards) const {
  return
    _analytics->predict_rs_update_time_ms(pending_cards) +
    _analytics->predict_rs_scan_time_ms(scanned_cards, collector_state()->in_young_only_phase()) +
    _analytics->predict_constant_other_time_ms();
}

double G1Policy::predict_base_elapsed_time_ms(size_t pending_cards) const {
  size_t rs_length = _analytics->predict_rs_lengths() + _analytics->predict_rs_length_diff();
  size_t card_num = _analytics->predict_card_num(rs_length, collector_state()->in_young_only_phase());
  return predict_base_elapsed_time_ms(pending_cards, card_num);
}

size_t G1Policy::predict_bytes_to_copy(HeapRegion* hr) const {
  size_t bytes_to_copy;
  if (!hr->is_young()) {
    bytes_to_copy = hr->max_live_bytes();
  } else {
    assert(hr->age_in_surv_rate_group() != -1, "invariant");
    int age = hr->age_in_surv_rate_group();
    double yg_surv_rate = predict_yg_surv_rate(age, hr->surv_rate_group());
    bytes_to_copy = (size_t) (hr->used() * yg_surv_rate);
  }
  return bytes_to_copy;
}

double G1Policy::predict_region_elapsed_time_ms(HeapRegion* hr,
                                                bool for_young_gc) const {
  size_t rs_length = hr->rem_set()->occupied();
  // Predicting the number of cards is based on which type of GC
  // we're predicting for.
  size_t card_num = _analytics->predict_card_num(rs_length, for_young_gc);
  size_t bytes_to_copy = predict_bytes_to_copy(hr);

  double region_elapsed_time_ms =
    _analytics->predict_rs_scan_time_ms(card_num, collector_state()->in_young_only_phase()) +
    _analytics->predict_object_copy_time_ms(bytes_to_copy, collector_state()->mark_or_rebuild_in_progress());

  // The prediction of the "other" time for this region is based
  // upon the region type and NOT the GC type.
  if (hr->is_young()) {
    region_elapsed_time_ms += _analytics->predict_young_other_time_ms(1);
  } else {
    region_elapsed_time_ms += _analytics->predict_non_young_other_time_ms(1);
  }
  return region_elapsed_time_ms;
}

bool G1Policy::should_allocate_mutator_region() const {
  uint young_list_length = _g1h->young_regions_count();
  uint young_list_target_length = _young_list_target_length;
  return young_list_length < young_list_target_length;
}

bool G1Policy::can_expand_young_list() const {
  uint young_list_length = _g1h->young_regions_count();
  uint young_list_max_length = _young_list_max_length;
  return young_list_length < young_list_max_length;
}

bool G1Policy::adaptive_young_list_length() const {
  return _young_gen_sizer->adaptive_young_list_length();
}

size_t G1Policy::desired_survivor_size(uint max_regions) const {
  size_t const survivor_capacity = HeapRegion::GrainWords * max_regions;
  return (size_t)((((double)survivor_capacity) * TargetSurvivorRatio) / 100);
}

void G1Policy::print_age_table() {
  _survivors_age_table.print_age_table(_tenuring_threshold);
}

void G1Policy::update_max_gc_locker_expansion() {
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
void G1Policy::update_survivors_policy() {
  double max_survivor_regions_d =
                 (double) _young_list_target_length / (double) SurvivorRatio;

  // Calculate desired survivor size based on desired max survivor regions (unconstrained
  // by remaining heap). Otherwise we may cause undesired promotions as we are
  // already getting close to end of the heap, impacting performance even more.
  uint const desired_max_survivor_regions = ceil(max_survivor_regions_d);
  size_t const survivor_size = desired_survivor_size(desired_max_survivor_regions);

  _tenuring_threshold = _survivors_age_table.compute_tenuring_threshold(survivor_size);
  if (UsePerfData) {
    _policy_counters->tenuring_threshold()->set_value(_tenuring_threshold);
    _policy_counters->desired_survivor_size()->set_value(survivor_size * oopSize);
  }
  // The real maximum survivor size is bounded by the number of regions that can
  // be allocated into.
  _max_survivor_regions = MIN2(desired_max_survivor_regions,
                               _g1h->num_free_or_available_regions());
}

bool G1Policy::force_initial_mark_if_outside_cycle(GCCause::Cause gc_cause) {
  // We actually check whether we are marking here and not if we are in a
  // reclamation phase. This means that we will schedule a concurrent mark
  // even while we are still in the process of reclaiming memory.
  bool during_cycle = _g1h->concurrent_mark()->cm_thread()->during_cycle();
  if (!during_cycle) {
    log_debug(gc, ergo)("Request concurrent cycle initiation (requested by GC cause). GC cause: %s", GCCause::to_string(gc_cause));
    collector_state()->set_initiate_conc_mark_if_possible(true);
    return true;
  } else {
    log_debug(gc, ergo)("Do not request concurrent cycle initiation (concurrent cycle already in progress). GC cause: %s", GCCause::to_string(gc_cause));
    return false;
  }
}

void G1Policy::initiate_conc_mark() {
  collector_state()->set_in_initial_mark_gc(true);
  collector_state()->set_initiate_conc_mark_if_possible(false);
}

void G1Policy::decide_on_conc_mark_initiation() {
  // We are about to decide on whether this pause will be an
  // initial-mark pause.

  // First, collector_state()->in_initial_mark_gc() should not be already set. We
  // will set it here if we have to. However, it should be cleared by
  // the end of the pause (it's only set for the duration of an
  // initial-mark pause).
  assert(!collector_state()->in_initial_mark_gc(), "pre-condition");

  if (collector_state()->initiate_conc_mark_if_possible()) {
    // We had noticed on a previous pause that the heap occupancy has
    // gone over the initiating threshold and we should start a
    // concurrent marking cycle. So we might initiate one.

    if (!about_to_start_mixed_phase() && collector_state()->in_young_only_phase()) {
      // Initiate a new initial mark if there is no marking or reclamation going on.
      initiate_conc_mark();
      log_debug(gc, ergo)("Initiate concurrent cycle (concurrent cycle initiation requested)");
    } else if (_g1h->is_user_requested_concurrent_full_gc(_g1h->gc_cause())) {
      // Initiate a user requested initial mark. An initial mark must be young only
      // GC, so the collector state must be updated to reflect this.
      collector_state()->set_in_young_only_phase(true);
      collector_state()->set_in_young_gc_before_mixed(false);

      // We might have ended up coming here about to start a mixed phase with a collection set
      // active. The following remark might change the change the "evacuation efficiency" of
      // the regions in this set, leading to failing asserts later.
      // Since the concurrent cycle will recreate the collection set anyway, simply drop it here.
      clear_collection_set_candidates();
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

void G1Policy::record_concurrent_mark_cleanup_end() {
  G1CollectionSetCandidates* candidates = G1CollectionSetChooser::build(_g1h->workers(), _g1h->num_regions());
  _collection_set->set_candidates(candidates);

  bool mixed_gc_pending = next_gc_should_be_mixed("request mixed gcs", "request young-only gcs");
  if (!mixed_gc_pending) {
    clear_collection_set_candidates();
    abort_time_to_mixed_tracking();
  }
  collector_state()->set_in_young_gc_before_mixed(mixed_gc_pending);
  collector_state()->set_mark_or_rebuild_in_progress(false);

  double end_sec = os::elapsedTime();
  double elapsed_time_ms = (end_sec - _mark_cleanup_start_sec) * 1000.0;
  _analytics->report_concurrent_mark_cleanup_times_ms(elapsed_time_ms);
  _analytics->append_prev_collection_pause_end_ms(elapsed_time_ms);

  record_pause(Cleanup, _mark_cleanup_start_sec, end_sec);
}

double G1Policy::reclaimable_bytes_percent(size_t reclaimable_bytes) const {
  return percent_of(reclaimable_bytes, _g1h->capacity());
}

class G1ClearCollectionSetCandidateRemSets : public HeapRegionClosure {
  virtual bool do_heap_region(HeapRegion* r) {
    r->rem_set()->clear_locked(true /* only_cardset */);
    return false;
  }
};

void G1Policy::clear_collection_set_candidates() {
  // Clear remembered sets of remaining candidate regions and the actual candidate
  // set.
  G1ClearCollectionSetCandidateRemSets cl;
  _collection_set->candidates()->iterate(&cl);
  _collection_set->clear_candidates();
}

void G1Policy::maybe_start_marking() {
  if (need_to_start_conc_mark("end of GC")) {
    // Note: this might have already been set, if during the last
    // pause we decided to start a cycle but at the beginning of
    // this pause we decided to postpone it. That's OK.
    collector_state()->set_initiate_conc_mark_if_possible(true);
  }
}

G1Policy::PauseKind G1Policy::young_gc_pause_kind() const {
  assert(!collector_state()->in_full_gc(), "must be");
  if (collector_state()->in_initial_mark_gc()) {
    assert(!collector_state()->in_young_gc_before_mixed(), "must be");
    return InitialMarkGC;
  } else if (collector_state()->in_young_gc_before_mixed()) {
    assert(!collector_state()->in_initial_mark_gc(), "must be");
    return LastYoungGC;
  } else if (collector_state()->in_mixed_phase()) {
    assert(!collector_state()->in_initial_mark_gc(), "must be");
    assert(!collector_state()->in_young_gc_before_mixed(), "must be");
    return MixedGC;
  } else {
    assert(!collector_state()->in_initial_mark_gc(), "must be");
    assert(!collector_state()->in_young_gc_before_mixed(), "must be");
    return YoungOnlyGC;
  }
}

void G1Policy::record_pause(PauseKind kind, double start, double end) {
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
      if (_g1h->gc_cause() != GCCause::_g1_periodic_collection) {
        _initial_mark_to_mixed.record_initial_mark_end(end);
      }
      break;
    case MixedGC:
      _initial_mark_to_mixed.record_mixed_gc_start(start);
      break;
    default:
      ShouldNotReachHere();
  }
}

void G1Policy::abort_time_to_mixed_tracking() {
  _initial_mark_to_mixed.reset();
}

bool G1Policy::next_gc_should_be_mixed(const char* true_action_str,
                                       const char* false_action_str) const {
  G1CollectionSetCandidates* candidates = _collection_set->candidates();

  if (candidates->is_empty()) {
    log_debug(gc, ergo)("%s (candidate old regions not available)", false_action_str);
    return false;
  }

  // Is the amount of uncollected reclaimable space above G1HeapWastePercent?
  size_t reclaimable_bytes = candidates->remaining_reclaimable_bytes();
  double reclaimable_percent = reclaimable_bytes_percent(reclaimable_bytes);
  double threshold = (double) G1HeapWastePercent;
  if (reclaimable_percent <= threshold) {
    log_debug(gc, ergo)("%s (reclaimable percentage not over threshold). candidate old regions: %u reclaimable: " SIZE_FORMAT " (%1.2f) threshold: " UINTX_FORMAT,
                        false_action_str, candidates->num_remaining(), reclaimable_bytes, reclaimable_percent, G1HeapWastePercent);
    return false;
  }
  log_debug(gc, ergo)("%s (candidate old regions available). candidate old regions: %u reclaimable: " SIZE_FORMAT " (%1.2f) threshold: " UINTX_FORMAT,
                      true_action_str, candidates->num_remaining(), reclaimable_bytes, reclaimable_percent, G1HeapWastePercent);
  return true;
}

uint G1Policy::calc_min_old_cset_length() const {
  // The min old CSet region bound is based on the maximum desired
  // number of mixed GCs after a cycle. I.e., even if some old regions
  // look expensive, we should add them to the CSet anyway to make
  // sure we go through the available old regions in no more than the
  // maximum desired number of mixed GCs.
  //
  // The calculation is based on the number of marked regions we added
  // to the CSet candidates in the first place, not how many remain, so
  // that the result is the same during all mixed GCs that follow a cycle.

  const size_t region_num = _collection_set->candidates()->num_regions();
  const size_t gc_num = (size_t) MAX2(G1MixedGCCountTarget, (uintx) 1);
  size_t result = region_num / gc_num;
  // emulate ceiling
  if (result * gc_num < region_num) {
    result += 1;
  }
  return (uint) result;
}

uint G1Policy::calc_max_old_cset_length() const {
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

void G1Policy::finalize_collection_set(double target_pause_time_ms, G1SurvivorRegions* survivor) {
  double time_remaining_ms = _collection_set->finalize_young_part(target_pause_time_ms, survivor);
  _collection_set->finalize_old_part(time_remaining_ms);
}

void G1Policy::transfer_survivors_to_cset(const G1SurvivorRegions* survivors) {

  // Add survivor regions to SurvRateGroup.
  note_start_adding_survivor_regions();
  finished_recalculating_age_indexes(true /* is_survivors */);

  HeapRegion* last = NULL;
  for (GrowableArrayIterator<HeapRegion*> it = survivors->regions()->begin();
       it != survivors->regions()->end();
       ++it) {
    HeapRegion* curr = *it;
    set_region_survivor(curr);

    // The region is a non-empty survivor so let's add it to
    // the incremental collection set for the next evacuation
    // pause.
    _collection_set->add_survivor_regions(curr);

    last = curr;
  }
  note_stop_adding_survivor_regions();

  // Don't clear the survivor list handles until the start of
  // the next evacuation pause - we need it in order to re-tag
  // the survivor regions from this evacuation pause as 'young'
  // at the start of the next.

  finished_recalculating_age_indexes(false /* is_survivors */);
}
