/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Handling of non-existent classpath elements during dump time and run time
 * @requires vm.cds
 * @library /test/lib
 * @compile test-classes/Hello.java
 * @compile test-classes/HelloMore.java
 * @run driver NonExistClasspath
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class NonExistClasspath {
    static final String outDir = CDSTestUtils.getOutputDir();
    static final String newFile = "non-exist.jar";
    static final String nonExistPath = outDir + File.separator + newFile;
    static final String emptyJarPath = outDir + File.separator + "empty.jar";
    static final String errorMessage1 = "Unable to use shared archive";
    static final String errorMessage2 = "shared class paths mismatch";

    public static void main(String[] args) throws Exception {
        String appJar = JarBuilder.getOrCreateHelloJar();
        doTest(appJar, false);
        doTest(appJar, true);
    }

    static void doTest(String appJar, boolean bootcp) throws Exception {
        final String errorMessage3 = (bootcp ? "BOOT" : "APP") + " classpath mismatch";
        (new File(nonExistPath)).delete();

        String classPath = nonExistPath + File.pathSeparator + appJar;
        TestCommon.testDump("foobar", TestCommon.list("Hello"), make_args(bootcp, classPath));

        // The nonExistPath doesn't exist yet, so we should be able to run without problem
        TestCommon.run(make_args(bootcp,
                                 classPath,
                                 "-Xlog:class+path=trace",
                                 "Hello"))
            .assertNormalExit();

        // Replace nonExistPath with another non-existent file in the CP, it should still work
        TestCommon.run(make_args(bootcp,
                                 nonExistPath + ".duh"  + File.pathSeparator + appJar,
                                 "-Xlog:class+path=trace",
                                 "Hello"))
            .assertNormalExit();

        // Add a few more non-existent files in the CP, it should still work
        TestCommon.run(make_args(bootcp,
                                 nonExistPath + ".duh"  + File.pathSeparator +
                                 nonExistPath + ".daa"  + File.pathSeparator +
                                 nonExistPath + ".boo"  + File.pathSeparator +
                                 appJar,
                                 "-Xlog:class+path=trace",
                                 "Hello"))
            .assertNormalExit();

        // Or, remove all non-existent paths from the CP, it should still work
        TestCommon.run(make_args(bootcp,
                                 appJar,
                                 "-Xlog:class+path=trace",
                                 "Hello"))
            .assertNormalExit();

        // Now make nonExistPath exist. CDS will fail to load.
        Files.copy(Paths.get(outDir, "hello.jar"),
                   Paths.get(outDir, newFile),
                   StandardCopyOption.REPLACE_EXISTING);

        TestCommon.run(make_args(bootcp,
                                 classPath,
                                 "-Xlog:class+path=trace",
                                 "Hello"))
            .assertAbnormalExit(errorMessage1, errorMessage2, errorMessage3);

        if (bootcp) {
            doMoreBCPTests(appJar, errorMessage3);
        }
    }

    static void doMoreBCPTests(String appJar, String errorMessage3) throws Exception {

        // Dump an archive with non-existent boot class path.
        (new File(nonExistPath)).delete();
        TestCommon.testDump("foobar", TestCommon.list("Hello"), make_args(true, nonExistPath, "-cp", appJar));

        // Run with non-existent boot class path, test should pass.
        TestCommon.run(make_args(true,
                                 nonExistPath,
                                 "-cp", appJar,
                                 "-Xlog:class+path=trace",
                                 "Hello"))
            .assertNormalExit();

        // Run with existent boot class path, test should fail.
        TestCommon.run(make_args(true,
                                 appJar,
                                 "-cp", appJar,
                                 "-Xlog:class+path=trace",
                                 "Hello"))
            .assertAbnormalExit(errorMessage1, errorMessage2, errorMessage3);

        // Dump an archive with existent boot class path.
        TestCommon.testDump("foobar", TestCommon.list("Hello"), make_args(true, appJar));

        // Run with non-existent boot class path, test should fail.
        TestCommon.run(make_args(true,
                                 nonExistPath,
                                 "-Xlog:class+path=trace",
                                 "Hello"))
            .assertAbnormalExit(errorMessage1, errorMessage2, errorMessage3);

        // Run with existent boot class path, test should pass.
        TestCommon.run(make_args(true,
                                 appJar,
                                 "-Xlog:class+path=trace",
                                 "Hello"))
            .assertNormalExit();

        // Test with empty jar file.
        (new File(emptyJarPath)).delete();
        (new File(emptyJarPath)).createNewFile();

        // Dump an archive with an empty jar in the boot class path.
        TestCommon.testDump("foobar", TestCommon.list("Hello"), make_args(true, emptyJarPath, "-cp", appJar));

        // Run with an empty jar in boot class path, test should pass.
        TestCommon.run(make_args(true,
                                 emptyJarPath,
                                 "-cp", appJar,
                                 "-Xlog:class+path=trace",
                                 "Hello"))
            .assertNormalExit();

        // Run with non-existent boot class path, test should pass.
        TestCommon.run(make_args(true,
                                 nonExistPath,
                                 "-cp", appJar,
                                 "-Xlog:class+path=trace",
                                 "Hello"))
            .assertNormalExit();

        // Run with existent boot class path, test should fail.
        TestCommon.run(make_args(true,
                                 appJar,
                                 "-cp", appJar,
                                 "-Xlog:class+path=trace",
                                 "Hello"))
            .assertAbnormalExit(errorMessage1, errorMessage2, errorMessage3);
    }

    static String[] make_args(boolean bootcp, String cp, String... suffix) {
        String args[];
        if (bootcp) {
            args = TestCommon.concat("-Xbootclasspath/a:" + cp);
        } else {
            args = TestCommon.concat("-cp", cp);
        }

        return TestCommon.concat(args, suffix);
    }
}
