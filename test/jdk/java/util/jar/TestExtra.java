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
 * field of the first entry of JAR files.
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
import static org.junit.jupiter.api.Assertions.fail;

/*
 * Tests that the get/set operations on extra data in zip and jar files work
 * as advertised.  The base class tests ZIP files, the member class
 * TestJarExtra checks JAR files.
 */
public class TestExtra {

    static final int JAR_MAGIC = 0xcafe; // private IN JarOutputStream.java
    static final int TEST_HEADER = 0xbabe;

    static final Charset ascii = StandardCharsets.US_ASCII;

    // ZipEntry extra data
    static final byte[][] extra = new byte[][] {
        ascii.encode("hello, world").array(),
        ascii.encode("foo bar").array()
    };

    // For naming entries in JAR/ZIP streams
    static int count = 1;

    // Instance variables safely reset per test method under JUnit default lifecycle

    // Use byte arrays instead of files
    ByteArrayOutputStream baos;

    // JAR/ZIP content written here.
    ZipOutputStream zos;

    @Test
    void extraHeaderPlusDataTest() throws IOException {
        new TestExtra().testHeaderPlusData();
    }

    @Test
    void jarExtraHeaderPlusDataTest() throws IOException {
        new TestJarExtra().testHeaderPlusData();
    }

    @Test
    void jarExtraHeaderOnlyTest() throws IOException {
        new TestJarExtra().testHeaderOnly();
    }

    @Test
    void jarExtraClientJarMagicTest() throws IOException {
        new TestJarExtra().testClientJarMagic();
    }

    TestExtra() {
        baos = new ByteArrayOutputStream();
        zos = assertDoesNotThrow(() -> getOutputStream(baos));
    }

    /** Test that a header + data set by client works. */
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

    /** Test that a header only (i.e., no extra "data") set by client works. */
    void testHeaderOnly() throws IOException {
        ZipEntry ze = getEntry();
        byte[] data = new byte[4];
        set16(data, 0, TEST_HEADER);
        set16(data, 2, 0); // Length of data is 0.
        ze.setExtra(data);
        zos.putNextEntry(ze);

        zos.close();

        ZipInputStream zis = getInputStream();

        ze = zis.getNextEntry();
        checkExtra(data, ze.getExtra());
        checkEntry(ze, 0, 0);
    }

    /** Tests the client providing extra data which uses JAR_MAGIC header. */
    void testClientJarMagic() throws IOException {
        ZipEntry ze = getEntry();
        byte[] data = new byte[8];

        set16(data, 0, TEST_HEADER);
        set16(data, 2, 0); // Length of data is 0.
        set16(data, 4, JAR_MAGIC);
        set16(data, 6, 0); // Length of data is 0.

        ze.setExtra(data);
        zos.putNextEntry(ze);

        zos.close();

        ZipInputStream zis = getInputStream();
        ze = zis.getNextEntry();
        byte[] e = ze.getExtra();
        checkExtra(data, ze.getExtra());
        checkEntry(ze, 0, 0);
    }

    // check if all "expected" extra fields equal to their
    // corresponding fields in "extra". The "extra" might have
    // timestamp fields added by ZOS.
    static void checkExtra(byte[] expected, byte[] extra) {
        if (expected == null)
            return;
        int off = 0;
        int len = expected.length;
        while (off + 4 < len) {
            int tag = get16(expected, off);
            int sz = get16(expected, off + 2);
            int off0 = 0;
            int len0 = extra.length;
            boolean matched = false;
            while (off0 + 4 < len0) {
                int tag0 = get16(extra, off0);
                int sz0 = get16(extra, off0 + 2);
                if (tag == tag0 && sz == sz0) {
                    matched = true;
                    for (int i = 0; i < sz; i++) {
                        if (expected[off + i] != extra[off0 +i])
                            matched = false;
                    }
                    break;
                }
                off0 += (4 + sz0);
            }
            if (!matched) {
                fail("Expected extra data [tag=" + tag + "sz=" + sz + "] check failed");
            }
            off += (4 + sz);
        }
    }

    /** Check that the entry's extra data is correct. */
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

    /** Look up descriptor in data, returning corresponding byte[]. */
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

    ZipOutputStream getOutputStream(ByteArrayOutputStream baos) throws IOException {
        return new ZipOutputStream(baos);
    }

    ZipInputStream getInputStream(ByteArrayInputStream bais) throws IOException {
        return new ZipInputStream(bais);
    }

    ZipEntry getEntry() { return new ZipEntry("zip" + count++ + ".txt"); }


    private static int get16(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off+1] & 0xff) << 8);
    }

    private static void set16(byte[] b, int off, int value) {
        b[off+0] = (byte)value;
        b[off+1] = (byte)(value >> 8);
    }

    /** Test extra field of a JAR file. */
    static class TestJarExtra extends TestExtra {
        ZipOutputStream getOutputStream(ByteArrayOutputStream baos) throws IOException {
            return new JarOutputStream(baos);
        }

        ZipInputStream getInputStream(ByteArrayInputStream bais) throws IOException {
            return new JarInputStream(bais);
        }

        ZipEntry getEntry() { return new ZipEntry("jar" + count++ + ".txt"); }

        void checkEntry(ZipEntry ze, int count, int dataLength) {
            // zeroth entry should have JAR_MAGIC
            if (count == 0) {
                byte[] extraData = ze.getExtra();
                byte[] data = getField(JAR_MAGIC, extraData);
                assertNotNull(data, "unexpected null data for JAR_MAGIC");
                assertEquals(0, data.length, "unexpected non-zero data length for JAR_MAGIC");
            }
            // In a jar file, the first ZipEntry should have both JAR_MAGIC
            // and the TEST_HEADER, so check that also.
            super.checkEntry(ze, count, dataLength);
        }
    }
}
