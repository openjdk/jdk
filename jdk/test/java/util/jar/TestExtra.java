/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6480504
 * @summary Test that client-provided data in the extra field is written and
 * read correctly, taking into account the JAR_MAGIC written into the extra
 * field of the first entry of JAR files.
 * @author Dave Bristor
 */

import java.io.*;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Tests that the get/set operations on extra data in zip and jar files work
 * as advertised.  The base class tests ZIP files, the member class
 * TestJarExtra checks JAR files.
 */
public class TestExtra {
    final static int JAR_MAGIC = 0xcafe; // private IN JarOutputStream.java
    final static int TEST_HEADER = 0xbabe;

    final static Charset ascii = Charset.forName("ASCII");

    // ZipEntry extra data
    final static byte[][] extra = new byte[][] {
        ascii.encode("hello, world").array(),
        ascii.encode("foo bar").array()
    };

    // For naming entries in JAR/ZIP streams
    int count = 1;

    // Use byte arrays instead of files
    ByteArrayOutputStream baos;

    // JAR/ZIP content written here.
    ZipOutputStream zos;

    public static void realMain(String[] args) throws Throwable{
        new TestExtra().testHeaderPlusData();

        new TestJarExtra().testHeaderPlusData();
        new TestJarExtra().testHeaderOnly();
        new TestJarExtra().testClientJarMagic();
    }

    TestExtra() {
        try {
            baos = new ByteArrayOutputStream();
            zos = getOutputStream(baos);
        } catch (Throwable t) {
            unexpected(t);
        }
    }

    /** Test that a header + data set by client works. */
    void testHeaderPlusData() throws IOException {
        for (byte[] b : extra) {
            ZipEntry ze = getEntry();
            byte[] data = new byte[b.length + 4];
            set16(data, 0, TEST_HEADER);
            set16(data, 2, b.length);
            for (int i = 0; i < b.length; i++) {
                data[i + 4] = b[i];
            }
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
        byte[] e = ze.getExtra();
        check(e.length == 8, "expected extra length is 8, got " + e.length);
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
        check(e.length == 8, "expected extra length is 8, got " + e.length);
        checkEntry(ze, 0, 0);
    }


    /** Check that the entry's extra data is correct. */
    void checkEntry(ZipEntry ze, int count, int dataLength) {
        byte[] extraData = ze.getExtra();
        byte[] data = getField(TEST_HEADER, extraData);
        if (!check(data != null, "unexpected null data for TEST_HEADER")) {
            return;
        }

        if (dataLength == 0) {
            check(data.length == 0, "unexpected non-zero data length for TEST_HEADER");
        } else {
            check(Arrays.equals(extra[count], data),
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
                if (!check(data != null, "unexpected null data for JAR_MAGIC")) {
                    check(data.length != 0, "unexpected non-zero data length for JAR_MAGIC");
                }
            }
            // In a jar file, the first ZipEntry should have both JAR_MAGIC
            // and the TEST_HEADER, so check that also.
            super.checkEntry(ze, count, dataLength);
        }
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() {passed++;}
    static void fail() {failed++; Thread.dumpStack();}
    static void fail(String msg) {System.out.println(msg); fail();}
    static void unexpected(Throwable t) {failed++; t.printStackTrace();}
    static void check(boolean cond) {if (cond) pass(); else fail();}
    static boolean check(boolean cond, String msg) {if (cond) pass(); else fail(msg); return cond; }
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        try {realMain(args);} catch (Throwable t) {unexpected(t);}
        System.out.println("\nPassed = " + passed + " failed = " + failed);
        if (failed > 0) throw new Error("Some tests failed");}
}
