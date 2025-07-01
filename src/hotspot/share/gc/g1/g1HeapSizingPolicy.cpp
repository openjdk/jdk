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

G1HeapSizingPolicy::G1HeapSizingPolicy(const G1CollectedHeap* g1h, const G1Analytics* analytics) :
  _g1h(g1h),
  _analytics(analytics),
  // Bias for expansion at startup; the +1 is to counter the first sample always
  // being 0.0, i.e. lower than any threshold.
  _gc_cpu_usage_deviation_counter((G1CPUUsageExpandThreshold / 2) + 1),
  _recent_cpu_usage_deltas(long_term_count_limit()),
  _long_term_count(0) {
}

void G1HeapSizingPolicy::reset_cpu_usage_tracking_data() {
  _long_term_count = 0;
  _gc_cpu_usage_deviation_counter = 0;
  // Keep the recent GC CPU usage data.
}

void G1HeapSizingPolicy::decay_cpu_usage_tracking_data() {
  _long_term_count = 0;
  _gc_cpu_usage_deviation_counter /= 2;
  // Keep the recent GC CPU usage data.
}

double G1HeapSizingPolicy::scale_with_heap(double gc_cpu_usage_target) {
  double target = gc_cpu_usage_target;
  // If the heap is at less than half its maximum size, scale the threshold down,
  // to a limit of 1%. Thus the smaller the heap is, the more likely it is to expand,
  // though the scaling code will likely keep the increase small.
  if (_g1h->capacity() <= _g1h->max_capacity() / 2) {
    target *= (double)_g1h->capacity() / (double)(_g1h->max_capacity() / 2);
    target = MAX2(target, 0.01);
  }

  return target;
}

static void log_resize(double short_term_cpu_usage,
                       double long_term_cpu_usage,
                       double lower_threshold,
                       double upper_threshold,
                       double cpu_usage_target,
                       bool at_limit,
                       size_t resize_bytes,
                       bool expand) {

  log_debug(gc, ergo, heap)("Heap resize: "
                            "short term GC CPU usage %1.2f%% long term GC CPU usage %1.2f%% "
                            "lower threshold %1.2f%% upper threshold %1.2f%% GC CPU usage target %1.2f%% "
                            "at limit %s resize by %zuB expand %s",
                            short_term_cpu_usage * 100.0,
                            long_term_cpu_usage * 100.0,
                            lower_threshold * 100.0,
                            upper_threshold * 100.0,
                            cpu_usage_target * 100.0,
                            BOOL_TO_STR(at_limit),
                            resize_bytes,
                            BOOL_TO_STR(expand));
}

// Logistic function, returns values in the range [0,1]
static double sigmoid_function(double value) {
  // Sigmoid Parameters:
  double inflection_point = 1.0; // Inflection point (midpoint of the sigmoid).
  double steepness = 6.0;
  return 1.0 / (1.0 + exp(-steepness * (value - inflection_point)));
}

// Computes a smooth scaling factor based on the relative deviation of actual gc_cpu_usage
// from the gc_cpu_usage_target, using a sigmoid function to transition between
// the specified minimum and maximum scaling factors.
//
// The input cpu_usage_delta represents the relative deviation of the current gc_cpu_usage to the
// gc_cpu_usage_target. This value is passed through a sigmoid function that produces a smooth
// output between 0 and 1, which is then scaled to the range [min_scale_factor, max_scale_factor].
//
// The sigmoid's inflection point is set at cpu_usage_delta = 1.0 (a 100% deviation), where the scaling
// response increases most rapidly.
//
// The steepness parameter controls how sharply the scale factor changes near the inflection point.
//  * Low steepness (1-3): gradual scaling over a wide range of deviations (more conservative).
//  * High steepness (7-10): rapid scaling near the inflection point; small deviations result
//                           in very low scaling, but larger deviations ramp up scaling quickly.
//                           Steepness at 10 is nearly a step function.
//
// In this case, we choose a steepness of 6.0:
// - For small deviations, the sigmoid output is close to 0, resulting in scale factors near the
//   lower bound, preventing excessive resizing.
// - As cpu_usage_delta grows toward 1.0, the steepness value makes the transition sharper, enabling
//   more aggressive scaling for large deviations.
//
// This helps avoid overreacting to small gc_cpu_usage deviations but respond appropriately
// when necessary.
double G1HeapSizingPolicy::scale_cpu_usage_delta(double cpu_usage_delta,
                                                 double min_scale_factor,
                                                 double max_scale_factor) const {
  double sigmoid = sigmoid_function(cpu_usage_delta);

  double scale_factor = min_scale_factor + (max_scale_factor - min_scale_factor) * sigmoid;
  return scale_factor;
}

// Calculate the relative difference between a and b.
static double rel_diff(double a, double b) {
  return (a - b) / b;
}

size_t G1HeapSizingPolicy::young_collection_expand_amount(double cpu_usage_delta) const {
  assert(cpu_usage_delta >= 0.0, "must be");

  size_t reserved_bytes = _g1h->max_capacity();
  size_t committed_bytes = _g1h->capacity();
  size_t uncommitted_bytes = reserved_bytes - committed_bytes;
  size_t expand_bytes_via_pct = uncommitted_bytes * G1ExpandByPercentOfAvailable / 100;
  size_t min_expand_bytes = MIN2(G1HeapRegion::GrainBytes, uncommitted_bytes);

  // Take the current size or G1ExpandByPercentOfAvailable % of
  // the available expansion space, whichever is smaller, as the base
  // expansion size. Then possibly scale this size according to how much the
  // GC CPU usage (on average) has exceeded the target.
  const double min_scale_factor = 0.2;
  const double max_scale_factor = 2.0;

  double scale_factor = scale_cpu_usage_delta(cpu_usage_delta,
                                              min_scale_factor,
                                              max_scale_factor);

  size_t resize_bytes = MIN2(expand_bytes_via_pct, committed_bytes);

  resize_bytes = static_cast<size_t>(resize_bytes * scale_factor);

  // Ensure the expansion size is at least the minimum growth amount
  // and at most the remaining uncommitted byte size.
  return clamp(resize_bytes, min_expand_bytes, uncommitted_bytes);
}

size_t G1HeapSizingPolicy::young_collection_shrink_amount(double cpu_usage_delta, size_t allocation_word_size) const {
  assert(cpu_usage_delta >= 0.0, "must be");

  const double max_scale_factor = G1ShrinkByPercentOfAvailable / 100.0;
  const double min_scale_factor = max_scale_factor / 10.0;

  double scale_factor = scale_cpu_usage_delta(cpu_usage_delta,
                                              min_scale_factor,
                                              max_scale_factor);
  assert(scale_factor <= max_scale_factor, "must be");

  // We are at the end of GC, so free regions are at maximum. Do not try to shrink
  // to have less than the reserve or the number of regions we are most certainly
  // going to use during this mutator phase.
  uint target_regions_to_shrink = _g1h->num_free_regions();

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
                            "needed for alloc %u "
                            "base targeted for shrinking %u "
                            "resize_bytes %zd ( %zu regions)",
                            scale_factor * 100.0,
                            _g1h->num_free_regions(),
                            needed_for_allocation,
                            target_regions_to_shrink,
                            resize_bytes,
                            (resize_bytes / G1HeapRegion::GrainBytes));

  return resize_bytes;
}

size_t G1HeapSizingPolicy::young_collection_resize_amount(bool& expand, size_t allocation_word_size) {
  assert(GCTimeRatio > 0, "must be");
  expand = false;

  const double long_term_gc_cpu_usage = _analytics->long_term_pause_time_ratio();
  const double short_term_gc_cpu_usage = _analytics->short_term_pause_time_ratio();

  double gc_cpu_usage_target = 1.0 / (1.0 + GCTimeRatio);
  gc_cpu_usage_target = scale_with_heap(gc_cpu_usage_target);

  // Calculate gc_cpu_usage acceptable deviation thresholds:
  // - upper_threshold, do not want to exceed this.
  // - lower_threshold, we do not want to go below.
  const double gc_cpu_usage_margin = G1CPUUsageDeviationPercent / 100.0;
  const double upper_threshold = gc_cpu_usage_target * (1 + gc_cpu_usage_margin);
  const double lower_threshold = gc_cpu_usage_target * (1 - gc_cpu_usage_margin);

  // Decide to expand/shrink based on how far the current GC CPU usage deviates
  // from the target. This allows the policy to respond more quickly to GC pressure
  // when the heap is small relative to the maximum heap.
  const double long_term_delta = rel_diff(long_term_gc_cpu_usage, gc_cpu_usage_target);
  const double short_term_delta = rel_diff(short_term_gc_cpu_usage, gc_cpu_usage_target);

  // If the short term GC CPU usage exceeds the upper threshold, increment the deviation
  // counter. If it falls below the lower_threshold, decrement the deviation counter.
  if (short_term_gc_cpu_usage > upper_threshold) {
    _gc_cpu_usage_deviation_counter++;
  } else if (short_term_gc_cpu_usage < lower_threshold) {
    _gc_cpu_usage_deviation_counter--;
  }
  // Ignore very first sample as it is garbage.
  if (_long_term_count != 0 || _recent_cpu_usage_deltas.num() != 0) {
    _recent_cpu_usage_deltas.add(short_term_delta);
  }
  _long_term_count++;

  log_trace(gc, ergo, heap)("Heap resize triggers: long term count: %u "
                            "long term count limit: %u "
                            "short term delta: %1.2f "
                            "recent recorded short term deltas: %u"
                            "GC CPU usage deviation counter: %d",
                            _long_term_count,
                            long_term_count_limit(),
                            short_term_delta,
                            _recent_cpu_usage_deltas.num(),
                            _gc_cpu_usage_deviation_counter);

  // Check if there is a short- or long-term need for resizing, expansion first.
  //
  // Short-term resizing need is detected by exceeding the upper or lower thresholds
  // multiple times, tracked in _gc_cpu_usage_deviation_counter. If it contains a large
  // positive or negative (larger than the respective thresholds), we trigger
  // resizing calculation.
  //
  // Slowly occurring long-term changes to the actual GC CPU usage are checked
  // only every once in a while.
  //
  // The _gc_cpu_usage_deviation_counter value is reset after each resize, or slowly
  // decayed if no resizing happens.

  size_t resize_bytes = 0;

  const bool use_long_term_delta = (_long_term_count == long_term_count_limit());
  const double avg_short_term_delta = _recent_cpu_usage_deltas.avg();

  double delta;
  if (use_long_term_delta) {
    // For expansion, deltas are positive, and we want to expand aggressively.
    // For shrinking, deltas are negative, so the MAX2 below selects the least
    // aggressive one as we are using the absolute value for scaling.
    delta = MAX2(avg_short_term_delta, long_term_delta);
  } else {
    delta = avg_short_term_delta;
  }
  // Delta is negative when shrinking, but the calculation of the resize amount
  // always expects an absolute value. Do that here unconditionally.
  delta = fabsd(delta);

  int count_threshold_for_shrink = (int)G1CPUUsageShrinkThreshold;

  if ((_gc_cpu_usage_deviation_counter >= (int)G1CPUUsageExpandThreshold) ||
      (use_long_term_delta && (long_term_gc_cpu_usage > upper_threshold))) {
    expand = true;

    // Short-cut calculation if already at maximum capacity.
    if (_g1h->capacity() == _g1h->max_capacity()) {
      log_resize(short_term_gc_cpu_usage, long_term_gc_cpu_usage,
                 lower_threshold, upper_threshold, gc_cpu_usage_target, true, 0, expand);
      reset_cpu_usage_tracking_data();
      return resize_bytes;
    }

    log_trace(gc, ergo, heap)("expand deltas long %1.2f short %1.2f use long term %u delta %1.2f",
                              long_term_delta, avg_short_term_delta, use_long_term_delta, delta);

    resize_bytes = young_collection_expand_amount(delta);

    reset_cpu_usage_tracking_data();
  } else if ((_gc_cpu_usage_deviation_counter <= -count_threshold_for_shrink) ||
             (use_long_term_delta && (long_term_gc_cpu_usage < lower_threshold))) {
    expand = false;
    // Short-cut calculation if already at minimum capacity.
    if (_g1h->capacity() == _g1h->min_capacity()) {
      log_resize(short_term_gc_cpu_usage, long_term_gc_cpu_usage,
                 lower_threshold, upper_threshold, gc_cpu_usage_target, true, 0, expand);
      reset_cpu_usage_tracking_data();
      return resize_bytes;
    }

    log_trace(gc, ergo, heap)("expand deltas long %1.2f short %1.2f use long term %u delta %1.2f",
                              long_term_delta, avg_short_term_delta, use_long_term_delta, delta);

    resize_bytes = young_collection_shrink_amount(delta, allocation_word_size);

    reset_cpu_usage_tracking_data();
  } else if (use_long_term_delta) {
    // A resize has not been triggered, but the long term counter overflowed.
    decay_cpu_usage_tracking_data();
    expand = false; // Does not matter.
  }

  log_resize(short_term_gc_cpu_usage, long_term_gc_cpu_usage,
             lower_threshold, upper_threshold, gc_cpu_usage_target,
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
  maximum_desired_capacity = MAX2(maximum_desired_capacity, _g1h->min_capacity());

  // Don't expand unless it's significant; prefer expansion to shrinking.
  if (capacity_after_gc < minimum_desired_capacity) {
    size_t expand_bytes = minimum_desired_capacity - capacity_after_gc;

    log_debug(gc, ergo, heap)("Heap resize. Attempt heap expansion (capacity lower than min desired capacity). "
                              "Capacity: %zuB occupancy: %zuB live: %zuB "
                              "min_desired_capacity: %zuB (%zu %%)",
                              capacity_after_gc, used_after_gc, _g1h->used(), minimum_desired_capacity, MinHeapFreeRatio);

    expand = true;
    return expand_bytes;
    // No expansion, now see if we want to shrink
  } else if (capacity_after_gc > maximum_desired_capacity) {
    // Capacity too large, compute shrinking size
    size_t shrink_bytes = capacity_after_gc - maximum_desired_capacity;

    log_debug(gc, ergo, heap)("Heap resize. Attempt heap shrinking (capacity higher than max desired capacity). "
                              "Capacity: %zuB occupancy: %zuB live: %zuB "
                              "maximum_desired_capacity: %zuB (%zu %%)",
                              capacity_after_gc, used_after_gc, _g1h->used(), maximum_desired_capacity, MaxHeapFreeRatio);

    expand = false;
    return shrink_bytes;
  }

  expand = true; // Does not matter.
  return 0;
}
