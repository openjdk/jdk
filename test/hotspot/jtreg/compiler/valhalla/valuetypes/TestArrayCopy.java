/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xbatch
 *                   ${test.main.class}
 */

/*
 * @test id=NoTraps
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockExperimentalVMOptions -XX:PerMethodSpecTrapLimit=0 -XX:PerMethodTrapLimit=0
 *                   ${test.main.class}
 */

/*
 * @test id=NoTrapsNoCompressedOops
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockExperimentalVMOptions -XX:PerMethodSpecTrapLimit=0 -XX:PerMethodTrapLimit=0
 *                   -XX:-UseCompressedOops
 *                   ${test.main.class}
 */

/*
 * @test id=AlwaysIncrementalInline
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline
 *                   ${test.main.class}
 */

/*
 * @test id=NoArrayFlattening
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:-UseArrayFlattening
 *                   ${test.main.class}
 */

/*
 * @test id=ir
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run driver ${test.main.class} ir
 */

package compiler.valhalla.valuetypes;

import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;

public class TestArrayCopy {
    static final Random RAND = Utils.getRandomInstance();
    static final int LEN = RAND.nextInt(10) + 1;
    static final int SRC_INIT = 444;
    static final int DEST_INIT = 555;

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

    /*
     * Test cases where we have the same null free non-atomic array flavors but actual different value classes.
     *
     * We distinguish these cases:
     * - Same elem width
     * - Smaller to larger elem width
     * - Larger to smaller elem width
     *
     * All of these should result in an ArrayStoreException and the dest array should not be modified.
     */
    static class NullFreeArraysOfDifferentClasses {
        static final VInt[] SRC_INT = newSrc(VInt.class, VInt::new);
        static final VInt2[] SRC_INT2 = newSrc(VInt2.class, VInt2::new);
        static final VLong[] SRC_LONG = newSrc(VLong.class, VLong::new);
        static final VLong2[] SRC_LONG2 = newSrc(VLong2.class, VLong2::new);

        static final VInt[] DEST_INT = newDest(VInt.class, VInt::new);
        static final VInt2[] DEST_INT2 = newDest(VInt2.class, VInt2::new);
        static final VLong[] DEST_LONG = newDest(VLong.class, VLong::new);
        static final VLong2[] DEST_LONG2 = newDest(VLong2.class, VLong2::new);

        static final VInt[] DEST_INT_UNMODIFIED = newDest(VInt.class, VInt::new);
        static final VLong[] DEST_LONG_UNMODIFIED = newDest(VLong.class, VLong::new);
        static final VInt2[] DEST_INT2_UNMODIFIED = newDest(VInt2.class, VInt2::new);
        static final VLong2[] DEST_LONG2_UNMODIFIED = newDest(VLong2.class, VLong2::new);

        static int srcIdx;
        static int destIdx;
        static int length;
        static boolean flag;

        static <V> V[] newSrc(Class<V> type, IntFunction<V> constructor) {
            return newArr(type, constructor, SRC_INIT);
        }

        static <V> V[] newDest(Class<V> type, IntFunction<V> constructor) {
            return newArr(type, constructor, DEST_INIT);
        }

        @SuppressWarnings("unchecked")
        static <V> V[] newArr(Class<V> type, IntFunction<V> constructor, int initValue) {
            V[] arr = (V[]) ValueClass.newNullRestrictedNonAtomicArray(type, LEN, constructor.apply(0));
            init(arr, constructor, initValue);
            return arr;
        }

        static void run() {
            for (int i = 0; i < 10_000; i++) {
                flag = !flag;
                runTest(NullFreeArraysOfDifferentClasses::testSmallToLarge, DEST_LONG, DEST_LONG_UNMODIFIED);
                runTest(NullFreeArraysOfDifferentClasses::testLargeToSmall, DEST_INT, DEST_INT_UNMODIFIED);
                runTest(NullFreeArraysOfDifferentClasses::testSmallToSmall, DEST_INT2, DEST_INT2_UNMODIFIED);
                runTest(NullFreeArraysOfDifferentClasses::testLargeToLarge, DEST_LONG2, DEST_LONG2_UNMODIFIED);
                runTest(NullFreeArraysOfDifferentClasses::testSmallToLargeAbstract,
                        flag ? DEST_LONG : DEST_LONG2,
                        flag ? DEST_LONG_UNMODIFIED : DEST_LONG2_UNMODIFIED);
                runTest(NullFreeArraysOfDifferentClasses::testLargeToSmallAbstract,
                        flag ? DEST_INT : DEST_INT2,
                        flag ? DEST_INT_UNMODIFIED : DEST_INT2_UNMODIFIED);
                runTest(NullFreeArraysOfDifferentClasses::testSmallToSmallAbstract,
                        flag ? DEST_INT2 : DEST_INT,
                        flag ? DEST_INT2_UNMODIFIED : DEST_INT_UNMODIFIED);
                runTest(NullFreeArraysOfDifferentClasses::testLargeToLargeAbstract,
                        flag ? DEST_LONG2 : DEST_LONG,
                        flag ? DEST_LONG2_UNMODIFIED : DEST_LONG_UNMODIFIED);
                if (LEN > 1) {
                    length = 1 + RAND.nextInt(LEN - 1);      // 1..LEN-1
                    srcIdx = 1 + RAND.nextInt(LEN - length); // 1..LEN-length
                    destIdx = 1 + RAND.nextInt(LEN - length); // 1..LEN-length
                } else {
                    length = 1;
                    srcIdx = 0;
                    destIdx = 0;
                }
                runTest(NullFreeArraysOfDifferentClasses::testSmallToLargeRandom, DEST_LONG, DEST_LONG_UNMODIFIED);
                runTest(NullFreeArraysOfDifferentClasses::testLargeToSmallRandom, DEST_INT, DEST_INT_UNMODIFIED);
                runTest(NullFreeArraysOfDifferentClasses::testSmallToSmallRandom, DEST_INT2, DEST_INT2_UNMODIFIED);
                runTest(NullFreeArraysOfDifferentClasses::testLargeToLargeRandom, DEST_LONG2, DEST_LONG2_UNMODIFIED);
                runTest(NullFreeArraysOfDifferentClasses::testSmallToLargeAbstractRandom,
                        flag ? DEST_LONG : DEST_LONG2,
                        flag ? DEST_LONG_UNMODIFIED : DEST_LONG2_UNMODIFIED);
                runTest(NullFreeArraysOfDifferentClasses::testLargeToSmallAbstractRandom,
                        flag ? DEST_INT : DEST_INT2,
                        flag ? DEST_INT_UNMODIFIED : DEST_INT2_UNMODIFIED);
                runTest(NullFreeArraysOfDifferentClasses::testSmallToSmallAbstractRandom,
                        flag ? DEST_INT2 : DEST_INT,
                        flag ? DEST_INT2_UNMODIFIED : DEST_INT_UNMODIFIED);
                runTest(NullFreeArraysOfDifferentClasses::testLargeToLargeAbstractRandom,
                        flag ? DEST_LONG2 : DEST_LONG,
                        flag ? DEST_LONG2_UNMODIFIED : DEST_LONG_UNMODIFIED);
            }
        }

        static <V> void runTest(Runnable test, V[] dest, V[] expectedDest) {
            try {
                test.run();
                throw new RuntimeException("Should throw ArrayStoreException");
            } catch (ArrayStoreException e) {
                // Expected
            }

            for (int j = 0; j < LEN; j++) {
                V actual = dest[j];
                V expected = expectedDest[j];
                if (actual != expected) {
                    throw new RuntimeException("Should be unchanged: actual=" + actual + " != expected=" + expected);
                }
            }
        }

        static void testSmallToLarge() {
            System.arraycopy(SRC_INT, 0, DEST_LONG, 0, LEN);
        }

        static void testSmallToLargeAbstract() {
            VBase[] src = flag ? SRC_INT : SRC_INT2;
            VBase[] dest = flag ? DEST_LONG : DEST_LONG2;
            System.arraycopy(src, 0, dest, 0, LEN);
        }

        static void testSmallToLargeRandom() {
            System.arraycopy(SRC_INT, srcIdx, DEST_LONG, destIdx, length);
        }

        static void testSmallToLargeAbstractRandom() {
            VBase[] src = flag ? SRC_INT : SRC_INT2;
            VBase[] dest = flag ? DEST_LONG : DEST_LONG2;
            System.arraycopy(src, srcIdx, dest, destIdx, length);
        }

        static void testLargeToSmall() {
            System.arraycopy(SRC_LONG, 0, DEST_INT, 0, LEN);
        }

        static void testLargeToSmallAbstract() {
            VBase[] src = flag ? SRC_LONG : SRC_LONG2;
            VBase[] dest = flag ? DEST_INT : DEST_INT2;
            System.arraycopy(src, 0, dest, 0, LEN);
        }

        static void testLargeToSmallRandom() {
            System.arraycopy(SRC_LONG, srcIdx, DEST_INT, destIdx, length);
        }

        static void testLargeToSmallAbstractRandom() {
            VBase[] src = flag ? SRC_LONG : SRC_LONG2;
            VBase[] dest = flag ? DEST_INT : DEST_INT2;
            System.arraycopy(src, srcIdx, dest, destIdx, length);
        }

        static void testSmallToSmall() {
            System.arraycopy(SRC_INT, 0, DEST_INT2, 0, LEN);
        }

        static void testSmallToSmallAbstract() {
            VBase[] src = flag ? SRC_INT : SRC_INT2;
            VBase[] dest = flag ? DEST_INT2 : DEST_INT;
            System.arraycopy(src, 0, dest, 0, LEN);
        }

        static void testSmallToSmallRandom() {
            System.arraycopy(SRC_INT, srcIdx, DEST_INT2, destIdx, length);
        }

        static void testSmallToSmallAbstractRandom() {
            VBase[] src = flag ? SRC_INT : SRC_INT2;
            VBase[] dest = flag ? DEST_INT2 : DEST_INT;
            System.arraycopy(src, srcIdx, dest, destIdx, length);
        }

        static void testLargeToLarge() {
            System.arraycopy(SRC_LONG, 0, DEST_LONG2, 0, LEN);
        }

        static void testLargeToLargeAbstract() {
            VBase[] src = flag ? SRC_LONG : SRC_LONG2;
            VBase[] dest = flag ? DEST_LONG2 : DEST_LONG;
            System.arraycopy(src, 0, dest, 0, LEN);
        }

        static void testLargeToLargeRandom() {
            System.arraycopy(SRC_LONG, srcIdx, DEST_LONG2, destIdx, length);
        }

        static void testLargeToLargeAbstractRandom() {
            VBase[] src = flag ? SRC_LONG : SRC_LONG2;
            VBase[] dest = flag ? DEST_LONG2 : DEST_LONG;
            System.arraycopy(src, srcIdx, dest, destIdx, length);
        }
    }

    /*
     * Test cases where src and dest have the same class but different layouts.
     *
     * We cover all variations of src/dest <-> dest/src:
     * - null-free-atomic     <-> nullable
     * - null-free-non-atomic <-> nullable
     * - null-free-atomic     <-> null-free-non-atomic
     *
     * To make this work, we add branches such that C2 falls back to the type in [] to have the same src and dest property:
     * - null-free-atomic      [C2: nullable] <-> nullable
     * - null-free-non-atomic  [C2: nullable] <-> nullable
     * - null-free-atomic      [C2: unknown atomicity] <-> null-free-non-atomic
     * - null-free-non-atomic  [C2: unknown atomicity] <-> null-free-atomic
     *
     * Finally, we also add branches for src and dest where C2 only knows unknown atomicity + nullable
     *
     * We also add tests where we have a null element in a nullable src array. When calling arraycopy, we first copy
     * all non-null elements up to the null element and then throw a NPE for that null element. The rest of the dest
     * array should remain unmodified.
     */
    static class SameClassDifferentLayout {
        static final VTwoShorts[] SRC_NULL_FREE_ATOMIC = (VTwoShorts[]) ValueClass.newNullRestrictedAtomicArray(VTwoShorts.class, LEN, new VTwoShorts(0));
        static final VTwoShorts[] SRC_NULLABLE = (VTwoShorts[]) ValueClass.newNullableAtomicArray(VTwoShorts.class, LEN);
        static final VTwoShorts[] DEST_NULLABLE = (VTwoShorts[]) ValueClass.newNullableAtomicArray(VTwoShorts.class, LEN);
        static final VTwoShorts[] DEST_NULLABLE_UNMODIFIED = (VTwoShorts[]) ValueClass.newNullableAtomicArray(VTwoShorts.class, LEN);
        static final VTwoShorts[] DEST_NULL_FREE_ATOMIC = (VTwoShorts[]) ValueClass.newNullRestrictedAtomicArray(VTwoShorts.class, LEN, new VTwoShorts(0));
        static final VTwoShorts[] DEST_NULL_FREE_ATOMIC_UNMODIFIED = (VTwoShorts[]) ValueClass.newNullRestrictedAtomicArray(VTwoShorts.class, LEN, new VTwoShorts(0));

        static final VTwoShorts[] SRC_NULL_FREE_NON_ATOMIC = (VTwoShorts[]) ValueClass.newNullRestrictedNonAtomicArray(VTwoShorts.class, LEN, new VTwoShorts(0));
        static final VTwoShorts[] DEST_NULL_FREE_NON_ATOMIC = (VTwoShorts[]) ValueClass.newNullRestrictedNonAtomicArray(VTwoShorts.class, LEN, new VTwoShorts(0));
        static final VTwoShorts[] DEST_NULL_FREE_NON_ATOMIC_UNMODIFIED = (VTwoShorts[]) ValueClass.newNullRestrictedNonAtomicArray(VTwoShorts.class, LEN, new VTwoShorts(0));

        static final VTwoShorts[] SRC_NULL_FREE_NON_ATOMIC_SIZE_10 = (VTwoShorts[]) ValueClass.newNullRestrictedNonAtomicArray(VTwoShorts.class, 10, new VTwoShorts(0));
        static final VTwoShorts[] DEST_NULL_FREE_NON_ATOMIC_SIZE_10 = (VTwoShorts[]) ValueClass.newNullRestrictedNonAtomicArray(VTwoShorts.class, 10, new VTwoShorts(0));

        static final VTwoShorts2[] SRC_NULL_FREE_NON_ATOMIC2 = (VTwoShorts2[]) ValueClass.newNullRestrictedNonAtomicArray(VTwoShorts2.class, LEN, new VTwoShorts2(0));
        static final VTwoShorts2[] DEST_NULL_FREE_NON_ATOMIC2 = (VTwoShorts2[]) ValueClass.newNullRestrictedNonAtomicArray(VTwoShorts2.class, LEN, new VTwoShorts2(0));

        static {
            initSrc(SRC_NULL_FREE_ATOMIC);
            initSrc(SRC_NULLABLE);
            initSrc(SRC_NULL_FREE_NON_ATOMIC);

            initDest(DEST_NULLABLE_UNMODIFIED);
            initDest(DEST_NULL_FREE_ATOMIC_UNMODIFIED);
            initDest(DEST_NULL_FREE_NON_ATOMIC_UNMODIFIED);

            init(SRC_NULL_FREE_NON_ATOMIC2, VTwoShorts2::new, SRC_INIT);
        }

        static void initSrc(VTwoShorts[] arr) {
            init(arr, VTwoShorts::new, SRC_INIT);
        }

        static void initDest(VTwoShorts[] arr) {
            init(arr, VTwoShorts::new, DEST_INIT);
        }

        @Run(test = {"testNullFreeAtomicToNullable",
                     "testNullableToNullFreeAtomic",
                     "testNullFreeNonAtomicToNullable",
                     "testNullableToNullFreeNonAtomic",
                     "testNullFreeToNonAtomic",
                     "testNullFreeToAtomic",
                     "testMergedNullableNullFreeAtomic",
                     "testMergedNullableNullFreeNonAtomic",
                     "testFastPathMacroExpansion",
                     "testSlowPathAtomic",
                     "testAbstract"})
        static void run() {
            boolean flag = RAND.nextBoolean();
            boolean flag2 = RAND.nextBoolean();
            initDest(DEST_NULLABLE);
            runTest(SameClassDifferentLayout::testNullFreeAtomicToNullable, flag,
                    SRC_NULL_FREE_ATOMIC, SRC_NULLABLE, DEST_NULLABLE);

            initDest(DEST_NULLABLE);
            initDest(DEST_NULL_FREE_ATOMIC);
            runTest(SameClassDifferentLayout::testNullableToNullFreeAtomic, flag,
                    DEST_NULL_FREE_ATOMIC, DEST_NULLABLE, SRC_NULLABLE);

            initDest(DEST_NULLABLE);
            runTest(SameClassDifferentLayout::testNullFreeNonAtomicToNullable, flag,
                    SRC_NULL_FREE_NON_ATOMIC, SRC_NULLABLE, DEST_NULLABLE);

            initDest(DEST_NULLABLE);
            initDest(DEST_NULL_FREE_NON_ATOMIC);
            runTest(SameClassDifferentLayout::testNullableToNullFreeNonAtomic, flag,
                    DEST_NULL_FREE_NON_ATOMIC, DEST_NULLABLE, SRC_NULLABLE);

            initDest(DEST_NULL_FREE_NON_ATOMIC);
            runTest(SameClassDifferentLayout::testNullFreeToNonAtomic, flag,
                    SRC_NULL_FREE_ATOMIC, SRC_NULL_FREE_NON_ATOMIC, DEST_NULL_FREE_NON_ATOMIC);

            initDest(DEST_NULL_FREE_ATOMIC);
            initDest(DEST_NULL_FREE_NON_ATOMIC);
            runTest(SameClassDifferentLayout::testNullFreeToAtomic, flag,
                    DEST_NULL_FREE_ATOMIC, DEST_NULL_FREE_NON_ATOMIC, SRC_NULL_FREE_NON_ATOMIC);

            initDest(DEST_NULLABLE);
            initDest(DEST_NULL_FREE_ATOMIC);
            VTwoShorts[] vTwoShortsSrc = flag ? SRC_NULL_FREE_ATOMIC : SRC_NULLABLE;
            VTwoShorts[] vTwoShortsDest = flag2 ? DEST_NULL_FREE_ATOMIC : DEST_NULLABLE;
            runTestMerged(SameClassDifferentLayout::testMergedNullableNullFreeAtomic, flag, flag2,
                          vTwoShortsSrc, vTwoShortsDest);

            initDest(DEST_NULLABLE);
            initDest(DEST_NULL_FREE_NON_ATOMIC);
            vTwoShortsSrc = flag ? SRC_NULL_FREE_NON_ATOMIC : SRC_NULLABLE;
            vTwoShortsDest = flag2 ? DEST_NULL_FREE_NON_ATOMIC : DEST_NULLABLE;
            runTestMerged(SameClassDifferentLayout::testMergedNullableNullFreeNonAtomic, flag, flag2,
                          vTwoShortsSrc, vTwoShortsDest);

            int nullIndex = RAND.nextInt(LEN);
            VTwoShorts backup = SRC_NULLABLE[nullIndex];

            SRC_NULLABLE[nullIndex] = null;
            initDest(DEST_NULLABLE);
            initDest(DEST_NULL_FREE_ATOMIC);
            runTestPrefixWithNull(SameClassDifferentLayout::testNullableToNullFreeAtomic, flag, nullIndex,
                                  DEST_NULL_FREE_ATOMIC,
                                  DEST_NULL_FREE_ATOMIC_UNMODIFIED);

            initDest(DEST_NULLABLE);
            initDest(DEST_NULL_FREE_NON_ATOMIC);
            runTestPrefixWithNull(SameClassDifferentLayout::testNullableToNullFreeNonAtomic, flag, nullIndex,
                                  DEST_NULL_FREE_NON_ATOMIC,
                                  DEST_NULL_FREE_NON_ATOMIC_UNMODIFIED);
            SRC_NULLABLE[nullIndex] = backup;

            initDest(DEST_NULL_FREE_NON_ATOMIC_SIZE_10);
            testFastPathMacroExpansion();
            verifySame(SRC_NULL_FREE_NON_ATOMIC_SIZE_10, DEST_NULL_FREE_NON_ATOMIC_SIZE_10);

            initDest(DEST_NULL_FREE_ATOMIC);
            testSlowPathAtomic();
            verifySame(SRC_NULL_FREE_ATOMIC, DEST_NULL_FREE_ATOMIC);

            initDest(DEST_NULL_FREE_NON_ATOMIC);
            init(DEST_NULL_FREE_NON_ATOMIC2, VTwoShorts2::new, DEST_INIT);
            testAbstract(SRC_NULL_FREE_NON_ATOMIC, DEST_NULL_FREE_NON_ATOMIC);
            verifySame(SRC_NULL_FREE_NON_ATOMIC, DEST_NULL_FREE_NON_ATOMIC);
            testAbstract(SRC_NULL_FREE_NON_ATOMIC2, DEST_NULL_FREE_NON_ATOMIC2);
            verifySame(SRC_NULL_FREE_NON_ATOMIC2, DEST_NULL_FREE_NON_ATOMIC2);
        }

        static void runTest(Consumer<Boolean> test, boolean flag,
                            VTwoShorts[] choice1, VTwoShorts[] choice2, VTwoShorts[] expected) {
            test.accept(flag);
            VTwoShorts[] vTwoShorts = flag ? choice1 : choice2;
            verifySame(vTwoShorts, expected);
        }

        static void runTestMerged(BiConsumer<Boolean, Boolean> test, boolean flag, boolean flag2,
                                  VTwoShorts[] src, VTwoShorts[] dest) {
            test.accept(flag, flag2);
            verifySame(src, dest);
        }

        static void runTestPrefixWithNull(Consumer<Boolean> test, boolean isNullFree, int nullIndex,
                                          VTwoShorts[] nullFreeArr,
                                          VTwoShorts[] nullFreeArrUnmodified) {
            VTwoShorts[] vTwoShorts = isNullFree ? nullFreeArr : DEST_NULLABLE;
            VTwoShorts[] vTwoShortsUnmodified = isNullFree ? nullFreeArrUnmodified : DEST_NULLABLE_UNMODIFIED;
            try {
                test.accept(isNullFree);
                Asserts.assertFalse(isNullFree);

                // Nullable case: Can successfully copy null.
                verifySame(SRC_NULLABLE, vTwoShorts);
            } catch (NullPointerException e) {
                // Null Free case -> threw when encounter null at nullIndex
                Asserts.assertTrue(isNullFree);
                verifyPrefixCopied(vTwoShorts, vTwoShortsUnmodified, nullIndex);
            }
        }

        static void verifyPrefixCopied(VTwoShorts[] dest, VTwoShorts[] destUnmodified, int nullIdx) {
            for (int i = 0; i < nullIdx; i++) {
                // Prefix copied
                Asserts.assertEQ(SRC_NULLABLE[i], dest[i]);
            }

            for (int i = nullIdx; i < LEN; i++) {
                // Null + postfix NOT copied
                Asserts.assertEQ(dest[i], destUnmodified[i]);
            }
        }

        @Test
        @IR(counts = { IRNode.SLOW_ARRAYCOPY, "1" }, // Different layouts
            failOn = { IRNode.JINT_ARRAYCOPY, IRNode.JLONG_ARRAYCOPY }) // no fast path
        static void testNullFreeAtomicToNullable(boolean flag) {
            VTwoShorts[] vTwoShorts = flag ? SRC_NULL_FREE_ATOMIC : SRC_NULLABLE; // Type: nullable
            System.arraycopy(vTwoShorts, 0, DEST_NULLABLE, 0, LEN);
        }

        @Test
        @IR(counts = { IRNode.SLOW_ARRAYCOPY, "1" }, // Different layouts
            failOn = { IRNode.JINT_ARRAYCOPY, IRNode.JLONG_ARRAYCOPY }) // no fast path
        static void testNullableToNullFreeAtomic(boolean flag) {
            VTwoShorts[] vTwoShorts = flag ? DEST_NULL_FREE_ATOMIC : DEST_NULLABLE; // Type: nullable
            System.arraycopy(SRC_NULLABLE, 0, vTwoShorts, 0, LEN);
        }

        @Test
        @IR(counts = { IRNode.SLOW_ARRAYCOPY, "1" }, // Different layouts
            failOn = { IRNode.JINT_ARRAYCOPY, IRNode.JLONG_ARRAYCOPY }) // no fast path
        static void testNullFreeNonAtomicToNullable(boolean flag) {
            VTwoShorts[] vTwoShorts = flag ? SRC_NULL_FREE_NON_ATOMIC : SRC_NULLABLE; // Type: Unknown atomicity + nullable
            System.arraycopy(vTwoShorts, 0, DEST_NULLABLE, 0, LEN);
        }

        @Test
        @IR(counts = { IRNode.SLOW_ARRAYCOPY, "1" }, // Different layouts
            failOn = { IRNode.JINT_ARRAYCOPY, IRNode.JLONG_ARRAYCOPY }) // no fast path
        static void testNullableToNullFreeNonAtomic(boolean flag) {
            VTwoShorts[] vTwoShorts = flag ? DEST_NULL_FREE_NON_ATOMIC : DEST_NULLABLE; // Type: Unknown atomicity + nullable
            System.arraycopy(SRC_NULLABLE, 0, vTwoShorts, 0, LEN);
        }

        @Test
        @IR(counts = { IRNode.SLOW_ARRAYCOPY, "1" }, // Different layouts
            failOn = { IRNode.JINT_ARRAYCOPY, IRNode.JLONG_ARRAYCOPY }) // no fast path
        static void testNullFreeToNonAtomic(boolean flag) {
            VTwoShorts[] vTwoShorts = flag ? SRC_NULL_FREE_ATOMIC : SRC_NULL_FREE_NON_ATOMIC; // Type: null free + unknown atomicity
            System.arraycopy(vTwoShorts, 0, DEST_NULL_FREE_NON_ATOMIC, 0, LEN);
        }

        @Test
        @IR(counts = { IRNode.SLOW_ARRAYCOPY, "1" }, // Different layouts
            failOn = { IRNode.JINT_ARRAYCOPY, IRNode.JLONG_ARRAYCOPY }) // no fast path
        static void testNullFreeToAtomic(boolean flag) {
            VTwoShorts[] vTwoShorts = flag ? DEST_NULL_FREE_ATOMIC : DEST_NULL_FREE_NON_ATOMIC; // Type: null free + unknown atomicity
            System.arraycopy(SRC_NULL_FREE_NON_ATOMIC, 0, vTwoShorts, 0, LEN);
        }

        @Test
        @IR(counts = { IRNode.SLOW_ARRAYCOPY, "1" }, // Different layouts
            failOn = { IRNode.JINT_ARRAYCOPY, IRNode.JLONG_ARRAYCOPY }) // no fast path
        static void testMergedNullableNullFreeAtomic(boolean flag, boolean flag2) {
            VTwoShorts[] vTwoShortsSrc = flag ? SRC_NULL_FREE_ATOMIC : SRC_NULLABLE; // Type: nullable
            VTwoShorts[] vTwoShortsDest = flag2 ? DEST_NULL_FREE_ATOMIC : DEST_NULLABLE; // Type: nullable
            System.arraycopy(vTwoShortsSrc, 0, vTwoShortsDest, 0, LEN);
        }

        @Test
        @IR(counts = { IRNode.SLOW_ARRAYCOPY, "1" }, // Different layouts
            failOn = { IRNode.JINT_ARRAYCOPY, IRNode.JLONG_ARRAYCOPY }) // no fast path
        static void testMergedNullableNullFreeNonAtomic(boolean flag, boolean flag2) {
            VTwoShorts[] vTwoShortsSrc = flag ? SRC_NULL_FREE_NON_ATOMIC : SRC_NULLABLE; // Type: Unknown atomicity + nullable
            VTwoShorts[] vTwoShortsDest = flag2 ? DEST_NULL_FREE_NON_ATOMIC : DEST_NULLABLE; // Type: Unknown atomicity + nullable
            System.arraycopy(vTwoShortsSrc, 0, vTwoShortsDest, 0, LEN);
        }

        // Without traps: Not verified but known to be same layout + not atomic
        //                -> Select fast path in macro expansion.
        @Test
        @IR(counts = { IRNode.JINT_ARRAYCOPY, "1" }, // Matching layout + non-atomic -> fast path
            failOn = { IRNode.SLOW_ARRAYCOPY })      // and no slow path
        static void testFastPathMacroExpansion() {
            System.arraycopy(SRC_NULL_FREE_NON_ATOMIC_SIZE_10, 0,
                             DEST_NULL_FREE_NON_ATOMIC_SIZE_10, 0, 10);
        }

        @Test
        @IR(counts = { IRNode.SLOW_ARRAYCOPY, "1" }, // Matching layout but atomic -> slow path
            failOn = { IRNode.JINT_ARRAYCOPY, IRNode.JLONG_ARRAYCOPY }) // no fast path
        static void testSlowPathAtomic() {
            System.arraycopy(SRC_NULL_FREE_ATOMIC, 0,
                             DEST_NULL_FREE_ATOMIC, 0, LEN);
        }

        @Test
        // Cannot prove same layouts due to abstract types (i.e. inexact) -> slow path
        @IR(counts = { IRNode.SLOW_ARRAYCOPY, "1" },
            failOn = { IRNode.JINT_ARRAYCOPY, IRNode.JLONG_ARRAYCOPY }) // no fast path
        static void testAbstract(VBaseTwoShorts[] src, VBaseTwoShorts[] dest) {
            System.arraycopy(src, 0, dest, 0, LEN);
        }

        static void verifySame(Object[] src, Object[] dest) {
            Asserts.assertEQ(src.length, dest.length);
            for (int i = 0; i < src.length; i++) {
                if (src[i] != dest[i]) {
                    throw new RuntimeException(arrayAsString(dest));
                }
            }
        }

        static String arrayAsString(Object[] arr) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < arr.length; i++) {
                builder.append(i).append(": ").append(arr[i]).append(System.lineSeparator());
            }
            return builder.toString();
        }

        @LooselyConsistentValue
        static abstract value class VBaseTwoShorts {}

        @LooselyConsistentValue
        static value class VTwoShorts extends VBaseTwoShorts {
            short x;
            short y;

            VTwoShorts(int i) {
                x = (short) i;
                y = (short) (i + 1);
            }

            @Override
            public String toString() {
                return "x=" + x + ", y=" + y;
            }
        }

        @LooselyConsistentValue
        static value class VTwoShorts2 extends VBaseTwoShorts {
            short x;
            short y;

            VTwoShorts2(int i) {
                x = (short) i;
                y = (short) (i + 1);
            }

            @Override
            public String toString() {
                return "x=" + x + ", y=" + y;
            }
        }
    }

    /*
     * Test cases where src and dest are different classes but src starts with some null elements.
     *
     * The semantics of arraycopy allow the first few nulls to be copied until we hit the first non-null element where
     * we then throw an ArrayStoreException.
     *
     * For example, with NON_NULL_INDEX = 3:
     *
     * Before Arraycopy:
     *   VInt[] src:   [null,     null, null, VInt(... ), VInt(... ), ...]
     *   VInt2[] dest: [D0,       D1,   D2,   D3,         D4,         ...]
     * After Arraycopy:
     *   VInt2[] dest: [null,     null, null, D3,         D4,         ...]
     *   -> Index 0 - 2: Successfully copy null to VInt2[]
     *   -> At index 3:  Copying VInt(34) to VInt2[] throws an ArrayStoreException without updating index 3.
     */
    static class DifferentClassWithNull {
        static final VInt[] SRC_NULLABLE = (VInt[]) ValueClass.newNullableAtomicArray(VInt.class, LEN + 3);
        static final VInt2[] DEST_NULLABLE = (VInt2[]) ValueClass.newNullableAtomicArray(VInt2.class, LEN + 3);
        static final VInt2[] DEST_NULLABLE_UNMODIFIED = (VInt2[]) ValueClass.newNullableAtomicArray(VInt2.class, LEN + 3);
        static final int NON_NULL_INDEX = RAND.nextInt(1, SRC_NULLABLE.length - 2);

        static void run() {
            for (int i = 0; i < SRC_NULLABLE.length; i++) {
                if (i < NON_NULL_INDEX) {
                    SRC_NULLABLE[i] = null;
                } else {
                    SRC_NULLABLE[i] = new VInt(SRC_INIT + i);
                }
                DEST_NULLABLE[i] = new VInt2(DEST_INIT + i); // reset again
                DEST_NULLABLE_UNMODIFIED[i] = new VInt2(DEST_INIT + i);
            }

            for (int i = 0; i < 10_000; i++) {
                runTest();
            }
        }

        private static void runTest() {
            try {
                test();
                Asserts.fail("Should throw ArrayStoreException");
            } catch (ArrayStoreException e) {
                // Expected
                for (int i = 0; i < SRC_NULLABLE.length; i++) {
                    if (i < NON_NULL_INDEX) {
                        Asserts.assertEQ(DEST_NULLABLE[i], null);
                    } else {
                        Asserts.assertEQ(DEST_NULLABLE[i], DEST_NULLABLE_UNMODIFIED[i]);
                    }
                    DEST_NULLABLE[i] = new VInt2(DEST_INIT + i); // reset again
                }
            }
        }

        static void test() {
            System.arraycopy(SRC_NULLABLE, 0, DEST_NULLABLE, 0, LEN + 3);
        }
    }

    private static <V> void init(V[] arr, IntFunction<V> constructor, int initValue) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = constructor.apply(initValue + i);
        }
    }

    static abstract value class VBase {}

    static value class VInt extends VBase {
        int x;

        VInt(int x) {
            this.x = x;
        }
    }

    static value class VInt2 extends VBase {
        int x;

        VInt2(int x) {
            this.x = x;
        }
    }

    static value class VLong extends VBase {
        long x;

        VLong(long x) {
            this.x = x;
        }
    }

    static value class VLong2 extends VBase {
        long x;

        VLong2(long x) {
            this.x = x;
        }
    }

    public static void main(String[] args) {
        if (args.length == 1) {
            Asserts.assertEQ(args[0], "ir");
            new TestFramework(SameClassDifferentLayout.class)
                    .addFlags(
                            "--enable-preview",
                            "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                            "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                            "-XX:-TieredCompilation",
                            "-XX:+UnlockExperimentalVMOptions",
                            "-XX:PerMethodSpecTrapLimit=0",
                            "-XX:PerMethodTrapLimit=0")
                    .start();
            return;
        }

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

        NullFreeArraysOfDifferentClasses.run();
        for (int i = 0; i < 10_000; i++) {
            // Invoke normally, no need for IR Framework interaction.
            SameClassDifferentLayout.run();
        }
        DifferentClassWithNull.run();
    }
}
