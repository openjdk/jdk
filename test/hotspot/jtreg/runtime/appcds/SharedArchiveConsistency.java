/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary SharedArchiveConsistency
 * @requires vm.cds
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.jartool/sun.tools.jar
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @build sun.hotspot.WhiteBox
 * @compile test-classes/Hello.java
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI SharedArchiveConsistency
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
    public static int offset_magic;    // FileMapHeader::_magic
    public static int sp_offset_crc;   // CDSFileMapRegion::_crc
    public static int file_header_size = -1;// total size of header, variant, need calculation
    public static int CDSFileMapRegion_size; // size of CDSFileMapRegion
    public static int sp_offset;       // offset of CDSFileMapRegion
    public static int sp_used_offset;  // offset of CDSFileMapRegion::_used
    public static int size_t_size;     // size of size_t

    public static File jsa;        // will be updated during test
    public static File orgJsaFile; // kept the original file not touched.
    public static String[] shared_region_name = {"MiscCode", "ReadWrite", "ReadOnly", "MiscData"};
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
    }

    public static int getFileHeaderSize(FileChannel fc) throws Exception {
        if (file_header_size != -1) {
            return file_header_size;
        }
        // this is not real header size, it is struct size
        file_header_size = wb.getOffsetForName("file_header_size");
        int offset_path_misc_info = wb.getOffsetForName("FileMapHeader::_paths_misc_info_size") -
            offset_magic;
        int path_misc_info_size   = (int)readInt(fc, offset_path_misc_info, size_t_size);
        file_header_size += path_misc_info_size; //readInt(fc, offset_path_misc_info, size_t_size);
        System.out.println("offset_path_misc_info = " + offset_path_misc_info);
        System.out.println("path_misc_info_size   = " + path_misc_info_size);
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
        fc.force(true);
    }

    public static FileChannel getFileChannel() throws Exception {
        List<StandardOpenOption> arry = new ArrayList<StandardOpenOption>();
        arry.add(READ);
        arry.add(WRITE);
        return FileChannel.open(jsa.toPath(), new HashSet<StandardOpenOption>(arry));
    }

    public static void modifyJsaContentRandomly() throws Exception {
        FileChannel fc = getFileChannel();
        // corrupt random area in the data areas (MiscCode, ReadWrite, ReadOnly, MiscData)
        long[] used    = new long[num_regions];       // record used bytes
        long start0, start, end, off;
        int used_offset, path_info_size;

        int bufSize;
        System.out.printf("%-12s%-12s%-12s%-12s%-12s\n", "Space Name", "Offset", "Used bytes", "Reg Start", "Random Offset");
        start0 = getFileHeaderSize(fc);
        for (int i = 0; i < num_regions; i++) {
            used_offset = sp_offset + CDSFileMapRegion_size * i + sp_used_offset;
            // read 'used'
            used[i] = readInt(fc, used_offset, size_t_size);
            start = start0;
            for (int j = 0; j < i; j++) {
                start += align_up_page(used[j]);
            }
            end = start + used[i];
            off = getRandomBetween(start, end);
            System.out.printf("%-12s%-12d%-12d%-12d%-12d\n", shared_region_name[i], used_offset, used[i], start, off);
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

    public static void modifyJsaContent() throws Exception {
        FileChannel fc = getFileChannel();
        byte[] buf = new byte[4096];
        ByteBuffer bbuf = ByteBuffer.wrap(buf);

        long total = 0L;
        long used_offset = 0L;
        long[] used = new long[num_regions];
        System.out.printf("%-12s%-12s\n", "Space name", "Used bytes");
        for (int i = 0; i < num_regions; i++) {
            used_offset = sp_offset + CDSFileMapRegion_size* i + sp_used_offset;
            // read 'used'
            used[i] = readInt(fc, used_offset, size_t_size);
            System.out.printf("%-12s%-12d\n", shared_region_name[i], used[i]);
            total += used[i];
        }
        System.out.printf("%-12s%-12d\n", "Total: ", total);
        long corrupt_used_offset =  getFileHeaderSize(fc);
        System.out.println("Corrupt RO section, offset = " + corrupt_used_offset);
        while (used_offset < used[0]) {
            writeData(fc, corrupt_used_offset, bbuf);
            bbuf.clear();
            used_offset += 4096;
        }
        fc.force(true);
        if (fc.isOpen()) {
            fc.close();
        }
    }

    public static void modifyJsaHeader() throws Exception {
        FileChannel fc = getFileChannel();
        // screw up header info
        byte[] buf = new byte[getFileHeaderSize(fc)];
        ByteBuffer bbuf = ByteBuffer.wrap(buf);
        writeData(fc, 0L, bbuf);
        if (fc.isOpen()) {
            fc.close();
        }
    }

    public static void copyFile(File from, File to) throws Exception {
        if (to.exists()) {
            if(!to.delete()) {
                throw new IOException("Could not delete file " + to);
            }
        }
        to.createNewFile();
        setReadWritePermission(to);
        Files.copy(from.toPath(), to.toPath(), REPLACE_EXISTING);
    }

    // Copy file with bytes deleted or inserted
    // del -- true, deleted, false, inserted
    public static void copyFile(File from, File to, boolean del) throws Exception {
        try (
            FileChannel inputChannel = new FileInputStream(from).getChannel();
            FileChannel outputChannel = new FileOutputStream(to).getChannel()
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
    }

    public static void restoreJsaFile() throws Exception {
        Files.copy(orgJsaFile.toPath(), jsa.toPath(), REPLACE_EXISTING);
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
        OutputAnalyzer output = TestCommon.execCommon(execArgs);
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
    //   3) keep header correct but modify content
    //   4) update both header and content, test
    //   5) delete bytes in data begining
    //   6) insert bytes in data begining
    //   7) randomly corrupt data in four areas: RO, RW. MISC DATA, MISC CODE
    public static void main(String... args) throws Exception {
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
        String[] execArgs = {"-cp", jarFile, "Hello"};

        OutputAnalyzer output = TestCommon.execCommon(execArgs);

        try {
            TestCommon.checkExecReturn(output, 0, true, "Hello World");
        } catch (Exception e) {
            TestCommon.checkExecReturn(output, 1, true, matchMessages[0]);
        }

        // get current archive name
        jsa = new File(TestCommon.getCurrentArchiveName());
        if (!jsa.exists()) {
            throw new IOException(jsa + " does not exist!");
        }

        setReadWritePermission(jsa);

        // save as original untouched
        orgJsaFile = new File(new File(currentDir), "appcds.jsa.bak");
        copyFile(jsa, orgJsaFile);


        // modify jsa header, test should fail
        System.out.println("\n2. Corrupt header, should fail\n");
        modifyJsaHeader();
        output = TestCommon.execCommon(execArgs);
        output.shouldContain("The shared archive file has the wrong version");
        output.shouldNotContain("Checksum verification failed");

        // modify content
        System.out.println("\n3. Corrupt Content, should fail\n");
        copyFile(orgJsaFile, jsa);
        modifyJsaContent();
        testAndCheck(execArgs);

        // modify both header and content, test should fail
        System.out.println("\n4. Corrupt Header and Content, should fail\n");
        copyFile(orgJsaFile, jsa);
        modifyJsaHeader();
        modifyJsaContent();  // this will not be reached since failed on header change first
        output = TestCommon.execCommon(execArgs);
        output.shouldContain("The shared archive file has the wrong version");
        output.shouldNotContain("Checksum verification failed");

        // delete bytes in data sectoin
        System.out.println("\n5. Delete bytes at begining of data section, should fail\n");
        copyFile(orgJsaFile, jsa, true);
        testAndCheck(execArgs);

        // insert bytes in data sectoin forward
        System.out.println("\n6. Insert bytes at begining of data section, should fail\n");
        copyFile(orgJsaFile, jsa, false);
        testAndCheck(execArgs);

        System.out.println("\n7. modify Content in random areas, should fail\n");
        copyFile(orgJsaFile, jsa);
        modifyJsaContentRandomly();
        testAndCheck(execArgs);
    }
}
