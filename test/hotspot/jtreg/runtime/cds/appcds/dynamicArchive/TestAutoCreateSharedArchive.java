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
 * @bug 8261455
 * @summary test -XX:+AutoCreateSharedArchive feature
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build Hello
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar hello.jar Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar sun.hotspot.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:./WhiteBox.jar TestAutoCreateSharedArchive verifySharedSpacesOff
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:./WhiteBox.jar TestAutoCreateSharedArchive verifySharedSpacesOn
 */

/*
 * -XX:SharedArchiveFile can be specified in two styles:
 *
 *  (A) Test with default base archive -XX:+SharedArchiveFile=<archive>
 *  (B) Test with the base archive specified: -XX:SharedArchiveFile=<base>:<top>
 *  all the following if not explained explicitly, run with flag -XX:+AutoCreateSharedArchive
 *
 *  Note VerifySharedSpaces will affect output so the tests run twice: one with -XX:+VerifySharedSpaces and the other with -XX:-VerifySharedSpaces
 *
 * 10 Case (A)
 *
 *   10.01 run with non-existing archive should automatically create dynamic archive.
 *        If the JDK's default CDS archive cannot be loaded, print out warning, run continue without shared archive and no shared archive created at exit.
 *   10.02 run with the created dynamic archive should pass.
 *   10.03 run with the created dynamic archive and -XX:+AutoCreateSharedArchive should pass and no shared archive created at exit.
 *
 * 11 run with static archive.
 *    run with static archive should printout warning and continue, share or no share depends on the archive validation at exit,
 *    no shared archive (top) will be generated.
 *
 * 12 run with damaged magic should not regenerate dynamic archive.
 *    if magic is not expected, no shared archive will be regenerated at exit.
 *
 * 13 run with a bad versioned archive.
 *   13.01  run with a bad versioned (< CDS_GENERIC_HEADER_SUPPORTED_MIN_VERSION) archive should not create dynamic archive at exit.
 *   13.02  run with a bad versioned (> CDS_GENERIC_HEADER_SUPPORTED_MIN_VERSION) archive should create dynamic archive at exit.
 *
 * 14 run with an archive whose base name is not matched, no shared archive at exit.
 *
 * 15 run with an archive whose jvm_ident is corrupted should
 *     create dynamic archive at exit with -XX:-VerifySharedSpaces
 *     not create dynamic archive at exit with -XX:+VerifySharedSpaces
 *
 * 16 run with an archive only containing magic in the file (size of 4 bytes)
 *    the archive will be created at exit.
 *
 * 20 (case B)
 *
 *   20.01 dump base archive which will be used for dumping top archive.
 *   20.02 dump top archive based on base archive obtained in 20.1.
 *   20.03 run -XX:SharedArchiveFile=<base>:<top> to verify the archives.
 *   20.04 run with -XX:SharedArchveFile=base:top (reversed)
 *
 * 21 Mismatched versions
 *   21.01 if version of top archive is higher than CDS_GENERIC_HEADER_SUPPORTED_MIN_VERSION, the archive cannot be shared and will be
 *         regenerated at exit.
 *   21.02 if version of top archive is lower than CDS_GENERIC_HEADER_SUPPORTED_MIN_VERSION, the archive cannot be shared and will be
 *         created at exit.
 *
 * 22 create an archive with dynamic magic number only
 *    archive will be created at exit if base can be shared.
 *
 * 23  mismatched jvm_indent in base/top archive
 *     23.01 mismatched jvm_indent in top archive
 *     23.02 mismatched jvm_indent in base archive
 *
 * 24 run with non-existing shared archives
 *   24.01 run -Xshare:auto -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=base.jsa:non-exist-top.jsa
 *     The top archive will be regenerated.
 *   24.02 run -Xshare:auto -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=non-exist-base.jsa:top.jsa
 *     top archive will not be shared if base archive failed to load.
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

import jtreg.SkippedException;

public class TestAutoCreateSharedArchive extends DynamicArchiveTestBase {
    private static final String BASE_NAME = CDSTestUtils.getOutputFileName("base.jsa");
    private static final String TOP_NAME  = CDSTestUtils.getOutputFileName("top.jsa");
    private static final String mainAppClass = "Hello";
    private static final String HELLO_SOURCE = "Hello source: shared objects file (top)";
    private static final String HELLO_WORLD = "Hello World";
    private static boolean verifyOn = false;

    private static int   genericHeaderMinVersion = CDSArchiveUtils.getGenericHeaderMinVersion();
    private static int   currentCDSVersion = CDSArchiveUtils.getCurrentCDSArchiveVersion();

    public static void main(String[] args) throws Exception {
        if (isUseSharedSpacesDisabled()) {
            throw new SkippedException("Skipped -- This test is not applicable when JTREG tests are executed with -Xshare:off, or if the JDK doesn't have a default archive.");
        }
        if (args.length != 1 || (!args[0].equals("verifySharedSpacesOff") && !args[0].equals("verifySharedSpacesOn"))) {
            throw new RuntimeException("Must run with verifySharedSpacesOff or verifySharedSpacesOn");
        }
        verifyOn = args[0].equals("verifySharedSpacesOn");
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
        boolean fileModified = false;

        String verifySharedSpaces = verifyOn ? "-XX:+VerifySharedSpaces" : "-XX:-VerifySharedSpaces";
        File archiveFile = new File(TOP_NAME);
        if (archiveFile.exists()) {
          archiveFile.delete();
        }

        // dump a static archive, used later.
        // 0. Dump a static archive
        print("0. dump a static archive " + BASE_NAME);
        dumpBaseArchive(BASE_NAME,
                        "-Xlog:cds",
                        "-cp", appJar,
                        mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0);
            });
        checkFileExists(BASE_NAME);

        // The list numbers try to match JDK-8272331 (CSR for JDK-8261455) test items but not exactly matched.

        // 10 non-existing archive should automatically create dynamic archive based on default shared archive
        // if base archive loaded.
        print("10 Test with default base shared archive");
        print("    10.01 run with non-existing archive should automatically create dynamic archive");
        File fileTop = new File(TOP_NAME);
        if (fileTop.exists()) {
            fileTop.delete();
        }
        run(TOP_NAME,
            "-Xshare:auto",
            "-XX:+AutoCreateSharedArchive",
            "-Xlog:cds",
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain(HELLO_WORLD)
                      .shouldContain("Dumping shared data to file:")
                      .shouldContain(TOP_NAME);
                });
        checkFileExists(TOP_NAME);

        //10.02 run with the created dynamic archive should pass
        print("    10.02 run with the created dynamic archive should pass");
        run(TOP_NAME,
            "-Xlog:cds",
            "-Xlog:class+load",
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain(HELLO_WORLD)
                      .shouldContain(HELLO_SOURCE);
                });
        // remember the FileTime
        FileTime ft1 = Files.getLastModifiedTime(Paths.get(TOP_NAME));

        // 10.03 run with the created dynamic archive with -XX:+AutoCreateSharedArchive should pass
        //      archive should not be created again.
        print("    10.03 run with the created dynamic archive with -XX:+AutoCreateSharedArchive should pass");
        run(TOP_NAME,
            "-Xlog:cds",
            "-Xlog:class+load",
            "-Xlog:cds+dynamic=info",
            "-XX:+AutoCreateSharedArchive",
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain(HELLO_WORLD)
                      .shouldContain(HELLO_SOURCE)
                      .shouldNotContain("Dumping shared data to file");
                });
        FileTime ft2 = Files.getLastModifiedTime(Paths.get(TOP_NAME));
        fileModified = !ft2.equals(ft1);
        if (fileModified) {
            throw new RuntimeException("Archive file " + TOP_NAME + "  should not be updated");
        }

        // 11 run with static archive
        print("11 run with static archive");
        ft1 = Files.getLastModifiedTime(Paths.get(BASE_NAME));
        run(BASE_NAME,
            "-Xlog:cds",
            "-Xlog:cds+dynamic=info",
            "-Xshare:auto",
            "-XX:+AutoCreateSharedArchive",
            verifySharedSpaces,
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain(HELLO_WORLD)
                      .shouldContain("AutoCreateSharedArchive is ignored because " + BASE_NAME + " is a static archive")
                      .shouldNotContain("Dumping shared data to file");
                });
        ft2 = Files.getLastModifiedTime(Paths.get(BASE_NAME));
        fileModified = !ft1.equals(ft2);
        if (fileModified) {
            throw new RuntimeException("Run -XX:+AutoCreateSharedArchive on static archive create new archive");
        }

        // 12 run with damaged magic should not regenerate dynamic archive
        print("12 run with damaged magic should not regenerate dynamic archive");
        String modMagic = startNewArchive("modify-magic");
        File copiedJsa = CDSArchiveUtils.copyArchiveFile(archiveFile, modMagic);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetMagic(), 0x1234);
        ft1 = Files.getLastModifiedTime(Paths.get(modMagic));

        run(modMagic,
            "-Xshare:auto",
            "-XX:+AutoCreateSharedArchive",
            "-Xlog:cds",
            "-Xlog:cds+dynamic=info",
            verifySharedSpaces,
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain(HELLO_WORLD)
                      .shouldNotContain("Dumping shared data to file");
                });
        ft2 = Files.getLastModifiedTime(Paths.get(modMagic));
        fileModified = !ft1.equals(ft2);
        if (fileModified) {
            throw new RuntimeException("Shared archive " + modMagic + " should not automatically be generated");
        }

        // 13 run with a bad versioned (< genericHeaderMinVersion) archive
        print("13 run with a bad versioned archive");
        print("    13.01 run with a bad versioned (< genericHeaderMinVersion) archive should not create new archive");
        String modVersion = startNewArchive("modify-version-b");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(archiveFile, modVersion);
        final int version1 = genericHeaderMinVersion - 1;
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetVersion(), version1);
        ft1 = Files.getLastModifiedTime(Paths.get(modVersion));

        run(modVersion,
            "-Xshare:auto",
            "-XX:+AutoCreateSharedArchive",
            "-Xlog:cds",
            "-Xlog:cds+dynamic=info",
            verifySharedSpaces,
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain(HELLO_WORLD)
                      .shouldContain("Cannot handle shared archive file version " + version1 + ". Must be at least " + genericHeaderMinVersion)
                      .shouldContain("Unable to use shared archive: invalid archive")
                      .shouldNotContain("Dumping shared data to file");
            });
        ft2 = Files.getLastModifiedTime(Paths.get(modVersion));
        fileModified = !ft1.equals(ft2);
        if (fileModified) {
            throw new RuntimeException("Run -XX:+AutoCreateSharedArchive with lower version archive " + modVersion + " should not create new archive");
        }
        //    13.02 run with a bad versioned (> currentCDSVersion) archive
        print("    13.02 run with a bad versioned (> currentCDSVersion) archive");
        modVersion = startNewArchive("modify-version-d");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(archiveFile, modVersion);
        final int version2 = currentCDSVersion + 1;
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetVersion(), version2);
        ft1 = Files.getLastModifiedTime(Paths.get(modVersion));

        run(modVersion,
            "-Xshare:auto",
            "-XX:+AutoCreateSharedArchive",
            "-Xlog:cds",
            "-Xlog:cds+dynamic=info",
            verifySharedSpaces,
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain(HELLO_WORLD)
                      .shouldContain("The shared archive file version " + version2 + " does not match the required version " + currentCDSVersion)
                      .shouldContain("UseSharedSpaces: The shared archive file has the wrong version")
                      .shouldContain("UseSharedSpaces: Initialize dynamic archive failed")
                      .shouldContain("Dumping shared data to file");
            });
        ft2 = Files.getLastModifiedTime(Paths.get(modVersion));
        fileModified = !ft1.equals(ft2);
        if (!fileModified) {
            throw new RuntimeException("Run -XX:+AutoCreateSharedArchive with higher version archive " + modVersion + " should create new archive");
        }

        // 14 run with an archive whose base name is not matched, no share
        print("14 run with an archive whose base name is not matched, no share");
        String baseNameMismatch= startNewArchive("basename-mismatch");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(archiveFile, baseNameMismatch);
        int nameSize = CDSArchiveUtils.baseArchiveNameSize(copiedJsa);
        int offset = CDSArchiveUtils.baseArchiveNameOffset(copiedJsa);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nameSize - 4; i++) {
            sb.append('Z');
        }
        sb.append(".jsa");
        sb.append('\0');
        String newName = sb.toString();
        CDSArchiveUtils.writeData(copiedJsa, offset, newName.getBytes());

        ft1 = Files.getLastModifiedTime(Paths.get(baseNameMismatch));
        run(baseNameMismatch,
            "-Xshare:auto",
            "-XX:+AutoCreateSharedArchive",
            "-Xlog:cds",
            "-Xlog:cds+dynamic=info",
            verifySharedSpaces,
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain(HELLO_WORLD)
                      .shouldNotContain("Dumping shared data to file");
                });
        ft2 = Files.getLastModifiedTime(Paths.get(baseNameMismatch));

        fileModified = !ft1.equals(ft2);
        if (fileModified) {
            throw new RuntimeException("Shared archive " + baseNameMismatch+ " should not automatically be generated");
        }

        // 15 mismatched jvm_indent in archive, create (-VerifySharedSpaces) or not (-XX:+VerifySharedSpaces) create the new archive
        print("15 mismatched jvm_indent in archive, " + (verifyOn ? "-XX:+VerifySharedSpaces not " : "-XX:-VerifySharedSpaces ") + "create new archive");
        String modJvmIdent = startNewArchive("modify-jvmident");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(archiveFile, modJvmIdent);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetJvmIdent(), 0x65656565);
        ft1 = Files.getLastModifiedTime(Paths.get(modJvmIdent));

        run(modJvmIdent,
            "-Xshare:auto",
            "-XX:+AutoCreateSharedArchive",
            "-Xlog:cds",
            "-Xlog:cds+dynamic=info",
            verifySharedSpaces,
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0);
                if (verifyOn) {
                    output.shouldContain("UseSharedSpaces: Header checksum verification failed")
                          .shouldContain("Unable to use shared archive: invalid archive")
                          .shouldNotContain("Dumping shared data to file");
                } else {
                    output.shouldContain(HELLO_WORLD)
                          .shouldContain("Dumping shared data to file");
                }
            });
        ft2 = Files.getLastModifiedTime(Paths.get(modJvmIdent));
        fileModified = !ft1.equals(ft2);
        if (verifyOn) {
            if (fileModified) {
                throw new RuntimeException("Shared archive " + modJvmIdent + " should not be generated");
            }

        } else {
            if (!fileModified) {
                throw new RuntimeException("Shared archive " + modJvmIdent + " should be generated");
            }
        }

        // 16 run with an archive of only containing dynamic magic (size of 4) will not create new archive at exit
        print("16 run with an archive of only containing dynamic magic (size of 4) will not create new archive at exit");
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
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain(HELLO_WORLD)
                      .shouldContain("Unable to read generic CDS file map header from shared archive")
                      .shouldNotContain("Dumping shared data to file:");
                });
        ft2 = Files.getLastModifiedTime(Paths.get(magicOnly));
        fileModified = !ft1.equals(ft2);
        if (fileModified) {
            throw new RuntimeException("Shared archive " + magicOnly + " should not automatically be generated");
        }

        // Do some base tests for -XX:SharedArchiveFile=base:top, they should be same as default archive as base.
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
        // 20.01 dump base archive and top archive
        print("    20.01 dump base archive " + BASE_NAME);
        dumpBaseArchive(BASE_NAME, "-Xlog:cds")
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0);
            });
        checkFileExists(BASE_NAME);

        // 20.02 dump top based on base
        print("    20.02 dump top based on base");
        dump2(BASE_NAME, TOP_NAME,
              "-Xlog:cds",
              "-cp", appJar, mainAppClass)
              .assertNormalExit(output -> {
                  output.shouldHaveExitValue(0)
                        .shouldContain("Dumping shared data to file:")
                        .shouldContain(TOP_NAME);
              });
        checkFileExists(TOP_NAME);

        // 20.03 run with -XX:SharedArchveFile=base:top
        print("    20.03 run with -XX:SharedArchveFile=base:top");
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

        // 20.04 run with -XX:SharedArchveFile=top:base (reversed)
        print("    20.04 run with -XX:SharedArchveFile=top:base (reversed)");
        run2(TOP_NAME, BASE_NAME,
             "-Xlog:cds",
             "-Xlog:cds+dynamic=info",
             "-Xlog:class+load",
             "-cp", appJar,
             mainAppClass)
            .assertAbnormalExit(output -> {
                output.shouldHaveExitValue(1)
                      .shouldContain("Not a base shared archive: " + TOP_NAME)
                      .shouldContain("An error has occurred while processing the shared archive file")
                      .shouldNotContain(HELLO_WORLD);
            });

        // 21 Mismatched versions
        print("21 Mismatched versions");
        //   21.01 top version is lower than CDS_GENERIC_HEADER_SUPPORTED_MIN_VERSION, regenerate top
        print("  21.01 top version is lower than CDS_GENERIC_HEADER_SUPPORTED_MIN_VERSION, regenerate top");
        String versionB = startNewArchive("modify-version-B");
        archiveFile = new File(TOP_NAME);
        copiedJsa = CDSArchiveUtils.copyArchiveFile(archiveFile, versionB);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetVersion(), version1);
        ft1 = Files.getLastModifiedTime(Paths.get(versionB));

        run2(BASE_NAME, versionB,
             "-Xshare:auto",
             "-XX:+AutoCreateSharedArchive",
             "-Xlog:cds",
             "-Xlog:cds+dynamic=info",
             verifySharedSpaces,
             "-cp", appJar,
             mainAppClass)
             .assertNormalExit(output -> {
                 output.shouldHaveExitValue(0)
                       .shouldContain(HELLO_WORLD)
                       .shouldContain("Cannot handle shared archive file version " + version1)
                       .shouldContain(versionB)
                       .shouldContain("Dumping shared data to file:");
             });
        ft2 = Files.getLastModifiedTime(Paths.get(versionB));
        fileModified = !ft1.equals(ft2);
        if (!fileModified) {
            throw new RuntimeException("Shared archive " + versionB + " should automatically be generated");
        }

        //   21.02 top version is higher than CDS_GENERIC_HEADER_SUPPORTED_MIN_VERSION, no share for top, create archive at exit
        print("  21.02 top version is higher than CDS_GENERIC_HEADER_SUPPORTED_MIN_VERSION, no share for top, create archive at exit");
        String versionF = startNewArchive("versionF");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(archiveFile, versionF);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetVersion(), version2);
        ft1 = Files.getLastModifiedTime(Paths.get(versionF));
        run2(BASE_NAME, versionF,
             "-Xshare:auto",
             "-XX:+AutoCreateSharedArchive",
             "-Xlog:cds",
             "-Xlog:cds+dynamic=info",
             verifySharedSpaces,
             "-cp", appJar,
             mainAppClass)
             .assertNormalExit(output -> {
                 output.shouldContain("The shared archive file version " + version2 + " does not match the required version " + currentCDSVersion)
                       .shouldContain(HELLO_WORLD)
                       .shouldContain("Dumping shared data to file:");
             });
        ft2 = Files.getLastModifiedTime(Paths.get(versionB));
        fileModified = !ft1.equals(ft2);
        if (!fileModified) {
            throw new RuntimeException("Shared archive " + versionB + " should be created at exit");
        }

        // 22 create an archive with dynamic magic number only
        //    archive will be created at exit if base can be shared.
        print("22 create an archive with dynamic magic number only");
        copiedJsa = CDSArchiveUtils.createMagicOnlyFile(magicOnly, false /*dynamic*/);
        ft1 = Files.getLastModifiedTime(Paths.get(magicOnly));
        run2(BASE_NAME, magicOnly,
             "-Xshare:auto",
             "-XX:+AutoCreateSharedArchive",
             "-Xlog:cds",
             "-Xlog:cds+dynamic=info",
             verifySharedSpaces,
             "-cp", appJar,
             mainAppClass)
             .assertNormalExit(output -> {
                 output.shouldContain(HELLO_WORLD)
                       .shouldContain("Unable to read generic CDS file map header from shared archive")
                       .shouldContain("Dumping shared data to file:");
             });
        ft2 = Files.getLastModifiedTime(Paths.get(magicOnly));
        fileModified = !ft1.equals(ft2);
        if (!fileModified) {
            throw new RuntimeException("Shared archive " + magicOnly + " should be created at exit");
        }

        // 23  mismatched jvm_indent in top or base archive
        //    23.01 mismatched jvm_indent in top archive
        print("    23.01  mismatched jvm_indent in top archive");
        String modJvmIdentTop = startNewArchive("modify-jvmident-top");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(archiveFile, modJvmIdentTop);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetJvmIdent(), 0x65656565);
        ft1 = Files.getLastModifiedTime(Paths.get(modJvmIdentTop));

        run2(BASE_NAME, modJvmIdentTop,
            "-Xshare:auto",
            "-XX:+AutoCreateSharedArchive",
            "-Xlog:cds",
            "-Xlog:cds+dynamic=info",
            verifySharedSpaces,
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0);
                if (verifyOn) {
                    output.shouldContain("UseSharedSpaces: Header checksum verification failed");
                }
                output.shouldContain(HELLO_WORLD)
                      .shouldContain("Dumping shared data to file");
            });
        ft2 = Files.getLastModifiedTime(Paths.get(modJvmIdentTop));
        fileModified = !ft1.equals(ft2);
        if (!fileModified) {
            throw new RuntimeException("Shared archive " + modJvmIdentTop + " should be generated");
        }
        //    23.02 mismatched jvm_indent in base archive
        print("    23.02  mismatched jvm_indent in base archive");
        String modJvmIdentBase = startNewArchive("modify-jvmident-base");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(new File(BASE_NAME), modJvmIdentBase);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetJvmIdent(), 0x65656565);
        ft1 = Files.getLastModifiedTime(Paths.get(TOP_NAME));

        run2(modJvmIdentBase, TOP_NAME,
            "-Xshare:auto",
            "-XX:+AutoCreateSharedArchive",
            "-Xlog:cds",
            "-Xlog:cds+dynamic=info",
            verifySharedSpaces,
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldContain(HELLO_WORLD);
                if (verifyOn) {
                    output.shouldContain("UseSharedSpaces: Header checksum verification failed");
                }
                output.shouldContain("Unable to map shared spaces")
                      .shouldNotContain("Dumping shared data to file");
            });
        ft2 = Files.getLastModifiedTime(Paths.get(TOP_NAME));
        fileModified = !ft1.equals(ft2);
        if (fileModified) {
            throw new RuntimeException("Shared archive " + TOP_NAME + " should not be generated");
        }

        // 24 run with non-existing shared archives
        print("24 run with non-existing shared archives");
        //   24.01 run -Xshare:auto -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=base.jsa:non-exist-top.jsa
        print("    24.01 run -Xshare:auto -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=base.jsa:non-exist-top.jsa");
        String nonExistTop = "non-existing-top.jsa";
        File fileNonExist = new File(nonExistTop);
        if (fileNonExist.exists()) {
            fileNonExist.delete();
        }
        run2(BASE_NAME, nonExistTop,
             "-Xshare:auto",
             "-XX:+AutoCreateSharedArchive",
             "-Xlog:cds",
             "-Xlog:cds+dynamic=info",
             verifySharedSpaces,
             "-cp", appJar,
             mainAppClass)
             .assertNormalExit(output -> {
                 output.shouldContain("Specified shared archive not found (" + nonExistTop + ")")
                       .shouldContain(HELLO_WORLD)
                       .shouldContain("Dumping shared data to file:");
             });
        if (!fileNonExist.exists()) {
            throw new RuntimeException("Shared archive " + nonExistTop + " should be created at exit");
        }

        //    24.02 run -Xshare:auto -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=non-exist-base.jsa:top.jsa
        print("    24.02 run -Xshare:auto -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=non-exist-base.jsa:top.jsa");
        String nonExistBase = "non-existing-base.jsa";
        fileNonExist = new File(nonExistBase);
        if (fileNonExist.exists()) {
            fileNonExist.delete();
        }
        ft1 = Files.getLastModifiedTime(Paths.get(TOP_NAME));
        run2(nonExistBase, TOP_NAME,
             "-Xshare:auto",
             "-XX:+AutoCreateSharedArchive",
             "-Xlog:cds",
             "-Xlog:cds+dynamic=info",
             verifySharedSpaces,
             "-cp", appJar,
             mainAppClass)
             .assertNormalExit(output -> {
                 output.shouldContain("Specified shared archive not found (" + nonExistBase + ")")
                       .shouldContain(HELLO_WORLD)
                       .shouldNotContain("Dumping shared data to file:");
             });
        ft2 = Files.getLastModifiedTime(Paths.get(TOP_NAME));
        fileModified = !ft1.equals(ft2);
        if (fileModified) {
            throw new RuntimeException("Shared archive " + TOP_NAME + " should not be created at exit");
        }
    }
}
