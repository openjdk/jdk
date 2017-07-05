/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "unittest.hpp"

static const double epsilon = 1e-6;

// Some basic formula tests with confidence = 0.0
TEST_VM(G1Predictions, basic_predictions) {
  G1Predictions predictor(0.0);
  TruncatedSeq s;

  double p0 = predictor.get_new_prediction(&s);
  ASSERT_LT(p0, epsilon) << "Initial prediction of empty sequence must be 0.0";

  s.add(5.0);
  double p1 = predictor.get_new_prediction(&s);
  ASSERT_NEAR(p1, 5.0, epsilon);

  for (int i = 0; i < 40; i++) {
    s.add(5.0);
  }
  double p2 = predictor.get_new_prediction(&s);
  ASSERT_NEAR(p2, 5.0, epsilon);
}

// The following tests checks that the initial predictions are based on
// the average of the sequence and not on the stddev (which is 0).
TEST_VM(G1Predictions, average_not_stdev_predictions) {
  G1Predictions predictor(0.5);
  TruncatedSeq s;

  s.add(1.0);
  double p1 = predictor.get_new_prediction(&s);
  ASSERT_GT(p1, s.davg()) << "First prediction must be greater than average";

  s.add(1.0);
  double p2 = predictor.get_new_prediction(&s);
  ASSERT_GT(p1, p2) << "First prediction must be greater than second";

  s.add(1.0);
  double p3 = predictor.get_new_prediction(&s);
  ASSERT_GT(p2, p3) << "Second prediction must be greater than third";

  s.add(1.0);
  s.add(1.0); // Five elements are now in the sequence.
  double p4 = predictor.get_new_prediction(&s);
  ASSERT_LT(p4, p3) << "Fourth prediction must be smaller than third";
  ASSERT_NEAR(p4, 1.0, epsilon);
}

// The following tests checks that initially prediction based on
// the average is used, that gets overridden by the stddev prediction at
// the end.
TEST_VM(G1Predictions, average_stdev_predictions) {
  G1Predictions predictor(0.5);
  TruncatedSeq s;

  s.add(0.5);
  double p1 = predictor.get_new_prediction(&s);
  ASSERT_GT(p1, s.davg()) << "First prediction must be greater than average";

  s.add(0.2);
  double p2 = predictor.get_new_prediction(&s);
  ASSERT_GT(p1, p2) << "First prediction must be greater than second";

  s.add(0.5);
  double p3 = predictor.get_new_prediction(&s);
  ASSERT_GT(p2, p3) << "Second prediction must be greater than third";

  s.add(0.2);
  s.add(2.0);
  double p4 = predictor.get_new_prediction(&s);
  ASSERT_GT(p4, p3) << "Fourth prediction must be greater than third";
}
