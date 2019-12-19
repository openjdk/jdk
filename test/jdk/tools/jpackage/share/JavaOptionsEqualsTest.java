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

import java.io.File;
import java.nio.file.Files;

/*
 * @test
 * @summary jpackage create image with --java-options test
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @build JavaOptionsBase
 * @modules jdk.incubator.jpackage
 * @run main/othervm -Xmx512m JavaOptionsEqualsTest
 */
public class JavaOptionsEqualsTest {

    private static final String app = JPackagePath.getApp();

    private static final String OUTPUT = "output";

    private static final String WARNING_1
                                   = "WARNING: Unknown module: me.mymodule.foo";

    private static final String WARNING_2
                                   = "WARNING: Unknown module: other.mod.bar";

    private static final String[] CMD = {
        "--type", "app-image",
        "--input", "input",
        "--description", "the two options below should cause two app execution "
            + "Warnings with two lines output saying: "
            + "WARNING: Unknown module: <module-name>",
        "--dest", OUTPUT,
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
        "--java-options",
        "--add-exports=java.base/sun.util=me.mymodule.foo,ALL-UNNAMED",
        "--java-options",
        "--add-exports=java.base/sun.security.util=other.mod.bar,ALL-UNNAMED",
    };

    private static void validate() throws Exception {
        File outfile = new File("app.out");

        int retVal = JPackageHelper.execute(outfile, app);
        if (retVal != 0) {
            throw new AssertionError(
                   "Test application exited with error: " + retVal);
        }

        if (!outfile.exists()) {
            throw new AssertionError(
                    "outfile: " + outfile + " was not created");
        }

        String output = Files.readString(outfile.toPath());
        System.out.println("App output:");
        System.out.print(output);

        String[] result = JPackageHelper.splitAndFilter(output);
        if (result.length < 4) {
            throw new AssertionError(
                   "Unexpected number of lines: " + result.length
                   + " - output: " + output);
        }

        String nextWarning = WARNING_1;
        if (!result[0].startsWith(nextWarning)){
            nextWarning = WARNING_2;
            if (!result[0].startsWith(WARNING_2)){
                throw new AssertionError("Unexpected result[0]: " + result[0]);
            } else {
                nextWarning = WARNING_1;
            }
        }

        if (!result[1].startsWith(nextWarning)) {
            throw new AssertionError("Unexpected result[1]: " + result[1]);
        }

        if (!result[result.length - 2].trim().endsWith("jpackage test application")) {
            throw new AssertionError("Unexpected result[2]: " + result[2]);
        }

        if (!result[result.length - 1].trim().equals("args.length: 0")) {
            throw new AssertionError("Unexpected result[3]: " + result[3]);
        }
    }

    public static void main(String[] args) throws Exception {
        JPackageHelper.createHelloImageJar();
        String output = JPackageHelper.executeCLI(true, CMD);
        validate();

        JPackageHelper.deleteOutputFolder(OUTPUT);
        output = JPackageHelper.executeToolProvider(true, CMD);
        validate();
    }

}
