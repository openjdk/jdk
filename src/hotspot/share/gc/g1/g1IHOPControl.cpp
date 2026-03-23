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

double G1IHOPControl::predict(const TruncatedSeq* seq) const {
  assert(_is_adaptive, "precondition");
  assert(_predictor != nullptr, "precondition");

  return  _predictor->predict_zero_bounded(seq);
}

bool G1IHOPControl::have_enough_data_for_prediction() const {
  assert(_is_adaptive, "precondition");

  return ((size_t)_marking_times_s.num() >= G1AdaptiveIHOPNumInitialSamples) &&
         ((size_t)_allocation_rate_s.num() >= G1AdaptiveIHOPNumInitialSamples);
}

double G1IHOPControl::last_marking_length_s() const {
  return _marking_times_s.last();
}

size_t G1IHOPControl::actual_target_threshold() const {
  assert(_is_adaptive, "precondition");

  // The actual target threshold takes the heap reserve and the expected waste in
  // free space  into account.
  // _heap_reserve is that part of the total heap capacity that is reserved for
  // eventual promotion failure.
  // _heap_waste is the amount of space will never be reclaimed in any
  // heap, so can not be used for allocation during marking and must always be
  // considered.
  double safe_total_heap_percentage =
    MIN2((double)(_heap_reserve_percent + _heap_waste_percent), 100.0);

  return (size_t)MIN2(
    G1CollectedHeap::heap()->max_capacity() * (100.0 - safe_total_heap_percentage) / 100.0,
    _target_occupancy * (100.0 - _heap_waste_percent) / 100.0
  );
}

G1IHOPControl::G1IHOPControl(double ihop_percent,
                             const G1OldGenAllocationTracker* old_gen_alloc_tracker,
                             bool adaptive,
                             const G1Predictions* predictor,
                             size_t heap_reserve_percent,
                             size_t heap_waste_percent)
  : _is_adaptive(adaptive),
    _initial_ihop_percent(ihop_percent),
    _target_occupancy(0),
    _heap_reserve_percent(heap_reserve_percent),
    _heap_waste_percent(heap_waste_percent),
    _last_allocation_time_s(0.0),
    _old_gen_alloc_tracker(old_gen_alloc_tracker),
    _predictor(predictor),
    _marking_times_s(10, 0.05),
    _allocation_rate_s(10, 0.05),
    _last_unrestrained_young_size(0) {
  assert(_initial_ihop_percent >= 0.0 && _initial_ihop_percent <= 100.0,
         "IHOP percent out of range: %.3f", ihop_percent);
  assert(!_is_adaptive || _predictor != nullptr, "precondition");
}

void G1IHOPControl::update_target_occupancy(size_t new_target_occupancy) {
  log_debug(gc, ihop)("Target occupancy update: old: %zuB, new: %zuB",
                      _target_occupancy, new_target_occupancy);
  _target_occupancy = new_target_occupancy;
}

void G1IHOPControl::report_statistics(G1NewTracer* new_tracer, size_t non_young_occupancy) {
  print_log(non_young_occupancy);
  send_trace_event(new_tracer, non_young_occupancy);
}

void G1IHOPControl::update_allocation_info(double allocation_time_s, size_t additional_buffer_size) {
  assert(allocation_time_s > 0, "Invalid allocation time: %.3f", allocation_time_s);
  _last_allocation_time_s = allocation_time_s;
  double alloc_rate = _old_gen_alloc_tracker->last_period_old_gen_growth() / allocation_time_s;
  _allocation_rate_s.add(alloc_rate);
  _last_unrestrained_young_size = additional_buffer_size;
}

void G1IHOPControl::update_marking_length(double marking_length_s) {
  assert(marking_length_s >= 0.0, "Invalid marking length: %.3f", marking_length_s);
  _marking_times_s.add(marking_length_s);
}

size_t G1IHOPControl::get_conc_mark_start_threshold() {
  guarantee(_target_occupancy > 0, "Target occupancy must be initialized");

  if (!_is_adaptive || !have_enough_data_for_prediction()) {
    return (size_t)(_initial_ihop_percent * _target_occupancy / 100.0);
  }

  double pred_marking_time = predict(&_marking_times_s);
  double pred_rate = predict(&_allocation_rate_s);
  size_t pred_bytes = (size_t)(pred_marking_time * pred_rate);
  size_t predicted_needed = pred_bytes + _last_unrestrained_young_size;
  size_t internal_threshold = actual_target_threshold();

  return predicted_needed < internal_threshold
         ? internal_threshold - predicted_needed
         : 0;
}

void G1IHOPControl::print_log(size_t non_young_occupancy) {
  assert(_target_occupancy > 0, "Target occupancy still not updated yet.");
  size_t cur_conc_mark_start_threshold = get_conc_mark_start_threshold();
  log_debug(gc, ihop)("Basic information (value update), threshold: %zuB (%1.2f), target occupancy: %zuB, non-young occupancy: %zuB, "
                      "recent allocation size: %zuB, recent allocation duration: %1.2fms, recent old gen allocation rate: %1.2fB/s, recent marking phase length: %1.2fms",
                      cur_conc_mark_start_threshold,
                      percent_of(cur_conc_mark_start_threshold, _target_occupancy),
                      _target_occupancy,
                      non_young_occupancy,
                      _old_gen_alloc_tracker->last_period_old_gen_bytes(),
                      _last_allocation_time_s * 1000.0,
                      _last_allocation_time_s > 0.0 ? _old_gen_alloc_tracker->last_period_old_gen_bytes() / _last_allocation_time_s : 0.0,
                      last_marking_length_s() * 1000.0);

  if (!_is_adaptive) {
    return;
  }

  size_t actual_threshold = actual_target_threshold();
  log_debug(gc, ihop)("Adaptive IHOP information (value update), threshold: %zuB (%1.2f), internal target threshold: %zuB, "
                      "non-young occupancy: %zuB, additional buffer size: %zuB, predicted old gen allocation rate: %1.2fB/s, "
                      "predicted marking phase length: %1.2fms, prediction active: %s",
                      cur_conc_mark_start_threshold,
                      percent_of(cur_conc_mark_start_threshold, actual_threshold),
                      actual_threshold,
                      non_young_occupancy,
                      _last_unrestrained_young_size,
                      predict(&_allocation_rate_s),
                      predict(&_marking_times_s) * 1000.0,
                      have_enough_data_for_prediction() ? "true" : "false");
}

void G1IHOPControl::send_trace_event(G1NewTracer* tracer, size_t non_young_occupancy) {
  assert(_target_occupancy > 0, "Target occupancy still not updated yet.");
  tracer->report_basic_ihop_statistics(get_conc_mark_start_threshold(),
                                       _target_occupancy,
                                       non_young_occupancy,
                                       _old_gen_alloc_tracker->last_period_old_gen_bytes(),
                                       _last_allocation_time_s,
                                       last_marking_length_s());

  if (_is_adaptive) {
    tracer->report_adaptive_ihop_statistics(get_conc_mark_start_threshold(),
                                            actual_target_threshold(),
                                            non_young_occupancy,
                                            _last_unrestrained_young_size,
                                            predict(&_allocation_rate_s),
                                            predict(&_marking_times_s),
                                            have_enough_data_for_prediction());
  }
}
