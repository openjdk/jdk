/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Corrupt the header CRC fields of the top archive. VM should exit with an error.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build Hello sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar hello.jar Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI ArchiveConsistency on
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI ArchiveConsistency auto
 */

import java.io.File;
import java.io.IOException;
import jdk.test.lib.cds.CDSArchiveUtils;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;

public class ArchiveConsistency extends DynamicArchiveTestBase {
    private static final String HELLO_WORLD = "Hello World";
    private static boolean isAuto;

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || (!args[0].equals("on") && !args[0].equals("auto"))) {
            throw new RuntimeException("Must have one arg either of \"on\" or \"auto\"");
        }
        isAuto = args[0].equals("auto");
        setAutoMode(isAuto);
        runTest(ArchiveConsistency::testCustomBase);
    }

    // Test with custom base archive + top archive
    static void testCustomBase() throws Exception {
        String topArchiveName = getNewArchiveName("top2");
        String baseArchiveName = getNewArchiveName("base");
        TestCommon.dumpBaseArchive(baseArchiveName);
        doTest(baseArchiveName, topArchiveName);
    }

    static boolean VERIFY_CRC = false;

    static void runTwo(String base, String top,
                       String jarName, String mainClassName, int expectedExitValue,
                       String ... checkMessages) throws Exception {
        CDSTestUtils.Result result = run2(base, top,
                "-Xlog:cds",
                "-Xlog:cds+dynamic=debug",
                VERIFY_CRC ? "-XX:+VerifySharedSpaces" : "-XX:-VerifySharedSpaces",
                "-cp",
                jarName,
                mainClassName);
        if (expectedExitValue == 0) {
            result.assertNormalExit( output -> {
                for (String s : checkMessages) {
                    output.shouldContain(s);
                }
                output.shouldContain(HELLO_WORLD);
            });
        } else {
            result.assertAbnormalExit( output -> {
                for (String s : checkMessages) {
                    output.shouldContain(s);
                }
                output.shouldContain("Unable to use shared archive");
            });
        }
    }

    private static void startTest(String str) {
        System.out.println("\n" + str);
    }

    private static void doTest(String baseArchiveName, String topArchiveName) throws Exception {
        String appJar = ClassFileInstaller.getJarPath("hello.jar");
        String mainClass = "Hello";
        dump2(baseArchiveName, topArchiveName,
             "-Xlog:cds",
             "-Xlog:cds+dynamic=debug",
             "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                    output.shouldContain("Written dynamic archive 0x");
                });

        File jsa = new File(topArchiveName);
        if (!jsa.exists()) {
            throw new IOException(jsa + " does not exist!");
        }

        startTest("1. Modify the CRC values in the header of the top archive");
        String modTop = getNewArchiveName("modTopRegionsCrc");
        File copiedJsa = CDSArchiveUtils.copyArchiveFile(jsa, modTop);
        CDSArchiveUtils.modifyAllRegionsCrc(copiedJsa);

        VERIFY_CRC = true;
        runTwo(baseArchiveName, modTop,
               appJar, mainClass, isAuto ? 0 : 1,
               "Header checksum verification failed");
        VERIFY_CRC = false;

        startTest("2. Make header size larger than the archive size");
        String largerHeaderSize = getNewArchiveName("largerHeaderSize");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(jsa, largerHeaderSize);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetHeaderSize(),  (int)copiedJsa.length() + 1024);
        runTwo(baseArchiveName, largerHeaderSize,
               appJar, mainClass, isAuto ? 0 : 1,
               "Archive file header larger than archive file");

        startTest("3. Make base archive name offset beyond of header size.");
        String wrongBaseArchiveNameOffset = getNewArchiveName("wrongBaseArchiveNameOffset");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(jsa, wrongBaseArchiveNameOffset);
        int fileHeaderSize = (int)CDSArchiveUtils.fileHeaderSize(copiedJsa);
        int baseArchiveNameOffset = CDSArchiveUtils.baseArchiveNameOffset(copiedJsa);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetBaseArchiveNameOffset(), baseArchiveNameOffset + 1024);
        runTwo(baseArchiveName, wrongBaseArchiveNameOffset,
               appJar, mainClass, isAuto ? 0 : 1,
               "Invalid base_archive_name offset/size (out of range)");

        startTest("4. Make base archive name offset points to middle of the base archive name");
        String wrongBaseNameOffset = getNewArchiveName("wrongBaseNameOffset");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(jsa, wrongBaseNameOffset);
        int baseArchiveNameSize = CDSArchiveUtils.baseArchiveNameSize(copiedJsa);
        baseArchiveNameOffset = CDSArchiveUtils.baseArchiveNameOffset(copiedJsa);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, baseArchiveNameOffset,
                                             baseArchiveNameOffset + baseArchiveNameSize/2);
        runTwo(baseArchiveName, wrongBaseNameOffset,
               appJar, mainClass, isAuto ? 0 : 1,
               "Base archive name is damaged");

        startTest("5. Make base archive name not terminated with '\0'");
        String wrongBaseName = getNewArchiveName("wrongBaseName");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(jsa, wrongBaseName);
        baseArchiveNameOffset = CDSArchiveUtils.baseArchiveNameOffset(copiedJsa);
        baseArchiveNameSize = CDSArchiveUtils.baseArchiveNameSize(copiedJsa);
        long offset = baseArchiveNameOffset + baseArchiveNameSize - 1;  // end of line
        CDSArchiveUtils.writeData(copiedJsa, offset, new byte[] {(byte)'X'});

        runTwo(baseArchiveName, wrongBaseName,
               appJar, mainClass, isAuto ? 0 : 1,
               "Base archive name is damaged");

        startTest("6. Modify base archive name to a file that doesn't exist");
        String wrongBaseName2 = getNewArchiveName("wrongBaseName2");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(jsa, wrongBaseName2);
        baseArchiveNameOffset = CDSArchiveUtils.baseArchiveNameOffset(copiedJsa);
        baseArchiveNameSize = CDSArchiveUtils.baseArchiveNameSize(copiedJsa);
        offset = baseArchiveNameOffset + baseArchiveNameSize - 2;  // the "a" in ".jsa"
        CDSArchiveUtils.writeData(copiedJsa, offset, new byte[] {(byte)'b'}); // .jsa -> .jsb

        // Make sure it doesn't exist
        String badName = baseArchiveName.replace(".jsa", ".jsb");
        (new File(badName)).delete();

        runTwo(baseArchiveName, wrongBaseName2,
               appJar, mainClass, isAuto ? 0 : 1,
               "Base archive " + badName + " does not exist");

        // Following three tests:
        //   -XX:SharedArchiveFile=non-exist-base.jsa:top.jsa
        //   -XX:SharedArchiveFile=base.jsa:non-exist-top.jsa
        //   -XX:SharedArchiveFile=non-exist-base.jsa:non-exist-top.jsa
        startTest("7. Non-exist base archive");
        String nonExistBase = "non-exist-base.jsa";
        File nonExistBaseFile = new File(nonExistBase);
        nonExistBaseFile.delete();
        runTwo(nonExistBase, topArchiveName,
               appJar, mainClass, isAuto ? 0 : 1,
               "Specified shared archive not found (" + nonExistBase + ")");

        startTest("8. Non-exist top archive");
        String nonExistTop = "non-exist-top.jsa";
        File nonExistTopFile = new File(nonExistTop);
        nonExistTopFile.delete();
        runTwo(baseArchiveName, nonExistTop,
               appJar, mainClass, isAuto ? 0 : 1,
               "Specified shared archive not found (" + nonExistTop + ")");

        startTest("9. nost-exist-base and non-exist-top");
        runTwo(nonExistBase, nonExistTop,
               appJar, mainClass, isAuto ? 0 : 1,
               "Specified shared archive not found (" + nonExistBase + ")");
    }
}
