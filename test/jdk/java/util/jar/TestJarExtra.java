/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * field of the first entry of JAR files. Jar file specific.
 * @run junit TestJarExtra
 */

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Tests that the get/set operations on extra data in jar files work as advertised.
public class TestJarExtra extends TestExtra {

    @Test
    void jarExtraHeaderPlusDataTest() throws IOException {
        testHeaderPlusData();
    }

    @Test
    void jarExtraHeaderOnlyTest() throws IOException {
        testHeaderOnly();
    }

    @Test
    void jarExtraClientJarMagicTest() throws IOException {
        testClientJarMagic();
    }

    // Test that a header only (i.e., no extra "data") set by client works.
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

    // Tests the client providing extra data which uses JAR_MAGIC header.
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

    @Override
    ZipOutputStream getOutputStream(ByteArrayOutputStream baos) throws IOException {
        return new JarOutputStream(baos);
    }

    @Override
    ZipInputStream getInputStream(ByteArrayInputStream bais) throws IOException {
        return new JarInputStream(bais);
    }

    @Override
    ZipEntry getEntry() {
        return new ZipEntry("jar" + count++ + ".txt");
    }

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
