/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
   @bug 8340684
   @summary Verify unspecified, but long-standing behavior when reading
   from an input stream obtained using ZipFile::getInputStream after
   the ZipFile has been closed.
   @run junit ReadAfterClose
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ReadAfterClose {

    // ZIP file used in this test
    private Path zip = Path.of("read-after-close.zip");

    /**
     * Create a sample ZIP file for use by this test
     * @throws IOException if an unexpected IOException occurs
     */
    @BeforeEach
    public void setUp() throws IOException {
        byte[] content = "hello".repeat(1000).getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = Files.newOutputStream(zip);
             ZipOutputStream zo = new ZipOutputStream(out)) {
            {
                zo.putNextEntry(new ZipEntry("deflated.txt"));
                zo.write(content);
            }
            {
                ZipEntry entry = new ZipEntry("stored.txt");
                entry.setMethod(ZipEntry.STORED);
                CRC32 crc = new CRC32();
                crc.update(content);
                entry.setCrc(crc.getValue());
                entry.setSize(content.length);
                zo.putNextEntry(entry);
                zo.write(content);
            }
        }
    }

    /**
     * Delete the ZIP file produced by this test
     * @throws IOException if an unexpected IOException occurs
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(zip);
    }

    /**
     * Produce arguments with a variation of stored / deflated entries,
     * and read behavior before closing the ZipFile.
     * @return
     */
    public static Stream<Arguments> arguments() {
        return Stream.of(
                Arguments.of("stored.txt",   true),
                Arguments.of("stored.txt",   false),
                Arguments.of("deflated.txt", true),
                Arguments.of("deflated.txt", false)
        );
    }
    /**
     * Attempting to read from an InputStream obtained by ZipFile.getInputStream
     * after the backing ZipFile is closed should throw IOException
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @ParameterizedTest
    @MethodSource("arguments")
    public void readAfterClose(String entryName, boolean readFirst) throws IOException {
        // Retain a reference to an input stream backed by a closed ZipFile
        InputStream in;
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            in = zf.getInputStream(new ZipEntry(entryName));
            // Optionally consume a single byte from the stream before closing
            if (readFirst) {
                in.read();
            }
        }

        assertThrows(IOException.class, () -> {
            in.read();
        });
    }
}