/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1Analytics.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1HeapSizingPolicy.hpp"
#include "gc/shared/gc_globals.hpp"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

G1HeapSizingPolicy* G1HeapSizingPolicy::create(const G1CollectedHeap* g1h, const G1Analytics* analytics) {
  return new G1HeapSizingPolicy(g1h, analytics);
}

uint G1HeapSizingPolicy::long_term_count_limit() const {
  return _analytics->max_num_of_recorded_pause_times();
}

G1HeapSizingPolicy::G1HeapSizingPolicy(const G1CollectedHeap* g1h, const G1Analytics* analytics) :
  _g1h(g1h),
  _analytics(analytics),
  // Bias for expansion at startup; the +1 is to counter the first sample always
  // being 0.0, i.e. lower than any threshold.
  _ratio_exceeds_threshold((MinOverThresholdForExpansion / 2) + 1),
  _recent_pause_ratios(long_term_count_limit()),
  _long_term_count(0) {

  assert(_ratio_exceeds_threshold < MinOverThresholdForExpansion,
         "Initial ratio counter value too high.");

  assert(_ratio_exceeds_threshold > -MinOverThresholdForExpansion,
         "Initial ratio counter value too low.");

  assert(MinOverThresholdForExpansion <= long_term_count_limit(),
         "Expansion threshold count must be less than %u", long_term_count_limit());

  assert(G1ShortTermShrinkThreshold <= long_term_count_limit(),
         "Shrink threshold count must be less than %u", long_term_count_limit());
}

void G1HeapSizingPolicy::reset_ratio_tracking_data() {
  _long_term_count = 0;
  _ratio_exceeds_threshold = 0;
  // Keep the recent gc time ratio data.
}

void G1HeapSizingPolicy::decay_ratio_tracking_data() {
  _long_term_count = 0;
  _ratio_exceeds_threshold /= 2;
  // Keep the recent gc time ratio data.
}

double G1HeapSizingPolicy::scale_with_heap(double pause_time_threshold) {
  double threshold = pause_time_threshold;
  // If the heap is at less than half its maximum size, scale the threshold down,
  // to a limit of 1%. Thus the smaller the heap is, the more likely it is to expand,
  // though the scaling code will likely keep the increase small.
  if (_g1h->capacity() <= _g1h->max_capacity() / 2) {
    threshold *= (double)_g1h->capacity() / (double)(_g1h->max_capacity() / 2);
    threshold = MAX2(threshold, 0.01);
  }

  return threshold;
}

// Logistic function, returns values in the range [0,1]
static double sigmoid_function(double value) {
  // Sigmoid Parameters:
  double inflection_point = 1.0; // Inflection point where acceleration begins (midpoint of sigmoid).
  double steepness = 6.0;

  return 1.0 / (1.0 + pow(M_E, -steepness * (value - inflection_point)));
}

double G1HeapSizingPolicy::scale_resize_ratio_delta(double ratio_delta,
                                                    double min_scale_down_factor,
                                                    double max_scale_up_factor) const {
   // We use a sigmoid function for scaling smoothly as we transition from a slow start to a fast growth
   // function with increasing ratio_delta. The sigmoid outputs a value in the range [0,1] which we scale to
   // the range [min_scale_down_factor, max_scale_up_factor]
  double sigmoid = sigmoid_function(ratio_delta);

  double scale_factor = min_scale_down_factor + (max_scale_up_factor - min_scale_down_factor) * sigmoid;
  return scale_factor;
}

// Calculate the ratio of the difference of a and b relative to b.
static double rel_ratio(double a, double b) {
  return (a - b) / b;
}

static void log_resize(double short_term_pause_time_ratio,
                       double long_term_pause_time_ratio,
                       double lower_threshold,
                       double upper_threshold,
                       double pause_time_ratio,
                       bool at_limit,
                       size_t resize_bytes,
                       bool expand) {

  log_debug(gc, ergo, heap)("Heap resize: "
                            "short term pause time ratio %1.2f%% long term pause time ratio %1.2f%% "
                            "lower threshold %1.2f%% upper threshold %1.2f%% pause time ratio %1.2f%% "
                            "at limit %s resize by %zuM expand %s",
                            short_term_pause_time_ratio * 100.0,
                            long_term_pause_time_ratio * 100.0,
                            lower_threshold * 100.0,
                            upper_threshold * 100.0,
                            pause_time_ratio * 100.0,
                            BOOL_TO_STR(at_limit),
                            resize_bytes / M,
                            BOOL_TO_STR(expand));
}

size_t G1HeapSizingPolicy::young_collection_expand_amount(double delta) const {
  assert(delta >= 0.0, "must be");

  size_t reserved_bytes = _g1h->max_capacity();
  size_t committed_bytes = _g1h->capacity();
  size_t uncommitted_bytes = reserved_bytes - committed_bytes;
  size_t expand_bytes_via_pct =
    uncommitted_bytes * G1ExpandByPercentOfAvailable / 100;
  size_t min_expand_bytes = MIN2(G1HeapRegion::GrainBytes, uncommitted_bytes);

  // Take the current size, or G1ExpandByPercentOfAvailable % of
  // the available expansion space, whichever is smaller, as the base
  // expansion size. Then possibly scale this size according to how much the
  // threshold has (on average) been exceeded by.
  const double MinScaleDownFactor = 0.2;
  const double MaxScaleUpFactor = 2.0;

  double scale_factor = scale_resize_ratio_delta(delta,
                                                 MinScaleDownFactor,
                                                 MaxScaleUpFactor);

  size_t resize_bytes = MIN2(expand_bytes_via_pct, committed_bytes);

  resize_bytes = static_cast<size_t>(resize_bytes * scale_factor);

  // Ensure the expansion size is at least the minimum growth amount
  // and at most the remaining uncommitted byte size.
  return clamp((size_t)resize_bytes, min_expand_bytes, uncommitted_bytes);
}

size_t G1HeapSizingPolicy::young_collection_shrink_amount(double delta, size_t allocation_word_size) const {
  assert(delta >= 0.0, "must be");

  double scale_factor = scale_resize_ratio_delta(delta,
                                                 G1ShrinkByPercentOfAvailable / 1000.0,
                                                 G1ShrinkByPercentOfAvailable / 100.0);
  assert(scale_factor <= 1.0, "must be");

  // We are at the end of GC, so free regions are at maximum. Do not try to shrink
  // to have less than the reserve or the number of regions we are most certainly
  // going to use during this mutator phase.
  uint target_regions_to_shrink = _g1h->num_free_regions();

  uint reserve_regions = ceil(_g1h->num_committed_regions() * G1ReservePercent / 100.0);

  uint needed_for_allocation = _g1h->eden_target_length();
  if (_g1h->is_humongous(allocation_word_size)) {
    needed_for_allocation += (uint) _g1h->humongous_obj_size_in_regions(allocation_word_size);
  }

  if (target_regions_to_shrink >= needed_for_allocation) {
    target_regions_to_shrink -= needed_for_allocation;
  } else {
    target_regions_to_shrink = 0;
  }

  size_t resize_bytes = (double)G1HeapRegion::GrainBytes * target_regions_to_shrink * scale_factor;

  log_debug(gc, ergo, heap)("Shrink log: scale factor %1.2f%% "
                            "total free regions %u "
                            "reserve regions %u "
                            "needed for alloc %u "
                            "base targeted for shrinking %u "
                            "resize_bytes %zd ( %zu regions)",
                            scale_factor * 100.0,
                            _g1h->num_free_regions(),
                            reserve_regions,
                            needed_for_allocation,
                            target_regions_to_shrink,
                            resize_bytes,
                            (resize_bytes / G1HeapRegion::GrainBytes));

  return resize_bytes;
}

size_t G1HeapSizingPolicy::young_collection_resize_amount(bool& expand, size_t allocation_word_size) {
  assert(GCTimeRatio > 0, "must be");
  expand = false;

  const double long_term_pause_time_ratio = _analytics->long_term_pause_time_ratio();
  const double short_term_pause_time_ratio = _analytics->short_term_pause_time_ratio();

  // Calculate gc time ratio thresholds:
  // - upper threshold, directly based on GCTimeRatio. We do not want to exceed
  // this.
  // - lower threshold, we do not want to go under.
  // - mid threshold, halfway between upper and lower threshold, represents the
  // actual target when resizing the heap.
  double pause_time_threshold = 1.0 / (1.0 + GCTimeRatio);

  pause_time_threshold = scale_with_heap(pause_time_threshold);
  const double min_gc_time_ratio_ratio = G1MinimumPercentOfGCTimeRatio / 100.0;
  double upper_threshold = pause_time_threshold * (1 + min_gc_time_ratio_ratio);
  double lower_threshold = pause_time_threshold * (1 - min_gc_time_ratio_ratio);

  // Use threshold based relative to current GCTimeRatio to more quickly expand
  // and shrink at smaller heap sizes (relative to maximum).
  const double long_term_delta = rel_ratio(long_term_pause_time_ratio, pause_time_threshold);
  double short_term_ratio_delta = rel_ratio(short_term_pause_time_ratio, pause_time_threshold);

  // If the short term GC time ratio exceeds a threshold, increment the occurrence
  // counter.
  if (short_term_pause_time_ratio > upper_threshold) {
    _ratio_exceeds_threshold++;
  } else if (short_term_pause_time_ratio < lower_threshold) {
    _ratio_exceeds_threshold--;
  }
  // Ignore very first sample as it is garbage.
  if (_long_term_count != 0 || _recent_pause_ratios.num() != 0) {
    _recent_pause_ratios.add(short_term_ratio_delta);
  }
  _long_term_count++;

  log_trace(gc, ergo, heap)("Heap resize triggers: long term count: %u "
                            "long term interval: %u "
                            "delta: %1.2f "
                            "ratio exceeds threshold count: %d",
                            _long_term_count,
                            long_term_count_limit(),
                            short_term_ratio_delta,
                            _ratio_exceeds_threshold);

  log_debug(gc, ergo, heap)("Heap triggers: pauses-since-start: %u num-prev-pauses-for-heuristics: %u ratio-exceeds-threshold-count: %d",
                            _recent_pause_ratios.num(), long_term_count_limit(), _ratio_exceeds_threshold);

  // Check if there is a short- or long-term need for resizing, expansion first.
  //
  // Short-term resizing need is detected by exceeding the upper or lower thresholds
  // multiple times, tracked in _ratio_exceeds_threshold. If it contains a large
  // positive or negative (larger than the respective thresholds), we trigger
  // resizing calculation.
  //
  // Slowly occurring long-term changes to the actual gc time ratios are checked
  // only every once a while.
  //
  // The _ratio_exceeds_threshold value is reset after each resize, or slowly
  // decayed if nothing happens.

  size_t resize_bytes = 0;

  const bool use_long_term_delta = (_long_term_count == long_term_count_limit());
  const double short_term_delta = _recent_pause_ratios.avg();

  double delta;
  if (use_long_term_delta) {
    // For expansion, deltas are positive, and we want to expand aggressively.
    // For shrinking, deltas are negative, so the MAX2 below selects the least
    // aggressive one as we are using the absolute value for scaling.
    delta = MAX2(short_term_delta, long_term_delta);
  } else {
    delta = short_term_delta;
  }
  // Delta is negative when shrinking, but the calculation of the resize amount
  // always expects an absolute value. Do that here unconditionally.
  delta = fabsd(delta);

  int ThresholdForShrink = (int)MIN2(G1ShortTermShrinkThreshold, long_term_count_limit());

  if ((_ratio_exceeds_threshold == MinOverThresholdForExpansion) ||
      (use_long_term_delta && (long_term_pause_time_ratio > upper_threshold))) {

    // Short-cut calculation if already at maximum capacity.
    if (_g1h->capacity() == _g1h->max_capacity()) {
      log_resize(short_term_pause_time_ratio, long_term_pause_time_ratio,
                 lower_threshold, upper_threshold, pause_time_threshold, true, 0, expand);
      reset_ratio_tracking_data();
      return resize_bytes;
    }

    log_trace(gc, ergo, heap)("expand deltas long %1.2f short %1.2f use long term %u delta %1.2f",
                              long_term_delta, short_term_delta, use_long_term_delta, delta);

    resize_bytes = young_collection_expand_amount(delta);
    expand = true;

    reset_ratio_tracking_data();
  } else if ((_ratio_exceeds_threshold == -ThresholdForShrink) ||
             (use_long_term_delta && (long_term_pause_time_ratio < lower_threshold))) {

    // Short-cut calculation if already at minimum capacity.
    if (_g1h->capacity() == _g1h->min_capacity()) {
      log_resize(short_term_pause_time_ratio, long_term_pause_time_ratio,
                 lower_threshold, upper_threshold, pause_time_threshold, true, 0, expand);
      reset_ratio_tracking_data();
      return resize_bytes;
    }

    log_trace(gc, ergo, heap)("expand deltas long %1.2f short %1.2f use long term %u delta %1.2f",
                              long_term_delta, short_term_delta, use_long_term_delta, delta);

    resize_bytes = young_collection_shrink_amount(delta, allocation_word_size);
    expand = false;

    reset_ratio_tracking_data();
  } else if (use_long_term_delta) {
    // A resize has not been triggered, but the long term counter overflowed.
    decay_ratio_tracking_data();
    expand = true; // Does not matter.
  }

  log_resize(short_term_pause_time_ratio, long_term_pause_time_ratio,
             lower_threshold, upper_threshold, pause_time_threshold,
             false, resize_bytes, expand);

  return resize_bytes;
}

static size_t target_heap_capacity(size_t used_bytes, uintx free_ratio) {
  assert(free_ratio <= 100, "precondition");
  if (free_ratio == 100) {
    // If 100 then below calculations will divide by zero and return min of
    // resulting infinity and MaxHeapSize.  Avoid issues of UB vs is_iec559
    // and ubsan warnings, and just immediately return MaxHeapSize.
    return MaxHeapSize;
  }

  const double desired_free_percentage = (double) free_ratio / 100.0;
  const double desired_used_percentage = 1.0 - desired_free_percentage;

  // We have to be careful here as these two calculations can overflow
  // 32-bit size_t's.
  double used_bytes_d = (double) used_bytes;
  double desired_capacity_d = used_bytes_d / desired_used_percentage;
  // Let's make sure that they are both under the max heap size, which
  // by default will make it fit into a size_t.
  double desired_capacity_upper_bound = (double) MaxHeapSize;
  desired_capacity_d = MIN2(desired_capacity_d, desired_capacity_upper_bound);
  // We can now safely turn it into size_t's.
  return (size_t) desired_capacity_d;
}

size_t G1HeapSizingPolicy::full_collection_resize_amount(bool& expand, size_t allocation_word_size) {
  // If the full collection was triggered by an allocation failure, we should account
  // for the bytes required for this allocation under used_after_gc. This prevents
  // unnecessary shrinking that would be followed by an expand call to satisfy the
  // allocation.
  size_t allocation_bytes = allocation_word_size * HeapWordSize;
  if (_g1h->is_humongous(allocation_word_size)) {
    // Humongous objects are allocated in entire regions, we must calculate
    // required space in terms of full regions, not just the object size.
    allocation_bytes = G1HeapRegion::align_up_to_region_byte_size(allocation_bytes);
  }

  // Capacity, free and used after the GC counted as full regions to
  // include the waste in the following calculations.
  const size_t capacity_after_gc = _g1h->capacity();
  const size_t used_after_gc = capacity_after_gc + allocation_bytes -
                               _g1h->unused_committed_regions_in_bytes() -
                               // Discount space used by current Eden to establish a
                               // situation during Remark similar to at the end of full
                               // GC where eden is empty. During Remark there can be an
                               // arbitrary number of eden regions which would skew the
                               // results.
                               _g1h->eden_regions_count() * G1HeapRegion::GrainBytes;

  size_t minimum_desired_capacity = target_heap_capacity(used_after_gc, MinHeapFreeRatio);
  size_t maximum_desired_capacity = target_heap_capacity(used_after_gc, MaxHeapFreeRatio);

  // This assert only makes sense here, before we adjust them
  // with respect to the min and max heap size.
  assert(minimum_desired_capacity <= maximum_desired_capacity,
         "minimum_desired_capacity = %zu, "
         "maximum_desired_capacity = %zu",
         minimum_desired_capacity, maximum_desired_capacity);

  // Should not be greater than the heap max size. No need to adjust
  // it with respect to the heap min size as it's a lower bound (i.e.,
  // we'll try to make the capacity larger than it, not smaller).
  minimum_desired_capacity = MIN2(minimum_desired_capacity, _g1h->max_capacity());
  // Should not be less than the heap min size. No need to adjust it
  // with respect to the heap max size as it's an upper bound (i.e.,
  // we'll try to make the capacity smaller than it, not greater).
  maximum_desired_capacity =  MAX2(maximum_desired_capacity, _g1h->min_capacity());

  // Don't expand unless it's significant; prefer expansion to shrinking.
  if (capacity_after_gc < minimum_desired_capacity) {
    size_t expand_bytes = minimum_desired_capacity - capacity_after_gc;

    log_debug(gc, ergo, heap)("Heap resize. Attempt heap expansion (capacity lower than min desired capacity). "
                              "Capacity: %zuMB occupancy: %zuMB live: %zuMB "
                              "min_desired_capacity: %zuMB (%zu %%)",
                              capacity_after_gc / M, used_after_gc / M, _g1h->used() / M, minimum_desired_capacity / M, MinHeapFreeRatio);

    expand = true;
    return expand_bytes;
    // No expansion, now see if we want to shrink
  } else if (capacity_after_gc > maximum_desired_capacity) {
    // Capacity too large, compute shrinking size
    size_t shrink_bytes = capacity_after_gc - maximum_desired_capacity;

    log_debug(gc, ergo, heap)("Heap resize. Attempt heap shrinking (capacity higher than max desired capacity). "
                              "Capacity: %zuMB occupancy: %zuMB live: %zuMB "
                              "maximum_desired_capacity: %zuMB (%zu %%)",
                              capacity_after_gc / M, used_after_gc / M, _g1h->used() / M, maximum_desired_capacity / M, MaxHeapFreeRatio);

    expand = false;
    return shrink_bytes;
  }

  expand = true; // Does not matter.
  return 0;
}
