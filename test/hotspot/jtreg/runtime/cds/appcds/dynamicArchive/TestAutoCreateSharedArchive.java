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
 * @bug 8261455
 * @summary test -XX:+AutoCreateSharedArchive feature
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build Hello
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar hello.jar Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar sun.hotspot.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:./WhiteBox.jar TestAutoCreateSharedArchive
 */

/*
 * -XX:SharedArchiveFile can be specified in two styles:
 *
 *  (A) Test with default base archive -XX:+SharedArchiveFile=<archive>
 *  (B) Test with the base archive is specified: -XX:SharedArchiveFile=<base>:<file>
 *  all the following if not explained explicitly, run with flag -XX:+AutoCreateSharedArchive
 *
 * 10 Case (A)
 *
 * 10.1 run with non-existing archive should automatically create dynamic archive
 *      If the JDK's default CDS archive cannot be loaded, print out warning, run continue without shared archive
 *      and no shared archive created at exit.
 * 10.2 run with the created dynamic archive should pass.
 * 10.3 run with the created dynamic archive and -XX:+AutoCreateSharedArchive should pass and no shared archive created at exit.
 *
 * 11 run with damaged magic should not regenerate dynamic archive.
 *    Bad magic of the shared archive leads the archive open as static that will not find base archive. With base archive not shared,
 *    at exit, no shared archive (top) will be generated.
 * 12 run with a bad versioned archive should create dynamic archive.
 *    A bad version of the archive still makes the archive open as dynamic so a new archive but failed to share, but base archive
 *    is shared, so the top archive  will be generated at exit.
 * 13 run with a bad jvm_ident archive should create dynamic archive
 *    The reason as 12.
 * 14 Read base archive from top archive failed
 *    If stored base archive name is not correct, it will lead the archive opened as static and no shared in runtime,
 *    also no shared archive created at exit.
 *
 * 20 (case B)
 *
 * 20.1 dump base archive which will be used for dumping top archive.
 * 20.2 dump top archive based on base archive obtained in 20.1.
 * 20.3 run -XX:SharedArchiveFile=<base>:<top> to verify the archives.
 *
 * 21 if version of top archive is not correct (not the current version), the archive cannot be shared and will be
 *    regenerated at exit.
 * 22 if base archive is not with correct version, both base and top archives will not be shared.
 *    At exit, there is no shared archive created automatically.
 */

import java.io.IOException;
import java.io.File;

import java.nio.file.attribute.FileTime;
import java.nio.file.Files;
import java.nio.file.Paths;

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
             throw new IOException("Archive " + fileName + " is not automatically created");
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

        // The list numbers try to match JDK-8272331 (CSR for JDK-8261455) test items but not exactly matched.

        // 10 non-existing archive should automatically create dynamic archive based on default shared archive
        // if base archive loaded.
        print("10 Test with default base shared archive");
        print("10.1 run with non-existing archive should automatically create dynamic archive");
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

        // 10.2 run with the created dynamic archive should pass
        print("10.2 run with the created dynamic archive should pass");
        run(TOP_NAME,
            "-Xlog:cds",
            "-Xlog:class+load",
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain(HELLO_SOURCE);
                });
        // remember the FileTime
        FileTime ft1 = Files.getLastModifiedTime(Paths.get(TOP_NAME));

        // 10.3 run with the created dynamic archive with -XX:+AutoCreateSharedArchive should pass
        print("10.3 run with the created dynamic archive with -XX:+AutoCreateSharedArchive should pass");
        run(TOP_NAME,
            "-Xlog:cds",
            "-Xlog:class+load",
            "-Xlog:cds+dynamic=info",
            "-XX:+AutoCreateSharedArchive",
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldNotContain("Dumping shared data to file")
                      .shouldContain(HELLO_SOURCE);
                });
        FileTime ft2 = Files.getLastModifiedTime(Paths.get(TOP_NAME));
        if (!ft2.equals(ft1)) {
            throw new RuntimeException("Archive file " + TOP_NAME + "  should not be updated");
        }

        // 11 run with damaged magic should not regenerate dynamic archive
        //    The bad magic will make the archive be opened as static archive
        //    and failed, no shared for base archive either.
        print("11 run with damaged magic should not regenerate dynamic archive");
        String modMagic = startNewArchive("modify-magic");
        File copiedJsa = CDSArchiveUtils.copyArchiveFile(archiveFile, modMagic);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetMagic(), 0x1234);
        ft1 = Files.getLastModifiedTime(Paths.get(modMagic));

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
        ft2 = Files.getLastModifiedTime(Paths.get(modMagic));
        if (!ft1.equals(ft2)) {
            throw new RuntimeException("Shared archive " + modMagic + " should not automatically be generated");
        }

        // 12 run with a bad versioned archive should create dynamic archive
        print("12 run with a bad versioned archive should create dynamic archive");
        String modVersion = startNewArchive("modify-version");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(archiveFile, modVersion);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetVersion(), 0x00000000);
        ft1 = Files.getLastModifiedTime(Paths.get(modVersion));

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
        ft2 = Files.getLastModifiedTime(Paths.get(modVersion));
        if (ft1.equals(ft2)) {
            throw new RuntimeException("Shared archive " + modVersion + " should automatically be generated");
        }

        // 13 run with a bad jvm_indent archive should create dynamic archive
        print("13 run with a bad jvm_ident archive should create dynamic archive");
        String modJvmIdent = startNewArchive("modify-jvmident");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(archiveFile, modJvmIdent);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetJvmIdent(), 0x00000000);
        ft1 = Files.getLastModifiedTime(Paths.get(modJvmIdent));

        run(modJvmIdent,
            "-Xshare:auto",
            "-XX:+AutoCreateSharedArchive",
            "-Xlog:cds",
            "-Xlog:cds+dynamic=info",
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0);
                });
        ft2 = Files.getLastModifiedTime(Paths.get(modJvmIdent));
        if (ft1.equals(ft2)) {
            throw new RuntimeException("Shared archive " + modJvmIdent + " should automatically be generated");
        }

        // 14 read base archive from top archive failed
        //    the failure will cause the archive be opened as static
        //    so no shared both for base and top
        print("14 read base archive from top archive failed");
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

        ft1 = Files.getLastModifiedTime(Paths.get(modBaseName));
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
        ft2 = Files.getLastModifiedTime(Paths.get(modBaseName));
        if (!ft1.equals(ft2)) {
            throw new RuntimeException("Shared archive " + modBaseName + " should not automatically be generated");
        }

        // 15 Create an archive with only dynamic magic (size of 4)
        print("15 Create an archive with only dynamic magic (size of 4)");
        String magicOnly = startNewArchive("magic-only");
        copiedJsa = CDSArchiveUtils.createMagicOnlyFile(magicOnly, false/*dynamic*/);
        ft1 = Files.getLastModifiedTime(Paths.get(magicOnly));
        run(magicOnly,
            "-Xshare:auto",
            "-XX:+AutoCreateSharedArchive",
            "-Xlog:cds",
            "-Xlog:cds+dynamic=info",
            "-cp", appJar,
            mainAppClass)
            .assertAbnormalExit(output -> {
                output.shouldHaveExitValue(1);
                output.shouldContain("Unable to read generic CDS file map header from shared archive");
                output.shouldNotContain("Dumping shared data to file:");
                });
        ft2 = Files.getLastModifiedTime(Paths.get(magicOnly));
        if (!ft1.equals(ft2)) {
            throw new RuntimeException("Shared archive " + modBaseName + " should not automatically be generated");
        }

        // delete top archive
        if (archiveFile.exists()) {
            archiveFile.delete();
        }
        // delete base archive
        File baseFile = new File(BASE_NAME);
        if (baseFile.exists()) {
            baseFile.delete();
        }
        // 20 Testing with -XX:SharedArchiveFile=base:top
        print("20 Testing with -XX:SharedArchiveFile=base:top");
        // 20.1 dump base archive and top archive
        print("20.1 dump base archive " + BASE_NAME);
        dumpBaseArchive(BASE_NAME, "-Xlog:cds")
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0);
            });
        checkFileExists(BASE_NAME);

        // 20.2 dump top based on base
        print("20.2 dump top based on base");
        dump2(BASE_NAME, TOP_NAME,
              "-Xlog:cds",
              "-cp", appJar, mainAppClass)
              .assertNormalExit(output -> {
                  output.shouldHaveExitValue(0)
                      .shouldContain("Dumping shared data to file:")
                      .shouldContain(TOP_NAME);
              });
        checkFileExists(TOP_NAME);

        // 20.3 run with base and top
        print("20.3 run with base and top");
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

        // 21 top version is not correct, regenerate top
        print("21 top version is not correct, regenerate top");
        String modHeader = startNewArchive("modify-header");
        File topFile = new File(TOP_NAME);
        copiedJsa = CDSArchiveUtils.copyArchiveFile(topFile, modHeader);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetVersion(), 0xff);
        ft1 = Files.getLastModifiedTime(Paths.get(modHeader));

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
        ft2 = Files.getLastModifiedTime(Paths.get(modHeader));
        if (ft1.equals(ft2)) {
            throw new RuntimeException("Shared archive " + modBaseName + " should automatically be generated");
        }

        // 22 screw up base archive, will not generate top
        print("22 screw up base archive, will not generate top");
        baseFile = new File(BASE_NAME);
        String modBase = startNewArchive("modify-base");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(baseFile, modBase);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetVersion(), 0xff);
        ft1 = Files.getLastModifiedTime(Paths.get(TOP_NAME));

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
        ft2 = Files.getLastModifiedTime(Paths.get(TOP_NAME));
        if (!ft1.equals(ft2)) {
            throw new RuntimeException("Shared archive " + TOP_NAME + " should not be created at exit");
        }

        // 23 create an archive like in 15
        print("23 create an archive with dynamic magic number only");
        copiedJsa = CDSArchiveUtils.createMagicOnlyFile(magicOnly, false /*dynamic*/);
        ft1 = Files.getLastModifiedTime(Paths.get(magicOnly));
        run2(BASE_NAME, magicOnly,
             "-Xshare:auto",
             "-XX:+AutoCreateSharedArchive",
             "-Xlog:cds",
             "-Xlog:cds+dynamic=info",
             "-cp", appJar,
             mainAppClass)
             .assertAbnormalExit(output -> {
                 output.shouldContain("Unable to read generic CDS file map header from shared archive")
                       .shouldNotContain("Dumping shared data to file:");
             });
        ft2 = Files.getLastModifiedTime(Paths.get(magicOnly));
        if (!ft1.equals(ft2)) {
            throw new RuntimeException("Shared archive " + magicOnly + " should not be created at exit");
        }
    }
}
