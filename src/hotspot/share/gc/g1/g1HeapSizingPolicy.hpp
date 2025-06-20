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

#ifndef SHARE_GC_G1_G1HEAPSIZINGPOLICY_HPP
#define SHARE_GC_G1_G1HEAPSIZINGPOLICY_HPP

#include "memory/allocation.hpp"
#include "utilities/numberSeq.hpp"

class G1Analytics;
class G1CollectedHeap;

//
// Contains heuristics to resize the heap, i.e. expand or shrink, during operation.
//
// For young collections, this heuristics is based on gc time ratio, i.e. trying
// to change the heap so that current gc time ratio stays approximately as
// selected  by the user.
//
// The heuristics tracks both short and long term GC behavior to effect heap size
// change.
//
// Short term tracking is based on the short-term gc time ratio i.e we  count
// events for which short-term gc time ratio is outside the range of
// [GCTimeRatio * (1 - G1MinimumPercentOfGCTimeRatio / 100), GCTimeRatio * (1 + G1MinimumPercentOfGCTimeRatio / 100)]
// If below that range, we decrement that counter, if above, we increment it.
//
// The intent of this mechanism is to filter short term events because heap sizing has
// some overhead.
//
// If that counter reaches the MinOverThresholdForExpansion we consider expansion,
// if that counter reaches -G1ShortTermShrinkThreshold we consider shrinking the heap.
//
// While doing so, we accumulate the relative difference to the midpoint of this range
// (GCTimeRatio) to guide the expansion/shrinking amount.
//
// Further, if there is no short-term based resizing event for a "long" time, we
// decay that counter, i.e. drop it towards zero again to avoid that previous
// intermediate length short term behavior followed by a quiet time and a single
// short term event causes unnecessary resizes.
//
// Long term behavior is solely managed by regularly comparing actual long term gc
// time ratio with the boundaries of above range in regular long term  intervals.
// If current long term gc time ratio is outside, expand or shrink  respectively.
//
// For full collections, we base resize decisions only on Min/MaxHeapFreeRatio.
//
class G1HeapSizingPolicy: public CHeapObj<mtGC> {
  // MinOverThresholdForExpansion defines the number of actual gc time
  // ratios over the upper and lower thresholds respectively.
  const static int MinOverThresholdForExpansion = 4;

  const G1CollectedHeap* _g1h;
  const G1Analytics* _analytics;

  uint long_term_count_limit() const;
  // Number of times short-term gc time ratio crossed the lower or upper threshold
  // recently; every time the upper threshold is exceeded, it is incremented,  and
  // decremented if the lower threshold is exceeded.
  int _ratio_exceeds_threshold;
  // Recent actual gc time ratios relative to the middle of lower and upper threshold.
  TruncatedSeq _recent_pause_ratios;
  uint _long_term_count;

  // Clear ratio tracking data used by resize_amount().
  void reset_ratio_tracking_data();
  // Decay (move towards "no changes") ratio tracking data.
  void decay_ratio_tracking_data();

  // Scale "full" gc time ratio threshold with heap size as we want to resize more
  // eagerly at small heap sizes.
  double scale_with_heap(double pause_time_threshold);

  // Scale the ratio delta depending on the relative difference from the target gc time ratio.
  double scale_resize_ratio_delta(double ratio_delta, double min_scale_down_factor, double max_scale_up_factor) const;

  size_t young_collection_expand_amount(double delta) const;
  size_t young_collection_shrink_amount(double delta, size_t allocation_word_size) const;

  G1HeapSizingPolicy(const G1CollectedHeap* g1h, const G1Analytics* analytics);
public:

  // Return by how many bytes the heap should be changed based on recent gc time
  // ratio after young collection. If expand is set, the heap should be expanded,
  // otherwise shrunk.
  size_t young_collection_resize_amount(bool& expand, size_t allocation_word_size);

  // Returns the amount of bytes to resize the heap; if expand is set, the heap
  // should by expanded by that amount, shrunk otherwise.
  size_t full_collection_resize_amount(bool& expand, size_t allocation_word_size);
  // Clear ratio tracking data used by expansion_amount().
  void clear_ratio_check_data();

  static G1HeapSizingPolicy* create(const G1CollectedHeap* g1h, const G1Analytics* analytics);
};

#endif // SHARE_GC_G1_G1HEAPSIZINGPOLICY_HPP
