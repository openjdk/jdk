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

package compiler.runtime;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

/*
 * @test
 * @bug 8370947 8381003
 * @summary Test command-line options for UseSingleICacheInvalidation and NeoverseN1ICacheErratumMitigation
 * @library /test/lib
 * @requires os.arch == "aarch64"
 * @requires os.family == "linux"
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.runtime.TestDeferredICacheInvalidationCmdOptions
 */

public class TestDeferredICacheInvalidationCmdOptions {

    // CPU identifiers
    private static final int CPU_ARM = 0x41;
    private static final int NEOVERSE_N1_MODEL = 0xd0c;

    // Known ARM Neoverse models where we can predict UseSingleICacheInvalidation
    // behavior.
    private static final int[] KNOWN_NEOVERSE_MODELS = {
        NEOVERSE_N1_MODEL,
        0xd40, // Neoverse V1
        0xd49, // Neoverse N2
        0xd4f, // Neoverse V2
        0xd83, // Neoverse V3AE
        0xd84, // Neoverse V3
        0xd8e, // Neoverse N3
    };

    private static boolean isAffected;
    private static boolean isKnownModel;

    public static void main(String[] args) throws Exception {
        // This test does not depend on CPU identification — run it first.
        testDisableBothFlags();

        // Parse CPU features and print CPU info
        parseCPUFeatures();

        if (!isKnownModel) {
            throw new SkippedException("Unknown CPU model - skipping remaining tests.");
        }

        if (isAffected) {
            // Detect whether IC IVAU is trapped on this system.
            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+NeoverseN1ICacheErratumMitigation",
                "-version");
            OutputAnalyzer output = new OutputAnalyzer(pb.start());

            if (output.getExitValue() != 0) {
                // Verify the failure is the expected probe error, not something else.
                output.shouldContain("IC IVAU is not trapped");
                throw new SkippedException("IC IVAU is not trapped - skipping remaining tests.");
            } else {
                System.out.println("IC IVAU trap active.");
            }
        }

        if (isAffected) {
            // Check defaults on Neoverse N1 pre-r4p1
            testCase_DefaultsOnNeoverseN1();

            // Check if NeoverseN1ICacheErratumMitigation is set to false on affected CPUs,
            // UseSingleICacheInvalidation is also set to false
            testCase_ExplicitlyDisableErrataAffectsDeferred();

            // Check JVM error if UseSingleICacheInvalidation=true
            // but NeoverseN1ICacheErratumMitigation=false on affected CPUs
            testCase_ConflictingFlagsOnAffectedCPUs();

            // Check explicit NeoverseN1ICacheErratumMitigation=true enables UseSingleICacheInvalidation
            testCase_ExplicitlyEnableErrataEnablesDeferred();

            // Check UseSingleICacheInvalidation=false with NeoverseN1ICacheErratumMitigation=true
            testCase_ConflictingErrataWithoutDeferred();
        } else {
            // Check NeoverseN1ICacheErratumMitigation is false on unaffected CPUs
            testCase_DefaultsOnUnaffectedCPUs();

            // Check setting NeoverseN1ICacheErratumMitigation=true on unaffected CPU causes an error
            testCase_EnablingErrataOnUnaffectedCPU();
        }

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
     * Sets the static fields isAffected and isKnownModel, and prints CPU info.
     *
     * Format: 0x%02x:0x%x:0x%03x:%d[(0x%03x)]
     * Example: "0x41:0x3:0xd0c:0" or "0x41:0x3:0xd0c:0(0xd0c)"
     *
     * @throws SkippedException if not running on ARM CPU
     */
    private static void parseCPUFeatures() {
        WhiteBox wb = WhiteBox.getWhiteBox();
        String cpuFeatures = wb.getCPUFeatures();
        System.out.println("CPU Features string: " + cpuFeatures);

        if (cpuFeatures == null || cpuFeatures.isEmpty()) {
            throw new RuntimeException("No CPU features available");
        }

        int commaIndex = cpuFeatures.indexOf(",");
        if (commaIndex == -1) {
            throw new RuntimeException("Unexpected CPU features format (no comma): " + cpuFeatures);
        }

        String cpuPart = cpuFeatures.substring(0, commaIndex).trim();

        String[] parts = cpuPart.split(":");
        if (parts.length < 4) {
            throw new RuntimeException("Unexpected CPU features format: " + cpuPart);
        }

        int cpuFamily = Integer.parseInt(parts[0].substring(2), 16);
        if (cpuFamily != CPU_ARM) {
            throw new SkippedException("Not running on ARM CPU (cpuFamily=0x" + Integer.toHexString(cpuFamily) + ")");
        }

        int cpuVariant = Integer.parseInt(parts[1].substring(2), 16);
        int cpuModel = Integer.parseInt(parts[2].substring(2), 16);
        int cpuModel2 = 0;

        int model2Start = parts[3].indexOf("(");
        String revisionStr = parts[3];
        if (model2Start != -1) {
            if (!parts[3].endsWith(")")) {
                throw new RuntimeException("Unexpected CPU features format (missing closing parenthesis): " + parts[3]);
            }
            String model2Str = parts[3].substring(model2Start + 1, parts[3].length() - 1);
            cpuModel2 = Integer.parseInt(model2Str.substring(2), 16);
            revisionStr = parts[3].substring(0, model2Start);
        }
        int cpuRevision = Integer.parseInt(revisionStr);

        // Neoverse N1 errata 1542419 affects r3p0, r3p1 and r4p0.
        // It is fixed in r4p1 and later revisions.
        if (cpuModel == NEOVERSE_N1_MODEL || cpuModel2 == NEOVERSE_N1_MODEL) {
            isAffected = (cpuVariant == 3 && cpuRevision == 0) ||
                         (cpuVariant == 3 && cpuRevision == 1) ||
                         (cpuVariant == 4 && cpuRevision == 0);
        }

        // Check if this is a known Neoverse model.
        isKnownModel = false;
        for (int model : KNOWN_NEOVERSE_MODELS) {
            if (cpuModel == model || cpuModel2 == model) {
                isKnownModel = true;
                break;
            }
        }

        printCPUInfo(cpuFamily, cpuVariant, cpuModel, cpuModel2, cpuRevision);
    }

    private static void printCPUInfo(int cpuFamily, int cpuVariant, int cpuModel, int cpuModel2, int cpuRevision) {
        boolean isNeoverseN1 = (cpuFamily == CPU_ARM) &&
                               (cpuModel == NEOVERSE_N1_MODEL || cpuModel2 == NEOVERSE_N1_MODEL);
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
        System.out.println("Is known model: " + isKnownModel);
        System.out.println("======================\n");
    }

    /**
     * Test that UseSingleICacheInvalidation and NeoverseN1ICacheErratumMitigation flags
     * can be explicitly set to false on any system.
     */
    private static void testDisableBothFlags() throws Exception {
        System.out.println("\nTest: Explicitly disable both flags");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:-UseSingleICacheInvalidation",
            "-XX:-NeoverseN1ICacheErratumMitigation",
            "-XX:+PrintFlagsFinal",
            "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        String output_str = output.getOutput();
        boolean hasSingleDisabled = output_str.matches("(?s).*bool\\s+UseSingleICacheInvalidation\\s*=\\s*false.*");
        boolean hasErrataDisabled = output_str.matches("(?s).*bool\\s+NeoverseN1ICacheErratumMitigation\\s*=\\s*false.*");

        System.out.println("UseSingleICacheInvalidation disabled: " + hasSingleDisabled);
        System.out.println("NeoverseN1ICacheErratumMitigation disabled: " + hasErrataDisabled);

        if (!hasErrataDisabled) {
            throw new RuntimeException("Failed to disable NeoverseN1ICacheErratumMitigation via command line");
        }

        if (!hasSingleDisabled) {
            throw new RuntimeException("Failed to disable UseSingleICacheInvalidation via command line");
        }

        System.out.println("Successfully disabled both flags");
    }

    /**
     * Check defaults on Neoverse N1 affected revisions.
     * UseSingleICacheInvalidation and NeoverseN1ICacheErratumMitigation flags should be true.
     */
    private static void testCase_DefaultsOnNeoverseN1() throws Exception {
        System.out.println("\nTest: Check defaults on Neoverse N1 affected revisions");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+PrintFlagsFinal",
            "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        String output_str = output.getOutput();
        boolean hasSingleEnabled = output_str.matches("(?s).*bool\\s+UseSingleICacheInvalidation\\s*=\\s*true.*");
        boolean hasErrataEnabled = output_str.matches("(?s).*bool\\s+NeoverseN1ICacheErratumMitigation\\s*=\\s*true.*");

        System.out.println("UseSingleICacheInvalidation enabled: " + hasSingleEnabled);
        System.out.println("NeoverseN1ICacheErratumMitigation enabled: " + hasErrataEnabled);

        if (!hasSingleEnabled || !hasErrataEnabled) {
            throw new RuntimeException("On affected Neoverse N1 with trap active, " +
                 "UseSingleICacheInvalidation and NeoverseN1ICacheErratumMitigation flags should be enabled by default");
        }
        System.out.println("Correctly enabled on affected Neoverse N1");
    }

    /**
     * Check NeoverseN1ICacheErratumMitigation is false on unaffected CPUs.
     */
    private static void testCase_DefaultsOnUnaffectedCPUs() throws Exception {
        System.out.println("\nTest: Check NeoverseN1ICacheErratumMitigation is false on unaffected CPUs");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+PrintFlagsFinal",
            "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        String output_str = output.getOutput();
        boolean hasErrataEnabled = output_str.matches("(?s).*bool\\s+NeoverseN1ICacheErratumMitigation\\s*=\\s*true.*");

        System.out.println("NeoverseN1ICacheErratumMitigation enabled: " + hasErrataEnabled);

        if (hasErrataEnabled) {
            throw new RuntimeException("On unaffected CPUs, NeoverseN1ICacheErratumMitigation should be disabled");
        }
        System.out.println("Correctly disabled on unaffected CPU");
    }

    /**
     * Check if NeoverseN1ICacheErratumMitigation is set to false via cmd on affected CPUs,
     * UseSingleICacheInvalidation is set to false.
     */
    private static void testCase_ExplicitlyDisableErrataAffectsDeferred() throws Exception {
        System.out.println("\nTest: Explicitly disable NeoverseN1ICacheErratumMitigation, check UseSingleICacheInvalidation");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:-NeoverseN1ICacheErratumMitigation",
            "-XX:+PrintFlagsFinal",
            "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        String output_str = output.getOutput();
        boolean hasSingleDisabled = output_str.matches("(?s).*bool\\s+UseSingleICacheInvalidation\\s*=\\s*false.*");
        boolean hasErrataDisabled = output_str.matches("(?s).*bool\\s+NeoverseN1ICacheErratumMitigation\\s*=\\s*false.*");

        System.out.println("NeoverseN1ICacheErratumMitigation disabled: " + hasErrataDisabled);
        System.out.println("UseSingleICacheInvalidation disabled: " + hasSingleDisabled);

        if (!hasErrataDisabled) {
            throw new RuntimeException("Failed to disable NeoverseN1ICacheErratumMitigation via command line");
        }

        if (!hasSingleDisabled) {
            throw new RuntimeException("On affected CPU, disabling NeoverseN1ICacheErratumMitigation should also disable UseSingleICacheInvalidation");
        }
        System.out.println("Correctly synchronized on affected CPU");
    }

    /**
     * Check JVM reports an error if UseSingleICacheInvalidation is set to true
     * but NeoverseN1ICacheErratumMitigation is set to false on affected CPUs.
     */
    private static void testCase_ConflictingFlagsOnAffectedCPUs() throws Exception {
        System.out.println("\nTest: Try to set UseSingleICacheInvalidation=true with NeoverseN1ICacheErratumMitigation=false");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+UseSingleICacheInvalidation",
            "-XX:-NeoverseN1ICacheErratumMitigation",
            "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        if (output.getExitValue() == 0) {
            throw new RuntimeException("On affected CPU, setting UseSingleICacheInvalidation=true " +
                "with NeoverseN1ICacheErratumMitigation=false should cause error");
        }
        output.shouldContain("Error");
        System.out.println("JVM correctly rejected conflicting flags on affected CPU");
    }

    /**
     * Check explicit NeoverseN1ICacheErratumMitigation=true enables UseSingleICacheInvalidation.
     */
    private static void testCase_ExplicitlyEnableErrataEnablesDeferred() throws Exception {
        System.out.println("\nTest: Explicitly enable NeoverseN1ICacheErratumMitigation, check UseSingleICacheInvalidation");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+NeoverseN1ICacheErratumMitigation",
            "-XX:+PrintFlagsFinal",
            "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        String output_str = output.getOutput();
        boolean hasSingleEnabled = output_str.matches("(?s).*bool\\s+UseSingleICacheInvalidation\\s*=\\s*true.*");
        boolean hasErrataEnabled = output_str.matches("(?s).*bool\\s+NeoverseN1ICacheErratumMitigation\\s*=\\s*true.*");

        System.out.println("NeoverseN1ICacheErratumMitigation enabled: " + hasErrataEnabled);
        System.out.println("UseSingleICacheInvalidation enabled: " + hasSingleEnabled);

        if (!hasErrataEnabled) {
            throw new RuntimeException("Failed to enable NeoverseN1ICacheErratumMitigation via command line");
        }

        if (!hasSingleEnabled) {
            throw new RuntimeException("On affected CPU, enabling NeoverseN1ICacheErratumMitigation should also enable UseSingleICacheInvalidation");
        }
        System.out.println("Correctly synchronized on affected CPU");
    }

    /**
     * Check JVM reports an error if UseSingleICacheInvalidation is set to false
     * and NeoverseN1ICacheErratumMitigation is set to true on affected CPUs.
     */
    private static void testCase_ConflictingErrataWithoutDeferred() throws Exception {
        System.out.println("\nTest: Try to set NeoverseN1ICacheErratumMitigation=true with UseSingleICacheInvalidation=false");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:-UseSingleICacheInvalidation",
            "-XX:+NeoverseN1ICacheErratumMitigation",
            "-XX:+PrintFlagsFinal",
            "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        if (output.getExitValue() == 0) {
            throw new RuntimeException("On affected CPU, setting NeoverseN1ICacheErratumMitigation=true with UseSingleICacheInvalidation=false should cause an error");
        }
        output.shouldContain("Error");
        System.out.println("JVM correctly rejected conflicting flags on affected CPU");
    }

    /**
     * Check setting NeoverseN1ICacheErratumMitigation=true on unaffected CPU causes an error.
     */
    private static void testCase_EnablingErrataOnUnaffectedCPU() throws Exception {
        System.out.println("\nTest: Try to set NeoverseN1ICacheErratumMitigation=true on unaffected CPU");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+NeoverseN1ICacheErratumMitigation",
            "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        if (output.getExitValue() == 0) {
            throw new RuntimeException("On unaffected CPU, setting NeoverseN1ICacheErratumMitigation=true should cause error");
        }
        output.shouldContain("Error");
        System.out.println("JVM correctly rejected enabling errata flag on unaffected CPU");
    }
}
