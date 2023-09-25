/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8193660 8303476
 * @summary Check SOURCE line and JAVA_RUNTIME_VERSION in "release" file
 * @run main CheckReleaseFile
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheckReleaseFile {

    public static final String SRC_HASH_REGEXP = ":((hg)|(git)):[a-z0-9]*\\+?";

    private final boolean isOpenJDK;
    CheckReleaseFile(String dataFile, boolean isOpenJDK) {
        this.isOpenJDK = isOpenJDK;
        // Read data files
        readFile(dataFile);
    }

    private void readFile(String fileName) {
        String fishForSOURCE = null;
        String implementor = null;
        String runtimeVersion = null;

        File file = new File(fileName);

        // open the stream to read in for Entries
        try (BufferedReader buffRead =
            new BufferedReader(new FileReader(fileName))) {

            // this is the string read
            String readIn;

            // let's read some strings!
            while ((readIn = buffRead.readLine()) != null) {
                readIn = readIn.trim();

                // throw out blank lines
                if (readIn.length() == 0)
                    continue;

                // grab SOURCE line
                if (readIn.startsWith("SOURCE=")) {
                    fishForSOURCE = readIn;
                    continue;
                }

                // grab IMPLEMENTOR line
                if (readIn.startsWith("IMPLEMENTOR=")) {
                    implementor = readIn;
                    continue;
                }

                // grab JAVA_RUNTIME_VERSION line
                if (readIn.startsWith("JAVA_RUNTIME_VERSION=")) {
                    runtimeVersion = readIn;
                    continue;
                }
            }
        } catch (FileNotFoundException fileExcept) {
            throw new RuntimeException("File " + fileName +
                                       " not found reading data!", fileExcept);
        } catch (IOException ioExcept) {
            throw new RuntimeException("Unexpected problem reading data!",
                                       ioExcept);
        }

        // was SOURCE even found?
        if (fishForSOURCE == null) {
            throw new RuntimeException("SOURCE line was not found!");
        }

        // Check if implementor is Oracle
        boolean isOracle = (implementor != null) && implementor.contains("Oracle Corporation");
        checkSource(fishForSOURCE, isOracle);

        if (runtimeVersion == null) {
            throw new RuntimeException("JAVA_RUNTIME_VERSION line was not found!");
        }
        String expected = "JAVA_RUNTIME_VERSION=\"" + Runtime.version() + "\"";
        if (!expected.equals(runtimeVersion)) {
            throw new RuntimeException("Mismatched runtime version: " +
                    runtimeVersion + " expected: " + expected);
        }
    }

    private void checkSource(String fishForSOURCE, boolean isOracle) {

        System.out.println("The source string found: " + fishForSOURCE);

        // Extract the value of SOURCE=
        Pattern valuePattern = Pattern.compile("SOURCE=\"(.*)\"");
        Matcher valueMatcher = valuePattern.matcher(fishForSOURCE);
        if (!valueMatcher.matches()) {
            throw new RuntimeException("SOURCE string has bad format, should be SOURCE=\"<value>\"");
        }
        String valueString = valueMatcher.group(1);


        String[] values = valueString.split(" ");

        // First value MUST start with ".:" regardless of Oracle or OpenJDK
        String rootRegexp = "\\." + SRC_HASH_REGEXP;
        if (!values[0].matches(rootRegexp)) {
            throw new RuntimeException("The test failed, first element did not match regexp: " + rootRegexp);
        }

        // If it's an Oracle build, it can be either OpenJDK or OracleJDK. Other
        // builds may have any number of additional elements in any format.
        if (isOracle) {
            if (isOpenJDK) {
                if (values.length != 1) {
                    throw new RuntimeException("The test failed, wrong number of elements in SOURCE list." +
                            " Should be 1 for Oracle built OpenJDK.");
                }
            } else {
                if (values.length != 2) {
                    throw new RuntimeException("The test failed, wrong number of elements in SOURCE list." +
                            " Should be 2 for OracleJDK.");
                }
                // Second value MUST start with "open:" for OracleJDK
                String openRegexp = "open" + SRC_HASH_REGEXP;
                if (!values[1].matches(openRegexp)) {
                    throw new RuntimeException("The test failed, second element did not match regexp: " + openRegexp);
                }
            }
        }

        // Everything was fine
        System.out.println("The test passed!");
    }

    public static void main(String args[]) {
        String jdkPath = System.getProperty("test.jdk");
        String runtime = System.getProperty("java.runtime.name");

        System.out.println("JDK Path : " + jdkPath);
        System.out.println("Runtime Name : " + runtime);

        new CheckReleaseFile(jdkPath + "/release", runtime.contains("OpenJDK"));
    }
}
