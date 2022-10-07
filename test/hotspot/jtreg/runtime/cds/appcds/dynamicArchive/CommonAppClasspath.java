/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @build jdk.test.whitebox.WhiteBox
 * @compile ../test-classes/Hello.java
 * @compile ../test-classes/HelloMore.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. CommonAppClasspath
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import jdk.test.lib.cds.CDSTestUtils;

public class CommonAppClasspath extends DynamicArchiveTestBase {

    private static final Path USER_DIR = Paths.get(CDSTestUtils.getOutputDir());

    public static void main(String[] args) throws Exception {
        runTest(CommonAppClasspath::testDefaultBase);
    }

    static void testDefaultBase() throws Exception {
        String topArchiveName = getNewArchiveName("top");
        doTest(topArchiveName);
    }

    private static void doTest(String topArchiveName) throws Exception {
        String mainClass = "HelloMore";
        String appJar = JarBuilder.getOrCreateHelloJar();
        String appJar2 = JarBuilder.build("AppendClasspath_HelloMore", mainClass);

        // dump an archive with both jars in the original location
        String jars = appJar + File.pathSeparator + appJar2;
        dump(topArchiveName,
             "-Xlog:cds",
             "-Xlog:cds+dynamic=debug",
             "-cp", jars, mainClass)
            .assertNormalExit(output -> {
                    output.shouldContain("Written dynamic archive 0x");
                });

        // copy hello.jar to USER_DIR/deploy
        Path srcPath = Paths.get(appJar);
        Path newPath = Paths.get(USER_DIR.toString() + File.separator + "deploy");
        Path destDir = Files.createDirectory(newPath);
        int idx = appJar.lastIndexOf(File.separator);
        String jarName = appJar.substring(idx + 1);
        Path destPath = destDir.resolve(jarName);
        Files.copy(srcPath, destPath, REPLACE_EXISTING, COPY_ATTRIBUTES);

        // copy AppendClasspath_HelloMore.jar to USER_DIR/deploy
        idx = appJar2.lastIndexOf(File.separator);
        jarName = appJar2.substring(idx + 1);
        srcPath = Paths.get(appJar2);
        Path destPath2 = destDir.resolve(jarName);
        Files.copy(srcPath, destPath2, REPLACE_EXISTING, COPY_ATTRIBUTES);

        // Run with both jars relocated to USER_DIR/dpeloy - should PASS
        jars = destPath.toString() + File.pathSeparator + destPath2.toString();
        run(topArchiveName,
            "-Xlog:class+load",
            "-Xlog:cds+dynamic=debug,cds=debug",
            "-cp", jars, mainClass)
            .assertNormalExit(output -> {
                    output.shouldContain("Hello source: shared objects file")
                          .shouldContain("HelloMore source: shared objects file")
                          .shouldHaveExitValue(0);
                });

        // Run with relocation of only the second jar - should FAIL
        jars = appJar + File.pathSeparator + destPath2.toString();
        run(topArchiveName,
            "-Xlog:class+load",
            "-Xlog:cds+dynamic=debug,cds=debug",
            "-cp", jars, mainClass)
        .assertAbnormalExit(output -> {
                output.shouldContain("shared class paths mismatch")
                      .shouldHaveExitValue(1);
            });

        // Run with relocation of only the first jar - should FAIL
        jars = destPath.toString() + File.pathSeparator + appJar2;
        run(topArchiveName,
            "-Xlog:class+load",
            "-Xlog:cds+dynamic=debug,cds=debug",
            "-cp", jars, mainClass)
        .assertAbnormalExit(output -> {
                output.shouldContain("shared class paths mismatch")
                      .shouldHaveExitValue(1);
            });

        // Dump CDS archive with the first jar relocated.
        jars = destPath.toString() + File.pathSeparator + appJar2;
        dump(topArchiveName,
             "-Xlog:cds",
             "-Xlog:cds+dynamic=debug",
             "-cp", jars, mainClass)
            .assertNormalExit(output -> {
                    output.shouldContain("Written dynamic archive 0x");
                });

        // Run with first jar relocated - should PASS
        jars = destPath.toString() + File.pathSeparator + appJar2;
        run(topArchiveName,
            "-Xlog:class+load",
            "-Xlog:cds+dynamic=debug,cds=debug",
            "-cp", jars, mainClass)
            .assertNormalExit(output -> {
                    output.shouldContain("Hello source: shared objects file")
                          .shouldContain("HelloMore source: shared objects file")
                          .shouldHaveExitValue(0);
                });

        // Run with both jars relocated - should FAIL
        jars = destPath.toString() + File.pathSeparator + destPath2.toString();
        run(topArchiveName,
            "-Xlog:class+load",
            "-Xlog:cds+dynamic=debug,cds=debug",
            "-cp", jars, mainClass)
        .assertAbnormalExit(output -> {
                output.shouldContain("shared class paths mismatch")
                      .shouldHaveExitValue(1);
            });

    }
}
