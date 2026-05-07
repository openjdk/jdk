/*
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

#include <cmath>

ShenandoahWeightedSeq::ShenandoahWeightedSeq(uint size)
: _size(size),
  _first_sample_index(0),
  _num_samples(0),
  _x_values(NEW_C_HEAP_ARRAY(double, _size, mtGC)),
  _y_values(NEW_C_HEAP_ARRAY(double, _size, mtGC)),
  _weights(NEW_C_HEAP_ARRAY(double, _size, mtGC)),
  _x_sum(0),
  _y_sum(0),
  _weighted_y_sum(0),
  _weighted_sum(0),
  _weighted_yy_sum(0),
  _xy_sum(0),
  _xx_sum(0),
  _slope(0.0),
  _y_intercept(0.0),
  _residual_sd(0.0) {
}

ShenandoahWeightedSeq::~ShenandoahWeightedSeq() {
  FREE_C_HEAP_ARRAY(_x_values);
  FREE_C_HEAP_ARRAY(_y_values);
  FREE_C_HEAP_ARRAY(_weights);
}

void ShenandoahWeightedSeq::add(double x, double y) {
  const uint index = (_first_sample_index + _num_samples - 1) % _size;
  const double weight = _num_samples > 0 ? x - _x_values[index] : 0;
  add(x, y, weight);
}

void ShenandoahWeightedSeq::add(double x, double y, double weight) {
  // Update best-fit linear regression
  const uint index = (_first_sample_index + _num_samples) % _size;
  if (_num_samples == _size) {
    _x_sum -= _x_values[index];
    _y_sum -= _y_values[index];
    _xy_sum -= _x_values[index] * _y_values[index];
    _xx_sum -= _x_values[index] * _x_values[index];
    _weighted_y_sum -= _weights[index] * _y_values[index];
    _weighted_sum -= _weights[index];
    _weighted_yy_sum -= _y_values[index] * _y_values[index] * _weights[index];
  }

  _x_values[index] = x;
  _y_values[index] = y;
  _weights[index] = weight;

  _x_sum += x;
  _y_sum += y;
  _xy_sum += x * y;
  _xx_sum += x * x;
  _weighted_y_sum += y * weight;
  _weighted_sum += weight;
  _weighted_yy_sum += y * y * weight;

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
  const double denominator = _num_samples * _xx_sum - _x_sum * _x_sum;
  assert(denominator != 0.0, "Invariant: samples: %u, sum_of_xx: %.6f, sum_of_x_values: %.6f",
         _num_samples, _xx_sum, _x_sum);
  _slope = (_num_samples * _xy_sum - _x_sum * _y_sum) / denominator;
  _y_intercept = (_y_sum - _slope * _x_sum) / _num_samples;
  double sum_of_squared_deviations = 0.0;
  for (size_t i = 0; i < _num_samples; i++) {
    const uint idx = (_first_sample_index + i) % _size;
    const double x_value = _x_values[idx];
    const double predicted_y = _slope * x_value + _y_intercept;
    const double deviation = predicted_y - _y_values[idx];
    sum_of_squared_deviations += deviation * deviation;
  }
  _residual_sd = std::sqrt(sum_of_squared_deviations / _num_samples);
}

double ShenandoahWeightedSeq::predict(double x, double margin_of_error) const {
  const double prediction = _slope * x + _y_intercept + _residual_sd * margin_of_error;
  if (prediction <= 0.0) {
    // return average time, rather than negative or zero time
    return _y_sum / MAX2(_num_samples, 1u);
  }
  return prediction;
}

double ShenandoahWeightedSeq::weighted_sd() const {
  if (_weighted_sum <= 0.0) {
    return 0.0;
  }

  const double weighted_mean = _weighted_y_sum / _weighted_sum;
  const double variance = _weighted_yy_sum / _weighted_sum - weighted_mean * weighted_mean;
  return std::sqrt(MAX2(variance, 0.0));
}
