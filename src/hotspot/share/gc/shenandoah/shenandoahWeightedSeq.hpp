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
#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHWEIGHTEDSEQ_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHWEIGHTEDSEQ_HPP

#include "utilities/globalDefinitions.hpp"

class ShenandoahWeightedSeq {

  uint _size;
  uint _first_sample_index;
  uint _num_samples;
  double* const _x_values;
  double* const _y_values;
  double* const _weights;
  double _x_sum;
  double _y_sum;
  double _weighted_y_sum;
  double _weighted_sum;
  double _weighted_yy_sum;
  double _xy_sum;
  double _xx_sum;

  double _slope;            // slope
  double _y_intercept;      // y-intercept
  double _residual_sd;      // sd on deviance from prediction

public:

  explicit ShenandoahWeightedSeq(uint size);
  ~ShenandoahWeightedSeq();

  double latest() const {
    if (_num_samples == 0) {
      return 0.0;
    }

    const uint index = (_first_sample_index + _num_samples - 1) % _size;
    return _x_values[index];
  }

  void add(double x, double y);
  void add(double x, double y, double weight);
  double predict(double x, double margin_of_error) const;
  double residual_sd() const { return _residual_sd; }
  double average() const { return _y_sum / MAX2(_num_samples, 1u); }
  double weighted_average() const { return _weighted_y_sum / MAX2(_weighted_sum, 1.0); }
  double weighted_sd() const {
    if (_weighted_sum <= 0.0) {
      return 0.0;
    }

    const double weighted_mean = _weighted_y_sum / _weighted_sum;
    const double variance = _weighted_yy_sum / _weighted_sum - weighted_mean * weighted_mean;
    return sqrt(MAX2(variance, 0.0));
  }

  void fit_line(double& slope, double& intercept) const {
    slope = _slope;
    intercept = _y_intercept;
  }
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHWEIGHTEDSEQ_HPP
