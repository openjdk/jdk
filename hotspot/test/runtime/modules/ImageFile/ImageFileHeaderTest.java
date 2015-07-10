/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * Test that opening image containing wrong headers fails.
 * @test ImageFileHeaderTest
 * @library /testlibrary /../../test/lib
 * @build ImageFileHeaderTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI ImageFileHeaderTest
 */

import java.nio.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import sun.hotspot.WhiteBox;
import static jdk.test.lib.Asserts.*;

public class ImageFileHeaderTest {

    public static final int MAGIC = 0xCAFEDADA;
    public static final short MAJOR = 0;
    public static final short MINOR = 1;

    public static final WhiteBox wb = WhiteBox.getWhiteBox();
    public static ByteBuffer buf;

    public static void main(String... args) throws Exception {

        ByteOrder endian = getEndian();

        // Try to read a non-existing file
        assertFalse(wb.readImageFile("bogus"));

        // Incomplete header, only include the correct magic
        buf = ByteBuffer.allocate(100);
        buf.order(endian);
        buf.putInt(MAGIC);
        assertFalse(testImageFile("invalidheader.jimage"));

        // Build a complete header but reverse the endian
        buf = ByteBuffer.allocate(100);
        buf.order(endian == ByteOrder.LITTLE_ENDIAN ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        buf.putInt(MAGIC);
        buf.putShort(MAJOR);
        buf.putShort(MINOR);
        assertFalse(testImageFile("wrongendian.jimage"));

        // Use the wrong magic
        buf = ByteBuffer.allocate(100);
        buf.order(endian);
        buf.putInt(0xBEEFCACE);
        buf.putShort(MAJOR);
        buf.putShort(MINOR);
        assertFalse(testImageFile("wrongmagic.jimage"));

        // Wrong major version (current + 1)
        buf = ByteBuffer.allocate(100);
        buf.order(endian);
        buf.putInt(MAGIC);
        buf.putShort((short)(MAJOR + 1));
        buf.putShort((short)MINOR);
        assertFalse(testImageFile("wrongmajorversion.jimage"));

        // Wrong major version (negative)
        buf = ByteBuffer.allocate(100);
        buf.order(endian);
        buf.putInt(MAGIC);
        buf.putShort((short) -17);
        buf.putShort((short)MINOR);
        assertFalse(testImageFile("negativemajorversion.jimage"));

        // Wrong minor version (current + 1)
        buf = ByteBuffer.allocate(100);
        buf.order(endian);
        buf.putInt(MAGIC);
        buf.putShort((short)MAJOR);
        buf.putShort((short)(MINOR + 1));
        assertFalse(testImageFile("wrongminorversion.jimage"));

        // Wrong minor version (negative)
        buf = ByteBuffer.allocate(100);
        buf.order(endian);
        buf.putInt(MAGIC);
        buf.putShort((short)MAJOR);
        buf.putShort((short) -17);
        assertFalse(testImageFile("negativeminorversion.jimage"));
    }

    public static boolean testImageFile(String filename) throws Exception {
        Files.write(Paths.get(filename), buf.array());
        System.out.println("Calling ReadImageFile on " + filename);
        return wb.readImageFile(filename);
    }

    public static ByteOrder getEndian() {
        String endian = System.getProperty("sun.cpu.endian");
        if (endian.equalsIgnoreCase("little")) {
            return ByteOrder.LITTLE_ENDIAN;
        } else if (endian.equalsIgnoreCase("big")) {
            return ByteOrder.BIG_ENDIAN;
        }
        throw new RuntimeException("Unexpected sun.cpu.endian value: " + endian);
    }
}
