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
import com.oracle.java.testlibrary.cli.predicate.OrPredicate;

/**
 * Generic test case for SHA-related options targeted to X86 CPUs that don't
 * support SHA-related instructions.
 */
public class GenericTestCaseForUnsupportedX86CPU
        extends SHAOptionsBase.TestCase {
    public GenericTestCaseForUnsupportedX86CPU(String optionName) {
        super(optionName, new OrPredicate(Platform::isX64, Platform::isX86));
    }

    @Override
    protected void verifyWarnings() throws Throwable {
        // Verify that when the tested option is explicitly enabled, then
        // a warning will occur in VM output.
        CommandLineOptionTest.verifySameJVMStartup(new String[] {
                        SHAOptionsBase.getWarningForUnsupportedCPU(optionName)
                }, null, ExitCode.OK,
                CommandLineOptionTest.prepareBooleanFlag(optionName, true));

        // Verify that the tested option could be explicitly disabled without
        // a warning.
        CommandLineOptionTest.verifySameJVMStartup(null, new String[] {
                        SHAOptionsBase.getWarningForUnsupportedCPU(optionName)
                }, ExitCode.OK,
                CommandLineOptionTest.prepareBooleanFlag(optionName, false));
    }

    @Override
    protected void verifyOptionValues() throws Throwable {
        // Verify that the tested option is disabled by default.
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName, "false");

        // Verify that it is not possible to explicitly enable the option.
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName, "false",
                CommandLineOptionTest.prepareBooleanFlag(optionName, true));

        // Verify that the tested option is disabled even if +UseSHA was passed
        // to JVM.
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName, "false",
                CommandLineOptionTest.prepareBooleanFlag(
                        SHAOptionsBase.USE_SHA_OPTION, true));
    }
}
