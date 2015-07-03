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
 * Test getting all attributes,
 * @test ImageGetAttributesTest
 * @summary Unit test for JVM_ImageGetAttributes() method
 * @library /testlibrary /../../test/lib
 * @build LocationConstants ImageGetAttributesTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI ImageGetAttributesTest
 */

import java.io.File;
import java.nio.ByteOrder;
import sun.hotspot.WhiteBox;
import static jdk.test.lib.Asserts.*;

public class ImageGetAttributesTest implements LocationConstants {

    public static final WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String... args) throws Exception {
        String javaHome = System.getProperty("java.home");
        String imageFile = javaHome + File.separator + "lib" + File.separator
                + "modules" + File.separator + "bootmodules.jimage";

        if (!(new File(imageFile)).exists()) {
            System.out.printf("Test skipped.");
            return;
        }

        testImageGetAttributes(imageFile);
    }

    private static void testImageGetAttributes(String imageFile) {

        boolean bigEndian = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
        long id = wb.imageOpenImage(imageFile, bigEndian);
        try {
            long stringsSize = wb.imageGetStringsSize(id);
            assertNE(stringsSize, 0, "strings size is 0");

            int[] array = wb.imageAttributeOffsets(id);
            assertNotNull(array, "Could not retrieve offsets of array");

            // Get non-null attributes
            boolean attFound = false;
            int[] idx = {-1, -1, -1};
            // first non-null attribute
            for (int i = 0; i < array.length; i++) {
                if (array[i] != 0) {
                    attFound = true;
                    idx[0] = i;
                    break;
                }
            }

            // middle non-null attribute
            for (int i = array.length / 2; i < array.length; i++) {
                if (array[i] != 0) {
                    attFound = true;
                    idx[1] = i;
                    break;
                }
            }

            // last non-null attribute
            for (int i = array.length - 1; i >= 0; i--) {
                if (array[i] != 0) {
                    attFound = true;
                    idx[2] = i;
                    break;
                }
            }
            assertTrue(attFound, "Failed. No non-null offset attributes");
                // test cases above
                for (int i = 0; i < 3; i++) {
                    if (idx[i] != -1) {
                        long[] attrs = wb.imageGetAttributes(id, (int) array[idx[i]]);
                        long module = attrs[LOCATION_ATTRIBUTE_MODULE];
                        long parent = attrs[LOCATION_ATTRIBUTE_PARENT];
                        long base = attrs[LOCATION_ATTRIBUTE_BASE];
                        long ext = attrs[LOCATION_ATTRIBUTE_EXTENSION];

                        if ((module >= 0) && (module < stringsSize)
                                && (parent >= 0) && (parent < stringsSize)
                                && (base != 0)
                                && (ext >= 0) && (ext < stringsSize)) {
                        } else {
                            System.out.printf("Failed. Read attribute offset %d (position %d) but wrong offsets\n",
                                    array[idx[i]], idx[i]);
                            System.out.printf("    offsets: module = %d parent = %d base = %d extention = %d\n",
                                    module, parent, base, ext);
                            throw new RuntimeException("Read attribute offset error");
                        }
                    } else {
                        System.out.printf("Failed. Could not read attribute offset %d (position %d)\n",
                                array[idx[i]], idx[i]);
                        throw new RuntimeException("Read attribute offset error");
                    }
                }
        } finally {
            wb.imageCloseImage(id);
        }
    }
}
