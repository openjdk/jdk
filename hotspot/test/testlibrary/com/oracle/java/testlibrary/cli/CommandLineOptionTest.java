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

package com.oracle.java.testlibrary.cli;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.BooleanSupplier;

import com.oracle.java.testlibrary.*;

/**
 * Base class for command line option tests.
 */
public abstract class CommandLineOptionTest {
    public static final String UNLOCK_DIAGNOSTIC_VM_OPTIONS
            = "-XX:+UnlockDiagnosticVMOptions";
    public static final String UNLOCK_EXPERIMENTAL_VM_OPTIONS
            = "-XX:+UnlockExperimentalVMOptions";
    protected static final String UNRECOGNIZED_OPTION_ERROR_FORMAT
            = "Unrecognized VM option '[+-]?%s(=.*)?'";
    protected static final String EXPERIMENTAL_OPTION_ERROR_FORMAT
            = "VM option '%s' is experimental and must be enabled via "
            + "-XX:\\+UnlockExperimentalVMOptions.";
    protected static final String DIAGNOSTIC_OPTION_ERROR_FORMAT
            = " VM option '%s' is diagnostic and must be enabled via "
            + "-XX:\\+UnlockDiagnosticVMOptions.";
    private static final String PRINT_FLAGS_FINAL_FORMAT = "%s\\s*:?=\\s*%s";

    /**
     * Verifies that JVM startup behaviour matches our expectations.
     *
     * @param option an option that should be passed to JVM
     * @param expectedMessages an array of patterns that should occur
     *                          in JVM output. If {@code null} then
     *                          JVM output could be empty.
     * @param unexpectedMessages an array of patterns that should not
     *                           occur in JVM output. If {@code null} then
     *                           JVM output could be empty.
     * @param exitCode expected exit code.
     * @throws Throwable if verification fails or some other issues occur.
     */
    public static void verifyJVMStartup(String option,
            String expectedMessages[], String unexpectedMessages[],
            ExitCode exitCode) throws Throwable {
        CommandLineOptionTest.verifyJVMStartup(expectedMessages,
                unexpectedMessages, exitCode, false, option);
    }

    /**
     * Verifies that JVM startup behaviour matches our expectations.
     *
     * @param expectedMessages an array of patterns that should occur
     *                         in JVM output. If {@code null} then
     *                         JVM output could be empty.
     * @param unexpectedMessages an array of patterns that should not
     *                           occur in JVM output. If {@code null} then
     *                           JVM output could be empty.
     * @param exitCode expected exit code.
     * @param addTestVMOptions if {@code true} then test VM options will be
     *                         passed to VM.
     * @param options options that should be passed to VM in addition to mode
     *                flag.
     * @throws Throwable if verification fails or some other issues occur.
     */
    public static void verifyJVMStartup(String expectedMessages[],
            String unexpectedMessages[], ExitCode exitCode,
            boolean addTestVMOptions, String... options) throws Throwable {
        List<String> finalOptions = new ArrayList<>();
        if (addTestVMOptions) {
            Collections.addAll(finalOptions, Utils.getTestJavaOpts());
        }
        Collections.addAll(finalOptions, options);
        finalOptions.add("-version");

        ProcessBuilder processBuilder
                = ProcessTools.createJavaProcessBuilder(finalOptions.toArray(
                new String[finalOptions.size()]));
        OutputAnalyzer outputAnalyzer
                = new OutputAnalyzer(processBuilder.start());
        outputAnalyzer.shouldHaveExitValue(exitCode.value);

        if (expectedMessages != null) {
            for (String expectedMessage : expectedMessages) {
                outputAnalyzer.shouldMatch(expectedMessage);
            }
        }

        if (unexpectedMessages != null) {
            for (String unexpectedMessage : unexpectedMessages) {
                outputAnalyzer.shouldNotMatch(unexpectedMessage);
            }
        }
    }

    /**
     * Verifies that JVM startup behaviour matches our expectations when type
     * of newly started VM is the same as the type of current.
     *
     * @param expectedMessages an array of patterns that should occur
     *                         in JVM output. If {@code null} then
     *                         JVM output could be empty.
     * @param unexpectedMessages an array of patterns that should not
     *                           occur in JVM output. If {@code null} then
     *                           JVM output could be empty.
     * @param exitCode expected exit code.
     * @param options options that should be passed to VM in addition to mode
     *                flag.
     * @throws Throwable if verification fails or some other issues occur.
     */
    public static void verifySameJVMStartup(String expectedMessages[],
            String unexpectedMessages[], ExitCode exitCode, String... options)
            throws  Throwable {
        List<String> finalOptions = new ArrayList<>();
        finalOptions.add(CommandLineOptionTest.getVMTypeOption());
        Collections.addAll(finalOptions, options);

        CommandLineOptionTest.verifyJVMStartup(expectedMessages,
                unexpectedMessages, exitCode, false,
                finalOptions.toArray(new String[finalOptions.size()]));
    }

    /**
     * Verifies that value of specified JVM option is the same as
     * expected value.
     * This method filter out option with {@code optionName}
     * name from test java options.
     *
     * @param optionName a name of tested option.
     * @param expectedValue expected value of tested option.
     * @param additionalVMOpts additional options that should be
     *                         passed to JVM.
     * @throws Throwable if verification fails or some other issues occur.
     */
    public static void verifyOptionValue(String optionName,
            String expectedValue, String... additionalVMOpts) throws Throwable {
        verifyOptionValue(optionName, expectedValue, true, additionalVMOpts);
    }

    /**
     * Verifies that value of specified JVM option is the same as
     * expected value.
     * This method filter out option with {@code optionName}
     * name from test java options.
     *
     * @param optionName a name of tested option.
     * @param expectedValue expected value of tested option.
     * @param addTestVmOptions if {@code true}, then test VM options
     *                         will be used.
     * @param additionalVMOpts additional options that should be
     *                         passed to JVM.
     * @throws Throwable if verification fails or some other issues
     *                          occur.
     */
    public static void verifyOptionValue(String optionName,
            String expectedValue, boolean addTestVmOptions,
            String... additionalVMOpts) throws Throwable {
        List<String> vmOpts = new ArrayList<>();

        if (addTestVmOptions) {
            Collections.addAll(vmOpts,
                               Utils.getFilteredTestJavaOpts(optionName));
        }
        Collections.addAll(vmOpts, additionalVMOpts);
        Collections.addAll(vmOpts, "-XX:+PrintFlagsFinal", "-version");

        ProcessBuilder processBuilder = ProcessTools.createJavaProcessBuilder(
                vmOpts.toArray(new String[vmOpts.size()]));

        OutputAnalyzer outputAnalyzer
                = new OutputAnalyzer(processBuilder.start());

        outputAnalyzer.shouldHaveExitValue(0);
        outputAnalyzer.shouldMatch(String.format(
                CommandLineOptionTest.PRINT_FLAGS_FINAL_FORMAT,
                optionName, expectedValue));
    }

    /**
     * Verifies that value of specified JVM when type of newly started VM
     * is the same as the type of current.
     * This method filter out option with {@code optionName}
     * name from test java options.
     * Only mode flag will be passed to VM in addition to
     * {@code additionalVMOpts}
     *
     * @param optionName name of tested option.
     * @param expectedValue expected value of tested option.
     * @param additionalVMOpts additional options that should be
     *                         passed to JVM.
     * @throws Throwable if verification fails or some other issues occur.
     */
    public static void verifyOptionValueForSameVM(String optionName,
            String expectedValue, String... additionalVMOpts) throws Throwable {
        List<String> finalOptions = new ArrayList<>();
        finalOptions.add(CommandLineOptionTest.getVMTypeOption());
        Collections.addAll(finalOptions, additionalVMOpts);

        CommandLineOptionTest.verifyOptionValue(optionName, expectedValue,
                false, finalOptions.toArray(new String[finalOptions.size()]));
    }

    /**
     * Prepares boolean command line flag with name {@code name} according
     * to it's {@code value}.
     *
     * @param name the name of option to be prepared
     * @param value the value of option
     * @return prepared command line flag
     */
    public static String prepareBooleanFlag(String name, boolean value) {
        return String.format("-XX:%c%s", (value ? '+' : '-'), name);
    }

    /**
     * Prepares numeric command line flag with name {@code name} by setting
     * it's value to {@code value}.
     *
     * @param name the name of option to be prepared
     * @param value the value of option
     * @return prepared command line flag
     */
    public static String prepareNumericFlag(String name, Number value) {
        return String.format("-XX:%s=%s", name, value.toString());
    }

    /**
     * Returns message that should occur in VM output if option
     * {@code optionName} if unrecognized.
     *
     * @param optionName the name of option for which message should be returned
     * @return message saying that option {@code optionName} is unrecognized
     */
    public static String getUnrecognizedOptionErrorMessage(String optionName) {
        return String.format(
                CommandLineOptionTest.UNRECOGNIZED_OPTION_ERROR_FORMAT,
                optionName);
    }

    /**
     * Returns message that should occur in VM output if option
     * {@code optionName} is experimental and
     * -XX:+UnlockExperimentalVMOptions was not passed to VM.
     *
     * @param optionName the name of option for which message should be returned
     * @return message saying that option {@code optionName} is experimental
     */
    public static String getExperimentalOptionErrorMessage(String optionName) {
        return String.format(
                CommandLineOptionTest.EXPERIMENTAL_OPTION_ERROR_FORMAT,
                optionName);
    }

    /**
     * Returns message that should occur in VM output if option
     * {@code optionName} is diagnostic and -XX:+UnlockDiagnosticVMOptions
     * was not passed to VM.
     *
     * @param optionName the name of option for which message should be returned
     * @return message saying that option {@code optionName} is diganostic
     */
    public static String getDiagnosticOptionErrorMessage(String optionName) {
        return String.format(
                CommandLineOptionTest.DIAGNOSTIC_OPTION_ERROR_FORMAT,
                optionName);
    }

    /**
     * @return option required to start a new VM with the same type as current.
     * @throws RuntimeException when VM type is unknown.
     */
    private static String getVMTypeOption() {
        if (Platform.isServer()) {
            return "-server";
        } else if (Platform.isClient()) {
            return "-client";
        } else if (Platform.isMinimal()) {
            return "-minimal";
        } else if (Platform.isGraal()) {
            return "-graal";
        }
        throw new RuntimeException("Unknown VM mode.");
    }

    private final BooleanSupplier predicate;

    /**
     * Constructs new CommandLineOptionTest that will be executed only if
     * predicate {@code predicate} return {@code true}.
     * @param predicate a predicate responsible for test's preconditions check.
     */
    public CommandLineOptionTest(BooleanSupplier predicate) {
        this.predicate = predicate;
    }

    /**
     * Runs command line option test.
     */
    public final void test() throws Throwable {
        if (predicate.getAsBoolean()) {
            runTestCases();
        }
    }

    /**
     * @throws Throwable if some issue happened during test cases execution.
     */
    protected abstract void runTestCases() throws Throwable;
}
