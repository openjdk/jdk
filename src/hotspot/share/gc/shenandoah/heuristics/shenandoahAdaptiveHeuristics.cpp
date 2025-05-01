/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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


#include "gc/shared/gcCause.hpp"
#include "gc/shenandoah/heuristics/shenandoahAdaptiveHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahSpaceInfo.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "runtime/globals.hpp"
#include "utilities/quickSort.hpp"

// These constants are used to adjust the margin of error for the moving
// average of the allocation rate and cycle time. The units are standard
// deviations.
const double ShenandoahAdaptiveHeuristics::FULL_PENALTY_SD = 0.2;
const double ShenandoahAdaptiveHeuristics::DEGENERATE_PENALTY_SD = 0.1;

// These are used to decide if we want to make any adjustments at all
// at the end of a successful concurrent cycle.
const double ShenandoahAdaptiveHeuristics::LOWEST_EXPECTED_AVAILABLE_AT_END = -0.5;
const double ShenandoahAdaptiveHeuristics::HIGHEST_EXPECTED_AVAILABLE_AT_END = 0.5;

// These values are the confidence interval expressed as standard deviations.
// At the minimum confidence level, there is a 25% chance that the true value of
// the estimate (average cycle time or allocation rate) is not more than
// MINIMUM_CONFIDENCE standard deviations away from our estimate. Similarly, the
// MAXIMUM_CONFIDENCE interval here means there is a one in a thousand chance
// that the true value of our estimate is outside the interval. These are used
// as bounds on the adjustments applied at the outcome of a GC cycle.
const double ShenandoahAdaptiveHeuristics::MINIMUM_CONFIDENCE = 0.319; // 25%
const double ShenandoahAdaptiveHeuristics::MAXIMUM_CONFIDENCE = 3.291; // 99.9%

ShenandoahAdaptiveHeuristics::ShenandoahAdaptiveHeuristics(ShenandoahSpaceInfo* space_info) :
  ShenandoahHeuristics(space_info),
  _margin_of_error_sd(ShenandoahAdaptiveInitialConfidence),
  _spike_threshold_sd(ShenandoahAdaptiveInitialSpikeThreshold),
  _last_trigger(OTHER),
  _available(Moving_Average_Samples, ShenandoahAdaptiveDecayFactor),
  _words_most_recently_evacuated(0),
  _anticipated_mark_words(0),
  _anticipated_evac_words(0),
  _anticipated_update_words(0) { }

ShenandoahAdaptiveHeuristics::~ShenandoahAdaptiveHeuristics() {}

void ShenandoahAdaptiveHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                                         RegionData* data, size_t size,
                                                                         size_t actual_free) {
  size_t garbage_threshold = ShenandoahHeapRegion::region_size_bytes() * ShenandoahGarbageThreshold / 100;

  // The logic for cset selection in adaptive is as follows:
  //
  //   1. We cannot get cset larger than available free space. Otherwise we guarantee OOME
  //      during evacuation, and thus guarantee full GC. In practice, we also want to let
  //      application to allocate something. This is why we limit CSet to some fraction of
  //      available space. In non-overloaded heap, max_cset would contain all plausible candidates
  //      over garbage threshold.
  //
  //   2. We should not get cset too low so that free threshold would not be met right
  //      after the cycle. Otherwise we get back-to-back cycles for no reason if heap is
  //      too fragmented. In non-overloaded non-fragmented heap min_garbage would be around zero.
  //
  // Therefore, we start by sorting the regions by garbage. Then we unconditionally add the best candidates
  // before we meet min_garbage. Then we add all candidates that fit with a garbage threshold before
  // we hit max_cset. When max_cset is hit, we terminate the cset selection. Note that in this scheme,
  // ShenandoahGarbageThreshold is the soft threshold which would be ignored until min_garbage is hit.

  size_t capacity    = _space_info->soft_max_capacity();
  size_t max_cset    = (size_t)((1.0 * capacity / 100 * ShenandoahEvacReserve) / ShenandoahEvacWaste);
  size_t free_target = (capacity / 100 * ShenandoahMinFreeThreshold) + max_cset;
  size_t min_garbage = (free_target > actual_free ? (free_target - actual_free) : 0);

  log_info(gc, ergo)("Adaptive CSet Selection. Target Free: %zu%s, Actual Free: "
                     "%zu%s, Max Evacuation: %zu%s, Min Garbage: %zu%s",
                     byte_size_in_proper_unit(free_target), proper_unit_for_byte_size(free_target),
                     byte_size_in_proper_unit(actual_free), proper_unit_for_byte_size(actual_free),
                     byte_size_in_proper_unit(max_cset),    proper_unit_for_byte_size(max_cset),
                     byte_size_in_proper_unit(min_garbage), proper_unit_for_byte_size(min_garbage));

  // Better select garbage-first regions
  QuickSort::sort(data, size, compare_by_garbage);

  size_t cur_cset = 0;
  size_t cur_garbage = 0;

  for (size_t idx = 0; idx < size; idx++) {
    ShenandoahHeapRegion* r = data[idx].get_region();

    size_t new_cset    = cur_cset + r->get_live_data_bytes();
    size_t new_garbage = cur_garbage + r->garbage();

    if (new_cset > max_cset) {
      break;
    }

    if ((new_garbage < min_garbage) || (r->garbage() > garbage_threshold)) {
      cset->add_region(r);
      cur_cset = new_cset;
      cur_garbage = new_garbage;
    }
  }
}

#undef KELVIN_DEVELOPMENT
#ifdef KELVIN_DEVELOPMENT
const char* major_phase_name(ShenandoahMajorGCPhase stage) {
  switch (stage) {
    case ShenandoahMajorGCPhase::_num_phases:
      return "<num-phases>";
    case ShenandoahMajorGCPhase::_final_roots:
      return "final_roots";
    case ShenandoahMajorGCPhase::_mark:
      return "mark";
    case ShenandoahMajorGCPhase::_evac:
      return "evac";
    case ShenandoahMajorGCPhase::_update:
      return "update";
    default:
      return "<unknown>";
  }
}
#endif
#undef KELVIN_DEVELOPMENT

void ShenandoahAdaptiveHeuristics::record_cycle_start() {
  ShenandoahHeuristics::record_cycle_start();
  _allocation_rate.allocation_counter_reset();
  double now = os::elapsedTime();
#undef KELVIN_MARK
#ifdef KELVIN_MARK
  log_info(gc)("record_cycle_start(), most recent _mark start time: %.6f", now);
#endif
  _phase_stats[ShenandoahMajorGCPhase::_mark].set_most_recent_start_time(now);
}

void ShenandoahAdaptiveHeuristics::record_success_concurrent() {
  ShenandoahHeuristics::record_success_concurrent();

  size_t available = _space_info->available();

  double z_score = 0.0;
  double available_sd = _available.sd();
  if (available_sd > 0) {
    double available_avg = _available.avg();
    z_score = (double(available) - available_avg) / available_sd;
    log_debug(gc, ergo)("Available: %zu %sB, z-score=%.3f. Average available: %.1f %sB +/- %.1f %sB.",
                        byte_size_in_proper_unit(available), proper_unit_for_byte_size(available),
                        z_score,
                        byte_size_in_proper_unit(available_avg), proper_unit_for_byte_size(available_avg),
                        byte_size_in_proper_unit(available_sd), proper_unit_for_byte_size(available_sd));
  }

  _available.add(double(available));

  // In the case when a concurrent GC cycle completes successfully but with an
  // unusually small amount of available memory we will adjust our trigger
  // parameters so that they are more likely to initiate a new cycle.
  // Conversely, when a GC cycle results in an above average amount of available
  // memory, we will adjust the trigger parameters to be less likely to initiate
  // a GC cycle.
  //
  // The z-score we've computed is in no way statistically related to the
  // trigger parameters, but it has the nice property that worse z-scores for
  // available memory indicate making larger adjustments to the trigger
  // parameters. It also results in fewer adjustments as the application
  // stabilizes.
  //
  // In order to avoid making endless and likely unnecessary adjustments to the
  // trigger parameters, the change in available memory (with respect to the
  // average) at the end of a cycle must be beyond these threshold values.
  if (z_score < LOWEST_EXPECTED_AVAILABLE_AT_END ||
      z_score > HIGHEST_EXPECTED_AVAILABLE_AT_END) {
    // The sign is flipped because a negative z-score indicates that the
    // available memory at the end of the cycle is below average. Positive
    // adjustments make the triggers more sensitive (i.e., more likely to fire).
    // The z-score also gives us a measure of just how far below normal. This
    // property allows us to adjust the trigger parameters proportionally.
    //
    // The `100` here is used to attenuate the size of our adjustments. This
    // number was chosen empirically. It also means the adjustments at the end of
    // a concurrent cycle are an order of magnitude smaller than the adjustments
    // made for a degenerated or full GC cycle (which themselves were also
    // chosen empirically).
    adjust_last_trigger_parameters(z_score / -100);
  }
}

void ShenandoahAdaptiveHeuristics::record_success_degenerated() {
  ShenandoahHeuristics::record_success_degenerated();
  // Adjust both trigger's parameters in the case of a degenerated GC because
  // either of them should have triggered earlier to avoid this case.
  adjust_margin_of_error(DEGENERATE_PENALTY_SD);
  adjust_spike_threshold(DEGENERATE_PENALTY_SD);
}

void ShenandoahAdaptiveHeuristics::record_success_full() {
  ShenandoahHeuristics::record_success_full();
  // Adjust both trigger's parameters in the case of a full GC because
  // either of them should have triggered earlier to avoid this case.
  adjust_margin_of_error(FULL_PENALTY_SD);
  adjust_spike_threshold(FULL_PENALTY_SD);
}

static double saturate(double value, double min, double max) {
  return MAX2(MIN2(value, max), min);
}

//  Rationale:
//    The idea is that there is an average allocation rate and there are occasional abnormal bursts (or spikes) of
//    allocations that exceed the average allocation rate.  What do these spikes look like?
//
//    1. At certain phase changes, we may discard large amounts of data and replace it with large numbers of newly
//       allocated objects.  This "spike" looks more like a phase change.  We were in steady state at M bytes/sec
//       allocation rate and now we're in a "reinitialization phase" that looks like N bytes/sec.  We need the "spike"
//       accommodation to give us enough runway to recalibrate our "average allocation rate".
//
//   2. The typical workload changes.  "Suddenly", our typical workload of N TPS increases to N+delta TPS.  This means
//       our average allocation rate needs to be adjusted.  Once again, we need the "spike" accomodation to give us
//       enough runway to recalibrate our "average allocation rate".
//
//    3. Though there is an "average" allocation rate, a given workload's demand for allocation may be very bursty.  We
//       allocate a bunch of LABs during the 5 ms that follow completion of a GC, then we perform no more allocations for
//       the next 150 ms.  It seems we want the "spike" to represent the maximum divergence from average within the
//       period of time between consecutive evaluation of the should_start_gc() service.  Here's the thinking:
//
//       a) Between now and the next time I ask whether should_start_gc(), we might experience a spike representing
//          the anticipated burst of allocations.  If that would put us over budget, then we should start GC immediately.
//       b) Between now and the anticipated depletion of allocation pool, there may be two or more bursts of allocations.
//          If there are more than one of these bursts, we can "approximate" that these will be separated by spans of
//          time with very little or no allocations so the "average" allocation rate should be a suitable approximation
//          of how this will behave.
//
//    For cases 1 and 2, we need to "quickly" recalibrate the average allocation rate whenever we detect a change
//    in operation mode.  We want some way to decide that the average rate has changed, while keeping average
//    allocation rate computation independent.
bool ShenandoahAdaptiveHeuristics::should_start_gc() {
  size_t capacity = _space_info->soft_max_capacity();
  size_t available = _space_info->soft_available();
  size_t allocated = _space_info->bytes_allocated_since_gc_start();

  log_debug(gc)("should_start_gc? available: %zu, soft_max_capacity: %zu"
                ", allocated: %zu", available, capacity, allocated);

  if (_start_gc_is_pending) {
    log_trigger("GC start is already pending");
    return true;
  }

  // Track allocation rate even if we decide to start a cycle for other reasons.
  double rate = _allocation_rate.sample(allocated);
  _last_trigger = OTHER;

  size_t min_threshold = min_free_threshold();
  if (available < min_threshold) {
    log_trigger("Free (%zu%s) is below minimum threshold (%zu%s)",
                 byte_size_in_proper_unit(available), proper_unit_for_byte_size(available),
                 byte_size_in_proper_unit(min_threshold), proper_unit_for_byte_size(min_threshold));
    accept_trigger_with_type(OTHER);
    return true;
  }

  // Check if we need to learn a bit about the application
  const size_t max_learn = ShenandoahLearningSteps;
  if (_gc_times_learned < max_learn) {
    size_t init_threshold = capacity / 100 * ShenandoahInitFreeThreshold;
    if (available < init_threshold) {
      log_trigger("Learning %zu of %zu. Free (%zu%s) is below initial threshold (%zu%s)",
                   _gc_times_learned + 1, max_learn,
                   byte_size_in_proper_unit(available), proper_unit_for_byte_size(available),
                   byte_size_in_proper_unit(init_threshold), proper_unit_for_byte_size(init_threshold));
      accept_trigger_with_type(OTHER);
      return true;
    }
  }
  // Check if allocation headroom is still okay. This also factors in:
  //   1. Some space to absorb allocation spikes (ShenandoahAllocSpikeFactor)
  //   2. Accumulated penalties from Degenerated and Full GC
  size_t allocation_headroom = available;

  size_t spike_headroom = capacity / 100 * ShenandoahAllocSpikeFactor;
  size_t penalties      = capacity / 100 * _gc_time_penalties;

  allocation_headroom -= MIN2(allocation_headroom, spike_headroom);
  allocation_headroom -= MIN2(allocation_headroom, penalties);

  double avg_cycle_time = _gc_cycle_time_history->davg() + (_margin_of_error_sd * _gc_cycle_time_history->dsd());
  double avg_alloc_rate = _allocation_rate.upper_bound(_margin_of_error_sd);

  log_debug(gc)("average GC time: %.2f ms, allocation rate: %.0f %s/s",
          avg_cycle_time * 1000, byte_size_in_proper_unit(avg_alloc_rate), proper_unit_for_byte_size(avg_alloc_rate));
  if (avg_cycle_time * avg_alloc_rate > allocation_headroom) {
    log_trigger("Average GC time (%.2f ms) is above the time for average allocation rate (%.0f %sB/s)"
                 " to deplete free headroom (%zu%s) (margin of error = %.2f)",
                 avg_cycle_time * 1000,
                 byte_size_in_proper_unit(avg_alloc_rate), proper_unit_for_byte_size(avg_alloc_rate),
                 byte_size_in_proper_unit(allocation_headroom), proper_unit_for_byte_size(allocation_headroom),
                 _margin_of_error_sd);
    log_info(gc, ergo)("Free headroom: %zu%s (free) - %zu%s (spike) - %zu%s (penalties) = %zu%s",
                       byte_size_in_proper_unit(available),           proper_unit_for_byte_size(available),
                       byte_size_in_proper_unit(spike_headroom),      proper_unit_for_byte_size(spike_headroom),
                       byte_size_in_proper_unit(penalties),           proper_unit_for_byte_size(penalties),
                       byte_size_in_proper_unit(allocation_headroom), proper_unit_for_byte_size(allocation_headroom));
    accept_trigger_with_type(RATE);
    return true;
  }

  bool is_spiking = _allocation_rate.is_spiking(rate, _spike_threshold_sd);
  if (is_spiking && avg_cycle_time > allocation_headroom / rate) {
    log_trigger("Average GC time (%.2f ms) is above the time for instantaneous allocation rate (%.0f %sB/s) to deplete free headroom (%zu%s) (spike threshold = %.2f)",
                 avg_cycle_time * 1000,
                 byte_size_in_proper_unit(rate), proper_unit_for_byte_size(rate),
                 byte_size_in_proper_unit(allocation_headroom), proper_unit_for_byte_size(allocation_headroom),
                 _spike_threshold_sd);
    accept_trigger_with_type(SPIKE);
    return true;
  }

  if (ShenandoahHeuristics::should_start_gc()) {
    _start_gc_is_pending = true;
    return true;
  } else {
    return false;
  }
}

void ShenandoahAdaptiveHeuristics::adjust_last_trigger_parameters(double amount) {
  switch (_last_trigger) {
    case RATE:
      adjust_margin_of_error(amount);
      break;
    case SPIKE:
      adjust_spike_threshold(amount);
      break;
    case OTHER:
      // nothing to adjust here.
      break;
    default:
      ShouldNotReachHere();
  }
}

void ShenandoahAdaptiveHeuristics::adjust_margin_of_error(double amount) {
  _margin_of_error_sd = saturate(_margin_of_error_sd + amount, MINIMUM_CONFIDENCE, MAXIMUM_CONFIDENCE);
  log_debug(gc, ergo)("Margin of error now %.2f", _margin_of_error_sd);
}

void ShenandoahAdaptiveHeuristics::adjust_spike_threshold(double amount) {
  _spike_threshold_sd = saturate(_spike_threshold_sd - amount, MINIMUM_CONFIDENCE, MAXIMUM_CONFIDENCE);
  log_debug(gc, ergo)("Spike threshold now: %.2f", _spike_threshold_sd);
}

size_t ShenandoahAdaptiveHeuristics::min_free_threshold() {
  // Note that soft_max_capacity() / 100 * min_free_threshold is smaller than max_capacity() / 100 * min_free_threshold.
  // We want to behave conservatively here, so use max_capacity().  By returning a larger value, we cause the GC to
  // trigger when the remaining amount of free shrinks below the larger threshold.
  return _space_info->max_capacity() / 100 * ShenandoahMinFreeThreshold;
}

double ShenandoahAdaptiveHeuristics::predict_gc_time() {
  size_t mark_words = get_anticipated_mark_words();
  if (mark_words == 0) {
    // Use other heuristics to trigger.
    return 0.0;
  }
  double mark_time = predict_mark_time(mark_words);
  double evac_time = predict_evac_time(get_anticipated_evac_words());
  double update_time = predict_update_time(get_anticipated_update_words());
  double result = mark_time + evac_time + update_time;
#undef KELVIN_DEBUG
#ifdef KELVIN_DEBUG
  log_info(gc)("AddaptiveHeuristics::predicting gc time: %.3f from mark(%zu): %.3f, evac(%zu): %.3f, update(%zu): %.3f",
	       result, get_anticipated_mark_words(), mark_time, get_anticipated_evac_words(),
	       evac_time, get_anticipated_update_words(), update_time);
#endif
  return result;
}

// Marking effort is assumed to be a function of "time".  During steady state, marking efforts should be constant.  During
// initialization, marking may increase linearly as data is retained for promotion.
void ShenandoahAdaptiveHeuristics::record_mark_end(double now, size_t marked_words) {
  // mark will be followed by evac or final_roots, we're not sure which
  _phase_stats[ShenandoahMajorGCPhase::_evac].set_most_recent_start_time(now);
  _phase_stats[ShenandoahMajorGCPhase::_final_roots].set_most_recent_start_time(now);
  if (_surge_level == 0) {
    double start_phase_time = _phase_stats[ShenandoahMajorGCPhase::_mark].get_most_recent_start_time();
    double duration = now - start_phase_time;
#undef KELVIN_MARK
#ifdef KELVIN_MARK
    log_info(gc)("Recording duration of _mark phase: %.6f for %zu words young live and surge level: %d",
                 duration, marked_words, _surge_level);
#endif
    record_phase_duration(ShenandoahMajorGCPhase::_mark, (double) marked_words, duration);
  }
}

// Evacuation effort is assumed to be a function of words evacuated or promoted in place.  In non-generational mode,
// use promoted_in_place_words equal zero.
void ShenandoahAdaptiveHeuristics::record_evac_end(double now, size_t evacuated_words, size_t promoted_in_place_words) {
  // evac will be followed by update
  _phase_stats[ShenandoahMajorGCPhase::_update].set_most_recent_start_time(now);
  if (_surge_level == 0) {
    double start_phase_time = _phase_stats[ShenandoahMajorGCPhase::_evac].get_most_recent_start_time();
    double duration = now - start_phase_time;
#ifdef KELVIN_DEVELOPMENT
    log_info(gc)("Recording duration of _evac phase with (evacuated: %zu, promoted in place: %zu): %.3f with surge level: %d",
                 evacuated_words, promoted_in_place_words, duration, _surge_level);
#endif
    // Evacuation time is a linear function of both evacuated_words and promoted_in_place_words.  Analysis of selected
    // (not exhaustive) experiments shows that the proportionality constant for evacuated_words is 5 times larger than
    // the proportionality constant for promoted_in_place_words.  This was determined by first analyzing multiple results
    // for which promoted_in_place_words equals zero to first determine the proportionality constant for evacuated_words,
    // and then feeding that result into the analysis of proportionality constant for promoted_in_place_words.  Our current
    // thoughts are that analyzing two-dimensional linear equations in real time is not practical.  Instead, we convert this
    // into a one-dimenstional problem by assuming a 5:1 ratio between the two dependencies.
    record_phase_duration(ShenandoahMajorGCPhase::_evac, (double)(5 * evacuated_words + promoted_in_place_words), duration);
  }
}

// Update effort is assumed to be a function of live words updated.  For young collection, this is number of live words
// in young at start of evac that are not residing within the cset.  This does not include the old-gen words that are
// updated from remset.  That component is assumed to remain approximately constant and negligible, and will be accounted
// in the y-intercept.  For mixed collections, this is the number of live words in young and old at start of evac (excluding cset).
//
// TODO: do i need better accounting for remset updates?  As is, I am underestimating mixed updates because the delta of updated
// words between young and mixed appears larger than it actually is.  But if I account better, then how do I predict update
// word count for a young collection?
void ShenandoahAdaptiveHeuristics::record_update_end(double now, size_t updated_words) {
  if (_surge_level == 0) {
    double start_phase_time = _phase_stats[ShenandoahMajorGCPhase::_update].get_most_recent_start_time();
    double duration = now - start_phase_time;
#ifdef KELVIN_DEVELOPMENT
    log_info(gc)("Recording duration of _update phase with (updated_words: %zu): %.3f with surge_level: %d",
                 updated_words, duration, _surge_level);
#endif
    record_phase_duration(ShenandoahMajorGCPhase::_update, (double) updated_words, duration);
  }
}

// Final roots is assumed to be a function of pip_words.  For non-generational mode, use zero.
void ShenandoahAdaptiveHeuristics::record_final_roots_end(double now, size_t promoted_in_place_words) {
  if (_surge_level == 0) {
    double start_phase_time = _phase_stats[ShenandoahMajorGCPhase::_final_roots].get_most_recent_start_time();
    double duration = now - start_phase_time;
#ifdef KELVIN_DEVELOPMENT
    log_info(gc)("Recording duration of _final_roots phase with (pip_words: %zu): %.3f with surge_level: %d",
                 promoted_in_place_words, duration, _surge_level);
#endif
    record_phase_duration(ShenandoahMajorGCPhase::_final_roots, (double) promoted_in_place_words, duration);
  }
}

double ShenandoahAdaptiveHeuristics::predict_mark_time(size_t anticipated_marked_words) {
  return _phase_stats[ShenandoahMajorGCPhase::_mark].predict_at((double) anticipated_marked_words);
}

double ShenandoahAdaptiveHeuristics::predict_evac_time(size_t anticipated_evac_words) {
  return _phase_stats[ShenandoahMajorGCPhase::_evac].predict_at((double) (5 * anticipated_evac_words));
}

double ShenandoahAdaptiveHeuristics::predict_update_time(size_t anticipated_update_words) {
  return _phase_stats[ShenandoahMajorGCPhase::_update].predict_at((double) anticipated_update_words);
}

double ShenandoahAdaptiveHeuristics::predict_final_roots_time() {
  return _phase_stats[ShenandoahMajorGCPhase::_final_roots].predict_at((double) 0.0);
}

uint ShenandoahAdaptiveHeuristics::should_surge_phase(ShenandoahMajorGCPhase phase, double now) {
  _phase_stats[phase].set_most_recent_start_time(now);

  // If we're already surging within this cycle, do not reduce the surge level
  uint surge = _surge_level;
  size_t allocatable = ShenandoahHeap::heap()->free_set()->available();
  double time_to_finish_gc = 0.0;

#undef KELVIN_SURGE
#ifdef KELVIN_SURGE
  log_info(gc)("should_surge(), inherited surge_level %u, allocatable: %zu", surge, allocatable);
#endif

#ifdef KELVIN_DEPRECATE
  if (_previous_cycle_max_surge_level > Min_Surge_Level) {
    // If we required more than minimal surge in previous cycle, continue with a small surge now.  Assume we're catching up.
    if (surge < Min_Surge_Level) {
      surge = Min_Surge_Level;
    }
  }
#endif
  
  size_t bytes_allocated = _space_info->bytes_allocated_since_gc_start();
  _phase_stats[phase].set_most_recent_bytes_allocated(bytes_allocated);
  double avg_alloc_rate = _allocation_rate.average_rate(_margin_of_error_sd);
  double alloc_rate = avg_alloc_rate;

#ifdef KELVIN_SURGE
  log_info(gc)(" bytes_allocated: %zu, avg_alloc_rate: %.3f MB/s, _margin_of_error_sd: %.3f",
	       bytes_allocated, alloc_rate / (1024 * 1024), _margin_of_error_sd);
#endif

  double predicted_gc_time = 0.0;
  switch (phase) {
    case ShenandoahMajorGCPhase::_num_phases:
      assert(false, "Should not happen");
      break;
    case ShenandoahMajorGCPhase::_final_roots:
    {
      // May happen after _mark in case this is an abbreviated cycle
      time_to_finish_gc += predict_final_roots_time();

      // final_roots is preceded by mark, no evac or update
      size_t allocated_since_last_sample = bytes_allocated;
      double time_since_last_sample = now - _phase_stats[_mark].get_most_recent_start_time();

      double alloc_rate_since_gc_start = allocated_since_last_sample / time_since_last_sample;
      if (alloc_rate_since_gc_start > alloc_rate) {
        alloc_rate = alloc_rate_since_gc_start;
#ifdef KELVIN_SURGE
        log_info(gc)(" increasing alloc rate to %.3f MB/s in final_roots: %zu / %.3f",
                     alloc_rate / (1024 * 1024), bytes_allocated, now - _phase_stats[_mark].get_most_recent_start_time());
#endif
      }
    }
    break;

    case ShenandoahMajorGCPhase::_mark:
    {
      // If this is the start of a new GC cycle, reset default surge to 0.
      surge = 0;
      time_to_finish_gc += predict_mark_time((double) get_anticipated_mark_words());
      // TODO: Use the larger of predict_gc_time(now) and avg_cycle_time if we integrate "accelerated triggers"
      double avg_cycle_time = _gc_cycle_time_history->davg() + (_margin_of_error_sd * _gc_cycle_time_history->dsd());
      predicted_gc_time = predict_gc_time();
#ifdef KELVIN_SURGE
      log_info(gc)(" avg_cycle_time: %.3f, predicted_cycle_time: %.3f, time_to_finish_mark: %.3f",
                   avg_cycle_time, predicted_gc_time, time_to_finish_gc);
#endif
      if (avg_cycle_time > predicted_gc_time) {
        predicted_gc_time = avg_cycle_time;
      }
    }
    case ShenandoahMajorGCPhase::_evac:
    {
      if (phase == _evac) {
        size_t allocated_since_last_sample = bytes_allocated;
        double time_since_last_sample = now - _phase_stats[_mark].get_most_recent_start_time();
        double alloc_rate_since_gc_start = allocated_since_last_sample / time_since_last_sample;
        if (alloc_rate_since_gc_start > alloc_rate) {
          alloc_rate = alloc_rate_since_gc_start;
#ifdef KELVIN_SURGE
          log_info(gc)(" increasing alloc rate to %.3f MB/s in evac: %zu / %.3f",
                       alloc_rate / (1024 * 1024), bytes_allocated, now - _phase_stats[_mark].get_most_recent_start_time());
#endif
      	}
      }
      time_to_finish_gc += predict_evac_time(get_anticipated_evac_words());
#ifdef KELVIN_SURGE
      log_info(gc)(" with evac, time_to_finish_gc: %.3f", time_to_finish_gc);
#endif
    }
    case ShenandoahMajorGCPhase::_update:
    {
      if (phase == _update) {
        size_t allocated_since_last_sample = bytes_allocated - _phase_stats[_evac].get_most_recent_bytes_allocated();
        double time_since_last_sample = now - _phase_stats[_evac].get_most_recent_start_time();
        double alloc_rate_since_evac_start = allocated_since_last_sample / time_since_last_sample;
        if (alloc_rate_since_evac_start > alloc_rate) {
          alloc_rate = alloc_rate_since_evac_start;
#ifdef KELVIN_SURGE
          log_info(gc)(" increasing alloc rate to %.3f MB/s in update since evac: %zu / %.3f",
                       alloc_rate / (1024 * 1024), bytes_allocated - _phase_stats[_evac].get_most_recent_bytes_allocated(),
                       (now - _phase_stats[_evac].get_most_recent_start_time()));
#endif
        }
        allocated_since_last_sample = bytes_allocated;
        time_since_last_sample = now - _phase_stats[_mark].get_most_recent_start_time();
        double alloc_rate_since_gc_start = allocated_since_last_sample / time_since_last_sample;
        if (alloc_rate_since_gc_start > alloc_rate) {
          alloc_rate = alloc_rate_since_gc_start;
#ifdef KELVIN_SURGE
          log_info(gc)(" increasing alloc rate to %.3f MB/s in update since mark: %zu / %.3f",
                       alloc_rate / (1024 * 1024), bytes_allocated, now - _phase_stats[_mark].get_most_recent_start_time());
#endif
        }
      }
      time_to_finish_gc += predict_update_time(get_anticipated_update_words());
#ifdef KELVIN_SURGE
      log_info(gc)(" with update, time_to_finish_gc: %.3f", time_to_finish_gc);
#endif
    }
  }

  if (surge == Max_Surge_Level) {
    // Even if surge is already max, we need to do the above to update _phase_stats.  But no need to do acceleration
    // computations if we're already at max surge level.
    return surge;
  }

  if (time_to_finish_gc < predicted_gc_time) {
    time_to_finish_gc = predicted_gc_time;
  }

  double avg_odds;
  if (allocatable == 0) {
    // Avoid divide by zero, and force high surge if we are out of memory
    avg_odds = 1000.0;
  } else {
    avg_odds = (alloc_rate * time_to_finish_gc) / allocatable;
  }

#ifdef KELVIN_NEEDS_WORK
  // we don't have acceleration history in this branch
  if ((now - _previous_allocation_timestamp) >= MINIMUM_ALLOC_RATE_SAMPLE_INTERVAL) {
    size_t words_allocated_since_last_sample = _freeset->get_mutator_allocations_since_previous_sample();
    double time_since_last_sample = now - _previous_allocation_timestamp;
    double instantaneous_rate_words_per_second = words_allocated_since_last_sample / time_since_last_sample;
    _previous_allocation_timestamp = now;

#ifdef KELVIN_SURGE
    log_info(gc)("should_surge_gc()?, time_to_finish_gc: %0.3f, words_allocated_since_last_sample: %zu, "
                 "instantaneous_rate: %0.3f MB/s",
                 time_to_finish_gc, words_allocated_since_last_sample,
                 (instantaneous_rate_words_per_second * HeapWordSize) / (1024 * 1024));
#endif
    add_rate_to_acceleration_history(now, instantaneous_rate_words_per_second);
  }
#endif
#ifdef KELVIN_SURGE
  log_info(gc)(" avg_odds: %.3f", avg_odds);
#endif

#ifdef KELVIN_NEEDS_WORK
  // we don't have accelerated_consumption in this branch
  double race_odds;
  if (_spike_acceleration_num_samples > 0) {
    double acceleration;
    double current_rate;
 
   size_t consumption_accelerated = accelerated_consumption(acceleration, current_rate,
                                                             avg_alloc_rate / HeapWordSize, time_to_finish_gc);
    consumption_accelerated *= HeapWordSize;
    double accelerated_odds;
    if (allocatable == 0) {
      // Avoid divide by zero, and force high surge if we are out of memory
      accelerated_odds = 1000.0;
    } else {
      accelerated_odds = ((double) consumption_accelerated) / allocatable;
    }
#ifdef KELVIN_SURGE
    log_info(gc)("should_surge() current rate: %.3f MB/s, acceleration: %.3f MB/s/s, "
                 "consumption_accelerated: %zu, allocatable: %zu, avg_odds: %.3f, accelerated_odds: %.3f",
                 (HeapWordSize * current_rate) / (1024 * 1024), (HeapWordSize * acceleration) / (1024 * 1024),
                 consumption_accelerated, allocatable, avg_odds, accelerated_odds);
#endif
    race_odds = MAX2(avg_odds, accelerated_odds);
  } else {
    race_odds = avg_odds;
  }
#endif

  uint candidate_surge = (avg_odds > 1.0)? (uint) ((avg_odds - 0.75) / 0.25): 0;
  if (candidate_surge > Max_Surge_Level) {
    candidate_surge = Max_Surge_Level;
  }
  if (ConcGCThreads * (1 + candidate_surge * 0.25) > ParallelGCThreads) {
    candidate_surge = (uint) (((((double) ParallelGCThreads) / ConcGCThreads) - 1.0) / 0.25);
  }
  if (candidate_surge > surge) {
    surge = candidate_surge;
  }

#ifdef KELVIN_SURGE
  const char* phase_name = major_phase_name(phase);
  log_info(gc)("ShouldSurge(%s), allocatable: %zu, alloc_rate: %.3f MB/s, time_to_finish_gc: %.3fs, avg_odds: %.3f returns %u",
               phase_name, allocatable, alloc_rate / (1024 * 1024), time_to_finish_gc, avg_odds, surge);
#endif
  _surge_level = surge;
  if ((phase == ShenandoahMajorGCPhase::_update) || (phase == ShenandoahMajorGCPhase::_final_roots)) {
    _previous_cycle_max_surge_level = surge;
  }
  return surge;
}

ShenandoahAllocationRate::ShenandoahAllocationRate() :
  _last_sample_time(os::elapsedTime()),
  _last_sample_value(0),
  _interval_sec(1.0 / ShenandoahAdaptiveSampleFrequencyHz),
  _rate(int(ShenandoahAdaptiveSampleSizeSeconds * ShenandoahAdaptiveSampleFrequencyHz), ShenandoahAdaptiveDecayFactor),
  _rate_avg(int(ShenandoahAdaptiveSampleSizeSeconds * ShenandoahAdaptiveSampleFrequencyHz), ShenandoahAdaptiveDecayFactor) {
}

double ShenandoahAllocationRate::average_rate(double sds) const {
  double avg = _rate_avg.avg();
  double computed_sd = _rate_avg.sd();
#ifdef KELVIN_DEVELOPMENT
  log_info(gc)("average_rate computed from prediction: %.3f, computed_sd: %.3f, sds: %.3f, result: %.3f",
	       avg, computed_sd, sds, avg + (sds * computed_sd));
#endif
  return avg + (sds * computed_sd);
}

double ShenandoahAllocationRate::sample(size_t allocated) {
  double now = os::elapsedTime();
  double rate = 0.0;
  if (now - _last_sample_time > _interval_sec) {
    if (allocated >= _last_sample_value) {
      rate = instantaneous_rate(now, allocated);
      _rate.add(rate);
      _rate_avg.add(_rate.avg());
    }

    _last_sample_time = now;
    _last_sample_value = allocated;
  }
  return rate;
}

double ShenandoahAllocationRate::upper_bound(double sds) const {
  // Here we are using the standard deviation of the computed running
  // average, rather than the standard deviation of the samples that went
  // into the moving average. This is a much more stable value and is tied
  // to the actual statistic in use (moving average over samples of averages).
  return _rate.davg() + (sds * _rate_avg.dsd());
}

void ShenandoahAllocationRate::allocation_counter_reset() {
  _last_sample_time = os::elapsedTime();
  _last_sample_value = 0;
}

bool ShenandoahAllocationRate::is_spiking(double rate, double threshold) const {
  if (rate <= 0.0) {
    return false;
  }

  double sd = _rate.sd();
  if (sd > 0) {
    // There is a small chance that that rate has already been sampled, but it
    // seems not to matter in practice.
    double z_score = (rate - _rate.avg()) / sd;
    if (z_score > threshold) {
      return true;
    }
  }
  return false;
}

double ShenandoahAllocationRate::instantaneous_rate(double time, size_t allocated) const {
  size_t last_value = _last_sample_value;
  double last_time = _last_sample_time;
  size_t allocation_delta = (allocated > last_value) ? (allocated - last_value) : 0;
  double time_delta_sec = time - last_time;
  return (time_delta_sec > 0)  ? (allocation_delta / time_delta_sec) : 0;
}

void ShenandoahAdaptiveHeuristics::record_phase_duration(ShenandoahMajorGCPhase phase, double x, double duration) {
  assert (_surge_level <= Max_Surge_Level, "sanity");
#undef KELVIN_DEVELOPMENT
#ifdef KELVIN_DEVELOPMENT
  const char* phase_name = stage_name(phase);
  log_info(gc)("Recording duration of phase %s, adjusted by surge_level %u: %.3f", phase_name, _surge_level, duration);
#endif
  _phase_stats[phase].add_sample(x, duration);
}

ShenandoahPhaseTimeEstimator::ShenandoahPhaseTimeEstimator(const char* name) :
  _name(name),
  _changed(true),
  _first_index(0),
  _num_samples(0),
  _sum_of_x(0.0),
  _sum_of_y(0.0),
  _sum_of_xx(0.0),
  _sum_of_xy(0.0) { }

// We use the history of recent phase execution times to predict the time required to execute this phase in the future.
// The x_value represents an input parameter for the size of the phase's work.  For example, the evacuation phase is
// parameterized by the amount of memory that we expect to evacuate.  The y-value is the time required to execute the phase.
//
// The samples are calibrated under the assumption that workers are not surged.  In theory, we should be able to add
// phase-time samples for phases that have experienced worker surge, adjusting the duration by the magnitude of the
// surge.  For example, if we surged with 2x the number of normal workers, then we could record that the normal time
// (without the worker surge) to execute this phase would have been 2x the time it took with the 2x worker surge.  We
// have found this does not work.  It gets us into a death spiral.  In particular, this causes the triggering heuristic
// to "believe" it will take too long to execute the phase, so it triggers early, but usually not early enough to safely
// handle the anticipated long duration of the phase (because there is simply not enough allocation runway to handle that
// very long anticipated duration even when we trigger back to back).  Then the surge heuristics observes the situation and
// decides we have to surge with even more workers in order to handle the situation we are in.  Then at the end of the
// phase, we record the result of executing the phase with the 2.25x as taking 2.25x as long without the surge.  It gets
// worse and worse until we are stuck in maximum surge of 3x.  Meanwhile, the service is deprived of CPU attention
// because almost all the cores (75%) are fully consumed by out-of-control GC worker surge.  So whenever they get CPU
// time, the service threads are very hungry to allocate memory in order to catch up with pending work.
//
// We also experimented with scaling measured surge execution times to lower values.  For example, if surge was 2x, we
// tried scaling the measured execution time to 1.5x.  This also resulted in the death spiral behavior, albeit at a slightly
// slower pace.  Several considerations have motivated us to abandon the pursuit of the "perfect" scale factor:
//
//  1. If we accidentally undershoot the right scale value, we will end up with an overly optimistic scheduling heuristic.
//     We will trigger too late for normal operation, and the surge trigger will not kick in because it will not recognize
//     that we scheduled too late.
//
//  2. We expect that the "perfect" scale factor will differ for each surge percentage.  Typical experience is diminishing
//     returns for each new concurrent processor thrown at a shared job due to increased contention for shared resources and
//     locking mechanisms.
//
//  3. We expect that the scalability of different phases will be different.  Marking, for example, is especially difficulit
//     to scale, because typical workloads have mostly small objects, and the current implementation requires synchronization
//     between workers for each object that we mark through, and for each object added to the shared scan queue.  On the other
//     hand, evacuation and updating is much more easily performed by many cores.
//
// Our current approach to this problem is to only add samples that result from measurement of "unsurged execution phases".

void ShenandoahPhaseTimeEstimator::add_sample(double x_value, double y_value) {
  if (_num_samples >= MaxSamples) {
    _sum_of_x -= _x_values[_first_index];
    _sum_of_xx -= _x_values[_first_index] * _x_values[_first_index];
    _sum_of_xy -= _x_values[_first_index] * _y_values[_first_index];
    _sum_of_y -= _y_values[_first_index];
    _num_samples--;
    _first_index++;
    if (_first_index == MaxSamples) {
      _first_index = 0;
    }
  }
  _sum_of_x += x_value;
  _sum_of_xx += x_value * x_value;
  _sum_of_xy += x_value * y_value;
  assert(_num_samples < MaxSamples, "Unexpected overflow of ShenandoahPhaseTimeEstimator samples");
  assert(_first_index < MaxSamples, "Unexpected overflow");

  _sum_of_y += y_value;;
  _x_values[(_first_index + _num_samples) % MaxSamples] = x_value;
  _y_values[(_first_index + _num_samples++) % MaxSamples] = y_value;;
  _changed = true;
}

double ShenandoahPhaseTimeEstimator::predict_at(double x_value) {
  if (!_changed && (_most_recent_prediction_x_value == x_value)) {
    return _most_recent_prediction;
  } else if (_num_samples > 2) {
    double m = (_num_samples * _sum_of_xy - _sum_of_x * _sum_of_y) / (_num_samples * _sum_of_xx - _sum_of_x * _sum_of_x);
    double b = (_sum_of_y - m * _sum_of_x) / _num_samples;
    double sum_of_squared_deviations = 0;
    for (uint i = 0; i < _num_samples; i++) {
      double x_value = _x_values[(_first_index + i) % MaxSamples];
      double estimated_y = b + m * x_value;
      double y_value = _y_values[(_first_index + i) % MaxSamples];
      double delta = estimated_y - y_value;
      sum_of_squared_deviations += delta * delta;
#undef KELVIN_ESTIMATOR
#ifdef KELVIN_ESTIMATOR
      log_info(gc)("%s sample[%u] (x: %.3f, y: %.3f), predicted_y: %.3f, delta: %.3f",
		   _name, i, x_value, y_value, estimated_y, delta);
#endif
    }
    double standard_deviation = sqrt(sum_of_squared_deviations / _num_samples);
    double prediction_by_trend = b + m * x_value + standard_deviation;;
#ifdef KELVIN_ESTIMATOR
    log_info(gc)(" m: %.3f, b: %3f, std_dev: %.3f, prediction_by_trend: %.3f", m, b, standard_deviation, prediction_by_trend);
#endif
    _most_recent_prediction = prediction_by_trend;
    _changed = false;
    _most_recent_prediction_x_value = x_value;
    return _most_recent_prediction;
  } else {
    // Insufficient samples to make a non-zero prediction
    return 0.0;
  }
}

