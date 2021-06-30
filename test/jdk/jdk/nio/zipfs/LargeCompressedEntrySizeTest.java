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
 *
 */

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * This is intentionally a manual test. The (jtreg) configurations below are here only
 * for reference about runtime expectations of this test.
 *
 * @bug 8190753
 * @summary Verify that using zip filesystem for opening an outputstream for a zip entry whose
 * compressed size is large, doesn't run into "Negative initial size" exception
 * @requires (sun.arch.data.model == "64" & os.maxMemory >= 8g)
 * @run testng/othervm -Xmx6g LargeCompressedEntrySizeTest
 */
public class LargeCompressedEntrySizeTest {

    private static final String LARGE_FILE_NAME = "LargeZipEntry.txt";
    private static final String ZIP_FILE_NAME = "8190753-test-compressed-size.zip";

    @BeforeMethod
    public void setUp() throws IOException {
        deleteFiles();
    }

    @AfterMethod
    public void tearDown() throws IOException {
        deleteFiles();
    }

    /**
     * Delete the files created for use by the test
     *
     * @throws IOException if an error occurs deleting the files
     */
    private static void deleteFiles() throws IOException {
        Files.deleteIfExists(Path.of(ZIP_FILE_NAME));
        Files.deleteIfExists(Path.of(LARGE_FILE_NAME));
    }


    /**
     * Using zip filesystem, creates a zip file and writes out a zip entry whose compressed size is
     * expected to be greater than 2gb.
     */
    @Test
    public void testLargeCompressedSizeWithZipFS() throws Exception {
        final Path zipFile = Path.of(ZIP_FILE_NAME);
        final long largeFileSize = (2L * 1024L * 1024L * 1024L) + 1L;
        final Random random = new Random();
        try (FileSystem fs = FileSystems.newFileSystem(zipFile, Collections.singletonMap("create", "true"))) {
            try (OutputStream os = Files.newOutputStream(fs.getPath(LARGE_FILE_NAME))) {
                long remaining = largeFileSize;
                final int chunkSize = 102400;
                for (long l = 0; l < largeFileSize; l+=chunkSize) {
                    final int numToWrite = (int) Math.min(remaining, chunkSize);
                    final byte[] b = new byte[numToWrite];
                    // fill with random bytes
                    random.nextBytes(b);
                    os.write(b);
                    remaining -= b.length;
                }
            }
        }
    }

}
