/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @key randomness
 * @summary SharedArchiveConsistency
 * @requires vm.cds
 * @library /test/lib
 * @build sun.hotspot.WhiteBox
 * @compile test-classes/Hello.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI SharedArchiveConsistency on
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI SharedArchiveConsistency auto
 */
import jdk.test.lib.cds.CDSArchiveUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import sun.hotspot.WhiteBox;

public class SharedArchiveConsistency {
    public static boolean shareAuto;       // true  == -Xshare:auto
                                           // false == -Xshare:on

    // The following should be consistent with the enum in the C++ MetaspaceShared class
    public static String[] shared_region_name = {
        "rw",          // ReadWrite
        "ro",          // ReadOnly
        "bm",          // relocation bitmaps
        "first_closed_archive",
        "last_closed_archive",
        "first_open_archive",
        "last_open_archive"
    };

    public static final String HELLO_WORLD = "Hello World";

    public static int num_regions = shared_region_name.length;
    public static String[] matchMessages = {
        "Unable to use shared archive",
        "The shared archive file has a bad magic number",
        "Unable to map shared spaces",
        "An error has occurred while processing the shared archive file.",
        "Checksum verification failed.",
        "The shared archive file has been truncated."
    };

    private static long getRandomBetween(long start, long end) throws Exception {
        if (start > end) {
            throw new IllegalArgumentException("start must be less than end");
        }
        Random aRandom = Utils.getRandomInstance();
        int d = aRandom.nextInt((int)(end - start));
        if (d < 1) {
            d = 1;
        }
        return start + d;
    }

    public static void modifyJsaContentRandomly(File jsaFile) throws Exception {
        // corrupt random area in the data areas
        long[] used = new long[num_regions]; // record used bytes
        long start0, start, end, offset;
        int bufSize;

        System.out.printf("%-24s%12s%12s%16s\n", "Space Name", "Used bytes", "Reg Start", "Random Offset");
        start0 = CDSArchiveUtils.fileHeaderSize();
        for (int i = 0; i < num_regions; i++) {
            used[i] = CDSArchiveUtils.usedRegionSizeAligned(jsaFile, i);
            start = start0;
            for (int j = 0; j < i; j++) {
                start += CDSArchiveUtils.alignUpWithPageSize(used[j]);
            }
            end = start + used[i];
            if (start == end) {
                continue; // Ignore empty regions
            }
            offset = getRandomBetween(start, end);
            System.out.printf("%-24s%12d%12d%16d\n", shared_region_name[i], used[i], start, offset);
            if (end - offset < 1024) {
                bufSize = (int)(end - offset + 1);
            } else {
                bufSize = 1024;
            }
            CDSArchiveUtils.writeData(jsaFile, offset, new byte[bufSize]);
        }
    }

    public static boolean modifyJsaContent(int region, File jsaFile) throws Exception {
        byte[] buf = new byte[4096];

        long total = 0L;
        long[] used = new long[num_regions];
        System.out.printf("%-24s%12s\n", "Space name", "Used bytes");
        for (int i = 0; i < num_regions; i++) {
            used[i] = CDSArchiveUtils.usedRegionSizeAligned(jsaFile, i);
            System.out.printf("%-24s%12d\n", shared_region_name[i], used[i]);
            total += used[i];
        }
        System.out.printf("%-24s%12d\n", "Total: ", total);
        long regionStartOffset = CDSArchiveUtils.fileHeaderSize();
        for (int i = 0; i < region; i++) {
            regionStartOffset += used[i];
        }
        if (used[region] == 0) {
            System.out.println("Region " + shared_region_name[region] + " is empty. Nothing to corrupt.");
            return false;
        }
        System.out.println("Corrupt " + shared_region_name[region] + " section, start = " + regionStartOffset
                           + " (header_size + 0x" + Long.toHexString(regionStartOffset - CDSArchiveUtils.fileHeaderSize()) + ")");
        long bytesWritten = 0L;
        while (bytesWritten < used[region]) {
            CDSArchiveUtils.writeData(jsaFile, regionStartOffset + bytesWritten, buf);
            bytesWritten += 4096;
        }

        return true;
    }

    public static void modifyJsaHeader(File jsaFile) throws Exception {
        // screw up header info
        byte[] buf = new byte[CDSArchiveUtils.fileHeaderSize()];
        System.out.println("CDSArchiveUtils.fileHeaderSize = " + CDSArchiveUtils.fileHeaderSize());
        CDSArchiveUtils.writeData(jsaFile, 0, buf);
    }

    public static void modifyJvmIdent(File jsaFile) throws Exception {
        System.out.println("    offset_jvm_ident " + CDSArchiveUtils.offsetJvmIdent);
        byte[] buf = new String("Bad JDK 1.0001").getBytes();
        CDSArchiveUtils.writeData(jsaFile, (long)CDSArchiveUtils.offsetJvmIdent, buf);
    }

    public static void modifyHeaderIntField(File jsaFile, long offset, int value) throws Exception {
        System.out.println("    offset " + offset);
        byte[] buf = { (byte)(value >> 24), (byte)(value >> 16), (byte)(value >> 8), (byte)(value)};
        CDSArchiveUtils.writeData(jsaFile, offset, buf);
    }

    static int testCount = 0;
    public static String startNewArchive(String testName) {
        ++ testCount;
        String newArchiveName = TestCommon.getNewArchiveName(String.format("%02d", testCount) + "-" + testName);
        TestCommon.setCurrentArchiveName(newArchiveName);
        return newArchiveName;
    }

    // Copy file with bytes deleted or inserted
    // delete or insert number of bytes into/from archive
    public static void insertOrDeleteBytes(File orgJsaFile, boolean del) throws Exception {
        File newJsaFile = new File(startNewArchive(del ? "delete-bytes" : "insert-bytes"));
        int n = (int)getRandomBetween(0, 1024);
        if (del) {
            CDSArchiveUtils.deleteBytesAtOffset(orgJsaFile, newJsaFile, CDSArchiveUtils.fileHeaderSize(), n);
        } else {
            CDSArchiveUtils.insertBytesAtOffset(orgJsaFile, newJsaFile, CDSArchiveUtils.fileHeaderSize(), new byte[n]);
        }
    }

    public static void testAndCheck(String[] execArgs) throws Exception {
        OutputAnalyzer output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);
        String stdtxt = output.getOutput();
        System.out.println("Note: this test may fail in very rare occasions due to CRC32 checksum collision");
        for (String message : matchMessages) {
            if (stdtxt.contains(message)) {
                // match any to return
                return;
            }
        }
        TestCommon.checkExec(output);
    }

    // dump with hello.jsa, then
    // read the jsa file
    //   1) run normal
    //   2) modify header
    //   3) keep header correct but modify content in each region specified by shared_region_name[]
    //   4) update both header and content, test
    //   5) delete bytes in data begining
    //   6) insert bytes in data begining
    //   7) randomly corrupt data in each region specified by shared_region_name[]
    public static void main(String... args) throws Exception {
        if (args.length != 1) {
            throw new RuntimeException("One arg of 'on' or 'auto' to run the test");
        }
        if (!args[0].equals("on") && !args[0].equals("auto")) {
            throw new RuntimeException("Arg must be 'on' or 'auto'");
        }
        shareAuto = args[0].equals("auto");
        WhiteBox box = WhiteBox.getWhiteBox();
        CDSArchiveUtils.initialize(box);  // all offsets available
        String jarFile = JarBuilder.getOrCreateHelloJar();

        // dump (appcds.jsa created)
        TestCommon.testDump(jarFile, null);

        // test, should pass
        System.out.println("1. Normal, should pass but may fail\n");

        String[] execArgs = {"-Xlog:cds=debug", "-cp", jarFile, "Hello"};
        // tests that corrupt contents of the archive need to run with
        // VerifySharedSpaces enabled to detect inconsistencies
        String[] verifyExecArgs = {"-Xlog:cds", "-XX:+VerifySharedSpaces", "-cp", jarFile, "Hello"};

        OutputAnalyzer output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);

        try {
            TestCommon.checkExecReturn(output, 0, true, HELLO_WORLD);
        } catch (Exception e) {
            TestCommon.checkExecReturn(output, 1, true, matchMessages[0]);
        }

        // get the archive that has just been created.
        File orgJsaFile = new File(TestCommon.getCurrentArchiveName());
        if (!orgJsaFile.exists()) {
            throw new IOException(orgJsaFile + " does not exist!");
        }

        // modify jsa header, test should fail
        String modifyHeader = startNewArchive("modify-header");
        File copiedJsa = CDSArchiveUtils.copyArchiveFile(orgJsaFile, modifyHeader);
        System.out.println("\n2. Corrupt header, should fail\n");
        modifyJsaHeader(copiedJsa);
        output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);
        output.shouldContain("The shared archive file has a bad magic number");
        output.shouldNotContain("Checksum verification failed");
        if (shareAuto) {
            output.shouldContain(HELLO_WORLD);
        }

        // modify _jvm_ident, test should fail
        System.out.println("\n2a. Corrupt _jvm_ident, should fail\n");

        String modJvmIdent = startNewArchive("modify-jvm-ident");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(orgJsaFile, modJvmIdent);
        modifyJvmIdent(copiedJsa);
        output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);
        output.shouldContain("The shared archive file was created by a different version or build of HotSpot");
        output.shouldNotContain("Checksum verification failed");
        if (shareAuto) {
            output.shouldContain(HELLO_WORLD);
        }

        // modify _magic, test should fail
        System.out.println("\n2c. Corrupt _magic, should fail\n");
        String modHeadIntField = startNewArchive("modify-magic");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(orgJsaFile, modHeadIntField);
        modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetMagic, 0x00000000);
        output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);
        output.shouldContain("The shared archive file has a bad magic number");
        output.shouldNotContain("Checksum verification failed");
        if (shareAuto) {
            output.shouldContain(HELLO_WORLD);
        }

        // modify _version, test should fail
        System.out.println("\n2d. Corrupt _version, should fail\n");
        modHeadIntField = startNewArchive("modify-version");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(orgJsaFile, modHeadIntField);
        modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetVersion, 0x00000000);
        output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);
        output.shouldContain("The shared archive file has the wrong version");
        output.shouldNotContain("Checksum verification failed");
        if (shareAuto) {
            output.shouldContain(HELLO_WORLD);
        }

        // modify content inside regions
        System.out.println("\n3. Corrupt Content, should fail\n");
        for (int i=0; i<num_regions; i++) {
            String newArchiveName = startNewArchive(shared_region_name[i]);
            copiedJsa  = CDSArchiveUtils.copyArchiveFile(orgJsaFile, newArchiveName);
            if (modifyJsaContent(i, copiedJsa)) {
                testAndCheck(verifyExecArgs);
            }
        }

        // modify both header and content, test should fail
        System.out.println("\n4. Corrupt Header and Content, should fail\n");
        String headerAndContent = startNewArchive("header-and-content");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(orgJsaFile, headerAndContent);
        modifyJsaHeader(copiedJsa);
        modifyJsaContent(0, copiedJsa);  // this will not be reached since failed on header change first
        output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);
        output.shouldContain("The shared archive file has a bad magic number");
        output.shouldNotContain("Checksum verification failed");
        if (shareAuto) {
            output.shouldContain(HELLO_WORLD);
        }

        // delete bytes in data section
        System.out.println("\n5. Delete bytes at beginning of data section, should fail\n");
        insertOrDeleteBytes(orgJsaFile, true);
        testAndCheck(verifyExecArgs);

        // insert bytes in data section forward
        System.out.println("\n6. Insert bytes at beginning of data section, should fail\n");
        insertOrDeleteBytes(orgJsaFile, false);
        testAndCheck(verifyExecArgs);

        System.out.println("\n7. modify Content in random areas, should fail\n");
        String randomAreas = startNewArchive("random-areas");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(orgJsaFile, randomAreas);
        modifyJsaContentRandomly(copiedJsa);
        testAndCheck(verifyExecArgs);
    }
}
