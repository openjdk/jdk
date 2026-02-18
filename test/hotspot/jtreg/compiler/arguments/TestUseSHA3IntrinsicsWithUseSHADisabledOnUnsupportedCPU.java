/*
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
 */

/**
 * @test
 * @bug 8375443
 * @summary Verify that UseSHA3Intrinsics is properly disabled with warnings
 *          on unsupported CPU.
 * @library /test/lib /
 * @requires vm.flagless
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.arguments.TestUseSHA3IntrinsicsWithUseSHADisabledOnUnsupportedCPU
 */

package compiler.arguments;

import compiler.testlibrary.sha.predicate.IntrinsicPredicates;
import jdk.test.lib.cli.CommandLineOptionTest;
import jdk.test.lib.process.ExitCode;
import jtreg.SkippedException;

public class TestUseSHA3IntrinsicsWithUseSHADisabledOnUnsupportedCPU {
    private static final String OPTION_NAME = "UseSHA3Intrinsics";
    private static final String MASTER_OPTION = "UseSHA";
    private static final String WARNING_MESSAGE =
        "Intrinsics for SHA3-224, SHA3-256, SHA3-384 and SHA3-512 crypto hash functions not available on this CPU\\.";
    private static final String UNLOCK_DIAGNOSTIC = "-XX:+UnlockDiagnosticVMOptions";

    public static void main(String[] args) throws Throwable {
        if (IntrinsicPredicates.SHA3_INSTRUCTION_AVAILABLE.getAsBoolean()) {
            throw new SkippedException("Skipping... SHA3 intrinsics are available on this platform.");
        }

        // Verify that UseSHA3Intrinsics remains false when UseSHA is enabled
        // but CPU doesn't support the instructions
        testRemainsDisabledWithUseSHAEnabled();

        // Verify that explicitly disabling UseSHA3Intrinsics works without warnings
        testExplicitDisableWithoutWarning();

        // Verify behavior with both -XX:-UseSHA and +XX:+UseSHA3Intrinsics
        testWithUseSHADisabled();
    }

    private static void testRemainsDisabledWithUseSHAEnabled() throws Throwable {
        // Even with +UseSHA, UseSHA3Intrinsics should remain false on unsupported CPU
        CommandLineOptionTest.verifyOptionValueForSameVM(
            OPTION_NAME,
            "false",
            "UseSHA3Intrinsics should remain false even when UseSHA is enabled on unsupported CPU",
            UNLOCK_DIAGNOSTIC,
            CommandLineOptionTest.prepareBooleanFlag(MASTER_OPTION, true)
        );

        // Trying to enable both should still produce a warning
        CommandLineOptionTest.verifySameJVMStartup(
            new String[] { WARNING_MESSAGE },
            null,
            "JVM should start with a warning",
            "Warning should be printed even when UseSHA is explicitly enabled",
            ExitCode.OK,
            UNLOCK_DIAGNOSTIC,
            CommandLineOptionTest.prepareBooleanFlag(MASTER_OPTION, true),
            CommandLineOptionTest.prepareBooleanFlag(OPTION_NAME, true)
        );
    }

    private static void testExplicitDisableWithoutWarning() throws Throwable {
        // Explicitly disabling should not produce any warnings
        CommandLineOptionTest.verifySameJVMStartup(
            null,  // No specific output expected
            new String[] { WARNING_MESSAGE },  // Warning should NOT appear
            "JVM should start without warnings",
            "No warning should be printed when explicitly disabling UseSHA3Intrinsics",
            ExitCode.OK,
            UNLOCK_DIAGNOSTIC,
            CommandLineOptionTest.prepareBooleanFlag(OPTION_NAME, false)
        );

        // Verify the flag value is false
        CommandLineOptionTest.verifyOptionValueForSameVM(
            OPTION_NAME,
            "false",
            "UseSHA3Intrinsics should be false when explicitly disabled",
            UNLOCK_DIAGNOSTIC,
            CommandLineOptionTest.prepareBooleanFlag(OPTION_NAME, false)
        );
    }

    private static void testWithUseSHADisabled() throws Throwable {
        // When UseSHA is disabled, UseSHA3Intrinsics should also be disabled
        // and a warning should be printed
        CommandLineOptionTest.verifySameJVMStartup(
            new String[] { WARNING_MESSAGE },
            null,
            "JVM should start with a warning",
            String.format("Warning should be printed when trying to enable %s while %s is disabled on unsupported CPU",
                         OPTION_NAME,
                         MASTER_OPTION),
            ExitCode.OK,
            UNLOCK_DIAGNOSTIC,
            CommandLineOptionTest.prepareBooleanFlag(MASTER_OPTION, false),
            CommandLineOptionTest.prepareBooleanFlag(OPTION_NAME, true)
        );

        // Verify the flag is forced to false
        CommandLineOptionTest.verifyOptionValueForSameVM(
            OPTION_NAME,
            "false",
            String.format("UseSHA3Intrinsics should be false when %s is disabled on unsupported CPU",
                         MASTER_OPTION),
            UNLOCK_DIAGNOSTIC,
            CommandLineOptionTest.prepareBooleanFlag(MASTER_OPTION, false),
            CommandLineOptionTest.prepareBooleanFlag(OPTION_NAME, true)
        );

        // Test that with both flags disabled, no warning appears
        CommandLineOptionTest.verifySameJVMStartup(
            null,
            new String[] { WARNING_MESSAGE },
            "JVM should start without warnings",
            "No warning when both UseSHA and UseSHA3Intrinsics are disabled",
            ExitCode.OK,
            UNLOCK_DIAGNOSTIC,
            CommandLineOptionTest.prepareBooleanFlag(MASTER_OPTION, false),
            CommandLineOptionTest.prepareBooleanFlag(OPTION_NAME, false)
        );
    }
}
