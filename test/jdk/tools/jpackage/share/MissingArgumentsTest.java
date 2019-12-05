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
 * @summary jpackage create image missing arguments test
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @modules jdk.incubator.jpackage
 * @run main/othervm -Xmx512m MissingArgumentsTest
 */

public class MissingArgumentsTest {
    private static final String [] RESULT_1 = {"--input"};
    private static final String [] CMD_1 = {
        "--type", "app-image",
        "--dest", "output",
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
    };

    private static final String [] RESULT_2 = {"--input", "--app-image"};
    private static final String [] CMD_2 = {
        "--type", "app-image",
        "--type", "invalid-type",
        "--dest", "output",
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
    };

    private static final String [] RESULT_3 = {"main class was not specified"};
    private static final String [] CMD_3 = {
        "--type", "app-image",
        "--input", "input",
        "--dest", "output",
        "--name", "test",
        "--main-jar", "hello.jar",
    };

    private static final String [] RESULT_4 = {"--main-jar"};
    private static final String [] CMD_4 = {
        "--type", "app-image",
        "--input", "input",
        "--dest", "output",
        "--name", "test",
        "--main-class", "Hello",
    };

    private static final String [] RESULT_5 = {"--module-path", "--runtime-image"};
    private static final String [] CMD_5 = {
        "--type", "app-image",
        "--dest", "output",
        "--name", "test",
        "--module", "com.hello/com.hello.Hello",
    };

    private static final String [] RESULT_6 = {"--module-path", "--runtime-image",
                                               "--app-image"};
    private static final String [] CMD_6 = {
        "--type", "invalid-type",
        "--dest", "output",
        "--name", "test",
        "--module", "com.hello/com.hello.Hello",
    };

    private static void validate(String output, String [] expected,
           boolean single) throws Exception {
        String[] result = JPackageHelper.splitAndFilter(output);
        if (single && result.length != 1) {
            System.err.println(output);
            throw new AssertionError("Invalid number of lines in output: "
                    + result.length);
        }

        for (String s : expected) {
            if (!result[0].contains(s)) {
                System.err.println("Expected to contain: " + s);
                System.err.println("Actual: " + result[0]);
                throw new AssertionError("Unexpected error message");
            }
        }
    }

    private static void testMissingArg() throws Exception {
        String output = JPackageHelper.executeCLI(false, CMD_1);
        validate(output, RESULT_1, true);

        output = JPackageHelper.executeCLI(false, CMD_2);
        validate(output, RESULT_2, true);

        output = JPackageHelper.executeCLI(false, CMD_3);
        validate(output, RESULT_3, false);

        output = JPackageHelper.executeCLI(false, CMD_4);
        validate(output, RESULT_4, true);

        output = JPackageHelper.executeCLI(false, CMD_5);
        validate(output, RESULT_5, true);

        output = JPackageHelper.executeCLI(false, CMD_6);
        validate(output, RESULT_6, true);

    }

    private static void testMissingArgToolProvider() throws Exception {
        String output = JPackageHelper.executeToolProvider(false, CMD_1);
        validate(output, RESULT_1, true);

        output = JPackageHelper.executeToolProvider(false, CMD_2);
        validate(output, RESULT_2, true);

        output = JPackageHelper.executeToolProvider(false, CMD_3);
        validate(output, RESULT_3, false);

        output = JPackageHelper.executeToolProvider(false, CMD_4);
        validate(output, RESULT_4, true);

        output = JPackageHelper.executeToolProvider(false, CMD_5);
        validate(output, RESULT_5, true);

        output = JPackageHelper.executeToolProvider(false, CMD_6);
        validate(output, RESULT_6, true);
    }

    public static void main(String[] args) throws Exception {
        JPackageHelper.createHelloImageJar();
        testMissingArg();
        testMissingArgToolProvider();
    }

}
