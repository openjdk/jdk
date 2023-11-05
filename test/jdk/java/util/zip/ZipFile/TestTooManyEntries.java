/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8272746
 * @summary ZipFile can't open big file (NegativeArraySizeException)
 * @requires (sun.arch.data.model == "64" & os.maxMemory > 8g)
 * @run testng/manual/othervm -Xmx8g TestTooManyEntries
 */

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.File;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.util.UUID;

import static org.testng.Assert.assertThrows;

public class TestTooManyEntries {
    // Number of directories in the zip file
    private static final int DIR_COUNT = 25000;
    // Number of entries per directory
    private static final int ENTRIES_IN_DIR = 1000;

    // Zip file to create for testing
    private File hugeZipFile;

    /**
     * Create a zip file and add entries that exceed the CEN limit.
     * @throws IOException if an error occurs creating the ZIP File
     */
    @BeforeTest
    public void setup() throws IOException {
        hugeZipFile = File.createTempFile("hugeZip", ".zip", new File("."));
        hugeZipFile.deleteOnExit();
        long startTime = System.currentTimeMillis();
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(hugeZipFile)))) {
            for (int dirN = 0; dirN < DIR_COUNT; dirN++) {
                String dirName = UUID.randomUUID() + "/";
                for (int fileN = 0; fileN < ENTRIES_IN_DIR; fileN++) {
                    ZipEntry entry = new ZipEntry(dirName + UUID.randomUUID());
                    zip.putNextEntry(entry);
                    zip.closeEntry(); // all files are empty
                }
                if ((dirN + 1) % 1000 == 0) {
                    System.out.printf("%s / %s of entries written, file size is %sMb (%ss)%n",
                            (dirN + 1) * ENTRIES_IN_DIR, DIR_COUNT * ENTRIES_IN_DIR, hugeZipFile.length() / 1024 / 1024,
                            (System.currentTimeMillis() - startTime) / 1000);
                }
            }
        }
    }

    /**
     * Validates that the ZipException is thrown when the ZipFile class
     * is initialized with a zip file whose entries exceed the CEN limit.
     */
    @Test
    public void test() {
        assertThrows(ZipException.class, () -> new ZipFile(hugeZipFile));
    }
}
