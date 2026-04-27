/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8051959 8350689
 * @summary Option to print extra information in java.security.debug output
 * @library /test/lib
 * @run junit DebugOptions
 */

import java.security.KeyStore;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DebugOptions {

    static final String DATE_REGEX = "\\d{4}-\\d{2}-\\d{2}";
    static final String EXPECTED_PROP_REGEX =
            "properties\\[.*\\|main|" + DATE_REGEX + ".*\\]:";
    static final String EXPECTED_PROP_KEYSTORE_REGEX =
            "properties\\[.*\\|main|" + DATE_REGEX +
            ".*\\Rkeystore\\[.*\\|main|" + DATE_REGEX + ".*\\]:";
    static final String EXPECTED_ALL_REGEX =
            "properties\\[.*\\|main.*\\|" + DATE_REGEX +
            ".*\\]((.*\\R)*)keystore\\[.*\\|main.*\\|"
            + DATE_REGEX + ".*\\]:";

    private static final List<String[]> patternMatches = List.of(
            // test for thread and timestamp info
            new String[]{"properties",
                    EXPECTED_PROP_REGEX,
                    "properties:"},
            // test for thread and timestamp info
            new String[]{"properties+thread",
                    EXPECTED_PROP_REGEX,
                    "properties:"},
            // flip the arguments of previous test
            new String[]{"properties+thread+timestamp",
                    EXPECTED_PROP_REGEX,
                    "properties:"},
            // regular keystore,properties component string
            new String[]{"keystore,properties",
                    EXPECTED_PROP_KEYSTORE_REGEX,
                    "properties:"},
            // turn on all
            new String[]{"all",
                    EXPECTED_ALL_REGEX,
                    "properties:"},
            // expect thread and timestamp info
            new String[]{"all+thread",
                    EXPECTED_ALL_REGEX,
                    "properties:"}
    );

    /**
     * This will execute the test logic, but first change the param
     * to be mixed case
     *
     * @param paramName   name of the parameter e.g. -Djava.security.debug=
     * @param paramVal    value of the parameter
     * @param expected    expected output
     * @param notExpected not expected output
     */
    public void testMixedCaseParameter(String paramName,
                                       String paramVal,
                                       String expected,
                                       String notExpected) throws Exception {

        final String formattedParam = makeFirstAndLastLetterUppercase(paramVal);
        System.out.printf("Executing: {%s%s DebugOptions}%n",
                paramName,
                formattedParam);

        final OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(
                paramName + formattedParam,
                "DebugOptions");
        outputAnalyzer.shouldHaveExitValue(0)
                .shouldMatch(expected)
                .shouldNotMatch(notExpected);
    }

    /**
     * This will execute the test logic, but first change the param
     * to be mixed case
     * Additionally it will input a nonsensical input testing if
     * the execution should be successful, but no debug output expected
     *
     * @param paramName   name of the parameter e.g. -Djava.security.debug=
     * @param paramVal    value of the parameter
     * @param expected    expected output
     * @param notExpected not expected output
     */
    public void testMixedCaseBrokenParameter(String paramName,
                                             String paramVal,
                                             String notExpected) throws Exception {

        final String formattedParam = makeFirstAndLastLetterUppercase(paramVal);

        final String nonsenseParam = "NONSENSE" +
                                     formattedParam.substring(0, formattedParam.length() - 2) +
                                     "NONSENSE" +
                                     formattedParam.substring(formattedParam.length() - 2) +
                                     "NONSENSE";

        System.out.printf("Executing: {%s%s DebugOptions}%n",
                paramName,
                nonsenseParam);

        final OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(
                paramName + nonsenseParam,
                "DebugOptions");

        // shouldn't fail, but shouldn't give 'properties:' back
        outputAnalyzer.shouldHaveExitValue(0)
                .shouldNotMatch(notExpected);
    }

    /**
     * This method will change the input string to have
     * first and last letters uppercase
     * <p>
     * e.g.:
     * hello -> HellO
     *
     * @param paramString string to change. Must not be null or empty
     * @return resulting string
     */
    private String makeFirstAndLastLetterUppercase(final String paramString) {
        Assertions.assertTrue(paramString != null && !paramString.isEmpty());

        final int length = paramString.length();
        final String firstLetter = paramString.substring(0, 1);
        final String lastLetter = paramString.substring((length - 1),
                length);

        return firstLetter.toUpperCase()
               + paramString.substring(1, length - 1)
               + lastLetter.toUpperCase();
    }

    /**
     * This test will run all options in parallel with all param names
     * in mixed case
     */
    @Test
    public void debugOptionsMixedCaseTest() throws Exception {

        try (final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            final List<Callable<Void>> testsCallables = new ArrayList<>();

            patternMatches.forEach(params -> {
                testsCallables.add(() -> {
                    testMixedCaseParameter(
                            "-Djava.security.debug=",
                            params[0],
                            params[1],
                            params[2]);
                    return null;
                });
                testsCallables.add(() -> {
                    testMixedCaseParameter(
                            "-Djava.security.auth.debug=",
                            params[0],
                            params[1],
                            params[2]);
                    return null;
                });

                System.out.println("Option added to all mixed case tests " + Arrays.toString(params));
            });

            System.out.println("Starting all the threads");
            final List<Future<Void>> res = executorService.invokeAll(testsCallables);
            for (final Future<Void> future : res) {
                future.get();
            }
        }
    }

    /**
     * This test will run all options in parallel with all param names
     * However all params will be automatically adjusted to be broken
     * the expectation is to have a complete execution, but no debug output
     */
    @Test
    public void debugOptionsBrokenTest() throws Exception {

        try (final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            final List<Callable<Void>> testsCallables = new ArrayList<>();

            patternMatches.forEach(params -> {
                testsCallables.add(() -> {
                    testMixedCaseBrokenParameter(
                            "-Djava.security.debug=",
                            params[0],
                            params[2]);
                    return null;
                });
                testsCallables.add(() -> {
                    testMixedCaseBrokenParameter(
                            "-Djava.security.auth.debug=",
                            params[0],
                            params[2]);
                    return null;
                });

                System.out.println("Option added to all broken case tests " + Arrays.toString(params));
            });

            System.out.println("Starting all the threads");
            final List<Future<Void>> res = executorService.invokeAll(testsCallables);
            for (final Future<Void> future : res) {
                future.get();
            }
        }
    }

    /**
     * This is used for the test logic itself
     */
    public static void main(String[] args) throws Exception {

        // something to trigger "properties" debug output
        Security.getProperty("test");
        // trigger "keystore" debug output
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
    }
}
