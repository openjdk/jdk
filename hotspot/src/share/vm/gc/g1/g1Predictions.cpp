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
#include "gc/g1/g1Predictions.hpp"

#ifndef PRODUCT

void G1Predictions::test() {
  double const epsilon = 1e-6;
  {
    // Some basic formula tests with confidence = 0.0
    G1Predictions predictor(0.0);
    TruncatedSeq s;

    double p0 = predictor.get_new_prediction(&s);
    assert(p0 < epsilon, "Initial prediction of empty sequence must be 0.0 but is %f", p0);

    s.add(5.0);
    double p1 = predictor.get_new_prediction(&s);
    assert(fabs(p1 - 5.0) < epsilon, "Prediction should be 5.0 but is %f", p1);
    for (int i = 0; i < 40; i++) {
      s.add(5.0);
    }
    double p2 = predictor.get_new_prediction(&s);
    assert(fabs(p2 - 5.0) < epsilon, "Prediction should be 5.0 but is %f", p1);
  }

  {
    // The following tests checks that the initial predictions are based on the
    // average of the sequence and not on the stddev (which is 0).
    G1Predictions predictor(0.5);
    TruncatedSeq s;

    s.add(1.0);
    double p1 = predictor.get_new_prediction(&s);
    assert(p1 > 1.0, "First prediction must be larger than average, but avg is %f and prediction %f", s.davg(), p1);
    s.add(1.0);
    double p2 = predictor.get_new_prediction(&s);
    assert(p2 < p1, "First prediction must be larger than second, but they are %f %f", p1, p2);
    s.add(1.0);
    double p3 = predictor.get_new_prediction(&s);
    assert(p3 < p2, "Second prediction must be larger than third, but they are %f %f", p2, p3);
    s.add(1.0);
    s.add(1.0); // Five elements are now in the sequence.
    double p5 = predictor.get_new_prediction(&s);
    assert(p5 < p3, "Fifth prediction must be smaller than third, but they are %f %f", p3, p5);
    assert(fabs(p5 - 1.0) < epsilon, "Prediction must be 1.0+epsilon, but is %f", p5);
  }

  {
    // The following tests checks that initially prediction based on the average is
    // used, that gets overridden by the stddev prediction at the end.
    G1Predictions predictor(0.5);
    TruncatedSeq s;

    s.add(0.5);
    double p1 = predictor.get_new_prediction(&s);
    assert(p1 > 0.5, "First prediction must be larger than average, but avg is %f and prediction %f", s.davg(), p1);
    s.add(0.2);
    double p2 = predictor.get_new_prediction(&s);
    assert(p2 < p1, "First prediction must be larger than second, but they are %f %f", p1, p2);
    s.add(0.5);
    double p3 = predictor.get_new_prediction(&s);
    assert(p3 < p2, "Second prediction must be larger than third, but they are %f %f", p2, p3);
    s.add(0.2);
    s.add(2.0);
    double p5 = predictor.get_new_prediction(&s);
    assert(p5 > p3, "Fifth prediction must be bigger than third, but they are %f %f", p3, p5);
  }
}

void TestPredictions_test() {
  G1Predictions::test();
}

#endif
