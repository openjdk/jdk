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
 * Test that the string referenced by an attribute is retrieved.
 * @test ImageGetStringBytesTest
 * @summary Unit test for JVM_ImageGetStringBytes() method
 * @library /testlibrary /../../test/lib
 * @build LocationConstants ImageGetStringBytesTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI ImageGetStringBytesTest
 */

import java.io.File;
import java.nio.ByteOrder;
import sun.hotspot.WhiteBox;
import static jdk.test.lib.Asserts.*;

public class ImageGetStringBytesTest implements LocationConstants {

    public static final WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String... args) throws Exception {
        String javaHome = System.getProperty("java.home");
        String imageFile = javaHome + File.separator + "lib" + File.separator
                + "modules" + File.separator + "bootmodules.jimage";

        if (!(new File(imageFile)).exists()) {
            System.out.printf("Test skipped.");
            return;
        }

        boolean bigEndian = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
        long id = wb.imageOpenImage(imageFile, bigEndian);

        String className = "/java.base/java/lang/String.class";
        long[] offsetArr = wb.imageFindAttributes(id, className.getBytes());

        // Module
        assertTrue(checkAttribute(id, offsetArr, LOCATION_ATTRIBUTE_MODULE, "Module"));

        // Parent
        assertTrue(checkAttribute(id, offsetArr, LOCATION_ATTRIBUTE_PARENT, "Parent"));

        // Base
        assertTrue(checkAttribute(id, offsetArr, LOCATION_ATTRIBUTE_BASE, "Base"));

        // Extension
        assertTrue(checkAttribute(id, offsetArr, LOCATION_ATTRIBUTE_EXTENSION, "Extension"));

        wb.imageCloseImage(id);
    }

    private static boolean checkAttribute(long id, long[] offsetArr, int attrId, String attrName) {
        long offset = offsetArr[attrId];
        return wb.imageGetStringBytes(id, (int) offset) != null;
    }
}
