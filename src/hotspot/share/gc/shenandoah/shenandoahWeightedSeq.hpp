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
  double _sum_of_x_values;
  double _sum_of_y_values;
  double _sum_of_xy;
  double _sum_of_xx;

  double _slope;            // slope
  double _y_intercept;      // y-intercept
  double _residual_sd;      // sd on deviance from prediction

public:

  ShenandoahWeightedSeq(uint size);
  ~ShenandoahWeightedSeq();

  void add(double x, double y, double weight = 1.0);
  double predict(double x, double margin_of_error) const;
  double residual_sd() const { return _residual_sd; }
  double average() const { return _sum_of_y_values / MAX2(_num_samples, 1u); }
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHWEIGHTEDSEQ_HPP
