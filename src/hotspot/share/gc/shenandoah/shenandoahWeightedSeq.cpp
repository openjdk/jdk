/*
* Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
#include "gc/shenandoah/shenandoahWeightedSeq.hpp"

#include "logging/log.hpp"
#include "memory/allocation.hpp"

ShenandoahWeightedSeq::ShenandoahWeightedSeq(uint size)
: _size(size),
  _first_sample_index(0),
  _num_samples(0),
  _x_values(NEW_C_HEAP_ARRAY(double, _size, mtGC)),
  _y_values(NEW_C_HEAP_ARRAY(double, _size, mtGC)),
  _sum_of_x_values(0),
  _sum_of_y_values(0),
  _sum_of_xy(0),
  _sum_of_xx(0),
  _slope(0.0),
  _y_intercept(0.0),
  _residual_sd(0.0) {
}

ShenandoahWeightedSeq::~ShenandoahWeightedSeq() {
  FREE_C_HEAP_ARRAY(_x_values);
  FREE_C_HEAP_ARRAY(_y_values);
}

void ShenandoahWeightedSeq::add(double x, double y, double weight) {
  // Update best-fit linear regression
  const uint index = (_first_sample_index + _num_samples) % _size;
  if (_num_samples == _size) {
    _sum_of_x_values -= _x_values[index];
    _sum_of_y_values -= _y_values[index];
    _sum_of_xy -= _x_values[index] * _y_values[index];
    _sum_of_xx -= _x_values[index] * _x_values[index];
  }
  _x_values[index] = x;
  _y_values[index] = y;

  _sum_of_x_values += x;
  _sum_of_y_values += y;
  _sum_of_xy += x * y;
  _sum_of_xx += x * x;

  if (_num_samples < _size) {
    _num_samples++;
  } else {
    _first_sample_index = (_first_sample_index + 1) % _size;
  }

  if (_num_samples == 1) {
    // The predictor is constant (horizontal line)
    _slope = 0;
    _y_intercept = y;
    _residual_sd = 0.0;
    return;
  }

  // Assume x values are monotonically increasing, denominator does not equal zero.
  const double denominator = _num_samples * _sum_of_xx - _sum_of_x_values * _sum_of_x_values;
  assert(denominator != 0.0, "Invariant: samples: %u, sum_of_xx: %.6f, sum_of_x_values: %.6f",
         _num_samples, _sum_of_xx, _sum_of_x_values);
  _slope = (_num_samples * _sum_of_xy - _sum_of_x_values * _sum_of_y_values) / denominator;
  _y_intercept = (_sum_of_y_values - _slope * _sum_of_x_values) / _num_samples;
  double sum_of_squared_deviations = 0.0;
  for (size_t i = 0; i < _num_samples; i++) {
    const uint idx = (_first_sample_index + i) % _size;
    const double x_value = _x_values[idx];
    const double predicted_y = _slope * x_value + _y_intercept;
    const double deviation = predicted_y - _y_values[idx];
    sum_of_squared_deviations += deviation * deviation;
  }
  _residual_sd = sqrt(sum_of_squared_deviations / _num_samples);
}

double ShenandoahWeightedSeq::predict(double x, double margin_of_error) const {
  const double prediction = _slope * x + _y_intercept + _residual_sd * margin_of_error;
  if (prediction <= 0.0) {
    // return average time, rather than negative or zero time
    return _sum_of_y_values / MAX2(_num_samples, 1u);
  }
  return prediction;
}
