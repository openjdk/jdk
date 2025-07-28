/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1Allocator.hpp"
#include "gc/g1/g1Analytics.hpp"
#include "gc/g1/g1Arguments.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSet.hpp"
#include "gc/g1/g1CollectionSetCandidates.inline.hpp"
#include "gc/g1/g1CollectionSetChooser.hpp"
#include "gc/g1/g1ConcurrentMark.hpp"
#include "gc/g1/g1ConcurrentMarkThread.inline.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/g1ConcurrentRefineStats.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "gc/g1/g1IHOPControl.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1SurvivorRegions.hpp"
#include "gc/g1/g1YoungGenSizer.hpp"
#include "gc/shared/concurrentGCBreakpoints.hpp"
#include "gc/shared/gcPolicyCounters.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "logging/log.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/debug.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/pair.hpp"

G1Policy::G1Policy(STWGCTimer* gc_timer) :
  _predictor((100 - G1ConfidencePercent) / 100.0),
  _analytics(new G1Analytics(&_predictor)),
  _remset_tracker(),
  _mmu_tracker(new G1MMUTracker(GCPauseIntervalMillis / 1000.0, MaxGCPauseMillis / 1000.0)),
  _old_gen_alloc_tracker(),
  _ihop_control(create_ihop_control(&_old_gen_alloc_tracker, &_predictor)),
  _policy_counters(new GCPolicyCounters("GarbageFirst", 1, 2)),
  _cur_pause_start_sec(0.0),
  _young_list_desired_length(0),
  _young_list_target_length(0),
  _eden_surv_rate_group(new G1SurvRateGroup()),
  _survivor_surv_rate_group(new G1SurvRateGroup()),
  _reserve_factor((double) G1ReservePercent / 100.0),
  _reserve_regions(0),
  _young_gen_sizer(),
  _free_regions_at_end_of_collection(0),
  _card_rs_length(0),
  _pending_cards_at_gc_start(0),
  _concurrent_start_to_mixed(),
  _collection_set(nullptr),
  _g1h(nullptr),
  _phase_times_timer(gc_timer),
  _phase_times(nullptr),
  _tenuring_threshold(MaxTenuringThreshold),
  _max_survivor_regions(0),
  _survivors_age_table(true)
{
}

G1Policy::~G1Policy() {
  delete _ihop_control;
}

G1CollectorState* G1Policy::collector_state() const { return _g1h->collector_state(); }

void G1Policy::init(G1CollectedHeap* g1h, G1CollectionSet* collection_set) {
  _g1h = g1h;
  _collection_set = collection_set;

  assert(Heap_lock->owned_by_self(), "Locking discipline.");

  _young_gen_sizer.adjust_max_new_size(_g1h->max_num_regions());

  _free_regions_at_end_of_collection = _g1h->num_free_regions();

  update_young_length_bounds();

  // We immediately start allocating regions placing them in the collection set.
  // Initialize the collection set info.
  _collection_set->start_incremental_building();
}

void G1Policy::record_young_gc_pause_start() {
  phase_times()->record_gc_pause_start();
}

class G1YoungLengthPredictor {
  const double _base_time_ms;
  const double _base_free_regions;
  const double _target_pause_time_ms;
  const G1Policy* const _policy;

 public:
  G1YoungLengthPredictor(double base_time_ms,
                         double base_free_regions,
                         double target_pause_time_ms,
                         const G1Policy* policy) :
    _base_time_ms(base_time_ms),
    _base_free_regions(base_free_regions),
    _target_pause_time_ms(target_pause_time_ms),
    _policy(policy) {}

  bool will_fit(uint young_length) const {
    if (young_length >= _base_free_regions) {
      // end condition 1: not enough space for the young regions
      return false;
    }

    size_t bytes_to_copy = 0;
    const double copy_time_ms = _policy->predict_eden_copy_time_ms(young_length, &bytes_to_copy);
    const double young_other_time_ms = _policy->analytics()->predict_young_other_time_ms(young_length);
    const double pause_time_ms = _base_time_ms + copy_time_ms + young_other_time_ms;
    if (pause_time_ms > _target_pause_time_ms) {
      // end condition 2: prediction is over the target pause time
      return false;
    }

    const size_t free_bytes = (_base_free_regions - young_length) * G1HeapRegion::GrainBytes;

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

  _young_gen_sizer.heap_size_changed(new_number_of_regions);

  _ihop_control->update_target_occupancy(new_number_of_regions * G1HeapRegion::GrainBytes);
}

uint G1Policy::calculate_desired_eden_length_by_mmu() const {
  assert(use_adaptive_young_list_length(), "precondition");
  double now_sec = os::elapsedTime();
  double when_ms = _mmu_tracker->when_max_gc_sec(now_sec) * 1000.0;
  double alloc_rate_ms = _analytics->predict_alloc_rate_ms();
  return (uint) ceil(alloc_rate_ms * when_ms);
}

void G1Policy::update_young_length_bounds() {
  assert(!Universe::is_fully_initialized() || SafepointSynchronize::is_at_safepoint(), "must be");
  bool for_young_only_phase = collector_state()->in_young_only_phase();
  update_young_length_bounds(_analytics->predict_pending_cards(for_young_only_phase),
                             _analytics->predict_card_rs_length(for_young_only_phase),
                             _analytics->predict_code_root_rs_length(for_young_only_phase));
}

void G1Policy::update_young_length_bounds(size_t pending_cards, size_t card_rs_length, size_t code_root_rs_length) {
  uint old_young_list_target_length = young_list_target_length();

  uint new_young_list_desired_length = calculate_young_desired_length(pending_cards, card_rs_length, code_root_rs_length);
  uint new_young_list_target_length = calculate_young_target_length(new_young_list_desired_length);

  log_trace(gc, ergo, heap)("Young list length update: pending cards %zu card_rs_length %zu old target %u desired: %u target: %u",
                            pending_cards,
                            card_rs_length,
                            old_young_list_target_length,
                            new_young_list_desired_length,
                            new_young_list_target_length);

  // Write back. This is not an attempt to control visibility order to other threads
  // here; all the revising of the young gen length are best effort to keep pause time.
  // E.g. we could be "too late" revising young gen upwards to avoid GC because
  // there is some time left, or some threads could get different values for stopping
  // allocation.
  // That is "fine" - at most this will schedule a GC (hopefully only a little) too
  // early or too late.
  Atomic::store(&_young_list_desired_length, new_young_list_desired_length);
  Atomic::store(&_young_list_target_length, new_young_list_target_length);
}

// Calculates desired young gen length. It is calculated from:
//
// - sizer min/max bounds on young gen
// - pause time goal for whole young gen evacuation
// - MMU goal influencing eden to make GCs spaced apart
// - if after a GC, request at least one eden region to avoid immediate full gcs
//
// We may enter with already allocated eden and survivor regions because there
// are survivor regions (after gc). Young gen revising can call this method at any
// time too.
//
// For this method it does not matter if the above goals may result in a desired
// value smaller than what is already allocated or what can actually be allocated.
// This return value is only an expectation.
//
uint G1Policy::calculate_young_desired_length(size_t pending_cards,
                                              size_t card_rs_length,
                                              size_t code_root_rs_length) const {
  uint min_young_length_by_sizer = _young_gen_sizer.min_desired_young_length();
  uint max_young_length_by_sizer = _young_gen_sizer.max_desired_young_length();

  assert(min_young_length_by_sizer >= 1, "invariant");
  assert(max_young_length_by_sizer >= min_young_length_by_sizer, "invariant");

  // Calculate the absolute and desired min bounds first.

  // This is how many survivor regions we already have.
  const uint survivor_length = _g1h->survivor_regions_count();
  // Size of the already allocated young gen.
  const uint allocated_young_length = _g1h->young_regions_count();
  // This is the absolute minimum young length that we can return. Ensure that we
  // don't go below any user-defined minimum bound.  Also, we must have at least
  // one eden region, to ensure progress. But when revising during the ensuing
  // mutator phase we might have already allocated more than either of those, in
  // which case use that.
  uint absolute_min_young_length = MAX3(min_young_length_by_sizer,
                                        survivor_length + 1,
                                        allocated_young_length);
  // Calculate the absolute max bounds. After evac failure or when revising the
  // young length we might have exceeded absolute min length or absolute_max_length,
  // so adjust the result accordingly.
  uint absolute_max_young_length = MAX2(max_young_length_by_sizer, absolute_min_young_length);

  uint desired_eden_length_by_mmu = 0;
  uint desired_eden_length_by_pause = 0;

  uint desired_young_length = 0;
  if (use_adaptive_young_list_length()) {
    desired_eden_length_by_mmu = calculate_desired_eden_length_by_mmu();

    double base_time_ms = predict_base_time_ms(pending_cards, card_rs_length, code_root_rs_length);
    double retained_time_ms = predict_retained_regions_evac_time();
    double total_time_ms = base_time_ms + retained_time_ms;

    log_trace(gc, ergo, heap)("Predicted total base time: total %f base_time %f retained_time %f",
                              total_time_ms, base_time_ms, retained_time_ms);

    desired_eden_length_by_pause =
      calculate_desired_eden_length_by_pause(total_time_ms,
                                             absolute_min_young_length - survivor_length,
                                             absolute_max_young_length - survivor_length);

    // Incorporate MMU concerns; assume that it overrides the pause time
    // goal, as the default value has been chosen to effectively disable it.
    uint desired_eden_length = MAX2(desired_eden_length_by_pause,
                                    desired_eden_length_by_mmu);

    desired_young_length = desired_eden_length + survivor_length;
  } else {
    // The user asked for a fixed young gen so we'll fix the young gen
    // whether the next GC is young or mixed.
    desired_young_length = min_young_length_by_sizer;
  }
  // Clamp to absolute min/max after we determined desired lengths.
  desired_young_length = clamp(desired_young_length, absolute_min_young_length, absolute_max_young_length);

  log_trace(gc, ergo, heap)("Young desired length %u "
                            "survivor length %u "
                            "allocated young length %u "
                            "absolute min young length %u "
                            "absolute max young length %u "
                            "desired eden length by mmu %u "
                            "desired eden length by pause %u ",
                            desired_young_length, survivor_length,
                            allocated_young_length, absolute_min_young_length,
                            absolute_max_young_length, desired_eden_length_by_mmu,
                            desired_eden_length_by_pause);

  assert(desired_young_length >= allocated_young_length, "must be");
  return desired_young_length;
}

// Limit the desired (wished) young length by current free regions. If the request
// can be satisfied without using up reserve regions, do so, otherwise eat into
// the reserve, giving away at most what the heap sizer allows.
uint G1Policy::calculate_young_target_length(uint desired_young_length) const {
  uint allocated_young_length = _g1h->young_regions_count();

  uint receiving_additional_eden;
  if (allocated_young_length >= desired_young_length) {
    // Already used up all we actually want (may happen as G1 revises the
    // young list length concurrently). Do not allow more, potentially resulting in GC.
    receiving_additional_eden = 0;
    log_trace(gc, ergo, heap)("Young target length: Already used up desired young %u allocated %u",
                              desired_young_length,
                              allocated_young_length);
  } else {
    // Now look at how many free regions are there currently, and the heap reserve.
    // We will try our best not to "eat" into the reserve as long as we can. If we
    // do, we at most eat the sizer's minimum regions into the reserve or half the
    // reserve rounded up (if possible; this is an arbitrary value).

    uint max_to_eat_into_reserve = MIN2(_young_gen_sizer.min_desired_young_length(),
                                        (_reserve_regions + 1) / 2);

    log_trace(gc, ergo, heap)("Young target length: Common "
                              "free regions at end of collection %u "
                              "desired young length %u "
                              "reserve region %u "
                              "max to eat into reserve %u",
                              _free_regions_at_end_of_collection,
                              desired_young_length,
                              _reserve_regions,
                              max_to_eat_into_reserve);

    uint survivor_regions_count = _g1h->survivor_regions_count();
    uint desired_eden_length = desired_young_length - survivor_regions_count;
    uint allocated_eden_length = allocated_young_length - survivor_regions_count;

    if (_free_regions_at_end_of_collection <= _reserve_regions) {
      // Fully eat (or already eating) into the reserve, hand back at most absolute_min_length regions.
      uint receiving_eden = MIN3(_free_regions_at_end_of_collection,
                                  desired_eden_length,
                                  max_to_eat_into_reserve);
      // Ensure that we provision for at least one Eden region.
      receiving_eden = MAX2(receiving_eden, 1u);
      // We could already have allocated more regions than what we could get
      // above.
      receiving_additional_eden = allocated_eden_length < receiving_eden ?
                                  receiving_eden - allocated_eden_length : 0;

      log_trace(gc, ergo, heap)("Young target length: Fully eat into reserve "
                                "receiving eden %u receiving additional eden %u",
                                receiving_eden, receiving_additional_eden);
    } else if (_free_regions_at_end_of_collection < (desired_eden_length + _reserve_regions)) {
      // Partially eat into the reserve, at most max_to_eat_into_reserve regions.
      uint free_outside_reserve = _free_regions_at_end_of_collection - _reserve_regions;
      assert(free_outside_reserve < desired_eden_length,
             "must be %u %u",
             free_outside_reserve, desired_eden_length);

      uint receiving_within_reserve = MIN2(desired_eden_length - free_outside_reserve,
                                           max_to_eat_into_reserve);
      uint receiving_eden = free_outside_reserve + receiving_within_reserve;

      // Again, we could have already allocated more than we could get.
      receiving_additional_eden = allocated_eden_length < receiving_eden ?
                                  receiving_eden - allocated_eden_length : 0;

      log_trace(gc, ergo, heap)("Young target length: Partially eat into reserve "
                                "free outside reserve %u "
                                "receiving within reserve %u "
                                "receiving eden %u "
                                "receiving additional eden %u",
                                free_outside_reserve, receiving_within_reserve,
                                receiving_eden, receiving_additional_eden);
    } else {
      // No need to use the reserve.
      receiving_additional_eden = desired_young_length - allocated_young_length;
      log_trace(gc, ergo, heap)("Young target length: No need to use reserve "
                                "receiving additional eden %u",
                                receiving_additional_eden);
    }
  }

  uint target_young_length = allocated_young_length + receiving_additional_eden;

  assert(target_young_length >= allocated_young_length, "must be");

  log_trace(gc, ergo, heap)("Young target length: "
                            "young target length %u "
                            "allocated young length %u "
                            "received additional eden %u",
                            target_young_length, allocated_young_length,
                            receiving_additional_eden);
  return target_young_length;
}

uint G1Policy::calculate_desired_eden_length_by_pause(double base_time_ms,
                                                      uint min_eden_length,
                                                      uint max_eden_length) const {
  if (!next_gc_should_be_mixed()) {
    return calculate_desired_eden_length_before_young_only(base_time_ms,
                                                           min_eden_length,
                                                           max_eden_length);
  } else {
    return calculate_desired_eden_length_before_mixed(base_time_ms,
                                                      min_eden_length,
                                                      max_eden_length);
  }
}

uint G1Policy::calculate_desired_eden_length_before_young_only(double base_time_ms,
                                                               uint min_eden_length,
                                                               uint max_eden_length) const {
  assert(use_adaptive_young_list_length(), "pre-condition");

  assert(min_eden_length <= max_eden_length, "must be %u %u", min_eden_length, max_eden_length);

  // Here, we will make sure that the shortest young length that
  // makes sense fits within the target pause time.

  G1YoungLengthPredictor p(base_time_ms,
                           _free_regions_at_end_of_collection,
                           _mmu_tracker->max_gc_time() * 1000.0,
                           this);
  if (p.will_fit(min_eden_length)) {
    // The shortest young length will fit into the target pause time;
    // we'll now check whether the absolute maximum number of young
    // regions will fit in the target pause time. If not, we'll do
    // a binary search between min_young_length and max_young_length.
    if (p.will_fit(max_eden_length)) {
      // The maximum young length will fit into the target pause time.
      // We are done so set min young length to the maximum length (as
      // the result is assumed to be returned in min_young_length).
      min_eden_length = max_eden_length;
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

      assert(min_eden_length < max_eden_length, "invariant");
      uint diff = (max_eden_length - min_eden_length) / 2;
      while (diff > 0) {
        uint eden_length = min_eden_length + diff;
        if (p.will_fit(eden_length)) {
          min_eden_length = eden_length;
        } else {
          max_eden_length = eden_length;
        }
        assert(min_eden_length <  max_eden_length, "invariant");
        diff = (max_eden_length - min_eden_length) / 2;
      }
      // The results is min_young_length which, according to the
      // loop invariants, should fit within the target pause time.

      // These are the post-conditions of the binary search above:
      assert(min_eden_length < max_eden_length,
             "otherwise we should have discovered that max_eden_length "
             "fits into the pause target and not done the binary search");
      assert(p.will_fit(min_eden_length),
             "min_eden_length, the result of the binary search, should "
             "fit into the pause target");
      assert(!p.will_fit(min_eden_length + 1),
             "min_eden_length, the result of the binary search, should be "
             "optimal, so no larger length should fit into the pause target");
    }
  } else {
    // Even the minimum length doesn't fit into the pause time
    // target, return it as the result nevertheless.
  }
  return min_eden_length;
}

uint G1Policy::calculate_desired_eden_length_before_mixed(double base_time_ms,
                                                          uint min_eden_length,
                                                          uint max_eden_length) const {
  uint min_marking_candidates = MIN2(calc_min_old_cset_length(candidates()->last_marking_candidates_length()),
                                     candidates()->from_marking_groups().num_regions());
  double predicted_region_evac_time_ms = base_time_ms;
  uint selected_candidates = 0;
  for (G1CSetCandidateGroup* gr : candidates()->from_marking_groups()) {
    if (selected_candidates >= min_marking_candidates) {
      break;
    }
    predicted_region_evac_time_ms += gr->predict_group_total_time_ms();
    selected_candidates += gr->length();
  }

  return calculate_desired_eden_length_before_young_only(predicted_region_evac_time_ms,
                                                         min_eden_length,
                                                         max_eden_length);
}

double G1Policy::predict_survivor_regions_evac_time() const {
  const GrowableArray<G1HeapRegion*>* survivor_regions = _g1h->survivor()->regions();
  double survivor_regions_evac_time = predict_young_region_other_time_ms(_g1h->survivor()->length());
  for (GrowableArrayIterator<G1HeapRegion*> it = survivor_regions->begin();
       it != survivor_regions->end();
       ++it) {
    survivor_regions_evac_time += predict_region_copy_time_ms(*it, _g1h->collector_state()->in_young_only_phase());
  }

  return survivor_regions_evac_time;
}

double G1Policy::predict_retained_regions_evac_time() const {
  uint num_regions = 0;
  uint num_pinned_regions = 0;

  double result = 0.0;

  G1CSetCandidateGroupList* retained_groups = &candidates()->retained_groups();
  uint min_regions_left = MIN2(min_retained_old_cset_length(),
                               retained_groups->num_regions());

  for (G1CSetCandidateGroup* group : *retained_groups) {
    assert(group->length() == 1, "We should only have one region in a retained group");
    G1HeapRegion* r = group->region_at(0); // We only have one region per group.
    // We optimistically assume that any of these marking candidate regions will
    // be reclaimable the next gc, so just consider them as normal.
    if (r->has_pinned_objects()) {
      num_pinned_regions++;
    }
    if (min_regions_left == 0) {
      // Minimum amount of regions considered. Exit.
      break;
    }
    min_regions_left--;
    result += group->predict_group_total_time_ms();
    num_regions++;
  }

  log_trace(gc, ergo, heap)("Selected %u of %u retained candidates (pinned %u) taking %1.3fms additional time",
                            num_regions, retained_groups->num_regions(), num_pinned_regions, result);
  return result;
}

G1GCPhaseTimes* G1Policy::phase_times() const {
  // Lazy allocation because it must follow initialization of all the
  // OopStorage objects by various other subsystems.
  if (_phase_times == nullptr) {
    _phase_times = new G1GCPhaseTimes(_phase_times_timer, ParallelGCThreads);
  }
  return _phase_times;
}

void G1Policy::revise_young_list_target_length(size_t card_rs_length, size_t code_root_rs_length) {
  guarantee(use_adaptive_young_list_length(), "should not call this otherwise" );

  size_t thread_buffer_cards = _analytics->predict_dirtied_cards_in_thread_buffers();
  G1DirtyCardQueueSet& dcqs = G1BarrierSet::dirty_card_queue_set();
  size_t pending_cards = dcqs.num_cards() + thread_buffer_cards;
  update_young_length_bounds(pending_cards, card_rs_length, code_root_rs_length);
}

void G1Policy::record_full_collection_start() {
  record_pause_start_time();
  // Release the future to-space so that it is available for compaction into.
  collector_state()->set_in_young_only_phase(false);
  collector_state()->set_in_full_gc(true);
  _collection_set->abandon_all_candidates();
  _pending_cards_at_gc_start = 0;
}

void G1Policy::record_full_collection_end() {
  // Consider this like a collection pause for the purposes of allocation
  // since last pause.
  double end_sec = os::elapsedTime();

  collector_state()->set_in_full_gc(false);

  // "Nuke" the heuristics that control the young/mixed GC
  // transitions and make sure we start with young GCs after the Full GC.
  collector_state()->set_in_young_only_phase(true);
  collector_state()->set_in_young_gc_before_mixed(false);
  collector_state()->set_initiate_conc_mark_if_possible(need_to_start_conc_mark("end of Full GC"));
  collector_state()->set_in_concurrent_start_gc(false);
  collector_state()->set_mark_in_progress(false);
  collector_state()->set_mark_or_rebuild_in_progress(false);
  collector_state()->set_clear_bitmap_in_progress(false);

  _eden_surv_rate_group->start_adding_regions();
  // also call this on any additional surv rate groups

  _free_regions_at_end_of_collection = _g1h->num_free_regions();
  _survivor_surv_rate_group->reset();
  update_young_length_bounds();

  _old_gen_alloc_tracker.reset_after_gc(_g1h->humongous_regions_count() * G1HeapRegion::GrainBytes);

  double start_time_sec = cur_pause_start_sec();
  record_pause(G1GCPauseType::FullGC, start_time_sec, end_sec);
}

static void log_refinement_stats(const char* kind, const G1ConcurrentRefineStats& stats) {
  log_debug(gc, refine, stats)
           ("%s refinement: %.2fms, refined: %zu"
            ", precleaned: %zu, dirtied: %zu",
            kind,
            stats.refinement_time().seconds() * MILLIUNITS,
            stats.refined_cards(),
            stats.precleaned_cards(),
            stats.dirtied_cards());
}

void G1Policy::record_concurrent_refinement_stats(size_t pending_cards,
                                                  size_t thread_buffer_cards) {
  _pending_cards_at_gc_start = pending_cards;
  _analytics->report_dirtied_cards_in_thread_buffers(thread_buffer_cards);

  // Collect per-thread stats, mostly from mutator activity.
  G1DirtyCardQueueSet& dcqs = G1BarrierSet::dirty_card_queue_set();
  G1ConcurrentRefineStats mut_stats = dcqs.concatenated_refinement_stats();

  // Collect specialized concurrent refinement thread stats.
  G1ConcurrentRefine* cr = _g1h->concurrent_refine();
  G1ConcurrentRefineStats cr_stats = cr->get_and_reset_refinement_stats();

  G1ConcurrentRefineStats total_stats = mut_stats + cr_stats;

  log_refinement_stats("Mutator", mut_stats);
  log_refinement_stats("Concurrent", cr_stats);
  log_refinement_stats("Total", total_stats);

  // Record the rate at which cards were refined.
  // Don't update the rate if the current sample is empty or time is zero.
  Tickspan refinement_time = total_stats.refinement_time();
  size_t refined_cards = total_stats.refined_cards();
  if ((refined_cards > 0) && (refinement_time > Tickspan())) {
    double rate = refined_cards / (refinement_time.seconds() * MILLIUNITS);
    _analytics->report_concurrent_refine_rate_ms(rate);
    log_debug(gc, refine, stats)("Concurrent refinement rate: %.2f cards/ms", rate);
  }

  // Record mutator's card logging rate.
  double mut_start_time = _analytics->prev_collection_pause_end_ms();
  double mut_end_time = cur_pause_start_sec() * MILLIUNITS;
  double mut_time = mut_end_time - mut_start_time;
  // Unlike above for conc-refine rate, here we should not require a
  // non-empty sample, since an application could go some time with only
  // young-gen or filtered out writes.  But we'll ignore unusually short
  // sample periods, as they may just pollute the predictions.
  if (mut_time > 1.0) {   // Require > 1ms sample time.
    double dirtied_rate = total_stats.dirtied_cards() / mut_time;
    _analytics->report_dirtied_cards_rate_ms(dirtied_rate);
    log_debug(gc, refine, stats)("Generate dirty cards rate: %.2f cards/ms", dirtied_rate);
  }
}

bool G1Policy::should_retain_evac_failed_region(uint index) const {
  size_t live_bytes = _g1h->region_at(index)->live_bytes();
  size_t threshold = G1RetainRegionLiveThresholdPercent * G1HeapRegion::GrainBytes / 100;
  return live_bytes < threshold;
}

void G1Policy::record_pause_start_time() {
  Ticks now = Ticks::now();
  _cur_pause_start_sec = now.seconds();
}

void G1Policy::record_young_collection_start() {
  record_pause_start_time();
  // We only need to do this here as the policy will only be applied
  // to the GC we're about to start. so, no point is calculating this
  // every time we calculate / recalculate the target young length.
  update_survivors_policy();

  assert(max_survivor_regions() + _g1h->num_used_regions() <= _g1h->max_num_regions(),
         "Maximum survivor regions %u plus used regions %u exceeds max regions %u",
         max_survivor_regions(), _g1h->num_used_regions(), _g1h->max_num_regions());
  assert_used_and_recalculate_used_equal(_g1h);

  // do that for any other surv rate groups
  _eden_surv_rate_group->stop_adding_regions();
  _survivors_age_table.clear();

  assert(_g1h->collection_set()->verify_young_ages(), "region age verification failed");
}

void G1Policy::record_concurrent_mark_init_end() {
  assert(!collector_state()->initiate_conc_mark_if_possible(), "we should have cleared it by now");
  collector_state()->set_in_concurrent_start_gc(false);
}

void G1Policy::record_concurrent_mark_remark_end() {
  double end_time_sec = os::elapsedTime();
  double start_time_sec = cur_pause_start_sec();
  double elapsed_time_ms = (end_time_sec - start_time_sec) * 1000.0;
  _analytics->report_concurrent_mark_remark_times_ms(elapsed_time_ms);
  record_pause(G1GCPauseType::Remark, start_time_sec, end_time_sec);
  collector_state()->set_mark_in_progress(false);
}

G1CollectionSetCandidates* G1Policy::candidates() const {
  return _collection_set->candidates();
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
  return other_time_ms(pause_time_ms) - (young_other_time_ms() + non_young_other_time_ms());
}

bool G1Policy::about_to_start_mixed_phase() const {
  return _g1h->concurrent_mark()->cm_thread()->in_progress() || collector_state()->in_young_gc_before_mixed();
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
    result = collector_state()->in_young_only_phase();
    log_debug(gc, ergo, ihop)("%s occupancy: %zuB allocation request: %zuB threshold: %zuB (%1.2f) source: %s",
                              result ? "Request concurrent cycle initiation (occupancy higher than threshold)" : "Do not request concurrent cycle initiation (still doing mixed collections)",
                              cur_used_bytes, alloc_byte_size, marking_initiating_used_threshold, (double) marking_initiating_used_threshold / _g1h->capacity() * 100, source);
  }
  return result;
}

bool G1Policy::concurrent_operation_is_full_mark(const char* msg) {
  return collector_state()->in_concurrent_start_gc() &&
    ((_g1h->gc_cause() != GCCause::_g1_humongous_allocation) || need_to_start_conc_mark(msg));
}

double G1Policy::logged_cards_processing_time() const {
  double all_cards_processing_time = average_time_ms(G1GCPhaseTimes::ScanHR) + average_time_ms(G1GCPhaseTimes::OptScanHR);
  size_t logged_dirty_cards = phase_times()->sum_thread_work_items(G1GCPhaseTimes::MergeLB, G1GCPhaseTimes::MergeLBDirtyCards);
  size_t scan_heap_roots_cards = phase_times()->sum_thread_work_items(G1GCPhaseTimes::ScanHR, G1GCPhaseTimes::ScanHRScannedCards) +
                                 phase_times()->sum_thread_work_items(G1GCPhaseTimes::OptScanHR, G1GCPhaseTimes::ScanHRScannedCards);

  double merge_logged_cards_time = average_time_ms(G1GCPhaseTimes::MergeLB) +
                                   phase_times()->cur_distribute_log_buffers_time_ms();

  // Approximate the time spent processing cards from log buffers by scaling
  // the total processing time by the ratio of logged cards to total cards
  // processed.  There might be duplicate cards in different log buffers,
  // leading to an overestimate.  That effect should be relatively small
  // unless there are few cards to process, because cards in buffers are
  // dirtied to limit duplication.  Also need to avoid scaling when both
  // counts are zero, which happens especially during early GCs.  So ascribe
  // all of the time to the logged cards unless there are more total cards.
  if (logged_dirty_cards >= scan_heap_roots_cards) {
    return all_cards_processing_time + merge_logged_cards_time;
  }
  return (all_cards_processing_time * logged_dirty_cards / scan_heap_roots_cards) + merge_logged_cards_time;
}

// Anything below that is considered to be zero
#define MIN_TIMER_GRANULARITY 0.0000001

void G1Policy::record_young_collection_end(bool concurrent_operation_is_full_mark, bool allocation_failure) {
  G1GCPhaseTimes* p = phase_times();

  double start_time_sec = cur_pause_start_sec();
  double end_time_sec = Ticks::now().seconds();
  double pause_time_ms = (end_time_sec - start_time_sec) * 1000.0;

  G1GCPauseType this_pause = collector_state()->young_gc_pause_type(concurrent_operation_is_full_mark);
  bool is_young_only_pause = G1GCPauseTypeHelper::is_young_only_pause(this_pause);

  if (G1GCPauseTypeHelper::is_concurrent_start_pause(this_pause)) {
    record_concurrent_mark_init_end();
  } else {
    maybe_start_marking();
  }

  double app_time_ms = (start_time_sec * 1000.0 - _analytics->prev_collection_pause_end_ms());
  if (app_time_ms < MIN_TIMER_GRANULARITY) {
    // This usually happens due to the timer not having the required
    // granularity. Some Linuxes are the usual culprits.
    // We'll just set it to something (arbitrarily) small.
    app_time_ms = 1.0;
  }

  // Evacuation failures skew the timing too much to be considered for some statistics updates.
  // We make the assumption that these are rare.
  bool update_stats = !allocation_failure;

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
  }

  record_pause(this_pause, start_time_sec, end_time_sec, allocation_failure);

  if (G1GCPauseTypeHelper::is_last_young_pause(this_pause)) {
    assert(!G1GCPauseTypeHelper::is_concurrent_start_pause(this_pause),
           "The young GC before mixed is not allowed to be concurrent start GC");
    // This has been the young GC before we start doing mixed GCs. We already
    // decided to start mixed GCs much earlier, so there is nothing to do except
    // advancing the state.
    collector_state()->set_in_young_only_phase(false);
    collector_state()->set_in_young_gc_before_mixed(false);
  } else if (G1GCPauseTypeHelper::is_mixed_pause(this_pause)) {
    // This is a mixed GC. Here we decide whether to continue doing more
    // mixed GCs or not.
    if (!next_gc_should_be_mixed()) {
      log_debug(gc, ergo)("do not continue mixed GCs (candidate old regions not available)");
      collector_state()->set_in_young_only_phase(true);

      assert(!candidates()->has_more_marking_candidates(),
             "only end mixed if all candidates from marking were processed");

      maybe_start_marking();
    }
  } else {
    assert(is_young_only_pause, "must be");
  }

  _eden_surv_rate_group->start_adding_regions();

  if (update_stats) {
    // Update prediction for card merge.
    size_t const merged_cards_from_log_buffers = p->sum_thread_work_items(G1GCPhaseTimes::MergeLB, G1GCPhaseTimes::MergeLBDirtyCards);
    // MergeRSCards includes the cards from the Eager Reclaim phase.
    size_t const merged_cards_from_rs = p->sum_thread_work_items(G1GCPhaseTimes::MergeRS, G1GCPhaseTimes::MergeRSCards) +
                                        p->sum_thread_work_items(G1GCPhaseTimes::OptMergeRS, G1GCPhaseTimes::MergeRSCards);
    size_t const total_cards_merged = merged_cards_from_rs +
                                      merged_cards_from_log_buffers;

    if (total_cards_merged >= G1NumCardsCostSampleThreshold) {
      double avg_time_merge_cards = average_time_ms(G1GCPhaseTimes::MergeER) +
                                    average_time_ms(G1GCPhaseTimes::MergeRS) +
                                    average_time_ms(G1GCPhaseTimes::MergeLB) +
                                    p->cur_distribute_log_buffers_time_ms() +
                                    average_time_ms(G1GCPhaseTimes::OptMergeRS);
      _analytics->report_cost_per_card_merge_ms(avg_time_merge_cards / total_cards_merged, is_young_only_pause);
    }

    // Update prediction for card scan
    size_t const total_cards_scanned = p->sum_thread_work_items(G1GCPhaseTimes::ScanHR, G1GCPhaseTimes::ScanHRScannedCards) +
                                       p->sum_thread_work_items(G1GCPhaseTimes::OptScanHR, G1GCPhaseTimes::ScanHRScannedCards);

    if (total_cards_scanned >= G1NumCardsCostSampleThreshold) {
      double avg_time_dirty_card_scan = average_time_ms(G1GCPhaseTimes::ScanHR) +
                                        average_time_ms(G1GCPhaseTimes::OptScanHR);

      _analytics->report_cost_per_card_scan_ms(avg_time_dirty_card_scan / total_cards_scanned, is_young_only_pause);
    }

    // Update prediction for the ratio between cards from the remembered
    // sets and actually scanned cards from the remembered sets.
    // Due to duplicates in the log buffers, the number of scanned cards
    // can be smaller than the cards in the log buffers.
    const size_t scanned_cards_from_rs = (total_cards_scanned > merged_cards_from_log_buffers) ? total_cards_scanned - merged_cards_from_log_buffers : 0;
    double scan_to_merge_ratio = 0.0;
    if (merged_cards_from_rs > 0) {
      scan_to_merge_ratio = (double)scanned_cards_from_rs / merged_cards_from_rs;
    }
    _analytics->report_card_scan_to_merge_ratio(scan_to_merge_ratio, is_young_only_pause);

    // Update prediction for code root scan
    size_t const total_code_roots_scanned = p->sum_thread_work_items(G1GCPhaseTimes::CodeRoots, G1GCPhaseTimes::CodeRootsScannedNMethods) +
                                            p->sum_thread_work_items(G1GCPhaseTimes::OptCodeRoots, G1GCPhaseTimes::CodeRootsScannedNMethods);

    if (total_code_roots_scanned >= G1NumCodeRootsCostSampleThreshold) {
      double avg_time_code_root_scan = average_time_ms(G1GCPhaseTimes::CodeRoots) +
                                       average_time_ms(G1GCPhaseTimes::OptCodeRoots);

      _analytics->report_cost_per_code_root_scan_ms(avg_time_code_root_scan / total_code_roots_scanned, is_young_only_pause);
    }

    // Update prediction for copy cost per byte
    size_t copied_bytes = p->sum_thread_work_items(G1GCPhaseTimes::MergePSS, G1GCPhaseTimes::MergePSSCopiedBytes);

    if (copied_bytes > 0) {
      double cost_per_byte_ms = (average_time_ms(G1GCPhaseTimes::ObjCopy) + average_time_ms(G1GCPhaseTimes::OptObjCopy)) / copied_bytes;
      _analytics->report_cost_per_byte_ms(cost_per_byte_ms, is_young_only_pause);
    }

    if (_collection_set->young_region_length() > 0) {
      _analytics->report_young_other_cost_per_region_ms(young_other_time_ms() /
                                                        _collection_set->young_region_length());
    }

    if (_collection_set->initial_old_region_length() > 0) {
      _analytics->report_non_young_other_cost_per_region_ms(non_young_other_time_ms() /
                                                            _collection_set->initial_old_region_length());
    }

    _analytics->report_constant_other_time_ms(constant_other_time_ms(pause_time_ms));

    _analytics->report_pending_cards((double)pending_cards_at_gc_start(), is_young_only_pause);
    _analytics->report_card_rs_length((double)_card_rs_length, is_young_only_pause);
    _analytics->report_code_root_rs_length((double)total_code_roots_scanned, is_young_only_pause);
  }

  assert(!(G1GCPauseTypeHelper::is_concurrent_start_pause(this_pause) && collector_state()->mark_or_rebuild_in_progress()),
         "If the last pause has been concurrent start, we should not have been in the marking window");
  if (G1GCPauseTypeHelper::is_concurrent_start_pause(this_pause)) {
    collector_state()->set_mark_in_progress(concurrent_operation_is_full_mark);
    collector_state()->set_mark_or_rebuild_in_progress(concurrent_operation_is_full_mark);
  }

  _free_regions_at_end_of_collection = _g1h->num_free_regions();

  // Do not update dynamic IHOP due to G1 periodic collection as it is highly likely
  // that in this case we are not running in a "normal" operating mode.
  if (_g1h->gc_cause() != GCCause::_g1_periodic_collection) {
    update_young_length_bounds();

    _old_gen_alloc_tracker.reset_after_gc(_g1h->humongous_regions_count() * G1HeapRegion::GrainBytes);
    update_ihop_prediction(app_time_ms / 1000.0,
                           G1GCPauseTypeHelper::is_young_only_pause(this_pause));

    _ihop_control->send_trace_event(_g1h->gc_tracer_stw());
  } else {
    // Any garbage collection triggered as periodic collection resets the time-to-mixed
    // measurement. Periodic collection typically means that the application is "inactive", i.e.
    // the marking threads may have received an uncharacteristic amount of cpu time
    // for completing the marking, i.e. are faster than expected.
    // This skews the predicted marking length towards smaller values which might cause
    // the mark start being too late.
    abort_time_to_mixed_tracking();
  }

  // Note that _mmu_tracker->max_gc_time() returns the time in seconds.
  double logged_cards_time_goal_ms = _mmu_tracker->max_gc_time() * MILLIUNITS * G1RSetUpdatingPauseTimePercent / 100.0;

  double const logged_cards_time_ms = logged_cards_processing_time();
  size_t logged_cards =
    phase_times()->sum_thread_work_items(G1GCPhaseTimes::MergeLB,
                                         G1GCPhaseTimes::MergeLBDirtyCards);
  bool exceeded_goal = logged_cards_time_goal_ms < logged_cards_time_ms;
  size_t predicted_thread_buffer_cards = _analytics->predict_dirtied_cards_in_thread_buffers();
  G1ConcurrentRefine* cr = _g1h->concurrent_refine();

  log_debug(gc, ergo, refine)
           ("GC refinement: goal: %zu + %zu / %1.2fms, actual: %zu / %1.2fms, %s",
            cr->pending_cards_target(),
            predicted_thread_buffer_cards,
            logged_cards_time_goal_ms,
            logged_cards,
            logged_cards_time_ms,
            (exceeded_goal ? " (exceeded goal)" : ""));

  cr->adjust_after_gc(logged_cards_time_ms,
                      logged_cards,
                      predicted_thread_buffer_cards,
                      logged_cards_time_goal_ms);
}

G1IHOPControl* G1Policy::create_ihop_control(const G1OldGenAllocationTracker* old_gen_alloc_tracker,
                                             const G1Predictions* predictor) {
  if (G1UseAdaptiveIHOP) {
    return new G1AdaptiveIHOPControl(InitiatingHeapOccupancyPercent,
                                     old_gen_alloc_tracker,
                                     predictor,
                                     G1ReservePercent,
                                     G1HeapWastePercent);
  } else {
    return new G1StaticIHOPControl(InitiatingHeapOccupancyPercent, old_gen_alloc_tracker);
  }
}

void G1Policy::update_ihop_prediction(double mutator_time_s,
                                      bool this_gc_was_young_only) {
  // Always try to update IHOP prediction. Even evacuation failures give information
  // about e.g. whether to start IHOP earlier next time.

  // Avoid using really small application times that might create samples with
  // very high or very low values. They may be caused by e.g. back-to-back gcs.
  double const min_valid_time = 1e-6;

  bool report = false;

  double marking_to_mixed_time = -1.0;
  if (!this_gc_was_young_only && _concurrent_start_to_mixed.has_result()) {
    marking_to_mixed_time = _concurrent_start_to_mixed.last_marking_time();
    assert(marking_to_mixed_time > 0.0,
           "Concurrent start to mixed time must be larger than zero but is %.3f",
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
    // IHOP control wants to know the expected young gen length if it were not
    // restrained by the heap reserve. Using the actual length would make the
    // prediction too small and the limit the young gen every time we get to the
    // predicted target occupancy.
    size_t young_gen_size = young_list_desired_length() * G1HeapRegion::GrainBytes;
    _ihop_control->update_allocation_info(mutator_time_s, young_gen_size);
    report = true;
  }

  if (report) {
    report_ihop_statistics();
  }
}

void G1Policy::report_ihop_statistics() {
  _ihop_control->print();
}

void G1Policy::record_young_gc_pause_end(bool evacuation_failed) {
  phase_times()->record_gc_pause_end();
  phase_times()->print(evacuation_failed);
}

double G1Policy::predict_base_time_ms(size_t pending_cards,
                                      size_t card_rs_length,
                                      size_t code_root_rs_length) const {
  bool in_young_only_phase = collector_state()->in_young_only_phase();

  size_t unique_cards_from_rs = _analytics->predict_scan_card_num(card_rs_length, in_young_only_phase);
  // Assume that all cards from the log buffers will be scanned, i.e. there are no
  // duplicates in that set.
  size_t effective_scanned_cards = unique_cards_from_rs + pending_cards;

  double card_merge_time = _analytics->predict_card_merge_time_ms(pending_cards + card_rs_length, in_young_only_phase);
  double card_scan_time = _analytics->predict_card_scan_time_ms(effective_scanned_cards, in_young_only_phase);
  double code_root_scan_time = _analytics->predict_code_root_scan_time_ms(code_root_rs_length, in_young_only_phase);
  double constant_other_time = _analytics->predict_constant_other_time_ms();
  double survivor_evac_time = predict_survivor_regions_evac_time();

  double total_time = card_merge_time + card_scan_time + code_root_scan_time + constant_other_time + survivor_evac_time;

  log_trace(gc, ergo, heap)("Predicted base time: total %f lb_cards %zu card_rs_length %zu effective_scanned_cards %zu "
                            "card_merge_time %f card_scan_time %f code_root_rs_length %zu code_root_scan_time %f "
                            "constant_other_time %f survivor_evac_time %f",
                            total_time, pending_cards, card_rs_length, effective_scanned_cards,
                            card_merge_time, card_scan_time, code_root_rs_length, code_root_scan_time,
                            constant_other_time, survivor_evac_time);
  return total_time;
}

double G1Policy::predict_base_time_ms(size_t pending_cards) const {
  bool for_young_only_phase = collector_state()->in_young_only_phase();
  size_t card_rs_length = _analytics->predict_card_rs_length(for_young_only_phase);
  return predict_base_time_ms(pending_cards, card_rs_length);
}

double G1Policy::predict_base_time_ms(size_t pending_cards, size_t card_rs_length) const {
  bool for_young_only_phase = collector_state()->in_young_only_phase();
  size_t code_root_rs_length = _analytics->predict_code_root_rs_length(for_young_only_phase);
  return predict_base_time_ms(pending_cards, card_rs_length, code_root_rs_length);
}

size_t G1Policy::predict_bytes_to_copy(G1HeapRegion* hr) const {
  size_t bytes_to_copy;
  if (!hr->is_young()) {
    bytes_to_copy = hr->live_bytes();
  } else {
    bytes_to_copy = (size_t) (hr->used() * hr->surv_rate_prediction(_predictor));
  }
  return bytes_to_copy;
}

double G1Policy::predict_gc_efficiency(G1HeapRegion* hr) {

  double total_based_on_incoming_refs_ms = predict_merge_scan_time(hr->incoming_refs()) + // We use the number of incoming references as an estimate for remset cards.
                                           predict_non_young_other_time_ms(1) +
                                           predict_region_copy_time_ms(hr, false /* for_young_only_phase */);

  return hr->reclaimable_bytes() / total_based_on_incoming_refs_ms;
}

double G1Policy::predict_young_region_other_time_ms(uint count) const {
  return _analytics->predict_young_other_time_ms(count);
}

double G1Policy::predict_non_young_other_time_ms(uint count) const {
  return _analytics->predict_non_young_other_time_ms(count);
}

double G1Policy::predict_eden_copy_time_ms(uint count, size_t* bytes_to_copy) const {
  if (count == 0) {
    return 0.0;
  }
  size_t const expected_bytes = _eden_surv_rate_group->accum_surv_rate_pred(count - 1) * G1HeapRegion::GrainBytes;
  if (bytes_to_copy != nullptr) {
    *bytes_to_copy = expected_bytes;
  }
  return _analytics->predict_object_copy_time_ms(expected_bytes, collector_state()->in_young_only_phase());
}

double G1Policy::predict_region_copy_time_ms(G1HeapRegion* hr, bool for_young_only_phase) const {
  size_t const bytes_to_copy = predict_bytes_to_copy(hr);
  return _analytics->predict_object_copy_time_ms(bytes_to_copy, for_young_only_phase);
}

double G1Policy::predict_merge_scan_time(size_t card_rs_length) const {
  size_t scan_card_num = _analytics->predict_scan_card_num(card_rs_length, false);

  return
    _analytics->predict_card_merge_time_ms(card_rs_length, false) +
    _analytics->predict_card_scan_time_ms(scan_card_num, false);
}

double G1Policy::predict_region_code_root_scan_time(G1HeapRegion* hr, bool for_young_only_phase) const {
  size_t code_root_length = hr->rem_set()->code_roots_list_length();

  return
    _analytics->predict_code_root_scan_time_ms(code_root_length, for_young_only_phase);
}

bool G1Policy::should_allocate_mutator_region() const {
  uint young_list_length = _g1h->young_regions_count();
  return young_list_length < young_list_target_length();
}

bool G1Policy::use_adaptive_young_list_length() const {
  return _young_gen_sizer.use_adaptive_young_list_length();
}

size_t G1Policy::estimate_used_young_bytes_locked() const {
  assert_lock_strong(Heap_lock);
  G1Allocator* allocator = _g1h->allocator();
  uint used = _g1h->young_regions_count();
  uint alloc = allocator->num_nodes();
  uint full = used - MIN2(used, alloc);
  size_t bytes_used = full * G1HeapRegion::GrainBytes;
  return bytes_used + allocator->used_in_alloc_regions();
}

size_t G1Policy::desired_survivor_size(uint max_regions) const {
  size_t const survivor_capacity = G1HeapRegion::GrainWords * max_regions;
  return (size_t)((((double)survivor_capacity) * TargetSurvivorRatio) / 100);
}

void G1Policy::print_age_table() {
  _survivors_age_table.print_age_table();
}

// Calculates survivor space parameters.
void G1Policy::update_survivors_policy() {
  double max_survivor_regions_d =
                 (double)young_list_target_length() / (double) SurvivorRatio;

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
                               _g1h->num_available_regions());
}

bool G1Policy::force_concurrent_start_if_outside_cycle(GCCause::Cause gc_cause) {
  // We actually check whether we are marking here and not if we are in a
  // reclamation phase. This means that we will schedule a concurrent mark
  // even while we are still in the process of reclaiming memory.
  bool during_cycle = _g1h->concurrent_mark()->cm_thread()->in_progress();
  if (!during_cycle) {
    log_debug(gc, ergo)("Request concurrent cycle initiation (requested by GC cause). "
                        "GC cause: %s",
                        GCCause::to_string(gc_cause));
    collector_state()->set_initiate_conc_mark_if_possible(true);
    return true;
  } else {
    log_debug(gc, ergo)("Do not request concurrent cycle initiation "
                        "(concurrent cycle already in progress). GC cause: %s",
                        GCCause::to_string(gc_cause));
    return false;
  }
}

void G1Policy::initiate_conc_mark() {
  collector_state()->set_in_concurrent_start_gc(true);
  collector_state()->set_initiate_conc_mark_if_possible(false);
}

static const char* requester_for_mixed_abort(GCCause::Cause cause) {
  if (cause == GCCause::_wb_breakpoint) {
    return "run_to breakpoint";
  } else if (GCCause::is_codecache_requested_gc(cause)) {
    return "codecache";
  } else {
    assert(G1CollectedHeap::heap()->is_user_requested_concurrent_full_gc(cause), "must be");
    return "user";
  }
}

void G1Policy::decide_on_concurrent_start_pause() {
  // We are about to decide on whether this pause will be a
  // concurrent start pause.

  // First, collector_state()->in_concurrent_start_gc() should not be already set. We
  // will set it here if we have to. However, it should be cleared by
  // the end of the pause (it's only set for the duration of a
  // concurrent start pause).
  assert(!collector_state()->in_concurrent_start_gc(), "pre-condition");

  // We should not be starting a concurrent start pause if the concurrent mark
  // thread is terminating.
  if (_g1h->concurrent_mark_is_terminating()) {
    return;
  }

  if (collector_state()->initiate_conc_mark_if_possible()) {
    // We had noticed on a previous pause that the heap occupancy has
    // gone over the initiating threshold and we should start a
    // concurrent marking cycle.  Or we've been explicitly requested
    // to start a concurrent marking cycle.  Either way, we initiate
    // one if not inhibited for some reason.

    GCCause::Cause cause = _g1h->gc_cause();
    if ((cause != GCCause::_wb_breakpoint) &&
        ConcurrentGCBreakpoints::is_controlled()) {
      log_debug(gc, ergo)("Do not initiate concurrent cycle (whitebox controlled)");
    } else if (!about_to_start_mixed_phase() && collector_state()->in_young_only_phase()) {
      // Initiate a new concurrent start if there is no marking or reclamation going on.
      initiate_conc_mark();
      log_debug(gc, ergo)("Initiate concurrent cycle (concurrent cycle initiation requested)");
    } else if (_g1h->is_user_requested_concurrent_full_gc(cause) ||
               GCCause::is_codecache_requested_gc(cause) ||
               (cause == GCCause::_wb_breakpoint)) {
      // Initiate a concurrent start.  A concurrent start must be a young only
      // GC, so the collector state must be updated to reflect this.
      collector_state()->set_in_young_only_phase(true);
      collector_state()->set_in_young_gc_before_mixed(false);

      // We might have ended up coming here about to start a mixed phase with a collection set
      // active. The following remark might change the change the "evacuation efficiency" of
      // the regions in this set, leading to failing asserts later.
      // Since the concurrent cycle will recreate the collection set anyway, simply drop it here.
      abandon_collection_set_candidates();
      abort_time_to_mixed_tracking();
      initiate_conc_mark();
      log_debug(gc, ergo)("Initiate concurrent cycle (%s requested concurrent cycle)",
                          requester_for_mixed_abort(cause));
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
  // Result consistency checks.
  // We do not allow concurrent start to be piggy-backed on a mixed GC.
  assert(!collector_state()->in_concurrent_start_gc() ||
         collector_state()->in_young_only_phase(), "sanity");
  // We also do not allow mixed GCs during marking.
  assert(!collector_state()->mark_or_rebuild_in_progress() || collector_state()->in_young_only_phase(), "sanity");
}

void G1Policy::record_concurrent_mark_cleanup_end(bool has_rebuilt_remembered_sets) {
  bool mixed_gc_pending = false;
  if (has_rebuilt_remembered_sets) {
    candidates()->sort_marking_by_efficiency();
    mixed_gc_pending = next_gc_should_be_mixed();
  }

  if (log_is_enabled(Trace, gc, liveness)) {
    G1PrintRegionLivenessInfoClosure cl("Post-Cleanup");
    _g1h->heap_region_iterate(&cl);
  }

  if (!mixed_gc_pending) {
    abort_time_to_mixed_tracking();
    log_debug(gc, ergo)("request young-only gcs (candidate old regions not available)");
  }
  collector_state()->set_in_young_gc_before_mixed(mixed_gc_pending);
  collector_state()->set_mark_or_rebuild_in_progress(false);
  collector_state()->set_clear_bitmap_in_progress(true);

  double end_sec = os::elapsedTime();
  double start_sec = cur_pause_start_sec();
  double elapsed_time_ms = (end_sec - start_sec) * 1000.0;
  _analytics->report_concurrent_mark_cleanup_times_ms(elapsed_time_ms);

  record_pause(G1GCPauseType::Cleanup, start_sec, end_sec);
}

void G1Policy::abandon_collection_set_candidates() {
  _collection_set->abandon_all_candidates();
}

void G1Policy::maybe_start_marking() {
  if (need_to_start_conc_mark("end of GC")) {
    // Note: this might have already been set, if during the last
    // pause we decided to start a cycle but at the beginning of
    // this pause we decided to postpone it. That's OK.
    collector_state()->set_initiate_conc_mark_if_possible(true);
  }
}

void G1Policy::update_gc_pause_time_ratios(G1GCPauseType gc_type, double start_time_sec, double end_time_sec) {

  double pause_time_sec = end_time_sec - start_time_sec;
  double pause_time_ms = pause_time_sec * 1000.0;

  _analytics->compute_pause_time_ratios(end_time_sec, pause_time_ms);
  _analytics->update_recent_gc_times(end_time_sec, pause_time_ms);

  if (gc_type == G1GCPauseType::Cleanup || gc_type == G1GCPauseType::Remark) {
    _analytics->append_prev_collection_pause_end_ms(pause_time_ms);
  } else {
    _analytics->set_prev_collection_pause_end_ms(end_time_sec * 1000.0);
  }
}

void G1Policy::record_pause(G1GCPauseType gc_type,
                            double start,
                            double end,
                            bool allocation_failure) {
  // Manage the MMU tracker. For some reason it ignores Full GCs.
  if (gc_type != G1GCPauseType::FullGC) {
    _mmu_tracker->add_pause(start, end);
  }

  if (!allocation_failure) {
    update_gc_pause_time_ratios(gc_type, start, end);
  }

  update_time_to_mixed_tracking(gc_type, start, end);
}

void G1Policy::update_time_to_mixed_tracking(G1GCPauseType gc_type,
                                             double start,
                                             double end) {
  // Manage the mutator time tracking from concurrent start to first mixed gc.
  switch (gc_type) {
    case G1GCPauseType::FullGC:
      abort_time_to_mixed_tracking();
      break;
    case G1GCPauseType::Cleanup:
    case G1GCPauseType::Remark:
    case G1GCPauseType::YoungGC:
    case G1GCPauseType::LastYoungGC:
      _concurrent_start_to_mixed.add_pause(end - start);
      break;
    case G1GCPauseType::ConcurrentStartMarkGC:
      // Do not track time-to-mixed time for periodic collections as they are likely
      // to be not representative to regular operation as the mutators are idle at
      // that time. Also only track full concurrent mark cycles.
      if (_g1h->gc_cause() != GCCause::_g1_periodic_collection) {
        _concurrent_start_to_mixed.record_concurrent_start_end(end);
      }
      break;
    case G1GCPauseType::ConcurrentStartUndoGC:
      assert(_g1h->gc_cause() == GCCause::_g1_humongous_allocation,
             "GC cause must be humongous allocation but is %d",
             _g1h->gc_cause());
      break;
    case G1GCPauseType::MixedGC:
      _concurrent_start_to_mixed.record_mixed_gc_start(start);
      break;
    default:
      ShouldNotReachHere();
  }
}

void G1Policy::abort_time_to_mixed_tracking() {
  _concurrent_start_to_mixed.reset();
}

bool G1Policy::next_gc_should_be_mixed() const {
  // Mixed GCs should continue until marking candidates are completely consumed.
  return candidates()->has_more_marking_candidates();
}

size_t G1Policy::allowed_waste_in_collection_set() const {
  return G1HeapWastePercent * _g1h->capacity() / 100;
}

uint G1Policy::min_retained_old_cset_length() const {
  // Guarantee some progress with retained regions regardless of available time by
  // taking at least one region.
  return 1;
}

uint G1Policy::calc_min_old_cset_length(uint num_candidate_regions) const {
  // The min old CSet region bound is based on the maximum desired
  // number of mixed GCs after a cycle. I.e., even if some old regions
  // look expensive, we should add them to the CSet anyway to make
  // sure we go through the available old regions in no more than the
  // maximum desired number of mixed GCs.
  //
  // The calculation is based on the number of marked regions we added
  // to the CSet candidates in the first place, not how many remain, so
  // that the result is the same during all mixed GCs that follow a cycle.
  const size_t gc_num = MAX2((size_t)G1MixedGCCountTarget, (size_t)1);
  // Round up to be conservative.
  return (uint)ceil((double)num_candidate_regions / gc_num);
}

uint G1Policy::calc_max_old_cset_length() const {
  // The max old CSet region bound is based on the threshold expressed
  // as a percentage of the heap size. I.e., it should bound the
  // number of old regions added to the CSet irrespective of how many
  // of them are available.
  double result = (double)_g1h->num_committed_regions() * G1OldCSetRegionThresholdPercent / 100;
  // Round up to be conservative.
  return (uint)ceil(result);
}

void G1Policy::transfer_survivors_to_cset(const G1SurvivorRegions* survivors) {
  start_adding_survivor_regions();

  for (GrowableArrayIterator<G1HeapRegion*> it = survivors->regions()->begin();
       it != survivors->regions()->end();
       ++it) {
    G1HeapRegion* curr = *it;
    set_region_survivor(curr);

    // The region is a non-empty survivor so let's add it to
    // the incremental collection set for the next evacuation
    // pause.
    _collection_set->add_survivor_regions(curr);
  }
  stop_adding_survivor_regions();

  // Don't clear the survivor list handles until the start of
  // the next evacuation pause - we need it in order to re-tag
  // the survivor regions from this evacuation pause as 'young'
  // at the start of the next.
}
