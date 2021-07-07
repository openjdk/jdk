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
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.nio.file.StandardOpenOption;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import sun.hotspot.WhiteBox;

public class SharedArchiveConsistency {
    public static WhiteBox wb;
    public static int offset_magic;     // CDSFileMapHeaderBase::_magic
    public static int offset_version;   // CDSFileMapHeaderBase::_version
    public static int offset_jvm_ident; // FileMapHeader::_jvm_ident
    public static int sp_offset_crc;    // CDSFileMapRegion::_crc
    public static int file_header_size = -1;// total size of header, variant, need calculation
    public static int CDSFileMapRegion_size; // size of CDSFileMapRegion
    public static int sp_offset;       // offset of CDSFileMapRegion
    public static int sp_used_offset;  // offset of CDSFileMapRegion::_used
    public static int size_t_size;     // size of size_t
    public static int int_size;        // size of int
    public static long alignment;      // MetaspaceShared::core_region_alignment

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
        "An error has occurred while processing the shared archive file.",
        "Checksum verification failed.",
        "The shared archive file has been truncated."
    };

    public static void getFileOffsetInfo() throws Exception {
        wb = WhiteBox.getWhiteBox();
        offset_magic = wb.getOffsetForName("FileMapHeader::_magic");
        offset_version = wb.getOffsetForName("FileMapHeader::_version");
        offset_jvm_ident = wb.getOffsetForName("FileMapHeader::_jvm_ident");
        sp_offset_crc = wb.getOffsetForName("CDSFileMapRegion::_crc");
        try {
            int nonExistOffset = wb.getOffsetForName("FileMapHeader::_non_exist_offset");
            System.exit(-1); // should fail
        } catch (Exception e) {
            // success
        }

        sp_offset = wb.getOffsetForName("FileMapHeader::_space[0]") - offset_magic;
        sp_used_offset = wb.getOffsetForName("CDSFileMapRegion::_used") - sp_offset_crc;
        size_t_size = wb.getOffsetForName("size_t_size");
        CDSFileMapRegion_size  = wb.getOffsetForName("CDSFileMapRegion_size");
        alignment = wb.metaspaceSharedRegionAlignment();
    }

    public static int getFileHeaderSize(FileChannel fc) throws Exception {
        if (file_header_size != -1) {
            return file_header_size;
        }
        // this is not real header size, it is struct size
        int_size = wb.getOffsetForName("int_size");
        file_header_size = wb.getOffsetForName("file_header_size");
        System.out.println("file_header_size      = " + file_header_size);
        file_header_size = (int)align_up_page(file_header_size);
        System.out.println("file_header_size (aligned to page) = " + file_header_size);
        return file_header_size;
    }

    public static long align_up_page(long l) throws Exception {
        // wb is obtained in getFileOffsetInfo() which is called first in main() else we should call
        // WhiteBox.getWhiteBox() here first.
        int pageSize = wb.getVMPageSize();
        return (l + pageSize -1) & (~ (pageSize - 1));
    }

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

    public static long readInt(FileChannel fc, long offset, int nbytes) throws Exception {
        ByteBuffer bb = ByteBuffer.allocate(nbytes);
        bb.order(ByteOrder.nativeOrder());
        fc.position(offset);
        fc.read(bb);
        return  (nbytes > 4 ? bb.getLong(0) : bb.getInt(0));
    }

    public static void writeData(FileChannel fc, long offset, ByteBuffer bb) throws Exception {
        fc.position(offset);
        fc.write(bb);
    }

    public static FileChannel getFileChannel(File jsaFile) throws Exception {
        List<StandardOpenOption> arry = new ArrayList<StandardOpenOption>();
        arry.add(READ);
        arry.add(WRITE);
        return FileChannel.open(jsaFile.toPath(), new HashSet<StandardOpenOption>(arry));
    }

    public static void modifyJsaContentRandomly(File jsaFile) throws Exception {
        FileChannel fc = getFileChannel(jsaFile);
        // corrupt random area in the data areas
        long[] used    = new long[num_regions];       // record used bytes
        long start0, start, end, off;
        int used_offset, path_info_size;

        int bufSize;
        System.out.printf("%-24s%12s%12s%16s\n", "Space Name", "Used bytes", "Reg Start", "Random Offset");
        start0 = getFileHeaderSize(fc);
        for (int i = 0; i < num_regions; i++) {
            used[i] = get_region_used_size_aligned(fc, i);
            start = start0;
            for (int j = 0; j < i; j++) {
                start += align_up_page(used[j]);
            }
            end = start + used[i];
            if (start == end) {
                continue; // Ignore empty regions
            }
            off = getRandomBetween(start, end);
            System.out.printf("%-24s%12d%12d%16d\n", shared_region_name[i], used[i], start, off);
            if (end - off < 1024) {
                bufSize = (int)(end - off + 1);
            } else {
                bufSize = 1024;
            }
            ByteBuffer bbuf = ByteBuffer.wrap(new byte[bufSize]);
            writeData(fc, off, bbuf);
        }
        if (fc.isOpen()) {
            fc.close();
        }
    }

    static long get_region_used_size_aligned(FileChannel fc, int region) throws Exception {
        long n = sp_offset + CDSFileMapRegion_size * region + sp_used_offset;
        long used = readInt(fc, n, size_t_size);
        used = (used + alignment - 1) & ~(alignment - 1);
        return used;
    }

    public static boolean modifyJsaContent(int region, File jsaFile) throws Exception {
        FileChannel fc = getFileChannel(jsaFile);
        byte[] buf = new byte[4096];
        ByteBuffer bbuf = ByteBuffer.wrap(buf);

        long total = 0L;
        long[] used = new long[num_regions];
        System.out.printf("%-24s%12s\n", "Space name", "Used bytes");
        for (int i = 0; i < num_regions; i++) {
            used[i] = get_region_used_size_aligned(fc, i);
            System.out.printf("%-24s%12d\n", shared_region_name[i], used[i]);
            total += used[i];
        }
        System.out.printf("%-24s%12d\n", "Total: ", total);
        long header_size = getFileHeaderSize(fc);
        long region_start_offset = header_size;
        for (int i=0; i<region; i++) {
            region_start_offset += used[i];
        }
        if (used[region] == 0) {
            System.out.println("Region " + shared_region_name[region] + " is empty. Nothing to corrupt.");
            return false;
        }
        System.out.println("Corrupt " + shared_region_name[region] + " section, start = " + region_start_offset
                           + " (header_size + 0x" + Long.toHexString(region_start_offset-header_size) + ")");
        long bytes_written = 0L;
        while (bytes_written < used[region]) {
            writeData(fc, region_start_offset + bytes_written, bbuf);
            bbuf.clear();
            bytes_written += 4096;
        }
        if (fc.isOpen()) {
            fc.close();
        }
        return true;
    }

    public static void modifyJsaHeader(File jsaFile) throws Exception {
        FileChannel fc = getFileChannel(jsaFile);
        // screw up header info
        byte[] buf = new byte[getFileHeaderSize(fc)];
        ByteBuffer bbuf = ByteBuffer.wrap(buf);
        writeData(fc, 0L, bbuf);
        if (fc.isOpen()) {
            fc.close();
        }
    }

    public static void modifyJvmIdent(File jsaFile) throws Exception {
        FileChannel fc = getFileChannel(jsaFile);
        int headerSize = getFileHeaderSize(fc);
        System.out.println("    offset_jvm_ident " + offset_jvm_ident);
        byte[] buf = new byte[256];
        ByteBuffer bbuf = ByteBuffer.wrap(buf);
        writeData(fc, (long)offset_jvm_ident, bbuf);
        if (fc.isOpen()) {
            fc.close();
        }
    }

    public static void modifyHeaderIntField(File jsaFile, long offset, int value) throws Exception {
        FileChannel fc = getFileChannel(jsaFile);
        int headerSize = getFileHeaderSize(fc);
        System.out.println("    offset " + offset);
        byte[] buf = ByteBuffer.allocate(4).putInt(value).array();
        ByteBuffer bbuf = ByteBuffer.wrap(buf);
        writeData(fc, offset, bbuf);
        if (fc.isOpen()) {
            fc.close();
        }
    }

    static int testCount = 0;
    public static String startNewTestArchive(String testName) {
        ++ testCount;
        String newArchiveName = TestCommon.getNewArchiveName(String.format("%02d", testCount) + "-" + testName);
        TestCommon.setCurrentArchiveName(newArchiveName);
        return newArchiveName;
    }

    public static File copyFile(File orgJsaFile, String testName) throws Exception {
        File newJsaFile = new File(startNewTestArchive(testName));
        if (newJsaFile.exists()) {
            if (!newJsaFile.delete()) {
                throw new IOException("Could not delete file " + newJsaFile);
            }
        }
        Files.copy(orgJsaFile.toPath(), newJsaFile.toPath(), REPLACE_EXISTING);

        // orgJsaFile is read only, and Files.copy passes on this attribute to newJsaFile.
        // Since we need to modify newJsaFile later, let's set it to r/w
        setReadWritePermission(newJsaFile);

        return newJsaFile;
    }

    // Copy file with bytes deleted or inserted
    // del -- true, deleted, false, inserted
    public static File insertOrDeleteBytes(File orgJsaFile, boolean del) throws Exception {
        File newJsaFile = new File(startNewTestArchive(del ? "delete-bytes" : "insert-bytes"));
        try (
            FileChannel inputChannel = new FileInputStream(orgJsaFile).getChannel();
            FileChannel outputChannel = new FileOutputStream(newJsaFile).getChannel()
        ) {
            long size = inputChannel.size();
            int init_size = getFileHeaderSize(inputChannel);
            outputChannel.transferFrom(inputChannel, 0, init_size);
            int n = (int)getRandomBetween(0, 1024);
            if (del) {
                System.out.println("Delete " + n + " bytes at data start section");
                inputChannel.position(init_size + n);
                outputChannel.transferFrom(inputChannel, init_size, size - init_size - n);
            } else {
                System.out.println("Insert " + n + " bytes at data start section");
                outputChannel.position(init_size);
                outputChannel.write(ByteBuffer.wrap(new byte[n]));
                outputChannel.transferFrom(inputChannel, init_size + n , size - init_size);
            }
        }

        return newJsaFile;
    }

    public static void setReadWritePermission(File file) throws Exception {
        if (!file.canRead()) {
            if (!file.setReadable(true)) {
                throw new IOException("Cannot modify file " + file + " as readable");
            }
        }
        if (!file.canWrite()) {
            if (!file.setWritable(true)) {
                throw new IOException("Cannot modify file " + file + " as writable");
            }
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

        // must call to get offset info first!!!
        getFileOffsetInfo();
        Path currentRelativePath = Paths.get("");
        String currentDir = currentRelativePath.toAbsolutePath().toString();
        System.out.println("Current relative path is: " + currentDir);
        // get jar file
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
        System.out.println("\n2. Corrupt header, should fail\n");
        modifyJsaHeader(copyFile(orgJsaFile, "corrupt-header"));
        output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);
        output.shouldContain("The shared archive file has a bad magic number");
        output.shouldNotContain("Checksum verification failed");
        if (shareAuto) {
            output.shouldContain(HELLO_WORLD);
        }

        // modify _jvm_ident, test should fail
        System.out.println("\n2a. Corrupt _jvm_ident, should fail\n");
        modifyJvmIdent(copyFile(orgJsaFile, "modify-jvm-ident"));
        output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);
        output.shouldContain("The shared archive file was created by a different version or build of HotSpot");
        output.shouldNotContain("Checksum verification failed");
        if (shareAuto) {
            output.shouldContain(HELLO_WORLD);
        }

        // modify _magic, test should fail
        System.out.println("\n2c. Corrupt _magic, should fail\n");
        modifyHeaderIntField(copyFile(orgJsaFile, "modify-magic"), offset_magic, 0x00000000);
        output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);
        output.shouldContain("The shared archive file has a bad magic number");
        output.shouldNotContain("Checksum verification failed");
        if (shareAuto) {
            output.shouldContain(HELLO_WORLD);
        }

        // modify _version, test should fail
        System.out.println("\n2d. Corrupt _version, should fail\n");
        modifyHeaderIntField(copyFile(orgJsaFile, "modify-version"), offset_version, 0x00000000);
        output = shareAuto ? TestCommon.execAuto(execArgs) : TestCommon.execCommon(execArgs);
        output.shouldContain("The shared archive file has the wrong version");
        output.shouldNotContain("Checksum verification failed");
        if (shareAuto) {
            output.shouldContain(HELLO_WORLD);
        }

        // modify content inside regions
        System.out.println("\n3. Corrupt Content, should fail\n");
        for (int i=0; i<num_regions; i++) {
            File newJsaFile = copyFile(orgJsaFile, (shared_region_name[i]));
            if (modifyJsaContent(i, newJsaFile)) {
                testAndCheck(verifyExecArgs);
            }
        }

        // modify both header and content, test should fail
        System.out.println("\n4. Corrupt Header and Content, should fail\n");
        File newJsaFile = copyFile(orgJsaFile, "header-and-content");
        modifyJsaHeader(newJsaFile);
        modifyJsaContent(0, newJsaFile);  // this will not be reached since failed on header change first
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
        modifyJsaContentRandomly(copyFile(orgJsaFile, "random-areas"));
        testAndCheck(verifyExecArgs);
    }
}
