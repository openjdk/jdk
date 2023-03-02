/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package gc;

/*
 * All these tests test the trim-native feature for all GCs.
 * Trim-native is the ability to trim the C-heap as part of the GC cycle.
 * This feature is controlled by -XX:+TrimNativeHeap (by default off).
 * Trimming happens on full gc for all gcs. Shenandoah and G1 also support
 * concurrent trimming (Shenandoah supports this without any ties to java
 * heap occupancy).
 *
 */

//// full gc tests /////

/*
 * @test id=testExplicitTrimOnFullGC-serial
 * @summary Test that TrimNativeHeap works with Serial
 * @requires vm.gc.Serial
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testExplicitTrimOnFullGC serial
 */

/*
 * @test id=testExplicitTrimOnFullGC-parallel
 * @summary Test that TrimNativeHeap works with Parallel
 * @requires vm.gc.Parallel
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testExplicitTrimOnFullGC parallel
 */

/*
 * @test id=testExplicitTrimOnFullGC-shenandoah
 * @summary Test that TrimNativeHeap works with Shenandoah
 * @requires vm.gc.Shenandoah
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testExplicitTrimOnFullGC shenandoah
 */

/*
 * @test id=testExplicitTrimOnFullGC-g1
 * @summary Test that TrimNativeHeap works with G1
 * @requires vm.gc.G1
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testExplicitTrimOnFullGC g1
 */

/*
 * @test id=testExplicitTrimOnFullGC-z
 * @summary Test that TrimNativeHeap works with Z
 * @requires vm.gc.Z
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testExplicitTrimOnFullGC z
 */

//// auto mode tests /////

/*
 * @test id=testPeriodicTrim-serial
 * @summary Test that TrimNativeHeap works with Parallel
 * @requires vm.gc.Parallel
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testPeriodicTrim serial
 */

/*
 * @test id=testPeriodicTrim-parallel
 * @summary Test that TrimNativeHeap works with Parallel
 * @requires vm.gc.Parallel
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testPeriodicTrim parallel
 */

/*
 * @test id=testPeriodicTrim-shenandoah
 * @summary Test that TrimNativeHeap works with Shenandoah
 * @requires vm.gc.Shenandoah
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testPeriodicTrim shenandoah
 */

/*
 * @test id=testPeriodicTrim-g1
 * @summary Test that TrimNativeHeap works with G1
 * @requires vm.gc.G1
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testPeriodicTrim g1
 */

/*
 * @test id=testPeriodicTrim-z
 * @summary Test that TrimNativeHeap works with Z
 * @requires vm.gc.Z
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testPeriodicTrim z
 */

//// testPeriodicTrimDisabled test /////

/*
 * @test id=testPeriodicTrimDisabled-serial
 * @summary Test that TrimNativeHeap works with Parallel
 * @requires vm.gc.Parallel
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testPeriodicTrimDisabled serial
 */

/*
 * @test id=testPeriodicTrimDisabled-parallel
 * @summary Test that TrimNativeHeap works with Parallel
 * @requires vm.gc.Parallel
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testPeriodicTrimDisabled parallel
 */

/*
 * @test id=testPeriodicTrimDisabled-shenandoah
 * @summary Test that TrimNativeHeap works with Shenandoah
 * @requires vm.gc.Shenandoah
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testPeriodicTrimDisabled shenandoah
 */

/*
 * @test id=testPeriodicTrimDisabled-g1
 * @summary Test that TrimNativeHeap works with G1
 * @requires vm.gc.G1
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testPeriodicTrimDisabled g1
 */

/*
 * @test id=testPeriodicTrimDisabled-z
 * @summary Test that TrimNativeHeap works with Z
 * @requires vm.gc.Z
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testPeriodicTrimDisabled z
 */

// Other tests

/*
 * @test id=testOffByDefault
 * @summary Test that -GCTrimNative disables the feature
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testOffByDefault
 */

/*
 * @test id=testOffExplicit
 * @summary Test that GCTrimNative is off by default
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testOffExplicit
 */

/*
 * @test id=testOffOnNonCompliantPlatforms
 * @summary Test that GCTrimNative is off on unsupportive platforms
 * @requires (os.family!="linux") | vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative testOffOnNonCompliantPlatforms
 */

import jdk.internal.misc.Unsafe;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestTrimNative {

    // Actual RSS increase is a lot larger than 4 MB. Depends on glibc overhead, and NMT malloc headers in debug VMs.
    // We need small-grained allocations to make sure they actually increase RSS (all touched) and to see the
    // glibc-retaining-memory effect.
    static final int szAllocations = 16;
    static final int totalAllocationsSize = 16 * 1024 * 1024; // 16 MB total
    static final int numAllocations = totalAllocationsSize / szAllocations;

    static long[] ptrs = new long[numAllocations];

    enum Unit {
        B(1), K(1024), M(1024*1024), G(1024*1024*1024);
        public final long size;
        Unit(long size) { this.size = size; }
    }

    enum GC {
        serial, parallel, g1, shenandoah, z;
        String getSwitchName() {
            String s = name();
            return "-XX:+Use" + s.substring(0, 1).toUpperCase() + s.substring(1) + "GC";
        }
        boolean isZ() { return this == GC.z; }
        boolean isSerial() { return this == GC.serial; }
        boolean isParallel() { return this == GC.parallel; }
        boolean isG1() { return this == GC.g1; }
        boolean isShenandoah() { return this == GC.shenandoah; }
    }

    private static OutputAnalyzer runTestWithOptions(String[] extraOptions, String[] testArgs) throws IOException {

        List<String> allOptions = new ArrayList<String>();
        allOptions.add("-XX:+UnlockExperimentalVMOptions");
        allOptions.addAll(Arrays.asList(extraOptions));
        allOptions.add("-Xmx128m");
        allOptions.add("-Xms128m"); // Stabilize RSS
        allOptions.add("-XX:+AlwaysPreTouch"); // Stabilize RSS
        allOptions.add("-Xlog:gc+trim=debug");
        allOptions.add("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED");

        allOptions.add(TestTrimNative.class.getName());
        allOptions.add("RUN");
        allOptions.addAll(Arrays.asList(testArgs));
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(allOptions.toArray(new String[0]));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        return output;

    }

    private static void checkExpectedLogMessages(OutputAnalyzer output, boolean expectEnabled,
                                                 int expectedInterval, boolean expectAutoStepDown) {
        if (expectEnabled) {
            output.shouldContain("Native trim enabled");
            if (expectedInterval > 0) {
                output.shouldContain("Periodic native trim enabled (interval: " + expectedInterval +
                        " seconds, dynamic step-down " + (expectAutoStepDown ? "enabled" : "disabled") + ")");
                output.shouldContain("NativeTrimmer start");
                output.shouldContain("NativeTrimmer stop");
            } else {
                output.shouldContain("Periodic native trim disabled");
            }

        } else {
            output.shouldNotContain("Native trim");
        }
    }

    /**
     * Given JVM output, look for a log line that describes a successful negative trim. The total amount of trims
     * should be matching about what the test program allocated.
     * like this:
     * "[2.053s][debug][gc,trim] Trim native heap (retain size: 5120K): RSS+Swap: 271M->223M (-49112K), 2.834ms"
     * (Note: we use the "properXXX" print routines, therefore units can differ)
     * Check that the sum of all trim log lines comes to a total RSS reduction in the MB range
     * @param output
     * @param minPeriodicTrimsExpected min number of periodic trim lines expected in UL log
     * @param maxPeriodicTrimsExpected min number of periodic trim lines expected in UL log
     * @param minExplicitTrimsExpected min number of explicit trim lines expected in UL log
     * @param maxExplicitTrimsExpected min number of explicit trim lines expected in UL log
     */
    private static void parseOutputAndLookForNegativeTrim(OutputAnalyzer output, int minPeriodicTrimsExpected,
                                                          int maxPeriodicTrimsExpected, int minExplicitTrimsExpected,
                                                          int maxExplicitTrimsExpected) {
        output.reportDiagnosticSummary();
        List<String> lines = output.asLines();
        Pattern pat = Pattern.compile(".*\\[gc,trim\\] Trim native heap \\((explicit|periodic)\\)" +
                ".*RSS\\+Swap: (\\d+)([BKMG])->(\\d+)([BKMG]).*");
        int numExplicitTrimsFound = 0;
        int numPeriodicTrimsFound = 0;
        long rssReductionTotal = 0;
        for (String line : lines) {
            Matcher mat = pat.matcher(line);
            if (mat.matches()) {
                String explicitOrPeriodic = mat.group(1);
                boolean periodic = false;
                switch (explicitOrPeriodic) {
                    case "explicit": periodic = false; break;
                    case "periodic": periodic = true; break;
                    default: throw new RuntimeException("Invalid line \"" + line + "\"");
                }
                long rss1 = Long.parseLong(mat.group(2)) * Unit.valueOf(mat.group(3)).size;
                long rss2 = Long.parseLong(mat.group(4)) * Unit.valueOf(mat.group(5)).size;
                System.out.println("Parsed Trim Line. Periodic: " + periodic + ", rss1: " + rss1 + " rss2: " + rss2);
                if (rss1 > rss2) {
                    rssReductionTotal += (rss1 - rss2);
                }
                if (periodic) {
                    numPeriodicTrimsFound ++;
                } else {
                    numExplicitTrimsFound ++;
                }
            }
            if (numPeriodicTrimsFound > maxPeriodicTrimsExpected) {
                throw new RuntimeException("Abnormal high number of periodic trim attempts found (more than " + maxPeriodicTrimsExpected +
                        "). Does the interval setting not work?");
            }
            if (numExplicitTrimsFound > maxExplicitTrimsExpected) {
                throw new RuntimeException("Abnormal high number of explicit trim attempts found (more than " + maxExplicitTrimsExpected +
                        "). Does the interval setting not work?");
            }
        }
        if (numPeriodicTrimsFound < minPeriodicTrimsExpected) {
            throw new RuntimeException("We found fewer (periodic) trim lines in UL log than expected (expected at least " + minPeriodicTrimsExpected +
                    ", found " + numPeriodicTrimsFound + ").");
        }
        if (numExplicitTrimsFound < minExplicitTrimsExpected) {
            throw new RuntimeException("We found fewer (explicit) trim lines in UL log than expected (expected at least " + minExplicitTrimsExpected +
                    ", found " + numExplicitTrimsFound + ").");
        }
        // This is very fuzzy. Test program malloced X bytes, then freed them again and trimmed. But the log line prints change in RSS.
        // Which, of course, is influenced by a lot of other factors. But we expect to see *some* reasonable reduction in RSS
        // due to trimming.
        float fudge = 0.7f;
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
        }
    }

    final static int DEFAULT_TRIM_INTERVAL = 30;
    final static boolean DEFAULT_AUTOSTEP = false;

    // Test explicit trim on full gc
    static private final void testExplicitTrimOnFullGC(GC gc) throws IOException {
        System.out.println("testExplicitTrimOnFullGC");
        int sleeptime_secs = 2;
        OutputAnalyzer output = runTestWithOptions (
                new String[] { gc.getSwitchName(), "-XX:+TrimNativeHeap" },
                new String[] { "true" /* full gc */, String.valueOf(sleeptime_secs * 1000) /* ms after peak */ }
        );

        checkExpectedLogMessages(output, true, DEFAULT_TRIM_INTERVAL, DEFAULT_AUTOSTEP);

        // We expect to see at least one pause (because we pause during STW gc cycles) followed by an reqested immediate trim
        output.shouldContain("NativeTrimmer pause");
        output.shouldContain("NativeTrimmer unpause + request explicit trim");

        int minPeriodicTrimsExpected = 0;
        int maxPeriodicTrimsExpected = 10;
        int minExplicitTrimsExpected = 1;
        int maxExplicitTrimsExpected = 10;

        parseOutputAndLookForNegativeTrim(output,
                0, /*  minPeriodicTrimsExpected */
                10,  /*  maxPeriodicTrimsExpected */
                1, /*  minExplicitTrimsExpected */
                10 /*  maxExplicitTrimsExpected */
        );
    }

    // Test periodic trim with very short trim interval. We explicitly don't do a GC to not get an explicite trim
    // "stealing" our gains.
    static private final void testPeriodicTrim(GC gc) throws IOException {
        System.out.println("testPeriodicTrim");
        long t1 = System.currentTimeMillis();
        int sleeptime_secs = 4;
        OutputAnalyzer output = runTestWithOptions (
                new String[] { gc.getSwitchName(), "-XX:+TrimNativeHeap", "-XX:TrimNativeHeapInterval=1" },
                new String[] { "false" /* no full gc */, String.valueOf(sleeptime_secs * 1000) /* ms after peak */ }
        );
        long t2 = System.currentTimeMillis();
        int runtime_s = (int)((t2 - t1) / 1000);

        checkExpectedLogMessages(output, true, 1, DEFAULT_AUTOSTEP);

        // With an interval time of 1 second and a runtime of 6..x seconds we expect to see x periodic trim
        // log lines (+- fudge factor).
        parseOutputAndLookForNegativeTrim(output,
                runtime_s - 4, /*  minPeriodicTrimsExpected */
                runtime_s,  /*  maxPeriodicTrimsExpected */
                0, /*  minExplicitTrimsExpected */
                10 /*  maxExplicitTrimsExpected */
        );

    }

    static private final void testPeriodicTrimDisabled(GC gc) throws IOException {
        System.out.println("testPeriodicTrimDisabled");
        OutputAnalyzer output = runTestWithOptions (
                new String[] { gc.getSwitchName(), "-XX:+TrimNativeHeap", "-XX:TrimNativeHeapInterval=0" },
                new String[] { "true" /* full gc */, "4000" /* ms after peak */ }
        );
        checkExpectedLogMessages(output, true, 0, DEFAULT_AUTOSTEP);

        // We expect only explicit trims, no periodic trims
        parseOutputAndLookForNegativeTrim(output,
                0, /*  minPeriodicTrimsExpected */
                0,  /*  maxPeriodicTrimsExpected */
                1, /*  minExplicitTrimsExpected */
                10 /*  maxExplicitTrimsExpected */
        );
    }

    // Test that trim-native gets disabled on platforms that don't support it.
    static private final void testOffOnNonCompliantPlatforms() throws IOException {
        if (Platform.isLinux() && !Platform.isMusl()) {
            throw new RemoteException("Don't call me for Linux glibc");
        }
        // Logic is shared, so no need to test with every GC. Just use the default GC.
        System.out.println("testOffOnNonCompliantPlatforms");
        OutputAnalyzer output = runTestWithOptions (
                new String[] { "-XX:+TrimNativeHeap" },
                new String[] { "true" /* full gc */, "2000" /* ms after peak */ }
        );
        checkExpectedLogMessages(output, false, 0, false);
    }

    // Test trim native is disabled if explicitly switched off
    static private final void testOffExplicit() throws IOException {
        // Logic is shared, so no need to test with every GC. Just use the default GC.
        System.out.println("testOffExplicit");
        OutputAnalyzer output = runTestWithOptions (
                new String[] { "-XX:-TrimNativeHeap" },
                new String[] { "true" /* full gc */, "2000" /* ms after peak */ }
        );
        checkExpectedLogMessages(output, false, 0, false);
    }

    // Test that trim-native is disabled by default
    static private final void testOffByDefault() throws IOException {
        // Logic is shared, so no need to test with every GC. Just use the default GC.
        System.out.println("testOffByDefault");
        OutputAnalyzer output = runTestWithOptions (
                new String[] { },
                new String[] { "true" /* full gc */, "2000" /* ms after peak */ }
        );
        checkExpectedLogMessages(output, false, 0, false);
    }

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            throw new RuntimeException("Argument error");
        }

        if (args[0].equals("RUN")) {
            boolean doFullGC = Boolean.parseBoolean(args[1]);

            System.out.println("Will spike now...");
            for (int i = 0; i < numAllocations; i++) {
                ptrs[i] = Unsafe.getUnsafe().allocateMemory(szAllocations);
                Unsafe.getUnsafe().putByte(ptrs[i], (byte)0);
                Unsafe.getUnsafe().putByte(ptrs[i] + szAllocations / 2, (byte)0);
            }
            for (int i = 0; i < numAllocations; i++) {
                Unsafe.getUnsafe().freeMemory(ptrs[i]);
            }
            System.out.println("Done spiking.");

            if (doFullGC) {
                System.out.println("GC...");
                System.gc();
            }

            // give GC time to react
            int time = Integer.parseInt(args[2]);
            System.out.println("Sleeping...");
            Thread.sleep(time);
            System.out.println("Done.");

            return;

        } else if (args[0].equals("testExplicitTrimOnFullGC")) {
            final GC gc = GC.valueOf(args[1]);
            testExplicitTrimOnFullGC(gc);
        } else if (args[0].equals("testPeriodicTrim")) {
            final GC gc = GC.valueOf(args[1]);
            testPeriodicTrim(gc);
        } else if (args[0].equals("testPeriodicTrimDisabled")) {
            final GC gc = GC.valueOf(args[1]);
            testPeriodicTrimDisabled(gc);
        } else if (args[0].equals("testOffOnNonCompliantPlatforms")) {
            testOffOnNonCompliantPlatforms();
        } else if (args[0].equals("testOffExplicit")) {
            testOffExplicit();
        } else if (args[0].equals("testOffByDefault")) {
            testOffByDefault();
        } else {
            throw new RuntimeException("Invalid test " + args[0]);
        }

    }

}
