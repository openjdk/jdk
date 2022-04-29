/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=default
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm BaseOffsets
 */
/*
 * @test id=no-coops
 * @library /test/lib
 * @requires vm.bits == "64"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -XX:-UseCompressedOops BaseOffsets
 */

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import jdk.internal.misc.Unsafe;

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;

public class BaseOffsets {

    static class LIClass {
        public int i;
    }

    // @0:  8 byte header,  @8: int field
    static final long INT_OFFSET  = 8L;

    static public void main(String[] args) {
        Unsafe unsafe = Unsafe.getUnsafe();
        Class c = LIClass.class;
        Field[] fields = c.getFields();
        for (int i = 0; i < fields.length; i++) {
            long offset = unsafe.objectFieldOffset(fields[i]);
            if (fields[i].getType() == int.class) {
                Asserts.assertEquals(offset, INT_OFFSET, "Misplaced int field");
            } else {
                Asserts.fail("Unexpected field type");
            }
        }

        Asserts.assertEquals(unsafe.arrayBaseOffset(boolean[].class), 12, "Misplaced boolean array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(byte[].class),    12, "Misplaced byte    array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(char[].class),    12, "Misplaced char    array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(short[].class),   12, "Misplaced short   array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(int[].class),     12, "Misplaced int     array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(long[].class),    16, "Misplaced long    array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(float[].class),   12, "Misplaced float   array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(double[].class),  16, "Misplaced double  array base");
        boolean narrowOops = System.getProperty("java.vm.compressedOopsMode") != null ||
                             !Platform.is64bit();
        int expected_objary_offset = narrowOops ? 12 : 16;
        Asserts.assertEquals(unsafe.arrayBaseOffset(Object[].class),  expected_objary_offset, "Misplaced object  array base");
    }
}
