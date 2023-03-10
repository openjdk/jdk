/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static java.lang.String.format;

/**
 * @test
 * @bug 8255380 8257445
 * @summary Test that Zip FS can access the LOC offset from the Zip64 extra field
 * @modules jdk.zipfs
 * @run testng TestLocOffsetFromZip64EF
 */
public class TestLocOffsetFromZip64EF {

    // Buffer used when writing zero-filled entries in the sparse file
    private final static byte[] EMPTY_BYTES = new byte[16384];

    private static final String ZIP_FILE_NAME = "LargeZipTest.zip";

    private static final long LARGE_FILE_SIZE = 4L * 1024L * 1024L * 1024L;

    /**
     * Create the files used by this test
     *
     * @throws IOException if an error occurs
     */
    @BeforeClass
    public void setUp() throws IOException {
        cleanup();
        createZipWithZip64Ext();
    }

    /**
     * Delete files used by this test
     * @throws IOException if an error occurs
     */
    @AfterClass
    public void cleanup() throws IOException {
        Files.deleteIfExists(Path.of(ZIP_FILE_NAME));
    }

    /*
     * DataProvider used to verify that a Zip file that contains a Zip64 Extra
     * (EXT) header can be traversed
     */
    @DataProvider(name = "zipInfoTimeMap")
    protected Object[][] zipInfoTimeMap() {
        return new Object[][]{
                {Map.of()},
                {Map.of("zipinfo-time", "False")},
                {Map.of("zipinfo-time", "true")},
                {Map.of("zipinfo-time", "false")}
        };
    }

    /**
     * Navigate through the Zip file entries using Zip FS
     * @param env Zip FS properties to use when accessing the Zip file
     * @throws IOException if an error occurs
     */
    @Test(dataProvider = "zipInfoTimeMap")
    public void walkZipFSTest(final Map<String, String> env) throws IOException {
        try (FileSystem fs =
                     FileSystems.newFileSystem(Paths.get(ZIP_FILE_NAME), env)) {
            for (Path root : fs.getRootDirectories()) {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes
                            attrs) throws IOException {
                        System.out.println(Files.readAttributes(file,
                                BasicFileAttributes.class).toString());
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }

    /**
     * Navigate through the Zip file entries using ZipFile
     * @throws IOException if an error occurs
     */
    @Test
    public void walkZipFileTest() throws IOException {
        try (ZipFile zip = new ZipFile(ZIP_FILE_NAME)) {
            zip.stream().forEach(z -> System.out.printf("%s, %s, %s%n",
                    z.getName(), z.getMethod(), z.getLastModifiedTime()));
        }
    }

    /**
     * Create a ZIP file where the second entry is large enough to output a
     * Zip64 CEN entry where the LOC offset is found inside a Zip64 Extra field.
     */
    public void createZipWithZip64Ext() throws IOException {
        // Make a ZIP with two entries
        try (FileOutputStream fileOutputStream = new FileOutputStream(new File(ZIP_FILE_NAME));
             ZipOutputStream zo = new ZipOutputStream(new SparseOutputStream(fileOutputStream))) {

            // First entry is a small DEFLATED entry
            zo.putNextEntry(new ZipEntry("first"));

            // Second entry is STORED to enable sparse writing
            ZipEntry e = new ZipEntry("second");
            e.setMethod(ZipEntry.STORED);
            e.setSize(LARGE_FILE_SIZE);
            e.setCrc(crc(LARGE_FILE_SIZE));

            // This forces an Info-ZIP Extended Timestamp extra field to be produced
            e.setLastModifiedTime(FileTime.from(Instant.now()));
            zo.putNextEntry(e);

            // Write LARGE_FILE_SIZE empty bytes
            for (int i = 0; i < LARGE_FILE_SIZE / EMPTY_BYTES.length; i++) {
                zo.write(EMPTY_BYTES, 0, EMPTY_BYTES.length);
            }
        }
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
     * An OutputStream which creates sparse holes when contents
     * from EMPTY_BYTES is written to it.
     */
    private class SparseOutputStream extends FilterOutputStream {
        private final FileChannel channel;

        public SparseOutputStream(FileOutputStream fileOutputStream) {
            super(fileOutputStream);
            this.channel = fileOutputStream.getChannel();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (b == EMPTY_BYTES) {
                channel.position(channel.position() + len);
            } else {
                super.write(b, off, len);
            }
        }
    }
}
