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
import jdk.test.lib.Utils;

import java.nio.file.Files;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.nio.file.StandardOpenOption;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import jdk.test.lib.cds.CDSTestUtils;
import sun.hotspot.WhiteBox;

// This class performs operations on shared archive file
public class CDSArchiveUtils {
    // wb must be assigned first to use the functions
    public static WhiteBox wb;
    public static int offsetMagic;           // CDSFileMapHeaderBase::_magic
    public static int offsetVersion;         // CDSFileMapHeaderBase::_version
    public static int offsetJvmIdent;        // FileMapHeader::_jvm_ident
    public static int spOffsetCrc;           // CDSFileMapRegion::_crc
    private static int fileHeaderSize = -1;  // total size of header, aligned with pageSize
    public static int cdsFileMapRegionSize;  // size of CDSFileMapRegion
    public static int spOffset;              // offset of CDSFileMapRegion
    public static int spUsedOffset;          // offset of CDSFileMapRegion::_used
    public static int sizetSize;             // size of size_t
    public static int intSize;               // size of int
    public static int pageSize;              // page size
    public static long alignment;            // MetaspaceShared::core_region_alignment

    public static void initialize(WhiteBox box) throws Exception {
        wb = box;
        offsetMagic = wb.getOffsetForName("FileMapHeader::_magic");
        offsetVersion = wb.getOffsetForName("FileMapHeader::_version");
        offsetJvmIdent = wb.getOffsetForName("FileMapHeader::_jvm_ident");
        spOffsetCrc = wb.getOffsetForName("CDSFileMapRegion::_crc");
        try {
            int nonExistOffset = wb.getOffsetForName("FileMapHeader::_non_exist_offset");
            System.exit(-1); // should fail
        } catch (Exception e) {
            // success
        }

        spOffset = wb.getOffsetForName("FileMapHeader::_space[0]") - offsetMagic;
        spUsedOffset = wb.getOffsetForName("CDSFileMapRegion::_used") - spOffsetCrc;
        sizetSize = wb.getOffsetForName("size_t_size");
        intSize = wb.getOffsetForName("int_size");
        cdsFileMapRegionSize  = wb.getOffsetForName("CDSFileMapRegion_size");
        pageSize = wb.getVMPageSize();
        alignment = wb.metaspaceSharedRegionAlignment();
        // fileHeaderSize may not be available
        // fileHeaderSize = (int)alignUpWithPageSize(wb.getOffsetForName("file_header_size"));
    }

    public static int fileHeaderSize() throws Exception {
        if (fileHeaderSize > 0) {
            return fileHeaderSize;
        }

        // this is not real header size, it is struct size
        intSize = wb.getOffsetForName("int_size");
        fileHeaderSize = wb.getOffsetForName("file_header_size");
        fileHeaderSize = (int)alignUpWithPageSize(fileHeaderSize);
        if (fileHeaderSize <= 0 ) {
            throw new RuntimeException("file_header_size is not available");
        }
        return fileHeaderSize;
    }

    public static long alignUpWithPageSize(long l) {
        return (l + pageSize - 1) & (~ (pageSize - 1));
    }

    public static long alignUpWithAlignment(long l) {
        return (l + alignment - 1) & (~ (alignment - 1));
    }

    public static int offsetByName(String name) throws Exception {
        return wb.getOffsetForName(name);
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

    private static FileChannel getFileChannel(File file) throws Exception {
        List<StandardOpenOption> arry = new ArrayList<StandardOpenOption>();
        arry.add(READ);
        arry.add(WRITE);
        return FileChannel.open(file.toPath(), new HashSet<StandardOpenOption>(arry));
    }

    public static long readInt(File file, long offset, int nBytes) throws Exception {
        try (FileChannel fc = getFileChannel(file)) {
            ByteBuffer bb = ByteBuffer.allocate(nBytes);
            bb.order(ByteOrder.nativeOrder());
            fc.position(offset);
            fc.read(bb);
            return  (nBytes > 4 ? bb.getLong(0) : bb.getInt(0));
        }
    }

    private static void writeData(FileChannel fc, long offset, ByteBuffer bb) throws Exception {
        fc.position(offset);
        fc.write(bb);
    }

    public static void writeData(File file, long offset, byte[] array) throws Exception {
        try (FileChannel fc = getFileChannel(file)) {
            ByteBuffer bbuf = ByteBuffer.wrap(array);
            writeData(fc, offset, bbuf);
         }
    }

    public static File modifyByOffsetName(File archiveFile, String offsetName, byte[] replace) throws Exception {
        int offset = offsetByName(offsetName);
        try (FileChannel fc = getFileChannel(archiveFile)) {
            ByteBuffer bbuf = ByteBuffer.wrap(replace);
            writeData(fc, offset, bbuf);
        }
        return archiveFile;
    }

    // dstFile will keep original size so will remove corresponding bytes.length bytes at end of file
    public static File insertBytesAtOffset(File orgFile, File dstFile, long offset, byte[] bytes) throws Exception {
        try (FileChannel inputChannel = new FileInputStream(orgFile).getChannel();
             FileChannel outputChannel = new FileOutputStream(dstFile).getChannel()) {
            long orgSize = inputChannel.size();
            outputChannel.transferFrom(inputChannel, 0, offset);
            outputChannel.write(ByteBuffer.wrap(bytes));
            outputChannel.transferFrom(inputChannel, offset + bytes.length, orgSize - bytes.length);
        }
        return dstFile;
    }

    // delete nBytes bytes from offset, so new file will be smaller than the original
    public static File deleteBytesAtOffset(File orgFile, File dstFile, long offset, int nBytes) throws Exception {
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
