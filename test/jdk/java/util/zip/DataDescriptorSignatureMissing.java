/*
 * Copyright 2012 Google, Inc.  All Rights Reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verify the ability to read zip files whose local header
 * data descriptor is missing the optional signature
 * <p>
 * No way to adapt the technique in this test to get a ZIP64 zip file
 * without data descriptors was found.
 * @run junit DataDescriptorSignatureMissing
 */


import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

public class DataDescriptorSignatureMissing {

    /**
     * Verify that ZipInputStream correctly parses a ZIP with a Data Descriptor without
     * the recommended but optional signature.
     */
    @Test
    public void shouldParseSignaturelessDescriptor() throws IOException {
        // The ZIP with a signature-less descriptor
        byte[] zip = makeZipWithSignaturelessDescriptor();

        // ZipInputStream should read the signature-less data descriptor
        try (ZipInputStream in = new ZipInputStream(
                new ByteArrayInputStream(zip))) {
            ZipEntry first = in.getNextEntry();
            assertNotNull(first, "Zip file is unexpectedly missing first entry");
            assertEquals("first", first.getName());
            assertArrayEquals("first".getBytes(StandardCharsets.UTF_8), in.readAllBytes());

            ZipEntry second = in.getNextEntry();
            assertNotNull(second, "Zip file is unexpectedly missing second entry");
            assertEquals("second", second.getName());
            assertArrayEquals("second".getBytes(StandardCharsets.UTF_8), in.readAllBytes());
        }

    }

    /**
     * The 'Data descriptor' record is used to facilitate ZIP streaming. If the size of an
     * entry is unknown at the time the LOC header is written, bit 3 of the General Purpose Bit Flag
     * is set, and the File data is immediately followed by the 'Data descriptor' record. This record
     * then contains the compressed and uncompressed sizes of the entry and also the CRC value.
     *
     * The 'Data descriptor' record is usually preceded by the recommended, but optional
     * signature value 0x08074b50.
     *
     * A ZIP entry in streaming mode has the following structure:
     *
     *  ------  Local File Header  ------
     *  000000  signature          0x04034b50
     *  000004  version            20
     *  000006  flags              0x0808   # Notice bit 3 is set
     *  [..] Omitted for brevity
     *
     *  ------  File Data  ------
     *  000035  data               7 bytes
     *
     *  ------  Data Descriptor  ------
     *  000042  signature          0x08074b50
     *  000046  crc                0x3610a686
     *  000050  csize              7
     *  000054  size               5
     *
     * A signature-less data descriptor will look like the following:
     *
     *  ------  Data Descriptor  ------
     *  000042  crc                0x3610a686
     *  000046  csize              7
     *  000050  size               5
     *
     * This method produces a ZIP with two entries, where the first entry
     * is made signature-less.
     */
    private static byte[] makeZipWithSignaturelessDescriptor() throws IOException {
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
        // Skip the signature
        sigLess.write(sigZip, crcOffset, sigZip.length - crcOffset);

        byte[] siglessZip = sigLess.toByteArray();

        // Adjust the CEN offset in the END header
        ByteBuffer buffer = ByteBuffer.wrap(siglessZip).order(ByteOrder.LITTLE_ENDIAN);
        // Reduce cenOffset by 4 bytes
        int cenOff = siglessZip.length - ZipFile.ENDHDR + ZipFile.ENDOFF;
        int realCenOff = buffer.getInt(cenOff) - Integer.BYTES;
        buffer.putInt(cenOff, realCenOff);

        // Adjust the LOC offset in the second CEN header
        int cen = realCenOff;
        // Skip past the first CEN header
        int nlen = buffer.getShort(cen + ZipFile.CENNAM);
        cen += ZipFile.CENHDR + nlen;

        // Reduce LOC offset by 4 bytes
        int locOff = cen + ZipFile.CENOFF;
        buffer.putInt(locOff, buffer.getInt(locOff) - Integer.BYTES);

        return siglessZip;
    }
}
