/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestPrintReferences
 * @bug 8136991 8186402 8186465
 * @summary Validate the reference processing logging
 * @key gc
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 */

import java.lang.ref.SoftReference;
import java.util.ArrayList;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class TestPrintReferences {
  static String output;
  static final String doubleRegex = "[0-9]+[.,][0-9]+";
  static final String referenceProcessing = "Reference Processing";
  static final String softReference = "SoftReference";
  static final String weakReference = "WeakReference";
  static final String finalReference = "FinalReference";
  static final String phantomReference = "PhantomReference";
  static final String phase1 = "Phase1";
  static final String phase2 = "Phase2";
  static final String phase3 = "Phase3";
  static final String gcLogTimeRegex = ".* GC\\([0-9]+\\) ";

  public static void main(String[] args) throws Exception {
    ProcessBuilder pb_enabled = ProcessTools.createJavaProcessBuilder("-Xlog:gc+phases+ref=debug",
                                                                      "-XX:+UseG1GC",
                                                                      "-Xmx32M",
                                                                      // Explicit thread setting is required to avoid using only 1 thread
                                                                      "-XX:ParallelGCThreads=2",
                                                                      GCTest.class.getName());
    OutputAnalyzer output = new OutputAnalyzer(pb_enabled.start());

    checkLogFormat(output);
    checkLogValue(output);

    output.shouldHaveExitValue(0);
  }

  static String indent(int count) {
    return " {" + count + "}";
  }

  // Find the first Reference Processing log and check its format.
  public static void checkLogFormat(OutputAnalyzer output) {
    String countRegex = "[0-9]+";
    String timeRegex = doubleRegex + "ms";
    String totalRegex = gcLogTimeRegex + indent(4) + referenceProcessing + ": " + timeRegex + "\n";
    String balanceRegex = gcLogTimeRegex + indent(8) + "Balance queues: " + timeRegex + "\n";
    String softRefRegex = gcLogTimeRegex + indent(6) + softReference + ": " + timeRegex + "\n";
    String weakRefRegex = gcLogTimeRegex + indent(6) + weakReference + ": " + timeRegex + "\n";
    String finalRefRegex = gcLogTimeRegex + indent(6) + finalReference + ": " + timeRegex + "\n";
    String phantomRefRegex = gcLogTimeRegex + indent(6) + phantomReference + ": " + timeRegex + "\n";
    String refDetailRegex = gcLogTimeRegex + indent(8) + phase2 + ": " + timeRegex + "\n" +
                            gcLogTimeRegex + indent(8) + phase3 + ": " + timeRegex + "\n" +
                            gcLogTimeRegex + indent(8) + "Discovered: " + countRegex + "\n" +
                            gcLogTimeRegex + indent(8) + "Cleared: " + countRegex + "\n";
    String softRefDetailRegex = gcLogTimeRegex + indent(8) + phase1 + ": " + timeRegex + "\n" + refDetailRegex;
    String enqueueRegex = gcLogTimeRegex + indent(4) + "Reference Enqueuing: " + timeRegex + "\n";
    String enqueueDetailRegex = gcLogTimeRegex + indent(6) + "Reference Counts:  Soft: " + countRegex +
                                "  Weak: " + countRegex + "  Final: " + countRegex + "  Phantom: " + countRegex + "\n";

    output.shouldMatch(/* Total Reference processing time */
                       totalRegex +
                       /* SoftReference processing */
                       softRefRegex + balanceRegex + softRefDetailRegex +
                       /* WeakReference processing */
                       weakRefRegex + balanceRegex + refDetailRegex +
                       /* FinalReference processing */
                       finalRefRegex + balanceRegex + refDetailRegex +
                       /* PhantomReference processing */
                       phantomRefRegex + balanceRegex + refDetailRegex +
                       /* Total Enqueuing time */
                       enqueueRegex +
                         /* Enqueued Stats */
                       enqueueDetailRegex
                       );
  }

  // After getting time value, update 'output' for next use.
  public static double getTimeValue(String name, int indentCount) {
    // Pattern of 'name', 'value' and some extra strings.
    String patternString = gcLogTimeRegex + indent(indentCount) + name + ": " + "(" + doubleRegex + ")";
    Matcher m = Pattern.compile(patternString).matcher(output);
     if (!m.find()) {
      throw new RuntimeException("Could not find time log for " + patternString);
     }

    String match = m.group();
    String value = m.group(1);

    double result = Double.parseDouble(value);

    int index = output.indexOf(match);
    if (index != -1) {
      output = output.substring(index, output.length());
    }

    return result;
  }

  // Reference log is printing 1 decimal place of elapsed time.
  // So sum of each sub-phases could be slightly larger than the enclosing phase in some cases.
  // As the maximum of sub-phases is 3, allow 0.1 of TOLERANCE.
  // e.g. Actual value:  SoftReference(5.55) = phase1(1.85) + phase2(1.85) + phase3(1.85)
  //      Log value:     SoftReference(5.6) = phase1(1.9) + phase2(1.9) + phase3(1.9)
  //      When checked:  5.6 < 5.7 (sum of phase1~3)
  public static boolean approximatelyEqual(double a, double b) {
    final double TOLERANCE = 0.1;

    return Math.abs(a - b) <= TOLERANCE;
  }

  // Return false, if 'total' is larger and not approximately equal to 'refTime'.
  public static boolean compare(double refTime, double total) {
    return (refTime < total) && (!approximatelyEqual(refTime, total));
  }

  public static double checkRefTime(String refType) {
    double refTime = getTimeValue(refType, 2);
    double total = 0.0;

    if (softReference.equals(refType)) {
      total += getTimeValue(phase1, 4);
    }
    total += getTimeValue(phase2, 4);
    total += getTimeValue(phase3, 4);

    if (compare(refTime, total)) {
      throw new RuntimeException(refType +" time(" + refTime +
                                 "ms) is less than the sum(" + total + "ms) of each phases");
    }

    return refTime;
  }

  // Find the first concurrent Reference Processing log and compare sub-time vs. total.
  public static void checkLogValue(OutputAnalyzer out) {
    output = out.getStdout();

    double refProcTime = getTimeValue(referenceProcessing, 0);

    double total = 0.0;
    total += checkRefTime(softReference);
    total += checkRefTime(weakReference);
    total += checkRefTime(finalReference);
    total += checkRefTime(phantomReference);

    if (compare(refProcTime, total)) {
      throw new RuntimeException("Reference Processing time(" + refProcTime + "ms) is less than the sum("
                                 + total + "ms) of each phases");
    }
  }

  static class GCTest {
    static final int SIZE = 512 * 1024;
    static Object[] dummy = new Object[SIZE];

    public static void main(String [] args) {
      for (int i = 0; i < SIZE; i++) {
        dummy[i] = new SoftReference<>(new Object());
      }
    }
  }
}
