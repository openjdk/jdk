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
 * @test TestPeriodicGCStringTableCleanup
 * @bug 8213198
 * @summary Test that periodic GC properly triggers concurrent mark cycles for string table cleanup
 * @requires vm.gc.G1
 * @library /test/lib
 * @build jdk.test.lib.Platform
 * @run driver gc.g1.TestPeriodicGCStringTableCleanup
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestPeriodicGCStringTableCleanup {

    private static final int TIMEOUT = 180; // 3 minutes

    public static void main(String[] args) throws Exception {
        // Test 1: Default behavior (no G1PeriodicGCInterval set) - should use ergonomic interval
        testErgonomicPeriodicGC();

        // Test 2: Explicit interval with concurrent=true
        testExplicitIntervalConcurrent();

        // Test 3: Explicit interval with concurrent=false, no load threshold
        testExplicitIntervalFullGC();

        // Test 4: Explicit interval with concurrent=false, with load threshold
        testExplicitIntervalWithLoadThreshold();
    }

    private static void testErgonomicPeriodicGC() throws Exception {
        System.out.println("Testing ergonomic periodic GC (JDK-8213198 main case)");

        List<String> vmArgs = new ArrayList<>();
        Collections.addAll(vmArgs,
            "-XX:+UseG1GC",
            "-Xms256m",
            "-Xmx512m",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:G1PeriodicGCErgonomicInterval=5000", // 5 seconds for testing
            "-Xlog:gc+periodic=debug",
            StringTableLeakSimulator.class.getName(),
            "10000" // Create 10k interned strings
        );

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(vmArgs.toArray(new String[0]));
        OutputAnalyzer output = ProcessTools.executeProcess(pb);

        output.shouldHaveExitValue(0);
        output.shouldContain("Checking for periodic GC");
        output.shouldContain("Triggering periodic concurrent GC for string table cleanup");

        System.out.println("✓ Ergonomic periodic GC test passed");
    }

    private static void testExplicitIntervalConcurrent() throws Exception {
        System.out.println("Testing explicit interval with concurrent=true");

        List<String> vmArgs = new ArrayList<>();
        Collections.addAll(vmArgs,
            "-XX:+UseG1GC",
            "-Xms256m",
            "-Xmx512m",
            "-XX:G1PeriodicGCInterval=5000", // 5 seconds
            "-XX:+G1PeriodicGCInvokesConcurrent", // explicit true
            "-Xlog:gc+periodic=debug",
            StringTableLeakSimulator.class.getName(),
            "5000"
        );

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(vmArgs.toArray(new String[0]));
        OutputAnalyzer output = ProcessTools.executeProcess(pb);

        output.shouldHaveExitValue(0);
        output.shouldContain("Triggering periodic concurrent GC.");
        output.shouldNotContain("string table cleanup");

        System.out.println("✓ Explicit interval concurrent test passed");
    }

    private static void testExplicitIntervalFullGC() throws Exception {
        System.out.println("Testing explicit interval with concurrent=false");

        List<String> vmArgs = new ArrayList<>();
        Collections.addAll(vmArgs,
            "-XX:+UseG1GC",
            "-Xms256m",
            "-Xmx512m",
            "-XX:G1PeriodicGCInterval=5000", // 5 seconds
            "-XX:-G1PeriodicGCInvokesConcurrent", // explicit false
            "-Xlog:gc+periodic=debug",
            StringTableLeakSimulator.class.getName(),
            "3000"
        );

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(vmArgs.toArray(new String[0]));
        OutputAnalyzer output = ProcessTools.executeProcess(pb);

        output.shouldHaveExitValue(0);
        output.shouldContain("Triggering periodic full GC");

        System.out.println("✓ Explicit interval full GC test passed");
    }

    private static void testExplicitIntervalWithLoadThreshold() throws Exception {
        System.out.println("Testing explicit interval with load threshold");

        List<String> vmArgs = new ArrayList<>();
        Collections.addAll(vmArgs,
            "-XX:+UseG1GC",
            "-Xms256m",
            "-Xmx512m",
            "-XX:G1PeriodicGCInterval=3000", // 3 seconds
            "-XX:-G1PeriodicGCInvokesConcurrent", // false
            "-XX:G1PeriodicGCSystemLoadThreshold=999.0", // Very high threshold
            "-Xlog:gc+periodic=debug",
            StringTableLeakSimulator.class.getName(),
            "3000"
        );

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(vmArgs.toArray(new String[0]));
        OutputAnalyzer output = ProcessTools.executeProcess(pb);

        output.shouldHaveExitValue(0);
        // With high load threshold and concurrent=false, should trigger full GC when load < threshold
        output.shouldContain("Triggering periodic full GC");

        System.out.println("✓ Load threshold test passed");
    }

    /**
     * Simulates the string table leak scenario described in JDK-8213198
     */
    public static class StringTableLeakSimulator {
        public static void main(String[] args) throws Exception {
            int numStrings = args.length > 0 ? Integer.parseInt(args[0]) : 10000;

            System.out.println("Creating " + numStrings + " interned strings to simulate string table growth");

            // Create many unique interned strings that will accumulate in the string table
            // This simulates the scenario where an application creates many temporary
            // strings that get interned but are not referenced after creation
            for (int i = 0; i < numStrings; i++) {
                String unique = ("temp_string_" + i + "_" + System.nanoTime()).intern();

                // Occasionally trigger young GCs by allocating objects
                if (i % 100 == 0) {
                    byte[] garbage = new byte[1024 * 100]; // 100KB
                    Thread.sleep(1); // Give time for GC activity
                }

                // Let references go out of scope, but strings remain interned
                unique = null;

                if (i % 1000 == 0) {
                    System.out.println("Created " + i + " interned strings");
                }
            }

            // Wait a bit longer to allow periodic GC to trigger
            System.out.println("Waiting for periodic GC to clean up string table...");
            Thread.sleep(15000); // 15 seconds

            System.out.println("String table leak simulation completed");
        }
    }
}
