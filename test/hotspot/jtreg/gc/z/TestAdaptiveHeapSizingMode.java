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

package gc.z;

/**
 * @test TestAdaptiveHeapSizingMode
 * @requires vm.gc.Z & vm.flagless
 * @summary Test that heap size flags affect ZGC Adaptive Heap Sizing mode.
 * @library / /test/lib
 * @run driver gc.z.TestAdaptiveHeapSizingMode
 */

import jdk.test.lib.JDKToolFinder;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestAdaptiveHeapSizingMode {
    private static List<String> createTestSpecificArgs(String... heapSizeArgs) {
      List<String> args = new ArrayList<>();
      args.add("-XX:+UseZGC");
      args.add("-XX:+ZAdaptiveHeapSizing");
      args.add("-Xlog:gc+init");
      args.addAll(Arrays.asList(heapSizeArgs));
      args.add("--version");
      return args;
    }

    private static void assertExpectedMode(OutputAnalyzer out, List<String> args, String expectedMode) {
      String output = out.getOutput();
      String needle = "Heap Sizing: " + expectedMode;
      if (!output.contains(needle)) {
        throw new AssertionError("Expected to find: \"" + needle + "\"\n" +
                                 "Arguments: " + String.join(" ", args) + "\n" +
                                 "Output:\n" + output);
      }
    }

    private static void assertHeapSizingMode(String expectedMode, String... heapSizeArgs) throws Exception {
      List<String> args = createTestSpecificArgs(heapSizeArgs);

      // Launch child JVM
      ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(args);
      OutputAnalyzer out = new OutputAnalyzer(pb.start());
      out.shouldHaveExitValue(0);

      assertExpectedMode(out, args, expectedMode);
    }

    private static void assertHeapSizingModeClean(String expectedMode, String... heapSizeArgs) throws Exception {
      String java = JDKToolFinder.getJDKTool("java");
      List<String> args = createTestSpecificArgs(heapSizeArgs);

      List<String> cmd = new ArrayList<>(args.size() + 1);
      cmd.add(java);    // executable
      cmd.addAll(args); // arguments

      // Launch child JVM
      ProcessBuilder pb = new ProcessBuilder(cmd);
      OutputAnalyzer out = new OutputAnalyzer(pb.start());
      out.shouldHaveExitValue(0);

      assertExpectedMode(out, args, expectedMode);
    }

    private static final String FIXED_MODE = "Fixed";
    private static final String IMPLICIT_MODE = "Adaptive (Implicit Boundaries)";
    private static final String EXPLICIT_MODE = "Adaptive (Explicit Boundaries)";

    public static void main(String[] args) throws Exception {
      // No flags
      assertHeapSizingModeClean(IMPLICIT_MODE);

      // Only lower-bound
      assertHeapSizingModeClean(EXPLICIT_MODE,
                                "-XX:MinHeapSize=100m");
      assertHeapSizingModeClean(EXPLICIT_MODE,
                                "-Xms100m");

      // Only upper-bound
      assertHeapSizingModeClean(EXPLICIT_MODE,
                                "-XX:MaxRAMPercentage=10");
      assertHeapSizingModeClean(EXPLICIT_MODE,
                                "-Xmx100m");

      // Xms == Xmx
      assertHeapSizingMode(FIXED_MODE,
                           "-Xms100m",
                           "-Xmx100m");

      // Xms != Xmx
      assertHeapSizingMode(EXPLICIT_MODE,
                           "-Xms50m",
                           "-Xmx100m");

      // MinHeapSize == Xmx
      assertHeapSizingMode(FIXED_MODE,
                           "-XX:MinHeapSize=100m",
                           "-Xmx100m");

      // MinHeapSize < Xmx
      assertHeapSizingMode(EXPLICIT_MODE,
                           "-XX:MinHeapSize=50m",
                           "-Xmx100m");
    }
}
