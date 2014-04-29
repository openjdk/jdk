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

/**
 * Base for all RTM-related CLI tests.
 */
public abstract class RTMGenericCommandLineOptionTest
        extends CommandLineOptionTest {
    protected static final String RTM_INSTR_ERROR
            = "RTM instructions are not available on this CPU";
    protected static final String RTM_UNSUPPORTED_VM_ERROR
            = "RTM locking optimization is not supported in this VM";
    protected static final String RTM_ABORT_RATIO_WARNING
            = "RTMAbortRatio must be in the range 0 to 100, resetting it to 50";
    protected static final String RTM_FOR_STACK_LOCKS_WARNING
            = "UseRTMForStackLocks flag should be off when UseRTMLocking "
            + "flag is off";
    protected static final String RTM_COUNT_INCR_WARNING
            = "RTMTotalCountIncrRate must be a power of 2, resetting it to 64";
    protected static final String RTM_BIASED_LOCKING_WARNING
            = "Biased locking is not supported with RTM locking; "
            + "ignoring UseBiasedLocking flag";

    protected final String optionName;
    protected final String errorMessage;
    protected final String experimentalOptionError;
    protected final boolean isExperimental;
    protected final boolean isBoolean;
    protected final String defaultValue;
    protected final String[] optionValues;

    /**
     * Constructs new genetic RTM CLI test, for option {@code optionName} which
     * has default value {@code defaultValue}. Test cases will use option's
     * values passed via {@code optionValues} for verification of correct
     * option processing.
     *
     * Test constructed using this ctor will be started on any cpu regardless
     * it's architecture and supported/unsupported features.
     *
     * @param predicate predicate responsible for test's preconditions check
     * @param optionName name of option to be tested
     * @param isBoolean {@code true} if option is binary
     * @param isExperimental {@code true} if option is experimental
     * @param defaultValue default value of tested option
     * @param optionValues different option values
     */
    public RTMGenericCommandLineOptionTest(BooleanSupplier predicate,
            String optionName, boolean isBoolean, boolean isExperimental,
            String defaultValue, String... optionValues) {
        super(predicate);
        this.optionName = optionName;
        this.isExperimental = isExperimental;
        this.isBoolean = isBoolean;
        this.defaultValue = defaultValue;
        this.optionValues = optionValues;
        this.errorMessage = CommandLineOptionTest.
                getUnrecognizedOptionErrorMessage(optionName);
        this.experimentalOptionError = CommandLineOptionTest.
                getExperimentalOptionErrorMessage(optionName);
    }

    @Override
    public void runTestCases() throws Throwable {
        if (Platform.isX86() || Platform.isX64()) {
            if (Platform.isServer() && !Platform.isEmbedded()) {
                runX86SupportedVMTestCases();
            } else {
                runX86UnsupportedVMTestCases();
            }
        } else {
            runNonX86TestCases();
        }
    }

    /**
     * Runs test cases on X86 CPU if VM supports RTM locking.
     * @throws Throwable
     */
    protected void runX86SupportedVMTestCases() throws Throwable {
        runGenericX86TestCases();
    }

    /**
     * Runs test cases on non-X86 CPU if VM does not support RTM locking.
     * @throws Throwable
     */
    protected void runX86UnsupportedVMTestCases() throws Throwable {
        runGenericX86TestCases();
    }

    /**
     * Runs test cases on non-X86 CPU.
     * @throws Throwable
     */
    protected void runNonX86TestCases() throws Throwable {
        CommandLineOptionTest.verifySameJVMStartup(
                new String[] { errorMessage }, null, ExitCode.FAIL,
                prepareOptionValue(defaultValue));
    }

    /**
     * Runs generic X86 test cases.
     * @throws Throwable
     */
    protected void runGenericX86TestCases() throws Throwable {
        verifyJVMStartup();
        verifyOptionValues();
    }

    protected void verifyJVMStartup() throws Throwable {
        String optionValue = prepareOptionValue(defaultValue);
        if (isExperimental) {
            // verify that option is experimental
            CommandLineOptionTest.verifySameJVMStartup(
                    new String[] { experimentalOptionError },
                    new String[] { errorMessage }, ExitCode.FAIL,
                    optionValue);
            // verify that it could be passed if experimental options
            // are unlocked
            CommandLineOptionTest.verifySameJVMStartup(null,
                    new String[] {
                            experimentalOptionError,
                            errorMessage
                    },
                    ExitCode.OK,
                    CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                    optionValue);
        } else {
            // verify that option could be passed
            CommandLineOptionTest.verifySameJVMStartup(null,
                    new String[]{errorMessage}, ExitCode.OK, optionValue);
        }
    }

    protected void verifyOptionValues() throws Throwable {
        // verify default value
        if (isExperimental) {
            CommandLineOptionTest.verifyOptionValueForSameVM(optionName,
                    defaultValue,
                    CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS);
        } else {
            CommandLineOptionTest.verifyOptionValueForSameVM(optionName,
                    defaultValue);
        }
        // verify other specified option values
        if (optionValues == null) {
            return;
        }

        for (String value : optionValues) {
            if (isExperimental) {
                CommandLineOptionTest.verifyOptionValueForSameVM(optionName,
                        value,
                        CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                        prepareOptionValue(value));
            } else {
                CommandLineOptionTest.verifyOptionValueForSameVM(optionName,
                        value, prepareOptionValue(value));
            }
        }
    }

    protected String prepareOptionValue(String value) {
        if (isBoolean) {
            return CommandLineOptionTest.prepareBooleanFlag(optionName,
                    Boolean.valueOf(value));
        } else {
            return String.format("-XX:%s=%s", optionName, value);
        }
    }
}
