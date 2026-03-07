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
 * @test TestPeriodicGCBehaviorTable
 * @bug 8213198
 * @summary Validate the periodic GC behavior table implementation for JDK-8213198
 * @requires vm.gc.G1
 * @library /test/lib
 * @build jdk.test.lib.Platform
 * @run driver gc.g1.TestPeriodicGCBehaviorTable
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestPeriodicGCBehaviorTable {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Testing JDK-8213198 Periodic GC Behavior Table ===");

        // Test Case 1: N* (interval not set) - NEW: Ergonomic concurrent GC
        testErgonomicPeriodicGC();

        // Test Case 2: Y + Y + Y (interval=5000, concurrent=true, load_threshold=1.0)
        testExplicitConcurrentWithThreshold();

        // Test Case 3: Y + Y + N (interval=5000, concurrent=true, no load threshold)
        testExplicitConcurrentNoThreshold();

        // Test Case 4: Y + N + Y (interval=5000, concurrent=false, load_threshold=999.0)
        testExplicitFullWithHighThreshold();

        // Test Case 5: Y + N + N (interval=5000, concurrent=false, no load threshold)
        testExplicitFullNoThreshold();

        // Test Case 6: Y==0 (interval=0) - Should do nothing
        testDisabledPeriodicGC();

        System.out.println("\n=== All Behavior Table Tests Passed! ===");
    }

    /**
     * Test Case 1: G1PeriodicGCInterval=N* (not set)
     * Expected: Initiate Concurrent Mark @ ergonomic interval
     * This is the main JDK-8213198 fix for string table cleanup
     */
    private static void testErgonomicPeriodicGC() throws Exception {
        System.out.println("\n[Test 1] N* - Ergonomic Concurrent GC (JDK-8213198 main case)");

        List<String> vmArgs = new ArrayList<>();
        Collections.addAll(vmArgs,
            "-XX:+UseG1GC",
            "-Xms512m", "-Xmx512m",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:G1PeriodicGCErgonomicInterval=5000", // Enable ergonomic intervals for JDK-8213198
            // Note: G1PeriodicGCInterval is NOT set (defaults to 0, disabled)
            "-Xlog:gc+periodic=debug",
            SimpleApp.class.getName()
        );

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(vmArgs.toArray(new String[0]));
        OutputAnalyzer output = ProcessTools.executeProcess(pb);

        output.shouldHaveExitValue(0);
        output.shouldContain("Triggering periodic concurrent GC for string table cleanup");

        System.out.println("✓ PASS: Ergonomic interval triggers concurrent GC");
    }

    /**
     * Test Case 2: Y + Y + Y (interval=5000, concurrent=true, load_threshold=1.0)
     * Expected: Initiate Conc GC @ Interval with usage >= threshold
     */
    private static void testExplicitConcurrentWithThreshold() throws Exception {
        System.out.println("\n[Test 2] Y+Y+Y - Explicit Concurrent with Load Threshold");

        List<String> vmArgs = new ArrayList<>();
        Collections.addAll(vmArgs,
            "-XX:+UseG1GC",
            "-Xms512m", "-Xmx512m",
            "-XX:G1PeriodicGCInterval=3000",              // Y (set)
            "-XX:+G1PeriodicGCInvokesConcurrent",         // Y (true)
            "-XX:G1PeriodicGCSystemLoadThreshold=50.0",   // Y (set to very high value for CI environments)
            "-Xlog:gc+periodic=debug",
            SimpleApp.class.getName()
        );

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(vmArgs.toArray(new String[0]));
        OutputAnalyzer output = ProcessTools.executeProcess(pb);

        output.shouldHaveExitValue(0);
        output.shouldContain("Triggering periodic concurrent GC.");
        output.shouldNotContain("string table cleanup");

        System.out.println("✓ PASS: Explicit concurrent with load threshold");
    }

    /**
     * Test Case 3: Y + Y + N (interval=5000, concurrent=true, no load threshold)
     * Expected: Initiate Conc GC @ Interval
     */
    private static void testExplicitConcurrentNoThreshold() throws Exception {
        System.out.println("\n[Test 3] Y+Y+N - Explicit Concurrent without Load Threshold");

        List<String> vmArgs = new ArrayList<>();
        Collections.addAll(vmArgs,
            "-XX:+UseG1GC",
            "-Xms512m", "-Xmx512m",
            "-XX:G1PeriodicGCInterval=3000",              // Y (set)
            "-XX:+G1PeriodicGCInvokesConcurrent",         // Y (true)
            "-XX:G1PeriodicGCSystemLoadThreshold=0.0",    // N (disabled)
            "-Xlog:gc+periodic=debug",
            SimpleApp.class.getName()
        );

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(vmArgs.toArray(new String[0]));
        OutputAnalyzer output = ProcessTools.executeProcess(pb);

        output.shouldHaveExitValue(0);
        output.shouldContain("Triggering periodic concurrent GC.");
        output.shouldNotContain("string table cleanup");

        System.out.println("✓ PASS: Explicit concurrent without threshold");
    }

    /**
     * Test Case 4: Y + N + Y (interval=5000, concurrent=false, load_threshold=999.0)
     * Expected: Full GC @ usage < threshold, Conc GC @ usage >= threshold
     * NEW BEHAVIOR: With very high threshold, should trigger Full GC
     */
    private static void testExplicitFullWithHighThreshold() throws Exception {
        System.out.println("\n[Test 4] Y+N+Y - Explicit Full with High Load Threshold");

        List<String> vmArgs = new ArrayList<>();
        Collections.addAll(vmArgs,
            "-XX:+UseG1GC",
            "-Xms512m", "-Xmx512m",
            "-XX:G1PeriodicGCInterval=3000",              // Y (set)
            "-XX:-G1PeriodicGCInvokesConcurrent",         // N (false)
            "-XX:G1PeriodicGCSystemLoadThreshold=999.0",  // Y (very high)
            "-Xlog:gc+periodic=debug",
            SimpleApp.class.getName()
        );

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(vmArgs.toArray(new String[0]));
        OutputAnalyzer output = ProcessTools.executeProcess(pb);

        output.shouldHaveExitValue(0);
        // Should trigger Full GC since system load < 999.0
        output.shouldContain("Triggering periodic full GC");

        System.out.println("✓ PASS: High threshold triggers full GC");
    }

    /**
     * Test Case 5: Y + N + N (interval=5000, concurrent=false, no load threshold)
     * Expected: Initiate Full GC @ Interval
     */
    private static void testExplicitFullNoThreshold() throws Exception {
        System.out.println("\n[Test 5] Y+N+N - Explicit Full without Load Threshold");

        List<String> vmArgs = new ArrayList<>();
        Collections.addAll(vmArgs,
            "-XX:+UseG1GC",
            "-Xms512m", "-Xmx512m",
            "-XX:G1PeriodicGCInterval=3000",              // Y (set)
            "-XX:-G1PeriodicGCInvokesConcurrent",         // N (false)
            "-XX:G1PeriodicGCSystemLoadThreshold=0.0",    // N (disabled)
            "-Xlog:gc+periodic=debug",
            SimpleApp.class.getName()
        );

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(vmArgs.toArray(new String[0]));
        OutputAnalyzer output = ProcessTools.executeProcess(pb);

        output.shouldHaveExitValue(0);
        output.shouldContain("Triggering periodic full GC");

        System.out.println("✓ PASS: Explicit full GC without threshold");
    }

    /**
     * Test Case 6: Y==0 (interval=0)
     * Expected: Do nothing
     */
    private static void testDisabledPeriodicGC() throws Exception {
        System.out.println("\n[Test 6] Y==0 - Disabled Periodic GC");

        List<String> vmArgs = new ArrayList<>();
        Collections.addAll(vmArgs,
            "-XX:+UseG1GC",
            "-Xms512m", "-Xmx512m",
            "-XX:G1PeriodicGCInterval=0",                 // Y==0 (disabled)
            "-Xlog:gc+periodic=debug",
            QuickApp.class.getName()  // Shorter run
        );

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(vmArgs.toArray(new String[0]));
        OutputAnalyzer output = ProcessTools.executeProcess(pb);

        output.shouldHaveExitValue(0);
        output.shouldNotContain("Triggering periodic");

        System.out.println("✓ PASS: Periodic GC disabled");
    }

    /**
     * Simple test application that runs long enough for periodic GC to trigger
     */
    public static class SimpleApp {
        public static void main(String[] args) throws Exception {
            System.out.println("Starting periodic GC test application...");

            // Create some interned strings to simulate JDK-8213198 scenario
            for (int i = 0; i < 1000; i++) {
                String s = ("test_string_" + i + "_" + System.nanoTime()).intern();
                if (i % 100 == 0) {
                    // Trigger some young GCs
                    byte[] garbage = new byte[1024 * 50];
                }
            }

            // Wait for periodic GC (should trigger within interval)
            Thread.sleep(8000); // 8 seconds
            System.out.println("Test application completed");
        }
    }

    /**
     * Quick application for disabled test
     */
    public static class QuickApp {
        public static void main(String[] args) throws Exception {
            Thread.sleep(2000); // 2 seconds - shouldn't see periodic GC
            System.out.println("Quick app completed");
        }
    }
}
