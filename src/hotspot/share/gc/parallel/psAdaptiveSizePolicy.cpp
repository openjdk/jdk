/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/psAdaptiveSizePolicy.hpp"
#include "gc/parallel/psScavenge.hpp"
#include "gc/shared/gcArguments.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/shared/gcPolicyCounters.hpp"
#include "gc/shared/gcUtil.hpp"
#include "logging/log.hpp"
#include "runtime/timer.hpp"
#include "utilities/align.hpp"

#include <math.h>

PSAdaptiveSizePolicy::PSAdaptiveSizePolicy(size_t space_alignment,
                                           double gc_pause_goal_sec,
                                           uint gc_cost_ratio) :
     AdaptiveSizePolicy(gc_pause_goal_sec,
                        gc_cost_ratio),
     _avg_promoted(new AdaptivePaddedNoZeroDevAverage(AdaptiveSizePolicyWeight, PromotedPadding)),
     _space_alignment(space_alignment),
     _young_gen_size_increment_supplement(YoungGenerationSizeSupplement) {}

void PSAdaptiveSizePolicy::major_collection_begin() {
  _major_timer.reset();
  _major_timer.start();
  record_gc_pause_start_instant();
}

void PSAdaptiveSizePolicy::major_collection_end() {
  // Update the pause time.
  _major_timer.stop();

  double major_pause_in_seconds = _major_timer.seconds();

  record_gc_duration(major_pause_in_seconds);
  _trimmed_major_gc_time_seconds.add(major_pause_in_seconds);
}

void PSAdaptiveSizePolicy::print_stats(bool is_survivor_overflowing) {
  log_debug(gc, ergo)("Adaptive: throughput: %.3f, pause: %.1f ms, "
                      "gc-distance: %.3f (%.3f) s, "
                      "promoted: %.1f %s (%.1f %s), promotion-rate: %.1f M/s (%.1f M/s), overflowing: %s",
    mutator_time_percent(),
    minor_gc_time_estimate() * 1000.0,
    _gc_distance_seconds_seq.davg(), _gc_distance_seconds_seq.last(),
    PROPERFMTARGS(promoted_bytes_estimate()), PROPERFMTARGS(_promoted_bytes.last()),
    _promotion_rate_bytes_per_sec.davg()/M, _promotion_rate_bytes_per_sec.last()/M,
    is_survivor_overflowing ? "true" : "false");
}

size_t PSAdaptiveSizePolicy::compute_desired_eden_size(bool is_survivor_overflowing, size_t cur_eden) {
  // Guard against divide-by-zero; 0.001ms
  double gc_distance = MAX2(_gc_distance_seconds_seq.last(), 0.000001);
  double min_gc_distance = MinGCDistanceSecond;

  if (mutator_time_percent() < _throughput_goal) {
    size_t new_eden;
    const double expected_gc_distance = _trimmed_minor_gc_time_seconds.last() * GCTimeRatio;
    if (gc_distance >= expected_gc_distance) {
      // The lastest sample already satisfies throughput goal; keep the current size
      new_eden = cur_eden;
    } else {
      // Using the latest sample to limit the growth in order to avoid overshoot
      new_eden = MIN2((expected_gc_distance / gc_distance) * cur_eden,
                      (double)increase_eden(cur_eden));
    }
    log_debug(gc, ergo)("Adaptive: throughput (actual vs goal): %.3f vs %.3f ; eden delta: + %zu K",
      mutator_time_percent(), _throughput_goal, (new_eden - cur_eden)/K);
    return new_eden;
  }

  if (minor_gc_time_estimate() > gc_pause_goal_sec()) {
    log_debug(gc, ergo)("Adaptive: pause (ms) (actual vs goal): %.1f vs %.1f",
      minor_gc_time_estimate() * 1000.0, gc_pause_goal_sec() * 1000.0);
    return decrease_eden_for_minor_pause_time(cur_eden);
  }

  if (gc_distance < min_gc_distance) {
    size_t new_eden = MIN2((min_gc_distance / gc_distance) * cur_eden,
                           (double)increase_eden(cur_eden));
    log_debug(gc, ergo)("Adaptive: gc-distance (predicted vs goal): %.3f vs %.3f",
      gc_distance, min_gc_distance);
    return new_eden;
  }

  // If no overflowing and promotion is small
  if (!is_survivor_overflowing && promoted_bytes_estimate() < 1*K) {
    size_t delta = MIN2(eden_increment(cur_eden) / AdaptiveSizeDecrementScaleFactor, cur_eden / 2);
    double delta_factor = (double) delta / cur_eden;

    const double gc_time_lower_estimate = _trimmed_minor_gc_time_seconds.davg() - _trimmed_minor_gc_time_seconds.dsd();
    // Limit gc-frequency so that promoted rate is < 1M/s
    // promoted_bytes_estimate() / (gc_distance + gc_time_lower_estimate) < 1M/s
    // ==> promoted_bytes_estimate() / M - gc_time_lower_estimate < gc_distance

    const double gc_distance_target = MAX3(minor_gc_time_conservative_estimate() * GCTimeRatio,
                                           promoted_bytes_estimate() / M - gc_time_lower_estimate,
                                           min_gc_distance);
    double predicted_gc_distance = gc_distance * (1 - delta_factor) - _gc_distance_seconds_seq.dsd();

    if (predicted_gc_distance > gc_distance_target) {
      log_debug(gc, ergo)("Adaptive: shrinking gc-distance (predicted vs threshold): %.3f vs %.3f",
        predicted_gc_distance, gc_distance_target);
      return cur_eden - delta;
    }
  }

  log_debug(gc, ergo)("Adaptive: eden unchanged");
  return cur_eden;
}

size_t PSAdaptiveSizePolicy::compute_desired_survivor_size(
  size_t current_survivor_size,
  size_t max_gen_size) {
  size_t desired_survivor_size = survived_bytes_estimate();

  if (desired_survivor_size >= current_survivor_size) {
    // Increasing survivor
    return MIN2(desired_survivor_size, max_survivor_size(max_gen_size));
  }

  size_t delta = current_survivor_size - desired_survivor_size;
  return current_survivor_size - delta / AdaptiveSizeDecrementScaleFactor;
}

size_t PSAdaptiveSizePolicy::compute_old_gen_shrink_bytes(size_t old_gen_free_bytes, size_t max_shrink_bytes) {
  // 10min
  static constexpr double lookahead_sec = 10 * 60;

  double free_bytes = old_gen_free_bytes;

  double promotion_rate = promotion_rate_bytes_per_sec_estimate();

  double min_free_bytes = MAX2((double)padded_average_promoted_in_bytes(),
                               promotion_rate * lookahead_sec);
  size_t shrink_bytes = 0;

  if (free_bytes > min_free_bytes) {
    shrink_bytes = (free_bytes - min_free_bytes) / 2;
    shrink_bytes = MIN2(shrink_bytes, max_shrink_bytes);
  }

  log_debug(gc, ergo)("Adaptive: old-gen free bytes: %.0f M, min-free-bytes: %.1f M, shrink-bytes: %zu K",
    free_bytes/M, min_free_bytes/M, shrink_bytes/K);

  return shrink_bytes;
}

void PSAdaptiveSizePolicy::decay_supplemental_growth(uint num_minor_gcs) {
  if ((num_minor_gcs >= AdaptiveSizePolicyReadyThreshold) &&
      (num_minor_gcs % YoungGenerationSizeSupplementDecay) == 0) {
    _young_gen_size_increment_supplement =
      _young_gen_size_increment_supplement >> 1;
  }
}

size_t PSAdaptiveSizePolicy::decrease_eden_for_minor_pause_time(size_t current_eden_size) {
  size_t desired_eden_size = minor_pause_young_estimator()->decrement_will_decrease()
                           ? current_eden_size - eden_decrement_aligned_down(current_eden_size)
                           : current_eden_size;

  assert(desired_eden_size <= current_eden_size, "postcondition");

  return desired_eden_size;
}

size_t PSAdaptiveSizePolicy::increase_eden(size_t current_eden_size) {
  size_t delta = eden_increment_with_supplement_aligned_up(current_eden_size);

  size_t desired_eden_size = current_eden_size + delta;

  assert(desired_eden_size >= current_eden_size, "postcondition");

  return desired_eden_size;
}

size_t PSAdaptiveSizePolicy::eden_increment_with_supplement_aligned_up(size_t cur_eden) {
  size_t result = eden_increment(cur_eden,
    YoungGenerationSizeIncrement + _young_gen_size_increment_supplement);
  return align_up(result, _space_alignment);
}

size_t PSAdaptiveSizePolicy::eden_decrement_aligned_down(size_t cur_eden) {
  size_t eden_heap_delta = eden_increment(cur_eden) / AdaptiveSizeDecrementScaleFactor;
  return align_down(eden_heap_delta, _space_alignment);
}

uint PSAdaptiveSizePolicy::compute_tenuring_threshold(bool is_survivor_overflowing,
                                                      uint tenuring_threshold) {
  if (!young_gen_policy_is_ready()) {
    return tenuring_threshold;
  }

  if (is_survivor_overflowing) {
    return tenuring_threshold;
  }

  bool incr_tenuring_threshold = false;

  const double major_cost = major_gc_time_sum();
  const double minor_cost = minor_gc_time_sum();

  if (minor_cost > major_cost * _threshold_tolerance_percent) {
    // nothing; we prefer young-gc over full-gc
  } else if (major_cost > minor_cost * _threshold_tolerance_percent) {
    // Major times are too long, so we want less promotion.
    incr_tenuring_threshold = true;
  }

  // Finally, increment or decrement the tenuring threshold, as decided above.
  // We test for decrementing first, as we might have hit the target size
  // limit.
  if (!(AlwaysTenure || NeverTenure)) {
    if (incr_tenuring_threshold && tenuring_threshold < MaxTenuringThreshold) {
      tenuring_threshold++;
    }
  }

  return tenuring_threshold;
}

void PSAdaptiveSizePolicy::update_averages(bool is_survivor_overflow,
                                           size_t survived,
                                           size_t promoted) {
  if (!is_survivor_overflow) {
    _survived_bytes.add(survived);
  } else {
    // survived is an underestimate
    _survived_bytes.add(survived + promoted);
  }

  avg_promoted()->sample(promoted);
  _promoted_bytes.add(promoted);

  double promotion_rate = promoted / (_gc_distance_seconds_seq.last() + _trimmed_minor_gc_time_seconds.last());
  _promotion_rate_bytes_per_sec.add(promotion_rate);
}