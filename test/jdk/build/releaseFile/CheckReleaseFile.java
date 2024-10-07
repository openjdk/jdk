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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheckReleaseFile {

    public static final String SRC_HASH_REGEXP = ":git:[a-z0-9]*\\+?";

    public static void main(String args[]) throws IOException {
        String jdkPath = System.getProperty("test.jdk");
        String runtime = System.getProperty("java.runtime.name");

        System.out.println("JDK Path : " + jdkPath);
        System.out.println("Runtime Name : " + runtime);

        checkReleaseFile(Path.of(jdkPath));
    }

    private static void checkReleaseFile(Path javaHome) throws IOException {
        String source = null;
        String runtimeVersion = null;

        Path releaseFile = javaHome.resolve("release");

        // open the stream to read in for Entries
        try (BufferedReader buffRead = Files.newBufferedReader(releaseFile)) {

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
                    source = readIn;
                    continue;
                }

                // grab JAVA_RUNTIME_VERSION line
                if (readIn.startsWith("JAVA_RUNTIME_VERSION=")) {
                    runtimeVersion = readIn;
                    continue;
                }
            }
        }

        // was SOURCE even found?
        if (source == null) {
            throw new RuntimeException("SOURCE line was not found!");
        }
        checkSource(source);

        if (runtimeVersion == null) {
            throw new RuntimeException("JAVA_RUNTIME_VERSION line was not found!");
        }
        String expected = "JAVA_RUNTIME_VERSION=\"" + Runtime.version() + "\"";
        if (!expected.equals(runtimeVersion)) {
            throw new RuntimeException("Mismatched runtime version: " +
                    runtimeVersion + " expected: " + expected);
        }
    }

    private static void checkSource(String source) {

        System.out.println("The source string found: " + source);

        // Extract the value of SOURCE=
        Pattern valuePattern = Pattern.compile("SOURCE=\"(.*)\"");
        Matcher valueMatcher = valuePattern.matcher(source);
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
        String runtime = System.getProperty("java.runtime.name");
        String vendor = System.getProperty("java.vendor");
        if (runtime.contains("OpenJDK") && vendor.contains("Oracle Corporation")) {
            System.out.println("Oracle built OpenJDK, verifying SOURCE format");
            if (values.length != 1) {
                throw new RuntimeException("The test failed, wrong number of elements in SOURCE list." +
                                           " Should be 1 for Oracle built OpenJDK.");
            }
        } else {
            System.out.println("Not Oracle built OpenJDK, skipping further SOURCE verification");
        }

        // Everything was fine
        System.out.println("The test passed!");
    }
}
