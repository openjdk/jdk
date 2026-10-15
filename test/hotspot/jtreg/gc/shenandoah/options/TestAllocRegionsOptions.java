/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @test
 * @summary Verify alloc-region option validation (power-of-2 rejection) and effective count clamping
 * @bug 8361099
 * @requires vm.gc.Shenandoah
 * @requires os.maxMemory > 4G
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestAllocRegionsOptions
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestAllocRegionsOptions {
    public static void main(String[] args) throws Exception {
        testPowerOfTwoRejection();
        testPowerOfTwoAcceptance();
        testEffectiveCounts();
    }

    private static void testPowerOfTwoRejection() throws Exception {
        // Non-power-of-2 values must be rejected for ShenandoahMutatorAllocRegions
        for (int bad : new int[]{3, 5, 6, 7, 9, 10, 12, 15, 17, 24, 31}) {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                    "-Xmx128m",
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseShenandoahGC",
                    "-XX:ShenandoahMutatorAllocRegions=" + bad,
                    "-version");
            output.shouldContain("ShenandoahMutatorAllocRegions to be a power of 2");
            output.shouldNotHaveExitValue(0);
        }

        // Non-power-of-2 values must be rejected for ShenandoahCollectorAllocRegions
        for (int bad : new int[]{3, 5, 6, 7, 9, 10, 12, 15, 17, 24, 31}) {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                    "-Xmx128m",
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseShenandoahGC",
                    "-XX:ShenandoahCollectorAllocRegions=" + bad,
                    "-version");
            output.shouldContain("ShenandoahCollectorAllocRegions to be a power of 2");
            output.shouldNotHaveExitValue(0);
        }
    }

    private static void testPowerOfTwoAcceptance() throws Exception {
        // Valid power-of-2 values must be accepted
        for (int good : new int[]{0, 1, 2, 4, 8, 16, 32}) {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                    "-Xmx256m",
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseShenandoahGC",
                    "-XX:ShenandoahMutatorAllocRegions=" + good,
                    "-version");
            output.shouldHaveExitValue(0);
        }
        for (int good : new int[]{0, 1, 2, 4, 8, 16, 32}) {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                    "-Xmx256m",
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseShenandoahGC",
                    "-XX:ShenandoahCollectorAllocRegions=" + good,
                    "-version");
            output.shouldHaveExitValue(0);
        }
    }

    private static void testEffectiveCounts() throws Exception {
        // With -Xmx2g -XX:ShenandoahRegionSize=256K: region_count=8192
        //   mutator heap_bound = round_power_of_2(8192/256) = 32
        //   collector heap_bound = round_power_of_2(8192/512) = 16
        assertEffectiveCount(32, 16,
                "-Xmx2g", "-XX:ShenandoahRegionSize=256K",
                "-XX:ShenandoahMutatorAllocRegions=32",
                "-XX:ShenandoahCollectorAllocRegions=16");

        // Explicit override of 32 clamped by heap_bound on small heap:
        // With -Xmx256m: region_count=1024, mutator heap_bound=round_power_of_2(1024/256)=4
        // So requesting 32 yields 4.
        assertEffectiveCount(4, 2,
                "-Xmx256m",
                "-XX:ShenandoahMutatorAllocRegions=32",
                "-XX:ShenandoahCollectorAllocRegions=32");

        // With -Xmx4g -XX:ShenandoahRegionSize=256K: region_count=16384
        //   mutator heap_bound = round_power_of_2(16384/256) = 64 -> clamped to MAX=32
        //   collector heap_bound = round_power_of_2(16384/512) = 32
        // Both partitions should achieve the full 32 slots.
        assertEffectiveCount(32, 32,
                "-Xmx4g", "-XX:ShenandoahRegionSize=256K",
                "-XX:ShenandoahMutatorAllocRegions=32",
                "-XX:ShenandoahCollectorAllocRegions=32");

        // Default (auto) on a small heap: CPU-bound but clamped by heap_bound.
        // -Xmx256m: region_count=1024, mutator heap_bound=4, collector heap_bound=2
        assertEffectiveCountAtMost(4, 2,
                "-Xmx256m");

        // Aggressive heuristics exercises degenerated GC with active CAS alloc regions.
        assertEffectiveCount(32, 16,
                "-Xmx2g", "-XX:ShenandoahRegionSize=256K",
                "-XX:ShenandoahMutatorAllocRegions=32",
                "-XX:ShenandoahCollectorAllocRegions=16",
                "-XX:ShenandoahGCHeuristics=aggressive");
    }

    private static void assertEffectiveCount(int expectedMutator, int expectedCollector,
                                             String... extraFlags) throws Exception {
        String[] baseFlags = {
            "-XX:+UseShenandoahGC",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+UnlockExperimentalVMOptions",
            "-Xlog:gc+init",
        };
        String[] allFlags = new String[baseFlags.length + extraFlags.length + 1];
        System.arraycopy(baseFlags, 0, allFlags, 0, baseFlags.length);
        System.arraycopy(extraFlags, 0, allFlags, baseFlags.length, extraFlags.length);
        allFlags[allFlags.length - 1] = "-version";

        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(allFlags);
        output.shouldHaveExitValue(0);
        output.shouldContain("CAS Alloc Regions: mutator=" + expectedMutator
                             + ", collector=" + expectedCollector);
    }

    private static void assertEffectiveCountAtMost(int maxMutator, int maxCollector,
                                                   String... extraFlags) throws Exception {
        String[] baseFlags = {
            "-XX:+UseShenandoahGC",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+UnlockExperimentalVMOptions",
            "-Xlog:gc+init",
        };
        String[] allFlags = new String[baseFlags.length + extraFlags.length + 1];
        System.arraycopy(baseFlags, 0, allFlags, 0, baseFlags.length);
        System.arraycopy(extraFlags, 0, allFlags, baseFlags.length, extraFlags.length);
        allFlags[allFlags.length - 1] = "-version";

        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(allFlags);
        output.shouldHaveExitValue(0);
        String combined = output.getStdout() + output.getStderr();
        Matcher m = Pattern.compile("CAS Alloc Regions: mutator=(\\d+), collector=(\\d+)").matcher(combined);
        if (!m.find()) {
            throw new RuntimeException("CAS Alloc Regions log line not found in output:\n" + combined);
        }
        int actualMutator = Integer.parseInt(m.group(1));
        int actualCollector = Integer.parseInt(m.group(2));
        if (actualMutator > maxMutator) {
            throw new RuntimeException("Mutator alloc regions " + actualMutator
                                       + " exceeds expected max " + maxMutator);
        }
        if (actualCollector > maxCollector) {
            throw new RuntimeException("Collector alloc regions " + actualCollector
                                       + " exceeds expected max " + maxCollector);
        }
    }
}
