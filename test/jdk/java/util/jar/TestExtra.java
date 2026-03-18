/*
 * Copyright (c) 2006, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 6480504 6303183
 * @summary Test that client-provided data in the extra field is written and
 * read correctly, taking into account the JAR_MAGIC written into the extra
 * field of the first entry of JAR files. ZIP file specific.
 * @run junit TestExtra
 */

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.jar.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Tests that the get/set operations on extra data in ZIP files work as advertised.
public class TestExtra {

    static final int TEST_HEADER = 0xbabe;
    static final Charset ascii = StandardCharsets.US_ASCII;

    // ZipEntry extra data
    static final byte[][] extra = new byte[][] {
            ascii.encode("hello, world").array(),
            ascii.encode("foo bar").array()
    };

    // For naming entries in ZIP streams
    static int count = 1;

    // Use byte arrays instead of files
    ByteArrayOutputStream baos  = new ByteArrayOutputStream();

    // ZIP content written here.
    ZipOutputStream zos = assertDoesNotThrow(() -> getOutputStream(baos));

    // Test that a header + data set by client works.
    @Test
    void testHeaderPlusData() throws IOException {
        for (byte[] b : extra) {
            ZipEntry ze = getEntry();
            byte[] data = new byte[b.length + 4];
            set16(data, 0, TEST_HEADER);
            set16(data, 2, b.length);
            System.arraycopy(b, 0, data, 4, b.length);
            ze.setExtra(data);
            zos.putNextEntry(ze);
        }
        zos.close();
        ZipInputStream zis = getInputStream();
        ZipEntry ze = zis.getNextEntry();
        checkEntry(ze, 0, extra[0].length);
        ze = zis.getNextEntry();
        checkEntry(ze, 1, extra[1].length);
    }

    // Check that the entry's extra data is correct.
    void checkEntry(ZipEntry ze, int count, int dataLength) {
        byte[] extraData = ze.getExtra();
        byte[] data = getField(TEST_HEADER, extraData);
        assertNotNull(data, "unexpected null data for TEST_HEADER");
        if (dataLength == 0) {
            assertEquals(0, data.length, "unexpected non-zero data length for TEST_HEADER");
        } else {
            assertArrayEquals(data, extra[count],
                    "failed to get entry " + ze.getName()
                            + ", expected " + new String(extra[count]) + ", got '" + new String(data) + "'");
        }
    }

    // Look up descriptor in data, returning corresponding byte[].
    static byte[] getField(int descriptor, byte[] data) {
        byte[] rc = null;
        try {
            int i = 0;
            while (i < data.length) {
                if (get16(data, i) == descriptor) {
                    int length = get16(data, i + 2);
                    rc = new byte[length];
                    for (int j = 0; j < length; j++) {
                        rc[j] = data[i + 4 + j];
                    }
                    return rc;
                }
                i += get16(data, i + 2) + 4;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // descriptor not found
        }
        return rc;
    }

    ZipInputStream getInputStream() {
        return new ZipInputStream(
                new ByteArrayInputStream(baos.toByteArray()));
    }

    ZipOutputStream getOutputStream(ByteArrayOutputStream baos) {
        return new ZipOutputStream(baos);
    }

    ZipEntry getEntry() {
        return new ZipEntry("zip" + count++ + ".txt");
    }

    static int get16(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }

    static void set16(byte[] b, int off, int value) {
        b[off + 0] = (byte) value;
        b[off + 1] = (byte) (value >> 8);
    }
}
