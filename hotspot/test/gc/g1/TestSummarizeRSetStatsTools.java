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
 * Common helpers for TestSummarizeRSetStats* tests
 */

import sun.management.ManagementFactoryHelper;
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;

import com.oracle.java.testlibrary.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.Arrays;

class VerifySummaryOutput {
    // 4M size, both are directly allocated into the old gen
    static Object[] largeObject1 = new Object[1024 * 1024];
    static Object[] largeObject2 = new Object[1024 * 1024];

    static int[] temp;

    public static void main(String[] args) {
        // create some cross-references between these objects
        for (int i = 0; i < largeObject1.length; i++) {
            largeObject1[i] = largeObject2;
        }

        for (int i = 0; i < largeObject2.length; i++) {
            largeObject2[i] = largeObject1;
        }

        int numGCs = Integer.parseInt(args[0]);

        if (numGCs > 0) {
            // try to force a minor collection: the young gen is 4M, the
            // amount of data allocated below is roughly that (4*1024*1024 +
            // some header data)
            for (int i = 0; i < 1024 ; i++) {
                temp = new int[1024];
            }
        }

        for (int i = 0; i < numGCs - 1; i++) {
            System.gc();
        }
    }
}

public class TestSummarizeRSetStatsTools {

    // the VM is currently run using G1GC, i.e. trying to test G1 functionality.
    public static boolean testingG1GC() {
        HotSpotDiagnosticMXBean diagnostic = ManagementFactoryHelper.getDiagnosticMXBean();

        VMOption option = diagnostic.getVMOption("UseG1GC");
        if (option.getValue().equals("false")) {
          System.out.println("Skipping this test. It is only a G1 test.");
          return false;
        }
        return true;
    }

    public static String runTest(String[] additionalArgs, int numGCs) throws Exception {
        ArrayList<String> finalargs = new ArrayList<String>();
        String[] defaultArgs = new String[] {
            "-XX:+UseG1GC",
            "-XX:+UseCompressedOops",
            "-Xmn4m",
            "-Xmx20m",
            "-XX:InitiatingHeapOccupancyPercent=100", // we don't want the additional GCs due to initial marking
            "-XX:+PrintGC",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:G1HeapRegionSize=1M",
        };

        finalargs.addAll(Arrays.asList(defaultArgs));

        if (additionalArgs != null) {
            finalargs.addAll(Arrays.asList(additionalArgs));
        }

        finalargs.add(VerifySummaryOutput.class.getName());
        finalargs.add(String.valueOf(numGCs));

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            finalargs.toArray(new String[0]));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        output.shouldHaveExitValue(0);

        String result = output.getStdout();
        return result;
    }

    private static void checkCounts(int expected, int actual, String which) throws Exception {
        if (expected != actual) {
            throw new Exception("RSet summaries mention " + which + " regions an incorrect number of times. Expected " + expected + ", got " + actual);
        }
    }

    public static void expectPerRegionRSetSummaries(String result, int expectedCumulative, int expectedPeriodic) throws Exception {
        expectRSetSummaries(result, expectedCumulative, expectedPeriodic);
        int actualYoung = result.split("Young regions").length - 1;
        int actualHumonguous = result.split("Humonguous regions").length - 1;
        int actualFree = result.split("Free regions").length - 1;
        int actualOther = result.split("Old regions").length - 1;

        // the strings we check for above are printed four times per summary
        int expectedPerRegionTypeInfo = (expectedCumulative + expectedPeriodic) * 4;

        checkCounts(expectedPerRegionTypeInfo, actualYoung, "Young");
        checkCounts(expectedPerRegionTypeInfo, actualHumonguous, "Humonguous");
        checkCounts(expectedPerRegionTypeInfo, actualFree, "Free");
        checkCounts(expectedPerRegionTypeInfo, actualOther, "Old");
    }

    public static void expectRSetSummaries(String result, int expectedCumulative, int expectedPeriodic) throws Exception {
        int actualTotal = result.split("concurrent refinement").length - 1;
        int actualCumulative = result.split("Cumulative RS summary").length - 1;

        if (expectedCumulative != actualCumulative) {
            throw new Exception("Incorrect amount of RSet summaries at the end. Expected " + expectedCumulative + ", got " + actualCumulative);
        }

        if (expectedPeriodic != (actualTotal - actualCumulative)) {
            throw new Exception("Incorrect amount of per-period RSet summaries at the end. Expected " + expectedPeriodic + ", got " + (actualTotal - actualCumulative));
        }
    }
}

