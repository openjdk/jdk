/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test correctness of arraycopy intrinsic.
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xbatch
 *                   compiler.valhalla.inlinetypes.TestArrayCopy
 */

/*
 * @test id=NoTraps
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockExperimentalVMOptions -XX:PerMethodSpecTrapLimit=0 -XX:PerMethodTrapLimit=0
 *                   compiler.valhalla.inlinetypes.TestArrayCopy
 */

/*
 * @test id=AlwaysIncrementalInline
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline
 *                   compiler.valhalla.inlinetypes.TestArrayCopy
 */

/*
 * @test id=NoArrayFlattening
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xbatch -XX:-UseArrayFlattening
 *                   compiler.valhalla.inlinetypes.TestArrayCopy
 */

package compiler.valhalla.inlinetypes;

import java.util.Random;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;

public class TestArrayCopy {
    static final Random RAND = Utils.getRandomInstance();
    static final int LEN = RAND.nextInt(10) + 1;

    @LooselyConsistentValue
    static value class MyInt {
        int i = RAND.nextInt(10); // No need to check all values but the all-zero value is important
    }

    static Object[] getSrc1(int len) {
        MyInt[] src = new MyInt[len];
        src[0] = new MyInt();
        return src;
    }

    static Object[] getDst1(int len) {
        return new MyInt[len];
    }

    static void test1(int len) {
        Object[] src = getSrc1(len);
        Object[] dst = getDst1(len);
        System.arraycopy(src, 0, dst, 0, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static Object[] getSrc2(int len) {
        MyInt[] src = new MyInt[len];
        for (int i = 0; i < len; ++i) {
            src[i] = new MyInt();
        }
        return src;
    }

    static Object[] getDst2(int len) {
        return ValueClass.newNullRestrictedNonAtomicArray(MyInt.class, len, new MyInt());
    }

    static void test2(int len) {
        Object[] src = getSrc2(len);
        Object[] dst = getDst2(len);
        System.arraycopy(src, 0, dst, 0, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static Object[] getSrc2NoInit(int len) {
        MyInt[] src = new MyInt[len];
        // Last element not initialized
        for (int i = 0; i < len - 1; ++i) {
            src[i] = new MyInt();
        }
        return src;
    }

    static void test2NoInit(int len) {
        Object[] src = getSrc2NoInit(len);
        Object[] dst = getDst2(len);
        try {
            System.arraycopy(src, 0, dst, 0, len);
            throw new RuntimeException("No NullPointerException thrown");
        } catch (NullPointerException npe) {
            // Expected
        }
    }

    static Object[] getSrc3(int len) {
        MyInt[] src = new MyInt[len];
        for (int i = 0; i < len; ++i) {
            src[i] = new MyInt();
        }
        return src;
    }

    static Object[] getDst3(int len) {
        return ValueClass.newNullRestrictedAtomicArray(MyInt.class, len, new MyInt());
    }

    static void test3(int len) {
        Object[] src = getSrc3(len);
        Object[] dst = getDst3(len);
        System.arraycopy(src, 0, dst, 0, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static Object[] getSrc3NoInit(int len) {
        MyInt[] src = new MyInt[len];
        // Last element not initialized
        for (int i = 0; i < len - 1; ++i) {
            src[i] = new MyInt();
        }
        return src;
    }

    static void test3NoInit(int len) {
        Object[] src = getSrc3NoInit(len);
        Object[] dst = getDst3(len);
        try {
            System.arraycopy(src, 0, dst, 0, len);
            throw new RuntimeException("No NullPointerException thrown");
        } catch (NullPointerException npe) {
            // Expected
        }
    }

    static Object[] getSrc4(int len) {
        MyInt[] src = new MyInt[len];
        src[0] = new MyInt();
        return src;
    }

    static Object[] getDst4(int len) {
        return ValueClass.newNullableAtomicArray(MyInt.class, len);
    }

    static void test4(int len) {
        Object[] src = getSrc4(len);
        Object[] dst = getDst4(len);
        System.arraycopy(src, 0, dst, 0, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static Object[] getSrc5(int len) {
        return ValueClass.newNullRestrictedNonAtomicArray(MyInt.class, len, new MyInt());
    }

    static Object[] getDst5(int len) {
        return new MyInt[len];
    }

    static void test5(int len) {
        Object[] src = getSrc5(len);
        Object[] dst = getDst5(len);
        System.arraycopy(src, 0, dst, 0, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static Object[] getSrc6(int len) {
        return ValueClass.newNullRestrictedNonAtomicArray(MyInt.class, len, new MyInt());
    }

    static Object[] getDst6(int len) {
        return ValueClass.newNullRestrictedNonAtomicArray(MyInt.class, len, new MyInt());
    }

    static void test6(int len) {
        Object[] src = getSrc6(len);
        Object[] dst = getDst6(len);
        System.arraycopy(src, 0, dst, 0, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static Object[] getSrc7(int len) {
        return ValueClass.newNullRestrictedNonAtomicArray(MyInt.class, len, new MyInt());
    }

    static Object[] getDst7(int len) {
        return ValueClass.newNullRestrictedAtomicArray(MyInt.class, len, new MyInt());
    }

    static void test7(int len) {
        Object[] src = getSrc7(len);
        Object[] dst = getDst7(len);
        System.arraycopy(src, 0, dst, 0, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static Object[] getSrc8(int len) {
        return ValueClass.newNullRestrictedNonAtomicArray(MyInt.class, len, new MyInt());
    }

    static Object[] getDst8(int len) {
        return ValueClass.newNullableAtomicArray(MyInt.class, len);
    }

    static void test8(int len) {
        Object[] src = getSrc8(len);
        Object[] dst = getDst8(len);
        System.arraycopy(src, 0, dst, 0, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static Object[] getSrc9(int len) {
        return ValueClass.newNullRestrictedAtomicArray(MyInt.class, len, new MyInt());
    }

    static Object[] getDst9(int len) {
        return new MyInt[len];
    }

    static void test9(int len) {
        Object[] src = getSrc9(len);
        Object[] dst = getDst9(len);
        System.arraycopy(src, 0, dst, 0, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static Object[] getSrc10(int len) {
        return ValueClass.newNullRestrictedAtomicArray(MyInt.class, len, new MyInt());
    }

    static Object[] getDst10(int len) {
        return ValueClass.newNullRestrictedNonAtomicArray(MyInt.class, len, new MyInt());
    }

    static void test10(int len) {
        Object[] src = getSrc10(len);
        Object[] dst = getDst10(len);
        System.arraycopy(src, 0, dst, 0, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static Object[] getSrc11(int len) {
        return ValueClass.newNullRestrictedAtomicArray(MyInt.class, len, new MyInt());
    }

    static Object[] getDst11(int len) {
        return ValueClass.newNullRestrictedAtomicArray(MyInt.class, len, new MyInt());
    }

    static void test11(int len) {
        Object[] src = getSrc11(len);
        Object[] dst = getDst11(len);
        System.arraycopy(src, 0, dst, 0, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static Object[] getSrc12(int len) {
        return ValueClass.newNullRestrictedAtomicArray(MyInt.class, len, new MyInt());
    }

    static Object[] getDst12(int len) {
        return ValueClass.newNullableAtomicArray(MyInt.class, len);
    }

    static void test12(int len) {
        Object[] src = getSrc12(len);
        Object[] dst = getDst12(len);
        System.arraycopy(src, 0, dst, 0, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static Object[] getSrc13(int len) {
        MyInt[] src = (MyInt[])ValueClass.newNullableAtomicArray(MyInt.class, len);
        src[0] = new MyInt();
        return src;
    }

    static Object[] getDst13(int len) {
        return new MyInt[len];
    }

    static void test13(int len) {
        Object[] src = getSrc13(len);
        Object[] dst = getDst13(len);
        System.arraycopy(src, 0, dst, 0, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static Object[] getSrc14(int len) {
        MyInt[] src = (MyInt[])ValueClass.newNullableAtomicArray(MyInt.class, len);
        for (int i = 0; i < len; ++i) {
            src[i] = new MyInt();
        }
        return src;
    }

    static Object[] getDst14(int len) {
        return ValueClass.newNullRestrictedNonAtomicArray(MyInt.class, len, new MyInt());
    }

    static void test14(int len) {
        Object[] src = getSrc14(len);
        Object[] dst = getDst14(len);
        System.arraycopy(src, 0, dst, 0, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static Object[] getSrc14NoInit(int len) {
        MyInt[] src = (MyInt[])ValueClass.newNullableAtomicArray(MyInt.class, len);
        // Last element not initialized
        for (int i = 0; i < len - 1; ++i) {
            src[i] = new MyInt();
        }
        return src;
    }

    static void test14NoInit(int len) {
        Object[] src = getSrc14NoInit(len);
        Object[] dst = getDst14(len);
        try {
            System.arraycopy(src, 0, dst, 0, len);
            throw new RuntimeException("No NullPointerException thrown");
        } catch (NullPointerException npe) {
            // Expected
        }
    }

    static Object[] getSrc15(int len) {
        MyInt[] src = (MyInt[])ValueClass.newNullableAtomicArray(MyInt.class, len);
        for (int i = 0; i < len; ++i) {
            src[i] = new MyInt();
        }
        return src;
    }

    static Object[] getDst15(int len) {
        return ValueClass.newNullRestrictedAtomicArray(MyInt.class, len, new MyInt());
    }

    static void test15(int len) {
        Object[] src = getSrc15(len);
        Object[] dst = getDst15(len);
        System.arraycopy(src, 0, dst, 0, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static Object[] getSrc15NoInit(int len) {
        MyInt[] src = (MyInt[])ValueClass.newNullableAtomicArray(MyInt.class, len);
        // Last element not initialized
        for (int i = 0; i < len - 1; ++i) {
            src[i] = new MyInt();
        }
        return src;
    }

    static void test15NoInit(int len) {
        Object[] src = getSrc15NoInit(len);
        Object[] dst = getDst15(len);
        try {
            System.arraycopy(src, 0, dst, 0, len);
            throw new RuntimeException("No NullPointerException thrown");
        } catch (NullPointerException npe) {
            // Expected
        }
    }

    static Object[] getSrc16(int len) {
        MyInt[] src = (MyInt[])ValueClass.newNullableAtomicArray(MyInt.class, len);
        src[0] = new MyInt();
        return src;
    }

    static Object[] getDst16(int len) {
        return ValueClass.newNullableAtomicArray(MyInt.class, len);
    }

    static void test16(int len) {
        Object[] src = getSrc16(len);
        Object[] dst = getDst16(len);
        System.arraycopy(src, 0, dst, 0, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static void testConstantLen1() {
        Object[] src = getSrc1(LEN);
        Object[] dst = getDst1(LEN);
        System.arraycopy(src, 0, dst, 0, LEN);
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static void testConstantLen2() {
        Object[] src = getSrc2(LEN);
        Object[] dst = getDst2(LEN);
        System.arraycopy(src, 0, dst, 0, LEN);
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static void testConstantLen2NoInit() {
        Object[] src = getSrc2NoInit(LEN);
        Object[] dst = getDst2(LEN);
        try {
            System.arraycopy(src, 0, dst, 0, LEN);
            throw new RuntimeException("No NullPointerException thrown");
        } catch (NullPointerException npe) {
            // Expected
        }
    }

    static void testConstantLen3() {
        Object[] src = getSrc3(LEN);
        Object[] dst = getDst3(LEN);
        System.arraycopy(src, 0, dst, 0, LEN);
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static void testConstantLen3NoInit() {
        Object[] src = getSrc3NoInit(LEN);
        Object[] dst = getDst3(LEN);
        try {
            System.arraycopy(src, 0, dst, 0, LEN);
            throw new RuntimeException("No NullPointerException thrown");
        } catch (NullPointerException npe) {
            // Expected
        }
    }

    static void testConstantLen4() {
        Object[] src = getSrc4(LEN);
        Object[] dst = getDst4(LEN);
        System.arraycopy(src, 0, dst, 0, LEN);
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static void testConstantLen5() {
        Object[] src = getSrc5(LEN);
        Object[] dst = getDst5(LEN);
        System.arraycopy(src, 0, dst, 0, LEN);
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static void testConstantLen6() {
        Object[] src = getSrc6(LEN);
        Object[] dst = getDst6(LEN);
        System.arraycopy(src, 0, dst, 0, LEN);
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static void testConstantLen7() {
        Object[] src = getSrc7(LEN);
        Object[] dst = getDst7(LEN);
        System.arraycopy(src, 0, dst, 0, LEN);
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static void testConstantLen8() {
        Object[] src = getSrc8(LEN);
        Object[] dst = getDst8(LEN);
        System.arraycopy(src, 0, dst, 0, LEN);
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static void testConstantLen9() {
        Object[] src = getSrc9(LEN);
        Object[] dst = getDst9(LEN);
        System.arraycopy(src, 0, dst, 0, LEN);
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static void testConstantLen10() {
        Object[] src = getSrc10(LEN);
        Object[] dst = getDst10(LEN);
        System.arraycopy(src, 0, dst, 0, LEN);
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static void testConstantLen11() {
        Object[] src = getSrc11(LEN);
        Object[] dst = getDst11(LEN);
        System.arraycopy(src, 0, dst, 0, LEN);
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static void testConstantLen12() {
        Object[] src = getSrc12(LEN);
        Object[] dst = getDst12(LEN);
        System.arraycopy(src, 0, dst, 0, LEN);
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static void testConstantLen13() {
        Object[] src = getSrc13(LEN);
        Object[] dst = getDst13(LEN);
        System.arraycopy(src, 0, dst, 0, LEN);
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static void testConstantLen14() {
        Object[] src = getSrc14(LEN);
        Object[] dst = getDst14(LEN);
        System.arraycopy(src, 0, dst, 0, LEN);
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static void testConstantLen14NoInit() {
        Object[] src = getSrc14NoInit(LEN);
        Object[] dst = getDst14(LEN);
        try {
            System.arraycopy(src, 0, dst, 0, LEN);
            throw new RuntimeException("No NullPointerException thrown");
        } catch (NullPointerException npe) {
            // Expected
        }
    }

    static void testConstantLen15() {
        Object[] src = getSrc15(LEN);
        Object[] dst = getDst15(LEN);
        System.arraycopy(src, 0, dst, 0, LEN);
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    static void testConstantLen15NoInit() {
        Object[] src = getSrc15NoInit(LEN);
        Object[] dst = getDst15(LEN);
        try {
            System.arraycopy(src, 0, dst, 0, LEN);
            throw new RuntimeException("No NullPointerException thrown");
        } catch (NullPointerException npe) {
            // Expected
        }
    }

    static void testConstantLen16() {
        Object[] src = getSrc16(LEN);
        Object[] dst = getDst16(LEN);
        System.arraycopy(src, 0, dst, 0, LEN);
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(src[i], dst[i]);
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 50_000; ++i) {
            int len = (i % 10) + 1;
            test1(len);
            test2(len);
            test2NoInit(len);
            test3(len);
            test3NoInit(len);
            test4(len);
            test5(len);
            test6(len);
            test7(len);
            test8(len);
            test9(len);
            test10(len);
            test11(len);
            test12(len);
            test13(len);
            test14(len);
            test14NoInit(len);
            test15(len);
            test15NoInit(len);
            test16(len);

            testConstantLen1();
            testConstantLen2();
            testConstantLen2NoInit();
            testConstantLen3();
            testConstantLen3NoInit();
            testConstantLen4();
            testConstantLen5();
            testConstantLen6();
            testConstantLen7();
            testConstantLen8();
            testConstantLen9();
            testConstantLen10();
            testConstantLen11();
            testConstantLen12();
            testConstantLen13();
            testConstantLen14();
            testConstantLen14NoInit();
            testConstantLen15();
            testConstantLen15NoInit();
            testConstantLen16();
        }
    }
}
