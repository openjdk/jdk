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

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;

/** API for handling the underlying heap sampling monitoring system. */
public class HeapMonitor {
  private static int[][] arrays;
  private static int allocationIterations = 1000;

  static {
    try {
      System.loadLibrary("HeapMonitorTest");
    } catch (UnsatisfiedLinkError ule) {
      System.err.println("Could not load HeapMonitor library");
      System.err.println("java.library.path: " + System.getProperty("java.library.path"));
      throw ule;
    }
  }

  /** Set a specific sampling interval, 0 samples every allocation. */
  public native static void setSamplingInterval(int interval);
  public native static void enableSamplingEvents();
  public native static boolean enableSamplingEventsForTwoThreads(Thread firstThread, Thread secondThread);
  public native static void disableSamplingEvents();

  /**
   * Allocate memory but first create a stack trace.
   *
   * @return list of frames for the allocation.
   */
  public static List<Frame> allocate() {
    int sum = 0;
    List<Frame> frames = new ArrayList<Frame>();
    allocate(frames);
    frames.add(new Frame("allocate", "()Ljava/util/List;", "HeapMonitor.java", 62));
    return frames;
  }

  private static void allocate(List<Frame> frames) {
    int sum = 0;
    for (int j = 0; j < allocationIterations; j++) {
      sum += actuallyAllocate();
    }
    frames.add(new Frame("actuallyAllocate", "()I", "HeapMonitor.java", 97));
    frames.add(new Frame("allocate", "(Ljava/util/List;)V", "HeapMonitor.java", 70));
  }

  public static List<Frame> repeatAllocate(int max) {
    List<Frame> frames = null;
    for (int i = 0; i < max; i++) {
      frames = allocate();
    }
    frames.add(new Frame("repeatAllocate", "(I)Ljava/util/List;", "HeapMonitor.java", 79));
    return frames;
  }

  private static int actuallyAllocate() {
    int sum = 0;

    // Let us assume that a 1-element array is 24 bytes of memory and we want
    // 2MB allocated.
    int iterations = (1 << 19) / 6;

    if (arrays == null) {
      arrays = new int[iterations][];
    }

    for (int i = 0; i < iterations; i++) {
      int tmp[] = new int[1];
      // Force it to be kept and, at the same time, wipe out any previous data.
      arrays[i] = tmp;
      sum += arrays[0][0];
    }
    return sum;
  }

  private static double averageOneElementSize;
  private static native double getAverageSize();

  // Calculate the size of a 1-element array in order to assess average sampling interval
  // via the HeapMonitorStatIntervalTest. This is needed because various GCs could add
  // extra memory to arrays.
  // This is done by allocating a 1-element array and then looking in the heap monitoring
  // samples for the average size of objects collected.
  public static void calculateAverageOneElementSize() {
    enableSamplingEvents();
    // Assume a size of 24 for the average size.
    averageOneElementSize = 24;

    // Call allocateSize once, this allocates the internal array for the iterations.
    int totalSize = 10 * 1024 * 1024;
    allocateSize(totalSize);

    // Reset the storage and now really track the size of the elements.
    resetEventStorage();
    allocateSize(totalSize);
    disableSamplingEvents();

    // Get the actual average size.
    averageOneElementSize = getAverageSize();
    if (averageOneElementSize == 0) {
      throw new RuntimeException("Could not calculate the average size of a 1-element array.");
    }
  }

  public static int allocateSize(int totalSize) {
    if (averageOneElementSize == 0) {
      throw new RuntimeException("Average size of a 1-element array was not calculated.");
    }

    int sum = 0;

    int iterations = (int) (totalSize / averageOneElementSize);

    if (arrays == null || arrays.length < iterations) {
      arrays = new int[iterations][];
    }

    System.out.println("Allocating for " + iterations);
    for (int i = 0; i < iterations; i++) {
      int tmp[] = new int[1];

      // Force it to be kept and, at the same time, wipe out any previous data.
      arrays[i] = tmp;
      sum += arrays[0][0];
    }

    return sum;
  }

  /** Remove the reference to the global array to free data at the next GC. */
  public static void freeStorage() {
    arrays = null;
  }

  public static int[][][] sampleEverything() {
    enableSamplingEvents();
    setSamplingInterval(0);

    // Loop around an allocation loop and wait until the tlabs have settled.
    final int maxTries = 10;
    int[][][] result = new int[maxTries][][];
    for (int i = 0; i < maxTries; i++) {
      final int maxInternalTries = 400;
      result[i] = new int[maxInternalTries][];

      resetEventStorage();
      for (int j = 0; j < maxInternalTries; j++) {
        final int size = 1000;
        result[i][j] = new int[size];
      }

      int sampledEvents = sampledEvents();
      if (sampledEvents == maxInternalTries) {
        return result;
      }
    }

    throw new RuntimeException("Could not set the sampler");
  }

  public native static int sampledEvents();
  public native static boolean obtainedEvents(Frame[] frames, boolean checkLines);
  public native static boolean garbageContains(Frame[] frames, boolean checkLines);
  public native static boolean eventStorageIsEmpty();
  public native static void resetEventStorage();
  public native static int getEventStorageElementCount();
  public native static void forceGarbageCollection();
  public native static boolean enableVMEvents();

  private static boolean getCheckLines() {
    boolean checkLines = true;

    // Do not check lines for Graal since it is not always "precise" with BCIs at uncommon traps.
    try {
      HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);

      VMOption enableJVMCI = bean.getVMOption("EnableJVMCI");
      VMOption useJVMCICompiler = bean.getVMOption("UseJVMCICompiler");
      String compiler = System.getProperty("jvmci.Compiler");

      checkLines = !(enableJVMCI.getValue().equals("true")
          && useJVMCICompiler.getValue().equals("true") && compiler.equals("graal"));
    } catch (Exception e) {
      // NOP.
    }

    return checkLines;
  }

  public static boolean obtainedEvents(Frame[] frames) {
    return obtainedEvents(frames, getCheckLines());
  }

  public static boolean garbageContains(Frame[] frames) {
    return garbageContains(frames, getCheckLines());
  }

  public static boolean statsHaveExpectedNumberSamples(int expected, int acceptedErrorPercentage) {
    double actual = getEventStorageElementCount();
    double diffPercentage = Math.abs(actual - expected) / expected;
    return diffPercentage < acceptedErrorPercentage;
  }

  public static void setAllocationIterations(int iterations) {
    allocationIterations = iterations;
  }
}
