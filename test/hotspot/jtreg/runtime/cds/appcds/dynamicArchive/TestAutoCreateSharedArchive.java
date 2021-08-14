/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @summary test -XX:+AutoCreateSharedArchive feature
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build Hello
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar hello.jar Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar sun.hotspot.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:./WhiteBox.jar TestAutoCreateSharedArchive
 */

import java.io.IOException;
import java.io.File;

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.cds.CDSArchiveUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class TestAutoCreateSharedArchive extends DynamicArchiveTestBase {
    private static final String BASE_NAME = CDSTestUtils.getOutputFileName("base.jsa");
    private static final String TOP_NAME  = CDSTestUtils.getOutputFileName("top.jsa");
    private static final String mainAppClass = "Hello";
    private static final String HELLO_SOURCE = "Hello source: shared objects file (top)";

    public static void main(String[] args) throws Exception {
        runTest(TestAutoCreateSharedArchive::testAutoCreateSharedArchive);
    }

    public static void checkFileExists(String fileName) throws Exception {
        File file = new File(fileName);
        if (!file.exists()) {
             throw new IOException("Archive " + file.getName() + " is not autamatically created!");
        }
    }

    public static String startNewArchive(String testName) {
        String newArchiveName = TestCommon.getNewArchiveName(testName);
        TestCommon.setCurrentArchiveName(newArchiveName);
        return newArchiveName;
    }

    public static void print(String message) {
        System.out.println(message);
    }

    private static void testAutoCreateSharedArchive() throws Exception {
        String appJar = ClassFileInstaller.getJarPath("hello.jar");

        File archiveFile = new File(TOP_NAME);
        if (archiveFile.exists()) {
          archiveFile.delete();
        }

        // 1. run with non-existing archive should automatically create dynamic archive
        print("1. run with non-existing archive should automatically create dynamic archive");
        run(TOP_NAME,
            "-Xshare:auto",
            "-XX:+AutoCreateSharedArchive",
            "-Xlog:cds",
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain("Dumping shared data to file:")
                      .shouldContain(TOP_NAME);
                });
        checkFileExists(TOP_NAME);

        // 2. run with the created dynamic archive should pass
        print("2. run with the created dynamic archive should pass");
        run(TOP_NAME,
            "-Xlog:cds",
            "-Xlog:class+load",
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain(HELLO_SOURCE);
                });

        // 3. run with the created dynamic archive with -XX:+AutoCreateSharedArchive should pass
        print("3. run with the created dynamic archive with -XX:+AutoCreateSharedArchive should pass");
        run(TOP_NAME,
            "-Xlog:cds",
            "-Xlog:class+load",
            "-Xlog:cds+dynamic=info",
            "-XX:+AutoCreateSharedArchive",
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain(HELLO_SOURCE);
                });

        // 4. run with a bad versioned archive should create dynamic archive
        print("4. run with a bad versioned archive should create dynamic archive");
        archiveFile = new File(TOP_NAME);
        String modVersion = startNewArchive("modify-version");
        File copiedJsa = CDSArchiveUtils.copyArchiveFile(archiveFile, modVersion);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetVersion, 0x00000000);

        run(modVersion,
            "-Xshare:auto",
            "-XX:+AutoCreateSharedArchive",
            "-Xlog:cds",
            "-Xlog:cds+dynamic=info",
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0);
                });
        checkFileExists(modVersion);

        // 5. run with the new created archive should pass
        print("5. run with the new created archive should pass");
         run(modVersion,
            "-Xlog:cds",
            "-Xlog:class+load",
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain(HELLO_SOURCE);
                });

         // 6. dump base archive and top archive
         print("6. dump base archive " + BASE_NAME);
         dumpBaseArchive(BASE_NAME, null)
             .assertNormalExit(output -> {
                output.shouldHaveExitValue(0);
             });
         checkFileExists(BASE_NAME);

         // 7. dump top based on base
         print("7. dump top based on base");
         dump2(BASE_NAME, TOP_NAME,
               "-Xlog:cds",
               "-cp", appJar, mainAppClass)
             .assertNormalExit(output -> {
                 output.shouldHaveExitValue(0)
                       .shouldContain("Dumping shared data to file:")
                       .shouldContain(TOP_NAME);
             });

         // 8. run with base and top"
         print("8. run with base and top");
         run2(BASE_NAME, TOP_NAME,
              "-Xlog:cds",
              "-Xlog:cds+dynamic=info",
              "-Xlog:class+load",
              "-cp", appJar,
              mainAppClass)
             .assertNormalExit(output -> {
                 output.shouldHaveExitValue(0)
                       .shouldContain(HELLO_SOURCE);
             });


         File topFile = new File(TOP_NAME);
         // 9. damaged top, regenerate top
         print("9. damaged top, regenerate top");
         String modHeader = startNewArchive("modify-header");
         copiedJsa = CDSArchiveUtils.copyArchiveFile(topFile, modHeader);
         CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetMagic, 0xffffffff);
         run2(BASE_NAME, modHeader,
              "-Xshare:auto",
              "-XX:+AutoCreateSharedArchive",
              "-Xlog:cds",
              "-Xlog:cds+dynamic=info",
              "-cp", appJar,
              mainAppClass)
              .assertNormalExit(output -> {
                  output.shouldHaveExitValue(0)
                        .shouldContain("Dumping shared data to file:")
                        .shouldContain(modHeader)
                        .shouldContain("Regenerate MethodHandle Holder classes");
              });
         File baseFile = new File(BASE_NAME);
         // 10. screw up base archive
         print("10. screw up base archive");
         String modBase = startNewArchive("modify-base");
         copiedJsa = CDSArchiveUtils.copyArchiveFile(topFile, modBase);
         CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetMagic, 0x0);
         run2(modBase, TOP_NAME,
              "-Xshare:auto",
              "-XX:+AutoCreateSharedArchive",
              "-Xlog:cds",
              "-Xlog:cds+dynamic=info",
              "-cp", appJar,
              mainAppClass)
              .assertNormalExit(output -> {
                  output.shouldContain("Unable to map shared spaces")
                        .shouldContain("Read static archive failed")
                        .shouldContain("Hello World")
                        .shouldNotContain("Dumping shared data to file:");
              });
    }
}
