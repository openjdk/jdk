/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/adaptiveSizePolicy.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/shared/gcUtil.hpp"
#include "logging/log.hpp"
#include "runtime/timer.hpp"

elapsedTimer AdaptiveSizePolicy::_minor_timer;
elapsedTimer AdaptiveSizePolicy::_major_timer;

// The throughput goal is implemented as
//      _throughput_goal = 1 - ( 1 / (1 + gc_cost_ratio))
// gc_cost_ratio is the ratio
//      application cost / gc cost
// For example a gc_cost_ratio of 4 translates into a
// throughput goal of .80

AdaptiveSizePolicy::AdaptiveSizePolicy(double gc_pause_goal_sec,
                                       uint gc_cost_ratio) :
  _throughput_goal(1.0 - double(1.0 / (1.0 + (double) gc_cost_ratio))),
  _gc_distance_timer(),
  _gc_distance_seconds_seq(seq_default_alpha_value),
  _trimmed_minor_gc_time_seconds(NumOfGCSample, seq_default_alpha_value),
  _trimmed_major_gc_time_seconds(NumOfGCSample, seq_default_alpha_value),
  _gc_samples(),
  _promoted_bytes(seq_default_alpha_value),
  _survived_bytes(seq_default_alpha_value),
  _promotion_rate_bytes_per_sec(seq_default_alpha_value),
  _peak_old_used_bytes_seq(seq_default_alpha_value),
  _minor_pause_young_estimator(new LinearLeastSquareFit(AdaptiveSizePolicyWeight)),
  _threshold_tolerance_percent(1.0 + ThresholdTolerance/100.0),
  _gc_pause_goal_sec(gc_pause_goal_sec),
  _young_gen_policy_is_ready(false) {}

void AdaptiveSizePolicy::minor_collection_begin() {
  _minor_timer.reset();
  _minor_timer.start();
  record_gc_pause_start_instant();
}

void AdaptiveSizePolicy::minor_collection_end(size_t eden_capacity_in_bytes) {
  _minor_timer.stop();

  double minor_pause_in_seconds = _minor_timer.seconds();
  double minor_pause_in_ms = minor_pause_in_seconds * MILLIUNITS;

  record_gc_duration(minor_pause_in_seconds);
  _trimmed_minor_gc_time_seconds.add(minor_pause_in_seconds);

  if (!_young_gen_policy_is_ready) {
    // The policy does not have enough data until at least some
    // young collections have been done.
    _young_gen_policy_is_ready = GCId::current() >= AdaptiveSizePolicyReadyThreshold;
  }

  {
    double eden_size_in_mbytes = ((double)eden_capacity_in_bytes)/((double)M);
    _minor_pause_young_estimator->update(eden_size_in_mbytes, minor_pause_in_ms);
  }
}

size_t AdaptiveSizePolicy::eden_increment(size_t cur_eden, uint percent_change) {
  size_t eden_heap_delta = cur_eden * percent_change / 100;
  return eden_heap_delta;
}

size_t AdaptiveSizePolicy::eden_increment(size_t cur_eden) {
  return eden_increment(cur_eden, YoungGenerationSizeIncrement);
}