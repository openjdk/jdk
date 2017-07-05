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

import com.oracle.java.testlibrary.ExitCode;
import com.oracle.java.testlibrary.Platform;
import com.oracle.java.testlibrary.cli.CommandLineOptionTest;
import com.oracle.java.testlibrary.cli.predicate.AndPredicate;
import com.oracle.java.testlibrary.cli.predicate.NotPredicate;

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
        //Verify that option could be disabled without any warnings.
        CommandLineOptionTest.verifySameJVMStartup(null, new String[] {
                        SHAOptionsBase.getWarningForUnsupportedCPU(optionName)
                }, ExitCode.OK,
                CommandLineOptionTest.prepareBooleanFlag(optionName, false));
    }

    @Override
    protected void verifyOptionValues() throws Throwable {
        // Verify that option is disabled by default.
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName, "false");

        // Verify that option is disabled even if it was explicitly enabled
        // using CLI options.
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName, "false",
                CommandLineOptionTest.prepareBooleanFlag(optionName, true));

        // Verify that option is disabled when +UseSHA was passed to JVM.
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName, "false",
                CommandLineOptionTest.prepareBooleanFlag(
                        SHAOptionsBase.USE_SHA_OPTION, true));
    }
}
