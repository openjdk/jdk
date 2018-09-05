/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @build Frame HeapMonitor
 * @summary Verifies the JVMTI Heap Monitor interval when allocating arrays.
 * @compile HeapMonitorStatArrayCorrectnessTest.java
 * @run main/othervm/native -agentlib:HeapMonitorTest MyPackage.HeapMonitorStatArrayCorrectnessTest
 */

public class HeapMonitorStatArrayCorrectnessTest {

  // Do 100000 iterations and expect maxIteration / multiplier samples.
  private static final int maxIteration = 100000;
  private static int array[];

  private static void allocate(int size) {
    for (int j = 0; j < maxIteration; j++) {
      array = new int[size];
    }
  }

  public static void main(String[] args) {
    int sizes[] = {1000, 10000, 100000};

    for (int currentSize : sizes) {
      System.out.println("Testing size " + currentSize);

      HeapMonitor.resetEventStorage();
      if (!HeapMonitor.eventStorageIsEmpty()) {
        throw new RuntimeException("Should not have any events stored yet.");
      }

      HeapMonitor.enableSamplingEvents();

      // 111 is as good a number as any.
      final int samplingMultiplier = 111;
      HeapMonitor.setSamplingInterval(samplingMultiplier * currentSize);

      allocate(currentSize);

      HeapMonitor.disableSamplingEvents();

      // For simplifications, we ignore the array memory usage for array internals (with the array
      // sizes requested, it should be a negligible oversight).
      //
      // That means that with maxIterations, the loop in the method allocate requests:
      //    maxIterations * currentSize * 4 bytes (4 for integers)
      //
      // Via the enable sampling, the code requests a sample every samplingMultiplier * currentSize bytes.
      //
      // Therefore, the expected sample number is:
      //   (maxIterations * currentSize * 4) / (samplingMultiplier * currentSize);
      double expected = maxIteration;
      expected *= 4;
      expected /= samplingMultiplier;

      // 10% error ensures a sanity test without becoming flaky.
      // Flakiness is due to the fact that this test is dependent on the sampling interval, which is a
      // statistical geometric variable around the sampling interval. This means that the test could be
      // unlucky and not achieve the mean average fast enough for the test case.
      if (!HeapMonitor.statsHaveExpectedNumberSamples((int) expected, 10)) {
        throw new RuntimeException("Statistics should show about " + expected + " samples.");
      }
    }
  }
}
