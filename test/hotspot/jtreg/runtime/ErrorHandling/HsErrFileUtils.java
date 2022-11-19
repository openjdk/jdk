/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 SAP SE. All rights reserved.
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
     * @param output
     * @return
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
     * Given an open hs-err file, read it line by line and check for pattern. Pattern
     * need to appear in order, but not necessarily uninterrupted.
     */
    public static void checkHsErrFileContent(File f, Pattern[] patterns, boolean verbose) throws IOException {

        FileInputStream fis = new FileInputStream(f);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        String line = null;

        int currentPattern = 0;

        String lastLine = null;
        while ((line = br.readLine()) != null) {
            if (verbose) {
                System.out.println(line);
            }
            if (currentPattern < patterns.length) {
                if (patterns[currentPattern].matcher(line).matches()) {
                    if (!verbose) {
                        System.out.println(line);
                    }
                    System.out.println("^^^ Match " + currentPattern + ": matches " + patterns[currentPattern] + "^^^");
                    currentPattern ++;
                }
            }
            lastLine = line;
        }
        br.close();

        if (currentPattern < patterns.length) {
            throw new RuntimeException("hs-err file incomplete (found " + currentPattern + " matching pattern, " +
                                       "first missing pattern: " + patterns[currentPattern] + ")");
        }

        if (!lastLine.equals("END.")) {
            throw new RuntimeException("hs-err file incomplete (missing END marker.)");
        }

        System.out.println("Found all expected pattern in hs-err file at " + f.getAbsolutePath());

    }

}
