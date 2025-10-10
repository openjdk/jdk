/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1IHOPControl.hpp"
#include "gc/g1/g1Predictions.hpp"
#include "gc/g1/g1Trace.hpp"
#include "logging/log.hpp"

G1IHOPControl::G1IHOPControl(double initial_ihop_percent,
                             G1OldGenAllocationTracker const* old_gen_alloc_tracker) :
  _current_heap_size(0),
  _current_soft_max_heap_size(0),
  _initial_ihop_percent(initial_ihop_percent),
  _last_allocation_time_s(0.0),
  _old_gen_alloc_tracker(old_gen_alloc_tracker)
{
  assert(_initial_ihop_percent >= 0.0 && _initial_ihop_percent <= 100.0, "Initial IHOP value must be between 0 and 100 but is %.3f", initial_ihop_percent);
}

size_t G1IHOPControl::target_occupancy() const {
  return MIN2(_current_heap_size, _current_soft_max_heap_size);
}

size_t G1IHOPControl::current_heap_size() const {
  return _current_heap_size;
}

size_t G1IHOPControl::current_soft_max_heap_size() const {
  return _current_soft_max_heap_size;
}

size_t G1IHOPControl::get_conc_mark_start_threshold() {
  _current_soft_max_heap_size = G1CollectedHeap::heap()->soft_max_capacity();
  return get_conc_mark_start_threshold_internal();
}

void G1IHOPControl::update_heap_size(size_t new_heap_size) {
  log_debug(gc, ihop)("Target occupancy update: old: %zuB, new: %zuB",
                      _current_heap_size, new_heap_size);
  _current_heap_size = new_heap_size;
  _current_soft_max_heap_size = G1CollectedHeap::heap()->soft_max_capacity();
}

void G1IHOPControl::update_allocation_info(double allocation_time_s, size_t additional_buffer_size) {
  assert(allocation_time_s >= 0.0, "Allocation time must be positive but is %.3f", allocation_time_s);

  _last_allocation_time_s = allocation_time_s;
}

void G1IHOPControl::print() {
  assert(_current_heap_size > 0, "Heap size occupancy still not updated yet.");
  size_t cur_conc_mark_start_threshold = get_conc_mark_start_threshold_internal();
  log_debug(gc, ihop)("Basic information (value update), threshold: %zuB (%1.2f), target occupancy: %zuB, heap used: %zuB, heap size: %zuB, soft max size: %zuB, "
                      "recent allocation size: %zuB, recent allocation duration: %1.2fms, recent old gen allocation rate: %1.2fB/s, recent marking phase length: %1.2fms",
                      cur_conc_mark_start_threshold,
                      percent_of(cur_conc_mark_start_threshold, target_occupancy()),
                      target_occupancy(),
                      G1CollectedHeap::heap()->used(),
                      current_heap_size(),
                      current_soft_max_heap_size(),
                      _old_gen_alloc_tracker->last_period_old_gen_bytes(),
                      _last_allocation_time_s * 1000.0,
                      _last_allocation_time_s > 0.0 ? _old_gen_alloc_tracker->last_period_old_gen_bytes() / _last_allocation_time_s : 0.0,
                      last_marking_length_s() * 1000.0);
}

void G1IHOPControl::send_trace_event(G1NewTracer* tracer) {
  assert(_current_heap_size > 0, "Heap size still not updated yet.");
  tracer->report_basic_ihop_statistics(get_conc_mark_start_threshold_internal(),
                                       target_occupancy(),
                                       G1CollectedHeap::heap()->used(),
                                       current_heap_size(),
                                       current_soft_max_heap_size(),
                                       _old_gen_alloc_tracker->last_period_old_gen_bytes(),
                                       _last_allocation_time_s,
                                       last_marking_length_s());
}

G1StaticIHOPControl::G1StaticIHOPControl(double ihop_percent,
                                         G1OldGenAllocationTracker const* old_gen_alloc_tracker) :
  G1IHOPControl(ihop_percent, old_gen_alloc_tracker),
  _last_marking_length_s(0.0) {
}

G1AdaptiveIHOPControl::G1AdaptiveIHOPControl(double ihop_percent,
                                             G1OldGenAllocationTracker const* old_gen_alloc_tracker,
                                             G1Predictions const* predictor,
                                             size_t heap_reserve_percent,
                                             size_t heap_waste_percent) :
  G1IHOPControl(ihop_percent, old_gen_alloc_tracker),
  _heap_reserve_percent(heap_reserve_percent),
  _heap_waste_percent(heap_waste_percent),
  _predictor(predictor),
  _marking_times_s(10, 0.05),
  _allocation_rate_s(10, 0.05),
  _last_unrestrained_young_size(0)
{
}

size_t G1AdaptiveIHOPControl::actual_target_threshold() const {
  guarantee(current_heap_size() > 0, "Target occupancy still not updated yet.");
  // The actual target threshold takes the heap reserve and the expected waste in
  // free space  into account.
  // _heap_reserve is that part of the total heap capacity that is reserved for
  // eventual promotion failure.
  // _heap_waste is the amount of space will never be reclaimed in any
  // heap, so can not be used for allocation during marking and must always be
  // considered.

  double safe_total_heap_percentage = MIN2((double)(_heap_reserve_percent + _heap_waste_percent), 100.0);

  return (size_t)MIN2(
    G1CollectedHeap::heap()->max_capacity() * (100.0 - safe_total_heap_percentage) / 100.0,
    target_occupancy() * (100.0 - _heap_waste_percent) / 100.0
    );
}

double G1AdaptiveIHOPControl::predict(TruncatedSeq const* seq) const {
  return _predictor->predict_zero_bounded(seq);
}

bool G1AdaptiveIHOPControl::have_enough_data_for_prediction() const {
  return ((size_t)_marking_times_s.num() >= G1AdaptiveIHOPNumInitialSamples) &&
         ((size_t)_allocation_rate_s.num() >= G1AdaptiveIHOPNumInitialSamples);
}

size_t G1AdaptiveIHOPControl::get_conc_mark_start_threshold_internal() {
  if (have_enough_data_for_prediction()) {
    double pred_marking_time = predict(&_marking_times_s);
    double pred_promotion_rate = predict(&_allocation_rate_s);
    size_t pred_promotion_size = (size_t)(pred_marking_time * pred_promotion_rate);

    size_t predicted_needed_bytes_during_marking =
      pred_promotion_size +
      // In reality we would need the maximum size of the young gen during
      // marking. This is a conservative estimate.
      _last_unrestrained_young_size;

    size_t internal_threshold = actual_target_threshold();
    size_t predicted_initiating_threshold = predicted_needed_bytes_during_marking < internal_threshold ?
                                            internal_threshold - predicted_needed_bytes_during_marking :
                                            0;
    return predicted_initiating_threshold;
  } else {
    // Use the initial value.
    return (size_t)(_initial_ihop_percent * target_occupancy() / 100.0);
  }
}

double G1AdaptiveIHOPControl::last_mutator_period_old_allocation_rate() const {
  assert(_last_allocation_time_s > 0, "This should not be called when the last GC is full");

  return _old_gen_alloc_tracker->last_period_old_gen_growth() / _last_allocation_time_s;
}

void G1AdaptiveIHOPControl::update_allocation_info(double allocation_time_s,
                                                   size_t additional_buffer_size) {
  G1IHOPControl::update_allocation_info(allocation_time_s, additional_buffer_size);
  _allocation_rate_s.add(last_mutator_period_old_allocation_rate());

  _last_unrestrained_young_size = additional_buffer_size;
}

void G1AdaptiveIHOPControl::update_marking_length(double marking_length_s) {
   assert(marking_length_s >= 0.0, "Marking length must be larger than zero but is %.3f", marking_length_s);
  _marking_times_s.add(marking_length_s);
}

void G1AdaptiveIHOPControl::print() {
  G1IHOPControl::print();
  size_t actual_target = actual_target_threshold();
  log_debug(gc, ihop)("Adaptive IHOP information (value update), threshold: %zuB (%1.2f), internal target occupancy: %zuB, "
                      "occupancy: %zuB, heap size: %zuB, soft max size: %zuB, additional buffer size: %zuB, predicted old gen allocation rate: %1.2fB/s, "
                      "predicted marking phase length: %1.2fms, prediction active: %s",
                      get_conc_mark_start_threshold_internal(),
                      percent_of(get_conc_mark_start_threshold_internal(), actual_target),
                      actual_target,
                      G1CollectedHeap::heap()->used(),
                      current_heap_size(),
                      current_soft_max_heap_size(),
                      _last_unrestrained_young_size,
                      predict(&_allocation_rate_s),
                      predict(&_marking_times_s) * 1000.0,
                      have_enough_data_for_prediction() ? "true" : "false");
}

void G1AdaptiveIHOPControl::send_trace_event(G1NewTracer* tracer) {
  G1IHOPControl::send_trace_event(tracer);
  tracer->report_adaptive_ihop_statistics(get_conc_mark_start_threshold_internal(),
                                          actual_target_threshold(),
                                          G1CollectedHeap::heap()->used(),
                                          current_heap_size(),
                                          current_soft_max_heap_size(),
                                          _last_unrestrained_young_size,
                                          predict(&_allocation_rate_s),
                                          predict(&_marking_times_s),
                                          have_enough_data_for_prediction());
}
