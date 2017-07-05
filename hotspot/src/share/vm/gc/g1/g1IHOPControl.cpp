/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1IHOPControl.hpp"
#include "gc/g1/g1Predictions.hpp"
#include "gc/shared/gcTrace.hpp"
#include "logging/log.hpp"

G1IHOPControl::G1IHOPControl(double initial_ihop_percent) :
  _initial_ihop_percent(initial_ihop_percent),
  _target_occupancy(0),
  _last_allocated_bytes(0),
  _last_allocation_time_s(0.0)
{
  assert(_initial_ihop_percent >= 0.0 && _initial_ihop_percent <= 100.0, "Initial IHOP value must be between 0 and 100 but is %.3f", initial_ihop_percent);
}

void G1IHOPControl::update_target_occupancy(size_t new_target_occupancy) {
  log_debug(gc, ihop)("Target occupancy update: old: " SIZE_FORMAT "B, new: " SIZE_FORMAT "B",
                      _target_occupancy, new_target_occupancy);
  _target_occupancy = new_target_occupancy;
}

void G1IHOPControl::update_allocation_info(double allocation_time_s, size_t allocated_bytes, size_t additional_buffer_size) {
  assert(allocation_time_s >= 0.0, "Allocation time must be positive but is %.3f", allocation_time_s);

  _last_allocation_time_s = allocation_time_s;
  _last_allocated_bytes = allocated_bytes;
}

void G1IHOPControl::print() {
  assert(_target_occupancy > 0, "Target occupancy still not updated yet.");
  size_t cur_conc_mark_start_threshold = get_conc_mark_start_threshold();
  log_debug(gc, ihop)("Basic information (value update), threshold: " SIZE_FORMAT "B (%1.2f), target occupancy: " SIZE_FORMAT "B, current occupancy: " SIZE_FORMAT "B, "
                      "recent allocation size: " SIZE_FORMAT "B, recent allocation duration: %1.2fms, recent old gen allocation rate: %1.2fB/s, recent marking phase length: %1.2fms",
                      cur_conc_mark_start_threshold,
                      cur_conc_mark_start_threshold * 100.0 / _target_occupancy,
                      _target_occupancy,
                      G1CollectedHeap::heap()->used(),
                      _last_allocated_bytes,
                      _last_allocation_time_s * 1000.0,
                      _last_allocation_time_s > 0.0 ? _last_allocated_bytes / _last_allocation_time_s : 0.0,
                      last_marking_length_s() * 1000.0);
}

void G1IHOPControl::send_trace_event(G1NewTracer* tracer) {
  assert(_target_occupancy > 0, "Target occupancy still not updated yet.");
  tracer->report_basic_ihop_statistics(get_conc_mark_start_threshold(),
                                       _target_occupancy,
                                       G1CollectedHeap::heap()->used(),
                                       _last_allocated_bytes,
                                       _last_allocation_time_s,
                                       last_marking_length_s());
}

G1StaticIHOPControl::G1StaticIHOPControl(double ihop_percent) :
  G1IHOPControl(ihop_percent),
  _last_marking_length_s(0.0) {
}

#ifndef PRODUCT
static void test_update(G1IHOPControl* ctrl, double alloc_time, size_t alloc_amount, size_t young_size, double mark_time) {
  for (int i = 0; i < 100; i++) {
    ctrl->update_allocation_info(alloc_time, alloc_amount, young_size);
    ctrl->update_marking_length(mark_time);
  }
}

void G1StaticIHOPControl::test() {
  size_t const initial_ihop = 45;

  G1StaticIHOPControl ctrl(initial_ihop);
  ctrl.update_target_occupancy(100);

  size_t threshold = ctrl.get_conc_mark_start_threshold();
  assert(threshold == initial_ihop,
         "Expected IHOP threshold of " SIZE_FORMAT " but is " SIZE_FORMAT, initial_ihop, threshold);

  ctrl.update_allocation_info(100.0, 100, 100);
  threshold = ctrl.get_conc_mark_start_threshold();
  assert(threshold == initial_ihop,
         "Expected IHOP threshold of " SIZE_FORMAT " but is " SIZE_FORMAT, initial_ihop, threshold);

  ctrl.update_marking_length(1000.0);
  threshold = ctrl.get_conc_mark_start_threshold();
  assert(threshold == initial_ihop,
         "Expected IHOP threshold of " SIZE_FORMAT " but is " SIZE_FORMAT, initial_ihop, threshold);

  // Whatever we pass, the IHOP value must stay the same.
  test_update(&ctrl, 2, 10, 10, 3);
  threshold = ctrl.get_conc_mark_start_threshold();
  assert(threshold == initial_ihop,
         "Expected IHOP threshold of " SIZE_FORMAT " but is " SIZE_FORMAT, initial_ihop, threshold);

  test_update(&ctrl, 12, 10, 10, 3);
  threshold = ctrl.get_conc_mark_start_threshold();
  assert(threshold == initial_ihop,
         "Expected IHOP threshold of " SIZE_FORMAT " but is " SIZE_FORMAT, initial_ihop, threshold);
}
#endif

G1AdaptiveIHOPControl::G1AdaptiveIHOPControl(double ihop_percent,
                                             G1Predictions const* predictor,
                                             size_t heap_reserve_percent,
                                             size_t heap_waste_percent) :
  G1IHOPControl(ihop_percent),
  _predictor(predictor),
  _marking_times_s(10, 0.95),
  _allocation_rate_s(10, 0.95),
  _last_unrestrained_young_size(0),
  _heap_reserve_percent(heap_reserve_percent),
  _heap_waste_percent(heap_waste_percent)
{
}

size_t G1AdaptiveIHOPControl::actual_target_threshold() const {
  guarantee(_target_occupancy > 0, "Target occupancy still not updated yet.");
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
    _target_occupancy * (100.0 - _heap_waste_percent) / 100.0
    );
}

bool G1AdaptiveIHOPControl::have_enough_data_for_prediction() const {
  return ((size_t)_marking_times_s.num() >= G1AdaptiveIHOPNumInitialSamples) &&
         ((size_t)_allocation_rate_s.num() >= G1AdaptiveIHOPNumInitialSamples);
}

size_t G1AdaptiveIHOPControl::get_conc_mark_start_threshold() {
  if (have_enough_data_for_prediction()) {
    double pred_marking_time = _predictor->get_new_prediction(&_marking_times_s);
    double pred_promotion_rate = _predictor->get_new_prediction(&_allocation_rate_s);
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
    return (size_t)(_initial_ihop_percent * _target_occupancy / 100.0);
  }
}

void G1AdaptiveIHOPControl::update_allocation_info(double allocation_time_s,
                                                   size_t allocated_bytes,
                                                   size_t additional_buffer_size) {
  G1IHOPControl::update_allocation_info(allocation_time_s, allocated_bytes, additional_buffer_size);

  double allocation_rate = (double) allocated_bytes / allocation_time_s;
  _allocation_rate_s.add(allocation_rate);

  _last_unrestrained_young_size = additional_buffer_size;
}

void G1AdaptiveIHOPControl::update_marking_length(double marking_length_s) {
   assert(marking_length_s >= 0.0, "Marking length must be larger than zero but is %.3f", marking_length_s);
  _marking_times_s.add(marking_length_s);
}

void G1AdaptiveIHOPControl::print() {
  G1IHOPControl::print();
  size_t actual_target = actual_target_threshold();
  log_debug(gc, ihop)("Adaptive IHOP information (value update), threshold: " SIZE_FORMAT "B (%1.2f), internal target occupancy: " SIZE_FORMAT "B, "
                      "occupancy: " SIZE_FORMAT "B, additional buffer size: " SIZE_FORMAT "B, predicted old gen allocation rate: %1.2fB/s, "
                      "predicted marking phase length: %1.2fms, prediction active: %s",
                      get_conc_mark_start_threshold(),
                      percent_of(get_conc_mark_start_threshold(), actual_target),
                      actual_target,
                      G1CollectedHeap::heap()->used(),
                      _last_unrestrained_young_size,
                      _predictor->get_new_prediction(&_allocation_rate_s),
                      _predictor->get_new_prediction(&_marking_times_s) * 1000.0,
                      have_enough_data_for_prediction() ? "true" : "false");
}

void G1AdaptiveIHOPControl::send_trace_event(G1NewTracer* tracer) {
  G1IHOPControl::send_trace_event(tracer);
  tracer->report_adaptive_ihop_statistics(get_conc_mark_start_threshold(),
                                          actual_target_threshold(),
                                          G1CollectedHeap::heap()->used(),
                                          _last_unrestrained_young_size,
                                          _predictor->get_new_prediction(&_allocation_rate_s),
                                          _predictor->get_new_prediction(&_marking_times_s),
                                          have_enough_data_for_prediction());
}

#ifndef PRODUCT
void G1AdaptiveIHOPControl::test() {
  size_t const initial_threshold = 45;
  size_t const young_size = 10;
  size_t const target_size = 100;

  // The final IHOP value is always
  // target_size - (young_size + alloc_amount/alloc_time * marking_time)

  G1Predictions pred(0.95);
  G1AdaptiveIHOPControl ctrl(initial_threshold, &pred, 0, 0);
  ctrl.update_target_occupancy(target_size);

  // First "load".
  size_t const alloc_time1 = 2;
  size_t const alloc_amount1 = 10;
  size_t const marking_time1 = 2;
  size_t const settled_ihop1 = target_size - (young_size + alloc_amount1/alloc_time1 * marking_time1);

  size_t threshold;
  threshold = ctrl.get_conc_mark_start_threshold();
  assert(threshold == initial_threshold,
         "Expected IHOP threshold of " SIZE_FORMAT " but is " SIZE_FORMAT, initial_threshold, threshold);
  for (size_t i = 0; i < G1AdaptiveIHOPNumInitialSamples - 1; i++) {
    ctrl.update_allocation_info(alloc_time1, alloc_amount1, young_size);
    ctrl.update_marking_length(marking_time1);
    // Not enough data yet.
    threshold = ctrl.get_conc_mark_start_threshold();
    assert(threshold == initial_threshold,
           "Expected IHOP threshold of " SIZE_FORMAT " but is " SIZE_FORMAT, initial_threshold, threshold);
  }

  test_update(&ctrl, alloc_time1, alloc_amount1, young_size, marking_time1);

  threshold = ctrl.get_conc_mark_start_threshold();
  assert(threshold == settled_ihop1,
         "Expected IHOP threshold to settle at " SIZE_FORMAT " but is " SIZE_FORMAT, settled_ihop1, threshold);

  // Second "load". A bit higher allocation rate.
  size_t const alloc_time2 = 2;
  size_t const alloc_amount2 = 30;
  size_t const marking_time2 = 2;
  size_t const settled_ihop2 = target_size - (young_size + alloc_amount2/alloc_time2 * marking_time2);

  test_update(&ctrl, alloc_time2, alloc_amount2, young_size, marking_time2);

  threshold = ctrl.get_conc_mark_start_threshold();
  assert(threshold < settled_ihop1,
         "Expected IHOP threshold to settle at a value lower than " SIZE_FORMAT " but is " SIZE_FORMAT, settled_ihop1, threshold);

  // Third "load". Very high (impossible) allocation rate.
  size_t const alloc_time3 = 1;
  size_t const alloc_amount3 = 50;
  size_t const marking_time3 = 2;
  size_t const settled_ihop3 = 0;

  test_update(&ctrl, alloc_time3, alloc_amount3, young_size, marking_time3);
  threshold = ctrl.get_conc_mark_start_threshold();

  assert(threshold == settled_ihop3,
         "Expected IHOP threshold to settle at " SIZE_FORMAT " but is " SIZE_FORMAT, settled_ihop3, threshold);

  // And back to some arbitrary value.
  test_update(&ctrl, alloc_time2, alloc_amount2, young_size, marking_time2);

  threshold = ctrl.get_conc_mark_start_threshold();
  assert(threshold > settled_ihop3,
         "Expected IHOP threshold to settle at value larger than " SIZE_FORMAT " but is " SIZE_FORMAT, settled_ihop3, threshold);
}

void IHOP_test() {
  G1StaticIHOPControl::test();
  G1AdaptiveIHOPControl::test();
}
#endif
