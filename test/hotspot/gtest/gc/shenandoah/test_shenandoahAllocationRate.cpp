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

#include "gc/shenandoah/shenandoahAllocRate.hpp"

class ShenandoahMockClock {
public:
  static jlong Counter;
  static jlong elapsed_counter() {
    const jlong result = Counter;
    Counter += 10;
    return result;
  }

  static jlong elapsed_frequency() {
    return 1;
  }
};

class ShenandoahSlowClock {
public:
  static jlong elapsed_counter() {
    return 1;
  }

  static jlong elapsed_frequency() {
    return 1;
  }
};

jlong ShenandoahMockClock::Counter = 0;


TEST_VM(ShenandoahAllocationRateTest, ignore_too_small_sample) {
  ShenandoahAllocRate<ShenandoahMockClock> rate;
  rate.allocated(512);
  EXPECT_EQ(rate.average(), 0);
}

TEST_VM(ShenandoahAllocationRateTest, ignore_too_small_elapsed_time) {
  ShenandoahAllocRate<ShenandoahSlowClock> rate;
  rate.allocated(2048);
  rate.allocated(2048);
  EXPECT_EQ(rate.average(), 2048);
}

TEST_VM(ShenandoahAllocationRateTest, ten_second_average) {
  ShenandoahAllocRate<ShenandoahMockClock> rate;
  rate.allocated(2048); // t = 0
  rate.allocated(2048); // t = 10
  EXPECT_EQ(rate.average(), 409.6);
}
