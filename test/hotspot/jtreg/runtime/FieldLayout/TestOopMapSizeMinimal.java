/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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

import jdk.internal.misc.Unsafe;
import jdk.test.whitebox.WhiteBox;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/*
 * @test id=no_coops_no_ccptr_no_coh
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UnlockExperimentalVMOptions -XX:-UseCompressedOops -XX:-UseCompressedClassPointers -XX:-UseCompactObjectHeaders TestOopMapSizeMinimal
 */

/*
 * @test id=coops_no_ccptr_no_coh
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UnlockExperimentalVMOptions -XX:+UseCompressedOops -XX:-UseCompressedClassPointers -XX:-UseCompactObjectHeaders TestOopMapSizeMinimal
 */

/*
 * @test id=no_coops_ccptr_no_coh
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UnlockExperimentalVMOptions -XX:-UseCompressedOops -XX:+UseCompressedClassPointers -XX:-UseCompactObjectHeaders TestOopMapSizeMinimal
 */

/*
 * @test id=coops_ccptr_no_coh
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UnlockExperimentalVMOptions -XX:+UseCompressedOops -XX:+UseCompressedClassPointers -XX:-UseCompactObjectHeaders TestOopMapSizeMinimal
 */

/*
 * @test id=no_coops_ccptr_coh
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UnlockExperimentalVMOptions -XX:-UseCompressedOops -XX:+UseCompactObjectHeaders TestOopMapSizeMinimal
 */

public class TestOopMapSizeMinimal {

    public static int OOP_SIZE_IN_BYTES = -1;
    public static int HEADER_SIZE_IN_BYTES = -1;

    static {
        WhiteBox WB = WhiteBox.getWhiteBox();
        boolean is_64_bit = System.getProperty("sun.arch.data.model").equals("64");
        if (is_64_bit) {
            if (System.getProperty("java.vm.compressedOopsMode") == null) {
                OOP_SIZE_IN_BYTES = 8;
            } else {
                OOP_SIZE_IN_BYTES = 4;
            }
        } else {
            OOP_SIZE_IN_BYTES = 4;
        }
        if (is_64_bit) {
            if (WB.getBooleanVMFlag("UseCompactObjectHeaders")) {
                HEADER_SIZE_IN_BYTES = 8;
            } else if (WB.getBooleanVMFlag("UseCompressedClassPointers")) {
                HEADER_SIZE_IN_BYTES = 12;
            } else {
                HEADER_SIZE_IN_BYTES = 16;
            }
        } else {
            HEADER_SIZE_IN_BYTES = 8;
        }
    }

    public static long alignUp(long value, long alignment) {
        return (value + alignment - 1) & ~(alignment - 1);
    }

    public static long alignForOop(long position) {
        return alignUp(position, OOP_SIZE_IN_BYTES);
    }

    private static final Unsafe U = Unsafe.getUnsafe();

    public static class BASE {
        int i1;
        Object o1;
    }

    public static class DERIVED1 extends BASE {
        int i2;
        Object o2;
    }

    public static class DERIVED2 extends DERIVED1 {
        int i3;
        Object o3;
    }

    public static class DERIVED3 extends DERIVED2 {
        int i4;
        Object o4;
    }

    static boolean mismatch = false;

    private static void checkOffset(Field f, long expectedOffset) {
        long realOffset = U.objectFieldOffset(f);
        System.out.println("Offset for field " + f.getName() +
                ": expected " + expectedOffset + ", got " + realOffset + ".");
        if (U.objectFieldOffset(f) != expectedOffset) {
            mismatch = true;
            System.out.println(" ... mimatch");
        }
    }

    private static List<Field> allFieldsOf(Class c) {
        ArrayList<Field> l = new ArrayList<>();
        while (c != null) {
            for (Field f : c.getDeclaredFields()) {
                l.add(f);
            }
            c = c.getSuperclass();
        }
        return l;
    }

    public static void main(String[] args) throws Exception {

        System.out.println("HEADER_SIZE_IN_BYTES " + HEADER_SIZE_IN_BYTES + ", OOP_SIZE_IN_BYTES " + OOP_SIZE_IN_BYTES);

        long i1_loc_expected;
        long o1_loc_expected;
        long o2_loc_expected;
        long i2_loc_expected;
        long i3_loc_expected;
        long o3_loc_expected;
        long o4_loc_expected;
        long i4_loc_expected;

        // We expect the layouter to reverse order of oop- and non-oop fields
        // when it is useful to minimize the number of oop map entries.
        //
        // If we have no gaps, this should be the layout:
        // BASE      i1
        //           o1  oopmap entry 1
        // DERIVED1  o2  oopmap entry 1  (reversed order)
        //           i2
        // DERIVED3  i3
        //           o3  oopmap entry 2
        // DERIVED4  o4  oopmap entry 2  (reversed order)
        //           i4

        // There are two combinations that have gaps:
        // -UseCompressedOops + +COH, and -UseCompressedOops + -UseCompressedClassPointers.
        // In both cases there is a gap following i1, and i2 will therefore nestle into that gap.
        // Otherwise the same logic applies.

        if (OOP_SIZE_IN_BYTES == 4 ||                               // oop size == int size
            (OOP_SIZE_IN_BYTES == 8 && HEADER_SIZE_IN_BYTES == 12)
        ) {
            // No gaps

            // Expected layout for BASE: int, object
            i1_loc_expected = HEADER_SIZE_IN_BYTES;
            o1_loc_expected = i1_loc_expected + 4;

            // Expected layout for DERIVED1: object, int (to make o2 border o1)
            o2_loc_expected = o1_loc_expected + OOP_SIZE_IN_BYTES;
            i2_loc_expected = o2_loc_expected + OOP_SIZE_IN_BYTES;

            // Expected layout for DERIVED2: int, object (to trail with oops, for derived classes to nestle against)
            i3_loc_expected = i2_loc_expected + 4;
            o3_loc_expected = i3_loc_expected + 4;

            // Expected layout for DERIVED3: object, int (to make o4 border o3)
            o4_loc_expected = o3_loc_expected + OOP_SIZE_IN_BYTES;
            i4_loc_expected = o4_loc_expected + OOP_SIZE_IN_BYTES;

        } else if (OOP_SIZE_IN_BYTES == 8) {

            // gap after i1

            i1_loc_expected = HEADER_SIZE_IN_BYTES;
            o1_loc_expected = i1_loc_expected + 4 + 4; // + alignment gap

            o2_loc_expected = o1_loc_expected + OOP_SIZE_IN_BYTES;
            i2_loc_expected = i1_loc_expected + 4; // into gap following i1

            o3_loc_expected = o2_loc_expected + OOP_SIZE_IN_BYTES;
            i3_loc_expected = o3_loc_expected + OOP_SIZE_IN_BYTES;

            i4_loc_expected = i3_loc_expected + 4;
            o4_loc_expected = i4_loc_expected + 4;
        } else {
            throw new RuntimeException("Unexpected");
        }

        List<Field> l = allFieldsOf(DERIVED3.class);
        for (Field f : l) {
            switch (f.getName()) {
                case "i1" : checkOffset(f, i1_loc_expected); break;
                case "o1" : checkOffset(f, o1_loc_expected); break;
                case "i2" : checkOffset(f, i2_loc_expected); break;
                case "o2" : checkOffset(f, o2_loc_expected); break;
                case "i3" : checkOffset(f, i3_loc_expected); break;
                case "o3" : checkOffset(f, o3_loc_expected); break;
                case "i4" : checkOffset(f, i4_loc_expected); break;
                case "o4" : checkOffset(f, o4_loc_expected); break;
                default: throw new RuntimeException("Unexpected");
            }
        }
        if (mismatch) {
            throw new RuntimeException("Mismatch!");
        }
        System.out.println("All good.");
    }

}

