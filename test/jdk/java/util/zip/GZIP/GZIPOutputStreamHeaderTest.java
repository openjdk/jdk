/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPHeaderBuilder;

/**
 * @test
 * @bug 8244706
 * @summary Verify that the OS header flag in the stream written out by java.util.zip.GZIPOutputStream
 * has the correct expected value
 * @run testng GZIPOutputStreamHeaderTest
 */
public class GZIPOutputStreamHeaderTest {

    private static final int FLAGS_HEADER_INDEX = 3;
    private static final int OS_HEADER_INDEX = 9;
    private static final int HEADER_VALUE_OS_UNKNOWN = 255;
    // flags for test
    private static final int FHCRC      = 2;    // Header CRC
    private static final int FEXTRA     = 4;    // Extra field
    private static final int FNAME      = 8;    // File name
    private static final int FCOMMENT   = 16;   // File comment

    /**
     * Test that the {@code OS} header field in the GZIP output stream
     * has a value of {@code 255} which represents "unknown"
     */
    @Test
    public void testOSHeaderWithDefaultMembers() throws Exception {
        final String data = "Hello world!!!";
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(baos);) {
            gzipOutputStream.write(data.getBytes(StandardCharsets.UTF_8));
        }
        final byte[] compressed = baos.toByteArray();
        Assert.assertNotNull(compressed, "Compressed data is null");
        Assert.assertEquals(toUnsignedByte(compressed[OS_HEADER_INDEX]), HEADER_VALUE_OS_UNKNOWN,
                "Unexpected value for OS header");
        // finally verify that the compressed data is readable back to the original
        final String uncompressed;
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream();
             final ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
             final GZIPInputStream gzipInputStream = new GZIPInputStream(bis)) {
            gzipInputStream.transferTo(os);
            uncompressed = new String(os.toByteArray(), StandardCharsets.UTF_8);
        }
        Assert.assertEquals(uncompressed, data, "Unexpected data read from GZIPInputStream");
    }

    /**
     * Test that the {@code OS} header field in the GZIP output stream
     * has a value of {@code 255} which represents "unknown", and test
     * that the flags and header crc16 field could be successfully set.
     */
    @Test
    public void testOSHeaderWithField() throws Exception {
        final String data = "Hello world!!!";
        // header fields
        boolean generateHeaderCrc = true;
        /* extra field
         * per RFC 1952:
         * If the FLG.FEXTRA bit is set, an "extra field" is present in
         * the header, with total length XLEN bytes.  It consists of a
         * series of subfields, each of the form:
         *
         *   +---+---+---+---+==================================+
         *   |SI1|SI2|  LEN  |... LEN bytes of subfield data ...|
         *   +---+---+---+---+==================================+
         *
         * SI1 and SI2 provide a subfield ID, typically two ASCII letters
         * with some mnemonic value.
         *
         * Subfield
         * IDs with SI2 = 0 are reserved for future use.  The following
         * IDs are currently defined:
         *
         *   SI1         SI2         Data
         *   ----------  ----------  ----
         *   0x41 ('A')  0x70 ('P')  Apollo file type information
         */
        ByteArrayOutputStream fieldStream = new ByteArrayOutputStream();
        fieldStream.write("AP".getBytes());
        String xFieldData = "Apollo file type information";
        int len = xFieldData.length();
        fieldStream.write((byte)(len & 0xff));
        fieldStream.write((byte) (len >> 8) & 0xff);
        fieldStream.write(xFieldData.getBytes());
        byte[] xfield = fieldStream.toByteArray();
        // file name
        String fname = "FileNameTest.tmp";
        // file comment
        String fcomment = "FileCommentTest";
        CRC32 crc = new CRC32();
        crc.reset();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPHeaderBuilder.GZIPHeaderData headerData = new GZIPHeaderBuilder()
                                                            .withExtraFieldBytes(xfield)
                                                            .withFileName(fname)
                                                            .withFileComment(fcomment)
                                                            .calculateHeaderCRC(generateHeaderCrc)
                                                            .build();
        try (final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(baos, headerData);) {
            gzipOutputStream.write(data.getBytes(StandardCharsets.UTF_8));
        }
        final byte[] compressed = baos.toByteArray();
        Assert.assertNotNull(compressed, "Compressed data is null");
        Assert.assertEquals(toUnsignedByte(compressed[OS_HEADER_INDEX]), HEADER_VALUE_OS_UNKNOWN,
                "Unexpected value for OS header");
        // test header flags
        byte expectedFlags = (byte) ((FHCRC | FEXTRA | FNAME | FCOMMENT) & 0xff);
        Assert.assertEquals(toUnsignedByte(compressed[FLAGS_HEADER_INDEX]), expectedFlags,
                "Unexpected value for header flags");
        // test extra field
        int index = OS_HEADER_INDEX + 1;
        int lo = toUnsignedByte(compressed[index++]);
        int hi = toUnsignedByte(compressed[index++]) << 8;
        int fieldLen = hi | lo;
        int expectedFieldLen = xfield.length;
        String fieldStr = new String(compressed, index, expectedFieldLen);
        Assert.assertEquals(fieldLen, expectedFieldLen, "Unexpected length of extra field");
        Assert.assertEquals(fieldStr, new String(xfield), "Unexpected extra field contents");
        index += expectedFieldLen;

        // test file name
        int fnameLen = fname.length();
        String fn = new String(compressed, index, fnameLen);
        Assert.assertEquals(fn, new String(fname), "Unexpected file name in header");
        index += fnameLen;
        Assert.assertEquals(compressed[index++], 0, "File name in header must be end with 0");

        // test file comment
        int fcommLen = fcomment.length();
        String fc = new String(compressed, index, fcommLen);
        Assert.assertEquals(fc, new String(fcomment), "Unexpected file name in header");
        index += fcommLen;
        Assert.assertEquals(compressed[index++], 0, "File comment in header must be end with 0");

        // test crc of header (lower 16bits)
        crc.update(compressed, 0, index);
        int expected = (int)(crc.getValue()) & 0xffff;
        int low = toUnsignedByte(compressed[index++]);
        int high = toUnsignedByte(compressed[index++]) << 8;
        int headerCrc = high | low;
        Assert.assertEquals(headerCrc, expected, "Unexpected CRC value of header");

        // finally verify that the compressed data is readable back to the original
        final String uncompressed;
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream();
             final ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
             final GZIPInputStream gzipInputStream = new GZIPInputStream(bis)) {
            gzipInputStream.transferTo(os);
            uncompressed = new String(os.toByteArray(), StandardCharsets.UTF_8);
        }
        Assert.assertEquals(uncompressed, data, "Unexpected data read from GZIPInputStream");
    }

    private static int toUnsignedByte(final byte b) {
        return b & 0xff;
    }
}
