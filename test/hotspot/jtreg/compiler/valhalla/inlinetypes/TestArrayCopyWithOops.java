/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8252506
 * @summary Verify that arraycopy intrinsics properly handle flat value class arrays with oop fields.
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestArrayCopyWithOops
 */

/*
 * @test id=do
 * @bug 8252506
 * @summary Verify that arraycopy intrinsics properly handle flat value class arrays with oop fields.
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestArrayCopyWithOops::test*
 *                   -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestArrayCopyWithOops::create*
 *                   -Xbatch
 *                   compiler.valhalla.inlinetypes.TestArrayCopyWithOops
 */

/*
 * @test id=do-no-flattening
 * @bug 8252506
 * @summary Verify that arraycopy intrinsics properly handle flat value class arrays with oop fields.
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestArrayCopyWithOops::test*
 *                   -XX:CompileCommand=dontinline,compiler.valhalla.inlinetypes.TestArrayCopyWithOops::create*
 *                   -Xbatch -XX:-UseArrayFlattening
 *                   compiler.valhalla.inlinetypes.TestArrayCopyWithOops
 */

package compiler.valhalla.inlinetypes;

import java.util.Arrays;

import jdk.test.lib.Asserts;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;

public class TestArrayCopyWithOops {
    static final int LEN = 200;

    static class MyObject {
        long val = Integer.MAX_VALUE;
    }

    @LooselyConsistentValue
    static value class ManyOops {
        MyObject o1 = new MyObject();
        MyObject o2 = new MyObject();
        MyObject o3 = new MyObject();
        MyObject o4 = new MyObject();

        long hash() {
            return o1.val + o2.val + o3.val + o4.val;
        }
    }

    static ManyOops[] createValueClassArray() {
        return (ManyOops[])ValueClass.newNullRestrictedNonAtomicArray(ManyOops.class, LEN, new ManyOops());
    }

    static Object[] createObjectArray() {
        return createValueClassArray();
    }

    static Object createObject() {
        return createValueClassArray();
    }

    // System.arraycopy tests

    static void test1(ManyOops[] dst) {
        System.arraycopy(createValueClassArray(), 0, dst, 0, LEN);
    }

    static void test2(Object[] dst) {
        System.arraycopy(createObjectArray(), 0, dst, 0, LEN);
    }

    static void test3(ManyOops[] dst) {
        System.arraycopy(createObjectArray(), 0, dst, 0, LEN);
    }

    static void test4(Object[] dst) {
        System.arraycopy(createValueClassArray(), 0, dst, 0, LEN);
    }

    // System.arraycopy tests (tightly coupled with allocation of dst array)

    static Object[] test5() {
        ManyOops[] dst = (ManyOops[])ValueClass.newNullRestrictedNonAtomicArray(ManyOops.class, LEN, new ManyOops());
        System.arraycopy(createValueClassArray(), 0, dst, 0, LEN);
        return dst;
    }

    static Object[] test6() {
        Object[] dst = new Object[LEN];
        System.arraycopy(createObjectArray(), 0, dst, 0, LEN);
        return dst;
    }

    static Object[] test7() {
        ManyOops[] dst = (ManyOops[])ValueClass.newNullRestrictedNonAtomicArray(ManyOops.class, LEN, new ManyOops());
        System.arraycopy(createObjectArray(), 0, dst, 0, LEN);
        return dst;
    }

    static Object[] test8() {
        Object[] dst = new Object[LEN];
        System.arraycopy(createValueClassArray(), 0, dst, 0, LEN);
        return dst;
    }

    // Arrays.copyOf tests

    static Object[] test9() {
        return Arrays.copyOf(createValueClassArray(), LEN, ManyOops[].class);
    }

    static Object[] test10() {
        return Arrays.copyOf(createObjectArray(), LEN, Object[].class);
    }

    static Object[] test11() {
        ManyOops[] src = createValueClassArray();
        return Arrays.copyOf(src, LEN, src.getClass());
    }

    static Object[] test12() {
        Object[] src = createObjectArray();
        return Arrays.copyOf(createObjectArray(), LEN, src.getClass());
    }

    // System.arraycopy test using generic_copy stub

    static void test13(Object dst) {
        System.arraycopy(createObject(), 0, dst, 0, LEN);
    }

    static void produceGarbage() {
        for (int i = 0; i < 100; ++i) {
            Object[] arrays = new Object[1024];
            for (int j = 0; j < arrays.length; j++) {
                arrays[j] = new int[1024];
            }
        }
        System.gc();
    }

    public static void main(String[] args) {
        ManyOops[] dst1 = createValueClassArray();
        ManyOops[] dst2 = createValueClassArray();
        ManyOops[] dst3 = createValueClassArray();
        ManyOops[] dst4 = createValueClassArray();
        ManyOops[] dst13 = createValueClassArray();

        // Warmup runs to trigger compilation
        for (int i = 0; i < 50_000; ++i) {
            test1(dst1);
            test2(dst2);
            test3(dst3);
            test4(dst4);
            test5();
            test6();
            test7();
            test8();
            test9();
            test10();
            test11();
            test12();
            test13(dst13);
        }

        // Trigger GC to make sure dst arrays are moved to old gen
        produceGarbage();

        // Move data from flat src to flat dest
        test1(dst1);
        test2(dst2);
        test3(dst3);
        test4(dst4);
        Object[] dst5 = test5();
        Object[] dst6 = test6();
        Object[] dst7 = test7();
        Object[] dst8 = test8();
        Object[] dst9 = test9();
        Object[] dst10 = test10();
        Object[] dst11 = test11();
        Object[] dst12 = test12();
        test13(dst13);

        // Trigger GC again to make sure that the now dead src arrays are collected.
        // MyObjects should be kept alive via oop references from the dst array.
        produceGarbage();

        // Verify content
        long expected = 4L*Integer.MAX_VALUE;
        for (int i = 0; i < LEN; ++i) {
            Asserts.assertEquals(dst1[i].hash(), expected);
            Asserts.assertEquals(dst2[i].hash(), expected);
            Asserts.assertEquals(dst3[i].hash(), expected);
            Asserts.assertEquals(dst4[i].hash(), expected);
            Asserts.assertEquals(((ManyOops)dst5[i]).hash(), expected);
            Asserts.assertEquals(((ManyOops)dst7[i]).hash(), expected);
            Asserts.assertEquals(((ManyOops)dst8[i]).hash(), expected);
            Asserts.assertEquals(((ManyOops)dst8[i]).hash(), expected);
            Asserts.assertEquals(((ManyOops)dst9[i]).hash(), expected);
            Asserts.assertEquals(((ManyOops)dst10[i]).hash(), expected);
            Asserts.assertEquals(((ManyOops)dst11[i]).hash(), expected);
            Asserts.assertEquals(((ManyOops)dst12[i]).hash(), expected);
            Asserts.assertEquals(dst13[i].hash(), expected);
        }
    }
}
