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
 * @bug 8051959
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
import java.util.stream.Stream;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.Test;

public class DebugOptions {

    static final String DATE_REGEX = "\\d{4}-\\d{2}-\\d{2}";

    private static final Stream<String[]> patternMatches = Stream.of(
            // no extra info present
            new String[]{"properties",
                    "properties: Initial",
                    "properties\\["},
            // thread info only
            new String[]{"properties+thread",
                    "properties\\[.*\\|main\\|.*java.*]:",
                    "properties\\[" + DATE_REGEX},
            // timestamp info only
            new String[]{"properties+timestamp",
                    "properties\\[" + DATE_REGEX + ".*\\]",
                    "\\|main\\]:"},
            // both thread and timestamp
            new String[]{"properties+timestamp+thread",
                    "properties\\[.*\\|main|" + DATE_REGEX + ".*\\]:",
                    "properties:"},
            // flip the List of previous test
            new String[]{"properties+thread+timestamp",
                    "properties\\[.*\\|main|" + DATE_REGEX + ".*\\]:",
                    "properties:"},
            // comma not valid separator, ignore extra info printing request
            new String[]{"properties,thread,timestamp",
                    "properties:",
                    "properties\\[.*\\|main|" + DATE_REGEX + ".*\\]:"},
            // no extra info for keystore debug prints
            new String[]{"properties+thread+timestamp,keystore",
                    "properties\\[.*\\|main|" + DATE_REGEX + ".*\\]:",
                    "keystore\\["},
            // flip List around in last test - same outcome expected
            new String[]{"keystore,properties+thread+timestamp",
                    "properties\\[.*\\|main|" + DATE_REGEX + ".*\\]:",
                    "keystore\\["},
            // turn on thread info for both keystore and properties components
            new String[]{"keystore+thread,properties+thread",
                    "properties\\[.*\\|main|.*\\Rkeystore\\[.*\\|main|.*\\]:",
                    "\\|" + DATE_REGEX + ".*\\]:"},
            // same as above with erroneous comma at end of string. same output expected
            new String[]{"keystore+thread,properties+thread,",
                    "properties\\[.*\\|main|.*\\Rkeystore\\[.*\\|main|.*\\]:",
                    "\\|" + DATE_REGEX + ".*\\]:"},
            // turn on thread info for properties and timestamp for keystore
            new String[]{"keystore+timestamp,properties+thread",
                    "properties\\[.*\\|main|.*\\Rkeystore\\[" + DATE_REGEX + ".*\\]:",
                    "properties\\[.*\\|" + DATE_REGEX + ".*\\]:"},
            // turn on thread info for all components
            new String[]{"all+thread",
                    "properties\\[.*\\|main.*((.*\\R)*)keystore\\[.*\\|main.*java.*\\]:",
                    "properties\\[" + DATE_REGEX + ".*\\]:"},
            // turn on thread info and timestamp for all components
            new String[]{"all+thread+timestamp",
                    "properties\\[.*\\|main.*\\|" + DATE_REGEX +
                    ".*\\]((.*\\R)*)keystore\\[.*\\|main.*\\|" + DATE_REGEX + ".*\\]:",
                    "properties:"},
            // all decorator option should override other component options
            new String[]{"all+thread+timestamp,properties",
                    "properties\\[.*\\|main.*\\|" + DATE_REGEX +
                    ".*\\]((.*\\R)*)keystore\\[.*\\|main.*\\|" + DATE_REGEX + ".*\\]:",
                    "properties:"},
            // thread details should only be printed for properties option
            new String[]{"properties+thread,all",
                    "properties\\[.*\\|main\\|.*\\]:",
                    "keystore\\[.*\\|main\\|.*\\]:"},
            // thread details should be printed for all statements
            new String[]{"properties,all+thread",
                    "properties\\[.*\\|main.*java" +
                    ".*\\]((.*\\R)*)keystore\\[.*\\|main.*java.*\\]:",
                    "properties:"}
    );

    /**
     * This will execute the test logic without any param manipulation
     *
     * @param paramName   name of the parameter e.g. -Djava.security.debug=
     * @param paramVal    value of the parameter
     * @param expected    expected output
     * @param notExpected not expected output
     */
    public void testParameter(String paramName,
                              String paramVal,
                              String expected,
                              String notExpected) throws Exception {

        System.out.printf("Executing: {%s%s%s}%n",
                paramName,
                paramVal,
                "DebugOptions");


        final OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(
                paramName + paramVal,
                "DebugOptions");
        outputAnalyzer.shouldHaveExitValue(0)
                .shouldMatch(expected)
                .shouldNotMatch(notExpected);
    }

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
        System.out.printf("Executing: {%s%s%s}%n",
                paramName,
                formattedParam,
                "DebugOptions");

        final OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(
                paramName + formattedParam,
                "DebugOptions");
        outputAnalyzer.shouldHaveExitValue(0)
                .shouldMatch(expected)
                .shouldNotMatch(notExpected);
    }

    /**
     * This method will change the input string to have the first
     * and last letters uppercase
     * <p>
     * e.g.:
     * hello -> HellO
     *
     * @param paramString string to change
     * @return resulting string
     */
    private String makeFirstAndLastLetterUppercase(final String paramString) {
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
     * in both mixed and lowercase
     */
    @Test
    public void debugOptionsTest() throws Exception {

        try (final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            final List<Callable<Void>> testsCallables = new ArrayList<>();

            patternMatches.forEach(params -> {
                testsCallables.add(() -> {
                    testParameter(
                            "-Djava.security.debug=",
                            params[0],
                            params[1],
                            params[2]);
                    return null;
                });
                testsCallables.add(() -> {
                    testMixedCaseParameter(
                            "-Djava.security.debug=",
                            params[0],
                            params[1],
                            params[2]);
                    return null;
                });

                testsCallables.add(() -> {
                    testParameter(
                            "-Djava.security.auth.debug=",
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

                System.out.println("option added to all tests " + Arrays.toString(params));
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
