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
#include "memory/allocation.hpp"

#include <cmath>
#include <float.h>

ShenandoahWeightedSeq::ShenandoahWeightedSeq(uint size)
: _size(size),
  _first_sample_index(0),
  _num_samples(0),
  _x_values(NEW_C_HEAP_ARRAY(double, _size, mtGC)),
  _y_values(NEW_C_HEAP_ARRAY(double, _size, mtGC)),
  _weights(NEW_C_HEAP_ARRAY(double, _size, mtGC)),
  _x_origin(0),
  _y_origin(0),
  _x_sum(0),
  _y_sum(0),
  _weighted_y_sum(0),
  _weighted_sum(0),
  _weighted_yy_sum(0),
  _xy_sum(0),
  _xx_sum(0),
  _yy_sum(0),
  _slope(0.0),
  _y_intercept(0.0),
  _residual_sd(0.0),
  _slope_se(0.0) {
}

ShenandoahWeightedSeq::~ShenandoahWeightedSeq() {
  FREE_C_HEAP_ARRAY(_x_values);
  FREE_C_HEAP_ARRAY(_y_values);
  FREE_C_HEAP_ARRAY(_weights);
}

void ShenandoahWeightedSeq::add(double x, double y) {
  if (_num_samples == 0) {
    add(x, y, 0.0);
  } else {
    const uint index = (_first_sample_index + _num_samples - 1) % _size;
    const double weight = x - _x_values[index];
    add(x, y, weight);
  }
}

void ShenandoahWeightedSeq::add(double x, double y, double weight) {
  uint index = 0;
  if (_num_samples < _size) {
    index = _num_samples++;
  } else {
    index = _first_sample_index;
    _first_sample_index = (_first_sample_index + 1) % _size;
  }
  _x_values[index] = x;
  _y_values[index] = y;
  _weights[index] = weight;

  // Recompute everything from current data, to avoid accumulating errors.
  _x_sum = 0;
  _y_sum = 0;
  _xx_sum = 0;
  _xy_sum = 0;
  _yy_sum = 0;
  _weighted_sum = 0;
  _weighted_y_sum = 0;
  _weighted_yy_sum = 0;

  // Most robust estimation is when origin is at average
  _x_origin = 0;
  _y_origin = 0;
  for (uint i = 0; i < _num_samples; i++) {
    _x_origin += _x_values[i];
    _y_origin += _y_values[i];
  }
  _x_origin /= _num_samples;
  _y_origin /= _num_samples;

  for (uint i = 0; i < _num_samples; i++) {
    double x = _x_values[i] - _x_origin;
    double y = _y_values[i] - _y_origin;
    _x_sum += x;
    _y_sum += y;
    _xx_sum += x * x;
    _xy_sum += x * y;
    _yy_sum += y * y;
    _weighted_sum += _weights[i];
    _weighted_y_sum += _weights[i] * y;
    _weighted_yy_sum += _weights[i] * y * y;
  }

  // For centered math, the "noisy" path is when the spread is at the scale
  // representation noise of inputs.
  const double K = 100.0;
  const double x_noise = K * DBL_EPSILON * _x_origin;
  if (_num_samples < 2 || _xx_sum <= _num_samples * x_noise * x_noise) {
    // All samples are the sample point, can't make a line
    _slope = 0;
    _y_intercept = y - _y_origin;
    _residual_sd = 0.0;
    return;
  }

  const double x_spread = _num_samples * _xx_sum - _x_sum * _x_sum;
  _slope = (_num_samples * _xy_sum - _x_sum * _y_sum) / x_spread;
  _y_intercept = (_y_sum - _slope * _x_sum) / _num_samples;
  const double total_sum_of_squares = _yy_sum - _y_sum * _y_sum / _num_samples;
  const double sum_of_cross_deviations = _xy_sum - _x_sum * _y_sum / _num_samples;
  const double residual_sum_of_squares = total_sum_of_squares - _slope * sum_of_cross_deviations;
  _residual_sd = std::sqrt(MAX2(residual_sum_of_squares, 0.0) / _num_samples);
  _slope_se = std::sqrt(MAX2(residual_sum_of_squares, 0.0) / _num_samples) / _xx_sum;
}

double ShenandoahWeightedSeq::predict(double x_absolute, double margin_of_error) const {
  const double prediction = predict_y(x_absolute) + _residual_sd * margin_of_error;
  if (prediction <= 0.0) {
    // return average time, rather than negative or zero time
    return average();
  }
  return prediction;
}

double ShenandoahWeightedSeq::weighted_average() const {
  if (_weighted_sum <= 0.0) {
    return 0.0;
  }

  return _weighted_y_sum / _weighted_sum + _y_origin;
}

double ShenandoahWeightedSeq::weighted_sd() const {
  if (_weighted_sum <= 0.0) {
    return 0.0;
  }

  const double weighted_mean = _weighted_y_sum / _weighted_sum;
  const double variance = _weighted_yy_sum / _weighted_sum - weighted_mean * weighted_mean;
  return std::sqrt(MAX2(variance, 0.0));
}

double ShenandoahWeightedSeq::sd() const {
  if (_num_samples < 2) {
    return 0.0;
  }

  const double mean = _y_sum / _num_samples;
  const double variance = _yy_sum / _num_samples - mean * mean;
  return std::sqrt(MAX2(variance, 0.0));
}
