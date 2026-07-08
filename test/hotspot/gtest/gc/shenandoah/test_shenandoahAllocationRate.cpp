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
#include "gc/shenandoah/shenandoahStripedCounter.inline.hpp"
#include "runtime/atomic.hpp"
#include "threadHelper.inline.hpp"

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

  template<typename Clock>
  static void allocate(ShenandoahAllocRate<Clock>& rate, size_t quantity) {
    rate.allocated(quantity);
  }
};

constexpr uint BASELINE_SAMPLES = 100;
constexpr uint RECENT_SAMPLES = 8;
constexpr uint MOMENTARY_SAMPLES = 2;
constexpr uint MINIMUM_SAMPLE_SIZE = 1024;

TEST_VM_F(ShenandoahAllocationRateTest, ignore_too_small_sample) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(MINIMUM_SAMPLE_SIZE, BASELINE_SAMPLES, RECENT_SAMPLES, MOMENTARY_SAMPLES);
  rate.allocated(512);
  EXPECT_DOUBLE_EQ(rate.weighted_average(), 0);
}

TEST_VM_F(ShenandoahAllocationRateTest, two_second_average) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(MINIMUM_SAMPLE_SIZE, BASELINE_SAMPLES, RECENT_SAMPLES, MOMENTARY_SAMPLES);
  allocate(rate, 2048); // t = 1
  allocate(rate, 2048); // t = 2
  EXPECT_DOUBLE_EQ(rate.weighted_average(), 2048.0);
}

TEST_VM_F(ShenandoahAllocationRateTest, accelerated_consumption_small_number_of_samples) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(MINIMUM_SAMPLE_SIZE, BASELINE_SAMPLES, RECENT_SAMPLES, MOMENTARY_SAMPLES);
  allocate(rate, 1024);
  ShenandoahAnticipatedConsumption consumption = rate.snapshot(100, 1);
  EXPECT_DOUBLE_EQ(consumption.acceleration(), 0.0);
  EXPECT_DOUBLE_EQ(consumption.predicted_rate(), 0.0);
  EXPECT_EQ(consumption.accelerated_consumption(), 0UL);
}

TEST_VM_F(ShenandoahAllocationRateTest, accelerated_consumption_uniform_rate) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(MINIMUM_SAMPLE_SIZE, BASELINE_SAMPLES, RECENT_SAMPLES, MOMENTARY_SAMPLES);
  for (uint i = 0; i < BASELINE_SAMPLES; ++i) {
    allocate(rate, 1024);
  }

  ShenandoahAnticipatedConsumption consumption = rate.snapshot(100, 1);
  EXPECT_DOUBLE_EQ(rate.weighted_average(), 1024);  // Average rate, 1024 bytes per tick
  EXPECT_DOUBLE_EQ(consumption.acceleration(), 0.0);   // No acceleration, rate is constant
  EXPECT_DOUBLE_EQ(consumption.momentary_rate(), 1024);  // Momentary rate is the same as the average
  EXPECT_EQ(consumption.momentary_consumption(), 102400UL); // 100 clock ticks at 1024 bytes per tick
  EXPECT_EQ(consumption.accelerated_consumption(), 0UL);
}

TEST_VM_F(ShenandoahAllocationRateTest, accelerated_consumption_momentary_spike) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(MINIMUM_SAMPLE_SIZE, BASELINE_SAMPLES, RECENT_SAMPLES, MOMENTARY_SAMPLES);
  for (uint i = 0; i < BASELINE_SAMPLES; ++i) {
    allocate(rate, 2048);
  }

  for (uint i = 0; i < RECENT_SAMPLES; ++i) {
    allocate(rate, 1024);
  }

  for (uint i = 0; i < MOMENTARY_SAMPLES + 1; ++i) {
    allocate(rate, 2048);
  }

  // Here we simulate a situation where we are returning from a lull (avg 1024/s) back
  // to the baseline average allocation rate (2048/s). The momentary rate will reflect
  // the recent samples, but we will not consider this to be an acceleration.
  ShenandoahAnticipatedConsumption consumption = rate.snapshot(100, 1);
  EXPECT_DOUBLE_EQ(consumption.acceleration(), 0.0);
  EXPECT_DOUBLE_EQ(consumption.momentary_rate(), 2048);
  EXPECT_EQ(consumption.momentary_consumption(), 204800UL);
  EXPECT_EQ(consumption.accelerated_consumption(), 0UL);
}

TEST_VM_F(ShenandoahAllocationRateTest, event_driven_sampling_single_dominant_allocator) {
  // Single mutator: one stripe allocates, other stripes stay empty.
  ShenandoahStripedCounter stripes;
  if (stripes.num_stripes() == 1) {
    // Regression requires multiple stripes.
    return;
  }

  ShenandoahAllocRate<ShenandoahMockClock> rate(MINIMUM_SAMPLE_SIZE, BASELINE_SAMPLES, RECENT_SAMPLES, MOMENTARY_SAMPLES);
  // Multiple epochs prove the allocation-path trigger re-fires without force_update().
  constexpr size_t alloc_size = 64;
  constexpr size_t epochs = 4;
  for (size_t allocated = 0; allocated < MINIMUM_SAMPLE_SIZE * epochs; allocated += alloc_size) {
    allocate(rate, alloc_size);
  }

  // Old one-shot trigger left the average at zero until force_update().
  EXPECT_GT(rate.weighted_average(), 0.0);
}

TEST_VM_F(ShenandoahAllocationRateTest, event_driven_sampling_rearms_when_floor_lowered) {
  // Lowering the floor must re-arm a stripe that crossed the old share.
  constexpr size_t high_floor = 1 * M;
  constexpr size_t low_floor = 1024;
  constexpr size_t alloc_size = 64;

  ShenandoahAllocRate<ShenandoahMockClock> rate(high_floor, BASELINE_SAMPLES, RECENT_SAMPLES, MOMENTARY_SAMPLES);

  // Accumulate below the high floor, but above the later lowered share.
  constexpr size_t phase1_bytes = high_floor / 4;
  for (size_t allocated = 0; allocated < phase1_bytes; allocated += alloc_size) {
    allocate(rate, alloc_size);
  }
  EXPECT_DOUBLE_EQ(rate.weighted_average(), 0.0);  // nothing drained yet

  // A GC lowers the floor.
  rate.set_minimum_sample_size(low_floor);

  // New crossings under the lowered floor must sample without force_update().
  for (size_t allocated = 0; allocated < low_floor * 16; allocated += alloc_size) {
    allocate(rate, alloc_size);
  }

  EXPECT_GT(rate.weighted_average(), 0.0);
}

// Skewed allocation must still sample on allocation volume, without force_update().
// Static light residuals stay below the floor; the heavy stream must re-fire sampling.
class SkewedAllocators {
public:
  static constexpr int kLightThreads = 7;
  static constexpr size_t kLightBytes = 8;    // one sub-share residual per light stripe
  static constexpr size_t kHeavyEpochs = 200;
  static constexpr size_t kAllocSize = 64;
};

TEST_VM_F(ShenandoahAllocationRateTest, event_driven_sampling_skewed_distribution) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(MINIMUM_SAMPLE_SIZE, BASELINE_SAMPLES, RECENT_SAMPLES, MOMENTARY_SAMPLES);

  // Seed light residuals below the aggregate floor.
  auto light = [&](Thread*, int) {
    rate.allocated(SkewedAllocators::kLightBytes);
  };
  TestThreadGroup<decltype(light)> ttg(light, SkewedAllocators::kLightThreads);
  ttg.doit();
  ttg.join();
  EXPECT_DOUBLE_EQ(rate.weighted_average(), 0.0);  // residuals never reach the aggregate floor

  // Heavy stream must trigger samples from allocation crossings.
  const size_t heavy_bytes = MINIMUM_SAMPLE_SIZE * SkewedAllocators::kHeavyEpochs;
  for (size_t allocated = 0; allocated < heavy_bytes; allocated += SkewedAllocators::kAllocSize) {
    allocate(rate, SkewedAllocators::kAllocSize);
  }

  // No force_update(): samples came from allocation crossings.
  EXPECT_GT(rate.weighted_average(), 0.0);
}

TEST_VM_F(ShenandoahAllocationRateTest, accelerated_consumption_accelerating) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(256, BASELINE_SAMPLES, RECENT_SAMPLES, MOMENTARY_SAMPLES);
  for (uint i = 0; i < BASELINE_SAMPLES; ++i) {
    allocate(rate, 512);
  }

  for (uint i = 0; i < RECENT_SAMPLES; ++i) {
    allocate(rate, 1024);
  }

  for (uint i = 0; i < MOMENTARY_SAMPLES + 1; ++i) {
    allocate(rate, 2048);
  }

  // Setup as before, but pretend our baseline acceleration rate is lower (512). This
  // will evaluate the acceleration of the rate.
  ShenandoahAnticipatedConsumption consumption = rate.snapshot(100, 1);
  EXPECT_GE(consumption.acceleration(), 180.0);
  EXPECT_GE(consumption.predicted_rate(), 2047.0); // should be 2048, but can be 2047.9999 from fp issues
  EXPECT_GE(consumption.accelerated_consumption(), 102400UL);
  EXPECT_EQ(consumption.momentary_consumption(), 0UL);
}

TEST_VM_F(ShenandoahAllocationRateTest, accelerated_consumption_decelerating) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(MINIMUM_SAMPLE_SIZE, BASELINE_SAMPLES, RECENT_SAMPLES, MOMENTARY_SAMPLES);
  for (uint i = 0; i < RECENT_SAMPLES; ++i) {
    allocate(rate, 2048);
  }

  for (uint i = 0; i < MOMENTARY_SAMPLES + 1; ++i) {
    allocate(rate, 1024);
  }

  // In this setup, the allocation rate is declining.
  ShenandoahAnticipatedConsumption consumption = rate.snapshot(100, 1);
  EXPECT_DOUBLE_EQ(consumption.acceleration(), 0.0);
  EXPECT_DOUBLE_EQ(consumption.momentary_rate(), 1024.0);
  EXPECT_EQ(consumption.momentary_consumption(), 102400UL);
}

TEST_VM_F(ShenandoahAllocationRateTest, force_updates) {
  ShenandoahAllocRate<ShenandoahMockClock> rate(MINIMUM_SAMPLE_SIZE, BASELINE_SAMPLES, RECENT_SAMPLES, MOMENTARY_SAMPLES);
  for (uint i = 0; i < BASELINE_SAMPLES; ++i) {
    allocate(rate, 2048);
  }
  EXPECT_DOUBLE_EQ(rate.weighted_average(), 2048.0);

  // Now simulate an equal number of seconds passing without any allocations. This
  // should decay our baseline average back to zero.
  for (uint i = 0; i < BASELINE_SAMPLES; ++i) {
    rate.force_update();
  }
  EXPECT_DOUBLE_EQ(rate.weighted_average(), 0.0);
}

