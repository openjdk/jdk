/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test TestSummarizeRSetStatsThreads
 * @bug 8025441
 * @summary Ensure that various values of worker threads/concurrent
 * refinement threads do not crash the VM.
 * @key gc
 * @library /testlibrary
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.java.testlibrary.ProcessTools;
import com.oracle.java.testlibrary.OutputAnalyzer;

public class TestSummarizeRSetStatsThreads {

  private static void runTest(int refinementThreads, int workerThreads) throws Exception {
    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                                              "-XX:+UnlockDiagnosticVMOptions",
                                                              "-XX:+G1SummarizeRSetStats",
                                                              "-XX:G1ConcRefinementThreads=" + refinementThreads,
                                                              "-XX:ParallelGCThreads=" + workerThreads,
                                                              "-version");

    OutputAnalyzer output = new OutputAnalyzer(pb.start());

    // check output to contain the string "Concurrent RS threads times (s)" followed by
    // the correct number of values in the next line.

    // a zero in refinement thread numbers indicates that the value in ParallelGCThreads should be used.
    // Additionally use at least one thread.
    int expectedNumRefinementThreads = refinementThreads == 0 ? workerThreads : refinementThreads;
    expectedNumRefinementThreads = Math.max(1, expectedNumRefinementThreads);
    // create the pattern made up of n copies of a floating point number pattern
    String numberPattern = String.format("%0" + expectedNumRefinementThreads + "d", 0)
      .replace("0", "\\s+\\d+\\.\\d+");
    String pattern = "Concurrent RS threads times \\(s\\)$" + numberPattern + "$";
    Matcher m = Pattern.compile(pattern, Pattern.MULTILINE).matcher(output.getStdout());

    if (!m.find()) {
      throw new Exception("Could not find correct output for concurrent RS threads times in stdout," +
        " should match the pattern \"" + pattern + "\", but stdout is \n" + output.getStdout());
    }
    output.shouldHaveExitValue(0);
  }

  public static void main(String[] args) throws Exception {
    if (!TestSummarizeRSetStatsTools.testingG1GC()) {
      return;
    }
    // different valid combinations of number of refinement and gc worker threads
    runTest(0, 0);
    runTest(0, 5);
    runTest(5, 0);
    runTest(10, 10);
    runTest(1, 2);
    runTest(4, 3);
  }
}
