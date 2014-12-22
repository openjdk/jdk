/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.java.testlibrary.Asserts;
import com.oracle.java.testlibrary.ExitCode;
import com.oracle.java.testlibrary.Platform;
import com.oracle.java.testlibrary.cli.CommandLineOptionTest;
import com.oracle.java.testlibrary.cli.predicate.AndPredicate;
import com.oracle.java.testlibrary.cli.predicate.NotPredicate;
import sha.predicate.IntrinsicPredicates;

/**
 * UseSHA specific test case targeted to SPARC CPUs which don't support all sha*
 * instructions.
 */
public class UseSHASpecificTestCaseForUnsupportedSparcCPU
        extends SHAOptionsBase.TestCase {
    public UseSHASpecificTestCaseForUnsupportedSparcCPU(String optionName) {
        super(SHAOptionsBase.USE_SHA_OPTION, new AndPredicate(Platform::isSparc,
                new NotPredicate(
                        IntrinsicPredicates.ANY_SHA_INSTRUCTION_AVAILABLE)));

        Asserts.assertEQ(optionName, SHAOptionsBase.USE_SHA_OPTION,
                "Test case should be used for " + SHAOptionsBase.USE_SHA_OPTION
                        + " option only.");
    }

    @Override
    protected void verifyWarnings() throws Throwable {
        // Verify that attempt to use UseSHA option will cause a warning.
        String shouldPassMessage = String.format("JVM startup should pass with"
                + " '%s' option on unsupported SparcCPU, but there should be"
                + "the message shown.", optionName);
        CommandLineOptionTest.verifySameJVMStartup(new String[] {
                        SHAOptionsBase.getWarningForUnsupportedCPU(optionName)
                }, null, shouldPassMessage, shouldPassMessage, ExitCode.OK,
                CommandLineOptionTest.prepareBooleanFlag(optionName, true));
    }

    @Override
    protected void verifyOptionValues() throws Throwable {
        // Verify that UseSHA option remains disabled even if all
        // UseSHA*Intrinsics were enabled.
        CommandLineOptionTest.verifyOptionValueForSameVM(
                SHAOptionsBase.USE_SHA_OPTION, "false", String.format(
                    "%s option should be disabled on unsupported SparcCPU"
                        + " even if all UseSHA*Intrinsics options were enabled.",
                    SHAOptionsBase.USE_SHA_OPTION),
                CommandLineOptionTest.prepareBooleanFlag(
                        SHAOptionsBase.USE_SHA1_INTRINSICS_OPTION, true),
                CommandLineOptionTest.prepareBooleanFlag(
                        SHAOptionsBase.USE_SHA256_INTRINSICS_OPTION, true),
                CommandLineOptionTest.prepareBooleanFlag(
                        SHAOptionsBase.USE_SHA512_INTRINSICS_OPTION, true));

        // Verify that UseSHA option remains disabled even if all
        // UseSHA*Intrinsics options were enabled and UseSHA was enabled as well.
        CommandLineOptionTest.verifyOptionValueForSameVM(
                SHAOptionsBase.USE_SHA_OPTION, "false", String.format(
                    "%s option should be disabled on unsupported SparcCPU"
                        + " even if all UseSHA*Intrinsics options were enabled"
                        + " and %s was enabled as well",
                    SHAOptionsBase.USE_SHA_OPTION,
                    SHAOptionsBase.USE_SHA_OPTION),
                CommandLineOptionTest.prepareBooleanFlag(
                        SHAOptionsBase.USE_SHA_OPTION, true),
                CommandLineOptionTest.prepareBooleanFlag(
                        SHAOptionsBase.USE_SHA1_INTRINSICS_OPTION, true),
                CommandLineOptionTest.prepareBooleanFlag(
                        SHAOptionsBase.USE_SHA256_INTRINSICS_OPTION, true),
                CommandLineOptionTest.prepareBooleanFlag(
                        SHAOptionsBase.USE_SHA512_INTRINSICS_OPTION, true));
    }
}
