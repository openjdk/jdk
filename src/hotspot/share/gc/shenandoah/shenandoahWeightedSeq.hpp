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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHWEIGHTEDSEQ_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHWEIGHTEDSEQ_HPP

#include "utilities/globalDefinitions.hpp"

// Provides a weighted sequence of x, y pairs. Various statistical properties
// such as weighted mean, standard deviation, the line of best fit and the
// residual deviation (deviation about the line of best fit) are available.
// These attributes are maintained incrementally as we expect this structure
// to be read more often than it is written.
class ShenandoahWeightedSeq {

  uint _size;
  uint _first_sample_index;
  uint _num_samples;

  double* const _x_values;
  double* const _y_values;
  double* const _weights;

  // Values stored in the x,y accumulators will be reduced to avoid arithmetic
  // errors caused by loss of precision when working with large doubles. This
  // is particularly important for the common use case when x is a monotonically
  // increasing timestamp
  double _x_origin;
  double _y_origin;

  double _x_sum;
  double _y_sum;
  double _weighted_y_sum;
  double _weighted_sum;
  double _weighted_yy_sum;
  double _xy_sum;
  double _xx_sum;
  double _yy_sum;

  double _slope;            // slope
  double _y_intercept;      // y-intercept
  double _residual_sd;      // sd on deviance from prediction

public:

  explicit ShenandoahWeightedSeq(uint size);
  ~ShenandoahWeightedSeq();

  // Return last item x value added to the sequence (zero if sequence is empty).
  double last() const {
    if (_num_samples == 0) {
      return 0.0;
    }

    const uint index = (_first_sample_index + _num_samples - 1) % _size;
    return _x_values[index];
  }

  // Add x, y to the sequence. Weight will be calculated as x - last().
  void add(double x, double y);


  // Add x, y to the sequence using given weight.
  void add(double x, double y, double weight);

  // Predict the next value in the sequence for a given x. Uses average
  // if the prediction is <= 0. This is a legacy method visible only for
  // testing.
  double predict(double x, double margin_of_error) const;

  // The standard deviation of the samples about the line of best fit rather
  // than deviation about the mean.
  double residual_sd() const { return _residual_sd; }

  // An unweighted mean.
  double average() const { return _y_sum / MAX2(_num_samples, 1u) + _y_origin; }

  // The weighted mean for the sequence.
  double weighted_average() const;

  // Standard deviation for the weighted mean.
  double weighted_sd() const;

  // An unweighted standard deviation of the unweighted mean
  double sd() const;

  // The slope for a line of best fit through the samples
  double slope() const { return _slope; }

  // Predict the y-value for the given x value based on linear reg
  double predict_y(double x_absolute) const {
    return _slope * (x_absolute - _x_origin) + _y_intercept + _y_origin;
  }

  // Provides the slope and y-intercept for the line of best fit through the sequence
  void fit_line(const double x_absolute, double& slope, double& intercept) const {
    slope = _slope;
    intercept = predict_y(x_absolute);
  }

private:
  // Removes about to be overwritten sample from x accumulators and rebases x origin
  void deduct_oldest_and_rebase(double x, double y, double weight);

  // Record the sample into the sequence, update x, y accumulators
  void add_latest(double x, double y, double weight);
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHWEIGHTEDSEQ_HPP
