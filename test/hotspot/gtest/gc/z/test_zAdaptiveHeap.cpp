/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zAdaptiveHeap.hpp"
#include "gc/z/zGlobals.hpp"
#include "unittest.hpp"

class ZAdaptiveHeapTest : public ::testing::Test {
protected:
  static constexpr size_t K = 1024;
  static constexpr size_t M = 1024 * K;
  static constexpr size_t G = 1024 * M;

  static constexpr size_t container_capacity = 16 * G;
  static constexpr size_t machine_capacity = 32 * G;

  static ZHeapResizeMetrics heap_resize_metrics() {
    return {
      container_capacity - 1 * G,
      machine_capacity - 1 * G,
      4 * G,
      2 * M,
      5 * G,
      2 * G,
      double(128 * M)
    };
  }

  static ZSystemMemoryPressureMetrics sys_mem_pressure_metrics(double usage, physical_memory_size_type max) {
    return {
      physical_memory_size_type(usage * max),
      max,
      ZMemoryConcerningThreshold,
      ZMemoryHighThreshold,
      ZMemoryCriticalThreshold
    };
  }

  static ZMemoryPressureMetrics mem_pressure_metrics(double container_usage, physical_memory_size_type container_max,
                                                     double machine_usage, physical_memory_size_type machine_max) {
    return {
      5.0,
      true,
      sys_mem_pressure_metrics(machine_usage, machine_max),
      sys_mem_pressure_metrics(container_usage, container_max)
    };
  }

  static int concern_level(double availability) {
    if (availability < ZMemoryCriticalThreshold) {
      return 3;
    }

    if (availability < ZMemoryHighThreshold) {
      return 2;
    }

    if (availability < ZMemoryConcerningThreshold) {
      return 1;
    }

    return 0;
  }

  static double memory_pressure(const ZMemoryPressureMetrics& metrics) {
    return ZAdaptiveHeap::compute_memory_pressure(metrics);
  }
};

TEST_F(ZAdaptiveHeapTest, test_mem_pressure) {
  double mem_pressures[101 * 101];

  for (int i = 0; i <= 100; ++i) {
    for (int j = 0; j <= 100; ++j) {
      double container_availability = double(100 - j) / 100.0;
      double machine_availability = double(100 - i) / 100.0;

      int container_concern_level = concern_level(container_availability);
      int machine_concern_level = concern_level(machine_availability);
      int highest_concern_level = MAX2(container_concern_level, machine_concern_level);

      ZMemoryPressureMetrics metrics = mem_pressure_metrics(1.0 - container_availability, 16 * G,
                                                            1.0 - machine_availability, 32 * G);

      double mem_pressure = memory_pressure(metrics);

      switch (highest_concern_level) {
        // Normal
      case 0: {
        EXPECT_EQ(mem_pressure, 1.0);
        EXPECT_FALSE(ZAdaptiveHeap::is_memory_pressure_concerning(metrics));
        EXPECT_FALSE(ZAdaptiveHeap::is_memory_pressure_high(metrics));
        EXPECT_FALSE(ZAdaptiveHeap::is_memory_pressure_critical(metrics));
        break;
      }
        // Concerning
      case 1: {
        EXPECT_TRUE(ZAdaptiveHeap::is_memory_pressure_concerning(metrics));
        EXPECT_FALSE(ZAdaptiveHeap::is_memory_pressure_high(metrics));
        EXPECT_FALSE(ZAdaptiveHeap::is_memory_pressure_critical(metrics));
        EXPECT_GT(mem_pressure, 1.0);
        EXPECT_LE(mem_pressure, 5.0);
        break;
      }

        // High
      case 2: {
        EXPECT_TRUE(ZAdaptiveHeap::is_memory_pressure_concerning(metrics));
        EXPECT_TRUE(ZAdaptiveHeap::is_memory_pressure_high(metrics));
        EXPECT_FALSE(ZAdaptiveHeap::is_memory_pressure_critical(metrics));
        EXPECT_GT(mem_pressure, 5.0);
        EXPECT_LE(mem_pressure, 25.0);
        break;
      }

        // Critical
      case 3: {
        EXPECT_TRUE(ZAdaptiveHeap::is_memory_pressure_concerning(metrics));
        EXPECT_TRUE(ZAdaptiveHeap::is_memory_pressure_high(metrics));
        EXPECT_TRUE(ZAdaptiveHeap::is_memory_pressure_critical(metrics));
        EXPECT_GT(mem_pressure, 25.0);
      }
      }

      mem_pressures[i * 101 + j] = mem_pressure;

      if (j > 0) {
        // Pressure should not go down when only increasing container usage
        EXPECT_GE(mem_pressure, mem_pressures[i * 101 + j - 1]);
      }

      if (i > 0) {
        // Pressure should not go down when only increasing machine usage
        EXPECT_GE(mem_pressure, mem_pressures[(i - 1) * 101 + j]);
      }

      double epsilon = 0.001;

      if (j > 1) {
        double prev = mem_pressures[i * 101 + j - 1];
        double prev_prev = mem_pressures[i * 101 + j - 2];

        double diff = mem_pressure - prev;
        double prev_diff = prev - prev_prev;

        // Rate of increase should never go down when increasing container usage
        EXPECT_GE(diff - prev_diff, -epsilon);
      }

      if (i > 1) {
        double prev = mem_pressures[(i - 1) * 101 + j];
        double prev_prev = mem_pressures[(i - 2) * 101 + j];

        double diff = mem_pressure - prev;
        double prev_diff = prev - prev_prev;

        // Rate of increase should never go down when increasing machine usage
        EXPECT_GE(diff - prev_diff, -epsilon);
      }
    }
  }
}
