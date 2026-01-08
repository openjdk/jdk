/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=with-coops-with-ccp
 * @library /test/lib /
 * @requires vm.bits == "64"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseCompressedOops -XX:+UseCompressedClassPointers -XX:-UseCompactObjectHeaders BaseOffsets
 */
/*
 * @test id=no-coops-with-ccp
 * @library /test/lib /
 * @requires vm.bits == "64"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:-UseCompressedOops -XX:+UseCompressedClassPointers -XX:-UseCompactObjectHeaders BaseOffsets
 */
/*
 * @test id=with-coops-no-ccp
 * @library /test/lib /
 * @requires vm.bits == "64"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseCompressedOops -XX:-UseCompressedClassPointers -XX:-UseCompactObjectHeaders BaseOffsets
 */
/*
 * @test id=no-coops-no-ccp
 * @library /test/lib /
 * @requires vm.bits == "64"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:-UseCompressedOops -XX:-UseCompressedClassPointers -XX:-UseCompactObjectHeaders BaseOffsets
 */
/*
 * @test id=with-coop--with-coh
 * @library /test/lib /
 * @requires vm.bits == "64"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseCompressedOops -XX:+UseCompactObjectHeaders BaseOffsets
 */
/*
 * @test id=no-coops-with-coh
 * @library /test/lib /
 * @requires vm.bits == "64"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:-UseCompressedOops -XX:+UseCompactObjectHeaders BaseOffsets
 */
/*
 * @test id=32bit
 * @library /test/lib /
 * @requires vm.bits == "32"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI BaseOffsets
 */

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import jdk.internal.misc.Unsafe;

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.whitebox.WhiteBox;

public class BaseOffsets {

    static class LIClass {
        public int i;
    }

    public static final WhiteBox WB = WhiteBox.getWhiteBox();

    static final long INT_OFFSET;
    static final long INT_ARRAY_OFFSET;
    static final long LONG_ARRAY_OFFSET;
    static {
        if (!Platform.is64bit() || WB.getBooleanVMFlag("UseCompactObjectHeaders")) {
            INT_OFFSET = 8;
            INT_ARRAY_OFFSET = 12;
            LONG_ARRAY_OFFSET = 16;
        } else if (WB.getBooleanVMFlag("UseCompressedClassPointers")) {
            INT_OFFSET = 12;
            INT_ARRAY_OFFSET = 16;
            LONG_ARRAY_OFFSET = 16;
        } else {
            INT_OFFSET = 16;
            INT_ARRAY_OFFSET = 20;
            LONG_ARRAY_OFFSET = 24;
        }
    }

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

        Asserts.assertEquals(unsafe.arrayBaseOffset(boolean[].class), INT_ARRAY_OFFSET,  "Misplaced boolean array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(byte[].class),    INT_ARRAY_OFFSET,  "Misplaced byte    array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(char[].class),    INT_ARRAY_OFFSET,  "Misplaced char    array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(short[].class),   INT_ARRAY_OFFSET,  "Misplaced short   array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(int[].class),     INT_ARRAY_OFFSET,  "Misplaced int     array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(long[].class),    LONG_ARRAY_OFFSET, "Misplaced long    array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(float[].class),   INT_ARRAY_OFFSET,  "Misplaced float   array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(double[].class),  LONG_ARRAY_OFFSET, "Misplaced double  array base");
        boolean narrowOops = System.getProperty("java.vm.compressedOopsMode") != null ||
                             !Platform.is64bit();
        long expected_objary_offset = narrowOops ? INT_ARRAY_OFFSET : LONG_ARRAY_OFFSET;
        Asserts.assertEquals(unsafe.arrayBaseOffset(Object[].class),  expected_objary_offset, "Misplaced object  array base");
    }
}
