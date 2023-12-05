/*
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
 * @bug 8321396
 * @summary Verify that ZipInputStream ignores non-zero, incorrect 'crc',
 * 'compressed size' and 'uncompressed size' values when in streaming mode.
 * @run junit DataDescriptorIgnoreCrcAndSizeFields
 */

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

public class DataDescriptorIgnoreCrcAndSizeFields {

    /**
     * Verify that ZipInputStream correctly ignores values from a LOC header's
     * 'crc', 'compressed size' and 'uncompressed size' fields, when in
     * streaming mode and these fields are incorrectly set to non-zero values.
     */
    @Test
    public void shouldIgnoreCrcAndSizeValuesInStreamingMode() throws IOException {
        // ZIP with incorrect 'CRC', 'compressed size' and 'uncompressed size' values
        byte[] zip = zipWithIncorrectCrcAndSizeValuesInLocalHeader();

        // ZipInputStream should ignore the incorrect field values
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry first = in.getNextEntry();
            assertNotNull(first, "Zip file is unexpectedly missing first entry");

            // CRC, compressed size and size should be uninitialized at this point
            assertCrcAndSize(first, -1, -1, -1);
            // Check that name and contents is as expected
            assertNameAndContents("first", first, in);
            // At this point, ZipInputStream should have read correct values from the data descriptor
            assertCrcAndSize(first, crc32("first"), compressedSize("first"), uncompressedSize("first"));

            // For extra caution, also read and validate the second entry
            ZipEntry second = in.getNextEntry();
            assertNotNull(second, "Zip file is unexpectedly missing second entry");

            // CRC, compressed size and size should be uninitialized at this point
            assertCrcAndSize(second, -1, -1, -1);
            // Check that name and contents is as expected
            assertNameAndContents("second", second, in);
            // At this point, ZipInputStream should have read correct values from the data descriptor
            assertCrcAndSize(second, crc32("second"), compressedSize("second"), uncompressedSize("second"));
        }

    }

    /**
     * Assert that the given ZipEntry has the expected name and that
     * the expected content can be read from the ZipInputStream
     * @param expected the expected name and content
     * @param entry the entry to check the name of
     * @param in the ZipInputStream to check the entry content of
     * @throws IOException if an IO exception occurs
     */
    private static void assertNameAndContents(String expected, ZipEntry entry, ZipInputStream in) throws IOException {
        assertEquals(expected, entry.getName());
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), in.readAllBytes());
    }

    /**
     * Assert that a ZipEntry has the expected CRC-32, compressed size and uncompressed size values
     * @param entry the ZipEntry to validate
     * @param expectedCrc the expected CRC-32 value
     * @param expectedCompressedSize the exprected compressed size value
     * @param expectedSize the expected size value
     */
    private static void assertCrcAndSize(ZipEntry entry, long expectedCrc, long expectedCompressedSize, long expectedSize) {
        assertEquals(expectedCrc, entry.getCrc());
        assertEquals(expectedCompressedSize, entry.getCompressedSize());
        assertEquals(expectedSize, entry.getSize());
    }

    /**
     * Return the CRC-32 value for the given string encoded in UTF-8
     * @param content the string to produce a CRC-32 checksum for
     * @return the CRC-value of the encoded string
     */
    private long crc32(String content) {
        CRC32 crc32 = new CRC32();
        crc32.update(content.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }

    /**
     * Return the length of the given content encoded in UTF-8
     * @param content the content to return the encoded length for
     * @return the uncompressed size of the encoded content
     */
    private long uncompressedSize(String content) {
        return content.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Returns the size of the given content, as if it was encoded in UTF-8 and then deflated
     * @param content the content to get the compressed size of
     * @return the compressed size of the content
     * @throws IOException if an IO exception occurs
     */
    private long compressedSize(String content) throws IOException {
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        try (OutputStream o = new DeflaterOutputStream(bao, new Deflater(Deflater.DEFAULT_COMPRESSION, true))) {
            o.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return bao.size();
    }

    /**
     * When a ZIP entry is created in 'streaming' mode, the 'general purpose bit flag' 3
     * is set, and the fields crc-32, compressed size and uncompressed size are set to
     * zero in the local header.
     *
     * Certain legacy ZIP tools incorrectly set non-zero values for one or more of these
     * three fields when in streaming mode.
     *
     * This method creates a ZIP where the first entry has a local header where the
     * mentioned fields are set to a non-zero, incorrect values. The second entry
     * has the correct zero values for these fields.
     */
    private static byte[] zipWithIncorrectCrcAndSizeValuesInLocalHeader() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zo = new ZipOutputStream(out)) {
            // Write a first entry
            zo.putNextEntry(new ZipEntry("first"));
            zo.write("first".getBytes(StandardCharsets.UTF_8));
            // Add a second entry
            zo.putNextEntry(new ZipEntry("second"));
            zo.write("second".getBytes(StandardCharsets.UTF_8));
        }

        // ZipOutputStream correctly produces local headers with zero crc and sizes values
        byte[] zip = out.toByteArray();

        // Buffer for updating the local header values
        ByteBuffer buffer = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        // Set the CRC-32 field to an incorrect value
        buffer.putShort(ZipEntry.LOCCRC, (short) 42);
        // Set the compressed size to an incorrect value
        buffer.putShort(ZipEntry.LOCSIZ, (short) 42);
        // Set the uncompressed size to an incorrect value
        buffer.putShort(ZipEntry.LOCLEN, (short) 42);

        return zip;
    }
}
