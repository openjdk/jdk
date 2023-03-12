/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verifies that ZipFile rejects ZIP files which CEN size does not fit in a Java byte array
 * @requires (sun.arch.data.model == "64" & os.maxMemory > 8g)
 * @run testng/manual/othervm -Xmx8g CenSizeTooLarge
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

import static org.testng.Assert.*;

public class CenSizeTooLarge {
    // Maximum allowed CEN size allowed by the ZipFile implementation
    private static int MAX_CEN_SIZE = Integer.MAX_VALUE - ZipFile.ENDHDR - 1;

    // Zip file to create for testing
    private File hugeZipFile;

    /**
     * Create a zip file with a CEN size which does not fit within a Java byte array
     */
    @BeforeTest
    public void setup() throws IOException {
        hugeZipFile = new File("cen-too-large.zip");
        hugeZipFile.deleteOnExit();

        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(hugeZipFile)))) {
            long cenSize = 0;
            // Add entries until MAX_CEN_SIZE is reached
            for (int i = 0; cenSize < MAX_CEN_SIZE; i++) {
                String name = Long.toString(i);
                ZipEntry entry = new ZipEntry(name);
                // Use STORED for faster processing
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(0);
                entry.setCrc(0);
                zip.putNextEntry(entry);
                // Calculate current cenSize
                cenSize += ZipFile.CENHDR + name.length();
            }
        }
    }

    /**
     * Validates that a ZipException is thrown with the expected message when
     * the ZipFile is initialized with a ZIP whose CEN exeeds {@link #MAX_CEN_SIZE}
     */
    @Test
    public void test() {
        ZipException ex = expectThrows(ZipException.class, () -> new ZipFile(hugeZipFile));
        assertEquals(ex.getMessage(), "invalid END header (central directory size too large)");
    }
}
