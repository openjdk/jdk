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
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI ArchiveConsistency
 */

import java.io.File;
import java.io.IOException;
import jdk.test.lib.cds.CDSArchiveUtils;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;

public class ArchiveConsistency extends DynamicArchiveTestBase {

    public static void main(String[] args) throws Exception {
        runTest(ArchiveConsistency::testCustomBase);
    }

    // Test with custom base archive + top archive
    static void testCustomBase() throws Exception {
        String topArchiveName = getNewArchiveName("top2");
        String baseArchiveName = getNewArchiveName("base");
        TestCommon.dumpBaseArchive(baseArchiveName);
        doTest(baseArchiveName, topArchiveName);
    }

    static void runTwo(String base, String top,
                       String jarName, String mainClassName, int exitValue,
                       String ... checkMessages) throws Exception {
        CDSTestUtils.Result result = run2(base, top,
                "-Xlog:cds",
                "-Xlog:cds+dynamic=debug",
                "-XX:+VerifySharedSpaces",
                "-cp",
                jarName,
                mainClassName);
        if (exitValue == 0) {
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

        // 1. Modify the CRC values in the header of the top archive.
        System.out.println("\n1. Modify the CRC values in the header of the top archive");
        String modTop = getNewArchiveName("modTopRegionsCrc");
        File copiedJsa = CDSArchiveUtils.copyArchiveFile(jsa, modTop);
        CDSArchiveUtils.modifyAllRegionsCrc(copiedJsa);

        runTwo(baseArchiveName, modTop,
               appJar, mainClass, 1,
               new String[] {"Header checksum verification failed",
                             "Unable to use shared archive"});

        // 2. Make header size larger than the archive size
        System.out.println("\n2. Make header size larger than the archive size");
        String largerHeaderSize = getNewArchiveName("largerHeaderSize");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(jsa, largerHeaderSize);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetHeaderSize(),  (int)copiedJsa.length() + 1024);
        runTwo(baseArchiveName, largerHeaderSize,
               appJar, mainClass, 1,
               new String[] {"_header_size should be equal to _base_archive_path_offset plus _base_archive_name_size",
                             "Unable to use shared archive"});

        // 3. Make base archive path offset beyond of header size
        System.out.println("\n3. Make base archive path offset beyond of header size.");
        String wrongBaseArchivePathOffset = getNewArchiveName("wrongBaseArchivePathOffset");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(jsa, wrongBaseArchivePathOffset);
        int fileHeaderSize = (int)CDSArchiveUtils.fileHeaderSize(copiedJsa);
        int baseArchivePathOffset = CDSArchiveUtils.baseArchivePathOffset(copiedJsa);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetBaseArchivePathOffset(), baseArchivePathOffset + 1024);
        runTwo(baseArchiveName, wrongBaseArchivePathOffset,
               appJar, mainClass, 1,
               new String[] {"_header_size should be equal to _base_archive_path_offset plus _base_archive_name_size",
                             "The shared archive file has an incorrect header size",
                             "Unable to use shared archive"});

        // 4. Make base archive path offset points to middle of name size
        System.out.println("\n4. Make base archive path offset points to middle of name size");
        String wrongBasePathOffset = getNewArchiveName("wrongBasePathOffset");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(jsa, wrongBasePathOffset);
        int baseArchiveNameSize = CDSArchiveUtils.baseArchiveNameSize(copiedJsa);
        baseArchivePathOffset = CDSArchiveUtils.baseArchivePathOffset(copiedJsa);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, baseArchivePathOffset,
                                             baseArchivePathOffset + baseArchiveNameSize/2);
        runTwo(baseArchiveName, wrongBasePathOffset,
               appJar, mainClass, 1,
               new String[] {"An error has occurred while processing the shared archive file.",
                             "Header checksum verification failed",
                             "Unable to use shared archive"});

        // 5. Make base archive name not terminated with '\0'
        System.out.println("\n5. Make base archive name not terminated with '\0'");
        String wrongBaseName = getNewArchiveName("wrongBaseName");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(jsa, wrongBaseName);
        baseArchivePathOffset = CDSArchiveUtils.baseArchivePathOffset(copiedJsa);
        baseArchiveNameSize = CDSArchiveUtils.baseArchiveNameSize(copiedJsa);
        long offset = baseArchivePathOffset + baseArchiveNameSize - 1;  // end of line
        CDSArchiveUtils.writeData(copiedJsa, offset, new byte[] {(byte)'X'});

        runTwo(baseArchiveName, wrongBaseName,
               appJar, mainClass, 1,
               new String[] {"Base archive name is damaged",
                             "Header checksum verification failed"});

        // 6. Modify base archive name to a file that doesn't exist.
        System.out.println("\n6. Modify base archive name to a file that doesn't exist");
        String wrongBaseName2 = getNewArchiveName("wrongBaseName2");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(jsa, wrongBaseName2);
        baseArchivePathOffset = CDSArchiveUtils.baseArchivePathOffset(copiedJsa);
        baseArchiveNameSize = CDSArchiveUtils.baseArchiveNameSize(copiedJsa);
        offset = baseArchivePathOffset + baseArchiveNameSize - 2;  // the "a" in ".jsa"
        CDSArchiveUtils.writeData(copiedJsa, offset, new byte[] {(byte)'b'}); // .jsa -> .jsb

        // Make sure it doesn't exist
        String badName = baseArchiveName.replace(".jsa", ".jsb");
        (new File(badName)).delete();

        runTwo(baseArchiveName, wrongBaseName2,
               appJar, mainClass, 1,
               new String[] {"Base archive " + badName + " does not exist",
                             "Header checksum verification failed"});
    }
}
