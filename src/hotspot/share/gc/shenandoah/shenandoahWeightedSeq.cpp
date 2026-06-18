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
  _residual_sd(0.0) {
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

void ShenandoahWeightedSeq::deduct_oldest_and_rebase(const double x_absolute, const double y_absolute, const double weight) {
  // Suppose we want to shift _x_origin by delta. Our accumulators for x
  // components are based on the relative value 'x - x_origin', call this 'a'.
  // We want to update our accumulators to hold 'a - delta'.
  // Our new value for
  // updated sum(x) = sum(a - delta)
  //                = sum(a) - n * delta.
  // Similarly
  // updated sum(x^2) = sum((a - delta)^2)
  //                  = sum(a^2 - 2 * delta * a + delta^2)
  //                  = sum(a^2) - 2 * delta * sum(a) + n * delta^2
  // Finally
  // updated sum(xy) = sum(a - delta) * y
  //                 = sum(xy) - delta * sum(y)
  const double x_delta = x_absolute - _x_origin;
  const double y_delta = y_absolute - _y_origin;

  // order matters here, we must use old _x_sum
  _xx_sum = _xx_sum - 2.0 * x_delta * _x_sum + _num_samples * x_delta * x_delta;
  _xy_sum = _xy_sum - x_delta * _y_sum;
  _x_sum  = _x_sum  - _num_samples * x_delta;
  _x_origin = x_absolute;

  // similarly, rebase y
  _yy_sum = _yy_sum - 2.0 * y_delta * _y_sum + _num_samples * y_delta * y_delta;
  _xy_sum = _xy_sum - y_delta * _x_sum;
  _y_sum = _y_sum - _num_samples * y_delta;
  _y_origin = y_absolute;

  // and our weighted sums
  _weighted_yy_sum = _weighted_yy_sum - 2.0 * y_delta * _weighted_y_sum + _weighted_sum * y_delta * y_delta;
  _weighted_y_sum = _weighted_y_sum - _weighted_sum * y_delta;
  _weighted_sum -= weight;
}

void ShenandoahWeightedSeq::add_latest(double x_absolute, double y_absolute, double weight) {
  const double x_delta = x_absolute - _x_origin;
  const double y_delta = y_absolute - _y_origin;
  _x_sum += x_delta;
  _y_sum += y_delta;
  _xy_sum += x_delta * y_delta;
  _xx_sum += x_delta * x_delta;
  _yy_sum += y_delta * y_delta;
  _weighted_sum += weight;
  _weighted_y_sum += y_delta * weight;
  _weighted_yy_sum += y_delta * y_delta * weight;
}

void ShenandoahWeightedSeq::add(double x, double y, double weight) {
  // Update best-fit linear regression
  const uint index = (_first_sample_index + _num_samples) % _size;
  if (_num_samples == _size) {
    deduct_oldest_and_rebase(_x_values[index], _y_values[index], _weights[index]);
  } else if (_num_samples == 0) {
    _x_origin = x;
    _y_origin = y;
  }

  _x_values[index] = x;
  _y_values[index] = y;
  _weights[index] = weight;

  add_latest(x, y, weight);

  if (_num_samples < _size) {
    _num_samples++;
  } else {
    _first_sample_index = (_first_sample_index + 1) % _size;
  }

  const double x_spread = _num_samples * _xx_sum - _x_sum * _x_sum;
  if (x_spread <= 0.0 || _num_samples < 2) {
    // All samples are the sample point, can't make a line
    _slope = 0;
    _y_intercept = y - _y_origin;
    _residual_sd = 0.0;
    return;
  }

  _slope = (_num_samples * _xy_sum - _x_sum * _y_sum) / x_spread;
  _y_intercept = (_y_sum - _slope * _x_sum) / _num_samples;
  const double total_sum_of_squares = _yy_sum - _y_sum * _y_sum / _num_samples;
  const double sum_of_cross_deviations = _xy_sum - _x_sum * _y_sum / _num_samples;
  const double residual_sum_of_squares = total_sum_of_squares - _slope * sum_of_cross_deviations;
  _residual_sd = std::sqrt(MAX2(residual_sum_of_squares, 0.0) / _num_samples);
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
