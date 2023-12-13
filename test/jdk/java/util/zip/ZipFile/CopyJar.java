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

/* @test 1.1 99/06/01
   @bug 4239446
   @summary Make sure the ZipEntry fields are correct.
   @run junit CopyJar
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class CopyJar {

    // ZIP file produced by this test
    private Path jar = Path.of("copy-jar.jar");

    /**
     * Create a sample ZIP file used by this test
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @BeforeEach
    public void setUp() throws IOException {
        try (JarOutputStream jo = new JarOutputStream(Files.newOutputStream(jar), new Manifest())) {
            jo.putNextEntry(new ZipEntry("file.txt"));
            jo.write("helloworld".getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Clean up the ZIP file produced by this test
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(jar);
    }

    /**
     * Check that a ZipEntry read by ZipFile.getEntry does not produce
     * a CRC value inconsistent with the CRC computed when the entry
     * and its content is copied over to a ZipOutputStream
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void copyingZipEntryShouldFailCRCValidation() throws IOException {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            ZipEntry ze = zf.getEntry("file.txt");

            try (ZipOutputStream zos = new ZipOutputStream(OutputStream.nullOutputStream());
                 InputStream in = zf.getInputStream(ze)) {
                /* The original bug mentions that ZipEntry
                 * 'loses the correct CRC value read from the CEN directory'.
                 * Enable the code below to trigger a ZipException similar to the bug description
                 */
                if (false) {
                    // Reset the CRC, as if a zero value was read from a streaming mode LOC header
                    ze.setCrc(0);
                    // Required to set ZipEntry.csizeSet = true
                    ze.setCompressedSize(ze.getCompressedSize());
                }
                zos.putNextEntry(ze);
                in.transferTo(zos);
            }
        }
    }
}
