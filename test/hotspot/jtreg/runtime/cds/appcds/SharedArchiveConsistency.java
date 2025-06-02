/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @build jdk.test.whitebox.WhiteBox
 * @compile test-classes/Hello.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI SharedArchiveConsistency on
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI SharedArchiveConsistency auto
 */
import jdk.test.lib.cds.CDSArchiveUtils;
import jdk.test.lib.process.OutputAnalyzer;
import java.io.File;
import java.io.IOException;

public class SharedArchiveConsistency {
    public static boolean shareAuto;       // true  == -Xshare:auto
                                           // false == -Xshare:on

    private static int genericHeaderMinVersion;  // minimum supported CDS version
    private static int currentCDSArchiveVersion; // current CDS version in java process
    // The following should be consistent with the enum in the C++ MetaspaceShared class
    public static String[] shared_region_name = {
        "rw",          // ReadWrite
        "ro",          // ReadOnly
        "bm",          // relocation bitmaps
        "hp",          // heap
    };

    public static final String HELLO_WORLD = "Hello World";
    public static final String errMsg = "An error has occurred while processing the shared archive file.";

    public static int num_regions = shared_region_name.length;
    public static String[] matchMessages = {
        "Header checksum verification failed.",
        "The shared archive file has an incorrect header size.",
        "Unable to use shared archive",
        "An error has occurred while processing the shared archive file.",
        "Checksum verification failed.",
        "The shared archive file has been truncated."
    };

    static int testCount = 0;
    public static String startNewArchive(String testName) {
        ++ testCount;
        String newArchiveName = TestCommon.getNewArchiveName(String.format("%02d", testCount) + "-" + testName);
        TestCommon.setCurrentArchiveName(newArchiveName);
        return newArchiveName;
    }

    public static void testAndCheck(String[] execArgs, String... expectedMessages) throws Exception {
        OutputAnalyzer output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);
        String stdtxt = output.getOutput();
        System.out.println("Note: this test may fail in very rare occasions due to CRC32 checksum collision");
        for (String opt : execArgs) {
          if (opt.equals("-XX:+VerifySharedSpaces")) {
            // If VerifySharedSpaces is enabled, the VM should never crash even if the archive
            // is corrupted (unless if we are so lucky that the corrupted archive ends up
            // have the same checksum as recoreded in the header)
            output.shouldNotContain("A fatal error has been detected by the Java Runtime Environment");
          }
        }
        for (int i = 0; i < expectedMessages.length; i++) {
            output.shouldContain(expectedMessages[i]);
        }
        for (String message : matchMessages) {
            if (stdtxt.contains(message)) {
                // match any to return
                return;
            }
        }
        TestCommon.checkExec(output);
    }

    private static String hex(int version) {
        return String.format("0x%x", version);
    }

    // dump with hello.jsa, then
    // read the jsa file
    //   1) run normal
    //   2) modify header
    //   3) keep header correct but modify content in each region specified by shared_region_name[]
    //   4) update both header and content, test
    //   5) delete bytes in data begining
    //   6) insert bytes in data begining
    //   7) randomly corrupt data in each region in shared_region_name[]
    public static void main(String... args) throws Exception {
        if (args.length != 1) {
            throw new RuntimeException("One arg of 'on' or 'auto' to run the test");
        }
        if (!args[0].equals("on") && !args[0].equals("auto")) {
            throw new RuntimeException("Arg must be 'on' or 'auto'");
        }
        shareAuto = args[0].equals("auto");
        genericHeaderMinVersion = CDSArchiveUtils.getGenericHeaderMinVersion();
        currentCDSArchiveVersion = CDSArchiveUtils.getCurrentCDSArchiveVersion();

        String jarFile = JarBuilder.getOrCreateHelloJar();

        // dump (appcds.jsa created)
        TestCommon.testDump(jarFile, null);

        // test, should pass
        System.out.println("1. Normal, should pass but may fail\n");

        // disable VerifySharedSpaces, it may be turned on by jtreg args
        String[] execArgs = {"-Xlog:cds=debug", "-XX:-VerifySharedSpaces", "-cp", jarFile, "Hello"};
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
        CDSArchiveUtils.modifyFileHeader(copiedJsa);
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
        CDSArchiveUtils.modifyJvmIdent(copiedJsa, "My non-exist jdk 1.000001");
        output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);
        output.shouldContain("The shared archive file was created by a different version or build of HotSpot");
        output.shouldNotContain("Checksum verification failed");
        output.shouldContain(errMsg);
        if (shareAuto) {
            output.shouldContain(HELLO_WORLD);
        }

        // modify _magic, test should fail
        System.out.println("\n2b. Corrupt _magic, should fail\n");
        String modMagic = startNewArchive("modify-magic");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(orgJsaFile, modMagic);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetMagic(), -1);
        output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);
        output.shouldContain("The shared archive file has a bad magic number");
        output.shouldNotContain("Checksum verification failed");
        if (shareAuto) {
            output.shouldContain(HELLO_WORLD);
        }

        // modify _version, test should fail
        System.out.println("\n2c. Corrupt _version, should fail\n");
        String modVersion = startNewArchive("modify-version");
        int version = currentCDSArchiveVersion + 1;
        copiedJsa = CDSArchiveUtils.copyArchiveFile(orgJsaFile, modVersion);
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetVersion(), version);
        output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);
        output.shouldContain("The shared archive file version " + hex(version) + " does not match the required version " + hex(currentCDSArchiveVersion));
        output.shouldContain(errMsg);
        if (shareAuto) {
            output.shouldContain(HELLO_WORLD);
        }

        System.out.println("\n2d. Corrupt _version, should fail\n");
        String modVersion2 = startNewArchive("modify-version2");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(orgJsaFile, modVersion2);
        version = genericHeaderMinVersion - 1;
        CDSArchiveUtils.modifyHeaderIntField(copiedJsa, CDSArchiveUtils.offsetVersion(), version);
        output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);
        output.shouldContain("Cannot handle shared archive file version " + hex(version) + ". Must be at least " + hex(genericHeaderMinVersion));
        output.shouldNotContain("Checksum verification failed");
        if (shareAuto) {
            output.shouldContain(HELLO_WORLD);
        }

        // modify content inside regions
        System.out.println("\n3. Corrupt Content, should fail\n");
        for (int i=0; i<num_regions; i++) {
            String newArchiveName = startNewArchive(shared_region_name[i]);
            copiedJsa  = CDSArchiveUtils.copyArchiveFile(orgJsaFile, newArchiveName);
            if (CDSArchiveUtils.modifyRegionContent(i, copiedJsa)) {
                testAndCheck(verifyExecArgs);
            }
        }

        // modify both header and content, test should fail
        System.out.println("\n4. Corrupt Header and Content, should fail\n");
        String headerAndContent = startNewArchive("header-and-content");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(orgJsaFile, headerAndContent);
        CDSArchiveUtils.modifyFileHeader(copiedJsa);
        CDSArchiveUtils.modifyRegionContent(0, copiedJsa);  // this will not be reached since failed on header change first
        output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);
        output.shouldContain("The shared archive file has a bad magic number");
        output.shouldNotContain("Checksum verification failed");
        if (shareAuto) {
            output.shouldContain(HELLO_WORLD);
        }

        // insert  bytes in data section
        System.out.println("\n5. Insert bytes at beginning of data section, should fail\n");
        String insertBytes = startNewArchive("insert-bytes");
        CDSArchiveUtils.insertBytesRandomlyAfterHeader(orgJsaFile, insertBytes);
        testAndCheck(verifyExecArgs, errMsg);

        // delete bytes in data section forward
        System.out.println("\n6a. Delete bytes at beginning of data section, should fail\n");
        String deleteBytes = startNewArchive("delete-bytes");
        CDSArchiveUtils.deleteBytesAtRandomPositionAfterHeader(orgJsaFile, deleteBytes, 4096 /*bytes*/);
        testAndCheck(verifyExecArgs, errMsg);

        // delete bytes at the end
        System.out.println("\n6b. Delete bytes at the end, should fail\n");
        deleteBytes = startNewArchive("delete-bytes-end");
        CDSArchiveUtils.deleteBytesAtTheEnd(orgJsaFile, deleteBytes);
        testAndCheck(verifyExecArgs, "The shared archive file has been truncated.", errMsg);

        // modify contents in random area
        System.out.println("\n7. modify Content in random areas, should fail\n");
        String randomAreas = startNewArchive("random-areas");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(orgJsaFile, randomAreas);
        CDSArchiveUtils.modifyRegionContentRandomly(copiedJsa);
        testAndCheck(verifyExecArgs, errMsg);

        // modify _base_archive_name_offet to non-zero
        System.out.println("\n8. modify _base_archive_name_offset to non-zero\n");
        String baseArchiveNameOffsetName = startNewArchive("base-arhive-path-offset");
        copiedJsa = CDSArchiveUtils.copyArchiveFile(orgJsaFile, baseArchiveNameOffsetName);
        int baseArchiveNameOffset = CDSArchiveUtils.baseArchiveNameOffset(copiedJsa);
        System.out.println("    baseArchiveNameOffset = " + baseArchiveNameOffset);
        CDSArchiveUtils.writeData(copiedJsa, CDSArchiveUtils.offsetBaseArchiveNameOffset(), 1024);
        baseArchiveNameOffset = CDSArchiveUtils.baseArchiveNameOffset(copiedJsa);
        System.out.println("new baseArchiveNameOffset = " + baseArchiveNameOffset);
        testAndCheck(verifyExecArgs);
    }
}
