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
 * @test TestTimeBasedHeapSizing
 * @bug 8357445
 * @summary Test time-based heap sizing functionality in G1
 * @requires vm.gc.G1
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management/sun.management
 * @run main/othervm -XX:+UseG1GC -XX:+UnlockDiagnosticVMOptions
 *     -Xms32m -Xmx128m -XX:G1HeapRegionSize=1M
 *     -XX:G1TimeBasedEvaluationIntervalMillis=5000
 *     -XX:G1UncommitDelayMillis=10000
 *     -XX:G1MinRegionsToUncommit=2
 *     -Xlog:gc*,gc+sizing*=debug
 *     gc.g1.TestTimeBasedHeapSizing
 */

import java.util.*;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestTimeBasedHeapSizing {

    private static final String TEST_VM_OPTS = "-XX:+UseG1GC " +
        "-XX:+UnlockDiagnosticVMOptions " +
        "-XX:G1TimeBasedEvaluationIntervalMillis=5000 " +
        "-XX:G1UncommitDelayMillis=10000 " +
        "-XX:G1MinRegionsToUncommit=2 " +
        "-XX:G1HeapRegionSize=1M " +
        "-Xmx128m -Xms32m " +
        "-Xlog:gc*,gc+sizing*=debug";

    public static void main(String[] args) throws Exception {
        testBasicFunctionality();
        testHumongousObjectHandling();
        testRapidAllocationCycles();
        testLargeHumongousObjects();
    }

    static void testBasicFunctionality() throws Exception {
        String[] command = new String[TEST_VM_OPTS.split(" ").length + 1];
        System.arraycopy(TEST_VM_OPTS.split(" "), 0, command, 0, TEST_VM_OPTS.split(" ").length);
        command[command.length - 1] = "gc.g1.TestTimeBasedHeapSizing$BasicFunctionalityTest";
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(command);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        output.shouldContain("G1 Time-Based Heap Sizing enabled (uncommit-only)");
        output.shouldContain("Starting heap evaluation");
        output.shouldContain("Full region scan:");

        output.shouldHaveExitValue(0);
    }

    public static class BasicFunctionalityTest {
        private static final int MB = 1024 * 1024;
        private static ArrayList<byte[]> arrays = new ArrayList<>();

        public static void main(String[] args) throws Exception {
            System.out.println("BasicFunctionalityTest: Starting heap activity");

            // Create significant heap activity
            for (int cycle = 0; cycle < 3; cycle++) {
                System.out.println("Allocation cycle " + cycle);
                allocateMemory(25);  // 25MB per cycle
                Thread.sleep(200);   // Brief pause
                clearMemory();
                System.gc();
                Thread.sleep(200);
            }

            System.out.println("BasicFunctionalityTest: Starting idle period");

            // Sleep to allow time-based evaluation
            Thread.sleep(18000);  // 18 seconds

            System.out.println("BasicFunctionalityTest: Completed idle period");

            // Final cleanup
            clearMemory();
            Thread.sleep(500);

            System.out.println("BasicFunctionalityTest: Test completed");
            Runtime.getRuntime().halt(0);
        }

        static void allocateMemory(int mb) throws InterruptedException {
            for (int i = 0; i < mb; i++) {
                arrays.add(new byte[MB]);
                if (i % 4 == 0) Thread.sleep(10);
            }
        }

        static void clearMemory() {
            arrays.clear();
            System.gc();
        }
    }

    static void testHumongousObjectHandling() throws Exception {
        String[] command = new String[TEST_VM_OPTS.split(" ").length + 1];
        System.arraycopy(TEST_VM_OPTS.split(" "), 0, command, 0, TEST_VM_OPTS.split(" ").length);
        command[command.length - 1] = "gc.g1.TestTimeBasedHeapSizing$HumongousObjectTest";
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(command);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        output.shouldContain("Starting heap evaluation");
        output.shouldHaveExitValue(0);
    }

    static void testRapidAllocationCycles() throws Exception {
        String[] command = new String[TEST_VM_OPTS.split(" ").length + 1];
        System.arraycopy(TEST_VM_OPTS.split(" "), 0, command, 0, TEST_VM_OPTS.split(" ").length);
        command[command.length - 1] = "gc.g1.TestTimeBasedHeapSizing$RapidCycleTest";
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(command);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        output.shouldContain("Starting heap evaluation");
        output.shouldHaveExitValue(0);
    }

    static void testLargeHumongousObjects() throws Exception {
        System.out.println("Testing large humongous object activity tracking...");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UseG1GC",
            "-XX:+UnlockDiagnosticVMOptions",
            "-Xms64m", "-Xmx256m",
            "-XX:G1HeapRegionSize=1M",
            "-XX:G1UncommitDelayMillis=5000",
            "-XX:G1MinRegionsToUncommit=1",
            "-Xlog:gc*,gc+sizing*=debug",
            "gc.g1.TestTimeBasedHeapSizing$LargeHumongousTest"
        );

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        // Large humongous objects should not affect uncommit safety
        output.shouldContain("G1 Time-Based Heap Sizing enabled (uncommit-only)");
        output.shouldHaveExitValue(0);
        System.out.println("Large humongous object test passed!");
    }

    public static class HumongousObjectTest {
        private static final int MB = 1024 * 1024;
        private static ArrayList<byte[]> humongousObjects = new ArrayList<>();

        public static void main(String[] args) throws Exception {
            System.out.println("HumongousObjectTest: Starting");

            // Allocate humongous objects (> 512KB for 1MB regions)
            for (int i = 0; i < 8; i++) {
                humongousObjects.add(new byte[800 * 1024]); // 800KB humongous
                System.out.println("Allocated humongous object " + (i + 1));
                Thread.sleep(200);
            }

            // Keep them alive for a while
            Thread.sleep(3000);

            // Clear and test uncommit behavior
            humongousObjects.clear();
            System.gc();
            Thread.sleep(12000); // Wait for uncommit delay

            System.out.println("HumongousObjectTest: Test completed");
            Runtime.getRuntime().halt(0);
        }
    }

    public static class RapidCycleTest {
        private static final int MB = 1024 * 1024;
        private static ArrayList<byte[]> memory = new ArrayList<>();

        public static void main(String[] args) throws Exception {
            System.out.println("RapidCycleTest: Starting");

            // Rapid allocation/deallocation cycles
            for (int cycle = 0; cycle < 15; cycle++) {
                // Quick allocation
                for (int i = 0; i < 8; i++) {
                    memory.add(new byte[MB]); // 1MB
                }

                // Quick deallocation
                memory.clear();
                System.gc();

                // Brief pause
                Thread.sleep(100);

                if (cycle % 5 == 0) {
                    System.out.println("Completed cycle " + cycle);
                }
            }

            // Final wait for time-based evaluation
            Thread.sleep(12000);

            System.out.println("RapidCycleTest: Test completed");
            Runtime.getRuntime().halt(0);
        }
    }

    public static class LargeHumongousTest {
        public static void main(String[] args) throws Exception {
            System.out.println("=== Large Humongous Object Test ===");

            // Allocate several large humongous objects (multiple regions each)
            List<byte[]> humongousObjects = new ArrayList<>();

            // Each region is 1MB, so allocate 2MB objects (humongous spanning multiple regions)
            for (int i = 0; i < 5; i++) {
                humongousObjects.add(new byte[2 * 1024 * 1024]);
                System.gc(); // Force potential region transitions
                Thread.sleep(100);
            }

            // Hold some, release others to create mixed region states
            humongousObjects.remove(0);
            humongousObjects.remove(0);
            System.gc();

            // Wait for time-based evaluation with humongous regions present
            Thread.sleep(8000);

            // Clean up
            humongousObjects.clear();
            System.gc();

            System.out.println("LargeHumongousTest: Test completed");
            Runtime.getRuntime().halt(0);
        }
    }
}
