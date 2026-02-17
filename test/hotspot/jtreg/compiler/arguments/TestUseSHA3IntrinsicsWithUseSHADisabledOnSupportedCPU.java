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
 * @summary Verify that UseSHA3Intrinsics is properly disabled when UseSHA is disabled
 *          on supported CPU
 * @library /test/lib /
 * @requires vm.flagless

 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.arguments.TestUseSHA3IntrinsicsWithUseSHADisabledOnSupportedCPU
 */

package compiler.arguments;

import compiler.testlibrary.sha.predicate.IntrinsicPredicates;
import jdk.test.lib.cli.CommandLineOptionTest;
import jdk.test.lib.process.ExitCode;
import jtreg.SkippedException;

public class TestUseSHA3IntrinsicsWithUseSHADisabledOnSupportedCPU {
    private static final String OPTION_NAME = "UseSHA3Intrinsics";
    private static final String MASTER_OPTION = "UseSHA";
    private static final String WARNING_MESSAGE =
        "Intrinsics for SHA3-224, SHA3-256, SHA3-384 and SHA3-512 crypto hash functions not available on this CPU\\.";
    private static final String UNLOCK_DIAGNOSTIC = "-XX:+UnlockDiagnosticVMOptions";

    public static void main(String[] args) throws Throwable {
        if (!IntrinsicPredicates.SHA3_INSTRUCTION_AVAILABLE.getAsBoolean()) {
            throw new SkippedException("Skipping... SHA3 intrinsics are not available on this platform.");
        }

        // Verify that UseSHA3Intrinsics can be explicitly enabled when UseSHA is enabled (default)
        testExplicitEnableWithUseSHAEnabled();

        // Verify that UseSHA3Intrinsics is forced to false when UseSHA is disabled,
        // even if explicitly set to true
        testForcedDisableWhenUseSHADisabled();

        // Verify that a warning is printed when trying to enable UseSHA3Intrinsics
        // while UseSHA is disabled
        testWarningWhenEnablingWithUseSHADisabled();

        // Verify that UseSHA3Intrinsics can be explicitly disabled even when UseSHA is enabled
        testExplicitDisableWithUseSHAEnabled();
    }

    private static void testExplicitEnableWithUseSHAEnabled() throws Throwable {
        // Verify the option value is true when explicitly enabled (with UseSHA enabled by default)
        CommandLineOptionTest.verifyOptionValueForSameVM(
            OPTION_NAME,
            "true",
            "UseSHA3Intrinsics should be enabled when explicitly set to true with UseSHA enabled",
            UNLOCK_DIAGNOSTIC,
            CommandLineOptionTest.prepareBooleanFlag(OPTION_NAME, true)
        );

        // Verify no warning is printed when enabling UseSHA3Intrinsics with UseSHA enabled
        CommandLineOptionTest.verifySameJVMStartup(
            null,  // No specific output expected
            new String[] { WARNING_MESSAGE },  // Warning should not appear
            "No warning should be printed when enabling UseSHA3Intrinsics with UseSHA enabled",
            "UseSHA3Intrinsics should be enabled without warnings when UseSHA is enabled",
            ExitCode.OK,
            UNLOCK_DIAGNOSTIC,
            CommandLineOptionTest.prepareBooleanFlag(OPTION_NAME, true)
        );
    }

    private static void testForcedDisableWhenUseSHADisabled() throws Throwable {
        // When -XX:-UseSHA is set, UseSHA3Intrinsics should be forced to false
        // even if +UseSHA3Intrinsics is explicitly passed
        CommandLineOptionTest.verifyOptionValueForSameVM(
            OPTION_NAME,
            "false",
            String.format("UseSHA3Intrinsics should be forced to false when %s is set, " +
                         "even if explicitly enabled",
                         CommandLineOptionTest.prepareBooleanFlag(MASTER_OPTION, false)),
            UNLOCK_DIAGNOSTIC,
            CommandLineOptionTest.prepareBooleanFlag(OPTION_NAME, true),
            CommandLineOptionTest.prepareBooleanFlag(MASTER_OPTION, false)
        );
    }

    private static void testWarningWhenEnablingWithUseSHADisabled() throws Throwable {
        // A warning should be printed when trying to enable UseSHA3Intrinsics with -UseSHA
        CommandLineOptionTest.verifySameJVMStartup(
            new String[] { WARNING_MESSAGE },  // Warning should appear
            null,  // No unexpected output
            "JVM should start successfully",
            String.format("A warning should be printed when trying to enable %s while %s is disabled",
                         OPTION_NAME,
                         MASTER_OPTION),
            ExitCode.OK,
            UNLOCK_DIAGNOSTIC,
            CommandLineOptionTest.prepareBooleanFlag(MASTER_OPTION, false),
            CommandLineOptionTest.prepareBooleanFlag(OPTION_NAME, true)
        );
    }

    private static void testExplicitDisableWithUseSHAEnabled() throws Throwable {
        // Verify that UseSHA3Intrinsics can be explicitly disabled even when UseSHA is enabled
        CommandLineOptionTest.verifyOptionValueForSameVM(
            OPTION_NAME,
            "false",
            "UseSHA3Intrinsics should be disabled when explicitly set to false",
            UNLOCK_DIAGNOSTIC,
            CommandLineOptionTest.prepareBooleanFlag(MASTER_OPTION, true),
            CommandLineOptionTest.prepareBooleanFlag(OPTION_NAME, false)
        );

        // Verify no warning is printed when explicitly disabling UseSHA3Intrinsics
        CommandLineOptionTest.verifySameJVMStartup(
            null,  // No specific output expected
            new String[] { WARNING_MESSAGE },  // Warning should not appear
            "No warning should be printed when explicitly disabling UseSHA3Intrinsics",
            "UseSHA3Intrinsics should be disabled without warnings",
            ExitCode.OK,
            UNLOCK_DIAGNOSTIC,
            CommandLineOptionTest.prepareBooleanFlag(MASTER_OPTION, true),
            CommandLineOptionTest.prepareBooleanFlag(OPTION_NAME, false)
        );
    }
}
