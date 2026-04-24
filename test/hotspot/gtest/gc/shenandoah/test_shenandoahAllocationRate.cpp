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
#include "gc/shared/gc_globals.hpp"

#include "gc/shenandoah/shenandoahAllocRate.inline.hpp"

class ShenandoahMockClock {
public:
  static volatile jlong Counter;
  static jlong elapsed_counter() {
    const jlong result = Counter;
    Counter += NANOSECS_PER_SEC;
    return result;
  }

  static jlong elapsed_frequency() {
    return NANOSECS_PER_SEC;
  }
};

volatile jlong ShenandoahMockClock::Counter = 0;

class ShenandoahAllocationRateTest : public testing::Test {
protected:
  ShenandoahAllocationRateTest() {
    ShenandoahMockClock::Counter = 0;
  }
};

TEST_VM_F(ShenandoahAllocationRateTest, ignore_too_small_sample) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(1024);
  rate.allocated(512);
  EXPECT_EQ(rate.average(), 0);
}

TEST_VM_F(ShenandoahAllocationRateTest, ignore_too_small_elapsed_time) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(1024);
  rate.allocated(2048);
  rate.allocated(2048);
  EXPECT_EQ(rate.average(), 2048);
}

TEST_VM_F(ShenandoahAllocationRateTest, two_second_average) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(1024);
  rate.allocated(2048); // t = 1
  rate.allocated(2048); // t = 2
  EXPECT_EQ(rate.average(), 2048.0);
}

TEST_VM_F(ShenandoahAllocationRateTest, accelerated_consumption_not_enough_samples) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(1024);
  rate.allocated(1024);
  double acceleration(0), current_rate(0);
  size_t anticipated_consumption = rate.accelerated_consumption(acceleration, current_rate, rate.average(), 100);
  EXPECT_DOUBLE_EQ(acceleration, 0.0);
  EXPECT_DOUBLE_EQ(current_rate, 0.0);
  EXPECT_EQ(anticipated_consumption, 0UL);
}

TEST_VM_F(ShenandoahAllocationRateTest, accelerated_consumption_uniform_rate) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(512);
  for (uint i = 0; i < ShenandoahRateAccelerationSampleSize; ++i) {
    rate.allocated(1024);
  }

  double acceleration(0), current_rate(0), average_rate(rate.average());
  size_t anticipated_consumption = rate.accelerated_consumption(acceleration, current_rate, average_rate, 100);
  EXPECT_DOUBLE_EQ(average_rate, 1024);  // Average rate, 1024 bytes per tick
  EXPECT_DOUBLE_EQ(acceleration, 0.0);   // No acceleration, rate is constant
  EXPECT_DOUBLE_EQ(current_rate, 1024);  // Momentary rate is the same as the average
  EXPECT_EQ(anticipated_consumption, 102400UL); // 100 clock ticks at 1024 bytes per tick
}

TEST_VM_F(ShenandoahAllocationRateTest, accelerated_consumption_momentary_spike) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(512);
  for (uint i = 0; i < ShenandoahRateAccelerationSampleSize; ++i) {
    rate.allocated(1024);
  }

  for (uint i = 0; i < ShenandoahMomentaryAllocationRateSpikeSampleSize + 1; ++i) {
    rate.allocated(2048);
  }

  // Here we simulate a situation where we are returning from a lull (avg 1024/s) back
  // to the baseline average allocation rate (2048/s). The momentary rate will reflect
  // the recent samples, but we will not consider this to be an acceleration.
  double acceleration(0), current_rate(0), average_rate(2048);
  size_t anticipated_consumption = rate.accelerated_consumption(acceleration, current_rate, average_rate, 100);
  EXPECT_EQ(acceleration, 0.0);
  EXPECT_DOUBLE_EQ(current_rate, 2048);
  EXPECT_GE(anticipated_consumption, 102400UL);
}

TEST_VM_F(ShenandoahAllocationRateTest, accelerated_consumption_accelerating) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(512);
  for (uint i = 0; i < ShenandoahRateAccelerationSampleSize; ++i) {
    rate.allocated(1024);
  }

  for (uint i = 0; i < ShenandoahMomentaryAllocationRateSpikeSampleSize + 1; ++i) {
    rate.allocated(2048);
  }

  // Setup as before, but pretend our baseline acceleration rate is lower (512). This
  // will evaluate the acceleration of the rate.
  double acceleration(0), current_rate(0), average_rate(512);
  size_t anticipated_consumption = rate.accelerated_consumption(acceleration, current_rate, average_rate, 100);
  EXPECT_GE(acceleration, 12);
  EXPECT_GE(current_rate, 1800);
  EXPECT_GE(anticipated_consumption, 102400UL);
}

TEST_VM_F(ShenandoahAllocationRateTest, accelerated_consumption_decelerating) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(512);
  for (uint i = 0; i < ShenandoahRateAccelerationSampleSize; ++i) {
    rate.allocated(2048);
  }

  for (uint i = 0; i < ShenandoahMomentaryAllocationRateSpikeSampleSize + 1; ++i) {
    rate.allocated(1024);
  }

  // In this setup, the allocation rate is declining.
  double acceleration(0), current_rate(0), average_rate(4096);
  size_t anticipated_consumption = rate.accelerated_consumption(acceleration, current_rate, average_rate, 100);
  EXPECT_GE(acceleration, 0.0);
  EXPECT_DOUBLE_EQ(current_rate, 1024);
  EXPECT_GE(anticipated_consumption, 102400UL);
}