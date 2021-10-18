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

        // 0. run with non-existing archive should automatically create dynamic archive
        print("0. run with non-existing archive should automatically create dynamic archive");
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

        // 1. run with the created dynamic archive should pass
        print("1. run with the created dynamic archive should pass");
        run(TOP_NAME,
            "-Xlog:cds",
            "-Xlog:class+load",
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain(HELLO_SOURCE);
                });

        // 2. run with the created dynamic archive with -XX:+AutoCreateSharedArchive should pass
        print("2. run with the created dynamic archive with -XX:+AutoCreateSharedArchive should pass");
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

        // 3. run with a bad versioned archive should create dynamic archive
        print("3. run with a bad versioned archive should create dynamic archive");
        archiveFile = new File(TOP_NAME);
        String modVersion = startNewArchive("modify-version");
        File copiedJsa = CDSArchiveUtils.copyArchiveFile(archiveFile, modVersion);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetVersion(), 0x00000000);

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

        // 4. run with the new created archive should pass
        print("4. run with the new created archive should pass");
        run(modVersion,
            "-Xlog:cds",
            "-Xlog:class+load",
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain(HELLO_SOURCE);
                });

         // 5. run with damaged magic should not regenerate dynamic archive
         //    The bad magic will make the archive be opened as static archive
         //    and failed, no shared for base archive either.
         print("5. run with damaged magic should not regenerate dynamic archive");
         String modMagic = startNewArchive("modify-magic");
         copiedJsa = CDSArchiveUtils.copyArchiveFile(archiveFile, modMagic);
         CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetMagic(), 0x1234);

         run(modMagic,
            "-Xshare:auto",
            "-XX:+AutoCreateSharedArchive",
            "-Xlog:cds",
            "-Xlog:cds+dynamic=info",
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0);
                output.shouldNotContain("Dumping shared data to file");
                });

         // 6. read base archive from top archive failed
         //    the failure will cause the archive be opened as static
         //    so no shared both for static and dynamic
         print("6. read base archive from top archive failed");
         String modBaseName = startNewArchive("modify-basename");
         copiedJsa = CDSArchiveUtils.copyArchiveFile(archiveFile, modBaseName);
         int nameSize = CDSArchiveUtils.baseArchiveNameSize(copiedJsa);
         int offset = CDSArchiveUtils.baseArchivePathOffset(copiedJsa);
         StringBuilder sb = new StringBuilder();
         for (int i = 0; i < nameSize - 4; i++) {
             sb.append('Z');
         }
         sb.append(".jsa");
         sb.append('\0');
         String newName = sb.toString();
         CDSArchiveUtils.writeData(copiedJsa, offset, newName.getBytes());

         run(modBaseName,
            "-Xshare:auto",
            "-XX:+AutoCreateSharedArchive",
            "-Xlog:cds",
            "-Xlog:cds+dynamic=info",
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0);
                output.shouldNotContain("Dumping shared data to file");
                });

         // 7. dump base archive and top archive
         print("7. dump base archive " + BASE_NAME);
         dumpBaseArchive(BASE_NAME, "-Xlog:cds")
             .assertNormalExit(output -> {
                output.shouldHaveExitValue(0);
             });
         checkFileExists(BASE_NAME);

         // 8. dump top based on base
         print("8. dump top based on base");
         dump2(BASE_NAME, TOP_NAME,
               "-Xlog:cds",
               "-cp", appJar, mainAppClass)
             .assertNormalExit(output -> {
                 output.shouldHaveExitValue(0)
                       .shouldContain("Dumping shared data to file:")
                       .shouldContain(TOP_NAME);
             });

         // 9. run with base and top"
         print("9. run with base and top");
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
         // 10. damaged top, regenerate top
         print("10. damaged top, regenerate top");
         String modHeader = startNewArchive("modify-header");
         copiedJsa = CDSArchiveUtils.copyArchiveFile(topFile, modHeader);
         CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetVersion(), 0xff);
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
         // 11. screw up base archive, will not generate top
         print("11. screw up base archive, will not generate top");
         File baseFile = new File(BASE_NAME);
         String modBase = startNewArchive("modify-base");
         copiedJsa = CDSArchiveUtils.copyArchiveFile(baseFile, modBase);
         CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetVersion(), 0xff);
         run2(modBase, TOP_NAME,
              "-Xshare:auto",
              "-XX:+AutoCreateSharedArchive",
              "-Xlog:cds",
              "-Xlog:cds+dynamic=info",
              "-cp", appJar,
              mainAppClass)
              .assertNormalExit(output -> {
                  output.shouldContain("The shared archive file has the wrong version")
                        .shouldContain("Initialize static archive failed")
                        .shouldContain("Unable to map shared spaces")
                        .shouldContain("Hello World")
                        .shouldNotContain("Dumping shared data to file:");
              });
    }
}
