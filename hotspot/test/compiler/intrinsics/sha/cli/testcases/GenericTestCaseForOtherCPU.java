/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.ExitCode;
import jdk.test.lib.Platform;
import jdk.test.lib.cli.CommandLineOptionTest;
import jdk.test.lib.cli.predicate.NotPredicate;
import jdk.test.lib.cli.predicate.OrPredicate;

/**
 * Generic test case for SHA-related options targeted to non-x86 and
 * non-SPARC CPUs.
 */
public class GenericTestCaseForOtherCPU extends
        SHAOptionsBase.TestCase {
    public GenericTestCaseForOtherCPU(String optionName) {
        // Execute the test case on any CPU except SPARC and X86
        super(optionName, new NotPredicate(
                new OrPredicate(
                    new OrPredicate(Platform::isSparc, Platform::isAArch64),
                    new OrPredicate(Platform::isX64, Platform::isX86))));
    }

    @Override
    protected void verifyWarnings() throws Throwable {
        String shouldPassMessage = String.format("JVM should start with "
                + "option '%s' without any warnings", optionName);
        // Verify that on non-x86, non-SPARC and non-AArch64 CPU usage of
        //  SHA-related options will not cause any warnings.
        CommandLineOptionTest.verifySameJVMStartup(null,
                new String[] { ".*" + optionName + ".*" }, shouldPassMessage,
                shouldPassMessage, ExitCode.OK,
                CommandLineOptionTest.prepareBooleanFlag(optionName, true));

        CommandLineOptionTest.verifySameJVMStartup(null,
                new String[] { ".*" + optionName + ".*" }, shouldPassMessage,
                shouldPassMessage, ExitCode.OK,
                CommandLineOptionTest.prepareBooleanFlag(optionName, false));
    }

    @Override
    protected void verifyOptionValues() throws Throwable {
        // Verify that option is disabled by default.
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName, "false",
                String.format("Option '%s' should be disabled by default",
                        optionName));

        // Verify that option is disabled even if it was explicitly enabled
        // using CLI options.
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName, "false",
                String.format("Option '%s' should be off on unsupported "
                        + "CPU even if set to true directly", optionName),
                CommandLineOptionTest.prepareBooleanFlag(optionName, true));

        // Verify that option is disabled when it explicitly disabled
        // using CLI options.
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName, "false",
                String.format("Option '%s' should be off on unsupported CPU"
                        + " even if '%s' flag set to JVM", optionName,
                        CommandLineOptionTest.prepareBooleanFlag(
                        SHAOptionsBase.USE_SHA_OPTION, true)),
                CommandLineOptionTest.prepareBooleanFlag(optionName, false));
    }
}
