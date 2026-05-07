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
#include "unittest.hpp"
#include "gc/shenandoah/shenandoahWeightedSeq.hpp"

constexpr uint SAMPLE_SIZE = 3;

TEST_VM(ShenandoahWeightedSeqTest, empty_sanity) {
  ShenandoahWeightedSeq seq(SAMPLE_SIZE);
  EXPECT_DOUBLE_EQ(seq.predict(0, 0), 0.0);
  EXPECT_DOUBLE_EQ(seq.predict(1, 0), 0.0);
}

TEST_VM(ShenandoahWeightedSeqTest, predict_flat_line) {
  ShenandoahWeightedSeq seq(SAMPLE_SIZE);
  seq.add(1, 2);
  EXPECT_DOUBLE_EQ(seq.predict(1, 1), 2.0);
  EXPECT_DOUBLE_EQ(seq.predict(2, 1), 2.0);
}

TEST_VM(ShenandoahWeightedSeqTest, predict_y_equals_x) {
  ShenandoahWeightedSeq seq(SAMPLE_SIZE);
  seq.add(1, 1);
  seq.add(2, 2);
  seq.add(3, 3);
  EXPECT_DOUBLE_EQ(seq.predict(4, 1), 4.0);
  EXPECT_DOUBLE_EQ(seq.predict(5, 1), 5.0);
}

TEST_VM(ShenandoahWeightedSeqTest, predict_y_equals_x_squared) {
  ShenandoahWeightedSeq seq(SAMPLE_SIZE);
  seq.add(1, 1);
  seq.add(2, 4);
  seq.add(3, 9);
  EXPECT_NEAR(seq.predict(4, 0), 12.666, 0.001);
  EXPECT_NEAR(seq.predict(5, 0), 16.666, 0.001);
  // Give a margin of error that incorporates residuals standard deviation
  EXPECT_NEAR(seq.predict(4, 1), 13.138, 0.001);
  EXPECT_NEAR(seq.predict(5, 1), 17.138, 0.001);
}

TEST_VM(ShenandoahWeightedSeqTest, simple_average_no_samples) {
  ShenandoahWeightedSeq seq(SAMPLE_SIZE);
  EXPECT_DOUBLE_EQ(seq.average(), 0.0);
}

TEST_VM(ShenandoahWeightedSeqTest, simple_average_one_sample) {
  ShenandoahWeightedSeq seq(SAMPLE_SIZE);
  seq.add(1, 1);
  EXPECT_DOUBLE_EQ(seq.average(), 1.0);
}

TEST_VM(ShenandoahWeightedSeqTest, simple_average) {
  ShenandoahWeightedSeq seq(SAMPLE_SIZE);
  seq.add(1, 1);
  seq.add(2, 1);
  EXPECT_DOUBLE_EQ(seq.average(), 1.0);
}

TEST_VM(ShenandoahWeightedSeqTest, simple_average_2) {
  ShenandoahWeightedSeq seq(SAMPLE_SIZE);
  seq.add(1, 1);
  seq.add(2, 2);
  EXPECT_DOUBLE_EQ(seq.average(), 1.5);
}

TEST_VM(ShenandoahWeightedSeqTest, weighted_average_no_samples) {
  ShenandoahWeightedSeq seq(SAMPLE_SIZE);
  EXPECT_DOUBLE_EQ(seq.weighted_average(), 0.0);
}

TEST_VM(ShenandoahWeightedSeqTest, weighted_average_one_sample) {
  ShenandoahWeightedSeq seq(SAMPLE_SIZE);
  seq.add(1, 2, 2);
  EXPECT_DOUBLE_EQ(seq.weighted_average(), 2.0);
}