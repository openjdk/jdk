/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

package compiler.intrinsics.sha.cli.testcases;

import compiler.intrinsics.sha.cli.SHAOptionsBase;
import jdk.test.lib.process.ExitCode;
import jdk.test.lib.Platform;
import jdk.test.lib.cli.CommandLineOptionTest;
import jdk.test.lib.cli.predicate.AndPredicate;
import jdk.test.lib.cli.predicate.NotPredicate;

/**
 * Generic test case for SHA-related options targeted to SPARC CPUs which don't
 * support instruction required by the tested option.
 */
public class GenericTestCaseForUnsupportedSparcCPU extends
        SHAOptionsBase.TestCase {
    public GenericTestCaseForUnsupportedSparcCPU(String optionName) {
        super(optionName, new AndPredicate(Platform::isSparc,
                new NotPredicate(SHAOptionsBase.getPredicateForOption(
                        optionName))));
    }

    @Override
    protected void verifyWarnings() throws Throwable {
        String shouldPassMessage = String.format("JVM startup should pass with"
                + "option '-XX:-%s' without any warnings", optionName);
        //Verify that option could be disabled without any warnings.
        CommandLineOptionTest.verifySameJVMStartup(null, new String[] {
                        SHAOptionsBase.getWarningForUnsupportedCPU(optionName)
                }, shouldPassMessage, shouldPassMessage, ExitCode.OK,
                SHAOptionsBase.UNLOCK_DIAGNOSTIC_VM_OPTIONS,
                CommandLineOptionTest.prepareBooleanFlag(optionName, false));

        // Verify that when the tested option is enabled, then
        // a warning will occur in VM output if UseSHA is disabled.
        if (!optionName.equals(SHAOptionsBase.USE_SHA_OPTION)) {
            CommandLineOptionTest.verifySameJVMStartup(
                    new String[] { SHAOptionsBase.getWarningForUnsupportedCPU(optionName) },
                    null,
                    shouldPassMessage,
                    shouldPassMessage,
                    ExitCode.OK,
                    SHAOptionsBase.UNLOCK_DIAGNOSTIC_VM_OPTIONS,
                    CommandLineOptionTest.prepareBooleanFlag(SHAOptionsBase.USE_SHA_OPTION, false),
                    CommandLineOptionTest.prepareBooleanFlag(optionName, true));
        }
    }

    @Override
    protected void verifyOptionValues() throws Throwable {
        // Verify that option is disabled by default.
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName, "false",
                String.format("Option '%s' should be disabled by default",
                        optionName),
                SHAOptionsBase.UNLOCK_DIAGNOSTIC_VM_OPTIONS);

        // Verify that option is disabled even if it was explicitly enabled
        // using CLI options.
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName, "false",
                String.format("Option '%s' should be off on unsupported "
                        + "SparcCPU even if set to true directly", optionName),
                SHAOptionsBase.UNLOCK_DIAGNOSTIC_VM_OPTIONS,
                CommandLineOptionTest.prepareBooleanFlag(optionName, true));

        // Verify that option is disabled when +UseSHA was passed to JVM.
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName, "false",
                String.format("Option '%s' should be off on unsupported "
                        + "SparcCPU even if %s flag set to JVM",
                        optionName, CommandLineOptionTest.prepareBooleanFlag(
                            SHAOptionsBase.USE_SHA_OPTION, true)),
                SHAOptionsBase.UNLOCK_DIAGNOSTIC_VM_OPTIONS,
                CommandLineOptionTest.prepareBooleanFlag(
                        SHAOptionsBase.USE_SHA_OPTION, true));
    }
}
