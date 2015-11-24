/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1ErgoVerbose.hpp"
#include "gc/g1/g1IHOPControl.hpp"

G1IHOPControl::G1IHOPControl(double initial_ihop_percent, size_t target_occupancy) :
  _initial_ihop_percent(initial_ihop_percent),
  _target_occupancy(target_occupancy),
  _last_allocated_bytes(0),
  _last_allocation_time_s(0.0)
{
  assert(_initial_ihop_percent >= 0.0 && _initial_ihop_percent <= 100.0, "Initial IHOP value must be between 0 and 100 but is %.3f", initial_ihop_percent);
}

void G1IHOPControl::update_allocation_info(double allocation_time_s, size_t allocated_bytes, size_t additional_buffer_size) {
  assert(allocation_time_s >= 0.0, "Allocation time must be positive but is %.3f", allocation_time_s);

  _last_allocation_time_s = allocation_time_s;
  _last_allocated_bytes = allocated_bytes;
}

void G1IHOPControl::print() {
  size_t cur_conc_mark_start_threshold = get_conc_mark_start_threshold();
  ergo_verbose6(ErgoIHOP,
                "basic information",
                ergo_format_reason("value update")
                ergo_format_byte_perc("threshold")
                ergo_format_byte("target occupancy")
                ergo_format_byte("current occupancy")
                ergo_format_double("recent old gen allocation rate")
                ergo_format_double("recent marking phase length"),
                cur_conc_mark_start_threshold,
                cur_conc_mark_start_threshold * 100.0 / _target_occupancy,
                _target_occupancy,
                G1CollectedHeap::heap()->used(),
                _last_allocation_time_s > 0.0 ? _last_allocated_bytes / _last_allocation_time_s : 0.0,
                last_marking_length_s());
}

G1StaticIHOPControl::G1StaticIHOPControl(double ihop_percent, size_t target_occupancy) :
  G1IHOPControl(ihop_percent, target_occupancy),
  _last_marking_length_s(0.0) {
  assert(_target_occupancy > 0, "Target occupancy must be larger than zero.");
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

  G1StaticIHOPControl ctrl(initial_ihop, 100);

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

void IHOP_test() {
  G1StaticIHOPControl::test();
}
#endif
