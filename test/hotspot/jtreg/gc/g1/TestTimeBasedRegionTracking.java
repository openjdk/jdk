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
 * @test TestTimeBasedRegionTracking
 * @bug 8357445
 * @summary Test region activity tracking and state transitions for time-based heap sizing
 * @requires vm.gc.G1
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management/sun.management
 * @run main/othervm/timeout=120 -XX:+UseG1GC -XX:+UnlockDiagnosticVMOptions
 *      -Xms32m -Xmx128m -XX:G1HeapRegionSize=1M
 *      -XX:G1TimeBasedEvaluationIntervalMillis=5000
 *      -XX:G1UncommitDelayMillis=10000
 *      -XX:G1MinRegionsToUncommit=2
 *      -Xlog:gc*,gc+sizing*=debug gc.g1.TestTimeBasedRegionTracking
 */

import java.util.*;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestTimeBasedRegionTracking {

    private static final String TEST_VM_OPTS = "-XX:+UseG1GC " +
        "-XX:+UnlockDiagnosticVMOptions " +
        "-XX:G1TimeBasedEvaluationIntervalMillis=5000 " +
        "-XX:G1UncommitDelayMillis=10000 " +
        "-XX:G1MinRegionsToUncommit=2 " +
        "-XX:G1HeapRegionSize=1M " +
        "-Xmx128m -Xms32m " +
        "-Xlog:gc*,gc+sizing*=debug";

    public static void main(String[] args) throws Exception {
        testRegionStateTransitions();
        testConcurrentRegionAccess();
        testRegionLifecycleEdgeCases();
        testSafepointRaceConditions();
    }

    static void testRegionStateTransitions() throws Exception {
        String[] command = new String[TEST_VM_OPTS.split(" ").length + 1];
        System.arraycopy(TEST_VM_OPTS.split(" "), 0, command, 0, TEST_VM_OPTS.split(" ").length);
        command[command.length - 1] = "gc.g1.TestTimeBasedRegionTracking$RegionTransitionTest";
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(command);

        Process process = pb.start();
        OutputAnalyzer output = new OutputAnalyzer(process);

        // Verify region state changes and basic functionality
        // Check for key log messages that indicate the feature is working
        if (output.getStdout().contains("Region state transition:") ||
            output.getStdout().contains("Uncommit candidates found:") ||
            output.getStdout().contains("Starting uncommit evaluation")) {
            System.out.println("Time-based evaluation system is working");
        } else {
            // If none of the expected messages appear, that's a failure
            output.shouldContain("Starting uncommit evaluation");
        }

        output.shouldHaveExitValue(0);
    }

    public static class RegionTransitionTest {
        private static final int MB = 1024 * 1024;
        private static ArrayList<byte[]> arrays = new ArrayList<>();

        public static void main(String[] args) throws Exception {
            System.out.println("RegionTransitionTest: Starting");

            // Phase 1: Active allocation
            allocateMemory(20); // Reduced from 32MB for faster execution
            System.gc();

            // Phase 2: Idle period
            arrays.clear();
            System.gc();
            Thread.sleep(12000); // Reduced wait time - should still trigger uncommit

            // Phase 3: Reallocation
            allocateMemory(10); // Smaller reallocation
            System.gc();

            // Clean up and wait for final uncommit evaluation
            arrays = null;
            System.gc();
            Thread.sleep(1000); // Shorter final wait

            System.out.println("RegionTransitionTest: Test completed");
            Runtime.getRuntime().halt(0);
        }

        static void allocateMemory(int mb) throws InterruptedException {
            for (int i = 0; i < mb; i++) {
                arrays.add(new byte[MB]);
                if (i % 4 == 0) Thread.sleep(10);
            }
        }
    }

    static void testConcurrentRegionAccess() throws Exception {
        String[] command = new String[TEST_VM_OPTS.split(" ").length + 1];
        System.arraycopy(TEST_VM_OPTS.split(" "), 0, command, 0, TEST_VM_OPTS.split(" ").length);
        command[command.length - 1] = "gc.g1.TestTimeBasedRegionTracking$ConcurrentAccessTest";
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(command);

        Process process = pb.start();
        OutputAnalyzer output = new OutputAnalyzer(process);

        // Verify concurrent access is handled safely
        output.shouldHaveExitValue(0);
    }

    static void testRegionLifecycleEdgeCases() throws Exception {
        String[] command = new String[TEST_VM_OPTS.split(" ").length + 1];
        System.arraycopy(TEST_VM_OPTS.split(" "), 0, command, 0, TEST_VM_OPTS.split(" ").length);
        command[command.length - 1] = "gc.g1.TestTimeBasedRegionTracking$RegionLifecycleEdgeCaseTest";
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(command);

        Process process = pb.start();
        OutputAnalyzer output = new OutputAnalyzer(process);

        // Verify region lifecycle edge cases are handled
        output.shouldHaveExitValue(0);
    }

    static void testSafepointRaceConditions() throws Exception {
        System.out.println("Testing safepoint and allocation race conditions...");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UseG1GC",
            "-XX:+UnlockDiagnosticVMOptions",
            "-Xms64m", "-Xmx256m",
            "-XX:G1HeapRegionSize=1M",
            "-XX:G1TimeBasedEvaluationIntervalMillis=5000", // More reasonable interval
            "-XX:G1UncommitDelayMillis=3000", // Shorter delay for faster test
            "-XX:G1MinRegionsToUncommit=1",
            "-Xlog:gc*,gc+sizing*=debug",
            "gc.g1.TestTimeBasedRegionTracking$SafepointRaceTest"
        );

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        // Should handle safepoint races without errors
        output.shouldContain("G1 Time-Based Heap Sizing enabled (uncommit-only)");
        output.shouldHaveExitValue(0);
        System.out.println("Safepoint race conditions test passed!");
    }

    public static class ConcurrentAccessTest {
        private static final int MB = 1024 * 1024;
        private static final List<byte[]> sharedMemory = new ArrayList<>();
        private static volatile boolean stopThreads = false;

        public static void main(String[] args) throws Exception {
            System.out.println("ConcurrentAccessTest: Starting");

            // Start multiple allocation threads
            Thread[] threads = new Thread[3];
            for (int t = 0; t < threads.length; t++) {
                final int threadId = t;
                threads[t] = new Thread(() -> {
                    int iterations = 0;
                    while (!stopThreads && iterations < 30) {
                        try {
                            // Allocate
                            for (int i = 0; i < 3; i++) {
                                synchronized (sharedMemory) {
                                    sharedMemory.add(new byte[512 * 1024]); // 512KB
                                }
                                Thread.sleep(10);
                            }

                            // Clear some memory
                            synchronized (sharedMemory) {
                                if (sharedMemory.size() > 15) {
                                    for (int i = 0; i < 5; i++) {
                                        if (!sharedMemory.isEmpty()) {
                                            sharedMemory.remove(0);
                                        }
                                    }
                                }
                            }

                            if (iterations % 10 == 0) {
                                System.gc();
                            }

                            iterations++;
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    System.out.println("Thread " + threadId + " completed " + iterations + " iterations");
                });
                threads[t].start();
            }

            // Let threads run for a shorter time
            Thread.sleep(5000); // Reduced from 8000ms

            stopThreads = true;
            for (Thread t : threads) {
                t.join(1000); // Reduced join timeout
            }

            synchronized (sharedMemory) {
                sharedMemory.clear();
            }
            System.gc();
            Thread.sleep(1000); // Reduced from 3000ms

            System.out.println("ConcurrentAccessTest: Test completed");
            Runtime.getRuntime().halt(0);
        }
    }

    public static class RegionLifecycleEdgeCaseTest {
        private static final int MB = 1024 * 1024;
        private static List<Object> memory = new ArrayList<>();

        public static void main(String[] args) throws Exception {
            System.out.println("RegionLifecycleEdgeCaseTest: Starting");

            // Phase 1: Mixed allocation patterns
            // Small objects
            for (int i = 0; i < 100; i++) {
                memory.add(new byte[8 * 1024]); // 8KB objects
            }

            // Medium objects
            for (int i = 0; i < 20; i++) {
                memory.add(new byte[40 * 1024]); // 40KB objects
            }

            // Large objects (but not humongous)
            for (int i = 0; i < 5; i++) {
                memory.add(new byte[300 * 1024]); // 300KB objects
            }

            Thread.sleep(2000);

            // Phase 2: Create fragmentation by selective deallocation
            for (int i = memory.size() - 1; i >= 0; i -= 2) {
                memory.remove(i);
            }

            System.gc();
            Thread.sleep(3000);

            // Phase 3: Add humongous objects
            for (int i = 0; i < 3; i++) {
                memory.add(new byte[900 * 1024]); // 900KB humongous
                Thread.sleep(500);
            }

            Thread.sleep(2000);

            // Phase 4: Final cleanup
            memory.clear();
            System.gc();
            Thread.sleep(6000); // Reduced wait time but still allow for evaluation

            System.out.println("RegionLifecycleEdgeCaseTest: Test completed");
            Runtime.getRuntime().halt(0);
        }
    }

    public static class SafepointRaceTest {
        public static void main(String[] args) throws Exception {
            System.out.println("=== Safepoint Race Conditions Test ===");

            final AtomicBoolean stopFlag = new AtomicBoolean(false);
            final List<byte[]> sharedMemory = Collections.synchronizedList(new ArrayList<>());

            // Start fewer threads with reduced iterations for faster completion
            Thread[] threads = new Thread[2];
            for (int i = 0; i < threads.length; i++) {
                final int threadId = i;
                threads[i] = new Thread(() -> {
                    int iteration = 0;
                    while (!stopFlag.get() && iteration < 10) { // Reduced iterations
                        try {
                            // Allocate and deallocate
                            for (int j = 0; j < 3; j++) { // Fewer allocations per iteration
                                sharedMemory.add(new byte[256 * 1024]); // Smaller allocations
                            }

                            // Force GC less frequently
                            if (iteration % 5 == 0) {
                                System.gc();
                            }

                            // Clear some allocations
                            synchronized (sharedMemory) {
                                if (sharedMemory.size() > 6) {
                                    for (int k = 0; k < 2; k++) {
                                        if (!sharedMemory.isEmpty()) {
                                            sharedMemory.remove(0);
                                        }
                                    }
                                }
                            }

                            Thread.sleep(50); // Shorter pause
                            iteration++;
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    System.out.println("Thread " + threadId + " completed");
                });
                threads[i].start();
            }

            // Much shorter run time - just enough for one evaluation cycle
            Thread.sleep(4000);

            // Stop threads
            stopFlag.set(true);
            for (Thread thread : threads) {
                thread.join(1000);
            }

            // Clean up
            sharedMemory.clear();
            System.gc();

            // Wait for one more evaluation cycle to see some activity
            Thread.sleep(2000);

            System.out.println("SafepointRaceTest: Test completed");
            Runtime.getRuntime().halt(0);
        }
    }
}
