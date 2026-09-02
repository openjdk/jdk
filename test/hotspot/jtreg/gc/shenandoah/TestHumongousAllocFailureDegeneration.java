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
 * @summary Collector degenerates on humongous allocation failure
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 * @run main/othervm TestHumongousAllocFailureDegeneration
 */
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestHumongousAllocFailureDegeneration {
    static final int KB = 1024;
    static final int MB = 1024 * 1024;
    static final long HEAP_SIZE = MB * 128;
    static final long TARGET = HEAP_SIZE - (HEAP_SIZE / 10);

    public static void runTests(List<String> args) throws Exception {
        final String degenStartMsg = "Starting GC (degenerated): Humongous Allocation Failure";
        final String degenCompletedMsg = "Completed GC (degenerated): Humongous Allocation Failure";
        final String fullMsg = "Pause Full";
        final String degenTest = DegenTest.class.getName();
        final String fullTest = FullTest.class.getName();

        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(args);
        output.shouldHaveExitValue(0);
        output.shouldContain(degenStartMsg);
        output.shouldContain(degenCompletedMsg);

        if (args.get(args.size() - 1).equals(degenTest)) {
            // No consecutive degenerated cycles
             output.shouldNotContain(fullMsg);
        } else {
            // Consecutive degenerated cycles triggers a full GC.
            output.shouldContain(fullMsg);
        }
    }

    public static class DegenTest {
        public static void main(String[] args) {
            long estimated_allocated = 0;
            for (int i = 0; i < 128; i++) {
                // Allocate enough bytes to the point where the heap is ~90% full.
                // This gives us a good chance that we run into humongous allocation failure.
                // These small allocations shouldn't be enough where we run back-to-back
                // degenerated cycles.
                while (estimated_allocated <= TARGET) {
                    byte[] regular = new byte[KB * 256];
                    byte[] humongous = new byte[MB * 4];
                    estimated_allocated += (KB * 256) + (MB * 4);
                }
                estimated_allocated = 0;
            }
        }
    }

    public static class FullTest {
        public static void main(String[] args) {
            // Allocate a humongous object that takes up half the heap.
            // There will be consecutive degenerated cycles that will
            // transition into full GCs.
            for (int i = 0; i < 32; i++) {
                byte[] obj1 = new byte[MB * 64];
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
                                                            "-Xlog:gc,gc+thread=debug",
                                                            "-XX:ConcGCThreads=1", 
                                                            "-XX:ParallelGCThreads=1"));

        flags.add(DegenTest.class.getName());
        runTests(flags);
        flags.removeLast();

        flags.add(FullTest.class.getName());
        runTests(flags);
    }
}
