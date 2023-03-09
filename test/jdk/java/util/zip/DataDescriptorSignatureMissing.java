/*
 * Copyright 2012 Google, Inc.  All Rights Reserved.
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

/**
 * @test
 * @bug 8056934
 * @summary Check ability to read zip files created by python zipfile
 * implementation, which fails to write optional (but recommended) data
 * descriptor signatures.
 * <p>
 * No way to adapt the technique in this test to get a ZIP64 zip file
 * without data descriptors was found.
 * @run testng DataDescriptorSignatureMissing
 */

import org.testng.annotations.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

import static org.testng.Assert.assertEquals;

public class DataDescriptorSignatureMissing {

    @Test
    public void shouldParseSignaturlessDescriptor() throws IOException {
        // The ZIP with a signature-less descriptor
        byte[] zip = makeZipWithSiglessDescriptor();

        // ZipInputStream should read the signature-less data descriptor
        try (ZipInputStream in = new ZipInputStream(
                new ByteArrayInputStream(zip))) {
            ZipEntry first = in.getNextEntry();
            assertEquals(first.getName(), "first");
            assertEquals(in.readAllBytes(), "first".getBytes(StandardCharsets.UTF_8));

            ZipEntry second = in.getNextEntry();
            assertEquals(second.getName(), "second");
            assertEquals(in.readAllBytes(), "second".getBytes(StandardCharsets.UTF_8));
        }

    }

    private static byte[] makeZipWithSiglessDescriptor() throws IOException {
        // Offset of the signed data descriptor
        int sigOffset;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zo = new ZipOutputStream(out)) {
            // Write a first entry
            zo.putNextEntry(new ZipEntry("first"));
            zo.write("first".getBytes(StandardCharsets.UTF_8));
            // Force the data descriptor to be written out
            zo.closeEntry();
            // Signed data descriptor starts 16 bytes before current offset
            sigOffset = out.size() - 4 * Integer.BYTES;
            // Add a second entry
            zo.putNextEntry(new ZipEntry("second"));
            zo.write("second".getBytes(StandardCharsets.UTF_8));
        }

        // The generated ZIP file with a signed data descriptor
        byte[] sigZip = out.toByteArray();

        // The offset of the CRC immediately following the 4-byte signature
        int crcOffset = sigOffset + Integer.BYTES;

        // Create a ZIP file with a signature-less data descriptor for the first entry
        ByteArrayOutputStream sigLess = new ByteArrayOutputStream();
        sigLess.write(sigZip, 0, sigOffset);
        sigLess.write(sigZip, crcOffset, sigZip.length - crcOffset);

        return sigLess.toByteArray();
    }
}
