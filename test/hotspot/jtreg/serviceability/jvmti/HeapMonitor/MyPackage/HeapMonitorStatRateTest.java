/*
 * Copyright (c) 2018, Google and/or its affiliates. All rights reserved.
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

package MyPackage;

/**
 * @test
 * @summary Verifies the JVMTI Heap Monitor sampling rate average.
 * @build Frame HeapMonitor
 * @compile HeapMonitorStatRateTest.java
 * @requires vm.compMode != "Xcomp"
 * @run main/othervm/native -agentlib:HeapMonitorTest MyPackage.HeapMonitorStatRateTest
 */

public class HeapMonitorStatRateTest {

  private native static double getAverageRate();

  private static boolean testRateOnce(int rate, boolean throwIfFailure) {
    HeapMonitor.resetEventStorage();
    HeapMonitor.setSamplingRate(rate);

    HeapMonitor.enableSamplingEvents();

    int allocationTotal = 10 * 1024 * 1024;
    HeapMonitor.allocateSize(allocationTotal);

    HeapMonitor.disableSamplingEvents();

    double actualCount = HeapMonitor.getEventStorageElementCount();
    double expectedCount = allocationTotal / rate;

    double error = Math.abs(actualCount - expectedCount);
    double errorPercentage = error / expectedCount * 100;

    boolean failure = (errorPercentage > 10.0);

    if (failure && throwIfFailure) {
      throw new RuntimeException("Rate average over 10% for rate " + rate + " -> " + actualCount
          + ", " + expectedCount);
    }

    return failure;
  }


  private static void testRate(int rate) {
    // Test the rate twice, it can happen that the test is "unlucky" and the rate just goes above
    // the 10% mark. So try again to squash flakiness.
    // Flakiness is due to the fact that this test is dependent on the sampling rate, which is a
    // statistical geometric variable around the sampling rate. This means that the test could be
    // unlucky and not achieve the mean average fast enough for the test case.
    if (!testRateOnce(rate, false)) {
      testRateOnce(rate, true);
    }
  }

  public static void main(String[] args) {
    int[] tab = {1024, 8192};

    for (int rateIdx = 0; rateIdx < tab.length; rateIdx++) {
      testRate(tab[rateIdx]);
    }
  }
}
