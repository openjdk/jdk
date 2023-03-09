/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.testng.Assert.assertTrue;

/**
 * @test
 * @bug 8226530 8303891
 * @summary Verify that ZipFile reads size fields using the Zip64 extra field for entries
 * of size > 0xFFFFFFFF
 * @compile Zip64SizeTest.java
 * @run testng Zip64SizeTest
 */
public class Zip64SizeTest {
    // Buffer used when writing zero-filled entries
    private static final byte[] EMPTY_BYTES = new byte[16384];
    // ZIP file to create
    private static final String ZIP_FILE_NAME = "Zip64SizeTest.zip";
    // File that will be created with a size greater than 0xFFFFFFFF
    private static final String LARGE_FILE_NAME = "LargeZipEntry.txt";
    // File that will be created with a size less than 0xFFFFFFFF
    private static final String SMALL_FILE_NAME = "SmallZipEntry.txt";
    private static final long LARGE_FILE_SIZE = 5L * 1024L * 1024L * 1024L; // 5GB
    private static final long SMALL_FILE_SIZE = 0x1024L * 1024L; // 1MB

    /**
     * Validate that if the size of a ZIP entry exceeds 0xFFFFFFFF, that the
     * correct size is returned from the ZIP64 Extended information.
     * @throws IOException
     */
    @Test
    private static void validateZipEntrySizes() throws IOException {
        createZipFile();
        System.out.println("Validating Zip Entry Sizes");
        try (ZipFile zip = new ZipFile(ZIP_FILE_NAME)) {
            ZipEntry ze = zip.getEntry(LARGE_FILE_NAME);
            System.out.printf("Entry: %s, size= %s%n", ze.getName(), ze.getSize());
            assertTrue(ze.getSize() == LARGE_FILE_SIZE);
            ze = zip.getEntry(SMALL_FILE_NAME);
            System.out.printf("Entry: %s, size= %s%n", ze.getName(), ze.getSize());
            assertTrue(ze.getSize() == SMALL_FILE_SIZE);
        }
    }

    /**
     * Delete the files created for use by the test
     * @throws IOException if an error occurs deleting the files
     */
    private static void deleteFiles() throws IOException {
        Files.deleteIfExists(Path.of(ZIP_FILE_NAME));
        Files.deleteIfExists(Path.of(LARGE_FILE_NAME));
        Files.deleteIfExists(Path.of(SMALL_FILE_NAME));
    }

    /**
     * Create the ZIP file adding an entry whose size exceeds 0xFFFFFFFF
     *
     * @throws IOException if an error occurs creating the ZIP File
     */
    private static void createZipFile() throws IOException {
        try (OutputStream fos = new SparseOutputStream(new FileOutputStream(ZIP_FILE_NAME));
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            System.out.printf("Creating Zip file: %s%n", ZIP_FILE_NAME);

            addEntry(LARGE_FILE_NAME, LARGE_FILE_SIZE, zos);
            addEntry(SMALL_FILE_NAME, SMALL_FILE_SIZE, zos);
        }
    }

    /**
     * Add a STORED entry with the given name and size. The file content is filled with zero-bytes.
     */
    private static void addEntry(String entryName, long size, ZipOutputStream zos) throws IOException {
        ZipEntry e = new ZipEntry(entryName);
        e.setMethod(ZipEntry.STORED);
        e.setSize(size);
        e.setCrc(crc(size));
        zos.putNextEntry(e);

        // Write size number of empty bytes
        long rem = size;
        while (rem > 0) {
            int lim = EMPTY_BYTES.length;
            if (rem < lim) {
                lim = (int) rem;
            }
            // Allows SparseOutputStream to simply advance position
            zos.write(EMPTY_BYTES, 0, lim);
            rem -= lim;
        }
    }

    /**
     * Make sure the needed test files do not exist prior to executing the test
     * @throws IOException
     */
    @BeforeMethod
    public void setUp() throws IOException {
        deleteFiles();
    }

    /**
     * Remove the files created for the test
     * @throws IOException
     */
    @AfterMethod
    public void tearDown() throws IOException {
        deleteFiles();
    }

    /**
     * Compute the CRC for a file of the given size filled with zero-bytes
     */
    private static long crc(long size) {
        CRC32 crc32 = new CRC32();
        long rem = size;
        while (rem > 0) {
            int lim = EMPTY_BYTES.length;
            if (rem < lim) {
                lim = (int) rem;
            }
            crc32.update(EMPTY_BYTES, 0, lim);
            rem -= lim;
        }

        return crc32.getValue();
    }

    /**
     * An OutputStream which creates sparse holes contents from EMPTY_BYTES
     * is written to it.
     */
    private static class SparseOutputStream extends FilterOutputStream {
        private final FileChannel channel;

        public SparseOutputStream(FileOutputStream fileOutputStream) {
            super(fileOutputStream);
            this.channel = fileOutputStream.getChannel();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (b == EMPTY_BYTES) {
                // Create a sparse 'hole' in the file instead of writing bytes
                channel.position(channel.position() + len);
            } else {
                super.write(b, off, len);
            }
        }
    }
}
