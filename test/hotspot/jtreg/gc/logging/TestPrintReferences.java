/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8136991 8186402 8186465 8188245
 * @summary Validate the reference processing logging
 * @key gc
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 */

import java.lang.ref.SoftReference;
import java.math.BigDecimal;
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
    test(true);
    test(false);
  }

  static String indent(int count) {
    return " {" + count + "}";
  }

  public static void test(boolean parallelRefProcEnabled) throws Exception {
    ProcessBuilder pb_enabled = ProcessTools.createJavaProcessBuilder("-Xlog:gc+phases+ref=debug",
                                                                      "-XX:+UseG1GC",
                                                                      "-Xmx32M",
                                                                      // Explicit thread setting is required to avoid using only 1 thread
                                                                      "-XX:" + (parallelRefProcEnabled ? "+" : "-") + "ParallelRefProcEnabled",
                                                                      "-XX:ParallelGCThreads=2",
                                                                      GCTest.class.getName());
    OutputAnalyzer output = new OutputAnalyzer(pb_enabled.start());

    checkLogFormat(output, parallelRefProcEnabled);
    checkLogValue(output);

    output.shouldHaveExitValue(0);
  }

  // Find the first Reference Processing log and check its format.
  public static void checkLogFormat(OutputAnalyzer output, boolean parallelRefProcEnabled) {
    String countRegex = "[0-9]+";
    String timeRegex = doubleRegex + "ms";
    String totalRegex = gcLogTimeRegex + indent(4) + referenceProcessing + ": " + timeRegex + "\n";
    String balanceRegex = parallelRefProcEnabled ? gcLogTimeRegex + indent(8) + "Balance queues: " + timeRegex + "\n" : "";
    String softRefRegex = gcLogTimeRegex + indent(6) + softReference + ": " + timeRegex + "\n";
    String weakRefRegex = gcLogTimeRegex + indent(6) + weakReference + ": " + timeRegex + "\n";
    String finalRefRegex = gcLogTimeRegex + indent(6) + finalReference + ": " + timeRegex + "\n";
    String phantomRefRegex = gcLogTimeRegex + indent(6) + phantomReference + ": " + timeRegex + "\n";
    String refDetailRegex = gcLogTimeRegex + indent(8) + phase2 + ": " + timeRegex + "\n" +
                            gcLogTimeRegex + indent(8) + phase3 + ": " + timeRegex + "\n" +
                            gcLogTimeRegex + indent(8) + "Discovered: " + countRegex + "\n" +
                            gcLogTimeRegex + indent(8) + "Cleared: " + countRegex + "\n";
    String softRefDetailRegex = gcLogTimeRegex + indent(8) + phase1 + ": " + timeRegex + "\n" + refDetailRegex;

    output.shouldMatch(/* Total Reference processing time */
                       totalRegex +
                       /* SoftReference processing */
                       softRefRegex + balanceRegex + softRefDetailRegex +
                       /* WeakReference processing */
                       weakRefRegex + balanceRegex + refDetailRegex +
                       /* FinalReference processing */
                       finalRefRegex + balanceRegex + refDetailRegex +
                       /* PhantomReference processing */
                       phantomRefRegex + balanceRegex + refDetailRegex
                       );
  }

  // After getting time value, update 'output' for next use.
  public static BigDecimal getTimeValue(String name, int indentCount) {
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

    // Convert to BigDecimal to control the precision of floating point arithmetic.
    return BigDecimal.valueOf(result);
  }

  // Reference log is printing 1 decimal place of elapsed time.
  // So sum of each sub-phases could be slightly larger than the enclosing phase in some cases.
  // e.g. If there are 3 sub-phases:
  //      Actual value:  SoftReference(5.55) = phase1(1.85) + phase2(1.85) + phase3(1.85)
  //      Log value:     SoftReference(5.6) = phase1(1.9) + phase2(1.9) + phase3(1.9)
  //      When checked:  5.6 < 5.7 (sum of phase1~3)
  // Because of this we need method to verify that our measurements and calculations are valid.
  public static boolean greaterThanOrApproximatelyEqual(BigDecimal phaseTime, BigDecimal sumOfSubPhasesTime, BigDecimal tolerance) {
    if (phaseTime.compareTo(sumOfSubPhasesTime) >= 0) {
      // phaseTime is greater than or equal.
      return true;
    }

    BigDecimal diff = sumOfSubPhasesTime.subtract(phaseTime);
    if (diff.compareTo(tolerance) <= 0) {
      // Difference is within tolerance, so approximately equal.
      return true;
    }

    // sumOfSubPhasesTime is greater than phaseTime and not within tolerance.
    return false;
  }

  public static BigDecimal checkPhaseTime(String refType) {
    BigDecimal phaseTime = getTimeValue(refType, 2);
    BigDecimal sumOfSubPhasesTime = BigDecimal.valueOf(0.0);

    if (softReference.equals(refType)) {
      sumOfSubPhasesTime = sumOfSubPhasesTime.add(getTimeValue(phase1, 4));
    }
    sumOfSubPhasesTime = sumOfSubPhasesTime.add(getTimeValue(phase2, 4));
    sumOfSubPhasesTime = sumOfSubPhasesTime.add(getTimeValue(phase3, 4));

    // If there are 3 sub-phases, we should allow 0.1 tolerance.
    final BigDecimal toleranceFor3SubPhases = BigDecimal.valueOf(0.1);
    if (!greaterThanOrApproximatelyEqual(phaseTime, sumOfSubPhasesTime, toleranceFor3SubPhases)) {
      throw new RuntimeException(refType +" time(" + phaseTime +
                                 "ms) is less than the sum(" + sumOfSubPhasesTime + "ms) of each phases");
    }

    return phaseTime;
  }

  // Find the first concurrent Reference Processing log and compare phase time vs. sum of sub-phases.
  public static void checkLogValue(OutputAnalyzer out) {
    output = out.getStdout();

    String patternString = gcLogTimeRegex + indent(0) +
                           referenceProcessing + ": " + "[0-9]+[.,][0-9]+";
    Matcher m = Pattern.compile(patternString).matcher(output);
    if (m.find()) {
        int start = m.start();
        int end = output.length();
        // If there's another concurrent Reference Processing log, ignore it.
        if (m.find()) {
            end = m.start();
        }
        if (start != -1) {
            output = output.substring(start, end);
            checkTrimmedLogValue();
        }
     }
  }

  public static void checkTrimmedLogValue() {
    BigDecimal refProcTime = getTimeValue(referenceProcessing, 0);

    BigDecimal sumOfSubPhasesTime = checkPhaseTime(softReference);
    sumOfSubPhasesTime = sumOfSubPhasesTime.add(checkPhaseTime(weakReference));
    sumOfSubPhasesTime = sumOfSubPhasesTime.add(checkPhaseTime(finalReference));
    sumOfSubPhasesTime = sumOfSubPhasesTime.add(checkPhaseTime(phantomReference));

    // If there are 4 sub-phases, we should allow 0.2 tolerance.
    final BigDecimal toleranceFor4SubPhases = BigDecimal.valueOf(0.2);
    if (!greaterThanOrApproximatelyEqual(refProcTime, sumOfSubPhasesTime, toleranceFor4SubPhases)) {
      throw new RuntimeException("Reference Processing time(" + refProcTime + "ms) is less than the sum("
                                 + sumOfSubPhasesTime + "ms) of each phases");
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
