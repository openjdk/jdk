/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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
   @bug 7003462
   @summary Make sure cached Inflater does not get finalized.
   @run junit FinalizeInflater
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class FinalizeInflater {

    // ZIP file produced by this test
    private Path zip = Path.of("finalize-inflater.zip");

    /**
     * Create the sample ZIP used in this test
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @BeforeEach
    public void setUp() throws IOException {
        try (OutputStream out = Files.newOutputStream(zip);
             ZipOutputStream zo = new ZipOutputStream(out)) {
            zo.putNextEntry(new ZipEntry("file.txt"));
            byte[] hello = "hello".getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 100; i++) {
                zo.write(hello);
            }
        }
    }

    /**
     * Delete the ZIP file produced by this test
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(zip);
    }

    /**
     * A cached Inflater should not be made invalid by finalization
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void shouldNotFinalizeInflaterInPool() throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry ze = zf.getEntry("file.txt");
            read(zf.getInputStream(ze));
            System.gc();
            System.runFinalization();
            System.gc();
            // read again
            read(zf.getInputStream(ze));
        }
    }

    private static void read(InputStream is)
        throws IOException
    {
        Wrapper wrapper = new Wrapper(is);
        is.readAllBytes();
    }

    static class Wrapper {
        InputStream is;
        public Wrapper(InputStream is) {
            this.is = is;
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            is.close();
        }
    }
}
