/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 8279366
 * @summary Test app class paths checking with the longest common path taken into account.
 * @requires vm.cds
 * @library /test/lib
 * @compile test-classes/Hello.java
 * @compile test-classes/HelloMore.java
 * @run driver CommonAppClasspath
 */

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.test.lib.cds.CDSTestUtils;

public class CommonAppClasspath {

    private static final Path USER_DIR = Paths.get(CDSTestUtils.getOutputDir());
    private static final String failedMessage = "Archived app classpath validation: failed";
    private static final String successMessage1 = "Hello source: shared objects file";
    private static final String successMessage2 = "HelloMore source: shared objects file";

    private static void runtimeTest(String classPath, String mainClass, int expectedExitValue,
                                    String ... checkMessages) throws Exception {
        CDSTestUtils.Result result = TestCommon.run(
            "-Xshare:on",
            "-XX:SharedArchiveFile=" + TestCommon.getCurrentArchiveName(),
            "-cp", classPath,
            "-Xlog:class+load=trace,class+path=info",
            mainClass);
        if (expectedExitValue == 0) {
            result.assertNormalExit( output -> {
                for (String s : checkMessages) {
                    output.shouldContain(s);
                }
            });
        } else {
            result.assertAbnormalExit( output -> {
                for (String s : checkMessages) {
                    output.shouldContain(s);
                }
            });
        }
    }

    public static void main(String[] args) throws Exception {
        String appJar = JarBuilder.getOrCreateHelloJar();
        String appJar2 = JarBuilder.build("AppendClasspath_HelloMore", "HelloMore");
        // dump an archive with only appJar in the original location
        String jars = appJar;
        TestCommon.testDump(jars, TestCommon.list("Hello"));

        // copy hello.jar to tmp dir
        Path destPath = CDSTestUtils.copyFile(appJar, System.getProperty("java.io.tmpdir"));

        // Run with appJar relocated to tmp dir - should PASS
        runtimeTest(destPath.toString(), "Hello", 0, successMessage1);

        // dump an archive with both jars in the original location
        jars = appJar + File.pathSeparator + appJar2;
        TestCommon.testDump(jars, TestCommon.list("Hello", "HelloMore"));

        // copy hello.jar to USER_DIR/deploy
        String newDir = USER_DIR.toString() + File.separator + "deploy";
        destPath = CDSTestUtils.copyFile(appJar, newDir);

        // copy AppendClasspath_HelloMore.jar to USER_DIR/deploy
        Path destPath2 = CDSTestUtils.copyFile(appJar2, newDir);

        // Run with both jars relocated to USER_DIR/dpeloy - should PASS
        runtimeTest(destPath.toString() + File.pathSeparator + destPath2.toString(),
                    "HelloMore", 0, successMessage1, successMessage2);

        // Run with relocation of only the second jar - should FAIL
        runtimeTest(appJar + File.pathSeparator + destPath2.toString(),
                    "HelloMore", 1, failedMessage);

        // Run with relocation of only the first jar - should FAIL
        runtimeTest(destPath.toString() + File.pathSeparator + appJar2,
                    "HelloMore", 1, failedMessage);

        // Dump CDS archive with the first jar relocated.
        jars = destPath.toString() + File.pathSeparator + appJar2;
        TestCommon.testDump(jars, TestCommon.list("Hello", "HelloMore"));

        // Run with first jar relocated - should PASS
        runtimeTest(destPath.toString() + File.pathSeparator + appJar2,
                    "HelloMore", 0, successMessage1, successMessage2);

        // Run with both jars relocated - should FAIL
        runtimeTest(destPath.toString() + File.pathSeparator + destPath2.toString(),
                    "HelloMore", 1, failedMessage);

        // Copy hello.jar to USER_DIR/a
        destPath = CDSTestUtils.copyFile(appJar, USER_DIR.toString() + File.separator + "a");

        // copy AppendClasspath_HelloMore.jar to USER_DIR/aa
        destPath2 = CDSTestUtils.copyFile(appJar2, USER_DIR.toString() + File.separator + "aa");

        // Dump CDS archive with the both jar files relocated
        // appJar to USER_DIR/a
        // appJar2 to USER_DIR/aa
        jars = destPath.toString() + File.pathSeparator + destPath2.toString();
        TestCommon.testDump(jars, TestCommon.list("Hello", "HelloMore"));

        // Copy hello.jar to USER_DIR/x/a
        Path runPath = CDSTestUtils.copyFile(appJar, USER_DIR.toString() + File.separator + "x" + File.separator + "a");

        // copy AppendClasspath_HelloMore.jar to USER_DIR/x/aa
        Path runPath2= CDSTestUtils.copyFile(appJar2, USER_DIR.toString() + File.separator + "x" + File.separator + "aa");

        // Run with both jars relocated to USER_DIR/x/a and USER_DIR/x/aa dirs - should PASS
        runtimeTest(runPath.toString() + File.pathSeparator + runPath2.toString(),
                    "HelloMore", 0, successMessage1, successMessage2);

        // copy AppendClasspath_HelloMore.jar to USER_DIR/x/a
        runPath2= CDSTestUtils.copyFile(appJar2, USER_DIR.toString() + File.separator + "x" + File.separator + "a");

        // Run with both jars relocated to USER_DIR/x/a dir - should FAIL
        runtimeTest(runPath.toString() + File.pathSeparator + runPath2.toString(),
                    "HelloMore", 1, failedMessage);
    }
}
