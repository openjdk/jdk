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
 *
 */

import com.oracle.java.testlibrary.*;
import com.oracle.java.testlibrary.cli.*;

import java.util.function.BooleanSupplier;

public abstract class TestPrintPreciseRTMLockingStatisticsBase
        extends RTMGenericCommandLineOptionTest {
    protected static final String DEFAULT_VALUE = "false";

    protected TestPrintPreciseRTMLockingStatisticsBase(
            BooleanSupplier predicate) {
        super(predicate, "PrintPreciseRTMLockingStatistics", true, false,
                TestPrintPreciseRTMLockingStatisticsBase.DEFAULT_VALUE);
    }

    @Override
    protected void runNonX86TestCases() throws Throwable {
        verifyJVMStartup();
        verifyOptionValues();
    }

    @Override
    protected void verifyJVMStartup() throws Throwable {
        if (Platform.isServer()) {
            if (!Platform.isDebugBuild()) {
                String errorMessage = CommandLineOptionTest.
                        getDiagnosticOptionErrorMessage(optionName);
                // verify that option is actually diagnostic
                CommandLineOptionTest.verifySameJVMStartup(
                        new String[] { errorMessage }, null, ExitCode.FAIL,
                        prepareOptionValue("true"));

                CommandLineOptionTest.verifySameJVMStartup(null,
                        new String[] { errorMessage }, ExitCode.OK,
                        CommandLineOptionTest.UNLOCK_DIAGNOSTIC_VM_OPTIONS,
                        prepareOptionValue("true"));
            } else {
                CommandLineOptionTest.verifySameJVMStartup(
                        null, null, ExitCode.OK, prepareOptionValue("true"));
            }
        } else {
            String errorMessage = CommandLineOptionTest.
                    getUnrecognizedOptionErrorMessage(optionName);

            CommandLineOptionTest.verifySameJVMStartup(
                    new String[]{errorMessage}, null, ExitCode.FAIL,
                    CommandLineOptionTest.UNLOCK_DIAGNOSTIC_VM_OPTIONS,
                    prepareOptionValue("true"));
        }
    }

    @Override
    protected void verifyOptionValues() throws Throwable {
        if (Platform.isServer()) {
            // Verify default value
            CommandLineOptionTest.verifyOptionValueForSameVM(optionName,
                    TestPrintPreciseRTMLockingStatisticsBase.DEFAULT_VALUE,
                    CommandLineOptionTest.UNLOCK_DIAGNOSTIC_VM_OPTIONS);
        }
    }
}
