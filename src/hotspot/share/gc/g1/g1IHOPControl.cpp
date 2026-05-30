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

  return ((size_t)_marking_start_to_mixed_time_s.num() >= G1AdaptiveIHOPNumInitialSamples) &&
         ((size_t)_old_non_humongous_alloc_rate.num() >= G1AdaptiveIHOPNumInitialSamples);
}

size_t G1IHOPControl::effective_target_occupancy() const {
  assert(_is_adaptive, "precondition");

  // The effective target occupancy takes the heap reserve and the expected waste in
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
                             bool adaptive,
                             const G1Predictions* predictor,
                             size_t heap_reserve_percent,
                             size_t heap_waste_percent)
  : _is_adaptive(adaptive),
    _initial_ihop_percent(ihop_percent),
    _target_occupancy(0),
    _heap_reserve_percent(heap_reserve_percent),
    _heap_waste_percent(heap_waste_percent),
    _predictor(predictor),
    _marking_start_to_mixed_time_s(10, 0.05),
    _old_non_humongous_alloc_rate(10, 0.05),
    _peak_humongous_allocated_in_mark_cycle(10, 0.05),
    _expected_young_gen_at_first_mixed_gc(0) {
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

void G1IHOPControl::record_expected_young_gen_size(size_t expected_young_gen_size) {
  _expected_young_gen_at_first_mixed_gc = expected_young_gen_size;
}

void G1IHOPControl::record_concurrent_cycle(double marking_start_to_mixed_time_s,
                                            size_t non_humongous_bytes,
                                            size_t peak_extra_humongous_reserve_bytes) {
  assert(marking_start_to_mixed_time_s > 0.0, "Invalid concurrent cycle duration: %.3f", marking_start_to_mixed_time_s);

  double non_humongous_rate =  non_humongous_bytes / marking_start_to_mixed_time_s;
  _marking_start_to_mixed_time_s.add(marking_start_to_mixed_time_s);
  _old_non_humongous_alloc_rate.add(non_humongous_rate);
  _peak_humongous_allocated_in_mark_cycle.add(peak_extra_humongous_reserve_bytes);
}

// Determine the old generation occupancy threshold at which to start
// concurrent marking such that reclamation (first Mixed GC) begins
// before the heap reaches a critical occupancy level.
size_t G1IHOPControl::old_gen_threshold_for_conc_mark_start() const {
  guarantee(_target_occupancy > 0, "Target occupancy must be initialized");

  if (!_is_adaptive || !have_enough_data_for_prediction()) {
    return (size_t)(_initial_ihop_percent * _target_occupancy / 100.0);
  }

  // Between Concurrent Start GC and the first Mixed GC (i.e. concurrent cycle),
  // we expect extra heap occupancy from three sources:
  //    - non-humongous allocations into the old-gen
  //    - peak extra humongous occupancy during the cycle, relative to the humongous occupancy
  //      at the end of the Concurrent Start GC.
  //    - we also wish to maintain the current desired young generation until the first Mixed-gc;
  //      promotions into the old gen should not shrink the young gen and degrade performance.
  //
  //  We therefore start marking early enough such that:
  //
  //   old_gen_at_concurrent_start +
  //   predicted_non_hum_old_growth +
  //   predicted_peak_extra_humongous_reserve +
  //   expected_young_gen_at_first_mixed_gc
  //
  // stays below the effective target occupancy.
  double marking_start_to_mixed_time = predict(&_marking_start_to_mixed_time_s);
  double old_non_humongous_alloc_rate = predict(&_old_non_humongous_alloc_rate);
  size_t old_non_humongous_alloc_bytes = (size_t)(marking_start_to_mixed_time * old_non_humongous_alloc_rate);

  size_t peak_humongous_reserve = predict(&_peak_humongous_allocated_in_mark_cycle);

  size_t reserve_for_young_regions = _expected_young_gen_at_first_mixed_gc;
  size_t target_heap_occupancy = effective_target_occupancy();

  size_t needed_for_concurrent_cycle = reserve_for_young_regions +
                                       old_non_humongous_alloc_bytes +
                                       peak_humongous_reserve;

  size_t threshold = needed_for_concurrent_cycle < target_heap_occupancy ?
                     target_heap_occupancy - needed_for_concurrent_cycle : 0;
  return threshold;
}

void G1IHOPControl::print_log(size_t non_young_occupancy) {
  assert(_target_occupancy > 0, "Target occupancy still not updated yet.");
  size_t old_gen_mark_start_threshold = old_gen_threshold_for_conc_mark_start();
  log_debug(gc, ihop)("Basic information (value update), old-gen threshold: %zuB (%1.2f%%), target occupancy: %zuB, old-gen occupancy: %zuB (%1.2f%%), ",
                      old_gen_mark_start_threshold,
                      percent_of(old_gen_mark_start_threshold, _target_occupancy),
                      _target_occupancy,
                      non_young_occupancy,
                      percent_of(non_young_occupancy, _target_occupancy));

  if (!_is_adaptive || !have_enough_data_for_prediction()) {
    return;
  }

  size_t effective_target = effective_target_occupancy();
  log_debug(gc, ihop)("Adaptive IHOP information (value update), old-gen threshold: %zuB (%1.2f%%), internal target occupancy: %zuB, "
                      "old-gen occupancy: %zuB (%1.2f%%), additional buffer size: %zuB, predicted old-gen non-humongous allocation rate: %1.2fB/s, predicted peak humongous %1.2fB, "
                      "predicted concurrent cycle duration: %1.2fms",
                      old_gen_mark_start_threshold,
                      percent_of(old_gen_mark_start_threshold, effective_target),
                      effective_target,
                      non_young_occupancy,
                      percent_of(non_young_occupancy, effective_target),
                      _expected_young_gen_at_first_mixed_gc,
                      predict(&_old_non_humongous_alloc_rate),
                      predict(&_peak_humongous_allocated_in_mark_cycle),
                      predict(&_marking_start_to_mixed_time_s) * 1000.0);
}

void G1IHOPControl::send_trace_event(G1NewTracer* tracer,
                                     size_t non_young_occupancy) {
  assert(_target_occupancy > 0, "Target occupancy still not updated yet.");
  tracer->report_basic_ihop_statistics(old_gen_threshold_for_conc_mark_start(),
                                       _target_occupancy,
                                       non_young_occupancy);

  if (_is_adaptive) {
    tracer->report_adaptive_ihop_statistics(old_gen_threshold_for_conc_mark_start(),
                                            effective_target_occupancy(),
                                            non_young_occupancy,
                                            _expected_young_gen_at_first_mixed_gc,
                                            predict(&_old_non_humongous_alloc_rate),
                                            predict(&_peak_humongous_allocated_in_mark_cycle),
                                            predict(&_marking_start_to_mixed_time_s),
                                            have_enough_data_for_prediction());
  }
}
