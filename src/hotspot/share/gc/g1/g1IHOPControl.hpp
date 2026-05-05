/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1IHOPCONTROL_HPP
#define SHARE_GC_G1_G1IHOPCONTROL_HPP

#include "gc/g1/g1OldGenAllocationTracker.hpp"
#include "memory/allocation.hpp"
#include "utilities/numberSeq.hpp"

class G1Predictions;
class G1NewTracer;

// Implements two strategies for calculating the concurrent mark starting occupancy threshold:
// - Static mode: Uses a fixed percentage of the target heap occupancy.
// - Adaptive mode: Predicts a threshold based on allocation rates and marking durations
//   to ensure the target occupancy is never exceeded during marking.
class G1IHOPControl : public CHeapObj<mtGC> {
 private:
  const bool _is_adaptive;

  // The initial IHOP value relative to the target occupancy.
  double _initial_ihop_percent;

  // The target maximum occupancy of the heap. The target occupancy is the number
  // of bytes when marking should be finished and reclaim started.
  size_t _target_occupancy;

  // Percentage of maximum heap capacity we should avoid to touch
  const size_t _heap_reserve_percent;

  // Percentage of free heap that should be considered as waste.
  const size_t _heap_waste_percent;

  // Most recent complete mutator allocation period in seconds.
  double _last_allocation_time_s;
  const G1OldGenAllocationTracker* _old_gen_alloc_tracker;

  const G1Predictions* _predictor;
  // Wall-clock time in seconds from marking start to the first mixed GC,
  // excluding GC Pause time.
  TruncatedSeq _marking_start_to_mixed_time_s;
  // Old generation allocation rate in bytes per second.
  TruncatedSeq _old_gen_alloc_rate;

  // The most recent unrestrained size of the young gen. This is used as an additional
  // factor in the calculation of the threshold, as the threshold is based on
  // non-young gen occupancy at the end of GC. For the IHOP threshold, we need to
  // consider the young gen size during that time too.
  // Since we cannot know what young gen sizes are used in the future, we will just
  // use the current one. We expect that this one will be one with a fairly large size,
  // as there is no marking or mixed gc that could impact its size too much.
  size_t _expected_young_gen_at_first_mixed_gc;

  // Get a new prediction bounded below by zero from the given sequence.
  double predict(const TruncatedSeq* seq) const;

  bool have_enough_data_for_prediction() const;
  double last_marking_start_to_mixed_time_s() const;

  // The "effective" target occupancy the algorithm wants to keep until the start
  // of Mixed GCs. This is typically lower than the target occupancy, as the
  // algorithm needs to consider restrictions by the environment.
  size_t effective_target_occupancy() const;

 void print_log(size_t non_young_occupancy);
 void send_trace_event(G1NewTracer* tracer, size_t non_young_occupancy);

 public:
  G1IHOPControl(double ihop_percent,
                const G1OldGenAllocationTracker* old_gen_alloc_tracker,
                bool adaptive,
                const G1Predictions* predictor,
                size_t heap_reserve_percent,
                size_t heap_waste_percent);

  // Adjust target occupancy.
  void update_target_occupancy(size_t new_target_occupancy);

  void update_target_after_marking_phase();

  // Update allocation rate information and current expected young gen size for the
  // first mixed gc needed for the predictor. Allocation rate is given as the
  // separately passed in allocation increment and the time passed (mutator time)
  // for the latest allocation increment here. Allocation size is the memory needed
  // during the mutator before and the first mixed gc pause itself.
  // Contents include young gen at that point, and the memory required for evacuating
  // the collection set in that first mixed gc (including waste caused by PLAB
  // allocation etc.).
  void update_allocation_info(double allocation_time_s, size_t expected_young_gen_size);

  // Update the time spent in the mutator beginning from the end of concurrent start to
  // the first mixed gc.
  void add_marking_start_to_mixed_length(double length_s);

  // Get the current non-young occupancy at which concurrent marking should start.
  size_t old_gen_threshold_for_conc_mark_start() const;

  void report_statistics(G1NewTracer* tracer, size_t non_young_occupancy);
};

#endif // SHARE_GC_G1_G1IHOPCONTROL_HPP
