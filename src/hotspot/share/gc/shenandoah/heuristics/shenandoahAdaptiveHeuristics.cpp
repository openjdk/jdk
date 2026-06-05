/*
 * Copyright (c) 2018, 2026, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shenandoah/heuristics/shenandoahAdaptiveHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahSpaceInfo.hpp"
#include "gc/shenandoah/shenandoahAllocRate.inline.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/quickSort.hpp"

#include <cmath>

#define PROPERFMT_F         "%.1f %s"
#define PROPERFMT_F_ARGS(s) byte_size_in_proper_unit(s), proper_unit_for_byte_size(s)

// These are used to decide if we want to make any adjustments at all
// at the end of a successful concurrent cycle.
constexpr double LOWEST_EXPECTED_AVAILABLE_AT_END = -0.5;
constexpr double HIGHEST_EXPECTED_AVAILABLE_AT_END = 0.5;

// These values are the confidence interval expressed as standard deviations.
// At the minimum confidence level, there is a 25% chance that the true value of
// the estimate (average cycle time or allocation rate) is not more than
// MINIMUM_CONFIDENCE standard deviations away from our estimate. Similarly, the
// MAXIMUM_CONFIDENCE interval here means there is a one in a thousand chance
// that the true value of our estimate is outside the interval. These are used
// as bounds on the adjustments applied at the outcome of a GC cycle.
constexpr double MINIMUM_CONFIDENCE = 0.319; // 25%
constexpr double MAXIMUM_CONFIDENCE = 3.291; // 99.9%

ShenandoahAdaptiveHeuristics::ShenandoahAdaptiveHeuristics(ShenandoahSpaceInfo* space_info) :
  ShenandoahHeuristics(space_info),
  _margin_of_error_sd(ShenandoahAdaptiveInitialConfidence),
  _last_trigger(OTHER),
  _available(Moving_Average_Samples),
  _headroom_adjustment(0) {
}

void ShenandoahAdaptiveHeuristics::initialize() {
  ShenandoahHeuristics::initialize();
}

void ShenandoahAdaptiveHeuristics::post_initialize() {
  ShenandoahHeuristics::post_initialize();
  assert(!ShenandoahHeap::heap()->mode()->is_generational(), "ShenandoahGenerationalHeuristics overrides this method");
  compute_headroom_adjustment();
}

void ShenandoahAdaptiveHeuristics::compute_headroom_adjustment() {
  // The trigger threshold represents mutator available - "head room".
  // We plan for GC to finish before the amount of allocated memory exceeds trigger threshold.  This is the same  as saying we
  // intend to finish GC before the amount of available memory is less than the allocation headroom.  Headroom is the planned
  // safety buffer to allow a small amount of additional allocation to take place in case we were overly optimistic in delaying
  // our trigger.
  const size_t capacity = ShenandoahHeap::heap()->soft_max_capacity();
  const size_t spike_headroom = capacity / 100 * ShenandoahAllocSpikeFactor;
  const size_t penalties      = capacity / 100 * _gc_time_penalties;
  _headroom_adjustment = spike_headroom + penalties;
}

void ShenandoahAdaptiveHeuristics::start_idle_span() {
  compute_headroom_adjustment();
}

void ShenandoahAdaptiveHeuristics::adjust_penalty(intx step) {
  ShenandoahHeuristics::adjust_penalty(step);
}

void ShenandoahAdaptiveHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                                         RegionData* data, size_t size,
                                                                         size_t actual_free) {
  size_t garbage_threshold = ShenandoahHeapRegion::region_size_bytes() * ShenandoahGarbageThreshold / 100;

  // The logic for cset selection in adaptive is as follows:
  //
  //   1. We cannot get cset larger than available free space. Otherwise we guarantee OOME
  //      during evacuation, and thus guarantee full GC. In practice, we also want to let the
  //      application allocate during concurrent GC. This is why we limit CSet to some fraction of
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

  const size_t capacity    = ShenandoahHeap::heap()->soft_max_capacity();
  const size_t max_cset    = shenandoah_safe_size_cast(1.0 * capacity / 100 * ShenandoahEvacReserve / ShenandoahEvacWaste);
  const size_t free_target = (capacity / 100 * ShenandoahMinFreeThreshold) + max_cset;
  const size_t min_garbage = (free_target > actual_free ? (free_target - actual_free) : 0);

  log_info(gc, ergo)("Adaptive CSet Selection. Target Free: " PROPERFMT ", Actual Free: " PROPERFMT
                         ", Max Evacuation: " PROPERFMT ", Min Garbage: " PROPERFMT ,
                     PROPERFMTARGS(free_target), PROPERFMTARGS(actual_free), PROPERFMTARGS(max_cset), PROPERFMTARGS(min_garbage));

  // Better select garbage-first regions
  QuickSort::sort(data, size, compare_by_garbage);

  size_t cur_cset = 0;
  size_t cur_garbage = 0;

  // Regions are sorted in order of decreasing garbage
  for (size_t idx = 0; idx < size; idx++) {
    ShenandoahHeapRegion* r = data[idx].get_region();

    const size_t new_cset    = cur_cset + r->get_live_data_bytes();
    const size_t new_garbage = cur_garbage + r->garbage();

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

void ShenandoahAdaptiveHeuristics::add_degenerated_gc_time(double time_at_start, double gc_time) {
  // Conservatively add sample into linear model, if this time is above the predicted concurrent gc time
  if (_cycles.predict_duration(time_at_start, _margin_of_error_sd) < gc_time) {
    _cycles.record_duration(time_at_start, gc_time);
  }
}

void ShenandoahAdaptiveHeuristics::record_success_concurrent() {
  ShenandoahHeuristics::record_success_concurrent();

  // We add this time even if it is a shortened cycle. There is a risk that this pulls
  // the gc time trend down, but it is still a more accurate view than excluding times
  // from shortened cycles. Suppose we did excluded shortened times, the risk would then
  // be running the collector more often than necessary because it continues to believe
  // the average cycle time is much higher than it otherwise would be.
  _cycles.record_duration(_cycle_start, elapsed_cycle_time());

  double z_score = 0.0;
  const double available = static_cast<double>(_space_info->available());
  const double available_sd = _available.sd();
  if (available_sd > 0) {
    const double available_avg = _available.avg();
    z_score = (available - available_avg) / available_sd;
    log_debug(gc, ergo)("Available: " PROPERFMT_F "B, z-score=%.3f. Average available: " PROPERFMT_F "B +/- " PROPERFMT_F "B.",
                        PROPERFMT_F_ARGS(available), z_score, PROPERFMT_F_ARGS(available_avg), PROPERFMT_F_ARGS(available_sd));
  }

  _available.add(available);

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
    // number was chosen empirically.
    if (_last_trigger == RATE) {
      adjust_margin_of_error(z_score / -100);
    }
  }
}

void ShenandoahAdaptiveHeuristics::record_degenerated() {
  ShenandoahHeuristics::record_degenerated();
  add_degenerated_gc_time(_precursor_cycle_start, elapsed_degenerated_cycle_time());
}

bool ShenandoahAdaptiveHeuristics::should_start_gc() {
  const size_t capacity = ShenandoahHeap::heap()->soft_max_capacity();
  const size_t available = _space_info->soft_mutator_available();

  log_debug(gc, ergo)("should_start_gc calculation: available: " PROPERFMT ", soft_max_capacity: "  PROPERFMT,
                      PROPERFMTARGS(available), PROPERFMTARGS(capacity));

  if (_start_gc_is_pending) {
    log_trigger("GC start is already pending");
    return true;
  }

  _last_trigger = OTHER;

  if (trigger_min_free_threshold(available, capacity)) {
    return true;
  }

  if (trigger_learning(available, capacity)) {
    return true;
  }

  const double anticipated_gc_start_time = get_most_recent_wake_time() + get_planned_sleep_interval();
  const double anticipated_gc_duration = _cycles.predict_duration(anticipated_gc_start_time, _margin_of_error_sd);
  ShenandoahAllocationRate& alloc_rate = ShenandoahHeap::heap()->alloc_rate();
  const ShenandoahAnticipatedConsumption consumption = alloc_rate.snapshot(anticipated_gc_duration, _margin_of_error_sd);
  const size_t allocatable_bytes = allocatable(available);
  maybe_log_rate_trigger_parameters(consumption, allocatable_bytes);

  if (trigger_accelerating_allocation_rate(consumption, allocatable_bytes)) {
    return true;
  }

  if (trigger_average_allocation_rate(consumption, allocatable_bytes)) {
    return true;
  }

  return ShenandoahHeuristics::should_start_gc();
}

bool ShenandoahAdaptiveHeuristics::trigger_min_free_threshold(size_t available, size_t capacity) {
  const size_t min_threshold = min_free_threshold(capacity);
  if (available < min_threshold) {
    log_trigger("Free (Soft) (" PROPERFMT ") is below minimum threshold (" PROPERFMT ")",
                PROPERFMTARGS(available), PROPERFMTARGS(min_threshold));
    accept_trigger_with_type(OTHER);
    return true;
  }
  return false;
}

bool ShenandoahAdaptiveHeuristics::trigger_learning(size_t available, size_t capacity) {
  // Check if we need to learn a bit about the application
  if (_gc_times_learned < ShenandoahLearningSteps) {
    const size_t init_threshold = capacity / 100 * ShenandoahInitFreeThreshold;
    if (available < init_threshold) {
      log_trigger("Learning %zu of %zu. Free (" PROPERFMT ") is below initial threshold (" PROPERFMT ")",
                  _gc_times_learned + 1, ShenandoahLearningSteps, PROPERFMTARGS(available), PROPERFMTARGS(init_threshold));
      accept_trigger_with_type(OTHER);
      return true;
    }
  }
  return false;
}

bool ShenandoahAdaptiveHeuristics::trigger_average_allocation_rate(const ShenandoahAnticipatedConsumption& rate, const size_t allocatable_bytes) {
  if (rate.baseline_consumption() > allocatable_bytes) {
    log_trigger("Anticipated GC duration (%.2f ms) is above the time for average allocation rate (" PROPERFMT_F "/s)"
                " to deplete free headroom (" PROPERFMT ") (margin of error = %.2f)",
                rate.duration_seconds() * 1000,
                PROPERFMT_F_ARGS(rate.baseline_rate()), PROPERFMTARGS(allocatable_bytes), _margin_of_error_sd);
    accept_trigger_with_type(RATE);
    return true;
  }
  return false;
}

// Note that even a single thread that wakes up and begins to allocate excessively can manifest as accelerating allocation
// rate. This thread will initially allocate a TLAB of minimum size.  Then it will allocate a TLAB twice as big a bit later,
// and then twice as big again after another short delay.  When a phase change causes many threads to increase their
// allocation behavior, this effect is multiplied, and compounded by jitter in the times that individual threads experience
// the phase change.
//
// The following trace represents an actual workload, with allocation rates sampled at 10 Hz, the default behavior before
// introduction of accelerated allocation rate detection.  Though the allocation rate is seen to be increasing at times
// 101.907 and 102.007 and 102.108, the newly sampled allocation rate is not enough to trigger GC because the headroom is
// still quite large.  In fact, GC is not triggered until time 102.409s, and this GC degenerates.
//
//    Sample Time (s)      Allocation Rate (MB/s)       Headroom (GB)
//       101.807                       0.0                  26.93
//                                                                  <--- accelerated spike can trigger here, around time 101.9s
//       101.907                     477.6                  26.85
//       102.007                   3,206.0                  26.35
//       102.108                  23,797.8                  24.19
//       102.208                  24,164.5                  21.83
//       102.309                  23,965.0                  19.47
//       102.409                  24,624.35                 17.05   <--- without accelerated rate detection, we trigger here
//
// Though the above measurements are from actual workload, the following details regarding sampled allocation rates at 3ms
// period were not measured directly for this run-time sample.  These are hypothetical, though they represent a plausible
// result that correlates with the actual measurements.
//
// For most of the 100 ms time span that precedes the sample at 101.907, the allocation rate still remains at zero.  The phase
// change that causes increasing allocations occurs near the end ot this time segment.  When sampled with a 3 ms period,
// acceleration of allocation can be triggered at approximately time 101.88s.
//
// In the default configuration, accelerated allocation rate is detected by examining a sequence of 8 allocation rate samples.
//
// Even a single allocation rate sample above the norm can be interpreted as acceleration of allocation rate.  For example,
// the best-fit line for the following samples has an acceleration rate of 3,553.3 MB/s/s.  This is not enough to trigger GC,
// especially given the abundance of Headroom at this moment in time.
//
//    TimeStamp (s)     Alloc rate (MB/s)
//    101.857                 0
//    101.860                 0
//    101.863                 0
//    101.866                 0
//    101.869                53.3
//
// At the next sample time, we will compute a slightly higher acceleration, 9,150 MB/s/s.  This is also insufficient to trigger
// GC.
//
//    TimeStamp (s)     Alloc rate (MB/s)
//    101.860                 0
//    101.863                 0
//    101.866                 0
//    101.869                53.3
//    101.872               110.6
//
// Eventually, we will observe a full history of accelerating rate samples, computing acceleration of 18,500 MB/s/s.  This will
// trigger GC over 500 ms earlier than was previously possible.
//
//    TimeStamp (s)     Alloc rate (MB/s)
//    101.866                 0
//    101.869                53.3
//    101.872               110.6
//    101.875               165.9
//    101.878               221.2
//
// The accelerated rate heuristic is based on the following idea:
//
//    Assume allocation rate is accelerating at a constant rate.  If we postpone the spike trigger until the subsequent
//    sample point, will there be enough memory to satisfy allocations that occur during the anticipated concurrent GC
//    cycle?  If not, we should trigger right now.
//
// Outline of this heuristic triggering technique:
//
//  1. We remember the N (e.g. N=3) most recent samples of spike allocation rate r0, r1, r2 samples at t0, t1, and t2
//  2. if r1 < r0 or r2 < r1, approximate Acceleration = 0.0, Rate = Average(r0, r1, r2)
//  3. Otherwise, use least squares method to compute best-fit line of rate vs time
//  4. The slope of this line represents Acceleration. The y-intercept of this line represents "initial rate"
//  5. Use r2 to represent CurrentRate
//  6. Use Consumption = CurrentRate * GCTime + 1/2 * Acceleration * GCTime * GCTime
//     (See High School physics discussions on constant acceleration: D = v0 * t + 1/2 * a * t^2)
//  7. if Consumption exceeds headroom, trigger now
//
// Though larger sample size may improve quality of predictor, it also delays trigger response.  Smaller sample sizes
// are more susceptible to false triggers based on random noise.  The default configuration uses a sample size of 8 and
// a sample period of roughly 15 ms, spanning approximately 120 ms of execution.
bool ShenandoahAdaptiveHeuristics::trigger_accelerating_allocation_rate(const ShenandoahAnticipatedConsumption& rate, const size_t allocatable_bytes) {
  if (rate.momentary_consumption() > allocatable_bytes) {
    assert(rate.accelerated_consumption() == 0, "Momentary trigger is meant to exclude acceleration trigger");
    log_trigger("Momentary spike consumption (" PROPERFMT ") exceeds free headroom (" PROPERFMT ") at "
                "current rate (" PROPERFMT_F "/s) for anticipated GC duration (%.2f ms)",
                PROPERFMTARGS(rate.momentary_consumption()), PROPERFMTARGS(allocatable_bytes),
                PROPERFMT_F_ARGS(rate.momentary_rate()), rate.duration_seconds() * 1000);
    accept_trigger_with_type(RATE);
    return true;
  }

  if (rate.accelerated_consumption() > allocatable_bytes) {
    assert(rate.momentary_consumption() == 0, "Acceleration trigger is meant to exclude momentary trigger");
    log_trigger("Accelerated consumption (" PROPERFMT ") exceeds free headroom (" PROPERFMT ") at "
                "current rate (" PROPERFMT_F "/s) with acceleration (" PROPERFMT_F "/s/s) for anticipated GC duration (%.2f ms)",
                PROPERFMTARGS(rate.accelerated_consumption()), PROPERFMTARGS(allocatable_bytes),
                PROPERFMT_F_ARGS(rate.predicted_rate()), PROPERFMT_F_ARGS(rate.acceleration()), rate.duration_seconds() * 1000);
    accept_trigger_with_type(RATE);
    return true;
  }

  return false;
}

void ShenandoahAdaptiveHeuristics::maybe_log_rate_trigger_parameters(const ShenandoahAnticipatedConsumption &consumption,
                                                                     size_t allocatable_bytes) const {
  if (log_is_enabled(Debug, gc, sampling)) {
    log_debug(gc, sampling)(
      "%s: Anticipated cycle duration: %.3fs, head room: " PROPERFMT ", margin of error: %.3f "
        "Baseline consumption: " PROPERFMT ", Baseline rate: " PROPERFMT_F "/s, "
        "Momentary consumption: " PROPERFMT ", Momentary rate: " PROPERFMT_F "/s, "
        "Accelerated consumption: " PROPERFMT ", Predicted rate: " PROPERFMT_F "/s, Acceleration: %.3f",
        _space_info->name(), consumption.duration_seconds(), PROPERFMTARGS(allocatable_bytes), _margin_of_error_sd,
        PROPERFMTARGS(consumption.baseline_consumption()), PROPERFMT_F_ARGS(consumption.baseline_rate()),
        PROPERFMTARGS(consumption.momentary_consumption()), PROPERFMT_F_ARGS(consumption.momentary_rate()),
        PROPERFMTARGS(consumption.accelerated_consumption()), PROPERFMT_F_ARGS(consumption.predicted_rate()), consumption.acceleration()
    );
  }
}

void ShenandoahAdaptiveHeuristics::adjust_margin_of_error(double amount) {
  _margin_of_error_sd = clamp(_margin_of_error_sd + amount, MINIMUM_CONFIDENCE, MAXIMUM_CONFIDENCE);
  log_debug(gc, ergo)("Margin of error now %.2f", _margin_of_error_sd);
}

size_t ShenandoahAdaptiveHeuristics::min_free_threshold(size_t capacity) const {
  return capacity / 100 * ShenandoahMinFreeThreshold;
}

#undef PROPERFMT_F
#undef PROPERFMT_F_ARGS
