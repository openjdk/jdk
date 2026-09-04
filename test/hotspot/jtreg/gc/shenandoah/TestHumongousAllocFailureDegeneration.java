/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=generational
 * @bug 8391591
 * @summary Collector degenerates on humongous allocation failure
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm TestHumongousAllocFailureDegeneration
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestHumongousAllocFailureDegeneration {
    static final int KB = 1024;
    static final int MB = 1024 * 1024;
    static final int REGIONS = 128;

    public static void runTests(List<String> args) throws Exception {
        final String degenStartMsg = "Starting GC (degenerated): Humongous Allocation Failure";
        final String degenCompleteMsg = "Completed GC (degenerated): Humongous Allocation Failure";
        final String fullStartMsg = "Starting GC (full): Humongous Allocation Failure";
        final String fullCompleteMsg = "Completed GC (full): Humongous Allocation Failure";
        final String degenTest = DegenTest.class.getName();
        final String fullTest = FullTest.class.getName();

        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(args);
        output.shouldHaveExitValue(0);
        output.shouldContain(degenStartMsg);
        output.shouldContain(degenCompleteMsg);

        if (args.get(args.size() - 1).equals(degenTest)) {
            // We should not have transitioned into a full GC.
            output.shouldNotContain(fullStartMsg);
        } else {
            // Consecutive degenerated cycles triggers a full GC.
            output.shouldContain(fullStartMsg);
            output.shouldContain(fullCompleteMsg);
        }
    }

    public static class DegenTest {
        public static void main(String[] args) throws Exception {
            final int regularAlloc = 512;
            final int humongousMin = 1;
            final int humongousMax = 8;
            final int humongousIterations = 8;
            final int regularIterations = 64;
            final int humongousPressureRegions = REGIONS - (regularIterations / 2);
            final int continueHumongousAllocation = 10;
            final Random r = Utils.getRandomInstance();

            for (int i = 0; i < 8; i++) {
                System.gc();
                List<Object> regular = new ArrayList<>();

                // Allocates ~ 32 MB
                for (int j = 0; j < regularIterations; j++) {
                    regular.add(new byte[KB * regularAlloc]);
                }

                int humongousRegions = 0;
                int retries = 0;
                while (humongousRegions < humongousPressureRegions || retries < continueHumongousAllocation) {
                    // We most likely hit humongous allocation failure here. The degenerated cycle should've
                    // reclaimed all unreachable humongous objects. It's safe to allocate more humongous objects
                    // because we have enough free regions to satisfy the humongous allocation. For example,
                    // continueHumongousAllocation * humongousMax < humongousPressureRegions.
                    if (humongousRegions >= humongousPressureRegions) {
                        retries += 1;
                    }

                    int humongousRandomAlloc = r.nextInt((humongousMax - humongousMin) + 1) + humongousMin;
                    byte[] humongous = new byte[MB * humongousRandomAlloc];
                    humongousRegions += humongousRandomAlloc;
                }
            }
        }
    }

    public static class FullTest {
        public static void main(String[] args) {
            // Allocate a humongous object that takes up a large portion
            // of the heap. There will be consecutive degenerated cycles
            // that will transition into full GCs.
            for (int i = 0; i < 32; i++) {
                byte[] humongous = new byte[MB * 96];
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ArrayList<String> flags = new ArrayList<>(List.of("-XX:+UnlockExperimentalVMOptions",
                                                          "-XX:+UseShenandoahGC",
                                                          "-XX:ShenandoahGCMode=generational",
                                                          "-Xmx128m",
                                                          "-Xms128m",
                                                          "-XX:ShenandoahRegionSize=1m",
                                                          "-XX:ShenandoahFullGCThreshold=3",
                                                          "-XX:ShenandoahGuaranteedGCInterval=0",
                                                          "-XX:+AlwaysPreTouch",
                                                          "-Xlog:gc+thread=debug",
                                                          "-XX:ConcGCThreads=1",
                                                          "-XX:ParallelGCThreads=1"));
        flags.add(DegenTest.class.getName());
        runTests(flags);
        flags.removeLast();

        flags.add(FullTest.class.getName());
        runTests(flags);
    }
}
