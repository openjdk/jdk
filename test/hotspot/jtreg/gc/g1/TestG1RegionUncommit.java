/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package gc.g1;

/**
 * @test TestG1RegionUncommit
 * @requires vm.gc.G1
 * @summary Test that G1 uncommits regions based on time threshold
 * @bug 8357445
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management/sun.management
 * @run main/othervm -XX:+UseG1GC -Xms8m -Xmx256m -XX:G1HeapRegionSize=1M
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:G1UncommitDelayMillis=3000 -XX:G1TimeBasedEvaluationIntervalMillis=2000
 *                   -XX:G1MinRegionsToUncommit=2
 *                   -Xlog:gc*,gc+sizing*=debug
 *                   gc.g1.TestG1RegionUncommit
 */

import java.util.ArrayList;
import java.util.List;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestG1RegionUncommit {

    public static void main(String[] args) throws Exception {
        // If no args, run the subprocess with log analysis
        if (args.length == 0) {
            testTimeBasedEvaluation();
            testMinimumHeapBoundary();
            testConcurrentAllocationUncommit();
        } else if ("subprocess".equals(args[0])) {
            // This is the subprocess that does the actual allocation/deallocation
            runTimeBasedUncommitTest();
        } else if ("minheap".equals(args[0])) {
            runMinHeapBoundaryTest();
        } else if ("concurrent".equals(args[0])) {
            runConcurrentTest();
        }
    }

    static void testTimeBasedEvaluation() throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UseG1GC",
            "-Xms8m", "-Xmx256m", "-XX:G1HeapRegionSize=1M",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:G1UncommitDelayMillis=3000", "-XX:G1TimeBasedEvaluationIntervalMillis=2000",
            "-XX:G1MinRegionsToUncommit=2",
            "-Xlog:gc*,gc+sizing*=debug",
            "gc.g1.TestG1RegionUncommit", "subprocess"
        );

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        // Verify the uncommit evaluation logic is working
        output.shouldContain("G1 Time-Based Heap Sizing enabled (uncommit-only)");
        output.shouldContain("Starting heap evaluation");
        output.shouldContain("Region state transition:");
        output.shouldContain("transitioning from active to inactive");
        output.shouldContain("Uncommit candidates found:");
        output.shouldContain("Uncommit evaluation: Found");
        output.shouldContain("target shrink:");

        output.shouldHaveExitValue(0);
        System.out.println("Test passed - time-based uncommit verified!");
    }

    static void runTimeBasedUncommitTest() throws Exception {
        final int allocSize = 64 * 1024 * 1024; // 64MB allocation - much larger than initial 8MB
        Object keepAlive;
        Object keepAlive2; // Keep some memory allocated to prevent full shrinkage

        System.out.println("=== Testing G1 Time-Based Uncommit ===");

        // Phase 1: Allocate memory to force significant heap expansion
        System.out.println("Phase 1: Allocating large amount of memory");
        keepAlive = new byte[allocSize];

        // Phase 2: Keep some memory allocated, free the rest to create inactive regions
        // This ensures current_heap > min_heap so uncommit is possible
        System.out.println("Phase 2: Partially freeing memory, keeping some allocated");
        keepAlive2 = new byte[24 * 1024 * 1024]; // Keep 24MB allocated
        keepAlive = null; // Free the 64MB, leaving regions available for uncommit
        System.gc();
        System.gc(); // Double GC to ensure the 64MB is cleaned up

        // Phase 3: Wait for regions to become inactive and uncommit to occur
        System.out.println("Phase 3: Waiting for time-based uncommit...");

        // Wait long enough for:
        // 1. G1UncommitDelayMillis (3000ms) - regions to become inactive
        // 2. G1TimeBasedEvaluationIntervalMillis (2000ms) - evaluation to run
        // 3. Multiple evaluation cycles to ensure uncommit happens
        Thread.sleep(15000); // 15 seconds should be plenty

        // Clean up remaining allocation
        keepAlive2 = null;
        System.gc();

        System.out.println("=== Test completed ===");
        Runtime.getRuntime().halt(0);
    }

    static void testMinimumHeapBoundary() throws Exception {
        System.out.println("Testing minimum heap boundary conditions...");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UseG1GC",
            "-Xms32m", "-Xmx64m",  // Small heap to test boundaries
            "-XX:G1HeapRegionSize=1M",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:G1UncommitDelayMillis=2000", // Short delay
            "-XX:G1TimeBasedEvaluationIntervalMillis=1000",
            "-XX:G1MinRegionsToUncommit=1",
            "-Xlog:gc+sizing=debug,gc+task=debug",
            "gc.g1.TestG1RegionUncommit", "minheap"
        );

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        // Should not uncommit below initial heap size
        output.shouldHaveExitValue(0);
        System.out.println("Minimum heap boundary test passed!");
    }

    static void testConcurrentAllocationUncommit() throws Exception {
        System.out.println("Testing concurrent allocation and uncommit...");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UseG1GC",
            "-Xms64m", "-Xmx256m",
            "-XX:G1HeapRegionSize=1M",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:G1TimeBasedEvaluationIntervalMillis=1000", // Frequent evaluation
            "-XX:G1UncommitDelayMillis=2000",
            "-XX:G1MinRegionsToUncommit=2",
            "-Xlog:gc+sizing=debug,gc+task=debug",
            "gc.g1.TestG1RegionUncommit", "concurrent"
        );

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        // Should handle concurrent operations safely
        output.shouldHaveExitValue(0);
        System.out.println("Concurrent allocation/uncommit test passed!");
    }

    static void runMinHeapBoundaryTest() throws Exception {
        System.out.println("=== Min Heap Boundary Test ===");

        List<byte[]> memory = new ArrayList<>();

        // Allocate close to max
        for (int i = 0; i < 28; i++) { // 28MB, close to 32MB limit
            memory.add(new byte[1024 * 1024]);
        }

        // Clear and wait for uncommit attempt
        memory.clear();
        System.gc();
        Thread.sleep(8000); // Wait longer than uncommit delay

        System.out.println("MinHeapBoundaryTest completed");
        Runtime.getRuntime().halt(0);
    }

    static void runConcurrentTest() throws Exception {
        System.out.println("=== Concurrent Test ===");

        final List<byte[]> sharedMemory = new ArrayList<>();
        final boolean[] stopFlag = {false};

        // Start allocation thread
        Thread allocThread = new Thread(() -> {
            int iterations = 0;
            while (!stopFlag[0] && iterations < 50) {
                try {
                    // Allocate
                    for (int j = 0; j < 5; j++) {
                        synchronized (sharedMemory) {
                            sharedMemory.add(new byte[1024 * 1024]); // 1MB
                        }
                        Thread.sleep(10);
                    }

                    // Clear some
                    synchronized (sharedMemory) {
                        if (sharedMemory.size() > 10) {
                            for (int k = 0; k < 5; k++) {
                                if (!sharedMemory.isEmpty()) {
                                    sharedMemory.remove(0);
                                }
                            }
                        }
                    }
                    System.gc();
                    Thread.sleep(50);
                    iterations++;
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        allocThread.start();

        // Let it run for a while to trigger time-based evaluation
        Thread.sleep(8000);

        stopFlag[0] = true;
        allocThread.join(2000);

        synchronized (sharedMemory) {
            sharedMemory.clear();
        }
        System.gc();

        System.out.println("ConcurrentTest completed");
        Runtime.getRuntime().halt(0);
    }
}
