/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/heuristics/shenandoahReactiveHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "utilities/quickSort.hpp"

// These constants are used to adjust the margin of error for the moving
// average of the allocation rate and cycle time. The units are standard
// deviations.
const double ShenandoahReactiveHeuristics::FULL_PENALTY_SD = 0.2;
const double ShenandoahReactiveHeuristics::DEGENERATE_PENALTY_SD = 0.1;

// These are used to decide if we want to make any adjustments at all
// at the end of a successful concurrent cycle.
const double LOWEST_EXPECTED_AVAILABLE_AT_END = -0.5;
const double HIGHEST_EXPECTED_AVAILABLE_AT_END = 0.5;

// These values are the confidence interval expressed as standard deviations.
// At the minimum confidence level, there is a 25% chance that the true value of
// the estimate (average cycle time or allocation rate) is not more than
// MINIMUM_CONFIDENCE standard deviations away from our estimate. Similarly, the
// MAXIMUM_CONFIDENCE interval here means there is a one in a thousand chance
// that the true value of our estimate is outside the interval. These are used
// as bounds on the adjustments applied at the outcome of a GC cycle.
const double ShenandoahReactiveHeuristics::MINIMUM_CONFIDENCE = 0.319; // 25%
const double ShenandoahReactiveHeuristics::MAXIMUM_CONFIDENCE = 3.291; // 99.9%

ShenandoahReactiveHeuristics::ShenandoahReactiveHeuristics() :
  ShenandoahAdaptiveHeuristics(),
  _allocation_rate(this),
  _available_at_cycle_start(0),
  _margin_of_error_sd(ShenandoahReactiveInitialConfidence),
  _spike_threshold_sd(ShenandoahReactiveInitialSpikeThreshold),
  _last_trigger(OTHER) { }

ShenandoahReactiveHeuristics::~ShenandoahReactiveHeuristics() {}

void ShenandoahReactiveHeuristics::record_cycle_start() {
  ShenandoahAdaptiveHeuristics::record_cycle_start();
  _allocation_rate.allocation_counter_reset();
  _available_at_cycle_start = ShenandoahHeap::heap()->free_set()->available();
}

bool ShenandoahReactiveHeuristics::should_start_gc() const {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  size_t capacity = heap->max_capacity();
  size_t available = heap->free_set()->available();
  size_t bytes_allocated_since_gc_start = heap->bytes_allocated_since_gc_start();

  // Track allocation rate even if we decide to start a cycle for other reasons.
  ShenandoahReactiveHeuristics *heuristic = const_cast<ShenandoahReactiveHeuristics *>(this);
  heuristic->_allocation_rate.sample(bytes_allocated_since_gc_start);
  heuristic->_last_trigger = OTHER;

  if (is_available_below_min_threshold(capacity, available)) {
    return true;
  }

  if (is_learning_necessary(capacity, available)) {
    return true;
  }

  if (is_allocation_rate_too_high(capacity, available, bytes_allocated_since_gc_start)) {
    return true;
  }

  // Don't call into our immediate parent class, we've already done
  // everything it would do (and more).
  return ShenandoahHeuristics::should_start_gc(); // NOLINT(bugprone-parent-virtual-call)
}


bool ShenandoahReactiveHeuristics::is_allocation_rate_too_high(size_t capacity,
                                                               size_t available,
                                                               size_t bytes_allocated_since_gc_start) const {
  // Check if allocation headroom is still okay. This also factors in:
  //   1. Some space to absorb allocation spikes
  //   2. Accumulated penalties from Degenerated and Full GC
  // The headroom computation was copied from Adaptive heuristics.
  size_t allocation_headroom = available;

  size_t spike_headroom = capacity / 100 * ShenandoahAllocSpikeFactor;
  size_t penalties      = double(capacity) / 100 * _gc_time_penalties;

  allocation_headroom -= MIN2(allocation_headroom, spike_headroom);
  allocation_headroom -= MIN2(allocation_headroom, penalties);

  double average_cycle_seconds = _gc_time_history->davg() + (_margin_of_error_sd * _gc_time_history->dsd());
  double bytes_allocated_per_second = _allocation_rate.upper_bound(_margin_of_error_sd);
  if (average_cycle_seconds > allocation_headroom / bytes_allocated_per_second) {
    log_info(gc)("Trigger: Average GC time (%.2f ms) is above the time for allocation rate (%.0f %sB/s) to deplete free headroom (" SIZE_FORMAT "%s) (margin of error = %.2f)",
                 average_cycle_seconds * 1000,
                 byte_size_in_proper_unit(bytes_allocated_per_second), proper_unit_for_byte_size(bytes_allocated_per_second),
                 byte_size_in_proper_unit(allocation_headroom), proper_unit_for_byte_size(allocation_headroom),
                 _margin_of_error_sd);

    log_info(gc, ergo)("Free headroom: " SIZE_FORMAT "%s (free) - " SIZE_FORMAT "%s (spike) - " SIZE_FORMAT "%s (penalties) = " SIZE_FORMAT "%s",
                         byte_size_in_proper_unit(available),           proper_unit_for_byte_size(available),
                         byte_size_in_proper_unit(spike_headroom),      proper_unit_for_byte_size(spike_headroom),
                         byte_size_in_proper_unit(penalties),           proper_unit_for_byte_size(penalties),
                         byte_size_in_proper_unit(allocation_headroom), proper_unit_for_byte_size(allocation_headroom));

    const_cast<ShenandoahReactiveHeuristics *>(this)->_last_trigger = RATE;
    return true;
  }

  double instantaneous_rate = _allocation_rate.instantaneous_rate(bytes_allocated_since_gc_start);
  if (_allocation_rate.is_spiking(instantaneous_rate) && average_cycle_seconds > allocation_headroom / instantaneous_rate) {
    log_info(gc)("Trigger: Instantaneous allocation rate (%.0f %sB/s) will deplete free headroom (" SIZE_FORMAT "%s) before average time (%.2f ms) to complete GC cycle.",
                 byte_size_in_proper_unit(instantaneous_rate), proper_unit_for_byte_size(instantaneous_rate),
                 byte_size_in_proper_unit(allocation_headroom), proper_unit_for_byte_size(allocation_headroom),
                 average_cycle_seconds * 1000);
    const_cast<ShenandoahReactiveHeuristics *>(this)->_last_trigger = SPIKE;
    return true;
  }

  return false;
}

void ShenandoahReactiveHeuristics::record_success_concurrent() {
  ShenandoahHeuristics::record_success_concurrent();

  double available = ShenandoahHeap::heap()->free_set()->available();

  _available.add(available);
  double z_score = 0.0;
  if (_available.sd() > 0) {
    z_score = (available - _available.avg()) / _available.sd();
  }

  log_debug(gc, ergo)("Available: " SIZE_FORMAT " %sB, z-score=%.3f. Average available: %.1f %sB +/- %.1f %sB.",
                       byte_size_in_proper_unit(size_t(available)), proper_unit_for_byte_size(size_t(available)),
                       z_score,
                       byte_size_in_proper_unit(_available.avg()), proper_unit_for_byte_size(_available.avg()),
                       byte_size_in_proper_unit(_available.sd()), proper_unit_for_byte_size(_available.sd()));

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

void ShenandoahReactiveHeuristics::record_success_degenerated() {
  ShenandoahHeuristics::record_success_degenerated();
  // Adjust both trigger's parameters in the case of a degenerated GC because
  // either of them should have triggered earlier to avoid this case.
  adjust_margin_of_error(DEGENERATE_PENALTY_SD);
  adjust_spike_threshold(DEGENERATE_PENALTY_SD);
}

void ShenandoahReactiveHeuristics::record_success_full() {
  ShenandoahHeuristics::record_success_full();
  // Adjust both trigger's parameters in the case of a full GC because
  // either of them should have triggered earlier to avoid this case.
  adjust_margin_of_error(FULL_PENALTY_SD);
  adjust_spike_threshold(FULL_PENALTY_SD);
}

static double saturate(double value, double min, double max) {
  return MAX2(MIN2(value, max), min);
}

void ShenandoahReactiveHeuristics::adjust_last_trigger_parameters(double amount) {
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

void ShenandoahReactiveHeuristics::adjust_margin_of_error(double amount) {
  _margin_of_error_sd = saturate(_margin_of_error_sd + amount, MINIMUM_CONFIDENCE, MAXIMUM_CONFIDENCE);
  log_debug(gc, ergo)("Margin of error now %.2f", _margin_of_error_sd);
}

void ShenandoahReactiveHeuristics::adjust_spike_threshold(double amount) {
  _spike_threshold_sd = saturate(_spike_threshold_sd - amount, MINIMUM_CONFIDENCE, MAXIMUM_CONFIDENCE);
  log_debug(gc, ergo)("Spike threshold now: %.2f", _spike_threshold_sd);
}


ShenandoahAllocationRate::ShenandoahAllocationRate(ShenandoahReactiveHeuristics *heuristics) :
  _heuristics(heuristics),
  _last_sample_time(os::javaTimeNanos()),
  _last_sample_value(0),
  _interval_ns(NANOUNITS / ShenandoahReactiveSampleFrequencyHz),
  _rate(ShenandoahReactiveSampleSizeSeconds * ShenandoahReactiveSampleFrequencyHz, ShenandoahReactiveDecayFactor),
  _rate_avg(ShenandoahReactiveSampleSizeSeconds * ShenandoahReactiveSampleFrequencyHz, ShenandoahReactiveDecayFactor) {
}

void ShenandoahAllocationRate::sample(size_t bytes_allocated_since_gc_start) {
  jlong now = os::javaTimeNanos();
  if (now - _last_sample_time > _interval_ns) {
    if (bytes_allocated_since_gc_start > _last_sample_value) {
      size_t allocation_delta = bytes_allocated_since_gc_start - _last_sample_value;
      size_t time_delta_ns = now - _last_sample_time;
      double alloc_bytes_per_second = ((double) allocation_delta * NANOUNITS) / time_delta_ns;

      _rate.add(alloc_bytes_per_second);
      _rate_avg.add(_rate.avg());
    }

    _last_sample_time = now;
    _last_sample_value = bytes_allocated_since_gc_start;
  }
}

double ShenandoahAllocationRate::upper_bound(double standard_deviations) const {
  // Here we are using the standard deviation of the computed running
  // average, rather than the standard deviation of the samples that went
  // into the moving average. This is a much more stable value and is tied
  // to the actual statistic in use (moving average over samples of averages).
  return _rate.davg() + (standard_deviations * _rate_avg.dsd());
}

void ShenandoahAllocationRate::allocation_counter_reset() {
  _last_sample_time = os::javaTimeNanos();
  _last_sample_value = 0;
}

bool ShenandoahAllocationRate::is_spiking(double instantaneous_rate) const {
  double standard_deviation = _rate.sd();
  if (standard_deviation > 0) {
    // There is a small chance that that rate has already been sampled, but it
    // seems not to matter in practice.
    double z_score = (instantaneous_rate - _rate.avg()) / standard_deviation;
    if (z_score > _heuristics->_spike_threshold_sd) {
      return true;
    }
  }
  return false;
}

double ShenandoahAllocationRate::instantaneous_rate(size_t bytes_allocated_since_gc_start) const {
  size_t allocation_delta = bytes_allocated_since_gc_start - _last_sample_value;
  size_t time_delta_ns = os::javaTimeNanos() - _last_sample_time;
  double alloc_bytes_per_second = ((double) allocation_delta * NANOUNITS) / time_delta_ns;
  return alloc_bytes_per_second;
}
