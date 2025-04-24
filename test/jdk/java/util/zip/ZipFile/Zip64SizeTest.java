/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @test
 * @bug 8226530 8303891
 * @summary Verify that ZipFile reads size fields using the Zip64 extra
 * field when only the 'uncompressed size' field has the ZIP64 "magic value" 0xFFFFFFFF
 * @compile Zip64SizeTest.java
 * @run junit Zip64SizeTest
 */
public class Zip64SizeTest {
    // ZIP file to create
    private static final Path ZIP_FILE = Path.of("Zip64SizeTest.zip");
    // Contents to write to ZIP entries
    private static final byte[] CONTENT = "Hello".getBytes(StandardCharsets.UTF_8);
    // This opaque tag will be ignored by ZipEntry.setExtra0
    private static final int UNKNOWN_TAG = 0x9902;
    // Tag used when converting the extra field to a real ZIP64 extra field
    private static final short ZIP64_TAG = 0x1;
    // Marker value to indicate that the actual value is stored in the ZIP64 extra field
    private static final int ZIP64_MAGIC_VALUE = 0xFFFFFFFF;

    /**
     * Validate that if the 'uncompressed size' of a ZIP CEN header is 0xFFFFFFFF, then the
     * actual size is retrieved from the corresponding ZIP64 Extended information field.
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void validateZipEntrySizes() throws IOException {
        createZipFile();
        System.out.println("Validating Zip Entry Sizes");
        try (ZipFile zip = new ZipFile(ZIP_FILE.toFile())) {
            ZipEntry ze = zip.getEntry("first");
            System.out.printf("Entry: %s, size= %s%n", ze.getName(), ze.getSize());
            assertEquals(CONTENT.length, ze.getSize());
            ze = zip.getEntry("second");
            System.out.printf("Entry: %s, size= %s%n", ze.getName(), ze.getSize());
            assertEquals(CONTENT.length, ze.getSize());
        }
    }

    /**
     * Create a ZIP file with a CEN entry where the 'uncompressed size' is stored in
     * the ZIP64 field, but the 'compressed size' is in the CEN field. This makes the
     * ZIP64 data block 8 bytes long, which triggers the regression described in 8226530.
     *
     * The CEN entry for the "first" entry will have the following structure:
     * (Note the CEN 'Uncompressed Length' being 0xFFFFFFFF and the ZIP64
     * 'Uncompressed Size' being 5)
     *
     * 0081 CENTRAL HEADER #1     02014B50
     * 0085 Created Zip Spec      14 '2.0'
     * 0086 Created OS            00 'MS-DOS'
     * [...] Omitted for brevity
     * 0091 CRC                   F7D18982
     * 0095 Compressed Length     00000007
     * 0099 Uncompressed Length   FFFFFFFF
     * [...] Omitted for brevity
     * 00AF Filename              'first'
     * 00B4 Extra ID #0001        0001 'ZIP64'
     * 00B6   Length              0008
     * 00B8   Uncompressed Size   0000000000000005
     *
     * @throws IOException if an error occurs creating the ZIP File
     */
    private static void createZipFile() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

            // The 'first' entry will store 'uncompressed size' in the Zip64 format
            ZipEntry e1 = new ZipEntry("first");

            // Make an extra field with the correct size for an 8-byte 'uncompressed size'
            // Zip64 field. Temporarily use the 'unknown' tag 0x9902 to make
            // ZipEntry.setExtra0 skip parsing this as a Zip64.
            // See APPNOTE.TXT, 4.6.1 Third Party Mappings
            byte[] opaqueExtra = createBlankExtra((short) UNKNOWN_TAG, (short) Long.BYTES);
            e1.setExtra(opaqueExtra);

            zos.putNextEntry(e1);
            zos.write(CONTENT);

            // A second entry, not in Zip64 format
            ZipEntry e2 = new ZipEntry("second");
            zos.putNextEntry(e2);
            zos.write(CONTENT);
        }

        byte[] zip = baos.toByteArray();

        // Update the CEN of 'first' to use the Zip64 format
        updateCENHeaderToZip64(zip);
        Files.write(ZIP_FILE, zip);
    }

    /**
     * Update the CEN entry of the "first" entry to use ZIP64 format for the
     * 'uncompressed size' field. The updated extra field will have the following
     * structure:
     *
     * 00B4 Extra ID #0001        0001 'ZIP64'
     * 00B6   Length              0008
     * 00B8   Uncompressed Size   0000000000000005
     *
     * @param zip the ZIP file to update to ZIP64
     */
    private static void updateCENHeaderToZip64(byte[] zip) {
        ByteBuffer buffer = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        // Find the offset of the first CEN header
        int cenOffset = buffer.getInt(zip.length- ZipFile.ENDHDR + ZipFile.ENDOFF);
        // Find the offset of the extra field
        int nlen = buffer.getShort(cenOffset + ZipFile.CENNAM);
        int extraOffset = cenOffset + ZipFile.CENHDR + nlen;

        // Change the header ID from 'unknown' to ZIP64
        buffer.putShort(extraOffset, ZIP64_TAG);
        // Update the 'uncompressed size' ZIP64 value to the actual uncompressed length
        int fieldOffset = extraOffset
                + Short.BYTES // TAG
                + Short.BYTES; // data size
        buffer.putLong(fieldOffset, CONTENT.length);

        // Set the 'uncompressed size' field of the CEN to 0xFFFFFFFF
        buffer.putInt(cenOffset + ZipFile.CENLEN, ZIP64_MAGIC_VALUE);
    }

    /**
     * Create an extra field with the given tag and data block size, and a
     * blank data block.
     * @return an extra field with the specified tag and size
     * @param tag the header id of the extra field
     * @param blockSize the size of the extra field's data block
     */
    private static byte[] createBlankExtra(short tag, short blockSize) {
        int size = Short.BYTES  // tag
                + Short.BYTES   // data block size
                + blockSize;   // data block;

        byte[] extra = new byte[size];
        ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(0, tag)
                .putShort(Short.BYTES, blockSize);
        return extra;
    }

    /**
     * Make sure the needed test files do not exist prior to executing the test
     * @throws IOException
     */
    @BeforeEach
    public void setUp() throws IOException {
        deleteFiles();
    }

    /**
     * Remove the files created for the test
     * @throws IOException
     */
    @AfterEach
    public void tearDown() throws IOException {
        deleteFiles();
    }

    /**
     * Delete the files created for use by the test
     * @throws IOException if an error occurs deleting the files
     */
    private static void deleteFiles() throws IOException {
        Files.deleteIfExists(ZIP_FILE);
    }
}
