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
 * Retrieves the array of offsets once with MemoryMapImage enabled once disabled.
 * @test ImageAttributeOffsetsTest
 * @summary Unit test for JVM_ImageAttributeOffsets() method
 * @library /testlibrary /../../test/lib
 * @build ImageAttributeOffsetsTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+MemoryMapImage ImageAttributeOffsetsTest
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:-MemoryMapImage ImageAttributeOffsetsTest
 */

import java.io.File;
import java.nio.ByteOrder;
import sun.hotspot.WhiteBox;
import static jdk.test.lib.Asserts.*;

public class ImageAttributeOffsetsTest {

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
        boolean passed = true;
        // Get offsets
        int[] array = wb.imageAttributeOffsets(id);
        assertNotNull(array, "Could not retrieve offsets of array");

        wb.imageCloseImage(id);
    }
}
