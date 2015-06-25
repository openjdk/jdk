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
 * Test reading resource content.
 * @test ImageReadTest
 * @summary Unit test for JVM_ImageRead() method
 * @library /testlibrary /../../test/lib
 * @build LocationConstants ImageReadTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+MemoryMapImage ImageReadTest +
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:-MemoryMapImage ImageReadTest -
 */

import java.io.File;
import java.nio.ByteBuffer;
import sun.hotspot.WhiteBox;
import static jdk.test.lib.Asserts.*;

public class ImageReadTest implements LocationConstants {

    public static final WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String... args) throws Exception {
        String javaHome = System.getProperty("java.home");
        String imageFile = javaHome + File.separator + "lib"
                + File.separator + "modules" + File.separator
                + "bootmodules.jimage";

        if (!(new File(imageFile)).exists()) {
            System.out.printf("Test skipped.");
            return;
        }

        boolean isMMap = args[0].equals("+");

        long id = wb.imageOpenImage(imageFile, isMMap);

        final String mm = isMMap ? "-XX:+MemoryMapImage" : "-XX:-MemoryMapImage";
        final int magic = 0xCAFEBABE;

        String className = "/java.base/java/lang/String.class";
        long[] offsetArr = wb.imageFindAttributes(id, className.getBytes());
        long offset = offsetArr[LOCATION_ATTRIBUTE_OFFSET];
        long size = offsetArr[LOCATION_ATTRIBUTE_UNCOMPRESSED];

        // positive: read
        ByteBuffer buf = ByteBuffer.allocateDirect((int) size);
        assertTrue(wb.imageRead(id, offset, buf, size), "Failed. Read operation returned false, should be true");
        int m = buf.getInt();
        assertTrue(m == magic, "Failed. Read operation returned true but wrong magic = " + magic);

        // positive: mmap
        if (isMMap) {
            long dataAddr = wb.imageGetDataAddress(id);
            assertFalse(dataAddr == 0L, "Failed. Did not obtain data address on mmapped test");
            int data = wb.imageGetIntAtAddress(dataAddr, (int) offset, true);
            assertTrue(data == magic, "Failed. MMap operation returned true but wrong magic = " + data);
        }

        // negative: wrong offset
        boolean success = wb.imageRead(id, -100, buf, size);
        assertFalse(success, "Failed. Read operation (wrong offset): returned true");

        // negative: too big offset
        long filesize = new File(imageFile).length();
        success = wb.imageRead(id, filesize + 1, buf, size);
        assertFalse(success, "Failed. Read operation (offset > file size) returned true");

        // negative: negative size
        success = wb.imageRead(id, offset, buf, -100);
        assertFalse(success, "Failed. Read operation (negative size) returned true");

        wb.imageCloseImage(id);
    }
}
