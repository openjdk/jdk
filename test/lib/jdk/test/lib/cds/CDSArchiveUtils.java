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
 */
package jdk.test.lib.cds;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import java.nio.file.Files;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.nio.file.StandardOpenOption;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import jdk.test.lib.Utils;
import sun.hotspot.WhiteBox;

// This class performs operations on shared archive file
public class CDSArchiveUtils {
    // offsets
    public static int offsetMagic;                // CDSFileMapHeaderBase::_magic
    public static int offsetVersion;              // CDSFileMapHeaderBase::_version
    public static int offsetJvmIdent;             // FileMapHeader::_jvm_ident
    public static int offsetBaseArchiveNameSize;  // FileMapHeader::_base_archive_name_size
    public static int spOffsetCrc;                // CDSFileMapRegion::_crc
    public static int spOffset;                   // offset of CDSFileMapRegion
    public static int spUsedOffset;               // offset of CDSFileMapRegion::_used
    // constants
    public static int staticMagic;                // static magic value defined in hotspot
    public static int dynamicMagic;               // dyamic magic value defined in hotspot
    public static int sizetSize;                  // size of size_t
    public static int intSize;                    // size of int
    public static int staticArchiveHeaderSize;    // static archive file header size
    public static int dynamicArchiveHeaderSize;   // dynamic archive file header size
    public static int cdsFileMapRegionSize;       // size of CDSFileMapRegion
    public static long alignment;                 // MetaspaceShared::core_region_alignment

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
    public static int num_regions = shared_region_name.length;

    static {
        WhiteBox wb;
        try {
            wb = WhiteBox.getWhiteBox();
            // offsets
            offsetMagic = wb.getCDSOffsetForName("CDSFileMapHeaderBase::_magic");
            offsetVersion = wb.getCDSOffsetForName("CDSFileMapHeaderBase::_version");
            offsetJvmIdent = wb.getCDSOffsetForName("FileMapHeader::_jvm_ident");
            offsetBaseArchiveNameSize = wb.getCDSOffsetForName("FileMapHeader::_base_archive_name_size");
            spOffsetCrc = wb.getCDSOffsetForName("CDSFileMapRegion::_crc");
            spUsedOffset = wb.getCDSOffsetForName("CDSFileMapRegion::_used") - spOffsetCrc;
            spOffset = wb.getCDSOffsetForName("CDSFileMapHeaderBase::_space[0]") - offsetMagic;
            // constants
            staticMagic = wb.getCDSConstantForName("static_magic");
            dynamicMagic = wb.getCDSConstantForName("dynamic_magic");
            staticArchiveHeaderSize = wb.getCDSConstantForName("static_file_header_size");
            dynamicArchiveHeaderSize = wb.getCDSConstantForName("dynamic_archive_header_size");
            sizetSize = wb.getCDSConstantForName("size_t_size");
            intSize = wb.getCDSConstantForName("int_size");
            cdsFileMapRegionSize  = wb.getCDSConstantForName("CDSFileMapRegion_size");
            alignment = wb.metaspaceSharedRegionAlignment();
            // file_header_size is structure size, real size aligned with alignment
            // so must be calculated after alignment is available
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        try {
            int nonExistOffset = wb.getCDSOffsetForName("FileMapHeader::_non_exist_offset");
            System.exit(-1); // should fail
        } catch (Exception e) {
            // success
        }
    }

    public static long fileHeaderSize(File jsaFile) throws Exception {
      long magicValue = readInt(jsaFile, offsetMagic, 4);
      if (magicValue == staticMagic) {
          return alignUpWithAlignment((long)staticArchiveHeaderSize);
      } else if (magicValue == dynamicMagic) {
          // dynamic archive store base archive name after header, so we count it in header size.
          int baseArchiveNameSize = (int)readInt(jsaFile, (long)offsetBaseArchiveNameSize, 4);
          return alignUpWithAlignment((long)dynamicArchiveHeaderSize + baseArchiveNameSize);
      } else {
          throw new RuntimeException("Wrong magic value from archive file: " + magicValue);
      }
    }

    private static long alignUpWithAlignment(long l) {
        return (l + alignment - 1) & (~ (alignment - 1));
    }

    private static void setReadWritePermission(File file) throws Exception {
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

    public static long getRandomBetween(long start, long end) throws Exception {
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

    public static void modifyContentRandomly(File jsaFile) throws Exception {
        // corrupt random area in the data areas
        long[] used = new long[num_regions]; // record used bytes
        long start0, start, end, offset;
        int bufSize;

        System.out.printf("%-24s%12s%12s%16s\n", "Space Name", "Used bytes", "Reg Start", "Random Offset");
        start0 = fileHeaderSize(jsaFile);
        for (int i = 0; i < num_regions; i++) {
            used[i] = usedRegionSizeAligned(jsaFile, i);
            start = start0;
            for (int j = 0; j < i; j++) {
                start += alignUpWithAlignment(used[j]);
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
            writeData(jsaFile, offset, new byte[bufSize]);
        }
    }

    public static void modifyRegionContentRandomly(File jsaFile) throws Exception {
        // corrupt random area in the data areas
        long[] used = new long[num_regions]; // record used bytes
        long start0, start, end, offset;
        int bufSize;

        System.out.printf("%-24s%12s%12s%16s\n", "Space Name", "Used bytes", "Reg Start", "Random Offset");
        start0 = fileHeaderSize(jsaFile);
        for (int i = 0; i < num_regions; i++) {
            used[i] = usedRegionSizeAligned(jsaFile, i);
            start = start0;
            for (int j = 0; j < i; j++) {
                start += alignUpWithAlignment(used[j]);
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
            writeData(jsaFile, offset, new byte[bufSize]);
        }
    }

    public static boolean modifyRegionContent(int region, File jsaFile) throws Exception {
        long total = 0L;
        long[] used = new long[num_regions];
        System.out.printf("%-24s%12s\n", "Space name", "Used bytes");
        for (int i = 0; i < num_regions; i++) {
            used[i] = usedRegionSizeAligned(jsaFile, i);
            System.out.printf("%-24s%12d\n", shared_region_name[i], used[i]);
            total += used[i];
        }
        if (used[region] == 0) {
            System.out.println("Region " + shared_region_name[region] + " is empty. Nothing to corrupt.");
            return false;
        }
        byte[] buf = new byte[4096];
        System.out.printf("%-24s%12d\n", "Total: ", total);
        long regionStartOffset = fileHeaderSize(jsaFile);
        for (int i = 0; i < region; i++) {
            regionStartOffset += used[i];
        }
        System.out.println("Corrupt " + shared_region_name[region] + " section, start = " + regionStartOffset
                           + " (header_size + 0x" + Long.toHexString(regionStartOffset - fileHeaderSize(jsaFile)) + ")");
        long bytesWritten = 0L;
        while (bytesWritten < used[region]) {
            bytesWritten += writeData(jsaFile, regionStartOffset + bytesWritten, buf);
        }
        return true;
    }

    public static void modifyRegionCrc(File jsaFile, int region, int value) throws Exception {
        long regionCrcOffset = spOffset + region * spOffsetCrc;
        writeData(jsaFile, regionCrcOffset, value);
    }

    public static void  modifyAllRegionsCrc(File jsaFile) throws Exception {
        int value = 0xbadebabe;
        long[] used = new long[num_regions];
        for (int i = 0; i < num_regions; i++) {
            used[i] = usedRegionSizeAligned(jsaFile, i);
            if (used[i] == 0) {
                // skip empty region
                continue;
            }
            modifyRegionCrc(jsaFile, i, value);
        }
    }

    public static void modifyFileHeader(File jsaFile) throws Exception {
        // screw up header info
        byte[] buf = new byte[(int)fileHeaderSize(jsaFile)];
        writeData(jsaFile, 0, buf);
    }

    public static void modifyJvmIdent(File jsaFile, String newJvmIdent) throws Exception {
        byte[] buf = newJvmIdent.getBytes();
        writeData(jsaFile, (long)offsetJvmIdent, buf);
    }

    public static void modifyHeaderIntField(File jsaFile, long offset, int value) throws Exception {
        System.out.println("    offset " + offset);

        byte[] bytes = ByteBuffer.allocate(4).putInt(value).array();
        writeData(jsaFile, offset, bytes);
    }

    // copy archive and set copied read/write permit
    public static File copyArchiveFile(File orgJsaFile, String newName) throws Exception {
        File newJsaFile = new File(newName);
        if (newJsaFile.exists()) {
            if (!newJsaFile.delete()) {
                throw new IOException("Could not delete file " + newJsaFile);
            }
        }

        Files.copy(orgJsaFile.toPath(), newJsaFile.toPath(), REPLACE_EXISTING);

        // change permission
        setReadWritePermission(newJsaFile);

        return newJsaFile;
    }

    private static FileChannel getFileChannel(File file, boolean write) throws Exception {
        List<StandardOpenOption> arry = new ArrayList<StandardOpenOption>();
        arry.add(READ);
        if (write) {
            arry.add(WRITE);
        }
        return FileChannel.open(file.toPath(), new HashSet<StandardOpenOption>(arry));
    }

    public static long readInt(File file, long offset, int nBytes) throws Exception {
        try (FileChannel fc = getFileChannel(file, false /*read only*/)) {
            ByteBuffer bb = ByteBuffer.allocate(nBytes);
            bb.order(ByteOrder.nativeOrder());
            fc.position(offset);
            fc.read(bb);
            return  (nBytes > 4 ? bb.getLong(0) : bb.getInt(0));
        }
    }

    private static long writeData(FileChannel fc, long offset, ByteBuffer bb) throws Exception {
        fc.position(offset);
        return fc.write(bb);
    }

    public static long writeData(File file, long offset, byte[] array) throws Exception {
        try (FileChannel fc = getFileChannel(file, true /*write*/)) {
            ByteBuffer bbuf = ByteBuffer.wrap(array);
            return writeData(fc, offset, bbuf);
         }
    }

    public static long writeData(File file, long offset, int value) throws Exception {
        try (FileChannel fc = getFileChannel(file, true /*write*/)) {
            ByteBuffer bbuf = ByteBuffer.allocate(4).putInt(value).position(0);
            return writeData(fc, offset, bbuf);
         }
    }

    // dstFile will keep original size so will remove corresponding bytes.length bytes at end of file
    public static File insertBytesRandomlyAfterHeader(File orgFile, String newFileName, byte[] bytes) throws Exception {
        long offset = fileHeaderSize(orgFile) + getRandomBetween(0L, 4096L);
        File dstFile = new File(newFileName);
        try (FileChannel inputChannel = new FileInputStream(orgFile).getChannel();
             FileChannel outputChannel = new FileOutputStream(dstFile).getChannel()) {
            long orgSize = inputChannel.size();
            outputChannel.transferFrom(inputChannel, 0, offset);
            outputChannel.position(offset);
            outputChannel.write(ByteBuffer.wrap(bytes));
            outputChannel.transferFrom(inputChannel, offset + bytes.length, orgSize - bytes.length);
        }
        return dstFile;
    }

    // delete nBytes bytes from offset, so new file will be smaller than the original
    public static File deleteBytesAtRandomPositionAfterHeader(File orgFile, String newFileName, int nBytes) throws Exception {
        long offset = fileHeaderSize(orgFile) + getRandomBetween(0L, 4096L);
        File dstFile = new File(newFileName);
        try (FileChannel inputChannel = new FileInputStream(orgFile).getChannel();
             FileChannel outputChannel = new FileOutputStream(dstFile).getChannel()) {
            long orgSize = inputChannel.size();
            outputChannel.transferFrom(inputChannel, 0, offset);
            inputChannel.position(offset + nBytes);
            outputChannel.transferFrom(inputChannel, offset, orgSize - nBytes);
        }
        return dstFile;
    }

    // used region size
    public static long usedRegionSizeAligned(File archiveFile, int region) throws Exception {
        long offset = spOffset + cdsFileMapRegionSize * region + spUsedOffset;
        long used = readInt(archiveFile, offset, sizetSize);
        return alignUpWithAlignment(used);
    }
}
