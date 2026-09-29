/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4028605 4109069 4234207 4401122 8339154
 * @summary Verify that ZipInputStream, InflaterInputStream, ZipFileInputStream,
 *          ZipFileInflaterInputStream.available() return values according
 *          to their specification or long-standing behavior
 * @run junit Available
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Available {

    // ZIP file produced in this test
    private final Path zip = Path.of("available.jar");

    /**
     * Create the ZIP file used in this test, containing
     * one deflated and one stored entry.
     *
     * @throws IOException if an unexpected error occurs
     */
    @BeforeEach
    public void setup() throws IOException {
        byte[] contents = "contents".repeat(10).getBytes(StandardCharsets.UTF_8);

        try (ZipOutputStream zo = new ZipOutputStream(Files.newOutputStream(zip))) {
            // First entry uses DEFLATE method
            zo.putNextEntry(new ZipEntry("deflated.txt"));
            zo.write(contents);

            // Second entry uses STORED method
            ZipEntry stored = new ZipEntry("stored.txt");
            stored.setMethod(ZipEntry.STORED);
            stored.setSize(contents.length);
            CRC32 crc32 = new CRC32();
            crc32.update(contents);
            stored.setCrc(crc32.getValue());
            zo.putNextEntry(stored);
            zo.write(contents);
        }
    }

    /**
     * Delete the ZIP file created by this test
     *
     * @throws IOException if an unexpected error occurs
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(zip);
    }

    /**
     * Verify that ZipInputStream.available() returns 0 after EOF or
     * closeEntry, otherwise 1, as specified in the API description.
     * This tests 4028605 4109069 4234207
     * @throws IOException if an unexpected error occurs
     */
    @Test
    public void testZipInputStream() throws IOException {
        try (InputStream in = Files.newInputStream(zip)) {
            ZipInputStream z = new ZipInputStream(in);
            z.getNextEntry();
            assertEquals(1, z.available());
            z.read();
            assertEquals(1, z.available());
            z.transferTo(OutputStream.nullOutputStream());
            assertEquals(0, z.available(),
                    "ZipInputStream.available() should return 0 after EOF");

            z.close();
            assertThrows(IOException.class, () -> z.available(),
                    "Expected an IOException when calling available on a closed stream");
        }

        try (InputStream in = Files.newInputStream(zip);
             ZipInputStream z = new ZipInputStream(in)) {
            z.getNextEntry();
            z.closeEntry();
            assertEquals(0, z.available(),
                    "ZipInputStream.available() should return 0 after closeEntry");
        }
    }

    /**
     * Verify that ZipFileInputStream|ZipFileInflaterInputStream.available()
     * return the number of remaining uncompressed bytes.
     *
     * This verifies unspecified, but long-standing behavior. See 4401122.
     *
     * @throws IOException if an unexpected error occurs
     */
    @ParameterizedTest
    @ValueSource(strings = { "stored.txt", "deflated.txt" })
    public void testZipFileStreamsRemainingBytes(String entryName) throws IOException {
        try (ZipFile zfile = new ZipFile(zip.toFile())) {
            ZipEntry entry = zfile.getEntry(entryName);
            // Could be ZipFileInputStream or ZipFileInflaterInputStream
            InputStream in = zfile.getInputStream(entry);

            int initialAvailable = in.available();

            // Initally, the number of remaining uncompressed bytes is the entry size
            assertEquals(entry.getSize(), initialAvailable);

            // Read all bytes one by one
            for (int i = initialAvailable; i > 0; i--) {
                // Reading a single byte should decrement available by 1
                in.read();
                assertEquals(i - 1, in.available(), "Available not decremented");
            }

            // No remaining uncompressed bytes
            assertEquals(0, in.available());

            // available() should still return 0 after close
            in.close();
            assertEquals(0, in.available());
        }
    }
}
