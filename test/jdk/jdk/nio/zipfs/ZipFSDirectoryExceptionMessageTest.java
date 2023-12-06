/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @test
 * @bug 8305945
 * @summary Validate that Zip FS provides the correct exception message when an
 * attempt is made to obtain an InputStream from a directory entry
 * @modules jdk.zipfs
 * @run junit ZipFSDirectoryExceptionMessageTest
 */
public class ZipFSDirectoryExceptionMessageTest {
    /**
     * Name of Directory created within the Zip file
     */
    public static final String DIRECTORY_NAME = "folder/";
    /**
     * The expected error message
     */
    public static final String DIR_EXCEPTION_MESSAGE = "/folder: is a directory";
    /**
     * Zip file to create
     */
    public static final Path ZIP_FILE = Path.of("directoryExceptionTest.zip");

    /**
     * Create a Zip file which contains a single directory entry
     * @throws IOException if an error occurs
     */
    @BeforeEach
    public void setup() throws IOException {
        var ze = new ZipEntry(DIRECTORY_NAME);
        ze.setMethod(ZipEntry.STORED);
        ze.setCompressedSize(0);
        ze.setSize(0);
        ze.setCrc(0);
        try (ZipOutputStream zos = new ZipOutputStream(
                Files.newOutputStream(ZIP_FILE))) {
            zos.putNextEntry(ze);
        }
    }

    /**
     * Delete the Zip file used by the test
     * @throws IOException If an error occurs
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(ZIP_FILE);
    }

    /**
     * Validate that Zip FS returns the correct Exception message when
     * attempting to obtain an InputStream from a path representing a directory
     * and the FileSystemException::getOtherfile returns null
     *
     * @throws IOException If an error occurs
     */
    @Test
    public void testException() throws IOException {
        try (FileSystem zipfs = FileSystems.newFileSystem(ZIP_FILE)) {
            var file = zipfs.getPath(DIRECTORY_NAME);
            var x = assertThrows(FileSystemException.class, () -> Files.newInputStream(file));
            // validate that other file should be null
            assertNull(x.getOtherFile());
            assertEquals(DIR_EXCEPTION_MESSAGE, x.getMessage());
        }
    }
}
