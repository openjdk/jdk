/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4214795
 * @summary Make sure the same inflater will only be recycled
 *          once.
 * @run junit ReleaseInflater
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReleaseInflater {

    // ZIP file produced in this test
    private Path zip = Path.of("release-inflater.zip");

    /**
     * Create a sample ZIP file for use by tests
     * @param name name of the ZIP file to create
     * @return a sample ZIP file
     * @throws IOException if an unexpected IOException occurs
     */
    @BeforeEach
    public void setUp() throws IOException {
        try (ZipOutputStream zo = new ZipOutputStream(Files.newOutputStream(zip))) {
            zo.putNextEntry(new ZipEntry("file.txt"));
            zo.write("helloworld".getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Delete the ZIP and JAR files produced after each test method
     * @throws IOException if an unexpected IOException occurs
     */

    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(zip);
    }

    /**
     * Verify that the same Inflater is not recycled across input streams
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void recycleInflaterOnlyOnce() throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry e = zf.getEntry("file.txt");

            InputStream in1 = zf.getInputStream(e);
            // close the stream, the inflater will be released
            in1.close();
            // close the stream again, should be no-op
            in1.close();

            // create two new streams, allocating inflaters
            InputStream in2 = zf.getInputStream(e);
            InputStream in3 = zf.getInputStream(e);

            // check to see if they influence each other
            assertEquals(in2.read(), in3.read(), "Stream is corrupted!");
        }
    }
}
