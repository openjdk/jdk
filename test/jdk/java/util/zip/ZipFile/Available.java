/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @run junit Available
 */
public class Available {
    // ZIP file produced by this test
    private Path zip = Path.of("available.zip");
    // The number of uncompressed bytes to write to the ZIP entry
    private static final int EXPECTED_BYTES = 512;

    /**
     * Produce a ZIP file containing an entry with byte length
     * @throws IOException if an unexpected IOException occurs
     */
    @BeforeEach
    public void setUp() throws IOException {
        try (ZipOutputStream zo = new ZipOutputStream(Files.newOutputStream(zip))) {
            zo.putNextEntry(new ZipEntry("file.txt"));
            zo.write(new byte[EXPECTED_BYTES]);
        }
    }

    /**
     * Clean up the ZIP file produced by this test
     * @throws IOException if an unexpected IOException occurs
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(zip);
    }

    /**
     * Check that the available() method overriden by the input stream returned by
     * ZipFile.getInputStream correctly returns the number of remaining uncompressed bytes
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void shouldReturnRemainingUncompressedBytes() throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry e = zf.getEntry("file.txt");
            try (InputStream in = zf.getInputStream(e)) {
                // Initially, available() should return the full uncompressed size of the entry
                assertEquals(EXPECTED_BYTES, in.available(),
                        "wrong initial return value of available");

                // Reading a few bytes should reduce the number of available bytes accordingly
                int bytesToRead = 10;
                in.read(new byte[bytesToRead]);
                assertEquals(EXPECTED_BYTES - bytesToRead, in.available());

                // Reading all remaining bytes should reduce the number of available bytes to zero
                in.transferTo(OutputStream.nullOutputStream());
                assertEquals(0, in.available());

                // available on a closed input stream should return zero
                in.close();
                assertEquals(0, in.available());
            }
        }
    }
}
