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
   @bug 8340814
   @summary Verify that ZipFileInputStream releases its Inflater after encountering EOF
   @modules java.base/java.util.zip:+open
   @run junit ReleaseInflaterOnEOF
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ReleaseInflaterOnEOF {

    // ZIP file produced during tests
    private Path zip = Path.of("release-zip-inflaters.zip");

    /**
     * Create a sample ZIP file for use by tests
     * @param name name of the ZIP file to create
     * @return a sample ZIP file
     * @throws IOException if an unexpected IOException occurs
     */
    private Path createZip(String name) throws IOException {
        Path zip = Path.of(name);

        try (OutputStream out = Files.newOutputStream(zip);
             ZipOutputStream zo = new ZipOutputStream(out)) {
            zo.putNextEntry(new ZipEntry("deflated.txt"));
            zo.write("hello".getBytes(StandardCharsets.UTF_8));
        }

        return zip;
    }

    /**
     * Delete the ZIP file produced after each test method
     * @throws IOException if an unexpected IOException occurs
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(zip);
    }

    /**
     * Verify that ZipFileInflaterInputStream releases its Inflater to the
     * pool after the stream has been fully consumed.
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void shouldReleaseInflaterAfterEof() throws Exception {
        zip = createZip("release-inflater.zip");
        try (var zf = new ZipFile(zip.toFile())) {

            ZipEntry entry = new ZipEntry("deflated.txt");

            Inflater initialInflater;

            // Open an input stream
            try (var in = zf.getInputStream(entry)) {
                // Record its Inflater instance
                initialInflater = getInflater(in);
            }
            // Closing the input stream releases the Inflater back to the cache
            // The Inflater cache now has a single entry

            for (int i = 0; i < 100; i++) {
                var is = zf.getInputStream(entry);
                // Assert that the ZipFileInflaterInputStream reused the cached Inflater instance
                assertSame(initialInflater, getInflater(is));
                // Fully consume the stream to allow ZipFileInflaterInputStream to observe the EOF
                is.transferTo(OutputStream.nullOutputStream());
            }
        }
    }

    // Use reflection to get the ZipFileInflaterInputStream's Inflater instance
    private Inflater getInflater(InputStream in) throws IllegalAccessException, NoSuchFieldException {
        Field field = InflaterInputStream.class.getDeclaredField("inf");
        field.setAccessible(true);
        return (Inflater) field.get(in);
    }
}