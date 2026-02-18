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

package gc;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

/*
 * @test
 * @bug 8370947
 * @summary Test command-line options for UseDeferredICacheInvalidation and NeoverseN1Errata1542419
 * @library /test/lib
 * @requires os.arch == "aarch64"
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI gc.TestDeferredICacheInvalidationCmdOptions
 */

public class TestDeferredICacheInvalidationCmdOptions {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final String USE_DEFERRED_ICACHE_INVALIDATION = "UseDeferredICacheInvalidation";
    private static final String NEOVERSE_N1_ERRATA_1542419 = "NeoverseN1Errata1542419";

    // CPU identifiers
    private static final int CPU_ARM = 0x41;
    private static final int NEOVERSE_N1_MODEL = 0xd0c;

    // CPU info parsed from WhiteBox.getCPUFeatures()
    private static int cpuFamily;
    private static int cpuVariant;
    private static int cpuModel;
    private static int cpuModel2;
    private static int cpuRevision;
    private static boolean isNeoverseN1;
    private static boolean isAffected;

    public static void main(String[] args) throws Exception {
        System.out.println("Testing UseDeferredICacheInvalidation and NeoverseN1Errata1542419 command-line options...");

        // Parse CPU features first
        parseCPUFeatures();
        printCPUInfo();

        if (cpuFamily != CPU_ARM) {
            System.out.println("Not running on ARM CPU, skipping tests");
            return;
        }

        // Test case 1: Check defaults on Neoverse N1 pre-r4p1 (if applicable)
        testCase1_DefaultsOnNeoverseN1();

        // Test case 2: Check NeoverseN1Errata1542419 is false on unaffected CPUs
        testCase2_DefaultsOnUnaffectedCPUs();

        // Test case 3: Check if NeoverseN1Errata1542419 is set to false on affected CPUs,
        //             UseDeferredICacheInvalidation is also set to false
        testCase3_ExplicitlyDisableNeoverseN1ErroraAffectsDeferred();

        // Test case 4: Check JVM error if UseDeferredICacheInvalidation=true
        //             but NeoverseN1Errata1542419=false on affected CPUs
        testCase4_ConflictingFlagsOnAffectedCPUs();

        // Test case 5: Check explicit NeoverseN1Errata1542419=true enables UseDeferredICacheInvalidation
        testCase5_ExplicitlyEnableErrataEnablesDeferred();

        // Test case 6: Check both flags can be explicitly set to false
        testCase6_ExplicitlyDisableBothFlags();

        // Test case 7: Check UseDeferredICacheInvalidation=false with NeoverseN1Errata1542419=true
        testCase7_ConflictingErrataWithoutDeferred();

        // Test case 8: Check setting NeoverseN1Errata1542419=true on unaffected CPU causes an error
        testCase8_EnablingErrataOnUnaffectedCPU();

        System.out.println("All test cases passed!");
    }

    /**
     * Parse CPU features string from WhiteBox.getCPUFeatures() to extract:
     * - cpuFamily: 0x41 for ARM
     * - cpuVariant: major revision
     * - cpuModel: e.g., 0xd0c for Neoverse N1
     * - cpuRevision: minor revision
     * - cpuModel2: secondary model (if present, in parentheses)
     *
     * Format: 0x%02x:0x%x:0x%03x:%d[(0x%03x)]
     * Example: "0x41:0x3:0xd0c:0" or "0x41:0x3:0xd0c:0(0xd0c)"
     */
    private static void parseCPUFeatures() {
        String cpuFeatures = WB.getCPUFeatures();
        System.out.println("CPU Features string: " + cpuFeatures);

        if (cpuFeatures == null || cpuFeatures.isEmpty()) {
            System.out.println("Warning: No CPU features available");
            isNeoverseN1 = false;
            isAffected = false;
            return;
        }

        try {
            // Extract the first part before the comma (contains family:variant:model:revision(model2))
            int commaIndex = cpuFeatures.indexOf(",");
            if (commaIndex == -1) {
                System.out.println("Warning: Unexpected CPU features format (no comma): " + cpuFeatures);
                isNeoverseN1 = false;
                isAffected = false;
                return;
            }

            String cpuPart = cpuFeatures.substring(0, commaIndex).trim();

            // Split by colon to get individual components
            String[] parts = cpuPart.split(":");
            if (parts.length < 4) {
                System.out.println("Warning: Unexpected CPU features format: " + cpuPart);
                isNeoverseN1 = false;
                isAffected = false;
                return;
            }

            // Parse hex/decimal values, removing "0x" prefix where applicable
            cpuFamily = Integer.parseInt(parts[0].substring(2), 16);
            cpuVariant = Integer.parseInt(parts[1].substring(2), 16);
            cpuModel = Integer.parseInt(parts[2].substring(2), 16);

            int model2Start = parts[3].indexOf("(");
            String revisionStr = parts[3];
            cpuModel2 = 0; // Default to 0 if not present
            if (model2Start != -1) {
                if (!parts[3].endsWith(")")) {
                    System.out.println("Warning: Unexpected CPU features format (missing closing parenthesis): " + parts[3]);
                    isNeoverseN1 = false;
                    isAffected = false;
                    return;
                }
                String model2Str = parts[3].substring(model2Start + 1, parts[3].length() - 1);
                cpuModel2 = Integer.parseInt(model2Str.substring(2), 16);
                revisionStr = parts[3].substring(0, model2Start);
            }
            cpuRevision = Integer.parseInt(revisionStr);

            // Check if either model or model2 matches Neoverse N1
            isNeoverseN1 = (cpuFamily == CPU_ARM) &&
                           (cpuModel == NEOVERSE_N1_MODEL || cpuModel2 == NEOVERSE_N1_MODEL);

            // Neoverse N1: Errata 1542419 affects r3p0, r3p1 and r4p0
            // It is fixed in r4p1 and later revisions
            if (isNeoverseN1) {
                isAffected = (cpuVariant == 3 && cpuRevision == 0) ||
                             (cpuVariant == 3 && cpuRevision == 1) ||
                             (cpuVariant == 4 && cpuRevision == 0);
            } else {
                isAffected = false;
            }
        } catch (Exception e) {
            System.out.println("Error parsing CPU features: " + e.getMessage());
            e.printStackTrace();
            isNeoverseN1 = false;
            isAffected = false;
        }
    }

    private static void printCPUInfo() {
        System.out.println("\n=== CPU Information ===");
        System.out.println("CPU Family: 0x" + Integer.toHexString(cpuFamily));
        System.out.println("CPU Variant: 0x" + Integer.toHexString(cpuVariant));
        System.out.println("CPU Model: 0x" + Integer.toHexString(cpuModel));
        if (cpuModel2 != 0) {
            System.out.println("CPU Model2: 0x" + Integer.toHexString(cpuModel2));
        }
        System.out.println("CPU Revision: " + cpuRevision);
        System.out.println("Is Neoverse N1: " + isNeoverseN1);
        System.out.println("Is affected by errata 1542419: " + isAffected);
        System.out.println("======================\n");
    }

    /**
     * Test case 1: Check the UseDeferredICacheInvalidation and NeoverseN1Errata1542419
     * are set to true for Neoverse N1 pre-r4p1.
     */
    private static void testCase1_DefaultsOnNeoverseN1() throws Exception {
        if (!isAffected) {
            System.out.println("\nTest case 1: Skipping since CPU is not affected by Neoverse N1 errata 1542419");
            return;
        }

        System.out.println("\nTest case 1: Check defaults on Neoverse N1 affected revisions");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+PrintFlagsFinal",
            "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        String output_str = output.getOutput();
        boolean hasDeferredEnabled = output_str.matches("(?s).*bool\\s+UseDeferredICacheInvalidation\\s*=\\s*true.*");
        boolean hasErrataEnabled = output_str.matches("(?s).*bool\\s+NeoverseN1Errata1542419\\s*=\\s*true.*");

        System.out.println("UseDeferredICacheInvalidation enabled: " + hasDeferredEnabled);
        System.out.println("NeoverseN1Errata1542419 enabled: " + hasErrataEnabled);

        // If running on affected Neoverse N1, both should be true
        if (!hasDeferredEnabled || !hasErrataEnabled) {
            throw new RuntimeException("On affected Neoverse N1, both flags should be enabled by default");
        }
        System.out.println("Correctly enabled on affected Neoverse N1");
    }

    /**
     * Test case 2: Check NeoverseN1Errata1542419 is false on unaffected CPUs.
     */
    private static void testCase2_DefaultsOnUnaffectedCPUs() throws Exception {
        if (isAffected) {
            System.out.println("\nTest case 2: Skipping since CPU is affected by Neoverse N1 errata 1542419");
            return;
        }

        System.out.println("\nTest case 2: Check NeoverseN1Errata1542419 is false on unaffected CPUs");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+PrintFlagsFinal",
            "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        String output_str = output.getOutput();
        boolean hasErrataEnabled = output_str.matches("(?s).*bool\\s+NeoverseN1Errata1542419\\s*=\\s*true.*");

        System.out.println("NeoverseN1Errata1542419 enabled: " + hasErrataEnabled);

        // On non-Neoverse N1 or unaffected Neoverse N1 CPUs, NeoverseN1Errata1542419 should be false
        if (hasErrataEnabled) {
            throw new RuntimeException("On unaffected CPUs, NeoverseN1Errata1542419 should be disabled");
        }
        System.out.println("Correctly disabled on unaffected CPU");
    }

    /**
     * Test case 3: Check if NeoverseN1Errata1542419 is set to false via cmd on affected CPUs,
     * UseDeferredICacheInvalidation is set to false.
     */
    private static void testCase3_ExplicitlyDisableNeoverseN1ErroraAffectsDeferred() throws Exception {
        if (!isAffected) {
            System.out.println("\nTest case 3: Skipping since CPU is not affected by Neoverse N1 errata 1542419");
            return;
        }

        System.out.println("\nTest case 3: Explicitly disable NeoverseN1Errata1542419, check UseDeferredICacheInvalidation");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:-NeoverseN1Errata1542419",
            "-XX:+PrintFlagsFinal",
            "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        String output_str = output.getOutput();
        boolean hasDeferredEnabled = output_str.matches("(?s).*bool\\s+UseDeferredICacheInvalidation\\s*=\\s*true.*");
        boolean hasErrataDisabled = output_str.matches("(?s).*bool\\s+NeoverseN1Errata1542419\\s*=\\s*false.*");

        System.out.println("NeoverseN1Errata1542419 disabled: " + hasErrataDisabled);
        System.out.println("UseDeferredICacheInvalidation enabled: " + hasDeferredEnabled);

        // On affected CPUs, disabling errata should disable deferred invalidation
        if (hasErrataDisabled && hasDeferredEnabled) {
            throw new RuntimeException("On affected CPU, disabling NeoverseN1Errata1542419 should disable UseDeferredICacheInvalidation");
        }
        System.out.println("Correctly synchronized on affected CPU");
    }

    /**
     * Test case 4: Check JVM reports an error if UseDeferredICacheInvalidation is set to true
     * but NeoverseN1Errata1542419 is set to false on affected CPUs.
     */
    private static void testCase4_ConflictingFlagsOnAffectedCPUs() throws Exception {
        System.out.println("\nTest case 4: Try to set UseDeferredICacheInvalidation=true with NeoverseN1Errata1542419=false");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+UseDeferredICacheInvalidation",
            "-XX:-NeoverseN1Errata1542419",
            "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        if (isAffected) {
            // This should fail on affected CPUs (conflicting requirement)
            if (output.getExitValue() != 0) {
                System.out.println("JVM correctly rejected conflicting flags on affected CPU");
                output.shouldContain("Error");
            } else {
                throw new RuntimeException("On affected CPU, conflicting flags should cause error");
            }
        } else {
            // On unaffected CPUs, this should succeed
            output.shouldHaveExitValue(0);
            System.out.println("Note: JVM accepted flags on unaffected CPU (or CPU not Neoverse N1)");
        }
    }

    /**
     * Test case 5: Check explicit NeoverseN1Errata1542419=true enables UseDeferredICacheInvalidation.
     */
    private static void testCase5_ExplicitlyEnableErrataEnablesDeferred() throws Exception {
        if (!isAffected) {
            System.out.println("\nTest case 5: Skipping since CPU is not affected by errata");
            return;
        }

        System.out.println("\nTest case 5: Explicitly enable NeoverseN1Errata1542419, check UseDeferredICacheInvalidation");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+NeoverseN1Errata1542419",
            "-XX:+PrintFlagsFinal",
            "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        String output_str = output.getOutput();
        boolean hasDeferredEnabled = output_str.matches("(?s).*bool\\s+UseDeferredICacheInvalidation\\s*=\\s*true.*");
        boolean hasErrataEnabled = output_str.matches("(?s).*bool\\s+NeoverseN1Errata1542419\\s*=\\s*true.*");

        System.out.println("NeoverseN1Errata1542419 enabled: " + hasErrataEnabled);
        System.out.println("UseDeferredICacheInvalidation enabled: " + hasDeferredEnabled);

        if (hasErrataEnabled && !hasDeferredEnabled) {
            throw new RuntimeException("On affected CPU, enabling NeoverseN1Errata1542419 should enable UseDeferredICacheInvalidation");
        }
        System.out.println("Correctly synchronized on affected CPU");
    }

    /**
     * Test case 6: Check both flags can be explicitly set to false.
     */
    private static void testCase6_ExplicitlyDisableBothFlags() throws Exception {
        System.out.println("\nTest case 6: Explicitly disable both flags");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:-UseDeferredICacheInvalidation",
            "-XX:-NeoverseN1Errata1542419",
            "-XX:+PrintFlagsFinal",
            "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        String output_str = output.getOutput();
        boolean hasDeferredDisabled = output_str.matches("(?s).*bool\\s+UseDeferredICacheInvalidation\\s*=\\s*false.*");
        boolean hasErrataDisabled = output_str.matches("(?s).*bool\\s+NeoverseN1Errata1542419\\s*=\\s*false.*");

        System.out.println("UseDeferredICacheInvalidation disabled: " + hasDeferredDisabled);
        System.out.println("NeoverseN1Errata1542419 disabled: " + hasErrataDisabled);
        if (!hasDeferredDisabled || !hasErrataDisabled) {
            throw new RuntimeException("Both flags should be disabled when explicitly set to false");
        }
        System.out.println("Successfully disabled both flags");
    }

    /**
     * Test case 7: Check UseDeferredICacheInvalidation=false with NeoverseN1Errata1542419=true.
     * On affected CPUs, this should error (conflicting requirement).
     */
    private static void testCase7_ConflictingErrataWithoutDeferred() throws Exception {
        if (!isAffected) {
            System.out.println("\nTest case 7: Skipping since CPU is not affected by errata");
            return;
        }
        System.out.println("\nTest case 7: Try to set NeoverseN1Errata1542419=true with UseDeferredICacheInvalidation=false");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:-UseDeferredICacheInvalidation",
            "-XX:+NeoverseN1Errata1542419",
            "-XX:+PrintFlagsFinal",
            "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        // This should fail on affected CPUs (conflicting requirement)
        if (output.getExitValue() != 0) {
            System.out.println("JVM correctly rejected conflicting flags on affected CPU");
            output.shouldContain("Error");
        } else {
            throw new RuntimeException("On affected CPU, setting NeoverseN1Errata1542419=true with UseDeferredICacheInvalidation=false");
        }
    }

    /**
     * Test case 8: Check setting NeoverseN1Errata1542419=true on unaffected CPU causes an error.
     */
    private static void testCase8_EnablingErrataOnUnaffectedCPU() throws Exception {
        if (isAffected) {
            System.out.println("\nTest case 8: Skipping since CPU is affected by errata");
            return;
        }

        System.out.println("\nTest case 8: Try to set NeoverseN1Errata1542419=true on unaffected CPU");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+NeoverseN1Errata1542419",
            "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        // This should fail on unaffected CPUs (errata not present)
        if (output.getExitValue() != 0) {
            System.out.println("JVM correctly rejected enabling errata flag on unaffected CPU");
            output.shouldContain("Error");
        } else {
            throw new RuntimeException("On unaffected CPU, setting NeoverseN1Errata1542419=true should cause error");
        }
    }
}
