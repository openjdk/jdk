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

import com.oracle.java.testlibrary.*;

/**
 * Base class for command line option tests.
 */
public abstract class CommandLineOptionTest {

    public static final String UNRECOGNIZED_OPTION_ERROR_FORMAT =
        "Unrecognized VM option '[+-]?%s'";

    public static final String printFlagsFinalFormat = "%s\\s*:?=\\s*%s";

    /**
     * Verify that JVM startup behaviour matches our expectations.
     *
     * @param option The option that should be passed to JVM
     * @param excpectedMessages Array of patterns that should occur
     *                          in JVM output. If <b>null</b> then
     *                          JVM output could be empty.
     * @param unexpectedMessages Array of patterns that should not
     *                           occur in JVM output. If <b>null</b> then
     *                          JVM output could be empty.
     * @param exitCode expected exit code.
     * @throws Throwable if verification fails or some other issues occur.
     */
    public static void verifyJVMStartup(String option,
                                        String expectedMessages[],
                                        String unexpectedMessages[],
                                        ExitCode exitCode)
                                 throws Throwable {

        OutputAnalyzer outputAnalyzer =
            ProcessTools.executeTestJvm(option, "-version");

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
     * Verify that value of specified JVM option is the same as
     * expected value.
     * This method filter out option with {@code optionName}
     * name from test java options.
     *
     * @param optionName Name of tested option.
     * @param expectedValue Expected value of tested option.
     * @param additionalVMOpts Additonal options that should be
     *                         passed to JVM.
     * @throws Throwable if verification fails or some other issues occur.
     */
    public static void verifyOptionValue(String optionName,
                                         String expectedValue,
                                         String... additionalVMOpts)
                                  throws Throwable {
        verifyOptionValue(optionName, expectedValue, true, additionalVMOpts);
    }

    /**
     * Verify that value of specified JVM option is the same as
     * expected value.
     * This method filter out option with {@code optionName}
     * name from test java options.
     *
     * @param optionName Name of tested option.
     * @param expectedValue Expected value of tested option.
     * @param addTestVmOptions If <b>true</b>, then test VM options
     *                         will be used.
     * @param additionalVMOpts Additonal options that should be
     *                         passed to JVM.
     * @throws Throwable if verification fails or some other issues occur.
     */
    public static void verifyOptionValue(String optionName,
                                         String expectedValue,
                                         boolean addTestVmOptions,
                                         String... additionalVMOpts)
                                  throws Throwable {

        List<String> vmOpts = new ArrayList<String>();

        if (addTestVmOptions) {
            Collections.addAll(vmOpts,
                               Utils.getFilteredTestJavaOpts(optionName));
        }
        Collections.addAll(vmOpts, additionalVMOpts);
        Collections.addAll(vmOpts, new String[] {
                "-XX:+PrintFlagsFinal",
                "-version"
            });

        ProcessBuilder processBuilder =
            ProcessTools.
            createJavaProcessBuilder(vmOpts.
                                     toArray(new String[vmOpts.size()]));

        OutputAnalyzer outputAnalyzer =
            new OutputAnalyzer(processBuilder.start());

        outputAnalyzer.shouldHaveExitValue(0);
        outputAnalyzer.shouldMatch(String.
                                   format(printFlagsFinalFormat,
                                          optionName,
                                          expectedValue));
    }


    /**
     * Run command line option test.
     *
     * @throws Throwable if test failed.
     */
    public final void test() throws Throwable {
        if (checkPreconditions()) {
            runTestCases();
        }
    }

    /**
     * Check that all preconditions for test execution are met.
     *
     * @return <b>true</b> if test could be executed.
     */
    public boolean checkPreconditions() {
        return true;
    }

    /**
     * Run test cases.
     *
     * @throws Throwable if test failed.
     */
    public abstract void runTestCases() throws Throwable;
}

