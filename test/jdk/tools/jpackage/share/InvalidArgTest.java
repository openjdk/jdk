/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @summary jpackage invalid argument test
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @modules jdk.incubator.jpackage
 * @run main/othervm -Xmx512m InvalidArgTest
 */
public class InvalidArgTest {

    private static final String ARG1 = "--no-such-argument";
    private static final String ARG2 = "--dest";
    private static final String RESULT1 =
            "Invalid Option: [--no-such-argument]";
    private static final String RESULT2 = "--main-jar or --module";

    private static void validate(String arg, String output) throws Exception {
        String[] result = JPackageHelper.splitAndFilter(output);
        if (result.length != 1) {
            System.err.println(output);
            throw new AssertionError("Invalid number of lines in output: "
                    + result.length);
        }

        if (arg.equals(ARG1)) {
            if (!result[0].trim().contains(RESULT1)) {
                System.err.println("Expected: " + RESULT1);
                System.err.println("Actual: " + result[0]);
                throw new AssertionError("Unexpected output: " + result[0]);
            }
        } else if (arg.equals(ARG2)) {
            if (!result[0].trim().contains(RESULT2)) {
                System.err.println("Expected: " + RESULT2);
                System.err.println("Actual: " + result[0]);
                throw new AssertionError("Unexpected output: " + result[0]);
            }
        }
    }

    private static void testInvalidArg() throws Exception {
        String output = JPackageHelper.executeCLI(false,
                "--type", "app-image", ARG1);
        validate(ARG1, output);

        output = JPackageHelper.executeCLI(false,
                "--type", "app-image", ARG2);
        validate(ARG2, output);
    }

    private static void testInvalidArgToolProvider() throws Exception {
        String output = JPackageHelper.executeToolProvider(false,
                "--type", "app-image", ARG1);
        validate(ARG1, output);

        output = JPackageHelper.executeToolProvider(false,
                "--type", "app-image", ARG2);
        validate(ARG2, output);
    }

    public static void main(String[] args) throws Exception {
        testInvalidArg();
        testInvalidArgToolProvider();
    }

}
