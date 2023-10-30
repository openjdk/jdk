/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023 Red Hat, Inc. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test id=trimNative
 * @requires vm.flagless
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestTrimNative trimNative
 */

/*
 * @test id=trimNativeStrict
 * @requires vm.flagless
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/manual TestTrimNative trimNativeStrict
 */

/*
 * @test id=trimNativeHighInterval
 * @summary High interval trimming should not even kick in for short program runtimes
 * @requires vm.flagless
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestTrimNative trimNativeHighInterval
 */

/*
 * @test id=trimNativeLowInterval
 * @summary Very low (sub-second) interval, nothing should explode
 * @requires vm.flagless
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestTrimNative trimNativeLowInterval
 */

/*
 * @test id=trimNativeLowIntervalStrict
 * @summary Very low (sub-second) interval, nothing should explode (stricter test, manual mode)
 * @requires vm.flagless
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/manual TestTrimNative trimNativeLowIntervalStrict
 */

/*
 * @test id=testOffByDefault
 * @summary Test that trimming is disabled by default
 * @requires vm.flagless
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestTrimNative testOffByDefault
 */

/*
 * @test id=testOffExplicit
 * @summary Test that trimming can be disabled explicitly
 * @requires vm.flagless
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestTrimNative testOffExplicit
 */

/*
 * @test id=testOffOnNonCompliantPlatforms
 * @summary Test that trimming is correctly reported as unavailable if unavailable
 * @requires vm.flagless
 * @requires (os.family!="linux") | vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestTrimNative testOffOnNonCompliantPlatforms
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.whitebox.WhiteBox;

public class TestTrimNative {

    // Actual RSS increase is a lot larger than 4 MB. Depends on glibc overhead, and NMT malloc headers in debug VMs.
    // We need small-grained allocations to make sure they actually increase RSS (all touched) and to see the
    // glibc-retaining-memory effect.
    static final int szAllocations = 128;
    static final int totalAllocationsSize = 128 * 1024 * 1024; // 128 MB total
    static final int numAllocations = totalAllocationsSize / szAllocations;

    static long[] ptrs = new long[numAllocations];

    enum Unit {
        B(1), K(1024), M(1024*1024), G(1024*1024*1024);
        public final long size;
        Unit(long size) { this.size = size; }
    }

    private static String[] prepareOptions(String[] extraVMOptions, String[] programOptions) {
        List<String> allOptions = new ArrayList<String>();
        if (extraVMOptions != null) {
            allOptions.addAll(Arrays.asList(extraVMOptions));
        }
        allOptions.add("-Xmx128m");
        allOptions.add("-Xms128m"); // Stabilize RSS
        allOptions.add("-XX:+AlwaysPreTouch"); // Stabilize RSS
        allOptions.add("-XX:+UnlockDiagnosticVMOptions"); // For whitebox
        allOptions.add("-XX:+WhiteBoxAPI");
        allOptions.add("-Xbootclasspath/a:.");
        allOptions.add("-XX:-ExplicitGCInvokesConcurrent"); // Invoke explicit GC on System.gc
        allOptions.add("-Xlog:trimnative=debug");
        allOptions.add("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED");
        if (programOptions != null) {
            allOptions.addAll(Arrays.asList(programOptions));
        }
        return allOptions.toArray(new String[0]);
    }

    private static OutputAnalyzer runTestWithOptions(String[] extraOptions, String[] programOptions) throws IOException {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(prepareOptions(extraOptions, programOptions));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        return output;
    }

    private static void checkExpectedLogMessages(OutputAnalyzer output, boolean expectEnabled,
                                                 int expectedInterval) {
        if (expectEnabled) {
            output.shouldContain("Periodic native trim enabled (interval: " + expectedInterval + " ms");
            output.shouldContain("Native heap trimmer start");
        } else {
            output.shouldNotContain("Periodic native trim enabled");
        }
    }

    /**
     * Given JVM output, look for one or more log lines that describes a successful negative trim. The total amount
     * of trims should be matching about what the test program allocated.
     * @param output
     * @param minTrimsExpected min number of periodic trim lines expected in UL log
     * @param maxTrimsExpected min number of periodic trim lines expected in UL log
     * @param strict: if true, expect RSS to go down; if false, just look for trims without looking at RSS.
     */
    private static void parseOutputAndLookForNegativeTrim(OutputAnalyzer output, int minTrimsExpected,
                                                          int maxTrimsExpected, boolean strict) {
        output.reportDiagnosticSummary();
        List<String> lines = output.asLines();
        Pattern pat = Pattern.compile(".*\\[trimnative\\] Periodic Trim \\(\\d+\\): (\\d+)([BKMG])->(\\d+)([BKMG]).*");
        int numTrimsFound = 0;
        long rssReductionTotal = 0;
        for (String line : lines) {
            Matcher mat = pat.matcher(line);
            if (mat.matches()) {
                long rss1 = Long.parseLong(mat.group(1)) * Unit.valueOf(mat.group(2)).size;
                long rss2 = Long.parseLong(mat.group(3)) * Unit.valueOf(mat.group(4)).size;
                if (rss1 > rss2) {
                    rssReductionTotal += (rss1 - rss2);
                }
                numTrimsFound ++;
            }
            if (numTrimsFound > maxTrimsExpected) {
                throw new RuntimeException("Abnormal high number of periodic trim attempts found (more than " + maxTrimsExpected +
                        "). Does the interval setting not work?");
            }
        }
        if (numTrimsFound < minTrimsExpected) {
            throw new RuntimeException("We found fewer (periodic) trim lines in UL log than expected (expected at least " + minTrimsExpected +
                    ", found " + numTrimsFound + ").");
        }
        System.out.println("Found " + numTrimsFound + " trims. Ok.");
        if (strict && maxTrimsExpected > 0) {
            // This is very fuzzy. Test program malloced X bytes, then freed them again and trimmed. But the log line prints change in RSS.
            // Which, of course, is influenced by a lot of other factors. But we expect to see *some* reasonable reduction in RSS
            // due to trimming.
            float fudge = 0.5f;
            // On ppc, we see a vastly diminished return (~3M reduction instead of ~200), I suspect because of the underlying
            // 64k pages lead to a different geometry. Manual tests with larger reclaim sizes show that autotrim works. For
            // this test, we just reduce the fudge factor.
            if (Platform.isPPC()) { // le and be both
                fudge = 0.01f;
            }
            long expectedMinimalReduction = (long) (totalAllocationsSize * fudge);
            if (rssReductionTotal < expectedMinimalReduction) {
                throw new RuntimeException("We did not see the expected RSS reduction in the UL log. Expected (with fudge)" +
                        " to see at least a combined reduction of " + expectedMinimalReduction + ".");
            } else {
                System.out.println("Found high enough RSS reduction from trims: " + rssReductionTotal);
            }
        }
    }

    static class Tester {
        public static void main(String[] args) throws Exception {
            long sleeptime = Long.parseLong(args[0]);

            System.out.println("Will spike now...");
            WhiteBox wb = WhiteBox.getWhiteBox();
            for (int i = 0; i < numAllocations; i++) {
                ptrs[i] = wb.NMTMalloc(szAllocations);
                wb.preTouchMemory(ptrs[i], szAllocations);
            }
            for (int i = 0; i < numAllocations; i++) {
                wb.NMTFree(ptrs[i]);
            }
            System.out.println("Done spiking.");

            System.out.println("GC...");
            System.gc();

            // give GC time to react
            System.out.println("Sleeping for " + sleeptime + " ms...");
            Thread.sleep(sleeptime);
            System.out.println("Done.");
        }
    }

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            throw new RuntimeException("Argument error");
        }

        boolean strictTesting = args[0].endsWith("Strict");

        switch (args[0]) {
            case "trimNative":
            case "trimNativeStrict": {
                long trimInterval = 500; // twice per second
                long ms1 = System.currentTimeMillis();
                OutputAnalyzer output = runTestWithOptions(
                        new String[] { "-XX:+UnlockExperimentalVMOptions", "-XX:TrimNativeHeapInterval=" + trimInterval },
                        new String[] { TestTrimNative.Tester.class.getName(), "5000" }
                );
                long ms2 = System.currentTimeMillis();
                long runtime_ms = ms2 - ms1;

                checkExpectedLogMessages(output, true, 500);

                long maxTrimsExpected = runtime_ms / trimInterval;
                long minTrimsExpected = maxTrimsExpected / 2;
                parseOutputAndLookForNegativeTrim(output, (int) minTrimsExpected, (int) maxTrimsExpected, strictTesting);
            } break;

            case "trimNativeHighInterval": {
                OutputAnalyzer output = runTestWithOptions(
                        new String[] { "-XX:+UnlockExperimentalVMOptions", "-XX:TrimNativeHeapInterval=" + Integer.MAX_VALUE },
                        new String[] { TestTrimNative.Tester.class.getName(), "5000" }
                );
                checkExpectedLogMessages(output, true, Integer.MAX_VALUE);
                // We should not see any trims since the interval would prevent them
                parseOutputAndLookForNegativeTrim(output, 0, 0, strictTesting);
            } break;

            case "trimNativeLowInterval":
            case "trimNativeLowIntervalStrict": {
                long ms1 = System.currentTimeMillis();
                OutputAnalyzer output = runTestWithOptions(
                        new String[] { "-XX:+UnlockExperimentalVMOptions", "-XX:TrimNativeHeapInterval=1" },
                        new String[] { TestTrimNative.Tester.class.getName(), "0" }
                );
                long ms2 = System.currentTimeMillis();
                int maxTrimsExpected = (int)(ms2 - ms1); // 1ms trim interval
                checkExpectedLogMessages(output, true, 1);
                parseOutputAndLookForNegativeTrim(output, 1, (int)maxTrimsExpected, strictTesting);
            } break;

            case "testOffOnNonCompliantPlatforms": {
                OutputAnalyzer output = runTestWithOptions(
                        new String[] { "-XX:+UnlockExperimentalVMOptions", "-XX:TrimNativeHeapInterval=1" },
                        new String[] { "-version" }
                );
                checkExpectedLogMessages(output, false, 0);
                parseOutputAndLookForNegativeTrim(output, 0, 0, strictTesting);
                // The following output is expected to be printed with warning level, so it should not need -Xlog
                output.shouldContain("[warning][trimnative] Native heap trim is not supported on this platform");
            } break;

            case "testOffExplicit": {
                OutputAnalyzer output = runTestWithOptions(
                        new String[] { "-XX:+UnlockExperimentalVMOptions", "-XX:TrimNativeHeapInterval=0" },
                        new String[] { "-version" }
                );
                checkExpectedLogMessages(output, false, 0);
                parseOutputAndLookForNegativeTrim(output, 0, 0, strictTesting);
            } break;

            case "testOffByDefault": {
                OutputAnalyzer output = runTestWithOptions(null, new String[] { "-version" } );
                checkExpectedLogMessages(output, false, 0);
                parseOutputAndLookForNegativeTrim(output, 0, 0, strictTesting);
            } break;

            default:
                throw new RuntimeException("Invalid test " + args[0]);

        }
    }
}
