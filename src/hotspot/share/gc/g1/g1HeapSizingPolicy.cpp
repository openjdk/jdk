/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1HeapSizingPolicy.hpp"
#include "gc/g1/g1Analytics.hpp"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

G1HeapSizingPolicy::G1HeapSizingPolicy(const G1CollectedHeap* g1h, const G1Analytics* analytics) :
  _g1h(g1h),
  _analytics(analytics),
  _num_prev_pauses_for_heuristics(analytics->number_of_recorded_pause_times()) {

  assert(MinOverThresholdForGrowth < _num_prev_pauses_for_heuristics, "Threshold must be less than %u", _num_prev_pauses_for_heuristics);
  clear_ratio_check_data();
}

void G1HeapSizingPolicy::clear_ratio_check_data() {
  _ratio_over_threshold_count = 0;
  _ratio_over_threshold_sum = 0.0;
  _pauses_since_start = 0;
}

size_t G1HeapSizingPolicy::expansion_amount() {
  double recent_gc_overhead = _analytics->recent_avg_pause_time_ratio() * 100.0;
  double last_gc_overhead = _analytics->last_pause_time_ratio() * 100.0;
  assert(GCTimeRatio > 0,
         "we should have set it to a default value set_g1_gc_flags() "
         "if a user set it to 0");
  const double gc_overhead_percent = 100.0 * (1.0 / (1.0 + GCTimeRatio));

  double threshold = gc_overhead_percent;
  size_t expand_bytes = 0;

  // If the heap is at less than half its maximum size, scale the threshold down,
  // to a limit of 1. Thus the smaller the heap is, the more likely it is to expand,
  // though the scaling code will likely keep the increase small.
  if (_g1h->capacity() <= _g1h->max_capacity() / 2) {
    threshold *= (double)_g1h->capacity() / (double)(_g1h->max_capacity() / 2);
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
  bool filled_history_buffer = _pauses_since_start == _num_prev_pauses_for_heuristics;
  if ((_ratio_over_threshold_count == MinOverThresholdForGrowth) ||
      (filled_history_buffer && (recent_gc_overhead > threshold))) {
    size_t min_expand_bytes = HeapRegion::GrainBytes;
    size_t reserved_bytes = _g1h->max_capacity();
    size_t committed_bytes = _g1h->capacity();
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
      double const StartScaleDownAt = gc_overhead_percent;
      double const StartScaleUpAt = gc_overhead_percent * 1.5;
      double const ScaleUpRange = gc_overhead_percent * 2.0;

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
      if (_pauses_since_start > _num_prev_pauses_for_heuristics) {
        clear_ratio_check_data();
      }
    }
  }

  return expand_bytes;
}
