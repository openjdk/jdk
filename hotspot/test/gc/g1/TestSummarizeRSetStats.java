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
 * @test TestSummarizeRSetStats.java
 * @bug 8013895
 * @library /testlibrary
 * @build TestSummarizeRSetStats
 * @summary Verify output of -XX:+G1SummarizeRSetStats
 * @run main TestSummarizeRSetStats
 *
 * Test the output of G1SummarizeRSetStats in conjunction with G1SummarizeRSetStatsPeriod.
 */

import com.oracle.java.testlibrary.*;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.Arrays;

class RunSystemGCs {
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

public class TestSummarizeRSetStats {

    public static String runTest(String[] additionalArgs, int numGCs) throws Exception {
        ArrayList<String> finalargs = new ArrayList<String>();
        String[] defaultArgs = new String[] {
            "-XX:+UseG1GC",
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

        finalargs.add(RunSystemGCs.class.getName());
        finalargs.add(String.valueOf(numGCs));

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            finalargs.toArray(new String[0]));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        output.shouldHaveExitValue(0);

        String result = output.getStdout();
        return result;
    }

    private static void expectStatistics(String result, int expectedCumulative, int expectedPeriodic) throws Exception {
        int actualTotal = result.split("Concurrent RS processed").length - 1;
        int actualCumulative = result.split("Cumulative RS summary").length - 1;

        if (expectedCumulative != actualCumulative) {
            throw new Exception("Incorrect amount of RSet summaries at the end. Expected " + expectedCumulative + ", got " + actualCumulative);
        }

        if (expectedPeriodic != (actualTotal - actualCumulative)) {
            throw new Exception("Incorrect amount of per-period RSet summaries at the end. Expected " + expectedPeriodic + ", got " + (actualTotal - actualCumulative));
        }
    }

    public static void main(String[] args) throws Exception {
        String result;

        // no RSet statistics output
        result = runTest(null, 0);
        expectStatistics(result, 0, 0);

        // no RSet statistics output
        result = runTest(null, 2);
        expectStatistics(result, 0, 0);

        // no RSet statistics output
        result = runTest(new String[] { "-XX:G1SummarizeRSetStatsPeriod=1" }, 3);
        expectStatistics(result, 0, 0);

        // single RSet statistics output at the end
        result = runTest(new String[] { "-XX:+G1SummarizeRSetStats" }, 0);
        expectStatistics(result, 1, 0);

        // single RSet statistics output at the end
        result = runTest(new String[] { "-XX:+G1SummarizeRSetStats" }, 2);
        expectStatistics(result, 1, 0);

        // single RSet statistics output
        result = runTest(new String[] { "-XX:+G1SummarizeRSetStats", "-XX:G1SummarizeRSetStatsPeriod=1" }, 0);
        expectStatistics(result, 1, 0);

        // two times RSet statistics output
        result = runTest(new String[] { "-XX:+G1SummarizeRSetStats", "-XX:G1SummarizeRSetStatsPeriod=1" }, 1);
        expectStatistics(result, 1, 1);

        // four times RSet statistics output
        result = runTest(new String[] { "-XX:+G1SummarizeRSetStats", "-XX:G1SummarizeRSetStatsPeriod=1" }, 3);
        expectStatistics(result, 1, 3);

        // three times RSet statistics output
        result = runTest(new String[] { "-XX:+G1SummarizeRSetStats", "-XX:G1SummarizeRSetStatsPeriod=2" }, 3);
        expectStatistics(result, 1, 2);

        // single RSet statistics output
        result = runTest(new String[] { "-XX:+G1SummarizeRSetStats", "-XX:G1SummarizeRSetStatsPeriod=100" }, 3);
        expectStatistics(result, 1, 1);
    }
}

