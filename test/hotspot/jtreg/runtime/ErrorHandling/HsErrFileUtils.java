/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2024 SAP SE. All rights reserved.
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

import jdk.test.lib.process.OutputAnalyzer;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class HsErrFileUtils {

    /**
     * Given the output of a java VM that crashed, extract the name of the hs-err file from the output
     */
    public static String extractHsErrFileNameFromOutput(OutputAnalyzer output) {
        output.shouldMatch("# A fatal error has been detected.*");

        // extract hs-err file
        String hs_err_file = output.firstMatch("# *(\\S*hs_err_pid\\d+\\.log)", 1);
        if (hs_err_file == null) {
            throw new RuntimeException("Did not find hs-err file in output.\n");
        }

        return hs_err_file;
    }

    /**
     * Given the output of a java VM that crashed, extract the name of the hs-err file from the output,
     * open that file and return its File.
     */
    public static File openHsErrFileFromOutput(OutputAnalyzer output) {
        String name = extractHsErrFileNameFromOutput(output);
        File f = new File(name);
        if (!f.exists()) {
            throw new RuntimeException("Cannot find hs-err file at " + f.getAbsolutePath());
        }
        return f;
    }

    /**
     * Given an open hs-err file, read it line by line and check for existence of a set of patterns. Will fail
     * if patterns are missing, or if the END marker is missing.
     * @param f Input file
     * @param patterns An array of patterns that need to match, in that order
     * @param verbose If true, the content of the hs-err file is printed while matching. If false, only the matched patterns
     *                are printed.
     * @throws RuntimeException, {@link IOException}
     */
    public static void checkHsErrFileContent(File f, Pattern[] patterns, boolean verbose) throws IOException {
        checkHsErrFileContent(f, patterns, null, true, verbose, false);
    }

    /**
     * Given an open hs-err file, read it line by line and check for various conditions.
     * @param f input file
     * @param positivePatterns Optional array of patterns that need to appear, in given order, in the file. Missing
     *                        patterns cause the test to fail.
     * @param negativePatterns Optional array of patterns that must not appear in the file; test fails if they do.
     *                        Order is irrelevant.
     * @param checkEndMarker If true, we check for the final "END" in an hs-err file; if it is missing it indicates
     *                        that hs-err file printing did not complete successfully.
     * @param verbose If true, the content of the hs-err file is printed while matching. If false, only the matched patterns
     *                are printed.
     * @throws RuntimeException, {@link IOException}
     */
    public static void checkHsErrFileContent(File f, Pattern[] positivePatterns, Pattern[] negativePatterns, boolean checkEndMarker, boolean verbose) throws IOException {
        checkHsErrFileContent(f, positivePatterns, negativePatterns, checkEndMarker, verbose, false);
    }

    /**
     * Given an open hs-err file, read it line by line and check for existence of a set of patterns. Will fail
     * if patterns are missing, or if the END marker is missing.
     * @param f Input file
     * @param patterns An array of patterns that need to match, in that order
     * @param verbose If true, the content of the hs-err file is printed while matching. If false, only the matched patterns
     *                are printed.
     * @param printHserrOnError If true, the content of the hs-err file is printed in case of a failing check
     * @throws RuntimeException, {@link IOException}
     */
    public static void checkHsErrFileContent(File f, Pattern[] patterns, boolean verbose, boolean printHserrOnError) throws IOException {
        checkHsErrFileContent(f, patterns, null, true, verbose, printHserrOnError);
    }

    /**
     * Given an open hs-err file, read it line by line and check for various conditions.
     * @param f input file
     * @param positivePatterns Optional array of patterns that need to appear, in given order, in the file. Missing
     *                        patterns cause the test to fail.
     * @param negativePatterns Optional array of patterns that must not appear in the file; test fails if they do.
     *                        Order is irrelevant.
     * @param checkEndMarker If true, we check for the final "END" in an hs-err file; if it is missing it indicates
     *                        that hs-err file printing did not complete successfully.
     * @param verbose If true, the content of the hs-err file is printed while matching. If false, only the matched patterns
     *                are printed.
     * @param printHserrOnError If true, the content of the hs-err file is printed in case of a failing check
     * @throws RuntimeException, {@link IOException}
     */
    public static void checkHsErrFileContent(File f, Pattern[] positivePatterns, Pattern[] negativePatterns, boolean checkEndMarker, boolean verbose, boolean printHserrOnError) throws IOException {
        try (
                FileInputStream fis = new FileInputStream(f);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        ) {
            String line = null;
            String lastLine = null;
            int lineNo = 0;

            Deque<Pattern> positivePatternStack = new ArrayDeque<Pattern>();
            if (positivePatterns != null) {
                Collections.addAll(positivePatternStack, positivePatterns);
            }
            Pattern currentPositivePattern = positivePatternStack.pollFirst();

            while ((line = br.readLine()) != null) {
                if (verbose) {
                    System.out.println(line);
                }
                if (currentPositivePattern != null) {
                    if (currentPositivePattern.matcher(line).matches()) {
                        if (!verbose) {
                            System.out.println(line);
                        }
                        System.out.println("^^^ Matches " + currentPositivePattern + " at line " + lineNo + "^^^");
                        currentPositivePattern = positivePatternStack.pollFirst();
                        if (currentPositivePattern == null && negativePatterns == null && checkEndMarker == false) {
                            System.out.println("Lazily skipping the rest of the hs-err file...");
                            break; // Shortcut. Nothing to do.
                        }
                    }
                }
                if (negativePatterns != null) {
                    for (Pattern negativePattern : negativePatterns) {
                        if (negativePattern.matcher(line).matches()) {
                            if (!verbose) {
                                System.out.println(line);
                            }
                            System.out.println("^^^ Forbidden pattern found at line " + lineNo + ": " + negativePattern + "^^^");
                            if (printHserrOnError) {
                                printHsErrFile(f);
                            }
                            throw new RuntimeException("Forbidden pattern found at line " + lineNo + ": " + negativePattern);
                        }
                    }
                }
                lastLine = line;
                lineNo++;
            }
            // If the current pattern is not null then it didn't match
            if (currentPositivePattern != null) {
                if (printHserrOnError) {
                    printHsErrFile(f);
                }
                throw new RuntimeException("hs-err file incomplete (first missing pattern: " + currentPositivePattern.pattern() + ")");
            }
            if (checkEndMarker && !lastLine.equals("END.")) {
                if (printHserrOnError) {
                    printHsErrFile(f);
                }
                throw new RuntimeException("hs-err file incomplete (missing END marker.)");
            }
            System.out.println("hs-err file " + f.getAbsolutePath() + " scanned successfully.");
        }
    }

    private static void printHsErrFile(File f) throws IOException {
        try (
                FileInputStream fis = new FileInputStream(f);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        ) {
            String line;
            System.out.println("------------------------ hs-err file ------------------------");
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
            System.out.println("-------------------------------------------------------------");
        }
    }

}
