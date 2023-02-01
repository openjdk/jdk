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
 * This feature is controlled by -XX:+GCTrimNativeHeap (by default off).
 * Trimming happens on full gc for all gcs. Shenandoah and G1 also support
 * concurrent trimming (Shenandoah supports this without any ties to java
 * heap occupancy).
 *
 */

//// full gc tests /////

/*
 * @test id=fullgc-serial
 * @summary Test that GCTrimNativeHeap works with Serial
 * @requires vm.gc.Serial
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-fullgc serial
 */

/*
 * @test id=fullgc-parallel
 * @summary Test that GCTrimNativeHeap works with Parallel
 * @requires vm.gc.Parallel
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-fullgc parallel
 */

/*
 * @test id=fullgc-shenandoah
 * @summary Test that GCTrimNativeHeap works with Shenandoah
 * @requires vm.gc.Shenandoah
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-fullgc shenandoah
 */

/*
 * @test id=fullgc-g1
 * @summary Test that GCTrimNativeHeap works with G1
 * @requires vm.gc.G1
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-fullgc g1
 */

/*
 * @test id=fullgc-z
 * @summary Test that GCTrimNativeHeap works with Z
 * @requires vm.gc.Z
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-fullgc z
 */

//// auto mode tests /////

// Note: not serial, since it does not do periodic trimming, only trimming on full gc

/*
 * @test id=auto-parallel
 * @summary Test that GCTrimNativeHeap works with Parallel
 * @requires vm.gc.Parallel
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-auto parallel
 */

/*
 * @test id=auto-shenandoah
 * @summary Test that GCTrimNativeHeap works with Shenandoah
 * @requires vm.gc.Shenandoah
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-auto shenandoah
 */

/*
 * @test id=auto-g1
 * @summary Test that GCTrimNativeHeap works with G1
 * @requires vm.gc.G1
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-auto g1
 */

/*
 * @test id=auto-z
 * @summary Test that GCTrimNativeHeap works with Z
 * @requires vm.gc.Z
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-auto z
 */

//// test-auto-high-interval interval test /////

// Note: not serial, since it does not do periodic trimming, only trimming on full gc

/*
 * @test id=auto-high-interval-parallel
 * @summary Test that a high GCTrimNativeHeapInterval effectively disables automatic trimming
 * @requires vm.gc.Parallel
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-auto-high-interval parallel
 */

/*
 * @test id=auto-high-interval-g1
 * @summary Test that a high GCTrimNativeHeapInterval effectively disables automatic trimming
 * @requires vm.gc.G1
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-auto-high-interval g1
 */

/*
 * @test id=auto-high-interval-shenandoah
 * @summary Test that a high GCTrimNativeHeapInterval effectively disables automatic trimming
 * @requires vm.gc.Shenandoah
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-auto-high-interval shenandoah
 */

/*
 * @test id=auto-high-interval-z
 * @summary Test that a high GCTrimNativeHeapInterval effectively disables automatic trimming
 * @requires vm.gc.Z
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-auto-high-interval z
 */

//// test-auto-interval-0 test /////

// Note: not serial, since it does not do periodic trimming, only trimming on full gc

/*
 * @test id=auto-zero-interval-parallel
 * @summary Test that a GCTrimNativeHeapInterval=0 disables periodic trimming
 * @requires vm.gc.Parallel
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-auto-zero-interval parallel
 */

/*
 * @test id=auto-zero-interval-g1
 * @summary Test that a GCTrimNativeHeapInterval=0 disables periodic trimming
 * @requires vm.gc.G1
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-auto-zero-interval g1
 */

/*
 * @test id=auto-zero-interval-shenandoah
 * @summary Test that a GCTrimNativeHeapInterval=0 disables periodic trimming
 * @requires vm.gc.Shenandoah
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-auto-zero-interval shenandoah
 */

/*
 * @test id=auto-zero-interval-z
 * @summary Test that a GCTrimNativeHeapInterval=0 disables periodic trimming
 * @requires vm.gc.Z
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-auto-zero-interval z
 */

// Other tests

/*
 * @test id=off-explicit
 * @summary Test that -GCTrimNative disables the feature
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-off-explicit
 */

/*
 * @test id=off-by-default
 * @summary Test that GCTrimNative is off by default
 * @requires (os.family=="linux") & !vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-off-by-default
 */

/*
 * @test id=off-on-other-platforms
 * @summary Test that GCTrimNative is off on unsupportive platforms
 * @requires (os.family!="linux") | vm.musl
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver gc.TestTrimNative test-off-on-other-platforms
 */

import jdk.internal.misc.Unsafe;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
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

    static private final OutputAnalyzer runTestWithOptions(String[] extraOptions, String[] testArgs) throws IOException {

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

    /**
     * Given JVM output, look for a log line that describes a successful negative trim in the megabyte range
     * like this:
     * "[2.053s][debug][gc,trim] Trim native heap (retain size: 5120K): RSS+Swap: 271M->223M (-49112K), 2.834ms"
     * (Note: we use the "properXXX" print routines, therefore units can differ)
     * Check that the sum of all trim log lines comes to a total RSS reduction in the MB range
     * @param output
     * @param minExpected min number of trim lines expected in UL log
     * @param maxExpected max number of trim lines expected in UL log
     */
    private final static void parseOutputAndLookForNegativeTrim(OutputAnalyzer output, int minExpected, int maxExpected) {
        output.reportDiagnosticSummary();
        List<String> lines = output.asLines();
        Pattern pat = Pattern.compile(".*\\[gc,trim\\] Trim native heap.*RSS\\+Swap: (\\d+)([KMB])->(\\d+)([KMB]).*");
        int numTrimsFound = 0;
        long rssReductionTotal = 0;
        for (String line : lines) {
            Matcher mat = pat.matcher(line);
            if (mat.matches()) {
                long rss1 = Long.parseLong(mat.group(1)) * Unit.valueOf(mat.group(2)).size;
                long rss2 = Long.parseLong(mat.group(3)) * Unit.valueOf(mat.group(4)).size;
                System.out.println("Parsed Trim Line. rss1: " + rss1 + " rss2: " + rss2);
                if (rss1 > rss2) {
                    rssReductionTotal += (rss1 - rss2);
                }
                numTrimsFound ++;
            }
            if (numTrimsFound > maxExpected) {
                throw new RuntimeException("Abnormal high number of trim attempts found (more than " + maxExpected +
                        "). Does the interval setting not work?");
            }
        }
        if (numTrimsFound < minExpected) {
            throw new RuntimeException("We found fewer trim lines in UL log than expected (expected " + minExpected +
                    ", found " + numTrimsFound + ".");
        }
        // This is very fuzzy. We malloced X, free'd X, trimmed, measured the combined effect of all reductions.
        // This does not take into effect mallocs or frees that may happen concurrently. But we expect to see *some*
        // reduction somewhere. Test with a fudge factor.
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

    // Test that we trim on full gc
    static private final void testWithFullGC(GC gc) throws IOException {
        System.out.println("testWithFullGC");
        int sleeptime_secs = 2;
        OutputAnalyzer output = runTestWithOptions (
                new String[] { gc.getSwitchName(), "-XX:+GCTrimNativeHeap" },
                new String[] { "true" /* full gc */, String.valueOf(sleeptime_secs * 1000) /* ms after peak */ }
        );
        output.shouldContain("Native trim enabled.");
        output.shouldContain("Periodic native trim enabled (interval: 30 seconds, step-down-interval: 120 seconds).");
        output.shouldContain("NativeTrimmer start");
        output.shouldContain("NativeTrimmer pause");
        output.shouldContain("NativeTrimmer unpause+trim");
        output.shouldContain("NativeTrimmer stop");
        // With default interval time of 30 seconds, auto trimming should never kick in, so the only
        // log line we expect to see is the one from the full-gc induced trim.
        parseOutputAndLookForNegativeTrim(output, 1, 1);
    }

    // Test that GCTrimNativeHeap=1 causes a trim-native automatically, without GC (for now, shenandoah only)
    static private final void testAuto(GC gc) throws IOException {
        System.out.println("testAuto");
        long t1 = System.currentTimeMillis();
        int sleeptime_secs = 4;
        OutputAnalyzer output = runTestWithOptions (
                new String[] { gc.getSwitchName(), "-XX:+GCTrimNativeHeap", "-XX:GCTrimNativeHeapInterval=1" },
                new String[] { "false" /* full gc */, String.valueOf(sleeptime_secs * 1000) /* ms after peak */ }
        );
        long t2 = System.currentTimeMillis();
        int runtime_s = (int)((t2 - t1) / 1000);
        output.shouldContain("Native trim enabled.");
        output.shouldContain("Periodic native trim enabled (interval: 1 seconds, step-down-interval: 4 seconds).");
        // With an interval time of 1 second and a runtime of 6..x seconds we expect to see x log lines (+- fudge factor).
        parseOutputAndLookForNegativeTrim(output, runtime_s - 4, runtime_s);
    }

    // Test that trim-native correctly honors interval
    static private final void testAutoWithHighInterval(GC gc) throws IOException {
        // We pass a high interval than the expected test runtime. We expect no trims.
        System.out.println("testAutoWithHighInterval");
        OutputAnalyzer output = runTestWithOptions (
                new String[] { gc.getSwitchName(), "-XX:+GCTrimNativeHeap", "-XX:GCTrimNativeHeapInterval=12" },
                new String[] { "false" /* full gc */, "6000" /* ms after peak */ }
        );
        output.shouldContain("Native trim enabled.");
        output.shouldContain("Periodic native trim enabled (interval: 12 seconds, step-down-interval: 48 seconds).");
        output.shouldNotContain("Trim native heap");
    }

    static private final void testAutoWithZeroInterval(GC gc) throws IOException {
        System.out.println("testAutoWithZeroInterval");
        OutputAnalyzer output = runTestWithOptions (
                new String[] { gc.getSwitchName(), "-XX:+GCTrimNativeHeap", "-XX:GCTrimNativeHeapInterval=0" },
                new String[] { "false" /* full gc */, "4000" /* ms after peak */ }
        );
        output.shouldContain("Native trim enabled.");
        output.shouldContain("Periodic native trim disabled");
        output.shouldNotContain("Trim native heap");
    }

    // Test that trim-native gets disabled on platforms that don't support it.
    static private final void testOffOnNonCompliantPlatforms() throws IOException {
        // Logic is shared, so no need to test with every GC. Just use the default GC.
        System.out.println("testOffOnNonCompliantPlatforms");
        OutputAnalyzer output = runTestWithOptions (
                new String[] { "-XX:+GCTrimNativeHeap" },
                new String[] { "true" /* full gc */, "2000" /* ms after peak */ }
        );
        output.shouldContain("GCTrimNativeHeap disabled");
        output.shouldNotContain("Native trim enabled.");
        output.shouldNotContain("Trim native heap");
    }

    // Test that GCTrimNativeHeap=0 switches trim-native off
    static private final void testOffExplicit() throws IOException {
        // Logic is shared, so no need to test with every GC. Just use the default GC.
        System.out.println("testOffExplicit");
        OutputAnalyzer output = runTestWithOptions (
                new String[] { "-XX:-GCTrimNativeHeap" },
                new String[] { "true" /* full gc */, "2000" /* ms after peak */ }
        );
        output.shouldNotContain("Native trim enabled.");
        output.shouldNotContain("Trim native heap");
    }

    // Test that trim-native is disabled by default
    static private final void testOffByDefault() throws IOException {
        // Logic is shared, so no need to test with every GC. Just use the default GC.
        System.out.println("testOffByDefault");
        OutputAnalyzer output = runTestWithOptions (
                new String[] { },
                new String[] { "true" /* full gc */, "2000" /* ms after peak */ }
        );
        output.shouldNotContain("Native trim enabled.");
        output.shouldNotContain("Trim native heap");
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

        } else if (args[0].equals("test-fullgc")) {
            final GC gc = GC.valueOf(args[1]);
            testWithFullGC(gc);
        } else if (args[0].equals("test-auto")) {
            final GC gc = GC.valueOf(args[1]);
            testAuto(gc);
        } else if (args[0].equals("test-auto-high-interval")) {
            final GC gc = GC.valueOf(args[1]);
            testAutoWithHighInterval(gc);
        } else if (args[0].equals("test-auto-zero-interval")) {
            final GC gc = GC.valueOf(args[1]);
            testAutoWithZeroInterval(gc);
        } else if (args[0].equals("test-off-explicit")) {
            testOffExplicit();
        } else if (args[0].equals("test-off-by-default")) {
            testOffByDefault();
        } else if (args[0].equals("test-off-on-other-platforms")) {
            testOffOnNonCompliantPlatforms();
        } else {
            throw new RuntimeException("Invalid test " + args[0]);
        }

    }

}
