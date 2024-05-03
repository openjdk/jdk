/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
   @bug 4290060
   @summary Check if the zip file is closed before access any
            elements in the Enumeration.
   @run junit EnumerateAfterClose
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class EnumerateAfterClose {

    // ZIP file used in this test
    private Path zip = Path.of("enum-after-close.zip");

    /**
     * Create a sample ZIP file for use by this test
     * @throws IOException if an unexpected IOException occurs
     */
    @BeforeEach
    public void setUp() throws IOException {
        try (OutputStream out = Files.newOutputStream(zip);
             ZipOutputStream zo = new ZipOutputStream(out)) {
            zo.putNextEntry(new ZipEntry("file.txt"));
            zo.write("hello".getBytes(StandardCharsets.UTF_8));
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
     * Attempting to using a ZipEntry Enumeration after its backing
     * ZipFile is closed should throw IllegalStateException.
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void enumeratingAfterCloseShouldThrowISE() throws IOException {
        // Retain a reference to an enumeration backed by a closed ZipFile
        Enumeration e;
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            e = zf.entries();
        }
        // Using the enumeration after the ZipFile is closed should throw ISE
        assertThrows(IllegalStateException.class, () -> {
            if (e.hasMoreElements()) {
                ZipEntry ze = (ZipEntry)e.nextElement();
            }
        });
    }
}
