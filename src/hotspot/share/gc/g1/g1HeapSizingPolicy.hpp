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

#include "gc/g1/g1Analytics.hpp"
#include "memory/allocation.hpp"
#include "utilities/numberSeq.hpp"

class G1CollectedHeap;

//
// Contains heuristics to resize the heap, i.e. expand or shrink, during operation.
//
// For young collections, this heuristics is based on GC CPU usage, i.e. trying to
// change the heap so that the GC CPU usage stays approximately close to the target
// GC CPU usage set by the user.
//
// The heuristics track both short and long term GC behavior to affect heap resizing.
//
// Short term tracking is based on the short-term GC CPU usage i.e we count events
// for which short-term GC CPU usage is outside the range:
// gc_cpu_usage_target × [1 - d, 1 + d], where d = G1CPUUsageDeviationPercent / 100
// If below that range, we decrement that counter, if above, we increment it.
//
// If that counter reaches the G1CPUUsageExpandThreshold we consider expansion,
// if that counter reaches -G1CPUUsageShrinkThreshold we consider shrinking the heap.
//
// While doing so, we accumulate the relative difference to the target GC CPU usage
// to guide the expansion/shrinking amount.
//
// Furthermore, if there is no short-term based resizing event for a "long" time,
// we decay that counter, i.e. drop it towards zero again to avoid that previous
// intermediate length short term behavior followed by a quiet time and a single
// short term event causes unnecessary resizes.
//
// Long term behavior is solely managed by regularly comparing actual long term
// GC CPU usage with the boundaries of acceptable deviation range. If the actual
// long term GC CPU usage is outside this range, expand or shrink accordingly.
//
// The mechanism is meant to filter out short term events because heap resizing
// has some overhead.
//
// For full collections, we base resize decisions only on Min/MaxHeapFreeRatio.
//
class G1HeapSizingPolicy: public CHeapObj<mtGC> {
  const G1CollectedHeap* _g1h;
  const G1Analytics* _analytics;

  // Number of times short-term GC CPU usage crossed the lower or upper threshold
  // recently; every time the upper threshold is exceeded, it is incremented, and
  // decremented if the lower threshold is exceeded.
  int _gc_cpu_usage_deviation_counter;
  // Recent GC CPU usage deviations relative to the gc_cpu_usage_target
  TruncatedSeq _recent_cpu_usage_deltas;
  uint _long_term_count;

  // Clear GC CPU usage tracking data used by young_collection_resize_amount().
  void reset_cpu_usage_tracking_data();
  // Decay (move towards "no changes") GC CPU usage tracking data.
  void decay_cpu_usage_tracking_data();

  // Scale the gc_cpu_usage_target with heap size as we want to resize more
  // eagerly at small heap sizes.
  double scale_with_heap(double gc_cpu_usage_target);

  // Scale the cpu usage delta depending on the relative difference from the target gc_cpu_usage.
  double scale_cpu_usage_delta(double cpu_usage_delta, double min_scale_factor, double max_scale_factor) const;

  size_t young_collection_expand_amount(double cpu_usage_delta) const;
  size_t young_collection_shrink_amount(double cpu_usage_delta, size_t allocation_word_size) const;

  G1HeapSizingPolicy(const G1CollectedHeap* g1h, const G1Analytics* analytics);
public:

  static constexpr uint long_term_count_limit() {
    return G1Analytics::max_num_of_recorded_pause_times();
  }
  // Return by how many bytes the heap should be changed based on recent GC CPU
  // usage after young collection. If expand is set, the heap should be expanded,
  // otherwise shrunk.
  size_t young_collection_resize_amount(bool& expand, size_t allocation_word_size);

  // Returns the amount of bytes to resize the heap; if expand is set, the heap
  // should by expanded by that amount, shrunk otherwise.
  size_t full_collection_resize_amount(bool& expand, size_t allocation_word_size);

  static G1HeapSizingPolicy* create(const G1CollectedHeap* g1h, const G1Analytics* analytics);
};

#endif // SHARE_GC_G1_G1HEAPSIZINGPOLICY_HPP
