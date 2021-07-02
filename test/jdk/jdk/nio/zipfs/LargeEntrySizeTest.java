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
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @test
 * @bug 8190753
 * @summary Verify that using zip filesystem for opening an outputstream for a large zip entry doesn't
 * run into "Negative initial size" exception
 * @run testng LargeEntrySizeTest
 */
public class LargeEntrySizeTest {

    // a value which when cast to an integer, becomes a negative value
    private static final long LARGE_FILE_SIZE = Integer.MAX_VALUE + 1L;
    private static final long SMALL_FILE_SIZE = 0x100000L; // 1024L x 1024L;
    private static final String LARGE_FILE_NAME = "LargeZipEntry.txt";
    // File that will be created with a size less than 0xFFFFFFFF
    private static final String SMALL_FILE_NAME = "SmallZipEntry.txt";
    // List of files to be added to the ZIP file
    private static final List<String> ZIP_ENTRIES = List.of(LARGE_FILE_NAME, SMALL_FILE_NAME);
    private static final String ZIP_FILE_NAME = "8190753-test.zip";

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
        Files.deleteIfExists(Path.of(SMALL_FILE_NAME));
    }


    /**
     * Verifies that large entry (whose size is greater than {@link Integer#MAX_VALUE}) in a zip file
     * can be opened as an {@link OutputStream} using the zip filesystem
     */
    @Test
    public void testLargeEntryZipFSOutputStream() throws Exception {
        final Path zipFile = Path.of(ZIP_FILE_NAME);
        createZipFile(zipFile);
        try (FileSystem fs = FileSystems.newFileSystem(zipFile)) {
            for (String entryName : ZIP_ENTRIES) {
                try (OutputStream os = Files.newOutputStream(fs.getPath(entryName), StandardOpenOption.WRITE)) {
                    // just a dummy write
                    os.write(0x01);
                }
            }
        }
    }

    /**
     * Creates a zip file with an entry whose size is larger than {@link Integer#MAX_VALUE}
     */
    private static void createZipFile(final Path zipFile) throws IOException {
        createFiles();
        try (OutputStream os = Files.newOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            System.out.println("Creating Zip file: " + zipFile.getFileName());
            for (String srcFile : ZIP_ENTRIES) {
                File fileToZip = new File(srcFile);
                long fileSize = fileToZip.length();
                System.out.println("Adding entry " + srcFile + " of size " + fileSize + " bytes");
                try (FileInputStream fis = new FileInputStream(fileToZip)) {
                    ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
                    zipEntry.setSize(fileSize);
                    zos.putNextEntry(zipEntry);
                    fis.transferTo(zos);
                }
            }
        }
    }

    /**
     * Create the files that will be added to the ZIP file
     */
    private static void createFiles() throws IOException {
        try (RandomAccessFile largeFile = new RandomAccessFile(LARGE_FILE_NAME, "rw");
             RandomAccessFile smallFile = new RandomAccessFile(SMALL_FILE_NAME, "rw")) {
            System.out.printf("Creating %s%n", LARGE_FILE_NAME);
            largeFile.setLength(LARGE_FILE_SIZE);
            System.out.printf("Creating %s%n", SMALL_FILE_NAME);
            smallFile.setLength(SMALL_FILE_SIZE);
        }
    }

}
