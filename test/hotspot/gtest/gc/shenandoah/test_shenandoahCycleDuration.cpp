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
 */

#include "unittest.hpp"

#include "gc/shenandoah/shenandoahCycleDuration.hpp"

TEST_VM(ShenandoahCycleDurationTest, empty_sanity) {
  ShenandoahCycleDuration cycles(5);
  EXPECT_DOUBLE_EQ(cycles.predict_duration(1.0, 1.0), 0.0);
}

TEST_VM(ShenandoahCycleDurationTest, predict_duration) {
  ShenandoahCycleDuration cycles(5);
  cycles.record_duration(1.0, 1.0);
  cycles.record_duration(2.0, 2.0);
  cycles.record_duration(3.0, 3.0);
  EXPECT_DOUBLE_EQ(cycles.predict_duration(4.0, 0.0), 4.0);
}

TEST_VM(ShenandoahCycleDurationTest, fallback_to_average) {
  ShenandoahCycleDuration cycles(5);
  cycles.record_duration(1.0, 5.0);
  cycles.record_duration(2.0, 4.0);
  cycles.record_duration(3.0, 3.0);
  // With this downward trend, predicted duration at 6 seconds would be zero, so
  // we fall back to the average (5 + 4 + 3 / 3 = 4)
  EXPECT_DOUBLE_EQ(cycles.predict_duration(6.0, 0.0), 4.0);
  // Average is 4.0, sd = sqrt((25+16+9)/3 - 16) = sqrt(50/3 - 16) = sqrt(2/3) ~ 0.816
  EXPECT_NEAR(cycles.predict_duration(6.0, 1.0), 4.0 + 0.816, 0.001);
}
